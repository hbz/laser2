package com.k_int.kbplus

import de.laser.Identifier
import de.laser.IdentifierNamespace
import de.laser.IssueEntitlement
import de.laser.Org
import de.laser.OrgRole
import de.laser.RefdataCategory
import de.laser.RefdataValue
import de.laser.Subscription
import de.laser.TitleInstancePackagePlatform
import de.laser.base.AbstractPropertyWithCalculatedLastUpdated
import de.laser.finance.BudgetCode
import de.laser.finance.CostItem
import de.laser.finance.CostItemGroup
import de.laser.finance.PriceItem
import de.laser.helper.RDConstants
import de.laser.properties.PropertyDefinition
import de.laser.properties.PropertyDefinitionGroup
import de.laser.base.AbstractCoverage
import de.laser.IssueEntitlementCoverage
import de.laser.TIPPCoverage
import de.laser.helper.DateUtils
import de.laser.helper.RDStore
import de.laser.titles.BookInstance
import de.laser.titles.TitleInstance
import grails.gorm.transactions.Transactional
import groovy.time.TimeDuration
import org.apache.poi.POIXMLProperties
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.streaming.SXSSFSheet
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFDataFormat
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.context.i18n.LocaleContextHolder

import java.awt.*
import java.math.RoundingMode
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.List

/**
 * This service should contain the methods required to build the different exported files.
 * CSV methods will stream out the content of the file to a given output.
 * XML methods are provided to build the XML document
 * JSON methods build a Map object which can then be converted into Json.
 *
 * To be modified: the now specialised methods should be generalised into one method generating all exports.
 *
 * @author wpetit
 * @author agalffy
 */
@Transactional
class ExportService {

	SimpleDateFormat formatter = DateUtils.getSDF_ymd()
	def messageSource
	def escapeService
	def contextService

	/**
		new CSV/TSV export interface - should subsequently replace StreamOutLicenseCSV, StreamOutSubsCSV and StreamOutTitlesCSV
		expect data in structure:
		@param titleRow - {@link Collection} of column headers [header1,header2,...,headerN]
		@param columnData - {@link Collection} of the rows, each row is itself a {@link Collection}:
	 	[
		 	[column1, column2, ..., columnN], //for row 1
		 	[column1, column2, ..., columnN], //for row 2
		 	...
		 	[column1, column2, ..., columnN]  //for row N
		]
	 */
	String generateSeparatorTableString(Collection titleRow, Collection columnData,String separator) {
		List output = []
		output.add(titleRow.join(separator))
		columnData.each { row ->
			if(row.size() > 0)
				output.add(row.join(separator))
			else output.add(" ")
		}
		output.join("\n")
	}

	/**
		new XSLX export interface - should subsequently collect the Excel export points
		expect data in structure:
		 [sheet:
		 	titleRow: [colHeader1, colHeader2, ..., colHeaderN]
			columnData:[
				[field:field1,style:style1], //for row 1
				[field:field2,style:style2], //for row 2
				...,
				[field:fieldN,style:styleN]  //for row N
			]
		 ]
	 */
    SXSSFWorkbook generateXLSXWorkbook(Map sheets) {
		Locale locale = LocaleContextHolder.getLocale()
		XSSFWorkbook wb = new XSSFWorkbook()
		POIXMLProperties xmlProps = wb.getProperties()
		POIXMLProperties.CoreProperties coreProps = xmlProps.getCoreProperties()
		coreProps.setCreator(messageSource.getMessage('laser',null, locale))
		XSSFCellStyle csPositive = wb.createCellStyle()
		csPositive.setFillForegroundColor(new XSSFColor(new Color(198,239,206)))
		csPositive.setFillPattern(FillPatternType.SOLID_FOREGROUND)
		XSSFCellStyle csNegative = wb.createCellStyle()
		csNegative.setFillForegroundColor(new XSSFColor(new Color(255,199,206)))
		csNegative.setFillPattern(FillPatternType.SOLID_FOREGROUND)
		XSSFCellStyle csNeutral = wb.createCellStyle()
		csNeutral.setFillForegroundColor(new XSSFColor(new Color(255,235,156)))
		csNeutral.setFillPattern(FillPatternType.SOLID_FOREGROUND)
		XSSFCellStyle bold = wb.createCellStyle()
		XSSFFont font = wb.createFont()
		font.setBold(true)
		bold.setFont(font)
		Map workbookStyles = ['positive':csPositive,'neutral':csNeutral,'negative':csNegative,'bold':bold]
		SXSSFWorkbook output = new SXSSFWorkbook(wb,50,true)
		output.setCompressTempFiles(true)
		sheets.entrySet().eachWithIndex { sheetData, index ->
			try {
				String title = sheetData.key
				List titleRow = (List) sheetData.value.titleRow
				List columnData = (List) sheetData.value.columnData

				if (title.length() > 31) {
					title = title.substring(0, 31-3);
					title = title + "_${index+1}"
				}

				Sheet sheet = output.createSheet(title)
				sheet.setAutobreaks(true)
				int rownum = 0
				Row headerRow = sheet.createRow(rownum++)
				headerRow.setHeightInPoints(16.75f)
				titleRow.eachWithIndex{ colHeader, int i ->
					Cell cell = headerRow.createCell(i)
					cell.setCellValue(colHeader)
				}
				sheet.createFreezePane(0,1)
				Row row
				Cell cell
				CellStyle numberStyle = wb.createCellStyle();
				XSSFDataFormat df = wb.createDataFormat();
				numberStyle.setDataFormat(df.getFormat("#,##0.00"));
				columnData.each { rowData ->
					int cellnum = 0
					row = sheet.createRow(rownum)
					rowData.each { cellData ->
						cell = row.createCell(cellnum++)
						if (cellData.field instanceof String) {
							cell.setCellValue((String) cellData.field)
						} else if (cellData.field instanceof Integer) {
							cell.setCellValue((Integer) cellData.field)
						} else if (cellData.field instanceof Double || cellData.field instanceof BigDecimal) {
							cell.setCellValue((Double) cellData.field)
							cell.setCellStyle(numberStyle)
						}
						switch(cellData.style) {
							case 'positive': cell.setCellStyle(csPositive)
								break
							case 'neutral': cell.setCellStyle(csNeutral)
								break
							case 'negative': cell.setCellStyle(csNegative)
								break
							case 'bold': cell.setCellStyle(bold)
								break
						}
					}
					rownum++
				}
				for(int i = 0;i < titleRow.size(); i++) {
					try {
						sheet.autoSizeColumn(i)
					}
					catch (Exception e) {
						log.error("Null pointer exception in column ${i}")
					}
				}
			}
			catch (ClassCastException e) {
				log.error("Data delivered in inappropriate structure!")
			}
		}
        output
    }

