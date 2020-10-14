<%@ page import="com.k_int.kbplus.Package" %>
<!doctype html>
<html>
  <head>
    <meta name="layout" content="semanticUI">
    <g:set var="entityName" value="${message(code: 'package.label')}" />
    <title><g:message code="default.edit.label" args="[entityName]" /></title>
  </head>
  <body>

    <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />${message(code:'menu.datamanager.uploadPackage')}</h1>

    <semui:messages data="${flash}" />

    <semui:errors bean="${packageInstance}" />

        <semui:form>
          <g:form action="reviewPackage" method="post" enctype="multipart/form-data" class="ui form">

            <div class="four fields">

              <div class="field" id="uploadPackage">
                <label>${message(code:'package.upload.file')}</label>
                <div class="ui fluid action input">
                  <input type="text" readonly="readonly" placeholder="${message(code:'template.addDocument.selectFile')}">
                  <input type="file" id="soFile"  name="soFile"  style="display: none;">
                  <div class="ui icon button" style="padding-left:30px; padding-right:30px">
                    <i class="attach icon"></i>
                  </div>
                </div>
                <r:script>
                  $('#uploadPackage .action .icon.button').click( function() {
                    $(this).parent('.action').find('input:file').click();
                  });

                  $('input:file', '.ui.action.input').on('change', function(e) {
                    var name = e.target.files[0].name;
                    $('input:text', $(e.target).parent()).val(name);
                  });
                </r:script>
              </div>

              <div class="field">
                <label>${message(code:'package.upload.docStyle', default:'Doc Style')}</label>
                <select name="docstyle" class="ui dropdown">
                  <option value="csv" selected>${message(code:'package.upload.docStyle.csv')}</option>
                  <option value="tsv">${message(code:'package.upload.docStyle.tsv')}</option>
                </select>
              </div>

              <div class="field">
                <label>${message(code:'package.upload.override', default:'Override Character Set Test')}</label>
                <input type="checkbox" name="OverrideCharset" checked="false"/>
              </div>

              <div class="field">
                <label>&nbsp;</label>
                <button type="submit" class="ui button">${message(code:'package.upload.upload')}</button>
              </div>
            </div>
          </g:form>
        
        </semui:form>

        <g:if test="${validationResult}">
          <g:if test="${validationResult.stats != null}">
            <h3 class="ui header">${message(code:'default.stats.label')}</h3>
            <ul>
              <g:each in="${validationResult?.stats}" var="msg">
                <li>${msg.key} = ${msg.value}</li>
              </g:each>
            </ul>
          </g:if>

          <g:each in="${validationResult?.messages}" var="msg">
            <div class="alert alert-error">${msg}</div>
          </g:each>

          <hr/>

          <g:if test="${validationResult.processFile==true}">
            <semui:msg class="positive" message="package.upload.passed"><br>
              <g:link controller="package" action="show" id="${validationResult.new_pkg_id}">${message(code:'package.upload.details', default:'New Package Details')}</g:link><br/>
            </semui:msg>
          </g:if>
          <g:else>
            <div class="alert alert-error">${message(code:'package.upload.failed', default:'File failed validation checks, details follow')}</div>
          </g:else>
          <table class="ui table">
            <tbody>
              <g:each in="${['soName', 'soIdentifier', 'soProvider', 'soPackageIdentifier', 'soPackageName', 'aggreementTermStartYear', 'aggreementTermEndYear', 'consortium', 'numPlatformsListed']}" var="fld">
                <tr>
                  <td>${fld}</td>
                  <td>${validationResult[fld]?.value} 
                    <g:if test="${validationResult[fld]?.messages != null}">
                      <hr/>
                      <g:each in="${validationResult[fld]?.messages}" var="msg">
                        <div class="alert alert-error">${msg}</div>
                      </g:each>
                    </g:if>
                  </td>
                </tr>
              </g:each>
            </tbody>
          </table>

          <table class="ui table">
            <thead>
              <tr>
                <g:each in="${validationResult.soHeaderLine}" var="c">
                  <th>${c}</th>
                </g:each>
              </tr>
            </thead>
            <tbody>
              <g:each in="${validationResult.tipps}" var="tipp">
              
                <tr>
                  <g:each in="${tipp.row}" var="c">
                    <td>${c}</td>
                  </g:each>
                </tr>
                
                <g:if test="${tipp.messages?.size() > 0}">
                  <tr>
                    <td colspan="${validationResult.soHeaderLine.size()}">
                      <ul>
                        <g:each in="${tipp.messages}" var="msg">
                          <g:if test="${msg instanceof java.lang.String || msg instanceof org.codehaus.groovy.runtime.GStringImpl}">
                            <div class="alert alert-error">${msg}</div>
                          </g:if>
                          <g:else>
                            <div class="alert ${msg.type}">${msg.message}</div>
                          </g:else>
                        </g:each>
                      </ul>
                    </td>
                  </tr>
                </g:if>
              </g:each>
            </tbody>
          </table>
          
        </g:if>

  </body>
</html>
