<%@ page import="de.laser.SurveyConfig; de.laser.helper.RDStore; de.laser.properties.PropertyDefinition;de.laser.RefdataCategory;de.laser.RefdataValue;de.laser.Org" %>
<laser:serviceInjection/>
<!doctype html>



<html>
<head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code: 'laser')} : ${message(code: 'myinst.currentSubscriptions.label')}</title>
</head>

<body>

<semui:breadcrumbs>
    <semui:crumb controller="survey" action="currentSurveysConsortia" text="${message(code: 'menu.my.surveys')}"/>

    <g:if test="${surveyInfo}">
        <semui:crumb controller="survey" action="show" id="${surveyInfo.id}"
                     params="[surveyConfigID: surveyConfig.id]" text="${surveyInfo.name}"/>
    </g:if>
    <semui:crumb message="myinst.currentSubscriptions.label" class="active"/>
</semui:breadcrumbs>

<semui:controlButtons>
        <g:if test="${surveyInfo.status.id == RDStore.SURVEY_SURVEY_STARTED.id && surveyConfig.isResultsSetFinishByOrg(participant)}">
            <semui:actionsDropdown>
            <semui:actionsDropdownItem controller="survey" action="openSurveyAgainForParticipant" params="[surveyConfigID: surveyConfig.id, participant: participant.id]"
                                       message="openSurveyAgainForParticipant.button"/>
            </semui:actionsDropdown>
        </g:if>
</semui:controlButtons>

<h1 class="ui icon header"><semui:headerTitleIcon type="Survey"/>
<semui:xEditable owner="${surveyInfo}" field="name"/>
<semui:surveyStatus object="${surveyInfo}"/>
</h1>

<g:render template="nav"/>


<semui:messages data="${flash}"/>

<g:if test="${surveyConfig.isResultsSetFinishByOrg(participant)}">
    <div class="ui icon positive message">
        <i class="info icon"></i>
        <div class="content">
            <div class="header"></div>
            <p>
                <g:message code="surveyResult.finish.info.consortia"/>.
            </p>
        </div>
    </div>
</g:if>

<g:if test="${participant}">
    <g:set var="choosenOrg" value="${Org.findById(participant.id)}"/>
    <g:set var="choosenOrgCPAs" value="${choosenOrg?.getGeneralContactPersons(false)}"/>

    <table class="ui table la-table compact">
        <tbody>
        <tr>
            <td>
                <p><strong>${choosenOrg?.name} (${choosenOrg?.shortname})</strong></p>

                ${choosenOrg?.libraryType?.getI10n('value')}
            </td>
            <td>
                <g:if test="${choosenOrgCPAs}">
                    <g:set var="oldEditable" value="${editable}"/>
                    <g:set var="editable" value="${false}" scope="request"/>
                    <g:each in="${choosenOrgCPAs}" var="gcp">
                        <g:render template="/templates/cpa/person_details"
                                  model="${[person: gcp, tmplHideLinkToAddressbook: true]}"/>
                    </g:each>
                    <g:set var="editable" value="${oldEditable ?: false}" scope="request"/>
                </g:if>
            </td>
        </tr>
        </tbody>
    </table>
</g:if>

<div class="ui stackable grid">
    <div class="sixteen wide column">

        <div class="la-inline-lists">

            <g:if test="${surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION}">

                <g:render template="/templates/survey/subscriptionSurvey" model="[surveyConfig        : surveyConfig,
                                                                                  costItemSums        : costItemSums,
                                                                                  subscriptionInstance: subscriptionInstance,
                                                                                  visibleOrgRelations : visibleOrgRelations,
                                                                                  surveyResults       : surveyResults]"/>
            </g:if>

            <g:if test="${surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_GENERAL_SURVEY}">

                <g:render template="/templates/survey/generalSurvey" model="[surveyConfig        : surveyConfig,
                                                                             costItemSums        : costItemSums,
                                                                             subscriptionInstance: surveyConfig.subscription,
                                                                             tasks               : tasks,
                                                                             visibleOrgRelations : visibleOrgRelations]"/>
            </g:if>

        </div>
    </div>
</div>
</body>
</html>
