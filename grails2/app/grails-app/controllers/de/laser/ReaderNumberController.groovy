package de.laser


import de.laser.controller.AbstractDebugController
import de.laser.helper.DateUtil
import de.laser.helper.DebugAnnotation
import grails.plugin.springsecurity.annotation.Secured

import java.text.SimpleDateFormat

@Secured(['IS_AUTHENTICATED_FULLY'])
class ReaderNumberController extends AbstractDebugController {

	def springSecurityService
	def contextService

	@DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
	@Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def create() {
		SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
		if (params.dueDate)
			params.dueDate = sdf.parse(params.dueDate)

		params.org = Org.get(params.orgid)
		params.referenceGroup = params.referenceGroup.isLong() ? RefdataValue.findById(Long.parseLong(params.referenceGroup)).getI10n('value') : params.referenceGroup
		ReaderNumber numbersInstance = new ReaderNumber(params)
		if (! numbersInstance.save(flush:true)) {
			flash.error = message(code: 'default.not.created.message', args: [message(code: 'readerNumber.number.label')])
			log.error(numbersInstance.errors.toString())
		}
		redirect controller: 'organisation', action: 'readerNumber', params: [id:params.orgid,tableA:params.tableA,tableB:params.tableB,sort:params.sort,order:params.order]
    }

	@DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
	@Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def edit() {
		ReaderNumber numbersInstance = ReaderNumber.get(params.id)
		if (! numbersInstance) {
			flash.message = message(code: 'default.not.found.message', args: [message(code: 'readerNumber.label'), params.id])
		}
		SimpleDateFormat sdf = DateUtil.getSDF_NoTime()
		params.referenceGroup = params.referenceGroup.isLong() ? RefdataValue.findById(Long.parseLong(params.referenceGroup)).getI10n('value') : params.referenceGroup
		if(params.dueDate)
			params.dueDate = sdf.parse(params.dueDate)
		numbersInstance.properties = params
		if (! numbersInstance.save(flush:true)) {
			flash.error = message(code:'default.not.updated.message', args: [message(code: 'readerNumber.label'), numbersInstance.id])
			log.error(numbersInstance.errors.toString())
		}
		redirect controller: 'organisation', action: 'readerNumber', params: [id:params.orgid,tableA:params.tableA,tableB:params.tableB,sort:params.sort,order:params.order]
    }

	@DebugAnnotation(test='hasAffiliation("INST_EDITOR")')
	@Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def delete() {
        List<Long> numbersToDelete = []
		Org org = Org.get(params.org)
		if(params.dueDate) {
			Date dueDate = DateUtil.parseDateGeneric(params.dueDate)
			numbersToDelete.addAll(ReaderNumber.findAllByDueDateAndOrg(dueDate,org).collect{ ReaderNumber rn -> rn.id })
		}
		else if(params.semester) {
			RefdataValue semester = RefdataValue.get(params.semester)
			numbersToDelete.addAll(ReaderNumber.findAllBySemesterAndOrg(semester,org).collect{ ReaderNumber rn -> rn.id })
		}
		else if(params.referenceGroup) {
			numbersToDelete.addAll(ReaderNumber.findAllByReferenceGroupAndOrg(params.referenceGroup,org).collect{ ReaderNumber rn -> rn.id })
		}
		if (numbersToDelete) {
			RefdataValue.executeUpdate('delete from ReaderNumber rn where rn.id in (:ids)',[ids:numbersToDelete])
		}
		redirect(url: request.getHeader('referer'))
    }
}
