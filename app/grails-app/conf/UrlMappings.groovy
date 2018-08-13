class UrlMappings {

    static mappings = {
        "/$controller/$action?/$id?" {
            constraints {
                // apply constraints here
            }
        }

        "/lic/$action?/$id?"        (controller: 'license')

        "/myInstitution/tipview/$id"    (controller: 'myInstitution', action: 'tip')
        "/myInstitution/finance"        (controller: 'finance', action: 'index')
        name subfinance:        "/subscriptionDetails/$sub/finance/"   (controller: 'finance', action: 'index')
        name subfinanceEditCI:  "/subscriptionDetails/$sub/editCostItem/$id"   (controller: 'finance', action: 'editCostItem')

        "/ajax/$action?/$id?"(controller: 'ajax')

        // new custom api
        "/api/push/orgs"        (controller: 'api', action: 'importInstitutions', parseRequest: false)

        "/api/v0/spec"          (uri: '/swagger/v0/laser.yaml.gsp')
        "/api/v0/$obj"          (controller: 'api', action: 'v0')

        "/api/v1/spec"          (uri: '/swagger/v1/laser.yaml.gsp')
        "/api/v1/$obj"          (controller: 'api', action: 'v1')

        "/"                     (view: "public/index")
        "/gasco"                (controller: 'public', action: 'gasco')

        "/oai/$id"(controller: 'oai', action: 'index')

        "500"(view: '/serverCodes/error')
        "401"(view: '/serverCodes/forbidden')
        "403"(view: '/serverCodes/error')
        "404"(view: '/serverCodes/notFound404')

    }
}
