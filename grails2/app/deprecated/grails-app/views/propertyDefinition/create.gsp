<!doctype html>
<r:require module="scaffolding" />
<%@ page import="de.laser.RefdataValue; de.laser.RefdataCategory; com.k_int.properties.PropertyDefinition" %>

<html>
  <head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code:'laser')} Manage Custom Properties</title>
  </head>
     <g:set var="entityName" value="${message(code: 'propertyDefinition.label')}"/>
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
                    <g:link class="create" action="create">
                        <i class="icon-plus"></i>
                        <g:message code="default.create.label" args="[entityName]"/>
                    </g:link>
                </li>
            </ul>
        </div>
    </div>

    <div class="span9">

            <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon /><g:message code="default.create.label" args="[entityName]"/></h1>
        <semui:messages data="${flash}" />

        <semui:errors bean="${newProp}" />
   

      <p>Use the following form to create additional property definitions. Property definition names are unique.</p>
       <g:form id="create_cust_prop" url="[controller: 'ajax', action: 'addCustomPropertyType']" >
          <input type="hidden" name="redirect" value="yes"/>
          <input type="hidden" name="ownerClass" value="${this.class}"/>

          <div class="modal-body">
              <dl>
                  <dt><label class="control-label">New Property Definition:</label></dt>
                  <dd>
                      <label class="property-label">Name:</label> <input type="text" name="cust_prop_name"/>
                  </dd>
                  <dd>
                      <label class="property-label">Type:</label> <g:select 
                          from="${PropertyDefinition.validTypes2.entrySet()}"
                                  optionKey="key" optionValue="${{PropertyDefinition.getLocalizedValue(it.key)}}"
                                  name="cust_prop_type"
                                  id="cust_prop_modal_select" />
                  </dd>

                  <div class="hide" id="cust_prop_ref_data_name">
                      <dd>
                          <label class="property-label">Refdata Category:</label> 
                          <input type="hidden" name="refdatacategory" id="cust_prop_refdatacatsearch"/>
                      </dd>
                  </div>
                  <dd>
                      <label class="property-label">Context:</label> 
                         <%--<g:select name="cust_prop_desc" from="${PropertyDefinition.AVAILABLE_CUSTOM_DESCR}" />--%>
                          <select name="cust_prop_desc" id="cust_prop_desc">
                              <g:each in="${PropertyDefinition.AVAILABLE_CUSTOM_DESCR}" var="pd">
                                  <option value="${pd}"><g:message code="propertyDefinition.${pd}.label" default="${pd}"/></option>
                              </g:each>
                          </select>

                  </dd>
                  <button type="submit" class="ui positive button">
                    <i class="checkmark icon"></i>
                    Create Property
                </button>
              </dl>
          </div>
          </g:form>

    </div>

</div>
  </body>
 
  <g:javascript>
    function chk(n1,n2) {
      if ( n1===0 && n2 ===0 ) {
        return true;
      }
      else {
        return confirm("Deleting this property will also delete "+n1+" License Value[s] and "+n2+" Subscription Value[s]. Are you sure you want to HARD delete these values? Deletions will NOT be recoverable!");
      }
      return false;
    }

       if( $( "#cust_prop_modal_select option:selected" ).val() == "${RefdataValue.CLASS}") {
            $("#cust_prop_ref_data_name").show();
       }

    $('#cust_prop_modal_select').change(function() {
        var selectedText = $( "#cust_prop_modal_select option:selected" ).val();
        if( selectedText == "${RefdataValue.CLASS}") {
            $("#cust_prop_ref_data_name").show();
        }else{
            $("#cust_prop_ref_data_name").hide();
        }
    });

    $("#cust_prop_refdatacatsearch").select2({
        placeholder: "Type category...",
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
