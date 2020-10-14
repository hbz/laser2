package de.laser


import de.laser.properties.PersonProperty
import de.laser.titles.TitleInstance
import com.k_int.kbplus.auth.User
import de.laser.controller.AbstractDebugController
import de.laser.helper.DebugAnnotation
import de.laser.helper.RDConstants
import de.laser.helper.RDStore
import de.laser.properties.PropertyDefinition
import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.annotation.Secured
import org.springframework.dao.DataIntegrityViolationException

@Secured(['IS_AUTHENTICATED_FULLY'])
class PersonController extends AbstractDebugController {

    def springSecurityService
    def addressbookService
    def genericOIDService
    def contextService
    def formService

    static allowedMethods = [create: ['GET', 'POST'], edit: ['GET', 'POST'], delete: 'POST']

    @Secured(['ROLE_USER'])
    def index() {
        redirect controller: 'myInstitution', action: 'addressbook'
    }

    @DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def create() {
        Org contextOrg = contextService.getOrg()
        List userMemberships = User.get(springSecurityService.principal.id).authorizedOrgs
        
        switch (request.method) {
		case 'GET':
            def personInstance = new Person(params)
            // processing dynamic form data
            addPersonRoles(personInstance)
            
        	[personInstance: personInstance, userMemberships: userMemberships]
			break
		case 'POST':
            if (formService.validateToken(params)) {

                def personInstance = new Person(params)
                if (!personInstance.save(flush: true)) {
                    flash.error = message(code: 'default.not.created.message', args: [message(code: 'person.label')])
                    log.debug("Person could not be created: "+personInstance.errors )
                    redirect(url: request.getHeader('referer'))
                    //render view: 'create', model: [personInstance: personInstance, userMemberships: userMemberships]
                    return
                }
                // processing dynamic form data
                //addPersonRoles(personInstance)
                Org personRoleOrg
                if(params.personRoleOrg)
                {
                    personRoleOrg = Org.get(params.personRoleOrg)
                }else {
                    personRoleOrg = contextOrg
                }


                if(params.functionType) {
                    params.list('functionType').each {
                        PersonRole personRole
                        RefdataValue functionType = RefdataValue.get(it)
                        personRole = new PersonRole(prs: personInstance, functionType: functionType, org: personRoleOrg)

                        if (PersonRole.findWhere(prs: personInstance, org:  personRoleOrg, functionType: functionType)) {
                            log.debug("ignore adding PersonRole because of existing duplicate")
                        }
                        else if (personRole) {
                            if (personRole.save()) {
                                log.debug("adding PersonRole ${personRole}")
                            }
                            else {
                                log.error("problem saving new PersonRole ${personRole}")
                            }
                        }
                    }
                }

                if(params.positionType) {
                    params.list('positionType').each {
                        PersonRole personRole
                        RefdataValue positionType = RefdataValue.get(it)
                        personRole = new PersonRole(prs: personInstance, positionType: positionType, org: personRoleOrg)

                        if (PersonRole.findWhere(prs: personInstance, org:  personRoleOrg, positionType: positionType)) {
                            log.debug("ignore adding PersonRole because of existing duplicate")
                        }
                        else if (personRole) {
                            if (personRole.save()) {
                                log.debug("adding PersonRole ${personRole}")
                            }
                            else {
                                log.error("problem saving new PersonRole ${personRole}")
                            }
                        }
                    }

                }

                if(params.content) {
                    params.list('content').eachWithIndex { content, i->
                        if (content) {
                            RefdataValue rdvCT = RefdataValue.get(params.list('contentType.id')[i])
                            RefdataValue rdvTY = RDStore.CONTACT_TYPE_JOBRELATED

                            if (RDStore.CCT_EMAIL == rdvCT) {
                                if (!formService.validateEmailAddress(content)) {
                                    flash.error = message(code: 'contact.create.email.error')
                                    return
                                }
                            }

                            Contact contact = new Contact(prs: personInstance, contentType: rdvCT, type: rdvTY, content: content)
                            contact.save()
                        }
                    }
                }

                if(params.multipleAddresses) {
                    params.list('multipleAddresses').eachWithIndex { name, i ->
                        Address addressInstance = new Address(
                                name: (1 == params.list('name').size()) ? params.name : params.name[i],
                                additionFirst:  (1 == params.list('additionFirst').size()) ? params.additionFirst : params.additionFirst[i],
                                additionSecond:  (1 == params.list('additionSecond').size()) ? params.additionSecond : params.additionSecond[i],
                                street_1:  (1 == params.list('street_1').size()) ? params.street_1 : params.street_1[i],
                                street_2:  (1 == params.list('street_2').size()) ? params.street_2 : params.street_2[i],
                                zipcode:  (1 == params.list('zipcode').size()) ? params.zipcode : params.zipcode[i],
                                city:  (1 == params.list('city').size()) ? params.city : params.city[i],
                                region:  (1 == params.list('region').size()) ? params.region : params.region[i],
                                country:  (1 ==params.list('country').size()) ? params.country : params.country[i],
                                pob:  (1 == params.list('pob').size()) ? params.pob : params.pob[i],
                                pobZipcode:  (1 == params.list('pobZipcode').size()) ? params.pobZipcode : params.pobZipcode[i],
                                pobCity:  (1 == params.list('pobCity').size()) ? params.pobCity : params.pobCity[i],
                                prs: personInstance)

                        params.list('type').each {
                            if(!(it in addressInstance.type))
                            {
                                addressInstance.addToType(RefdataValue.get(Long.parseLong(it)))
                            }
                        }
                        if (!addressInstance.save()) {
                            flash.error = message(code: 'default.save.error.general.message')
                            log.error('Adresse konnte nicht gespeichert werden. ' + addressInstance.errors)
                            redirect(url: request.getHeader('referer'), params: params)
                            return
                        }
                    }
                }

                /*['contact1', 'contact2', 'contact3'].each { c ->
                    if (params."${c}_contentType" && params."${c}_type" && params."${c}_content") {

                        RefdataValue rdvCT = RefdataValue.get(params."${c}_contentType")
                        RefdataValue rdvTY = RefdataValue.get(params."${c}_type")

                        if(RDStore.CCT_EMAIL == rdvCT){
                            if ( !formService.validateEmailAddress(params."${c}_content") ) {
                                flash.error = message(code:'contact.create.email.error')
                                return
                            }
                        }

                        Contact contact = new Contact(prs: personInstance, contentType: rdvCT, type: rdvTY, content: params."${c}_content")
                        contact.save(flush: true)
                    }
                }*/

                flash.message = message(code: 'default.created.message', args: [message(code: 'person.label'), personInstance.toString()])
            }
            redirect(url: request.getHeader('referer'))
			break
		}
    }

