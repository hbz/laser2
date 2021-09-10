package de.laser


import com.k_int.kbplus.GenericOIDService
import de.laser.base.AbstractPropertyWithCalculatedLastUpdated
import de.laser.ctrl.MyInstitutionControllerService
import de.laser.ctrl.SubscriptionControllerService
import de.laser.finance.CostItem
import de.laser.helper.DateUtils
import de.laser.helper.RDConstants
import de.laser.helper.RDStore
import de.laser.properties.PropertyDefinition
import de.laser.properties.SubscriptionProperty
import grails.gorm.transactions.Transactional
import grails.web.mvc.FlashScope
import grails.web.servlet.mvc.GrailsParameterMap
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.transaction.TransactionStatus

import javax.servlet.http.HttpServletRequest
import java.text.SimpleDateFormat
import java.util.concurrent.ExecutorService

@Transactional
class ManagementService {

    static final int STATUS_OK = 0
    static final int STATUS_ERROR = 1

    AccessService accessService
    FormService formService
    AddressbookService addressbookService
    SubscriptionService subscriptionService
    ContextService contextService
    ExecutorService executorService
    AuditService auditService
    GenericOIDService genericOIDService
    MessageSource messageSource
    SubscriptionControllerService subscriptionControllerService
    MyInstitutionControllerService myInstitutionControllerService


    Map subscriptionsManagement(def controller, GrailsParameterMap parameterMap) {
        Map<String, Object> result = [:]

        switch (parameterMap.tab) {
            case "linkLicense":
                    if(parameterMap.processOption) {
                        processLinkLicense(controller, parameterMap)
                        parameterMap.remove('processOption')
                    }
                    result << linkLicense(controller, parameterMap)
                break
            case "linkPackages":
                    if(parameterMap.processOption) {
                        processLinkPackages(controller, parameterMap)
                        parameterMap.remove('processOption')
                    }
                    result << linkPackages(controller, parameterMap)
                break
            case "properties":
                    if(parameterMap.processOption) {
                        processProperties(controller, parameterMap)
                        parameterMap.remove('processOption')
                    }
                    result << properties(controller, parameterMap)
                break
            case "generalProperties":
                    if(parameterMap.processOption) {
                        processSubscriptionProperties(controller, parameterMap)
                        parameterMap.remove('processOption')
                    }
                    result << subscriptionProperties(controller, parameterMap)
                break
            case "providerAgency":
                result << subscriptionProperties(controller, parameterMap)
                break
            case "multiYear":
                result << subscriptionProperties(controller, parameterMap)
                break
            case "notes":
                result << subscriptionProperties(controller, parameterMap)
                break
            case "documents":
                result << subscriptionProperties(controller, parameterMap)
                break
            case "customerIdentifiers":
                result << customerIdentifierMembers(controller, parameterMap)
                break
        }

        //println(result)
        result.result

    }

    //--------------------------------------------- subscriptions management section for SubscriptionController-------------------------------------------------

    Map<String, Object> customerIdentifierMembers(SubscriptionController controller, GrailsParameterMap params) {
        Map<String,Object> result = getResultGenericsAndCheckAccess(controller, params)
        if(!result) {
            [result:null,status:STATUS_ERROR]
        }
        else {
            result.platforms = Platform.executeQuery('select pkg.nominalPlatform from SubscriptionPackage sp join sp.pkg pkg where sp.subscription = :parentSub', [parentSub: result.subscription]) as Set<Platform>
            result.members = Org.executeQuery("select org from OrgRole oo join oo.sub sub join oo.org org where sub.instanceOf = :parent and oo.roleType in (:subscrTypes) order by org.sortname asc, org.name asc",[parent: result.subscription, subscrTypes: [RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIBER_CONS_HIDDEN]]) as Set<Org>
            result.keyPairs = []
            result.platforms.each { Platform platform ->
                result.members.each { Org customer ->
                    //create dummies for that they may be xEdited - OBSERVE BEHAVIOR for eventual performance loss!
                    CustomerIdentifier keyPair = CustomerIdentifier.findByPlatformAndCustomer(platform, customer)
                    if(!keyPair) {
                        keyPair = new CustomerIdentifier(platform: platform,
                                customer: customer,
                                type: RefdataValue.getByValueAndCategory('Default', RDConstants.CUSTOMER_IDENTIFIER_TYPE),
                                owner: contextService.getOrg(),
                                isPublic: true)
                        if(!keyPair.save()) {
                            log.warn(keyPair.errors.getAllErrors().toListString())
                        }
                    }
                    result.keyPairs << keyPair
                }
            }
            [result:result,status:STATUS_OK]
        }
    }

