<%@ page import="com.k_int.kbplus.Creator" %>
<!doctype html>
<html>
	<head>
		<meta name="layout" content="semanticUI">
		<g:set var="entityName" value="${message(code: 'creator.label')}" />
		<title><g:message code="default.edit.label" args="[entityName]" /></title>
	</head>
	<body>
		<h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon /><g:message code="default.edit.label" args="[entityName]" /></h1>

        <semui:messages data="${flash}" />

		<div class="ui grid">

			<div class="twelve wide column">

				<g:hasErrors bean="${creatorInstance}">
				<semui:msg class="negative">
					<ul>
						<g:eachError bean="${creatorInstance}" var="error">
						<li <g:if test="${error in org.springframework.validation.FieldError}">data-field-id="${error.field}"</g:if>><g:message error="${error}"/></li>
						</g:eachError>
					</ul>
				</semui:msg>
				</g:hasErrors>

				<fieldset>
					<g:form class="ui form" action="edit" id="${creatorInstance?.id}" >
						<g:hiddenField name="version" value="${creatorInstance?.version}" />

                        <f:all bean="creatorInstance"/>
                        <div class="ui form-actions">
                            <button type="submit" class="ui button">
                                <i class="checkmark icon"></i>
                                <g:message code="default.button.update.label" />
                            </button>
                            <button type="submit" class="ui negative button" name="_action_delete" formnovalidate>
                                <i class="trash icon"></i>
                                <g:message code="default.button.delete.label" />
                            </button>
                        </div>
					</g:form>
				</fieldset>

			</div><!-- .twelve -->

            <aside class="four wide column">
                <g:render template="/templates/sideMenu" />
            </aside><!-- .four -->

		</div><!-- .grid -->
	</body>
</html>
