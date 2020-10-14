<%@ page import="de.laser.Subscription; de.laser.Links; de.laser.interfaces.CalculatedType; de.laser.OrgRole; de.laser.Org; de.laser.helper.RDStore; de.laser.RefdataValue; de.laser.SubscriptionPackage" %>
<%@ page import="grails.plugin.springsecurity.SpringSecurityUtils" %>
<%@ page import="org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes" %>

<laser:serviceInjection />

<g:set var="user" value="${contextService.user}"/>
<g:set var="org" value="${contextService.org}"/>
<%
    List menuArgs
    if(showConsortiaFunctions)
        menuArgs = [message(code:'subscription.details.consortiaMembers.label')]
    else if(showCollectiveFunctions)
        menuArgs = [message(code:'subscription.details.collectiveMembers.label')]
%>

    <g:if test="${actionName in ['index','addEntitlements']}">
        <semui:exportDropdown>
            <semui:exportDropdownItem>
                <g:if test="${filterSet}">
                    <g:link class="item js-open-confirm-modal"
                            data-confirm-tokenMsg="${message(code: 'confirmation.content.exportPartial')}"
                            data-confirm-term-how="ok" action="${actionName}"
                            params="${params + [format: 'csv']}">
                        <g:message code="default.button.exports.csv"/>
                    </g:link>
                </g:if>
                <g:else>
                    <g:link class="item" action="${actionName}" params="${params + [format: 'csv']}">CSV Export</g:link>
                </g:else>
            </semui:exportDropdownItem>
            <semui:exportDropdownItem>
                <g:if test="${filterSet}">
                    <g:link class="item js-open-confirm-modal"
                            data-confirm-tokenMsg="${message(code: 'confirmation.content.exportPartial')}"
                            data-confirm-term-how="ok" action="${actionName}"
                            params="${params + [exportXLSX: true]}">
                        <g:message code="default.button.exports.xls"/>
                    </g:link>
                </g:if>
                <g:else>
                    <g:link class="item" action="${actionName}" params="${params+[exportXLSX: true]}">
                        <g:message code="default.button.exports.xls"/>
                    </g:link>
                </g:else>
            </semui:exportDropdownItem>
            <semui:exportDropdownItem>
                <g:if test="${filterSet}">
                    <g:link  class="item js-open-confirm-modal"
                             data-confirm-tokenMsg = "${message(code: 'confirmation.content.exportPartial')}"
                             data-confirm-term-how="ok"
                             action="${actionName}"
                             id="${params.id}"
                             params="${[exportKBart:true, mode: params.mode, filter: params.filter, asAt: params.asAt]}">KBART Export
                    </g:link>
                </g:if>
                <g:else>
                    <g:link class="item" action="${actionName}" id="${params.id}" params="${[exportKBart:true, mode: params.mode]}">KBART Export</g:link>
                </g:else>
            </semui:exportDropdownItem>
        <%--<semui:exportDropdownItem>
                <g:link class="item" controller="subscription" action="index" id="${subscriptionInstance.id}" params="${params + [format:'json']}">JSON</g:link>
            </semui:exportDropdownItem>
            <semui:exportDropdownItem>
                <g:link class="item" controller="subscription" action="index" id="${subscriptionInstance.id}" params="${params + [format:'xml']}">XML</g:link>
            </semui:exportDropdownItem>--%>
        </semui:exportDropdown>
