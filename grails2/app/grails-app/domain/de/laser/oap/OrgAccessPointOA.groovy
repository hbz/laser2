package de.laser.oap

import groovy.util.logging.Log4j

@Log4j
class OrgAccessPointOA extends OrgAccessPoint{

    String entityId

    static mapping = {
        includes OrgAccessPoint.mapping
        entityId        column:'oar_entity_id'
    }

    static constraints = {
    }
}
