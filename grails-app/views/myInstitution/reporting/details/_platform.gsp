<%@ page import="de.laser.reporting.report.GenericHelper; de.laser.RefdataValue; de.laser.helper.RDConstants; de.laser.helper.DateUtils; de.laser.reporting.export.GlobalExportHelper; de.laser.reporting.report.myInstitution.base.BaseConfig; de.laser.reporting.report.myInstitution.base.BaseFilter; de.laser.ApiSource; de.laser.helper.RDStore; de.laser.reporting.report.myInstitution.base.BaseDetails;" %>
<laser:serviceInjection />

<g:render template="/myInstitution/reporting/details/top" />

<g:set var="filterCache" value="${GlobalExportHelper.getFilterCache(token)}"/>
<g:set var="esRecords" value="${filterCache.data.platformESRecords ?: [:]}"/>
<g:set var="esRecordIds" value="${esRecords.keySet().collect{Long.parseLong(it)} ?: []}"/>
<g:set var="wekb" value="${ApiSource.findByTypAndActive(ApiSource.ApiTyp.GOKBAPI, true)}"/>

<div class="ui segment" id="reporting-detailsTable">
    <table class="ui table la-table compact">
        <thead>
            <tr>
                <%
                    String key = GlobalExportHelper.getCachedExportStrategy(token)
                    Map<String, Boolean> dtConfig = BaseConfig.getCurrentDetailsTableConfig( key )
                %>
                <th></th>
                <g:each in="${dtConfig}" var="k,b">
                    <g:set var="label" value="${ BaseDetails.getFieldLabelforColumns( key, k ) }" />
                    <g:if test="${b}">
                        <th data-column="dtc:${k}">${label}</th>
                    </g:if>
                    <g:else>
                        <th data-column="dtc:${k}" class="hidden">${label}</th>
                    </g:else>
                </g:each>
            </tr>
        </thead>
        <tbody>
            <g:each in="${list}" var="plt" status="i">
                <tr>
                    <td>${i + 1}.</td>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="name">

                        <g:link controller="platform" action="show" id="${plt.id}" target="_blank">${plt.name}</g:link>
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="org">

                        <g:if test="${plt.org}">
                            <g:link controller="org" action="show" id="${plt.org.id}" target="_blank">${plt.org.sortname ?: plt.org.name}</g:link>
                        </g:if>
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="primaryUrl">

                        <g:if test="${plt.primaryUrl}">
                            <a href="${plt.primaryUrl}" target="_blank">${plt.primaryUrl}</a>
                        </g:if>
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="serviceProvider">

                        ${plt.serviceProvider?.getI10n('value')}
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="softwareProvider">

                        ${plt.softwareProvider?.getI10n('value')}
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="status">

                        ${plt.status?.getI10n('value')}
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="platform-ipAuthentication">

                        <laser:reportDetailsTableEsValue key="${key}" id="${plt.id}" field="ipAuthentication" records="${esRecords}" />
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="platform-shibbolethAuthentication">

                        <laser:reportDetailsTableEsValue key="${key}" id="${plt.id}" field="shibbolethAuthentication" records="${esRecords}" />
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="platform-passwordAuthentication">

                        <laser:reportDetailsTableEsValue key="${key}" id="${plt.id}" field="passwordAuthentication" records="${esRecords}" />
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="platform-proxySupported">

                        <laser:reportDetailsTableEsValue key="${key}" id="${plt.id}" field="proxySupported" records="${esRecords}" />
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="platform-statisticsFormat">

                        <laser:reportDetailsTableEsValue key="${key}" id="${plt.id}" field="statisticsFormat" records="${esRecords}" />
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="platform-statisticsUpdate">

                        <laser:reportDetailsTableEsValue key="${key}" id="${plt.id}" field="statisticsUpdate" records="${esRecords}" />
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="platform-counterCertified">

                        <laser:reportDetailsTableEsValue key="${key}" id="${plt.id}" field="counterCertified" records="${esRecords}" />
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="platform-counterR3Supported">

                        <laser:reportDetailsTableEsValue key="${key}" id="${plt.id}" field="counterR3Supported" records="${esRecords}" />
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="platform-counterR4Supported">

                        <laser:reportDetailsTableEsValue key="${key}" id="${plt.id}" field="counterR4Supported" records="${esRecords}" />
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="platform-counterR5Supported">

                        <laser:reportDetailsTableEsValue key="${key}" id="${plt.id}" field="counterR5Supported" records="${esRecords}" />
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="platform-counterR4SushiApiSupported">

                        <laser:reportDetailsTableEsValue key="${key}" id="${plt.id}" field="counterR4SushiApiSupported" records="${esRecords}" />
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="platform-counterR5SushiApiSupported">

                        <laser:reportDetailsTableEsValue key="${key}" id="${plt.id}" field="counterR5SushiApiSupported" records="${esRecords}" />
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="___lastUpdated">

                        <g:if test="${esRecordIds.contains(plt.id)}">
                            <g:formatDate format="${message(code:'default.date.format.notime')}" date="${DateUtils.parseDateGeneric(esRecords.getAt(plt.id.toString()).lastUpdatedDisplay)}" />
                        </g:if>
                        <g:else>
                            <g:formatDate format="${message(code:'default.date.format.notime')}" date="${plt._getCalculatedLastUpdated()}" />
                        </g:else>
                    </laser:reportDetailsTableTD>

                    <laser:reportDetailsTableTD config="${dtConfig}" field="___wekb">

                        <g:if test="${wekb?.baseUrl && plt.gokbId}">
                            <g:if test="${esRecordIds.contains(plt.id)}">
                                <a href="${wekb.baseUrl + '/public/platformContent/' + plt.gokbId}" target="_blank"><i class="icon external alternate"></i></a>
                            </g:if>
                            <g:else>
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-content="${message(code:'reporting.query.base.noCounterpart.label')}"
                                      data-position="top right">
                                    <i class="icon times grey"></i>
                                </span>
                            </g:else>
                        </g:if>
                    </laser:reportDetailsTableTD>

                    %{--
                    <g:if test="${query in [ 'platform-x-property' ]}">
                        <td>
                            <laser:reportObjectProperties owner="${plt}" tenant="${contextService.getOrg()}" propDefId="${id}" />
                        </td>
                    </g:if>
                    --}%
                </tr>
            </g:each>
        </tbody>
    </table>
</div>

<g:render template="/myInstitution/reporting/export/detailsModal" model="[modalID: 'detailsExportModal', token: token]" />