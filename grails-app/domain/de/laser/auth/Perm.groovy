package de.laser.auth

class Perm {

    String code

    static mapping = {
        cache   true
    }

    static constraints = {
        code    blank: false, unique: true
    }

    static hasMany = [
            grantedTo: PermGrant
    ]

    static mappedBy = [
            grantedTo: 'perm'
    ]
}
