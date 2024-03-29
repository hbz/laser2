<%@ page import="de.laser.OrgSetting; de.laser.RefdataValue; de.laser.properties.PropertyDefinition; de.laser.Org; de.laser.auth.Role; de.laser.helper.RDStore; de.laser.helper.RDConstants" %>
<%@ page import="grails.plugin.springsecurity.SpringSecurityUtils" %>
<laser:serviceInjection />

<!doctype html>
<html>
    <head>
        <meta name="layout" content="laser">
        <title>${message(code:'laser')} : ${message(code:'org.nav.options')}</title>
    </head>
    <body>

        <semui:breadcrumbs>
            <g:if test="${!inContextOrg}">
                <semui:crumb text="${orgInstance.getDesignation()}" class="active"/>
            </g:if>
        </semui:breadcrumbs>

        <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />${orgInstance.name}</h1>

        <semui:objectStatus object="${orgInstance}" status="${orgInstance.status}" />

        <g:render template="nav" model="${[orgInstance: orgInstance, inContextOrg: inContextOrg]}"/>

        <semui:messages data="${flash}" />

        <semui:tabs actionName="settings">
            <semui:tabsItem controller="org" action="settings" params="[id: orgInstance.id, tab: 'general']" tab="general" text="${message(code: 'org.setting.tab.general')}"/>
            <g:if test="${accessService.checkPermX('FAKE,ORG_INST,ORG_CONSORTIUM', 'ROLE_ADMIN,ROLE_ORG_EDITOR')}">
                <semui:tabsItem controller="org" action="settings" params="[id: orgInstance.id, tab: 'api']" tab="api" text="${message(code: 'org.setting.tab.api')}"/>
            </g:if>
            <semui:tabsItem controller="org" action="settings" params="[id: orgInstance.id, tab: 'ezb']" tab="ezb" text="${message(code: 'org.setting.tab.ezb')}"/>
            <semui:tabsItem controller="org" action="settings" params="[id: orgInstance.id, tab: 'natstat']" tab="natstat" text="${message(code: 'org.setting.tab.natstat')}"/>
            <g:if test="${accessService.checkPermX('ORG_INST,ORG_CONSORTIUM', 'ROLE_ADMIN,ROLE_ORG_EDITOR')}">
                <semui:tabsItem controller="org" action="settings" params="[id: orgInstance.id, tab: 'oamonitor']" tab="oamonitor" text="${message(code: 'org.setting.tab.oamonitor')}"/>
            </g:if>
        </semui:tabs>

        <div class="ui bottom attached tab active segment">

                <div class="la-inline-lists">

                    <div class="ui card la-dl-no-table la-js-hideable">
                        <div class="content">

                            <table class="ui la-js-responsive-table la-table table">
                                <thead>
                                <tr>
                                    <th>Merkmal</th>
                                    <th>Wert</th>
                                </tr>
                                </thead>
                                <tbody>
                                <%-- Extra Call from editable cause valiation needed only in Case of Selection "Ja" --%>
                                <laser:script file="${this.getGroovyPageFileName()}">

                                    $('body #natstat_server_access').editable('destroy').editable({
                                        tpl: '<select class="ui dropdown"></select>'
                                    }).on('shown', function() {
                                        r2d2.initDynamicSemuiStuff('body');
                                        $(".table").trigger('reflow');
                                        $('.ui.dropdown')
                                                .dropdown({
                                            clearable: true
                                        })
                                        ;
                                    }).on('hidden', function() {
                                        $(".table").trigger('reflow')
                                    });

                                    $('body #oamonitor_server_access').editable('destroy').editable({
                                        tpl: '<select class="ui dropdown"></select>'
                                    }).on('shown', function() {
                                        r2d2.initDynamicSemuiStuff('body');
                                        $(".table").trigger('reflow');
                                        $('.ui.dropdown')
                                                .dropdown({
                                            clearable: true
                                        })
                                        ;
                                    }).on('hidden', function() {
                                        $(".table").trigger('reflow')
                                    });

                                    $('body #ezb_server_access').editable('destroy').editable({
                                        tpl: '<select class="ui dropdown"></select>'
                                    }).on('shown', function() {
                                        r2d2.initDynamicSemuiStuff('body');
                                        $(".table").trigger('reflow');
                                        $('.ui.dropdown')
                                                .dropdown({
                                            clearable: true
                                        })
                                        ;
                                    }).on('hidden', function() {
                                        $(".table").trigger('reflow')
                                    });
                                </laser:script>

                                <g:each in="${settings}" var="os">
                                    <tr>
                                        <td>
                                            ${message(code:"org.setting.${os.key}", default: "${os.key}")}
                                            <g:if test="${OrgSetting.KEYS.NATSTAT_SERVER_ACCESS == os.key}">
                                                <span class="la-popup-tooltip la-delay" data-content="${message(code:'org.setting.NATSTAT_SERVER_ACCESS.tooltip')}">
                                                    <i class="question circle icon"></i>
                                                </span>
                                            </g:if>
                                            <g:elseif test="${OrgSetting.KEYS.OAMONITOR_SERVER_ACCESS == os.key}">
                                                <span class="la-popup-tooltip la-delay" data-content="${message(code:'org.setting.OAMONITOR_SERVER_ACCESS.tooltip')}">
                                                    <i class="question circle icon"></i>
                                                </span>
                                            </g:elseif>
                                            <g:elseif test="${OrgSetting.KEYS.EZB_SERVER_ACCESS == os.key}">
                                                <span class="la-popup-tooltip la-delay" data-content="${message(code:'org.setting.EZB.tooltip')}">
                                                    <i class="question circle icon"></i>
                                                </span>
                                            </g:elseif>
                                        </td>
                                        <td>

                                            <g:if test="${editable && os.key in OrgSetting.getEditableSettings()}">
                                                <g:if test="${OrgSetting.KEYS.NATSTAT_SERVER_ACCESS == os.key}">
                                                    <semui:xEditableRefData owner="${os}"
                                                                            field="rdValue"
                                                                            id="natstat_server_access"
                                                                            data_confirm_tokenMsg="${message(code: 'org.setting.NATSTAT_SERVER_ACCESS.confirm')}"
                                                                            data_confirm_term_how="ok"
                                                                            cssClass="js-open-confirm-modal-xeditable"
                                                                            data_confirm_value="${RefdataValue.class.name}:${RDStore.YN_YES.id}"
                                                                            config="${os.key.rdc}" />
                                                </g:if>
                                                <g:elseif test="${OrgSetting.KEYS.OAMONITOR_SERVER_ACCESS == os.key}">
                                                    <semui:xEditableRefData owner="${os}"
                                                                            field="rdValue"
                                                                            id="oamonitor_server_access"
                                                                            data_confirm_tokenMsg="${message(code: 'org.setting.OAMONITOR_SERVER_ACCESS.confirm')}"
                                                                            data_confirm_term_how="ok"
                                                                            cssClass="js-open-confirm-modal-xeditable"
                                                                            data_confirm_value="${RefdataValue.class.name}:${RDStore.YN_YES.id}"
                                                                            config="${os.key.rdc}" />
                                                </g:elseif>
                                                <g:elseif test="${OrgSetting.KEYS.EZB_SERVER_ACCESS == os.key}">
                                                    <semui:xEditableRefData owner="${os}"
                                                                            field="rdValue"
                                                                            id="ezb_server_access"
                                                                            data_confirm_tokenMsg="${message(code: 'org.setting.EZB.confirm')}"
                                                                            data_confirm_term_how="ok"
                                                                            cssClass="js-open-confirm-modal-xeditable"
                                                                            data_confirm_value="${RefdataValue.class.name}:${RDStore.YN_YES.id}"
                                                                            config="${os.key.rdc}" />
                                                </g:elseif>
                                                <g:elseif test="${os.key.type == RefdataValue}">
                                                    <semui:xEditableRefData owner="${os}" field="rdValue" config="${os.key.rdc}" />
                                                </g:elseif>
                                                <g:elseif test="${os.key.type == Role}">
                                                    ${os.getValue()?.getI10n('authority')} (Editierfunktion deaktiviert) <%-- TODO --%>
                                                </g:elseif>
                                                <g:else>
                                                    <semui:xEditable owner="${os}" field="strValue" />
                                                </g:else>

                                            </g:if>
                                            <g:else>

                                                <g:if test="${OrgSetting.KEYS.GASCO_ENTRY == os.key}">
                                                    <g:if test="${SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN,ROLE_ORG_EDITOR')}">
                                                        <semui:xEditableRefData owner="${os}" field="rdValue" config="${os.key.rdc}" />
                                                    </g:if>
                                                    <g:else>
                                                        ${os.getValue()?.getI10n('value')}
                                                    </g:else>
                                                </g:if>
                                                <g:elseif test="${os.key.type == RefdataValue}">
                                                    ${os.getValue()?.getI10n('value')}
                                                </g:elseif>
                                                <g:elseif test="${os.key.type == Role}">
                                                    ${os.getValue()?.getI10n('authority')}
                                                </g:elseif>
                                                <g:else>
                                                    ${os.getValue()}
                                                </g:else>

                                            </g:else>

                                        </td>
                                    </tr>
                                </g:each>
                                </tbody>
                        </table>
                        </div><!-- .content -->
                    </div>

                    <g:if test="${params.tab == 'general'}">
                        <div class="ui card la-dl-no-table la-js-hideable">
                            <div class="content">
                                <h2 class="ui header">
                                    ${message(code:'org.confProperties')}
                                </h2>

                                <div id="custom_props_div_1">
                                    <g:render template="/templates/properties/custom" model="${[
                                            prop_desc: PropertyDefinition.ORG_CONF,
                                            ownobj: orgInstance,
                                            orphanedProperties: orgInstance.propertySet,
                                            custom_props_div: "custom_props_div_1" ]}"/>
                                </div>
                            </div><!-- .content -->
                        </div><!-- .card -->

                        <laser:script file="${this.getGroovyPageFileName()}">
                            c3po.initProperties("<g:createLink controller='ajaxJson' action='lookup'/>", "#custom_props_div_1");
                        </laser:script>
                    </g:if>


                </div><!-- .la-inline-lists -->

        </div><!-- .grid -->

    </body>
</html>
