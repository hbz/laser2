
<%@ page import="de.laser.RefdataCategory; com.k_int.kbplus.Contact; de.laser.helper.RDStore; de.laser.helper.RDConstants" %>
<!doctype html>
<html>
	<head>
		<meta name="layout" content="semanticUI">
		<g:set var="entityName" value="${message(code: 'contact.label')}" />
		<title><g:message code="default.list.label" args="[entityName]" /></title>
	</head>
	<body>
		<div>
				
			<h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon /><g:message code="default.list.label" args="[entityName]" /></h1>

			<semui:messages data="${flash}" />
				
				<table class="ui sortable celled la-table table">
					<thead>
						<tr>
						
							<g:sortableColumn property="contentType" title="${message(code: 'contact.contentType.label')}" />
						
							<g:sortableColumn property="content" title="${message(code: 'contact.content.label')}" />
						
							<th class="header">${RefdataCategory.getByDesc(RDConstants.CONTACT_TYPE).getI10n('desc')}</th>

               				<th class="header"><g:message code="contact.prs.label" /></th>

							<th class="header"><g:message code="contact.org.label" /></th>

							<th class="header"><g:message code="person.isPublic.label" /></th>

							<th></th>
						</tr>
					</thead>
					<tbody>
					<g:each in="${contactInstanceList}" var="contactInstance">
						<tr>
						
							<td>${contactInstance?.contentType}</td>
						
							<td>${fieldValue(bean: contactInstance, field: "content")}</td>
						
							<td>${fieldValue(bean: contactInstance, field: "type")}</td>

							<td>${fieldValue(bean: contactInstance, field: "prs")}</td>

							<td>${fieldValue(bean: contactInstance, field: "org")}</td>
						
							<td>${contactInstance?.prs?.isPublic ? RDStore.YN_YES.getI10n('value') : RDStore.YN_NO.getI10n('value')}</td>
							
							<td class="link">
								<g:link action="show" id="${contactInstance.id}" class="ui tiny button">${message('code':'default.button.show.label')}</g:link>
							</td>
						</tr>
					</g:each>
					</tbody>
				</table>

					<semui:paginate total="${contactInstanceTotal}" />


		</div>
	</body>
</html>
