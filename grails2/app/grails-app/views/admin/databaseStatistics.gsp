<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code:'laser')} : ${message(code: "menu.admin.databaseStatistics")}</title>
</head>

<body>

<semui:breadcrumbs>
    <semui:crumb message="menu.admin.dash" controller="admin" action="index"/>
    <semui:crumb message="menu.admin.databaseStatistics" class="active"/>
</semui:breadcrumbs>

<h2 class="ui header la-noMargin-top">${message(code: "menu.admin.databaseStatistics")}</h2>

<div class="ui grid">
    <div class="twelve wide column">

        <table class="ui sortable celled la-table compact la-ignore-fixed table">
            <thead>
                <tr>
                    <th>Schema</th>
                    <th>Tabelle</th>
                    <th>Anzahl</th>
                </tr>
            </thead>
            <tbody>
                <g:each in="${statistic}" var="row">
                    <tr>
                        <td>${row[0]}</td>
                        <td>${row[1]}</td>
                        <td><g:formatNumber number="${row[2]}" type="number" /></td>
                    </tr>
                </g:each>
            </tbody>
        </table>

    </div>
</div>

</body>
</html>