    boolean deleteCustomerIdentifier(Long id) {
        CustomerIdentifier ci = CustomerIdentifier.get(id)
        ci.value = null
        ci.requestorKey = null
        ci.save()
    }

    //--------------------------------------------- general subscriptions management section -------------------------------------------------

    Map<String,Object> linkLicense(def controller, GrailsParameterMap params) {
        Map<String,Object> result = getResultGenericsAndCheckAccess(controller, params)
        if (!result) {
            [result:null,status:STATUS_ERROR]
        }
        else{

            if(controller instanceof SubscriptionController) {
                result.parentLicenses = Links.executeQuery('select li.sourceLicense from Links li where li.destinationSubscription = :subscription and li.linkType = :linkType',[subscription:result.subscription,linkType: RDStore.LINKTYPE_LICENSE])
                result.validLicenses = []
                if(result.parentLicenses) {
                    result.validLicenses.addAll(License.findAllByInstanceOfInList(result.parentLicenses))
                }
                result.filteredSubscriptions = subscriptionControllerService.getFilteredSubscribers(params,result.subscription)
            }

            if(controller instanceof MyInstitutionController) {
                result.putAll(subscriptionService.getMySubscriptions(params,result.user,result.institution))

                result.filteredSubscriptions = result.subscriptions

                String base_qry
                Map qry_params

                if (accessService.checkPerm("ORG_INST")) {
                    base_qry = "from License as l where ( exists ( select o from l.orgRelations as o where ( ( o.roleType = :roleType1 or o.roleType = :roleType2 ) AND o.org = :lic_org ) ) )"
                    qry_params = [roleType1:RDStore.OR_LICENSEE, roleType2:RDStore.OR_LICENSEE_CONS, lic_org:result.institution]
                }
                else if (accessService.checkPerm("ORG_CONSORTIUM")) {
                    base_qry = "from License as l where exists ( select o from l.orgRelations as o where ( o.roleType = :roleTypeC AND o.org = :lic_org AND l.instanceOf is null AND NOT exists ( select o2 from l.orgRelations as o2 where o2.roleType = :roleTypeL ) ) )"
                    qry_params = [roleTypeC:RDStore.OR_LICENSING_CONSORTIUM, roleTypeL:RDStore.OR_LICENSEE_CONS, lic_org:result.institution]
                }
                else {
                    base_qry = "from License as l where exists ( select o from l.orgRelations as o where  o.roleType = :roleType AND o.org = :lic_org ) "
                    qry_params = [roleType:RDStore.OR_LICENSEE_CONS, lic_org:result.institution]
                }

                result.validLicenses = License.executeQuery( "select l " + base_qry, qry_params )

            }

            [result:result,status:STATUS_OK]
        }
    }

