package de.laser

import de.laser.titles.BookInstance
import com.k_int.kbplus.PendingChangeService
import de.laser.titles.TitleInstance
import com.k_int.kbplus.auth.Role
import com.k_int.kbplus.auth.User
import com.k_int.kbplus.auth.UserOrg
import com.k_int.kbplus.auth.UserRole
import de.laser.finance.CostItemElementConfiguration
import de.laser.properties.PropertyDefinition
import de.laser.properties.PropertyDefinitionGroup
import de.laser.properties.PropertyDefinitionGroupItem
import de.laser.api.v0.ApiToolkit
import de.laser.controller.AbstractDebugController
import de.laser.exceptions.CleanupException
import de.laser.helper.DebugAnnotation
import de.laser.helper.RDStore
import de.laser.helper.ServerUtils
import de.laser.helper.SessionCacheWrapper
import de.laser.system.SystemAnnouncement
import de.laser.system.SystemEvent
import de.laser.system.SystemMessage
import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.annotation.Secured
import grails.util.Holders
import groovy.sql.Sql
import groovy.util.slurpersupport.GPathResult
import groovy.xml.MarkupBuilder
import org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin
import org.hibernate.impl.SQLQueryImpl
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.multipart.commons.CommonsMultipartFile
import de.laser.helper.ConfigUtils

import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat

@Secured(['IS_AUTHENTICATED_FULLY'])
class AdminController extends AbstractDebugController {

    def springSecurityService
    def dataloadService
    def statsSyncService
    StatusUpdateService statusUpdateService
    def messageService
    def changeNotificationService
    def yodaService
    def sessionFactory
    def genericOIDService
    def deletionService
    def filterService

    def contextService
    def refdataService
    def propertyService
    def dataConsistencyService
    def organisationService
    def apiService

    def propertyInstanceMap = DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP

    @Secured(['ROLE_ADMIN'])
    def index() { }

    @Secured(['ROLE_ADMIN'])
    def systemAnnouncements() {
        Map<String, Object> result = [:]

        result.mailDisabled = grailsApplication.config.grails.mail.disabled

        if (params.id) {
            SystemAnnouncement sa = SystemAnnouncement.get(params.long('id'))

            if (sa) {
                if (params.cmd == 'edit') {
                    result.currentAnnouncement = sa
                }
                else if (params.cmd == 'publish') {
                    if (result.mailDisabled) {
                        flash.error = message(code: 'system.config.mail.disabled')
                    }
                    else if (sa.publish()) {
                        flash.message = message(code: 'announcement.published')
                    }
                    else {
                        flash.error = message(code: 'announcement.published_error')
                    }
                }
                else if (params.cmd == 'undo') {
                    sa.isPublished = false
                    if (sa.save(flush:true)) {
                        flash.message = message(code: 'announcement.undo')
                    }
                    else {
                        flash.error = message(code: 'announcement.undo_error')
                    }
                }
                else if (params.cmd == 'delete') {
                    if (sa.delete(flush: true)) {
                        flash.message = message(code: 'default.success')
                    }
                    else {
                        flash.error = message(code: 'default.delete.error.general.message')
                    }
                }
            }
        }
        result.numberOfCurrentRecipients = SystemAnnouncement.getRecipients().size()
        result.announcements = SystemAnnouncement.list(sort: 'lastUpdated', order: 'desc')
        result
    }

    @Secured(['ROLE_ADMIN'])
    def createSystemAnnouncement() {
        if (params.saTitle && params.saContent) {
            SystemAnnouncement sa
            boolean isNew = false

            if (params.saId) {
                sa = SystemAnnouncement.get(params.long('saId'))
            }
            if (!sa) {
                sa = new SystemAnnouncement()
                isNew = true
            }

            sa.title = params.saTitle
            sa.content = params.saContent
            sa.user = User.get(springSecurityService.principal.id)
            sa.isPublished = false

            if (sa.save(flush: true)) {
                flash.message = isNew ? message(code: 'announcement.created') : message(code: 'announcement.updated')
            }
            else {
                flash.error = message(code: 'default.save.error.message', args: [sa])
            }
        }
        else {
            flash.error = message(code: 'default.error')
        }
        redirect(action: 'systemAnnouncements')
    }

    @Secured(['ROLE_ADMIN'])
    def manageDeletedObjects() {
        Map<String, Object> result = [:]
        result.stats = [:]

        List<String> jobList = [
                'DocContext',
                //['GlobalRecordInfo', 'globalRecordInfoStatus'],
                'IssueEntitlement',
                'License',
                'Org',
                ['Package', 'packageStatus'],
                'Platform',
                'Subscription',
                'TitleInstance',
                'TitleInstancePackagePlatform',
                'Combo',
                'Doc'
        ]
        result.jobList = jobList

        jobList.each { job ->
            if (job instanceof String) {
                log.info('processing: ' + job)
                String query = "select count(obj) from ${job} obj join obj.status s where lower(s.value) like 'deleted'"
                result.stats."${job}" = Org.executeQuery( query )
            }
            else {
                log.info('processing: ' + job[0])
                String query = "select count(obj) from ${job[0]} obj join obj.${job[1]} s where lower(s.value) like 'deleted'"
                result.stats."${job[0]}" = Org.executeQuery( query )
            }
        }
        result
    }

    @DebugAnnotation(test = 'hasRole("ROLE_ADMIN") || hasAffiliation("INST_ADM")')
    @Secured(closure = {
        ctx.springSecurityService.getCurrentUser()?.hasRole('ROLE_ADMIN') ||
                ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_ADM")
    })
    def manageAffiliationRequests() {
        Map<String, Object> result = [:]

        result.user = User.get(springSecurityService.principal.id)
        result << organisationService.getPendingRequests(result.user, contextService.getOrg())

        result
    }

    @Secured(['ROLE_ADMIN'])
    def serverDifferences() {
      Map<String, Object> result = [:]
      result
    }

  @Secured(['ROLE_ADMIN'])
  def updatePendingChanges() {
  //Find all pending changes with license FK and timestamp after summer 14
  // For those with changeType: CustomPropertyChange, change it to PropertyChange
  // on changeDoc add value propertyOID with the value of OID
    String theDate = "01/05/2014 00:00:00";
    Date summer_date = new Date().parse("d/M/yyyy H:m:s", theDate)
    def criteria = PendingChange.createCriteria()
    def changes = criteria.list{
      isNotNull("license")
      ge("ts",summer_date)
      like("changeDoc","%changeType\":\"CustomPropertyChange\",%")
    }
    log.debug("Starting PendingChange Update. Found:${changes.size()}")

        changes.each{
            def parsed_change_info = JSON.parse(it.payload)
            parsed_change_info.changeType = PendingChangeService.EVENT_PROPERTY_CHANGE
            //parsed_change_info.changeType = "PropertyChange"
            //parsed_change_info.changeDoc.propertyOID = parsed_change_info.changeDoc.OID
            it.payload = parsed_change_info
            it.save(failOnError:true, flush: true)
        }
        log.debug("Pending Change Update Complete.")
        redirect(controller:'home')

    }

  @Secured(['ROLE_ADMIN'])
  def actionAffiliationRequest() {
      log.debug("actionMembershipRequest");
      UserOrg req = UserOrg.get(params.req);
      User user = User.get(springSecurityService.principal.id)

    if ( req != null ) {
      switch(params.act) {
        case 'approve':
          req.status = UserOrg.STATUS_APPROVED
          break;
        case 'deny':
          req.status = UserOrg.STATUS_REJECTED
          break;
        default:
          log.error("FLASH UNKNOWN CODE");
          break;
      }
      // req.actionedBy = user
      req.dateActioned = System.currentTimeMillis();
      req.save(flush: true)
    }
    else {
      log.error("FLASH");
    }
    redirect(action: "manageAffiliationRequests")
  }

