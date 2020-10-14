<%@ page import="com.k_int.kbplus.Platform" %>
<!doctype html>
<html>
	<head>
		<meta name="layout" content="semanticUI">
		<g:set var="entityName" value="${message(code: 'platform.label')}" />
		<title><g:message code="default.create.label" args="[entityName]" /></title>
	</head>
	<body>

			<h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon /><g:message code="default.create.label" args="[entityName]" /></h1>

			<semui:messages data="${flash}" />

			<semui:errors bean="${platformInstance}" />

			<fieldset>
				<g:form class="ui form" action="create" >
					<fieldset>
						<f:all bean="platformInstance"/>
						<div class="ui form-actions">
							<button type="submit" class="ui button">
								<i class="checkmark icon"></i>
								<g:message code="default.button.create.label" />
							</button>
						</div>
					</fieldset>
				</g:form>
			</fieldset>

	</body>
</html>
