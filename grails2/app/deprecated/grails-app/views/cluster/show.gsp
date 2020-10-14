
<%@ page import="de.laser.RefdataCategory; com.k_int.kbplus.Cluster" %>
<!doctype html>
<html>
	<head>
		<meta name="layout" content="semanticUI">
		<g:set var="entityName" value="${message(code: 'cluster.label')}" />
		<title><g:message code="default.show.label" args="[entityName]" /></title>
	</head>
	<body>
		<h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon /><g:message code="default.show.label" args="[entityName]" /></h1>

		<semui:messages data="${flash}" />

		<div class="ui grid">
			
			<div class="twelve wide column">

				<div class="inline-lists">
				<dl>
				
					<g:if test="${clusterInstance?.definition}">
						<dt><g:message code="cluster.definition.label" default="Definition" /></dt>
						<dd><g:fieldValue bean="${clusterInstance}" field="definition"/></dd>
					</g:if>

					<g:if test="${clusterInstance?.name}">
						<dt><g:message code="cluster.name.label" default="Name" /></dt>
						<dd><g:fieldValue bean="${clusterInstance}" field="name"/></dd>
					</g:if>

					<g:if test="${clusterInstance?.orgs}">
						<dt><g:message code="cluster.orgs.label" default="Orgs" /></dt>
						<dd><ul>
							<g:each in="${clusterInstance.orgs}" var="o">
							<li>${o?.roleType?.value} - <g:link controller="organisation" action="show" id="${o?.org?.id}">${o?.org?.name}</g:link></li>
							</g:each>
						</ul></dd>
					</g:if>

					<g:if test="${clusterInstance?.type}">
						<dt>${RefdataCategory.getByDesc(de.laser.helper.RDConstants.CLUSTER_TYPE).getI10n('desc')}</dt>
						<dd>${clusterInstance?.type}</dd>
					</g:if>
				
				</dl>
				</div>
				<g:form>
					<g:hiddenField name="id" value="${clusterInstance?.id}" />
					<div class="ui form-actions">
						<g:link class="ui button" action="edit" id="${clusterInstance?.id}">
							<i class="write icon"></i>
							<g:message code="default.button.edit.label" />
						</g:link>
						<button class="ui negative button" type="submit" name="_action_delete">
							<i class="trash alternate icon"></i>
							<g:message code="default.button.delete.label" />
						</button>
					</div>
				</g:form>
			</div><!-- .twelve -->

				<aside class="four wide column">
				</aside><!-- .four -->

		</div><!-- .grid -->
	</body>
</html>
