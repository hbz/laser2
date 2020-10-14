package de.laser.api.v0.entities

import de.laser.Identifier
import de.laser.Org
import de.laser.api.v0.*
import de.laser.helper.Constants
import de.laser.helper.RDStore
import grails.converters.JSON
import groovy.util.logging.Log4j
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsHibernateUtil

@Log4j
class ApiOrg {

    /**
     * @return ApiBox(obj: Org | null, status: null | BAD_REQUEST | PRECONDITION_FAILED | NOT_FOUND | OBJECT_STATUS_DELETED)
     */
    static ApiBox findOrganisationBy(String query, String value) {
        ApiBox result = ApiBox.get()

        switch(query) {
            case 'id':
                result.obj = Org.findAllWhere(id: Long.parseLong(value))
                break
            case 'globalUID':
                result.obj = Org.findAllWhere(globalUID: value)
                break
            case 'gokbId':
                result.obj = Org.findAllWhere(gokbId: value)
                break
            case 'ns:identifier':
                result.obj = Identifier.lookupObjectsByIdentifierString(new Org(), value)
                break
            default:
                result.status = Constants.HTTP_BAD_REQUEST
                return result
                break
        }
        result.validatePrecondition_1()

        if (result.obj instanceof Org) {
            result.validateDeletedStatus_2('status', RDStore.ORG_STATUS_DELETED)
        }
        result
    }

    /**
     * @return JSON | FORBIDDEN
     */
    static requestOrganisation(Org org, Org context, boolean isInvoiceTool) {
        Map<String, Object> result = [:]

        boolean hasAccess = isInvoiceTool || (org.id == context.id)
        if (hasAccess) {
            result = getOrganisationMap(org, context)
        }

        return (hasAccess ? new JSON(result) : Constants.HTTP_FORBIDDEN)
    }

    /**
     * @return Map<String, Object>
     */
    static Map<String, Object> getOrganisationMap(Org org, Org context) {
        Map<String, Object> result = [:]

        org = GrailsHibernateUtil.unwrapIfProxy(org)

        result.globalUID    = org.globalUID
        result.gokbId       = org.gokbId
        result.comment      = org.comment
        result.name         = org.name
        result.scope        = org.scope
        result.shortname    = org.shortname
        result.sortname     = org.sortname
        result.region       = org.region?.value
        result.country      = org.country?.value
        result.libraryType  = org.libraryType?.value
        result.lastUpdated  = ApiToolkit.formatInternalDate(org._getCalculatedLastUpdated())
        result.eInvoice  = org.eInvoice ? 'Yes' : 'No'

        //result.fteStudents  = org.fteStudents // TODO dc/table readerNumber
        //result.fteStaff     = org.fteStaff // TODO dc/table readerNumber

        // RefdataValues

        result.eInvoicePortal = org.eInvoicePortal?.value
        result.sector       = org.sector?.value
        result.type         = org.orgType?.collect{ it.value }
        result.status       = org.status?.value

        // References

        result.addresses    = ApiCollectionReader.getAddressCollection(org.addresses, ApiReader.NO_CONSTRAINT) // de.laser.Address
        result.contacts     = ApiCollectionReader.getContactCollection(org.contacts, ApiReader.NO_CONSTRAINT)  // de.laser.Contact
        result.identifiers  = ApiCollectionReader.getIdentifierCollection(org.ids) // de.laser.Identifier
        result.persons      = ApiCollectionReader.getPrsLinkCollection(
                org.prsLinks, ApiReader.NO_CONSTRAINT, ApiReader.NO_CONSTRAINT, context
        ) // de.laser.PersonRole

        result.orgAccessPoints	= ApiCollectionReader.getOrgAccessPointCollection(org.accessPoints)

        result.properties   = ApiCollectionReader.getPropertyCollection(org, context, ApiReader.IGNORE_NONE) // com.k_int.kbplus.(OrgCustomProperty, OrgPrivateProperty)

        // Ignored

        //result.affiliations         = org.affiliations // com.k_int.kblpus.UserOrg
        //result.incomingCombos       = org.incomingCombos // de.laser.Combo
        //result.links                = exportHelperService.resolveOrgLinks(org.links) // de.laser.OrgRole
        //result.membership           = org.membership?.value // RefdataValue
        //result.outgoingCombos       = org.outgoingCombos // de.laser.Combo

        ApiToolkit.cleanUp(result, true, true)
    }
}
