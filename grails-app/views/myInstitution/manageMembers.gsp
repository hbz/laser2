<%@ page import="de.laser.helper.RDStore" %>
<laser:serviceInjection />

<%
    String title, memberPlural
    if(comboType.id == RDStore.COMBO_TYPE_CONSORTIUM.id) {
        title = message(code: 'menu.institutions.manage_consortia')
        memberPlural = message(code: 'consortium.member.plural')
    }
    else if(comboType.id == RDStore.COMBO_TYPE_DEPARTMENT.id) {
        title = message(code: 'menu.institutions.manage_departments')
        memberPlural = message(code: 'collective.member.plural')
    }
%>
<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI">
    <g:set var="entityName" value="${message(code: 'org.label')}"/>
    <title>${message(code: 'laser')} : ${title}</title>
</head>

<body>

<semui:breadcrumbs>
    <semui:crumb text="${title}" class="active"/>
</semui:breadcrumbs>

<semui:controlButtons>
    <semui:exportDropdown>
        <g:if test="${filterSet}">
            <semui:exportDropdownItem>
                <g:link class="item js-open-confirm-modal"
                        data-confirm-tokenMsg = "${message(code: 'confirmation.content.exportPartial')}"
                        data-confirm-term-how="ok" controller="myInstitution" action="manageMembers"
                        params="${params+[exportXLS:true]}">
                    ${message(code:'default.button.exports.xls')}
                </g:link>
            </semui:exportDropdownItem>
            <semui:exportDropdownItem>
                <g:link class="item js-open-confirm-modal"
                        data-confirm-tokenMsg = "${message(code: 'confirmation.content.exportPartial')}"
                        data-confirm-term-how="ok" controller="myInstitution" action="manageMembers"
                        params="${params+[format:'csv']}">
                    ${message(code:'default.button.exports.csv')}
                </g:link>
            </semui:exportDropdownItem>
        </g:if>
        <g:else>
            <semui:exportDropdownItem>
                <g:link class="item" action="manageMembers" params="${params+[exportXLS:true]}">${message(code:'default.button.exports.xls')}</g:link>
            </semui:exportDropdownItem>
            <semui:exportDropdownItem>
                <g:link class="item" action="manageMembers" params="${params+[format:'csv']}">${message(code:'default.button.exports.csv')}</g:link>
            </semui:exportDropdownItem>
        </g:else>
    </semui:exportDropdown>
    <g:if test="${editable}">
        <g:render template="actions"/>
    </g:if>
</semui:controlButtons>

<h1 class="ui left floated aligned icon header la-clear-before"><semui:headerIcon />${title}
<semui:totalNumber total="${membersCount}"/>
</h1>
<g:render template="/templates/filter/javascript" />
<semui:messages data="${flash}"/>
    <%
        List configShowFilter = []
        List configShowTable = []
        if(comboType.id == RDStore.COMBO_TYPE_CONSORTIUM.id) {
            configShowFilter = [['name', 'identifier', 'libraryType', 'subjectGroup'], ['region', 'libraryNetwork','property']]
            configShowTable = ['sortname', 'name', 'mainContact', 'libraryType', 'legalInformation', 'numberOfSubscriptions', 'numberOfSurveys']
        }
        else if(comboType.id == RDStore.COMBO_TYPE_DEPARTMENT.id) {
            configShowFilter = [['name', 'identifier'], ['property']]
            configShowTable = ['name', 'mainContact', 'legalInformation', 'numberOfSubscriptions']
        }
    %>

    <semui:filter showFilterButton="true">
        <g:form action="manageMembers" method="get" class="ui form">
            <g:render template="/templates/filter/orgFilter"
                      model="[
                              tmplConfigShow: configShowFilter,
                              tmplConfigFormFilter: true,
                              useNewLayouter: true
                      ]"/>
        </g:form>
    </semui:filter>
<div class="la-clear-before">
    <g:if test="${members}">
        <g:form action="manageMembers" controller="myInstitution" method="post" class="ui form la-clear-before">
            <g:render template="/templates/filter/orgFilterTable"
                      model="[orgList: members,
                              tmplShowCheckbox: editable,
                              comboType: comboType,
                              tmplConfigShow: configShowTable
                      ]"/>


        <g:if test="${members && editable}">
            <input type="submit" class="ui button"
                   data-confirm-tokenMsg="${message(code: "confirm.dialog.delete.function", args: [message(code:'members.confirmDelete')])}"
                   data-confirm-term-how="delete" value="${message(code: 'default.button.revoke.label')}"/>
        </g:if>
    </g:form>
</g:if>
<g:else>
    <g:if test="${filterSet}">
        <br><strong><g:message code="filter.result.empty.object" args="${[memberPlural]}"/></strong>
    </g:if>
    <g:else>
        <br><strong><g:message code="result.empty.object" args="${[memberPlural]}"/></strong>
    </g:else>
</g:else>

    <g:render template="/templates/copyEmailaddresses" model="[orgList: totalMembers]"/>
    <semui:paginate action="manageMembers" controller="myInstitution" params="${params}" next="${message(code:'default.paginate.next')}" prev="${message(code:'default.paginate.prev')}" max="${max}" total="${membersCount}" />

    <semui:debugInfo>
        <g:render template="/templates/debug/benchMark" model="[debug: benchMark]" />
    </semui:debugInfo>
</body>
</html>
