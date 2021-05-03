package de.laser.reporting.export

abstract class AbstractExport {

    static String FIELD_TYPE_PROPERTY           = 'property'
    static String FIELD_TYPE_REFDATA            = 'refdata'
    static String FIELD_TYPE_REFDATA_JOINTABLE  = 'refdataJoinTable'
    static String FIELD_TYPE_CUSTOM_IMPL        = 'customImplementation'

    static String FIELD_TYPE_CUSTOM_IMPL_QDP    = 'customImplementationQDP' // query depending

    static String CSV_VALUE_SEPARATOR   = ';'
    static String CSV_FIELD_SEPARATOR   = ','
    static String CSV_FIELD_QUOTATION   = '"'

    static Map<String, String> CUSTOM_LABEL = [

            'globalUID'                 : 'Link (Global UID)',
            'identifier-assignment'     : 'Identifikatoren',
            'provider-assignment'       : 'Anbieter',
            'property-assignment'       : 'impl @ ExportHelper.getFieldLabel()',
            '___subscription_members'   : 'Anzahl Teilnehmer',          // virtual
            '___license_subscriptions'  : 'Anzahl Lizenzen',            // virtual
            '___license_members'        : 'Anzahl Teilnehmerverträge',  // virtual
            '___org_contact'            : 'Kontaktdaten',               // virtual
    ]

    String token

    Map<String, Object> selectedExportFields = [:]

    abstract Map<String, Object> getAllFields()

    abstract Map<String, Object> getSelectedFields()

    abstract String getFieldLabel(String fieldName)

    abstract List<String> getObject(Long id, Map<String, Object> fields)
}
