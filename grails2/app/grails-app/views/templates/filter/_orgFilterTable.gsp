<%@ page import="de.laser.Subscription; de.laser.PersonRole; de.laser.RefdataValue; de.laser.finance.CostItem; de.laser.ReaderNumber; de.laser.Contact; com.k_int.kbplus.auth.User; com.k_int.kbplus.auth.Role; grails.plugin.springsecurity.SpringSecurityUtils; de.laser.SubscriptionsQueryService; de.laser.helper.RDConstants; de.laser.helper.RDStore; java.text.SimpleDateFormat; de.laser.License; de.laser.Org; de.laser.OrgRole; de.laser.SurveyOrg; de.laser.SurveyResult; de.laser.OrgSetting" %>
<laser:serviceInjection/>
<g:if test="${'surveySubCostItem' in tmplConfigShow}">
    <g:set var="oldCostItem" value="${0.0}"/>
    <g:set var="oldCostItemAfterTax" value="${0.0}"/>
    <g:set var="sumOldCostItemAfterTax" value="${0.0}"/>
    <g:set var="sumOldCostItem" value="${0.0}"/>
</g:if>

<g:if test="${'surveyCostItem' in tmplConfigShow}">
    <g:set var="sumNewCostItem" value="${0.0}"/>
    <g:set var="sumSurveyCostItem" value="${0.0}"/>
    <g:set var="sumNewCostItemAfterTax" value="${0.0}"/>
    <g:set var="sumSurveyCostItemAfterTax" value="${0.0}"/>
</g:if>

