<!doctype html>
<html>
<head>
    <meta name="layout" content="semanticUI">
    <title>${message(code:'laser')} : ${message(code:'menu.yoda.cacheInfo')}</title>
</head>
<body>

<laser:serviceInjection />

<semui:breadcrumbs>
    <semui:crumb message="menu.yoda.dash" controller="yoda" action="index"/>
    <semui:crumb message="menu.yoda.cacheInfo" class="active"/>
</semui:breadcrumbs>

<%
    // EXAMPLE:
    sessionCache = contextService.getSessionCache()
    sessionCache.put("test", "${new Date()}")
    sessionCache.get("test")
%>
<br />
<h2 class="ui icon header la-clear-before la-noMargin-top"><semui:headerIcon />${message(code:'menu.yoda.cacheInfo')}</h2>

<g:set var="sessionCache" value="${contextService.getSessionCache()}" />
<h3 class="ui header">SessionCache <span class="ui label">${sessionCache.getSession().class}</span></h3>

<div class="ui segment">
    <p>${sessionCache.getSession().id}</p>

    <g:link class="ui button small"
            controller="yoda" action="cacheInfo" params="[cmd: 'clearCache', type: 'session']">Cache leeren</g:link>

    <g:if test="${sessionCache.list().size() > 0}">
        <dl>
            <g:each in="${contextService.getSessionCache().list()}" var="entry">
                <dt>${entry.key}</dt>
                <dd>
                    ${entry.value} <br />
                    <em>${entry.value.class.name}</em>
                </dd>
            </g:each>
        </dl>
    </g:if>

</div>

<hr />

<h3 class="ui header">Ehcache <span class="ui label">${ehcacheManager.class}</span></h3>

<%
    List ehCaches = [
        ehcacheManager.getCacheNames().findAll { it -> !it.startsWith('com.k_int.') && !it.startsWith('de.laser.')},
        ehcacheManager.getCacheNames().findAll { it -> it.startsWith('com.k_int.') || it.startsWith('de.laser.')}
    ]
%>

<g:each in="${ehCaches}" var="ehCache">

    <g:each in="${ehCache}" var="cacheName">
        <g:set var="cache" value="${ehcacheManager.getCache(cacheName)}" />
        <g:set var="cacheStats" value="${cache.getStatistics()}" />

        <h4 class="ui header">${cacheName}
            <span class="ui label">${cache.class}</span>
            <%-- <span class="ui label">

                try {
                    println "disk: ${Math.round(cacheStats.getLocalDiskSizeInBytes() / 1024)} kb, "
                } catch (Exception e) {
                    println "error, "
                }
                try {
                    println "heap: ${Math.round(cacheStats.getLocalHeapSizeInBytes() / 1024)} kb / "
                } catch (Exception e) {
                    println "error / "
                }
                try {
                    println "${Math.round(cacheStats.getLocalOffHeapSizeInBytes() / 1024)} kb"
                } catch (Exception e) {
                    println "error"
                }

            </span> --%>
        </h4>

        <div class="ui segments">

            <div class="ui segment">

                <g:link class="ui button small" controller="yoda" action="cacheInfo" params="[cmd: 'clearCache', cache: cacheName, type: 'ehcache']">Cache leeren</g:link>

                <button class="ui button small" onclick="$(this).parent('.segment').next('.cacheConfig').toggleClass('hidden')">Konfiguration</button>

                <g:if test="${cache.getKeysWithExpiryCheck().size() > 0}">
                    <button class="ui button small positive" onclick="$(this).parent('.segment').find('.cacheContent').toggleClass('hidden')">Elemente: ${cache.getKeys().size()}</button>

                    <div class="cacheContent hidden">
                        <dl>
                        <g:each in="${cache.getKeys().toSorted()}" var="key">
                            <g:set var="element" value="${cache.get(key)}" />
                            <g:if test="${element}">
                                <dt>
                                    <g:set var="ceKey" value="${element.getObjectKey() instanceof String ? element.getObjectKey() : element.getObjectKey().id}" />
                                    ${ceKey} -
                                    creation=${new Date(element.getCreationTime()).format('HH:mm:ss')},
                                    lastAccess=${new Date(element.getLastAccessTime()).format('HH:mm:ss')},
                                    version=${element.version},
                                    hitCount=${element.hitCount}
                                </dt>
                                <dd>
                                    <g:set var="objectValue" value="${element.getObjectValue()}" />
                                    <g:if test="${objectValue.getClass().getSimpleName() != 'Item'}">
                                        ${objectValue}
                                    </g:if>
                                    <g:else>
                                        ${objectValue.getValue()}
                                    </g:else>
                                </dd>
                            </g:if>
                        </g:each>
                        </dl>
                    </div>

                </g:if>
            </div>

            <div class="ui secondary segment cacheConfig hidden">
                <%
                    Map<String, Object> cacheConfig = cache.getCacheConfiguration().getProperties().findAll {
                        it.value instanceof Number || it.value instanceof Boolean || it.value instanceof String
                    }.sort{ it.key.toLowerCase() }

                    print cacheConfig.collect{ "${it.key} = ${it.value}" }
                %>
            </div>

        </div>
    </g:each>

</g:each>

<hr />

<h3 class="ui header">Hibernate <span class="ui label">${hibernateSession.class}</span></h3>

<div class="ui segment">
    <g:each in="${hibernateSession.statistics}" var="hst">
        ${hst} <br />
    </g:each>
</div>

<hr />

</body>
</html>