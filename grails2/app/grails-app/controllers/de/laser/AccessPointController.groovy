package de.laser


import com.k_int.kbplus.auth.User
import de.laser.controller.AbstractDebugController
import de.laser.helper.DateUtil
import de.laser.helper.RDConstants
import de.laser.helper.RDStore
import de.laser.oap.OrgAccessPoint
import de.laser.oap.OrgAccessPointEzproxy
import de.laser.oap.OrgAccessPointLink
import de.laser.oap.OrgAccessPointOA
import de.laser.oap.OrgAccessPointShibboleth
import de.laser.oap.OrgAccessPointVpn
import de.uni_freiburg.ub.IpRange
import grails.plugin.springsecurity.annotation.Secured
import groovy.json.JsonOutput
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.springframework.dao.DataIntegrityViolationException

import java.text.SimpleDateFormat

class AccessPointController extends AbstractDebugController {

    def springSecurityService
    def contextService

    def subscriptionsQueryService
    def orgTypeService
    AccessService accessService
    AccessPointService accessPointService
    EscapeService escapeService


    static allowedMethods = [create: ['GET', 'POST'], delete: ['GET', 'POST'], dynamicSubscriptionList: ['POST'], dynamicPlatformList: ['POST']]

    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def addIpRange() {
        OrgAccessPoint orgAccessPoint = OrgAccessPoint.get(params.id)
        // need to check if contextOrg == orgAccessPoint.org for ORG_CONSORTIUM? The template has no editable elements
        // in that context (would need to fake a post request), similar for deleteIpRange method.
        List<IpRange> validRanges = []
        List<IpRange> invalidRanges = []
        // allow multiple ip ranges as input (must be separated by comma)
        String[] ipCol = params.ip.replaceAll("\\s+", " ").split(",")
        for (range in ipCol) {
            try {
                // check if given input string is a valid ip range
                IpRange ipRange = IpRange.parseIpRange(range)
                List<AccessPointData> accessPointDataList = AccessPointData.findAllByOrgAccessPoint(orgAccessPoint);

                // so far we know that the input string represents a valid ip range
                // check if the input string is already saved
                boolean isDuplicate = false
                for (accessPointData in accessPointDataList) {
                    if (accessPointData.getInputStr() == ipRange.toInputString()) {
                        isDuplicate = true
                    }
                }
                if (!isDuplicate) {
                    validRanges << ipRange
                }
            } catch (InvalidRangeException) {
                invalidRanges << range
            }
        }

        // persist all valid ranges
        for (ipRange in validRanges) {
            def jsonData = JsonOutput.toJson([
                    inputStr  : ipRange.toInputString(),
                    startValue: ipRange.lowerLimit.toHexString(),
                    endValue  : ipRange.upperLimit.toHexString()]
            )

            AccessPointData accessPointData = new AccessPointData()
            accessPointData.orgAccessPoint = orgAccessPoint
            accessPointData.datatype = 'ip' + ipRange.getIpVersion()
            accessPointData.data = jsonData
            accessPointData.save(flush: true)

            orgAccessPoint.lastUpdated = new Date()
            orgAccessPoint.save(flush: true)
        }

        if (invalidRanges) {
            // return only those input strings to UI which represent a invalid ip range
            flash.error = message(code: 'accessPoint.invalid.ip', args: [invalidRanges.join(' ')])
            redirect controller: 'accessPoint', action: 'edit_' + params.accessMethod, id: params.id, params: [ip: invalidRanges.join(' ')]
        } else {
            redirect controller: 'accessPoint', action: 'edit_' + params.accessMethod, id: params.id, params: [autofocus: true]
        }
    }

