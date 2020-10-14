<!doctype html>
<html>
<head>
    <meta name="layout" content="public"/>
    <title>Terms and Conditions | ${message(code: 'laser')}</title>
</head>

<body class="public">
    <g:render template="public_navbar" contextPath="/templates" model="['active': 'about']"/>

    <div class="ui container">
        <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />Terms and Conditions</h1>

        <div class="ui grid">
            <div class="twelve wide column">
                <markdown:renderHtml><g:dbContent key="kbplus.termsAndConditions"/></markdown:renderHtml>
            </div>

            <div class="four wide column">
                <g:render template="/templates/loginDiv"/>
            </div>
        </div>
    </div>
</body>
</html>
