package com.k_int.kbplus

import com.k_int.kbplus.auth.Role

/**
 * An OnixplUsageTerm belongs to an OnixplLicense and can have many OnixplLicenseTexts.
 */
class OnixplUsageTerm {

  OnixplLicense oplLicense
  RefdataValue usageType
  RefdataValue usageStatus

  //static hasMany = [ licenseText:OnixplLicenseText ]
  static hasMany = [ usageTermLicenseText:OnixplUsageTermLicenseText, usedResource:RefdataValue, user:RefdataValue ]

  static belongsTo = [
    oplLicense:OnixplLicense
  ]
  //static hasOne = [onixplLicense:OnixplLicense]

  static mappedBy = [
    usageTermLicenseText: 'usageTerm'
  ]

  static mapping = {
    id column:              'oput_id'
    version column:         'oput_version'
    oplLicense column:      'oput_opl_fk', index:'oput_entry_idx'
    usageType column:       'oput_usage_type_rv_fk', index:'oput_entry_idx'
    usageStatus column:     'oput_usage_status_rv_fk', index:'oput_entry_idx'
  }

  static constraints = {
    oplLicense(nullable:true, blank: true)
    usageType(nullable:false, blank: false)
    usageStatus(nullable:false, blank: false)
  }


  /*static constraints = {
    licenseText unique: true
  }*/

    def hasPerm(perm, user) {
        def result = false

        if (perm == 'view' && oplLicense.license.isPublic?.value == 'Yes') {
            result = true;
        }

        if (!result) {
            // If user is a member of admin role, they can do anything.
            def admin_role = Role.findByAuthority('ROLE_ADMIN');
            if (admin_role) {
                if (user.getAuthorities().contains(admin_role)) {
                    result = true;
                }
            }
        }

        if (!result) {
            result = checkPermissions(perm, user);
        }

        result;
    }

    def checkPermissions(perm, user) {
        def result = false
        def principles = user.listPrincipalsGrantingPermission(perm);   // This will list all the orgs and people granted the given perm
        log.debug("The target list if principles : ${principles}");

        // Now we need to see if we can find a path from this object to any of those resources... Any of these orgs can edit

        // If this is a concrete license, the owner is the
        // If it's a template, the owner is the consortia that negotiated
        // def owning org list
        // We're looking for all org links that grant a role with the corresponding edit property.
        Set object_orgs = new HashSet();
        oplLicense.license.orgLinks.each { ol ->
            def perm_exists = false
            if (!ol.roleType)
                log.warn("Org link with no role type! Org Link ID is ${ol.id}");

            ol.roleType?.sharedPermissions.each { sp ->
                if (sp.perm.code == perm)
                    perm_exists = true;
            }
            if (perm_exists) {
                log.debug("Looks like org ${ol.org} has perm ${perm} shared with it.. so add to list")
                object_orgs.add("${ol.org.id}:${perm}")
            }
        }

        log.debug("After analysis, the following relevant org_permissions were located ${object_orgs}, user has the following orgs for that perm ${principles}")

        // Now find the intersection
        def intersection = principles.retainAll(object_orgs)

        log.debug("intersection is ${principles}")

        if (principles.size() > 0)
            result = true

        result
    }

  @Override
  public java.lang.String toString() {
    return "OnixplUsageTerm{" +
        "id=" + id +
        ", oplLicense=" + oplLicense +
        ", usageType=" + usageType +
        ", usageStatus=" + usageStatus +
        ", usedResource=" + usedResource +
        ", user=" + user +
        '}';
  }

    /**
     * Display method that takes all of the license text associated with a usage term and formats it for display. Not
     * all license texts have a display number and so, in their absence, the elementId is used.
     * @param oput
     * @return
     */
    public String getLicenseText() {
        StringBuilder sb = new StringBuilder();
        for (OnixplUsageTermLicenseText utlt : this.usageTermLicenseText.sort { it.licenseText.text }) {
            if (utlt.licenseText.displayNum) {
                sb.append(utlt.licenseText.displayNum + " ");
            } else {
                sb.append(utlt.licenseText.elementId + " ");
            }
            sb.append(utlt.licenseText.text);
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Given a usage term a comparison will be made. Usage type, usage status and license text content are all taken
     * into account.
     * @param oput
     * @return
     */
    public Boolean compare(OnixplUsageTerm oput) {
        if (oput == null) {
            return false;
        }
        StringBuilder text1 = new StringBuilder();
        StringBuilder text2 = new StringBuilder();
        for (OnixplUsageTermLicenseText utlt : this.usageTermLicenseText.sort {it.licenseText.text}) {
            text1.append(utlt.licenseText.text);
        }
        for (OnixplUsageTermLicenseText utlt : oput.usageTermLicenseText.sort {it.licenseText.text}) {
            text2.append(utlt.licenseText.text);
        }
        return (this.usageType.id == oput.usageType.id && this.usageStatus.id == oput.usageStatus.id && text1.toString() == text2.toString());
    }
}
