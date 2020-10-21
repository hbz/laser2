package com.k_int.kbplus.auth

import de.laser.Org
import org.apache.commons.lang.builder.HashCodeBuilder
import javax.persistence.Transient

class UserOrg implements Comparable {

    static STATUS_PENDING       = 0
    static STATUS_APPROVED      = 1
    static STATUS_REJECTED      = 2
    // static STATUS_AUTO_APPROVED = 3
    // static STATUS_CANCELLED     = 4

    int status  // 0=Pending, 1=Approved, 2=Rejected

    Long dateRequested
    Long dateActioned

    Org org
    User user
    Role formalRole

    Date dateCreated
    Date lastUpdated

    static transients = ['sortString'] // mark read-only accessor methods

    static mapping = {
        cache true
        lastUpdated     column: 'uo_last_updated'
        dateCreated     column: 'uo_date_created'
    }

    static constraints = {
        dateActioned    nullable: true
        dateRequested   nullable: true
        formalRole      nullable: true
        lastUpdated     nullable: true
        dateCreated     nullable: true
    }

    @Transient
    String getSortString() {
        return org?.name + ' ' + formalRole?.authority
    }

    @Transient
    int compareTo(obj) {
        sortString.compareTo(obj?.sortString)
    }

    int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder()

        if (user) {
            builder.append(user.id)
        }
        if (org) {
            builder.append(org.id)
        }
        if (formalRole) {
            builder.append(formalRole.id)
        }

        builder.toHashCode()
    }
}

