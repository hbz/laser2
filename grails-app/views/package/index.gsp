<%@page import="de.laser.Org; de.laser.Package; de.laser.Platform" %>
<!doctype html>

<html>
<head>
    <meta name="layout" content="laser">
    <title>${message(code: 'laser')} : ${message(code: 'package.show.all')}</title>
</head>

<body>

<semui:breadcrumbs>
    <semui:crumb message="package.show.all" class="active"/>
</semui:breadcrumbs>

<h1 class="ui left floated aligned icon header la-clear-before"><semui:headerIcon/>${message(code: 'package.show.all')}
<semui:totalNumber total="${recordsCount}"/>
</h1>

<semui:messages data="${flash}"/>

<g:render template="/templates/filter/javascript" />
<semui:filter showFilterButton="true">
    <g:form action="index" method="get" params="${params}" class="ui form">
        <input type="hidden" name="offset" value="${params.offset}"/>

        <div class="three fields">
            <div class="field">
                <label for="search-title">
                    <g:message code='default.search.text'/>
                    <span data-position="right center" data-variation="tiny" class="la-popup-tooltip la-delay" data-content="${message(code:'default.search.tooltip.package')}">
                        <i class="question circle icon"></i>
                    </span>
                </label>
                <input id="search-title" name="q" placeholder="${message(code:'default.search.ph')}" value="${params.q}"/>
            </div>

            <%-- not implemented until ERMS-3044 is finalised --%>
            <div class="field">
                <label for="provider"><g:message code='default.provider.label'/></label>
                <input id="provider"/>
            </div>
            <div class="field">
                <label for="componentType"><g:message code='subscription.resource.label'/></label>
                <input id="componentType"/>
            </div>
        </div>

        <div class="three fields">
            <div class="field">
                <label for="curatoryGroup"><g:message code='package.curatoryGroup.label'/></label>
                <input id="curatoryGroup"/>
            </div>
            <div class="field"></div>
            <div class="field la-field-right-aligned">
                <a href="${request.forwardURI}"
                   class="ui reset primary button">${message(code: 'default.button.filterreset.label')}</a>
                <button type="submit" name="search" value="yes"
                        class="ui secondary button">${message(code: 'default.button.filter.label')}</button>
            </div>
        </div>

    </g:form>
</semui:filter>
<div class="ui icon info message">
    <i class="exclamation triangle icon"></i>
    <i class="close icon"></i>
    <div class="content">
        <div class="header">
            ${message(code: 'message.attention')}
        </div>

        <p>${message(code: 'message.attention.needTime')}</p>
    </div>
</div>

