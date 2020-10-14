package de.laser

import com.k_int.kbplus.ExecutorWrapperService
import com.k_int.kbplus.auth.User
import de.laser.controller.AbstractDebugController
import de.laser.helper.DateUtil
import de.laser.helper.DebugAnnotation
import de.laser.helper.RDConstants
import de.laser.helper.RDStore
import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.annotation.Secured
import groovy.util.slurpersupport.GPathResult
import org.apache.poi.hssf.usermodel.HSSFRow
import org.apache.poi.hssf.usermodel.HSSFSheet
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.codehaus.groovy.grails.plugins.orm.auditable.AuditLogEvent

import javax.servlet.ServletOutputStream
import java.text.SimpleDateFormat

@Secured(['IS_AUTHENTICATED_FULLY'])
class PackageController extends AbstractDebugController {

    def springSecurityService
    def genericOIDService
    def yodaService
    def exportService
    def institutionsService
    ExecutorWrapperService executorWrapperService
    def accessService
    def contextService
    def taskService
    def addressbookService
    def docstoreService
    def GOKbService
    def globalSourceSyncService
    def filterService
    EscapeService escapeService

    static allowedMethods = [create: ['GET', 'POST'], edit: ['GET', 'POST'], delete: 'POST']

    @Secured(['ROLE_USER'])
    def index() {

        Map<String, Object> result = [:]
        result.user = springSecurityService.getCurrentUser()
        params.max = params.max ?: result.user.getDefaultPageSize()

        if (springSecurityService.isLoggedIn()) {
            if (params.q == "") params.remove('q');

            if (params.search.equals("yes")) {
                //when searching make sure results start from first page
                params.offset = 0
                params.remove("search")
            }

            def old_q = params.q
            def old_sort = params.sort

            if (!ApiSource.findAllByTypAndActive(ApiSource.ApiTyp.GOKBAPI, true)) {
                redirect controller: 'package', action: 'list'
                return
            }

            def gokbRecords = []

            ApiSource.findAllByTypAndActive(ApiSource.ApiTyp.GOKBAPI, true).each { api ->
                gokbRecords << GOKbService.getPackagesMap(api, params.q, false).records
            }

            params.sort = params.sort ?: 'name'
            params.order = params.order ?: 'asc'

            result.records = gokbRecords ? gokbRecords.flatten().sort() : null

            result.records?.sort { x, y ->
                if (params.order == 'desc') {
                    y."${params.sort}".toString().compareToIgnoreCase x."${params.sort}".toString()
                } else {
                    x."${params.sort}".toString().compareToIgnoreCase y."${params.sort}".toString()
                }
            }

            result.resultsTotal2 = result.records?.size()

            Integer start = params.offset ? params.int('offset') : 0
            Integer end = params.offset ? params.int('max') + params.int('offset') : params.int('max')
            end = (end > result.records?.size()) ? result.records?.size() : end

            result.records = result.records?.subList(start, end)

            //Double-Quoted search strings wont display without this
            params.q = old_q?.replace("\"", "&quot;")

            if (!old_q) {
                params.remove('q')
            }
            if (!old_sort) {
                params.remove('sort')
            }
        }
        result
    }

    @Secured(['ROLE_USER'])
    def list() {
        Map<String, Object> result = [:]
        result.user = User.get(springSecurityService.principal.id)
        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()

        result.editable = true

        def paginate_after = params.paginate_after ?: ((2 * result.max) - 1)
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        RefdataValue deleted_package_status = RefdataValue.getByValueAndCategory('Deleted', RDConstants.PACKAGE_STATUS)
        //def qry_params = [deleted_package_status]
        def qry_params = []

        // TODO: filter by status in frontend
        // TODO: use elastic search
        String base_qry = " from Package as p where ( (p.packageStatus is null ) OR ( p.packageStatus is not null ) ) "
        //def base_qry = " from Package as p where ( (p.packageStatus is null ) OR ( p.packageStatus = ? ) ) "

        if (params.q?.length() > 0) {
            base_qry += " and ( ( lower(p.name) like ? ) or ( lower(p.identifier) like ? ) )"
            qry_params.add("%${params.q.trim().toLowerCase()}%");
            qry_params.add("%${params.q.trim().toLowerCase()}%");
        }

        if (params.updateStartDate?.length() > 0) {
            base_qry += " and ( p.lastUpdated > ? )"
            qry_params.add(params.date('updateStartDate', message(code: 'default.date.format.notime')));
        }

        if (params.updateEndDate?.length() > 0) {
            base_qry += " and ( p.lastUpdated < ? )"
            qry_params.add(params.date('updateEndDate', message(code: 'default.date.format.notime')));
        }

        if (params.createStartDate?.length() > 0) {
            base_qry += " and ( p.dateCreated > ? )"
            qry_params.add(params.date('createStartDate', message(code: 'default.date.format.notime')));
        }

        if (params.createEndDate?.length() > 0) {
            base_qry += " and ( p.dateCreated < ? )"
            qry_params.add(params.date('createEndDate', message(code: 'default.date.format.notime')));
        }

        if ((params.sort != null) && (params.sort.length() > 0)) {
            base_qry += " order by p.${params.sort} ${params.order}"
        } else {
            base_qry += " order by lower(p.name) asc"
        }


        log.debug(base_qry + ' <<< ' + qry_params)
        result.packageInstanceTotal = Subscription.executeQuery( "select p.id " + base_qry, qry_params ).size()


        withFormat {
            html {
                result.packageInstanceList = Subscription.executeQuery( "select p " + base_qry, qry_params, [max: result.max, offset: result.offset] )
                result
            }
            csv {
                response.setHeader("Content-disposition", "attachment; filename=\"packages.csv\"")
                response.contentType = "text/csv"
                def packages = Subscription.executeQuery( "select p " + base_qry, qry_params )
                def out = response.outputStream
                log.debug('colheads');
                out.withWriter { writer ->
                    writer.write('Package Name, Creation Date, Last Modified, Identifier\n');
                    packages.each {
                        log.debug(it);
                        writer.write("${it.name},${it.dateCreated},${it.lastUpdated},${it.identifier}\n")
                    }
                    writer.write("END");
                    writer.flush();
                    writer.close();
                }
                out.close()
            }
        }
    }

