package de.laser.ajax


import com.k_int.kbplus.auth.Role
import com.k_int.kbplus.auth.User
import com.k_int.kbplus.auth.UserRole
import de.laser.*
import de.laser.base.AbstractI10n
import de.laser.base.AbstractPropertyWithCalculatedLastUpdated
import de.laser.helper.*
import de.laser.interfaces.ShareSupport
import de.laser.properties.PropertyDefinition
import de.laser.properties.PropertyDefinitionGroup
import de.laser.properties.PropertyDefinitionGroupBinding
import de.laser.system.SystemProfiler
import de.laser.traits.I10nTrait
import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import org.codehaus.groovy.grails.commons.GrailsClass
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.support.RequestContextUtils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.regex.Matcher
import java.util.regex.Pattern

//import org.grails.orm.hibernate.cfg.GrailsHibernateUtil

@Secured(['permitAll']) // TODO
class AjaxController {

    def genericOIDService
    def subscriptionService
    def contextService
    def accessService
    def escapeService
    def formService
    def dashboardDueDatesService
    CompareService compareService
    LinksGenerationService linksGenerationService
    LicenseService licenseService

    def refdata_config = [
    "ContentProvider" : [
      domain:'Org',
      countQry:"select count(o) from Org as o where exists (select roletype from o.orgType as roletype where roletype.value = 'Provider' ) and lower(o.name) like ? and (o.status is null or o.status != ?)",
      rowQry:"select o from Org as o where exists (select roletype from o.orgType as roletype where roletype.value = 'Provider' ) and lower(o.name) like ? and (o.status is null or o.status != ?) order by o.name asc",
      qryParams:[
              [
                param:'sSearch',
                clos:{ value ->
                    String result = '%'
                    if ( value && ( value.length() > 0 ) )
                        result = "%${value.trim().toLowerCase()}%"
                    result
                }
              ]
      ],
      cols:['name'],
      format:'map'
    ],
    "Licenses" : [
      domain:'License',
      countQry:"select count(l) from License as l",
      rowQry:"select l from License as l",
      qryParams:[],
      cols:['reference'],
      format:'simple'
    ],
    'Currency' : [
      domain:'RefdataValue',
      countQry:"select count(rdv) from RefdataValue as rdv where rdv.owner.desc='${RDConstants.CURRENCY}'",
      rowQry:"select rdv from RefdataValue as rdv where rdv.owner.desc='${RDConstants.CURRENCY}'",
      qryParams:[
                   [
                      param:'iDisplayLength',
                      value: 200
                   ]
      ],
      cols:['value'],
      format:'simple'
    ],
    "allOrgs" : [
            domain:'Org',
            countQry:"select count(o) from Org as o where lower(o.name) like ? and (o.status is null or o.status != ?)",
            rowQry:"select o from Org as o where lower(o.name) like ? and (o.status is null or o.status != ?) order by o.name asc",
            qryParams:[
                    [
                            param:'sSearch',
                            clos:{ value ->
                                String result = '%'
                                if ( value && ( value.length() > 0 ) )
                                    result = "%${value.trim().toLowerCase()}%"
                                result
                            }
                    ]
            ],
            cols:['name'],
            format:'map'
    ],
    "CommercialOrgs" : [
            domain:'Org',
            countQry:"select count(o) from Org as o where (o.sector.value = 'Publisher') and lower(o.name) like ? and (o.status is null or o.status != ?)",
            rowQry:"select o from Org as o where (o.sector.value = 'Publisher') and lower(o.name) like ? and (o.status is null or o.status != ?) order by o.name asc",
            qryParams:[
                    [
                            param:'sSearch',
                            clos:{ value ->
                                String result = '%'
                                if ( value && ( value.length() > 0 ) )
                                    result = "%${value.trim().toLowerCase()}%"
                                result
                            }
                    ]
            ],
            cols:['name'],
            format:'map'
    ]
  ]

    def test() {
        render 'test()'
    }

    def genericDialogMessage() {

        if (params.template) {
            render template: "/templates/ajax/${params.template}", model: [a: 1, b: 2, c: 3]
        }
        else {
            render '<p>invalid call</p>'
        }
    }

    def updateSessionCache() {
        if (contextService.getUser()) {
            SessionCacheWrapper cache = contextService.getSessionCache()

            if (params.key == UserSetting.KEYS.SHOW_EXTENDED_FILTER.toString()) {

                if (params.uri) {
                    cache.put("${params.key}/${params.uri}", params.value)
                    log.debug("update session based user setting: [${params.key}/${params.uri} -> ${params.value}]")
                }
            }
        }

        if (params.redirect) {
            redirect(url: request.getHeader('referer'))
        }
        Map<String, Object> result = [:]
        render result as JSON
    }

    @Secured(['ROLE_USER'])
    def genericSetRel() {
        String result = ''

        try {
            String[] target_components = params.pk.split(":")
            def target = genericOIDService.resolveOID(params.pk)

            if ( target ) {
                if ( params.value == '' ) {
                    // Allow user to set a rel to null be calling set rel ''
                    target[params.name] = null
                    if ( ! target.save(flush: true)){
                        Map r = [status:"error", msg: message(code: 'default.save.error.general.message')]
                        render r as JSON
                        return
                    }
                }
                else {
                    String[] value_components = params.value.split(":")
                    def value = genericOIDService.resolveOID(params.value)

                    if ( target && value ) {
                        if (target instanceof UserSetting) {
                            target.setValue(value)
                        }
                        else {
                            def binding_properties = ["${params.name}": value]
                            bindData(target, binding_properties)
                        }

                        if ( ! target.save(flush: true)){
                            Map r = [status:"error", msg: message(code: 'default.save.error.general.message')]
                            render r as JSON
                            return
                        }
                        if (target instanceof SurveyResult) {
                            Org org = contextService.getOrg()

                            //If Survey Owner set Value then set FinishDate
                            if (org?.id == target.owner?.id && target.finishDate == null) {
                                String property = ""
                                if (target.type.isIntegerType()) {
                                    property = "intValue"
                                } else if (target.type.isStringType()) {
                                    property = "stringValue"
                                } else if (target.type.isBigDecimalType()) {
                                    property = "decValue"
                                } else if (target.type.isDateType()) {
                                    property = "dateValue"
                                } else if (target.type.isURLType()) {
                                    property = "urlValue"
                                } else if (target.type.isRefdataValueType()) {
                                    property = "refValue"
                                }

                                if (target[property] != null) {
                                    log.debug("Set/Save FinishDate of SurveyResult (${target.id})")
                                    target.finishDate = new Date()
                                    target.save(flush: true)
                                }
                            }
                        }

                        // We should clear the session values for a user if this is a user to force reload of the parameters.
                        if (target instanceof User) {
                            session.userPereferences = null
                        }

                        if (target instanceof UserSetting) {
                            if (target.key.toString() == 'LANGUAGE') {
                                Locale newLocale = new Locale(value.value, value.value.toUpperCase())
                                log.debug("UserSetting: LANGUAGE changed to: " + newLocale)

                                LocaleResolver localeResolver = RequestContextUtils.getLocaleResolver(request)
                                localeResolver.setLocale(request, response, newLocale)
                            }
                        }

                        if ( params.resultProp ) {
                            result = value[params.resultProp]
                        }
                        else {
                            if ( value ) {
                                result = renderObjectValue(value)
                            }
                        }
                    }
                    else {
                        log.debug("no value (target=${target_components}, value=${value_components}");
                    }
                }
            }
            else {
                log.error("no target (target=${target_components}");
            }

        } catch (Exception e) {
            log.error("@ genericSetRel()")
            log.error( e.toString() )
        }

        def resp = [ newValue: result ]
        log.debug("genericSetRel() returns ${resp as JSON}")
        render resp as JSON
    }

