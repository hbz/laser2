<%@ page import="de.laser.SurveyConfig; de.laser.RefdataCategory;de.laser.properties.PropertyDefinition;de.laser.RefdataValue; de.laser.helper.RDStore" %>
<laser:serviceInjection/>

<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code: 'laser')} : ${message(code: 'survey.label')}</title>
</head>

<body>

<semui:breadcrumbs>
    <semui:crumb controller="survey" action="currentSurveysConsortia" text="${message(code:'menu.my.surveys')}" />
    <g:if test="${surveyInfo}">
        <semui:crumb controller="survey" action="show" id="${surveyInfo.id}" params="[surveyConfigID: surveyConfig.id]" text="${surveyConfig.getConfigNameShort()}" />
    </g:if>
    <semui:crumb message="surveyEvaluation.label" class="active"/>
</semui:breadcrumbs>

<semui:controlButtons>
    <g:if test="${surveyInfo.status != RDStore.SURVEY_IN_PROCESSING}">
        <semui:exportDropdown>
            <semui:exportDropdownItem>
                <g:link class="item" action="surveyEvaluation" id="${surveyInfo.id}"
                        params="[surveyConfigID: surveyConfig.id, exportXLSX: true]">${message(code: 'survey.exportSurvey')}</g:link>
            </semui:exportDropdownItem>

            <g:if test="${surveyInfo.type.id in [RDStore.SURVEY_TYPE_RENEWAL.id, RDStore.SURVEY_TYPE_SUBSCRIPTION.id]}">
            <semui:exportDropdownItem>
                <g:link class="item" action="surveyEvaluation" id="${surveyInfo.id}"
                        params="[surveyConfigID: surveyConfig.id, exportXLSX: true, surveyCostItems: true]">${message(code: 'survey.exportSurveyCostItems')}</g:link>
            </semui:exportDropdownItem>
            </g:if>
        </semui:exportDropdown>
    </g:if>

    <g:render template="actions"/>
</semui:controlButtons>

<h1 class="ui icon header"><semui:headerTitleIcon type="Survey"/>
<semui:xEditable owner="${surveyInfo}" field="name"/>
<semui:surveyStatusWithRings object="${surveyInfo}" surveyConfig="${surveyConfig}" controller="survey" action="surveyEvaluation"/>
</h1>



<g:render template="nav"/>

<semui:objectStatus object="${surveyInfo}" status="${surveyInfo.status}"/>

<semui:messages data="${flash}"/>

<br>

<g:if test="${surveyInfo.status == RDStore.SURVEY_IN_PROCESSING}">
    <div class="ui segment">
        <strong>${message(code: 'surveyEvaluation.notOpenSurvey')}</strong>
    </div>
</g:if>
<g:else>
    <h2 class="ui icon header la-clear-before la-noMargin-top">
        <g:if test="${surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION}">
            <i class="icon clipboard outline la-list-icon"></i>
            <g:link controller="subscription" action="show" id="${surveyConfig.subscription?.id}">
                ${surveyConfig.subscription?.name}
            </g:link>
        </g:if>
        <g:else>
            ${surveyConfig.getConfigNameShort()}
        </g:else>: ${message(code: 'surveyEvaluation.label')}
    </h2>
    <br>

    <div class="ui grid">

        <div class="sixteen wide stretched column">
            <div class="ui top attached tabular menu">

                <g:link class="item ${params.tab == 'participantsViewAllFinish' ? 'active' : ''}"
                        controller="survey" action="surveyEvaluation"
                        params="[id: params.id, surveyConfigID: surveyConfig.id, tab: 'participantsViewAllFinish']">
                    ${message(code: 'surveyEvaluation.participantsViewAllFinish')}
                    <div class="ui floating circular label">${participantsFinishTotal}</div>
                </g:link>

                <g:link class="item ${params.tab == 'participantsViewAllNotFinish' ? 'active' : ''}"
                        controller="survey" action="surveyEvaluation"
                        params="[id: params.id, surveyConfigID: surveyConfig.id, tab: 'participantsViewAllNotFinish']">
                    ${message(code: 'surveyEvaluation.participantsViewAllNotFinish')}
                    <div class="ui floating circular label">${participantsNotFinishTotal}</div>
                </g:link>

                <g:link class="item ${params.tab == 'participantsView' ? 'active' : ''}"
                        controller="survey" action="surveyEvaluation"
                        params="[id: params.id, surveyConfigID: surveyConfig.id, tab: 'participantsView']">
                    ${message(code: 'surveyEvaluation.participantsView')}
                    <div class="ui floating circular label">${participantsTotal}</div>
                </g:link>

            </div>


            <g:render template="evaluationParticipantsView"/>

        </div>
    </div>

</g:else>

<r:script>
    $(document).ready(function () {
        $('.tabular.menu .item').tab()
    });
</r:script>

</body>
</html>
