package de.laser

class LaserInjectionTagLib {

    static namespace = "laser"

    // <laser:serviceInjection/>
    def serviceInjection = { attrs, body ->

        g.set( var:'accessService',     bean:'accessService' )
        g.set( var:'auditService',      bean:'auditService' )
        g.set( var:'cacheService',      bean:'cacheService' )
        g.set( var:'contextService',    bean:'contextService' )
        g.set( var:'escapeService',     bean: 'escapeService')
        g.set( var:'filterService',     bean:'filterService' )
        g.set( var:'formService',       bean:'formService' )
        g.set( var:'genericOIDService', bean:'genericOIDService' )
        g.set( var:'GOKbService',       bean:'GOKbService' )
        g.set( var:'instAdmService',    bean:'instAdmService' )
        g.set( var:'linksGenerationService',    bean:'linksGenerationService' )
        g.set( var:'orgDocumentService',        bean:'orgDocumentService' )
        g.set( var:'pendingChangeService',      bean:'pendingChangeService')
        g.set( var:'springSecurityService',     bean:'springSecurityService' )
        g.set( var:'subscriptionsQueryService', bean:'subscriptionsQueryService' )
        g.set( var:'surveyService',     bean:'surveyService' )
        g.set( var:'taskService',       bean:'taskService' )
        g.set( var:'yodaService',       bean:'yodaService' )
    }
}
