package de.laser.exporting

import de.laser.License
import de.laser.helper.DateUtils
import de.laser.helper.RDStore

import java.text.SimpleDateFormat

class LicenseExport extends AbstractExport {

    static String KEY = 'license'

    static Map<String, Object> FIELDS = [

            'endDate'           : [type: FIELD_TYPE_PROPERTY, text: 'Enddatum' ],
            'licenseCategory'   : [type: FIELD_TYPE_REFDATA, text: 'Vertragskategorie' ],
            'startDate'         : [type: FIELD_TYPE_PROPERTY, text: 'Anfangsdatum' ],
            'status'            : [type: FIELD_TYPE_REFDATA, text: 'Vertragstatus' ],
            'type'              : [type: FIELD_TYPE_REFDATA, text: 'Typ' ]
    ]

    LicenseExport (Map<String, Object> fields) {
        selectedExport = getAllFields().findAll{ it.key in fields.keySet() }
    }

    @Override
    Map<String, Object> getAllFields() {
        Map<String, Object> fields = [
                'reference'     : [type: FIELD_TYPE_PROPERTY, text: 'Name' ],
                'globalUID'     : [type: FIELD_TYPE_PROPERTY, text: 'globalUID' ]
        ]
        return fields + FIELDS
    }

    @Override
    Map<String, Object> getSelectedFields() {
        selectedExport
    }

    @Override
    List<String> getObject(Long id, Map<String, Object> fields) {

        License lic = License.get(id)
        List<String> content = []

        fields.each{ f ->
            String key = f.key
            String type = f.value.type

            // --> generic properties
            if (type == FIELD_TYPE_PROPERTY) {

                if (key == 'globalUID') {
                    content.add( lic.getProperty(key) as String )
                }
                else if (License.getDeclaredField(key).getType() == Date) {
                    if (lic.getProperty(key)) {
                        SimpleDateFormat sdf = DateUtils.getSDF_NoTime()
                        content.add( sdf.format( lic.getProperty(key) ) as String )
                    }
                    else {
                        content.add( '' )
                    }
                }
                else if (License.getDeclaredField(key).getType() in [boolean, Boolean]) {
                    if (lic.getProperty(key) == true) {
                        content.add( RDStore.YN_YES.getI10n('value') )
                    }
                    else if (lic.getProperty(key) == false) {
                        content.add( RDStore.YN_NO.getI10n('value') )
                    }
                    else {
                        content.add( '' )
                    }
                }
                else {
                    content.add( lic.getProperty(key) as String )
                }
            }
            // --> generic refdata
            else if (type == FIELD_TYPE_REFDATA) {
                String value = lic.getProperty(key)?.getI10n('value')
                content.add( value ?: '')
            }
            // --> refdata join tables
            else if (type == FIELD_TYPE_REFDATA_JOINTABLE) {
                Set refdata = lic.getProperty(key) as Set
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
