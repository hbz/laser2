package de.laser.ajax

import de.laser.License
import de.laser.Org
import de.laser.Subscription
import de.laser.Address
import de.laser.Doc
import de.laser.Person
import de.laser.PersonRole
import de.laser.Task
import de.laser.helper.RDConstants
import de.laser.helper.RDStore
import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured

@Secured(['permitAll'])
class AjaxHtmlController {

    /**
     * only template rendering here ..
     * no object manipulation
     *
     */

    def addressbookService
    def contextService
    def financeService
    def genericOIDService
    def taskService
    def reportingService

    def test() {
        String result = '<p data-status="ok">OK'
        if (params.id) {
            result += ', ID:' + params.id
        }
        result += '</p>'
        render result
    }

    @Secured(['ROLE_USER'])
    def loadGeneralFilter() {
        Map<String,Object> result = [entry:params.entry,queried:params.queried]
        render view: '/myInstitution/_displayConfigurations', model: result
    }

    @Secured(['ROLE_USER'])
    def getGraphsForGeneral() {
        Map<String,Object> result = [:]
        def options = JSON.parse(params.requestOptions)
        options.institution = contextService.org
        result.putAll(reportingService.generateGrowth(options))
        log.debug(result)
        render view: '/reporting/_generalGraphs', model: result
    }

    @Secured(['ROLE_USER'])
    def getGraphsForSubscription() {
        Map<String, Object> result = [institution:contextService.org]
        def options = JSON.parse(params.requestOptions)

        if (params.costItem) {
            Subscription entry = (Subscription) genericOIDService.resolveOID(params.subscription)
            //get cost item groupings
            result.putAll(reportingService.groupCostItemsBySubscription([institution:result.institution,entry:entry,options:options]))
            result.entry = entry
            result.displayConfig = options.displayConfiguration
        }
        log.debug(result)
        render view: '/reporting/_subscriptionGraphs', model: result
    }

    @Secured(['ROLE_USER'])
    def getLicensePropertiesForSubscription() {
        License loadFor = License.get(params.loadFor)
        if (loadFor) {
            Map<String,Object> derivedPropDefGroups = loadFor._getCalculatedPropDefGroups(contextService.org)
            render view: '/subscription/_licProp', model: [license: loadFor, derivedPropDefGroups: derivedPropDefGroups, linkId: params.linkId]
        }
    }

    @Secured(['ROLE_USER'])
    def editNote() {
        Map<String, Object> result = [:]
        result.params = params
        result.noteInstance = Doc.get(params.id)

        render template: "/templates/notes/modal_edit", model: result
    }

    @Secured(['ROLE_USER'])
    def readNote() {
        Map<String, Object> result = [:]
        result.params = params
        result.noteInstance = Doc.get(params.id)

        render template: "/templates/notes/modal_read", model: result
    }

    @Secured(['ROLE_USER'])
    def createTask() {
        long backendStart = System.currentTimeMillis()
        Org contextOrg = contextService.getOrg()
        Map<String, Object> result = taskService.getPreconditions(contextOrg)

        result.backendStart = backendStart

        render template: "/templates/tasks/modal_create", model: result
    }

    @Secured(['ROLE_USER'])
    def editTask() {
        Org contextOrg = contextService.getOrg()
        Map<String, Object> result = taskService.getPreconditionsWithoutTargets(contextOrg)

        result.params = params
        result.taskInstance = Task.get(params.id)

        if (result.taskInstance){
            render template: "/templates/tasks/modal_edit", model: result
//        } else {
//            flash.error = "Diese Aufgabe existiert nicht (mehr)."
//            redirect(url: request.getHeader('referer'))
        }
    }

