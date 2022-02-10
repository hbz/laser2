package de.laser.reporting.report.myInstitution

import de.laser.*
import de.laser.helper.DateUtils
import de.laser.helper.RDStore
import de.laser.reporting.report.ElasticSearchHelper
import de.laser.reporting.report.GenericHelper
import de.laser.reporting.report.myInstitution.base.BaseConfig
import de.laser.reporting.report.myInstitution.base.BaseFilter
import grails.util.Holders
import grails.web.servlet.mvc.GrailsParameterMap
import org.springframework.context.ApplicationContext

@Deprecated
class IssueEntitlementFilter extends BaseFilter {

    static int TMP_QUERY_CONSTRAINT = 2000

    static Map<String, Object> filter(GrailsParameterMap params) {
        // notice: params is cloned
        Map<String, Object> filterResult = [ labels: [:], data: [:] ]

        List<String> queryParts         = [ 'select distinct (ie.id) from IssueEntitlement ie']
        List<String> whereParts         = [ 'where ie.id in (:issueEntitlementIdList)']
        Map<String, Object> queryParams = [ issueEntitlementIdList: [] ]

        ApplicationContext mainContext = Holders.grailsApplication.mainContext
        ContextService contextService  = mainContext.getBean('contextService')
        SubscriptionsQueryService subscriptionsQueryService = mainContext.getBean('subscriptionsQueryService')

        String filterSource = getCurrentFilterSource(params, BaseConfig.KEY_ISSUEENTITLEMENT)
        filterResult.labels.put('base', [source: BaseConfig.getMessage(BaseConfig.KEY_ISSUEENTITLEMENT + '.source.' + filterSource)])

        switch (filterSource) {
//            case 'all-ie':
//                queryParams.issueEntitlementIdList = IssueEntitlement.executeQuery( 'select ie.id from IssueEntitlement ie' )
//                break
            case 'my-ie':
                List tmp = subscriptionsQueryService.myInstitutionCurrentSubscriptionsBaseQuery([validOn: null], contextService.getOrg())
                List<Long> subIdList = Subscription.executeQuery( 'select s.id ' + tmp[0], tmp[1])
                subIdList = Subscription.executeQuery( "select s.id from Subscription s where s.status.value != 'Deleted' and s.id in (:subIdList)", [subIdList: subIdList])

                queryParams.issueEntitlementIdList = IssueEntitlement.executeQuery(
                        'select distinct(ie.id) from IssueEntitlement ie join ie.subscription sub where sub.id in (:subscriptionIdList) ' +
                                'and ie.status = :ieStatus and ie.acceptStatus = :ieAcceptStatus',
                        [subscriptionIdList: subIdList, ieStatus: RDStore.TIPP_STATUS_CURRENT, ieAcceptStatus: RDStore.IE_ACCEPT_STATUS_FIXED]
                )
                break
            case 'my-ie-deleted':
                List tmp = subscriptionsQueryService.myInstitutionCurrentSubscriptionsBaseQuery([validOn: null], contextService.getOrg())
                List<Long> subIdList = Subscription.executeQuery( 'select s.id ' + tmp[0], tmp[1])

                queryParams.issueEntitlementIdList = IssueEntitlement.executeQuery(
                        'select distinct(ie.id) from IssueEntitlement ie join ie.subscription sub where sub.id in (:subscriptionIdList)',
                        [subscriptionIdList: subIdList]
                )
                break
        }

        String cmbKey = BaseConfig.FILTER_PREFIX + BaseConfig.KEY_ISSUEENTITLEMENT + '_'
        int pCount = 0

        getCurrentFilterKeys(params, cmbKey).each { key ->
            //println key + " >> " + params.get(key)

            if (params.get(key)) {
                String p = key.replaceFirst(cmbKey,'')
                String pType = GenericHelper.getFieldType(BaseConfig.getCurrentConfig( BaseConfig.KEY_ISSUEENTITLEMENT ).base, p)

                def filterLabelValue

                // --> properties generic
                if (pType == BaseConfig.FIELD_TYPE_PROPERTY) {
                    if (IssueEntitlement.getDeclaredField(p).getType() == Date) {

                        String modifier = getDateModifier( params.get(key + '_modifier') )

                        whereParts.add( 'ie.' + p + ' ' + modifier + ' :p' + (++pCount) )
                        queryParams.put( 'p' + pCount, DateUtils.parseDateGeneric(params.get(key)) )

                        filterLabelValue = getDateModifier(params.get(key + '_modifier')) + ' ' + params.get(key)
                    }
                    else if (IssueEntitlement.getDeclaredField(p).getType() in [boolean, Boolean]) {
                        RefdataValue rdv = RefdataValue.get(params.long(key))

                        if (rdv == RDStore.YN_YES)     { whereParts.add( 'ie.' + p + ' is true' ) }
                        else if (rdv == RDStore.YN_NO) { whereParts.add( 'ie.' + p + ' is false' ) }

                        filterLabelValue = rdv.getI10n('value')
                    }
                    else {
                        queryParams.put( 'p' + pCount, params.get(key) )
                        filterLabelValue = params.get(key)
                    }
                }
                // --> refdata generic
                else if (pType == BaseConfig.FIELD_TYPE_REFDATA) {
                    whereParts.add( 'ie.' + p + '.id = :p' + (++pCount) )
                    queryParams.put( 'p' + pCount, params.long(key) )

                    filterLabelValue = RefdataValue.get(params.long(key)).getI10n('value')
                }
                // --> refdata join tables
                else if (pType == BaseConfig.FIELD_TYPE_REFDATA_JOINTABLE) {
                    println ' --- ' + pType +' not implemented --- '
                }
                // --> custom implementation
                else if (pType == BaseConfig.FIELD_TYPE_CUSTOM_IMPL) {
                    if (p == BaseConfig.CUSTOM_IMPL_KEY_IE_TIPP_PACKAGE) {
                        queryParts.add('TitleInstancePackagePlatform tipp')
                        whereParts.add('ie.tipp = tipp and tipp.' + p + '.id = :p' + (++pCount))
                        //whereParts.add( 'ie.' + p + '.id in (:p' + (++pCount) + ')')
                        queryParams.put('p' + pCount, params.long(key) )

                        filterLabelValue = de.laser.Package.get(params.long(key)).name
                    }
                    else if (p == BaseConfig.CUSTOM_IMPL_KEY_IE_TIPP_PKG_PLATFORM) {
                        queryParts.add('TitleInstancePackagePlatform tipp')
                        queryParts.add('Package pkg')  // status !!
                        whereParts.add('ie.tipp = tipp and tipp.pkg = pkg and pkg.nominalPlatform.id = :p' + (++pCount))
                        //whereParts.add( 'ie.' + p + '.id in (:p' + (++pCount) + ')')
                        queryParams.put('p' + pCount, params.long(key) )

                        filterLabelValue = Platform.get(params.long(key)).name
                    }
                    else if (p == BaseConfig.CUSTOM_IMPL_KEY_IE_PROVIDER) {
                        queryParts.add('TitleInstancePackagePlatform tipp')
                        queryParts.add('Package pkg')  // status !!
                        queryParts.add('OrgRole ro')

                        whereParts.add('ie.tipp = tipp and tipp.pkg = ro.pkg and ro.org.id = :p' + (++pCount))
                        queryParams.put('p' + pCount, params.long(key))

                        whereParts.add('ro.roleType in (:p'  + (++pCount) + ')')
                        queryParams.put('p' + pCount, [RDStore.OR_PROVIDER, RDStore.OR_CONTENT_PROVIDER])

                        filterLabelValue = Org.get(params.long(key)).name
                    }
                    else if (p == BaseConfig.CUSTOM_IMPL_KEY_IE_SUBSCRIPTION) {
                        whereParts.add('ie.' + p + '.id = :p' + (++pCount))
                        //whereParts.add( 'ie.' + p + '.id in (:p' + (++pCount) + ')')
                        queryParams.put('p' + pCount, params.long(key) )

                        filterLabelValue = Subscription.get(params.long(key)).getLabel()
                    }
                    else {
                        println ' --- ' + pType + ' not implemented --- '
                    }
                }

                if (filterLabelValue) {
                    filterResult.labels.get('base').put(p, [label: GenericHelper.getFieldLabel(BaseConfig.getCurrentConfig( BaseConfig.KEY_ISSUEENTITLEMENT ).base, p), value: filterLabelValue])
                }
            }
        }

        String query = queryParts.unique().join(' , ') + ' ' + whereParts.join(' and ')

        //println 'IssueEntitlementFilter.filter() -->'
        //println query
        //println queryParams
        //println whereParts

        Set<Long> tmpIdSet = []
        List<Long> tmp = queryParams.issueEntitlementIdList.clone() as List
        while (tmp) {
            queryParams.issueEntitlementIdList = tmp.take(TMP_QUERY_CONSTRAINT)
            tmp = tmp.drop(TMP_QUERY_CONSTRAINT) as List<Long>
            tmpIdSet.addAll( IssueEntitlement.executeQuery( query, queryParams ))
        }
        List<Long> idList = tmpIdSet.sort().toList().take(TMP_QUERY_CONSTRAINT)

        filterResult.data.put( BaseConfig.KEY_ISSUEENTITLEMENT + 'IdList', idList) // postgresql: out-of-range

        BaseConfig.getCurrentConfig( BaseConfig.KEY_ISSUEENTITLEMENT ).keySet().each{ pk ->
            if (pk != 'base') {
                if (pk == 'subscription') {
                    _handleInternalSubscriptionFilter(pk, filterResult)
                }
                else if (pk == 'package') {
                    _handleInternalPackageFilter(pk, filterResult)
                }
                else if (pk == 'provider') {
                    _handleInternalOrgFilter(pk, filterResult)
                }
                else if (pk == 'platform') {
                    _handleInternalPlatformFilter(pk, filterResult)
                }
            }
        }

        // -- ES --

        List<Long> pkgIdList = filterResult.data.getAt( BaseConfig.KEY_PACKAGE + 'IdList') as List
        ElasticSearchHelper.handleEsRecords( BaseConfig.KEY_PACKAGE, pkgIdList, cmbKey, filterResult, params )

        List<Long> pltIdList = filterResult.data.getAt( BaseConfig.KEY_PLATFORM + 'IdList') as List
        ElasticSearchHelper.handleEsRecords( BaseConfig.KEY_PLATFORM, pltIdList, cmbKey, filterResult, params )

        filterResult
    }

