<%@ page import="org.springframework.context.i18n.LocaleContextHolder; de.laser.I10nTranslation; de.laser.*; com.k_int.kbplus.auth.Role; de.laser.helper.RDConstants; de.laser.RefdataValue" %>

<%
    String locale = I10nTranslation.decodeLocale(LocaleContextHolder.getLocale())
    String getAllRefDataValuesForCategoryQuery = "select rdv from RefdataValue as rdv where rdv.owner.desc=:category order by rdv.order, rdv.value_" + locale
%>

<g:each in="${tmplConfigShow}" var="row">

    <g:set var="numberOfFields" value="${row.size()}" />
    <% if (row.contains('property')) { numberOfFields++ } %>

    <g:if test="${numberOfFields > 1}">
        <div class="${numberOfFields==4 ? 'four fields' : numberOfFields==3 ? 'three fields' : numberOfFields==2 ? 'two fields' : ''}">
    </g:if>

        <g:each in="${row}" var="field" status="fieldCounter">

            <g:if test="${field.equalsIgnoreCase('name')}">
                <div class="field">
                    <label for="orgNameContains">${actionName == 'listProvider' ? message(code: 'org.search.provider.contains') : message(code: 'org.search.contains')}</label>
                    <input type="text" id="orgNameContains" name="orgNameContains"
                           placeholder="${message(code:'default.search.ph')}"
                           value="${params.orgNameContains}"/>
                </div>
            </g:if>

            <g:if test="${field.equalsIgnoreCase('identifier')}">
                <div class="field">
                    <label for="orgIdentifier">${message(code: 'default.search.identifier')}</label>
                    <div class="ui input">
                        <input type="text" id="orgIdentifier" name="orgIdentifier"
                               placeholder="${message(code: 'default.search.identifier.ph')}"
                               value="${params.orgIdentifier}"/>
                    </div>
                </div>
            </g:if>

            <g:if test="${field.equalsIgnoreCase('property')}">
                <g:render template="/templates/properties/genericFilter" model="[propList: propList]"/>
            </g:if>

            <g:if test="${field.equalsIgnoreCase('type')}">
                <div class="field">
                    <label for="orgType">${message(code: 'org.orgType.label')}</label>
                    <g:if test="${orgTypes == null || orgTypes.isEmpty()}">
                        <g:set var="orgTypes" value="${RefdataValue.executeQuery(getAllRefDataValuesForCategoryQuery, [category: RDConstants.ORG_TYPE])}" scope="request"/>
                    </g:if>
                    <laser:select class="ui dropdown search" id="orgType" name="orgType"
                                  from="${orgTypes}"
                                  optionKey="id"
                                  optionValue="value"
                                  value="${params.orgType}"
                                  noSelection="${['':message(code:'default.select.choose.label')]}"/>
                </div>
            </g:if>

            <g:if test="${field.equalsIgnoreCase('role')}">
                <div class="field">
                    <label for="orgRole">${message(code: 'org.orgRole.label')}</label>
                    <g:if test="${orgRoles == null || orgRoles.isEmpty()}">
                        %{--<g:set var="orgRoles" value="${RefdataCategory.getAllRefdataValues(RDConstants.ORGANISATIONAL_ROLE)}"/>--}%
                        <g:set var="orgRoles" value="${RefdataValue.executeQuery(getAllRefDataValuesForCategoryQuery, [category: RDConstants.ORGANISATIONAL_ROLE])}" scope="request"/>
                    </g:if>
                    <laser:select class="ui dropdown search" id="orgRole" name="orgRole"
                                  from="${orgRoles}"
                                  optionKey="id"
                                  optionValue="value"
                                  value="${params.orgRole}"
                                  noSelection="${['':message(code:'default.select.choose.label')]}"/>
                </div>
            </g:if>

            <g:if test="${field.equalsIgnoreCase('sector')}">
                <div class="field">
                    <label for="orgSector">${message(code: 'org.sector.label')}</label>
                    <g:set var="orgSectors" value="${RefdataValue.executeQuery(getAllRefDataValuesForCategoryQuery, [category: RDConstants.ORG_SECTOR])}" scope="request"/>
                    <laser:select class="ui dropdown search" id="orgSector" name="orgSector"
                                  from="${orgSectors}"
                                  optionKey="id"
                                  optionValue="value"
                                  value="${params.orgSector}"
                                  noSelection="${['':message(code:'default.select.choose.label')]}"/>
                </div>
            </g:if>

            <g:if test="${field.equalsIgnoreCase('region')}">
                <g:render template="/templates/filter/orgRegionsFilter" />
            </g:if>

            <g:if test="${field.equalsIgnoreCase('libraryNetwork')}">
                <div class="field">
                    <label for="libraryNetwork">${message(code: 'org.libraryNetwork.label')}</label>
                    <select id="libraryNetwork" name="libraryNetwork" multiple="" class="ui selection fluid dropdown">
                        <option value="">${message(code:'default.select.choose.label')}</option>
                        <g:set var="libraryNetworks" value="${RefdataValue.executeQuery(getAllRefDataValuesForCategoryQuery, [category: RDConstants.LIBRARY_NETWORK])}" scope="request"/>
                        <g:each in="${libraryNetworks}" var="rdv">
                            <option <%=(params.list('libraryNetwork').contains(rdv.id.toString())) ? 'selected="selected"' : '' %> value="${rdv.id}">${rdv.getI10n("value")}</option>
                        </g:each>
                    </select>
                </div>
            </g:if>

            <g:if test="${field.equalsIgnoreCase('libraryType')}">
                <div class="field">
                    <label for="libraryType">${message(code: 'org.libraryType.label')}</label>
                    <select id="libraryType" name="libraryType" multiple="" class="ui selection fluid dropdown">
                        <option value="">${message(code:'default.select.choose.label')}</option>
                        <g:set var="libraryTypes" value="${RefdataValue.executeQuery(getAllRefDataValuesForCategoryQuery, [category: RDConstants.LIBRARY_TYPE])}" scope="request"/>
                        <g:each in="${libraryTypes}" var="rdv">
                            <option <%=(params.list('libraryType').contains(rdv.id.toString())) ? 'selected="selected"' : '' %> value="${rdv.id}">${rdv.getI10n("value")}</option>
                        </g:each>
                    </select>
                </div>
            </g:if>

            <g:if test="${field.equalsIgnoreCase('country')}">
                <div class="field">
                    <label for="country">${message(code: 'org.country.label')}</label>
                    <g:set var="countries" value="${RefdataValue.executeQuery(getAllRefDataValuesForCategoryQuery, [category: RDConstants.COUNTRY])}" scope="request"/>
                    <laser:select class="ui dropdown search" id="country" name="country"
                                  from="${countries}"
                                  optionKey="id"
                                  optionValue="value"
                                  value="${params.country}"
                                  noSelection="${['':message(code:'default.select.choose.label')]}"/>
                </div>
            </g:if>
            <g:if test="${field.equalsIgnoreCase('customerType')}">
            <div class="field">
                <label for="customerType">${message(code:'org.customerType.label')}</label>
                <laser:select id="customerType" name="customerType"
                              from="${[Role.findByAuthority('FAKE')] + Role.findAllByRoleType('org')}"
                              optionKey="id"
                              optionValue="authority"
                              value="${params.customerType}"
                              class="ui dropdown"
                              noSelection="${['':message(code:'default.select.choose.label')]}"
                />
            </div>
            </g:if>
            <g:if test="${field.equalsIgnoreCase('subscription')}">
                <div class="field">
                    <label for="subscription">${message(code:'subscription')}</label>
                    <select id="subscription" name="subscription" multiple="" class="ui selection fluid dropdown">
                        <option value="">${message(code:'default.select.choose.label')}</option>
                        <g:each in="${subscriptions}" var="sub">
                            <option <%=(params.list('subscription').contains(sub.id.toString())) ? 'selected="selected"' : '' %> value="${sub.id}">${sub.dropdownNamingConvention()}</option>
                        </g:each>
                    </select>
                </div>
            </g:if>
            <g:if test="${field.equalsIgnoreCase('subRunTimeMultiYear')}">
                <div class="field">
                    <label>${message(code: 'myinst.currentSubscriptions.subscription.runTime')}</label>
                    <div class="inline fields la-filter-inline">
                        <div class="inline field">
                            <div class="ui checkbox">
                                <label for="checkSubRunTimeMultiYear">${message(code: 'myinst.currentSubscriptions.subscription.runTime.multiYear')}</label>
                                <input id="checkSubRunTimeMultiYear" name="subRunTimeMultiYear" type="checkbox" <g:if test="${params.subRunTimeMultiYear}">checked=""</g:if>
                                       tabindex="0">
                            </div>
                        </div>
                        <div class="inline field">
                            <div class="ui checkbox">
                                <label for="checkSubRunTimeNoMultiYear">${message(code: 'myinst.currentSubscriptions.subscription.runTime.NoMultiYear')}</label>
                                <input id="checkSubRunTimeNoMultiYear" name="subRunTime" type="checkbox" <g:if test="${params.subRunTime}">checked=""</g:if>
                                       tabindex="0">
                            </div>
                        </div>
                    </div>
                </div>
            </g:if>

            <g:if test="${field.equalsIgnoreCase('subjectGroup')}">
                <div class="field">
                    <label for="subjectGroup">${message(code: 'org.subjectGroup.label')}</label>
                    <select id="subjectGroup" name="subjectGroup" multiple="" class="ui selection fluid dropdown">
                        <option value="">${message(code:'default.select.choose.label')}</option>
                        <g:set var="subjectGroups" value="${RefdataValue.executeQuery(getAllRefDataValuesForCategoryQuery, [category: RDConstants.SUBJECT_GROUP])}" scope="request"/>
                        <g:each in="${subjectGroups}" var="rdv">
                            <option <%=(params.list('subjectGroup').contains(rdv.id.toString())) ? 'selected="selected"' : '' %> value="${rdv.id}">${rdv.getI10n("value")}</option>
                        </g:each>
                    </select>
                </div>
            </g:if>


        </g:each>
    <g:if test="${numberOfFields > 1}">
        </div><!-- .fields -->
    </g:if>