    @Secured(['ROLE_ADMIN'])
    def hardDeletePkgs(){
        Map<String, Object> result = [:]
        //If we make a search while paginating return to start
        if(params.search == "yes"){
            params.offset = 0
            params.search = null
        }
        result.user = User.get(springSecurityService.principal.id)
        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0;

    if(params.id){
        Package pkg = Package.get(params.id)
      def conflicts_list = []

      if(pkg.documents){
        def document_map = [:]
        document_map.name = "Documents"
        document_map.details = []
        pkg.documents.each{
          document_map.details += ['text':it.owner.title]
        }
        document_map.action = ['actionRequired':false,'text':"References will be deleted"]
        conflicts_list += document_map
      }
      if(pkg.subscriptions){
        def subscription_map = [:]
        subscription_map.name = "Subscriptions"
        subscription_map.details = []
        pkg.subscriptions.each{
          subscription_map.details += ['link':createLink(controller:'subscription', action: 'show', id:it.subscription.id), 'text': it.subscription.name]
        }
        subscription_map.action = ['actionRequired':true,'text':"Unlink subscriptions. (IEs will be removed as well)"]
        if(subscription_map.details){
          conflicts_list += subscription_map
        }
      }
      if(pkg.tipps){
        def tipp_map = [:]
        tipp_map.name = "TIPPs"
        def totalIE = 0
        pkg.tipps.each{
          totalIE += IssueEntitlement.countByTipp(it)
        }
        tipp_map.details = [['text':"Number of TIPPs: ${pkg.tipps.size()}"],
                ['text':"Number of IEs: ${totalIE}"]]
        tipp_map.action = ['actionRequired':false,'text':"TIPPs and IEs will be deleted"]
        conflicts_list += tipp_map
      }
      result.conflicts_list = conflicts_list
      result.pkg = pkg

            render(template: "hardDeleteDetails",model:result)
        }else{

            def criteria = Package.createCriteria()
            result.pkgs = criteria.list(max: result.max, offset:result.offset){
                if(params.pkg_name){
                    ilike("name","${params.pkg_name}%")
                }
                order("name", params.order?:'asc')
            }
        }

        result
    }

  @Secured(['ROLE_ADMIN'])
  def performPackageDelete(){
   if (request.method == 'POST'){
      Package pkg = Package.get(params.id)
      Package.withTransaction { status ->
        log.info("Deleting Package ")
        log.info("${pkg.id}::${pkg}")
        pkg.pendingChanges.each{
          it.delete(flush:true)
        }
        pkg.documents.each{
          it.delete(flush:true)
        }
        pkg.orgs.each{
          it.delete(flush:true)
        }

                pkg.subscriptions.each{
                    it.delete(flush:true)
                }
                pkg.tipps.each{
                    it.delete(flush:true)
                }
                pkg.delete(flush:true)
            }
            log.info("Delete Complete.")
        }
        redirect controller: 'admin', action:'hardDeletePkgs'

    }

    @Secured(['ROLE_ADMIN'])
    def userMerge(){
        log.debug("AdminController :: userMerge :: ${params}");
        def usrMrgId = params.userToMerge == "null"?null:params.userToMerge
        def usrKeepId = params.userToKeep == "null"?null:params.userToKeep
        Map<String, Object> result = [:]
        try {
            log.debug("Determine user merge operation : ${request.method}");
            switch (request.method) {
                case 'GET':
                    if(usrMrgId && usrKeepId ){
                        User usrMrg = User.get(usrMrgId)
                        User usrKeep =  User.get(usrKeepId)
                        log.debug("Selected users : ${usrMrg}, ${usrKeep}");
                        result.userRoles = usrMrg.getAuthorities()
                        result.userAffiliations =  usrMrg.getAuthorizedAffiliations()
                        result.userMerge = usrMrg
                        result.userKeep = usrKeep
                    }else{
                        log.error("Missing keep/merge userid ${params}");
                        flash.error = "Please select'user to keep' and 'user to merge' from the dropdown."
                    }
                    log.debug("Get processing completed");
                    break;
                case 'POST':
                    log.debug("Post...");
                    if(usrMrgId && usrKeepId){
                        User usrMrg = User.get(usrMrgId)
                        User usrKeep =  User.get(usrKeepId)
                        boolean success = false
                        try{
                            log.debug("Copying user roles... from ${usrMrg} to ${usrKeep}");
                            success = copyUserRoles(usrMrg, usrKeep)
                            log.debug("Result of copyUserRoles : ${success}");
                        }catch(Exception e){
                            log.error("Exception while copying user roles.",e)
                        }
                        if(success){
                            log.debug("Success");
                            usrMrg.enabled = false
                            log.debug("Save disable and save merged user");
                            usrMrg.save(flush:true,failOnError:true)
                            flash.message = "Rights copying successful. User '${usrMrg.displayName}' is now disabled."
                        }else{
                            flash.error = "An error occured before rights transfer was complete."
                        }
                    }else{
                        flash.error = "Please select 'user to keep' and 'user to merge' from the dropdown."
                    }
                    break
                default:
                    break;
            }

       log.debug("Get all users");
       result.usersAll = User.list(sort:"display", order:"asc")
       log.debug("Get active users");
       String activeHQL = " from User as usr where usr.enabled=true or usr.enabled=null order by display asc"
       result.usersActive = User.executeQuery(activeHQL)
    }
    catch ( Exception e ) {
      log.error("Problem in user merge",e);
    }

        log.debug("Returning ${result}");
        result
    }

    @Secured(['ROLE_ADMIN'])
    def copyUserRoles(User usrMrg, User usrKeep){
        Set<Role> mergeRoles = usrMrg.getAuthorities()
        Set<UserOrg> mergeAffil = usrMrg.getAuthorizedAffiliations()
        Set<Role> currentRoles = usrKeep.getAuthorities()
        Set<UserOrg> currentAffil = usrKeep.getAuthorizedAffiliations()

        mergeRoles.each{ role ->
            if (!currentRoles.contains(role) && role.authority != "ROLE_YODA") {
                UserRole.create(usrKeep,role)
            }
        }
        mergeAffil.each{affil ->
            if(!currentAffil.contains(affil)){
                // We should check that the new role does not already exist
                UserOrg existing_affil_check = UserOrg.findByOrgAndUserAndFormalRole(affil.org,usrKeep,affil.formalRole)

        if ( existing_affil_check == null ) {
            log.debug("No existing affiliation");
            UserOrg newAffil = new UserOrg(org:affil.org,user:usrKeep,formalRole:affil.formalRole,status:affil.status)
          if(!newAffil.save(flush:true,failOnError:true)){
            log.error("Probem saving user roles");
            newAffil.errors.each { e ->
                log.error( e.toString() )
            }
            return false
          }
        }
        else {
          if (affil.status != existing_affil_check.status) {
            existing_affil_check.status = affil.status
            existing_affil_check.save(flush:true)
          }
          log.debug("Affiliation already present - skipping ${existing_affil_check}");
        }
      }
    }
    log.debug("copyUserRoles returning true");
    return true
  }

    @Secured(['ROLE_ADMIN'])
    def showAffiliations() {
        Map<String, Object> result = [:]
        result.user = User.get(springSecurityService.principal.id)
        result.users = User.list()

        withFormat {
            html {
                render(view:'showAffiliations',model:result)
            }
            json {
                def r2 = []
                result.users.each { u ->
                    def row = [:]
                    row.username = u.username
                    row.display = u.display
                    row.email = u.email
                    row.shibbScope = u.shibbScope
                    row.enabled = u.enabled
                    row.accountExpired = u.accountExpired
                    row.accountLocked = u.accountLocked
                    row.passwordExpired = u.passwordExpired
                    row.affiliations = []
                    u.affiliations.each { ua ->
                        row.affiliations.add( [org: ua.org.shortcode, status: ua.status, formalRole:formalRole?.authority] )
                    }
                    r2.add(row)
                }
                render r2 as JSON
            }
        }
    }

    @Secured(['ROLE_ADMIN'])
    def systemEvents() {
        Map<String, Object> result = [:]

        params.sort =   params.sort ?: 'created'
        params.order =  params.order ?: 'desc'
        params.max =    params.max ?: 1000

        result.events = SystemEvent.list(params)

        result
    }

    @Secured(['ROLE_YODA'])
    def dataCleanse() {
        // Sets nominal platform
        dataloadService.dataCleanse()
    }

    @Secured(['ROLE_ADMIN'])
    def updateQASubscriptionDates() {
        if (ServerUtils.getCurrentServer() in [ServerUtils.SERVER_QA, ServerUtils.SERVER_LOCAL]) {
            def updateReport = statusUpdateService.updateQASubscriptionDates()
            if(updateReport instanceof Boolean)
                flash.message = message(code:'subscription.qaTestDateUpdate.success')
            else {
                flash.error = message(code:'subscription.qaTestDateUpdate.updateError',updateReport)
            }
        }
        else flash.error = message(code:'subscription.qaTestDateUpdate.wrongServer')
        redirect(url: request.getHeader('referer'))
    }

    @Secured(['ROLE_ADMIN'])
    def statsSync() {
        log.debug("statsSync()")
        statsSyncService.doSync()
        redirect(controller:'home')
    }

  @Secured(['ROLE_ADMIN'])
  def manageContentItems() {
    Map<String, Object> result = [:]

        result.items = ContentItem.list()

        result
    }

    @Secured(['ROLE_ADMIN'])
    def databaseStatistics() {
        Map<String, Object> result = [:]

        def dataSource = Holders.grailsApplication.mainContext.getBean('dataSource')
        Sql sql = new Sql(dataSource)
        result.statistic = sql.rows("select * from count_rows_for_all_tables('public')")

        result
    }

