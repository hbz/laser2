
<%@ page import="com.k_int.kbplus.Creator" %>
<!doctype html>
<html>
	<head>
		<meta name="layout" content="semanticUI">
		<g:set var="entityName" value="${message(code: 'creator.label')}" />
		<title><g:message code="default.list.label" args="[entityName]" /></title>
	</head>
	<body>
		<h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon /><g:message code="default.list.label" args="[entityName]" />
			<semui:totalNumber total="${creatorInstanceTotal}"/>
		</h1>

        <semui:messages data="${flash}" />
				
				<table class="ui sortable celled la-table table">
					<thead>
						<tr>

							<g:sortableColumn property="lastname" title="${message(code: 'creator.lastname.label')}" />
						
							<g:sortableColumn property="firstname" title="${message(code: 'creator.firstname.label')}" />
						
							<g:sortableColumn property="middlename" title="${message(code: 'creator.middlename.label')}" />
						
							<th class="header"><g:message code="creator.gnd_id.label" /></th>
						
							<th></th>
						</tr>
					</thead>
					<tbody>
					<g:each in="${creatorInstanceList}" var="creatorInstance">
						<tr>

							<td>${fieldValue(bean: creatorInstance, field: "lastname")}</td>

							<td>${fieldValue(bean: creatorInstance, field: "firstname")}</td>
						
							<td>${fieldValue(bean: creatorInstance, field: "middlename")}</td>
						
							<td><%-- TODO [ticket=1789] ${fieldValue(bean: creatorInstance, field: "gnd_id")} --%></td>


						<td class="link">
							<g:link action="show" id="${creatorInstance.id}" class="ui tiny button">${message('code':'default.button.show.label')}</g:link>
							<g:link action="edit" id="${creatorInstance.id}" class="ui tiny button">${message('code':'default.button.edit.label')}</g:link>
						</td>
						</tr>
					</g:each>
					</tbody>
				</table>

				<semui:paginate total="${creatorInstanceTotal}" />
	</body>
</html>