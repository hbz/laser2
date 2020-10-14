package de.laser

import com.k_int.kbplus.GenericOIDService
import de.laser.properties.PlatformProperty
import de.laser.properties.PropertyDefinitionGroup
import de.laser.properties.PropertyDefinitionGroupBinding
import de.laser.oap.OrgAccessPoint
import de.laser.oap.OrgAccessPointLink
import de.laser.base.AbstractBaseWithCalculatedLastUpdated
import de.laser.helper.RDConstants
import de.laser.helper.RDStore
import de.laser.helper.RefdataAnnotation
import grails.util.Holders
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap

class Platform extends AbstractBaseWithCalculatedLastUpdated {

  def grailsApplication
  def propertyService
  def deletionService

  static Log static_logger = LogFactory.getLog(Platform)

  String gokbId
  String name
  String normname
  String primaryUrl
  String provenance

  @RefdataAnnotation(cat = '?')
  RefdataValue type

  @RefdataAnnotation(cat = RDConstants.PLATFORM_STATUS)
  RefdataValue status // TODO: not in Bootstrap

  @RefdataAnnotation(cat = RDConstants.Y_N)
  RefdataValue serviceProvider

  @RefdataAnnotation(cat = RDConstants.Y_N)
  RefdataValue softwareProvider

  Date dateCreated
  Date lastUpdated
  Date lastUpdatedCascading

  Org org

  static mappedBy = [tipps: 'platform']

  static hasMany = [
          tipps      : TitleInstancePackagePlatform,
          oapp       : OrgAccessPointLink,
          propertySet:   PlatformProperty,
  ]

  static transients = ['currentTipps'] // mark read-only accessor methods

  static mapping = {
                id column:'plat_id'
         globalUID column:'plat_guid'
           version column:'plat_version'
            gokbId column:'plat_gokb_id', type:'text'
              name column:'plat_name'
          normname column:'plat_normalised_name'
        provenance column:'plat_data_provenance'
        primaryUrl column:'plat_primary_url'
              type column:'plat_type_rv_fk'
            status column:'plat_status_rv_fk'
   serviceProvider column:'plat_servprov_rv_fk'
  softwareProvider column:'plat_softprov_rv_fk'
              org  column: 'plat_org_fk', index: 'plat_org_idx'
    lastUpdatedCascading column: 'plat_last_updated_cascading'
             tipps sort: 'title.title', order: 'asc', batchSize: 10
            oapp batchSize: 10
    propertySet sort:'type', order:'desc', batchSize: 10
  }

  static constraints = {
    globalUID(nullable:true, blank:false, unique:true, maxSize:255)
    primaryUrl(nullable:true, blank:false)
    provenance(nullable:true, blank:false)
    type            (nullable:true)
    status          (nullable:true)
    serviceProvider (nullable:true)
    softwareProvider(nullable:true)
    gokbId (blank:false, unique: true, maxSize:511)
    org             (nullable:true)
    lastUpdatedCascading (nullable: true)
  }

