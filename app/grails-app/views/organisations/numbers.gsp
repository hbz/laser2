<%@ page
        import="com.k_int.kbplus.Org"
        import="com.k_int.kbplus.Person"
        import="com.k_int.kbplus.PersonRole"
        import="com.k_int.kbplus.RefdataValue"
        import="com.k_int.kbplus.RefdataCategory"
%>

<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI">
    <g:set var="entityName" value="${message(code: 'org.label', default: 'Org')}" />
    <title>${message(code:'laser', default:'LAS:eR')} : <g:message code="default.show.label" args="[entityName]" /></title>
</head>
<body>

<g:render template="breadcrumb" model="${[ orgInstance:orgInstance, params:params ]}"/>

<h1 class="ui header"><semui:headerIcon />
${orgInstance.name}
</h1>

<g:render template="nav" contextPath="." />

<semui:messages data="${flash}" />


</body>
</html>