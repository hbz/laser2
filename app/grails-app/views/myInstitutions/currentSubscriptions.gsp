<!doctype html>
<r:require module="annotations" />

<html>
  <head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code:'laser', default:'LAS:eR')} ${institution.name} - ${message(code:'myinst.currentSubscriptions.label', default:'Current Subscriptions')}</title>
  </head>
    <body>
        <semui:breadcrumbs>
            <semui:crumb controller="myInstitutions" action="dashboard" params="${[shortcode:institution.shortcode]}" text="${institution.name}" />
            <semui:crumb message="myinst.currentSubscriptions.label" class="active" />
        </semui:breadcrumbs>

        <g:if test="${editable}">
            <semui:crumbAsBadge message="default.editable" class="orange" />
        </g:if>

        <semui:messages data="${flash}" />

        <h1 class="ui header">${institution?.name} - ${message(code:'myinst.currentSubscriptions.label', default:'Current Subscriptions')}</h1>

      <g:render template="subsNav" contextPath="." />

      <semui:filter>

        <g:form action="currentSubscriptions" params="${[shortcode:institution.shortcode]}" controller="myInstitutions" method="get" class="form-inline ui form">
            <div class="fields">
                <!-- SEARCH -->
                <div class="field">

                    <label>${message(code: 'default.search.text', default: 'Search text')}:</label>
                    <div class="ui  input">

                    <input type="text" name="q"
                           placeholder="${message(code: 'default.search.ph', default: 'enter search term...')}"
                           value="${params.q?.encodeAsHTML()}"/>
                    </div>

                </div>

                <!-- SEARCH END -->

                <!-- DATE VALID ON -->
                <div class="field">
                    <label>${message(code:'default.valid_on.label', default:'Valid On')}: </label>
                    <div class="ui calendar" id="valid-on">
                        <div class="ui input left icon">
                            <i class="calendar icon"></i>
                            <input name="validOn" type="text" placeholder="Date" value="${validOn}">
                        </div>
                    </div>
                </div>
                <!-- DATE VALID ON END-->

                <!-- DROPDOWN DATE -->
                <div class="field">
                    <label class="control-label">${message(code:'default.filter.label', default:'Filter')}: </label>
                    <g:set var="noDate" value="${message(code:'default.filter.date.none', default:'-None-')}" />
                    <g:set var="renewalDate" value="${message(code:'default.renewalDate.label', default:'Renewal Date')}" />
                    <g:set var="endDate" value="${message(code:'default.endDate.label', default:'End Date')}" />
                    <g:select class="ui dropdown"
                              name="dateBeforeFilter"
                              value="${params.dateBeforeFilter}"
                              from="${['renewalDate' : renewalDate, 'endDate' : endDate]}"
                              noSelection="${['null' : noDate]}"
                              optionKey="key"
                              optionValue="value" />
                </div>
                <!-- DROPDOWN DATE END -->

                <!-- dateBeforeVal -->
                <div class="field">
                    <label >${message(code:'myinst.currentSubscriptions.filter.before', default:'before')}</label>
                    <div class="ui calendar" id="date-before-val">
                        <div class="ui input left icon">
                            <i class="calendar icon"></i>
                            <input  type="text" name="dateBeforeVal" value="${params.dateBeforeVal}">
                        </div>
                    </div>
                </div>
                <!-- dateBeforeVal -->

               <!-- SEND-BUTTON -->
                <div class="field">

                        <input type="submit" class="ui primary button la-nolabel" value="${message(code:'default.button.search.label', default:'Search')}" />
                </div>
                <!-- SEND-BUTTON END-->
            </div>
        </g:form>
      </semui:filter>



      <div class="subscription-results">
        <table class="ui celled striped table table-tworow">
          <thead>
            <tr>
              <g:sortableColumn colspan="7" params="${params}" property="s.name" title="${message(code:'license.slash.name')}" />
              <th rowspan="2">${message(code:'default.action.label', default:'Action')}</th>
            </tr>

            <tr>
                <th><g:annotatedLabel owner="${institution}" property="linkedPackages">${message(code:'license.details.linked_pkg', default:'Linked Packages')}</g:annotatedLabel></th>
                <th>${message(code:'consortium.plural', default:'Consortia')}</th>
                <g:sortableColumn params="${params}" property="s.startDate" title="${message(code:'default.startDate.label', default:'Start Date')}" />
                <g:sortableColumn params="${params}" property="s.endDate" title="${message(code:'default.endDate.label', default:'End Date')}" />
                <g:sortableColumn params="${params}" property="s.manualRenewalDate" title="${message(code:'default.renewalDate.label', default:'Renewal Date')}" />
                <g:sortableColumn params="${params}" property="s.manualCancellationDate" title="${message(code:'default.cancellationDate.label', default:'Cancellation Date')}" />
            </tr>
          </thead>
          <g:each in="${subscriptions}" var="s">
            <tr>
              <td colspan="7">
                <g:link controller="subscriptionDetails" action="details" params="${[shortcode:institution.shortcode]}" id="${s.id}">
                  <div class="ui list">
                    <div class="item">
                      <i class="handshake icon"></i>
                        <div class="content">
                          <g:if test="${s.name}">${s.name}</g:if><g:else>-- ${message(code:'myinst.currentSubscriptions.name_not_set', default:'Name Not Set')}  --</g:else>
                          <g:if test="${s.instanceOf}">(${message(code:'subscription.isInstanceOf.label', default:'Dependent')}<g:if test="${s.consortia && s.consortia == institution}">: ${s.subscriber?.name}</g:if>)</g:if>
                        </div>
                    </div>
                  </div>
                </g:link>
                <g:if test="${s.owner}">
                  <span class="pull-right">
                    <div class="ui list">
                      <div class="item">
                        <i class="law icon"></i>
                        <div class="content">
                          ${message(code:'license')} : <g:link controller="licenseDetails" action="index" id="${s.owner.id}">${s.owner?.reference}</g:link>
                        </div>
                      </div>
                    </div>
                  </span>
                </g:if>
              </td>
              <td rowspan="2">
                <g:if test="${ editable && ( (institution in s.allSubscribers) || s.consortia == institution )}">
                    <g:link controller="myInstitutions" action="actionCurrentSubscriptions" params="${[shortcode:institution.shortcode,curInst:institution.id,basesubscription:s.id]}" onclick="return confirm($message(code:'licence.details.delete.confirm', args:[(s.name?:'this subscription')})" class="ui negative button">${message(code:'default.button.delete.label', default:'Delete')}</g:link>
                </g:if>
              </td>
            </tr>
            <tr>
              <td>
                  <g:each in="${s.packages}" var="sp" status="ind">
                    <g:if test="${ind < 10}">
                          <g:link controller="packageDetails" action="show" id="${sp.pkg?.id}" title="${sp.pkg?.contentProvider?.name}">
                            <div class="ui list">
                              <div class="item">
                                <i class="archive icon"></i>
                                <div class="content">
                                    ${sp.pkg.name}
                                </div>
                              </div>
                            </div>
                          </g:link>
                    </g:if>
                  </g:each>
                  <g:if test="${s.packages.size() > 10}">
                    <div>${message(code:'myinst.currentSubscriptions.etc.label', args:[s.packages.size() - 10])}</div>
                  </g:if>

                <g:if test="${editable && (s.packages==null || s.packages.size()==0)}">
                  <i>${message(code:'myinst.currentSubscriptions.no_links', default:'None currently, Add packages via')} <g:link controller="subscriptionDetails" action="linkPackage" id="${s.id}">${message(code:'subscription.details.linkPackage.label', default:'Link Package')}</g:link></i>
                </g:if>
                &nbsp;<br/>
                &nbsp;<br/>
              </td>
              <td>${s.consortia?.name}</td>
              <td><g:formatDate formatName="default.date.format.notime" date="${s.startDate}"/></td>
              <td><g:formatDate formatName="default.date.format.notime" date="${s.endDate}"/></td>
              <td><g:formatDate formatName="default.date.format.notime" date="${s.renewalDate}"/></td>
              <td><g:formatDate formatName="default.date.format.notime" date="${s.manualCancellationDate}"/></td>
            </tr>
          </g:each>
        </table>
      </div>

  

        <g:if test="${subscriptions}" >
          <semui:paginate  action="currentSubscriptions" controller="myInstitutions" params="${params}" next="${message(code:'default.paginate.next', default:'Next')}" prev="${message(code:'default.paginate.prev', default:'Prev')}" max="${max}" total="${num_sub_rows}" />
        </g:if>

    <r:script type="text/javascript">
        $(document).ready(function(){
            var val = "${params.dateBeforeFilter}";
            if(val == "null"){
                $(".dateBefore").addClass("hidden");
            }else{
                $(".dateBefore").removeClass("hidden");
            }
        });

        $("[name='dateBeforeFilter']").change(function(){
            var val = $(this)['context']['selectedOptions'][0]['label'];

            if(val != "${message(code:'default.filter.date.none', default:'-None-')}"){
                $(".dateBefore").removeClass("hidden");
            }else{
                $(".dateBefore").addClass("hidden");
            }
        })
    </r:script>


    <r:script>
        $('.ui.dropdown').dropdown();
        $('#valid-on').calendar({
            type: 'date',
            firstDayOfWeek: 1,
            formatter: {
                date: function (date, settings) {
                    if (!date) return '';
                    var day = date.getDate();
                    var month = date.getMonth() + 1;
                    var year = date.getFullYear();
                    return day + '.' + month + '.' + year;
                }
            }
        });

        $('#date-before-val').calendar({
            type: 'date',
            firstDayOfWeek: 1,
            formatter: {
                date: function (date, settings) {
                    if (!date) return '';
                    var day = date.getDate();
                    var month = date.getMonth() + 1;
                    var year = date.getFullYear();
                    return day + '.' + month + '.' + year;
                }
            }
        });



    </r:script>

  </body>
</html>
