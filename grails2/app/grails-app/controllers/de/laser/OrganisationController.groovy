package de.laser


import de.laser.properties.OrgProperty
import com.k_int.kbplus.auth.Role
import com.k_int.kbplus.auth.User
import com.k_int.kbplus.auth.UserOrg
import de.laser.properties.PropertyDefinition
import de.laser.controller.AbstractDebugController
import de.laser.helper.*
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.annotation.Secured
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsHibernateUtil

import javax.servlet.ServletOutputStream
import java.text.SimpleDateFormat

@Secured(['IS_AUTHENTICATED_FULLY'])
class OrganisationController extends AbstractDebugController {

    def springSecurityService
    def accessService
    def contextService
    def addressbookService
    def filterService
    def genericOIDService
    def propertyService
    def docstoreService
    def instAdmService
    def organisationService
    def deletionService
    def userService
    def accessPointService
    FormService formService
    TaskService taskService

    @Secured(['ROLE_ORG_EDITOR','ROLE_ADMIN'])
    def index() {
        redirect action: 'list', params: params
    }

    @DebugAnnotation(perm="FAKE,ORG_BASIC_MEMBER,ORG_CONSORTIUM", affil="INST_ADM", specRole="ROLE_ADMIN,ROLE_ORG_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("FAKE,ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_ADM", "ROLE_ADMIN,ROLE_ORG_EDITOR")
    })
    def settings() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (! result.orgInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'org.label'), params.id])
            redirect action: 'list'
            return
        }

        Boolean isComboRelated = Combo.findByFromOrgAndToOrg(result.orgInstance, result.institution)
        result.isComboRelated = isComboRelated
        result.contextOrg = result.institution //for the properties template

        Boolean hasAccess = (result.inContextOrg && accessService.checkMinUserOrgRole(result.user, result.orgInstance, 'INST_ADM')) ||
                (isComboRelated && accessService.checkMinUserOrgRole(result.user, result.institution, 'INST_ADM')) ||
                SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN,ROLE_ORG_EDITOR')

        // forbidden access
        if (! hasAccess) {
            redirect controller: 'organisation', action: 'show', id: org.id
        }

        // adding default settings
        organisationService.initMandatorySettings(result.orgInstance)

        // collecting visible settings by customer type, role and/or combo
        List<OrgSetting> allSettings = OrgSetting.findAllByOrg(result.orgInstance)

        List<OrgSetting.KEYS> ownerSet = [
                OrgSetting.KEYS.API_LEVEL,
                OrgSetting.KEYS.API_KEY,
                OrgSetting.KEYS.API_PASSWORD,
                OrgSetting.KEYS.CUSTOMER_TYPE,
                OrgSetting.KEYS.GASCO_ENTRY
        ]
        List<OrgSetting.KEYS> accessSet = [
                OrgSetting.KEYS.OAMONITOR_SERVER_ACCESS,
                OrgSetting.KEYS.NATSTAT_SERVER_ACCESS
        ]
        List<OrgSetting.KEYS> credentialsSet = [
                OrgSetting.KEYS.NATSTAT_SERVER_API_KEY,
                OrgSetting.KEYS.NATSTAT_SERVER_REQUESTOR_ID
        ]

        result.settings = []

        if (SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN,ROLE_ORG_EDITOR')) {
            result.settings.addAll(allSettings.findAll { it.key in ownerSet })
            result.settings.addAll(allSettings.findAll { it.key in accessSet })
            result.settings.addAll(allSettings.findAll { it.key in credentialsSet })
        }
        else if (result.inContextOrg) {
            log.debug( 'settings for own org')
            result.settings.addAll(allSettings.findAll { it.key in ownerSet })

            if (result.institution.hasPerm('ORG_CONSORTIUM,ORG_INST')) {
                result.settings.addAll(allSettings.findAll { it.key in accessSet })
                result.settings.addAll(allSettings.findAll { it.key in credentialsSet })
            }
            else if (['ORG_BASIC_MEMBER'].contains(result.institution.getCustomerType())) {
                result.settings.addAll(allSettings.findAll { it.key == OrgSetting.KEYS.NATSTAT_SERVER_ACCESS })
            }
            else if (['FAKE'].contains(result.institution.getCustomerType())) {
                result.settings.addAll(allSettings.findAll { it.key == OrgSetting.KEYS.NATSTAT_SERVER_ACCESS })
            }
        }

