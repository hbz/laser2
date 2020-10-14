package com.k_int.kbplus

import de.laser.domain.TIPPCoverage
import de.laser.helper.RDStore
import grails.util.Holders

@Deprecated
public class GokbDiffEngine {

    def static diff(ctx, oldpkg, newpkg, newTippClosure, updatedTippClosure, deletedTippClosure, pkgPropChangeClosure, tippUnchangedClosure, auto_accept) {

        if ((oldpkg == null) || (newpkg == null)) {
            println("Error - null package passed to diff");
            return
        }

        if (oldpkg.packageName != newpkg.packageName && oldpkg.packageName != null) {
            // println("packageName updated from ${oldpkg.packageName} to ${newpkg.packageName}");
            pkgPropChangeClosure(ctx, 'title', newpkg.packageName, true);
        } else {
            // println("packageName consistent");
        }

        if (oldpkg.packageId != newpkg.packageId) {
            // println("packageId updated from ${oldpkg.packageId} to ${newpkg.packageId}");
        } else {
            // println("packageId consistent");
        }

        //def primaryUrl = (oldpkg?.nominalPlatformPrimaryUrl == newpkg?.nominalPlatformPrimaryUrl) ? oldpkg?.nominalPlatformPrimaryUrl : newpkg?.nominalPlatformPrimaryUrl

        compareLocalPkgWithGokbPkg(ctx, oldpkg, newpkg, newTippClosure, updatedTippClosure, tippUnchangedClosure,deletedTippClosure, auto_accept)

        /*//TODO: Umstellung auf UUID vielleicht
        oldpkg.tipps.sort { it.tippUuid }
        newpkg.tipps.sort { it.tippUuid }

        def ai = oldpkg.tipps.iterator();
        def bi = newpkg.tipps.iterator();


        def tippa = ai.hasNext() ? ai.next() : null
        def tippb = bi.hasNext() ? bi.next() : null

        while (tippa != null || tippb != null) {

            replaceImpIDwithGokbID(ctx, tippb, primaryUrl)

            if (tippa != null && tippb != null &&
                    (tippa.tippId == tippb.tippId ||
                            (tippa.tippUuid && tippb.tippUuid && tippa.tippUuid == tippb.tippUuid)
                    )
            ) {

                def tipp_diff = getTippDiff(tippa, tippb)

                if (tippb.status != 'Current' && tipp_diff.size() == 0) {
                    deletedTippClosure(ctx, tippa, auto_accept)
                    System.out.println("Title " + tippa + " Was removed from the package");

                    tippa = ai.hasNext() ? ai.next() : null;
                    tippb = bi.hasNext() ? bi.next() : null
                } else if (tipp_diff.size() == 0) {
                    tippUnchangedClosure(ctx, tippa);

                    tippa = ai.hasNext() ? ai.next() : null
                    tippb = bi.hasNext() ? bi.next() : null
                } else {
                    // See if any of the actual properties are null
                    println("Got tipp diffs: ${tipp_diff}")
                    try {
                        updatedTippClosure(ctx, tippb, tippa, tipp_diff, auto_accept)
                    }
                    catch (Exception e) {
                        System.err.println("Error on executing updated TIPP closure! Please verify logs:")
                        e.printStackTrace()
                    }

                    tippa = ai.hasNext() ? ai.next() : null
                    tippb = bi.hasNext() ? bi.next() : null
                }
            } else if ((tippb != null) && (tippa == null)) {
                System.out.println("TIPP " + tippb + " Was added to the package");
                newTippClosure(ctx, tippb, auto_accept)
                tippb = bi.hasNext() ? bi.next() : null;
                tippa = ai.hasNext() ? ai.next() : null;
            } else {
                deletedTippClosure(ctx, tippa, auto_accept)
                System.out.println("TIPP " + tippa + " Was removed from the package");
                tippa = ai.hasNext() ? ai.next() : null;
                tippb = bi.hasNext() ? bi.next() : null;
            }
        }*/

    }

