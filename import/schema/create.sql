#
# Initial attempt at create .sql script for KB+ project.
#
# Adopted the tbl_prefix naming convention Paul Needham mentioned, as it makes it easier to build queries
# without recourse to lots of aliases in join clauses
#
# Changes
# 
# 04-Apr-2012 II  Created
# 11-Apr-2012 II  Added <prefix>_imp_id varchar(36) columns to org,pkg,plat and so tables, to optimise import. Allows guid lookup.
# 11-Apr-2012 II  Reworked - Added extra tables from schema, split FK constraints out to prevent cycles when dropping / creating
# 
#
#
# Outstanding issues
# 1. re: DocContext <- Alert Is the intention really that a doc context can only have 1 alert?
# 2. Mapping of "Target Context" entities - Ive elected disjoint subclass model - sparse FKs to each target.. Other option is generic "Type","Key" but issues
# 3. SID mappings - Changed to Namespace:Identifier (Indexed as Identifier:Namespace)
#

#
# Configure database
#

create database kbplus default charset UTF8 default collate UTF8_BIN;
grant all on kbplus.* to 'k-int'@'localhost';
grant all on kbplus.* to 'k-int'@'localhost.localdomain';
use kbplus;

#
# Disable old constraints
#

alter table combo drop foreign key FK5A7318E69CBC6D5;
alter table combo drop foreign key FK5A7318EF93C805D;
alter table doc drop foreign key FK1853897381E73;
alter table doc drop foreign key FK185387758376B;
alter table doc_context drop foreign key FK30EBA9A8C7230C87;
alter table license drop foreign key FK9F08441F144465;
alter table license drop foreign key FK9F0844168C2A8DD;
alter table license_entitlement drop foreign key FKD202F62F1086E0CA;
alter table license_entitlement drop foreign key FKD202F62F7FEDED6F;
alter table org_role drop foreign key FK4E5C38F1E646D31D;
alter table org_role drop foreign key FK4E5C38F199CCEB1A;
alter table org_role drop foreign key FK4E5C38F1879D5409;
alter table org_role drop foreign key FK4E5C38F14F370323;
alter table org_role drop foreign key FK4E5C38F11960C01E;
alter table package drop foreign key FKCFE534462A381B57;
alter table package drop foreign key FKCFE5344692580D5F;
alter table platform drop foreign key FK6FBD68736DC6881C;
alter table platform drop foreign key FK6FBD6873E3F2DAD4;
alter table refdata_value drop foreign key FKF33A596F18DAEBF6;
alter table subscription drop foreign key FK1456591D7D96D7D2;
alter table subscription drop foreign key FK1456591D6297706B;
alter table subscription drop foreign key FK1456591DE82AEB63;
alter table subscription_package drop foreign key FK5122C72467963563;
alter table subscription_package drop foreign key FK5122C7241B1C4D60;
alter table title_instance drop foreign key FKACC69C334E5D16;
alter table title_instance drop foreign key FKACC69C66D9594E;
alter table title_instance_package_platform drop foreign key FKE793FB8F54894D8B;
alter table title_instance_package_platform drop foreign key FKE793FB8F40E502F5;
alter table title_instance_package_platform drop foreign key FKE793FB8F810634BB;
alter table title_instance_package_platform drop foreign key FKE793FB8F16ABD6D1;
alter table title_instance_package_platform drop foreign key FKE793FB8F3E48CC8E;
alter table titlesid drop foreign key FK908A5B76B3E66DCA;

#
# Drop old tables
#
drop table if exists combo;
drop table if exists doc;
drop table if exists doc_context;
drop table if exists license;
drop table if exists license_entitlement;
drop table if exists org;
drop table if exists org_role;
drop table if exists package;
drop table if exists platform;
drop table if exists refdata_category;
drop table if exists refdata_value;
drop table if exists subscription;
drop table if exists subscription_package;
drop table if exists title_instance;
drop table if exists title_instance_package_platform;
drop table if exists titlesid;

#
# Create schema
#
create table combo (
  combo_id bigint not null auto_increment, 
  combo_version bigint not null, 
  combo_status_rv_fk bigint, 
  combo_type_rv_fk bigint, 
  primary key (combo_id)
);

create table doc (
  doc_id bigint not null auto_increment, 
  doc_version bigint not null, 
  doc_status_rv_fk bigint, 
  doc_type_rv_fk bigint, 
  primary key (doc_id));

create table doc_context (
  dc_id bigint not null auto_increment, 
  dc_version bigint not null, 
  dc_doc_fk bigint not null, 
  primary key (dc_id));

create table license (lic_id bigint not null auto_increment, 
  lic_version bigint not null, 
  lic_status_rv_fk bigint, 
  lic_type_rv_fk bigint, 
  primary key (lic_id));

create table license_entitlement (
  le_id bigint not null auto_increment, 
  le_version bigint not null, 
  le_owner_subscription_fk bigint, 
  le_status_rv_fk bigint, 
  primary key (le_id));

