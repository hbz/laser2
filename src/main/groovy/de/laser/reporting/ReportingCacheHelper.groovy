package de.laser.reporting

import de.laser.Subscription
import de.laser.helper.DateUtils
import java.text.SimpleDateFormat

class ReportingCacheHelper {

    static void initSubscriptionCache(long id) {
        println 'initSubscriptionCache( ' + id + ' )'

        Subscription sub = Subscription.get(id)
        String filterResult = sub.name

        if (sub.startDate || sub.endDate) {
            SimpleDateFormat sdf = DateUtils.getSDF_NoTime()
            filterResult += ' (' + (sub.startDate ? sdf.format(sub.startDate) : '') + ' - ' + (sub.endDate ? sdf.format(sub.endDate) : '')  + ')'
        }

        ReportingCache rCache = new ReportingCache( ReportingCache.CTX_SUBSCRIPTION )
        rCache.put( [
                filterCache: [:],
                queryCache: [:]
        ] )

        rCache.intoFilterCache('result', filterResult)
    }
}