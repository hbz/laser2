  <table class="ui celled la-table table">
    <thead>
      <tr>
        <th>${message(code:'license.organisationName')}</th>
        <th>${message(code:'template.orgLinks.role')}</th>
        <th class="la-action-info">${message(code:'default.actions.label')}</th>
      </tr>
    </thead>
    <g:each in="${roleLinks}" var="role">
      <tr>
        <g:if test="${role.org}">
          <td><g:link controller="organisation" action="show" id="${role.org.id}">${role?.org?.name}</g:link></td>
          <td>${role?.roleType?.getI10n("value")}</td>
          <td>
            <g:if test="${editmode}">
              <g:link controller="ajax" action="delOrgRole" id="${role.id}" onclick="return confirm(${message(code:'template.orgLinks.delete.warn')})">
                ${message(code:'default.button.delete.label')}
              </g:link>
            </g:if>
          </td>
        </g:if>
        <g:else>
          <td colspan="3">${message(code:'template.orgLinks.error' , args:[role.id])}</td>
        </g:else>
      </tr>
    </g:each>
  </table>
  <g:if test="${editmode}">
    <a role="button" class="ui button" data-semui="modal" href="#osel_add_modal" >${message(code:'license.addOrgLink')}</a>
  </g:if>
