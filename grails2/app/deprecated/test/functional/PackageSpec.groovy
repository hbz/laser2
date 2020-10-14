import de.laser.RefdataValue
import geb.spock.GebReportingSpec
import org.elasticsearch.common.joda.time.LocalDate
import pages.*
import spock.lang.Stepwise
import com.k_int.kbplus.*
import groovy.time.TimeCategory

@Stepwise
class PackageSpec extends GebReportingSpec {
    //The following will setup everything required for this test case
    def setupSpec(){
        def org = new com.k_int.kbplus.Org(name:Data.Org_name,impId:Data.Org_impId,sector:RefdataValue.getByValue('Higher Education')).save()
        def user = com.k_int.kbplus.auth.User.findByUsername(Data.UserA_name)
        def userAdm = com.k_int.kbplus.auth.User.findByUsername(Data.UserD_name)
        def formal_role = com.k_int.kbplus.auth.Role.findByAuthority('INST_ADM')
        def userOrg = new com.k_int.kbplus.auth.UserOrg(dateRequested:System.currentTimeMillis(),
                status:com.k_int.kbplus.auth.UserOrg.STATUS_APPROVED,
                org:org,
                user:user,
                formalRole:formal_role).save()
        def userOrgAdmin = new com.k_int.kbplus.auth.UserOrg(dateRequested:System.currentTimeMillis(),
                status:com.k_int.kbplus.auth.UserOrg.STATUS_APPROVED,
                org:org,
                user:userAdm,
                formalRole:formal_role).save()

    }

    def "Upload Package"(){
        setup:
        to PublicPage
        loginLink()
        at LogInPage
        login(Data.UserD_name, Data.UserD_passwd)
        when:
        go '/laser/upload/reviewPackage'
        $('form').soFile = Data.Package_import_file
        $('button', text: "Upload SO").click()
        then:
        waitFor{$("div.alert-success")}
    }

    def "View Package"(){
        setup:
            def pkg_id = Package.findByName(Data.Package_name).id
        when:
            go "package/show/${pkg_id}"
        then:
            $("h1",text:Data.Package_name).verifyNotEmpty()
    }

    def "Test As At "(){
        setup:
            at PackageDetailsPage
            def pkg_id = Package.findByName(Data.Package_name).id
            editDate("endDate","2015-01-01","#comk_intkbplusPackage_1_endDate")
        when:
            go "package/show/${pkg_id}"
        then:
            //The snapshot effect is partially from JS so we should wait a bit.
            Thread.sleep(500)
            $("h1",text:"Snapshot on 2015-01-01 from").verifyNotEmpty()

        when:
            go "package/show/${pkg_id}?mode=advanced"
        then:
            $("h1",text:"Snapshot on 2015-01-01 from ").isEmpty()
        when:
            editDate("endDate","2115-01-01","#comk_intkbplusPackage_1_endDate")
            go "package/show/${pkg_id}"
        then:
            $("h1",text:"Snapshot on 2015-01-01 from ").isEmpty()

    }
}