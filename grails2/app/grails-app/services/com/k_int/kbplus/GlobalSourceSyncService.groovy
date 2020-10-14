package com.k_int.kbplus

import de.laser.GlobalRecordSource
import de.laser.Identifier
import de.laser.IssueEntitlement
import de.laser.Org
import de.laser.OrgRole
import de.laser.Package
import de.laser.PendingChange
import de.laser.Platform
import de.laser.RefdataCategory
import de.laser.RefdataValue
import de.laser.SubscriptionPackage
import de.laser.TitleInstancePackagePlatform
import de.laser.system.SystemEvent
import de.laser.base.AbstractCoverage
import de.laser.IssueEntitlementCoverage
import de.laser.PendingChangeConfiguration
import de.laser.TIPPCoverage
import de.laser.exceptions.SyncException
import de.laser.helper.DateUtil
import de.laser.helper.RDConstants
import de.laser.helper.RDStore
import de.laser.interfaces.AbstractLockableService
import de.laser.titles.BookInstance
import de.laser.titles.DatabaseInstance
import de.laser.titles.JournalInstance
import de.laser.titles.TitleHistoryEvent
import de.laser.titles.TitleHistoryEventParticipant
import de.laser.titles.TitleInstance
import groovy.util.slurpersupport.GPathResult
import groovy.util.slurpersupport.NodeChild
import groovy.util.slurpersupport.NodeChildren
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseException
import org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin
import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import org.hibernate.SessionFactory
import org.hibernate.classic.Session
import org.springframework.transaction.TransactionStatus

import java.text.SimpleDateFormat
import java.util.concurrent.ExecutorService

/**
 * Implements the synchronisation workflow according to https://dienst-wiki.hbz-nrw.de/display/GDI/GOKB+Sync+mit+LASER
 */
//@Transactional
class GlobalSourceSyncService extends AbstractLockableService {

    SessionFactory sessionFactory
    ExecutorService executorService
    ChangeNotificationService changeNotificationService
    def genericOIDService
    def propertyInstanceMap = DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP
    GlobalRecordSource source

    final static Set<String> DATE_FIELDS = ['accessStartDate','accessEndDate','startDate','endDate']
    Map<String, RefdataValue> titleStatus = [:], titleMedium = [:], tippStatus = [:], packageStatus = [:], orgStatus = [:]

    SimpleDateFormat xmlTimestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

    boolean running = false

    /**
     * This is the entry point for triggering the sync workflow. To ensure locking, a flag will be set when a process is already running
     * @return a flag whether a process is already running
     */
    boolean startSync() {
        if (!running) {
            executorService.execute({ doSync() })
            //doSync()
            return true
        }
        else {
            log.warn("Sync already running, not starting again")
            return false
        }
    }

    /**
     * The sync process wrapper. It takes every {@link GlobalRecordSource}, fetches the information since a given timestamp
     * and updates the local records
     */
    void doSync() {
        running = true
        defineMapFields()
        //we need to consider that there may be several sources per instance
        List<GlobalRecordSource> jobs = GlobalRecordSource.findAll()
        jobs.each { source ->
            this.source = source
            try {
                SystemEvent.createEvent('GSSS_OAI_START',['jobId':source.id])
                Thread.currentThread().setName("GlobalDataSync")
                Date oldDate = source.haveUpTo
                Long maxTimestamp = 0
                log.info("getting records from job #${source.id} with uri ${source.uri} since ${oldDate} using ${source.fullPrefix}")
                //merging from OaiClient
                log.info("getting latest changes ...")
                List<List<Map<String,Object>>> tippsToNotify = []
                boolean more = true
                log.info("attempt get ...")
                String resumption = null
                // perform GET request, expection XML response data
                while(more) {
                    Map<String,String> queryParams = [verb:'ListRecords',metadataPrefix:'gokb']
                    //Map<String,String> queryParams = [verb:'GetRecord',metadataPrefix:'gokb',identifier:'a3f41aef-8316-442e-99e9-29e2f011fc22'] //for debugging
                    if(resumption) {
                        queryParams.resumptionToken = resumption
                        log.info("in loop, making request with link ${source.uri}?verb=ListRecords&metadataPrefix=gokb&resumptionToken=${resumption} ...")
                    }
                    else {
                        String fromParam = oldDate ? xmlTimestampFormat.format(oldDate) : ''
                        log.info("in loop, making first request, timestamp: ${fromParam} ...")
                        queryParams.metadataPrefix = source.fullPrefix
                        queryParams.from = fromParam
                    }
                    GPathResult listOAI = fetchRecord(source.uri,'packages',queryParams)
                    if(listOAI) {
                        updateNonPackageData(listOAI)
                        listOAI.record.each { NodeChild r ->
                            //continue processing here, original code jumps back to GlobalSourceSyncService
                            log.info("got OAI record ${r.header.identifier} datestamp: ${r.header.datestamp} job: ${source.id} url: ${source.uri}")
                            //String recUUID = r.header.uuid.text() ?: '0'
                            //String recIdentifier = r.header.identifier.text()
                            Date recordTimestamp = DateUtil.parseDateGeneric(r.header.datestamp.text())
                            //leave out GlobalRecordInfo update, no need to reflect it twice since we keep the package structure internally
                            //jump to packageReconcile which includes packageConv - check if there is a package, otherwise, update package data
                            tippsToNotify << createOrUpdatePackage(r.metadata.gokb.package)
                            if(recordTimestamp.getTime() > maxTimestamp)
                                maxTimestamp = recordTimestamp.getTime()
                        }
                        if(listOAI.resumptionToken.size() > 0 && listOAI.resumptionToken.text().length() > 0) {
                            resumption = listOAI.resumptionToken
                            log.info("Continue with next iteration, token: ${resumption}")
                            //cleanUpGorm()
                        }
                        else
                            more = false
                    }
                    log.info("Endloop")
                }
                if(maxTimestamp+1000 > oldDate.time) { // +1000 (i.e. 1 sec) because otherwise, the last record is always dumped in list
                    source.haveUpTo = new Date(maxTimestamp+1000)
                    source.save()
                    log.info("all OAI info fetched, local records updated, notifying dependent entitlements ...")
                }
                else {
                    log.info("all OAI info fetched, no records to update. Leaving timestamp as is ...")
                }
                cleanUpGorm()
                notifyDependencies(tippsToNotify)
                log.info("sync job finished")
                SystemEvent.createEvent('GSSS_OAI_COMPLETE',['jobId',source.id])
            }
            catch (Exception e) {
                SystemEvent.createEvent('GSSS_OAI_ERROR',['jobId':source.id])
                log.error("sync job has failed, please consult stacktrace as follows: ")
                e.printStackTrace()
            }
        }
        running = false
    }

