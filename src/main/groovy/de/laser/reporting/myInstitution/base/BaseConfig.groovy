package de.laser.reporting.myInstitution.base

import de.laser.ContextService
import de.laser.RefdataCategory
import de.laser.auth.Role
import de.laser.helper.RDConstants
import de.laser.reporting.myInstitution.CostItemConfig
import de.laser.reporting.myInstitution.config.LicenseConsortium
import de.laser.reporting.myInstitution.config.LicenseInst
import de.laser.reporting.myInstitution.config.OrganisationConsortium
import de.laser.reporting.myInstitution.config.OrganisationInst
import de.laser.reporting.myInstitution.config.SubscriptionConsortium
import de.laser.reporting.myInstitution.config.SubscriptionInst
import grails.util.Holders
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder

class BaseConfig {

    static String KEY                           = 'myInstitution'

    static String KEY_COSTITEM                  = 'costItem'
    static String KEY_LICENSE                   = 'license'
    static String KEY_ORGANISATION              = 'organisation'
    static String KEY_SUBSCRIPTION              = 'subscription'

    static String FILTER_PREFIX                 = 'filter:'
    static String FILTER_SOURCE_POSTFIX         = '_source'

    static String CHART_BAR                     = 'bar'
    static String CHART_PIE                     = 'pie'
    static String CHART_RADAR                   = 'radar'

    static String FIELD_TYPE_PROPERTY           = 'property'
    static String FIELD_TYPE_REFDATA            = 'refdata'
    static String FIELD_TYPE_REFDATA_JOINTABLE  = 'refdataJoinTable'
    static String FIELD_TYPE_CUSTOM_IMPL        = 'customImplementation'

    static String CUSTOM_KEY_SUBJECT_GROUP      = 'subjectGroup'
    static String CUSTOM_KEY_ORG_TYPE           = 'orgType'
    static String CUSTOM_KEY_CUSTOMER_TYPE      = 'customerType'
    static String CUSTOM_KEY_LEGAL_INFO         = 'legalInfo'
    static String CUSTOM_KEY_ANNUAL             = 'annual'

    static Map<String, String> FILTER = [

            organisation : 'Organisationen',
            subscription : 'Lizenzen',
            license      : 'Verträge',
            // costItem     : 'Kosten (* experimentelle Funktion)'
    ]

    static Map<String, String> CHARTS = [

            bar   : 'Balkendiagramm',
            pie   : 'Tortendiagramm',
            //radar : 'Netzdiagramm'
    ]

    static Map<String, Object> getCurrentConfig(String key) {
        ContextService contextService = (ContextService) Holders.grailsApplication.mainContext.getBean('contextService')

        if (key == KEY_COSTITEM) {

            CostItemConfig.CONFIG_X
        }
        else if (key == KEY_LICENSE) {

            if (contextService.getOrg().getCustomerType() == 'ORG_CONSORTIUM') {
                LicenseConsortium.CONFIG
            }
            else if (contextService.getOrg().getCustomerType() == 'ORG_INST') {
                LicenseInst.CONFIG
            }
        }
        else if (key == KEY_ORGANISATION) {

            if (contextService.getOrg().getCustomerType() == 'ORG_CONSORTIUM') {
                OrganisationConsortium.CONFIG
            }
            else if (contextService.getOrg().getCustomerType() == 'ORG_INST') {
                OrganisationInst.CONFIG
            }
        }
        else if (key == KEY_SUBSCRIPTION) {

            if (contextService.getOrg().getCustomerType() == 'ORG_CONSORTIUM') {
                SubscriptionConsortium.CONFIG
            }
            else if (contextService.getOrg().getCustomerType() == 'ORG_INST') {
                SubscriptionInst.CONFIG
            }
        }
    }

    static Map<String, Object> getCustomRefdata(String key) {

        MessageSource messageSource = Holders.grailsApplication.mainContext.getBean('messageSource')
        Locale locale = LocaleContextHolder.getLocale()

        if (key == CUSTOM_KEY_SUBJECT_GROUP) {
            return [
                    label: messageSource.getMessage('org.subjectGroup.label', null, locale),
                    from: RefdataCategory.getAllRefdataValues(RDConstants.SUBJECT_GROUP)
            ]
        }
        else if (key == CUSTOM_KEY_ORG_TYPE) {
            return [
                    label: messageSource.getMessage('org.orgType.label', null, locale),
                    from: RefdataCategory.getAllRefdataValues(RDConstants.ORG_TYPE)
            ]
        }
        else if (key == CUSTOM_KEY_CUSTOMER_TYPE) {
            List<Role> roles = Role.findAllByRoleType('org')
            return [
                    label: messageSource.getMessage('org.setting.CUSTOMER_TYPE', null, locale),
                    from: roles.collect{[ id: it.id, value_de: it.getI10n('authority') ] }
            ]
        }
        else if (key == CUSTOM_KEY_LEGAL_INFO) {
            return [
                    label: 'Erstellt bzw. organisiert durch ..', // TODO
                    from: [
                        [id: 0, value_de: 'Keine Einträge'],
                        [id: 1, value_de: 'Erstellt von / Organisiert durch (beides)'], // ui icon green check circle
                        [id: 2, value_de: 'Erstellt von (exklusive)'],                  // ui icon grey outline circle
                        [id: 3, value_de: 'Organisiert durch (exklusive)']              // ui icon red question mark
            ]]
        }
        else if (key == CUSTOM_KEY_ANNUAL) {
            return [
                    label: 'Jahresring',
                    from: (2023..2017).collect{[ id: it, value_de: it] }  // TODO hardcoded
            ]
        }
    }
}
