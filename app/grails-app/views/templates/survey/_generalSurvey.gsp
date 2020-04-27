<%@ page import="de.laser.helper.RDStore" %>
<div class="ui stackable grid">
    <div class="twelve wide column">
        <g:if test="${controllerName == 'survey' && actionName == 'show'}">
            <g:set var="countParticipants" value="${surveyConfig.countParticipants()}"/>
            <div class="ui horizontal segments">

                <div class="ui segment left aligned">
                    <b>${message(code: 'surveyConfig.orgs.label')}:</b>
                    <g:link controller="survey" action="surveyParticipants"
                            id="${surveyConfig.surveyInfo.id}"
                            params="[surveyConfigID: surveyConfig?.id]">
                        <div class="ui circular label">${countParticipants.surveyMembers}</div>
                    </g:link>
                </div>
            </div>
        </g:if>

        <div class="ui card ">
            <div class="content">
                <g:if test="${contextOrg?.id == surveyConfig.surveyInfo.owner.id && controllerName == 'survey' && actionName == 'show'}">
                    <dl>
                        <dt class="control-label">
                            <div class="ui icon la-popup-tooltip la-delay"
                                 data-content="${message(code: "surveyConfig.internalComment.comment")}">
                                ${message(code: 'surveyConfig.internalComment.label')}
                                <i class="question small circular inverted icon"></i>
                            </div>
                        </dt>
                        <dd><semui:xEditable owner="${surveyConfig}" field="internalComment" type="textarea"/></dd>

                    </dl>

                    <dl>
                        <dt class="control-label">
                            ${message(code: 'surveyConfig.url.label')}
                        </dt>
                        <dd><semui:xEditable owner="${surveyConfig}" field="url" type="url"/>
                        <g:if test="${surveyConfig.url}">
                            <semui:linkIcon href="${surveyConfig.url}"/>
                        </g:if>
                            <br/>&nbsp<br/>&nbsp<br/>
                        </dd>

                    </dl>

                    <br>

                    <div class="ui form">
                        <g:form action="setSurveyConfigComment" controller="survey" method="post"
                                params="[surveyConfigID: surveyConfig?.id, id: surveyInfo?.id]">
                            <div class="field">
                                <label><div class="ui icon la-popup-tooltip la-delay"
                                            data-content="${message(code: "surveyConfig.comment.comment")}">
                                    ${message(code: 'surveyConfig.comment.label')}
                                    <i class="question small circular inverted icon"></i>
                                </div></label>
                                <textarea name="comment" rows="15">${surveyConfig?.comment}</textarea>
                            </div>

                            <div class="left aligned">
                                <button type="submit"
                                        class="ui button">${message(code: 'default.button.save_changes')}</button>
                            </div>
                        </g:form>
                    </div>

                </g:if>
                <g:else>
                    <g:if test="${surveyConfig.url}">
                        <dl>
                            <dt class="control-label">
                                ${message(code: 'surveyConfig.url.label')}
                            </dt>
                            <dd><semui:xEditable owner="${surveyConfig}" field="url" type="url"
                                                 overwriteEditable="${false}"/>

                                <semui:linkIcon href="${surveyConfig.url}"/>

                                <br/>&nbsp<br/>&nbsp<br/>
                            </dd>

                        </dl>
                    </g:if>

                    <div class="ui form">
                        <div class="field">
                            <label>
                                <g:message code="surveyConfigsInfo.comment"/>
                            </label>
                            <g:if test="${surveyConfig?.comment}">
                                <textarea readonly="readonly" rows="15">${surveyConfig?.comment}</textarea>
                            </g:if>
                            <g:else>
                                <g:message code="surveyConfigsInfo.comment.noComment"/>
                            </g:else>
                        </div>
                    </div>
                </g:else>
            </div>
        </div>
    </div>

    <aside class="four wide column la-sidekick">
        <g:if test="${controllerName == 'survey' && actionName == 'show'}">

            <g:render template="/templates/tasks/card"
                      model="${[ownobj: surveyConfig, owntp: 'surveyConfig', css_class: '']}"/>


            <div id="container-notes">
                <g:render template="/templates/notes/card"
                          model="${[ownobj: surveyConfig, owntp: 'surveyConfig', css_class: '', editable: accessService.checkPermAffiliation('ORG_CONSORTIUM_SURVEY', 'INST_EDITOR')]}"/>
            </div>

            <g:if test="${accessService.checkPermAffiliation('ORG_CONSORTIUM_SURVEY', 'INST_EDITOR')}">

                <g:render template="/templates/tasks/modal_create"
                          model="${[ownobj: surveyConfig, owntp: 'surveyConfig']}"/>

            </g:if>
            <g:if test="${accessService.checkPermAffiliation('ORG_CONSORTIUM_SURVEY', 'INST_EDITOR')}">
                <g:render template="/templates/notes/modal_create"
                          model="${[ownobj: surveyConfig, owntp: 'surveyConfig']}"/>
            </g:if>
        </g:if>

        <div id="container-documents">
            <g:render template="/survey/cardDocuments"
                      model="${[ownobj: surveyConfig, owntp: 'surveyConfig', css_class: '']}"/>
        </div>
    </aside><!-- .four -->

