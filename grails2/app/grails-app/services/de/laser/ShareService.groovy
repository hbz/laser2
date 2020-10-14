package de.laser


import de.laser.interfaces.ShareSupport
import de.laser.titles.TitleInstance
import grails.transaction.Transactional

//@CompileStatic
@Transactional
class ShareService {

    boolean addDocShareForTarget(DocContext share, ShareSupport target) {

        if (share.sharedFrom) {
            return false
        }
        // todo check existence

        DocContext clonedShare = new DocContext(
                owner:          share.owner ,
                license:        (target instanceof License) ? target : share.license,
                subscription:   (target instanceof Subscription) ? target : share.subscription,
                pkg:            (target instanceof Package) ? target : share.pkg,
                link:           (target instanceof Links) ? target : share.link,
                doctype:        share.doctype,
                domain:         share.domain,
                globannounce:   share.globannounce,
                sharedFrom:     share,
                isShared:       false
        )
        if (clonedShare.save(flush: true)) {
            if(target instanceof Subscription) {
                //damn that three-tier inheritance level ... check if there are departments for a consortial subscription!!!! Show David!!!
                List<Subscription> descendants = Subscription.findAllByInstanceOf(target)
                descendants.each { Subscription d ->
                    DocContext clonedDescendantShare = new DocContext(
                            owner:          share.owner ,
                            subscription:   d,
                            doctype:        share.doctype,
                            domain:         share.domain,
                            globannounce:   share.globannounce,
                            sharedFrom:     share,
                            isShared:       false
                    )
                    clonedDescendantShare.save(flush: true)
                }
            }
            if (! share.isShared) {
                share.isShared = true
                if (share.save(flush: true)) {
                    return true
                }
            }
        }
        return false
    }

    boolean deleteDocShareForTarget(DocContext share, ShareSupport target) {

        String tp =
                (target instanceof License) ? 'license' :
                        (target instanceof Subscription) ? 'subscription' :
                                (target instanceof Package) ? 'pkg' :
                                        (target instanceof Links) ? 'link' :
                                                null

        if (tp) {
            DocContext.executeUpdate('delete from DocContext dc where dc.sharedFrom = :sf and ' + tp + ' = :target', [sf: this, target: target])
            return true
        }
        return false
    }

    boolean addOrgRoleShareForTarget(OrgRole share, ShareSupport target) {

        if (share.sharedFrom) {
            return false
        }
        // todo check existence

        OrgRole clonedShare = new OrgRole(
                org:            share.org ,
                lic:            (target instanceof License) ? target : share.lic,
                sub:            (target instanceof Subscription) ? target : share.sub,
                pkg:            (target instanceof Package) ? target : share.pkg,
                title:          (target instanceof TitleInstance) ? target : share.title,
                roleType:       share.roleType,
                startDate:      share.startDate,
                endDate:        share.endDate,
                sharedFrom:     share,
                isShared:       false
        )

        if (clonedShare.save(flush: true)) {
            if (! share.isShared) {
                share.isShared = true
                if (share.save(flush: true)) {
                    return true
                }
            }
        }
        return false
    }

    boolean deleteOrgRoleShareForTarget(OrgRole share, ShareSupport target) {

        String tp =
                (target instanceof License) ? 'lic' :
                        (target instanceof Subscription) ? 'sub' :
                                (target instanceof Package) ? 'pkg' :
                                        (target instanceof TitleInstance) ? 'title' :
                                                null

        if (tp) {
            OrgRole.executeUpdate('delete from OrgRole oorr where oorr.sharedFrom = :sf and ' + tp + ' = :target', [sf: this, target: target])
            return true
        }
        return false
    }
}