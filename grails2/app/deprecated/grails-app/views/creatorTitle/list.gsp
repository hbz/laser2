
<%@ page import="com.k_int.kbplus.CreatorTitle" %>
<!doctype html>
<html>
	<head>
		<meta name="layout" content="semanticUI">
		<g:set var="entityName" value="${message(code: 'creatorTitle.label', default: 'CreatorTitle')}" />
		<title><g:message code="default.list.label" args="[entityName]" /></title>
	</head>
	<body>
		<h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon /><g:message code="default.list.label" args="[entityName]" />
			<semui:totalNumber total="${creatorTitleInstanceTotal}"/>
		</h1>

        <semui:messages data="${flash}" />

		<div class="ui grid">

			<div class="twelve wide column">
				
				<table class="ui celled striped table">
					<thead>
						<tr>
						
							<th class="header"><g:message code="creatorTitle.creator.label" default="Creator" /></th>
						
							<g:sortableColumn property="dateCreated" title="${message(code: 'default.dateCreated.label')}" />
						
							<g:sortableColumn property="lastUpdated" title="${message(code: 'default.lastUpdated.label')}" />
						
							<th class="header"><g:message code="creatorTitle.role.label" default="Role" /></th>
						
							<th class="header"><g:message code="creatorTitle.title.label" default="Title" /></th>
						
							<th></th>
						</tr>
					</thead>
					<tbody>
					<g:each in="${creatorTitleInstanceList}" var="creatorTitleInstance">
						<tr>
						
							<td>${fieldValue(bean: creatorTitleInstance, field: "creator")}</td>
						
							<td><g:formatDate date="${creatorTitleInstance.dateCreated}" /></td>
						
							<td><g:formatDate date="${creatorTitleInstance.lastUpdated}" /></td>
						
							<td>${fieldValue(bean: creatorTitleInstance, field: "role")}</td>
						
							<td>${fieldValue(bean: creatorTitleInstance, field: "title")}</td>
							<td class="link">
								<g:link action="show" id="${creatorTitleInstance.id}" class="ui tiny button">${message('code':'default.button.show.label')}</g:link>
								<g:link action="edit" id="${creatorTitleInstance.id}" class="ui tiny button">${message('code':'default.button.edit.label')}</g:link>
							</td>
						</tr>
					</g:each>
					</tbody>
				</table>

<semui:paginate total="${creatorTitleInstanceTotal}" />
	</body>
</html>