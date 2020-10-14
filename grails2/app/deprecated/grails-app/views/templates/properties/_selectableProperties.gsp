%{-- To use, add the g:render custom_props inside a div with id=custom_props_div_xxx, add g:javascript src=properties.js --}%
%{-- on head of container page, and on window load execute  --}%
%{-- c3po.initProperties("<g:createLink controller='ajax' action='lookup'/>", "#custom_props_div_xxx"); --}%

<%@ page import="de.laser.RefdataValue; de.laser.base.AbstractPropertyWithCalculatedLastUpdated; com.k_int.properties.PropertyDefinition; java.net.URL; com.k_int.kbplus.License; de.laser.AuditConfig" %>
<laser:serviceInjection />

<!-- OVERWRITE editable for INST_EDITOR: ${editable} -&gt; ${accessService.checkMinUserOrgRole(user, contextService.getOrg(), 'INST_EDITOR')} -->
<g:set var="overwriteEditable" value="${editable || accessService.checkMinUserOrgRole(user, contextService.getOrg(), 'INST_EDITOR')}" />
<g:set var="overwriteEditable" value="${ ! forced_not_editable}" />

<g:if test="${newProp}">
    <semui:errors bean="${newProp}" />
</g:if>

<table class="ui compact la-table-inCard table">
    <g:set var="properties" value="${showPropClass == PrivateProperty.class? ownobj.privateProperties : ownobj.customProperties}" />
    <g:if test="${properties}">
        <colgroup>
            <g:if test="${show_checkboxes}">
                <col style="width: 5px;">
            </g:if>
            <col style="width: 129px;">
            <col style="width: 96px;">
            <g:if test="${ownobj instanceof License}">
                <col style="width: 359px;">
            </g:if>
            <col style="width: 148px;">
        </colgroup>
        <thead>
            <tr>
                <g:if test="${show_checkboxes}">
                    <th></th>
                </g:if>
                <th class="la-js-dont-hide-this-card">${message(code:'property.table.property')}</th>
                <th>${message(code:'property.table.value')}</th>
                <g:if test="${ownobj instanceof License}">
                    <th>${message(code:'property.table.paragraph')}</th>
                </g:if>
                <th>${message(code:'property.table.notes')}</th>
            </tr>
        </thead>
    </g:if>
    <tbody>
        <g:each in="${properties.sort{a, b -> a.type.getI10n('name').compareToIgnoreCase b.type.getI10n('name')}}" var="prop">
            <g:if test="${showPropClass == CustomProperty.class || ( showPropClass == PrivateProperty.class && prop.type?.tenant?.id == tenant?.id)}">
                <g:if test="${showCopyConflicts}">
                    <tr data-prop-type="${prop.class.simpleName + prop.type.name}">
                </g:if><g:else>
                    <tr>
                </g:else>
                    <g:if test="${show_checkboxes}">
                        <g:if test="${prop.type.multipleOccurrence}">
                            <td><g:checkBox name="subscription.takeProperty" value="${genericOIDService.getOID(prop)}" checked="false"/></td>
                        </g:if><g:else>
                            <td><g:checkBox name="subscription.takeProperty" value="${genericOIDService.getOID(prop)}" data-prop-type="${prop.class.simpleName + prop.type.name}" checked="false"/></td>
                        </g:else>
                    </g:if>
                    <td>
                        ${prop.type.getI10n('name')}
                        <g:if test="${prop.type.getI10n('expl') != null && !prop.type.getI10n('expl').contains(' °')}">
                            <g:if test="${prop.type.getI10n('expl')}">
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center" data-content="${prop.type.getI10n('expl')}">
                                    <i class="question circle icon"></i>
                                </span>
                            </g:if>
                        </g:if>
                        <%
                            if (showPropClass == CustomProperty.class) {
                                if (AuditConfig.getConfig(prop)) {
                                    println '&nbsp; <span  class="la-popup-tooltip la-delay" data-content="Wert wird vererbt." data-position="top right"><i class="icon thumbtack blue"></i></span>'
                                }

                                if (prop.hasProperty('instanceOf') && prop.instanceOf && AuditConfig.getConfig(prop.instanceOf)) {
                                    if (ownobj.isSlaved) {
                                        println '&nbsp; <span class="la-popup-tooltip la-delay" data-content="Wert wird automatisch geerbt." data-position="top right"><i class="icon thumbtack blue"></i></span>'
                                    }
                                    else {
                                        println '&nbsp; <span class="la-popup-tooltip la-delay" data-contet="Wert wird geerbt." data-position="top right"><i class="icon thumbtack grey"></i></span>'
                                    }
                                }
                            }
                        %>

                        <g:if test="${showPropClass == CustomProperty.class && prop.type.mandatory}">
                            <span data-position="top right"  class="la-popup-tooltip la-delay" data-content="${message(code:'default.mandatory.tooltip')}">
                                <i class="star icon yellow"></i>
                            </span>
                        </g:if>
                        <g:if test="${prop.type.multipleOccurrence}">
                            <span data-position="top right"  class="la-popup-tooltip la-delay" data-content="${message(code:'default.multipleOccurrence.tooltip')}">
                                <i class="redo icon orange"></i>
                            </span>
                        </g:if>
                    </td>
                    <td>
                        <g:if test="${prop.type.isIntegerType()}">
                            <semui:xEditable owner="${prop}" type="number" field="intValue" overwriteEditable="${overwriteEditable}" />
                        </g:if>
                        <g:elseif test="${prop.type.isStringType()}">
                            <semui:xEditable owner="${prop}" type="text" field="stringValue" overwriteEditable="${overwriteEditable}" />
                        </g:elseif>
                        <g:elseif test="${prop.type.isBigDecimalType()}">
                            <semui:xEditable owner="${prop}" type="text" field="decValue" overwriteEditable="${overwriteEditable}" />
                        </g:elseif>
                        <g:elseif test="${prop.type.isDateType()}">
                            <semui:xEditable owner="${prop}" type="date" field="dateValue" overwriteEditable="${overwriteEditable}" />
                        </g:elseif>
                        <g:elseif test="${prop.type.isURLType()}">
                            <semui:xEditable owner="${prop}" type="url" field="urlValue" overwriteEditable="${overwriteEditable}" class="la-overflow la-ellipsis" />
                        </g:elseif>
                        <g:elseif test="${prop.type.isRefdataValueType()}">
                            <semui:xEditableRefData owner="${prop}" type="text" field="refValue" config="${prop.type.refdataCategory}" overwriteEditable="${overwriteEditable}" />
                        </g:elseif>
                    </td>
                    <g:if test="${ownobj instanceof License}">
                        <td>
                            <semui:xEditable owner="${prop}" type="textarea" field="paragraph"/>
                        </td>
                    </g:if>
                    <td>
                        <semui:xEditable owner="${prop}" type="textarea" field="note" overwriteEditable="${overwriteEditable}" />
                    </td>
                </tr>
            </g:if>
        </g:each>
    </tbody>
</table>
<g:if test="${error}">
    <semui:msg class="negative" header="${message(code: 'myinst.message.attention')}" text="${error}"/>
</g:if>
<r:script>
    $('input:checkbox').change( function(event) {
        if (this.checked) {
            var dPropType = $(this).attr('data-prop-type');
            $('.table tr[data-prop-type="' + dPropType + '"]').addClass('trWarning')
        } else {
            var dPropType = $(this).attr('data-prop-type');
            $('.table tr[data-prop-type="' + dPropType + '"]').removeClass('trWarning')
        }
    })
</r:script>
<style>
table tr.trWarning td {
    background-color:tomato !important;
    text-decoration: line-through;
}
/*table tr.trWarning td[data-uri]:hover,*/
/*table tr.trWarning td[data-context]:hover {*/
    /*cursor: pointer;*/
/*}*/
</style>
