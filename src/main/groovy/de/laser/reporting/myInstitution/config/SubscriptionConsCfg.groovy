package de.laser.reporting.myInstitution.config

import de.laser.Org
import de.laser.Subscription
import de.laser.reporting.myInstitution.base.BaseConfig

class SubscriptionConsCfg extends BaseConfig {

    static Map<String, Object> CONFIG = [

            key : KEY_SUBSCRIPTION,

            base : [
                    meta : [
                            class: Subscription
                    ],
                    source : [
                            'consortia-sub'
                    ],
                    fields : [
                            'annual'                : BaseConfig.FIELD_TYPE_CUSTOM_IMPL,
                            'endDateLimit'          : BaseConfig.FIELD_TYPE_CUSTOM_IMPL,
                            'form'                  : BaseConfig.FIELD_TYPE_REFDATA,
                            'hasPerpetualAccess'    : BaseConfig.FIELD_TYPE_PROPERTY,
                            'hasPublishComponent'   : BaseConfig.FIELD_TYPE_PROPERTY,
                            'isPublicForApi'        : BaseConfig.FIELD_TYPE_PROPERTY,
                            'isMultiYear'           : BaseConfig.FIELD_TYPE_PROPERTY,
                            'kind'                  : BaseConfig.FIELD_TYPE_REFDATA,
                            'resource'              : BaseConfig.FIELD_TYPE_REFDATA,
                            'startDateLimit'        : BaseConfig.FIELD_TYPE_CUSTOM_IMPL,
                            'status'                : BaseConfig.FIELD_TYPE_REFDATA,
                            //'type'                : FIELD_TYPE_REFDATA,
                            //'manualRenewalDate'       : FIELD_TYPE_PROPERTY,
                            //'manualCancellationDate'  : FIELD_TYPE_PROPERTY
                    ],
                    filter : [
                            default : [
                                    [ 'form', 'kind', 'status' ],
                                    [ 'resource', 'hasPublishComponent', 'hasPerpetualAccess' ],
                                    [ 'isPublicForApi', 'isMultiYear' ],
                                    [ 'startDateLimit', 'endDateLimit', 'annual' ]
                            ]
                    ],
                    query : [
                            default : [
                                    'Lizenz' : [
                                            'subscription-form',
                                            'subscription-kind',
                                            'subscription-resource',
                                            'subscription-status',
                                            'subscription-isMultiYear',
                                            'subscription-manualCancellationDate',
                                            'subscription-*'
                                    ]
                            ]
                    ],
                    query2 : [
                            'Verteilung' : [ // TODO ..
                                     'subscription-x-identifier' : [
                                             label               : 'Identifikatoren → Lizenz',
                                             detailsTemplate     : 'subscription',
                                             chartTemplate       : '2axis2values_nonMatches',
                                             chartLabels         : [ 'Lizenzen', 'Vergebene Identifikatoren' ]
                                     ],
                                     'subscription-x-property' : [
                                            label               : 'Merkmale (eigene/allgemeine) → Lizenz',
                                            detailsTemplate     : 'subscription',
                                            chartTemplate       : '2axis2values',
                                            chartLabels         : [ 'Lizenzen', 'Vergebene Merkmale (eigene/allgemeine)' ]
                                     ],
                                     'subscription-x-annual' : [
                                             label              : 'Jahresring → Lizenz',
                                             detailsTemplate    : 'subscription',
                                             chartTemplate      : 'generic',
                                             chartLabels        : []
                                     ],
                                     'subscription-x-memberAnnual' : [
                                             label              : 'Jahresring → Teilnehmerlizenz',
                                             detailsTemplate    : 'subscription',
                                             chartTemplate      : 'generic',
                                             chartLabels        : []
                                     ],
                                     'subscription-x-provider' : [
                                             label               : 'Anbieter → Lizenz',
                                             detailsTemplate     : 'subscription',
                                             chartTemplate       : 'generic',
                                             chartLabels         : []
                                     ],
                                     'subscription-x-memberProvider' : [
                                             label              : 'Anbieter → Lizenz → Teilnehmerlizenz',
                                             detailsTemplate    : 'subscription',
                                             chartTemplate      : 'generic',
                                             chartLabels        : []
                                     ],
//                                     'subscription-x-memberCost-TODO' : [
//                                             label              : 'Anbieter → Lizenz → Teilnehmerkosten',
//                                             detailsTemplate    : 'TODO',
//                                             chartTemplate      : 'generic',
//                                             chartLabels        : []
//                                     ],
                                     'subscription-x-platform' : [
                                             label               : 'Plattform → Anbieter → Lizenz',
                                             detailsTemplate     : 'subscription',
                                             chartTemplate       : '2axis2values_nonMatches',
                                             chartLabels         : [ 'Ermittelt durch Bestand', 'Zuordnung über Anbieter' ]
                                     ],
                                     'subscription-x-memberSubscription' : [
                                             label              : 'Lizenz → Teilnehmerlizenz',
                                             detailsTemplate    : 'subscription',
                                             chartTemplate      : 'generic',
                                             chartLabels        : []
                                     ],
                                     'subscription-x-member' : [
                                             label              : 'Lizenz → Teilnehmerlizenz → Teilnehmer',
                                             detailsTemplate    : 'organisation',
                                             chartTemplate      : 'generic',
                                             chartLabels        : []
                                     ]
                            ]
                    ]
            ],

            memberSubscription : [
                    meta : [
                            class: Subscription
                    ],
                    source : [
                            'depending-memberSubscription'
                    ],
                    fields : [
                            'annual'                : BaseConfig.FIELD_TYPE_CUSTOM_IMPL,
                            'endDateLimit'          : BaseConfig.FIELD_TYPE_CUSTOM_IMPL,
                            'form'                  : BaseConfig.FIELD_TYPE_REFDATA,
                            'hasPerpetualAccess'    : BaseConfig.FIELD_TYPE_PROPERTY,
                            'hasPublishComponent'   : BaseConfig.FIELD_TYPE_PROPERTY,
                            'isPublicForApi'        : BaseConfig.FIELD_TYPE_PROPERTY,
                            'isMultiYear'           : BaseConfig.FIELD_TYPE_PROPERTY,
                            'kind'                  : BaseConfig.FIELD_TYPE_REFDATA,
                            'resource'              : BaseConfig.FIELD_TYPE_REFDATA,
                            'startDateLimit'        : BaseConfig.FIELD_TYPE_CUSTOM_IMPL,
                            'status'                : BaseConfig.FIELD_TYPE_REFDATA,
                    ],
                    filter : [
                            default : [
                                    [ 'form', 'kind', 'status' ],
                                    [ 'resource', 'hasPublishComponent', 'hasPerpetualAccess' ],
                                    [ 'isPublicForApi', 'isMultiYear' ],
                                    [ 'startDateLimit', 'endDateLimit', 'annual' ]
                            ]
                    ],
                    query : [
                            default: [
                                    'Teilnehmerlizenz' : [
                                                 'memberSubscription-form',
                                                 'memberSubscription-kind',
                                                 'memberSubscription-resource',
                                                 'memberSubscription-status',
                                                 'memberSubscription-isMultiYear',
                                                 'memberSubscription-manualCancellationDate',
                                                 'memberSubscription-*'
                                    ]
                            ]
                    ]
            ],

            member : [
                    meta : [
                            class: Org
                    ],
                    source : [
                            'depending-member'
                    ],
                    fields : [
                            'country'           : BaseConfig.FIELD_TYPE_REFDATA,
                            'region'            : BaseConfig.FIELD_TYPE_REFDATA,
                            'customerType'      : BaseConfig.FIELD_TYPE_CUSTOM_IMPL,
                            'eInvoice'          : BaseConfig.FIELD_TYPE_PROPERTY,
                            'funderHskType'     : BaseConfig.FIELD_TYPE_REFDATA,
                            'funderType'        : BaseConfig.FIELD_TYPE_REFDATA,
                            'legalInfo'         : BaseConfig.FIELD_TYPE_CUSTOM_IMPL,
                            'libraryNetwork'    : BaseConfig.FIELD_TYPE_REFDATA,
                            'libraryType'       : BaseConfig.FIELD_TYPE_REFDATA,
                            'orgType'           : BaseConfig.FIELD_TYPE_REFDATA_JOINTABLE,
                            //'region'            : FIELD_TYPE_REFDATA,
                            'subjectGroup'      : BaseConfig.FIELD_TYPE_CUSTOM_IMPL
                    ],
                    filter : [
                            default : [
                                    [ 'country', 'region', 'subjectGroup', 'libraryType' ],
                                    [ 'libraryNetwork', 'funderType', 'funderHskType' ],
                                    [ 'orgType', 'eInvoice' ],
                                    [ 'customerType', 'legalInfo' ]
                            ]
                    ],
                    query : [
                            default : [
                                    'Teilnehmer' : [
                                            'member-orgType',
                                            'member-customerType',
                                            'member-libraryType',
                                            'member-region',
                                            'member-subjectGroup',
                                            'member-libraryNetwork',
                                            'member-funderType',
                                            'member-funderHskType',
                                            'member-*'
                                    ]
                            ]
                    ]
            ],

            provider : [
                    meta : [
                            class: Org
                    ],
                    source : [
                            'depending-provider'
                    ],
                    fields : [
                            'country'   : BaseConfig.FIELD_TYPE_REFDATA,
                            'region'    : BaseConfig.FIELD_TYPE_REFDATA,
                            'orgType'   : BaseConfig.FIELD_TYPE_REFDATA_JOINTABLE
                    ],
                    filter : [
                            default : []
                    ],
                    query : [
                            default : [
                                    'Anbieter' : [ // TODO ..
                                            'provider-orgType',
                                            'provider-*'
                                            //'provider-country',
                                            //'provider-region'
                                    ]
                            ]
                    ]
            ],

            agency : [
                    meta : [
                            class: Org
                    ],
                    source : [
                            'depending-agency'
                    ],
                    fields : [
                            'country'   : BaseConfig.FIELD_TYPE_REFDATA,
                            'region'    : BaseConfig.FIELD_TYPE_REFDATA,
                            'orgType'   : BaseConfig.FIELD_TYPE_REFDATA_JOINTABLE
                    ],
                    filter : [
                            default : []
                    ],
                    query : [
                            default : [
                                    'Lieferant' : [
                                            'agency-orgType',
                                            'agency-*'
                                    ]
                            ]
                    ]
            ]
    ]
}
