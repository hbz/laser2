package com.k_int.kbplus

import de.laser.Identifier

class IdentifierOccurrence {

    // TODO AuditTrail
    static auditable = true

    Identifier identifier

    Date dateCreated
    Date lastUpdated

    static belongsTo = [
            lic:    License,
            org:    Org,
            pkg:    Package,
            sub:    Subscription,
            ti:     TitleInstance,
            tipp:   TitleInstancePackagePlatform,
            cre:   Creator
    ]

    static mapping = {
        id  column:'io_id'
        identifier column:'io_canonical_id'
        lic     column:'io_lic_fk'
        org     column:'io_org_fk'
        pkg     column:'io_pkg_fk'
        sub     column:'io_sub_fk'
        ti      column:'io_ti_fk',      index:'io_title_idx'
        tipp    column:'io_tipp_fk'
        cre    column:'io_cre_fk'

        dateCreated column: 'io_date_created'
        lastUpdated column: 'io_last_updated'

  }

  static constraints = {
        lic     (nullable:true)
        org     (nullable:true)
        pkg     (nullable:true)
        sub     (nullable:true)
        ti      (nullable:true)
        tipp    (nullable:true)
        cre     (nullable:true)

      // Nullable is true, because values are already in the database
      lastUpdated (nullable: true, blank: false)
      dateCreated (nullable: true, blank: false)

  }

    /**
     * Generic setter
     */
    def setReference(def owner) {
        lic  = owner instanceof License ? owner : lic
        org  = owner instanceof Org ? owner : org
        pkg  = owner instanceof Package ? owner : pkg
        sub  = owner instanceof Subscription ? owner : sub
        tipp = owner instanceof TitleInstancePackagePlatform ? owner : tipp
        ti   = owner instanceof TitleInstance ? owner : ti
        cre  = owner instanceof Creator ? owner : cre
    }

    static getAttributeName(def object) {
        def name

        name = object instanceof License ?  'lic' : name
        name = object instanceof Org ?      'org' : name
        name = object instanceof Package ?  'pkg' : name
        name = object instanceof Subscription ?                 'sub' :  name
        name = object instanceof TitleInstancePackagePlatform ? 'tipp' : name
        name = object instanceof TitleInstance ?                'ti' :   name
        name = object instanceof Creator ?                'cre' :   name

        name
    }

    String toString() {
        "IdentifierOccurrence(${id} - lic:${lic}, org:${org}, pkg:${pkg}, sub:${sub}, ti:${ti}, tipp:${tipp}, cre:${cre})"
    }
}