    @Secured(['ROLE_USER'])
    def createAddress() {
        Map<String, Object> model = [:]
        model.orgId = params.orgId
        model.prsId = params.prsId
        model.redirect = params.redirect
        model.typeId = params.typeId ? Long.valueOf(params.typeId) : null
        model.hideType = params.hideType

        if (model.orgId && model.typeId) {
            String messageCode = 'addressFormModalLibraryAddress'
            if (model.typeId == RDStore.ADRESS_TYPE_LEGAL_PATRON.id)  {
                messageCode = 'addressFormModalLegalPatronAddress'
            }
            else if (model.typeId == RDStore.ADRESS_TYPE_BILLING.id)  {
                messageCode = 'addressFormModalBillingAddress'
            }
            else if (model.typeId == RDStore.ADRESS_TYPE_POSTAL.id)   {
                messageCode = 'addressFormModalPostalAddress'
            }
            else if (model.typeId == RDStore.ADRESS_TYPE_DELIVERY.id) {
                messageCode = 'addressFormModalDeliveryAddress'
            }
            else if (model.typeId == RDStore.ADRESS_TYPE_LIBRARY.id)  {
                messageCode = 'addressFormModalLibraryAddress'
            }

            model.modalText = message(code: 'default.create.label', args: [message(code: messageCode)])
        } else {
            model.modalText = message(code: 'default.new.label', args: [message(code: 'person.address.label')])
        }
        model.modalMsgSave = message(code: 'default.button.create.label')
        model.url = [controller: 'address', action: 'create']

        render template: "/templates/cpa/addressFormModal", model: model
    }

    @Secured(['ROLE_USER'])
    def editAddress() {
        Map<String, Object> model = [:]
        model.addressInstance = Address.get(params.id)

        if (model.addressInstance){
            model.modalId = 'addressFormModal'
            String messageCode = 'person.address.label'
            model.typeId = model.addressInstance.type.id
            if(model.addressInstance.prs) {
                model.modalText = message(code: 'default.edit.label', args: [message(code: messageCode)]) + ' (' + model.addressInstance.prs.toString() + ')'
            }
            else if(model.addressInstance.org) {
                model.modalText = message(code: 'default.edit.label', args: [message(code: messageCode)]) + ' (' + model.addressInstance.org.toString() + ')'
            }
            else{
                model.modalText = message(code: 'default.edit.label', args: [message(code: messageCode)])
            }
            model.modalMsgSave = message(code: 'default.button.save_changes')
            model.url = [controller: 'address', action: 'edit']

            render template: "/templates/cpa/addressFormModal", model: model
        }
    }

