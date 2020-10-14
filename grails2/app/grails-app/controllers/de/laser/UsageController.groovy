package de.laser


import com.k_int.kbplus.auth.User
import de.laser.controller.AbstractDebugController
import grails.plugin.springsecurity.annotation.Secured
import grails.transaction.Transactional
import org.hibernate.criterion.CriteriaSpecification

@Secured(['IS_AUTHENTICATED_FULLY'])
class UsageController extends AbstractDebugController {

    def statsSyncService
    def factService
    def contextService
    def springSecurityService

    static transactional = false

    @Secured(['ROLE_STATISTICS_EDITOR','ROLE_ADMIN'])
    def index() {
        def result = initResult()

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0

        // criteria and totalCount for PageResultList Object seems to be problematic with projections and aggregation
        // use extra hql query for now, TODO only use hql base query and move the query out of this method

        String hql = "select stc.supplierId, stc.customerId, min(stc.availFrom), max(stc.availTo), stc.factType.id from StatsTripleCursor as stc"
        String groupCondition = " group by stc.supplierId, stc.customerId, stc.factType.id"
        ArrayList whereConditions = []
        LinkedHashMap<String,Object> queryParams = [:]
        if (params.supplier){
            whereConditions.add('supplierId=:supplierId')
            queryParams += [supplierId: params.supplier]
        }
        if (params.institution) {
            whereConditions.add('customerId=:customerId')
            queryParams += [customerId: params.institution]
        }
        if (!whereConditions.empty) {
            hql += " where " + whereConditions.join(' and ')
        }
        hql += groupCondition
        /* needed if we remove the criteria
        if ((params.sort != null) && (params.sort.length() > 0)) {
           //numFact has to be addressed seperatly (todo)
            hql += " order by stc.${params.sort} ${params.order}"
        } else {
            hql += " order by stc.supplierId asc"
        }*/
        ArrayList totalResultIds = StatsTripleCursor.executeQuery(hql, queryParams)

        List<HashMap> results = StatsTripleCursor.createCriteria().list(max: result.max, offset: result.offset) {
            projections {
                groupProperty('supplierId', 'supplierId')
                groupProperty('customerId', 'customerId')
                groupProperty('factType', 'factType')
                min('availFrom', 'availFrom')
                max('availTo', 'availTo')
                sum('numFacts', 'numFacts')
            }
            if (params.supplier) {
                eq("supplierId", params.supplier)
            }
            if (params.institution) {
                eq("customerId", params.institution)
            }
            if ((params.sort != null) && (params.sort.length() > 0)) {
                order(params.sort, params.order)
            } else {
                order("supplierId", "asc")
            }

            resultTransformer(CriteriaSpecification.ALIAS_TO_ENTITY_MAP)
        }

        result.availStatsRanges = results
        result.num_stc_rows = totalResultIds.size()
        result
    }

    private initResult()
    {
        Map<String, Object> result = [:]
        result.statsSyncService = [:]
        result.statsSyncService.running = statsSyncService.running
        result.statsSyncService.submitCount = statsSyncService.submitCount
        result.statsSyncService.completedCount = statsSyncService.completedCount
        result.statsSyncService.newFactCount = statsSyncService.newFactCount
        result.statsSyncService.totalTime = statsSyncService.totalTime
        result.statsSyncService.threads = statsSyncService.THREAD_POOL_SIZE
        result.statsSyncService.queryTime = statsSyncService.queryTime
        result.statsSyncService.activityHistogram = statsSyncService.activityHistogram
        result.statsSyncService.syncStartTime = statsSyncService.syncStartTime
        result.statsSyncService.syncElapsed = statsSyncService.syncElapsed

        result.institution = contextService.getOrg()
        result.institutionList = factService.institutionsWithRequestorIDAndAPIKey()
        result.user = User.get(springSecurityService.principal.id)

        ArrayList platformsWithNatstatId = factService.platformsWithNatstatId()

        ArrayList providerList = []
        if (!result.institutionList.isEmpty()) {
            String joinedInstitutions = result.institutionList.id.join(',')
            platformsWithNatstatId.each {
                String hql = "select s.id from Subscription s join s.orgRelations as institution " +
                    "where institution.org.id in (${joinedInstitutions}) and exists (select 1 from IssueEntitlement as ie INNER JOIN ie.tipp.platform  as platform where ie.subscription=s and platform.id=:platform_id)"
                ArrayList subsWithIssueEntitlements = Subscription.executeQuery(hql, [platform_id: it.id])
                LinkedHashMap<String,Object> listItem = [:]
                listItem.id = it.id
                listItem.name = it.name
                listItem.optionDisabled = (subsWithIssueEntitlements.size() == 0)
                providerList.add(listItem)
            }
        }
        result.providerList = providerList
        result.institutionsWithFacts = factService.getFactInstitutionList()
        result.providersWithFacts = factService.getFactProviderList()
        result.natstatProviders = StatsTripleCursor.withCriteria {
            projections {
                distinct("supplierId")
            }
            order("supplierId", "asc")
        }
        String institutionsForQuery = StatsTripleCursor.withCriteria {
            projections {
                distinct("customerId")
            }
            order("customerId", "asc")
        }.collect {"'$it'"}.join(',')

        String hql = "select ident.org, ident from Identifier as ident where ident.value in (${institutionsForQuery})"
        result.natstatInstitutions = institutionsForQuery ? Org.executeQuery(hql) : []
        result.cursorCount = factService.getSupplierCursorCount()

        if (statsSyncService.errors) {
            flash.error = statsSyncService.errors.join('</br>')
        }
        statsSyncService.errors = []
        return result
    }

