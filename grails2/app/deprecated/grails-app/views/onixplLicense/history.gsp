<!doctype html>
<html>
    <head>
        <meta name="layout" content="semanticUI"/>
        <title>${message(code:'laser')}</title>
</head>

<body>

    <div>
        <ul class="breadcrumb">
            <li> <g:link controller="home" action="index">Home</g:link> <span class="divider">/</span> </li>
            <li>ONIX-PL Licenses</li>
        </ul>
    </div>

    <div>
        <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />${onixplLicense.license.getLicensee()?.name} ${onixplLicense.license.type?.value} License : <span id="reference" class="ipe" style="padding-top: 5px;">${onixplLicense.license.reference}</span></h1>

<g:render template="nav" contextPath="." />

    </div>

    <div>
License history
      <table  class="ui celled la-table table">
        <tr>
          <th>Event ID</th>
          <th>Person</th>
          <th>Date</th>
          <th>Event</th>
          <th>Field</th>
          <th>Old Value</th>
          <th>New Value</th>
        </tr>
        <g:if test="${historyLines}">
          <g:each in="${historyLines}" var="hl">
            <tr>
              <td>${hl.id}</td>
              <td style="white-space:nowrap;">${hl.actor}</td>
              <td style="white-space:nowrap;">${hl.dateCreated}</td>
              <td style="white-space:nowrap;">${hl.eventName}</td>
              <td style="white-space:nowrap;">${hl.propertyName}</td>
              <td>${hl.oldValue}</td>
              <td>${hl.newValue}</td>
            </tr>
          </g:each>
        </g:if>
      </table>
    </div>

</body>
</html>
