package de.laser


import grails.transaction.Transactional

@Transactional
class IdentifierService {

    void checkNullUIDs() {
        List<Person> persons = Person.findAllByGlobalUIDIsNull()
        persons.each { person ->
            log.debug("Da identificator pro persona ${person.id}")
            person.setGlobalUID()
            person.save(flush: true)
        }

        List<Org> orgs = Org.findAllByGlobalUIDIsNull()
        orgs.each { org ->
            log.debug("Da identificator pro societati ${org.id}")
            org.setGlobalUID()
            org.save(flush:true)
        }

        List<Subscription> subs = Subscription.findAllByGlobalUIDIsNull()
        subs.each { sub ->
            log.debug("Da identificator pro subscriptioni ${sub.id}")
            sub.setGlobalUID()
            sub.save(flush:true)
        }

        List<License> licenses = License.findAllByGlobalUIDIsNull()
        licenses.each { lic ->
            log.debug("Da identificator pro contracto ${lic.id}")
            lic.setGlobalUID()
            lic.save(flush:true)
        }

        List<Package> packages = Package.findAllByGlobalUIDIsNull()
        packages.each { pkg ->
            log.debug("Da identificator pro ballo ${pkg.id}")
            pkg.setGlobalUID()
            pkg.save(flush:true)
        }
    }
}
