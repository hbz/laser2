<%@ page import="de.laser.RefdataValue; de.laser.helper.RDConstants" %>
<!doctype html>
<r:require module="scaffolding" />
<html>
<head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code:'laser')}</title>
</head>

<body>

<div>
    <ul class="breadcrumb">
        <li><g:link controller="home" action="index">Home</g:link> <span class="divider">/</span></li>
        <g:if test="${onixplLicense.license.getLicensee()}">
            <li><g:link controller="myInstitution" action="currentLicenses">${onixplLicense.license.getLicensee().name} Current Licenses</g:link> <span
                    class="divider">/</span></li>
        </g:if>
        <li><g:link controller="onixplLicense" action="index" id="${params.id}">ONIX-PL License Details</g:link> <span class="divider">/</span></li>
        <li><g:link controller="onixplLicense" action="documents" id="${params.id}">License Documents</g:link></li>
    </ul>
</div>

    <g:if test="${editable}">
        <semui:crumbAsBadge message="default.editable" class="orange" />
    </g:if>
    <br>
    <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />${onixplLicense.license.getLicensee()?.name} ${onixplLicense.license.type?.value} License : <span id="reference"
                                                                                                     style="padding-top: 5px;">${onixplLicense.license.reference}</span>
    </h1>

    <g:render template="nav" contextPath="."/>

<div>
    <g:form id="delete_doc_form" url="[controller: 'license', action: 'deleteDocuments']" method="post">
        <div class="well hide license-documents-options">
            <button class="ui negative button" id="delete-doc">Delete Selected Documents</button>&nbsp;
            <input type="submit" class="ui button" value="Add new document" data-semui="modal"
                   data-href="#modalCreateDocument"/>

            <input type="hidden" name="licid" value="${params.id}"/>
        </div>

        <div class="row">
        <div class="span8">
        <g:if test="${onixplLicense.doc}">
            <h6 class="ui header">Document Details</h6>

            <div class="inline-lists">
                <dl>
                    <dt>Title</dt>
                    <dd>${onixplLicense?.doc?.title}</dd>
                </dl>
                <dl>
                    <dt>Filename</dt>
                    <dd>${onixplLicense?.doc?.filename}</dd>
                </dl>
                <dl>
                    <dt>Type</dt>
                    <dd>${onixplLicense?.doc?.type?.value}</dd>
                </dl>
                <dl>
                    <dt>Status</dt>
                    <dd>${onixplLicense?.doc?.status?.value}</dd>
                </dl>
                <dl>
                    <dt>Creator</dt>
                    <dd>${onixplLicense?.doc?.creator}</dd>
                </dl>
                <dl>
                    <dt>User</dt>
                    <dd>${onixplLicense?.doc?.user?.display}</dd>
                </dl>
                <dl>
                    <dt>Created</dt>
                    <dd>${onixplLicense?.doc?.dateCreated}</dd>
                </dl>
                <dl>
                    <dt>Last Modified</dt>
                    <dd>${onixplLicense?.doc?.lastUpdated}</dd>
                </dl>
                <dl>
                    <dt>Content</dt>
                    <dd>${onixplLicense?.doc?.content}</dd>
                </dl>
            </div>
        </g:if>
        <g:else>
            No document available
        </g:else>

    </g:form>
</div>
</div>
</div>

<div class="ui modal" id="modalCreateDocument">
    <div class="modal-header">
        <button type="button" class="close" onclick="$('#modalCreateDocument').modal('hide')">×</button>

        <h3 class="ui header">Create New Document</h3>
    </div>
    <g:form id="upload_new_doc_form" url="[controller: 'license', action: 'uploadDocument']" method="post"
            enctype="multipart/form-data">
        <input type="hidden" name="licid" value="${onixplLicense.license.id}"/>

        <div class="modal-body">
            <div class="inline-lists">
                <dl>
                    <dt>
                        <label>Document Name:</label>
                    </dt>
                    <dd>
                        <input type="text" name="upload_title">
                    </dd>
                </dl>
                <dl>
                    <dt>
                        <label>File:</label>
                    </dt>
                    <dd>
                        <input type="file" name="upload_file"/>
                    </dd>
                </dl>
                <dl>
                    <dt>
                        <label>Document Type:</label>
                    </dt>
                    <dd>
                        <select name="doctype">
                            <option value="${RefdataValue.getByValueAndCategory('License', RDConstants.DOCUMENT_TYPE)}"><g:message code="license"/></option>
                            <option value="${RefdataValue.getByValueAndCategory('General', RDConstants.DOCUMENT_TYPE)}"><g:message code="template.addDocument.type.general" default="General"/></option>
                            <option value="${RefdataValue.getByValueAndCategory('Addendum', RDConstants.DOCUMENT_TYPE)}"><g:message code="template.addDocument.type.addendum" default="Addendum"/></option>
                        </select>
                    </dd>
                </dl>
            </div>
        </div>

        <div class="modal-footer">
            <a href="#" class="ui button" onclick="$('#modalCreateDocument').modal('hide')">Close</a>
            <input type="submit" class="ui button" value="Save Changes">
        </div>
    </g:form>
</div>
<!-- End lightbox modal -->

<r:script language="JavaScript">
    $(document).ready(function () {

        var checkEmptyEditable = function () {
            $('.fieldNote').each(function () {
                if ($(this).text().length == 0) {
                    $(this).addClass('editableEmpty');
                } else {
                    $(this).removeClass('editableEmpty');
                }
            });
        }

        checkEmptyEditable();

        $('.fieldNote').click(function () {
            // Hide edit icon with overwriting style.
            $(this).addClass('clicked');

            var e = $(this);

            var removeClicked = function () {
                setTimeout(function () {
                    e.removeClass('clicked');

                    if (iconStyle) {
                        e.parent().find('.select-icon').show();
                    }
                }, 1);
            }

            setTimeout(function () {
                e.find('form button').click(function () {
                    removeClicked();
                });
                e.keydown(function (event) {
                    if (event.keyCode == 27) {
                        removeClicked();
                    }
                });
            }, 1);
        });

        $('.fieldNote').editable('<g:createLink controller="ajax" action="genericSetValue" />', {
            type: 'textarea',
            cancel: 'Cancel',
            submit: 'OK',
            id: 'elementid',
            rows: 3,
            tooltip: 'Click to edit...',
            onblur: 'ignore'
        });
    });
</r:script>

<!-- JS for license documents -->
<r:script>
    $('.license-documents input[type="checkbox"]').click(function () {
        if ($('.license-documents input:checked').length > 0) {
            $('.license-documents-options').slideDown('fast');
        } else {
            $('.license-documents-options').slideUp('fast');
        }
    });

    $('.license-documents-options .delete-document').click(function () {
        if (!confirm('Are you sure you wish to delete these documents?')) {
            $('.license-documents input:checked').attr('checked', false);
            return false;
        }
        $('.license-documents input:checked').each(function () {
            $(this).parent().parent().fadeOut('slow');
            $('.license-documents-options').slideUp('fast');
        });
    })
</r:script>

</body>
</html>
