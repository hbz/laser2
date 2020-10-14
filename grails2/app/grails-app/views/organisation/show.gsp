<%@ page import="de.laser.RefdataValue; de.laser.RefdataCategory; de.laser.Person; org.springframework.context.i18n.LocaleContextHolder; de.laser.I10nTranslation; de.laser.OrgSubjectGroup; de.laser.helper.RDStore; de.laser.helper.RDConstants; de.laser.PersonRole; de.laser.Org; de.laser.properties.PropertyDefinition; de.laser.properties.PropertyDefinitionGroup; de.laser.OrgSetting" %>
<%@ page import="de.laser.Combo;grails.plugin.springsecurity.SpringSecurityUtils" %>
<laser:serviceInjection/>

<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI">
    <g:set var="allOrgTypeIds" value="${orgInstance.getAllOrgTypeIds()}" />
    <g:set var="isProviderOrAgency" value="${RDStore.OT_PROVIDER.id in allOrgTypeIds || RDStore.OT_AGENCY.id in allOrgTypeIds}" />
    <g:set var="isGrantedOrgRoleAdminOrOrgEditor" value="${SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN,ROLE_ORG_EDITOR')}" />
    <g:set var="isGrantedOrgRoleAdmin" value="${SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN')}" />

    <g:if test="${isProviderOrAgency}">
        <g:set var="entityName" value="${message(code: 'default.provider.label')}"/>
    </g:if>
    <g:elseif test="${institutionalView}">
        <g:set var="entityName" value="${message(code: 'org.institution.label')}"/>
    </g:elseif>
    <g:else>
        <g:set var="entityName" value="${message(code: 'org.label')}"/>
    </g:else>
    <title>${message(code: 'laser')} : ${message(code:'menu.institutions.org_info')}</title>

    <g:javascript src="properties.js"/>
</head>

<body>

<semui:debugInfo>
    <g:render template="/templates/debug/benchMark" model="[debug: benchMark]"/>
    <g:render template="/templates/debug/orgRoles" model="[debug: orgInstance.links]"/>
    <g:render template="/templates/debug/prsRoles" model="[debug: orgInstance.prsLinks]"/>
</semui:debugInfo>

<g:render template="breadcrumb"
          model="${[orgInstance: orgInstance, inContextOrg: inContextOrg, institutionalView: institutionalView]}"/>

<semui:controlButtons>
    <g:render template="actions" model="${[org: orgInstance, user: user]}"/>
</semui:controlButtons>

<h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon/>${orgInstance.name}</h1>

<g:if test="${inContextOrg && orgInstance.eInvoice && (!orgInstance.eInvoicePortal || !orgInstance.getLeitID()?.value)}">
    <div class="ui icon message warning">
        <i class="info icon"></i>
        <div class="content">
            <div class="header">${message(code: 'org.eInvoice.info.header')}</div>
            ${message(code: 'org.eInvoice.info.text')}
            <div class="ui bulleted list">
            <g:if test="${!orgInstance.eInvoicePortal}">
                <div class="item">${message(code: 'org.eInvoice.info.missing.eInvoicePortal')}</div>
            </g:if>
            <g:if test="${!orgInstance.getLeitID()?.value}">
                <div class="item">${message(code: 'org.eInvoice.info.missing.leitID')}</div>
            </g:if>
        </div>
        </div>
    </div>
</g:if>

<g:render template="nav" model="${[orgInstance: orgInstance, inContextOrg: inContextOrg, isProviderOrAgency: isProviderOrAgency]}"/>

<semui:objectStatus object="${orgInstance}" status="${orgInstance.status}"/>


%{--<g:if test="${departmentalView == false}">
    <g:render template="/templates/meta/identifier" model="${[object: orgInstance, editable: editable]}"/>
</g:if>--}%

<semui:messages data="${flash}"/>

