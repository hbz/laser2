<%@ page import="de.laser.RefdataCategory; de.laser.helper.RDConstants; com.k_int.kbplus.Cluster" %>
<!doctype html>
<html>
	<head>
		<meta name="layout" content="semanticUI">
		<g:set var="entityName" value="${message(code: 'cluster.label')}" />
		<title><g:message code="default.list.label" args="[entityName]" /></title>
	</head>
	<body>
		<div>

			<h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon /><g:message code="default.list.label" args="[entityName]" /></h1>

			<semui:messages data="${flash}" />
				
				<table class="ui sortable celled la-table table">
					<thead>
						<tr>
						
							<g:sortableColumn property="definition" title="${message(code: 'default.definition.label')}" />
						
							<g:sortableColumn property="name" title="${message(code: 'default.name.label')}" />
						
							<th class="header">${RefdataCategory.getByDesc(RDConstants.CLUSTER_TYPE).getI10n('desc')}</th>
						
							<th></th>
						</tr>
					</thead>
					<tbody>
					<g:each in="${clusterInstanceList}" var="clusterInstance">
						<tr>
						
							<td>${fieldValue(bean: clusterInstance, field: "definition")}</td>
						
							<td>${fieldValue(bean: clusterInstance, field: "name")}</td>
						
							<td>${fieldValue(bean: clusterInstance, field: "type")}</td>
						
							<td class="link">
								<g:link action="show" id="${clusterInstance.id}" class="ui tiny button">${message('code':'default.button.show.label')}</g:link>
								<g:link action="edit" id="${clusterInstance.id}" class="ui tiny button">${message('code':'default.button.edit.label')}</g:link>
							</td>
						</tr>
					</g:each>
					</tbody>
				</table>

			<semui:paginate total="${clusterInstanceTotal}" />
		</div>
	</body>
</html>
