package de.laser

import de.laser.titles.BookInstance
import de.laser.titles.DatabaseInstance
import de.laser.titles.JournalInstance
import de.laser.titles.TitleHistoryEvent
import de.laser.titles.TitleInstance
import com.k_int.kbplus.auth.User
import de.laser.controller.AbstractDebugController
import de.laser.helper.DateUtil
import de.laser.helper.RDConstants
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.annotation.Secured

import java.text.SimpleDateFormat

@Secured(['IS_AUTHENTICATED_FULLY'])
class TitleController extends AbstractDebugController {

    def springSecurityService
    def ESSearchService
    def contextService

    @Secured(['ROLE_USER'])
    def index() {
        redirect controller: 'title', action: 'list', params: params
    }

    @Secured(['ROLE_USER'])
    def list() {
        log.debug("titleSearch : ${params}");

        // TODO: copied from index() because no list() given | DB_QUERY

        Map<String, Object> result = [:]

        if (springSecurityService.isLoggedIn()) {
            params.rectype = ["EBookInstance", "JournalInstance", "BookInstance", "TitleInstance", "DatabaseInstance"] // Tells ESSearchService what to look for
            params.showAllTitles = true
            result.user = springSecurityService.getCurrentUser()
            params.max = params.max ?: result.user.getDefaultPageSize()


            if (params.search.equals("yes")) {
                //when searching make sure results start from first page
                params.offset = params.offset ? params.int('offset') : 0
                params.remove("search")
            }

            def old_q = params.q
            def old_sort = params.sort

            params.q = params.q ?: null
            params.sort = params.sort ?: "sortTitle.keyword"

            if (params.filter) {
                params.put(params.filter, params.q)
                params.q = null
            }

            result =  ESSearchService.search(params)
            //Double-Quoted search strings wont display without this
            params.q = old_q

            if(! old_q ) {
                params.remove('q')
            }
            if(! old_sort ) {
                params.remove('sort')
            }
        }

        result.editable = SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN')

        //log.debug(result)

        result
    }

  @Secured(['ROLE_ADMIN'])
  def findTitleMatches() { 
    // find all titles by n_title proposedTitle
    Map<String, Object> result = [:]
    if ( params.proposedTitle ) {
      // def proposed_title_key = de.laser.titles.TitleInstance.generateKeyTitle(params.proposedTitle)
      // result.titleMatches=de.laser.titles.TitleInstance.findAllByKeyTitle(proposed_title_key)
      String normalised_title = TitleInstance.generateNormTitle(params.proposedTitle)
      result.titleMatches = TitleInstance.findAllByNormTitleLike("${normalised_title}%")
    }
    result
  }

  @Secured(['ROLE_ADMIN'])
  @Deprecated
  /**
   * Is a GOKb functionality, no need to keep it in LAS:eR
   */
  def createTitle() {
    log.debug("Create new title for ${params.title}");
    //def new_title = new TitleInstance(title:params.title, impId:java.util.UUID.randomUUID().toString()
    def ti_status = RefdataValue.getByValueAndCategory('Current', RefdataCategory.TITLE_STATUS)
    def new_title =  ((params.typ=='Ebook') ? new BookInstance(title:params.title, impId:java.util.UUID.randomUUID().toString(), status: ti_status, type: RefdataValue.getByValueAndCategory('EBook', RefdataCategory.TI_MEDIUM)) :
              (params.typ=='Database' ? new DatabaseInstance(title:params.title, impId:java.util.UUID.randomUUID().toString(), status: ti_status, type: RefdataValue.getByValueAndCategory('Database', RefdataCategory.TI_MEDIUM)) : new JournalInstance(title:params.title, impId:java.util.UUID.randomUUID().toString(), status: ti_status, type: RefdataValue.getByValueAndCategory('Journal', RefdataCategory.TI_MEDIUM))))

    if ( new_title.save(flush:true) ) {
        new_title.impId = new_title.globalUID
        new_title.save(flush:true)
        log.debug("New title id is ${new_title.id}");
        redirect ( action:'edit', id:new_title.id);
    }
    else {
      log.error("Problem creating title: ${new_title.errors}");
      flash.message = "Problem creating title: ${new_title.errors}"
      redirect ( action:'findTitleMatches' )
    }
  }

