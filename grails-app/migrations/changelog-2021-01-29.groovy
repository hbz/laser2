databaseChangeLog = {

    changeSet(author: "klober (modified)", id: "1611910413872-1") {
        grailsChange {
            change {
                sql.execute ( "CREATE COLLATION IF NOT EXISTS laser_german_phonebook (provider = icu, locale = 'de-u-co-phonebk')" )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-2") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE address ALTER COLUMN adr_pob_city TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE address' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-3") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE budget_code ALTER COLUMN bc_value TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE budget_code' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-4") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE cost_item ALTER COLUMN ci_cost_title TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE cost_item' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-5") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE customer_identifier ALTER COLUMN cid_value TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE customer_identifier' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-6") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE doc ALTER COLUMN doc_title TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE doc' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-7") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE due_date_object ALTER COLUMN ddo_attribute_value_de TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE due_date_object' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-8") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE i10n_translation ALTER COLUMN i10n_value_de TYPE text COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE i10n_translation ALTER COLUMN i10n_value_fr TYPE text COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE i10n_translation' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-9") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE identifier ALTER COLUMN id_value TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE identifier' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-10") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE identifier_namespace ALTER COLUMN idns_description_de TYPE text COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE identifier_namespace ALTER COLUMN idns_name_de TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE identifier_namespace' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-11") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE issue_entitlement_group ALTER COLUMN ig_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE issue_entitlement_group' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-12") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE license ALTER COLUMN lic_ref TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE license ALTER COLUMN lic_sortable_ref TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE license' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-13") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE org ALTER COLUMN org_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE org ALTER COLUMN org_shortname TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE org ALTER COLUMN org_sortname TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE org' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-14") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE org_access_point ALTER COLUMN oar_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE org_access_point' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-15") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE package ALTER COLUMN pkg_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE package ALTER COLUMN pkg_sort_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE package' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-16") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE person ALTER COLUMN prs_first_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE person ALTER COLUMN prs_middle_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE person ALTER COLUMN prs_last_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE person ALTER COLUMN prs_title TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE person' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-17") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE platform ALTER COLUMN plat_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE platform ALTER COLUMN plat_normalised_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE platform' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-18") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE property_definition ALTER COLUMN pd_explanation_de TYPE text COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE property_definition ALTER COLUMN pd_name_de TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                //sql.execute ( 'ALTER TABLE property_definition ALTER COLUMN pd_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE property_definition' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-19") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE property_definition_group ALTER COLUMN pdg_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE property_definition_group' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-20") {
        grailsChange {
            change {
                //sql.execute ( 'ALTER TABLE refdata_category ALTER COLUMN rdc_description TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE refdata_category ALTER COLUMN rdc_description_de TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE refdata_category' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-21") {
        grailsChange {
            change {
                //sql.execute ( 'ALTER TABLE refdata_value ALTER COLUMN rdv_value TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE refdata_value ALTER COLUMN rdv_value_de TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE refdata_value ALTER COLUMN rdv_explanation_de TYPE text COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE refdata_value' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-22") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE subscription ALTER COLUMN sub_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE subscription' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-23") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE survey_config ALTER COLUMN surconf_header TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE survey_config' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-24") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE survey_info ALTER COLUMN surin_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE survey_info' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-25") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE survey_property ALTER COLUMN surpro_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE survey_property' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-26") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE system_announcement ALTER COLUMN sa_title TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE system_announcement' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-27") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE system_message ALTER COLUMN sm_content_de TYPE text COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE system_message' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-28") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE system_setting ALTER COLUMN set_name TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE system_setting' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-29") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE system_ticket ALTER COLUMN sti_title TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE system_ticket' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-30") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE task ALTER COLUMN tsk_title TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE task' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-31") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE title_instance ALTER COLUMN sort_title TYPE text COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE title_instance ALTER COLUMN ti_key_title TYPE text COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE title_instance ALTER COLUMN ti_norm_title TYPE text COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE title_instance ALTER COLUMN ti_title TYPE text COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE title_instance' )
            }
        }
    }

    changeSet(author: "klober (modified)", id: "1611910413872-32") {
        grailsChange {
            change {
                sql.execute ( 'ALTER TABLE "user" ALTER COLUMN display TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'ALTER TABLE "user" ALTER COLUMN username TYPE varchar(255) COLLATE "laser_german_phonebook"' )
                sql.execute ( 'REINDEX TABLE "user"' )
            }
        }
    }
}