<div class="twelve wide column la-clear-before">
    <div>
        <g:if test="${records}">

            <div id="resultsarea">
                <table class="ui sortable celled la-table table">
                    <thead>
                    <tr>
                        <th>${message(code: 'sidewide.number')}</th>
                        <g:sortableColumn property="name" title="${message(code: 'package.show.pkg_name')}"
                                          params="${params}"/>
                        <g:sortableColumn property="titleCount" title="${message(code: 'package.compare.overview.tipps')}"
                                          params="${params}"/>
                        <g:sortableColumn property="providerName" title="${message(code: 'package.content_provider')}"
                                          params="${params}"/>
                        <g:sortableColumn property="nominalPlatformName" title="${message(code: 'package.nominalPlatform')}"
                                          params="${params}"/>
                        <g:sortableColumn property="curatoryGroups" title="${message(code: 'package.curatoryGroup.label')}"
                                          params="${params}"/>
                        <th>${message(code: 'package.listVerifiedDate.label')}</th>
                        <th>${message(code: 'package.scope')}</th>
                        <th>${message(code: 'package.contentType.label')}</th>
                        <th>${message(code: 'package.packageListStatus')}</th>
                        <sec:ifAllGranted roles="ROLE_YODA">
                            <th class="x"></th>
                        </sec:ifAllGranted>
                    </tr>
                    </thead>
                    <tbody>
                    <g:each in="${records}" var="record" status="jj">
                        <tr>
                            <g:set var="pkg" value="${Package.findByGokbId(record.uuid)}"/>
                            <g:set var="org" value="${Org.findByGokbId(record.providerUuid)}"/>
                            <g:set var="plat" value="${Platform.findByGokbId(record?.platformUuid)}"/>
                            <td>${(params.int('offset') ?: 0) + jj + 1}</td>
                            <td>
                                <!--${record} -->
                                <%--UUID: ${record.uuid} --%>
                                <%--Package: ${Package.findByGokbId(record.uuid)} --%>
                                <g:if test="">
                                    <g:link controller="package" action="show"
                                            id="${pkg.id}">${record.name}</g:link>
                                </g:if>
                                <g:else>
                                    ${record.name} <a target="_blank"
                                                      href="${record.editUrl ? record.editUrl + '/gokb/public/packageContent/' + record.uuid : '#'}"><i
                                            title="GOKB Link" class="external alternate icon"></i></a>
                                </g:else>
                            </td>
                            <td>
                                <g:if test="${record.titleCount}">
                                    <g:if test="${record.titleCount == 1}">
                                        <g:if test="${pkg}">
                                            <g:link controller="package" action="current"
                                                    id="${pkg.id}">${message(code: 'package.index.result.titles.single')}</g:link>
                                        </g:if>
                                        <g:else>
                                            ${message(code: 'package.index.result.titles.single')}
                                        </g:else>
                                    </g:if>
                                    <g:else>
                                        <g:if test="${pkg}">
                                            <g:link controller="package" action="current"
                                                    id="${pkg.id}">${message(code: 'package.index.result.titles', args: [record.titleCount])}</g:link>
                                        </g:if>
                                        <g:else>
                                            ${message(code: 'package.index.result.titles', args: [record.titleCount])}
                                        </g:else>

                                    </g:else>
                                </g:if>
                                <g:else>
                                    ${message(code: 'package.index.result.titles.unknown')}
                                </g:else>
                            </td>
                            <td><g:if test="${org}"><g:link
                                    controller="organisation" action="show"
                                    id="${org.id}">${record.providerName}</g:link></g:if>
                            <g:else>${record.providerName}</g:else>
                            </td>
                            <td><g:if test="${plat}"><g:link
                                    controller="platform" action="show"
                                    id="${plat.id}">${record.platformName}</g:link></g:if>
                                <g:else>${record.platformName}</g:else></td>
                            <td>
                                <div class="ui bulleted list">
                                <g:each in="${record.curatoryGroups}" var="curatoryGroup">
                                    <div class="item">${curatoryGroup}</div>
                                </g:each>
                                </div>
                            </td>
                            <td>${record.listVerifiedDate}</td>
                            <td>${record.scope}</td>
                            <td>${record.contentType}</td>
                            <td class="center aligned">
                                <g:if test="${record.listStatus == 'In Progress'}">
                                    <span class="la-popup-tooltip la-delay" data-position="right center" data-content="${message(code:'package.show.record.listStatus.inProgress')}">
                                        <i class="exclamation triangle yellow icon"></i>
                                    </span>
                                </g:if>
                                <g:elseif test="${record.listStatus == 'Checked'}">
                                    <span class="la-popup-tooltip la-delay" data-position="right center" data-content="${message(code:'package.show.record.listStatus.Checked')}">
                                        <i class="check green circle icon"></i>
                                    </span>
                                </g:elseif>
                            </td>
                            <sec:ifAllGranted roles="ROLE_YODA">
                                <td>
                                    <g:link class="ui button" controller="yoda" action="retriggerPendingChanges" params="${[packageUUID:record.uuid]}"><g:message code="menu.yoda.retriggerPendingChanges"/></g:link>
                                </td>
                            </sec:ifAllGranted>
                        </tr>
                    </g:each>
                    </tbody>
                </table>
            </div><!-- #resultsarea -->

            <semui:paginate action="index" controller="package" params="${params}"
                            next="${message(code: 'default.paginate.next')}"
                            prev="${message(code: 'default.paginate.prev')}" max="${max}"
                            total="${recordsCount}"/>

        </g:if>
        <g:else>
            <p><g:message code="default.search.empty" /></p>
        </g:else>
    </div>
</div>

</body>
</html>
