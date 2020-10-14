package de.laser


import de.laser.base.AbstractI10n
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.springframework.context.i18n.LocaleContextHolder

class RefdataCategory extends AbstractI10n {

    static Log static_logger = LogFactory.getLog(RefdataCategory)

    String desc
    String desc_de
    String desc_en

    // indicates this object is created via current bootstrap
    boolean isHardData = false

    Date dateCreated
    Date lastUpdated

    static mapping = {
            cache   true
              id column: 'rdc_id'
         version column: 'rdc_version'
            desc column: 'rdc_description', index:'rdc_description_idx'
         desc_de column: 'rdc_description_de', index:'rdc_description_de_idx'
         desc_en column: 'rdc_description_en', index:'rdc_description_en_idx'
        isHardData column: 'rdc_is_hard_data'
        dateCreated column: 'rdc_date_created'
        lastUpdated column: 'rdc_last_updated'
    }

    static constraints = {
        // Nullable is true, because values are already in the database
        desc_de (nullable: true, blank: false)
        desc_en (nullable: true, blank: false)
        lastUpdated (nullable: true)
        dateCreated (nullable: true)
    }

    static RefdataCategory construct(Map<String, Object> map) {

        withTransaction {
            String token = map.get('token')
            boolean hardData = new Boolean(map.get('hardData'))
            Map i10n = map.get('i10n')

            RefdataCategory rdc = RefdataCategory.findByDescIlike(token) // todo: case sensitive token

            if (!rdc) {
                static_logger.debug("INFO: no match found; creating new refdata category for ( ${token}, ${i10n} )")
                rdc = new RefdataCategory(desc: token) // todo: token
            }

            rdc.desc_de = i10n.get('desc_de') ?: null
            rdc.desc_en = i10n.get('desc_en') ?: null

            rdc.isHardData = hardData
            rdc.save()

            rdc
        }
    }

  static def refdataFind(GrailsParameterMap params) {
      List<Map<String, Object>> result = []
      List<RefdataCategory> matches = []

      if(! params.q) {
          matches = RefdataCategory.findAll()
      }
      else {
          switch (I10nTranslation.decodeLocale(LocaleContextHolder.getLocale())) {
              case 'en':
                  matches = RefdataCategory.findAllByDesc_enIlike("%${params.q}%")
                  break
              case 'de':
                  matches = RefdataCategory.findAllByDesc_deIlike("%${params.q}%")
                  break
          }
      }

      matches.each { it ->
          result.add([id: "${it.id}", text: "${it.getI10n('desc')}"])
      }
      result
  }

    static RefdataCategory getByDesc(String desc) {
        RefdataCategory.findByDescIlike(desc)
    }

  /**
   * Returns a list containing category depending refdata_values.
   * 
   * @param category_name
   * @return ArrayList
   */
  static List<RefdataValue> getAllRefdataValues(String category_name) {
      if (! category_name) {
          return []
      }
      String i10nAttr = LocaleContextHolder.getLocale().getLanguage() == Locale.GERMAN.getLanguage() ? 'value_de' : 'value_en'
      String query = "select rdv from RefdataValue as rdv, RefdataCategory as rdc where rdv.owner = rdc and lower(rdc.desc) = :category order by rdv.${i10nAttr}"

      RefdataValue.executeQuery( query, [category: category_name.toLowerCase()] )
  }
  static List<RefdataValue> getAllRefdataValues(List category_names) {
      if (! category_names) {
          return []
      }
      String i10nAttr = LocaleContextHolder.getLocale().getLanguage() == Locale.GERMAN.getLanguage() ? 'value_de' : 'value_en'
      String query = "select rdv from RefdataValue as rdv, RefdataCategory as rdc where rdv.owner = rdc and lower(rdc.desc) in (:categories) order by rdv.${i10nAttr}"

      RefdataValue.executeQuery( query, [categories: category_names.collect{it.toLowerCase()}] )
  }
    static List<RefdataValue> getAllRefdataValuesWithOrder(String category_name) {
        if (! category_name) {
            return []
        }
        String query = "select rdv from RefdataValue as rdv, RefdataCategory as rdc where rdv.owner = rdc and lower(rdc.desc) = :category order by rdv.order asc"

        RefdataValue.executeQuery( query, [category: category_name.toLowerCase()] )
    }

    static getAllRefdataValuesWithI10nExplanation(String category_name, Map sort) {
        List<RefdataValue> refdatas = RefdataValue.findAllByOwner(RefdataCategory.findByDescIlike(category_name), sort)

        List result = []
        refdatas.each { rd ->
            result.add(id:rd.id, value:rd.getI10n('value'), expl:rd.getI10n('expl'))
        }
        result
    }
}
