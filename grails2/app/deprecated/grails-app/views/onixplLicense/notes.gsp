<!doctype html>
<html>
    <head>
        <meta name="layout" content="semanticUI"/>
        <title>${message(code:'laser')}</title>
</head>

<body>

<% /* REMOVE; not tested
    <div>
      <ul class="breadcrumb">
        <li> <g:link controller="home" action="index">Home</g:link> <span class="divider">/</span> </li>
        <g:if test="${onixplLicense.license.getLicensee()}">
          <li> <g:link controller="myInstitution" action="currentLicenses" params="${[shortcode:onixplLicense.license.licensee.shortcode]}"> ${onixplLicense.license.licensee.name} Current Licenses</g:link> <span class="divider">/</span> </li>
        </g:if>
        <li> <g:link controller="onixplLicense" action="index" id="${params.id}">ONIX-PL License Details</g:link> <span class="divider">/</span></li>
        <li> <g:link controller="onixplLicense" action="notes" id="${params.id}">License Notes</g:link> </li>

        <g:if test="${editable}">
            <semui:crumbAsBadge message="default.editable" class="orange" />
        </g:if>

      </ul>
    </div>
*/ %>

        <semui:breadcrumbs>
            <g:if test="${onixplLicense.license.getLicensee()}">
                <semui:crumb controller="myInstitution" action="currentLicenses" params="${[shortcode:onixplLicense.license.getLicensee().shortcode]}" text="${onixplLicense.license.getLicensee().name} Current Licenses" />
            </g:if>
            <semui:crumb controller="onixplLicense" action="index" id="${params.id}" text="ONIX-PL License Details" />
            <semui:crumb controller="onixplLicense" action="notes" id="${params.id}" text="License Notes" />
        </semui:breadcrumbs>
        <br>
        <g:if test="${editable}">
            <semui:crumbAsBadge message="default.editable" class="orange" />
        </g:if>
        <br>

        <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />${onixplLicense.license.getLicensee()?.name} ${onixplLicense.license.type?.value} License : <span id="reference" style="padding-top: 5px;">${onixplLicense.license.reference}</span></h1>

        <g:render template="nav" contextPath="." />


    <div>
        <g:form id="delete_doc_form" url="[controller:'license', action:'deleteDocuments']" method="post">

            <div class="well hide license-notes-options">
                <input type="hidden" name="licid" value="${params.id}"/>
                <input type="submit" class="ui negative button" value="Delete Selected Notes"/>
            </div>

            <table class="ui celled la-table table license-notes">
                <thead>
                    <tr>
                        <th>Select</th>
                        <th>Title</th>
                        <th>Note</th>
                        <th>Creator</th>
                        <th>Type</th>
                    </tr>
                </thead>
                <tbody>
                <g:each in="${onixplLicense.license.documents}" var="docctx">
                    <g:if test="${docctx.owner.contentType==0 && ( docctx.status == null || docctx.status?.value != 'Deleted') && ( docctx.domain == null ) }">
                        <tr>
                            <td><input type="checkbox" name="_deleteflag.${docctx.id}" value="true"/></td>
                            <td>
                              <semui:xEditable owner="${docctx.owner}" field="title" id="title"/>
                            </td>
                            <td>
                              <semui:xEditable owner="${docctx.owner}" field="content" id="content"/>
                            </td>
                            <td>
                              <semui:xEditable owner="${docctx.owner}" field="creator" id="creator"/>
                            </td>
                            <td>${docctx.owner?.type?.value}</td>
                        </tr>
                    </g:if>
                </g:each>
                </tbody>
            </table>
        </g:form>
    </div>

    <!-- JS for license documents -->
    <r:script>
        $('.license-notes input[type="checkbox"]').click(function () {
            if ($('.license-notes input:checked').length > 0) {
                $('.license-notes-options').slideDown('fast');
            } else {
                $('.license-notes-options').slideUp('fast');
            }
        });

        $('.license-notes-options .delete-document').click(function () {
            if (!confirm('Are you sure you wish to delete the selected note(s)?')) {
                $('.license-notes input:checked').attr('checked', false);
                return false;
            }
            $('.license-notes input:checked').each(function () {
                $(this).parent().parent().fadeOut('slow');
                $('.license-notes-options').slideUp('fast');
            });
        })
    </r:script>

</body>
</html>
