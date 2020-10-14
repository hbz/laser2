package com.k_int.kbplus

import com.k_int.kbplus.auth.Role
import com.k_int.kbplus.onixpl.OnixPLHelperService
import com.k_int.kbplus.onixpl.OnixPLService
import com.k_int.xml.XMLDoc
import de.laser.Doc
import de.laser.interfaces.Permissions
import groovy.util.logging.Log4j

import javax.persistence.Transient

/**
 * An OnixplLicense has many OnixplUsageTerms and OnixplLicenseTexts.
 * It can be associated with many licenses.
 * The OnixplLicenseTexts relation is redundant as UsageTerms refer to the
 * LicenseTexts, but is a convenient way to access the whole license text.
 */

@Log4j
class OnixplLicense implements Permissions {

  Date lastmod;
  String title;

  Date dateCreated
  Date lastUpdated

  // An ONIX-PL license relates to a a doc
  Doc doc;

  @Transient
  private XMLDoc xml

  @Transient
  private OnixPLService onixService

  @Transient
  private OnixPLHelperService onixHelperService

  @Transient
  void setOnixPLService (service) {
    onixService = service
  }

  @Transient
  void setOnixPLHelperService (service) {
    onixHelperService = service
  }


  @Transient
  XMLDoc getXML() {
    xml = xml ?: new XMLDoc (doc.getBlobContent().binaryStream)
    xml
  }

  // One to many
  static hasMany = [
    licenses: License
  ]

  // Reference to license in the many
  static mappedBy = [
    licenses: 'onixplLicense',
  ]

  static mapping = {
    id column: 'opl_id'
    version column: 'opl_version'
    doc column: 'opl_doc_fk'
    lastmod column: 'opl_lastmod'
    title column: 'opl_title'

    dateCreated column: 'opl_date_created'
    lastUpdated column: 'opl_last_updated'
  }

  static constraints = {
    doc(nullable: false, blank: false)
    lastmod(nullable: true, blank: true)
    title(nullable: false, blank: false)

    // Nullable is true, because values are already in the database
    lastUpdated (nullable: true, blank: false)
    dateCreated (nullable: true, blank: false)
  }

  boolean isEditableBy(user) {
    hasPerm('edit', user)
  }

  boolean isVisibleBy(user) {
    hasPerm('view', user)
  }

    // Only admin has permission to change ONIX-PL licenses; anyone can view them.
  boolean hasPerm(perm, user) {
        if (perm == 'view') {
            return true
        }

        // If user is a member of admin role, they can do anything.
        def admin_role = Role.findByAuthority('ROLE_ADMIN')
        if (admin_role && user.getAuthorities().contains(admin_role)) {
            return true
        }

        false
    }


  @Override
  String toString() {
    return "OnixplLicense{" +
        "id=" + id +
        ", lastmod=" + lastmod +
        ", title='" + title + '\'' +
        ", doc=" + doc +
        '}';
  }

  Map toMap (List<String> sections = null) {

    // Get all comparison points as a map.
    Map all_points = onixService.allComparisonPointsMap

    // Default to all points.
    sections = sections ?: all_points.keySet() as List

    // Go through each of the available or requested comparison points and examine them to determine equality.
    TreeMap data = [:]

    sections.each { String xpath_expr ->

      def group = all_points."${xpath_expr}"?."group"
      if (group) {
        if (data[group] == null) data[group] = new TreeMap<String, List<Map>>().withDefault {
          []
        }

        def xml = getXML()
        
        // log.debug("XPath expression: ${xpath_expr}")
        
        // Query for xpath results.
        def results = xml.XPath(xpath_expr)
        if (results.length > 0) {

          // For each of the results we need to add a map representation to the result.
          results.each { org.w3c.dom.Node node ->

            node = onixHelperService.duplicateDefinitionText(node,xml)

            def snippet = new XMLDoc (node)
            snippet = onixHelperService.replaceAllTextElements(xml, snippet)

            // Create our new XML element of the segment.
            def xml_maps = snippet.toMaps()
            
            // if we have a post processor then execute it here
            all_points."${xpath_expr}"?."processor"?.call(xml_maps)
            
            // Add the data to the result.
            data[group][xpath_expr] += xml_maps
          }
        }
      }
    }

    // Return the data.
    data
  }

  static def refdataFind(params) {
      def result = []
      def  ql = findAllByTitleIlike(params.q,params)
      if ( ql ) {
          ql.each { prop ->
              result.add([id:"${prop.title}||${prop.id}",text:"${prop.title}"])
          }
      }
      result
  }
}