create table org (
  org_id bigint not null auto_increment, 
  org_version bigint not null, 
  org_imp_id varchar(255) not null, 
  org_name varchar(255) not null, 
  primary key (org_id));

create table org_role (
  or_id bigint not null auto_increment, 
  or_version bigint not null, 
  or_lic_fk bigint, 
  or_org_fk bigint not null, 
  or_pkg_fk bigint, 
  or_roletype_fk bigint, 
  or_sub_fk bigint, 
  primary key (or_id));

create table package (
  pkg_id bigint not null auto_increment, 
  pkg_version bigint not null, 
  pkg_identifier varchar(255) not null, 
  pkg_imp_id varchar(255) not null, 
  pkg_name varchar(255) not null, 
  pkg_status_rv_fk bigint, 
  pkg_type_rv_fk bigint, 
  primary key (pkg_id));

create table platform (
  plat_id bigint not null auto_increment, 
  plat_version bigint not null, 
  plat_imp_id varchar(255) not null, 
  plat_name varchar(255) not null, 
  plat_status_rv_fk bigint, 
  plat_type_rv_fk bigint, 
  primary key (plat_id));

create table refdata_category (
  rdc_id bigint not null auto_increment, 
  rdc_version bigint not null, 
  rdc_description varchar(255) not null, 
  primary key (rdc_id));

create table refdata_value (
  rdv_id bigint not null auto_increment, 
  rdv_version bigint not null, 
  rdv_owner bigint not null, 
  rdv_value varchar(255) not null, 
  primary key (rdv_id));

create table subscription (
  sub_id bigint not null auto_increment, 
  sub_version bigint not null, 
  sub_owner_license_fk bigint, 
  sub_status_rv_fk bigint, 
  sub_type_rv_fk bigint, 
  primary key (sub_id));

create table subscription_package (
  sp_id bigint not null auto_increment, 
  sp_version bigint not null, 
  sp_pkg_fk bigint, 
  sp_sub_fk bigint, 
  primary key (sp_id));

create table title_instance (
  ti_id bigint not null auto_increment, 
  ti_version bigint not null, 
  ti_imp_id varchar(255) not null, 
  ti_status_rv_fk bigint, 
  title varchar(255) not null, 
  ti_type_rv_fk bigint, 
  primary key (ti_id));

create table title_instance_package_platform (
  tipp_id bigint not null auto_increment, 
  tipp_version bigint not null, 
  tipp_coverage_depth varchar(255), 
  tipp_coverage_note longtext, 
  tipp_embargo varchar(255), 
  tipp_end_date varchar(255), 
  tipp_end_issue varchar(255), 
  tipp_end_volume varchar(255), 
  tipp_imp_id varchar(255), 
  tipp_option_rv_fk bigint, 
  tipp_pkg_fk bigint not null, 
  tipp_plat_fk bigint not null, 
  tipp_start_date varchar(255), 
  tipp_start_issue varchar(255), 
  tipp_start_volume varchar(255), 
  tipp_status_rv_fk bigint, 
  tipp_ti_fk bigint not null, 
  primary key (tipp_id));

create table titlesid (
  tsi_id bigint not null auto_increment, 
  version bigint not null, tsi_identifier varchar(255) not null, 
  tsi_namespace varchar(255) not null, 
  tsi_ti_fk bigint not null, 
  primary key (tsi_id));


#
# Add constraints and indexes
#
alter table combo 
  add index FK5A7318E69CBC6D5 (combo_status_rv_fk), 
  add constraint FK5A7318E69CBC6D5 foreign key (combo_status_rv_fk) references refdata_value (rdv_id);

alter table combo 
  add index FK5A7318EF93C805D (combo_type_rv_fk), 
  add constraint FK5A7318EF93C805D foreign key (combo_type_rv_fk) references refdata_value (rdv_id);

alter table doc
  add index FK1853897381E73 (doc_type_rv_fk),
  add constraint FK1853897381E73 foreign key (doc_type_rv_fk) references refdata_value (rdv_id);

alter table doc
  add index FK185387758376B (doc_status_rv_fk),
  add constraint FK185387758376B foreign key (doc_status_rv_fk) references refdata_value (rdv_id);

alter table doc_context
  add index FK30EBA9A8C7230C87 (dc_doc_fk),
  add constraint FK30EBA9A8C7230C87 foreign key (dc_doc_fk) references doc (doc_id);

alter table license
  add index FK9F08441F144465 (lic_type_rv_fk),
  add constraint FK9F08441F144465 foreign key (lic_type_rv_fk) references refdata_value (rdv_id);

alter table license
  add index FK9F0844168C2A8DD (lic_status_rv_fk),
  add constraint FK9F0844168C2A8DD foreign key (lic_status_rv_fk) references refdata_value (rdv_id);

alter table license_entitlement
  add index FKD202F62F1086E0CA (le_status_rv_fk),
  add constraint FKD202F62F1086E0CA foreign key (le_status_rv_fk) references refdata_value (rdv_id);

