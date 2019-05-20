<g:set var="deletionService" bean="deletionService" />

<!doctype html>
<html>
    <head>
        <meta name="layout" content="semanticUI"/>
        <title>${message(code:'laser', default:'LAS:eR')} : ${message(code:'subscription.label')}</title>
</head>

<body>
    <g:render template="breadcrumb" model="${[ subscription:subscription, params:params ]}"/>

    <h1 class="ui left aligned icon header"><semui:headerIcon />
        ${subscription.name}
    </h1>

    <g:if test="${deletionService.RESULT_SUCCESS != result?.status}">
        <g:render template="nav" />
    </g:if>

    <g:if test="${preview}">
        <semui:msg class="info" header=""
                   text="Wollen Sie die ausgewählte Lizenz endgültig aus dem System entfernen?" />
        <br />
        <g:link controller="subscription" action="show" params="${[id: subscription.id]}" class="ui button">Vorgang abbrechen</g:link>
        <g:if test="${editable}">
            <g:link controller="subscription" action="delete" params="${[id: subscription.id, process: true]}" class="ui button red">Lizenzen löschen</g:link>
        </g:if>
        <br />

        <table class="ui celled la-table la-table-small table">
            <thead>
            <tr>
                <th>Anhängende, bzw. referenzierte Objekte</th>
                <th>Anzahl</th>
                <th>Objekt-Ids</th>
            </tr>
            </thead>
            <tbody>
            <g:each in="${preview.sort()}" var="stat">
                <tr>
                    <td>
                        ${stat.key}
                    </td>
                    <td>
                        ${stat.value.size()}
                    </td>
                    <td>
                        ${stat.value.collect{ item -> item.hasProperty('id') ? item.id : 'x'}.join(', ')}
                    </td>
                </tr>
            </g:each>
            </tbody>
        </table>
    </g:if>

    <g:if test="${result?.status == deletionService.RESULT_SUCCESS}">
        <semui:msg class="positive" header=""
                   text="Löschvorgang wurde erfolgreich durchgeführt." />
        <g:link controller="myInstitution" action="currentSubscriptions" class="ui button">Meine Lizenzen</g:link>
    </g:if>
    <g:if test="${result?.status == deletionService.RESULT_QUIT}">
        <semui:msg class="negative" header="Löschvorgang abgebrochen"
                   text="Es existieren Teilnehmerlizenzen. Diese müssen zuerst gelöscht werden." />
        <g:link controller="subscription" action="delete" params="${[id: subscription.id]}" class="ui button">Zur Übersicht</g:link>
    </g:if>
    <g:if test="${result?.status == deletionService.RESULT_ERROR}">
        <semui:msg class="negative" header="Unbekannter Fehler"
                   text="Der Löschvorgang wurde abgebrochen." />
        <g:link controller="subscription" action="delete" params="${[id: subscription.id]}" class="ui button">Zur Übersicht</g:link>
    </g:if>

</body>
</html>