    @Secured(['ROLE_ADMIN'])
    @Deprecated
    // should this not be only possible from GOKb??
    def create() {
        User user = User.get(springSecurityService.principal.id)

        switch (request.method) {
            case 'GET':
                [packageInstance: new Package(params), user: user]
                break
            case 'POST':
                def providerName = params.contentProviderName
                def packageName = params.packageName
                def identifier = params.identifier

                Org contentProvider = Org.findByName(providerName);
                Package existing_pkg = Package.findByIdentifier(identifier);

                if (contentProvider && existing_pkg == null) {
                    log.debug("Create new package, content provider = ${contentProvider}, identifier is ${identifier}");
                    Package new_pkg = new Package(identifier: identifier,
                            contentProvider: contentProvider,
                            name: packageName,
                            impId: java.util.UUID.randomUUID().toString());
                    if (new_pkg.save(flush: true)) {
                        redirect action: 'edit', id: new_pkg.id
                    } else {
                        new_pkg.errors.each { e ->
                            log.error("Problem: ${e}");
                        }
                        render view: 'create', model: [packageInstance: new_pkg, user: user]
                    }
                } else {
                    render view: 'create', model: [packageInstance: packageInstance, user: user]
                    return
                }

                // flash.message = message(code: 'default.created.message', args: [message(code: 'package.label'), packageInstance.id])
                // redirect action: 'show', id: packageInstance.id
                break
        }
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_USER")
    })
    def compare() {
        Map<String, Object> result = [:]
        result.unionList = []

        result.user = User.get(springSecurityService.principal.id)
        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        if (params.pkgA?.length() > 0 && params.pkgB?.length() > 0) {

            result.pkgInsts = []
            result.pkgDates = []
            def listA
            def listB
            try {
                listA = createCompareList(params.pkgA, params.dateA, params, result)
                listB = createCompareList(params.pkgB, params.dateB, params, result)
                if (!params.countA) {
                    String countHQL = "select count(elements(pkg.tipps)) from Package pkg where pkg.id = :pid"
                    params.countA = Package.executeQuery(countHQL, [pid: result.pkgInsts.get(0).id])
                    log.debug("countA is ${params.countA}")
                    params.countB = Package.executeQuery(countHQL, [pid: result.pkgInsts.get(1).id])
                    log.debug("countB is ${params.countB}")
                }
            } catch (IllegalArgumentException e) {
                request.message = e.getMessage()
                return
            }

            def groupedA = listA.groupBy({ it.title.title })
            def groupedB = listB.groupBy({ it.title.title })

            def mapA = listA.collectEntries { [it.title.title, it] }
            def mapB = listB.collectEntries { [it.title.title, it] }

            result.listACount = [tipps: listA.size(), titles: mapA.size()]
            result.listBCount = [tipps: listB.size(), titles: mapB.size()]

            log.debug("mapA: ${mapA.size()}, mapB: ${mapB.size()}")

            def unionList = groupedA.keySet().plus(groupedB.keySet()).toList() // heySet is hashSet
            unionList = unionList.unique()
            unionList.sort()

            log.debug("UnionList has ${unionList.size()} entries.")

            def filterRules = [params.insrt ? true : false, params.dlt ? true : false, params.updt ? true : false, params.nochng ? true : false]

            result.unionListSize = institutionsService.generateComparisonMap(unionList, mapA, mapB, 0, unionList.size(), filterRules).size()

            withFormat {
                html {
                    def toIndex = result.offset + result.max < unionList.size() ? result.offset + result.max : unionList.size()
                    result.comparisonMap =
                            institutionsService.generateComparisonMap(unionList, groupedA, groupedB, result.offset, toIndex.intValue(), filterRules)
                    result
                }
                csv {
                    try {

                        def comparisonMap =
                                institutionsService.generateComparisonMap(unionList, mapA, mapB, 0, unionList.size(), filterRules)
                        log.debug("Create CSV Response")
                        SimpleDateFormat dateFormatter = DateUtil.getSDF_NoTime()
                        response.setHeader("Content-disposition", "attachment; filename=\"packageComparison.csv\"")
                        response.contentType = "text/csv"
                        def out = response.outputStream
                        out.withWriter { writer ->
                            writer.write("${result.pkgInsts[0].name} on ${params.dateA}, ${result.pkgInsts[1].name} on ${params.dateB}\n")
                            writer.write('Title, pISSN, eISSN, Start Date A, Start Date B, Start Volume A, Start Volume B, Start Issue A, Start Issue B, End Date A, End Date B, End Volume A,End  Volume B,End  Issue A,End  Issue B, Coverage Note A, Coverage Note B, ColorCode\n');
                            // log.debug("UnionList size is ${unionList.size}")
                            comparisonMap.each { title, values ->
                                def tippA = values[0]
                                def tippB = values[1]
                                def colorCode = values[2]
                                def pissn = tippA ? tippA.title.getIdentifierValue('issn') : tippB.title.getIdentifierValue('issn');
                                def eissn = tippA ? tippA.title.getIdentifierValue('eISSN') : tippB.title.getIdentifierValue('eISSN');

                                writer.write("\"${title}\",\"${pissn ?: ''}\",\"${eissn ?: ''}\",\"${formatDateOrNull(dateFormatter, tippA?.startDate)}\",\"${formatDateOrNull(dateFormatter, tippB?.startDate)}\",\"${tippA?.startVolume ?: ''}\",\"${tippB?.startVolume ?: ''}\",\"${tippA?.startIssue ?: ''}\",\"${tippB?.startIssue ?: ''}\",\"${formatDateOrNull(dateFormatter, tippA?.endDate)}\",\"${formatDateOrNull(dateFormatter, tippB?.endDate)}\",\"${tippA?.endVolume ?: ''}\",\"${tippB?.endVolume ?: ''}\",\"${tippA?.endIssue ?: ''}\",\"${tippB?.endIssue ?: ''}\",\"${tippA?.coverageNote ?: ''}\",\"${tippB?.coverageNote ?: ''}\",\"${colorCode}\"\n")
                            }
                            writer.write("END");
                            writer.flush();
                            writer.close();
                        }
                        out.close()

                    } catch (Exception e) {
                        log.error("An Exception was thrown here", e)
                    }
                }
            }

        } else {
            SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
            Date currentDate = sdf?.format(new Date())
            params.dateA = currentDate
            params.dateB = currentDate
            params.insrt = "Y"
            params.dlt = "Y"
            params.updt = "Y"
            flash.message = message(code: 'package.compare.flash')
            result
        }

    }

    private def formatDateOrNull(formatter, date) {
        def result;
        if (date) {
            result = formatter.format(date)
        } else {
            result = ''
        }
        return result
    }

    private def createCompareList(pkg, dateStr, params, result) {
        def returnVals = [:]
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        Date date = dateStr ? sdf.parse(dateStr) : new Date()
        def packageId = pkg.substring(pkg.indexOf(":") + 1)

        Package packageInstance = Package.get(packageId)

        if (date < packageInstance.startDate) {
            throw new IllegalArgumentException(
                    "${packageInstance.name} start date is ${sdf.format(packageInstance.startDate)}. " +
                            "Date to compare it on is ${sdf.format(date)}, this is before start date.")
        }
        if (packageInstance.endDate && date > packageInstance.endDate) {
            throw new IllegalArgumentException(
                    "${packageInstance.name} end date is ${sdf.format(packageInstance.endDate)}. " +
                            "Date to compare it on is ${sdf.format(date)}, this is after end date.")
        }

        result.pkgInsts.add(packageInstance)

        result.pkgDates.add(sdf.format(date))

        def queryParams = [packageInstance]

        def query = filterService.generateBasePackageQuery(params, queryParams, true, date, "Platform")
        def list = TitleInstancePackagePlatform.executeQuery("select tipp " + query.base_qry, query.qry_params)

        return list
    }

    @Secured(['ROLE_USER'])
    def show() {
        exportService.printStart("Package show")

        Map<String, Object> result = [:]
        boolean showDeletedTipps = false

        result.user = User.get(springSecurityService.principal.id)
        Package packageInstance = Package.get(params.id)
        if (!packageInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'package.label'), params.id])
            redirect action: 'list'
            return
        }

        result.pkg_link_str = "${grailsApplication.config.grails.serverURL}/package/show/${params.id}"

        // tasks
        Org contextOrg = contextService.getOrg()
        result.tasks = taskService.getTasksByResponsiblesAndObject(User.get(springSecurityService.principal.id), contextOrg, packageInstance)
        Map<String,Object> preCon = taskService.getPreconditionsWithoutTargets(contextOrg)
        result << preCon

        result.contextOrg = contextOrg

        result.modalPrsLinkRole = RefdataValue.getByValueAndCategory('Specific package editor', RDConstants.PERSON_RESPONSIBILITY)
        result.modalVisiblePersons = addressbookService.getPrivatePersonsByTenant(contextService.getOrg())

        // restrict visible for templates/links/orgLinksAsList
        result.visibleOrgs = packageInstance.orgs
        //result.visibleOrgs.sort { it.org.sortname }

        List<RefdataValue> roleTypes = [RDStore.OR_SUBSCRIBER]
        if(accessService.checkPerm('ORG_CONSORTIUM')) {
            roleTypes.addAll([RDStore.OR_SUBSCRIPTION_CONSORTIA, RDStore.OR_SUBSCRIBER_CONS])
        }
        else if(accessService.checkPerm('ORG_INST_COLLECTIVE')) {
            roleTypes.addAll([RDStore.OR_SUBSCRIPTION_COLLECTIVE, RDStore.OR_SUBSCRIBER_COLLECTIVE])
        }

        result.subscriptionList = Subscription.executeQuery('select oo.sub from OrgRole oo where oo.org = :contextOrg and oo.roleType in :roleTypes and oo.sub.status = :current and not exists (select sp.subscription from SubscriptionPackage sp where sp.subscription = oo.sub and sp.pkg = :pkg)',
                [contextOrg: contextService.org, roleTypes: roleTypes, current: RDStore.SUBSCRIPTION_CURRENT,pkg:packageInstance])

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        params.max = result.max
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0

        Map<String,Object> limits = (!params.format || params.format.equals("html")) ? [max: result.max, offset: result.offset] : [offset: 0]

        // def base_qry = "from TitleInstancePackagePlatform as tipp where tipp.pkg = ? "
        Map<String,Object> qry_params = [pkgInstance: packageInstance]

        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        Date today = new Date()
        if (!params.asAt) {
            if (packageInstance.startDate > today) {
                params.asAt = sdf.format(packageInstance.startDate)
            } else if (packageInstance.endDate < today && packageInstance.endDate) {
                params.asAt = sdf.format(packageInstance.endDate)
            }
        }
        Date date_filter
        if (params.mode == 'advanced') {
            date_filter = null
            params.asAt = null
        } else if (params.asAt && params.asAt.length() > 0) {
            date_filter = sdf.parse(params.asAt)
        } else {
            date_filter = today
        }

        Map<String,Object> query = filterService.generateBasePackageQuery(params, qry_params, showDeletedTipps, date_filter,"Package")

        // log.debug("Base qry: ${base_qry}, params: ${qry_params}, result:${result}");
        List<TitleInstancePackagePlatform> titlesList = TitleInstancePackagePlatform.executeQuery("select tipp " + query.base_qry, query.qry_params, limits)
        //result.num_tipp_rows = TitleInstancePackagePlatform.executeQuery("select tipp.id " + base_qry, qry_params).size()
        result.unfiltered_num_tipp_rows = TitleInstancePackagePlatform.executeQuery(
                "select tipp.id from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkg", [pkg: packageInstance]).size()

        result.lasttipp = result.offset + result.max > result.num_tipp_rows ? result.num_tipp_rows : result.offset + result.max

        if (OrgSetting.get(contextOrg, OrgSetting.KEYS.NATSTAT_SERVER_REQUESTOR_ID) instanceof OrgSetting){
            result.statsWibid = contextOrg.getIdentifierByType('wibid')?.value
            result.usageMode = accessService.checkPerm("ORG_CONSORTIUM") ? 'package' : 'institution'
            result.packageIdentifier = packageInstance.getIdentifierByType('isil')?.value
        }

        result.packageInstance = packageInstance

        /*
        TODO [ticket=1142] matter of ERMS-1142
        String filename = "${escapeService.escapeString(result.packageInstance.name)}_asAt_${date_filter ? sdf.format(date_filter) : sdf.format(today)}"
        */
        withFormat {
            html result
            /*
            json {
                def map = exportService.getPackageMap(packageInstance, result.titlesList)

                def json = map as JSON

                response.setHeader("Content-disposition", "attachment; filename=\"${filename}.json\"")
                response.contentType = "application/json"
                render json
            }
            xml {
                def starttime = exportService.printStart("Building XML Doc")
                def doc = exportService.buildDocXML("Packages")
                exportService.addPackageIntoXML(doc, doc.getDocumentElement(), packageInstance, result.titlesList)
                exportService.printDuration(starttime, "Building XML Doc")

                response.setHeader("Content-disposition", "attachment; filename=\"${filename}.xml\"")
                response.contentType = "text/xml"
                exportService.streamOutXML(doc, response.outputStream)
            }
        */
        }
    }

    @Secured(['ROLE_USER'])
    def current() {
        log.debug("current ${params}");
        Map<String, Object> result = [:]
        boolean showDeletedTipps = false
        result.user = User.get(springSecurityService.principal.id)
        result.editable = isEditable()

        Package packageInstance = Package.get(params.id)
        if (!packageInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'package.label'), params.id])
            redirect action: 'list'
            return
        }
        result.packageInstance = packageInstance

        if (executorWrapperService.hasRunningProcess(packageInstance)) {
            result.processingpc = true
        }

        result.pendingChanges = PendingChange.executeQuery(
                "select pc from PendingChange as pc where pc.pkg = :pkg and ( pc.status is null or pc.status = :status ) order by ts, payload",
                [pkg: packageInstance, status: RDStore.PENDING_CHANGE_PENDING]
        )

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        params.max = result.max
        //def paginate_after = params.paginate_after ?: ((2 * result.max) - 1);
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0

        // def base_qry = "from TitleInstancePackagePlatform as tipp where tipp.pkg = ? "
        Map<String,Object> qry_params = [pkgInstance: packageInstance]
        Date date_filter = params.mode == 'advanced' ? null : new Date()

        Map<String,Object> query = filterService.generateBasePackageQuery(params, qry_params, showDeletedTipps, date_filter,"Package")
        result.filterSet = query.filterSet

        List<TitleInstancePackagePlatform> titlesList = TitleInstancePackagePlatform.executeQuery("select tipp "+query.base_qry, query.qry_params)
        result.titlesList = titlesList.drop(result.offset).take(result.max)
        result.num_tipp_rows = titlesList.size()

        result.lasttipp = result.offset + result.max > result.num_tipp_rows ? result.num_tipp_rows : result.offset + result.max

        String filename = "${escapeService.escapeString(packageInstance.name)}_${DateUtil.SDF_NoTimeNoPoint.format(new Date())}"

        if(params.exportKBart) {
            response.setHeader("Content-disposition", "attachment; filename=${filename}.tsv")
            response.contentType = "text/tsv"
            ServletOutputStream out = response.outputStream
            Map<String,List> tableData = exportService.generateTitleExportKBART(titlesList)
            out.withWriter { writer ->
                writer.write(exportService.generateSeparatorTableString(tableData.titleRow,tableData.columnData,'\t'))
            }
            out.flush()
            out.close()
        }
        else if(params.exportXLSX) {
            response.setHeader("Content-disposition", "attachment; filename=\"${filename}.xlsx\"")
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            Map<String,List> export = exportService.generateTitleExportXLS(titlesList)
            Map sheetData = [:]
            sheetData[message(code:'menu.my.titles')] = [titleRow:export.titles,columnData:export.rows]
            SXSSFWorkbook workbook = exportService.generateXLSXWorkbook(sheetData)
            workbook.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            workbook.dispose()
        }
        withFormat {
            html {
                result
            }
            csv {
                response.setHeader("Content-disposition", "attachment; filename=${filename}.csv")
                response.contentType = "text/csv"

                ServletOutputStream out = response.outputStream
                Map<String,List> tableData = exportService.generateTitleExportCSV(titlesList)
                out.withWriter { writer ->
                    writer.write(exportService.generateSeparatorTableString(tableData.titleRow,tableData.rows,';'))
                }
                out.flush()
                out.close()
            }
        }
    }

    @Deprecated
    @Secured(['ROLE_USER'])
    def deleteDocuments() {
        def ctxlist = []

        log.debug("deleteDocuments ${params}");

        docstoreService.unifiedDeleteDocuments(params)

        redirect controller: 'package', action: params.redirectAction, id: params.instanceId
    }

    @Deprecated
    @Secured(['ROLE_USER'])
    def documents() {
        Map<String, Object> result = [:]
        result.user = User.get(springSecurityService.principal.id)
        result.institution = contextService.org
        result.packageInstance = Package.get(params.id)
        result.editable = isEditable()

        result
    }

    @Secured(['ROLE_USER'])
    def expected() {
        previous_expected(params, "expected")
    }

    @Secured(['ROLE_USER'])
    def previous() {
        previous_expected(params, "previous")
    }

    @Secured(['ROLE_USER'])
    def previous_expected(params, func) {
        log.debug("previous_expected ${params}");
        Map<String, Object> result = [:]
        boolean showDeletedTipps = false
        result.user = User.get(springSecurityService.principal.id)
        result.editable = isEditable()
        def packageInstance = Package.get(params.id)
        if (!packageInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'package.label'), params.id])
            redirect action: 'list'
            return
        }
        result.packageInstance = packageInstance

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        params.max = result.max
        def paginate_after = params.paginate_after ?: ((2 * result.max) - 1);
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        def limits = (!params.format || params.format.equals("html")) ? [max: result.max, offset: result.offset] : [offset: 0]

        String base_qry = "from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkg and tipp.status != :status "
        Map<String, Object> qry_params = [pkg: packageInstance, status: RDStore.TIPP_STATUS_DELETED, date: new Date()]

        if (func == "expected") {
            base_qry += " and ( coalesce(tipp.accessStartDate, tipp.pkg.startDate) >= :date ) "
        }
        else {
            base_qry += " and ( tipp.accessEndDate <= :date ) "
        }

        base_qry += " order by ${params.sort ?: 'tipp.title.sortTitle'} ${params.order ?: 'asc'} "

    log.debug("Base qry: ${base_qry}, params: ${qry_params}, result:${result}");
    result.titlesList = TitleInstancePackagePlatform.executeQuery("select tipp "+base_qry, qry_params, limits);
    result.num_tipp_rows = TitleInstancePackagePlatform.executeQuery("select tipp.id " + base_qry, qry_params ).size()

        result.lasttipp = result.offset + result.max > result.num_tipp_rows ? result.num_tipp_rows : result.offset + result.max;

        result
    }

    @Secured(['ROLE_ADMIN'])
    def uploadTitles() {
        Package pkg = Package.get(params.id)
        def upload_mime_type = request.getFile("titleFile")?.contentType
        log.debug("Uploaded content type: ${upload_mime_type}");
        def input_stream = request.getFile("titleFile")?.inputStream

        if (upload_mime_type == 'application/vnd.ms-excel') {
            attemptXLSLoad(pkg, input_stream);
        } else {
            attemptCSVLoad(pkg, input_stream);
        }

        redirect action: 'show', id: params.id
    }

    private def attemptXLSLoad(pkg, stream) {
        log.debug("attemptXLSLoad");
        HSSFWorkbook wb = new HSSFWorkbook(stream);
        HSSFSheet hssfSheet = wb.getSheetAt(0);

        attemptv1XLSLoad(pkg, hssfSheet);
    }

    private def attemptCSVLoad(pkg, stream) {
        log.debug("attemptCSVLoad");
        attemptv1CSVLoad(pkg, stream);
    }

    private def attemptv1XLSLoad(pkg, hssfSheet) {

        log.debug("attemptv1XLSLoad");
        def extracted = [:]
        extracted.rows = []

        int row_counter = 0;
        Iterator rowIterator = hssfSheet.rowIterator();
        while (rowIterator.hasNext()) {
            HSSFRow hssfRow = (HSSFRow) rowIterator.next();
            switch (row_counter++) {
                case 0:
                    break;
                case 1:
                    break;
                case 2:
                    // Record header row
                    log.debug("Header");
                    hssfRow.cellIterator().each { c ->
                        log.debug("Col: ${c.toString()}");
                    }
                    break;
                default:
                    // A real data row
                    def row_info = [
                            issn                    : hssfRow.getCell(0)?.toString(),
                            eissn                   : hssfRow.getCell(1)?.toString(),
                            date_first_issue_online : hssfRow.getCell(2)?.toString(),
                            num_first_volume_online : hssfRow.getCell(3)?.toString(),
                            num_first_issue_online  : hssfRow.getCell(4)?.toString(),
                            date_last_issue_online  : hssfRow.getCell(5)?.toString(),
                            date_first_volume_online: hssfRow.getCell(6)?.toString(),
                            date_first_issue_online : hssfRow.getCell(7)?.toString(),
                            embargo                 : hssfRow.getCell(8)?.toString(),
                            coverageDepth           : hssfRow.getCell(9)?.toString(),
                            coverageNote            : hssfRow.getCell(10)?.toString(),
                            platformUrl             : hssfRow.getCell(11)?.toString()
                    ]

                    extracted.rows.add(row_info);
                    log.debug("datarow: ${row_info}");
                    break;
            }
        }

        processExractedData(pkg, extracted);
    }

    private def attemptv1CSVLoad(pkg, stream) {
        log.debug("attemptv1CSVLoad");
        def extracted = [:]
        processExractedData(pkg, extracted);
    }

    private def processExractedData(pkg, extracted_data) {
        log.debug("processExractedData...");
        List old_title_list = [[title: [id: 667]], [title: [id: 553]], [title: [id: 19]]]
        List new_title_list = [[title: [id: 19]], [title: [id: 554]], [title: [id: 667]]]

        reconcile(old_title_list, new_title_list);
    }

    private def reconcile(old_title_list, new_title_list) {
        def title_list_comparator = new com.k_int.kbplus.utils.TitleComparator()
        Collections.sort(old_title_list, title_list_comparator)
        Collections.sort(new_title_list, title_list_comparator)

        Iterator i1 = old_title_list.iterator()
        Iterator i2 = new_title_list.iterator()

        def current_old_title = i1.hasNext() ? i1.next() : null;
        def current_new_title = i2.hasNext() ? i2.next() : null;

        while (current_old_title || current_new_title) {
            if (current_old_title == null) {
                // We have exhausted all old titles. Everything in the new title list must be newly added
                log.debug("Title added: ${current_new_title.title.id}");
                current_new_title = i2.hasNext() ? i2.next() : null;
            } else if (current_new_title == null) {
                // We have exhausted new old titles. Everything remaining in the old titles list must have been removed
                log.debug("Title removed: ${current_old_title.title.id}");
                current_old_title = i1.hasNext() ? i1.next() : null;
            } else {
                // Work out whats changed
                if (current_old_title.title.id == current_new_title.title.id) {
                    // This title appears in both old and new lists, it may be an updated
                    log.debug("title ${current_old_title.title.id} appears in both lists - possible update / unchanged");
                    current_old_title = i1.hasNext() ? i1.next() : null;
                    current_new_title = i2.hasNext() ? i2.next() : null;
                } else {
                    if (current_old_title.title.id > current_new_title.title.id) {
                        // The current old title id is greater than the current new title. This means that a new title must
                        // have been introduced into the new list with a lower title id than the one on the current list.
                        // hence, current_new_title.title.id is a new record. Consume it and move forwards.
                        log.debug("Title added: ${current_new_title.title.id}");
                        current_new_title = i2.hasNext() ? i2.next() : null;
                    } else {
                        // The current old title is less than the current new title. This indicates that the current_old_title
                        // must have been removed in the new list. Process it as a removal and continue.
                        log.debug("Title removed: ${current_old_title.title.id}");
                        current_old_title = i1.hasNext() ? i1.next() : null;
                    }
                }
            }
        }
    }

    def isEditable() {
        if (SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN, ROLE_PACKAGE_EDITOR')) {
            return true
        } else {
            return false
        }
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def addToSub() {
        Package pkg = Package.get(params.id)
        Subscription sub = Subscription.get(params.subid)
        boolean add_entitlements = params.addEntitlements == 'true'
        String baseUrl = ApiSource.findByTypAndActive(ApiSource.ApiTyp.GOKBAPI,true).baseUrl
        GlobalRecordSource source = GlobalRecordSource.findByUri("${baseUrl}/gokb/oai/packages")
        log.debug("addToSub. Global Record Source URL: " +source.baseUrl)
        globalSourceSyncService.source = source
        GPathResult packageRecord = globalSourceSyncService.fetchRecord(source.uri,'packages',[verb:'GetRecord',metadataPrefix:'gokb',identifier:pkg.gokbId])
        if(packageRecord && packageRecord.record?.header?.status?.text() != 'deleted') {
            pkg.addToSubscription(sub, add_entitlements)
            if(add_entitlements) {
                flash.message = message(code:'subscription.details.link.processingWithEntitlements')
                redirect controller: 'subscription', action: 'index', id: params.subid
            }
            else {
                flash.message = message(code:'subscription.details.link.processingWithoutEntitlements')
                redirect controller: 'subscription', action: 'addEntitlements', params: [id: params.subid, pkgfilter: pkg.gokbId]
            }
        }
        else {
            flash.error = message(code:'subscription.details.link.packageNotFound')
            redirect controller: 'subscription', action: 'linkPackage', id: params.subid
        }
    }


    @Secured(['ROLE_USER'])
    def notes() {
        Map<String, Object> result = [:]
        result.user = User.get(springSecurityService.principal.id)
        result.packageInstance = Package.get(params.id)
        result.editable = isEditable()
        result
    }

    @Secured(['ROLE_USER'])
    def tasks() {
        Map<String, Object> result = [:]
        result.user = User.get(springSecurityService.principal.id)
        result.packageInstance = Package.get(params.id)
        result.editable = isEditable()

        if (params.deleteId) {
            Task dTask = Task.get(params.deleteId)
            if (dTask && dTask.creator.id == result.user.id) {
                try {
                    flash.message = message(code: 'default.deleted.message', args: [message(code: 'task.label'), dTask.title])
                    dTask.delete(flush: true)
                }
                catch (Exception e) {
                    flash.message = message(code: 'default.not.deleted.message', args: [message(code: 'task.label'), params.deleteId])
                }
            }
        }

        int offset = params.offset ? Integer.parseInt(params.offset) : 0
        result.taskInstanceList = taskService.getTasksByResponsiblesAndObject(result.user, contextService.getOrg(), result.packageInstance)
        result.taskInstanceCount = result.taskInstanceList.size()
        result.taskInstanceList = taskService.chopOffForPageSize(result.taskInstanceList, result.user, offset)

        result.myTaskInstanceList = taskService.getTasksByCreatorAndObject(result.user,  result.packageInstance)
        result.myTaskInstanceCount = result.myTaskInstanceList.size()
        result.myTaskInstanceList = taskService.chopOffForPageSize(result.myTaskInstanceList, result.user, offset)

        log.debug(result.taskInstanceList.toListString())

        result
    }

    @Secured(['ROLE_USER'])
    def packageBatchUpdate() {

        Package packageInstance = Package.get(params.id)
        boolean showDeletedTipps = false

        if (SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN, ROLE_PACKAGE_EDITOR')) {
            showDeletedTipps = true
        }

        log.debug("packageBatchUpdate ${params}");

        SimpleDateFormat formatter = DateUtil.getSDF_NoTime()

        def bulk_fields = [
                [formProp: 'start_date', domainClassProp: 'startDate', type: 'date'],
                [formProp: 'start_volume', domainClassProp: 'startVolume'],
                [formProp: 'start_issue', domainClassProp: 'startIssue'],
                [formProp: 'end_date', domainClassProp: 'endDate', type: 'date'],
                [formProp: 'end_volume', domainClassProp: 'endVolume'],
                [formProp: 'end_issue', domainClassProp: 'endIssue'],
                [formProp: 'coverage_depth', domainClassProp: 'coverageDepth'],
                [formProp: 'coverage_note', domainClassProp: 'coverageNote'],
                [formProp: 'embargo', domainClassProp: 'embargo'],
                [formProp: 'delayedOA', domainClassProp: 'delayedOA', type: 'ref'],
                [formProp: 'hybridOA', domainClassProp: 'hybridOA', type: 'ref'],
                [formProp: 'payment', domainClassProp: 'payment', type: 'ref'],
                [formProp: 'hostPlatformURL', domainClassProp: 'hostPlatformURL'],
        ]


        if (params.BatchSelectedBtn == 'on') {
            log.debug("Apply batch changes - selected")
            params.filter = null //remove filters
            params.coverageNoteFilter = null
            params.startsBefore = null
            params.endsAfter = null
            params.each { p ->
                if (p.key.startsWith('_bulkflag.') && (p.value == 'on')) {
                    def tipp_id_to_edit = p.key.substring(10);
                    log.debug("row selected for bulk edit: ${tipp_id_to_edit}");
                    def tipp_to_bulk_edit = TitleInstancePackagePlatform.get(tipp_id_to_edit);
                    boolean changed = false

                    if (params.bulkOperation == 'edit') {
                        bulk_fields.each { bulk_field_defn ->
                            if (params["clear_${bulk_field_defn.formProp}"] == 'on') {
                                log.debug("Request to clear field ${bulk_field_defn.formProp}");
                                tipp_to_bulk_edit[bulk_field_defn.domainClassProp] = null
                                changed = true
                            } else {
                                def proposed_value = params['bulk_' + bulk_field_defn.formProp]
                                if ((proposed_value != null) && (proposed_value.length() > 0)) {
                                    log.debug("Set field ${bulk_field_defn.formProp} to ${proposed_value}");
                                    if (bulk_field_defn.type == 'date') {
                                        tipp_to_bulk_edit[bulk_field_defn.domainClassProp] = formatter.parse(proposed_value)
                                    } else if (bulk_field_defn.type == 'ref') {
                                        tipp_to_bulk_edit[bulk_field_defn.domainClassProp] = genericOIDService.resolveOID(proposed_value)
                                    } else {
                                        tipp_to_bulk_edit[bulk_field_defn.domainClassProp] = proposed_value
                                    }
                                    changed = true
                                }
                            }
                        }
                        if (changed)
                            tipp_to_bulk_edit.save(flush: true)
                    } else {
                        log.debug("Bulk removal ${tipp_to_bulk_edit.id}");
                        tipp_to_bulk_edit.status = RefdataValue.getByValueAndCategory('Deleted', RDConstants.TIPP_STATUS)
                        tipp_to_bulk_edit.save(flush: true)
                    }
                }
            }
        } else if (params.BatchAllBtn == 'on') {
            log.debug("Batch process all filtered by: " + params.filter);
            def qry_params = [pkgInstance: packageInstance]
            def query = filterService.generateBasePackageQuery(params, qry_params, showDeletedTipps, new Date(),"Package")
            def tipplist = TitleInstancePackagePlatform.executeQuery("select tipp " + query.base_qry, query.qry_params)
            tipplist.each { tipp_to_bulk_edit ->
                boolean changed = false
                log.debug("update tipp ${tipp_to_bulk_edit.id}");
                if (params.bulkOperation == 'edit') {
                    bulk_fields.each { bulk_field_defn ->
                        if (params["clear_${bulk_field_defn.formProp}"] == 'on') {
                            log.debug("Request to clear field ${bulk_field_defn.formProp}");
                            tipp_to_bulk_edit[bulk_field_defn.domainClassProp] = null
                            changed = true
                        } else {
                            def proposed_value = params['bulk_' + bulk_field_defn.formProp]
                            if ((proposed_value != null) && (proposed_value.length() > 0)) {
                                log.debug("Set field ${bulk_field_defn.formProp} to proposed_value");
                                if (bulk_field_defn.type == 'date') {
                                    tipp_to_bulk_edit[bulk_field_defn.domainClassProp] = formatter.parse(proposed_value)
                                } else if (bulk_field_defn.type == 'ref') {
                                    tipp_to_bulk_edit[bulk_field_defn.domainClassProp] = genericOIDService.resolveOID(proposed_value)
                                } else {
                                    tipp_to_bulk_edit[bulk_field_defn.domainClassProp] = proposed_value
                                }
                                changed = true
                            }
                        }
                    }
                    if (changed)
                        tipp_to_bulk_edit.save(flush: true)
                }
            }
        }

        redirect(action: 'show', params: [id: params.id, sort: params.sort, order: params.order, max: params.max, offset: params.offset]);
    }

    @Secured(['ROLE_USER'])
    def history() {
        Map<String, Object> result = [:]
        def exporting = params.format == 'csv' ? true : false

        if (exporting) {
            result.max = 9999999
            params.max = 9999999
            result.offset = 0
        } else {
            User user = User.get(springSecurityService.principal.id)
            result.max = params.max ? Integer.parseInt(params.max) : user.getDefaultPageSizeAsInteger()
            params.max = result.max
            result.offset = params.offset ? Integer.parseInt(params.offset) : 0;
        }

    result.packageInstance = Package.get(params.id)
    result.editable=isEditable()

      def limits = (!params.format||params.format.equals("html"))?[max:result.max, offset:result.offset]:[offset:0]

        // postgresql migration
        String subQuery = 'select cast(id as string) from TitleInstancePackagePlatform as tipp where tipp.pkg = cast(:pkgid as int)'
        def subQueryResult = TitleInstancePackagePlatform.executeQuery(subQuery, [pkgid: params.id])

        //def base_query = 'from org.codehaus.groovy.grails.plugins.orm.auditable.AuditLogEvent as e where ( e.className = :pkgcls and e.persistedObjectId = cast(:pkgid as string)) or ( e.className = :tippcls and e.persistedObjectId in ( select id from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkgid ) )'
        //def query_params = [ pkgcls: Package.class.name, tippcls: TitleInstancePackagePlatform.class.name, pkgid: params.id, subQueryResult: subQueryResult ]

        String base_query   = 'from org.codehaus.groovy.grails.plugins.orm.auditable.AuditLogEvent as e where ( e.className = :pkgcls and e.persistedObjectId = cast(:pkgid as string))'
        def query_params = [ pkgcls: Package.class.name, pkgid: params.id]

      // postgresql migration
        if (subQueryResult) {
            base_query += ' or ( e.className = :tippcls and e.persistedObjectId in (:subQueryResult) )'
            query_params.'tippcls' = TitleInstancePackagePlatform.class.name
            query_params.'subQueryResult' = subQueryResult
        }


        log.debug("base_query: ${base_query}, params:${query_params}, limits:${limits}");

    result.historyLines = AuditLogEvent.executeQuery('select e ' + base_query + ' order by e.lastUpdated desc', query_params, limits);
    result.num_hl = AuditLogEvent.executeQuery('select e.id '+ base_query, query_params).size()
    result.formattedHistoryLines = []


        result.historyLines.each { hl ->

            def line_to_add = [:]
            def linetype = null

            switch (hl.className) {
                case Package.class.name :
                    Package package_object = Package.get(hl.persistedObjectId);
                    line_to_add = [link        : createLink(controller: 'package', action: 'show', id: hl.persistedObjectId),
                                   name        : package_object.toString(),
                                   lastUpdated : hl.lastUpdated,
                                   propertyName: hl.propertyName,
                                   actor       : User.findByUsername(hl.actor),
                                   oldValue    : hl.oldValue,
                                   newValue    : hl.newValue
                    ]
                    linetype = 'Package'
                    break;
                case TitleInstancePackagePlatform.class.name :
                    TitleInstancePackagePlatform tipp_object = TitleInstancePackagePlatform.get(hl.persistedObjectId);
                    if (tipp_object != null) {
                        line_to_add = [link        : createLink(controller: 'tipp', action: 'show', id: hl.persistedObjectId),
                                       name        : tipp_object.title?.title + " / " + tipp_object.pkg?.name,
                                       lastUpdated : hl.lastUpdated,
                                       propertyName: hl.propertyName,
                                       actor       : User.findByUsername(hl.actor),
                                       oldValue    : hl.oldValue,
                                       newValue    : hl.newValue
                        ]
                        linetype = 'TIPP'
                    } else {
                        log.debug("Cleaning up history line that relates to a deleted item");
                        hl.delete(flush:true)
                    }
            }
            switch (hl.eventName) {
                case 'INSERT':
                    line_to_add.eventName = "New ${linetype}"
                    break;
                case 'UPDATE':
                    line_to_add.eventName = "Updated ${linetype}"
                    break;
                case 'DELETE':
                    line_to_add.eventName = "Deleted ${linetype}"
                    break;
                default:
                    line_to_add.eventName = "Unknown ${linetype}"
                    break;
            }
            result.formattedHistoryLines.add(line_to_add);
        }

        result
    }

    //for that no accidental call may occur ... ROLE_YODA is correct!
    @Secured(['ROLE_YODA'])
    Map getDuplicatePackages() {
        yodaService.listDuplicatePackages()
    }

    @Secured(['ROLE_YODA'])
    def purgeDuplicatePackages() {
        List<Long> toDelete = (List<Long>) JSON.parse(params.toDelete)
        if(params.doIt == "true") {
            yodaService.executePackageCleanup(toDelete)
            redirect action: 'index'
        }
        else {
            flash.message = "Betroffene Paket-IDs wären gelöscht worden: ${toDelete.join(", ")}"
            redirect action: 'getDuplicatePackages'
        }
    }
}