    static void _handleInternalSubscriptionFilter(String partKey, Map<String, Object> filterResult) {
        if (! filterResult.data.get('issueEntitlementIdList')) { filterResult.data.put( partKey + 'IdList', [] ) }

        String queryBase = 'select distinct(ie.subscription.id) from IssueEntitlement ie'
        List<String> whereParts = [ 'ie.id in (:issueEntitlementIdList)' ]

        Map<String, Object> queryParams = [ issueEntitlementIdList: filterResult.data.issueEntitlementIdList ]

        String query = queryBase + ' where ' + whereParts.join(' and ')
        filterResult.data.put( partKey + 'IdList', queryParams.issueEntitlementIdList ? Subscription.executeQuery(query, queryParams) : [] )
    }

    static void _handleInternalPackageFilter(String partKey, Map<String, Object> filterResult) {
        if (! filterResult.data.get('issueEntitlementIdList')) { filterResult.data.put( partKey + 'IdList', [] ) }

        String queryBase = 'select distinct (pkg.id) from SubscriptionPackage subPkg join subPkg.pkg pkg join subPkg.subscription sub join sub.issueEntitlements ie'
        List<String> whereParts = [ 'ie.id in (:issueEntitlementIdList)' ]

        Map<String, Object> queryParams = [ issueEntitlementIdList: filterResult.data.issueEntitlementIdList ]

        String query = queryBase + ' where ' + whereParts.join(' and ')
        filterResult.data.put( partKey + 'IdList', queryParams.issueEntitlementIdList ? Package.executeQuery(query, queryParams) : [] )
    }

