<%@ page import="de.laser.IssueEntitlementCoverage" %>
<g:set var="overwriteEditable" value="${(overwriteEditable == null) ? editable : overwriteEditable}" />
<%
    Map<String, Object> paramData = [ieCoverage: covStmt.id]
    if(params.sort && params.order) {
        paramData.sort = params.sort
        paramData.order = params.order
    }
    if(params.max && params.offset) {
        paramData.max = params.max
        paramData.offset = params.offset
    }
%>
<div class="content">
    <div class="la-card-column">
        <i class="grey fitted calendar alternate icon la-popup-tooltip la-delay" data-content="${message(code: 'tipp.startDate.tooltip')}"></i>
        <semui:xEditable owner="${covStmt}" type="date" field="startDate" overwriteEditable="${overwriteEditable}"/><br />
        <i class="grey fitted la-books icon la-popup-tooltip la-delay" data-content="${message(code: 'tipp.startVolume.tooltip')}"></i>
        <semui:xEditable owner="${covStmt}" field="startVolume" overwriteEditable="${overwriteEditable}"/><br />

        <i class="grey fitted la-notebook icon la-popup-tooltip la-delay" data-content="${message(code: 'tipp.startIssue.tooltip')}"></i>
        <semui:xEditable owner="${covStmt}" field="startIssue" overwriteEditable="${overwriteEditable}"/>
        <semui:dateDevider/>
        <!-- bis -->
        <i class="grey fitted calendar alternate icon la-popup-tooltip la-delay" data-content="${message(code: 'tipp.endDate.tooltip')}"></i>
        <semui:xEditable owner="${covStmt}" type="date" field="endDate" overwriteEditable="${overwriteEditable}"/><br />
        <i class="grey fitted la-books icon la-popup-tooltip la-delay" data-content="${message(code: 'tipp.endVolume.tooltip')}"></i>
        <semui:xEditable owner="${covStmt}" field="endVolume" overwriteEditable="${overwriteEditable}"/><br />

        <i class="grey fitted la-notebook icon la-popup-tooltip la-delay" data-content="${message(code: 'tipp.endIssue.tooltip')}"></i>
        <semui:xEditable owner="${covStmt}" field="endIssue" overwriteEditable="${overwriteEditable}"/><br />
    </div>
    <div class="la-card-column-with-row">
        <div class="la-card-row">
            <i class="grey icon quote right la-popup-tooltip la-delay" data-content="${message(code: 'tipp.coverageNote')}"></i>
            <semui:xEditable owner="${covStmt}" field="coverageNote" overwriteEditable="${overwriteEditable}"/><br />
            <i class="grey icon file alternate right la-popup-tooltip la-delay" data-content="${message(code: 'tipp.coverageDepth')}"></i>
            <semui:xEditable owner="${covStmt}" field="coverageDepth" overwriteEditable="${overwriteEditable}"/><br />
            <i class="grey icon hand paper right la-popup-tooltip la-delay" data-content="${message(code: 'tipp.embargo')}"></i>
            <semui:xEditable owner="${covStmt}" field="embargo" overwriteEditable="${overwriteEditable}"/><br />
        </div>
        <div class="la-card-row">
            <g:if test="${overwriteEditable && (covStmt instanceof IssueEntitlementCoverage)}">
                <span class="right floated" >
                    <g:link controller="subscription" action="removeCoverage" params="${paramData+ [id: subscription.id]}" class="ui compact icon button negative tiny removeCoverage"><i class="ui icon minus" data-content="Abdeckung entfernen"></i></g:link>
                </span>
            </g:if>
        </div>
    </div>
</div>

