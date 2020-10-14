/**
 * Created by ioannis on 29/05/2014.
 */
class Data {
    static sep = File.separator
    static workingDir = "${System.getProperty('user.dir')}" + sep + ".." + sep

    static UserA_name = "usera"
    static UserA_passwd = "usera"
    static UserA_displayName = "UserA"

    static UserB_name = "userb"
    static UserB_passwd = "userb"

    static UserC_name = "userc"
    static UserC_passwd = "userc"

    static UserD_name = "admin"
    static UserD_passwd = "admin"
    static UserD_displayName = "TestAdmin"
    
    static Org_name = "Functional Test Organisation"
    static Org_impId = "FunctionalTestOrganisation"
    static Org_Url = "Functional_Test_Organisation"

    static License_template_D = "Functional Test Temp License"
    static License_template_copy_D = "Functional Test Temp License edited copy"
    static License_actual_C = "Functional Test License C"

    static License_ONIX_PL_title = "Nature Publishing Group Academic Site License Agreement US"
    static License_ONIX_PL =
            workingDir + "manual_test_data" + sep + "onix" + sep + "TX-DRAFT Nature Publishing Group Academic License Template-ople-t-1375739573670.xml"
    static Test_Doc_name = "Functional Test Doc"
    static Test_Doc_file =
            workingDir + "manual_test_data" + sep + "so_v3" + sep + "os_test_1"
    static Package_name = "Art Journals:Master:2014"
    static Subscription_name_A = "FT Subscription A"
    static Subscription_name_B = "FT Subscription B"
    static Subscription_name_C = "FT Subscription C"

    static Test_Announcement = "FT Test Announcement"

    static Content_Item_welcome_key = "kbplus.welcome.text"
    static Content_Item_welcome_text = "Welcome to KBPlus fucntional tests"

    static Package_import_file = workingDir + "manual_test_data" + sep + "so_v3" + sep + "Art Journals_Master_2014.csv"
    static issnl_mapping_file = workingDir + "manual_test_data" + sep + "issnl" + sep + "small_issnl_data.tsv"

    static RenewalsUploadFile = workingDir + "manual_test_data"+sep+"renewalsUpload" +sep+"EmeraldNewRenewalWorksheet.xls"
    static JasperReportExistingFile = workingDir + "app" + sep + "grails-app"+sep+"conf"+sep+"resources"+sep+"jasper_reports"+sep+"title_no_url.jrxml"
    static JasperReportNewFile = workingDir + "manual_test_data" +sep +"jasper"+sep+"titles.jrxml"

    static License_public_journals     = "public"
    static License_publicProp_journals = "Public Journal Access"

    static Title_identifier        = "eISSN:1234"
    static Title_titlename         = "testing 123"
    static Title_uniqID            = java.util.UUID.randomUUID().toString()
    static Tipp_uniqID             = java.util.UUID.randomUUID().toString()
}
