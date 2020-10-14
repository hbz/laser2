<%@ page import="de.laser.SubscriptionPackage; de.laser.IssueEntitlement; de.laser.helper.RDConstants; de.laser.RefdataValue" %>
<laser:serviceInjection />

<!doctype html>
<html>
<!-- deprecated -->
  <head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code:'laser')} : ${institution.name} :: Financial Information</title>
  </head>

  <body>

    <div>
      <ul class="breadcrumb">
        <li> <g:link controller="home" action="index">Home</g:link> <span class="divider">/</span> </li>
        <li> <g:link controller="myInstitution" action="finance">${institution.name} Finance</g:link> </li>
      </ul>
    </div>

    <div>
      <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />${institution.name} Cost Items</h1>
      <g:form action="index" class="ui form" method="post">
        <input type="hidden" name="shortcode" value="${contextService.getOrg().shortcode}"/>
        <table class="ui celled la-table table table table-tworow">
          <thead>
          <tr><td colspan="9">&nbsp;</td></tr>
          <tr>
              <td rowspan="2">Add new cost item</td>
              <td><input type="text" name="newInvoiceNumber" class="input-medium"
                         placeholder="New item invoice #" id="newInvoiceNumber" value="${params.newInvoiceNumber}"/></td>
              <td><input type="text" name="newOrderNumber" class="input-medium"
                         placeholder="New Order #" id="newOrderNumber" value="${params.newOrderNumber}"/></td>
              <td>
                  <select name="newSubscription" class="input-medium" id="newSubscription" value="${params.newSubscription}">
                      <option value="all">Not Set</option>
                      <g:each in="${institutionSubscriptions}" var="s">
                          <option value="${s.id}" ${s.id==params.long('newSubscription')?'selected="selected"':''}>${s.name}</option>
                      </g:each>
                  </select>
              </td>
              <td>
                  <select name="newPackage" class="input-medium" id="newPackage" value="${params.newPackage}">
                      <option value="">Not Set</option>
                  </select>
              </td>
              <td>
                  <input name="newIe" class="input-medium" id="newIE" value="${params.newIe}">
              </td>
              <td rowspan="2"><button class="ui button" type="submit" name="Add" value="add">${message('code':'default.button.add.label')}</button></td>
          </tr>
          <tr>
              <td>
                  <h3 class="ui header">Cost date and status</h3>
                  <input type="date" name="newDatePaid" value="${params.newDatePaid}"/><br/>

                  <g:select name="newCostItemStatus"
                            from="${RefdataValue.executeQuery('select rdv from RefdataValue as rdv where rdv.owner.desc = :desc', [desc: RDConstants.COST_ITEM_STATUS])}"
                            optionKey="id"
                            noSelection="${['':'No Status']}"/>

                  <g:select name="newCostItemCategory"
                            from="${RefdataValue.executeQuery('select rdv from RefdataValue as rdv where rdv.owner.desc = :desc', [desc: RDConstants.COST_ITEM_CATEGORY])}"
                            optionKey="id"
                            noSelection="${['':'No Category']}"/>

                  <g:select name="newCostItemElement"
                            from="${RefdataValue.executeQuery('select rdv from RefdataValue as rdv where rdv.owner.desc = :desc', [desc: RDConstants.COST_ITEM_ELEMENT])}"
                            optionKey="id"
                            noSelection="${['':'No Element']}"/>

                  <g:select name="newCostCurrency"
                            from="${RefdataValue.executeQuery('select rdv from RefdataValue as rdv where rdv.owner.desc = :desc', [desc: RDConstants.CURRENCY])}"
                            optionKey="id"
                            noSelection="${['':'No Currency']}"/>

                  <g:select name="newCostTaxType"
                            from="${RefdataValue.executeQuery('select rdv from RefdataValue as rdv where rdv.owner.desc = :desc', [desc: RDConstants.TAX_TYPE])}"
                            optionKey="id"
                            noSelection="${['':'No Tax Type']}"/>
              </td>
              <td>
                  <h3 class="ui header">Cost values and tax</h3>
                  <input type="number" name="newCostInBillingCurrency" placeholder="New Cost Ex-Tax - Billing Currency" id="newCostInBillingCurrency" step="0.01"/> <br/>
                  <input type="number" name="newCostCurrencyRate" placeholder="Exchange Rate" id="newCostCurrencyRate" step="0.01"/> <br/>
                  <input type="number" name="newCostInLocalCurrency" placeholder="New Cost Ex-Tax - Local Currency" id="newCostInLocalCurrency" step="0.01"/>
                  <input type="number" name="newCostTaxRate" placeholder="New Cost Tax Rate" id="newCostTaxRate" step="0.01"/>
                  <input type="number" name="newCostTaxAmount" placeholder="New Cost Tax Amount" id="newCostTaxAmount" step="0.01"/>
              </td>
              <td>
                  <h3 class="ui header">Reference</h3>
                  <input type="text" name="newReference" placeholder="New Item Reference" id="newCostItemReference" value="${params.newReference}"/><br/>
                  <input type="text" name="newBudgetCode" placeholder="New Item Budget Code" id="newBudgetCode" ></td>
              <td colspan="2">
                  <h3 class="ui header">Description</h3>
                  <textarea name="newDescription"
                            placeholder="New Item Description" id="newCostItemDescription"/></textarea>
              </td>
          </tr>

          </thead>
          <tbody>
           <tr><td colspan="9">&nbsp;</td></tr>
            <g:if test="${cost_item_count==0}">
              <tr><td colspan="7" style="text-align:center">&nbsp;<br/>No Cost Items Found<br/>&nbsp;</td></tr>
            </g:if>
            <g:else>
              <g:each in="${cost_items}" var="ci">
                <tr>
                  <td rowspan="2">${ci.id}</td>
                  <td>${ci.invoice?.invoiceNumber}</td>
                  <td>${ci.order?.orderNumber}</td>
                  <td>${ci.sub?.name}</td>
                  <td>${ci.subPkg?.name}</td>
                  <td colspan="2">${ci.issueEntitlement?.id}</td>
                </tr>
                <tr>
                  <td>${ci.datePaid}</td>
                  <td>${ci.costInBillingCurrency} ${ci.billingCurrency?.value} / ${ci.costInLocalCurrency}</td>
                  <td>${ci.reference}</td>
                  <td colspan="3">${ci.costDescription}</td>
                </tr>
              </g:each>
            </g:else>
          </tbody>
          <tfoot>

          </tfoot>
        </table>
      </g:form>

    </div>
  </body>

  <r:script>

    function filtersUpdated() {
      $('#newInvoiceNumber').val($('#filterInvoiceNumber').val());
      $('#newOrderNumber').val($('#filterOrderNumber').val());
      $('#newSubscription').val($('#filterSubscription').val());
      $('#newPackage').val($('#filterPackage').val());
    }

    function filterSubUpdated() {
      // Fetch packages for the selected subscription
      var selectedSub = $('#filterSubscription').val();

      $.ajax({
        url: "<g:createLink controller='ajaxJson' action='lookup'/>",
        data: {
          format:'json',
          subFilter:selectedSub,
          baseClass:'${SubscriptionPackage.class.name}'
        },
        dataType:'json'
      }).done(function(data) {
        console.log("%o",data);
        $('#filterPackage').children().remove()
        $('#filterPackage').append('<option value="xx">Not specified</option>');
        var numValues = data.values.length;
        for (var i = 0; i != numValues; i++) {
          $('#filterPackage').append('<option value="'+data.values[i].id+'">'+data.values[i].text+'</option>');
        }
      });


      filtersUpdated();
    }

  $(document).ready(function() {

    $("#newIE").select2({
      placeholder: "Identifier..",
      minimumInputLength: 1,
      ajax: { // instead of writing the function to execute the request we use Select2's convenient helper
        url: "<g:createLink controller='ajaxJson' action='lookup'/>",
        dataType: 'json',
        data: function (term, page) {
            return {
                format:'json',
                q: term,
                subFilter: $('#newSubscription').val(),
                baseClass:'${IssueEntitlement.class.name}'
            };
        },
        results: function (data, page) {
          return {results: data.values};
        }
      }
    });

   $('#newDatePaid').datepicker({
      format:"${message(code:'default.date.format.notime').toLowerCase()}",
      language:"${message(code:'default.locale.label')}",
      autoclose:true
    });
  });



  </r:script>
</html>