//        result.allPlatforms = Platform.executeQuery('select p from Platform p join p.org o where p.org is not null order by o.name, o.sortname, p.name')
        result
    }

    @Secured(['ROLE_ORG_EDITOR','ROLE_ADMIN'])
    def list() {

        Map<String, Object> result = [:]
        result.user = User.get(springSecurityService.principal.id)
        result.max  = params.max ? Long.parseLong(params.max) : result.user?.getDefaultPageSize()
        result.offset = params.offset ? Long.parseLong(params.offset) : 0
        params.sort = params.sort ?: " LOWER(o.shortname), LOWER(o.name)"

        result.editable = SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN,ROLE_ORG_EDITOR')

        def fsq = filterService.getOrgQuery(params)
        result.filterSet = params.filterSet ? true : false

        List orgListTotal  = Org.findAll(fsq.query, fsq.queryParams)
        result.orgListTotal = orgListTotal.size()
        result.orgList = orgListTotal.drop((int) result.offset).take((int) result.max)

        SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
        String datetoday = sdf.format(new Date(System.currentTimeMillis()))
        def message = message(code: 'export.all.orgs')
        // Write the output to a file
        String file = message+"_${datetoday}"
        if ( params.exportXLS ) {

            try {
                SXSSFWorkbook wb = (SXSSFWorkbook) organisationService.exportOrg(orgListTotal, message, true,'xls')

                response.setHeader "Content-disposition", "attachment; filename=\"${file}.xlsx\""
                response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                wb.write(response.outputStream)
                response.outputStream.flush()
                response.outputStream.close()
                wb.dispose()

            }
            catch (Exception e) {
                log.error("Problem",e);
                response.sendError(500)
            }
        }
        else {
            withFormat {
                html {
                    result
                }
                csv {
                    response.setHeader("Content-disposition", "attachment; filename=\"${file}.csv\"")
                    response.contentType = "text/csv"
                    ServletOutputStream out = response.outputStream
                    out.withWriter { writer ->
                        writer.write((String) organisationService.exportOrg(orgListTotal,message,true,"csv"))
                    }
                    out.close()
                }
            }
        }
    }

    @DebugAnnotation(perm="ORG_CONSORTIUM", type="Consortium", affil="INST_USER")
    @Secured(closure = {
        ctx.accessService.checkPermTypeAffiliation("ORG_CONSORTIUM", "Consortium", "INST_USER")
    })
    Map listInstitution() {
        Map<String, Object> result = setResultGenericsAndCheckAccess()
        params.orgType   = RDStore.OT_INSTITUTION.id.toString()
        params.orgSector = RDStore.O_SECTOR_HIGHER_EDU.id.toString()
        if(!params.sort)
            params.sort = " LOWER(o.sortname)"
        def fsq = filterService.getOrgQuery(params)
        result.availableOrgs = Org.executeQuery(fsq.query, fsq.queryParams, params)
        result.consortiaMemberIds = []
        Combo.findAllWhere(
                toOrg: result.institution,
                type:    RefdataValue.getByValueAndCategory('Consortium', RDConstants.COMBO_TYPE)
        ).each { cmb ->
            result.consortiaMemberIds << cmb.fromOrg.id
        }

        result.consortiaMemberTotal = result.availableOrgs.size()

        result
    }

    @Secured(['ROLE_USER'])
    def listProvider() {
        Map<String, Object> result = [:]
        result.propList    = PropertyDefinition.findAllPublicAndPrivateOrgProp(contextService.getOrg())
        result.user        = User.get(springSecurityService.principal.id)
        result.editable    = SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN,ROLE_ORG_EDITOR') || accessService.checkConstraint_ORG_COM_EDITOR()

        params.orgSector    = RDStore.O_SECTOR_PUBLISHER?.id?.toString()
        params.orgType      = RDStore.OT_PROVIDER?.id?.toString()
        params.sort        = params.sort ?: " LOWER(o.shortname), LOWER(o.name)"

        def fsq            = filterService.getOrgQuery(params)
        result.filterSet = params.filterSet ? true : false

        if (params.filterPropDef) {
            def orgIdList = Org.executeQuery("select o.id ${fsq.query}", fsq.queryParams)
            params.constraint_orgIds = orgIdList
            fsq = filterService.getOrgQuery(params)
            fsq = propertyService.evalFilterQuery(params, fsq.query, 'o', fsq.queryParams)
        }
        result.max          = params.max ? Integer.parseInt(params.max) : result.user?.getDefaultPageSizeAsInteger()
        result.offset       = params.offset ? Integer.parseInt(params.offset) : 0
        List orgListTotal   = Org.findAll(fsq.query, fsq.queryParams)
        result.orgListTotal = orgListTotal.size()
        result.orgList      = orgListTotal.drop((int) result.offset).take((int) result.max)

        def message = g.message(code: 'export.all.providers')
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        String datetoday = sdf.format(new Date(System.currentTimeMillis()))
        String filename = message+"_${datetoday}"

        if ( params.exportXLS) {
            params.remove('max')
            try {
                SXSSFWorkbook wb = (SXSSFWorkbook) organisationService.exportOrg(orgListTotal, message, false, "xls")
                // Write the output to a file

                response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
                response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                wb.write(response.outputStream)
                response.outputStream.flush()
                response.outputStream.close()
                wb.dispose()

                return
            }
            catch (Exception e) {
                log.error("Problem",e);
                response.sendError(500)
            }
        }
        withFormat {
            html {
                result
            }
            csv {
                response.setHeader("Content-disposition", "attachment; filename=\"${filename}.csv\"")
                response.contentType = "text/csv"
                ServletOutputStream out = response.outputStream
                out.withWriter { writer ->
                    writer.write((String) organisationService.exportOrg(orgListTotal,message,true,"csv"))
                }
                out.close()
            }
        }
    }
    def createIdentifier(){
        log.debug("OrganisationController::createIdentifier ${params}")
        Org org   = params.id? Org.get(params.id) : null

        if (! org) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'org.label'), params.id])
            redirect(url: request.getHeader('referer'))
            return
        }
        //                List<IdentifierNamespace> nsList = IdentifierNamespace.where{(nsType == de.laser.Org.class.name || nsType == null)}
        List<IdentifierNamespace> nsList = IdentifierNamespace.where{(nsType == Org.class.name)}
                .list(sort: 'ns')
                .sort { a, b ->
            String aVal = a.getI10n('name') ?: a.ns
            String bVal = b.getI10n('name') ?: b.ns
            aVal.compareToIgnoreCase bVal
        }
        .collect{ it }
        nsList = nsList - [IdentifierNamespace.findByNs(IdentifierNamespace.LEIT_ID), IdentifierNamespace.findByNs(IdentifierNamespace.LEIT_KR)]
        render template: '/templates/identifier/modal_create', model: [orgInstance: org, nsList: nsList]
    }

    def editIdentifier(){
        log.debug("OrganisationController::editIdentifier ${params}")
        Identifier identifier = Identifier.get(params.identifier)
        Org org = identifier?.org

        if (! identifier) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'default.search.identifier'), params.identifier])
            redirect(url: request.getHeader('referer'))
            return
        }

        render template: '/templates/identifier/modal_create', model: [orgInstance: org, identifier: identifier]
    }

    def processCreateIdentifier(){
        log.debug("OrganisationController::processCreateIdentifier ${params}")
        Org org   = params.orgid ? Org.get(params.orgid) : null
        if ( ! (org && params.ns.id)){
            flash.error = message(code: 'menu.admin.error')
            redirect(url: request.getHeader('referer'))
            return
        }
        IdentifierNamespace namespace   = IdentifierNamespace.get(params.ns.id)
        if (!namespace){
            flash.error = message(code: 'default.not.found.message', args: [message(code: 'identifier.namespace.label'), params.ns.id])
            redirect(url: request.getHeader('referer'))
            return
        }
        if ( ! params.value){
            flash.error = message(code: 'identifier.create.err.missingvalue', args: [namespace.getI10n('name') ?: namespace.ns])
            redirect(url: request.getHeader('referer'))
            return
        }
        String value = params.value.trim()
        String note = params.note?.trim()
        Identifier id = Identifier.construct([value: value, reference: org, namespace: namespace])
        id.note = note
        id.save(flush:true)

        redirect(url: request.getHeader('referer'))
    }
    def processCreateCustomerIdentifier(){
        log.debug("OrganisationController::processCreateCustomerIdentifier ${params}")
        Org org   = params.orgid ? Org.get(params.orgid) : null
        if ( ! (org && params.addCIPlatform)){
            flash.error = message(code: 'menu.admin.error')
            redirect(url: request.getHeader('referer'))
            return
        }
        Platform plt = Platform.get(params.addCIPlatform)
        if (!plt){
            flash.error = message(code: 'default.not.found.message', args: [message(code: 'default.provider.platform.label'), params.addCIPlatform])
            redirect(url: request.getHeader('referer'))
            return
        }
        if ( ! params.value){
            String p = plt.org.name + (plt.org.sortname ? " (${plt.org.sortname})" : '') + ' : ' + plt.name
            flash.error = message(code: 'org.customerIdentifier.create.err.missingvalue', args: [p])
            redirect(url: request.getHeader('referer'))
            return
        }
        if (plt) {
            CustomerIdentifier ci = new CustomerIdentifier(
                    customer: org,
                    platform: plt,
                    value: params.value.trim(),
                    note: params.note?.trim(),
                    owner: contextService.getOrg(),
                    isPublic: true,
                    type: RefdataValue.getByValueAndCategory('Default', RDConstants.CUSTOMER_IDENTIFIER_TYPE)
            )
            ci.save(flush:true)
        }

        redirect(url: request.getHeader('referer'))
    }

    def processEditIdentifier(){
        log.debug("OrganisationController::processEditIdentifier ${params}")
        Identifier identifier   = Identifier.get(params.identifierId)
        if ( ! identifier){
            flash.error = message(code: 'default.not.found.message', args: [message(code: 'default.search.identifier'), params.identifierId])
            redirect(url: request.getHeader('referer'))
            return
        }

        if(identifier.ns.ns == IdentifierNamespace.LEIT_ID && params.leitID1 && params.leitID3){
            String leitID1
            String leitID2
            String leitID3

            if(params.leitID1 ==~ /[0-9]{2,12}/) {
                leitID1 = params.leitID1
            }else{
                flash.error = message(code: 'identifier.edit.err.leitID', args: [message(code: 'identifier.leitID.leitID1.info')])
                redirect(url: request.getHeader('referer'))
                return
            }

            if(params.leitID2 ==~ /[a-z0-9]{0,30}/) {
                leitID2 = params.leitID2
            }else{
                flash.error = message(code: 'identifier.edit.err.leitID', args: [message(code: 'identifier.leitID.leitID2.info')])
                redirect(url: request.getHeader('referer'))
                return
            }

            if(params.leitID3 ==~ /[0-9]{2,2}/) {
                leitID3 = params.leitID3
            }else{
                flash.error = message(code: 'identifier.edit.err.leitID', args: [message(code: 'identifier.leitID.leitID3.info')])
                redirect(url: request.getHeader('referer'))
                return
            }

            params.value = leitID1 + '-' + (leitID2 ? leitID2 + '-' : '') + leitID3
        }

        if ( ! params.value){
            flash.error = message(code: 'identifier.edit.err.missingvalue', args: [identifier.ns?.getI10n('name') ?: identifier.ns?.ns])
            redirect(url: request.getHeader('referer'))
            return
        }

        identifier.value = params.value.trim()
        identifier.note = params.note?.trim()
        identifier.save(flush:true)

        redirect(url: request.getHeader('referer'))
    }
    def processEditCustomerIdentifier(){
        log.debug("OrganisationController::processEditIdentifier ${params}")
        CustomerIdentifier customeridentifier   = CustomerIdentifier.get(params.customeridentifier)
        if ( ! customeridentifier){
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'default.search.identifier'), params.identifierId])
            redirect(url: request.getHeader('referer'))
            return
        }
        if ( ! params.value){
            Platform plt = customeridentifier.platform
            String p = plt.org.name + (plt.org.sortname ? " (${plt.org.sortname})" : '') + ' : ' + plt.name
            flash.error = message(code: 'org.customerIdentifier.edit.err.missingvalue', args: [p])
            redirect(url: request.getHeader('referer'))
            return
        }
        customeridentifier.value = params.value
        customeridentifier.note = params.note?.trim()
        customeridentifier.save(flush:true)

        redirect(url: request.getHeader('referer'))
    }

    def createCustomerIdentifier(){
        log.debug("OrganisationController::createCustomerIdentifier ${params}")
        Org org   = Org.get(params.id)

        if (! org) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'org.label'), params.id])
            redirect(url: request.getHeader('referer'))
            return
        }
        List allPlatforms = Platform.executeQuery('select p from Platform p join p.org o where p.org is not null order by o.name, o.sortname, p.name')

        render template: '/templates/customerIdentifier/modal_create', model: [orgInstance: org, allPlatforms: allPlatforms]
    }

    def editCustomerIdentifier(){
        log.debug("OrganisationController::editCustomerIdentifier ${params}")
        CustomerIdentifier customeridentifier = CustomerIdentifier.get(params.customeridentifier)
        Org org = customeridentifier?.owner

        if (! customeridentifier) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'org.customerIdentifier'), params.customeridentifier])
            redirect(url: request.getHeader('referer'))
            return
        }

        render template: '/templates/customerIdentifier/modal_create', model: [orgInstance: org, customeridentifier: customeridentifier]
    }

    @Secured(['ROLE_ADMIN','ROLE_ORG_EDITOR'])
    def create() {
        switch (request.method) {
            case 'POST':
                Org orgInstance = new Org(params)
                orgInstance.status = RDStore.O_STATUS_CURRENT

                //if (params.name) {
                    if (orgInstance.save(flush: true)) {
                        orgInstance.setDefaultCustomerType()

                        flash.message = message(code: 'default.created.message', args: [message(code: 'org.label'), orgInstance.name])
                        redirect action: 'show', id: orgInstance.id
                        return
                    }
                //}

                render view: 'create', model: [orgInstance: orgInstance]
                break
        }
    }

    @Deprecated
    @Secured(['ROLE_ADMIN','ROLE_ORG_EDITOR'])
    def setupBasicTestData() {
        Org targetOrg = Org.get(params.id)
        if(organisationService.setupBasicTestData(targetOrg)) {
            flash.message = message(code:'org.setup.success')
        }
        else {
            flash.error = message(code:'org.setup.error',args: [organisationService.dumpErrors()])
        }
        redirect action: 'show', id: params.id
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN,ROLE_ORG_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN,ROLE_ORG_EDITOR")
    })
    def createProvider() {

        RefdataValue orgSector = RefdataValue.getByValueAndCategory('Publisher', RDConstants.ORG_SECTOR)
        RefdataValue orgType = RefdataValue.getByValueAndCategory('Provider', RDConstants.ORG_TYPE)
        RefdataValue orgType2 = RefdataValue.getByValueAndCategory('Agency', RDConstants.ORG_TYPE)
        Org orgInstance = new Org(name: params.provider, sector: orgSector.id)

        if ( orgInstance.save(flush:true) ) {

            orgInstance.addToOrgType(orgType)
            orgInstance.addToOrgType(orgType2)
            orgInstance.save(flush:true)

            flash.message = message(code: 'default.created.message', args: [message(code: 'org.label'), orgInstance.name])
            redirect action: 'show', id: orgInstance.id
        }
        else {
            log.error("Problem creating title: ${orgInstance.errors}");
            flash.message = message(code:'org.error.createProviderError',args:[orgInstance.errors])
            redirect ( action:'findProviderMatches' )
        }
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN,ROLE_ORG_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN,ROLE_ORG_EDITOR")
    })
    def findProviderMatches() {

        Map<String, Object> result = [:]
        if ( params.proposedProvider ) {

            result.providerMatches= Org.executeQuery("from Org as o where exists (select roletype from o.orgType as roletype where roletype = :provider ) and (lower(o.name) like :searchName or lower(o.shortname) like :searchName or lower(o.sortname) like :searchName ) ",
                    [provider: RDStore.OT_PROVIDER, searchName: "%${params.proposedProvider.toLowerCase()}%"])
        }
        result
    }

    @DebugAnnotation(perm="ORG_CONSORTIUM", affil="INST_EDTIOR",specRole="ROLE_ADMIN, ROLE_ORG_EDITOR")
    @Secured(closure = { ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM","INST_EDITOR","ROLE_ADMIN, ROLE_ORG_EDITOR") })
    def createMember() {
        Org contextOrg = contextService.org
        //new institution = consortia member, implies combo type consortium
        RefdataValue orgSector = RDStore.O_SECTOR_HIGHER_EDU
        Org orgInstance
        if(formService.validateToken(params)) {
            try {
                // createdBy will set by Org.beforeInsert()
                orgInstance = new Org(name: params.institution, sector: orgSector, status: RDStore.O_STATUS_CURRENT)
                orgInstance.save(flush:true)

                Combo newMember = new Combo(fromOrg:orgInstance,toOrg:contextOrg,type: RDStore.COMBO_TYPE_CONSORTIUM)
                newMember.save(flush:true)

                orgInstance.setDefaultCustomerType()
                orgInstance.addToOrgType(RefdataValue.getByValueAndCategory('Institution', RDConstants.ORG_TYPE)) //RDStore adding causes a DuplicateKeyException

                flash.message = message(code: 'default.created.message', args: [message(code: 'org.institution.label'), orgInstance.name])
                redirect action: 'show', id: orgInstance.id, params: [fromCreate: true]
            }
            catch (Exception e) {
                log.error("Problem creating institution")
                log.error(e.printStackTrace())

                flash.message = message(code: "org.error.createInstitutionError", args:[orgInstance ? orgInstance.errors : 'unbekannt'])

                redirect ( action:'findOrganisationMatches', params: params )
            }
        }
        else redirect action:'findOrganisationMatches'
    }

    @DebugAnnotation(perm="ORG_CONSORTIUM", affil="INST_EDITOR",specRole="ROLE_ADMIN, ROLE_ORG_EDITOR")
    @Secured(closure = { ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM","INST_EDITOR","ROLE_ADMIN, ROLE_ORG_EDITOR") })
    Map findOrganisationMatches() {
        Map memberMap = [:]
        RefdataValue comboType = RDStore.COMBO_TYPE_CONSORTIUM

        Combo.findAllByType(comboType).each { lObj ->
            Combo link = (Combo) lObj
            List members = memberMap.get(link.fromOrg.id)
            if(!members) {
                members = [link.toOrg.id]
            } else {
                members << link.toOrg.id
            }
            memberMap.put(link.fromOrg.id,members)
        }

        Map result = [institution:contextService.org,organisationMatches:[],members:memberMap,comboType:comboType]
        //searching members for consortium, i.e. the context org is a consortium
        if (params.proposedOrganisation) {
            result.organisationMatches.addAll(Org.executeQuery("select o from Org as o where exists (select roletype from o.orgType as roletype where roletype = :institution ) and (lower(o.name) like :searchName or lower(o.shortname) like :searchName or lower(o.sortname) like :searchName) ",
                    [institution: RDStore.OT_INSTITUTION, searchName: "%${params.proposedOrganisation.toLowerCase()}%"]))
        }
        if (params.proposedOrganisationID) {
            result.organisationMatches.addAll(Org.executeQuery("select id.org from Identifier id where lower(id.value) like :identifier and lower(id.ns.ns) in (:namespaces) ",
                    [identifier: "%${params.proposedOrganisationID.toLowerCase()}%",namespaces:["isil","wibid"]]))
        }

        result
    }

    @Secured(['ROLE_USER'])
    def show() {

        ProfilerUtils pu = new ProfilerUtils()
        pu.setBenchmark('this-n-that')

        Map<String, Object> result = setResultGenericsAndCheckAccess()
        if (! result) {
            response.sendError(401)
            return
        }
        if (! result.orgInstance) {
            redirect action: 'list'
            return
        }

        //this is a flag to check whether the page has been called directly after creation
        result.fromCreate = params.fromCreate ? true : false

        pu.setBenchmark('orgRoles & editable')

      if (!result.orgInstance) {
        flash.message = message(code: 'default.not.found.message', args: [message(code: 'org.label'), params.id])
        redirect action: 'list'
        return
      }

        pu.setBenchmark('tasks')

        result.tasks = taskService.getTasksByResponsiblesAndObject(result.user,result.institution,result.orgInstance)
        Map<String,Object> preCon = taskService.getPreconditionsWithoutTargets(result.institution)
        result << preCon

        pu.setBenchmark('properties')

        result.authorizedOrgs = result.user?.authorizedOrgs

        // create mandatory OrgPrivateProperties if not existing

        List<PropertyDefinition> mandatories = PropertyDefinition.getAllByDescrAndMandatoryAndTenant(PropertyDefinition.ORG_PROP, true, result.institution)

        mandatories.each { PropertyDefinition pd ->
            if (!OrgProperty.findWhere(owner: result.orgInstance, type: pd)) {
                def newProp = PropertyDefinition.createGenericProperty(PropertyDefinition.PRIVATE_PROPERTY, result.orgInstance, pd, result.institution)


                if (newProp.hasErrors()) {
                    log.error(newProp.errors.toString())
                } else {
                    log.debug("New org private property created via mandatory: " + newProp.type.name)
                }
            }
        }

        pu.setBenchmark('identifier')

        if(!Combo.findByFromOrgAndType(result.orgInstance, RDStore.COMBO_TYPE_DEPARTMENT) && !(RDStore.OT_PROVIDER.id in result.orgInstance.getAllOrgTypeIds())){
            result.orgInstance.createCoreIdentifiersIfNotExist()
        }

        pu.setBenchmark('createdBy and legallyObligedBy')

        if (result.orgInstance.createdBy) {
			result.createdByOrgGeneralContacts = PersonRole.executeQuery(
					"select distinct(prs) from PersonRole pr join pr.prs prs join pr.org oo " +
							"where oo = :org and pr.functionType = :ft and prs.isPublic = true",
					[org: result.orgInstance.createdBy, ft: RDStore.PRS_FUNC_GENERAL_CONTACT_PRS]
			)
        }
		if (result.orgInstance.legallyObligedBy) {
			result.legallyObligedByOrgGeneralContacts = PersonRole.executeQuery(
					"select distinct(prs) from PersonRole pr join pr.prs prs join pr.org oo " +
							"where oo = :org and pr.functionType = :ft and prs.isPublic = true",
					[org: result.orgInstance.legallyObligedBy, ft: RDStore.PRS_FUNC_GENERAL_CONTACT_PRS]
			)
		}
        List bm = pu.stopBenchmark()
        result.benchMark = bm

        result
    }

    @Secured(['ROLE_USER'])
    def ids() {

        User user = User.get(springSecurityService.principal.id)
        Org org   = Org.get(params.id)
        ProfilerUtils pu = new ProfilerUtils()
        pu.setBenchmark('this-n-that')

        Map<String, Object> result = setResultGenericsAndCheckAccess()
        if(!result) {
            response.sendError(401)
            return
        }
        result.editable_identifier = result.editable

        //this is a flag to check whether the page has been called directly after creation
        result.fromCreate = params.fromCreate ? true : false

        pu.setBenchmark('editable_identifier')


        RefdataValue orgSector = RDStore.O_SECTOR_PUBLISHER
        RefdataValue orgType = RDStore.OT_PROVIDER

        //IF ORG is a Provider
        if(result.orgInstance?.sector == orgSector || orgType.id in result.orgInstance?.getAllOrgTypeIds()) {
            pu.setBenchmark('editable_identifier2')
            result.editable_identifier = accessService.checkMinUserOrgRole(result.user, result.orgInstance, 'INST_EDITOR') ||
                    accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN,ROLE_ORG_EDITOR")
        }
        else {
            pu.setBenchmark('editable_identifier2')
            if(accessService.checkPerm("ORG_CONSORTIUM")) {
                List<Long> consortia = Combo.findAllByTypeAndFromOrg(RDStore.COMBO_TYPE_CONSORTIUM,result.orgInstance).collect { it ->
                    it.toOrg.id
                }
                if(consortia.size() == 1 && consortia.contains(result.institution.id) && accessService.checkMinUserOrgRole(result.user,result.institution,'INST_EDITOR'))
                    result.editable_identifier = true
            }
            else if(accessService.checkPerm("ORG_INST_COLLECTIVE")) {
                List<Long> department = Combo.findAllByTypeAndFromOrg(RDStore.COMBO_TYPE_DEPARTMENT,result.orgInstance).collect { it ->
                    it.toOrg.id
                }
                if (department.contains(result.institution.id) && accessService.checkMinUserOrgRole(result.user, result.institution, 'INST_EDITOR'))
                    result.editable_identifier = true
            }
            else
                result.editable_identifier = accessService.checkMinUserOrgRole(result.user, result.orgInstance, 'INST_EDITOR') || SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN,ROLE_ORG_EDITOR')
        }

      if (!result.orgInstance) {
        flash.message = message(code: 'default.not.found.message', args: [message(code: 'org.label'), params.id])
        redirect action: 'list'
        return
      }

        pu.setBenchmark('create Identifiers if necessary')

        // TODO: experimental asynchronous task
        //waitAll(task_orgRoles, task_properties)

        if(!Combo.findByFromOrgAndType(result.orgInstance,RDStore.COMBO_TYPE_DEPARTMENT) && !(RDStore.OT_PROVIDER.id in result.orgInstance.getAllOrgTypeIds())){
            result.orgInstance.createCoreIdentifiersIfNotExist()
        }

//------------------------orgSettings --------------------
        pu.setBenchmark('orgsettings')
        Boolean inContextOrg = contextService.getOrg().id == org.id
        Boolean isComboRelated = Combo.findByFromOrgAndToOrg(org, contextService.getOrg())

        result.hasAccessToCustomeridentifier = (inContextOrg && accessService.checkMinUserOrgRole(user, org, 'INST_USER')) ||
                (isComboRelated && accessService.checkMinUserOrgRole(user, contextService.getOrg(), 'INST_USER')) ||
                SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN,ROLE_ORG_EDITOR')

        if (result.hasAccessToCustomeridentifier) {

            result.user = user
            result.orgInstance = org

            result.inContextOrg = inContextOrg
            result.editable_customeridentifier = (inContextOrg && accessService.checkMinUserOrgRole(user, org, 'INST_EDITOR')) ||
                    (isComboRelated && accessService.checkMinUserOrgRole(user, contextService.getOrg(), 'INST_EDITOR')) ||
                    SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN,ROLE_ORG_EDITOR')
            result.isComboRelated = isComboRelated

            // adding default settings
            organisationService.initMandatorySettings(org)

            // collecting visible settings by customer type, role and/or combo
            List<OrgSetting> allSettings = OrgSetting.findAllByOrg(org)

            List<OrgSetting.KEYS> ownerSet = [
                    OrgSetting.KEYS.API_LEVEL,
                    OrgSetting.KEYS.API_KEY,
                    OrgSetting.KEYS.API_PASSWORD,
                    OrgSetting.KEYS.CUSTOMER_TYPE,
                    OrgSetting.KEYS.GASCO_ENTRY
            ]
            List<OrgSetting.KEYS> accessSet = [
                    OrgSetting.KEYS.OAMONITOR_SERVER_ACCESS,
                    OrgSetting.KEYS.NATSTAT_SERVER_ACCESS
            ]
            List<OrgSetting.KEYS> credentialsSet = [
                    OrgSetting.KEYS.NATSTAT_SERVER_API_KEY,
                    OrgSetting.KEYS.NATSTAT_SERVER_REQUESTOR_ID
            ]

            result.settings = []

            if (SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN,ROLE_ORG_EDITOR')) {
                result.settings.addAll(allSettings.findAll { it.key in ownerSet })
                result.settings.addAll(allSettings.findAll { it.key in accessSet })
                result.settings.addAll(allSettings.findAll { it.key in credentialsSet })
                result.customerIdentifier = CustomerIdentifier.findAllByCustomer(org, [sort: 'platform'])
            } else if (inContextOrg) {
                log.debug('settings for own org')
                result.settings.addAll(allSettings.findAll { it.key in ownerSet })

                if (org.hasPerm('ORG_CONSORTIUM,ORG_INST')) {
                    result.settings.addAll(allSettings.findAll { it.key in accessSet })
                    result.settings.addAll(allSettings.findAll { it.key in credentialsSet })
                    result.customerIdentifier = CustomerIdentifier.findAllByCustomer(org, [sort: 'platform'])
                } else if (['ORG_BASIC_MEMBER'].contains(org.getCustomerType())) {
                    result.settings.addAll(allSettings.findAll { it.key == OrgSetting.KEYS.NATSTAT_SERVER_ACCESS })
                    result.customerIdentifier = CustomerIdentifier.findAllByCustomer(org, [sort: 'platform'])
                } else if (['FAKE'].contains(org.getCustomerType())) {
                    result.settings.addAll(allSettings.findAll { it.key == OrgSetting.KEYS.NATSTAT_SERVER_ACCESS })
                }
            } else if (isComboRelated) {
                log.debug('settings for combo related org: consortia or collective')
                result.customerIdentifier = CustomerIdentifier.findAllByCustomer(org, [sort: 'platform'])
            }
        }
        List bm = pu.stopBenchmark()
        result.benchMark = bm
        result
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = { ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_USER") })
    def tasks() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result) {
            response.sendError(401); return
        }

        if (params.deleteId) {
            Task dTask = Task.get(params.deleteId)
            if (dTask && dTask.creator.id == result.user.id) {
                try {
                    flash.message = message(code: 'default.deleted.message', args: [message(code: 'task.label'), dTask.title])
                    dTask.delete()
                    if(params.returnToShow)
                        redirect action: 'show', id: params.id
                }
                catch (Exception e) {
                    flash.message = message(code: 'default.not.deleted.message', args: [message(code: 'task.label'), params.deleteId])
                }
            }
        }

        int offset = params.offset ? Integer.parseInt(params.offset) : 0
        result.taskInstanceList = taskService.getTasksByResponsiblesAndObject(result.user, result.institution, result.orgInstance)
        result.taskInstanceCount = result.taskInstanceList.size()
        result.taskInstanceList = taskService.chopOffForPageSize(result.taskInstanceList, result.user, offset)

        result.myTaskInstanceList = taskService.getTasksByCreatorAndObject(result.user,  result.orgInstance)
        result.myTaskInstanceCount = result.myTaskInstanceList.size()
        result.myTaskInstanceList = taskService.chopOffForPageSize(result.myTaskInstanceList, result.user, offset)

        log.debug(result.taskInstanceList.toString())
        result
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_USER")
    })
    def documents() {
        Map<String, Object> result = setResultGenericsAndCheckAccess()
        if(!result) {
            response.sendError(401)
            return
        }
        result
    }

    @DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def editDocument() {
        Map<String, Object> result = setResultGenericsAndCheckAccess()
        if(!result) {
            response.sendError(401)
            return
        }
        result.ownobj = result.institution
        result.owntp = 'organisation'
        if(params.id) {
            result.docctx = DocContext.get(params.id)
            result.doc = result.docctx.owner
        }

        render template: "/templates/documents/modal", model: result
    }

    @DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def deleteDocuments() {
        log.debug("deleteDocuments ${params}");

        docstoreService.unifiedDeleteDocuments(params)

        redirect controller: 'organisation', action: 'documents', id: params.instanceId /*, fragment: 'docstab' */
    }

    @DebugAnnotation(test='hasAffiliation("INST_USER")')
    @Secured(closure = {
        ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER")
    })
    def notes() {
        Map<String, Object> result = setResultGenericsAndCheckAccess()
        if(!result) {
            response.sendError(401)
            return
        }
        result
    }

    @DebugAnnotation(perm="FAKE,ORG_BASIC_MEMBER,ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN,ROLE_ORG_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("FAKE,ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN,ROLE_ORG_EDITOR")
    })
    def deleteCustomerIdentifier() {
        log.debug("OrganisationController::deleteIdentifier ${params}");
        CustomerIdentifier ci = (CustomerIdentifier) genericOIDService.resolveOID(params.deleteCI)
        Org owner = GrailsHibernateUtil.unwrapIfProxy(ci).owner
        if (ci && owner.id == contextService.org.id) {
            ci.delete(flush:true)
            log.debug("Customeridentifier deleted: ${params}")
        } else {
            if ( ! ci ) {
                flash.message = message(code: 'default.not.found.message', args: [message(code:
                        'org.customerIdentifier'), params.deleteCI])
            } else {
                flash.message = message(code: 'org.customerIdentifier.delete.norights')
            }
            log.error("Customeridentifier NOT deleted: ${params}; CostomerIdentifier not found or ContextOrg is not " +
                    "owner of this CustomerIdentifier and has no rights to delete it!")
        }
        redirect action: 'ids', id: params.id
    }

    @Secured(['ROLE_USER'])
    def deleteIdentifier() {
        log.debug("OrganisationController::deleteIdentifier ${params}")
        def owner = genericOIDService.resolveOID(params.owner)
        def target = genericOIDService.resolveOID(params.target)

        log.debug('owner: ' + owner)
        log.debug('target: ' + target)

        if (owner && target) {
            if (target."${Identifier.getAttributeName(owner)}"?.id == owner.id) {
                log.debug("Identifier deleted: ${params}")
                target.delete(flush:true)
            }
        }
        redirect(url: request.getHeader('referer'))
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_ADM")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_ADM") })
    def users() {
        Map<String, Object> result = setResultGenericsAndCheckAccess()

        if (! result.orgInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'org.label'), params.id])
            redirect action: 'list'
            return
        }

        result.editable = checkIsEditable(result.user, result.orgInstance)

        if (! result.editable) {
            boolean instAdminExists = result.orgInstance.getAllValidInstAdmins().size() > 0
            boolean comboCheck = instAdmService.hasInstAdmPivileges(result.user, result.orgInstance, [RDStore.COMBO_TYPE_DEPARTMENT, RDStore.COMBO_TYPE_CONSORTIUM])

            result.editable = comboCheck && ! instAdminExists
        }

        if (! result.editable) {
            redirect controller: 'organisation', action: 'show', id: result.orgInstance.id
        }

        result.pendingRequests = UserOrg.findAllByStatusAndOrg(UserOrg.STATUS_PENDING, result.orgInstance, [sort:'dateRequested', order:'desc'])

        Map filterParams = params
        filterParams.status = UserOrg.STATUS_APPROVED
        filterParams.org = result.orgInstance

        result.users = userService.getUserSet(filterParams)
        result.breadcrumb = 'breadcrumb'
        result.titleMessage = "${result.orgInstance.name} - ${message(code:'org.nav.users')}"
        result.inContextOrg = false
        result.navPath = "nav"
        result.navConfiguration = [orgInstance: result.orgInstance, inContextOrg: false]
        result.multipleAffiliationsWarning = true
        Set<Org> availableComboOrgs = Org.executeQuery('select c.fromOrg from Combo c where c.toOrg = :ctxOrg order by c.fromOrg.name asc', [ctxOrg:result.orgInstance])
        availableComboOrgs.add(result.orgInstance)
        result.filterConfig = [filterableRoles:Role.findAllByRoleType('user'), orgField: false]
        result.tableConfig = [
                editable: result.editable,
                editor: result.user,
                editLink: 'userEdit',
                users: result.users,
                showAllAffiliations: false,
                showAffiliationDeleteLink: true,
                modifyAccountEnability: SpringSecurityUtils.ifAllGranted('ROLE_YODA'),
                availableComboOrgs: availableComboOrgs
        ]
        result.total = result.users.size()
        render view: '/templates/user/_list', model: result
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_ADM", specRole = "ROLE_ADMIN")
    @Secured(closure = { ctx.accessService.checkPermAffiliationX("ORG_INST_COLLECTIVE,ORG_CONSORTIUM", "INST_ADM", "ROLE_ADMIN") })
    def userEdit() {
        Map result = [user: User.get(params.id), editor: contextService.user, orgInstance: contextService.org, manipulateAffiliations: false]
        result.editable = checkIsEditable(result.user, result.orgInstance)

        render view: '/templates/user/_edit', model: result
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_ADM", specRole = "ROLE_ADMIN")
    @Secured(closure = { ctx.accessService.checkPermAffiliationX("ORG_INST_COLLECTIVE,ORG_CONSORTIUM", "INST_ADM", "ROLE_ADMIN") })
    def userCreate() {
        Map<String, Object> result = setResultGenericsAndCheckAccess()
        result.availableOrgs = Org.get(params.id)
        result.availableOrgRoles = Role.findAllByRoleType('user')
        result.editor = result.user
        result.breadcrumb = 'breadcrumb'

        render view: '/templates/user/_create', model: result
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_ADM", specRole = "ROLE_ADMIN")
    @Secured(closure = { ctx.accessService.checkPermAffiliationX("ORG_INST_COLLECTIVE,ORG_CONSORTIUM","INST_ADM","ROLE_ADMIN") })
    def processUserCreate() {
        def success = userService.addNewUser(params, flash)
        //despite IntelliJ's warnings, success may be an array other than the boolean true
        if(success instanceof User) {
            flash.message = message(code: 'default.created.message', args: [message(code: 'user.label'), success.id])
            redirect action: 'userEdit', id: success.id
        }
        else if(success instanceof List) {
            flash.error = success.join('<br>')
            redirect action: 'userCreate'
        }
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_ADM", specRole = "ROLE_ADMIN")
    @Secured(closure = { ctx.accessService.checkPermAffiliationX("ORG_INST_COLLECTIVE,ORG_CONSORTIUM","INST_ADM","ROLE_ADMIN") })
    def addAffiliation() {
        Map<String, Object> result = userService.setResultGenerics(params)
        if (! result.editable) {
            flash.error = message(code: 'default.noPermissions')
            redirect action: 'userEdit', id: params.id
            return
        }
        userService.addAffiliation(result.user,params.org,params.formalRole,flash)
        redirect action: 'userEdit', id: params.id
    }

    @Secured(['ROLE_ADMIN','ROLE_ORG_EDITOR'])
    def edit() {
        redirect controller: 'organisation', action: 'show', params: params
        return
    }

    @Secured(['ROLE_ADMIN'])
    def _delete() {
        Map<String, Object> result = setResultGenericsAndCheckAccess()

        if (result.orgInstance) {
            if (params.process  && result.editable) {
                result.delResult = deletionService.deleteOrganisation(result.orgInstance, null, false)
            }
            else {
                result.delResult = deletionService.deleteOrganisation(result.orgInstance, null, DeletionService.DRY_RUN)
            }

            if (contextService.getUser().isAdmin()) {
                result.substituteList = Org.executeQuery("select distinct o from Org o where o.status != :delState", [delState: RDStore.O_STATUS_DELETED])
            }
            else {
                List<Org> orgList = [result.orgInstance]
                orgList.addAll(Org.executeQuery("select o from Combo cmb join cmb.fromOrg o where o.status != :delState and cmb.toOrg = :org", [delState: RDStore.O_STATUS_DELETED, org: result.orgInstance]))
                orgList.addAll(Org.executeQuery("select o from Combo cmb join cmb.toOrg o where o.status != :delState and cmb.fromOrg = :org", [delState: RDStore.O_STATUS_DELETED, org: result.orgInstance]))
                orgList.unique()

                result.substituteList = orgList
            }
        }

        render view: 'delete', model: result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_ADM")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_ADM") })
    def processAffiliation() {
      Map<String, Object> result = [:]
      result.user = User.get(springSecurityService.principal.id)

      // ERMS-2370 -> support multiple assocs
      UserOrg uo = UserOrg.get(params.assoc)

        // ERMS-2370 -> what about ADMIN and YODA?
      if (instAdmService.hasInstAdmPivileges(result.user, Org.get(params.id), [
              RDStore.COMBO_TYPE_DEPARTMENT, RDStore.COMBO_TYPE_CONSORTIUM
      ])) {

          if (params.cmd == 'approve') {
              uo.status = UserOrg.STATUS_APPROVED
              uo.dateActioned = System.currentTimeMillis()
              uo.save(flush: true)
          }
          else if (params.cmd == 'reject') {
              uo.status = UserOrg.STATUS_REJECTED
              uo.dateActioned = System.currentTimeMillis()
              uo.save(flush: true)
          }
          else if (params.cmd == 'delete') {
              uo.delete(flush: true)
          }
      }
      redirect action: 'users', id: params.id
    }

    @Secured(['ROLE_USER'])
    def addOrgCombo(Org fromOrg, Org toOrg) {
        RefdataValue comboType = RefdataValue.get(params.comboTypeTo)
      log.debug("Processing combo creation between ${fromOrg} AND ${toOrg} with type ${comboType}")
      def dupe = Combo.executeQuery("from Combo as c where c.fromOrg = :fromOrg and c.toOrg = :toOrg", [fromOrg: fromOrg, toOrg: toOrg])
      
      if (! dupe) {
          Combo consLink = new Combo(fromOrg:fromOrg,
                                 toOrg:toOrg,
                                 status:null,
                                 type:comboType)
          consLink.save(flush:true)
      }
      else {
        flash.message = "This Combo already exists!"
      }
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_USER")
    })
    def addressbook() {
        Map<String, Object> result = setResultGenericsAndCheckAccess()
        if(!result) {
            response.sendError(401)
            return
        }

        if (! result.institution) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'org.label'), params.id])
            redirect action: 'list'
            return
        }

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0

        params.org = result.orgInstance

        List visiblePersons = addressbookService.getVisiblePersons("addressbook",params)

        result.propList =
                PropertyDefinition.findAllWhere(
                        descr: PropertyDefinition.PRS_PROP,
                        tenant: contextService.getOrg() // private properties
                )

        result.num_visiblePersons = visiblePersons.size()
        result.visiblePersons = visiblePersons.drop(result.offset).take(result.max)

        if (visiblePersons){
            result.emailAddresses = Contact.executeQuery("select c.content from Contact c where c.prs in (:persons) and c.contentType = :contentType",
                    [persons: visiblePersons, contentType: RDStore.CCT_EMAIL])
        }

        result
    }
    @DebugAnnotation(perm="ORG_BASIC_MEMBER,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = { ctx.accessService.checkPermAffiliation("ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_USER") })
    def readerNumber() {
        Map<String, Object> result = setResultGenericsAndCheckAccess()
        if(!result) {
            response.sendError(401)
            return
        }

        if(params.tableA) {
            params.sortA = params.sort
            params.orderA = params.order
        }
        else {
            params.sortA = 'semester'
            params.orderA = 'desc'
        }

        if(params.tableB) {
            params.sortB = params.sort
            params.orderB = params.order
        }
        else {
            params.sortB = 'dueDate'
            params.orderB = 'desc'
        }

        Map<String,Map<String,ReaderNumber>> numbersWithSemester = organisationService.groupReaderNumbersByProperty(ReaderNumber.findAllByOrgAndSemesterIsNotNull((Org) result.orgInstance,[sort:params.sortA,order:params.orderA]),"semester")
        Map<String,Map<String,ReaderNumber>> numbersWithDueDate = organisationService.groupReaderNumbersByProperty(ReaderNumber.findAllByOrgAndDueDateIsNotNull((Org) result.orgInstance,[sort:params.sortB,order:params.orderB]),"dueDate")

        TreeSet<String> semesterCols = [], dueDateCols = []
        Map<String,Integer> dueDateSums = [:]
        Map<String,Map<String,Integer>> semesterSums = [:]
        numbersWithSemester.each { Map.Entry<String,Map<String,ReaderNumber>> semesters ->
            semesters.value.each { Map.Entry<String,ReaderNumber> row ->
                semesterCols << row.key
                ReaderNumber rn = row.value
                Map<String,Integer> semesterSumRow = semesterSums.get(semesters.key)
                if(!semesterSumRow)
                    semesterSumRow = [:]
                if(rn.value) {
                    Integer groupSum = semesterSumRow.get(rn.referenceGroup)
                    if(groupSum == null) {
                        groupSum = rn.value
                    }
                    else groupSum += rn.value
                    semesterSumRow.put(rn.referenceGroup,groupSum)
                }
                semesterSums.put(semesters.key,semesterSumRow)
            }
        }
        numbersWithDueDate.each { Map.Entry<String,Map<String,ReaderNumber>> dueDates ->
            dueDates.value.each { Map.Entry<String,ReaderNumber> row ->
                dueDateCols << row.key
                ReaderNumber rn = row.value
                Integer dueDateSum = dueDateSums.get(dueDates.key)
                if(rn.value) {
                    if(dueDateSum == null) {
                        dueDateSum = rn.value
                    }
                    else dueDateSum += rn.value
                }
                dueDateSums.put(dueDates.key,dueDateSum)
            }
        }

        result.numbersWithSemester = numbersWithSemester
        result.numbersWithDueDate = numbersWithDueDate
        result.semesterCols = semesterCols
        result.semesterSums = semesterSums
        result.dueDateCols = dueDateCols
        result.dueDateSums = dueDateSums

        result
    }

    @DebugAnnotation(perm="ORG_BASIC_MEMBER,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = { ctx.accessService.checkPermAffiliation("ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_USER") })
    def accessPoints() {
        Map<String, Object> result = setResultGenericsAndCheckAccess()
        if(!result) {
            response.sendError(401)
            return
        }

        if (! result.orgInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'org.label'), params.id])
            redirect action: 'list'
            return
        }

        List orgAccessPointList = accessPointService.getOapListWithLinkCounts(result.orgInstance)
        result.orgAccessPointList = orgAccessPointList.groupBy {it.oap.accessMethod.value}.sort {it.key}

        if (params.exportXLSX) {

            SXSSFWorkbook wb
            SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
            String datetoday = sdf.format(new Date(System.currentTimeMillis()))
            String filename = "${datetoday}_" + g.message(code: "org.nav.accessPoints")
            response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            wb = (SXSSFWorkbook) accessPointService.exportAccessPoints(orgAccessPointList.collect {it.oap}, result.institution)
            wb.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            wb.dispose()
            return
        }else {
            result
        }
    }

    def addOrgType()
    {
        Map<String, Object> result = [:]
        result.user = User.get(springSecurityService.principal.id)
        Org orgInstance = Org.get(params.org)

        if (!orgInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'org.label'), params.id])
            redirect action: 'list'
            return
        }

        result.editable = checkIsEditable(result.user, orgInstance)

        if(result.editable)
        {
            orgInstance.addToOrgType(RefdataValue.get(params.orgType))
            orgInstance.save(flush: true)
//            flash.message = message(code: 'default.updated.message', args: [message(code: 'org.label'), orgInstance.name])
            redirect action: 'show', id: orgInstance.id
        }
    }
    def deleteOrgType()
    {
        Map<String, Object> result = [:]
        result.user = User.get(springSecurityService.principal.id)
        Org orgInstance = Org.get(params.org)

        if (!orgInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'org.label'), params.id])
            redirect action: 'list'
            return
        }

        result.editable = checkIsEditable(result.user, orgInstance)

        if(result.editable)
        {
            orgInstance.removeFromOrgType(RefdataValue.get(params.removeOrgType))
            orgInstance.save(flush: true)
//            flash.message = message(code: 'default.updated.message', args: [message(code: 'org.label'), orgInstance.name])
            redirect action: 'show', id: orgInstance.id
        }
    }
    def addSubjectGroup() {
        Map<String, Object> result = setResultGenericsAndCheckAccess()

        if (!result.orgInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'org.label'), params.id])
            redirect(url: request.getHeader('referer'))
            return
        }
        RefdataValue newSubjectGroup = RefdataValue.get(params.subjectGroup)
        if (!newSubjectGroup) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'org.subjectGroup.label'), params.subjectGroup])
            redirect(url: request.getHeader('referer'))
            return
        }
        if (result.orgInstance.getSubjectGroup().find { it.subjectGroupId == newSubjectGroup.id }) {
            flash.message = message(code: 'default.err.alreadyExist', args: [message(code: 'org.subjectGroup.label')])
            redirect(url: request.getHeader('referer'))
            return
        }
        result.editable = checkIsEditable(result.user, result.orgInstance)

        if (result.editable){
            result.orgInstance.addToSubjectGroup(subjectGroup: RefdataValue.get(params.subjectGroup))
            result.orgInstance.save(flush: true)
//            flash.message = message(code: 'default.updated.message', args: [message(code: 'org.label'), orgInstance.name])
            redirect action: 'show', id: params.id
        }
    }

    def deleteSubjectGroup() {
        Map<String, Object> result = setResultGenericsAndCheckAccess()

        if (!result.orgInstance) {
            flash.error = message(code: 'default.not.found.message', args: [message(code: 'org.label'), params.id])
            redirect(url: request.getHeader('referer'))
            return
        }
        if(result.editable) {
            OrgSubjectGroup osg = OrgSubjectGroup.get(params.removeOrgSubjectGroup)
            result.orgInstance.removeFromSubjectGroup(osg)
            result.orgInstance.save()
            osg.delete()
//            flash.message = message(code: 'default.updated.message', args: [message(code: 'org.label'), orgInstance.name])
            redirect(url: request.getHeader('referer'))
        }
    }

    @DebugAnnotation(perm="ORG_CONSORTIUM", type="Consortium", affil="INST_EDITOR", specRole="ROLE_ORG_EDITOR")
    @Secured(closure = { ctx.accessService.checkPermTypeAffiliationX("ORG_CONSORTIUM", "Consortium", "INST_EDITOR", "ROLE_ORG_EDITOR") })
    def toggleCombo() {
        Map<String, Object> result = setResultGenericsAndCheckAccess()
        if(!result) {
            response.sendError(401)
            return
        }
        if(!params.direction) {
            flash.error(message(code:'org.error.noToggleDirection'))
            response.sendError(404)
            return
        }
        switch(params.direction) {
            case 'add':
                Map map = [toOrg: result.institution,
                        fromOrg: Org.get(params.fromOrg),
                        type: RefdataValue.getByValueAndCategory('Consortium', RDConstants.COMBO_TYPE)]
                if (! Combo.findWhere(map)) {
                    Combo cmb = new Combo(map)
                    cmb.save(flush:true)
                }
                break
            case 'remove':

                def subs = Subscription.executeQuery("from Subscription as s where exists ( select o from s.orgRelations as o where o.org in (:orgs) )", [orgs: [result.institution, Org.get(params.fromOrg)]])
                if(subs){
                    flash.error = message(code:'org.consortiaToggle.remove.notPossible.sub')
                }
                def lics = License.executeQuery("from License as l where exists ( select o from l.orgRelations as o where o.org in (:orgs) )", [orgs: [result.institution, Org.get(params.fromOrg)]])
                if(lics){
                    flash.error = message(code:'org.consortiaToggle.remove.notPossible.sub')
                }

                if(!subs && !lics) {

                    Combo cmb = Combo.findWhere(toOrg: result.institution,
                            fromOrg: Org.get(params.fromOrg),
                            type: RefdataValue.getByValueAndCategory('Consortium', RDConstants.COMBO_TYPE))
                    cmb.delete(flush:true)
                }
                break
        }
        redirect action: 'listInstitution'
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def myPublicContacts() {
        Map<String, Object> result = setResultGenericsAndCheckAccess()

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0

        result.rdvAllPersonFunctions = [RDStore.PRS_FUNC_GENERAL_CONTACT_PRS, RDStore.PRS_FUNC_CONTACT_PRS, RDStore.PRS_FUNC_FUNC_BILLING_ADDRESS, RDStore.PRS_FUNC_TECHNICAL_SUPPORT, RDStore.PRS_FUNC_RESPONSIBLE_ADMIN]
        result.rdvAllPersonPositions = PersonRole.getAllRefdataValues(RDConstants.PERSON_POSITION) - [RDStore.PRS_POS_ACCOUNT, RDStore.PRS_POS_SD, RDStore.PRS_POS_SS]

        if(result.institution.getCustomerType() == 'ORG_CONSORTIUM' && result.orgInstance)
        {
            params.org = result.orgInstance
            result.rdvAllPersonFunctions << RDStore.PRS_FUNC_GASCO_CONTACT
        }else{
            params.org = result.institution
        }

        List allOrgTypeIds = result.orgInstance.getAllOrgTypeIds()
        if(RDStore.OT_PROVIDER.id in allOrgTypeIds || RDStore.OT_AGENCY.id in allOrgTypeIds){
            result.rdvAllPersonFunctions = PersonRole.getAllRefdataValues(RDConstants.PERSON_FUNCTION) - [RDStore.PRS_FUNC_GASCO_CONTACT, RDStore.PRS_FUNC_RESPONSIBLE_ADMIN, RDStore.PRS_FUNC_FUNC_LIBRARY_ADDRESS, RDStore.PRS_FUNC_FUNC_LEGAL_PATRON_ADDRESS, RDStore.PRS_FUNC_FUNC_POSTAL_ADDRESS, RDStore.PRS_FUNC_FUNC_BILLING_ADDRESS, RDStore.PRS_FUNC_FUNC_DELIVERY_ADDRESS]
            result.rdvAllPersonPositions = [RDStore.PRS_POS_ACCOUNT, RDStore.PRS_POS_DIREKTION, RDStore.PRS_POS_DIREKTION_ASS, RDStore.PRS_POS_RB, RDStore.PRS_POS_SD, RDStore.PRS_POS_SS, RDStore.PRS_POS_TS]

        }

        List visiblePersons = addressbookService.getVisiblePersons("myPublicContacts",params)
        result.num_visiblePersons = visiblePersons.size()
        result.visiblePersons = visiblePersons.drop(result.offset).take(result.max)

        if (visiblePersons){
            result.emailAddresses = Contact.executeQuery("select c.content from Contact c where c.prs in (:persons) and c.contentType = :contentType",
                    [persons: visiblePersons, contentType: RDStore.CCT_EMAIL])
        }

        params.tab = params.tab ?: 'contacts'

        result.addresses = Address.findAllByOrg(params.org)

        result
    }

    private Map<String, Object> setResultGenericsAndCheckAccess() {
        User user = User.get(springSecurityService.principal.id)
        Org org = contextService.org
        Map<String, Object> result = [user:user,institution:org,inContextOrg:true,institutionalView:false]

        if (params.id) {
            result.orgInstance = Org.get(params.id)
            result.editable = checkIsEditable(user, result.orgInstance)
            result.inContextOrg = result.orgInstance?.id == org.id
            //this is a flag to check whether the page has been called for a consortia or inner-organisation member
            Combo checkCombo = Combo.findByFromOrgAndToOrg(result.orgInstance,org)
            if(checkCombo && checkCombo.type == RDStore.COMBO_TYPE_CONSORTIUM)
                result.institutionalView = true
            //restrictions hold if viewed org is not the context org
            if(!result.inContextOrg && !accessService.checkPerm("ORG_CONSORTIUM") && !SpringSecurityUtils.ifAnyGranted("ROLE_ADMIN, ROLE_ORG_EDITOR")) {
                //restrictions further concern only single users, not consortia
                if(accessService.checkPerm("ORG_INST") && result.orgInstance.getCustomerType() == "ORG_INST") {
                    return null
                }
            }
        } else {
            result.editable = checkIsEditable(user, org)
        }
        result
    }


    private boolean checkIsEditable(User user, Org org) {
        boolean isEditable
        Org contextOrg = contextService.org
        Org orgInstance = org
        boolean inContextOrg =  orgInstance?.id == contextOrg.id
        boolean userHasEditableRights = user.hasRole('ROLE_ADMIN') ||user.hasRole('ROLE_ORG_EDITOR') || user.hasAffiliation('INST_EDITOR')
        switch(params.action){
            case 'userEdit':
                isEditable = true
                break
            case '_delete':
                isEditable = SpringSecurityUtils.ifAnyGranted('ROLE_ORG_EDITOR,ROLE_ADMIN')
                break
            case 'properties':
                isEditable = accessService.checkMinUserOrgRole(user, Org.get(params.id), 'INST_EDITOR') || SpringSecurityUtils.ifAllGranted('ROLE_ADMIN')
                break
            case 'addSubjectGroup':
            case 'deleteSubjectGroup':
                isEditable = accessService.checkMinUserOrgRole(user, Org.get(params.id), 'INST_EDITOR') || SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN,ROLE_ORG_EDITOR')
                break
            case 'users':
                isEditable = accessService.checkMinUserOrgRole(user, Org.get(params.id), 'INST_ADM') || SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN')
                break
            case 'addOrgType':
            case 'deleteOrgType':
                isEditable = accessService.checkMinUserOrgRole(user, Org.get(params.org), 'INST_ADM') || SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN,ROLE_ORG_EDITOR')
                break
            case 'myPublicContacts':
                if (inContextOrg) {
                    isEditable = userHasEditableRights
                }else{
                    isEditable = false
                }
                break
            case 'show':
            case 'ids':
            case 'readerNumber':
            case 'accessPoints':
            case 'addressbook':
                if (inContextOrg) {
                    isEditable = userHasEditableRights
                } else {
                    switch (contextOrg.getCustomerType()){
                        case 'ORG_BASIC_MEMBER':
                            switch (orgInstance.getCustomerType()){
                                case 'ORG_BASIC_MEMBER':    isEditable = false; break
                                case 'ORG_INST':            isEditable = false; break
                                case 'ORG_CONSORTIUM':      isEditable = false; break
                                default:                    isEditable = false; break
                            }
                            break
                        case 'ORG_INST':
                            switch (orgInstance.getCustomerType()){
                                case 'ORG_BASIC_MEMBER':    isEditable = false; break
                                case 'ORG_INST':            isEditable = false; break
                                case 'ORG_CONSORTIUM':      isEditable = false; break
                                default:                    isEditable = userHasEditableRights; break //means providers and agencies
                            }
                            break
                        case 'ORG_CONSORTIUM':
                            switch (orgInstance.getCustomerType()){
                                case 'ORG_BASIC_MEMBER':    isEditable = userHasEditableRights; break
                                case 'ORG_INST':            isEditable = userHasEditableRights; break
                                case 'ORG_CONSORTIUM':      isEditable = false; break
                                default:                    isEditable = userHasEditableRights; break //means providers and agencies
                            }
                            break
                    }
                }
                break
            default:
                isEditable = accessService.checkMinUserOrgRole(user, org,'INST_EDITOR') || SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN,ROLE_ORG_EDITOR')
        }
        isEditable
    }
}
