<!-- _result_tab_subscr.gsp -->
<%@ page import="de.laser.helper.RDStore; com.k_int.kbplus.*; de.laser.*" %>

<laser:serviceInjection />

<table id="costTable_${i}" data-queryMode="${i}" class="ui celled sortable table table-tworow la-table la-ignore-fixed">
    <thead>
        <tr>
            <g:if test="${fixedSubscription}">
                <th>${message(code:'sidewide.number')}</th>
                <th class="la-no-uppercase"><span class="la-popup-tooltip la-delay" data-content="${message(code:'financials.costItemConfiguration')}" data-position="left center"><i class="money bill alternate icon"></i></span></th>
                <g:sortableColumn property="ci.costInBillingCurrency" title="${message(code:'financials.invoice_total')}" params="[subscrSort: true, sub: fixedSubscription.id]" mapping="subfinance"/>
                <g:sortableColumn property="ci.costInLocalCurrency" title="${message(code:'financials.newCosts.value')}" params="[subscrSort: true, sub: fixedSubscription.id]" mapping="subfinance"/>
                <g:sortableColumn property="ci.costItemElement" title="${message(code:'financials.costItemElement')}" params="[subscrSort: true, sub: fixedSubscription.id]" mapping="subfinance"/>
                <g:sortableColumn property="sub.name" title="${message(code:'default.subscription.label')}" params="[subscrSort: true, sub: fixedSubscription.id]" mapping="subfinance"/>
                <g:sortableColumn property="subPkg.pkg" title="${message(code:'package.label')}" params="[subscrSort: true, sub: fixedSubscription.id]" mapping="subfinance"/>
                <th></th>
            </g:if>
            <g:else>
                <th>${message(code:'sidewide.number')}</th>
                <th class="la-no-uppercase"><span class="la-popup-tooltip la-delay" data-content="${message(code:'financials.costItemConfiguration')}" data-position="left center"><i class="money bill alternate icon"></i></span></th>
                <g:sortableColumn property="ci.costInBillingCurrency" title="${message(code:'financials.invoice_total')}" params="[subscrSort: true]"/>
                <g:sortableColumn property="ci.costInLocalCurrency" title="${message(code:'financials.newCosts.value')}" params="[subscrSort: true]"/>
                <g:sortableColumn property="ci.costItemElement" title="${message(code:'financials.costItemElement')}" params="[subscrSort: true]"/>
                <g:sortableColumn property="sub.name" title="${message(code:'default.subscription.label')}" params="[subscrSort: true]"/>
                <g:sortableColumn property="subPkg.pkg" title="${message(code:'package.label')}" params="[subscrSort: true]"/>
                <g:if test="${accessService.checkPermAffiliation("ORG_INST","INST_EDITOR")}">
                    <th class="la-action-info">${message(code:'default.actions.label')}</th>
                </g:if>
            </g:else>
        </tr>
    </thead>
    <tbody>
        %{--Empty result set--}%
        <g:if test="${data.count == 0}">
            <tr>
                <td colspan="8" style="text-align:center">
                    <br />
                    <g:if test="${msg}">${msg}</g:if>
                    <g:else>${message(code:'finance.result.filtered.empty')}</g:else>
                    <br />
                </td>
            </tr>
        </g:if>
        <g:else>
            <g:each in="${data.costItems}" var="ci" status="jj">
                <%
                    def elementSign = 'notSet'
                    String icon = ''
                    String dataTooltip = ""
                    if(ci.costItemElementConfiguration) {
                        elementSign = ci.costItemElementConfiguration
                    }
                    switch(elementSign) {
                        case RDStore.CIEC_POSITIVE:
                            dataTooltip = message(code:'financials.costItemConfiguration.positive')
                            icon = '<i class="plus green circle icon"></i>'
                            break
                        case RDStore.CIEC_NEGATIVE:
                            dataTooltip = message(code:'financials.costItemConfiguration.negative')
                            icon = '<i class="minus red circle icon"></i>'
                            break
                        case RDStore.CIEC_NEUTRAL:
                            dataTooltip = message(code:'financials.costItemConfiguration.neutral')
                            icon = '<i class="circle yellow icon"></i>'
                            break
                        default:
                            dataTooltip = message(code:'financials.costItemConfiguration.notSet')
                            icon = '<i class="question circle icon"></i>'
                            break
                    }
                %>
                <tr id="bulkdelete-b${ci.id}">
                    <td>
                        <% int offset = subscrOffset ? subscrOffset : 0 %>
                        ${ jj + 1 + offset }
                    </td>
                    <td>
                        <span class="la-popup-tooltip la-delay" data-position="right center" data-content="${dataTooltip}">${raw(icon)}</span>
                    </td>
                    <td>
                        <g:formatNumber number="${ci.costInBillingCurrencyAfterTax ?: 0.0}" type="currency" currencyCode="${ci.billingCurrency ?: 'EUR'}" />
                    </td>
                    <td>
                        <g:formatNumber number="${ci.costInLocalCurrencyAfterTax ?: 0.0}" type="currency" currencyCode="EUR" />
                    </td>
                    <td>
                        ${ci.costItemElement?.getI10n('value')}
                    </td>
                    <td>
                        <g:link controller="subscription" action="show" id="${ci.sub?.id}">${ci.sub}</g:link>
                    </td>
                    <td>
                        <g:link controller="package" action="show" id="${ci.subPkg?.pkg?.id}">${ci.subPkg?.pkg}</g:link>
                    </td>
                    <g:if test="${accessService.checkPermAffiliation("ORG_INST","INST_EDITOR")}">
                        <td class="x">
                            <g:if test="${editable}">
                                <g:if test="${fixedSubscription}">
                                    <span class="la-popup-tooltip la-delay" data-position="top right" data-content="${message(code:'financials.costItem.transfer.tooltip')}">
                                        <g:link mapping="subfinanceCopyCI" params='[sub:"${fixedSubscription.id}", id:"${ci.id}", showView:"own"]' class="ui icon button trigger-modal">

                                            <i class="la-copySend icon"></i>

                                            <i class="icon copy-send"></i>

                                        </g:link>
                                    </span>
                                </g:if>
                                <g:else>
                                    <span class="la-popup-tooltip la-delay" data-position="top right" data-content="${message(code:'financials.costItem.transfer.tooltip')}">
                                        <g:link controller="finance" action="copyCostItem" params='[sub:"${ci.sub?.id}", id:"${ci.id}", showView:"own"]' class="ui icon button trigger-modal">

                                            <i class="la-copySend icon"></i>

                                            <i class="icon copy-send"></i>

                                        </g:link>
                                    </span>
                                </g:else>
                            </g:if>
                        </td>
                    </g:if>
                </tr>
            </g:each>
        </g:else>
    </tbody>
    <tfoot>
        <g:if test="${data.count > 0 && data.sums.billingSums}">
            <tr>
                <th colspan="8">
                    ${message(code:'financials.totalCost')}
                </th>
            </tr>
            <g:each in="${data.sums.billingSums}" var="entry">
                <tr>
                    <td colspan="2">
                        ${message(code:'financials.sum.billing')} ${entry.currency}<br>
                    </td>
                    <td class="la-exposed-bg">
                        <g:formatNumber number="${entry.billingSumAfterTax}" type="currency" currencySymbol="${entry.currency}"/>
                    </td>
                    <td colspan="5">

                    </td>
                </tr>
            </g:each>
            <tr>
                <td colspan="3">
                    ${message(code:'financials.sum.local')}
                </td>
                <td class="la-exposed-bg">
                    <g:formatNumber number="${data.sums.localSums.localSumAfterTax}" type="currency" currencySymbol="EUR"/>
                </td>
                <td colspan="4">

                </td>
            </tr>
        </g:if>
        <g:elseif test="${data.count > 0 && !data.sums.billingSums}">
            <tr>
                <td colspan="8">
                    ${message(code:'financials.noCostsConsidered')}
                </td>
            </tr>
        </g:elseif>
        <tr>
            <td colspan="8">
                <div class="ui fluid accordion">
                    <div class="title">
                        <i aria-hidden="true" class="dropdown icon" ></i>
                        <strong>${message(code: 'financials.calculationBase')}</strong>
                    </div>
                    <div class="content">
                        <%
                            def argv0 = contextService.getOrg().costConfigurationPreset ? contextService.getOrg().costConfigurationPreset.getI10n('value') : message(code:'financials.costItemConfiguration.notSet')
                        %>
                        ${message(code: 'financials.calculationBase.paragraph1', args: [argv0])}
                        <p>
                            ${message(code: 'financials.calculationBase.paragraph2')}
                        </p>
                    </div>
                </div>
            </td>
        </tr>
    </tfoot>
</table>
<g:if test="${data.costItems}">
    <g:if test="${fixedSubscription}">
        <semui:paginate mapping="subfinance" params="${params+[showView:'subscr']}"
                        next="${message(code: 'default.paginate.next')}"
                        prev="${message(code: 'default.paginate.prev')}"
                        max="${max}" offset="${subscrOffset ? subscrOffset : 0}" total="${data.count}"/>
    </g:if>
    <g:else>
        <semui:paginate action="finance" controller="myInstitution" params="${params+[showView:'subscr']}"
                        next="${message(code: 'default.paginate.next')}"
                        prev="${message(code: 'default.paginate.prev')}"
                        max="${max}" offset="${subscrOffset ? subscrOffset : 0}" total="${data.count}"/>
    </g:else>
</g:if>
<!-- _result_tab_subscr.gsp -->
