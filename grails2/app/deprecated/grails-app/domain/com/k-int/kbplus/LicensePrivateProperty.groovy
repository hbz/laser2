package com.k_int.kbplus

import de.laser.base.AbstractPropertyWithCalculatedLastUpdated
import com.k_int.kbplus.abstract_domain.PrivateProperty
import com.k_int.properties.PropertyDefinition

import javax.persistence.Transient

class LicensePrivateProperty extends PrivateProperty {

    @Transient
    def grailsApplication
    @Transient
    def messageSource
    @Transient
    def changeNotificationService
    @Transient
    def deletionService

    PropertyDefinition type
    License owner
    String paragraph

    Date dateCreated
    Date lastUpdated

    static mapping = {
        includes AbstractPropertyWithCalculatedLastUpdated.mapping

        id      column:'lpp_id'
        version column:'lpp_version'
        owner   column:'lpp_owner_fk', index:'lpp_owner_idx'
        type    column:'lpp_type_fk'

        paragraph type:'text'

        dateCreated column: 'lpp_date_created'
        lastUpdated column: 'lpp_last_updated'
    }

    static constraints = {
        importFrom AbstractPropertyWithCalculatedLastUpdated

        paragraph (nullable:true)
        owner     (blank:false)

        // Nullable is true, because values are already in the database
        lastUpdated (nullable: true, blank: false)
        dateCreated (nullable: true, blank: false)
    }

    static belongsTo = [
        type:   PropertyDefinition,
        owner:  License
    ]

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

    @Override
    def copyInto(AbstractPropertyWithCalculatedLastUpdated newProp){
        newProp = super.copyInto(newProp)

        newProp.paragraph = paragraph
        newProp
    }

    @Transient
    def onDelete = { oldMap ->
        log.debug("onDelete LicenseProperty")
        def oid = "${this.owner.class.name}:${this.owner.id}"
        Map<String, Object> changeDoc = [ OID: oid,
                                          event:'LicenseProperty.deleted',
                                          prop: "${this.type.name}",
                                          old: "",
                                          new: "property removed",
                                          name: this.type.name
                                          ]
        def changeNotificationService = grailsApplication.mainContext.getBean("changeNotificationService")
        // changeNotificationService.broadcastEvent("com.k_int.kbplus.License:${owner.id}", changeDoc);
        changeNotificationService.fireEvent(changeDoc)
    }
}