<table id="${tableID ?: ''}" class="ui sortable celled la-table table">
    <g:set var="sqlDateToday" value="${new java.sql.Date(System.currentTimeMillis())}"/>
    <thead>
    <tr>
        <g:if test="${tmplShowCheckbox}">
            <th>
                <g:if test="${orgList}">
                    <g:checkBox name="orgListToggler" id="orgListToggler" checked="false"/>
                </g:if>
            </th>
        </g:if>

        <g:each in="${tmplConfigShow}" var="tmplConfigItem" status="i">

            <g:if test="${tmplConfigItem.equalsIgnoreCase('lineNumber')}">
                <th>${message(code: 'sidewide.number')}</th>
            </g:if>

            <g:if test="${tmplConfigItem.equalsIgnoreCase('sortname')}">
                <g:sortableColumn title="${message(code: 'org.sortname.label')}"
                                  property="lower(o.sortname)" params="${request.getParameterMap()}"/>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('shortname')}">
                <g:sortableColumn title="${message(code: 'org.shortname.label')}"
                                  property="lower(o.shortname)" params="${request.getParameterMap()}"/>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('name')}">
                <g:sortableColumn title="${message(code: 'org.fullName.label')}" property="lower(o.name)"
                                  params="${request.getParameterMap()}"/>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('mainContact')}">
                <th>${message(code: 'org.mainContact.label')}</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('hasInstAdmin')}">
                <th>${message(code: 'org.hasInstAdmin.label')}</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('legalInformation')}">
                <th class="la-no-uppercase">
                    <span class="la-popup-tooltip la-delay"
                          data-content="${message(code: 'org.legalInformation.tooltip')}">
                        <i class="handshake outline icon"></i>
                    </span>
                </th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('publicContacts')}">
                <th>${message(code: 'org.publicContacts.label')}</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('privateContacts')}">
                <th>${message(code: 'org.privateContacts.label')}</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('currentFTEs')}">
                <th class="la-th-wrap">${message(code: 'org.currentFTEs.label')}</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('numberOfSubscriptions')}">
                <th class="la-th-wrap">${message(code: 'org.subscriptions.label')}</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('numberOfSurveys')}">
                <th class="la-th-wrap">${message(code: 'survey.active')}</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('identifier')}">
                <th>Identifier</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('wibid')}">
                <th>WIB</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('isil')}">
                <th>ISIL</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('type')}">
                <th>${message(code: 'default.type.label')}</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('sector')}">
                <th>${message(code: 'org.sector.label')}</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('region')}">
                <th>${message(code: 'org.region.label')}</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('libraryNetwork')}">
                <th class="la-th-wrap la-hyphenation">${message(code: 'org.libraryNetworkTableHead.label')}</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('consortia')}">
                <th class="la-th-wrap la-hyphenation">${message(code: 'consortium.label')}</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('libraryType')}">
                <th>${message(code: 'org.libraryType.label')}</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('country')}">
                <th>${message(code: 'org.country.label')}</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('consortiaToggle')}">
                <th class="la-th-wrap la-hyphenation">${message(code: 'org.consortiaToggle.label')}</th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('addSubMembers')}">
                <th>
                    ${message(code: 'subscription.details.addMembers.option.package.label')}
                </th>
                <th>
                    ${message(code: 'subscription.details.addMembers.option.issueEntitlement.label')}
                </th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('surveySubInfo')}">
                <th>
                    ${message(code: 'subscription')}
                </th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('surveySubInfoStartEndDate')}">
                <th>
                    ${message(code: 'surveyProperty.subDate')}
                </th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('surveySubInfoStatus')}">
                <th>
                    ${message(code: 'subscription.status.label')}
                </th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('surveySubCostItem')}">
                <th>
                    <g:set var="costItemElements"
                           value="${RefdataValue.executeQuery('select ciec.costItemElement from CostItemElementConfiguration ciec where ciec.forOrganisation = :org', [org: institution])}"/>

                    <g:form action="surveyCostItems" method="post"
                            params="${params + [id: surveyInfo.id, surveyConfigID: params.surveyConfigID, tab: params.tab]}">
                        <laser:select name="selectedCostItemElement"
                                      from="${costItemElements}"
                                      optionKey="id"
                                      optionValue="value"
                                      value="${selectedCostItemElement}"
                                      class="ui dropdown"
                                      onchange="this.form.submit()"/>
                    </g:form>
                </th>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('surveyCostItem') && surveyInfo.type.id in [RDStore.SURVEY_TYPE_RENEWAL.id, RDStore.SURVEY_TYPE_SUBSCRIPTION.id]}">
                <th>
                    ${message(code: 'surveyCostItems.label')}
                </th>
                <th></th>
            </g:if>

        </g:each>
    </tr>
    </thead>
    <tbody>
    <g:each in="${orgList}" var="org" status="i">

        <g:if test="${controllerName in ["survey"]}">
            <g:set var="existSubforOrg"
                   value="${Subscription.get(surveyConfig.subscription?.id)?.getDerivedSubscribers()?.id?.contains(org?.id)}"/>

            <g:set var="orgSub" value="${surveyConfig.subscription?.getDerivedSubscriptionBySubscribers(org)}"/>
        </g:if>

        <g:if test="${tmplDisableOrgIds && (org.id in tmplDisableOrgIds)}">
            <tr class="disabled">
        </g:if>
        <g:else>
            <tr>
        </g:else>

        <g:if test="${tmplShowCheckbox}">
            <td>
                <g:if test="${controllerName in ["survey"] && actionName == "surveyCostItems"}">
                    <g:if test="${CostItem.findBySurveyOrgAndCostItemStatusNotEqual(SurveyOrg.findBySurveyConfigAndOrg(surveyConfig, org), RDStore.COST_ITEM_DELETED)}">
                        <g:checkBox name="selectedOrgs" value="${org.id}" checked="false"/>
                    </g:if>
                </g:if>
                <g:else>
                    <g:if test="${comboType == RDStore.COMBO_TYPE_DEPARTMENT}">
                        <g:if test="${org.isEmpty()}">
                            <g:checkBox name="selectedOrgs" value="${org.id}" checked="false"/>
                        </g:if>
                    </g:if>
                    <g:else>
                        <g:checkBox name="selectedOrgs" value="${org.id}" checked="false"/>
                    </g:else>
                </g:else>
            </td>
        </g:if>

        <g:each in="${tmplConfigShow}" var="tmplConfigItem">

            <g:if test="${tmplConfigItem.equalsIgnoreCase('lineNumber')}">
                <td class="center aligned">
                    ${(params.int('offset') ?: 0) + i + 1}<br>
                </td>
            </g:if>

            <g:if test="${tmplConfigItem.equalsIgnoreCase('sortname')}">
                <td>
                    ${org.sortname}
                </td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('shortname')}">
                <td>
                    <g:if test="${tmplDisableOrgIds && (org.id in tmplDisableOrgIds)}">
                        <g:if test="${org.shortname}">
                            ${fieldValue(bean: org, field: "shortname")}
                        </g:if>
                    </g:if>
                    <g:else>
                        <g:link controller="organisation" action="show" id="${org.id}">
                            <g:if test="${org.shortname}">
                                ${fieldValue(bean: org, field: "shortname")}
                            </g:if>
                        </g:link>
                    </g:else>
                </td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('name')}">
                <td class="la-main-object">
                    <g:if test="${tmplDisableOrgIds && (org.id in tmplDisableOrgIds)}">
                        ${fieldValue(bean: org, field: "name")} <br>
                        <g:if test="${org.shortname && !tmplConfigItem.equalsIgnoreCase('shortname')}">
                            (${fieldValue(bean: org, field: "shortname")})
                        </g:if>
                    </g:if>
                    <g:else>
                        <g:link controller="organisation" action="show" id="${org.id}">
                            ${fieldValue(bean: org, field: "name")}
                            <g:if test="${org.shortname && !tmplConfigItem.equalsIgnoreCase('shortname')}">
                                <br>
                                (${fieldValue(bean: org, field: "shortname")})
                            </g:if>
                        </g:link>
                    </g:else>
                    <g:if test="${org.getCustomerType() in ['ORG_INST', 'ORG_INST_COLLECTIVE']}">
                        <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="bottom center"
                              data-content="${org.getCustomerTypeI10n()}">
                            <i class="chess rook grey icon"></i>
                        </span>
                    </g:if>
                </td>
            </g:if>

            <g:if test="${tmplConfigItem.equalsIgnoreCase('mainContact')}">
                <td>
                    <g:each in="${PersonRole.findAllByFunctionTypeAndOrg(RDStore.PRS_FUNC_GENERAL_CONTACT_PRS, org)}"
                            var="personRole">
                        <g:if test="${personRole.prs.isPublic || (!personRole.prs.isPublic && personRole?.prs?.tenant?.id == contextService.getOrg().id)}">
                            <div class="item js-copyTriggerParent">
                                <%--
                                <g:if test="${! personRole.prs.isPublic}">
                                    <span class="la-popup-tooltip la-delay" data-content="${message(code:'address.private')}" data-position="top right">
                                        <i class="address card outline icon"></i>
                                    </span>
                                </g:if>
                                <g:else>
                                    <i class="address card icon"></i>
                                </g:else>
                                --%>
                                ${personRole?.getPrs()?.getFirst_name()} ${personRole?.getPrs()?.getLast_name()} <br/>

                                <g:each in="${Contact.findAllByPrsAndContentType(
                                        personRole.getPrs(),
                                        RDStore.CCT_EMAIL
                                )}" var="email">
                                    <i class="ui icon envelope outline la-list-icon js-copyTrigger"></i>
                                    <span data-position="right center"
                                          class="la-popup-tooltip la-delay"
                                          data-content="Mail senden an ${personRole?.getPrs()?.getFirst_name()} ${personRole?.getPrs()?.getLast_name()}">
                                        <a class="js-copyTopic" href="mailto:${email?.content}">${email?.content}</a>
                                    </span><br>
                                </g:each>
                                <g:each in="${Contact.findAllByPrsAndContentType(
                                        personRole.getPrs(),
                                        RDStore.CCT_PHONE
                                )}" var="telNr">
                                    <i class="ui icon phone"></i>
                                    <span data-position="right center">
                                        ${telNr?.content}
                                    </span><br>
                                </g:each>

                            </div>
                        </g:if>
                    </g:each>
                </td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('hasInstAdmin')}">
                <td>
                    <%
                        String instAdminIcon = '<i class="large red times icon"></i>'
                        List<User> users = User.executeQuery('select uo.user from UserOrg uo where uo.org = :org and uo.formalRole = :instAdmin', [org: org, instAdmin: Role.findByAuthority('INST_ADM')])
                        if (users)
                            instAdminIcon = '<i class="large green check icon"></i>'
                    %>
                    <g:if test="${contextService.user.hasAffiliation('INST_ADM') || SpringSecurityUtils.ifAllGranted("ROLE_ADMIN")}">
                        <br><g:link controller="organisation" action="users"
                                    params="${[id: org.id]}">${raw(instAdminIcon)}</g:link>
                    </g:if>
                    <g:else>
                        ${raw(instAdminIcon)}
                    </g:else>
                </td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('legalInformation')}">
                <td>
                    <g:if test="${org.createdBy && org.legallyObligedBy}">
                        <span class="la-popup-tooltip la-delay" data-position="top right"
                              data-content="${message(code: 'org.legalInformation.1.tooltip', args: [org.createdBy, org.legallyObligedBy])}">
                            <i class="ui icon green check circle"></i>
                        </span>
                    </g:if>
                    <g:elseif test="${org.createdBy}">
                        <span class="la-popup-tooltip la-delay" data-position="top right"
                              data-content="${message(code: 'org.legalInformation.2.tooltip', args: [org.createdBy])}">
                            <i class="ui icon grey outline circle"></i>
                        </span>
                    </g:elseif>
                    <g:elseif test="${org.legallyObligedBy}">
                        <span class="la-popup-tooltip la-delay" data-position="top right"
                              data-content="${message(code: 'org.legalInformation.3.tooltip', args: [org.legallyObligedBy])}">
                            <i class="ui icon red question mark"></i>
                        </span>
                    </g:elseif>
                </td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('publicContacts')}">
                <td>
                    <g:each in="${org?.prsLinks?.toSorted()}" var="pl">
                        <g:if test="${pl.functionType?.value && pl.prs.isPublic}">
                            <g:render template="/templates/cpa/person_details" model="${[
                                    personRole          : pl,
                                    tmplShowDeleteButton: false,
                                    tmplConfigShow      : ['E-Mail', 'Mail', 'Phone'],
                                    controller          : 'organisation',
                                    action              : 'show',
                                    id                  : org.id
                            ]}"/>
                        </g:if>
                    </g:each>
                </td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('privateContacts')}">
                <td>
                    <g:set var="visiblePrivateContacts" value="[]"/>
                    <g:each in="${org?.prsLinks?.toSorted()}" var="pl">
                        <g:if test="${pl?.functionType?.value && (!pl.prs.isPublic) && pl?.prs?.tenant?.id == contextService.getOrg().id}">

                            <g:if test="${!visiblePrivateContacts.contains(pl.prs.id)}">
                                <g:set var="visiblePrivateContacts" value="${visiblePrivateContacts + pl.prs.id}"/>

                                <g:render template="/templates/cpa/person_full_details" model="${[
                                        person                 : pl.prs,
                                        personContext          : org,
                                        tmplShowDeleteButton   : true,
                                        tmplShowAddPersonRoles : false,
                                        tmplShowAddContacts    : false,
                                        tmplShowAddAddresses   : false,
                                        tmplShowFunctions      : true,
                                        tmplShowPositions      : true,
                                        tmplShowResponsiblities: false,
                                        tmplConfigShow         : ['E-Mail', 'Mail', 'Phone'],
                                        controller             : 'organisation',
                                        action                 : 'show',
                                        id                     : org.id,
                                        editable               : true
                                ]}"/>
                            </g:if>
                        </g:if>
                    <%--
                    <g:if test="${pl?.functionType?.value && (! pl.prs.isPublic) && pl?.prs?.tenant?.id == contextService.getOrg().id}">
                        <g:render template="/templates/cpa/person_details" model="${[
                                personRole          : pl,
                                tmplShowDeleteButton: false,
                                tmplConfigShow      : ['E-Mail', 'Mail', 'Phone'],
                                controller          : 'organisation',
                                action              : 'show',
                                id                  : org.id
                        ]}"/>
                    </g:if>
                    --%>
                    </g:each>
                </td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('currentFTEs')}">
                <td>
                    <g:each in="${ReaderNumber.findAllByOrgAndReferenceGroup(org, RefdataValue.getByValueAndCategory('Students', RDConstants.NUMBER_TYPE).getI10n('value'))?.sort {
                        it.type?.getI10n("value")
                    }}" var="fte">
                        <g:if test="${fte.startDate <= sqlDateToday && fte.endDate >= sqlDateToday}">
                            ${fte.type?.getI10n("value")} : ${fte.number} <br>
                        </g:if>
                    </g:each>
                </td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('numberOfSubscriptions')}">
                <td class="center aligned">
                    <div class="la-flexbox">
                        <% (base_qry, qry_params) = subscriptionsQueryService.myInstitutionCurrentSubscriptionsBaseQuery([org: org, actionName: actionName, status: RDStore.SUBSCRIPTION_CURRENT.id], contextService.org)
                        def numberOfSubscriptions = Subscription.executeQuery("select s.id " + base_qry, qry_params).size()
                        %>
                        <g:if test="${actionName == 'manageMembers'}">
                            <g:link controller="myInstitution" action="manageConsortiaSubscriptions"
                                    params="${[member: org.id, status: RDStore.SUBSCRIPTION_CURRENT.id]}">
                                <div class="ui circular label">
                                    ${numberOfSubscriptions}
                                </div>
                            </g:link>
                        </g:if>
                        <g:else>
                            <g:link controller="myInstitution" action="currentSubscriptions"
                                    params="${[identifier: org.globalUID]}"
                                    title="${message(code: 'org.subscriptions.tooltip', args: [org.name])}">
                                <div class="ui circular label">
                                    ${numberOfSubscriptions}
                                </div>
                            </g:link>
                        </g:else>
                    </div>
                </td>
            </g:if>
                <g:if test="${tmplConfigItem.equalsIgnoreCase('numberOfSurveys')}">
                    <td class="center aligned">
                        <div class="la-flexbox">

                            <g:set var="participantSurveys"
                                   value="${SurveyResult.findAllByOwnerAndParticipantAndEndDateGreaterThanEquals(contextService.org, org, new Date(System.currentTimeMillis()))}"/>
                            <g:set var="numberOfSurveys"
                                   value="${participantSurveys.groupBy { it.surveyConfig.id }.size()}"/>
                            <%
                                def finishColor = ""
                                def countFinish = 0
                                def countNotFinish = 0

                                participantSurveys.each {
                                    if (it.isResultProcessed()) {
                                        countFinish++
                                    } else {
                                        countNotFinish++
                                    }
                                }
                                if (countFinish > 0 && countNotFinish == 0) {
                                    finishColor = "green"
                                } else if (countFinish > 0 && countNotFinish > 0) {
                                    finishColor = "yellow"
                                } else {
                                    finishColor = "red"
                                }
                            %>

                            <g:link controller="myInstitution" action="manageParticipantSurveys"
                                    id="${org.id}">
                                <div class="ui circular ${finishColor} label">
                                    ${numberOfSurveys}
                                </div>
                            </g:link>
                        </div>
                    </td>
                </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('identifier')}">
                <td><g:if test="${org.ids}">
                    <div class="ui list">
                        <g:each in="${org.ids?.sort { it?.ns?.ns }}" var="id"><div
                                class="item">${id.ns.ns}: ${id.value}</div></g:each>
                    </div>
                </g:if></td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('wibid')}">
                <td>${org.getIdentifiersByType('wibid')?.value?.join(', ')}</td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('isil')}">
                <td>${org.getIdentifiersByType('isil')?.value?.join(', ')}</td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('type')}">
                <td>
                    <g:each in="${org.orgType?.sort { it?.getI10n("value") }}" var="type">
                        ${type.getI10n("value")}
                    </g:each>
                </td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('sector')}">
                <td>${org.sector?.getI10n('value')}</td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('region')}">
                <td>${org.region?.getI10n('value')}</td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('libraryNetwork')}">
                <td>${org.libraryNetwork?.getI10n('value')}</td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('consortia')}">
                <td>
                    <g:each in="${org.outgoingCombos}" var="combo">
                        ${combo.toOrg.name}<br><br>
                    </g:each>
                </td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('libraryType')}">
                <td>${org.libraryType?.getI10n('value')}</td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('country')}">
                <td>${org.country?.getI10n('value')}</td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('consortiaToggle')}">
                <td>
                <%-- here: switch if in consortia or not --%>
                    <g:if test="${!consortiaMemberIds.contains(org.id)}">
                        <g:link class="ui icon positive button la-popup-tooltip la-delay"
                                data-content="${message(code: 'org.consortiaToggle.add.label')}"
                                controller="organisation"
                                action="toggleCombo" params="${params + [direction: 'add', fromOrg: org.id]}">
                            <i class="plus icon"></i>
                        </g:link>
                    </g:if>
                    <g:elseif test="${consortiaMemberIds.contains(org.id)}">
                        <g:link class="ui icon negative button la-popup-tooltip la-delay js-open-confirm-modal"
                                data-confirm-tokenMsg="${message(code: "confirm.dialog.unlink.consortiaToggle", args: [org.name])}"
                                data-confirm-term-how="unlink"
                                data-content="${message(code: 'org.consortiaToggle.remove.label')}"
                                controller="organisation" action="toggleCombo"
                                params="${params + [direction: 'remove', fromOrg: org.id]}">
                            <i class="minus icon"></i>
                        </g:link>
                    </g:elseif>
                </td>
            </g:if>

            <g:if test="${tmplConfigItem.equalsIgnoreCase('addSubMembers')}">
                <g:if test="${subInstance?.packages}">
                    <td><g:each in="${subInstance?.packages}">
                        <g:checkBox type="text" id="selectedPackage_${org.id + it.pkg.id}"
                                    name="selectedPackage_${org.id + it.pkg.id}" value="1"
                                    checked="false"
                                    onclick="checkselectedPackage(${org.id + it.pkg.id});"/> ${it.pkg.name}<br>
                    </g:each>
                    </td>
                    <td><g:each in="${subInstance?.packages}">
                        <g:checkBox type="text" id="selectedIssueEntitlement_${org.id + it.pkg.id}"
                                    name="selectedIssueEntitlement_${org.id + it.pkg.id}" value="1" checked="false"
                                    onclick="checkselectedIssueEntitlement(${org.id + it.pkg.id});"/> ${it.pkg.name}<br>
                    </g:each>
                    </td>
                </g:if><g:else>
                <td>${message(code: 'subscription.details.addMembers.option.noPackage.label', args: [subInstance?.name])}</td>
                <td>${message(code: 'subscription.details.addMembers.option.noPackage.label', args: [subInstance?.name])}</td>
            </g:else>
            </g:if>

            <g:if test="${tmplConfigItem.equalsIgnoreCase('surveySubInfo')}">
                <td>
                    <g:if test="${existSubforOrg}">

                        <g:if test="${orgSub.isCurrentMultiYearSubscriptionNew()}">
                            <g:message code="surveyOrg.perennialTerm.available"/>
                            <br>
                            <g:link controller="subscription" action="show"
                                    id="${orgSub.id}">
                                ${orgSub.name}
                            </g:link>
                        </g:if>
                        <g:else>
                            <g:link controller="subscription" action="show"
                                    id="${orgSub.id}">
                                ${orgSub.name}
                            </g:link>
                        </g:else>

                        <semui:xEditableAsIcon owner="${orgSub}" class="ui icon center aligned" iconClass="info circular inverted" field="comment" type="textarea" overwriteEditable="${false}"/>

                    </g:if>
                </td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('surveySubInfoStartEndDate')}">
                <td>
                    <g:if test="${existSubforOrg}">
                        <g:if test="${orgSub.isCurrentMultiYearSubscriptionNew()}">
                            <g:message code="surveyOrg.perennialTerm.available"/>
                            <br>
                            <g:link controller="subscription" action="show"
                                    id="${orgSub.id}">
                                <g:formatDate formatName="default.date.format.notime"
                                              date="${orgSub.startDate}"/><br>
                                <g:formatDate formatName="default.date.format.notime"
                                              date="${orgSub.endDate}"/>
                            </g:link>
                        </g:if>
                        <g:else>
                            <g:link controller="subscription" action="show"
                                    id="${orgSub.id}">
                                <g:formatDate formatName="default.date.format.notime"
                                              date="${orgSub.startDate}"/><br>
                                <g:formatDate formatName="default.date.format.notime"
                                              date="${orgSub.endDate}"/>
                            </g:link>
                        </g:else>

                        <semui:xEditableAsIcon owner="${orgSub}" class="ui icon center aligned" iconClass="info circular inverted" field="comment" type="textarea" overwriteEditable="${false}"/>

                    </g:if>
                </td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('surveySubInfoStatus')}">
                <td>
                    <g:if test="${existSubforOrg}">
                        <g:if test="${orgSub.isCurrentMultiYearSubscriptionNew()}">
                            <g:message code="surveyOrg.perennialTerm.available"/>
                            <br>
                            <g:link controller="subscription" action="show"
                                    id="${orgSub.id}">
                                ${orgSub.status.getI10n('value')}
                            </g:link>
                        </g:if>
                        <g:else>
                            <g:link controller="subscription" action="show"
                                    id="${orgSub.id}">
                                ${orgSub.status.getI10n('value')}
                            </g:link>
                        </g:else>

                    </g:if>
                </td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('surveySubCostItem')}">
                <td class="center aligned x">

                    <g:set var="oldCostItem" value="${0.0}"/>
                    <g:set var="oldCostItemAfterTax" value="${0.0}"/>
                <g:if test="${existSubforOrg}">
                    <g:if test="${surveyConfig.subSurveyUseForTransfer && orgSub.isCurrentMultiYearSubscriptionNew()}">
                        <g:message code="surveyOrg.perennialTerm.available"/>
                    </g:if>
                    <g:else>
                        <g:each in="${CostItem.findAllBySubAndOwnerAndCostItemStatusNotEqual(orgSub, institution, RDStore.COST_ITEM_DELETED)}"
                                var="costItem">

                            <g:if test="${costItem.costItemElement?.id?.toString() == selectedCostItemElement}">

                                <strong><g:formatNumber number="${costItem.costInBillingCurrencyAfterTax}"
                                                   minFractionDigits="2"
                                                   maxFractionDigits="2" type="number"/></strong>

                                (<g:formatNumber number="${costItem.costInBillingCurrency}" minFractionDigits="2"
                                                 maxFractionDigits="2" type="number"/>)

                                ${(costItem.billingCurrency?.getI10n('value')?.split('-')).first()}

                                <g:set var="sumOldCostItem"
                                       value="${sumOldCostItem + costItem.costInBillingCurrency?:0}"/>
                                <g:set var="sumOldCostItemAfterTax"
                                       value="${sumOldCostItemAfterTax + costItem.costInBillingCurrencyAfterTax?:0}"/>

                                <g:set var="oldCostItem" value="${costItem.costInBillingCurrency?:null}"/>
                                <g:set var="oldCostItemAfterTax" value="${costItem.costInBillingCurrencyAfterTax?:null}"/>

                            </g:if>
                        </g:each>
                    </g:else>
                </g:if>

                </td>
            </g:if>
            <g:if test="${tmplConfigItem.equalsIgnoreCase('surveyCostItem') && surveyInfo.type.id in [RDStore.SURVEY_TYPE_RENEWAL.id, RDStore.SURVEY_TYPE_SUBSCRIPTION.id]}">
                <td class="x">

                    <g:if test="${surveyConfig.subSurveyUseForTransfer && orgSub && orgSub.isCurrentMultiYearSubscriptionNew()}">
                        <g:message code="surveyOrg.perennialTerm.available"/>
                    </g:if>
                    <g:else>

                        <g:set var="costItem" scope="request"
                               value="${CostItem.findBySurveyOrgAndCostItemStatusNotEqual(SurveyOrg.findBySurveyConfigAndOrg(surveyConfig, org), RDStore.COST_ITEM_DELETED)}"/>

                        <g:if test="${costItem}">

                            <strong><g:formatNumber number="${costItem.costInBillingCurrencyAfterTax}" minFractionDigits="2"
                                               maxFractionDigits="2" type="number"/></strong>

                            (<g:formatNumber number="${costItem.costInBillingCurrency}" minFractionDigits="2"
                                             maxFractionDigits="2" type="number"/>)

                            ${(costItem.billingCurrency?.getI10n('value')?.split('-')).first()}

                            <g:set var="sumSurveyCostItem"
                                   value="${sumSurveyCostItem + costItem.costInBillingCurrency?:0}"/>
                            <g:set var="sumSurveyCostItemAfterTax"
                                   value="${sumSurveyCostItemAfterTax + costItem.costInBillingCurrencyAfterTax?:0}"/>

                            <g:if test="${oldCostItem || oldCostItemAfterTax}">
                                <br><strong><g:formatNumber number="${((costItem.costInBillingCurrencyAfterTax-oldCostItemAfterTax)/oldCostItemAfterTax)*100}"
                                                       minFractionDigits="2"
                                                       maxFractionDigits="2" type="number"/>%</strong>

                                (<g:formatNumber number="${((costItem.costInBillingCurrency-oldCostItem)/oldCostItem)*100}" minFractionDigits="2"
                                                 maxFractionDigits="2" type="number"/>%)
                            </g:if>

                            <br>
                            <g:if test="${costItem.startDate || costItem.endDate}">
                                (${formatDate(date: costItem.startDate, format: message(code: 'default.date.format.notimeShort'))} - ${formatDate(date: costItem.endDate, format: message(code: 'default.date.format.notimeShort'))})
                            </g:if>

                            <g:link onclick="addEditSurveyCostItem(${params.id}, ${surveyConfig.id}, ${org.id}, ${costItem.id})"
                                    class="ui icon circular button right floated trigger-modal">
                                <i class="write icon"></i>
                            </g:link>
                        </g:if>
                        <g:else>
                            <g:link onclick="addEditSurveyCostItem(${params.id}, ${surveyConfig.id}, ${org.id}, ${null})"
                                    class="ui icon circular button right floated trigger-modal">
                                <i class="write icon"></i>
                            </g:link>
                        </g:else>

                    </g:else>
                </td>

                <td class="center aligned">
                    <g:set var="costItem" scope="request"
                           value="${CostItem.findBySurveyOrgAndCostItemStatusNotEqual(SurveyOrg.findBySurveyConfigAndOrg(surveyConfig, org), RDStore.COST_ITEM_DELETED)}"/>
                    <g:if test="${costItem && costItem.costDescription}">

                        <div class="ui icon la-popup-tooltip la-delay" data-content="${costItem.costDescription}">
                            <i class="info circular inverted icon"></i>
                        </div>
                    </g:if>

                </td>
            </g:if>

        </g:each><!-- tmplConfigShow -->
        </tr>
    </g:each><!-- orgList -->
    </tbody>
    <g:if test="${orgList && ('surveySubCostItem' in tmplConfigShow || 'surveyCostItem' in tmplConfigShow)}">
        <tfoot>
        <tr>
            <g:if test="${tmplShowCheckbox}">
                <td></td>
            </g:if>
            <g:each in="${1..(tmplConfigShow?.size()- ('surveySubCostItem' in tmplConfigShow ? 2 : 1))}" var="tmplConfigItem">
                    <td></td>
            </g:each>
            <g:if test="${'surveySubCostItem' in tmplConfigShow}">
                <td>
                    <strong><g:formatNumber number="${sumOldCostItemAfterTax}" minFractionDigits="2"
                                       maxFractionDigits="2" type="number"/></strong>
                    (<g:formatNumber number="${sumOldCostItem}" minFractionDigits="2"
                                     maxFractionDigits="2" type="number"/>)
                </td>
            </g:if>
            <g:if test="${'surveyCostItem' in tmplConfigShow}">
                <td>
                    <strong><g:formatNumber number="${sumSurveyCostItemAfterTax}" minFractionDigits="2"
                                       maxFractionDigits="2" type="number"/></strong>
                    (<g:formatNumber number="${sumSurveyCostItem}" minFractionDigits="2"
                                     maxFractionDigits="2" type="number"/>)

                    <g:if test="${sumOldCostItemAfterTax || sumOldCostItem}">
                        <br><strong><g:formatNumber number="${((sumSurveyCostItemAfterTax-sumOldCostItemAfterTax)/sumOldCostItemAfterTax)*100}"
                                               minFractionDigits="2"
                                               maxFractionDigits="2" type="number"/>%</strong>

                        (<g:formatNumber number="${((sumSurveyCostItem-sumOldCostItem)/sumOldCostItem)*100}" minFractionDigits="2"
                                         maxFractionDigits="2" type="number"/>%)
                    </g:if>
                </td>
                <td></td>
            </g:if>
        </tr>
        </tfoot>
    </g:if>