    /**
     * Check for existing name in all supported locales and return available suggestions for IP Access Method
     * A simpler solution would be nice
     * TODO move out of controller
     * @return
     */
    private def availableOptions(Org org) {

        def availableLanguageKeys = ['accessPoint.option.remoteAccess', 'accessPoint.option.woRemoteAccess', 'accessPoint.option.vpn']
        def supportedLocales = ['en', 'de']
        Map localizedAccessPointNameSuggestions = [:]
        supportedLocales.each { locale ->
            availableLanguageKeys.each { key ->
                localizedAccessPointNameSuggestions[message(code : key, locale : locale)] = key
            }
        }
        def existingOapIpInstances = OrgAccessPoint.findAllByOrgAndAccessMethod(org, RefdataValue.getByValue('ip'))

        if (existingOapIpInstances) {
            existingOapIpInstances.each { it ->
             if (localizedAccessPointNameSuggestions.keySet().contains(it.name)){
                 if (localizedAccessPointNameSuggestions[it.name]) {
                     availableLanguageKeys.removeAll {languageKey ->
                         languageKey == localizedAccessPointNameSuggestions[it.name]
                     }
                 }
             }
            }
        }
        def resultList = []
        availableLanguageKeys.each { it ->
            resultList.add(["${it}" : "${message(code : it)}"])
        }
        resultList.add(["accessPoint.option.customName" : ''])
        return resultList
    }

    @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
    def dynamicSubscriptionList() {
        OrgAccessPoint orgAccessPoint = OrgAccessPoint.get(params.id)
        List<Long> currentSubIds = orgTypeService.getCurrentSubscriptionIds(orgAccessPoint.org)
        String qry = """
            Select p, sp, s from Platform p
            JOIN p.oapp as oapl
            JOIN oapl.subPkg as sp
            JOIN sp.subscription as s
            WHERE oapl.active=true and oapl.oap=${orgAccessPoint.id}
            AND s.id in (:currentSubIds)
            AND EXISTS (SELECT 1 FROM OrgAccessPointLink ioapl 
                where ioapl.subPkg=oapl.subPkg and ioapl.platform=p and ioapl.oap is null)
"""
        if (params.checked == "true"){
            qry += " AND s.status = ${RDStore.SUBSCRIPTION_CURRENT.id}"
        }

        ArrayList linkedPlatformSubscriptionPackages = Platform.executeQuery(qry, [currentSubIds: currentSubIds])
        return render(template: "linked_subs_table", model: [linkedPlatformSubscriptionPackages: linkedPlatformSubscriptionPackages, params:params])
    }