    void updateNonPackageData(GPathResult oaiBranch) throws SyncException {
        List titleNodes = oaiBranch.'**'.findAll { node ->
            node.name() == "title"
        }
        Set<String> titlesToUpdate = []
        titleNodes.each { title ->
            //temp fix
            if(title.type.text() in ['BookInstance','DatabaseInstance','JournalInstance'])
                titlesToUpdate << title.'@uuid'.text()
            else {
                log.warn("Unimplemented title type ${title.type.text()}")
                SystemEvent.createEvent('GSSS_OAI_WARNING',[titleRecordKey:title.'@uuid'.text()])
            }
        }
        List providerNodes = oaiBranch.'**'.findAll { node ->
            node.name() == "nominalProvider"
        }
        Set<String> providersToUpdate = []
        providerNodes.each { provider ->
            providersToUpdate << provider.'@uuid'.text()
        }
        List platformNodes = oaiBranch.'**'.findAll { node ->
            node.name() in ["nominalPlatform","platform"]
        }
        Set<Map<String,String>> platformsToUpdate = []
        platformNodes.each { platform ->
            if (!platformsToUpdate.find { plat -> plat.platformUUID == platform.'@uuid'.text() })
                platformsToUpdate << [gokbId: platform.'@uuid'.text(), name: platform.name.text(), primaryUrl: platform.primaryUrl.text()]
        }
        titlesToUpdate.each { titleUUID ->
            //that may destruct debugging ...
            try {
                createOrUpdateTitle(titleUUID)
            }
            catch (SyncException e) {
                SystemEvent.createEvent('GSSS_OAI_WARNING',[titleRecordKey:titleUUID])
            }
        }
        providersToUpdate.each { providerUUID ->
            try {
                Org.withNewSession {
                    createOrUpdateProvider(providerUUID)
                }
            }
            catch (SyncException e) {
                SystemEvent.createEvent('GSSS_OAI_WARNING',[providerRecordKey:providerUUID])
            }
        }
        platformsToUpdate.each { Map<String,String> platformParams ->
            try {
                Platform.withNewSession {
                    createOrUpdatePlatform(platformParams)
                }
            }
            catch (SyncException e) {
                SystemEvent.createEvent('GSSS_OAI_WARNING',[platformRecordKey:platformParams.gokbId])
            }
        }
    }

    /**
     * Looks up for a given OAI-PMH extract if a local record exists or not. If no {@link Package} record exists, it will be
     * created with the given remote record data, otherwise, the local record is going to be updated. The {@link TitleInstancePackagePlatform records}
     * in the {@link Package} will be checked for differences and if there are such, the according fields updated. Same counts for the {@link TIPPCoverage} records
     * in the {@link TitleInstancePackagePlatform}s. If {@link Subscription}s are linked to the {@link Package}, the {@link IssueEntitlement}s (just as their
     * {@link IssueEntitlementCoverage}s) are going to be notified; it is up to the respective subscription tenants to accept the changes or not.
     * Replaces the method GokbDiffEngine.diff and the onNewTipp, onUpdatedTipp and onUnchangedTipp closures
     *
     * @param packageData - A {@link NodeChildren} list containing a OAI-PMH record extract for a given package
     * @return
     */
    List<Map<String,Object>> createOrUpdatePackage(NodeChildren packageData) {
        log.info('converting XML record into map and reconciling new package!')
        List<Map<String,Object>> tippsToNotify = [] //this is the actual return object; a pool within which we will contain all the TIPPs whose IssueEntitlements needs to be notified
        String packageUUID = packageData.'@uuid'.text()
        String packageName = packageData.name.text()
        RefdataValue packageStatus = RefdataValue.getByValueAndCategory(packageData.status.text(), RDConstants.PACKAGE_STATUS)
        RefdataValue packageScope = RefdataValue.getByValueAndCategory(packageData.scope.text(),RDConstants.PACKAGE_SCOPE) //needed?
        RefdataValue packageListStatus = RefdataValue.getByValueAndCategory(packageData.listStatus.text(),RDConstants.PACKAGE_LIST_STATUS) //needed?
        RefdataValue breakable = RefdataValue.getByValueAndCategory(packageData.breakable.text(),RDConstants.PACKAGE_BREAKABLE) //needed?
        RefdataValue consistent = RefdataValue.getByValueAndCategory(packageData.consistent.text(),RDConstants.PACKAGE_CONSISTENT) //needed?
        RefdataValue fixed = RefdataValue.getByValueAndCategory(packageData.fixed.text(),RDConstants.PACKAGE_FIXED) //needed?
        RefdataValue contentType = RefdataValue.getByValueAndCategory(packageData.contentType.text(),RDConstants.PACKAGE_CONTENT_TYPE)
        Date listVerifiedDate = packageData.listVerifiedDate.text() ? DateUtil.parseDateGeneric(packageData.listVerifiedDate.text()) : null
        //result.global = packageData.global.text() needed? not used in packageReconcile
        String providerUUID
        String platformUUID
        if(packageData.nominalProvider) {
            providerUUID = (String) packageData.nominalProvider.'@uuid'.text()
            //lookupOrCreateProvider(providerParams)
        }
        if(packageData.nominalPlatform) {
            platformUUID = (String) packageData.nominalPlatform.'@uuid'.text()
        }
        //ex packageConv, processing TIPPs - conversion necessary because package may be not existent in LAS:eR; then, no comparison is needed any more
        List<Map<String,Object>> tipps = []
        Set<String> platformsInPackage = [], titleInstancesInPackage = []
        packageData.TIPPs.TIPP.eachWithIndex { tipp, int ctr ->
            if(tipp.title.type.text() in ['BookInstance','DatabaseInstance','JournalInstance']) {
                tipps << tippConv(tipp)
                platformsInPackage << tipp.platform.'@uuid'.text()
                titleInstancesInPackage << tipp.title.'@uuid'.text()
            }
            else {
                log.warn("see above - unimplemented title type ${tipp.title.type.text()}")
            }
        }
        log.info("Rec conversion for package returns object with name ${packageName} and ${tipps.size()} tipps")
        Package.withTransaction { TransactionStatus stat ->
            Package result = Package.findByGokbId(packageUUID)
            try {
                log.info("fetching objects for keys ...")
                Map<String,Platform> newPlatforms = [:]
                Platform.findAllByGokbIdInList(platformsInPackage).each { Platform plat ->
                    newPlatforms.put(plat.gokbId,plat)
                }
                Map<String,TitleInstance> newTitleInstances = [:]
                TitleInstance.findAllByGokbIdInList(titleInstancesInPackage).each { TitleInstance ti ->
                    newTitleInstances.put(ti.gokbId,ti)
                }
                if(result) {
                    //local package exists -> update closure, build up GokbDiffEngine and the horrendous closures
                    log.info("package successfully found, processing LAS:eR id #${result.id}, with GOKb id ${result.gokbId}")
                    if(packageStatus == RDStore.PACKAGE_STATUS_DELETED && result.packageStatus != RDStore.PACKAGE_STATUS_DELETED) {
                        log.info("package #${result.id}, with GOKb id ${result.gokbId} got deleted, mark as deleted all cascaded elements and rapport!")
                        tippsToNotify << [event:"pkgDelete",diffs:[[prop: 'packageStatus', newValue: packageStatus, oldValue: result.packageStatus]],target:result]
                        result.packageStatus = packageStatus
                        if(result.save()) {
                            result.tipps.each { TitleInstancePackagePlatform tippA ->
                                log.info("TIPP with UUID ${tippA.gokbId} has been deleted from package ${result.gokbId}")
                                tippA.status = RDStore.TIPP_STATUS_DELETED
                                tippsToNotify << [event:"delete",target:tippA]
                                if(!tippA.save())
                                    throw new SyncException("Error on marking TIPP with UUID ${tippA.gokbId}: ${tippA.errors} as deleted!")
                            }
                        }
                        else {
                            throw new SyncException("Error on marking package ${result.id}: ${result.errors} as deleted!")
                        }
                    }
                    else {
                        Map<String,Object> newPackageProps = [
                                uuid: packageUUID,
                                name: packageName,
                                packageStatus: packageStatus,
                                listVerifiedDate: listVerifiedDate,
                                packageScope: packageScope,
                                packageListStatus: packageListStatus,
                                breakable: breakable,
                                consistent: consistent,
                                fixed: fixed,
                                contentType: contentType
                        ]
                        if(platformUUID) {
                            newPackageProps.nominalPlatform = Platform.findByGokbId(platformUUID)
                        }
                        if(providerUUID) {
                            newPackageProps.contentProvider = Org.findByGokbId(providerUUID)
                        }
                        Set<Map<String,Object>> pkgPropDiffs = getPkgPropDiff(result,newPackageProps)
                        result.name = packageName
                        result.packageStatus = packageStatus
                        result.listVerifiedDate = listVerifiedDate
                        result.packageScope = packageScope //needed?
                        result.packageListStatus = packageListStatus //needed?
                        result.breakable = breakable //needed?
                        result.consistent = consistent //needed?
                        result.fixed = fixed //needed?
                        result.contentType = contentType
                        if(platformUUID)
                            result.nominalPlatform = newPackageProps.nominalPlatform
                        if(providerUUID) {
                            createOrUpdatePackageProvider(newPackageProps.contentProvider,result)
                        }
                        if(result.save()) {
                            if(pkgPropDiffs)
                                tippsToNotify << [event:"pkgPropUpdate",diffs:pkgPropDiffs,target:result]
                            tipps.eachWithIndex { Map<String, Object> tippB, int index ->
                                if(index % 30 == 0 && index > 0) {
                                    stat.flush()
                                }
                                TitleInstancePackagePlatform tippA = result.tipps.find { TitleInstancePackagePlatform a -> a.gokbId == tippB.uuid } //we have to consider here TIPPs, too, which were deleted but have been reactivated
                                if(tippA) {
                                    tippA.refresh()
                                    //ex updatedTippClosure / tippUnchangedClosure
                                    RefdataValue status = tippStatus.get(tippB.status)
                                    if(status == RDStore.TIPP_STATUS_DELETED) {
                                        log.info("TIPP with UUID ${tippA.gokbId} has been deleted from package ${result.gokbId}")
                                        tippA.status = status
                                        tippsToNotify << [event:"delete",target:tippA]
                                    }
                                    else {
                                        Set<Map<String,Object>> diffs = getTippDiff(tippA,tippB) //includes also changes in coverage statement set
                                        if(diffs) {
                                            log.info("Got tipp diffs: ${diffs}")
                                            //process actual diffs
                                            diffs.each { diff ->
                                                if(diff.prop == 'coverage') {
                                                    diff.covDiffs.each { entry ->
                                                        switch(entry.event) {
                                                            case 'add':
                                                                if(!entry.target.save())
                                                                    throw new SyncException("Error on adding coverage statement for TIPP ${tippA.gokbId}: ${entry.target.errors}")
                                                                break
                                                            case 'delete': tippA.coverages.remove(entry.target)
                                                                entry.target.delete()
                                                                break
                                                            case 'update': entry.diffs.each { covDiff ->
                                                                entry.target[covDiff.prop] = covDiff.newValue
                                                            }
                                                                if(!entry.target.save())
                                                                    throw new SyncException("Error on updating coverage statement for TIPP ${tippA.gokbId}: ${entry.target.errors}")
                                                                break
                                                        }
                                                    }
                                                }
                                                else {
                                                    if (diff.prop in PendingChange.REFDATA_FIELDS) {
                                                        tippA[diff.prop] = RefdataValue.get(diff.newValue)
                                                    }
                                                    else {
                                                        tippA[diff.prop] = diff.newValue
                                                    }
                                                }
                                            }
                                            if(tippA.save())
                                                tippsToNotify << [event:'update',target:tippA,diffs:diffs]
                                            else throw new SyncException("Error on updating TIPP with UUID ${tippA.gokbId}: ${tippA.errors}")
                                        }
                                        /*//ex updatedTitleAfterPackageReconcile
                                        TitleInstance titleInstance = TitleInstance.findByGokbId(tippB.title.gokbId)
                                        //TitleInstance titleInstance = createOrUpdateTitle((String) tippB.title.gokbId)
                                        //createOrUpdatePlatform([name:tippB.platformName,gokbId:tippB.platformUUID,primaryUrl:tippB.primaryUrl],tippA.platform)
                                        if(titleInstance) {
                                            tippA.title = titleInstance
                                            tippA.platform = Platform.findByGokbId(tippB.platformUUID)
                                            if(!tippA.save())
                                                throw new SyncException("Error on updating TIPP with UUID ${tippA.gokbId}: ${tippA.errors}")
                                        }
                                        else {
                                            throw new SyncException("Title loading failed for ${tippB.title.gokbId}!")
                                        }*/
                                    }
                                }
                                else {
                                    //ex newTippClosure
                                    TitleInstancePackagePlatform target = addNewTIPP(result,tippB,newPlatforms,newTitleInstances)
                                    tippsToNotify << [event:'add',target:target]
                                }
                            }
                        }
                        else {
                            throw new SyncException("Error on updating package ${result.id}: ${result.errors}")
                        }
                    }
                }
                else {
                    //local package does not exist -> insert new data
                    log.info("creating new package ...")
                    result = new Package(
                            gokbId: packageData.'@uuid'.text(),
                            name: packageName,
                            listVerifiedDate: listVerifiedDate,
                            packageStatus: packageStatus,
                            packageScope: packageScope, //needed?
                            packageListStatus: packageListStatus, //needed?
                            breakable: breakable, //needed?
                            consistent: consistent, //needed?
                            fixed: fixed, //needed?
                            contentType: contentType
                    )
                    if(result.save()) {
                        if(providerUUID) {
                            Org provider = Org.findByGokbId(providerUUID)
                            createOrUpdatePackageProvider(provider,result)
                        }
                        if(platformUUID) {
                            result.nominalPlatform = Platform.findByGokbId(platformUUID)
                        }
                        tipps.eachWithIndex { tipp, int idx ->
                            log.info("now processing entry #${idx} with UUID ${tipp.uuid}")
                            addNewTIPP(result,tipp,newPlatforms,newTitleInstances)
                        }
                    }
                    else {
                        throw new SyncException("Error on saving package: ${result.errors}")
                    }
                }
                log.info("before processing identifiers - identifier count: ${packageData.identifiers.identifier.size()}")
                packageData.identifiers.identifier.each { id ->
                    if(id.'@namespace'.text().toLowerCase() != 'originediturl') {
                        Identifier.withTransaction {
                            Identifier.construct([namespace: id.'@namespace'.text(), value: id.'@value'.text(), reference: result])
                        }
                    }
                }
                log.info("after processing identifiers")
                stat.flush()
            }
            catch (Exception e) {
                log.error("Error on updating package ${result.id} ... rollback!")
                e.printStackTrace()
                stat.setRollbackOnly()
            }
        }
        log.debug("returning tippsToNotify")
        tippsToNotify
    }