    @Secured(['ROLE_USER'])
    Map<String,Object> show() {
        Person personInstance = Person.get(params.id)
        Org contextOrg      = contextService.org
        if (! personInstance) {
			flash.message = message(code: 'default.not.found.message', args: [message(code: 'person.label'), params.id])
            //redirect action: 'list'
            redirect(url: request.getHeader('referer'))
            return
        }
        else if(personInstance && ! personInstance.isPublic) {
            if(contextOrg.id != personInstance.tenant?.id && !SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN')) {
                flash.error = message(code: 'default.notAutorized.message')
                redirect(url: request.getHeader('referer'))
                return
            }
        }

        //boolean myPublicContact = false
        //if(personInstance.isPublic == RDStore.YN_YES && personInstance.tenant == contextService.org && !personInstance.roleLinks)
        //    myPublicContact = true

        boolean myPublicContact = false // TODO: check

        List<PersonRole> gcp = PersonRole.where {
            prs == personInstance &&
            functionType == RefdataValue.getByValueAndCategory('General contact person', RDConstants.PERSON_FUNCTION)
        }.findAll()
        List<PersonRole> fcba = PersonRole.where {
            prs == personInstance &&
            functionType == RefdataValue.getByValueAndCategory('Functional Contact Billing Adress', RDConstants.PERSON_FUNCTION)
        }.findAll()
        

        Map<String,Object> result = [
                institution: contextOrg,
                personInstance: personInstance,
                presetOrg: gcp.size() == 1 ? gcp.first().org : fcba.size() == 1 ? fcba.first().org : personInstance.tenant,
                editable: addressbookService.isPersonEditable(personInstance, springSecurityService.getCurrentUser()),
                myPublicContact: myPublicContact,
                contextOrg: contextOrg
        ]

        result
    }

