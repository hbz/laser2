package de.laser.reporting.report.myInstitution.config

import de.laser.Org
import de.laser.reporting.report.myInstitution.base.BaseConfig

class OrganisationConsCfg extends BaseConfig {

    static Map<String, Object> CONFIG = [

            base : [
                    meta : [
                            class:  Org,
                            cfgKey: KEY_ORGANISATION
                    ],
                    source : [
                            'all-org',
                            'all-inst',
                            'all-provider',
                            'all-agency',
                            'all-providerAndAgency',
                            'my-inst',
                            'my-provider',
                            'my-agency',
                            'my-providerAndAgency'
                    ],
                    fields : [
                            'country'           : [ FIELD_TYPE_REFDATA ],
                            'region'            : [ FIELD_TYPE_REFDATA, FIELD_IS_VIRTUAL ],
                            'customerType'      : [ FIELD_TYPE_CUSTOM_IMPL ],   // TODO custom_impl
                            'eInvoice'          : [ FIELD_TYPE_PROPERTY ],
                            'funderHskType'     : [ FIELD_TYPE_REFDATA ],
                            'funderType'        : [ FIELD_TYPE_REFDATA ],
                            'legalInfo'         : [ FIELD_TYPE_CUSTOM_IMPL ],   // TODO custom_impl
                            'libraryNetwork'    : [ FIELD_TYPE_REFDATA ],
                            'libraryType'       : [ FIELD_TYPE_REFDATA ],
                            'orgType'           : [ FIELD_TYPE_REFDATA_JOINTABLE ],
                            'propertyKey'       : [ FIELD_TYPE_CUSTOM_IMPL ],   // TODO custom_impl
                            'propertyValue'     : [ FIELD_TYPE_CUSTOM_IMPL ],   // TODO custom_impl
                            'subjectGroup'      : [ FIELD_TYPE_CUSTOM_IMPL ]    // TODO custom_impl
                    ],
                    filter : [
                            default : [
                                    [ 'country', 'region', 'subjectGroup', 'libraryType' ],
                                    [ 'libraryNetwork', 'funderType', 'funderHskType' ] ,
                                    [ 'orgType', 'eInvoice' ],
                                    [ 'customerType', 'legalInfo' ],
                                    [ 'propertyKey', 'propertyValue' ]
                            ],
                            provider : [ // TODO : provider != agency
                                    // all disabled
                            ]
                    ],
                    query : [
                            default : [
                                    org : [
                                            'org-orgType' :         [ 'generic.org.orgType' ],
                                            'org-customerType' :    [ 'generic.org.customerType' ],
                                            'org-libraryType' :     [ 'generic.org.libraryType' ],
                                            'org-region' :          [ 'generic.org.region' ],
                                            'org-subjectGroup' :    [ 'generic.org.subjectGroup' ],
                                            'org-libraryNetwork' :  [ 'generic.org.libraryNetwork' ],
                                            'org-funderType' :      [ 'generic.org.funderType' ],
                                            'org-funderHskType' :   [ 'generic.org.funderHskType' ],
                                            'org-*' :               [ 'generic.all' ]
                                    ]
                            ],
                            providerAndAgency : [
                                    org : [
                                            'org-orgType': [ 'generic.org.orgType' ]
                                    ]
                            ],
                            provider : [
                                    org : [
                                            'org-orgType': [ 'generic.org.orgType' ]
                                    ]
                            ],
                            agency : [
                                    org : [
                                            'org-orgType': [ 'generic.org.orgType' ]
                                    ]
                            ]
                    ],
                    distribution : [
                            default : [
                                    'org-x-identifier' : [
                                             detailsTemplate     : 'organisation',
                                             chartTemplate       : '2axis2values_nonMatches',
                                             chartLabels         : [ 'base', 'x.identifiers' ]
                                    ],
                                    'org-x-property' : [
                                            detailsTemplate     : 'organisation',
                                            chartTemplate       : '2axis3values',
                                            chartLabels         : [ 'base', 'x.properties.2', 'x.properties.3' ]
                                   ]
                                    //'org-x-serverAccess' : 'Organisation nach Datenweitergabe',
                            ]
                    ]
            ]
    ]
}