    static void _handleInternalOrgFilter(String partKey, Map<String, Object> filterResult) {
        if (! filterResult.data.get('packageIdList')) { filterResult.data.put( partKey + 'IdList', [] ) }

        String queryBase = 'select distinct (org.id) from OrgRole ro join ro.pkg pkg join ro.org org'
        List<String> whereParts = [ 'pkg.id in (:packageIdList)', 'ro.roleType in (:roleTypes)' ]

        Map<String, Object> queryParams = [ packageIdList: filterResult.data.packageIdList, roleTypes: [RDStore.OR_PROVIDER, RDStore.OR_CONTENT_PROVIDER] ]

        String query = queryBase + ' where ' + whereParts.join(' and ')
        filterResult.data.put( partKey + 'IdList', queryParams.packageIdList ? Org.executeQuery(query, queryParams) : [] )
    }

    static void _handleInternalPlatformFilter(String partKey, Map<String, Object> filterResult) {
        if (! filterResult.data.get('packageIdList')) { filterResult.data.put( partKey + 'IdList', [] ) }

        String queryBase = 'select distinct (plt.id) from Package pkg join pkg.nominalPlatform plt'
        List<String> whereParts = [ 'pkg.id in (:packageIdList)' ]

        Map<String, Object> queryParams = [ packageIdList: filterResult.data.packageIdList ]

        String query = queryBase + ' where ' + whereParts.join(' and ')
        filterResult.data.put( partKey + 'IdList', queryParams.packageIdList ? Platform.executeQuery(query, queryParams) : [] )
    }
}