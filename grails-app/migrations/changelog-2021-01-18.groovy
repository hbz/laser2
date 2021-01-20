databaseChangeLog = {

    changeSet(author: "djebeniani (modified)", id: "1610973422255-1") {
        grailsChange {
            change {
                sql.execute("DELETE FROM issue_entitlement_group_item where igi_ie_fk in (select ie_id FROM issue_entitlement JOIN refdata_value rv ON issue_entitlement.ie_status_rv_fk = rv.rdv_id WHERE rdv_value = 'Deleted')")
            }
            rollback {}
        }
    }

    changeSet(author: "agalffy (generated)", id: "1610973422255-2") {
        addColumn(tableName: "pending_change") {
            column(name: "pc_tc_fk", type: "int8")
        }
    }

    changeSet(author: "agalffy (generated)", id: "1610973422255-3") {
        addColumn(tableName: "pending_change") {
            column(name: "pc_tipp_fk", type: "int8")
        }
    }

    changeSet(author: "agalffy (generated)", id: "1610973422255-4") {
        createIndex(indexName: "pending_change_tc_idx", tableName: "pending_change") {
            column(name: "pc_tc_fk")
        }
    }

    changeSet(author: "agalffy (generated)", id: "1610973422255-5") {
        createIndex(indexName: "pending_change_tipp_idx", tableName: "pending_change") {
            column(name: "pc_tipp_fk")
        }
    }

    changeSet(author: "agalffy (generated)", id: "1610973422255-6") {
        addForeignKeyConstraint(baseColumnNames: "pc_tipp_fk", baseTableName: "pending_change", constraintName: "FKm0vthqihnnrirmglv4samn8a7", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "tipp_id", referencedTableName: "title_instance_package_platform")
    }

    changeSet(author: "agalffy (generated)", id: "1610973422255-7") {
        addForeignKeyConstraint(baseColumnNames: "pc_tc_fk", baseTableName: "pending_change", constraintName: "FKpfvhr4eht8rf8mjpfvsoup0ky", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "tc_id", referencedTableName: "tippcoverage")
    }
}
