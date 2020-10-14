<%@ page import="de.laser.RefdataValue; de.laser.properties.PropertyDefinition; de.laser.Person; de.laser.helper.RDStore; de.laser.RefdataCategory; de.laser.interfaces.AuditableSupport; de.laser.AuditConfig" %>
<laser:serviceInjection/>

<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code: 'laser')} : ${message(code: 'menu.institutions.manage_props')}</title>
</head>

<body>

<semui:breadcrumbs>
    <semui:crumb message="menu.institutions.manage_props" class="active" />
</semui:breadcrumbs>
<br>

<h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />${message(code: 'menu.institutions.manage_props')}</h1>

<g:render template="nav"/>

<semui:filter>
    <g:form action="manageProperties" method="post" class="ui form" id="${params.id}">
        <g:render template="/templates/properties/genericFilter" model="[propList: propList, hideFilterProp: true]"/>

        <div class="field la-field-right-aligned">
            <a href="${request.forwardURI}"
               class="ui reset primary button">${message(code: 'default.button.reset.label')}</a>
            <input type="submit" value="${message(code: 'default.button.filter.label')}" class="ui secondary button"/>
        </div>
    </g:form>
</semui:filter>




<g:if test="${filterPropDef}">

%{--<div class="ui segment">

    <strong>${message(code: 'subscription.propertiesMembers.propertySelected')}: ${filterPropDef?.getI10n('name')}</strong>
    <br>${message(code: 'default.type.label')}: ${PropertyDefinition.getLocalizedValue(filterPropDef?.type)}
    <g:if test="${filterPropDef?.isRefdataValueType()}">
        <g:set var="refdataValues" value="${[]}"/>
        <g:each in="${RefdataCategory.getAllRefdataValues(filterPropDef.refdataCategory)}"
                var="refdataValue">
            <g:set var="refdataValues"
                   value="${refdataValues + refdataValue?.getI10n('value')}"/>
        </g:each>
        <br>
        (${refdataValues.join('/')})
    </g:if>

</div>--}%

    <g:set var="editableOld" value="${editable}"/>

    <div class="ui segment">
        <g:form action="processManageProperties" method="post" class="ui form">
            <div class="field">
                <h2><g:if test="${filterPropDef.tenant != null}"><i class="shield alternate icon"></i></g:if><g:message code="property.manageProperties.add" args="[filterPropDef.getI10n('name')]"/></h2>${message(code: 'default.type.label')}: ${PropertyDefinition.getLocalizedValue(filterPropDef.type)}
                <g:hiddenField name="filterPropDef" value="${genericOIDService.getOID(filterPropDef)}"/>
                <g:if test="${filterPropDef.isRefdataValueType()}">
                    <g:set var="refdataValues" value="${[]}"/>
                    <g:each in="${RefdataCategory.getAllRefdataValues(filterPropDef.refdataCategory)}"
                            var="refdataValue">
                        <g:if test="${refdataValue.getI10n('value')}">
                            <g:set var="refdataValues" value="${refdataValues + refdataValue.getI10n('value')}"/>
                        </g:if>
                    </g:each>

                    (${refdataValues.join('/')})
                </g:if>
            </div>
            <div class="field required">
                <label for="filterPropValue">${message(code: 'subscription.property.value')}</label>
                <g:if test="${filterPropDef.isRefdataValueType()}">
                    <g:select class="ui search dropdown"
                              optionKey="id" optionValue="${{ it.getI10n('value') }}"
                              from="${RefdataCategory.getAllRefdataValues(filterPropDef.refdataCategory)}"
                              name="filterPropValue" value=""
                              required=""
                              noSelection='["": "${message(code: 'default.select.choose.label')}"]'/>
                </g:if>
                <g:elseif test="${filterPropDef.isIntegerType()}">
                    <input id="filterPropValue" type="number" name="filterPropValue">
                </g:elseif>
                <g:elseif test="${filterPropDef.isBigDecimalType()}">
                    <input id="filterPropValue" type="number" step="0.01" name="filterPropValue">
                </g:elseif>
                <g:elseif test="${filterPropDef.isDateType()}">
                    <g:datePicker name="filterPropValue"/>
                </g:elseif>
                <g:else>
                    <input id="filterPropValue" type="text" name="filterPropValue" placeholder="${message(code: 'license.search.property.ph')}"/>
                </g:else>
            </div>
            <table class="ui celled la-table table" id="withoutPropTable">
                <thead>
                    <tr>
                        <th colspan="5">
                            <input name="filterTable" id="filterTableWithoutProp" value="" placeholder="${message(code:'property.manageProperties.filterTable')}">
                        </th>
                    </tr>
                    <tr>
                        <th>
                            <g:message code="property.manageProperties.markForAdd"/><br>
                            <g:checkBox name="membersAddListToggler" id="membersAddListToggler" checked="false"/>
                        </th>
                        <g:if test="${accessService.checkPerm("ORG_CONSORTIUM") && auditable}">
                            <th>
                                <span data-tooltip="${message(code:'property.manageProperties.markForAudit')}"><i class="ui thumbtack icon"></i></span><br>
                                <g:checkBox name="membersAuditListToggler" id="membersAuditListToggler" checked="false"/>
                            </th>
                        </g:if>
                        <g:if test="${sortname}">
                            <th><g:message code="default.sortname.label"/></th>
                        </g:if>
                        <th><g:message code="default.name.label"/></th>
                        <th><g:message code="property.manageProperties.propertySelected"/>: ${filterPropDef.getI10n('name')}</th>
                        <th class="x"><button class="ui button" type="submit">${message(code: 'default.button.save_changes')}</button></th>
                    </tr>
                </thead>
                <tbody>
                    <g:each in="${objectsWithoutProp}" var="objWithoutProp">
                        <tr>
                            <td><g:checkBox name="newObjects" value="${objWithoutProp.id}" checked="false"/></td>
                            <g:if test="${accessService.checkPerm("ORG_CONSORTIUM") && auditable}">
                                <td><g:checkBox name="withAudit" value="${objWithoutProp.id}" checked="false"/></td>
                            </g:if>
                            <g:if test="${sortname}">
                                <td>${objWithoutProp.sortname}</td>
                            </g:if>
                            <td>${objWithoutProp.name}</td>
                            <td>
                                <div class="ui middle aligned selection list">
                                    <g:if test="${filterPropDef.tenant == null}">
                                        <div class="item">

                                            <g:set var="customProperty" value="${objWithoutProp.propertySet.find { it.tenant?.id == institution.id && it.type == filterPropDef }}"/>
                                            <g:if test="${customProperty}">
                                                <div class="header">${message(code: 'subscription.propertiesMembers.CustomProperty')}: ${filterPropDef.getI10n('name')}</div>

                                                <div class="content">
                                                    <p>
                                                        <g:if test="${customProperty.type.isIntegerType()}">
                                                            <semui:xEditable owner="${customProperty}" type="number"
                                                                             field="intValue"/>
                                                        </g:if>
                                                        <g:elseif test="${customProperty.type.isStringType()}">
                                                            <semui:xEditable owner="${customProperty}" type="text"
                                                                             field="stringValue"/>
                                                        </g:elseif>
                                                        <g:elseif test="${customProperty.type.isBigDecimalType()}">
                                                            <semui:xEditable owner="${customProperty}" type="text"
                                                                             field="decValue"/>
                                                        </g:elseif>
                                                        <g:elseif test="${customProperty.type.isDateType()}">
                                                            <semui:xEditable owner="${customProperty}" type="date"
                                                                             field="dateValue"/>
                                                        </g:elseif>
                                                        <g:elseif test="${customProperty.type.isURLType()}">
                                                            <semui:xEditable owner="${customProperty}" type="url" field="urlValue"

                                                                             class="la-overflow la-ellipsis"/>
                                                            <g:if test="${customProperty.value}">
                                                                <semui:linkIcon href="${customProperty.value}"/>
                                                            </g:if>
                                                        </g:elseif>
                                                        <g:elseif test="${customProperty.type.isRefdataValueType()}">
                                                            <semui:xEditableRefData owner="${customProperty}" type="text"
                                                                                    field="refValue"
                                                                                    config="${customProperty.type.refdataCategory}"/>
                                                        </g:elseif>
                                                    </p>
                                                    <g:if test="${customProperty.hasProperty('paragraph')}">
                                                        <p><semui:xEditable owner="${customProperty}" type="text" field="paragraph"/></p>
                                                    </g:if>

                                                    <%
                                                        if (AuditConfig.getConfig(customProperty)) {
                                                            if (objWithoutProp.isSlaved) {
                                                                println '&nbsp; <span class="la-popup-tooltip la-delay" data-content="Wert wird automatisch geerbt." data-position="top right"><i class="icon thumbtack blue"></i></span>'
                                                            } else {
                                                                println '&nbsp; <span class="la-popup-tooltip la-delay" data-content="Wert wird geerbt." data-position="top right"><i class="icon thumbtack grey"></i></span>'
                                                            }
                                                        }
                                                    %>

                                                </div>
                                            </g:if>
                                            <g:else>
                                                <div class="content">
                                                    ${message(code: 'subscription.propertiesMembers.noCustomProperty')}
                                                </div>
                                            </g:else>
                                        </div>
                                    </g:if>
                                    <g:if test="${filterPropDef.tenant != null}">

                                        <div class="item">

                                            <g:set var="privateProperty" value="${objWithoutProp.propertySet.find { it.type == filterPropDef }}"/>
                                            <g:if test="${privateProperty}">
                                                <div class="header">${message(code: 'subscription.propertiesMembers.PrivateProperty')} ${contextService.org}: ${filterPropDef.getI10n('name')}</div>

                                                <div class="content">
                                                    <p>
                                                        <g:set var="editable" value="${!(AuditConfig.getConfig(privateProperty))}"
                                                               scope="request"/>

                                                        <g:if test="${privateProperty.type.isIntegerType()}">
                                                            <semui:xEditable owner="${privateProperty}" type="number"
                                                                             field="intValue"/>
                                                        </g:if>
                                                        <g:elseif test="${privateProperty.type.isStringType()}">
                                                            <semui:xEditable owner="${privateProperty}" type="text"
                                                                             field="stringValue"/>
                                                        </g:elseif>
                                                        <g:elseif test="${privateProperty.type.isBigDecimalType()}">
                                                            <semui:xEditable owner="${privateProperty}" type="text"
                                                                             field="decValue"/>
                                                        </g:elseif>
                                                        <g:elseif test="${privateProperty.type.isDateType()}">
                                                            <semui:xEditable owner="${privateProperty}" type="date"
                                                                             field="dateValue"/>
                                                        </g:elseif>
                                                        <g:elseif test="${privateProperty.type.isURLType()}">
                                                            <semui:xEditable owner="${privateProperty}" type="url" field="urlValue"

                                                                             class="la-overflow la-ellipsis"/>
                                                            <g:if test="${privateProperty.value}">
                                                                <semui:linkIcon href="${privateProperty.value}"/>
                                                            </g:if>
                                                        </g:elseif>
                                                        <g:elseif test="${privateProperty.type.isRefdataValueType()}">
                                                            <semui:xEditableRefData owner="${privateProperty}" type="text"
                                                                                    field="refValue"
                                                                                    config="${privateProperty.type.refdataCategory}"/>
                                                        </g:elseif>
                                                    </p>
                                                    <g:if test="${privateProperty.hasProperty('paragraph')}">
                                                        <p><semui:xEditable owner="${privateProperty}" type="text" field="paragraph"/></p>
                                                    </g:if>

                                                    <%
                                                        if (AuditConfig.getConfig(privateProperty)) {
                                                            if (objWithoutProp.isSlaved) {
                                                                println '&nbsp; <span class="la-popup-tooltip la-delay" data-content="Wert wird automatisch geerbt." data-position="top right"><i class="icon thumbtack blue"></i></span>'
                                                            } else {
                                                                println '&nbsp; <span class="la-popup-tooltip la-delay" data-content="Wert wird geerbt." data-position="top right"><i class="icon thumbtack grey"></i></span>'
                                                            }
                                                        }
                                                    %>

                                                </div>
                                            </g:if>
                                            <g:else>
                                                <div class="content">
                                                    ${message(code: 'subscription.propertiesMembers.noPrivateProperty')}
                                                </div>
                                            </g:else>
                                        </div>
                                    </g:if>
                                </div>
                            </td>
                            <td class="x">
                                <g:link controller="${objWithoutProp.displayController}" action="show" id="${objWithoutProp.id}" class="ui icon button"><i class="write icon"></i></g:link>
                            </td>
                        </tr>
                    </g:each>
                </tbody>
            </table>
        </g:form>
    </div>

    <div class="ui segment">

        <g:form action="processManageProperties" method="post" class="ui form">
            <g:hiddenField name="id" value="${params.id}"/>

            <div class="field">
                <h2>
                    <g:if test="${filterPropDef.tenant != null}">
                        <i class="shield alternate icon"></i>
                    </g:if>
                    <g:message code="property.manageProperties.edit" args="[filterPropDef.getI10n('name')]"/>
                </h2>
                <g:hiddenField name="filterPropDef" value="${genericOIDService.getOID(filterPropDef)}"/>
                ${message(code: 'default.type.label')}: ${PropertyDefinition.getLocalizedValue(filterPropDef.type)}
                <g:if test="${filterPropDef.isRefdataValueType()}">
                    <g:set var="refdataValues" value="${[]}"/>
                    <g:each in="${RefdataCategory.getAllRefdataValues(filterPropDef.refdataCategory)}"
                            var="refdataValue">
                        <g:if test="${refdataValue.getI10n('value')}">
                            <g:set var="refdataValues" value="${refdataValues + refdataValue.getI10n('value')}"/>
                        </g:if>
                    </g:each>

                    (${refdataValues.join('/')})
                </g:if>

            </div>

            <div class="field">
                <label for="filterPropValue">${message(code: 'subscription.property.value')}</label>
                <g:if test="${filterPropDef.isRefdataValueType()}">
                    <g:select class="ui search dropdown"
                              optionKey="id" optionValue="${{ it.getI10n('value') }}"
                              from="${RefdataCategory.getAllRefdataValues(filterPropDef.refdataCategory)}"
                              name="filterPropValue" value=""
                              noSelection='["": "${message(code: 'default.select.choose.label')}"]'/>
                </g:if>
                <g:elseif test="${filterPropDef.isIntegerType()}">
                    <input id="filterPropValue" type="number" name="filterPropValue">
                </g:elseif>
                <g:elseif test="${filterPropDef.isBigDecimalType()}">
                    <input id="filterPropValue" type="number" step="0.01" name="filterPropValue">
                </g:elseif>
                <g:elseif test="${filterPropDef.isDateType()}">
                    <g:datePicker name="filterPropValue"/>
                </g:elseif>
                <g:else>
                    <input id="filterPropValue" type="text" name="filterPropValue" placeholder="${message(code: 'license.search.property.ph')}">
                </g:else>
            </div>

            <table class="ui celled la-table table" id="existingObjTable">
                <thead>
                    <tr>
                        <th colspan="6">
                            <input name="filterTable" id="filterTableExistingObj" value="" placeholder="${message(code:'property.manageProperties.filterTable')}">
                        </th>
                    </tr>
                    <tr>
                        <th>
                            <g:checkBox name="membersListToggler" id="membersListToggler" checked="false"/>
                        </th>
                        <th>${message(code: 'sidewide.number')}</th>
                        <g:if test="${sortname}">
                            <th>${message(code: 'default.sortname.label')}</th>
                        </g:if>
                        <th>${message(code: 'default.name.label')}</th>
                        <th>${message(code: 'property.manageProperties.propertySelected')}: ${filterPropDef.getI10n('name')}</th>
                        <th class="x">
                            <button class="ui button" type="submit" name="saveChanges" value="true">${message(code: 'default.button.save_changes')}</button>
                            <button class="ui button negative" type="submit" name="deleteProperties" value="true">${message(code: 'property.manageProperties.deleteProperty.button', args: [filterPropDef.getI10n('name')])}</button>
                                <%-- TODO ask Ingrid
                                    js-open-confirm-modal
                                    data-confirm-tokenMsg="${message(code: 'property.manageProperties.deleteProperty.button.confirm')}"
                                    data-confirm-term-how="ok"
                                --%>
                        </th>
                    </tr>
                </thead>
                <tbody>
                    <g:each in="${filteredObjs}" status="i" var="row">
                        <tr>
                            <td>
                                <g:checkBox name="selectedObjects" value="${row.id}" checked="false"/>
                            </td>
                            <td>${i + 1}</td>
                            <td>
                                ${row.name}
                            </td>
                            <g:if test="${row.sortname}">
                                <td>${row.sortname}</td>
                            </g:if>
                            <td>

                                <div class="ui middle aligned selection list">

                                    <g:if test="${filterPropDef.tenant == null}">
                                        <div class="item">

                                            <g:set var="customProperty" value="${row.propertySet.find { it.tenant?.id == institution.id && it.type.id == filterPropDef.id }}"/>
                                            <g:if test="${customProperty}">
                                                <div class="header">${message(code: 'subscription.propertiesMembers.CustomProperty')}: ${filterPropDef.getI10n('name')}</div>

                                                <div class="content">
                                                    <p><g:if test="${customProperty.type.isIntegerType()}">
                                                        <semui:xEditable owner="${customProperty}" type="number"
                                                                         field="intValue"/>
                                                    </g:if>
                                                        <g:elseif test="${customProperty.type.isStringType()}">
                                                            <semui:xEditable owner="${customProperty}" type="text"
                                                                             field="stringValue"/>
                                                        </g:elseif>
                                                        <g:elseif
                                                                test="${customProperty.type.isBigDecimalType()}">
                                                            <semui:xEditable owner="${customProperty}" type="text"
                                                                             field="decValue"/>
                                                        </g:elseif>
                                                        <g:elseif test="${customProperty.type.isDateType()}">
                                                            <semui:xEditable owner="${customProperty}" type="date"
                                                                             field="dateValue"/>
                                                        </g:elseif>
                                                        <g:elseif test="${customProperty.type.isURLType()}">
                                                            <semui:xEditable owner="${customProperty}" type="url"
                                                                             field="urlValue"

                                                                             class="la-overflow la-ellipsis"/>
                                                            <g:if test="${customProperty.value}">
                                                                <semui:linkIcon href="${customProperty.value}"/>
                                                            </g:if>
                                                        </g:elseif>
                                                        <g:elseif
                                                                test="${customProperty.type.isRefdataValueType()}">
                                                            <semui:xEditableRefData owner="${customProperty}" type="text"
                                                                                    field="refValue"
                                                                                    config="${customProperty.type.refdataCategory}"/>
                                                        </g:elseif>
                                                    </p>

                                                    <g:if test="${customProperty.hasProperty('paragraph')}">
                                                        <p><semui:xEditable owner="${customProperty}" type="text" field="paragraph"/></p>
                                                    </g:if>
                                                    <%
                                                        if ((customProperty.hasProperty('instanceOf') && customProperty.instanceOf && AuditConfig.getConfig(customProperty.instanceOf)) || AuditConfig.getConfig(customProperty)) {
                                                            if (row.isSlaved) {
                                                                print '<span class="la-popup-tooltip la-delay" data-content="Wert wird automatisch geerbt." data-position="top right"><i class="icon thumbtack blue"></i></span>'
                                                            } else {
                                                                print '<span class="la-popup-tooltip la-delay" data-content="Wert wird geerbt." data-position="top right"><i class="icon thumbtack grey"></i></span>'
                                                            }
                                                        }
                                                    %>

                                                </div>
                                            </g:if><g:else>
                                            <div class="content">
                                                ${message(code: 'subscription.propertiesMembers.noCustomProperty')}
                                            </div>
                                        </g:else>
                                        </div>
                                    </g:if>
                                    <g:if test="${filterPropDef.tenant != null}">

                                        <div class="item">

                                            <g:set var="privateProperty" value="${row.propertySet.find { it.type.id == filterPropDef.id }}"/>

                                            <g:if test="${privateProperty}">
                                                <div class="header">${message(code: 'subscription.propertiesMembers.PrivateProperty')} ${contextService.org}: ${filterPropDef.getI10n('name')}</div>

                                                <div class="content">
                                                    <p>
                                                        <g:if test="${privateProperty.type.isIntegerType()}">
                                                            <semui:xEditable owner="${privateProperty}" type="number"
                                                                             field="intValue"/>
                                                        </g:if>
                                                        <g:elseif test="${privateProperty.type.isStringType()}">
                                                            <semui:xEditable owner="${privateProperty}" type="text"
                                                                             field="stringValue"/>
                                                        </g:elseif>
                                                        <g:elseif
                                                                test="${privateProperty.type.isBigDecimalType()}">
                                                            <semui:xEditable owner="${privateProperty}" type="text"
                                                                             field="decValue"/>
                                                        </g:elseif>
                                                        <g:elseif test="${privateProperty.type.isDateType()}">
                                                            <semui:xEditable owner="${privateProperty}" type="date"
                                                                             field="dateValue"/>
                                                        </g:elseif>
                                                        <g:elseif test="${privateProperty.type.isURLType()}">
                                                            <semui:xEditable owner="${privateProperty}" type="url"
                                                                             field="urlValue"
                                                                             class="la-overflow la-ellipsis"/>
                                                            <g:if test="${privateProperty.value}">
                                                                <semui:linkIcon href="${privateProperty.value}"/>
                                                            </g:if>
                                                        </g:elseif>
                                                        <g:elseif test="${privateProperty.type.isRefdataValueType()}">
                                                            <semui:xEditableRefData owner="${privateProperty}" type="text"
                                                                                    field="refValue"
                                                                                    config="${privateProperty.type.refdataCategory}"/>
                                                        </g:elseif>
                                                    </p>
                                                    <g:if test="${privateProperty.hasProperty('paragraph')}">
                                                        <p><semui:xEditable owner="${privateProperty}" type="text" field="paragraph"/></p>
                                                    </g:if>
                                                    <%
                                                        if ((privateProperty.hasProperty('instanceOf') && privateProperty.instanceOf && AuditConfig.getConfig(privateProperty.instanceOf)) || AuditConfig.getConfig(privateProperty)) {
                                                            if (row.isSlaved) {
                                                                println '&nbsp; <span class="la-popup-tooltip la-delay" data-content="Wert wird automatisch geerbt." data-position="top right"><i class="icon thumbtack blue"></i></span>'
                                                            } else {
                                                                println '&nbsp; <span class="la-popup-tooltip la-delay" data-content="Wert wird geerbt." data-position="top right"><i class="icon thumbtack grey"></i></span>'
                                                            }
                                                        }
                                                    %>

                                                </div>
                                            </g:if>
                                            <g:else>
                                                <div class="content">
                                                    ${message(code: 'subscription.propertiesMembers.noPrivateProperty')}
                                                </div>
                                            </g:else>

                                        </div>
                                    </g:if>
                                </div>

                            </td>

                            <td class="x">
                                <g:link controller="${row.displayController}" action="${row.displayAction}" id="${row.id}" class="ui icon button"><i class="write icon"></i></g:link>
                                <g:if test="${row.manageChildren}">
                                    <g:link controller="${row.displayController}" action="${row.manageChildren}" params="${row.manageChildrenParams}" class="ui icon button"><i class="users icon"></i></g:link>
                                </g:if>
                            </td>
                        </tr>
                    </g:each>
                </tbody>
            </table>
        </g:form>

    </div>
