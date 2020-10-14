<!doctype html>
<html>
  <head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code:'laser')} Admin::Orgs Import</title>
  </head>

  <body>
        <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />Orgs Import</h1>
        <g:form action="orgsImport" method="post" enctype="multipart/form-data">
          <p>
            Upload a .csv file formatted as<br/>
            org_name, sector, id.type...,affiliation.role...,role,</br>
            <table class="ui celled la-table table">
              <tr>
                <td>Example Header Row:</td>
                <td>name,</td>
                <td>sector,</td>
                <td>org.consortium,</td>
                <td>id.jusplogin</td>
                <td>id.JC</td>
                <td>id.Ringold</td>
                <td>id.UKAMF</td>
                <td>id.ISIL</td>
                <td>iprange</td>
              </tr>
              <tr>
                <td>Example Body Row:</td>
                <td>"Some inst",</td>
                <td>"Higher Education|Publisher",</td>
                <td>"JISC Collections",</td>
                <td>"",</td>
                <td>"",</td>
                <td>"",</td>
                <td>"",</td>
                <td>"",</td>
                <td>"n.n.n.n-n.n.n.n"</td>
              </tr>
            </table>
          </p>
          <dl>
            <div class="control-group">
              <dt>Orgs CSV File</dt>
              <dd>
                <input type="file" name="orgs_file" />
              </dd>
            </div>
            <button name="load" type="submit" value="Go">Load...</button>
          </dl>
        </g:form>

  </body>
</html>
