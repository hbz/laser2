<%@ page import="com.k_int.kbplus.Org; de.laser.RefdataCategory; de.laser.RefdataValue; de.laser.Combo"%>

<!doctype html>
<html>
    <head>
        <meta name="layout" content="semanticUI">
        <g:set var="entityName" value="${message(code: 'org.label')}" />
        <title>${message(code:'laser')} : <g:message code="default.edit.label" args="[entityName]" /></title>
    </head>
    <body>

        <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon /><g:message code="default.edit.label" args="[entityName]" /></h1>

        <semui:messages data="${flash}" />

        <semui:errors bean="${orgInstance}" />

        <div class="ui grid">
            <div class="twelve wide column">

              <fieldset>
                <g:form class="ui form" action="edit" id="${orgInstance?.id}" >
                  <g:hiddenField name="version" value="${orgInstance?.version}" />
                  <fieldset>
                    <f:all bean="orgInstance"/>
                    <div class="ui form-actions">
                      <button type="submit" class="ui button">
                        <i class="checkmark icon"></i>
                        <g:message code="default.button.update.label" />
                      </button>
                      <button type="submit" class="ui negative button" name="_action_delete" formnovalidate>
                        <i class="trash alternate icon"></i>
                        <g:message code="default.button.delete.label" />
                      </button>
                    </div>
                  </fieldset>
                </g:form>
              </fieldset>

            </div><!-- .twelve -->

            <aside class="four wide column">
                <semui:card text="${entityName}">
                    <div class="content">
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
                    </div>
                </semui:card>
            </aside><!-- .four -->

        </div><!-- .grid -->
    </body>
</html>
