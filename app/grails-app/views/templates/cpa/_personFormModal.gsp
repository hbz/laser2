<%@ page import="com.k_int.kbplus.PersonRole; de.laser.Contact; de.laser.Person; de.laser.FormService; de.laser.helper.RDStore; de.laser.RefdataValue;de.laser.RefdataCategory;de.laser.helper.RDConstants" %>
<laser:serviceInjection/>
<semui:modal id="${modalID?: 'personModal'}" text="${modalText ?: message(code: 'person.create_new.label')}" contentClass="scrolling "
             msgClose="${message(code: 'default.button.cancel')}"
             msgSave="${message(code: 'default.button.save.label')}">
    <g:form id="create_person" class="ui form" url="${url}" method="POST">

        <input type="hidden" name="tenant.id" value="${contextService.getOrg().id}"/>
        <input id="isPublic" name="isPublic" type="hidden" value="${personInstance?.isPublic ?: (isPublic ?: false)}"/>

        <div class="field">
            <div class="two fields">

                <div class="field wide twelve ${hasErrors(bean: personInstance, field: 'last_name', 'error')} required">
                    <label for="last_name">
                        <g:message code="person.last_name.label"/>
                    </label>
                    <g:textField name="last_name" required="" value="${personInstance?.last_name}"/>
                </div>

                <div id="person_title"
                     class="field wide four ${hasErrors(bean: personInstance, field: 'title', 'error')}">
                    <label for="title">
                        <g:message code="person.title.label"/>
                    </label>
                    <g:textField name="title" required="required" value="${personInstance?.title}"/>
                </div>

            </div>
        </div>

        <div class="field">
            <div class="three fields">

                <div id="person_first_name"
                     class="field wide eight ${hasErrors(bean: personInstance, field: 'first_name', 'error')}">
                    <label for="first_name">
                        <g:message code="person.first_name.label"/>
                    </label>
                    <g:textField name="first_name" value="${personInstance?.first_name}"/>
                </div>

                <div id="person_middle_name"
                     class="field wide four ${hasErrors(bean: personInstance, field: 'middle_name', 'error')} ">
                    <label for="middle_name">
                        <g:message code="person.middle_name.label"/>
                    </label>
                    <g:textField name="middle_name" value="${personInstance?.middle_name}"/>
                </div>

                <div id="person_gender"
                     class="field wide four ${hasErrors(bean: personInstance, field: 'gender', 'error')} ">
                    <label for="gender">
                        <g:message code="person.gender.label"/>
                    </label>
                    <laser:select class="ui dropdown" id="gender" name="gender"
                                  from="${Person.getAllRefdataValues(RDConstants.GENDER)}"
                                  optionKey="id"
                                  optionValue="value"
                                  value="${personInstance?.gender?.id}"
                                  noSelection="${['': message(code: 'default.select.choose.label')]}"/>
                </div>
            </div>
        </div>

        <g:if test="${!tmplHideFunctions}">

            <div class="field">
                <div class="two fields">
                    <div class="field">
                        <label for="functionType">
                            <g:message code="person.function.label"/>
                        </label>

                        <select name="functionType" id="functionType" multiple=""
                                class="ui search selection dropdown sortable">
                            <option value="">${message(code: 'default.select.choose.label')}</option>

                            <g:each in="${PersonRole.getAllRefdataValues(RDConstants.PERSON_FUNCTION)}"
                                    var="functionType">
                                <option <%=(personInstance ? (functionType.id in personInstance.getPersonRoleByOrg(contextOrg).functionType?.id) : (presetFunctionType?.id == functionType.id)) ? 'selected="selected"' : ''%>
                                        value="${functionType.id}">
                                    ${functionType.getI10n('value')}
                                </option>
                            </g:each>
                        </select>
                    </div>

                    <g:if test="${!isPublic}">
                        <div class="field">
                            <g:if test="${orgList}">
                                <label for="functionOrg">
                                    <g:message code="contact.belongesTo.label"/>
                                </label>
                                <g:select class="ui search dropdown"
                                          name="personRoleOrg"
                                          from="${orgList}"
                                          value="${org?.id}"
                                          optionKey="id"
                                          optionValue=""/>
                            </g:if>
                            <g:else>
                                <label for="functionOrg">
                                    <g:message code="contact.belongesTo.label"/>
                                </label>
                                <i class="icon university la-list-icon"></i>${org?.name}
                                <input id="functionOrg" name="personRoleOrg" type="hidden" value="${org?.id}"/>
                            </g:else>
                        </div>
                    </g:if>

                    %{-- <g:if test="${actionName != 'myPublicContacts'}">
                         <div class="field">
                             <g:if test="${institution}">
                                 <label for="functionOrg">
                                     <g:message code="contact.belongesTo.label"/>
                                 </label>
                                 <g:select class="ui search dropdown"
                                           name="functionOrg"
                                           from="${orgList}"
                                           value="${org?.id}"
                                           optionKey="id"
                                           optionValue=""/>
                             </g:if>
                             <g:else>
                                 <label for="functionOrg">
                                     <g:message code="contact.belongesTo.label"/>
                                 </label>
                                 <i class="icon university la-list-icon"></i>${org?.name}
                                 <input id="functionOrg" name="functionOrg" type="hidden" value="${org?.id}"/>
                             </g:else>
                         </div>
                     </g:if>--}%

                </div>
            </div><!-- .field -->

            <div class="field">
                <div class="two fields">
                    <div class="field">
                        <label for="positionType">
                            <g:message code="person.position.label"/>
                        </label>
                        <select name="positionType" id="positionType" multiple="" class="ui search selection dropdown">
                            <option value="">${message(code: 'default.select.choose.label')}</option>

                            <g:each in="${PersonRole.getAllRefdataValues(RDConstants.PERSON_POSITION)}"
                                    var="positionType">
                                <option <%=(personInstance ? (positionType.id in personInstance.getPersonRoleByOrg(contextOrg).positionType?.id) : (presetPositionType?.id == positionType.id)) ? 'selected="selected"' : ''%>
                                        value="${positionType.id}">
                                    ${positionType.getI10n('value')}
                                </option>
                            </g:each>
                        </select>

                    </div>
                    %{--<g:if test="${actionName != 'myPublicContacts'}">
                        <div class="field">

                            <g:if test="${institution}">
                                <label for="positionOrg">
                                    <g:message code="contact.belongesTo.label"/>
                                </label>
                                <g:select class="ui search dropdown"
                                          name="positionOrg"
                                          from="${orgList}"
                                          value="${org?.id}"
                                          optionKey="id"
                                          optionValue=""/>
                            </g:if>
                            <g:else>
                                <label for="positionOrg">
                                    <g:message code="contact.belongesTo.label"/>
                                </label>
                                <i class="icon university la-list-icon"></i>${org?.name}
                                <input id="positionOrg" name="positionOrg" type="hidden" value="${org?.id}"/>
                            </g:else>
                        </div>
                    </g:if>--}%

                </div>
            </div><!-- .field -->
        </g:if>

        <g:if test="${showContacts}">
            <div class="field">
                <br>
                <br>
                <label for="contacts">
                    <g:message code="person.contacts.label"/>:
                </label>

                <g:if test="${personInstance}">
                    <g:each in="${personInstance.contacts?.toSorted()}" var="contact">
                        <div class="two fields">
                            <div class="field three wide fieldcontain">
                                <input type="text" readonly value="${contact.contentType.getI10n('value')}"/>
                            </div>

                            <div class="field thirteen wide fieldcontain">
                                <g:textField name="content${contact.id}" value="${contact.content}"/>
                            </div>
                        </div>
                    </g:each>
                </g:if>
            </div>
            <g:if test="${addContacts}">
                <button type="button" id="addContactElement" class="ui icon button">
                    <i class="plus green circle icon"></i>
                </button>

                <button type="button" id="removeContactElement" class="ui icon button">
                    <i class="minus red circle icon"></i>
                </button>

                <br>
                <br>

                <div class="three fields">
                    <div class="field three wide fieldcontain">
                        <label></label>
                        <laser:select class="ui dropdown" name="contentType.id"
                                      from="${Contact.getAllRefdataValues(RDConstants.CONTACT_CONTENT_TYPE)}"
                                      optionKey="id"
                                      optionValue="value"
                                      value="${contactInstance?.contentType?.id}"/>
                    </div>

                    <div class="field one wide fieldcontain">

                    </div>


                    <div class="field twelve wide fieldcontain">
                        <label></label>
                        <g:textField id="content" name="content" value="${contactInstance?.content}"/>
                    </div>
                </div>

                <div id="contcatElements"></div>
            </g:if>

        </g:if>

        <g:if test="${showAddresses}">
            <div class="field">
                <br>
                <br>
                <label for="addresses">
                    <g:message code="person.addresses.label"/>:
                </label>
                <g:if test="${personInstance}">
                    <div class="ui divided middle aligned list la-flex-list ">
                    <g:each in="${personInstance.addresses.sort { it.type.each{it?.getI10n('value') }}}" var="address">
                        <g:render template="/templates/cpa/address"
                                  model="${[address: address, tmplShowDeleteButton: tmplShowDeleteButton, editable: editable]}"/>
                    </g:each>
                    </div>
                </g:if>
            </div>
            <g:if test="${addAddresses}">
                <button type="button" id="addAddressElement" class="ui icon button">
                    <i class="plus green circle icon"></i>
                </button>

                <button type="button" id="removeAddressElement" class="ui icon button">
                    <i class="minus red circle icon"></i>
                </button>

                <br>
                <br>

                <div id="addressElements"></div>
            </g:if>

        </g:if>

    </g:form>

    <script>

        $(document).ready(function () {
            $('#create_person').form({
                on: 'blur',
                inline: true,
                fields: {
                    last_name: {
                        identifier: 'last_name',
                        rules: [
                            {
                                type: 'empty',
                                prompt: '{name} <g:message code="validation.needsToBeFilledOut"
                                                        default=" muss ausgefüllt werden"/>'
                            }
                        ]
                    }
                }
            });

            var contactElementCount = 0;
            var addressElementCount = 0;

            var contcatContainer = $(document.createElement('div'));
            $(contcatContainer).attr('id', 'contcatElementsContainer');

            var addressContainer = $(document.createElement('div'));
            $(addressContainer).attr('id', 'addressElementsContainer');

            $('#addContactElement').click(function () {
                $.ajax({
                    url: "<g:createLink controller="ajax" action="contactFields"/>",
                    type: "POST",
                    success: function (data) {
                        if (contactElementCount <= 3) {

                            contactElementCount = contactElementCount + 1;
                            $(contcatContainer).append(data);
                            $('#contactFields').attr('id', 'contactFields' + contactElementCount);

                            $('#contcatElements').after(contcatContainer);
                        } else {
                            $('#addContactElement').attr('class', 'ui icon button disable');
                            $('#addContactElement').attr('disabled', 'disabled');
                        }
                        r2d2.initDynamicSemuiStuff('#contcatElementsContainer');
                    },
                    error: function (j, status, eThrown) {
                        console.log('Error ' + eThrown)
                    }
                });
            });

            $('#removeContactElement').click(function () {
                if (contactElementCount != 0) {
                    $('#contactFields' + contactElementCount).remove();
                    contactElementCount = contactElementCount - 1;
                }

                if (contactElementCount == 0) {
                    $(contcatContainer).empty().remove();
                    $('#addContactElement').removeAttr('disabled').attr('class', 'ui icon button');
                }
            });

            $('#addAddressElement').click(function () {
                $.ajax({
                    url: "<g:createLink controller="ajax" action="addressFields" params="[multipleAddresses: true]"/>",
                    type: "POST",
                    success: function (data) {
                        if (addressElementCount <= 3) {

                            addressElementCount = addressElementCount + 1;
                            $(addressContainer).append(data);
                            $('#addressFields').attr('id', 'addressFields' + addressElementCount);

                            $('#addressElements').after(addressContainer);
                        } else {
                            $('#addAddressElement').attr('class', 'ui icon button disable');
                            $('#addAddressElement').attr('disabled', 'disabled');
                        }
                        r2d2.initDynamicSemuiStuff('#addressElementsContainer');
                    },
                    error: function (j, status, eThrown) {
                        console.log('Error ' + eThrown)
                    }
                });
            });

            $('#removeAddressElement').click(function () {
                if (addressElementCount != 0) {
                    $('#addressFields' + addressElementCount).remove();
                    addressElementCount = addressElementCount - 1;
                }

                if (addressElementCount == 0) {
                    $(addressContainer).empty().remove();
                    $('#addAddressElement').removeAttr('disabled').attr('class', 'ui icon button');
                }
            });
        });

    </script>

</semui:modal>
