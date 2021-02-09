<%@ page import="de.laser.Package" %>
<!doctype html>
<html>
  <head>
    <meta name="layout" content="laser">
    <title>${message(code:'laser')} : ${message(code:'package.label')}</title>
    <asset:stylesheet src="datatables.css"/><laser:javascript src="datatables.js"/>%{-- dont move --}%
  </head>
  <body>

      <semui:breadcrumbs>
          <semui:crumb controller="package" action="index" text="${message(code:'package.show.all')}" />
          <semui:crumb text="${packageInstance.name}" id="${packageInstance.id}" class="active"/>
      </semui:breadcrumbs>

      <semui:modeSwitch controller="package" action="${params.action}" params="${params}" />

      <semui:controlButtons>
          <semui:exportDropdown>
              <semui:exportDropdownItem>
                  <g:link class="item" action="show" params="${params+[format:'json']}">JSON</g:link>
              </semui:exportDropdownItem>
              <semui:exportDropdownItem>
                  <g:link class="item" action="show" params="${params+[format:'xml']}">XML</g:link>
              </semui:exportDropdownItem>
          </semui:exportDropdown>
          <g:render template="actions" />
      </semui:controlButtons>


          <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />

              <g:if test="${editable}"><span id="packageNameEdit"
                        class="xEditableValue"
                        data-type="textarea"
                        data-pk="${packageInstance.class.name}:${packageInstance.id}"
                        data-name="name"
                        data-url='<g:createLink controller="ajax" action="editableSetValue"/>'>${packageInstance.name}</span></g:if>
              <g:else>${packageInstance.name}</g:else>
          </h1>

            <g:render template="nav" contextPath="." />


  <semui:messages data="${flash}" />

  <semui:errors bean="${packageInstance}" />

  <div class="row">
      <div class="column">
          <g:render template="filter" model="${[params: params]}"/>
      </div>
  </div>

  <div class="row">
      <div class="column">
          <g:render template="/templates/tipps/table"
                    model="[tipps: titlesList, showPackage: false, showPlattform: true]"/>
      </div>
  </div>
  </div>

  <g:if test="${titlesList}">
      <semui:paginate action="${actionName}" controller="package" params="${params}"
                      next="${message(code: 'default.paginate.next')}" prev="${message(code: 'default.paginate.prev')}"
                      maxsteps="${max}" total="${num_tipp_rows}"/>
  </g:if>


    <g:render template="/templates/orgLinksModal"
              model="${[roleLinks:packageInstance?.orgs,parent:packageInstance.class.name+':'+packageInstance.id,property:'orgs',recip_prop:'pkg']}" />

    <laser:script file="${this.getGroovyPageFileName()}">
      JSPC.app.selectAll = function () {
        $('#select-all').is( ":checked")? $('.bulkcheck').prop('checked', true) : $('.bulkcheck').prop('checked', false);
      }

      JSPC.app.confirmSubmit = function () {
        if ( $('#bulkOperationSelect').val() === 'remove' ) {
          var agree=confirm("${message(code:'default.continue.confirm')}");
          if (agree)
            return true ;
          else
            return false ;
        }
      }
    </laser:script>

  </body>
</html>
