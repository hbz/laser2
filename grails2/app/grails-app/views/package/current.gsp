<%@ page import="de.laser.Package" %>
<!doctype html>
<html>
    <head>
        <meta name="layout" content="semanticUI">
        <title>${message(code:'laser')} : ${message(code:'package.show.nav.titles')}</title>
    </head>
    <body>
        <semui:breadcrumbs>
            <semui:crumb controller="package" action="index" text="${message(code:'package.show.all')}" />
            <semui:crumb text="${packageInstance.name}" id="${packageInstance.id}" class="active"/>
        </semui:breadcrumbs>

        <semui:modeSwitch controller="package" action="show" params="${params}" />

        <semui:controlButtons>
            <semui:exportDropdown>
                <semui:exportDropdownItem>
                    <g:if test="${filterSet}">
                        <g:link class="item js-open-confirm-modal"
                                data-confirm-tokenMsg="${message(code: 'confirmation.content.exportPartial')}"
                                data-confirm-term-how="ok" controller="package" action="current"
                                params="${params + [format: 'csv']}">
                            <g:message code="default.button.exports.csv"/>
                        </g:link>
                    </g:if>
                    <g:else>
                        <g:link class="item" action="current" params="${params + [format: 'csv']}">CSV Export</g:link>
                    </g:else>
                </semui:exportDropdownItem>
                <semui:exportDropdownItem>
                    <g:if test="${filterSet}">
                        <g:link class="item js-open-confirm-modal"
                                data-confirm-tokenMsg="${message(code: 'confirmation.content.exportPartial')}"
                                data-confirm-term-how="ok" controller="package" action="current"
                                params="${params + [exportXLSX: true]}">
                            <g:message code="default.button.exports.xls"/>
                        </g:link>
                    </g:if>
                    <g:else>
                        <g:link class="item" action="current" params="${params+[exportXLSX: true]}">
                            <g:message code="default.button.exports.xls"/>
                        </g:link>
                    </g:else>
                </semui:exportDropdownItem>
                <semui:exportDropdownItem>
                    <g:if test="${filterSet}">
                        <g:link class="item js-open-confirm-modal"
                                data-confirm-tokenMsg="${message(code: 'confirmation.content.exportPartial')}"
                                data-confirm-term-how="ok" controller="package" action="current"
                                params="${params + [exportKBart: true]}">
                            KBART Export
                        </g:link>
                    </g:if>
                    <g:else>
                        <g:link class="item" action="current"
                                params="${params + [exportKBart: true]}">KBART Export</g:link>
                    </g:else>
                </semui:exportDropdownItem>
                <%--<semui:exportDropdownItem>
                    <g:link class="item" action="show" params="${params+[format:'json']}">JSON</g:link>
                </semui:exportDropdownItem>
                <semui:exportDropdownItem>
                    <g:link class="item" action="show" params="${params+[format:'xml']}">XML</g:link>
                </semui:exportDropdownItem>--%>
            </semui:exportDropdown>
            <g:render template="actions" />
        </semui:controlButtons>

        <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />

            ${packageInstance.name}
        </h1>

        <g:render template="nav"/>

            <sec:ifAnyGranted roles="ROLE_ADMIN">
            <g:link class="ui button" controller="announcement" action="index" params='[at:"Package Link: ${pkg_link_str}",as:"RE: Package ${packageInstance.name}"]'>${message(code:'package.show.announcement')}</g:link>
            </sec:ifAnyGranted>

            <g:if test="${forum_url != null}">
              &nbsp;<a href="${forum_url}">Discuss this package in forums</a> <a href="${forum_url}" title="Discuss this package in forums (new Window)" target="_blank"><i class="icon-share-alt"></i></a>
            </g:if>

    <sec:ifAnyGranted roles="ROLE_ADMIN,ROLE_PACKAGE_EDITOR">
        <g:render template="/templates/pendingChanges" model="${['pendingChanges': pendingChanges, 'flash':flash, 'model':packageInstance]}"/>
    </sec:ifAnyGranted>


  <semui:messages data="${flash}" />

  <semui:errors bean="${packageInstance}" />
    <br><br>
    <div class="ui grid">
        <div class="row">
            <div class="column">
                ${message(code:'title.search.offset.text', args:[offset+1,lasttipp,num_tipp_rows])} -
                <g:if test="${params.mode=='advanced'}">${message(code:'package.show.switchView.basic')} <g:link controller="package" action="current" params="${params+['mode':'basic']}">${message(code:'default.basic')}</g:link></g:if>
                    <g:else>${message(code:'package.show.switchView.advanced')} <g:link controller="package" action="current" params="${params+['mode':'advanced']}" type="button" >${message(code:'default.advanced')}</g:link></g:else>
            </div>
        </div>
        <div class="row">
            <div class="column">
                <g:render template="filter" model="${[params: params]}"/>
            </div>
        </div>

        <%-- as far as I understood, package information must not be edited from LAS:eR - this is GOKb's matter
        <g:if test="${editable}">
          <div class="row">
              <div class="column">
                  <semui:form>
                      <g:form class="ui form" controller="ajax" action="addToCollection">

                          <legend><h3 class="ui header">${message(code:'package.show.title.add', default:'Add A Title To This Package')}</h3></legend>
                          <input type="hidden" name="__context" value="${packageInstance.class.name}:${packageInstance.id}"/>
                          <input type="hidden" name="__newObjectClass" value="${TitleInstancePackagePlatform.class.name}"/>
                          <input type="hidden" name="__recip" value="pkg"/>

                          <!-- N.B. this should really be looked up in the controller and set, not hard coded here -->
                          <input type="hidden" name="status" value="${RefdataValue.class.name}:29"/>
                          <div class="two fluid fields">
                              <div class="field">
                                  <label for="title">${message(code:'package.show.title.add.title', default:'Title To Add')}</label>
                                  <g:simpleReferenceTypedown class="input-xxlarge" style="width:350px;" id="title" name="title" baseClass="${TitleInstance.class.name}"/>
                              </div>
                              <div class="field">
                                  <label for="platform">${message(code:'package.show.title.add.platform', default:'Platform For Added Title')}</label>
                                  <g:simpleReferenceTypedown class="input-large" style="width:350px;" id="platform" name="platform" baseClass="${Platform.class.name}"/>
                              </div>
                          </div>
                          <button type="submit" class="ui button">${message(code:'package.show.title.add.submit', default:'Add Title...')}</button>

                      </g:form>
                  </semui:form>
              </div>
          </div>
        </g:if>--%>
        <div class="row">
            <div class="column">
                <%--<g:form action="packageBatchUpdate" params="${[id:packageInstance?.id]}">
            <g:if test="${editable}">
          <table class="ui celled la-table table la-ignore-fixed la-bulk-header">

            <thead>
            <tr>

              <td>
                <g:if test="${editable}"><input id="select-all" type="checkbox" name="chkall" onClick="selectAll()"/></g:if>
              </td>

              <td colspan="7">
                  <div class="ui form">
                      <div class="two fields">
                          <div class="field">
                              <select id="bulkOperationSelect" name="bulkOperation" class="ui selection dropdown la-clearable">
                                <option value="edit">${message(code:'package.show.batch.edit.label', default:'Batch Edit Selected Rows Using the following values')}</option>
                                <option value="remove">${message(code:'package.show.batch.remove.label', default:'Batch Remove Selected Rows')}</option>
                              </select>
                          </div>
                      </div>
                  </div>


                  <table class="ui celled la-table table">
                    <tr>
                        <td>${message(code:'subscription.details.coverageStartDate')}</td>
                        <td>${message(code:'tipp.startVolume')}</td>
                        <td>${message(code:'tipp.startIssue')}</td>
                    </tr>
                    <tr>
                        <td>${message(code:'subscription.details.coverageEndDate')}</td>
                        <td>${message(code:'tipp.endVolume')}</td>
                        <td>${message(code:'tipp.endIssue')}</td>
                    </tr>
                    <tr>
                      <td>${message(code:'tipp.coverageDepth')}</td>
                      <td>${message(code:'tipp.coverageNote')}</td>
                      <td>${message(code:'tipp.embargo')}</td>
                    </tr>
                  </table>

              </td>
            </tr>

            </thead>
                <tbody></tbody>
          </table>
            </g:if>

        </g:form>--%>
                <g:render template="/templates/tipps/table" model="[tipps: titlesList, showPackage: false, showPlattform: true, showBulkFlag: false]"/>
            </div>
        </div>
    </div>

          <g:if test="${titlesList}" >
            <semui:paginate action="current" controller="package" params="${params}" next="${message(code:'default.paginate.next')}" prev="${message(code:'default.paginate.prev')}" maxsteps="${max}" total="${num_tipp_rows}" />
          </g:if>




    <%-- <g:render template="enhanced_select" contextPath="../templates" />
    <g:render template="orgLinksModal" 
              contextPath="../templates" 
              model="${[roleLinks:packageInstance?.orgs,parent:packageInstance.class.name+':'+packageInstance.id,property:'orgs',recip_prop:'pkg']}" />--%>

    <r:script>
      $(function(){
        $.fn.editable.defaults.mode = 'inline';
        $('.xEditableValue').editable();
      });
      function selectAll() {
         $('#select-all').is( ":checked")? $('.bulkcheck').prop('checked', true) : $('.bulkcheck').prop('checked', false);

        //$('#select-all').is( ':checked' )? $('.bulkcheck').attr('checked', false) : $('.bulkcheck').attr('checked', true);
      }

      function confirmSubmit() {
        if ( $('#bulkOperationSelect').val() === 'remove' ) {
          var agree=confirm("${message(code:'default.continue.confirm')}");
          if (agree)
            return true ;
          else
            return false ;
        }
      }

    </r:script>

  </body>
</html>
