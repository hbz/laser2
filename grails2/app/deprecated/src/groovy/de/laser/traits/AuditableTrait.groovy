package de.laser.traits

import com.k_int.kbplus.License
import de.laser.RefdataValue
import com.k_int.kbplus.Subscription
import com.k_int.kbplus.abstract_domain.CustomProperty

import javax.persistence.Transient

@Deprecated
trait AuditableTrait {

    /**
     * IMPORTANT:
     *
     * Declare auditable and controlledProperties in implementing classes.
     *
     * Overwrite onChange() and/or notifyDependencies_trait() if needed ..
     *
     */

    // def changeNotificationService

    // static auditable = [ ignore: ['version', 'lastUpdated', 'pendingChanges'] ]

    // static controlledProperties = ['name', 'date', 'etc']

    @Transient
    def auditService

    @Transient
    def onDelete = { oldMap ->
        log?.debug("onDelete() ${this}")
    }

    @Transient
    def onSave = {
        log?.debug("onSave() ${this}")
    }

    @Transient
    def onChange = { oldMap, newMap ->

        log?.debug("onChange(id:${this.id}): ${oldMap} => ${newMap}")

        if(this.instanceOf == null) {
            List<String> gwp = auditService.getWatchedProperties(this)

            log?.debug("found watched properties: ${gwp}")

            gwp.each { cp ->
                if (oldMap[cp] != newMap[cp]) {

                    Map<String, Object> event = [:]
                    String clazz = this."${cp}".getClass().getName()

                    log?.debug("notifyChangeEvent() " + this + " : " + clazz)

                    if (this instanceof CustomProperty) {

                        if (auditService.getAuditConfig(this)) {

                            String old_oid
                            String new_oid
                            if (oldMap[cp] instanceof RefdataValue ) {
                                old_oid = oldMap[cp] ? "${oldMap[cp].class.name}:${oldMap[cp].id}" : null
                                new_oid = newMap[cp] ? "${newMap[cp].class.name}:${newMap[cp].id}" : null
                            }

                            event = [
                                    OID        : "${this.class.name}:${this.id}",
                                    //OID        : "${this.owner.class.name}:${this.owner.id}",
                                    event      : "${this.class.simpleName}.updated",
                                    prop       : cp,
                                    name       : type.name,
                                    type       : this."${cp}".getClass().toString(), // TODO ERMS-2880
                                    old        : old_oid ?: oldMap[cp], // Backward Compatibility
                                    oldLabel   : oldMap[cp] instanceof RefdataValue ? oldMap[cp].toString() : oldMap[cp],
                                    new        : new_oid ?: newMap[cp], // Backward Compatibility
                                    newLabel   : newMap[cp] instanceof RefdataValue ? newMap[cp].toString() : newMap[cp],
                                    //propertyOID: "${this.class.name}:${this.id}"
                            ]
                        }
                        else {
                            log?.debug("ignored because no audit config")
                        }
                    } // CustomProperty
                    else {

                        boolean isSubOrLic = (this instanceof Subscription || this instanceof License)

                        if ( ! isSubOrLic || (isSubOrLic && auditService.getAuditConfig(this, cp)) ) {

                            if (clazz.equals( RefdataValue.class.name )) {

                                String old_oid = oldMap[cp] ? "${oldMap[cp].class.name}:${oldMap[cp].id}" : null
                                String new_oid = newMap[cp] ? "${newMap[cp].class.name}:${newMap[cp].id}" : null

                                event = [
                                        OID     : "${this.class.name}:${this.id}",
                                        event   : "${this.class.simpleName}.updated",
                                        prop    : cp,
                                        old     : old_oid,
                                        oldLabel: oldMap[cp]?.toString(),
                                        new     : new_oid,
                                        newLabel: newMap[cp]?.toString()
                                ]
                            } else {

                                event = [
                                        OID  : "${this.class.name}:${this.id}",
                                        event: "${this.class.simpleName}.updated",
                                        prop : cp,
                                        old  : oldMap[cp],
                                        new  : newMap[cp]
                                ]
                            }
                        } // Subscription or License
                        else {
                            log?.debug("ignored because no audit config")
                        }
                    }

                    log?.debug(event)

                    if (event) {
                        if (! changeNotificationService) {
                            log?.error("changeNotificationService not implemented @ ${it}")
                        } else {
                            changeNotificationService.fireEvent(event)
                        }
                    }
                }
            }
        }

    }
}