    @Secured(['ROLE_STATISTICS_EDITOR','ROLE_ADMIN'])
    def abort()
    {
        def result = initResult()
        statsSyncService.setErrors([])
        statsSyncService.running = false
        redirect(view: "index", model: result)
    }

    @Secured(['ROLE_STATISTICS_EDITOR','ROLE_ADMIN'])
    def fetchSelection()
    {
        // TODO when we switch to global API Key / Requestor, query SUSHI Service status endpoint here
        // Do not continue if service is not active or there is an error with the API Credentials.
        statsSyncService.setErrors([])
        def result = initResult()
        statsSyncService.addFilters(params)
        statsSyncService.doSync()
        if (statsSyncService.errors) {
            flash.error = statsSyncService.errors.join('</br>')
        }
        redirect(view: "index", model: result)
    }

    @Secured(['ROLE_STATISTICS_EDITOR','ROLE_ADMIN'])
    @Transactional
    def deleteAll()
    {
        statsSyncService.setErrors([])
        def result = initResult()
        Fact.executeUpdate('delete from Fact')
        StatsTripleCursor.executeUpdate('delete from StatsTripleCursor ')
        flash.message = message(code: 'default.usage.delete.success')
        redirect(view: "index", model: result)
    }

    @Secured(['ROLE_STATISTICS_EDITOR','ROLE_ADMIN'])
    @Transactional
    def deleteSelection()
    {
        statsSyncService.setErrors([])
        def result = initResult()
        def wibid, supplier, platform, instOrg

        if (params.supplier != 'null'){
            platform = Platform.get(params.supplier)
            def cp = platform.propertySet.find(){
                it.type.name = "NatStat Supplier ID"
            }
            supplier = cp.stringValue
        }
        if (params.institution != 'null'){
            instOrg = Org.get(params.institution)
            wibid = instOrg?.getIdentifierByType('wibid')?.value
        }
        def factAndWhereCondition = ''
        def cursorAndWhereCondition = ''
        def factParams = [:]
        def cursorParams = [:]

        if (supplier) {
            factAndWhereCondition += " and t1.supplier = :supplier_id"
            cursorAndWhereCondition += " and t1.supplierId =:supplierName"
            factParams.supplier_id = platform
            cursorParams.supplierName = supplier
        }
        if (wibid) {
            factAndWhereCondition += " and t1.inst = :customer_id"
            cursorAndWhereCondition += " and t1.customerId = :customerName"
            factParams.customer_id = instOrg
            cursorParams.customerName = wibid
        }
        def deletedCursorCount = StatsTripleCursor.executeUpdate('delete from StatsTripleCursor t1 where 1=1' + cursorAndWhereCondition,
            cursorParams)
        def deletedFactCount = Fact.executeUpdate('delete from Fact t1 where 1=1' + factAndWhereCondition,
            factParams)
        log.debug("Deleted ${deletedCursorCount} entries from StatsTripleCursor table and ${deletedFactCount} entries from fact table")
        flash.message = message(code: 'default.usage.delete.success')
        redirect(view: "index", model: result)
    }

}
