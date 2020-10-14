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
      <h2 class="ui header">Permissions for user</h2>
      <table  class="ui celled la-table table">
      </table>

      <h2 class="ui header">The following organisations are granted the listed permissions from this license</h2>
      <table  class="ui celled la-table table">
        <tr>
          <th>Organisation</th><th>Roles and Permissions</th>
        </tr>
        <g:each in="${onixplLicense.license.orgRelations}" var="ol">
          <tr>
            <td>${ol.org.name}</td>
            <td>
              Connected to this license through link ${ol.id} link role : ${ol.roleType?.value}.<br/>
              This role grants the following permissions to members of that org whose membership role also includes the permission<br/>
              <ul>
                <g:each in="${ol.roleType?.sharedPermissions}" var="sp">
                  <li>${sp.perm.code} 
                      <g:if test="${onixplLicense.license.checkPermissions(sp.perm.code,user)}">
                        [Granted]
                      </g:if>
                      <g:else>
                        [Not granted]
                      </g:else>
 
                  </li>
                </g:each>
              </ul>
            </td>
          </tr>
        </g:each>
      </table>

      <h2 class="ui header">Logged in user permissions</h2>
      <table  class="ui celled la-table table">
        <tr>
          <th>Affiliated via Role</th><th>Permissions</th>
        </tr>
        <g:each in="${user.affiliations}" var="ol">
          <g:if test="${ol.status==1}">
            <tr>
              <td>Affiliated to ${ol.org?.name} with role <g:message code="cv.roles.${ol.formalRole?.authority}"/></td>
              <td>
                <ul>
                  <g:each in="${ol.formalRole.grantedPermissions}" var="gp">
                    <li>${gp.perm.code}</li>
                  </g:each>
                </ul>
              </td>
            </tr>
            <g:each in="${ol.org.outgoingCombos}" var="oc">
              <tr>
                <td> --&gt; This org is related to ${oc.toOrg.name} ( ${oc.type.value} )</td>
                <td>
                  <ul>
                    <g:each in="${oc.type.sharedPermissions}" var="gp">
                      <li>${gp.perm.code}</li>
                    </g:each>
                  </ul>
                </td>
              </tr>     
            </g:each>
          </g:if>
        </g:each>
      </table>
   
    </div>


</body>
</html>