    @Secured(['ROLE_USER'])
    Map<String,Object> show() {
        Map<String, Object> result = [:]

        result.editable = SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN')

        result.ti = TitleInstance.get(params.id)
        if (! result.ti) {
            flash.error = message(code:'titleInstance.error.notFound.es')
            redirect action: 'list'

            return
        }

        result.duplicates = reusedIdentifiers(result.ti);
        result.titleHistory = TitleHistoryEvent.executeQuery("select distinct thep.event from TitleHistoryEventParticipant as thep where thep.participant = :participant", [participant: result.ti] )

        result
    }

    private def reusedIdentifiers(title) {
        // Test for identifiers that are used accross multiple titles
        def duplicates = [:]
        def identifiers = title?.ids?.collect{it}

        identifiers.each { ident ->
            List<Identifier> dups = Identifier.findAll {
                value == ident.value && ti != null && ti.id != title.id && ti.status?.value == 'Current'
            }
            dups.each {
                if (duplicates."${it.ns.ns}:${it.value}") {
                    duplicates."${it.ns.ns}:${it.value}" += [it.ti]
                }
                else{
                    duplicates."${it.ns.ns}:${it.value}" = [it.ti]
                }
            }
        }

        /*
    identifiers.each{ident ->
      ident.occurrences.each{
        if(it.ti != title && it.ti!=null && it.ti.status?.value == 'Current'){
          if(duplicates."${ident.ns.ns}:${ident.value}"){
            duplicates."${ident.ns.ns}:${ident.value}" += [it.ti]
          }else{
            duplicates."${ident.ns.ns}:${ident.value}" = [it.ti]
          }
        }
      }

         */
        return duplicates
    }

    @Secured(['ROLE_ADMIN'])
  def batchUpdate() {
        log.debug( params.toMapString() )
        SimpleDateFormat formatter = DateUtil.getSDF_NoTime()
        User user = User.get(springSecurityService.principal.id)

      params.each { p ->
      if ( p.key.startsWith('_bulkflag.')&& (p.value=='on'))  {
        def tipp_id_to_edit = p.key.substring(10);
        def tipp_to_bulk_edit = TitleInstancePackagePlatform.get(tipp_id_to_edit)
        boolean changed = false

        if ( tipp_to_bulk_edit != null ) {
            def bulk_fields = [
                    [ formProp:'start_date', domainClassProp:'startDate', type:'date'],
                    [ formProp:'start_volume', domainClassProp:'startVolume'],
                    [ formProp:'start_issue', domainClassProp:'startIssue'],
                    [ formProp:'end_date', domainClassProp:'endDate', type:'date'],
                    [ formProp:'end_volume', domainClassProp:'endVolume'],
                    [ formProp:'end_issue', domainClassProp:'endIssue'],
                    [ formProp:'coverage_depth', domainClassProp:'coverageDepth'],
                    [ formProp:'coverage_note', domainClassProp:'coverageNote'],
                    [ formProp:'hostPlatformURL', domainClassProp:'hostPlatformURL']
            ]

            bulk_fields.each { bulk_field_defn ->
                if ( params["clear_${bulk_field_defn.formProp}"] == 'on' ) {
                    log.debug("Request to clear field ${bulk_field_defn.formProp}");
                    tipp_to_bulk_edit[bulk_field_defn.domainClassProp] = null
                    changed = true
                }
                else {
                    def proposed_value = params['bulk_'+bulk_field_defn.formProp]
                    if ( ( proposed_value != null ) && ( proposed_value.length() > 0 ) ) {
                        log.debug("Set field ${bulk_field_defn.formProp} to ${proposed_value}");
                        if ( bulk_field_defn.type == 'date' ) {
                            tipp_to_bulk_edit[bulk_field_defn.domainClassProp] = formatter.parse(proposed_value)
                        }
                        else {
                            tipp_to_bulk_edit[bulk_field_defn.domainClassProp] = proposed_value
                        }
                        changed = true
                    }
                }
            }
          if (changed)
             tipp_to_bulk_edit.save(flush: true)
        }
      }
    }

    redirect(controller:'title', action:'show', id:params.id);
  }

