package de.laser


import com.k_int.kbplus.ExportService
import com.k_int.kbplus.InstitutionsService
import de.laser.properties.LicenseProperty
import de.laser.properties.OrgProperty
import com.k_int.kbplus.PackageService
import com.k_int.kbplus.PendingChangeService
import de.laser.properties.PersonProperty
import de.laser.properties.PlatformProperty
import de.laser.properties.SubscriptionProperty
import de.laser.titles.TitleInstance
import com.k_int.kbplus.auth.Role
import com.k_int.kbplus.auth.User
import com.k_int.kbplus.auth.UserOrg
import de.laser.base.AbstractPropertyWithCalculatedLastUpdated
import de.laser.controller.AbstractDebugController
import de.laser.finance.BudgetCode
import de.laser.finance.CostItem
import de.laser.finance.CostItemGroup
import de.laser.helper.*
import de.laser.properties.PropertyDefinition
import de.laser.properties.PropertyDefinitionGroup
import de.laser.properties.PropertyDefinitionGroupItem
import de.laser.system.SystemAnnouncement
import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.annotation.Secured
import org.apache.commons.collections.BidiMap
import org.apache.commons.collections.bidimap.DualHashBidiMap

//import de.laser.TaskService //unused for quite a long time

import org.apache.poi.POIXMLProperties
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.streaming.SXSSFSheet
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.codehaus.groovy.grails.commons.GrailsClass
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.mozilla.universalchardet.UniversalDetector
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.multipart.commons.CommonsMultipartFile

import javax.servlet.ServletOutputStream
import java.nio.charset.Charset
import java.text.DateFormat
import java.text.SimpleDateFormat

@Secured(['IS_AUTHENTICATED_FULLY'])
class MyInstitutionController extends AbstractDebugController {
    def dataSource
    def springSecurityService
    def userService
    def genericOIDService
    PendingChangeService pendingChangeService
    ExportService exportService
    PackageService packageService
    def escapeService
    def institutionsService
    def docstoreService
    def addressbookService
    def accessService
    def contextService
    def taskService
    def filterService
    def propertyService
    def subscriptionsQueryService
    def orgTypeService
    def subscriptionService
    def organisationService
    def financeService
    def surveyService
    def formService
    def dashboardDueDatesService
    LinksGenerationService linksGenerationService
    ComparisonService comparisonService

    // copied from
    static String INSTITUTIONAL_LICENSES_QUERY      =
            " from License as l where exists ( select ol from OrgRole as ol where ol.lic = l AND ol.org = :lic_org and ol.roleType IN (:org_roles) ) "

    // copied from
    static String INSTITUTIONAL_SUBSCRIPTION_QUERY  =
            " from Subscription as s where  ( ( exists ( select o from s.orgRelations as o where ( o.roleType IN (:roleTypes) AND o.org = :activeInst ) ) ) ) "

    // Map the parameter names we use in the webapp with the ES fields
    def renewals_reversemap = ['subject': 'subject', 'provider': 'provid', 'pkgname': 'tokname']
    def reversemap = ['subject': 'subject', 'provider': 'provid', 'studyMode': 'presentations.studyMode', 'qualification': 'qual.type', 'level': 'qual.level']

