package com.k_int.kbplus

import de.laser.RefdataValue
import de.laser.helper.RefdataAnnotation

class GlobalRecordInfo {

  GlobalRecordSource source
  String identifier
  String uuid
  String desc
  String name
  Long rectype
  Date ts
  Set trackers 
  byte[] record

    Date dateCreated
    Date lastUpdated

    @RefdataAnnotation(cat = '?')
    RefdataValue kbplusCompliant

    @RefdataAnnotation(cat = '?')
    RefdataValue globalRecordInfoStatus // RefdataCategory unkown, RDConstants.PACKAGE_STATUS ?

  static hasMany = [ trackers : GlobalRecordTracker ]
  static mappedBy = [ trackers : 'owner']


  static mapping = {
                   id column:'gri_id'
              version column:'gri_version'
               source column:'gri_source_fk'
                   ts column:'gri_timestamp'
           identifier column:'gri_identifier'
                 uuid column:'gri_uuid'
                 name column:'gri_name', type:'text'
                 desc column:'gri_desc', type:'text'
              rectype column:'gri_rectype'
               record column:'gri_record', length:(1024*1024*64)// , type:'blob' // , length:(1024*1024*64)
      kbplusCompliant column:'gri_kbplus_compliant'
      globalRecordInfoStatus column: 'gri_status_rv_fk'

      dateCreated column: 'gri_date_created'
      lastUpdated column: 'gri_last_updated'

            trackers  batchSize: 10
  }

  static constraints = {
                  name(nullable:true, blank:false, maxSize:2048)
                  desc(nullable:true, blank:false)
               rectype(nullable:true, blank:false)
                  uuid(nullable:true, blank:false)
                    ts(nullable:true, blank:false)
       kbplusCompliant(nullable:true, blank:false)
      globalRecordInfoStatus(nullable: true, blank: false)
      // Nullable is true, because values are already in the database
      lastUpdated (nullable: true, blank: false)
      dateCreated (nullable: true, blank: false)
  }

  transient String getDisplayRectype() {
    String result = ""
    switch ( rectype ) {
      case 0:
        result = 'Package'
        break;
    }
    result
  }

}