    @Secured(['ROLE_ADMIN'])
    def dataConsistency() {
        Map<String, Object> result = [:]

        if (params.task) {
            List objIds = params.list('objId')

            if (params.task == 'merge' && params.objType == 'Org') {
                log.debug('dataConsistency( merge, ' + params.objType + ', ' + objIds + ' )')

                Org replacement = Org.get(objIds.first())
                for (def i = 1; i < objIds.size(); i++) {
                    deletionService.deleteOrganisation( Org.get(objIds[i]), replacement, false )
                }
            }
            if (params.task == 'delete' && params.objType == 'Org') {
                log.debug('dataConsistency( delete, ' + params.objType + ', ' + objIds + ' )')

                for (def i = 0; i < objIds.size(); i++) {
                    deletionService.deleteOrganisation( Org.get(objIds[i]), null, false )
                }
            }
            params.remove('task')
            params.remove('objType')
            params.remove('objId')

            redirect controller: 'admin', action: 'dataConsistency'
        }

        result.titles    = dataConsistencyService.checkTitles()

        result
    }

    @Secured(['ROLE_ADMIN'])
    def fileConsistency() {
        Map<String, Object> result = [:]

        result.filePath = ConfigUtils.getDocumentStorageLocation() ?: '/tmp/laser'

        Closure fileCheck = { Doc doc ->

            try {
                File test = new File("${result.filePath}/${doc.uuid}")
                if (test.exists() && test.isFile()) {
                    return true
                }
            }
            catch (Exception e) {
                return false
            }
        }

        // files

        result.listOfFiles = []
        result.listOfFilesMatchingDocs = []
        result.listOfFilesOrphaned = []

        result.listOfDocsInUse = []
        result.listOfDocsInUseOrphaned = []

        result.listOfDocsNotInUse= []
        result.listOfDocsNotInUseOrphaned = []

        try {
            File folder = new File("${result.filePath}")

            if (folder.exists()) {
                result.listOfFiles = folder.listFiles().collect{it.getName()}

                result.listOfFilesMatchingDocs = Doc.executeQuery(
                        'select doc from Doc doc where doc.contentType = :ct and doc.uuid in (:files)',
                        [ct: Doc.CONTENT_TYPE_FILE, files: result.listOfFiles]
                )
                List<String> matches = result.listOfFilesMatchingDocs.collect{ it.uuid }

                result.listOfFiles.each { ff ->
                    if (! matches.contains(ff)) {
                        result.listOfFilesOrphaned << ff
                    }
                }
                result.listOfFilesOrphaned.toSorted()
            }
        }
        catch (Exception e) {}

        // docs

        List<Doc> listOfDocs = Doc.executeQuery(
                'select doc from Doc doc where doc.contentType = :ct order by doc.id',
                [ct: Doc.CONTENT_TYPE_FILE]
        )

        result.listOfDocsInUse = Doc.executeQuery(
                'select distinct(doc) from DocContext dc join dc.owner doc where doc.contentType = :ct order by doc.id',
                [ct: Doc.CONTENT_TYPE_FILE]
        )

        result.listOfDocsNotInUse = listOfDocs - result.listOfDocsInUse

        result.listOfDocsInUse.each{ doc ->
            if (! fileCheck(doc)) {
                result.listOfDocsInUseOrphaned << doc
            }
        }
        result.listOfDocsNotInUse.each{ doc ->
            if (! fileCheck(doc)) {
                result.listOfDocsNotInUseOrphaned << doc
            }
        }

        // doc contexts

        result.numberOfDocContextsInUse = DocContext.executeQuery(
                'select distinct(dc) from DocContext dc join dc.owner doc where doc.contentType = :ct and (dc.status is null or dc.status != :del)',
                [ct: Doc.CONTENT_TYPE_FILE, del: RDStore.DOC_CTX_STATUS_DELETED]
        ).size()

        result.numberOfDocContextsDeleted = DocContext.executeQuery(
                'select distinct(dc) from DocContext dc join dc.owner doc where doc.contentType = :ct and dc.status = :del',
                [ct: Doc.CONTENT_TYPE_FILE, del: RDStore.DOC_CTX_STATUS_DELETED]
        ).size()

        result
    }

    @Secured(['ROLE_ADMIN'])
    def recoveryDoc() {
        Map<String, Object> result = [:]

        result.filePath = ConfigUtils.getDocumentStorageLocation() ?: '/tmp/laser'

        Closure fileCheck = { Doc doc ->

            try {
                File test = new File("${result.filePath}/${doc.uuid}")
                if (test.exists() && test.isFile()) {
                    return true
                }
            }
            catch (Exception e) {
                return false
            }
        }

        Doc doc = Doc.get(Long.parseLong(params.docID))

        if (!fileCheck(doc)) {
            result.doc = doc

            List docs = Doc.findAllWhere(
                    status: doc.status,
                    type: doc.type,
                    content: doc.content,
                    contentType: doc.contentType,
                    title: doc.title,
                    filename: doc.filename,
                    mimeType: doc.mimeType,
                    migrated: doc.migrated,
                    owner: doc.owner
            )
            result.docsToRecovery = docs
        }
        result

    }

    @Secured(['ROLE_ADMIN'])
    def processRecoveryDoc() {
        Map<String, Object> result = [:]

        result.filePath = ConfigUtils.getDocumentStorageLocation() ?: '/tmp/laser'

        Closure fileCheck = { Doc doc ->

            try {
                File test = new File("${result.filePath}/${doc.uuid}")
                if (test.exists() && test.isFile()) {
                    return true
                }
            }
            catch (Exception e) {
                return false
            }
        }

        Doc docWithoutFile = Doc.get(Long.parseLong(params.sourceDoc))
        Doc docWithFile = Doc.get(Long.parseLong(params.targetDoc))

        if (!fileCheck(docWithoutFile) && fileCheck(docWithFile)) {

            Path source = new File("${result.filePath}/${docWithFile.uuid}").toPath()
            Path target = new File("${result.filePath}/${docWithoutFile.uuid}").toPath()
            Files.copy(source, target)

            if(fileCheck(docWithoutFile)){
                flash.message = "Datei erfolgreich wiederhergestellt!"
            }else{
                flash.error = "Datei nicht erfolgreich wiederhergestellt!"
            }
        }else {
            flash.error = "Keine Quell-Datei gefunden um Wiederherzustellen!"
        }
        redirect(action:'recoveryDoc', params: ['docID': docWithoutFile.id])

    }

  @Secured(['ROLE_ADMIN'])
  def newContentItem() {
    Map<String, Object> result = [:]
    if ( ( params.key != null ) && ( params.content != null ) && ( params.key.length() > 0 ) && ( params.content.length() > 0 ) ) {

            String locale = ( ( params.locale != null ) && ( params.locale.length() > 0 ) ) ? params.locale : ''

            if ( ContentItem.findByKeyAndLocale(params.key,locale) != null ) {
                flash.message = 'Content item already exists'
            }
            else {
                ContentItem.lookupOrCreate(params.key, locale, params.content)
            }
        }

        redirect(action:'manageContentItems')

        result
    }

  @Secured(['ROLE_ADMIN'])
  def editContentItem() {
    Map<String, Object> result = [:]
    def idparts = params.id?.split(':')
    if ( idparts.length > 0 ) {
      def key = idparts[0]
      String locale = idparts.length > 1 ? idparts[1] : ''

            ContentItem contentItem = ContentItem.findByKeyAndLocale(key,locale)
            if ( contentItem != null ) {
                result.contentItem = contentItem
            }
            else {
                flash.message="Unable to locate content item for key ${idparts}"
                redirect(action:'manageContentItems');
            }
            if ( request.method.equalsIgnoreCase("post")) {
                contentItem.content = params.content
                contentItem.save(flush:true)
                messageService.update(key,locale) // TODO: refactoring legacy
                redirect(action:'manageContentItems');
            }
        }
        else {
            flash.message="Unable to parse content item id ${params.id} - ${idparts}"
            redirect(action:'manageContentItems');
        }

        result
    }

    @Secured(['ROLE_ADMIN'])
    def forceSendNotifications() {
        changeNotificationService.aggregateAndNotifyChanges()
        redirect(controller:'home')
    }

    @Secured(['ROLE_ADMIN'])
    def tippTransfer(){
        log.debug("tippTransfer :: ${params}")
        Map<String, Object> result = [:]
        result.error = []

    if(params.sourceTIPP && params.targetTI){
        TitleInstance ti = TitleInstance.get(params.long("targetTI"))
        TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(params.long("sourceTIPP"))
      if(ti && tipp){
        tipp.title = ti
        try{
          tipp.save(flush:true,failOnError:true)
          result.success = true
        }catch(Exception e){
            log.error( e.toString() )
          result.error += "An error occured while saving the changes."
        }
      }else{
        if(!ti) result.error += "No TitleInstance found with identifier: ${params.targetTI}."
        if(!tipp) result.error += "No TIPP found with identifier: ${params.sourceTIPP}" 
      }
    }

        result
    }

