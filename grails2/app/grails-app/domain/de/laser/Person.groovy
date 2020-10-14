package de.laser


import de.laser.properties.PersonProperty
import de.laser.titles.TitleInstance
import de.laser.base.AbstractBaseWithCalculatedLastUpdated
import de.laser.helper.RDConstants
import de.laser.helper.RDStore
import de.laser.helper.RefdataAnnotation
import groovy.util.logging.Log4j
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

@Log4j
class Person extends AbstractBaseWithCalculatedLastUpdated {

    static Log static_logger = LogFactory.getLog(Person)

    String       title
    String       first_name
    String       middle_name
    String       last_name
    Org          tenant

    @RefdataAnnotation(cat = RDConstants.GENDER)
    RefdataValue gender

    boolean isPublic = false

    @RefdataAnnotation(cat = RDConstants.PERSON_CONTACT_TYPE)
    RefdataValue contactType

    @Deprecated
    @RefdataAnnotation(cat = RDConstants.PERSON_POSITION)
    RefdataValue roleType // TODO remove !?

    Date dateCreated
    Date lastUpdated
    Date lastUpdatedCascading

    static mapping = {
        cache  true
        id              column:'prs_id'
        globalUID       column:'prs_guid'
        version         column:'prs_version'
        title           column:'prs_title'
        first_name      column:'prs_first_name'
        middle_name     column:'prs_middle_name'
        last_name       column:'prs_last_name'
        gender          column:'prs_gender_rv_fk'
        tenant          column:'prs_tenant_fk'
        isPublic        column:'prs_is_public'
        contactType     column:'prs_contact_type_rv_fk'
        roleType        column:'prs_role_type_rv_fk'

        roleLinks           cascade: 'all', batchSize: 10
        addresses           cascade: 'all', lazy: false
        contacts            cascade: 'all', lazy: false
        propertySet   cascade: 'all', batchSize: 10

        dateCreated column: 'prs_date_created'
        lastUpdated column: 'prs_last_updated'
        lastUpdatedCascading column: 'prs_last_updated_cascading'
    }
    
    static mappedBy = [
        roleLinks:          'prs',
        addresses:          'prs',
        contacts:           'prs',
        propertySet:        'owner'
    ]
  
    static hasMany = [
            roleLinks: PersonRole,
            addresses: Address,
            contacts:  Contact,
            propertySet: PersonProperty
    ]
    
    static constraints = {
        globalUID   (nullable:true,  blank:false, unique:true, maxSize:255)
        title       (nullable:true,  blank:false)
        first_name  (nullable:true,  blank:false)
        middle_name (nullable:true,  blank:false)
        last_name   (blank:false)
        gender      (nullable:true)
        tenant      (nullable:true)
        contactType (nullable:true)
        roleType    (nullable:true)

        // Nullable is true, because values are already in the database
        lastUpdated (nullable: true)
        dateCreated (nullable: true)
        lastUpdatedCascading (nullable: true)
    }
    
    static List<RefdataValue> getAllRefdataValues(String category) {
        RefdataCategory.getAllRefdataValues(category)
    }
    
    @Override
    String toString() {
        ((title ?: '') + ' ' + (last_name ?: ' ') + (first_name ? ', ' + first_name : '') + ' ' + (middle_name ?: '')).trim()
    }

    static Person lookup(String firstName, String lastName, Org tenant, boolean isPublic, RefdataValue contactType, Org org, RefdataValue functionType) {

        Person person
        List<Person> prsList = []

        Person.findAllWhere(
                first_name: firstName,
                last_name: lastName,
                contactType: contactType,
                isPublic: isPublic,
                tenant: tenant
        ).each{ Person p ->
            if (PersonRole.findWhere(prs: p, functionType: functionType, org: org)) {
                prsList << p
            }
        }
        if ( prsList.size() > 0 ) {
            person = prsList.get(0)
        }
        person
    }

    static List<Person> getPublicByOrgAndFunc(Org org, String func) {
        Person.executeQuery(
                "select p from Person as p inner join p.roleLinks pr where p.isPublic = true and pr.org = :org and pr.functionType.value = :functionType",
                [org: org, functionType: func]
        )
    }