    @DebugAnnotation(test='hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def index() {
        redirect(action:'dashboard')
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_USER")
    })
    def reporting() {
        Map<String, Object> result = setResultGenerics()
        result.subStatus = RefdataCategory.getAllRefdataValues(RDConstants.SUBSCRIPTION_STATUS)
        result.subProp = PropertyDefinition.findAllPublicAndPrivateProp([PropertyDefinition.SUB_PROP], result.institution)
        result.subForm = RefdataCategory.getAllRefdataValues(RDConstants.SUBSCRIPTION_FORM)
        result.subResourceType = RefdataCategory.getAllRefdataValues(RDConstants.SUBSCRIPTION_RESOURCE)
        result.subKind = RefdataCategory.getAllRefdataValues(RDConstants.SUBSCRIPTION_KIND)
        result
    }

    @Deprecated
    @DebugAnnotation(test='hasAffiliation("INST_ADM")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_ADM") })
    def manageAffiliationRequests() {
        redirect controller: 'organisation', action: 'users', id: contextService.getOrg().id

        Map<String, Object> result = [:]
        result.institution        = contextService.getOrg()
        result.user               = User.get(springSecurityService.principal.id)
        result.editable           = true // inherit
        result.pendingRequestsOrg = UserOrg.findAllByStatusAndOrg(UserOrg.STATUS_PENDING, contextService.getOrg(), [sort:'dateRequested'])

        result
    }

    @Secured(['ROLE_USER'])
    def currentPlatforms() {

        Map<String, Object> result = [:]
		ProfilerUtils pu = new ProfilerUtils()
		pu.setBenchmark('init')

        result.user = User.get(springSecurityService.principal.id)
        result.max = params.max ?: result.user.getDefaultPageSize()
        result.offset = params.offset ?: 0
        result.contextOrg = contextService.org

        EhcacheWrapper cache = contextService.getCache('MyInstitutionController/currentPlatforms', contextService.ORG_SCOPE)

        List idsCurrentSubscriptions = []
        List idsCategory1 = []
        List idsCategory2 = []

        if (cache.get('currentSubInfo')) {
            def currentSubInfo = cache.get('currentSubInfo')

            idsCurrentSubscriptions = currentSubInfo['idsCurrentSubscriptions']
            idsCategory1 = currentSubInfo['idsCategory1']
            idsCategory2 = currentSubInfo['idsCategory2']

            log.debug('currentSubInfo from cache')
        }
        else {
            idsCurrentSubscriptions = orgTypeService.getCurrentSubscriptionIds(contextService.getOrg())

            idsCategory1 = OrgRole.executeQuery("select distinct (sub.id) from OrgRole where org=:org and roleType in (:roleTypes)", [
                    org: contextService.getOrg(), roleTypes: [
                        RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIBER_COLLECTIVE
                    ]
            ])
            idsCategory2 = OrgRole.executeQuery("select distinct (sub.id) from OrgRole where org=:org and roleType in (:roleTypes)", [
                    org: contextService.getOrg(), roleTypes: [
                    RDStore.OR_SUBSCRIPTION_CONSORTIA, RDStore.OR_SUBSCRIPTION_COLLECTIVE
                ]
            ])

            cache.put('currentSubInfo', [
                    idsCurrentSubscriptions: idsCurrentSubscriptions,
                    idsCategory1: idsCategory1,
                    idsCategory2: idsCategory2
            ])
        }

        result.subscriptionMap = [:]
        result.platformInstanceList = []

        if (idsCurrentSubscriptions) {

            String qry3 = "select distinct p, s from SubscriptionPackage subPkg join subPkg.subscription s join subPkg.pkg pkg, " +
                    "TitleInstancePackagePlatform tipp join tipp.platform p left join p.org o " +
                    "where tipp.pkg = pkg and s.id in (:subIds) "

            qry3 += " and ((pkg.packageStatus is null) or (pkg.packageStatus != :pkgDeleted))"
            qry3 += " and ((p.status is null) or (p.status != :platformDeleted))"
            qry3 += " and ((tipp.status is null) or (tipp.status != :tippDeleted))"

            def qryParams3 = [
                    subIds         : idsCurrentSubscriptions,
                    pkgDeleted     : RDStore.PACKAGE_STATUS_DELETED,
                    platformDeleted: RDStore.PLATFORM_STATUS_DELETED,
                    tippDeleted    : RDStore.TIPP_STATUS_DELETED
            ]

            if (params.q?.length() > 0) {
                qry3 += "and ("
                qry3 += "   genfunc_filter_matcher(p.normname, :query) = true"
                qry3 += "   or genfunc_filter_matcher(p.primaryUrl, :query) = true"
                qry3 += "   or genfunc_filter_matcher(o.name, :query) = true"
                qry3 += "   or genfunc_filter_matcher(o.sortname, :query) = true"
                qry3 += "   or genfunc_filter_matcher(o.shortname, :query) = true "
                qry3 += ")"
                qry3 += " group by p, s"
                qryParams3.put('query', "${params.q}")
            }
            else {
                qry3 += " group by p, s"
                qry3 += " order by p.normname asc"
            }

            List platformSubscriptionList = Subscription.executeQuery(qry3, qryParams3)

            log.debug("found ${platformSubscriptionList.size()} in list ..")
            /*, [max:result.max, offset:result.offset])) */

            platformSubscriptionList.each { entry ->
                // entry[0] = Platform
                // entry[0] = Subscription

                String key = 'platform_' + entry[0].id

                if (! result.subscriptionMap.containsKey(key)) {
                    result.subscriptionMap.put(key, [])
                    result.platformInstanceList.add(entry[0])
                }

                if (entry[1].status?.value == RDStore.SUBSCRIPTION_CURRENT.value) {

                    if (idsCategory1.contains(entry[1].id)) {
                        result.subscriptionMap.get(key).add(entry[1])
                    }
                    else if (idsCategory2.contains(entry[1].id) && entry[1].instanceOf == null) {
                        result.subscriptionMap.get(key).add(entry[1])
                    }
                }
            }
        }
        result.platformInstanceTotal    = result.platformInstanceList.size()

        result.cachedContent = true

		List bm = pu.stopBenchmark()
		result.benchMark = bm

        result
    }

    @DebugAnnotation(test='hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def currentLicenses() {

        Map<String, Object> result = setResultGenerics()
        EhcacheWrapper cache = contextService.getCache("/license/filter/",ContextService.USER_SCOPE)
        if(cache && cache.get('licenseFilterCache')) {
            if(!params.resetFilter && !params.filterSet)
                params.putAll((GrailsParameterMap) cache.get('licenseFilterCache'))
            else params.remove('resetFilter')
            cache.remove('licenseFilterCache') //has to be executed in any case in order to enable cache updating
        }
		ProfilerUtils pu = new ProfilerUtils()
		pu.setBenchmark('init')

        result.is_inst_admin = accessService.checkMinUserOrgRole(result.user, result.institution, 'INST_ADM')

        def date_restriction = null
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()

        if (params.validOn == null || params.validOn.trim() == '') {
            result.validOn = ""
        } else {
            result.validOn = params.validOn
            date_restriction = sdf.parse(params.validOn)
        }

        result.propList = PropertyDefinition.findAllPublicAndPrivateProp([PropertyDefinition.LIC_PROP], contextService.org)
        result.max      = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset   = params.offset ? Integer.parseInt(params.offset) : 0;
        result.max      = params.format ? 10000 : result.max
        result.offset   = params.format? 0 : result.offset
        result.compare = params.compare ?: ''

        RefdataValue licensee_role           = RDStore.OR_LICENSEE
        RefdataValue licensee_cons_role      = RDStore.OR_LICENSEE_CONS
        RefdataValue lic_cons_role           = RDStore.OR_LICENSING_CONSORTIUM

        String base_qry
        Map qry_params

        result.filterSet = params.filterSet ? true : false
        if(result.filterSet) {
            cache.put('licenseFilterCache', params)
        }

        Set<String> licenseFilterTable = []

        if (accessService.checkPerm("ORG_INST")) {
            base_qry = """from License as l where (
                exists ( select o from l.orgRelations as o where ( ( o.roleType = :roleType1 or o.roleType = :roleType2 ) AND o.org = :lic_org ) ) 
            )"""
            qry_params = [roleType1:licensee_role, roleType2:licensee_cons_role, lic_org:result.institution]
            if(result.editable)
                licenseFilterTable << "action"
            licenseFilterTable << "licensingConsortium"
        }
        else if (accessService.checkPerm("ORG_CONSORTIUM")) {
            base_qry = "from License as l where exists ( select o from l.orgRelations as o where ( o.roleType = :roleTypeC AND o.org = :lic_org AND l.instanceOf is null AND NOT exists ( select o2 from l.orgRelations as o2 where o2.roleType = :roleTypeL ) ) )"
            qry_params = [roleTypeC:lic_cons_role, roleTypeL:licensee_cons_role, lic_org:result.institution]
            licenseFilterTable << "memberLicenses"
            if(result.editable)
                licenseFilterTable << "action"
        }
        else {
            base_qry = "from License as l where exists ( select o from l.orgRelations as o where  o.roleType = :roleType AND o.org = :lic_org ) "
            qry_params = [roleType:licensee_cons_role, lic_org:result.institution]
            licenseFilterTable << "licensingConsortium"
        }
        result.licenseFilterTable = licenseFilterTable

        if(params.subKind || params.subStatus || ((params['keyword-search'] != null) && (params['keyword-search'].trim().length() > 0)) || !params.filterSubmit) {
            Set<String> subscrQueryFilter = ["oo.org = :context"]
            qry_params.context = result.institution

            //the if needs to be done twice, here is the second case because the keyword may occur in subscriptions but also in licenses!
            if(params['keyword-search'] != null && params['keyword-search'].trim().length() > 0) {
                subscrQueryFilter << "genfunc_filter_matcher(s.name, :name_filter) = true"
            }

            if(params.subStatus || !params.filterSubmit) {
                subscrQueryFilter <<  "s.status.id = :subStatus"
                if(!params.filterSubmit) {
                    params.subStatus = RDStore.SUBSCRIPTION_CURRENT.id
                    result.filterSet = true
                }
                qry_params.subStatus = params.subStatus as Long
            }

            if(params.subKind) {
                subscrQueryFilter << "s.kind.id in (:subKinds)"
                List<Long> subKinds = []
                List<String> selKinds = params.list('subKind')
                selKinds.each { String sel ->
                    subKinds << Long.parseLong(sel)
                }
                qry_params.subKinds = subKinds
            }

            if(accessService.checkPerm("ORG_CONSORTIUM")) {
                subscrQueryFilter << "s.instanceOf is null"
            }

            base_qry += " or exists ( select li from Links li join li.destinationSubscription s left join s.orgRelations oo where li.sourceLicense = l and li.linkType = :linkType and "+subscrQueryFilter.join(" and ")+" )"
            qry_params.linkType = RDStore.LINKTYPE_LICENSE
        }
        if ((params['keyword-search'] != null) && (params['keyword-search'].trim().length() > 0)) {
            base_qry += (" and ( genfunc_filter_matcher(l.reference, :name_filter) = true " // filter by license
                    + " or exists ( select orgR from OrgRole as orgR where orgR.lic = l and ( "
                    + "   orgR.roleType in (:licRoleTypes) and ( "
                    + " genfunc_filter_matcher(orgR.org.name, :name_filter) = true "
                    + " or genfunc_filter_matcher(orgR.org.shortname, :name_filter) = true "
                    + " or genfunc_filter_matcher(orgR.org.sortname, :name_filter) = true "
                    + " ) ) ) ) ")
            qry_params.name_filter = params['keyword-search']
            qry_params.licRoleTypes = [RDStore.OR_LICENSOR, RDStore.OR_LICENSING_CONSORTIUM]
            result.keyWord = params['keyword-search']
        }

        if(params.consortium) {
            base_qry += " and ( exists ( select o from l.orgRelations as o where o.roleType = :licCons and o.org.id in (:cons) ) ) "
            List<Long> consortia = []
            List<String> selCons = params.list('consortium')
            selCons.each { String sel ->
                consortia << Long.parseLong(sel)
            }
            qry_params += [licCons:lic_cons_role,cons:consortia]
        }

        if (date_restriction) {
            base_qry += " and ( ( l.startDate <= :date_restr and l.endDate >= :date_restr ) OR l.startDate is null OR l.endDate is null ) "
            qry_params += [date_restr: date_restriction]
            qry_params += [date_restr: date_restriction]
        }

        // eval property filter

        if (params.filterPropDef) {
            def psq = propertyService.evalFilterQuery(params, base_qry, 'l', qry_params)
            base_qry = psq.query
            qry_params = psq.queryParams
        }

        if(params.licensor) {
            base_qry += " and ( exists ( select o from l.orgRelations as o where o.roleType = :licCons and o.org.id in (:licensors) ) ) "
            List<Long> licensors = []
            List<String> selLicensors = params.list('licensor')
            selLicensors.each { String sel ->
                licensors << Long.parseLong(sel)
            }
            qry_params += [licCons:RDStore.OR_LICENSOR,licensors:licensors]
        }

        if(params.categorisation) {
            base_qry += " and l.licenseCategory.id in (:categorisations) "
            List<Long> categorisations = []
            List<String> selCategories = params.list('categorisation')
            selCategories.each { String sel ->
                categorisations << Long.parseLong(sel)
            }
            qry_params.categorisations = categorisations
        }



        if(params.status || !params.filterSubmit) {
            base_qry += " and l.status.id = :status "
            if(!params.filterSubmit) {
                params.status = RDStore.LICENSE_CURRENT.id
                result.filterSet = true
            }
            qry_params.status = params.status as Long
        }

        if ((params.sort != null) && (params.sort.length() > 0)) {
            base_qry += " order by l.${params.sort} ${params.order}"
        } else {
            base_qry += " order by lower(trim(l.reference)) asc"
        }

        //log.debug("query = ${base_qry}");
        //log.debug("params = ${qry_params}");
        pu.setBenchmark('execute query')
        List<License> totalLicenses = License.executeQuery( "select l " + base_qry, qry_params )
        result.licenseCount = totalLicenses.size()
        pu.setBenchmark('get subscriptions')

        result.licenses = totalLicenses.drop((int) result.offset).take((int) result.max)
        if(result.licenses)
            result.allLinkedSubscriptions = Subscription.executeQuery("select li from Links li join li.destinationSubscription s where li.sourceLicense in (:licenses) and li.linkType = :linkType and s.status.id = :status",[licenses:result.licenses,linkType:RDStore.LINKTYPE_LICENSE,status:qry_params.subStatus])

        List orgRoles = OrgRole.findAllByOrgAndLicIsNotNull(result.institution)
        result.orgRoles = [:]
        orgRoles.each { OrgRole oo ->
            result.orgRoles.put(oo.lic.id,oo.roleType)
        }
        pu.setBenchmark('get consortia')
        Set<Org> consortia = Org.executeQuery("select os.org from OrgSetting os where os.key = 'CUSTOMER_TYPE' and os.roleValue in (select r from Role r where authority = 'ORG_CONSORTIUM') order by os.org.name asc")
        pu.setBenchmark('get licensors')
        Set<Org> licensors = orgTypeService.getOrgsForTypeLicensor()
        Map<String,Set<Org>> orgs = [consortia:consortia,licensors:licensors]
        result.orgs = orgs

		List bm = pu.stopBenchmark()
		result.benchMark = bm

        SimpleDateFormat sdfNoPoint = DateUtil.getSDF_NoTimeNoPoint()
        String filename = "${sdfNoPoint.format(new Date(System.currentTimeMillis()))}_${g.message(code: 'export.my.currentLicenses')}"
        List titles = [
                g.message(code:'license.details.reference'),
                g.message(code:'license.details.linked_subs'),
                g.message(code:'consortium'),
                g.message(code:'license.licensor.label'),
                g.message(code:'license.startDate.label'),
                g.message(code:'license.endDate.label')
        ]
        Map<License,Set<License>> licChildMap = [:]
        List<License> childLicsOfSet = totalLicenses.isEmpty() ? [] : License.findAllByInstanceOfInList(totalLicenses)
        childLicsOfSet.each { License child ->
            Set<License> children = licChildMap.get(child.instanceOf)
            if(!children)
                children = []
            children << child
            licChildMap.put(child.instanceOf,children)
        }
        Set<PropertyDefinition> propertyDefinitions = PropertyDefinition.findAllPublicAndPrivateProp([PropertyDefinition.LIC_PROP],result.institution)
        titles.addAll(exportService.loadPropListHeaders(propertyDefinitions))
        Map objectNames = [:]
        if(childLicsOfSet) {
            Set rows = OrgRole.executeQuery('select oo.sub,oo.org.sortname from OrgRole oo where oo.sub in (:subChildren) and oo.roleType = :licType',[subChildren:childLicsOfSet,licType:RDStore.OR_LICENSEE_CONS])
            rows.each { row ->
                log.debug("now processing ${row[0]}:${row[1]}")
                objectNames.put(row[0],row[1])
            }
        }
        if(params.exportXLS) {
            response.setHeader("Content-disposition", "attachment; filename=\"${filename}.xlsx\"")
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            List rows = []
            totalLicenses.each { License licObj ->
                License license = (License) licObj
                List row = [[field:license.reference.replaceAll(',',' '),style:'bold']]
                List linkedSubs = license.subscriptions.collect { sub ->
                    sub.name
                }
                row.add([field:linkedSubs.join(", "),style:null])
                row.add([field:license.licensingConsortium ? license.licensingConsortium.name : '',style:null])
                row.add([field:license.licensor ? license.licensor.name : '',style:null])
                row.add([field:license.startDate ? sdf.format(license.startDate) : '',style:null])
                row.add([field:license.endDate ? sdf.format(license.endDate) : '',style:null])
                row.addAll(exportService.processPropertyListValues(propertyDefinitions,'xls',license,licChildMap,objectNames))
                rows.add(row)
            }
            Map sheetData = [:]
            sheetData[g.message(code:'menu.my.licenses')] = [titleRow:titles,columnData:rows]
            SXSSFWorkbook workbook = exportService.generateXLSXWorkbook(sheetData)
            workbook.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            workbook.dispose()
            return
        }
        withFormat {
            html result

            json {
                response.setHeader("Content-disposition", "attachment; filename=\"${filename}.json\"")
                response.contentType = "application/json"
                render (result as JSON)
            }
            csv {
                response.setHeader("Content-disposition", "attachment; filename=\"${filename}.csv\"")
                response.contentType = "text/csv"
                ServletOutputStream out = response.outputStream
                List rows = []
                totalLicenses.each { licObj ->
                    License license = (License) licObj
                    List row = [license.reference.replaceAll(',',' ')]
                    List linkedSubs = license.subscriptions.collect { sub ->
                        sub.name.replaceAll(',',' ')
                    }
                    row.add(linkedSubs.join("; "))
                    row.add(license.licensingConsortium)
                    row.add(license.licensor)
                    row.add(license.startDate ? sdf.format(license.startDate) : '')
                    row.add(license.endDate ? sdf.format(license.endDate) : '')
                    row.addAll(row.addAll(exportService.processPropertyListValues(propertyDefinitions,'csv',license,licChildMap,objectNames)))
                    rows.add(row)
                }
                out.withWriter { writer ->
                    writer.write(exportService.generateSeparatorTableString(titles,rows,','))
                }
                out.close()
            }
            xml {
                def doc = exportService.buildDocXML("Licences")

                response.setHeader("Content-disposition", "attachment; filename=\"${filename}.xml\"")
                response.contentType = "text/xml"
                exportService.streamOutXML(doc, response.outputStream)
            }
        }
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def emptyLicense() {
        Map<String, Object> result = setResultGenerics()

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        if (! accessService.checkUserIsMember(result.user, result.institution)) {
            flash.error = message(code:'myinst.error.noMember', args:[result.institution.name]);
            response.sendError(401)
            return;
        }

        def cal = new java.util.GregorianCalendar()
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()

        cal.setTimeInMillis(System.currentTimeMillis())
        cal.set(Calendar.MONTH, Calendar.JANUARY)
        cal.set(Calendar.DAY_OF_MONTH, 1)

        result.defaultStartYear = sdf.format(cal.getTime())

        cal.set(Calendar.MONTH, Calendar.DECEMBER)
        cal.set(Calendar.DAY_OF_MONTH, 31)

        result.defaultEndYear = sdf.format(cal.getTime())

        result.is_inst_admin = accessService.checkMinUserOrgRole(result.user, result.institution, 'INST_EDITOR')

        result.licenses = [] // ERMS-2431
        result.numLicenses = 0

        if (params.sub) {
            result.sub         = params.sub
            result.subInstance = Subscription.get(params.sub)
        }

        result
    }

    @DebugAnnotation(test='hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def currentProviders() {
        Map<String, Object> result = setResultGenerics()
		ProfilerUtils pu = new ProfilerUtils()
		pu.setBenchmark('init')

        EhcacheWrapper cache = contextService.getCache('MyInstitutionController/currentProviders', contextService.ORG_SCOPE)
        List orgIds = []

        if (cache.get('orgIds')) {
            orgIds = cache.get('orgIds')
            log.debug('orgIds from cache')
        }
        else {

            List<Org> matches = Org.executeQuery("""
select distinct(or_pa.org) from OrgRole or_pa 
join or_pa.sub sub 
join sub.orgRelations or_sub where
    ( sub = or_sub.sub and or_sub.org = :subOrg ) and
    ( or_sub.roleType in (:subRoleTypes) ) and
        ( or_pa.roleType in (:paRoleTypes) )
""", [
        subOrg:       contextService.getOrg(),
        subRoleTypes: [RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIPTION_CONSORTIA],
        paRoleTypes:  [RDStore.OR_PROVIDER, RDStore.OR_AGENCY]
    ])
            orgIds = matches.collect{ it.id }

            // TODO: merge master into dev
            // TODO: orgIds = orgTypeService.getCurrentOrgIdsOfProvidersAndAgencies( contextService.getOrg() )

            cache.put('orgIds', orgIds)
        }

        result.orgRoles    = [RDStore.OR_PROVIDER, RDStore.OR_AGENCY]
        result.propList    = PropertyDefinition.findAllPublicAndPrivateOrgProp(contextService.getOrg())

        params.sort = params.sort ?: " LOWER(o.shortname), LOWER(o.name)"
		result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
		result.offset = params.offset ? Integer.parseInt(params.offset) : 0

        params.constraint_orgIds = orgIds
        def fsq  = filterService.getOrgQuery(params)

        result.filterSet = params.filterSet ? true : false
        if (params.filterPropDef) {
            fsq = propertyService.evalFilterQuery(params, fsq.query, 'o', fsq.queryParams)
        }
        List orgListTotal = Org.findAll(fsq.query, fsq.queryParams)
        result.orgListTotal = orgListTotal.size()
        result.orgList = orgListTotal.drop((int) result.offset).take((int) result.max)

        def message = g.message(code: 'export.my.currentProviders')
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        String datetoday = sdf.format(new Date(System.currentTimeMillis()))
        String filename = message+"_${datetoday}"

        result.cachedContent = true

		List bm = pu.stopBenchmark()
		result.benchMark = bm

        if ( params.exportXLS ) {
            try {
                SXSSFWorkbook wb = (SXSSFWorkbook) organisationService.exportOrg(orgListTotal, message, true, "xls")
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


    @DebugAnnotation(test='hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def currentSubscriptions() {
        Map<String, Object> result = setResultGenerics()

		ProfilerUtils pu = new ProfilerUtils()
		//pu.setBenchmark('init')
        result.tableConfig = ['showActions','showLicense']
        result.putAll(subscriptionService.getMySubscriptions(params,result.user,result.institution))

        result.compare = params.compare ?: ''

        // Write the output to a file
        SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
        String datetoday = sdf.format(new Date(System.currentTimeMillis()))
        String filename = "${datetoday}_" + g.message(code: "export.my.currentSubscriptions")

		//List bm = pu.stopBenchmark()
		//result.benchMark = bm

        if ( params.exportXLS ) {

            //if(wb instanceof XSSFWorkbook) file += "x";
            response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            SXSSFWorkbook wb = (SXSSFWorkbook) exportcurrentSubscription(result.allSubscriptions, "xls", result.institution)
            wb.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            wb.dispose()

            return
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
                    writer.write((String) exportcurrentSubscription(result.allSubscriptions,"csv", result.institution))
                }
                out.close()
            }
        }
    }


    private def exportcurrentSubscription(List<Subscription> subscriptions, String format,Org contextOrg) {
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        List titles = ['Name',
                       g.message(code: 'globalUID.label'),
                       g.message(code: 'license.label'),
                       g.message(code: 'subscription.packages.label'),
                       g.message(code: 'consortium.label'),
                       g.message(code: 'default.provider.label'),
                       g.message(code: 'default.agency.label'),
                       g.message(code: 'subscription.startDate.label'),
                       g.message(code: 'subscription.endDate.label'),
                       g.message(code: 'subscription.manualCancellationDate.label'),
                       g.message(code: 'default.identifiers.label'),
                       g.message(code: 'default.status.label'),
                       g.message(code: 'subscription.kind.label'),
                       g.message(code: 'subscription.form.label'),
                       g.message(code: 'subscription.resource.label'),
                       g.message(code: 'subscription.isPublicForApi.label'),
                       g.message(code: 'subscription.hasPerpetualAccess.label')]
        boolean asCons = false
        if(accessService.checkPerm('ORG_CONSORTIUM')) {
            asCons = true
            titles.addAll([g.message(code: 'subscription.memberCount.label'),g.message(code: 'subscription.memberCostItemsCount.label')])
        }
        //Set<PropertyDefinition> propertyDefinitions = PropertyDefinition.findAllPublicAndPrivateProp([PropertyDefinition.SUB_PROP],contextOrg)
        Set<PropertyDefinition> propertyDefinitions = PropertyDefinition.executeQuery("select sp.type from SubscriptionProperty sp where (sp.owner in (:subscriptions) or sp.owner.instanceOf in (:subscriptions)) and sp.tenant = :ctx",[subscriptions:subscriptions,ctx:contextOrg])
        titles.addAll(exportService.loadPropListHeaders(propertyDefinitions))
        Map<Subscription,Set> providers = [:], agencies = [:], identifiers = [:], licenseReferences = [:], subChildMap = [:]
        Map costItemCounts = [:]
        List allProviders = OrgRole.findAllByRoleTypeAndSubIsNotNull(RDStore.OR_PROVIDER)
        List allAgencies = OrgRole.findAllByRoleTypeAndSubIsNotNull(RDStore.OR_AGENCY)
        List allIdentifiers = Identifier.findAllBySubIsNotNull()
        List allLicenses = Links.executeQuery("select li from Links li where li.destinationSubscription in (:subscriptions) and li.linkType = :linkType",[subscriptions:subscriptions, linkType:RDStore.LINKTYPE_LICENSE])
        List allCostItems = CostItem.executeQuery('select count(ci.id),s.instanceOf.id from CostItem ci join ci.sub s where s.instanceOf != null and (ci.costItemStatus != :ciDeleted or ci.costItemStatus = null) and ci.owner = :owner group by s.instanceOf.id',[ciDeleted:RDStore.COST_ITEM_DELETED,owner:contextOrg])
        allProviders.each { OrgRole provider ->
            Set subProviders = providers.get(provider.sub)
            if(!providers.get(provider.sub))
                subProviders = new TreeSet()
            String providerName = provider.org.name ? provider.org.name : ' '
            subProviders.add(providerName)
            providers.put(provider.sub,subProviders)
        }
        allAgencies.each { OrgRole agency ->
            Set subAgencies = agencies.get(agency.sub)
            if(!agencies.get(agency.sub))
                subAgencies = new TreeSet()
            String agencyName = agency.org.name ? agency.org.name : ' '
            subAgencies.add(agencyName)
            agencies.put(agency.sub,subAgencies)
        }
        allIdentifiers.each { Identifier identifier ->
            Set subIdentifiers = identifiers.get(identifier.sub)
            if(!identifiers.get(identifier.sub))
                subIdentifiers = new TreeSet()
            subIdentifiers.add("(${identifier.ns.ns}) ${identifier.value}")
            identifiers.put(identifier.sub,subIdentifiers)
        }
        allCostItems.each { row ->
            costItemCounts.put(row[1],row[0])
        }
        allLicenses.each { Links row ->
            Subscription s = row.destinationSubscription
            License l = row.sourceLicense
            Set subLicenses = licenseReferences.get(s)
            if(!subLicenses)
                subLicenses = new TreeSet()
            subLicenses.add(l.reference)
            licenseReferences.put(s,subLicenses)
        }
        List membershipCounts = Subscription.executeQuery('select count(s.id),s.instanceOf.id from Subscription s where s.instanceOf != null group by s.instanceOf.id')
        Map subscriptionMembers = [:]
        membershipCounts.each { row ->
            subscriptionMembers.put(row[1],row[0])
        }
        List<Subscription> childSubsOfSet = subscriptions.isEmpty() ? [] : Subscription.findAllByInstanceOfInList(subscriptions)
        childSubsOfSet.each { Subscription child ->
            Set<Subscription> children = subChildMap.get(child.instanceOf)
            if(!children)
                children = []
            children << child
            subChildMap.put(child.instanceOf,children)
        }
        Map objectNames = [:]
        if(childSubsOfSet) {
            Set rows = OrgRole.executeQuery('select oo.sub,oo.org.sortname from OrgRole oo where oo.sub in (:subChildren) and oo.roleType in (:subscrTypes)',[subChildren:childSubsOfSet,subscrTypes:[RDStore.OR_SUBSCRIBER_CONS,RDStore.OR_SUBSCRIBER_CONS_HIDDEN]])
            rows.each { row ->
                log.debug("now processing ${row[0]}:${row[1]}")
                objectNames.put(row[0],row[1])
            }
        }
        List subscriptionData = []
        subscriptions.each { Subscription sub ->
            List row = []
            switch (format) {
                case "xls":
                case "xlsx":
                    row.add([field: sub.name ?: "", style: 'bold'])
                    row.add([field: sub.globalUID, style: null])
                    row.add([field: licenseReferences.get(sub) ? licenseReferences.get(sub).join(", ") : '', style: null])
                    List packageNames = sub.packages?.collect {
                        it.pkg.name
                    }
                    row.add([field: packageNames ? packageNames.join(", ") : '', style: null])
                    row.add([field: sub.getConsortia()?.name ?: '', style: null])
                    row.add([field: providers.get(sub) ? providers.get(sub).join(", ") : '', style: null])
                    row.add([field: agencies.get(sub) ? agencies.get(sub).join(", ") : '', style: null])
                    row.add([field: sub.startDate ? sdf.format(sub.startDate) : '', style: null])
                    row.add([field: sub.endDate ? sdf.format(sub.endDate) : '', style: null])
                    row.add([field: sub.manualCancellationDate ? sdf.format(sub.manualCancellationDate) : '', style: null])
                    row.add([field: identifiers.get(sub) ? identifiers.get(sub).join(", ") : '',style: null])
                    row.add([field: sub.status?.getI10n("value"), style: null])
                    row.add([field: sub.kind?.getI10n("value") ?: '', style: null])
                    row.add([field: sub.form?.getI10n("value") ?: '', style: null])
                    row.add([field: sub.resource?.getI10n("value") ?: '', style: null])
                    row.add([field: sub.isPublicForApi ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"), style: null])
                    row.add([field: sub.hasPerpetualAccess ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"), style: null])
                    if(asCons) {
                        row.add([field: subscriptionMembers.get(sub.id) ?: 0, style: null])
                        row.add([field: costItemCounts.get(sub.id) ?: 0, style: null])
                    }
                    row.addAll(exportService.processPropertyListValues(propertyDefinitions,format,sub,subChildMap,objectNames))
                    subscriptionData.add(row)
                    break
                case "csv":
                    row.add(sub.name ? sub.name.replaceAll(',',' ') : "")
                    row.add(sub.globalUID)
                    row.add(licenseReferences.get(sub) ? licenseReferences.get(sub).join("; ") : '')
                    List packageNames = sub.packages?.collect {
                        it.pkg.name
                    }
                    row.add(packageNames ? packageNames.join("; ") : '')
                    row.add(sub.getConsortia()?.name ?: '')
                    row.add(providers.get(sub) ? providers.get(sub).join("; ").replace(',','') : '')
                    row.add(agencies.get(sub) ? agencies.get(sub).join("; ").replace(',','') : '')
                    row.add(sub.startDate ? sdf.format(sub.startDate) : '')
                    row.add(sub.endDate ? sdf.format(sub.endDate) : '')
                    row.add(sub.manualCancellationDate ? sdf.format(sub.manualCancellationDate) : '')
                    row.add(identifiers.get(sub) ? identifiers.get(sub).join("; ") : '')
                    row.add(sub.status?.getI10n("value"))
                    row.add(sub.kind?.getI10n("value"))
                    row.add(sub.form?.getI10n("value"))
                    row.add(sub.resource?.getI10n("value"))
                    row.add(sub.isPublicForApi ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"))
                    row.add(sub.hasPerpetualAccess ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"))
                    if(asCons) {
                        row.add(subscriptionMembers.get(sub.id) ? (int) subscriptionMembers.get(sub.id) : 0)
                        row.add(costItemCounts.get(sub.id) ? (int) costItemCounts.get(sub.id) : 0)
                    }
                    row.addAll(exportService.processPropertyListValues(propertyDefinitions,format,sub,subChildMap,objectNames))
                    subscriptionData.add(row)
                    break
            }
        }
        switch(format) {
            case 'xls':
            case 'xlsx':
                Map sheetData = [:]
                sheetData[message(code: 'menu.my.subscriptions')] = [titleRow: titles, columnData: subscriptionData]
                return exportService.generateXLSXWorkbook(sheetData)
            case 'csv': return exportService.generateSeparatorTableString(titles, subscriptionData, ',')
        }
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def emptySubscription() {
        Map<String, Object> result = setResultGenerics()

        if (result.editable) {
            def cal = new java.util.GregorianCalendar()
            SimpleDateFormat sdf = DateUtil.getSDF_NoTime()

            cal.setTimeInMillis(System.currentTimeMillis())
            cal.set(Calendar.MONTH, Calendar.JANUARY)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            result.defaultStartYear = sdf.format(cal.getTime())
            cal.set(Calendar.MONTH, Calendar.DECEMBER)
            cal.set(Calendar.DAY_OF_MONTH, 31)
            result.defaultEndYear = sdf.format(cal.getTime())

            if(accessService.checkPerm("ORG_CONSORTIUM")) {
                if(accessService.checkPerm("ORG_CONSORTIUM")) {
                    params.comboType = RDStore.COMBO_TYPE_CONSORTIUM.value
                    result.consortialView = true
                }
                else if(accessService.checkPerm("ORG_INST_COLLECTIVE")) {
                    params.comboType = RDStore.COMBO_TYPE_DEPARTMENT.value
                    result.departmentalView = true
                }
                def fsq = filterService.getOrgComboQuery(params, result.institution)
                result.members = Org.executeQuery(fsq.query, fsq.queryParams, params)
            }

            result
        } else {
            flash.message = "${message(code: 'default.notAutorized.message')}"
            redirect action: 'currentSubscriptions'
        }
    }

    @DebugAnnotation(test='hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def processEmptySubscription() {
        log.debug( params.toMapString() )
        Map<String, Object> result = setResultGenerics()

        RefdataValue role_sub = RDStore.OR_SUBSCRIBER
        RefdataValue role_sub_cons = RDStore.OR_SUBSCRIBER_CONS
        RefdataValue role_sub_cons_hidden = RDStore.OR_SUBSCRIBER_CONS_HIDDEN
        RefdataValue role_sub_coll = RDStore.OR_SUBSCRIBER_COLLECTIVE
        RefdataValue role_cons = RDStore.OR_SUBSCRIPTION_CONSORTIA
        RefdataValue role_coll = RDStore.OR_SUBSCRIPTION_COLLECTIVE

        RefdataValue orgRole
        RefdataValue memberRole
        RefdataValue subType = RefdataValue.get(params.type)

        switch(subType) {
            case RDStore.SUBSCRIPTION_TYPE_CONSORTIAL:
            case RDStore.SUBSCRIPTION_TYPE_ADMINISTRATIVE:
				orgRole = role_cons
                memberRole = role_sub_cons
                break
            default:
                if (result.institution.getCustomerType() == 'ORG_INST_COLLECTIVE') {
                    orgRole = role_coll
                    memberRole = role_sub_coll
                }
                else {
                    orgRole = role_sub
                    if (! subType)
                        subType = RDStore.SUBSCRIPTION_TYPE_LOCAL
                }
                break
        }

        if (accessService.checkMinUserOrgRole(result.user, result.institution, 'INST_EDITOR')) {

            SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
            Date startDate = params.valid_from ? sdf.parse(params.valid_from) : null
            Date endDate = params.valid_to ? sdf.parse(params.valid_to) : null
            RefdataValue status = RefdataValue.get(params.status)

            //beware: at this place, we cannot calculate the subscription type because essential data for the calculation is not persisted/available yet!
            boolean administrative = false
            if(subType == RDStore.SUBSCRIPTION_TYPE_ADMINISTRATIVE)
                administrative = true

            def new_sub = new Subscription(
                    type: subType,
                    kind: (subType == RDStore.SUBSCRIPTION_TYPE_CONSORTIAL) ? RDStore.SUBSCRIPTION_KIND_CONSORTIAL : null,
                    name: params.newEmptySubName,
                    startDate: startDate,
                    endDate: endDate,
                    status: status,
                    administrative: administrative,
                    identifier: java.util.UUID.randomUUID().toString()
            )

            if (new_sub.save(flush:true)) {
                new OrgRole(org: result.institution, sub: new_sub, roleType: orgRole).save(flush:true)
                        
                if (accessService.checkPerm('ORG_INST_COLLECTIVE') ||
                        (accessService.checkPerm('ORG_CONSORTIUM') && subType != RDStore.SUBSCRIPTION_TYPE_LOCAL)
                ){
                    List<Org> cons_members = []

                    params.list('selectedOrgs').each{ it ->
                        Org fo =  Org.findById(Long.valueOf(it))
                        cons_members << Combo.executeQuery(
                                "select c.fromOrg from Combo as c where c.toOrg = :toOrg and c.fromOrg = :fromOrg",
                                [toOrg: result.institution, fromOrg:fo] )
                    }

                    //def cons_members = Combo.executeQuery("select c.fromOrg from Combo as c where c.toOrg = ?", [result.institution])

                    cons_members.each { cm ->

                    if (params.generateSlavedSubs == "Y") {
                        log.debug("Generating seperate slaved instances for consortia members")
                        String postfix = cm.get(0).shortname ?: cm.get(0).name

                        Subscription cons_sub = new Subscription(
                                          type: subType,
                                          kind: (subType == RDStore.SUBSCRIPTION_TYPE_CONSORTIAL) ? RDStore.SUBSCRIPTION_KIND_CONSORTIAL : null,
                                          name: params.newEmptySubName,
                                          // name: params.newEmptySubName + " (${postfix})",
                                          startDate: startDate,
                                          endDate: endDate,
                                          identifier: java.util.UUID.randomUUID().toString(),
                                          status: status,
                                          administrative: administrative,
                                          instanceOf: new_sub,
                                          isSlaved: true)

                        if (new_sub.administrative) {
                            new OrgRole(org: cm, sub: cons_sub, roleType: role_sub_cons_hidden).save(flush:true)
                        }
                        else {
                            new OrgRole(org: cm, sub: cons_sub, roleType: memberRole).save(flush:true)
                        }

                        new OrgRole(org: result.institution, sub: cons_sub, roleType: orgRole).save(flush:true)
                    }
                    else {
                        if(new_sub.administrative) {
                            new OrgRole(org: cm, sub: new_sub, roleType: role_sub_cons_hidden).save(flush:true)
                        }
                        else {
                            new OrgRole(org: cm, sub: new_sub, roleType: memberRole).save(flush:true)
                        }
                    }
                  }
                }

                redirect controller: 'subscription', action: 'show', id: new_sub.id
            } else {
                new_sub.errors.each { e ->
                    log.debug("Problem creating new sub: ${e}");
                }
                flash.error = new_sub.errors
                redirect action: 'emptySubscription'
            }
        } else {
            redirect action: 'currentSubscriptions'
        }
    }

    @DebugAnnotation(test='hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def processEmptyLicense() {
        User user = User.get(springSecurityService.principal.id)
        Org org = contextService.getOrg()

        Set<RefdataValue> defaultOrgRoleType = []
        if(accessService.checkPerm("ORG_CONSORTIUM"))
            defaultOrgRoleType << RDStore.OT_CONSORTIUM.id.toString()
        else defaultOrgRoleType << RDStore.OT_INSTITUTION.id.toString()

        params.asOrgType = params.asOrgType ? [params.asOrgType] : defaultOrgRoleType


        if (! accessService.checkMinUserOrgRole(user, org, 'INST_EDITOR')) {
            flash.error = message(code:'myinst.error.noAdmin', args:[org.name])
            response.sendError(401)
            // render(status: '401', text:"You do not have permission to access ${org.name}. Please request access on the profile page");
            return
        }

        def baseLicense = params.baselicense ? License.get(params.baselicense) : null
        //Nur wenn von Vorlage ist
        if (baseLicense) {
            if (!baseLicense?.hasPerm("view", user)) {
                log.debug("return 401....")
                flash.error = message(code: 'myinst.newLicense.error')
                response.sendError(401)
            }
            else {
                def copyLicense = institutionsService.copyLicense(
                        baseLicense, params, InstitutionsService.CUSTOM_PROPERTIES_COPY_HARD)

                if (copyLicense.hasErrors()) {
                    log.error("Problem saving license ${copyLicense.errors}")
                    render view: 'editLicense', model: [licenseInstance: copyLicense]
                } else {
                    copyLicense.reference = params.licenseName
                    copyLicense.startDate = DateUtil.parseDateGeneric(params.licenseStartDate)
                    copyLicense.endDate = DateUtil.parseDateGeneric(params.licenseEndDate)

                    if (copyLicense.save(flush:true)) {
                        flash.message = message(code: 'license.createdfromTemplate.message')
                    }

                    if( params.sub) {
                        Subscription subInstance = Subscription.get(params.sub)
                        subscriptionService.setOrgLicRole(subInstance,copyLicense,false)
                        //subInstance.owner = copyLicense
                        //subInstance.save(flush: true)
                    }

                    redirect controller: 'license', action: 'show', params: params, id: copyLicense.id
                    return
                }
            }
        }

        RefdataValue license_type = RDStore.LICENSE_TYPE_ACTUAL

        License licenseInstance = new License(type: license_type, reference: params.licenseName,
                startDate:params.licenseStartDate ? DateUtil.parseDateGeneric(params.licenseStartDate) : null,
                endDate: params.licenseEndDate ? DateUtil.parseDateGeneric(params.licenseEndDate) : null,
                status: RefdataValue.get(params.status),
                openEnded: RDStore.YNU_UNKNOWN
        )

        if (!licenseInstance.save(flush:true)) {
            log.error(licenseInstance.errors.toString())
            flash.error = message(code:'license.create.error')
            redirect action: 'emptyLicense'
        }
        else {
            log.debug("Save ok")
            RefdataValue licensee_role = RDStore.OR_LICENSEE
            RefdataValue lic_cons_role = RDStore.OR_LICENSING_CONSORTIUM

            log.debug("adding org link to new license")


            OrgRole orgRole
            if (params.asOrgType && (RDStore.OT_CONSORTIUM.id.toString() in params.asOrgType)) {
                orgRole = new OrgRole(lic: licenseInstance,org:org,roleType: lic_cons_role)
            } else {
                orgRole = new OrgRole(lic: licenseInstance,org:org,roleType: licensee_role)
            }

            if (!orgRole.save(flush:true)) {
                log.error("Problem saving org links to license ${orgRole.errors}");
            }

            redirect controller: 'license', action: 'show', params: params, id: licenseInstance.id
        }
    }

    /**
     * connects the context subscription with the given pair.
     *
     * @return void, redirects to main page
     */
    @DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def linkObjects() {
        //error when no pair is given!
        params.keySet().each {
            if(it.contains("pair_")) {
                def pairCheck = params.get(it)
                if(!pairCheck) {
                    flash.error = message(code:'default.linking.noLinkError')
                    redirect(url: request.getHeader('referer'))
                    return
                }
            }
        }
        //distinct between insert and update - if a link id exists, then proceed with edit, else create new instance
        Map<String,Object> configMap = [owner:contextService.org]
        //perspectiveIndex 0: source -> dest, 1: dest -> source
        if(params.link) {
            configMap.link = genericOIDService.resolveOID(params.link)
            if(params.commentID)
                configMap.comment = genericOIDService.resolveOID(params.commentID)
            if(params["linkType_${configMap.link.id}"]) {
                String linkTypeString = params["linkType_${configMap.link.id}"].split("§")[0]
                int perspectiveIndex = Integer.parseInt(params["linkType_${configMap.link.id}"].split("§")[1])
                RefdataValue linkType = (RefdataValue) genericOIDService.resolveOID(linkTypeString)
                configMap.commentContent = params["linkComment_${configMap.link.id}"].trim()
                if(perspectiveIndex == 0) {
                    configMap.source = genericOIDService.resolveOID(params.context)
                    configMap.destination = genericOIDService.resolveOID(params["pair_${configMap.link.id}"])
                }
                else if(perspectiveIndex == 1) {
                    configMap.source = genericOIDService.resolveOID(params["pair_${configMap.link.id}"])
                    configMap.destination = genericOIDService.resolveOID(params.context)
                }
                configMap.linkType = linkType
            }
            else if(!params["linkType_${configMap.link.id}"]) {
                flash.error = message(code:'default.linking.linkTypeError')
            }
        }
        else {
            if(params["linkType_new"]) {
                String linkTypeString = params["linkType_new"].split("§")[0]
                int perspectiveIndex = Integer.parseInt(params["linkType_new"].split("§")[1])
                configMap.linkType = genericOIDService.resolveOID(linkTypeString)
                configMap.commentContent = params.linkComment_new
                if(perspectiveIndex == 0) {
                    configMap.source = genericOIDService.resolveOID(params.context)
                    configMap.destination = genericOIDService.resolveOID(params.pair_new)
                }
                else if(perspectiveIndex == 1) {
                    configMap.source = genericOIDService.resolveOID(params.pair_new)
                    configMap.destination = genericOIDService.resolveOID(params.context)
                }
                def currentObject = genericOIDService.resolveOID(params.context)
                List childInstances = currentObject.getClass().findAllByInstanceOf(currentObject)
                if(childInstances) {
                    configMap.contextInstances = childInstances
                    def pairObject = genericOIDService.resolveOID(params.pair_new)
                    configMap.pairInstances = pairObject.getClass().findAllByInstanceOf(pairObject)
                }
            }
            else if(params["linkType_sl_new"]) {
                configMap.linkType = RDStore.LINKTYPE_LICENSE
                configMap.commentContent = params.linkComment_sl_new
                configMap.source = genericOIDService.resolveOID(params.pair_sl_new)
                configMap.destination = genericOIDService.resolveOID(params.context)
            }
            else if(!params["linkType_new"] && !params["linkType_sl_new"]) {
                flash.error = message(code:'default.linking.linkTypeError')
            }
        }
        def error = linksGenerationService.createOrUpdateLink(configMap)
        if(error != false)
            flash.error = error
        redirect(url: request.getHeader('referer'))
    }

    @DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def unlinkObjects() {
        linksGenerationService.deleteLink(params.oid)
        redirect(url: request.getHeader('referer'))
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_USER")
    })
    Map documents() {
        Map<String, Object> result = setResultGenerics()
        result
    }

    @DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def deleteDocuments() {
        def ctxlist = []

        log.debug("deleteDocuments ${params}");

        docstoreService.unifiedDeleteDocuments(params)

        redirect controller: 'myInstitution', action: 'documents' /*, fragment: 'docstab' */
    }

    @DebugAnnotation(test='hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def currentTitles() {

        Map<String,Object> result = setResultGenerics()
		ProfilerUtils pu = new ProfilerUtils()
		pu.setBenchmark('init')

        Set<RefdataValue> orgRoles = []

        List<String> queryFilter = []

        if(accessService.checkPerm("ORG_CONSORTIUM")) {
            orgRoles << RDStore.OR_SUBSCRIPTION_CONSORTIA
        }
        else {
            orgRoles << RDStore.OR_SUBSCRIBER
            orgRoles << RDStore.OR_SUBSCRIBER_CONS
        }

        // Set Date Restriction
        Date checkedDate = null

        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        boolean defaultSet = false
        if (params.validOn == null) {
            result.validOn = sdf.format(new Date(System.currentTimeMillis()))
            checkedDate = sdf.parse(result.validOn)
            defaultSet = true
            log.debug("Getting titles as of ${checkedDate} (current)")
        } else if (params.validOn.trim() == '') {
            result.validOn = ""
        } else {
            result.validOn = params.validOn
            checkedDate = sdf.parse(params.validOn)
            log.debug("Getting titles as of ${checkedDate} (given)")
        }

        // Set offset and max
        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0

        List filterSub = params.list("filterSub")
        if (filterSub == "all")
            filterSub = null
        List filterPvd = params.list("filterPvd")
        if (filterPvd == "all")
            filterPvd = null
        List filterHostPlat = params.list("filterHostPlat")
        if (filterHostPlat == "all")
            filterHostPlat = null
        log.debug("Using params: ${params}")

        Map<String,Object> qryParams = [
                institution: result.institution,
                deleted: RDStore.TIPP_STATUS_DELETED,
                current: RDStore.SUBSCRIPTION_CURRENT,
                orgRoles: orgRoles
        ]

        if(checkedDate) {
            queryFilter << ' ( :checkedDate >= coalesce(ie.accessStartDate,sub.startDate,tipp.accessStartDate) or (ie.accessStartDate is null and sub.startDate is null and tipp.accessStartDate is null) ) and ( :checkedDate <= coalesce(ie.accessEndDate,sub.endDate,tipp.accessEndDate) or (ie.accessEndDate is null and sub.endDate is null and tipp.accessEndDate is null)  or (sub.hasPerpetualAccess = true))'
            /*queryFilter << ' (ie.accessStartDate <= :checkedDate or ' +
                              '(ie.accessStartDate is null and ' +
                                '(sub.startDate <= :checkedDate or ' +
                                  '(sub.startDate is null and ' +
                                    '(tipp.accessStartDate <= :checkedDate or tipp.accessStartDate is null)' +
                                  ')' +
                                ')' +
                              ')' +
                            ') and ' +
                            '(ie.accessEndDate >= :checkedDate or ' +
                              '(ie.accessEndDate > :checkedDate and sub.hasPerpetualAccess = true) or ' +
                                '(ie.accessEndDate is null and ' +
                                  '(sub.endDate >= :checkedDate or ' +
                                    '(sub.endDate > :checkedDate and sub.hasPerpetualAccess = true) or ' +
                                      '(sub.endDate is null and ' +
                                        '(tipp.accessEndDate >= :checkedDate or ' +
                                          '(tipp.accessEndDate > :checkedDate and sub.hasPerpetualAccess = true) or ' +
                                        'tipp.accessEndDate is null)' +
                                      ')' +
                                  ')' +
                                ')' +
                            ')'*/
            qryParams.checkedDate = checkedDate
        }

        if ((params.filter) && (params.filter.length() > 0)) {
            log.debug("Adding title filter ${params.filter}");
            queryFilter << "genfunc_filter_matcher(ti.title, :titlestr) = true "
            qryParams.titlestr = params.get('filter').toString()
        }

        if (filterSub) {
            queryFilter << "sub in (" + filterSub.join(", ") + ")"
        }

        if (filterHostPlat) {
            queryFilter << "tipp.platform in (" + filterHostPlat.join(", ") + ")"
        }

        if (filterPvd) {
            qryParams.cprole = RDStore.OR_CONTENT_PROVIDER
            queryFilter << "oo.roleType in :cpRole and oo.org IN (" + filterPvd.join(", ") + ")"
        }

        //String havingClause = params.filterMultiIE ? 'having count(ie.ie_id) > 1' : ''

        String orderByClause = ''
        if (params.order == 'desc') {
            orderByClause = 'order by ti.sortTitle desc'
        } else {
            orderByClause = 'order by ti.sortTitle asc'
        }

        String qryString = "select ie from IssueEntitlement ie join ie.tipp tipp join tipp.title ti join ie.subscription sub join sub.orgRelations oo where ie.status != :deleted and sub.status = :current and oo.roleType in (:orgRoles) and oo.org = :institution "
        if(queryFilter)
            qryString += ' and '+queryFilter.join(' and ')
        qryString += orderByClause

        //all ideas to move the .unique() into a group by clause are greately appreciated, a half day's attempts were unsuccessful!
        Set<IssueEntitlement> currentIssueEntitlements = IssueEntitlement.executeQuery(qryString,qryParams).unique { ie -> ie.tipp.title }
        Set<TitleInstance> allTitles = currentIssueEntitlements.collect { IssueEntitlement ie -> ie.tipp.title }
        result.num_ti_rows = allTitles.size()
        result.titles = allTitles.drop(result.offset).take(result.max)

        result.filterSet = params.filterSet || defaultSet
        String filename = "${message(code:'export.my.currentTitles')}_${DateUtil.SDF_NoTimeNoPoint.format(new Date())}"

		List bm = pu.stopBenchmark()
		result.benchMark = bm

        if(params.exportKBart) {
            response.setHeader("Content-disposition", "attachment; filename=${filename}.tsv")
            response.contentType = "text/tsv"
            ServletOutputStream out = response.outputStream
            Map<String,List> tableData = exportService.generateTitleExportKBART(currentIssueEntitlements)
            out.withWriter { writer ->
                writer.write(exportService.generateSeparatorTableString(tableData.titleRow,tableData.columnData,'\t'))
            }
            out.flush()
            out.close()
        }
        else if(params.exportXLSX) {
            response.setHeader("Content-disposition", "attachment; filename=\"${filename}.xlsx\"")
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            Map<String,List> export = exportService.generateTitleExportXLS(currentIssueEntitlements)
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
                    response.setHeader("Content-disposition", "attachment; filename=${filename}.csv")
                    response.contentType = "text/csv"

                    ServletOutputStream out = response.outputStream
                    Map<String,List> tableData = exportService.generateTitleExportCSV(currentIssueEntitlements)
                    out.withWriter { writer ->
                        writer.write(exportService.generateSeparatorTableString(tableData.titleRow,tableData.columnData,';'))
                    }
                    out.flush()
                    out.close()
                }
                /*json {
                    def map = [:]
                    exportService.addTitlesToMap(map, result.titles)
                    def content = map as JSON

                    response.setHeader("Content-disposition", "attachment; filename=\"${filename}.json\"")
                    response.contentType = "application/json"

                    render content
                }
                xml {
                    def doc = exportService.buildDocXML("TitleList")
                    exportService.addTitleListXML(doc, doc.getDocumentElement(), result.titles)

                    response.setHeader("Content-disposition", "attachment; filename=\"${filename}.xml\"")
                    response.contentType = "text/xml"
                    exportService.streamOutXML(doc, response.outputStream)
                }
                */
            }
        }
    }

    @DebugAnnotation(test='hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def currentPackages() {

        Map<String, Object> result = [:]
        result.user = User.get(springSecurityService.principal.id)
        result.max = params.max ?: result.user.getDefaultPageSize()
        result.offset = params.offset ?: 0
        result.contextOrg = contextService.org

        //def cache = contextService.getCache('MyInstitutionController/currentPackages/', contextService.ORG_SCOPE)

        List currentSubIds = []
        List idsCategory1  = []
        List idsCategory2  = []

        if (! params.status) {
            if (params.isSiteReloaded != "yes") {
                params.status = RDStore.SUBSCRIPTION_CURRENT.id
                result.defaultSet = true
            }
            else {
                params.status = 'FETCH_ALL'
            }
        }

        def tmpQ = subscriptionsQueryService.myInstitutionCurrentSubscriptionsBaseQuery(params, contextService.org)
        result.filterSet = tmpQ[2]
        currentSubIds = Subscription.executeQuery( "select s.id " + tmpQ[0], tmpQ[1] ) //,[max: result.max, offset: result.offset]

        idsCategory1 = OrgRole.executeQuery("select distinct (sub.id) from OrgRole where org=:org and roleType in (:roleTypes)", [
                org: contextService.getOrg(), roleTypes: [
                RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIBER_COLLECTIVE
        ]
        ])
        idsCategory2 = OrgRole.executeQuery("select distinct (sub.id) from OrgRole where org=:org and roleType in (:roleTypes)", [
                org: contextService.getOrg(), roleTypes: [
                RDStore.OR_SUBSCRIPTION_CONSORTIA, RDStore.OR_SUBSCRIPTION_COLLECTIVE
        ]
        ])

        result.subscriptionMap = [:]

        if(currentSubIds) {

            String qry3 = "select distinct pkg, s from SubscriptionPackage subPkg join subPkg.subscription s join subPkg.pkg pkg, " +
                    "TitleInstancePackagePlatform tipp " +
                    "where tipp.pkg = pkg and s.id in (:currentSubIds) "

            qry3 += " and ((pkg.packageStatus is null) or (pkg.packageStatus != :pkgDeleted))"
            qry3 += " and ((tipp.status is null) or (tipp.status != :tippDeleted))"

            def qryParams3 = [
                    currentSubIds  : currentSubIds,
                    pkgDeleted     : RDStore.PACKAGE_STATUS_DELETED,
                    tippDeleted    : RDStore.TIPP_STATUS_DELETED
            ]

            if (params.pkg_q?.length() > 0) {
                qry3 += "and ("
                qry3 += "   genfunc_filter_matcher(pkg.name, :query) = true"
                qry3 += ")"
                qryParams3.put('query', "${params.pkg_q}")
            }

            qry3 += " group by pkg, s"
            qry3 += " order by pkg.name " + (params.order ?: 'asc')

            List packageSubscriptionList = Subscription.executeQuery(qry3, qryParams3)
            /*, [max:result.max, offset:result.offset])) */

            packageSubscriptionList.each { entry ->
                String key = 'package_' + entry[0].id

                if (! result.subscriptionMap.containsKey(key)) {
                    result.subscriptionMap.put(key, [])
                }
                if (entry[1].status?.value == RDStore.SUBSCRIPTION_CURRENT.value) {

                    if (idsCategory1.contains(entry[1].id)) {
                        result.subscriptionMap.get(key).add(entry[1])
                    }
                    else if (idsCategory2.contains(entry[1].id) && entry[1].instanceOf == null) {
                        result.subscriptionMap.get(key).add(entry[1])
                    }
                }
            }

            result.packageList = (packageSubscriptionList.collect { it[0] }).unique()
        }
        else {
            result.packageList = []
        }
        result.packagesTotal    = result.packageList.size()

        result

    }

    def resolveOID(oid_components) {
        GrailsClass domain_class = AppUtils.getDomainClassGeneric( oid_components[0] )
        if (domain_class) {
            return domain_class.getClazz().get(oid_components[1])
        }
        null
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def dashboard() {
        Map<String, Object> result = setResultGenerics()

        if (! accessService.checkUserIsMember(result.user, result.institution)) {
            flash.error = "You do not have permission to access ${result.institution.name} pages. Please request access on the profile page";
            response.sendError(401)
            return;
        }

        result.is_inst_admin = accessService.checkMinUserOrgRole(result.user, result.institution, 'INST_ADM')
        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0
        result.pendingOffset = 0
        result.acceptedOffset = 0
        result.dashboardDueDatesOffset = 0
        switch(params.view) {
            case 'PendingChanges': result.pendingOffset = result.offset
            break
            case 'AcceptedChanges': result.acceptedOffset = result.offset
            break
            case 'dueDatesView': result.dashboardDueDatesOffset = result.offset
            break
        }

        def periodInDays = contextService.getUser().getSettingsValue(UserSetting.KEYS.DASHBOARD_ITEMS_TIME_WINDOW, 14)

        // changes

        Map<String,Object> pendingChangeConfigMap = [contextOrg:result.institution,consortialView:accessService.checkPerm(result.institution,"ORG_CONSORTIUM"),periodInDays:periodInDays,max:result.max,pendingOffset:result.pendingOffset,acceptedOffset:result.acceptedOffset,pending:true,notifications:true]

        result.putAll(pendingChangeService.getChanges(pendingChangeConfigMap))

        // systemAnnouncements

        result.systemAnnouncements = SystemAnnouncement.getPublished(periodInDays)

        // tasks

        SimpleDateFormat sdFormat    = DateUtil.getSDF_NoTime()
        params.taskStatus = 'not done'
        def query       = filterService.getTaskQuery(params << [sort: 't.endDate', order: 'asc'], sdFormat)
        Org contextOrg  = contextService.getOrg()
        result.tasks    = taskService.getTasksByResponsibles(springSecurityService.getCurrentUser(), contextOrg, query)
        result.tasksCount    = result.tasks.size()
        result.enableMyInstFormFields = true // enable special form fields


        /*def announcement_type = RefdataValue.getByValueAndCategory('Announcement', RDConstants.DOCUMENT_TYPE)
        result.recentAnnouncements = Doc.findAllByType(announcement_type, [max: result.max,offset:result.announcementOffset, sort: 'dateCreated', order: 'desc'])
        result.recentAnnouncementsCount = Doc.findAllByType(announcement_type).size()*/

        result.dueDates = dashboardDueDatesService.getDashboardDueDates( contextService.user, contextService.org, false, false, result.max, result.dashboardDueDatesOffset)
        result.dueDatesCount = dashboardDueDatesService.getDashboardDueDates(contextService.user, contextService.org, false, false).size()

        List activeSurveyConfigs = SurveyConfig.executeQuery("from SurveyConfig surConfig where exists (select surOrg from SurveyOrg surOrg where surOrg.surveyConfig = surConfig AND surOrg.org = :org and surOrg.finishDate is null and surConfig.pickAndChoose = true and surConfig.surveyInfo.status = :status) " +
                " or exists (select surResult from SurveyResult surResult where surResult.surveyConfig = surConfig and surConfig.surveyInfo.status = :status and surResult.finishDate is null and surResult.participant = :org) " +
                " order by surConfig.surveyInfo.endDate",
                [org: result.institution,
                 status: RDStore.SURVEY_SURVEY_STARTED])

        if(accessService.checkPerm('ORG_CONSORTIUM')){
            activeSurveyConfigs = SurveyConfig.executeQuery("from SurveyConfig surConfig where surConfig.surveyInfo.status = :status  and surConfig.surveyInfo.owner = :org " +
                    " order by surConfig.surveyInfo.endDate",
                    [org: result.institution,
                     status: RDStore.SURVEY_SURVEY_STARTED])
        }

        result.surveys = activeSurveyConfigs.groupBy {it?.id}
        result.countSurvey = result.surveys.size()

        result
    }
    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def modal_create() {
        Map<String, Object> result = setResultGenerics()

        if (! accessService.checkUserIsMember(result.user, result.institution)) {
            flash.error = "You do not have permission to access ${result.institution.name} pages. Please request access on the profile page";
            response.sendError(401)
            return;
        }

        def preCon      = taskService.getPreconditions(result.institution)
        result << preCon

        render template: '/templates/tasks/modal_create', model: result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def changes() {
        Map<String, Object> result = setResultGenerics()

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0

        Map<String,Object> pendingChangeConfigMap = [contextOrg:result.institution,consortialView:accessService.checkPerm(result.institution,"ORG_CONSORTIUM"),max:result.max,pendingOffset:result.offset,pending:true,notifications:false]

        result.putAll(pendingChangeService.getChanges(pendingChangeConfigMap))

        result
    }

    //@DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    //@Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    @Secured(['ROLE_ADMIN'])
    def announcements() {
        Map<String, Object> result = setResultGenerics()

        result.itemsTimeWindow = 365
        result.recentAnnouncements = Doc.executeQuery(
                'select d from Doc d where d.type = :type and d.dateCreated >= :tsCheck order by d.dateCreated desc',
                [type: RDStore.DOC_TYPE_ANNOUNCEMENT, tsCheck: (new Date()).minus(365)]
        )
        result.num_announcements = result.recentAnnouncements.size()

        result
    }

    @Secured(['ROLE_YODA'])
    def changeLog() {
        Map<String, Object> result = setResultGenerics()

        def exporting = ( params.format == 'csv' ? true : false )

        result.institutional_objects = []

        if ( exporting ) {
          result.max = 1000000;
          result.offset = 0;
        }
        else {
          result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
          result.offset = params.offset ? Integer.parseInt(params.offset) : 0;
        }

        PendingChange.executeQuery('select distinct(pc.license) from PendingChange as pc where pc.owner = :owner', [owner: result.institution]).each {
          result.institutional_objects.add([License.class.name + ':' + it.id, "${message(code:'license.label')}: " + it.reference])
        }
        PendingChange.executeQuery('select distinct(pc.subscription) from PendingChange as pc where pc.owner = :owner', [owner: result.institution]).each {
          result.institutional_objects.add([Subscription.class.name + ':' + it.id, "${message(code:'subscription')}: " + it.name])
        }

        if ( params.restrict == 'ALL' )
          params.restrict=null

        String base_query = " from PendingChange as pc where owner = ?";
        def qry_params = [result.institution]
        if ( ( params.restrict != null ) && ( params.restrict.trim().length() > 0 ) ) {
          def o =  genericOIDService.resolveOID(params.restrict)
          if ( o != null ) {
            if ( o instanceof License ) {
              base_query += ' and license = ?'
            }
            else {
              base_query += ' and subscription = ?'
            }
            qry_params.add(o)
          }
        }

        result.num_changes = PendingChange.executeQuery("select pc.id "+base_query, qry_params).size()


        withFormat {
            html {
            result.changes = PendingChange.executeQuery("select pc "+base_query+"  order by ts desc", qry_params, [max: result.max, offset:result.offset])
                result
            }
            csv {
                SimpleDateFormat dateFormat = DateUtil.getSDF_NoTime()
                def changes = PendingChange.executeQuery("select pc "+base_query+"  order by ts desc", qry_params)
                response.setHeader("Content-disposition", "attachment; filename=\"${escapeService.escapeString(result.institution.name)}_changes.csv\"")
                response.contentType = "text/csv"

                def out = response.outputStream
                out.withWriter { w ->
                  w.write('Date,ChangeId,Actor, SubscriptionId,LicenseId,Description\n')
                  changes.each { c ->
                    def line = "\"${dateFormat.format(c.ts)}\",\"${c.id}\",\"${c.user?.displayName?:''}\",\"${c.subscription?.id ?:''}\",\"${c.license?.id?:''}\",\"${c.desc}\"\n".toString()
                    w.write(line)
                  }
                }
                out.close()
            }

        }
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def financeImport() {
        Map<String, Object> result = setResultGenerics()
        result.mappingCols = ["title","element","elementSign","referenceCodes","budgetCode","status","invoiceTotal",
                              "currency","exchangeRate","taxType","taxRate","value","subscription","package",
                              "issueEntitlement","datePaid","financialYear","dateFrom","dateTo","invoiceDate",
                              "description","invoiceNumber","orderNumber"/*,"institution"*/]
        result
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def generateFinanceImportWorksheet() {
        Subscription subscription = Subscription.get(params.id)
        Set<String> keys = ["title","element","elementSign","referenceCodes","budgetCode","status","invoiceTotal",
                            "currency","exchangeRate","taxType","taxRate","value","subscription","package",
                            "issueEntitlement","datePaid","financialYear","dateFrom","dateTo","invoiceDate",
                            "description","invoiceNumber","orderNumber"]
        Set<List<String>> identifierRows = []
        Set<String> colHeaders = []
        subscription.derivedSubscriptions.each { subChild ->
            List<String> row = []
            keys.eachWithIndex { String entry, int i ->
                colHeaders << message(code:"myinst.financeImport.${entry}")
                if(entry == "subscription") {
                    row[i] = subChild.globalUID
                }
                else row[i] = ""
            }
            identifierRows << row
        }
        String template = exportService.generateSeparatorTableString(colHeaders,identifierRows,",")
        response.setHeader("Content-disposition", "attachment; filename=\"bulk_upload_template_${escapeService.escapeString(subscription.name)}.csv\"")
        response.contentType = "text/csv"
        ServletOutputStream out = response.outputStream
        out.withWriter { writer ->
            writer.write(template)
        }
        out.close()
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def processFinanceImport() {
        Map<String, Object> result = setResultGenerics()
        CommonsMultipartFile tsvFile = params.tsvFile
        if(tsvFile && tsvFile.size > 0) {
            String encoding = UniversalDetector.detectCharset(tsvFile.getInputStream())
            if(encoding == "UTF-8") {
                result.filename = tsvFile.originalFilename
                Map<String,Map> financialData = financeService.financeImport(tsvFile)
                result.candidates = financialData.candidates
                result.budgetCodes = financialData.budgetCodes
                result.criticalErrors = [/*'ownerMismatchError',*/'noValidSubscription','multipleSubError','packageWithoutSubscription','noValidPackage','multipleSubPkgError',
                                         'packageNotInSubscription','entitlementWithoutPackageOrSubscription','noValidTitle','multipleTitleError','noValidEntitlement','multipleEntitlementError',
                                         'entitlementNotInSubscriptionPackage','multipleOrderError','multipleInvoiceError','invalidCurrencyError','invoiceTotalInvalid','valueInvalid','exchangeRateInvalid',
                                         'invalidTaxType','invalidYearFormat','noValidStatus','noValidElement','noValidSign']
                render view: 'postProcessingFinanceImport', model: result
            }
            else {
                flash.error = message(code:'default.import.error.wrongCharset',args:[encoding])
                redirect(url: request.getHeader('referer'))
            }
        }
        else {
            flash.error = message(code:'default.import.error.noFileProvided')
            redirect(url: request.getHeader('referer'))
        }
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def subscriptionImport() {
        Map<String, Object> result = setResultGenerics()
        result.mappingCols = ["name","owner","status","type","form","resource","provider","agency","startDate","endDate","instanceOf",
                              "manualCancellationDate","member","customProperties","privateProperties","notes"]
        result
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def processSubscriptionImport() {
        Map<String, Object> result = setResultGenerics()
        CommonsMultipartFile tsvFile = params.tsvFile
        if(tsvFile && tsvFile.size > 0) {
            String encoding = UniversalDetector.detectCharset(tsvFile.getInputStream())
            log.debug(Charset.defaultCharset())
            if(encoding == "UTF-8") {
                result.filename = tsvFile.originalFilename
                Map subscriptionData = subscriptionService.subscriptionImport(tsvFile)
                if(subscriptionData.globalErrors) {
                    flash.error = "<h3>${message([code:'myinst.subscriptionImport.post.globalErrors.header'])}</h3><p>${subscriptionData.globalErrors.join('</p><p>')}</p>"
                    redirect(action: 'subscriptionImport')
                }
                result.candidates = subscriptionData.candidates
                result.parentSubType = subscriptionData.parentSubType
                result.criticalErrors = ['multipleOrgsError','noValidOrg','noValidSubscription']
                render view: 'postProcessingSubscriptionImport', model: result
            }
            else {
                flash.error = message(code:'default.import.error.wrongCharset',args:[encoding])
                redirect(url: request.getHeader('referer'))
            }
        }
        else {
            flash.error = message(code:'default.import.error.noFileProvided')
            redirect(url: request.getHeader('referer'))
        }
    }

    @DebugAnnotation(perm="ORG_BASIC_MEMBER", affil="INST_USER", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_BASIC_MEMBER", "INST_USER", "ROLE_ADMIN")
    })
    def currentSurveys() {
        Map<String, Object> result = setResultGenerics()

        //result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        //result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

        params.tab = params.tab ?: 'new'

        if(params.tab != 'new'){
            params.sort = 'surInfo.endDate DESC, LOWER(surInfo.name)'
        }

        /*if (params.validOnYear == null || params.validOnYear == '') {
            def sdfyear = new java.text.SimpleDateFormat(message(code: 'default.date.format.onlyYear'))
            params.validOnYear = sdfyear.format(new Date(System.currentTimeMillis()))
        }*/

        result.surveyYears = SurveyOrg.executeQuery("select Year(surorg.surveyConfig.surveyInfo.startDate) from SurveyOrg surorg where surorg.org = :org and surorg.surveyConfig.surveyInfo.startDate != null group by YEAR(surorg.surveyConfig.surveyInfo.startDate) order by YEAR(surorg.surveyConfig.surveyInfo.startDate)", [org: result.institution]) ?: []

        result.allConsortia = Org.executeQuery(
                """select o from Org o, SurveyInfo surInfo where surInfo.owner = o
                        group by o order by lower(o.name) """
        )

        List orgIds = orgTypeService.getCurrentOrgIdsOfProvidersAndAgencies( contextService.org )

        result.providers = orgIds.isEmpty() ? [] : Org.findAllByIdInList(orgIds).sort { it?.name }

        result.subscriptions = Subscription.executeQuery("select DISTINCT s.name from Subscription as s where ( exists ( select o from s.orgRelations as o where ( o.roleType = :roleType AND o.org = :activeInst ) ) ) " +
                " AND s.instanceOf is not null order by s.name asc ", ['roleType': RDStore.OR_SUBSCRIBER_CONS, 'activeInst': result.institution])

        SimpleDateFormat sdFormat = DateUtil.getSDF_NoTime()

        def fsq = filterService.getParticipantSurveyQuery_New(params, sdFormat, result.institution)

        result.surveyResults = SurveyResult.executeQuery(fsq.query, fsq.queryParams, params)

        if ( params.exportXLSX ) {

            SXSSFWorkbook wb
            List surveyConfigsforExport = result.surveyResults.collect {it[1]}
            if ( params.surveyCostItems ) {
                SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
                String datetoday = sdf.format(new Date(System.currentTimeMillis()))
                String filename = "${datetoday}_" + g.message(code: "surveyCostItems.label")
                //if(wb instanceof XSSFWorkbook) file += "x";
                response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
                response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                wb = (SXSSFWorkbook) surveyService.exportSurveyCostItems(surveyConfigsforExport, result.institution)
            }else {
                SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
                String datetoday = sdf.format(new Date(System.currentTimeMillis()))
                String filename = "${datetoday}_" + g.message(code: "survey.plural")
                //if(wb instanceof XSSFWorkbook) file += "x";
                response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
                response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                wb = (SXSSFWorkbook) surveyService.exportSurveys(surveyConfigsforExport, result.institution)
            }
            wb.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            wb.dispose()

            return
        }else {
            result.surveyResults = result.surveyResults.groupBy {it.id[1]}
            result.countSurveys = getSurveyParticipantCounts_New(result.institution)

            withFormat {
                html {

                    result
                }
            }
        }
    }

    @DebugAnnotation(perm="ORG_BASIC_MEMBER", affil="INST_USER", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_BASIC_MEMBER", "INST_USER", "ROLE_ADMIN")
    })
    def surveyInfos() {
        Map<String, Object> result = setResultGenerics()
        result.contextOrg = contextService.getOrg()

        result.surveyInfo = SurveyInfo.get(params.id) ?: null
        result.surveyConfig = params.surveyConfigID ? SurveyConfig.get(params.surveyConfigID as Long ? params.surveyConfigID: Long.parseLong(params.surveyConfigID)) : result.surveyInfo.surveyConfigs[0]

        result.surveyResults = SurveyResult.findAllByParticipantAndSurveyConfig(result.institution, result.surveyConfig).sort { it.surveyConfig.configOrder }

        result.ownerId = result.surveyResults[0]?.owner?.id

        if(result.surveyConfig?.type == 'Subscription') {
            result.subscriptionInstance = result.surveyConfig?.subscription?.getDerivedSubscriptionBySubscribers(result.institution)
            result.subscription = result.subscriptionInstance
            result.authorizedOrgs = result.user?.authorizedOrgs
            // restrict visible for templates/links/orgLinksAsList
            result.costItemSums = [:]
            result.visibleOrgRelations = []
            if(result.subscriptionInstance) {
                result.subscriptionInstance.orgRelations.each { OrgRole or ->
                    if (!(or.org.id == result.contextOrg.id) && !(or.roleType in [RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS])) {
                        result.visibleOrgRelations << or
                    }
                }
                result.visibleOrgRelations.sort { it.org.sortname }

                //costs dataToDisplay
                result.dataToDisplay = ['subscr']
                result.offsets = [subscrOffset: 0]
                result.sortConfig = [subscrSort: 'sub.name', subscrOrder: 'asc']

                result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
                //cost items
                //params.forExport = true
                LinkedHashMap costItems = result.subscription ? financeService.getCostItemsForSubscription(params, result) : null
                if (costItems?.subscr) {
                    result.costItemSums.subscrCosts = costItems.subscr.costItems
                }
		        result.links = linksGenerationService.getSourcesAndDestinations(result.subscriptionInstance,result.user)
            }

            if(result.surveyConfig.subSurveyUseForTransfer) {
                result.successorSubscription = result.surveyConfig.subscription._getCalculatedSuccessor()

                result.customProperties = result.successorSubscription ? comparisonService.comparePropertiesWithAudit(result.surveyConfig.subscription.propertySet.findAll{it.type.tenant == null && (it.tenant?.id == result.contextOrg.id || (it.tenant?.id != result.contextOrg.id && it.isPublic))} + result.successorSubscription.propertySet.findAll{it.type.tenant == null && (it.tenant?.id == result.contextOrg.id || (it.tenant?.id != result.contextOrg.id && it.isPublic))}, true, true) : null
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
            withFormat {
                html {
                    result
                }
            }
        }

    }

    @DebugAnnotation(perm="ORG_BASIC_MEMBER", affil="INST_USER", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_BASIC_MEMBER", "INST_USER", "ROLE_ADMIN")
    })
    def surveyInfosIssueEntitlements() {
        Map<String, Object> result = setResultGenerics()

        result.surveyConfig = SurveyConfig.get(params.id)
        result.surveyInfo = result.surveyConfig.surveyInfo

        result.surveyResults = SurveyResult.findAllByParticipantAndSurveyConfig(result.institution, result.surveyConfig).sort { it.surveyConfig.configOrder }

        result.subscriptionInstance = result.surveyConfig.subscription?.getDerivedSubscriptionBySubscribers(result.institution)

        result.ies = subscriptionService.getIssueEntitlementsNotFixed(result.subscriptionInstance)
        result.iesListPriceSum = 0.0
        result.ies?.each{
            result.iesListPriceSum = result.iesListPriceSum + (it?.priceItem ? (it.priceItem?.listPrice ? it.priceItem.listPrice : 0.0) : 0.0)
        }


        result.iesFix = subscriptionService.getIssueEntitlementsFixed(result.subscriptionInstance)
        result.iesFixListPriceSum = 0.0
        result.iesFix?.each{
            result.iesFixListPriceSum = result.iesFixListPriceSum + (it?.priceItem ? (it.priceItem?.listPrice ? it.priceItem.listPrice : 0.0) : 0.0)
        }


        result.ownerId = result.surveyConfig.surveyInfo.owner?.id ?: null

        if(result.subscriptionInstance) {
            result.authorizedOrgs = result.user?.authorizedOrgs
            result.contextOrg = contextService.getOrg()
            // restrict visible for templates/links/orgLinksAsList
            result.visibleOrgRelations = []
            result.subscriptionInstance.orgRelations.each { OrgRole or ->
                if (!(or.org?.id == contextService.getOrg().id) && !(or.roleType in [RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS])) {
                    result.visibleOrgRelations << or
                }
            }
            result.visibleOrgRelations.sort { it.org.sortname }
	        result.links = linksGenerationService.getSourcesAndDestinations(result.subscriptionInstance,result.user)
        }
        result
    }


    @DebugAnnotation(perm="ORG_BASIC_MEMBER", affil="INST_EDITOR", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_BASIC_MEMBER", "INST_EDITOR", "ROLE_ADMIN")
    })
    def surveyInfoFinish() {
        Map<String, Object> result = setResultGenerics()

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        SurveyInfo surveyInfo = SurveyInfo.get(params.id)
        SurveyConfig surveyConfig = SurveyConfig.get(params.surveyConfigID)
        boolean sendMailToSurveyOwner = false

        if(surveyConfig && surveyConfig.pickAndChoose){

            def surveyOrg = SurveyOrg.findByOrgAndSurveyConfig(result.institution, surveyConfig)

            def ies = subscriptionService.getIssueEntitlementsUnderConsideration(surveyConfig.subscription?.getDerivedSubscriptionBySubscribers(result.institution))
            ies.each { ie ->
                ie.acceptStatus = RDStore.IE_ACCEPT_STATUS_UNDER_NEGOTIATION
                ie.save(flush: true)
            }

            /*if(ies.size() > 0) {*/

                if (surveyOrg && surveyConfig) {
                    surveyOrg.finishDate = new Date()
                    if (!surveyOrg.save(flush: true)) {
                        flash.error = message(code: 'renewEntitlementsWithSurvey.submitNotSuccess')
                    } else {
                        flash.message = message(code: 'renewEntitlementsWithSurvey.submitSuccess')
                        sendMailToSurveyOwner = true
                    }
                } else {
                    flash.error = message(code: 'renewEntitlementsWithSurvey.submitNotSuccess')
                }
            /*}else {
                flash.error = message(code: 'renewEntitlementsWithSurvey.submitNotSuccessEmptyIEs')
            }*/
        }

            List<SurveyResult> surveyResults = SurveyResult.findAllByParticipantAndSurveyConfig(result.institution, surveyConfig)

            boolean allResultHaveValue = true
            //Verbindlich??|
            if(surveyInfo.isMandatory) {

                boolean noParticipation = false
                if(surveyConfig && surveyConfig.subSurveyUseForTransfer){
                    noParticipation = (SurveyResult.findByParticipantAndSurveyConfigAndType(result.institution, surveyConfig, RDStore.SURVEY_PROPERTY_PARTICIPATION).refValue == RDStore.YN_NO)
                }

                if(!noParticipation) {
                    surveyResults.each { surre ->
                        SurveyOrg surorg = SurveyOrg.findBySurveyConfigAndOrg(surre.surveyConfig, result.institution)

                        if (!surre.isResultProcessed() && !surorg.existsMultiYearTerm())
                            allResultHaveValue = false
                    }
                }
            }
            if (allResultHaveValue) {
                surveyResults.each {
                    it.finishDate = new Date()
                    it.save(flush:true)
                }
                sendMailToSurveyOwner = true
                // flash.message = message(code: "surveyResult.finish.info")
            } else {
                if(!surveyConfig.pickAndChoose && surveyInfo.isMandatory) {
                    flash.error = message(code: "surveyResult.finish.error")
                }
            }

        if(sendMailToSurveyOwner) {
            surveyService.emailToSurveyOwnerbyParticipationFinish(surveyInfo, result.institution)
        }


        redirect(url: request.getHeader('referer'))
    }


    @Deprecated
    @DebugAnnotation(perm="ORG_BASIC_MEMBER", affil="INST_EDITOR", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_BASIC_MEMBER", "INST_EDITOR", "ROLE_ADMIN")
    })
    def surveyResultFinish() {
        Map<String, Object> result = setResultGenerics()

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        result.surveyInfo = SurveyInfo.get(params.id) ?: null

        result.surveyResults = SurveyResult.findAllByParticipantAndSurveyConfigInList(result.institution, result.surveyInfo.surveyConfigs).sort { it?.surveyConfig?.configOrder }

        result.surveyResults.each{

           if(it.participant == result.institution) {
               it.finishDate = new Date(System.currentTimeMillis())
               it.save(flush: true)

               flash.message = g.message(code: "default.notAutorized.message")
           }
        }


        redirect action: 'surveyResult', id: result.surveyInfo.id
    }

    /*
    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def tip() {
        Map<String, Object> result = setResultGenerics()

      log.debug("tip :: ${params}")
      result.tip = TitleInstitutionProvider.get(params.id)

      if (request.method == 'POST' && result.tip ){
        log.debug("Add usage ${params}")
          SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
          Date usageDate = sdf.parse(params.usageDate);
          GregorianCalendar cal = new GregorianCalendar()
          cal.setTime(usageDate)
        Fact fact = new Fact(
          relatedTitle:result.tip.title,
          supplier:result.tip.provider,
          inst:result.tip.institution,
          juspio:result.tip.title.getIdentifierValue('jusp'),
          factFrom:usageDate,
          factTo:usageDate,
          factValue:params.usageValue,
          factUid:java.util.UUID.randomUUID().toString(),
          reportingYear:cal.get(Calendar.YEAR),
          reportingMonth:cal.get(Calendar.MONTH),
          factType:RefdataValue.get(params.factType)
        ).save(flush:true, failOnError:true);

      }

      if ( result.tip ) {
        result.usage = Fact.findAllByRelatedTitleAndSupplierAndInst(result.tip.title,result.tip.provider,result.tip.institution)
      }
      result
    }
     */

    @DebugAnnotation(test = 'hasAffiliation("INST_ADM")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_ADM") })
    def userList() {
        Map<String, Object> result = setResultGenerics()

        Map filterParams = params
        filterParams.status = UserOrg.STATUS_APPROVED
        filterParams.org = result.institution

        result.users = userService.getUserSet(filterParams)
        result.breadcrumb = '/organisation/breadcrumb'
        result.titleMessage = "${result.institution}"
        result.inContextOrg = true
        result.pendingRequests = UserOrg.findAllByStatusAndOrg(UserOrg.STATUS_PENDING, result.institution, [sort:'dateRequested', order:'desc'])
        result.orgInstance = result.institution
        result.navPath = "/organisation/nav"
        result.navConfiguration = [orgInstance: result.institution, inContextOrg: true]
        result.multipleAffiliationsWarning = true
        result.filterConfig = [filterableRoles:Role.findAllByRoleType('user'), orgField: false]
        result.tableConfig = [
                editable: result.editable,
                editor: result.user,
                editLink: 'userEdit',
                users: result.users,
                showAllAffiliations: false,
                showAffiliationDeleteLink: true,
                modifyAccountEnability: SpringSecurityUtils.ifAllGranted('ROLE_YODA')
        ]
        result.total = result.users.size()

        render view: '/templates/user/_list', model: result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_ADM")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_ADM") })
    def userEdit() {
        Map result = [user: User.get(params.id), editor: contextService.user, editable: true, institution: contextService.org, manipulateAffiliations: true]
        result.availableComboDeptOrgs = Combo.executeQuery("select c.fromOrg from Combo c where (c.fromOrg.status = null or c.fromOrg.status = :current) and c.toOrg = :ctxOrg and c.type = :type order by c.fromOrg.name",
                [ctxOrg: result.institution, current: RDStore.O_STATUS_CURRENT, type: RDStore.COMBO_TYPE_DEPARTMENT])
        result.availableComboDeptOrgs << result.institution
        if(accessService.checkPerm("ORG_INST_COLLECTIVE"))
            result.orgLabel = message(code:'collective.member.plural')
        else result.orgLabel = message(code:'default.institution')
        result.availableOrgRoles = Role.findAllByRoleType('user')

        render view: '/templates/user/_edit', model: result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_ADM")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_ADM") })
    def userCreate() {
        Map<String, Object> result = setResultGenerics()
        result.orgInstance = result.institution
        result.editor = result.user
        result.inContextOrg = true
        result.breadcrumb = '/organisation/breadcrumb'

        result.availableOrgs = Combo.executeQuery('select c.fromOrg from Combo c where c.toOrg = :ctxOrg and c.type = :dept order by c.fromOrg.name', [ctxOrg: result.orgInstance, dept: RDStore.COMBO_TYPE_DEPARTMENT])
        result.availableOrgs.add(result.orgInstance)

        result.availableOrgRoles = Role.findAllByRoleType('user')

        render view: '/templates/user/_create', model: result
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_ADM")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_ADM") })
    def processUserCreate() {
        def success = userService.addNewUser(params,flash)
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

    @DebugAnnotation(test = 'hasAffiliation("INST_ADM")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_ADM") })
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

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_USER")
    })
    def addressbook() {
        Map<String, Object> result = setResultGenerics()

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0

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


    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_USER", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN")
    })
    Map<String, Object> budgetCodes() {
        Map<String, Object> result = setResultGenerics()

        if (result.editable) {

            flash.message = null
            flash.error = null

            if (params.cmd == "newBudgetCode") {
                if (params.bc) {
                    BudgetCode bc = new BudgetCode(
                            owner: result.institution,
                            value: params.bc,
                            descr: params.descr
                    )
                    if (bc.save(flush:true)) {
                        flash.message = "Neuer Budgetcode wurde angelegt."
                    }
                    else {
                        flash.error = "Der neue Budgetcode konnte nicht angelegt werden."
                    }

                }
            } else if (params.cmd == "deleteBudgetCode") {
                def bc = genericOIDService.resolveOID(params.bc)
                if (bc && bc.owner.id == result.institution.id) {
                    bc.delete(flush:true)
                }
            }

        }
        Set<BudgetCode> allBudgetCodes = BudgetCode.findAllByOwner(result.institution, [sort: 'value'])
        Map<BudgetCode, List<CostItemGroup>> costItemGroups = [:]
        if(allBudgetCodes) {
            List<CostItemGroup> ciGroupsForBC = CostItemGroup.findAllByBudgetCodeInList(allBudgetCodes)
            ciGroupsForBC.each { CostItemGroup cig ->
                List<CostItemGroup> ciGroupForBC = costItemGroups.get(cig.budgetCode)
                if(!ciGroupForBC)
                    ciGroupForBC = []
                ciGroupForBC << cig
                costItemGroups.put(cig.budgetCode,ciGroupForBC)
            }
        }
        result.budgetCodes = allBudgetCodes
        result.costItemGroups = costItemGroups

        if (params.redirect) {
            redirect(url: request.getHeader('referer'), params: params)
        }

        result
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = { ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_USER") })
    def tasks() {
        Map<String, Object> result = setResultGenerics()

        if (params.deleteId) {
            Task dTask = Task.get(params.deleteId)
            if (dTask && (dTask.creator.id == result.user.id || contextService.getUser().hasAffiliation("INST_ADM"))) {
                try {
                    dTask.delete(flush: true)
                    flash.message = message(code: 'default.deleted.message', args: [message(code: 'task.label'), params.deleteId])
                }
                catch (Exception e) {
                    flash.message = message(code: 'default.not.deleted.message', args: [message(code: 'task.label'), params.deleteId])
                }
            } else {
                flash.message = message(code: 'default.not.deleted.notAutorized.message', args: [message(code: 'task.label'), params.deleteId])
            }
        }

        if ( ! params.sort) {
            params.sort = "t.endDate"
            params.order = "asc"
        }
        SimpleDateFormat sdFormat = DateUtil.getSDF_NoTime()
        def queryForFilter = filterService.getTaskQuery(params, sdFormat)
        int offset = params.offset ? Integer.parseInt(params.offset) : 0
        result.taskInstanceList = taskService.getTasksByResponsibles(result.user, result.institution, queryForFilter)
        result.taskInstanceCount = result.taskInstanceList.size()
        result.taskInstanceList = taskService.chopOffForPageSize(result.taskInstanceList, result.user, offset)

        result.myTaskInstanceList = taskService.getTasksByCreator(result.user,  queryForFilter, null)
        result.myTaskInstanceCount = result.myTaskInstanceList.size()
        result.myTaskInstanceList = taskService.chopOffForPageSize(result.myTaskInstanceList, result.user, offset)

        def preCon = taskService.getPreconditions(contextService.getOrg())
        result << preCon

        log.debug(result.taskInstanceList.toString())
        log.debug(result.myTaskInstanceList.toString())
        result
    }

    @DebugAnnotation(perm="ORG_CONSORTIUM", affil="INST_EDITOR",specRole="ROLE_ADMIN, ROLE_ORG_EDITOR")
    @Secured(closure = { ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM","INST_EDITOR","ROLE_ADMIN, ROLE_ORG_EDITOR") })
    def addMembers() {
        Map<String, Object> result = setResultGenerics()

        // new: filter preset
        result.comboType = 'Consortium'
        params.orgType   = RDStore.OT_INSTITUTION.id.toString()
        params.orgSector = RDStore.O_SECTOR_HIGHER_EDU.id.toString()

        if (params.selectedOrgs) {
            if(formService.validateToken(params)) {
                log.debug('adding orgs to consortia/institution')

                params.list('selectedOrgs').each { soId ->
                    Map map = [
                            toOrg: result.institution,
                            fromOrg: Org.findById( Long.parseLong(soId)),
                            type: RefdataValue.getByValueAndCategory(result.comboType,RDConstants.COMBO_TYPE)
                    ]
                    if (! Combo.findWhere(map)) {
                        Combo cmb = new Combo(map)
                        cmb.save(flush:true)
                    }
                }

            }
            redirect action: 'manageMembers'
        }
        result.filterSet = params.filterSet ? true : false
        Map<String,Object> fsq = filterService.getOrgQuery(params)
        List<Org> availableOrgs = Org.executeQuery(fsq.query, fsq.queryParams, params)
        Set<Org> currentMembers = Org.executeQuery('select c.fromOrg from Combo c where c.toOrg = :current and c.type = :comboType',[current:result.institution,comboType:RefdataValue.getByValueAndCategory(result.comboType,RDConstants.COMBO_TYPE)])
        result.availableOrgs = availableOrgs-currentMembers
        /*
        SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()

        def tableHeader
        if(result.comboType == 'Consortium')
            tableHeader = message(code: 'menu.public.all_orgs')
        else if(result.comboType == 'Department')
            tableHeader = message(code: 'menu.my.departments')
        String filename = tableHeader+"_"+sdf.format(new Date(System.currentTimeMillis()))
        if ( params.exportXLS ) {
            List orgs = (List) result.availableOrgs
            SXSSFWorkbook workbook = (SXSSFWorkbook) organisationService.exportOrg(orgs, tableHeader, true,'xls')

            response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            workbook.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            workbook.dispose()
        }
        else {
            withFormat {
                html {
                }
                csv {
                    response.setHeader("Content-disposition", "attachment; filename=\"${filename}.csv\"")
                    response.contentType = "text/csv"
                    ServletOutputStream out = response.outputStream
                    List orgs = (List) result.availableOrgs
                    out.withWriter { writer ->
                        writer.write((String) organisationService.exportOrg(orgs,tableHeader,true,"csv"))
                    }
                    out.close()
                }
            }
        }*/
        result
    }

    @DebugAnnotation(perm="ORG_CONSORTIUM", affil="INST_USER", specRole="ROLE_ADMIN,ROLE_ORG_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM","INST_USER","ROLE_ADMIN,ROLE_ORG_EDITOR")
    })
    def manageMembers() {
        Map<String, Object> result = setResultGenerics()

        ProfilerUtils pu = new ProfilerUtils()
        pu.setBenchmark('start')

        // new: filter preset
        result.comboType = RDStore.COMBO_TYPE_CONSORTIUM
        if (params.selectedOrgs) {
            log.debug('remove orgs from consortia')

            params.list('selectedOrgs').each { soId ->
                def cmb = Combo.findWhere(
                        toOrg: result.institution,
                        fromOrg: Org.get(Long.parseLong(soId)),
                        type: RDStore.COMBO_TYPE_CONSORTIUM
                )
                cmb.delete(flush:true)
            }
        }
        //params.orgSector    = RDStore.O_SECTOR_HIGHER_EDU?.id?.toString()

        result.max          = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset       = params.offset ? Integer.parseInt(params.offset) : 0
        result.propList     = PropertyDefinition.findAllPublicAndPrivateOrgProp(contextService.org)
        result.filterSet    = params.filterSet ? true : false

        params.comboType = result.comboType.value
        def fsq = filterService.getOrgComboQuery(params, result.institution)
        def tmpQuery = "select o.id " + fsq.query.minus("select o ")
        def memberIds = Org.executeQuery(tmpQuery, fsq.queryParams)

		pu.setBenchmark('query')

        if (params.filterPropDef && memberIds) {
            fsq                      = propertyService.evalFilterQuery(params, "select o FROM Org o WHERE o.id IN (:oids) order by o.sortname asc", 'o', [oids: memberIds])
        }

        List totalMembers      = Org.executeQuery(fsq.query, fsq.queryParams)
        result.totalMembers    = totalMembers.clone()
        result.membersCount    = totalMembers.size()
        result.members         = totalMembers.drop((int) result.offset).take((int) result.max)
        String header
        String exportHeader

        header = message(code: 'menu.my.consortia')
        exportHeader = message(code: 'export.my.consortia')
        SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
        // Write the output to a file
        String file = "${sdf.format(new Date(System.currentTimeMillis()))}_"+exportHeader

		List bm = pu.stopBenchmark()
		result.benchMark = bm

        if ( params.exportXLS ) {

            SXSSFWorkbook wb = (SXSSFWorkbook) organisationService.exportOrg(totalMembers, header, true, 'xls')
            response.setHeader "Content-disposition", "attachment; filename=\"${file}.xlsx\""
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            wb.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            wb.dispose()
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
                        writer.write((String) organisationService.exportOrg(totalMembers,header,true,"csv"))
                    }
                    out.close()
                }
            }
        }
    }

    @DebugAnnotation(perm="ORG_CONSORTIUM", affil="INST_USER", specRole="ROLE_ADMIN")
    @Secured(closure = { ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN") })
    def manageConsortiaSubscriptions() {

        Map<String,Object> result = setResultGenerics()
        result.tableConfig = ['withCostItems']
        result.putAll(subscriptionService.getMySubscriptionsForConsortia(params,result.user,result.institution,result.tableConfig))

        LinkedHashMap<Subscription,List<Org>> providers = [:]
        Map<Org,Set<String>> mailAddresses = [:]
        BidiMap subLinks = new DualHashBidiMap()
        if(params.format || params.exportXLS) {
            Links.findAllByLinkType(RDStore.LINKTYPE_FOLLOWS).each { Links link ->
                if(link.sourceSubscription && link.destinationSubscription)
                subLinks.put(link.sourceSubscription,link.destinationSubscription)
            }
            OrgRole.findAllByRoleTypeInList([RDStore.OR_PROVIDER,RDStore.OR_AGENCY]).each { it ->
                List<Org> orgs = providers.get(it.sub)
                if(orgs == null)
                    orgs = [it.org]
                else orgs.add(it.org)
                providers.put(it.sub,orgs)
            }
            List persons = Person.executeQuery("select c.content,c.prs from Contact c where c.prs in (select p from Person as p inner join p.roleLinks pr where " +
                    "( (p.isPublic = false and p.tenant = :ctx) or (p.isPublic = true) ) and pr.functionType = :roleType) and c.contentType = :email",
                    [ctx: result.institution,
                     roleType: RDStore.PRS_FUNC_GENERAL_CONTACT_PRS,
                     email: RDStore.CCT_EMAIL])
            persons.each {  personRow ->
                Person person = (Person) personRow[1]
                PersonRole pr = person.roleLinks.find{ p -> p.org != result.institution}
                if(pr) {
                    Org org = pr.org
                    Set<String> addresses = mailAddresses.get(org)
                    String mailAddress = (String) personRow[0]
                    if(!addresses) {
                        addresses = []
                    }
                    addresses << mailAddress
                    mailAddresses.put(org,addresses)
                }
            }
        }

        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()

        if(params.exportXLS) {
            XSSFWorkbook wb = new XSSFWorkbook()
            POIXMLProperties xmlProps = wb.getProperties()
            POIXMLProperties.CoreProperties coreProps = xmlProps.getCoreProperties()
            coreProps.setCreator(message(code:'laser'))
            XSSFCellStyle lineBreaks = wb.createCellStyle()
            lineBreaks.setWrapText(true)
            XSSFCellStyle csPositive = wb.createCellStyle()
            csPositive.setFillForegroundColor(new XSSFColor(new java.awt.Color(198,239,206)))
            csPositive.setFillPattern(FillPatternType.SOLID_FOREGROUND)
            XSSFCellStyle csNegative = wb.createCellStyle()
            csNegative.setFillForegroundColor(new XSSFColor(new java.awt.Color(255,199,206)))
            csNegative.setFillPattern(FillPatternType.SOLID_FOREGROUND)
            XSSFCellStyle csNeutral = wb.createCellStyle()
            csNeutral.setFillForegroundColor(new XSSFColor(new java.awt.Color(255,235,156)))
            csNeutral.setFillPattern(FillPatternType.SOLID_FOREGROUND)
            SXSSFWorkbook workbook = new SXSSFWorkbook(wb,50)
            workbook.setCompressTempFiles(true)
            SXSSFSheet sheet = workbook.createSheet(message(code:'menu.my.consortiaSubscriptions'))
            sheet.flushRows(10)
            sheet.setAutobreaks(true)
            Row headerRow = sheet.createRow(0)
            headerRow.setHeightInPoints(16.75f)
            List titles = [message(code:'sidewide.number'),message(code:'myinst.consortiaSubscriptions.member'), message(code:'org.mainContact.label'),message(code:'myinst.consortiaSubscriptions.subscription'),message(code:'globalUID.label'),
                           message(code:'license.label'), message(code:'myinst.consortiaSubscriptions.packages'),message(code:'myinst.consortiaSubscriptions.provider'),message(code:'myinst.consortiaSubscriptions.runningTimes'),
                           message(code:'subscription.isPublicForApi.label'),message(code:'subscription.hasPerpetualAccess.label'),
                           message(code:'financials.amountFinal'),"${message(code:'financials.isVisibleForSubscriber')} / ${message(code:'financials.costItemConfiguration')}"]
            titles.eachWithIndex{ titleName, int i ->
                Cell cell = headerRow.createCell(i)
                cell.setCellValue(titleName)
            }
            sheet.createFreezePane(0,1)
            Row row
            Cell cell
            int rownum = 1
            int sumcell = 11
            int sumTitleCell = 10
            result.entries.eachWithIndex { entry, int sidewideNumber ->
                log.debug("processing entry ${sidewideNumber} ...")
                CostItem ci = (CostItem) entry[0] ?: new CostItem()
                Subscription subCons = (Subscription) entry[1]
                Org subscr = (Org) entry[2]
                int cellnum = 0
                row = sheet.createRow(rownum)
                //sidewide number
                log.debug("insert sidewide number")
                cell = row.createCell(cellnum++)
                cell.setCellValue(rownum)
                //sortname
                log.debug("insert sortname")
                cell = row.createCell(cellnum++)
                String subscrName = ""
                if(subscr.sortname) subscrName += subscr.sortname
                subscrName += "(${subscr.name})"
                cell.setCellValue(subscrName)
                log.debug("insert general contacts")
                //general contacts
                Set<String> generalContacts = mailAddresses.get(subscr)
                cell = row.createCell(cellnum++)
                if(generalContacts)
                    cell.setCellValue(generalContacts.join('; '))
                //subscription name
                log.debug("insert subscription name")
                cell = row.createCell(cellnum++)
                String subscriptionString = subCons.name
                //if(subCons._getCalculatedPrevious()) //avoid! Makes 5846 queries!!!!!
                if(subLinks.getKey(subCons.id))
                    subscriptionString += " (${message(code:'subscription.hasPreviousSubscription')})"
                cell.setCellValue(subscriptionString)
                //subscription globalUID
                log.debug("insert subscription global UID")
                cell = row.createCell(cellnum++)
                cell.setCellValue(subCons.globalUID)
                //license name
                log.debug("insert license name")
                cell = row.createCell(cellnum++)
                if(result.linkedLicenses.get(subCons)) {
                    List<String> references = result.linkedLicenses.get(subCons).collect { License l -> l.reference }
                    cell.setCellValue(references.join("\n"))
                }
                //packages
                log.debug("insert package name")
                cell = row.createCell(cellnum++)
                cell.setCellStyle(lineBreaks)
                List<String> packageNames = []
                subCons.packages.each { subPkg ->
                    packageNames << subPkg.pkg.name
                }
                cell.setCellValue(packageNames.join("\n"))
                //provider
                log.debug("insert provider name")
                cell = row.createCell(cellnum++)
                cell.setCellStyle(lineBreaks)
                List<String> providerNames = []
                providers.get(subCons).each { p ->
                    log.debug("Getting provider ${p}")
                    providerNames << p.name
                }
                cell.setCellValue(providerNames.join("\n"))
                //running time from / to
                log.debug("insert running times")
                cell = row.createCell(cellnum++)
                String dateString = ""
                if(ci.id) {
                    if(ci.getDerivedStartDate()) dateString += sdf.format(ci.getDerivedStartDate())
                    if(ci.getDerivedEndDate()) dateString += " - ${sdf.format(ci.getDerivedEndDate())}"
                }
                cell.setCellValue(dateString)
                //is public for api
                log.debug("insert api flag")
                cell = row.createCell(cellnum++)
                cell.setCellValue(ci.sub?.isPublicForApi ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"))
                //has perpetual access
                log.debug("insert perpetual access flag")
                cell = row.createCell(cellnum++)
                cell.setCellValue(ci.sub?.hasPerpetualAccess ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"))
                //final sum
                log.debug("insert final sum")
                cell = row.createCell(cellnum++)
                if(ci.id && ci.costItemElementConfiguration) {
                    switch(ci.costItemElementConfiguration) {
                        case RDStore.CIEC_POSITIVE: cell.setCellStyle(csPositive)
                            break
                        case RDStore.CIEC_NEGATIVE: cell.setCellStyle(csNegative)
                            break
                        case RDStore.CIEC_NEUTRAL: cell.setCellStyle(csNeutral)
                            break
                    }
                    cell.setCellValue(formatNumber([number:ci.costInBillingCurrencyAfterTax ?: 0.0,type:'currency',currencySymbol:ci.billingCurrency ?: 'EUR']))
                }
                //cost item sign and visibility
                log.debug("insert cost sign and visiblity")
                cell = row.createCell(cellnum++)
                String costSignAndVisibility = ""
                if(ci.id) {
                    if(ci.isVisibleForSubscriber) {
                        costSignAndVisibility += message(code:'financials.isVisibleForSubscriber')+" / "
                    }
                    if(ci.costItemElementConfiguration) {
                        costSignAndVisibility += ci.costItemElementConfiguration.getI10n("value")
                    }
                    else
                        costSignAndVisibility += message(code:'financials.costItemConfiguration.notSet')
                }
                cell.setCellValue(costSignAndVisibility)
                rownum++
            }
            rownum++
            sheet.createRow(rownum)
            rownum++
            Row sumRow = sheet.createRow(rownum)
            cell = sumRow.createCell(sumTitleCell)
            cell.setCellValue(message(code:'financials.export.sums'))
            rownum++
            result.finances.each { entry ->
                sumRow = sheet.createRow(rownum)
                cell = sumRow.createCell(sumTitleCell)
                cell.setCellValue("${message(code:'financials.sum.billing')} ${entry.key}")
                cell = sumRow.createCell(sumcell)
                cell.setCellValue(formatNumber([number:entry.value,type:'currency',currencySymbol: entry.key]))
                rownum++
            }
            for(int i = 0;i < titles.size();i++) {
                try {
                    sheet.autoSizeColumn(i)
                }
                catch (NullPointerException e) {
                    log.error("Null value in column ${i}")
                }
            }
            String filename = "${DateUtil.SDF_NoTimeNoPoint.format(new Date(System.currentTimeMillis()))}_${g.message(code:'export.my.consortiaSubscriptions')}.xlsx"
            response.setHeader("Content-disposition","attachment; filename=\"${filename}\"")
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            workbook.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            workbook.dispose()
        }
        else
            withFormat {
                html {
                    result
                }
                csv {
                    List titles = [message(code:'sidewide.number'),message(code:'myinst.consortiaSubscriptions.member'), message(code:'org.mainContact.label'),message(code:'myinst.consortiaSubscriptions.subscription'), message(code:'globalUID.label'),
                                   message(code:'license.label'), message(code:'myinst.consortiaSubscriptions.packages'),message(code:'myinst.consortiaSubscriptions.provider'),message(code:'myinst.consortiaSubscriptions.runningTimes'),
                                   message(code:'subscription.isPublicForApi.label'),message(code:'subscription.hasPerpetualAccess.label'),
                                   message(code:'financials.amountFinal'),"${message(code:'financials.isVisibleForSubscriber')} / ${message(code:'financials.costItemConfiguration')}"]
                    List columnData = []
                    List row
                    result.entries.eachWithIndex { entry, int sidewideNumber ->
                        row = []
                        log.debug("processing entry ${sidewideNumber} ...")
                        CostItem ci = (CostItem) entry[0] ?: new CostItem()
                        Subscription subCons = (Subscription) entry[1]
                        Org subscr = (Org) entry[2]
                        int cellnum = 0
                        //sidewide number
                        log.debug("insert sidewide number")
                        cellnum++
                        row.add(sidewideNumber)
                        //sortname
                        log.debug("insert sortname")
                        cellnum++
                        String subscrName = ""
                        if(subscr.sortname) subscrName += subscr.sortname
                        subscrName += "(${subscr.name})"
                        row.add(subscrName.replaceAll(',',' '))
                        log.debug("insert general contacts")
                        //general contacts
                        Set<String> generalContacts = mailAddresses.get(subscr)
                        if(generalContacts)
                            row.add(generalContacts.join('; '))
                        else row.add(' ')
                        //subscription name
                        log.debug("insert subscription name")
                        cellnum++
                        String subscriptionString = subCons.name
                        //if(subCons._getCalculatedPrevious()) //avoid! Makes 5846 queries!!!!!
                        if(subLinks.getKey(subCons.id))
                            subscriptionString += " (${message(code:'subscription.hasPreviousSubscription')})"
                        row.add(subscriptionString.replaceAll(',',' '))
                        //subscription global uid
                        log.debug("insert global uid")
                        cellnum++
                        row.add(subCons.globalUID)
                        //license name
                        log.debug("insert license name")
                        cellnum++
                        if(result.linkedLicenses.get(subCons)) {
                            List<String> references = result.linkedLicenses.get(subCons).collect { License l -> l.reference.replace(',',' ') }
                            row.add(references.join(' '))
                        }
                        else row.add(' ')
                        //packages
                        log.debug("insert package name")
                        cellnum++
                        String packagesString = " "
                        subCons.packages.each { subPkg ->
                            packagesString += "${subPkg.pkg.name} "
                        }
                        row.add(packagesString.replaceAll(',',' '))
                        //provider
                        log.debug("insert provider name")
                        cellnum++
                        String providersString = " "
                        providers.get(subCons).each { p ->
                            log.debug("Getting provider ${p}")
                            providersString += "${p.name} "
                        }
                        row.add(providersString.replaceAll(',',' '))
                        //running time from / to
                        log.debug("insert running times")
                        cellnum++
                        String dateString = " "
                        if(ci.id) {
                            if(ci.getDerivedStartDate()) dateString += sdf.format(ci.getDerivedStartDate())
                            if(ci.getDerivedEndDate()) dateString += " - ${sdf.format(ci.getDerivedEndDate())}"
                        }
                        row.add(dateString)
                        //is public for api
                        log.debug("insert api flag")
                        cellnum++
                        row.add(ci.sub?.isPublicForApi ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"))
                        //has perpetual access
                        log.debug("insert perpetual access flag")
                        cellnum++
                        row.add(ci.sub?.hasPerpetualAccess ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"))
                        //final sum
                        log.debug("insert final sum")
                        cellnum++
                        if(ci.id && ci.costItemElementConfiguration) {
                            row.add("${ci.costInBillingCurrencyAfterTax ?: 0.0} ${ci.billingCurrency ?: 'EUR'}")
                        }
                        else row.add(" ")
                        //cost item sign and visibility
                        log.debug("insert cost sign and visiblity")
                        cellnum++
                        String costSignAndVisibility = " "
                        if(ci.id) {
                            if(ci.isVisibleForSubscriber) {
                                costSignAndVisibility += message(code:'financials.isVisibleForSubscriber')+" / "
                            }
                            if(ci.costItemElementConfiguration) {
                                costSignAndVisibility += ci.costItemElementConfiguration.getI10n("value")
                            }
                            else
                                costSignAndVisibility += message(code:'financials.costItemConfiguration.notSet')
                        }
                        row.add(costSignAndVisibility)
                        columnData.add(row)
                    }
                    columnData.add([])
                    columnData.add([])
                    row = []
                    //sumcell = 11
                    //sumTitleCell = 10
                    for(int h = 0;h < 10;h++) {
                        row.add(" ")
                    }
                    row.add(message(code:'financials.export.sums'))
                    columnData.add(row)
                    columnData.add([])
                    result.finances.each { entry ->
                        row = []
                        for(int h = 0;h < 10;h++) {
                            row.add(" ")
                        }
                        row.add("${message(code:'financials.sum.billing')} ${entry.key}")
                        row.add("${entry.value} ${entry.key}")
                        columnData.add(row)
                    }
                    String filename = "${DateUtil.SDF_NoTimeNoPoint.format(new Date(System.currentTimeMillis()))}_${g.message(code:'export.my.consortiaSubscriptions')}.csv"
                    response.setHeader("Content-disposition","attachment; filename=\"${filename}\"")
                    response.contentType = "text/csv"
                    response.outputStream.withWriter { writer ->
                        writer.write(exportService.generateSeparatorTableString(titles,columnData,','))
                    }
                    response.outputStream.flush()
                    response.outputStream.close()
                }
            }
    }
    @DebugAnnotation(perm="ORG_CONSORTIUM", affil="INST_USER", specRole="ROLE_ADMIN")
    @Secured(closure = { ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_USER", "ROLE_ADMIN") })
    def manageParticipantSurveys() {
        Map<String, Object> result = setResultGenerics()

        ProfilerUtils pu = new ProfilerUtils()
        pu.setBenchmark('filterService')

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0

        DateFormat sdFormat = DateUtil.getSDF_NoTime()

        result.participant = Org.get(Long.parseLong(params.id))

        params.tab = params.tab ?: 'new'

        if(params.tab != 'new'){
            params.sort = 'surInfo.endDate DESC, LOWER(surInfo.name)'
        }

        /*if (params.validOnYear == null || params.validOnYear == '') {
            def sdfyear = new java.text.SimpleDateFormat(message(code: 'default.date.format.onlyYear'))
            params.validOnYear = sdfyear.format(new Date(System.currentTimeMillis()))
        }*/

        result.surveyYears = SurveyOrg.executeQuery("select Year(surorg.surveyConfig.surveyInfo.startDate) from SurveyOrg surorg where surorg.org = :org and surorg.surveyConfig.surveyInfo.startDate != null group by YEAR(surorg.surveyConfig.surveyInfo.startDate) order by YEAR(surorg.surveyConfig.surveyInfo.startDate)", [org: result.participant]) ?: []

        params.consortiaOrg = result.institution

        def fsq = filterService.getParticipantSurveyQuery_New(params, sdFormat, result.participant)

        result.surveyResults = SurveyResult.executeQuery(fsq.query, fsq.queryParams, params)

        if ( params.exportXLSX ) {

            SXSSFWorkbook wb
            SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
            String datetoday = sdf.format(new Date(System.currentTimeMillis()))
            String filename = "${datetoday}_" + g.message(code: "survey.plural")
            //if(wb instanceof XSSFWorkbook) file += "x";
            response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            wb = (SXSSFWorkbook) surveyService.exportSurveysOfParticipant(result.surveyResults.collect{it[1]}, result.participant)

            wb.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            wb.dispose()

            return
        }else {
            result.surveyResults = result.surveyResults.groupBy {it.id[1]}
            result.countSurveys = getSurveyParticipantCounts_New(result.participant)

            result
        }
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_USER")
    })
    def managePropertyGroups() {
        Map<String,Object> result = setResultGenerics()
        //result.editable = true // true, because action is protected (is it? I doubt; INST_USERs have at least reading rights to this page!)
        switch(params.cmd) {
            case 'new': result.formUrl = g.createLink([controller: 'myInstitution', action: 'managePropertyGroups'])
                result.createOrUpdate = message(code:'default.button.create.label')
                render template: '/templates/properties/propertyGroupModal', model: result
                return
            case 'edit':
                result.pdGroup = genericOIDService.resolveOID(params.oid)
                result.formUrl = g.createLink([controller: 'myInstitution', action: 'managePropertyGroups'])
                result.createOrUpdate = message(code:'default.button.save.label')
                render template: '/templates/properties/propertyGroupModal', model: result
                return
            case 'delete':
                PropertyDefinitionGroup pdg = (PropertyDefinitionGroup) genericOIDService.resolveOID(params.oid)
                try {
                    pdg.delete(flush:true)
                    flash.message = message(code:'propertyDefinitionGroup.delete.success',args:[pdg.name])
                }
                catch (e) {
                    flash.error = message(code:'propertyDefinitionGroup.delete.failure',args:[pdg.name])
                }
                break
            case 'processing':
                if(formService.validateToken(params)) {
                    boolean valid
                    PropertyDefinitionGroup propDefGroup
                    String ownerType = PropertyDefinition.getDescrClass(params.prop_descr)

                    if (params.oid) {
                        propDefGroup = (PropertyDefinitionGroup) genericOIDService.resolveOID(params.oid)
                        propDefGroup.name = params.name ?: propDefGroup.name
                        propDefGroup.description = params.description
                        propDefGroup.ownerType = ownerType

                        if (propDefGroup.save(flush:true)) {
                            valid = true
                        }
                    }
                    else {
                        if (params.name && ownerType) {
                            propDefGroup = new PropertyDefinitionGroup(
                                    name: params.name,
                                    description: params.description,
                                    tenant: result.institution,
                                    ownerType: ownerType,
                                    isVisible: true
                            )
                            if (propDefGroup.save(flush:true)) {
                                valid = true
                            }
                        }
                    }

                    if (valid) {
                        PropertyDefinitionGroupItem.executeUpdate(
                                "DELETE PropertyDefinitionGroupItem pdgi WHERE pdgi.propDefGroup = :pdg",
                                [pdg: propDefGroup]
                        )

                        params.list('propertyDefinition')?.each { pd ->

                            new PropertyDefinitionGroupItem(
                                    propDef: pd,
                                    propDefGroup: propDefGroup
                            ).save(flush:true)
                        }
                    }
                }
                break
        }

        Set<PropertyDefinitionGroup> unorderedPdgs = PropertyDefinitionGroup.findAllByTenant(result.institution, [sort: 'name'])
        result.propDefGroups = [:]
        PropertyDefinition.AVAILABLE_GROUPS_DESCR.each { String propDefGroupType ->
            result.propDefGroups.put(propDefGroupType,unorderedPdgs.findAll { PropertyDefinitionGroup pdg -> pdg.ownerType == PropertyDefinition.getDescrClass(propDefGroupType)})
        }

        if(params.cmd == 'exportXLS') {
            SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
            SXSSFWorkbook workbook = exportService.generateXLSXWorkbook(exportService.generatePropertyGroupUsageXLS(result.propDefGroups))
            response.setHeader("Content-disposition", "attachment; filename=\"${sdf.format(new Date(System.currentTimeMillis()))}_${message(code:'export.my.propertyGroups')}.xlsx\"")
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            workbook.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            workbook.dispose()
        }
        else
            result
    }

    @DebugAnnotation(perm = "ORG_INST,ORG_CONSORTIUM", affil = "INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def manageProperties() {
        Map<String,Object> result = setResultGenerics()
        if (!result) {
            response.sendError(401); return
        }

        if (!result.editable) {
            flash.error = g.message(code: "default.notAutorized.message")
            redirect(url: request.getHeader('referer'))
        }

        PropertyDefinition propDef = params.filterPropDef ? genericOIDService.resolveOID(params.filterPropDef.replace(" ", "")) : null

        //params.remove('filterPropDef')

        //Set<Subscription> validSubChildren = Subscription.executeQuery("select oo.sub from OrgRole oo where oo.sub.instanceOf = :parent order by oo.org.sortname asc",[parent:result.parentSub])
        /*Sortieren
        result.validSubChilds = validSubChilds.sort { Subscription a, Subscription b ->
            def sa = a.getSubscriber()
            def sb = b.getSubscriber()
            (sa.sortname ?: sa.name).compareTo((sb.sortname ?: sb.name))
        }*/
        //result.validSubChilds = validSubChildren

        String localizedName
        switch(LocaleContextHolder.getLocale()) {
            case Locale.GERMANY:
            case Locale.GERMAN: localizedName = "name_de"
                break
            default: localizedName = "name_en"
                break
        }
        //result.propList = PropertyDefinition.findAllPublicAndPrivateProp([PropertyDefinition.SUB_PROP], contextService.org)
        Set<PropertyDefinition> propList = PropertyDefinition.executeQuery("select pd from PropertyDefinition pd where pd.descr in (:availableTypes) and (pd.tenant = null or pd.tenant = :ctx) order by pd."+localizedName+" asc",
                [ctx:result.institution,availableTypes:[PropertyDefinition.SUB_PROP,PropertyDefinition.LIC_PROP,PropertyDefinition.PRS_PROP,PropertyDefinition.PLA_PROP,PropertyDefinition.ORG_PROP]])
        result.propList = propList
        result.filteredObjs = []
        result.objectsWithoutProp = []

        if(propDef) {
            Set filteredObjs = [], objectsWithoutProp = []
            Map<String,Object> parameterMap = [type:propDef,ctx:result.institution], orgFilterParams = [:]
            String subFilterClause = '', licFilterClause = '', spOwnerFilterClause = '', lpOwnerFilterClause = '', orgFilterClause = ''
            if(accessService.checkPerm('ORG_CONSORTIUM')) {
                subFilterClause += 'and oo.sub.instanceOf = null'
                spOwnerFilterClause += 'and sp.owner.instanceOf = null'
                licFilterClause += 'and oo.lic.instanceOf = null'
                lpOwnerFilterClause += 'and lp.owner.instanceOf = null'
            }
            else if(accessService.checkPerm('ORG_BASIC_MEMBER')) {
                orgFilterClause += 'and ot in (:providerAgency)'
                orgFilterParams.providerAgency = [RDStore.OT_AGENCY,RDStore.OT_PROVIDER,RefdataValue.getByValueAndCategory('Broker',RDConstants.ORG_TYPE),RefdataValue.getByValueAndCategory('Content Provider',RDConstants.ORG_TYPE),RefdataValue.getByValueAndCategory('Vendor',RDConstants.ORG_TYPE)]
            }
            switch(propDef.descr) {
                case PropertyDefinition.SUB_PROP: objectsWithoutProp.addAll(Subscription.executeQuery('select oo.sub from OrgRole oo where oo.org = :ctx '+subFilterClause+' and oo.roleType in (:roleTypes) and not exists (select sp from SubscriptionProperty sp where sp.owner = oo.sub and sp.tenant = :ctx and sp.type = :type) order by oo.sub.name asc, oo.sub.startDate asc, oo.sub.endDate asc',parameterMap+[roleTypes:[RDStore.OR_SUBSCRIPTION_CONSORTIA,RDStore.OR_SUBSCRIBER_CONS,RDStore.OR_SUBSCRIBER]]))
                    filteredObjs.addAll(SubscriptionProperty.executeQuery('select sp.owner from SubscriptionProperty sp where sp.type = :type and sp.tenant = :ctx '+spOwnerFilterClause+' order by sp.owner.name asc',parameterMap))
                    result.auditable = propDef.tenant == null //blocked until inheritance of private property is cleared
                    result.manageChildren = true
                    break
                case PropertyDefinition.LIC_PROP: objectsWithoutProp.addAll(License.executeQuery('select oo.lic from OrgRole oo where oo.org = :ctx '+licFilterClause+' and oo.roleType in (:roleTypes) and not exists (select lp from LicenseProperty lp where lp.owner = oo.lic and lp.tenant = :ctx and lp.type = :type) order by oo.lic.reference asc, oo.lic.startDate asc, oo.lic.endDate asc',parameterMap+[roleTypes:[RDStore.OR_LICENSING_CONSORTIUM,RDStore.OR_LICENSEE_CONS,RDStore.OR_LICENSEE]]))
                    filteredObjs.addAll(LicenseProperty.executeQuery('select lp.owner from LicenseProperty lp where lp.type = :type and lp.tenant = :ctx '+lpOwnerFilterClause+' order by lp.owner.reference asc',parameterMap))
                    result.auditable = propDef.tenant == null //blocked until inheritance of private property is cleared
                    break
                case PropertyDefinition.PRS_PROP: objectsWithoutProp.addAll(Person.executeQuery('select p from Person p where (p.tenant = :ctx or p.tenant = null) and not exists (select pp from PersonProperty pp where pp.owner = p and pp.tenant = :ctx and pp.type = :type) order by p.last_name asc, p.first_name asc',parameterMap))
                    filteredObjs.addAll(PersonProperty.executeQuery('select pp.owner from PersonProperty pp where pp.type = :type and pp.tenant = :ctx order by pp.owner.last_name asc, pp.owner.first_name asc',parameterMap))
                    break
                case PropertyDefinition.ORG_PROP: objectsWithoutProp.addAll(Org.executeQuery('select o from Org o join o.orgType ot where o.status != :deleted and not exists (select op from OrgProperty op where op.owner = o and op.tenant = :ctx and op.type = :type) '+orgFilterClause+' order by o.sortname asc, o.name asc',parameterMap+orgFilterParams+[deleted:RDStore.ORG_STATUS_DELETED]))
                    filteredObjs.addAll(OrgProperty.executeQuery('select op.owner from OrgProperty op where op.type = :type and op.tenant = :ctx order by op.owner.sortname asc, op.owner.name asc',parameterMap))
                    result.sortname = true
                    break
                case PropertyDefinition.PLA_PROP: objectsWithoutProp.addAll(Platform.executeQuery('select pl from Platform pl where pl.status != :deleted and not exists (select plp from PlatformProperty plp where plp.owner = plp and plp.tenant = :ctx and plp.type = :type) order by pl.name asc',parameterMap+[deleted:RDStore.PLATFORM_STATUS_DELETED]))
                    filteredObjs.addAll(PlatformProperty.executeQuery('select plp.owner from PlatformProperty plp where plp.type = :type and plp.tenant = :ctx order by plp.owner.name asc',parameterMap))
                    break
            }
            objectsWithoutProp.each { obj ->
                result.objectsWithoutProp << propertyService.processObjects(obj,result.institution,propDef)
            }
            filteredObjs.each { obj ->
                result.filteredObjs << propertyService.processObjects(obj,result.institution,propDef)
            }
            result.filterPropDef = propDef
        }

        /*
        def oldID = params.id
        params.id = result.parentSub.id

        ArrayList<Long> filteredOrgIds = getOrgIdsForFilter()
        result.filteredSubChilds = new ArrayList<Subscription>()
        result.validSubChilds.each { Subscription sub ->
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

        params.id = oldID*/

        result
    }

    @DebugAnnotation(perm = "ORG_INST,ORG_CONSORTIUM", affil = "INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def processManageProperties() {
        Map<String, Object> result = setResultGenerics()
        log.debug( params.toMapString() )
        PropertyDefinition pd = (PropertyDefinition) genericOIDService.resolveOID(params.filterPropDef)
        List withAudit = params.list("withAudit")
        String propertyType = pd.tenant ? PropertyDefinition.PRIVATE_PROPERTY : PropertyDefinition.CUSTOM_PROPERTY
        if(params.newObjects) {
            params.list("newObjects").each { String id ->
                def owner = resolveOwner(pd,id)
                if(owner) {
                    AbstractPropertyWithCalculatedLastUpdated prop = owner.propertySet.find { exProp -> exProp.type.id == pd.id && exProp.tenant.id == result.institution.id }
                    if(!prop || pd.multipleOccurrence) {
                        prop = PropertyDefinition.createGenericProperty(propertyType,owner,pd,result.institution)
                        if(setPropValue(prop,params.filterPropValue)) {
                            if(id in withAudit) {
                                owner.getClass().findAllByInstanceOf(owner).each { member ->
                                    AbstractPropertyWithCalculatedLastUpdated memberProp = PropertyDefinition.createGenericProperty(propertyType,member,prop.type,result.institution)
                                    memberProp = prop.copyInto(memberProp)
                                    memberProp.instanceOf = prop
                                    memberProp.save(flush:true)
                                    AuditConfig.addConfig(prop,AuditConfig.COMPLETE_OBJECT)
                                }
                            }
                        }
                        else log.error(prop.errors.toString())
                    }
                }
            }
        }
        if(params.selectedObjects) {
            if(params.deleteProperties) {
                List selectedObjects = params.list("selectedObjects")
                processDeleteProperties(pd,selectedObjects,result.institution)
            }
            else {
                params.list("selectedObjects").each { String id ->
                    def owner = resolveOwner(pd,id)
                    if(owner) {
                        AbstractPropertyWithCalculatedLastUpdated prop = owner.propertySet.find { exProp -> exProp.type.id == pd.id && exProp.tenant.id == result.institution.id }
                        if(prop)
                            setPropValue(prop, params.filterPropValue)
                    }
                }
            }
        }
        redirect action: 'manageProperties', params: [filterPropDef:params.filterPropDef]
    }

    @DebugAnnotation(perm = "ORG_INST,ORG_CONSORTIUM", affil = "INST_EDITOR")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def processDeleteProperties(PropertyDefinition propDef, selectedObjects, Org contextOrg) {
        int deletedProperties = 0
        selectedObjects.each { ownerId ->
            def owner = resolveOwner(propDef,ownerId)
            Set<AbstractPropertyWithCalculatedLastUpdated> existingProps = owner.propertySet.findAll {
                it.owner.id == owner.id && it.type.id == propDef.id && it.tenant?.id == contextOrg.id && !AuditConfig.getConfig(it)
            }

            existingProps.each { AbstractPropertyWithCalculatedLastUpdated prop ->
                owner.propertySet.remove(prop)
                owner.save(flush:true)
                prop.delete(flush:true)
                deletedProperties++
            }
        }
    }

    def resolveOwner(PropertyDefinition pd, String id) {
        def owner
        switch(pd.descr) {
            case PropertyDefinition.SUB_PROP: owner = Subscription.get(id)
                break
            case PropertyDefinition.LIC_PROP: owner = License.get(id)
                break
            case PropertyDefinition.ORG_PROP: owner = Org.get(id)
                break
            case PropertyDefinition.PRS_PROP: owner = Person.get(id)
                break
            case PropertyDefinition.PLA_PROP: owner = Platform.get(id)
                break
        }
        owner
    }

    //explicit assignal raises a grails warning
    boolean setPropValue(prop, String filterPropValue) {
        prop = (AbstractPropertyWithCalculatedLastUpdated) prop

        if (prop.type.isIntegerType()) {
            prop.intValue = Integer.parseInt(filterPropValue)
        }
        else if (prop.type.isStringType()) {
            prop.stringValue = filterPropValue
        }
        else if (prop.type.isBigDecimalType()) {
            prop.decValue = new BigDecimal(filterPropValue)
        }
        else if (prop.type.isDateType()) {
            SimpleDateFormat sdf = DateUtil.SDF_NoTime
                prop.dateValue = sdf.parse(filterPropValue)
        }
        else if (prop.type.isURLType()) {
            prop.urlValue = filterPropValue.startsWith('http') ? new URL(filterPropValue) : new URL('http://'+filterPropValue)
        }
        else if (prop.type.isRefdataValueType()) {
            prop.refValue = RefdataValue.get(filterPropValue)
        }

        prop.save(flush:true)
    }

    /**
     * Display and manage PrivateProperties for this institution
     */
    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_USER")
    })
    def managePrivatePropertyDefinitions() {
        Map<String, Object> result = setResultGenerics()

        switch(params.cmd) {
            case 'add':List rl = addPrivatePropertyDefinition(params)
                flash."${rl[0]}" = rl[1]
                break
            case 'toggleMandatory':
                PropertyDefinition pd = (PropertyDefinition) genericOIDService.resolveOID(params.pd)
                pd.mandatory = !pd.mandatory
                pd.save(flush:true)
                break
            case 'toggleMultipleOccurrence':
                PropertyDefinition pd = (PropertyDefinition) genericOIDService.resolveOID(params.pd)
                pd.multipleOccurrence = !pd.multipleOccurrence
                pd.save(flush:true)
                break
            case 'delete': flash.message = deletePrivatePropertyDefinition(params)
                break
        }

        result.languageSuffix = I10nTranslation.decodeLocale(LocaleContextHolder.getLocale())

        Map<String, Set<PropertyDefinition>> propDefs = [:]
        PropertyDefinition.AVAILABLE_PRIVATE_DESCR.each { String it ->
            Set<PropertyDefinition> itResult = PropertyDefinition.findAllByDescrAndTenant(it, result.institution, [sort: 'name_'+result.languageSuffix]) // ONLY private properties!
            propDefs[it] = itResult
        }

        result.propertyDefinitions = propDefs

        def (usedPdList, attrMap, multiplePdList) = propertyService.getUsageDetails() // [List<Long>, Map<String, Object>, List<Long>]
        result.usedPdList = usedPdList
        result.attrMap = attrMap
        result.multiplePdList = multiplePdList
        //result.editable = true // true, because action is protected (it is not, cf. ERMS-2132! INST_USERs do have reading access to this page!)
        result.propertyType = 'private'
        if(params.cmd == 'exportXLS') {
            SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
            SXSSFWorkbook workbook = exportService.generateXLSXWorkbook(exportService.generatePropertyUsageExportXLS(propDefs))
            response.setHeader("Content-disposition", "attachment; filename=\"${sdf.format(new Date(System.currentTimeMillis()))}_${message(code:'export.my.privateProperties')}.xlsx\"")
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            workbook.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            workbook.dispose()
        }
        else
            result
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_USER")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_USER")
    })
    Object managePropertyDefinitions() {
        Map<String,Object> result = setResultGenerics()

        if(params.pd) {
            PropertyDefinition pd = (PropertyDefinition) genericOIDService.resolveOID(params.pd)
            if (pd) {
                switch(params.cmd) {
                    case 'toggleMandatory': pd.mandatory = !pd.mandatory
                        pd.save(flush:true)
                        break
                    case 'toggleMultipleOccurrence': pd.multipleOccurrence = !pd.multipleOccurrence
                        pd.save(flush:true)
                        break
                    case 'deletePropertyDefinition':
                        if (! pd.isHardData) {
                            try {
                                pd.delete(flush:true)
                                flash.message = message(code:'propertyDefinition.delete.success',[pd.getI10n('name')])
                            }
                            catch(Exception e) {
                                flash.error = message(code:'propertyDefinition.delete.failure.default',[pd.getI10n('name')])
                            }
                        }
                        break
                }
            }
        }

        result.languageSuffix = I10nTranslation.decodeLocale(LocaleContextHolder.getLocale())

        Map<String,Set<PropertyDefinition>> propDefs = [:]
        PropertyDefinition.AVAILABLE_CUSTOM_DESCR.each { it ->
            Set<PropertyDefinition> itResult = PropertyDefinition.findAllByDescrAndTenant(it, null, [sort: 'name_'+result.languageSuffix]) // NO private properties!
            propDefs[it] = itResult
        }

        def (usedPdList, attrMap, multiplePdList) = propertyService.getUsageDetails() // [List<Long>, Map<String, Object>, List<Long>]
        result.propertyDefinitions = propDefs
        result.attrMap = attrMap
        result.usedPdList = usedPdList

        result.propertyType = 'custom'
        if(params.cmd == 'exportXLS') {
            SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
            SXSSFWorkbook workbook = exportService.generateXLSXWorkbook(exportService.generatePropertyUsageExportXLS(propDefs))
            response.setHeader("Content-disposition", "attachment; filename=\"${sdf.format(new Date(System.currentTimeMillis()))}_${message(code:'export.my.customProperties')}.xlsx\"")
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            workbook.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            workbook.dispose()
        }
        else
            render view: 'managePrivatePropertyDefinitions', model: result
    }

    @Secured(['ROLE_USER'])
    def switchContext() {
        User user = User.get(springSecurityService.principal.id)
        Org org = (Org) genericOIDService.resolveOID(params.oid)

        if (user && org && org.id in user.getAuthorizedOrgsIds()) {
            log.debug('switched context to: ' + org)
            contextService.setOrg(org)
        }
        redirect action:'dashboard', params:params.remove('oid')
    }

    /**
     * Adding new PrivateProperty for this institution if not existing
     *
     * @param params
     * @return
     */

    private List addPrivatePropertyDefinition(params) {
        log.debug("trying to add private property definition for institution: " + params)

        Org tenant = contextService.getOrg()

        RefdataCategory rdc = null

        if (params.refdatacategory) {
            rdc = RefdataCategory.findById( Long.parseLong(params.refdatacategory) )
        }

        Map<String, Object> map = [
                token       : UUID.randomUUID(),
                category    : params.pd_descr,
                type        : params.pd_type,
                rdc         : rdc?.getDesc(),
                multiple    : (params.pd_multiple_occurrence ? true : false),
                mandatory   : (params.pd_mandatory ? true : false),
                i10n        : [
                        name_de: params.pd_name?.trim(),
                        name_en: params.pd_name?.trim(),
                        expl_de: params.pd_expl?.trim(),
                        expl_en: params.pd_expl?.trim()
                ],
                tenant      : tenant.globalUID]

        PropertyDefinition privatePropDef = PropertyDefinition.construct(map)
        if (privatePropDef.save(flush: true)) {
            return ['message', message(code: 'default.created.message', args:[privatePropDef.descr, privatePropDef.getI10n('name')])]
        }
        else {
            return ['error', message(code: 'default.not.created.message', args:[privatePropDef.descr, privatePropDef.getI10n('name')])]
        }
    }

    /**
     * Delete existing PrivateProperty for this institution
     *
     * @param params
     * @return
     */

    private deletePrivatePropertyDefinition(params) {
        log.debug("delete private property definition for institution: " + params)

        def messages  = ""
        def tenant    = contextService.getOrg()
        def deleteIds = params.list('deleteIds')

        deleteIds.each { did ->
            def id = Long.parseLong(did)
            def privatePropDef = PropertyDefinition.findWhere(id: id, tenant: tenant)
            if (privatePropDef) {

                try {
                    if (privatePropDef.mandatory) {
                        privatePropDef.mandatory = false
                        privatePropDef.save(flush:true)

                        // delete inbetween created mandatories
                        Class.forName(privatePropDef.getImplClass())?.findAllByType(privatePropDef)?.each { it ->
                            it.delete(flush:true)
                        }
                    }
                } catch(Exception e) {
                    log.error( e.toString() )
                }

                privatePropDef.delete(flush:true)
                messages += message(code: 'default.deleted.message', args:[privatePropDef.descr, privatePropDef.name])
            }
        }
        messages
    }

    @Deprecated
    @DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def ajaxEmptySubscription() {
        Map<String, Object> result = setResultGenerics()

        if (result.editable) {

            if(accessService.checkPerm("ORG_INST_COLLECTIVE,ORG_CONSORTIUM")) {
                if(accessService.checkPerm("ORG_CONSORTIUM"))
                    params.comboType = RDStore.COMBO_TYPE_CONSORTIUM.value
                else(accessService.checkPerm("ORG_INST_COLLECTIVE"))
                    params.comboType = RDStore.COMBO_TYPE_DEPARTMENT.value
                def fsq = filterService.getOrgComboQuery(params, result.institution)
                result.members = Org.executeQuery(fsq.query, fsq.queryParams, params)
            }

            result
        }
        render (template: "/templates/filter/orgFilterTable", model: [orgList: result.members, tmplShowCheckbox: true, tmplConfigShow: ['sortname', 'name']])
    }

    @DebugAnnotation(test='hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def copyLicense() {
        Map<String, Object> result = setResultGenerics()

        if(params.id)
        {
            License license = License.get(params.id)
            boolean isEditable = license.isEditableBy(result.user)

            if (! (accessService.checkMinUserOrgRole(result.user, result.institution, 'INST_EDITOR'))) {
                flash.error = message(code:'license.permissionInfo.noPerms')
                response.sendError(401)
                return;
            }

            if(isEditable){
                redirect controller: 'license', action: 'copyLicense', params: [sourceObjectId: genericOIDService.getOID(license), copyObject: true]
            }else {
                flash.error = message(code:'license.permissionInfo.noPerms')
                response.sendError(401)
                return;
            }
        }
    }

    private def getSurveyParticipantCounts(Org participant){
        Map<String, Object> result = [:]

        result.new = SurveyInfo.executeQuery("from SurveyInfo surInfo left join surInfo.surveyConfigs surConfig left join surConfig.propertySet surResult  where surResult.participant = :participant and (surResult.surveyConfig.surveyInfo.status = :status and surResult.id in (select sr.id from SurveyResult sr where sr.surveyConfig  = surveyConfig and sr.dateCreated = sr.lastUpdated and sr.finishDate is null))",
                [status: RDStore.SURVEY_SURVEY_STARTED,
                 participant: participant]).groupBy {it.id[1]}.size()

        result.processed = SurveyInfo.executeQuery("from SurveyInfo surInfo left join surInfo.surveyConfigs surConfig left join surConfig.propertySet surResult  where surResult.participant = :participant and (surResult.surveyConfig.surveyInfo.status = :status and surResult.id in (select sr.id from SurveyResult sr where sr.surveyConfig  = surveyConfig and sr.dateCreated < sr.lastUpdated and sr.finishDate is null))",
                [status: RDStore.SURVEY_SURVEY_STARTED,
                 participant: participant]).groupBy {it.id[1]}.size()

        result.finish = SurveyInfo.executeQuery("from SurveyInfo surInfo left join surInfo.surveyConfigs surConfig left join surConfig.propertySet surResult  where surResult.participant = :participant and (surResult.finishDate is not null)",
                [participant: participant]).groupBy {it.id[1]}.size()

        result.notFinish = SurveyInfo.executeQuery("from SurveyInfo surInfo left join surInfo.surveyConfigs surConfig left join surConfig.propertySet surResult  where surResult.participant = :participant and surResult.finishDate is null and (surResult.surveyConfig.surveyInfo.status in (:status))",
                [status: [RDStore.SURVEY_SURVEY_COMPLETED, RDStore.SURVEY_IN_EVALUATION, RDStore.SURVEY_COMPLETED],
                 participant: participant]).groupBy {it.id[1]}.size()
        return result
    }

    private def getSurveyParticipantCounts_New(Org participant){
        Map<String, Object> result = [:]

        Org contextOrg = contextService.getOrg()
        if (contextOrg.getCustomerType()  == 'ORG_CONSORTIUM') {
            result.new = SurveyInfo.executeQuery("from SurveyInfo surInfo left join surInfo.surveyConfigs surConfig where (exists (select surOrg from SurveyOrg surOrg where surOrg.surveyConfig = surConfig AND surOrg.org = :org and surOrg.finishDate is null and surConfig.pickAndChoose = true and surConfig.surveyInfo.status = :status) " +
                    "or exists (select surResult from SurveyResult surResult where surResult.surveyConfig = surConfig and surConfig.surveyInfo.status = :status and surResult.dateCreated = surResult.lastUpdated and surResult.finishDate is null and surResult.participant = :org)) and surInfo.owner = :owner",
                    [status: RDStore.SURVEY_SURVEY_STARTED,
                     org   : participant,
                     owner : contextOrg]).groupBy { it.id[1] }.size()

            result.processed = SurveyInfo.executeQuery("from SurveyInfo surInfo left join surInfo.surveyConfigs surConfig where (surInfo.status = :status and exists (select surResult from SurveyResult surResult where surResult.surveyConfig = surConfig and surResult.participant = :org and surResult.dateCreated < surResult.lastUpdated and surResult.finishDate is null)) and surInfo.owner = :owner",
                    [status: RDStore.SURVEY_SURVEY_STARTED,
                     org   : participant,
                     owner : contextOrg]).groupBy { it.id[1] }.size()

            result.finish = SurveyInfo.executeQuery("from SurveyInfo surInfo left join surInfo.surveyConfigs surConfig where (exists (select surResult from SurveyResult surResult where surResult.surveyConfig = surConfig and surResult.participant = :org and surResult.finishDate is not null) " +
                    "or exists (select surOrg from SurveyOrg surOrg where surOrg.surveyConfig = surConfig AND surOrg.org = :org and surOrg.finishDate is not null and surConfig.pickAndChoose = true)) and surInfo.owner = :owner",
                    [org  : participant,
                     owner: contextOrg]).groupBy { it.id[1] }.size()

            result.notFinish = SurveyInfo.executeQuery("from SurveyInfo surInfo left join surInfo.surveyConfigs surConfig left join surConfig.orgs surOrgs where surConfig.subSurveyUseForTransfer = false and (surInfo.status in (:status) and exists (select surResult from SurveyResult surResult where surResult.surveyConfig = surConfig and surResult.participant = :org and surResult.finishDate is null)) and surInfo.owner = :owner",
                    [status : [RDStore.SURVEY_SURVEY_COMPLETED, RDStore.SURVEY_IN_EVALUATION, RDStore.SURVEY_COMPLETED],
                     org    : participant,
                     owner  : contextOrg]).groupBy { it.id[1] }.size()

            result.termination = SurveyInfo.executeQuery("from SurveyInfo surInfo left join surInfo.surveyConfigs surConfig left join surConfig.orgs surOrgs where surConfig.subSurveyUseForTransfer = true and (surInfo.status in (:status) and exists (select surResult from SurveyResult surResult where surResult.surveyConfig = surConfig and surResult.participant = :org and surResult.finishDate is null)) and surInfo.owner = :owner",
                    [status : [RDStore.SURVEY_SURVEY_COMPLETED, RDStore.SURVEY_IN_EVALUATION, RDStore.SURVEY_COMPLETED],
                     org    : participant,
                     owner  : contextOrg]).groupBy { it.id[1] }.size()

        }else {

            result.new = SurveyInfo.executeQuery("from SurveyInfo surInfo left join surInfo.surveyConfigs surConfig where (exists (select surOrg from SurveyOrg surOrg where surOrg.surveyConfig = surConfig AND surOrg.org = :org and surOrg.finishDate is null and surConfig.pickAndChoose = true and surConfig.surveyInfo.status = :status)) " +
                    "or (exists (select surResult from SurveyResult surResult where surResult.surveyConfig = surConfig and surConfig.surveyInfo.status = :status and surResult.dateCreated = surResult.lastUpdated and surResult.finishDate is null and surResult.participant = :org))",
                    [status: RDStore.SURVEY_SURVEY_STARTED,
                     org   : participant]).groupBy { it.id[1] }.size()

            result.processed = SurveyInfo.executeQuery("from SurveyInfo surInfo left join surInfo.surveyConfigs surConfig where (surInfo.status = :status and exists (select surResult from SurveyResult surResult where surResult.surveyConfig = surConfig and surResult.participant = :org and surResult.dateCreated < surResult.lastUpdated and surResult.finishDate is null))",
                    [status: RDStore.SURVEY_SURVEY_STARTED,
                     org   : participant]).groupBy { it.id[1] }.size()

            result.finish = SurveyInfo.executeQuery("from SurveyInfo surInfo left join surInfo.surveyConfigs surConfig where (exists (select surResult from SurveyResult surResult where surResult.surveyConfig = surConfig and surResult.participant = :org and surResult.finishDate is not null)) " +
                    "or (exists (select surOrg from SurveyOrg surOrg where surOrg.surveyConfig = surConfig AND surOrg.org = :org and surOrg.finishDate is not null and surConfig.pickAndChoose = true))",
                    [org: participant]).groupBy { it.id[1] }.size()

            result.notFinish = SurveyInfo.executeQuery("from SurveyInfo surInfo left join surInfo.surveyConfigs surConfig left join surConfig.orgs surOrgs where surConfig.subSurveyUseForTransfer = false and (surInfo.status in (:status) and exists (select surResult from SurveyResult surResult where surResult.surveyConfig = surConfig and surResult.participant = :org and surResult.finishDate is null))",
                    [status : [RDStore.SURVEY_SURVEY_COMPLETED, RDStore.SURVEY_IN_EVALUATION, RDStore.SURVEY_COMPLETED],
                     org    : participant]).groupBy { it.id[1] }.size()

            result.termination = SurveyInfo.executeQuery("from SurveyInfo surInfo left join surInfo.surveyConfigs surConfig left join surConfig.orgs surOrgs where surConfig.subSurveyUseForTransfer = true and (surInfo.status in (:status) and exists (select surResult from SurveyResult surResult where surResult.surveyConfig = surConfig and surResult.participant = :org and surResult.finishDate is null))",
                    [status : [RDStore.SURVEY_SURVEY_COMPLETED, RDStore.SURVEY_IN_EVALUATION, RDStore.SURVEY_COMPLETED],
                     org    : participant]).groupBy { it.id[1] }.size()
        }
        return result
    }

    private Map<String, Object> setResultGenerics() {
        Map<String, Object> result = [:]
        switch(params.action){
            case 'currentSurveys':
            case 'surveyInfos':
            case 'surveyInfoFinish':
            case 'surveyInfosIssueEntitlements':
            case 'surveyResultFinish':
                result.user = User.get(springSecurityService.principal.id)
                break
            default:
                result.user = contextService.getUser()
        }
        //result.institution  = Org.findByShortcode(params.shortcode)
        result.institution  = contextService.getOrg()
        result.editable = checkIsEditable(result.user, result.institution)
        result
    }

    private boolean checkIsEditable(User user, Org org){
        boolean isEditable
        switch(params.action){
            case 'ajaxEmptySubscription':
            case 'currentLicenses':
            case 'currentSurveys':
            case 'dashboard':
            case 'emptySubscription':
            case 'surveyInfoFinish':
            case 'surveyResultFinish':
                isEditable = accessService.checkMinUserOrgRole(user, org, 'INST_EDITOR')
                break
            case 'addressbook':
            case 'budgetCodes':
            case 'tasks':
                isEditable = accessService.checkMinUserOrgRole(user, org, 'INST_EDITOR') || SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN')
                break
            case 'surveyInfos':
                isEditable = surveyService.isEditableSurvey(org, SurveyInfo.get(params.id) ?: null)
                break
            case 'surveyInfosIssueEntitlements':
                isEditable = surveyService.isEditableIssueEntitlementsSurvey(org, SurveyConfig.get(params.id))
                break
            case 'userList':
                isEditable = user.hasRole('ROLE_ADMIN') || user.hasAffiliation('INST_ADM')
                break
            case 'managePropertyDefinitions':
                isEditable = false
                break
            default:
                isEditable = accessService.checkMinUserOrgRole(user, org, 'INST_EDITOR') || SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN,ROLE_YODA')
        }
        isEditable
    }

}
