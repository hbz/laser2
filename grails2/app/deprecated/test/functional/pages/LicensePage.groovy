package pages

import org.openqa.selenium.Keys

/**
 * Created by ioannis on 29/05/2014.
 * For downloading document: https://blog.codecentric.de/en/2010/07/file-downloads-with-selenium-mission-impossible/
 */
class LicensePage extends AbstractDetails {
    static at = {
        browser.page.title.startsWith("KB+ Current Licenses") || browser.page.title.startsWith("KB+")
    }
    static content = {
        createNewLicense { ref ->
            $("a", text: "Add Blank License").click()
            editRef(ref)
        }

        openLicense { ref ->
            $("a", text: ref).click()
        }
        downloadDoc {
            $("a", text: "Download Doc").click()
        }

        deleteLicense { ref ->
            withConfirm { 
                $("a", text: ref).parent().siblings().find("a",text:"Delete").click()
            }
        }

        license {
            $("a", text: "License Details").click()
        }
        viewTemplateLicenses {
            $("a", text: "Copy from Template").click()
        }
        createCopyOf { ref ->
            $("a", text: ref).parent().siblings().find("a",text:"Copy").click()
        }
        alertBox { text ->
            !$("div.alert-block").children().filter("p", text: text).isEmpty()
        }
       
        acceptAll {
            $("a", text: "Accept All").click(LicensePage)
        }
        rejectOne {
            $("a", text: "Reject").click(LicensePage)
        }
        verifyInformation { column, value ->
            $("label", for: column).parent().next().text().equals(value)

        }

        searchLicense { date, ref ->
            $("input",name:"validOn").value( date)
            $("input",name:"keyword-search").value(ref)
            $("input",type:"submit",value:"Search").click()
        }

        importONIX { fileName ->
            $("a", text: "Import an ONIX-PL license").click()
            waitFor{$("input", type: "file", name: "import_file")}
            $("input", type: "file", name: "import_file").value(fileName)
            $("button", text: "Import license").click()
            def createNew = $("#replace_opl")
            if (!createNew.isEmpty()) {
                createNew.click()
            }
            !$("h2", text: "Upload successful").isEmpty()
            $("a.btn-info").click()
        }
        addCustomPropType{ name ->
            $("#select2-chosen-2").click()
            waitFor{$("#s2id_autogen2_search").value(name)} 
            Thread.sleep(400)
            waitFor{$("span.select2-match",text:name)}
            $("span.select2-match",text:name).tail().click()
            $("input", value:"Add Property").click()
        }
        setRefPropertyValue{ prop, value ->
            // waitElement{$("span",'data-pk':"com.k_int.kbplus.LicenseCustomProperty:13").click()}
            waitElement { $("td",text:prop) }
            $("td",text:prop).next("td").find("span").find("span").click()
            waitElement { $("form.editableform") }
            waitFor {$("select.input-medium")}
            $("select.input-medium").value(value)
            $("button.editable-submit").click()
        }
        deleteCustomProp { prop ->
            waitElement { 
                withConfirm { 
                    $("td",text:prop).nextAll().find("a").click()
                }
            }
        }

        addCustomInputProperty { propName, prop ->
            waitFor { $("td",text:propName)}
            $("td",text:propName).next("td").find("span").click()
            Thread.sleep(200)
            waitFor{$("input.input-medium")}
            $("input.input-medium").value(prop)
            $("button.editable-submit").click()
        }

        propertyChangedCheck { propName ->
            $("td",text:propName).next("td").find("span").text()
        }

        rowResults { $("table",0).find("tbody tr").size() }

        exportLicense { export_name ->
            $("a#export-menu").click()
            $("a", text: export_name).click()
        }
    }
}
