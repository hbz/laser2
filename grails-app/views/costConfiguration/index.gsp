<%@ page import="de.laser.OrgRole;de.laser.RefdataCategory;de.laser.RefdataValue" %>
<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="semanticUI">
        <title>${message(code:'laser')} : ${message(code: 'menu.institutions.costConfiguration')}</title>
    </head>
    <body>

        <semui:breadcrumbs>
            <semui:crumb message="menu.institutions.costConfiguration" class="active" />
        </semui:breadcrumbs>
        <br>
        <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon/><g:message code="menu.institutions.costConfiguration"/></h1>



         <semui:msg class="warning" header="${message(code: 'message.information')}" message="costConfiguration.preset" />
        <semui:messages data="${flash}"/>
        <g:if test="${editable}">
            <div class="content ui form">
                <div class="fields">
                    <div class="field">
                        <g:link controller="costConfiguration" action="createNewConfiguration" class="ui button trigger-modal">
                            ${message(code:'costItemElementConfiguration.create_new.label')}
                        </g:link>
                    </div>
                </div>
            </div>
        </g:if>
        <div class="ui styled fluid">
            <table class="ui celled la-table compact table">
                <thead>
                    <tr>
                        <th><g:message code="financials.costItemElement"/></th>
                        <th><g:message code="financials.costItemConfiguration"/></th>
                        <g:if test="${editable}">
                            <th><g:message code="financials.setAll"/></th>
                            <th><g:message code="default.actions.label"/></th>
                        </g:if>
                    </tr>
                </thead>
                <tbody>
                    <g:each in="${costItemElementConfigurations}" var="ciec">
                        <tr>
                            <td>${ciec.costItemElement.getI10n('value')}</td>
                            <td>
                                <semui:xEditableRefData owner="${ciec}" field="elementSign" emptytext="${message(code:'financials.costItemConfiguration.notSet')}" config="${de.laser.helper.RDConstants.COST_CONFIGURATION}"/>
                            </td>
                            <g:if test="${editable}">
                                <td>
                                    <g:link class="button js-open-confirm-modal"
                                            data-confirm-tokenMsg="${message(code:'confirmation.content.bulkCostConfiguration')}"
                                            data-confirm-term-how="ok"
                                            action="setAllCostItems" params="${[cie:ciec.costItemElement.class.name+":"+ciec.costItemElement.id]}">
                                        ${message(code:'costConfiguration.configureAllCostItems')}
                                    </g:link>
                                </td>
                                <td>
                                    <g:link class="ui icon negative button js-open-confirm-modal"
                                            data-confirm-tokenMsg="${message(code: "confirm.dialog.delete.costItemElementConfiguration", args: [ciec.costItemElement.getI10n("value")])}"
                                            data-confirm-term-how="delete"
                                            controller="costConfiguration" action="deleteCostConfiguration"
                                            params="${[ciec: ciec.id]}">
                                        <i class="trash alternate icon"></i>
                                    </g:link>
                                </td>
                            </g:if>
                        </tr>
                    </g:each>
                </tbody>
            </table>
        </div>
        <script>
            $('.trigger-modal').on('click', function(e) {
                e.preventDefault();

                $.ajax({
                    url: $(this).attr('href')
                }).done( function (data) {
                    $('.ui.dimmer.modals > #ciecModal').remove();
                    $('#dynamicModalContainer').empty().html(data);

                    $('#dynamicModalContainer .ui.modal').modal({
                        onVisible: function () {
                            r2d2.initDynamicSemuiStuff('#ciecModal');
                            r2d2.initDynamicXEditableStuff('#ciecModal');
                        },
                        detachable: true,
                        autofocus: false,
                        closable: false,
                        transition: 'scale',
                        onApprove : function() {
                            $(this).find('.ui.form').submit();
                            return false;
                        }
                    }).modal('show');
                })
            })
        </script>
    </body>
</html>