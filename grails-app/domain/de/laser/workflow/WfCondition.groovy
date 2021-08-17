package de.laser.workflow

class WfCondition extends WfConditionBase {

    static final String KEY = 'WF_CONDITION'

    WfConditionPrototype prototype

    static mapping = {
                     id column: 'wfc_id'
                version column: 'wfc_version'
              prototype column: 'wfc_prototype_fk'
                  type  column: 'wfc_type'
                  title column: 'wfc_title'
            description column: 'wfc_description', type: 'text'

              checkbox1 column: 'wfc_checkbox1'
        checkbox1_title column: 'wfc_checkbox1_title'
    checkbox1_isTrigger column: 'wfc_checkbox1_is_trigger'
              checkbox2 column: 'wfc_checkbox2'
        checkbox2_title column: 'wfc_checkbox2_title'
    checkbox2_isTrigger column: 'wfc_checkbox2_is_trigger'
                  date1 column: 'wfc_date1'
            date1_title column: 'wfc_date1_title'
                  date2 column: 'wfc_date2'
            date2_title column: 'wfc_date2_title'
                  file1 column: 'wfc_file1'
            file1_title column: 'wfc_file1_title'

            dateCreated column: 'wfc_date_created'
            lastUpdated column: 'wfc_last_updated'
    }

    static constraints = {
        title           (blank: false)
        description     (nullable: true)
        checkbox1_title (nullable: true)
        checkbox2_title (nullable: true)
        date1           (nullable: true)
        date1_title     (nullable: true)
        date2           (nullable: true)
        date2_title     (nullable: true)
        file1           (nullable: true)
        file1_title     (nullable: true)
    }

    void remove() throws Exception {
        this.delete()
    }

    WfTask getTask() {
        WfTask.findByCondition( this )
    }
}