    @Secured(['ROLE_USER'])
    def createPerson() {
        Map<String, Object> result = [:]
        result.contextOrg = contextService.getOrg()
        result.tenant = result.contextOrg
        result.modalId = 'personModal'
        result.presetFunctionType = RDStore.PRS_FUNC_GENERAL_CONTACT_PRS
        result.showContacts = params.showContacts == "true" ? true : ''
        result.addContacts = params.showContacts == "true" ? true : ''
        result.org = params.org ? Org.get(Long.parseLong(params.org)) : null
        result.functions = [RDStore.PRS_FUNC_GENERAL_CONTACT_PRS, RDStore.PRS_FUNC_CONTACT_PRS, RDStore.PRS_FUNC_FUNC_BILLING_ADDRESS, RDStore.PRS_FUNC_TECHNICAL_SUPPORT, RDStore.PRS_FUNC_RESPONSIBLE_ADMIN]
        if(result.contextOrg.getCustomerType() == 'ORG_CONSORTIUM'){
            result.functions << RDStore.PRS_FUNC_GASCO_CONTACT
        }
        result.positions = PersonRole.getAllRefdataValues(RDConstants.PERSON_POSITION) - [RDStore.PRS_POS_ACCOUNT, RDStore.PRS_POS_SD, RDStore.PRS_POS_SS]

        switch(params.contactFor) {
            case 'contactPersonForInstitution':
                result.isPublic = false
                if (result.org) {
                    result.modalText = message(code: "person.create_new.contactPersonForInstitution.label") + ' (' + result.org.toString() + ')'
                } else {
                    result.modalText = message(code: "person.create_new.contactPersonForInstitution.label")
                    result.orgList = Org.executeQuery("from Org o where exists (select roletype from o.orgType as roletype where roletype.id = :orgType ) and o.sector.id = :orgSector order by LOWER(o.sortname)", [orgSector: RDStore.O_SECTOR_HIGHER_EDU.id, orgType: RDStore.OT_INSTITUTION.id])
                }
                break
            case 'contactPersonForProviderAgency':
                result.isPublic    = false
                result.functions = PersonRole.getAllRefdataValues(RDConstants.PERSON_FUNCTION) - [RDStore.PRS_FUNC_GASCO_CONTACT, RDStore.PRS_FUNC_RESPONSIBLE_ADMIN, RDStore.PRS_FUNC_FUNC_LIBRARY_ADDRESS, RDStore.PRS_FUNC_FUNC_LEGAL_PATRON_ADDRESS, RDStore.PRS_FUNC_FUNC_POSTAL_ADDRESS, RDStore.PRS_FUNC_FUNC_BILLING_ADDRESS, RDStore.PRS_FUNC_FUNC_DELIVERY_ADDRESS]
                result.positions = [RDStore.PRS_POS_ACCOUNT, RDStore.PRS_POS_DIREKTION, RDStore.PRS_POS_DIREKTION_ASS, RDStore.PRS_POS_RB, RDStore.PRS_POS_SD, RDStore.PRS_POS_SS, RDStore.PRS_POS_TS]
                if (result.org) {
                    result.modalText = message(code: "person.create_new.contactPersonForProviderAgency.label") + ' (' + result.org.toString() + ')'
                } else {
                    result.modalText = message(code: "person.create_new.contactPersonForProviderAgency.label")
                    result.orgList = Org.executeQuery("from Org o where exists (select roletype from o.orgType as roletype where roletype.id in (:orgType) ) and o.sector.id = :orgSector order by LOWER(o.sortname)", [orgSector: RDStore.O_SECTOR_PUBLISHER.id, orgType: [RDStore.OT_PROVIDER.id, RDStore.OT_AGENCY.id]])
                }
                break
            case 'contactPersonForProviderAgencyPublic':
                result.contactPersonForProviderAgencyPublic = true
                result.isPublic    = true
                result.presetFunctionType = RDStore.PRS_FUNC_TECHNICAL_SUPPORT
                //result.functions = PersonRole.getAllRefdataValues(RDConstants.PERSON_FUNCTION) - [RDStore.PRS_FUNC_GASCO_CONTACT, RDStore.PRS_FUNC_RESPONSIBLE_ADMIN, RDStore.PRS_FUNC_FUNC_LIBRARY_ADDRESS, RDStore.PRS_FUNC_FUNC_LEGAL_PATRON_ADDRESS, RDStore.PRS_FUNC_FUNC_POSTAL_ADDRESS, RDStore.PRS_FUNC_FUNC_BILLING_ADDRESS, RDStore.PRS_FUNC_FUNC_DELIVERY_ADDRESS]
                //result.positions = [RDStore.PRS_POS_ACCOUNT, RDStore.PRS_POS_DIREKTION, RDStore.PRS_POS_DIREKTION_ASS, RDStore.PRS_POS_RB, RDStore.PRS_POS_SD, RDStore.PRS_POS_SS, RDStore.PRS_POS_TS]
                if(result.org){
                    result.modalText = message(code: "person.create_new.contactPersonForProviderAgency.label") + ' (' + result.org.toString() + ')'
                    result.tenant = result.org
                }else {
                    result.modalText = message(code: "person.create_new.contactPersonForProviderAgency.label")
                    result.orgList = result.orgList = Org.executeQuery("from Org o where exists (select roletype from o.orgType as roletype where roletype.id in (:orgType) ) and o.sector.id = :orgSector order by LOWER(o.sortname)", [orgSector: RDStore.O_SECTOR_PUBLISHER.id, orgType: [RDStore.OT_PROVIDER.id, RDStore.OT_AGENCY.id]])
                }

                break
            case 'contactPersonForPublic':
                result.isPublic    = true
                result.modalText = message(code: "person.create_new.contactPersonForPublic.label")
                break
        }
        result.url = [controller: 'person', action: 'create']


        render template: "/templates/cpa/personFormModal", model: result
    }

