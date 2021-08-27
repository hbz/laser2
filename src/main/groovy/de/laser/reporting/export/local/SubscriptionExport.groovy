package de.laser.reporting.export.local

import de.laser.ContextService
import de.laser.Identifier
import de.laser.LinksGenerationService
import de.laser.Org
import de.laser.Subscription
import de.laser.helper.DateUtils
import de.laser.helper.RDStore
import de.laser.reporting.export.base.BaseExport
import de.laser.reporting.myInstitution.base.BaseDetails
import grails.util.Holders
import org.grails.plugins.web.taglib.ApplicationTagLib

import java.text.SimpleDateFormat

class SubscriptionExport extends BaseExport {

    static String KEY = 'subscription'

    static Map<String, Object> CONFIG_ORG_CONSORTIUM = [

            base : [
                    meta : [
                            class: Subscription
                    ],
                    fields : [
                            default: [
                                    'globalUID'             : FIELD_TYPE_PROPERTY,
                                    'name'                  : FIELD_TYPE_PROPERTY,
                                    'startDate'             : FIELD_TYPE_PROPERTY,
                                    'endDate'               : FIELD_TYPE_PROPERTY,
                                    'status'                : FIELD_TYPE_REFDATA,
                                    'kind'                  : FIELD_TYPE_REFDATA,
                                    'form'                  : FIELD_TYPE_REFDATA,
                                    'resource'              : FIELD_TYPE_REFDATA,
                                    '@ae-subscription-member'   : FIELD_TYPE_CUSTOM_IMPL,     // virtual
                                    '@ae-subscription-prevNext' : FIELD_TYPE_CUSTOM_IMPL,     // virtual
                                    'x-provider'            : FIELD_TYPE_CUSTOM_IMPL,
                                    'hasPerpetualAccess'    : FIELD_TYPE_PROPERTY,
                                    'hasPublishComponent'   : FIELD_TYPE_PROPERTY,
                                    'isPublicForApi'        : FIELD_TYPE_PROPERTY,
                                    'x-identifier'          : FIELD_TYPE_CUSTOM_IMPL,
                                    'x-property'            : FIELD_TYPE_CUSTOM_IMPL_QDP,   // qdp
                            ]
                    ]
            ]
    ]

    static Map<String, Object> CONFIG_ORG_INST = [

            base : [
                    meta : [
                            class: Subscription
                    ],
                    fields : [
                            default: [
                                    'globalUID'             : FIELD_TYPE_PROPERTY,
                                    'name'                  : FIELD_TYPE_PROPERTY,
                                    'startDate'             : FIELD_TYPE_PROPERTY,
                                    'endDate'               : FIELD_TYPE_PROPERTY,
                                    'status'                : FIELD_TYPE_REFDATA,
                                    'kind'                  : FIELD_TYPE_REFDATA,
                                    'form'                  : FIELD_TYPE_REFDATA,
                                    'resource'              : FIELD_TYPE_REFDATA,
                                    '@ae-subscription-prevNext' : FIELD_TYPE_CUSTOM_IMPL,     // virtual
                                    'x-provider'            : FIELD_TYPE_CUSTOM_IMPL,
                                    'hasPerpetualAccess'    : FIELD_TYPE_PROPERTY,
                                    'hasPublishComponent'   : FIELD_TYPE_PROPERTY,
                                    'isPublicForApi'        : FIELD_TYPE_PROPERTY,
                                    'x-identifier'          : FIELD_TYPE_CUSTOM_IMPL,
                                    'x-property'            : FIELD_TYPE_CUSTOM_IMPL_QDP,   // qdp
                            ]
                    ]
            ]
    ]

    SubscriptionExport(String token, Map<String, Object> fields) {
        this.token = token

        // keeping order ..
        getAllFields().keySet().each { k ->
            if (k in fields.keySet() ) {
                selectedExportFields.put(k, fields.get(k))
            }
        }
        ExportLocalHelper.normalizeSelectedMultipleFields( this )
    }

    @Override
    Map<String, Object> getSelectedFields() {
        selectedExportFields
    }

    @Override
    String getFieldLabel(String fieldName) {
        ExportLocalHelper.getFieldLabel( this, fieldName )
    }