    def static getTippDiff(tippa, tippb) {
        println "processing tipp diffs between ${tippa} and ${tippb}"
        def result = []

        if ((tippa.url ?: '').toString().compareTo((tippb.url ?: '').toString()) != 0) {
            result.add([field: 'hostPlatformURL', newValue: tippb.url, oldValue: tippa.url])
        }

        /* This is the boss enemy when refactoring coverage statements ...
        Expect ERMS-1607
        if ((tippa.coverage ?: '').toString().compareTo((tippb.coverage ?: '').toString()) != 0) {
            result.add([field: 'coverage', newValue: tippb.coverage, oldValue: tippa.coverage])
        }*/
        Map coverageDiffs = getCoverageDiffs(tippa.coverage,tippb.coverage)
        if(coverageDiffs)
            result.add(coverageDiffs)

        if ((tippa.accessStart ?: '').toString().compareTo((tippb.accessStart ?: '').toString()) != 0) {
            result.add([field: 'accessStart', newValue: tippb.accessStart, oldValue: tippa.accessStart])
        }

        if ((tippa.accessEnd ?: '').toString().compareTo((tippb.accessEnd ?: '').toString()) != 0) {
            result.add([field: 'accessEnd', newValue: tippb.accessEnd, oldValue: tippa.accessEnd])
        }

        if ((tippa?.title?.name ?: '').toString().compareTo((tippb?.title?.name ?: '').toString()) != 0) {
            result.add([field: 'titleName', newValue: tippb?.title?.name, oldValue: tippa?.title?.name])
        }

        if ((tippa?.platformUuid ?: '').toString().compareTo((tippb?.platformUuid ?: '').toString()) != 0) {
            result.add([field: 'platform', newValue: "${tippb?.platformUuid}", oldValue: "${tippa?.platformUuid}"])
        }

        result;
    }

    def static replaceImpIDwithGokbID(pkg, newTipp, primaryUrl) {

        //Replace ImpID with GokbID
        if (newTipp) {
            def db_tipp = null

            if (newTipp?.tippUuid) {
                db_tipp = pkg.tipps.find { it.gokbId == newTipp?.tippUuid }
            }
            if (!db_tipp) {
                db_tipp = pkg.tipps.find { it.impId == newTipp?.tippUuid }
            }
            if (db_tipp) {
                if (Holders.config.globalDataSync.replaceLocalImpIds.TIPP && newTipp.tippUuid && db_tipp.gokbId != newTipp.tippUuid) {
                    db_tipp.impId = (db_tipp.impId == newTipp.tippUuid) ? db_tipp.impId : newTipp.tippUuid
                    db_tipp.gokbId = newTipp.tippUuid
                    db_tipp.save(failOnError: true)
                }
            }

            def plat_instance = Platform.lookupOrCreatePlatform([name: newTipp.platform, gokbId: newTipp.platformUuid, primaryUrl: primaryUrl]);
            if(plat_instance) {
                plat_instance.primaryUrl = (plat_instance?.primaryUrl == primaryUrl) ? plat_instance?.primaryUrl : primaryUrl
                plat_instance.save()
            }

            def title_of_tipp_to_update = TitleInstance.findByGokbId(newTipp.title.gokbId)
            if (!title_of_tipp_to_update) {
                title_of_tipp_to_update = TitleInstance.lookupOrCreate(newTipp.title.identifiers, newTipp.title.name, newTipp.title.titleType, newTipp.title.gokbId, newTipp.title.status)
            }

            //continue here: use this occasion of needing TitleInstance diff check to check out whether the API gives title information
            if (Holders.config.globalDataSync.replaceLocalImpIds.TitleInstance && title_of_tipp_to_update && newTipp.title.gokbId &&
                    (title_of_tipp_to_update?.gokbId != newTipp.title.gokbId || !title_of_tipp_to_update?.gokbId)) {
                title_of_tipp_to_update.impId = (title_of_tipp_to_update.impId == newTipp.title.gokbId) ? title_of_tipp_to_update.impId : newTipp.title.gokbId
                title_of_tipp_to_update.gokbId = newTipp.title.gokbId
                title_of_tipp_to_update.save()
            }

        }

    }

