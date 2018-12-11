package com.k_int.kbplus

import com.k_int.properties.PropertyDefinition
import de.laser.AccessService
import de.laser.controller.AbstractDebugController
import de.laser.helper.DebugAnnotation
import de.laser.helper.RDStore
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.annotation.Secured

// 2.0
import grails.converters.*
import com.k_int.kbplus.auth.*
import groovy.time.TimeCategory
import org.apache.poi.hssf.usermodel.HSSFRichTextString
import org.apache.poi.hssf.usermodel.HSSFSheet
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row;
import org.codehaus.groovy.grails.plugins.orm.auditable.AuditLogEvent
import org.codehaus.groovy.runtime.InvokerHelper
import org.elasticsearch.client.Client

import java.text.NumberFormat
import java.text.SimpleDateFormat

//For Transform
import static groovyx.net.http.ContentType.*

@Mixin(com.k_int.kbplus.mixins.PendingChangeMixin)
@Secured(['IS_AUTHENTICATED_FULLY'])
class SubscriptionDetailsController extends AbstractDebugController {

    def springSecurityService
    def contextService
    def addressbookService
    def taskService
    def alertsService
    def genericOIDService
    def transformerService
    def exportService
    def grailsApplication
    def pendingChangeService
    def institutionsService
    def ESSearchService
    def executorWrapperService
    def renewals_reversemap = ['subject':'subject', 'provider':'provid', 'pkgname':'tokname' ]
    def accessService
    def filterService
    def factService
    def docstoreService
    def ESWrapperService
    def globalSourceSyncService
    def dataloadService

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
        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }
        result.contextOrg = contextService.getOrg()
        def verystarttime = exportService.printStart("SubscriptionDetails")

        log.debug("subscriptionDetails id:${params.id} format=${response.format}");

        result.transforms = grailsApplication.config.subscriptionTransforms

        result.max = params.max ? Integer.parseInt(params.max) : ((response.format && response.format != "html" && response.format != "all") ? 10000 : result.user.getDefaultPageSizeTMP());
        result.offset = (params.offset && response.format && response.format != "html") ? Integer.parseInt(params.offset) : 0;

        log.debug("max = ${result.max}");

        def pending_change_pending_status = RefdataValue.getByValueAndCategory('Pending','PendingChangeStatus')
        def pendingChanges = PendingChange.executeQuery("select pc.id from PendingChange as pc where subscription=? and ( pc.status is null or pc.status = ? ) order by ts desc", [result.subscriptionInstance, pending_change_pending_status]);

        if (result.subscriptionInstance?.isSlaved?.value == "Yes" && pendingChanges) {
            log.debug("Slaved subscription, auto-accept pending changes")
            def changesDesc = []
            pendingChanges.each { change ->
                if (!pendingChangeService.performAccept(change, request)) {
                    log.debug("Auto-accepting pending change has failed.")
                } else {
                    changesDesc.add(PendingChange.get(change).desc)
                }
            }
            flash.message = changesDesc
        } else {
            result.pendingChanges = pendingChanges.collect { PendingChange.get(it) }
        }

        // If transformer check user has access to it
        if (params.transforms && !transformerService.hasTransformId(result.user, params.transforms)) {
            flash.error = "It looks like you are trying to use an unvalid transformer or one you don't have access to!"
            params.remove("transforms")
            params.remove("format")
            redirect action: 'currentTitles', params: params
        }

    if ( result.institution ) {
      result.subscriber_shortcode = result.institution.shortcode
      result.institutional_usage_identifier =
              OrgCustomProperty.findByTypeAndOwner(PropertyDefinition.findByName("RequestorID"), result.institution)
    }

        if (params.mode == "advanced") {
            params.asAt = null
        }

        def base_qry = null;

        def deleted_ie = RefdataValue.getByValueAndCategory('Deleted', 'Entitlement Issue Status')
        def qry_params = [result.subscriptionInstance]

        def date_filter
        if (params.asAt && params.asAt.length() > 0) {
            def sdf = new java.text.SimpleDateFormat(message(code: 'default.date.format.notime', default: 'yyyy-MM-dd'));
            date_filter = sdf.parse(params.asAt)
            result.as_at_date = date_filter;
            result.editable = false;
        } else {
            date_filter = new Date()
            result.as_at_date = date_filter;
        }
        // We dont want this filter to reach SQL query as it will break it.
        def core_status_filter = params.sort == 'core_status'
        if (core_status_filter) params.remove('sort');

        if (params.filter) {
            base_qry = " from IssueEntitlement as ie where ie.subscription = ? "
            if (params.mode != 'advanced') {
                // If we are not in advanced mode, hide IEs that are not current, otherwise filter
                // base_qry += "and ie.status <> ? and ( ? >= coalesce(ie.accessStartDate,subscription.startDate) ) and ( ( ? <= coalesce(ie.accessEndDate,subscription.endDate) ) OR ( ie.accessEndDate is null ) )  "
                // qry_params.add(deleted_ie);
                base_qry += "and (( ? >= coalesce(ie.accessStartDate,subscription.startDate) ) OR ( ie.accessStartDate is null )) and ( ( ? <= coalesce(ie.accessEndDate,subscription.endDate) ) OR ( ie.accessEndDate is null ) )  "
                qry_params.add(date_filter);
                qry_params.add(date_filter);
            }
            base_qry += "and ( ( lower(ie.tipp.title.title) like ? ) or ( exists ( from IdentifierOccurrence io where io.ti.id = ie.tipp.title.id and io.identifier.value like ? ) ) ) "
            qry_params.add("%${params.filter.trim().toLowerCase()}%")
            qry_params.add("%${params.filter}%")
        } else {
            base_qry = " from IssueEntitlement as ie where ie.subscription = ? "
            if (params.mode != 'advanced') {
                // If we are not in advanced mode, hide IEs that are not current, otherwise filter

                base_qry += " and (( ? >= coalesce(ie.accessStartDate,subscription.startDate) ) OR ( ie.accessStartDate is null )) and ( ( ? <= coalesce(ie.accessEndDate,subscription.endDate) ) OR ( ie.accessEndDate is null ) ) "
                qry_params.add(date_filter);
                qry_params.add(date_filter);
            }
        }

        base_qry += " and ie.status <> ? "
        qry_params.add(deleted_ie);

        if (params.pkgfilter && (params.pkgfilter != '')) {
            base_qry += " and ie.tipp.pkg.id = ? "
            qry_params.add(Long.parseLong(params.pkgfilter));
        }

        if ((params.sort != null) && (params.sort.length() > 0)) {
            base_qry += "order by lower(ie.${params.sort}) ${params.order} "
        } else {
            base_qry += "order by lower(ie.tipp.title.title) asc"
        }

        result.num_sub_rows = IssueEntitlement.executeQuery("select ie.id " + base_qry, qry_params).size()

        if (params.format == 'html' || params.format == null) {
            result.entitlements = IssueEntitlement.executeQuery("select ie " + base_qry, qry_params, [max: result.max, offset: result.offset]);
        } else {
            result.entitlements = IssueEntitlement.executeQuery("select ie " + base_qry, qry_params);
        }

        // Now we add back the sort so that the sortable column will recognize asc/desc
        // Ignore the sorting if we are doing an export
        if (core_status_filter) {
            params.put('sort', 'core_status');
            if (params.format == 'html' || params.format == null) sortOnCoreStatus(result, params);
        }

        exportService.printDuration(verystarttime, "Querying")

        log.debug("subscriptionInstance returning... ${result.num_sub_rows} rows ");
        def filename = "subscriptionDetails_${result.subscriptionInstance.identifier}"


        if (executorWrapperService.hasRunningProcess(result.subscriptionInstance)) {
            result.processingpc = true
        }

        result.navPrevSubscription = result.subscriptionInstance.previousSubscription
        result.navNextSubscription = Subscription.findByPreviousSubscription(result.subscriptionInstance)

        withFormat {
            html result
            csv {
                response.setHeader("Content-disposition", "attachment; filename=\"${result.subscriptionInstance.identifier}.csv\"")
                response.contentType = "text/csv"
                def out = response.outputStream
                def header = (params.omitHeader == null) || (params.omitHeader != 'Y')
                exportService.StreamOutSubsCSV(out, result.subscriptionInstance, result.entitlements, header)
                out.close()
                exportService.printDuration(verystarttime, "Overall Time")
            }
            json {
                def starttime = exportService.printStart("Building Map")
                def map = exportService.getSubscriptionMap(result.subscriptionInstance, result.entitlements)
                exportService.printDuration(starttime, "Building Map")

                starttime = exportService.printStart("Create JSON")
                def json = map as JSON
                exportService.printDuration(starttime, "Create JSON")

                if (params.transforms) {
                    transformerService.triggerTransform(result.user, filename, params.transforms, json, response)
                } else {
                    response.setHeader("Content-disposition", "attachment; filename=\"${filename}.json\"")
                    response.contentType = "application/json"
                    render json
                }
                exportService.printDuration(verystarttime, "Overall Time")
            }
            xml {
                def starttime = exportService.printStart("Building XML Doc")
                def doc = exportService.buildDocXML("Subscriptions")
                exportService.addSubIntoXML(doc, doc.getDocumentElement(), result.subscriptionInstance, result.entitlements)
                exportService.printDuration(starttime, "Building XML Doc")

                if ((params.transformId) && (result.transforms[params.transformId] != null)) {
                    String xml = exportService.streamOutXML(doc, new StringWriter()).getWriter().toString();
                    transformerService.triggerTransform(result.user, filename, result.transforms[params.transformId], xml, response)
                } else {
                    response.setHeader("Content-disposition", "attachment; filename=\"${filename}.xml\"")
                    response.contentType = "text/xml"
                    starttime = exportService.printStart("Sending XML")
                    exportService.streamOutXML(doc, response.outputStream)
                    exportService.printDuration(starttime, "Sending XML")
                }
                exportService.printDuration(verystarttime, "Overall Time")
            }
        }
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def unlinkPackage() {
        log.debug("unlinkPackage :: ${params}")
        def result = [:]
        result.user = User.get(springSecurityService.principal.id)
        result.subscription = Subscription.get(params.subscription.toLong())
        result.package = Package.get(params.package.toLong())
        def query = "from IssueEntitlement ie, Package pkg where ie.subscription =:sub and pkg.id =:pkg_id and ie.tipp in ( select tipp from TitleInstancePackagePlatform tipp where tipp.pkg.id = :pkg_id ) "
        def queryParams = [sub: result.subscription, pkg_id: result.package.id]

        if (result.subscription.isEditableBy(result.user)) {
            result.editable = true
            if (params.confirmed) {
                //delete matches
                IssueEntitlement.withTransaction { status ->
                    removePackagePendingChanges(result.package.id, result.subscription.id, params.confirmed)
                    def deleteIdList = IssueEntitlement.executeQuery("select ie.id ${query}", queryParams)
                    if (deleteIdList) IssueEntitlement.executeUpdate("delete from IssueEntitlement ie where ie.id in (:delList)", [delList: deleteIdList]);
                    SubscriptionPackage.executeUpdate("delete from SubscriptionPackage sp where sp.pkg=? and sp.subscription=? ", [result.package, result.subscription])
                }
            } else {
                def numOfPCs = removePackagePendingChanges(result.package.id, result.subscription.id, params.confirmed)

                def numOfIEs = IssueEntitlement.executeQuery("select count(ie) ${query}", queryParams)[0]
                def conflict_item_pkg = [name: "${g.message(code: "subscription.details.unlink.linkedPackage")}", details: [['link': createLink(controller: 'packageDetails', action: 'show', id: result.package.id), 'text': result.package.name]], action: [actionRequired: false, text: "${g.message(code: "subscription.details.unlink.linkedPackage.action")}"]]
                def conflicts_list = [conflict_item_pkg]

                if (numOfIEs > 0) {
                    def conflict_item_ie = [name: "${g.message(code: "subscription.details.unlink.packageIEs")}", details: [['text': "${g.message(code: "subscription.details.unlink.packageIEs.numbers")} " + numOfIEs]], action: [actionRequired: false, text: "${g.message(code: "subscription.details.unlink.packageIEs.action")}"]]
                    conflicts_list += conflict_item_ie
                }
                if (numOfPCs > 0) {
                    def conflict_item_pc = [name: "${g.message(code: "subscription.details.unlink.pendingChanges")}", details: [['text': "${g.message(code: "subscription.details.unlink.pendingChanges.numbers")} " + numOfPCs]], action: [actionRequired: false, text: "${g.message(code: "subscription.details.unlink.pendingChanges.numbers.action")}"]]
                    conflicts_list += conflict_item_pc
                }

                return render(template: "unlinkPackageModal", model: [pkg: result.package, subscription: result.subscription, conflicts_list: conflicts_list])
            }
        } else {
            result.editable = false
        }


        redirect(url: request.getHeader('referer'))

    }

    private def removePackagePendingChanges(pkg_id, sub_id, confirmed) {

        def tipp_class = TitleInstancePackagePlatform.class.getName()
        def tipp_id_query = "from TitleInstancePackagePlatform tipp where tipp.pkg.id = ?"
        def change_doc_query = "from PendingChange pc where pc.subscription.id = ? "
        def tipp_ids = TitleInstancePackagePlatform.executeQuery("select tipp.id ${tipp_id_query}", [pkg_id])
        def pendingChanges = PendingChange.executeQuery("select pc.id, pc.changeDoc ${change_doc_query}", [sub_id])

        def pc_to_delete = []
        pendingChanges.each { pc ->
            def parsed_change_info = JSON.parse(pc[1])
            if (parsed_change_info.tippID) {
                pc_to_delete += pc[0]
            }
            else if (parsed_change_info.changeDoc) {
                def (oid_class, ident) = parsed_change_info.changeDoc.OID.split(":")
                if (oid_class == tipp_class && tipp_ids.contains(ident.toLong())) {
                    pc_to_delete += pc[0]
                }
            }
            else {
                log.error("Could not decide if we should delete the pending change id:${pc[0]} - ${parsed_change_info}")
            }
        }
        if (confirmed && pc_to_delete) {
            log.debug("Deleting Pending Changes: ${pc_to_delete}")
            def del_pc_query = "delete from PendingChange where id in (:del_list) "
            PendingChange.executeUpdate(del_pc_query, [del_list: pc_to_delete])
        }
        else {
            return pc_to_delete.size()
        }
    }

    private def sortOnCoreStatus(result, params) {
        result.entitlements.sort { it.getTIP()?.coreStatus(null) }
        if (params.order == 'desc') result.entitlements.reverse(true);
        result.entitlements = result.entitlements.subList(result.offset, (result.offset + result.max).intValue())
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def compare() {
        def result = [:]
        result.unionList = []

        result.user = User.get(springSecurityService.principal.id)
        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeTMP()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0

        if (params.subA?.length() > 0 && params.subB?.length() > 0) {
            log.debug("Subscriptions submitted for comparison ${params.subA} and ${params.subB}.")
            log.debug("Dates submited are ${params.dateA} and ${params.dateB}")

            result.subInsts = []
            result.subDates = []

            def listA
            def listB
            try {
                listA = createCompareList(params.subA, params.dateA, params, result)
                listB = createCompareList(params.subB, params.dateB, params, result)
                if (!params.countA) {
                    def countQuery = "select count(elements(sub.issueEntitlements)) from Subscription sub where sub.id = ?"
                    params.countA = Subscription.executeQuery(countQuery, [result.subInsts.get(0).id])
                    params.countB = Subscription.executeQuery(countQuery, [result.subInsts.get(1).id])
                }
            } catch (IllegalArgumentException e) {
                request.message = e.getMessage()
                return
            }

            result.listACount = listA.size()
            result.listBCount = listB.size()

            def mapA = listA.collectEntries { [it.tipp.title.title, it] }
            def mapB = listB.collectEntries { [it.tipp.title.title, it] }

            //FIXME: It should be possible to optimize the following lines
            def unionList = mapA.keySet().plus(mapB.keySet()).toList()
            unionList = unionList.unique()
            result.unionListSize = unionList.size()
            unionList.sort()

            def filterRules = [params.insrt ? true : false, params.dlt ? true : false, params.updt ? true : false, params.nochng ? true : false]
            withFormat {
                html {
                    def toIndex = result.offset + result.max < unionList.size() ? result.offset + result.max : unionList.size()
                    result.comparisonMap =
                            institutionsService.generateComparisonMap(unionList, mapA, mapB, result.offset, toIndex.intValue(), filterRules)
                    log.debug("Comparison Map" + result.comparisonMap)
                    result
                }
                csv {
                    try {
                        log.debug("Create CSV Response")
                        def comparisonMap =
                                institutionsService.generateComparisonMap(unionList, mapA, mapB, 0, unionList.size(), filterRules)
                        def dateFormatter = new java.text.SimpleDateFormat(message(code: 'default.date.format.notime', default: 'yyyy-MM-dd'))

                        response.setHeader("Content-disposition", "attachment; filename=\"subscriptionComparison.csv\"")
                        response.contentType = "text/csv"
                        def out = response.outputStream
                        out.withWriter { writer ->
                            writer.write("${result.subInsts[0].name} on ${params.dateA}, ${result.subInsts[1].name} on ${params.dateB}\n")
                            writer.write('IE Title, pISSN, eISSN, Start Date A, Start Date B, Start Volume A, Start Volume B, Start Issue A, Start Issue B, End Date A, End Date B, End Volume A, End Volume B, End Issue A, End Issue B, Coverage Note A, Coverage Note B, ColorCode\n');
                            log.debug("UnionList size is ${unionList.size}")
                            comparisonMap.each { title, values ->
                                def ieA = values[0]
                                def ieB = values[1]
                                def colorCode = values[2]
                                def pissn = ieA ? ieA.tipp.title.getIdentifierValue('issn') : ieB.tipp.title.getIdentifierValue('issn');
                                def eissn = ieA ? ieA.tipp.title.getIdentifierValue('eISSN') : ieB.tipp.title.getIdentifierValue('eISSN')

                                writer.write("\"${title}\",\"${pissn ?: ''}\",\"${eissn ?: ''}\",\"${formatDateOrNull(dateFormatter, ieA?.startDate)}\",\"${formatDateOrNull(dateFormatter, ieB?.startDate)}\",\"${ieA?.startVolume ?: ''}\",\"${ieB?.startVolume ?: ''}\",\"${ieA?.startIssue ?: ''}\",\"${ieB?.startIssue ?: ''}\",\"${formatDateOrNull(dateFormatter, ieA?.endDate)}\",\"${formatDateOrNull(dateFormatter, ieB?.endDate)}\",\"${ieA?.endVolume ?: ''}\",\"${ieB?.endVolume ?: ''}\",\"${ieA?.endIssue ?: ''}\",\"${ieB?.endIssue ?: ''}\",\"${ieA?.coverageNote ?: ''}\",\"${ieB?.coverageNote ?: ''}\",\"${colorCode}\"\n")
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
            def currentDate = new java.text.SimpleDateFormat(message(code: 'default.date.format.notime', default: 'yyyy-MM-dd')).format(new Date())
            params.dateA = currentDate
            params.dateB = currentDate
            params.insrt = "Y"
            params.dlt = "Y"
            params.updt = "Y"

            if (contextService.getOrg()) {
                result.institutionName = contextService.getOrg().getName()
                log.debug("FIND ORG NAME ${result.institutionName}")
            }
            flash.message = message(code: 'subscription.compare.note', default: "Please select two subscriptions for comparison")
        }
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
        def sdf = new java.text.SimpleDateFormat(message(code: 'default.date.format.notime', default: 'yyyy-MM-dd'))
        def date = dateStr ? sdf.parse(dateStr) : new Date()
        def subId = sub.substring(sub.indexOf(":") + 1)

        def subInst = Subscription.get(subId)
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

        def base_qry = "from IssueEntitlement as ie where ie.subscription = ? and ie.tipp.title.status.value != 'Deleted' "

        if (showDeletedTipps == false) {
            base_qry += "and ie.tipp.status.value != 'Deleted' "
        }

        if (params.filter) {
            base_qry += " and ( ( lower(ie.tipp.title.title) like ? ) or ( exists ( from IdentifierOccurrence io where io.ti.id = ie.tipp.title.id and io.identifier.value like ? ) ) )"
            qry_params.add("%${params.filter.trim().toLowerCase()}%")
            qry_params.add("%${params.filter}%")
        }

        if (params.startsBefore && params.startsBefore.length() > 0) {
            def sdf = new java.text.SimpleDateFormat(message(code: 'default.date.format.notime', default: 'yyyy-MM-dd'));
            def d = sdf.parse(params.startsBefore)
            base_qry += " and ie.startDate <= ?"
            qry_params.add(d)
        }

        if (asAt != null) {
            base_qry += " and ( ( ? >= coalesce(ie.tipp.accessStartDate, ie.startDate) ) and ( ( ? <= ie.tipp.accessEndDate ) or ( ie.tipp.accessEndDate is null ) ) ) "
            qry_params.add(asAt);
            qry_params.add(asAt);
        }

        return base_qry
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def subscriptionBatchUpdate() {

        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_EDIT)
        if (!result) {
            response.sendError(401); return
        }

        // def formatter = new java.text.SimpleDateFormat("MM/dd/yyyy")
        def formatter = new java.text.SimpleDateFormat(message(code: 'default.date.format.notime', default: 'yyyy-MM-dd'))

        // def subscriptionInstance = Subscription.get(params.id)
        // def user = User.get(springSecurityService.principal.id)
        // userAccessCheck(subscriptionInstance, user, 'edit')

        log.debug("subscriptionBatchUpdate ${params}");

        params.each { p ->
            if (p.key.startsWith('_bulkflag.') && (p.value == 'on')) {
                def ie_to_edit = p.key.substring(10);

                def ie = IssueEntitlement.get(ie_to_edit)

                if (params.bulkOperation == "edit") {

                    if (params.bulk_start_date && (params.bulk_start_date.trim().length() > 0)) {
                        ie.startDate = formatter.parse(params.bulk_start_date)
                    }

                    if (params.bulk_end_date && (params.bulk_end_date.trim().length() > 0)) {
                        ie.endDate = formatter.parse(params.bulk_end_date)
                    }

                    if (params.bulk_core_start && (params.bulk_core_start.trim().length() > 0)) {
                        ie.coreStatusStart = formatter.parse(params.bulk_core_start)
                    }

                    if (params.bulk_core_end && (params.bulk_core_end.trim().length() > 0)) {
                        ie.coreStatusEnd = formatter.parse(params.bulk_core_end)
                    }

                    if (params.bulk_embargo && (params.bulk_embargo.trim().length() > 0)) {
                        ie.embargo = params.bulk_embargo
                    }

                    if (params.bulk_coreStatus && params.bulk_coreStatus.trim().length() > 0) {
                        def selected_refdata = genericOIDService.resolveOID(params.bulk_coreStatus.trim())
                        log.debug("Selected core status is ${selected_refdata}");
                        ie.coreStatus = selected_refdata
                    }

                    if (params.bulk_medium.trim().length() > 0) {
                        def selected_refdata = genericOIDService.resolveOID(params.bulk_medium.trim())
                        log.debug("Selected medium is ${selected_refdata}");
                        ie.medium = selected_refdata
                    }

                    if (params.bulk_coverage && (params.bulk_coverage.trim().length() > 0)) {
                        ie.coverageDepth = params.bulk_coverage
                    }

                    if (ie.save(flush: true)) {
                    } else {
                        log.error("Problem saving ${ie.errors}")
                    }
                } else if (params.bulkOperation == "remove") {
                    log.debug("Updating ie ${ie.id} status to deleted");
                    def deleted_ie = RefdataValue.getByValueAndCategory('Deleted', 'Entitlement Issue Status')
                    ie.status = deleted_ie;
                    if (ie.save(flush: true)) {
                    } else {
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
        log.debug("addEntitlements ..")

        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW_AND_EDIT)
        if (!result) {
            response.sendError(401); return
        }

        result.max = params.max ? Integer.parseInt(params.max) : request.user.getDefaultPageSizeTMP();
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        def tipp_deleted = RefdataCategory.lookupOrCreate(RefdataCategory.TIPP_STATUS, 'Deleted');
        def ie_deleted = RefdataValue.getByValueAndCategory('Deleted', 'Entitlement Issue Status')

        log.debug("filter: \"${params.filter}\"");

        if (result.subscriptionInstance) {
            // We need all issue entitlements from the parent subscription where no row exists in the current subscription for that item.
            def basequery = null;
            def qry_params = [result.subscriptionInstance, tipp_deleted, result.subscriptionInstance, ie_deleted]

            if (params.filter) {
                log.debug("Filtering....");
                basequery = "from TitleInstancePackagePlatform tipp where tipp.pkg in ( select pkg from SubscriptionPackage sp where sp.subscription = ? ) and tipp.status != ? and ( not exists ( select ie from IssueEntitlement ie where ie.subscription = ? and ie.tipp.id = tipp.id and ie.status != ? ) ) and ( ( lower(tipp.title.title) like ? ) OR ( exists ( select io from IdentifierOccurrence io where io.ti.id = tipp.title.id and io.identifier.value like ? ) ) ) "
                qry_params.add("%${params.filter.trim().toLowerCase()}%")
                qry_params.add("%${params.filter}%")
            } else {
                basequery = "from TitleInstancePackagePlatform tipp where tipp.pkg in ( select pkg from SubscriptionPackage sp where sp.subscription = ? ) and tipp.status != ? and ( not exists ( select ie from IssueEntitlement ie where ie.subscription = ? and ie.tipp.id = tipp.id and ie.status != ? ) )"
            }

            if (params.endsAfter && params.endsAfter.length() > 0) {
                def sdf = new java.text.SimpleDateFormat(message(code: 'default.date.format.notime', default: 'yyyy-MM-dd'));
                def d = sdf.parse(params.endsAfter)
                basequery += " and tipp.endDate >= ?"
                qry_params.add(d)
            }

            if (params.startsBefore && params.startsBefore.length() > 0) {
                def sdf = new java.text.SimpleDateFormat(message(code: 'default.date.format.notime', default: 'yyyy-MM-dd'));
                def d = sdf.parse(params.startsBefore)
                basequery += " and tipp.startDate <= ?"
                qry_params.add(d)
            }


            if (params.pkgfilter && (params.pkgfilter != '')) {
                basequery += " and tipp.pkg.id = ? "
                qry_params.add(Long.parseLong(params.pkgfilter));
            }


            if ((params.sort != null) && (params.sort.length() > 0)) {
                basequery += " order by tipp.${params.sort} ${params.order} "
            } else {
                basequery += " order by tipp.title.title asc "
            }

            log.debug("Query ${basequery} ${qry_params}");

            result.num_tipp_rows = IssueEntitlement.executeQuery("select count(tipp) " + basequery, qry_params)[0]
            result.tipps = IssueEntitlement.executeQuery("select tipp ${basequery}", qry_params, [max: result.max, offset: result.offset]);
        } else {
            result.num_sub_rows = 0;
            result.tipps = []
        }

        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def previous() {
        previousAndExpected(params, 'previous');
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def members() {
        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        //if (params.showDeleted == 'Y') {

        def validSubChilds = Subscription.findAllByInstanceOfAndStatusNotEqual(
                result.subscriptionInstance,
                RefdataValue.getByValueAndCategory('Deleted', 'Subscription Status')
        )

        result.validSubChilds = validSubChilds.sort{ a, b ->
            def sa = a.getSubscriber()
            def sb = b.getSubscriber()
            (sa.sortname ?: sa.name).compareTo( (sb.sortname ?: sb.name) )
        }

        def deletedSubChilds = Subscription.findAllByInstanceOfAndStatus(
                result.subscriptionInstance,
                RefdataValue.getByValueAndCategory('Deleted', 'Subscription Status')
        )
        result.deletedSubChilds = deletedSubChilds
        //}
        //else {
        //    result.subscriptionChildren = Subscription.executeQuery(
        //           "select sub from Subscription as sub where sub.instanceOf = ? and sub.status.value != 'Deleted'",
        //            [result.subscriptionInstance]
        //    )
        //}

        result.navPrevSubscription = result.subscriptionInstance.previousSubscription
        result.navNextSubscription = Subscription.findByPreviousSubscription(result.subscriptionInstance)

        if ( params.exportXLS=='yes' ) {

            def orgs = []


           validSubChilds.each { subChild ->

               subChild.getAllSubscribers().each { subscr ->
                   orgs.add(subscr)
               }
           }

            def message = g.message(code: 'subscriptionDetails.members.members')

            exportOrg(orgs, message, true)
            return
        }

        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def addMembers() {
        log.debug("addMembers ..")

        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW_AND_EDIT)
        if (!result) {
            response.sendError(401); return
        }

        if ((com.k_int.kbplus.RefdataValue.getByValueAndCategory('Consortium', 'OrgRoleType')?.id in result.institution?.getallOrgRoleTypeIds())) {
            def fsq = filterService.getOrgComboQuery(params, result.institution)
            result.cons_members = Org.executeQuery(fsq.query, fsq.queryParams, params)
            result.cons_members_disabled = []
            result.cons_members.each { it ->
                if (Subscription.executeQuery("select s from Subscription as s join s.orgRelations as sor where s.instanceOf = ? and sor.org.id = ?",
                        [result.subscriptionInstance, it.id])
                ) {
                    result.cons_members_disabled << it.id
                }
            }
        }

        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def processAddMembers() {
        log.debug(params)

        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW_AND_EDIT)
        if (!result) {
            response.sendError(401); return
        }

        def orgRoleType       = [com.k_int.kbplus.RefdataValue.getByValueAndCategory('Institution', 'OrgRoleType')?.id.toString()]
        if ((com.k_int.kbplus.RefdataValue.getByValueAndCategory('Consortium', 'OrgRoleType')?.id in result.institution?.getallOrgRoleTypeIds())) {
            orgRoleType = [com.k_int.kbplus.RefdataValue.getByValueAndCategory('Consortium', 'OrgRoleType')?.id.toString()]
        }

        def subStatus     = RefdataValue.get(params.subStatus) ?: RefdataCategory.lookupOrCreate('Subscription Status', 'Current')

        def role_sub      = RDStore.OR_SUBSCRIBER_CONS
        def role_sub_cons = RDStore.OR_SUBSCRIPTION_CONSORTIA
        def role_lic      = RDStore.OR_LICENSEE_CONS
        def role_lic_cons = RDStore.OR_LICENSING_CONSORTIUM

        def role_provider = RefdataValue.getByValueAndCategory('Provider', 'Organisational Role')
        def role_agency   = RefdataValue.getByValueAndCategory('Agency', 'Organisational Role')


        if (accessService.checkMinUserOrgRole(result.user, result.institution, 'INST_EDITOR')) {

            if ((com.k_int.kbplus.RefdataValue.getByValueAndCategory('Consortium', 'OrgRoleType')?.id in result.institution?.getallOrgRoleTypeIds())) {
                def cons_members = []
                def licenseCopy

                params.list('selectedOrgs').each { it ->
                    def fo = Org.findById(Long.valueOf(it))
                    cons_members << Combo.executeQuery("select c.fromOrg from Combo as c where c.toOrg = ? and c.fromOrg = ?", [result.institution, fo])
                }

                def subLicense = result.subscriptionInstance.owner

                cons_members.each { cm ->

                    def postfix = (cons_members.size() > 1) ? 'Teilnehmervertrag' : (cm.get(0).shortname ?: cm.get(0).name)

                    if (subLicense) {
                        def subLicenseParams = [
                                lic_name: "${subLicense.reference} (${postfix})",
                                isSlaved: params.isSlaved,
                                asOrgRoleType: orgRoleType,
                                copyStartEnd: true
                        ]

                        if (params.generateSlavedLics == 'explicit') {
                            licenseCopy = institutionsService.copyLicense(subLicense, subLicenseParams)
                        }
                        else if (params.generateSlavedLics == 'shared' && ! licenseCopy) {
                            licenseCopy = institutionsService.copyLicense(subLicense, subLicenseParams)
                        }
                        else if (params.generateSlavedLics == 'reference' && ! licenseCopy) {
                            licenseCopy = genericOIDService.resolveOID(params.generateSlavedLicsReference)
                        }

                        if (licenseCopy) {
                            new OrgRole(org: cm, lic: licenseCopy, roleType: role_lic).save()
                        }
                    }

                    if (params.generateSlavedSubs == "Y") {
                        log.debug("Generating seperate slaved instances for consortia members")

                        def cons_sub = new Subscription(
                                type: result.subscriptionInstance.type?:"",
                                status: subStatus,
                                name: result.subscriptionInstance.name,
                                //name: result.subscriptionInstance.name + " (" + (cm.get(0).shortname ?: cm.get(0).name) + ")",
                                startDate: result.subscriptionInstance.startDate,
                                endDate: result.subscriptionInstance.endDate,
                                manualRenewalDate: result.subscriptionInstance.manualRenewalDate,
                                /* manualCancellationDate: result.subscriptionInstance.manualCancellationDate, */
                                identifier: java.util.UUID.randomUUID().toString(),
                                instanceOf: result.subscriptionInstance,
                                isSlaved: RefdataValue.getByValueAndCategory('Yes', 'YN'),
                                isPublic: result.subscriptionInstance.isPublic,
                                impId: java.util.UUID.randomUUID().toString(),
                                owner: licenseCopy,
                                resource: result.subscriptionInstance.resource ?: null,
                                form: result.subscriptionInstance.form ?: null
                        )

                        if(!cons_sub.save()){
                            cons_sub?.errors.each { e ->
                                log.debug("Problem creating new sub: ${e}")
                            }
                            flash.error = cons_sub.errors
                        }


                        result.subscriptionInstance.packages.each { sub_pkg ->
                                    def takePackage = params."selectedPackage_${cm.get(0).id+sub_pkg.pkg.id}"
                                    def takeIE = params."selectedIssueEntitlement_${cm.get(0).id+sub_pkg.pkg.id}"

                                    log.debug("Package:${takePackage} IE:${takeIE}")

                                    def pkg_to_link = sub_pkg.pkg
                                    //def sub_instances = Subscription.executeQuery("select s from Subscription as s where s.instanceOf = ? ", [result.subscriptionInstance])
                                    if (takeIE) {
                                        pkg_to_link.addToSubscription(cons_sub, true)
                                        /*sub_instances.each {
                                            pkg_to_link.addToSubscription(it, true)
                                        }*/
                                    } else if (takePackage) {
                                        pkg_to_link.addToSubscription(cons_sub, false)
                                        /*sub_instances.each {
                                            pkg_to_link.addToSubscription(it, false)
                                        }*/
                                    }
                        }

                        if (cons_sub) {

                            def providers = OrgRole.findAllBySubAndRoleType(result.subscriptionInstance, role_provider)

                            providers.each { provider ->
                                new OrgRole(org: provider.org, sub: cons_sub, roleType: role_provider).save()
                            }

                            def agencys = OrgRole.findAllBySubAndRoleType(result.subscriptionInstance, role_agency)
                            agencys.each { agency ->
                                new OrgRole(org: agency.org, sub: cons_sub, roleType: role_agency).save()
                            }

                            new OrgRole(org: cm, sub: cons_sub, roleType: role_sub).save()
                            new OrgRole(org: result.institution, sub: cons_sub, roleType: role_sub_cons).save()
                        }


                    }
                }
                redirect controller: 'subscriptionDetails', action: 'members', params: [id: result.subscriptionInstance?.id]
            }
            else {
                redirect controller: 'subscriptionDetails', action: 'show', params: [id: result.subscriptionInstance?.id]
            }
        }else {
            redirect controller: 'subscriptionDetails', action: 'show', params: [id: result.subscriptionInstance?.id]
        }
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def deleteMember() {
        log.debug(params)

        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW_AND_EDIT)
        if (!result) {
            response.sendError(401); return
        }

        def delSubscription = genericOIDService.resolveOID(params.target)
        def delInstitution = delSubscription?.getSubscriber()

        def deletedStatus = RefdataValue.getByValueAndCategory('Deleted', 'Subscription Status')

        if (delSubscription?.hasPerm("edit", result.user)) {
            def derived_subs = Subscription.findByInstanceOfAndStatusNot(delSubscription, deletedStatus)

            if (!derived_subs) {
                if (delSubscription.getConsortia() && delSubscription.getConsortia() != delInstitution) {
                    OrgRole.executeUpdate("delete from OrgRole where sub = ? and org = ?", [delSubscription, delInstitution])
                }
                delSubscription.status = deletedStatus
                delSubscription.save(flush: true)

            } else {
                flash.error = message(code: 'myinst.actionCurrentSubscriptions.error', default: 'Unable to delete - The selected license has attached subscriptions')
            }
        } else {
            log.warn("${result.user} attempted to delete subscription ${delSubscription} without perms")
            flash.message = message(code: 'subscription.delete.norights')
        }

        redirect action: 'members', params: [id: params.id], model: result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def pendingChanges() {
        log.debug("subscriptionDetails id:${params.id}");

        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        def validSubChilds = Subscription.findAllByInstanceOfAndStatusNotEqual(
                result.subscriptionInstance,
                RefdataValue.getByValueAndCategory('Deleted', 'Subscription Status')
        )

        validSubChilds = validSubChilds.sort{ a, b ->
            def sa = a.getSubscriber()
            def sb = b.getSubscriber()
            (sa.sortname ?: sa.name).compareTo( (sb.sortname ?: sb.name) )
        }

        result.pendingChanges = [:]

        validSubChilds.each{ member ->

            if (executorWrapperService.hasRunningProcess(member)) {
                log.debug("PendingChange processing in progress")
                result.processingpc = true
            }
            else {
                def pending_change_pending_status = RefdataValue.getByValueAndCategory('Pending','PendingChangeStatus')
                def pendingChanges = PendingChange.executeQuery("select pc.id from PendingChange as pc where subscription.id=? and ( pc.status is null or pc.status = ? ) order by pc.ts desc", [member.id, pending_change_pending_status])

                result.pendingChanges << [ "${member.id}" : pendingChanges.collect { PendingChange.get(it) }]
            }
        }

        result.navPrevSubscription = result.subscriptionInstance.previousSubscription
        result.navNextSubscription = Subscription.findByPreviousSubscription(result.subscriptionInstance)

        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def expected() {
        previousAndExpected(params, 'expected');
    }

    private def previousAndExpected(params, screen) {
        log.debug("previousAndExpected ${params}");

        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        if (! result.subscriptionInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'package.label', default: 'Subscription'), params.id])
            redirect action: 'list'
            return
        }

        result.max = params.max ? Integer.parseInt(params.max) : request.user.getDefaultPageSizeTMP()
        params.max = result.max
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        def limits = (!params.format || params.format.equals("html")) ? [max: result.max, offset: result.offset] : [offset: 0]

        def qry_params = [result.subscriptionInstance]
        def date_filter = new Date();

        def base_qry = "from IssueEntitlement as ie where ie.subscription = ? "
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
        result.num_ie_rows = IssueEntitlement.executeQuery("select count(ie) " + base_qry, qry_params)[0]

        result.lastie = result.offset + result.max > result.num_ie_rows ? result.num_ie_rows : result.offset + result.max;

        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def processAddEntitlements() {
        log.debug("addEntitlements....");

        params.id = params.siid // TODO refactoring frontend siid -> id
        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_EDIT)
        if (!result) {
            response.sendError(401); return
        }

        //result.user = User.get(springSecurityService.principal.id)
        //result.subscriptionInstance = Subscription.get(params.siid)
        //result.institution = result.subscriptionInstance?.subscriber
        //userAccessCheck(result.subscriptionInstance, result.user, 'edit')

        if (result.subscriptionInstance) {
            params.each { p ->
                if (p.key.startsWith('_bulkflag.')) {
                    def tipp_id = p.key.substring(10);
                    // def ie = IssueEntitlement.get(ie_to_edit)
                    def tipp = TitleInstancePackagePlatform.get(tipp_id)

                    if (tipp == null) {
                        log.error("Unable to tipp ${tipp_id}");
                        flash.error("Unable to tipp ${tipp_id}");
                    } else {
                        def ie_current = RefdataValue.getByValueAndCategory('Current', 'Entitlement Issue Status')

                        def new_ie = new IssueEntitlement(status: ie_current,
                                subscription: result.subscriptionInstance,
                                tipp: tipp,
                                accessStartDate: tipp.accessStartDate,
                                accessEndDate: tipp.accessEndDate,
                                startDate: tipp.startDate,
                                startVolume: tipp.startVolume,
                                startIssue: tipp.startIssue,
                                endDate: tipp.endDate,
                                endVolume: tipp.endVolume,
                                endIssue: tipp.endIssue,
                                embargo: tipp.embargo,
                                coverageDepth: tipp.coverageDepth,
                                coverageNote: tipp.coverageNote,
                                ieReason: 'Manually Added by User')
                        if (new_ie.save(flush: true)) {
                            log.debug("Added tipp ${tipp_id} to sub ${params.siid}");
                        } else {
                            new_ie.errors.each { e ->
                                log.error(e);
                            }
                            flash.error = new_ie.errors
                        }
                    }
                }
            }
        } else {
            log.error("Unable to locate subscription instance");
        }

        redirect action: 'index', id: result.subscriptionInstance?.id
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def removeEntitlement() {
        log.debug("removeEntitlement....");
        def ie = IssueEntitlement.get(params.ieid)
        def deleted_ie = RefdataValue.getByValueAndCategory('Deleted', 'Entitlement Issue Status')
        ie.status = deleted_ie;

        redirect action: 'index', id: params.sub
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def notes() {
        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }
        result.contextOrg = contextService.getOrg()
        if (result.institution) {
            result.subscriber_shortcode = result.institution.shortcode
        }
        result.navPrevSubscription = result.subscriptionInstance.previousSubscription
        result.navNextSubscription = Subscription.findByPreviousSubscription(result.subscriptionInstance)
        result
    }


    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def documents() {
        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }
        result.contextOrg = contextService.getOrg()
        if (result.institution) {
            result.subscriber_shortcode = result.institution.shortcode
        }

        result.navPrevSubscription = result.subscriptionInstance.previousSubscription
        result.navNextSubscription = Subscription.findByPreviousSubscription(result.subscriptionInstance)
        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def tasks() {
        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }
        result.contextOrg = contextService.getOrg()

        if (params.deleteId) {
            def dTask = Task.get(params.deleteId)
            if (dTask && dTask.creator.id == result.user.id) {
                try {
                    flash.message = message(code: 'default.deleted.message', args: [message(code: 'task.label', default: 'Task'), dTask.title])
                    dTask.delete(flush: true)
                }
                catch (Exception e) {
                    flash.message = message(code: 'default.not.deleted.message', args: [message(code: 'task.label', default: 'Task'), params.deleteId])
                }
            }
        }

        if (result.institution) {
            result.subscriber_shortcode = result.institution.shortcode
        }
        result.taskInstanceList = taskService.getTasksByResponsiblesAndObject(result.user, contextService.getOrg(), result.subscriptionInstance, params)

        result.navPrevSubscription = result.subscriptionInstance.previousSubscription
        result.navNextSubscription = Subscription.findByPreviousSubscription(result.subscriptionInstance)

        log.debug(result.taskInstanceList)
        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def renewals() {
        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        if (result.institution) {
            result.subscriber_shortcode = result.institution.shortcode
        }
        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def deleteDocuments() {
        def ctxlist = []

        log.debug("deleteDocuments ${params}");

        docstoreService.unifiedDeleteDocuments(params)

        redirect controller: 'subscriptionDetails', action: params.redirectAction, id: params.instanceId
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def permissionInfo() {
        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }
        result.contextOrg = contextService.getOrg()
        result.navPrevSubscription = result.subscriptionInstance.previousSubscription
        result.navNextSubscription = Subscription.findByPreviousSubscription(result.subscriptionInstance)
        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def launchRenewalsProcess() {
        def result = [:]
        result.user = User.get(springSecurityService.principal.id)
        result.subscriptionInstance = Subscription.get(params.id)
        result.institution = result.subscriptionInstance.subscriber

        def shopping_basket = UserFolder.findByUserAndShortcode(result.user, 'RenewalsBasket') ?: new UserFolder(user: result.user, shortcode: 'RenewalsBasket').save(flush: true);

        log.debug("Clear basket....");
        shopping_basket.items?.clear();
        shopping_basket.save(flush: true)

        def oid = "com.k_int.kbplus.Subscription:${params.id}"
        shopping_basket.addIfNotPresent(oid)
        Subscription.get(params.id).packages.each {
            oid = "com.k_int.kbplus.Package:${it.pkg.id}"
            shopping_basket.addIfNotPresent(oid)
        }

        redirect controller: 'myInstitution', action: 'renewalsSearch'
    }

    /*
    @Deprecated
    private def userAccessCheck(sub, user, role_str) {
        if ((sub == null || user == null) || (!sub.hasPerm(role_str, user))) {
            response.sendError(401);
            return
        }
    }
    */

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def acceptChange() {
        processAcceptChange(params, Subscription.get(params.id), genericOIDService)
        redirect controller: 'subscriptionDetails', action: 'index', id: params.id
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def rejectChange() {
        processRejectChange(params, Subscription.get(params.id))
        redirect controller: 'subscriptionDetails', action: 'index', id: params.id
    }


    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def possibleLicensesForSubscription() {
        def result = []

        def subscription = genericOIDService.resolveOID(params.oid)
        def subscriber = subscription.getSubscriber();
        def consortia = subscription.getConsortia();

    result.add([value:'', text:'None']);

        if (subscriber || consortia) {

            def licensee_role =  RDStore.OR_LICENSEE
            def licensee_cons_role = RDStore.OR_LICENSING_CONSORTIUM

            def template_license_type = RefdataValue.getByValueAndCategory('Template', 'License Type')

            def qry_params = [(subscription.instanceOf ? consortia : subscriber), licensee_role, licensee_cons_role]

            def qry = ""

            if(subscription.instanceOf)
            {
                qry = """
select l from License as l 
where exists ( select ol from OrgRole as ol where ol.lic = l AND ol.org = ? and ( ol.roleType = ? or ol.roleType = ?) ) 
AND l.status.value != 'Deleted' AND (l.instanceOf is not null) order by LOWER(l.reference)
"""
            }
            else {
                qry = """
select l from License as l 
where exists ( select ol from OrgRole as ol where ol.lic = l AND ol.org = ? and ( ol.roleType = ? or ol.roleType = ?) ) 
AND l.status.value != 'Deleted' order by LOWER(l.reference)
"""
            }
           if(subscriber == consortia)
           {
               qry_params = [consortia, licensee_cons_role]
               qry = """
select l from License as l 
where exists ( select ol from OrgRole as ol where ol.lic = l AND ol.org = ? and ( ol.roleType = ?) ) 
AND l.status.value != 'Deleted' AND (l.instanceOf is null) order by LOWER(l.reference)
"""
           }

            def license_list = License.executeQuery(qry, qry_params);
            license_list.each { l ->
                result.add([value: "${l.class.name}:${l.id}", text: l.reference ?: "No reference - license ${l.id}"]);
            }
        }
        render result as JSON
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def linkPackage() {
        log.debug("Link package, params: ${params} ");

        //Wenn die Subsc schon ein Anbieter hat. Soll nur Pakete von diesem Anbieter angezeigt werden als erstes
        /*if(params.size() == 3) {
            def subInstance = Subscription.get(params.id)
                    subInstance.orgRelations?.each { or ->
           if (or.roleType.value == "Provider") {
                params.put("cpname", or.org.name)
                }
            }
        }*/

        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW_AND_EDIT)
        if (!result) {
            response.sendError(401); return
        }

        if ((com.k_int.kbplus.RefdataValue.getByValueAndCategory('Consortium', 'OrgRoleType')?.id in result.institution?.getallOrgRoleTypeIds())) {

        }

        //Change to GOKB ElasticSearch
        params.esgokb = "Package"
        params.sort = "name"

        if (params.addType && (params.addType != '')) {
            if(params.esgokb)
            {
                def gri = GlobalRecordInfo.findByIdentifier(params.addId)
                def grt = GlobalRecordTracker.findByOwner(gri)
                if(!grt){
                    def new_tracker_id = java.util.UUID.randomUUID().toString()

                    grt = new GlobalRecordTracker(
                                        owner: gri,
                                        identifier: new_tracker_id,
                                        name: gri.name,
                                        autoAcceptTippAddition: params.autoAcceptTippAddition == 'on' ? true : false,
                                        autoAcceptTippDelete: params.autoAcceptTippDelete == 'on' ? true : false,
                                        autoAcceptTippUpdate: params.autoAcceptTippUpdate == 'on' ? true : false,
                                        autoAcceptPackageUpdate: params.autoAcceptPackageChange == 'on' ? true : false)
                    if ( grt.save() ) {
                         globalSourceSyncService.initialiseTracker(grt);
                        //Update INDEX ES
                        dataloadService.updateFTIndexes();
                    }
                    else {
                          log.error(grt.errors)
                    }
                }
                if (!Package.findByImpId(grt.owner.identifier)){
                    globalSourceSyncService.initialiseTracker(grt);
                    //Update INDEX ES
                    dataloadService.updateFTIndexes();
                }

                def pkg_to_link =Package.findByImpId(grt.owner.identifier)
                def sub_instances = Subscription.executeQuery("select s from Subscription as s where s.instanceOf = ? ", [result.subscriptionInstance])
                log.debug("Add package ${params.addType} to subscription ${params}");

                if (params.addType == 'With') {
                    pkg_to_link.addToSubscription(result.subscriptionInstance, true)

                    sub_instances.each {
                        pkg_to_link.addToSubscription(it, true)
                    }

                    redirect action:'index', id:params.id
                }
                else if ( params.addType == 'Without' ) {
                    pkg_to_link.addToSubscription(result.subscriptionInstance, false)

                    sub_instances.each {
                        pkg_to_link.addToSubscription(it, false)
                    }

                    redirect action: 'addEntitlements', id: params.id
                }

            }else
            {
                def pkg_to_link = Package.get(params.addId)
                def sub_instances = Subscription.executeQuery("select s from Subscription as s where s.instanceOf = ? ", [result.subscriptionInstance])
                log.debug("Add package ${params.addType} to subscription ${params}");

                if (params.addType == 'With') {
                    pkg_to_link.addToSubscription(result.subscriptionInstance, true)

                    sub_instances.each {
                        pkg_to_link.addToSubscription(it, true)
                    }

                    redirect action:'index', id:params.id
                }
                else if ( params.addType == 'Without' ) {
                     pkg_to_link.addToSubscription(result.subscriptionInstance, false)

                    sub_instances.each {
                        pkg_to_link.addToSubscription(it, false)
                    }

                    redirect action: 'addEntitlements', id: params.id
                }
            }
        }

        if (result.subscriptionInstance.packages) {
            result.pkgs = []
            if(params.esgokb) {
                result.subscriptionInstance.packages.each { sp ->
                    result.pkgs.add(sp.pkg.impId)
                }
            }else {
                result.subscriptionInstance.packages.each { sp ->
                    result.pkgs.add(sp.pkg.id)
                }
            }
        }

    if (result.institution) {
      result.subscriber_shortcode = result.institution.shortcode
      result.institutional_usage_identifier =
        OrgCustomProperty.findByTypeAndOwner(PropertyDefinition.findByName("RequestorID"), result.institution)
    }
    log.debug("Going for ES")
    User user   = springSecurityService.getCurrentUser()
    params.max = user?.getDefaultPageSizeTMP()?:25

    result.putAll(ESSearchService.search(params))
        result
    }

    def buildRenewalsQuery(params) {
        log.debug("BuildQuery...");

        StringWriter sw = new StringWriter()

        // sw.write("subtype:'Subscription Offered'")
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

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def history() {
        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }
        result.contextOrg = contextService.getOrg()
        result.max = params.max ?: result.user.getDefaultPageSizeTMP();
        result.offset = params.offset ?: 0;

        def qry_params = [result.subscription.class.name, "${result.subscription.id}"]
        result.historyLines = AuditLogEvent.executeQuery("select e from AuditLogEvent as e where className=? and persistedObjectId=? order by id desc", qry_params, [max: result.max, offset: result.offset]);
        result.historyLinesTotal = AuditLogEvent.executeQuery("select count(e.id) from AuditLogEvent as e where className=? and persistedObjectId=?", qry_params)[0];

        result.navPrevSubscription = result.subscriptionInstance.previousSubscription
        result.navNextSubscription = Subscription.findByPreviousSubscription(result.subscriptionInstance)

        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def changes() {
        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        result.contextOrg = contextService.getOrg()
        result.max = params.max ?: result.user.getDefaultPageSizeTMP();
        result.offset = params.offset ?: 0;

        def baseQuery = "select pc from PendingChange as pc where pc.subscription = :sub and pc.subscription.status.value != 'Deleted' and pc.status.value in (:stats)"
        def baseParams = [sub: result.subscription, stats: ['Accepted', 'Rejected']]

        result.todoHistoryLines = PendingChange.executeQuery(
                baseQuery + " order by pc.ts desc",
                baseParams,
                [max: result.max, offset: result.offset]
        )

        result.todoHistoryLinesTotal = PendingChange.executeQuery(
                baseQuery,
                baseParams
        )[0]

        result.navPrevSubscription = result.subscriptionInstance.previousSubscription
        result.navNextSubscription = Subscription.findByPreviousSubscription(result.subscriptionInstance)

        result
    }


    // TODO Cost per use tab, still needed?
    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def costPerUse() {
        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        if ( result.institution ) {
          result.subscriber_shortcode = result.institution.shortcode
          result.institutional_usage_identifier =
                  OrgCustomProperty.findByTypeAndOwner(PropertyDefinition.findByName("RequestorID"), result.institution)
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
                        start: it[3].startDate,
                        end  : it[3].endDate,
                        sub  : result.subscription,
                        factType : 'STATS:JR1'])[0]

                if (usage_str && usage_str.trim().length() > 0) {
                    cost_row.total_usage_for_sub = Double.parseDouble(usage_str);
                    if (cost_row.total_usage_for_sub > 0) {
                        cost_row.overall_cost_per_use = cost_row.total_cost_for_sub / cost_row.total_usage_for_sub;
                    }
                    else {
                        cost_row.overall_cost_per_use = 0;
                    }
                }
                else {
                    cost_row.total_usage_for_sub = Double.parseDouble('0');
                    cost_row.overall_cost_per_use = cost_row.total_usage_for_sub
                }

                // Work out what cost items appear under this subscription in the period given
                cost_row.usage = Fact.executeQuery(USAGE_FOR_SUB_IN_PERIOD,[start:it[3].startDate, end:it[3].endDate, sub:result.subscription, jr1a:'STATS:JR1' ])
                cost_row.billingCurrency = it[3].billingCurrency.value.take(3)
                result.costItems.add(cost_row);
              }
              else {
                log.error("Invoice ${it} had no start or end date");
              }
            }

        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def show() {
        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        // unlink license

        if (params.cmd?.equalsIgnoreCase('unlinkLicense')) {
            if (result.subscriptionInstance.owner) {
                result.subscriptionInstance.setOwner(null)
                params.remove('cmd')
                redirect(url: request.getHeader('referer'))
                return
            }
        }

        //if (!result.institution) {
        //    result.institution = result.subscriptionInstance.subscriber ?: result.subscriptionInstance.consortia
        //}
        if (result.institution) {
            result.subscriber_shortcode = result.institution.shortcode
            result.institutional_usage_identifier =
                    OrgCustomProperty.findByTypeAndOwner(PropertyDefinition.findByName("RequestorID"), result.institution)
        }

        result.navPrevSubscription = result.subscriptionInstance.previousSubscription
        result.navNextSubscription = Subscription.findByPreviousSubscription(result.subscriptionInstance)


        // ---- pendingChanges : start

        if (executorWrapperService.hasRunningProcess(result.subscriptionInstance)) {
            log.debug("PendingChange processing in progress")
            result.processingpc = true
        } else {

            def pending_change_pending_status = RefdataValue.getByValueAndCategory('Pending','PendingChangeStatus')
            def pendingChanges = PendingChange.executeQuery("select pc.id from PendingChange as pc where subscription=? and ( pc.status is null or pc.status = ? ) order by pc.ts desc", [result.subscription, pending_change_pending_status])

            log.debug("pc result is ${result.pendingChanges}")

            if (result.subscription.isSlaved?.value == "Yes" && pendingChanges) {
                log.debug("Slaved subscription, auto-accept pending changes")
                def changesDesc = []
                pendingChanges.each { change ->
                    if (!pendingChangeService.performAccept(change, request)) {
                        log.debug("Auto-accepting pending change has failed.")
                    } else {
                        changesDesc.add(PendingChange.get(change).desc)
                    }
                }
                flash.message = changesDesc
            } else {
                result.pendingChanges = pendingChanges.collect { PendingChange.get(it) }
            }
        }

        // ---- pendingChanges : end



        // tasks
        def contextOrg  = contextService.getOrg()
        result.tasks    = taskService.getTasksByResponsiblesAndObject(result.user, contextOrg, result.subscriptionInstance)
        def preCon      = taskService.getPreconditions(contextOrg)
        result << preCon

        // restrict visible for templates/links/orgLinksAsList
        result.visibleOrgRelations = []
        result.subscriptionInstance.orgRelations?.each { or ->
            if (!(or.org?.id == contextService.getOrg()?.id) && !(or.roleType.value in ['Subscriber', 'Subscriber_Consortial'])) {
                result.visibleOrgRelations << or
            }
        }
        result.visibleOrgRelations.sort{it.org.sortname}

        // -- private properties

        result.authorizedOrgs = result.user?.authorizedOrgs
        result.contextOrg = contextService.getOrg()

        // create mandatory OrgPrivateProperties if not existing

        def mandatories = []
        result.user?.authorizedOrgs?.each { org ->
            def ppd = PropertyDefinition.findAllByDescrAndMandatoryAndTenant("Subscription Property", true, org)
            if (ppd) {
                mandatories << ppd
            }
        }
        mandatories.flatten().each { pd ->
            if (!SubscriptionPrivateProperty.findWhere(owner: result.subscriptionInstance, type: pd)) {
                def newProp = PropertyDefinition.createGenericProperty(PropertyDefinition.PRIVATE_PROPERTY, result.subscriptionInstance, pd)

            if (newProp.hasErrors()) {
                log.error(newProp.errors)
            } else {
                log.debug("New subscription private property created via mandatory: " + newProp.type.name)
            }
        }
    }

        // -- private properties

        result.modalPrsLinkRole = RefdataValue.findByValue('Specific subscription editor')
        result.modalVisiblePersons = addressbookService.getPrivatePersonsByTenant(contextService.getOrg())

        result.visiblePrsLinks = []

          result.subscriptionInstance.prsLinks.each { pl ->
              if (! result.visiblePrsLinks.contains(pl.prs)) {
                  if (pl.prs.isPublic?.value != 'No') {
                      result.visiblePrsLinks << pl
                  }
                  else {
                      // nasty lazy loading fix
                      result.user.authorizedOrgs.each{ ao ->
                          if (ao.getId() == pl.prs.tenant.getId()) {
                              result.visiblePrsLinks << pl
                          }
                      }
                  }
              }
          }
        // usage
        def suppliers = result.subscriptionInstance.issueEntitlements?.tipp.pkg.contentProvider?.id.unique()

        if (suppliers) {
          if (suppliers.size() > 1) {
            log.debug('Found different content providers, cannot show usage')
          } else  {
            def supplier_id = suppliers[0]
            result.natStatSupplierId = Org.get(supplier_id).getIdentifierByType('statssid')?.value
            result.institutional_usage_identifier =
                OrgCustomProperty.findByTypeAndOwner(PropertyDefinition.findByName("RequestorID"), result.institution)
            if (result.institutional_usage_identifier) {

                def fsresult = factService.generateUsageData(result.institution.id, supplier_id, result.subscriptionInstance)
                def fsLicenseResult = factService.generateUsageDataForSubscriptionPeriod(result.institution.id, supplier_id, result.subscriptionInstance)

                def holdingTypes = result.subscriptionInstance.getHoldingTypes() ?: null
                if (!holdingTypes) {
                    log.debug('No types found, maybe there are no issue entitlements linked to subscription')
                } else if (holdingTypes.size()>1){
                    log.info('Different content type for this license, cannot calculate Cost Per Use.')
                } else if (! fsLicenseResult.isEmpty()){
                    def existingReportMetrics = fsLicenseResult.y_axis_labels*.split(':')*.last()
                    def costPerUseMetricValuePair = factService.getTotalCostPerUse(result.subscriptionInstance, holdingTypes.first(), existingReportMetrics)
                    if (costPerUseMetricValuePair) {
                        result.costPerUseMetric = costPerUseMetricValuePair[0]
                        result.totalCostPerUse = costPerUseMetricValuePair[1]
                        result.currencyCode = NumberFormat.getCurrencyInstance().getCurrency().currencyCode
                    }
                }

                result.statsWibid = result.institution.getIdentifierByType('wibid')?.value
                result.usageMode = ((com.k_int.kbplus.RefdataValue.getByValueAndCategory('Consortium', 'OrgRoleType')?.id in result.institution?.getallOrgRoleTypeIds())) ? 'package' : 'institution'
                result.usage = fsresult?.usage
                result.x_axis_labels = fsresult?.x_axis_labels
                result.y_axis_labels = fsresult?.y_axis_labels

                result.lusage = fsLicenseResult?.usage
                result.l_x_axis_labels = fsLicenseResult?.x_axis_labels
                result.l_y_axis_labels = fsLicenseResult?.y_axis_labels
            }
          }
        }

        result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def renewSubscriptionConsortia(){

        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!(result || (com.k_int.kbplus.RefdataValue.getByValueAndCategory('Consortium', 'OrgRoleType')?.id in contextService.getOrg()?.getallOrgRoleTypeIds()) ) ) {
               response.sendError(401); return
        }

        if ((com.k_int.kbplus.RefdataValue.getByValueAndCategory('Consortium', 'OrgRoleType')?.id in result.institution?.getallOrgRoleTypeIds())) {
            def baseSub = Subscription.get(params.baseSubscription ?: params.id)

            Date newStartDate
            Date newEndDate

            use(TimeCategory) {
                result.newStartDate = baseSub.startDate + 1.year
                result.newEndDate = baseSub.endDate + 1.year
            }

            if (params.workFlowPart == '3') {

                def newSubConsortia = Subscription.get(params.newSubscription)

                def subMembers = []

                params.list('selectedSubs').each { it ->
                    subMembers << Long.valueOf(it)
                }


                subMembers.each { sub ->

                    def subMember = Subscription.findById(sub)

                    //ChildSub Exisit
                    if (!Subscription.findAllByPreviousSubscription(subMember)) {

                        /* Subscription.executeQuery("select s from Subscription as s join s.orgRelations as sor where s.instanceOf = ? and sor.org.id = ?",
                            [result.subscriptionInstance, it.id])*/

                        def newSubscription = new Subscription(
                                type: subMember.type,
                                status: newSubConsortia.status,
                                name: subMember.name,
                                startDate: newSubConsortia.startDate,
                                endDate: newSubConsortia.endDate,
                                manualRenewalDate: subMember.manualRenewalDate,
                                /* manualCancellationDate: result.subscriptionInstance.manualCancellationDate, */
                                identifier: java.util.UUID.randomUUID().toString(),
                                instanceOf: newSubConsortia?.id,
                                previousSubscription: subMember?.id,
                                isSlaved: subMember.isSlaved,
                                isPublic: subMember.isPublic,
                                impId: java.util.UUID.randomUUID().toString(),
                                owner: newSubConsortia.owner?.id ? subMember.owner?.id : null,
                                resource: newSubConsortia.resource ?: null,
                                form: newSubConsortia.form ?: null
                        )
                        newSubscription.save(flush: true)


                        if (subMember.customProperties) {
                            //customProperties
                            for (prop in subMember.customProperties) {
                                def copiedProp = new SubscriptionCustomProperty(type: prop.type, owner: newSubscription)
                                copiedProp = prop.copyInto(copiedProp)
                                copiedProp.save(flush: true)
                                //newSubscription.addToCustomProperties(copiedProp) // ERROR Hibernate: Found two representations of same collection
                            }
                        }
                        if (subMember.privateProperties) {
                            //privatProperties
                            def contextOrg = contextService.getOrg()

                            subMember.privateProperties?.each { prop ->
                                if (prop.type?.tenant?.id == contextOrg?.id) {
                                    def copiedProp = new SubscriptionPrivateProperty(type: prop.type, owner: newSubscription)
                                    copiedProp = prop.copyInto(copiedProp)
                                    copiedProp.save(flush: true)
                                    //newSubscription.addToPrivateProperties(copiedProp)  // ERROR Hibernate: Found two representations of same collection
                                }
                            }
                        }

                        if (subMember.packages && newSubConsortia.packages) {
                            //Package
                            subMember.packages?.each { pkg ->
                                SubscriptionPackage newSubscriptionPackage = new SubscriptionPackage()
                                InvokerHelper.setProperties(newSubscriptionPackage, pkg.properties)
                                newSubscriptionPackage.subscription = newSubscription
                                newSubscriptionPackage.save(flush: true)
                            }
                        }
                        if (subMember.issueEntitlements && newSubConsortia.issueEntitlements) {

                            subMember.issueEntitlements?.each { ie ->

                                if (ie.status != RefdataValue.getByValueAndCategory('Deleted', 'Entitlement Issue Status')) {
                                    def ieProperties = ie.properties
                                    ieProperties.globalUID = null

                                    IssueEntitlement newIssueEntitlement = new IssueEntitlement()
                                    InvokerHelper.setProperties(newIssueEntitlement, ieProperties)
                                    newIssueEntitlement.subscription = newSubscription
                                    newIssueEntitlement.save(flush: true)
                                }
                            }

                        }

                        //OrgRole
                        subMember.orgRelations?.each { or ->
                            if ((or.org?.id == contextService.getOrg()?.id) || (or.roleType.value in ['Subscriber', 'Subscriber_Consortial']) || (newSubConsortia.orgRelations.size() >= 1)) {
                                OrgRole newOrgRole = new OrgRole()
                                InvokerHelper.setProperties(newOrgRole, or.properties)
                                newOrgRole.sub = newSubscription
                                newOrgRole.save(flush: true)
                            }
                        }



                        if (subMember.prsLinks && newSubConsortia.prsLinks) {
                            //PersonRole
                            subMember.prsLinks?.each { prsLink ->
                                PersonRole newPersonRole = new PersonRole()
                                InvokerHelper.setProperties(newPersonRole, prsLink.properties)
                                newPersonRole.sub = newSubscription
                                newPersonRole.save(flush: true)
                            }
                        }
                    }

                }


                redirect controller: 'subscriptionDetails', action: 'show', params: [id: newSubConsortia?.id]

            }

            if (params.workFlowPart == '2') {
                def newSub2 = Subscription.get(params.newSubscription)

                //Copy Docs
                def toCopyDocs = []
                params.list('subscription.takeDocs').each { doc ->

                    toCopyDocs << Long.valueOf(doc)
                }

                //Copy Announcements
                def toCopyAnnouncements = []
                params.list('subscription.takeAnnouncements').each { announcement ->

                    toCopyAnnouncements << Long.valueOf(announcement)
                }
                if (newSub2.documents.size() == 0) {
                    baseSub.documents?.each { dctx ->

                        //Copy Docs
                        if (dctx.id in toCopyDocs) {
                            if (((dctx.owner?.contentType == 1) || (dctx.owner?.contentType == 3)) && (dctx.status?.value != 'Deleted')) {

                                Doc newDoc = new Doc()
                                InvokerHelper.setProperties(newDoc, dctx.owner.properties)
                                newDoc.save(flush: true)

                                DocContext newDocContext = new DocContext()
                                InvokerHelper.setProperties(newDocContext, dctx.properties)
                                newDocContext.subscription = newSub2
                                newDocContext.owner = newDoc
                                newDocContext.save(flush: true)
                            }
                        }
                        //Copy Announcements
                        if (dctx.id in toCopyAnnouncements) {
                            if ((dctx.owner?.contentType == com.k_int.kbplus.Doc.CONTENT_TYPE_STRING) && !(dctx.domain) && (dctx.status?.value != 'Deleted')) {
                                Doc newDoc = new Doc()
                                InvokerHelper.setProperties(newDoc, dctx.owner.properties)
                                newDoc.save(flush: true)

                                DocContext newDocContext = new DocContext()
                                InvokerHelper.setProperties(newDocContext, dctx.properties)
                                newDocContext.subscription = newSub2
                                newDocContext.owner = newDoc
                                newDocContext.save(flush: true)
                            }
                        }
                    }
                }
                if(!Task.findAllBySubscription(newSub2)) {
                    //Copy Tasks
                    params.list('subscription.takeTasks').each { tsk ->

                        def task = Task.findBySubscriptionAndId(baseSub, Long.valueOf(tsk))
                        if (task) {
                            if (task.status != RefdataValue.loc('Task Status', [en: 'Done', de: 'Erledigt'])) {
                                Task newTask = new Task()
                                InvokerHelper.setProperties(newTask, task.properties)
                                newTask.subscription = newSub2
                                newTask.save(flush: true)
                            }

                        }
                    }
                }
                params.workFlowPart = 3
                params.workFlowPartNext = 4
                result.newSub = newSub2

                def validSubChilds = Subscription.findAllByInstanceOfAndStatusNotEqual(
                        baseSub,
                        RefdataValue.getByValueAndCategory('Deleted', 'Subscription Status')
                )

                result.validSubChilds = validSubChilds.sort { a, b ->
                    def sa = a.getSubscriber()
                    def sb = b.getSubscriber()
                    (sa.sortname ?: sa.name).compareTo((sb.sortname ?: sb.name))
                }

            }


            if (params.workFlowPart == '1') {

                if (params.baseSubscription) {

                    if (Subscription.findAllByPreviousSubscription(baseSub)) {
                        flash.error = message(code: 'subscription.renewSubExist', default: 'The Subscription is already renewed!')
                    } else {


                        def newSub = new Subscription(
                                name: baseSub.name,
                                startDate: result.newStartDate,
                                endDate: result.newEndDate,
                                previousSubscription: baseSub.id,
                                identifier: java.util.UUID.randomUUID().toString(),
                                isPublic: baseSub.isPublic,
                                isSlaved: baseSub.isSlaved,
                                type: baseSub.type,
                                status: RefdataValue.loc('Subscription Status', [en: 'Intended', de: 'Geplant']),
                                resource: baseSub.resource ?: null,
                                form: baseSub.form ?: null
                        )
                        if (!newSub.save(flush: true)) {
                            log.error("Problem saving subscription ${newSub.errors}");
                            return newSub
                        } else {
                            log.debug("Save ok");
                            //Copy References
                                //OrgRole
                                baseSub.orgRelations?.each { or ->

                                    if ((or.org?.id == contextService.getOrg()?.id) || (or.roleType.value in ['Subscriber', 'Subscriber_Consortial']) || params.subscription.takeLinks) {
                                        OrgRole newOrgRole = new OrgRole()
                                        InvokerHelper.setProperties(newOrgRole, or.properties)
                                        newOrgRole.sub = newSub
                                        newOrgRole.save(flush: true)
                                    }
                                }
                            if(params.subscription.takeLinks){
                                //Package
                                baseSub.packages?.each { pkg ->
                                    SubscriptionPackage newSubscriptionPackage = new SubscriptionPackage()
                                    InvokerHelper.setProperties(newSubscriptionPackage, pkg.properties)
                                    newSubscriptionPackage.subscription = newSub
                                    newSubscriptionPackage.save(flush: true)
                                }
                                //License
                                newSub.owner = baseSub.owner ?: null
                                newSub.save(flush: true)
                            }

                            if (params.subscription.takeEntitlements) {

                                baseSub.issueEntitlements.each { ie ->

                                    if (ie.status != RefdataValue.getByValueAndCategory('Deleted', 'Entitlement Issue Status')) {
                                        def properties = ie.properties
                                        properties.globalUID = null

                                        IssueEntitlement newIssueEntitlement = new IssueEntitlement()
                                        InvokerHelper.setProperties(newIssueEntitlement, properties)
                                        newIssueEntitlement.subscription = newSub
                                        newIssueEntitlement.save(flush: true)
                                    }
                                }

                            }


                            if (params.subscription.takeCustomProperties) {
                                //customProperties
                                for (prop in baseSub.customProperties) {
                                    def copiedProp = new SubscriptionCustomProperty(type: prop.type, owner: newSub)
                                    copiedProp = prop.copyInto(copiedProp)
                                    copiedProp.save(flush: true)
                                    //newSub.addToCustomProperties(copiedProp) // ERROR Hibernate: Found two representations of same collection
                                }
                            }
                            if (params.subscription.takePrivateProperties) {
                                //privatProperties
                                def contextOrg = contextService.getOrg()

                                baseSub.privateProperties.each { prop ->
                                    if (prop.type?.tenant?.id == contextOrg?.id) {
                                        def copiedProp = new SubscriptionPrivateProperty(type: prop.type, owner: newSub)
                                        copiedProp = prop.copyInto(copiedProp)
                                        copiedProp.save(flush: true)
                                        //newSub.addToPrivateProperties(copiedProp)  // ERROR Hibernate: Found two representations of same collection
                                    }
                                }
                            }

                            params.workFlowPart = 2
                            params.workFlowPartNext = 3
                            result.newSub = newSub
                        }
                    }
                }
            }


            result.navPrevSubscription = result.subscriptionInstance.previousSubscription
            result.navNextSubscription = Subscription.findByPreviousSubscription(result.subscriptionInstance)

            // tasks
            def contextOrg = contextService.getOrg()
            result.tasks = taskService.getTasksByResponsiblesAndObject(result.user, contextOrg, result.subscriptionInstance)

            result.contextOrg = contextOrg
            // restrict visible for templates/links/orgLinksAsList
            result.visibleOrgRelations = []
            result.subscriptionInstance.orgRelations?.each { or ->
                if (!(or.org?.id == contextService.getOrg()?.id) && !(or.roleType.value in ['Subscriber', 'Subscriber_Consortial'])) {
                    result.visibleOrgRelations << or
                }
            }
            result.visibleOrgRelations.sort { it.org.sortname }


            result.modalPrsLinkRole = RefdataValue.findByValue('Specific subscription editor')
            result.modalVisiblePersons = addressbookService.getPrivatePersonsByTenant(contextService.getOrg())

            result.visiblePrsLinks = []

            result.subscriptionInstance.prsLinks.each { pl ->
                if (!result.visiblePrsLinks.contains(pl.prs)) {
                    if (pl.prs.isPublic?.value != 'No') {
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

            result.workFlowPart = params.workFlowPart ?: 1
            result.workFlowPartNext = params.workFlowPartNext ?: 2

        }
            result

    }

    def copySubscription(){

        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        // tasks
        def contextOrg  = contextService.getOrg()
        result.tasks    = taskService.getTasksByResponsiblesAndObject(result.user, contextOrg, result.subscriptionInstance)
        def preCon      = taskService.getPreconditions(contextOrg)
        result << preCon


        result.visibleOrgRelations = []
        result.subscriptionInstance.orgRelations?.each { or ->
            if (!(or.org?.id == contextService.getOrg()?.id) && !(or.roleType.value in ['Subscriber', 'Subscriber_Consortial'])) {
                result.visibleOrgRelations << or
            }
        }
        result.visibleOrgRelations.sort{it.org.sortname}

        // -- private properties

        result.authorizedOrgs = result.user?.authorizedOrgs
        result.contextOrg = contextService.getOrg()

        // create mandatory OrgPrivateProperties if not existing

        def mandatories = []
        result.user?.authorizedOrgs?.each { org ->
            def ppd = PropertyDefinition.findAllByDescrAndMandatoryAndTenant("Subscription Property", true, org)
            if (ppd) {
                mandatories << ppd
            }
        }
        mandatories.flatten().each { pd ->
            if (!SubscriptionPrivateProperty.findWhere(owner: result.subscriptionInstance, type: pd)) {
                def newProp = PropertyDefinition.createGenericProperty(PropertyDefinition.PRIVATE_PROPERTY, result.subscriptionInstance, pd)

                if (newProp.hasErrors()) {
                    log.error(newProp.errors)
                } else {
                    log.debug("New subscription private property created via mandatory: " + newProp.type.name)
                }
            }
        }

        // -- private properties

        result.modalPrsLinkRole = RefdataValue.findByValue('Specific subscription editor')
        result.modalVisiblePersons = addressbookService.getPrivatePersonsByTenant(contextService.getOrg())

        result.visiblePrsLinks = []

        result.subscriptionInstance.prsLinks.each { pl ->
            if (! result.visiblePrsLinks.contains(pl.prs)) {
                if (pl.prs.isPublic?.value != 'No') {
                    result.visiblePrsLinks << pl
                }
                else {
                    // nasty lazy loading fix
                    result.user.authorizedOrgs.each{ ao ->
                        if (ao.getId() == pl.prs.tenant.getId()) {
                            result.visiblePrsLinks << pl
                        }
                    }
                }
            }
        }

        result

    }

    def processcopySubscription(){

        params.id = params.baseSubscription
        def result = setResultGenericsAndCheckAccess(AccessService.CHECK_VIEW)
        if (!result) {
            response.sendError(401); return
        }

        def baseSubscription = com.k_int.kbplus.Subscription.get(params.baseSubscription)

        if (baseSubscription) {

            def sub_name = params.sub_name ?: "Kopie von ${baseSubscription.name}"

            def newSubscriptionInstance = new Subscription(
                    name: sub_name,
                    status: baseSubscription.status,
                    type: baseSubscription.type,
                    identifier: java.util.UUID.randomUUID().toString(),
                    isPublic: baseSubscription.isPublic,
                    isSlaved: baseSubscription.isSlaved,
                    startDate: params.subscription.copyDates ? baseSubscription?.startDate : null,
                    endDate: params.subscription.copyDates ? baseSubscription?.endDate : null,
                    resource: baseSubscription.resource ?: null,
                    form: baseSubscription.form ?: null,
            )


            if (!newSubscriptionInstance.save(flush: true)) {
                log.error("Problem saving subscription ${newSubscriptionInstance.errors}");
                return newSubscriptionInstance
            } else {
                log.debug("Save ok");

                baseSubscription.documents?.each { dctx ->

                    //Copy Docs
                    if (params.subscription.copyDocs) {
                        if (((dctx.owner?.contentType == 1) || (dctx.owner?.contentType == 3)) && (dctx.status?.value != 'Deleted')) {
                            Doc clonedContents = new Doc(
                                    blobContent: dctx.owner.blobContent,
                                    status: dctx.owner.status,
                                    type: dctx.owner.type,
                                    alert: dctx.owner.alert,
                                    content: dctx.owner.content,
                                    uuid: dctx.owner.uuid,
                                    contentType: dctx.owner.contentType,
                                    title: dctx.owner.title,
                                    creator: dctx.owner.creator,
                                    filename: dctx.owner.filename,
                                    mimeType: dctx.owner.mimeType,
                                    user: dctx.owner.user,
                                    migrated: dctx.owner.migrated
                            ).save()

                            DocContext ndc = new DocContext(
                                    owner: clonedContents,
                                    subscription: newSubscriptionInstance,
                                    domain: dctx.domain,
                                    status: dctx.status,
                                    doctype: dctx.doctype
                            ).save()
                        }
                    }
                    //Copy Announcements
                    if (params.subscription.copyAnnouncements) {
                        if ((dctx.owner?.contentType == com.k_int.kbplus.Doc.CONTENT_TYPE_STRING) && !(dctx.domain) && (dctx.status?.value != 'Deleted')) {
                            Doc clonedContents = new Doc(
                                    blobContent: dctx.owner.blobContent,
                                    status: dctx.owner.status,
                                    type: dctx.owner.type,
                                    alert: dctx.owner.alert,
                                    content: dctx.owner.content,
                                    uuid: dctx.owner.uuid,
                                    contentType: dctx.owner.contentType,
                                    title: dctx.owner.title,
                                    creator: dctx.owner.creator,
                                    filename: dctx.owner.filename,
                                    mimeType: dctx.owner.mimeType,
                                    user: dctx.owner.user,
                                    migrated: dctx.owner.migrated
                            ).save()

                            DocContext ndc = new DocContext(
                                    owner: clonedContents,
                                    subscription: newSubscriptionInstance,
                                    domain: dctx.domain,
                                    status: dctx.status,
                                    doctype: dctx.doctype
                            ).save()
                        }
                    }
                }
                //Copy Tasks
                if (params.subscription.copyTasks) {

                    Task.findAllBySubscription(baseSubscription).each { task ->

                        Task newTask = new Task()
                        InvokerHelper.setProperties(newTask, task.properties)
                        newTask.subscription = newSubscriptionInstance
                        newTask.save(flush: true)
                    }

                }
                //Copy References
                baseSubscription.orgRelations?.each { or ->
                    if ((or.org?.id == contextService.getOrg()?.id) || (or.roleType.value in ['Subscriber', 'Subscriber_Consortial']) || (params.subscription.copyLinks)) {
                        OrgRole newOrgRole = new OrgRole()
                        InvokerHelper.setProperties(newOrgRole, or.properties)
                        newOrgRole.sub = newSubscriptionInstance
                        newOrgRole.save(flush: true)

                    }

                }


                    //Copy Package
                    if(params.subscription.copyPackages){
                        baseSubscription.packages?.each { pkg ->
                            SubscriptionPackage newSubscriptionPackage = new SubscriptionPackage()
                            InvokerHelper.setProperties(newSubscriptionPackage, pkg.properties)
                            newSubscriptionPackage.subscription = newSubscriptionInstance
                            newSubscriptionPackage.save(flush: true)
                        }
                    }
                    //Copy License
                    if(params.subscription.copyLicense) {
                        newSubscriptionInstance.owner = baseSubscription.owner ?: null
                        newSubscriptionInstance.save(flush: true)
                    }
                    //Copy InstanceOf
                    if(params.subscription.copylinktoSubscription) {
                        newSubscriptionInstance.instanceOf = baseSubscription?.instanceOf ?: null
                    }

                if (params.subscription.copyEntitlements) {

                    baseSubscription.issueEntitlements.each { ie ->

                        if (ie.status != RefdataValue.getByValueAndCategory('Deleted', 'Entitlement Issue Status')) {
                            def properties = ie.properties
                            properties.globalUID = null

                            IssueEntitlement newIssueEntitlement = new IssueEntitlement()
                            InvokerHelper.setProperties(newIssueEntitlement, properties)
                            newIssueEntitlement.subscription = newSubscriptionInstance
                            newIssueEntitlement.save(flush: true)
                        }
                    }

                }

                if (params.subscription.copyCustomProperties) {
                    //customProperties
                    for (prop in baseSubscription.customProperties) {
                        def copiedProp = new SubscriptionCustomProperty(type: prop.type, owner: newSubscriptionInstance)
                        copiedProp = prop.copyInto(copiedProp)
                        copiedProp.save(flush: true)
                        //newSubscriptionInstance.addToCustomProperties(copiedProp) // ERROR Hibernate: Found two representations of same collection
                    }
                }
                if (params.subscription.copyPrivateProperties) {
                    //privatProperties
                    def contextOrg = contextService.getOrg()

                    baseSubscription.privateProperties.each { prop ->
                        if (prop.type?.tenant?.id == contextOrg?.id) {
                            def copiedProp = new SubscriptionPrivateProperty(type: prop.type, owner: newSubscriptionInstance)
                            copiedProp = prop.copyInto(copiedProp)
                            copiedProp.save(flush: true)
                            //newSubscriptionInstance.addToPrivateProperties(copiedProp)  // ERROR Hibernate: Found two representations of same collection
                        }
                    }
                }


                redirect controller: 'subscriptionDetails', action: 'show', params: [id: newSubscriptionInstance.id]
            }
        }

    }

    private LinkedHashMap setResultGenericsAndCheckAccess(checkOption) {
        def result                  = [:]
        result.user                 = User.get(springSecurityService.principal.id)
        result.subscriptionInstance = Subscription.get(params.id)
        result.subscription         = Subscription.get(params.id)
        result.institution          = result.subscription?.subscriber

        result.showConsortiaFunctions = showConsortiaFunctions(result.subscription)

        if (checkOption in [AccessService.CHECK_VIEW, AccessService.CHECK_VIEW_AND_EDIT]) {
            if (! result.subscriptionInstance?.isVisibleBy(result.user)) {
                log.debug( "--- NOT VISIBLE ---")
                return null
            }
        }
        result.editable = result.subscriptionInstance?.isEditableBy(result.user)

        if (checkOption in [AccessService.CHECK_EDIT, AccessService.CHECK_VIEW_AND_EDIT]) {
            if (! result.editable) {
                log.debug( "--- NOT EDITABLE ---")
                return null
            }
        }

        result
    }

    def showConsortiaFunctions (def subscription) {

        return ((subscription?.getConsortia()?.id == contextService.getOrg()?.id) && ! subscription.instanceOf)
    }
    private def exportOrg(orgs, message, addHigherEducationTitles) {
        try {
            def titles = [
                    'Name', g.message(code: 'org.shortname.label'), g.message(code: 'org.sortname.label')]

            def orgSector = RefdataValue.getByValueAndCategory('Higher Education','OrgSector')
            def orgRoleType = RefdataValue.getByValueAndCategory('Provider','OrgRoleType')


            if(addHigherEducationTitles)
            {
                titles.add(g.message(code: 'org.libraryType.label'))
                titles.add(g.message(code: 'org.libraryNetwork.label'))
                titles.add(g.message(code: 'org.funderType.label'))
                titles.add(g.message(code: 'org.federalState.label'))
                titles.add(g.message(code: 'org.country.label'))
            }

            titles.add(g.message(code: 'subscription.details.startDate'))
            titles.add(g.message(code: 'subscription.details.endDate'))
            titles.add(g.message(code: 'subscription.details.status'))

            def propList =
                    PropertyDefinition.findAll( "from PropertyDefinition as pd where pd.descr in :defList and pd.tenant is null", [
                            defList: [PropertyDefinition.ORG_PROP],
                    ] // public properties
                    ) +
                            PropertyDefinition.findAll( "from PropertyDefinition as pd where pd.descr in :defList and pd.tenant = :tenant", [
                                    defList: [PropertyDefinition.ORG_PROP],
                                    tenant: contextService.getOrg()
                            ]// private properties
                            )

            propList.sort { a, b -> a.name.compareToIgnoreCase b.name}

            propList.each {
                titles.add(it.name)
            }

            def sdf = new SimpleDateFormat(g.message(code:'default.date.format.notime', default:'yyyy-MM-dd'));
            def datetoday = sdf.format(new Date(System.currentTimeMillis()))

            HSSFWorkbook wb = new HSSFWorkbook();

            HSSFSheet sheet = wb.createSheet(message);

            //the following three statements are required only for HSSF
            sheet.setAutobreaks(true);

            //the header row: centered text in 48pt font
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(16.75f);
            titles.eachWithIndex { titlesName, index ->
                Cell cell = headerRow.createCell(index);
                cell.setCellValue(titlesName);
            }

            //freeze the first row
            sheet.createFreezePane(0, 1);

            Row row;
            Cell cell;
            int rownum = 1;

            orgs.sort{it.name}
            orgs.each{  org ->
                int cellnum = 0;
                row = sheet.createRow(rownum);

                //Name
                cell = row.createCell(cellnum++);
                cell.setCellValue(new HSSFRichTextString(org.name));

                //Shortname
                cell = row.createCell(cellnum++);
                cell.setCellValue(new HSSFRichTextString(org.shortname));

                //Sortname
                cell = row.createCell(cellnum++);
                cell.setCellValue(new HSSFRichTextString(org.sortname));


                if(addHigherEducationTitles) {

                    //libraryType
                    cell = row.createCell(cellnum++);
                    cell.setCellValue(new HSSFRichTextString(org.libraryType?.getI10n('value') ?: ' '));

                    //libraryNetwork
                    cell = row.createCell(cellnum++);
                    cell.setCellValue(new HSSFRichTextString(org.libraryNetwork?.getI10n('value') ?: ' '));

                    //funderType
                    cell = row.createCell(cellnum++);
                    cell.setCellValue(new HSSFRichTextString(org.funderType?.getI10n('value') ?: ' '));

                    //federalState
                    cell = row.createCell(cellnum++);
                    cell.setCellValue(new HSSFRichTextString(org.federalState?.getI10n('value') ?: ' '));

                    //country
                    cell = row.createCell(cellnum++);
                    cell.setCellValue(new HSSFRichTextString(org.country?.getI10n('value') ?: ' '));
                }

                cell = row.createCell(cellnum++);
                cell.setCellValue(new HSSFRichTextString(org.startDate ?: ' '));

                cell = row.createCell(cellnum++);
                cell.setCellValue(new HSSFRichTextString(org.endDate ?: ' '));

                cell = row.createCell(cellnum++);
                cell.setCellValue(new HSSFRichTextString(org.status?.getI10n('value') ?: ' '));


                propList.each { pd ->
                    def value = ''
                    org.customProperties.each{ prop ->
                        if(prop.type.descr == pd.descr && prop.type == pd)
                        {
                            if(prop.type.type == Integer.toString()){
                                value = prop.intValue.toString()
                            }
                            else if (prop.type.type == String.toString()){
                                value = prop.stringValue
                            }
                            else if (prop.type.type == BigDecimal.toString()){
                                value = prop.decValue.toString()
                            }
                            else if (prop.type.type == Date.toString()){
                                value = prop.dateValue.toString()
                            }
                            else if (prop.type.type == RefdataValue.toString()) {
                                value = prop.refValue?.getI10n('value') ?: ''
                            }

                        }
                    }

                    org.privateProperties.each{ prop ->
                        if(prop.type.descr == pd.descr && prop.type == pd)
                        {
                            if(prop.type.type == Integer.toString()){
                                value = prop.intValue.toString()
                            }
                            else if (prop.type.type == String.toString()){
                                value = prop.stringValue
                            }
                            else if (prop.type.type == BigDecimal.toString()){
                                value = prop.decValue.toString()
                            }
                            else if (prop.type.type == Date.toString()){
                                value = prop.dateValue.toString()
                            }
                            else if (prop.type.type == RefdataValue.toString()) {
                                value = prop.refValue?.getI10n('value') ?: ''
                            }

                        }
                    }
                    cell = row.createCell(cellnum++);
                    cell.setCellValue(new HSSFRichTextString(value));
                }

                rownum++
            }

            for (int i = 0; i < 22; i++) {
                sheet.autoSizeColumn(i);
            }
            // Write the output to a file
            String file = message+"_${datetoday}.xls";
            //if(wb instanceof XSSFWorkbook) file += "x";

            response.setHeader "Content-disposition", "attachment; filename=\"${file}\""
            // response.contentType = 'application/xls'
            response.contentType = 'application/vnd.ms-excel'
            wb.write(response.outputStream)
            response.outputStream.flush()

        }
        catch ( Exception e ) {
            log.error("Problem",e);
            response.sendError(500)
        }
    }
}