    @Override
    List<String> getObject(Object obj, Map<String, Object> fields) {

        ApplicationTagLib g = Holders.grailsApplication.mainContext.getBean(ApplicationTagLib)
        ContextService contextService = (ContextService) Holders.grailsApplication.mainContext.getBean('contextService')
        LinksGenerationService linksGenerationService = (LinksGenerationService) Holders.grailsApplication.mainContext.getBean('linksGenerationService')

        Subscription sub = obj as Subscription
        List<String> content = []

        fields.each{ f ->
            String key = f.key
            String type = getAllFields().get(f.key)

            // --> generic properties
            if (type == FIELD_TYPE_PROPERTY) {

                if (key == 'globalUID') {
                    content.add( g.createLink( controller: 'subscription', action: 'show', absolute: true ) + '/' + sub.getProperty(key) as String )
                }
                else if (Subscription.getDeclaredField(key).getType() == Date) {
                    if (sub.getProperty(key)) {
                        SimpleDateFormat sdf = DateUtils.getSDF_NoTime()
                        content.add( sdf.format( sub.getProperty(key) ) as String )
                    }
                    else {
                        content.add( '' )
                    }
                }
                else if (Subscription.getDeclaredField(key).getType() in [boolean, Boolean]) {
                    if (sub.getProperty(key) == true) {
                        content.add( RDStore.YN_YES.getI10n('value') )
                    }
                    else if (sub.getProperty(key) == false) {
                        content.add( RDStore.YN_NO.getI10n('value') )
                    }
                    else {
                        content.add( '' )
                    }
                }
                else {
                    content.add( sub.getProperty(key) as String)
                }
            }
            // --> generic refdata
            else if (type == FIELD_TYPE_REFDATA) {
                String rdv = sub.getProperty(key)?.getI10n('value')
                content.add( rdv ?: '')
            }
            // --> refdata join tables
            else if (type == FIELD_TYPE_REFDATA_JOINTABLE) {
                Set refdata = sub.getProperty(key) as Set
                content.add( refdata.collect{ it.getI10n('value') }.join( CSV_VALUE_SEPARATOR ))
            }
            // --> custom filter implementation
            else if (type == FIELD_TYPE_CUSTOM_IMPL) {

                if (key == 'x-identifier') {
                    List<Identifier> ids = []

                    if (f.value) {
                        ids = Identifier.executeQuery( "select i from Identifier i where i.value != null and i.value != '' and i.sub = :sub and i.ns.id in (:idnsList)",
                                [sub: sub, idnsList: f.value] )
                    }
                    content.add( ids.collect{ (it.ns.getI10n('name') ?: it.ns.ns + ' *') + ':' + it.value }.join( CSV_VALUE_SEPARATOR ))
                }
                else if (key == 'x-provider') {
                    List<Org> plts = Org.executeQuery('select ro.org from OrgRole ro where ro.sub.id = :id and ro.roleType in (:roleTypes)',
                            [id: sub.id, roleTypes: [RDStore.OR_PROVIDER]]
                    )
                    content.add( plts.collect{ it.name }.join( CSV_VALUE_SEPARATOR ))
                }
                else if (key == '@ae-subscription-member') {
                    List<Org> members = Subscription.executeQuery(
                            'select distinct oo.org from Subscription sub join sub.orgRelations oo where sub = :sub and oo.roleType in :subscriberRoleTypes',
                            [sub: sub, subscriberRoleTypes: [RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIBER_CONS_HIDDEN]]
                    )
                    content.add( members.collect{ it.name }.join( CSV_VALUE_SEPARATOR ) )
                }
                else if (key == '@ae-subscription-prevNext') {

                    Map<String, List> navMap = linksGenerationService.generateNavigation(sub, false)
                    content.add(
                            (navMap.prevLink ? RDStore.YN_YES.getI10n('value') : RDStore.YN_NO.getI10n('value'))
                            + ' / ' +
                            (navMap.nextLink ? RDStore.YN_YES.getI10n('value') : RDStore.YN_NO.getI10n('value'))
                    )
                }
            }
            // --> custom query depending filter implementation
            else if (type == FIELD_TYPE_CUSTOM_IMPL_QDP) {

                if (key == 'x-property') {
                    Long pdId = ExportLocalHelper.getDetailsCache(token).id as Long

                    List<String> properties = BaseDetails.resolvePropertiesGeneric(sub, pdId, contextService.getOrg())
                    content.add( properties.findAll().join( CSV_VALUE_SEPARATOR ) ) // removing empty and null values
                }
            }
            else {
                content.add( '- not implemented -' )
            }
        }

        content
    }
}