    @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
    def dynamicPlatformList() {
        OrgAccessPoint orgAccessPoint = OrgAccessPoint.get(params.id)
        List<Long> currentSubIds = orgTypeService.getCurrentSubscriptionIds(orgAccessPoint.org)

        String sort = params.sort ?: "LOWER(p.name)"
        String order = params.order ?: "ASC"
        String qry1 = "select new map(p as platform,oapl as aplink) from Platform p join p.oapp as oapl where oapl.active = true and oapl.oap=${orgAccessPoint.id} and oapl.subPkg is null order by ${sort} ${order}"

        List<HashMap> linkedPlatforms = Platform.executeQuery(qry1)
        linkedPlatforms.each() {
            String qry2 = """
            SELECT distinct s from Subscription s
            JOIN s.packages as sp
            JOIN sp.pkg as pkg
            JOIN pkg.tipps as tipps
            WHERE s.id in (:currentSubIds)
            AND tipps.platform.id = ${it.platform.id}
            AND NOT EXISTS 
            (SELECT ioapl from OrgAccessPointLink ioapl
                WHERE ioapl.active=true and ioapl.subPkg=sp and ioapl.oap is null)
"""
            if (params.checked == "true"){
                qry2 += " AND s.status = ${RDStore.SUBSCRIPTION_CURRENT.id}"
            }
            ArrayList linkedSubs = Subscription.executeQuery(qry2, [currentSubIds: currentSubIds])
            it['linkedSubs'] = linkedSubs
        }
        return render(template: "linked_platforms_table",
            model: [linkedPlatforms: linkedPlatforms, params:params, accessPoint: orgAccessPoint])
    }

    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def create() {
        Map<String, Object> result = [:]
        result.user = User.get(springSecurityService.principal.id)
        Org organisation = accessService.checkPerm("ORG_CONSORTIUM") ? Org.get(params.id) : contextService.getOrg()
        result.orgInstance = organisation
        result.inContextOrg = result.orgInstance.id == contextService.org.id
        result.availableOptions = availableOptions()

        if (params.template) {
            RefdataValue accessMethod = RefdataValue.getByValueAndCategory(params.template, RDConstants.ACCESS_POINT_TYPE)
            return render(template: 'create_' + accessMethod, model: [accessMethod: accessMethod, availableOptions : result.availableOptions])
        } else {
            result.accessMethod = params.accessMethod ? RefdataValue.getByValueAndCategory(params.accessMethod, RDConstants.ACCESS_POINT_TYPE).value : RDStore.ACCESS_POINT_TYPE_IP.value
            result
        }

    }

    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def create_ip() {
        // without the org somehow passed we can only create AccessPoints for the context org
        Org orgInstance = accessService.checkPerm("ORG_CONSORTIUM") ? Org.get(params.id) : contextService.getOrg()
        List<OrgAccessPoint> oap = OrgAccessPoint.findAllByNameAndOrg(params.name, orgInstance)

        params.accessMethod = RDStore.ACCESS_POINT_TYPE_IP.value

        if (! params.name) {
            flash.error = message(code: 'accessPoint.require.name', args: [params.name])
            redirect(controller: "accessPoint", action: "create", params: params)
            return
        }

        if (oap) {
            flash.error = message(code: 'accessPoint.duplicate.error', args: [params.name])
            redirect(controller: "accessPoint", action: "create", params: params)
        } else {
            OrgAccessPoint accessPoint = new OrgAccessPoint()
            accessPoint.org = orgInstance
            accessPoint.name = params.name
            accessPoint.accessMethod = RDStore.ACCESS_POINT_TYPE_IP
            accessPoint.save(flush: true)

            //flash.message = message(code: 'accessPoint.create.message', args: [accessPoint.name])
            redirect controller: 'accessPoint', action: 'edit_'+accessPoint.accessMethod.value, id: accessPoint.id
        }
    }

    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def create_oa() {
        // without the org somehow passed we can only create AccessPoints for the context org
        Org orgInstance = accessService.checkPerm("ORG_CONSORTIUM") ? Org.get(params.id) : contextService.getOrg()
        List<OrgAccessPoint> oap = OrgAccessPoint.findAllByNameAndOrg(params.name, orgInstance)

        params.accessMethod = RDStore.ACCESS_POINT_TYPE_OA.value

        if (! params.name) {
            flash.error = message(code: 'accessPoint.require.name', args: [params.name])
            redirect(controller: "accessPoint", action: "create", params: params)
            return
        }

        if (! params.entityId) {
            flash.error = message(code: 'accessPoint.require.entityId')
            redirect(controller: "accessPoint", action: "create", params: params)
            return
        }

        if (oap) {
            flash.error = message(code: 'accessPoint.duplicate.error', args: [params.name])
            redirect(controller: "accessPoint", action: "create", params: params)
        } else {
            OrgAccessPointOA accessPoint = new OrgAccessPointOA()
            accessPoint.org = orgInstance
            accessPoint.name = params.name
            accessPoint.accessMethod = RDStore.ACCESS_POINT_TYPE_OA
            accessPoint.entityId = params.entityId
            accessPoint.save(flush: true)

            //flash.message = message(code: 'accessPoint.create.message', args: [accessPoint.name])
            redirect controller: 'accessPoint', action: 'edit_'+accessPoint.accessMethod.value, id: accessPoint.id
        }
    }

    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def create_proxy() {
        // without the org somehow passed we can only create AccessPoints for the context org
        Org orgInstance = accessService.checkPerm("ORG_CONSORTIUM") ? Org.get(params.id) : contextService.getOrg()
        List<OrgAccessPoint> oap = OrgAccessPoint.findAllByNameAndOrg(params.name, orgInstance)

        params.accessMethod = RDStore.ACCESS_POINT_TYPE_PROXY.value

        if (! params.name) {
            flash.error = message(code: 'accessPoint.require.name', args: [params.name])
            redirect(controller: "accessPoint", action: "create", params: params)
            return
        }

        if (oap) {
            flash.error = message(code: 'accessPoint.duplicate.error', args: [params.name])
            redirect(controller: "accessPoint", action: "create", params: params)
        } else {
            OrgAccessPoint accessPoint = new OrgAccessPoint()
            accessPoint.org = orgInstance
            accessPoint.name = params.name
            accessPoint.accessMethod = RDStore.ACCESS_POINT_TYPE_PROXY
            accessPoint.save(flush: true)

            //flash.message = message(code: 'accessPoint.create.message', args: [accessPoint.name])
            redirect controller: 'accessPoint', action: 'edit_'+accessPoint.accessMethod.value, id: accessPoint.id
        }
    }

    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def create_vpn() {
        // without the org somehow passed we can only create AccessPoints for the context org
        Org orgInstance = accessService.checkPerm("ORG_CONSORTIUM") ? Org.get(params.id) : contextService.getOrg()

        params.accessMethod = RDStore.ACCESS_POINT_TYPE_VPN.value

        if (! params.name) {
            flash.error = message(code: 'accessPoint.require.name', args: [params.name])
            redirect(controller: "accessPoint", action: "create", params: params)
            return
        }

        List<OrgAccessPoint> oap = OrgAccessPoint.findAllByNameAndOrg(params.name, orgInstance)
        if (oap) {
            flash.error = message(code: 'accessPoint.duplicate.error', args: [params.name])
            redirect(controller: "accessPoint", action: "create", params: params)
        } else {
            OrgAccessPointVpn accessPoint = new OrgAccessPointVpn()
            accessPoint.org = orgInstance
            accessPoint.name = params.name
            accessPoint.accessMethod = RDStore.ACCESS_POINT_TYPE_VPN
            accessPoint.save(flush: true)

            //flash.message = message(code: 'accessPoint.create.message', args: [accessPoint.name])
            redirect controller: 'accessPoint', action: 'edit_'+accessPoint.accessMethod.value, id: accessPoint.id
        }
    }

    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def create_ezproxy() {
        // without the org somehow passed we can only create AccessPoints for the context org
        Org orgInstance = accessService.checkPerm("ORG_CONSORTIUM") ? Org.get(params.id) : contextService.getOrg()

        params.accessMethod = RDStore.ACCESS_POINT_TYPE_EZPROXY.value

        if (! params.name) {
            flash.error = message(code: 'accessPoint.require.name', args: [params.name])
            redirect(controller: "accessPoint", action: "create", params: params)
            return
        }

        if (! params.url) {
            flash.error = message(code: 'accessPoint.require.url', args: [params.name])
            redirect(controller: "accessPoint", action: "create", params: params)
            return
        }

        List<OrgAccessPoint> oap = OrgAccessPoint.findAllByNameAndOrg(params.name, orgInstance)
        if (oap) {
            flash.error = message(code: 'accessPoint.duplicate.error', args: [params.name])
            redirect(controller: "accessPoint", action: "create", params: params)
        } else {
            OrgAccessPointEzproxy accessPoint = new OrgAccessPointEzproxy()
            accessPoint.org = orgInstance
            accessPoint.name = params.name
            accessPoint.url = params.url
            accessPoint.accessMethod = RDStore.ACCESS_POINT_TYPE_EZPROXY
            accessPoint.save(flush: true)

            //flash.message = message(code: 'accessPoint.create.message', args: [accessPoint.name])
            redirect controller: 'accessPoint', action: 'edit_'+accessPoint.accessMethod.value, id: accessPoint.id
        }
    }

    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def create_shibboleth() {
        // without the org somehow passed we can only create AccessPoints for the context org
        Org orgInstance = accessService.checkPerm("ORG_CONSORTIUM") ? Org.get(params.id) : contextService.getOrg()

        params.accessMethod = RDStore.ACCESS_POINT_TYPE_SHIBBOLETH.value

        if (! params.name) {
            flash.error = message(code: 'accessPoint.require.name')
            redirect(controller: "accessPoint", action: "create", params: params)
            return
        }

        if (! params.entityId) {
            flash.error = message(code: 'accessPoint.require.entityId')
            redirect(controller: "accessPoint", action: "create", params: params)
            return
        }

        List<OrgAccessPoint> oap = OrgAccessPoint.findAllByNameAndOrg(params.name, orgInstance)
        if (oap) {
            flash.error = message(code: 'accessPoint.duplicate.error', args: [params.name])
            redirect(controller: "accessPoint", action: "create", params: params)
        } else {
            OrgAccessPointShibboleth accessPoint = new OrgAccessPointShibboleth()
            accessPoint.org = orgInstance
            accessPoint.name = params.name
            accessPoint.entityId = params.entityId
            accessPoint.accessMethod = RDStore.ACCESS_POINT_TYPE_SHIBBOLETH
            accessPoint.save(flush: true)

            //flash.message = message(code: 'accessPoint.create.message', args: [accessPoint.name])
            redirect controller: 'accessPoint', action: 'edit_'+accessPoint.accessMethod.value, id: accessPoint.id
        }
    }

    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def delete() {
        OrgAccessPoint accessPoint = OrgAccessPoint.get(params.id)
        if (!accessPoint) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'accessMethod.label'), params.id])
            redirect(url: request.getHeader("referer"))
            return
        }

        Org org = accessPoint.org;
        boolean inContextOrg = (org.id == contextService.org.id)

        if(((accessService.checkPermAffiliation('ORG_BASIC_MEMBER', 'INST_EDITOR') && inContextOrg) || (accessService.checkPermAffiliation('ORG_CONSORTIUM', 'INST_EDITOR')))) {
            Long oapPlatformLinkCount = OrgAccessPointLink.countByActiveAndOapAndSubPkgIsNull(true, accessPoint)
            Long oapSubscriptionLinkCount = OrgAccessPointLink.countByActiveAndOapAndSubPkgIsNotNull(true, accessPoint)

            if (oapPlatformLinkCount != 0 || oapSubscriptionLinkCount != 0) {
                flash.message = message(code: 'accessPoint.list.deleteDisabledInfo', args: [oapPlatformLinkCount, oapSubscriptionLinkCount])
                redirect(url: request.getHeader("referer"))
                return
            }
            Long orgId = org.id

            try {
                accessPoint.delete(flush: true)
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'accessPoint.label'), accessPoint.name])
                redirect controller: 'organisation', action: 'accessPoints', id: orgId
            }
            catch (DataIntegrityViolationException e) {
                flash.message = message(code: 'default.not.deleted.message', args: [message(code: 'address.label'), accessPoint.id])
                redirect action: 'show', id: params.id
            }
        }else {
                response.sendError(401)
                return
        }
    }

    @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
    def edit_ip() {
        OrgAccessPoint orgAccessPoint = OrgAccessPoint.get(params.id)
        Org org = orgAccessPoint.org
        Long orgId = org.id
        Org contextOrg = contextService.org
        boolean inContextOrg = (orgId == contextOrg.id)

        if (params.exportXLSX) {
            SXSSFWorkbook wb
            SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
            String datetoday = sdf.format(new Date(System.currentTimeMillis()))
            String filename = "${datetoday}_" + escapeService.escapeString(orgAccessPoint.name)
            response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            wb = (SXSSFWorkbook) accessPointService.exportAccessPoints([orgAccessPoint], contextService.org)
            wb.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            wb.dispose()
            return
        }else {

            Boolean autofocus = (params.autofocus) ? true : false
            Boolean activeChecksOnly = (params.checked == 'false') ? false : true

            Map<String, Object> accessPointDataList = orgAccessPoint.getAccessPointIpRanges()

            orgAccessPoint.getAllRefdataValues(RDConstants.IPV6_ADDRESS_FORMAT)

            List<Long> currentSubIds = orgTypeService.getCurrentSubscriptionIds(orgAccessPoint.org)

            String sort = params.sort ?: "LOWER(p.name)"
            String order = params.order ?: "ASC"
            String qry1 = "select new map(p as platform,oapl as aplink) from Platform p join p.oapp as oapl where oapl.active = true and oapl.oap=${orgAccessPoint.id} and oapl.subPkg is null order by ${sort} ${order}"

            ArrayList<HashMap> linkedPlatforms = Platform.executeQuery(qry1)
            linkedPlatforms.each() {
                String qry2 = """
            SELECT distinct s from Subscription s
            JOIN s.packages as sp
            JOIN sp.pkg as pkg
            JOIN pkg.tipps as tipps
            WHERE s.id in (:currentSubIds)
            AND tipps.platform.id = ${it.platform.id}
            AND NOT EXISTS 
            (SELECT ioapl from OrgAccessPointLink ioapl
                WHERE ioapl.active=true and ioapl.subPkg=sp and ioapl.oap is null)
"""
                if (activeChecksOnly) {
                    qry2 += " AND s.status = ${RDStore.SUBSCRIPTION_CURRENT.id}"
                }
                ArrayList linkedSubs = Subscription.executeQuery(qry2, [currentSubIds: currentSubIds])
                it['linkedSubs'] = linkedSubs
            }

            String qry3 = """
            Select p, sp, s from Platform p
            JOIN p.oapp as oapl
            JOIN oapl.subPkg as sp
            JOIN sp.subscription as s
            WHERE oapl.active=true and oapl.oap=${orgAccessPoint.id}
            AND s.id in (:currentSubIds) 
            AND EXISTS (SELECT 1 FROM OrgAccessPointLink ioapl 
                where ioapl.subPkg=oapl.subPkg and ioapl.platform=p and ioapl.oap is null)
            AND s.status = ${RDStore.SUBSCRIPTION_CURRENT.id}    
"""
            ArrayList linkedPlatformSubscriptionPackages = Platform.executeQuery(qry3, [currentSubIds: currentSubIds])

            return [
                    accessPoint                       : orgAccessPoint,
                    accessPointDataList               : accessPointDataList,
                    orgId                             : orgId,
                    platformList                      : orgAccessPoint.getNotLinkedPlatforms(),
                    linkedPlatforms                   : linkedPlatforms,
                    linkedPlatformSubscriptionPackages: linkedPlatformSubscriptionPackages,
                    ip                                : params.ip,
                    editable                          : ((accessService.checkPermAffiliation('ORG_BASIC_MEMBER', 'INST_EDITOR') && inContextOrg) || (accessService.checkPermAffiliation('ORG_CONSORTIUM', 'INST_EDITOR'))),
                    autofocus                         : autofocus,
                    orgInstance                       : orgAccessPoint.org,
                    inContextOrg                      : inContextOrg,
                    activeSubsOnly                    : activeChecksOnly,
                    institution                       : contextOrg
            ]
        }
    }

    @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
    def edit_vpn() {
        _edit()
    }

    @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
    def edit_oa() {
        _edit()
    }

    @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
    def edit_proxy() {
        _edit()
    }

    @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
    def edit_ezproxy() {
        _edit()
    }

    @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
    def edit_shibboleth() {
        _edit()
    }

    def private _edit() {
        OrgAccessPoint orgAccessPoint = OrgAccessPoint.get(params.id)
        Org org = orgAccessPoint.org
        Long orgId = org.id
        Org contextOrg = contextService.org
        boolean inContextOrg = (orgId == contextOrg.id)

        if (params.exportXLSX) {
            SXSSFWorkbook wb
            SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
            String datetoday = sdf.format(new Date(System.currentTimeMillis()))
            String filename = "${datetoday}_" + escapeService.escapeString(orgAccessPoint.name)
            response.setHeader "Content-disposition", "attachment; filename=\"${filename}.xlsx\""
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            wb = (SXSSFWorkbook) accessPointService.exportAccessPoints([orgAccessPoint], contextService.org)
            wb.write(response.outputStream)
            response.outputStream.flush()
            response.outputStream.close()
            wb.dispose()
            return
        }else {

            Boolean autofocus = (params.autofocus) ? true : false
            Boolean activeChecksOnly = (params.checked == 'false') ? false : true

            Map<String, Object> accessPointDataList = orgAccessPoint.getAccessPointIpRanges()


            orgAccessPoint.getAllRefdataValues(RDConstants.IPV6_ADDRESS_FORMAT)

            List<Long> currentSubIds = orgTypeService.getCurrentSubscriptionIds(orgAccessPoint.org)

            String sort = params.sort ?: "LOWER(p.name)"
            String order = params.order ?: "ASC"
            String qry1 = "select new map(p as platform,oapl as aplink) from Platform p join p.oapp as oapl where oapl.active = true and oapl.oap=${orgAccessPoint.id} and oapl.subPkg is null order by ${sort} ${order}"

            ArrayList<HashMap> linkedPlatforms = Platform.executeQuery(qry1)
            linkedPlatforms.each() {
                String qry2 = """
            SELECT distinct s from Subscription s
            JOIN s.packages as sp
            JOIN sp.pkg as pkg
            JOIN pkg.tipps as tipps
            WHERE s.id in (:currentSubIds)
            AND tipps.platform.id = ${it.platform.id}
            AND NOT EXISTS 
            (SELECT ioapl from OrgAccessPointLink ioapl
                WHERE ioapl.active=true and ioapl.subPkg=sp and ioapl.oap is null)
"""
                if (activeChecksOnly) {
                    qry2 += " AND s.status = ${RDStore.SUBSCRIPTION_CURRENT.id}"
                }
                ArrayList linkedSubs = Subscription.executeQuery(qry2, [currentSubIds: currentSubIds])
                it['linkedSubs'] = linkedSubs
            }

            String qry3 = """
            Select p, sp, s from Platform p
            JOIN p.oapp as oapl
            JOIN oapl.subPkg as sp
            JOIN sp.subscription as s
            WHERE oapl.active=true and oapl.oap=${orgAccessPoint.id}
            AND s.id in (:currentSubIds) 
            AND EXISTS (SELECT 1 FROM OrgAccessPointLink ioapl 
                where ioapl.subPkg=oapl.subPkg and ioapl.platform=p and ioapl.oap is null)
            AND s.status = ${RDStore.SUBSCRIPTION_CURRENT.id}    
"""
            ArrayList linkedPlatformSubscriptionPackages = Platform.executeQuery(qry3, [currentSubIds: currentSubIds])

            return [
                    accessPoint                       : orgAccessPoint,
                    accessPointDataList               : accessPointDataList,
                    rgId                              : orgId,
                    platformList                      : orgAccessPoint.getNotLinkedPlatforms(),
                    linkedPlatforms                   : linkedPlatforms,
                    linkedPlatformSubscriptionPackages: linkedPlatformSubscriptionPackages,
                    ip                                : params.ip,
                    editable                          : ((accessService.checkPermAffiliation('ORG_BASIC_MEMBER', 'INST_EDITOR') && inContextOrg) || (accessService.checkPermAffiliation('ORG_CONSORTIUM', 'INST_EDITOR'))),
                    autofocus                         : autofocus,
                    orgInstance                       : orgAccessPoint.org,
                    inContextOrg                      : inContextOrg,
                    activeSubsOnly                    : activeChecksOnly,
                    institution                       : contextOrg
            ]
        }
    }

    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def deleteIpRange() {
        AccessPointData accessPointData = AccessPointData.get(params.id)
        accessPointData.delete(flush: true)

        redirect(url: request.getHeader('referer'))
        //redirect controller: 'accessPoint', action: 'edit_'+params.accessMethod, id: accessPoint.id, params: [autofocus: true]
    }

    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def linkPlatform() {
        OrgAccessPoint accessPoint = OrgAccessPoint.get(params.accessPointId)
        OrgAccessPointLink oapl = new OrgAccessPointLink()
        oapl.active = true
        oapl.oap = accessPoint
        if (params.platforms) {
            oapl.platform = Platform.get(params.platforms)
            String hql = "select oap from OrgAccessPoint oap " +
                "join oap.oapp as oapl where oapl.active = true and oapl.platform.id =${accessPoint.id} and oapl.oap=:oap and oapl.subPkg is null order by LOWER(oap.name)"
            def existingActiveAP = OrgAccessPoint.executeQuery(hql, ['oap' : accessPoint])

            if (! existingActiveAP.isEmpty()){
                flash.error = "Existing active AccessPoint for platform"
                redirect(url: request.getHeader('referer'))
                return
            }
            if (! oapl.save(flush:true)) {
                flash.error = "Could not link AccessPoint to Platform"
            }
        }
        redirect(url: request.getHeader('referer'))
        //redirect controller: 'accessPoint', action: 'edit_ip', id: accessPoint.id, params: [autofocus: true]
    }

    @Secured(closure = {
        ctx.accessService.checkPermAffiliation("ORG_BASIC_MEMBER,ORG_CONSORTIUM", "INST_EDITOR")
    })
    def unlinkPlatform() {
        OrgAccessPointLink aoplInstance = OrgAccessPointLink.get(params.id)
        aoplInstance.active = false
        if (! aoplInstance.save(flush:true)) {
            log.debug("Error updateing AccessPoint for platform")
            log.debug(aopl.errors.toString())
            // TODO flash
        }
        redirect(url: request.getHeader('referer'))
        //redirect controller: 'accessPoint', action: 'edit_ip', id: aoplInstance.oap.id, params: [autofocus: true]
    }
}
