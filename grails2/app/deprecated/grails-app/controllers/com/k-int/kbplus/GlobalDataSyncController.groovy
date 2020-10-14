package com.k_int.kbplus

import com.k_int.kbplus.auth.User
import grails.plugin.springsecurity.annotation.Secured

@Secured(['IS_AUTHENTICATED_FULLY'])
@Deprecated
class GlobalDataSyncController {

  def springSecurityService
  def globalSourceSyncService
  def genericOIDService
  def dataloadService

  @Secured(['ROLE_GLOBAL_DATA'])
  def index() {
    Map<String, Object> result = [:]

    result.user = User.get(springSecurityService.principal.id)
    result.max = params.max ? Integer.parseInt(params.max) : result.user?.getDefaultPageSizeTMP()

    def paginate_after = params.paginate_after ?: ((2 * result.max) - 1)
    result.offset = params.offset ?: 0
    result.order = params.order ?: 'desc'

    result.rectype = params.rectype ?: 0

    if (result.order == "asc") {
      result.order = "desc"
    } else {
      result.order = "asc"
    }


    String base_qry = " from GlobalRecordInfo as r where lower(r.name) like ? and r.source.rectype = ${result.rectype} "

    def qry_params = []
    if (params.q?.length() > 0) {
      qry_params.add("%${params.q.trim().toLowerCase()}%");
    } else {
      qry_params.add("%");
    }

    if ((params.sort != null) && (params.sort.length() > 0)) {
      base_qry += " order by ${params.sort} ${params.order}"
    } else {
      base_qry += " order by r.name asc"
    }

    Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
    Thread[] threadArray = threadSet.toArray(new Thread[threadSet.size()]);

    threadArray.each {
      if (it.name == 'GlobalDataSync') {
        flash.message = message(code: 'globalDataSync.running')
      }
    }

    result.globalItemTotal = Subscription.executeQuery("select r.id " + base_qry, qry_params).size()
    result.items = Subscription.executeQuery("select r ${base_qry}", qry_params, [max: result.max, offset: result.offset]);

    result.tippcount = []
    result.items.each {
      def bais = new ByteArrayInputStream((byte[]) (it.record))
      def ins = new ObjectInputStream(bais);
      def rec_info = ins.readObject()
      ins.close()
      result.tippcount.add(rec_info.tipps.size())
    }
    result
  }

  @Secured(['ROLE_GLOBAL_DATA'])
  def newCleanTracker() {
    log.debug("params:"+params)
    Map<String, Object> result = [:]
    result.item = GlobalRecordInfo.get(params.id)

    log.debug("Calling diff....");
    result.impact = globalSourceSyncService.diff(null, result.item)

    result.type='new'
    render view:'reviewTracker', model:result
  }

  @Secured(['ROLE_ADMIN'])
  def selectLocalPackage() {
    log.debug("params:"+params)
    Map<String, Object> result = [:]
    result.item = GlobalRecordInfo.get(params.id)
    result
  }

  @Secured(['ROLE_ADMIN'])
  def cancelTracking() {
    log.debug("cancelTracking: " + params)
    GlobalRecordTracker.get(params.trackerId).delete()

    redirect(action:'index', params:[q:params.itemName])
  }

  /*@Secured(['ROLE_ADMIN'])
  def buildMergeTracker() {
    log.debug("params:"+params)
    if(!params.localPkg)
    {
      flash.error = message(code: 'globalDataSync.noselectedPackage')
      redirect(action:'selectLocalPackage', params:[id:params.id])
    }
    Map<String, Object> result = [:]
    result.type='existing'
    result.item = GlobalRecordInfo.get(params.id)
    result.localPkgOID = params.localPkg
    result.localPkg = genericOIDService.resolveOID(params.localPkg)

    log.debug("Calling diff....");
    result.impact = globalSourceSyncService.diff(result.localPkg, result.item)

    render view:'reviewTracker', model:result
  }*/

  @Secured(['ROLE_GLOBAL_DATA'])
  def createTracker() {
    log.debug("params: ${params}")
    Map<String, Object> result = [:]

    result.item = GlobalRecordInfo.get(params.id)
    def new_tracker_id = java.util.UUID.randomUUID().toString()

    if ( params.synctype != null ) {
      // new tracker and redirect back to list page

      switch ( params.synctype ) {
        case 'new':
          log.debug("merge remote package with new local package...");
          def grt = new GlobalRecordTracker(
                  owner: result.item,
                  identifier: new_tracker_id,
                  name: params.newPackageName,
                  autoAcceptTippAddition: params.autoAcceptTippAddition == 'on' ? true : false,
                  autoAcceptTippDelete: params.autoAcceptTippDelete == 'on' ? true : false,
                  autoAcceptTippUpdate: params.autoAcceptTippUpdate == 'on' ? true : false,
                  autoAcceptPackageUpdate: params.autoAcceptPackageChange == 'on' ? true : false)
          if ( grt.save() ) {
            globalSourceSyncService.initialiseTracker(grt);
            //Update INDEX ES
            dataloadService.updateFTIndexes();
          }
          else {
            log.error(grt.errors)
          }
          redirect(action:'index',params:[q:result.item.name])
          break;
        case 'existing':
          log.debug("merge remote package with existing local package...");
          def grt = new GlobalRecordTracker(
                  owner: result.item,
                  identifier: new_tracker_id,
                  name: result.item.name,
                  localOid: params.localPkg,
                  autoAcceptTippAddition: params.autoAcceptTippAddition == 'on' ? true : false,
                  autoAcceptTippDelete: params.autoAcceptTippDelete == 'on' ? true : false,
                  autoAcceptTippUpdate: params.autoAcceptTippUpdate == 'on' ? true : false,
                  autoAcceptPackageUpdate: params.autoAcceptPackageChange == 'on' ? true : false)
          if ( grt.save() ) {
            globalSourceSyncService.initialiseTracker(grt, params.localPkg);
          }
          else {
            log.error(grt.errors)
          }
          redirect(action:'index',params:[q:result.item.name])
          break;
        default:
          log.error("Unhandled package tracking type ${params.synctype}");
          break;
      }
    }

    result
  }

}
