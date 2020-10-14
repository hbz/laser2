<%@ page import="de.laser.oap.OrgAccessPoint; de.laser.helper.RDConstants" %>
<div class="field required">
    <label>${message(code: 'accessMethod.label')}</label>
    <%--<laser:select class="ui dropdown" id="accessMethod" name="accessMethod"
                  from="${OrgAccessPoint.getAllRefdataValues(RDConstants.ACCESS_POINT_TYPE)}"
                  optionKey="value"
                  optionValue="value"
                  value="${accessMethod}"
                  onchange="${remoteFunction (
                          controller: 'accessPoint',
                          action: 'create',
                          params: "'template=' + this.value",
                          update: 'details',
                  )}"
    /> --%>
    <laser:select class="ui dropdown" id="accessMethod" name="accessMethod"
                  from="${OrgAccessPoint.getAllRefdataValues(RDConstants.ACCESS_POINT_TYPE)}"
                  optionKey="value"
                  optionValue="value"
                  value="${accessMethod}"
                  onchange="${laser.remoteJsOnChangeHandler(
                          controller: 'accessPoint',
                          action: 'create',
                          data: '{template:this.value}',
                          update: '#details',
                  )}"
    />
</div>

