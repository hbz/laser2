<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="com.k_int.kbplus.onixpl.OnixPLService" %>
<!doctype html>
<r:require module="scaffolding" />
<html>
<head>
<meta name="layout" content="semanticUI" />
<title>${message(code:'laser')} : ${message(code:'onixplLicense.compare.label')}</title>

</head>

<body>

<semui:breadcrumbs>
	<semui:crumb message="menu.institutions.comp_onix" class="active" />
</semui:breadcrumbs>
<br>
	<h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />${message(code:'menu.institutions.comp_onix')}</h1>

	<div>
		<div class="row">
			<div class="span8">
				<g:form id="compare" name="compare" action="matrix" method="post">
					<div>
						<label for="addIdentifierSelect">${message(code:'onixplLicense.compare.add_id.label')}</label>

		                <input type="hidden" name="selectedIdentifier" id="addIdentifierSelect"/>
		                <button type="button" class="ui positive button" id="addToList" style="margin-top:10px">${message(code:'default.button.add.label')}</button>
					</div>
					
					<label for="selectedLicenses">${message(code:'onixplLicense.compare.selected.label', default:'Licenses selected for comparison:')}</label>
					<g:select style="width:90%; word-wrap: break-word;" id="selectedLicenses" name="selectedLicenses" class="compare-license" from="${[]}" multiple="true" />


					<div>
						<label for="section">${message(code:'onixplLicense.compare.section.label', default:'Compare section:')}</label>
						<g:treeSelect name="sections" id="section" class="compare-section"
							options="${termList}" selected="true" multiple="true" />
					</div>

				<div class="fields">
                  	<div class="field">
                  		<a href="${request.forwardURI}" class="ui button">${message(code:'default.button.comparereset.label')}</a>
                    </div>
					<div class="field">
					  <input id="submitButton" disabled='true' type="submit" value="${message(code:'default.button.compare.label')}"  name="Compare" class="ui button" />
					</div>
				</div>
				</g:form>
			</div>
		</div>
	</div>
	  <r:script language="JavaScript">


	    $(function(){

	      var main = $('#selectedLicenses');
	  
	      // Now add the onchange.
	      main.change(function() {
	        var conceptName = main.find(":selected");
	        if(conceptName != null){
	        	$('#submitButton').removeAttr('disabled')
	        }
	      });

	      $('#addToList').click(function() {
	      		var option = $("input[name='selectedIdentifier']").val()
	      		var option_name = option.split("||")[0]
	      		var option_id = option.split("||") [1]
	      		var list_option = "<option selected='selected' value='"+option_id+"''>"+option_name+"</option>"
	      		$("#selectedLicenses").append(list_option)
	      		$('#selectedLicenses').trigger( "change" )
			});

	      $("#addIdentifierSelect").select2({
  	        width: '90%',
	        placeholder: "${message(code:'onixplLicense.compare.search.ph')}",
	        minimumInputLength: 1,
                formatInputTooShort: function () {
                    return "${message(code:'select2.minChars.note')}";
                },
	        ajax: { // instead of writing the function to execute the request we use Select2's convenient helper
	          url: "<g:createLink controller='ajax' action='lookup'/>",
	          dataType: 'json',
	          data: function (term, page) {
	              return {
	                  q: "%" + term + "%", // search term
	                  page_limit: 10,
	                  baseClass:'com.k_int.kbplus.OnixplLicense'
	              };
	          },
	          results: function (data, page) {
	            return {results: data.values};
	          },
	        }
	      });
	    });
      </r:script>
</body>
</html>
