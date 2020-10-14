
<%@ page import="com.k_int.kbplus.Org" %>
<!doctype html>
<html>
  <head>
    <meta name="layout" content="semanticUI">
    <g:set var="entityName" value="${message(code: 'org.label')}" />
    <title>${message(code:'laser')} : <g:message code="default.show.label" args="[entityName]" /></title>
  </head>
  <body>

    <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />${orgInstance.name}</h1>

    <semui:messages data="${flash}" />

    <div>

        <dl>
          <g:if test="${orgInstance?.name}">
            <dt><g:message code="default.name.label" /></dt>
            
              <dd><g:fieldValue bean="${orgInstance}" field="name"/></dd>
          </g:if>
        
			<g:if test="${orgInstance?.addresses}">
				<dt><g:message code="org.addresses.label" /></dt>
				<g:each in="${orgInstance?.addresses?.sort{it?.type?.getI10n('value')}}" var="a">
					<g:if test="${a.org}">
						<g:render template="/templates/cpa/address" model="${[address: a]}"></g:render>
					</g:if>
				</g:each>
			</g:if>
		
			<g:if test="${orgInstance?.contacts}">
				<dt><g:message code="org.contacts.label" /></dt>
				<g:each in="${orgInstance?.contacts?.toSorted()}" var="c">
					<g:if test="${c.org}">
						<g:render template="/templates/cpa/contact" model="${[contact: c]}"></g:render>
					</g:if>
				</g:each>
			</g:if>

        	<g:if test="${orgInstance?.prsLinks}">
				<dt><g:message code="org.prsLinks.label" /></dt>
				<g:each in="${orgInstance?.prsLinks?.toSorted()}" var="pl">
					<g:if test="${pl?.functionType?.value && pl?.prs.isPublic}">
						<g:render template="/templates/cpa/person_details" model="${[
                                personRole: pl,
                                tmplConfigShow: 'address'
                        ]}"></g:render>
					</g:if>
				</g:each>
			</g:if>
		
          <g:if test="${orgInstance?.ipRange}">
            <dt><g:message code="org.ipRange.label" /></dt>
            
              <dd>${orgInstance.ipRange}</dd>
            
          </g:if>
        
          <g:if test="${orgInstance?.sector}">
            <dt><g:message code="org.sector.label" /></dt>
            
              <dd>${orgInstance.sector.getI10n('value')}</dd>
            
          </g:if>

      <g:if test="${orgInstance?.membership}">
        <dt><g:message code="org.membership.label" /></dt>

        <dd>${orgInstance.membership.getI10n('value')}</dd>

      </g:if>
        
          <g:if test="${orgInstance?.ids?.sort{it?.ns?.ns}}">
            <dt><g:message code="org.ids.label" /></dt>
              <g:each in="${orgInstance.ids?.sort{it?.ns?.ns}}" var="i">
              <dd><g:link controller="identifier" action="show" id="${i.id}">${i?.ns?.ns} : ${i?.value}</g:link></dd>
              </g:each>
          </g:if>

          <g:if test="${orgInstance?.outgoingCombos}">
            <dt><g:message code="org.outgoingCombos.label" /></dt>
            <g:each in="${orgInstance.outgoingCombos}" var="i">
              <dd>${i.type?.value} - <g:link controller="organisation" action="show" id="${i.toOrg.id}">${i.toOrg?.name}</g:link>
                (<g:each in="${i?.toOrg?.ids?.sort{it?.ns?.ns}}" var="id">
                  ${id.ns.ns}: ${id.value}
                </g:each>)
              </dd>
            </g:each>
          </g:if>

          <g:if test="${orgInstance?.incomingCombos}">
            <dt><g:message code="org.incomingCombos.label" /></dt>
            <g:each in="${orgInstance.incomingCombos}" var="i">
              <dd>${i.type?.value} - <g:link controller="organisation" action="show" id="${i.toOrg.id}">${i.fromOrg?.name}</g:link>
                (<g:each in="${i?.fromOrg?.ids?.sort{it?.ns?.ns}}" var="id">
                  ${id.ns.ns}: ${id.value}
                </g:each>)
              </dd>

            </g:each>
          </g:if>

          <g:if test="${orgInstance?.links}">
            <dt><g:message code="org.links.other.label" default="Other org links" /></dt>
            <dd>
              <g:each in="${sorted_links}" var="rdv_id,link_cat">
                <div>
                  <span style="font-weight:bold;">${link_cat.rdv.getI10n('value')} (${link_cat.total})</span>
                </div>
                <ul>
                  <g:each in="${link_cat.links}" var="i">
                    <li>
                      <g:if test="${i.pkg}">
                        <g:link controller="package" action="show" id="${i.pkg.id}">
                          ${message(code:'package.label')}: ${i.pkg.name} (${i.pkg?.packageStatus?.getI10n('value')})
                        </g:link>
                      </g:if>
                      <g:if test="${i.sub}">
                        <g:link controller="subscription" action="index" id="${i.sub.id}">
                          ${message(code:'default.subscription.label')}: ${i.sub.name} (${i.sub.status?.getI10n('value')})
                        </g:link>
                      </g:if>
                      <g:if test="${i.lic}">
                        <g:link controller="license" action="show" id="${i.lic.id}">
                          ${message(code:'license.label')}: ${i.lic.reference ?: i.lic.id}
                        </g:link>
                      </g:if>
                      <g:if test="${i.title}">
                        <g:link controller="title" action="show" id="${i.title.id}">
                          ${message(code:'title.label')}: ${i.title.title} (${i.title.status?.getI10n('value')})
                        </g:link>
                      </g:if> 
                    </li>
                  </g:each>
                </ul>
                <g:set var="local_offset" value="${params[link_cat.rdvl] ? Long.parseLong(params[link_cat.rdvl]) : null}" />
                <div>
                  <g:if test="${link_cat.total > 10}">
                    ${message(code:'default.paginate.offset', args:[(local_offset ?: 1),(local_offset ? (local_offset + 10 > link_cat.total ? link_cat.total : local_offset + 10) : 10), link_cat.total])}
                  </g:if>
                </div>
                <div>
                  <g:if test="${link_cat.total > 10 && local_offset}">
                    <g:set var="os_prev" value="${local_offset > 9 ? (local_offset - 10) : 0}" />
                    <g:link controller="organisation" action="info" id="${orgInstance.id}" params="${params + ["rdvl_${rdv_id}": os_prev]}">${message(code:'default.paginate.prev')}</g:link>
                  </g:if>
                  <g:if test="${link_cat.total > 10 && ( !local_offset || ( local_offset < (link_cat.total - 10) ) )}">
                    <g:set var="os_next" value="${local_offset ? (local_offset + 10) : 10}" />
                    <g:link controller="organisation" action="info" id="${orgInstance.id}" params="${params + ["rdvl_${rdv_id}": os_next]}">${message(code:'default.paginate.next')}</g:link>
                  </g:if>
                </div>
              </g:each>
            </dd>
          </g:if>
        
          <g:if test="${orgInstance?.gokbId}">
            <dt><g:message code="org.gokbId.label"/></dt>
            
              <dd><g:fieldValue bean="${orgInstance}" field="gokbId"/></dd>
            
          </g:if>
        
        
        </dl>


    </div>
  </body>
</html>
