<%@ page import="de.laser.Package" %>
<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code:'laser')} : Package Tipps LAS:eR and GOKB</title>
</head>

<body>
<semui:breadcrumbs>
    <semui:crumb message="menu.admin.dash" controller="admin" action="index" />
    <semui:crumb text="Package Tipps LAS:eR and GOKB" class="active"/>
</semui:breadcrumbs>
<br>
<h1 class="ui header la-noMargin-top"> Package Tipps LAS:eR and GOKB</h1>

%{--<g:link action="checkPackageTIPPs" params="${params+[onlyNotEqual: true]}" class="ui button">Show only where Tipps Not Equal</g:link>--}%
<br>
<br>
<div class="ui grid">

    <div>
        <g:if test="${records}">
            <div>
                <table class="ui celled la-selectable la-table table">
                    <thead>
                    <tr>
                        <th>${message(code:'package.show.pkg_name')} in GOKB</th>
                        <th>${message(code:'package.show.pkg_name')} in LAS:eR</th>
                        <%--<th>${message(code:'consortium.label', default:'Consortium')}</th>--%>
                        <th>${message(code:'tipp.plural')} in GOKB</th>
                        <th>${message(code:'tipp.plural')} in LAS:eR</th>
                    </thead>
                    <tbody>

                    <g:each in="${records}" var="hit" >
                        <tr>
                            <td>
                                ${hit.name} <a target="_blank" href="${hit.url ? hit.url+'/gokb/public/packageContent/'+hit.id : '#'}" ><i title="GOKB Link" class="external alternate icon"></i></a>
                            </td>

                            <g:if test="${Package.findByGokbId(hit.uuid)}">
                                <g:set var="style" value="${(Package.findByGokbId(hit.uuid)?.name != hit.name) ? "style=background-color:red;":''}"/>
                                <td ${style}>
                                    <g:link controller="package" target="_blank" action="current" id="${Package.findByGokbId(hit.uuid).id}">${Package.findByGokbId(hit.uuid).name}</g:link>
                                </td>
                            </g:if>
                            <g:else>
                                <td>
                                    No Package in LAS:eR
                                </td>
                            </g:else>
                            <td>
                                <strong>${hit.titleCount?:'0'} </strong>
                            </td>
                            <g:if test="${Package.findByGokbId(hit.uuid)}">
                                <g:set var="laserTipps" value="${(Package.findByGokbId(hit.uuid)?.tipps?.findAll {it.status.value == 'Current'}.size().toString())}" />
                                <g:set var="style" value="${(laserTipps != hit.titleCount && hit.titleCount != '0') ? "style=background-color:red;":''}"/>
                                <td ${style}>
                                    <strong>${laserTipps ?:'0'} </strong>
                                </td>
                            </g:if>
                            <g:else>
                                <td>
                                    No Tipps.
                                </td>
                            </g:else>

                        </tr>
                    </g:each>
                    </tbody>
                </table>
            </div>

            <semui:paginate action="${actionName}" controller="${controllerName}" params="${params}"
                            next="${message(code: 'default.paginate.next')}"
                            prev="${message(code: 'default.paginate.prev')}"
                            max="${max}"
                            total="${resultsTotal2}"/>
        </g:if>

    </div>

</div>
</body>
</html>