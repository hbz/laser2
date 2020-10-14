<!doctype html>
<html>
  <head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code:'laser')} - ${message(code:'menu.institutions.imp_sub_work')}</title>
  </head>

  <body>

  <semui:breadcrumbs>
    <semui:crumb message="menu.institutions.imp_sub_work" class="active" />
  </semui:breadcrumbs>

    <semui:form>
      <g:form class="ui form" action="importSubscriptionWorksheet" method="post" enctype="multipart/form-data" params="${params}">
        <input type="file" id="renewalsWorksheet" name="renewalsWorksheet"/>
          <br /><br />
        <button type="submit" class="ui button"><g:message code="subscription.upload.worksheet" default="Upload New Subscription Taken Worksheet"/></button>
      </g:form>
    </semui:form>

    <g:if test="${(errors && (errors.size() > 0))}">
      <div>
        <ul>
          <g:each in="${errors}" var="e">
            <li>${e}</li>
          </g:each>
        </ul>
      </div>
    </g:if>

  <semui:messages data="${flash}" />


    <g:set var="counter" value="${-1}" />

    <g:if test="${1==1}">
      <g:form class="ui form" action="processSubscriptionImport" method="post" params="${params}" enctype="multipart/form-data" >
        <div>
        <g:if test="${subOrg!=null}">
          ${message(code:'subscription.import.upload.note', default:'Import will create a subscription for')} ${subOrg.name}
          <input type="hidden" name="orgId" value="${subOrg.id}"/>
        </g:if>
        <hr/>
          <table class="ui celled la-table table">
            <thead>
              <tr>
                <th>${message(code:'title.label')}</th>
                <th>${message(code:'subscription.details.from_pkg')}</th>
                <th>ISSN</th>
                <th>eISSN</th>
                <th>${message(code:'default.startDate.label')}</th>
                <th>${message(code:'tipp.startVolume')}</th>
                <th>${message(code:'tipp.startIssue')}</th>
                <th>${message(code:'default.endDate.label')}</th>
                <th>${message(code:'tipp.endVolume')}</th>
                <th>${message(code:'tipp.endIssue')}</th>
                <th>${message(code:'subscription.details.core_medium')}</th>
              </tr>
            </thead>
            <tbody>
              <g:each in="${entitlements}" var="e">
                <tr>
                  <td><input type="hidden" name="entitlements.${++counter}.tipp_id" value="${e.base_entitlement.id}"/>
                      <input type="hidden" name="entitlements.${counter}.core_status" value="${e.core_status}"/>
                      <input type="hidden" name="entitlements.${counter}.start_date" value="${e.start_date}"/>
                      <input type="hidden" name="entitlements.${counter}.end_date" value="${e.end_date}"/>
                      <input type="hidden" name="entitlements.${counter}.coverage" value="${e.coverage}"/>
                      <input type="hidden" name="entitlements.${counter}.coverage_note" value="${e.coverage_note}"/>
                      <input type="hidden" name="entitlements.${counter}.core_start_date" value="${e.core_start_date}"/>
                      <input type="hidden" name="entitlements.${counter}.core_end_date" value="${e.core_end_date}"/>
                      ${e.base_entitlement.title.title}</td>
                  <td><g:link controller="package" action="show" id="${e.base_entitlement.pkg.id}">${e.base_entitlement.pkg.name}(${e.base_entitlement.pkg.id})</g:link></td>
                  <td>${e.base_entitlement.title.getIdentifierValue('ISSN')}</td>
                  <td>${e.base_entitlement.title.getIdentifierValue('eISSN')}</td>
                  <td>${e.start_date} (Default:<g:formatDate format="${message(code:'default.date.format.notime')}" date="${e.base_entitlement.startDate}"/>)</td>
                  <td>${e.base_entitlement.startVolume}</td>
                  <td>${e.base_entitlement.startIssue}</td>
                  <td>${e.end_date} (Default:<g:formatDate format="${message(code:'default.date.format.notime')}" date="${e.base_entitlement.endDate}"/>)</td>
                  <td>${e.base_entitlement.endVolume}</td>
                  <td>${e.base_entitlement.endIssue}</td>
                  <td>${e.core_status?:'N'}</td>
                </tr>
              </g:each>
            </tbody>
          </table>
          <input type="hidden" name="ecount" value="${counter}"/>

          <div class="la-float-right">
            <button type="submit" class="ui button">${message(code:'myinst.renewalUpload.accept')}</button>
          </div>
        </div>
      </g:form>
    </g:if>

  </body>
</html>