  @Deprecated
  def refdataSearch() {
      // TODO: refactoring - only used by /templates/_orgLinksModal.gsp

    //log.debug("refdataSearch params: ${params}");
    
    Map<String, Object> result = [:]
    //we call toString in case we got a GString
    def config = refdata_config.get(params.id?.toString())

    if ( config == null ) {
        String locale = I10nTranslation.decodeLocale(LocaleContextHolder.getLocale())
        // If we werent able to locate a specific config override, assume the ID is just a refdata key
      config = [
        domain:'RefdataValue',
        countQry:"select count(rdv) from RefdataValue as rdv where rdv.owner.desc='${params.id}'",
        rowQry:"select rdv from RefdataValue as rdv where rdv.owner.desc='${params.id}' order by rdv.order, rdv.value_" + locale,
        qryParams:[],
        cols:['value'],
        format:'simple'
      ]
    }

    if ( config ) {

      // result.config = config

      def query_params = []
      config.qryParams.each { qp ->
        log.debug("Processing query param ${qp} value will be ${params[qp.param]}");
        if ( qp.clos ) {
          query_params.add(qp.clos(params[qp.param]?:''));
        }
        else {
          query_params.add(params[qp.param]);
        }
      }

        if (config.domain == 'Org') {
            // new added param for org queries in this->refdata_config
            query_params.add(RefdataValue.getByValueAndCategory('Deleted', RDConstants.ORG_STATUS))
        }

        //log.debug("Row qry: ${config.rowQry}");
        //log.debug("Params: ${query_params}");
        //log.debug("Count qry: ${config.countQry}");

      def cq = Org.executeQuery(config.countQry,query_params);    

      def rq = Org.executeQuery(config.rowQry,
                                query_params,
                                [max:params.iDisplayLength?:1000,offset:params.iDisplayStart?:0]);

      if ( config.format=='map' ) {
        result.aaData = []
        result.sEcho = params.sEcho
        result.iTotalRecords = cq[0]
        result.iTotalDisplayRecords = cq[0]
    
        rq.each { it ->
          def rowobj = GrailsHibernateUtil.unwrapIfProxy(it)
          int ctr = 0;
          def row = [:]
          config.cols.each { cd ->
            // log.debug("Processing result col ${cd} pos ${ctr}");
            row["${ctr++}"] = rowobj[cd]
          }
          row["DT_RowId"] = "${rowobj.class.name}:${rowobj.id}"
          result.aaData.add(row)
        }
      }
      else {
        rq.each { it ->
          def rowobj = GrailsHibernateUtil.unwrapIfProxy(it)
          result["${rowobj.class.name}:${rowobj.id}"] = rowobj[config.cols[0]];
        }
      }
    }

    // log.debug("refdataSearch returning ${result as JSON}");
    withFormat {
      html {
        result
      }
      json {
        render result as JSON
      }
    }
  }

    def sel2RefdataSearch() {

        log.debug("sel2RefdataSearch params: ${params}");
    
        List result = []
        Map<String, Object> config = refdata_config.get(params.id?.toString()) //we call toString in case we got a GString
        boolean defaultOrder = true

        if (config == null) {
            String locale = I10nTranslation.decodeLocale(LocaleContextHolder.getLocale())
            defaultOrder = false
            // If we werent able to locate a specific config override, assume the ID is just a refdata key
            config = [
                domain      :'RefdataValue',
                countQry    :"select count(rdv) from RefdataValue as rdv where rdv.owner.desc='" + params.id + "'",
                rowQry      :"select rdv from RefdataValue as rdv where rdv.owner.desc='" + params.id + "' order by rdv.order asc, rdv.value_" + locale,
                qryParams   :[],
                cols        :['value'],
                format      :'simple'
            ]
        }

    if ( config ) {

      List query_params = []
      config.qryParams.each { qp ->
        if ( qp?.clos) {
          query_params.add(qp.clos(params[qp.param]?:''));
        }
        else if(qp?.value) {
            params."${qp.param}" = qp?.value
        }
        else {
          query_params.add(params[qp.param]);
        }
      }

      def cq = RefdataValue.executeQuery(config.countQry,query_params);
      def rq = RefdataValue.executeQuery(config.rowQry,
                                query_params,
                                [max:params.iDisplayLength?:1000,offset:params.iDisplayStart?:0]);

      rq.each { it ->
        def rowobj = GrailsHibernateUtil.unwrapIfProxy(it)

          // handle custom constraint(s) ..
          if (it.value.equalsIgnoreCase('deleted') && params.constraint?.contains('removeValue_deleted')) {
              log.debug('ignored value "' + it + '" from result because of constraint: '+ params.constraint)
          }
          if (it.value.equalsIgnoreCase('administrative subscription') && params.constraint?.contains('removeValue_administrativeSubscription')) {
              log.debug('ignored value "' + it + '" from result because of constraint: '+ params.constraint)
          }
          //value is correct incorrectly translated!
          if (it.value.equalsIgnoreCase('local licence') && accessService.checkPerm("ORG_CONSORTIUM") && params.constraint?.contains('removeValue_localSubscription')) {
              log.debug('ignored value "' + it + '" from result because of constraint: '+ params.constraint)
          }
          // default ..
          else {
              if (it instanceof I10nTrait) {
                  result.add([value: "${rowobj.class.name}:${rowobj.id}", text: "${it.getI10n(config.cols[0])}"])
              }
              else if (it instanceof AbstractI10n) {
                  result.add([value: "${rowobj.class.name}:${rowobj.id}", text: "${it.getI10n(config.cols[0])}"])
              }
              else {
                  def objTest = rowobj[config.cols[0]]
                  if (objTest) {
                      def no_ws = objTest.replaceAll(' ', '');
                      def local_text = message(code: "refdata.${no_ws}", default: "${objTest}");
                      result.add([value: "${rowobj.class.name}:${rowobj.id}", text: "${local_text}"])
                  }
              }
          }
      }
    }
    else {
      log.error("No config for refdata search ${params.id}");
    }

      if (result && defaultOrder) {
          result.sort{ x,y -> x.text.compareToIgnoreCase y.text  }
      }

    withFormat {
      html {
        result
      }
      json {
        render result as JSON
      }
    }
  }

  @Secured(['ROLE_USER'])
  def updateChecked() {
      Map success = [success:false]
      EhcacheWrapper cache = contextService.getCache("/subscription/${params.referer}/${params.sub}", contextService.USER_SCOPE)
      Map checked = cache.get('checked')
      if(params.index == 'all') {
		  def newChecked = [:]
		  checked.eachWithIndex { e, int idx ->
			  newChecked[e.key] = params.checked == 'true' ? 'checked' : null
			  cache.put('checked',newChecked)
		  }
	  }
	  else {
		  checked[params.index] = params.checked == 'true' ? 'checked' : null
		  if(cache.put('checked',checked))
			  success.success = true
	  }

      render success as JSON
  }

  @Secured(['ROLE_USER'])
  def updateIssueEntitlementOverwrite() {
      Map success = [success:false]
      EhcacheWrapper cache = contextService.getCache("/subscription/${params.referer}/${params.sub}", contextService.USER_SCOPE)
      Map issueEntitlementCandidates = cache.get('issueEntitlementCandidates')
      def ieCandidate = issueEntitlementCandidates.get(params.key)
      if(!ieCandidate)
          ieCandidate = [:]
      if(params.coverage) {
          def ieCoverage
          Pattern pattern = Pattern.compile("(\\w+)(\\d+)")
          Matcher matcher = pattern.matcher(params.prop)
          if(matcher.find()) {
              String prop = matcher.group(1)
              int covStmtKey = Integer.parseInt(matcher.group(2))
              if(!ieCandidate.coverages){
                  ieCandidate.coverages = []
                  ieCoverage = [:]
              }
              else
                  ieCoverage = ieCandidate.coverages[covStmtKey]
              if(prop in ['startDate','endDate']) {
                  SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
                  ieCoverage[prop] = sdf.parse(params.propValue)
              }
              else {
                  ieCoverage[prop] = params.propValue
              }
              ieCandidate.coverages[covStmtKey] = ieCoverage
          }
          else {
              log.error("something wrong with the regex matching ...")
          }
      }
      else {
          ieCandidate[params.prop] = params.propValue
      }
      issueEntitlementCandidates.put(params.key,ieCandidate)
      if(cache.put('issueEntitlementCandidates',issueEntitlementCandidates))
          success.success = true
      render success as JSON
  }