    /**
     * Converts the TIPP data from OAI-XML elements into an object structure, reflected by a {@link Map}, representing the {@link TitleInstancePackagePlatform} and {@link TIPPCoverage} class structures
     * @param TIPPData - the base branch ({@link NodeChildren}) containing the OAI extract for {@link TitleInstancePackagePlatform} data
     * @return a {@link Map} containing the underlying TIPP data
     */
    Map<String,Object> tippConv(tipp) {
        Map<String,Object> updatedTIPP = [
                title: [
                        gokbId: tipp.title.'@uuid'.text()
                ],
                platformUUID: tipp.platform.'@uuid'.text(),
                status: tipp.status.text(),
                coverages: [],
                hostPlatformURL: tipp.url.text(),
                identifiers: [],
                id: tipp.'@id'.text(),
                uuid: tipp.'@uuid'.text(),
                accessStartDate : tipp.access.'@start'.text() ? DateUtil.parseDateGeneric(tipp.access.'@start'.text()) : null,
                accessEndDate   : tipp.access.'@end'.text() ? DateUtil.parseDateGeneric(tipp.access.'@end'.text()) : null,
                medium      : tipp.medium.text()
        ]
        updatedTIPP.identifiers.add([namespace: 'uri', value: tipp.'@id'.tippId])
        if(tipp.title.type.text() == 'JournalInstance') {
            tipp.coverage.each { cov ->
                updatedTIPP.coverages << [
                        startDate: cov.'@startDate'.text() ? DateUtil.parseDateGeneric(cov.'@startDate'.text()) : null,
                        endDate: cov.'@endDate'.text() ? DateUtil.parseDateGeneric(cov.'@endDate'.text()) : null,
                        startVolume: cov.'@startVolume'.text() ?: null,
                        endVolume: cov.'@endVolume'.text() ?: null,
                        startIssue: cov.'@startIssue'.text() ?: null,
                        endIssue: cov.'@endIssue'.text() ?: null,
                        coverageDepth: cov.'@coverageDepth'.text() ?: null,
                        coverageNote: cov.'@coverageNote'.text() ?: null,
                        embargo: cov.'@embargo'.text() ?: null
                ]
            }
            updatedTIPP.coverages = updatedTIPP.coverages.toSorted { a, b -> a.startDate <=> b.startDate }
        }
        updatedTIPP
    }

