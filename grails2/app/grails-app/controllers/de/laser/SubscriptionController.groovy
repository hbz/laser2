package de.laser

import de.laser.titles.BookInstance
import com.k_int.kbplus.ExecutorWrapperService
import com.k_int.kbplus.GlobalSourceSyncService
import de.laser.titles.JournalInstance
import de.laser.properties.PlatformProperty
import de.laser.properties.SubscriptionProperty
import de.laser.titles.TitleInstance
import com.k_int.kbplus.auth.User
import com.k_int.kbplus.traits.PendingChangeControllerTrait
import de.laser.base.AbstractPropertyWithCalculatedLastUpdated
import de.laser.controller.AbstractDebugController
import de.laser.exceptions.CreationException
import de.laser.exceptions.EntitlementCreationException
import de.laser.finance.CostItem
import de.laser.finance.PriceItem
import de.laser.helper.*
import de.laser.interfaces.CalculatedType
import de.laser.properties.PropertyDefinition
import grails.converters.JSON
import grails.doc.internal.StringEscapeCategory
import grails.plugin.springsecurity.annotation.Secured
import groovy.time.TimeCategory
import groovy.util.slurpersupport.GPathResult
import org.apache.poi.POIXMLProperties
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.codehaus.groovy.grails.plugins.orm.auditable.AuditLogEvent
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.codehaus.groovy.runtime.InvokerHelper
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.multipart.commons.CommonsMultipartFile

import javax.servlet.ServletOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.concurrent.ExecutorService

