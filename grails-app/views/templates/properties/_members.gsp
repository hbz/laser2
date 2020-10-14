%{-- To use, add the g:render custom_props inside a div with id=custom_props_div_xxx, add g:javascript src=properties.js --}%
%{-- on head of container page, and on window load execute  --}%
%{-- c3po.initProperties("<g:createLink controller='ajaxJson' action='lookup'/>", "#custom_props_div_xxx"); --}%

<%@ page import="de.laser.Subscription; de.laser.properties.SubscriptionProperty; de.laser.RefdataValue; de.laser.properties.PropertyDefinition" %>
<laser:serviceInjection />

<%-- OVERWRITE editable for INST_EDITOR: ${editable} -&gt; ${accessService.checkMinUserOrgRole(user, contextService.getOrg(), 'INST_EDITOR')}
<g:set var="overwriteEditable" value="${editable || accessService.checkPermAffiliationX('ORG_INST','INST_EDITOR','ROLE_ADMIN')}" />--%>

<g:if test="${newProp}">
    <semui:errors bean="${newProp}" />
</g:if>
<g:if test="${subscription}">
    <g:set var="memberSubs" value="${Subscription.executeQuery('select s from Subscription s where s.instanceOf = :sub', [sub: subscription])}"/>
</g:if>
<table class="ui compact la-table-inCard table">
    <tbody>
        <g:each in="${memberProperties}" var="propType">
            <tr>
                <td>
                    <g:if test="${editable == true && subscription}">
                        <g:link action="propertiesMembers" params="${[id:subscription.id,filterPropDef:genericOIDService.getOID(propType)]}" >
                            <g:if test="${propType.getI10n('expl') != null && !propType.getI10n('expl').contains(' °')}">
                                ${propType.getI10n('name')}
                                <g:if test="${propType.getI10n('expl')}">
                                    <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center" data-content="${propType.getI10n('expl')}">
                                        <i class="question circle icon"></i>
                                    </span>
                                </g:if>
                            </g:if>
                            <g:else>
                                ${propType.getI10n('name')}
                            </g:else>
                            <g:if test="${propType.mandatory}">
                                <span data-position="top right"  class="la-popup-tooltip la-delay" data-content="${message(code:'default.mandatory.tooltip')}">
                                    <i class="star icon yellow"></i>
                                </span>
                            </g:if>
                            <g:if test="${propType.multipleOccurrence}">
                                <span data-position="top right"  class="la-popup-tooltip la-delay" data-content="${message(code:'default.multipleOccurrence.tooltip')}">
                                    <i class="redo icon orange"></i>
                                </span>
                            </g:if>
                        </g:link>
                    </g:if>
                    <g:else>
                        <g:if test="${propType.getI10n('expl') != null && !propType.getI10n('expl').contains(' °')}">
                            ${propType.getI10n('name')}
                            <g:if test="${propType.getI10n('expl')}">
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center" data-content="${propType.getI10n('expl')}">
                                    <i class="question circle icon"></i>
                                </span>
                            </g:if>
                        </g:if>
                        <g:else>
                            ${propType.getI10n('name')}
                        </g:else>
                        <g:if test="${propType.mandatory}">
                            <span data-position="top right"  class="la-popup-tooltip la-delay" data-content="${message(code:'default.mandatory.tooltip')}">
                                <i class="star icon yellow"></i>
                            </span>
                        </g:if>
                        <g:if test="${propType.multipleOccurrence}">
                            <span data-position="top right"  class="la-popup-tooltip la-delay" data-content="${message(code:'default.multipleOccurrence.tooltip')}">
                                <i class="redo icon orange"></i>
                            </span>
                        </g:if>
                    </g:else>
                </td>
                <td class="x">
                    <span class="la-popup-tooltip la-delay" data-content="${message(code:'property.notInherited.fromConsortia2')}" data-position="top right"><i class="large icon cart arrow down blue"></i></span>
                    <g:if test="${memberSubs}">
                        (<span data-tooltip="${message(code:'property.notInherited.info.propertyCount')}"><i class="ui icon sticky note blue"></i></span> ${SubscriptionProperty.executeQuery('select sp from SubscriptionProperty sp where sp.owner in (:subscriptionSet) and sp.tenant = :context and sp.instanceOf = null and sp.type = :type', [subscriptionSet: memberSubs, context: contextOrg, type: propType]).size() ?: 0} / <span data-tooltip="${message(code:'property.notInherited.info.membersCount')}"><i class="ui icon clipboard blue"></i></span> ${memberSubs.size() ?: 0})
                    </g:if>
                </td>
            </tr>
        </g:each>
    </tbody>

    <%--<g:if test="${editable}">
        <tfoot>
            <tr>
                <g:if test="${ownobj.privateProperties}">
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

                            <input type="submit" value="${message(code:'default.button.add.label')}" class="ui button js-wait-wheel"/>
                        </g:if>
                    </laser:remoteForm>

                    </td>
            </tr>
        </tfoot>
    </g:if>--%>
</table>