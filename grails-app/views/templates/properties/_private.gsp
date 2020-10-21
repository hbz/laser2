%{-- To use, add the g:render custom_props inside a div with id=custom_props_div_xxx, add g:javascript src=properties.js --}%
%{-- on head of container page, and on window load execute  --}%
%{-- c3po.initProperties("<g:createLink controller='ajax' action='lookup'/>", "#custom_props_div_xxx"); --}%

<%@ page import="de.laser.License; de.laser.RefdataValue; de.laser.properties.PropertyDefinition; java.net.URL" %>
<laser:serviceInjection />


<%-- OVERWRITE editable for INST_EDITOR: ${editable} -&gt; ${accessService.checkMinUserOrgRole(user, contextService.getOrg(), 'INST_EDITOR')} --%>
<g:set var="overwriteEditable" value="${editable || accessService.checkPermAffiliationX('ORG_INST','INST_EDITOR','ROLE_ADMIN')}" />

<g:if test="${newProp}">
    <semui:errors bean="${newProp}" />
</g:if>

<table class="ui compact la-table-inCard table">
    <g:set var="privateProperties" value="${ownobj.propertySet.findAll { cp -> cp.type.tenant?.id == contextOrg.id && cp.tenant?.id == contextOrg.id }}"/>
    <g:if test="${privateProperties}">
        <colgroup>
            <col style="width: 129px;">
            <col style="width: 96px;">
            <g:if test="${ownobj instanceof License}">
                <col style="width: 359px;">
            </g:if>
            <col style="width: 148px;">
            <col style="width: 76px;">
        </colgroup>
        <thead>
            <tr>
                <th class="la-js-dont-hide-this-card">${message(code:'property.table.property')}</th>
                <th>${message(code:'property.table.value')}</th>
                <g:if test="${ownobj instanceof License}">
                    <th>${message(code:'property.table.paragraph')}</th>
                </g:if>
                <th>${message(code:'property.table.notes')}</th>
                <th class="la-action-info">${message(code:'default.actions.label')}</th>
            </tr>
        </thead>
    </g:if>
    <tbody>
        <g:each in="${privateProperties.sort{a, b -> a.type.getI10n('name') <=> b.type.getI10n('name') ?: a.getValue() <=> b.getValue() ?: a.id <=> b.id }}" var="prop">
            <g:if test="${prop.type.tenant?.id == tenant?.id}">
                <tr>
                    <td>
                        <g:if test="${prop.type.getI10n('expl') != null && !prop.type.getI10n('expl').contains(' °')}">
                            ${prop.type.getI10n('name')}
                            <g:if test="${prop.type.getI10n('expl')}">
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center" data-content="${prop.type.getI10n('expl')}">
                                    <i class="question circle icon"></i>
                                </span>
                            </g:if>
                        </g:if>
                        <g:else>
                            ${prop.type.getI10n('name')}
                        </g:else>
                        <g:if test="${prop.type.mandatory}">
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
                            <semui:xEditable owner="${prop}" type="url" field="urlValue" overwriteEditable="${overwriteEditable}" class="la-overflow la-ellipsis"/>
                            <g:if test="${prop.value}">
                                <semui:linkIcon href="${prop.value}" />
                            </g:if>
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
                    <td class="x la-js-editmode-container">
                        <g:if test="${overwriteEditable == true}">
                            <laser:remoteLink class="ui icon negative button js-open-confirm-modal"
                                              controller="ajax"
                                              action="deletePrivateProperty"
                                              params='[propClass: prop.getClass(),ownerId:"${ownobj.id}", ownerClass:"${ownobj.class}", editable:"${editable}"]'
                                              id="${prop.id}"
                                              data-confirm-tokenMsg="${message(code: "confirm.dialog.delete.property", args: [prop.type.getI10n('name')])}"
                                              data-confirm-term-how="delete"
                                              data-done="c3po.initProperties('${createLink(controller:'ajaxJson', action:'lookup')}', '#${custom_props_div}', ${tenant?.id})"
                                              data-always="c3po.loadJsAfterAjax()"
                                              data-update="${custom_props_div}"
                                              role="button"
                            >
                                <i class="trash alternate icon"></i>
                            </laser:remoteLink>
                        </g:if>
                    </td>
                </tr>
            </g:if>
        </g:each>
    </tbody>

    <g:if test="${overwriteEditable}">
        <tfoot>
            <tr>
                <g:if test="${privateProperties}">
                    <td colspan="4">
                </g:if>
                <g:else>
                    <td>
                </g:else>
                        <laser:remoteForm url="[controller: 'ajax', action: 'addPrivatePropertyValue']"
                                      name="cust_prop_add_value_private"
                                      class="ui form"
                                      data-update="${custom_props_div}"
                                      data-done="c3po.initProperties('${createLink(controller:'ajaxJson', action:'lookup')}', '#${custom_props_div}', ${tenant?.id})"
                                      data-always="c3po.loadJsAfterAjax()"
                        >
                        <g:if test="${!(actionName.contains('survey') || controllerName.contains('survey'))}">
                            <input type="hidden" name="propIdent"  data-desc="${prop_desc}" class="customPropSelect"/>
                            <input type="hidden" name="ownerId"    value="${ownobj?.id}"/>
                            <input type="hidden" name="tenantId"   value="${tenant?.id}"/>
                            <input type="hidden" name="editable"   value="${editable}"/>
                            <input type="hidden" name="ownerClass" value="${ownobj?.class}"/>
                            <input type="hidden" name="withoutRender" value="${withoutRender}"/>

                            <input type="submit" value="${message(code:'default.button.add.label')}" class="ui button js-wait-wheel"/>
                        </g:if>
                    </laser:remoteForm>

                    </td>
        </tr>
    </tfoot>
</g:if>
</table>
<g:if test="${error}">
    <semui:msg class="negative" header="${message(code: 'myinst.message.attention')}" text="${error}"/>
</g:if>