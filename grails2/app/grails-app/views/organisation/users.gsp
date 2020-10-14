<%@ page import="de.laser.UserSetting; de.laser.Org; com.k_int.kbplus.auth.UserOrg; com.k_int.kbplus.auth.Role" %>
<laser:serviceInjection />

<!doctype html>
<html>
    <head>
        <meta name="layout" content="semanticUI">
        <g:set var="entityName" value="${message(code: 'org.label')}" />
        <title>${message(code:'laser')} : <g:message code="default.show.label" args="[entityName]" /></title>
    </head>
    <body>

    <g:render template="breadcrumb" model="${[ orgInstance:orgInstance, params:params ]}"/>

    <g:if test="${editable}">
        <semui:controlButtons>
            <g:render template="actions" />
        </semui:controlButtons>
    </g:if>

    <h1 class="ui left floated aligned icon header la-clear-before">
        <semui:headerIcon />
        ${orgInstance.name} - ${message(code:'org.nav.users')}
    </h1>

    <g:render template="nav" model="${[orgInstance: orgInstance, inContextOrg: orgInstance.id == contextService.getOrg().id]}"/>

    <g:render template="/templates/filter/javascript" />
    <semui:filter showFilterButton="true">
        <g:form controller="organisation" action="users" params="${[id: orgInstance.id]}" method="get" class="ui form">

            <div class="three fields">
                <div class="field">
                    <label for="name">${message(code:'default.search.text')}</label>
                    <input type="text" placeholder="${message(code:'default.search.ph')}" id="name" name="name" value="${params.name}"/>
                </div>

                <div class="field">
                    <label for="authority">${message(code:'user.role')}</label>
                    <g:select from="${Role.findAllByRoleType('user')}"
                              noSelection="${['' : message(code:'accessMethod.all')]}"
                              class="ui dropdown"
                              value="${params.authority}" optionKey="id" optionValue="${{message(code:'cv.roles.' + it.authority)}}"
                              id="authority" name="authority" />
                </div>

                <div class="field la-field-right-aligned">
                    <g:link controller="organisation" action="users" params="${[id: orgInstance.id]}" class="ui reset primary button">${message(code:'default.button.reset.label')}</g:link>
                    <input type="submit" value="${message(code:'default.button.search.label')}" class="ui secondary button"/>
                </div>
            </div>
        </g:form>
    </semui:filter>

    <semui:messages data="${flash}" />

    <g:if test="${pendingRequests && editable}">

        <h3 class="ui header">Offene Anfragen</h3>

        <table class="ui celled la-table table">
            <thead>
            <tr>
                <th>${message(code:'user.username.label')}</th>
                <th>${message(code:'user.displayName.label')}</th>
                <th>${message(code:'user.email')}</th>
                <th>${message(code:'profile.membership.role')}</th>
                <th class="la-action-info">${message(code:'default.actions.label')}</th>
            </tr>
            </thead>

            <g:each in="${pendingRequests}" var="uo">
                <tr>
                    <td class="la-main-object">
                        ${uo.user.username}
                    </td>
                    <td>
                        ${uo.user.displayName}
                    </td>
                    <td>
                        ${uo.user.email}
                    </td>
                    <td>
                        <g:message code="cv.roles.${uo.formalRole?.authority}"/>
                    </td>
                    <td class="x">
                        <g:link controller="organisation" action="processAffiliation"
                                params="${[assoc:uo.id, id:params.id, cmd:'approve']}" class="ui icon positive button la-popup-tooltip la-delay"
                                data-content="${message(code:'profile.membership.accept.button')}" data-position="top left" >
                            <i class="checkmark icon"></i>
                        </g:link>

                        <g:link controller="organisation" action="processAffiliation"
                                params="${[assoc:uo.id, id:params.id, cmd:'reject']}" class="ui icon negative button la-popup-tooltip la-delay"
                                data-content="${message(code:'profile.membership.cancel.button')}" data-position="top left" >
                            <i class="times icon"></i>
                        </g:link>
                    </td>
                </tr>
            </g:each>
        </table>

        <h3 class="ui header">${message(code: 'profile.membership.existing')}</h3>

    </g:if>

    <sec:ifNotGranted roles="ROLE_ADMIN">
        <div class="ui info message">${message(code:'user.edit.info')}</div>
    </sec:ifNotGranted>

    <table class="ui celled la-table table">
        <thead>
        <tr>
            <th>${message(code:'user.username.label')}</th>
            <th>${message(code:'user.displayName.label')}</th>
            <th>${message(code:'user.email')}</th>
            <th>${message(code:'profile.membership.role')}</th>
            <%--<th>${message(code:'user.sys_role')}</th>--%>
            <g:if test="${editable}">
                <th class="la-action-info">${message(code:'default.actions.label')}</th>
            </g:if>
        </tr>
        </thead>

        <g:each in="${users}" var="uo">
            <tr>
                <td class="la-main-object">
                    ${uo.user.username}

                    <g:if test="${! uo.user.enabled}">
                        <span data-position="top left"  class="la-popup-tooltip la-delay" data-content="${message(code:'user.disabled.text')}">
                            <i class="icon minus circle red"></i>
                        </span>
                    </g:if>
                </td>
                <td>
                    ${uo.user.displayName}
                </td>
                <td>
                    ${uo.user.email}
                </td>
                <td>
                    <g:message code="cv.roles.${uo.formalRole?.authority}"/>
                </td>

                <g:if test="${editable}">
                    <td class="x">
                        <g:if test="${contextService.getUser().hasRole('ROLE_ADMIN') || instAdmService.isUserEditableForInstAdm(uo.user, contextService.getUser())}">
                            <g:link controller="user" action="edit" id="${uo.user.id}" class="ui icon button">
                                <i class="icon write"></i>
                            </g:link>
                        </g:if>

                        <g:link class="ui icon negative button js-open-confirm-modal la-popup-tooltip la-delay"
                                data-confirm-tokenMsg="${message(code: "confirm.dialog.delete.user.organisation", args: [uo.user.displayName,uo.user.getSettingsValue(UserSetting.KEYS.DASHBOARD)?.name ])}"
                                data-confirm-term-how="delete"
                                controller="organisation"
                                action="processAffiliation"
                                params="${[assoc:uo.id, id:params.id, cmd:'delete']}"
                                data-content="${message(code:'profile.membership.delete.button')}" data-position="top left" >
                            <i class="trash alternate icon"></i>
                        </g:link>
                    </td>
                </g:if>
            </tr>
        </g:each>
    </table>

</body>
</html>
