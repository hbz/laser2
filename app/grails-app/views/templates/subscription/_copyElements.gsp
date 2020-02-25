<%@ page import="com.k_int.kbplus.IssueEntitlement; com.k_int.kbplus.SubscriptionController; de.laser.helper.RDStore; com.k_int.kbplus.Person; com.k_int.kbplus.Subscription; com.k_int.kbplus.GenericOIDService "%>
<%@ page import="com.k_int.kbplus.SubscriptionController" %>
<laser:serviceInjection />

<semui:form>

    <g:if test="${controllerName != 'survey' && !isRenewSub}">
        <g:render template="selectSourceAndTargetSubscription" model="[
                sourceSubscription: sourceSubscription,
                targetSubscription: targetSubscription,
                allSubscriptions_readRights: allSubscriptions_readRights,
                allSubscriptions_writeRights: allSubscriptions_writeRights]"/>
    </g:if>

    <g:form action="${actionName}" controller="${controllerName}" id="${params.id}"
            params="[workFlowPart: workFlowPart, sourceSubscriptionId: sourceSubscriptionId, targetSubscriptionId: targetSubscription?.id, isRenewSub: isRenewSub]"
            method="post" class="ui form newLicence">
        <table class="ui celled table table-tworow la-table">
            <thead>
                <tr>
                    <th class="six wide">
                        <g:if test="${sourceSubscription}"><g:link controller="subscription" action="show" id="${sourceSubscription?.id}">${sourceSubscription?.dropdownNamingConvention()}</g:link></g:if>
                    </th>
                    <g:if test="${isConsortialSubs}">
                                %{--th SHARE--}%
                                <th class="center aligned">
                                    <g:message code="subscription.details.copyElementsIntoSubscription.share"/>
                                </th>
                    </g:if>
                    <th class="one wide center aligned"><input type="checkbox" name="checkAllCopyCheckboxes" data-action="copy" onClick="toggleAllCheckboxes(this)" checked />
                    <th class="six wide">
                        <g:if test="${targetSubscription}"><g:link controller="subscription" action="show" id="${targetSubscription?.id}">${targetSubscription?.dropdownNamingConvention()}</g:link></g:if>
                    </th>
                    <th class="one wide center aligned">
                        <g:if test="${targetSubscription}">
                            <input type="checkbox" data-action="delete" onClick="toggleAllCheckboxes(this)" />
                        </g:if>
                    </th>
                </tr>
            </thead>
            <tbody>
            <g:if test="${ ! isRenewSub}">
                <tr>
                    <td  name="subscription.takeDates.source">
                        <div>
                            <b><i class="calendar alternate outline icon"></i>${message(code: 'subscription.periodOfValidity.label')}:</b>&nbsp
                            <g:formatDate date="${sourceSubscription?.startDate}" format="${message(code: 'default.date.format.notime')}"/>
                            ${sourceSubscription?.endDate ? (' - ' + formatDate(date: sourceSubscription?.endDate, format: message(code: 'default.date.format.notime'))) : ''}
                        </div>
                    </td>
                    <g:if test="${isConsortialSubs}">
                        %{--SHARE--}%
                        <td class="center aligned">
                        </td>
                    </g:if>
                    %{--AKTIONEN:--}%
                    <td class="center aligned">
                        <g:if test="${sourceSubscription?.startDate || sourceSubscription?.endDate}">
                            <div class="ui checkbox la-toggle-radio la-replace">
                                <g:checkBox name="subscription.takeDates" data-action="copy" checked="${true}" />
                            </div>
                        </g:if>
                    </td>

                    <td  name="subscription.takeDates.target">
                        <div>
                            <b><i class="calendar alternate outline icon"></i>${message(code: 'subscription.periodOfValidity.label')}:</b>&nbsp
                            <g:formatDate date="${targetSubscription?.startDate}" format="${message(code: 'default.date.format.notime')}"/>
                            ${targetSubscription?.endDate ? (' - ' + formatDate(date: targetSubscription?.endDate, format: message(code: 'default.date.format.notime'))) : ''}
                        </div>
                    </td>

                    <td>
                        <g:if test="${targetSubscription?.startDate || targetSubscription?.endDate}">
                            <div class="ui checkbox la-toggle-radio la-noChange">
                                <g:checkBox name="subscription.deleteDates" data-action="delete" />
                            </div>
                        </g:if>
                    </td>
                </tr>
            </g:if>

            <tr>
                <td  name="subscription.takeOwner.source">
                    <div>
                        <b><i class="balance scale icon"></i>${message(code: 'license.label')}:</b>
                        <g:if test="${sourceSubscription?.owner}">
                            <g:link controller="license" action="show" target="_blank" id="${sourceSubscription.owner.id}">
                                ${sourceSubscription.owner}
                            </g:link>
                        </g:if>
                    </div>
                </td>
                <g:if test="${isConsortialSubs}">
                        %{--SHARE--}%
                        <td class="center aligned">
                        </td>
                </g:if>
                %{--AKTIONEN:--}%
                <td class="center aligned">
                    <g:if test="${sourceSubscription?.owner}">
                        <div class="ui checkbox la-toggle-radio la-replace">
                            <g:checkBox name="subscription.takeOwner" data-action="copy" checked="${true}" />
                        </div>
                    </g:if>
                </td>

                <td  name="subscription.takeOwner.target">
                    <div>
                        <b><i class="balance scale icon"></i>${message(code: 'license.label')}:</b>
                        <g:if test="${targetSubscription?.owner}">
                            <g:link controller="license" action="show" target="_blank" id="${targetSubscription?.owner?.id}">
                                ${targetSubscription?.owner}
                            </g:link>
                        </g:if>
                    </div>
                </td>

                <td>
                    <g:if test="${targetSubscription?.owner}">
                        <div class="ui checkbox la-toggle-radio la-noChange">
                            <g:checkBox name="subscription.deleteOwner" data-action="delete" />
                        </div>
                    </g:if>
                </td>
            </tr>
            <tr>
                <td  name="subscription.takeOrgRelations.source">
                    <div>
                        <g:if test="${ ! source_visibleOrgRelations}">
                            <b><i class="university icon"></i>&nbsp${message(code: 'subscription.organisations.label')}:</b>
                        </g:if>
                        <g:each in="${source_visibleOrgRelations}" var="source_role">
                            <g:if test="${source_role.org}">
                                <div value="${genericOIDService.getOID(source_role)}" class="la-multi-sources">
                                    <b><i class="university icon"></i>&nbsp${source_role?.roleType?.getI10n("value")}:</b>
                                    <g:link controller="organisation" action="show" target="_blank" id="${source_role.org.id}">
                                        ${source_role?.org?.name}
                                    </g:link>

                                <br>
                                </div>
                            </g:if>
                        </g:each>
                    </div>
                </td>
                <g:if test="${isConsortialSubs}">
                        %{--SHARE--}%
                        <td class="center aligned">
                            <g:each in="${source_visibleOrgRelations}" var="source_role">
                                <g:if test="${source_role.org}">
                                    <div class="ui checkbox la-toggle-radio la-share">
                                        <input class="ui checkbox" type="checkbox" name="toggleShareOrgRoles" value="${source_role.class.name}:${source_role.id}" ${source_role.isShared ? 'checked': ''} />
                                    </div>
                                    <br>
                                </g:if>
                            </g:each>

                        </td>
                </g:if>

                </td>
                %{--AKTIONEN:--}%
                <td class="center aligned">
                    <g:each in="${source_visibleOrgRelations}" var="source_role">
                        <g:if test="${source_role.org}">
                            <div class="ui checkbox la-toggle-radio la-replace">
                                <g:checkBox name="subscription.takeOrgRelations" data-action="copy" value="${genericOIDService.getOID(source_role)}" checked="${true}" />
                            </div>
                        </g:if>
                    </g:each>
                </td>

                <td  name="subscription.takeOrgRelations.target">
                    <div>
                        <g:if test="${ ! target_visibleOrgRelations}">
                            <b><i class="university icon"></i>&nbsp${message(code: 'subscription.organisations.label')}:</b>
                        </g:if>
                        <g:each in="${target_visibleOrgRelations}" var="target_role">
                            <g:if test="${target_role.org}">
                                <div value="${genericOIDService.getOID(target_role)}">
                                    <b><i class="university icon"></i>&nbsp${target_role?.roleType?.getI10n("value")}:</b>
                                    <g:link controller="organisation" action="show" target="_blank" id="${target_role.org.id}">
                                        ${target_role?.org?.name}
                                    </g:link>
                                    <g:if test="${isConsortialSubs}">
                                            <div class="right aligned wide column">
                                                <g:if test="${target_role.isShared}">
                                                    <span data-position="top left"  class="la-popup-tooltip la-delay" data-content="${message(code:'property.share.tooltip.on')}">
                                                        <i class="la-share icon la-js-editmode-icon"></i>
                                                    </span>

                                                </g:if>
                                                <g:else>
                                                    <span data-position="top left"  class="la-popup-tooltip la-delay" data-content="${message(code:'property.share.tooltip.off')}">
                                                        <i class="la-share slash icon la-js-editmode-icon"></i>
                                                    </span>
                                                </g:else>

                                            </div>
                                    </g:if>
                                    <br>
                                </div>
                            </g:if>
                        </g:each>
                    </div>
                </td>
                <td>
                    <g:each in="${target_visibleOrgRelations}" var="target_role">
                        <g:if test="${target_role.org}">
                            <div class="ui checkbox la-toggle-radio la-noChange">
                                <g:checkBox name="subscription.deleteOrgRelations" data-action="delete" value="${genericOIDService.getOID(target_role)}" checked="${false}"/>
                            </div>
                            <br/>
                        </g:if>
                    </g:each>
                </td>
            </tr>
            <tr>
                <td name="subscription.takeIdentifier.source">
                    <b><i class="barcode icon"></i>&nbsp${message(code: 'default.identifiers.label')}:</b><br />
                    <g:each in="${sourceIdentifiers}" var="ident">
                        <b>${ident.ns.ns}:</b>&nbsp${ident.value}<br />
                    </g:each>
                </td>
                <g:if test="${isConsortialSubs}">
                %{--SHARE--}%
                    <td class="center aligned">
                    </td>
                </g:if>

                %{--COPY:--}%
                <td class="center aligned">
                    <g:each in="${sourceIdentifiers}" var="ident">
                        <div data-id="${ident.id}" class="la-element">
                            <div class="ui checkbox la-toggle-radio la-replace">
                                <g:checkBox name="subscription.takeIdentifierIds" value="${ident.id}" data-action="copy"  />
                            </div>
                        </div>
                    </g:each>
                </td>
                <td name="subscription.takeIdentifier.target">
                    <b><i class="barcode icon"></i>&nbsp${message(code: 'default.identifiers.label')}:</b><br />
                    <g:each in="${targetIdentifiers}" var="ident">
                        <b>${ident.ns.ns}:</b>&nbsp${ident.value}<br />
                    </g:each>
                </td>
                %{--DELETE:--}%
                <td>
                    <g:each in="${targetIdentifiers}" var="ident">
                        <div data-id="${ident.id}" class="la-element">
                            <div class="ui checkbox la-toggle-radio la-noChange">
                                <g:checkBox name="subscription.deleteIdentifierIds" value="${ident.id}" data-action="delete" checked="${false}" />
                            </div>
                        </div>
                    </g:each>
                </td>
            </tr>
            </tbody>
        </table>
        <g:set var="submitButtonText" value="${isRenewSub?
                message(code: 'subscription.renewSubscriptionConsortia.workFlowSteps.nextStep') :
                message(code: 'subscription.details.copyElementsIntoSubscription.copyDeleteElements.button') }" />

        <g:if test="${controllerName == 'survey'}">
            <div class="two fields">
                <div class="eight wide field" style="text-align: left;">
                    <g:set var="surveyConfig" value="${com.k_int.kbplus.SurveyConfig.findBySubscriptionAndSubSurveyUseForTransfer(Subscription.get(sourceSubscriptionId), true)}" />
                    <g:link action="renewalWithSurvey" id="${surveyConfig?.surveyInfo?.id}" params="[surveyConfigID: surveyConfig?.id]" class="ui button js-click-control">
                        <g:message code="renewalWithSurvey.back"/>
                    </g:link>
                </div>
                <div class="eight wide field" style="text-align: right;">
                    <g:set var="submitDisabled" value="${(sourceSubscription && targetSubscription)? '' : 'disabled'}"/>
                    <input type="submit" class="ui button js-click-control" value="${submitButtonText}" onclick="return jsConfirmation()"  ${submitDisabled}/>
                </div>
            </div>
        </g:if>
        <g:else>
            <div class="sixteen wide field" style="text-align: right;">
                <g:set var="submitDisabled" value="${(sourceSubscription && targetSubscription)? '' : 'disabled'}"/>
                <input type="submit" class="ui button js-click-control" value="${submitButtonText}" onclick="return jsConfirmation()" ${submitDisabled}/>
            </div>
        </g:else>
    </g:form>