    @Secured(['ROLE_USER'])
    def editPerson() {
        Map<String, Object> result = [:]
        Org contextOrg = contextService.getOrg()
        result.personInstance = Person.get(params.id)

        if (result.personInstance){
            result.org = result.personInstance.getBelongsToOrg()
            result.functions = [RDStore.PRS_FUNC_GENERAL_CONTACT_PRS, RDStore.PRS_FUNC_CONTACT_PRS, RDStore.PRS_FUNC_FUNC_BILLING_ADDRESS, RDStore.PRS_FUNC_TECHNICAL_SUPPORT, RDStore.PRS_FUNC_RESPONSIBLE_ADMIN]
            if(contextOrg.getCustomerType() == 'ORG_CONSORTIUM'){
                result.functions << RDStore.PRS_FUNC_GASCO_CONTACT
            }
            result.positions = PersonRole.getAllRefdataValues(RDConstants.PERSON_POSITION) - [RDStore.PRS_POS_ACCOUNT, RDStore.PRS_POS_SD, RDStore.PRS_POS_SS]

            if (result.org || (params.org && params.org instanceof String)) {
                result.org = params.org ? Org.get(Long.parseLong(params.org)) : result.org
                List allOrgTypeIds =result.org.getAllOrgTypeIds()
                if(RDStore.OT_PROVIDER.id in allOrgTypeIds || RDStore.OT_AGENCY.id in allOrgTypeIds){
                    result.functions = PersonRole.getAllRefdataValues(RDConstants.PERSON_FUNCTION) - [RDStore.PRS_FUNC_GASCO_CONTACT, RDStore.PRS_FUNC_RESPONSIBLE_ADMIN, RDStore.PRS_FUNC_FUNC_LIBRARY_ADDRESS, RDStore.PRS_FUNC_FUNC_LEGAL_PATRON_ADDRESS, RDStore.PRS_FUNC_FUNC_POSTAL_ADDRESS, RDStore.PRS_FUNC_FUNC_BILLING_ADDRESS, RDStore.PRS_FUNC_FUNC_DELIVERY_ADDRESS]
                    result.positions = [RDStore.PRS_POS_ACCOUNT, RDStore.PRS_POS_DIREKTION, RDStore.PRS_POS_DIREKTION_ASS, RDStore.PRS_POS_RB, RDStore.PRS_POS_SD, RDStore.PRS_POS_SS, RDStore.PRS_POS_TS]
                    result.modalText = message(code: 'default.edit.label', args: [message(code: "person.contactPersonForProviderAgency.label")]) + ' (' + result.org.toString() + ')'
                    result.contactPersonForProviderAgencyPublic = result.personInstance.isPublic
                }else{
                    result.modalText = message(code: 'default.edit.label', args: [message(code: "person.contactPersonForInstitution.label")]) + ' (' + result.org.toString() + ')'
                }
            }else {
                result.modalText = message(code: 'default.edit.label', args: [message(code: 'person.label')])
            }

            result.modalId = 'personModal'
            result.modalMsgSave = message(code: 'default.button.save_changes')
            result.showContacts = params.showContacts == "true" ? true : ''
            result.addContacts = params.showContacts == "true" ? true : ''
            result.showAddresses = params.showAddresses == "true" ? true : ''
            result.addAddresses = params.showAddresses == "true" ? true : ''
            result.isPublic = result.personInstance.isPublic
            result.editable = addressbookService.isPersonEditable(result.personInstance, contextService.getUser())
            result.tmplShowDeleteButton = result.editable
            result.url = [controller: 'person', action: 'edit', id: result.personInstance.id]
            result.contextOrg = contextService.getOrg()

            render template: "/templates/cpa/personFormModal", model: result
        }
    }

    @Secured(['ROLE_USER'])
    def contactFields() {
        render template: "/templates/cpa/contactFields"
    }

    @Secured(['ROLE_USER'])
    def addressFields() {
        render template: "/templates/cpa/addressFields", model: [multipleAddresses: params.multipleAddresses]
    }
}