</g:if>

<g:else>
    <br>
    <g:if test="${!filterPropDef}">
        <strong><g:message code="property.manageProperties.noPropertySelected"/></strong>
    </g:if>
</g:else>

<div id="magicArea"></div>

<r:script>
    $('#membersListToggler').click(function () {
        if ($(this).prop('checked')) {
            $("tr[class!=disabled] input[name=selectedObjects]:visible").prop('checked', true);
        }
        else {
            $("tr[class!=disabled] input[name=selectedObjects]:visible").prop('checked', false);
        }
    });
    $('#membersAddListToggler').click(function () {
        if ($(this).prop('checked')) {
            $("tr[class!=disabled] input[name=newObjects]:visible").prop('checked', true);
        }
        else {
            $("tr[class!=disabled] input[name=newObjects]:visible").prop('checked', false);
        }
    });
    $('#membersAuditListToggler').click(function () {
        if ($(this).prop('checked')) {
            $("tr[class!=disabled] input[name=withAudit]:visible").prop('checked', true);
        }
        else {
            $("tr[class!=disabled] input[name=withAudit]:visible").prop('checked', false);
        }
    });
    $('#filterTableWithoutProp').keyup(function() {
        $("#withoutPropTable tbody tr:icontains('"+$(this).val()+"')").show();
        $("#withoutPropTable tbody tr:not(:icontains('"+$(this).val()+"'))").hide();
    });
    $('#filterTableExistingObj').keyup(function() {
        $("#existingObjTable tbody tr:icontains('"+$(this).val()+"')").show();
        $("#existingObjTable tbody tr:not(:icontains('"+$(this).val()+"'))").hide();
        //$("#existingObjTable tr:contains('"+$(this).val()+"')").addClass("positive");
        //$("#existingObjTable tr:not(:contains('"+$(this).val()+"'))").removeClass("positive");
    });
    $.expr[':'].icontains = function(a,i,m) {
        return $(a).text().toUpperCase().indexOf(m[3].toUpperCase()) >= 0;
    }
</r:script>

</body>
</html>

