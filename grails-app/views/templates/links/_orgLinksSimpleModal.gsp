<%@ page import="de.laser.Org" %>
<g:if test="${editmode}">
    <a role="button" class="ui button" data-semui="modal" href="#${tmplModalID}">${tmplButtonText}</a>
</g:if>

<semui:modal id="${tmplModalID}" text="${tmplText}" isEditModal="${editmode}">
    <g:form id="create_org_role_link_${tmplModalID}" class="ui form" url="[controller:'ajax', action:'addOrgRole']" method="post">
        <input type="hidden" name="parent" value="${parent}" />
        <input type="hidden" name="property" value="${property}" />
        <input type="hidden" name="recip_prop" value="${recip_prop}" />
        <input type="hidden" name="orm_orgRole" value="${tmplRole?.id}" />
        <input type="hidden" name="linkType" value="${linkType}" />

        <div class="field">
            <g:if test="${orgList.size() > 0}">
                <p>
                    <g:message code="template.orgLinksModal.found" args="${[orgList.size(),tmplEntity]}"/>
                    <%--
                    <br />
                    Bereits von Ihnen verwendete ${tmplEntity} sind durch ein Symbol (&#10004;) gekennzeichnet.
                    --%>
                </p>
                <g:set var="varSelectOne" value="${message(code:'default.selectOne.label')}" />

                <semui:signedDropdown id="orm_orgOid_${tmplModalID}" name="orm_orgOid" noSelection="${varSelectOne}" from="${orgList}" signedIds="${signedIdList}" />
            </g:if>
            <g:else>
                <p>
                    <g:message code="template.orgLinksModal.noEntityFound" args="${[tmplEntity]}"/>
                </p>
            </g:else>
        </div>
    </g:form>
</semui:modal>

