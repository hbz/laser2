package de.laser.system


import com.k_int.kbplus.auth.User
import de.laser.RefdataValue
import de.laser.UserSetting
import de.laser.helper.ConfigUtils
import de.laser.helper.RDStore
import de.laser.helper.ServerUtils
import grails.util.Holders
import net.sf.json.JSON

class SystemAnnouncement {

    def grailsApplication
    def mailService
    def escapeService

    User    user
    String  title
    String  content
    String  status

    boolean isPublished = false

    Date    lastPublishingDate
    Date    dateCreated
    Date    lastUpdated

    static transients = ['cleanTitle', 'cleanContent'] // mark read-only accessor methods

    static mapping = {
        id              column: 'sa_id'
        version         column: 'sa_version'
        user            column: 'sa_user_fk'
        title           column: 'sa_title'
        content         column: 'sa_content', type: 'text'
        status          column: 'sa_status', type: 'text'
        isPublished     column: 'sa_is_published'
        lastPublishingDate column: 'sa_last_publishing_date'
        dateCreated     column: 'sa_date_created'
        lastUpdated     column: 'sa_last_updated'
    }

    static constraints = {
        title       (blank:false)
        content     (blank:false)
        status      (nullable:true, blank:false)
        lastPublishingDate (nullable:true)
        dateCreated (nullable:true)
        lastUpdated (nullable:true)
    }

    static List<SystemAnnouncement> getPublished(int periodInDays) {
        Date dcCheck = (new Date()).minus(periodInDays)

        SystemAnnouncement.executeQuery(
                'select sa from SystemAnnouncement sa ' +
                'where sa.isPublished = true and sa.lastPublishingDate >= :dcCheck order by sa.lastPublishingDate desc',
                [dcCheck: dcCheck]
        )
    }

    static List<User> getRecipients() {
        User.executeQuery(
                'select u from UserSetting uss join uss.user u where uss.key = :ussKey and uss.rdValue = :ussValue order by u.id',
                [ussKey: UserSetting.KEYS.IS_NOTIFICATION_FOR_SYSTEM_MESSAGES, ussValue: RDStore.YN_YES]
        )
    }

    static String cleanUp(String s) {
        s.replaceAll("\\<.*?>","")
    }
    String getCleanTitle() {
        SystemAnnouncement.cleanUp(escapeService.replaceUmlaute(title))
    }

    String getCleanContent() {
        SystemAnnouncement.cleanUp(escapeService.replaceUmlaute(content))
    }

    boolean publish() {
        if (grailsApplication.config.grails.mail.disabled == true) {
            println 'SystemAnnouncement.publish() failed due grailsApplication.config.grails.mail.disabled = true'
            return false
        }

        withTransaction {

            List<User> reps = SystemAnnouncement.getRecipients()
            List validUserIds = []
            List failedUserIds = []

            lastPublishingDate = new Date()
            isPublished = true
            save()

            reps.each { u ->
                try {
                    sendMail(u)
                    validUserIds << u.id
                }
                catch (Exception e) {
                    log.error(e.getMessage())
                    log.error(e.getStackTrace())
                    failedUserIds << u.id
                }
            }

            status = ([
                    validUserIds : validUserIds,
                    failedUserIds: failedUserIds
            ] as JSON).toString()

            save()

            if (validUserIds.size() > 0) {
                SystemEvent.createEvent('SYSANN_SENDING_OK', ['count': validUserIds.size()])
            }

            if (failedUserIds.size() > 0) {
                SystemEvent.createEvent('SYSANN_SENDING_ERROR', ['users': failedUserIds, 'count': failedUserIds.size()])
            }

            return failedUserIds.isEmpty()
        }
    }

    private void sendMail(User user) throws Exception {

        def messageSource = Holders.grailsApplication.mainContext.getBean('messageSource')
        Locale language = new Locale(user.getSetting(UserSetting.KEYS.LANGUAGE_OF_EMAILS, RefdataValue.getByValueAndCategory('de', de.laser.helper.RDConstants.LANGUAGE)).value.toString())

        String currentServer = ServerUtils.getCurrentServer()
        String subjectSystemPraefix = (currentServer == ServerUtils.SERVER_PROD) ? "LAS:eR - " : (ConfigUtils.getLaserSystemId() + " - ")
        String mailSubject = subjectSystemPraefix + messageSource.getMessage('email.subject.sysAnnouncement', null, language)

        boolean isRemindCCbyEmail = user.getSetting(UserSetting.KEYS.IS_REMIND_CC_BY_EMAIL, RDStore.YN_NO)?.rdValue == RDStore.YN_YES
        String ccAddress

        if (isRemindCCbyEmail){
            ccAddress = user.getSetting(UserSetting.KEYS.REMIND_CC_EMAILADDRESS, null)?.getValue()
            // println user.toString() + " : " + isRemindCCbyEmail + " : " + ccAddress
        }

        if (isRemindCCbyEmail && ccAddress) {
            mailService.sendMail {
                to      user.getEmail()
                from    ConfigUtils.getNotificationsEmailFrom()
                cc      ccAddress
                replyTo ConfigUtils.getNotificationsEmailReplyTo()
                subject mailSubject
                body    (view: "/mailTemplates/text/systemAnnouncement", model: [user: user, announcement: this])
            }
        }
        else {
            mailService.sendMail {
                to      user.getEmail()
                from    ConfigUtils.getNotificationsEmailFrom()
                replyTo ConfigUtils.getNotificationsEmailReplyTo()
                subject mailSubject
                body    (view: "/mailTemplates/text/systemAnnouncement", model: [user: user, announcement: this])
            }
        }
    }
}