    @Secured(['ROLE_USER'])
    def addOrgRole() {
        def owner  = genericOIDService.resolveOID(params.parent)
        def rel    = RefdataValue.get(params.orm_orgRole)

        def orgIds = params.list('orm_orgOid')
        orgIds.each{ oid ->
            def org_to_link = genericOIDService.resolveOID(oid)
            boolean duplicateOrgRole = false

            if(params.recip_prop == 'sub') {
                duplicateOrgRole = OrgRole.findAllBySubAndRoleTypeAndOrg(owner, rel, org_to_link) ? true : false
            }
            else if(params.recip_prop == 'pkg') {
                duplicateOrgRole = OrgRole.findAllByPkgAndRoleTypeAndOrg(owner, rel, org_to_link) ? true : false
            }
            else if(params.recip_prop == 'lic') {
                duplicateOrgRole = OrgRole.findAllByLicAndRoleTypeAndOrg(owner, rel, org_to_link) ? true : false
            }
            else if(params.recip_prop == 'title') {
                duplicateOrgRole = OrgRole.findAllByTitleAndRoleTypeAndOrg(owner, rel, org_to_link) ? true : false
            }

            if(! duplicateOrgRole) {
                def new_link = new OrgRole(org: org_to_link, roleType: rel)
                new_link[params.recip_prop] = owner

                if (new_link.save(flush: true)) {
                    // log.debug("Org link added")
                    if (owner instanceof ShareSupport && owner.checkSharePreconditions(new_link)) {
                        new_link.isShared = true
                        new_link.save(flush:true)

                        owner.updateShare(new_link)
                    }
                } else {
                    log.error("Problem saving new org link ..")
                    new_link.errors.each { e ->
                        log.error( e.toString() )
                    }
                    //flash.error = message(code: 'default.error')
                }
            }
        }
        redirect(url: request.getHeader('referer'))
    }

    @Secured(['ROLE_USER'])
    def delOrgRole() {
        def or = OrgRole.get(params.id)

        def owner = or.getOwner()
        if (owner instanceof ShareSupport && or.isShared) {
            or.isShared = false
            owner.updateShare(or)
        }
        or.delete(flush:true)

        redirect(url: request.getHeader('referer'))
    }

    @Secured(['ROLE_USER'])
    def addPrsRole() {
        Org org             = (Org) genericOIDService.resolveOID(params.org)
        def parent          = genericOIDService.resolveOID(params.parent)
        Person person       = (Person) genericOIDService.resolveOID(params.person)
        RefdataValue role   = (RefdataValue) genericOIDService.resolveOID(params.role)

        PersonRole newPrsRole
        PersonRole existingPrsRole

        if (org && person && role) {
            newPrsRole = new PersonRole(prs: person, org: org)
            if (parent) {
                newPrsRole.responsibilityType = role
                newPrsRole.setReference(parent)

                String[] ref = newPrsRole.getReference().split(":")
                existingPrsRole = PersonRole.findWhere(prs:person, org: org, responsibilityType: role, "${ref[0]}": parent)
            }
            else {
                newPrsRole.functionType = role
                existingPrsRole = PersonRole.findWhere(prs:person, org: org, functionType: role)
            }
        }

        if (! existingPrsRole && newPrsRole && newPrsRole.save(flush:true)) {
            //flash.message = message(code: 'default.success')
        }
        else {
            log.error("Problem saving new person role ..")
            //flash.error = message(code: 'default.error')
        }

        redirect(url: request.getHeader('referer'))
    }

    @Secured(['ROLE_USER'])
    def delPrsRole() {
        PersonRole prsRole = PersonRole.get(params.id)

        if (prsRole && prsRole.delete(flush: true)) {
        }
        else {
            log.error("Problem deleting person role ..")
            //flash.error = message(code: 'default.error')
        }
        redirect(url: request.getHeader('referer'))
    }

    @Secured(['ROLE_USER'])
    def addRefdataValue() {

        RefdataValue newRefdataValue
        String error
        String msg

        RefdataCategory rdc = RefdataCategory.findById(params.refdata_category_id)

        if (RefdataValue.getByValueAndCategory(params.refdata_value, rdc.desc)) {
            error = message(code: "refdataValue.create_new.unique")
            log.debug(error)
        }
        else {
            Map<String, Object> map = [
                    token   : params.refdata_value,
                    rdc     : rdc.desc,
                    hardData: false,
                    i10n    : [value_de: params.refdata_value, value_en: params.refdata_value]
            ]

            newRefdataValue = RefdataValue.construct(map)

            if (newRefdataValue?.hasErrors()) {
                log.error(newRefdataValue.errors.toString())
                error = message(code: 'default.error')
            }
            else {
                msg = message(code: 'refdataValue.created', args: [newRefdataValue.value])
            }
        }

        if (params.reloadReferer) {
            flash.newRefdataValue = newRefdataValue
            flash.error   = error
            flash.message = msg
            redirect(url: params.reloadReferer)
        }
    }

    @Secured(['ROLE_USER'])
    def addRefdataCategory() {

        RefdataCategory newRefdataCategory
        String error
        String msg

        RefdataCategory rdc = RefdataCategory.getByDesc(params.refdata_category)
        if (rdc) {
            error = message(code: 'refdataCategory.create_new.unique')
            log.debug(error)
        }
        else {
            Map<String, Object> map = [
                    token   : params.refdata_category,
                    hardData: false,
                    i10n    : [desc_de: params.refdata_category, desc_en: params.refdata_category]
            ]

            newRefdataCategory = RefdataCategory.construct(map)

            if (newRefdataCategory?.hasErrors()) {
                log.error(newRefdataCategory.errors.toString())
                error = message(code: 'default.error')
            }
            else {
                msg = message(code: 'refdataCategory.created', args: [newRefdataCategory.desc])
            }
        }

        if (params.reloadReferer) {
            flash.newRefdataCategory = newRefdataCategory
            flash.error   = error
            flash.message = msg
            redirect(url: params.reloadReferer)
        }
    }

    @Secured(['ROLE_USER'])
    def addCustomPropertyType() {
        def newProp
        def error
        def msg
        def ownerClass = params.ownerClass // we might need this for addCustomPropertyValue
        def owner      = AppUtils.getDomainClass( ownerClass )?.getClazz()?.get(params.ownerId)

        // TODO ownerClass
        if (PropertyDefinition.findByNameAndDescrAndTenantIsNull(params.cust_prop_name, params.cust_prop_desc)) {
            error = message(code: 'propertyDefinition.name.unique')
        }
        else {
            if (params.cust_prop_type.equals(RefdataValue.toString())) { // TODO [ticket=2880]
                if (params.refdatacategory) {

                    Map<String, Object> map = [
                            token       : params.cust_prop_name,
                            category    : params.cust_prop_desc,
                            type        : params.cust_prop_type,
                            rdc         : RefdataCategory.get(params.refdatacategory)?.getDesc(),
                            multiple    : (params.cust_prop_multiple_occurence == 'on'),
                            i10n        : [
                                    name_de: params.cust_prop_name?.trim(),
                                    name_en: params.cust_prop_name?.trim(),
                                    expl_de: params.cust_prop_expl?.trim(),
                                    expl_en: params.cust_prop_expl?.trim()
                            ]
                    ]

                    newProp = PropertyDefinition.construct(map)
                }
                else {
                    error = message(code: 'ajax.addCustPropertyType.error')
                }
            }
            else {
                    Map<String, Object> map = [
                            token       : params.cust_prop_name,
                            category    : params.cust_prop_desc,
                            type        : params.cust_prop_type,
                            multiple    : (params.cust_prop_multiple_occurence == 'on'),
                            i10n        : [
                                    name_de: params.cust_prop_name?.trim(),
                                    name_en: params.cust_prop_name?.trim(),
                                    expl_de: params.cust_prop_expl?.trim(),
                                    expl_en: params.cust_prop_expl?.trim()
                            ]
                    ]

                    newProp = PropertyDefinition.construct(map)
            }

            if (newProp?.hasErrors()) {
                log.error(newProp.errors.toString())
                error = message(code: 'default.error')
            }
            else {
                msg = message(code: 'ajax.addCustPropertyType.success')
                //newProp.softData = true
                newProp.save(flush: true)

                if (params.autoAdd == "on" && newProp) {
                    params.propIdent = newProp.id.toString()
                    chain(action: "addCustomPropertyValue", params: params)
                }
            }
        }

        request.setAttribute("editable", params.editable == "true")

        if (params.reloadReferer) {
            flash.newProp = newProp
            flash.error = error
            flash.message = msg
            redirect(url: params.reloadReferer)
        }
        else if (params.redirect) {
            flash.newProp = newProp
            flash.error = error
            flash.message = msg
            redirect(controller:"propertyDefinition", action:"create")
        }
        else {
            Map<String, Object> allPropDefGroups = owner._getCalculatedPropDefGroups(contextService.getOrg())

            render(template: "/templates/properties/custom", model: [
                    ownobj: owner,
                    customProperties: owner.propertySet,
                    newProp: newProp,
                    error: error,
                    message: msg,
                    orphanedProperties: allPropDefGroups.orphanedProperties
            ])
        }
    }

