<%@ page import="de.laser.Identifier; de.laser.IdentifierNamespace; de.laser.I10nTranslation" %>
<!doctype html>
<html>
	<head>
		<meta name="layout" content="semanticUI">
		<title>${message(code:'laser')} : ${message(code: 'menu.admin.manageIdentifierNamespaces')}</title>
	</head>

		<semui:breadcrumbs>
			<semui:crumb message="menu.admin.dash" controller="admin" action="index" />
			<semui:crumb message="menu.admin.manageIdentifierNamespaces" class="active"/>
		</semui:breadcrumbs>
		<br>
		<h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon /><g:message code="menu.admin.manageIdentifierNamespaces"/></h1>

		<semui:messages data="${flash}" />

        <semui:errors bean="${identifierNamespaceInstance}" />

        <semui:form message="identifier.namespace.add.label">
            <g:form class="ui form" action="manageNamespaces">
                <div class="two fields">
                    <div class="field ${hasErrors(bean: identifierNamespaceInstance, field: 'name_de', 'error')} ">
                        <label for="name_de"><g:message code="default.name.label" /> (DE)</label>
                        <g:textField name="name_de"/>
                    </div>

                    <div class="field ${hasErrors(bean: identifierNamespaceInstance, field: 'name_en', 'error')} ">
                        <label for="name_en"><g:message code="default.name.label" /> (EN)</label>
                        <g:textField name="name_en"/>
                    </div>
                </div>

                <div class="two fields">
                    <div class="field ${hasErrors(bean: identifierNamespaceInstance, field: 'description_de', 'error')} ">
                        <label for="description_de"><g:message code="default.description.label" /> (DE)</label>
                        <g:textField name="description_de"/>
                    </div>

                    <div class="field ${hasErrors(bean: identifierNamespaceInstance, field: 'description_en', 'error')} ">
                        <label for="description_en"><g:message code="default.description.label" /> (EN)</label>
                        <g:textField name="description_en"/>
                    </div>
                </div>

                <div class="two fields">
                    <div class="field ${hasErrors(bean: identifierNamespaceInstance, field: 'ns', 'error')} required">
                        <label for="ns"><g:message code="identifierNamespace.ns.label" /></label>
                        <g:textField name="ns" required=""/>
                    </div>

                    <div class="field ${hasErrors(bean: identifierNamespaceInstance, field: 'nsType', 'error')} ">
                        <label for="nsType"><g:message code="default.type.label" /></label>
                        <g:select id="nsType" name="nsType" class="ui dropdown la-clearable"
                                  from="${IdentifierNamespace.getAVAILABLE_NSTYPES()}"
                                  noSelection="${['': message(code: 'default.select.choose.label')]}"/>
                    </div>
                </div>

                <div class="two fields">
                    <div class="field ${hasErrors(bean: identifierNamespaceInstance, field: 'urlPrefix', 'error')} ">
                        <label for="urlPrefix"><g:message code="identifierNamespace.urlPrefix.label" /></label>
                        <g:textField name="urlPrefix"/>
                    </div>

                    <div class="field ${hasErrors(bean: identifierNamespaceInstance, field: 'family', 'error')} ">
                        <label for="family"><g:message code="identifierNamespace.family.label" /></label>
                        <g:textField name="family"/>
                    </div>
                </div>

                <div class="two fields">
                    <div class="field ${hasErrors(bean: identifierNamespaceInstance, field: 'isUnique', 'error')} ">
                        <label for="isUnique"><g:message code="identifierNamespace.unique.label" /></label>
                        <g:checkBox name="isUnique" checked="true" />
                    </div>

                    <div class="field ${hasErrors(bean: identifierNamespaceInstance, field: 'validationRegex', 'error')} ">
                        <label for="validationRegex"><g:message code="identifierNamespace.validationRegex.label" /></label>
                        <g:textField name="validationRegex"/>
                    </div>
                </div>

                <input name="isHidden" type="hidden" value="false" />

                <button type="submit" class="ui button">
                    <g:message code="default.button.create.label"/>
                </button>
            </g:form>
        </semui:form>

        <g:if test="${cmd == 'details'}">

            <g:link controller="admin" action="manageNamespaces" class="ui button right floated"><g:message code="default.button.back"/></g:link>

            &nbsp;&nbsp;

            <h2 class="ui header"><g:message code="identifierNamespace.detailsStats" args="${[identifierNamespaceInstance.ns]}" /></h2>

            <g:each in="${detailsStats}" var="list">
                <g:if test="${list && list.value}">
                    <p><strong>${list.key} - ${list.value.size()} <g:message code="default.matches.label"/></strong></p>
                </g:if>
            </g:each>

            &nbsp;

            <g:each in="${detailsStats}" var="list">
                <g:if test="${list && list.value}">
                    <p><strong><i class="ui icon angle right"></i> ${list.key}</strong></p>
                    <div class="ui list">
                        <g:each in="${list.value}" var="entry" status="i">
                            <div class="item" <%= ((i+1)%10)==0 ? 'style="margin-bottom:1.2em"':''%>>
                                ${entry[0]}
                                &nbsp;&nbsp;&nbsp;&nbsp; &rarr; &nbsp;&nbsp;&nbsp;&nbsp;
                                <a href="${list.key}/${entry[1]}">${list.key}/${entry[1]}</a>
                            </div>
                        </g:each>
                    </div>
                </g:if>
            </g:each>
        </g:if>
        <g:else>
                <table class="ui celled la-table compact table">
                    <thead>
						<tr>
							<th><g:message code="identifierNamespace.ns.label"/></th>
							<th></th>
							<th><g:message code="default.name.label"/> (${currentLang})</th>
							<th><g:message code="default.description.label"/> (${currentLang})</th>
							<th><g:message code="identifierNamespace.family.label"/></th>
							<th><g:message code="default.type.label"/></th>
                            <th><g:message code="identifierNamespace.validationRegex.label"/></th>
                            <th><g:message code="identifierNamespace.urlPrefix.label"/></th>
                            <%--<th><g:message code="identifierNamespace.hide.label"/></th>--%>
                            <th><g:message code="identifierNamespace.unique.label"/></th>
							<th></th>
						</tr>
                    </thead>
                    <tbody>
						<g:each in="${IdentifierNamespace.where{}.sort('ns')}" var="idNs">
							<tr>
                                <g:if test="${Identifier.countByNs(idNs) == 0}">
                                    <td>
                                        <semui:xEditable owner="${idNs}" field="ns"/>
                                    </td>
                                    <td></td>
                                    <td>
                                        <semui:xEditable owner="${idNs}" field="name_${currentLang}"/>
                                    </td>
                                    <td>
                                        <semui:xEditable owner="${idNs}" field="description_${currentLang}"/>
                                    </td>
                                    <td>
                                        <semui:xEditable owner="${idNs}" field="family"/>
                                    </td>
                                    <td>
                                        <semui:xEditable owner="${idNs}" field="nsType"/>
                                    </td>
                                    <td>
                                        <semui:xEditable owner="${idNs}" field="validationRegex"/>
                                    </td>
                                    <td>
                                        <semui:xEditable owner="${idNs}" field="urlPrefix" validation="url"/>
                                    </td>
                                    <%--<td>${fieldValue(bean: idNs, field: "hide")}</td>--%>
                                    <td>
                                        <semui:xEditableBoolean owner="${idNs}" field="isUnique"/>
                                    </td>
                                    <td>
                                        <g:link controller="admin" action="manageNamespaces"
                                                params="${[cmd: 'deleteNamespace', oid: IdentifierNamespace.class.name + ':' + idNs.id]}" class="ui icon negative button">
                                            <i class="trash alternate icon"></i>
                                        </g:link>
                                    </td>
                                </g:if>
                                <g:else>
                                    <td>
                                        ${fieldValue(bean: idNs, field: "ns")}
                                    </td>
                                    <td>
                                        ${Identifier.countByNs(idNs)}
                                    </td>
                                    <td>
                                        <semui:xEditable owner="${idNs}" field="name_${currentLang}"/>
                                    </td>
                                    <td>
                                        <semui:xEditable owner="${idNs}" field="description_${currentLang}"/>
                                    </td>
                                    <td>
                                        <semui:xEditable owner="${idNs}" field="family"/>
                                    </td>
                                    <td>
                                        ${fieldValue(bean: idNs, field: "nsType")}
                                    </td>
                                    <td>
                                        ${fieldValue(bean: idNs, field: "validationRegex")}
                                    </td>
                                    <td>
                                        <semui:xEditable owner="${idNs}" field="urlPrefix" validation="url"/>
                                    </td>
                                    <%--<td>${fieldValue(bean: idNs, field: "hide")}</td>--%>
                                    <td>
                                        ${idNs.isUnique}
                                    </td>
                                    <td>
                                        <%
                                            List tooltip = []
                                            globalNamespaceStats.each { e ->
                                                if ( e[1] == idNs.id) {
                                                    if (e[2] > 0) tooltip.add("Verträge: ${e[2]}")
                                                    if (e[3] > 0) tooltip.add("Organisationen: ${e[3]}")
                                                    if (e[4] > 0) tooltip.add("Pakete: ${e[4]}")
                                                    if (e[5] > 0) tooltip.add("Lizenzen: ${e[5]}")
                                                    if (e[6] > 0) tooltip.add("Titel: ${e[6]}")
                                                    if (e[7] > 0) tooltip.add("TIPPs: ${e[7]}")
                                                }
                                            }
                                        %>
                                        <g:if test="${tooltip}">
                                            <span data-tooltip="Verwendet für ${tooltip.join(', ')}" data-position="left center"
                                                  class="la-long-tooltip la-popup-tooltip la-delay">
                                                <g:link class="ui button icon" controller="admin" action="manageNamespaces"
                                                        params="${[cmd: 'details', oid: IdentifierNamespace.class.name + ':' + idNs.id]}"><i class="ui icon question"></i></g:link>
                                            </span>
                                        </g:if>
                                    </td>
                                </g:else>
                            </tr>
						</g:each>
                    </tbody>
                </table>
        </g:else>
	</body>
</html>
