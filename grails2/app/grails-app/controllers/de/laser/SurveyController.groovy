package de.laser


import com.k_int.kbplus.DocstoreService
import com.k_int.kbplus.ExportService
import com.k_int.kbplus.GenericOIDService
import com.k_int.kbplus.InstitutionsService
import de.laser.properties.SubscriptionProperty
import com.k_int.kbplus.auth.User
import de.laser.base.AbstractPropertyWithCalculatedLastUpdated
import de.laser.finance.CostItem
import de.laser.helper.*
import de.laser.interfaces.CalculatedType
import de.laser.properties.PropertyDefinition
import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.annotation.Secured
import groovy.time.TimeCategory
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.codehaus.groovy.runtime.InvokerHelper
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.dao.DataIntegrityViolationException

import javax.servlet.ServletOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat

@Secured(['IS_AUTHENTICATED_FULLY'])
class SurveyController {

    SpringSecurityService springSecurityService
    AccessService accessService
    ContextService contextService
    SubscriptionsQueryService subscriptionsQueryService
    FilterService filterService
    DocstoreService docstoreService
    OrgTypeService orgTypeService
    GenericOIDService genericOIDService
    SurveyService surveyService
    FinanceService financeService
    ExportService exportService
    TaskService taskService
    SubscriptionService subscriptionService
    ComparisonService comparisonService
    EscapeService escapeService
    InstitutionsService institutionsService
    PropertyService propertyService
    LinksGenerationService linksGenerationService
    CopyElementsService copyElementsService

    def possible_date_formats = [
            new SimpleDateFormat('yyyy/MM/dd'),
            new SimpleDateFormat('dd.MM.yyyy'),
            new SimpleDateFormat('dd/MM/yyyy'),
            new SimpleDateFormat('dd/MM/yy'),
            new SimpleDateFormat('yyyy/MM'),
            new SimpleDateFormat('yyyy')
    ]


    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
    Map<String, Object> redirectSurveyConfig() {
        SurveyConfig surveyConfig = SurveyConfig.get(params.id)

        redirect(action: 'show', params: [id: surveyConfig.surveyInfo.id, surveyConfigID: surveyConfig.id])

    }


    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
    Map<String, Object> currentSurveysConsortia() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        params.max = result.max
        params.offset = result.offset
        params.filterStatus = params.filterStatus ?: ((params.size() > 4) ? "" : [RDStore.SURVEY_SURVEY_STARTED.id.toString(), RDStore.SURVEY_READY.id.toString(), RDStore.SURVEY_IN_PROCESSING.id.toString()])

        result.propList = PropertyDefinition.findAllPublicAndPrivateProp([PropertyDefinition.SVY_PROP], result.institution)

        if (params.validOnYear == null || params.validOnYear == '') {
            SimpleDateFormat sdfyear = new java.text.SimpleDateFormat(message(code: 'default.date.format.onlyYear'))
            params.validOnYear = sdfyear.format(new Date(System.currentTimeMillis()))
        }

        result.surveyYears = SurveyInfo.executeQuery("select Year(startDate) from SurveyInfo where owner = :org and startDate != null group by YEAR(startDate) order by YEAR(startDate)", [org: result.institution]) ?: []

        List orgIds = orgTypeService.getCurrentOrgIdsOfProvidersAndAgencies( contextService.org )

        result.providers = orgIds.isEmpty() ? [] : Org.findAllByIdInList(orgIds, [sort: 'name'])

        result.subscriptions = Subscription.executeQuery("select DISTINCT s.name from Subscription as s where ( exists ( select o from s.orgRelations as o where ( o.roleType = :roleType AND o.org = :activeInst ) ) ) " +
                " AND s.instanceOf is not null order by s.name asc ", ['roleType': RDStore.OR_SUBSCRIPTION_CONSORTIA, 'activeInst': result.institution])

        DateFormat sdFormat = DateUtil.getSDF_NoTime()
        Map<String,Object> fsq = filterService.getSurveyConfigQueryConsortia(params, sdFormat, result.institution)

        result.surveys = SurveyInfo.executeQuery(fsq.query, fsq.queryParams, params)

        if ( params.exportXLSX ) {

            SXSSFWorkbook wb
            if ( params.surveyCostItems ) {
                SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
                String datetoday = sdf.format(new Date(System.currentTimeMillis()))
                String filename = "${datetoday}_" + g.message(code: "surveyCostItems.label")
                //if(wb instanceof XSSFWorkbook) file += "x";
                response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
                response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                wb = (SXSSFWorkbook) surveyService.exportSurveyCostItems(result.surveys.collect {it[1]}, result.institution)
            }else{
                SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
                String datetoday = sdf.format(new Date(System.currentTimeMillis()))
                String filename = "${datetoday}_" + g.message(code: "survey.plural")
                //if(wb instanceof XSSFWorkbook) file += "x";
                response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
                response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                wb = (SXSSFWorkbook) surveyService.exportSurveys(result.surveys.collect {it[1]}, result.institution)
            }
            
            wb.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            wb.dispose()

            return
        }else {
            result.surveysCount = SurveyInfo.executeQuery(fsq.query, fsq.queryParams).size()
            result.filterSet = params.filterSet ? true : false

            result
        }
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
    Map<String, Object> workflowsSurveysConsortia() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        params.max = result.max
        params.offset = result.offset

        params.tab = params.tab ?: 'created'

        if (params.validOnYear == null || params.validOnYear == '') {
            SimpleDateFormat sdfyear = new java.text.SimpleDateFormat(message(code: 'default.date.format.onlyYear'))
            params.validOnYear = sdfyear.format(new Date(System.currentTimeMillis()))
        }

        result.surveyYears = SurveyInfo.executeQuery("select Year(startDate) from SurveyInfo where owner = :org and startDate != null group by YEAR(startDate) order by YEAR(startDate)", [org: result.institution]) ?: []

        result.providers = orgTypeService.getCurrentOrgsOfProvidersAndAgencies( contextService.org )

        result.subscriptions = Subscription.executeQuery("select DISTINCT s.name from Subscription as s where ( exists ( select o from s.orgRelations as o where ( o.roleType = :roleType AND o.org = :activeInst ) ) ) " +
                " AND s.instanceOf is not null order by s.name asc ", ['roleType': RDStore.OR_SUBSCRIPTION_CONSORTIA, 'activeInst': result.institution])


        DateFormat sdFormat = DateUtil.getSDF_NoTime()
        Map<String,Object> fsq = filterService.getSurveyConfigQueryConsortia(params, sdFormat, result.institution)

        result.surveys = SurveyInfo.executeQuery(fsq.query, fsq.queryParams, params)

