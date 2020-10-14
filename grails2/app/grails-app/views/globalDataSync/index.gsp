<%@ page import="de.laser.Package" %>
<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI">
    <g:set var="entityName" value="${message(code: 'globalDataSync.label')}"/>
    <title>${message(code:'laser')} : <g:message code="default.list.label" args="[entityName]"/></title>
</head>

<body>

<h1 class="ui left aligned icon header la-clear-before"><semui:headerIcon /><g:message code="globalDataSync.label"/></h1>

<semui:messages data="${flash}"/>


<semui:filter>
    <g:form action="index" method="get" class="ui form">
        <div class="two fields">
            <div class="field">
                <label for="q">${message(code: 'globalDataSync.search.text')}</label>
                <input type="text" id="q" name="q" placeholder="${message(code: 'globalDataSync.search.ph')}"
                       value="${params.q}"/>
            </div>
            <div class="field la-field-right-aligned">
                <a href="${request.forwardURI}" class="ui reset primary button">${message(code:'default.button.filterreset.label')}</a>
                <input type="submit" class="ui secondary button"
                       value="${message(code: 'default.button.filter.label')}"/>
            </div>
        </div>
    </g:form>
    <g:form action="index" method="get" class="ui form">
        <div class="fields">
            <div class="field">
                <g:link class="ui secondary button" params="[sort: 'ts', max: max, offset: offset, order: order]"><g:message
                        code="globalDataSync.updated"/></g:link>
            </div>
        </div>
    </g:form>
</semui:filter>

<div>
    <g:form action="index" method="get" class="ui form">
        <div class="fields">
            <div class="field">
                <label>${message(code: 'package.type.change')}</label>
            </div>

            <div class="field">
                <label>&nbsp;</label>
                <g:select name="rectype" from="[0: 'Package', 1: 'Title']" onchange="this.form.submit()"
                          value="${rectype}" optionKey="key" optionValue="value"/>
            </div>
        </div>
    </g:form>

    <g:if test="${items != null}">
        <div class="container" style="text-align:center">
            ${message(code: 'globalDataSync.pagination.text', args: [offset, (offset.toInteger() + max.toInteger()), globalItemTotal])}
        </div>
    </g:if>
    <table class="ui sortable celled la-table table">
        <thead>
        <tr>
            <g:sortableColumn property="identifier" title="${message(code: 'default.identifier.label')}"/>
            <th>${message(code: 'package.name.slash.description')}</th>
            <g:sortableColumn property="source.name" title="${message(code: 'package.source.label')}"/>
            <g:sortableColumn property="kbplusCompliant" title="${message(code: 'package.kbplusCompliant.label')}"/>
            <g:sortableColumn property="globalRecordInfoStatus"
                              title="${message(code: 'default.status.label')}"/>
            <th>${message(code: 'globalDataSync.tippscount')}</th>
            <th>${message(code: 'default.actions.label')}</th>
        </tr>
        </thead>
        <tbody>
        <g:each in="${items}" var="item" status="k">
            <tr>
                <td><a href="${item.source.baseEditUrl}resource/show/${item.identifier}">${fieldValue(bean: item, field: "identifier")}</a><br/>
                    <g:message code="globalDataSync.updated.brackets"
                               args="[formatDate(date: item.ts, format: 'dd.MM.yyyy HH:mm')]"/></td>
                <td><a href="${item.source.baseEditUrl}resource/show/${item.identifier}">${fieldValue(bean: item, field: "name")}</a>
                <hr><a href="${item.source.baseEditUrl}resource/show/${item.identifier}">${fieldValue(bean: item, field: "desc")}</a>
                </td>
                <td><a href="${item.source.editUri}?verb=getRecord&amp;identifier=${item.identifier}&amp;metadataPrefix=${item.source.fullPrefix}">
                    ${item.source.name}</a></td>
                %{--<td><a href="${item.source.baseUrl}search/index?qbe=g:1packages">${item.displayRectype}</a></td>--}%
                <td>${item.kbplusCompliant?.getI10n('value')}</td>
                <td>${item.globalRecordInfoStatus?.getI10n('value')}</td>
                <td>${tippcount[k]}</td>
                <g:if test="${item.globalRecordInfoStatus?.value != 'Current'}">
                    <td><g:link action="newCleanTracker" controller="globalDataSync" id="${item.id}"
                                class="ui negative button"
                                onclick="return confirm('${message(code: 'globalDataSync.trackingDeleted')}')">
                        ${message(code: 'globalDataSync.track_new')}</g:link><hr>
                    <g:link action="selectLocalPackage" controller="globalDataSync" id="${item.id}"
                            class="ui negative button"
                            onclick="return confirm('${message(code: 'globalDataSync.trackingDeleted')}')">
                        ${message(code: 'globalDataSync.track_merge')}</g:link>
                    </td>
                </g:if>
                <g:else>
                    <td><g:link action="newCleanTracker" controller="globalDataSync" id="${item.id}"
                                class="ui positive button">${message(code: 'globalDataSync.track_new')}</g:link><hr>
                    <g:link action="selectLocalPackage" controller="globalDataSync" id="${item.id}"
                            class="ui positive button">${message(code: 'globalDataSync.track_merge')}</g:link>
                    </td>
                </g:else>
            </tr>
            <g:each in="${item.trackers}" var="tracker">
                <tr>
                    <td colspan="6">
                        -> ${message(code: 'globalDataSync.using_id')}
                        <g:if test="${tracker.localOid != null}">
                            <g:if test="${tracker.localOid.startsWith(Package.class.name)}">
                                <g:link controller="package" action="show"
                                        id="${tracker.localOid.split(':')[1]}">
                                    ${tracker.name ?: message(code: 'globalDataSync.noname')}</g:link>
                                <g:if test="${tracker.name == null}">
                                    <g:set var="confirm"
                                           value="${message(code: 'globalDataSync.cancel.confirm.noname')}"/>
                                </g:if>
                                <g:else>
                                    <g:set var="confirm"
                                           value="${message(code: 'globalDataSync.cancel.confirm', args: [tracker.name])}"/>
                                </g:else>
                                <g:link controller="globalDataSync" action="cancelTracking" class="ui negative button"
                                        params="[trackerId: tracker.id, itemName: fieldValue(bean: item, field: 'name')]"
                                        onclick="return confirm('${confirm}')">
                                    <g:message code="globalDataSync.cancel"/>
                                </g:link>
                            </g:if>
                        </g:if>
                        <g:else>No tracker local oid</g:else>
                    </td>
                </tr>
            </g:each>
        </g:each>
        </tbody>
    </table>

    <semui:paginate action="index" controller="globalDataSync" params="${params}" next="Next" prev="Prev" max="${max}"
                    total="${globalItemTotal}"/>

</div>
</body>
</html>
