package de.laser.finance

import de.laser.IssueEntitlement
import de.laser.RefdataValue
import de.laser.base.AbstractBase
import de.laser.helper.RDConstants
import de.laser.helper.RefdataAnnotation

class PriceItem extends AbstractBase {

    @RefdataAnnotation(cat = RDConstants.CURRENCY)
    RefdataValue listCurrency

    @RefdataAnnotation(cat = RDConstants.CURRENCY)
    RefdataValue localCurrency

    BigDecimal listPrice
    BigDecimal localPrice

    Date priceDate

    Date dateCreated
    Date lastUpdated

    static belongsTo = [issueEntitlement: IssueEntitlement]

    static mapping = {
        id                  column: 'pi_id'
        globalUID           column: 'pi_guid'
        listPrice           column: 'pi_list_price'
        listCurrency        column: 'pi_list_currency_rv_fk'
        localPrice          column: 'pi_local_price'
        localCurrency       column: 'pi_local_currency_rv_fk'
        priceDate           column: 'pi_price_date'
        issueEntitlement    column: 'pi_ie_fk'
        lastUpdated         column: 'pi_last_updated'
        dateCreated         column: 'pi_date_created'
    }

    static constraints = {
        globalUID           (blank: false, unique: true, maxSize: 255)
        listPrice           (nullable: true)
        listCurrency        (nullable: true)
        localPrice          (nullable: true)
        localCurrency       (nullable: true)
        priceDate           (nullable: true)
        lastUpdated         (nullable: true)
        dateCreated         (nullable: true)
    }

    @Override
    def beforeInsert() {
        super.beforeInsertHandler()
    }
    @Override
    def beforeUpdate() {
        super.beforeUpdateHandler()
    }
    @Override
    def beforeDelete() {
        super.beforeDeleteHandler()
    }
}
