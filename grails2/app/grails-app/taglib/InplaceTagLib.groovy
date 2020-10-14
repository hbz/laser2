class InplaceTagLib {

  def inPlaceEdit = { attrs, body ->
    String data_link = createLink(controller:'ajax', action: 'editableSetValue')
    out << "<span id=\"${attrs.domain}:${attrs.pk}:${attrs.field}:${attrs.id}\" class=\"xEditableValue ${attrs.class?:''}\" data-type=\"textarea\" data-pk=\"${attrs.domain}:${attrs.pk}\" data-name=\"${attrs.field}\" data-url=\"${data_link}\">"
    if ( body ) {
      out << body()
    }
    out << "</span>"
  }

  /**
   * simpleReferenceTypedown - create a hidden input control that has the value fully.qualified.class:primary_key and which is editable with the
   * user typing into the box. Takes advantage of refdataFind and refdataCreate methods on the domain class.
   */ 
  def simpleReferenceTypedown = { attrs, body ->
    out << "<input type=\"hidden\" name=\"${attrs.name}\" data-domain=\"${attrs.baseClass}\" ${attrs.disabled ?  'disabled=\"true\"' : ''} "
    if ( attrs.id ) {
      out << "id=\"${attrs.id}\" "
    }
    if ( attrs.style ) {
      out << "style=\"${attrs.style}\" "
    }

    attrs.each { att ->
      if ( att.key.startsWith("data-") ) {
        out << "${att.key}=\"${att.value}\" "
      }
    }

    out << "class=\"${attrs.modified? 'modifiedReferenceTypedown':'simpleReferenceTypedown' } ${attrs?.class}\" />"
  }


  def simpleHiddenRefdata = { attrs, body ->
    def default_empty = message(code:'default.button.edit.label')
    def emptyText = attrs?.emptytext ? " data-emptytext=\"${attrs.emptytext}\"" : " data-emptytext=\"${default_empty}\""
    def data_link = createLink(controller:'ajax', action: 'sel2RefdataSearch', params:[id:attrs.refdataCategory,format:'json'])
    // out << "<input type=\"hidden\" id=\"${attrs.id}\" name=\"${attrs.name}\" value=\"${params[attrs.name]}\"/>"
    out << "<input type=\"hidden\" id=\"${attrs.id}\" name=\"${attrs.name}\" />"
    out << "<a href=\"#\" class=\"simpleHiddenRefdata\" data-type=\"select\" data-source=\"${data_link}\" data-hidden-id=\"${attrs.name}\" ${emptyText} >"
    out << body()
    out << "</a>";
  }
}