  @Secured(['ROLE_USER'])
  def addCustomPropertyValue(){
    if(params.propIdent.length() > 0) {
      def error
      def newProp
      def owner = AppUtils.getDomainClass( params.ownerClass )?.getClazz()?.get(params.ownerId)
      def type = PropertyDefinition.get(params.propIdent.toLong())
      Org contextOrg = contextService.getOrg()
      def existingProp = owner.propertySet.find { it.type.name == type.name && it.tenant?.id == contextOrg.id }

      if (existingProp == null || type.multipleOccurrence) {
        newProp = PropertyDefinition.createGenericProperty(PropertyDefinition.CUSTOM_PROPERTY, owner, type, contextOrg )
        if (newProp.hasErrors()) {
          log.error(newProp.errors.toString())
        } else {
          log.debug("New custom property created: " + newProp.type.name)
        }
      } else {
        error = message(code: 'ajax.addCustomPropertyValue.error')
      }

      owner.refresh()

      request.setAttribute("editable", params.editable == "true")
      boolean showConsortiaFunctions = Boolean.parseBoolean(params.showConsortiaFunctions)
        if(params.withoutRender){
            if(params.url){
                redirect(url: params.url)
            }else {
                redirect(url: request.getHeader('referer'))
            }
        }
        else
        {
              if (params.propDefGroup) {
                render(template: "/templates/properties/group", model: [
                        ownobj          : owner,
                        contextOrg      : contextOrg,
                        newProp         : newProp,
                        error           : error,
                        showConsortiaFunctions: showConsortiaFunctions,
                        propDefGroup    : genericOIDService.resolveOID(params.propDefGroup),
                        propDefGroupBinding : genericOIDService.resolveOID(params.propDefGroupBinding),
                        custom_props_div: "${params.custom_props_div}", // JS markup id
                        prop_desc       : type.descr // form data
                ])
              }
              else {
                  Map<String, Object> allPropDefGroups = owner._getCalculatedPropDefGroups(contextService.getOrg())

                  Map<String, Object> modelMap =  [
                          ownobj                : owner,
                          contextOrg            : contextOrg,
                          newProp               : newProp,
                          showConsortiaFunctions: showConsortiaFunctions,
                          error                 : error,
                          custom_props_div      : "${params.custom_props_div}", // JS markup id
                          prop_desc             : type.descr, // form data
                          orphanedProperties    : allPropDefGroups.orphanedProperties
                  ]


                      render(template: "/templates/properties/custom", model: modelMap)
                  }
        }
    }
    else {
      log.error("Form submitted with missing values")
    }
  }

    @Secured(['ROLE_USER'])
    def addCustomPropertyGroupBinding() {

        def ownobj              = genericOIDService.resolveOID(params.ownobj)
        def propDefGroup        = genericOIDService.resolveOID(params.propDefGroup)
        List<PropertyDefinitionGroup> availPropDefGroups  = PropertyDefinitionGroup.getAvailableGroups(contextService.getOrg(), ownobj.class.name)

        if (ownobj && propDefGroup) {
            if (params.isVisible in ['Yes', 'No']) {
                PropertyDefinitionGroupBinding gb = new PropertyDefinitionGroupBinding(
                        propDefGroup: propDefGroup,
                        isVisible: (params.isVisible == 'Yes')
                )
                if (ownobj.class.name == License.class.name) {
                    gb.lic = ownobj
                }
                else if (ownobj.class.name == Org.class.name) {
                    gb.org = ownobj
                }
                else if (ownobj.class.name == Subscription.class.name) {
                    gb.sub = ownobj
                }
                gb.save(flush:true)
            }
        }

        render(template: "/templates/properties/groupBindings", model:[
                propDefGroup: propDefGroup,
                ownobj: ownobj,
                availPropDefGroups: availPropDefGroups,
                editable: params.editable,
                showConsortiaFunctions: params.showConsortiaFunctions
        ])
    }


    @Secured(['ROLE_USER'])
    def deleteCustomPropertyGroupBinding() {
        def ownobj              = genericOIDService.resolveOID(params.ownobj)
        def propDefGroup        = genericOIDService.resolveOID(params.propDefGroup)
        def binding             = genericOIDService.resolveOID(params.propDefGroupBinding)
        List<PropertyDefinitionGroup> availPropDefGroups  = PropertyDefinitionGroup.getAvailableGroups(contextService.getOrg(), ownobj.class.name)

        if (ownobj && propDefGroup && binding) {
            binding.delete(flush:true)
        }

        render(template: "/templates/properties/groupBindings", model:[
                propDefGroup: propDefGroup,
                ownobj: ownobj,
                availPropDefGroups: availPropDefGroups,
                editable: params.editable,
                showConsortiaFunctions: params.showConsortiaFunctions
        ])
    }

    /**
    * Add domain specific private property
    * @return
    */
    @Secured(['ROLE_USER'])
    def addPrivatePropertyValue(){
      if(params.propIdent.length() > 0) {
        def error
        def newProp
        Org tenant = Org.get(params.tenantId)
          def owner  = AppUtils.getDomainClass( params.ownerClass )?.getClazz()?.get(params.ownerId)
          PropertyDefinition type   = PropertyDefinition.get(params.propIdent.toLong())

        if (! type) { // new property via select2; tmp deactivated
          error = message(code:'propertyDefinition.private.deactivated')
        }
        else {
            Set<AbstractPropertyWithCalculatedLastUpdated> existingProps
            if(owner.hasProperty("privateProperties")) {
                existingProps = owner.propertySet.findAll {
                    it.owner.id == owner.id && it.type.id == type.id // this sucks due lazy proxy problem
                }
            }
            else {
                existingProps = owner.propertySet.findAll { AbstractPropertyWithCalculatedLastUpdated prop ->
                    prop.owner.id == owner.id && prop.type.id == type.id && prop.tenant.id == tenant.id && !prop.isPublic
                }
            }
          existingProps.removeAll { it.type.name != type.name } // dubious fix


          if (existingProps.size() == 0 || type.multipleOccurrence) {
            newProp = PropertyDefinition.createGenericProperty(PropertyDefinition.PRIVATE_PROPERTY, owner, type, contextService.getOrg())
            if (newProp.hasErrors()) {
              log.error(newProp.errors.toString())
            } else {
              log.debug("New private property created: " + newProp.type.name)
            }
          } else {
            error = message(code: 'ajax.addCustomPropertyValue.error')
          }
        }

        owner.refresh()

        request.setAttribute("editable", params.editable == "true")
          if(params.withoutRender){
              if(params.url){
                  redirect(url: params.url)
              }else {
                  redirect(url: request.getHeader('referer'))
              }
          }else {
              render(template: "/templates/properties/private", model: [
                      ownobj          : owner,
                      tenant          : tenant,
                      newProp         : newProp,
                      error           : error,
                      contextOrg      : contextService.org,
                      custom_props_div: "custom_props_div_${tenant.id}", // JS markup id
                      prop_desc       : type.descr // form data
              ])
          }
      }
      else  {
        log.error("Form submitted with missing values")
      }
    }

