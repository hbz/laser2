package de.laser.properties

import com.k_int.kbplus.GenericOIDService
import de.laser.Org
import de.laser.CacheService
import de.laser.I10nTranslation
import de.laser.helper.EhcacheWrapper
import grails.util.Holders
import groovy.util.logging.Log4j
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.springframework.context.i18n.LocaleContextHolder

@Log4j
class PropertyDefinitionGroup {

    String name
    String description
    Org    tenant
    String ownerType // PropertyDefinition.[LIC_PROP, SUB_PROP, ORG_PROP]

    boolean isVisible = false // default value: will be overwritten by existing bindings

    Date dateCreated
    Date lastUpdated

    def contextService

    static hasMany = [
            items: PropertyDefinitionGroupItem,
            bindings: PropertyDefinitionGroupBinding
    ]
    static mappedBy = [
            items:    'propDefGroup',
            bindings: 'propDefGroup'
    ]

    static mapping = {
        id          column: 'pdg_id'
        version     column: 'pdg_version'
        name        column: 'pdg_name'
        description column: 'pdg_description',  type: 'text'
        tenant      column: 'pdg_tenant_fk',    index: 'pdg_tenant_idx'
        ownerType   column: 'pdg_owner_type'
        isVisible   column: 'pdg_is_visible'
        lastUpdated     column: 'pdg_last_updated'
        dateCreated     column: 'pdg_date_created'

        items       cascade: 'all', batchSize: 10
        bindings    cascade: 'all', batchSize: 10
    }

    static constraints = {
        name        (blank: false)
        description (nullable: true,  blank: true)
        tenant      (nullable: true)
        ownerType   (blank: false)
        lastUpdated (nullable: true)
        dateCreated (nullable: true)
    }

    List<PropertyDefinition> getPropertyDefinitions() {

        PropertyDefinition.executeQuery(
            "SELECT pd from PropertyDefinition pd, PropertyDefinitionGroupItem pdgi WHERE pdgi.propDef = pd AND pdgi.propDefGroup = :pdg",
            [pdg: this]
        )
    }

    List getCurrentProperties(def currentObject) {
        List result = []
        def givenIds = getPropertyDefinitions().collect{ it.id }

        currentObject?.propertySet?.each{ cp ->
            if (cp.type.id in givenIds) {
                result << GrailsHibernateUtil.unwrapIfProxy(cp)
            }
        }
        result
    }

    List getCurrentPropertiesOfTenant(def currentObject, Org tenant) {
        List result = []
        def givenIds = getPropertyDefinitions().collect{ it.id } //continue here: wrong number delivered

        currentObject?.propertySet?.each{ cp ->
            if (cp.type.id in givenIds && cp.tenant.id == tenant.id) {
                result << GrailsHibernateUtil.unwrapIfProxy(cp)
            }
        }
        result
    }

    static List<PropertyDefinitionGroup> getAvailableGroups(Org tenant, String ownerType) {
        List<PropertyDefinitionGroup> result = []
        List<PropertyDefinitionGroup> global  = findAllWhere( tenant: null, ownerType: ownerType)
        List<PropertyDefinitionGroup> context = findAllByTenantAndOwnerType(tenant, ownerType)

        result.addAll(global)
        result.addAll(context)

        result
    }

    static def refdataFind(GrailsParameterMap params) {
        List<Map<String, Object>> result = []

        GenericOIDService genericOIDService = (GenericOIDService) Holders.applicationContext.getBean('genericOIDService')
        def currentObject = genericOIDService.resolveOID(params.oid)

        CacheService cacheService = (CacheService) Holders.grailsApplication.mainContext.getBean('cacheService')
        EhcacheWrapper cache

        cache = cacheService.getTTL300Cache("PropertyDefinitionGroup/refdataFind/${currentObject.id}")

        if (! cache.get('propDefs')) {
            List<PropertyDefinition> propDefs = currentObject.getPropertyDefinitions()

            List cacheContent = []
            propDefs.each { it ->
                cacheContent.add([id:"${it.id}", en:"${it.name_en}", de:"${it.name_de}"])
            }
            cache.put('propDefs', cacheContent)
        }

        cache.get('propDefs').each { it ->
            switch (I10nTranslation.decodeLocale(LocaleContextHolder.getLocale())) {
                case 'en':
                    if (params.q == '*' || it.en?.toLowerCase()?.contains(params.q?.toLowerCase())) {
                        result.add([id:"${it.id}", text:"${it.en}"])
                    }
                    break
                case 'de':
                    if (params.q == '*' || it.de?.toLowerCase()?.contains(params.q?.toLowerCase())) {
                        result.add([id:"${it.id}", text:"${it.de}"])
                    }
                    break
            }
        }

        result
    }
}