	def exportOrg(Collection orgs, String message, boolean addHigherEducationTitles, String format) {
		Locale locale = LocaleContextHolder.getLocale()
		List titles = [messageSource.getMessage('org.sortname.label',null,locale), 'Name', messageSource.getMessage('org.shortname.label',null,locale),messageSource.getMessage('globalUID.label',null,locale)]


		if (addHigherEducationTitles) {
			titles.add(messageSource.getMessage('org.libraryType.label',null,locale))
			titles.add(messageSource.getMessage('org.libraryNetwork.label',null,locale))
			titles.add(messageSource.getMessage('org.funderType.label',null,locale))
			titles.add(messageSource.getMessage('org.region.label',null,locale))
			titles.add(messageSource.getMessage('org.country.label',null,locale))
		}

		titles.add(messageSource.getMessage('subscription.details.startDate',null,locale))
		titles.add(messageSource.getMessage('subscription.details.endDate',null,locale))
		titles.add(messageSource.getMessage('subscription.isPublicForApi.label',null,locale))
		titles.add(messageSource.getMessage('subscription.hasPerpetualAccess.label',null,locale))
		titles.add(messageSource.getMessage('default.status.label',null,locale))
		titles.add(RefdataValue.getByValueAndCategory('General contact person', RDConstants.PERSON_FUNCTION).getI10n('value'))
		//titles.add(RefdataValue.getByValueAndCategory('Functional contact', RDConstants.PERSON_CONTACT_TYPE).getI10n('value'))

		def propList = PropertyDefinition.findAllPublicAndPrivateOrgProp(contextService.getOrg())

		propList.sort { a, b -> a.name.compareToIgnoreCase b.name }

		propList.each {
			titles.add(it.name)
		}

		orgs.sort { it.sortname } //see ERMS-1196. If someone finds out how to put order clauses into GORM domain class mappings which include a join, then OK. Otherwise, we must do sorting here.
		try {
			if(format == "xlsx") {

				XSSFWorkbook workbook = new XSSFWorkbook()
				POIXMLProperties xmlProps = workbook.getProperties()
				POIXMLProperties.CoreProperties coreProps = xmlProps.getCoreProperties()
				coreProps.setCreator(messageSource.getMessage('laser',null,locale))
				SXSSFWorkbook wb = new SXSSFWorkbook(workbook,50,true)

				Sheet sheet = wb.createSheet(message)

				//the following three statements are required only for HSSF
				sheet.setAutobreaks(true)

				//the header row: centered text in 48pt font
				Row headerRow = sheet.createRow(0)
				headerRow.setHeightInPoints(16.75f)
				titles.eachWithIndex { titlesName, index ->
					Cell cell = headerRow.createCell(index)
					cell.setCellValue(titlesName)
				}

				//freeze the first row
				sheet.createFreezePane(0, 1)

				Row row
				Cell cell
				int rownum = 1


				orgs.each { org ->
					int cellnum = 0
					row = sheet.createRow(rownum)

					//Sortname
					cell = row.createCell(cellnum++)
					cell.setCellValue(org.sortname ?: '')

					//Name
					cell = row.createCell(cellnum++)
					cell.setCellValue(org.name ?: '')

					//Shortname
					cell = row.createCell(cellnum++)
					cell.setCellValue(org.shortname ?: '')

					//subscription globalUID
					cell = row.createCell(cellnum++)
					cell.setCellValue(org.globalUID)

					if (addHigherEducationTitles) {

						//libraryType
						cell = row.createCell(cellnum++)
						cell.setCellValue(org.libraryType?.getI10n('value') ?: ' ')

						//libraryNetwork
						cell = row.createCell(cellnum++)
						cell.setCellValue(org.libraryNetwork?.getI10n('value') ?: ' ')

						//funderType
						cell = row.createCell(cellnum++)
						cell.setCellValue(org.funderType?.getI10n('value') ?: ' ')

						//region
						cell = row.createCell(cellnum++)
						cell.setCellValue(org.region?.getI10n('value') ?: ' ')

						//country
						cell = row.createCell(cellnum++)
						cell.setCellValue(org.country?.getI10n('value') ?: ' ')
					}

					cell = row.createCell(cellnum++)
					cell.setCellValue(org.startDate) //null check done already in calling method

					cell = row.createCell(cellnum++)
					cell.setCellValue(org.endDate) //null check done already in calling method

					cell = row.createCell(cellnum++)
					cell.setCellValue(org.isPublicForApi)

					cell = row.createCell(cellnum++)
					cell.setCellValue(org.hasPerpetualAccess)

					cell = row.createCell(cellnum++)
					cell.setCellValue(org.status?.getI10n('value') ?: ' ')

					cell = row.createCell(cellnum++)
					cell.setCellValue(org.generalContacts ?: '')

					/*cell = row.createCell(cellnum++)
                    cell.setCellValue('')*/

					propList.each { pd ->
						def value = ''
						org.customProperties.each { prop ->
							if (prop.type.descr == pd.descr && prop.type == pd) {
								if (prop.type.isIntegerType()) {
									value = prop.intValue.toString()
								} else if (prop.type.isStringType()) {
									value = prop.stringValue ?: ''
								} else if (prop.type.isBigDecimalType()) {
									value = prop.decValue.toString()
								} else if (prop.type.isDateType()) {
									value = prop.dateValue.toString()
								} else if (prop.type.isRefdataValueType()) {
									value = prop.refValue?.getI10n('value') ?: ''
								}
							}
						}

						org.privateProperties.each { prop ->
							if (prop.type.descr == pd.descr && prop.type == pd) {
								if (prop.type.isIntegerType()) {
									value = prop.intValue.toString()
								} else if (prop.type.isStringType()) {
									value = prop.stringValue ?: ''
								} else if (prop.type.isBigDecimalType()) {
									value = prop.decValue.toString()
								} else if (prop.type.isDateType()) {
									value = prop.dateValue.toString()
								} else if (prop.type.isRefdataValueType()) {
									value = prop.refValue?.getI10n('value') ?: ''
								}

							}
						}
						cell = row.createCell(cellnum++)
						cell.setCellValue(value)
					}

					rownum++
				}

				for (int i = 0; i < titles.size(); i++) {
					sheet.autoSizeColumn(i)
				}
				// Write the output to a file
				/* String file = message + ".xlsx"
                response.setHeader "Content-disposition", "attachment; filename=\"${file}\""
                response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                wb.write(response.outputStream)
                response.outputStream.flush()
                response.outputStream.close()
                wb.dispose() */
				wb
			}
			else if(format == 'csv') {
				List orgData = []
				orgs.each{  org ->
					List row = []
					//Sortname
					row.add(org.sortname ? org.sortname.replaceAll(',','') : '')
					//Name
					row.add(org.name ? org.name.replaceAll(',','') : '')
					//Shortname
					row.add(org.shortname ? org.shortname.replaceAll(',','') : '')
					//subscription globalUID
					row.add(org.globalUID)
					if(addHigherEducationTitles) {
						//libraryType
						row.add(org.libraryType?.getI10n('value') ?: ' ')
						//libraryNetwork
						row.add(org.libraryNetwork?.getI10n('value') ?: ' ')
						//funderType
						row.add(org.funderType?.getI10n('value') ?: ' ')
						//region
						row.add(org.region?.getI10n('value') ?: ' ')
						//country
						row.add(org.country?.getI10n('value') ?: ' ')
					}
					//startDate
					row.add(org.startDate) //null check already done in calling method
					//endDate
					row.add(org.endDate) //null check already done in calling method
					//isPublicForApi
					row.add(org.isPublicForApi) //null check already done in calling method
					//hasPerpetualAccess
					row.add(org.hasPerpetualAccess) //null check already done in calling method
					//status
					row.add(org.status?.getI10n('value') ?: ' ')
					//generalContacts
					row.add(org.generalContacts ?: '')
					propList.each { pd ->
						def value = ''
						org.customProperties.each{ prop ->
							if(prop.type.descr == pd.descr && prop.type == pd) {
								if(prop.type.isIntegerType()){
									value = prop.intValue.toString()
								}
								else if (prop.type.isStringType()){
									value = prop.stringValue ?: ''
								}
								else if (prop.type.isBigDecimalType()){
									value = prop.decValue.toString()
								}
								else if (prop.type.isDateType()){
									value = prop.dateValue.toString()
								}
								else if (prop.type.isRefdataValueType()) {
									value = prop.refValue?.getI10n('value') ?: ''
								}
							}
						}
						org.privateProperties.each{ prop ->
							if(prop.type.descr == pd.descr && prop.type == pd) {
								if(prop.type.isIntegerType()){
									value = prop.intValue.toString()
								}
								else if (prop.type.isStringType()){
									value = prop.stringValue ?: ''
								}
								else if (prop.type.isBigDecimalType()){
									value = prop.decValue.toString()
								}
								else if (prop.type.isDateType()){
									value = prop.dateValue.toString()
								}
								else if (prop.type.isRefdataValueType()) {
									value = prop.refValue?.getI10n('value') ?: ''
								}
							}
						}
						row.add(value.replaceAll(',',';'))
					}
					orgData.add(row)
				}
				generateSeparatorTableString(titles,orgData,',')
			}
		}
		catch (Exception e) {
			log.error("Problem", e)
		}
	}

	/**
	 * Retrieves for the given property definition type and organisation of list of headers, containing property definition names. Includes custom and privare properties
	 * @param propDefConst - a {@link PropertyDefinition} constant which property definition type should be loaded
	 * @param contextOrg - the context {@link de.laser.Org}
	 * @return a {@link List} of headers
	 */
	List<String> loadPropListHeaders(Set<PropertyDefinition> propSet) {
		List<String> titles = []
		propSet.each {
			titles.add(it.name_de)
		}
		titles
	}

