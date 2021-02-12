<div class="la-icon-list">

    <semui:listIcon type="${tipp.titleType}"/>
    <g:if test="${ie}">
        <g:link controller="issueEntitlement" id="${ie.id}"
                action="show"><strong>${ie.tipp.name}</strong>
        </g:link>
    </g:if>
    <g:else>
        <g:link controller="tipp" id="${tipp.id}"
                action="show"><strong>${tipp.name}</strong>
        </g:link>
    </g:else>

    <g:if test="${tipp.hostPlatformURL}">
        <semui:linkIcon
                href="${tipp.hostPlatformURL.startsWith('http') ? tipp.hostPlatformURL : 'http://' + tipp.hostPlatformURL}"/>
    </g:if>
    <br/>

    <g:if test="${!showCompact}">
        <br/>
    </g:if>

    <g:each in="${tipp.ids.sort { it.ns.ns }}" var="title_id">
        <span class="ui small basic image label">
            ${title_id.ns.ns}: <div class="detail">${title_id.value}</div>
        </span>
    </g:each>
<!--                  ISSN:<strong>${tipp.getIdentifierValue('ISSN') ?: ' - '}</strong>,
                  eISSN:<strong>${tipp.getIdentifierValue('eISSN') ?: ' - '}</strong><br />-->
    <br/>

    <g:if test="${!showCompact}">
        <br/>
    </g:if>

    <div class="item">
        <semui:listIcon type="${tipp.titleType}"/>

        <div class="content">
            ${showCompact ? '' : message(code: 'title.type.label') + ':'} ${tipp.titleType}
        </div>
    </div>

    <g:if test="${ie && (ie.medium || showEmptyFields)}">
        <div class="item">
            <i class="grey icon la-popup-tooltip la-delay"
               data-content="${message(code: 'tipp.medium')}"></i>

            <div class="content">
                ${showCompact ? '' : message(code: 'tipp.medium') + ':'} ${ie.medium?.getI10n('value')}
            </div>
        </div>
    </g:if>
    <g:else>
        <g:if test="${(tipp.medium || showEmptyFields)}">
            <div class="item">
                <i class="grey medium icon la-popup-tooltip la-delay"
                   data-content="${message(code: 'tipp.medium')}"></i>

                <div class="content">
                    ${showCompact ? '' : message(code: 'tipp.medium') + ':'} ${tipp.medium?.getI10n('value')}
                </div>
            </div>
        </g:if>
    </g:else>


    <g:if test="${ie && (ie.status || showEmptyFields)}">
        <div class="item">
            <i class="grey key icon la-popup-tooltip la-delay"
               data-content="${message(code: 'default.status.label')}"></i>

            <div class="content">
                ${showCompact ? '' : message(code: 'default.status.label') + ':'} ${ie.status?.getI10n('value')}
            </div>
        </div>
    </g:if>
    <g:else>
        <g:if test="${(tipp.status || showEmptyFields)}">
            <div class="item">
                <i class="grey key icon la-popup-tooltip la-delay"
                   data-content="${message(code: 'default.status.label')}"></i>

                <div class="content">
                    ${showCompact ? '' : message(code: 'default.status.label') + ':'} ${tipp.status?.getI10n('value')}
                </div>
            </div>
        </g:if>
    </g:else>

    <g:if test="${tipp.titleType.contains('Book') && (tipp.volume || showEmptyFields)}">
        <div class="item">
            <i class="grey icon la-books la-popup-tooltip la-delay"
               data-content="${message(code: 'tipp.volume')}"></i>

            <div class="content">
                ${showCompact ? '' : message(code: 'tipp.volume') + ':'} ${tipp.volume}
            </div>
        </div>
    </g:if>

    <g:if test="${tipp.titleType.contains('Book') && (tipp.firstAuthor || tipp.firstEditor || showEmptyFields)}">
        <div class="item">
            <i class="grey icon user circle la-popup-tooltip la-delay"
               data-content="${message(code: 'author.slash.editor')}"></i>

            <div class="content">
                ${showCompact ? '' : message(code: 'author.slash.editor') + ':'} ${tipp.getEbookFirstAutorOrFirstEditor()}
            </div>
        </div>
    </g:if>

    <g:if test="${tipp.titleType.contains('Book') && (tipp.editionStatement || showEmptyFields)}">
        <div class="item">
            <i class="grey icon copy la-popup-tooltip la-delay"
               data-content="${message(code: 'title.type.label')}"></i>

            <div class="content">
                ${showCompact ? '' : message(code: 'title.type.label') + ':'} ${tipp.editionStatement}
            </div>
        </div>
    </g:if>

    <g:if test="${tipp.titleType.contains('Book') && (tipp.summaryOfContent || showEmptyFields)}">
        <div class="item">
            <i class="grey icon desktop la-popup-tooltip la-delay"
               data-content="${message(code: 'title.summaryOfContent.label')}"></i>

            <div class="content">
                ${showCompact ? '' : message(code: 'title.summaryOfContent.label') + ':'} ${tipp.summaryOfContent}
            </div>
        </div>
    </g:if>

    <g:if test="${(tipp.seriesName || showEmptyFields)}">
        <div class="item">
            <i class="grey icon list la-popup-tooltip la-delay"
               data-content="${message(code: 'title.seriesName.label')}"></i>

            <div class="content">
                ${showCompact ? '' : message(code: 'title.seriesName.label') + ':'} ${tipp.seriesName}
            </div>
        </div>
    </g:if>

    <g:if test="${(tipp.subjectReference || showEmptyFields)}">
        <div class="item">
            <i class="grey icon comment alternate la-popup-tooltip la-delay"
               data-content="${message(code: 'title.subjectReference.label')}"></i>

            <div class="content">
                ${showCompact ? '' : message(code: 'title.subjectReference.label') + ':'} ${tipp.subjectReference}
            </div>
        </div>

    </g:if>

    <g:if test="${(tipp.statusReason || showEmptyFields)}">
        <div class="item">
            <i class="grey edit icon la-popup-tooltip la-delay"
               data-content="${message(code: 'tipp.show.statusReason')}"></i>

            <div class="content">
                ${showCompact ? '' : message(code: 'tipp.show.statusReason') + ':'} ${tipp.statusReason?.getI10n("value")}
            </div>
        </div>

    </g:if>

    <g:if test="${(tipp.delayedOA || showEmptyFields)}">
        <div class="item">
            <i class="grey lock open icon la-popup-tooltip la-delay"
               data-content="${message(code: 'tipp.delayedOA')}"></i>

            <div class="content">
                ${showCompact ? '' : message(code: 'tipp.delayedOA') + ':'} ${tipp.delayedOA?.getI10n("value")}"
            </div>
        </div>
    </g:if>

    <g:if test="${(tipp.hybridOA || showEmptyFields)}">
        <div class="item">
            <i class="grey lock open alternate icon la-popup-tooltip la-delay"
               data-content="${message(code: 'tipp.hybridOA')}"></i>

            <div class="content">
                ${showCompact ? '' : message(code: 'tipp.hybridOA') + ':'} ${tipp.hybridOA?.getI10n("value")}
            </div>
        </div>
    </g:if>

    <g:if test="${(tipp.payment || showEmptyFields)}">
        <div class="item">
            <i class="grey money icon la-popup-tooltip la-delay"
               data-content="${message(code: 'tipp.paymentType')}"></i>

            <div class="content">
                ${showCompact ? '' : message(code: 'tipp.paymentType') + ':'} ${tipp.payment?.getI10n("value")}
            </div>
        </div>
    </g:if>

    <g:if test="${ie && (ie.availabilityStatus || showEmptyFields)}">
        <g:if test="${ie.availabilityStatus?.value == 'Expected'}">
            ${message(code: 'default.on')} <g:formatDate
                format="${message(code: 'default.date.format.notime')}"
                date="${ie.accessStartDate}"/>
        </g:if>

        <g:if test="${ie.availabilityStatus?.value == 'Expired'}">
            ${message(code: 'default.on')} <g:formatDate
                format="${message(code: 'default.date.format.notime')}"
                date="${ie.accessEndDate}"/>
        </g:if>
    </g:if>

    <g:if test="${showPackage}">
        <g:if test="${tipp.pkg.id}">
            <div class="item">
                <i class="grey icon gift scale la-popup-tooltip la-delay"
                   data-content="${message(code: 'package.label')}"></i>

                <div class="content">
                    <g:link controller="package" action="show"
                            id="${tipp.pkg.id}">${tipp.pkg.name}</g:link>
                </div>
            </div>
        </g:if>
    </g:if>
    <g:if test="${showPlattform}">
        <g:if test="${tipp.platform.name}">
            <div class="item">
                <i class="grey icon cloud la-popup-tooltip la-delay"
                   data-content="${message(code: 'tipp.platform')}"></i>

                <div class="content">
                    <g:if test="${tipp.platform.name}">
                        <g:link controller="platform" action="show"
                                id="${tipp.platform.id}">
                            ${tipp.platform.name}
                        </g:link>
                    </g:if>
                    <g:else>
                        ${message(code: 'default.unknown')}
                    </g:else>
                </div>
            </div>
        </g:if>
    </g:if>

    <div class="la-title">${message(code: 'default.details.label')}</div>
    <g:if test="${controllerName != 'tipp' && tipp.id}">
        <g:link class="ui icon tiny blue button la-js-dont-hide-button la-popup-tooltip la-delay"
                data-content="${message(code: 'laser')}"
                target="_blank"
                controller="tipp" action="show"
                id="${tipp.id}">
            <i class="book icon"></i>
        </g:link>
    </g:if>

    <g:each in="${apisources}" var="gokbAPI">
        <g:if test="${tipp.gokbId}">
            <a role="button" class="ui icon tiny blue button la-js-dont-hide-button la-popup-tooltip la-delay"
               data-content="${message(code: 'gokb')}"
               href="${gokbAPI.editUrl ? gokbAPI.editUrl + '/gokb/resource/show/?id=' + tipp.gokbId : '#'}"
               target="_blank"><i class="la-gokb  icon"></i>
            </a>
        </g:if>
    </g:each>

</div>




