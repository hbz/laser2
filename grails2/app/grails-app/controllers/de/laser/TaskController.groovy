package de.laser


import com.k_int.kbplus.auth.User
import de.laser.controller.AbstractDebugController
import de.laser.helper.DateUtil
import de.laser.helper.DebugAnnotation
import grails.plugin.springsecurity.annotation.Secured
import org.springframework.dao.DataIntegrityViolationException

import java.text.SimpleDateFormat

@Secured(['IS_AUTHENTICATED_FULLY'])
class TaskController extends AbstractDebugController {

	def springSecurityService
    def contextService
    def taskService

    static allowedMethods = [create: ['GET', 'POST'], edit: ['GET', 'POST'], delete: 'POST']

    @Secured(['ROLE_ADMIN'])
    def index() {
        redirect action: 'list', params: params
    }

	@Secured(['ROLE_ADMIN'])
    def list() {
		if (! params.max) {
			User user   = springSecurityService.getCurrentUser()
			params.max  = user?.getDefaultPageSize()
		}
        [taskInstanceList: Task.list(params), taskInstanceTotal: Task.count()]
    }

	@DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
	@Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def create() {
        def contextOrg  = contextService.getOrg()
//		def result      = taskService.getPreconditions(contextOrg)

		SimpleDateFormat sdf = DateUtil.getSDF_NoTime()

		if (params.endDate)
			params.endDate = sdf.parse(params.endDate)

		switch (request.method) {
			/*case 'GET':
				result.taskInstance = new Task(params)
				result
				break*/
			case 'POST':
				def taskInstance = new Task(title: params.title, description: params.description, status: params.status.id, systemCreateDate: new Date(), endDate: params.endDate)
				taskInstance.creator = contextService.getUser()
				taskInstance.createDate = new Date()

				//Bearbeiter festlegen
				if (params.responsible == "Org") {
					taskInstance.responsibleOrg = contextOrg
				}
				else if (params.responsible == "User") {
					taskInstance.responsibleUser = (params.responsibleUser.id != 'null') ? User.get(params.responsibleUser.id): contextService.getUser()
				}

				if (params.linkto == "license" && params.license && params.license != 'null') {
					taskInstance.license = License.get(params.license) ?: null
				}
				else if (params.linkto == "pkg" && params.pkg && params.pkg != 'null') {
					taskInstance.pkg = Package.get(params.pkg) ?: null
				}
				else if (params.linkto == "subscription" && params.subscription && params.subscription != 'null') {
					taskInstance.subscription = Subscription.get(params.subscription) ?: null
				}
				else if (params.linkto == "org" && params.org && params.org != 'null') {
					taskInstance.org = Org.get(params.org) ?: null
				}
				else if (params.linkto == "surveyConfig" && params.surveyConfig && params.surveyConfig != 'null') {
					taskInstance.surveyConfig = SurveyConfig.get(params.surveyConfig) ?: null
				}

				if (!taskInstance.save(flush: true)) {
					/*result.taskInstance = taskInstance
					render view: 'create', model: result*/
					flash.error = message(code: 'default.not.created.message', args: [message(code: 'task.label')])
					redirect(url: request.getHeader('referer'))
					return
				}

				flash.message = message(code: 'default.created.message', args: [message(code: 'task.label'), taskInstance.title])

				redirect(url: request.getHeader('referer'))
				break
		}
    }
	@DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
	@Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def _modal_create() {
        def contextOrg  = contextService.getOrg()
		def result      = taskService.getPreconditions(contextOrg)
		result.validSubscriptionsList = new ArrayList()
		result.validSubscriptions.each{
			result.validSubscriptionsList.add([it.id, it.dropdownNamingConvention(contextService.org)])
		}
		render template: "/templates/tasks/modal_create", model: result
    }

    @Secured(['ROLE_ADMIN'])
    def show() {
        def taskInstance = Task.get(params.id)
        if (! taskInstance) {
			flash.message = message(code: 'default.not.found.message', args: [message(code: 'task.label'), params.id])
            //redirect action: 'list'
			redirect controller: 'myInstitution', action: 'dashboard'
            return
        }

        [taskInstance: taskInstance]
    }

