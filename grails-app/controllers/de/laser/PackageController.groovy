package de.laser

import com.k_int.kbplus.ExecutorWrapperService
import de.laser.auth.User
import de.laser.exceptions.CreationException
import de.laser.helper.DateUtils
import de.laser.annotations.DebugAnnotation
import de.laser.helper.RDConstants
import de.laser.helper.RDStore
import de.laser.helper.SwissKnife
import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.annotation.Secured
import groovy.util.slurpersupport.GPathResult
import org.apache.poi.hssf.usermodel.HSSFRow
import org.apache.poi.hssf.usermodel.HSSFSheet
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.codehaus.groovy.grails.plugins.orm.auditable.AuditLogEvent
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder

import javax.servlet.ServletOutputStream
import java.text.SimpleDateFormat

@Secured(['IS_AUTHENTICATED_FULLY'])
class PackageController {

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
    def gokbService
    def filterService
    EscapeService escapeService
    MessageSource messageSource

    static allowedMethods = [create: ['GET', 'POST'], edit: ['GET', 'POST'], delete: 'POST']

    //Data from GOKB ES
    @Secured(['ROLE_USER'])
    def index() {

        ApiSource apiSource = ApiSource.findByTypAndActive(ApiSource.ApiTyp.GOKBAPI, true)
        if (!apiSource) {
            redirect controller: 'package', action: 'list'
            return
        }
        Map<String, Object> result = [:]
        result.user = contextService.getUser()
        SwissKnife.setPaginationParams(result, params, result.user)

        result.editUrl = apiSource.baseUrl + apiSource.fixToken

        String esQuery = "?componentType=Package"
        if (params.q) {
            result.filterSet = true
            //for ElasticSearch
            esQuery += "&name=${params.q}"
            //the result set has to be broadened down by IdentifierNamespace queries! Problematic if the package is not in LAS:eR yet!
        }

        if (params.provider) {
            result.filterSet = true
            esQuery += "&provider=${params.provider}"
        }

        if (params.curatoryGroup) {
            result.filterSet = true
            esQuery += "&curatoryGroup=${params.curatoryGroup}"
        }

        if (params.resourceTyp) {
            result.filterSet = true
            esQuery += "&contentType=${params.resourceTyp}"
        }


        /*
        to implement:
        - provider
        - componentType
        - series
        - subjectArea
        - curatoryGroup
        - year (combination of dateFirstPrint and dateFirstOnline)
         */

        String sort = params.sort ? "&sort="+params.sort: "&sort=sortname"
        String order = params.order ? "&order="+params.order: "&order=asc"
        String max = params.max ? "&max=${params.max}" : "&max=${result.max}"
        String offset = params.offset ? "&offset=${params.offset}" : "&offset=${result.offset}"

        Map queryCuratoryGroups = gokbService.queryElasticsearch(apiSource.baseUrl + apiSource.fixToken + '/groups')
        if (queryCuratoryGroups.warning) {
            List recordsCuratoryGroups = queryCuratoryGroups.warning.result
            result.curatoryGroups = recordsCuratoryGroups?.findAll {it.status == "Current"}
        }


        Map queryResult = gokbService.queryElasticsearch(apiSource.baseUrl + apiSource.fixToken + '/find' + esQuery + sort + order + max + offset)
        if (queryResult.warning) {
            List records = queryResult.warning.records
            result.recordsCount = queryResult.warning.count
            result.records = records
        }

        result
    }