    boolean processLinkLicense(def controller, GrailsParameterMap params) {
        Map<String,Object> result = getResultGenericsAndCheckAccess(controller, params)
        if(result.editable && formService.validateToken(params)) {
            Locale locale = LocaleContextHolder.getLocale()
            FlashScope flash = getCurrentFlashScope()
            List selectedSubs = params.list("selectedSubs")
            License newLicense = License.get(params.selectedLicense)
            if(selectedSubs && newLicense) {
                if (result.subscription && (params.processOption == 'unlinkAll')) {
                    Set<Subscription> validSubChilds = Subscription.findAllByInstanceOf(result.subscription)
                    List<GString> changeAccepted = []
                    validSubChilds.each { Subscription subChild ->
                        if (subscriptionService.setOrgLicRole(subChild, newLicense, params.processOption == 'unlinkAll'))
                            changeAccepted << "${subChild.name} (${messageSource.getMessage('subscription.linkInstance.label', null, locale)} ${subChild.getSubscriber().sortname})"
                    }
                    if (changeAccepted) {
                        flash.message = changeAccepted.join('<br>')
                    }
                }
                else if (params.processOption == 'linkLicense' || params.processOption == 'unlinkLicense') {
                    Set<Subscription> subscriptions = Subscription.findAllByIdInList(selectedSubs)
                    List<GString> changeAccepted = []
                    subscriptions.each { Subscription subscription ->
                        if (subscription.isEditableBy(result.user)) {
                            if (newLicense && subscriptionService.setOrgLicRole(subscription, newLicense, params.processOption == 'unlinkLicense'))
                                changeAccepted << "${subscription.name} (${messageSource.getMessage('subscription.linkInstance.label', null, locale)} ${subscription.getSubscriber().sortname})"
                        }
                    }
                    if (changeAccepted) {
                        flash.message = changeAccepted.join('<br>')
                    }
                }
            }else{
                if (selectedSubs.size() < 1) {
                    flash.error = messageSource.getMessage('subscriptionsManagement.noSelectedSubscriptions', null, locale)
                }
                if (!newLicense) {
                    flash.error = messageSource.getMessage('subscriptionsManagement.noSelectedLicense', null, locale)
                }
            }
        }
    }

    Map<String,Object> linkPackages(def controller, GrailsParameterMap params) {
        Map<String,Object> result = getResultGenericsAndCheckAccess(controller, params)
        if(!result)
            [result:null,status:STATUS_ERROR]
        else {
            if(controller instanceof SubscriptionController) {
                Set<Thread> threadSet = Thread.getAllStackTraces().keySet()
                Thread[] threadArray = threadSet.toArray(new Thread[threadSet.size()])
                threadArray.each {
                    if (it.name == 'processLinkPackages'+result.subscription.id) {
                        result.isLinkingRunning = true
                    }
                }
                result.validPackages = result.subscription.packages
                result.filteredSubscriptions = subscriptionControllerService.getFilteredSubscribers(params,result.subscription)
                result.childWithCostItems = CostItem.executeQuery('select ci.subPkg from CostItem ci where ci.subPkg.subscription in (:filteredSubChildren) and ci.costItemStatus != :deleted and ci.owner = :context',[context:result.institution, deleted:RDStore.COST_ITEM_DELETED, filteredSubChildren:result.filteredSubscriptions.collect { row -> row.sub }])
            }

            if(controller instanceof MyInstitutionController) {
                Set<Thread> threadSet = Thread.getAllStackTraces().keySet()
                Thread[] threadArray = threadSet.toArray(new Thread[threadSet.size()])
                threadArray.each {
                    if (it.name == 'processLinkPackages'+result.user.id) {
                        result.isLinkingRunning = true
                    }
                }
                result.validPackages = Package.findAllByGokbIdIsNotNullAndPackageStatusNotEqual(RDStore.PACKAGE_STATUS_DELETED)

                result.putAll(subscriptionService.getMySubscriptions(params,result.user,result.institution))

                result.filteredSubscriptions = result.subscriptions
                result.childWithCostItems = CostItem.executeQuery('select ci.subPkg from CostItem ci where ci.subPkg.subscription in (:filteredSubscriptions) and ci.costItemStatus != :deleted and ci.owner = :context',[context:result.institution, deleted:RDStore.COST_ITEM_DELETED, filteredSubscriptions:result.filteredSubscriptions])
            }

            [result:result,status:STATUS_OK]
        }
    }