alter table license_entitlement
  add index FKD202F62F7FEDED6F (le_owner_subscription_fk),
  add constraint FKD202F62F7FEDED6F foreign key (le_owner_subscription_fk) references subscription (sub_id);

alter table org_role
  add index FK4E5C38F1E646D31D (or_pkg_fk),
  add constraint FK4E5C38F1E646D31D foreign key (or_pkg_fk) references package (pkg_id);

alter table org_role
  add index FK4E5C38F199CCEB1A (or_sub_fk),
  add constraint FK4E5C38F199CCEB1A foreign key (or_sub_fk) references subscription (sub_id);

alter table org_role
  add index FK4E5C38F1879D5409 (or_roletype_fk),
  add constraint FK4E5C38F1879D5409 foreign key (or_roletype_fk) references refdata_value (rdv_id);

alter table org_role
  add index FK4E5C38F14F370323 (or_org_fk),
  add constraint FK4E5C38F14F370323 foreign key (or_org_fk) references org (org_id);

alter table org_role
  add index FK4E5C38F11960C01E (or_lic_fk),
  add constraint FK4E5C38F11960C01E foreign key (or_lic_fk) references license (lic_id);

alter table package
  add index FKCFE534462A381B57 (pkg_status_rv_fk),
  add constraint FKCFE534462A381B57 foreign key (pkg_status_rv_fk) references refdata_value (rdv_id);

alter table package
  add index FKCFE5344692580D5F (pkg_type_rv_fk),
  add constraint FKCFE5344692580D5F foreign key (pkg_type_rv_fk) references refdata_value (rdv_id);

alter table platform
  add index FK6FBD68736DC6881C (plat_type_rv_fk),
  add constraint FK6FBD68736DC6881C foreign key (plat_type_rv_fk) references refdata_value (rdv_id);

alter table platform
  add index FK6FBD6873E3F2DAD4 (plat_status_rv_fk),
  add constraint FK6FBD6873E3F2DAD4 foreign key (plat_status_rv_fk) references refdata_value (rdv_id);

alter table refdata_value
  add index FKF33A596F18DAEBF6 (rdv_owner),
  add constraint FKF33A596F18DAEBF6 foreign key (rdv_owner) references refdata_category (rdc_id);

alter table subscription
  add index FK1456591D7D96D7D2 (sub_owner_license_fk),
  add constraint FK1456591D7D96D7D2 foreign key (sub_owner_license_fk) references license (lic_id);

alter table subscription
  add index FK1456591D6297706B (sub_type_rv_fk),
  add constraint FK1456591D6297706B foreign key (sub_type_rv_fk) references refdata_value (rdv_id);

alter table subscription
  add index FK1456591DE82AEB63 (sub_status_rv_fk),
  add constraint FK1456591DE82AEB63 foreign key (sub_status_rv_fk) references refdata_value (rdv_id);

alter table subscription_package
  add index FK5122C72467963563 (sp_pkg_fk),
  add constraint FK5122C72467963563 foreign key (sp_pkg_fk) references package (pkg_id);

alter table subscription_package
  add index FK5122C7241B1C4D60 (sp_sub_fk),
  add constraint FK5122C7241B1C4D60 foreign key (sp_sub_fk) references subscription (sub_id);

alter table title_instance
  add index FKACC69C334E5D16 (ti_type_rv_fk),
  add constraint FKACC69C334E5D16 foreign key (ti_type_rv_fk) references refdata_value (rdv_id);

alter table title_instance
  add index FKACC69C66D9594E (ti_status_rv_fk),
  add constraint FKACC69C66D9594E foreign key (ti_status_rv_fk) references refdata_value (rdv_id);

alter table title_instance_package_platform
  add index FKE793FB8F54894D8B (tipp_pkg_fk),
  add constraint FKE793FB8F54894D8B foreign key (tipp_pkg_fk) references package (pkg_id);

alter table title_instance_package_platform
  add index FKE793FB8F40E502F5 (tipp_ti_fk),
  add constraint FKE793FB8F40E502F5 foreign key (tipp_ti_fk) references title_instance (ti_id);

alter table title_instance_package_platform
  add index FKE793FB8F810634BB (tipp_plat_fk),
  add constraint FKE793FB8F810634BB foreign key (tipp_plat_fk) references platform (plat_id);

alter table title_instance_package_platform
  add index FKE793FB8F16ABD6D1 (tipp_option_rv_fk),
  add constraint FKE793FB8F16ABD6D1 foreign key (tipp_option_rv_fk) references refdata_value (rdv_id);

alter table title_instance_package_platform
  add index FKE793FB8F3E48CC8E (tipp_status_rv_fk),
  add constraint FKE793FB8F3E48CC8E foreign key (tipp_status_rv_fk) references refdata_value (rdv_id);

alter table titlesid
  add index FK908A5B76B3E66DCA (tsi_ti_fk),
  add constraint FK908A5B76B3E66DCA foreign key (tsi_ti_fk) references title_instance (ti_id);