    @Secured(['ROLE_ADMIN'])
    def ieTransfer(){
        log.debug( params.toMapString() )
        Map<String, Object> result = [:]
        if(params.sourceTIPP && params.targetTIPP){
            result.sourceTIPPObj = TitleInstancePackagePlatform.get(params.sourceTIPP)
            result.targetTIPPObj = TitleInstancePackagePlatform.get(params.targetTIPP)
        }

        if(params.transfer == "Go" && result.sourceTIPPObj && result.targetTIPPObj){
            log.debug("Tranfering ${IssueEntitlement.countByTipp(result.sourceTIPPObj)} IEs from ${result.sourceTIPPObj} to ${result.targetTIPPObj}")
            def sourceIEs = IssueEntitlement.findAllByTipp(result.sourceTIPPObj)
            sourceIEs.each{
                it.setTipp(result.targetTIPPObj)
                it.save(flush:true)
            }
        }

        result
    }

    @Secured(['ROLE_YODA'])
    Map<String,Object> listDuplicateTitles() {
        SessionCacheWrapper sessionCache = contextService.getSessionCache()
        Map<String,Object> result = sessionCache.get("AdminController/titleRemap/result")
        if(!result) {
            result = yodaService.listDuplicateTitles()
            sessionCache.put("AdminController/titleRemap/result",result)
        }
        result
    }

    @Secured(['ROLE_YODA'])
    def executeTiCleanup() {
        log.debug("WARNING: bulk deletion of title entries triggered! Start nuking!")
        SessionCacheWrapper sessionCache = contextService.getSessionCache()
        Map<String,Object> result = (Map<String,Object>) sessionCache.get("AdminController/titleRemap/result")
        if(result) {
            try {
                yodaService.executeTiCleanup(result)
                sessionCache.remove("AdminController/titleRemap/result")
                redirect(controller:'title',action: 'index')
            }
            catch (CleanupException e) {
                log.error("failure on merging titles ... rollback!")
                e.printStackTrace()
                redirect(controller:'admin',action: 'listDuplicateTitles')
            }
        }
        else {
            log.error("data missing, rebuilding data")
            redirect(controller:'admin',action: 'listDuplicateTitles')
        }
    }

