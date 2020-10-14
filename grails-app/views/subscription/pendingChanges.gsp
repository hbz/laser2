<%@ page import="de.laser.Subscription; de.laser.License; de.laser.RefdataValue" %>
<laser:serviceInjection />

<!doctype html>
<html>
<head>
  <meta name="layout" content="semanticUI"/>
  <title>${message(code:'laser')} : ${message(code:'pendingChange.plural')}</title>
</head>
<body>

    <g:render template="breadcrumb" model="${[ license:subscription, params:params ]}"/>

    <semui:controlButtons>
        <g:render template="actions" />
    </semui:controlButtons>

    <h1 class="ui icon header la-noMargin-top"><semui:headerIcon />
        <semui:xEditable owner="${subscriptionInstance}" field="name" />
    </h1>
    <semui:anualRings object="${subscriptionInstance}" controller="subscription" action="pendingChanges" navNext="${navNextSubscription}" navPrev="${navPrevSubscription}"/>


    <g:render template="nav" />

    <g:each in="${pendingChanges}" var="memberId, pcList">
        <g:set var="member" value="${Subscription.get(memberId)}" />

        <h4>${member.getNameConcatenated()}</h4>

        <g:render template="/templates/pendingChanges" model="${['pendingChanges':pcList, 'flash':flash, 'model':member, 'tmplSimpleView':true]}"/>
    </g:each>


</body>
</html>
