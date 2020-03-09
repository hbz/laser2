package de.laser.api.v0


import com.k_int.kbplus.License
import com.k_int.kbplus.OnixplLicense
import com.k_int.kbplus.Org
import de.laser.api.v0.entities.ApiDoc
import de.laser.helper.Constants
import groovy.util.logging.Log4j

@Log4j
class ApiReader {

    static SUPPORTED_FORMATS = [
            'costItem':             [Constants.MIME_APPLICATION_JSON],
            'document':             [],
            'issueEntitlements':    [Constants.MIME_TEXT_PLAIN, Constants.MIME_APPLICATION_JSON],
            'license':              [Constants.MIME_APPLICATION_JSON],
            'onixpl':               [Constants.MIME_APPLICATION_XML],
            'oaMonitor':            [Constants.MIME_APPLICATION_JSON],
            'oaMonitorList':        [Constants.MIME_APPLICATION_JSON],
            'organisation':         [Constants.MIME_APPLICATION_JSON],
            'package':              [Constants.MIME_APPLICATION_JSON],
            'propertyList':         [Constants.MIME_APPLICATION_JSON],
            'refdataList':          [Constants.MIME_APPLICATION_JSON],
            'statistic':            [Constants.MIME_APPLICATION_JSON],
            'statisticList':        [Constants.MIME_APPLICATION_JSON],
            'subscription':         [Constants.MIME_APPLICATION_JSON]
    ]

    static SIMPLE_QUERIES = ['oaMonitorList', 'refdataList', 'propertyList', 'statisticList']


    // ##### CONSTANTS #####

    final static NO_CONSTRAINT          = "NO_CONSTRAINT"
    final static LICENSE_STUB           = "LICENSE_STUB"

    // type of stub to return
    final static PACKAGE_STUB           = "PACKAGE_STUB"
    final static SUBSCRIPTION_STUB      = "SUBSCRIPTION_STUB"

    // ignoring relations
    final static IGNORE_ALL             = "IGNORE_ALL"  // cutter for nested objects
    final static IGNORE_NONE            = "IGNORE_NONE" // placeholder, if needed
    final static IGNORE_CLUSTER         = "IGNORE_CLUSTER"

    final static IGNORE_LICENSE         = "IGNORE_LICENSE"
    final static IGNORE_ORGANISATION    = "IGNORE_ORGANISATION"
    final static IGNORE_PACKAGE         = "IGNORE_PACKAGE"
    final static IGNORE_SUBSCRIPTION    = "IGNORE_SUBSCRIPTION"
    final static IGNORE_TITLE           = "IGNORE_TITLE"
    final static IGNORE_TIPP            = "IGNORE_TIPP"

    final static IGNORE_SUBSCRIPTION_AND_PACKAGE = "IGNORE_SUBSCRIPTION_AND_PACKAGE"

    final static IGNORE_CUSTOM_PROPERTIES   = "IGNORE_CUSTOM_PROPERTIES"
    final static IGNORE_PRIVATE_PROPERTIES  = "IGNORE_PRIVATE_PROPERTIES"


    /**
     * Access rights due wrapping license
     *
     * @param com.k_int.kbplus.OnixplLicense opl
     * @param com.k_int.kbplus.License lic
     * @param com.k_int.kbplus.Org context
     * @return Map | Constants.HTTP_FORBIDDEN
     */
    static requestOnixplLicense(OnixplLicense opl, License lic, Org context) {
        Map<String, Object> result = [:]
        boolean hasAccess = false

        if (!opl) {
            return null
        }

        if (opl.getLicenses().contains(lic)) {
            lic.orgLinks.each { orgRole ->
                // TODO check orgRole.roleType
                if (orgRole.getOrg().id == context?.id) {
                    hasAccess = true
                }
            }
        }

        if (hasAccess) {
            //result.id       = opl.id
            result.lastmod  = opl.lastmod
            result.title    = opl.title

            // References
            result.document = ApiDoc.getDocumentMap(opl.doc) // com.k_int.kbplus.Doc
            //result.licenses = ApiStubReader.resolveLicenseStubs(opl.licenses) // com.k_int.kbplus.License
            //result.xml = opl.xml // XMLDoc // TODO
            result = ApiToolkit.cleanUp(result, true, true)
        }

        return (hasAccess ? result : Constants.HTTP_FORBIDDEN)
    }
}
