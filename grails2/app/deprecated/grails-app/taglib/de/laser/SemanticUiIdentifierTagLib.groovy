package de.laser

class SemanticUiIdentifierTagLib {
    //static defaultEncodeAs = [taglib:'html']
    //static encodeAsForTags = [tagName: [taglib:'html'], otherTagName: [taglib:'none']]

    static namespace = "semui"

    // <laser:formAddIdentifier owner="${objInstance}" buttonText="some text" placeholderText="some text" class="someCssClass" checkUnique="yes" />

    def formAddIdentifier = { attrs, body ->
        def formUrl = g.createLink(controller:'ajax', action:'addToCollection')
        def context = "${(attrs.owner).class.name}:${(attrs.owner).id}"
        def recip   = Identifier.getAttributeName(attrs.owner)
        def onlyNameSpace = attrs.onlyoneNamespace

        def cssClass   = attrs.class ? " ${attrs.class}" : ""
        def buttonText = attrs.buttonText ? attrs.buttonText : message(code:'identifier.select.add')

        def random               = (new Random()).nextInt(100000)
        String formSelector      = "add-identifier-form-" + random
        String namespaceSelector = "add-identifier-input-" + random
        String identSelector     = "add-identifier-select-" + random

        out << '<form id="' + formSelector + '" class="ui form' + cssClass + '" action="' + formUrl +'" method="post">'
        out <<   body()

        out <<   '<input type="hidden" name="__newObjectClass" value="' + Identifier.class.name + '" />'
        out <<   '<input type="hidden" name="__context" value="' + context + '" />'
        out <<   '<input type="hidden" name="__recip" value="' + recip + '" />'

        out <<   '<div class="fields">'
        out <<     '<div class="field">'
        out <<       '<label></label>'
        out <<       '<select name="namespace" id="' + namespaceSelector + '" class="ui search dropdown" />'

        if(onlyNameSpace)
        {
            IdentifierNamespace.where{ns == onlyNameSpace}
                    .list(sort:'ns')
                    .sort { a,b -> a.ns.compareToIgnoreCase b.ns }
                    .each{ ns ->
                out <<     '<option value="' + ns.ns + '">' + ns.ns + '</option>'
                }

        }else{
        IdentifierNamespace.where{(nsType == attrs.owner.class.name || nsType == null)}
                .list(sort:'ns')
                .sort { a,b -> a.ns.compareToIgnoreCase b.ns }
                .each{ ns ->
            out <<     '<option value="' + ns.ns + '">' + ns.ns + '</option>'
             }
        }

        out <<       '</select>'
        out <<     '</div>'

        out <<     '<div class="field">'
        out <<       '<label></label>'
        out <<       '<input type="hidden" name="identifier" id="' + identSelector + '"/>'
        out <<     '</div>'

        out <<     '<div class="field">'
        out <<       '<label></label>'
        out <<       '<input type="submit" value="' + buttonText + '" class="ui button" />'
        out <<     '</div>'

        out <<   '</div>'

        out <<   '<script>'
        out <<     getJS1(namespaceSelector, identSelector, attrs)
        out <<     getJS2(formSelector, identSelector, attrs)
        out <<   '</script>'
        out << '</form>'
    }

    private getJS1(namespaceSelector, identSelector, attrs) {
        def lookupUrl = g.createLink(controller:'ajax', action:'lookup2')

        def phText = attrs.placeholderText ? attrs.placeholderText : message(code:'identifier.select.add')

        return """
        \$(function(){
            \$("#${identSelector}").select2({
                placeholder: "${phText}",
                minimumInputLength: 1,
                formatInputTooShort: function () {
                    return "${message(code:'select2.minChars.note')}"
                },
                ajax: { // select2's convenient helper
                    url: "${lookupUrl}",
                    dataType: 'json',
                    data: function (term, page) {
                        term = \$("#${namespaceSelector}").val() + ':' + term
                        return {
                            q: term,
                            page_limit: 10,
                            baseClass:'${Identifier.class.name}'
                        };
                    },
                    results: function (data, page) {
                        return {results: data.values};
                    }
                },
                createSearchChoice:function(term, data) {
                    return {id:'${Identifier.class.name}:__new__:' + \$("#${namespaceSelector}").val() + ':' + term, text:term};
                }
            });
        });
        """
    }

    private getJS2(formSelector, identSelector, attrs) {
        def ajaxUrl = g.createLink(controller:'ajax', action:'validateIdentifierUniqueness')
        def context = "${(attrs.owner).class.name}:${(attrs.owner).id}"

        def warningText = attrs.uniqueWarningText ? attrs.uniqueWarningText : "Duplicates found"

        if ("yes" == attrs.uniqueCheck) {
            return """
            \$("#${formSelector}").submit(function(event) {
                event.preventDefault();
                \$.ajax({
                    url: "${ajaxUrl}?identifier=" + \$("#${identSelector}").val() + "&owner=${context}",
                    success: function(data) {
                        if (data.unique) {
                            \$("#${formSelector}").unbind("submit").submit();
                        }
                        else if(data.duplicates) {
                            var warning = "${warningText}:\\n";
                            for(var dd of data.duplicates){
                                warning += "- " + dd.id + ":" + (dd.title ? dd.title : dd.name) + "\\n";
                            }
                            var accept = confirm(warning);
                            if (accept){
                                \$("#${formSelector}").unbind("submit").submit();
                            }
                        }
                    },
                });
            });
            """
        }
    }
}
