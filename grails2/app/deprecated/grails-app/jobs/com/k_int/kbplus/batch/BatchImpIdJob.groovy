package com.k_int.kbplus.batch

import de.laser.SystemEvent
import de.laser.quartz.AbstractJob
import org.hibernate.ScrollMode

/*
* This job is only run once on system startup, and is responsible for generating sort names on Package
*/
@Deprecated
class BatchImpIdJob extends AbstractJob {

    /* DISABLED
    static triggers = {
        simple name:'BatchImpIdJob', startDelay:40000, repeatInterval:30000, repeatCount:0
    }
    */

    static List<String> configFlags = []

    boolean isAvailable() {
        !jobIsRunning // no service needed
    }
    boolean isRunning() {
        jobIsRunning
    }

    def execute() {
        if (! isAvailable()) {
            return false
        }

        jobIsRunning = true
        SystemEvent.createEvent('BATCH_IMP_JOB_START')

        log.debug("BatchImpIdJob::execute()")


    String event = "BatchImpIdJob"
    Date startTime = printStart(event)
    int counter = 0
    def classList = [com.k_int.kbplus.Package,com.k_int.kbplus.Org,com.k_int.kbplus.License,com.k_int.kbplus.Subscription,com.k_int.kbplus.Platform,com.k_int.kbplus.TitleInstance] 
    classList.each{ currentClass ->
      def auditable_store = null
      try{
        if(currentClass.hasProperty('auditable'))  {
          auditable_store = currentClass.auditable
          currentClass.auditable = false ;
        }
        currentClass.withSession { session ->
           def scroll_res = session.createCriteria(currentClass).scroll(ScrollMode.FORWARD_ONLY)
           while (scroll_res.next()) {
              def obj = scroll_res.get(0)
              if(updateObject(obj)){
                counter ++
              }
              if(counter == 500){
                cleanUpGorm(session)
                counter = 0
              }
           }
           cleanUpGorm(session)
        }

      }
      catch( Exception e ) {
          log.error(e)
      }
      finally {
        if(currentClass.hasProperty('auditable')) {
            currentClass.auditable = auditable_store ?: true
        }
      }

      }
      printDuration(startTime,event)
      jobIsRunning = false
  }

    private def cleanUpGorm(session) {
    session.flush()
    session.clear()
  }

    private def updateObject(obj){
    if(obj.impId) return null;
    obj.impId = java.util.UUID.randomUUID().toString();
    obj.save()
  }

    private Date printStart(String event){
        Date starttime = new Date()
       log.debug("******* Start ${event}: ${starttime} *******")
       return starttime
   }

    private void printDuration(Date starttime, String event){
      use(groovy.time.TimeCategory) {
      def duration = new Date() - starttime
      log.debug("******* End ${event}: ${new Date()} *******")
      log.debug("Duration: ${(duration.hours*60)+duration.minutes}m ${duration.seconds}s")
    }
  }
}