</g:if>
<g:if test="${accessService.checkMinUserOrgRole(user,org,'INST_EDITOR')}">
    <semui:actionsDropdown>
        <g:if test="${editable || accessService.checkPermAffiliation('ORG_INST,ORG_CONSORTIUM','INST_EDITOR')}">
            <semui:actionsDropdownItem message="task.create.new" data-semui="modal" href="#modalCreateTask" />
            <semui:actionsDropdownItem message="template.documents.add" data-semui="modal" href="#modalCreateDocument" />
        </g:if>
        <semui:actionsDropdownItem message="template.addNote" data-semui="modal" href="#modalCreateNote" />
        <g:if test="${editable || accessService.checkPermAffiliation('ORG_INST,ORG_CONSORTIUM','INST_EDITOR')}">
            <div class="divider"></div>
            <g:if test="${(accessService.checkPerm("ORG_INST") && subscriptionInstance._getCalculatedType() == Subscription.TYPE_LOCAL) || (accessService.checkPerm("ORG_CONSORTIUM") && subscriptionInstance._getCalculatedType() == Subscription.TYPE_CONSORTIAL)}">
                <semui:actionsDropdownItem controller="subscription" action="copySubscription" params="${[sourceObjectId: genericOIDService.getOID(subscriptionInstance), copyObject: true]}" message="myinst.copySubscription" />
            </g:if>
            <g:else>
                <semui:actionsDropdownItemDisabled controller="subscription" action="copySubscription" params="${[sourceObjectId: genericOIDService.getOID(subscriptionInstance), copyObject: true]}" message="myinst.copySubscription" />
            </g:else>

            <g:if test="${(accessService.checkPerm("ORG_INST") && !subscriptionInstance.instanceOf) || accessService.checkPerm("ORG_CONSORTIUM")}">
                <semui:actionsDropdownItem controller="subscription" action="copyElementsIntoSubscription" params="${[sourceObjectId: genericOIDService.getOID(subscriptionInstance)]}" message="myinst.copyElementsIntoSubscription" />
            </g:if>

            <g:if test="${accessService.checkPerm("ORG_INST") && subscriptionInstance.instanceOf}">
                <semui:actionsDropdownItem controller="subscription" action="copyMyElements" params="${[sourceObjectId: genericOIDService.getOID(subscriptionInstance)]}" message="myinst.copyMyElements" />
                <g:if test="${navPrevSubscription}">
                    <semui:actionsDropdownItem controller="subscription" action="copyMyElements" params="${[sourceObjectId: genericOIDService.getOID(navPrevSubscription[0]), targetObjectId: genericOIDService.getOID(subscriptionInstance)]}" message="myinst.copyMyElementsFromPrevSubscription" />
                </g:if>
            </g:if>

            <div class="divider"></div>

            <g:if test="${editable}">
                <semui:actionsDropdownItem controller="subscription" action="linkPackage" params="${[id:params.id]}" message="subscription.details.linkPackage.label" />
                <g:if test="${subscriptionInstance.packages}">
                    <semui:actionsDropdownItem controller="subscription" action="addEntitlements" params="${[id:params.id]}" message="subscription.details.addEntitlements.label" />
                    <semui:actionsDropdownItem controller="subscription" action="manageEntitlementGroup" params="${[id:params.id]}" message="subscription.details.manageEntitlementGroup.label" />
                    <semui:actionsDropdownItem controller="subscription" action="index" notActive="true" params="${[id:params.id, issueEntitlementEnrichment: true]}" message="subscription.details.issueEntitlementEnrichment.label" />
                </g:if>
                <g:else>
                    <semui:actionsDropdownItemDisabled message="subscription.details.addEntitlements.label" tooltip="${message(code:'subscription.details.addEntitlements.noPackagesYetAdded')}"/>
                </g:else>
            </g:if>

            <%-- TODO: once the hookup has been decided, the ifAnyGranted securing can be taken down --%>
            <sec:ifAnyGranted roles="ROLE_ADMIN">
                <g:if test="${subscriptionInstance.instanceOf}">
                    <g:if test="${params.pkgfilter}">
                        <g:set var="pkg" value="${SubscriptionPackage.executeQuery("select sp from SubscriptionPackage sp where sp.pkg.gokbId = :filter",[filter:params.pkgfilter])}"/>
                        <g:if test="${pkg && !pkg.finishDate}">
                            <semui:actionsDropdownItem controller="subscription" action="renewEntitlements" params="${[targetObjectId:params.id,packageId:params.pkgfilter]}" message="subscription.details.renewEntitlements.label"/>
                        </g:if>
                        <g:else>
                            <semui:actionsDropdownItemDisabled message="subscription.details.renewEntitlements.label" tooltip="${message(code:'subscription.details.renewEntitlements.packageRenewalAlreadySubmitted')}"/>
                        </g:else>
                    </g:if>
                    <g:else>
                        <semui:actionsDropdownItemDisabled message="subscription.details.renewEntitlements.label" tooltip="${message(code:'subscription.details.renewEntitlements.packageMissing')}"/>
                    </g:else>
                </g:if>
            </sec:ifAnyGranted>

            <g:set var="previousSubscriptions" value="${Links.findByLinkTypeAndDestinationSubscription(RDStore.LINKTYPE_FOLLOWS, subscriptionInstance)}"/>


            <g:if test="${subscriptionInstance._getCalculatedType() in [CalculatedType.TYPE_CONSORTIAL, CalculatedType.TYPE_COLLECTIVE, CalculatedType.TYPE_ADMINISTRATIVE] && accessService.checkPerm("ORG_INST_COLLECTIVE,ORG_CONSORTIUM")}">
                <div class="divider"></div>
                <g:if test="${previousSubscriptions}">
                    <semui:actionsDropdownItemDisabled controller="subscription" action="renewSubscription"
                                                       params="${[id: params.id]}" tooltip="${message(code: 'subscription.details.renewals.isAlreadyRenewed')}" message="subscription.details.renewalsConsortium.label"/>
                </g:if>
                <g:else>
                    <semui:actionsDropdownItem controller="subscription" action="renewSubscription"
                                           params="${[id: params.id]}" message="subscription.details.renewalsConsortium.label"/>
                </g:else>
            </g:if>
            <g:if test ="${subscriptionInstance._getCalculatedType() == CalculatedType.TYPE_LOCAL}">
                <g:if test ="${previousSubscriptions}">
                    <semui:actionsDropdownItemDisabled controller="subscription" action="renewSubscription"
                                                       params="${[id: params.id]}" tooltip="${message(code: 'subscription.details.renewals.isAlreadyRenewed')}" message="subscription.details.renewals.label"/>
                </g:if>
                <g:else>
                    <semui:actionsDropdownItem controller="subscription" action="renewSubscription"
                                           params="${[id: params.id]}" message="subscription.details.renewals.label"/>
                </g:else>
            </g:if>

            <g:if test="${accessService.checkPerm("ORG_CONSORTIUM") && showConsortiaFunctions && subscriptionInstance.instanceOf == null }">
                    <semui:actionsDropdownItem controller="survey" action="addSubtoSubscriptionSurvey"
                                               params="${[sub:params.id]}" text="${message(code:'createSubscriptionSurvey.label')}" />

                <semui:actionsDropdownItem controller="survey" action="addSubtoIssueEntitlementsSurvey"
                                           params="${[sub:params.id]}" text="${message(code:'createIssueEntitlementsSurvey.label')}" />
            </g:if>


            <g:if test="${showConsortiaFunctions || subscriptionInstance.administrative}">
                <semui:actionsDropdownItem controller="subscription" action="addMembers" params="${[id:params.id]}" text="${message(code:'subscription.details.addMembers.label',args:menuArgs)}" />
            </g:if>

            <g:if test="${subscriptionInstance._getCalculatedType() == CalculatedType.TYPE_CONSORTIAL && accessService.checkPerm("ORG_CONSORTIUM")}">

                  <semui:actionsDropdownItem controller="subscription" action="linkLicenseMembers"
                                             params="${[id: params.id]}"
                                             text="${message(code:'subscription.details.subscriberManagement.label',args:menuArgs)}"/>
            </g:if>

            <g:if test="${actionName == 'members'}">
                <g:if test="${validSubChilds}">
                    <div class="divider"></div>
                    <semui:actionsDropdownItem data-semui="modal" href="#copyEmailaddresses_ajaxModal" message="menu.institutions.copy_emailaddresses.button"/>
                </g:if>
            </g:if>

            <g:if test="${actionName == 'show'}">
                <%-- the editable setting needs to be the same as for the properties themselves -> override! --%>
                <%-- the second clause is to prevent the menu display for consortia at member subscriptions --%>
                <g:if test="${accessService.checkPermAffiliation('ORG_INST, ORG_CONSORTIUM','INST_EDITOR') && !(org.id == subscriptionInstance.getConsortia()?.id && subscriptionInstance.instanceOf)}">
                    <div class="divider"></div>
                    <semui:actionsDropdownItem data-semui="modal" href="#propDefGroupBindings" message="menu.institutions.configure_prop_groups" />
                </g:if>

                <g:if test="${editable}">
                    <div class="divider"></div>
                    <g:link class="item" action="delete" id="${params.id}"><i class="trash alternate icon"></i> ${message(code:'deletion.subscription')}</g:link>
                </g:if>
                <g:else>
                    <a class="item disabled" href="#"><i class="trash alternate icon"></i> ${message(code:'deletion.subscription')}</a>
                </g:else>
            </g:if>

        </g:if>
    </semui:actionsDropdown>
</g:if>
<g:if test="${editable || accessService.checkPermAffiliation('ORG_INST,ORG_CONSORTIUM','INST_EDITOR')}">
    <g:render template="/templates/documents/modal" model="${[ownobj: subscriptionInstance, owntp: 'subscription']}"/>
    <g:render template="/templates/tasks/modal_create" model="${[ownobj: subscriptionInstance, owntp: 'subscription']}"/>
</g:if>
<g:if test="${accessService.checkMinUserOrgRole(user,org,'INST_EDITOR')}">
    <g:render template="/templates/notes/modal_create" model="${[ownobj: subscriptionInstance, owntp: 'subscription']}"/>
</g:if>
