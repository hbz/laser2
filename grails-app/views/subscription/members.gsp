<%@ page import="de.laser.finance.CostItem; de.laser.Links; de.laser.Person; de.laser.interfaces.CalculatedType; de.laser.helper.RDStore; de.laser.Subscription" %>
<laser:serviceInjection />

<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code:'laser')} :
        <g:if test="${accessService.checkPerm("ORG_CONSORTIUM")}">
            <g:message code="subscription.details.consortiaMembers.label"/>
        </g:if>
        <g:elseif test="${accessService.checkPerm("ORG_INST_COLLECTIVE")}">
            <g:message code="subscription.details.collectiveMembers.label"/>
        </g:elseif>
    </title>
</head>
<body>

    <g:render template="breadcrumb" model="${[ params:params ]}"/>

    <semui:controlButtons>
        <semui:exportDropdown>
            <semui:exportDropdownItem>
                <g:if test="${filterSet}">
                    <g:link class="item js-open-confirm-modal"
                            data-confirm-tokenMsg = "${message(code: 'confirmation.content.exportPartial')}"
                            data-confirm-term-how="ok" controller="subscription" action="members"
                            params="${params+[exportXLS:true]}">
                        ${message(code:'default.button.exports.xls')}
                    </g:link>
                </g:if>
                <g:else>
                    <g:link class="item" action="members" params="${params+[exportXLS:true]}">${message(code:'default.button.exports.xls')}</g:link>
                </g:else>
            </semui:exportDropdownItem>
            <semui:exportDropdownItem>
                <g:if test="${filterSet}">
                    <g:link class="item js-open-confirm-modal"
                            data-confirm-tokenMsg = "${message(code: 'confirmation.content.exportPartial')}"
                            data-confirm-term-how="ok" controller="subscription" action="members"
                            params="${params+[format:'csv']}">
                        ${message(code:'default.button.exports.csv')}
                    </g:link>
                </g:if>
                <g:else>
                    <g:link class="item" action="members" params="${params+[format:'csv']}">${message(code:'default.button.exports.csv')}</g:link>
                </g:else>
            </semui:exportDropdownItem>
            <semui:exportDropdownItem>
                <g:if test="${filterSet}">
                    <g:link class="item js-open-confirm-modal"
                            data-confirm-tokenMsg = "${message(code: 'confirmation.content.exportPartial')}"
                            data-confirm-term-how="ok" controller="subscription" action="members"
                            params="${params+[exportIPs:true]}">
                        ${message(code:'subscriptionDetails.members.exportIPs')}
                    </g:link>
                </g:if>
                <g:else>
                    <g:link class="item" action="members" params="${params+[exportIPs:true]}">${message(code:'subscriptionDetails.members.exportIPs')}</g:link>
                </g:else>
                <g:if test="${filterSet}">
                    <g:link class="item js-open-confirm-modal"
                            data-confirm-tokenMsg = "${message(code: 'confirmation.content.exportPartial')}"
                            data-confirm-term-how="ok" controller="subscription" action="members"
                            params="${params+[exportProxys:true]}">
                        ${message(code:'subscriptionDetails.members.exportProxys')}
                    </g:link>
                </g:if>
                <g:else>
                    <g:link class="item" action="members" params="${params+[exportProxys:true]}">${message(code:'subscriptionDetails.members.exportProxys')}</g:link>
                </g:else>
                <g:if test="${filterSet}">
                    <g:link class="item js-open-confirm-modal"
                            data-confirm-tokenMsg = "${message(code: 'confirmation.content.exportPartial')}"
                            data-confirm-term-how="ok" controller="subscription" action="members"
                            params="${params+[exportEZProxys:true]}">
                        ${message(code:'subscriptionDetails.members.exportEZProxys')}
                    </g:link>
                </g:if>
                <g:else>
                    <g:link class="item" action="members" params="${params+[exportEZProxys:true]}">${message(code:'subscriptionDetails.members.exportEZProxys')}</g:link>
                </g:else>
                <g:if test="${filterSet}">
                    <g:link class="item js-open-confirm-modal"
                            data-confirm-tokenMsg = "${message(code: 'confirmation.content.exportPartial')}"
                            data-confirm-term-how="ok" controller="subscription" action="members"
                            params="${params+[exportShibboleths:true]}">
                        ${message(code:'subscriptionDetails.members.exportShibboleths')}
                    </g:link>
                </g:if>
                <g:else>
                    <g:link class="item" action="members" params="${params+[exportShibboleths:true]}">${message(code:'subscriptionDetails.members.exportShibboleths')}</g:link>
                </g:else>
            </semui:exportDropdownItem>
        </semui:exportDropdown>
        <g:render template="actions" />
    </semui:controlButtons>

    <h1 class="ui icon header la-noMargin-top"><semui:headerIcon />
        <semui:xEditable owner="${subscriptionInstance}" field="name" />
        <semui:totalNumber total="${filteredSubChilds.size() ?: 0}"/>
    </h1>
    <semui:anualRings object="${subscriptionInstance}" controller="subscription" action="members" navNext="${navNextSubscription}" navPrev="${navPrevSubscription}"/>

    <g:render template="nav" />
    <g:render template="/templates/filter/javascript" />

    <semui:filter showFilterButton="true">
        <g:form action="members" controller="subscription" params="${[id:params.id]}" method="get" class="ui form">
            <%
                List<List<String>> tmplConfigShow
                if(accessService.checkPerm("ORG_CONSORTIUM"))
                    tmplConfigShow = [['name', 'identifier', 'libraryType', 'subjectGroup'], ['region', 'libraryNetwork','property'], ['subRunTimeMultiYear']]
                else if(accessService.checkPerm("ORG_INST_COLLECTIVE"))
                    tmplConfigShow = [['name', 'identifier'], ['property']]
            %>
            <g:render template="/templates/filter/orgFilter"
                  model="[
                      tmplConfigShow: tmplConfigShow,
                      tmplConfigFormFilter: true,
                      useNewLayouter: true
                  ]"/>
        </g:form>
    </semui:filter>

    <semui:messages data="${flash}" />

    <g:if test="${filteredSubChilds}">
        <table class="ui celled la-table table">
            <thead>
            <tr>
                <th>${message(code:'sidewide.number')}</th>
                <th>${message(code:'default.sortname.label')}</th>
                <th>${message(code:'subscriptionDetails.members.members')}</th>
                <th class="center aligned">
                    <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="bottom center"
                          data-content="${message(code: 'default.previous.label')}">
                        <i class="arrow left icon"></i>
                    </span>
                </th>
                <th>${message(code:'default.startDate.label')}</th>
                <th>${message(code:'default.endDate.label')}</th>
                <th class="center aligned">
                    <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="bottom center"
                          data-content="${message(code: 'default.next.label')}">
                        <i class="arrow right icon"></i>
                    </span>
                </th>
                <th class="center aligned la-no-uppercase">
                    <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="bottom center" data-content="${message(code: 'subscription.linktoLicense')}">
                        <i class="balance scale icon"></i>
                    </span>
                </th>
                <th class="center aligned la-no-uppercase">
                    <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="bottom center" data-content="${message(code: 'subscription.packages.label')}">
                        <i class="gift icon"></i>
                    </span>
                </th>
                <th>${message(code:'default.status.label')}</th>
                <th class="center aligned la-no-uppercase">
                    <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="bottom center"
                          data-content="${message(code: 'subscription.isMultiYear.consortial.label')}">
                        <i class="map icon"></i>
                    </span>
                </th>
                <th class="la-action-info">${message(code:'default.actions.label')}</th>
            </tr>
            </thead>
            <tbody>
            <g:each in="${filteredSubChilds}" status="i" var="zeile">
                <g:set var="sub" value="${zeile.sub}"/>
                <tr>
                    <td>${i + 1}</td>
                    <g:set var="filteredSubscribers" value="${zeile.orgs}" />
                    <g:set var="org" value="${null}" />
                    <g:each in="${filteredSubscribers}" var="subscr">
                        <g:set var="org" value="${subscr}" />
                        <td>
                            ${subscr.sortname}</td>
                        <td>
                            <g:link controller="organisation" action="show" id="${subscr.id}">${subscr}</g:link>

                            <g:if test="${sub.isSlaved}">
                                <span data-position="top right"  class="la-popup-tooltip la-delay" data-content="${message(code:'license.details.isSlaved.tooltip')}">
                                    <i class="thumbtack blue icon"></i>
                                </span>
                            </g:if>

                            <g:if test="${subscr.getCustomerType() in ['ORG_INST', 'ORG_INST_COLLECTIVE']}">
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="bottom center"
                                      data-content="${subscr.getCustomerTypeI10n()}">
                                    <i class="chess rook grey icon"></i>
                                </span>
                            </g:if>

                            <div class="ui list">
                                <g:each in="${Person.getPublicByOrgAndFunc(subscr, 'General contact person')}" var="gcp">
                                    <div class="item">
                                        ${gcp}
                                        (${RDStore.PRS_FUNC_GENERAL_CONTACT_PRS.getI10n('value')})
                                    </div>
                                </g:each>
                                <g:each in="${Person.getPrivateByOrgAndFuncFromAddressbook(subscr, 'General contact person', contextService.getOrg())}" var="gcp">
                                    <div class="item">
                                        ${gcp}
                                        (${RDStore.PRS_FUNC_GENERAL_CONTACT_PRS.getI10n('value')} <i class="address book outline icon" style="display:inline-block"></i>)
                                    </div>
                                </g:each>
                                <g:each in="${Person.getPublicByOrgAndObjectResp(subscr, sub, 'Specific subscription editor')}" var="sse">
                                    <div class="item">
                                        ${sse}
                                        (${RDStore.PRS_RESP_SPEC_SUB_EDITOR.getI10n('value')})
                                    </div>
                                </g:each>
                                <g:each in="${Person.getPrivateByOrgAndObjectRespFromAddressbook(subscr, sub, 'Specific subscription editor', contextService.getOrg())}" var="sse">
                                    <div class="item">
                                        ${sse}
                                        (${RDStore.PRS_RESP_SPEC_SUB_EDITOR.getI10n('value')} <i class="address book outline icon" style="display:inline-block"></i>)
                                    </div>
                                </g:each>
                            </div>
                        </td>
                    </g:each>
                    <g:if test="${! sub.getAllSubscribers()}">
                        <td></td>
                        <td></td>
                    </g:if>
                    <%
                        LinkedHashMap<String, List> links = linksGenerationService.generateNavigation(sub)
                        Subscription navPrevSubMember = (links?.prevLink && links?.prevLink?.size() > 0) ? links?.prevLink[0] : null
                        Subscription navNextSubMember = (links?.nextLink && links?.nextLink?.size() > 0) ? links?.nextLink[0] : null
                    %>
                    <td class="center aligned">
                        <g:if test="${navPrevSubMember}">
                            <g:link controller="subscription" action="show" id="${navPrevSubMember.id}"><i class="arrow left icon"></i></g:link>
                        </g:if>
                        <g:elseif test="${(navPrevSubscription?.size() > 0) && navPrevSubscription[0].getDerivedSubscriptionBySubscribers(org)}">
                            <g:link controller="subscription" class="ui icon js-open-confirm-modal"
                                    data-confirm-tokenMsg="${message(code: "confirm.dialog.linkPrevMemberSub")}"
                                    data-confirm-term-how="ok"
                                    action="linkNextPrevMemberSub"
                                    id="${subscriptionInstance.id}"
                                    params="[prev: true, memberOrg: org.id, memberSubID: sub.id]"><i class="arrow left icon grey"></i></g:link>
                        </g:elseif>
                    </td>
                    <td><g:formatDate formatName="default.date.format.notime" date="${sub.startDate}"/></td>
                    <td><g:formatDate formatName="default.date.format.notime" date="${sub.endDate}"/></td>
                    <td class="center aligned">
                        <g:if test="${navNextSubMember}">
                            <g:link controller="subscription" action="show" id="${navNextSubMember.id}"><i class="arrow right icon"></i></g:link>
                        </g:if>
                        <g:elseif test="${(navNextSubscription?.size() > 0) && navNextSubscription[0].getDerivedSubscriptionBySubscribers(org)}">
                            <g:link controller="subscription" class="ui icon js-open-confirm-modal"
                                    data-confirm-tokenMsg="${message(code: "confirm.dialog.linkNextMemberSub")}"
                                    data-confirm-term-how="ok"
                                    action="linkNextPrevMemberSub"
                                    id="${subscriptionInstance.id}"
                                    params="[next: true, memberOrg: org.id, memberSubID: sub.id]"><i class="arrow right icon grey"></i></g:link>
                        </g:elseif>
                    </td>
                    <g:if test="${accessService.checkPerm("ORG_CONSORTIUM")}">
                        <td class="center aligned">
                            <g:set var="license" value="${Links.findByDestinationSubscriptionAndLinkType(sub,RDStore.LINKTYPE_LICENSE)}"/>
                            <g:if test="${!license}">
                                <g:link controller="subscription" action="linkLicenseMembers" id="${subscriptionInstance.id}" class="ui icon ">
                                    <i class="circular la-light-grey inverted minus icon"></i>
                                </g:link>
                            </g:if>
                            <g:else>
                                <g:link controller="subscription" action="linkLicenseMembers" id="${subscriptionInstance.id}" class="ui icon ">
                                    <i class="circular la-license icon"></i>
                                </g:link>
                            </g:else>
                        </td>
                    </g:if>
                    <g:if test="${accessService.checkPerm("ORG_CONSORTIUM")}">
                        <td class="center aligned">
                            <g:if test="${!sub.packages}">
                                <g:link controller="subscription" action="linkPackagesMembers" id="${subscriptionInstance.id}" class="ui icon ">
                                    <i class="circular la-light-grey inverted minus icon"></i>
                                </g:link>
                            </g:if>
                            <g:else>
                                <g:link controller="subscription" action="linkPackagesMembers" id="${subscriptionInstance.id}" class="ui icon ">
                                    <i class="circular la-package icon"></i>
                                </g:link>
                            </g:else>
                        </td>
                    </g:if>
                    <td>${sub.status.getI10n('value')}</td>
                    <td>
                        <g:if test="${sub.isMultiYear}">
                            <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="bottom center"
                                  data-content="${message(code: 'subscription.isMultiYear.consortial.label')}">
                                <i class="map orange icon"></i>
                            </span>
                        </g:if>
                    </td>
                    <td class="x">

                        <g:link controller="subscription" action="show" id="${sub.id}" class="ui icon button"><i class="write icon"></i></g:link>
                        <g:if test="${sub.isEditableBy(contextService.getUser())}"> <%-- needs to be checked for child subscription because of collective subscriptions! --%>
                            <g:if test="${sub._getCalculatedType() in [CalculatedType.TYPE_PARTICIPATION, CalculatedType.TYPE_PARTICIPATION_AS_COLLECTIVE] && sub.instanceOf._getCalculatedType() == CalculatedType.TYPE_ADMINISTRATIVE}">
                                <g:if test="${sub.orgRelations.find{it.roleType == RDStore.OR_SUBSCRIBER_CONS_HIDDEN}}">
                                    <span  class="la-popup-tooltip la-delay" data-content="${message(code:'subscription.details.hiddenForSubscriber')}">
                                        <g:link class="ui icon button" controller="ajax" action="toggleOrgRole" params="${[id:sub.id]}">
                                            <i class="eye orange icon"></i>
                                        </g:link>
                                    </span>
                                </g:if>
                                <g:else>
                                    <span  class="la-popup-tooltip la-delay" data-content="${message(code:'subscription.details.hideToSubscriber')}">
                                        <g:link class="ui icon orange button" controller="ajax" action="toggleOrgRole" params="${[id:sub.id]}">
                                            <i class="eye icon"></i>
                                        </g:link>
                                    </span>
                                </g:else>
                            </g:if>
                            <g:set var="hasCostItems" value="${CostItem.executeQuery('select ci from CostItem ci where ci.sub = :sub and ci.costItemStatus != :deleted',[sub:sub,deleted:RDStore.COST_ITEM_DELETED])}"/>
                            <g:if test="${!hasCostItems}">
                                <g:link class="ui icon negative button" controller="subscription" action="delete" params="${[id:sub.id]}">
                                    <i class="trash alternate icon"></i>
                                </g:link>
                            </g:if>
                            <g:else>
                                <span class="la-popup-tooltip" data-content="${message(code:'subscription.delete.existingCostItems')}">
                                    <button class="ui disabled icon negative button">
                                        <i class="trash alternate icon"></i>
                                    </button>
                                </span>
                            </g:else>
                        </g:if>

                        <semui:xEditableAsIcon owner="${sub}" class="ui icon center aligned" iconClass="info circular inverted" field="comment" type="textarea"/>
                    </td>
                </tr>
            </g:each>
        </tbody>
        </table>
                <g:render template="/templates/copyEmailaddresses" model="[orgList: filteredSubChilds?.collect {it.orgs}?:[]]"/>
            </g:if>
            <g:else>
                <g:if test="${filterSet}">
                    <br><strong><g:message code="filter.result.empty.object" args="${[message(code:"subscriptionDetails.members.members")]}"/></strong>
                </g:if>
                <g:else>
                <br><strong><g:message code="subscription.details.nomembers.label" args="${args.memberType}"/></strong>
                </g:else>
            </g:else>

        </body>
        </html>