    @Secured(['ROLE_USER'])
    def list() {
        Map<String, Object> result = [:]
        result.user = contextService.getUser()
        result.editable = true

        SwissKnife.setPaginationParams(result, params, (User) result.user)

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
        result.packageInstanceTotal = Subscription.executeQuery("select p.id " + base_qry, qry_params).size()


        withFormat {
            html {
                result.packageInstanceList = Subscription.executeQuery("select p " + base_qry, qry_params, [max: result.max, offset: result.offset])
                result
            }
            csv {
                response.setHeader("Content-disposition", "attachment; filename=\"packages.csv\"")
                response.contentType = "text/csv"
                def packages = Subscription.executeQuery("select p " + base_qry, qry_params)
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

    @DebugAnnotation(perm = "ORG_INST,ORG_CONSORTIUM", affil = "INST_USER")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_USER")
    })
    def compare() {
        Map<String, Object> result = [:]
        result.unionList = []

        result.user = contextService.getUser()
        SwissKnife.setPaginationParams(result, params, (User) result.user)

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

            def groupedA = listA.groupBy({ it.name })
            def groupedB = listB.groupBy({ it.name })

            def mapA = listA.collectEntries { [it.name, it] }
            def mapB = listB.collectEntries { [it.name, it] }

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
                        SimpleDateFormat dateFormatter = DateUtils.getSDF_NoTime()
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
                                def pissn = tippA ? tippA.getIdentifierValue('issn') : tippB.getIdentifierValue('issn');
                                def eissn = tippA ? tippA.getIdentifierValue('eISSN') : tippB.getIdentifierValue('eISSN');

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
            SimpleDateFormat sdf = DateUtils.getSDF_NoTime()
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
        return (date ? formatter.format(date) : '')
    }

    private def createCompareList(pkg, dateStr, params, result) {

        SimpleDateFormat sdf = DateUtils.getSDF_NoTime()
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
        Map<String, Object> result = [:]

        result.user = contextService.getUser()
        Package packageInstance = Package.get(params.id)
        if (!packageInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'package.label'), params.id])
            redirect action: 'index'
            return
        }

        result.currentTippsCounts = TitleInstancePackagePlatform.executeQuery("select count(tipp) from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkg and tipp.status = :status", [pkg: packageInstance, status: RDStore.TIPP_STATUS_CURRENT])[0]
        result.plannedTippsCounts = TitleInstancePackagePlatform.executeQuery("select count(tipp) from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkg and tipp.status = :status", [pkg: packageInstance, status: RDStore.TIPP_STATUS_EXPECTED])[0]
        result.expiredTippsCounts = TitleInstancePackagePlatform.executeQuery("select count(tipp) from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkg and tipp.status = :status", [pkg: packageInstance, status: RDStore.TIPP_STATUS_RETIRED])[0]
        result.deletedTippsCounts = TitleInstancePackagePlatform.executeQuery("select count(tipp) from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkg and tipp.status = :status", [pkg: packageInstance, status: RDStore.TIPP_STATUS_DELETED])[0]
        result.contextOrg = contextService.getOrg()
        result.contextCustomerType = result.contextOrg.getCustomerType()

        // tasks
        /*
        result.tasks = taskService.getTasksByResponsiblesAndObject(contextService.getUser(), result.contextOrg, packageInstance)
        Map<String,Object> preCon = taskService.getPreconditionsWithoutTargets(result.contextOrg)
        result << preCon*/

        result.modalPrsLinkRole = RefdataValue.getByValueAndCategory('Specific package editor', RDConstants.PERSON_RESPONSIBILITY)
        result.modalVisiblePersons = addressbookService.getPrivatePersonsByTenant(result.contextOrg)

        // restrict visible for templates/links/orgLinksAsList
        result.visibleOrgs = packageInstance.orgs
        //result.visibleOrgs.sort { it.org.sortname }

        List<RefdataValue> roleTypes = [RDStore.OR_SUBSCRIBER]
        if (accessService.checkPerm('ORG_CONSORTIUM')) {
            roleTypes.addAll([RDStore.OR_SUBSCRIPTION_CONSORTIA, RDStore.OR_SUBSCRIBER_CONS])
        }

        SwissKnife.setPaginationParams(result, params, (User) result.user)
        params.max = result.max

        SimpleDateFormat sdf = DateUtils.getSDF_NoTime()
        Date today = new Date()
        if (!params.asAt) {
            if (packageInstance.startDate > today) {
                params.asAt = sdf.format(packageInstance.startDate)
            } else if (packageInstance.endDate < today && packageInstance.endDate) {
                params.asAt = sdf.format(packageInstance.endDate)
            }
        }

        result.lasttipp = result.offset + result.max > result.num_tipp_rows ? result.num_tipp_rows : result.offset + result.max

        if (OrgSetting.get(result.contextOrg, OrgSetting.KEYS.NATSTAT_SERVER_REQUESTOR_ID) instanceof OrgSetting) {
            result.statsWibid = result.contextOrg.getIdentifierByType('wibid')?.value
            result.usageMode = accessService.checkPerm("ORG_CONSORTIUM") ? 'package' : 'institution'
            result.packageIdentifier = packageInstance.getIdentifierByType('isil')?.value
        }

        result.packageInstance = packageInstance

        ApiSource apiSource = ApiSource.findByTypAndActive(ApiSource.ApiTyp.GOKBAPI, true)

        String esQuery = "?componentType=Package&uuid=${packageInstance.gokbId}"

        Map queryResult = gokbService.queryElasticsearch(apiSource.baseUrl + apiSource.fixToken + '/find' + esQuery)
        if (queryResult.warning) {
            List records = queryResult.warning.records
            result.packageInstanceRecord = records ? records[0] : [:]
        }

        result
    }

    @Secured(['ROLE_USER'])
    def current() {
        log.debug("current ${params}");
        Map<String, Object> result = [:]
        result.user = contextService.getUser()
        result.editable = isEditable()
        result.contextOrg = contextService.getOrg()
        result.contextCustomerType = result.contextOrg.getCustomerType()

        Package packageInstance = Package.get(params.id)
        if (!packageInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'package.label'), params.id])
            redirect action: 'index'
            return
        }
        result.packageInstance = packageInstance

        result.currentTippsCounts = TitleInstancePackagePlatform.executeQuery("select count(tipp) from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkg and tipp.status = :status", [pkg: packageInstance, status: RDStore.TIPP_STATUS_CURRENT])[0]
        result.plannedTippsCounts = TitleInstancePackagePlatform.executeQuery("select count(tipp) from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkg and tipp.status = :status", [pkg: packageInstance, status: RDStore.TIPP_STATUS_EXPECTED])[0]
        result.expiredTippsCounts = TitleInstancePackagePlatform.executeQuery("select count(tipp) from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkg and tipp.status = :status", [pkg: packageInstance, status: RDStore.TIPP_STATUS_RETIRED])[0]
        result.deletedTippsCounts = TitleInstancePackagePlatform.executeQuery("select count(tipp) from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkg and tipp.status = :status", [pkg: packageInstance, status: RDStore.TIPP_STATUS_DELETED])[0]
        if (executorWrapperService.hasRunningProcess(packageInstance)) {
            result.processingpc = true
        }

        result.pendingChanges = PendingChange.executeQuery(
                "select pc from PendingChange as pc where pc.pkg = :pkg and ( pc.status is null or pc.status = :status ) order by ts, payload",
                [pkg: packageInstance, status: RDStore.PENDING_CHANGE_PENDING]
        )

        SwissKnife.setPaginationParams(result, params, (User) result.user)

        Map<String, Object> query = filterService.getTippQuery(params, [packageInstance])
        result.filterSet = query.filterSet

        List<TitleInstancePackagePlatform> titlesList = TitleInstancePackagePlatform.executeQuery("select tipp " + query.query, query.queryParams)

        String filename = "${escapeService.escapeString(packageInstance.name+'_'+message(code: 'package.show.nav.current'))}_${DateUtils.SDF_NoTimeNoPoint.format(new Date())}"

        if (params.exportKBart) {
            response.setHeader("Content-disposition", "attachment; filename=${filename}.tsv")
            response.contentType = "text/tsv"
            ServletOutputStream out = response.outputStream
            Map<String,List> tableData = exportService.generateTitleExportKBART(titlesList)
            out.withWriter { writer ->
                writer.write(exportService.generateSeparatorTableString(tableData.titleRow, tableData.columnData, '\t'))
            }
            out.flush()
            out.close()
        } else if (params.exportXLSX) {
            response.setHeader("Content-disposition", "attachment; filename=\"${filename}.xlsx\"")
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            Map<String,List> export = exportService.generateTitleExportXLS(titlesList)
            Map sheetData = [:]
            sheetData[message(code: 'title.plural')] = [titleRow: export.titles, columnData: export.rows]
            SXSSFWorkbook workbook = exportService.generateXLSXWorkbook(sheetData)
            workbook.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            workbook.dispose()
        }
        withFormat {
            html {

                result.titlesList = titlesList.drop(result.offset).take(result.max)
                result.num_tipp_rows = titlesList.size()

                result.lasttipp = result.offset + result.max > result.num_tipp_rows ? result.num_tipp_rows : result.offset + result.max
                result
            }
            csv {
                response.setHeader("Content-disposition", "attachment; filename=${filename}.csv")
                response.contentType = "text/csv"

                ServletOutputStream out = response.outputStream
                Map<String, List> tableData = exportService.generateTitleExportCSV(titlesList)
                out.withWriter { writer ->
                    writer.write(exportService.generateSeparatorTableString(tableData.titleRow, tableData.rows, ';'))
                }
                out.flush()
                out.close()
            }
        }
    }

    @Deprecated
    @Secured(['ROLE_ADMIN'])
    def deleteDocuments() {
        def ctxlist = []

        log.debug("deleteDocuments ${params}");

        docstoreService.unifiedDeleteDocuments(params)

        redirect controller: 'package', action: params.redirectAction, id: params.instanceId
    }

    @Deprecated
    @Secured(['ROLE_ADMIN'])
    def documents() {
        Map<String, Object> result = [:]
        result.user = contextService.getUser()
        result.contextOrg = contextService.getOrg()
        result.contextCustomerType = result.contextOrg.getCustomerType()
        result.institution = result.contextOrg
        result.packageInstance = Package.get(params.id)
        result.editable = isEditable()

        result
    }

    @Secured(['ROLE_USER'])
    def planned() {
        planned_expired_deleted(params, "planned")
    }

    @Secured(['ROLE_USER'])
    def expired() {
        planned_expired_deleted(params, "expired")
    }

    @Secured(['ROLE_USER'])
    def deleted() {
        planned_expired_deleted(params, "deleted")
    }

    @Secured(['ROLE_USER'])
    def planned_expired_deleted(params, func) {
        log.debug("planned_expired_deleted ${params}");
        Map<String, Object> result = [:]
        result.user = contextService.getUser()
        result.editable = isEditable()
        result.contextOrg = contextService.getOrg()
        result.contextCustomerType = result.contextOrg.getCustomerType()

        Package packageInstance = Package.get(params.id)
        if (!packageInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'package.label'), params.id])
            redirect action: 'index'
            return
        }
        result.packageInstance = packageInstance

        result.currentTippsCounts = TitleInstancePackagePlatform.executeQuery("select count(tipp) from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkg and tipp.status = :status", [pkg: packageInstance, status: RDStore.TIPP_STATUS_CURRENT])[0]
        result.plannedTippsCounts = TitleInstancePackagePlatform.executeQuery("select count(tipp) from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkg and tipp.status = :status", [pkg: packageInstance, status: RDStore.TIPP_STATUS_EXPECTED])[0]
        result.expiredTippsCounts = TitleInstancePackagePlatform.executeQuery("select count(tipp) from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkg and tipp.status = :status", [pkg: packageInstance, status: RDStore.TIPP_STATUS_RETIRED])[0]
        result.deletedTippsCounts = TitleInstancePackagePlatform.executeQuery("select count(tipp) from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkg and tipp.status = :status", [pkg: packageInstance, status: RDStore.TIPP_STATUS_DELETED])[0]

        SwissKnife.setPaginationParams(result, params, (User) result.user)

        def limits = (!params.format || params.format.equals("html")) ? [max: result.max, offset: result.offset] : [offset: 0]

        String filename
        if (func == "planned") {
            params.status = RDStore.TIPP_STATUS_EXPECTED.id
            filename = "${escapeService.escapeString(packageInstance.name+'_'+message(code: 'package.show.nav.planned'))}_${DateUtils.SDF_NoTimeNoPoint.format(new Date())}"
        } else if (func == "expired"){
            params.status = RDStore.TIPP_STATUS_RETIRED.id
            filename = "${escapeService.escapeString(packageInstance.name+'_'+message(code: 'package.show.nav.expired'))}_${DateUtils.SDF_NoTimeNoPoint.format(new Date())}"
        }
        else if (func == "deleted"){
            params.status = RDStore.TIPP_STATUS_DELETED.id
            filename = "${escapeService.escapeString(packageInstance.name+'_'+message(code: 'package.show.nav.deleted'))}_${DateUtils.SDF_NoTimeNoPoint.format(new Date())}"
        }

        Map<String, Object> query = filterService.getTippQuery(params, [packageInstance])
        result.filterSet = query.filterSet
        //println(query)

        List<TitleInstancePackagePlatform> titlesList = TitleInstancePackagePlatform.executeQuery("select tipp " + query.query, query.queryParams)

        if (params.exportKBart) {
            response.setHeader("Content-disposition", "attachment; filename=${filename}.tsv")
            response.contentType = "text/tsv"
            ServletOutputStream out = response.outputStream
            Map<String,List> tableData = exportService.generateTitleExportKBART(titlesList)
            out.withWriter { writer ->
                writer.write(exportService.generateSeparatorTableString(tableData.titleRow, tableData.columnData, '\t'))
            }
            out.flush()
            out.close()
        } else if (params.exportXLSX) {
            response.setHeader("Content-disposition", "attachment; filename=\"${filename}.xlsx\"")
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            Map<String,List> export = exportService.generateTitleExportXLS(titlesList)
            Map sheetData = [:]
            sheetData[message(code: 'title.plural')] = [titleRow: export.titles, columnData: export.rows]
            SXSSFWorkbook workbook = exportService.generateXLSXWorkbook(sheetData)
            workbook.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            workbook.dispose()
        }
        withFormat {
            html {

                result.titlesList = titlesList.drop(result.offset).take(result.max)
                result.num_tipp_rows = titlesList.size()

                result.lasttipp = result.offset + result.max > result.num_tipp_rows ? result.num_tipp_rows : result.offset + result.max
                result
            }
            csv {
                response.setHeader("Content-disposition", "attachment; filename=${filename}.csv")
                response.contentType = "text/csv"

                ServletOutputStream out = response.outputStream
                Map<String, List> tableData = exportService.generateTitleExportCSV(titlesList)
                out.withWriter { writer ->
                    writer.write(exportService.generateSeparatorTableString(tableData.titleRow, tableData.rows, ';'))
                }
                out.flush()
                out.close()
            }
        }
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
        SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN, ROLE_PACKAGE_EDITOR')
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.contextService.getUser()?.hasAffiliation("INST_EDITOR") })
    def processLinkToSub() {
        Map<String, Object> result = [:]
        result.pkg = Package.get(params.id)
        result.subscription = genericOIDService.resolveOID(params.targetObjectId)

        if(result.subscription) {
            Locale locale = LocaleContextHolder.getLocale()
            Set<Thread> threadSet = Thread.getAllStackTraces().keySet()
            Thread[] threadArray = threadSet.toArray(new Thread[threadSet.size()])
            threadArray.each { Thread thread ->
                if (thread.name == 'PackageSync_' + result.subscription.id && !SubscriptionPackage.findBySubscriptionAndPkg(result.subscription, result.pkg)) {
                    result.message = messageSource.getMessage('subscription.details.linkPackage.thread.running', null, locale)
                }
            }
            //to be deployed in parallel thread
            if (result.pkg) {
                String addType = params.addType
                log.debug("Add package ${addType} entitlements to subscription ${result.subscription}")
                if (addType == 'With') {
                    result.pkg.addToSubscription(result.subscription, true)
                } else if (addType == 'Without') {
                    result.pkg.addToSubscription(result.subscription, false)
                }

                if (addType != null && addType != '') {
                    SubscriptionPackage subscriptionPackage = SubscriptionPackage.findBySubscriptionAndPkg(result.subscription, result.pkg)
                    if (subscriptionPackage) {
                        PendingChangeConfiguration.SETTING_KEYS.each { String settingKey ->
                            Map<String, Object> configMap = [subscriptionPackage: subscriptionPackage, settingKey: settingKey, withNotification: false]
                            boolean auditable = false
                            //Set because we have up to three keys in params with the settingKey
                            Set<String> keySettings = params.keySet().findAll { k -> k.contains(settingKey) }
                            keySettings.each { key ->
                                List<String> settingData = key.split('!§!')
                                switch (settingData[1]) {
                                    case 'setting': configMap.settingValue = RefdataValue.get(params[key])
                                        break
                                    case 'notification': configMap.withNotification = params[key] != null
                                        break
                                    case 'auditable': auditable = params[key] != null
                                        break
                                }
                            }
                            try {
                                PendingChangeConfiguration.construct(configMap)
                                boolean hasConfig = AuditConfig.getConfig(subscriptionPackage.subscription, settingKey) != null
                                if (auditable && !hasConfig) {
                                    AuditConfig.addConfig(subscriptionPackage.subscription, settingKey)
                                } else if (!auditable && hasConfig) {
                                    AuditConfig.removeConfig(subscriptionPackage.subscription, settingKey)
                                }
                            }
                            catch (CreationException e) {
                                log.error("ProcessLinkPackage -> PendingChangeConfiguration: " + e.message)
                            }
                        }
                    }
                }
            }
            switch (params.addType) {
                case "With": flash.message = message(code: 'subscription.details.link.processingWithEntitlements')
                    redirect controller: 'subscription', action: 'index', params: [id: result.subscription.id, gokbId: result.pkg.gokbId]
                    return
                    break
                case "Without": flash.message = message(code: 'subscription.details.link.processingWithoutEntitlements')
                    redirect controller: 'subscription', action: 'addEntitlements', params: [id: result.subscription.id, packageLinkPreselect: result.pkg.gokbId, preselectedName: result.pkg.name]
                    return
                    break
            }
        }else {
            flash.error = message(code: 'package.show.linkToSub.noSubSelection')
            redirect controller: 'package', action: 'show', params: [id: params.id]
            return
        }

        redirect(url: request.getHeader("referer"))
    }


    @Secured(['ROLE_ADMIN'])
    def notes() {
        Map<String, Object> result = [:]
        result.user = contextService.getUser()
        result.contextOrg = contextService.getOrg()
        result.contextCustomerType = result.contextOrg.getCustomerType()
        result.packageInstance = Package.get(params.id)
        result.editable = isEditable()
        result
    }

    @Secured(['ROLE_ADMIN'])
    @Transactional
    def tasks() {
        Map<String, Object> result = [:]
        result.user = contextService.getUser()
        result.contextOrg = contextService.getOrg()
        result.contextCustomerType = result.contextOrg.getCustomerType()
        result.packageInstance = Package.get(params.id)
        result.editable = isEditable()

        if (params.deleteId) {
            Task dTask = Task.get(params.deleteId)
            if (dTask && dTask.creator.id == result.user.id) {
                try {
                    flash.message = message(code: 'default.deleted.message', args: [message(code: 'task.label'), dTask.title])
                    dTask.delete()
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

        result.myTaskInstanceList = taskService.getTasksByCreatorAndObject(result.user, result.packageInstance)
        result.myTaskInstanceCount = result.myTaskInstanceList.size()
        result.myTaskInstanceList = taskService.chopOffForPageSize(result.myTaskInstanceList, result.user, offset)

        log.debug(result.taskInstanceList.toListString())

        result
    }

    @Secured(['ROLE_ADMIN'])
    @Transactional
    def history() {
        Map<String, Object> result = [:]
        def exporting = params.format == 'csv' ? true : false

        if (exporting) {
            result.max = 9999999
            params.max = 9999999
            result.offset = 0
        } else {
            User user = contextService.getUser()
            SwissKnife.setPaginationParams(result, params, user)
            params.max = result.max
        }

        result.packageInstance = Package.get(params.id)
        result.editable = isEditable()

        def limits = (!params.format || params.format.equals("html")) ? [max: result.max, offset: result.offset] : [offset: 0]

        // postgresql migration
        String subQuery = 'select cast(id as string) from TitleInstancePackagePlatform as tipp where tipp.pkg = cast(:pkgid as int)'
        def subQueryResult = TitleInstancePackagePlatform.executeQuery(subQuery, [pkgid: params.id])

        //def base_query = 'from org.codehaus.groovy.grails.plugins.orm.auditable.AuditLogEvent as e where ( e.className = :pkgcls and e.persistedObjectId = cast(:pkgid as string)) or ( e.className = :tippcls and e.persistedObjectId in ( select id from TitleInstancePackagePlatform as tipp where tipp.pkg = :pkgid ) )'
        //def query_params = [ pkgcls: Package.class.name, tippcls: TitleInstancePackagePlatform.class.name, pkgid: params.id, subQueryResult: subQueryResult ]

        String base_query = 'from org.codehaus.groovy.grails.plugins.orm.auditable.AuditLogEvent as e where ( e.className = :pkgcls and e.persistedObjectId = cast(:pkgid as string))'
        def query_params = [pkgcls: Package.class.name, pkgid: params.id]

        // postgresql migration
        if (subQueryResult) {
            base_query += ' or ( e.className = :tippcls and e.persistedObjectId in (:subQueryResult) )'
            query_params.'tippcls' = TitleInstancePackagePlatform.class.name
            query_params.'subQueryResult' = subQueryResult
        }


        log.debug("base_query: ${base_query}, params:${query_params}, limits:${limits}");

        result.historyLines = AuditLogEvent.executeQuery('select e ' + base_query + ' order by e.lastUpdated desc', query_params, limits);
        result.num_hl = AuditLogEvent.executeQuery('select e.id ' + base_query, query_params).size()
        result.formattedHistoryLines = []


        result.historyLines.each { hl ->

            def line_to_add = [:]
            def linetype = null

            switch (hl.className) {
                case Package.class.name:
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
                case TitleInstancePackagePlatform.class.name:
                    TitleInstancePackagePlatform tipp_object = TitleInstancePackagePlatform.get(hl.persistedObjectId);
                    if (tipp_object != null) {
                        line_to_add = [link        : createLink(controller: 'tipp', action: 'show', id: hl.persistedObjectId),
                                       name        : tipp_object.name + " / " + tipp_object.pkg?.name,
                                       lastUpdated : hl.lastUpdated,
                                       propertyName: hl.propertyName,
                                       actor       : User.findByUsername(hl.actor),
                                       oldValue    : hl.oldValue,
                                       newValue    : hl.newValue
                        ]
                        linetype = 'TIPP'
                    } else {
                        log.debug("Cleaning up history line that relates to a deleted item");
                        hl.delete()
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
        if (params.doIt == "true") {
            yodaService.executePackageCleanup(toDelete)
            redirect action: 'index'
        } else {
            flash.message = "Betroffene Paket-IDs wären gelöscht worden: ${toDelete.join(", ")}"
            redirect action: 'getDuplicatePackages'
        }
    }
}
