<%@ page import="com.k_int.kbplus.Subscription" %>
<r:require module="annotations" />

<!doctype html>
<html>
  <head>
      <meta name="layout" content="semanticUI"/>
      <title>${message(code:'laser', default:'LAS:eR')} ${message(code:'subscription.label', default:'Subscription')}</title>
  </head>
  <body>

    <div class="container">
        <ul class="breadcrumb">
            <li> <g:link controller="home" action="index">${message(code:'default.home.label', default:'Home')}</g:link> <span class="divider">/</span> </li>
            <g:if test="${params.shortcode}">
                <li> <g:link controller="myInstitutions" action="currentSubscriptions" params="${[shortcode:params.shortcode]}"> ${params.shortcode} - ${message(code:'myinst.currentSubscriptions.label', default:'Current Subscriptions')}</g:link> <span class="divider">/</span> </li>
            </g:if>
            <li> <g:link controller="subscriptionDetails" action="index" id="${subscription.id}">${message(code:'subscription.label', default:'Subscription')} ${subscription.id} - ${message(code:'default.details.label', default:'Details')}</g:link> <span class="divider">/</span> </li>
            <li> <g:link controller="subscriptionDetails" action="costPerUse" id="${subscription.id}">${message(code:'subscription.label', default:'Subscription')} ${subscription.id} - ${message(code:'subscription.details.costPerUse.label', default:'Cost Per Use')}</g:link> </li>
        </ul>
    </div>

    <g:if test="${flash.message}">
        <div class="container"><bootstrap:alert class="alert-info">${flash.message}</bootstrap:alert></div>
    </g:if>

    <g:if test="${flash.error}">
        <div class="container"><bootstrap:alert class="alert-error">${flash.error}</bootstrap:alert></div>
    </g:if>

    <div class="container">
        <h1>${message(code:'subscription.details.costPerUse.label', default:'Cost Per Use')} :: ${subscription.name}</h1>
        <g:render template="nav"  />
    </div>

    <div class="container">
      <g:if test="${costItems && costItems.size() > 0}">
        <table class="ui celled table">
          <thead>
            <tr>
              <th>${message(code:'financials.invoice_number', default:'Invoice Number')}</th>
              <th>${message(code:'default.startDate.label', default:'Start Date')}</th>
              <th>${message(code:'default.endDate.label', default:'End Date')}</th>
              <th>${message(code:'financials.invoice_total', default:'Invoice Total')}</th>
            </tr>
          </thead>
          <tbody>
            <g:each in="${costItems}" var="ci">
              <tr>
                <td>${ci.invoice.invoiceNumber}</td>
                <td><g:formatDate date="${ci.invoice.startDate}" format="${message(code:'default.date.format.notime', default:'yyyy-MM-dd')}"/></td>
                <td><g:formatDate date="${ci.invoice.endDate}" format="${message(code:'default.date.format.notime', default:'yyyy-MM-dd')}"/></td>
                <td><span class="pull-right"><g:formatNumber number="${ci.total}" groupingUsed="true" type="currency"/></span></td>
              </tr>
              <tr>
                <td colspan="4">Total usage for this invoice period: <g:formatNumber number="${ci.total_usage_for_sub}"  groupingUsed="true"/> gives an overall cost per use of
                       <strong><g:formatNumber number="${ci.overall_cost_per_use}" groupingUsed="true"  type="currency"/></strong></td>
              </tr>
              <g:each in="${ci.usage}" var="u">
                <tr>
                  <td colspan="3"><span class="pull-right">Apportionment for usage period ${u[0]}/${u[1]}</span></td>
                  <td><span class="pull-right">${u[2]} @ <g:formatNumber number="${ci.overall_cost_per_use}"  groupingUsed="true" type="currency"/>
                       = <g:formatNumber number="${ci.overall_cost_per_use * Integer.parseInt(u[2])}"  groupingUsed="true" type="currency"/></span></td>

                </tr>
              </g:each>
            </g:each>
          </tbody>
        </table>
      </g:if>
      <g:else>
        Unable to locate any invoices against this subscription
      </g:else>
    </div>

  </body>
</html>
