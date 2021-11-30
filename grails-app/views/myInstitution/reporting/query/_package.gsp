<%@page import="de.laser.reporting.report.ElasticSearchHelper; de.laser.reporting.report.myInstitution.base.BaseConfig;" %>
<laser:serviceInjection/>

<g:if test="${filterResult}">
    <g:render template="/myInstitution/reporting/query/generic_filterLabels" model="${[filterLabels: filterResult.labels]}" />

    <g:if test="${filterResult.data.packageIdList}">

        <div class="ui message ${filterResult.get(ElasticSearchHelper.ELASTIC_SEARCH_IS_NOT_REACHABLE) ? 'warning' : 'success'}">
            <p>
                <g:render template="/myInstitution/reporting/query/filterResult" model="${[filter: filter, filterResult: filterResult]}" />
            </p>
            <g:if test="${filterResult.get(ElasticSearchHelper.ELASTIC_SEARCH_IS_NOT_REACHABLE)}">
                <p><i class="icon large exclamation triangle"></i> ${message(code:'reporting.filterResult.elasticSearchIsNotReachable')}</p>

                <laser:script file="${this.getGroovyPageFileName()}">
                    $('#filter-package label').filter( function(){
                            return $(this).text().indexOf('we:kb')>0 }
                        ).each( function(){
                            $(this)
                                .css('opacity', '0.2').css('color', '#333')
                                .next('.dropdown').addClass('disabled').css('opacity', '0.2').css('color', '#333')
                                .find('select').attr('disabled', 'disabled');
                        })
                </laser:script>
            </g:if>
        </div>
        <g:render template="/myInstitution/reporting/query/form" model="${[cfgKey: "${BaseConfig.KEY_PACKAGE}"]}" />

    </g:if>
    <g:else>
        <div class="ui message negative">
            <p><g:message code="reporting.filter.no.matches" /></p>

            <g:if test="${filterResult.get(ElasticSearchHelper.ELASTIC_SEARCH_IS_NOT_REACHABLE)}">
                <p><i class="icon large exclamation circle"></i> ${message(code:'reporting.filterResult.elasticSearchIsNotReachable')}</p>
            </g:if>
        </div>
    </g:else>
</g:if>