	@DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
	@Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def edit() {
		Org contextOrg = contextService.getOrg()
        def result     = taskService.getPreconditionsWithoutTargets(contextOrg)

		SimpleDateFormat sdf = DateUtil.getSDF_NoTime()

		if (params.endDate)
			params.endDate = sdf.parse(params.endDate)

		switch (request.method) {
		/*case 'GET':
            result.taskInstance = Task.get(params.id)
	        if (! result.taskInstance) {
	            flash.message = message(code: 'default.not.found.message', args: [message(code: 'task.label'), params.id])
	            //redirect action: 'list'
				redirect controller: 'myInstitution', action: 'dashboard'
	            return
	        }

            result
			break*/
		case 'POST':
	        def taskInstance = Task.get(params.id)

			if(((!taskInstance.responsibleOrg) && taskInstance.responsibleUser != contextService.getUser()) && (taskInstance.responsibleOrg != contextOrg) && (taskInstance.creator != contextService.getUser()))
			{
				flash.error = message(code: 'task.edit.norights', args: [taskInstance.title])
				redirect(url: request.getHeader('referer'))
				return
			}

	        if (! taskInstance) {
	            flash.message = message(code: 'default.not.found.message', args: [message(code: 'task.label'), params.id])
	            //redirect action: 'list'
				redirect controller: 'myInstitution', action: 'dashboard'
	            return
	        }

	        if (params.version) {
	            Long version = params.long('version')
	            if (taskInstance.version > version) {
	                taskInstance.errors.rejectValue('version', 'default.optimistic.locking.failure',
	                          [message(code: 'task.label')] as Object[],
	                          "Another user has updated this Task while you were editing")

                    result.taskInstance = taskInstance
	                //render view: 'edit', model: result
					redirect(url: request.getHeader('referer'))
	                return
	            }
	        }

	        taskInstance.properties = params

			//Bearbeiter festlegen/ändern
			if (params.responsible == "Org") {
				taskInstance.responsibleOrg = contextOrg
				taskInstance.responsibleUser = null
			}
			else if (params.responsible == "User") {
				taskInstance.responsibleUser = (params.responsibleUser.id != 'null') ? User.get(params.responsibleUser.id): contextService.getUser()
				taskInstance.responsibleOrg = null
			}

	        if (! taskInstance.save(flush: true)) {
                result.taskInstance = taskInstance
	            /*render view: 'edit', model: result*/
				flash.error = message(code: 'default.not.updated.message', args: [message(code: 'task.label'), taskInstance.title])
				redirect(url: request.getHeader('referer'))
	            return
	        }

			flash.message = message(code: 'default.updated.message', args: [message(code: 'task.label'), taskInstance.title])
			redirect(url: request.getHeader('referer'))
			break
		}
    }

	@Secured(['permitAll']) // TODO
	def ajaxEdit() {
		Org contextOrg = contextService.getOrg()
		def result     = taskService.getPreconditionsWithoutTargets(contextOrg)
		result.params = params
		result.taskInstance = Task.get(params.id)

		render template: "/templates/tasks/modal_edit", model: result
	}

	@DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
	@Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def delete() {
        def taskInstance = Task.get(params.id)
		def tasktitel = taskInstance.title
        if (! taskInstance) {
			flash.message = message(code: 'default.not.found.message', args: [message(code: 'task.label'), params.id])
            //redirect action: 'list'
			redirect(url: request.getHeader('referer'))
			return
        }

		if(taskInstance.creator != contextService.getUser())
		{
			flash.error = message(code: 'task.delete.norights', args: [tasktitel])
			redirect(url: request.getHeader('referer'))
			return
		}

        try {

            taskInstance.delete(flush: true)
			flash.message = message(code: 'default.deleted.message', args: [message(code: 'task.label'), tasktitel])
            //redirect action: 'list'
			redirect(url: request.getHeader('referer'))
        }
        catch (DataIntegrityViolationException e) {
			flash.error = message(code: 'default.not.deleted.message', args: [message(code: 'task.label'),  tasktitel])
            //redirect action: 'show', id: params.id
			redirect(url: request.getHeader('referer'))
        }
    }
}
