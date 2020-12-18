databaseChangeLog = {

    changeSet(author: "galffy (generated)", id: "1608295080812-1") {
        addColumn(tableName: "title_instance_package_platform") {
            column(name: "tipp_name_fk", type: "varchar(255)")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-2") {
        addColumn(tableName: "title_instance_package_platform") {
            column(name: "tipp_series_name_fk", type: "varchar(255)")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-3") {
        addColumn(tableName: "title_instance_package_platform") {
            column(name: "tipp_subject_reference_fk", type: "varchar(255)")
        }
    }

    /*
    changeSet(author: "galffy (generated)", id: "1608295080812-4") {
        addUniqueConstraint(columnNames: "ciec_guid", constraintName: "UC_COST_ITEM_ELEMENT_CONFIGURATIONCIEC_GUID_COL", tableName: "cost_item_element_configuration")
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-5") {
        addUniqueConstraint(columnNames: "pkg_gokb_id", constraintName: "UC_PACKAGEPKG_GOKB_ID_COL", tableName: "package")
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-6") {
        addUniqueConstraint(columnNames: "plat_gokb_id", constraintName: "UC_PLATFORMPLAT_GOKB_ID_COL", tableName: "platform")
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-7") {
        addUniqueConstraint(columnNames: "pi_guid", constraintName: "UC_PRICE_ITEMPI_GUID_COL", tableName: "price_item")
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-8") {
        addUniqueConstraint(columnNames: "ti_gokb_id", constraintName: "UC_TITLE_INSTANCETI_GOKB_ID_COL", tableName: "title_instance")
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-9") {
        addUniqueConstraint(columnNames: "tipp_gokb_id", constraintName: "UC_TITLE_INSTANCE_PACKAGE_PLATFORMTIPP_GOKB_ID_COL", tableName: "title_instance_package_platform")
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-10") {
        dropTable(tableName: "delete_me")
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-11") {
        dropSequence(sequenceName: "hibernate_sequence")
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-12") {
        addNotNullConstraint(columnDataType: "bigint", columnName: "dc_link_fk", tableName: "doc_context")
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-13") {
        dropNotNullConstraint(columnDataType: "bigint", columnName: "tipp_ti_fk", tableName: "title_instance_package_platform")
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-14") {
        dropIndex(indexName: "adr_org_idx", tableName: "address")

        createIndex(indexName: "adr_org_idx", tableName: "address") {
            column(name: "adr_org_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-15") {
        dropIndex(indexName: "adr_prs_idx", tableName: "address")

        createIndex(indexName: "adr_prs_idx", tableName: "address") {
            column(name: "adr_prs_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-16") {
        dropIndex(indexName: "auc_ref_idx", tableName: "audit_config")

        createIndex(indexName: "auc_ref_idx", tableName: "audit_config") {
            column(name: "auc_reference_class")

            column(name: "auc_reference_id")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-17") {
        dropIndex(indexName: "bc_owner_idx", tableName: "budget_code")

        createIndex(indexName: "bc_owner_idx", tableName: "budget_code") {
            column(name: "bc_owner_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-18") {
        dropIndex(indexName: "ci_dates_idx", tableName: "cost_item")

        createIndex(indexName: "ci_dates_idx", tableName: "cost_item") {
            column(name: "ci_end_date")

            column(name: "ci_start_date")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-19") {
        dropIndex(indexName: "ci_owner_idx", tableName: "cost_item")

        createIndex(indexName: "ci_owner_idx", tableName: "cost_item") {
            column(name: "ci_owner")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-20") {
        dropIndex(indexName: "ci_sub_idx", tableName: "cost_item")

        createIndex(indexName: "ci_sub_idx", tableName: "cost_item") {
            column(name: "ci_sub_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-21") {
        dropIndex(indexName: "ct_org_idx", tableName: "contact")

        createIndex(indexName: "ct_org_idx", tableName: "contact") {
            column(name: "ct_org_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-22") {
        dropIndex(indexName: "ct_prs_idx", tableName: "contact")

        createIndex(indexName: "ct_prs_idx", tableName: "contact") {
            column(name: "ct_prs_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-23") {
        dropIndex(indexName: "das_responsible_org_fk_idx", tableName: "dashboard_due_date")

        createIndex(indexName: "das_responsible_org_fk_idx", tableName: "dashboard_due_date") {
            column(name: "das_responsible_org_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-24") {
        dropIndex(indexName: "das_responsible_user_fk_idx", tableName: "dashboard_due_date")

        createIndex(indexName: "das_responsible_user_fk_idx", tableName: "dashboard_due_date") {
            column(name: "das_responsible_user_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-25") {
        dropIndex(indexName: "doc_lic_idx", tableName: "doc_context")

        createIndex(indexName: "doc_lic_idx", tableName: "doc_context") {
            column(name: "dc_lic_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-26") {
        dropIndex(indexName: "doc_org_idx", tableName: "doc_context")

        createIndex(indexName: "doc_org_idx", tableName: "doc_context") {
            column(name: "dc_org_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-27") {
        dropIndex(indexName: "doc_owner_idx", tableName: "doc_context")

        createIndex(indexName: "doc_owner_idx", tableName: "doc_context") {
            column(name: "dc_doc_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-28") {
        dropIndex(indexName: "doc_sub_idx", tableName: "doc_context")

        createIndex(indexName: "doc_sub_idx", tableName: "doc_context") {
            column(name: "dc_sub_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-29") {
        dropIndex(indexName: "doc_type_idx", tableName: "doc")

        createIndex(indexName: "doc_type_idx", tableName: "doc") {
            column(name: "doc_type_rv_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-30") {
        dropIndex(indexName: "doc_uuid_idx", tableName: "doc")

        createIndex(indexName: "doc_uuid_idx", tableName: "doc") {
            column(name: "doc_docstore_uuid")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-31") {
        dropIndex(indexName: "fact_access_idx", tableName: "fact")

        createIndex(indexName: "fact_access_idx", tableName: "fact") {
            column(name: "related_title_id")

            column(name: "inst_id")

            column(name: "supplier_id")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-32") {
        dropIndex(indexName: "fact_metric_idx", tableName: "fact")

        createIndex(indexName: "fact_metric_idx", tableName: "fact") {
            column(name: "fact_metric_rdv_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-33") {
        dropUniqueConstraint(constraintName: "UC_FACTFACT_UID_COL", tableName: "fact")

        addUniqueConstraint(columnNames: "fact_uid", constraintName: "UC_FACTFACT_UID_COL", tableName: "fact")
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-34") {
        dropIndex(indexName: "i10n_ref_idx", tableName: "i10n_translation")

        createIndex(indexName: "i10n_ref_idx", tableName: "i10n_translation") {
            column(name: "i10n_reference_class")

            column(name: "i10n_reference_id")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-35") {
        dropIndex(indexName: "ic_dates_idx", tableName: "issue_entitlement_coverage")

        createIndex(indexName: "ic_dates_idx", tableName: "issue_entitlement_coverage") {
            column(name: "ic_end_date")

            column(name: "ic_start_date")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-36") {
        dropIndex(indexName: "id_tipp_idx", tableName: "identifier")

        createIndex(indexName: "id_tipp_idx", tableName: "identifier") {
            column(name: "id_tipp_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-37") {
        dropIndex(indexName: "id_title_idx", tableName: "identifier")

        createIndex(indexName: "id_title_idx", tableName: "identifier") {
            column(name: "id_ti_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-38") {
        dropIndex(indexName: "id_value_idx", tableName: "identifier")

        createIndex(indexName: "id_value_idx", tableName: "identifier") {
            column(name: "id_ns_fk")

            column(name: "id_value")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-39") {
        dropIndex(indexName: "ie_sub_idx", tableName: "issue_entitlement")

        createIndex(indexName: "ie_sub_idx", tableName: "issue_entitlement") {
            column(name: "ie_subscription_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-40") {
        dropIndex(indexName: "ie_tipp_idx", tableName: "issue_entitlement")

        createIndex(indexName: "ie_tipp_idx", tableName: "issue_entitlement") {
            column(name: "ie_tipp_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-41") {
        dropIndex(indexName: "l_dest_lic_idx", tableName: "links")

        createIndex(indexName: "l_dest_lic_idx", tableName: "links") {
            column(name: "l_dest_lic_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-42") {
        dropIndex(indexName: "l_dest_sub_idx", tableName: "links")

        createIndex(indexName: "l_dest_sub_idx", tableName: "links") {
            column(name: "l_dest_sub_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-43") {
        dropIndex(indexName: "l_source_lic_idx", tableName: "links")

        createIndex(indexName: "l_source_lic_idx", tableName: "links") {
            column(name: "l_source_lic_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-44") {
        dropIndex(indexName: "l_source_sub_idx", tableName: "links")

        createIndex(indexName: "l_source_sub_idx", tableName: "links") {
            column(name: "l_source_sub_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-45") {
        dropIndex(indexName: "lcp_owner_idx", tableName: "license_property")

        createIndex(indexName: "lcp_owner_idx", tableName: "license_property") {
            column(name: "lp_owner_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-46") {
        dropIndex(indexName: "lic_dates_idx", tableName: "license")

        createIndex(indexName: "lic_dates_idx", tableName: "license") {
            column(name: "lic_end_date")

            column(name: "lic_start_date")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-47") {
        dropIndex(indexName: "lic_parent_idx", tableName: "license")

        createIndex(indexName: "lic_parent_idx", tableName: "license") {
            column(name: "lic_parent_lic_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-48") {
        dropIndex(indexName: "lp_instance_of_idx", tableName: "license_property")

        createIndex(indexName: "lp_instance_of_idx", tableName: "license_property") {
            column(name: "lp_instance_of_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-49") {
        dropIndex(indexName: "lp_tenant_idx", tableName: "license_property")

        createIndex(indexName: "lp_tenant_idx", tableName: "license_property") {
            column(name: "lp_tenant_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-50") {
        dropIndex(indexName: "lp_type_idx", tableName: "license_property")

        createIndex(indexName: "lp_type_idx", tableName: "license_property") {
            column(name: "lp_type_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-51") {
        dropIndex(indexName: "op_owner_idx", tableName: "org_property")

        createIndex(indexName: "op_owner_idx", tableName: "org_property") {
            column(name: "op_owner_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-52") {
        dropIndex(indexName: "op_tenant_idx", tableName: "org_property")

        createIndex(indexName: "op_tenant_idx", tableName: "org_property") {
            column(name: "op_tenant_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-53") {
        dropIndex(indexName: "op_type_idx", tableName: "org_property")

        createIndex(indexName: "op_type_idx", tableName: "org_property") {
            column(name: "op_type_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-54") {
        dropIndex(indexName: "or_lic_idx", tableName: "org_role")

        createIndex(indexName: "or_lic_idx", tableName: "org_role") {
            column(name: "or_lic_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-55") {
        dropIndex(indexName: "or_org_rt_idx", tableName: "org_role")

        createIndex(indexName: "or_org_rt_idx", tableName: "org_role") {
            column(name: "or_org_fk")

            column(name: "or_roletype_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-56") {
        dropIndex(indexName: "or_pkg_idx", tableName: "org_role")

        createIndex(indexName: "or_pkg_idx", tableName: "org_role") {
            column(name: "or_pkg_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-57") {
        dropIndex(indexName: "or_sub_idx", tableName: "org_role")

        createIndex(indexName: "or_sub_idx", tableName: "org_role") {
            column(name: "or_sub_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-58") {
        dropIndex(indexName: "ord_owner_idx", tableName: "ordering")

        createIndex(indexName: "ord_owner_idx", tableName: "ordering") {
            column(name: "ord_owner")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-59") {
        dropIndex(indexName: "org_name_idx", tableName: "org")

        createIndex(indexName: "org_name_idx", tableName: "org") {
            column(name: "org_name")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-60") {
        dropIndex(indexName: "org_shortcode_idx", tableName: "org")

        createIndex(indexName: "org_shortcode_idx", tableName: "org") {
            column(name: "org_shortcode")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-61") {
        dropIndex(indexName: "org_shortname_idx", tableName: "org")

        createIndex(indexName: "org_shortname_idx", tableName: "org") {
            column(name: "org_shortname")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-62") {
        dropIndex(indexName: "org_sortname_idx", tableName: "org")

        createIndex(indexName: "org_sortname_idx", tableName: "org") {
            column(name: "org_sortname")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-63") {
        dropIndex(indexName: "os_org_idx", tableName: "org_setting")

        createIndex(indexName: "os_org_idx", tableName: "org_setting") {
            column(name: "os_org_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-64") {
        dropIndex(indexName: "pd_tenant_idx", tableName: "property_definition")

        createIndex(indexName: "pd_tenant_idx", tableName: "property_definition") {
            column(name: "pd_tenant_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-65") {
        dropIndex(indexName: "pdg_tenant_idx", tableName: "property_definition_group")

        createIndex(indexName: "pdg_tenant_idx", tableName: "property_definition_group") {
            column(name: "pdg_tenant_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-66") {
        dropIndex(indexName: "pending_change_costitem_idx", tableName: "pending_change")

        createIndex(indexName: "pending_change_costitem_idx", tableName: "pending_change") {
            column(name: "pc_ci_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-67") {
        dropIndex(indexName: "pending_change_lic_idx", tableName: "pending_change")

        createIndex(indexName: "pending_change_lic_idx", tableName: "pending_change") {
            column(name: "pc_lic_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-68") {
        dropIndex(indexName: "pending_change_oid_idx", tableName: "pending_change")

        createIndex(indexName: "pending_change_oid_idx", tableName: "pending_change") {
            column(name: "pc_oid")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-69") {
        dropIndex(indexName: "pending_change_pkg_idx", tableName: "pending_change")

        createIndex(indexName: "pending_change_pkg_idx", tableName: "pending_change") {
            column(name: "pc_pkg_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-70") {
        dropIndex(indexName: "pending_change_pl_cd_oid_idx", tableName: "pending_change")

        createIndex(indexName: "pending_change_pl_cd_oid_idx", tableName: "pending_change") {
            column(name: "pc_change_doc_oid")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-71") {
        dropIndex(indexName: "pending_change_pl_ct_oid_idx", tableName: "pending_change")

        createIndex(indexName: "pending_change_pl_ct_oid_idx", tableName: "pending_change") {
            column(name: "pc_change_target_oid")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-72") {
        dropIndex(indexName: "pending_change_sub_idx", tableName: "pending_change")

        createIndex(indexName: "pending_change_sub_idx", tableName: "pending_change") {
            column(name: "pc_sub_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-73") {
        dropIndex(indexName: "pkg_dates_idx", tableName: "package")

        createIndex(indexName: "pkg_dates_idx", tableName: "package") {
            column(name: "pkg_end_date")

            column(name: "pkg_start_date")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-74") {
        dropIndex(indexName: "plat_org_idx", tableName: "platform")

        createIndex(indexName: "plat_org_idx", tableName: "platform") {
            column(name: "plat_org_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-75") {
        dropIndex(indexName: "plp_owner_idx", tableName: "platform_property")

        createIndex(indexName: "plp_owner_idx", tableName: "platform_property") {
            column(name: "plp_owner_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-76") {
        dropIndex(indexName: "plp_tenant_idx", tableName: "platform_property")

        createIndex(indexName: "plp_tenant_idx", tableName: "platform_property") {
            column(name: "plp_tenant_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-77") {
        dropIndex(indexName: "plp_type_idx", tableName: "platform_property")

        createIndex(indexName: "plp_type_idx", tableName: "platform_property") {
            column(name: "plp_type_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-78") {
        dropIndex(indexName: "pp_owner_idx", tableName: "person_property")

        createIndex(indexName: "pp_owner_idx", tableName: "person_property") {
            column(name: "pp_owner_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-79") {
        dropIndex(indexName: "pp_tenant_fk", tableName: "person_property")

        createIndex(indexName: "pp_tenant_fk", tableName: "person_property") {
            column(name: "pp_tenant_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-80") {
        dropIndex(indexName: "pp_type_idx", tableName: "person_property")

        createIndex(indexName: "pp_type_idx", tableName: "person_property") {
            column(name: "pp_type_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-81") {
        dropIndex(indexName: "pr_prs_org_idx", tableName: "person_role")

        createIndex(indexName: "pr_prs_org_idx", tableName: "person_role") {
            column(name: "pr_org_fk")

            column(name: "pr_prs_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-82") {
        dropIndex(indexName: "rdc_description_de_idx", tableName: "refdata_category")

        createIndex(indexName: "rdc_description_de_idx", tableName: "refdata_category") {
            column(name: "rdc_description_de")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-83") {
        dropIndex(indexName: "rdc_description_en_idx", tableName: "refdata_category")

        createIndex(indexName: "rdc_description_en_idx", tableName: "refdata_category") {
            column(name: "rdc_description_en")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-84") {
        dropIndex(indexName: "rdc_description_idx", tableName: "refdata_category")

        createIndex(indexName: "rdc_description_idx", tableName: "refdata_category") {
            column(name: "rdc_description")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-85") {
        dropIndex(indexName: "rdv_owner_value_idx", tableName: "refdata_value")

        createIndex(indexName: "rdv_owner_value_idx", tableName: "refdata_value") {
            column(name: "rdv_owner")

            column(name: "rdv_value")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-86") {
        dropIndex(indexName: "rdv_value_de_idx", tableName: "refdata_value")

        createIndex(indexName: "rdv_value_de_idx", tableName: "refdata_value") {
            column(name: "rdv_value_de")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-87") {
        dropIndex(indexName: "rdv_value_en_idx", tableName: "refdata_value")

        createIndex(indexName: "rdv_value_en_idx", tableName: "refdata_value") {
            column(name: "rdv_value_en")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-88") {
        dropIndex(indexName: "sp_instance_of_idx", tableName: "subscription_property")

        createIndex(indexName: "sp_instance_of_idx", tableName: "subscription_property") {
            column(name: "sp_instance_of_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-89") {
        dropIndex(indexName: "sp_owner_idx", tableName: "subscription_property")

        createIndex(indexName: "sp_owner_idx", tableName: "subscription_property") {
            column(name: "sp_owner_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-90") {
        dropIndex(indexName: "sp_sub_pkg_idx", tableName: "subscription_package")

        createIndex(indexName: "sp_sub_pkg_idx", tableName: "subscription_package") {
            column(name: "sp_pkg_fk")

            column(name: "sp_sub_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-91") {
        dropIndex(indexName: "sp_tenant_idx", tableName: "subscription_property")

        createIndex(indexName: "sp_tenant_idx", tableName: "subscription_property") {
            column(name: "sp_tenant_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-92") {
        dropIndex(indexName: "sp_type_idx", tableName: "subscription_property")

        createIndex(indexName: "sp_type_idx", tableName: "subscription_property") {
            column(name: "sp_type_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-93") {
        dropIndex(indexName: "sp_uri_idx", tableName: "system_profiler")

        createIndex(indexName: "sp_uri_idx", tableName: "system_profiler") {
            column(name: "sp_uri")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-94") {
        dropIndex(indexName: "stats_cursor_idx", tableName: "stats_triple_cursor")

        createIndex(indexName: "stats_cursor_idx", tableName: "stats_triple_cursor") {
            column(name: "stats_title_id")

            column(name: "stats_supplier_id")

            column(name: "stats_customer_id")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-95") {
        dropIndex(indexName: "sub_dates_idx", tableName: "subscription")

        createIndex(indexName: "sub_dates_idx", tableName: "subscription") {
            column(name: "sub_end_date")

            column(name: "sub_start_date")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-96") {
        dropIndex(indexName: "sub_parent_idx", tableName: "subscription")

        createIndex(indexName: "sub_parent_idx", tableName: "subscription") {
            column(name: "sub_parent_sub_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-97") {
        dropIndex(indexName: "sub_type_idx", tableName: "subscription")

        createIndex(indexName: "sub_type_idx", tableName: "subscription") {
            column(name: "sub_type_rv_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-98") {
        dropIndex(indexName: "tc_dates_idx", tableName: "tippcoverage")

        createIndex(indexName: "tc_dates_idx", tableName: "tippcoverage") {
            column(name: "tc_end_date")

            column(name: "tc_start_date")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-99") {
        dropIndex(indexName: "td_new_idx", tableName: "property_definition")

        createIndex(indexName: "td_new_idx", tableName: "property_definition") {
            column(name: "pd_description")

            column(name: "pd_name")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-100") {
        dropIndex(indexName: "td_type_idx", tableName: "property_definition")

        createIndex(indexName: "td_type_idx", tableName: "property_definition") {
            column(name: "pd_rdc")

            column(name: "pd_type")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-101") {
        dropUniqueConstraint(constraintName: "UC_TITLE_INSTANCETI_GOKB_ID_COL", tableName: "title_instance")

        addUniqueConstraint(columnNames: "ti_gokb_id", constraintName: "UC_TITLE_INSTANCETI_GOKB_ID_COL", tableName: "title_instance")
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-102") {
        dropIndex(indexName: "tipp_idx", tableName: "title_instance_package_platform")

        createIndex(indexName: "tipp_idx", tableName: "title_instance_package_platform") {
            column(name: "tipp_plat_fk")

            column(name: "tipp_pkg_fk")

            column(name: "tipp_ti_fk")
        }
    }

    changeSet(author: "galffy (generated)", id: "1608295080812-103") {
        dropIndex(indexName: "us_user_idx", tableName: "user_setting")

        createIndex(indexName: "us_user_idx", tableName: "user_setting") {
            column(name: "us_user_fk")
        }
    }
     */
}

