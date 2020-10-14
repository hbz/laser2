<!-- _result.gsp -->
<%@ page import="com.k_int.kbplus.OrgRole;de.laser.RefdataCategory;de.laser.RefdataValue;com.k_int.properties.PropertyDefinition;de.laser.FinanceController" %>
<laser:serviceInjection />

<%
    def tabOwnerActive  = (tab?.equalsIgnoreCase('owner')) ? 'active' : ''
    def tabSCActive     = (tab?.equalsIgnoreCase('sc')) ? 'active' : ''
%>


<div id="financeFilterData" class="ui top attached tabular menu" data-current="${queryMode ? queryMode.minus('MODE_') : ''}">
    <g:if test="${queryMode != FinanceController.MODE_CONS_AT_SUBSCR}">
        <div class="item ${tabOwnerActive}" data-tab="OWNER">${message(code:'financials.tab.ownCosts')}</div>
    </g:if>
    <g:if test="${queryMode == FinanceController.MODE_CONS}">
        <div class="item ${tabSCActive}" data-tab="CONS">${message(code:'financials.tab.consCosts')}</div>
    </g:if>
    <g:if test="${queryMode == FinanceController.MODE_CONS_AT_SUBSCR}">
        <div class="item ${tabOwnerActive}" data-tab="CONS_AT_SUBSCR">${message(code:'financials.tab.consCosts')}</div>
    </g:if>
    <g:if test="${queryMode == FinanceController.MODE_SUBSCR}">
        <div class="item" data-tab="SUBSCR">${message(code:'financials.tab.subscrCosts')}</div>
    </g:if>
</div>

<%
    // WORKAROUND; grouping costitems by subscription
    def costItemsOwner = ["clean":[]]
    (ciList?.collect{it.sub}).each{ item ->
        if (item) {
            costItemsOwner << ["${item.name}": []]
        }
    }
    cost_items.each{ item ->
        if (item.sub) {
            costItemsOwner.get("${item.sub?.name}").add(item)
        }
        else {
            costItemsOwner.get('clean').add(item)
        }
    }
    costItemsOwner = costItemsOwner.findAll{ ! it.value.isEmpty() }
%>

<g:if test="${queryMode != FinanceController.MODE_CONS_AT_SUBSCR}">
    <!-- OWNER -->
    <div class="ui bottom attached tab ${tabOwnerActive}" data-tab="OWNER">
        <br />
        <g:render template="result_tab_owner" model="${[forSingleSubscription: forSingleSubscription, editable: editable, cost_items: costItemsOwner, cost_items_count: ciCountOwner, i: 'OWNER', ownerOffset: ownerOffset]}"/>
    </div><!-- OWNER -->
</g:if>

<g:if test="${queryMode == FinanceController.MODE_CONS}">
    <div class="ui bottom attached tab ${tabSCActive}" data-tab="CONS">
        <br />
        <g:render template="result_tab_cons" model="[forSingleSubscription: forSingleSubscription, editable: editable, cost_items: ciListCons, cost_items_count: ciCountCons, i: 'CONS', consOffset: consOffset]"/>
    </div>
</g:if>

<g:if test="${queryMode == FinanceController.MODE_CONS_AT_SUBSCR}">

    <div class="ui bottom attached tab ${tabOwnerActive}" data-tab="CONS_AT_SUBSCR">
        <br />
        <g:render template="result_tab_cons" model="[forSingleSubscription: forSingleSubscription, editable: editable, cost_items: ciListCons, cost_items_count: ciCountCons, i: 'CONS_AT_SUBSCR', consOffset: consOffset]"/>
    </div>
</g:if>

<g:if test="${queryMode == FinanceController.MODE_SUBSCR}">

    <div class="ui bottom attached tab" data-tab="SUBSCR">
        <br />
        <g:render template="result_tab_subscr" model="[editable: editable, cost_items: ciListSubscr, cost_items_count: ciCountSub, i: 'SUBSCR', subscrOffset: subscrOffset]"/>
    </div>
</g:if>

<r:script>
    $('#financeFilterData .item').tab({
        onVisible: function(tabPath) {
            $('#financeFilterData').attr('data-current', tabPath)
            console.log(tabPath)
        }
    });
</r:script>

<!-- _result.gsp -->