<div class="ui stackable grid">
    <div class="twelve wide column">

        <div class="la-inline-lists">

            <div class="ui card">
                <div class="content">
                    <dl>
                        <dt><g:message code="default.name.label" /></dt>
                        <dd>
                            <semui:xEditable owner="${orgInstance}" field="name"/>
                            <g:if test="${orgInstance.getCustomerType() in ['ORG_INST', 'ORG_INST_COLLECTIVE']}">
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="bottom center"
                                      data-content="${orgInstance.getCustomerTypeI10n()}">
                                    <i class="chess rook grey icon"></i>
                                </span>
                            </g:if>
                        </dd>
                    </dl>
                    <g:if test="${!inContextOrg || isGrantedOrgRoleAdminOrOrgEditor}">
                        <dl>
                            <dt><g:message code="org.shortname.label" /></dt>
                            <dd>
                                <semui:xEditable owner="${orgInstance}" field="shortname"/>
                            </dd>
                        </dl>
                        <g:if test="${!isProviderOrAgency}">
                            <dl>
                                <dt>
                                    <g:message code="org.sortname.label" />
                                </dt>
                                <dd>
                                    <semui:xEditable owner="${orgInstance}" field="sortname"/>
                                </dd>
                            </dl>
                        </g:if>
                    </g:if>
                    <dl>
                        <dt><g:message code="org.url.label"/></dt>
                        <dd>
                            <semui:xEditable owner="${orgInstance}" type="url" field="url" class="la-overflow la-ellipsis" />
                            <g:if test="${orgInstance.url}">
                                <semui:linkIcon href="${orgInstance.url}" />
                            </g:if>
                            <br />&nbsp<br />&nbsp<br />
                        </dd>
                    </dl>
                    <g:if test="${!isProviderOrAgency}">
                        <dl>
                            <dt>
                                <g:message code="org.legalPatronName.label" />
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center"
                                      data-content="${message(code: 'org.legalPatronName.expl')}">
                                    <i class="question circle icon"></i>
                                </span>
                            </dt>
                            <dd>
                                <semui:xEditable owner="${orgInstance}" field="legalPatronName"/>
                            </dd>
                        </dl>
                    </g:if>
                    <g:if test="${ !isProviderOrAgency}">
                        <dl>
                            <dt>
                                <g:message code="org.urlGov.label"/>
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center"
                                      data-content="${message(code: 'org.urlGov.expl')}">
                                    <i class="question circle icon"></i>
                                </span>
                            </dt>
                            <dd>
                                <semui:xEditable owner="${orgInstance}" type="url" field="urlGov" class="la-overflow la-ellipsis" />
                                <g:if test="${orgInstance.urlGov}">
                                    <semui:linkIcon href="${orgInstance.urlGov}" />
                                </g:if>
                            </dd>
                        </dl>
                    </g:if>
                </div>
            </div><!-- .card -->

            <g:if test="${!isProviderOrAgency}">
                <div class="ui card">
                    <div class="content">
                        <dl>
                            <dt>
                                <g:message code="org.eInvoice.label" />
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center"
                                      data-content="${message(code: 'org.eInvoice.expl')}">
                                    <i class="question circle icon"></i>
                                </span>
                            </dt>
                            <dd>
                                <semui:xEditableBoolean owner="${orgInstance}" field="eInvoice"/>
                            </dd>
                        </dl>
                        <dl>
                            <dt>
                                <g:message code="org.eInvoicePortal.label" />
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center"
                                      data-content="${message(code: 'org.eInvoicePortal.expl')}">
                                    <i class="question circle icon"></i>
                                </span>
                            </dt>
                            <dd>
                                <semui:xEditableRefData owner="${orgInstance}" field="eInvoicePortal" config="${RDConstants.E_INVOICE_PORTAL}"/>
                            </dd>
                        </dl>
                    </div>
                </div><!-- .card -->
            </g:if>

            <g:if test="${isGrantedOrgRoleAdmin}">
                <div class="ui card">
                    <div class="content">
                        <dl>
                            <dt><g:message code="org.sector.label" /></dt>
                            <dd>
                                <semui:xEditableRefData owner="${orgInstance}" field="sector" config="${RDConstants.ORG_SECTOR}" overwriteEditable="${isGrantedOrgRoleAdminOrOrgEditor}"/>
                            </dd>
                        </dl>
                        <dl>
                            <dt>${message(code: 'default.status.label')}</dt>

                            <dd>
                                <g:if test="${isGrantedOrgRoleAdminOrOrgEditor}">
                                    <semui:xEditableRefData owner="${orgInstance}" field="status" config="${RDConstants.ORG_STATUS}"/>
                                </g:if>
                            </dd>
                        </dl>
                    </div>
                </div><!-- .card -->
            </g:if>

            <g:if test="${isGrantedOrgRoleAdminOrOrgEditor}">
                <div class="ui card">
                    <div class="content">
                        <%-- ROLE_ADMIN: all , ROLE_ORG_EDITOR: all minus Consortium --%>
                        <dl>
                            <dt><g:message code="org.orgType.label" /></dt>
                            <dd>
                                <%
                                    // hotfix:
                                    String locale = I10nTranslation.decodeLocale(LocaleContextHolder.getLocale())
                                    def orgType_types = RefdataValue.executeQuery("select rdv from RefdataValue as rdv where rdv.owner.desc='" + RDConstants.ORG_TYPE + "' order by rdv.order, rdv.value_" + locale)
                                    def orgType_editable = SpringSecurityUtils.ifAnyGranted('ROLE_ADMIN')

                                    if (!orgType_editable) {
                                        orgType_editable = SpringSecurityUtils.ifAnyGranted('ROLE_ORG_EDITOR')

                                        orgType_types = orgType_types.minus(RDStore.OT_CONSORTIUM)
                                    }

                                %>
                                <g:render template="orgTypeAsList"
                                          model="${[org: orgInstance, orgTypes: orgInstance.orgType, availableOrgTypes: orgType_types, editable: orgType_editable]}"/>
                            </dd>
                        </dl>

                        <g:render template="orgTypeModal"
                                  model="${[org: orgInstance, availableOrgTypes: orgType_types, editable: orgType_editable]}"/>
                    </div>
                </div>
            </g:if>

            <g:if test="${ !isProviderOrAgency}">
                <div class="ui card">
                    <div class="content">
                        <dl>
                            <dt>
                                <g:message code="org.libraryType.label" />
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center"
                                      data-content="${message(code: 'org.libraryType.expl')}">
                                    <i class="question circle icon"></i>
                                </span>
                            </dt>
                            <dd>
                                <semui:xEditableRefData owner="${orgInstance}" field="libraryType"
                                                        config="${RDConstants.LIBRARY_TYPE}"/>
                            </dd>
                        </dl>
                        <dl>
                            <dt>
                                <g:message code="org.subjectGroup.label" />
                            </dt>
                            <dd>
                                <%
                                    def subjectGroups = RefdataCategory.getAllRefdataValues(RDConstants.SUBJECT_GROUP)
                                %>
                                <g:render template="orgSubjectGroupAsList"
                                          model="${[org: orgInstance, orgSubjectGroups: orgInstance.subjectGroup, availableSubjectGroups: subjectGroups, editable: editable]}"/>

                                <g:render template="orgSubjectGroupModal"
                                          model="${[org: orgInstance, availableSubjectGroups: subjectGroups, editable: editable]}"/>
                            </dd>
                        </dl>
                        <dl>
                            <dt>
                                <g:message code="org.libraryNetwork.label" />
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center"
                                      data-content="${message(code: 'org.libraryNetwork.expl')}">
                                    <i class="question circle icon"></i>
                                </span>
                            </dt>
                            <dd>
                                <semui:xEditableRefData owner="${orgInstance}" field="libraryNetwork"
                                                        config="${RDConstants.LIBRARY_NETWORK}"/>
                            </dd>
                        </dl>
                        <dl>
                            <dt>
                                <g:message code="org.funderType.label" />
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center"
                                      data-content="${message(code: 'org.funderType.expl')}">
                                    <i class="question circle icon"></i>
                                </span>
                            </dt>
                            <dd>
                                <semui:xEditableRefData owner="${orgInstance}" field="funderType" config="${RDConstants.FUNDER_TYPE}"/>
                            </dd>
                        </dl>
                        <dl>
                            <dt>
                                <g:message code="org.funderHSK.label" />
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center"
                                      data-content="${message(code: 'org.funderHSK.expl')}">
                                    <i class="question circle icon"></i>
                                </span>
                            </dt>
                            <dd>
                                <semui:xEditableRefData owner="${orgInstance}" field="funderHskType" config="${RDConstants.FUNDER_HSK_TYPE}"/>
                            </dd>
                        </dl>
                        <dl>
                            <dt>
                                <g:message code="address.country.label" />
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center"
                                      data-content="${message(code: 'org.country.expl')}">
                                    <i class="question circle icon"></i>
                                </span>
                            </dt>
                            <dd>
                                <semui:xEditableRefData id="country" owner="${orgInstance}" field="country" config="${RDConstants.COUNTRY}" />
                                &nbsp
                            </dd>
                            <dt>
                                <g:message code="org.region.label" />
                                <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center"
                                      data-content="${message(code: 'org.region.expl')}">
                                    <i class="question circle icon"></i>
                                </span>
                            </dt>
                            <dd>
                                <semui:xEditableRefData id="regions_${RDStore.COUNTRY_DE.id}" owner="${orgInstance}" field="region" config="${RDConstants.REGIONS_DE}"/>
                                <semui:xEditableRefData id="regions_${RDStore.COUNTRY_AT.id}" owner="${orgInstance}" field="region" config="${RDConstants.REGIONS_AT}"/>
                                <semui:xEditableRefData id="regions_${RDStore.COUNTRY_CH.id}" owner="${orgInstance}" field="region" config="${RDConstants.REGIONS_CH}"/>
                            </dd>
                        </dl>
                    </div>
                </div><!-- .card -->
            </g:if>


            <g:if test="${isProviderOrAgency}">
                <div class="ui card">
                    <div class="content">
                        <dl>
                            <dt><g:message code="org.platforms.label" /></dt>
                            <dd>

                                <div class="ui divided middle aligned selection list la-flex-list">
                                    <g:each in="${orgInstance.platforms.sort { it?.name }}" var="platform">
                                        <div class="ui item">
                                            <div class="content la-space-right">
                                                <strong><g:link controller="platform" action="show"
                                                                id="${platform.id}">${platform.name}</g:link>
                                                </strong>
                                            </div>
                                        </div>
                                    </g:each>
                                </div>
                            </dd>
                        </dl>
                    </div>
                </div>
            </g:if>


            <div class="ui card">
                <div class="content">
                    <g:if test="${!isProviderOrAgency}">
                        <h3><g:message code="org.contactpersons.and.addresses.label"/></h3>
                    </g:if>

                    <div class="ui la-float-right">
                        <g:if test="${(orgInstance.id == contextService.getOrg().id && user.hasAffiliation('INST_EDITOR'))}">
                            <g:link action="myPublicContacts" controller="organisation" params="[id: orgInstance.id, tab: 'contacts']"
                                    class="ui button">${message('code': 'org.edit.contactsAndAddresses')}</g:link>
                        </g:if>
                    </div>
                </div>

                <div class="description">
                    <dl>
                        <dt>
                            <dd>
                            <g:render template="publicContacts" model="[isProviderOrAgency: isProviderOrAgency]"/>

                            <g:if test="${isProviderOrAgency && (accessService.checkConstraint_ORG_COM_EDITOR())}">
                                <div class="ui list">

                                    <div class="item">

                                        <a href="#createPersonModal" class="ui button" size="35" data-semui="modal"
                                           onclick="personCreate('contactPersonForProviderAgencyPublic', ${orgInstance.id});"><g:message
                                                code="personFormModalTechnichalSupport"/></a>

                                    </div>
                                </div>
                            </g:if>
                            <g:if test="${!isProviderOrAgency}">
                                <div class="ui cards">

                                    <%
                                        Set<String> typeNames = new TreeSet<String>()
                                        typeNames.add(RDStore.ADRESS_TYPE_BILLING.getI10n('value'))
                                        typeNames.add(RDStore.ADRESS_TYPE_POSTAL.getI10n('value'))
                                        Map<String, List> typeAddressMap = [:]
                                        orgInstance.addresses.each {
                                            it.type.each { type ->
                                                String typeName = type.getI10n('value')
                                                typeNames.add(typeName)
                                                List addresses = typeAddressMap.get(typeName) ?: []
                                                addresses.add(it)
                                                typeAddressMap.put(typeName, addresses)
                                            }
                                        }
                                    %>
                                    <g:each in="${typeNames}" var="typeName">
                                        <div class="card">
                                            <div class="content">
                                                <div class="header">${typeName}</div>

                                                <div class="description">
                                                    <div class="ui divided middle aligned list la-flex-list">
                                                        <% List addresses = typeAddressMap.get(typeName) %>
                                                        <g:each in="${addresses}" var="a">
                                                            <g:if test="${a.org}">
                                                                <g:render template="/templates/cpa/address" model="${[
                                                                        hideAddressType     : true,
                                                                        address             : a,
                                                                        tmplShowDeleteButton: false,
                                                                        controller          : 'org',
                                                                        action              : 'show',
                                                                        id                  : orgInstance.id,
                                                                        editable            : false
                                                                ]}"/>
                                                            </g:if>
                                                        </g:each>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </g:each>
                                </div>
                            </g:if>
                            </dd>
                        </dt>
                    </dl>
                </div>
            </div><!-- .card -->

                <g:if test="${(contextService.getUser().isAdmin() || contextService.getOrg().getCustomerType()  == 'ORG_CONSORTIUM') && (contextService.getOrg() != orgInstance)}">
                    <g:if test="${orgInstance.createdBy || orgInstance.legallyObligedBy}">
                        <div class="ui card">
                            <div class="content">
                                <g:if test="${orgInstance.createdBy}">
                                    <dl>
                                        <dt>
                                            <g:message code="org.createdBy.label" />
                                        </dt>
                                        <dd>
                                            <h5 class="ui header">
                                                <g:link controller="organisation" action="show" id="${orgInstance.createdBy.id}">${orgInstance.createdBy.name}</g:link>
                                            </h5>
                                            <g:if test="${createdByOrgGeneralContacts}">
                                                <g:each in="${createdByOrgGeneralContacts}" var="cbogc">
                                                    <g:render template="/templates/cpa/person_full_details" model="${[
                                                            person              : cbogc,
                                                            personContext       : orgInstance.createdBy,
                                                            tmplShowFunctions       : true,
                                                            tmplShowPositions       : true,
                                                            tmplShowResponsiblities : true,
                                                            tmplConfigShow      : ['E-Mail', 'Mail', 'Url', 'Phone', 'Fax', 'address'],
                                                            editable            : false
                                                    ]}"/>
                                                </g:each>
                                            </g:if>
                                        </dd>
                                    </dl>
                                </g:if>
                                <g:if test="${orgInstance.legallyObligedBy}">
                                    <dl>
                                        <dt>
                                            <g:message code="org.legallyObligedBy.label" />
                                        </dt>
                                        <dd>
                                            <h5 class="ui header">
                                                <g:link controller="organisation" action="show" id="${orgInstance.legallyObligedBy.id}">${orgInstance.legallyObligedBy.name}</g:link>
                                            </h5>
                                            <g:if test="${legallyObligedByOrgGeneralContacts}">
                                                <g:each in="${legallyObligedByOrgGeneralContacts}" var="lobogc">
                                                    <g:render template="/templates/cpa/person_full_details" model="${[
                                                            person              : lobogc,
                                                            personContext       : orgInstance.legallyObligedBy,
                                                            tmplShowFunctions       : true,
                                                            tmplShowPositions       : true,
                                                            tmplShowResponsiblities : true,
                                                            tmplConfigShow      : ['E-Mail', 'Mail', 'Url', 'Phone', 'Fax', 'address'],
                                                            editable            : false
                                                    ]}"/>
                                                </g:each>
                                            </g:if>
                                        </dd>
                                    </dl>
                                </g:if>
                            </div>
                        </div><!-- .card -->
                    </g:if>
                </g:if>

            <g:if test="${accessService.checkPerm("ORG_INST,ORG_CONSORTIUM")}">
                <div id="new-dynamic-properties-block">
                    <g:render template="properties" model="${[
                            orgInstance   : orgInstance,
                            authorizedOrgs: authorizedOrgs,
                            contextOrg: institution
                    ]}"/>
                </div><!-- #new-dynamic-properties-block -->
            </g:if>

        </div>
    </div>
    <aside class="four wide column la-sidekick">
        <g:render template="/templates/aside1" model="${[ownobj: orgInstance, owntp: 'organisation']}"/>
    </aside>
