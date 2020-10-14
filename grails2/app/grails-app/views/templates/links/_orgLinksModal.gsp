<%@ page import="de.laser.Org" %>
<g:if test="${editmode}">
    <a role="button" class="ui button" data-semui="modal" href="#${tmplModalID}">${tmplButtonText}</a>
</g:if>

<semui:modal id="${tmplModalID}" text="${tmplText}"  isEditModal="isEditModal">
    <g:set var="ajaxID" value="${tmplID ?:'allOrgs'}"/>
    <g:form id="create_org_role_link" class="ui form" url="[controller:'ajax', action:'addOrgRole']" method="post" onsubmit="return validateAddOrgRole();">
        <input type="hidden" name="parent" value="${parent}" />
        <input type="hidden" name="property" value="${property}" />
        <input type="hidden" name="recip_prop" value="${recip_prop}" />

        <div class="field">
            <table id="org_role_tab_${tmplModalID}" class="ui celled la-table compact table">
                <thead>
                    <tr>
                        <th>${message(code:'template.orgLinksModal.name.label')}</th>
                        <th>${message(code:'template.orgLinksModal.select')}</th>
                    </tr>
                </thead>
                <tbody></tbody>
            </table>
        </div>

        <input type="hidden" name="orm_orgRole" value="${tmplRole?.id}" />
        <input type="hidden" name="linkType" value="${linkType}" />

    </g:form>
</semui:modal>

<g:javascript>
    var oOrTable;

    $(document).ready(function(){

        $('#add_org_head_row').empty()

        oOrTable = $('#org_role_tab_${tmplModalID}').dataTable( {
            'bAutoWidth':  true,
            "sScrollY":    "240px",
            "sAjaxSource": "<g:createLink controller="ajaxJson" action="getProvidersWithPrivateContacts" id="${ajaxID}" params="${[oid:"${contextOrg.class.name}:${contextOrg.id}"]}"/>",
            "bServerSide": true,
            "bProcessing": true,
            "bDestroy":    true,
            "bSort":       false,
            "sDom":        "frtiS",
            "oScroller": {
                "loadingIndicator": false
            },
            "aoColumnDefs": [ {
                "aTargets": [ 1 ],
                "mData": "DT_RowId",
                "mRender": function ( data, type, full ) {
                    return '<input type="checkbox" name="orm_orgOid" value="' + data + '"/>';
                }
            } ],
            "language": {
                "decimal":        "<g:message code='datatables.decimal' />",
                "emptyTable":     "<g:message code='datatables.emptyTable' />",
                "info":           "<g:message code='datatables.info' />",
                "infoEmpty":      "<g:message code='datatables.infoEmpty' />",
                "infoFiltered":   "<g:message code='datatables.infoFiltered' />",
                "infoPostFix":    "<g:message code='datatables.infoPostFix' />",
                "thousands":      "<g:message code='datatables.thousands' />",
                "lengthMenu":     "<g:message code='datatables.lengthMenu' />",
                "loadingRecords": "<g:message code='datatables.loadingRecords' />",
                "processing":     "<g:message code='datatables.processing' />",
                "search":         "<g:message code='datatables.search' />",
                "zeroRecords":    "<g:message code='datatables.zeroRecords' />",
                "paginate": {
                    "first":      "<g:message code='datatables.paginate.first' />",
                    "last":       "<g:message code='datatables.paginate.last' />",
                    "next":       "<g:message code='datatables.paginate.next' />",
                    "previous":   "<g:message code='datatables.paginate.previous' />"
                },
                "aria": {
                    "sortAscending":  "<g:message code='datatables.aria.sortAscending' />",
                    "sortDescending": "<g:message code='datatables.aria.sortDescending' />"
                }
            }
        } );

        oOrTable.fnAdjustColumnSizing();

    });

    function validateAddOrgRole() {
      if ( $('#orm_orgRole').val() == '' ) {
        return confirm("${message(code:'template.orgLinksModal.warn')}");
      }
      return true;
    }

</g:javascript>
