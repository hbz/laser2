package de.laser


import de.laser.oap.OrgAccessPoint
import de.laser.oap.OrgAccessPointLink
import de.laser.helper.RDStore
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap

import javax.persistence.Transient

class SubscriptionPackage implements Comparable {

  Subscription subscription
  Package pkg
  Date finishDate

  Date dateCreated
  Date lastUpdated

  static transients = ['issueEntitlementsofPackage', 'IEandPackageSize', 'currentTippsofPkg', 'packageName'] // mark read-only accessor methods

  static mapping = {
                id column:'sp_id'
           version column:'sp_version'
      subscription column:'sp_sub_fk',  index: 'sp_sub_pkg_idx'
               pkg column:'sp_pkg_fk',  index: 'sp_sub_pkg_idx'
        finishDate column:'sp_finish_date'

    dateCreated column: 'sp_date_created'
    lastUpdated column: 'sp_last_updated'
  }

  static hasMany = [
    oapls: OrgAccessPointLink,
    pendingChangeConfig: PendingChangeConfiguration
  ]

  static belongsTo = [
      pkg: Package,
      subscription: Subscription
  ]

  static mappedBy = [
    oapls: 'subPkg',
    pendingChangeConfig: 'subscriptionPackage'
  ]

  static constraints = {
    subscription(nullable:true)
    pkg         (nullable:true)
    finishDate  (nullable:true)

    // Nullable is true, because values are already in the database
    lastUpdated (nullable: true)
    dateCreated (nullable: true)
    //oapls   batchSize: 10
  }

  @Override
  int compareTo(Object o) {
    return this.pkg.name.compareTo(o.pkg.name)
  }

  @Transient
  static def refdataFind(GrailsParameterMap params) {
    List<Map<String, Object>> result = []
    Map<String, Object> hqlParams = [:]
    String hqlString = "select sp from SubscriptionPackage as sp"

    if ( params.subFilter ) {
      hqlString += ' where sp.subscription.id = :sid'
      hqlParams.put('sid', params.long('subFilter'))
    }

    List<SubscriptionPackage> results = SubscriptionPackage.executeQuery(hqlString,hqlParams)

    results?.each { t ->
      String resultText = t.subscription.name + '/' + t.pkg.name
      result.add([id:"${t.class.name}:${t.id}",text:resultText])
    }

    result
  }

  List getIssueEntitlementsofPackage(){

    List result = []

    this.subscription.issueEntitlements.findAll{(it.status?.id == RDStore.TIPP_STATUS_CURRENT.id) && (it.acceptStatus?.id == RDStore.IE_ACCEPT_STATUS_FIXED.id)}.each { iE ->
      if(TitleInstancePackagePlatform.findByIdAndPkg(iE.tipp?.id, pkg))
      {
        result << iE
      }
    }
    result
  }

  String getIEandPackageSize(){

    return '(<span data-tooltip="Titel in der Lizenz"><i class="ui icon archive"></i></span>' + this.getIssueEntitlementsofPackage().size() + ' / <span data-tooltip="Titel im Paket"><i class="ui icon book"></i></span>' + this.getCurrentTippsofPkg()?.size() + ')'
  }

  def getCurrentTippsofPkg()
  {
    def result = this.pkg.tipps?.findAll{it?.status?.value == 'Current'}

    result
  }

  String getPackageName() {
    return this.pkg.name
  }

  def getNotActiveAccessPoints(Org org){
    String notActiveAPLinkQuery = "select oap from OrgAccessPoint oap where oap.org =:institution "
    notActiveAPLinkQuery += "and not exists ("
    notActiveAPLinkQuery += "select 1 from oap.oapp as oapl where oapl.oap=oap and oapl.active=true "
    notActiveAPLinkQuery += "and oapl.subPkg.id = ${id}) order by lower(oap.name)"
    OrgAccessPoint.executeQuery(notActiveAPLinkQuery, [institution : org])
  }

  def getAccessPointListForOrgAndPlatform(Org org, Platform platform){
    // do not mix derived and not derived
    if (platform.usesPlatformAccessPoints(org, this)){
      String hql = "select oapl from OrgAccessPointLink oapl join oapl.oap as oap where oap.org=:org and oapl.platform=:platform and oapl.active=true"
      return OrgAccessPointLink.executeQuery(hql, [org:org, platform:platform])
    } else {
        String hql = "select oapl from OrgAccessPointLink oapl join oapl.oap as oap where oapl.subPkg=:subPkg and oap.org=:org and oapl.active=true"
        return OrgAccessPointLink.executeQuery(hql, [subPkg:this, org:org])
    }
  }

  //used by /subscription/show.gsp
  PendingChangeConfiguration getPendingChangeConfig(String config) {
    PendingChangeConfiguration.findBySubscriptionPackageAndSettingKey(this,config)
  }
}
