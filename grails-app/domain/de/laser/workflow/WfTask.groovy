package de.laser.workflow

import de.laser.RefdataValue
import de.laser.annotations.RefdataAnnotation
import de.laser.helper.RDConstants

class WfTask extends WfTaskPrototype {

    @RefdataAnnotation(cat = RDConstants.WORKFLOW_TASK_STATUS)
    RefdataValue status

    WfTaskPrototype prototype
    WfTask parent

    String comment

    static mapping = {
                 id column: 'wft_id'
            version column: 'wft_version'
           priority column: 'wft_priority_rv_fk'
             status column: 'wft_status_rv_fk'
               type column: 'wft_type_rv_fk'
          prototype column: 'wft_prototype_fk'
              title column: 'wft_title'
        description column: 'wft_description', type: 'text'
            comment column: 'wft_comment', type: 'text'
             parent column:' wft_parent_fk'

        dateCreated column: 'wft_date_created'
        lastUpdated column: 'wft_last_updated'
    }

    static constraints = {
        description (nullable: true, blank: false)
        comment     (nullable: true, blank: false)
        parent      (nullable: true)
    }
}
