<%@page import="de.laser.reporting.myInstitution.base.BaseConfig;" %>
<laser:serviceInjection/>

<g:if test="${filterResult}">
    <g:render template="/myInstitution/reporting/query/generic_filterLabels" model="${[filterLabels: filterResult.labels]}" />

    <g:if test="${filterResult.data.costItemIdList}">

        <div class="ui message success">
            <p>
                ${message(code: 'reporting.filterResult.costItem', args: [filterResult.data.costItemIdList.size()])}
            </p>
        </div>

        <g:render template="/myInstitution/reporting/query/base.part1" />

        <laser:script file="${this.getGroovyPageFileName()}">
            JSPC.app.reporting.current.request = {
                context: '${BaseConfig.KEY_MYINST}',
                filter: '${BaseConfig.KEY_COSTITEM}',
                token: '${token}'
            }
        </laser:script>

    </g:if>
    <g:else>
        <div class="ui message negative">
            <p><g:message code="reporting.filter.no.matches" /></p>
        </div>
    </g:else>
</g:if>