</table>

<g:if test="${tmplShowCheckbox}">
    <script language="JavaScript">
        $('#orgListToggler').click(function () {
            if ($(this).prop('checked')) {
                $("tr[class!=disabled] input[name=selectedOrgs]").prop('checked', true)
            } else {
                $("tr[class!=disabled] input[name=selectedOrgs]").prop('checked', false)
            }
        })
        <g:if test="${tmplConfigShow?.contains('addSubMembers')}">

        function checkselectedIssueEntitlement(selectedid) {
            if ($('#selectedIssueEntitlement_' + selectedid).prop('checked')) {
                $('#selectedPackage_' + selectedid).prop('checked', false);
            }
        }

        function checkselectedPackage(selectedid) {
            if ($('#selectedPackage_' + selectedid).prop('checked')) {
                $('#selectedIssueEntitlement_' + selectedid).prop('checked', false);
            }

        }

        </g:if>

    </script>

</g:if>
<g:if test="${tmplConfigShow?.contains('surveyCostItem') && surveyInfo.type.id in [RDStore.SURVEY_TYPE_RENEWAL.id, RDStore.SURVEY_TYPE_SUBSCRIPTION.id]}">
    <r:script>
   $('table[id^=costTable] .x .trigger-modal').on('click', function(e) {
                    e.preventDefault();

                    $.ajax({
                        url: $(this).attr('href')
                    }).done( function(data) {
                        $('.ui.dimmer.modals > #costItem_ajaxModal').remove();
                        $('#dynamicModalContainer').empty().html(data);

                        $('#dynamicModalContainer .ui.modal').modal({
                            onVisible: function () {
                                r2d2.initDynamicSemuiStuff('#costItem_ajaxModal');
                                r2d2.initDynamicXEditableStuff('#costItem_ajaxModal');

                                ajaxPostFunc();
                                setupCalendar();
                            },
                            detachable: true,
                            closable: false,
                            transition: 'scale',
                            onApprove : function() {
                                $(this).find('.ui.form').submit();
                                return false;
                            }
                        }).modal('show');
                    })
                });

        function addEditSurveyCostItem(id, surveyConfigID, participant, costItem) {
            event.preventDefault();
            $.ajax({
                url: "<g:createLink controller='survey' action='editSurveyCostItem'/>",
                                data: {
                                    id: id,
                                    surveyConfigID: surveyConfigID,
                                    participant: participant,
                                    costItem: costItem
                                }
            }).done( function(data) {
                $('.ui.dimmer.modals > #modalSurveyCostItem').remove();
                $('#dynamicModalContainer').empty().html(data);

                $('#dynamicModalContainer .ui.modal').modal({
                    onVisible: function () {
                        r2d2.initDynamicSemuiStuff('#modalSurveyCostItem');
                        r2d2.initDynamicXEditableStuff('#modalSurveyCostItem');
                    },
                    detachable: true,
                    closable: false,
                    transition: 'scale',
                    onApprove : function() {
                        $(this).find('.ui.form').submit();
                        return false;
                    }
                }).modal('show');
            })
        };

    </r:script>
</g:if>

