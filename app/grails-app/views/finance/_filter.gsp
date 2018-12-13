<!-- _filter.gsp -->
<%@ page import="com.k_int.kbplus.OrgRole;com.k_int.kbplus.RefdataCategory;com.k_int.kbplus.RefdataValue;com.k_int.properties.PropertyDefinition;com.k_int.kbplus.FinanceController" %>
<laser:serviceInjection />

<g:if test="${false}"><!-- TMP::IGNORE LEGACY FILTER -->

%{--AJAX rendered messages--}%
<g:if test="${info}">
    <div id="info" >
        <table id="financeErrors" class="ui striped celled table">
            <thead>
            <tr>
                <th>Problem/Update</th>
                <th>Info</th>
            </tr>
            </thead>
            <tbody>
            <g:each in="${info}" var="i">
                <tr>
                    <td>${i.status}</td>
                    <td>${i.msg}</td>
                </tr>
            </g:each>
            </tbody>
        </table>
    </div>
</g:if>

%{--Basic static help text--}%
<g:render template="help" />

<semui:filter>
    <g:form id="filterView" class="ui form" action="index" method="post">
        <input type="hidden" name="shortcode" value="${contextService.getOrg()?.shortcode}"/>

        <!-- row1 -->
        <div class="three fields">
            <div class="field">
                <label for="adv_codes">${message(code:'financials.budgetCode')}</label>
                <input id="adv_codes" name="adv_codes" type="text"/>
            </div>
            <div class="field">
                <label for="adv_costItemCategory">${message(code:'financials.costItemCategory')}</label>

                <laser:select id="adv_costItemCategory" class="ui dropdown"
                          name="adv_costItemCategory"
                          from="${costItemCategory}"
                          optionKey="id"
                          optionValue="value"
                          noSelection="${['':'Alle ..']}"/>
            </div>
            <div class="field required">

                <label>${message(code:'subscription.label')}</label>
                <g:if test="${inSubMode == true}">
                    <input name="subscriptionFilter" id="subscriptionFilter" class="la-full-width" value="${fixedSubscription?.name}" disabled="disabled"
                           data-filterMode="${fixedSubscription.class.name}:${fixedSubscription.id}"  />
                </g:if>
                <g:else>
                    <input type="text" name="subscriptionFilter" class="la-full-width" data-filterMode="" id="subscriptionFilter" value="${params.subscriptionFilter}" />
                </g:else>

                <g:hiddenField name="sub" value="${fixedSubscription?.id}"></g:hiddenField>
            </div>
        </div>

        <!-- row2 -->
        <div class="three fields">
            <div class="field required">
                <label>${message(code:'financials.invoice_number')}</label><!-- invoice -->
                <input id="filterInvoiceNumber" name="invoiceNumberFilter"
                       type="text" class="filterUpdated"
                       value="${params.invoiceNumberFilter}" />
            </div>

            <div class="field">
                <label for="adv_costItemStatus">${message(code:'financials.costItemStatus')}</label>
                <laser:select id="adv_costItemStatus" class="ui dropdown"
                          name="adv_costItemStatus"
                          from="${costItemStatus}"
                          optionKey="id"
                          optionValue="value"
                          noSelection="${['':'Alle ..']}"/>
            </div>
            <div class="field required">
                <label>${message(code:'package.label')}</label>
                <input type="text" name="packageFilter" class="filterUpdated la-full-width" id="packageFilter" value="${params.packageFilter}" />
            </div>
        </div>

        <!-- row3 -->
        <div class="three fields">
            <div class="field required">
                <label>${message(code:'financials.order_number')}</label>
                <input type="text" name="orderNumberFilter"
                       class="filterUpdated"
                       id="filterOrderNumber"  value="${params.orderNumberFilter}" data-type="select"/>
            </div>

            <div class="field">
                <label>Steuer</label>
                <laser:select id="taxCode" name="taxCode" class="ui dropdown" disabled="disabled"
                          from="${taxType}"
                          optionKey="id"
                          optionValue="value"
                          noSelection="${['':'Alle ..']}"/>
            </div>

            <div class="field">
                <label for="adv_ie">${message(code:'financials.newCosts.singleEntitlement')}</label>
                <input id="adv_ie" name="adv_ie" class="input-large" type="text" disabled="disabled" />
            </div>
        </div>

        <!-- row4 -->
        <div class="three fields">
            <div class="field">
            </div>
            <div class="field">

            </div>
            <div class="two fields">
                <div class="field">
                    <%--<span ${wildcard && filterMode=='ON'? hidden="hidden" : ''}>
                        (${g.message(code: 'financials.help.wildcard')} : <g:checkBox name="wildcard" title="${g.message(code: 'financials.wildcard.title')}" type="checkbox" value="${wildcard}"></g:checkBox> )
                    </span>--%>
                    <input type="hidden" name="wildcard" value="on" />
                    <label>&nbsp;</label>
                    <div id="filtering" data-toggle="buttons-radio">
                        <g:if test="${filterMode=='OFF'}">
                            <g:select name="filterMode" from="['OFF','ON']" type="button" class="ui button"></g:select>
                        </g:if>
                        <g:hiddenField type="hidden" name="resetMode" value="${params.resetMode}"></g:hiddenField>
                        <%--<g:submitButton name="submitFilterMode" id="submitFilterMode" class="ui button"  value="${filterMode=='ON'?'reset':'search'}" title="${g.message(code: 'financials.pagination.title')}"></g:submitButton>--%>
                    </div>
                </div>
                <div class="field">
                    <label>&nbsp;</label>
                    <g:submitButton name="submitFilterMode" id="submitFilterMode" class="ui secondary button" value="${filterMode=='ON'?'reset':'search'}" title="${g.message(code: 'financials.pagination.title')}" />
                </div>
            </div>
        </div>

        <%-- advanced legacy filter fields here --%>
        <%-- advanced legacy filter fields here --%>
        <%-- advanced legacy filter fields here --%>

        <%--
        <div class="three fields">
            <div class="two fields">
                <div class="field">
                    <label for="adv_datePaid">Date Paid</label>
                    <select name="_adv_datePaidType" class="input-mini"  id="adv_datePaidType">
                        <option value="">N/A</option>
                        <option value="eq">==</option>
                        <option value="gt">&gt;</option>
                        <option value="gt">&lt;</option>
                    </select>
                </div>
                <semui:datepicker label="financials.datePaid" name="newDate" placeholder ="financials.datePaid" value="${params.newDate}" />
            </div>
        </div>

        <div class="three fields">
            <div class="two fields">
                <div class="field">
                    <label for="adv_amount">Local Amount </label>
                    <select name="_adv_amountType" class="input-mini"  id="adv_amountType">
                        <option value="">N/A</option>
                        <option value="eq">==</option>
                        <option value="gt">&gt;</option>
                        <option value="gt">&lt;</option>
                    </select>
                </div>
                <div class="field">
                    <label>&nbsp;</label>
                    <input id="adv_amount" name="adv_amount" type="number" step="0.01" />
                </div>
            </div>
        </div>

        <div class="three fields">
            <div class="two fields">
                <semui:datepicker label ="datamanager.changeLog.from_date" name="newStartDate" placeholder ="default.date.label" />
                <semui:datepicker label ="datamanager.changeLog.to_date" name="newEndDate" placeholder ="default.date.label" value ="${params.endDate}" />
            </div>

            <div class="field">
                <label for="adv_ref">Cost Reference</label>
                <input id="adv_ref" name="adv_ref" />
            </div>
        </div>
        --%>

    </g:form>

