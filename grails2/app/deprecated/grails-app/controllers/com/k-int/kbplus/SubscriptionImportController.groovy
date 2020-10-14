package com.k_int.kbplus

import com.k_int.kbplus.auth.User
import de.laser.OrgRole
import de.laser.RefdataValue
import de.laser.controller.AbstractDebugController
import de.laser.helper.RDConstants
import de.laser.helper.RDStore
import grails.plugin.springsecurity.annotation.Secured
import org.apache.poi.hssf.usermodel.*
import org.apache.poi.hssf.util.HSSFColor
import org.apache.poi.ss.usermodel.*

import java.text.SimpleDateFormat

@Secured(['IS_AUTHENTICATED_FULLY'])
class SubscriptionImportController extends AbstractDebugController {

  def springSecurityService
  def ESSearchService
  def genericOIDService
  def contextService

  def renewals_reversemap = ['subject':'subject', 'provider':'provid', 'pkgname':'tokname' ]
 
  def possible_date_formats = [
    new SimpleDateFormat('yyyy/MM/dd'),
    new SimpleDateFormat('dd.mm.yyyy'),
    new SimpleDateFormat('dd/MM/yyyy'),
    new SimpleDateFormat('dd/MM/yy'),
    new SimpleDateFormat('yyyy/MM'),
    new SimpleDateFormat('yyyy')
  ];

  @Deprecated
  @Secured(['ROLE_USER'])
  def generateImportWorksheet() { 

    com.k_int.kbplus.SubscriptionImportController.log.debug("renewalsSearch : ${params}");
      com.k_int.kbplus.SubscriptionImportController.log.debug("Start year filters: ${params.startYear}");

    StringWriter sw = new StringWriter()
    def fq = null;
    boolean has_filter = false
  
    params.each { p ->
      if ( p.key.startsWith('fct:') && p.value.equals("on") ) {
        com.k_int.kbplus.SubscriptionImportController.log.debug("start year ${p.key} : -${p.value}-");

        if ( !has_filter )
          has_filter = true
        else
          sw.append(" AND ")

        String[] filter_components = p.key.split(':');
            switch ( filter_components[1] ) {
              case 'consortiaName':
                sw.append('consortiaName')
                break;
              case 'startYear':
                sw.append('startYear')
                break;
              case 'cpname':
                sw.append('cpname')
                break;
              case 'endYear':
                sw.append('endYear')
                break;
            }
            if ( filter_components[2].indexOf(' ') > 0 ) {
              sw.append(":\"");
              sw.append(filter_components[2])
              sw.append("\"");
            }
            else {
              sw.append(":");
              sw.append(filter_components[2])
            }
      }
    }

    if ( has_filter ) {
      fq = sw.toString();
        com.k_int.kbplus.SubscriptionImportController.log.debug("Filter Query: ${fq}");
    }

    // Be mindful that the behavior of this controller is strongly influenced by the schema setup in ES.
    // Specifically, see KBPlus/import/processing/processing/dbreset.sh for the mappings that control field type and analysers
    // Internal testing with http://localhost:9200/kbplus/_search?q=subtype:'Subscription%20Offered'
    def result=[:]

    //result.institution = Org.findByShortcode(params.shortcode)
    def inst = params.id ? Org.get(params.id) : null

    result.user = springSecurityService.getCurrentUser()

//    def shopping_basket = UserFolder.findByUserAndShortcode(result.user,'SubBasket') ?: new UserFolder(user:result.user, shortcode:'SubBasket').save();
//
//    if ( params.addBtn ) {
//      com.k_int.kbplus.SubscriptionImportController.log.debug("Add item ${params.addBtn} to basket");
//      def oid = "com.k_int.kbplus.Package:${params.addBtn}"
//      shopping_basket.addIfNotPresent(oid)
//      shopping_basket.save(flush:true);
//    }
//    else if ( params.clearBasket=='yes' ) {
//      com.k_int.kbplus.SubscriptionImportController.log.debug("Clear basket....");
//      shopping_basket.items?.clear();
//      shopping_basket.save(flush:true)
//    }
//    else if ( params.generate=='yes' ) {
//      com.k_int.kbplus.SubscriptionImportController.log.debug("Generate");
//      generate(materialiseFolder(shopping_basket.items), inst)
//      return
//    }

//    result.basket = materialiseFolder(shopping_basket.items)
    if (fq) { //filtered query
      params.tempFQ = fq
    }
    params.rectype = 'Package'
    result.putAll(ESSearchService.search(params,renewals_reversemap))

    render (view:'packageSearch', model:result);
  }