    /**
     * Replaces the onNewTipp closure.
     * Creates a new {@link TitleInstance} with its respective {@link TIPPCoverage} statements
     * @param pkg
     * @param tippData
     * @return the new {@link TitleInstancePackagePlatform} object
     */
    TitleInstancePackagePlatform addNewTIPP(Package pkg, Map<String,Object> tippData,Map<String,Platform> platformsInPackage,Map<String,TitleInstance> titleInstancesInPackage) throws SyncException {
        TitleInstancePackagePlatform newTIPP = new TitleInstancePackagePlatform(
                gokbId: tippData.uuid,
                status: tippStatus.get(tippData.status),
                hostPlatformURL: tippData.hostPlatformURL,
                accessStartDate: (Date) tippData.accessStartDate,
                accessEndDate: (Date) tippData.accessEndDate,
                pkg: pkg
        )
        //ex updatedTitleAfterPackageReconcile
        long start = System.currentTimeMillis()
        TitleInstance titleInstance = titleInstancesInPackage.get(tippData.title.gokbId)
        newTIPP.platform = platformsInPackage.get(tippData.platformUUID)
        log.debug("time needed for queries: ${System.currentTimeMillis()-start}")
        if(titleInstance) {
            newTIPP.title = titleInstance
            if(newTIPP.save()) {
                tippData.coverages.each { covB ->
                    TIPPCoverage covStmt = new TIPPCoverage(
                            startDate: (Date) covB.startDate ?: null,
                            startVolume: covB.startVolume,
                            startIssue: covB.startIssue,
                            endDate: (Date) covB.endDate ?: null,
                            endVolume: covB.endVolume,
                            endIssue: covB.endIssue,
                            embargo: covB.embargo,
                            coverageDepth: covB.coverageDepth,
                            coverageNote: covB.coverageNote,
                            tipp: newTIPP
                    )
                    if (!covStmt.save())
                        throw new SyncException("Error on saving coverage data: ${covStmt.errors}")
                }
                newTIPP
            }
            else
                throw new SyncException("Error on saving TIPP data: ${newTIPP.errors}")
        }
        else {
            throw new SyncException("Title loading error for UUID ${tippData.title.gokbId}!")
        }
    }