</semui:filter>

</g:if><!-- TMP::IGNORE LEGACY FILTER -->

    <semui:filter>
        <%
            def formUrl = [controller: 'myInstitution', action: 'finance']

            if (fixedSubscription) {
                formUrl = [mapping: 'subfinance', params: [sub: "${fixedSubscription?.id}"]]
            }
        %>

        <g:form url="${formUrl}" method="get" class="ui form">

            <div class="three fields">
                <div class="field">
                    <label for="filterCITitle">${message(code:'financials.newCosts.costTitle')}</label>
                    <input id="filterCITitle" name="filterCITitle" type="text" value="${params.filterCITitle}"/>
                </div>

                <div class="field fieldcontain"><!--NEW -->
                    <label for="filterCISub">${message(code:'subscription.label')}</label>
                    <g:select id="filterCISub" class="ui dropdown selection"
                              name="filterCISub"
                              from="${allCISubs}"
                              optionValue="${{it.name ?: 'Keine Verknüpfung'}}"
                              optionKey="${{"com.k_int.kbplus.Subscription:" + it.id}}"
                              noSelection="['':'']"
                              value="${params.filterCISub}" />
                </div>

                <div class="field fieldcontain"><!--NEW -->
                    <label for="filterCISPkg">${message(code:'package.label')}</label>
                    <g:select id="filterCISPkg" class="ui dropdown selection"
                              name="filterCISPkg"
                              from="${allCISPkgs}"
                              optionValue="${{it?.pkg?.name ?: 'Keine Verknüpfung'}}"
                              optionKey="${{"com.k_int.kbplus.SubscriptionPackage:" + it?.id}}"
                              noSelection="['':'']"
                              value="${params.filterCISPkg}" />
                </div>
            </div><!-- .three -->

            <div class="three fields">
                <div class="field">
                    <label for="filterCIBudgetCode">${message(code:'financials.budgetCode')}</label>
                    <g:select id="filterCIBudgetCode" class="ui dropdown search selection"
                              name="filterCIBudgetCode"
                              from="${allCIBudgetCodes}"
                              value="${params.filterCIBudgetCode}"
                              noSelection="${['':'Alle ..']}"
                    />
                </div>

                <div class="field">
                    <label>${message(code:'financials.invoice_number')}</label>
                    <g:select id="filterCIInvoiceNumber" class="ui dropdown search selection"
                              name="filterCIInvoiceNumber"
                              from="${allCIInvoiceNumbers}"
                              value="${params.filterCIInvoiceNumber}"
                              noSelection="${['':'Alle ..']}"
                    />
                </div>

                <div class="field">
                    <label>${message(code:'financials.order_number')}</label>
                    <g:select id="filterCIOrderNumber" class="ui dropdown search selection"
                              name="filterCIOrderNumber"
                              from="${allCIOrderNumbers}"
                              value="${params.filterCIOrderNumber}"
                              noSelection="${['':'Alle ..']}"
                    />
                </div>
            </div><!-- .three -->

            <div class="three fields">
                <div class="field fieldcontain">
                    <label for="filterCIElement">${message(code:'financials.costItemElement')}</label>
                    <laser:select id="filterCIElement" class="ui dropdown selection"
                                  name="filterCIElement"
                                  from="${costItemElement}"
                                  optionKey="${{it.class.getName() + ":" + it.id}}"
                                  optionValue="value"
                                  value="${params.filterCIElement}"
                                  noSelection="${['':'Alle ..']}"/>
                </div>

                <div class="field fieldcontain">
                    <label for="filterCIStatus">${message(code:'financials.costItemStatus')}</label>
                    <laser:select id="filterCIStatus" class="ui dropdown selection"
                                  name="filterCIStatus"
                                  from="${costItemStatus}"
                                  optionKey="${{it.class.getName() + ":" + it.id}}"
                                  optionValue="value"
                                  value="${params.filterCIStatus}"
                                  noSelection="${['':'Alle ..']}"/>
                </div>
                <div class="field fieldcontain"><!--NEW -->
                <%--
                <label for="filterCICategory">${message(code:'financials.costItemCategory')}</label>
                <laser:select id="filterCICategory" class="ui dropdown selection"
                              name="filterCICategory"
                              from="${costItemCategory}"
                              optionKey="${{it.class.getName() + ":" + it.id}}"
                              optionValue="value"
                              value="${params.filterCICategory}"
                              noSelection="${['':'Alle ..']}"/>
                              --%>
                    <label for="filterCITaxType">${message(code:'financials.newCosts.controllable')}</label>
                    <laser:select id="filterCITaxType" class="ui dropdown selection"
                                  name="filterCITaxType"
                                  from="${taxType}"
                                  optionKey="${{it.class.getName() + ":" + it.id}}"
                                  optionValue="value"
                                  value="${params.taxType}"
                                  noSelection="${['':'Alle ..']}"/>
                </div>
            </div><!-- .three -->

            <div class="three fields">
                <div class="field">
                    <semui:datepicker label="default.valid_on.label" name="filterCIValidOn" placeholder="filter.placeholder"
                                      value="${params.filterCIValidOn}"/>
                </div>

                <div class="field">
                    <semui:datepicker label="financials.invoice_from" name="filterCIInvoiceFrom" placeholder="filter.placeholder"
                                      value="${params.filterCIInvoiceFrom}"/>
                </div>

                <div class="field">
                    <semui:datepicker label="financials.invoice_to" name="filterCIInvoiceTo" placeholder="filter.placeholder"
                                      value="${params.filterCIInvoiceTo}"/>
                </div>
            </div>

            <div class="three fields">
                <div class="field">
                    <semui:datepicker label="financials.paid_from" name="filterCIPaidFrom" placeholder="filter.placeholder"
                                      value="${params.filterCIPaidFrom}"/>
                </div>

                <div class="field">
                    <semui:datepicker label="financials.paid_to" name="filterCIPaidTo" placeholder="filter.placeholder"
                                      value="${params.filterCIPaidTo}"/>
                </div>

                <div class="field la-field-right-aligned ">
                    <a href="${request.forwardURI}" class="ui reset primary button">${message(code:'default.button.reset.label')}</a>
                    <input type="submit" name="submit" class="ui secondary button" value="${message(code:'default.button.filter.label', default:'Filter')}">
                </div>
            </div>

            <g:hiddenField name="orgId" value="${contextService.getOrg()?.id}"></g:hiddenField>
        </g:form>
    </semui:filter>

<!-- _filter.gsp -->