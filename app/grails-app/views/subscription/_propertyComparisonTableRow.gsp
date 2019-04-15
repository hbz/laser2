<%@page import="com.k_int.properties.PropertyDefinition; de.laser.helper.RDStore;com.k_int.kbplus.*" %>
<% String unknownString = raw("<span data-tooltip=\"${RDStore.PERM_UNKNOWN.getI10n("value")}\"><i class=\"question circle icon  \"></i></span>") %>
<laser:serviceInjection/>
<th>
    <th>${key}</th>
    <th>
        <g:if test="${propBinding && propBinding.get(sourceSubscription)?.visibleForConsortiaMembers}">
            <g:if test="${sourceSubscription}"><g:link controller="subscription" action="show" id="${sourceSubscription?.id}">${sourceSubscription?.name}</g:link></g:if><span class="ui blue tag label">${message(code:'financials.isVisibleForSubscriber')}</span>
        </g:if>
        <g:else>
            <g:if test="${sourceSubscription}"><g:link controller="subscription" action="show" id="${sourceSubscription?.id}">${sourceSubscription?.name}</g:link></g:if>
        </g:else>
    </th>
    <th class="center aligned">${message(code: 'default.copy.label')}</th>
    <th class="center aligned">${message(code: 'default.replace.label')}</th>
    <th class="center aligned">${message(code: 'default.doNothing.label')}</th>
    <th>
        <g:if test="${propBinding && propBinding.get(targetSubscription)?.visibleForConsortiaMembers}">
            <g:if test="${targetSubscription}"><g:link controller="subscription" action="show" id="${targetSubscription?.id}">${targetSubscription?.name}</g:link></g:if><span class="ui blue tag label">${message(code:'financials.isVisibleForSubscriber')}</span>
        </g:if>
        <g:else>
            <g:if test="${targetSubscription}"><g:link controller="subscription" action="show" id="${targetSubscription?.id}">${targetSubscription?.name}</g:link></g:if>
        </g:else>
    </th>
