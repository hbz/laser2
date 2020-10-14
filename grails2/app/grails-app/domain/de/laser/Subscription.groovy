package de.laser

import com.k_int.kbplus.PendingChangeService
import com.k_int.kbplus.auth.Role
import com.k_int.kbplus.auth.User
import de.laser.finance.CostItem
import de.laser.properties.PropertyDefinitionGroup
import de.laser.properties.PropertyDefinitionGroupBinding
import de.laser.oap.OrgAccessPoint
import de.laser.base.AbstractBaseWithCalculatedLastUpdated
import de.laser.helper.DateUtil
import de.laser.helper.RDConstants
import de.laser.helper.RDStore
import de.laser.helper.RefdataAnnotation
import de.laser.interfaces.AuditableSupport
import de.laser.interfaces.CalculatedType
import de.laser.interfaces.Permissions
import de.laser.interfaces.ShareSupport
import de.laser.properties.SubscriptionProperty
import de.laser.traits.ShareableTrait
import grails.util.Holders
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.springframework.context.i18n.LocaleContextHolder

import javax.persistence.Transient
import java.text.SimpleDateFormat

class Subscription extends AbstractBaseWithCalculatedLastUpdated
        implements AuditableSupport, CalculatedType, Permissions, ShareSupport {

    def grailsApplication
    def contextService
    def messageSource
    def pendingChangeService
    def changeNotificationService
    def springSecurityService
    def accessService
    def propertyService
    def deletionService
    def subscriptionService
    def auditService
    def genericOIDService

    static auditable            = [ ignore: ['version', 'lastUpdated', 'lastUpdatedCascading', 'pendingChanges'] ]
    static controlledProperties = [ 'name', 'startDate', 'endDate', 'manualCancellationDate', 'status', 'type', 'kind', 'form', 'resource', 'isPublicForApi', 'hasPerpetualAccess' ]

    static Log static_logger = LogFactory.getLog(Subscription)

    @RefdataAnnotation(cat = RDConstants.SUBSCRIPTION_STATUS)
    RefdataValue status

    @RefdataAnnotation(cat = RDConstants.SUBSCRIPTION_TYPE)
    RefdataValue type

    @RefdataAnnotation(cat = RDConstants.SUBSCRIPTION_KIND)
    RefdataValue kind

    @RefdataAnnotation(cat = RDConstants.SUBSCRIPTION_FORM)
    RefdataValue form

    @RefdataAnnotation(cat = RDConstants.SUBSCRIPTION_RESOURCE)
    RefdataValue resource

    // If a subscription is slaved then any changes to instanceOf will automatically be applied to this subscription
    boolean isSlaved = false
	boolean isPublicForApi = false
    boolean hasPerpetualAccess = false
    boolean isMultiYear = false

  String name
  String identifier
  Date startDate
  Date endDate
  Date manualRenewalDate
  Date manualCancellationDate
  String cancellationAllowances

  //Only for Consortia: ERMS-2098
  String comment

  Subscription instanceOf
  //Subscription previousSubscription //deleted as ERMS-800
  // If a subscription is administrative, subscription members will not see it resp. there is a toggle which en-/disables visibility
  boolean administrative = false

    String noticePeriod

    Date dateCreated
    Date lastUpdated
    Date lastUpdatedCascading

  SortedSet issueEntitlements
  SortedSet packages

  static hasMany = [
          ids                 : Identifier,
          packages            : SubscriptionPackage,
          issueEntitlements   : IssueEntitlement,
          documents           : DocContext,
          orgRelations        : OrgRole,
          prsLinks            : PersonRole,
          derivedSubscriptions: Subscription,
          pendingChanges      : PendingChange,
          propertySet         : SubscriptionProperty,
          //privateProperties: SubscriptionPrivateProperty,
          costItems           : CostItem,
          ieGroups            : IssueEntitlementGroup
  ]

  static mappedBy = [
                      ids: 'sub',
                      packages: 'subscription',
                      issueEntitlements: 'subscription',
                      documents: 'subscription',
                      orgRelations: 'sub',
                      prsLinks: 'sub',
                      derivedSubscriptions: 'instanceOf',
                      pendingChanges: 'subscription',
                      costItems: 'sub',
                      propertySet: 'owner',
                      //privateProperties: 'owner',
                      ]

    static transients = [
            'nameConcatenated', 'isSlavedAsString', 'provider', 'collective', 'multiYearSubscription',
            'currentMultiYearSubscription', 'currentMultiYearSubscriptionNew', 'renewalDate', 'holdingTypes',
            'commaSeperatedPackagesIsilList', 'allocationTerm',
            'subscriber', 'providers', 'agencies', 'consortia'
    ] // mark read-only accessor methods

    static mapping = {
        sort name: 'asc'
        id          column:'sub_id'
        version     column:'sub_version'
        globalUID   column:'sub_guid'
        status      column:'sub_status_rv_fk'
        type        column:'sub_type_rv_fk',        index: 'sub_type_idx'
        kind        column:'sub_kind_rv_fk'
        //owner       column:'sub_owner_license_fk',  index: 'sub_owner_idx'
        form        column:'sub_form_fk'
        resource    column:'sub_resource_fk'
        name        column:'sub_name'
        comment     column: 'sub_comment', type: 'text'
        identifier  column:'sub_identifier'
        startDate   column:'sub_start_date',        index: 'sub_dates_idx'
        endDate     column:'sub_end_date',          index: 'sub_dates_idx'
        manualRenewalDate       column:'sub_manual_renewal_date'
        manualCancellationDate  column:'sub_manual_cancellation_date'
        instanceOf              column:'sub_parent_sub_fk', index:'sub_parent_idx'
        administrative          column:'sub_is_administrative'
        //previousSubscription    column:'sub_previous_subscription_fk' //-> see Links, deleted as ERMS-800
        isSlaved        column:'sub_is_slaved'
        hasPerpetualAccess column: 'sub_has_perpetual_access', defaultValue: false
        isPublicForApi  column:'sub_is_public_for_api', defaultValue: false
        lastUpdatedCascading column: 'sub_last_updated_cascading'

        noticePeriod    column:'sub_notice_period'
        isMultiYear column: 'sub_is_multi_year'
        pendingChanges  sort: 'ts', order: 'asc', batchSize: 10

        ids                 batchSize: 10
        packages            batchSize: 10
        issueEntitlements   batchSize: 10
        documents           batchSize: 10
        orgRelations        batchSize: 10
        prsLinks            batchSize: 10
        derivedSubscriptions    batchSize: 10
        propertySet    batchSize: 10
        //privateProperties   batchSize: 10
        costItems           batchSize: 10
    }

    static constraints = {
        globalUID(nullable:true, blank:false, unique:true, maxSize:255)
        type        (nullable:true)
        kind        (nullable:true)
        //owner(nullable:true, blank:false)
        form        (nullable:true)
        resource    (nullable:true)
        startDate(nullable:true, validator: { val, obj ->
            if(obj.startDate != null && obj.endDate != null) {
                if(obj.startDate > obj.endDate) return ['startDateAfterEndDate']
            }
        })
        endDate(nullable:true, validator: { val, obj ->
            if(obj.startDate != null && obj.endDate != null) {
                if(obj.startDate > obj.endDate) return ['endDateBeforeStartDate']
            }
        })
        manualRenewalDate       (nullable:true)
        manualCancellationDate  (nullable:true)
        instanceOf              (nullable:true)
        comment(nullable: true, blank: true)
        //previousSubscription    (nullable:true) //-> see Links, deleted as ERMS-800
        noticePeriod(nullable:true, blank:true)
        cancellationAllowances(nullable:true, blank:true)
        lastUpdated(nullable: true)
        lastUpdatedCascading (nullable: true)
        isMultiYear(nullable: true)
    }

    @Override
    Collection<String> getLogIncluded() {
        [ 'name', 'startDate', 'endDate', 'manualCancellationDate', 'status', 'type', 'kind', 'form', 'resource', 'isPublicForApi', 'hasPerpetualAccess' ]
    }
    @Override
    Collection<String> getLogExcluded() {
        [ 'version', 'lastUpdated', 'lastUpdatedCascading', 'pendingChanges' ]
    }

    @Override
    def afterDelete() {
        super.afterDeleteHandler()

        deletionService.deleteDocumentFromIndex(this.globalUID)
    }
    @Override
    def afterInsert() {
        super.afterInsertHandler()
    }
    @Override
    def afterUpdate() {
        super.afterUpdateHandler()
    }
    @Override
    def beforeInsert() {
        super.beforeInsertHandler()
    }
    @Override
    def beforeUpdate() {
        Map<String, Object> changes = super.beforeUpdateHandler()
        log.debug ("beforeUpdate() " + changes.toMapString())

        auditService.beforeUpdateHandler(this, changes.oldMap, changes.newMap)
    }
    @Override
    def beforeDelete() {
        super.beforeDeleteHandler()
    }

    @Override
    boolean checkSharePreconditions(ShareableTrait sharedObject) {
        // needed to differentiate OrgRoles
        if (sharedObject instanceof OrgRole) {
            if (showUIShareButton() && sharedObject.roleType.value in ['Provider', 'Agency']) {
                return true
            }
        }
        false
    }

    @Override
    boolean showUIShareButton() {
        _getCalculatedType() in [CalculatedType.TYPE_CONSORTIAL, CalculatedType.TYPE_COLLECTIVE]
    }

    @Override
    void updateShare(ShareableTrait sharedObject) {
        log.debug('updateShare: ' + sharedObject)

        if (sharedObject instanceof DocContext) {
            if (sharedObject.isShared) {
                List<Subscription> newTargets = Subscription.findAllByInstanceOf(this)
                log.debug('found targets: ' + newTargets)

                newTargets.each{ sub ->
                    log.debug('adding for: ' + sub)
                    sharedObject.addShareForTarget_trait(sub)
                }
            }
            else {
                sharedObject.deleteShare_trait()
            }
        }
        if (sharedObject instanceof OrgRole) {
            if (sharedObject.isShared) {
                List<Subscription> newTargets = Subscription.findAllByInstanceOf(this)
                log.debug('found targets: ' + newTargets)

                newTargets.each{ sub ->
                    log.debug('adding for: ' + sub)

                    // ERMS-1185
                    if (sharedObject.roleType in [RDStore.OR_AGENCY, RDStore.OR_PROVIDER]) {
                        List<OrgRole> existingOrgRoles = OrgRole.findAll{
                            sub == sub && roleType == sharedObject.roleType && org == sharedObject.org
                        }
                        if (existingOrgRoles) {
                            log.debug('found existing orgRoles, deleting: ' + existingOrgRoles)
                            existingOrgRoles.each{ tmp -> tmp.delete(flush:true) }
                        }
                    }
                    sharedObject.addShareForTarget_trait(sub)
                }
            }
            else {
                sharedObject.deleteShare_trait()
            }
        }
    }

    @Override
    void syncAllShares(List<ShareSupport> targets) {
        log.debug('synAllShares: ' + targets)

        documents.each{ sharedObject ->
            targets.each{ sub ->
                if (sharedObject.isShared) {
                    log.debug('adding for: ' + sub)
                    sharedObject.addShareForTarget_trait(sub)
                }
                else {
                    log.debug('deleting all shares')
                    sharedObject.deleteShare_trait()
                }
            }
        }

        orgRelations.each{ sharedObject ->
            targets.each{ sub ->
                if (sharedObject.isShared) {
                    log.debug('adding for: ' + sub)
                    sharedObject.addShareForTarget_trait(sub)
                }
                else {
                    log.debug('deleting all shares')
                    sharedObject.deleteShare_trait()
                }
            }
        }
    }

    @Override
    String _getCalculatedType() {
        String result = TYPE_UNKOWN

        if (getCollective() && getConsortia() && instanceOf) {
            result = TYPE_PARTICIPATION_AS_COLLECTIVE
        }
        else if(getCollective() && !getAllSubscribers() && !instanceOf) {
            result = TYPE_COLLECTIVE
        }
        else if(getConsortia() && !getAllSubscribers() && !instanceOf) {
            if(administrative) {
                result = TYPE_ADMINISTRATIVE
            }
            else result = TYPE_CONSORTIAL
        }
        else if((getCollective() || getConsortia()) && instanceOf) {
            result = TYPE_PARTICIPATION
        }
        // TODO remove type_local
        else if(getAllSubscribers() && !instanceOf) {
            result = TYPE_LOCAL
        }
        result
    }

    List<Org> getProviders() {
        Org.executeQuery("select og.org from OrgRole og where og.sub =:sub and og.roleType = :provider",
            [sub: this, provider: RDStore.OR_PROVIDER])
    }

    List<Org> getAgencies() {
        Org.executeQuery("select og.org from OrgRole og where og.sub =:sub and og.roleType = :agency",
                [sub: this, agency: RDStore.OR_AGENCY])
    }

    // used for views and dropdowns
    String getNameConcatenated() {
        Org cons = getConsortia()
        List<Org> subscr = getAllSubscribers()
        if (subscr) {
            "${name} (" + subscr.join(', ') + ")"
        }
        else if (cons){
            "${name} (${cons})"
        }
        else {
            name
        }
    }

    String getIsSlavedAsString() {
        isSlaved ? "Yes" : "No"
    }

    Set<License> getLicenses() {
        Set<License> result = []
        Links.findAllByDestinationAndLinkType(genericOIDService.getOID(this),RDStore.LINKTYPE_LICENSE).each { l ->
            result << genericOIDService.resolveOID(l.source)
        }
        result
    }

  Org getSubscriber() {
    Org result
    Org cons
    
    orgRelations.each { or ->
      if ( or.roleType.id in [RDStore.OR_SUBSCRIBER.id, RDStore.OR_SUBSCRIBER_CONS.id, RDStore.OR_SUBSCRIBER_CONS_HIDDEN.id] )
        result = or.org
        
      if ( or.roleType.id == RDStore.OR_SUBSCRIPTION_CONSORTIA.id )
        cons = or.org
    }
    
    if ( !result && cons ) {
      result = cons
    }
    
    result
  }

    List<Org> getAllSubscribers() {
        List<Org> result = []
        orgRelations.each { OrgRole or ->
            if ( or.roleType in [RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIBER_CONS_HIDDEN, RDStore.OR_SUBSCRIBER_COLLECTIVE] )
                result.add(or.org)
            }
        result
    }

    Org getProvider() {
        Org result
        orgRelations.each { OrgRole or ->
            if ( or.roleType == RDStore.OR_CONTENT_PROVIDER )
                result = or.org
            }
        result
    }

    Org getConsortia() {
        Org result
        orgRelations.each { OrgRole or ->
            if ( or.roleType == RDStore.OR_SUBSCRIPTION_CONSORTIA )
                result = or.org
            }
        result
    }

    Org getCollective() {
        Org result
        orgRelations.each {OrgRole or ->
            if ( or.roleType == RDStore.OR_SUBSCRIPTION_COLLECTIVE ) {
                result = or.org
            }
        }
        result
    }

    List<Org> getDerivedSubscribers() {
        List<Subscription> subs = Subscription.findAllByInstanceOf(this)
        subs.isEmpty() ? [] : OrgRole.findAllBySubInListAndRoleTypeInList(subs, [RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS], [sort: 'org.name']).collect{it.org}
    }

    Subscription _getCalculatedPrevious() {
        Links match = Links.findBySourceSubscriptionAndLinkType(this, RDStore.LINKTYPE_FOLLOWS)
        return match ? match.destinationSubscription : null
    }

    Subscription _getCalculatedSuccessor() {
        Links match = Links.findByDestinationSubscriptionAndLinkType(this,RDStore.LINKTYPE_FOLLOWS)
        return match ? match.sourceSubscription : null
    }

    boolean isMultiYearSubscription() {
        return (this.startDate && this.endDate && (this.endDate.minus(this.startDate) > 366))
    }

    boolean isCurrentMultiYearSubscription() {
        Date currentDate = new Date(System.currentTimeMillis())
        //println(this.endDate.minus(currentDate))
        return (this.isMultiYearSubscription() && this.endDate && (this.endDate.minus(currentDate) > 366))
    }

    boolean isCurrentMultiYearSubscriptionNew() {
        Date currentDate = new Date(System.currentTimeMillis())
        //println(this.endDate.minus(currentDate))
        return (this.isMultiYear && this.endDate && (this.endDate.minus(currentDate) > 366))
    }

    boolean islateCommer() {
        return (this.endDate && (this.endDate.minus(this.startDate) > 366 && this.endDate.minus(this.startDate) < 728))
    }

    boolean isEditableBy(user) {
        hasPerm('edit', user)
    }

    boolean isVisibleBy(user) {
        hasPerm('view', user)
    }

    boolean hasPerm(perm, user) {
        Role adm = Role.findByAuthority('ROLE_ADMIN')
        Role yda = Role.findByAuthority('ROLE_YODA')

        if (user.getAuthorities().contains(adm) || user.getAuthorities().contains(yda)) {
            return true
        }

        Org contextOrg = contextService.getOrg()
        if (user.getAuthorizedOrgsIds().contains(contextOrg?.id)) {

            OrgRole cons = OrgRole.findBySubAndOrgAndRoleType(
                    this, contextOrg, RDStore.OR_SUBSCRIPTION_CONSORTIA
            )
            OrgRole subscrCons = OrgRole.findBySubAndOrgAndRoleType(
                    this, contextOrg, RDStore.OR_SUBSCRIBER_CONS
            )
            OrgRole subscr = OrgRole.findBySubAndOrgAndRoleType(
                    this, contextOrg, RDStore.OR_SUBSCRIBER
            )

            if (perm == 'view') {
                return cons || subscrCons || subscr
            }
            if (perm == 'edit') {
                if(accessService.checkPermAffiliationX('ORG_INST,ORG_CONSORTIUM','INST_EDITOR','ROLE_ADMIN'))
                    return cons || subscr
            }
        }

        return false
    }

    @Transient
    def notifyDependencies(changeDocument) {
        log.debug("notifyDependencies(${changeDocument})")

        List<PendingChange> slavedPendingChanges = []
        List<Subscription> derived_subscriptions = getNonDeletedDerivedSubscriptions()

        derived_subscriptions.each { ds ->

            log.debug("Send pending change to ${ds.id}")

            Locale locale = org.springframework.context.i18n.LocaleContextHolder.getLocale()
            String description = messageSource.getMessage('default.accept.placeholder',null, locale)
            String definedType = 'text'

            if (this."${changeDocument.prop}" instanceof RefdataValue) {
                definedType = 'rdv'
            }
            else if (this."${changeDocument.prop}" instanceof Date) {
                definedType = 'date'
            }

            def msgParams = [
                    definedType,
                    "${changeDocument.prop}",
                    "${changeDocument.old}",
                    "${changeDocument.new}",
                    "${description}"
            ]

            PendingChange newPendingChange = changeNotificationService.registerPendingChange(
                    PendingChange.PROP_SUBSCRIPTION,
                    ds,
                    ds.getSubscriber(),
                    [
                            changeTarget:"${Subscription.class.name}:${ds.id}",
                            changeType:PendingChangeService.EVENT_PROPERTY_CHANGE,
                            changeDoc:changeDocument
                    ],
                    PendingChange.MSG_SU01,
                    msgParams,
                    "<strong>${changeDocument.prop}</strong> hat sich von <strong>\"${changeDocument.oldLabel?:changeDocument.old}\"</strong> zu <strong>\"${changeDocument.newLabel?:changeDocument.new}\"</strong> von der Lizenzvorlage geändert. " + description
            )

            if (newPendingChange && ds.isSlaved) {
                slavedPendingChanges << newPendingChange
            }
        }

        slavedPendingChanges.each { spc ->
            log.debug('autoAccept! performing: ' + spc)
            pendingChangeService.performAccept(spc)
        }
    }

    List<Subscription> getNonDeletedDerivedSubscriptions() {

        Subscription.where { instanceOf == this }.findAll()
    }

    Map<String, Object> _getCalculatedPropDefGroups(Org contextOrg) {
        def result = [ 'sorted':[], 'global':[], 'local':[], 'member':[], 'orphanedProperties':[]]

        // ALL type depending groups without checking tenants or bindings
        List<PropertyDefinitionGroup> groups = PropertyDefinitionGroup.findAllByOwnerType(Subscription.class.name, [sort:'name', order:'asc'])
        groups.each{ PropertyDefinitionGroup it ->

            // cons_members
            if (this.instanceOf) {
                Long subId
                if(this.getConsortia().id == contextOrg.id)
                    subId = this.instanceOf.id
                else subId = this.id
                List<PropertyDefinitionGroupBinding> bindings = PropertyDefinitionGroupBinding.executeQuery('select b from PropertyDefinitionGroupBinding b where b.propDefGroup = :pdg and b.sub.id = :id and b.propDefGroup.tenant = :ctxOrg',[pdg:it, id: subId,ctxOrg:contextOrg])
                PropertyDefinitionGroupBinding binding = null
                if(bindings)
                    binding = bindings.get(0)

                // global groups
                if (it.tenant == null) {
                    if (binding) {
                        result.member << [it, binding] // TODO: remove
                        result.sorted << ['member', it, binding]
                    } else {
                        result.global << it // TODO: remove
                        result.sorted << ['global', it, null]
                    }
                }
                // consortium @ member or single user; getting group by tenant (and instanceOf.binding)
                if (it.tenant?.id == contextOrg.id) {
                    if (binding) {
                        if(contextOrg.id == this.getConsortia().id) {
                            result.member << [it, binding] // TODO: remove
                            result.sorted << ['member', it, binding]
                        }
                        else {
                            result.local << [it, binding]
                            result.sorted << ['local', it, binding]
                        }
                    } else {
                        result.global << it // TODO: remove
                        result.sorted << ['global', it, null]
                    }
                }
                // subscriber consortial; getting group by consortia and instanceOf.binding
                else if (it.tenant?.id == this.getConsortia()?.id) {
                    if (binding) {
                        result.member << [it, binding] // TODO: remove
                        result.sorted << ['member', it, binding]
                    }
                }
            }
            // consortium or locals
            else {
                PropertyDefinitionGroupBinding binding = PropertyDefinitionGroupBinding.findByPropDefGroupAndSub(it, this)

                if (it.tenant == null || it.tenant?.id == contextOrg.id) {
                    if (binding) {
                        result.local << [it, binding] // TODO: remove
                        result.sorted << ['local', it, binding]
                    } else {
                        result.global << it // TODO: remove
                        result.sorted << ['global', it, null]
                    }
                }
            }
        }

        // storing properties without groups
        result.orphanedProperties = propertyService.getOrphanedProperties(this, result.sorted)

        result
    }

  String toString() {
      name ? "${name}" : "Subscription ${id}"
  }

  // JSON definition of the subscription object
  // see http://manbuildswebsite.com/2010/02/15/rendering-json-in-grails-part-3-customise-your-json-with-object-marshallers/
  // Also http://jwicz.wordpress.com/2011/07/11/grails-custom-xml-marshaller/
  // Also http://lucy-michael.klenk.ch/index.php/informatik/grails/c/
  static {
    grails.converters.JSON.registerObjectMarshaller(User) {
      // you can filter here the key-value pairs to output:
      return it?.properties.findAll {k,v -> k != 'passwd'}
    }
  }

  Date getRenewalDate() {
    manualRenewalDate
  }
  /**
  * OPTIONS: startDate, endDate, hideIdent, inclSubStartDate, hideDeleted, accessibleToUser,inst_shortcode
  **/
  @Transient
  static def refdataFind(GrailsParameterMap params) {
      List<Map<String, Object>> result = []

      String hqlString = "select sub from Subscription sub where lower(sub.name) like :name "
    def hqlParams = [name: ((params.q ? params.q.toLowerCase() : '' ) + "%")]
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd")
      RefdataValue cons_role        = RDStore.OR_SUBSCRIPTION_CONSORTIA
      RefdataValue subscr_role      = RDStore.OR_SUBSCRIBER
      RefdataValue subscr_cons_role = RDStore.OR_SUBSCRIBER_CONS
    def viableRoles = [cons_role, subscr_role, subscr_cons_role]
    
    hqlParams.put('viableRoles', viableRoles)

    if(params.hasDate ){

      def startDate = params.startDate.length() > 1 ? sdf.parse(params.startDate) : null
      def endDate = params.endDate.length() > 1 ? sdf.parse(params.endDate)  : null

      if(startDate){
          hqlString += " AND sub.startDate >= :startDate "
          hqlParams.put('startDate', startDate)
      }
      if(endDate){
        hqlString += " AND sub.endDate <= :endDate "
        hqlParams.put('endDate', endDate)
        }
    }

    if(params.inst_shortcode && params.inst_shortcode.length() > 1){
      hqlString += " AND exists ( select orgs from sub.orgRelations orgs where orgs.org.shortcode = :inst AND orgs.roleType IN (:viableRoles) ) "
      hqlParams.put('inst', params.inst_shortcode)
    }


    def results = Subscription.executeQuery(hqlString,hqlParams)

    if(params.accessibleToUser){
      for(int i=0;i<results.size();i++){
        if(! results.get(i).checkPermissionsNew("view",User.get(params.accessibleToUser))){
          results.remove(i)
        }
      }
    }

    results?.each { t ->
      String resultText = t.name
      def date = t.startDate ? " (${sdf.format(t.startDate)})" : ""
      resultText = params.inclSubStartDate == "true"? resultText + date : resultText
      resultText = params.hideIdent == "true"? resultText : resultText + " (${t.identifier})"
      result.add([id:"${t.class.name}:${t.id}",text:resultText])
    }

    result
  }

  def setInstitution(Org inst) {
      log.debug("Set institution ${inst}")

      OrgRole or = new OrgRole(org:inst, roleType:RDStore.OR_SUBSCRIBER, sub:this)
      if (this.orgRelations == null) {
        this.orgRelations = []
      }
     this.orgRelations.add(or)
  }

  String getCommaSeperatedPackagesIsilList() {
      List<String> result = []
      packages.each { it ->
          String identifierValue = it.pkg.getIdentifierByType('isil')?.value ?: null
          if (identifierValue) {
              result += identifierValue
          }
      }
      result.join(',')
  }

  boolean hasPlatformWithUsageSupplierId() {
      boolean hasUsageSupplier = false
      packages.each { it ->
          String hql="select count(distinct sp) from SubscriptionPackage sp "+
              "join sp.subscription.orgRelations as or "+
              "join sp.pkg.tipps as tipps "+
              "where sp.id=:sp_id "
              "and exists (select 1 from CustomProperties as cp where cp.owner = tipps.platform.id and cp.type.name = 'NatStat Supplier ID')"
          def queryResult = OrgRole.executeQuery(hql, ['sp_id':it.id])
          if (queryResult[0] > 0){
              hasUsageSupplier = true
          }
      }
      return hasUsageSupplier
  }

  def deduplicatedAccessPointsForOrgAndPlatform(org, platform) {
      String hql = """
select distinct oap from OrgAccessPoint oap 
    join oap.oapp as oapl
    join oapl.subPkg as subPkg
    join subPkg.subscription as sub
    where sub=:sub
    and oap.org=:org
    and oapl.active=true
    and oapl.platform=:platform
    
"""
      return OrgAccessPoint.executeQuery(hql, [sub:this, org:org, platform:platform])
  }

  def getHoldingTypes() {
      def types = issueEntitlements?.tipp?.title?.medium?.unique()
      types
  }

  String dropdownNamingConvention() {
      dropdownNamingConvention(contextService.org)
  }

  String dropdownNamingConvention(contextOrg){
       def messageSource = Holders.grailsApplication.mainContext.getBean('messageSource')
       SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
       String period = startDate ? sdf.format(startDate)  : ''

       period = endDate ? period + ' - ' + sdf.format(endDate)  : ''

       period = period ? '('+period+')' : ''

       String statusString = status ? status.getI10n('value') : RDStore.SUBSCRIPTION_NO_STATUS.getI10n('value')

       if(instanceOf) {
           def additionalInfo
           Map<Long,Org> orgRelationsMap = [:]
           orgRelations.each { or ->
               orgRelationsMap.put(or.roleType.id,or.org)
           }
           if(orgRelationsMap.get(RDStore.OR_SUBSCRIPTION_CONSORTIA.id)?.id == contextOrg.id) {
               if(orgRelationsMap.get(RDStore.OR_SUBSCRIBER_CONS.id))
                   additionalInfo =  orgRelationsMap.get(RDStore.OR_SUBSCRIBER_CONS.id)?.sortname
               else if(orgRelationsMap.get(RDStore.OR_SUBSCRIBER_CONS_HIDDEN.id))
                   additionalInfo =  orgRelationsMap.get(RDStore.OR_SUBSCRIBER_CONS_HIDDEN.id)?.sortname
           }
           else{
               additionalInfo = messageSource.getMessage('gasco.filter.consortialLicence',null, LocaleContextHolder.getLocale())
           }


           return name + ' - ' + statusString + ' ' +period + ' - ' + additionalInfo

       } else {

           return name + ' - ' + statusString + ' ' +period
       }
  }

    String dropdownNamingConventionWithoutOrg() {
        dropdownNamingConventionWithoutOrg(contextService.org)
    }

    String dropdownNamingConventionWithoutOrg(Org contextOrg){
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        String period = startDate ? sdf.format(startDate)  : ''

        period = endDate ? period + ' - ' + sdf.format(endDate)  : ''
        period = period ? '('+period+')' : ''

        String statusString = status ? status.getI10n('value') : RDStore.SUBSCRIPTION_NO_STATUS.getI10n('value')

        return name + ' - ' + statusString + ' ' +period
    }

    Subscription getDerivedSubscriptionBySubscribers(Org org) {
        Subscription result

        Subscription.findAllByInstanceOf(this).each { s ->
            List<OrgRole> ors = OrgRole.findAllWhere( sub: s )
            ors.each { OrgRole or ->
                if (or.roleType in [RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS] && or.org.id == org.id) {
                    result = or.sub
                }
            }
        }
        result
    }

    def getAllocationTerm() {
        def result = [:]

        if(isMultiYear) {
            result.startDate = startDate
            result.endDate = (endDate == instanceOf.endDate) ? endDate : instanceOf.endDate
        }
        else {
            result.startDate = startDate
            result.endDate = endDate
        }

        result
    }

    Collection<OrgAccessPoint> getOrgAccessPointsOfSubscriber() {
        Collection<OrgAccessPoint> result = []

        result = this.getSubscriber().accessPoints

        result
    }

}