    /**
     * Checks for a given UUID if a {@link TitleInstance} is existing in the database, if it does not exist, it will be created.
     * Replaces the former updatedTitleAfterPackageReconcile, titleConv and titleReconcile closure
     *
     * @param titleUUID - the GOKb UUID of the {@link TitleInstance} to create or update
     * @return the new or updated {@link TitleInstance}
     */
    TitleInstance createOrUpdateTitle(String titleUUID) throws SyncException {
        GPathResult titleOAI = fetchRecord(source.uri,'titles',[verb:'GetRecord',metadataPrefix:source.fullPrefix,identifier:titleUUID])
        if(titleOAI) {
            TitleInstance.withNewSession { Session session ->
                GPathResult titleRecord = titleOAI.record.metadata.gokb.title
                log.info("title record loaded, converting XML record and reconciling title record for UUID ${titleUUID} ...")
                TitleInstance titleInstance = TitleInstance.findByGokbId(titleUUID)
                if(titleRecord.type.text()) {
                    RefdataValue status = titleStatus.get(titleRecord.status.text())
                    //titleRecord.medium.text()
                    RefdataValue medium = titleMedium.get(titleRecord.medium.text()) //misunderstandable mapping ...
                    try {
                        switch(titleRecord.type.text()) {
                            case 'BookInstance':
                                Integer editionNumber = null
                                if(titleRecord.editionNumber.text()) {
                                    try {
                                        editionNumber = Integer.parseInt(titleRecord.editionNumber.text())
                                    }
                                    catch (NumberFormatException e) {
                                        log.warn("${titleUUID} has invalid edition number supplied: ${titleRecord.editionNumber.text()}")
                                        editionNumber = null
                                    }
                                }
                                Map<String,Object> newTitleParams = [gokbId:titleUUID,
                                                                     medium: medium,
                                                                     status: status,
                                                                     title:titleRecord.name.text(),
                                                                     editionNumber: editionNumber,
                                                                     editionDifferentiator: titleRecord.editionDifferentiator.text() ?: null,
                                                                     editionStatement: titleRecord.editionStatement.text() ?: null,
                                                                     volume: titleRecord.volumeNumber.text() ?: null,
                                                                     dateFirstInPrint: titleRecord.dateFirstInPrint ? DateUtil.parseDateGeneric(titleRecord.dateFirstInPrint.text()) : null,
                                                                     dateFirstOnline: titleRecord.dateFirstOnline ? DateUtil.parseDateGeneric(titleRecord.dateFirstOnline.text()) : null,
                                                                     firstAuthor: titleRecord.firstAuthor.text() ?: null,
                                                                     firstEditor: titleRecord.firstEditor.text() ?: null]
                                titleInstance = titleInstance ? (BookInstance) titleInstance :  BookInstance.construct(newTitleParams)
                                titleInstance.editionNumber = editionNumber
                                titleInstance.editionDifferentiator = titleRecord.editionDifferentiator.text() ?: null
                                titleInstance.editionStatement = titleRecord.editionStatement.text() ?: null
                                titleInstance.volume = titleRecord.volumeNumber.text() ?: null
                                titleInstance.dateFirstInPrint = titleRecord.dateFirstInPrint ? DateUtil.parseDateGeneric(titleRecord.dateFirstInPrint.text()) : null
                                titleInstance.dateFirstOnline = titleRecord.dateFirstOnline ? DateUtil.parseDateGeneric(titleRecord.dateFirstOnline.text()) : null
                                titleInstance.firstAuthor = titleRecord.firstAuthor.text() ?: null
                                titleInstance.firstEditor = titleRecord.firstEditor.text() ?: null
                                break
                            case 'DatabaseInstance': titleInstance = titleInstance ? (DatabaseInstance) titleInstance : DatabaseInstance.construct([gokbId:titleUUID,title:titleRecord.name.text(),medium: medium,status: status])
                                break
                            case 'JournalInstance': titleInstance = titleInstance ? (JournalInstance) titleInstance : JournalInstance.construct([gokbId:titleUUID,title:titleRecord.name.text(),medium: medium,status: status])
                                break
                        }
                    }
                    catch (GroovyCastException e) {
                        log.error("Title type mismatch! This should not be possible! Inform GOKb team! -> ${titleInstance.gokbId} is corrupt!")
                        SystemEvent.createEvent('GSSS_OAI_WARNING',[titleInstance:titleInstance.gokbId,errorType:"titleTypeMismatch"])
                    }
                    //this is taken only if object has been persisted because of other transaction
                    log.debug("processing ${titleInstance.title} before update")
                    titleInstance.title = titleRecord.name.text()
                    titleInstance.medium = medium
                    titleInstance.status = status
                    log.debug("title name before save: ${titleInstance.title}")
                    if(titleInstance.save()) {
                        log.debug("title name before refresh: ${titleInstance.title}")
                        //titleInstance.refresh()
                        if(titleRecord.publishers) {
                            OrgRole.executeUpdate('delete from OrgRole oo where oo.title = :titleInstance',[titleInstance: titleInstance])
                            titleRecord.publishers.publisher.each { pubData ->
                                Map<String,Object> publisherParams = [
                                        name: pubData.name.text(),
                                        //status: RefdataValue.getByValueAndCategory(pubData.status.text(),RefdataCategory.ORG_STATUS), -> is for combo type
                                        gokbId: pubData.'@uuid'.text()
                                ]
                                Set<Map<String,String>> identifiers = []
                                pubData.identifiers.identifier.each { identifier ->
                                    //the filter is temporary, should be excluded ...
                                    if(identifier.'@namespace'.text().toLowerCase() != 'originediturl') {
                                        identifiers << [namespace:(String) identifier.'@namespace'.text(),value:(String) identifier.'@value'.text()]
                                    }
                                }
                                publisherParams.identifiers = identifiers
                                Org publisher = lookupOrCreateTitlePublisher(publisherParams)
                                //ex OrgRole.assertOrgTitleLink
                                OrgRole titleLink = OrgRole.findByTitleAndOrgAndRoleType(titleInstance,publisher,RDStore.OR_PUBLISHER)
                                if(!titleLink) {
                                    titleLink = new OrgRole(title:titleInstance,org:publisher,roleType:RDStore.OR_PUBLISHER,isShared:false)
                                    /*
                                    its usage / relevance for LAS:eR is unclear for the moment, must be clarified
                                    if(pubData.startDate)
                                        titleLink.startDate = DateUtil.parseDateGeneric(pubData.startDate.text())
                                    if(pubData.endDate)
                                        titleLink.endDate = DateUtil.parseDateGeneric(pubData.endDate.text())
                                    */
                                    if(!titleLink.save())
                                        throw new SyncException("Error on creating title link: ${titleLink.errors}")
                                }
                            }
                        }
                        if(titleRecord.identifiers) {
                            //I hate this solution ... wrestlers of GOKb stating that Identifiers do not need UUIDs were stronger.
                            if(titleInstance.ids){
                                titleInstance.ids.clear()
                                titleInstance.save() //damn those wrestlers ...
                            }
                            titleRecord.identifiers.identifier.each { idData ->
                                if(idData.'@namespace'.text().toLowerCase() != 'originediturl') {
                                    Identifier.withTransaction { TransactionStatus transactionStatus ->
                                        Identifier.construct([namespace: idData.'@namespace'.text(), value: idData.'@value'.text(), reference: titleInstance])
                                    }
                                }
                            }
                        }
                        if(titleRecord.history) {
                            titleRecord.history.historyEvent.each { eventData ->
                                if(eventData.date.text()) {
                                    Set<TitleInstance> from = [], to = []
                                    eventData.from.each { fromEv ->
                                        try {
                                            from << createOrUpdateHistoryParticipant(fromEv,titleInstance.class.name)
                                        }
                                        catch (SyncException e) {
                                            throw e
                                        }
                                    }
                                    eventData.to.each { toEv ->
                                        try {
                                            to << createOrUpdateHistoryParticipant(toEv,titleInstance.class.name)
                                        }
                                        catch (SyncException e) {
                                            throw e
                                        }
                                    }
                                    Date eventDate = DateUtil.parseDateGeneric(eventData.date.text())
                                    String baseQuery = "select the from TitleHistoryEvent the where the.eventDate = :eventDate"
                                    Map<String,Object> queryParams = [eventDate:eventDate]
                                    if(from) {
                                        baseQuery += " and exists (select p from the.participants p where p.participant in :from and p.participantRole = 'from')"
                                        queryParams.from = from
                                    }
                                    if(to) {
                                        baseQuery += " and exists (select p from the.participants p where p.participant in :to and p.participantRole = 'to')"
                                        queryParams.to = to
                                    }
                                    List<TitleHistoryEvent> events = TitleHistoryEvent.executeQuery(baseQuery,queryParams)
                                    if(!events) {
                                        Map<String,Object> event = [:]
                                        event.from = from
                                        event.to = to
                                        event.internalId = eventData.'@id'.text()
                                        event.eventDate = eventDate
                                        TitleHistoryEvent the = new TitleHistoryEvent(event)
                                        if(the.save()) {
                                            from.each { partData ->
                                                TitleHistoryEventParticipant participant = new TitleHistoryEventParticipant(event:the,participant:partData,participantRole:'from')
                                                if(!participant.save())
                                                    throw new SyncException("Error on creating from participant: ${participant.errors}")
                                            }
                                            to.each { partData ->
                                                TitleHistoryEventParticipant participant = new TitleHistoryEventParticipant(event:the,participant:partData,participantRole:'to')
                                                if(!participant.save())
                                                    throw new SyncException("Error on creating to participant: ${participant.errors}")
                                            }
                                        }
                                        else throw new SyncException("Error on creating title history event: ${the.errors}")
                                    }
                                }
                                else {
                                    log.error("Title history event without date, that should not be, report history event with internal ID ${eventData.@id.text()} to GOKb!")
                                    SystemEvent.createEvent('GSSS_OAI_WARNING',[titleHistoryEvent:eventData.@id.text(),errorType:"historyEventWithoutDate"])
                                }
                            }
                        }
                        titleInstance
                    }
                    else {
                        throw new SyncException("Error on creating title instance: ${titleInstance.errors}")
                    }
                }
                else {
                    throw new SyncException("ALARM! Title record ${titleUUID} without title type! Unable to process!")
                }
            }
        }
        else {
            throw new SyncException("Title creation for ${titleUUID} called without record data! PANIC!")
        }
    }

    /**
     * Creates or updates a {@link TitleInstance} as history participant
     * @param particData - the OAI extract of the history participant
     * @param titleType - the class name of the title history participant
     * @return the updated title history participant statement
     * @throws SyncException
     */
    TitleInstance createOrUpdateHistoryParticipant(particData, String titleType) throws SyncException {
        TitleInstance participant = TitleInstance.findByGokbId(particData.uuid.text())
        try {
            switch(titleType) {
                case BookInstance.class.name: participant = participant ? (BookInstance) participant : BookInstance.construct([gokbId:particData.uuid.text()])
                    break
                case DatabaseInstance.class.name: participant = participant ? (DatabaseInstance) participant : DatabaseInstance.construct([gokbId:particData.uuid.text()])
                    break
                case JournalInstance.class.name: participant = participant ? (JournalInstance) participant : JournalInstance.construct([gokbId:particData.uuid.text()])
                    break
            }
            participant.status = titleStatus.get(particData.status.text())
            participant.title = particData.title.text()
            if(participant.save()) {
                if(particData.identifiers) {
                    particData.identifiers.identifier.each { idData ->
                        if(idData.'@namespace'.text().toLowerCase() != 'originediturl') {
                            Identifier.withTransaction {
                                Identifier.construct([namespace: idData.'@namespace'.text(), value: idData.'@value'.text(), reference: participant])
                            }
                        }
                    }
                }
                Identifier.withTransaction {
                    Identifier.construct([namespace:'uri',value:particData.internalId.text(),reference:participant])
                }
                participant
            }
            else {
                throw new SyncException(participant.errors)
            }
        }
        catch (GroovyCastException e) {
            throw new SyncException("Title type mismatch! This should not be possible! Inform GOKb team! -> ${participant.gokbId} is corrupt!")
        }
    }

