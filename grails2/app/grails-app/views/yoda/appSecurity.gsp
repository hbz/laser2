<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI">
    <title>${message(code:'laser')} : ${message(code:'menu.yoda.security')}</title>
</head>
<body>

<semui:breadcrumbs>
    <semui:crumb message="menu.yoda.dash" controller="yoda" action="index"/>
    <semui:crumb message="menu.yoda.security" class="active"/>
</semui:breadcrumbs>
<br>
<h2 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />${message(code:'menu.yoda.security')}</h2>

<h3 class="ui header">Hierarchical Global Roles</h3>

<div class="secInfoWrapper">
    <div class="ui list">
        <div class="item">
            <span class="ROLE_YODA">ROLE_YODA</span> &rArr;
            <span class="ROLE_ADMIN">ROLE_ADMIN</span> &rArr;
            <span class="ROLE_USER">ROLE_USER</span> &rArr;
            <span class="IS_AUTHENTICATED_FULLY">IS_AUTHENTICATED_FULLY</span>
        </div>
    </div>
</div>

<h3 class="ui header">Independent Global Roles</h3>

<div class="secInfoWrapper">
    <div class="ui list">
        <div class="item">
            <span class="ROLE_GLOBAL_DATA">ROLE_GLOBAL_DATA</span> |
            <span class="ROLE_ORG_EDITOR">ROLE_ORG_EDITOR</span> |
            <span class="ROLE_PACKAGE_EDITOR">ROLE_PACKAGE_EDITOR</span> |
            <span class="ROLE_STATISTICS_EDITOR">ROLE_STATISTICS_EDITOR</span> |
            <span class="ROLE_TICKET_EDITOR">ROLE_TICKET_EDITOR</span> |
            <span class="ROLE_API">ROLE_API</span>
        </div>
    </div>
</div>

<h3 class="ui header">Hierarchical Org Roles (Customer Types)</h3>

<div class="secInfoWrapper">
    <div class="ui list">
        <div class="item">
            <span class="ROLE_YODA">ORG_INST_COLLECTIVE</span> &rArr;
            <span class="IS_AUTHENTICATED_FULLY">ORG_INST</span> &rArr;
            <span class="ROLE_USER">ORG_BASIC_MEMBER</span> |
            <span class="ROLE_DATAMANAGER">ORG_CONSORTIUM</span> |
            <span class="ROLE_API">FAKE</span>
        </div>
    </div>
</div>

<h3 class="ui header">Hierarchical User Roles</h3>

<div class="secInfoWrapper">
    <div class="ui list">
        <div class="item">
            <span>INST_ADM</span> &rArr;
            <span>INST_EDITOR</span> &rArr;
            <span>INST_USER</span>  &nbsp; (implizite Prüfung auf <span class="ROLE_USER">ROLE_USER</span>)
        </div>
        <div class="item">
            <span class="ROLE_YODA">ROLE_YODA</span> und <span class="ROLE_ADMIN">ROLE_ADMIN</span> liefern <code>TRUE</code>
        </div>
    </div>
</div>

<h3 class="ui header">Controller Annotations</h3>

<div class="ui grid">
    <div class="sixteen wide column">

        <div class="secInfoWrapper secInfoWrapper2">
            <g:each in="${controller}" var="c">

                <h5 class="ui header" id="jumpMark_${c.key}">
                    ${c.key}
                    <g:each in="${c.value.secured}" var="cSecured">
                        <span class="${cSecured}">${cSecured}</span> &nbsp;
                    </g:each>
                </h5>

                <div class="ui segment">
                    <div class="ui divided list">
                        <g:each in="${c.value.methods}" var="method">
                            <div class="item">
                                <g:link controller="${c.key.split('Controller')[0]}" action="${method.key}">${method.key}</g:link>

                                <g:each in="${method.value}" var="v">
                                    <g:if test="${v instanceof String}">
                                        <span class="${v}">${v}</span>
                                    </g:if>
                                    <g:else>
                                        <g:if test="${v.value}">
                                            <span class="${v.key}">${v.key}: ${v.value}</span>
                                        </g:if>
                                    </g:else>
                                </g:each>
                            </div>
                        </g:each>
                    </div>
                </div>
            </g:each>
        </div>

    </div>
</div>

</body>
</html>

