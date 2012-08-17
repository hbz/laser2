<!doctype html>
<html>
  <head>
    <meta name="layout" content="mmbootstrap"/>
    <title>KB+</title>
    <r:require modules="jeditable"/>
    <r:require module="jquery-ui"/>
  </head>

  <body>

    <div class="container">
      <ul class="breadcrumb">
        <li> <g:link controller="home">KBPlus</g:link> <span class="divider">/</span> </li>
        <li>Licences</li>
      </ul>
    </div>

    <div class="container">
      <h1>${license.licensee?.name} ${license.type?.value} Licence : <span id="reference" class="ipe" style="padding-top: 5px;">${license.reference}</span></h1>

      <ul class="nav nav-pills">
        <li><g:link controller="licenseDetails" 
                                   action="index" 
                                   params="${[id:params.id]}">License Details</g:link></li>

        <li class="active"><g:link controller="licenseDetails" 
                    action="documents" 
                    params="${[id:params.id]}">Documents</g:link></li>

        <li><g:link controller="licenseDetails" 
                    action="links" 
                    params="${[id:params.id]}">Links</g:link></li>

        <li><g:link controller="licenseDetails" 
                    action="notes" 
                    params="${[id:params.id]}">Notes</g:link></li>
      </ul>

    </div>

    <div class="container">
                <button class="btn btn-danger" id="delete-doc">Delete Selected Documents</button>&nbsp;
                <input type="submit" class="btn btn-primary" value="Add new document" data-toggle="modal" href="#modalCreateDocument" />
  
                <g:form id="delete_doc_form" url="[controller:'licenseDetails',action:'deleteDocuments']" method="post">
                  <input type="hidden" name="licid" value="${params.id}"/>
  
                <table class="table table-striped table-bordered table-condensed">
                  <thead>
                    <tr>
                      <td>Select</td>
                      <td>Title</td>
                      <td>File Name</td>
                      <td>Download Link</td>
                      <td>Creator</td>
                      <td>Type</td>
                      <td>Doc Store ID</td>
                    </tr>
                  </thead>
                  <tbody>
                    <g:each in="${license.documents}" var="docctx">
                      <g:if test="${docctx.owner.contentType==1}">
                        <tr>
                          <td><input type="checkbox" name="_deleteflag.${docctx.id}" value="true"/></td>
                          <td><g:inPlaceEdit domain="Doc" pk="${docctx.owner.id}" field="title" id="doctitle" class="newipe">${docctx.owner.title}</g:inPlaceEdit></td>
                          <td><g:inPlaceEdit domain="Doc" pk="${docctx.owner.id}" field="filename" id="docfilename" class="newipe">${docctx.owner.filename}</g:inPlaceEdit></td>
                          <td>
                            <g:if test="${docctx.owner?.contentType==1}">
                              <g:link controller="docstore" id="${docctx.owner.uuid}">Download Doc</g:link>
                            </g:if>
                          </td>
                          <td><g:inPlaceEdit domain="Doc" pk="${docctx.owner.id}" field="creator" id="docCreator" class="newipe">${docctx.owner.creator}</g:inPlaceEdit></td>
                          <td>${docctx.owner?.type?.value}</td>
                          <td><g:if test="${docctx.owner?.uuid}">${docctx.owner?.uuid}</g:if></td>
                        </tr>
                      </g:if>
                    </g:each>
                  </tbody>
                </table>
                </g:form>
    </div>
    
    <script language="JavaScript">
      $(document).ready(function() {

       });
    </script>


<!-- Lightbox modal for creating a document taken from licenceDocuments.html -->
<div class="modal hide" id="modalCreateDocument">
  <div class="modal-header">
    <button type="button" class="close" data-dismiss="modal">×</button>
    <h3>Create New Document</h3>
  </div>
  <g:form id="upload_new_doc_form" url="[controller:'licenseDetails',action:'uploadDocument']" method="post" enctype="multipart/form-data">
    <input type="hidden" name="licid" value="${license.id}"/>
    <div class="modal-body">
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
          <input type="file" name="upload_file" />
        </dd>
      </dl>
     <dl>
        <dt>
          <label>Document Type:</label>
        </dt>
        <dd>
          <select name="doctype">
            <option value="License">License</option>
            <option value="General">General</option>
            <option value="General">Addendum</option>
          </select>
        </dd>
      </dl>
    </div>
    <div class="modal-footer">
      <a href="#" class="btn" data-dismiss="modal">Close</a>
      <input type="submit" class="btn btn-primary" value="Save Changes">
    </div>
  </g:form>
</div>
<!-- End lightbox modal -->

    <script language="JavaScript">
      $(document).ready(function() {
         $('.newipe').editable('<g:createLink controller="ajax" action="genericSetValue" absolute="true"/>', {
           type      : 'textarea',
           cancel    : 'Cancel',
           submit    : 'OK',
           id        : 'elementid',
           rows      : 3,
           tooltip   : 'Click to edit...'
         });
       });
    </script>

  </body>
</html>
