<%@page import="de.laser.reporting.myInstitution.OrganisationConfig;de.laser.ReportingService;de.laser.Org;de.laser.Subscription" %>
<laser:serviceInjection/>

    <g:form action="reporting" method="POST" class="ui form">

        <div class="menu ui top attached tabular">
            <a class="active item" data-tab="org-filter-tab-1">Organisationen</a>
        </div><!-- .menu -->

        <div class="ui bottom attached active tab segment" data-tab="org-filter-tab-1">
            <div class="field">
                <label for="filter:org_source">Auswahl</label>
                <g:set var="config" value="${OrganisationConfig.CONFIG.base}" />
                <g:select name="filter:org_source" class="ui selection dropdown la-not-clearable" from="${config.source}" optionKey="key" optionValue="value" value="${params.get('filter:org_source')}" />
            </div>

            <div id="filter-wrapper-default">
                <g:each in="${config.filter.default}" var="cfgFilter">
                    <div class="fields <laser:numberToString number="${cfgFilter.size()}" min="2"/>">
                    <g:each in="${cfgFilter}" var="field">
                        <laser:reportFilterField config="${config}" field="${field}" />
                    </g:each>
                    </div>
                </g:each>
            </div>

            <div id="filter-wrapper-provider">
                <g:each in="${config.filter.provider}" var="cfgFilter">
                    <div class="fields <laser:numberToString number="${cfgFilter.size()}" min="2"/>">
                        <g:each in="${cfgFilter}" var="field">
                            <laser:reportFilterField config="${config}" field="${field}" />
                        </g:each>
                    </div>
                </g:each>
            </div>

        </div><!-- .first -->

        <div class="field">
            <g:link action="reporting" class="ui button primary">${message(code:'default.button.reset.label')}</g:link>
            <input type="submit" class="ui button secondary" value="${message(code:'default.button.search.label')}" />
            <input type="hidden" name="filter" value="${OrganisationConfig.KEY}" />
            <input type="hidden" name="token" value="${token}" />
        </div>

    </g:form>

<laser:script file="${this.getGroovyPageFileName()}">
    $('#filter\\:org_source').on( 'change', function(e) {

        var $fwDefault = $('#filter-wrapper-default')
        var $fwProvider = $('#filter-wrapper-provider')

        if (JSPC.helper.contains( ['all-provider', 'my-provider'], $(e.target).dropdown('get value') )) {
            $fwDefault.find('*').attr('disabled', 'disabled');
            $fwDefault.hide();

            $fwProvider.find('*').removeAttr('disabled');
            $fwProvider.show();
        }
        else {
            $fwProvider.find('*').attr('disabled', 'disabled');
            $fwProvider.hide();

            $fwDefault.find('*').removeAttr('disabled');
            $fwDefault.show();
        }
    })

    $('#filter\\:org_source').trigger('change');
</laser:script>