</div><!-- .grid -->

<g:if test="${contextOrg?.id == surveyConfig.surveyInfo.owner.id}">
    <g:set var="surveyProperties" value="${surveyConfig.surveyProperties}"/>

    <semui:form>

        <h4 class="ui icon header la-clear-before la-noMargin-top">${message(code: 'surveyProperty.selected.label')} <semui:totalNumber
                total="${surveyProperties.size()}"/></h4>

        <table class="ui celled sortable table la-table">
            <thead>
            <tr>
                <th class="center aligned">${message(code: 'sidewide.number')}</th>
                <th>${message(code: 'surveyProperty.name')}</th>
                <th>${message(code: 'surveyProperty.expl.label')}</th>
                <th>${message(code: 'default.type.label')}</th>
                <th></th>
            </tr>
            </thead>

            <tbody>
            <g:each in="${surveyProperties.sort { it.surveyProperty?.name }}" var="surveyProperty" status="i">
                <tr>
                    <td class="center aligned">
                        ${i + 1}
                    </td>
                    <td>
                        ${surveyProperty?.surveyProperty?.getI10n('name')}

                        <g:if test="${surveyProperty?.surveyProperty?.tenant?.id == contextService.getOrg().id}">
                            <i class='shield alternate icon'></i>
                        </g:if>

                        <g:if test="${surveyProperty?.surveyProperty?.getI10n('expl')}">
                            <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center"
                                  data-content="${surveyProperty?.surveyProperty?.getI10n('expl')}">
                                <i class="question circle icon"></i>
                            </span>
                        </g:if>

                    </td>

                    <td>
                        <g:if test="${surveyProperty?.surveyProperty?.getI10n('expl')}">
                            ${surveyProperty?.surveyProperty?.getI10n('expl')}
                        </g:if>
                    </td>
                    <td>

                        ${com.k_int.properties.PropertyDefinition.getLocalizedValue(surveyProperty?.surveyProperty.type)}
                        <g:if test="${surveyProperty?.surveyProperty?.type == 'class com.k_int.kbplus.RefdataValue'}">
                            <g:set var="refdataValues" value="${[]}"/>
                            <g:each in="${com.k_int.kbplus.RefdataCategory.getAllRefdataValues(surveyProperty?.surveyProperty.refdataCategory)}"
                                    var="refdataValue">
                                <g:set var="refdataValues"
                                       value="${refdataValues + refdataValue?.getI10n('value')}"/>
                            </g:each>
                            <br>
                            (${refdataValues.join('/')})
                        </g:if>

                    </td>
                    <td>
                        <g:if test="${editable && surveyInfo.status == de.laser.helper.RDStore.SURVEY_IN_PROCESSING &&
                                com.k_int.kbplus.SurveyConfigProperties.findBySurveyConfigAndSurveyProperty(surveyConfig, surveyProperty?.surveyProperty)
                                && (de.laser.helper.RDStore.SURVEY_PROPERTY_PARTICIPATION.id != surveyProperty?.surveyProperty?.id)}">
                            <g:link class="ui icon negative button"
                                    controller="survey" action="deleteSurveyPropFromConfig"
                                    id="${surveyProperty?.id}">
                                <i class="trash alternate icon"></i>
                            </g:link>
                        </g:if>
                    </td>
                </tr>
            </g:each>
            </tbody>
            <tfoot>
            <tr>
                <g:if test="${editable && properties && surveyInfo.status == de.laser.helper.RDStore.SURVEY_IN_PROCESSING}">
                    <td colspan="6">
                        <g:form action="addSurveyPropToConfig" controller="survey" method="post" class="ui form">
                            <g:hiddenField name="id" value="${surveyInfo?.id}"/>
                            <g:hiddenField name="surveyConfigID" value="${surveyConfig?.id}"/>

                            <div class="field required">
                                <label>${message(code: 'surveyConfigs.property')}</label>
                                <semui:dropdown name="selectedProperty"

                                                class="la-filterPropDef"
                                                from="${properties}"
                                                iconWhich="shield alternate"
                                                optionKey="${{ "${it.id}" }}"
                                                optionValue="${{ it.getI10n('name') }}"
                                                noSelection="${message(code: 'default.search_for.label', args: [message(code: 'surveyProperty.label')])}"
                                                required=""/>

                            </div>
                            <input type="submit" class="ui button"
                                   value="${message(code: 'surveyConfigsInfo.add.button')}"/>

                        </g:form>
                    </td>
                </g:if>
            </tr>
            </tfoot>

        </table>

    </semui:form>
</g:if>

