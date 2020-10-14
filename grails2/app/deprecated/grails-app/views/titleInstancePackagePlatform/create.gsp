<%@ page import="com.k_int.kbplus.TitleInstancePackagePlatform" %>
<!doctype html>
<r:require module="scaffolding" />
<html>
	<head>
		<meta name="layout" content="semanticUI">
		<g:set var="entityName" value="${message(code: 'titleInstancePackagePlatform.label')}" />
		<title><g:message code="default.create.label" args="[entityName]" /></title>
	</head>
	<body>
		<div>
			
			<div class="span3">
				<div class="well">
					<ul class="nav nav-list">
						<li class="nav-header">${entityName}</li>
						<li>
							<g:link class="list" action="list">
								<i class="icon-list"></i>
								<g:message code="default.list.label" args="[entityName]" />
							</g:link>
						</li>
						<li class="active">
							<g:link class="create" action="create">
								<i class="icon-plus icon-white"></i>
								<g:message code="default.create.label" args="[entityName]" />
							</g:link>
						</li>
					</ul>
				</div>
			</div>
			
			<div class="span9">


					<h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon /><g:message code="default.create.label" args="[entityName]" /></h1>

				<semui:messages data="${flash}" />

					<semui:errors bean="${titleInstancePackagePlatformInstance}" />

				<fieldset>
					<g:form class="ui form" action="create" >
						<fieldset>
							<f:all bean="titleInstancePackagePlatformInstance"/>
							<div class="ui form-actions">
								<button type="submit" class="ui button">
									<i class="checkmark icon"></i>
									<g:message code="default.button.create.label"/>
								</button>
							</div>
						</fieldset>
					</g:form>
				</fieldset>
				
			</div>

		</div>
	</body>
</html>
