<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code:'laser', default:'LAS:eR')} License</title>
</head>

<body>

    <g:render template="breadcrumb" model="${[ license:license, params:params ]}"/>

    <h1 class="ui header">
        <semui:editableLabel editable="${editable}" />
        ${license.licensee?.name} ${license.type?.value} License : <span id="reference" style="padding-top: 5px;">${license.reference}</span>
    </h1>

    <g:render template="nav" />

    <g:render template="/templates/documents/table" model="${[instance:license, redirect:'documents']}"/>

    <g:render template="/templates/documents/modal" model="${[ownobj:license, owntp:'license']}"/>

</body>
</html>
