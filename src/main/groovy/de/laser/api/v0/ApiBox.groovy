package de.laser.api.v0

import de.laser.RefdataValue
import de.laser.helper.Constants

class ApiBox {

    static final List FAILURE_CODES  = [
            Constants.HTTP_BAD_REQUEST,
            Constants.HTTP_PRECONDITION_FAILED,
            Constants.OBJECT_NOT_FOUND,
            Constants.OBJECT_STATUS_DELETED
    ]

    Object obj
    String status


    static ApiBox get() {
        new ApiBox(obj:null, status:null)
    }

    void validatePrecondition_1() {
        if (obj) {
            if (obj.size() == 1) {
                obj = obj.get(0)
            }
            else {
                obj = null
                status = Constants.HTTP_PRECONDITION_FAILED
            }
        } else {
            obj = null
            status = Constants.OBJECT_NOT_FOUND
        }
    }

    void validateDeletedStatus_2(String attribute, RefdataValue rdvDeleted) {
        if (obj.getProperty(attribute) == rdvDeleted) {
            status = Constants.OBJECT_STATUS_DELETED
        }
    }

    boolean checkFailureCodes_3() {
        ! (status in ApiBox.FAILURE_CODES)
    }
}

