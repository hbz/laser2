package de.laser.exporting

import de.laser.Subscription
import de.laser.helper.DateUtils
import de.laser.helper.RDStore

import java.text.SimpleDateFormat

class SubscriptionExport extends AbstractExport {

    static String KEY = 'subscription'

    static Map<String, Object> FIELDS = [

            'endDate'               : [type: FIELD_TYPE_PROPERTY, text: 'Laufzeit-Ende' ],
            'form'                  : [type: FIELD_TYPE_REFDATA, text: 'Lizenzform' ],
            'hasPerpetualAccess'    : [type: FIELD_TYPE_PROPERTY, text: 'Dauerhafter Zugriff' ],
            'isPublicForApi'        : [type: FIELD_TYPE_PROPERTY, text: 'Freigabe Datenaustausch' ],
            'kind'                  : [type: FIELD_TYPE_REFDATA, text: 'Lizenztyp' ],
            'resource'              : [type: FIELD_TYPE_REFDATA, text: 'Ressourcentyp' ],
            'startDate'             : [type: FIELD_TYPE_PROPERTY, text: 'Laufzeit-Beginn' ],
            'status'                : [type: FIELD_TYPE_REFDATA, text: 'Lizenzstatus' ]
    ]

    SubscriptionExport (Map<String, Object> fields) {
        selectedExport = getAllFields().findAll{ it.key in fields.keySet() }
    }

    @Override
    Map<String, Object> getAllFields() {
        Map<String, Object> fields = [
                'name'              : [type: FIELD_TYPE_PROPERTY, text: 'Name' ],
                'globalUID'         : [type: FIELD_TYPE_PROPERTY, text: 'globalUID' ]
        ]
        return fields + FIELDS
    }

    @Override
    Map<String, Object> getSelectedFields() {
        selectedExport
    }

    @Override
    List<String> getObject(Long id, Map<String, Object> fields) {

        Subscription sub = Subscription.get(id)
        List<String> content = []

        fields.each{ f ->
            String key = f.key
            String type = f.value.type

            // --> generic properties
            if (type == FIELD_TYPE_PROPERTY) {

                if (key == 'globalUID') {
                    content.add( sub.getProperty(key) as String )
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
                String value = sub.getProperty(key)?.getI10n('value')
                content.add( value ?: '')
            }
            // --> refdata join tables
            else if (type == FIELD_TYPE_REFDATA_JOINTABLE) {
                Set refdata = sub.getProperty(key) as Set
                content.add( refdata.collect{ it.getI10n('value') }.join( CSV_VALUE_SEPARATOR ))
            }
            // --> custom filter implementation
            else if (type == FIELD_TYPE_CUSTOM_IMPL) {

                content.add( '* ' + FIELD_TYPE_CUSTOM_IMPL )
            }
            else {
                content.add( '- not implemented -' )
            }
        }

        content
    }
}
