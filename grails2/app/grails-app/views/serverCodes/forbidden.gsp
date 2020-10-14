<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code: 'laser')} - ${message(code: 'serverCode.forbidden.message2')}</title>
</head>

<body>
<semui:messages data="${flash}"/>
<semui:card>
    <div class="content">
        <h3 class="ui header">
            ${message(code: 'serverCode.forbidden.message2')}
        </h3>

        <g:if test="${!flash.error}">
            <div>
                <p>${message(code: 'serverCode.forbidden.message')}</p>
                <br/>

                <p>
                    <button class="ui button"
                            onclick="window.history.back()">${message(code: 'default.button.back')}</button>
                </p>
            </div>
        </g:if>
    </div>
</semui:card>
</body>
</html>