  private def materialiseFolder(f) {
    def result = []
    f.each {
      def item_to_add = genericOIDService.resolveOID(it.referencedOid)
      if (item_to_add) {
        result.add(item_to_add)
      }
      else {
        flash.message="Folder contains item that cannot be found";
      }
    }
    result
  }

  private def generate(plist, inst) {
    try {
      def m = generateMatrix(plist)
      exportWorkbook(m, inst);
    }
    catch ( Exception e ) {
      com.k_int.kbplus.SubscriptionImportController.log.error("Problem",e);
      response.sendError(500)
    }
  }

  private def generateMatrix(plist) {

    def titleMap = [:]
    def subscriptionMap = [:]

      com.k_int.kbplus.SubscriptionImportController.log.debug("pre-pre-process");

    boolean first = true;

    SimpleDateFormat formatter = de.laser.helper.DateUtil.getSDF_NoTime()

    // Step one - Assemble a list of all titles and packages.. We aren't assembling the matrix
    // of titles x packages yet.. Just gathering the data for the X and Y axis
    plist.each { sub ->

      def sub_info = [
        sub_idx : subscriptionMap.size(),
        sub_name : sub.name,
        sub_id : "${sub.class.name}:${sub.id}"
      ]

        com.k_int.kbplus.SubscriptionImportController.log.debug("Added sub entry ${sub_info}");

      subscriptionMap[sub.id] = sub_info

      if ( sub instanceof Package ) {
        com.k_int.kbplus.SubscriptionImportController.log.debug("Adding package into renewals worksheet");
        sub.tipps.each { tipp ->
          if ( ! (tipp.status?.value=='Deleted')  ) {
            def title_info = titleMap[tipp.title.id]
            if ( !title_info ) {
              // log.debug("Adding ie: ${ie}");
              title_info = [:]
              title_info.title_idx = titleMap.size()
              title_info.id = tipp.title.id;
              title_info.issn = tipp.title.getIdentifierValue('ISSN');
              title_info.eissn = tipp.title.getIdentifierValue('eISSN');
              title_info.title = tipp.title.title
              titleMap[tipp.title.id] = title_info;
            }
          }
        }
      }

      first=false
    }

      com.k_int.kbplus.SubscriptionImportController.log.debug("Result will be a matrix of size ${titleMap.size()} by ${subscriptionMap.size()}");

    // Object[][] result = new Object[subscriptionMap.size()+1][titleMap.size()+1]
    Object[][] ti_info_arr = new Object[titleMap.size()][subscriptionMap.size()]
    Object[] sub_info_arr = new Object[subscriptionMap.size()]
    Object[] title_info_arr = new Object[titleMap.size()]

    // Run through the list of packages, and set the X axis headers accordingly
    subscriptionMap.values().each { v ->
      sub_info_arr[v.sub_idx] = v
    }

    // Run through the titles and set the Y axis headers accordingly
    titleMap.values().each { v ->
      title_info_arr[v.title_idx] = v
    }

    // Fill out the matrix by looking through each sub/package and adding the appropriate cell info
    plist.each { sub ->
      def sub_info = subscriptionMap[sub.id]
      if ( sub instanceof Package ) {
        com.k_int.kbplus.SubscriptionImportController.log.debug("Filling out renewal sheet column for a package");
        sub.tipps.each { tipp ->
          if ( ! (tipp.status?.value=='Deleted')  ) {
            def title_info = titleMap[tipp.title.id]
            def ie_info = [:]
            // log.debug("Adding tipp info ${tipp.startDate} ${tipp.derivedFrom}");
            ie_info.tipp_id = tipp.id;
            ie_info.startDate_d = tipp.startDate
            ie_info.startDate = ie_info.startDate_d ? formatter.format(ie_info.startDate_d) : null
            ie_info.startVolume = tipp.startVolume
            ie_info.startIssue = tipp.startIssue
            ie_info.endDate_d = tipp.endDate
            ie_info.endDate = ie_info.endDate_d ? formatter.format(ie_info.endDate_d) : null
            ie_info.endVolume = tipp.endVolume ?: tipp.derivedFrom?.endVolume
            ie_info.endIssue = tipp.endIssue ?: tipp.derivedFrom?.endIssue

            ti_info_arr[title_info.title_idx][sub_info.sub_idx] = ie_info
          }
        }
      }
    }

      com.k_int.kbplus.SubscriptionImportController.log.debug("Completed.. returning final result");

    def final_result = [
                        ti_info:ti_info_arr,                      // A crosstab array of the packages where a title occours
                        title_info:title_info_arr,                // A list of the titles
                        sub_info:sub_info_arr ]                   // The subscriptions offered (Packages)
    return final_result
  }

