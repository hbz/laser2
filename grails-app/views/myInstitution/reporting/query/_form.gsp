<%@ page import="de.laser.reporting.report.myInstitution.base.BaseConfig" %>
<g:set var="esData" value="${BaseConfig.getCurrentEsData(cfgKey) ?: []}" />

<div class="ui segment form">
    <div class="fields <laser:numberToString number="${cfgQueryList.size()}" min="2"/>">
        <g:each in="${cfgQueryList}" var="cfgQuery" status="qci">
            <g:each in="${cfgQuery}" var="field">
                <div class="field">
                    <label for="query-chooser-1-${qci}">${BaseConfig.getMessage(field.key)}</label>
                    <semui:dropdownWithI18nExplanations name="query-chooser"
                                                        id="query-chooser-1-${qci}"
                                                        from="${field.value}"
                                                        optionKey="${{it}}"
                                                        optionValue="${{BaseConfig.getMessage(cfgKey + '.query.' + it)}}"
                                                        optionExpl="${{esData.contains(it) ? '(we:kb)' : ''}}"
                                                        class="ui selection dropdown la-not-clearable"
                                                        noSelection="${message(code: 'default.select.choose.label')}" />
                </div>
            </g:each>
        </g:each>
    </div>

    <div class="fields <laser:numberToString number="${2 + cfgQuery2List.size()}"/>">
        <g:each in="${cfgQuery2List}" var="cfgQuery" status="qci">
            <g:each in="${cfgQuery}" var="field">
                <div class="field">
                    <label for="query-chooser-2-${qci}">${BaseConfig.getMessage(field.key)}</label>
                         <semui:dropdownWithI18nExplanations name="query-chooser"
                                                             id="query-chooser-2-${qci}"
                                                             from="${field.value}"
                                                             optionKey="${{it.key}}"
                                                             optionValue="${{BaseConfig.getMessage(cfgKey + '.dist.' + it.key)}}"
                                                             optionExpl="${{esData.contains(it.key) ? '(we:kb)' : ''}}"
                                                             class="ui selection dropdown la-not-clearable"
                                                             noSelection="${message(code: 'default.select.choose.label')}" />
                </div>
            </g:each>
        </g:each>

        <div class="field">
            <label for="query-chooser">${message(code: 'reporting.query.chart')}</label>
            <g:select name="chart-chooser"
                      from="${cfgChartsList}"
                      optionKey="${{it}}"
                      optionValue="${{BaseConfig.getMessage('base.chart.' + it)}}"
                      class="ui selection dropdown la-not-clearable"
                      noSelection="${['': message(code: 'default.select.choose.label')]}" />
        </div>

        <div class="field" style="text-align: right">
            <label for="query-export-button">&nbsp;</label>
            <button id="query-export-button" class="ui icon button la-modern-button" data-href="#queryExportModal" data-semui="modal" disabled><i class="ui icon download"></i></button>
            <button id="query-help-button" class="ui icon button la-modern-button" data-href="#queryHelpModal" data-semui="modal" disabled><i class="ui icon question"></i></button>
        </div>
    </div>
</div>

<g:render template="/myInstitution/reporting/export/queryModal" model="[modalID: 'queryExportModal', token: token]" />
<g:render template="/myInstitution/reporting/query/helpModal" model="[modalID: 'queryHelpModal', token: token]" />

<laser:script file="${this.getGroovyPageFileName()}">
    JSPC.app.reporting.current.request = {
        context: '${BaseConfig.KEY_MYINST}',
        filter: '${cfgKey}',
        token: '${token}'
    }
    $('#filter-package input.button[type=submit]').on('click', function() {
        $('#loadingIndicator').show();
    })
</laser:script>

