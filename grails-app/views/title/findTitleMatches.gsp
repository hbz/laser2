<%@ page import="de.laser.Package" %>
<!doctype html>
<html>
  <head>
    <meta name="layout" content="semanticUI">
    <title><g:message code="default.edit.label" args="[entityName ?: message(code:'title.label')]" /></title>
  </head>
  <body>

    <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />${message(code:'title.findTitleMatches.label', default:'New Title - Step 1')}</h1>

    <semui:messages data="${flash}" />

    <p>${message(code:'title.findTitleMatches.note')}</p>

            <semui:searchSegment controller="title" action="findTitleMatches" method="get" >
              <div class="field">
                <label for="proposedTitle">${message(code:'title.findTitleMatches.proposed')}</label>
                <input type="text" id="proposedTitle" name="proposedTitle" value="${params.proposedTitle}" />
              </div>
              <div class="field la-field-right-aligned">
                <a href="${request.forwardURI}" class="ui reset primary button">${message(code:'default.button.searchreset.label')}</a>
                <input type="submit" value="${message(code:'default.button.search.label')}" class="ui secondary button">
              </div>
            </semui:searchSegment>

            <br/>

            <g:if test="${titleMatches != null}">
              <g:if test="${titleMatches.size()>0}">
                <table class="ui celled la-table table">
                  <thead>
                    <tr>
                      <th>${message(code:'title.label')}</th>
                      <th>${message(code:'indentifier.plural')}</th>
                      <th>${message(code:'org.plural', default:'Orgs')}</th>
                      <th>${message(code:'default.key.label')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <g:each in="${titleMatches}" var="titleInstance">
                      <tr>
                        <td>${titleInstance.title} <g:link controller="title" action="show" id="${titleInstance.id}">(${message(code:'default.button.edit.label')})</g:link></td>
                        <td><ul><g:each in="${titleInstance.ids?.sort{it?.ns?.ns}}" var="id"><li>${id.ns.ns}: ${id.value}</li></g:each></ul></td>
                        <td>
                          <ul>
                            <g:each in="${titleInstance.orgs}" var="org">
                              <li>${org.org.name} (${org.roleType?.value?:''})</li>
                            </g:each>
                          </ul>
                        </td>
                        <td>${titleInstance.keyTitle}</td>
                      </tr>
                    </g:each>
                  </tbody>
                </table>
                <semui:msg class="warning" message="title.findTitleMatches.match" args="[params.proposedTitle]" />
                <g:link controller="title" action="createTitle" class="ui negative button" params="${[title:params.proposedTitle, typ: 'Journal']}">${message(code:'title.findTitleMatches.create_for_journal', default:'Create New Journal Title for')} <em>"${params.proposedTitle}"</em></g:link>
                <g:link controller="title" action="createTitle" class="ui negative button" params="${[title:params.proposedTitle, typ: 'Ebook']}">${message(code:'title.findTitleMatches.create_for_ebook', default:'Create New eBook Title for')} <em>"${params.proposedTitle}"</em></g:link>
                <g:link controller="title" action="createTitle" class="ui negative button" params="${[title:params.proposedTitle, typ: 'Database']}">${message(code:'title.findTitleMatches.create_for_database', default:'Create New Database Title for')} <em>"${params.proposedTitle}"</em></g:link>
              </g:if>
              <g:else>
                <semui:msg class="warning" message="title.findTitleMatches.no_match" args="[params.proposedTitle]" />
                <g:link controller="title" action="createTitle" class="ui positive button" params="${[title:params.proposedTitle, typ: 'Journal']}">${message(code:'title.findTitleMatches.create_for_journal', default:'Create New Journal Title for')} <em>"${params.proposedTitle}"</em></g:link>
                <g:link controller="title" action="createTitle" class="ui positive button" params="${[title:params.proposedTitle, typ: 'Ebook']}">${message(code:'title.findTitleMatches.create_for_ebook', default:'Create New eBook Title for')} <em>"${params.proposedTitle}"</em></g:link>
                <g:link controller="title" action="createTitle" class="ui positive button" params="${[title:params.proposedTitle, typ: 'Database']}">${message(code:'title.findTitleMatches.create_for_database', default:'Create New Database Title for')} <em>"${params.proposedTitle}"</em></g:link>
              </g:else>


            </g:if>
            <g:else>
            </g:else>

  </body>
</html>