  private def exportWorkbook(m, inst) {
    try {
      com.k_int.kbplus.SubscriptionImportController.log.debug("export workbook");
  
      // read http://stackoverflow.com/questions/2824486/groovy-grails-how-do-you-stream-or-buffer-a-large-file-in-a-controllers-respon
  
      HSSFWorkbook workbook = new HSSFWorkbook();
   
      CreationHelper factory = workbook.getCreationHelper();
  
      //
      // Create two sheets in the excel document and name it First Sheet and
      // Second Sheet.
      //
      HSSFSheet firstSheet = workbook.createSheet("Renewals Worksheet");
      Drawing drawing = firstSheet.createDrawingPatriarch();
  
   
      // Cell style for a present TI
      HSSFCellStyle present_cell_style = workbook.createCellStyle();  
      present_cell_style.setFillForegroundColor(HSSFColor.LIGHT_GREEN.index);  
      present_cell_style.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);  
  
      // Cell style for a core TI
      HSSFCellStyle core_cell_style = workbook.createCellStyle();  
      core_cell_style.setFillForegroundColor(HSSFColor.LIGHT_YELLOW.index);  
      core_cell_style.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);  
  
      int rc=0;
      // header
      int cc=0;
      HSSFRow row = null;
      HSSFCell cell = null;
  
