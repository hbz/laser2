<%@ page import="de.laser.DocContext; de.laser.Doc; de.laser.helper.RDStore" %>
<laser:serviceInjection />

<%
    List<DocContext> baseItems = []
    List<DocContext> sharedItems = []

    ownobj.documents.sort{it.owner?.title}.each{ it ->
        if (it.status != RDStore.DOC_CTX_STATUS_DELETED){
            if (it.sharedFrom) {
                sharedItems << it
            }
            else {
                if(it.owner.owner?.id == contextService.org.id || it.owner.owner == null)
                    baseItems << it
            }
        }
    }

    boolean editable2 = accessService.checkMinUserOrgRole(contextService.user,contextService.org,"INST_EDITOR")
    //println "EDITABLE: ${editable}"
    //println "EDITABLE2: ${editable2}"
%>

    <semui:card message="license.notes" class="notes la-js-hideable ${css_class}" href="#modalCreateNote" editable="${editable || editable2}">
        <g:each in="${baseItems}" var="docctx">
            <g:if test="${((docctx.owner?.contentType == Doc.CONTENT_TYPE_STRING) && !(docctx.domain) )}">
                <div class="ui small feed content la-js-dont-hide-this-card">
                    <div class="ui grid summary">
                        <div class="ten wide column la-column-right-lessPadding">
                            <g:if test="${(docctx.owner.owner?.id == contextService.org.id || docctx.owner.owner == null) && (editable || editable2)}">
                                <a onclick="noteedit(${docctx.owner.id});">
                                    <g:if test="${docctx.owner.title}">
                                        ${docctx.owner.title}</a>
                                    </g:if>
                                    <g:else>
                                        <g:message code="license.notes.noTitle"/>
                                    </g:else>
                                </a>
                            </g:if>
                            <g:else>
                                <a onclick="noteread(${docctx.owner.id});">
                                    <g:if test="${docctx.owner.title}">
                                        ${docctx.owner.title}</a>
                                    </g:if>
                                    <g:else>
                                        <g:message code="license.notes.noTitle"/>
                                    </g:else>
                                </a>
                            </g:else>
                            <br/>
                            ${message(code:'template.notes.created')}
                            <g:formatDate format="${message(code:'default.date.format.notime')}" date="${docctx.owner.dateCreated}"/>
                        </div>
                        <div class="right aligned six wide column la-column-left-lessPadding">
                            <%-- START First Button --%>
                            <g:if test="${!docctx.isShared}">
                                <g:link controller="${controllerName}" action="deleteDocuments" class="ui mini icon negative button js-open-confirm-modal"
                                        data-confirm-tokenMsg="${message(code: "confirm.dialog.delete.notes", args: [docctx.owner.title])}"
                                        data-confirm-term-how="delete"
                                        params='[instanceId:"${ownobj.id}", deleteId:"${docctx.id}", redirectAction:"${actionName}"]'>
                                    <i class="trash alternate icon"></i>
                                </g:link>
                            </g:if>
                            <%-- STOP First Button --%>
                            <g:if test="${ownobj.respondsTo('showUIShareButton') && ownobj.showUIShareButton()}">
                            <%-- START Second Button --%>
                                <g:if test="${docctx?.isShared}">
                                    <laser:remoteLink class="ui mini icon green button js-no-wait-wheel la-popup-tooltip la-delay"
                                                      controller="ajax"
                                                      action="toggleShare"
                                                      params='[owner:"${ownobj.class.name}:${ownobj.id}", sharedObject:"${docctx.class.name}:${docctx.id}", tmpl:"notes"]'
                                                      data-content="${message(code:'property.share.tooltip.on')}"
                                                      data-done=""
                                                      data-update="container-notes"
                                                      role="button"
                                    >
                                        <i class="icon la-share la-js-editmode-icon"></i>
                                    </laser:remoteLink>
                                </g:if>
                                <g:else>
                                    <laser:remoteLink class="ui mini icon button js-no-wait-wheel la-popup-tooltip la-delay js-open-confirm-modal"
                                                      controller="ajax"
                                                      action="toggleShare"
                                                      params='[owner:"${ownobj.class.name}:${ownobj.id}", sharedObject:"${docctx.class.name}:${docctx.id}", tmpl:"notes"]'
                                                      data-content="${message(code:'property.share.tooltip.off')}"
                                                      data-confirm-tokenMsg="${message(code: "confirm.dialog.share.element.member", args: [docctx.owner.title])}"
                                                      data-confirm-term-how="share"
                                                      data-done=""
                                                      data-update="container-notes"
                                                      role="button"
                                    >
                                        <i class="la-share slash icon la-js-editmode-icon"></i>
                                    </laser:remoteLink>
                                </g:else>

                            </g:if>
                            <g:else>

                                    <!-- Hidden Fake Button To hold the other Botton in Place -->
                                    <div class="ui icon mini button la-hidden">
                                        <i class="coffe icon"></i>
                                    </div>

                            </g:else>
                            <%-- START Second Button --%>
                        </div>
                    </div>
                </div>
            </g:if>
        </g:each>
    </semui:card>

    <g:if test="${sharedItems}">
        <semui:card message="license.notes.shared" class="documents la-js-hideable ${css_class}" editable="${editable}">
            <g:each in="${sharedItems}" var="docctx">

                <g:if test="${((docctx.owner?.contentType == Doc.CONTENT_TYPE_STRING) && !(docctx.domain) && (docctx.status?.value != 'Deleted') )}">
                    <div class="ui small feed content la-js-dont-hide-this-card">

                        <div class="ui grid summary">
                            <div class="twelve wide column">
                                <g:if test="${docctx.owner.title}">
                                    <a onclick="noteread(${docctx.owner.id});">${docctx.owner.title}</a>
                                </g:if>
                                <g:else>
                                    <a onclick="noteread(${docctx.owner.id});">Ohne Titel</a>
                                </g:else>
                                (${docctx.owner.type.getI10n("value")})
                            </div>
                            <div class="four wide column">
                                <g:if test="${docctx.owner.owner?.id == contextService.org.id}">
                                    <g:render template="/templates/documents/modal" model="[ownobj: ownobj, owntp: owntp, docctx: docctx, doc: docctx.owner]" />
                                    <button type="button" class="ui icon mini button editable-cancel" data-semui="modal" data-href="#modalEditDocument_${docctx.id}" ><i class="pencil icon"></i></button>
                                </g:if>
                            </div>
                        </div>
                    </div>
                </g:if>

            </g:each>
        </semui:card>
    </g:if>

    <script>
        function noteedit(id) {
            $.ajax({
                url: '<g:createLink controller="ajaxHtml" action="editNote"/>?id='+id,
                success: function(result){
                    $("#dynamicModalContainer").empty();
                    $("#modalEditNote").remove();

                    $("#dynamicModalContainer").html(result);
                    $("#dynamicModalContainer .ui.modal").modal('show');
                }
            });
        }
        function noteread(id) {
            $.ajax({
                url: '<g:createLink controller="ajaxHtml" action="readNote"/>?id='+id,
                success: function(result){
                    $("#dynamicModalContainer").empty();
                    $("#modalEditNote").remove();

                    $("#dynamicModalContainer").html(result);
                    $("#dynamicModalContainer .ui.modal").modal('show');
                }
            });
        }
        $( document ).ready(function() {
            if (r2d2) {
                r2d2.initDynamicSemuiStuff('#container-notes');
            }
        });
    </script>
