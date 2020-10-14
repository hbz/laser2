<!doctype html>
<html>
  <head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code:'laser')} : ${message(code:'default.documents.label')}</title>
  </head>

  <body>

    <g:render template="breadcrumb" model="${[ params:params ]}"/>
    <semui:controlButtons>
      <g:render template="actions" />
    </semui:controlButtons>
    <semui:messages data="${flash}" />

      <h1 class="ui icon header la-noMargin-top"><semui:headerIcon />
        <semui:xEditable owner="${subscriptionInstance}" field="name" />
      </h1>
      <semui:anualRings object="${subscriptionInstance}" controller="subscription" action="documents" navNext="${navNextSubscription}" navPrev="${navPrevSubscription}"/>

    <g:render template="nav" />

    <g:if test="${subscriptionInstance.instanceOf && (contextOrg?.id in [subscriptionInstance.getConsortia()?.id,subscriptionInstance.getCollective()?.id])}">
      <g:render template="message" />
    </g:if>

    <semui:messages data="${flash}" />

    <g:render template="/templates/documents/table" model="${[instance:subscriptionInstance, context:'documents', redirect:'documents', owntp: 'subscription']}"/>

  </body>
</html>
