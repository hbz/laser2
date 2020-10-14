<%@ page import="com.k_int.kbplus.auth.User; de.laser.DeletionService" %>
<g:set var="deletionService" bean="deletionService" />
<laser:serviceInjection />
<!doctype html>
<html>
    <head>
        <meta name="layout" content="semanticUI"/>
        <title>${message(code:'laser')} : ${message(code:'user.label')}</title>
</head>

<body>
    <g:render template="breadcrumb" model="${[ user:user, params:params ]}"/>
    <br>
    <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />
        ${user?.username} : ${user?.displayName?:'Nutzer unbekannt'}
    </h1>

    <g:if test="${delResult}">
        <g:if test="${delResult.status == DeletionService.RESULT_SUCCESS}">
            <semui:msg class="positive" header="" message="deletion.success.msg" />
            <g:link controller="organisation" action="users" params="${[id: contextService.getOrg().id]}" class="ui button">${message(code:'org.nav.users')}</g:link>
        </g:if>
        <g:else>
            <g:if test="${delResult.status == DeletionService.RESULT_SUBSTITUTE_NEEDED}">
                <semui:msg class="info" header="" message="user.delete.info2" />
            </g:if>
            <g:else>
                <semui:msg class="info" header="" message="user.delete.info" />
            </g:else>

            <g:if test="${delResult.status == DeletionService.RESULT_CUSTOM}">
                <semui:msg class="negative" header="${message(code: 'deletion.blocked.header')}" message="deletion.custom.msg.user" />
            </g:if>
            <g:if test="${delResult.status == DeletionService.RESULT_BLOCKED}">
                <semui:msg class="negative" header="${message(code: 'deletion.blocked.header')}" message="deletion.blocked.msg.user" />
            </g:if>
            <g:if test="${delResult.status == DeletionService.RESULT_ERROR}">
                <semui:msg class="negative" header="${message(code: 'deletion.error.header')}" message="deletion.error.msg" />
            </g:if>

            <g:form controller="user" action="_delete" params="${[id: user.id, process: true]}">

                <g:link controller="organisation" action="users" params="${[id: contextService.getOrg().id]}" class="ui button">${message(code:'org.nav.users')}</g:link>

                <g:link controller="user" action="edit" params="${[id: user.id]}" class="ui button"><g:message code="default.button.cancel.label"/></g:link>

                <g:if test="${editable}">

                    <g:if test="${delResult.deletable}">
                        <g:if test="${delResult.status == DeletionService.RESULT_SUBSTITUTE_NEEDED}">
                            <input type="submit" class="ui button red" value="${message(code:'deletion.user')}"
                                   onclick="return confirm('${message(code:'user.delete.confirm')}')" />

                            <br/><br/>${message(code:'user.delete.moveToNewUser')}<br/>

                            <g:select id="userReplacement" name="userReplacement" class="ui dropdown selection"
                                      from="${substituteList.sort()}"
                                      optionKey="${{ User.class.name + ':' + it.id }}"
                                      optionValue="${{it.displayName + ' (' + it.username + ')'}}" />
                        </g:if>
                        <g:elseif test="${delResult.status != DeletionService.RESULT_ERROR}">
                            <input type="submit" class="ui button red" value="${message(code:'deletion.user')}"
                                   onclick="return confirm('${message(code:'user.delete.confirm')}')" />
                        </g:elseif>
                    </g:if>
                    <g:else>
                        <input disabled type="submit" class="ui button red" value="${message(code:'deletion.user')}" />
                    </g:else>

                </g:if>
            </g:form>

        </g:else>

        <%-- --%>

        <table class="ui celled la-table compact table">
            <thead>
            <tr>
                <th>Objekte, Referenzen</th>
                <th>Anzahl</th>
                <th>Objekt-Ids</th>
            </tr>
            </thead>
            <tbody>
            <g:each in="${delResult.info.sort{ a,b -> a[0] <=> b[0] }}" var="info">
                <tr>
                    <td>
                        ${info[0]}
                    </td>
                    <td style="text-align:center">
                        <g:if test="${info.size() > 2 && info[1].size() > 0}">
                            <span class="ui circular label la-popup-tooltip la-delay ${info[2]}"
                                <g:if test="${info[2] == DeletionService.FLAG_WARNING}">
                                    data-content="${message(code:'user.delete.warning')}"
                                </g:if>
                                <g:if test="${info[2] == DeletionService.FLAG_SUBSTITUTE}">
                                    data-content="${message(code:'user.delete.substitute')}"
                                </g:if>
                                <g:if test="${info[2] == DeletionService.FLAG_BLOCKER}">
                                    data-content="${message(code:'user.delete.blocker')}"
                                </g:if>
                            >${info[1].size()}</span>
                        </g:if>
                        <g:else>
                            ${info[1].size()}
                        </g:else>
                    </td>
                    <td>
                        <div style="overflow-y:scroll;scrollbar-color:grey white;max-height:14.25em">
                            ${info[1].collect{ item -> (item.hasProperty('id') && item.id) ? item.id : item}.sort().join(', ')}
                        </div>
                    </td>
                </tr>
            </g:each>
            </tbody>
        </table>

        <%-- --%>

        <br />

        <div class="ui list">
            <div class="item">
                <span class="ui circular label yellow">1</span>
                <span class="content">
                    ${message(code:'user.delete.warning')}
                </span>
            </div>
            <div class="item">
                <span class="ui circular label teal">2</span>
                <span class="content">
                    ${message(code:'user.delete.substitute')}
                </span>
            </div>
            <div class="item">
                <span class="ui circular label red">3</span>
                <span class="content">
                    ${message(code:'user.delete.blocker')}
                </span>
            </div>
        </div>

    </g:if>

</body>
</html>