    def static compareLocalPkgWithGokbPkg(ctx, oldpkg, newpkg, newTippClosure, updatedTippClosure, tippUnchangedClosure,deletedTippClosure, auto_accept)
    {
        //oldpkg?.nominalPlatformPrimaryUrl always null
        def primaryUrl = (oldpkg?.nominalPlatformPrimaryUrl == newpkg?.nominalPlatformPrimaryUrl) ? oldpkg?.nominalPlatformPrimaryUrl : newpkg?.nominalPlatformPrimaryUrl
        //println oldpkg
        //println "---------------------------------------------------------------------------------------------------------------------------------------"
        //println newpkg
        def oldpkgTippsTippUuid = oldpkg.tipps.collect{it.tippUuid}
        def newpkgTippsTippUuid = newpkg.tipps.collect{it.tippUuid}

        newpkg.tipps.each{ tippnew ->


            replaceImpIDwithGokbID(ctx, tippnew, primaryUrl)

            if(tippnew?.tippUuid in oldpkgTippsTippUuid)
            {

                    //Temporary
                    def localDuplicateTippEntries = TitleInstancePackagePlatform.executeQuery("from TitleInstancePackagePlatform as tipp where tipp.gokbId = :tippUuid and tipp.status != :status ", [tippUuid: tippnew.tippUuid, status: RDStore.TIPP_STATUS_DELETED])
                    def newAuto_accept = (localDuplicateTippEntries.size() > 1) ? true : false
                    newAuto_accept = auto_accept ?: newAuto_accept
                    if(localDuplicateTippEntries.size() > 1 && tippnew.status != 'Deleted') {
                        System.out.println("TIPP " + tippnew + " Was added to the package with autoAccept" + newAuto_accept);
                        newTippClosure(ctx, tippnew, newAuto_accept)
                    } else
                    {
                    //Temporary END

                        def tippold = oldpkg.tipps.find{it.tippUuid == tippnew.tippUuid && it.status != 'Deleted'}

                        def db_tipp = ctx.tipps.find {it.gokbId == tippnew.tippUuid && it.status?.value != 'Deleted'}
                        def tipp_diff = getTippDiff(tippold, tippnew)

                        if (tippnew.status != 'Current' && tipp_diff.size() == 0) {
                            if(tippnew.status != db_tipp.status.value) {
                                deletedTippClosure(ctx, tippold, auto_accept, db_tipp)
                                System.out.println("Title " + tippold + " Was removed from the package");
                            }

                        } else if (tipp_diff.size() == 0) {
                            tippUnchangedClosure(ctx, tippold);

                        } else {
                            // See if any of the actual properties are null
                            println("Got tipp diffs: ${tipp_diff}")
                            try {
                                updatedTippClosure(ctx, tippnew, tippold, tipp_diff, auto_accept, db_tipp)
                            }
                            catch (Exception e) {
                                System.err.println("Error on executing updated TIPP closure! Please verify logs: ${e.getMessage()}")
                                e.printStackTrace()
                            }
                        }
                    }
            }
            else if (!(tippnew.tippUuid in oldpkgTippsTippUuid) && tippnew.status != 'Deleted') {


                    System.out.println("TIPP " + tippnew + " Was added to the package with autoAccept " + auto_accept);
                    newTippClosure(ctx, tippnew, auto_accept)


            }
        }
        oldpkg.tipps.each { tippold ->
            if(!(tippold?.tippUuid in newpkgTippsTippUuid))
            {
                //was introduced to fight the duplicate TIPPs in Beck - may now be obsolete; it now causes problems elsewhere (Web of Science)
                def db_tipp = ctx.tipps.find {it.gokbId == tippold.tippUuid && it.status?.value != 'Deleted'}
                //if(tippold.status.id != db_tipp.status.id) {
                    deletedTippClosure(ctx, tippold, auto_accept, db_tipp)
                    System.out.println("TIPP " + tippold + " Was removed from the package");
                //}
            }
        }
    }

    static Map getCoverageDiffs(List<Map> covListA,List<Map> covListB) {
        boolean isDifferent = false
        covListA.each { covA ->
            Map equivalentCoverageEntry
            //several attempts ... take dates! Where are the unique identifiers when we REALLY need them??!!
            //here is the culprit
            for(def k: covA) {
                equivalentCoverageEntry = covListB.find { covB ->
                    covB[k] == covA[k]
                }
                if(equivalentCoverageEntry)
                    break
            }
            if(equivalentCoverageEntry) {
                TIPPCoverage.controlledProperties.each { cp ->
                    if(cp in ['startDate','endDate']) {
                        Calendar calA = Calendar.getInstance()
                        Calendar calB = Calendar.getInstance()
                        if(covA[cp] && equivalentCoverageEntry[cp]) {
                            calA.setTime(covA[cp])
                            calB.setTime(equivalentCoverageEntry[cp])
                            if(!(calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) && calA.get(Calendar.DAY_OF_YEAR) && calB.get(Calendar.DAY_OF_YEAR)))
                                isDifferent = true
                        }
                        else if(!covA[cp] && equivalentCoverageEntry[cp])
                            isDifferent = true
                        else if(covA[cp] && !equivalentCoverageEntry[cp])
                            isDifferent = true
                    }
                    else {
                        if(covA[cp] != equivalentCoverageEntry[cp])
                            isDifferent = true
                    }
                }
            }
            else {
                //there are coverage statements removed ...
                isDifferent = true
            }
        }
        if(covListB.size() > covListA.size()) {
            //there are new coverage statements ...
            isDifferent = true
        }
        if(isDifferent)
            [field: 'coverage', newValue: covListB, oldValue: covListA]
        else null
    }


}