      // Blank rows
      row = firstSheet.createRow(rc++);
      row = firstSheet.createRow(rc++);
      cc=0;
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("Subscriber ID"));
      cell = row.createCell(cc++);
  
      row = firstSheet.createRow(rc++);
      cc=0;

      if ( inst != null ) {
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString("${inst.id}"));
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString(inst.name));
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString(inst.shortcode));
      }
      else {
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString("--PLEASE COMPLETE--"));
      }
  
      row = firstSheet.createRow(rc++);
  
      // Key
      row = firstSheet.createRow(rc++);
      cc=0;
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("Key"));
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("Title"));
      cell.setCellStyle(present_cell_style);  
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("Core Title"));
      cell.setCellStyle(core_cell_style);  
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("Not In Subscription"));
  
      row = firstSheet.createRow(rc++);
      cc=11
      m.sub_info.each { sub ->
          com.k_int.kbplus.SubscriptionImportController.log.debug("Adding package OID to sheet: ${sub.sub_id}");
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString("${sub.sub_id}"));
      }

        com.k_int.kbplus.SubscriptionImportController.log.debug("Done adding package IDs to sheet.. rows..");
      
      // headings
      row = firstSheet.createRow(rc++);
      cc=0;
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("Title ID"));
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("Title"));
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("ISSN"));
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("eISSN"));
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("Current Start Date"));
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("Current End Date"));
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("Current Coverage Depth"));
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("Current Coverage Note"));
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("Core Status"));
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("Core Status Checked"));
      cell = row.createCell(cc++);
      cell.setCellValue(new HSSFRichTextString("Core Medium"));
  
      m.sub_info.each { sub ->
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString("${sub.sub_name}"));
  
        // Hyperlink link = createHelper.createHyperlink(Hyperlink.LINK_URL);
        // link.setAddress("http://poi.apache.org/");
        // cell.setHyperlink(link);
      }
  
      m.title_info.each { title ->
  
        row = firstSheet.createRow(rc++);
        cc = 0;
  
        // Internal title ID
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString("${title.id}"));
        // Title
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString("${title.title?:''}"));
  
        // ISSN
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString("${title.issn?:''}"));
  
        // eISSN
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString("${title.eissn?:''}"));
  
        // startDate
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString("${title.current_start_date?:''}"));
  
        // endDate
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString("${title.current_end_date?:''}"));
  
        // coverageDepth
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString("${title.current_depth?:''}"));
  
        // embargo
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString("${title.current_coverage_note?:''}"));
  
        // IsCore
        //TODO: are we ever getting a sub in here ?
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString(""));
  
        // Core Start Date
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString(""));
  
        // Core End Date
        cell = row.createCell(cc++);
        cell.setCellValue(new HSSFRichTextString("${title.coreStatus?:''}"));
  
        m.sub_info.each { sub ->
          cell = row.createCell(cc++);
          def ie_info = m.ti_info[title.title_idx][sub.sub_idx]
          if ( ie_info ) {
            if ( ( ie_info.core ) && ( ie_info.core != 'No' ) ) {
              cell.setCellValue(new HSSFRichTextString(""));
              cell.setCellStyle(core_cell_style);  
            }
            else {
              cell.setCellValue(new HSSFRichTextString(""));
              cell.setCellStyle(present_cell_style);  
            }
            addCellComment(row, cell,"${title.title} provided by ${sub.sub_name}\nStart Date:${ie_info.startDate?:'Not set'}\nStart Volume:${ie_info.startVolume?:'Not set'}\nStart Issue:${ie_info.startIssue?:'Not set'}\nEnd Date:${ie_info.endDate?:'Not set'}\nEnd Volume:${ie_info.endVolume?:'Not set'}\nEnd Issue:${ie_info.endIssue?:'Not set'}\nSelect Title by setting this cell to Y", drawing, factory);
          }
  
        }
      }
      row = firstSheet.createRow(rc++);
      cell = row.createCell(0);
      cell.setCellValue(new HSSFRichTextString("END"));
  
      // firstSheet.autoSizeRow(6); //adjust width of row 6 (Headings for JUSP Stats)
      Row jusp_heads_row = firstSheet.getRow(6);
      jusp_heads_row.setHeight((short)(jusp_heads_row.getHeight() * 2));
  
      firstSheet.autoSizeColumn(0); //adjust width of the first column
      firstSheet.autoSizeColumn(1); //adjust width of the first column
      firstSheet.autoSizeColumn(2); //adjust width of the first column
      firstSheet.autoSizeColumn(3); //adjust width of the first column
      for ( int i=0; i<m.sub_info.size(); i++ ) {
        firstSheet.autoSizeColumn(7+i); //adjust width of the second column
      }
  
  
  
      response.setHeader "Content-disposition", "attachment; filename=\"NewSubscription.xls\""
      // response.contentType = 'application/xls'
      response.contentType = 'application/vnd.ms-excel'
      workbook.write(response.outputStream)
      response.outputStream.flush()
    }
    catch ( Exception e ) {
      com.k_int.kbplus.SubscriptionImportController.log.error("Problem",e);
      response.sendError(500)
    }
  }

  private def addCellComment(row, cell, comment_text, drawing, factory) {

    // When the comment box is visible, have it show in a 1x3 space
    ClientAnchor anchor = factory.createClientAnchor();
    anchor.setCol1(cell.getColumnIndex());
    anchor.setCol2(cell.getColumnIndex()+7);
    anchor.setRow1(row.getRowNum());
    anchor.setRow2(row.getRowNum()+9);

    // Create the comment and set the text+author
    def comment = drawing.createCellComment(anchor);
    RichTextString str = factory.createRichTextString(comment_text);
    comment.setString(str);
    comment.setAuthor("KBPlus System");

    // Assign the comment to the cell
    cell.setCellComment(comment);
  }

  @Secured(['ROLE_USER'])
  def importSubscriptionWorksheet() {
    Map<String, Object> result = [:]
      com.k_int.kbplus.SubscriptionImportController.log.debug("importSubscriptionWorksheet :: ${params}")
    result.user = User.get(springSecurityService.principal.id)
    // result.institution = Org.findByShortcode(params.shortcode)

    // if (! permissionHelperService.checkUserIsMember(result.user, result.institution) ) {
    //   flash.error="You do not have permission to view ${result.institution.name}. Please request access on the profile page";
    //   response.sendError(401)
    //   return;
    // }

    result.errors = []

    if ( request.method == 'POST' ) {
      def upload_mime_type = request.getFile("renewalsWorksheet")?.contentType
      def upload_filename = request.getFile("renewalsWorksheet")?.getOriginalFilename()
        com.k_int.kbplus.SubscriptionImportController.log.debug("Uploaded worksheet type: ${upload_mime_type} filename was ${upload_filename} - Params.id=${params.id}");
      def input_stream = request.getFile("renewalsWorksheet")?.inputStream
      def so_start_col = 11 // ( ( params.id != null ) ? 21 : 11 )
      processRenewalUpload(input_stream, upload_filename, result, so_start_col)
    }

    result
  }

  /**
   *  SO_START_COL varies depending on the presence of JUSP stats
   */
  private def processRenewalUpload(input_stream, upload_filename, result, SO_START_COL) {
    // int SO_START_COL=11
    // int SO_START_COL=21
    int SO_START_ROW=7
      com.k_int.kbplus.SubscriptionImportController.log.debug("processRenewalUpload - opening upload input stream as HSSFWorkbook");
    if ( input_stream ) {
      HSSFWorkbook wb = new HSSFWorkbook(input_stream);
      HSSFSheet firstSheet = wb.getSheetAt(0);

      // Step 1 - Extract institution id, name and shortcode
      HSSFRow org_details_row = firstSheet.getRow(2)
      String org_name = org_details_row?.getCell(1)?.toString()
      String org_id = org_details_row?.getCell(0)?.toString()
      String org_shortcode = org_details_row?.getCell(2)?.toString()
        com.k_int.kbplus.SubscriptionImportController.log.debug("Worksheet upload on behalf of ${org_name}, ${org_id}, ${org_shortcode}");
      Double d = Double.parseDouble(org_id)
      result.subOrg = Org.get(d.intValue())

      if ( result.subOrg == null ) {
        flash.message="Unable to locate Org"
        return
      }

      def sub_info = []
      // Step 2 - Row 5 (6, but 0 based) contains package identifiers starting in column 4(5)
      HSSFRow package_ids_row = firstSheet.getRow(5)
      for (int i=SO_START_COL;((i<package_ids_row.getLastCellNum())&&(package_ids_row.getCell(i)));i++) {
        com.k_int.kbplus.SubscriptionImportController.log.debug("Got package identifier: ${package_ids_row.getCell(i).toString()}");
        def sub_id = package_ids_row.getCell(i).toString()
        def sub_rec = genericOIDService.resolveOID(sub_id) // Subscription.get(sub_id);
        if ( sub_rec ) {
          sub_info.add(sub_rec);
        }
        else  {
          com.k_int.kbplus.SubscriptionImportController.log.error("Unable to resolve the package identifier in row 6 column ${i+5}, please check");
          return
        }
      }

      result.entitlements = []

      boolean processing = true
      // Step three, process each title row, starting at row 11(10)
      for (int i=SO_START_ROW;((i<firstSheet.getLastRowNum())&&(processing)); i++) {
        com.k_int.kbplus.SubscriptionImportController.log.debug("processing row ${i}... SO_START_COL=${SO_START_COL}");

        HSSFRow title_row = firstSheet.getRow(i)
        // Title ID
        def title_id = title_row.getCell(0).toString()
        if ( title_id == 'END' ) {
          com.k_int.kbplus.SubscriptionImportController.log.debug("Encountered END title, entitlements.size=${result.entitlements.size()}");
          processing = false;
        }
        else {
          com.k_int.kbplus.SubscriptionImportController.log.debug("Upload Process title: ${title_id}, num subs=${sub_info.size()}, last cell=${title_row.getLastCellNum()}");
          def title_id_long = Long.parseLong(title_id)
          def title_rec = TitleInstance.get(title_id_long);
          for ( int j=0; ( ((j+SO_START_COL)<title_row.getLastCellNum()) && (j<=sub_info.size() ) ); j++ ) {
            def resp_cell = title_row.getCell(j+SO_START_COL)
            if ( resp_cell ) {
              com.k_int.kbplus.SubscriptionImportController.log.debug("  -> Testing col[${j+SO_START_COL}(${j}+${SO_START_COL})] val=${resp_cell.toString()}");

              def subscribe=resp_cell.toString()

                com.k_int.kbplus.SubscriptionImportController.log.debug("Entry : sub:${subscribe}");
                
              if ( subscribe == 'Y' || subscribe == 'y' ) {
                com.k_int.kbplus.SubscriptionImportController.log.debug("Add an issue entitlement from subscription[${j}] for title ${title_id_long}");

                def entitlement_info = [:]
                entitlement_info.base_entitlement = extractEntitlement(sub_info[j], title_id_long)
                if ( entitlement_info.base_entitlement ) {
                  entitlement_info.title_id = title_id_long
                  entitlement_info.subscribe = subscribe

                  entitlement_info.start_date = title_row.getCell(4)
                  entitlement_info.end_date = title_row.getCell(5)
                  entitlement_info.coverage = title_row.getCell(6)
                  entitlement_info.coverage_note = title_row.getCell(7)
                  entitlement_info.core_status = title_row.getCell(10) // moved from 8

                    com.k_int.kbplus.SubscriptionImportController.log.debug("Added entitlement_info ${entitlement_info}");
                  result.entitlements.add(entitlement_info)
                }
                else {
                  com.k_int.kbplus.SubscriptionImportController.log.error("TIPP not found in package.");
                  flash.error="You have selected an invalid title/package combination for title ${title_id_long}";
                }
              }
              else {
                com.k_int.kbplus.SubscriptionImportController.log.debug("Subscribe=${subscribe} for row ${i} col ${j+SO_START_COL}");
              }
            }
          }
        }
      }
    }
    else {
      com.k_int.kbplus.SubscriptionImportController.log.error("Input stream is null");
    }
      com.k_int.kbplus.SubscriptionImportController.log.debug("Done");

    result
  }

  private def extractEntitlement(pkg, title_id) {
    def result = pkg.tipps.find { e -> e.title?.id == title_id }
    if ( result == null ) {
      com.k_int.kbplus.SubscriptionImportController.log.error("Failed to look up title ${title_id} in package ${pkg.name}");
    }
    result
  }

  @Secured(['ROLE_USER'])
  def processSubscriptionImport() {

    com.k_int.kbplus.SubscriptionImportController.log.debug("processSubscriptionImport...");

    Map<String, Object> result = [:]

    result.user = User.get(springSecurityService.principal.id)
    result.institution = Org.get(params.orgId)

    if ( result.institution == null )
      return

    // if (! permissionHelperService.checkUserIsMember(result.user, result.institution) ) {
    //   flash.error="You do not have permission to view ${result.institution.name}. Please request access on the profile page";
    //   response.sendError(401)
      // render(status: '401', text:"You do not have permission to access ${result.institution.name}. Please request access on the profile page");
    //   return;
    // }

    com.k_int.kbplus.SubscriptionImportController.log.debug("-> renewalsUpload params: ${params}");

      com.k_int.kbplus.SubscriptionImportController.log.debug("entitlements...[${params.ecount}]");

    if ( params.ecount != null ) {
      int ent_count = Integer.parseInt(params.ecount);
  
      def new_subscription = new Subscription(
                                   identifier: java.util.UUID.randomUUID().toString(),
                                   status: RefdataValue.getByValueAndCategory('Current', RDConstants.SUBSCRIPTION_STATUS),
                                   impId: java.util.UUID.randomUUID().toString(),
                                   name: "Unset: Generated by import",
                                   // startDate: db_sub.startDate,
                                   // endDate: db_sub.endDate,
                                   // instanceOf: db_sub,
                                   type: RefdataValue.getByValue('Subscription Taken') )
  
      def packages_referenced = []
      Date earliest_start_date =  null
      Date latest_end_date = null
  
      if ( new_subscription.save() ) {
        // assert an org-role
        def org_link = new OrgRole(org:result.institution,
                                   sub: new_subscription,
                                   roleType: RDStore.OR_SUBSCRIBER
        ).save();
  
        // Copy any links from SO
        // db_sub.orgRelations.each { or ->
        //   if ( or.roleType?.value != 'Subscriber' ) {
        //     def new_or = new OrgRole(org: or.org, sub: new_subscription, roleType: or.roleType).save();
        //   }
        // }
      }
      else {
        com.k_int.kbplus.SubscriptionImportController.log.error("Problem saving new subscription, ${new_subscription.errors}");
      }
  
      new_subscription.save(flush:true);
  
  
      if ( !new_subscription.issueEntitlements ) {
        new_subscription.issueEntitlements = new java.util.TreeSet()
      }
  
      for ( int i=0; i<=ent_count; i++ ) {
        def entitlement = params.entitlements."${i}";
          com.k_int.kbplus.SubscriptionImportController.log.debug("process entitlement[${i}]: ${entitlement} - TIPP id is ${entitlement.tipp_id}");
  
        def dbtipp = TitleInstancePackagePlatform.get(entitlement.tipp_id)
  
        if ( ! packages_referenced.contains(dbtipp.pkg) ) {
          packages_referenced.add(dbtipp.pkg)
          def new_package_link = new SubscriptionPackage(subscription:new_subscription, pkg:dbtipp.pkg).save();
          if ( ( earliest_start_date == null ) || ( dbtipp.pkg.startDate < earliest_start_date ) )
            earliest_start_date = dbtipp.pkg.startDate
          if ( ( latest_end_date == null ) || ( dbtipp.pkg.endDate > latest_end_date ) )
            latest_end_date = dbtipp.pkg.endDate
        }
  
        if ( dbtipp ) {
          RefdataValue live_issue_entitlement = RDStore.TIPP_STATUS_CURRENT
          boolean is_core = false

          RefdataValue new_core_status
  
          switch ( entitlement.core_status?.toUpperCase() ) {
            case 'Y':
            case 'YES':
              new_core_status = RefdataValue.getByValueAndCategory('Yes', RDConstants.CORE_STATUS)
              is_core = true;
              break;
            case 'P':
            case 'PRINT':
              new_core_status = RefdataValue.getByValueAndCategory('Print', RDConstants.CORE_STATUS)
              is_core = true;
              break;
            case 'E':
            case 'ELECTRONIC':
              new_core_status = RefdataValue.getByValueAndCategory('Electronic', RDConstants.CORE_STATUS)
              is_core = true;
              break;
            case 'P+E':
            case 'E+P':
            case 'PRINT+ELECTRONIC':
            case 'ELECTRONIC+PRINT':
              new_core_status = RefdataValue.getByValueAndCategory('Print+Electronic', RDConstants.CORE_STATUS)
              is_core = true;
              break;
            default:
              new_core_status = RefdataValue.getByValueAndCategory('No', RDConstants.CORE_STATUS)
              break;
          }
  
          def new_start_date = entitlement.start_date ? parseDate(entitlement.start_date, possible_date_formats)  : null
          def new_end_date = entitlement.end_date ?  parseDate(entitlement.end_date, possible_date_formats) : null
  
          // entitlement.is_core
          def new_ie =  new IssueEntitlement(subscription:new_subscription,
                                             status: live_issue_entitlement,
                                             tipp: dbtipp,
                                             startDate:new_start_date ?: dbtipp.startDate,
                                             startVolume:dbtipp.startVolume,
                                             startIssue:dbtipp.startIssue,
                                             endDate:new_end_date ?: dbtipp.endDate,
                                             endVolume:dbtipp.endVolume,
                                             endIssue:dbtipp.endIssue,
                                             embargo:dbtipp.embargo,
                                             coverageDepth:dbtipp.coverageDepth,
                                             coverageNote:dbtipp.coverageNote,
                                             coreStatus:new_core_status
                                             )
  
          if ( new_ie.save() ) {
            com.k_int.kbplus.SubscriptionImportController.log.debug("new ie saved");
          }
          else {
            new_ie.errors.each{ e ->
                com.k_int.kbplus.SubscriptionImportController.log.error("Problem saving new ie : ${e}");
            }
          }
        }
        else {
          com.k_int.kbplus.SubscriptionImportController.log.debug("Unable to locate tipp with id ${entitlement.tipp_id}");
        }
      }
        com.k_int.kbplus.SubscriptionImportController.log.debug("done entitlements...");
  
      new_subscription.startDate = earliest_start_date
      new_subscription.endDate = latest_end_date
      new_subscription.save()
  
      if ( new_subscription )
        redirect controller:'subscription', action:'index', id:new_subscription.id
      else
        redirect controller:'home', action:'index'
    }
    else {
      flash.message="Problem loading subscription - check form submission length";
      redirect controller:'home', action:'index'
    }
  }

  private def parseDate(datestr, possible_formats) {
    def parsed_date = null;
    if ( datestr && ( datestr.toString().trim().length() > 0 ) ) {
      for(Iterator i = possible_formats.iterator(); ( i.hasNext() && ( parsed_date == null ) ); ) {
        try {
          parsed_date = i.next().parse(datestr.toString());
        }
        catch ( Exception e ) {
        }
      }
    }
    parsed_date
  }

}

