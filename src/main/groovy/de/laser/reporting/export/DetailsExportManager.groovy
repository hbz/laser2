package de.laser.reporting.export

import de.laser.IssueEntitlement
import de.laser.License
import de.laser.Org
import de.laser.Subscription
import de.laser.finance.CostItem
import de.laser.helper.DateUtils
import de.laser.reporting.export.base.BaseDetailsExport
import de.laser.reporting.export.base.BaseExportHelper
import de.laser.reporting.export.local.CostItemExport
import de.laser.reporting.export.local.ExportLocalHelper
import de.laser.reporting.export.local.IssueEntitlementExport
import de.laser.reporting.export.myInstitution.ExportGlobalHelper
import de.laser.reporting.export.myInstitution.LicenseExport
import de.laser.reporting.export.myInstitution.OrgExport
import de.laser.reporting.export.myInstitution.SubscriptionExport
import de.laser.reporting.myInstitution.base.BaseConfig
import grails.util.Holders
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.grails.plugins.web.taglib.ApplicationTagLib

import java.text.SimpleDateFormat

class DetailsExportManager {

    static BaseDetailsExport createExport(String token, String context) {
        if (context == BaseConfig.KEY_MYINST) {
            createGlobalExport(token, [:])
        }
        else if (context in [ BaseConfig.KEY_SUBSCRIPTION ]) {
            createLocalExport(token, [:])
        }
    }

    static BaseDetailsExport createGlobalExport(String token, Map<String, Object> selectedFields) {
        ExportGlobalHelper.createExport( token, selectedFields )
    }

    static BaseDetailsExport createLocalExport(String token, Map<String, Object> selectedFields) {
        ExportLocalHelper.createExport( token, selectedFields )
    }

    static List exportAsList(BaseDetailsExport export, List<Long> idList, String format, boolean hideEmptyResults) {

        List rows = []

        List objList = resolveObjectList( export, idList )
        Map<String, Object> fields = export.getSelectedFields()
        List<String> cols = fields.collect{it -> export.getFieldLabel(it.key as String) }

        if (format == 'csv') {
            List<List<String>> csv
            List<Integer> ici
            (csv, ici) = buildCSV(export, objList, fields)

            if (hideEmptyResults) {
                ici.each { i -> /* println 'Export CSV ignored: ' + cols[i]; */ cols.removeAt(i) }
            }
            rows.add( cols.join( BaseDetailsExport.CSV_FIELD_SEPARATOR ) )

            csv.each { row ->
                if (hideEmptyResults) {
                    ici.each { i -> row.removeAt(i) }
                }
                rows.add( row.join( BaseDetailsExport.CSV_FIELD_SEPARATOR ) )
            }
        }
        else if (format == 'pdf') {
            List<List<List<String>>> pdf
            List<Integer> ici
            (pdf, ici) = buildPDF(export, objList, fields)

            if (hideEmptyResults) {
                ici.each { i -> /* println 'Export PDF ignored: ' + cols[i]; */ cols.removeAt(i) }
            }
            rows.add( buildRowAsPDF(cols) )

            pdf.each { row ->
                if (hideEmptyResults) {
                    ici.each { i -> row.removeAt(i) }
                }
                rows.add( row )
            }
        }
        rows
    }

    static Workbook exportAsWorkbook(BaseDetailsExport export, List<Long> idList, String format, boolean hideEmptyResults) {

        List objList = resolveObjectList( export, idList )

        if (format == 'xlsx') {
            buildXLSX(export, objList, export.getSelectedFields(), hideEmptyResults)
        }
    }

    static List buildCSV(BaseDetailsExport export, List objList, Map<String, Object> fields) {

        ApplicationTagLib g = Holders.grailsApplication.mainContext.getBean(ApplicationTagLib)

        List<List<String>> rows = []
        List<Integer> ici = []

        Integer[] cc = new Integer[fields.size()].collect{ 0 }

        objList.each{ obj ->
            List<String> row = export.getDetailedObject( obj, fields ).collect{ it ->
                if (it instanceof Date) {
                    SimpleDateFormat sdf = DateUtils.getSDF_NoTime()
                    return sdf.format(it)
                }
                else if (it instanceof Double) {
                    return g.formatNumber( number: it, type: 'currency',  currencySymbol: '' ).trim()
                }
                return it as String
            } // TODO date, double, etc

            if (row) {
                List<String> cols = buildRowAsCSV( row )
                cols.eachWithIndex{ c, i -> if (c) { cc[i]++ } }
                rows.add( cols )
            }
        }
        cc.eachWithIndex{ c, i -> if (c == 0) { ici.add(i) } }

        [rows, ici.reverse()]
    }

    static List<String> buildRowAsCSV(List<String> content) {

        content.collect{it ->
            boolean enclose = false
            if (! it) {
                return ''
            }
            if (it.contains( BaseDetailsExport.CSV_FIELD_QUOTATION )) {
                it = it.replaceAll( BaseDetailsExport.CSV_FIELD_QUOTATION , BaseDetailsExport.CSV_FIELD_QUOTATION + BaseDetailsExport.CSV_FIELD_QUOTATION) // !
                enclose = true
            }
            if (enclose || it.contains( BaseDetailsExport.CSV_FIELD_SEPARATOR )) {
                return BaseDetailsExport.CSV_FIELD_QUOTATION + it.trim() + BaseDetailsExport.CSV_FIELD_QUOTATION
            }
            return it.trim()
        }
    }

