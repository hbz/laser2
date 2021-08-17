package de.laser

import de.laser.helper.DateUtils
import de.laser.helper.WorkflowHelper
import de.laser.workflow.WfCondition
import de.laser.workflow.WfTask

class LaserWorkflowTagLib {

    static namespace = "laser"

    def workflowTask = { attrs, body ->

        WfTask task = attrs.task as WfTask

        String tooltip = '<p><strong>' + task.title + '</strong></p>' + ( task.description ? '<p>' + task.description + '</p>' : '')

        if (task.condition) {
            WfCondition cnd = task.condition
            tooltip = tooltip + '<div class="ui divider"></div>'

            if (cnd.type != 0) {
                List<String> fields = []
                cnd.getFields().each { f ->
                    String fstr = (cnd.getProperty( f + '_title') ?: 'Feld ohne Titel')

                    if (f.startsWith('checkbox')) {
                        fstr = ( cnd.getProperty( f ) == true ? '<i class="ui icon check square outline"></i> ' : '<i class="ui icon square outline la-light-grey"></i> ' ) + fstr
                    }
                    else if (f.startsWith('date')) {
                        fstr = ( cnd.getProperty( f ) ?
                                '<i class="icon calendar alternate outline"></i> ' + fstr + ': <strong>' + DateUtils.getSDF_NoTime().format(cnd.getProperty(f)) + '</strong>' :
                                '<i class="icon calendar alternate outline la-light-grey"></i> ' + fstr  )
                    }
                    else if (f.startsWith('file')) {
                        DocContext docctx = cnd.getProperty( f ) as DocContext
                        String docStr = message(code:'template.documents.missing')

                        if (docctx) {
                            if (docctx.owner?.title) {
                                docStr = docctx.owner.title
                            }
                            else if (docctx.owner?.filename) {
                                docStr = docctx.owner.filename
                            }
                            fstr = '<i class="icon file"></i> ' + fstr + ': <strong>' + docStr + '</strong>'
                        }
                        else {
                            fstr = '<i class="icon file la-light-grey"></i> ' + fstr
                        }
                    }
                    fields.add( fstr )
                }
                tooltip = tooltip + '<p>' + fields.join('<br/>') + '</p>'
            }
            else {
                tooltip = tooltip + '<p><em>' + task.condition.getTypeAsRefdataValue().getI10n('value') + '</em></p>'
            }
        }

        String cssColor = WorkflowHelper.getCssColorByStatus(task.status)
        String cssIcon = WorkflowHelper.getCssIconByTaskPriority( task.priority )

        out << '<span class="la-popup-tooltip la-delay" data-position="top center" data-html="' + tooltip.encodeAsHTML() + '">'
        out <<   '<a href="' + g.createLink( controller:'ajaxHtml', action:'useWfXModal', params:attrs.params ) + '" class="ui circular compact button wfModalLink ' + cssColor + '">'
        out <<     '<i class="ui icon ' + cssIcon + '" style="margin-left:0;"></i>'
        out <<   '</a>'
        out << '</span>'
    }

    def workflowTaskConditionField = { attrs, body ->

        String field = attrs.field
        WfCondition condition = attrs.condition as WfCondition

        if (field && condition) {

            if (field.startsWith('checkbox')) {
                if (condition.getProperty(field) == true) {
                    out << '<i class="icon check square outline"></i> '
                }
                else {
                    out << '<i class="icon square outline la-light-grey"></i> '
                }
                out << condition.getProperty(field + '_title') ?: 'Feld ohne Titel'
            }
            else if (field.startsWith('date')) {
                if (condition.getProperty(field)) {
                    out << '<i class="icon calendar alternate outline"></i> '
                    out << condition.getProperty(field + '_title') ?: 'Feld ohne Titel'
                    out << ': ' + DateUtils.getSDF_NoTime().format(condition.getProperty(field))
                }
                else {
                    out << '<i class="icon calendar alternate outline la-light-grey"></i> '
                    out << condition.getProperty(field + '_title') ?: 'Feld ohne Titel'
                    out << ': -'
                }
            }
            else if (field.startsWith('file')) {
                DocContext docctx = condition.getProperty(field) as DocContext

                if (docctx) {
                    String linkBody = message(code:'template.documents.missing')
                    if (docctx.owner?.title) {
                        linkBody = docctx.owner.title
                    }
                    else if (docctx.owner?.filename) {
                        linkBody = docctx.owner.filename
                    }
                    linkBody = '<i class="icon file"></i>' + linkBody + ' (' + docctx.owner?.type?.getI10n('value') + ')'

                    out << g.link( [controller: 'docstore', id: docctx.owner.uuid], linkBody)
                }
                else {
                    out << '<i class="icon file la-light-grey"></i>'
                }
            }
        }
        else {
            out << '[laser:conditionField]'
        }
    }
}