    /**
     * Was formerly in the {@link Org} domain class; deployed for better maintainability
     * Checks for a given UUID if the provider exists, otherwise, it will be created. The
     * default {@link Platform}s are set up or updated as well
     *
     * @param providerUUID - the GOKb UUID of the given provider {@link Org}
     * @throws SyncException
     */
    void createOrUpdateProvider(String providerUUID) throws SyncException {
        //Org.lookupOrCreate2 simplified
        GPathResult providerOAI = fetchRecord(source.uri,'orgs',[verb:'GetRecord',metadataPrefix:source.fullPrefix,identifier:providerUUID])
        //check provider entry: for some reason, name has not been filled properly, crashed at record b8760baa-cc1b-4f39-b07d-d19b5bd86ea5
        if(providerOAI) {
            GPathResult providerRecord = providerOAI.record.metadata.gokb.org
            log.info("provider record loaded, converting XML record and reconciling title record for UUID ${providerUUID} ...")
            Org.withTransaction {
                Org provider = Org.findByGokbId(providerUUID)
                if(provider) {
                    provider.name = providerRecord.name.text()
                    provider.status = orgStatus.get(providerRecord.status.text())
                }
                else {
                    provider = new Org(
                            name: providerRecord.name.text(),
                            sector: RDStore.O_SECTOR_PUBLISHER,
                            type: [RDStore.OT_PROVIDER],
                            status: orgStatus.get(providerRecord.status.text()),
                            gokbId: providerUUID
                    )
                }
                if(provider.save()) {
                    providerRecord.providedPlatforms.platform.each { plat ->
                        //ex setOrUpdateProviderPlattform()
                        log.info("checking provider with uuid ${providerUUID}")
                        createOrUpdatePlatform([gokbId: plat.'@uuid'.text(), name: plat.name.text(), primaryUrl: plat.primaryUrl.text(), platformProvider:providerUUID])
                        Platform platform = Platform.findByGokbId(plat.'@uuid'.text())
                        if(platform.org != provider) {
                            platform.org = provider
                            platform.save()
                        }
                    }
                }
                else throw new SyncException(provider.errors)
            }
        }
        else throw new SyncException("Provider loading failed for UUID ${providerUUID}!")
    }

    /**
     * Was complicatedly included in the Org domain class, has been deployed externally for better maintainability
     * Retrieves an {@link Org} instance as title publisher, if the given {@link Org} instance does not exist, it will be created.
     *
     * @param publisherParams - a {@link Map} containing the OAI PMH extract of the title publisher
     * @return the title publisher {@link Org}
     * @throws SyncException
     */
    Org lookupOrCreateTitlePublisher(Map<String,Object> publisherParams) throws SyncException {
        if(publisherParams.gokbId && publisherParams.gokbId instanceof String) {
            //Org.lookupOrCreate simplified, leading to Org.lookupOrCreate2
            Org publisher = Org.findByGokbId((String) publisherParams.gokbId)
            if(!publisher) {
                Org.withTransaction {
                    publisher = new Org(
                            name: publisherParams.name,
                            status: RDStore.O_STATUS_CURRENT,
                            gokbId: publisherParams.gokbId,
                            sector: publisherParams.status instanceof RefdataValue ? (RefdataValue) publisherParams.status : null
                    )
                    if(publisher.save()) {
                        publisherParams.identifiers.each { Map<String,Object> pubId ->
                            pubId.reference = publisher
                            Identifier.construct(pubId)
                        }
                    }
                    else {
                        throw new SyncException(publisher.errors)
                    }
                }
            }
            publisher
        }
        else {
            throw new SyncException("Org submitted without UUID! No checking possible!")
        }
    }

    /**
     * Updates a {@link Platform} with the given parameters. If it does not exist, it will be created.
     *
     * @param platformParams - the platform parameters
     * @throws SyncException
     */
    void createOrUpdatePlatform(Map<String,String> platformParams) throws SyncException {
        Platform.withTransaction {
            Platform platform = Platform.findByGokbId(platformParams.gokbId)
            if(platform) {
                platform.name = platformParams.name
            }
            else {
                platform = new Platform(name: platformParams.name, gokbId: platformParams.gokbId)
            }
            platform.normname = platformParams.name.trim().toLowerCase()
            if(platformParams.primaryUrl)
                platform.primaryUrl = new URL(platformParams.primaryUrl)
            if(platformParams.platformProvider)
                platform.org = Org.findByGokbId(platformParams.platformProvider)
            if(!platform.save()) {
                throw new SyncException("Error on saving platform: ${platform.errors}")
            }
        }
    }

    /**
     * Checks for a given provider uuid if there is a link with the package for the given uuid
     * @param providerUUID - the provider UUID
     * @param pkg - the package to check against
     */
    void createOrUpdatePackageProvider(Org provider, Package pkg) throws SyncException {
        OrgRole providerRole = OrgRole.findByPkgAndRoleTypeInList(pkg,[RDStore.OR_PROVIDER,RDStore.OR_CONTENT_PROVIDER])
        if(providerRole) {
            providerRole.org = provider
        }
        else {
            providerRole = new OrgRole(org:provider,pkg:pkg,roleType: RDStore.OR_PROVIDER, isShared: false)
        }
        if(!providerRole.save()) {
            throw new SyncException("Error on saving org role: ${providerRole.errors}")
        }
    }

    /**
     * Compares two packages on domain property level against each other, retrieving the differences between both.
     * @param pkgA - the old package (as {@link Package} which is already persisted)
     * @param pkgB - the new package (as unprocessed {@link Map}
     * @return a {@link Set} of {@link Map}s with the differences
     */
    Set<Map<String,Object>> getPkgPropDiff(Package pkgA, Map<String,Object> pkgB) {
        log.info("processing package prop diffs; the respective GOKb UUIDs are: ${pkgA.gokbId} (LAS:eR) vs. ${pkgB.uuid} (remote)")
        Set<Map<String,Object>> result = []
        Set<String> controlledProperties = ['name','packageStatus','listVerifiedDate','packageScope','packageListStatus','breakable','consistent','fixed']

        controlledProperties.each { prop ->
            if(pkgA[prop] != pkgB[prop]) {
                if(prop in PendingChange.REFDATA_FIELDS)
                    result.add([prop: prop, newValue: pkgB[prop]?.id, oldValue: pkgA[prop]?.id])
                else result.add([prop: prop, newValue: pkgB[prop], oldValue: pkgA[prop]])
            }
        }

        if(pkgA.nominalPlatform != pkgB.nominalPlatform) {
            result.add([prop: 'nominalPlatform', newValue: pkgB.nominalPlatform?.name, oldValue: pkgA.nominalPlatform?.name])
        }

        if(pkgA.contentProvider != pkgB.contentProvider) {
            result.add([prop: 'nominalProvider', newValue: pkgB.contentProvider?.name, oldValue: pkgA.contentProvider?.name])
        }

        result
    }

