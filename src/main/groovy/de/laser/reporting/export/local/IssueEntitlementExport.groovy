package de.laser.reporting.export.local

import de.laser.ContextService
import de.laser.Identifier
import de.laser.IssueEntitlement
import de.laser.helper.DateUtils
import de.laser.helper.RDStore
import de.laser.reporting.export.base.BaseExport
import grails.util.Holders
import org.grails.plugins.web.taglib.ApplicationTagLib

import java.text.SimpleDateFormat

class IssueEntitlementExport extends BaseExport {

    static String KEY = 'entitlement'

    static Map<String, Object> CONFIG_X = [

            base : [
                    meta : [
                            class: IssueEntitlement
                    ],
                    fields : [
                            default: [
                                    'globalUID'             : FIELD_TYPE_PROPERTY,
                                    '@ae-entitlement-tippName'              : FIELD_TYPE_CUSTOM_IMPL,   // virtual
                                    '@ae-entitlement-tippTitleType'         : FIELD_TYPE_CUSTOM_IMPL,   // virtual
                                    'medium'                : FIELD_TYPE_REFDATA,
                                    'acceptStatus'          : FIELD_TYPE_REFDATA,
                                    'status'                : FIELD_TYPE_REFDATA,
                                    '@ae-entitlement-tippFirstAuthor'       : FIELD_TYPE_CUSTOM_IMPL,   // virtual
                                    '@ae-entitlement-tippEditionStatement'  : FIELD_TYPE_CUSTOM_IMPL,   // virtual
                                    '@ae-entitlement-tippPublisherName'     : FIELD_TYPE_CUSTOM_IMPL,   // virtual
                                    '@ae-entitlement-tippSeriesName'        : FIELD_TYPE_CUSTOM_IMPL,   // virtual
                                    '@ae-entitlement-tippSubjectReference'  : FIELD_TYPE_CUSTOM_IMPL,   // virtual
                                    '@ae-entitlement-tippHostPlatformURL'   : FIELD_TYPE_CUSTOM_IMPL,   // virtual
                                    '@ae-entitlement-tippIdentifier'        : FIELD_TYPE_CUSTOM_IMPL    // virtual
                            ]
                    ]
            ]
    ]

    IssueEntitlementExport(String token, Map<String, Object> fields) {
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

        IssueEntitlement ie = obj as IssueEntitlement
        List<String> content = []

        fields.each{ f ->
            String key = f.key
            String type = getAllFields().get(f.key)

            // --> generic properties
            if (type == FIELD_TYPE_PROPERTY) {

                if (key == 'globalUID') {
                    content.add( g.createLink( controller: 'issueEntitlement', action: 'show', absolute: true ) + '/' + ie.getProperty(key) as String )
                }
                else if (IssueEntitlement.getDeclaredField(key).getType() == Date) {
                    if (ie.getProperty(key)) {
                        SimpleDateFormat sdf = DateUtils.getSDF_NoTime()
                        content.add( sdf.format( ie.getProperty(key) ) as String )
                    }
                    else {
                        content.add( '' )
                    }
                }
                else if (IssueEntitlement.getDeclaredField(key).getType() in [boolean, Boolean]) {
                    if (ie.getProperty(key) == true) {
                        content.add( RDStore.YN_YES.getI10n('value') )
                    }
                    else if (ie.getProperty(key) == false) {
                        content.add( RDStore.YN_NO.getI10n('value') )
                    }
                    else {
                        content.add( '' )
                    }
                }
                else {
                    content.add( ie.getProperty(key) as String )
                }
            }
            // --> generic refdata
            else if (type == FIELD_TYPE_REFDATA) {
                String rdv = ie.getProperty(key)?.getI10n('value')
                content.add( rdv ?: '')
            }
            // --> refdata join tables
            else if (type == FIELD_TYPE_REFDATA_JOINTABLE) {
                Set refdata = ie.getProperty(key) as Set
                content.add( refdata.collect{ it.getI10n('value') }.join( CSV_VALUE_SEPARATOR ))
            }
            // --> custom filter implementation
            else if (type == FIELD_TYPE_CUSTOM_IMPL) {

                if (key == '@ae-entitlement-tippName') {
                    content.add( ie.tipp.name ?: '' )
                }
                else if (key == '@ae-entitlement-tippEditionStatement') {
                    content.add( ie.tipp.editionStatement ?: '' )
                }
                else if (key == '@ae-entitlement-tippFirstAuthor') {
                    content.add( ie.tipp.firstAuthor ?: '' )
                }
                else if (key == '@ae-entitlement-tippHostPlatformURL') {
                    content.add( ie.tipp.hostPlatformURL ?: '' )
                }
                else if (key == '@ae-entitlement-tippIdentifier') {
                    List<Identifier> ids = []

                    if (f.value) {
                        ids = Identifier.executeQuery( "select i from Identifier i where i.value != null and i.value != '' and i.tipp = :tipp and i.ns.id in (:idnsList)",
                                [tipp: ie.tipp, idnsList: f.value] )
                    }
                    content.add( ids.collect{ (it.ns.getI10n('name') ?: it.ns.ns + ' *') + ':' + it.value }.join( CSV_VALUE_SEPARATOR ))
                }
                else if (key == '@ae-entitlement-tippPublisherName') {
                    content.add( ie.tipp.publisherName ?: '' )
                }
                else if (key == '@ae-entitlement-tippSeriesName') {
                    content.add( ie.tipp.seriesName ?: '' )
                }
                else if (key == '@ae-entitlement-tippSubjectReference') {
                    content.add( ie.tipp.subjectReference ?: '' )
                }
                else if (key == '@ae-entitlement-tippTitleType') {
                    content.add( ie.tipp.titleType ?: '' )
                }
            }
            else {
                content.add( '- not implemented -' )
            }
        }

        content
    }
}