    @Secured(['ROLE_USER'])
    def toggleShare() {
        def owner = genericOIDService.resolveOID( params.owner )
        def sharedObject = genericOIDService.resolveOID( params.sharedObject )

        if (! sharedObject.isShared) {
            sharedObject.isShared = true
        } else {
            sharedObject.isShared = false
        }
        sharedObject.save(flush:true)

        ((ShareSupport) owner).updateShare(sharedObject)

        if (params.tmpl) {
            if (params.tmpl == 'documents') {
                render(template: '/templates/documents/card', model: [ownobj: owner, editable: true]) // TODO editable from owner
            }
            else if (params.tmpl == 'notes') {
                render(template: '/templates/notes/card', model: [ownobj: owner, editable: true]) // TODO editable from owner
            }
        }
        else {
            redirect(url: request.getHeader('referer'))
        }
    }

    @Secured(['ROLE_USER'])
    def toggleOrgRole() {
        OrgRole oo = OrgRole.executeQuery('select oo from OrgRole oo where oo.sub = :sub and oo.roleType in :roleTypes',[sub:Subscription.get(params.id),roleTypes:[RDStore.OR_SUBSCRIBER_CONS,RDStore.OR_SUBSCRIBER_CONS_HIDDEN]])[0]
        if(oo) {
            if(oo.roleType == RDStore.OR_SUBSCRIBER_CONS)
                oo.roleType = RDStore.OR_SUBSCRIBER_CONS_HIDDEN
            else if(oo.roleType == RDStore.OR_SUBSCRIBER_CONS_HIDDEN)
                oo.roleType = RDStore.OR_SUBSCRIBER_CONS
        }
        oo.save(flush: true)
        redirect(url: request.getHeader('referer'))
    }