  @Secured(['ROLE_USER'])
  def history() {
    Map<String, Object> result = [:]
    boolean exporting = params.format == 'csv'

    if ( exporting ) {
      result.max = 9999999
      params.max = 9999999
      result.offset = 0
    }
    else {
        User user = User.get(springSecurityService.principal.id)
      result.max = params.max ? Integer.parseInt(params.max) : user.getDefaultPageSizeAsInteger()
      params.max = result.max
      result.offset = params.offset ? Integer.parseInt(params.offset) : 0;
    }

    result.titleInstance = TitleInstance.get(params.id)
    String base_query = 'from org.codehaus.groovy.grails.plugins.orm.auditable.AuditLogEvent as e where ( e.className = :instCls and e.persistedObjectId = :instId )'

    def limits = (!params.format||params.format.equals("html"))?[max:result.max, offset:result.offset]:[offset:0]

    def query_params = [ instCls: TitleInstance.class.name, instId: params.id]

    log.debug("base_query: ${base_query}, params:${query_params}, limits:${limits}");

    result.historyLines = org.codehaus.groovy.grails.plugins.orm.auditable.AuditLogEvent.executeQuery('select e '+base_query+' order by e.lastUpdated desc', query_params, limits);
    result.num_hl = org.codehaus.groovy.grails.plugins.orm.auditable.AuditLogEvent.executeQuery('select e.id '+base_query, query_params).size()
    result.formattedHistoryLines = []


    result.historyLines.each { hl ->

        def line_to_add = [:]
        def linetype = null

        switch(hl.className) {
          case TitleInstance.class.name :
              TitleInstance instance_obj = TitleInstance.get(hl.persistedObjectId);
            line_to_add = [ link: createLink(controller:'title', action: 'show', id:hl.persistedObjectId),
                            name: instance_obj.title,
                            lastUpdated: hl.lastUpdated,
                            propertyName: hl.propertyName,
                            actor: User.findByUsername(hl.actor),
                            oldValue: hl.oldValue,
                            newValue: hl.newValue
                          ]
            linetype = 'TitleInstance'
            break;
        }
        switch ( hl.eventName ) {
          case 'INSERT':
            line_to_add.eventName= "New ${linetype}"
            break;
          case 'UPDATE':
            line_to_add.eventName= "Updated ${linetype}"
            break;
          case 'DELETE':
            line_to_add.eventName= "Deleted ${linetype}"
            break;
          default:
            line_to_add.eventName= "Unknown ${linetype}"
            break;
        }
        result.formattedHistoryLines.add(line_to_add);
    }

    result
  }

  @Secured(['ROLE_ADMIN'])
  def dmIndex() {
    log.debug("dmIndex ${params}");

    if(params.search == "yes"){
      params.offset = 0
      params.remove("search")
    }
      User user = User.get(springSecurityService.principal.id)
    Map<String, Object> result = [:]
    result.max = params.max ? Integer.parseInt(params.max) : user.getDefaultPageSizeAsInteger()
    result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

    result.availableStatuses = RefdataCategory.getAllRefdataValues(RDConstants.TITLE_STATUS)
    def ti_status = null
    
    if(params.status){
      ti_status = result.availableStatuses.find { it.value == params.status }
    }
    
    def criteria = TitleInstance.createCriteria()
    result.hits = criteria.list(max: result.max, offset:result.offset){
        if(params.q){
          ilike("title","${params.q}%")
        }
        if(ti_status){
          eq('status',ti_status)
        }
        order("sortTitle", params.order?:'asc')
    }

    result.totalHits = result.hits.totalCount

    result
  }

}
