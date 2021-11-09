<%@page import="de.laser.ReportingFilter;de.laser.reporting.export.GlobalExportHelper;de.laser.helper.DateUtils;de.laser.reporting.report.myInstitution.base.BaseConfig;de.laser.reporting.report.ReportingCache;" %>
<laser:serviceInjection/>

<g:if test="${filterHistory}">
    <div id="history-content"<g:if test="${tab != 'history'}"> class="hidden"</g:if>>
        <div class="ui small header aligned center">
            <i class="icon la-light-grey history large"></i>${message(code:'reporting.filter.history')}
        </div>
        <div class="ui segment">
            <table class="ui single line table compact">
                <g:each in="${filterHistory}" var="fh">
                    <g:set var="fhRCache" value="${new ReportingCache(ReportingCache.CTX_GLOBAL, fh.split('/').last() as String)}" />
                    <g:set var="meta" value="${fhRCache.readMeta()}" />
                    <g:set var="filterCache" value="${fhRCache.readFilterCache()}" />
                    <tr>
                        <td>
                            <g:link controller="myInstitution" action="reporting" class="ui icon button blue la-modern-button"
                                    params="${[filter: meta.filter /*, token: fhRCache.token*/ ] + filterCache.map}">
                                <g:if test="${meta.filter == BaseConfig.KEY_ORGANISATION}">
                                    <i class="ui icon university" aria-hidden="true"></i>
                                </g:if>
                                <g:elseif test="${meta.filter == BaseConfig.KEY_PACKAGE}">
                                    <i class="ui icon gift" aria-hidden="true"></i>
                                </g:elseif>
                                <g:elseif test="${meta.filter == BaseConfig.KEY_LICENSE}">
                                    <i class="ui icon clipboard" aria-hidden="true"></i>
                                </g:elseif>
                                <g:elseif test="${meta.filter == BaseConfig.KEY_SUBSCRIPTION}">
                                    <i class="ui icon balance scale" aria-hidden="true"></i>
                                </g:elseif>
                                <g:else>
                                    <i class="ui icon question" aria-hidden="true"></i>
                                </g:else>
                            </g:link>
                        </td>
                        <td>
                            <div class="content">
                                <div class="header">
                                    <strong>${BaseConfig.getMessage('base.filter.' + meta.filter)}</strong> - ${DateUtils.getSDF_OnlyTime().format(meta.timestamp)}
                                </div>
                                <div class="description">
                                    <g:render template="/myInstitution/reporting/query/generic_filterLabels" model="${[filterLabels: GlobalExportHelper.getCachedFilterLabels(fhRCache.token), simple: true]}" />
                                </div>
                                <div class="footer" style="color:grey">
                                    <%= filterCache.result %>
                                </div>
                            </div>
                        </td>
                        <td>
                            <g:if test="${ReportingFilter.findByToken(fhRCache.token)}">
                            %{--
                            <g:link controller="ajaxHtml" action="reporting" params="${[context: BaseConfig.KEY_MYINST, cmd: 'deleteBookmark', token: "${fhRCache.token}", tab: 'history']}"
                                    class="ui small icon negative la-modern-button button right floated"><i class="icon trash alternate outline"></i></g:link>
                                    --}%
                            </g:if>
                            <g:else>
                                <g:link controller="ajaxHtml" action="reporting" params="${[context: BaseConfig.KEY_MYINST, cmd: 'addBookmark', token: "${fhRCache.token}", tab: 'history']}"
                                        class="ui small icon positive la-modern-button button right floated"><i class="icon plus"></i></g:link>
                            </g:else>
                        </td>
                    </tr>
                </g:each>
            </table>
        </div>
        <div>
            <g:link controller="ajaxHtml" action="reporting" params="${[context: BaseConfig.KEY_MYINST, cmd: 'deleteHistory']}"
                    elementId="history-delete" class="ui button">${message(code:'reporting.filter.history.delete')}</g:link>
        </div>
    </div>
</g:if>
<g:if test="${bookmarks}">
    <div id="bookmark-content"<g:if test="${tab != 'bookmark'}"> class="hidden"</g:if>>
        <div class="ui small header aligned center">
            <i class="icon teal bookmark large"></i>${message(code:'reporting.filter.bookmarks')}
        </div>
        <div class="ui segment">
            <table class="ui single line table compact">
                <g:each in="${bookmarks}" var="fav">
                    <tr>
                        <td>
                            <g:link controller="myInstitution" action="reporting" class="ui icon button blue la-modern-button"
                                params="${[filter: fav.filter /*, token: fhRCache.token*/ ] + fav.getParsedFilterMap()}">
                                <g:if test="${fav.filter == BaseConfig.KEY_ORGANISATION}">
                                    <i class="ui icon university" aria-hidden="true"></i>
                                </g:if>
                                <g:elseif test="${fav.filter == BaseConfig.KEY_PACKAGE}">
                                    <i class="ui icon gift" aria-hidden="true"></i>
                                </g:elseif>
                                <g:elseif test="${fav.filter == BaseConfig.KEY_LICENSE}">
                                    <i class="ui icon clipboard" aria-hidden="true"></i>
                                </g:elseif>
                                <g:elseif test="${fav.filter == BaseConfig.KEY_SUBSCRIPTION}">
                                    <i class="ui icon balance scale" aria-hidden="true"></i>
                                </g:elseif>
                                <g:else>
                                    <i class="ui icon question" aria-hidden="true"></i>
                                </g:else>
                            </g:link>
                        </td>
                        <td>
                            <div class="content">
                                <div class="header">
                                    <strong><semui:xEditable owner="${fav}" field="title" overwriteEditable="true" /></strong>
                                    <g:if test="${fav.id == lastAddedBookmarkId}">
                                        <i id="last-added-bookmark" class="ui icon bookmark small teal"></i>
                                    </g:if>
                                </div>
                                <div class="description">
                                    <g:render template="/myInstitution/reporting/query/generic_filterLabels" model="${[filterLabels: fav.getParsedLabels(), simple: true]}" />
                                </div>
                                <div class="footer">
                                    <semui:xEditable owner="${fav}" field="description" overwriteEditable="true" />
                                </div>
                            </div>
                        </td>
                        <td>
                            <g:link controller="ajaxHtml" action="reporting" params="${[context: BaseConfig.KEY_MYINST, cmd: 'deleteBookmark', token: "${fav.token}", tab: 'bookmark']}"
                                    class="ui small icon negative la-modern-button button right floated"><i class="icon trash alternate outline"></i></g:link>
                        </td>
                    </tr>
                </g:each>
            </table>
        </div>
    </div>
</g:if>

<laser:script file="${this.getGroovyPageFileName()}">
    r2d2.initDynamicXEditableStuff('#hab-wrapper');

    <g:if test="${filterHistory}">
        $('#history-toggle').removeClass('disabled');
    </g:if>
    <g:else>
        $('#history-toggle').addClass('disabled').removeClass('blue');
    </g:else>
    <g:if test="${bookmarks}">
        $('#bookmark-toggle').removeClass('disabled');
    </g:if>
    <g:else>
        $('#bookmark-toggle').addClass('disabled').removeClass('blue');
    </g:else>

    $('#hab-wrapper a.positive, #hab-wrapper a.negative, #history-delete').on( 'click', function(e) {
        e.preventDefault();
        $('#hab-wrapper').load( $(this).attr('href'), function() {});
    })
</laser:script>