package com.k_int.kbplus

import de.laser.helper.DateUtil
import grails.transaction.Transactional
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder
import org.elasticsearch.search.aggregations.bucket.composite.CompositeValuesSourceBuilder
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.FieldSortBuilder
import org.elasticsearch.search.sort.SortOrder

@Transactional
class ESSearchService{
// Map the parameter names we use in the webapp with the ES fields
  def reversemap = ['rectype':'rectype',
                    'endYear':'endYear',
                    'startYear':'startYear',
                    'consortiaName':'consortiaName',
                    'providerName':'providerName',
                    'availableToOrgs':'availableToOrgs',
                    'isPublic':'isPublic',
                    'status':'status',
                    'publisher':'publisher',
                    'name':'name']

  def ESWrapperService

  def search(params){
    search(params,reversemap)
  }

  def search(params, field_map){
    // log.debug("Search Index, params.coursetitle=${params.coursetitle}, params.coursedescription=${params.coursedescription}, params.freetext=${params.freetext}")
    log.debug("ESSearchService::search - ${params}")

   Map<String, Object> result = [:]

   //List client = getClient()
   RestHighLevelClient esclient = ESWrapperService.getClient()
   Map<String, String> esSettings =  ESWrapperService.getESSettings()

    try {
      if(ESWrapperService.testConnection()) {
        if ((params.q && params.q.length() > 0) || params.rectype) {

          params.max = Math.min(params.max ? params.int('max') : 15, 10000)
          params.offset = params.offset ? params.int('offset') : 0

          String query_str = buildQuery(params, field_map)
          if (params.tempFQ) //add filtered query
          {
            query_str = query_str + " AND ( " + params.tempFQ + " ) "
            params.remove("tempFQ") //remove from GSP access
          }

          //log.debug("index:${esSettings.indexName} query: ${query_str}")
          //def search
          SearchResponse searchResponse
          try {

            SearchRequest searchRequest = new SearchRequest(esSettings.indexName)
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()


            //SearchRequestBuilder searchRequestBuilder  = esclient.prepareSearch(esSettings.indexName)
            if (params.sort) {
              SortOrder order = SortOrder.ASC
              if (params.order) {
                order = SortOrder.valueOf(params.order?.toUpperCase())
              }

              //searchRequestBuilder = searchRequestBuilder.addSort("${params.sort}".toString()+".keyword", order)
              searchSourceBuilder.sort(new FieldSortBuilder("${params.sort}").order(order))
            }

            //searchRequestBuilder = searchRequestBuilder.addSort("priority", SortOrder.DESC)
            searchSourceBuilder.sort(new FieldSortBuilder("priority").order(SortOrder.DESC))

            log.debug("index: ${esSettings.indexName} -> searchRequestBuilder: ${query_str}")

            if (params.actionName == 'index') {

              NestedAggregationBuilder nestedAggregationBuilder = new NestedAggregationBuilder('status', 'status')

              searchSourceBuilder.query(QueryBuilders.queryStringQuery(query_str))
              searchSourceBuilder.aggregation(AggregationBuilders.terms('rectype').size(25).field('rectype.keyword'))
              searchSourceBuilder.aggregation(AggregationBuilders.terms('providerName').size(50).field('providerName.keyword'))
              searchSourceBuilder.aggregation(nestedAggregationBuilder.subAggregation(AggregationBuilders.terms('status').size(50).field('status.value')))
              searchSourceBuilder.aggregation(AggregationBuilders.terms('startYear').size(50).field('startYear.keyword'))
              searchSourceBuilder.aggregation(AggregationBuilders.terms('endYear').size(50).field('endYear.keyword'))
              searchSourceBuilder.aggregation(AggregationBuilders.terms('consortiaName').size(50).field('consortiaName.keyword'))

              searchSourceBuilder.from(params.offset)
              searchSourceBuilder.size(params.max)
              searchRequest.source(searchSourceBuilder)
            } else {

              searchSourceBuilder.query(QueryBuilders.queryStringQuery(query_str))
              searchSourceBuilder.from(params.offset)
              searchSourceBuilder.size(params.max)
              searchRequest.source(searchSourceBuilder)

            }
            searchResponse = esclient.search(searchRequest, RequestOptions.DEFAULT)

            //search = searchRequestBuilder.get()
          }
          catch (Exception ex) {
            log.error("Error processing ${esSettings.indexName} ${query_str}", ex)
          }

          if (searchResponse) {

            if (searchResponse.getAggregations()) {
              result.facets = [:]
              searchResponse.getAggregations().each { entry ->
                def facet_values = []
                //log.debug("Entry: ${entry.type}")

                if(entry.type == 'nested'){
                  entry.getAggregations().each { subEntry ->
                    //log.debug("metaData: ${subEntry.name}")
                    subEntry.buckets.each { bucket ->
                      //log.debug("Bucket: ${bucket}")
                      bucket.each { bi ->
                        //log.debug("Bucket item: ${bi} ${bi.getKey()} ${bi.getDocCount()}")
                        facet_values.add([term: bi.getKey(), display: bi.getKey(), count: bi.getDocCount()])
                      }
                    }
                  }
                }else {
                  entry.buckets.each { bucket ->
                    //log.debug("Bucket: ${bucket}")
                    bucket.each { bi ->
                      //log.debug("Bucket item: ${bi} ${bi.getKey()} ${bi.getDocCount()}")
                      facet_values.add([term: bi.getKey(), display: bi.getKey(), count: bi.getDocCount()])
                    }
                  }
                }
                result.facets[entry.getName()] = facet_values

              }
            }

            result.hits = searchResponse.getHits()
            result.resultsTotal = searchResponse.getHits().getTotalHits().value ?: "0"
            result.index = esSettings.indexName

          }

        } else {
          log.debug("No query.. Show search page")
        }
      }
    }
    finally {
      try {
        esclient.close()
      }
      catch ( Exception e ) {
        log.error("Problem by Close ES Client",e)
      }
    }
    result
  }

