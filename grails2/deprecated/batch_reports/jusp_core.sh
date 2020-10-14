#!/bin/bash
mysql -u k-int -pk-int KBPlus -e "\. jusp_core.sql" > jusp_core_report.tsv
sed '1,7d' < jusp_core_report.tsv > wip
echo jusp_institution,jusp_title_id,year,tipp_id
while read rdv_value core_start_year core_end_year jusp_title_id jusp_inst_id tipp
do
  # echo $core_start_year $core_end_year $jusp_title_id $jusp_inst_id
  for (( y=core_start_year; y<=core_end_year; y++ ))
  do
    echo "$jusp_inst_id,$jusp_title_id,$y,$tipp"
  done
done < wip

