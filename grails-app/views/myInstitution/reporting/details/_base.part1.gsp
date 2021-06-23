<%@ page import="grails.plugin.springsecurity.SpringSecurityUtils" %>
<h3 class="ui header">${message(code:'reporting.macro.step3')}</h3>

<div class="ui right aligned">
    <button id="details-export-button" class="ui icon button" href="#detailsExportModal" data-semui="modal">
        <i class="ui icon download"></i>
    </button>

    <g:if test="${query.split('-')[0] in ['org', 'member', 'consortium', 'provider', 'licensor']}">
        <button id="details-copy-email-button" class="ui icon button" href="#detailsCopyEmailModal" data-semui="modal">
            <i class="icon envelope"></i>
        </button>
    </g:if>
</div>

<g:render template="/myInstitution/reporting/details/generic_queryLabels" model="${[queryLabels: labels]}" />