    @Secured(['ROLE_USER'])
    def toggleAudit() {
        //String referer = request.getHeader('referer')
        if(formService.validateToken(params)) {
            def owner = genericOIDService.resolveOID(params.owner)
            if (owner) {
                def members = owner.getClass().findAllByInstanceOf(owner)
                def objProps = owner.getClass().controlledProperties
                def prop = params.property

                if (prop in objProps) {
                    if (! AuditConfig.getConfig(owner, prop)) {
                        AuditConfig.addConfig(owner, prop)

                        members.each { m ->
                            m.setProperty(prop, owner.getProperty(prop))
                            m.save(flush:true)
                        }
                    }
                    else {
                        AuditConfig.removeConfig(owner, prop)

                        if (! params.keep) {
                            members.each { m ->
                                if(m[prop] instanceof Boolean)
                                    m.setProperty(prop, false)
                                else m.setProperty(prop, null)
                                m.save(flush: true)
                            }
                        }

                        // delete pending changes
                        // e.g. PendingChange.changeDoc = {changeTarget, changeType, changeDoc:{OID,  event}}
                        members.each { m ->
                            List<PendingChange> openPD = PendingChange.executeQuery("select pc from PendingChange as pc where pc.status is null and pc.costItem is null and pc.oid = :objectID",
                                    [objectID: "${m.class.name}:${m.id}"])

                            openPD?.each { pc ->
                                def payload = JSON.parse(pc?.payload)
                                if (payload && payload?.changeDoc) {
                                    def eventObj = genericOIDService.resolveOID(payload.changeDoc?.OID)
                                    def eventProp = payload.changeDoc?.prop
                                    if (eventObj?.id == owner?.id && eventProp.equalsIgnoreCase(prop)) {
                                        pc.delete(flush: true)
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
        redirect(url: request.getHeader('referer'))
    }

    @Secured(['ROLE_USER'])
    def togglePropertyIsPublic() {
        if(formService.validateToken(params)) {
            AbstractPropertyWithCalculatedLastUpdated property = genericOIDService.resolveOID(params.oid)
            property.isPublic = !property.isPublic
            property.save(flush: true)
            Org contextOrg = contextService.getOrg()
            request.setAttribute("editable", params.editable == "true")
            if(params.propDefGroup) {
                render(template: "/templates/properties/group", model: [
                        ownobj          : property.owner,
                        newProp         : property,
                        contextOrg      : contextOrg,
                        showConsortiaFunctions: params.showConsortiaFunctions == "true",
                        propDefGroup    : genericOIDService.resolveOID(params.propDefGroup),
                        custom_props_div: "${params.custom_props_div}", // JS markup id
                        prop_desc       : property.type.descr // form data
                ])
            }
            else {
                Map<String, Object>  allPropDefGroups = property.owner._getCalculatedPropDefGroups(contextOrg)

                Map<String, Object> modelMap =  [
                        ownobj                : property.owner,
                        newProp               : property,
                        contextOrg            : contextOrg,
                        showConsortiaFunctions: params.showConsortiaFunctions == "true",
                        custom_props_div      : "${params.custom_props_div}", // JS markup id
                        prop_desc             : property.type.descr, // form data
                        orphanedProperties    : allPropDefGroups.orphanedProperties
                ]
                render(template: "/templates/properties/custom", model: modelMap)
            }
        }
    }

    @Secured(['ROLE_USER'])
    def togglePropertyAuditConfig() {
        def className = params.propClass.split(" ")[1]
        def propClass = Class.forName(className)
        def owner     = AppUtils.getDomainClass( params.ownerClass )?.getClazz()?.get(params.ownerId)
        def property  = propClass.get(params.id)
        def prop_desc = property.getType().getDescr()
        Org contextOrg = contextService.getOrg()

        if (AuditConfig.getConfig(property, AuditConfig.COMPLETE_OBJECT)) {

            AuditConfig.removeAllConfigs(property)

            property.getClass().findAllByInstanceOf(property).each{ prop ->
                prop.delete(flush: true) //see ERMS-2049. Here, it is unavoidable because it affects the loading of orphaned properties - Hibernate tries to set up a list and encounters implicitely a SessionMismatch
            }


            // delete pending changes

            /*def openPD = PendingChange.executeQuery("select pc from PendingChange as pc where pc.status is null" )
            openPD.each { pc ->
                if (pc.payload) {
                    def payload = JSON.parse(pc.payload)
                    if (payload.changeDoc) {
                        def scp = genericOIDService.resolveOID(payload.changeDoc.OID)
                        if (scp?.id == property.id) {
                            pc.delete(flush:true)
                        }
                    }
                }
            }*/
        }
        else {

            owner.getClass().findAllByInstanceOf(owner).each { member ->

                def existingProp = property.getClass().findByOwnerAndInstanceOf(member, property)
                if (! existingProp) {

                    // multi occurrence props; add one additional with backref
                    if (property.type.multipleOccurrence) {
                        AbstractPropertyWithCalculatedLastUpdated additionalProp = PropertyDefinition.createGenericProperty(PropertyDefinition.CUSTOM_PROPERTY, member, property.type, contextOrg)
                        additionalProp = property.copyInto(additionalProp)
                        additionalProp.instanceOf = property
                        additionalProp.isPublic = true
                        additionalProp.save(flush: true)
                    }
                    else {
                        List<AbstractPropertyWithCalculatedLastUpdated> matchingProps = property.getClass().findAllByOwnerAndTypeAndTenant(member, property.type, contextOrg)
                        // unbound prop found with matching type, set backref
                        if (matchingProps) {
                            matchingProps.each { AbstractPropertyWithCalculatedLastUpdated memberProp ->
                                memberProp.instanceOf = property
                                memberProp.isPublic = true
                                memberProp.save(flush:true)
                            }
                        }
                        else {
                            // no match found, creating new prop with backref
                            AbstractPropertyWithCalculatedLastUpdated newProp = PropertyDefinition.createGenericProperty(PropertyDefinition.CUSTOM_PROPERTY, member, property.type, contextOrg)
                            newProp = property.copyInto(newProp)
                            newProp.instanceOf = property
                            newProp.isPublic = true
                            newProp.save(flush: true)
                        }
                    }
                }
            }

            AuditConfig.addConfig(property, AuditConfig.COMPLETE_OBJECT)
        }

        request.setAttribute("editable", params.editable == "true")
        if(params.propDefGroup) {
          render(template: "/templates/properties/group", model: [
                  ownobj          : owner,
                  newProp         : property,
                  showConsortiaFunctions: params.showConsortiaFunctions,
                  propDefGroup    : genericOIDService.resolveOID(params.propDefGroup),
                  contextOrg      : contextOrg,
                  custom_props_div: "${params.custom_props_div}", // JS markup id
                  prop_desc       : prop_desc // form data
          ])
        }
        else {
            Map<String, Object>  allPropDefGroups = owner._getCalculatedPropDefGroups(contextService.getOrg())

            Map<String, Object> modelMap =  [
                    ownobj                : owner,
                    newProp               : property,
                    showConsortiaFunctions: params.showConsortiaFunctions,
                    custom_props_div      : "${params.custom_props_div}", // JS markup id
                    prop_desc             : prop_desc, // form data
                    contextOrg            : contextOrg,
                    orphanedProperties    : allPropDefGroups.orphanedProperties
            ]
            render(template: "/templates/properties/custom", model: modelMap)
        }
    }

    @Secured(['ROLE_USER'])
    def deleteCustomProperty() {
        def className = params.propClass.split(" ")[1]
        def propClass = Class.forName(className)
        def owner     = AppUtils.getDomainClass( params.ownerClass )?.getClazz()?.get(params.ownerId)
        def property  = propClass.get(params.id)
        def prop_desc = property.getType().getDescr()
        Org contextOrg = contextService.getOrg()

        AuditConfig.removeAllConfigs(property)

        //owner.customProperties.remove(property)

        try {
            property.delete(flush:true)
        } catch (Exception e) {
            log.error(" TODO: fix property.delete() when instanceOf ")
        }


        if(property.hasErrors()) {
            log.error(property.errors.toString())
        }
        else {
            log.debug("Deleted custom property: " + property.type.name)
        }
        request.setAttribute("editable", params.editable == "true")
        boolean showConsortiaFunctions = Boolean.parseBoolean(params.showConsortiaFunctions)
        if(params.propDefGroup) {
          render(template: "/templates/properties/group", model: [
                  ownobj          : owner,
                  newProp         : property,
                  showConsortiaFunctions: showConsortiaFunctions,
                  contextOrg      : contextOrg,
                  propDefGroup    : genericOIDService.resolveOID(params.propDefGroup),
                  propDefGroupBinding : genericOIDService.resolveOID(params.propDefGroupBinding),
                  custom_props_div: "${params.custom_props_div}", // JS markup id
                  prop_desc       : prop_desc // form data
          ])
        }
        else {
            Map<String, Object> allPropDefGroups = owner._getCalculatedPropDefGroups(contextOrg)
            Map<String, Object> modelMap =  [
                    ownobj                : owner,
                    newProp               : property,
                    showConsortiaFunctions: showConsortiaFunctions,
                    contextOrg            : contextOrg,
                    custom_props_div      : "${params.custom_props_div}", // JS markup id
                    prop_desc             : prop_desc, // form data
                    orphanedProperties    : allPropDefGroups.orphanedProperties
            ]

            render(template: "/templates/properties/custom", model: modelMap)
        }
    }

  /**
    * Delete domain specific private property
    *
    * @return
    */
  @Secured(['ROLE_USER'])
  def deletePrivateProperty(){
    def className = params.propClass.split(" ")[1]
    def propClass = Class.forName(className)
    def property  = propClass.get(params.id)
    def tenant    = property.type.tenant
    def owner     = AppUtils.getDomainClass( params.ownerClass )?.getClazz()?.get(params.ownerId)
    def prop_desc = property.getType().getDescr()

    owner.propertySet.remove(property)
    property.delete(flush:true)

    if(property.hasErrors()){
      log.error(property.errors.toString())
    } else{
      log.debug("Deleted private property: " + property.type.name)
    }
    request.setAttribute("editable", params.editable == "true")
    render(template: "/templates/properties/private", model:[
            ownobj: owner,
            tenant: tenant,
            newProp: property,
            contextOrg: contextService.org,
            custom_props_div: "custom_props_div_${tenant.id}",  // JS markup id
            prop_desc: prop_desc // form data
    ])
  }

    @Secured(['ROLE_USER'])
    def hideDashboardDueDate(){
        setDashboardDueDateIsHidden(true)
    }

    @Secured(['ROLE_USER'])
    def showDashboardDueDate(){
        setDashboardDueDateIsHidden(false)
    }

    @Secured(['ROLE_USER'])
    private setDashboardDueDateIsHidden(boolean isHidden){
        log.debug("Hide/Show Dashboard DueDate - isHidden="+isHidden)

        Map<String, Object> result = [:]
        result.user = contextService.user
        result.institution = contextService.org
        flash.error = ''

        if (! accessService.checkUserIsMember(result.user, result.institution)) {
            flash.error = "You do not have permission to access ${contextService.org.name} pages. Please request access on the profile page"
            response.sendError(401)
            return;
        }

        if (params.owner) {
            DashboardDueDate dueDate = (DashboardDueDate) genericOIDService.resolveOID(params.owner)
            if (dueDate){
                dueDate.isHidden = isHidden
                dueDate.save(flush: true)
            } else {
                if (isHidden)   flash.error += message(code:'dashboardDueDate.err.toHide.doesNotExist')
                else            flash.error += message(code:'dashboardDueDate.err.toShow.doesNotExist')
            }
        } else {
            if (isHidden)   flash.error += message(code:'dashboardDueDate.err.toHide.doesNotExist')
            else            flash.error += message(code:'dashboardDueDate.err.toShow.doesNotExist')
        }

        result.is_inst_admin = accessService.checkMinUserOrgRole(result.user, result.institution, 'INST_ADM')
        result.editable = accessService.checkMinUserOrgRole(result.user, result.institution, 'INST_EDITOR')

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0
        result.dashboardDueDatesOffset = result.offset

        result.dueDates = dashboardDueDatesService.getDashboardDueDates(contextService.user, contextService.org, false, false, result.max, result.dashboardDueDatesOffset)
        result.dueDatesCount = dashboardDueDatesService.getDashboardDueDates(contextService.user, contextService.org, false, false).size()

        render (template: "/user/tableDueDates", model: [dueDates: result.dueDates, dueDatesCount: result.dueDatesCount, max: result.max, offset: result.offset])
    }

    @Secured(['ROLE_USER'])
    def dashboardDueDateSetIsDone() {
       setDashboardDueDateIsDone(true)
    }

    @Secured(['ROLE_USER'])
    def dashboardDueDateSetIsUndone() {
       setDashboardDueDateIsDone(false)
    }

    @Secured(['ROLE_USER'])
    private setDashboardDueDateIsDone(boolean isDone){
        log.debug("Done/Undone Dashboard DueDate - isDone="+isDone)

        Map<String, Object> result = [:]
        result.user = contextService.user
        result.institution = contextService.org
        flash.error = ''

        if (! accessService.checkUserIsMember(result.user, result.institution)) {
            flash.error = "You do not have permission to access ${contextService.org.name} pages. Please request access on the profile page"
            response.sendError(401)
            return
        }


        if (params.owner) {
            DueDateObject dueDateObject = (DueDateObject) genericOIDService.resolveOID(params.owner)
            if (dueDateObject){
                Object obj = genericOIDService.resolveOID(dueDateObject.oid)
                if (obj instanceof Task && isDone){
                    Task dueTask = (Task)obj
                    dueTask.setStatus(RDStore.TASK_STATUS_DONE)
                    dueTask.save(flush: true)
                }
                dueDateObject.isDone = isDone
                dueDateObject.save(flush: true)
            } else {
                if (isDone)   flash.error += message(code:'dashboardDueDate.err.toSetDone.doesNotExist')
                else          flash.error += message(code:'dashboardDueDate.err.toSetUndone.doesNotExist')
            }
        } else {
            if (isDone)   flash.error += message(code:'dashboardDueDate.err.toSetDone.doesNotExist')
            else          flash.error += message(code:'dashboardDueDate.err.toSetUndone.doesNotExist')
        }

        result.is_inst_admin = accessService.checkMinUserOrgRole(result.user, result.institution, 'INST_ADM')
        result.editable = accessService.checkMinUserOrgRole(result.user, result.institution, 'INST_EDITOR')

        result.max = params.max ? Integer.parseInt(params.max) : result.user.getDefaultPageSizeAsInteger()
        result.offset = params.offset ? Integer.parseInt(params.offset) : 0
        result.dashboardDueDatesOffset = result.offset

        result.dueDates = dashboardDueDatesService.getDashboardDueDates(contextService.user, contextService.org, false, false, result.max, result.dashboardDueDatesOffset)
        result.dueDatesCount = dashboardDueDatesService.getDashboardDueDates(contextService.user, contextService.org, false, false).size()

        render (template: "/user/tableDueDates", model: [dueDates: result.dueDates, dueDatesCount: result.dueDatesCount, max: result.max, offset: result.offset])
    }

    def delete() {
      switch(params.cmd) {
        case 'deletePersonRole': deletePersonRole()
        break
        default: def obj = genericOIDService.resolveOID(params.oid)
          if (obj) {
            obj.delete(flush:true)
          }
        break
      }
      redirect(url: request.getHeader('referer'))
    }

    //TODO: Überprüfuen, ob die Berechtigung korrekt funktioniert.
    @Secured(['ROLE_ORG_EDITOR'])
    def deletePersonRole(){
        def obj = genericOIDService.resolveOID(params.oid)
        if (obj) {
                obj.delete(flush:true)
        }
    }

    def toggleEditMode() {
        log.debug ('toggleEditMode()')

        User user = contextService.getUser()
        def show = params.showEditMode

        if (show) {
            def setting = user.getSetting(UserSetting.KEYS.SHOW_EDIT_MODE, RDStore.YN_YES)

            if (show == 'true') {
                setting.setValue(RDStore.YN_YES)
            }
            else if (show == 'false') {
                setting.setValue(RDStore.YN_NO)
            }
        }
        render show
    }

    @Secured(['ROLE_USER'])
    def addIdentifier() {
        log.debug("AjaxController::addIdentifier ${params}")
        def owner = genericOIDService.resolveOID(params.owner)
        def namespace = genericOIDService.resolveOID(params.namespace)
        String value = params.value?.trim()

        if (owner && namespace && value) {
            FactoryResult fr = Identifier.constructWithFactoryResult([value: value, reference: owner, namespace: namespace])

            fr.setFlashScopeByStatus(flash)
        }
        redirect(url: request.getHeader('referer'))
    }

    @Secured(['ROLE_USER'])
    def deleteIdentifier() {
        log.debug("AjaxController::deleteIdentifier ${params}")
        def owner = genericOIDService.resolveOID(params.owner)
        def target = genericOIDService.resolveOID(params.target)

        log.debug('owner: ' + owner)
        log.debug('target: ' + target)

        if (owner && target) {
            if (target."${Identifier.getAttributeName(owner)}"?.id == owner.id) {
                log.debug("Identifier deleted: ${params}")
                target.delete(flush:true)
            }
        }
        redirect(url: request.getHeader('referer'))
    }

    @Secured(['ROLE_USER'])
  def addToCollection() {
    log.debug("AjaxController::addToCollection ${params}");

    def contextObj = resolveOID2(params.__context)
    GrailsClass domain_class = AppUtils.getDomainClass( params.__newObjectClass )
    if ( domain_class ) {

      if ( contextObj ) {
        // log.debug("Create a new instance of ${params.__newObjectClass}");

        def new_obj = domain_class.getClazz().newInstance();

        domain_class.getPersistentProperties().each { p -> // list of GrailsDomainClassProperty
          // log.debug("${p.name} (assoc=${p.isAssociation()}) (oneToMany=${p.isOneToMany()}) (ManyToOne=${p.isManyToOne()}) (OneToOne=${p.isOneToOne()})");
          if ( params[p.name] ) {
            if ( p.isAssociation() ) {
              if ( p.isManyToOne() || p.isOneToOne() ) {
                // Set ref property
                // log.debug("set assoc ${p.name} to lookup of OID ${params[p.name]}");
                // if ( key == __new__ then we need to create a new instance )
                def new_assoc = resolveOID2(params[p.name])
                if(new_assoc){
                  new_obj[p.name] = new_assoc               
                }
              }
              else {
                // Add to collection
                // log.debug("add to collection ${p.name} for OID ${params[p.name]}");
                new_obj[p.name].add(resolveOID2(params[p.name]))
              }
            }
            else {
              // log.debug("Set simple prop ${p.name} = ${params[p.name]}");
              new_obj[p.name] = params[p.name]
            }
          }
        }

        if ( params.__recip ) {
          // log.debug("Set reciprocal property ${params.__recip} to ${contextObj}");
          new_obj[params.__recip] = contextObj
        }

        // log.debug("Saving ${new_obj}");
        try{
          if ( new_obj.save(flush: true) ) {
            log.debug("Saved OK");
          }
          else {
            flash.domainError = new_obj
            new_obj.errors.each { e ->
              log.debug("Problem ${e}");
            }
          }
        }catch(Exception ex){

            flash.domainError = new_obj
            new_obj.errors.each { e ->
            log.debug("Problem ${e}");
            }
        }
      }
      else {
        log.debug("Unable to locate instance of context class with oid ${params.__context}");
      }
    }
    else {
      log.error("Unable to lookup domain class ${params.__newObjectClass}");
    }
    redirect(url: request.getHeader('referer'))
  }
    
  def resolveOID2(String oid) {
    String[] oid_components = oid.split(':')
    def result

    GrailsClass domain_class = AppUtils.getDomainClass(oid_components[0])
    if (domain_class) {
      if (oid_components[1] == '__new__') {
        result = domain_class.getClazz().refdataCreate(oid_components)
        // log.debug("Result of create ${oid} is ${result?.id}");
      }
      else {
        result = domain_class.getClazz().get(oid_components[1])
      }
    }
    else {
      log.error("resolve OID failed to identify a domain class. Input was ${oid_components}");
    }
    result
  }

    @Secured(['ROLE_USER'])
  def deleteThrough() {
    // log.debug("deleteThrough(${params})");
    def context_object = resolveOID2(params.contextOid)
    def target_object = resolveOID2(params.targetOid)
    if ( context_object."${params.contextProperty}".contains(target_object) ) {
      def otr = context_object."${params.contextProperty}".remove(target_object)
      target_object.delete(flush:true)
      context_object.save(flush:true)
    }
    redirect(url: request.getHeader('referer'))

  }

    @Secured(['ROLE_USER'])
    def editableSetValue() {
        log.debug("editableSetValue ${params}");
        def result = null

        try {

            def target_object = resolveOID2(params.pk)

            if (target_object) {
                if (params.type == 'date') {
                    SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
                    def backup = target_object."${params.name}"

                    try {
                        if (params.value && params.value.size() > 0) {
                            // parse new date
                            def parsed_date = sdf.parse(params.value)
                            target_object."${params.name}" = parsed_date
                        } else {
                            // delete existing date
                            target_object."${params.name}" = null
                        }
                        target_object.save(flush:true, failOnError: true)
                    }
                    catch (Exception e) {
                        target_object."${params.name}" = backup
                        log.error( e.toString() )
                    }
                    finally {
                        if (target_object."${params.name}") {
                            result = (target_object."${params.name}").format(message(code: 'default.date.format.notime'))
                        }
                    }
                }
                else if (params.type == 'url') {
                    def backup = target_object."${params.name}"

                    try {
                        if (params.value && params.value.size() > 0) {
                            target_object."${params.name}" = new URL(params.value)
                        } else {
                            // delete existing url
                            target_object."${params.name}" = null
                        }
                        target_object.save(flush:true, failOnError: true)
                    }
                    catch (Exception e) {
                        target_object."${params.name}" = backup
                        log.error( e.toString() )
                    }
                    finally {
                        if (target_object."${params.name}") {
                            result = target_object."${params.name}"
                        }
                    }
                }
                else {
                    def binding_properties = [:]

                    if (target_object."${params.name}" instanceof BigDecimal) {
                        params.value = escapeService.parseFinancialValue(params.value)
                    }
                    if (target_object."${params.name}" instanceof Boolean) {
                        params.value = params.value?.equals("1")
                    }
                    if (params.value instanceof String) {
                        String value = params.value.startsWith('www.') ? ('http://' + params.value) : params.value
                        binding_properties[params.name] = value
                    } else {
                        binding_properties[params.name] = params.value
                    }
                    bindData(target_object, binding_properties)

                    target_object.save(flush:true, failOnError: true)


                    if (target_object."${params.name}" instanceof BigDecimal) {
                        result = NumberFormat.getInstance(LocaleContextHolder.getLocale()).format(target_object."${params.name}")
                        //is for that German users do not cry about comma-dot-change
                    } else {
                        result = target_object."${params.name}"
                    }
                }

                if (target_object instanceof SurveyResult) {

                    Org org = contextService.getOrg()
                    //If Survey Owner set Value then set FinishDate
                    if (org?.id == target_object.owner?.id && target_object.finishDate == null) {
                        String property = ""
                        if (target_object.type.isIntegerType()) {
                            property = "intValue"
                        } else if (target_object.type.isStringType()) {
                            property = "stringValue"
                        } else if (target_object.type.isBigDecimalType()) {
                            property = "decValue"
                        } else if (target_object.type.isDateType()) {
                            property = "dateValue"
                        } else if (target_object.type.isURLType()) {
                            property = "urlValue"
                        } else if (target_object.type.isRefdataValueType()) {
                            property = "refValue"
                        }

                        if (target_object[property] != null) {
                            log.debug("Set/Save FinishDate of SurveyResult (${target_object.id})")
                            target_object.finishDate = new Date()
                            target_object.save(flush:true)
                        }
                    }
                }

            }

        } catch(Exception e) {
            log.error("@ editableSetValue()")
            log.error( e.toString() )
        }

        log.debug("editableSetValue() returns ${result}")

        response.setContentType('text/plain')

        def outs = response.outputStream
        outs << result
        outs.flush()
        outs.close()
    }

    @Secured(['ROLE_USER'])
    def removeUserRole() {
        User user = resolveOID2(params.user)
        Role role = resolveOID2(params.role)
        if (user && role) {
            UserRole.remove(user,role,true)
        }
        redirect(url: request.getHeader('referer'))
    }

  /**
   * ToDo: This function is a duplicate of the one found in InplaceTagLib, both should be moved to a shared static utility
   */
  def renderObjectValue(value) {
    def result=''
    def not_set = message(code:'refdata.notSet')

    if ( value ) {
      switch ( value.class ) {
        case RefdataValue.class:

          if ( value.icon != null ) {
            result="<span class=\"select-icon ${value.icon}\"></span>";
            result += value.value ? value.getI10n('value') : not_set
          }
          else {
            result = value.value ? value.getI10n('value') : not_set
          }
          break;
        default:
          if(value instanceof String){

          }else{
            value = value.toString()
          }
          def no_ws = value.replaceAll(' ','')

          result = message(code:"refdata.${no_ws}", default:"${value ?: not_set}")
      }
    }
    // log.debug("Result of render: ${value} : ${result}");
    result;
  }



    def adjustSubscriptionList(){
        List<Subscription> data
        List result = []
        boolean showSubscriber = params.showSubscriber == 'true'
        boolean showConnectedObjs = params.showConnectedObjs == 'true'
        Map queryParams = [:]
        queryParams.status = []
        if(params.status){
            queryParams.status = JSON.parse(params.status).collect{Long.parseLong(it)}

        }

        queryParams.showSubscriber = showSubscriber
        queryParams.showConnectedObjs = showConnectedObjs

        data = subscriptionService.getMySubscriptions_writeRights(queryParams)


        if(data) {
            if(params.valueAsOID){
                data.each { Subscription s ->
                    result.add([value: genericOIDService.getOID(s), text: s.dropdownNamingConvention()])
                }
            }else {
                data.each { Subscription s ->
                    result.add([value: s.id, text: s.dropdownNamingConvention()])
                }
            }
        }
        withFormat {
            json {
                render result as JSON
            }
        }
    }

    def adjustLicenseList(){
        List<Subscription> data
        List result = []
        boolean showSubscriber = params.showSubscriber == 'true'
        boolean showConnectedObjs = params.showConnectedObjs == 'true'
        Map queryParams = [:]
        queryParams.status = []
        if(params.status){
            queryParams.status = JSON.parse(params.status).collect{Long.parseLong(it)}

        }

        queryParams.showSubscriber = showSubscriber
        queryParams.showConnectedObjs = showConnectedObjs

        data =  licenseService.getMyLicenses_writeRights(queryParams)


        if(data) {
            if(params.valueAsOID){
                data.each { License l ->
                    result.add([value: genericOIDService.getOID(l), text: l.dropdownNamingConvention()])
                }
            }else {
                data.each { License l ->
                    result.add([value: l.id, text: l.dropdownNamingConvention()])
                }
            }
        }
        withFormat {
            json {
                render result as JSON
            }
        }
    }

    def adjustCompareSubscriptionList(){
        List<Subscription> data
        List result = []
        boolean showSubscriber = params.showSubscriber == 'true'
        boolean showConnectedObjs = params.showConnectedObjs == 'true'
        Map queryParams = [:]
        if(params.status){
            queryParams.status = JSON.parse(params.status).collect{Long.parseLong(it)}

        }

        queryParams.showSubscriber = showSubscriber
        queryParams.showConnectedObjs = showConnectedObjs

        data = compareService.getMySubscriptions(queryParams)

        if(accessService.checkPerm("ORG_CONSORTIUM")) {
            if (showSubscriber) {
                List parents = data.clone()
                Set<RefdataValue> subscriberRoleTypes = [RDStore.OR_SUBSCRIBER, RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIBER_CONS_HIDDEN, RDStore.OR_SUBSCRIBER_COLLECTIVE]
                data.addAll(Subscription.executeQuery('select s from Subscription s join s.orgRelations oo where s.instanceOf in (:parents) and oo.roleType in :subscriberRoleTypes order by oo.org.sortname asc, oo.org.name asc', [parents: parents, subscriberRoleTypes: subscriberRoleTypes]))
            }
        }

        if (showConnectedObjs){
            data.addAll(linksGenerationService.getAllLinkedSubscriptions(data, contextService.user))
        }

        if(data) {
            data.each { Subscription s ->
                result.add([value: s.id, text: s.dropdownNamingConvention()])
            }

            result.sort{it.text}
        }

        withFormat {
            json {
                render result as JSON
            }
        }
    }

    def adjustCompareLicenseList(){
        List<License> data
        List result = []
        boolean showSubscriber = params.showSubscriber == 'true'
        boolean showConnectedLics = params.showConnectedLics == 'true'
        Map queryParams = [:]
        if(params.status){
            queryParams.status = JSON.parse(params.status).collect{Long.parseLong(it)}

        }

        queryParams.showSubscriber = showSubscriber
        queryParams.showConnectedLics = showConnectedLics

        data = compareService.getMyLicenses(queryParams)

        if(accessService.checkPerm("ORG_CONSORTIUM")) {
            if (showSubscriber) {
                List parents = data.clone()
                Set<RefdataValue> subscriberRoleTypes = [RDStore.OR_LICENSEE_CONS, RDStore.OR_LICENSEE]
                data.addAll(License.executeQuery('select l from License l join l.orgRelations oo where l.instanceOf in (:parents) and oo.roleType in :subscriberRoleTypes order by oo.org.sortname asc, oo.org.name asc', [parents: parents, subscriberRoleTypes: subscriberRoleTypes]))
            }
        }

        if (showConnectedLics){

        }

        if(data) {
            data.each { License l ->
                result.add([value: l.id, text: l.dropdownNamingConvention()])
            }
            result.sort{it.text}
        }
        withFormat {
            json {
                render result as JSON
            }
        }
    }

    def notifyProfiler() {
        Map<String, Object> result = [status: 'failed']
        SessionCacheWrapper cache = contextService.getSessionCache()
        ProfilerUtils pu = (ProfilerUtils) cache.get(ProfilerUtils.SYSPROFILER_SESSION)

        if (pu) {
            long delta = pu.stopSimpleBench(params.uri)

            SystemProfiler.update(delta, params.uri)

            result.uri = params.uri
            result.delta = delta
            result.status = 'ok'
        }

        render result as JSON
    }
}
