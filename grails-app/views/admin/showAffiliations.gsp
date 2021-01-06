<!doctype html>
<html>
  <head>
    <meta name="layout" content="laser">
    <title>${message(code:'laser')} : ${message(code:'menu.admin.showAffiliations')}</title>
  </head>

  <body>

    <semui:breadcrumbs>
      <semui:crumb message="menu.admin.dash" controller="admin" action="index"/>
      <semui:crumb message="menu.admin.showAffiliations" class="active"/>
    </semui:breadcrumbs>

    <semui:messages data="${flash}" />
        <br />
        <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />
          ${message(code:'menu.admin.showAffiliations')}
        </h1>

        <table class="ui celled la-table table">
          <thead>
            <tr>
              <th>${message(code:'profile.display')} / ${message(code:'profile.username')}</th>
              <th>${message(code:'profile.membership')}</th>
            </tr>
          </thead>
          <g:each in="${users}" var="u">
            <tr>
              <td>${u.displayName} / ${u.username}</td>
              <td>
                <ul>
                  <g:each in="${u.affiliations}" var="ua">
                    <li>${ua.org.shortcode}:${ua.status}:${ua.formalRole?.authority}</li>
                  </g:each>
                </ul>
              </td>
          </g:each>
        </table>

  </body>
</html>
