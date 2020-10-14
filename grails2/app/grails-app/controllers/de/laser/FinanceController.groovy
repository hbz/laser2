package de.laser


import com.k_int.kbplus.auth.User
import de.laser.finance.BudgetCode
import de.laser.finance.CostItem
import de.laser.finance.CostItemElementConfiguration
import de.laser.finance.CostItemGroup
import de.laser.finance.Invoice
import de.laser.controller.AbstractDebugController
import de.laser.exceptions.CreationException
import de.laser.exceptions.FinancialDataException
import de.laser.finance.Order
import de.laser.helper.DateUtil
import de.laser.helper.DebugAnnotation
import de.laser.helper.RDConstants
import de.laser.helper.RDStore
import de.laser.interfaces.CalculatedType
import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import org.apache.poi.POIXMLProperties
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.streaming.SXSSFSheet
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.context.i18n.LocaleContextHolder

import javax.servlet.ServletOutputStream
import java.awt.*
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Year
import java.util.List
import java.util.regex.Matcher
import java.util.regex.Pattern

@Secured(['IS_AUTHENTICATED_FULLY'])
class FinanceController extends AbstractDebugController {

    def springSecurityService
    def accessService
    def contextService
    def genericOIDService
    def financeService
    def escapeService
    def exportService

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def index() {
        log.debug("FinanceController::index() ${params}")
        try {
            Map<String,Object> result = financeService.setResultGenerics(params)
            result.financialData = financeService.getCostItems(params,result)
            result.filterPresets = result.financialData.filterPresets
            result.filterSet = result.financialData.filterSet
            result.allCIElements = CostItemElementConfiguration.executeQuery('select ciec.costItemElement from CostItemElementConfiguration ciec where ciec.forOrganisation = :org',[org:result.institution])
            result
        }
        catch(FinancialDataException e) {
            flash.error = e.getMessage()
            redirect controller: "home"
        }
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def subFinancialData() {
        log.debug("FinanceController::subFinancialData() ${params}")
        try {
            Map<String,Object> result = financeService.setResultGenerics(params)
            result.financialData = financeService.getCostItemsForSubscription(params,result)
            result.filterPresets = result.financialData.filterPresets
            result.filterSet = result.financialData.filterSet
            result.allCIElements = CostItemElementConfiguration.executeQuery('select ciec.costItemElement from CostItemElementConfiguration ciec where ciec.forOrganisation = :org',[org:result.institution])
            result
        }
        catch (FinancialDataException e) {
            flash.error = e.getMessage()
            redirect controller: 'myInstitution', action: 'currentSubscriptions'
        }
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def financialsExport()  {
        log.debug("Financial Export :: ${params}")
        Map<String, Object> result = financeService.setResultGenerics(params+[forExport:true])
        if (!accessService.checkMinUserOrgRole(result.user,result.institution,"INST_USER")) {
            flash.error=message(code: 'financials.permission.unauthorised', args: [result.institution? result.institution.name : 'N/A'])
            response.sendError(403)
            return
        }
        Map financialData = result.subscription ? financeService.getCostItemsForSubscription(params,result) : financeService.getCostItems(params,result)
        result.cost_item_tabs = [:]
        if(result.dataToDisplay.contains("own")) {
            result.cost_item_tabs["own"] = financialData.own
        }
        if(result.dataToDisplay.contains("cons")) {
            result.cost_item_tabs["cons"] = financialData.cons
        }
        if(result.dataToDisplay.any { d -> ["subscr","consAtSubscr"].contains(d) }) {
            result.cost_item_tabs["subscr"] = financialData.subscr
        }
        SimpleDateFormat sdf = DateUtil.getSDF_NoTimeNoPoint()
        String filename = result.subscription ? escapeService.escapeString(result.subscription.name)+"_financialExport" : escapeService.escapeString(result.institution.name)+"_financialExport"
        if(params.exportXLS) {
            SXSSFWorkbook workbook = processFinancialXLSX(result)
            response.setHeader("Content-disposition", "attachment; filename=\"${sdf.format(new Date(System.currentTimeMillis()))}_${filename}.xlsx\"")
            response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            try {
                workbook.write(response.outputStream)
                response.outputStream.flush()
                response.outputStream.close()
                workbook.dispose()
            }
            catch (IOException e) {
                log.error("A request was started before the started one was terminated")
            }
        }
        else {
            ArrayList titles = []
            String viewMode = params.showView
            int sumcell = -1
            int sumcellAfterTax = -1
            int sumTitleCell = -1
            int sumCurrencyCell = -1
            int sumCurrencyAfterTaxCell = -1
            if(viewMode == "cons")
                titles.addAll([message(code:'org.sortName.label'),message(code:'financials.newCosts.costParticipants'),message(code:'financials.isVisibleForSubscriber')])
            titles.add(message(code: 'financials.newCosts.costTitle'))
            if(viewMode == "cons")
                titles.add(message(code:'default.provider.label'))
            titles.addAll([message(code: 'default.subscription.label'), message(code:'subscription.startDate.label'), message(code: 'subscription.endDate.label'),
                           message(code: 'financials.costItemConfiguration'), message(code: 'package.label'), message(code: 'issueEntitlement.label'),
                           message(code: 'financials.datePaid'), message(code: 'financials.dateFrom'), message(code: 'financials.dateTo'), message(code:'financials.financialYear'),
                           message(code: 'default.status.label'), message(code: 'financials.billingCurrency'), message(code: 'financials.costInBillingCurrency'),"EUR",
                           message(code: 'financials.costInLocalCurrency')])
            if(["own","cons"].indexOf(viewMode) > -1)
                titles.addAll(message(code: 'financials.taxRate'), [message(code:'financials.billingCurrency'),message(code: 'financials.costInBillingCurrencyAfterTax'),"EUR",message(code: 'financials.costInLocalCurrencyAfterTax')])
            titles.addAll([message(code: 'financials.costItemElement'),message(code: 'financials.newCosts.description'),
                           message(code: 'financials.newCosts.constsReferenceOn'), message(code: 'financials.budgetCode'),
                           message(code: 'financials.invoice_number'), message(code: 'financials.order_number')])
            SimpleDateFormat dateFormat = DateUtil.getSDF_NoTime()
            LinkedHashMap<Subscription,List<Org>> subscribers = [:]
            LinkedHashMap<Subscription,Set<Org>> providers = [:]
            LinkedHashMap<Subscription,BudgetCode> costItemGroups = [:]
            OrgRole.findAllByRoleTypeInList([RDStore.OR_SUBSCRIBER_CONS,RDStore.OR_SUBSCRIBER_CONS_HIDDEN]).each { it ->
                List<Org> orgs = subscribers.get(it.sub)
                if(orgs == null)
                    orgs = [it.org]
                else orgs.add(it.org)
                subscribers.put(it.sub,orgs)
            }
            OrgRole.findAllByRoleTypeInList([RDStore.OR_PROVIDER,RDStore.OR_AGENCY]).each { it ->
                Set<Org> orgs = providers.get(it.sub)
                if(orgs == null)
                    orgs = [it.org]
                else orgs.add(it.org)
                providers.put(it.sub,orgs)
            }
            CostItemGroup.findAll().each{ cig -> costItemGroups.put(cig.costItem,cig.budgetCode) }
            withFormat {
                csv {
                    response.setHeader("Content-disposition", "attachment; filename=\"${sdf.format(new Date(System.currentTimeMillis()))}_${filename}_${viewMode}.csv\"")
                    response.contentType = "text/csv"
                    ServletOutputStream out = response.outputStream
                    out.withWriter { writer ->
                        ArrayList rowData = []
                        if(financialData[viewMode].count > 0) {
                            ArrayList row
                            financialData[viewMode].costItems.each { ci ->
                                BudgetCode codes = costItemGroups.get(ci)
                                String start_date   = ci.startDate ? dateFormat.format(ci?.startDate) : ''
                                String end_date     = ci.endDate ? dateFormat.format(ci?.endDate) : ''
                                String paid_date    = ci.datePaid ? dateFormat.format(ci?.datePaid) : ''
                                row = []
                                int cellnum = 0
                                if(viewMode == "cons") {
                                    if(ci.sub) {
                                        List<Org> orgRoles = subscribers.get(ci.sub)
                                        //participants (visible?)
                                        String cellValueA = ""
                                        String cellValueB = ""
                                        orgRoles.each { or ->
                                            cellValueA += or.sortname
                                            cellValueB += or.name
                                        }
                                        cellnum++
                                        row.add(cellValueA)
                                        cellnum++
                                        row.add(cellValueB)
                                        cellnum++
                                        row.add(ci.isVisibleForSubscriber ? message(code:'financials.isVisibleForSubscriber') : " ")
                                    }
                                }
                                //cost title
                                cellnum++
                                row.add(ci.costTitle ?: '')
                                if(viewMode == "cons") {
                                    //provider
                                    cellnum++
                                    if(ci.sub) {
                                        Set<Org> orgRoles = providers.get(ci.sub)
                                        String cellValue = ""
                                        orgRoles.each { or ->
                                            cellValue += or.name
                                        }
                                        row.add(cellValue)
                                    }
                                    else row.add(" ")
                                }
                                //subscription
                                cellnum++
                                row.add(ci.sub ? ci.sub.name : "")
                                //dates from-to
                                if(ci.sub) {
                                    cellnum++
                                    if(ci.sub.startDate)
                                        row.add(dateFormat.format(ci.sub.startDate))
                                    else
                                        row.add("")
                                    cellnum++
                                    if(ci.sub.endDate)
                                        row.add(dateFormat.format(ci.sub.endDate))
                                    else
                                        row.add("")
                                }
                                //cost sign
                                cellnum++
                                if(ci.costItemElementConfiguration) {
                                    row.add(ci.costItemElementConfiguration.getI10n("value"))
                                }
                                else
                                    row.add(message(code:'financials.costItemConfiguration.notSet'))
                                //subscription package
                                cellnum++
                                row.add(ci?.subPkg ? ci.subPkg.pkg.name:'')
                                //issue entitlement
                                cellnum++
                                row.add(ci?.issueEntitlement ? ci.issueEntitlement?.tipp?.title?.title:'')
                                //date paid
                                cellnum++
                                row.add(paid_date ?: '')
                                //date from
                                cellnum++
                                row.add(start_date ?: '')
                                //date to
                                cellnum++
                                row.add(end_date ?: '')
                                //financial year
                                cellnum++
                                row.add(ci?.financialYear ? ci.financialYear.toString() : '')
                                //for the sum title
                                sumTitleCell = cellnum
                                //cost item status
                                cellnum++
                                row.add(ci?.costItemStatus ? ci.costItemStatus.getI10n("value"):'')
                                if(["own","cons"].indexOf(viewMode) > -1) {
                                    sumCurrencyCell = cellnum
                                    cellnum++
                                    //billing currency and value
                                    row.add(ci?.billingCurrency ? ci.billingCurrency.value : '')
                                    cellnum++
                                    row.add(ci?.costInBillingCurrency ? ci.costInBillingCurrency : 0.0)
                                    sumcell = cellnum
                                    //local currency and value
                                    cellnum++
                                    row.add("EUR")
                                    cellnum++
                                    row.add(ci?.costInLocalCurrency ? ci.costInLocalCurrency : 0.0)
                                    sumCurrencyAfterTaxCell = cellnum
                                    //tax rate
                                    cellnum++
                                    String taxString
                                    if(ci.taxKey && ci.taxKey.display) {
                                        taxString = "${ci.taxKey.taxType.getI10n('value')} (${ci.taxKey.taxRate} %)"
                                    }
                                    else if(ci.taxKey == CostItem.TAX_TYPES.TAX_REVERSE_CHARGE) {
                                        taxString = "${ci.taxKey.taxType.getI10n('value')}"
                                    }
                                    else taxString = message(code:'financials.taxRate.notSet')
                                    row.add(taxString)
                                }
                                if(["own","cons"].indexOf(viewMode) < 0)
                                    sumCurrencyAfterTaxCell = cellnum
                                //billing currency and value
                                cellnum++
                                row.add(ci?.billingCurrency ? ci.billingCurrency.value : '')
                                if(["own","cons"].indexOf(viewMode) > -1)
                                    sumcellAfterTax = cellnum
                                cellnum++
                                row.add(ci?.costInBillingCurrencyAfterTax ? ci.costInBillingCurrencyAfterTax : 0.0)
                                if(["own","cons"].indexOf(viewMode) < 0)
                                    sumcellAfterTax = cellnum
                                //local currency and value
                                cellnum++
                                row.add("EUR")
                                cellnum++
                                row.add(ci?.costInLocalCurrencyAfterTax ? ci.costInLocalCurrencyAfterTax : 0.0)
                                //cost item element
                                cellnum++
                                row.add(ci?.costItemElement?ci.costItemElement.getI10n("value") : '')
                                //cost item description
                                cellnum++
                                row.add(ci?.costDescription?: '')
                                //reference
                                cellnum++
                                row.add(ci?.reference?:'')
                                //budget codes
                                cellnum++
                                row.add(codes ? codes.value : '')
                                //invoice number
                                cellnum++
                                row.add(ci?.invoice ? ci.invoice.invoiceNumber : "")
                                //order number
                                cellnum++
                                row.add(ci?.order ? ci.order.orderNumber : "")
                                //rownum++
                                rowData.add(row)
                            }
                            rowData.add([])
                            List sumRow = []
                            int h = 0
                            for(h;h < sumTitleCell;h++) {
                                sumRow.add(" ")
                            }
                            sumRow.add(message(code:'financials.export.sums'))
                            if(sumcell > 0) {
                                for(h;h < sumcell;h++) {
                                    sumRow.add(" ")
                                }
                                BigDecimal localSum = BigDecimal.valueOf(financialData[viewMode].sums.localSums.localSum)
                                sumRow.add(localSum.setScale(2,RoundingMode.HALF_UP))
                            }
                            for(h;h < sumcellAfterTax;h++) {
                                sumRow.add(" ")
                            }
                            BigDecimal localSumAfterTax = BigDecimal.valueOf(financialData[viewMode].sums.localSums.localSumAfterTax)
                            sumRow.add(localSumAfterTax.setScale(2,RoundingMode.HALF_UP))
                            rowData.add(sumRow)
                            rowData.add([])
                            financialData[viewMode].sums.billingSums.each { entry ->
                                int i = 0
                                sumRow = []
                                for(i;i < sumTitleCell;i++) {
                                    sumRow.add(" ")
                                }
                                sumRow.add(entry.currency)
                                if(sumCurrencyCell > 0) {
                                    for(i;i < sumCurrencyCell;i++) {
                                        sumRow.add(" ")
                                    }
                                    BigDecimal billingSum = BigDecimal.valueOf(entry.billingSum)
                                    sumRow.add(billingSum.setScale(2,RoundingMode.HALF_UP))
                                }
                                for(i;i < sumCurrencyAfterTaxCell;i++) {
                                    sumRow.add(" ")
                                }
                                BigDecimal billingSumAfterTax = BigDecimal.valueOf(entry.billingSumAfterTax)
                                sumRow.add(billingSumAfterTax.setScale(2,RoundingMode.HALF_UP))
                                rowData.add(sumRow)
                            }
                            writer.write(exportService.generateSeparatorTableString(titles,rowData,';'))
                        }
                        else {
                            writer.write(message(code:'finance.export.empty'))
                        }
                    }
                    out.close()
                }
            }
        }
    }

    /**
     * Make a XLSX export of cost item results
     * @param result - passed from index
     * @return
     */
    SXSSFWorkbook processFinancialXLSX(result) {
        SimpleDateFormat dateFormat = DateUtil.getSDF_NoTime()
        XSSFWorkbook workbook = new XSSFWorkbook()
        POIXMLProperties xmlProps = workbook.getProperties()
        POIXMLProperties.CoreProperties coreProps = xmlProps.getCoreProperties()
        coreProps.setCreator(message(code:'laser'))
        LinkedHashMap<Subscription,List<Org>> subscribers = [:]
        LinkedHashMap<Subscription,Set<Org>> providers = [:]
        LinkedHashMap<Subscription,BudgetCode> costItemGroups = [:]
        OrgRole.findAllByRoleTypeInList([RDStore.OR_SUBSCRIBER_CONS,RDStore.OR_SUBSCRIBER_CONS_HIDDEN]).each { it ->
            List<Org> orgs = subscribers.get(it.sub)
            if(orgs == null)
                orgs = [it.org]
            else orgs.add(it.org)
            subscribers.put(it.sub,orgs)
        }
        OrgRole.findAllByRoleTypeInList([RDStore.OR_PROVIDER,RDStore.OR_AGENCY]).each { it ->
            Set<Org> orgs = providers.get(it.sub)
            if(orgs == null)
                orgs = [it.org]
            else orgs.add(it.org)
            providers.put(it.sub,orgs)
        }
        XSSFCellStyle csPositive = workbook.createCellStyle()
        csPositive.setFillForegroundColor(new XSSFColor(new Color(198,239,206)))
        csPositive.setFillPattern(FillPatternType.SOLID_FOREGROUND)
        XSSFCellStyle csNegative = workbook.createCellStyle()
        csNegative.setFillForegroundColor(new XSSFColor(new Color(255,199,206)))
        csNegative.setFillPattern(FillPatternType.SOLID_FOREGROUND)
        XSSFCellStyle csNeutral = workbook.createCellStyle()
        csNeutral.setFillForegroundColor(new XSSFColor(new Color(255,235,156)))
        csNeutral.setFillPattern(FillPatternType.SOLID_FOREGROUND)
        SXSSFWorkbook wb = new SXSSFWorkbook(workbook,50)
        wb.setCompressTempFiles(true)
        CostItemGroup.findAll().each{ cig -> costItemGroups.put(cig.costItem,cig.budgetCode) }
        result.cost_item_tabs.entrySet().each { cit ->
            String sheettitle
            String viewMode = cit.getKey()
            switch(viewMode) {
                case "own": sheettitle = message(code:'financials.header.ownCosts')
                break
                case "cons": sheettitle = message(code:'financials.header.consortialCosts')
                break
                case "subscr": sheettitle = message(code:'financials.header.subscriptionCosts')
                break
            }
            SXSSFSheet sheet = wb.createSheet(sheettitle)
            sheet.flushRows(10)
            sheet.setAutobreaks(true)
            Row headerRow = sheet.createRow(0)
            headerRow.setHeightInPoints(16.75f)
            ArrayList titles = [message(code: 'sidewide.number')]
            if(viewMode == "cons")
                titles.addAll([message(code:'org.sortName.label'),message(code:'financials.newCosts.costParticipants'),message(code:'financials.isVisibleForSubscriber')])
            titles.add(message(code: 'financials.newCosts.costTitle'))
            if(viewMode == "cons")
                titles.add(message(code:'default.provider.label'))
            titles.addAll([message(code: 'default.subscription.label'), message(code:'subscription.startDate.label'), message(code: 'subscription.endDate.label'),
                           message(code: 'financials.costItemConfiguration'), message(code: 'package.label'), message(code: 'issueEntitlement.label'),
                           message(code: 'financials.datePaid'), message(code: 'financials.dateFrom'), message(code: 'financials.dateTo'), message(code:'financials.financialYear'),
                           message(code: 'default.status.label'), message(code: 'financials.billingCurrency'), message(code: 'financials.costInBillingCurrency'),"EUR",
                           message(code: 'financials.costInLocalCurrency')])
            if(["own","cons"].indexOf(viewMode) > -1)
                titles.addAll([message(code: 'financials.taxRate'), message(code:'financials.billingCurrency'),message(code: 'financials.costInBillingCurrencyAfterTax'),"EUR",message(code: 'financials.costInLocalCurrencyAfterTax')])
            titles.addAll([message(code: 'financials.costItemElement'),message(code: 'financials.newCosts.description'),
                           message(code: 'financials.newCosts.constsReferenceOn'), message(code: 'financials.budgetCode'),
                           message(code: 'financials.invoice_number'), message(code: 'financials.order_number'), message(code: 'globalUID.label')])
            titles.eachWithIndex { titleName, int i ->
                Cell cell = headerRow.createCell(i)
                cell.setCellValue(titleName)
            }
            sheet.createFreezePane(0, 1)
            Row row
            Cell cell
            int rownum = 1
            int sumcell = -1
            int sumcellAfterTax = -1
            int sumTitleCell = -1
            int sumCurrencyCell = -1
            int sumCurrencyAfterTaxCell = -1
            HashSet<String> currencies = new HashSet<String>()
            if(cit.getValue().count > 0) {
                cit.getValue().costItems.each { ci ->
                    BudgetCode codes = costItemGroups.get(ci)
                    String start_date   = ci.startDate ? dateFormat.format(ci?.startDate) : ''
                    String end_date     = ci.endDate ? dateFormat.format(ci?.endDate) : ''
                    String paid_date    = ci.datePaid ? dateFormat.format(ci?.datePaid) : ''
                    int cellnum = 0
                    row = sheet.createRow(rownum)
                    //sidewide number
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(rownum)
                    if(viewMode == "cons") {
                        if(ci.sub) {
                            List<Org> orgRoles = subscribers.get(ci.sub)
                            //participants (visible?)
                            Cell cellA = row.createCell(cellnum++)
                            Cell cellB = row.createCell(cellnum++)
                            String cellValueA = ""
                            String cellValueB = ""
                            orgRoles.each { or ->
                                cellValueA += or.sortname
                                cellValueB += or.name
                            }
                            cellA.setCellValue(cellValueA)
                            cellB.setCellValue(cellValueB)
                            cell = row.createCell(cellnum++)
                            cell.setCellValue(ci.isVisibleForSubscriber ? message(code:'financials.isVisibleForSubscriber') : "")
                        }
                    }
                    //cost title
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(ci.costTitle ?: '')
                    if(viewMode == "cons") {
                        //provider
                        cell = row.createCell(cellnum++)
                        if(ci.sub) {
                            Set<Org> orgRoles = providers.get(ci.sub)
                            String cellValue = ""
                            orgRoles.each { or ->
                                cellValue += or.name
                            }
                            cell.setCellValue(cellValue)
                        }
                        else cell.setCellValue("")
                    }
                    //cell.setCellValue(ci.sub ? ci.sub"")
                    //subscription
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(ci.sub ? ci.sub.name : "")
                    //dates from-to
                    Cell fromCell = row.createCell(cellnum++)
                    Cell toCell = row.createCell(cellnum++)
                    if(ci.sub) {
                        if(ci.sub.startDate)
                            fromCell.setCellValue(dateFormat.format(ci.sub.startDate))
                        else
                            fromCell.setCellValue("")
                        if(ci.sub.endDate)
                            toCell.setCellValue(dateFormat.format(ci.sub.endDate))
                        else
                            toCell.setCellValue("")
                    }
                    //cost sign
                    cell = row.createCell(cellnum++)
                    if(ci.costItemElementConfiguration) {
                        cell.setCellValue(ci.costItemElementConfiguration.getI10n("value"))
                    }
                    else
                        cell.setCellValue(message(code:'financials.costItemConfiguration.notSet'))
                    //subscription package
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(ci?.subPkg ? ci.subPkg.pkg.name:'')
                    //issue entitlement
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(ci?.issueEntitlement ? ci.issueEntitlement?.tipp?.title?.title:'')
                    //date paid
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(paid_date ?: '')
                    //date from
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(start_date ?: '')
                    //date to
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(end_date ?: '')
                    //financial year
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(ci?.financialYear ? ci.financialYear.toString():'')
                    //for the sum title
                    sumTitleCell = cellnum
                    //cost item status
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(ci?.costItemStatus ? ci.costItemStatus.getI10n("value"):'')
                    if(["own","cons"].indexOf(viewMode) > -1) {
                        //billing currency and value
                        cell = row.createCell(cellnum++)
                        cell.setCellValue(ci?.billingCurrency ? ci.billingCurrency.value : '')
                        sumCurrencyCell = cellnum
                        cell = row.createCell(cellnum++)
                        cell.setCellValue(ci?.costInBillingCurrency ? ci.costInBillingCurrency : 0.0)
                        if(ci.costItemElementConfiguration) {
                            switch(ci.costItemElementConfiguration) {
                                case RDStore.CIEC_POSITIVE: cell.setCellStyle(csPositive)
                                break
                                case RDStore.CIEC_NEGATIVE: cell.setCellStyle(csNegative)
                                break
                                case RDStore.CIEC_NEUTRAL: cell.setCellStyle(csNeutral)
                                break
                            }
                        }
                        //local currency and value
                        cell = row.createCell(cellnum++)
                        cell.setCellValue("EUR")
                        sumcell = cellnum
                        cell = row.createCell(cellnum++)
                        cell.setCellValue(ci?.costInLocalCurrency ? ci.costInLocalCurrency : 0.0)
                        if(ci.costItemElementConfiguration) {
                            switch(ci.costItemElementConfiguration) {
                                case RDStore.CIEC_POSITIVE: cell.setCellStyle(csPositive)
                                break
                                case RDStore.CIEC_NEGATIVE: cell.setCellStyle(csNegative)
                                break
                                case RDStore.CIEC_NEUTRAL: cell.setCellStyle(csNeutral)
                                break
                            }
                        }
                        //tax rate
                        cell = row.createCell(cellnum++)
                        String taxString
                        if(ci.taxKey && ci.taxKey.display) {
                            taxString = "${ci.taxKey.taxType.getI10n('value')} (${ci.taxKey.taxRate} %)"
                        }
                        else if(ci.taxKey == CostItem.TAX_TYPES.TAX_REVERSE_CHARGE) {
                            taxString = "${ci.taxKey.taxType.getI10n('value')}"
                        }
                        else taxString = message(code:'financials.taxRate.notSet')
                        cell.setCellValue(taxString)
                    }
                    //billing currency and value
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(ci?.billingCurrency ? ci.billingCurrency.value : '')
                    sumCurrencyAfterTaxCell = cellnum
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(ci?.costInBillingCurrencyAfterTax ? ci.costInBillingCurrencyAfterTax : 0.0)
                    if(ci.costItemElementConfiguration) {
                        switch(ci.costItemElementConfiguration) {
                            case RDStore.CIEC_POSITIVE: cell.setCellStyle(csPositive)
                            break
                            case RDStore.CIEC_NEGATIVE: cell.setCellStyle(csNegative)
                            break
                            case RDStore.CIEC_NEUTRAL: cell.setCellStyle(csNeutral)
                            break
                        }
                    }
                    //local currency and value
                    cell = row.createCell(cellnum++)
                    cell.setCellValue("EUR")
                    sumcellAfterTax = cellnum
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(ci?.costInLocalCurrencyAfterTax ? ci.costInLocalCurrencyAfterTax : 0.0)
                    if(ci.costItemElementConfiguration) {
                        switch(ci.costItemElementConfiguration) {
                            case RDStore.CIEC_POSITIVE: cell.setCellStyle(csPositive)
                            break
                            case RDStore.CIEC_NEGATIVE: cell.setCellStyle(csNegative)
                            break
                            case RDStore.CIEC_NEUTRAL: cell.setCellStyle(csNeutral)
                            break
                        }
                    }
                    //cost item element
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(ci?.costItemElement?ci.costItemElement.getI10n("value") : '')
                    //cost item description
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(ci?.costDescription?: '')
                    //reference
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(ci?.reference?:'')
                    //budget codes
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(codes ? codes.value : '')
                    //invoice number
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(ci?.invoice ? ci.invoice.invoiceNumber : "")
                    //order number
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(ci?.order ? ci.order.orderNumber : "")
                    //globalUUID
                    cell = row.createCell(cellnum++)
                    cell.setCellValue(ci?.globalUID ?: '')
                    rownum++
                }
                rownum++
                sheet.createRow(rownum)
                Row sumRow = sheet.createRow(rownum)
                cell = sumRow.createCell(sumTitleCell)
                cell.setCellValue(message(code:'financials.export.sums'))
                if(sumcell > 0) {
                    cell = sumRow.createCell(sumcell)
                    BigDecimal localSum = BigDecimal.valueOf(cit.getValue().sums.localSums.localSum)
                    cell.setCellValue(localSum.setScale(2, RoundingMode.HALF_UP))
                }
                cell = sumRow.createCell(sumcellAfterTax)
                BigDecimal localSumAfterTax = BigDecimal.valueOf(cit.getValue().sums.localSums.localSumAfterTax)
                cell.setCellValue(localSumAfterTax.setScale(2, RoundingMode.HALF_UP))
                rownum++
                cit.getValue().sums.billingSums.each { entry ->
                    sumRow = sheet.createRow(rownum)
                    cell = sumRow.createCell(sumTitleCell)
                    cell.setCellValue(entry.currency)
                    if(sumCurrencyCell > 0) {
                        cell = sumRow.createCell(sumCurrencyCell)
                        BigDecimal billingSum = BigDecimal.valueOf(entry.billingSum)
                        cell.setCellValue(billingSum.setScale(2, RoundingMode.HALF_UP))
                    }
                    cell = sumRow.createCell(sumCurrencyAfterTaxCell)
                    BigDecimal billingSumAfterTax = BigDecimal.valueOf(entry.billingSumAfterTax)
                    cell.setCellValue(billingSumAfterTax.setScale(2, RoundingMode.HALF_UP))
                    rownum++
                }
            }
            else {
                row = sheet.createRow(rownum)
                cell = row.createCell(0)
                cell.setCellValue(message(code:"finance.export.empty"))
            }

            for(int i = 0; i < titles.size(); i++) {
                try {
                    sheet.autoSizeColumn(i)
                }
                catch(NullPointerException e) {
                    log.error("Null value in column ${i}")
                }
            }
        }

        wb
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    Object newCostItem() {
        Map<String, Object> result = financeService.setResultGenerics(params)
        result.putAll(financeService.setAdditionalGenericEditResults(result))
        result.modalText = message(code:'financials.addNewCost')
        result.submitButtonLabel = message(code:'default.button.create_new.label')
        result.formUrl = g.createLink(controller:'finance', action:'createOrUpdateCostItem', params:[showView: params.showView])
        Set<String> pickedSubscriptions = []
        JSON.parse(params.preselectedSubscriptions).each { String ciId ->
            CostItem ci = CostItem.get(Long.parseLong(ciId))
            pickedSubscriptions << "'${genericOIDService.getOID(ci.sub)}'"
        }
        result.pickedSubscriptions = pickedSubscriptions
        render(template: "/finance/ajaxModal", model: result)
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    Object editCostItem() {
        Map<String, Object> result = financeService.setResultGenerics(params)
        result.costItem = CostItem.get(params.id)
        result.putAll(financeService.setAdditionalGenericEditResults(result))
        if(!result.dataToDisplay.contains('subscr')) {
            if(result.costItem.taxKey)
                result.taxKey = result.costItem.taxKey
        }
        result.modalText = message(code: 'financials.editCost')
        result.submitButtonLabel = message(code:'default.button.save.label')
        result.formUrl = g.createLink(controller:'finance', action:'createOrUpdateCostItem', params:[showView: params.showView])
        render(template: "/finance/ajaxModal", model: result)
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    Object copyCostItem() {
        Map<String, Object> result = financeService.setResultGenerics(params)
        result.costItem = CostItem.get(params.id)
        result.putAll(financeService.setAdditionalGenericEditResults(result))
        result.modalText = message(code: 'financials.costItem.copy.tooltip')
        result.submitButtonLabel = message(code:'default.button.copy.label')
        result.copyCostsFromConsortia = result.costItem.owner == result.costItem.sub?.getConsortia() && result.institution.id != result.costItem.sub?.getConsortia().id
        result.taxKey = result.costItem.taxKey
        result.formUrl = createLink(controller:"finance",action:"createOrUpdateCostItem",params:[showView:params.showView, mode:"copy"])
        result.mode = "copy"
        render(template: "/finance/ajaxModal", model: result)
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def deleteCostItem() {
        Map<String, Object> result = [showView:params.showView]

        CostItem ci = CostItem.get(params.id)
        if (ci) {
            List<CostItemGroup> cigs = CostItemGroup.findAllByCostItem(ci)
            Order order = ci.order
            Invoice invoice = ci.invoice
            log.debug("deleting CostItem: " + ci)
            ci.costItemStatus = RDStore.COST_ITEM_DELETED
            ci.invoice = null
            ci.order = null
            if(ci.save(flush:true)) {
                if (!CostItem.findByOrderAndIdNotEqualAndCostItemStatusNotEqual(order, ci.id, RDStore.COST_ITEM_DELETED))
                    order.delete(flush: true)
                if (!CostItem.findByInvoiceAndIdNotEqualAndCostItemStatusNotEqual(invoice, ci.id, RDStore.COST_ITEM_DELETED))
                    invoice.delete(flush: true)
                cigs.each { CostItemGroup item ->
                    item.delete(flush:true)
                    log.debug("deleting CostItemGroup: " + item)
                }
            }
            else log.error(ci.errors.toString())
        }

        redirect(uri: request.getHeader('referer').replaceAll('(#|\\?).*', ''), params: [showView: result.showView])
    }

    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def createOrUpdateCostItem() {

        SimpleDateFormat dateFormat = DateUtil.getSDF_NoTime()
        Map<String,Object> result = financeService.setResultGenerics(params)
        CostItem newCostItem = null

      if(params.newSubscription) {
          try {
              log.debug("FinanceController::newCostItem() ${params}");

              result.institution  =  contextService.getOrg()
              result.error        =  [] as List

              Order order = null
              if (params.newOrderNumber)
                  order = Order.findByOrderNumberAndOwner(params.newOrderNumber, result.institution) ?: new Order(orderNumber: params.newOrderNumber, owner: result.institution).save(flush:true)

              Invoice invoice = null
              if (params.newInvoiceNumber)
                  invoice = Invoice.findByInvoiceNumberAndOwner(params.newInvoiceNumber, result.institution) ?: new Invoice(invoiceNumber: params.newInvoiceNumber, owner: result.institution).save(flush:true)

              Set<Subscription> subsToDo = []
              if (params.newSubscription.contains("${Subscription.class.name}:"))
              {
                  try {
                      subsToDo << genericOIDService.resolveOID(params.newSubscription)
                  } catch (Exception e) {
                      log.error("Non-valid subscription sent ${params.newSubscription}",e)
                  }
              }

              switch (params.newLicenseeTarget) {

                  case "${Subscription.class.name}:forParent":
                      // keep current
                      break
                  case "${Subscription.class.name}:forAllSubscribers":
                      // iterate over members
                      subsToDo = Subscription.findAllByInstanceOfAndStatusNotEqual(
                              genericOIDService.resolveOID(params.newSubscription),
                              RefdataValue.getByValueAndCategory('Deleted', RDConstants.SUBSCRIPTION_STATUS)
                      )
                      break
                  default:
                      if (params.newLicenseeTarget) {
                          subsToDo.clear()
                          if(params.newLicenseeTarget instanceof String)
                            subsToDo << genericOIDService.resolveOID(params.newLicenseeTarget)
                          else if(params.newLicenseeTarget instanceof String[]) {
                              params.newLicenseeTarget.each { newLicenseeTarget ->
                                  subsToDo << genericOIDService.resolveOID(newLicenseeTarget)
                              }
                          }
                      }
                      break
              }

              SubscriptionPackage pkg
              if (params.newPackage?.contains("${SubscriptionPackage.class.name}:"))
              {
                  try {
                      if (params.newPackage.split(":")[1] != 'null') {
                          pkg = SubscriptionPackage.load(params.newPackage.split(":")[1])
                      }
                  } catch (Exception e) {
                      log.error("Non-valid sub-package sent ${params.newPackage}",e)
                  }
              }

              Closure newDate = { param, format ->
                  Date date
                  try {
                      date = dateFormat.parse(param)
                  } catch (Exception e) {
                      log.debug("Unable to parse date : ${param} in format ${format}")
                  }
                  date
              }

              Date datePaid    = newDate(params.newDatePaid,  dateFormat.toPattern())
              Date startDate   = newDate(params.newStartDate, dateFormat.toPattern())
              Date endDate     = newDate(params.newEndDate,   dateFormat.toPattern())
              Date invoiceDate = newDate(params.newInvoiceDate,    dateFormat.toPattern())
              Year financialYear = params.newFinancialYear ? Year.parse(params.newFinancialYear) : null

              IssueEntitlement ie = null
              if(params.newIE)
              {
                  try {
                      ie = IssueEntitlement.load(params.newIE.split(":")[1])
                  } catch (Exception e) {
                      log.error("Non-valid IssueEntitlement sent ${params.newIE}",e)
                  }
              }

              IssueEntitlementGroup issueEntitlementGroup = null
              if(params.newTitleGroup)
              {
                  try {
                      issueEntitlementGroup = IssueEntitlementGroup.load(params.newTitleGroup.split(":")[1])
                  } catch (Exception e) {
                      log.error("Non-valid IssueEntitlementGroup sent ${params.newTitleGroup}",e)
                  }
              }

              //println(issueEntitlementGroup)
              RefdataValue billing_currency = RefdataValue.get(params.newCostCurrency)

              //RefdataValue tempCurrencyVal       = params.newCostCurrencyRate?      params.double('newCostCurrencyRate',1.00) : 1.00//def cost_local_currency   = params.newCostInLocalCurrency?   params.double('newCostInLocalCurrency', cost_billing_currency * tempCurrencyVal) : 0.00
              RefdataValue cost_item_status      = params.newCostItemStatus ?       (RefdataValue.get(params.long('newCostItemStatus'))) : null;    //estimate, commitment, etc
              RefdataValue cost_item_element     = params.newCostItemElement ?      (RefdataValue.get(params.long('newCostItemElement'))): null    //admin fee, platform, etc
              //moved to TAX_TYPES
              //RefdataValue cost_tax_type         = params.newCostTaxType ?          (RefdataValue.get(params.long('newCostTaxType'))) : null           //on invoice, self declared, etc

              RefdataValue cost_item_category    = params.newCostItemCategory ?     (RefdataValue.get(params.long('newCostItemCategory'))): null  //price, bank charge, etc

              NumberFormat format = NumberFormat.getInstance(LocaleContextHolder.getLocale())
              Double cost_billing_currency = params.newCostInBillingCurrency? format.parse(params.newCostInBillingCurrency.trim()).doubleValue() : 0.00
              Double cost_currency_rate    = params.newCostCurrencyRate?      params.double('newCostCurrencyRate', 1.00) : 1.00
              Double cost_local_currency   = params.newCostInLocalCurrency?   format.parse(params.newCostInLocalCurrency.trim()).doubleValue() : 0.00

              Double cost_billing_currency_after_tax   = params.newCostInBillingCurrencyAfterTax ? format.parse(params.newCostInBillingCurrencyAfterTax).doubleValue() : cost_billing_currency
              Double cost_local_currency_after_tax     = params.newCostInLocalCurrencyAfterTax ? format.parse(params.newCostInLocalCurrencyAfterTax).doubleValue() : cost_local_currency
              //moved to TAX_TYPES
              //def new_tax_rate                      = params.newTaxRate ? params.int( 'newTaxRate' ) : 0
              def tax_key = null
              if(!params.newTaxRate.contains("null")) {
                  String[] newTaxRate = params.newTaxRate.split("§")
                  RefdataValue taxType = (RefdataValue) genericOIDService.resolveOID(newTaxRate[0])
                  int taxRate = Integer.parseInt(newTaxRate[1])
                  switch(taxType.id) {
                      case RefdataValue.getByValueAndCategory("taxable", RDConstants.TAX_TYPE).id:
                          switch(taxRate) {
                              case 5: tax_key = CostItem.TAX_TYPES.TAXABLE_5
                                  break
                              case 7: tax_key = CostItem.TAX_TYPES.TAXABLE_7
                                  break
                              case 16: tax_key = CostItem.TAX_TYPES.TAXABLE_16
                                  break
                              case 19: tax_key = CostItem.TAX_TYPES.TAXABLE_19
                                  break
                          }
                          break
                      case RefdataValue.getByValueAndCategory("taxable tax-exempt",RDConstants.TAX_TYPE).id:
                          tax_key = CostItem.TAX_TYPES.TAX_EXEMPT
                          break
                      case RefdataValue.getByValueAndCategory("not taxable",RDConstants.TAX_TYPE).id:
                          tax_key = CostItem.TAX_TYPES.TAX_NOT_TAXABLE
                          break
                      case RefdataValue.getByValueAndCategory("not applicable",RDConstants.TAX_TYPE).id:
                          tax_key = CostItem.TAX_TYPES.TAX_NOT_APPLICABLE
                          break
                      case RefdataValue.getByValueAndCategory("reverse charge",RDConstants.TAX_TYPE).id:
                          tax_key = CostItem.TAX_TYPES.TAX_REVERSE_CHARGE
                          break
                  }
              }
              RefdataValue elementSign
              try {
                  elementSign = RefdataValue.get(Long.parseLong(params.ciec))
              }
              catch (Exception e) {
                  elementSign = null
              }

              boolean cost_item_isVisibleForSubscriber = (params.newIsVisibleForSubscriber ? (RefdataValue.get(params.newIsVisibleForSubscriber)?.value == 'Yes') : false)

              if (! subsToDo) {
                  subsToDo << null // Fallback for editing cost items via myInstitution/finance // TODO: ugly
              }
              subsToDo.each { sub ->

                  List<CostItem> copiedCostItems = []

                  if(params.costItemId && params.mode != 'copy') {
                      newCostItem = CostItem.get(Long.parseLong(params.costItemId))
                      //get copied cost items
                      copiedCostItems = CostItem.findAllByCopyBaseAndCostItemStatusNotEqual(newCostItem, RDStore.COST_ITEM_DELETED)
                      if(params.newOrderNumber == null || params.newOrderNumber.length() < 1) {
                          CostItem costItemWithOrder = CostItem.findByOrderAndIdNotEqualAndCostItemStatusNotEqual(newCostItem.order,newCostItem.id,RDStore.COST_ITEM_DELETED)
                          if(!costItemWithOrder)
                              newCostItem.order.delete(flush:true)
                      }
                      if(params.newInvoiceNumber == null || params.newInvoiceNumber.length() < 1) {
                          CostItem costItemWithInvoice = CostItem.findByInvoiceAndIdNotEqualAndCostItemStatusNotEqual(newCostItem.invoice,newCostItem.id,RDStore.COST_ITEM_DELETED)
                          if(!costItemWithInvoice)
                              newCostItem.invoice.delete(flush:true)
                      }
                  }
                  else {
                      newCostItem = new CostItem()
                      if(params.mode == 'copy')
                          newCostItem.copyBase = CostItem.get(Long.parseLong(params.costItemId))
                  }

                  newCostItem.owner = (Org) result.institution
                  newCostItem.sub = sub
                  newCostItem.subPkg = SubscriptionPackage.findBySubscriptionAndPkg(sub,pkg?.pkg) ?: null
                  newCostItem.issueEntitlement = IssueEntitlement.findBySubscriptionAndTipp(sub,ie?.tipp) ?: null
                  newCostItem.issueEntitlementGroup = issueEntitlementGroup ?: null
                  newCostItem.order = order
                  newCostItem.invoice = invoice
                  //continue here: test, if visibility is set to false, check visibility settings of other consortial subscriptions, check then the financial data query whether the costs will be displayed or not!
                  if(sub)
                      newCostItem.isVisibleForSubscriber = sub._getCalculatedType() == CalculatedType.TYPE_ADMINISTRATIVE ? false : cost_item_isVisibleForSubscriber
                  else newCostItem.isVisibleForSubscriber = false
                  newCostItem.costItemCategory = cost_item_category
                  newCostItem.costItemElement = cost_item_element
                  newCostItem.costItemStatus = cost_item_status
                  newCostItem.billingCurrency = billing_currency //Not specified default to GDP
                  //newCostItem.taxCode = cost_tax_type -> to taxKey
                  newCostItem.costDescription = params.newDescription ? params.newDescription.trim() : null
                  newCostItem.costTitle = params.newCostTitle ?: null
                  newCostItem.costInBillingCurrency = cost_billing_currency as Double
                  newCostItem.costInLocalCurrency = cost_local_currency as Double

                  newCostItem.finalCostRounding = params.newFinalCostRounding ? true : false
                  newCostItem.costInBillingCurrencyAfterTax = cost_billing_currency_after_tax as Double
                  newCostItem.costInLocalCurrencyAfterTax = cost_local_currency_after_tax as Double
                  newCostItem.currencyRate = cost_currency_rate as Double
                  //newCostItem.taxRate = new_tax_rate as Integer -> to taxKey
                  newCostItem.taxKey = tax_key
                  newCostItem.costItemElementConfiguration = elementSign

                  newCostItem.datePaid = datePaid
                  newCostItem.startDate = startDate
                  newCostItem.endDate = endDate
                  newCostItem.invoiceDate = invoiceDate
                  newCostItem.financialYear = financialYear

                  //Discussion needed, nobody is quite sure of the functionality behind this...
                  //I am, it is completely legacy
                  //newCostItem.includeInSubscription = null
                  newCostItem.reference = params.newReference ? params.newReference.trim() : null

                  if (newCostItem.save(flush:true)) {
                          def newBcObjs = []

                          params.list('newBudgetCodes')?.each { newbc ->
                              def bc = genericOIDService.resolveOID(newbc)
                              if (bc) {
                                  newBcObjs << bc
                                  if (! CostItemGroup.findByCostItemAndBudgetCode( newCostItem, bc )) {
                                      new CostItemGroup(costItem: newCostItem, budgetCode: bc).save(flush:true)
                                  }
                              }
                          }

                          def toDelete = newCostItem.getBudgetcodes().minus(newBcObjs)
                          toDelete.each{ bc ->
                              def cig = CostItemGroup.findByCostItemAndBudgetCode( newCostItem, bc )
                              if (cig) {
                                  log.debug('deleting ' + cig)
                                  cig.delete(flush:true)
                              }
                          }

                          //notify cost items copied from this cost item
                          copiedCostItems.each { cci ->
                              List diffs = []
                              String costTitle = cci.costTitle ?: ''
                              String prop
                              if(newCostItem.costInBillingCurrencyAfterTax != cci.costInBillingCurrency) {
                                  //diffs.add(message(code:'pendingChange.message_CI01',args:[costTitle,g.createLink(mapping:'subfinance',controller:'subscription',action:'index',params:[sub:cci.sub.id]),cci.sub.name,cci.costInBillingCurrency,newCostItem.costInBillingCurrencyAfterTax]))
                                  diffs.add([prop:'billingCurrency', msgToken: PendingChangeConfiguration.BILLING_SUM_UPDATED, oldValue: cci.costInBillingCurrency, newValue:newCostItem.costInBillingCurrencyAfterTax])
                              }
                              if(newCostItem.costInLocalCurrencyAfterTax != cci.costInLocalCurrency) {
                                  diffs.add([prop:'localCurrency',msgToken:PendingChangeConfiguration.LOCAL_SUM_UPDATED,oldValue: cci.costInLocalCurrency,newValue:newCostItem.costInLocalCurrencyAfterTax])
                              }
                              diffs.each { diff ->
                                  //JSON json = [changeDoc:[OID:"${cci.class.name}:${cci.id}",prop:prop]] as JSON
                                  //String changeDoc = json.toString()
                                  //PendingChange change = new PendingChange(costItem: cci, owner: cci.owner,desc: diff, ts: new Date(), payload: changeDoc)
                                  //change.workaroundForDatamigrate() // ERMS-2184
                                  try {
                                      PendingChange.construct([target:cci,owner:cci.owner,prop:diff.prop,oldValue:diff.oldValue,newValue:diff.newValue,msgToken:diff.msgToken,status:RDStore.PENDING_CHANGE_PENDING])
                                  }
                                  catch (CreationException e) {
                                      log.error( e.toString() )
                                  }
                              }
                          }
                   }
                  else {
                      result.error = newCostItem.errors.allErrors.collect {
                          log.error("Field: ${it.properties.field}, user input: ${it.properties.rejectedValue}, Reason! ${it.properties.code}")
                          message(code:'finance.addNew.error', args:[it.properties.field])
                      }
                  }
              } // subsToDo.each



          }
          catch ( Exception e ) {
              log.error("Problem in add cost item", e);
          }
      }


      params.remove("Add")
      // render ([newCostItem:newCostItem.id, error:result.error]) as JSON


        redirect(uri: request.getHeader('referer').replaceAll('(#|\\?).*', ''), params: [showView: result.showView])
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR", specRole="ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def importCostItems() {
        boolean withErrors = false
        Org contextOrg = contextService.org
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        flash.error = ""
        def candidates = JSON.parse(params.candidates)
        def bcJSON = JSON.parse(params.budgetCodes)
        List budgetCodes = []
        bcJSON.each { k,v ->
            if(v)
                budgetCodes[Integer.parseInt(k)] = v
        }
        candidates.eachWithIndex { ci, Integer c ->
            if(params["take${c}"]) {
                //a single cast did not work because of financialYear type mismatch
                CostItem costItem = new CostItem(owner: contextOrg)
                costItem.sub = Subscription.get(ci.sub.id) ?: null
                costItem.subPkg = SubscriptionPackage.get(ci.subPkg?.id) ?: null
                costItem.issueEntitlement = IssueEntitlement.get(ci.issueEntitlement?.id) ?: null
                costItem.order = Order.get(ci.order?.id) ?: null
                costItem.invoice = Invoice.get(ci.invoice?.id) ?: null
                costItem.billingCurrency = RefdataValue.get(ci.billingCurrency?.id) ?: null
                costItem.costItemElement = RefdataValue.get(ci.costItemElement?.id) ?: null
                costItem.costItemElementConfiguration = RefdataValue.get(ci.costItemElementConfiguration?.id) ?: null
                costItem.taxKey = CostItem.TAX_TYPES.valueOf(ci.taxKey?.name) ?: null
                costItem.costInBillingCurrency = ci.costInBillingCurrency ?: 0.0
                costItem.costInLocalCurrency = ci.costInLocalCurrency ?: 0.0
                costItem.currencyRate = ci.currencyRate ?: 0.0
                costItem.invoiceDate = ci.invoiceDate ? sdf.parse(ci.invoiceDate) : null
                costItem.financialYear = ci.financialYear ? Year.parse(ci.financialYear.value.toString()) : null
                costItem.costTitle = ci.costTitle ?: null
                costItem.costDescription = ci.costDescription ?: null
                costItem.costItemStatus = RefdataValue.get(ci.costItemStatus.id)
                costItem.reference = ci.reference ?: null
                costItem.datePaid = ci.datePaid ? sdf.parse(ci.datePaid) : null
                costItem.startDate = ci.startDate ? sdf.parse(ci.startDate) : null
                costItem.endDate = ci.endDate ? sdf.parse(ci.endDate) : null
                costItem.isVisibleForSubscriber = params["visibleForSubscriber${c}"] == 'true' ?: false
                if(!costItem.save(flush:true)) {
                    withErrors = true
                    flash.error += costItem.errors
                }
                else {
                    if(budgetCodes) {
                        String[] budgetCodeKeys
                        Pattern p = Pattern.compile('.*[,;].*')
                        String code = budgetCodes.get(c)
                        Matcher m = p.matcher(code)
                        if(m.find())
                            budgetCodeKeys = code.split('[,;]')
                        else
                            budgetCodeKeys = [code]
                        budgetCodeKeys.each { k ->
                            String bck = k.trim()
                            BudgetCode bc = BudgetCode.findByOwnerAndValue(contextOrg,bck)
                            if(!bc) {
                                bc = new BudgetCode(owner: contextOrg, value: bck)
                            }
                            if(!bc.save(flush:true)) {
                                withErrors = true
                                flash.error += bc.errors
                            }
                            else {
                                CostItemGroup cig = new CostItemGroup(costItem: costItem, budgetCode: bc)
                                if(!cig.save(flush:true)) {
                                    withErrors = true
                                    flash.error += cig.errors
                                }
                            }
                        }
                    }
                }
            }
        }
        if(!withErrors)
            redirect action: 'index'
        else redirect(controller: 'myInstitution', action: 'financeImport')
    }

    @DebugAnnotation(perm="ORG_INST,ORG_CONSORTIUM", affil="INST_EDITOR")
    @Secured(closure = { ctx.accessService.checkPermAffiliation("ORG_INST,ORG_CONSORTIUM", "INST_EDITOR") })
    def acknowledgeChange() {
        PendingChange changeAccepted = PendingChange.get(params.id)
        if(changeAccepted)
            changeAccepted.delete(flush:true)
        redirect(uri:request.getHeader('referer'))
    }

    @Deprecated
    @DebugAnnotation(test = 'hasAffiliation("INST_USER")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_USER") })
    def getRecentCostItems() {
        def dateTimeFormat     = new java.text.SimpleDateFormat(message(code:'default.date.format')) {{setLenient(false)}}
        def  institution       = contextService.getOrg()
        def  result            = [:]
        def  recentParams      = [max:10, order:'desc', sort:'lastUpdated']
        result.to              = new Date()
        result.from            = params.from? dateTimeFormat.parse(params.from): new Date()
        result.recentlyUpdated = CostItem.findAllByOwnerAndLastUpdatedBetweenAndCostItemStatusNotEqual(institution,result.from,result.to,recentParams,RDStore.COST_ITEM_DELETED)
        result.from            = dateTimeFormat.format(result.from)
        result.to              = dateTimeFormat.format(result.to)
        log.debug("FinanceController - getRecentCostItems, rendering template with model: ${result}")

        render(template: "/finance/recentlyAddedModal", model: result)
    }

    @Deprecated
    @DebugAnnotation(test = 'hasAffiliation("INST_EDITOR")')
    @Secured(closure = { ctx.springSecurityService.getCurrentUser()?.hasAffiliation("INST_EDITOR") })
    def delete() {
        log.debug("FinanceController::delete() ${params}");

        def results        =  [:]
        results.successful =  []
        results.failures   =  []
        results.message    =  null
        results.sentIDs    =  JSON.parse(params.del) //comma seperated list
        User user           =  User.get(springSecurityService.principal.id)
        Org institution    =  contextService.getOrg()

        if (!accessService.checkMinUserOrgRole(user,institution,"INST_EDITOR"))
        {
            response.sendError(403)
            return
        }

        if (results.sentIDs && institution)
        {
            def _costItem = null
            def _props

            results.sentIDs.each { id ->
                _costItem = CostItem.findByIdAndOwner(id,institution)
                if (_costItem)
                {
                    try {
                        _props = _costItem.properties
                        CostItemGroup.deleteAll(CostItemGroup.findAllByCostItem(_costItem))
                        // TODO delete BudgetCode
                        _costItem.delete(flush: true)
                        results.successful.add(id)
                        log.debug("User: ${user.username} deleted cost item with properties ${_props}")
                    } catch (Exception e)
                    {
                        log.error("FinanceController::delete() : Delete Exception",e)
                        results.failures.add(id)
                    }
                }
                else
                    results.failures.add(id)
            }

            if (results.successful.size() > 0 && results.failures.isEmpty())
                results.message = "All ${results.successful.size()} Cost Items completed successfully : ${results.successful}"
            else if (results.successful.isEmpty() && results.failures.size() > 0)
                results.message = "All ${results.failures.size()} failed, unable to delete, have they been deleted already? : ${results.failures}"
            else
                results.message = "Success completed ${results.successful.size()} out of ${results.sentIDs.size()}  Failures as follows : ${results.failures}"

        } else
            results.message = "Incorrect parameters sent, not able to process the following : ${results.sentIDs.size()==0? 'Empty, no IDs present' : results.sentIDs}"

        render results as JSON
    }

    @DebugAnnotation(perm = "ORG_CONSORTIUM", affil = "INST_EDITOR", specRole = "ROLE_ADMIN")
    @Secured(closure = {
        ctx.accessService.checkPermAffiliationX("ORG_CONSORTIUM", "INST_EDITOR", "ROLE_ADMIN")
    })
    def processCostItemsBulk() {
        Map<String,Object> result = financeService.setResultGenerics(params)
        if (!result.editable) {
            response.sendError(401); return
        }

        result.putAll(financeService.setEditVars(result.institution))
        List selectedCostItems = params.list("selectedCostItems")

        if(selectedCostItems) {

            def billing_currency = null
            if (params.long('newCostCurrency2')) //GBP,etc
            {
                billing_currency = RefdataValue.get(params.newCostCurrency2)
            }


            NumberFormat format = NumberFormat.getInstance(LocaleContextHolder.getLocale())
            def cost_billing_currency = params.newCostInBillingCurrency2 ? format.parse(params.newCostInBillingCurrency2).doubleValue() : null //0.00
            def cost_currency_rate = params.newCostCurrencyRate2 ? params.double('newCostCurrencyRate2', 1.00) : null //1.00
            def cost_local_currency = params.newCostInLocalCurrency2 ? format.parse(params.newCostInLocalCurrency2).doubleValue() : null //0.00

            def tax_key = null
            if (!params.newTaxRate2.contains("null")) {
                String[] newTaxRate = params.newTaxRate2.split("§")
                RefdataValue taxType = (RefdataValue) genericOIDService.resolveOID(newTaxRate[0])
                int taxRate = Integer.parseInt(newTaxRate[1])
                switch (taxType.id) {
                    case RefdataValue.getByValueAndCategory("taxable", RDConstants.TAX_TYPE).id:
                        switch (taxRate) {
                            case 5: tax_key = CostItem.TAX_TYPES.TAXABLE_5
                                break
                            case 7: tax_key = CostItem.TAX_TYPES.TAXABLE_7
                                break
                            case 16: tax_key = CostItem.TAX_TYPES.TAXABLE_16
                                break
                            case 19: tax_key = CostItem.TAX_TYPES.TAXABLE_19
                                break
                        }
                        break
                    case RefdataValue.getByValueAndCategory("taxable tax-exempt", RDConstants.TAX_TYPE).id:
                        tax_key = CostItem.TAX_TYPES.TAX_EXEMPT
                        break
                    case RefdataValue.getByValueAndCategory("not taxable", RDConstants.TAX_TYPE).id:
                        tax_key = CostItem.TAX_TYPES.TAX_NOT_TAXABLE
                        break
                    case RefdataValue.getByValueAndCategory("not applicable", RDConstants.TAX_TYPE).id:
                        tax_key = CostItem.TAX_TYPES.TAX_NOT_APPLICABLE
                        break
                    case RefdataValue.getByValueAndCategory("reverse charge", RDConstants.TAX_TYPE).id:
                        tax_key = CostItem.TAX_TYPES.TAX_REVERSE_CHARGE
                        break
                }
            }

            selectedCostItems.each { id ->
                CostItem costItem = CostItem.get(Long.parseLong(id))
                if(costItem && costItem.costItemStatus != RDStore.COST_ITEM_DELETED){

                    costItem.costInBillingCurrency = cost_billing_currency ?: costItem.costInBillingCurrency

                    costItem.billingCurrency = billing_currency ?: costItem.billingCurrency
                    //Not specified default to GDP
                    costItem.costInLocalCurrency = cost_local_currency ?: costItem.costInLocalCurrency

                    costItem.finalCostRounding = params.newFinalCostRounding2 ? true : false

                    costItem.currencyRate = cost_currency_rate ?: costItem.currencyRate
                    costItem.taxKey = tax_key ?: costItem.taxKey

                    costItem.save(flush:true)
                }
            }
        }

        redirect(url: request.getHeader('referer'))
    }
}