</semui:form>

<r:script>

    var subCopyController = {

        checkboxes : {
            $takeDates: $('input:checkbox[name="subscription.takeDates"]'),
            $deleteDates: $('input:checkbox[name="subscription.deleteDates"]'),
            $takeOwner: $('input:checkbox[name="subscription.takeOwner"]'),
            $deleteOwner: $('input:checkbox[name="subscription.deleteOwner"]'),
            $takeOrgRelations: $('input:checkbox[name="subscription.takeOrgRelations"]'),
            $deleteOrgRelations: $('input:checkbox[name="subscription.deleteOrgRelations"]')
        },

        init: function() {
            var ref = subCopyController.checkboxes

            ref.$takeDates.change( function(event) {
                subCopyController.takeDates(this);
            }).trigger('change')

            ref.$deleteDates.change( function(event) {
                subCopyController.deleteDates(this);
            }).trigger('change')

            ref.$takeOwner.change( function(event) {
                subCopyController.takeOwner(this);
            }).trigger('change')

            ref.$deleteOwner.change( function(event) {
                subCopyController.deleteOwner(this);
            }).trigger('change')

            ref.$takeOrgRelations.change( function(event) {
                subCopyController.takeOrgRelations(this);
            }).trigger('change')

            ref.$deleteOrgRelations.change( function(event) {
                subCopyController.deleteOrgRelations(this);
            }).trigger('change')
        },

        takeDates: function(elem) {
            if (elem.checked) {
                $('.table tr td[name="subscription.takeDates.source"] div').addClass('willStay');
                $('.table tr td[name="subscription.takeDates.target"] div').addClass('willBeReplaced');
            }
            else {
                $('.table tr td[name="subscription.takeDates.source"] div').removeClass('willStay');
                $('.table tr td[name="subscription.takeDates.target"] div').removeClass('willBeReplaced');
            }
        },

        deleteDates: function(elem) {
            if (elem.checked) {
                $('.table tr td[name="subscription.takeDates.target"] div').addClass('willBeReplacedStrong');
            }
            else {
                $('.table tr td[name="subscription.takeDates.target"] div').removeClass('willBeReplacedStrong');
            }
        },

        takeOwner: function(elem) {
            if (elem.checked) {
                $('.table tr td[name="subscription.takeOwner.source"] div').addClass('willStay');
                $('.table tr td[name="subscription.takeOwner.target"] div').addClass('willBeReplaced');
            }
            else {
                $('.table tr td[name="subscription.takeOwner.source"] div').removeClass('willStay');
                $('.table tr td[name="subscription.takeOwner.target"] div').removeClass('willBeReplaced');
            }
        },

        deleteOwner: function(elem) {
            if (elem.checked) {
                $('.table tr td[name="subscription.takeOwner.target"] div').addClass('willBeReplacedStrong');
            }
            else {
                $('.table tr td[name="subscription.takeOwner.target"] div').removeClass('willBeReplacedStrong');
            }
        },

        takeOrgRelations: function(elem) {
            if (elem.checked) {
                $('.table tr td[name="subscription.takeOrgRelations.source"] div div[value="' + elem.value + '"]').addClass('willStay');
                $('.table tr td[name="subscription.takeOrgRelations.target"] div div').addClass('willStay');
            }
            else {
                $('.table tr td[name="subscription.takeOrgRelations.source"] div div[value="' + elem.value + '"]').removeClass('willStay');
                if (subCopyController.getNumberOfCheckedCheckboxes('subscription.takeOrgRelations') < 1) {
                    $('.table tr td[name="subscription.takeOrgRelations.target"] div div').removeClass('willStay');
                }
            }
        },

        deleteOrgRelations: function(elem) {
            if (elem.checked) {
                $('.table tr td[name="subscription.takeOrgRelations.target"] div div[value="' + elem.value + '"]').addClass('willBeReplacedStrong');
            } else {
                $('.table tr td[name="subscription.takeOrgRelations.target"] div div[value="' + elem.value + '"]').removeClass('willBeReplacedStrong');
            }
        },

        getNumberOfCheckedCheckboxes: function(inputElementName) {
            var checkboxes = document.querySelectorAll('input[name="' + inputElementName + '"]');
            var numberOfChecked = 0;
            for (var i = 0; i < checkboxes.length; i++) {
                if (checkboxes[i].checked) {
                    numberOfChecked++;
                }
            }
            return numberOfChecked;
        }
    }

    subCopyController.init()
</r:script>