    boolean processLinkPackages(def controller, GrailsParameterMap params) {
        Map<String,Object> result = getResultGenericsAndCheckAccess(controller, params)
        if (result.editable && formService.validateToken(params)) {
            FlashScope flash = getCurrentFlashScope()
            Locale locale = LocaleContextHolder.getLocale()
            List selectedSubs = params.list("selectedSubs")
            result.message = []
            result.error = []
            if (result.subscription && (params.processOption == 'allWithoutTitle' || params.processOption == 'allWithTitle')) {
                List<SubscriptionPackage> validSubChildPackages = SubscriptionPackage.executeQuery("select sp from SubscriptionPackage sp join sp.subscription sub where sub.instanceOf = :parent", [parent: result.subscription])
                validSubChildPackages.each { SubscriptionPackage sp ->
                    if (!CostItem.executeQuery('select ci from CostItem ci where ci.subPkg = :sp and ci.costItemStatus != :deleted and ci.owner = :context', [sp: sp, deleted: RDStore.COST_ITEM_DELETED, context: result.institution])) {
                        if (params.processOption == 'allWithTitle') {
                            if (sp.pkg.unlinkFromSubscription(sp.subscription, result.institution, true)) {
                                Object[] args = [sp.pkg.name, sp.subscription.getSubscriber().name]
                                result.message << messageSource.getMessage('subscriptionsManagement.unlinkInfo.withIE.successful', args, locale)
                            } else {
                                Object[] args = [sp.pkg.name, sp.subscription.getSubscriber().name]
                                result.error << messageSource.getMessage('subscriptionsManagement.unlinkInfo.withIE.fail', args, locale)
                            }
                        } else {
                            if (sp.pkg.unlinkFromSubscription(sp.subscription, result.institution, false)) {
                                Object[] args = [sp.pkg.name, sp.subscription.getSubscriber().name]
                                result.message << messageSource.getMessage('subscriptionsManagement.unlinkInfo.onlyPackage.successful', args, locale)
                            } else {
                                Object[] args = [sp.pkg.name, sp.subscription.getSubscriber().name]
                                result.error << messageSource.getMessage('subscriptionsManagement.unlinkInfo.onlyPackage.fail', args, locale)
                            }
                        }
                    } else {
                        Object[] args = [sp.pkg.name, sp.subscription.getSubscriber().name]
                        result.error << messageSource.getMessage('subscriptionsManagement.unlinkInfo.costsExisting', args, locale)
                    }
                }
            } else if (selectedSubs && params.selectedPackage && params.processOption) {
                Package pkg_to_link
                SubscriptionPackage subscriptionPackage
                String threadName
                if(controller instanceof SubscriptionController) {
                    subscriptionPackage = SubscriptionPackage.get(params.selectedPackage)
                    pkg_to_link = subscriptionPackage.pkg
                    threadName = "processLinkPackages${result.subscription.id}"
                }

                if(controller instanceof MyInstitutionController) {
                    pkg_to_link = Package.get(params.selectedPackage)
                    threadName = "processLinkPackages${result.user.id}"
                }

                if (pkg_to_link) {
                    executorService.execute({
                        IssueEntitlement.withNewTransaction { TransactionStatus ts ->
                            Thread.currentThread().setName(threadName)
                            selectedSubs.each { id ->
                                Subscription subscription = Subscription.get(Long.parseLong(id))
                                if (subscription.isEditableBy(result.user)) {
                                    if (params.processOption == 'linkwithIE' || params.processOption == 'linkwithoutIE') {
                                        if (!(subscription.packages && (pkg_to_link.id in subscription.packages.pkg.id))) {
                                            if (params.processOption == 'linkwithIE') {
                                                if (result.subscription) {
                                                    subscriptionService.addToSubscriptionCurrentStock(subscription, result.subscription, pkg_to_link)
                                                } else {
                                                    subscriptionService.addToSubscription(subscription, pkg_to_link, true)
                                                }
                                            } else {
                                                subscriptionService.addToSubscription(subscription, pkg_to_link, false)
                                            }
                                        }
                                    }
                                    if (params.processOption == 'unlinkwithIE' || params.processOption == 'unlinkwithoutIE') {
                                        if (subscription.packages && (pkg_to_link.id in subscription.packages.pkg.id)) {
                                            SubscriptionPackage subPkg = SubscriptionPackage.findBySubscriptionAndPkg(subscription, pkg_to_link)
                                            if (!CostItem.executeQuery('select ci from CostItem ci where ci.subPkg = :sp and ci.costItemStatus != :deleted and ci.owner = :context', [sp: subPkg, deleted: RDStore.COST_ITEM_DELETED, context: result.institution])) {
                                                if (params.processOption == 'unlinkwithIE') {
                                                    pkg_to_link.unlinkFromSubscription(subscription, result.institution, true)
                                                } else {
                                                    pkg_to_link.unlinkFromSubscription(subscription, result.institution, false)
                                                }
                                            } else {
                                                Object[] args = [subPkg.pkg.name, subPkg.subscription.getSubscriber().name]
                                                result.error << messageSource.getMessage('subscriptionsManagement.unlinkInfo.costsExisting', args, locale)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    })
                }
            } else {
                if (selectedSubs.size() < 1) {
                    flash.error = messageSource.getMessage('subscriptionsManagement.noSelectedMember', null, locale)
                }
                if (!params.selectedPackage) {
                    flash.error = messageSource.getMessage('subscriptionsManagement.noSelectedPackage', null, locale)
                }
            }
            if (result.error) {
                flash.error = result.error.join('<br>')
            }

            if (result.message) {
                flash.message = result.message.join('<br>')
            }
        }
    }

    Map<String,Object> properties(def controller, GrailsParameterMap params) {
        Map<String,Object> result = getResultGenericsAndCheckAccess(controller, params)
        if (!result) {
            [result:null,status:STATUS_ERROR]
        }
        else {

            result.propertiesFilterPropDef = params.propertiesFilterPropDef ? genericOIDService.resolveOID(params.propertiesFilterPropDef.replace(" ", "")) : null

            params.remove('propertiesFilterPropDef')

            if(controller instanceof SubscriptionController) {
                Set<Subscription> validSubChildren = Subscription.executeQuery("select oo.sub from OrgRole oo where oo.sub.instanceOf = :parent and oo.roleType = :roleType order by oo.org.sortname asc", [parent: result.subscription, roleType: RDStore.OR_SUBSCRIBER_CONS])
                if (validSubChildren) {
                    Set<PropertyDefinition> propList = PropertyDefinition.executeQuery("select distinct(sp.type) from SubscriptionProperty sp where sp.owner in (:subscriptionSet) and sp.tenant = :ctx and sp.instanceOf = null", [subscriptionSet: validSubChildren, ctx: result.institution])
                    propList.addAll(result.subscription.propertySet.type)
                    result.propList = propList
                    result.filteredSubscriptions = validSubChildren
                    List<Subscription> childSubs = result.subscription.getNonDeletedDerivedSubscriptions()
                    if (childSubs) {
                        String localizedName
                        switch (LocaleContextHolder.getLocale()) {
                            case Locale.GERMANY:
                            case Locale.GERMAN: localizedName = "name_de"
                                break
                            default: localizedName = "name_en"
                                break
                        }
                        String query = "select sp.type from SubscriptionProperty sp where sp.owner in (:subscriptionSet) and sp.tenant = :context and sp.instanceOf = null order by sp.type.${localizedName} asc"
                        Set<PropertyDefinition> memberProperties = PropertyDefinition.executeQuery(query, [subscriptionSet: childSubs, context: result.institution])
                        result.memberProperties = memberProperties
                    }
                }
            }

            if(controller instanceof MyInstitutionController) {

                result.putAll(subscriptionService.getMySubscriptions(params,result.user,result.institution))

                result.filteredSubscriptions = result.subscriptions
            }

            [result:result,status:STATUS_OK]
        }
    }

    boolean processProperties(def controller, GrailsParameterMap params) {
        Map<String,Object> result = getResultGenericsAndCheckAccess(controller, params)
        if(result.editable && formService.validateToken(params)) {
            Locale locale = LocaleContextHolder.getLocale()
            FlashScope flash = getCurrentFlashScope()
            PropertyDefinition propertiesFilterPropDef = params.propertiesFilterPropDef ? genericOIDService.resolveOID(params.propertiesFilterPropDef.replace(" ", "")) : null
            List selectedSubs = params.list("selectedSubs")
            if (selectedSubs.size() > 0 && params.processOption && propertiesFilterPropDef && params.filterPropValue) {
                int newProperties = 0
                int changeProperties = 0
                int deletedProperties = 0
                Object[] args
                    if(params.processOption == 'changeCreateProperty') {
                        Set<Subscription> subscriptions = Subscription.findAllByIdInList(selectedSubs)
                        subscriptions.each { Subscription subscription ->
                            if (subscription.isEditableBy(result.user)) {
                                List<SubscriptionProperty> existingProps = []
                                String propDefFlag
                                if (propertiesFilterPropDef.tenant == result.institution) {
                                    //private Property
                                    existingProps.addAll(subscription.propertySet.findAll { SubscriptionProperty sp ->
                                        sp.owner.id == subscription.id && sp.type.id == propertiesFilterPropDef.id
                                    })
                                    propDefFlag = PropertyDefinition.PRIVATE_PROPERTY
                                } else {
                                    //custom Property
                                    existingProps.addAll(subscription.propertySet.findAll { SubscriptionProperty sp ->
                                        sp.type.id == propertiesFilterPropDef.id && sp.owner.id == subscription.id && sp.tenant.id == result.institution.id
                                    })
                                    propDefFlag = PropertyDefinition.CUSTOM_PROPERTY
                                }
                                if (existingProps.size() == 0 || propertiesFilterPropDef.multipleOccurrence) {
                                    AbstractPropertyWithCalculatedLastUpdated newProp = PropertyDefinition.createGenericProperty(propDefFlag, subscription, propertiesFilterPropDef, result.institution)
                                    if (newProp.hasErrors()) {
                                        log.error(newProp.errors.toString())
                                    } else {
                                        log.debug("New property created: " + newProp.type.name)
                                        newProperties++
                                        subscriptionService.updateProperty(controller, newProp, params.filterPropValue)
                                    }
                                }
                                if (existingProps.size() == 1) {
                                    SubscriptionProperty privateProp = SubscriptionProperty.get(existingProps[0].id)
                                    changeProperties++
                                    subscriptionService.updateProperty(controller, privateProp, params.filterPropValue)
                                }
                            }
                        }

                        args = [newProperties, changeProperties]
                        flash.message = messageSource.getMessage('subscriptionsManagement.successful.property', args, locale)

                    }else if(params.processOption == 'deleteAllProperties'){
                        List<Subscription> validSubChilds = Subscription.findAllByInstanceOf(result.subscription)
                        validSubChilds.each { Subscription subChild ->
                            SubscriptionProperty existingProp
                            if (propertiesFilterPropDef.tenant == result.institution) {
                                //private Property
                                existingProp = subChild.propertySet.find { SubscriptionProperty sp ->
                                    sp.owner.id == subChild.id && sp.type.id == propertiesFilterPropDef.id
                                }
                                if (existingProp){
                                    try {
                                        subChild.propertySet.remove(existingProp)
                                        existingProp.delete()
                                        deletedProperties++
                                    }
                                    catch (Exception e) {
                                        log.error( e.toString() )
                                    }
                                }
                            }
                            else {
                                //custom Property
                                existingProp = subChild.propertySet.find { SubscriptionProperty sp ->
                                    sp.type.id == propertiesFilterPropDef.id && sp.owner.id == subChild.id && sp.tenant.id == result.institution.id
                                }
                                if (existingProp && !(existingProp.hasProperty('instanceOf') && existingProp.instanceOf && AuditConfig.getConfig(existingProp.instanceOf))){
                                    try {
                                        subChild.propertySet.remove(existingProp)
                                        existingProp.delete()
                                        deletedProperties++
                                    }
                                    catch (Exception e){
                                        log.error( e.toString() )
                                    }
                                }
                            }
                        }
                        args = [deletedProperties]
                        result.message = messageSource.getMessage('subscriptionsManagement.deletedProperties', args, LocaleContextHolder.getLocale())
                    }
            } else {
                if (selectedSubs.size() < 1) {
                    flash.error = messageSource.getMessage('subscriptionsManagement.noSelectedMember', null, locale)
                }
                if (!propertiesFilterPropDef) {
                    flash.error = messageSource.getMessage('subscriptionsManagement.noPropertySelected',null, locale)
                }
                if (!params.filterPropValue) {
                    flash.error = messageSource.getMessage('subscriptionsManagement.noPropertyValue', null, locale)
                }
            }
        }
    }

    Map<String,Object> subscriptionProperties(def controller, GrailsParameterMap params) {
        Map<String,Object> result = getResultGenericsAndCheckAccess(controller, params)
        if(!result) {
            [result:null,status:STATUS_ERROR]
        }
        else {

            if(controller instanceof SubscriptionController) {
                result.filteredSubscriptions = subscriptionControllerService.getFilteredSubscribers(params,result.subscription)
            }

            if(controller instanceof MyInstitutionController) {
                result.putAll(subscriptionService.getMySubscriptions(params,result.user,result.institution))

                result.filteredSubscriptions = result.subscriptions
            }

            if(params.tab == 'providerAgency') {
                result.modalPrsLinkRole = RefdataValue.getByValueAndCategory('Specific subscription editor', RDConstants.PERSON_RESPONSIBILITY)
                result.modalVisiblePersons = addressbookService.getPrivatePersonsByTenant(result.institution)
                if(result.subscription) {
                    result.visibleOrgRelations = OrgRole.executeQuery("select oo from OrgRole oo join oo.org org where oo.sub = :parent and oo.roleType in (:roleTypes) order by org.name asc", [parent: result.subscription, roleTypes: [RDStore.OR_PROVIDER, RDStore.OR_AGENCY]])
                }
            }
            [result:result,status:STATUS_OK]
        }
    }

    Map<String,Object> processSubscriptionProperties(def controller, GrailsParameterMap params) {
        Map<String,Object> result = getResultGenericsAndCheckAccess(controller, params)
        if(result.editable) {
            Locale locale = LocaleContextHolder.getLocale()
            List selectedSubs = params.list("selectedSubs")
            if (selectedSubs) {
                Set change = [], noChange = []
                SimpleDateFormat sdf = DateUtils.getSDF_NoTime()
                Date startDate = params.valid_from ? sdf.parse(params.valid_from) : null
                Date endDate = params.valid_to ? sdf.parse(params.valid_to) : null
                Set<Subscription> subscriptions = Subscription.findAllByIdInList(selectedSubs)
                subscriptions.each { Subscription subscription ->
                    if (subscription.isEditableBy(result.user)) {
                        if (startDate && !auditService.getAuditConfig(subscription.instanceOf, 'startDate')) {
                            subscription.startDate = startDate
                            change << messageSource.getMessage('default.startDate.label', null, locale)
                        }
                        if (startDate && auditService.getAuditConfig(subscription.instanceOf, 'startDate')) {
                            noChange << messageSource.getMessage('default.startDate.label', null, locale)
                        }
                        if (endDate && !auditService.getAuditConfig(subscription.instanceOf, 'endDate')) {
                            subscription.endDate = endDate
                            change << messageSource.getMessage('default.endDate.label', null, locale)
                        }
                        if (endDate && auditService.getAuditConfig(subscription.instanceOf, 'endDate')) {
                            noChange << messageSource.getMessage('default.endDate.label', null, locale)
                        }
                        if (params.status && !auditService.getAuditConfig(subscription.instanceOf, 'status')) {
                            subscription.status = RefdataValue.get(params.status) ?: subscription.status
                            change << messageSource.getMessage('subscription.status.label', null, locale)
                        }
                        if (params.status && auditService.getAuditConfig(subscription.instanceOf, 'status')) {
                            noChange << messageSource.getMessage('subscription.status.label', null, locale)
                        }
                        if (params.kind && !auditService.getAuditConfig(subscription.instanceOf, 'kind')) {
                            subscription.kind = RefdataValue.get(params.kind) ?: subscription.kind
                            change << messageSource.getMessage('subscription.kind.label', null, locale)
                        }
                        if (params.kind && auditService.getAuditConfig(subscription.instanceOf, 'kind')) {
                            noChange << messageSource.getMessage('subscription.kind.label', null, locale)
                        }
                        if (params.form && !auditService.getAuditConfig(subscription.instanceOf, 'form')) {
                            subscription.form = RefdataValue.get(params.form) ?: subscription.form
                            change << messageSource.getMessage('subscription.form.label', null, locale)
                        }
                        if (params.form && auditService.getAuditConfig(subscription.instanceOf, 'form')) {
                            noChange << messageSource.getMessage('subscription.form.label', null, locale)
                        }
                        if (params.resource && !auditService.getAuditConfig(subscription.instanceOf, 'resource')) {
                            subscription.resource = RefdataValue.get(params.resource) ?: subscription.resource
                            change << messageSource.getMessage('subscription.resource.label', null, locale)
                        }
                        if (params.resource && auditService.getAuditConfig(subscription.instanceOf, 'resource')) {
                            noChange << messageSource.getMessage('subscription.resource.label', null, locale)
                        }
                        if (params.isPublicForApi && !auditService.getAuditConfig(subscription.instanceOf, 'isPublicForApi')) {
                            subscription.isPublicForApi = RefdataValue.get(params.isPublicForApi) == RDStore.YN_YES
                            change << messageSource.getMessage('subscription.isPublicForApi.label', null, locale)
                        }
                        if (params.isPublicForApi && auditService.getAuditConfig(subscription.instanceOf, 'isPublicForApi')) {
                            noChange << messageSource.getMessage('subscription.isPublicForApi.label', null, locale)
                        }
                        if (params.hasPerpetualAccess && !auditService.getAuditConfig(subscription.instanceOf, 'hasPerpetualAccess')) {
                            subscription.hasPerpetualAccess = RefdataValue.get(params.hasPerpetualAccess) == RDStore.YN_YES
                            //subscription.hasPerpetualAccess = RefdataValue.get(params.hasPerpetualAccess)
                            change << messageSource.getMessage('subscription.hasPerpetualAccess.label', null, locale)
                        }
                        if (params.hasPerpetuaLAccess && auditService.getAuditConfig(subscription.instanceOf, 'hasPerpetualAccess')) {
                            noChange << messageSource.getMessage('subscription.hasPerpetualAccess.label', null, locale)
                        }
                        if (params.hasPublishComponent && !auditService.getAuditConfig(subscription.instanceOf, 'hasPublishComponent')) {
                            subscription.hasPublishComponent = RefdataValue.get(params.hasPublishComponent) == RDStore.YN_YES
                            change << messageSource.getMessage('subscription.hasPublishComponent.label', null, locale)
                        }
                        if (params.hasPublishComponent && auditService.getAuditConfig(subscription.instanceOf, 'hasPublishComponent')) {
                            noChange << messageSource.getMessage('subscription.hasPublishComponent.label', null, locale)
                        }
                        if (subscription.isDirty()) {
                            subscription.save()
                        }
                    }
                }

            } else {
                result.error = messageSource.getMessage('subscriptionsManagement.noSelectedMember', null, locale)
            }
        }
    }


    //--------------------------------------------- helper section -------------------------------------------------

    FlashScope getCurrentFlashScope() {
        GrailsWebRequest grailsWebRequest = WebUtils.retrieveGrailsWebRequest()
        HttpServletRequest request = grailsWebRequest.getCurrentRequest()

        grailsWebRequest.attributes.getFlashScope(request)
    }

    Map<String,Object> getResultGenericsAndCheckAccess(def controller, GrailsParameterMap params) {
        Map<String, Object> result = [:]

        if(controller instanceof SubscriptionController) {
            result = subscriptionControllerService.getResultGenericsAndCheckAccess(params, AccessService.CHECK_VIEW)
        }

        if(controller instanceof MyInstitutionController) {
            result = myInstitutionControllerService.getResultGenerics(controller, params)
            result.contextOrg = contextService.getOrg()
        }

        return  result

    }
}