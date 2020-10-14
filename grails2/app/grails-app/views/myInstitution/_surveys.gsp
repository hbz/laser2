<%@ page import="de.laser.SurveyConfig; de.laser.OrgRole" %>

<h3><g:message code="surveys.active"/></h3>


<div class="ui divided items">
    <g:each in="${surveys}" var="survey" status="i">

        <g:set var="surveyConfig"
               value="${SurveyConfig.get(survey.key)}"/>

        <g:set var="surveyInfo"
               value="${surveyConfig.surveyInfo}"/>

        <div class="item">

            <div class="content">
                <div class="header">
                    <i class="icon chart pie la-list-icon"></i>
                    <g:if test="${accessService.checkPerm('ORG_CONSORTIUM')}">
                        <g:link controller="survey" action="show" params="[surveyConfigID: surveyConfig.id]"
                                id="${surveyInfo.id}">${surveyConfig.getSurveyName()}
                        </g:link>
                    </g:if>
                    <g:else>
                        <g:if test="${!surveyConfig.pickAndChoose}">
                        <g:link controller="myInstitution" action="surveyInfos" params="[surveyConfigID: surveyConfig.id]"
                                              id="${surveyInfo.id}">${surveyConfig.getSurveyName()}</g:link>
                        </g:if>
                        <g:if test="${surveyConfig.pickAndChoose}">
                            <g:link controller="myInstitution" action="surveyInfosIssueEntitlements" id="${surveyConfig.id}"
                                    params="${[targetObjectId: surveyConfig.subscription?.getDerivedSubscriptionBySubscribers(institution)?.id]}">
                                ${surveyConfig.getSurveyName()}
                            </g:link>
                        </g:if>
                    </g:else>

                    <div class="ui label left pointing survey-${surveyInfo.type.value}">
                        ${surveyInfo.type.getI10n('value')}
                    </div>

                    <g:if test="${surveyInfo.isMandatory}">
                        <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center"
                              data-content="${message(code: "surveyInfo.isMandatory.label.info2")}">
                            <i class="yellow icon exclamation triangle"></i>
                        </span>
                    </g:if>

                </div>

                <div class="meta">
                    <g:if test="${accessService.checkPerm('ORG_CONSORTIUM')}">
                        <g:link controller="survey" action="surveyParticipants" id="${surveyInfo.id}"
                                params="[surveyConfigID: surveyConfig.id]" class="ui icon"> ${message(code: 'surveyParticipants.label')}:
                            <div class="ui circular ${surveyConfig.configFinish ? "green" : ""} label">
                                ${surveyConfig.orgs?.size() ?: 0}
                            </div>
                        </g:link>

                        <div class="la-float-right">
                        <g:if test="${surveyConfig && surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_ISSUE_ENTITLEMENT && surveyConfig.pickAndChoose}">

                                <g:link controller="survey" action="surveyTitlesEvaluation" id="${surveyInfo.id}"
                                        params="[surveyConfigID: surveyConfig.id]"
                                        class="ui icon button">
                                    <i class="icon blue chart pie"></i>
                                </g:link>
                            </g:if>
                            <g:else>
                                <g:link controller="survey" action="surveyEvaluation" id="${surveyInfo.id}"
                                        params="[surveyConfigID: surveyConfig.id]"
                                        class="ui icon button">
                                    <i class="icon blue chart pie"></i>
                                </g:link>
                            </g:else>
                        </div>

                    </g:if>
                    <g:else>
                        <strong><g:message code="surveyInfo.owner.label"/>:</strong> ${surveyInfo.owner}
                    </g:else>
                </div>

                <div class="description">
                    <p>
                        <g:if test="${surveyInfo.startDate}">
                            <strong><g:message code="surveyInfo.startDate.label"/>:</strong> <g:formatDate
                                date="${surveyInfo.startDate}" formatName="default.date.format.notime"/>
                        </g:if>

                        <g:if test="${surveyInfo.endDate}">
                            <strong><g:message code="surveyInfo.endDate.label"/>:</strong> <g:formatDate
                                date="${surveyInfo.endDate}" formatName="default.date.format.notime"/>
                        </g:if>

                    </p>

                    <p>
                        <g:if test="${surveyInfo.comment}">
                            <strong><g:message code="surveyInfo.comment.label"/>:</strong> ${surveyInfo.comment}
                        </g:if>
                    </p>
                </div>

            </div>
        </div>

    </g:each>

</div>
