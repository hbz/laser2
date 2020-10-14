package com.k_int.kbplus

import de.laser.base.AbstractBase

class Creator extends AbstractBase {

    String firstname
    String middlename
    String lastname

    Date dateCreated
    Date lastUpdated

    static hasMany = [
            title:  CreatorTitle,
    ]

    static mapping = {
        id column: 'cre_id'
        version column: 'cre_version'
        firstname column: 'cre_firstname'
        middlename column:'cre_middlename'
        lastname column:'cre_lastname'
        globalUID column:'cre_guid'
        lastUpdated column:'cre_last_updated'
        dateCreated column:'cre_date_created'

        title   batchSize: 10
    }

    static constraints = {
        firstname   (nullable:true, blank:false);
        middlename  (nullable:true, blank:false);
        globalUID   (nullable:true, blank:false, unique:true, maxSize:255)
        title       (nullable:true)
    }

    @Override
    def beforeInsert() {
        super.beforeInsertHandler()
    }
    @Override
    def beforeUpdate() {
        super.beforeUpdateHandler()
    }
    @Override
    def beforeDelete() {
        super.beforeDeleteHandler()
    }
}
