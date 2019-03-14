package de.laser

import com.k_int.kbplus.Address
import com.k_int.kbplus.Combo
import com.k_int.kbplus.Contact
import com.k_int.kbplus.Doc
import com.k_int.kbplus.License
import com.k_int.kbplus.Org
import com.k_int.kbplus.OrgRole
import com.k_int.kbplus.Package
import com.k_int.kbplus.Person
import com.k_int.kbplus.Platform
import com.k_int.kbplus.Subscription
import com.k_int.kbplus.TitleInstance
import com.k_int.kbplus.TitleInstancePackagePlatform
import com.k_int.kbplus.auth.Role
import com.k_int.kbplus.auth.User
import com.k_int.kbplus.auth.UserOrg
import grails.plugin.springsecurity.SpringSecurityUtils

class AccessService {

    static final CHECK_VIEW = 'CHECK_VIEW'
    static final CHECK_EDIT = 'CHECK_EDIT'
    static final CHECK_VIEW_AND_EDIT = 'CHECK_VIEW_AND_EDIT'

    def grailsApplication
    def springSecurityService
    def contextService

    // copied from FinanceController, LicenseCompareController, MyInstitutionsController
    boolean checkUserIsMember(User user, Org org) {

        // def uo = UserOrg.findByUserAndOrg(user,org)
        def uoq = UserOrg.where {
            (user == user && org == org && (status == UserOrg.STATUS_APPROVED || status == UserOrg.STATUS_AUTO_APPROVED))
        }

        return (uoq.count() > 0)
    }

    boolean checkMinUserOrgRole(User user, Org org, def role) {

        def result = false
        def rolesToCheck = []

        if (! user || ! org) {
            return result
        }
        if (role instanceof String) {
            role = Role.findByAuthority(role)
        }
        rolesToCheck << role

        // NEW CONSTRAINT:
        if (org.id != contextService.getOrg()?.id) {
            return result
        }

        // sym. role hierarchy
        if (role.authority == "INST_USER") {
            rolesToCheck << Role.findByAuthority("INST_EDITOR")
            rolesToCheck << Role.findByAuthority("INST_ADM")
        }
        else if (role.authority == "INST_EDITOR") {
            rolesToCheck << Role.findByAuthority("INST_ADM")
        }

        rolesToCheck.each{ rot ->
            def userOrg = UserOrg.findByUserAndOrgAndFormalRole(user, org, rot)
            if (userOrg && (userOrg.status == UserOrg.STATUS_APPROVED || userOrg.status == UserOrg.STATUS_AUTO_APPROVED)) {
                result = true
            }
        }
        result
    }

    boolean checkIsEditableForAdmin(User toEdit, User editor, Org org) {

        boolean roleAdmin = editor.hasRole('ROLE_ADMIN')
        boolean instAdmin = editor.hasAffiliation('INST_ADM') // check @ contextService.getOrg()
        boolean orgMatch  = checkUserIsMember(toEdit, contextService.getOrg())

        roleAdmin || (instAdmin && orgMatch)
    }
}