</div>
</body>
</html>
<r:script>
    $('#country').on('save', function(e, params) {
        showRegionsdropdown(params.newValue);
    });


    function showRegionsdropdown(newValue) {
        $("*[id^=regions_]").hide();
        if(newValue){
            var id = newValue.split(':')[1]
            // $("#regions_" + id).editable('setValue', null);
            $("#regions_" + id).show();
        }
    };

    $(document).ready(function(){
        var country = $("#country").editable('getValue', true);
        showRegionsdropdown(country);
    });
</r:script>
<g:javascript>
        function addresscreate_org(orgId, typeId, redirect, hideType) {
            var url = '<g:createLink controller="ajaxHtml" action="createAddress"/>'+'?orgId='+orgId+'&typeId='+typeId+'&redirect='+redirect+'&hideType='+hideType;
            private_address_modal(url);
        }
        function addresscreate_prs(prsId, typeId, redirect, hideType) {
            var url = '<g:createLink controller="ajaxHtml" action="createAddress"/>'+'?prsId='+prsId+'&typeId='+typeId+'&redirect='+redirect+'&hideType='+hideType;
            private_address_modal(url);
        }

        function private_address_modal(url) {
            $.ajax({
                url: url,
                success: function(result){
                    $("#dynamicModalContainer").empty();
                    $("#addressFormModal").remove();

                    $("#dynamicModalContainer").html(result);
                    $("#dynamicModalContainer .ui.modal").modal({
                        onVisible: function () {
                            r2d2.initDynamicSemuiStuff('#addressFormModal');
                            r2d2.initDynamicXEditableStuff('#addressFormModal');

                            // ajaxPostFunc()
                        }
                    }).modal('show');
                }
            });
        }
</g:javascript>
<g:if test="${isProviderOrAgency}">
<g:javascript>
    function personCreate(contactFor, org) {
        var url = '<g:createLink controller="ajaxHtml"
                                 action="createPerson"/>?contactFor='+contactFor+'&org='+org+'&showAddresses=false&showContacts=true';
        createPersonModal(url)
    }
    function createPersonModal(url) {
        $.ajax({
            url: url,
            success: function(result){
                $("#dynamicModalContainer").empty();
                $("#personModal").remove();

                $("#dynamicModalContainer").html(result);
                $("#dynamicModalContainer .ui.modal").modal({
                    onVisible: function () {
                        r2d2.initDynamicSemuiStuff('#personModal');
                        r2d2.initDynamicXEditableStuff('#personModal');
                    }
                }).modal('show');
            }
        });
    }
</g:javascript>
</g:if>
