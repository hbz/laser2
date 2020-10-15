<%@ page import="de.laser.RefdataValue; de.laser.RefdataCategory;" %>
<laser:serviceInjection/>

<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code: 'laser')} : ${message(code: 'createIssueEntitlementsSurvey.label')}</title>
</head>

<body>

<semui:breadcrumbs>

    <semui:crumb controller="survey" action="currentSurveysConsortia" message="currentSurveys.label"/>
    <semui:crumb message="createIssueEntitlementsSurvey.label" class="active"/>
</semui:breadcrumbs>
<br />
<h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerTitleIcon type="Survey"/>${message(code: 'createIssueEntitlementsSurvey.label')}</h1>

<semui:messages data="${flash}"/>

<semui:form>
    <g:form action="processCreateIssueEntitlementsSurvey" controller="survey" method="post" class="ui form">
        <g:hiddenField name="sub" value="${subscription?.id}"/>

        <div class="field required ">
            <label>${message(code: 'surveyInfo.name.label')}</label>
            <input type="text" name="name" placeholder="" value="${subscription?.name ?: params.name}" required />
        </div>

        <div class="two fields ">
            <semui:datepicker label="surveyInfo.startDate.label" id="startDate" name="startDate"
                              value="${params.startDate}" required="" />

            <semui:datepicker label="surveyInfo.endDate.label" id="endDate" name="endDate"
                              value="${params.endDate}" />
        </div>

        <div class="field">
            <label>${message(code: 'surveyconfig.createTitleGroups.label.info')}</label>
            <div>
                <input type="checkbox" id="createTitleGroups" name="createTitleGroups" ${params.createTitleGroups? 'checked':''}>
            </div>
        </div>

        <div class="field">
            <label>${message(code: 'surveyInfo.isMandatory.label.info')}</label>
            <div>
                <input type="checkbox" id="mandatory" name="mandatory" ${params.mandatory? 'checked':''}>
            </div>
        </div>

        <div class="field ">
            <label>${message(code: 'surveyInfo.comment.label')}</label>

            <textarea name="comment">${params.comment}</textarea>
        </div>

        <input type="submit" class="ui button"
               value="${message(code: 'createIssueEntitlementsSurvey.create')}"/>

    </g:form>
</semui:form>

</body>
</html>
