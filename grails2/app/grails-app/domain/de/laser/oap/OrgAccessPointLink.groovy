package de.laser.oap

import de.laser.Platform
import de.laser.SubscriptionPackage
import de.laser.base.AbstractBase

class OrgAccessPointLink extends AbstractBase {

    OrgAccessPoint oap
    Platform platform
    SubscriptionPackage subPkg
    Boolean active = false
    Date dateCreated
    Date lastUpdated

    static belongsTo = [
        oap:OrgAccessPoint,
        platform:Platform,
        subPkg:SubscriptionPackage
    ]

    static constraints = {
        globalUID(nullable:true, blank:false, unique:true, maxSize:255)
        platform(nullable:true)
        oap     (nullable:true) //intentional, null used in program logic
        subPkg  (nullable:true) //intentional, null used in program logic
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
