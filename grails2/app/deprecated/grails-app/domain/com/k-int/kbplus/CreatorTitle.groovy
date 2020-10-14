package com.k_int.kbplus

import de.laser.RefdataValue
import de.laser.helper.RefdataAnnotation

class CreatorTitle {

    @RefdataAnnotation(cat = '?')
    RefdataValue role

    Date dateCreated
    Date lastUpdated

    static mapping = {

        id column:'ct_id'
        version column:'ct_version'
        role column:'ct_role_rv_fk'
        title column:'ct_title_fk'
        creator column:'ct_creator_fk'
        lastUpdated column:'ct_last_updated'
        dateCreated column:'ct_date_created'

    }

    static belongsTo = [
            title:  TitleInstance,
            creator:    Creator
    ]

    static constraints = {
    }
}
