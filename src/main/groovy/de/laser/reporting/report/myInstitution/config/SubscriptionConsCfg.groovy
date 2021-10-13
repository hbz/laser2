package de.laser.reporting.report.myInstitution.config

import de.laser.Org
import de.laser.Subscription
import de.laser.reporting.report.myInstitution.base.BaseConfig

class SubscriptionConsCfg extends BaseConfig {

    static Map<String, Object> CONFIG = [

            base : [
                    meta : [
                            class:  Subscription,
                            cfgKey: KEY_SUBSCRIPTION
                    ],
                    source : [
                            'consortia-sub'
                    ],
                    fields : [
                            'annual'                : FIELD_TYPE_CUSTOM_IMPL,
                            'endDateLimit'          : FIELD_TYPE_CUSTOM_IMPL,
                            'form'                  : FIELD_TYPE_REFDATA,
                            'hasPerpetualAccess'    : FIELD_TYPE_PROPERTY,
                            'hasPublishComponent'   : FIELD_TYPE_PROPERTY,
                            'isPublicForApi'        : FIELD_TYPE_PROPERTY,
                            'isMultiYear'           : FIELD_TYPE_PROPERTY,
                            'kind'                  : FIELD_TYPE_REFDATA,
                            'resource'              : FIELD_TYPE_REFDATA,
                            'startDateLimit'        : FIELD_TYPE_CUSTOM_IMPL,
                            'status'                : FIELD_TYPE_REFDATA,
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
                                    'subscription' : [
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
                            'distribution' : [
                                     'subscription-x-identifier' : [
                                             detailsTemplate     : 'subscription',
                                             chartTemplate       : '2axis2values_nonMatches',
                                             chartLabels         : [ 'base', 'x.identifiers' ]
                                     ],
                                     'subscription-x-property' : [
                                            detailsTemplate     : 'subscription',
                                            chartTemplate       : '2axis2values',
                                            chartLabels         : [ 'base', 'x.properties' ]
                                     ],
                                     'subscription-x-annual' : [
                                             detailsTemplate    : 'subscription',
                                             chartTemplate      : 'annual',
                                             chartLabels        : []
                                     ],
                                     'subscription-x-memberAnnual' : [
                                             detailsTemplate    : 'subscription',
                                             chartTemplate      : 'annual',
                                             chartLabels        : []
                                     ],
                                     'subscription-x-provider' : [
                                             detailsTemplate     : 'subscription',
                                             chartTemplate       : 'generic',
                                             chartLabels         : []
                                     ],
                                     'subscription-x-memberProvider' : [
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
                                             detailsTemplate     : 'subscription',
                                             chartTemplate       : '2axis2values_nonMatches',
                                             chartLabels         : [ 'x.platforms.1', 'x.platforms.2' ]
                                     ],
                                     'subscription-x-memberSubscription' : [
                                             detailsTemplate    : 'subscription',
                                             chartTemplate      : 'generic',
                                             chartLabels        : []
                                     ],
                                     'subscription-x-member' : [
                                             detailsTemplate    : 'organisation',
                                             chartTemplate      : 'generic',
                                             chartLabels        : []
                                     ]
                            ]
                    ]
            ],

            memberSubscription : [
                    meta : [
                            class:  Subscription,
                            cfgKey: KEY_SUBSCRIPTION
                    ],
                    source : [
                            'depending-memberSubscription'
                    ],
                    fields : [
                            'annual'                : FIELD_TYPE_CUSTOM_IMPL,
                            'endDateLimit'          : FIELD_TYPE_CUSTOM_IMPL,
                            'form'                  : FIELD_TYPE_REFDATA,
                            'hasPerpetualAccess'    : FIELD_TYPE_PROPERTY,
                            'hasPublishComponent'   : FIELD_TYPE_PROPERTY,
                            'isPublicForApi'        : FIELD_TYPE_PROPERTY,
                            'isMultiYear'           : FIELD_TYPE_PROPERTY,
                            'kind'                  : FIELD_TYPE_REFDATA,
                            'resource'              : FIELD_TYPE_REFDATA,
                            'startDateLimit'        : FIELD_TYPE_CUSTOM_IMPL,
                            'status'                : FIELD_TYPE_REFDATA,
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
                                    'memberSubscription' : [
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
                            class:  Org,
                            cfgKey: KEY_SUBSCRIPTION
                    ],
                    source : [
                            'depending-member'
                    ],
                    fields : [
                            'country'           : FIELD_TYPE_REFDATA,
                            'region'            : FIELD_TYPE_REFDATA,
                            'customerType'      : FIELD_TYPE_CUSTOM_IMPL,
                            'eInvoice'          : FIELD_TYPE_PROPERTY,
                            'funderHskType'     : FIELD_TYPE_REFDATA,
                            'funderType'        : FIELD_TYPE_REFDATA,
                            'legalInfo'         : FIELD_TYPE_CUSTOM_IMPL,
                            'libraryNetwork'    : FIELD_TYPE_REFDATA,
                            'libraryType'       : FIELD_TYPE_REFDATA,
                            'orgType'           : FIELD_TYPE_REFDATA_JOINTABLE,
                            //'region'            : FIELD_TYPE_REFDATA,
                            'subjectGroup'      : FIELD_TYPE_CUSTOM_IMPL
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
                                    'member' : [
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
                            class:  Org,
                            cfgKey: KEY_SUBSCRIPTION
                    ],
                    source : [
                            'depending-provider'
                    ],
                    fields : [
                            'country'   : FIELD_TYPE_REFDATA,
                            'region'    : FIELD_TYPE_REFDATA,
                            'orgType'   : FIELD_TYPE_REFDATA_JOINTABLE
                    ],
                    filter : [
                            default : []
                    ],
                    query : [
                            default : [
                                    'provider' : [
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
                            class:  Org,
                            cfgKey: KEY_SUBSCRIPTION
                    ],
                    source : [
                            'depending-agency'
                    ],
                    fields : [
                            'country'   : FIELD_TYPE_REFDATA,
                            'region'    : FIELD_TYPE_REFDATA,
                            'orgType'   : FIELD_TYPE_REFDATA_JOINTABLE
                    ],
                    filter : [
                            default : []
                    ],
                    query : [
                            default : [
                                    'agency' : [
                                            'agency-orgType',
                                            'agency-*'
                                    ]
                            ]
                    ]
            ]
    ]
}