    /**
     * Compares two package entries against each other, retrieving the differences between both.
     * @param tippa - the old TIPP
     * @param tippb - the new TIPP
     * @return a {@link Set} of {@link Map}s with the differences
     */
    Set<Map<String,Object>> getTippDiff(tippa, tippb) {
        if(tippa instanceof TitleInstancePackagePlatform && tippb instanceof Map)
            log.info("processing diffs; the respective GOKb UUIDs are: ${tippa.gokbId} (LAS:eR) vs. ${tippb.uuid} (remote)")
        else if(tippa instanceof IssueEntitlement && tippb instanceof TitleInstancePackagePlatform)
            log.info("processing diffs; the respective objects are: ${tippa.id} (IssueEntitlement) pointing to ${tippb.id} (TIPP)")
        Set<Map<String, Object>> result = []

        if (tippa.hasProperty("hostPlatformURL") && tippa.hostPlatformURL != tippb.hostPlatformURL) {
            result.add([prop: 'hostPlatformURL', newValue: tippb.hostPlatformURL, oldValue: tippa.hostPlatformURL])
        }

        // This is the boss enemy when refactoring coverage statements ... works so far, is going to be kept
        // the question marks are necessary because only JournalInstance's TIPPs are supposed to have coverage statements
        if(tippa.coverages?.size() > 0 && tippb.coverages?.size() > 0){
            Set<Map<String, Object>> coverageDiffs = getCoverageDiffs(tippa,tippb.coverages)
            if(!coverageDiffs.isEmpty())
                result.add([prop: 'coverage', covDiffs: coverageDiffs])
        }

        if (tippa.accessStartDate != tippb.accessStartDate) {
            result.add([prop: 'accessStartDate', newValue: tippb.accessStartDate, oldValue: tippa.accessStartDate])
        }

        if (tippa.accessEndDate != tippb.accessEndDate) {
            result.add([prop: 'accessEndDate', newValue: tippb.accessEndDate, oldValue: tippa.accessEndDate])
        }

        if(tippa instanceof TitleInstancePackagePlatform && tippb instanceof Map) {
            if(tippa.status != tippStatus.get(tippb.status)) {
                result.add([prop: 'status', newValue: tippStatus.get(tippb.status).id, oldValue: tippa.status.id])
            }
        }
        else if(tippa instanceof IssueEntitlement && tippb instanceof TitleInstancePackagePlatform) {
            if(tippa.status != tippb.status) {
                result.add([prop: 'status', newValue: tippb.status.id, oldValue: tippa.status.id])
            }
        }

        result
    }

    /**
     * Compares two coverage entries against each other, retrieving the differences between both.
     * @param tippA - the old {@link TitleInstancePackagePlatform} object, containing the current {@link Set} of coverages
     * @param covListB - the new coverage statements (a {@link List} of remote records, kept in {@link Map}s)
     * @return a {@link Set} of {@link Map}s reflecting the differences between the coverage statements
     */
    Set<Map<String,Object>> getCoverageDiffs(tippA,covListB) {
        Set covDiffs = []
        Set covListA = tippA.coverages
        if(covListA.size() == covListB.size()) {
            //coverage statements may have changed or not, no deletions or insertions
            //sorting has been done by mapping (listA) resp. when converting XML data (listB)
            covListB.each { covB ->
                def covA = locateEquivalent(covB,covListA)
                Set<Map<String,Object>> currDiffs = covA.compareWith(covB)
                if(currDiffs)
                    covDiffs << [event: 'update', target: covA, diffs: currDiffs]
            }
        }
        else if(covListA.size() > covListB.size()) {
            //coverage statements have been deleted
            Set toKeep = []
            covListB.each { covB ->
                def covA = locateEquivalent(covB,covListA)
                if(covA) {
                    toKeep << covA
                    Set<Map<String,Object>> currDiffs = covA.compareWith(covB)
                    if(currDiffs)
                        covDiffs << [event: 'update', target: covA, diffs: currDiffs]
                }
                else {
                    //a new statement may have been added for which I cannot determine an equivalent
                    Map<String,Object> params = [startDate: (Date) covB.startDate,
                                                 startVolume: covB.startVolume,
                                                 startIssue: covB.startIssue,
                                                 endDate: (Date) covB.endDate,
                                                 endVolume: covB.endVolume,
                                                 endIssue: covB.endIssue,
                                                 embargo: covB.embargo,
                                                 coverageDepth: covB.coverageDepth,
                                                 coverageNote: covB.coverageNote]
                    AbstractCoverage newStatement
                    if(tippA instanceof TitleInstancePackagePlatform)
                        newStatement = new TIPPCoverage(params+[tipp: tippA])
                    else if(tippA instanceof IssueEntitlement)
                        newStatement = new IssueEntitlementCoverage(params+[issueEntitlement: tippA])
                    covDiffs << [event: 'add', target: newStatement]
                }
            }
            covListA.each { covA ->
                if(!toKeep.contains(covA)) {
                    covDiffs << [event: 'delete', target: covA]
                }
            }
        }
        else if(covListA.size() < covListB.size()) {
            //coverage statements have been added
            covListB.each { covB ->
                def covA = locateEquivalent(covB,covListA)
                if(covA) {
                    Set<Map<String,Object>> currDiffs = covA.compareWith(covB)
                    if(currDiffs)
                        covDiffs << [event: 'update', target: covA, diffs: currDiffs]
                }
                else {
                    Map<String,Object> params = [startDate: (Date) covB.startDate,
                                                 startVolume: covB.startVolume,
                                                 startIssue: covB.startIssue,
                                                 endDate: (Date) covB.endDate,
                                                 endVolume: covB.endVolume,
                                                 endIssue: covB.endIssue,
                                                 embargo: covB.embargo,
                                                 coverageDepth: covB.coverageDepth,
                                                 coverageNote: covB.coverageNote]
                    AbstractCoverage newStatement
                    if(tippA instanceof TitleInstancePackagePlatform)
                        newStatement = new TIPPCoverage(params+[tipp: tippA])
                    if(tippA instanceof IssueEntitlement)
                        newStatement = new IssueEntitlementCoverage(params+[issueEntitlement: tippA])
                    covDiffs << [event: 'add', target: newStatement]
                }
            }
        }
        covDiffs
    }