  @Override
  def afterDelete() {
    super.afterDeleteHandler()

    deletionService.deleteDocumentFromIndex(this.globalUID)
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

  @Deprecated
  static Platform lookupOrCreatePlatform(Map params=[:]) {

    withTransaction {

    Platform platform
    List<Platform> platform_candidates = []

/*    if ( params.impId && params.impId.trim().length() > 0) {
      platform = Platform.findByImpId(params.impId)
    }*/

    if ( params.gokbId && params.gokbId.trim().length() > 0) {
      platform = Platform.findByGokbId(params.gokbId)

    }

    if ( !platform && params.name && (params.name.trim().length() > 0)  ) {

      String norm_name = params.name.trim().toLowerCase();
        //TODO: Dieser Zweig passieert nicht bei GOKB Sync
      if( params.primaryUrl && (params.primaryUrl.length() > 0) ){

        platform_candidates = Platform.executeQuery("from Platform where normname = :nname or primaryUrl = :url", [nname: norm_name, url: params.primaryUrl])

        if(platform_candidates && platform_candidates.size() == 1){
          platform = platform_candidates[0]
        }
      }
      else {

        platform_candidates = Platform.executeQuery("from Platform where normname = :nname or primaryUrl = :nname",[nname: norm_name])

        if(platform_candidates && platform_candidates.size() == 1){
          platform = platform_candidates[0]
        }
      }

      if ( !platform && !platform_candidates) {
        platform = new Platform(gokbId: params.gokbId?.length() > 0 ? params.gokbId : null,
                                name: params.name,
                                normname: norm_name,
                                provenance: (params.provenance ?: null),
                                primaryUrl: (params.primaryUrl ?: null),
                                lastmod: System.currentTimeMillis()).save(flush:true)

      }
    }

    if (platform && params.gokbId  && platform.gokbId != params.gokbId) {
      platform.gokbId = params.gokbId
      platform.save()
    }

    if(platform && params.primaryUrl && platform.primaryUrl != params.primaryUrl)
    {
      platform.primaryUrl = params.primaryUrl
      platform.save()
    }

    if(platform && params.name && platform.name != params.name)
    {
      platform.name = params.name
      platform.save()
    }

        platform
      }
  }

  Map<String, Object> _getCalculatedPropDefGroups(Org contextOrg) {
    Map<String, Object> result = [ 'sorted':[], 'global':[], 'local':[], 'orphanedProperties':[] ]

    // ALL type depending groups without checking tenants or bindings
    List<PropertyDefinitionGroup> groups = PropertyDefinitionGroup.findAllByOwnerType(Platform.class.name, [sort:'name', order:'asc'])
    groups.each{ it ->

      PropertyDefinitionGroupBinding binding = PropertyDefinitionGroupBinding.findByPropDefGroupAndOrg(it, contextOrg)

      if (it.tenant == null || it.tenant?.id == contextOrg.id) {
        if (binding) {
          result.local << [it, binding] // TODO: remove
          result.sorted << ['local', it, binding]
        }
        else {
          result.global << it // TODO: remove
          result.sorted << ['global', it, null]
        }
      }
    }

    // storing properties without groups
    result.orphanedProperties = propertyService.getOrphanedProperties(this, result.sorted)

    result
  }

  boolean usesPlatformAccessPoints(contextOrg, subscriptionPackage){
    // TODO do we need the contextOrg?
    // look for OrgAccessPointLinks for this platform and a given subscriptionPackage, if we can find that "marker",
    // we know the AccessPoints are not derived from the AccessPoints configured for the platform
    String hql = "select oapl from OrgAccessPointLink oapl where oapl.platform=${this.id} and oapl.subPkg = ${subscriptionPackage.id} and oapl.oap is null"
    def result = OrgAccessPointLink.executeQuery(hql)
    (result) ? false : true
  }

  def getContextOrgAccessPoints(contextOrg) {
    String hql = "select oap from OrgAccessPoint oap " +
        "join oap.oapp as oapp where oap.org=:org and oapp.active = true and oapp.platform.id =${this.id} and oapp.subPkg is null order by LOWER(oap.name)"
    def result = OrgAccessPoint.executeQuery(hql, ['org': contextOrg])
    return result
  }

  def getNotActiveAccessPoints(org){
    String notActiveAPLinkQuery = "select oap from OrgAccessPoint oap where oap.org =:institution "
    notActiveAPLinkQuery += "and not exists ("
    notActiveAPLinkQuery += "select 1 from oap.oapp as oapl where oapl.oap=oap and oapl.active=true "
    notActiveAPLinkQuery += "and oapl.platform.id = ${id}) order by lower(oap.name)"
    OrgAccessPoint.executeQuery(notActiveAPLinkQuery, [institution : org])
  }

  static def refdataFind(GrailsParameterMap params) {
    GenericOIDService genericOIDService = (GenericOIDService) Holders.grailsApplication.mainContext.getBean('genericOIDService')

    genericOIDService.getOIDMapList( Platform.findAllByNameIlike("${params.q}%", params), 'name' )
  }

  @Override
  boolean equals (Object o) {
    //def obj = ClassUtils.deproxy(o)
    def obj = GrailsHibernateUtil.unwrapIfProxy(o)
    if (obj != null) {
      if ( obj instanceof Platform ) {
        return obj.id == id
      }
    }
    return false
  }

  @Override
  String toString() {
    name
  }

  def getCurrentTipps() {
    def result = this.tipps?.findAll{it?.status?.id == RDStore.TIPP_STATUS_CURRENT.id}

    result
  }
}
