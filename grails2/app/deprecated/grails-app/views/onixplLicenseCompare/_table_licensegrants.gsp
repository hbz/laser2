<%@ page import="com.k_int.kbplus.onixpl.OnixPLService" %>
<g:each var="row_key,row" in="${data}" status="rowCount">
      <!-- Get the data we are to derive the title cell from -->
      <g:set var="rth" value="${service.getRowHeadingData(row)}" />
<g:if test="${OnixPLService.getAllValues(rth, 'LicenseGrantType', ', ')}">
  <tr>
    <!-- Header -->
    <td class="tr-${ (rowCount + 1) } cell-1" >
      <span class="cell-inner">
      Grants ${ OnixPLService.getAllValues(rth, 'LicenseGrantType', ', ') } license for
      ${ OnixPLService.getSingleValue(rth, 'LicenseGrantPurpose') }
        </span>
      </td>


    <g:each var="heading" in="${headings}" status="colCount">
      <g:set var="entry" value="${ row[heading] }" />
      <td class="tr-${ (rowCount + 1) } cell-${ colCount + 2 }">
        <g:if test="${ entry }" >
          <div class="onix-icons" >
            <g:if test="${ entry['TextElement'] }" >
              <g:render template="text" model="${ ["data" : entry['TextElement']] }" /> 
            </g:if>
            <g:if test="${ entry['Annotation'] }" >
              <g:render template="annotation" model="${ ["data" : entry['Annotation']] }" />
            </g:if>
          </div>
          <span class="cell-inner">
            <span title='Granted by license' class="onix-status onix-tick" ></span>
          </span>
        </g:if>
        <g:else>
          <span class="cell-inner-undefined">
            <span title='Not defined by the license' class="onix-status onix-pl-undefined" ></span>
          </span>
        </g:else>
      </td>
    </g:each>
  </tr>
  </g:if>

</g:each>