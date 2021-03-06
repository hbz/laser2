<%@page import="de.laser.helper.RDStore; de.laser.helper.RDConstants; de.laser.RefdataCategory; de.laser.PendingChangeConfiguration; de.laser.Platform" %>
<laser:serviceInjection />

<div class="ui card">
    <div class="content">
        <h2 class="ui header">${message(code: 'subscription.packages.label')}</h2>
        <table class="ui three column table">
            <g:each in="${subscription.packages}" var="sp">
                <% String buttonColor = ""
                    if(sp.pendingChangeConfig.size() > 0) {
                        buttonColor = "green"
                }%>
                <tr>
                    <td colspan="2">
                        <g:link controller="package" action="show" id="${sp.pkg.id}">${sp.pkg.name}</g:link>

                        <g:if test="${sp.pkg.contentProvider}">
                            (${sp.pkg.contentProvider.name})
                        </g:if>
                    </td>
                </tr>
                <tr>
                    <td style="border-top: none">
                        <div class="ui top aligned divided relaxed list">
                            <div class="item">
                                <div class="content">
                                    <strong>${message(code: 'subscription.details.linkAccessPoint.platform.label')}</strong>
                                </div>
                            </div>
                        <%--<g:if test="${sp.pkg.tipps}">--%>
                            <g:each in="${Platform.executeQuery('select distinct tipp.platform from TitleInstancePackagePlatform tipp where tipp.pkg = :pkg',[pkg:sp.pkg])}" var="platform">
                                <div class="item">

                                    <div class="content">
                                        <g:if test="${platform}">
                                            <g:link controller="platform" action="show" id="${platform.id}">${platform.name}</g:link>
                                            <semui:linkIcon href="${platform.primaryUrl?.startsWith('http') ? platform.primaryUrl : 'http://' + platform.primaryUrl}"/>
                                        </g:if>
                                    </div>
                                </div>
                            </g:each>
                        <%--</g:if>--%>
                        </div>
                    </td>
                    <td style="border-top: none" class="right aligned">
                        <button id="pendingChangeConfigurationToggle${sp.id}"
                                class="ui icon button ${buttonColor} la-js-dont-hide-button la-popup-tooltip la-delay"
                                data-content="${message(code:'subscription.packages.config.header')}">
                            <i class="ui angle double down icon"></i>
                        </button>

                        <laser:script file="${this.getGroovyPageFileName()}">
                            $("#pendingChangeConfigurationToggle${sp.id}").on('click', function() {
                                $("#pendingChangeConfiguration${sp.id}").transition('slide down');
                                if ($("#pendingChangeConfiguration${sp.id}").hasClass('visible')) {
                                    $(this).html('<i class="ui angle double down icon"></i>');
                                } else {
                                    $(this).html('<i class="ui angle double up icon"></i>');
                                }
                            })
                        </laser:script>
                        <g:if test="${editmode}">
                            <g:link controller="subscription"
                                    action="unlinkPackage"
                                    extaContentFlag="false"
                                    params="${[subscription: sp.subscription.id, package: sp.pkg.id, confirmed: 'Y']}"
                                    data-confirm-messageUrl="${createLink(controller:'subscription', action:'unlinkPackage', params:[subscription: sp.subscription.id, package: sp.pkg.id])}"
                                    data-confirm-tokenMsg="${message(code: "confirm.dialog.unlink.subscription.package", args: [sp.pkg.name])}"
                                    data-confirm-term-how="delete"
                                    class="ui icon negative button js-open-confirm-modal la-popup-tooltip la-delay"
                                    role="button"
                                    aria-label="${message(code: "ariaLabel.unlink.subscription.package", args: [sp.pkg.name])}">
                                <i aria-hidden="true" class="trash alternate icon"></i>
                            </g:link>
                        </g:if>
                    </td>
                </tr>
                <tr>
                    <td style="border-top: none" colspan="3">
                        <div id="pendingChangeConfiguration${sp.id}" class="hidden">
                            <h5 class="ui header">
                                <g:message code="subscription.packages.config.label" args="${[sp.pkg.name]}"/>
                            </h5>
                            <g:form controller="subscription" action="setupPendingChangeConfiguration" params="[id:sp.subscription.id,subscriptionPackage:sp.id]">
                                <dl>
                                    <dt class="control-label"><g:message code="subscription.packages.changeType.label"/></dt>
                                    <dt class="control-label">
                                        <g:message code="subscription.packages.setting.label"/>
                                    </dt>
                                    <dt class="control-label" data-tooltip="${message(code:"subscription.packages.notification.label")}">
                                        <i class="ui large icon bullhorn"></i>
                                    </dt>
                                    <g:if test="${contextCustomerType == 'ORG_CONSORTIUM'}">
                                        <dt class="control-label" data-tooltip="${message(code:'subscription.packages.auditable')}">
                                            <i class="ui large icon thumbtack"></i>
                                        </dt>
                                    </g:if>
                                </dl>
                                <g:set var="excludes" value="${[PendingChangeConfiguration.PACKAGE_PROP,PendingChangeConfiguration.PACKAGE_DELETED]}"/>
                                <g:each in="${PendingChangeConfiguration.SETTING_KEYS}" var="settingKey">
                                    <%
                                        PendingChangeConfiguration pcc = sp.getPendingChangeConfig(settingKey)
                                    %>
                                    <dl>
                                        <dt class="control-label">
                                            <g:message code="subscription.packages.${settingKey}"/>
                                        </dt>
                                        <dd>
                                            <g:if test="${!(settingKey in excludes)}">
                                                <g:if test="${editmode}">
                                                    <laser:select class="ui dropdown"
                                                                  name="${settingKey}!§!setting" from="${RefdataCategory.getAllRefdataValues(RDConstants.PENDING_CHANGE_CONFIG_SETTING)}"
                                                                  optionKey="id" optionValue="value"
                                                                  value="${(pcc && pcc.settingValue) ? pcc.settingValue.id : RDStore.PENDING_CHANGE_CONFIG_PROMPT.id}"
                                                    />
                                                </g:if>
                                                <g:else>
                                                    ${(pcc && pcc.settingValue) ? pcc.settingValue.getI10n("value") : RDStore.PENDING_CHANGE_CONFIG_PROMPT.getI10n("value")}
                                                </g:else>
                                            </g:if>
                                        </dd>
                                        <dd>
                                            <g:if test="${editmode}">
                                                <g:checkBox class="ui checkbox" name="${settingKey}!§!notification" checked="${pcc?.withNotification}"/>
                                            </g:if>
                                            <g:else>
                                                ${(pcc && pcc.withNotification) ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value")}
                                            </g:else>
                                        </dd>
                                        <g:if test="${contextCustomerType == 'ORG_CONSORTIUM'}">
                                            <dd>
                                                <g:if test="${!(settingKey in excludes)}">
                                                    <g:if test="${editmode}">
                                                        <g:checkBox class="ui checkbox" name="${settingKey}!§!auditable" checked="${pcc ? auditService.getAuditConfig(subscription, settingKey) : false}"/>
                                                    </g:if>
                                                    <g:else>
                                                        ${pcc ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value")}
                                                    </g:else>
                                                </g:if>
                                            </dd>
                                        </g:if>
                                    </dl>
                                </g:each>
                                <g:if test="${editmode}">
                                    <dl>
                                        <dt class="control-label"><g:submitButton class="ui button btn-primary" name="${message(code:'subscription.packages.submit.label')}"/></dt>
                                    </dl>
                                </g:if>
                            </g:form>
                        </div><!-- .content -->
                    </td>
                </tr>
            </g:each>
        </table>
    </div><!-- .content -->
</div>
