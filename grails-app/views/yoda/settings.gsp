<%@ page import="de.laser.system.SystemSetting" %>
<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code: 'laser')} : ${message(code: 'menu.yoda.systemSettings')}</title>
</head>

<body>

<semui:breadcrumbs>
    <semui:crumb message="menu.yoda.dash" controller="yoda" action="index"/>
    <semui:crumb message="menu.yoda.systemSettings" class="active"/>
</semui:breadcrumbs>

<div>
    <h2 class="ui header">${message(code: 'menu.yoda.systemSettings')}</h2>

    <g:set var="mailConfigDisabled" value="${grailsApplication.config.grails.mail.disabled}" />
    <g:set var="maintenanceModeEnabled" value="${SystemSetting.findByName('MaintenanceMode')?.value == 'true'}" />

    <table class="ui celled la-table table">
        <thead>
        <tr>
            <th>${message(code: 'default.setting.label')}</th>
            <th>${message(code: 'default.value.label')}</th>
            <th></th>
        </tr>
        </thead>
        <tbody>
        <tr>
            <td>${message(code: 'system.config.mail.label')}</td>
            <td>
                <g:if test="${mailConfigDisabled}">
                    <div class="ui red horizontal label"> ${message(code: 'default.deactivated.label')} </div>
                </g:if>
                <g:else>
                    <div class="ui green horizontal label"> ${message(code: 'default.activated.label')} </div>
                </g:else>
            </td>
            <td>
                <g:if test="${mailConfigDisabled}">
                    <g:link controller="yoda" action="toggleMailSent" class="ui button positive right floated" params="${[mailSent: true]}">
                        ${message(code: 'system.config.mail.activate')}
                    </g:link>
                </g:if>
                <g:else>
                    <g:link controller="yoda" action="toggleMailSent" class="ui button negative right floated" params="${[mailSent: false]}">
                        ${message(code: 'system.config.mail.deactivate')}
                    </g:link>
                </g:else>
            </td>
        </tr>
        <tr>
            <td>${message(code: 'system.setting.maintenanceMode.label')}</td>
            <td>
                <g:if test="${! maintenanceModeEnabled}">
                    <div class="ui red horizontal label"> ${message(code: 'default.deactivated.label')} </div>

                </g:if>
                <g:else>
                    <div class="ui green horizontal label"> ${message(code: 'default.activated.label')} </div>
                </g:else>
            </td>
            <td>
                <g:if test="${! maintenanceModeEnabled}">
                    <g:link controller="yoda" action="toggleBoolSetting" class="ui button positive right floated" params="${[setting: 'MaintenanceMode']}">
                        ${message(code: 'system.setting.maintenanceMode.activate')}
                    </g:link>
                </g:if>
                <g:else>
                    <g:link controller="yoda" action="toggleBoolSetting" class="ui button negative right floated" params="${[setting: 'MaintenanceMode']}">
                        ${message(code: 'system.setting.maintenanceMode.deactivate')}
                    </g:link>
                </g:else>
            </td>
        </tr>
        <g:each in="${settings}" var="s">
            <tr>
                <td>${s.name}</td>
                <td>
                    <g:if test="${s.tp == 1}">
                        <g:link controller="yoda" action="toggleBoolSetting" params="${[setting: s.name]}">${s.value}</g:link>
                    </g:if>
                </td>
            </tr>
        </g:each>
        </tbody>
    </table>
</div>
</body>
</html>
