<%@ page import="de.laser.RefdataValue; de.laser.RefdataCategory; com.k_int.properties.PropertyDefinition" %>

<!doctype html>
<r:require module="scaffolding" />
<html>
<head>
    <meta name="layout" content="semanticUI">
    <g:set var="entityName" value="${message(code: 'propertyDefinition.label')}"/>
    <title><g:message code="default.edit.label" args="[entityName]"/></title>
</head>

<body>
<div>
    <div class="span3">
        <div class="well">
            <ul class="nav nav-list">
                <li class="nav-header">${entityName}</li>
                <li>
                    <g:link class="list" action="list">
                        <i class="icon-list"></i>
                        <g:message code="default.list.label" args="[entityName]"/>
                    </g:link>
                </li>
                <li>
                	<g:if test="${editable}">
                    	<g:link class="create" action="create">
                        	<i class="icon-plus"></i>
                        	<g:message code="default.create.label" args="[entityName]"/>
                    	</g:link>
                    </g:if>
                </li>
            </ul>
        </div>
    </div>

    <div>

            <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon /><g:message code="default.edit.label" args="[entityName]"/></h1>


        <semui:messages data="${flash}" />

            <semui:errors bean="${propDefInstance}" />

        <fieldset>
              <g:set var="usages" value="${propDefInstance.countOccurrences('com.k_int.kbplus.LicenseProperty','com.k_int.kbplus.OrgProperty')}" />
              <g:set var="usageOwner" value="${propDefInstance.getOccurrencesOwner('com.k_int.kbplus.LicenseProperty','com.k_int.kbplus.OrgProperty')}" />
            <g:form class="ui form" action="edit" id="${propDefInstance?.id}">
                <g:hiddenField name="version" value="${propDefInstance?.version}"/>
                <fieldset>
                    <div class="control-group ">
                        <label class="control-label" for="name">Name</label>
                        <div class="controls">
                            <input type="text"  <%= ( editable ) ? '' : 'disabled' %> name="name" value="${propDefInstance.name}" required="" id="name">
                            
                        </div>
                    </div>
                    <div class="control-group ">
                        <label class="control-label" for="descr">Context</label>
                        <div class="controls">
                        	<%--<g:select name="descr" disabled="${!editable}" value="${propDefInstance.descr}" from="${PropertyDefinition.AVAILABLE_CUSTOM_DESCR}" />--%>
                            <select name="descr" id="descr" disabled="${!editable}">
                                <g:each in="${PropertyDefinition.AVAILABLE_CUSTOM_DESCR}" var="pd">
                                    <g:if test="${propDefInstance.descr == pd}">
                                        <option value="${pd}" selected="selected"><g:message code="propertyDefinition.${pd}.label" default="${pd}"/></option>
                                    </g:if>
                                    <g:else>
                                        <option value="${pd}"><g:message code="propertyDefinition.${pd}.label" default="${pd}"/></option>
                                    </g:else>
                                </g:each>
                            </select>
                        </div>
                    </div>
                    <div class="control-group ">
                        <label class="control-label" for="type">Type</label>
                        <div class="controls">
                            <g:select name="type" disabled="${!editable}" value="${propDefInstance.type}"
                                      from="${PropertyDefinition.validTypes2.entrySet()}"
                                      optionKey="key" optionValue="${{PropertyDefinition.getLocalizedValue(it.key)}}" id="type"/>
                        </div>
                    </div>
                    <div class="control-group hide" id="cust_prop_ref_data_name">
                        <label class="control-label" for="refDataCategory">RefdataCategory</label>
                        <div class="controls">
                            <input type="hidden" <%= ( editable ) ? '' : 'disabled' %> name="refdataCategory" value="${propDefInstance.refdataCategory}"  id="refDataCategory"/>
                        </div>
                    </div>
                    <div class="control-group ">
                        <label class="control-label" for="name">Occurrences</label>
                        <div class="controls">
                            <input type="text" disabled="" value="${usages}"/>
                            
                        </div>
                    </div>
                    <div class="control-group ">
                        <label class="control-label" for="name">Occurrence Owners</label>
                        <div class="controls">
                            <div class="well" style="width: 300px">
                            <ul class="overflow-y-scroll" style="overflow:auto; max-height: 300px;">
                                <g:each in="${usageOwner}" var="${cls}">
                                    <g:each in="${cls}" var="${ownerInstance}">
                                        <li>${ownerInstance.getClass().getName()}:${ownerInstance.id}</li>
                                    </g:each>
                                </g:each>
                            </ul>
                            </div>
                        </div>
                    </div>
                    <div class="ui form-actions">
                        <g:if test="${editable}">
                          <button type="submit" <%= ( ( usages == 0  ) ) ? '' : 'disabled' %> class="ui negative button" name="_action_delete" formnovalidate>
                              <i class="trash alternate icon"></i>
                              <g:message code="default.button.delete.label"/>
                          </button>
                        </g:if>
                        <g:else>
                          You do not have permission to delete properties
                        </g:else>
                    </div>
                </fieldset>
            </g:form>
        </fieldset>
    </div>

</div>
</body>

<g:javascript>
    console.log("${propDefInstance.refdataCategory}")
    //Runs if type edited is Refdata
    if( $("#type option:selected").val() == "${RefdataValue.CLASS}") {
        $("#cust_prop_ref_data_name").show();
   

    //Runs everytime type is changed
    $('#type').change(function() {
        var selectedText = $("#type option:selected").val();
        if( selectedText == "${RefdataValue.CLASS}") {
            $("#cust_prop_ref_data_name").show();
        }else{
            $("#cust_prop_ref_data_name").hide();
        }
    });

     $("#refDataCategory").select2({
        placeholder: "Type category...",
        initSelection : function (element, callback) {
        <g:if test="${propDefInstance.refdataCategory}">
                var data = {id: -1, text: "${propDefInstance.refdataCategory}"};
                callback(data);
            </g:if>
        },
        minimumInputLength: 1,
        ajax: { // instead of writing the function to execute the request we use Select2's convenient helper
            url: '${createLink(controller:'ajax', action:'lookup')}',

            dataType: 'json',
            data: function (term, page) {
              return {
                q: term, // search term
                page_limit: 10,
                baseClass:'${RefdataCategory.class.name}'
              };
            },
            results: function (data, page) {
                return {results: data.values};
            }
        }
    });
</g:javascript>
</html>
