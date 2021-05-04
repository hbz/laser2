package de.laser.reporting.myInstitution

import de.laser.ContextService
import de.laser.Org
import de.laser.reporting.myInstitution.base.BaseConfig
import grails.util.Holders

class OrganisationConfig extends BaseConfig {

    static String KEY = 'organisation'

    static Map<String, Object> CONFIG_ORG_CONSORTIUM = [

            base : [
                    meta : [
                            class: Org
                    ],
                    source : [
                            'all-org'               : 'Alle Organisationen',
                            'all-inst'              : 'Alle Einrichtungen',
                            'all-provider'          : 'Alle Anbieter',
                            'all-agency'            : 'Alle Lieferanten',
                            'all-providerAndAgency' : 'Alle Anbieter und Lieferanten',
                            'my-inst'               : 'Meine Einrichtungen',
                            'my-provider'           : 'Meine Anbieter',
                            'my-agency'             : 'Meine Lieferanten',
                            'my-providerAndAgency'  : 'Meine Anbieter und Lieferanten'
                    ],
                    fields: [
                            'country'           : BaseConfig.FIELD_TYPE_REFDATA,
                            'customerType'      : BaseConfig.FIELD_TYPE_CUSTOM_IMPL,
                            'eInvoice'          : BaseConfig.FIELD_TYPE_PROPERTY,
                            'funderHskType'     : BaseConfig.FIELD_TYPE_REFDATA,
                            'funderType'        : BaseConfig.FIELD_TYPE_REFDATA,
                            'legalInfo'         : BaseConfig.FIELD_TYPE_CUSTOM_IMPL,
                            'libraryNetwork'    : BaseConfig.FIELD_TYPE_REFDATA,
                            'libraryType'       : BaseConfig.FIELD_TYPE_REFDATA,
                            'orgType'           : BaseConfig.FIELD_TYPE_REFDATA_JOINTABLE,
                            //'region'            : FIELD_TYPE_REFDATA,
                            'subjectGroup'      : BaseConfig.FIELD_TYPE_CUSTOM_IMPL,
                    ],
                    filter : [
                            default: [
                                    [ 'country',  'subjectGroup', 'orgType' ],
                                    [ 'libraryNetwork', 'funderType', 'funderHskType' ] ,
                                    [ 'libraryType', 'eInvoice' ],
                                    [ 'customerType', 'legalInfo' ]
                            ],
                            provider: [
                                    // all disabled
                            ]
                    ],
                    query : [
                            'Organisation' : [ // TODO ..
                                    'org-orgType'           : 'Organisationstyp',
                                    'org-customerType'      : 'Kundentyp',
                                    'org-libraryType'       : 'Bibliothekstyp',
                                    'org-region'            : 'Bundesländer',
                                    'org-subjectGroup'      : 'Fächergruppen',
                                    'org-libraryNetwork'    : 'Verbundzugehörigkeit',
                                    'org-funderType'        : 'Unterhaltsträger',
                                    'org-funderHskType'     : 'Trägerschaft'
                            ]
                    ],
                    query2 : [
                            'Verteilung' : [ // TODO ..
                                    'org-property-assignment' : [
                                            label: 'Organisation → Merkmale (eigene/allgemeine)',
                                            template: '2axis2values',
                                            chartLabels: [ 'Organisationen', 'Vergebene Merkmale (eigene/allgemeine)' ]
                                            ],
                                    'org-identifier-assignment' : [
                                            label : 'Organisation → Identifikatoren',
                                            template: '2axis2values_nonMatches',
                                            chartLabels: [ 'Organisationen', 'Vergebene Identifikatoren' ]
                                    ]
                                    //'org-serverAccess-assignment' : 'Organisation nach Datenweitergabe',
                            ]
                    ]
            ]
    ]

    static Map<String, Object> CONFIG_ORG_INST = [

            base : [
                    meta : [
                            class: Org
                    ],
                    source : [
                            'all-consortium'        : 'Alle Konsortialstellen',
                            'all-provider'          : 'Alle Anbieter',
                            'all-agency'            : 'Alle Lieferanten',
                            'all-providerAndAgency' : 'Alle Anbieter und Lieferanten',
                            'my-consortium'         : 'Meine Konsortialstellen',
                            'my-provider'           : 'Meine Anbieter',
                            'my-agency'             : 'Meine Lieferanten',
                            'my-providerAndAgency'  : 'Meine Anbieter und Lieferanten'
                    ],
                    fields: [
                            'country'           : BaseConfig.FIELD_TYPE_REFDATA,
                            'customerType'      : BaseConfig.FIELD_TYPE_CUSTOM_IMPL,
                            'eInvoice'          : BaseConfig.FIELD_TYPE_PROPERTY,
                            'funderHskType'     : BaseConfig.FIELD_TYPE_REFDATA,
                            'funderType'        : BaseConfig.FIELD_TYPE_REFDATA,
                            'legalInfo'         : BaseConfig.FIELD_TYPE_CUSTOM_IMPL,
                            'libraryNetwork'    : BaseConfig.FIELD_TYPE_REFDATA,
                            'libraryType'       : BaseConfig.FIELD_TYPE_REFDATA,
                            'orgType'           : BaseConfig.FIELD_TYPE_REFDATA_JOINTABLE,
                            //'region'            : FIELD_TYPE_REFDATA,
                            'subjectGroup'      : BaseConfig.FIELD_TYPE_CUSTOM_IMPL,
                    ],
                    filter : [
                            default: [
                                    [ 'country',  'subjectGroup', 'orgType' ],
                                    [ 'libraryNetwork', 'funderType', 'funderHskType' ] ,
                                    [ 'libraryType', 'eInvoice' ],
                                    [ 'customerType', 'legalInfo' ]
                            ],
                            provider: [
                                    // all disabled
                            ]
                    ],
                    query : [
                            'Organisation' : [ // TODO ..
                                               'org-orgType'           : 'Organisationstyp',
                                               'org-customerType'      : 'Kundentyp',
                                               'org-libraryType'       : 'Bibliothekstyp',
                                               'org-region'            : 'Bundesländer',
                                               'org-subjectGroup'      : 'Fächergruppen',
                                               'org-libraryNetwork'    : 'Verbundzugehörigkeit',
                                               'org-funderType'        : 'Unterhaltsträger',
                                               'org-funderHskType'     : 'Trägerschaft'
                            ]
                    ],
                    query2 : [
                            'Verteilung' : [ // TODO ..
                                             'org-property-assignment' : [
                                                     label: 'Organisation → Merkmale (eigene/allgemeine)',
                                                     template: '2axis2values',
                                                     chartLabels: [ 'Organisationen', 'Vergebene Merkmale (eigene/allgemeine)' ]
                                             ],
                                             'org-identifier-assignment' : [
                                                     label : 'Organisation → Identifikatoren',
                                                     template: '2axis2values_nonMatches',
                                                     chartLabels: [ 'Organisationen', 'Vergebene Identifikatoren' ]
                                             ]
                                             //'org-serverAccess-assignment' : 'Organisation nach Datenweitergabe',
                            ]
                    ]
            ]
    ]

    static Map<String, Object> getCurrentConfig() {
        ContextService contextService = (ContextService) Holders.grailsApplication.mainContext.getBean('contextService')

        if (contextService.getOrg().getCustomerType() == 'ORG_CONSORTIUM') {
            OrganisationConfig.CONFIG_ORG_CONSORTIUM
        }
        else if (contextService.getOrg().getCustomerType() == 'ORG_INST') {
            OrganisationConfig.CONFIG_ORG_INST
        }
    }
}