	/**
	 * Fetches for the given {@link Set} of {@link PropertyDefinition}s the values and inserts them into the cell of the given format
	 *
	 * @param propertyDefinitions - the {@link Set} of {@link PropertyDefinition}s to read the values off
	 * @param format - the format (Excel or CSV) in which the values should be outputted
	 * @param target - the target object whose property set should be consulted
	 * @param childObjects - a {@link Map} of dependent objects
	 * @return a {@link List} or a {@link List} of {@link Map}s for the export sheet containing the value
	 */
	List processPropertyListValues(Set<PropertyDefinition> propertyDefinitions, String format, def target, Map childObjects, Map objectNames, Org contextOrg) {
		if(!contextOrg)
			contextOrg = contextService.getOrg()
		List cells = []
		SimpleDateFormat sdf = DateUtils.getSimpleDateFormatByToken('default.date.format.notime')
		propertyDefinitions.each { PropertyDefinition pd ->
			Set<String> value = []
			target.propertySet.each{ AbstractPropertyWithCalculatedLastUpdated prop ->
				if(prop.type.descr == pd.descr && prop.type == pd && prop.value) {
					if(prop.refValue)
						value << prop.refValue.getI10n('value')
					else
						value << prop.getValue() ?: ' '
				}
			}
			if(childObjects) {
				childObjects.get(target).each { childObj ->
					if(childObj.hasProperty("propertySet")) {
						childObj.propertySet.findAll{ AbstractPropertyWithCalculatedLastUpdated childProp -> childProp.type.descr == pd.descr && childProp.type == pd && childProp.value && !childProp.instanceOf && (childProp.tenant == contextOrg || childProp.isPublic) }.each { AbstractPropertyWithCalculatedLastUpdated childProp ->
							if(childProp.refValue)
								value << "${childProp.refValue.getI10n('value')} (${objectNames.get(childObj)})"
							else
								value << childProp.getValue() ? "${childProp.getValue()} (${objectNames.get(childObj)})" : ' '
						}
					}
				}
			}
			def cell
			switch(format) {
				case "xls":
				case "xlsx": cell = [field: value.join(', '), style: null]
					break
				case "csv": cell = value.join('; ').replaceAll(',',';')
					break
			}
			if(cell)
				cells.add(cell)
		}
		cells
	}

