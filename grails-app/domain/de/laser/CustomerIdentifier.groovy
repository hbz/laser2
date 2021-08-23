package de.laser


import de.laser.annotations.RefdataAnnotation

class CustomerIdentifier {

    @RefdataAnnotation(cat = 'CustomerIdentifierType')
    RefdataValue type
    String value
    String requestorKey
    String note

    Org customer        // target org
    Platform platform   // target platform

    Org owner           // owner
    boolean isPublic = false    // true = visible only for owner

    static transients = ['provider'] // mark read-only accessor methods

    static mapping = {
                id column:'cid_id'
           version column:'cid_version'
              type column:'cid_type_rv_fk'
             value column:'cid_value'
      requestorKey column:'cid_requestor_key', type: 'text'
             note  column:'cid_note', type: 'text'
          customer column:'cid_customer_fk'
          platform column:'cid_platform_fk'
             owner column:'cid_owner_fk'
          isPublic column:'cid_is_public'
    }

    static constraints = {
        value           (nullable: true)
        requestorKey    (nullable: true)
        note     (nullable:true, blank:true)
    }

    Org getProvider() {
        return platform?.org
    }
}
