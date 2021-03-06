<%@ page import="de.laser.titles.BookInstance; de.laser.helper.RDStore; de.laser.ApiSource" %>
<div class="sixteen wide column">
    <g:set var="counter" value="${offset + 1}"/>
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
            <g:sortableColumn class="ten wide" params="${params}" property="tipp.sortName"
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
                    <g:if test="${params.tab == 'selectedIEs' && isContainedByTarget && targetIE?.acceptStatus == RDStore.IE_ACCEPT_STATUS_UNDER_CONSIDERATION && editable}">
                        <input type="checkbox" name="bulkflag" data-index="${ie.id}" class="bulkcheck">
                    </g:if>
                    <g:elseif test="${!isContainedByTarget && editable}">
                        <input type="checkbox" name="bulkflag" data-index="${ie.id}" class="bulkcheck">
                    </g:elseif>

                </td>
                <td>${counter++}</td>
                <td class="titleCell">

                    <g:if test="${targetIE}">
                        <semui:ieAcceptStatusIcon status="${targetIE?.acceptStatus}"/>
                    </g:if>
                    <g:else>
                        <div class="la-inline-flexbox la-popup-tooltip la-delay">
                            <i class="icon"></i>
                        </div>
                    </g:else>

                    <!-- START TEMPLATE -->
                        <g:render template="/templates/title"
                                  model="${[ie: ie, tipp: ie.tipp, apisources: ApiSource.findAllByTypAndActive(ApiSource.ApiTyp.GOKBAPI, true),
                                            showPackage: showPackage, showPlattform: showPlattform, showCompact: true, showEmptyFields: false]}"/>
                    <!-- END TEMPLATE -->
                </td>
                <td>
                    <g:if test="${targetIE?.priceItems}">
                            <g:each in="${targetIE.priceItems}" var="priceItem" status="i">
                                <g:message code="tipp.price.listPrice"/>: <semui:xEditable field="listPrice"
                                                                                     owner="${priceItem}"
                                                                                     format=""/> <semui:xEditableRefData
                                    field="listCurrency" owner="${priceItem}"
                                    config="Currency"/> <%--<g:formatNumber number="${priceItem.listPrice}" type="currency" currencyCode="${priceItem.listCurrency.value}" currencySymbol="${priceItem.listCurrency.value}"/>--%><br/>
                                <g:message code="tipp.price.localPrice"/>: <semui:xEditable field="localPrice"
                                                                                      owner="${priceItem}"/> <semui:xEditableRefData
                                    field="localCurrency" owner="${priceItem}"
                                    config="Currency"/> <%--<g:formatNumber number="${priceItem.localPrice}" type="currency" currencyCode="${priceItem.localCurrency.value}" currencySymbol="${priceItem.listCurrency.value}"/>--%>
                                <semui:xEditable field="startDate" type="date"
                                                 owner="${priceItem}"/><semui:dateDevider/><semui:xEditable
                                    field="endDate" type="date"
                                    owner="${priceItem}"/>  <%--<g:formatDate format="${message(code:'default.date.format.notime')}" date="${priceItem.startDate}"/>--%>
                                <g:if test="${i < targetIE.priceItems.size() - 1}"><hr></g:if>
                                <g:set var="sumlistPrice" value="${sumlistPrice + (priceItem.listPrice ?: 0)}"/>
                                <g:set var="sumlocalPrice" value="${sumlocalPrice + (priceItem.localPrice ?: 0)}"/>
                            </g:each>
                    </g:if>
                    <g:else>
                        <g:if test="${ie?.priceItems}">
                            <g:each in="${ie.priceItems}" var="priceItem" status="i">
                                <g:message code="tipp.price.listPrice"/>: <semui:xEditable field="listPrice"
                                                                                     owner="${priceItem}"
                                                                                     format=""/> <semui:xEditableRefData
                                    field="listCurrency" owner="${priceItem}"
                                    config="Currency"/> <%--<g:formatNumber number="${priceItem.listPrice}" type="currency" currencyCode="${priceItem.listCurrency.value}" currencySymbol="${priceItem.listCurrency.value}"/>--%><br/>
                                <g:message code="tipp.price.localPrice"/>: <semui:xEditable field="localPrice"
                                                                                      owner="${priceItem}"/> <semui:xEditableRefData
                                    field="localCurrency" owner="${priceItem}"
                                    config="Currency"/> <%--<g:formatNumber number="${priceItem.localPrice}" type="currency" currencyCode="${priceItem.localCurrency.value}" currencySymbol="${priceItem.listCurrency.value}"/>--%>
                                <semui:xEditable field="startDate" type="date"
                                                 owner="${priceItem}"/><semui:dateDevider/><semui:xEditable
                                    field="endDate" type="date"
                                    owner="${priceItem}"/>  <%--<g:formatDate format="${message(code:'default.date.format.notime')}" date="${priceItem.startDate}"/>--%>
                                <g:if test="${i < ie.priceItems.size() - 1}"><hr></g:if>
                                <g:set var="sumlistPrice" value="${sumlistPrice + (priceItem.listPrice ?: 0)}"/>
                                <g:set var="sumlocalPrice" value="${sumlocalPrice + (priceItem.localPrice ?: 0)}"/>
                            </g:each>
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
            <th><g:message code="financials.export.sums"/> <br />
                <g:message code="tipp.price.listPrice"/>: <g:formatNumber number="${sumlistPrice}" type="currency"/><br />
                %{--<g:message code="tipp.price.localPrice"/>: <g:formatNumber number="${sumlocalPrice}" type="currency"/>--}%
            </th>
            <th></th>
        </tr>
        </tfoot>
    </table>
</div>
