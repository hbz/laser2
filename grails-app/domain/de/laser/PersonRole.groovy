package de.laser


import de.laser.titles.TitleInstance
import de.laser.helper.RDConstants
import de.laser.helper.RefdataAnnotation


class PersonRole implements Comparable<PersonRole>{
    private static final String REFDATA_GENERAL_CONTACT_PRS = "General contact person"

    static TYPE_FUNCTION        = 'functionType'
    static TYPE_POSITION        = 'positionType'
    static TYPE_RESPONSIBILITY  = 'responsibilityType'

    @RefdataAnnotation(cat = RDConstants.PERSON_POSITION)
    RefdataValue    positionType  //  exclusive with other types

    @RefdataAnnotation(cat = RDConstants.PERSON_FUNCTION)
    RefdataValue    functionType   // exclusive with other types

    @RefdataAnnotation(cat = RDConstants.PERSON_RESPONSIBILITY)
    RefdataValue    responsibilityType  // exclusive other types

    License         lic
    Package         pkg
    Subscription    sub
    TitleInstance   title
    Date            start_date 
    Date            end_date

    Date dateCreated
    Date lastUpdated
    
    static belongsTo = [
        prs:        Person,
        org:        Org
    ]

    static transients = ['reference'] // mark read-only accessor methods
    
    static mapping = {
        id          column:'pr_id'
        version     column:'pr_version'
        positionType            column:'pr_position_type_rv_fk'
        functionType            column:'pr_function_type_rv_fk'
        responsibilityType      column:'pr_responsibility_type_rv_fk'
        prs         column:'pr_prs_fk',     index: 'pr_prs_org_idx'
        lic         column:'pr_lic_fk'
        org         column:'pr_org_fk',     index: 'pr_prs_org_idx'
        pkg         column:'pr_pkg_fk'
        sub         column:'pr_sub_fk'
        title       column:'pr_title_fk'
        start_date  column:'pr_startdate'
        end_date    column:'pr_enddate'
        
        dateCreated column: 'pr_date_created'
        lastUpdated column: 'pr_last_updated'
    }
    
    static constraints = {
        positionType        (nullable:true)
        functionType        (nullable:true)
        responsibilityType  (nullable:true)
        lic         (nullable:true)
        org         (nullable:true)
        pkg         (nullable:true)
        sub         (nullable:true)
        title       (nullable:true)
        start_date  (nullable:true)
        end_date    (nullable:true)

        // Nullable is true, because values are already in the database
        lastUpdated (nullable: true)
        dateCreated (nullable: true)
    }

    /**
     * Generic setter
     */
    void setReference(def owner) {
        org     = owner instanceof Org ? owner : org
        lic     = owner instanceof License ? owner : lic
        pkg     = owner instanceof Package ? owner : pkg
        sub     = owner instanceof Subscription ? owner : sub
        title   = owner instanceof TitleInstance ? owner : title
    }

    String getReference() {
        if (lic)        return 'lic:' + lic.id
        if (pkg)        return 'pkg:' + pkg.id
        if (sub)        return 'sub:' + sub.id
        if (title)      return 'title:' + title.id
    }

    static List<RefdataValue> getAllRefdataValues(String category) {
        RefdataCategory.getAllRefdataValues(category)//.sort {it.getI10n("value")}
    }

    static PersonRole getByPersonAndOrgAndRespValue(Person prs, Org org, def resp) {

        List<PersonRole> result = PersonRole.findAllWhere(
            prs: prs,
            org: org,
            responsibilityType: RefdataValue.getByValueAndCategory(resp, RDConstants.PERSON_RESPONSIBILITY)
        )

        result.first()
    }

    @Override
    int compareTo(PersonRole that) {
        String this_FunctionType = this?.functionType?.value
        String that_FunctionType = that?.functionType?.value
        int result

        if  (REFDATA_GENERAL_CONTACT_PRS == this_FunctionType){
            if (REFDATA_GENERAL_CONTACT_PRS == that_FunctionType) {
                String this_Name = (this?.prs?.last_name + this?.prs?.first_name)?:""
                String that_Name = (that?.prs?.last_name + that?.prs?.first_name)?:""
                result = (this_Name)?.compareTo(that_Name)
            } else {
                result = -1
            }
        } else {
            if (REFDATA_GENERAL_CONTACT_PRS == that_FunctionType) {
                result = 1
            } else {
                String this_fkType = (this?.functionType?.getI10n('value'))?:""
                String that_fkType = (that?.functionType?.getI10n('value'))?:""
                result = this_fkType?.compareTo(that_fkType)
            }
        }
        result
    }

}
