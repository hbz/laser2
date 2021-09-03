<%@ page import="de.laser.helper.RDStore; org.springframework.context.i18n.LocaleContextHolder; de.laser.Subscription" %>
<laser:serviceInjection />
<%-- r:require module="annotations" / --%>

<!doctype html>
<html>
    <head>
        <meta name="layout" content="laser">
        <title>${message(code:'laser')} : ${message(code:'subscription.details.stats.label')}</title>
    </head>
    <body>
        <semui:debugInfo>
            <g:render template="/templates/debug/benchMark" model="[debug: benchMark]" />
        </semui:debugInfo>
        <g:render template="breadcrumb" model="${[ params:params ]}"/>
        <semui:controlButtons>
            <g:render template="actions" />
        </semui:controlButtons>
        <h1 class="ui icon header la-noMargin-top">
            <semui:headerIcon />${subscription.name}
        </h1>
        <semui:anualRings object="${subscription}" controller="subscription" action="show" navNext="${navNextSubscription}" navPrev="${navPrevSubscription}"/>

        <g:render template="nav" />

        <semui:objectStatus object="${subscription}" status="${subscription.status}" />
        <g:render template="message" />
        <semui:messages data="${flash}" />
        <g:if test="${showConsortiaFunctions && !subscription.instanceOf}">
            <div class="ui segment">
                <table class="ui celled table">
                    <tr>
                        <th><g:message code="default.usage.consortiaTableHeader"/></th>
                    </tr>
                    <g:each in="${Subscription.executeQuery('select new map(sub.id as memberId, org.sortname as memberName) from OrgRole oo join oo.org org join oo.sub sub where sub.instanceOf = :parent and oo.roleType in (:subscrRoles) order by org.sortname asc', [parent: subscription, subscrRoles: [RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIBER_CONS_HIDDEN]])}" var="row">
                        <tr>
                            <td><g:link action="stats" id="${row.memberId}">${row.memberName}</g:link></td>
                        </tr>
                    </g:each>
                </table>
            </div>
        </g:if>
        <g:else>
            <g:render template="/templates/filter/javascript"/>
            <semui:filter showFilterButton="true">
                <g:form action="stats" class="ui form" method="get">
                    <g:hiddenField name="tab" value="${params.tab}"/>
                    <g:hiddenField name="id" value="${subscription.id}"/>
                    <g:hiddenField name="sort" value="${params.sort}"/>
                    <g:hiddenField name="order" value="${params.order}"/>
                    <div class="four fields">
                        <div class="field">
                            <label for="series_names">${message(code: 'titleInstance.seriesName.label')}</label>

                            <select name="series_names" id="series_names" multiple=""
                                    class="ui search selection dropdown">
                                <option value="">${message(code: 'default.select.choose.label')}</option>

                                <g:each in="${controlledListService.getAllPossibleSeriesBySub(subscription)}" var="seriesName">
                                    <option <%=(params.list('series_names')?.contains(seriesName)) ? 'selected="selected"' : ''%>
                                            value="${seriesName}">
                                        ${seriesName}
                                    </option>
                                </g:each>
                            </select>
                        </div>

                        <div class="field">
                            <label for="subject_reference">${message(code: 'titleInstance.subjectReference.label')}</label>

                            <select name="subject_references" id="subject_reference" multiple=""
                                    class="ui search selection dropdown">
                                <option value="">${message(code: 'default.select.choose.label')}</option>

                                <g:each in="${controlledListService.getAllPossibleSubjectsBySub(subscription)}" var="subject">
                                    <option <%=(params.list('subject_references')?.contains(subject)) ? 'selected="selected"' : ''%>
                                            value="${subject}">
                                        ${subject}
                                    </option>
                                </g:each>
                            </select>
                        </div>

                        <div class="field">
                            <label for="ddc">${message(code: 'titleInstance.ddc.label')}</label>

                            <select name="ddcs" id="ddc" multiple=""
                                    class="ui search selection dropdown">
                                <option value="">${message(code: 'default.select.choose.label')}</option>

                                <g:each in="${controlledListService.getAllPossibleDdcsBySub(subscription)}" var="ddc">
                                    <option <%=(params.list('ddcs')?.contains(ddc.id.toString())) ? 'selected="selected"' : ''%>
                                            value="${ddc.id}">
                                        ${ddc.value} - ${ddc.getI10n("value")}
                                    </option>
                                </g:each>
                            </select>
                        </div>

                        <div class="field">
                            <label for="language">${message(code: 'titleInstance.language.label')}</label>

                            <select name="languages" id="language" multiple="multiple"
                                    class="ui search selection dropdown">
                                <option value="">${message(code: 'default.select.choose.label')}</option>

                                <g:each in="${controlledListService.getAllPossibleLanguagesBySub(subscription)}" var="language">
                                    <option <%=(params.list('languages')?.contains(language.id.toString())) ? 'selected="selected"' : ''%>
                                            value="${language.id}">
                                        ${language.getI10n("value")}
                                    </option>
                                </g:each>
                            </select>
                        </div>
                    </div>
                    <div class="three fields">
                        <div class="field">
                            <label for="metricType"><g:message code="default.usage.metricType"/></label>
                            <select name="metricType" id="metricType" multiple="multiple" class="ui selection dropdown">
                                <option value=""><g:message code="default.select.choose.label"/></option>
                                <g:each in="${metricTypes}" var="metricType">
                                    <option <%=(params.list('metricType')?.contains(metricType)) ? 'selected="selected"' : ''%>
                                            value="${metricType}">
                                        ${metricType}
                                    </option>
                                </g:each>
                            </select>
                        </div>

                        <div class="field">
                            <label for="reportType"><g:message code="default.usage.reportType"/></label>
                            <select name="reportType" id="reportType" multiple="multiple" class="ui selection dropdown">
                                <option value=""><g:message code="default.select.choose.label"/></option>
                                <g:each in="${reportTypes}" var="reportType">
                                    <option <%=(params.list('reportType')?.contains(reportType)) ? 'selected="selected"' : ''%>
                                            value="${reportType}">
                                        <g:message code="default.usage.${reportType}"/>
                                    </option>
                                </g:each>
                            </select>
                        </div>

                        <div class="field la-field-right-aligned">
                            <g:link action="stats" id="${subscription.id}" class="ui reset primary button">${message(code: 'default.button.reset.label')}</g:link>
                            <input type="submit" class="ui secondary button"
                                   value="${message(code: 'default.button.filter.label')}"/>
                        </div>
                    </div>
                </g:form>
            </semui:filter>
            <semui:tabs>
                <semui:tabsItem controller="subscription" action="stats" params="${params + [tab: 'total']}" text="${message(code: 'default.usage.allUsageGrid.header')}" tab="total" counts="${c4total}"/>
                <g:each in="${monthsInRing}" var="month">
                    <semui:tabsItem controller="subscription" action="stats" params="${params + [tab: month.format("yyyy-MM")]}" text="${month.format("yyyy-MM")}" tab="${month.format("yyyy-MM")}" counts="${c4total}"/>
                </g:each>
            </semui:tabs>
            <div class="ui bottom attached tab active segment">
                <g:if test="${sums}">
                    <table class="ui celled la-table table">
                        <thead>
                            <tr>
                                <th><g:message code="default.usage.date"/></th>
                                <th><g:message code="default.usage.metricType"/></th>
                                <th><g:message code="default.usage.reportCount"/></th>
                            </tr>
                        </thead>
                        <tbody>
                            <g:each in="${sums}" var="row">
                                <tr>
                                    <td><g:formatDate date="${row.reportMonth}" format="yyyy-MM"/></td>
                                    <td>${row.metricType}</td>
                                    <td>${row.reportCount}</td>
                                </tr>
                            </g:each>
                        </tbody>
                    </table>
                </g:if>
                <g:elseif test="${usages}">
                    <table class="ui sortable celled la-table table">
                        <thead>
                            <tr>
                                <g:if test="${usages[0].title}">
                                    <g:sortableColumn title="${message(code:"default.title.label")}" property="title.name"/>
                                </g:if>
                                <g:sortableColumn title="${message(code:"default.usage.metricType")}" property="r.metricType"/>
                                <g:sortableColumn title="${message(code:"default.usage.reportCount")}" property="r.reportCount"/>
                            </tr>
                        </thead>
                        <tbody>
                            <g:each in="${usages}" var="row">
                                <tr>
                                    <g:if test="${row.title}">
                                        <td>${row.title.name}</td>
                                    </g:if>
                                    <td>${row.metricType}</td>
                                    <td>${row.reportCount}</td>
                                </tr>
                            </g:each>
                        </tbody>
                    </table>
                    <semui:paginate total="${total}" params="${params}" max="${max}" offset="${offset}"/>
                </g:elseif>
            </div>
        </g:else>

    </body>
</html>
