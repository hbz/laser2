<!doctype html>
<html>
  <head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code:'laser', default:'LAS:eR')} ${message(code:'myinst.title', default:'Institutional Dash')} :: ${institution?.name}</title>
  </head>

  <body>

    <semui:breadcrumbs>
        <semui:crumb text="${institution?.name}" class="active" />
    </semui:breadcrumbs>

    <div class="home-page">
        <div class="ui segment">
            <h1 class="ui header">${institution.name} - Dashboard</h1>
            <ul class="inline">

                <li><h5 class="ui header">${message(code:'myinst.view', default:'View')}:</h5></li>
                <li><g:link controller="myInstitutions"
                            action="currentLicenses"
                            params="${[shortcode:params.shortcode]}">${message(code:'license.plural', default:'Licenses')}</g:link></li>
                <li><g:link controller="myInstitutions"
                            action="currentSubscriptions"
                            params="${[shortcode:params.shortcode]}">${message(code:'subscription.plural', default:'Subscriptions')}</g:link></li>
                <li><g:link controller="myInstitutions"
                            action="currentTitles"
                            params="${[shortcode:params.shortcode]}">${message(code:'title.plural', default:'Titles')}</g:link></li>

                <li><h5 class="ui header">${message(code:'myinst.renewals', default:'Renewals')}:</h5></li>
                <li><g:link controller="myInstitutions"
                            action="renewalsSearch"
                            params="${[shortcode:params.shortcode]}">${message(code:'menu.institutions.gen_renewals', default:'Generate Renewals Worksheet')}</g:link></li>
                <li><g:link controller="myInstitutions"
                            action="renewalsUpload"
                            params="${[shortcode:params.shortcode]}">${message(code:'menu.institutions.imp_renew', default:'Import Renewals')}</g:link></li>
                <g:if test="${grailsApplication.config.feature_finance}">
                    <li><g:link controller="myInstitutions"
                            action="finance"
                            params="${[shortcode:params.shortcode]}">${message(code:'menu.institutions.finance', default:'Finance')}</g:link></li>
                </g:if>
          
                <li><h5 class="ui header">${message(code:'default.special.label', default:'Special')}:</h5></li>
                <li><g:link controller="myInstitutions"
                            action="tasks"
                            params="${[shortcode:params.shortcode]}">${message(code:'task.plural', default:'Tasks')}</g:link></li>
                <li><g:link controller="myInstitutions"
                            action="addressbook"
                            params="${[shortcode:params.shortcode]}">${message(code:'menu.institutions.addressbook', default:'Addressbook')}</g:link></li>
                <li><g:link controller="myInstitutions"
                            action="managePrivateProperties"
                            params="${[shortcode:params.shortcode]}">${message(code:'menu.institutions.manage_props')}</g:link></li>
            </ul>
        </div>
    </div>

    <semui:messages data="${flash}" />

    <div class="home-page">
      <div class="ui grid">
        <div class="five wide column">
            <table class="ui celled table dashboard-widget">
              <thead>
                <th>
                  <h5 class="pull-left">${message(code:'myinst.todo.label', default:'To Do')}</h5>
                  <span class="pull-right">
                    <i class="checkmark box icon large"></i>
                  </span>
                </th>
              </thead>
              <tbody>
              <g:each in="${todos}" var="todo">
                <tr>
                  <td>
                    <div class="pull-left icon">
                        <i class="checkmark box icon"></i>
                        <span class="badge badge-warning">${todo.num_changes}</span>
                    </div>
                    <div class="pull-right message">
                      <p>
                        <g:if test="${todo.item_with_changes instanceof com.k_int.kbplus.Subscription}">
                          <g:link controller="subscriptionDetails" action="index" id="${todo.item_with_changes.id}">${todo.item_with_changes.toString()}</g:link>
                        </g:if>
                        <g:else>
                          <g:link controller="licenseDetails" action="index" id="${todo.item_with_changes.id}">${todo.item_with_changes.toString()}</g:link>
                        </g:else>
                      </p>
                      <p>${message(code:'myinst.change_from', default:'Changes between')} <g:formatDate date="${todo.earliest}" formatName="default.date.format"/></span> ${message(code:'myinst.change_to', default:'and')} <g:formatDate date="${todo.latest}" formatName="default.date.format"/></p>
                    </div>
                  </td>
                </tr>
              </g:each>
                <tr>
                  <td>
                    <g:link action="todo" params="${[shortcode:params.shortcode]}" class="ui button">${message(code:'myinst.todo.submit.label', default:'View To Do List')}</g:link>
                  </td>
                </tr>
              </tbody>
            </table>
        </div><!-- .five -->
        <div class="six wide column">
            <table class="ui celled table dashboard-widget">
              <thead>
                <th>
                  <h5 class="pull-left">${message(code:'announcement.plural', default:'Announcements')}</h5>
                    <span class="pull-right">
                        <i class="warning circle icon large"></i>
                    </span>
                </th>
              </thead>
              <tbody>
              <g:each in="${recentAnnouncements}" var="ra">
                <tr>
                  <td>
                    <div class="pull-left icon">
                        <i class="warning circle icon"></i>
                    </div>
                    <div class="pull-right message">
                      <g:set var="ann_nws" value="${ra.title.replaceAll(' ','')}" />
                      <p><strong>${message(code:"announcement.${ann_nws}", default:"${ra.title}")}</strong></p>
                      <div>
                        <span class="widget-content">${ra.content}</span>
                        <div class="see-more"><a href="">[ ${message(code:'default.button.see_more.label', default:'See More')} ]</a></div>
                      </div> 
                      <p>${message(code:'myinst.ann.posted_by', default:'Posted by')} <em><g:link controller="userDetails" action="show" id="${ra.user?.id}">${ra.user?.displayName}</g:link></em><div> ${message(code:'myinst.ann.posted_on', default:'on')} <g:formatDate date="${ra.dateCreated}" formatName="default.date.format"/></div></p>
                    </div>
                  </td>
                </tr>
              </g:each>
                <tr>
                  <td>
                     <g:link action="announcements" params="${[shortcode:params.shortcode]}" class="ui button">${message(code:'myinst.ann.view.label', default:'View All Announcements')}</g:link>
                  </td>
                </tr>
              </tbody>
            </table>
        </div><!-- .six -->
        <div class="five wide column">
            <table class="ui table dashboard-widget">
                <thead>
                    <th>
                        <h5 class="pull-left">${message(code:'myinst.dash.task.label')}</h5>
                        <span class="pull-right">
                            <i class="checked calendar icon large"></i>
                        </span>
                    </th>
                </thead>
                <tbody>
                    <tr>
                        <td>
                            <input type="submit" class="ui button" value="${message(code:'task.create.new')}" data-semui="modal" href="#modalCreateTask" />
                        </td>
                    </tr>
                    <g:each in="${tasks}" var="tsk">
                        <tr>
                            <td>
                                <strong><g:link controller="task" action="show" params="${[id:tsk.id]}">${tsk.title}</g:link></strong> <br />
                                <g:if test="${tsk.description}">
                                    <span><em>${tsk.description}</em></span> <br />
                                </g:if>
                                <span>
                                    <strong>${tsk.status?.getI10n('value')}</strong>
                                    / fällig am
                                    <g:formatDate format="${message(code:'default.date.format.notime', default:'yyyy-MM-dd')}" date="${tsk?.endDate}"/>
                                </span>
                            </td>
                        </tr>
                    </g:each>
                </tbody>
            </table>

            <g:render template="/templates/tasks/modal" />
            <div class="modal hide fade" id="modalTasks"></div>
        </div>

        <% /*
        <g:if test="${grailsApplication.config.ZenDeskBaseURL}">
        <div class="five wide column">
           <table class="ui table dashboard-widget">
              <thead>
                <th>
                  <h5 class="pull-left">${message(code:'myinst.dash.forum.label', default:'Latest Discussions')}</h5>
                  <img src="${resource(dir: 'images', file: 'icon_discuss.png')}" alt="Discussions" class="pull-right" />
                </th>
              </thead>
              <tbody>
            <g:if test="${forumActivity}">
                <g:each in="${forumActivity}" var="fa">
                  <tr>
                    <td>
                      <div class="pull-left icon">
                        <img src="${resource(dir: 'images', file: 'icon_discuss.png')}" alt="Discussion" />
                      </div>
                      <div class="pull-right message">
                        <p><strong>${fa.title}</strong></p>
                        <p>
                        <g:if test="${fa.result_type=='topic'}">
                          <g:formatDate date="${fa.updated_at}"  formatName="default.date.format"/>
                          <a href="${grailsApplication.config.ZenDeskBaseURL}/entries/${fa.id}">View Topic</a>
                          <a href="${grailsApplication.config.ZenDeskBaseURL}/entries/${fa.id}" title="View Topic (new Window)" target="_blank"><i class="icon-share-alt"></i></a>
                        </g:if>
                        <g:else>
                          <a href="${fa.url}">View ${fa.result_type}</a>
                        </g:else>
                        </p>
                      </div>
                    </td>
                  </tr>
                </g:each>
            </g:if>
            <g:else>
            <tr>
              <td>
                <p>${message(code:'myinst.dash.forum.noActivity', default:'Recent forum activity not available. Please retry later.')}</p>
              </td>
            </tr>
            </g:else>
            <tr>
              <td>
                <g:if test="${!grailsApplication.config.ZenDeskBaseURL.equals('https://projectname.zendesk.com')}">
                  <a href="${grailsApplication.config.ZenDeskBaseURL}/forums" class="btn btn-primary pull-right">${message(code:'myinst.dash.forum.visit', default:'Visit Discussion Forum')}</a>
                </g:if>
                <g:else>
                  <span class="btn btn-primary pull-right disabled">${message(code:'myinst.dash.forum.visit', default:'Visit Discussion Forum')}</span>
                </g:else>
              </td>
            </tr>
          </tbody>
          </table>
        </div><!-- .five -->
        </g:if>
        */ %>
      </div><!-- .grid -->
    </div>

    <r:script>
      $(document).ready(function() {

        $(".widget-content").dotdotdot({
           height: 50,
           after: ".see-more",
           callback: function(isTruncated, orgContent) {
             if(isTruncated) {
               $(this).parent().find('.see-more').show();
             }
           }
         });

         $('.see-more').click(function(e) {

           if ($(this).text() == "[ ${message(code:'default.button.see_more.label', default:'See More')} ]") {
             e.preventDefault();
             $(this).parent().find('.widget-content').trigger('destroy');
             $(this).html("<a href=\"\">[ ${message(code:'default.button.see_less.label', default:'See Less')} ]</a>");

           } else {
             e.preventDefault();
             $(this).parent().find('.widget-content').dotdotdot({
               height: 50,
               after: ".see-more",
               callback: function(isTruncated, orgContent) {
                 if(isTruncated) {
                   $(this).parent().find('.see-more').show();
                 }
               }
             });
             $(this).html("<a href=\"\">[ ${message(code:'default.button.see_more.label', default:'See More')} ]</a>");
           }



           // e.preventDefault();
           // $(this).parent().find('.widget-content').trigger('destroy');
           // $(this).hide();
         });
      });
    </r:script>

  </body>
</html>
