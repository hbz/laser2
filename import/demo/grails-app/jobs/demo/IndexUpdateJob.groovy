package demo



class IndexUpdateJob {

  def mongoService 
  def ESWrapperService

  static triggers = {
    // Delay 20 seconds, run every 10 mins.
    cron name:'cronTrigger', startDelay:20000, cronExpression: "0 0/10 * * * ?"
  }

  def execute() {

    log.debug("Execute IndexUpdateJob starting at ${new Date()}");
    def start_time = System.currentTimeMillis();

    def mdb = mongoService.getMongo().getDB('kbplus_ds_reconciliation')
    org.elasticsearch.groovy.node.GNode esnode = ESWrapperService.getNode()
    org.elasticsearch.groovy.client.GClient esclient = esnode.getClient()

    updateOrgs(mdb, esclient, com.k_int.kbplus.Org.class) { org ->
      def result = [:]
      result._id = org.impId
      result.name = org.name
      result.sector = org.sector
      result
    }

    updateOrgs(mdb, esclient, com.k_int.kbplus.TitleInstance.class) { ti ->
      def result = [:]
      result._id = ti.impId
      result.title = ti.title
      result
    }

    def elapsed = System.currentTimeMillis() - start_time;
    log.debug("IndexUpdateJob completed in ${elapsed}ms at ${new Date()}");
  }

  def updateOrgs(mdb, esclient, domain, recgen_closure) {

    def timestamp_record = mdb.timestamps.findOne(domain:domain.name)
    def max_ts_so_far = 0;

    if ( !timestamp_record ) {
      timestamp_record = [
        _id:new org.bson.types.ObjectId(),
        domain:domain.name,
        latest:0
      ]
      mdb.timestamps.save(timestamp_record);
    }

    // Class clazz = grailsApplication.getDomainClass(domain)
    Date from = new Date(timestamp_record.latest);
    def qry = domain.findAllByLastUpdatedGreaterThan(from);
    qry.each { i ->
      // log.debug(i);
      max_ts_so_far = i.lastUpdated.getTime() ?: 0

      def idx_record = recgen_closure(i)

      // log.debug("Generated record ${idx_record}");
      def future = esclient.index {
        index "kbplus"
        type "domain.name"
        id idx_record['_id']
        source idx_record
      }
    }

    timestamp_record.latest = max_ts_so_far
    mdb.timestamps.save(timestamp_record);

    log.debug("Completed processing on ${domain.name}");
  }
}
