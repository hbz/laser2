<%@ page import="com.k_int.kbplus.Org; com.k_int.kbplus.RefdataCategory; com.k_int.kbplus.RefdataValue"; com.k_int.kbplus.Combo %>

<!doctype html>
<html>
    <head>
        <meta name="layout" content="semanticUI">
        <g:set var="entityName" value="${message(code: 'org.label', default: 'Org')}" />
        <title>${message(code:'laser', default:'LAS:eR')} <g:message code="default.edit.label" args="[entityName]" /></title>
    </head>
    <body>

        <h1 class="ui header"><g:message code="default.edit.label" args="[entityName]" /></h1>

        <semui:messages data="${flash}" />

        <g:hasErrors bean="${orgInstance}">
            <bootstrap:alert class="alert-error">
                <ul>
                    <g:eachError bean="${orgInstance}" var="error">
                        <li <g:if test="${error in org.springframework.validation.FieldError}">data-field-id="${error.field}"</g:if>><g:message error="${error}"/></li>
                    </g:eachError>
                </ul>
            </bootstrap:alert>
        </g:hasErrors>

        <div class="ui grid">
            <div class="twelve wide column">

              <fieldset>
                <g:form class="ui form" action="edit" id="${orgInstance?.id}" >
                  <g:hiddenField name="version" value="${orgInstance?.version}" />
                  <fieldset>
                    <f:all bean="orgInstance"/>
                    <div class="ui segment form-actions">
                      <button type="submit" class="ui button">
                        <i class="icon-ok icon-white"></i>
                        <g:message code="default.button.update.label" default="Update" />
                      </button>
                      <button type="submit" class="ui negative button" name="_action_delete" formnovalidate>
                        <i class="icon-trash icon-white"></i>
                        <g:message code="default.button.delete.label" default="Delete" />
                      </button>
                    </div>
                  </fieldset>
                </g:form>
              </fieldset>

            </div><!-- .twelve -->

            <div class="four wide column">
                <semui:card text="${entityName}" class="card-grey">
                    <ul class="nav nav-list">
                        <li>
                            <g:link class="list" action="list">
                                <i class="icon-list"></i>
                                <g:message code="default.list.label" args="[entityName]" />
                            </g:link>
                        </li>
                        <li>
                            <g:link class="create" action="create">
                                <i class="icon-plus"></i>
                                <g:message code="default.create.label" args="[entityName]" />
                            </g:link>
                        </li>
                    </ul>
                </semui:card>
            </div><!-- .four -->

        </div><!-- .grid -->
    </body>
</html>
