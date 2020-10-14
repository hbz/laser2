<%--
  Created by IntelliJ IDEA.
  User: galffy
  Date: 07.02.2019
  Time: 08:56
--%>
<!doctype html>
<html xmlns="http://www.w3.org/1999/html">
    <head>
        <meta name="layout" content="semanticUI"/>
        <title>${message(code:'laser')} : ${message(code:'subscription.details.financials.label')}</title>
    </head>
    <body>
        <laser:serviceInjection />
        <g:set var="own" value="${financialData.own}"/>
        <g:set var="cons" value="${financialData.cons}"/>
        <g:set var="coll" value="${financialData.coll}"/>
        <g:set var="subscr" value="${financialData.subscr}"/>
        <semui:breadcrumbs>
            <%--<semui:crumb controller="myInstitution" action="dashboard" text="${contextService.getOrg().getDesignation()}" />--%>
            <semui:crumb controller="myInstitution" action="currentSubscriptions" text="${message(code:'myinst.currentSubscriptions.label')}" />
            <semui:crumb class="active"  message="${subscription.name}" />
        </semui:breadcrumbs>
        <semui:controlButtons>
            <semui:exportDropdown>
                <g:if test="${filterSet}">
                    <semui:exportDropdownItem>
                        <g:link  class="item js-open-confirm-modal"
                                 data-confirm-tokenMsg = "${message(code: 'confirmation.content.exportPartial')}"
                                 data-confirm-term-how="ok"
                                 controller="finance"
                                 action="financialsExport"
                                 params="${params+[exportXLS:true,sub:subscription.id]}">${message(code:'default.button.exports.xls')}
                        </g:link>
                    </semui:exportDropdownItem>
                    <semui:exportDropdownItem>
                        <g:link class="item exportCSV js-open-confirm-modal"
                                 data-confirm-tokenMsg = "${message(code: 'confirmation.content.exportPartialCSV')}"
                                 data-confirm-term-how="ok"
                                 controller="finance"
                                 action="financialsExport"
                                 params="${params+[format:'csv',sub:subscription.id]}">${message(code:'default.button.exports.csv')}
                        </g:link>
                    </semui:exportDropdownItem>
                </g:if>
                <g:else>
                    <semui:exportDropdownItem>
                        <g:link class="item" controller="finance" action="financialsExport" params="${params+[exportXLS:true,sub:subscription.id]}">${message(code:'default.button.exports.xls', default:'XLS Export')}</g:link>
                    </semui:exportDropdownItem>
                    <semui:exportDropdownItem>
                        <g:link class="item exportCSV js-open-confirm-modal"
                                 data-confirm-tokenMsg = "${message(code: 'confirmation.content.exportCSV')}"
                                 data-confirm-term-how="ok"
                                 controller="finance"
                                 action="financialsExport"
                                 params="${params+[format:'csv',sub:subscription.id]}">${message(code:'default.button.exports.csv')}
                        </g:link>
                    </semui:exportDropdownItem>
                </g:else>
            </semui:exportDropdown>

            <g:if test="${editable}">
                <semui:actionsDropdown>
                    <semui:actionsDropdownItem id="btnAddNewCostItem" message="financials.addNewCost" />
                </semui:actionsDropdown>
            </g:if>
        </semui:controlButtons>

        <%
            List<GString> total = []
            dataToDisplay.each { view ->
                switch(view) {
                    case 'own': total << "${own.count} ${message(code:'financials.header.ownCosts')}"
                        break
                    case 'cons':
                    case 'consAtSubscr': total << "${cons.count} ${message(code:'financials.header.consortialCosts')}"
                        break
                    case 'coll':
                    case 'collAtSubscr': total << "${coll.count} ${message(code:'financials.header.collectiveCosts')}"
                        break
                    case 'subscr': total << "${subscr.count} ${message(code:'financials.header.subscriptionCosts')}"
                        break
                }
            }
        %>

        <h1 class="ui icon header la-noMargin-top">
            <semui:headerIcon />${message(code:'subscription.details.financials.label')} ${message(code:'default.for')} ${subscription} <semui:totalNumber total="${total.join(' / ')}"/>
        </h1>
        <semui:anualRings mapping="subfinance" object="${subscription}" controller="finance" action="index" navNext="${navNextSubscription}" navPrev="${navPrevSubscription}"/>


        <g:render template="/subscription/nav" model="${[subscriptionInstance:subscription, params:(params << [id:subscription.id,showConsortiaFunctions:showConsortiaFunctions,showCollectiveFunctions:showCollectiveFunctions])]}"/>

        <g:if test="${showConsortiaFunctions || showCollectiveFunctions || params.orgBasicMemberView}">
            <g:render template="/subscription/message" model="${[contextOrg: institution, subscriptionInstance: subscription]}"/>
        </g:if>

        <g:render template="result" model="[own:own,cons:cons,subscr:subscr,showView:showView,filterPresets:filterPresets,fixedSubscription:subscription]" />
    </body>
</html>