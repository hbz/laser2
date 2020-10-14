<dl>
    <dt><g:message code="license.responsibilites" /></dt>
    <dd>

        <g:each in="${visiblePrsLinks}" var="pr">
            <g:if test="${pr.org}">

                <g:link controller="organisation" action="show" id="${pr.org.id}">${pr.org.name}</g:link>
                <div class="ui list">
                    <div class="item">
                        <g:if test="${! pr.prs.isPublic}">
                            <span class="la-popup-tooltip la-delay" data-content="${message(code:'address.private')}" data-position="top right">
                                <i class="address card outline icon"></i>
                            </span>
                        </g:if>
                        <g:else>
                            <span  class="la-popup-tooltip la-delay" data-content="${message(code:'address.public')}" data-position="top right">
                                <i class="address card icon"></i>
                            </span>
                        </g:else>

                        <div class="content">
                            <g:link controller="person" action="show" id="${pr.prs.id}">${pr.prs}</g:link>

                            <g:if test="${true || tmplShowFunction}">
                                <g:if test="${pr.functionType}">
                                    (${(pr.functionType).getI10n("value")})
                                </g:if>
                                <g:if test="${pr.responsibilityType}">
                                    (${(pr.responsibilityType).getI10n("value")})
                                </g:if>
                            </g:if>
                        </div>
                    </div>
                </div>
                <g:if test="${editable}">
                    <div class="ui icon negative buttons">
                        <g:link class="ui button" controller="ajax" action="delPrsRole" id="${pr.id}"
                            onclick="return confirm('${message(code:'template.orgLinks.delete.warn')}')">
                            <i class="unlink icon"></i>
                        </g:link>
                    </div>
                    <br />
                </g:if>
            </g:if>
        </g:each>
    </dd>
</dl>
<g:if test="${editable}">
    <dl>
        <dt></dt>
        <dd>
            <input class="ui button"
                   value="${message(code: 'default.add.label', args: [message(code: 'person.label')])}"
                   data-semui="modal"
                   data-href="#prsLinksModal" />
        </dd>
    </dl>
</g:if>