    static Workbook buildXLSX(BaseDetailsExport export, List objList, Map<String, Object> fields, boolean hideEmptyResults) {

        Workbook workbook = new XSSFWorkbook()
        Sheet sheet = workbook.createSheet( export.token )

        CellStyle cellStyle = workbook.createCellStyle()
        cellStyle.setVerticalAlignment( VerticalAlignment.CENTER )

        List<List<String>> rows = []
        List<Integer> ici = []
        Integer[] cc = new Integer[fields.size()].collect{ 0 }

        objList.each{ obj ->
            List<String> row = export.getDetailedObject(obj, fields)
            if (row) {
                rows.add( row )
                row.eachWithIndex{ col, i -> if (col) { cc[i]++ } }
            }
        }
        cc.eachWithIndex{ c, i -> if (c == 0) { ici.add(i) } }
        ici = ici.reverse()

        rows.eachWithIndex { row, idx ->
            if (hideEmptyResults) {
                ici.each { i -> row.removeAt(i) }
            }
            if (row) {
                Row entry = sheet.createRow(idx + 1)
                row.eachWithIndex { v, i ->

                    Cell cell = BaseExportHelper.updateCell(workbook, entry.createCell(i), v)
                    sheet.autoSizeColumn(i)
                }
            }
        }

        Row header = sheet.createRow(0)

        List<String> cols = fields.collect{it -> export.getFieldLabel(it.key as String) }
        if (hideEmptyResults) {
            ici.each { i -> /* println 'Export XLSX ignored: ' + cols[i]; */ cols.remove(i) }
        }

        cols.eachWithIndex{ row, idx ->
            Cell headerCell = header.createCell(idx)
            headerCell.setCellStyle(cellStyle)
            headerCell.setCellValue(row)
            sheet.autoSizeColumn(idx)
        }

        workbook
    }

    static List buildPDF(BaseDetailsExport export, List objList, Map<String, Object> fields) {

        ApplicationTagLib g = Holders.grailsApplication.mainContext.getBean(ApplicationTagLib)

        List<List<List<String>>> rows = []
        List<Integer> ici = []

        Integer[] cc = new Integer[fields.size()].collect{ 0 }

        objList.each{ obj ->
            List<String> row = export.getDetailedObject( obj, fields ).collect{ it ->
                if (it instanceof Date) {
                    SimpleDateFormat sdf = DateUtils.getSDF_NoTime()
                    return sdf.format(it)
                }
                else if (it instanceof Double) {
                    return g.formatNumber( number: it, type: 'currency',  currencySymbol: '' ).trim()
                }
                return it as String
            } // TODO date, double, etc

            if (row) {
                List<List<String>> cols = buildRowAsPDF( row )
                cols.eachWithIndex{ c, i -> if (c.first()) { cc[i]++ } }
                rows.add( cols )
            }
        }
        cc.eachWithIndex{ c, i -> if (c == 0) { ici.add(i) } }

        [rows, ici.reverse()]
    }

    static List<List<String>> buildRowAsPDF(List<String> content) {

        content.collect{it ->
            if (it == null) {
                return ['']
            }
            return it.split(BaseDetailsExport.CSV_VALUE_SEPARATOR).collect{ it.trim() }
        }
    }

    static List<Object> resolveObjectList(BaseDetailsExport export, List<Long> idList) {

        List<Object> result = []

        if (export.KEY == LicenseExport.KEY) {
            result = License.executeQuery('select l from License l where l.id in (:idList) order by l.reference', [idList: idList])
        }
        else if (export.KEY == OrgExport.KEY) {
            result = Org.executeQuery('select o from Org o where o.id in (:idList) order by o.sortname, o.name', [idList: idList])
        }
        else if (export.KEY == SubscriptionExport.KEY) {
            result = Subscription.executeQuery('select s from Subscription s where s.id in (:idList) order by s.name', [idList: idList])
        }
        else if (export.KEY == IssueEntitlementExport.KEY) {
            Long subId = ExportLocalHelper.getDetailsCache( export.token ).id
            result = IssueEntitlement.executeQuery(
                    'select ie from IssueEntitlement ie where ie.subscription.id = :subId and ie.tipp.id in (:idList) order by ie.name',
                    [subId: subId, idList: idList]
            )
        }
        else if (export.KEY == CostItemExport.KEY) {
//            Long subId = ExportLocalHelper.getDetailsCache( export.token ).id
//            result = CostItem.executeQuery(
//                    'select ci from CostItem ci where ci.sub.id = :subId and ci.id in (:idList) order by ci.id',
//                    [subId: subId, idList: idList]
//            )
            result = CostItem.executeQuery('select ci from CostItem ci where ci.id in (:idList) order by ci.id', [idList: idList])
        }
        result
    }
}
