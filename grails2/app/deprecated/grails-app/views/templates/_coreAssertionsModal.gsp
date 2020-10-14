
<div name="coreAssertionEdit" class="modal hide">

  <div class="modal-header">
    <button type="button" class="close" data-dismiss="modal">×</button>
    <h3 class="ui header">${message(code:'template.coreAssertionsModal.label', args:[tip?.title?.title], default:"Core Dates for ${tip?.title?.title}")}</h3>
  </div>

  <div class="modal-body">

    <g:if test="${message}">
      <semui:msg class="warning" text="${message}" />
    </g:if>

    <p>${message(code:'template.coreAssertionsModal.note', default:'Edit existing core dates using the table below. Click the start and end dates to modify them and then the tick to accept your change. Once finished, click the Done button below')}</p>
    
    <table class="ui celled la-table table">
      <thead>
      <tr>
        <th>${message(code:'subscription.details.coreStartDate')}</th>
        <th>${message(code:'subscription.details.coreEndDate')}</th>
        <th>${message(code:'default.action.label')}</th>
      </tr>
      </thead>
      <tbody>
         <g:each in="${coreDates}" var="coreDate">
            <tr>
              <td>
                <semui:xEditable owner="${coreDate}" type="date" field="startDate" />
              </td>
              <td>
                <semui:xEditable owner="${coreDate}" type="date" field="endDate" />
              </td>
              <td>
              <g:if test="${editable == 'true' || editable == true}">
                <laser:remoteLink url="[controller: 'ajax', action: 'deleteCoreDate', params:[tipID:tipID,title:title,coreDateID:coreDate.id]]" method="get" name="show_core_assertion_modal"
                before="hideModal()" onComplete="showCoreAssertionModal()" data-update="magicArea" class="delete-coreDate">${message(code:'default.button.delete.label')} </laser:remoteLink>
                </g:if>
              </td>
            </tr>
         </g:each>
      </tbody>
    </table>


    <div class="well" style="word-break: normal;">
      <h4 class="ui header">${message(code:'template.coreAssertionsModal.addDate')}</h4>
      <p>${message(code:'template.coreAssertionsModal.addDate.note')}</p>
      
      <laser:remoteForm name="coreExtendForm" url="[controller: 'ajax', action: 'coreExtend']" data-before="hideModal()" data-always="showCoreAssertionModal()" data-update="magicArea">
        <input type="hidden" name="tipID" value="${tipID}"/>
        <input type="hidden" name="title" value="${title}"/>
        <table style="width:100%">
          <tr>
            <td>
              <label class="property-label">${message(code:'subscription.details.coreStartDate')}:</label>
              <semui:simpleHiddenValue  id="coreStartDate" name="coreStartDate" type="date"/>
            </td>
            <td>
             <label class="property-label">${message(code:'subscription.details.coreEndDate')}:</label>
              <semui:simpleHiddenValue id="coreEndDate" name="coreEndDate" type="date"/>
            </td>
            <td>
              <input type="submit" value="${message(code:'default.button.apply.label')}" class="ui button"/>&nbsp;
            </td>
          </tr>
        </table>
      </laser:remoteForm>
    </div>

  </div>

  <div class="modal-footer">
    <button type="button" data-dismiss="modal">${message(code:'default.done', default:'Done')}</button>
  </div>

</div>

<g:if test="${editable=='true' || editable == true}">
  <script>
    $('.xEditableValue').editable();
    $(".simpleHiddenRefdata").editable({
      url: function(params) {
        var hidden_field_id = $(this).data('hidden-id');
        $("#"+hidden_field_id).val(params.value);
        // Element has a data-hidden-id which is the hidden form property that should be set to the appropriate value
      }
    });
  </script>
</g:if>