@Secured(['IS_AUTHENTICATED_FULLY'])
class SubscriptionController
        extends AbstractDebugController
        implements PendingChangeControllerTrait {

    def springSecurityService
    def contextService
    def addressbookService
    def taskService
    def genericOIDService
    def exportService
    def pendingChangeService
    ExecutorService executorService
    ExecutorWrapperService executorWrapperService
    def renewals_reversemap = ['subject': 'subject', 'provider': 'provid', 'pkgname': 'tokname']
    def accessService
    def filterService
    def propertyService
    def factService
    def docstoreService
    GlobalSourceSyncService globalSourceSyncService
    def GOKbService
    def linksGenerationService
    def financeService
    def orgTypeService
    def subscriptionService
    def escapeService
    def deletionService
    def auditService
    def surveyService
    FormService formService
    AccessPointService accessPointService
    CopyElementsService copyElementsService

    def possible_date_formats = [
            new SimpleDateFormat('yyyy/MM/dd'),
            new SimpleDateFormat('dd.MM.yyyy'),
            new SimpleDateFormat('dd/MM/yyyy'),
            new SimpleDateFormat('dd/MM/yy'),
            new SimpleDateFormat('yyyy/MM'),
            new SimpleDateFormat('yyyy')
    ]

    private static String INVOICES_FOR_SUB_HQL =
            'select co.invoice, sum(co.costInLocalCurrency), sum(co.costInBillingCurrency), co from CostItem as co where co.sub = :sub group by co.invoice order by min(co.invoice.startDate) desc';

    // TODO Used in Cost per use tab, still needed?
    private static String USAGE_FOR_SUB_IN_PERIOD =
            'select f.reportingYear, f.reportingMonth+1, sum(factValue) ' +
                    'from Fact as f ' +
                    'where f.factFrom >= :start and f.factTo <= :end and f.factType.value=:jr1a and exists ' +
                    '( select ie.tipp.title from IssueEntitlement as ie where ie.subscription = :sub and ie.tipp.title = f.relatedTitle)' +
                    'group by f.reportingYear, f.reportingMonth order by f.reportingYear desc, f.reportingMonth desc';

    // TODO Used in Cost per use tab, still needed?
    private static String TOTAL_USAGE_FOR_SUB_IN_PERIOD =
            'select sum(factValue) ' +
                    'from Fact as f ' +
                    'where f.factFrom >= :start and f.factTo <= :end and f.factType.value=:factType and exists ' +
                    '(select 1 from IssueEntitlement as ie INNER JOIN ie.tipp as tipp ' +
                    'where ie.subscription= :sub  and tipp.title = f.relatedTitle)'

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def index() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        Set<Thread> threadSet = Thread.getAllStackTraces().keySet()
        Thread[] threadArray = threadSet.toArray(new Thread[threadSet.size()])

        threadArray.each {
            if (it.name == 'PackageSync_'+result.subscriptionInstance?.id) {
                flash.message = message(code: 'subscription.details.linkPackage.thread.running')
            }
        }

        result.issueEntitlementEnrichment = params.issueEntitlementEnrichment
        result.contextOrg = contextService.getOrg()
        Date verystarttime = exportService.printStart("subscription")

        log.debug("subscription id:${params.id} format=${response.format}")

        result.max = params.max ? Integer.parseInt(params.max) : ((response.format && response.format != "html" && response.format != "all") ? 10000 : result.user.getDefaultPageSizeAsInteger())
        result.offset = (params.offset && response.format && response.format != "html") ? Integer.parseInt(params.offset) : 0
        boolean filterSet = false

        log.debug("max = ${result.max}")

        List<PendingChange> pendingChanges = PendingChange.executeQuery(
                "select pc from PendingChange as pc where subscription = :sub and ( pc.status is null or pc.status = :status ) order by ts desc",
                [sub: result.subscriptionInstance, status: RDStore.PENDING_CHANGE_PENDING]
        )

        if (result.subscriptionInstance?.isSlaved && ! pendingChanges.isEmpty()) {
            log.debug("Slaved subscription, auto-accept pending changes")
            def changesDesc = []
            pendingChanges.each { change ->
                if (!pendingChangeService.performAccept(change)) {
                    log.debug("Auto-accepting pending change has failed.")
                } else {
                    changesDesc.add(change.desc)
                }
            }
            // ERMS-1844: Hotfix: Änderungsmitteilungen ausblenden
            // flash.message = changesDesc
        } else {
            result.pendingChanges = pendingChanges
        }

        String base_qry = null
        Map<String,Object> qry_params = [subscription: result.subscriptionInstance]

        Date date_filter
        if (params.asAt && params.asAt.length() > 0) {
            SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
            date_filter = sdf.parse(params.asAt)
            result.as_at_date = date_filter
            result.editable = false
        }

        if (params.filter) {
            base_qry = " from IssueEntitlement as ie where ie.subscription = :subscription "
            if (date_filter) {
                // If we are not in advanced mode, hide IEs that are not current, otherwise filter
                // base_qry += "and ie.status <> ? and ( ? >= coalesce(ie.accessStartDate,subscription.startDate) ) and ( ( ? <= coalesce(ie.accessEndDate,subscription.endDate) ) OR ( ie.accessEndDate is null ) )  "
                // qry_params.add(deleted_ie);
                base_qry += "and ( ( :startDate >= coalesce(ie.accessStartDate,ie.subscription.startDate,ie.tipp.accessStartDate) or (ie.accessStartDate is null and ie.subscription.startDate is null and ie.tipp.accessStartDate is null) ) and ( :endDate <= coalesce(ie.accessEndDate,ie.subscription.endDate,ie.tipp.accessEndDate) or (ie.accessEndDate is null and ie.subscription.endDate is null and ie.tipp.accessEndDate is null) OR ( ie.subscription.hasPerpetualAccess = true ) ) ) "
                qry_params.startDate = date_filter
                qry_params.endDate = date_filter
            }
            base_qry += "and ( ( lower(ie.tipp.title.title) like :title ) or ( exists ( from Identifier ident where ident.ti.id = ie.tipp.title.id and ident.value like :identifier ) ) ) "
            qry_params.title = "%${params.filter.trim().toLowerCase()}%"
            qry_params.identifier = "%${params.filter}%"
            filterSet = true
        } else {
            base_qry = " from IssueEntitlement as ie where ie.subscription = :subscription "
            /*if (params.mode != 'advanced') {
                // If we are not in advanced mode, hide IEs that are not current, otherwise filter

                base_qry += " and ( :startDate >= coalesce(ie.accessStartDate,ie.subscription.startDate,ie.tipp.accessStartDate) or (ie.accessStartDate is null and ie.subscription.startDate is null and ie.tipp.accessStartDate is null) ) and ( ( :endDate <= coalesce(ie.accessEndDate,ie.subscription.endDate,ie.accessEndDate) or (ie.accessEndDate is null and ie.subscription.endDate is null and ie.tipp.accessEndDate is null)  or (ie.subscription.hasPerpetualAccess = true) ) ) "
                qry_params.startDate = date_filter
                qry_params.endDate = date_filter
            }*/
        }
        if(params.mode != 'advanced') {
            base_qry += " and ie.status = :current "
            qry_params.current = RDStore.TIPP_STATUS_CURRENT
        }
        else {
            base_qry += " and ie.status != :deleted "
            qry_params.deleted = RDStore.TIPP_STATUS_DELETED
        }

        base_qry += " and ie.acceptStatus = :ieAcceptStatus "
        qry_params.ieAcceptStatus = RDStore.IE_ACCEPT_STATUS_FIXED

        if (params.pkgfilter && (params.pkgfilter != '')) {
            base_qry += " and ie.tipp.pkg.id = :pkgId "
            qry_params.pkgId = Long.parseLong(params.pkgfilter)
            filterSet = true
        }

        if (params.titleGroup && (params.titleGroup != '')) {
            base_qry += " and exists ( select iegi from IssueEntitlementGroupItem as iegi where iegi.ieGroup.id = :titleGroup and iegi.ie = ie) "
            qry_params.titleGroup = Long.parseLong(params.titleGroup)
        }
        if(params.seriesNames) {
            base_qry += " and lower(ie.tipp.title.seriesName) like :seriesNames "
            qry_params.seriesNames = "%${params.seriesNames.trim().toLowerCase()}%"
            filterSet = true
        }

        if (params.subject_references && params.subject_references != "" && params.list('subject_references')) {
            base_qry += " and lower(ie.tipp.title.subjectReference) in (:subject_references)"
            qry_params.subject_references = params.list('subject_references').collect { ""+it.toLowerCase()+"" }
            filterSet = true
        }

        if (params.series_names && params.series_names != "" && params.list('series_names')) {
            base_qry += " and lower(ie.tipp.title.seriesName) in (:series_names)"
            qry_params.series_names = params.list('series_names').collect { ""+it.toLowerCase()+"" }
            filterSet = true
        }



        if ((params.sort != null) && (params.sort.length() > 0)) {
            if(params.sort == 'startDate')
                base_qry += "order by ic.startDate ${params.order}, lower(ie.tipp.title.title) asc "
            else if(params.sort == 'endDate')
                base_qry += "order by ic.endDate ${params.order}, lower(ie.tipp.title.title) asc "
            else
                base_qry += "order by ie.${params.sort} ${params.order} "
        } else {
            base_qry += "order by lower(ie.tipp.title.title) asc"
        }

        result.filterSet = filterSet

        Set<IssueEntitlement> entitlements = IssueEntitlement.executeQuery("select ie " + base_qry, qry_params)

        if(params.kbartPreselect) {
            CommonsMultipartFile kbartFile = params.kbartPreselect
            InputStream stream = kbartFile.getInputStream()
            List issueEntitlements = entitlements.toList()

            result.enrichmentProcess = subscriptionService.issueEntitlementEnrichment(stream, issueEntitlements, (params.uploadCoverageDates == 'on'), (params.uploadPriceInfo == 'on'))

            params.remove("kbartPreselect")
            params.remove("uploadCoverageDates")
            params.remove("uploadPriceInfo")
        }

        result.subjects = subscriptionService.getSubjects(entitlements.collect {it.tipp.title.id})
        result.seriesNames = subscriptionService.getSeriesNames(entitlements.collect {it.tipp.title.id})

        if(result.subscriptionInstance.ieGroups.size() > 0) {
            result.num_ies = subscriptionService.getIssueEntitlementsWithFilter(result.subscriptionInstance, [offset: 0, max: 5000]).size()
        }
        result.num_sub_rows = entitlements.size()
        result.entitlements = entitlements.drop(result.offset).take(result.max)

        Set<SubscriptionPackage> deletedSPs = result.subscriptionInstance.packages.findAll {sp -> sp.pkg.packageStatus == RDStore.PACKAGE_STATUS_DELETED}

        if(deletedSPs) {
            result.deletedSPs = []
            ApiSource source = ApiSource.findByTypAndActive(ApiSource.ApiTyp.GOKBAPI,true)
            deletedSPs.each { sp ->
                result.deletedSPs << [name:sp.pkg.name,link:"${source.editUrl}/gokb/resource/show/${sp.pkg.gokbId}"]
            }
        }

        exportService.printDuration(verystarttime, "Querying")

        log.debug("subscriptionInstance returning... ${result.num_sub_rows} rows ");
        String filename = "${escapeService.escapeString(result.subscriptionInstance.dropdownNamingConvention())}_${DateUtil.SDF_NoTimeNoPoint.format(new Date())}"


        if (executorWrapperService.hasRunningProcess(result.subscriptionInstance)) {
            result.processingpc = true
        }

        if (params.exportKBart) {
            response.setHeader("Content-disposition", "attachment; filename=${filename}.tsv")
            response.contentType = "text/tsv"
            ServletOutputStream out = response.outputStream
            Map<String, List> tableData = exportService.generateTitleExportKBART(entitlements)
            out.withWriter { writer ->
                writer.write(exportService.generateSeparatorTableString(tableData.titleRow, tableData.columnData, '\t'))
            }
            out.flush()
            out.close()
        }
        else if(params.exportXLSX) {
            response.setHeader("Content-disposition", "attachment; filename=\"${filename}.xlsx\"")
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            Map<String,List> export = exportService.generateTitleExportXLS(entitlements)
            Map sheetData = [:]
            sheetData[message(code:'menu.my.titles')] = [titleRow:export.titles,columnData:export.rows]
            SXSSFWorkbook workbook = exportService.generateXLSXWorkbook(sheetData)
            workbook.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            workbook.dispose()
        }
        else {
            withFormat {
                html {
                    result
                }
                csv {
                    response.setHeader("Content-disposition", "attachment; filename=\"${filename}.csv\"")
                    response.contentType = "text/csv"
                    ServletOutputStream out = response.outputStream
                    Map<String,List> tableData = exportService.generateTitleExportCSV(entitlements)
                    out.withWriter { writer ->
                        writer.write(exportService.generateSeparatorTableString(tableData.titleRow,tableData.rows,';'))
                    }
                    out.close()
                    exportService.printDuration(verystarttime, "Overall Time")
                }
                /*
                json {
                    def starttime = exportService.printStart("Building Map")
                    def map = exportService.getSubscriptionMap(result.subscriptionInstance, result.entitlements)
                    exportService.printDuration(starttime, "Building Map")

                    starttime = exportService.printStart("Create JSON")
                    def json = map as JSON
                    exportService.printDuration(starttime, "Create JSON")

                    response.setHeader("Content-disposition", "attachment; filename=\"${filename}.json\"")
                    response.contentType = "application/json"
                    render json

                    exportService.printDuration(verystarttime, "Overall Time")
                }
                xml {
                    def starttime = exportService.printStart("Building XML Doc")
                    def doc = exportService.buildDocXML("Subscriptions")
                    exportService.addSubIntoXML(doc, doc.getDocumentElement(), result.subscriptionInstance, result.entitlements)
                    exportService.printDuration(starttime, "Building XML Doc")

                    response.setHeader("Content-disposition", "attachment; filename=\"${filename}.xml\"")
                    response.contentType = "text/xml"
                    starttime = exportService.printStart("Sending XML")
                    exportService.streamOutXML(doc, response.outputStream)
                    exportService.printDuration(starttime, "Sending XML")

                    exportService.printDuration(verystarttime, "Overall Time")
                }
                */
            }
        }

    }


    @DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def addCoverage() {
        IssueEntitlement base = IssueEntitlement.get(params.issueEntitlement)
        if(base) {
            IssueEntitlementCoverage ieCoverage = new IssueEntitlementCoverage(issueEntitlement: base)
            if(ieCoverage.save(flush:true))
                redirect action: 'index', id: base.subscription.id, params: params
            else log.error("Error on creation new coverage statement: ${ieCoverage.errors}")
        }
        else log.error("Issue entitlement with ID ${params.issueEntitlement} could not be found")
    }

    @DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def removeCoverage() {
        IssueEntitlementCoverage ieCoverage = IssueEntitlementCoverage.get(params.ieCoverage)
        Long subId = ieCoverage.issueEntitlement.subscription.id
        if(ieCoverage) {
            PendingChange.executeUpdate('update PendingChange pc set pc.status = :rejected where pc.oid = :oid',[rejected:RDStore.PENDING_CHANGE_REJECTED,oid:"${ieCoverage.class.name}:${ieCoverage.id}"])
            ieCoverage.delete(flush:true)
            redirect action: 'index', id: subId, params: params
        }
        else log.error("Issue entitlement coverage with ID ${params.ieCoverage} could not be found")
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def delete() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_EDIT)
        if(result.subscription.instanceOf)
            result.parentId = result.subscription.instanceOf.id
        else if(result.subscription._getCalculatedType() in [CalculatedType.TYPE_PARTICIPATION_AS_COLLECTIVE, CalculatedType.TYPE_COLLECTIVE, CalculatedType.TYPE_CONSORTIAL, CalculatedType.TYPE_ADMINISTRATIVE])
            result.parentId = result.subscription.id

        if (params.process  && result.editable) {
            result.licenses.each { License l ->
                subscriptionService.setOrgLicRole(result.subscription,l,true)
            }
            result.delResult = deletionService.deleteSubscription(result.subscription, false)
        }
        else {
            result.delResult = deletionService.deleteSubscription(result.subscription, DeletionService.DRY_RUN)
        }

        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def unlinkPackage() {
        log.debug("unlinkPackage :: ${params}")
        Map<String, Object> result = [:]
        result.user = User.get(springSecurityService.principal.id)
        result.subscription = Subscription.get(params.subscription)
        result.package = Package.get(params.package)
        def query = "from IssueEntitlement ie, Package pkg where ie.subscription =:sub and pkg.id =:pkg_id and ie.tipp in ( select tipp from TitleInstancePackagePlatform tipp where tipp.pkg.id = :pkg_id ) "
        def queryParams = [sub: result.subscription, pkg_id: result.package.id]

            result.editable = true
            if (params.confirmed) {
                if(result.package.unlinkFromSubscription(result.subscription, true)){
                    flash.message = message(code: 'subscription.details.unlink.successfully')
                }else {
                    flash.error = message(code: 'subscription.details.unlink.notSuccessfully')
                }

                return redirect(action:'show', id: params.subscription)

            } else {

                int numOfPCs = result.package.removePackagePendingChanges([result.subscription.id], false)

                int numOfIEs = IssueEntitlement.executeQuery("select ie.id ${query}", queryParams).size()

                int numOfCIs = CostItem.findAllBySubPkg(SubscriptionPackage.findBySubscriptionAndPkg(result.subscription,result.package)).size()
                def conflict_item_pkg =
                        [name: "${g.message(code: "subscription.details.unlink.linkedPackage")}",
                         details: [['link': createLink(controller: 'package', action: 'show', id: result.package.id), 'text': result.package.name]],
                         action: [actionRequired: false, text: "${g.message(code: "subscription.details.unlink.unlink.singular")}"]
                        ]
                def conflicts_list = [conflict_item_pkg]

                if (numOfIEs > 0) {
                    def conflict_item_ie =
                            [name: "${g.message(code: "subscription.details.unlink.packageIEs")}",
                             details: [[number: numOfIEs,'text': "${g.message(code: "default.ie")}"]],
                             action: [actionRequired: false, text: "${g.message(code: "subscription.details.unlink.delete.plural")}"]
                            ]
                    conflicts_list += conflict_item_ie
                }
                if (numOfPCs > 0) {
                    def conflict_item_pc =
                            [name: "${g.message(code: "subscription.details.unlink.pendingChanges")}",
                             details: [[number: numOfPCs, 'text': "${g.message(code: "default.pendingchanges")}"]],
                             action: [actionRequired: false, text: "${g.message(code: "subscription.details.unlink.delete.plural")}"]
                            ]
                    conflicts_list += conflict_item_pc
                }
                if (numOfCIs > 0) {
                    Map<String,Object> conflict_item_ci =
                            [name: "${g.message(code: "subscription.details.unlink.costItems")}",
                             details: [[number: numOfCIs, 'text': "${g.message(code: "financials.costItem")}"]],
                             action: [actionRequired: true, text: "${g.message(code: "subscription.details.unlink.delete.impossible.plural")}"]
                    ]
                    conflicts_list += conflict_item_ci
                }

                SubscriptionPackage sp = SubscriptionPackage.findByPkgAndSubscription(result.package, result.subscription)
                List accessPointLinks = []
                if (sp.oapls){
                    Map detailItem = [number: sp.oapls.size(),'text':"${g.message(code: "default.accessPoints")}"]
                    accessPointLinks.add(detailItem)
                }
                if (accessPointLinks) {
                    def conflict_item_oap =
                            [name: "${g.message(code: "subscription.details.unlink.accessPoints")}",
                             details: accessPointLinks,
                             action: [actionRequired: false, text: "${g.message(code: "subscription.details.unlink.delete.plural")}"]
                            ]
                    conflicts_list += conflict_item_oap
                }


                //Automatisch Paket entknüpfen, wenn das Paket in der Elternlizenz entknüpft wird
                if(result.subscription._getCalculatedType() in [CalculatedType.TYPE_CONSORTIAL, CalculatedType.TYPE_COLLECTIVE, CalculatedType.TYPE_ADMINISTRATIVE] &&
                        accessService.checkPerm("ORG_INST_COLLECTIVE,ORG_CONSORTIUM") && (result.subscription.instanceOf == null)){

                    List<Subscription> childSubs = Subscription.findAllByInstanceOf(result.subscription)
                    if (childSubs) {

                        List<SubscriptionPackage> spChildSubs = childSubs.isEmpty() ? [] : SubscriptionPackage.findAllByPkgAndSubscriptionInList(result.package, childSubs)

                        String queryChildSubs = "from IssueEntitlement ie, Package pkg where ie.subscription in (:sub) and pkg.id =:pkg_id and ie.tipp in ( select tipp from TitleInstancePackagePlatform tipp where tipp.pkg.id = :pkg_id ) "
                        Map queryParamChildSubs = [sub: childSubs, pkg_id: result.package.id]

                        int numOfPCsChildSubs = result.package.removePackagePendingChanges(childSubs.id, false)

                        int numOfIEsChildSubs = IssueEntitlement.executeQuery("select ie.id ${queryChildSubs}", queryParamChildSubs).size()

                        int numOfCIsChildSubs = childSubs.isEmpty() ? 0 : CostItem.findAllBySubPkgInList(SubscriptionPackage.findAllBySubscriptionInListAndPkg(childSubs, result.package)).size()

                        if (spChildSubs.size() > 0) {
                            Map conflict_item_pkgChildSubs = [
                                    name   : "${g.message(code: "subscription.details.unlink.linkedPackageSubChild")}",
                                    details: [[number: spChildSubs.size(), 'text': "${g.message(code: "subscription.details.unlink.linkedPackageSubChild")} "]],
                                    action : [actionRequired: false, text: "${g.message(code: "subscription.details.unlink.delete.plural")}"]]
                            conflicts_list += conflict_item_pkgChildSubs
                        }

                        if (numOfIEsChildSubs > 0) {
                            Map conflict_item_ie = [
                                    name   : "${g.message(code: "subscription.details.unlink.packageIEsSubChild")}",
                                    details: [[number: numOfIEsChildSubs, 'text': "${g.message(code: "subscription.details.unlink.packageIEsSubChild")} "]],
                                    action : [actionRequired: false, text: "${g.message(code: "subscription.details.unlink.delete.plural")}"]]
                            conflicts_list += conflict_item_ie
                        }
                        if (numOfPCsChildSubs > 0) {
                            Map conflict_item_pc = [
                                    name   : "${g.message(code: "subscription.details.unlink.pendingChangesSubChild")}",
                                    details: [[number: numOfPCsChildSubs, 'text': "${g.message(code: "subscription.details.unlink.pendingChangesSubChild")} "]],
                                    action : [actionRequired: false, text: "${g.message(code: "subscription.details.unlink.delete.plural")}"]]
                            conflicts_list += conflict_item_pc
                        }
                        if (numOfCIsChildSubs > 0) {
                            Map<String,Object> conflict_item_ci =
                                    [name: "${g.message(code: "subscription.details.unlink.costItems")}",
                                     details: [[number: numOfCIsChildSubs, 'text': "${g.message(code: "financials.costItem")}"]],
                                     action: [actionRequired: true, text: "${g.message(code: "subscription.details.unlink.delete.impossible.plural")}"]
                                    ]
                            conflicts_list += conflict_item_ci
                        }


                        List accessPointLinksChildSubs = []
                        if (spChildSubs.oapls) {
                            Map detailItem = [number: spChildSubs.oapls.size(), 'text': "${g.message(code: "subscription.details.unlink.accessPoints")} "]
                            accessPointLinksChildSubs.add(detailItem)
                        }
                        if (accessPointLinksChildSubs) {
                            Map conflict_item_oap = [
                                    name   : "${g.message(code: "subscription.details.unlink.accessPoints")}",
                                    details: accessPointLinksChildSubs,
                                    action : [actionRequired: false, text: "${g.message(code: "subscription.details.unlink.delete.plural")}"]]
                            conflicts_list += conflict_item_oap
                        }
                    }

                }

                return render(template: "unlinkPackageModal", model: [pkg: result.package, subscription: result.subscription, conflicts_list: conflicts_list])
            }

    }

    /*@DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_USER")
    })*/
    @Secured(['ROLE_ADMIN'])
    def compare() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(accessService.CHECK_VIEW)

        result
    }

    def formatDateOrNull(formatter, date) {
        def result;
        if (date) {
            result = formatter.format(date)
        } else {
            result = ''
        }
        return result
    }

    def createCompareList(sub, dateStr, params, result) {
        def returnVals = [:]
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        Date date = dateStr ? sdf.parse(dateStr) : new Date()
        String subId = sub.substring(sub.indexOf(":") + 1)

        Subscription subInst = Subscription.get(subId)
        if (subInst.startDate > date || subInst.endDate < date) {
            def errorMsg = "${subInst.name} start date is: ${sdf.format(subInst.startDate)} and end date is: ${sdf.format(subInst.endDate)}. You have selected to compare it on date ${sdf.format(date)}."
            throw new IllegalArgumentException(errorMsg)
        }

        result.subInsts.add(subInst)

        result.subDates.add(sdf.format(date))

        def queryParams = [subInst]
        def query = generateIEQuery(params, queryParams, true, date)

        def list = IssueEntitlement.executeQuery("select ie " + query, queryParams);
        list

    }

    private def generateIEQuery(params, qry_params, showDeletedTipps, asAt) {

        String base_qry = "from IssueEntitlement as ie where ie.subscription = ? and ie.tipp.title.status.value != 'Deleted' "

        if (showDeletedTipps == false) {
            base_qry += "and ie.tipp.status != ? "
            qry_params.add(RDStore.TIPP_STATUS_DELETED)
        }

        if (params.filter) {
            base_qry += " and ( ( lower(ie.tipp.title.title) like ? ) or ( exists ( from Identifier ident where ident.ti.id = ie.tipp.title.id and ident.value like ? ) ) )"
            qry_params.add("%${params.filter.trim().toLowerCase()}%")
            qry_params.add("%${params.filter}%")
        }

        if (params.startsBefore && params.startsBefore.length() > 0) {
            SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
            Date d = sdf.parse(params.startsBefore)
            base_qry += " and (select min(ic.startDate) from IssueEntitlementCoverage ic where ic.ie = ie) <= ?"
            qry_params.add(d)
        }

        if (asAt != null) {
            base_qry += " and ( ( ? >= coalesce(ie.tipp.accessStartDate, (select min(ic.startDate) from IssueEntitlementCoverage ic where ic.ie = ie)) ) and ( ( ? <= ie.tipp.accessEndDate ) or ( ie.tipp.accessEndDate is null ) ) ) "
            qry_params.add(asAt);
            qry_params.add(asAt);
        }

        return base_qry
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def subscriptionBatchUpdate() {

        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_EDIT)
        if (!result) {
            response.sendError(401); return
        }

        // def formatter = new java.text.SimpleDateFormat("MM/dd/yyyy")
        SimpleDateFormat formatter = DateUtil.getSDF_NoTime()

        // def subscriptionInstance = Subscription.get(params.id)
        // def user = User.get(springSecurityService.principal.id)
        // userAccessCheck(subscriptionInstance, user, 'edit')

        log.debug("subscriptionBatchUpdate ${params}");

        params.each { p ->
            if (p.key.startsWith('_bulkflag.') && (p.value == 'on')) {
                def ie_to_edit = p.key.substring(10);

                def ie = IssueEntitlement.get(ie_to_edit)

                if (params.bulkOperation == "edit") {

                    /*if (params.bulk_start_date && (params.bulk_start_date.trim().length() > 0)) {
                        ie.startDate = formatter.parse(params.bulk_start_date)
                    }

                    if (params.bulk_end_date && (params.bulk_end_date.trim().length() > 0)) {
                        ie.endDate = formatter.parse(params.bulk_end_date)
                    }*/

                    if (params.bulk_access_start_date && (params.bulk_access_start_date.trim().length() > 0)) {
                        ie.accessStartDate = formatter.parse(params.bulk_access_start_date)
                    }

                    if (params.bulk_access_end_date && (params.bulk_access_end_date.trim().length() > 0)) {
                        ie.accessEndDate = formatter.parse(params.bulk_access_end_date)
                    }

                    if (params.bulk_embargo && (params.bulk_embargo.trim().length() > 0)) {
                        ie.embargo = params.bulk_embargo
                    }

                    if (params.bulk_medium.trim().length() > 0) {
                        def selected_refdata = genericOIDService.resolveOID(params.bulk_medium.trim())
                        log.debug("Selected medium is ${selected_refdata}");
                        ie.medium = selected_refdata
                    }

                    if (params.bulk_coverage && (params.bulk_coverage.trim().length() > 0)) {
                        ie.coverageDepth = params.bulk_coverage
                    }

                    if (params.titleGroup && (params.titleGroup.trim().length() > 0)) {
                        IssueEntitlementGroup entitlementGroup = IssueEntitlementGroup.get(Long.parseLong(params.titleGroup))
                        if(entitlementGroup && !IssueEntitlementGroupItem.findByIeGroupAndIe(entitlementGroup, ie)){
                            IssueEntitlementGroupItem issueEntitlementGroupItem = new IssueEntitlementGroupItem(
                                    ie: ie,
                                    ieGroup: entitlementGroup)

                            if (!issueEntitlementGroupItem.save(flush: true)) {
                                log.error("Problem saving IssueEntitlementGroupItem ${issueEntitlementGroupItem.errors}")
                            }

                        }
                    }

                    if (!ie.save(flush: true)) {
                        log.error("Problem saving ${ie.errors}")
                    }
                } else if (params.bulkOperation == "remove") {
                    log.debug("Updating ie ${ie.id} status to deleted")
                    def deleted_ie = RDStore.TIPP_STATUS_DELETED
                    ie.status = deleted_ie
                    if (!ie.save(flush: true)) {
                        log.error("Problem saving ${ie.errors}")
                    }
                }
            }
        }

        redirect action: 'index', params: [id: result.subscriptionInstance?.id, sort: params.sort, order: params.order, offset: params.offset, max: params.max]
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def addEntitlements() {
        log.debug("addEntitlements .. params: ${params}")

        Map<String,Object> result = setResultGenericsAndCheckAccess(accessService.CHECK_VIEW_AND_EDIT)
        if (!result) {
            response.sendError(401); return
        }
        result.preselectValues = params.preselectValues == 'on'
        result.preselectCoverageDates = params.preselectCoverageDates == 'on'
        result.uploadPriceInfo = params.uploadPriceInfo == 'on'

        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        Thread[] threadArray = threadSet.toArray(new Thread[threadSet.size()]);

        threadArray.each {
            if (it.name == 'PackageSync_'+result.subscriptionInstance?.id) {
                flash.message = message(code: 'subscription.details.linkPackage.thread.running')
            }
        }

        result.max = params.max ? Integer.parseInt(params.max) : request.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        RefdataValue tipp_deleted = RDStore.TIPP_STATUS_DELETED
        RefdataValue tipp_current = RDStore.TIPP_STATUS_CURRENT
        RefdataValue ie_deleted = RDStore.TIPP_STATUS_DELETED
        RefdataValue ie_current = RDStore.TIPP_STATUS_CURRENT

        log.debug("filter: \"${params.filter}\"");

        List<TitleInstancePackagePlatform> tipps = []
        List errorList = []
            boolean filterSet = false
            EhcacheWrapper checkedCache = contextService.getCache("/subscription/addEntitlements/${params.id}", contextService.USER_SCOPE)
            Map<TitleInstance,IssueEntitlement> addedTipps = [:]
            result.subscriptionInstance.issueEntitlements.each { ie ->
                if(ie instanceof IssueEntitlement && ie.status != ie_deleted)
                    addedTipps[ie.tipp.title] = ie
            }
            // We need all issue entitlements from the parent subscription where no row exists in the current subscription for that item.
            def basequery = null
            def qry_params = [result.subscriptionInstance, tipp_current, result.subscriptionInstance, ie_current]

            if (params.filter) {
                log.debug("Filtering....");
                basequery = "from TitleInstancePackagePlatform tipp where tipp.pkg in ( select pkg from SubscriptionPackage sp where sp.subscription = ? ) and tipp.status = ? and ( not exists ( select ie from IssueEntitlement ie where ie.subscription = ? and ie.tipp.id = tipp.id and ie.status = ? ) ) and ( ( lower(tipp.title.title) like ? ) OR ( exists ( select ident from Identifier ident where ident.ti.id = tipp.title.id and ident.value like ? ) ) ) "
                qry_params.add("%${params.filter.trim().toLowerCase()}%")
                qry_params.add("%${params.filter}%")
                filterSet = true
            } else {
                basequery = "from TitleInstancePackagePlatform tipp where tipp.pkg in ( select pkg from SubscriptionPackage sp where sp.subscription = ? ) and tipp.status = ? and ( not exists ( select ie from IssueEntitlement ie where ie.subscription = ? and ie.tipp.id = tipp.id and ie.status = ? ) )"
            }

            if (params.endsAfter && params.endsAfter.length() > 0) {
                SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
                Date d = sdf.parse(params.endsAfter)
                basequery += " and (select max(tc.endDate) from TIPPCoverage tc where tc.tipp = tipp) >= ?"
                qry_params.add(d)
                filterSet = true
            }

            if (params.startsBefore && params.startsBefore.length() > 0) {
                SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
                Date d = sdf.parse(params.startsBefore)
                basequery += " and (select min(tc.startDate) from TIPPCoverage tc where tc.tipp = tipp) <= ?"
                qry_params.add(d)
                filterSet = true
            }

            if (params.pkgfilter && (params.pkgfilter != '')) {
                basequery += " and tipp.pkg.gokbId = ? "
                qry_params.add(params.pkgfilter)
                filterSet = true
            }

            if ((params.sort != null) && (params.sort.length() > 0)) {
                basequery += " order by tipp.${params.sort} ${params.order} "
                filterSet = true
            } else {
                basequery += " order by tipp.title.title asc "
            }

            result.filterSet = filterSet

            log.debug("Query ${basequery} ${qry_params}");

            tipps.addAll(TitleInstancePackagePlatform.executeQuery("select tipp ${basequery}", qry_params))
            result.num_tipp_rows = tipps.size()
            result.tipps = tipps.drop(result.offset).take(result.max)
            Map identifiers = [zdbIds:[],onlineIds:[],printIds:[],unidentified:[]]
            Map<String,Map> issueEntitlementOverwrite = [:]
            result.issueEntitlementOverwrite = [:]
            if(params.kbartPreselect && !params.pagination) {
                CommonsMultipartFile kbartFile = params.kbartPreselect
                identifiers.filename = kbartFile.originalFilename
                InputStream stream = kbartFile.getInputStream()
                ArrayList<String> rows = stream.text.split('\n')
                Map<String,Integer> colMap = [publicationTitleCol:-1,zdbCol:-1, onlineIdentifierCol:-1, printIdentifierCol:-1, dateFirstInPrintCol:-1, dateFirstOnlineCol:-1,
                startDateCol:-1, startVolumeCol:-1, startIssueCol:-1,
                endDateCol:-1, endVolumeCol:-1, endIssueCol:-1,
                accessStartDateCol:-1, accessEndDateCol:-1, coverageDepthCol:-1, coverageNotesCol:-1, embargoCol:-1,
                listPriceCol:-1, listCurrencyCol:-1, listPriceEurCol:-1, listPriceUsdCol:-1, listPriceGbpCol:-1, localPriceCol:-1, localCurrencyCol:-1, priceDateCol:-1]
                boolean isUniqueListpriceColumn = false
                //read off first line of KBART file
                rows[0].split('\t').eachWithIndex { headerCol, int c ->
                    switch(headerCol.toLowerCase().trim()) {
                        case "zdb_id": colMap.zdbCol = c
                            break
                        case "print_identifier": colMap.printIdentifierCol = c
                            break
                        case "online_identifier": colMap.onlineIdentifierCol = c
                            break
                        case "publication_title": colMap.publicationTitleCol = c
                            break
                        case "date_monograph_published_print": colMap.dateFirstInPrintCol = c
                            break
                        case "date_monograph_published_online": colMap.dateFirstOnlineCol = c
                            break
                        case "date_first_issue_online": colMap.startDateCol = c
                            break
                        case "num_first_vol_online": colMap.startVolumeCol = c
                            break
                        case "num_first_issue_online": colMap.startIssueCol = c
                            break
                        case "date_last_issue_online": colMap.endDateCol = c
                            break
                        case "num_last_vol_online": colMap.endVolumeCol = c
                            break
                        case "num_last_issue_online": colMap.endIssueCol = c
                            break
                        case "access_start_date": colMap.accessStartDateCol = c
                            break
                        case "access_end_date": colMap.accessEndDateCol = c
                            break
                        case "embargo_info": colMap.embargoCol = c
                            break
                        case "coverage_depth": colMap.coverageDepthCol = c
                            break
                        case "notes": colMap.coverageNotesCol = c
                            break
                        case "listprice_value": colMap.listPriceCol = c
                            break
                        case "listprice_currency": colMap.listCurrencyCol = c
                            break
                        case "listprice_eur": colMap.listPriceEurCol = c
                            break
                        case "listprice_usd": colMap.listPriceUsdCol = c
                            break
                        case "listprice_gbp": colMap.listPriceGbpCol = c
                            break
                        case "localprice_value": colMap.localPriceCol = c
                            break
                        case "localprice_currency": colMap.localCurrencyCol = c
                            break
                        case "price_date": colMap.priceDateCol = c
                            break
                    }
                }
                if((colMap.listPriceCol > -1 && colMap.listCurrencyCol > -1) && (colMap.listPriceEurCol > -1 || colMap.listPriceGbpCol > -1 || colMap.listPriceUsdCol > -1)) {
                    errorList.add(g.message(code:'subscription.details.addEntitlements.duplicatePriceColumn'))
                }
                else if((colMap.listPriceEurCol > -1 && colMap.listPriceUsdCol > -1) && (colMap.listPriceEurCol > -1 && colMap.listPriceGbpCol > -1) && (colMap.listPriceUsdCol > -1 && colMap.listPriceGbpCol > -1 )) {
                    errorList.add(g.message(code:'subscription.details.addEntitlements.duplicatePriceColumn'))
                }
                else isUniqueListpriceColumn = true
                //after having read off the header row, pop the first row
                rows.remove(0)
                //now, assemble the identifiers available to highlight
                Map<String, IdentifierNamespace> namespaces = [zdb  :IdentifierNamespace.findByNs('zdb'),
                                                               eissn:IdentifierNamespace.findByNs('eissn'), isbn:IdentifierNamespace.findByNs('isbn'),
                                                               issn :IdentifierNamespace.findByNs('issn'), pisbn:IdentifierNamespace.findByNs('pisbn')]
                rows.eachWithIndex { row, int i ->
                    log.debug("now processing entitlement ${i}")
                    Map<String,Object> ieCandidate = [:]
                    ArrayList<String> cols = row.split('\t')
                    Map<String,Object> idCandidate
                    String ieCandIdentifier
                    if(colMap.zdbCol >= 0 && cols[colMap.zdbCol]) {
                        identifiers.zdbIds.add(cols[colMap.zdbCol])
                        idCandidate = [namespaces:[namespaces.zdb],value:cols[colMap.zdbCol]]
                        if(issueEntitlementOverwrite[cols[colMap.zdbCol]])
                            ieCandidate = issueEntitlementOverwrite[cols[colMap.zdbCol]]
                        else ieCandIdentifier = cols[colMap.zdbCol]
                    }
                    if(colMap.onlineIdentifierCol >= 0 && cols[colMap.onlineIdentifierCol]) {
                        identifiers.onlineIds.add(cols[colMap.onlineIdentifierCol])
                        idCandidate = [namespaces:[namespaces.eissn,namespaces.isbn],value:cols[colMap.onlineIdentifierCol]]
                        if(ieCandIdentifier == null && !issueEntitlementOverwrite[cols[colMap.onlineIdentifierCol]])
                            ieCandIdentifier = cols[colMap.onlineIdentifierCol]
                        else if(issueEntitlementOverwrite[cols[colMap.onlineIdentifierCol]])
                            ieCandidate = issueEntitlementOverwrite[cols[colMap.onlineIdentifierCol]]
                    }
                    if(colMap.printIdentifierCol >= 0 && cols[colMap.printIdentifierCol]) {
                        identifiers.printIds.add(cols[colMap.printIdentifierCol])
                        idCandidate = [namespaces:[namespaces.issn,namespaces.pisbn],value:cols[colMap.printIdentifierCol]]
                        if(ieCandIdentifier == null && !issueEntitlementOverwrite[cols[colMap.printIdentifierCol]])
                            ieCandIdentifier = cols[colMap.printIdentifierCol]
                        else if(issueEntitlementOverwrite[cols[colMap.printIdentifierCol]])
                            ieCandidate = issueEntitlementOverwrite[cols[colMap.printIdentifierCol]]
                    }
                    if(((colMap.zdbCol >= 0 && cols[colMap.zdbCol].trim().isEmpty()) || colMap.zdbCol < 0) &&
                       ((colMap.onlineIdentifierCol >= 0 && cols[colMap.onlineIdentifierCol].trim().isEmpty()) || colMap.onlineIdentifierCol < 0) &&
                       ((colMap.printIdentifierCol >= 0 && cols[colMap.printIdentifierCol].trim().isEmpty()) || colMap.printIdentifierCol < 0)) {
                        identifiers.unidentified.add('"'+cols[0]+'"')
                    }
                    else {
                        //make checks ...
                        //is title in LAS:eR?
                        //List tiObj = TitleInstancePackagePlatform.executeQuery('select tipp from TitleInstancePackagePlatform tipp join tipp.title ti join ti.ids identifiers where identifiers.identifier.value in :idCandidates',[idCandidates:idCandidates])
                        //log.debug(idCandidates)
                        Identifier id = Identifier.findByValueAndNsInList(idCandidate.value,idCandidate.namespaces)
                        if(id && id.ti) {
                            //is title already added?
                            if(addedTipps.get(id.ti)) {
                                errorList.add("${cols[colMap.publicationTitleCol]}&#9;${cols[colMap.zdbCol] && colMap.zdbCol ? cols[colMap.zdbCol] : " "}&#9;${cols[colMap.onlineIdentifierCol] && colMap.onlineIndentifierCol > -1 ? cols[colMap.onlineIdentifierCol] : " "}&#9;${cols[colMap.printIdentifierCol] && colMap.printIdentifierCol > -1 ? cols[colMap.printIdentifierCol] : " "}&#9;${message(code:'subscription.details.addEntitlements.titleAlreadyAdded')}")
                            }
                            /*else if(!issueEntitlement) {
                                errors += g.message([code:'subscription.details.addEntitlements.titleNotMatched',args:cols[0]])
                            }*/
                        }
                        else if(!id) {
                            errorList.add("${cols[colMap.publicationTitleCol]}&#9;${cols[colMap.zdbCol] && colMap.zdbCol > -1 ? cols[colMap.zdbCol] : " "}&#9;${cols[colMap.onlineIdentifierCol] && colMap.onlineIndentifierCol > -1 ? cols[colMap.onlineIdentifierCol] : " "}&#9;${cols[colMap.printIdentifierCol] && colMap.printIdentifierCol > -1 ? cols[colMap.printIdentifierCol] : " "}&#9;${message(code:'subscription.details.addEntitlements.titleNotInERMS')}")
                        }
                    }
                    List<Map> ieCoverages
                    if(ieCandidate.coverages)
                        ieCoverages = ieCandidate.coverages
                    else ieCoverages = []
                    Map covStmt = [:]
                    colMap.each { String colName, int colNo ->
                        if(colNo > -1 && cols[colNo]) {
                            String cellEntry = cols[colNo].trim()
                            if(result.preselectCoverageDates) {
                                switch(colName) {
                                    case "dateFirstInPrintCol": ieCandidate.dateFirstInPrint = cellEntry
                                        break
                                    case "dateFirstOnlineCol": ieCandidate.dateFirstOnline = cellEntry
                                        break
                                    case "startDateCol": covStmt.startDate = cellEntry
                                        break
                                    case "startVolumeCol": covStmt.startVolume = cellEntry
                                        break
                                    case "startIssueCol": covStmt.startIssue = cellEntry
                                        break
                                    case "endDateCol": covStmt.endDate = cellEntry
                                        break
                                    case "endVolumeCol": covStmt.endVolume = cellEntry
                                        break
                                    case "endIssueCol": covStmt.endIssue = cellEntry
                                        break
                                    case "accessStartDateCol": ieCandidate.accessStartDate = cellEntry
                                        break
                                    case "accessEndDateCol": ieCandidate.accessEndDate = cellEntry
                                        break
                                    case "embargoCol": covStmt.embargo = cellEntry
                                        break
                                    case "coverageDepthCol": covStmt.coverageDepth = cellEntry
                                        break
                                    case "coverageNotesCol": covStmt.coverageNote = cellEntry
                                        break
                                }
                            }
                            if(result.uploadPriceInfo && isUniqueListpriceColumn) {
                                try {
                                    switch(colName) {
                                        case "listPriceCol": ieCandidate.listPrice = escapeService.parseFinancialValue(cellEntry)
                                            break
                                        case "listCurrencyCol": ieCandidate.listCurrency = RefdataValue.getByValueAndCategory(cellEntry, RDConstants.CURRENCY)?.value
                                            break
                                        case "listPriceEurCol": ieCandidate.listPrice = escapeService.parseFinancialValue(cellEntry)
                                            ieCandidate.listCurrency = RefdataValue.getByValueAndCategory("EUR",RDConstants.CURRENCY).value
                                            break
                                        case "listPriceUsdCol": ieCandidate.listPrice = escapeService.parseFinancialValue(cellEntry)
                                            ieCandidate.listCurrency = RefdataValue.getByValueAndCategory("USD",RDConstants.CURRENCY).value
                                            break
                                        case "listPriceGbpCol": ieCandidate.listPrice = escapeService.parseFinancialValue(cellEntry)
                                            ieCandidate.listCurrency = RefdataValue.getByValueAndCategory("GBP",RDConstants.CURRENCY).value
                                            break
                                        case "localPriceCol": ieCandidate.localPrice = escapeService.parseFinancialValue(cellEntry)
                                            break
                                        case "localCurrencyCol": ieCandidate.localCurrency = RefdataValue.getByValueAndCategory(cellEntry,RDConstants.CURRENCY)?.value
                                            break
                                        case "priceDateCol": ieCandidate.priceDate = cellEntry
                                            break
                                    }
                                }
                                catch (NumberFormatException e) {
                                    log.error("Unparseable number ${cellEntry}")
                                }
                            }
                        }
                    }
                    if(ieCandIdentifier) {
                        ieCoverages.add(covStmt)
                        ieCandidate.coverages = ieCoverages
                        issueEntitlementOverwrite[ieCandIdentifier] = ieCandidate
                    }
                }
                result.identifiers = identifiers
                params.remove("kbartPreselct")
            }
            if(!params.pagination) {
                result.checked = [:]
                tipps.each { tipp ->
                    String serial
                    String electronicSerial
                    String checked = ""
                    if(tipp.title instanceof BookInstance) {
                        serial = tipp.title.getIdentifierValue('pISBN')
                        electronicSerial = tipp?.title?.getIdentifierValue('ISBN')
                    }
                    else if(tipp.title instanceof JournalInstance) {
                        serial = tipp?.title?.getIdentifierValue('ISSN')
                        electronicSerial = tipp?.title?.getIdentifierValue('eISSN')
                    }
                    if(result.identifiers?.zdbIds?.indexOf(tipp.title.getIdentifierValue('zdb')) > -1) {
                        checked = "checked"
                        result.issueEntitlementOverwrite[tipp.gokbId] = issueEntitlementOverwrite[tipp.title.getIdentifierValue('zdb')]
                    }
                    else if(result.identifiers?.onlineIds?.indexOf(electronicSerial) > -1) {
                        checked = "checked"
                        result.issueEntitlementOverwrite[tipp.gokbId] = issueEntitlementOverwrite[electronicSerial]
                    }
                    else if(result.identifiers?.printIds?.indexOf(serial) > -1) {
                        checked = "checked"
                        result.issueEntitlementOverwrite[tipp.gokbId] = issueEntitlementOverwrite[serial]
                    }
                    result.checked[tipp.gokbId] = checked
                }
                if(result.identifiers && result.identifiers.unidentified.size() > 0) {
                    String unidentifiedTitles = result.identifiers.unidentified.join(", ")
                    String escapedFileName
                    try {
                        escapedFileName = StringEscapeCategory.encodeAsHtml(result.identifiers.filename)
                    }
                    catch (Exception | Error e) {
                        log.error(e.printStackTrace())
                        escapedFileName = result.identifiers.filename
                    }
                    errorList.add(g.message(code:'subscription.details.addEntitlements.unidentified',args:[escapedFileName, unidentifiedTitles]))
                }
                checkedCache.put('checked',result.checked)
                checkedCache.put('issueEntitlementCandidates',result.issueEntitlementOverwrite)
            }
            else {
                result.checked = checkedCache.get('checked')
                result.issueEntitlementOverwrite = checkedCache.get('issueEntitlementCandidates')
            }

        if(errorList)
            flash.error = "<pre style='font-family:Lato,Arial,Helvetica,sans-serif;'>"+errorList.join("\n")+"</pre>"
        String filename = "${escapeService.escapeString(result.subscriptionInstance.dropdownNamingConvention())}_${DateUtil.SDF_NoTimeNoPoint.format(new Date())}"
        if(params.exportKBart) {
            response.setHeader("Content-disposition", "attachment; filename=${filename}.tsv")
            response.contentType = "text/tsv"
            ServletOutputStream out = response.outputStream
            Map<String,List> tableData = exportService.generateTitleExportKBART(tipps)
            out.withWriter { writer ->
                writer.write(exportService.generateSeparatorTableString(tableData.titleRow,tableData.columnData,'\t'))
            }
            out.flush()
            out.close()
        }
        else if(params.exportXLSX) {
            response.setHeader("Content-disposition", "attachment; filename=\"${filename}.xlsx\"")
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            Map<String,List> export = exportService.generateTitleExportXLS(tipps)
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
                Map<String,List> tableData = exportService.generateTitleExportCSV(tipps)
                out.withWriter { writer ->
                    writer.write(exportService.generateSeparatorTableString(tableData.titleRow,tableData.rows,';'))
                }
                out.flush()
                out.close()
            }
        }
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def manageEntitlementGroup() {
        log.debug("ManageEntitlementGroup .. params: ${params}")

        Map<String, Object> result = setResultGenericsAndCheckAccess(accessService.CHECK_VIEW_AND_EDIT)
        if (!result.editable) {
            response.sendError(401); return
        }

        result.titleGroups = result.subscriptionInstance.ieGroups

        result

    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    Map<String,Object> editEntitlementGroupItem() {
        Map<String, Object> result = setResultGenericsAndCheckAccess(accessService.CHECK_VIEW_AND_EDIT)
        if (!result.editable) {
            response.sendError(401); return
        }

        result.ie = IssueEntitlement.get(params.ie)

        if (result.ie && params.cmd == 'edit') {
            render template: 'editEntitlementGroupItem', model: result
            return
        }
        else if (result.ie && params.cmd == 'processing') {
            List deleteIssueEntitlementGroupItem = []
            result.ie.ieGroups.each{

                if(!(it.ieGroup.id.toString() in params.list('titleGroup'))){

                    deleteIssueEntitlementGroupItem << it.id
                }
            }

            if(deleteIssueEntitlementGroupItem){
                IssueEntitlementGroupItem.executeUpdate("DELETE IssueEntitlementGroupItem iegi where iegi.id in (:iegiIDs)", [iegiIDs: deleteIssueEntitlementGroupItem])
            }
            params.list('titleGroup').each {
                IssueEntitlementGroup issueEntitlementGroup = IssueEntitlementGroup.get(it)

                if(issueEntitlementGroup && !IssueEntitlementGroupItem.findByIeAndIeGroup(result.ie, issueEntitlementGroup))
                {
                    IssueEntitlementGroupItem issueEntitlementGroupItem = new IssueEntitlementGroupItem(
                            ie: result.ie,
                            ieGroup: issueEntitlementGroup)

                    if (!issueEntitlementGroupItem.save(flush: true)) {
                        log.error("Problem saving IssueEntitlementGroupItem ${issueEntitlementGroupItem.errors}")
                    }
                }
            }

        }
        redirect action: 'index', id: params.id
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def processCreateEntitlementGroup() {
        log.debug("processCreateEntitlementGroup .. params: ${params}")

        Map<String, Object> result = setResultGenericsAndCheckAccess(accessService.CHECK_VIEW_AND_EDIT)
        if (!result.editable) {
            response.sendError(401); return
        }

        if(!IssueEntitlementGroup.findBySubAndName(result.subscriptionInstance, params.name)) {

            IssueEntitlementGroup issueEntitlementGroup = new IssueEntitlementGroup(
                    name: params.name,
                    description: params.description ?: null,
                    sub: result.subscriptionInstance
            ).save(flush:true)
        }else{
             flash.error = g.message(code: "issueEntitlementGroup.create.fail")
        }


        redirect action: 'manageEntitlementGroup', id: params.id

    }

    @Secured(['ROLE_ADMIN'])
    Map renewEntitlements() {
        params.id = params.targetObjectId
        params.sourceObjectId = genericOIDService.resolveOID(params.targetObjectId)?.instanceOf?.id
        def result = loadDataFor_PackagesEntitlements()
        //result.comparisonMap = comparisonService.buildTIPPComparisonMap(result.sourceIEs+result.targetIEs)
        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def renewEntitlementsWithSurvey() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)
        result.surveyConfig = SurveyConfig.get(params.surveyConfigID)
        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset  ? Integer.parseInt(params.offset) : 0

        params.offset = 0
        params.max = 5000
        params.tab = params.tab ?: 'allIEs'

        Subscription newSub = params.targetObjectId ? genericOIDService.resolveOID(params.targetObjectId) : Subscription.get(params.id)
        Subscription baseSub = result.surveyConfig.subscription ?: newSub.instanceOf
        params.id = newSub.id
        params.sourceObjectId = baseSub.id

        List<IssueEntitlement> sourceIEs

        if(params.tab == 'allIEs') {
            sourceIEs = subscriptionService.getIssueEntitlementsWithFilter(baseSub, params)
        }
        if(params.tab == 'selectedIEs') {
            sourceIEs = subscriptionService.getIssueEntitlementsWithFilter(newSub, params+[ieAcceptStatusNotFixed: true])
        }
        List<IssueEntitlement> targetIEs = subscriptionService.getIssueEntitlementsWithFilter(newSub, [max: 5000, offset: 0])

        List<IssueEntitlement> allIEs = subscriptionService.getIssueEntitlementsFixed(baseSub)

        result.subjects = subscriptionService.getSubjects(allIEs.collect {it.tipp.title.id})
        result.seriesNames = subscriptionService.getSeriesNames(allIEs.collect {it.tipp.title.id})
        result.countSelectedIEs = subscriptionService.getIssueEntitlementsNotFixed(newSub).size()
        result.countAllIEs = allIEs.size()
        result.countAllSourceIEs = sourceIEs.size()
        result.num_ies_rows = sourceIEs.size()//subscriptionService.getIssueEntitlementsFixed(baseSub).size()
        result.sourceIEs = sourceIEs.drop(result.offset).take(result.max)
        result.targetIEs = targetIEs
        result.newSub = newSub
        result.subscription = baseSub
        result.subscriber = result.newSub.getSubscriber()
        result.editable = surveyService.isEditableIssueEntitlementsSurvey(result.institution, result.surveyConfig)

        params.offset = result.offset
        params.max = result.max

        String filename = "${escapeService.escapeString(message(code:'renewEntitlementsWithSurvey.selectableTitles')+'_'+result.newSub.dropdownNamingConvention())}"

        if (params.exportKBart) {
            response.setHeader("Content-disposition", "attachment; filename=${filename}.tsv")
            response.contentType = "text/tsv"
            ServletOutputStream out = response.outputStream
            Map<String, List> tableData = exportService.generateTitleExportKBART(sourceIEs)
            out.withWriter { writer ->
                writer.write(exportService.generateSeparatorTableString(tableData.titleRow, tableData.columnData, '\t'))
            }
            out.flush()
            out.close()
        }else if(params.exportXLS) {
            response.setHeader("Content-disposition", "attachment; filename=\"${filename}.xlsx\"")
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            Map<String,List> export = exportService.generateTitleExportXLS(sourceIEs)
            Map sheetData = [:]
            sheetData[g.message(code:'renewEntitlementsWithSurvey.selectableTitles')] = [titleRow:export.titles,columnData:export.rows]
            SXSSFWorkbook workbook = exportService.generateXLSXWorkbook(sheetData)
            workbook.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            workbook.dispose()
            return
        }
        else {
            withFormat {
                html result
            }
        }

        result

    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def showEntitlementsRenewWithSurvey() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.editable = accessService.checkMinUserOrgRole(result.user, result.institution, 'INST_EDITOR')

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        result.surveyConfig = SurveyConfig.get(params.id)
        result.surveyInfo = result.surveyConfig.surveyInfo

        result.subscriptionInstance =  result.surveyConfig.subscription

        result.ies = subscriptionService.getIssueEntitlementsNotFixed(result.surveyConfig.subscription.getDerivedSubscriptionBySubscribers(result.institution))

        String filename = "renewEntitlements_${escapeService.escapeString(result.subscriptionInstance.dropdownNamingConvention())}"

        if (params.exportKBart) {
            response.setHeader("Content-disposition", "attachment; filename=${filename}.tsv")
            response.contentType = "text/tsv"
            ServletOutputStream out = response.outputStream
            Map<String, List> tableData = exportService.generateTitleExportKBART(result.ies)
            out.withWriter { writer ->
                writer.write(exportService.generateSeparatorTableString(tableData.titleRow, tableData.columnData, '\t'))
            }
            out.flush()
            out.close()
        }else if(params.exportXLS) {
            response.setHeader("Content-disposition", "attachment; filename=\"${filename}.xlsx\"")
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            Map<String,List> export = exportService.generateTitleExportXLS(result.ies)
            Map sheetData = [:]
            sheetData[g.message(code:'subscription.details.renewEntitlements.label')] = [titleRow:export.titles,columnData:export.rows]
            SXSSFWorkbook workbook = exportService.generateXLSXWorkbook(sheetData)
            workbook.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            workbook.dispose()
        }
        else {
            withFormat {
                html {
                    result
                }
            }
        }
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def unlinkLicense() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if(!result) {
            response.sendError(401)
            return
        }
        subscriptionService.setOrgLicRole(result.subscription,License.get(params.license),true)
        redirect(url: request.getHeader('referer'))
    }

    @DebugAnnotation(perm="ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def linkNextPrevMemberSub() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if(!result) {
            response.sendError(401)
            return
        }

        Subscription memberSub = Subscription.get(Long.parseLong(params.memberSubID))
        Org org = Org.get(Long.parseLong(params.memberOrg))
        Subscription prevMemberSub = (result.navPrevSubscription?.size() > 0) ? result.navPrevSubscription[0].getDerivedSubscriptionBySubscribers(org) : null
        Subscription nextMemberSub = (result.navNextSubscription?.size() > 0) ? result.navNextSubscription[0].getDerivedSubscriptionBySubscribers(org) : null

        if(params.prev && prevMemberSub) {

            Links prevLink = new Links(source: genericOIDService.getOID(memberSub), destination: genericOIDService.getOID(prevMemberSub), linkType: RDStore.LINKTYPE_FOLLOWS, owner: contextService.org)
            if (!prevLink.save(flush: true)) {
                log.error("Problem linking to previous subscription: ${prevLink.errors}")
                redirect(url: request.getHeader('referer'))
            }else {
                redirect(action: 'show', id: prevMemberSub.id)
            }
        }

        if(params.next && nextMemberSub) {

            Links nextLink = new Links(source: genericOIDService.getOID(nextMemberSub), destination: genericOIDService.getOID(memberSub), linkType: RDStore.LINKTYPE_FOLLOWS, owner: contextService.org)
            if (!nextLink.save(flush: true)) {
                log.error("Problem linking to next subscription: ${nextLink.errors}")
                redirect(url: request.getHeader('referer'))
            }else {
                redirect(action: 'show', id: nextMemberSub.id)
            }
        }
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def members() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }
//        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
//        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;
        result.propList = PropertyDefinition.findAllPublicAndPrivateOrgProp(contextService.org)

        //if (params.showDeleted == 'Y') {
        Set<RefdataValue> subscriberRoleTypes = [RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIBER_CONS_HIDDEN, RDStore.OR_SUBSCRIBER_COLLECTIVE]
        result.validSubChilds = Subscription.executeQuery('select s from Subscription s join s.orgRelations oo where s.instanceOf = :parent and oo.roleType in :subscriberRoleTypes order by oo.org.sortname asc, oo.org.name asc',[parent:result.subscriptionInstance,subscriberRoleTypes:subscriberRoleTypes])
        /*Sortieren --> an DB abgegeben
        result.validSubChilds = validSubChilds.sort { a, b ->
            def sa = a.getSubscriber()
            def sb = b.getSubscriber()
            (sa.sortname ?: sa.name).compareTo((sb.sortname ?: sb.name))
        }*/
        ArrayList<Long> filteredOrgIds = getOrgIdsForFilter()
        result.filteredSubChilds = []
        result.validSubChilds.each { sub ->
            List<Org> subscr = sub.getAllSubscribers()
            def filteredSubscr = []
            subscr.each { Org subOrg ->
                if (filteredOrgIds.contains(subOrg.id)) {
                    filteredSubscr << subOrg
                }
            }
            if (filteredSubscr) {
                if (params.subRunTimeMultiYear || params.subRunTime) {

                    if (params.subRunTimeMultiYear && !params.subRunTime) {
                        if(sub?.isMultiYear) {
                            result.filteredSubChilds << [sub: sub, orgs: filteredSubscr]
                        }
                    }else if (!params.subRunTimeMultiYear && params.subRunTime){
                        if(!sub?.isMultiYear) {
                            result.filteredSubChilds << [sub: sub, orgs: filteredSubscr]
                        }
                    }
                    else {
                        result.filteredSubChilds << [sub: sub, orgs: filteredSubscr]
                    }
                }
                else {
                    result.filteredSubChilds << [sub: sub, orgs: filteredSubscr]
                }
            }
        }

//        def deletedSubChilds = Subscription.findAllByInstanceOfAndStatus(
//                result.subscriptionInstance,
//                SUBSCRIPTION_DELETED
//          )
//        result.deletedSubChilds = deletedSubChilds
        //}
        //else {
        //    result.subscriptionChildren = Subscription.executeQuery(
        //           "select sub from Subscription as sub where sub.instanceOf = ? and sub.status.value != 'Deleted'",
        //            [result.subscriptionInstance]
        //    )
        //}

        result.filterSet = params.filterSet ? true : false

        SimpleDateFormat sdf = new SimpleDateFormat('yyyy-MM-dd')
        String datetoday = sdf.format(new Date(System.currentTimeMillis()))
        String filename = escapeService.escapeString(result.subscription.name) + "_" + g.message(code: 'subscriptionDetails.members.members') + "_" + datetoday
        Set<Map<String,Object>> orgs = []
        if (params.exportXLS || params.format) {
            Map allContacts = Person.getPublicAndPrivateEmailByFunc('General contact person',result.institution)
            Map publicContacts = allContacts.publicContacts
            Map privateContacts = allContacts.privateContacts
            result.filteredSubChilds.each { row ->
                Subscription subChild = (Subscription) row.sub
                row.orgs.each { Org subscr ->
                    Map<String,Object> org = [:]
                    org.name = subscr.name
                    org.sortname = subscr.sortname
                    org.shortname = subscr.shortname
                    org.globalUID = subChild.globalUID
                    org.libraryType = subscr.libraryType
                    org.libraryNetwork = subscr.libraryNetwork
                    org.funderType = subscr.funderType
                    org.region = subscr.region
                    org.country = subscr.country
                    org.startDate = subChild.startDate ? subChild.startDate.format(g.message(code:'default.date.format.notime')) : ''
                    org.endDate = subChild.endDate ? subChild.endDate.format(g.message(code:'default.date.format.notime')) : ''
                    org.isPublicForApi = subChild.isPublicForApi ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value")
                    org.hasPerpetualAccess = subChild.hasPerpetualAccess ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value")
                    org.status = subChild.status
                    org.customProperties = subscr.propertySet.findAll{ it.type.tenant == null && ((it.tenant?.id == result.institution.id && it.isPublic) || it.tenant == null) }
                    org.privateProperties = subscr.propertySet.findAll{ it.type.tenant?.id == result.institution.id }
                    Set generalContacts = []
                    if (publicContacts.get(subscr))
                        generalContacts.addAll(publicContacts.get(subscr))
                    if (privateContacts.get(subscr))
                        generalContacts.addAll(privateContacts.get(subscr))
                    org.generalContacts = generalContacts.join("; ")
                    orgs << org
                }
            }
        }

        if (params.exportXLS) {
            exportOrg(orgs, filename, true, 'xlsx')
            return
        }else if (params.exportShibboleths || params.exportEZProxys || params.exportProxys || params.exportIPs){
            SXSSFWorkbook wb
            if (params.exportIPs) {
                filename = "${datetoday}_" + escapeService.escapeString(g.message(code: 'subscriptionDetails.members.exportIPs.fileName'))
                wb = (SXSSFWorkbook) accessPointService.exportIPsOfOrgs(result.filteredSubChilds.orgs.flatten())
            }else if (params.exportProxys) {
                filename = "${datetoday}_" + escapeService.escapeString(g.message(code: 'subscriptionDetails.members.exportProxys.fileName'))
                wb = (SXSSFWorkbook) accessPointService.exportProxysOfOrgs(result.filteredSubChilds.orgs.flatten())
            }else if (params.exportEZProxys) {
                filename = "${datetoday}_" + escapeService.escapeString(g.message(code: 'subscriptionDetails.members.exportEZProxys.fileName'))
                wb = (SXSSFWorkbook) accessPointService.exportEZProxysOfOrgs(result.filteredSubChilds.orgs.flatten())
            }else if (params.exportShibboleths) {
                filename = "${datetoday}_" + escapeService.escapeString(g.message(code: 'subscriptionDetails.members.exportShibboleths.fileName'))
                wb = (SXSSFWorkbook) accessPointService.exportShibbolethsOfOrgs(result.filteredSubChilds.orgs.flatten())
            }

            response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            wb.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            wb.dispose()
            return
        }
        else {
            withFormat {
                html {
                    result
                }
                csv {
                    response.setHeader("Content-disposition", "attachment; filename=\"${filename}.csv\"")
                    response.contentType = "text/csv"
                    ServletOutputStream out = response.outputStream
                    out.withWriter { writer ->
                        writer.write((String) exportOrg(orgs, filename, true, "csv"))
                    }
                    out.close()
                }
            }
        }
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def surveys() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }
//        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
//        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        result.contextOrg = contextService.getOrg()

        result.surveys = SurveyConfig.executeQuery("from SurveyConfig as surConfig where surConfig.subscription = :sub and surConfig.surveyInfo.status not in (:invalidStatuses) and (exists (select surOrg from SurveyOrg surOrg where surOrg.surveyConfig = surConfig AND surOrg.org = :org))",
                [sub: result.subscription.instanceOf,
                 org: result.contextOrg,
                 invalidStatuses: [RDStore.SURVEY_IN_PROCESSING, RDStore.SURVEY_READY]])

       result
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_CONSORTIUM", "INST_EDITOR")
    })
    def surveysConsortia() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }
//        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
//        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;


        result.surveys = result.subscription ? SurveyConfig.findAllBySubscription(result.subscription) : null

        result
    }

    @DebugAnnotation(perm = "ORG_INST_COLLECTIVE,ORG_CONSORTIUM", affil = "INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST_COLLECTIVE,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def linkLicenseMembers() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        result.parentSub = result.subscriptionInstance.instanceOf && result.subscriptionInstance._getCalculatedType() != CalculatedType.TYPE_PARTICIPATION_AS_COLLECTIVE ? result.subscriptionInstance.instanceOf : result.subscriptionInstance

        result.parentLicenses = Links.executeQuery('select li.sourceLicense from Links li where li.destinationSubscription = :subscription and li.linkType = :linkType',[subscription:result.parentSub,linkType:RDStore.LINKTYPE_LICENSE])

        result.validLicenses = []

        if(result.parentLicenses) {
            def childLicenses = License.where {
                instanceOf in result.parentLicenses
            }

            childLicenses?.each {
                result.validLicenses << it
            }
        }

        def validSubChilds = Subscription.findAllByInstanceOf(result.parentSub)
        //Sortieren
        result.validSubChilds = validSubChilds.sort { a, b ->
            def sa = a.getSubscriber()
            def sb = b.getSubscriber()
            (sa.sortname ?: sa.name).compareTo((sb.sortname ?: sb.name))
        }

        def oldID =  params.id
        params.id = result.parentSub.id

        ArrayList<Long> filteredOrgIds = getOrgIdsForFilter()
        result.filteredSubChilds = new ArrayList<Subscription>()
        result.validSubChilds.each { sub ->
            List<Org> subscr = sub.getAllSubscribers()
            def filteredSubscr = []
            subscr.each { Org subOrg ->
                if (filteredOrgIds.contains(subOrg.id)) {
                    filteredSubscr << subOrg
                }
            }
            if (filteredSubscr) {
                result.filteredSubChilds << [sub: sub, orgs: filteredSubscr]
            }
        }

        params.id = oldID

        result
    }

    @DebugAnnotation(perm = "ORG_INST_COLLECTIVE,ORG_CONSORTIUM", affil = "INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST_COLLECTIVE,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def processLinkLicenseMembers() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
            return
        }
        if(formService.validateToken(params)) {
            result.parentSub = result.subscriptionInstance.instanceOf && result.subscriptionInstance._getCalculatedType() != CalculatedType.TYPE_PARTICIPATION_AS_COLLECTIVE ? result.subscriptionInstance.instanceOf : result.subscriptionInstance

            /*RefdataValue licenseeRoleType = OR_LICENSEE_CONS
            if(result.subscriptionInstance._getCalculatedType() == CalculatedType.TYPE_PARTICIPATION_AS_COLLECTIVE)
                licenseeRoleType = OR_LICENSEE_COLL

            result.parentLicense = result.parentSub.owner*/

            Set<Subscription> validSubChilds = Subscription.findAllByInstanceOf(result.parentSub)

            List selectedMembers = params.list("selectedMembers")

            List<GString> changeAccepted = []
            validSubChilds.each { subChild ->
                if (selectedMembers.contains(subChild.id.toString())) { //toString needed for type check
                    License newLicense = License.get(params.license_All)
                    if(subscriptionService.setOrgLicRole(subChild,newLicense,params.processOption == 'unlinkLicense'))
                        changeAccepted << "${subChild.name} (${message(code:'subscription.linkInstance.label')} ${subChild.getSubscriber().sortname})"
                }
            }
            if (changeAccepted) {
                flash.message = message(code: 'subscription.linkLicenseMembers.changeAcceptedAll', args: [changeAccepted.join(', ')])
            }
        }

        redirect(action: 'linkLicenseMembers', id: params.id)
    }

    @DebugAnnotation(perm = "ORG_INST_COLLECTIVE,ORG_CONSORTIUM", affil = "INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST_COLLECTIVE,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def processUnLinkLicenseMembers() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }
        if(formService.validateToken(params)) {
            result.parentSub = result.subscriptionInstance.instanceOf ? result.subscriptionInstance.instanceOf : result.subscriptionInstance

            //result.parentLicense = result.parentSub.owner TODO we need to iterate over ALL linked licenses!

            List selectedMembers = params.list("selectedMembers")

            def validSubChilds = Subscription.findAllByInstanceOf(result.parentSub)

            def removeLic = []
            validSubChilds.each { subChild ->
                if(subChild.id in selectedMembers || params.unlinkAll == 'true') {
                    Links.findAllByDestinationAndLinkType(genericOIDService.getOID(subChild),RDStore.LINKTYPE_LICENSE).each { Links li ->
                        License license = genericOIDService.resolveOID(li.source)
                        if (subscriptionService.setOrgLicRole(subChild,license,true)) {
                            removeLic << "${subChild.name} (${message(code:'subscription.linkInstance.label')} ${subChild.getSubscriber().sortname})"
                        }
                    }
                }

            }
            if (removeLic) {
                flash.message = message(code: 'subscription.linkLicenseMembers.removeAcceptedAll', args: [removeLic.join(', ')])
            }
        }

        redirect(action: 'linkLicenseMembers', id: params.id)
    }

    @DebugAnnotation(perm = "ORG_INST_COLLECTIVE,ORG_CONSORTIUM", affil = "INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST_COLLECTIVE,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def linkPackagesMembers() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        result.parentSub = result.subscriptionInstance.instanceOf ? result.subscriptionInstance.instanceOf : result.subscriptionInstance

        result.parentPackages = result.parentSub.packages.sort { it.pkg.name }

        result.validPackages = result.parentPackages

        def validSubChilds = Subscription.findAllByInstanceOf(result.parentSub)
        //Sortieren
        result.validSubChilds = validSubChilds.sort { a, b ->
            def sa = a.getSubscriber()
            def sb = b.getSubscriber()
            (sa.sortname ?: sa.name).compareTo((sb.sortname ?: sb.name))
        }

        def oldID = params.id
        if(result.subscription._getCalculatedType() in [CalculatedType.TYPE_CONSORTIAL, CalculatedType.TYPE_COLLECTIVE])
            params.id = result.parentSub.id

        ArrayList<Long> filteredOrgIds = getOrgIdsForFilter()
        result.filteredSubChilds = new ArrayList<Subscription>()
        result.validSubChilds.each { sub ->
            List<Org> subscr = sub.getAllSubscribers()
            def filteredSubscr = []
            subscr.each { Org subOrg ->
                if (filteredOrgIds.contains(subOrg.id)) {
                    filteredSubscr << subOrg
                }
            }
            if (filteredSubscr) {
                result.filteredSubChilds << [sub: sub, orgs: filteredSubscr]
            }
        }

        params.id = oldID

        result
    }

    @DebugAnnotation(perm = "ORG_INST_COLLECTIVE,ORG_CONSORTIUM", affil = "INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST_COLLECTIVE,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def processLinkPackagesMembers() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        if(formService.validateToken(params)) {
            result.parentSub = result.subscriptionInstance.instanceOf ? result.subscriptionInstance.instanceOf : result.subscriptionInstance

            //List changeAccepted = []
            //List changeAcceptedwithIE = []
            //List changeFailed = []

            List selectedMembers = params.list("selectedMembers")

            if(selectedMembers && params.package_All){
                Package pkg_to_link = SubscriptionPackage.get(params.package_All).pkg
                selectedMembers.each { id ->
                    Subscription subChild = Subscription.get(Long.parseLong(id))
                    if (params.processOption == 'linkwithIE' || params.processOption == 'linkwithoutIE') {
                        if (!(pkg_to_link in subChild.packages.pkg)) {

                            if (params.processOption == 'linkwithIE') {

                                pkg_to_link.addToSubscriptionCurrentStock(subChild, result.parentSub)
                                //changeAcceptedwithIE << "${subChild?.name} (${message(code: 'subscription.linkInstance.label')} ${subChild?.orgRelations.find { it.roleType in [RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIBER_COLLECTIVE] }.org.sortname})"

                            } else {
                                pkg_to_link.addToSubscription(subChild, false)
                                //changeAccepted << "${subChild?.name} (${message(code: 'subscription.linkInstance.label')} ${subChild?.orgRelations.find { it.roleType in [RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIBER_COLLECTIVE] }.org.sortname})"

                            }
                        } /*else {
                            //changeFailed << "${subChild?.name} (${message(code: 'subscription.linkInstance.label')} ${subChild?.orgRelations.find { it.roleType in [RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIBER_COLLECTIVE] }.org.sortname})"
                        }

                        if (changeAccepted) {
                            //flash.message = message(code: 'subscription.linkPackagesMembers.changeAcceptedAll', args: [pkg_to_link.name, changeAccepted.join(", ")])
                        }
                        if (changeAcceptedwithIE) {
                            ///flash.message = message(code: 'subscription.linkPackagesMembers.changeAcceptedIEAll', args: [pkg_to_link.name, changeAcceptedwithIE.join(", ")])
                        }

                        if (!changeAccepted && !changeAcceptedwithIE){
                            //flash.error = message(code: 'subscription.linkPackagesMembers.noChanges')
                        }*/

                    }

                    if (params.processOption == 'unlinkwithIE' || params.processOption == 'unlinkwithoutIE') {
                        if (pkg_to_link in subChild.packages.pkg) {

                            if (params.processOption == 'unlinkwithIE') {
                                pkg_to_link.unlinkFromSubscription(subChild, true)
                                /*if() {
                                    //changeAcceptedwithIE << "${subChild?.name} (${message(code: 'subscription.linkInstance.label')} ${subChild?.orgRelations.find { it.roleType in [RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIBER_COLLECTIVE] }.org.sortname})"
                                }*/
                            } else {
                                pkg_to_link.unlinkFromSubscription(subChild, false)
                                /*if() {
                                    //changeAccepted << "${subChild?.name} (${message(code: 'subscription.linkInstance.label')} ${subChild?.orgRelations.find { it.roleType in [RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIBER_COLLECTIVE] }.org.sortname})"
                                }*/
                            }
                        } /*else {
                            //changeFailed << "${subChild?.name} (${message(code: 'subscription.linkInstance.label')} ${subChild?.orgRelations.find { it.roleType in [RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIBER_COLLECTIVE] }.org.sortname})"
                        }

                        if (changeAccepted) {
                            //flash.message = message(code: 'subscription.linkPackagesMembers.changeAcceptedUnlinkAll', args: [pkg_to_link.name, changeAccepted.join(", ")])
                        }
                        if (changeAcceptedwithIE) {
                            //flash.message = message(code: 'subscription.linkPackagesMembers.changeAcceptedUnlinkWithIEAll', args: [pkg_to_link.name, changeAcceptedwithIE.join(", ")])
                        }

                        if (!changeAccepted && !changeAcceptedwithIE){
                            //flash.error = message(code: 'subscription.linkPackagesMembers.noChanges')
                        }*/

                    }
                }

            }else {
                if(!selectedMembers) {
                    flash.error = message(code: 'subscription.linkPackagesMembers.noSelectedMember')
                }

                if(!params.package_All) {
                    flash.error = message(code: 'subscription.linkPackagesMembers.noSelectedPackage')
                }
            }
        }

        redirect(action: 'linkPackagesMembers', id: params.id)
    }

    @DebugAnnotation(perm = "ORG_INST_COLLECTIVE,ORG_CONSORTIUM", affil = "INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST_COLLECTIVE,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def processUnLinkPackagesConsortia() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }
        flash.error = ""

        result.parentSub = result.subscriptionInstance.instanceOf ? result.subscriptionInstance.instanceOf : result.subscriptionInstance

        result.parentPackages = result.parentSub.packages.sort { it.pkg.name }

        Set<Subscription> validSubChilds = Subscription.findAllByInstanceOf(result.parentSub)

        validSubChilds.each { Subscription subChild ->

            subChild.packages.pkg.each { pkg ->

                    if(!CostItem.executeQuery('select ci from CostItem ci where ci.subPkg.subscription = :sub and ci.subPkg.pkg = :pkg',[pkg:pkg,sub:subChild])) {
                        String query = "from IssueEntitlement ie, Package pkg where ie.subscription =:sub and pkg.id =:pkg_id and ie.tipp in ( select tipp from TitleInstancePackagePlatform tipp where tipp.pkg.id = :pkg_id ) "
                        Map<String,Object> queryParams = [sub: subChild, pkg_id: pkg.id]


                        if (subChild.isEditableBy(result.user)) {
                            result.editable = true
                            if (params.withIE) {
                                if(pkg.unlinkFromSubscription(subChild, true)){
                                    flash.message = message(code: 'subscription.linkPackagesMembers.unlinkInfo.withIE.successful')
                                }else {
                                    flash.error = message(code: 'subscription.linkPackagesMembers.unlinkInfo.withIE.fail')
                                }
                            } else {
                                if(pkg.unlinkFromSubscription(subChild, false)){
                                    flash.message = message(code: 'subscription.linkPackagesMembers.unlinkInfo.onlyPackage.successful')
                                }else {
                                    flash.error = message(code: 'subscription.linkPackagesMembers.unlinkInfo.onlyPackage.fail')
                                }
                            }
                        }
                    } else {
                        flash.error += "Für das Paket ${pkg.name} von ${subChild.getSubscriber().name} waren noch Kosten anhängig. Das Paket wurde daher nicht entknüpft."
                        }
                }
            }
        redirect(action: 'linkPackagesMembers', id: params.id)
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_CONSORTIUM", "INST_EDITOR")
    })
    def propertiesMembers() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        result.filterPropDef = params.filterPropDef ? genericOIDService.resolveOID(params.filterPropDef.replace(" ", "")) : null

        params.remove('filterPropDef')


        result.parentSub = result.subscriptionInstance

        Set<Subscription> validSubChildren = Subscription.executeQuery("select oo.sub from OrgRole oo where oo.sub.instanceOf = :parent and oo.roleType = :roleType order by oo.org.sortname asc",[parent:result.parentSub,roleType:RDStore.OR_SUBSCRIBER_CONS])
        if(validSubChildren) {
            result.validSubChilds = validSubChildren
            Set<PropertyDefinition> propList = PropertyDefinition.executeQuery("select distinct(sp.type) from SubscriptionProperty sp where sp.owner in (:subscriptionSet) and sp.tenant = :ctx and sp.instanceOf = null",[subscriptionSet:validSubChildren,ctx:result.institution])
            propList.addAll(result.parentSub.propertySet.type)
            result.propList = propList

            def oldID = params.id
            params.id = result.parentSub.id

            result.filteredSubChilds = validSubChildren

            params.id = oldID

            List<Subscription> childSubs = result.parentSub.getNonDeletedDerivedSubscriptions()
            if(childSubs) {
                String localizedName
                switch(LocaleContextHolder.getLocale()) {
                    case Locale.GERMANY:
                    case Locale.GERMAN: localizedName = "name_de"
                        break
                    default: localizedName = "name_en"
                        break
                }
                String query = "select sp.type from SubscriptionProperty sp where sp.owner in (:subscriptionSet) and sp.tenant = :context and sp.instanceOf = null order by sp.type.${localizedName} asc"
                Set<PropertyDefinition> memberProperties = PropertyDefinition.executeQuery(query, [subscriptionSet:childSubs, context:result.institution] )

                result.memberProperties = memberProperties
            }
        }

        result
    }

    @DebugAnnotation(perm = "ORG_INST_COLLECTIVE,ORG_CONSORTIUM", affil = "INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST_COLLECTIVE,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def subscriptionPropertiesMembers() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        params.tab = params.tab ?: 'generalProperties'

        result.parentSub = result.subscriptionInstance.instanceOf && result.subscription._getCalculatedType() != CalculatedType.TYPE_PARTICIPATION_AS_COLLECTIVE ? result.subscriptionInstance.instanceOf : result.subscriptionInstance

        def validSubChilds = Subscription.findAllByInstanceOf(result.parentSub)
        //Sortieren
        result.validSubChilds = validSubChilds.sort { a, b ->
            def sa = a.getSubscriber()
            def sb = b.getSubscriber()
            (sa.sortname ?: sa.name).compareTo((sb.sortname ?: sb.name))
        }

        def oldID = params.id
        params.id = result.parentSub.id

        ArrayList<Long> filteredOrgIds = getOrgIdsForFilter()
        result.filteredSubChilds = new ArrayList<Subscription>()
        result.validSubChilds.each { sub ->
            List<Org> subscr = sub.getAllSubscribers()
            def filteredSubscr = []
            subscr.each { Org subOrg ->
                if (filteredOrgIds.contains(subOrg.id)) {
                    filteredSubscr << subOrg
                }
            }
            if (filteredSubscr) {
                result.filteredSubChilds << [sub: sub, orgs: filteredSubscr]
            }
        }

        if(params.tab == 'providerAgency') {

            result.modalPrsLinkRole = RefdataValue.getByValueAndCategory('Specific subscription editor', RDConstants.PERSON_RESPONSIBILITY)
            result.modalVisiblePersons = addressbookService.getPrivatePersonsByTenant(contextService.getOrg())
            result.visibleOrgRelations = []
            result.parentSub.orgRelations?.each { or ->
                if (!(or.org?.id == contextService.getOrg().id) && !(or.roleType.id in [RDStore.OR_SUBSCRIBER.id, RDStore.OR_SUBSCRIBER_CONS.id, RDStore.OR_SUBSCRIBER_COLLECTIVE.id])) {
                    result.visibleOrgRelations << or
                }
            }
            result.visibleOrgRelations.sort { it?.org?.sortname }
        }

        params.id = oldID

        result
    }

    @DebugAnnotation(perm = "ORG_INST_COLLECTIVE,ORG_CONSORTIUM", affil = "INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST_COLLECTIVE,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def processPropertiesMembers() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        if(formService.validateToken(params)) {
            result.filterPropDef = params.filterPropDef ? genericOIDService.resolveOID(params.filterPropDef.replace(" ", "")) : null

            result.parentSub = result.subscriptionInstance.instanceOf && result.subscription._getCalculatedType() != CalculatedType.TYPE_PARTICIPATION_AS_COLLECTIVE ? result.subscriptionInstance.instanceOf : result.subscriptionInstance


            if (params.filterPropDef && params.filterPropValue) {

                def filterPropDef = params.filterPropDef
                def propDef = genericOIDService.resolveOID(filterPropDef.replace(" ", ""))


                def newProperties = 0
                def changeProperties = 0

                if (propDef) {

                    List selectedMembers = params.list("selectedMembers")

                    if(selectedMembers){
                        selectedMembers.each { subId ->
                            Subscription subChild = Subscription.get(subId)
                            if (propDef?.tenant != null) {
                                //private Property
                                Subscription owner = subChild

                                def existingProps = owner.propertySet.findAll {
                                    it.owner.id == owner.id &&  it.type.id == propDef.id && it.tenant.id == result.institution && !it.isPublic
                                }
                                existingProps.removeAll { it.type.name != propDef.name } // dubious fix

                                if (existingProps.size() == 0 || propDef.multipleOccurrence) {
                                    def newProp = PropertyDefinition.createGenericProperty(PropertyDefinition.PRIVATE_PROPERTY, owner, propDef, result.institution)
                                    if (newProp.hasErrors()) {
                                        log.error(newProp.errors.toString())
                                    } else {
                                        log.debug("New private property created: " + newProp.type.name)

                                        newProperties++
                                        updateProperty(newProp, params.filterPropValue)
                                    }
                                }

                                if (existingProps.size() == 1){
                                    def privateProp = SubscriptionProperty.get(existingProps[0].id)
                                    changeProperties++
                                    updateProperty(privateProp, params.filterPropValue)

                                }

                            } else {
                                //custom Property
                                def owner = subChild

                                def existingProp = owner.propertySet.find {
                                    it.type.id == propDef.id && it.owner.id == owner.id
                                }

                                if (existingProp == null || propDef.multipleOccurrence) {
                                    def newProp = PropertyDefinition.createGenericProperty(PropertyDefinition.CUSTOM_PROPERTY, owner, propDef, result.institution)
                                    if (newProp.hasErrors()) {
                                        log.error(newProp.errors.toString())
                                    } else {
                                        log.debug("New custom property created: " + newProp.type.name)
                                        newProperties++
                                        updateProperty(newProp, params.filterPropValue)
                                    }
                                }

                                if (existingProp){
                                    SubscriptionProperty customProp = SubscriptionProperty.get(existingProp.id)
                                    changeProperties++
                                    updateProperty(customProp, params.filterPropValue)
                                }
                            }

                        }
                        flash.message = message(code: 'subscription.propertiesMembers.successful', args: [newProperties, changeProperties])
                    }else{
                        flash.error = message(code: 'subscription.propertiesMembers.successful', args: [newProperties, changeProperties])
                    }

                }

            }
        }

        def filterPropDef = params.filterPropDef
        def id = params.id
        redirect(action: 'propertiesMembers', id: id, params: [filterPropDef: filterPropDef])
    }

    @DebugAnnotation(perm = "ORG_INST_COLLECTIVE,ORG_CONSORTIUM", affil = "INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST_COLLECTIVE,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def processSubscriptionPropertiesMembers() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        List selectedMembers = params.list("selectedMembers")

        if(selectedMembers){
            def change = []
            def noChange = []
            selectedMembers.each { subID ->

                        Subscription subChild = Subscription.get(subID)

                        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
                        def startDate = params.valid_from ? sdf.parse(params.valid_from) : null
                        def endDate = params.valid_to ? sdf.parse(params.valid_to) : null


                        if(startDate && !auditService.getAuditConfig(subChild?.instanceOf, 'startDate'))
                        {
                            subChild?.startDate = startDate
                            change << message(code: 'default.startDate.label')
                        }

                        if(startDate && auditService.getAuditConfig(subChild?.instanceOf, 'startDate'))
                        {
                            noChange << message(code: 'default.startDate.label')
                        }

                        if(endDate && !auditService.getAuditConfig(subChild?.instanceOf, 'endDate'))
                        {
                            subChild?.endDate = endDate
                            change << message(code: 'default.endDate.label')
                        }

                        if(endDate && auditService.getAuditConfig(subChild?.instanceOf, 'endDate'))
                        {
                            noChange << message(code: 'default.endDate.label')
                        }


                        if(params.status && !auditService.getAuditConfig(subChild?.instanceOf, 'status'))
                        {
                            subChild?.status = RefdataValue.get(params.status) ?: subChild?.status
                            change << message(code: 'subscription.status.label')
                        }
                        if(params.status && auditService.getAuditConfig(subChild?.instanceOf, 'status'))
                        {
                            noChange << message(code: 'subscription.status.label')
                        }

                        if(params.kind && !auditService.getAuditConfig(subChild?.instanceOf, 'kind'))
                        {
                            subChild?.kind = RefdataValue.get(params.kind) ?: subChild?.kind
                            change << message(code: 'subscription.kind.label')
                        }
                        if(params.kind && auditService.getAuditConfig(subChild?.instanceOf, 'kind'))
                        {
                            noChange << message(code: 'subscription.kind.label')
                        }

                        if(params.form && !auditService.getAuditConfig(subChild?.instanceOf, 'form'))
                        {
                            subChild?.form = RefdataValue.get(params.form) ?: subChild?.form
                            change << message(code: 'subscription.form.label')
                        }
                        if(params.form && auditService.getAuditConfig(subChild?.instanceOf, 'form'))
                        {
                            noChange << message(code: 'subscription.form.label')
                        }

                        if(params.resource && !auditService.getAuditConfig(subChild?.instanceOf, 'resource'))
                        {
                            subChild?.resource = RefdataValue.get(params.resource) ?: subChild?.resource
                            change << message(code: 'subscription.resource.label')
                        }
                        if(params.resource && auditService.getAuditConfig(subChild?.instanceOf, 'resource'))
                        {
                            noChange << message(code: 'subscription.resource.label')
                        }

                        if(params.isPublicForApi && !auditService.getAuditConfig(subChild?.instanceOf, 'isPublicForApi'))
                        {
                            subChild?.isPublicForApi = RefdataValue.get(params.isPublicForApi) == RDStore.YN_YES
                            change << message(code: 'subscription.isPublicForApi.label')
                        }
                        if(params.isPublicForApi && auditService.getAuditConfig(subChild?.instanceOf, 'isPublicForApi'))
                        {
                            noChange << message(code: 'subscription.isPublicForApi.label')
                        }

                        if(params.hasPerpetualAccess && !auditService.getAuditConfig(subChild?.instanceOf, 'hasPerpetualAccess'))
                        {
                            subChild?.hasPerpetualAccess = RefdataValue.get(params.hasPerpetualAccess) == RDStore.YN_YES
                            change << message(code: 'subscription.hasPerpetualAccess.label')
                        }
                        if(params.hasPerpetuaLAccess && auditService.getAuditConfig(subChild?.instanceOf, 'hasPerpetualAccess'))
                        {
                            noChange << message(code: 'subscription.hasPerpetualAccess.label')
                        }

                        if (subChild?.isDirty()) {
                            subChild?.save(flush: true)
                        }
                    }

                    if(change){
                        flash.message = message(code: 'subscription.subscriptionPropertiesMembers.changes', args: [change?.unique { a, b -> a <=> b }.join(', ').toString()])
                    }

                    if(noChange){
                        flash.error = message(code: 'subscription.subscriptionPropertiesMembers.noChanges', args: [noChange?.unique { a, b -> a <=> b }.join(', ').toString()])
                    }
        }else {
            flash.error = message(code: 'subscription.subscriptionPropertiesMembers.noSelectedMember')
        }

        redirect(action: 'subscriptionPropertiesMembers', id: params.id)
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_CONSORTIUM", "INST_EDITOR")
    })
    def processDeletePropertiesMembers() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        result.filterPropDef = params.filterPropDef ? genericOIDService.resolveOID(params.filterPropDef.replace(" ", "")) : null

        result.parentSub = result.subscriptionInstance.instanceOf && result.subscription._getCalculatedType() != CalculatedType.TYPE_PARTICIPATION_AS_COLLECTIVE ? result.subscriptionInstance.instanceOf : result.subscriptionInstance

        List<Subscription> validSubChilds = Subscription.findAllByInstanceOf(result.parentSub)

        if (params.filterPropDef) {

            String filterPropDef = params.filterPropDef
            PropertyDefinition propDef = (PropertyDefinition) genericOIDService.resolveOID(filterPropDef.replace(" ", ""))

            int deletedProperties = 0

            if (propDef) {
                validSubChilds.each { Subscription subChild ->

                    if (propDef.tenant != null) {
                        //private Property

                        List<SubscriptionProperty> existingProps = subChild.propertySet.findAll {
                            it.owner.id == subChild.id && it.type.id == propDef.id && it.tenant.id == result.institution.id
                        }
                        existingProps.removeAll { it.type.name != propDef.name } // dubious fix


                        if (existingProps.size() == 1 ){
                            SubscriptionProperty privateProp = SubscriptionProperty.get(existingProps[0].id)

                            try {
                                privateProp.delete(flush:true)
                                deletedProperties++
                            } catch (Exception e)
                            {
                                log.error( e.toString() )
                            }

                        }

                    } else {
                        //custom Property

                        def existingProp = subChild.propertySet.find {
                            it.type.id == propDef.id && it.owner.id == subChild.id && it.tenant.id == result.institution.id
                        }


                        if (existingProp && !(existingProp.hasProperty('instanceOf') && existingProp.instanceOf && AuditConfig.getConfig(existingProp.instanceOf))){
                            SubscriptionProperty customProp = SubscriptionProperty.get(existingProp.id)

                            try {
                                customProp.delete(flush:true)
                                deletedProperties++
                            } catch (Exception e){
                                log.error( e.toString() )
                            }

                        }
                    }

                }
                flash.message = message(code: 'subscription.propertiesMembers.deletedProperties', args: [deletedProperties])
            }

        }

        def filterPropDef = params.filterPropDef
        def id = params.id
        redirect(action: 'propertiesMembers', id: id, params: [filterPropDef: filterPropDef])
    }

    private ArrayList<Long> getOrgIdsForFilter() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        GrailsParameterMap tmpParams = (GrailsParameterMap) params.clone()
        tmpParams.remove("max")
        tmpParams.remove("offset")
        if (accessService.checkPerm("ORG_CONSORTIUM"))
            tmpParams.comboType = RDStore.COMBO_TYPE_CONSORTIUM.value
        else if (accessService.checkPerm("ORG_INST_COLLECTIVE"))
            tmpParams.comboType = RDStore.COMBO_TYPE_DEPARTMENT.value
        def fsq = filterService.getOrgComboQuery(tmpParams, result.institution)

        if (tmpParams.filterPropDef) {
            fsq = propertyService.evalFilterQuery(tmpParams, fsq.query, 'o', fsq.queryParams)
        }
        fsq.query = fsq.query.replaceFirst("select o from ", "select o.id from ")
        Org.executeQuery(fsq.query, fsq.queryParams, tmpParams)
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def addMembers() {
        log.debug("addMembers ..")

        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW_AND_EDIT)
        if (!result) {
            response.sendError(401); return
        }

        result.superOrgType = []
        result.memberType = []
        params.comboType = RDStore.COMBO_TYPE_CONSORTIUM.value
        result.superOrgType << message(code:'consortium.superOrgType')
        result.memberType << message(code:'consortium.subscriber')
        def fsq = filterService.getOrgComboQuery(params, result.institution)
        result.members = Org.executeQuery(fsq.query, fsq.queryParams, params)
        result.members_disabled = []
        result.members.each { it ->
            if (Subscription.executeQuery("select s from Subscription as s join s.orgRelations as sor where s.instanceOf = :io and sor.org.id = :orgId", [io: result.subscriptionInstance, orgId: it.id])) {
                result.members_disabled << it.id
            }
        }
        result.validPackages = result.subscriptionInstance.packages?.sort { it.pkg.name }
        result.memberLicenses = License.executeQuery("select li.sourceLicense from Links li where li.destinationSubscription = :subscription and li.linkType = :linkType",[subscription:result.subscriptionInstance, linkType:RDStore.LINKTYPE_LICENSE])

        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def processAddMembers() {
        log.debug( params.toMapString() )
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW_AND_EDIT)
        if (!result) {
            response.sendError(401); return
        }
        if(formService.validateToken(params)) {

            List orgType = [RDStore.OT_INSTITUTION.id.toString()]
            if (accessService.checkPerm("ORG_CONSORTIUM")) {
                orgType = [RDStore.OT_CONSORTIUM.id.toString()]
            }

            RefdataValue subStatus = RefdataValue.get(params.subStatus) ?: RDStore.SUBSCRIPTION_CURRENT

            RefdataValue role_sub       = RDStore.OR_SUBSCRIBER_CONS
            RefdataValue role_sub_cons  = RDStore.OR_SUBSCRIPTION_CONSORTIA
            RefdataValue role_coll      = RDStore.OR_SUBSCRIBER_COLLECTIVE
            RefdataValue role_sub_coll  = RDStore.OR_SUBSCRIPTION_COLLECTIVE
            RefdataValue role_sub_hidden = RDStore.OR_SUBSCRIBER_CONS_HIDDEN
            RefdataValue role_lic       = RDStore.OR_LICENSEE_CONS
            RefdataValue role_lic_cons  = RDStore.OR_LICENSING_CONSORTIUM
            RefdataValue role_provider  = RDStore.OR_PROVIDER
            RefdataValue role_agency    = RDStore.OR_AGENCY

            if (accessService.checkMinUserOrgRole(result.user, result.institution, 'INST_EDITOR')) {

                if (accessService.checkPerm("ORG_INST_COLLECTIVE,ORG_CONSORTIUM")) {
                    List<Org> members = []
                    License licenseCopy

                    params.list('selectedOrgs').each { it ->
                        members << Org.findById(Long.valueOf(it))
                    }

                    List<Subscription> synShareTargetList = []
                    List<License> licensesToProcess = []
                    Set<Package> packagesToProcess = []

                    //copy package data
                    if(params.linkAllPackages) {
                        result.subscriptionInstance.packages.each { sp ->
                            packagesToProcess << sp.pkg
                        }
                    }
                    else if(params.packageSelection) {
                        List packageIds = params.list("packageSelection")
                        packageIds.each { spId ->
                            packagesToProcess << SubscriptionPackage.get(spId).pkg
                        }
                    }
                    if(params.generateSlavedLics == "all") {
                        String query = "select li.sourceLicense from Links li where li.destinationSubscription = :subscription and li.linkType = :linkType"
                        licensesToProcess.addAll(License.executeQuery(query, [subscription:result.subscriptionInstance, linkType:RDStore.LINKTYPE_LICENSE]))
                    }
                    else if(params.generateSlavedLics == "partial") {
                        List<String> licenseKeys = params.list("generateSlavedLicsReference")
                        licenseKeys.each { String licenseKey ->
                            licensesToProcess << genericOIDService.resolveOID(licenseKey)
                        }
                    }

                    Set<AuditConfig> inheritedAttributes = AuditConfig.findAllByReferenceClassAndReferenceIdAndReferenceFieldNotInList(Subscription.class.name,result.subscriptionInstance.id,PendingChangeConfiguration.SETTING_KEYS)

                    members.each { Org cm ->


                        //ERMS-1155
                        //if (true) {
                        log.debug("Generating seperate slaved instances for members")

                        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
                        Date startDate = params.valid_from ? sdf.parse(params.valid_from) : null
                        Date endDate = params.valid_to ? sdf.parse(params.valid_to) : null

                        Subscription memberSub = new Subscription(
                                type: result.subscriptionInstance.type ?: null,
                                kind: result.subscriptionInstance.kind ?: null,
                                status: subStatus,
                                name: result.subscriptionInstance.name,
                                //name: result.subscriptionInstance.name + " (" + (cm.get(0).shortname ?: cm.get(0).name) + ")",
                                startDate: startDate,
                                endDate: endDate,
                                administrative: result.subscriptionInstance._getCalculatedType() == CalculatedType.TYPE_ADMINISTRATIVE,
                                manualRenewalDate: result.subscriptionInstance.manualRenewalDate,
                                /* manualCancellationDate: result.subscriptionInstance.manualCancellationDate, */
                                identifier: UUID.randomUUID().toString(),
                                instanceOf: result.subscriptionInstance,
                                isSlaved: true,
                                resource: result.subscriptionInstance.resource ?: null,
                                form: result.subscriptionInstance.form ?: null,
                                isMultiYear: params.checkSubRunTimeMultiYear ?: false
                        )

                        inheritedAttributes.each { attr ->
                            memberSub[attr.referenceField] = result.subscriptionInstance[attr.referenceField]
                        }

                        if (!memberSub.save(flush:true)) {
                            memberSub.errors.each { e ->
                                log.debug("Problem creating new sub: ${e}")
                            }
                            flash.error = memberSub.errors
                        }


                        if (memberSub) {
                            if(accessService.checkPerm("ORG_CONSORTIUM")) {
                                if(result.subscriptionInstance._getCalculatedType() == CalculatedType.TYPE_ADMINISTRATIVE) {
                                    new OrgRole(org: cm, sub: memberSub, roleType: role_sub_hidden).save(flush:true)
                                }
                                else {
                                    new OrgRole(org: cm, sub: memberSub, roleType: role_sub).save(flush:true)
                                }
                                new OrgRole(org: result.institution, sub: memberSub, roleType: role_sub_cons).save(flush:true)
                            }
                            else {
                                new OrgRole(org: cm, sub: memberSub, roleType: role_coll).save(flush:true)
                                new OrgRole(org: result.institution, sub: memberSub, roleType: role_sub_coll).save(flush:true)
                            }

                            synShareTargetList.add(memberSub)

                            SubscriptionProperty.findAllByOwner(result.subscriptionInstance).each { SubscriptionProperty sp ->
                                AuditConfig ac = AuditConfig.getConfig(sp)

                                if (ac) {
                                    // multi occurrence props; add one additional with backref
                                    if (sp.type.multipleOccurrence) {
                                        SubscriptionProperty additionalProp = PropertyDefinition.createGenericProperty(PropertyDefinition.CUSTOM_PROPERTY, memberSub, sp.type, sp.tenant)
                                        additionalProp = sp.copyInto(additionalProp)
                                        additionalProp.instanceOf = sp
                                        additionalProp.save(flush: true)
                                    }
                                    else {
                                        // no match found, creating new prop with backref
                                        SubscriptionProperty newProp = PropertyDefinition.createGenericProperty(PropertyDefinition.CUSTOM_PROPERTY, memberSub, sp.type, sp.tenant)
                                        newProp = sp.copyInto(newProp)
                                        newProp.instanceOf = sp
                                        newProp.save(flush: true)
                                    }
                                }
                            }

                            memberSub.refresh()

                            packagesToProcess.each { Package pkg ->
                                if(params.linkWithEntitlements)
                                    pkg.addToSubscriptionCurrentStock(memberSub, result.subscriptionInstance)
                                else
                                    pkg.addToSubscription(memberSub, false)
                            }

                            licensesToProcess.each { License lic ->
                                subscriptionService.setOrgLicRole(memberSub,lic,false)
                            }

                        }
                        //}
                    }

                    result.subscriptionInstance.syncAllShares(synShareTargetList)

                    redirect controller: 'subscription', action: 'members', params: [id: result.subscriptionInstance?.id]
                } else {
                    redirect controller: 'subscription', action: 'show', params: [id: result.subscriptionInstance?.id]
                }
            } else {
                redirect controller: 'subscription', action: 'show', params: [id: result.subscriptionInstance?.id]
            }
        }
        else {
            redirect controller: 'subscription', action: 'members', params: [id: result.subscriptionInstance?.id]
        }
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def pendingChanges() {
        log.debug("subscription id:${params.id}");

        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        def validSubChilds = Subscription.findAllByInstanceOf(result.subscriptionInstance)

        validSubChilds = validSubChilds.sort { a, b ->
            def sa = a.getSubscriber()
            def sb = b.getSubscriber()
            (sa.sortname ?: sa.name).compareTo((sb.sortname ?: sb.name))
        }

        result.pendingChanges = [:]

        validSubChilds.each { member ->

            if (executorWrapperService.hasRunningProcess(member)) {
                log.debug("PendingChange processing in progress")
                result.processingpc = true
            } else {
                List<PendingChange> pendingChanges = PendingChange.executeQuery(
                        "select pc from PendingChange as pc where subscription.id = :subId and ( pc.status is null or pc.status = :status ) order by pc.ts desc",
                        [subId: member.id, status: RDStore.PENDING_CHANGE_PENDING]
                )

                result.pendingChanges << ["${member.id}": pendingChanges]
            }
        }

        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def expected() {
        previousAndExpected(params, 'expected');
    }

    private def previousAndExpected(params, screen) {
        log.debug("previousAndExpected ${params}");

        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        if (!result.subscriptionInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'package.label'), params.id])
            redirect action: 'list'
            return
        }

        result.max = params.max ? Integer.parseInt(params.max) : request.user.getDefaultPageSizeAsInteger()
        params.max = result.max
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        def limits = (!params.format || params.format.equals("html")) ? [max: result.max, offset: result.offset] : [offset: 0]

        def qry_params = [result.subscriptionInstance]
        Date date_filter = new Date()

        String base_qry = "from IssueEntitlement as ie where ie.subscription = ? "
        base_qry += "and ie.status.value != 'Deleted' "
        if (date_filter != null) {
            if (screen.equals('previous')) {
                base_qry += " and ( ie.accessEndDate <= ? ) "
            } else {
                base_qry += " and (ie.accessStartDate > ? )"
            }
            qry_params.add(date_filter);
        }

        log.debug("Base qry: ${base_qry}, params: ${qry_params}, result:${result}");
        result.titlesList = IssueEntitlement.executeQuery("select ie " + base_qry, qry_params, limits);
        result.num_ie_rows = IssueEntitlement.executeQuery("select ie.id " + base_qry, qry_params).size()

        result.lastie = result.offset + result.max > result.num_ie_rows ? result.num_ie_rows : result.offset + result.max;

        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def processAddEntitlements() {
        log.debug("addEntitlements....");

        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_EDIT)
        if (!result) {
            response.sendError(401); return
        }

        def ie_accept_status = RDStore.IE_ACCEPT_STATUS_FIXED
        //result.user = User.get(springSecurityService.principal.id)
        //result.subscriptionInstance = Subscription.get(params.siid)
        //result.institution = result.subscriptionInstance?.subscriber
        //userAccessCheck(result.subscriptionInstance, result.user, 'edit')
        def addTitlesCount = 0
        if (result.subscriptionInstance) {
            EhcacheWrapper cache = contextService.getCache("/subscription/addEntitlements/${result.subscriptionInstance.id}", contextService.USER_SCOPE)
            Map issueEntitlementCandidates = cache.get('issueEntitlementCandidates')
            if(!params.singleTitle) {
                Map checked = cache.get('checked')
                if(checked) {
                    checked.each { k,v ->
                        if(v == 'checked') {
                            try {
                                if(issueEntitlementCandidates?.get(k) || Boolean.valueOf(params.uploadPriceInfo))  {
                                    if(subscriptionService.addEntitlement(result.subscriptionInstance, k, issueEntitlementCandidates?.get(k), Boolean.valueOf(params.uploadPriceInfo), ie_accept_status))
                                        log.debug("Added tipp ${k} to sub ${result.subscriptionInstance.id} with issue entitlement overwrites")
                                }
                                else if(subscriptionService.addEntitlement(result.subscriptionInstance,k,null,false, ie_accept_status)) {
                                    log.debug("Added tipp ${k} to sub ${result.subscriptionInstance.id}")
                                }
                                addTitlesCount++

                            }
                            catch (EntitlementCreationException e) {
                                flash.error = e.getMessage()
                            }
                        }
                    }
                    flash.message = message(code: 'subscription.details.addEntitlements.titlesAddToSub', args: [addTitlesCount])
                }
                else {
                    log.error('cache error or no titles selected')
                }
            }
            else if(params.singleTitle) {
                try {
                    if(issueEntitlementCandidates?.get(params.singleTitle) || Boolean.valueOf(params.uploadPriceInfo))  {
                        if(subscriptionService.addEntitlement(result.subscriptionInstance, params.singleTitle, issueEntitlementCandidates?.get(params.singleTitle), Boolean.valueOf(params.uploadPriceInfo), ie_accept_status))
                            log.debug("Added tipp ${params.singleTitle} to sub ${result.subscriptionInstance.id} with issue entitlement overwrites")
                        flash.message = message(code: 'subscription.details.addEntitlements.titleAddToSub', args: [TitleInstancePackagePlatform.findByGokbId(params.singleTitle)?.title.title])
                    }
                    else if(subscriptionService.addEntitlement(result.subscriptionInstance, params.singleTitle, null, false, ie_accept_status))
                        log.debug("Added tipp ${params.singleTitle} to sub ${result.subscriptionInstance.id}")
                        flash.message = message(code: 'subscription.details.addEntitlements.titleAddToSub', args: [TitleInstancePackagePlatform.findByGokbId(params.singleTitle)?.title.title])

                }
                catch(EntitlementCreationException e) {
                    flash.error = e.getMessage()
                }
            }
        } else {
            log.error("Unable to locate subscription instance");
        }

        redirect action: 'addEntitlements', id: result.subscriptionInstance?.id
    }
    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def processAddIssueEntitlementsSurvey() {
        log.debug("processAddIssueEntitlementsSurvey....");

        Map<String, Object> result = [:]
        result.user = User.get(springSecurityService.principal.id)
        result.subscriptionInstance = Subscription.get(params.id)
        result.subscription = Subscription.get(params.id)
        result.institution = result.subscription?.subscriber
        result.surveyConfig = SurveyConfig.get(params.surveyConfigID)
        result.editable = surveyService.isEditableIssueEntitlementsSurvey(result.institution, result.surveyConfig)
        if (!result.editable) {
            response.sendError(401); return
        }

        def ie_accept_status = RDStore.IE_ACCEPT_STATUS_UNDER_CONSIDERATION

        def addTitlesCount = 0
        if (result.subscriptionInstance) {
            if(params.singleTitle) {
                IssueEntitlement ie = IssueEntitlement.get(params.singleTitle)
                def tipp = ie.tipp

                try {

                    if(subscriptionService.addEntitlement(result.subscriptionInstance, tipp.gokbId, ie, (ie.priceItem != null) , ie_accept_status)) {
                          log.debug("Added tipp ${tipp.gokbId} to sub ${result.subscriptionInstance.id}")
                          flash.message = message(code: 'subscription.details.addEntitlements.titleAddToSub', args: [tipp?.title.title])
                    }
                }
                catch(EntitlementCreationException e) {
                    flash.error = e.getMessage()
                }
            }
        } else {
            log.error("Unable to locate subscription instance");
        }

        redirect action: 'renewEntitlementsWithSurvey', params: [targetObjectId: result.subscriptionInstance?.id, surveyConfigID: result.surveyConfig?.id]
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def removeEntitlement() {
        log.debug("removeEntitlement....");
        IssueEntitlement ie = IssueEntitlement.get(params.ieid)
        def deleted_ie = RDStore.TIPP_STATUS_DELETED
        ie.status = deleted_ie;

        redirect action: 'index', id: params.sub
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def removeEntitlementGroup() {
        log.debug("removeEntitlementGroup....");
        IssueEntitlementGroup issueEntitlementGroup = IssueEntitlementGroup.get(params.titleGroup)

        if(issueEntitlementGroup) {
            IssueEntitlementGroupItem.findAllByIeGroup(issueEntitlementGroup).each {
                it.delete(flush: true)
            }

            IssueEntitlementGroup.executeUpdate("delete from IssueEntitlementGroup ieg where ieg.id in (:issueEntitlementGroup)", [issueEntitlementGroup: issueEntitlementGroup.id])
        }

        redirect action: 'manageEntitlementGroup', id: params.sub
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def processRemoveEntitlements() {
        log.debug("processRemoveEntitlements....");

        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_EDIT)
        if (!result) {
            response.sendError(401); return
        }

        if (result.subscriptionInstance && params.singleTitle) {
            if(subscriptionService.deleteEntitlement(result.subscriptionInstance,params.singleTitle))
                log.debug("Deleted tipp ${params.singleTitle} from sub ${result.subscriptionInstance.id}")
        } else {
            log.error("Unable to locate subscription instance");
        }

        redirect action: 'renewEntitlements', model: [targetObjectId: result.subscriptionInstance?.id, packageId: params.packageId]

    }
    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def processRemoveIssueEntitlementsSurvey() {
        log.debug("processRemoveIssueEntitlementsSurvey....");

        Map<String, Object> result = [:]
        result.user = User.get(springSecurityService.principal.id)
        result.subscriptionInstance = Subscription.get(params.id)
        result.subscription = Subscription.get(params.id)
        result.institution = result.subscription?.subscriber
        result.surveyConfig = SurveyConfig.get(params.surveyConfigID)
        result.editable = surveyService.isEditableIssueEntitlementsSurvey(result.institution, result.surveyConfig)
        if (!result.editable) {
            response.sendError(401); return
        }

        if (result.subscriptionInstance && params.singleTitle) {
            if(subscriptionService.deleteEntitlementbyID(result.subscriptionInstance,params.singleTitle))
                log.debug("Deleted ie ${params.singleTitle} from sub ${result.subscriptionInstance.id}")
        } else {
            log.error("Unable to locate subscription instance");
        }

        redirect action: 'renewEntitlementsWithSurvey', params: [targetObjectId: result.subscriptionInstance?.id, surveyConfigID: result.surveyConfig?.id]
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def processRenewEntitlements() {
        log.debug("processRenewEntitlements ...")
        params.id = Long.parseLong(params.id)
        params.packageId = Long.parseLong(params.packageId)
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_EDIT)
        if (!result) {
            response.sendError(401); return
        }

        List tippsToAdd = params."tippsToAdd".split(",")
        List tippsToDelete = params."tippsToDelete".split(",")

        def ie_accept_status = RDStore.IE_ACCEPT_STATUS_UNDER_CONSIDERATION

        if(result.subscriptionInstance) {
            tippsToAdd.each { tipp ->
                try {
                    if(subscriptionService.addEntitlement(result.subscriptionInstance,tipp,null,false, ie_accept_status))
                        log.debug("Added tipp ${tipp} to sub ${result.subscriptionInstance.id}")
                }
                catch (EntitlementCreationException e) {
                    flash.error = e.getMessage()
                }
            }
            tippsToDelete.each { tipp ->
                if(subscriptionService.deleteEntitlement(result.subscriptionInstance,tipp))
                    log.debug("Deleted tipp ${tipp} from sub ${result.subscriptionInstance.id}")
            }
            if(params.process == "finalise") {
                SubscriptionPackage sp = SubscriptionPackage.findBySubscriptionAndPkg(result.subscriptionInstance,Package.get(params.packageId))
                sp.finishDate = new Date()
                if(!sp.save(flush:true)) {
                    flash.error = sp.errors
                }
                else flash.message = message(code:'subscription.details.renewEntitlements.submitSuccess',args:[sp.pkg.name])
            }
        }
        else {
            log.error("Unable to locate subscription instance")
        }
        redirect action: 'index', id: params.id
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def processRenewEntitlementsWithSurvey() {
        log.debug("processRenewEntitlementsWithSurvey ...")
        Map<String, Object> result = [:]
        result.user = User.get(springSecurityService.principal.id)
        result.subscriptionInstance = Subscription.get(params.id)
        result.subscription = Subscription.get(params.id)
        result.institution = result.subscription?.subscriber
        result.surveyConfig = SurveyConfig.get(params.surveyConfigID)
        result.editable = surveyService.isEditableIssueEntitlementsSurvey(result.institution, result.surveyConfig)
        if (!result.editable) {
            response.sendError(401); return
        }

        List iesToAdd = params."iesToAdd".split(",")


        def ie_accept_status = RDStore.IE_ACCEPT_STATUS_UNDER_CONSIDERATION

        Integer countIEsToAdd = 0

        if(result.subscriptionInstance) {
            iesToAdd.each { ieID ->
                IssueEntitlement ie = IssueEntitlement.findById(ieID)
                def tipp = ie.tipp


                if(tipp) {
                    try {
                        if (subscriptionService.addEntitlement(result.subscriptionInstance, tipp.gokbId, ie, (ie.priceItem != null), ie_accept_status)) {
                            log.debug("Added tipp ${tipp.gokbId} to sub ${result.subscriptionInstance.id}")
                            countIEsToAdd++
                        }
                    }
                    catch (EntitlementCreationException e) {
                        log.debug("Error: Added tipp ${tipp} to sub ${result.subscriptionInstance.id}: " + e.getMessage())
                        flash.error = message(code: 'renewEntitlementsWithSurvey.noSelectedTipps')
                    }
                }
            }

            if(countIEsToAdd > 0){
                flash.message = message(code:'renewEntitlementsWithSurvey.tippsToAdd', args: [countIEsToAdd])
            }

        }
        else {
            log.error("Unable to locate subscription instance")
        }
        redirect action: 'renewEntitlementsWithSurvey', id: params.id, params: [targetObjectId: params.id, surveyConfigID: result.surveyConfig?.id]
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def addEmptyPriceItem() {
        if(params.ieid) {
            IssueEntitlement ie = IssueEntitlement.get(params.ieid)
            if(ie && !ie.priceItem) {
                PriceItem pi = new PriceItem(issueEntitlement: ie)
                pi.setGlobalUID()
                if(!pi.save(flush: true)) {
                    log.error(pi.errors.toString())
                    flash.error = message(code:'subscription.details.addEmptyPriceItem.priceItemNotSaved')
                }
            }
            else {
                flash.error = message(code:'subscription.details.addEmptyPriceItem.issueEntitlementNotFound')
            }
        }
        else {
            flash.error = message(code:'subscription.details.addEmptyPriceItem.noIssueEntitlement')
        }
        redirect action: 'index', id: params.id
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def notes() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }
        result.contextOrg = contextService.getOrg()
        if (result.institution) {
            result.subscriber_shortcode = result.institution.shortcode
        }
        result
    }


    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_USER")
    })
    def documents() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }
        result.contextOrg = contextService.getOrg()
        if (result.institution) {
            result.subscriber_shortcode = result.institution.shortcode
        }
        result
    }

    @DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def editDocument() {
        Map result = [user:springSecurityService.getCurrentUser(),institution:contextService.org]
        result.ownobj = Subscription.get(params.instanceId)
        result.owntp = 'subscription'
        if(params.id) {
            result.docctx = DocContext.get(params.id)
            result.doc = result.docctx.owner
        }

        render template: "/templates/documents/modal", model: result
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = { ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_USER") })
    def tasks() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }
        result.contextOrg = contextService.getOrg()

        if (params.deleteId) {
            Task dTask = Task.get(params.deleteId)
            if (dTask && dTask.creator.id == result.user.id) {
                try {
                    flash.message = message(code: 'default.deleted.message', args: [message(code: 'task.label'), dTask.title])
                    dTask.delete(flush: true)
                    if(params.returnToShow)
                        redirect action: 'show', id: params.id
                }
                catch (Exception e) {
                    flash.message = message(code: 'default.not.deleted.message', args: [message(code: 'task.label'), params.deleteId])
                }
            }
        }

        if (result.institution) {
            result.subscriber_shortcode = result.institution.shortcode
        }

        int offset = params.offset ? Integer.parseInt(params.offset) : 0
        result.taskInstanceList = taskService.getTasksByResponsiblesAndObject(result.user, contextService.getOrg(), result.subscriptionInstance)
        result.taskInstanceCount = result.taskInstanceList.size()
        result.taskInstanceList = taskService.chopOffForPageSize(result.taskInstanceList, result.user, offset)

        result.myTaskInstanceList = taskService.getTasksByCreatorAndObject(result.user,  result.subscriptionInstance)
        result.myTaskInstanceCount = result.myTaskInstanceList.size()
        result.myTaskInstanceList = taskService.chopOffForPageSize(result.myTaskInstanceList, result.user, offset)

        log.debug(result.taskInstanceList.toString())
        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def deleteDocuments() {
        def ctxlist = []

        log.debug("deleteDocuments ${params}");

        docstoreService.unifiedDeleteDocuments(params)

        redirect controller: 'subscription', action: params.redirectAction, id: params.instanceId
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def permissionInfo() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }
        result.contextOrg = contextService.getOrg()
        result
    }


    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def acceptChange() {
        processAcceptChange(params, Subscription.get(params.id), genericOIDService)
        redirect controller: 'subscription', action: 'index', id: params.id
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def rejectChange() {
        processRejectChange(params, Subscription.get(params.id))
        redirect controller: 'subscription', action: 'index', id: params.id
    }


    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def possibleLicensesForSubscription() {
        List result = []

        Subscription subscription = (Subscription) genericOIDService.resolveOID(params.oid)
        Org subscriber = subscription.getSubscriber()
        Org consortia = subscription.getConsortia()
        Org collective = subscription.getCollective()

        result.add([value: '', text: 'None']);

        if (subscriber || collective || consortia) {

            RefdataValue licensee_role = RDStore.OR_LICENSEE
            RefdataValue licensee_cons_role = RDStore.OR_LICENSING_CONSORTIUM

            Org org
            if(subscription.instanceOf) {
                if(subscription.getConsortia())
                    org = consortia
                else if(subscription.getCollective())
                    org = collective
            }
            else org = subscriber

            Map qry_params = [org: org, licRole: licensee_role, licConsRole: licensee_cons_role]

            String qry = ""

            if (subscription.instanceOf) {
                qry = "select l from License as l where exists ( select ol from OrgRole as ol where ol.lic = l AND ol.org = :org and ( ol.roleType = :licRole or ol.roleType = :licConsRole) ) AND (l.instanceOf is not null) order by LOWER(l.reference)"
            } else {
                qry = "select l from License as l where exists ( select ol from OrgRole as ol where ol.lic = l AND ol.org = :org and ( ol.roleType = :licRole or ol.roleType = :licConsRole) ) order by LOWER(l.reference)"
            }
            if (subscriber == consortia) {
                qry_params = [org: consortia,licConsRole: licensee_cons_role]
                qry = "select l from License as l where exists ( select ol from OrgRole as ol where ol.lic = l AND ol.org = :org and ( ol.roleType = :licConsRole) ) AND (l.instanceOf is null) order by LOWER(l.reference)"
            }
            else if(subscriber == collective) {
                qry_params = [org: collective,licRole: licensee_role]
                qry = "select l from License as l where exists ( select ol from OrgRole as ol where ol.lic = l AND ol.org = :org and ( ol.roleType = :licRole) ) AND (l.instanceOf is null) order by LOWER(l.reference)"
            }

            List<License> license_list = License.executeQuery(qry, qry_params);
            license_list.each { l ->
                result.add([value: "${l.class.name}:${l.id}", text: l.reference ?: "No reference - license ${l.id}"]);
            }
        }
        render result as JSON
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def linkPackage() {
        log.debug("Link package, params: ${params} ")

        //Wenn die Subsc schon ein Anbieter hat. Soll nur Pakete von diesem Anbieter angezeigt werden als erstes
        /*if(params.size() == 3) {
            def subInstance = Subscription.get(params.id)
                    subInstance.orgRelations?.each { or ->
           if (or.roleType.value == "Provider") {
                params.put("cpname", or.org.name)
                }
            }
        }*/

        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW_AND_EDIT)
        if (!result) {
            response.sendError(401); return
        }

        Set<Thread> threadSet = Thread.getAllStackTraces().keySet()
        Thread[] threadArray = threadSet.toArray(new Thread[threadSet.size()])

        threadArray.each {
            if (it.name == 'PackageSync_'+result.subscriptionInstance?.id && !SubscriptionPackage.findBySubscriptionAndPkg(result.subscriptionInstance,Package.findByGokbId(params.addUUID))) {
                flash.message = message(code: 'subscription.details.linkPackage.thread.running')
            }
        }

        params.sort = "name"

        //to be deployed in parallel thread - let's make a test!
        if (params.addUUID) {
            String pkgUUID = params.addUUID
            GlobalRecordSource source = GlobalRecordSource.findByUri("${params.source}/gokb/oai/packages")
            log.debug("linkPackage. Global Record Source URL: " +source.baseUrl)
            globalSourceSyncService.source = source
            String addType = params.addType
            GPathResult packageRecord = globalSourceSyncService.fetchRecord(source.uri,'packages',[verb:'GetRecord',metadataPrefix:'gokb',identifier:params.addUUID])
            if(packageRecord && packageRecord.record?.header?.status?.text() != 'deleted') {
                if(!Package.findByGokbId(pkgUUID)) {
                    executorService.execute({
                        Thread.currentThread().setName("PackageSync_"+result.subscriptionInstance?.id)
                        try {
                            globalSourceSyncService.defineMapFields()
                            globalSourceSyncService.updateNonPackageData(packageRecord.record.metadata.gokb.package)
                            List<Map<String,Object>> tippsToNotify = globalSourceSyncService.createOrUpdatePackage(packageRecord.record.metadata.gokb.package)
                            Package pkgToLink = Package.findByGokbId(pkgUUID)
                            //Set<Subscription> subInstances = Subscription.executeQuery("select s from Subscription as s where s.instanceOf = ? ", [result.subscriptionInstance])
                            println "Add package ${addType} entitlements to subscription ${result.subscriptionInstance}"
                            if (addType == 'With') {
                                pkgToLink.addToSubscription(result.subscriptionInstance, true)

                                /*subInstances.each {
                                    pkgToLink.addToSubscription(it, true)
                                }*/
                            } else if (addType == 'Without') {
                                pkgToLink.addToSubscription(result.subscriptionInstance, false)

                                /*subInstances.each {
                                    pkgToLink.addToSubscription(it, false)
                                }*/
                            }
                            //Thread.currentThread().setName("PackageSync_"+result.subscriptionInstance?.id+"_pendingChanges")
                            //globalSourceSyncService.notifyDependencies([tippsToNotify])
                            //globalSourceSyncService.cleanUpGorm()
                        }
                        catch (Exception e) {
                            log.error("sync job has failed, please consult stacktrace as follows: ")
                            e.printStackTrace()
                        }
                    })
                }
                else {
                    Package pkgToLink = Package.findByGokbId(pkgUUID)
                    //Set<Subscription> subInstances = Subscription.executeQuery("select s from Subscription as s where s.instanceOf = ? ", [result.subscriptionInstance])
                    println "Add package ${addType} entitlements to subscription ${result.subscriptionInstance}"
                    if (addType == 'With') {
                        pkgToLink.addToSubscription(result.subscriptionInstance, true)

                        /*subInstances.each {
                            pkgToLink.addToSubscription(it, true)
                        }*/
                    } else if (addType == 'Without') {
                        pkgToLink.addToSubscription(result.subscriptionInstance, false)

                        /*subInstances.each {
                            pkgToLink.addToSubscription(it, false)
                        }*/
                    }
                }
                switch(params.addType) {
                    case "With": flash.message = message(code:'subscription.details.link.processingWithEntitlements')
                        redirect action: 'index', params: [id: params.id, gokbId: params.addUUID]
                        break
                    case "Without": flash.message = message(code:'subscription.details.link.processingWithoutEntitlements')
                        redirect action: 'addEntitlements', params: [id: params.id, packageLinkPreselect: params.addUUID, preselectedName: packageRecord.record.metadata.gokb.package.name]
                        break
                }
            }
            else {
                flash.error = message(code:'subscription.details.link.packageNotFound')
            }
        }

        if (result.subscriptionInstance.packages) {
            result.pkgs = []
            if (params.gokbApi) {
                result.subscriptionInstance.packages.each { sp ->
                    log.debug("Existing package ${sp.pkg.name} (Adding GOKb ID: ${sp.pkg.gokbId})")
                    result.pkgs.add(sp.pkg.gokbId)
                }
            }
        } else {
            log.debug("Subscription has no linked packages yet")
        }

        // TODO can we remove this block? Was it used for usage in renewal process?
        if (result.institution) {
            result.subscriber_shortcode = result.institution.shortcode
            result.institutional_usage_identifier = OrgSetting.get(result.institution, OrgSetting.KEYS.NATSTAT_SERVER_REQUESTOR_ID)
        }
        log.debug("Going for GOKB API")
        User user = springSecurityService.getCurrentUser()
        params.max = params.max ?: (user?.getDefaultPageSize() ?: 25)

        //if (params.gokbApi) {
            def gokbRecords = []

            ApiSource.findAllByTypAndActive(ApiSource.ApiTyp.GOKBAPI, true).each { api ->
                gokbRecords << GOKbService.getPackagesMap(api, params.q, false).records
            }

            params.sort = params.sort ?: 'name'
            params.order = params.order ?: 'asc'

            result.records = null
            if(gokbRecords) {
                Map filteredMap = [:]
                gokbRecords.each { apiRes ->
                    apiRes.each { rec ->
                        filteredMap[rec.uuid] = rec
                    }
                }
                result.records = filteredMap.values().toList().flatten()
            }

            result.records?.sort { x, y ->
                if (params.order == 'desc') {
                    y."${params.sort}".toString().compareToIgnoreCase x."${params.sort}".toString()
                } else {
                    x."${params.sort}".toString().compareToIgnoreCase y."${params.sort}".toString()
                }
            }
            result.resultsTotal = result.records?.size()

            Integer start = params.offset ? params.int('offset') : 0
            Integer end = params.offset ? params.int('max') + params.int('offset') : params.int('max')
            end = (end > result.records?.size()) ? result.records?.size() : end

            result.hits = result.records?.subList(start, end)

        /*} else {
            params.rectype = "Package"
            result.putAll(ESSearchService.search(params))
        }*/

        result
    }

    def buildRenewalsQuery(params) {
        log.debug("BuildQuery...");

        StringWriter sw = new StringWriter()
        sw.write("rectype:'Package'")

        renewals_reversemap.each { mapping ->

            // log.debug("testing ${mapping.key}");

            if (params[mapping.key] != null) {
                if (params[mapping.key].class == java.util.ArrayList) {
                    params[mapping.key].each { p ->
                        sw.write(" AND ")
                        sw.write(mapping.value)
                        sw.write(":")
                        sw.write("\"${p}\"")
                    }
                } else {
                    // Only add the param if it's length is > 0 or we end up with really ugly URLs
                    // II : Changed to only do this if the value is NOT an *
                    if (params[mapping.key].length() > 0 && !(params[mapping.key].equalsIgnoreCase('*'))) {
                        sw.write(" AND ")
                        sw.write(mapping.value)
                        sw.write(":")
                        sw.write("\"${params[mapping.key]}\"")
                    }
                }
            }
        }


        def result = sw.toString();
        result;
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def setupPendingChangeConfiguration() {
        Map<String, Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW_AND_EDIT)
        if(!result) {
            response.sendError(403)
            return
        }
        log.debug("Received params: ${params}")
        SubscriptionPackage subscriptionPackage = SubscriptionPackage.get(params.subscriptionPackage)
        PendingChangeConfiguration.SETTING_KEYS.each { String settingKey ->
            Map<String,Object> configMap = [subscriptionPackage:subscriptionPackage,settingKey:settingKey,withNotification:false]
            boolean auditable = false
            //Set because we have up to three keys in params with the settingKey
            Set<String> keySettings = params.keySet().findAll { k -> k.contains(settingKey) }
            keySettings.each { key ->
                List<String> settingData = key.split('!§!')
                switch(settingData[1]) {
                    case 'setting': configMap.settingValue = RefdataValue.get(params[key])
                        break
                    case 'notification': configMap.withNotification = params[key] != null
                        break
                    case 'auditable': auditable = params[key] != null
                        break
                }
            }
            try {
                PendingChangeConfiguration pcc = PendingChangeConfiguration.construct(configMap)
                boolean hasConfig = AuditConfig.getConfig(subscriptionPackage.subscription,settingKey) != null
                if(auditable && !hasConfig) {
                    AuditConfig.addConfig(subscriptionPackage.subscription,settingKey)
                }
                else if(!auditable && hasConfig) {
                    AuditConfig.removeConfig(subscriptionPackage.subscription,settingKey)
                }
            }
            catch (CreationException e) {
                flash.error = e.message
            }
        }
        redirect(action:'show', params:[id:params.id])
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def history() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }
        result.contextOrg = contextService.getOrg()
        result.max = params.max ?: result.user.getDefaultPageSize()
        result.offset = params.offset ?: 0;

        Map<String, Object> qry_params = [cname: result.subscription.class.name, poid: "${result.subscription.id}"]

        result.historyLines = AuditLogEvent.executeQuery(
                "select e from AuditLogEvent as e where className = :cname and persistedObjectId = :poid order by id desc",
                qry_params, [max: result.max, offset: result.offset]
        )
        result.historyLinesTotal = AuditLogEvent.executeQuery(
                "select e.id from AuditLogEvent as e where className = :cname and persistedObjectId = :poid", qry_params
        ).size()

        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def changes() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        result.contextOrg = contextService.getOrg()
        result.max = params.max ?: result.user.getDefaultPageSize()
        result.offset = params.offset ?: 0;

        String baseQuery = "select pc from PendingChange as pc where pc.subscription = :sub and pc.status.value in (:stats)"
        def baseParams = [sub: result.subscription, stats: ['Accepted', 'Rejected']]

        result.todoHistoryLines = PendingChange.executeQuery(
                baseQuery + " order by pc.ts desc",
                baseParams,
                [max: result.max, offset: result.offset]
        )

        result.todoHistoryLinesTotal = PendingChange.executeQuery(
                baseQuery,
                baseParams
        )[0] ?: 0

        result
    }

    // TODO Cost per use tab, still needed?
    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def costPerUse() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }
        // Can we remove this block?
        if (result.institution) {
            result.subscriber_shortcode = result.institution.shortcode
            result.institutional_usage_identifier = OrgSetting.get(result.institution, OrgSetting.KEYS.NATSTAT_SERVER_REQUESTOR_ID)
        }

        // Get a unique list of invoices
        // select inv, sum(cost) from costItem as ci where ci.sub = x
        log.debug("Get all invoices for sub ${result.subscription}");
        result.costItems = []
        CostItem.executeQuery(INVOICES_FOR_SUB_HQL, [sub: result.subscription]).each {

            log.debug(it);

            def cost_row = [invoice: it[0], total: it[2]]

            cost_row.total_cost_for_sub = it[2];

            if (it && (it[3]?.startDate) && (it[3]?.endDate)) {

                log.debug("Total costs for sub : ${cost_row.total_cost_for_sub} period will be ${it[3]?.startDate} to ${it[3]?.endDate}");

                def usage_str = Fact.executeQuery(TOTAL_USAGE_FOR_SUB_IN_PERIOD, [
                        start   : it[3].startDate,
                        end     : it[3].endDate,
                        sub     : result.subscription,
                        factType: 'STATS:JR1'])[0]

                if (usage_str && usage_str.trim().length() > 0) {
                    cost_row.total_usage_for_sub = Double.parseDouble(usage_str);
                    if (cost_row.total_usage_for_sub > 0) {
                        cost_row.overall_cost_per_use = cost_row.total_cost_for_sub / cost_row.total_usage_for_sub;
                    } else {
                        cost_row.overall_cost_per_use = 0;
                    }
                } else {
                    cost_row.total_usage_for_sub = Double.parseDouble('0');
                    cost_row.overall_cost_per_use = cost_row.total_usage_for_sub
                }

                // Work out what cost items appear under this subscription in the period given
                cost_row.usage = Fact.executeQuery(USAGE_FOR_SUB_IN_PERIOD, [start: it[3].startDate, end: it[3].endDate, sub: result.subscription, jr1a: 'STATS:JR1'])
                cost_row.billingCurrency = it[3].billingCurrency.value.take(3)
                result.costItems.add(cost_row);
            } else {
                log.error("Invoice ${it} had no start or end date");
            }
        }

        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def show() {

        ProfilerUtils pu = new ProfilerUtils()
        pu.setBenchmark('1')

        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        pu.setBenchmark('this-n-that')

        //if (!result.institution) {
        //    result.institution = result.subscriptionInstance.subscriber ?: result.subscriptionInstance.consortia
        //}
        if (result.institution) {
            result.subscriber_shortcode = result.institution.shortcode
            result.institutional_usage_identifier = OrgSetting.get(result.institution, OrgSetting.KEYS.NATSTAT_SERVER_REQUESTOR_ID)
        }

        pu.setBenchmark('links')

        result.links = linksGenerationService.getSourcesAndDestinations(result.subscription,result.user)

        pu.setBenchmark('pending changes')

        // ---- pendingChanges : start

        if (executorWrapperService.hasRunningProcess(result.subscriptionInstance)) {
            log.debug("PendingChange processing in progress")
            result.processingpc = true
        } else {

            //pc.msgParams null check is the legacy check; new pending changes should NOT be displayed here but on dashboard and only there!
            List<PendingChange> pendingChanges = PendingChange.executeQuery(
                    "select pc from PendingChange as pc where subscription = :sub and ( pc.status is null or pc.status = :status ) and pc.msgParams is not null order by pc.ts desc",
                    [sub: result.subscription, status: RDStore.PENDING_CHANGE_PENDING]
            )

            log.debug("pc result is ${result.pendingChanges}")

            if (result.subscription.isSlaved && ! pendingChanges.isEmpty()) {
                log.debug("Slaved subscription, auto-accept pending changes")
                List changesDesc = []
                pendingChanges.each { change ->
                    if (!pendingChangeService.performAccept(change)) {
                        log.debug("Auto-accepting pending change has failed.")
                    } else {
                        changesDesc.add(change.desc)
                    }
                }
                //ERMS-1844 Hotfix: Änderungsmitteilungen ausblenden
                //flash.message = changesDesc
            } else {
                result.pendingChanges = pendingChanges
            }
        }

        // ---- pendingChanges : end

        pu.setBenchmark('tasks')

        // TODO: experimental asynchronous task
        //def task_tasks = task {

            // tasks

            Org contextOrg = contextService.getOrg()
            result.tasks = taskService.getTasksByResponsiblesAndObject(result.user, contextOrg, result.subscriptionInstance)
            def preCon = taskService.getPreconditionsWithoutTargets(contextOrg)
            result << preCon

            // restrict visible for templates/links/orgLinksAsList
            result.visibleOrgRelations = []
            result.subscriptionInstance.orgRelations?.each { or ->
                if (!(or.org.id == contextOrg.id) && !(or.roleType.id in [RDStore.OR_SUBSCRIBER.id, RDStore.OR_SUBSCRIBER_CONS.id, RDStore.OR_SUBSCRIBER_COLLECTIVE.id])) {
                    result.visibleOrgRelations << or
                }
            }
            result.visibleOrgRelations.sort { it.org.sortname }
        //}

        pu.setBenchmark('properties')

        // TODO: experimental asynchronous task
        //def task_properties = task {

            // -- private properties

            result.authorizedOrgs = result.user?.authorizedOrgs
            result.contextOrg = contextService.getOrg() //result.institution maps to subscriber

            // create mandatory OrgPrivateProperties if not existing

            List<PropertyDefinition> mandatories = PropertyDefinition.getAllByDescrAndMandatoryAndTenant(PropertyDefinition.SUB_PROP, true, result.contextOrg)

            mandatories.each { PropertyDefinition pd ->
                if (!SubscriptionProperty.findAllByOwnerAndTypeAndTenantAndIsPublic(result.subscriptionInstance, pd, result.institution, false)) {
                    def newProp = PropertyDefinition.createGenericProperty(PropertyDefinition.PRIVATE_PROPERTY, result.subscriptionInstance, pd, result.contextOrg)

                    if (newProp.hasErrors()) {
                        log.error(newProp.errors.toString())
                    } else {
                        log.debug("New subscription private property created via mandatory: " + newProp.type.name)
                    }
                }
            }

            // -- private properties

            result.modalPrsLinkRole = RefdataValue.getByValueAndCategory('Specific subscription editor', RDConstants.PERSON_RESPONSIBILITY)
            result.modalVisiblePersons = addressbookService.getPrivatePersonsByTenant(contextService.getOrg())

            result.visiblePrsLinks = []

            result.subscriptionInstance.prsLinks.each { pl ->
                if (!result.visiblePrsLinks.contains(pl.prs)) {
                    if (pl.prs.isPublic) {
                        result.visiblePrsLinks << pl
                    } else {
                        // nasty lazy loading fix
                        result.user.authorizedOrgs.each { ao ->
                            if (ao.getId() == pl.prs.tenant.getId()) {
                                result.visiblePrsLinks << pl
                            }
                        }
                    }
                }
            }
        //}

        pu.setBenchmark('usage')

        // TODO: experimental asynchronous task
        //def task_usage = task {

            // usage
            def suppliers = Platform.executeQuery('select distinct(plat.id) from IssueEntitlement ie join ie.tipp tipp join tipp.platform plat where ie.subscription = :sub',[sub:result.subscriptionInstance])

            if (suppliers) {
                if (suppliers.size() > 1) {
                    log.debug('Found different content platforms for this subscription, cannot show usage')
                } else {
                    def supplier_id = suppliers[0]
                    def platform = PlatformProperty.findByOwnerAndType(Platform.get(supplier_id), PropertyDefinition.getByNameAndDescr('NatStat Supplier ID', PropertyDefinition.PLA_PROP))
                    result.natStatSupplierId = platform?.stringValue ?: null
                    result.institutional_usage_identifier = OrgSetting.get(result.institution, OrgSetting.KEYS.NATSTAT_SERVER_REQUESTOR_ID)
                    if (result.institutional_usage_identifier) {

                        def fsresult = factService.generateUsageData(result.institution.id, supplier_id, result.subscriptionInstance)
                        def fsLicenseResult = factService.generateUsageDataForSubscriptionPeriod(result.institution.id, supplier_id, result.subscriptionInstance)
                        def holdingTypes = result.subscriptionInstance.getHoldingTypes() ?: null
                        if (!holdingTypes) {
                            log.debug('No types found, maybe there are no issue entitlements linked to subscription')
                        } else if (holdingTypes.size() > 1) {
                            log.info('Different content type for this license, cannot calculate Cost Per Use.')
                        } else if (!fsLicenseResult.isEmpty() && result.subscriptionInstance.startDate) {
                            def existingReportMetrics = fsLicenseResult.y_axis_labels*.split(':')*.last()
                            def costPerUseMetricValuePair = factService.getTotalCostPerUse(result.subscriptionInstance, holdingTypes.first(), existingReportMetrics)
                            if (costPerUseMetricValuePair) {
                                result.costPerUseMetric = costPerUseMetricValuePair[0]
                                result.totalCostPerUse = costPerUseMetricValuePair[1]
                                result.currencyCode = NumberFormat.getCurrencyInstance().getCurrency().currencyCode
                            }
                        }

                        result.statsWibid = result.institution.getIdentifierByType('wibid')?.value
                        if(result.statsWibid && result.natStatSupplierId) {
                            result.usageMode = accessService.checkPerm("ORG_CONSORTIUM") ? 'package' : 'institution'
                            result.usage = fsresult?.usage
                            result.missingMonths = fsresult?.missingMonths
                            result.missingSubscriptionMonths = fsLicenseResult?.missingMonths
                            result.x_axis_labels = fsresult?.x_axis_labels
                            result.y_axis_labels = fsresult?.y_axis_labels
                            result.lusage = fsLicenseResult?.usage
                            result.lastUsagePeriodForReportType = factService.getLastUsagePeriodForReportType(result.natStatSupplierId, result.statsWibid)
                            result.l_x_axis_labels = fsLicenseResult?.x_axis_labels
                            result.l_y_axis_labels = fsLicenseResult?.y_axis_labels
                        }
                    }
                }
            }
        //}

        pu.setBenchmark('costs')

        //cost items
        //params.forExport = true
        LinkedHashMap costItems = financeService.getCostItemsForSubscription(params, financeService.setResultGenerics(params))
        result.costItemSums = [:]
        if (costItems.own) {
            result.costItemSums.ownCosts = costItems.own.sums
        }
        if (costItems.cons) {
            result.costItemSums.consCosts = costItems.cons.sums
        }
        if(costItems.coll) {
            result.costItemSums.collCosts = costItems.coll.sums
        }
        if (costItems.subscr) {
            result.costItemSums.subscrCosts = costItems.subscr.sums
        }

        pu.setBenchmark('provider & agency filter')

        // TODO: experimental asynchronous task
        //def task_providerFilter = task {

            result.availableProviderList = orgTypeService.getOrgsForTypeProvider().minus(
                    OrgRole.executeQuery(
                            "select o from OrgRole oo join oo.org o where oo.sub.id = :sub and oo.roleType.value = 'Provider'",
                            [sub: result.subscriptionInstance.id]
                    ))
            result.existingProviderIdList = []
            // performance problems: orgTypeService.getCurrentProviders(contextService.getOrg()).collect { it -> it.id }

            result.availableAgencyList = orgTypeService.getOrgsForTypeAgency().minus(
                    OrgRole.executeQuery(
                            "select o from OrgRole oo join oo.org o where oo.sub.id = :sub and oo.roleType.value = 'Agency'",
                            [sub: result.subscriptionInstance.id]
                    ))
            result.existingAgencyIdList = []
            // performance problems: orgTypeService.getCurrentAgencies(contextService.getOrg()).collect { it -> it.id }

        //}

        result.publicSubscriptionEditors = Person.getPublicByOrgAndObjectResp(null, result.subscriptionInstance, 'Specific subscription editor')

        if(result.subscription._getCalculatedType() in [CalculatedType.TYPE_ADMINISTRATIVE,CalculatedType.TYPE_CONSORTIAL]) {
            pu.setBenchmark('non-inherited member properties')
            List<Subscription> childSubs = result.subscription.getNonDeletedDerivedSubscriptions()
            if(childSubs) {
                String localizedName
                switch(LocaleContextHolder.getLocale()) {
                    case Locale.GERMANY:
                    case Locale.GERMAN: localizedName = "name_de"
                        break
                    default: localizedName = "name_en"
                        break
                }
                String query = "select sp.type from SubscriptionProperty sp where sp.owner in (:subscriptionSet) and sp.tenant = :context and sp.instanceOf = null order by sp.type.${localizedName} asc"
                Set<PropertyDefinition> memberProperties = PropertyDefinition.executeQuery(query, [subscriptionSet:childSubs, context:result.institution] )

                result.memberProperties = memberProperties
            }
        }

        List bm = pu.stopBenchmark()
        result.benchMark = bm

        // TODO: experimental asynchronous task
        //waitAll(task_tasks, task_properties, task_usage, task_providerFilter)

        result
    }


    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def processRenewSubscription() {

        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_EDIT)
        if (!result.editable) {
            response.sendError(401); return
        }

        ArrayList<Links> previousSubscriptions = Links.findAllByDestinationSubscriptionAndLinkType(result.subscription, RDStore.LINKTYPE_FOLLOWS)
        if (previousSubscriptions.size() > 0) {
            flash.error = message(code: 'subscription.renewSubExist')
        } else {
            def sub_startDate = params.subscription.start_date ? parseDate(params.subscription.start_date, possible_date_formats) : null
            def sub_endDate = params.subscription.end_date ? parseDate(params.subscription.end_date, possible_date_formats) : null
            def sub_status = params.subStatus ?: RDStore.SUBSCRIPTION_NO_STATUS
            def sub_isMultiYear = params.subscription.isMultiYear
            def new_subname = params.subscription.name
            def manualCancellationDate = null

            use(TimeCategory) {
                manualCancellationDate =  result.subscription.manualCancellationDate ? (result.subscription.manualCancellationDate + 1.year) : null
            }

            Subscription newSub = new Subscription(
                    name: new_subname,
                    startDate: sub_startDate,
                    endDate: sub_endDate,
                    manualCancellationDate: manualCancellationDate,
                    identifier: UUID.randomUUID().toString(),
                    isSlaved: result.subscription.isSlaved,
                    type: result.subscription.type,
                    kind: result.subscription.kind,
                    resource: result.subscription.resource,
                    form: result.subscription.form,
                    isPublicForApi: result.subscription.isPublicForApi,
                    hasPerpetualAccess: result.subscription.hasPerpetualAccess,
                    status: sub_status,
                    isMultiYear: sub_isMultiYear ?: false,
                    administrative: result.subscription.administrative,
            )

            if (!newSub.save(flush:true)) {
                log.error("Problem saving subscription ${newSub.errors}");
                return newSub
            } else {
                log.debug("Save ok")
                if(accessService.checkPerm("ORG_CONSORTIUM")) {
                    if (params.list('auditList')) {
                        //copy audit
                        params.list('auditList').each { auditField ->
                            //All ReferenceFields were copied!
                            //'name', 'startDate', 'endDate', 'manualCancellationDate', 'status', 'type', 'form', 'resource'
                            AuditConfig.addConfig(newSub, auditField)
                        }
                    }
                }
                //Copy References
                //OrgRole
                result.subscription.orgRelations?.each { or ->

                    if ((or.org.id == contextService.getOrg().id) || (or.roleType in [RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS,  RDStore.OR_SUBSCRIBER_CONS_HIDDEN])) {
                        OrgRole newOrgRole = new OrgRole()
                        InvokerHelper.setProperties(newOrgRole, or.properties)
                        newOrgRole.sub = newSub
                        newOrgRole.save()
                    }
                }
                //link to previous subscription
                Links prevLink = Links.construct([source: newSub, destination: result.subscription, linkType: RDStore.LINKTYPE_FOLLOWS, owner: contextService.org])
                if (!prevLink.save(flush:true)) {
                    log.error("Problem linking to previous subscription: ${prevLink.errors}")
                }
                result.newSub = newSub

                if (params.targetObjectId == "null") params.remove("targetObjectId")
                result.isRenewSub = true

                redirect controller: 'subscription',
                            action: 'copyElementsIntoSubscription',
                            params: [sourceObjectId: genericOIDService.getOID(result.subscription), targetObjectId: genericOIDService.getOID(newSub), isRenewSub: true]
            }
        }
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def renewSubscription() {
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_EDIT)
        result.institution = contextService.org
        if (!result.editable) {
            response.sendError(401); return
        }

        Subscription subscription = Subscription.get(params.baseSubscription ?: params.id)
        SimpleDateFormat sdf = new SimpleDateFormat('dd.MM.yyyy')
        Date newStartDate
        Date newEndDate
        use(TimeCategory) {
            newStartDate = subscription.endDate ? (subscription.endDate + 1.day) : null
            newEndDate = subscription.endDate ? (subscription.endDate + 1.year) : null
        }

        result.isRenewSub = true
        result.permissionInfo = [sub_startDate: newStartDate ? sdf.format(newStartDate) : null,
                                 sub_endDate  : newEndDate ? sdf.format(newEndDate) : null,
                                 sub_name     : subscription.name,
                                 sub_id       : subscription.id,
                                 sub_status   : RDStore.SUBSCRIPTION_INTENDED.id.toString()
                                ]

        result.subscription = subscription
        result
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def copyElementsIntoSubscription() {
        Map<String, Object> result = [:]
        result.user = contextService.user
        result.contextOrg = contextService.getOrg()
        flash.error = ""
        flash.message = ""
        if (params.sourceObjectId == "null") params.remove("sourceObjectId")
        result.sourceObjectId = params.sourceObjectId
        result.sourceObject = genericOIDService.resolveOID(params.sourceObjectId)

        if (params.targetObjectId == "null") params.remove("targetObjectId")
        if (params.targetObjectId) {
            result.targetObjectId = params.targetObjectId
            result.targetObject = genericOIDService.resolveOID(params.targetObjectId)
        }

        result.showConsortiaFunctions = showConsortiaFunctions(result.contextOrg, result.sourceObject)
        result.consortialView = result.showConsortiaFunctions

        result.editable = result.sourceObject?.isEditableBy(result.user)

        if (!result.editable) {
            response.sendError(401); return
        }

        if (params.isRenewSub) {result.isRenewSub = params.isRenewSub}
        if (params.fromSurvey && accessService.checkPerm("ORG_CONSORTIUM")) {result.fromSurvey = params.fromSurvey}

        result.isConsortialObjects = (result.sourceObject?._getCalculatedType() == CalculatedType.TYPE_CONSORTIAL && result.targetObject?._getCalculatedType() == CalculatedType.TYPE_CONSORTIAL) ?: false

        if (params.copyObject) {result.isConsortialObjects = (result.sourceObject?._getCalculatedType() == CalculatedType.TYPE_CONSORTIAL)}

        result.allObjects_readRights = subscriptionService.getMySubscriptions_readRights([status: RDStore.SUBSCRIPTION_CURRENT.id])
        result.allObjects_writeRights = subscriptionService.getMySubscriptions_writeRights([status: RDStore.SUBSCRIPTION_CURRENT.id])

        List<String> subTypSubscriberVisible = [CalculatedType.TYPE_CONSORTIAL,
                                                CalculatedType.TYPE_ADMINISTRATIVE]
        result.isSubscriberVisible =
                result.sourceObject &&
                result.targetObject &&
                subTypSubscriberVisible.contains(result.sourceObject._getCalculatedType()) &&
                subTypSubscriberVisible.contains(result.targetObject._getCalculatedType())

        if (! result.isSubscriberVisible) {
            //flash.message += message(code: 'subscription.info.subscriberNotAvailable')
        }

        switch (params.workFlowPart) {
            case CopyElementsService.WORKFLOW_DATES_OWNER_RELATIONS:
                result << copyElementsService.copyObjectElements_DatesOwnerRelations(params)
                if (params.isRenewSub){
                    params.workFlowPart = CopyElementsService.WORKFLOW_PACKAGES_ENTITLEMENTS
                    result << copyElementsService.loadDataFor_PackagesEntitlements(params)
                } else {
                    result << copyElementsService.loadDataFor_DatesOwnerRelations(params)
                }
                break
            case CopyElementsService.WORKFLOW_PACKAGES_ENTITLEMENTS:
                result << copyElementsService.copyObjectElements_PackagesEntitlements(params)
                if (params.isRenewSub){
                    params.workFlowPart = CopyElementsService.WORKFLOW_DOCS_ANNOUNCEMENT_TASKS
                    result << copyElementsService.loadDataFor_DocsAnnouncementsTasks(params)
                } else {
                    result << copyElementsService.loadDataFor_PackagesEntitlements(params)
                }
                break
            case CopyElementsService.WORKFLOW_DOCS_ANNOUNCEMENT_TASKS:
                result << copyElementsService.copyObjectElements_DocsAnnouncementsTasks(params)
                if (params.isRenewSub){
                    if (!params.fromSurvey && result.isSubscriberVisible){
                        params.workFlowPart = CopyElementsService.WORKFLOW_SUBSCRIBER
                        result << copyElementsService.loadDataFor_Subscriber(params)
                    } else {
                        params.workFlowPart = CopyElementsService.WORKFLOW_PROPERTIES
                        result << copyElementsService.loadDataFor_Properties(params)
                    }
                } else {
                    result << copyElementsService.loadDataFor_DocsAnnouncementsTasks(params)
                }
                break
            case CopyElementsService.WORKFLOW_SUBSCRIBER:
                result << copyElementsService.copyObjectElements_Subscriber(params)
                if (params.isRenewSub) {
                    params.workFlowPart = CopyElementsService.WORKFLOW_PROPERTIES
                    result << copyElementsService.loadDataFor_Properties(params)
                } else {
                    result << copyElementsService.loadDataFor_Subscriber(params)
                }
                break
            case CopyElementsService.WORKFLOW_PROPERTIES:
                result << copyElementsService.copyObjectElements_Properties(params)
                if (params.isRenewSub && result.targetObject){
                    flash.error = ""
                    flash.message = ""
                    def surveyConfig = SurveyConfig.findBySubscriptionAndSubSurveyUseForTransfer(result.sourceObject, true)

                    if(surveyConfig && result.fromSurvey) {
                        redirect controller: 'survey', action: 'renewalWithSurvey', params: [id: surveyConfig.surveyInfo.id, surveyConfigID: surveyConfig.id]
                    }else {
                        redirect controller: 'subscription', action: 'show', params: [id: result.targetObject.id]
                    }
                } else {
                    result << copyElementsService.loadDataFor_Properties(params)
                }
                break
            case CopyElementsService.WORKFLOW_END:
                result << copyElementsService.copyObjectElements_Properties(params)
                if (result.targetObject){
                    flash.error = ""
                    flash.message = ""

                    def surveyConfig = SurveyConfig.findBySubscriptionAndSubSurveyUseForTransfer(result.sourceObject, true)

                    if(surveyConfig && result.fromSurvey) {
                        redirect controller: 'survey', action: 'renewalWithSurvey', params: [id: surveyConfig.surveyInfo.id, surveyConfigID: surveyConfig.id]
                    }else {
                        redirect controller: 'subscription', action: 'show', params: [id: result.targetObject.id]
                    }
                }
                break
            default:
                result << copyElementsService.loadDataFor_DatesOwnerRelations(params)
                break
        }

        if (params.targetObjectId) {
            result.targetObject = genericOIDService.resolveOID(params.targetObjectId)
        }
        result.workFlowPart = params.workFlowPart ?: CopyElementsService.WORKFLOW_DATES_OWNER_RELATIONS
        result.workFlowPartNext = params.workFlowPartNext ?: CopyElementsService.WORKFLOW_DOCS_ANNOUNCEMENT_TASKS

        if (params.isRenewSub) {result.isRenewSub = params.isRenewSub}
        result
    }

    @DebugAnnotation(perm = "ORG_INST", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST", "INST_EDITOR", "ROLE_ADMIN")
    })
    def copyMyElements() {
        Map<String, Object> result = [:]
        result.user = contextService.user
        result.contextOrg = contextService.getOrg()
        flash.error = ""
        flash.message = ""
        if (params.sourceObjectId == "null") params.remove("sourceObjectId")
        result.sourceObjectId = params.sourceObjectId
        result.sourceObject = genericOIDService.resolveOID(params.sourceObjectId)

        if (params.targetObjectId == "null") params.remove("targetObjectId")
        if (params.targetObjectId) {
            result.targetObjectId = params.targetObjectId
            result.targetObject = genericOIDService.resolveOID(params.targetObjectId)
        }

        //isVisibleBy benötigt hier um zu schauen, ob das Objekt überhaupt angesehen darf
        result.editable = result.sourceObject?.isVisibleBy(result.user)

        if (!result.editable) {
            response.sendError(401); return
        }

        result.allObjects_readRights = subscriptionService.getMySubscriptionsWithMyElements_readRights([status: RDStore.SUBSCRIPTION_CURRENT.id])
        result.allObjects_writeRights = subscriptionService.getMySubscriptionsWithMyElements_writeRights([status: RDStore.SUBSCRIPTION_CURRENT.id])

        switch (params.workFlowPart) {
            case CopyElementsService.WORKFLOW_DOCS_ANNOUNCEMENT_TASKS:
                result << copyElementsService.copyObjectElements_DocsAnnouncementsTasks(params)
                result << copyElementsService.loadDataFor_DocsAnnouncementsTasks(params)

                break;
            case CopyElementsService.WORKFLOW_PROPERTIES:
                result << copyElementsService.copyObjectElements_Properties(params)
                result << copyElementsService.loadDataFor_Properties(params)

                break;
            case CopyElementsService.WORKFLOW_END:
                result << copyElementsService.copyObjectElements_Properties(params)
                if (result.targetObject){
                    flash.error = ""
                    flash.message = ""
                    redirect controller: 'subscription', action: 'show', params: [id: result.targetObject.id]
                }
                break;
            default:
                result << copyElementsService.loadDataFor_DocsAnnouncementsTasks(params)
                break;
        }

        if (params.targetObjectId) {
            result.targetObject = genericOIDService.resolveOID(params.targetObjectId)
        }
        result.workFlowPart = params.workFlowPart ?: CopyElementsService.WORKFLOW_DOCS_ANNOUNCEMENT_TASKS
        result.workFlowPartNext = params.workFlowPartNext ?: CopyElementsService.WORKFLOW_PROPERTIES
        result
    }


    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def copySubscription() {
        Map<String, Object> result = [:]
        result.user = contextService.user
        result.contextOrg = contextService.getOrg()
        flash.error = ""
        flash.message = ""
        if (params.sourceObjectId == "null") params.remove("sourceObjectId")
        result.sourceObjectId = params.sourceObjectId
        result.sourceObject = genericOIDService.resolveOID(params.sourceObjectId)

        if (params.targetObjectId == "null") params.remove("targetObjectId")
        if (params.targetObjectId) {
            result.targetObjectId = params.targetObjectId
            result.targetObject = genericOIDService.resolveOID(params.targetObjectId)
        }

        result.showConsortiaFunctions = showConsortiaFunctions(result.contextOrg, result.sourceObject)
        result.consortialView = result.showConsortiaFunctions

        result.editable = result.sourceObject?.isEditableBy(result.user)

        if (!result.editable) {
            response.sendError(401); return
        }

        result.isConsortialObjects = (result.sourceObject?._getCalculatedType() == CalculatedType.TYPE_CONSORTIAL)
        result.copyObject = true

        if (params.name && !result.targetObject) {
            String sub_name = params.name ?: "Kopie von ${result.sourceObject.name}"

            Object targetObject = new Subscription(
                    name: sub_name,
                    status: RDStore.SUBSCRIPTION_NO_STATUS,
                    identifier: java.util.UUID.randomUUID().toString(),
                    type: result.sourceObject.type,
                    isSlaved: result.sourceObject.isSlaved,
                    administrative: result.sourceObject.administrative
            )
            //Copy InstanceOf
            if (params.targetObject?.copylinktoSubscription) {
                targetObject.instanceOf = result.sourceObject.instanceOf ?: null
            }


            if (!targetObject.save()) {
                log.error("Problem saving subscription ${targetObject.errors}");
            }else {
                result.targetObject = targetObject
                params.targetObjectId = genericOIDService.getOID(targetObject)

                //Copy References
                result.sourceObject.orgRelations.each { OrgRole or ->
                    if ((or.org.id == result.contextOrg.id) || (or.roleType.id in [RDStore.OR_SUBSCRIBER.id, RDStore.OR_SUBSCRIBER_CONS.id, RDStore.OR_SUBSCRIBER_CONS_HIDDEN.id])) {
                        OrgRole newOrgRole = new OrgRole()
                        InvokerHelper.setProperties(newOrgRole, or.properties)
                        newOrgRole.sub = result.targetObject
                        newOrgRole.save()
                    }
                }

            }
        }


        switch (params.workFlowPart) {
            case CopyElementsService.WORKFLOW_DATES_OWNER_RELATIONS:
                result << copyElementsService.copyObjectElements_DatesOwnerRelations(params)
                if(result.targetObject) {
                    params.workFlowPart = CopyElementsService.WORKFLOW_PACKAGES_ENTITLEMENTS
                }
                result << copyElementsService.loadDataFor_PackagesEntitlements(params)
                break
            case CopyElementsService.WORKFLOW_PACKAGES_ENTITLEMENTS:
                result << copyElementsService.copyObjectElements_PackagesEntitlements(params)
                params.workFlowPart = CopyElementsService.WORKFLOW_DOCS_ANNOUNCEMENT_TASKS
                result << copyElementsService.loadDataFor_DocsAnnouncementsTasks(params)
                break
            case CopyElementsService.WORKFLOW_DOCS_ANNOUNCEMENT_TASKS:
                result << copyElementsService.copyObjectElements_DocsAnnouncementsTasks(params)
                params.workFlowPart = CopyElementsService.WORKFLOW_PROPERTIES
                result << copyElementsService.loadDataFor_Properties(params)
                break
            case CopyElementsService.WORKFLOW_END:
                result << copyElementsService.copyObjectElements_Properties(params)
                if (result.targetObject){
                        redirect controller: 'subscription', action: 'show', params: [id: result.targetObject.id]
                }
                break
            default:
                result << copyElementsService.loadDataFor_DatesOwnerRelations(params)
                break
        }

        result.workFlowPart = params.workFlowPart ?: CopyElementsService.WORKFLOW_DATES_OWNER_RELATIONS

        result
    }

    @Deprecated
    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def processcopySubscription() {

        params.id = params.baseSubscription
        Map<String,Object> result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        /*Subscription baseSubscription = Subscription.get(params.baseSubscription)

        if (baseSubscription) {

            def sub_name = params.sub_name ?: "Kopie von ${baseSubscription.name}"

            def newSubscriptionInstance = new Subscription(
                    name: sub_name,
                    status: params.subscription.copyStatus ? baseSubscription.status : RDStore.SUBSCRIPTION_NO_STATUS,
                    type: baseSubscription.type,
                    kind: params.subscription.copyKind ? baseSubscription.kind : null,
                    identifier: java.util.UUID.randomUUID().toString(),
                    isSlaved: baseSubscription.isSlaved,
                    startDate: params.subscription.copyDates ? baseSubscription.startDate : null,
                    endDate: params.subscription.copyDates ? baseSubscription.endDate : null,
                    resource: params.subscription.copyResource ? baseSubscription.resource : null,
                    form: params.subscription.copyForm ? baseSubscription.form : null,
                    isPublicForApi: params.subscription.copyPublicForApi ? baseSubscription.isPublicForApi : false,
                    hasPerpetualAccess: params.subscription.copyPerpetualAccess ? baseSubscription.hasPerpetualAccess : false,
            )
            //Copy InstanceOf
            if (params.subscription.copylinktoSubscription) {
                newSubscriptionInstance.instanceOf = baseSubscription?.instanceOf ?: null
            }


            if (!newSubscriptionInstance.save(flush:true)) {
                log.error("Problem saving subscription ${newSubscriptionInstance.errors}");
                return newSubscriptionInstance
            } else {
                log.debug("Save ok")
                //Copy License
                if (params.subscription.copyLicense) {
                    newSubscriptionInstance.refresh()
                    Set<Links> baseSubscriptionLicenses = Links.findAllByDestinationAndLinkType(genericOIDService.getOID(baseSubscription), RDStore.LINKTYPE_LICENSE)
                    baseSubscriptionLicenses.each { Links link ->
                        subscriptionService.setOrgLicRole(newSubscriptionInstance,genericOIDService.resolveOID(link.source),false)
                    }
                }

                baseSubscription.documents?.each { dctx ->

                    //Copy Docs
                    if (params.subscription.copyDocs) {
                        if (((dctx.owner?.contentType == 1) || (dctx.owner?.contentType == 3)) && (dctx.status?.value != 'Deleted')) {
                            Doc clonedContents = new Doc(
                                    blobContent: dctx.owner.blobContent,
                                    status: dctx.owner.status,
                                    type: dctx.owner.type,
                                    content: dctx.owner.content,
                                    uuid: dctx.owner.uuid,
                                    contentType: dctx.owner.contentType,
                                    title: dctx.owner.title,
                                    filename: dctx.owner.filename,
                                    mimeType: dctx.owner.mimeType,
                                    migrated: dctx.owner.migrated,
                                    owner: dctx.owner.owner
                            ).save(flush:true)

                            String fPath = ConfigUtils.getDocumentStorageLocation() ?: '/tmp/laser'

                            Path source = new File("${fPath}/${dctx.owner.uuid}").toPath()
                            Path target = new File("${fPath}/${clonedContents.uuid}").toPath()
                            Files.copy(source, target)

                            DocContext ndc = new DocContext(
                                    owner: clonedContents,
                                    subscription: newSubscriptionInstance,
                                    domain: dctx.domain,
                                    status: dctx.status,
                                    doctype: dctx.doctype
                            ).save(flush:true)
                        }
                    }
                    //Copy Announcements
                    if (params.subscription.copyAnnouncements) {
                        if ((dctx.owner?.contentType == Doc.CONTENT_TYPE_STRING) && !(dctx.domain) && (dctx.status?.value != 'Deleted')) {
                            Doc clonedContents = new Doc(
                                    blobContent: dctx.owner.blobContent,
                                    status: dctx.owner.status,
                                    type: dctx.owner.type,
                                    content: dctx.owner.content,
                                    uuid: dctx.owner.uuid,
                                    contentType: dctx.owner.contentType,
                                    title: dctx.owner.title,
                                    filename: dctx.owner.filename,
                                    mimeType: dctx.owner.mimeType,
                                    migrated: dctx.owner.migrated
                            ).save(flush:true)

                            DocContext ndc = new DocContext(
                                    owner: clonedContents,
                                    subscription: newSubscriptionInstance,
                                    domain: dctx.domain,
                                    status: dctx.status,
                                    doctype: dctx.doctype
                            ).save(flush:true)
                        }
                    }
                }
                //Copy Tasks
                if (params.subscription.copyTasks) {

                    Task.findAllBySubscription(baseSubscription).each { task ->

                        Task newTask = new Task()
                        InvokerHelper.setProperties(newTask, task.properties)
                        newTask.systemCreateDate = new Date()
                        newTask.subscription = newSubscriptionInstance
                        newTask.save(flush:true)
                    }

                }
                //Copy References
                baseSubscription.orgRelations.each { OrgRole or ->
                    if ((or.org.id == result.institution.id) || (or.roleType.value in ['Subscriber', 'Subscriber_Consortial']) || (params.subscription.copyLinks)) {
                        OrgRole newOrgRole = new OrgRole()
                        InvokerHelper.setProperties(newOrgRole, or.properties)
                        newOrgRole.sub = newSubscriptionInstance
                        newOrgRole.save(flush:true)

                    }

                }
                //
                if ((params.subscription.copySpecificSubscriptionEditors)) {
                    subscriptionService.copySpecificSubscriptionEditorOfProvideryAndAgencies(baseSubscription, newSubscriptionInstance)
                }

                //Copy Package
                if (params.subscription.copyPackages) {
                    baseSubscription.packages?.each { pkg ->
                        def pkgOapls = pkg.oapls
                        Set<PendingChangeConfiguration> pcc = pkg.pendingChangeConfig
                        pkg.properties.oapls = null
                        pkg.properties.pendingChangeConfig = null
                        SubscriptionPackage newSubscriptionPackage = new SubscriptionPackage()
                        InvokerHelper.setProperties(newSubscriptionPackage, pkg.properties)
                        newSubscriptionPackage.subscription = newSubscriptionInstance

                        if(newSubscriptionPackage.save(flush:true)){
                            pkgOapls.each{ oapl ->
                                def oaplProperties = oapl.properties
                                oaplProperties.globalUID = null
                                OrgAccessPointLink newOrgAccessPointLink = new OrgAccessPointLink()
                                InvokerHelper.setProperties(newOrgAccessPointLink, oaplProperties)
                                newOrgAccessPointLink.subPkg = newSubscriptionPackage
                                newOrgAccessPointLink.save(flush:true)
                            }
                            if(params.subscription.copyPackageSettings) {
                                pcc.each { PendingChangeConfiguration config ->
                                    Map<String,Object> configSettings = [subscriptionPackage:newSubscriptionPackage,settingValue:config.settingValue,settingKey:config.settingKey,withNotification:config.withNotification]
                                    PendingChangeConfiguration newPcc = PendingChangeConfiguration.construct(configSettings)
                                    if(newPcc) {
                                        Set<AuditConfig> auditables = AuditConfig.findAllByReferenceClassAndReferenceIdAndReferenceFieldInList(baseSubscription.class.name,baseSubscription.id,PendingChangeConfiguration.SETTING_KEYS)
                                        auditables.each { audit ->
                                            AuditConfig.addConfig(newSubscriptionInstance,audit.referenceField)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                //Copy Identifiers
                if (params.subscription.copyIds) {
                    baseSubscription.ids?.each { id ->
                        Identifier.construct([value: id.value, reference: newSubscriptionInstance, namespace: id.ns])
                    }
                }


                if (params.subscription.copyEntitlements) {

                    baseSubscription.issueEntitlements.each { ie ->

                        if (ie.status != RDStore.TIPP_STATUS_DELETED) {
                            def properties = ie.properties
                            properties.globalUID = null

                            IssueEntitlement newIssueEntitlement = new IssueEntitlement()
                            InvokerHelper.setProperties(newIssueEntitlement, properties)
                            newIssueEntitlement.subscription = newSubscriptionInstance
                            newIssueEntitlement.ieGroups = null
                            newIssueEntitlement.coverages = null

                            if(newIssueEntitlement.save(flush:true)){
                                ie.properties.coverages.each{ coverage ->

                                    def coverageProperties = coverage.properties
                                    IssueEntitlementCoverage newIssueEntitlementCoverage = new IssueEntitlementCoverage()
                                    InvokerHelper.setProperties(newIssueEntitlementCoverage, coverageProperties)
                                    newIssueEntitlementCoverage.issueEntitlement = newIssueEntitlement
                                    newIssueEntitlementCoverage.save(flush:true)
                                }
                            }
                        }
                    }

                }

                if (params.subscription.copyIssueEntitlementGroupItem) {


                    baseSubscription.ieGroups.each { ieGroup ->

                        IssueEntitlementGroup issueEntitlementGroup = new IssueEntitlementGroup(
                                name: ieGroup.name,
                                description: ieGroup.description,
                                sub: newSubscriptionInstance
                        )
                        if(issueEntitlementGroup.save(flush:true)) {

                                    ieGroup.items.each{  ieGroupItem ->
                                        IssueEntitlement ie = IssueEntitlement.findBySubscriptionAndTippAndStatusNotEqual(newSubscriptionInstance, ieGroupItem.ie.tipp, RDStore.TIPP_STATUS_DELETED)
                                        if(ie){
                                            IssueEntitlementGroupItem issueEntitlementGroupItem = new IssueEntitlementGroupItem(
                                                    ie: ie,
                                                    ieGroup: issueEntitlementGroup)

                                            if (!issueEntitlementGroupItem.save(flush: true)) {
                                                log.error("Problem saving IssueEntitlementGroupItem ${issueEntitlementGroupItem.errors}")
                                            }
                                        }

                                    }
                        }
                    }

                }

                if (params.subscription.copyCustomProperties) {
                    //customProperties
                    baseSubscription.propertySet.findAll{ it.tenant.id == result.institution.id && it.type.tenant == null }.each{ SubscriptionProperty prop ->
                        SubscriptionProperty copiedProp = new SubscriptionProperty(type: prop.type, owner: newSubscriptionInstance, isPublic: prop.isPublic, tenant: prop.tenant)
                        copiedProp = prop.copyInto(copiedProp)
                        copiedProp.instanceOf = null
                        copiedProp.save(flush:true)
                        //newSubscriptionInstance.addToCustomProperties(copiedProp) // ERROR Hibernate: Found two representations of same collection
                    }
                }
                if (params.subscription.copyPrivateProperties) {
                    //privatProperties

                    baseSubscription.propertySet.findAll{ it.tenant.id == result.institution.id && it.type.tenant?.id == result.institution.id }.each { SubscriptionProperty prop ->
                        SubscriptionProperty copiedProp = new SubscriptionProperty(type: prop.type, owner: newSubscriptionInstance, isPublic: prop.isPublic, tenant: prop.tenant)
                        copiedProp = prop.copyInto(copiedProp)
                        copiedProp.save(flush:true)
                        //newSubscriptionInstance.addToPrivateProperties(copiedProp)  // ERROR Hibernate: Found two representations of same collection
                    }
                }


                redirect controller: 'subscription', action: 'show', params: [id: newSubscriptionInstance.id]
            }
        }*/

    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def addSubscriptions() {
        boolean withErrors = false
        Org contextOrg = contextService.org
        SimpleDateFormat databaseDateFormatParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        flash.error = ""
        def candidates = JSON.parse(params.candidates)
        candidates.eachWithIndex{ entry, int s ->
            if(params["take${s}"]) {
                //create object itself
                Subscription sub = new Subscription(name: entry.name,
                        status: genericOIDService.resolveOID(entry.status),
                        kind: genericOIDService.resolveOID(entry.kind),
                        form: genericOIDService.resolveOID(entry.form),
                        resource: genericOIDService.resolveOID(entry.resource),
                        type: genericOIDService.resolveOID(entry.type),
                        identifier: UUID.randomUUID())
                sub.startDate = entry.startDate ? databaseDateFormatParser.parse(entry.startDate) : null
                sub.endDate = entry.endDate ? databaseDateFormatParser.parse(entry.endDate) : null
                sub.manualCancellationDate = entry.manualCancellationDate ? databaseDateFormatParser.parse(entry.manualCancellationDate) : null
                /* TODO [ticket=2276]
                if(sub.type == SUBSCRIPTION_TYPE_ADMINISTRATIVE)
                    sub.administrative = true*/
                sub.instanceOf = entry.instanceOf ? genericOIDService.resolveOID(entry.instanceOf) : null
                Org member = entry.member ? genericOIDService.resolveOID(entry.member) : null
                Org provider = entry.provider ? genericOIDService.resolveOID(entry.provider) : null
                Org agency = entry.agency ? genericOIDService.resolveOID(entry.agency) : null
                if(sub.instanceOf && member)
                    sub.isSlaved = RDStore.YN_YES
                if(sub.save(flush:true)) {
                    //create the org role associations
                    RefdataValue parentRoleType, memberRoleType
                    if(accessService.checkPerm("ORG_CONSORTIUM")) {
                        parentRoleType = RDStore.OR_SUBSCRIPTION_CONSORTIA
                        memberRoleType = RDStore.OR_SUBSCRIBER_CONS
                    }
                    else
                        parentRoleType = RDStore.OR_SUBSCRIBER
                    entry.licenses.each { String licenseOID ->
                        License license = (License) genericOIDService.resolveOID(licenseOID)
                        subscriptionService.setOrgLicRole(sub,license,false)
                    }
                    OrgRole parentRole = new OrgRole(roleType: parentRoleType, sub: sub, org: contextOrg)
                    if(!parentRole.save(flush:true)) {
                        withErrors = true
                        flash.error += parentRole.errors
                    }
                    if(memberRoleType && member) {
                        OrgRole memberRole = new OrgRole(roleType: memberRoleType, sub: sub, org: member)
                        if(!memberRole.save(flush:true)) {
                            withErrors = true
                            flash.error += memberRole.errors
                        }
                    }
                    if(provider) {
                        OrgRole providerRole = new OrgRole(roleType: RDStore.OR_PROVIDER, sub: sub, org: provider)
                        if(!providerRole.save(flush:true)) {
                            withErrors = true
                            flash.error += providerRole.errors
                        }
                    }
                    if(agency) {
                        OrgRole agencyRole = new OrgRole(roleType: RDStore.OR_AGENCY, sub: sub, org: agency)
                        if(!agencyRole.save(flush:true)) {
                            withErrors = true
                            flash.error += agencyRole.errors
                        }
                    }
                    //process subscription properties
                    entry.properties.each { k, v ->
                        if(v.propValue?.trim()) {
                            log.debug("${k}:${v.propValue}")
                            PropertyDefinition propDef = (PropertyDefinition) genericOIDService.resolveOID(k)
                            List<String> valueList
                            if(propDef.multipleOccurrence) {
                                valueList = v?.propValue?.split(',')
                            }
                            else valueList = [v.propValue]
                            //in most cases, valueList is a list with one entry
                            valueList.each { value ->
                                try {
                                    createProperty(propDef,sub,contextOrg,value.trim(),v.propNote)
                                }
                                catch (Exception e) {
                                    withErrors = true
                                    flash.error += e.getMessage()
                                }
                            }
                        }
                    }
                    if(entry.notes) {
                        Doc docContent = new Doc(contentType: Doc.CONTENT_TYPE_STRING, content: entry.notes, title: message(code:'myinst.subscriptionImport.notes.title',args:[sdf.format(new Date())]), type: RefdataValue.getByValueAndCategory('Note', RDConstants.DOCUMENT_TYPE), owner: contextOrg, user: contextService.user)
                        if(docContent.save(flush:true)) {
                            DocContext dc = new DocContext(subscription: sub, owner: docContent, doctype: RDStore.DOC_TYPE_NOTE)
                            dc.save(flush:true)
                        }
                    }
                }
                else {
                    withErrors = true
                    flash.error += sub.errors
                }
            }
        }
        if(!withErrors)
            redirect controller: 'myInstitution', action: 'currentSubscriptions'
        else redirect(url: request.getHeader("referer"))
    }

    private void createProperty(PropertyDefinition propDef, Subscription sub, Org contextOrg, String value, String note) {
        //check if private or custom property
        AbstractPropertyWithCalculatedLastUpdated prop
        if(propDef.tenant == contextOrg) {
            //process private property
            prop = PropertyDefinition.createGenericProperty(PropertyDefinition.PRIVATE_PROPERTY, sub, propDef, contextOrg)
        }
        else {
            //process custom property
            prop = PropertyDefinition.createGenericProperty(PropertyDefinition.CUSTOM_PROPERTY, sub, propDef, contextOrg)
        }
        if (propDef.isIntegerType()) {
                int intVal = Integer.parseInt(value)
                prop.setIntValue(intVal)
        }
        else if (propDef.isBigDecimalType()) {
                BigDecimal decVal = new BigDecimal(value)
                prop.setDecValue(decVal)
        }
        else if (propDef.isRefdataValueType()) {
                RefdataValue rdv = (RefdataValue) genericOIDService.resolveOID(value)
                if(rdv)
                    prop.setRefValue(rdv)
        }
        else if (propDef.isDateType()) {
                Date date = parseDate(value,possible_date_formats)
                if(date)
                    prop.setDateValue(date)
        }
        else if (propDef.isURLType()) {
                URL url = new URL(value)
                if(url)
                    prop.setUrlValue(url)
        }
        else {
                prop.setStringValue(value)
        }
        if(note)
            prop.note = note
        prop.save(flush:true)
    }

    private Map<String,Object> setResultGenericsAndCheckAccess(checkOption) {
        Map<String, Object> result = [:]
        result.user = contextService.user
        result.subscriptionInstance = Subscription.get(params.id)
        result.subscription = Subscription.get(params.id)
        result.institution = result.subscription?.subscriber
        result.contextOrg = contextService.getOrg()
        result.licenses = Links.findAllByDestinationSubscriptionAndLinkType(result.subscription,RDStore.LINKTYPE_LICENSE).collect { Links li -> li.sourceLicense }

        LinkedHashMap<String, List> links = linksGenerationService.generateNavigation(result.subscription)
        result.navPrevSubscription = links.prevLink
        result.navNextSubscription = links.nextLink

        result.showConsortiaFunctions = showConsortiaFunctions(result.contextOrg, result.subscription)
        result.consortialView = result.showConsortiaFunctions

        Map args = [:]
        if(result.consortialView) {
            args.superOrgType = [message(code:'consortium.superOrgType')]
            args.memberTypeSingle = [message(code:'consortium.subscriber')]
            args.memberType = [message(code:'consortium.subscriber')]
            args.memberTypeGenitive = [message(code:'consortium.subscriber')]
        }
        result.args = args

        if (checkOption in [AccessService.CHECK_VIEW, AccessService.CHECK_VIEW_AND_EDIT]) {
            if (!result.subscriptionInstance?.isVisibleBy(result.user)) {
                log.debug("--- NOT VISIBLE ---")
                return null
            }
        }
        result.editable = result.subscriptionInstance?.isEditableBy(result.user)

        if(params.orgBasicMemberView){
            result.editable = false
        }

        if (checkOption in [AccessService.CHECK_EDIT, AccessService.CHECK_VIEW_AND_EDIT]) {
            if (!result.editable) {
                log.debug("--- NOT EDITABLE ---")
                return null
            }
        }

        result
    }

    static boolean showConsortiaFunctions(Org contextOrg, Subscription subscription) {
        return ((subscription.getConsortia()?.id == contextOrg.id) && subscription._getCalculatedType() in
                [CalculatedType.TYPE_CONSORTIAL, CalculatedType.TYPE_ADMINISTRATIVE])
    }

    private def exportOrg(orgs, message, addHigherEducationTitles, format) {
        def titles = [g.message(code: 'org.sortname.label'), 'Name', g.message(code: 'org.shortname.label'),g.message(code:'globalUID.label')]

        RefdataValue orgSector = RefdataValue.getByValueAndCategory('Higher Education', RDConstants.ORG_SECTOR)
        RefdataValue orgType = RefdataValue.getByValueAndCategory('Provider', RDConstants.ORG_TYPE)


        if (addHigherEducationTitles) {
            titles.add(g.message(code: 'org.libraryType.label'))
            titles.add(g.message(code: 'org.libraryNetwork.label'))
            titles.add(g.message(code: 'org.funderType.label'))
            titles.add(g.message(code: 'org.region.label'))
            titles.add(g.message(code: 'org.country.label'))
        }

        titles.add(g.message(code: 'subscription.details.startDate'))
        titles.add(g.message(code: 'subscription.details.endDate'))
        titles.add(g.message(code: 'subscription.isPublicForApi.label'))
        titles.add(g.message(code: 'subscription.hasPerpetualAccess.label'))
        titles.add(g.message(code: 'default.status.label'))
        titles.add(RefdataValue.getByValueAndCategory('General contact person', RDConstants.PERSON_FUNCTION).getI10n('value'))
        //titles.add(RefdataValue.getByValueAndCategory('Functional contact', RDConstants.PERSON_CONTACT_TYPE).getI10n('value'))

        def propList = PropertyDefinition.findAllPublicAndPrivateOrgProp(contextService.getOrg())

        propList.sort { a, b -> a.name.compareToIgnoreCase b.name }

        propList.each {
            titles.add(it.name)
        }

        orgs.sort { it.sortname } //see ERMS-1196. If someone finds out how to put order clauses into GORM domain class mappings which include a join, then OK. Otherwise, we must do sorting here.
        try {
            if(format == "xlsx") {

                XSSFWorkbook workbook = new XSSFWorkbook()
                POIXMLProperties xmlProps = workbook.getProperties()
                POIXMLProperties.CoreProperties coreProps = xmlProps.getCoreProperties()
                coreProps.setCreator(g.message(code:'laser'))
                SXSSFWorkbook wb = new SXSSFWorkbook(workbook,50,true)

                Sheet sheet = wb.createSheet(message)

                //the following three statements are required only for HSSF
                sheet.setAutobreaks(true)

                //the header row: centered text in 48pt font
                Row headerRow = sheet.createRow(0)
                headerRow.setHeightInPoints(16.75f)
                titles.eachWithIndex { titlesName, index ->
                    Cell cell = headerRow.createCell(index)
                    cell.setCellValue(titlesName)
                }

                //freeze the first row
                sheet.createFreezePane(0, 1)

                Row row
                Cell cell
                int rownum = 1


                orgs.each { org ->
                    int cellnum = 0
                    row = sheet.createRow(rownum)

                    //Sortname
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(org.sortname ?: '')

                    //Name
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(org.name ?: '')

                    //Shortname
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(org.shortname ?: '')

                    //subscription globalUID
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(org.globalUID)

                    if (addHigherEducationTitles) {

                        //libraryType
                        cell = row.createCell(cellnum++)
                        cell.setCellValue(org.libraryType?.getI10n('value') ?: ' ')

                        //libraryNetwork
                        cell = row.createCell(cellnum++)
                        cell.setCellValue(org.libraryNetwork?.getI10n('value') ?: ' ')

                        //funderType
                        cell = row.createCell(cellnum++)
                        cell.setCellValue(org.funderType?.getI10n('value') ?: ' ')

                        //region
                        cell = row.createCell(cellnum++)
                        cell.setCellValue(org.region?.getI10n('value') ?: ' ')

                        //country
                        cell = row.createCell(cellnum++)
                        cell.setCellValue(org.country?.getI10n('value') ?: ' ')
                    }

                    cell = row.createCell(cellnum++)
                    cell.setCellValue(org.startDate) //null check done already in calling method

                    cell = row.createCell(cellnum++)
                    cell.setCellValue(org.endDate) //null check done already in calling method

                    cell = row.createCell(cellnum++)
                    cell.setCellValue(org.isPublicForApi)

                    cell = row.createCell(cellnum++)
                    cell.setCellValue(org.hasPerpetualAccess)

                    cell = row.createCell(cellnum++)
                    cell.setCellValue(org.status?.getI10n('value') ?: ' ')

                    cell = row.createCell(cellnum++)
                    cell.setCellValue(org.generalContacts ?: '')

                    /*cell = row.createCell(cellnum++)
                    cell.setCellValue('')*/

                    propList.each { pd ->
                        def value = ''
                        org.customProperties.each { prop ->
                            if (prop.type.descr == pd.descr && prop.type == pd) {
                                if (prop.type.isIntegerType()) {
                                    value = prop.intValue.toString()
                                } else if (prop.type.isStringType()) {
                                    value = prop.stringValue ?: ''
                                } else if (prop.type.isBigDecimalType()) {
                                    value = prop.decValue.toString()
                                } else if (prop.type.isDateType()) {
                                    value = prop.dateValue.toString()
                                } else if (prop.type.isRefdataValueType()) {
                                    value = prop.refValue?.getI10n('value') ?: ''
                                }
                            }
                        }

                        org.privateProperties.each { prop ->
                            if (prop.type.descr == pd.descr && prop.type == pd) {
                                if (prop.type.isIntegerType()) {
                                    value = prop.intValue.toString()
                                } else if (prop.type.isStringType()) {
                                    value = prop.stringValue ?: ''
                                } else if (prop.type.isBigDecimalType()) {
                                    value = prop.decValue.toString()
                                } else if (prop.type.isDateType()) {
                                    value = prop.dateValue.toString()
                                } else if (prop.type.isRefdataValueType()) {
                                    value = prop.refValue?.getI10n('value') ?: ''
                                }

                            }
                        }
                        cell = row.createCell(cellnum++)
                        cell.setCellValue(value)
                    }

                    rownum++
                }

                for (int i = 0; i < titles.size(); i++) {
                    sheet.autoSizeColumn(i)
                }
                // Write the output to a file
                String file = message + ".xlsx"
                response.setHeader "Content-disposition", "attachment; filename=\"${file}\""
                response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                wb.write(response.outputStream)
                response.outputStream.flush()
                response.outputStream.close()
                wb.dispose()
            }
            else if(format == 'csv') {
                List orgData = []
                orgs.each{  org ->
                    List row = []
                    //Sortname
                    row.add(org.sortname ? org.sortname.replaceAll(',','') : '')
                    //Name
                    row.add(org.name ? org.name.replaceAll(',','') : '')
                    //Shortname
                    row.add(org.shortname ? org.shortname.replaceAll(',','') : '')
                    //subscription globalUID
                    row.add(org.globalUID)
                    if(addHigherEducationTitles) {
                        //libraryType
                        row.add(org.libraryType?.getI10n('value') ?: ' ')
                        //libraryNetwork
                        row.add(org.libraryNetwork?.getI10n('value') ?: ' ')
                        //funderType
                        row.add(org.funderType?.getI10n('value') ?: ' ')
                        //region
                        row.add(org.region?.getI10n('value') ?: ' ')
                        //country
                        row.add(org.country?.getI10n('value') ?: ' ')
                    }
                    //startDate
                    row.add(org.startDate) //null check already done in calling method
                    //endDate
                    row.add(org.endDate) //null check already done in calling method
                    //isPublicForApi
                    row.add(org.isPublicForApi) //null check already done in calling method
                    //hasPerpetualAccess
                    row.add(org.hasPerpetualAccess) //null check already done in calling method
                    //status
                    row.add(org.status?.getI10n('value') ?: ' ')
                    //generalContacts
                    row.add(org.generalContacts ?: '')
                    propList.each { pd ->
                        def value = ''
                        org.customProperties.each{ prop ->
                            if(prop.type.descr == pd.descr && prop.type == pd) {
                                if(prop.type.isIntegerType()){
                                    value = prop.intValue.toString()
                                }
                                else if (prop.type.isStringType()){
                                    value = prop.stringValue ?: ''
                                }
                                else if (prop.type.isBigDecimalType()){
                                    value = prop.decValue.toString()
                                }
                                else if (prop.type.isDateType()){
                                    value = prop.dateValue.toString()
                                }
                                else if (prop.type.isRefdataValueType()) {
                                    value = prop.refValue?.getI10n('value') ?: ''
                                }
                            }
                        }
                        org.privateProperties.each{ prop ->
                            if(prop.type.descr == pd.descr && prop.type == pd) {
                                if(prop.type.isIntegerType()){
                                    value = prop.intValue.toString()
                                }
                                else if (prop.type.isStringType()){
                                    value = prop.stringValue ?: ''
                                }
                                else if (prop.type.isBigDecimalType()){
                                    value = prop.decValue.toString()
                                }
                                else if (prop.type.isDateType()){
                                    value = prop.dateValue.toString()
                                }
                                else if (prop.type.isRefdataValueType()) {
                                    value = prop.refValue?.getI10n('value') ?: ''
                                }
                            }
                        }
                        row.add(value.replaceAll(',',';'))
                    }
                    orgData.add(row)
                }
                return exportService.generateSeparatorTableString(titles,orgData,',')
            }
        }
        catch (Exception e) {
            log.error("Problem", e);
            response.sendError(500)
        }
    }

    private void updateProperty(def property, def value) {

        String field

        if(property.type.isIntegerType()) {
            field = "intValue"
        }
        else if (property.type.isStringType())  {
            field = "stringValue"
        }
        else if (property.type.isBigDecimalType())  {
            field = "decValue"
        }
        else if (property.type.isDateType())  {
            field = "dateValue"
        }
        else if (property.type.isURLType())  {
            field = "urlValue"
        }
        else if (property.type.isRefdataValueType())  {
            field = "refValue"
        }

        //Wenn eine Vererbung vorhanden ist.
        if(field && property.hasProperty('instanceOf') && property.instanceOf && AuditConfig.getConfig(property.instanceOf)){
            if(property.instanceOf."${field}" == '' || property.instanceOf."${field}" == null)
            {
                value = property.instanceOf."${field}" ?: ''
            }else{
                //
                return
            }
        }

        if (value == '' && field) {
            // Allow user to set a rel to null be calling set rel ''
            property[field] = null
            property.save(flush: true)
        } else {

            if (property && value && field){

                if(field == "refValue") {
                    def binding_properties = ["${field}": value]
                    bindData(property, binding_properties)
                    //property.save(flush:true)
                    if(!property.save(failOnError: true, flush: true))
                    {
                        println(property.error)
                    }
                } else if(field == "dateValue") {
                    SimpleDateFormat sdf = DateUtil.getSDF_NoTime()

                    def backup = property."${field}"
                    try {
                        if (value && value.size() > 0) {
                            // parse new date
                            def parsed_date = sdf.parse(value)
                            property."${field}" = parsed_date
                        } else {
                            // delete existing date
                            property."${field}" = null
                        }
                        property.save(failOnError: true, flush: true)
                    }
                    catch (Exception e) {
                        property."${field}" = backup
                        log.error( e.toString() )
                    }
                } else if(field == "urlValue") {

                    def backup = property."${field}"
                    try {
                        if (value && value.size() > 0) {
                            property."${field}" = new URL(value)
                        } else {
                            // delete existing url
                            property."${field}" = null
                        }
                        property.save(failOnError: true, flush: true)
                    }
                    catch (Exception e) {
                        property."${field}" = backup
                        log.error( e.toString() )
                    }
                } else {
                    def binding_properties = [:]
                    if (property."${field}" instanceof Double) {
                        value = Double.parseDouble(value)
                    }

                    binding_properties["${field}"] = value
                    bindData(property, binding_properties)

                    property.save(failOnError: true, flush: true)
                }
            }
        }

    }
    def parseDate(datestr, possible_formats) {
        def parsed_date = null;
        if (datestr && (datestr.toString().trim().length() > 0)) {
            for (Iterator i = possible_formats.iterator(); (i.hasNext() && (parsed_date == null));) {
                try {
                    parsed_date = i.next().parse(datestr.toString());
                }
                catch (Exception e) {
                }
            }
        }
        parsed_date
    }


}
