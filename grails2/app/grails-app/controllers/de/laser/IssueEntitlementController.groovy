package de.laser


import de.laser.properties.PlatformProperty
import com.k_int.kbplus.auth.User
import de.laser.properties.PropertyDefinition
import de.laser.controller.AbstractDebugController
import de.laser.helper.DateUtil
import de.laser.helper.DebugAnnotation
import de.laser.helper.RDStore
import grails.plugin.springsecurity.annotation.Secured
import org.springframework.dao.DataIntegrityViolationException

import java.text.SimpleDateFormat

@Secured(['IS_AUTHENTICATED_FULLY'])
class IssueEntitlementController extends AbstractDebugController {

    def factService
    def contextService

   static allowedMethods = [create: ['GET', 'POST'], edit: ['GET', 'POST'], delete: 'POST']
   def springSecurityService

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def index() {
        redirect action: 'list', params: params
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def list() {
        params.max = params.max ?: ((User) springSecurityService.getCurrentUser())?.getDefaultPageSize()
        [issueEntitlementInstanceList: IssueEntitlement.list(params), issueEntitlementInstanceTotal: IssueEntitlement.count()]
    }

    @DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def create() {
        redirect controller: 'issueEntitlement', action: 'show', params: params
        return // ----- deprecated
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def show() {
      Map<String, Object> result = [:]

      result.user = User.get(springSecurityService.principal.id)
      result.issueEntitlementInstance = IssueEntitlement.get(params.id)

      params.max = Math.min(params.max ? params.int('max') : 10, 100)
      def paginate_after = params.paginate_after ?: 19;
      result.max = params.max
      result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

      result.editable = result.issueEntitlementInstance.subscription.isEditableBy(result.user)

      // Get usage statistics
      def title_id = result.issueEntitlementInstance.tipp.title?.id
      def org = result.issueEntitlementInstance.subscription.getSubscriber() // TODO
      def supplier =  result.issueEntitlementInstance.tipp.platform
      def supplier_id = supplier?.id

      if (title_id != null &&
           org != null &&
           supplier_id != null ) {
          def platform = PlatformProperty.findByOwnerAndType(Platform.get(supplier_id),
              PropertyDefinition.getByNameAndDescr('NatStat Supplier ID', PropertyDefinition.PLA_PROP))
          result.natStatSupplierId = platform?.stringValue ?: null
          def fsresult = factService.generateUsageData(org.id, supplier_id, result.issueEntitlementInstance.subscription, title_id)
          def fsLicenseResult = factService.generateUsageDataForSubscriptionPeriod(org.id, supplier_id, result.issueEntitlementInstance.subscription, title_id)
          result.institutional_usage_identifier = OrgSetting.get(org, OrgSetting.KEYS.NATSTAT_SERVER_REQUESTOR_ID)
          if (result.institutional_usage_identifier instanceof OrgSetting && fsresult.usage) {
              result.statsWibid = org.getIdentifierByType('wibid')?.value
              result.usageMode = org.hasPerm("ORG_CONSORTIUM") ? 'package' : 'institution'
              result.usage = fsresult?.usage
              result.x_axis_labels = fsresult?.x_axis_labels
              result.y_axis_labels = fsresult?.y_axis_labels
              if (fsLicenseResult.usage) {
                  result.lusage = fsLicenseResult?.usage
                  result.l_x_axis_labels = fsLicenseResult?.x_axis_labels
                  result.l_y_axis_labels = fsLicenseResult?.y_axis_labels
              }
          }
      }

      if (!result.issueEntitlementInstance) {
        flash.message = message(code: 'default.not.found.message', args: [message(code: 'issueEntitlement.label'), params.id])
        redirect action: 'list'
        return
      }

      String base_qry = "from TitleInstancePackagePlatform as tipp where tipp.title = ? and tipp.status != ?"
      def qry_params = [result.issueEntitlementInstance.tipp.title, RDStore.TIPP_STATUS_DELETED]

      if ( params.filter ) {
        base_qry += " and lower(tipp.pkg.name) like ? "
        qry_params.add("%${params.filter.trim().toLowerCase()}%")
      }

      if ( params.endsAfter && params.endsAfter.length() > 0 ) {
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        Date d = sdf.parse(params.endsAfter)
        base_qry += " and (select max(tc.endDate) from TIPPCoverage tc where tc.tipp = tipp) >= ?"
        qry_params.add(d)
      }

      if ( params.startsBefore && params.startsBefore.length() > 0 ) {
          SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
          Date d = sdf.parse(params.startsBefore)
        base_qry += " and (select min(tc.startDate) from TIPPCoverage tc where tc.tipp = tipp) <= ?"
        qry_params.add(d)
      }

      if ( ( params.sort != null ) && ( params.sort.length() > 0 ) ) {
        base_qry += " order by lower(${params.sort}) ${params.order}"
      }
      else {
        base_qry += " order by lower(tipp.title.title) asc"
      }

      // log.debug("Base qry: ${base_qry}, params: ${qry_params}, result:${result}");
      // result.tippList = TitleInstancePackagePlatform.executeQuery("select tipp "+base_qry, qry_params, [max:result.max, offset:result.offset]);
      // DMs report that this list is limited to 10
      result.tippList = TitleInstancePackagePlatform.executeQuery("select tipp "+base_qry, qry_params, [max:300, offset:0]);
      result.num_tipp_rows = TitleInstancePackagePlatform.executeQuery("select tipp.id "+base_qry, qry_params ).size()

      result

    }

    @DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def edit() {
        redirect controller: 'issueEntitlement', action: 'show', params: params
        return // ----- deprecated
    }

    @DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
  def delete() {
        IssueEntitlement issueEntitlementInstance = IssueEntitlement.get(params.id)
    if (!issueEntitlementInstance) {
    flash.message = message(code: 'default.not.found.message', args: [message(code: 'issueEntitlement.label'), params.id])
        redirect action: 'list'
        return
    }

    try {
      issueEntitlementInstance.delete(flush: true)
      flash.message = message(code: 'default.deleted.message', args: [message(code: 'issueEntitlement.label'), params.id])
      redirect action: 'list'
    }
    catch (DataIntegrityViolationException e) {
      flash.message = message(code: 'default.not.deleted.message', args: [message(code: 'issueEntitlement.label'), params.id])
      redirect action: 'show', id: params.id
    }
  }
}