<g:if test="${surveyResults}">
    <semui:form>
        <h3><g:message code="surveyConfigsInfo.properties"/>
        <semui:totalNumber
                total="${surveyResults?.size()}"/>
        </h3>

        <table class="ui celled sortable table la-table">
            <thead>
            <tr>
                <th class="center aligned">${message(code: 'sidewide.number')}</th>
                <th>${message(code: 'surveyProperty.label')}</th>
                <th>${message(code: 'default.type.label')}</th>
                <th>${message(code: 'surveyResult.result')}</th>
                <th>
                    <g:if test="${accessService.checkPermAffiliation('ORG_CONSORTIUM_SURVEY', 'INST_EDITOR')}">
                        ${message(code: 'surveyResult.participantComment')}
                    </g:if>
                    <g:else>
                        ${message(code: 'surveyResult.commentParticipant')}
                        <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center"
                              data-content="${message(code: 'surveyResult.commentParticipant.info')}">
                            <i class="question circle icon"></i>
                        </span>
                    </g:else>
                </th>
                <th>
                    <g:if test="${accessService.checkPermAffiliation('ORG_CONSORTIUM_SURVEY', 'INST_EDITOR')}">
                        ${message(code: 'surveyResult.commentOnlyForOwner')}
                        <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center"
                              data-content="${message(code: 'surveyResult.commentOnlyForOwner.info')}">
                            <i class="question circle icon"></i>
                        </span>
                    </g:if>
                    <g:else>
                        ${message(code: 'surveyResult.commentOnlyForParticipant')}
                        <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="right center"
                              data-content="${message(code: 'surveyResult.commentOnlyForParticipant.info')}">
                            <i class="question circle icon"></i>
                        </span>
                    </g:else>
                </th>
            </tr>
            </thead>
            <g:each in="${surveyResults.sort { it.type.getI10n('name') }}" var="surveyResult" status="i">

                <tr>
                    <td class="center aligned">
                        ${i + 1}
                    </td>
                    <td>
                        ${surveyResult?.type?.getI10n('name')}

                        <g:if test="${surveyResult?.type?.getI10n('expl')}">
                            <span class="la-long-tooltip la-popup-tooltip la-delay" data-position="bottom center"
                                  data-content="${surveyResult?.type?.getI10n('expl')}">
                                <i class="question circle icon"></i>
                            </span>
                        </g:if>

                    </td>
                    <td>
                        ${com.k_int.properties.PropertyDefinition.getLocalizedValue(surveyResult?.type.type)}
                        <g:if test="${surveyResult?.type.type == 'class com.k_int.kbplus.RefdataValue'}">
                            <g:set var="refdataValues" value="${[]}"/>
                            <g:each in="${com.k_int.kbplus.RefdataCategory.getAllRefdataValues(surveyResult?.type.refdataCategory)}"
                                    var="refdataValue">
                                <g:set var="refdataValues"
                                       value="${refdataValues + refdataValue?.getI10n('value')}"/>
                            </g:each>
                            <br>
                            (${refdataValues.join('/')})
                        </g:if>
                    </td>
                    <td>
                        <g:if test="${surveyResult?.type?.type == Integer.toString()}">
                            <semui:xEditable owner="${surveyResult}" type="text" field="intValue"/>
                        </g:if>
                        <g:elseif test="${surveyResult?.type?.type == String.toString()}">
                            <semui:xEditable owner="${surveyResult}" type="text" field="stringValue"/>
                        </g:elseif>
                        <g:elseif test="${surveyResult?.type?.type == BigDecimal.toString()}">
                            <semui:xEditable owner="${surveyResult}" type="text" field="decValue"/>
                        </g:elseif>
                        <g:elseif test="${surveyResult?.type?.type == Date.toString()}">
                            <semui:xEditable owner="${surveyResult}" type="date" field="dateValue"/>
                        </g:elseif>
                        <g:elseif test="${surveyResult?.type?.type == URL.toString()}">
                            <semui:xEditable owner="${surveyResult}" type="url" field="urlValue"
                                             overwriteEditable="${overwriteEditable}"
                                             class="la-overflow la-ellipsis"/>
                            <g:if test="${surveyResult?.urlValue}">
                                <semui:linkIcon/>
                            </g:if>
                        </g:elseif>
                        <g:elseif test="${surveyResult?.type?.type == com.k_int.kbplus.RefdataValue.toString()}">

                            <g:if test="${surveyResult?.type?.name in ["Participation"] && surveyResult?.owner?.id != contextService.getOrg().id}">
                                <semui:xEditableRefData owner="${surveyResult}" field="refValue" type="text"
                                                        id="participation"
                                                        config="${surveyResult.type?.refdataCategory}"/>
                            </g:if>
                            <g:else>
                                <semui:xEditableRefData owner="${surveyResult}" type="text" field="refValue"
                                                        config="${surveyResult.type?.refdataCategory}"/>
                            </g:else>
                        </g:elseif>
                    </td>
                    <td>
                        <semui:xEditable owner="${surveyResult}" type="textarea" field="comment"/>
                    </td>
                    <td>
                        <g:if test="${accessService.checkPermAffiliation('ORG_CONSORTIUM_SURVEY', 'INST_EDITOR')}">
                            <semui:xEditable owner="${surveyResult}" type="textarea" field="ownerComment"/>
                        </g:if>
                        <g:else>
                            <semui:xEditable owner="${surveyResult}" type="textarea" field="participantComment"/>
                        </g:else>
                    </td>
                </tr>
            </g:each>
        </table>
    </semui:form>
</g:if>