    @Secured(['ROLE_ADMIN'])
    def orgsExport() {
        Date now = new Date()
        File basicDataDir = new File(ConfigUtils.getBasicDataPath())
        if(basicDataDir) {
            GPathResult oldBase
            XmlSlurper slurper = new XmlSlurper()
            Date lastDumpDate
            List<File> dumpFiles = basicDataDir.listFiles(new FilenameFilter() {
                @Override
                boolean accept(File dir, String name) {
                    return name.matches("${ConfigUtils.getOrgDumpFileNamePattern()}.*")
                }
            })
            if(dumpFiles.size() > 0) {
                dumpFiles.toSorted { f1, f2 ->
                    f1.lastModified() <=> f2.lastModified()
                }
                File lastDump = dumpFiles.last()
                lastDumpDate = new Date(lastDump.lastModified())
                oldBase = slurper.parse(lastDump)
            }
            else {
                File f = new File("${ConfigUtils.getBasicDataPath()}${ConfigUtils.getBasicDataFileName()}")
                lastDumpDate = new Date(f.lastModified())
                if(f.exists()) {
                    //complicated way - determine most recent org and user creation dates
                    oldBase = slurper.parse(f)
                }
            }
            if(oldBase) {
                SimpleDateFormat sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss.S')
                Set<Org> newOrgData = []
                Set<User> newUserData = []
                Org.getAll().each { Org dbOrg ->
                    def correspOrg = oldBase.organisations.org.find { orgNode ->
                        orgNode.globalUID == dbOrg.globalUID
                    }
                    if(correspOrg) {
                        if(dbOrg.lastUpdated > lastDumpDate) {
                            newOrgData << dbOrg
                        }
                    }
                    else if(dbOrg.lastUpdated > lastDumpDate) {
                        newOrgData << dbOrg
                    }
                }
                User.getAll().each { User dbUser ->
                    def correspUser = oldBase.users.user.find { userNode ->
                        userNode.username == dbUser.username
                    }
                    if(correspUser) {
                        if(dbUser.lastUpdated > lastDumpDate) {
                            newUserData << dbUser
                        }
                    }
                    else if(dbUser.lastUpdated > lastDumpDate) {
                        newUserData << dbUser
                    }
                }
                //data collected: prepare export!
                if(newOrgData || newUserData) {
                    //List<Person> newPersonData = Person.executeQuery('select pr.prs from PersonRole pr where pr.org in :org',[org:newOrgData])
                    File newDump = new File("${ConfigUtils.getBasicDataPath()}${ConfigUtils.getOrgDumpFileNamePattern()}${now.format("yyyy-MM-dd")}${ConfigUtils.getOrgDumpFileExtension()}")
                    StringBuilder exportReport = new StringBuilder()
                    exportReport.append("<p>Folgende Organisationen wurden erfolgreich exportiert: <ul><li>")
                    newDump.withWriter { writer ->
                        MarkupBuilder orgDataBuilder = new MarkupBuilder(writer)
                        orgDataBuilder.data {
                            organisations {
                                newOrgData.each { Org o ->
                                    org {
                                        globalUID(o.globalUID)
                                        name(o.name)
                                        shortname(o.shortname)
                                        shortcode(o.shortcode)
                                        sortname(o.sortname)
                                        url(o.url)
                                        urlGov(o.urlGov)
                                        importSource(o.importSource)
                                        lastImportDate(o.lastImportDate)
                                        gokbId(o.gokbId)
                                        comment(o.comment)
                                        ipRange(o.ipRange)
                                        scope(o.scope)
                                        dateCreated(o.dateCreated)
                                        lastUpdated(o.lastUpdated)
                                        categoryId(o.categoryId)
                                        sector {
                                            if(o.sector) {
                                                rdc(o.sector.owner.desc)
                                                rdv(o.sector.value)
                                            }
                                        }
                                        status {
                                            if(o.status) {
                                                rdc(o.status.owner.desc)
                                                rdv(o.status.value)
                                            }
                                        }
                                        membership {
                                            if(o.membership) {
                                                rdc(o.membership.owner.desc)
                                                rdv(o.membership.value)
                                            }
                                        }
                                        countryElem {
                                            if(o.country) {
                                                rdc(o.country.owner.desc)
                                                rdv(o.country.value)
                                            }
                                        }
                                        region {
                                            if(o.region) {
                                                rdc(o.region.owner.desc)
                                                rdv(o.region.value)
                                            }
                                        }
                                        libraryNetwork {
                                            if(o.libraryNetwork) {
                                                rdc(o.libraryNetwork.owner.desc)
                                                rdv(o.libraryNetwork.value)
                                            }
                                        }
                                        funderType {
                                            if(o.funderType) {
                                                rdc(o.funderType.owner.desc)
                                                rdv(o.funderType.value)
                                            }
                                        }
                                        libraryType {
                                            if(o.libraryType) {
                                                rdc(o.libraryType.owner.desc)
                                                rdv(o.libraryType.value)
                                            }
                                        }
                                        costConfigurations {
                                            CostItemElementConfiguration.findAllByForOrganisation(o).each { ciecObj ->
                                                CostItemElementConfiguration ciec = (CostItemElementConfiguration) ciecObj
                                                costConfiguration {
                                                    rdc(ciec.costItemElement.owner.desc)
                                                    rdv(ciec.costItemElement.value)
                                                    elementSign {
                                                        rdc(ciec.elementSign.owner.desc)
                                                        rdv(ciec.elementSign.value)
                                                    }
                                                }
                                            }
                                        }
                                        ids {
                                            o.ids.each { idObj ->
                                                // TODO [ticket=1789]
                                                //IdentifierOccurrence idOcc = (IdentifierOccurrence) idObj
                                                id (namespace: idObj.ns.ns, value: idObj.value)
                                            }
                                        }
                                        //outgoing/ingoingCombos: assembled in branch combos
                                        //prsLinks, affiliations, contacts and addresses done on own branches respectively
                                        orgTypes {
                                            o.orgType.each { ot ->
                                                orgType {
                                                    rdc(ot.owner.desc)
                                                    rdv(ot.value)
                                                }
                                            }
                                        }
                                        settings {
                                            List<OrgSetting> os = OrgSetting.findAllByOrg(o)
                                            os.each { st ->
                                                switch(st.key.type) {
                                                    case RefdataValue:
                                                        if(st.rdValue) {
                                                            setting {
                                                                name(st.key)
                                                                rdValue {
                                                                    rdc(st.rdValue.owner.desc)
                                                                    rdv(st.rdValue.value)
                                                                }
                                                            }
                                                        }
                                                        break
                                                    case Role:
                                                        if(st.roleValue) {
                                                            setting {
                                                                name(st.key)
                                                                roleValue(st.roleValue.authority)
                                                            }
                                                        }
                                                        break
                                                    default: setting{
                                                        name(st.key)
                                                        value(st.getValue())
                                                    }
                                                        break
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            affiliations {
                                UserOrg.findAllByUserInList(newUserData.toList()).each { userOrg ->
                                    affiliation {
                                        user(userOrg.user.username)
                                        org(userOrg.org.globalUID)
                                        status(userOrg.status)
                                        if(userOrg.formalRole) {
                                            formalRole(userOrg.formalRole.authority)
                                        }
                                        if(userOrg.dateActioned) {
                                            dateActioned(userOrg.dateActioned)
                                        }
                                        if(userOrg.dateRequested) {
                                            dateRequested(userOrg.dateRequested)
                                        }
                                    }
                                }
                            }
                            combos {
                                Combo.executeQuery('select c from Combo c where c.fromOrg in :fromOrg or c.toOrg in :toOrg',[fromOrg: newOrgData.toList(),toOrg: newOrgData.toList()]).each { c ->
                                    if(c.type) {
                                        combo {
                                            status {
                                                if (c.status) {
                                                    rdc(c.status.owner.desc)
                                                    rdv(c.status.value)
                                                }
                                            }
                                            type{
                                                rdc(c.type.owner.desc)
                                                rdv(c.type.value)
                                            }
                                            fromOrg(c.fromOrg.globalUID)
                                            toOrg(c.toOrg.globalUID)
                                        }
                                    }
                                }
                            }
                            /*persons {
                              newPersonData.each { Person p ->
                                person {
                                  log.debug("now processing ${p.id}")
                                  globalUID(p.globalUID)
                                  title(p.title)
                                  firstName(p.first_name)
                                  middleName(p.middle_name)
                                  lastName(p.last_name)
                                  if(p.tenant)
                                    tenant(p.tenant.globalUID)
                                  if(p.gender) {
                                    gender {
                                      rdc(p.gender.owner.desc)
                                      rdv(p.gender.value)
                                    }
                                  }
                                  if(p.isPublic) {
                                    isPublic {
                                      'Yes'
                                    }
                                  }
                                  if(p.contactType) {
                                    contactType {
                                      rdc(p.contactType.owner.desc)
                                      rdv(p.contactType.value)
                                    }
                                  }
                                  if(p.roleType) {
                                    roleType {
                                      rdc(p.roleType.owner.desc)
                                      rdv(p.roleType.value)
                                    }
                                  }
                                }
                              }
                            }
                            personRoles {
                              PersonRole.findAllByOrgInList(newOrgData.toList()).each { link ->
                                personRole {
                                  org(link.org.globalUID)
                                  prs(link.prs.globalUID)
                                  if(link.positionType) {
                                    positionType {
                                      rdc(link.positionType.owner.desc)
                                      rdv(link.positionType.value)
                                    }
                                  }
                                  if(link.functionType) {
                                    functionType {
                                      rdc(link.functionType.owner.desc)
                                      rdv(link.functionType.value)
                                    }
                                  }
                                  if(link.responsibilityType) {
                                    responsibilityType {
                                      rdc(link.responsibilityType.owner.desc)
                                      rdv(link.responsibilityType.value)
                                    }
                                  }
                                }
                              }
                            }*/
                            users {
                                newUserData.each { User u ->
                                    user {
                                        username(u.username)
                                        display(u.display)
                                        password(u.password)
                                        email(u.email)
                                        shibbScope(u.shibbScope)
                                        enabled(u.enabled)
                                        accountExpired(u.accountExpired)
                                        accountLocked(u.accountLocked)
                                        passwordExpired(u.passwordExpired)
                                        dateCreated(u.dateCreated)
                                        lastUpdated(u.lastUpdated)
                                        //affiliations done already on organisations
                                        roles {
                                            u.roles.each { rObj ->
                                                UserRole r = (UserRole) rObj
                                                role(r.role.authority)
                                            }
                                        }
                                        settings {
                                            List<UserSetting> us = UserSetting.findAllByUser(u)
                                            us.each { st ->
                                                switch(st.key.type) {
                                                    case Org: setting{
                                                        name(st.key)
                                                        org(st.orgValue ? st.orgValue.globalUID : ' ')
                                                    }
                                                        break
                                                    case RefdataValue:
                                                        if(st.rdValue) {
                                                            setting {
                                                                name(st.key)
                                                                rdValue {
                                                                    rdc(st.rdValue.owner.desc)
                                                                    rdv(st.rdValue.value)
                                                                }
                                                            }
                                                        }
                                                        break
                                                    default: setting{
                                                        name(st.key)
                                                        value(st.getValue())
                                                    }
                                                        break
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            /*addresses {
                              Address.executeQuery('select a from Address a where a.prs in :prsList or a.org in :orgList',[prsList:newPersonData,orgList:newOrgData]).each { a ->
                                address {
                                  if(a.org) org(a.org.globalUID)
                                  if(a.prs) prs(a.prs.globalUID)
                                  street1(a.street_1)
                                  street2(a.street_2)
                                  zipcode(a.zipcode)
                                  city(a.city)
                                  pob(a.pob)
                                  pobZipcode(a.pobZipcode)
                                  pobCity(a.pobCity)
                                  if(a.state) {
                                    state {
                                      rdc(a.state.owner.desc)
                                      rdv(a.state.value)
                                    }
                                  }
                                  if(a.country) {
                                    countryElem {
                                      rdc(a.country.owner.desc)
                                      rdv(a.country.value)
                                    }
                                  }
                                  type {
                                    rdc(a.type.owner.desc)
                                    rdv(a.type.value)
                                  }
                                  if(a.name) name(a.name)
                                  if(a.additionFirst) additionFirst(a.additionFirst)
                                  if(a.additionSecond) additionSecond(a.additionSecond)
                                }
                              }
                            }
                            contacts {
                              Contact.executeQuery('select c from Contact c where c.prs in :prsList or c.org in :orgList',[prsList:newPersonData,orgList:newOrgData]).each { c ->
                                contact {
                                  if(c.org) org(c.org.globalUID)
                                  if(c.prs) prs(c.prs.globalUID)
                                  content(c.content)
                                  contentType {
                                    rdc(c.contentType.owner.desc)
                                    rdv(c.contentType.value)
                                  }
                                  type {
                                    rdc(c.type.owner.desc)
                                    rdv(c.type.value)
                                  }
                                }
                              }
                            }*/
                        }
                    }
                    exportReport.append(newOrgData.join("</li><li>")+"</ul></p>")
                    exportReport.append("<p>Folgende Nutzer wurden erfolgreich exportiert: <ul><li>"+newUserData.join("</li><li>")+"</ul></p>")
                    flash.message = exportReport.toString()
                }
                else {
                    flash.error = "Es liegen keine Daten zum Export vor!"
                }
            }
        }
        else {
            log.error("Basic data dump directory missing - PANIC!")
            flash.error = "Das Verzeichnis der Exportdaten fehlt! Bitte anlegen und Datenbestand kontrollieren, ggf. neuen Basisdatensatz anlegen"
        }
        redirect controller: 'myInstitution', action: 'dashboard'
    }

    @Secured(['ROLE_ADMIN'])
    def orgsImport() {
        File basicDataDir = new File(ConfigUtils.getDocumentStorageLocation() + "/basic_data_dumps/")
        List<File> dumpFiles = basicDataDir.listFiles(new FilenameFilter() {
            @Override
            boolean accept(File dir, String name) {
                return name.matches("${ConfigUtils.getOrgDumpFileNamePattern()}.*")
            }
        })
        if(dumpFiles.size() > 0) {
            dumpFiles.toSorted { f1, f2 ->
                f1.lastModified() <=> f2.lastModified()
            }
            File lastDump = dumpFiles.last()
            apiService.setupBasicData(lastDump)
            flash.message = "Daten wurden erfolgreich aufgesetzt!"
        }
        else {
            flash.error = "Es liegt kein inkrementeller Dump vor ... haben Sie vorher die Daten ausgeschrieben?"
        }
        redirect controller: 'myInstitution', action: 'dashboard'
    }

    def cleanUpGorm() {
        log.debug("Clean up GORM");
        def session = sessionFactory.currentSession
        session.flush()
        session.clear()
        propertyInstanceMap.get().clear()
    }

    @Secured(['ROLE_ADMIN'])
    def manageOrganisations() {
        Map<String, Object> result = [:]

        if (params.cmd == 'changeApiLevel') {
            Org target = (Org) genericOIDService.resolveOID(params.target)

            if (ApiToolkit.getAllApiLevels().contains(params.apiLevel)) {
                ApiToolkit.setApiLevel(target, params.apiLevel)
            }
            else if (params.apiLevel == 'Kein Zugriff') {
                ApiToolkit.removeApiLevel(target)
            }
            target.lastUpdated = new Date()
            target.save(flush:true)
        }
        else if (params.cmd == 'deleteCustomerType') {
            Org target = (Org) genericOIDService.resolveOID(params.target)
            def oss = OrgSetting.get(target, OrgSetting.KEYS.CUSTOMER_TYPE)
            if (oss != OrgSetting.SETTING_NOT_FOUND) {
                oss.delete(flush:true)
            }
            target.lastUpdated = new Date()
            target.save(flush:true)
        }
        else if (params.cmd == 'changeCustomerType') {
            Org target = (Org) genericOIDService.resolveOID(params.target)
            Role customerType = Role.get(params.customerType)

            def osObj = OrgSetting.get(target, OrgSetting.KEYS.CUSTOMER_TYPE)

            if (osObj != OrgSetting.SETTING_NOT_FOUND) {
                OrgSetting oss = (OrgSetting) osObj
                // ERMS-1615
                if (oss.roleValue.authority in ['ORG_INST', 'ORG_BASIC_MEMBER'] && customerType.authority == 'ORG_INST_COLLECTIVE') {
                    log.debug('changing ' + oss.roleValue.authority + ' to ' + customerType.authority)

                    // orgRole = subscriber
                    List<OrgRole> subscriberRoles = OrgRole.executeQuery(
                            'select ro from OrgRole ro ' +
                                    'where ro.org = :org and ro.sub is not null and ro.roleType.value like \'Subscriber\'',
                            [ org: target ]
                    )

                    List<OrgRole> conSubscriberRoles = OrgRole.executeQuery(
                            'select ro from OrgRole ro ' +
                                    'where ro.org = :org and ro.sub is not null and ro.roleType.value in (:roleTypes)',
                            [ org: target, roleTypes: ['Subscriber_Consortial', 'Subscriber_Consortial_Hidden'] ]
                    )


                    subscriberRoles.each{ role ->
                        if (role.sub._getCalculatedType() == Subscription.TYPE_LOCAL) {
                            role.setRoleType(RDStore.OR_SUBSCRIPTION_COLLECTIVE)
                            role.save(flush:true)

                            role.sub.type = RDStore.SUBSCRIPTION_TYPE_LOCAL
                            role.sub.save(flush:true)
                        }
                    }
                    /*
                      todo: IGNORED for 0.20

                    }*/
                    conSubscriberRoles.each { role ->
                        if (role.sub._getCalculatedType() == Subscription.TYPE_PARTICIPATION) {
                            OrgRole newRole = new OrgRole(
                                    org: role.org,
                                    sub: role.sub,
                                    roleType: RDStore.OR_SUBSCRIPTION_COLLECTIVE
                            )
                            newRole.save(flush:true)

                            // keep consortia type
                            //role.sub.type = RDStore.SUBSCRIPTION_TYPE_LOCAL
                            //role.sub.save(flush:true)
                        }
                    }
                }
                oss.roleValue = customerType
                oss.save(flush:true)

            }
            else {
                OrgSetting.add(target, OrgSetting.KEYS.CUSTOMER_TYPE, customerType)
            }
            target.lastUpdated = new Date()
            target.save(flush:true)
        }
        else if (params.cmd == 'changeGascoEntry') {
            Org target = (Org) genericOIDService.resolveOID(params.target)
            RefdataValue option = (RefdataValue) genericOIDService.resolveOID(params.gascoEntry)

            if (target && option) {
                def oss = OrgSetting.get(target, OrgSetting.KEYS.GASCO_ENTRY)

                if (oss != OrgSetting.SETTING_NOT_FOUND) {
                    oss.rdValue = option
                    oss.save(flush: true)
                } else {
                    OrgSetting.add(target, OrgSetting.KEYS.GASCO_ENTRY, option)
                }
            }
            target.lastUpdated = new Date()
            target.save(flush:true)
        }
        else if (params.cmd == 'changeLegalInformation') {
            Org target = (Org) genericOIDService.resolveOID(params.target)

            if (target) {
                target.createdBy = Org.get(params.createdBy)
                target.legallyObligedBy = Org.get(params.legallyObligedBy)
            }
            target.lastUpdated = new Date()
            target.save(flush:true)
        }

        def fsq = filterService.getOrgQuery(params)
        result.orgList = Org.executeQuery(fsq.query, fsq.queryParams, params)
        result.orgListTotal = result.orgList.size()

        result.allConsortia = Org.executeQuery(
                "select o from OrgSetting os join os.org o where os.key = 'CUSTOMER_TYPE' and os.roleValue.authority  = 'ORG_CONSORTIUM' order by o.sortname, o.name"
        )
        result
    }

    @Secured(['ROLE_ADMIN'])
    def manageNamespaces() {
        IdentifierNamespace idnsInstance = new IdentifierNamespace(params)
        Map detailsStats = [:]

        switch (request.method) {
            case 'GET':
                idnsInstance = (IdentifierNamespace) genericOIDService.resolveOID(params.oid)

                if (params.cmd == 'deleteNamespace') {
                    if (idnsInstance && Identifier.countByNs(idnsInstance) == 0) {
                        try {
                            idnsInstance.delete(flush:true)
                            flash.message = "Namensraum ${idnsInstance.ns} wurde gelöscht."
                        }
                        catch (Exception e) {
                            flash.message = "Namensraum ${idnsInstance.ns} konnte nicht gelöscht werden."
                        }
                    }
                }
                else if (params.cmd == 'details') {

                    if (idnsInstance) {
                        detailsStats.putAt(g.createLink(controller:'license', action:'show'),
                                IdentifierNamespace.executeQuery(
                                        "select i.value, i.lic.id from Identifier i join i.ns idns where idns = :idns and i.lic is not null order by i.value, i.lic.id", [idns: idnsInstance]))
                        detailsStats.putAt(g.createLink(controller:'org', action:'show'),
                                IdentifierNamespace.executeQuery(
                                        "select i.value, i.org.id from Identifier i join i.ns idns where idns = :idns and i.org is not null order by i.value, i.org.id", [idns: idnsInstance]))
                        detailsStats.putAt(g.createLink(controller:'package', action:'show'),
                                IdentifierNamespace.executeQuery(
                                        "select i.value, i.pkg.id from Identifier i join i.ns idns where idns = :idns and i.pkg is not null order by i.value, i.pkg.id", [idns: idnsInstance]))
                        detailsStats.putAt(g.createLink(controller:'subscription', action:'show'),
                                IdentifierNamespace.executeQuery(
                                        "select i.value, i.sub.id from Identifier i join i.ns idns where idns = :idns and i.sub is not null order by i.value, i.sub.id", [idns: idnsInstance]))
                        detailsStats.putAt(g.createLink(controller:'title', action:'show'),
                                IdentifierNamespace.executeQuery(
                                        "select i.value, i.ti.id from Identifier i join i.ns idns where idns = :idns and i.ti is not null order by i.value, i.ti.id", [idns: idnsInstance]))
                        detailsStats.putAt(g.createLink(controller:'tipp', action:'show'),
                                IdentifierNamespace.executeQuery(
                                        "select i.value, i.tipp.id from Identifier i join i.ns idns where idns = :idns and i.tipp is not null order by i.value, i.tipp.id", [idns: idnsInstance]))
                    }
                }
                break

            case 'POST':
                if (IdentifierNamespace.findByNsIlike(params.ns) || ! idnsInstance.save()) {

                    if(IdentifierNamespace.findByNsIlike(params.ns)) {
                        flash.error = message(code: 'identifier.namespace.exist', args:[params.ns])
                        break
                    }
                    return
                }
                else {
                    flash.message = message(code: 'default.created.message', args: [message(code: 'identifier.namespace.label'), idnsInstance.ns])
                }
                break
        }

        SQLQueryImpl sqlQuery = (sessionFactory.currentSession).createSQLQuery("""
SELECT * FROM (
      SELECT idns.idns_ns,
             idns.idns_id,
             sum(CASE WHEN i.id_lic_fk is null THEN 0 ELSE 1 END)  lic,
             sum(CASE WHEN i.id_org_fk is null THEN 0 ELSE 1 END)  org,
             sum(CASE WHEN i.id_pkg_fk is null THEN 0 ELSE 1 END)  pkg,
             sum(CASE WHEN i.id_sub_fk is null THEN 0 ELSE 1 END)  sub,
             sum(CASE WHEN i.id_ti_fk is null THEN 0 ELSE 1 END)   title,
             sum(CASE WHEN i.id_tipp_fk is null THEN 0 ELSE 1 END) tipp
      FROM identifier i
               JOIN identifier_namespace idns ON idns.idns_id = i.id_ns_fk
      GROUP BY idns.idns_ns, idns.idns_id
      order by idns.idns_ns
) sq WHERE (
    CASE WHEN sq.lic > 0 THEN 1 ELSE 0 END +
    CASE WHEN sq.org > 0 THEN 1 ELSE 0 END +
    CASE WHEN sq.pkg > 0 THEN 1 ELSE 0 END +
    CASE WHEN sq.sub > 0 THEN 1 ELSE 0 END +
    CASE WHEN sq.title > 0 THEN 1 ELSE 0 END +
    CASE WHEN sq.tipp > 0 THEN 1 ELSE 0 END
    ) > 1; """)

        List globalNamespaceStats = sqlQuery.with { list() }

        render view: 'manageNamespaces', model: [
                editable: true, // TODO check role and editable !!!
                cmd: params.cmd,
                identifierNamespaceInstance: idnsInstance,
                globalNamespaceStats: globalNamespaceStats,
                detailsStats: detailsStats,
                currentLang: I10nTranslation.decodeLocale(LocaleContextHolder.getLocale())
        ]
    }

    @Secured(['ROLE_ADMIN'])
    def managePropertyDefinitions() {

        if (params.cmd){
            PropertyDefinition pd = (PropertyDefinition) genericOIDService.resolveOID(params.pd)
            switch(params.cmd) {
                case 'toggleMandatory':
                    if(pd) {
                        pd.mandatory = !pd.mandatory
                        pd.save(flush:true)
                    }
                    break
                case 'toggleMultipleOccurrence':
                    if(pd) {
                        pd.multipleOccurrence = !pd.multipleOccurrence
                        pd.save(flush:true)
                    }
                    break
                case 'deletePropertyDefinition':
                    if (pd) {
                        if (! pd.isHardData) {
                            try {
                                pd.delete(flush:true)
                                flash.message = message(code:'propertyDefinition.delete.success',[pd.name_de])
                            }
                            catch(Exception e) {
                                flash.error = message(code:'propertyDefinition.delete.failure.default',[pd.name_de])
                            }
                        }
                    }
                    break
                case 'replacePropertyDefinition':
                    if (SpringSecurityUtils.ifAnyGranted('ROLE_YODA')) {
                        PropertyDefinition pdFrom = (PropertyDefinition) genericOIDService.resolveOID(params.xcgPdFrom)
                        PropertyDefinition pdTo = (PropertyDefinition) genericOIDService.resolveOID(params.xcgPdTo)

                        if (pdFrom && pdTo && (pdFrom.tenant?.id == pdTo.tenant?.id)) {

                            try {
                                def count = propertyService.replacePropertyDefinitions(pdFrom, pdTo)

                                flash.message = "${count} Vorkommen von ${params.xcgPdFrom} wurden durch ${params.xcgPdTo} ersetzt."
                            }
                            catch (Exception e) {
                                e.printStackTrace()
                                flash.error = "${params.xcgPdFrom} konnte nicht durch ${params.xcgPdTo} ersetzt werden."
                            }

                        }
                    } else {
                        flash.error = "Keine ausreichenden Rechte!"
                    }
                    break
            }
        }

        Map<String,Object> propDefs = [:]
        PropertyDefinition.AVAILABLE_CUSTOM_DESCR.each { String it ->
            Set<PropertyDefinition> itResult = PropertyDefinition.findAllByDescrAndTenant(it, null, [sort: 'name']) // NO private properties!
            propDefs << ["${it}": itResult]
        }

        def (usedPdList, attrMap, multiplePdList) = propertyService.getUsageDetails() // [List<Long>, Map<String, Object>, List<Long>]

        render view: 'managePropertyDefinitions', model: [
                editable    : true,
                propertyDefinitions: propDefs,
                attrMap     : attrMap,
                usedPdList  : usedPdList,
                multiplePdList : multiplePdList
        ]
    }

    @Secured(['ROLE_ADMIN'])
    def transferSubPropToSurProp() {

        PropertyDefinition propertyDefinition = PropertyDefinition.get(params.propertyDefinition)

        if(!PropertyDefinition.findByNameAndDescrAndTenant(propertyDefinition.name, PropertyDefinition.SVY_PROP, null)){
            PropertyDefinition surveyProperty = new PropertyDefinition(
                    name: propertyDefinition.name,
                    name_de: propertyDefinition.name_de,
                    name_en: propertyDefinition.name_en,
                    expl_de: propertyDefinition.expl_de,
                    expl_en: propertyDefinition.expl_en,
                    type: propertyDefinition.type,
                    refdataCategory: propertyDefinition.refdataCategory,
                    descr: PropertyDefinition.SVY_PROP
            )

            if (surveyProperty.save(flush: true)) {
                flash.message = message(code: 'propertyDefinition.copySubPropToSurProp.created.sucess')
            }
            else {
                flash.error = message(code: 'propertyDefinition.copySubPropToSurProp.created.fail')
            }
        }

        redirect(action: 'managePropertyDefinitions')
    }

    @Secured(['ROLE_ADMIN'])
    def managePropertyGroups() {
        //def result = setResultGenerics()
        Map<String, Object> result = [:]
        result.editable = true // true, because action is protected

        if (params.cmd == 'new') {
            result.formUrl = g.createLink([controller: 'admin', action: 'managePropertyGroups'])
            render template: '/templates/properties/propertyGroupModal', model: result
            return
        }
        else if (params.cmd == 'edit') {
            result.pdGroup = genericOIDService.resolveOID(params.oid)
            result.formUrl = g.createLink([controller: 'admin', action: 'managePropertyGroups'])

            render template: '/templates/properties/propertyGroupModal', model: result
            return
        }
        else if (params.cmd == 'delete') {
            def pdg = genericOIDService.resolveOID(params.oid)
            try {
                pdg.delete(flush:true)
                flash.message = "Die Gruppe ${pdg.name} wurde gelöscht."
            }
            catch (e) {
                flash.error = "Die Gruppe ${params.oid} konnte nicht gelöscht werden."
            }
        }
        else if (params.cmd == 'processing') {
            def valid
            def propDefGroup
            def ownerType = PropertyDefinition.getDescrClass(params.prop_descr)

            if (params.oid) {
                propDefGroup = genericOIDService.resolveOID(params.oid)
                propDefGroup.name = params.name ?: propDefGroup.name
                propDefGroup.description = params.description
                propDefGroup.ownerType = ownerType

                if (propDefGroup.save(flush:true)) {
                    valid = true
                }
            }
            else {
                if (params.name && ownerType) {
                    propDefGroup = new PropertyDefinitionGroup(
                            name: params.name,
                            description: params.description,
                            tenant: null,
                            ownerType: ownerType,
                            isVisible: true
                    )
                    if (propDefGroup.save(flush:true)) {
                        valid = true
                    }
                }
            }

            if (valid) {
                PropertyDefinitionGroupItem.executeUpdate(
                        "DELETE PropertyDefinitionGroupItem pdgi WHERE pdgi.propDefGroup = :pdg",
                        [pdg: propDefGroup]
                )

                params.list('propertyDefinition')?.each { pd ->

                    new PropertyDefinitionGroupItem(
                            propDef: pd,
                            propDefGroup: propDefGroup
                    ).save(flush: true)
                }
            }
        }

        result.propDefGroups = PropertyDefinitionGroup.findAllWhere(
                tenant: null
        )
        result
    }

    @Secured(['ROLE_ADMIN'])
    def manageRefdatas() {

        if (params.cmd == 'deleteRefdataValue') {
            RefdataValue rdv = (RefdataValue) genericOIDService.resolveOID(params.rdv)

            if (rdv) {
                if (! rdv.isHardData) {
                    try {
                        rdv.delete(flush:true)
                        flash.message = "${params.rdv} wurde gelöscht."
                    }
                    catch(Exception e) {
                        flash.error = "${params.rdv} konnte nicht gelöscht werden."
                    }
                }
            }
        }
        else if (params.cmd == 'replaceRefdataValue') {
            if (SpringSecurityUtils.ifAnyGranted('ROLE_YODA')) {
                RefdataValue rdvFrom = (RefdataValue) genericOIDService.resolveOID(params.xcgRdvFrom)
                RefdataValue rdvTo = (RefdataValue) genericOIDService.resolveOID(params.xcgRdvTo)

                boolean check = false

                if (! rdvFrom) {
                    check = false
                }
                else if (rdvTo && rdvTo.owner == rdvFrom.owner) {
                    check = true
                }
                else if (! rdvTo && params.xcgRdvGlobalTo) {

                    List<String> pParts = params.xcgRdvGlobalTo.split(':')
                    if (pParts.size() == 2) {
                        RefdataCategory rdvToCat = RefdataCategory.getByDesc(pParts[0].trim())
                        RefdataValue rdvToRdv = RefdataValue.getByValueAndCategory(pParts[1].trim(), pParts[0].trim())

                        if (rdvToRdv && rdvToRdv.owner == rdvToCat ) {
                            rdvTo = rdvToRdv
                            check = true
                        }
                    }
                }

                if (check) {
                    try {
                        def count = refdataService.replaceRefdataValues(rdvFrom, rdvTo)

                        flash.message = "${count} Vorkommen von ${params.xcgRdvFrom} wurden durch ${params.xcgRdvTo}${params.xcgRdvGlobalTo} ersetzt."
                    }
                    catch (Exception e) {
                        log.error( e.toString() )
                        flash.error = "${params.xcgRdvFrom} konnte nicht durch ${params.xcgRdvTo}${params.xcgRdvGlobalTo} ersetzt werden."
                    }

                }
            }
            else {
                flash.error = "Keine ausreichenden Rechte!"
            }
        }

        def (usedRdvList, attrMap) = refdataService.getUsageDetails()

        def integrityCheckResult = refdataService.integrityCheck()

        render view: 'manageRefdatas', model: [
                editable    : true,
                rdCategories: RefdataCategory.where{}.sort('desc_' + I10nTranslation.decodeLocale(LocaleContextHolder.getLocale())),
                attrMap     : attrMap,
                usedRdvList : usedRdvList,
                integrityCheckResult : integrityCheckResult
        ]
    }

    @Secured(['ROLE_ADMIN'])
    def titleEnrichment() {
        Map<String, Object> result = [:]

        if(params.kbartPreselect) {
            CommonsMultipartFile kbartFile = params.kbartPreselect
            Integer count = 0
            Integer countChanges = 0
            InputStream stream = kbartFile.getInputStream()
            ArrayList<String> rows = stream.text.split('\n')
            Map<String,Integer> colMap = [publicationTitleCol:-1,zdbCol:-1, onlineIdentifierCol:-1, printIdentifierCol:-1, doiTitleCol:-1, seriesTitleCol:-1]
            //read off first line of KBART file
            rows[0].split('\t').eachWithIndex { headerCol, int c ->
                switch(headerCol.toLowerCase().trim()) {
                    case "zdb_id": colMap.zdbCol = c
                        break
                    case "print_identifier": colMap.printIdentifierCol = c
                        break
                    case "online_identifier": colMap.onlineIdentifierCol = c
                        break
                    case "publication_title": colMap.publicationTitleCol = c
                        break
                    case "series_name": colMap.seriesNameTitleCol = c
                        break
                    case "monograph_parent_collection_title": colMap.seriesNameTitleCol = c
                        break
                    case "subject_reference": colMap.subjectReferenceTitleCol = c
                        break
                    case "summary_of_content": colMap.summaryOfContentTitleCol = c
                        break
                    case "doi_identifier": colMap.doiTitleCol = c
                        break
                }
            }
            //after having read off the header row, pop the first row
            rows.remove(0)
            //now, assemble the identifiers available to highlight
            Map<String,IdentifierNamespace> namespaces = [zdb:IdentifierNamespace.findByNs('zdb'),
                                                          eissn:IdentifierNamespace.findByNs('eissn'),isbn:IdentifierNamespace.findByNs('isbn'),
                                                          issn:IdentifierNamespace.findByNs('issn'),pisbn:IdentifierNamespace.findByNs('pisbn'),
                                                          doi: IdentifierNamespace.findByNs('doi')]
            rows.eachWithIndex { row, int i ->
                log.debug("now processing entitlement ${i}")
                ArrayList<String> cols = row.split('\t')
                Map idCandidate
                if(colMap.zdbCol >= 0 && cols[colMap.zdbCol]) {
                    idCandidate = [namespaces:[namespaces.zdb],value:cols[colMap.zdbCol]]
                }
                if(colMap.onlineIdentifierCol >= 0 && cols[colMap.onlineIdentifierCol]) {
                    idCandidate = [namespaces:[namespaces.eissn,namespaces.isbn],value:cols[colMap.onlineIdentifierCol]]
                }
                if(colMap.printIdentifierCol >= 0 && cols[colMap.printIdentifierCol]) {
                    idCandidate = [namespaces:[namespaces.issn,namespaces.pisbn],value:cols[colMap.printIdentifierCol]]
                }
                if(colMap.doiTitleCol >= 0 && cols[colMap.doiTitleCol]) {
                    idCandidate = [namespaces:[namespaces.doi],value:cols[colMap.doiTitleCol]]
                }
                if(((colMap.zdbCol >= 0 && cols[colMap.zdbCol].trim().isEmpty()) || colMap.zdbCol < 0) &&
                        ((colMap.onlineIdentifierCol >= 0 && cols[colMap.onlineIdentifierCol].trim().isEmpty()) || colMap.onlineIdentifierCol < 0) &&
                        ((colMap.printIdentifierCol >= 0 && cols[colMap.printIdentifierCol].trim().isEmpty()) || colMap.printIdentifierCol < 0)) {
                }
                else {

                    // TODO [ticket=1789]
                    //def tiObj = TitleInstance.executeQuery('select ti from TitleInstance ti join ti.ids ids where ids in (select io from IdentifierOccurrence io join io.identifier id where id.ns in :namespaces and id.value = :value)',[namespaces:idCandidate.namespaces,value:idCandidate.value])
                    def tiObj = TitleInstance.executeQuery('select ti from TitleInstance ti join ti.ids ident where ident.ns in :namespaces and ident.value = :value', [namespaces:idCandidate.namespaces, value:idCandidate.value])
                    if(tiObj) {

                        tiObj.each { titleInstance ->
                            count++
                            if(titleInstance instanceof BookInstance) {
                                if(colMap.summaryOfContentTitleCol && (cols[colMap.summaryOfContentTitleCol] != null || cols[colMap.summaryOfContentTitleCol] != "") && (cols[colMap.summaryOfContentTitleCol].trim() != titleInstance.summaryOfContent) ){
                                    countChanges++
                                    titleInstance.summaryOfContent = cols[colMap.summaryOfContentTitleCol].trim()
                                    titleInstance.save(flush: true)
                                }
                            }

                                if(colMap.seriesNameTitleCol && (cols[colMap.seriesNameTitleCol] != null || cols[colMap.seriesNameTitleCol] != "") && (cols[colMap.seriesNameTitleCol].trim() != titleInstance.seriesName) ){
                                    countChanges++
                                    titleInstance.seriesName = cols[colMap.seriesNameTitleCol].trim()
                                    titleInstance.save(flush: true)
                                }

                                if(colMap.subjectReferenceTitleCol && (cols[colMap.subjectReferenceTitleCol] != null || cols[colMap.subjectReferenceTitleCol] != "") && (cols[colMap.subjectReferenceTitleCol].trim() != titleInstance.subjectReference) ){
                                    countChanges++
                                    titleInstance.subjectReference = cols[colMap.subjectReferenceTitleCol].trim()
                                    titleInstance.save(flush: true)
                                }
                        }
                    }
                }
            }

            flash.message = "Verbearbeitet: ${count} /Geändert ${countChanges}"
            //println(count)
            //println(countChanges)
            params.remove("kbartPreselct")
        }

    }

    @Secured(['ROLE_ADMIN'])
    def systemMessages() {

        Map<String, Object> result = [:]
        result.user = springSecurityService.currentUser

        if (params.create){
            SystemMessage sm = new SystemMessage(
                    content_de: params.content_de ?: '',
                    content_en: params.content_en ?: '',
                    type: params.type,
                    isActive: false)

            if (sm.save(flush: true)){
                flash.message = 'Systemmeldung erstellt'
            } else {
                flash.error = 'Systemmeldung wurde nicht erstellt'
            }
        }

        result.systemMessages = SystemMessage.executeQuery('select sm from SystemMessage sm order by sm.isActive desc, sm.lastUpdated desc')
        result.editable = true
        result
    }

    @Secured(['ROLE_ADMIN'])
    def deleteSystemMessage(Long id) {

        if (SystemMessage.get(id)){
            SystemMessage.get(id).delete(flush: true)
            flash.message = 'Systemmeldung wurde gelöscht'
        }

        redirect(action: 'systemMessages')
    }
}