    @DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def edit() {
        //redirect controller: 'person', action: 'show', params: params
        //return // ----- deprecated

        Org contextOrg = contextService.getOrg()

        Person personInstance = Person.get(params.id)

        if (! personInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'person.label'), params.id])
            redirect(url: request.getHeader('referer'))
            return
        }
        if (! addressbookService.isPersonEditable(personInstance, springSecurityService.getCurrentUser())) {
            flash.error = message(code: 'default.notAutorized.message')
            redirect(url: request.getHeader('referer'))
            return
        }

	        personInstance.properties = params

	        if (!personInstance.save()) {
                log.info(personInstance.errors)
                flash.error = message(code: 'default.not.updated.message', args: [message(code: 'person.label'), personInstance.toString()])
                redirect(url: request.getHeader('referer'))
	            return
	        }

        Org personRoleOrg
        if(params.personRoleOrg)
        {
            personRoleOrg = Org.get(params.personRoleOrg)
        }else {
            personRoleOrg = contextOrg
        }

        if(params.functionType) {
            params.list('functionType').each {
                PersonRole personRole
                RefdataValue functionType = RefdataValue.get(it)
                personRole = new PersonRole(prs: personInstance, functionType: functionType, org: personRoleOrg)

                if (PersonRole.findWhere(prs: personInstance, org:  personRoleOrg, functionType: functionType)) {
                    log.debug("ignore adding PersonRole because of existing duplicate")
                }
                else if (personRole) {
                    if (personRole.save()) {
                        log.debug("adding PersonRole ${personRole}")
                    }
                    else {
                        log.error("problem saving new PersonRole ${personRole}")
                    }
                }
            }

            personInstance.getPersonRoleByOrg(contextOrg).each {psr ->
                if(psr.functionType && !(psr.functionType.id.toString() in params.list('functionType'))){
                    personInstance.removeFromRoleLinks(psr)
                    psr.delete()
                }
            }

        }

        if(params.positionType) {
            params.list('positionType').each {
                PersonRole personRole
                RefdataValue positionType = RefdataValue.get(it)
                personRole = new PersonRole(prs: personInstance, positionType: positionType, org: personRoleOrg)

                if (PersonRole.findWhere(prs: personInstance, org:  personRoleOrg, positionType: positionType)) {
                    log.debug("ignore adding PersonRole because of existing duplicate")
                }
                else if (personRole) {
                    if (personRole.save()) {
                        log.debug("adding PersonRole ${personRole}")
                    }
                    else {
                        log.error("problem saving new PersonRole ${personRole}")
                    }
                }
            }

            personInstance.getPersonRoleByOrg(contextOrg).each {psr ->
                if(psr.positionType && !(psr.positionType.id.toString() in params.list('positionType'))){
                    personInstance.removeFromRoleLinks(psr)
                    psr.delete()
                }
            }

        }

        personInstance.contacts.each {contact ->
            if(params."content${contact.id}"){

                contact.content = params."content${contact.id}"
                contact.save()
            }
        }

        if(params.content) {
            params.list('content').eachWithIndex { content, i->
                if (content) {
                    RefdataValue rdvCT = RefdataValue.get(params.list('contentType.id')[i])
                    RefdataValue rdvTY = RDStore.CONTACT_TYPE_JOBRELATED

                    if (RDStore.CCT_EMAIL == rdvCT) {
                        if (!formService.validateEmailAddress(content)) {
                            flash.error = message(code: 'contact.create.email.error')
                            return
                        }
                    }

                    Contact contact = new Contact(prs: personInstance, contentType: rdvCT, type: rdvTY, content: content)
                    contact.save()
                }
            }
        }

        if(params.multipleAddresses) {
            params.list('multipleAddresses').eachWithIndex { name, i ->
                Address addressInstance = new Address(
                        name: (1 == params.list('name').size()) ? params.name : params.name[i],
                        additionFirst:  (1 == params.list('additionFirst').size()) ? params.additionFirst : params.additionFirst[i],
                        additionSecond:  (1 == params.list('additionSecond').size()) ? params.additionSecond : params.additionSecond[i],
                        street_1:  (1 == params.list('street_1').size()) ? params.street_1 : params.street_1[i],
                        street_2:  (1 == params.list('street_2').size()) ? params.street_2 : params.street_2[i],
                        zipcode:  (1 == params.list('zipcode').size()) ? params.zipcode : params.zipcode[i],
                        city:  (1 == params.list('city').size()) ? params.city : params.city[i],
                        region:  (1 == params.list('region').size()) ? params.region : params.region[i],
                        country:  (1 ==params.list('country').size()) ? params.country : params.country[i],
                        pob:  (1 == params.list('pob').size()) ? params.pob : params.pob[i],
                        pobZipcode:  (1 == params.list('pobZipcode').size()) ? params.pobZipcode : params.pobZipcode[i],
                        pobCity:  (1 == params.list('pobCity').size()) ? params.pobCity : params.pobCity[i],
                        prs: personInstance)

                params.list('type').each {
                    if(!(it in addressInstance.type))
                    {
                        addressInstance.addToType(RefdataValue.get(Long.parseLong(it)))
                    }
                }
                if (!addressInstance.save()) {
                    flash.error = message(code: 'default.save.error.general.message')
                    log.error('Adresse konnte nicht gespeichert werden. ' + addressInstance.errors)
                    redirect(url: request.getHeader('referer'), params: params)
                    return
                }
            }
        }


		flash.message = message(code: 'default.updated.message', args: [message(code: 'person.label'), personInstance.toString()])
        redirect(url: request.getHeader('referer'))

    }

    @DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def _delete() {
        def personInstance = Person.get(params.id)
        if (! personInstance) {
			flash.message = message(code: 'default.not.found.message', args: [message(code: 'person.label'), params.id])
            String referer = request.getHeader('referer')
            if (referer.endsWith('person/show/'+params.id)) {
                if (params.previousReferer && ! params.previousReferer.endsWith('person/show/'+params.id)){
                    redirect(url: params.previousReferer)
                } else {
                    redirect controller: 'myInstitution', action: 'addressbook'
                }
            } else {
                //redirect action: 'list'
                redirect(url: request.getHeader('referer'))
            }
            return
        }
        if (! addressbookService.isPersonEditable(personInstance, springSecurityService.getCurrentUser())) {
            redirect action: 'show', id: params.id
            return
        }

        try {
            personInstance.delete(flush: true)
			flash.message = message(code: 'default.deleted.message', args: [message(code: 'person.label'), params.id])
            String referer = request.getHeader('referer')
            if (referer.endsWith('person/show/'+params.id)) {
                if (params.previousReferer && ! params.previousReferer.endsWith('person/show/'+params.id)){
                    redirect(url: params.previousReferer)
                } else {
                    redirect controller: 'myInstitution', action: 'addressbook'
                }
            } else {
//                redirect action: 'list'
                redirect(url: referer)
            }
        } catch (DataIntegrityViolationException e) {
 			flash.message = message(code: 'default.not.deleted.message', args: [message(code: 'person.label'), params.id])
            redirect action: 'show', id: params.id
        }
    }
    
    @Secured(['ROLE_USER'])
    def properties() {
        def personInstance = Person.get(params.id)
        if (!personInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'person.label'), params.id])
            //redirect action: 'list'
            redirect(url: request.getHeader('referer'))
            return
        }
        
        Org org = contextService.getOrg()

        boolean editable = true

        // create mandatory PersonPrivateProperties if not existing

        List<PropertyDefinition> mandatories = PropertyDefinition.getAllByDescrAndMandatoryAndTenant(PropertyDefinition.PRS_PROP, true, org)

        mandatories.each{ PropertyDefinition pd ->
            if (! PersonProperty.findWhere(owner: personInstance, type: pd)) {
                def newProp = PropertyDefinition.createGenericProperty(PropertyDefinition.PRIVATE_PROPERTY, personInstance, pd, org)

                if (newProp.hasErrors()) {
                    log.error(newProp.errors.toString())
                } else {
                    log.debug("New person private property created via mandatory: " + newProp.type.name)
                }
            }
        }

        [personInstance: personInstance, editable: editable]
    }

    @Secured(['ROLE_USER'])
    def getPossibleTenantsAsJson() {
        def result = []

        Person person = genericOIDService.resolveOID(params.oid)

        List<Org> orgs = person.roleLinks?.collect{ it.org }
        orgs.add(person.tenant)
        orgs.add(contextService.getOrg())

        orgs.unique().each { o ->
            result.add([value: "${o.class.name}:${o.id}", text: "${o.toString()}"])
        }

        render result as JSON
    }

    @Deprecated
    @Secured(['ROLE_USER'])
    def ajax() {        
        def person                  = Person.get(params.id)
        def existingPrsLinks
        
        def allSubjects             // all subjects of given type
        def subjectType             // type of subject
        def subjectFormOptionValue
        
        def cmd      = params.cmd
        def roleType = params.roleType
        
        // requesting form for deleting existing personRoles
        if('list' == cmd){ 
            
            if(person){
                if('func' == roleType){
                    RefdataCategory rdc = RefdataCategory.getByDesc(RDConstants.PERSON_FUNCTION)
                    String hqlPart = "from PersonRole as PR where PR.prs = ${person?.id} and PR.functionType.owner = ${rdc.id}"
                    existingPrsLinks = PersonRole.findAll(hqlPart) 
                }
                else if('resp' == roleType){
                    RefdataCategory rdc = RefdataCategory.getByDesc(RDConstants.PERSON_RESPONSIBILITY)
                    String hqlPart = "from PersonRole as PR where PR.prs = ${person?.id} and PR.responsibilityType.owner = ${rdc.id}"
                    existingPrsLinks = PersonRole.findAll(hqlPart)
                }

                render view: 'ajax/listPersonRoles', model: [
                    existingPrsLinks: existingPrsLinks
                ]
                return
            }
            else {
                render "No Data found."
                return
            }
        }
        
        // requesting form for adding new personRoles
        else if('add' == cmd){

            RefdataValue roleRdv = RefdataValue.get(params.roleTypeId)

            if('func' == roleType){
                
                // only one rdv of person function
            }
            else if('resp' == roleType){
                
                if(roleRdv?.value == "Specific license editor") {
                    allSubjects             = License.getAll()
                    subjectType             = "license"
                    subjectFormOptionValue  = "reference"
                }
                else if(roleRdv?.value == "Specific package editor") {
                    allSubjects             = Package.getAll()
                    subjectType             = "package"
                    subjectFormOptionValue  = "name"
                }
                else if(roleRdv?.value == "Specific subscription editor") {
                    allSubjects             = Subscription.getAll()
                    subjectType             = "subscription"
                    subjectFormOptionValue  = "name"
                }
                else if(roleRdv?.value == "Specific title editor") {
                    allSubjects             = TitleInstance.getAll()
                    subjectType             = "titleInstance"
                    subjectFormOptionValue  = "normTitle"
                }
            }
            
            render view: 'ajax/addPersonRole', model: [
                personInstance:     person,
                allOrgs:            Org.getAll(),
                allSubjects:        allSubjects,
                subjectType:        subjectType,
                subjectOptionValue: subjectFormOptionValue,
                existingPrsLinks:   existingPrsLinks,
                roleType:           roleType,
                roleRdv:            roleRdv,
                org:                Org.get(params.org),        // through passing for g:select value
                timestamp:          System.currentTimeMillis()
                ]
            return
        }
    }

    @Deprecated
    private deletePersonRoles(Person prs){

        params?.personRoleDeleteIds?.each{ key, value ->
             def prsRole = PersonRole.get(value)
             if(prsRole) {
                 log.debug("deleting PersonRole ${prsRole}")
                 prsRole.delete(flush: true)
             }
        }
    }


    def addPersonRole() {
        PersonRole result
        Person prs = Person.get(params.id)

        if (addressbookService.isPersonEditable(prs, springSecurityService.getCurrentUser())) {

            if (params.newPrsRoleOrg && params.newPrsRoleType) {
                Org org = Org.get(params.newPrsRoleOrg)
                RefdataValue rdv = RefdataValue.get(params.newPrsRoleType)

                def prAttr = params.roleType ?: PersonRole.TYPE_FUNCTION

                if (prAttr in [PersonRole.TYPE_FUNCTION, PersonRole.TYPE_POSITION]) {

                    String query = "from PersonRole as PR where PR.prs = ${prs.id} and PR.org = ${org.id} and PR.${prAttr} = ${rdv.id}"
                    if (PersonRole.find( query )) {
                        log.debug("ignore adding PersonRole because of existing duplicate")
                    }
                    else {
                        result = new PersonRole(prs: prs, org: org)
                        result."${prAttr}" = rdv

                        if (result.save(flush: true)) {
                            log.debug("adding PersonRole ${result}")
                        }
                        else {
                            log.error("problem saving new PersonRole ${result}")
                        }
                    }
                }
            }
        }

        if (params.redirect) {
            redirect(url: request.getHeader('referer'), params: params)
        } else {
            redirect action: 'show', id: params.id
        }
    }

    def deletePersonRole() {
        Person prs = Person.get(params.id)

        if (addressbookService.isPersonEditable(prs, springSecurityService.getCurrentUser())) {

            if (params.oid) {
                def pr = genericOIDService.resolveOID(params.oid)

                if (pr && (pr.prs.id == prs.id) && pr.delete(flush: true)) {
                    log.debug("deleted PersonRole ${pr}")
                } else {
                    log.debug("problem deleting PersonRole ${pr}")
                }
            }
        }
        redirect action: 'show', id: params.id
    }

    @Deprecated
    private addPersonRoles(Person prs){

        if (params.functionType) {
            PersonRole result

            RefdataValue functionRdv = RefdataValue.get(params.functionType) ?: RefdataValue.getByValueAndCategory('General contact person', RDConstants.PERSON_FUNCTION)
            Org functionOrg = Org.get(params.functionOrg)

            if (functionRdv && functionOrg) {
                result = new PersonRole(prs: prs, functionType: functionRdv, org: functionOrg)

                String query = "from PersonRole as PR where PR.prs = ${prs.id} and PR.org = ${functionOrg.id} and PR.functionType = ${functionRdv.id}"
                if (PersonRole.find( query )) {
                    log.debug("ignore adding PersonRole because of existing duplicate")
                }
                else if (result) {
                    if (result.save()) {
                        log.debug("adding PersonRole ${result}")
                    }
                    else {
                        log.error("problem saving new PersonRole ${result}")
                    }
                }
            }

            def positionRdv = params.positionType ? RefdataValue.get(params.positionType) : null
            def positionOrg = Org.get(params.positionOrg)

            if (positionRdv && positionOrg) {
                result = new PersonRole(prs: prs, positionType: positionRdv, org: positionOrg)

                String query = "from PersonRole as PR where PR.prs = ${prs.id} and PR.org = ${positionOrg.id} and PR.positionType = ${positionRdv.id}"
                if (PersonRole.find( query )) {
                    log.debug("ignore adding PersonRole because of existing duplicate")
                }
                else if (result) {
                    if (result.save(flush: true)) {
                        log.debug("adding PersonRole ${result}")
                    }
                    else {
                        log.error("problem saving new PersonRole ${result}")
                    }
                }
            }
        }

        //@Deprecated
       params?.responsibilityType?.each{ key, value ->
           PersonRole result

           RefdataValue roleRdv = RefdataValue.get(params.responsibilityType[key])
           Org org              = Org.get(params.org[key])

           if (roleRdv && org) {
               def subject      // dynamic
               def subjectType = params.subjectType[key]

               switch (subjectType) {
                   case "license":
                       if (params.license) {
                           subject = License.get(params.license[key])
                           result = new PersonRole(prs: prs, responsibilityType: roleRdv, org: org, lic: subject)
                       }
                       break;
                   case "package":
                       if (params.package) {
                           subject = Package.get(params.package[key])
                           result = new PersonRole(prs: prs, responsibilityType: roleRdv, org: org, pkg: subject)
                       }
                       break;
                   case "subscription":
                       if (params.subscription) {
                           subject = Subscription.get(params.subscription[key])
                           result = new PersonRole(prs: prs, responsibilityType: roleRdv, org: org, sub: subject)
                       }
                       break;
                   case "titleInstance":
                       if (params.titleInstance) {
                           subject = TitleInstance.get(params.titleInstance[key])
                           result = new PersonRole(prs: prs, responsibilityType: roleRdv, org: org, title: subject)
                       }
                       break;
               }
           }

           // TODO duplicate check
           /* if(PersonRole.find("from PersonRole as PR where PR.prs = ${prs.id} and PR.org = ${org.id} and PR.responsibilityType = ${roleRdv.id} and PR.${typeTODOHERE} = ${subject.id}")) {
               log.debug("ignore adding PersonRole because of existing duplicate")
           }
           else */ if (result) {
               if (result.save(flush:true)) {
                   log.debug("adding PersonRole ${result}")
               }
               else {
                   log.error("problem saving new PersonRole ${result}")
               }
           }
       }
    }
}