    static Map getPublicAndPrivateEmailByFunc(String func,Org contextOrg) {
        List allPersons = executeQuery('select p,pr from Person as p join p.roleLinks pr join p.contacts c where pr.functionType.value = :functionType',[functionType: func])
        Map publicContactMap = [:], privateContactMap = [:]
        allPersons.each { Person row ->
            Person p = (Person) row[0]
            PersonRole pr = (PersonRole) row[1]
            if(p.isPublic) {
                p.contacts.each { Contact c ->
                    if(c.contentType == RDStore.CCT_EMAIL) {
                        if(publicContactMap[pr.org])
                            publicContactMap[pr.org].add(c.content)
                        else {
                            publicContactMap[pr.org] = new HashSet()
                            publicContactMap[pr.org].add(c.content)
                        }
                    }
                }
            }
            else {
                p.contacts.each { Contact c ->
                    if(c.contentType == RDStore.CCT_EMAIL && p.tenant == contextOrg) {
                        if(privateContactMap[pr.org])
                            privateContactMap[pr.org].add(c.content)
                        else {
                            privateContactMap[pr.org] = new HashSet()
                            privateContactMap[pr.org].add(c.content)
                        }
                    }
                }
            }
        }
        [publicContacts: publicContactMap, privateContacts: privateContactMap]
    }

    // if org is null, get ALL public responsibilities
    static List<Person> getPublicByOrgAndObjectResp(Org org, def obj, String resp) {
        String q = ''
        def p = org ? ['org': org, 'resp': resp] : ['resp': resp]

        if (obj instanceof License) {
            q = ' and pr.lic = :obj '
            p << ['obj': obj]
        }
        if (obj instanceof Package) {
            q = ' and pr.pkg = :obj '
            p << ['obj': obj]
        }
        if (obj instanceof Subscription) {
            q = ' and pr.sub = :obj '
            p << ['obj': obj]
        }
        if (obj instanceof TitleInstance) {
            q = ' and pr.title = :obj '
            p << ['obj': obj]
        }

        List<Person> result = Person.executeQuery(
                "select p from Person as p inner join p.roleLinks pr where p.isPublic = true " +
                        (org ? "and pr.org = :org " : "" ) +
                        "and pr.responsibilityType.value = :resp " + q,
                p
        )
        result
    }

    static List<Person> getPrivateByOrgAndFuncFromAddressbook(Org org, String func, Org tenant) {
        List<Person> result = Person.executeQuery(
                "select p from Person as p inner join p.roleLinks pr where p.isPublic = false and pr.org = :org and pr.functionType.value = :functionType and p.tenant = :tenant",
                [org: org, functionType: func, tenant: tenant]
        )
        result
    }

    static List<Person> getPrivateByOrgAndObjectRespFromAddressbook(Org org, def obj, String resp, Org tenant) {
        String q = ''
        def p = ['org': org, 'resp': resp, 'tnt': tenant]

        if (obj instanceof License) {
            q = ' and pr.lic = :obj '
            p << ['obj': obj]
        }
        if (obj instanceof Package) {
            q = ' and pr.pkg = :obj '
            p << ['obj': obj]
        }
        if (obj instanceof Subscription) {
            q = ' and pr.sub = :obj '
            p << ['obj': obj]
        }
        if (obj instanceof TitleInstance) {
            q = ' and pr.title = :obj '
            p << ['obj': obj]
        }

        List<Person> result = Person.executeQuery(
                "select p from Person as p inner join p.roleLinks pr where p.isPublic = false and pr.org = :org and pr.responsibilityType.value = :resp and p.tenant = :tnt " + q,
                p
        )
        result
    }

    LinkedHashSet<PersonRole> getPersonRoleByOrg(Org org) {

        return roleLinks.findAll {it.org?.id == org.id}
    }

    @Override
    def afterDelete() {
        super.afterDeleteHandler()
    }
    @Override
    def afterInsert() {
        super.afterInsertHandler()
    }
    @Override
    def afterUpdate() {
        super.afterUpdateHandler()
    }
    @Override
    def beforeInsert() {
        super.beforeInsertHandler()
    }
    @Override
    def beforeUpdate() {
        super.beforeUpdateHandler()
    }
    @Override
    def beforeDelete() {
        super.beforeDeleteHandler()
    }

    Org getBelongsToOrg() {

        List<Org> orgs = PersonRole.executeQuery(
                "select distinct(pr.org) from PersonRole as pr where pr.prs = :person ", [person: this]
        )

        if(orgs.size() > 0)
        {

            return orgs[0]
        }else {
            return null
        }

    }
}