</tr>
<g:each in="${group}" var="prop">
    <% PropertyDefinition propKey = (PropertyDefinition) genericOIDService.resolveOID(prop.getKey()) %>
    <td>
        <td>
            ${propKey.getI10n("name")}
            <g:if test="${propKey.multipleOccurrence}">
                <span data-position="top right" data-tooltip="${message(code:'default.multipleOccurrence.tooltip')}">
                    <i class="redo icon orange"></i>
                </span>
            </g:if>
        </td>
        <g:set var="propValues" value="${prop.getValue()}" />

        %{--SOURCE-SUBSCRIPTION--}%
        <td>
            <g:if test="${propValues.containsKey(sourceSubscription)}">
                <% Set propValuesForSourceSub = propValues.get(sourceSubscription) %>
                <g:each var="propValue" in="${propValuesForSourceSub}">
                    <g:if test="${propValue.type.multipleOccurrence && propValues.get(sourceSubscription).size() > 1}">
                        <g:checkBox name="subscription.takePropertyIds" value="${propValue.id}" checked="${true}" />
                    </g:if>
                    <g:if test="${propValue.type.type == Integer.toString()}">
                        <semui:xEditable owner="${propValue}" type="text" field="intValue" overwriteEditable="${overwriteEditable}" />
                    </g:if>
                    <g:elseif test="${propValue.type.type == String.toString()}">
                        <semui:xEditable owner="${propValue}" type="text" field="stringValue" overwriteEditable="${overwriteEditable}" />
                    </g:elseif>
                    <g:elseif test="${propValue.type.type == BigDecimal.toString()}">
                        <semui:xEditable owner="${propValue}" type="text" field="decValue" overwriteEditable="${overwriteEditable}" />
                    </g:elseif>
                    <g:elseif test="${propValue.type.type == Date.toString()}">
                        <semui:xEditable owner="${propValue}" type="date" field="dateValue" overwriteEditable="${overwriteEditable}" />
                    </g:elseif>
                    <g:elseif test="${propValue.type.type == URL.toString()}">
                        <semui:xEditable owner="${propValue}" type="url" field="urlValue" overwriteEditable="${overwriteEditable}" class="la-overflow la-ellipsis"/>
                        <g:if test="${propValue.value}">
                            <semui:linkIcon />
                        </g:if>
                    </g:elseif>
                    <g:elseif test="${propValue.type.type == RefdataValue.toString()}">
                        <semui:xEditableRefData owner="${propValue}" type="text" field="refValue" config="${propValue.type.refdataCategory}" overwriteEditable="${overwriteEditable}" />
                    </g:elseif>
                    <g:else>
                        propValue.value
                    </g:else>
                    <g:if test="${propValues.get(sourceSubscription)?.size() > 1}"><br></g:if>
                    <g:if test="${propValue?.note}">
                        <div class="ui circular label la-long-tooltip" data-tooltip="${propValue?.note}">Anm.</div><br>
                    </g:if>
                </g:each>
            </g:if>
            <g:else>
                <a class="ui red circular label la-popup-tooltip la-delay" data-content="<g:message code="default.compare.propertyNotSet"/>"><strong>X</strong></a>
            </g:else>
        </td>

        %{--AKTIONEN:--}%
        <%String propKeyId = genericOIDService.getOID(propKey)%>
        <td>
            <g:if test="${propKey.multipleOccurrence}">
                <div class="ui checkbox la-toggle-radio la-append"><input type="radio" name="subscription.takeProperty,${propKeyId}" value="${COPY}" checked ></div>
            </g:if>
        </td>
        <td><div class="ui checkbox la-toggle-radio la-replace"><input type="radio" name="subscription.takeProperty,${propKeyId}" value="${REPLACE}" ${propKey.multipleOccurrence? 'checked' : ''}checked /></div></td>
        <td><div class="ui checkbox la-toggle-radio la-noChange"><input type="radio" name="subscription.takeProperty,${propKeyId}" value="${DO_NOTHING}" /></div></td>

        %{--TARGET-SUBSCRIPTION--}%
        <td>
            <g:if test="${ ! targetSubscription}">
            </g:if>
            <g:elseif test="${propValues.containsKey(targetSubscription)}">
                <% Set propValuesForTargetSub = propValues.get(sourceSubscription) %>
                <g:each var="propValue" in="${propValuesForTargetSub}">
                    <g:if test="${propValue.type.type == Integer.toString()}">
                        <semui:xEditable owner="${propValue}" type="text" field="intValue" overwriteEditable="${overwriteEditable}" />
                    </g:if>
                    <g:elseif test="${propValue.type.type == String.toString()}">
                        <semui:xEditable owner="${propValue}" type="text" field="stringValue" overwriteEditable="${overwriteEditable}" />
                    </g:elseif>
                    <g:elseif test="${propValue.type.type == BigDecimal.toString()}">
                        <semui:xEditable owner="${propValue}" type="text" field="decValue" overwriteEditable="${overwriteEditable}" />
                    </g:elseif>
                    <g:elseif test="${propValue.type.type == Date.toString()}">
                        <semui:xEditable owner="${propValue}" type="date" field="dateValue" overwriteEditable="${overwriteEditable}" />
                    </g:elseif>
                    <g:elseif test="${propValue.type.type == URL.toString()}">
                        <semui:xEditable owner="${propValue}" type="url" field="urlValue" overwriteEditable="${overwriteEditable}" class="la-overflow la-ellipsis"/>
                        <g:if test="${propValue.value}">
                            <semui:linkIcon />
                        </g:if>
                    </g:elseif>
                    <g:elseif test="${propValue.type.type == RefdataValue.toString()}">
                        <semui:xEditableRefData owner="${propValue}" type="text" field="refValue" config="${propValue.type.refdataCategory}" overwriteEditable="${overwriteEditable}" />
                    </g:elseif>
                     <g:else>
                         propValue.value
                     </g:else>
                    <g:if test="${propValues.get(targetSubscription)?.size() > 1}"><br></g:if>
                    <g:if test="${propValue?.note}">
                        <div class="ui circular label la-long-tooltip" data-tooltip="${propValue?.note}">Anm.</div><br>
                    </g:if>
                </g:each>
            </g:elseif>
            <g:else>
                <a class="ui red circular label la-popup-tooltip la-delay" data-content="<g:message code="default.compare.propertyNotSet"/>"><strong>X</strong></a>
            </g:else>
        </td>
    </tr>
</g:each>