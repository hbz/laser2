package com.k_int.kbplus

import de.laser.RefdataValue
import de.laser.helper.RefdataAnnotation

class Transforms {
	
	static belongsTo = [
		transformer: Transformer // make sure that transforms can't be created without allocated transformer
	]
	
	String name
	String path_to_stylesheet
	String return_file_extention
	String return_mime

	Date dateCreated
	Date lastUpdated
	
//	RefdataValue[] accepts_type // subscription, license
	@RefdataAnnotation(cat = '?')
	RefdataValue accepts_format // json, xml, url
	
	static hasMany = [ accepts_types: RefdataValue ]
	
	static mapping = {
		table 'transforms'
	    id column: 'tr_id', generator: 'increment'
		name column: 'tr_name'
//		accepts_type column: 'tr_accepts_type_rv_fk'
		accepts_format column: 'tr_accepts_format_rv_fk'
		return_file_extention column: 'tr_return_file_extention'
		return_mime column: 'tr_return_mime'
		transformer column: 'tr_transformer_fk' , index:'tr_transformer_id_idxfk'
		path_to_stylesheet column: 'tr_path_to_stylesheet'

		dateCreated column: 'tr_date_created'
		lastUpdated column: 'tr_last_updated'
	}
	
    static constraints = {
		//id(nullable:false, unique: true, blank:false)
		name(nullable:false, blank:false)
//		accepts_type(nullable:false, blank:false)
		accepts_format(nullable:false, blank:false)
		return_file_extention(nullable:false, blank:false)
		return_mime(nullable:false, blank:false)
		path_to_stylesheet(nullable:true, blank:true)
		transformer(nullable:false, blank:false)

		// Nullable is true, because values are already in the database
		lastUpdated (nullable: true, blank: false)
		dateCreated (nullable: true, blank: false)
	}
	
	String displayTypes(){
		String typesStr = ""
		accepts_types.eachWithIndex{ ref, index ->
			if(index > 0) typesStr += ","
			typesStr += ref.value
		}
		return typesStr
	}
	
	boolean hasType(String type){
		boolean found = false
		log.debug("type: ${type}")
		for ( ref in accepts_types ){
			log.debug("ref.value: ${ref.value}")
			if(ref.value.equals(type)){
				log.debug("found")
				found = true; break;
			}
		}
		return found
	}
}
