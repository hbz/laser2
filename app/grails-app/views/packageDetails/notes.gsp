<%@ page import="com.k_int.kbplus.Package" %>
<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI">
    <g:set var="entityName" value="${message(code: 'package.label', default: 'Package')}"/>
    <title><g:message code="default.edit.label" args="[entityName]"/></title>
</head>

<body>

    <semui:breadcrumbs>
        <semui:crumb controller="packageDetails" action="index" text="${message(code:'package.show.all', default:'All Packages')}" />
        <semui:crumb text="${packageInstance.name}" id="${packageInstance.id}" class="active"/>
        <div class="pull-right">
            <semui:exportDropdown>
                <semui:exportDropdownItem>
                    <g:link action="show" params="${params+[format:'json']}">JSON</g:link>
                </semui:exportDropdownItem>
                <semui:exportDropdownItem>
                    <g:link action="show" params="${params+[format:'xml']}">XML</g:link>
                </semui:exportDropdownItem>

                <g:each in="${transforms}" var="transkey,transval">
                    <semui:exportDropdownItem>
                        <g:link action="show" id="${params.id}" params="${[format:'xml', transformId:transkey, mode:params.mode]}"> ${transval.name}</g:link>
                    </semui:exportDropdownItem>
                </g:each>
            </semui:exportDropdown>
        </div>
    </semui:breadcrumbs>

    <semui:modeSwitch controller="packageDetails" action="show" params="${params}"/>

<semui:messages data="${flash}" />

<div>
    <h1 class="ui header">${packageInstance?.name}</h1>
    <g:render template="nav"/>
</div>

<div>
    <g:render template="/templates/notes_table" model="${[instance: packageInstance, redirect: 'notes']}"/>

</div>
<g:render template="/templates/addNote"
          model="${[doclist: packageInstance.documents, ownobj: packageInstance, owntp: 'pkg']}"/>
</body>
</html>
