<%@ page import="de.laser.helper.RDStore;de.laser.helper.RDConstants;com.k_int.kbplus.CostItemElementConfiguration;de.laser.RefdataValue;com.k_int.kbplus.CostItem" %>
<laser:serviceInjection />

<g:each in="${cost_items}" var="ci" status="jj">
    <%
        String elementSign = 'notSet'
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
            ${ jj + 1 + counterHelper}
        </td>
        <td>
            <semui:xEditable emptytext="${message(code:'default.button.edit.label')}" owner="${ci}" field="costTitle" />
        </td>
        <g:if test="${!forSingleSubscription}">
            <td>
                <g:if test="${ci.sub}">${ci.sub} (${formatDate(date:ci.sub.startDate,format:message(code: 'default.date.format.notime'))} - ${formatDate(date: ci.sub.endDate, format: message(code: 'default.date.format.notime'))})</g:if>
                <g:else>${message(code:'financials.clear')}</g:else>
            </td>
        </g:if>
        <td>
            <span class="la-popup-tooltip la-delay" data-position="right center" data-content="${dataTooltip}">${raw(icon)}</span>
        </td>
        <td>
            <span class="costData"
                  data-costInLocalCurrency="<g:formatNumber number="${ci.costInLocalCurrency}" locale="en" maxFractionDigits="2"/>"
                  data-costInLocalCurrencyAfterTax="<g:formatNumber number="${ci.costInLocalCurrencyAfterTax ?: 0.0}" locale="en" maxFractionDigits="2"/>"
                  data-billingCurrency="${ci.billingCurrency ?: 'EUR'}"
                  data-costInBillingCurrency="<g:formatNumber number="${ci.costInBillingCurrency}" locale="en" maxFractionDigits="2"/>"
                  data-costInBillingCurrencyAfterTax="<g:formatNumber number="${ci.costInBillingCurrencyAfterTax ?: 0.0}" locale="en" maxFractionDigits="2"/>"
                  ${cieString}
            >
                <g:formatNumber number="${ci.costInBillingCurrency ?: 0.0}" type="currency" currencyCode="${ci.billingCurrency ?: 'EUR'}"/>
                <br />
                <g:formatNumber number="${ci.costInBillingCurrencyAfterTax ?: 0.0}" type="currency" currencyCode="${ci.billingCurrency ?: 'EUR'}"/>
                <g:if test="${ci.taxKey && ci.taxKey.display}">
                    (${ci.taxRate ?: 0}%)
                </g:if>
                <g:elseif test="${ci.taxKey == CostItem.TAX_TYPES.TAX_REVERSE_CHARGE}">
                    (${RDStore.TAX_REVERSE_CHARGE.getI10n("value")})
                </g:elseif>
            </span>
        </td>
        <td>
            <g:formatNumber number="${ci.costInLocalCurrency}" type="currency" currencyCode="EUR" />
            <br />
            <g:formatNumber number="${ci.costInLocalCurrencyAfterTax ?: 0.0}" type="currency" currencyCode="EUR" />
            <g:if test="${ci.taxKey && ci.taxKey.display}">
                (${ci.taxRate ?: 0}%)
            </g:if>
            <g:elseif test="${ci.taxKey == CostItem.TAX_TYPES.TAX_REVERSE_CHARGE}">
                (${RDStore.TAX_REVERSE_CHARGE.getI10n("value")})
            </g:elseif>
        </td>
        <td>
            <semui:xEditableRefData config="${RDConstants.COST_ITEM_STATUS}" emptytext="${message(code:'default.button.edit.label')}" owner="${ci}" field="costItemStatus" />
        </td>
        <td>
            <semui:xEditable owner="${ci}" type="date" field="startDate" />
            <br />
            <semui:xEditable owner="${ci}" type="date" field="endDate" />
        </td>
        <td>
            ${ci.costItemElement?.getI10n("value")}
        </td>
        <td class="x">
            <g:if test="${editable}">
                <g:if test="${forSingleSubcription}">
                    <g:link mapping="subfinanceEditCI" params='[fixedSub:"${fixedSubscription?.id}", id:"${ci.id}", tab:"owner"]' class="ui icon button trigger-modal">
                        <i class="write icon"></i>
                    </g:link>
                    <span  class="la-popup-tooltip la-delay" data-position="top right" data-content="${message(code:'financials.costItem.copy.tooltip')}">
                        <g:link mapping="subfinanceCopyCI" params='[fixedSub:"${fixedSubscription?.id}", id:"${ci.id}", tab:"owner"]' class="ui icon button trigger-modal">
                            <i class="copy icon"></i>
                        </g:link>
                    </span>
                </g:if>
                <g:else>
                    <g:link controller="finance" action="editCostItem" id="${ci.id}" class="ui icon button trigger-modal">
                        <i class="write icon"></i>
                    </g:link>
                </g:else>
            </g:if>
            <g:if test="${editable}">
                <g:link controller="finance" action="deleteCostItem" id="${ci.id}" params="[ tab:'owner']" class="ui icon negative button"
                        onclick="return confirm('${message(code: 'default.button.confirm.delete')}')">
                    <i class="trash alternate icon"></i>
                </g:link>
            </g:if>
        </td>
    </tr>
</g:each>