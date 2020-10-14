package com.k_int.kbplus.onixpl

import com.k_int.xml.XMLDoc
import groovy.util.slurpersupport.GPathResult

import javax.xml.xpath.XPathConstants

/**
 * @author Steve Osguthorpe <steve.osguthorpe@k-int.com>
 *
 * Helper service for ONIX-PL data extraction.
 */
class OnixPLHelperService {
  def grailsApplication
  
  static scope = "singleton"
  private XMLDoc codeList
  
  public XMLDoc getCodeList() {
    codeList = codeList ?: new XMLDoc(
      grailsApplication.mainContext.getResource(
        "/WEB-INF/resources/${grailsApplication.config.onix.codelist}"
      )
    )
  }
  
  public String lookupCodeTypeAnnotation (String type) {
    getCodeList().XPath("/xs:schema/xs:simpleType[@name='${type}']/xs:annotation", XPathConstants.STRING)
  }
  
  public String lookupCodeValueAnnotation (String value) {
    def result = getCodeList().XPath("/xs:schema//*[@value='${value}']/xs:annotation", XPathConstants.STRING)
    result
  }
  public Object duplicateDefinitionText(org.w3c.dom.Node node,xml) {
      //If Node child of Definitions, go one level up and copy annotation and licensetext/or just all but current
      if(node.getParentNode()?.getParentNode()?.getNodeName() != "Definitions"){
        return node
      }

      def parent_node = node.getParentNode()
      parent_node.getChildNodes().each{
        if(it.getNodeName()=="Annotation" || it.getNodeName() == "LicenseTextLink"){
          def clone = it.cloneNode(true)
          node.appendChild(clone)
        }
      }

      return node

  }
  public XMLDoc replaceAllTextElements (XMLDoc doc, XMLDoc snippet) {
    
    // Get the Gpath representation.
    Map<String, GPathResult> text_elements = [:]
    
    // Create a map of all the TextElements
    doc.toGPath().'**'.each { 
      if (it.name() == 'TextElement') {
        text_elements["${it.'@id'}"] = it
      }
    }
    
    // Now get the GPath of the snippet we are replacing into.
    def gpath = snippet.toGPath()
    
    // Replace all the LicenseTextLink items with the linked text.
    gpath.'**'.findAll { it ->
      
      if (it.name() == 'LicenseTextLink' ) {
        
        // Use the id of the current element.
        // Get the ID
        def the_ref = it.'@href'
        
        // Replace.
        it.replaceNode {
          
          def node = text_elements["${the_ref}"]?.children()
          if (node) {
            return "TextElement" (node)
          }
        }
      }
    }
    
    // Because the XMLSlurper is lazy in modifications we need to serialize and re-parse.
    new XMLDoc( new ByteArrayInputStream (groovy.xml.XmlUtil.serialize(gpath).getBytes("UTF-8")) )
  }
}
