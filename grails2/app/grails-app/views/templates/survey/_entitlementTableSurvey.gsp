<%@ page import="de.laser.titles.BookInstance; de.laser.helper.RDStore; de.laser.ApiSource" %>
<div class="sixteen wide column">
    <g:set var="counter" value="${1}"/>
    <g:set var="sumlistPrice" value="${0}"/>
    <g:set var="sumlocalPrice" value="${0}"/>


    <table class="ui sortable celled la-table table la-ignore-fixed la-bulk-header" id="surveyEntitlements">
        <thead>
        <tr>
            <th>
                <g:if test="${editable}">
                    <input class="select-all" type="checkbox" name="chkall">
                </g:if>
            </th>
            <th>${message(code: 'sidewide.number')}</th>
            <g:sortableColumn class="ten wide" params="${params}" property="tipp.title.sortTitle"
                              title="${message(code: 'title.label')}"/>
            <th class="two wide"><g:message code="tipp.price"/></th>
            <th class="two wide"><g:message code="default.actions.label"/></th>
        </tr>
        </thead>
        <tbody>

        <g:each in="${ies.sourceIEs}" var="ie">
            <g:set var="tipp" value="${ie.tipp}"/>
            <g:set var="isContainedByTarget"
                   value="${ies.targetIEs.find { it.tipp == tipp && it.status != RDStore.TIPP_STATUS_DELETED }}"/>
            <g:set var="targetIE" value="${ies.targetIEs.find { it.tipp == tipp }}"/>
            <tr data-gokbId="${tipp.gokbId}" data-ieId="${ie?.id}" data-index="${counter}">
                <td>

                    <g:if test="${!isContainedByTarget && editable}">
                        <input type="checkbox" name="bulkflag" data-index="${ie.id}" class="bulkcheck">
                    </g:if>

                </td>
                <td>${params.offset ? params.offset + counter++ : counter++}</td>
                <td class="titleCell">

                    <g:if test="${targetIE}">
                        <semui:ieAcceptStatusIcon status="${targetIE?.acceptStatus}"/>
                    </g:if>
                    <g:else>
                        <div class="la-inline-flexbox la-popup-tooltip la-delay">
                            <i class="icon"></i>
                        </div>
                    </g:else>

                    <semui:listIcon type="${ie.tipp.title.class.name}"/>
                    <strong><g:link controller="title" action="show"
                                    id="${tipp.title.id}">${tipp.title.title}</g:link></strong>

                    <g:if test="${tipp.hostPlatformURL}">
                        <semui:linkIcon href="${tipp.hostPlatformURL.startsWith('http') ? tipp.hostPlatformURL : 'http://' + tipp.hostPlatformURL}"/>
                    </g:if>
                    <br>

                    <div class="la-icon-list">
                        <g:if test="${tipp.title instanceof BookInstance && tipp.title.volume}">
                            <div class="item">
                                <i class="grey icon la-books la-popup-tooltip la-delay"
                                   data-content="${message(code: 'tipp.volume')}"></i>

                                <div class="content">
                                    ${tipp.title.volume}
                                </div>
                            </div>
                        </g:if>

                        <g:if test="${tipp.title instanceof BookInstance && (tipp.title.firstAuthor || tipp.title.firstEditor)}">
                            <div class="item">
                                <i class="grey icon user circle la-popup-tooltip la-delay"
                                   data-content="${message(code: 'author.slash.editor')}"></i>

                                <div class="content">
                                    ${tipp.title.getEbookFirstAutorOrFirstEditor()}
                                </div>
                            </div>
                        </g:if>

                        <g:if test="${tipp.title instanceof BookInstance && tipp.title.editionStatement}">
                            <div class="item">
                                <i class="grey icon copy la-popup-tooltip la-delay"
                                   data-content="${message(code: 'title.editionStatement.label')}"></i>

                                <div class="content">
                                    ${tipp.title.editionStatement}
                                </div>
                            </div>
                        </g:if>

                        <g:if test="${tipp.title instanceof BookInstance && tipp.title.summaryOfContent}">
                            <div class="item">
                                <i class="grey icon desktop la-popup-tooltip la-delay"
                                   data-content="${message(code: 'title.summaryOfContent.label')}"></i>

                                <div class="content">
                                    ${tipp.title.summaryOfContent}
                                </div>
                            </div>
                        </g:if>

                        <g:if test="${tipp.title.seriesName}">
                            <div class="item">
                                <i class="grey icon list la-popup-tooltip la-delay" data-content="${message(code: 'title.seriesName.label')}"></i>
                                <div class="content">
                                    ${tipp.title.seriesName}
                                </div>
                            </div>
                        </g:if>

                        <g:if test="${tipp.title.subjectReference}">
                            <div class="item">
                                <i class="grey icon comment alternate la-popup-tooltip la-delay" data-content="${message(code: 'title.subjectReference.label')}"></i>
                                <div class="content">
                                    ${tipp.title.subjectReference}
                                </div>
                            </div>
                        </g:if>

                    </div>

                    <g:each in="${tipp.title.ids?.sort { it.ns.ns }}" var="id">
                        <span class="ui small blue image label">
                            ${id.ns.ns}: <div class="detail">${id.value}</div>
                        </span>
                    </g:each>

                    <div class="la-icon-list">

                    %{-- <g:if test="${tipp.availabilityStatus?.getI10n('value')}">
                         <div class="item">
                             <i class="grey key icon la-popup-tooltip la-delay" data-content="${message(code: 'default.access.label')}"></i>
                             <div class="content">
                                 ${tipp.availabilityStatus?.getI10n('value')}
                             </div>
                         </div>
                     </g:if>--}%

                        <g:if test="${tipp.status.getI10n("value")}">
                            <div class="item">
                                <i class="grey key icon la-popup-tooltip la-delay"
                                   data-content="${message(code: 'default.status.label')}"></i>

                                <div class="content">
                                    ${tipp.status.getI10n("value")}
                                </div>
                            </div>
                        </g:if>

                        <g:if test="${showPackage}">
                            <div class="item">
                                <i class="grey icon gift scale la-popup-tooltip la-delay"
                                   data-content="${message(code: 'package.label')}"></i>

                                <div class="content">
                                    <g:link controller="package" action="show"
                                            id="${tipp?.pkg?.id}">${tipp?.pkg?.name}</g:link>
                                </div>
                            </div>
                        </g:if>
                        <g:if test="${showPlattform}">
                            <div class="item">
                                <i class="grey icon cloud la-popup-tooltip la-delay"
                                   data-content="${message(code: 'tipp.tooltip.changePlattform')}"></i>

                                <div class="content">
                                    <g:if test="${tipp?.platform.name}">
                                        <g:link controller="platform" action="show" id="${tipp?.platform.id}">
                                            ${tipp?.platform.name}
                                        </g:link>
                                    </g:if>
                                    <g:else>
                                        ${message(code: 'default.unknown')}
                                    </g:else>
                                </div>
                            </div>
                        </g:if>

                        <g:if test="${tipp?.id}">
                            <div class="la-title">${message(code: 'default.details.label')}</div>
                            <g:link class="ui icon tiny blue button la-js-dont-hide-button la-popup-tooltip la-delay"
                                    data-content="${message(code: 'laser')}"
                                    href="${tipp?.hostPlatformURL.contains('http') ? tipp?.hostPlatformURL : 'http://' + tipp?.hostPlatformURL}"
                                    target="_blank"
                                    controller="tipp" action="show"
                                    id="${tipp?.id}">
                                <i class="book icon"></i>
                            </g:link>
                        </g:if>
                        <g:each in="${ApiSource.findAllByTypAndActive(ApiSource.ApiTyp.GOKBAPI, true)}"
                                var="gokbAPI">
                            <g:if test="${tipp?.gokbId}">
                                <a role="button"
                                   class="ui icon tiny blue button la-js-dont-hide-button la-popup-tooltip la-delay"
                                   data-content="${message(code: 'gokb')}"
                                   href="${gokbAPI.baseUrl ? gokbAPI.baseUrl + '/gokb/resource/show/' + tipp?.gokbId : '#'}"
                                   target="_blank"><i class="la-gokb  icon"></i>
                                </a>
                            </g:if>
                        </g:each>

                    </div>
                </td>
                <td>
                    <g:if test="${targetIE?.priceItem}">
                        <g:formatNumber number="${ie?.priceItem?.listPrice}" type="currency"
                                        currencySymbol="${ie?.priceItem?.listCurrency}"
                                        currencyCode="${ie?.priceItem?.listCurrency}"/><br>
                        <g:formatNumber number="${ie?.priceItem?.localPrice}" type="currency"
                                        currencySymbol="${ie?.priceItem?.localCurrency}"
                                        currencyCode="${ie?.priceItem?.localCurrency}"/><br>
                    %{--<semui:datepicker class="ieOverwrite" name="priceDate" value="${ie?.priceItem?.priceDate}" placeholder="${message(code:'tipp.priceDate')}"/>--}%

                        <g:set var="sumlistPrice" value="${sumlistPrice + (ie?.priceItem?.listPrice ?: 0)}"/>
                        <g:set var="sumlocalPrice" value="${sumlocalPrice + (ie?.priceItem?.localPrice ?: 0)}"/>

                    </g:if>
                    <g:else>
                        <g:if test="${ie?.priceItem}">
                            <g:formatNumber number="${ie?.priceItem?.listPrice}" type="currency"
                                            currencySymbol="${ie?.priceItem?.listCurrency}"
                                            currencyCode="${ie?.priceItem?.listCurrency}"/><br>
                            <g:formatNumber number="${ie?.priceItem?.localPrice}" type="currency"
                                            currencySymbol="${ie?.priceItem?.localCurrency}"
                                            currencyCode="${ie?.priceItem?.localCurrency}"/><br>
                        %{--<semui:datepicker class="ieOverwrite" name="priceDate" value="${ie?.priceItem?.priceDate}" placeholder="${message(code:'tipp.priceDate')}"/>--}%

                            <g:set var="sumlistPrice" value="${sumlistPrice + (ie?.priceItem?.listPrice ?: 0)}"/>
                            <g:set var="sumlocalPrice" value="${sumlocalPrice + (ie?.priceItem?.localPrice ?: 0)}"/>

                        </g:if>
                    </g:else>
                </td>
                <td>
                    <g:if test="${isContainedByTarget && targetIE?.acceptStatus == RDStore.IE_ACCEPT_STATUS_UNDER_CONSIDERATION && editable}">
                        <g:link class="ui icon negative button la-popup-tooltip la-delay"
                                action="processRemoveIssueEntitlementsSurvey"
                                params="${[id: newSub.id, singleTitle: isContainedByTarget.id, packageId: packageId, surveyConfigID: surveyConfig?.id]}"
                                data-content="${message(code: 'subscription.details.addEntitlements.remove_now')}">
                            <i class="minus icon"></i>
                        </g:link>
                    </g:if>
                    <g:elseif test="${!isContainedByTarget && editable}">
                        <g:link class="ui icon positive button la-popup-tooltip la-delay"
                                action="processAddIssueEntitlementsSurvey"
                                params="${[id: newSub.id, singleTitle: ie.id, surveyConfigID: surveyConfig?.id]}"
                                data-content="${message(code: 'subscription.details.addEntitlements.add_now')}">
                            <i class="plus icon"></i>
                        </g:link>
                    </g:elseif>
                </td>
            </tr>

        </g:each>
        </tbody>
        <tfoot>
        <tr>
            <th></th>
            <th></th>
            <th></th>
            <th><g:message code="financials.export.sums"/> <br>
                <g:message code="tipp.listPrice"/>: <g:formatNumber number="${sumlistPrice}" type="currency"/><br>
                %{--<g:message code="tipp.localPrice"/>: <g:formatNumber number="${sumlocalPrice}" type="currency"/>--}%
            </th>
            <th></th>
        </tr>
        </tfoot>
    </table>
</div>

<r:script>
    $("simpleHiddenRefdata").editable({
        url: function (params) {
            var hidden_field_id = $(this).data('hidden-id');
            $("#" + hidden_field_id).val(params.value);
            // Element has a data-hidden-id which is the hidden form property that should be set to the appropriate value
        }
    });
</r:script>