    /**
     * Contrary to {@link AbstractCoverage}.findEquivalent(), this method locates a non-persisted coverage statement an equivalent from the given {@link Collection}
     * @param covB - a {@link Map}, reflecting the OAI coverage node
     * @param listA - a {@link Collection} on {@link TIPPCoverage} statements, the list to be updated
     * @return the equivalent LAS:eR {@link TIPPCoverage} from the collection
     */
    AbstractCoverage locateEquivalent(covB, listA) {
        AbstractCoverage equivalent = null
        for (String k : ['startDate','startVolume','endDate','endVolume']) {
            if(k in ['startDate','endDate']) {
                Calendar calA = Calendar.getInstance(), calB = Calendar.getInstance()
                listA.each { covA ->
                    if(covA[k] != null && covB[k] != null) {
                        calA.setTime(covA[k])
                        calB.setTime(covB[k])
                        if (calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) && calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR))
                            equivalent = covA
                    }
                    else if(covA[k] == null && covB[k] == null)
                        equivalent = covA
                }
            }
            else
                equivalent = listA.find { it[k] == covB[k] }
            if (equivalent != null) {
                println "Coverage statement ${equivalent.id} located as equivalent to ${covB}"
                break
            }
        }
        equivalent
    }

    void notifyDependencies(List<List<Map<String,Object>>> tippsToNotify) {
        //if everything went well, we should have here the list of tipps to notify ...
        tippsToNotify.each { List<Map<String,Object>> entry ->
            entry.eachWithIndex { Map<String,Object> notify, int index ->
                log.debug("now processing entry #${index}, payload: ${notify}")
                if(notify.event in ['pkgPropUpdate','pkgDelete']) {
                    Package target = (Package) notify.target
                    Set<SubscriptionPackage> spConcerned = SubscriptionPackage.findAllByPkg(target)
                    if(notify.event == 'pkgPropUpdate') {
                        spConcerned.each { SubscriptionPackage sp ->
                            Map<String,Object> changeMap = [target:sp.subscription, oid:genericOIDService.getOID(target)]
                            notify.diffs.each { diff ->
                                changeMap.oldValue = diff.oldValue
                                changeMap.newValue = diff.newValue
                                changeMap.prop = diff.prop
                                changeNotificationService.determinePendingChangeBehavior(changeMap,PendingChangeConfiguration.PACKAGE_PROP,sp)
                            }
                        }
                    }
                    else if(notify.event == 'pkgDelete') {
                        spConcerned.each { SubscriptionPackage sp ->
                            changeNotificationService.determinePendingChangeBehavior([target:sp.subscription,oid:genericOIDService.getOID(target)],PendingChangeConfiguration.PACKAGE_DELETED,sp)
                        }
                    }
                }
                else {
                    TitleInstancePackagePlatform target = (TitleInstancePackagePlatform) notify.target
                    if(notify.event == 'add') {
                        Set<SubscriptionPackage> spConcerned = SubscriptionPackage.executeQuery('select sp from SubscriptionPackage sp where sp.pkg = :pkg',[pkg:target.pkg])
                        spConcerned.each { SubscriptionPackage sp ->
                            changeNotificationService.determinePendingChangeBehavior([target:sp.subscription,oid:genericOIDService.getOID(target)],PendingChangeConfiguration.NEW_TITLE,sp)
                        }
                    }
                    else {
                        Set<IssueEntitlement> ieConcerned = IssueEntitlement.executeQuery('select ie from IssueEntitlement ie where ie.tipp = :tipp',[tipp:target])
                        ieConcerned.each { IssueEntitlement ie ->
                            String changeDesc = ""
                            Map<String,Object> changeMap = [target:ie.subscription]
                            switch(notify.event) {
                                case 'update': notify.diffs.each { diff ->
                                    if(diff.prop == 'coverage') {
                                        //the city Coventry is beautiful, isn't it ... but here is the COVerageENTRY meant.
                                        diff.covDiffs.each { covEntry ->
                                            TIPPCoverage tippCov = (TIPPCoverage) covEntry.target
                                            switch(covEntry.event) {
                                                case 'update': IssueEntitlementCoverage ieCov = (IssueEntitlementCoverage) tippCov.findEquivalent(ie.coverages)
                                                    if(ieCov) {
                                                        covEntry.diffs.each { covDiff ->
                                                            changeDesc = PendingChangeConfiguration.COVERAGE_UPDATED
                                                            changeMap.oid = genericOIDService.getOID(ieCov)
                                                            changeMap.prop = covDiff.prop
                                                            changeMap.oldValue = ieCov[covDiff.prop]
                                                            changeMap.newValue = covDiff.newValue
                                                            changeNotificationService.determinePendingChangeBehavior(changeMap,changeDesc,SubscriptionPackage.findBySubscriptionAndPkg(ie.subscription,target.pkg))
                                                        }
                                                    }
                                                    else {
                                                        changeDesc = PendingChangeConfiguration.NEW_COVERAGE
                                                        changeMap.oid = "${tippCov.class.name}:${tippCov.id}"
                                                        changeNotificationService.determinePendingChangeBehavior(changeMap,changeDesc,SubscriptionPackage.findBySubscriptionAndPkg(ie.subscription,target.pkg))
                                                    }
                                                    break
                                                case 'add':
                                                    changeDesc = PendingChangeConfiguration.NEW_COVERAGE
                                                    changeMap.oid = "${tippCov.class.name}:${tippCov.id}"
                                                    changeNotificationService.determinePendingChangeBehavior(changeMap,changeDesc,SubscriptionPackage.findBySubscriptionAndPkg(ie.subscription,target.pkg))
                                                    break
                                                case 'delete':
                                                    IssueEntitlementCoverage ieCov = (IssueEntitlementCoverage) tippCov.findEquivalent(ie.coverages)
                                                    if(ieCov) {
                                                        changeDesc = PendingChangeConfiguration.COVERAGE_DELETED
                                                        changeMap.oid = genericOIDService.getOID(ieCov)
                                                        changeNotificationService.determinePendingChangeBehavior(changeMap,changeDesc,SubscriptionPackage.findBySubscriptionAndPkg(ie.subscription,target.pkg))
                                                    }
                                                    break
                                            }
                                        }
                                    }
                                    else {
                                        changeDesc = PendingChangeConfiguration.TITLE_UPDATED
                                        changeMap.oid = genericOIDService.getOID(ie)
                                        changeMap.prop = diff.prop
                                        if(diff.prop in PendingChange.REFDATA_FIELDS)
                                            changeMap.oldValue = ie[diff.prop].id
                                        else if(diff.prop in ['hostPlatformURL'])
                                            changeMap.oldValue = diff.oldValue
                                        else
                                            changeMap.oldValue = ie[diff.prop]
                                        changeMap.newValue = diff.newValue
                                        changeNotificationService.determinePendingChangeBehavior(changeMap,changeDesc,SubscriptionPackage.findBySubscriptionAndPkg(ie.subscription,target.pkg))
                                    }
                                }
                                    break
                                case 'delete':
                                    changeDesc = PendingChangeConfiguration.TITLE_DELETED
                                    changeMap.oid = genericOIDService.getOID(ie)
                                    changeNotificationService.determinePendingChangeBehavior(changeMap,changeDesc,SubscriptionPackage.findBySubscriptionAndPkg(ie.subscription,target.pkg))
                                    break
                            }
                            //changeNotificationService.registerPendingChange(PendingChange.PROP_SUBSCRIPTION,ie.subscription,ie.subscription.getSubscriber(),changeMap,null,null,changeDesc)
                        }
                    }
                }
                cleanUpGorm()
            }
        }
    }

    /**
     * Retrieves remote data with the given query parameters. Used to query a GOKb instance for changes since a given timestamp or to fetch remote package/provider data
     * Was formerly the OaiClient and OaiClientLaser classes
     *
     * @param url - the URL to query against
     * @param object - the object(s) about which records should be obtained. May be: {@link Package}, {@link TitleInstance} or {@link Org}
     * @param queryParams - parameters to pass along with the query
     * @return a {@link GPathResult} containing the OAI-PMH conform XML extract of the given record
     */
    GPathResult fetchRecord(String url, String object, Map<String,String> queryParams) {
        try {
            HTTPBuilder http = new HTTPBuilder(url)
            GPathResult record = (GPathResult) http.get(path:object,query:queryParams,contentType:'xml') { resp, xml ->
                GPathResult response = new XmlSlurper().parseText(xml.text)
                if(response.error && response.error.@code == 'idDoesNotExist') {
                    log.error(response.error)
                    null
                }
                else if(response[queryParams.verb] && response[queryParams.verb] instanceof GPathResult) {
                    if(response[queryParams.verb])
                        response[queryParams.verb]
                    else null
                }
                else {
                    log.error('Request succeeded but result data invalid. Please do checks!')
                    null
                }
            }
            http.shutdown()
            record
        }
        catch(HttpResponseException e) {
            e.printStackTrace()
            null
        }
    }

    void defineMapFields() {
        //define map fields
        RefdataCategory.getAllRefdataValues(RDConstants.TIPP_STATUS).each { RefdataValue rdv ->
            tippStatus.put(rdv.value,rdv)
        }
        RefdataCategory.getAllRefdataValues(RDConstants.TITLE_STATUS).each { RefdataValue rdv ->
            titleStatus.put(rdv.value,rdv)
        }
        RefdataCategory.getAllRefdataValues(RDConstants.TITLE_MEDIUM).each { RefdataValue rdv ->
            titleMedium.put(rdv.value,rdv)
        }
        RefdataCategory.getAllRefdataValues(RDConstants.PACKAGE_STATUS).each { RefdataValue rdv ->
            packageStatus.put(rdv.value,rdv)
        }
        RefdataCategory.getAllRefdataValues(RDConstants.ORG_STATUS).each { RefdataValue rdv ->
            orgStatus.put(rdv.value,rdv)
        }
    }

    /**
     * Flushes the session data to free up memory. Essential for bulk data operations like record syncing
     */
    void cleanUpGorm() {
        log.debug("Clean up GORM")

        Session session = sessionFactory.currentSession
        session.flush()
        session.clear()
        propertyInstanceMap.get().clear()
    }

}
