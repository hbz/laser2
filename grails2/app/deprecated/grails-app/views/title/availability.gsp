<%@ page import="com.k_int.kbplus.TitleInstance" %>
<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI">
    <g:set var="entityName" value="${message(code: 'titleInstance.label')}"/>
    <title><g:message code="default.edit.label" args="[entityName]"/></title>
</head>

<body>


  <ul class="breadcrumb">
    <li> <g:link controller="home" action="index">Home</g:link> <span class="divider">/</span> </li>
    <li> <g:link controller="title" action="show" id="${ti?.id}">Title ${ti?.title}</g:link> </li>
  </ul>

      <g:if test="${editable}">
        <semui:crumbAsBadge message="default.editable" class="orange" />
      </g:if>
  <br>
  <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />${ti.title}</h1>

    <g:render template="nav" />

      <semui:messages data="${flash}" />

    <g:if test="${availability?.size() > 0}">

      <div class="container alert-warn">
        <table class="ui celled la-rowspan table">
          <thead>
            <tr>
              <th rowspan="2">IE</th>
              <th>Subscribing Institution</th>
              <th>Status</th>
              <th>Coverage Start</th>
              <th>Coverage End</th>
            </tr>
            <tr>
              <th colspan="4">License properties</th>
            </tr>
          </thead>
          <tbody>
            <g:each in="${availability}" var="a">
              <tr>
                <td rowspan="2">${a.id}</a></td>
                <td>${a.subscription?.subscriber?.name}</a></td>
                <td>${a.status?.value}</a></td>
                <td>Start Date : <g:formatDate format="${message(code:'default.date.format.notime')}" date="${a.startDate}"/><br/>
                    Start Volume : ${a.startVolume}<br/>
                    Start Issue : ${a.startIssue}</td>
                <td>End Date : <g:formatDate format="${message(code:'default.date.format.notime')}" date="${a.endDate}"/><br/>
                    End Volume ${a.endVolume}<br/>
                    End Issue : ${a.endIssue}</td>
              </tr>
              <tr>
                <td colspan="4">
                  <ul>
                    <g:each in="${a.subscription?.owner?.customProperties}" var="lp">
                      <g:if test="${lp.type?.name.startsWith('ILL')}">
                        <li>${lp.type?.name} value:${lp.value}</li>
                      </g:if>
                    </g:each>
                  </ul>
                </td>
              </tr>
            </g:each>
          </tbody>
        </table>
      </div>
    </g:if>

</body>
</html>