	/**
	 * Make a XLSX export of cost item results
	 * @param result - passed from index
	 * @return
	 */
	SXSSFWorkbook processFinancialXLSX(Map<String,Object> result) {
		Locale locale = LocaleContextHolder.getLocale()
		SimpleDateFormat dateFormat = DateUtils.getSDF_NoTime()
		XSSFWorkbook workbook = new XSSFWorkbook()
		POIXMLProperties xmlProps = workbook.getProperties()
		POIXMLProperties.CoreProperties coreProps = xmlProps.getCoreProperties()
		coreProps.setCreator(messageSource.getMessage('laser',null,locale))
		//LinkedHashMap<Subscription,List<Org>> subscribers = [:]
		//LinkedHashMap<Subscription,Set<Org>> providers = [:]
		LinkedHashMap<Subscription, BudgetCode> costItemGroups = [:]
		/*OrgRole.findAllByRoleTypeInList([RDStore.OR_SUBSCRIBER_CONS, RDStore.OR_SUBSCRIBER_CONS_HIDDEN]).each { it ->
			List<Org> orgs = subscribers.get(it.sub)
			if(orgs == null)
				orgs = [it.org]
			else orgs.add(it.org)
			subscribers.put(it.sub,orgs)
		}
		OrgRole.findAllByRoleTypeInList([RDStore.OR_PROVIDER,RDStore.OR_AGENCY]).each { it ->
			Set<Org> orgs = providers.get(it.sub)
			if (orgs == null)
				orgs = [it.org]
			else orgs.add(it.org)
			providers.put(it.sub, orgs)
		}*/
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
				case "own": sheettitle = messageSource.getMessage('financials.header.ownCosts',null,locale)
					break
				case "cons": sheettitle = messageSource.getMessage('financials.header.consortialCosts',null,locale)
					break
				case "subscr": sheettitle = messageSource.getMessage('financials.header.subscriptionCosts',null,locale)
					break
			}
			SXSSFSheet sheet = wb.createSheet(sheettitle)
			sheet.flushRows(10)
			sheet.setAutobreaks(true)
			Row headerRow = sheet.createRow(0)
			headerRow.setHeightInPoints(16.75f)
			ArrayList titles = [messageSource.getMessage( 'sidewide.number',null,locale)]
			if(viewMode == "cons")
				titles.addAll([messageSource.getMessage('org.sortName.label',null,locale),messageSource.getMessage('financials.newCosts.costParticipants',null,locale),messageSource.getMessage('financials.isVisibleForSubscriber',null,locale)])
			titles.add(messageSource.getMessage( 'financials.newCosts.costTitle',null,locale))
			if(viewMode == "cons")
				titles.add(messageSource.getMessage('default.provider.label',null,locale))
			titles.addAll([messageSource.getMessage('default.subscription.label',null,locale), messageSource.getMessage('subscription.startDate.label',null,locale), messageSource.getMessage('subscription.endDate.label',null,locale),
						   messageSource.getMessage('financials.costItemConfiguration',null,locale), messageSource.getMessage('package.label',null,locale), messageSource.getMessage('issueEntitlement.label',null,locale),
						   messageSource.getMessage('financials.datePaid',null,locale), messageSource.getMessage('financials.dateFrom',null,locale), messageSource.getMessage('financials.dateTo',null,locale), messageSource.getMessage('financials.financialYear',null,locale),
						   messageSource.getMessage('default.status.label',null,locale), messageSource.getMessage('financials.billingCurrency',null,locale), messageSource.getMessage('financials.costInBillingCurrency',null,locale),"EUR",
						   messageSource.getMessage('financials.costInLocalCurrency',null,locale)])
			if(["own","cons"].indexOf(viewMode) > -1)
				titles.addAll([messageSource.getMessage('financials.taxRate',null,locale), messageSource.getMessage('financials.billingCurrency',null,locale),messageSource.getMessage('financials.costInBillingCurrencyAfterTax',null,locale),"EUR",messageSource.getMessage('financials.costInLocalCurrencyAfterTax',null,locale)])
			titles.addAll([messageSource.getMessage('financials.costItemElement',null,locale),messageSource.getMessage('financials.newCosts.description',null,locale),
						   messageSource.getMessage('financials.newCosts.constsReferenceOn',null,locale), messageSource.getMessage('financials.budgetCode',null,locale),
						   messageSource.getMessage('financials.invoice_number',null,locale), messageSource.getMessage('financials.order_number',null,locale), messageSource.getMessage('globalUID.label',null,locale)])
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
				cit.getValue().costItems.eachWithIndex { ci, int i ->
					//log.debug("now processing entry #${i}")
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
							List<Org> orgRoles = ci.sub.orgRelations.findAll { OrgRole oo -> oo.roleType in [RDStore.OR_SUBSCRIBER_CONS,RDStore.OR_SUBSCRIBER_CONS_HIDDEN] }.collect { it.org }
							//participants (visible?)
							Cell cellA = row.createCell(cellnum++)
							Cell cellB = row.createCell(cellnum++)
							String cellValueA = ""
							String cellValueB = ""
							orgRoles.each { Org or ->
								cellValueA += or.sortname
								cellValueB += or.name
							}
							cellA.setCellValue(cellValueA)
							cellB.setCellValue(cellValueB)
							cell = row.createCell(cellnum++)
							cell.setCellValue(ci.isVisibleForSubscriber ? messageSource.getMessage('financials.isVisibleForSubscriber',null,locale) : "")
						}
					}
					//cost title
					cell = row.createCell(cellnum++)
					cell.setCellValue(ci.costTitle ?: '')
					if(viewMode == "cons") {
						//provider
						cell = row.createCell(cellnum++)
						if(ci.sub) {
							Set<Org> orgRoles = ci.sub.orgRelations.findAll { OrgRole oo -> oo.roleType in [RDStore.OR_PROVIDER,RDStore.OR_AGENCY] }.collect { it.org }
							String cellValue = ""
							orgRoles.each { Org or ->
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
						cell.setCellValue(messageSource.getMessage('financials.costItemConfiguration.notSet',null,locale))
					//subscription package
					cell = row.createCell(cellnum++)
					cell.setCellValue(ci?.subPkg ? ci.subPkg.pkg.name:'')
					//issue entitlement
					cell = row.createCell(cellnum++)
					cell.setCellValue(ci?.issueEntitlement ? ci.issueEntitlement.tipp.name:'')
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
						else taxString = messageSource.getMessage('financials.taxRate.notSet',null,locale)
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
				cell.setCellValue(messageSource.getMessage('financials.export.sums',null,locale))
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
				cell.setCellValue(messageSource.getMessage("finance.export.empty",null,locale))
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

	/**
	 * Generates an Excel workbook of the property usage for the given {@link Map} of {@link PropertyDefinition}s.
	 * The structure of the workbook is the same as of the view _manage(Private)PropertyDefintions.gsp
	 * @param propDefs - the {@link Map} of {@link PropertyDefinition}s whose usages should be printed
	 * @return a {@link Map} of the Excel sheets containing the table data
	 */
	Map<String,Map> generatePropertyUsageExportXLS(Map propDefs) {
		Locale locale = LocaleContextHolder.getLocale()
		List titleRow = [messageSource.getMessage('default.name.label',null,locale),
						 messageSource.getMessage('propertyDefinition.expl.label',null,locale),
						 messageSource.getMessage('default.type.label',null,locale),
						 messageSource.getMessage('propertyDefinition.count.label',null,locale),
						 messageSource.getMessage('default.hardData.tooltip',null,locale),
						 messageSource.getMessage('default.multipleOccurrence.tooltip',null,locale),
						 messageSource.getMessage('default.isUsedForLogic.tooltip',null,locale),
						 messageSource.getMessage('default.mandatory.tooltip',null,locale),
						 messageSource.getMessage('default.multipleOccurrence.tooltip',null,locale)]
		Map<String,Map> sheetData = [:]
		propDefs.each { Map.Entry propDefEntry ->
			List rows = []
			propDefEntry.value.each { PropertyDefinition pd ->
				List row = []
				row.add([field:pd.getI10n("name"),style:null])
				row.add([field:pd.getI10n("expl"),style:null])
				String typeString = pd.getLocalizedValue(pd.type)
				if(pd.isRefdataValueType()) {
					List refdataValues = []
                    RefdataCategory.getAllRefdataValues(pd.refdataCategory).each { RefdataValue refdataValue ->
						refdataValues << refdataValue.getI10n("value")
					}
					typeString += "(${refdataValues.join('/')})"
				}
				row.add([field:typeString,style:null])
				row.add([field:pd.countOwnUsages(),style:null])
				row.add([field:pd.isHardData ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"),style:null])
				row.add([field:pd.multipleOccurrence ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"),style:null])
				row.add([field:pd.isUsedForLogic ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"),style:null])
				row.add([field:pd.mandatory ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"),style:null])
				row.add([field:pd.multipleOccurrence ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"),style:null])
				rows.add(row)
			}
			sheetData.put(messageSource.getMessage("propertyDefinition.${propDefEntry.key}.label",null,locale),[titleRow:titleRow,columnData:rows])
		}
		sheetData
	}

	/**
	 *
	 * @param propDefGroups
	 * @return
	 */
	Map<String,Map> generatePropertyGroupUsageXLS(Map propDefGroups) {
		Locale locale = LocaleContextHolder.getLocale()
		List titleRow = [messageSource.getMessage("default.name.label",null,locale),
						 messageSource.getMessage("propertyDefinitionGroup.table.header.description",null,locale),
						 messageSource.getMessage("propertyDefinitionGroup.table.header.properties",null,locale),
						 messageSource.getMessage("propertyDefinitionGroup.table.header.presetShow",null,locale)]
		Map<String,Map> sheetData = [:]
		propDefGroups.each { Map.Entry typeEntry ->
			List rows = []
			typeEntry.value.each { PropertyDefinitionGroup pdGroup ->
				List row = []
				row.add([field:pdGroup.name,style:null])
				row.add([field:pdGroup.description,style:null])
				row.add([field:pdGroup.getPropertyDefinitions().size(),style:null])
				row.add([field:pdGroup.isVisible ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"),style:null])
				rows.add(row)
			}
			sheetData.put(messageSource.getMessage("propertyDefinition.${typeEntry.key}.label",null,locale),[titleRow:titleRow,columnData:rows])
		}
		sheetData
	}

	/**
	 * Generates a title stream export list according to the KBART II-standard but enriched with proprietary fields such as ZDB-ID
	 * The standard is defined here: <a href="https://www.uksg.org/kbart/s5/guidelines/data_fields">KBART definition</a>
	 *
	 * @param entitlementData - a {@link Collection} containing the actual data
	 * @return a {@link Map} containing lists for the title row and the column data
	 */
	Map<String,List> generateTitleExportKBART(Collection entitlementData) {
		log.debug("Begin generateTitleExportKBART")
		List<IdentifierNamespace> otherTitleIdentifierNamespaces = getOtherIdentifierNamespaces(entitlementData)
		List<String> titleHeaders = [
				'publication_title',
				'print_identifier',
				'online_identifier',
				'date_first_issue_online',
				'num_first_vol_online',
				'num_first_issue_online',
				'date_last_issue_online',
				'num_last_vol_online',
				'num_last_issue_online',
				'title_url',
				'first_author',
				'title_id',
				'embargo_info',
				'coverage_depth',
				'notes',
				'publication_type',
				'publisher_name',
				'date_monograph_published_print',
				'date_monograph_published_online',
				'monograph_volume',
				'monograph_edition',
				'first_editor',
				'parent_publication_title_id',
				'preceding_publication_title_id',
				'access_type',
				'package_name',
				'package_id',
				'last_changed',
				'access_start_date',
				'access_end_date',
				'medium',
				'zdb_id',
				'doi_identifier',
				'ezb_id',
				'title_gokb_uuid',
				'package_gokb_uuid',
				'package_isci',
				'package_isil',
				'package_ezb_anchor',
				'ill_indicator',
				'superceding_publication_title_id',
				'monograph_parent_collection_title',
				'subject_area',
				'status',
				'zdb_ppn']
		titleHeaders.addAll(['listprice_eur',
				'listprice_gbp',
				'listprice_usd',
				'localprice_eur',
				'localprice_gbp',
				'localprice_usd'])
		titleHeaders.addAll(otherTitleIdentifierNamespaces.collect { IdentifierNamespace ns -> "${ns.ns}_identifer"})
		Map<String,List> export = [titleRow:titleHeaders,columnData:[]]
		List allRows = []
		Set<TitleInstancePackagePlatform> titleInstances = []
		//this double strucutre is necessary because the KBART standard foresees for each coverageStatement an own row with the full data
		entitlementData.each { ieObj ->
			def entitlement
			if(ieObj instanceof IssueEntitlement) {
				entitlement = (IssueEntitlement) ieObj
				titleInstances << entitlement.tipp
			}
			else if(ieObj instanceof TitleInstancePackagePlatform) {
				entitlement = (TitleInstancePackagePlatform) ieObj
				titleInstances << entitlement
			}
			if(entitlement) {
				if(!entitlement.coverages && !entitlement.priceItems) {
					allRows << entitlement
				}
				else if(entitlement.coverages.size() == 1){
					allRows << entitlement
				}
				else {
					entitlement.coverages.each { AbstractCoverage covStmt ->
						allRows << covStmt
					}
				}
			}
		}
		allRows.each { rowData ->
			IssueEntitlement entitlement = null
			TitleInstancePackagePlatform tipp = null
			AbstractCoverage covStmt = null
			if(rowData instanceof IssueEntitlement) {
				entitlement = (IssueEntitlement) rowData
				tipp = entitlement.tipp
				covStmt = entitlement.coverages.size() == 1 ? entitlement.coverages[0] : null
			}
			else if(rowData instanceof IssueEntitlementCoverage) {
				covStmt = (IssueEntitlementCoverage) rowData
				entitlement = covStmt.issueEntitlement
				tipp = covStmt.issueEntitlement.tipp
			}
			else if(rowData instanceof TitleInstancePackagePlatform) {
				tipp = (TitleInstancePackagePlatform) rowData
				covStmt = tipp.coverages.size() == 1 ? tipp.coverages[0] : null
			}
			else if(rowData instanceof TIPPCoverage) {
				covStmt = (TIPPCoverage) rowData
				tipp = covStmt.tipp
			}
			List row = []
			//log.debug("processing ${tipp.name}")
			//publication_title
			row.add("${tipp.name}")

			//print_identifier - namespace pISBN is proprietary for LAS:eR because no eISBN is existing and ISBN is used for eBooks as well
			if(tipp.getIdentifierValue('pISBN'))
				row.add(tipp.getIdentifierValue('pISBN'))
			else if(tipp.getIdentifierValue('ISSN'))
				row.add(tipp.getIdentifierValue('ISSN'))
			else row.add(' ')
			//online_identifier
			if(tipp.getIdentifierValue('ISBN'))
				row.add(tipp.getIdentifierValue('ISBN'))
			else if(tipp.getIdentifierValue('eISSN'))
				row.add(tipp.getIdentifierValue('eISSN'))
			else row.add(' ')

			if(covStmt) {
				//date_first_issue_online
				row.add(covStmt.startDate ? formatter.format(covStmt.startDate) : ' ')
				//num_first_volume_online
				row.add(covStmt.startVolume ?: ' ')
				//num_first_issue_online
				row.add(covStmt.startIssue ?: ' ')
				//date_last_issue_online
				row.add(covStmt.endDate ? formatter.format(covStmt.endDate) : ' ')
				//num_last_volume_online
				row.add(covStmt.endVolume ?: ' ')
				//num_last_issue_online
				row.add(covStmt.endIssue ?: ' ')
			}
			else {
				//empty values for coverage fields
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
			}
			//title_url
			row.add(tipp.hostPlatformURL ?: ' ')
			//first_author (no value?)
			row.add(tipp.firstAuthor ?: ' ')
			//title_id (no value?)
			row.add(' ')
			if(covStmt) {
				//embargo_information
				row.add(covStmt.embargo ?: ' ')
				//coverage_depth
				row.add(covStmt.coverageDepth ?: ' ')
				//notes
				row.add(covStmt.coverageNote ?: ' ')
			}
			else {
				//empty values for coverage fields
				row.add(' ')
				row.add(' ')
				row.add(' ')
			}
			//publication_type
			switch(tipp.titleType) {
				case "Journal": row.add('serial')
					break
				case "Book": row.add('monograph')
					break
				case "Database": row.add('database')
					break
				default: row.add('other')
					break
			}
			//publisher_name (no value?)
			row.add(' ')
			if(tipp.titleType == 'Book') {
				//date_monograph_published_print (no value unless BookInstance)
				row.add(tipp.dateFirstInPrint ? formatter.format(tipp.dateFirstInPrint) : ' ')
				//date_monograph_published_online (no value unless BookInstance)
				row.add(tipp.dateFirstOnline ? formatter.format(tipp.dateFirstOnline) : ' ')
				//monograph_volume (no value unless BookInstance)
				row.add(tipp.volume ?: ' ')
				//monograph_edition (no value unless BookInstance)
				row.add(tipp.editionNumber ?: ' ')
				//first_editor (no value unless BookInstance)
				row.add(tipp.firstEditor ?: ' ')
			}
			else {
				//empty values from date_monograph_published_print to first_editor
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
			}
			//parent_publication_title_id (no values defined for LAS:eR, must await GOKb)
			row.add(' ')
			//preceding_publication_title_id (no values defined for LAS:eR, must await GOKb)
			row.add(' ')
			//access_type (no values defined for LAS:eR, must await GOKb)
			row.add(' ')
			/*
            switch(entitlement.tipp.payment) {
                case RDStore.TIPP_PAYMENT_OA: row.add('F')
                    break
                case RDStore.TIPP_PAYMENT_PAID: row.add('P')
                    break
                default: row.add(' ')
                    break
            }*/
			//package_name
			row.add(tipp.pkg.name ?: ' ')
			//package_id
			row.add(joinIdentifiers(tipp.pkg.ids,IdentifierNamespace.PKG_ID,','))
			//last_changed
			row.add(tipp.lastUpdated ? formatter.format(tipp.lastUpdated) : ' ')
			//access_start_date
			row.add(entitlement?.accessStartDate ? formatter.format(entitlement.accessStartDate) : (tipp.accessStartDate ? formatter.format(tipp.accessStartDate) : ' ') )
			//access_end_date
			row.add(entitlement?.accessEndDate ? formatter.format(entitlement.accessEndDate) : (tipp.accessEndDate ? formatter.format(tipp.accessEndDate) : ' '))
			//medium
			row.add(tipp.medium.value ?: ' ')


			//zdb_id
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.ZDB,','))
			//doi_identifier
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.DOI,','))
			//ezb_id
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.EZB,','))
			//title_gokb_uuid
			row.add(tipp.gokbId)
			//package_gokb_uid
			row.add(tipp.pkg.gokbId)
			//package_isci
			row.add(joinIdentifiers(tipp.pkg.ids,IdentifierNamespace.ISCI,','))
			//package_isil
			row.add(joinIdentifiers(tipp.pkg.ids,IdentifierNamespace.ISIL_PAKETSIGEL,','))
			//package_ezb_anchor
			row.add(joinIdentifiers(tipp.pkg.ids,IdentifierNamespace.EZB_ANCHOR,','))
			//ill_indicator
			row.add(' ')
			//superceding_publication_title_id
			row.add(' ')
			//monograph_parent_collection_title
			row.add(tipp.seriesName ?: '')
			//subject_area
			row.add(tipp.subjectReference ?: '')
			//status
			row.add(tipp.status.value ?: '')

			//zdb_ppn
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.ZDB_PPN,','))
			/*if(entitlement) {
				//ezb_anchor
				row.add(joinIdentifiers(entitlement.subscription.ids,IdentifierNamespace.EZB_ANCHOR,','))
				//ezb_collection_id
				row.add(joinIdentifiers(entitlement.subscription.ids,IdentifierNamespace.EZB_COLLECTION_ID,','))
				//subscription_isil
				row.add(joinIdentifiers(entitlement.subscription.ids,IdentifierNamespace.ISIL_PAKETSIGEL,','))
				//subscription_isci
				row.add(joinIdentifiers(entitlement.subscription.ids,IdentifierNamespace.ISCI,','))
			}
			else {
				//empty values for subscription identifiers
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
			}*/
			/*//ISSNs
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.ISSN,','))
			//eISSNs
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.EISSN,','))
			//pISBNs
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.PISBN,','))
			//ISBNs
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.PISBN,','))*/
			//other identifier namespaces

			if(entitlement?.priceItems) {
				//listprice_eur
				row.add(entitlement.priceItems.find {it.listCurrency == RDStore.CURRENCY_EUR}?.listPrice ?: ' ')
				//listprice_gbp
				row.add(entitlement.priceItems.find {it.listCurrency == RDStore.CURRENCY_GBP}?.listPrice ?: ' ')
				//listprice_usd
				row.add(entitlement.priceItems.find {it.listCurrency == RDStore.CURRENCY_USD}?.listPrice ?: ' ')
				//localprice_eur
				row.add(entitlement.priceItems.find {it.localCurrency == RDStore.CURRENCY_EUR}?.localPrice ?: ' ')
				//localprice_gbp
				row.add(entitlement.priceItems.find {it.localCurrency == RDStore.CURRENCY_GBP}?.localPrice ?: ' ')
				//localprice_usd
				row.add(entitlement.priceItems.find {it.localCurrency == RDStore.CURRENCY_USD}?.localPrice ?: ' ')
			} else if (tipp.priceItems) {
				//listprice_eur
				row.add(tipp.priceItems.find { it.listCurrency == RDStore.CURRENCY_EUR }?.listPrice ?: ' ')
				//listprice_gbp
				row.add(tipp.priceItems.find { it.listCurrency == RDStore.CURRENCY_GBP }?.listPrice ?: ' ')
				//listprice_usd
				row.add(tipp.priceItems.find { it.listCurrency == RDStore.CURRENCY_USD }?.listPrice ?: ' ')
				//localprice_eur
				row.add(tipp.priceItems.find { it.localCurrency == RDStore.CURRENCY_EUR }?.localPrice ?: ' ')
				//localprice_gbp
				row.add(tipp.priceItems.find { it.localCurrency == RDStore.CURRENCY_GBP }?.localPrice ?: ' ')
				//localprice_usd
				row.add(tipp.priceItems.find { it.localCurrency == RDStore.CURRENCY_USD }?.localPrice ?: ' ')
			}
			else {
				//empty values for price item columns
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
			}

			otherTitleIdentifierNamespaces.each { IdentifierNamespace ns ->
				row.add(joinIdentifiers(tipp.ids,ns.ns,','))
			}
			export.columnData.add(row)
		}
		log.debug("End generateTitleExportKBART")
		export
	}

	String joinIdentifiers(Set<Identifier>ids, String namespace, String separator) {
		String joined = ' '
		List values = []
		ids.each { id ->
			if(id.ns.ns.equalsIgnoreCase(namespace)) {
				values.add(id.value)
			}
		}
		if(values)
			joined = values.join(separator)
		joined
	}

	Map<String,List> generateTitleExportCSV(Collection entitlementData) {
		log.debug("Begin generateTitleExportCSV")
		List<IdentifierNamespace> otherTitleIdentifierNamespaces = getOtherIdentifierNamespaces(entitlementData)
		List<String> titleHeaders = [
				'publication_title',
				'print_identifier',
				'online_identifier',
				'date_first_issue_online',
				'num_first_vol_online',
				'num_first_issue_online',
				'date_last_issue_online',
				'num_last_vol_online',
				'num_last_issue_online',
				'title_url',
				'first_author',
				'title_id',
				'embargo_info',
				'coverage_depth',
				'notes',
				'publication_type',
				'publisher_name',
				'date_monograph_published_print',
				'date_monograph_published_online',
				'monograph_volume',
				'monograph_edition',
				'first_editor',
				'parent_publication_title_id',
				'preceding_publication_title_id',
				'access_type',
				'package_name',
				'package_id',
				'last_changed',
				'access_start_date',
				'access_end_date',
				'medium',
				'zdb_id',
				'doi_identifier',
				'ezb_id',
				'title_gokb_uuid',
				'package_gokb_uuid',
				'package_isci',
				'package_isil',
				'package_ezb_anchor',
				'ill_indicator',
				'superceding_publication_title_id',
				'monograph_parent_collection_title',
				'subject_area',
				'status',
				'zdb_ppn']
		titleHeaders.addAll(['listprice_eur',
							 'listprice_gbp',
							 'listprice_usd',
							 'localprice_eur',
							 'localprice_gbp',
							 'localprice_usd'])
		titleHeaders.addAll(otherTitleIdentifierNamespaces.collect { IdentifierNamespace ns -> "${ns.ns}_identifer"})
		Map<String,List> export = [titleRow:titleHeaders,rows:[]]
		List allRows = []
		Set<TitleInstancePackagePlatform> titleInstances = []
		//this double strucutre is necessary because the KBART standard foresees for each coverageStatement an own row with the full data
		entitlementData.each { ieObj ->
			def entitlement
			if(ieObj instanceof IssueEntitlement) {
				entitlement = (IssueEntitlement) ieObj
				titleInstances << entitlement.tipp
			}
			else if(ieObj instanceof TitleInstancePackagePlatform) {
				entitlement = (TitleInstancePackagePlatform) ieObj
				titleInstances << entitlement
			}
			if(entitlement) {
				if(!entitlement.coverages && !entitlement.priceItems) {
					allRows << entitlement
				}
				else if(entitlement.coverages.size() == 1){
					allRows << entitlement
				}
				else {
					entitlement.coverages.each { AbstractCoverage covStmt ->
						allRows << covStmt
					}
				}
			}
		}
		allRows.each { rowData ->
			IssueEntitlement entitlement = null
			TitleInstancePackagePlatform tipp = null
			AbstractCoverage covStmt = null
			if(rowData instanceof IssueEntitlement) {
				entitlement = (IssueEntitlement) rowData
				tipp = entitlement.tipp
				covStmt = entitlement.coverages.size() == 1 ? entitlement.coverages[0] : null
			}
			else if(rowData instanceof IssueEntitlementCoverage) {
				covStmt = (IssueEntitlementCoverage) rowData
				entitlement = covStmt.issueEntitlement
				tipp = covStmt.issueEntitlement.tipp
			}
			else if(rowData instanceof TitleInstancePackagePlatform) {
				tipp = (TitleInstancePackagePlatform) rowData
				covStmt = tipp.coverages.size() == 1 ? tipp.coverages[0] : null
			}
			else if(rowData instanceof TIPPCoverage) {
				covStmt = (TIPPCoverage) rowData
				tipp = covStmt.tipp
			}
			List row = []
			//log.debug("processing ${tipp.name}")
			//publication_title
			row.add("${tipp.name}")

			//print_identifier - namespace pISBN is proprietary for LAS:eR because no eISBN is existing and ISBN is used for eBooks as well
			if(tipp.getIdentifierValue('pISBN'))
				row.add(tipp.getIdentifierValue('pISBN'))
			else if(tipp.getIdentifierValue('ISSN'))
				row.add(tipp.getIdentifierValue('ISSN'))
			else row.add(' ')
			//online_identifier
			if(tipp.getIdentifierValue('ISBN'))
				row.add(tipp.getIdentifierValue('ISBN'))
			else if(tipp.getIdentifierValue('eISSN'))
				row.add(tipp.getIdentifierValue('eISSN'))
			else row.add(' ')

			if(covStmt) {
				//date_first_issue_online
				row.add(covStmt.startDate ? formatter.format(covStmt.startDate) : ' ')
				//num_first_volume_online
				row.add(covStmt.startVolume ?: ' ')
				//num_first_issue_online
				row.add(covStmt.startIssue ?: ' ')
				//date_last_issue_online
				row.add(covStmt.endDate ? formatter.format(covStmt.endDate) : ' ')
				//num_last_volume_online
				row.add(covStmt.endVolume ?: ' ')
				//num_last_issue_online
				row.add(covStmt.endIssue ?: ' ')
			}
			else {
				//empty values for coverage fields
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
			}
			//title_url
			row.add(tipp.hostPlatformURL ?: ' ')
			//first_author (no value?)
			row.add(tipp.firstAuthor ?: ' ')
			//title_id (no value?)
			row.add(' ')
			if(covStmt) {
				//embargo_information
				row.add(covStmt.embargo ?: ' ')
				//coverage_depth
				row.add(covStmt.coverageDepth ?: ' ')
				//notes
				row.add(covStmt.coverageNote ?: ' ')
			}
			else {
				//empty values for coverage fields
				row.add(' ')
				row.add(' ')
				row.add(' ')
			}
			//publication_type
			switch(tipp.titleType) {
				case "Journal": row.add('serial')
					break
				case "Book": row.add('monograph')
					break
				case "Database": row.add('database')
					break
				default: row.add('other')
					break
			}
			//publisher_name (no value?)
			row.add(' ')
			if(tipp.titleType == 'Book') {
				//date_monograph_published_print (no value unless BookInstance)
				row.add(tipp.dateFirstInPrint ? formatter.format(tipp.dateFirstInPrint) : ' ')
				//date_monograph_published_online (no value unless BookInstance)
				row.add(tipp.dateFirstOnline ? formatter.format(tipp.dateFirstOnline) : ' ')
				//monograph_volume (no value unless BookInstance)
				row.add(tipp.volume ?: ' ')
				//monograph_edition (no value unless BookInstance)
				row.add(tipp.editionNumber ?: ' ')
				//first_editor (no value unless BookInstance)
				row.add(tipp.firstEditor ?: ' ')
			}
			else {
				//empty values from date_monograph_published_print to first_editor
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
			}
			//parent_publication_title_id (no values defined for LAS:eR, must await GOKb)
			row.add(' ')
			//preceding_publication_title_id (no values defined for LAS:eR, must await GOKb)
			row.add(' ')
			//access_type (no values defined for LAS:eR, must await GOKb)
			row.add(' ')
			/*
            switch(entitlement.tipp.payment) {
                case RDStore.TIPP_PAYMENT_OA: row.add('F')
                    break
                case RDStore.TIPP_PAYMENT_PAID: row.add('P')
                    break
                default: row.add(' ')
                    break
            }*/
			//package_name
			row.add(tipp.pkg.name ?: ' ')
			//package_id
			row.add(joinIdentifiers(tipp.pkg.ids,IdentifierNamespace.PKG_ID,','))
			//last_changed
			row.add(tipp.lastUpdated ? formatter.format(tipp.lastUpdated) : ' ')
			//access_start_date
			row.add(entitlement?.accessStartDate ? formatter.format(entitlement.accessStartDate) : (tipp.accessStartDate ? formatter.format(tipp.accessStartDate) : ' ') )
			//access_end_date
			row.add(entitlement?.accessEndDate ? formatter.format(entitlement.accessEndDate) : (tipp.accessEndDate ? formatter.format(tipp.accessEndDate) : ' '))
			//medium
			row.add(tipp.medium.value ?: ' ')


			//zdb_id
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.ZDB,','))
			//doi_identifier
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.DOI,','))
			//ezb_id
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.EZB,','))
			//title_gokb_uuid
			row.add(tipp.gokbId)
			//package_gokb_uid
			row.add(tipp.pkg.gokbId)
			//package_isci
			row.add(joinIdentifiers(tipp.pkg.ids,IdentifierNamespace.ISCI,','))
			//package_isil
			row.add(joinIdentifiers(tipp.pkg.ids,IdentifierNamespace.ISIL_PAKETSIGEL,','))
			//package_ezb_anchor
			row.add(joinIdentifiers(tipp.pkg.ids,IdentifierNamespace.EZB_ANCHOR,','))
			//ill_indicator
			row.add(' ')
			//superceding_publication_title_id
			row.add(' ')
			//monograph_parent_collection_title
			row.add(tipp.seriesName ?: '')
			//subject_area
			row.add(tipp.subjectReference ?: '')
			//status
			row.add(tipp.status.value ?: '')

			//zdb_ppn
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.ZDB_PPN,','))
			/*if(entitlement) {
				//ezb_anchor
				row.add(joinIdentifiers(entitlement.subscription.ids,IdentifierNamespace.EZB_ANCHOR,','))
				//ezb_collection_id
				row.add(joinIdentifiers(entitlement.subscription.ids,IdentifierNamespace.EZB_COLLECTION_ID,','))
				//subscription_isil
				row.add(joinIdentifiers(entitlement.subscription.ids,IdentifierNamespace.ISIL_PAKETSIGEL,','))
				//subscription_isci
				row.add(joinIdentifiers(entitlement.subscription.ids,IdentifierNamespace.ISCI,','))
			}
			else {
				//empty values for subscription identifiers
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
			}*/
			/*//ISSNs
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.ISSN,','))
			//eISSNs
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.EISSN,','))
			//pISBNs
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.PISBN,','))
			//ISBNs
			row.add(joinIdentifiers(tipp.ids,IdentifierNamespace.PISBN,','))*/
			//other identifier namespaces

			if(entitlement?.priceItems) {
				//listprice_eur
				row.add(entitlement.priceItems.find {it.listCurrency == RDStore.CURRENCY_EUR}?.listPrice ?: ' ')
				//listprice_gbp
				row.add(entitlement.priceItems.find {it.listCurrency == RDStore.CURRENCY_GBP}?.listPrice ?: ' ')
				//listprice_usd
				row.add(entitlement.priceItems.find {it.listCurrency == RDStore.CURRENCY_USD}?.listPrice ?: ' ')
				//localprice_eur
				row.add(entitlement.priceItems.find {it.localCurrency == RDStore.CURRENCY_EUR}?.localPrice ?: ' ')
				//localprice_gbp
				row.add(entitlement.priceItems.find {it.localCurrency == RDStore.CURRENCY_GBP}?.localPrice ?: ' ')
				//localprice_usd
				row.add(entitlement.priceItems.find {it.localCurrency == RDStore.CURRENCY_USD}?.localPrice ?: ' ')
			} else if (tipp.priceItems) {
				//listprice_eur
				row.add(tipp.priceItems.find { it.listCurrency == RDStore.CURRENCY_EUR }?.listPrice ?: ' ')
				//listprice_gbp
				row.add(tipp.priceItems.find { it.listCurrency == RDStore.CURRENCY_GBP }?.listPrice ?: ' ')
				//listprice_usd
				row.add(tipp.priceItems.find { it.listCurrency == RDStore.CURRENCY_USD }?.listPrice ?: ' ')
				//localprice_eur
				row.add(tipp.priceItems.find { it.localCurrency == RDStore.CURRENCY_EUR }?.localPrice ?: ' ')
				//localprice_gbp
				row.add(tipp.priceItems.find { it.localCurrency == RDStore.CURRENCY_GBP }?.localPrice ?: ' ')
				//localprice_usd
				row.add(tipp.priceItems.find { it.localCurrency == RDStore.CURRENCY_USD }?.localPrice ?: ' ')
			}
			else {
				//empty values for price item columns
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
				row.add(' ')
			}

			otherTitleIdentifierNamespaces.each { IdentifierNamespace ns ->
				row.add(joinIdentifiers(tipp.ids,ns.ns,','))
			}
			export.rows.add(row)
		}
		log.debug("End generateTitleExportCSV")
		export
	}

	Map<String, List> generateTitleExportXLS(Collection entitlements) {
		log.debug("Begin generateTitleExportXLS")
		Locale locale = LocaleContextHolder.getLocale()
		List<IdentifierNamespace> otherTitleIdentifierNamespaces = getOtherIdentifierNamespaces(entitlements)
		List<IdentifierNamespace> coreTitleIdentifierNamespaces = getCoreIdentifierNamespaces(entitlements)
		List<String> titleHeaders = [
				messageSource.getMessage('tipp.name',null,locale),
				'Print Identifier',
				'Online Identifier',
				messageSource.getMessage('package.label',null,locale),
				messageSource.getMessage('platform.label',null,locale),
				messageSource.getMessage('tipp.titleType',null,locale),
				messageSource.getMessage('tipp.medium',null,locale),
				messageSource.getMessage('tipp.accessStartDate',null,locale),
				messageSource.getMessage('tipp.accessEndDate',null,locale),
				messageSource.getMessage('tipp.hostPlatformURL',null,locale),
				messageSource.getMessage('tipp.firstAuthor',null,locale),
				messageSource.getMessage('tipp.firstEditor',null,locale),
				messageSource.getMessage('tipp.startDate',null,locale),
				messageSource.getMessage('tipp.startVolume',null,locale),
				messageSource.getMessage('tipp.startIssue',null,locale),
				messageSource.getMessage('tipp.endDate',null,locale),
				messageSource.getMessage('tipp.endVolume',null,locale),
				messageSource.getMessage('tipp.endIssue',null,locale),
				messageSource.getMessage('tipp.embargo',null,locale),
				messageSource.getMessage('tipp.coverageDepth',null,locale),
				messageSource.getMessage('tipp.coverageNote',null,locale),
				messageSource.getMessage('tipp.dateFirstInPrint',null,locale),
				messageSource.getMessage('tipp.dateFirstOnline',null,locale),
				messageSource.getMessage('tipp.volume',null,locale),
				messageSource.getMessage('tipp.editionNumber',null,locale),
				messageSource.getMessage('tipp.seriesName',null,locale),
				messageSource.getMessage('tipp.subjectReference',null,locale),
				messageSource.getMessage('tipp.status',null,locale)]

		titleHeaders.addAll([messageSource.getMessage('tipp.listprice_eur',null,locale),
							 messageSource.getMessage('tipp.listprice_gbp',null,locale),
							 messageSource.getMessage('tipp.listprice_usd',null,locale),
							 messageSource.getMessage('tipp.localprice_eur',null,locale),
							 messageSource.getMessage('tipp.localprice_gbp',null,locale),
							 messageSource.getMessage('tipp.localprice_usd',null,locale)])
		titleHeaders.addAll(coreTitleIdentifierNamespaces.collect {IdentifierNamespace ns -> "${ns.ns}"})
		titleHeaders.addAll(otherTitleIdentifierNamespaces.collect {IdentifierNamespace ns -> "${ns.ns}"})

		List allRows = []
		Set<TitleInstancePackagePlatform> titleInstances = []
		Map<String,List> export = [titles:titleHeaders]
		List rows = []
		entitlements.each { ieObj ->
			def entitlement
			if(ieObj instanceof IssueEntitlement) {
				entitlement = (IssueEntitlement) ieObj
				titleInstances << entitlement.tipp
			}
			else if(ieObj instanceof TitleInstancePackagePlatform) {
				entitlement = (TitleInstancePackagePlatform) ieObj
				titleInstances << entitlement
			}
			if(entitlement) {
				if(!entitlement.coverages && !entitlement.priceItems) {
					allRows << entitlement
				}
				else if(entitlement.coverages.size() == 1){
					allRows << entitlement
				}
				else {
					entitlement.coverages.each { AbstractCoverage covStmt ->
						allRows << covStmt
					}
				}
			}
		}
		allRows.each { rowData ->
			IssueEntitlement entitlement = null
			TitleInstancePackagePlatform tipp = null
			AbstractCoverage covStmt = null
			if(rowData instanceof IssueEntitlement) {
				entitlement = (IssueEntitlement) rowData
				tipp = entitlement.tipp
				covStmt = entitlement.coverages.size() == 1 ? entitlement.coverages[0] : null
			}
			else if(rowData instanceof IssueEntitlementCoverage) {
				covStmt = (IssueEntitlementCoverage) rowData
				entitlement = covStmt.issueEntitlement
				tipp = covStmt.issueEntitlement.tipp
			}
			else if(rowData instanceof TitleInstancePackagePlatform) {
				tipp = (TitleInstancePackagePlatform) rowData
				covStmt = tipp.coverages.size() == 1 ? tipp.coverages[0] : null
			}
			else if(rowData instanceof TIPPCoverage) {
				covStmt = (TIPPCoverage) rowData
				tipp = covStmt.tipp
			}
			List row = []
			row.add([field: tipp.name ?: '', style:null])
			//print_identifier - namespace pISBN is proprietary for LAS:eR because no eISBN is existing and ISBN is used for eBooks as well
			if(tipp.getIdentifierValue('pISBN'))
				row.add([field: tipp.getIdentifierValue('pISBN'), style:null])
			else if(tipp.getIdentifierValue('ISSN'))
				row.add([field: tipp.getIdentifierValue('ISSN'), style:null])
			else row.add([field: '', style:null])
			//online_identifier
			if(tipp.getIdentifierValue('ISBN'))
				row.add([field: tipp.getIdentifierValue('ISBN'), style:null])
			else if(tipp.getIdentifierValue('eISSN'))
				row.add([field: tipp.getIdentifierValue('eISSN'), style:null])
			else row.add([field: '', style:null])

			row.add([field: tipp.pkg.name ?: '', style:null])
			row.add([field: tipp.platform.name ?: '', style:null])
			switch(tipp.titleType) {
				case "Journal": row.add([field:'serial', style:null])
					break
				case "Book": row.add([field:'monograph', style:null])
					break
				case "Database": row.add([field:'database', style:null])
					break
				default: row.add([field:'other', style:null])
					break
			}
			row.add([field: tipp.medium ? tipp.medium.getI10n('value') : '', style:null])
			row.add([field: tipp.accessStartDate ? formatter.format(tipp.accessStartDate) : '', style:null])
			row.add([field: tipp.accessEndDate ? formatter.format(tipp.accessEndDate) : '', style:null])
			row.add([field: tipp.hostPlatformURL ?: '', style:null])
			row.add([field: tipp.firstAuthor ?: '', style:null])
			row.add([field: tipp.firstEditor ?: '', style:null])
			if(covStmt) {
				//date_first_issue_online
				row.add([field: covStmt.startDate ? formatter.format(covStmt.startDate) : ' ', style:null])
				//num_first_volume_online
				row.add([field: covStmt.startVolume ?: ' ', style:null])
				//num_first_issue_online
				row.add([field: covStmt.startIssue ?: ' ', style:null])
				//date_last_issue_online
				row.add([field: covStmt.endDate ? formatter.format(covStmt.endDate) : ' ', style:null])
				//num_last_volume_online
				row.add([field: covStmt.endVolume ?: ' ', style:null])
				//num_last_issue_online
				row.add([field: covStmt.endIssue ?: ' ', style:null])
				//embargo_information
				row.add([field: covStmt.embargo ?: ' ', style:null])
				//coverage_depth
				row.add([field: covStmt.coverageDepth ?: ' ', style:null])
				//notes
				row.add([field: covStmt.coverageNote ?: ' ', style:null])
			}
			else {
				//empty values for coverage fields
				row.add([field: '', style:null])
				row.add([field: '', style:null])
				row.add([field: '', style:null])
				row.add([field: '', style:null])
				row.add([field: '', style:null])
				row.add([field: '', style:null])
				row.add([field: '', style:null])
				row.add([field: '', style:null])
				row.add([field: '', style:null])
			}

			if(tipp.titleType == 'Book') {
				row.add([field: tipp.dateFirstInPrint ? formatter.format(tipp.dateFirstInPrint) : ' ', style:null])
				row.add([field: tipp.dateFirstOnline ? formatter.format(tipp.dateFirstOnline) : ' ', style:null])
				row.add([field: tipp.volume ?: ' ', style:null])
				row.add([field: tipp.editionNumber ?: ' ', style:null])
			}
			else {
				//empty values from date_monograph_published_print to first_editor
				row.add([field: '', style:null])
				row.add([field: '', style:null])
				row.add([field: '', style:null])
				row.add([field: '', style:null])
			}
			row.add([field: tipp.seriesName ?: ' ', style:null])
			row.add([field: tipp.subjectReference ?: ' ', style:null])
			row.add([field: tipp.status ? tipp.status.getI10n('value') : ' ', style:null])

			if(entitlement?.priceItems) {
				//listprice_eur
				row.add([field: entitlement.priceItems.find {it.listCurrency == RDStore.CURRENCY_EUR}?.listPrice ?: ' ', style:null])
				//listprice_gbp
				row.add([field: entitlement.priceItems.find {it.listCurrency == RDStore.CURRENCY_GBP}?.listPrice ?: ' ', style:null])
				//listprice_usd
				row.add([field: entitlement.priceItems.find {it.listCurrency == RDStore.CURRENCY_USD}?.listPrice ?: ' ', style:null])
				//localprice_eur
				row.add([field: entitlement.priceItems.find {it.localCurrency == RDStore.CURRENCY_EUR}?.localPrice ?: ' ', style:null])
				//localprice_gbp
				row.add([field: entitlement.priceItems.find {it.localCurrency == RDStore.CURRENCY_GBP}?.localPrice ?: ' ', style:null])
				//localprice_usd
				row.add([field: entitlement.priceItems.find {it.localCurrency == RDStore.CURRENCY_USD}?.localPrice ?: ' ', style:null])
			} else if (tipp.priceItems) {
				//listprice_eur
				row.add([field: tipp.priceItems.find { it.listCurrency == RDStore.CURRENCY_EUR }?.listPrice ?: ' ', style:null])
				//listprice_gbp
				row.add([field: tipp.priceItems.find { it.listCurrency == RDStore.CURRENCY_GBP }?.listPrice ?: ' ', style:null])
				//listprice_usd
				row.add([field: tipp.priceItems.find { it.listCurrency == RDStore.CURRENCY_USD }?.listPrice ?: ' ', style:null])
				//localprice_eur
				row.add([field: tipp.priceItems.find { it.localCurrency == RDStore.CURRENCY_EUR }?.localPrice ?: ' ', style:null])
				//localprice_gbp
				row.add([field: tipp.priceItems.find { it.localCurrency == RDStore.CURRENCY_GBP }?.localPrice ?: ' ', style:null])
				//localprice_usd
				row.add([field: tipp.priceItems.find { it.localCurrency == RDStore.CURRENCY_USD }?.localPrice ?: ' ', style:null])
			}
			else {
				//empty values for price item columns
				row.add([field: ' ', style:null])
				row.add([field: ' ', style:null])
				row.add([field: ' ', style:null])
				row.add([field: ' ', style:null])
				row.add([field: ' ', style:null])
				row.add([field: ' ', style:null])
			}

			coreTitleIdentifierNamespaces.each { IdentifierNamespace ns ->
				row.add(field: joinIdentifiers(tipp.ids,ns.ns,','), style: null)
			}
			otherTitleIdentifierNamespaces.each { IdentifierNamespace ns ->
				row.add(field: joinIdentifiers(tipp.ids,ns.ns,','), style: null)
			}
			rows.add(row)
		}
		export.rows = rows
		log.debug("End generateTitleExportXLS")
		export
	}

	List<IdentifierNamespace> getOtherIdentifierNamespaces(Collection entitlements) {
		Set<TitleInstancePackagePlatform> titleInstances = []
		entitlements.each { entObj ->
			if(entObj instanceof IssueEntitlement) {
				titleInstances << entObj.tipp
			}
			else if(entObj instanceof TitleInstancePackagePlatform) {
				titleInstances << entObj
			}
		}
		titleInstances ? IdentifierNamespace.executeQuery('select distinct(id.ns) from Identifier id where id.tipp in (:titleInstances) and id.ns.ns not in (:coreTitleNS)',[titleInstances:titleInstances,coreTitleNS:IdentifierNamespace.CORE_TITLE_NS]) : []
	}

	List<IdentifierNamespace> getCoreIdentifierNamespaces(Collection entitlements) {
		Set<TitleInstancePackagePlatform> titleInstances = []
		entitlements.each { entObj ->
			if(entObj instanceof IssueEntitlement) {
				titleInstances << entObj.tipp
			}
			else if(entObj instanceof TitleInstancePackagePlatform) {
				titleInstances << entObj
			}
		}
		titleInstances ? IdentifierNamespace.executeQuery('select distinct(id.ns) from Identifier id where id.tipp in (:titleInstances) and id.ns.ns in (:coreTitleNS)',[titleInstances:titleInstances,coreTitleNS:IdentifierNamespace.CORE_TITLE_NS]) : []
	}

	/**
	 * This function has been created to track the time taken by the different methods provided by this service
	 * It's suppose to be run at the start of an event and it will catch the time and display it.
	 * 
	 * @param event - text which will be print out, describing the event
	 * @return time when the method is called
	 */
	Date printStart(event){
		Date starttime = new Date()
		log.debug("******* Start ${event}: ${starttime} *******")
		starttime
	}
	
	/**
	 * This function has been created to track the time taken by the different methods provided by this service.
	 * It's suppose to be run at the end of an event.
	 * It will print the duration between the given time and the current time.
	 * 
	 * @param starttime - the time when the event started
	 * @param event - text which will be print out, describing the event
	 */
	void printDuration(Date starttime, String event){
		use(groovy.time.TimeCategory) {
			TimeDuration duration = new Date() - starttime
			log.debug("******* End ${event}: ${new Date()} *******")
			log.debug("Duration: ${(duration.hours*60)+duration.minutes}m ${duration.seconds}s")
		}
	}
	def formatDate(date){
		if(date){
			return formatter.format(date)
		}else
			return null
	}
	/**
	* @return the value in the required format for CSV exports.
	**/
	def val(val){
		if(val instanceof Timestamp || val instanceof Date){
			return val?formatter.format(val):" "
		}else{
			val = val? val.replaceAll('"',"'") :" "
			return "\"${val}\""
		}
	}
}