  String buildQuery(params,field_map) {
    //log.debug("BuildQuery... with params ${params}. ReverseMap: ${field_map}")

    StringWriter sw = new StringWriter()

    if ( params.q != null ){
      params.query = "${params.query}"
      //GOKBID, GUUID
      if(params.q.length() >= 37){
        if(params.q.contains(":") || params.q.contains("-")){
          //params.q = params.q.replaceAll('\\*', '')
          sw.write("\"${params.q}\"")
        }else {
          sw.write("${params.q}")
          sw.write(" AND ((NOT gokbId:'${params.q}') AND (NOT guid:'${params.q}')) ")
        }
      }else {
        if(params.q.contains(":") || params.q.contains("-")) {
          //params.q = params.q.replaceAll('\\*', '')
          sw.write("\"${params.q}\"")
        }else if (params.q.count("\"") >= 2){
          sw.write("${params.q}")
          sw.write(" AND ((NOT gokbId:'${params.q}') AND (NOT guid:'${params.q}')) ")
        }else{

          if(DateUtil.isDate(params.q)){
            params.q = DateUtil.parseDateGeneric(params.q).format("yyyy-MM-dd").toString()
          }

          params.q = params.q.replaceAll('\\"', '')
          sw.write("${params.q}")
          sw.write(" AND ((NOT gokbId:'${params.q}') AND (NOT guid:'${params.q}')) ")
        }
      }
    }
      
    /*if(params.rectype){
      if(sw.toString()) sw.write(" AND ")
      sw.write(" rectype:'${params.rectype}' ")
    }*/

      field_map.each { mapping ->

      if ( params[mapping.key] != null ) {
        if ( params[mapping.key].class == java.util.ArrayList) {
          if(sw.toString()) sw.write(" AND ")
          sw.write(" ( (( NOT rectype:\"Subscription\" ) AND ( NOT rectype:\"License\" ) " +
                  "AND ( NOT rectype:\"SurveyOrg\" ) AND ( NOT rectype:\"SurveyConfig\" ) " +
                  "AND ( NOT rectype:\"Task\" ) AND ( NOT rectype:\"Note\" ) AND ( NOT rectype:\"Document\" ) " +
                  "AND ( NOT rectype:\"IssueEntitlement\" ) " +
                  "AND ( NOT rectype:\"SubscriptionProperty\" ) " +
                  "AND ( NOT rectype:\"LicenseProperty\" ) " +
                  ") ")

          params[mapping.key].each { p ->
            if(p == params[mapping.key].first())
            {
              sw.write(" OR ( ")
            }
            sw.write(" ( ")
            sw.write(mapping.value)
            sw.write(":")
            sw.write("\"${p}\"")

            if(mapping.value == 'availableToOrgs')
            {
              if(params.consortiaGUID){
                if(sw.toString()) sw.write(" OR ")

                sw.write(" consortiaGUID:\"${params.consortiaGUID}\"")
              }
            }
            sw.write(" ) ")
            if(p == params[mapping.key].last()) {
              sw.write(" ) ")
            }else{
              sw.write(" OR ")
            }

          }

          sw.write(" ) ")
        }
        else {
          // Only add the param if it's length is > 0 or we end up with really ugly URLs
          // II : Changed to only do this if the value is NOT an *

          log.debug("Processing ${params[mapping.key]} ${mapping.key}")

          try {
            if ( params[mapping.key] ) {
                if (params[mapping.key].length() > 0 && !(params[mapping.key].equalsIgnoreCase('*'))) {
                  if (sw.toString()) sw.write(" AND ")
                  sw.write(mapping.value)
                  sw.write(":")
                  if (params[mapping.key].startsWith("[") && params[mapping.key].endsWith("]")) {
                    sw.write("${params[mapping.key]}")
                  } else if (params[mapping.key].count("\"") >= 2) {
                    sw.write("${params[mapping.key]}")
                  } else {
                    sw.write("( ${params[mapping.key]} )")
                  }
                }
            }
          }
          catch ( Exception e ) {
            log.error("Problem procesing mapping, key is ${mapping.key} value is ${params[mapping.key]}",e)
          }
        }
      }
    }

    if(params.searchObjects && params.searchObjects != 'allObjects'){
      if(sw.toString()) sw.write(" AND ")

        sw.write(" visible:'Private' ")
    }

    if(!params.showDeleted)
    {
      sw.write(  " AND ( NOT status:\"Deleted\" )")
    }

    if(params.showAllTitles) {
      sw.write(  " AND ((rectype: \"EBookInstance\") OR (rectype: \"JournalInstance\") OR (rectype: \"BookInstance\") OR (rectype: \"TitleInstance\") OR (rectype: \"DatabaseInstance\")) ")
    }

    sw.toString()
  }
}
