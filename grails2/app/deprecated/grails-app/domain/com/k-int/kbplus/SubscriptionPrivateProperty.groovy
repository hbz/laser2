package com.k_int.kbplus

import de.laser.base.AbstractPropertyWithCalculatedLastUpdated
import com.k_int.kbplus.abstract_domain.PrivateProperty
import com.k_int.properties.PropertyDefinition

import javax.persistence.Transient

class SubscriptionPrivateProperty extends PrivateProperty {

    @Transient
    def deletionService

    PropertyDefinition type
    Subscription owner

    Date dateCreated
    Date lastUpdated

    static mapping = {
        includes AbstractPropertyWithCalculatedLastUpdated.mapping

        id      column:'spp_id'
        version column:'spp_version'
        type    column:'spp_type_fk'
        owner   column:'spp_owner_fk', index:'spp_owner_idx'

        dateCreated column: 'spp_date_created'
        lastUpdated column: 'spp_last_updated'
    }

    static belongsTo = [
            type:  PropertyDefinition,
            owner: Subscription
    ]

    static constraints = {
        importFrom AbstractPropertyWithCalculatedLastUpdated

        type    (blank:false)
        owner   (blank:false)

        // Nullable is true, because values are already in the database
        lastUpdated (nullable: true, blank: false)
        dateCreated (nullable: true, blank: false)
    }

    @Override
    def afterDelete() {
        super.afterDeleteHandler()

        deletionService.deleteDocumentFromIndex(this.getClass().getSimpleName().toLowerCase()+":"+this.id)
    }
    @Override
    def afterInsert() {
        super.afterInsertHandler()
    }
    @Override
    def afterUpdate() {
        super.afterUpdateHandler()
    }
}