</g:each>

<%--
<g:set var="allFields" value="${tmplConfigShow.flatten()}" />

<g:if test="${! allFields.contains('type') && params.orgType}">
    <input type="hidden" name="orgType" value="${params.orgType}" />
</g:if>
<g:if test="${! allFields.contains('role') && params.orgRole}">
    <input type="hidden" name="orgRole" value="${params.orgRole}" />
</g:if>
<g:if test="${! allFields.contains('sector') && params.orgSector}">
    <input type="hidden" name="orgSector" value="${params.orgSector}" />
</g:if>
<g:if test="${! allFields.contains('region') && params.region}">
    <input type="hidden" name="region" value="${params.region}" />
</g:if>
<g:if test="${! allFields.contains('libraryNetwork') && params.libraryNetwork}">
    <input type="hidden" name="libraryNetwork" value="${params.libraryNetwork}" />
</g:if>
<g:if test="${! allFields.contains('libraryType') && params.libraryType}">
    <input type="hidden" name="libraryType" value="${params.libraryType}" />
</g:if>
<g:if test="${! allFields.contains('country') && params.country}">
    <input type="hidden" name="country" value="${params.country}" />
</g:if>
--%>


<div class="field la-field-right-aligned">

        <a href="${request.forwardURI}" class="ui reset primary button">${message(code:'default.button.reset.label')}</a>

        <input name="filterSet" type="hidden" value="true">
        <g:if test="${tmplConfigFormFilter}">
            <input type="submit" value="${message(code:'default.button.filter.label')}" class="ui secondary button" onclick="formFilter(event)" />
            <r:script>
                formFilter = function(e) {
                    e.preventDefault()

                    var form = $(e.target).parents('form')
                    $(form).find(':input').filter(function () {
                        return !this.value
                    }).attr('disabled', 'disabled')

                    form.submit()
                }
            </r:script>
        </g:if>
        <g:else>
            <input type="submit" value="${message(code:'default.button.filter.label')}" class="ui secondary button"/>
        </g:else>

</div>


