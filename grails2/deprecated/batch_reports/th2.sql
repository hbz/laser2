SET @issn_type_id = null, @eissn_type_id=null, @doi_type_id=null, @e_issn2_type_id = null;

select @issn_type_id:=idns_id from identifier_namespace where idns_ns='ISSN';
select @eissn_type_id:=idns_id from identifier_namespace where idns_ns='eISSN';
select @doi_type_id:=idns_id from identifier_namespace where idns_ns='DOI';
select @e_issn2_type_id:=idns_id from identifier_namespace where idns_ns='e-issn';


select title.ti_id,
       max(issn_identifier.id_value) ISBN,
       max(eissn_identifier.id_value) EISSN,
       max(doi_identifier.id_value) DOI,
       max(eissn_2_identifier.id_value) EISSN2,
       tipp.tipp_coverage_note
from title_instance_package_platform as tipp
     join title_instance as title on title.ti_id = tipp.tipp_ti_fk
       left outer join identifier_occurrence as io on io.io_ti_fk = title.ti_id
         left outer join identifier as issn_identifier on io.io_canonical_id = issn_identifier.id_id and issn_identifier.id_ns_fk = @issn_type_id
         left outer join identifier as eissn_identifier on io.io_canonical_id = eissn_identifier.id_id  and eissn_identifier.id_ns_fk = @eiisn_type_id
         left outer join identifier as doi_identifier on io.io_canonical_id =  doi_identifier.id_id and doi_identifier.id_ns_fk = @doi_type_id
         left outer join identifier as eissn_2_identifier on io.io_canonical_id =  eissn_2_identifier.id_id and eissn_2_identifier.id_ns_fk = @e_issn2_type_id
where tipp_coverage_note is not null
      and (
         tipp.tipp_coverage_note like 'Formerly%'
      or tipp.tipp_coverage_note like '%nclude%'
      or tipp.tipp_coverage_note like 'Now%'
      or tipp.tipp_coverage_note like 'now%'
      or tipp.tipp_coverage_note like '%split%'
      or tipp.tipp_coverage_note like 'Merged%'
      or tipp.tipp_coverage_note like 'merged%'
      or tipp.tipp_coverage_note like 'Absorbed%'
      or tipp.tipp_coverage_note like 'Original title of%'
      or tipp.tipp_coverage_note like 'incorporated%' )
group by title.ti_id;
