<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI"/>
    <title>${message(code:'laser')} : Login</title>
</head>

<body>

<br />
<br />

<div id='login' class="container">
    <div class='inner'>
        <div class='header'>
            <h1 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon /><g:message code="springSecurity.login.header"/></h1>
        </div>
    <p>
        <semui:messages data="${flash}" />
    </p>
    <semui:card >
        <div class="content">
            <form action='${postUrl}' method='POST' id='loginForm' class='ui form cssform' autocomplete='off'>
                <div class="field">
                    <label for='username'><g:message code="springSecurity.login.username.label"/>:</label>
                    <input type='text' class='text_' name='j_username' id='username'/>
                </div>

                <div class="field">
                    <label for='password'><g:message code="springSecurity.login.password.label"/>:</label>
                    <input type='password' class='text_' name='j_password' id='password'/>
                </div>

                <div class="field" id="remember_me_holder">
                    <label for='remember_me'><g:message code="springSecurity.login.remember.me.label"/></label>
                    <input type='checkbox' class='chk' name='${rememberMeParameter}' id='remember_me' <g:if test='${hasCookie}'>checked='checked'</g:if>/>
                </div>

                <div class="field">
                    <input type='submit' id="submit" class="ui button" value='${message(code: "menu.user.login")}'/>
                </div>
            </form>
            <g:form name="forgottenPassword" id="forgottenPassword" action="resetForgottenPassword" method="post">
                <input type="hidden" id="forgotten_username" name="forgotten_username">
                <div class="field">
                    <a id="forgotten" href="#">${message(code:'menu.user.forgottenPassword')}</a>
                </div>
            </g:form>
        </div>
    </semui:card>
    </div>
</div>
<r:script>
    (function () {
        document.forms['loginForm'].elements['j_username'].focus();
    })();

    $("#forgotten").click(function(e){
        e.preventDefault();
        var username = prompt("<g:message code="menu.user.forgottenPassword.username"/>");
        console.log(username);
        if(username){
            $("#forgotten_username").val(username);
            $("#forgottenPassword").submit();
        }
    });
</r:script>
</body>
</html>
