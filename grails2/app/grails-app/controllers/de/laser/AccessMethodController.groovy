package de.laser


import com.k_int.kbplus.auth.User
import de.laser.controller.AbstractDebugController
import de.laser.helper.DateUtil
import grails.plugin.springsecurity.annotation.Secured
import org.springframework.dao.DataIntegrityViolationException

import java.text.SimpleDateFormat

class AccessMethodController extends AbstractDebugController {

    def springSecurityService
    def contextService
    static allowedMethods = [create: ['GET', 'POST'], edit: ['GET', 'POST'], update: ['GET', 'POST'], delete: 'GET']
    
    @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
    def create() {
        params.max = params.max ?: ((User) springSecurityService.getCurrentUser())?.getDefaultPageSize()

        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        if (params.validFrom) {
            params.validFrom = sdf.parse(params.validFrom)
        } else {
            //params.validFrom =  sdf.parse(new Date().format( 'dd.MM.yyyy' ));
        }
        if (params.validTo) {
            params.validTo = sdf.parse(params.validTo)
        } else {
            //params.validTo =sdf.parse(new Date().format( 'dd.MM.yyyy' ));
        }
        PlatformAccessMethod accessMethod = new PlatformAccessMethod(params)

        if (params.validTo && params.validFrom && params.validTo.before(params.validFrom)) {
            flash.error = message(code: 'accessMethod.dateValidationError', args: [message(code: 'accessMethod.label'), accessMethod.accessMethod])
        } else {

            accessMethod.platf = Platform.get(params.platfId)

            accessMethod.accessMethod = RefdataValue.get(params.accessMethod)

            accessMethod.save(flush: true)
            accessMethod.errors.toString()

            flash.message = message(code: 'accessMethod.create.message', args: [accessMethod.accessMethod.getI10n('value')])
        }

        if (params.redirect) {
            redirect(url: request.getHeader('referer'), params: params)
        }
        else {
            redirect controller: 'platform', action: 'AccessMethods', id: params.platfId
        }
        
        //[personInstanceList: Person.list(params), personInstanceTotal: Person.count()]
    }
    
    @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
    def delete() {
        PlatformAccessMethod accessMethod = PlatformAccessMethod.get(params.id)

        Platform platform = accessMethod.platf
        Long platformId = platform.id
        
        try {
            accessMethod.delete(flush: true)
			flash.message = message(code: 'accessMethod.deleted', args: [accessMethod.accessMethod.getI10n('value')])
            redirect controller: 'platform', action: 'AccessMethods', id: platformId
        }
        catch (DataIntegrityViolationException e) {
			flash.message = message(code: 'default.not.deleted.message', args: [message(code: 'address.label'), params.id])
            redirect action: 'show', id: params.id
        }
    }


    @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
    def edit() {
        PlatformAccessMethod accessMethod= PlatformAccessMethod.get(params.id)
        Platform platf = accessMethod.platf

        [accessMethod: accessMethod, platfId: platf.id]

    }

    @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
    def update() {
        PlatformAccessMethod accessMethod= PlatformAccessMethod.get(params.id)
        Platform platf = accessMethod.platf


        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
        if (params.validFrom) {
            params.validFrom = sdf.parse(params.validFrom)
        } else {
            //params.validFrom =  sdf.parse(new Date().format( 'dd.MM.yyyy' ));
        }
        if (params.validTo) {
            params.validTo = sdf.parse(params.validTo)
        } else {
            //params.validTo =sdf.parse(new Date().format( 'dd.MM.yyyy' ));
        }

        if (params.validTo == "" || params.validTo && params.validFrom && params.validTo.before(params.validFrom)) {
            flash.error = message(code: 'accessMethod.dateValidationError', args: [message(code: 'accessMethod.label'), accessMethod.accessMethod])
            redirect controller: 'accessMethod', action: 'edit', id: accessMethod.id
        } else {
            accessMethod.validTo = params.validTo
            accessMethod.lastUpdated = new Date()

            accessMethod.save(flush: true)
            accessMethod.errors.toString()

            flash.message = message(code: 'accessMethod.create.message', args: [accessMethod.accessMethod.getI10n('value')])
            flash.message = message(code: 'accessMethod.updated', args: [accessMethod.accessMethod.getI10n('value')])
            redirect controller: 'platform', action: 'accessMethods', id: platf.id
        }
    }
}
