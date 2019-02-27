<%@ page import="com.k_int.properties.PropertyDefinition; de.laser.helper.RDStore; com.k_int.kbplus.Person; com.k_int.kbplus.Subscription" %>
<br>
<semui:form>
    <g:render template="selectSourceAndTargetSubscription" model="[
            sourceSubscription: sourceSubscription,
            targetSubscription: targetSubscription,
            allSubscriptions_readRights: allSubscriptions_readRights,
            allSubscriptions_writeRights: allSubscriptions_writeRights]"/>
    <hr>
    <g:form action="copyElementsIntoSubscription" controller="subscriptionDetails" id="${params.id}"
            params="[workFlowPart: workFlowPart, sourceSubscriptionId: sourceSubscriptionId, targetSubscriptionId: targetSubscription?.id]" method="post" class="ui form newLicence">
        <table class="ui celled table">
            <tbody>
            <tr>
                <th>${message(code: 'default.select.label')}</th>
                <th>${message(code: 'subscription.property')}</th>
                <td><b>${message(code: 'subscription.details.copyElementsIntoSubscription.sourceSubscription.name')}:</b>
                <g:if test="${sourceSubscription}"><g:link controller="subscriptionDetails" action="show" id="${sourceSubscription?.id}">${sourceSubscription?.name}</g:link></g:if>
                </td>
                <td><b>${message(code: 'subscription.details.copyElementsIntoSubscription.targetSubscription.name')}:</b>
                <g:if test="${targetSubscription}"><g:link controller="subscriptionDetails" action="show" id="${targetSubscription?.id}">${targetSubscription?.name}</g:link></g:if>
                </td>
            </tr>
            <tr>
                <th><g:checkBox name="subscription.takeDates" /></th>
                <td>${message(code: 'subscription.takeDates')}</td>
                <td><g:formatDate date="${sourceSubscription.startDate}" format="${message(code: 'default.date.format.notime')}"/>
                    ${sourceSubscription?.endDate ? (' - ' + formatDate(date: sourceSubscription.endDate, format: message(code: 'default.date.format.notime'))) : ''}</td>
                <td><g:formatDate date="${targetSubscription?.startDate}" format="${message(code: 'default.date.format.notime')}"/>
                    ${targetSubscription?.endDate ? (' - ' + formatDate(date: targetSubscription?.endDate, format: message(code: 'default.date.format.notime'))) : ''}</td>
            </tr>
            <tr>
                <th><g:checkBox name="subscription.takeLinks" disabled=""/></th>
                <td>${message(code: 'subscription.takeLinks')}</td>
                <td>
                    <g:each in="${sourceSubscription?.packages?.sort { it.pkg.name }}" var="sp">
                        <b>${message(code: 'subscription.packages.label')}:</b>
                        <g:link controller="packageDetails" action="show" target="_blank" id="${sp.pkg.id}">${sp?.pkg?.name}</g:link>
                        <g:if test="${sp.pkg?.contentProvider}">(${sp.pkg?.contentProvider?.name})</g:if>
                        <br>
                    </g:each>
                    <br>
                    <g:if test="${sourceSubscription?.owner}">
                        <b>${message(code: 'license')}:</b>
                        <g:link controller="licenseDetails" action="show" target="_blank" id="${sourceSubscription.owner.id}">
                            ${sourceSubscription.owner}
                        </g:link>
                        <br><br>
                    </g:if>
                    <g:each in="${source_visibleOrgRelations}" var="source_role">
                        <g:if test="${source_role.org}">
                            <b>${source_role?.roleType?.getI10n("value")}:</b>
                            <g:link controller="Organisations" action="show" target="_blank" id="${source_role.org.id}">
                                ${source_role?.org?.name}
                            </g:link><br>
                        </g:if>
                    </g:each>
                </td>
                <td>
                    <g:each in="${targetSubscription?.packages?.sort { it.pkg.name }}" var="sp">
                        <b>${message(code: 'subscription.packages.label')}:</b>
                        <g:link controller="packageDetails" action="show" target="_blank" id="${sp.pkg.id}">${sp?.pkg?.name}</g:link>
                        <g:if test="${sp.pkg?.contentProvider}">(${sp.pkg?.contentProvider?.name})</g:if>
                        <br>
                    </g:each>
                    <br>
                    <g:if test="${targetSubscription?.owner}">
                        <b>${message(code: 'license')}:</b>
                        <g:link controller="licenseDetails" action="show" target="_blank" id="${targetSubscription?.owner?.id}">
                            ${targetSubscription?.owner}
                        </g:link>
                        <br><br>
                    </g:if>
                    <g:each in="${target_visibleOrgRelations}" var="target_role">
                        <g:if test="${target_role.org}">
                            <b>${target_role?.roleType?.getI10n("value")}:</b>
                            <g:link controller="Organisations" action="show" target="_blank" id="${target_role.org.id}">
                                ${target_role?.org?.name}
                            </g:link><br>
                        </g:if>
                    </g:each>
                </td>
            </tr>

            <tr>
                <th><g:checkBox name="subscription.takeEntitlements" disabled=""/></th>
                <td>${message(code: 'subscription.takeEntitlements')} <br>COMING SOON</td>
                <% def sourceIECount = sourceSubscription?.issueEntitlements?.findAll { it.status != RDStore.IE_DELETED }?.size() %>
                <td><g:if test="${sourceIECount}"><b>${message(code: 'issueEntitlement.countSubscription')} </b>
                    ${sourceIECount}</g:if>
                    <br>
                </td>
                <% def targetIECount = targetSubscription?.issueEntitlements?.findAll { it.status != RDStore.IE_DELETED }?.size() %>
                <td><g:if test="${targetIECount}"> <b>${message(code: 'issueEntitlement.countSubscription')} </b>
                    ${targetIECount}</g:if>
                </td>
            </tr>
            </tbody>
        </table>
        <input type="submit" class="ui button js-click-control" value="Ausgewählte Elemente kopieren/überschreiben" />
    </g:form>
</semui:form>
