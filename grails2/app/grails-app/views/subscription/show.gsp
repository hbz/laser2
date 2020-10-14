<%@ page import="de.laser.DocContext; de.laser.helper.ConfigUtils; de.laser.Person; de.laser.PersonRole; java.math.MathContext; de.laser.Subscription; de.laser.Links; java.text.SimpleDateFormat" %>
<%@ page import="de.laser.properties.PropertyDefinition; de.laser.OrgRole; de.laser.License" %>
<%@ page import="de.laser.RefdataCategory;de.laser.RefdataValue;de.laser.helper.RDStore;de.laser.helper.RDConstants;de.laser.interfaces.CalculatedType" %>
<%@ page import="grails.plugin.springsecurity.SpringSecurityUtils;de.laser.PendingChangeConfiguration" %>
<laser:serviceInjection />
<%-- r:require module="annotations" / --%>

<!doctype html>
<html>
    <head>
        <meta name="layout" content="semanticUI"/>
        <title>${message(code:'laser')} : ${message(code:'subscription.details.label')}</title>
        <g:javascript src="properties.js"/>
        <style>
            .ui.table tr th {
                border-top: 1px solid #d3dae3;
            }
            .ui.table tr:first-child th {
                border-top: none;
            }
        </style>
    </head>
    <body>

        <semui:debugInfo>
            <div style="padding: 1em 0;">
                <p>sub.type: <semui:xEditableRefData owner="${subscriptionInstance}" field="type"
                                                     config="${RDConstants.SUBSCRIPTION_TYPE}"/>
                </p>
                <p>sub.instanceOf: <g:if test="${subscriptionInstance.instanceOf}"> <g:link action="show" id="${subscriptionInstance. instanceOf.id}">${subscriptionInstance.instanceOf.name}</g:link>
                    ${subscriptionInstance.instanceOf.getAllocationTerm()}
                </g:if> </p>
                <p>sub.administrative: ${subscriptionInstance.administrative}</p>
                <p>getCalculatedType(): ${subscriptionInstance._getCalculatedType()}</p>
            </div>
            <g:render template="/templates/debug/benchMark" model="[debug: benchMark]" />
            <g:render template="/templates/debug/orgRoles"  model="[debug: subscriptionInstance.orgRelations]" />
            <g:render template="/templates/debug/prsRoles"  model="[debug: subscriptionInstance.prsLinks]" />
        </semui:debugInfo>

        <g:render template="breadcrumb" model="${[ params:params ]}"/>

        <semui:controlButtons>
            <g:render template="actions" />
        </semui:controlButtons>

        <h1 class="ui icon header la-noMargin-top"><semui:headerIcon />
            <semui:xEditable owner="${subscriptionInstance}" field="name" />
        </h1>
        <g:if test="${editable}">
            <semui:auditButton auditable="[subscriptionInstance, 'name']" />
        </g:if>

        <semui:anualRings object="${subscriptionInstance}" controller="subscription" action="show" navNext="${navNextSubscription}" navPrev="${navPrevSubscription}"/>


    <g:render template="nav" />

    <semui:objectStatus object="${subscriptionInstance}" status="${subscriptionInstance.status}" />

    <g:render template="message" />

    <g:render template="/templates/meta/identifier" model="${[object: subscriptionInstance, editable: editable]}" />

        <semui:messages data="${flash}" />

        <g:render template="/templates/pendingChanges" model="${['pendingChanges': pendingChanges,'flash':flash,'model':subscriptionInstance]}"/>


    <div id="collapseableSubDetails" class="ui stackable grid">
        <div class="twelve wide column">

            <div class="la-inline-lists">
                <div class="ui two stackable cards">
                    <div class="ui card la-time-card">
                        <div class="content">
                            <dl>
                                <dt class="control-label">${message(code: 'subscription.startDate.label')}</dt>
                                <dd><semui:xEditable owner="${subscriptionInstance}" field="startDate" type="date" validation="datesCheck"/></dd>
                                <g:if test="${editable}">
                                    <dd class="la-js-editmode-container"><semui:auditButton auditable="[subscriptionInstance, 'startDate']"/></dd>
                                </g:if>
                            </dl>
                            <dl>
                                <dt class="control-label">${message(code: 'subscription.endDate.label')}</dt>
                                <dd><semui:xEditable owner="${subscriptionInstance}" field="endDate" type="date" validation="datesCheck"/></dd>
                                <g:if test="${editable}">
                                    <dd class="la-js-editmode-container"><semui:auditButton auditable="[subscriptionInstance, 'endDate']"/></dd>
                                </g:if>
                            </dl>

                            <dl>
                                <dt class="control-label">${message(code: 'subscription.manualCancellationDate.label')}</dt>
                                <dd><semui:xEditable owner="${subscriptionInstance}" field="manualCancellationDate" type="date"/></dd>
                                <g:if test="${editable}">
                                    <dd class="la-js-editmode-container"><semui:auditButton auditable="[subscriptionInstance, 'manualCancellationDate']" /></dd>
                                </g:if>
                            </dl>

                            <g:if test="${(subscriptionInstance.type == RDStore.SUBSCRIPTION_TYPE_CONSORTIAL &&
                                    subscriptionInstance._getCalculatedType() == CalculatedType.TYPE_PARTICIPATION) ||
                                    (subscriptionInstance.type == RDStore.SUBSCRIPTION_TYPE_LOCAL &&
                                    subscriptionInstance._getCalculatedType() == CalculatedType.TYPE_LOCAL)}">
                                <dl>
                                    <dt class="control-label">${message(code: 'subscription.isMultiYear.label')}</dt>
                                    <dd><semui:xEditableBoolean owner="${subscriptionInstance}" field="isMultiYear" /></dd>
                                </dl>
                            </g:if>

                        </div>
                    </div>
                    <div class="ui card">
                        <div class="content">
                            <dl>
                                <dt class="control-label">${message(code: 'default.status.label')}</dt>
                                <dd><semui:xEditableRefData owner="${subscriptionInstance}" field="status" config="${RDConstants.SUBSCRIPTION_STATUS}" constraint="removeValue_deleted" /></dd>
                                <g:if test="${editable}">
                                    <dd class="la-js-editmode-container"><semui:auditButton auditable="[subscriptionInstance, 'status']"/></dd>
                                </g:if>
                            </dl>
                            <sec:ifAnyGranted roles="ROLE_YODA">
                                <dl>
                                    <dt class="control-label">alter Lizenztyp</dt>
                                    <dd>
                                        %{--
                                        <%
                                            //does not work for some reason, proceed to IDs
                                            Set<Long> subscriberIDs = []
                                            subscriptionInstance.getAllSubscribers().each { subscriber ->
                                                subscriberIDs << subscriber.id
                                            }
                                        %>
                                        <g:if test="${subscriptionInstance.administrative || subscriberIDs.contains(contextOrg?.id)}">
                                            ${subscriptionInstance.type?.getI10n('value')}
                                        </g:if>
                                        <g:else>
                                            <semui:xEditableRefData owner="${subscriptionInstance}" field="type"
                                                                    config="${RDConstants.SUBSCRIPTION_TYPE}"
                                                                    constraint="removeValue_administrativeSubscription,removeValue_localSubscription"
                                            />
                                        </g:else>--}%
                                        <semui:xEditableRefData owner="${subscriptionInstance}" field="type"
                                                                config="${RDConstants.SUBSCRIPTION_TYPE}"
                                                                constraint="removeValue_administrativeSubscription,removeValue_localSubscription"
                                        />
                                    </dd>
                                    <dd class="la-js-editmode-container"><semui:auditButton auditable="[subscriptionInstance, 'type']"/></dd>
                                </dl>
                            </sec:ifAnyGranted>
                            <dl>
                                <dt class="control-label">${message(code: 'subscription.kind.label')}</dt>
                                <dd><semui:xEditableRefData owner="${subscriptionInstance}" field="kind" config="${RDConstants.SUBSCRIPTION_KIND}"/></dd>
                                <g:if test="${editable}">
                                    <dd class="la-js-editmode-container"><semui:auditButton auditable="[subscriptionInstance, 'kind']"/></dd>
                                </g:if>
                            </dl>
                            <dl>
                                <dt class="control-label">${message(code: 'subscription.form.label')}</dt>
                                <dd><semui:xEditableRefData owner="${subscriptionInstance}" field="form" config="${RDConstants.SUBSCRIPTION_FORM}"/></dd>
                                <g:if test="${editable}">
                                    <dd class="la-js-editmode-container"><semui:auditButton auditable="[subscriptionInstance, 'form']"/></dd>
                                </g:if>
                            </dl>
                            <dl>
                                <dt class="control-label">${message(code: 'subscription.resource.label')}</dt>
                                <dd><semui:xEditableRefData owner="${subscriptionInstance}" field="resource" config="${RDConstants.SUBSCRIPTION_RESOURCE}"/></dd>
                                <g:if test="${editable}">
                                    <dd class="la-js-editmode-container"><semui:auditButton auditable="[subscriptionInstance, 'resource']"/></dd>
                                </g:if>
                            </dl>
                            <g:if test="${!params.orgBasicMemberView && subscriptionInstance.instanceOf && (contextOrg?.id in [subscriptionInstance.getConsortia()?.id,subscriptionInstance.getCollective()?.id])}">
                                <dl>
                                    <dt class="control-label">${message(code:'subscription.isInstanceOfSub.label')}</dt>
                                    <dd>
                                        <g:link controller="subscription" action="show" id="${subscriptionInstance.instanceOf.id}">${subscriptionInstance.instanceOf}</g:link>
                                    </dd>
                                </dl>

                                <dl>
                                    <dt class="control-label">
                                        ${message(code:'license.details.linktoLicense.pendingChange')}
                                    </dt>
                                    <dd>
                                        <semui:xEditableBoolean owner="${subscriptionInstance}" field="isSlaved" />
                                    </dd>
                                </dl>
                            </g:if>

                            <dl>
                                <dt class="control-label">${message(code: 'subscription.isPublicForApi.label')}</dt>
                                <dd><semui:xEditableBoolean owner="${subscriptionInstance}" field="isPublicForApi" /></dd>
                                <g:if test="${editable}">
                                    <dd class="la-js-editmode-container"><semui:auditButton auditable="[subscriptionInstance, 'isPublicForApi']"/></dd>
                                </g:if>
                            </dl>

                            <dl>
                                <dt class="control-label">${message(code: 'subscription.hasPerpetualAccess.label')}</dt>
                                <dd><semui:xEditableBoolean owner="${subscriptionInstance}" field="hasPerpetualAccess" /></dd>
                                <g:if test="${editable}">
                                    <dd class="la-js-editmode-container"><semui:auditButton auditable="[subscriptionInstance, 'hasPerpetualAccess']"/></dd>
                                </g:if>
                            </dl>

                        </div>
                    </div>
                </div>

                <div class="ui card">
                    <div class="content">
                        <h5 class="ui header">
                           <g:message code="subscription.details.linksHeader"/>
                        </h5>
                        <g:if test="${links.entrySet()}">
                            <table class="ui three column table">
                                <g:each in="${links.entrySet().toSorted()}" var="linkTypes">
                                    <g:if test="${linkTypes.getValue().size() > 0 && linkTypes.getKey() != genericOIDService.getOID(RDStore.LINKTYPE_LICENSE)}">
                                        <g:each in="${linkTypes.getValue()}" var="link">
                                            <tr>
                                                <%
                                                    int perspectiveIndex = subscriptionInstance == link.sourceSubscription ? 0 : 1
                                                %>
                                                <th scope="row" class="control-label la-js-dont-hide-this-card">${genericOIDService.resolveOID(linkTypes.getKey()).getI10n("value").split("\\|")[perspectiveIndex]}</th>
                                                <td>
                                                    <g:set var="pair" value="${link.getOther(subscriptionInstance)}"/>
                                                    <g:link controller="subscription" action="show" id="${pair.id}">
                                                        ${pair.name}
                                                    </g:link><br>
                                                    <p><g:formatDate date="${pair.startDate}" format="${message(code:'default.date.format.notime')}"/>–<g:formatDate date="${pair.endDate}" format="${message(code:'default.date.format.notime')}"/></p>
                                                    <g:set var="comment" value="${DocContext.findByLink(link)}"/>
                                                    <g:if test="${comment}">
                                                        <p><em>${comment.owner.content}</em></p>
                                                    </g:if>
                                                </td>
                                                <td class="right aligned">
                                                    <g:render template="/templates/links/subLinksModal"
                                                              model="${[tmplText:message(code:'subscription.details.editLink'),
                                                                        tmplIcon:'write',
                                                                        tmplCss: 'icon la-selectable-button',
                                                                        tmplID:'editLink',
                                                                        tmplModalID:"sub_edit_link_${link.id}",
                                                                        editmode: editable,
                                                                        context: subscription,
                                                                        atConsortialParent: contextOrg.id == subscription.getConsortia()?.id ? "true" : "false",
                                                                        link: link
                                                              ]}" />
                                                    <g:if test="${editable}">
                                                        <span class="la-popup-tooltip la-delay" data-content="${message(code:'license.details.unlink')}">
                                                            <g:link class="ui negative icon button la-selectable-button js-open-confirm-modal"
                                                                    data-confirm-tokenMsg="${message(code: "confirm.dialog.unlink.subscription.subscription")}"
                                                                    data-confirm-term-how="unlink"
                                                                    controller="myInstitution" action="unlinkObjects" params="${[oid : link.class.name+':'+link.id]}">
                                                                <i class="unlink icon"></i>
                                                            </g:link>
                                                        </span>
                                                    </g:if>
                                                </td>
                                            </tr>
                                        </g:each>
                                    </g:if>
                                </g:each>
                            </table>
                        </g:if>
                        <g:else>
                            <p>
                                <g:message code="subscription.details.noLink"/>
                            </p>
                        </g:else>
                        <div class="ui la-vertical buttons">
                            <g:render template="/templates/links/subLinksModal"
                                      model="${[tmplText:message(code:'subscription.details.addLink'),
                                                tmplID:'addLink',
                                                tmplButtonText:message(code:'subscription.details.addLink'),
                                                tmplModalID:'sub_add_link',
                                                editmode: editable,
                                                atConsortialParent: contextOrg.id == subscription.getConsortia()?.id ? "true" : "false",
                                                context: subscription
                                      ]}" />
                        </div>
                    </div>
                </div>

              <g:if test="${subscriptionInstance.packages}">
                <div class="ui card la-js-hideable hidden">
                  <div class="content">
                      <g:render template="accessPointLinksAsList"
                                model="${[roleLinks: visibleOrgRelations,
                                          roleObject: subscriptionInstance,
                                          roleRespValue: 'Specific subscription editor',
                                          editmode: editable,
                                          accessConfigEditable : accessService.checkPermAffiliation('ORG_BASIC_MEMBER','INST_EDITOR') || (accessService.checkPermAffiliation('ORG_CONSORTIUM','INST_EDITOR') && subscriptionInstance.getSubscriber().id == contextOrg.id)
                                ]}" />
                  </div><!-- .content -->
                </div>


                      <div class="ui card la-js-hideable hidden">
                          <div class="ui segment accordion">
                              <div class="ui title header">
                                  <i class="dropdown icon la-dropdown-accordion"></i><g:message code="subscription.packages.config.header" />
                              </div>
                              <div class="content">
                                  <g:each in="${subscriptionInstance.packages}" var="subscriptionPackage">
                                      <h5 class="ui header">
                                          <g:message code="subscription.packages.config.label" args="${[subscriptionPackage.pkg.name]}"/>
                                      </h5>
                                      <g:form action="setupPendingChangeConfiguration" params="[id:subscriptionInstance.id,subscriptionPackage:subscriptionPackage.id]">
                                          <dl>
                                              <dt class="control-label"><g:message code="subscription.packages.changeType.label"/></dt>
                                              <dt class="control-label">
                                                  <g:message code="subscription.packages.setting.label"/>
                                              </dt>
                                              <dt class="control-label" data-tooltip="${message(code:"subscription.packages.notification.label")}">
                                                  <i class="ui large icon bullhorn"></i>
                                              </dt>
                                              <g:if test="${accessService.checkPerm('ORG_CONSORTIUM')}">
                                                  <dt class="control-label" data-tooltip="${message(code:'subscription.packages.auditable')}">
                                                      <i class="ui large icon thumbtack"></i>
                                                  </dt>
                                              </g:if>
                                          </dl>
                                          <g:set var="excludes" value="${[PendingChangeConfiguration.PACKAGE_PROP,PendingChangeConfiguration.PACKAGE_DELETED]}"/>
                                          <g:each in="${PendingChangeConfiguration.SETTING_KEYS}" var="settingKey">
                                              <dl>
                                                  <dt class="control-label">
                                                      <g:message code="subscription.packages.${settingKey}"/>
                                                  </dt>
                                                  <dd>
                                                      <g:if test="${!(settingKey in excludes)}">
                                                          <g:if test="${editable}">
                                                              <laser:select class="ui dropdown"
                                                                            name="${settingKey}!§!setting" from="${RefdataCategory.getAllRefdataValues(RDConstants.PENDING_CHANGE_CONFIG_SETTING)}"
                                                                            optionKey="id" optionValue="value"
                                                                            value="${subscriptionPackage.getPendingChangeConfig(settingKey) ? subscriptionPackage.getPendingChangeConfig(settingKey).settingValue.id : RDStore.PENDING_CHANGE_CONFIG_PROMPT.id}"
                                                              />
                                                          </g:if>
                                                          <g:else>
                                                              ${subscriptionPackage.getPendingChangeConfig(settingKey) ? subscriptionPackage.getPendingChangeConfig(settingKey).settingValue.getI10n("value") : RDStore.PENDING_CHANGE_CONFIG_PROMPT.getI10n("value")}
                                                          </g:else>
                                                      </g:if>
                                                  </dd>
                                                  <dd>
                                                      <g:if test="${editable}">
                                                          <g:checkBox class="ui checkbox" name="${settingKey}!§!notification" checked="${subscriptionPackage.getPendingChangeConfig(settingKey)?.withNotification}"/>
                                                      </g:if>
                                                      <g:else>
                                                          ${subscriptionPackage.getPendingChangeConfig(settingKey)?.withNotification ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value")}
                                                      </g:else>
                                                  </dd>
                                                  <g:if test="${accessService.checkPerm('ORG_CONSORTIUM')}">
                                                      <dd>
                                                          <g:if test="${!(settingKey in excludes)}">
                                                              <g:if test="${editable}">
                                                                  <g:checkBox class="ui checkbox" name="${settingKey}!§!auditable" checked="${subscriptionPackage.getPendingChangeConfig(settingKey) ? auditService.getAuditConfig(subscriptionInstance,settingKey) : false}"/>
                                                              </g:if>
                                                              <g:else>
                                                                  ${subscriptionPackage.getPendingChangeConfig(settingKey) ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value")}
                                                              </g:else>
                                                          </g:if>
                                                      </dd>
                                                  </g:if>
                                              </dl>
                                          </g:each>
                                          <g:if test="${editable}">
                                              <dl>
                                                  <dt class="control-label"><g:submitButton class="ui button btn-primary" name="${message(code:'subscription.packages.submit.label')}"/></dt>
                                              </dl>
                                          </g:if>
                                      </g:form>
                                  </g:each>

                              </div><!-- .content -->
                          </div>
                      </div>
              </g:if>

                <div class="ui card la-js-hideable hidden">
                    <div class="content">
                        <g:render template="/templates/links/orgLinksAsList"
                                  model="${[roleLinks: visibleOrgRelations,
                                            roleObject: subscriptionInstance,
                                            roleRespValue: 'Specific subscription editor',
                                            editmode: editable,
                                            showPersons: true
                                  ]}" />

                        <div class="ui la-vertical buttons la-js-hide-this-card">

                            <g:render template="/templates/links/orgLinksSimpleModal"
                                      model="${[linkType: subscriptionInstance?.class?.name,
                                                parent: subscriptionInstance.class.name + ':' + subscriptionInstance.id,
                                                property: 'orgs',
                                                recip_prop: 'sub',
                                                tmplRole: RDStore.OR_PROVIDER,
                                                tmplEntity:message(code:'subscription.details.linkProvider.tmplEntity'),
                                                tmplText:message(code:'subscription.details.linkProvider.tmplText'),
                                                tmplButtonText:message(code:'subscription.details.linkProvider.tmplButtonText'),
                                                tmplModalID:'modal_add_provider',
                                                editmode: editable,
                                                orgList: availableProviderList,
                                                signedIdList: existingProviderIdList
                                      ]}" />

                            <g:render template="/templates/links/orgLinksSimpleModal"
                                      model="${[linkType: subscriptionInstance?.class?.name,
                                                parent: subscriptionInstance.class.name + ':' + subscriptionInstance.id,
                                                property: 'orgs',
                                                recip_prop: 'sub',
                                                tmplRole: RDStore.OR_AGENCY,
                                                tmplEntity: message(code:'subscription.details.linkAgency.tmplEntity'),
                                                tmplText: message(code:'subscription.details.linkAgency.tmplText'),
                                                tmplButtonText: message(code:'subscription.details.linkAgency.tmplButtonText'),
                                                tmplModalID:'modal_add_agency',
                                                editmode: editable,
                                                orgList: availableAgencyList,
                                                signedIdList: existingAgencyIdList
                                      ]}" />

                        </div><!-- la-js-hide-this-card -->

                    </div>
                </div>

                <div class="ui card la-js-hideable hidden">
                    <div class="content">
                        <h5 class="ui header">
                            <g:message code="license.plural"/>
                        </h5>
                        <g:if test="${links[genericOIDService.getOID(RDStore.LINKTYPE_LICENSE)]}">
                            <table class="ui fixed table">
                                <g:each in="${links[genericOIDService.getOID(RDStore.LINKTYPE_LICENSE)]}" var="link">
                                    <tr><g:set var="pair" value="${link.getOther(subscriptionInstance)}"/>
                                        <th scope="row" class="control-label la-js-dont-hide-this-card">${pair.licenseCategory?.getI10n("value")}</th>
                                        <td>
                                            <g:link controller="license" action="show" id="${pair.id}">
                                                ${pair.reference} (${pair.status.getI10n("value")})
                                            </g:link>
                                            <p><g:formatDate date="${pair.startDate}" format="${message(code:'default.date.format.notime')}"/>-<g:formatDate date="${pair.endDate}" format="${message(code:'default.date.format.notime')}"/></p>
                                            <g:set var="comment" value="${DocContext.findByLink(link)}"/>
                                            <g:if test="${comment}">
                                                <p><em>${comment.owner.content}</em></p>
                                            </g:if>
                                        </td>
                                        <td class="right aligned">
                                            <g:if test="${pair.propertySet}">
                                                <span class="la-popup-tooltip la-delay" data-content="${message(code:'subscription.details.viewLicenseProperties')}">
                                                    <button id="derived-license-properties-toggle${link.id}" class="ui icon button la-js-dont-hide-button">
                                                        <i class="ui angle double down icon"></i>
                                                    </button>
                                                </span>
                                                <r:script>
                                                    $("#derived-license-properties-toggle${link.id}").on('click', function() {
                                                        $("#derived-license-properties${link.id}").transition('slide down');
                                                        //$("#derived-license-properties${link.id}").toggleClass('hidden');

                                                        if ($("#derived-license-properties${link.id}").hasClass('visible')) {
                                                            $(this).html('<i class="ui angle double down icon"></i>');
                                                        } else {
                                                            $(this).html('<i class="ui angle double up icon"></i>');
                                                        }
                                                    })
                                                </r:script>
                                            </g:if>
                                            <g:render template="/templates/links/subLinksModal"
                                                      model="${[tmplText:message(code:'subscription.details.editLink'),
                                                                tmplIcon:'write',
                                                                tmplCss: 'icon la-selectable-button',
                                                                tmplID:'editLicenseLink',
                                                                tmplModalID:"sub_edit_link_${link.id}",
                                                                editmode: editable,
                                                                subscriptionLicenseLink: true,
                                                                context: subscription,
                                                                atConsortialParent: contextOrg == subscription.getConsortia(),
                                                                link: link
                                                      ]}" />
                                            <g:if test="${editable}">
                                                <div class="ui icon negative buttons">
                                                    <span class="la-popup-tooltip la-delay" data-content="${message(code:'license.details.unlink')}">
                                                        <g:link class="ui negative icon button la-selectable-button js-open-confirm-modal"
                                                                data-confirm-tokenMsg="${message(code: "confirm.dialog.unlink.subscription.subscription")}"
                                                                data-confirm-term-how="unlink"
                                                                action="unlinkLicense" params="${[licenseOID: link.sourceLicense.id, id:subscription.id]}">
                                                            <i class="unlink icon"></i>
                                                        </g:link>
                                                    </span>
                                                </div>
                                                <br />
                                            </g:if>
                                        </td>
                                    </tr>
                                    <g:if test="${pair.propertySet}">
                                    <tr>
                                        <td style="border-top: none;" colspan="3"><div id="${link.id}Properties"></div></td>
                                    </tr>
                                    </g:if>
                                </g:each>
                            </table>
                        </g:if>

                        <g:if test="${editable}">
                            <div class="ui la-vertical buttons">
                                <g:render template="/templates/links/subLinksModal"
                                          model="${[tmplText:message(code:'license.details.addLink'),
                                                    tmplID:'addLicenseLink',
                                                    tmplButtonText:message(code:'license.details.addLink'),
                                                    tmplModalID:'sub_add_license_link',
                                                    editmode: editable,
                                                    subscriptionLicenseLink: true,
                                                    atConsortialParent: contextOrg == subscription.getConsortia(),
                                                    context: subscription
                                          ]}" />
                            </div>
                        </g:if>



                    </div><!-- .content -->
                </div>

                <%-- FINANCE, to be reactivated as of ERMS-943 --%>
                <%-- assemble data on server side --%>
                <g:if test="${costItemSums.ownCosts || costItemSums.consCosts || costItemSums.subscrCosts}">
                    <div class="ui card la-dl-no-table">
                        <div class="content">
                            <g:if test="${costItemSums.ownCosts}">
                                <g:if test="${(contextOrg.id != subscription.getConsortia()?.id && subscription.instanceOf) || !subscription.instanceOf}">
                                    <h5 class="ui header">${message(code:'financials.label')} : ${message(code:'financials.tab.ownCosts')}</h5>
                                    <g:render template="financials" model="[data:costItemSums.ownCosts]"/>
                                </g:if>
                            </g:if>
                            <g:if test="${costItemSums.consCosts}">
                                <h5 class="ui header">${message(code:'financials.label')} : ${message(code:'financials.tab.consCosts')}</h5>
                                <g:render template="financials" model="[data:costItemSums.consCosts]"/>
                            </g:if>
                            <g:elseif test="${costItemSums.subscrCosts}">
                                <h5 class="ui header">${message(code:'financials.label')} : ${message(code:'financials.tab.subscrCosts')}</h5>
                                <g:render template="financials" model="[data:costItemSums.subscrCosts]"/>
                            </g:elseif>
                        </div>
                    </div>
                </g:if>
                <g:if test="${usage}">
                    <div class="ui card la-dl-no-table">
                        <div class="content">
                            <g:if test="${totalCostPerUse}">
                                <dl>
                                    <dt class="control-label la-js-dont-hide-this-card">${message(code: 'subscription.details.costPerUse.header')}</dt>
                                    <dd><g:formatNumber number="${totalCostPerUse}" type="currency"
                                                        currencyCode="${currencyCode}" maxFractionDigits="2"
                                                        minFractionDigits="2" roundingMode="HALF_UP"/>
                                        (${message(code: 'subscription.details.costPerUse.usedMetric')}: ${costPerUseMetric})
                                    </dd>
                                </dl>
                                <div class="ui divider"></div>
                            </g:if>
                            <g:if test="${lusage}">
                            <dl>
                                <dt class="control-label">${message(code: 'default.usage.licenseGrid.header')}</dt>
                                <dd>
                                    <table class="ui compact celled la-table-inCard  table">
                                        <thead>
                                        <tr>
                                            <th>${message(code: 'default.usage.reportType')}</th>
                                            <g:each in="${l_x_axis_labels}" var="l">
                                                <th>${l}</th>
                                            </g:each>
                                            <th></th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        <g:set var="counter" value="${0}"/>
                                        <g:each in="${lusage}" var="v">
                                            <tr>
                                                <g:set var="reportMetric" value="${l_y_axis_labels[counter++]}" />
                                                <td>${reportMetric}
                                            </td>
                                                <g:each in="${v}" var="v2">
                                                    <td>${v2}</td>
                                                </g:each>
                                                <td>
                                                    <g:set var="missingSubMonths"
                                                           value="${missingSubscriptionMonths[reportMetric.split(':')[0]]}"/>
                                                    <g:if test="${missingSubMonths}">
                                                        <span class="la-long-tooltip la-popup-tooltip la-delay"
                                                              data-html="${message(code: 'default.usage.missingUsageInfo')}: ${missingSubMonths.join(',')}">
                                                            <i class="exclamation triangle icon la-popup small"></i>
                                                        </span>
                                                    </g:if>
                                                </td>
                                            </tr>
                                        </g:each>
                                        </tbody>
                                    </table>
                                </dd>
                            </dl>
                            <div class="ui divider"></div>
                            </g:if>
                            <dl>
                                <dt class="control-label la-js-dont-hide-this-card">${message(code: 'default.usage.label')}</dt>
                                <dd>
                                    <table class="ui compact celled la-table-inCard la-ignore-fixed table">
                                        <thead>
                                        <tr>
                                            <th>${message(code: 'default.usage.reportType')}
                                            </th>
                                            <g:each in="${x_axis_labels}" var="l">
                                                <th>${l}</th>
                                            </g:each>
                                            <th></th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        <g:set var="counter" value="${0}"/>
                                        <g:each in="${usage}" var="v">
                                            <tr>
                                                <g:set var="reportMetric" value="${y_axis_labels[counter++]}" />
                                                <td>${reportMetric}
                                                <span class="la-long-tooltip la-popup-tooltip la-delay"
                                                      data-html="${message(code: 'default.usage.reportUpToInfo')}: ${lastUsagePeriodForReportType[reportMetric.split(':')[0]]}">
                                                    <i class="info icon small circular la-popup"></i>
                                                </span>
                                                </td>
                                                <g:each in="${v}" status="i" var="v2">
                                                    <td>
                                                        <laser:statsLink
                                                            base="${ConfigUtils.getStatsApiUrl()}"
                                                            module="statistics"
                                                            controller="default"
                                                            action="select"
                                                            target="_blank"
                                                            params="[mode        : usageMode,
                                                                     packages    : subscription.getCommaSeperatedPackagesIsilList(),
                                                                     vendors     : natStatSupplierId,
                                                                     institutions: statsWibid,
                                                                     reports     : reportMetric.split(':')[0],
                                                                     years       : x_axis_labels[i]
                                                            ]"
                                                            title="Springe zu Statistik im Nationalen Statistikserver">
                                                            ${v2}
                                                        </laser:statsLink>
                                                    </td>
                                                </g:each>
                                                <g:set var="missing" value="${missingMonths[reportMetric.split(':')[0]]}"/>
                                                <td>
                                                    <g:if test="${missing}">
                                                        <span class="la-long-tooltip la-popup-tooltip la-delay"
                                                              data-html="${message(code: 'default.usage.missingUsageInfo')}: ${missing.join(',')}">
                                                            <i class="exclamation triangle icon la-popup small"></i>
                                                        </span>
                                                    </g:if>
                                                </td>
                                            </tr>
                                        </g:each>
                                        </tbody>
                                    </table>
                                </dd>
                            </dl>
                        </div>
                    </div>
                </g:if>

                <div id="new-dynamic-properties-block">

                    <g:render template="properties" model="${[
                            subscriptionInstance: subscriptionInstance
                    ]}" />

                </div><!-- #new-dynamic-properties-block -->

               <div class="clear-fix"></div>
            </div>
        </div><!-- .twelve -->

        <aside class="four wide column la-sidekick">
            <g:render template="/templates/aside1" model="${[ownobj:subscriptionInstance, owntp:'subscription']}" />
        </aside><!-- .four -->

    </div><!-- .grid -->


    <div id="magicArea"></div>

    <r:script>
      $(document).ready(function() {

          function unlinkPackage(pkg_id){
            var req_url = "${createLink(controller:'subscription', action:'unlinkPackage', params:[subscription:subscriptionInstance.id])}&package="+pkg_id

            $.ajax({url: req_url,
              success: function(result){
                 $('#magicArea').html(result);
              },
              complete: function(){
                $("#unlinkPackageModal").modal("show");
              }
            });
          }

          function hideModal(){
            $("[name='coreAssertionEdit']").modal('hide');
          }

          function showCoreAssertionModal(){

            $("[name='coreAssertionEdit']").modal('show');

          }

          <g:if test="${editable}">

             $('#collapseableSubDetails').on('show', function() {
                $('.hidden-license-details i').removeClass('icon-plus').addClass('icon-minus');
            });

            // Reverse it for hide:
            $('#collapseableSubDetails').on('hide', function() {
                $('.hidden-license-details i').removeClass('icon-minus').addClass('icon-plus');
            });
          </g:if>

          <g:if test="${params.asAt && params.asAt.length() > 0}"> $(function() {
            document.body.style.background = "#fcf8e3";
          });</g:if>

          <g:each in="${links[genericOIDService.getOID(RDStore.LINKTYPE_LICENSE)]}" var="link">
              $.ajax({
                  url: "<g:createLink controller="ajaxHtml" action="getLicensePropertiesForSubscription" />",
                  data: {
                       loadFor: "${link.sourceLicense.id}",
                       linkId: ${link.id}
                  }
              }).done(function(response) {
                  $("#${link.id}Properties").html(response);
              }).fail();

          </g:each>

        });

    </r:script>
  </body>
</html>