        if ( params.exportXLSX ) {
            SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
            String datetoday = sdf.format(new Date(System.currentTimeMillis()))
            String filename = "${datetoday}_" + g.message(code: "survey.plural")
            //if(wb instanceof XSSFWorkbook) file += "x";
            response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            SXSSFWorkbook wb = (SXSSFWorkbook) surveyService.exportSurveys(SurveyConfig.findAllByIdInList(result.surveys.collect {it[1].id}), result.institution)
            wb.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            wb.dispose()

            return
        }else {
            result.surveysCount = SurveyInfo.executeQuery(fsq.query, fsq.queryParams).size()
            result.countSurveyConfigs = surveyService.getSurveyConfigCounts()

            result.filterSet = params.filterSet ? true : false

            result
        }

    }


    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    Map<String,Object> createGeneralSurvey() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        result
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    Map<String,Object> processCreateGeneralSurvey() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        Date startDate = params.startDate ? sdf.parse(params.startDate) : null
        Date endDate = params.endDate ? sdf.parse(params.endDate) : null

        if(startDate != null && endDate != null) {
            if(startDate > endDate) {
                flash.error = g.message(code: "createSurvey.create.fail.startDateAndEndDate")
                redirect(action: 'createGeneralSurvey', params: params)
                return
            }
        }

        SurveyInfo surveyInfo = new SurveyInfo(
                name: params.name,
                startDate: startDate,
                endDate: endDate,
                type: RDStore.SURVEY_TYPE_INTEREST,
                owner: contextService.getOrg(),
                status: RDStore.SURVEY_IN_PROCESSING,
                comment: params.comment ?: null,
                isSubscriptionSurvey: false,
                isMandatory: params.mandatory ?: false
        )

        if (!(surveyInfo.save())) {
            flash.error = g.message(code: "createGeneralSurvey.create.fail")
            redirect(action: 'createGeneralSurvey', params: params)
            return
        }

        if (!SurveyConfig.findAllBySurveyInfo(surveyInfo)) {
            SurveyConfig surveyConfig = new SurveyConfig(
                    type: 'GeneralSurvey',
                    surveyInfo: surveyInfo,
                    configOrder: 1
            )

            if(!(surveyConfig.save())){
                surveyInfo.delete()
                flash.error = g.message(code: "createGeneralSurvey.create.fail")
                redirect(action: 'createGeneralSurvey', params: params)
                return
            }

        }

        //flash.message = g.message(code: "createGeneralSurvey.create.successfull")
        redirect action: 'show', id: surveyInfo.id

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    Map<String,Object> createSubscriptionSurvey() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0


        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()

        if (params.validOn == null || params.validOn.trim() == '') {
            result.validOn = ""
        } else {
            result.validOn = params.validOn
            date_restriction = sdf.parse(params.validOn)
        }

        result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        if (!params.status) {
            if (params.isSiteReloaded != "yes") {
                params.status = RDStore.SUBSCRIPTION_CURRENT.id
                result.defaultSet = true
            } else {
                params.status = 'FETCH_ALL'
            }
        }

        List orgIds = orgTypeService.getCurrentOrgIdsOfProvidersAndAgencies( contextService.org )

        result.providers = orgIds.isEmpty() ? [] : Org.findAllByIdInList(orgIds, [sort: 'name'])

        List tmpQ = subscriptionsQueryService.myInstitutionCurrentSubscriptionsBaseQuery(params, contextService.org)
        result.filterSet = tmpQ[2]
        List subscriptions = Subscription.executeQuery("select s ${tmpQ[0]}", tmpQ[1])
        //,[max: result.max, offset: result.offset]

        result.propList = PropertyDefinition.findAllPublicAndPrivateProp([PropertyDefinition.SUB_PROP], contextService.org)

        if (params.sort && params.sort.indexOf("§") >= 0) {
            switch (params.sort) {
                case "orgRole§provider":
                    subscriptions.sort { x, y ->
                        String a = x.getProviders().size() > 0 ? x.getProviders().first().name : ''
                        String b = y.getProviders().size() > 0 ? y.getProviders().first().name : ''
                        a.compareToIgnoreCase b
                    }
                    if (params.order.equals("desc"))
                        subscriptions.reverse(true)
                    break
            }
        }
        result.num_sub_rows = subscriptions.size()
        result.subscriptions = subscriptions.drop((int) result.offset).take((int) result.max)

        result.allLinkedLicenses = [:]
        Set<Links> allLinkedLicenses = Links.findAllByDestinationInListAndLinkType(result.subscriptions.collect { Subscription s -> genericOIDService.getOID(s) },RDStore.LINKTYPE_LICENSE)
        allLinkedLicenses.each { Links li ->
            Subscription s = (Subscription) genericOIDService.resolveOID(li.destination)
            License l = (License) genericOIDService.resolveOID(li.source)
            Set<License> linkedLicenses = result.allLinkedLicenses.get(s)
            if(!linkedLicenses)
                linkedLicenses = []
            linkedLicenses << l
            result.allLinkedLicenses.put(s,linkedLicenses)
        }

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    Map<String,Object> createIssueEntitlementsSurvey() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0

        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()

        if (params.validOn == null || params.validOn.trim() == '') {
            result.validOn = ""
        } else {
            result.validOn = params.validOn
            date_restriction = sdf.parse(params.validOn)
        }

        result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        if (!params.status) {
            if (params.isSiteReloaded != "yes") {
                params.status = RDStore.SUBSCRIPTION_INTENDED.id
                result.defaultSet = true
            } else {
                params.status = 'FETCH_ALL'
            }
        }

        List orgIds = orgTypeService.getCurrentOrgIdsOfProvidersAndAgencies( contextService.org )

        result.providers = orgIds.isEmpty() ? [] : Org.findAllByIdInList(orgIds, [sort: 'name'])

        List tmpQ = subscriptionsQueryService.myInstitutionCurrentSubscriptionsBaseQuery(params, contextService.org)
        result.filterSet = tmpQ[2]
        List subscriptions = Subscription.executeQuery("select s ${tmpQ[0]}", tmpQ[1])
        //,[max: result.max, offset: result.offset]

        result.propList = PropertyDefinition.findAllPublicAndPrivateProp([PropertyDefinition.SUB_PROP], contextService.org)

        if (params.sort && params.sort.indexOf("§") >= 0) {
            switch (params.sort) {
                case "orgRole§provider":
                    subscriptions.sort { x, y ->
                        String a = x.getProviders().size() > 0 ? x.getProviders().first().name : ''
                        String b = y.getProviders().size() > 0 ? y.getProviders().first().name : ''
                        a.compareToIgnoreCase b
                    }
                    if (params.order.equals("desc"))
                        subscriptions.reverse(true)
                    break
            }
        }
        result.num_sub_rows = subscriptions.size()
        result.subscriptions = subscriptions.drop((int) result.offset).take((int) result.max)

        result.allLinkedLicenses = [:]
        Set<Links> allLinkedLicenses = Links.findAllByDestinationInListAndLinkType(result.subscriptions.collect { Subscription s -> genericOIDService.getOID(s) },RDStore.LINKTYPE_LICENSE)
        allLinkedLicenses.each { Links li ->
            Subscription s = (Subscription) genericOIDService.resolveOID(li.destination)
            License l = (License) genericOIDService.resolveOID(li.source)
            Set<License> linkedLicenses = result.allLinkedLicenses.get(s)
            if(!linkedLicenses)
                linkedLicenses = []
            linkedLicenses << l
            result.allLinkedLicenses.put(s,linkedLicenses)
        }

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    Map<String,Object> addSubtoSubscriptionSurvey() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        result.subscription = Subscription.get(Long.parseLong(params.sub))
        if (!result.subscription) {
            redirect action: 'createSubscriptionSurvey'
        }

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    Map<String,Object> addSubtoIssueEntitlementsSurvey() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        result.subscription = Subscription.get(Long.parseLong(params.sub))
        result.pickAndChoose = true
        if (!result.subscription) {
            redirect action: 'createIssueEntitlementsSurvey'
        }

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    Map<String,Object> processCreateSubscriptionSurvey() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        Date startDate = params.startDate ? sdf.parse(params.startDate) : null
        Date endDate = params.endDate ? sdf.parse(params.endDate) : null

        if(startDate != null && endDate != null) {
            if(startDate > endDate) {
                flash.error = g.message(code: "createSurvey.create.fail.startDateAndEndDate")
                redirect(action: 'addSubtoSubscriptionSurvey', params: params)
                return
            }
        }

        Subscription subscription = Subscription.get(Long.parseLong(params.sub))
        boolean subSurveyUseForTransfer = (SurveyConfig.findAllBySubscriptionAndSubSurveyUseForTransfer(subscription, true)) ? false : (params.subSurveyUseForTransfer ? true : false)

        SurveyInfo surveyInfo = new SurveyInfo(
                name: params.name,
                startDate: startDate,
                endDate: endDate,
                type: subSurveyUseForTransfer ? RDStore.SURVEY_TYPE_RENEWAL : RDStore.SURVEY_TYPE_SUBSCRIPTION,
                owner: contextService.getOrg(),
                status: RDStore.SURVEY_IN_PROCESSING,
                comment: params.comment ?: null,
                isSubscriptionSurvey: true,
                isMandatory: subSurveyUseForTransfer ? true : (params.mandatory ?: false)
        )

        if (!(surveyInfo.save())) {
            flash.error = g.message(code: "createSubscriptionSurvey.create.fail")
            redirect(action: 'addSubtoSubscriptionSurvey', params: params)
            return
        }

        if (subscription && !SurveyConfig.findAllBySubscriptionAndSurveyInfo(subscription, surveyInfo)) {
            SurveyConfig surveyConfig = new SurveyConfig(
                    subscription: subscription,
                    configOrder: surveyInfo.surveyConfigs ? surveyInfo.surveyConfigs.size() + 1 : 1,
                    type: 'Subscription',
                    surveyInfo: surveyInfo,
                    subSurveyUseForTransfer: subSurveyUseForTransfer

            )

            surveyConfig.save()

            //Wenn es eine Umfrage schon gibt, die als Übertrag dient. Dann ist es auch keine Lizenz Umfrage mit einem Teilnahme-Merkmal abfragt!
            if (subSurveyUseForTransfer) {
                    SurveyConfigProperties configProperty = new SurveyConfigProperties(
                            surveyProperty: PropertyDefinition.getByNameAndDescr('Participation', PropertyDefinition.SVY_PROP),
                            surveyConfig: surveyConfig)

                    if (configProperty.save()) {
                        addSubMembers(surveyConfig)
                    }
                } else {
                    addSubMembers(surveyConfig)
                }
        } else {
            surveyInfo.delete()
            flash.error = g.message(code: "createSubscriptionSurvey.create.fail")
            redirect(action: 'addSubtoSubscriptionSurvey', params: params)
            return
        }

        //flash.message = g.message(code: "createSubscriptionSurvey.create.successfull")
        redirect action: 'show', id: surveyInfo.id

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    Map<String,Object> processCreateIssueEntitlementsSurvey() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        Date startDate = params.startDate ? sdf.parse(params.startDate) : null
        Date endDate = params.endDate ? sdf.parse(params.endDate) : null

        if(startDate != null && endDate != null) {
            if(startDate > endDate) {
                flash.error = g.message(code: "createSurvey.create.fail.startDateAndEndDate")
                redirect(action: 'addSubtoIssueEntitlementsSurvey', params: params)
                return
            }
        }

        SurveyInfo surveyInfo = new SurveyInfo(
                name: params.name,
                startDate: startDate,
                endDate: endDate,
                type: RDStore.SURVEY_TYPE_TITLE_SELECTION,
                owner: contextService.getOrg(),
                status: RDStore.SURVEY_IN_PROCESSING,
                comment: params.comment ?: null,
                isSubscriptionSurvey: true,
                isMandatory: params.mandatory ? true : false
        )

        if (!(surveyInfo.save())) {
            flash.error = g.message(code: "createSubscriptionSurvey.create.fail")
            redirect(action: 'addSubtoIssueEntitlementsSurvey', params: params)
            return
        }

        Subscription subscription = Subscription.get(Long.parseLong(params.sub))
        if (subscription && !SurveyConfig.findAllBySubscriptionAndSurveyInfo(subscription, surveyInfo)) {
            SurveyConfig surveyConfig = new SurveyConfig(
                    subscription: subscription,
                    configOrder: surveyInfo.surveyConfigs?.size() ? surveyInfo.surveyConfigs.size() + 1 : 1,
                    type: 'IssueEntitlementsSurvey',
                    surveyInfo: surveyInfo,
                    subSurveyUseForTransfer: false,
                    pickAndChoose: true,
                    createTitleGroups: params.createTitleGroups ? true : false

            )

            surveyConfig.save()

            addSubMembers(surveyConfig)

        } else {
            surveyInfo.delete()
            flash.error = g.message(code: "createIssueEntitlementsSurvey.create.fail")
            redirect(action: 'addSubtoIssueEntitlementsSurvey', params: params)
            return
        }

        //flash.message = g.message(code: "createIssueEntitlementsSurvey.create.successfull")
        redirect action: 'show', id: surveyInfo.id

    }


    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
    Map<String,Object> show() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()

        if(result.surveyInfo.surveyConfigs.size() >= 1  || params.surveyConfigID) {

            result.surveyConfig = params.surveyConfigID ? SurveyConfig.get(params.surveyConfigID) : result.surveyInfo.surveyConfigs[0]

            result.navigation = surveyService.getConfigNavigation(result.surveyInfo,  result.surveyConfig)

            if ( result.surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION) {
                result.authorizedOrgs = result.user.authorizedOrgs

                // restrict visible for templates/links/orgLinksAsList
                result.visibleOrgRelations = []
                 result.surveyConfig.subscription.orgRelations.each { OrgRole or ->
                    if (!(or.org.id == result.institution.id) && !(or.roleType in [RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS])) {
                        result.visibleOrgRelations << or
                    }
                }
                result.visibleOrgRelations.sort { it.org.sortname }

                result.subscription =  result.surveyConfig.subscription ?: null

                //costs dataToDisplay
               result.dataToDisplay = ['own','cons']
               result.offsets = [consOffset:0,ownOffset:0]
               result.sortConfig = [consSort:'sortname',consOrder:'asc',
                                    ownSort:'ci.costTitle',ownOrder:'asc']

                result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
                //cost items
                //params.forExport = true
                LinkedHashMap costItems = result.subscription ? financeService.getCostItemsForSubscription(params, result) : null
                result.costItemSums = [:]
                if (costItems?.own) {
                    result.costItemSums.ownCosts = costItems.own.sums
                }
                if (costItems?.cons) {
                    result.costItemSums.consCosts = costItems.cons.sums
                }
                result.links = linksGenerationService.getSourcesAndDestinations(result.subscription,result.user)
            }

            Org contextOrg = contextService.getOrg()
            result.tasks = taskService.getTasksByResponsiblesAndObject(result.user, contextOrg,  result.surveyConfig)
            Map<String,Object> preCon = taskService.getPreconditionsWithoutTargets(contextOrg)
            result << preCon

            result.properties = []
            List allProperties = surveyService.getSurveyProperties(contextOrg)
            result.properties = allProperties
            /*allProperties.each {

                if (!(it.id in SurveyConfigProperties.findAllBySurveyConfig(result.surveyConfig)?.surveyProperty.id)) {
                    result.properties << it
                }
            }*/

            if(result.surveyConfig.subSurveyUseForTransfer) {
                result.successorSubscription = result.surveyConfig.subscription._getCalculatedSuccessor()

                result.customProperties = result.successorSubscription ? comparisonService.comparePropertiesWithAudit(result.surveyConfig.subscription.propertySet.findAll{it.type.tenant == null && (it.tenant?.id == contextOrg.id || (it.tenant?.id != contextOrg.id && it.isPublic))} + result.successorSubscription.propertySet.findAll{it.type.tenant == null && (it.tenant?.id == contextOrg.id || (it.tenant?.id != contextOrg.id && it.isPublic))}, true, true) : null
            }


        }

        if ( params.exportXLSX ) {

            SXSSFWorkbook wb
            if ( params.surveyCostItems ) {
                SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
                String datetoday = sdf.format(new Date(System.currentTimeMillis()))
                String filename = "${datetoday}_" + g.message(code: "survey.label")
                //if(wb instanceof XSSFWorkbook) file += "x";
                response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
                response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                wb = (SXSSFWorkbook) surveyService.exportSurveyCostItems([result.surveyConfig], result.institution)
            }else{
                SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
                String datetoday = sdf.format(new Date(System.currentTimeMillis()))
                String filename = "${datetoday}_" + g.message(code: "survey.label")
                //if(wb instanceof XSSFWorkbook) file += "x";
                response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
                response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                wb = (SXSSFWorkbook) surveyService.exportSurveys([result.surveyConfig], result.institution)
            }
            wb.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            wb.dispose()

            return
        }else {
            result
        }
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
    Map<String,Object> surveyTitles() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0

        String base_qry = null
        Map<String,Object> qry_params = [subscription: result.surveyConfig.subscription]

        Date date_filter
        date_filter = new Date()
        result.as_at_date = date_filter
        base_qry = " from IssueEntitlement as ie where ie.subscription = :subscription "
        base_qry += " and (( :startDate >= coalesce(ie.accessStartDate,subscription.startDate) ) OR ( ie.accessStartDate is null )) and ( ( :endDate <= coalesce(ie.accessEndDate,subscription.endDate) ) OR ( ie.accessEndDate is null ) ) "
        qry_params.startDate = date_filter
        qry_params.endDate = date_filter

        base_qry += " and ie.status = :current "
        qry_params.current = RDStore.TIPP_STATUS_CURRENT

        base_qry += "order by lower(ie.tipp.title.title) asc"

        result.num_sub_rows = IssueEntitlement.executeQuery("select ie.id " + base_qry, qry_params).size()

        result.entitlements = IssueEntitlement.executeQuery("select ie " + base_qry, qry_params, [max: result.max, offset: result.offset])

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
    Map<String,Object> surveyConfigDocs() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
    Map<String,Object> surveyParticipants() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()

        // new: filter preset
        params.orgType = RDStore.OT_INSTITUTION.id.toString()
        params.orgSector = RDStore.O_SECTOR_HIGHER_EDU.id.toString()

        result.propList = PropertyDefinition.findAllPublicAndPrivateOrgProp(contextService.org)

        params.comboType = RDStore.COMBO_TYPE_CONSORTIUM.value
        Map<String,Object> fsq = filterService.getOrgComboQuery(params, result.institution)
        def tmpQuery = "select o.id " + fsq.query.minus("select o ")
        def consortiaMemberIds = Org.executeQuery(tmpQuery, fsq.queryParams)

        if (params.filterPropDef && consortiaMemberIds) {
            fsq = propertyService.evalFilterQuery(params, "select o FROM Org o WHERE o.id IN (:oids) order by o.sortname", 'o', [oids: consortiaMemberIds])
        }
        result.consortiaMembers = Org.executeQuery(fsq.query, fsq.queryParams, params)

        if(result.surveyConfig.pickAndChoose){

            List orgs = subscriptionService.getValidSurveySubChildOrgs(result.surveyConfig.subscription)
            result.consortiaMembers = result.consortiaMembers.findAll{ (it in orgs)}
        }

        result.consortiaMembersCount = Org.executeQuery(fsq.query, fsq.queryParams).size()

        result.editable = (result.surveyInfo && result.surveyInfo.status.id != RDStore.SURVEY_IN_PROCESSING.id) ? false : result.editable

        Map<String,Object> surveyOrgs = result.surveyConfig.getSurveyOrgsIDs()

        result.selectedParticipants = surveyService.getfilteredSurveyOrgs(surveyOrgs.orgsWithoutSubIDs, fsq.query, fsq.queryParams, params)
        result.selectedSubParticipants = surveyService.getfilteredSurveyOrgs(surveyOrgs.orgsWithSubIDs, fsq.query, fsq.queryParams, params)

        params.tab = params.tab ?: (result.surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_GENERAL_SURVEY ? 'selectedParticipants' : 'selectedSubParticipants')

        result

    }


    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
    Map<String,Object> surveyCostItems() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()

        result.putAll(financeService.setEditVars(result.institution))

        Map<Long,Object> orgConfigurations = [:]
        result.costItemElements.each { oc ->
            orgConfigurations.put(oc.costItemElement.id,oc.elementSign.id)
        }

        result.orgConfigurations = orgConfigurations as JSON

        params.tab = params.tab ?: 'selectedSubParticipants'

        // new: filter preset
        params.orgType = RDStore.OT_INSTITUTION.id.toString()
        params.orgSector = RDStore.O_SECTOR_HIGHER_EDU.id.toString()

        result.propList = PropertyDefinition.findAllPublicAndPrivateOrgProp(contextService.org)

        params.comboType = RDStore.COMBO_TYPE_CONSORTIUM.value
        Map<String,Object> fsq = filterService.getOrgComboQuery(params, result.institution)
        def tmpQuery = "select o.id " + fsq.query.minus("select o ")
        def consortiaMemberIds = Org.executeQuery(tmpQuery, fsq.queryParams)

        if (params.filterPropDef && consortiaMemberIds) {
            fsq = propertyService.evalFilterQuery(params, "select o FROM Org o WHERE o.id IN (:oids) order by o.sortname", 'o', [oids: consortiaMemberIds])
        }

        result.editable = (result.surveyInfo.status != RDStore.SURVEY_IN_PROCESSING) ? false : result.editable

        //Only SurveyConfigs with Subscriptions
        result.surveyConfigs = result.surveyInfo.surveyConfigs.findAll { it.subscription != null }.sort {
            it.configOrder
        }

        params.surveyConfigID = params.surveyConfigID ?: result.surveyConfigs[0].id.toString()

        result.surveyConfig = SurveyConfig.get(params.surveyConfigID)

        Map<String,Object> surveyOrgs = result.surveyConfig?.getSurveyOrgsIDs()

        result.selectedParticipants = surveyService.getfilteredSurveyOrgs(surveyOrgs.orgsWithoutSubIDs, fsq.query, fsq.queryParams, params)
        result.selectedSubParticipants = surveyService.getfilteredSurveyOrgs(surveyOrgs.orgsWithSubIDs, fsq.query, fsq.queryParams, params)

        result.selectedCostItemElement = params.selectedCostItemElement ?: RefdataValue.getByValueAndCategory('price: consortial price', RDConstants.COST_ITEM_ELEMENT).id.toString()

        if (params.selectedCostItemElement) {
            params.remove('selectedCostItemElement')
        }
        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    Map<String,Object> processSurveyCostItemsBulk() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.putAll(financeService.setEditVars(result.institution))
        List selectedMembers = params.list("selectedOrgs")

        if(selectedMembers) {

            RefdataValue billing_currency = null
            if (params.long('newCostCurrency2')) //GBP,etc
            {
                billing_currency = RefdataValue.get(params.newCostCurrency2)
            }


            NumberFormat format = NumberFormat.getInstance(LocaleContextHolder.getLocale())
            def cost_billing_currency = params.newCostInBillingCurrency2 ? format.parse(params.newCostInBillingCurrency2).doubleValue() : null //0.00
            //def cost_currency_rate = params.newCostCurrencyRate2 ? params.double('newCostCurrencyRate2', 1.00) : null //1.00
            //def cost_local_currency = params.newCostInLocalCurrency2 ? format.parse(params.newCostInLocalCurrency2).doubleValue() : null //0.00

            def tax_key = null
            if (!params.newTaxRate2.contains("null")) {
                String[] newTaxRate = params.newTaxRate2.split("§")
                RefdataValue taxType = (RefdataValue) genericOIDService.resolveOID(newTaxRate[0])
                int taxRate = Integer.parseInt(newTaxRate[1])
                switch (taxType.id) {
                    case RefdataValue.getByValueAndCategory("taxable", RDConstants.TAX_TYPE).id:
                        switch (taxRate) {
                            case 5: tax_key = CostItem.TAX_TYPES.TAXABLE_5
                                break
                            case 7: tax_key = CostItem.TAX_TYPES.TAXABLE_7
                                break
                            case 16: tax_key = CostItem.TAX_TYPES.TAXABLE_16
                                break
                            case 19: tax_key = CostItem.TAX_TYPES.TAXABLE_19
                                break
                        }
                        break
                    case RefdataValue.getByValueAndCategory("taxable tax-exempt", RDConstants.TAX_TYPE).id:
                        tax_key = CostItem.TAX_TYPES.TAX_EXEMPT
                        break
                    case RefdataValue.getByValueAndCategory("not taxable", RDConstants.TAX_TYPE).id:
                        tax_key = CostItem.TAX_TYPES.TAX_NOT_TAXABLE
                        break
                    case RefdataValue.getByValueAndCategory("not applicable", RDConstants.TAX_TYPE).id:
                        tax_key = CostItem.TAX_TYPES.TAX_NOT_APPLICABLE
                        break
                    case RefdataValue.getByValueAndCategory("reverse charge", RDConstants.TAX_TYPE).id:
                        tax_key = CostItem.TAX_TYPES.TAX_REVERSE_CHARGE
                        break
                }

                result.links = linksGenerationService.getSourcesAndDestinations(result.subscription,result.user)
            }
            List<CostItem> surveyCostItems = CostItem.executeQuery('select costItem from CostItem costItem join costItem.surveyOrg surOrg where surOrg.surveyConfig = :survConfig and surOrg.org.id in (:orgIDs) and costItem.costItemStatus != :status', [survConfig:  result.surveyConfig, orgIDs: selectedMembers.collect{Long.parseLong(it)}, status: RDStore.COST_ITEM_DELETED])
            surveyCostItems.each { surveyCostItem ->

                    if(params.deleteCostItems == "true")
                    {
                        surveyCostItem.delete()
                    }
                    else {

                        if (params.percentOnOldPrice) {
                            Double percentOnOldPrice = params.double('percentOnOldPrice', 0.00)
                            Subscription orgSub = result.surveyConfig.subscription.getDerivedSubscriptionBySubscribers(surveyCostItem.surveyOrg.org)
                            CostItem costItem = CostItem.findBySubAndOwnerAndCostItemStatusNotEqualAndCostItemElement(orgSub, surveyCostItem.owner, RDStore.COST_ITEM_DELETED, RDStore.COST_ITEM_ELEMENT_CONSORTIAL_PRICE)
                            surveyCostItem.costInBillingCurrency = costItem ? costItem.costInBillingCurrency * (1 + (percentOnOldPrice / 100)) : surveyCostItem.costInBillingCurrency
                        } else {
                            surveyCostItem.costInBillingCurrency = cost_billing_currency ?: surveyCostItem.costInBillingCurrency
                        }

                        surveyCostItem.billingCurrency = billing_currency ?: surveyCostItem.billingCurrency
                        //Not specified default to GDP
                        //surveyCostItem.costInLocalCurrency = cost_local_currency ?: surveyCostItem.costInLocalCurrency

                        surveyCostItem.finalCostRounding = params.newFinalCostRounding2 ? true : false

                        //surveyCostItem.currencyRate = cost_currency_rate ?: surveyCostItem.currencyRate
                        surveyCostItem.taxKey = tax_key ?: surveyCostItem.taxKey

                        surveyCostItem.save()
                    }
            }
        }

        redirect(url: request.getHeader('referer'))
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    Map<String,Object> surveyConfigFinish() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.surveyConfig.configFinish = params.configFinish ?: false
        if (result.surveyConfig.save()) {
            //flash.message = g.message(code: 'survey.change.successfull')
        } else {
            flash.error = g.message(code: 'survey.change.fail')
        }

        redirect(url: request.getHeader('referer'))

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    Map<String,Object> surveyCostItemsFinish() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.surveyConfig.costItemsFinish = params.costItemsFinish ?: false

        if (result.surveyConfig.save()) {
            //flash.message = g.message(code: 'survey.change.successfull')
        } else {
            flash.error = g.message(code: 'survey.change.fail')
        }

        redirect(url: request.getHeader('referer'))

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    Map<String,Object> surveyTransferConfig() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        Map transferWorkflow = result.surveyConfig.transferWorkflow ? JSON.parse(result.surveyConfig.transferWorkflow) : [:]

        if(params.transferMembers != null)
        {
            transferWorkflow.transferMembers = params.transferMembers
        }

        if(params.transferSurveyCostItems != null)
        {
            transferWorkflow.transferSurveyCostItems = params.transferSurveyCostItems
        }

        if(params.transferSurveyProperties != null)
        {
            transferWorkflow.transferSurveyProperties = params.transferSurveyProperties
        }

        if(params.transferCustomProperties != null)
        {
            transferWorkflow.transferCustomProperties = params.transferCustomProperties
        }

        if(params.transferPrivateProperties != null)
        {
            transferWorkflow.transferPrivateProperties = params.transferPrivateProperties
        }

        result.surveyConfig.transferWorkflow = transferWorkflow ?  (new JSON(transferWorkflow)).toString() : null

        if (result.surveyConfig.save()) {
            //flash.message = g.message(code: 'survey.change.successfull')
        } else {
            flash.error = g.message(code: 'survey.change.fail')
        }

        redirect(url: request.getHeader('referer'))

    }


    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
    Map<String,Object> surveyEvaluation() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()

        params.tab = params.tab ?: 'participantsViewAllFinish'

        if ( params.exportXLSX ) {
            SXSSFWorkbook wb
            if ( params.surveyCostItems ) {
                SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
                String datetoday = sdf.format(new Date(System.currentTimeMillis()))
                String filename = "${datetoday}_" + g.message(code: "survey.label")
                //if(wb instanceof XSSFWorkbook) file += "x";
                response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
                response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                wb = (SXSSFWorkbook) surveyService.exportSurveyCostItems([result.surveyConfig], result.institution)
            }else {
                SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
                String datetoday = sdf.format(new Date(System.currentTimeMillis()))
                String filename = "${datetoday}_" + g.message(code: "survey.label")
                //if(wb instanceof XSSFWorkbook) file += "x";
                response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
                response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                wb = (SXSSFWorkbook) surveyService.exportSurveys([result.surveyConfig], result.institution)
            }
            wb.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            wb.dispose()

            return
        }else {

            if(params.tab == 'participantsViewAllNotFinish'){
                params.participantsNotFinish = true
            }
            if(params.tab == 'participantsViewAllFinish'){
                params.participantsFinish = true
            }

            result.participantsNotFinishTotal = SurveyResult.findAllBySurveyConfigAndFinishDateIsNull(result.surveyConfig).participant.flatten().unique { a, b -> a.id <=> b.id }.size()
            result.participantsFinishTotal = SurveyResult.findAllBySurveyConfigAndFinishDateIsNotNull(result.surveyConfig).participant.flatten().unique { a, b -> a.id <=> b.id }.size()
            result.participantsTotal = result.surveyConfig.orgs.size()

             Map<String,Object> fsq = filterService.getSurveyResultQuery(params, result.surveyConfig)

            result.surveyResult = SurveyResult.executeQuery(fsq.query, fsq.queryParams, params)


            result.propList    = result.surveyConfig.surveyProperties.surveyProperty
            result
        }

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
     Map<String,Object> surveyTransfer() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()

         Map<String,Object> fsq = filterService.getSurveyResultQuery(params, result.surveyConfig)

        result.surveyResult = SurveyResult.executeQuery(fsq.query, fsq.queryParams, params)

        result.availableSubscriptions = subscriptionService.getMySubscriptions_writeRights([status: RDStore.SUBSCRIPTION_CURRENT.id])

        result.propList    = result.surveyConfig.surveyProperties.surveyProperty
        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> processTransferParticipants() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        if(!params.targetSubscriptionId)
        {
            flash.error = g.message(code: "surveyTransfer.error.noSelectedSub")
            redirect(url: request.getHeader('referer'))
            return
        }
        result.parentSubscription = result.surveyConfig.subscription
        result.targetParentSub = Subscription.get(params.targetSubscriptionId)
        result.targetParentSubChilds = result.targetParentSub ? subscriptionService.getValidSubChilds(result.targetParentSub) : null

        result.targetParentSubParticipantsList = []

        result.targetParentSubChilds.each { sub ->
            Org org = sub.getSubscriber()
            result.targetParentSubParticipantsList << org

        }

        RefdataValue role_sub       = RDStore.OR_SUBSCRIBER_CONS
        RefdataValue role_sub_cons  = RDStore.OR_SUBSCRIPTION_CONSORTIA

        result.newSubs = []
        Integer countNewSubs = 0

        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        Date startDate = params.startDate ? sdf.parse(params.startDate) : null
        Date endDate = params.endDate ? sdf.parse(params.endDate) : null

        params.list('selectedOrgs').each { orgId ->
            Org org = Org.get(orgId)
            if(org && result.parentSubscription && result.targetParentSub && !(org in result.targetParentSubParticipantsList)){
                log.debug("Generating seperate slaved instances for members")


                Subscription memberSub = new Subscription(
                        type: result.targetParentSub.type ?: null,
                        kind: result.targetParentSub.kind ?: null,
                        status: result.targetParentSub.status ?: null,
                        name: result.targetParentSub.name,
                        startDate: startDate,
                        endDate: endDate,
                        administrative: result.targetParentSub._getCalculatedType() == CalculatedType.TYPE_ADMINISTRATIVE,
                        manualRenewalDate: result.targetParentSub.manualRenewalDate,
                        identifier: UUID.randomUUID().toString(),
                        instanceOf: result.targetParentSub,
                        isSlaved: true,
                        resource: result.targetParentSub.resource ?: null,
                        form: result.targetParentSub.form ?: null,
                        isPublicForApi: result.targetParentSub.isPublicForApi,
                        hasPerpetualAccess: result.targetParentSub.hasPerpetualAccess,
                        isMultiYear: false
                )

                if (!memberSub.save(flush:true)) {
                    memberSub.errors.each { e ->
                        log.debug("Problem creating new sub: ${e}")
                    }
                }

                if (memberSub) {

                    new OrgRole(org: org, sub: memberSub, roleType: role_sub).save()
                    new OrgRole(org: result.institution, sub: memberSub, roleType: role_sub_cons).save()


                    SubscriptionProperty.findAllByOwner(result.targetParentSub).each { scp ->
                        AuditConfig ac = AuditConfig.getConfig(scp)

                        if (ac) {
                            // multi occurrence props; add one additional with backref
                            if (scp.type.multipleOccurrence) {
                                def additionalProp = PropertyDefinition.createGenericProperty(PropertyDefinition.CUSTOM_PROPERTY, memberSub, scp.type, scp.tenant)
                                additionalProp = scp.copyInto(additionalProp)
                                additionalProp.instanceOf = scp
                                additionalProp.save()
                            } else {
                                // no match found, creating new prop with backref
                                def newProp = PropertyDefinition.createGenericProperty(PropertyDefinition.CUSTOM_PROPERTY, memberSub, scp.type, scp.tenant)
                                newProp = scp.copyInto(newProp)
                                newProp.instanceOf = scp
                                newProp.save()
                            }
                        }
                    }
                }

                result.newSubs << memberSub
            }
            countNewSubs++
        }


        result.countNewSubs = countNewSubs
        if(result.newSubs?.size() > 0) {
            result.targetParentSub.syncAllShares(result.newSubs)
        }
        flash.message = message(code: 'surveyInfo.transfer.info', args: [countNewSubs, result.newSubs?.size() ?: 0])


        redirect(action: 'compareMembersOfTwoSubs', id: params.id, params: [surveyConfigID: result.surveyConfig.id, targetSubscriptionId: result.targetParentSub?.id])

    }


    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
     Map<String,Object> openParticipantsAgain() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()

        params.tab = params.tab ?: 'participantsViewAllFinish'
        if(params.tab == 'participantsViewAllNotFinish'){
            params.participantsNotFinish = true
        }
        if(params.tab == 'participantsViewAllFinish'){
            params.participantsFinish = true
        }

         Map<String,Object> fsq = filterService.getSurveyResultQuery(params, result.surveyConfig)

        result.surveyResult = SurveyResult.executeQuery(fsq.query, fsq.queryParams, params)

        result.participantsNotFinishTotal = SurveyResult.findAllBySurveyConfigAndFinishDateIsNull(result.surveyConfig).participant.flatten().unique { a, b -> a.id <=> b.id }.size()
        result.participantsFinishTotal = SurveyResult.findAllBySurveyConfigAndFinishDateIsNotNull(result.surveyConfig).participant.flatten().unique { a, b -> a.id <=> b.id }.size()

        result.propList    = result.surveyConfig.surveyProperties.surveyProperty

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
     Map<String,Object> processOpenParticipantsAgain() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()

        result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        result.editable = (result.surveyInfo && result.surveyInfo.status in [RDStore.SURVEY_SURVEY_STARTED]) ? result.editable : false

        Integer countReminderMails = 0
        Integer countOpenParticipants = 0
        boolean reminderMail = (params.openOption == 'ReminderMail')  ?: false
        boolean openAndSendMail = (params.openOption == 'OpenWithMail')  ?: false
        boolean open = (params.openOption == 'OpenWithoutMail') ?: false

        if (params.selectedOrgs && result.editable) {

            params.list('selectedOrgs').each { soId ->

                Org org = Org.get(Long.parseLong(soId))

                if(openAndSendMail || open) {
                    if (result.surveyConfig.pickAndChoose) {
                        SurveyOrg surveyOrg = SurveyOrg.findByOrgAndSurveyConfig(org, result.surveyConfig)

                        result.subscriptionInstance = result.surveyConfig.subscription

                        List<IssueEntitlement> ies = subscriptionService.getIssueEntitlementsUnderNegotiation(result.surveyConfig.subscription.getDerivedSubscriptionBySubscribers(org))

                        ies.each { ie ->
                            ie.acceptStatus = RDStore.IE_ACCEPT_STATUS_UNDER_CONSIDERATION
                            ie.save()
                        }

                        surveyOrg.finishDate = null
                        surveyOrg.save()
                    }

                    List<SurveyResult> surveyResults = SurveyResult.findAllByParticipantAndSurveyConfig(org, result.surveyConfig)

                    surveyResults.each {
                        it.finishDate = null
                        it.save()
                    }
                    countOpenParticipants++
                }

                if(openAndSendMail) {
                    surveyService.emailsToSurveyUsersOfOrg(result.surveyInfo, org, false)
                }
                if(reminderMail) {
                    surveyService.emailsToSurveyUsersOfOrg(result.surveyInfo, org, true)
                    countReminderMails++
                }

            }
        }

        if(countReminderMails > 0){
            flash.message =  g.message(code: 'openParticipantsAgain.sendReminderMail.count', args: [countReminderMails])
        }

        if(countOpenParticipants > 0 && !openAndSendMail){
            flash.message =  g.message(code: 'openParticipantsAgain.open.count', args: [countOpenParticipants])
        }

        if(countOpenParticipants > 0 && openAndSendMail){
            flash.message =  g.message(code: 'openParticipantsAgain.openWithMail.count', args: [countOpenParticipants])
        }

        redirect(action: 'openParticipantsAgain', id: result.surveyInfo.id, params:[tab: params.tab, surveyConfigID: result.surveyConfig.id])

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
     Map<String,Object> surveyTitlesEvaluation() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()

        result.propList = PropertyDefinition.findAllPublicAndPrivateOrgProp(contextService.org)

        def orgs = result.surveyConfig.orgs.org.flatten().unique { a, b -> a.id <=> b.id }
        result.participants = orgs.sort { it.sortname }

        result.participantsNotFinish = SurveyOrg.findAllByFinishDateIsNullAndSurveyConfig(result.surveyConfig).org.flatten().unique { a, b -> a.id <=> b.id }.sort {
            it.sortname
        }
        result.participantsFinish = SurveyOrg.findAllByFinishDateIsNotNullAndSurveyConfig(result.surveyConfig).org.flatten().unique { a, b -> a.id <=> b.id }.sort {
            it.sortname
        }

        if(result.surveyConfig.surveyProperties.size() > 0){
            result.surveyResult = SurveyResult.findAllByOwnerAndSurveyConfig(result.institution, result.surveyConfig).sort {
                it.participant.sortname
            }
        }

        if ( params.exportXLSX ) {
            SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
            String datetoday = sdf.format(new Date(System.currentTimeMillis()))
            String filename = "${datetoday}_" + g.message(code: "survey.label")
            //if(wb instanceof XSSFWorkbook) file += "x";
            response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            SXSSFWorkbook wb = (SXSSFWorkbook) surveyService.exportSurveys([result.surveyConfig], result.institution)
            wb.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            wb.dispose()

            return
        }else {
                    result
            }
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
     Map<String,Object> showEntitlementsRenew() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)
        result.participant = params.participant ? Org.get(params.participant) : null

        result.surveyConfig = SurveyConfig.get(params.id)
        result.surveyInfo = result.surveyConfig.surveyInfo

        result.editable = result.surveyInfo.isEditable() ?: false

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        result.surveyOrg = SurveyOrg.findByOrgAndSurveyConfig(result.participant, result.surveyConfig)


        result.subscriptionParticipant = result.surveyConfig.subscription?.getDerivedSubscriptionBySubscribers(result.participant)

        result.ies = subscriptionService.getIssueEntitlementsNotFixed(result.subscriptionParticipant)

        String filename = "renewEntitlements_${escapeService.escapeString(result.surveyConfig.subscription.dropdownNamingConvention(result.participant))}"

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
        }else if(params.exportXLSX) {
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

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
     Map<String,Object> surveyTitlesSubscriber() {
        Map<String, Object> result = setResultGenericsAndCheckAccess()
        result.participant = params.participant ? Org.get(params.participant) : null

        result.surveyOrg = SurveyOrg.findByOrgAndSurveyConfig(result.participant, result.surveyConfig)

        result.editable = result.surveyInfo.isEditable() ?: false

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        result.subscriptionInstance = result.surveyConfig.subscription.getDerivedSubscriptionBySubscribers(result.participant)

        result.ies = subscriptionService.getIssueEntitlementsNotFixed(result.subscriptionInstance)
        result.iesListPriceSum = 0
        result.ies.each{
            result.iesListPriceSum = result.iesListPriceSum + (it.priceItem ? (it.priceItem.listPrice ? it.priceItem.listPrice : 0) : 0)
        }


        result.iesFix = subscriptionService.getIssueEntitlementsFixed(result.subscriptionInstance)
        result.iesFixListPriceSum = 0
        result.iesFix.each{
            result.iesFixListPriceSum = result.iesFixListPriceSum + (it.priceItem ? (it.priceItem.listPrice ? it.priceItem.listPrice : 0) : 0)
        }


        result.ownerId = result.surveyConfig.surveyInfo.owner.id ?: null

        if(result.subscriptionInstance) {
            result.authorizedOrgs = result.user.authorizedOrgs
            result.contextOrg = result.institution
            // restrict visible for templates/links/orgLinksAsList
            result.visibleOrgRelations = []
            result.subscriptionInstance.orgRelations.each { OrgRole or ->
                if (!(or.org.id == result.contextOrg.id) && !(or.roleType in [RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS])) {
                    result.visibleOrgRelations << or
                }
            }
            result.visibleOrgRelations.sort { it.org.sortname }
            result.links = linksGenerationService.getSourcesAndDestinations(result.subscriptionInstance,result.user)
        }

        result.surveyResults = SurveyResult.findAllByParticipantAndSurveyConfig(result.participant, result.surveyConfig).sort { it.surveyConfig.configOrder }

        result

    }


    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> openIssueEntitlementsSurveyAgain() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)
        result.participant = params.participant ? Org.get(params.participant) : null

        result.surveyConfig = SurveyConfig.get(params.id)
        result.surveyInfo = result.surveyConfig.surveyInfo

        result.editable = result.surveyInfo.isEditable() ?: false

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        SurveyOrg surveyOrg = SurveyOrg.findByOrgAndSurveyConfig(result.participant, result.surveyConfig)

        result.subscriptionInstance =  result.surveyConfig.subscription

        List ies = subscriptionService.getIssueEntitlementsUnderNegotiation(result.surveyConfig.subscription.getDerivedSubscriptionBySubscribers(result.participant))

        ies.each { ie ->
            ie.acceptStatus = RDStore.IE_ACCEPT_STATUS_UNDER_CONSIDERATION
            ie.save()
        }

        surveyOrg.finishDate = null
        surveyOrg.save()

        //flash.message = message(code: 'openIssueEntitlementsSurveyAgain.info')

        redirect(action: 'showEntitlementsRenew', id: result.surveyConfig.id, params:[participant: result.participant.id])

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> openSurveyAgainForParticipant() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)
        result.participant = params.participant ? Org.get(params.participant) : null

        result.surveyConfig = SurveyConfig.get(params.surveyConfigID)
        result.surveyInfo = result.surveyConfig.surveyInfo

        result.editable = result.surveyInfo.isEditable() ?: false

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        List<SurveyResult> surveyResults = SurveyResult.findAllByParticipantAndSurveyConfig(result.participant, result.surveyConfig)

        surveyResults.each {
                it.finishDate = null
                it.save(flush:true)
        }

        redirect(action: 'evaluationParticipant', id: result.surveyInfo.id, params:[surveyConfigID: result.surveyConfig.id, participant: result.participant.id])

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM_SURVEY", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM_SURVEY", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> completeIssueEntitlementsSurveyforParticipant() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)
        result.participant = params.participant ? Org.get(params.participant) : null

        result.surveyConfig = SurveyConfig.get(params.id)
        result.surveyInfo = result.surveyConfig.surveyInfo

        result.editable = result.surveyInfo.isEditable() ?: false

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        Subscription participantSub = result.surveyConfig.subscription.getDerivedSubscriptionBySubscribers(result.participant)
        List<IssueEntitlement> ies = subscriptionService.getIssueEntitlementsUnderNegotiation(participantSub)

        IssueEntitlementGroup issueEntitlementGroup
        if(result.surveyConfig.createTitleGroups){

            Integer countTitleGroups = IssueEntitlementGroup.findAllBySubAndNameIlike(participantSub, 'Phase').size()

            issueEntitlementGroup = new IssueEntitlementGroup(sub: participantSub, name: "Phase ${countTitleGroups+1}").save(flush:true)
        }

        ies.each { ie ->
            ie.acceptStatus = RDStore.IE_ACCEPT_STATUS_FIXED
            ie.save()

            if(issueEntitlementGroup){
                //println(issueEntitlementGroup)
                IssueEntitlementGroupItem issueEntitlementGroupItem = new IssueEntitlementGroupItem(
                        ie: ie,
                        ieGroup: issueEntitlementGroup)

                if (!issueEntitlementGroupItem.save()) {
                    log.error("Problem saving IssueEntitlementGroupItem by Survey ${issueEntitlementGroupItem.errors}")
                }
            }
        }

        flash.message = message(code: 'completeIssueEntitlementsSurvey.forParticipant.info')

        redirect(action: 'showEntitlementsRenew', id: result.surveyConfig.id, params:[participant: result.participant.id])

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> completeIssueEntitlementsSurvey() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.surveyConfig = SurveyConfig.get(params.id)
        result.surveyInfo = result.surveyConfig.surveyInfo

        result.editable = result.surveyInfo.isEditable() ?: false

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        def participantsFinish = SurveyOrg.findAllByFinishDateIsNotNullAndSurveyConfig(result.surveyConfig)?.org.flatten().unique { a, b -> a.id <=> b.id }

        participantsFinish.each { org ->
            Subscription participantSub = result.surveyConfig.subscription.getDerivedSubscriptionBySubscribers(org)

            List<IssueEntitlement> ies = subscriptionService.getIssueEntitlementsUnderNegotiation(participantSub)

            IssueEntitlementGroup issueEntitlementGroup
            if(result.surveyConfig.createTitleGroups){

                Integer countTitleGroups = IssueEntitlementGroup.findAllBySubAndNameIlike(participantSub, 'Phase').size()

                issueEntitlementGroup = new IssueEntitlementGroup(sub: participantSub, name: "Phase ${countTitleGroups+1}").save(flush:true)
            }

            ies.each { ie ->
                ie.acceptStatus = RDStore.IE_ACCEPT_STATUS_FIXED
                ie.save()

                if(issueEntitlementGroup){
                    IssueEntitlementGroupItem issueEntitlementGroupItem = new IssueEntitlementGroupItem(
                            ie: ie,
                            ieGroup: issueEntitlementGroup)

                    if (!issueEntitlementGroupItem.save()) {
                        log.error("Problem saving IssueEntitlementGroupItem by Survey ${issueEntitlementGroupItem.errors}")
                    }
                }
            }
        }

        flash.message = message(code: 'completeIssueEntitlementsSurvey.forFinishParticipant.info')

        redirect(action: 'surveyTitlesEvaluation', id: result.surveyInfo.id, params:[surveyConfigID: result.surveyConfig.id])

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
     Map<String,Object> evaluateIssueEntitlementsSurvey() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        result.participant = params.participant ? Org.get(params.participant) : null

        result.surveyConfig = SurveyConfig.get(params.id)
        result.surveyInfo = result.surveyConfig.surveyInfo

        result.surveyOrg = SurveyOrg.findByOrgAndSurveyConfig(result.participant, result.surveyConfig)

        result.subscriptionInstance =  result.surveyConfig.subscription

        result.ies = subscriptionService.getCurrentIssueEntitlements(result.surveyConfig.subscription.getDerivedSubscriptionBySubscribers(result.participant))

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
     Map<String,Object> evaluationParticipant() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.participant = Org.get(params.participant)

        result.surveyInfo = SurveyInfo.get(params.id) ?: null

        result.surveyConfig = SurveyConfig.get(params.surveyConfigID)

        result.surveyResults = SurveyResult.findAllByParticipantAndSurveyConfig(result.participant, result.surveyConfig)

        result.ownerId = result.surveyResults[0].owner.id

        if(result.surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION) {
            result.subscriptionInstance = result.surveyConfig.subscription.getDerivedSubscriptionBySubscribers(result.participant)
            result.subscription =  result.subscriptionInstance ?: null
            // restrict visible for templates/links/orgLinksAsList
            result.visibleOrgRelations = []
            result.costItemSums = [:]
            if(result.subscriptionInstance) {
                result.subscriptionInstance.orgRelations.each { OrgRole or ->
                    if (!(or.org.id == result.institution.id) && !(or.roleType in [RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS])) {
                        result.visibleOrgRelations << or
                    }
                }
                result.visibleOrgRelations.sort { it.org.sortname }

            //costs dataToDisplay
            result.dataToDisplay = ['subscr']
            result.offsets = [subscrOffset:0]
            result.sortConfig = [subscrSort:'sub.name',subscrOrder:'asc']
            //result.dataToDisplay = ['consAtSubscr']
            //result.offsets = [consOffset:0]
            //result.sortConfig = [consSort:'ci.costTitle',consOrder:'asc']

            result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
            //cost items
            //params.forExport = true
            LinkedHashMap costItems = result.subscription ? financeService.getCostItemsForSubscription(params, result) : null
            result.costItemSums = [:]
            /*if (costItems?.cons) {
                result.costItemSums.consCosts = costItems.cons.sums
            }*/
            if (costItems?.subscr) {
                result.costItemSums.subscrCosts = costItems.subscr.costItems
            }
            result.links = linksGenerationService.getSourcesAndDestinations(result.subscriptionInstance,result.user)    
        }

            if(result.surveyConfig.subSurveyUseForTransfer) {
                result.successorSubscription = result.surveyConfig.subscription._getCalculatedSuccessor()

                result.customProperties = result.successorSubscription ? comparisonService.comparePropertiesWithAudit(result.surveyConfig.subscription.propertySet.findAll{it.type.tenant == null && (it.tenant?.id == result.contextOrg.id || (it.tenant?.id != result.contextOrg.id && it.isPublic))} + result.successorSubscription.propertySet.findAll{it.type.tenant == null && (it.tenant?.id == result.contextOrg.id || (it.tenant?.id != result.contextOrg.id && it.isPublic))}, true, true) : null
            }
            result.links = linksGenerationService.getSourcesAndDestinations(result.subscriptionInstance,result.user)
        }

        result.editable = surveyService.isEditableSurvey(result.institution, result.surveyInfo)
        result.institution = result.participant

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
     Map<String,Object> allSurveyProperties() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!accessService.checkPermAffiliationX('ORG_CONSORTIUM','INST_USER','ROLE_ADMIN')) {
            response.sendError(401); return
        }

        result.properties = surveyService.getSurveyProperties(result.institution)

        result.language = LocaleContextHolder.getLocale().toString()

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> addSurveyPropToConfig() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.editable = (result.surveyInfo && result.surveyInfo.status != RDStore.SURVEY_IN_PROCESSING) ? false : result.editable

        if (result.surveyInfo && result.editable) {

            if (params.selectedProperty) {
                PropertyDefinition property = PropertyDefinition.get(Long.parseLong(params.selectedProperty))
                //Config is Sub
                if (params.surveyConfigID) {
                    SurveyConfig surveyConfig = SurveyConfig.get(Long.parseLong(params.surveyConfigID))

                    if (surveyService.addSurPropToSurvey(surveyConfig, property)) {

                        //flash.message = g.message(code: "surveyConfigs.property.add.successfully")

                    } else {
                        flash.error = g.message(code: "surveyConfigs.property.exists")
                    }
                }
            }
        }
        redirect(url: request.getHeader('referer'))

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> deleteSurveyPropFromConfig() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        SurveyConfigProperties surveyConfigProp = SurveyConfigProperties.get(params.id)

        SurveyInfo surveyInfo = surveyConfigProp.surveyConfig.surveyInfo

        result.editable = (surveyInfo && surveyInfo.status != RDStore.SURVEY_IN_PROCESSING) ? false : result.editable

        if (result.editable) {
            try {
                surveyConfigProp.delete()
                //flash.message = g.message(code: "default.deleted.message", args: [g.message(code: "surveyProperty.label"), ''])
            }
            catch (DataIntegrityViolationException e) {
                flash.error = g.message(code: "default.not.deleted.message", args: [g.message(code: "surveyProperty.label"), ''])
            }
        }

        redirect(url: request.getHeader('referer'))

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> createSurveyProperty() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        PropertyDefinition surveyProperty = PropertyDefinition.findWhere(
                name: params.pd_name,
                type: params.pd_type,
                tenant: result.institution,
                descr: PropertyDefinition.SVY_PROP
        )

        if ((!surveyProperty) && params.pd_name && params.pd_type) {
            RefdataCategory rdc
            if (params.refdatacategory) {
                rdc = RefdataCategory.findById(Long.parseLong(params.refdatacategory))
            }

            Map<String, Object> map = [
                    token       : params.pd_name,
                    category    : PropertyDefinition.SVY_PROP,
                    type        : params.pd_type,
                    rdc         : rdc ? rdc.getDesc() : null,
                    tenant      : result.institution.globalUID,
                    i10n        : [
                            name_de: params.pd_name,
                            name_en: params.pd_name,
                            expl_de: params.pd_expl,
                            expl_en: params.pd_expl
                    ]
            ]

            if (PropertyDefinition.construct(map)) {
                //flash.message = message(code: 'surveyProperty.create.successfully', args: [surveyProperty.name])
            } else {
                flash.error = message(code: 'surveyProperty.create.fail')
            }
        } else if (surveyProperty) {
            flash.error = message(code: 'surveyProperty.create.exist')
        } else {
            flash.error = message(code: 'surveyProperty.create.fail')
        }

        redirect(url: request.getHeader('referer'))

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> deleteSurveyProperty() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        PropertyDefinition surveyProperty = PropertyDefinition.findByIdAndTenant(params.deleteId, result.institution)

        if (surveyProperty.countUsages()==0 && surveyProperty.owner.id == result.institution.id && surveyProperty.delete(flush:true))
        {
            //flash.message = message(code: 'default.deleted.message', args:[message(code: 'surveyProperty.label'), surveyProperty.getI10n('name')])
        }

        redirect(action: 'allSurveyProperties', id: params.id)

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> addSurveyParticipants() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        SurveyConfig surveyConfig = SurveyConfig.get(params.surveyConfigID)
        SurveyInfo surveyInfo = surveyConfig.surveyInfo

        result.editable = (surveyInfo && surveyInfo.status in [RDStore.SURVEY_IN_PROCESSING, RDStore.SURVEY_READY, RDStore.SURVEY_SURVEY_STARTED]) ? result.editable : false

        if (params.selectedOrgs && result.editable) {

            params.list('selectedOrgs').each { soId ->

                Org org = Org.get(Long.parseLong(soId))

                boolean existsMultiYearTerm = false
                Subscription sub = surveyConfig.subscription
                if (sub && !surveyConfig.pickAndChoose && surveyConfig.subSurveyUseForTransfer) {
                    Subscription subChild = sub.getDerivedSubscriptionBySubscribers(org)

                    if (subChild && subChild.isCurrentMultiYearSubscriptionNew()) {
                        existsMultiYearTerm = true
                    }

                }

                if (!(SurveyOrg.findAllBySurveyConfigAndOrg(surveyConfig, org)) && !existsMultiYearTerm) {
                    SurveyOrg surveyOrg = new SurveyOrg(
                            surveyConfig: surveyConfig,
                            org: org
                    )

                    if (!surveyOrg.save()) {
                        log.debug("Error by add Org to SurveyOrg ${surveyOrg.errors}");
                    } else {
                        if(surveyInfo.status in [RDStore.SURVEY_READY, RDStore.SURVEY_SURVEY_STARTED]){
                            surveyConfig.surveyProperties.each { property ->

                                SurveyResult surveyResult = new SurveyResult(
                                        owner: result.institution,
                                        participant: org ?: null,
                                        startDate: surveyInfo.startDate,
                                        endDate: surveyInfo.endDate ?: null,
                                        type: property.surveyProperty,
                                        surveyConfig: surveyConfig
                                )

                                if (surveyResult.save()) {
                                    log.debug( surveyResult.toString() )
                                } else {
                                    log.error("Not create surveyResult: "+ surveyResult)
                                }
                            }

                            if(surveyInfo.status == RDStore.SURVEY_SURVEY_STARTED){
                                surveyService.emailsToSurveyUsersOfOrg(surveyInfo, org, false)
                            }
                        }
                    }
                }
            }
            surveyConfig.save()

        }

        redirect action: 'surveyParticipants', id: params.id, params: [surveyConfigID: params.surveyConfigID]

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> processOpenSurvey() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.editable = (result.surveyInfo && result.surveyInfo.status != RDStore.SURVEY_IN_PROCESSING) ? false : result.editable

        if (result.editable) {

            result.surveyConfigs = result.surveyInfo.surveyConfigs.sort { it.configOrder }

            result.surveyConfigs.each { config ->

                config.orgs.org.each { org ->

                    config.surveyProperties.each { property ->
                        if (!SurveyResult.findWhere(owner: result.institution, participant: org, type: property.surveyProperty, surveyConfig: config)) {
                            SurveyResult surveyResult = new SurveyResult(
                                    owner: result.institution,
                                    participant: org,
                                    startDate: result.surveyInfo.startDate,
                                    endDate: result.surveyInfo.endDate ?: null,
                                    type: property.surveyProperty,
                                    surveyConfig: config
                            )

                            if (surveyResult.save()) {
                                log.debug(surveyResult.toString())
                            } else {
                                log.error("Not create surveyResult: " + surveyResult)
                            }
                        }
                    }
                }
            }

            result.surveyInfo.status = RDStore.SURVEY_READY
            result.surveyInfo.save()
            flash.message = g.message(code: "openSurvey.successfully")
        }

        redirect action: 'show', id: params.id
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> processEndSurvey() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        if (result.editable) {

            result.surveyInfo.status = RDStore.SURVEY_IN_EVALUATION
            result.surveyInfo.save()
            flash.message = g.message(code: "endSurvey.successfully")
        }

        if(result.surveyConfig && result.surveyConfig.subSurveyUseForTransfer) {
            redirect action: 'renewalWithSurvey', params: [surveyConfigID: result.surveyConfig.id, id: result.surveyInfo.id]
        }else{
            redirect(uri: request.getHeader('referer'))
        }
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> processBackInProcessingSurvey() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        if (result.editable) {

            result.surveyInfo.status = RDStore.SURVEY_IN_PROCESSING
            result.surveyInfo.save()
        }

        redirect(uri: request.getHeader('referer'))
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> processOpenSurveyNow() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.editable = (result.surveyInfo && result.surveyInfo.status != RDStore.SURVEY_IN_PROCESSING) ? false : result.editable

        Date currentDate = new Date(System.currentTimeMillis())

        if (result.editable) {

            result.surveyConfigs = result.surveyInfo.surveyConfigs.sort { it.configOrder }

            result.surveyConfigs.each { config ->
                config.orgs.org.each { org ->

                    config.surveyProperties.each { property ->

                        if (!SurveyResult.findWhere(owner: result.institution, participant: org, type: property.surveyProperty, surveyConfig: config)) {
                            SurveyResult surveyResult = new SurveyResult(
                                    owner: result.institution,
                                    participant: org,
                                    startDate: currentDate,
                                    endDate: result.surveyInfo.endDate,
                                    type: property.surveyProperty,
                                    surveyConfig: config
                            )

                            if (surveyResult.save()) {
                                log.debug(surveyResult.toString())
                            } else {
                                log.debug(surveyResult.toString())
                            }
                        }
                    }
                }
            }

            result.surveyInfo.status = RDStore.SURVEY_SURVEY_STARTED
            result.surveyInfo.startDate = currentDate
            result.surveyInfo.save()
            flash.message = g.message(code: "openSurveyNow.successfully")

            surveyService.emailsToSurveyUsers([result.surveyInfo.id])

        }

        redirect action: 'show', id: params.id
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> openSurveyAgain() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        if(result.surveyInfo && result.surveyInfo.status.id in [RDStore.SURVEY_IN_EVALUATION.id, RDStore.SURVEY_COMPLETED.id, RDStore.SURVEY_SURVEY_COMPLETED.id ]){

            SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
            Date endDate = params.newEndDate ? sdf.parse(params.newEndDate) : null

            if(result.surveyInfo.startDate != null && endDate != null) {
                if(result.surveyInfo.startDate > endDate) {
                    flash.error = g.message(code: "openSurveyAgain.fail.startDateAndEndDate")
                    redirect(uri: request.getHeader('referer'))
                    return
                }
            }

            result.surveyInfo.status = RDStore.SURVEY_SURVEY_STARTED
            result.surveyInfo.endDate = endDate
            result.surveyInfo.save()
        }

        redirect action: 'show', id: params.id

    }

        @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> deleteSurveyParticipants() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.editable = (result.surveyInfo && result.surveyInfo.status != RDStore.SURVEY_IN_PROCESSING) ? false : result.editable

        if (params.selectedOrgs && result.editable) {

            params.list('selectedOrgs').each { soId ->
                SurveyOrg surveyOrg = SurveyOrg.findBySurveyConfigAndOrg(result.surveyConfig, Org.get(Long.parseLong(soId)))

                CostItem.findAllBySurveyOrg(surveyOrg).each {
                    it.delete()
                }

                SurveyResult.findAllBySurveyConfigAndParticipant(result.surveyConfig, surveyOrg.org).each {
                    it.delete()
                }

                if (surveyOrg.delete()) {
                    //flash.message = g.message(code: "surveyParticipants.delete.successfully")
                }
            }
        }

        redirect(uri: request.getHeader('referer'))

    }


    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> deleteDocuments() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        log.debug("deleteDocuments ${params}");

        docstoreService.unifiedDeleteDocuments(params)

        redirect action: 'surveyConfigDocs', id: result.surveyInfo.id, params: [surveyConfigID: result.surveyConfig.id]
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> deleteSurveyInfo() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.editable = (result.surveyInfo.status in [RDStore.SURVEY_IN_PROCESSING, RDStore.SURVEY_READY])

        if (result.editable) {

            try {

                SurveyInfo surveyInfo = SurveyInfo.get(result.surveyInfo.id)
                SurveyInfo.withTransaction {

                    SurveyConfig.findAllBySurveyInfo(surveyInfo).each { config ->

                        DocContext.findAllBySurveyConfig(config).each {
                            it.delete()
                        }

                        SurveyConfigProperties.findAllBySurveyConfig(config).each {
                            it.delete()
                        }

                        SurveyOrg.findAllBySurveyConfig(config).each { surveyOrg ->
                            CostItem.findAllBySurveyOrg(surveyOrg).each {
                                it.delete()
                            }

                            surveyOrg.delete()
                        }

                        SurveyResult.findAllBySurveyConfig(config) {
                            it.delete()
                        }

                        Task.findAllBySurveyConfig(config) {
                            it.delete()
                        }
                    }

                    SurveyConfig.executeUpdate("delete from SurveyConfig sc where sc.id in (:surveyConfigIDs)", [surveyConfigIDs: SurveyConfig.findAllBySurveyInfo(surveyInfo).id])


                    surveyInfo.delete()
                }

                flash.message = message(code: 'surveyInfo.delete.successfully')

                redirect action: 'currentSurveysConsortia'
            }
            catch (DataIntegrityViolationException e) {
                flash.error = message(code: 'surveyInfo.delete.fail')

                redirect(uri: request.getHeader('referer'))
            }
        }


    }


    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
     Map<String,Object> editSurveyCostItem() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        result.putAll(financeService.setEditVars(result.institution))
        if (!result.editable) {
            response.sendError(401); return
        }
        result.costItem = CostItem.findById(params.costItem)


        Map<Long,Object> orgConfigurations = [:]
        result.costItemElements.each { oc ->
            orgConfigurations.put(oc.costItemElement.id,oc.elementSign.id)
        }

        result.orgConfigurations = orgConfigurations as JSON
        //result.selectedCostItemElement = params.selectedCostItemElement ?: RefdataValue.getByValueAndCategory('price: consortial price', 'CostItemElement').id.toString()

        result.participant = Org.get(params.participant)
        result.surveyOrg = SurveyOrg.findBySurveyConfigAndOrg(result.surveyConfig, result.participant)


        result.mode = result.costItem ? "edit" : ""
        result.taxKey = result.costItem ? result.costItem.taxKey : null
        render(template: "/survey/costItemModal", model: result)
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
     Map<String,Object> addForAllSurveyCostItem() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.putAll(financeService.setEditVars(result.institution))

        Map<Long,Object> orgConfigurations = [:]
        result.costItemElements.each { oc ->
            orgConfigurations.put(oc.costItemElement.id,oc.elementSign.id)
        }

        result.orgConfigurations = orgConfigurations as JSON
        //result.selectedCostItemElement = params.selectedCostItemElement ?: RefdataValue.getByValueAndCategory('price: consortial price', 'CostItemElement').id.toString()

        result.setting = 'bulkForAll'

        result.surveyOrgList = []

        if (params.get('orgsIDs')) {
            List idList = (params.get('orgsIDs')?.split(',').collect { Long.valueOf(it.trim()) }).toList()
            List<Org> orgList = Org.findAllByIdInList(idList)
            result.surveyOrgList = orgList.isEmpty() ? [] : SurveyOrg.findAllByOrgInListAndSurveyConfig(orgList, result.surveyConfig)
        }

        render(template: "/survey/costItemModal", model: result)
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> setInEvaluation() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.surveyInfo.status = RDStore.SURVEY_IN_EVALUATION

        if (result.surveyInfo.save()) {
            //flash.message = g.message(code: 'survey.change.successfull')
        } else {
            flash.error = g.message(code: 'survey.change.fail')
        }

        redirect action: 'renewalWithSurvey', params:[surveyConfigID: result.surveyConfig.id, id: result.surveyInfo.id]

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> setCompleted() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.surveyInfo.status = RDStore.SURVEY_COMPLETED


        if (result.surveyInfo.save()) {
            //flash.message = g.message(code: 'survey.change.successfull')
        } else {
            flash.error = g.message(code: 'survey.change.fail')
        }

        redirect(url: request.getHeader('referer'))

    }


    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> setCompleteSurvey() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.surveyInfo.status = RDStore.SURVEY_SURVEY_COMPLETED
        if (result.surveyInfo.save()) {
            //flash.message = g.message(code: 'survey.change.successfull')
        } else {
            flash.error = g.message(code: 'survey.change.fail')
        }

        redirect(url: request.getHeader('referer'))

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> setSurveyConfigComment() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.surveyConfig.comment = params.comment

        if (!result.surveyConfig.save()) {
            flash.error = g.message(code: 'default.save.error.general.message')
        }

        redirect(url: request.getHeader('referer'))

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> renewalWithSurvey() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }
        result.superOrgType = []
        if(accessService.checkPerm('ORG_CONSORTIUM')) {
            result.superOrgType << message(code:'consortium.superOrgType')
        }

        result.parentSubscription = result.surveyConfig.subscription
        result.parentSubChilds = subscriptionService.getValidSubChilds(result.parentSubscription)
        result.parentSuccessorSubscription = result.surveyConfig.subscription._getCalculatedSuccessor()
        result.parentSuccessorSubChilds = result.parentSuccessorSubscription ? subscriptionService.getValidSubChilds(result.parentSuccessorSubscription) : null


        result.participationProperty = RDStore.SURVEY_PROPERTY_PARTICIPATION
        if(result.parentSuccessorSubscription) {
            String query = "select li.sourceLicense.instanceOf from Links li where li.destinationSubscription = :subscription and li.linkType = :linkType"
            result.memberLicenses = License.executeQuery(query, [subscription: result.parentSuccessorSubscription, linkType: RDStore.LINKTYPE_LICENSE])
        }

        result.properties = []
        result.properties.addAll(SurveyConfigProperties.findAllBySurveyPropertyNotEqualAndSurveyConfig(result.participationProperty, result.surveyConfig)?.surveyProperty.sort {
            it.getI10n('name')
        })


        result.multiYearTermThreeSurvey = null
        result.multiYearTermTwoSurvey = null

        if (RDStore.SURVEY_PROPERTY_MULTI_YEAR_3.id in result.properties.id) {
            result.multiYearTermThreeSurvey = RDStore.SURVEY_PROPERTY_MULTI_YEAR_3
            result.properties.remove(result.multiYearTermThreeSurvey)
        }
        if (RDStore.SURVEY_PROPERTY_MULTI_YEAR_2.id in result.properties.id) {
            result.multiYearTermTwoSurvey = RDStore.SURVEY_PROPERTY_MULTI_YEAR_2
            result.properties.remove(result.multiYearTermTwoSurvey)

        }

        List currentParticipantIDs = []
        result.orgsWithMultiYearTermSub = []
        //result.orgsLateCommers = []
        List orgsWithMultiYearTermOrgsID = []
        List orgsLateCommersOrgsID = []
        result.parentSubChilds.each { sub ->
            if (sub.isCurrentMultiYearSubscriptionNew())
            {
                result.orgsWithMultiYearTermSub << sub
                sub.getAllSubscribers().each { org ->
                    orgsWithMultiYearTermOrgsID << org.id
                }
            }
            else
            {
                sub.getAllSubscribers().each { org ->
                    currentParticipantIDs << org.id
                }
            }
        }


        result.orgsWithParticipationInParentSuccessor = []
        result.parentSuccessorSubChilds.each { sub ->
            sub.getAllSubscribers().each { org ->
                if(!(org.id in orgsWithMultiYearTermOrgsID) || !(org.id in currentParticipantIDs)) {
                    result.orgsWithParticipationInParentSuccessor  << sub
                }
            }
        }

        result.orgsWithTermination = []

            //Orgs with termination there sub
            SurveyResult.executeQuery("from SurveyResult where owner.id = :owner and surveyConfig.id = :surConfig and type.id = :surProperty and refValue = :refValue  order by participant.sortname",
                    [
                     owner      : result.institution.id,
                     surProperty: result.participationProperty.id,
                     surConfig  : result.surveyConfig.id,
                     refValue   : RDStore.YN_NO]).each {
                Map newSurveyResult = [:]
                newSurveyResult.participant = it.participant
                newSurveyResult.resultOfParticipation = it
                newSurveyResult.surveyConfig = result.surveyConfig
                newSurveyResult.sub = Subscription.executeQuery("Select s from Subscription s left join s.orgRelations orgR where s.instanceOf = :parentSub and orgR.org = :participant",
                        [parentSub  : result.parentSubscription,
                         participant: it.participant
                        ])[0]
                newSurveyResult.properties = SurveyResult.findAllByParticipantAndOwnerAndSurveyConfigAndTypeInList(it.participant, result.institution, result.surveyConfig, result.properties).sort {
                    it.type.getI10n('name')
                }

                result.orgsWithTermination << newSurveyResult

            }


        // Orgs that renew or new to Sub
        result.orgsContinuetoSubscription = []
        result.newOrgsContinuetoSubscription = []

            SurveyResult.executeQuery("from SurveyResult where owner.id = :owner and surveyConfig.id = :surConfig and type.id = :surProperty and refValue = :refValue order by participant.sortname",
                    [
                     owner      : result.institution.id,
                     surProperty: result.participationProperty.id,
                     surConfig  : result.surveyConfig.id,
                     refValue   : RDStore.YN_YES]).each {
                Map newSurveyResult = [:]
                newSurveyResult.participant = it.participant
                newSurveyResult.resultOfParticipation = it
                newSurveyResult.surveyConfig = result.surveyConfig
                newSurveyResult.properties = SurveyResult.findAllByParticipantAndOwnerAndSurveyConfigAndTypeInList(it.participant, result.institution, result.surveyConfig, result.properties).sort {
                    it.type.getI10n('name')
                }

                if (it.participant.id in currentParticipantIDs) {

                    newSurveyResult.sub = Subscription.executeQuery("Select s from Subscription s left join s.orgRelations orgR where s.instanceOf = :parentSub and orgR.org = :participant",
                            [parentSub  : result.parentSubscription,
                             participant: it.participant
                            ])[0]

                    //newSurveyResult.sub = result.parentSubscription.getDerivedSubscriptionBySubscribers(it.participant)

                    if (result.multiYearTermTwoSurvey) {

                        newSurveyResult.newSubPeriodTwoStartDate = null
                        newSurveyResult.newSubPeriodTwoEndDate = null

                        SurveyResult participantPropertyTwo = SurveyResult.findByParticipantAndOwnerAndSurveyConfigAndType(it.participant, result.institution, result.surveyConfig, result.multiYearTermTwoSurvey)

                        if (participantPropertyTwo && participantPropertyTwo.refValue?.id == RDStore.YN_YES.id) {
                            use(TimeCategory) {
                                newSurveyResult.newSubPeriodTwoStartDate = newSurveyResult.sub.startDate ? (newSurveyResult.sub.endDate + 1.day) : null
                                newSurveyResult.newSubPeriodTwoEndDate = newSurveyResult.sub.endDate ? (newSurveyResult.sub.endDate + 2.year) : null
                                newSurveyResult.participantPropertyTwoComment = participantPropertyTwo.comment
                            }
                        }

                    }
                    if (result.multiYearTermThreeSurvey) {
                        newSurveyResult.newSubPeriodThreeStartDate = null
                        newSurveyResult.newSubPeriodThreeEndDate = null

                        SurveyResult participantPropertyThree = SurveyResult.findByParticipantAndOwnerAndSurveyConfigAndType(it.participant, result.institution, result.surveyConfig, result.multiYearTermThreeSurvey)
                        if (participantPropertyThree && participantPropertyThree.refValue?.id == RDStore.YN_YES.id) {
                            use(TimeCategory) {
                                newSurveyResult.newSubPeriodThreeStartDate = newSurveyResult.sub.startDate ? (newSurveyResult.sub.endDate + 1.day) : null
                                newSurveyResult.newSubPeriodThreeEndDate = newSurveyResult.sub.endDate ? (newSurveyResult.sub.endDate + 3.year) : null
                                newSurveyResult.participantPropertyThreeComment = participantPropertyThree.comment
                            }
                        }
                    }

                    result.orgsContinuetoSubscription << newSurveyResult
                }
                if (!(it.participant.id in currentParticipantIDs) && !(it.participant.id in orgsLateCommersOrgsID) && !(it.participant.id in orgsWithMultiYearTermOrgsID)) {


                    if (result.multiYearTermTwoSurvey) {

                        newSurveyResult.newSubPeriodTwoStartDate = null
                        newSurveyResult.newSubPeriodTwoEndDate = null

                        SurveyResult participantPropertyTwo = SurveyResult.findByParticipantAndOwnerAndSurveyConfigAndType(it.participant, result.institution, result.surveyConfig, result.multiYearTermTwoSurvey)

                        if (participantPropertyTwo && participantPropertyTwo.refValue?.id == RDStore.YN_YES.id) {
                            use(TimeCategory) {
                                newSurveyResult.newSubPeriodTwoStartDate = result.parentSubscription.startDate ? (result.parentSubscription.endDate + 1.day) : null
                                newSurveyResult.newSubPeriodTwoEndDate = result.parentSubscription.endDate ? (result.parentSubscription.endDate + 2.year) : null
                                newSurveyResult.participantPropertyTwoComment = participantPropertyTwo.comment
                            }
                        }

                    }
                    if (result.multiYearTermThreeSurvey) {
                        newSurveyResult.newSubPeriodThreeStartDate = null
                        newSurveyResult.newSubPeriodThreeEndDate = null

                        SurveyResult participantPropertyThree = SurveyResult.findByParticipantAndOwnerAndSurveyConfigAndType(it.participant, result.institution, result.surveyConfig, result.multiYearTermThreeSurvey)
                        if (participantPropertyThree && participantPropertyThree.refValue?.id == RDStore.YN_YES.id) {
                            use(TimeCategory) {
                                newSurveyResult.newSubPeriodThreeStartDate = result.parentSubscription.startDate ? (result.parentSubscription.endDate + 1.day) : null
                                newSurveyResult.newSubPeriodThreeEndDate = result.parentSubscription.endDate ? (result.parentSubscription.endDate + 3.year) : null
                                newSurveyResult.participantPropertyThreeComment = participantPropertyThree.comment
                            }
                        }
                    }

                    result.newOrgsContinuetoSubscription << newSurveyResult
                }


            }


        //Orgs without really result
        result.orgsWithoutResult = []

            SurveyResult.executeQuery("from SurveyResult where owner.id = :owner and surveyConfig.id = :surConfig and type.id = :surProperty and refValue is null order by participant.sortname",
                    [
                     owner      : result.institution.id,
                     surProperty: result.participationProperty.id,
                     surConfig  : result.surveyConfig.id]).each {
                Map newSurveyResult = [:]
                newSurveyResult.participant = it.participant
                newSurveyResult.resultOfParticipation = it
                newSurveyResult.surveyConfig = result.surveyConfig
                newSurveyResult.properties = SurveyResult.findAllByParticipantAndOwnerAndSurveyConfigAndTypeInList(it.participant, result.institution, result.surveyConfig, result.properties).sort {
                    it.type.getI10n('name')
                }

                if (it.participant.id in currentParticipantIDs) {
                    newSurveyResult.sub = Subscription.executeQuery("Select s from Subscription s left join s.orgRelations orgR where s.instanceOf = :parentSub and orgR.org = :participant",
                            [parentSub  : result.parentSubscription,
                             participant: it.participant
                            ])[0]
                    //newSurveyResult.sub = result.parentSubscription.getDerivedSubscriptionBySubscribers(it.participant)
                } else {
                    newSurveyResult.sub = null
                }
                result.orgsWithoutResult << newSurveyResult
            }


        //MultiYearTerm Subs
        Integer sumParticipantWithSub = ((result.orgsContinuetoSubscription.groupBy {
            it.participant.id
        }.size()?:0) + (result.orgsWithTermination.groupBy { it.participant.id }.size()?:0) + (result.orgsWithMultiYearTermSub.size()?:0))

        if (sumParticipantWithSub < result.parentSubChilds.size()?:0) {
            /*def property = PropertyDefinition.getByNameAndDescr("Perennial term checked", PropertyDefinition.SUB_PROP)

            def removeSurveyResultOfOrg = []
            result.orgsWithoutResult.each { surveyResult ->
                if (surveyResult.participant.id in currentParticipantIDs && surveyResult.sub) {

                    if (property.type == RefdataValue.CLASS) {
                        if (surveyResult.sub.propertySet.find {
                            it.type.id == property.id
                        }?.refValue == RefdataValue.getByValueAndCategory('Yes', property.refdataCategory)) {

                            result.orgsWithMultiYearTermSub << surveyResult.sub
                            removeSurveyResultOfOrg << surveyResult
                        }
                    }
                }
            }
            removeSurveyResultOfOrg.each{ it
                result.orgsWithoutResult?.remove(it)
            }*/

            result.orgsWithMultiYearTermSub = result.orgsWithMultiYearTermSub.sort{it.getAllSubscribers().sortname}

        }


        String message = g.message(code: 'renewalexport.renewals')
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        String datetoday = sdf.format(new Date(System.currentTimeMillis()))
        String filename = message + "_" + result.surveyConfig.getSurveyName() +"_${datetoday}"
        if (params.exportXLSX) {
            try {
                SXSSFWorkbook wb = (SXSSFWorkbook) exportRenewalResult(result)
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
                log.error("Problem", e);
                response.sendError(500)
            }
        }

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> copySurvey() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0

        if(result.surveyInfo.type.id == RDStore.SURVEY_TYPE_INTEREST.id){
            result.workFlow = '2'
        }else{
            if(params.targetSubs){
                result.workFlow = '2'
            }else{
                result.workFlow = '1'
            }
        }

        if(result.workFlow == '1') {
            Date date_restriction = null;
            SimpleDateFormat sdf = DateUtil.getSDF_NoTime()

            if (params.validOn == null || params.validOn.trim() == '') {
                result.validOn = ""
            } else {
                result.validOn = params.validOn
                date_restriction = sdf.parse(params.validOn)
            }

            result.editable = accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")

            if (!result.editable) {
                flash.error = g.message(code: "default.notAutorized.message")
                redirect(url: request.getHeader('referer'))
            }

            if (!params.status) {
                if (params.isSiteReloaded != "yes") {
                    params.status = RDStore.SUBSCRIPTION_CURRENT.id
                    result.defaultSet = true
                } else {
                    params.status = 'FETCH_ALL'
                }
            }

            List orgIds = orgTypeService.getCurrentOrgIdsOfProvidersAndAgencies(contextService.org)

            result.providers = orgIds.isEmpty() ? [] : Org.findAllByIdInList(orgIds, [sort: 'name'])

            List tmpQ = subscriptionsQueryService.myInstitutionCurrentSubscriptionsBaseQuery(params, contextService.org)
            result.filterSet = tmpQ[2]
            List subscriptions = Subscription.executeQuery("select s ${tmpQ[0]}", tmpQ[1])
            //,[max: result.max, offset: result.offset]

            result.propList = PropertyDefinition.findAllPublicAndPrivateProp([PropertyDefinition.SUB_PROP], contextService.org)

            if (params.sort && params.sort.indexOf("§") >= 0) {
                switch (params.sort) {
                    case "orgRole§provider":
                        subscriptions.sort { x, y ->
                            String a = x.getProviders().size() > 0 ? x.getProviders().first().name : ''
                            String b = y.getProviders().size() > 0 ? y.getProviders().first().name : ''
                            a.compareToIgnoreCase b
                        }
                        if (params.order.equals("desc"))
                            subscriptions.reverse(true)
                        break
                }
            }
            result.num_sub_rows = subscriptions.size()
            result.subscriptions = subscriptions.drop((int) result.offset).take((int) result.max)
        }

        if(result.surveyConfig.subscription) {
            String sourceLicensesQuery = "select li.sourceLicense from Links li where li.destinationSubscription = :sub and li.linkType = :linkType order by li.sourceLicense.sortableReference asc"
            result.sourceLicenses = License.executeQuery(sourceLicensesQuery, [sub: result.surveyConfig.subscription, linkType: RDStore.LINKTYPE_LICENSE])
        }
        
        result.targetSubs = params.targetSubs ? Subscription.findAllByIdInList(params.list('targetSubs').collect { it -> Long.parseLong(it) }): null

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> addSubMembersToSurvey() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        addSubMembers(result.surveyConfig)

        redirect(action: 'surveyParticipants', params: [id: result.surveyInfo.id, surveyConfigID: result.surveyConfig.id, tab: 'selectedSubParticipants'])

    }
    
    
    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> processCopySurvey() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        SurveyInfo baseSurveyInfo = result.surveyInfo
        SurveyConfig baseSurveyConfig = result.surveyConfig

        if (baseSurveyInfo && baseSurveyConfig) {

            result.targetSubs = params.targetSubs ? Subscription.findAllByIdInList(params.list('targetSubs').collect { it -> Long.parseLong(it) }): null

            List newSurveyIds = []

            if(result.targetSubs){
                result.targetSubs.each { sub ->
                    SurveyInfo newSurveyInfo = new SurveyInfo(
                            name: sub.name,
                            status: RDStore.SURVEY_IN_PROCESSING,
                            type: baseSurveyInfo.type,
                            startDate: params.copySurvey.copyDates ? baseSurveyInfo.startDate : null,
                            endDate: params.copySurvey.copyDates ? baseSurveyInfo.endDate : null,
                            comment: params.copySurvey.copyComment ? baseSurveyInfo.comment : null,
                            isMandatory: params.copySurvey.copyMandatory ? baseSurveyInfo.isMandatory : false,
                            owner: contextService.getOrg()
                    ).save(flush:true)

                    SurveyConfig newSurveyConfig = new SurveyConfig(
                            type: baseSurveyConfig.type,
                            subscription: sub,
                            surveyInfo: newSurveyInfo,
                            comment: params.copySurvey.copySurveyConfigComment ? baseSurveyConfig.comment : null,
                            url: params.copySurvey.copySurveyConfigUrl ? baseSurveyConfig.url : null,
                            urlComment: params.copySurvey.copySurveyConfigUrl ? baseSurveyConfig.urlComment : null,
                            url2: params.copySurvey.copySurveyConfigUrl2 ? baseSurveyConfig.url2 : null,
                            urlComment2: params.copySurvey.copySurveyConfigUrl2 ? baseSurveyConfig.urlComment2 : null,
                            url3: params.copySurvey.copySurveyConfigUrl3 ? baseSurveyConfig.url3 : null,
                            urlComment3: params.copySurvey.copySurveyConfigUrl3 ? baseSurveyConfig.urlComment3 : null,
                            configOrder: newSurveyInfo.surveyConfigs ? newSurveyInfo.surveyConfigs.size() + 1 : 1
                    ).save(flush:true)

                    copySurveyConfigCharacteristic(baseSurveyConfig, newSurveyConfig, params)

                    newSurveyIds << newSurveyInfo.id

                }

                redirect controller: 'survey', action: 'currentSurveysConsortia', params: [ids: newSurveyIds]
            }else{
                SurveyInfo newSurveyInfo = new SurveyInfo(
                        name: params.name,
                        status: RDStore.SURVEY_IN_PROCESSING,
                        type: baseSurveyInfo.type,
                        startDate: params.copySurvey.copyDates ? baseSurveyInfo.startDate : null,
                        endDate: params.copySurvey.copyDates ? baseSurveyInfo.endDate : null,
                        comment: params.copySurvey.copyComment ? baseSurveyInfo.comment : null,
                        isMandatory: params.copySurvey.copyMandatory ? baseSurveyInfo.isMandatory : false,
                        owner: contextService.getOrg()
                ).save(flush:true)

                SurveyConfig newSurveyConfig = new SurveyConfig(
                        type: baseSurveyConfig.type,
                        surveyInfo: newSurveyInfo,
                        comment: params.copySurvey.copySurveyConfigComment ? baseSurveyConfig.comment : null,
                        url: params.copySurvey.copySurveyConfigUrl ? baseSurveyConfig.url : null,
                        urlComment: params.copySurvey.copySurveyConfigUrl ? baseSurveyConfig.urlComment : null,
                        url2: params.copySurvey.copySurveyConfigUrl2 ? baseSurveyConfig.url2 : null,
                        urlComment2: params.copySurvey.copySurveyConfigUrl ? baseSurveyConfig.urlComment2 : null,
                        url3: params.copySurvey.copySurveyConfigUrl3 ? baseSurveyConfig.url3 : null,
                        urlComment3: params.copySurvey.copySurveyConfigUrl ? baseSurveyConfig.urlComment3 : null,
                        configOrder: newSurveyInfo.surveyConfigs ? newSurveyInfo.surveyConfigs.size() + 1 : 1
                ).save(flush:true)

                copySurveyConfigCharacteristic(baseSurveyConfig, newSurveyConfig, params)

                redirect controller: 'survey', action: 'show', params: [id: newSurveyInfo.id, surveyConfigID: newSurveyConfig.id]
            }
        }

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> renewSubscriptionConsortiaWithSurvey() {

        Map<String,Object> result = setResultGenericsAndCheckAccess()
        result.institution = contextService.org
        if (!(result || accessService.checkPerm("ORG_CONSORTIUM"))) {
            response.sendError(401); return
        }

        Subscription subscription = Subscription.get(params.parentSub ?: null)

        SimpleDateFormat sdf = new SimpleDateFormat('dd.MM.yyyy')

        result.errors = []
        Date newStartDate
        Date newEndDate
        use(TimeCategory) {
            newStartDate = subscription.endDate ? (subscription.endDate + 1.day) : null
            newEndDate = subscription.endDate ? (subscription.endDate + 1.year) : null
        }
        params.surveyConfig = params.surveyConfig ?: null
        result.isRenewSub = true
        result.permissionInfo = [sub_startDate: newStartDate ? sdf.format(newStartDate) : null,
                                 sub_endDate  : newEndDate ? sdf.format(newEndDate) : null,
                                 sub_name     : subscription.name,
                                 sub_id       : subscription.id,
                                 sub_status   : RDStore.SUBSCRIPTION_INTENDED.id.toString(),
                                 sub_type     : subscription.type?.id.toString(),
                                 sub_form     : subscription.form?.id.toString(),
                                 sub_resource : subscription.resource?.id.toString(),
                                 sub_kind     : subscription.kind?.id.toString(),
                                 sub_isPublicForApi : subscription.isPublicForApi ? RDStore.YN_YES.id.toString() : RDStore.YN_NO.id.toString(),
                                 sub_hasPerpetualAccess : subscription.hasPerpetualAccess ? RDStore.YN_YES.id.toString() : RDStore.YN_NO.id.toString()

        ]

        result.subscription = subscription
        result
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> processRenewalWithSurvey() {

        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!(result || accessService.checkPerm("ORG_CONSORTIUM"))) {
            response.sendError(401); return
        }

        Subscription baseSub = Subscription.get(params.parentSub ?: null)

        ArrayList<Links> previousSubscriptions = Links.findAllByDestinationSubscriptionAndLinkType(baseSub, RDStore.LINKTYPE_FOLLOWS)
        if (previousSubscriptions.size() > 0) {
            flash.error = message(code: 'subscription.renewSubExist')
        } else {
            def sub_startDate = params.subscription.start_date ? parseDate(params.subscription.start_date, possible_date_formats) : null
            def sub_endDate = params.subscription.end_date ? parseDate(params.subscription.end_date, possible_date_formats) : null
            def sub_status = params.subStatus
            def sub_type = params.subType
            def sub_kind = params.subKind
            def sub_form = params.subForm
            def sub_resource = params.subResource
            def sub_hasPerpetualAccess = params.subHasPerpetualAccess == '1'
            def sub_isPublicForApi = params.subIsPublicForApi == '1'
            def old_subOID = params.subscription.old_subid
            def new_subname = params.subscription.name
            def manualCancellationDate = null

            use(TimeCategory) {
                manualCancellationDate =  baseSub.manualCancellationDate ? (baseSub.manualCancellationDate + 1.year) : null
            }
            Subscription newSub = new Subscription(
                    name: new_subname,
                    startDate: sub_startDate,
                    endDate: sub_endDate,
                    manualCancellationDate: manualCancellationDate,
                    identifier: java.util.UUID.randomUUID().toString(),
                    isSlaved: baseSub.isSlaved,
                    type: sub_type,
                    kind: sub_kind,
                    status: sub_status,
                    resource: sub_resource,
                    form: sub_form,
                    hasPerpetualAccess: sub_hasPerpetualAccess,
                    isPublicForApi: sub_isPublicForApi
            )

            if (!newSub.save()) {
                log.error("Problem saving subscription ${newSub.errors}");
                return newSub
            } else {

                log.debug("Save ok");
                if (params.list('auditList')) {
                    //copy audit
                    params.list('auditList').each { auditField ->
                        //All ReferenceFields were copied!
                        //'name', 'startDate', 'endDate', 'manualCancellationDate', 'status', 'type', 'form', 'resource'
                        //println(auditField)
                        AuditConfig.addConfig(newSub, auditField)
                    }
                }
                //Copy References
                //OrgRole
                baseSub.orgRelations.each { OrgRole or ->

                    if ((or.org.id == result.institution.id) || (or.roleType in [RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS])) {
                        OrgRole newOrgRole = new OrgRole()
                        InvokerHelper.setProperties(newOrgRole, or.properties)
                        newOrgRole.sub = newSub
                        newOrgRole.save(flush:true)
                    }
                }
                //link to previous subscription
                Links prevLink = Links.construct([source: newSub, destination: baseSub, linkType: RDStore.LINKTYPE_FOLLOWS, owner: contextService.org])
                if (!prevLink) {
                    log.error("Problem linking to previous subscription: ${prevLink.errors}")
                }
                result.newSub = newSub

                if (params.targetObjectId == "null") params.remove("targetObjectId")
                result.isRenewSub = true

                    redirect controller: 'subscription',
                            action: 'copyElementsIntoSubscription',
                            params: [sourceObjectId: genericOIDService.getOID(Subscription.get(old_subOID)), targetObjectId: genericOIDService.getOID(newSub), isRenewSub: true, fromSurvey: true]

            }
        }
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
     Map<String,Object> exportSurCostItems() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }
        //result.putAll(financeService.setEditVars(result.institution))

        /*   def surveyInfo = SurveyInfo.findByIdAndOwner(params.id, result.institution) ?: null

           def surveyConfig = SurveyConfig.findByIdAndSurveyInfo(params.surveyConfigID, surveyInfo)*/

        if (params.exportXLSX) {
            SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
            String datetoday = sdf.format(new Date(System.currentTimeMillis()))
            String filename = "${datetoday}_" + g.message(code: "survey.exportSurveyCostItems")
            //if(wb instanceof XSSFWorkbook) file += "x";
            response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            SXSSFWorkbook wb = (SXSSFWorkbook) surveyService.exportSurveyCostItems([result.surveyConfig], result.institution)
            wb.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            wb.dispose()

            return
        } else {
            redirect(uri: request.getHeader('referer'))
        }

    }


    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
     Map<String,Object> copyEmailaddresses() {
        Map<String, Object> result = [:]
        result.modalID = params.targetId
        result.orgList = []

        if (params.get('orgListIDs')) {
            List idList = (params.get('orgListIDs').split(',').collect { Long.valueOf(it.trim()) }).toList()
            result.orgList = idList.isEmpty() ? [] : Org.findAllByIdInList(idList)
        }

        render(template: "/templates/copyEmailaddresses", model: result)
    }


    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
     Map<String,Object> newSurveyCostItem() {
        SimpleDateFormat dateFormat = DateUtil.getSDF_NoTime()

        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        def newCostItem = null
        result.putAll(financeService.setEditVars(result.institution))

        try {
            log.debug("SurveyController::newCostItem() ${params}");


            User user = User.get(springSecurityService.principal.id)
            result.error = [] as List

            if (!accessService.checkMinUserOrgRole(user, result.institution, "INST_EDITOR")) {
                result.error = message(code: 'financials.permission.unauthorised', args: [result.institution ? result.institution.name : 'N/A'])
                response.sendError(403)
            }


            Closure newDate = { param, format ->
                Date date
                try {
                    date = dateFormat.parse(param)
                } catch (Exception e) {
                    log.debug("Unable to parse date : ${param} in format ${format}")
                }
                date
            }

            Date startDate = newDate(params.newStartDate, dateFormat.toPattern())
            Date endDate = newDate(params.newEndDate, dateFormat.toPattern())
            RefdataValue billing_currency = null
            if (params.long('newCostCurrency')) //GBP,etc
            {
                billing_currency = RefdataValue.get(params.newCostCurrency)
            }

            //def tempCurrencyVal       = params.newCostCurrencyRate?      params.double('newCostCurrencyRate',1.00) : 1.00//def cost_local_currency   = params.newCostInLocalCurrency?   params.double('newCostInLocalCurrency', cost_billing_currency * tempCurrencyVal) : 0.00
            RefdataValue cost_item_status = params.newCostItemStatus ? (RefdataValue.get(params.long('newCostItemStatus'))) : null;
            //estimate, commitment, etc
            RefdataValue cost_item_element = params.newCostItemElement ? (RefdataValue.get(params.long('newCostItemElement'))) : null
            //admin fee, platform, etc
            //moved to TAX_TYPES
            //RefdataValue cost_tax_type         = params.newCostTaxType ?          (RefdataValue.get(params.long('newCostTaxType'))) : null           //on invoice, self declared, etc

            RefdataValue cost_item_category = params.newCostItemCategory ? (RefdataValue.get(params.long('newCostItemCategory'))) : null
            //price, bank charge, etc

            NumberFormat format = NumberFormat.getInstance(LocaleContextHolder.getLocale())
            def cost_billing_currency = params.newCostInBillingCurrency ? format.parse(params.newCostInBillingCurrency).doubleValue() : 0.00
            //def cost_currency_rate = params.newCostCurrencyRate ? params.double('newCostCurrencyRate', 1.00) : 1.00
            //def cost_local_currency = params.newCostInLocalCurrency ? format.parse(params.newCostInLocalCurrency).doubleValue() : 0.00

            def cost_billing_currency_after_tax = params.newCostInBillingCurrencyAfterTax ? format.parse(params.newCostInBillingCurrencyAfterTax).doubleValue() : cost_billing_currency
            //def cost_local_currency_after_tax = params.newCostInLocalCurrencyAfterTax ? format.parse(params.newCostInLocalCurrencyAfterTax).doubleValue() : cost_local_currency
            //moved to TAX_TYPES
            //def new_tax_rate                      = params.newTaxRate ? params.int( 'newTaxRate' ) : 0
            def tax_key = null
            if (!params.newTaxRate.contains("null")) {
                String[] newTaxRate = params.newTaxRate.split("§")
                RefdataValue taxType = (RefdataValue) genericOIDService.resolveOID(newTaxRate[0])
                int taxRate = Integer.parseInt(newTaxRate[1])
                switch (taxType.id) {
                    case RefdataValue.getByValueAndCategory("taxable", RDConstants.TAX_TYPE).id:
                        switch (taxRate) {
                            case 7: tax_key = CostItem.TAX_TYPES.TAXABLE_7
                                break
                            case 19: tax_key = CostItem.TAX_TYPES.TAXABLE_19
                                break
                        }
                        break
                    case RefdataValue.getByValueAndCategory("taxable tax-exempt", RDConstants.TAX_TYPE).id:
                        tax_key = CostItem.TAX_TYPES.TAX_EXEMPT
                        break
                    case RefdataValue.getByValueAndCategory("not taxable", RDConstants.TAX_TYPE).id:
                        tax_key = CostItem.TAX_TYPES.TAX_NOT_TAXABLE
                        break
                    case RefdataValue.getByValueAndCategory("not applicable", RDConstants.TAX_TYPE).id:
                        tax_key = CostItem.TAX_TYPES.TAX_NOT_APPLICABLE
                        break
                    case RefdataValue.getByValueAndCategory("reverse charge", RDConstants.TAX_TYPE).id:
                        tax_key = CostItem.TAX_TYPES.TAX_REVERSE_CHARGE
                        break
                }
            }
            RefdataValue cost_item_element_configuration = params.ciec ? RefdataValue.get(Long.parseLong(params.ciec)) : null

            boolean cost_item_isVisibleForSubscriber = false
            // (params.newIsVisibleForSubscriber ? (RefdataValue.get(params.newIsVisibleForSubscriber).value == 'Yes') : false)

            List surveyOrgsDo = []

            if (params.surveyOrg) {
                try {
                    surveyOrgsDo << genericOIDService.resolveOID(params.surveyOrg)
                } catch (Exception e) {
                    log.error("Non-valid surveyOrg sent ${params.surveyOrg}", e)
                }
            }

            if (params.get('surveyOrgs')) {
                List surveyOrgs = (params.get('surveyOrgs').split(',').collect {
                    String.valueOf(it.replaceAll("\\s", ""))
                }).toList()
                surveyOrgs.each {
                    try {

                        def surveyOrg = genericOIDService.resolveOID(it)
                        if (!CostItem.findBySurveyOrgAndCostItemStatusNotEqual(surveyOrg,RDStore.COST_ITEM_DELETED)) {
                            surveyOrgsDo << surveyOrg
                        }
                    } catch (Exception e) {
                        log.error("Non-valid surveyOrg sent ${it}", e)
                    }
                }
            }

            /* if (params.surveyConfig) {
                 def surveyConfig = genericOIDService.resolveOID(params.surveyConfig)

                 surveyConfig.orgs.each {

                     if (!CostItem.findBySurveyOrg(it)) {
                         surveyOrgsDo << it
                     }
                 }
             }*/

            surveyOrgsDo.each { surveyOrg ->

                if (!surveyOrg.existsMultiYearTerm()) {

                    if (params.oldCostItem && genericOIDService.resolveOID(params.oldCostItem)) {
                        newCostItem = genericOIDService.resolveOID(params.oldCostItem)
                    } else {
                        newCostItem = new CostItem()
                    }

                    newCostItem.owner = result.institution
                    newCostItem.surveyOrg = newCostItem.surveyOrg ?: surveyOrg
                    newCostItem.isVisibleForSubscriber = cost_item_isVisibleForSubscriber
                    newCostItem.costItemCategory = cost_item_category
                    newCostItem.costItemElement = cost_item_element
                    newCostItem.costItemStatus = cost_item_status
                    newCostItem.billingCurrency = billing_currency //Not specified default to GDP
                    //newCostItem.taxCode = cost_tax_type -> to taxKey
                    newCostItem.costTitle = params.newCostTitle ?: null
                    newCostItem.costInBillingCurrency = cost_billing_currency as Double
                    //newCostItem.costInLocalCurrency = cost_local_currency as Double

                    newCostItem.finalCostRounding = params.newFinalCostRounding ? true : false
                    newCostItem.costInBillingCurrencyAfterTax = cost_billing_currency_after_tax as Double
                    //newCostItem.costInLocalCurrencyAfterTax = cost_local_currency_after_tax as Double
                    //newCostItem.currencyRate = cost_currency_rate as Double
                    //newCostItem.taxRate = new_tax_rate as Integer -> to taxKey
                    newCostItem.taxKey = tax_key
                    newCostItem.costItemElementConfiguration = cost_item_element_configuration

                    newCostItem.costDescription = params.newDescription ? params.newDescription.trim() : null

                    newCostItem.startDate = startDate ?: null
                    newCostItem.endDate = endDate ?: null

                    //newCostItem.includeInSubscription = null
                    //todo Discussion needed, nobody is quite sure of the functionality behind this...


                    if (!newCostItem.validate()) {
                        result.error = newCostItem.errors.allErrors.collect {
                            log.error("Field: ${it.properties.field}, user input: ${it.properties.rejectedValue}, Reason! ${it.properties.code}")
                            message(code: 'finance.addNew.error', args: [it.properties.field])
                        }
                    } else {
                        if (newCostItem.save()) {
                            /* def newBcObjs = []

                         params.list('newBudgetCodes').each { newbc ->
                             def bc = genericOIDService.resolveOID(newbc)
                             if (bc) {
                                 newBcObjs << bc
                                 if (! CostItemGroup.findByCostItemAndBudgetCode( newCostItem, bc )) {
                                     new CostItemGroup(costItem: newCostItem, budgetCode: bc).save()
                                 }
                             }
                         }

                         def toDelete = newCostItem.getBudgetcodes().minus(newBcObjs)
                         toDelete.each{ bc ->
                             def cig = CostItemGroup.findByCostItemAndBudgetCode( newCostItem, bc )
                             if (cig) {
                                 log.debug('deleting ' + cig)
                                 cig.delete(flush:true)
                             }
                         }*/

                        } else {
                            result.error = "Unable to save!"
                        }
                    }
                }
            } // subsToDo.each

        }
        catch (Exception e) {
            log.error("Problem in add cost item", e);
        }


        redirect(uri: request.getHeader('referer'))
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> compareMembersOfTwoSubs() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.parentSubscription = result.surveyConfig.subscription
        result.parentSubChilds = subscriptionService.getValidSubChilds(result.parentSubscription)
        if(result.surveyConfig.subSurveyUseForTransfer){
            result.parentSuccessorSubscription = result.surveyConfig.subscription?._getCalculatedSuccessor()
        }else{
            result.parentSuccessorSubscription = params.targetSubscriptionId ? Subscription.get(params.targetSubscriptionId) : null
            result.targetSubscription =  result.parentSuccessorSubscription
        }

        result.parentSuccessorSubChilds = result.parentSuccessorSubscription ? subscriptionService.getValidSubChilds(result.parentSuccessorSubscription) : null

        result.superOrgType = []
        if(accessService.checkPerm('ORG_CONSORTIUM')) {
            result.superOrgType << message(code:'consortium.superOrgType')
        }

        result.participantsList = []

        result.parentParticipantsList = []
        result.parentSuccessortParticipantsList = []

        result.parentSubChilds.each { sub ->
            Org org = sub.getSubscriber()
            result.participantsList << org
            result.parentParticipantsList << org

        }

        result.parentSuccessorSubChilds.each { sub ->
            Org org = sub.getSubscriber()
            if(!(org in result.participantsList)) {
                result.participantsList << org
            }
            result.parentSuccessortParticipantsList << org

        }

        result.participantsList = result.participantsList.sort{it.sortname}


        result.participationProperty = RDStore.SURVEY_PROPERTY_PARTICIPATION
        if(result.surveyConfig.subSurveyUseForTransfer && result.parentSuccessorSubscription) {
            String query = "select li.sourceLicense from Links li where li.destinationSubscription = :subscription and li.linkType = :linkType"
            result.memberLicenses = License.executeQuery(query, [subscription: result.parentSuccessorSubscription, linkType: RDStore.LINKTYPE_LICENSE])
        }

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> copySurveyCostItems() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.parentSubscription = result.surveyConfig.subscription
        if(result.surveyConfig.subSurveyUseForTransfer){
            result.parentSuccessorSubscription = result.surveyConfig.subscription?._getCalculatedSuccessor()
        }else{
            result.parentSuccessorSubscription = params.targetSubscriptionId ? Subscription.get(params.targetSubscriptionId) : null
            result.targetSubscription =  result.parentSuccessorSubscription
        }
        result.parentSuccessorSubChilds = result.parentSuccessorSubscription ? subscriptionService.getValidSubChilds(result.parentSuccessorSubscription) : null

        result.participantsList = []

        result.parentSuccessortParticipantsList = []

        result.parentSuccessorSubChilds.each { sub ->
            Map newMap = [:]
            Org org = sub.getSubscriber()
            newMap.id = org.id
            newMap.sortname = org.sortname
            newMap.name = org.name
            newMap.newSub = sub
            newMap.oldSub = sub._getCalculatedPrevious()

            newMap.surveyOrg = SurveyOrg.findBySurveyConfigAndOrg(result.surveyConfig, org)
            newMap.surveyCostItem =newMap.surveyOrg ? CostItem.findBySurveyOrgAndCostItemStatusNotEqual(newMap.surveyOrg,RDStore.COST_ITEM_DELETED) : null

            result.participantsList << newMap

        }

        result.participantsList = result.participantsList.sort{it.sortname}

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> proccessCopySurveyCostItems() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.parentSubscription = result.surveyConfig.subscription

        if(result.surveyConfig.subSurveyUseForTransfer){
            result.parentSuccessorSubscription = result.surveyConfig.subscription?._getCalculatedSuccessor()
        }else{
            result.parentSuccessorSubscription = params.targetSubscriptionId ? Subscription.get(params.targetSubscriptionId) : null
            result.targetSubscription =  result.parentSuccessorSubscription
        }

        Integer countNewCostItems = 0
        RefdataValue costElement = RefdataValue.getByValueAndCategory('price: consortial price', RDConstants.COST_ITEM_ELEMENT)
        params.list('selectedSurveyCostItem').each { costItemId ->

            CostItem costItem = CostItem.get(costItemId)
            Subscription participantSub = result.parentSuccessorSubscription?.getDerivedSubscriptionBySubscribers(costItem.surveyOrg.org)
            List participantSubCostItem = CostItem.findAllBySubAndOwnerAndCostItemElementAndCostItemStatusNotEqual(participantSub, result.institution, costElement, RDStore.COST_ITEM_DELETED)
            if(costItem && participantSub && !participantSubCostItem){

                Map properties = costItem.properties
                CostItem copyCostItem = new CostItem()
                InvokerHelper.setProperties(copyCostItem, properties)
                copyCostItem.globalUID = null
                copyCostItem.surveyOrg = null
                copyCostItem.isVisibleForSubscriber = params.isVisibleForSubscriber ? true : null
                copyCostItem.sub = participantSub
                if(costItem.billingCurrency == RDStore.CURRENCY_EUR){
                    copyCostItem.currencyRate = 1.0
                    copyCostItem.costInLocalCurrency = costItem.costInBillingCurrency
                }
                if(copyCostItem.save(flush:true)) {
                    countNewCostItems++
                }

            }

        }

        flash.message = message(code: 'copySurveyCostItems.copy.success', args: [countNewCostItems])
        redirect(action: 'copySurveyCostItems', id: params.id, params: [surveyConfigID: result.surveyConfig.id, targetSubscriptionId: result.targetSubscription.id])

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> copySurveyCostItemsToSub() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.parentSubscription = result.surveyConfig.subscription
        result.parentSubChilds = result.parentSubscription ? subscriptionService.getValidSubChilds(result.parentSubscription) : null

        result.participantsList = []

        result.parentSubChilds.each { sub ->
            Map newMap = [:]
            Org org = sub.getSubscriber()
            newMap.id = org.id
            newMap.sortname = org.sortname
            newMap.name = org.name
            newMap.newSub = sub

            newMap.surveyOrg = SurveyOrg.findBySurveyConfigAndOrg(result.surveyConfig, org)
            newMap.surveyCostItem =newMap.surveyOrg ? CostItem.findBySurveyOrgAndCostItemStatusNotEqual(newMap.surveyOrg,RDStore.COST_ITEM_DELETED) : null

            result.participantsList << newMap

        }

        result.participantsList = result.participantsList.sort{it.sortname}

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> proccessCopySurveyCostItemsToSub() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.parentSubscription = result.surveyConfig.subscription


        Integer countNewCostItems = 0
        RefdataValue costElement = RefdataValue.getByValueAndCategory('price: consortial price', RDConstants.COST_ITEM_ELEMENT)
        params.list('selectedSurveyCostItem').each { costItemId ->

            CostItem costItem = CostItem.get(costItemId)
            Subscription participantSub = result.parentSubscription?.getDerivedSubscriptionBySubscribers(costItem.surveyOrg.org)
            List participantSubCostItem = CostItem.findAllBySubAndOwnerAndCostItemElementAndCostItemStatusNotEqual(participantSub, result.institution, costElement, RDStore.COST_ITEM_DELETED)
            if(costItem && participantSub && !participantSubCostItem){

                Map properties = costItem.properties
                CostItem copyCostItem = new CostItem()
                InvokerHelper.setProperties(copyCostItem, properties)
                copyCostItem.globalUID = null
                copyCostItem.surveyOrg = null
                copyCostItem.isVisibleForSubscriber = params.isVisibleForSubscriber ? true : null
                copyCostItem.sub = participantSub
                if(costItem.billingCurrency == RDStore.CURRENCY_EUR){
                    copyCostItem.currencyRate = 1.0
                    copyCostItem.costInLocalCurrency = costItem.costInBillingCurrency
                }

                if(copyCostItem.save(flush:true)) {
                    countNewCostItems++
                }

            }

        }

        flash.message = message(code: 'copySurveyCostItems.copy.success', args: [countNewCostItems])
        redirect(action: 'copySurveyCostItemsToSub', id: params.id, params: [surveyConfigID: result.surveyConfig.id])

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> copyProperties() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        params.tab = params.tab ?: 'surveyProperties'

        result.parentSubscription = result.surveyConfig.subscription
        if(result.surveyConfig.subSurveyUseForTransfer){
            result.parentSuccessorSubscription = result.surveyConfig.subscription?._getCalculatedSuccessor()
        }else{
            result.parentSuccessorSubscription = params.targetSubscriptionId ? Subscription.get(params.targetSubscriptionId) : null
            result.targetSubscription =  result.parentSuccessorSubscription
        }
        result.parentSuccessorSubChilds = result.parentSuccessorSubscription ? subscriptionService.getValidSubChilds(result.parentSuccessorSubscription) : null

        result.selectedProperty
        result.properties
        if(params.tab == 'surveyProperties') {
            result.properties = SurveyConfigProperties.findAllBySurveyConfig(result.surveyConfig).surveyProperty.findAll{it.tenant == null}
            result.properties -= RDStore.SURVEY_PROPERTY_PARTICIPATION
            result.properties -= RDStore.SURVEY_PROPERTY_MULTI_YEAR_2
            result.properties -= RDStore.SURVEY_PROPERTY_MULTI_YEAR_3
        }

        if(params.tab == 'customProperties') {
            result.properties = result.parentSubscription.propertySet.findAll{it.type.tenant == null && (it.tenant?.id == result.contextOrg.id || (it.tenant?.id != result.contextOrg.id && it.isPublic))}.type
        }

        if(params.tab == 'privateProperties') {
            result.properties = result.parentSubscription.propertySet.findAll{it.type.tenant?.id == result.contextOrg.id}.type
        }

        if(result.properties) {
            result.selectedProperty = params.selectedProperty ?: result.properties[0].id

            result.participantsList = []
            result.parentSuccessorSubChilds.each { sub ->

                Map newMap = [:]
                Org org = sub.getSubscriber()
                newMap.id = org.id
                newMap.sortname = org.sortname
                newMap.name = org.name
                newMap.newSub = sub
                newMap.oldSub = sub._getCalculatedPrevious()


                if (params.tab == 'surveyProperties') {
                    PropertyDefinition surProp = PropertyDefinition.get(result.selectedProperty)
                    newMap.surveyProperty = SurveyResult.findBySurveyConfigAndTypeAndParticipant(result.surveyConfig, surProp, org)
                    PropertyDefinition propDef = surProp ? PropertyDefinition.getByNameAndDescr(surProp.name, PropertyDefinition.SUB_PROP) : null


                    newMap.newCustomProperty = (sub && propDef) ? sub.propertySet.find {
                        it.type.id == propDef.id && it.type.tenant == null && (it.tenant?.id == result.contextOrg.id || (it.tenant?.id != result.contextOrg.id && it.isPublic))
                    } : null
                    newMap.oldCustomProperty = (newMap.oldSub && propDef) ? newMap.oldSub.propertySet.find {
                        it.type.id == propDef.id && it.type.tenant == null && (it.tenant?.id == result.contextOrg.id || (it.tenant?.id != result.contextOrg.id && it.isPublic))
                    } : null
                }
                if(params.tab == 'customProperties') {
                    newMap.newCustomProperty = (sub) ? sub.propertySet.find {
                        it.type.id == (result.selectedProperty instanceof Long ? result.selectedProperty : Long.parseLong(result.selectedProperty)) && it.type.tenant == null && (it.tenant?.id == result.contextOrg.id || (it.tenant?.id != result.contextOrg.id && it.isPublic))
                    } : null
                    newMap.oldCustomProperty = (newMap.oldSub) ? newMap.oldSub.propertySet.find {
                        it.type.id == (result.selectedProperty instanceof Long ? result.selectedProperty : Long.parseLong(result.selectedProperty)) && it.type.tenant == null && (it.tenant?.id == result.contextOrg.id || (it.tenant?.id != result.contextOrg.id && it.isPublic))
                    } : null
                }

                if(params.tab == 'privateProperties') {
                    newMap.newPrivateProperty = (sub) ? sub.propertySet.find {
                        it.type.id == (result.selectedProperty instanceof Long ? result.selectedProperty : Long.parseLong(result.selectedProperty)) && it.type.tenant?.id == result.contextOrg.id
                    } : null
                    newMap.oldPrivateProperty = (newMap.oldSub) ? newMap.oldSub.propertySet.find {
                        it.type.id == (result.selectedProperty instanceof Long ? result.selectedProperty : Long.parseLong(result.selectedProperty)) && it.type.tenant?.id == result.contextOrg.id
                    } : null
                }


                result.participantsList << newMap
            }

            result.participantsList = result.participantsList.sort { it.sortname }
        }

        result

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> proccessCopyProperties() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        if(result.surveyConfig.subSurveyUseForTransfer){
            result.parentSuccessorSubscription = result.surveyConfig.subscription?._getCalculatedSuccessor()
        }else{
            result.parentSuccessorSubscription = params.targetSubscriptionId ? Subscription.get(params.targetSubscriptionId) : null
            result.targetSubscription =  result.parentSuccessorSubscription
        }

        result.parentSuccessorSubChilds = result.parentSuccessorSubscription ? subscriptionService.getValidSubChilds(result.parentSuccessorSubscription) : null

        if(params.list('selectedSub')) {
            result.selectedProperty
            PropertyDefinition propDef
            PropertyDefinition surveyProperty
            if (params.tab == 'surveyProperties') {
                result.selectedProperty = params.selectedProperty ?: null

                surveyProperty = params.copyProperty ? PropertyDefinition.get(Long.parseLong(params.copyProperty)) : null

                propDef = surveyProperty ? PropertyDefinition.getByNameAndDescr(surveyProperty.name, PropertyDefinition.SUB_PROP) : null
                if (!propDef && surveyProperty) {

                    Map<String, Object> map = [
                            token       : surveyProperty.name,
                            category    : 'Subscription Property',
                            type        : surveyProperty.type,
                            rdc         : (surveyProperty.isRefdataValueType()) ? surveyProperty.refdataCategory : null,
                            i10n        : [
                                    name_de: surveyProperty.getI10n('name', 'de'),
                                    name_en: surveyProperty.getI10n('name', 'en'),
                                    expl_de: surveyProperty.getI10n('expl', 'de'),
                                    expl_en: surveyProperty.getI10n('expl', 'en')
                            ]
                    ]
                    propDef = PropertyDefinition.construct(map)
                }

            } else {
                result.selectedProperty = params.selectedProperty ?: null
                propDef = params.selectedProperty ? PropertyDefinition.get(Long.parseLong(params.selectedProperty)) : null
            }

            Integer countSuccessfulCopy = 0

            if (propDef && params.list('selectedSub')) {
                params.list('selectedSub').each { subID ->
                    if (Long.parseLong(subID) in result.parentSuccessorSubChilds.id) {
                        Subscription sub = Subscription.get(Long.parseLong(subID))
                        Org org = sub.getSubscriber()
                        Subscription oldSub = sub._getCalculatedPrevious()

                        AbstractPropertyWithCalculatedLastUpdated copyProperty
                        if (params.tab == 'surveyProperties') {
                            copyProperty = SurveyResult.findBySurveyConfigAndTypeAndParticipant(result.surveyConfig, surveyProperty, org)
                        } else {
                            if (params.tab == 'privateProperties') {
                                copyProperty = oldSub ? oldSub.propertySet.find {
                                    it.type.id == propDef.id && it.type.tenant.id == result.contextOrg.id
                                } : []
                            } else {
                                copyProperty = oldSub ? oldSub.propertySet.find {
                                    it.type.id == propDef.id && (it.tenant?.id == result.contextOrg.id || (it.tenant?.id != result.contextOrg.id && it.isPublic))
                                } : []
                            }
                        }

                        if (copyProperty) {
                            if (propDef.tenant != null) {
                                //private Property
                                def existingProps = sub.propertySet.findAll {
                                    it.owner.id == sub.id && it.type.id == propDef.id && it.type.tenant.id == result.contextOrg.id
                                }
                                existingProps.removeAll { it.type.name != propDef.name } // dubious fix

                                if (existingProps.size() == 0 || propDef.multipleOccurrence) {
                                    def newProp = PropertyDefinition.createGenericProperty(PropertyDefinition.PRIVATE_PROPERTY, sub, propDef, result.contextOrg)
                                    if (newProp.hasErrors()) {
                                        log.error(newProp.errors.toString())
                                    } else {
                                        log.debug("New private property created: " + newProp.type.name)
                                        def newValue = copyProperty.getValue()
                                        if (copyProperty.type.isRefdataValueType()) {
                                            newValue = copyProperty.refValue ? copyProperty.refValue : null
                                        }
                                        def prop = setNewProperty(newProp, newValue)
                                        countSuccessfulCopy++
                                    }
                                }
                            } else {
                                //custom Property
                                def existingProp = sub.propertySet.find {
                                    it.type.id == propDef.id && it.owner.id == sub.id && (it.tenant?.id == result.contextOrg.id || (it.tenant?.id != result.contextOrg.id && it.isPublic))
                                }

                                if (existingProp == null || propDef.multipleOccurrence) {
                                    def newProp = PropertyDefinition.createGenericProperty(PropertyDefinition.CUSTOM_PROPERTY, sub, propDef, result.contextOrg)
                                    if (newProp.hasErrors()) {
                                        log.error(newProp.errors.toString())
                                    } else {
                                        log.debug("New custom property created: " + newProp.type.name)
                                        def newValue = copyProperty.getValue()
                                        if (copyProperty.type.isRefdataValueType()) {
                                            newValue = copyProperty.refValue ? copyProperty.refValue : null
                                        }
                                        def prop = setNewProperty(newProp, newValue)
                                        countSuccessfulCopy++
                                    }
                                }

                                /*if (existingProp) {
                            def customProp = SubscriptionCustomProperty.get(existingProp.id)
                            def prop = setNewProperty(customProp, copyProperty)
                        }*/
                            }
                        }
                    }
                }
            }
            flash.message = message(code: 'copyProperties.successful', args: [countSuccessfulCopy, message(code: 'copyProperties.' + params.tab) ,params.list('selectedSub').size()])
        }

        redirect(action: 'copyProperties', id: params.id, params: [surveyConfigID: result.surveyConfig.id, tab: params.tab, selectedProperty: params.selectedProperty, targetSubscriptionId: result.targetSubscription?.id])

    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
     Map<String,Object> processTransferParticipantsByRenewal() {
        Map<String,Object> result = setResultGenericsAndCheckAccess()
        if (!result.editable) {
            response.sendError(401); return
        }

        result.parentSubscription = result.surveyConfig.subscription
        result.parentSubChilds = subscriptionService.getValidSubChilds(result.parentSubscription)
        result.parentSuccessorSubscription = result.surveyConfig.subscription?._getCalculatedSuccessor()
        result.parentSuccessorSubChilds = result.parentSuccessorSubscription ? subscriptionService.getValidSubChilds(result.parentSuccessorSubscription) : null

        result.participationProperty = RDStore.SURVEY_PROPERTY_PARTICIPATION

        result.properties = []
        result.properties.addAll(SurveyConfigProperties.findAllBySurveyPropertyNotEqualAndSurveyConfig(result.participationProperty, result.surveyConfig)?.surveyProperty)

        result.multiYearTermThreeSurvey = null
        result.multiYearTermTwoSurvey = null

        if (RDStore.SURVEY_PROPERTY_MULTI_YEAR_3.id in result.properties.id) {
            result.multiYearTermThreeSurvey = RDStore.SURVEY_PROPERTY_MULTI_YEAR_3
            result.properties.remove(result.multiYearTermThreeSurvey)
        }
        if (RDStore.SURVEY_PROPERTY_MULTI_YEAR_2.id in result.properties.id) {
            result.multiYearTermTwoSurvey = RDStore.SURVEY_PROPERTY_MULTI_YEAR_2
            result.properties.remove(result.multiYearTermTwoSurvey)

        }

        result.parentSuccessortParticipantsList = []

        result.parentSuccessorSubChilds.each { sub ->
            Org org = sub.getSubscriber()
            result.parentSuccessortParticipantsList << org

        }

        result.newSubs = []

        Integer countNewSubs = 0

        SurveyResult.executeQuery("from SurveyResult where owner.id = :owner and surveyConfig.id = :surConfig and type.id = :surProperty and refValue = :refValue order by participant.sortname",
                [
                        owner      : result.institution.id,
                        surProperty: result.participationProperty.id,
                        surConfig  : result.surveyConfig.id,
                        refValue   : RDStore.YN_YES]).each {

            // Keine Kindlizenz in der Nachfolgerlizenz vorhanden
            if(!(it.participant in result.parentSuccessortParticipantsList)){

                List oldSubofParticipant = Subscription.executeQuery("Select s from Subscription s left join s.orgRelations orgR where s.instanceOf = :parentSub and orgR.org = :participant",
                        [parentSub  : result.parentSubscription,
                         participant: it.participant
                        ])[0]


                if(!oldSubofParticipant)
                {
                    oldSubofParticipant = result.parentSubscription
                }

                Date newStartDate = null
                Date newEndDate = null

                //Umfrage-Merkmal MJL2
                if (result.multiYearTermTwoSurvey) {

                    SurveyResult participantPropertyTwo = SurveyResult.findByParticipantAndOwnerAndSurveyConfigAndType(it.participant, result.institution, result.surveyConfig, result.multiYearTermTwoSurvey)
                    if (participantPropertyTwo && participantPropertyTwo.refValue?.id == RDStore.YN_YES.id) {
                        use(TimeCategory) {
                            newStartDate = oldSubofParticipant.startDate ? (oldSubofParticipant.endDate + 1.day) : null
                            newEndDate = oldSubofParticipant.endDate ? (oldSubofParticipant.endDate + 2.year) : null
                        }
                            countNewSubs++
                            result.newSubs << processAddMember(((oldSubofParticipant != result.parentSubscription) ? oldSubofParticipant: null), result.parentSuccessorSubscription, it.participant, newStartDate, newEndDate, true, params)
                    } else {
                        use(TimeCategory) {
                            newStartDate = oldSubofParticipant.startDate ? (oldSubofParticipant.endDate + 1.day) : null
                            newEndDate = oldSubofParticipant.endDate ? (oldSubofParticipant.endDate + 1.year) : null
                        }
                        countNewSubs++
                        result.newSubs << processAddMember(((oldSubofParticipant != result.parentSubscription) ? oldSubofParticipant: null), result.parentSuccessorSubscription, it.participant, newStartDate, newEndDate, false, params)
                    }

                }
                //Umfrage-Merkmal MJL3
                else if (result.multiYearTermThreeSurvey) {

                    SurveyResult participantPropertyThree = SurveyResult.findByParticipantAndOwnerAndSurveyConfigAndType(it.participant, result.institution, result.surveyConfig, result.multiYearTermThreeSurvey)
                    if (participantPropertyThree && participantPropertyThree.refValue?.id == RDStore.YN_YES.id) {
                        use(TimeCategory) {
                            newStartDate = oldSubofParticipant.startDate ? (oldSubofParticipant.endDate + 1.day) : null
                            newEndDate = oldSubofParticipant.endDate ? (oldSubofParticipant.endDate + 3.year) : null
                        }
                        countNewSubs++
                        result.newSubs << processAddMember(((oldSubofParticipant != result.parentSubscription) ? oldSubofParticipant: null), result.parentSuccessorSubscription, it.participant, newStartDate, newEndDate, true, params)
                    }
                    else {
                        use(TimeCategory) {
                            newStartDate = oldSubofParticipant.startDate ? (oldSubofParticipant.endDate + 1.day) : null
                            newEndDate = oldSubofParticipant.endDate ? (oldSubofParticipant.endDate + 1.year) : null
                        }
                        countNewSubs++
                        result.newSubs << processAddMember(((oldSubofParticipant != result.parentSubscription) ? oldSubofParticipant: null), result.parentSuccessorSubscription, it.participant, newStartDate, newEndDate, false, params)
                    }
                }else {
                    use(TimeCategory) {
                        newStartDate = oldSubofParticipant.startDate ? (oldSubofParticipant.endDate + 1.day) : null
                        newEndDate = oldSubofParticipant.endDate ? (oldSubofParticipant.endDate + 1.year) : null
                    }
                    countNewSubs++
                    result.newSubs << processAddMember(((oldSubofParticipant != result.parentSubscription) ? oldSubofParticipant: null), result.parentSuccessorSubscription, it.participant, newStartDate, newEndDate, false, params)
                }
            }
        }

        //MultiYearTerm Subs
        result.parentSubChilds.each { sub ->
            if (sub.isCurrentMultiYearSubscriptionNew()){
                sub.getAllSubscribers().each { org ->
                    if (!(org in result.parentSuccessortParticipantsList)) {

                        countNewSubs++
                        result.newSubs << processAddMember(sub, result.parentSuccessorSubscription, org, sub.startDate, sub.endDate, true, params)
                    }
                }
            }

        }

        result.countNewSubs = countNewSubs
        if(result.newSubs) {
            result.parentSuccessorSubscription.syncAllShares(result.newSubs)
        }
        flash.message = message(code: 'surveyInfo.transfer.info', args: [countNewSubs, result.newSubs.size() ?: 0])


        redirect(action: 'compareMembersOfTwoSubs', id: params.id, params: [surveyConfigID: result.surveyConfig.id])


    }

    private def processAddMember(Subscription oldSub, Subscription newParentSub, Org org, Date newStartDate, Date newEndDate, boolean multiYear, params) {

        Org institution = contextService.getOrg()

        RefdataValue subStatus = RDStore.SUBSCRIPTION_INTENDED

        RefdataValue role_sub       = RDStore.OR_SUBSCRIBER_CONS
        RefdataValue role_sub_cons  = RDStore.OR_SUBSCRIPTION_CONSORTIA
        RefdataValue role_sub_hidden = RDStore.OR_SUBSCRIBER_CONS_HIDDEN

        RefdataValue role_lic       = RDStore.OR_LICENSEE_CONS
        RefdataValue role_lic_cons  = RDStore.OR_LICENSING_CONSORTIUM

        RefdataValue role_provider  = RDStore.OR_PROVIDER
        RefdataValue role_agency    = RDStore.OR_AGENCY

        if (accessService.checkPerm("ORG_CONSORTIUM")) {

                License licenseCopy

                //def subLicense = newParentSub.owner

                Set<Package> packagesToProcess = []
                List<License> licensesToProcess = []

                //copy package data
                if(params.linkAllPackages) {
                    newParentSub.packages.each { sp ->
                        packagesToProcess << sp.pkg
                    }
                }else if(params.packageSelection) {
                    List packageIds = params.list("packageSelection")
                    packageIds.each { spId ->
                        packagesToProcess << SubscriptionPackage.get(spId).pkg
                    }
                }
                if(params.generateSlavedLics == "all") {
                    String query = "select li.sourceLicense from Links li where li.destinationSubscription = :subscription and li.linkType = :linkType"
                    licensesToProcess.addAll(License.executeQuery(query, [subscription:newParentSub, linkType:RDStore.LINKTYPE_LICENSE]))
                }
                else if(params.generateSlavedLics == "partial") {
                    List<String> licenseKeys = params.list("generateSlavedLicsReference")
                    licenseKeys.each { String licenseKey ->
                        licensesToProcess << genericOIDService.resolveOID(licenseKey)
                    }
                }


            log.debug("Generating seperate slaved instances for members")

                    SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
                    Date startDate = newStartDate ?: null
                    Date endDate = newEndDate ?: null

                    Subscription memberSub = new Subscription(
                            type: newParentSub.type ?: null,
                            kind: newParentSub.kind ?: null,
                            status: subStatus,
                            name: newParentSub.name,
                            startDate: startDate,
                            endDate: endDate,
                            administrative: newParentSub._getCalculatedType() == CalculatedType.TYPE_ADMINISTRATIVE,
                            manualRenewalDate: newParentSub.manualRenewalDate,
                            identifier: UUID.randomUUID().toString(),
                            instanceOf: newParentSub,
                            isSlaved: true,
                            resource: newParentSub.resource ?: null,
                            form: newParentSub.form ?: null,
                            isPublicForApi: newParentSub.isPublicForApi,
                            hasPerpetualAccess: newParentSub.hasPerpetualAccess,
                            isMultiYear: multiYear ?: false
                    )

                    if (!memberSub.save(flush:true)) {
                        memberSub.errors.each { e ->
                            log.debug("Problem creating new sub: ${e}")
                        }
                    }

                    if (memberSub) {
                        if(accessService.checkPerm("ORG_CONSORTIUM")) {

                            new OrgRole(org: org, sub: memberSub, roleType: role_sub).save(flush:true)
                            new OrgRole(org: institution, sub: memberSub, roleType: role_sub_cons).save(flush:true)

                            if(params.transferProviderAgency) {
                                newParentSub.getProviders().each { provider ->
                                    new OrgRole(org: provider, sub: memberSub, roleType: role_provider).save(flush:true)
                                }
                                newParentSub.getAgencies().each { provider ->
                                    new OrgRole(org: provider, sub: memberSub, roleType: role_agency).save(flush:true)
                                }
                            }else if(params.providersSelection) {
                                List orgIds = params.list("providersSelection")
                                orgIds.each { orgID ->
                                    new OrgRole(org: Org.get(orgID), sub: memberSub, roleType: role_provider).save(flush:true)
                                }
                            }else if(params.agenciesSelection) {
                                List orgIds = params.list("agenciesSelection")
                                orgIds.each { orgID ->
                                    new OrgRole(org: Org.get(orgID), sub: memberSub, roleType: role_agency).save(flush:true)
                                }
                            }

                        }

                        SubscriptionProperty.findAllByOwner(newParentSub).each { scp ->
                            AuditConfig ac = AuditConfig.getConfig(scp)

                            if (ac) {
                                // multi occurrence props; add one additional with backref
                                if (scp.type.multipleOccurrence) {
                                    def additionalProp = PropertyDefinition.createGenericProperty(PropertyDefinition.CUSTOM_PROPERTY, memberSub, scp.type, scp.tenant)
                                    additionalProp = scp.copyInto(additionalProp)
                                    additionalProp.instanceOf = scp
                                    additionalProp.save()
                                }
                                else {
                                    // no match found, creating new prop with backref
                                    def newProp = PropertyDefinition.createGenericProperty(PropertyDefinition.CUSTOM_PROPERTY, memberSub, scp.type, scp.tenant)
                                    newProp = scp.copyInto(newProp)
                                    newProp.instanceOf = scp
                                    newProp.save()
                                }
                            }
                        }

                        packagesToProcess.each { pkg ->
                            if(params.linkWithEntitlements)
                                pkg.addToSubscriptionCurrentStock(memberSub, newParentSub)
                            else
                                pkg.addToSubscription(memberSub, false)
                        }

                        licensesToProcess.each { License lic ->
                            subscriptionService.setOrgLicRole(memberSub,lic,false)
                        }

                        if(oldSub){
                            new Links(linkType: RDStore.LINKTYPE_FOLLOWS, source: genericOIDService.getOID(memberSub), destination: genericOIDService.getOID(oldSub), owner: contextService.getOrg()).save(flush:true)
                        }

                        if(org.getCustomerType() == 'ORG_INST') {
                            PendingChange.construct([target: memberSub, oid: "${memberSub.getClass().getName()}:${memberSub.id}", msgToken: "pendingChange.message_SU_NEW_01", status: RDStore.PENDING_CHANGE_PENDING, owner: org])
                        }

                        return memberSub
                    }
            }
    }

    private def addSubMembers(SurveyConfig surveyConfig) {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)

        result.editable = (accessService.checkMinUserOrgRole(result.user, result.institution, 'INST_EDITOR') && surveyConfig.surveyInfo.owner.id == result.institution.id)

        if (!result.editable) {
            return
        }

        List orgs = []
        List currentMembersSubs = subscriptionService.getValidSurveySubChilds(surveyConfig.subscription)

        currentMembersSubs.each{ sub ->
            orgs.addAll(sub.getAllSubscribers())
        }

        if (orgs) {

            orgs.each { org ->

                if (!(SurveyOrg.findAllBySurveyConfigAndOrg(surveyConfig, org))) {

                    boolean existsMultiYearTerm = false
                    Subscription sub = surveyConfig.subscription
                    if (sub && !surveyConfig.pickAndChoose && surveyConfig.subSurveyUseForTransfer) {
                        Subscription subChild = sub.getDerivedSubscriptionBySubscribers(org)

                        if (subChild && subChild.isCurrentMultiYearSubscriptionNew()) {
                            existsMultiYearTerm = true
                        }

                    }
                    if (!existsMultiYearTerm) {
                        SurveyOrg surveyOrg = new SurveyOrg(
                                surveyConfig: surveyConfig,
                                org: org
                        )

                        if (!surveyOrg.save()) {
                            log.debug("Error by add Org to SurveyOrg ${surveyOrg.errors}");
                        }else{
                            if(surveyConfig.surveyInfo.status in [RDStore.SURVEY_READY, RDStore.SURVEY_SURVEY_STARTED]) {
                                surveyConfig.surveyProperties.each { property ->

                                    SurveyResult surveyResult = new SurveyResult(
                                            owner: result.institution,
                                            participant: org ?: null,
                                            startDate: surveyConfig.surveyInfo.startDate,
                                            endDate: surveyConfig.surveyInfo.endDate ?: null,
                                            type: property.surveyProperty,
                                            surveyConfig: surveyConfig
                                    )

                                    if (surveyResult.save()) {
                                        log.debug( surveyResult.toString() )
                                    } else {
                                        log.error("Not create surveyResult: " + surveyResult)
                                    }
                                }

                                if (surveyConfig.surveyInfo.status == RDStore.SURVEY_SURVEY_STARTED) {
                                    surveyService.emailsToSurveyUsersOfOrg(surveyConfig.surveyInfo, org, false)
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    private def exportRenewalResult(Map renewalResult) {
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        List titles = [g.message(code: 'org.sortname.label'),
                       g.message(code: 'default.name.label'),

                       renewalResult.participationProperty?.getI10n('name'),
                       g.message(code: 'surveyResult.participantComment') + " " + renewalResult.participationProperty?.getI10n('name')
        ]


        titles << g.message(code: 'renewalWithSurvey.period')

        if (renewalResult.multiYearTermTwoSurvey || renewalResult.multiYearTermThreeSurvey)
        {
            titles << g.message(code: 'renewalWithSurvey.periodComment')
        }

        renewalResult.properties.each { surveyProperty ->
            titles << surveyProperty?.getI10n('name')
            titles << g.message(code: 'surveyResult.participantComment') + " " + g.message(code: 'renewalWithSurvey.exportRenewal.to') +" " + surveyProperty?.getI10n('name')
        }
        titles << g.message(code: 'renewalWithSurvey.costBeforeTax')
        titles << g.message(code: 'renewalWithSurvey.costAfterTax')
        titles << g.message(code: 'renewalWithSurvey.costTax')
        titles << g.message(code: 'renewalWithSurvey.currency')

        List renewalData = []

        renewalData.add([[field: g.message(code: 'renewalWithSurvey.continuetoSubscription.label')+ " (${renewalResult.orgsContinuetoSubscription.size() ?: 0})", style: 'positive']])

        renewalResult.orgsContinuetoSubscription.sort{it.participant.sortname}.each { participantResult ->
            List row = []

            row.add([field: participantResult.participant.sortname ?: '', style: null])
            row.add([field: participantResult.participant.name ?: '', style: null])
            row.add([field: participantResult.resultOfParticipation.getResult() ?: '', style: null])

            row.add([field: participantResult.resultOfParticipation.comment ?: '', style: null])


            String period = ""
            if (renewalResult.multiYearTermTwoSurvey) {
                period = participantResult.newSubPeriodTwoStartDate ? sdf.format(participantResult.newSubPeriodTwoStartDate) : ""
                period = participantResult.newSubPeriodTwoEndDate ? period + " - " +sdf.format(participantResult.newSubPeriodTwoEndDate) : ""
            }

            if (renewalResult.multiYearTermThreeSurvey) {
                period = participantResult.newSubPeriodThreeStartDate ? sdf.format(participantResult.newSubPeriodThreeStartDate) : ""
                period = participantResult.newSubPeriodThreeEndDate ? period + " - " +sdf.format(participantResult.newSubPeriodThreeEndDate) : ""
            }

            row.add([field: period ?: '', style: null])

            if (renewalResult.multiYearTermTwoSurvey) {
                row.add([field: participantResult.participantPropertyTwoComment ?: '', style: null])
            }

            if (renewalResult.multiYearTermThreeSurvey) {
                row.add([field: participantResult.participantPropertyThreeComment ?: '', style: null])
            }

            participantResult.properties.sort { it.type.name }.each { participantResultProperty ->
                row.add([field: participantResultProperty.getResult() ?: "", style: null])

                row.add([field: participantResultProperty.comment ?: "", style: null])

            }

            CostItem costItem = CostItem.findBySurveyOrgAndCostItemStatusNotEqual(SurveyOrg.findBySurveyConfigAndOrg(participantResult.resultOfParticipation.surveyConfig, participantResult.participant),RDStore.COST_ITEM_DELETED)

            row.add([field: costItem?.costInBillingCurrency ? costItem.costInBillingCurrency : "", style: null])
            row.add([field: costItem?.costInBillingCurrencyAfterTax ? costItem.costInBillingCurrencyAfterTax : "", style: null])
            row.add([field: costItem?.taxKey ? costItem.taxKey.taxRate+'%' : "", style: null])
            row.add([field: costItem?.billingCurrency ? costItem.billingCurrency.getI10n('value').split('-').first() : "", style: null])


            renewalData.add(row)
        }

        renewalData.add([[field: '', style: null]])
        renewalData.add([[field: '', style: null]])
        renewalData.add([[field: '', style: null]])
        renewalData.add([[field: g.message(code: 'renewalWithSurvey.withMultiYearTermSub.label')+ " (${renewalResult.orgsWithMultiYearTermSub.size() ?: 0})", style: 'positive']])


        renewalResult.orgsWithMultiYearTermSub.each { sub ->
            List row = []

            sub.getAllSubscribers().sort{it.sortname}.each{ subscriberOrg ->

                row.add([field: subscriberOrg.sortname ?: '', style: null])
                row.add([field: subscriberOrg.name ?: '', style: null])

                row.add([field: '', style: null])

                row.add([field: '', style: null])

                String period = ""

                period = sub.startDate ? sdf.format(sub.startDate) : ""
                period = sub.endDate ? period + " - " +sdf.format(sub.endDate) : ""

                row.add([field: period?: '', style: null])

                if (renewalResult.multiYearTermTwoSurvey || renewalResult.multiYearTermThreeSurvey)
                {
                    row.add([field: '', style: null])
                }

            }


            renewalData.add(row)
        }

        renewalData.add([[field: '', style: null]])
        renewalData.add([[field: '', style: null]])
        renewalData.add([[field: '', style: null]])
        renewalData.add([[field: g.message(code: 'renewalWithSurvey.orgsWithParticipationInParentSuccessor.label')+ " (${renewalResult.orgsWithParticipationInParentSuccessor.size() ?: 0})", style: 'positive']])


        renewalResult.orgsWithParticipationInParentSuccessor.each { sub ->
            List row = []

            sub.getAllSubscribers().sort{it.sortname}.each{ subscriberOrg ->

                row.add([field: subscriberOrg.sortname ?: '', style: null])
                row.add([field: subscriberOrg.name ?: '', style: null])

                row.add([field: '', style: null])

                row.add([field: '', style: null])

                String period = ""

                period = sub.startDate ? sdf.format(sub.startDate) : ""
                period = sub.endDate ? period + " - " +sdf.format(sub.endDate) : ""

                row.add([field: period?: '', style: null])

                if (renewalResult.multiYearTermTwoSurvey || renewalResult.multiYearTermThreeSurvey)
                {
                    row.add([field: '', style: null])
                }
            }


            renewalData.add(row)
        }

        renewalData.add([[field: '', style: null]])
        renewalData.add([[field: '', style: null]])
        renewalData.add([[field: '', style: null]])
        renewalData.add([[field: g.message(code: 'renewalWithSurvey.newOrgstoSubscription.label')+ " (${renewalResult.newOrgsContinuetoSubscription.size() ?: 0})", style: 'positive']])


        renewalResult.newOrgsContinuetoSubscription.sort{it.participant.sortname}.each { participantResult ->
            List row = []

            row.add([field: participantResult.participant.sortname ?: '', style: null])
            row.add([field: participantResult.participant.name ?: '', style: null])

            row.add([field: participantResult.resultOfParticipation.getResult() ?: '', style: null])

            row.add([field: participantResult.resultOfParticipation.comment ?: '', style: null])


            String period = ""
            if (renewalResult.multiYearTermTwoSurvey) {
                period = participantResult.newSubPeriodTwoStartDate ? sdf.format(participantResult.newSubPeriodTwoStartDate) : ""
                period = period + " - " + participantResult.newSubPeriodTwoEndDate ? sdf.format(participantResult.newSubPeriodTwoEndDate) : ""
            }
            period = ""
            if (renewalResult.multiYearTermThreeSurvey) {
                period = participantResult.newSubPeriodThreeStartDate ?: ""
                period = period + " - " + participantResult.newSubPeriodThreeEndDate ?: ""
            }
            row.add([field: period ?: '', style: null])

            if (renewalResult.multiYearTermTwoSurvey) {
                row.add([field: participantResult.participantPropertyTwoComment ?: '', style: null])
            }

            if (renewalResult.multiYearTermThreeSurvey) {
                row.add([field: participantResult.participantPropertyThreeComment ?: '', style: null])
            }

            participantResult.properties.sort {
                it.type.name
            }.each { participantResultProperty ->
                row.add([field: participantResultProperty.getResult() ?: "", style: null])

                row.add([field: participantResultProperty.comment ?: "", style: null])

            }

            CostItem costItem = CostItem.findBySurveyOrgAndCostItemStatusNotEqual(SurveyOrg.findBySurveyConfigAndOrg(participantResult.resultOfParticipation.surveyConfig, participantResult.participant),RDStore.COST_ITEM_DELETED)
            row.add([field: costItem?.costInBillingCurrency ? costItem.costInBillingCurrency : "", style: null])
            row.add([field: costItem?.costInBillingCurrencyAfterTax ? costItem.costInBillingCurrencyAfterTax : "", style: null])
            row.add([field: costItem?.taxKey ? costItem.taxKey.taxRate+'%' : "", style: null])
            row.add([field: costItem?.billingCurrency ? costItem.billingCurrency.getI10n('value').split('-').first() : "", style: null])

            renewalData.add(row)
        }

        renewalData.add([[field: '', style: null]])
        renewalData.add([[field: '', style: null]])
        renewalData.add([[field: '', style: null]])
        renewalData.add([[field: g.message(code: 'renewalWithSurvey.withTermination.label')+ " (${renewalResult.orgsWithTermination.size() ?: 0})", style: 'negative']])


        renewalResult.orgsWithTermination.sort{it.participant.sortname}.each { participantResult ->
            List row = []

            row.add([field: participantResult.participant.sortname ?: '', style: null])
            row.add([field: participantResult.participant.name ?: '', style: null])

            row.add([field: participantResult.resultOfParticipation.getResult() ?: '', style: null])

            row.add([field: participantResult.resultOfParticipation.comment ?: '', style: null])

            row.add([field: '', style: null])

            if (renewalResult.multiYearTermTwoSurvey || renewalResult.multiYearTermThreeSurvey)
            {
                row.add([field: '', style: null])
            }

            participantResult.properties.sort {
                it.type.name
            }.each { participantResultProperty ->
                row.add([field: participantResultProperty.getResult() ?: "", style: null])

                row.add([field: participantResultProperty.comment ?: "", style: null])

            }

            CostItem costItem = CostItem.findBySurveyOrgAndCostItemStatusNotEqual(SurveyOrg.findBySurveyConfigAndOrg(participantResult.resultOfParticipation.surveyConfig, participantResult.participant),RDStore.COST_ITEM_DELETED)

            row.add([field: costItem?.costInBillingCurrency ? costItem.costInBillingCurrency : "", style: null])
            row.add([field: costItem?.costInBillingCurrencyAfterTax ? costItem.costInBillingCurrencyAfterTax : "", style: null])
            row.add([field: costItem?.taxKey ? costItem.taxKey.taxRate+'%' : "", style: null])
            row.add([field: costItem?.billingCurrency ? costItem.billingCurrency.getI10n('value').split('-').first() : "", style: null])

            renewalData.add(row)
        }

        renewalData.add([[field: '', style: null]])
        renewalData.add([[field: '', style: null]])
        renewalData.add([[field: '', style: null]])
        renewalData.add([[field: g.message(code: 'surveys.tabs.termination')+ " (${renewalResult.orgsWithoutResult.size()})", style: 'negative']])


        renewalResult.orgsWithoutResult.sort{it.participant.sortname}.each { participantResult ->
            List row = []

            row.add([field: participantResult.participant.sortname ?: '', style: null])
            row.add([field: participantResult.participant.name ?: '', style: null])

            row.add([field: participantResult.resultOfParticipation.getResult() ?: '', style: null])

            row.add([field: participantResult.resultOfParticipation.comment ?: '', style: null])

            row.add([field: '', style: null])

            if (renewalResult.multiYearTermTwoSurvey || renewalResult.multiYearTermThreeSurvey)
            {
                row.add([field: '', style: null])
            }

            participantResult.properties.sort {
                it.type.name
            }.each { participantResultProperty ->
                row.add([field: participantResultProperty.getResult() ?: "", style: null])

                row.add([field: participantResultProperty.comment ?: "", style: null])

            }

            CostItem costItem = CostItem.findBySurveyOrgAndCostItemStatusNotEqual(SurveyOrg.findBySurveyConfigAndOrg(participantResult.resultOfParticipation.surveyConfig, participantResult.participant),RDStore.COST_ITEM_DELETED)

            row.add([field: costItem?.costInBillingCurrency ? costItem.costInBillingCurrency : "", style: null])
            row.add([field: costItem?.costInBillingCurrencyAfterTax ? costItem.costInBillingCurrencyAfterTax : "", style: null])
            row.add([field: costItem?.taxKey ? costItem.taxKey.taxRate+'%' : "", style: null])
            row.add([field: costItem?.billingCurrency ? costItem.billingCurrency.getI10n('value').split('-').first() : "", style: null])

            renewalData.add(row)
        }


        Map sheetData = [:]
        sheetData[message(code: 'renewalexport.renewals')] = [titleRow: titles, columnData: renewalData]
        return exportService.generateXLSXWorkbook(sheetData)
    }

    private Map<String,Object> setResultGenericsAndCheckAccess() {
        Map<String, Object> result = [:]
        result.institution = contextService.getOrg()
        result.contextOrg = contextService.getOrg()
        result.user = User.get(springSecurityService.principal.id)
        result.surveyInfo = SurveyInfo.get(params.id)
        result.surveyConfig = params.surveyConfigID ? SurveyConfig.get(params.surveyConfigID as Long ? params.surveyConfigID: Long.parseLong(params.surveyConfigID)) : result.surveyInfo.surveyConfigs[0]
        result.surveyWithManyConfigs = (result.surveyInfo.surveyConfigs?.size() > 1)

        result.editable = result.surveyInfo.isEditable() ?: false

        if(result.surveyConfig)
        {
            result.transferWorkflow = result.surveyConfig.transferWorkflow ? JSON.parse(result.surveyConfig.transferWorkflow) : null
        }

        result.subscriptionInstance =  result.surveyConfig.subscription ?: null

        result
    }

    private Map<String,Object> setResultGenericsAndCheckAccessforSub(checkOption) {
        Map<String, Object> result = [:]
        result.user = User.get(springSecurityService.principal.id)
        result.subscriptionInstance = Subscription.get(params.id)
        result.subscription = Subscription.get(params.id)
        result.institution = result.subscription.subscriber

        if (checkOption in [AccessService.CHECK_VIEW, AccessService.CHECK_VIEW_AND_EDIT]) {
            if (!result.subscriptionInstance.isVisibleBy(result.user)) {
                log.debug("--- NOT VISIBLE ---")
                return null
            }
        }
        result.editable = result.subscriptionInstance.isEditableBy(result.user)

        if (checkOption in [AccessService.CHECK_EDIT, AccessService.CHECK_VIEW_AND_EDIT]) {
            if (!result.editable) {
                log.debug("--- NOT EDITABLE ---")
                return null
            }
        }

        result
    }

    private def setNewProperty(def property, def value) {

        String field = null

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
            property.save()
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
                    if(field == "decValue") {
                        value = new BigDecimal(value)
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
    
    boolean copySurveyConfigCharacteristic(SurveyConfig oldSurveyConfig, SurveyConfig newSurveyConfig, params){

        oldSurveyConfig.documents.each { dctx ->
                //Copy Docs
                if (params.copySurvey.copyDocs) {
                    if ((dctx.owner?.contentType == Doc.CONTENT_TYPE_FILE) && (dctx.status != RDStore.DOC_CTX_STATUS_DELETED)) {
                        Doc clonedContents = new Doc(
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
                                surveyConfig: newSurveyConfig,
                                domain: dctx.domain,
                                status: dctx.status,
                                doctype: dctx.doctype
                        ).save(flush:true)
                    }
                }
                //Copy Announcements
                if (params.copySurvey.copyAnnouncements) {
                    if ((dctx.owner?.contentType == Doc.CONTENT_TYPE_STRING) && !(dctx.domain) && (dctx.status != RDStore.DOC_CTX_STATUS_DELETED)) {
                        Doc clonedContents = new Doc(
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
                                surveyConfig: newSurveyConfig,
                                domain: dctx.domain,
                                status: dctx.status,
                                doctype: dctx.doctype
                        ).save(flush:true)
                    }
                }
            }
            //Copy Tasks
            if (params.copySurvey.copyTasks) {

                Task.findAllBySurveyConfig(oldSurveyConfig).each { task ->

                    Task newTask = new Task()
                    InvokerHelper.setProperties(newTask, task.properties)
                    newTask.systemCreateDate = new Date()
                    newTask.surveyConfig = newSurveyConfig
                    newTask.save(flush:true)
                }

            }

        //Copy Participants
        if (params.copySurvey.copyParticipants) {
            oldSurveyConfig.orgs.each { surveyOrg ->

                SurveyOrg newSurveyOrg = new SurveyOrg(surveyConfig: newSurveyConfig, org: surveyOrg.org).save(flush:true)
            }
        }

        //Copy Properties
        if (params.copySurvey.copySurveyProperties) {
            oldSurveyConfig.surveyProperties.each { surveyConfigProperty ->

                SurveyConfigProperties configProperty = new SurveyConfigProperties(
                        surveyProperty: surveyConfigProperty.surveyProperty,
                        surveyConfig: newSurveyConfig).save(flush:true)
            }
        }
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_USER", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
     Map<String,Object> copyElementsIntoSurvey() {
        def result             = [:]
        result.user            = User.get(springSecurityService.principal.id)
        result.institution     = contextService.org
        result.contextOrg      = result.institution

        flash.error = ""
        flash.message = ""
        if (params.sourceObjectId == "null") params.remove("sourceObjectId")
        result.sourceObjectId = params.sourceObjectId ?: params.id
        result.sourceObject = genericOIDService.resolveOID(params.sourceObjectId)

        if (params.targetObjectId == "null") params.remove("targetObjectId")
        if (params.targetObjectId) {
            result.targetObjectId = params.targetObjectId
            result.targetObject = genericOIDService.resolveOID(params.targetObjectId)
        }

        result.editable = result.sourceObject.surveyInfo.isEditable()

        if (!result.editable) {
            response.sendError(401); return
        }

        result.allObjects_readRights = SurveyConfig.executeQuery("select surConfig from SurveyConfig as surConfig join surConfig.surveyInfo as surInfo where surInfo.owner = :contextOrg order by surInfo.name", [contextOrg: result.contextOrg])
        //Nur Umfragen, die noch in Bearbeitung sind da sonst Umfragen-Prozesse zerstört werden.
        result.allObjects_writeRights = SurveyConfig.executeQuery("select surConfig from SurveyConfig as surConfig join surConfig.surveyInfo as surInfo where surInfo.owner = :contextOrg and surInfo.status = :status order by surInfo.name", [contextOrg: result.contextOrg, status: RDStore.SURVEY_IN_PROCESSING])

        switch (params.workFlowPart) {
            case CopyElementsService.WORKFLOW_DATES_OWNER_RELATIONS:
                result << copyElementsService.copyObjectElements_DatesOwnerRelations(params)
                result << copyElementsService.loadDataFor_DatesOwnerRelations(params)
                break
            case CopyElementsService.WORKFLOW_DOCS_ANNOUNCEMENT_TASKS:
                result << copyElementsService.copyObjectElements_DocsAnnouncementsTasks(params)
                result << copyElementsService.loadDataFor_DocsAnnouncementsTasks(params)
                break
            case CopyElementsService.WORKFLOW_SUBSCRIBER:
                result << copyElementsService.copyObjectElements_Subscriber(params)
                result << copyElementsService.loadDataFor_Subscriber(params)
                break
            case CopyElementsService.WORKFLOW_PROPERTIES:
                result << copyElementsService.copyObjectElements_Properties(params)
                result << copyElementsService.loadDataFor_Properties(params)
                break
            case CopyElementsService.WORKFLOW_END:
                result << copyElementsService.copyObjectElements_Properties(params)
                if (params.targetObjectId){
                    flash.error = ""
                    flash.message = ""
                    redirect controller: 'survey', action: 'show', params: [id: result.targetObject.surveyInfo.id, surveyConfigID: result.targetObject.id]
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

        result
    }
}
