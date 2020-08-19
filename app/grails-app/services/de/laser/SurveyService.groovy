package de.laser

import com.k_int.kbplus.*
import com.k_int.kbplus.auth.User
import com.k_int.kbplus.auth.UserOrg
import com.k_int.properties.PropertyDefinition
import de.laser.helper.ConfigUtils
import de.laser.helper.DateUtil
import de.laser.helper.RDStore
import de.laser.helper.ServerUtils
import grails.plugin.mail.MailService
import grails.transaction.Transactional
import grails.util.Holders
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.context.i18n.LocaleContextHolder

import java.text.SimpleDateFormat

@Transactional
class SurveyService {

    AccessService accessService
    ContextService contextService
    def messageSource
    ExportService exportService
    MailService mailService
    EscapeService escapeService
    GrailsApplication grailsApplication
    String replyTo
    GenericOIDService genericOIDService

    SimpleDateFormat formatter = DateUtil.getSDF_dmy()
    String from

    @javax.annotation.PostConstruct
    void init() {
        from = ConfigUtils.getNotificationsEmailFrom()
        messageSource = Holders.grailsApplication.mainContext.getBean('messageSource')
    }


    boolean isEditableSurvey(Org org, SurveyInfo surveyInfo) {

        if (accessService.checkPermAffiliationX('ORG_CONSORTIUM', 'INST_EDITOR', 'ROLE_ADMIN') && surveyInfo.owner?.id == contextService.getOrg().id) {
            return true
        }

        if (surveyInfo.status != RDStore.SURVEY_SURVEY_STARTED) {
            return false
        }

        if (accessService.checkPermAffiliationX('ORG_BASIC_MEMBER', 'INST_EDITOR', 'ROLE_ADMIN')) {
            def surveyResults = SurveyResult.findAllByParticipantAndSurveyConfigInList(org, surveyInfo.surveyConfigs)

            if (surveyResults) {
                return surveyResults.finishDate.contains(null) ? true : false
            } else {
                return false
            }
        }else{
            return false
        }


    }

    boolean isEditableIssueEntitlementsSurvey(Org org, SurveyConfig surveyConfig) {

        if (accessService.checkPermAffiliationX('ORG_CONSORTIUM', 'INST_EDITOR', 'ROLE_ADMIN') && surveyConfig.surveyInfo.owner?.id == contextService.getOrg().id) {
            return true
        }

        if (!surveyConfig.pickAndChoose) {
            return false
        }

        if (surveyConfig.surveyInfo.status != RDStore.SURVEY_SURVEY_STARTED) {
            return false
        }

        if (accessService.checkPermAffiliationX('ORG_BASIC_MEMBER', 'INST_EDITOR', 'ROLE_ADMIN')) {

            if (SurveyOrg.findByOrgAndSurveyConfig(org, surveyConfig)?.finishDate) {
                return false
            } else {
                return true
            }
        } else {
            return false
        }

    }
    @Deprecated
    Map<String, Object> getParticipantConfigNavigation(Org org, SurveyInfo surveyInfo, SurveyConfig surveyConfig) {
        Map<String, Object> result = [:]
        def surveyResults = SurveyResult.findAllByParticipantAndSurveyConfigInList(org, surveyInfo.surveyConfigs).sort { it.surveyConfig.configOrder }

        int currentOrder = surveyConfig.configOrder
        List<Integer> configOrders = SurveyConfig.findAllByIdInList(surveyResults.findAll { it.surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION }.groupBy { it.surveyConfig.id }.keySet()).configOrder
        int currentOrderIndex = configOrders.indexOf(currentOrder)

        if (currentOrderIndex > 0) {
            result.prev = SurveyConfig.executeQuery('select sc from SurveyConfig sc where sc.configOrder = :prev and sc.surveyInfo = :surInfo', [prev: configOrders.get(currentOrderIndex - 1), surInfo: surveyInfo])[0]
        }
        if (currentOrderIndex < configOrders.size() - 1) {
            result.next = SurveyConfig.executeQuery('select sc from SurveyConfig sc where sc.configOrder = :next and sc.surveyInfo = :surInfo', [next: configOrders.get(currentOrderIndex + 1), surInfo: surveyInfo])[0]
        }

        result.total = configOrders.size()

        return result

    }

    Map<String, Object> getConfigNavigation(SurveyInfo surveyInfo, SurveyConfig surveyConfig) {
        Map<String, Object> result = [:]
        int currentOrder = surveyConfig.configOrder
        List<Integer> configOrders = surveyInfo.surveyConfigs?.sort { it.configOrder }.configOrder
        int currentOrderIndex = configOrders.indexOf(currentOrder)

        if (currentOrderIndex > 0) {
            result.prev = SurveyConfig.executeQuery('select sc from SurveyConfig sc where sc.configOrder = :prev and sc.surveyInfo = :surInfo', [prev: configOrders.get(currentOrderIndex - 1), surInfo: surveyInfo])[0]
        }
        if (currentOrderIndex < configOrders.size() - 1) {
            result.next = SurveyConfig.executeQuery('select sc from SurveyConfig sc where sc.configOrder = :next and sc.surveyInfo = :surInfo', [next: configOrders.get(currentOrderIndex + 1), surInfo: surveyInfo])[0]
        }

        result.total = configOrders.size()

        return result

    }

    boolean isContinueToParticipate(Org org, SurveyConfig surveyConfig) {
        def participationProperty = RDStore.SURVEY_PROPERTY_PARTICIPATION

        def result = SurveyResult.findBySurveyConfigAndParticipantAndType(surveyConfig, org, participationProperty)?.getResult() == RDStore.YN_YES ? true : false

        return result
    }

    private boolean save(obj, flash) {
        if (obj.save(flush: true)) {
            log.debug("Save ${obj} ok")
            return true
        } else {
            log.error("Problem saving ${obj.errors}")
            Object[] args = [obj]
            flash.error += messageSource.getMessage('default.save.error.message', args, LocaleContextHolder.getLocale())
            return false
        }
    }

    def exportSurveys(List<SurveyConfig> surveyConfigs, Org contextOrg) {
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()

        Map sheetData = [:]

        surveyConfigs.each { surveyConfig ->
            List titles = []
            List surveyData = []

            boolean exportForSurveyOwner = (surveyConfig.surveyInfo.owner.id == contextOrg.id)

            if (exportForSurveyOwner) {
                titles.addAll([messageSource.getMessage('org.sortname.label', null, LocaleContextHolder.getLocale()),
                                messageSource.getMessage('surveyParticipants.label', null, LocaleContextHolder.getLocale())])
                if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION || surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_ISSUE_ENTITLEMENT) {
                    titles.push(messageSource.getMessage('surveyProperty.subName', null, LocaleContextHolder.getLocale()))
                }
                if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_GENERAL_SURVEY) {
                    titles.addAll([messageSource.getMessage('surveyInfo.name.label', null, LocaleContextHolder.getLocale())])
                }

                titles.addAll([messageSource.getMessage('surveyConfig.url.label', null, LocaleContextHolder.getLocale()),
                        messageSource.getMessage('surveyConfig.url2.label', null, LocaleContextHolder.getLocale()),
                        messageSource.getMessage('surveyConfig.url3.label', null, LocaleContextHolder.getLocale()),
                        messageSource.getMessage('surveyConfigsInfo.comment', null, LocaleContextHolder.getLocale())])

                if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION || surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_ISSUE_ENTITLEMENT) {
                    titles.addAll([messageSource.getMessage('surveyProperty.subProvider', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('surveyProperty.subAgency', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('license.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('subscription.packages.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('default.status.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('subscription.kind.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('subscription.form.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('subscription.resource.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('subscription.isPublicForApi.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('subscription.hasPerpetualAccess.label', null, LocaleContextHolder.getLocale())])

                    if (surveyConfig.subSurveyUseForTransfer) {
                        titles.addAll([messageSource.getMessage('surveyConfigsInfo.newPrice', null, LocaleContextHolder.getLocale()),
                                       messageSource.getMessage('financials.billingCurrency', null, LocaleContextHolder.getLocale()),
                                       messageSource.getMessage('surveyConfigsInfo.newPrice.comment', null, LocaleContextHolder.getLocale())])
                    }
                }

                surveyConfig.surveyProperties.each {
                    titles.addAll([messageSource.getMessage('surveyProperty.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('default.type.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('surveyResult.result', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('surveyResult.comment', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('surveyResult.commentOnlyForOwner', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('surveyResult.finishDate', null, LocaleContextHolder.getLocale())])
                }

            } else {
                titles.push(messageSource.getMessage('surveyInfo.owner.label', null, LocaleContextHolder.getLocale()))
                titles.push(messageSource.getMessage('surveyConfigsInfo.comment', null, LocaleContextHolder.getLocale()))
                if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION || surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_ISSUE_ENTITLEMENT) {
                    titles.addAll([messageSource.getMessage('surveyConfig.url.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('surveyConfig.url2.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('surveyConfig.url3.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('surveyConfigsInfo.comment', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('surveyProperty.subName', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('surveyProperty.subProvider', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('surveyProperty.subAgency', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('license.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('subscription.packages.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('default.status.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('subscription.kind.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('subscription.form.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('subscription.resource.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('subscription.isPublicForApi.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('subscription.hasPerpetualAccess.label', null, LocaleContextHolder.getLocale())])
                    if (surveyConfig.subSurveyUseForTransfer) {
                        titles.push(messageSource.getMessage('surveyConfigsInfo.newPrice', null, LocaleContextHolder.getLocale()))
                        titles.push(messageSource.getMessage('financials.billingCurrency', null, LocaleContextHolder.getLocale()))
                        titles.push(messageSource.getMessage('surveyConfigsInfo.newPrice.comment', null, LocaleContextHolder.getLocale()))
                    }
                }
                if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_GENERAL_SURVEY) {
                    titles.push(messageSource.getMessage('surveyInfo.name.label', null, LocaleContextHolder.getLocale()))
                    titles.push(messageSource.getMessage('surveyConfig.url.label', null, LocaleContextHolder.getLocale()))
                    titles.push(messageSource.getMessage('surveyConfig.url2.label', null, LocaleContextHolder.getLocale()))
                    titles.push(messageSource.getMessage('surveyConfig.url3.label', null, LocaleContextHolder.getLocale()))
                }
            }

            Subscription subscription

            if (exportForSurveyOwner) {
                String surveyName = surveyConfig.getConfigNameShort()
                surveyConfig.orgs.sort{it.org.sortname}.each { surveyOrg ->
                    List row = []

                    row.add([field: surveyOrg.org.sortname ?: '', style: null])
                    row.add([field: surveyOrg.org.name ?: '', style: null])

                    if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION || surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_ISSUE_ENTITLEMENT) {

                        OrgRole orgRole = Subscription.findAllByInstanceOf(surveyConfig.subscription) ? OrgRole.findByOrgAndRoleTypeAndSubInList(surveyOrg.org, RDStore.OR_SUBSCRIBER_CONS, Subscription.findAllByInstanceOf(surveyConfig.subscription)) : null
                        subscription =  orgRole ? orgRole.sub : null
                        row.add([field: subscription?.name ?: surveyName ?: '', style: null])
                    }
                    if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_GENERAL_SURVEY) {
                        row.add([field: surveyName ?: '', style: null])
                    }
                    row.add([field: surveyConfig.url ?: '', style: null])
                    row.add([field: surveyConfig.url2 ?: '', style: null])
                    row.add([field: surveyConfig.url3 ?: '', style: null])
                    row.add([field: surveyConfig.comment ?: '', style: null])

                    if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION || surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_ISSUE_ENTITLEMENT) {
                        //Performance lastig providers und agencies
                        row.add([field: subscription?.providers ? subscription?.providers?.join(", ") : '', style: null])
                        row.add([field: subscription?.agencies ? subscription?.agencies?.join(", ") : '', style: null])

                        List licenseNames = []
                        Links.findAllByDestinationAndLinkType(GenericOIDService.getOID(subscription),RDStore.LINKTYPE_LICENSE).each { Links li ->
                            License l = genericOIDService.resolveOID(li.source)
                            licenseNames << l.reference
                        }
                        row.add([field: licenseNames ? licenseNames.join(", ") : '', style: null])
                        List packageNames = subscription?.packages?.collect {
                            it.pkg.name
                        }
                        row.add([field: packageNames ? packageNames.join(", ") : '', style: null])

                        row.add([field: subscription?.status?.getI10n("value") ?: '', style: null])
                        row.add([field: subscription?.kind?.getI10n("value") ?: '', style: null])
                        row.add([field: subscription?.form?.getI10n("value") ?: '', style: null])
                        row.add([field: subscription?.resource?.getI10n("value") ?: '', style: null])
                        row.add([field: subscription?.isPublicForApi ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"), style: null])
                        row.add([field: subscription?.hasPerpetualAccess ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"), style: null])

                        if (surveyConfig.subSurveyUseForTransfer) {
                            CostItem surveyCostItem = CostItem.findBySurveyOrgAndCostItemStatusNotEqual(SurveyOrg.findBySurveyConfigAndOrg(surveyConfig, surveyOrg.org), RDStore.COST_ITEM_DELETED)

                            row.add([field: surveyCostItem?.costInBillingCurrencyAfterTax ?: '', style: null])
                            row.add([field: surveyCostItem?.billingCurrency?.value ?: '', style: null])
                            row.add([field: surveyCostItem?.costDescription ?: '', style: null])
                        }
                    }

                    SurveyResult.findAllBySurveyConfigAndParticipant(surveyConfig, surveyOrg.org).sort{it.type.name}.each { surResult ->
                        row.add([field: surResult.type?.getI10n('name') ?: '', style: null])
                        row.add([field: PropertyDefinition.getLocalizedValue(surResult.type.type) ?: '', style: null])

                        String value = ""

                        if (surResult.type.type == Integer.toString()) {
                            value = surResult?.intValue ? surResult.intValue.toString() : ""
                        } else if (surResult.type.type == String.toString()) {
                            value = surResult.stringValue ?: ""
                        } else if (surResult.type.type == BigDecimal.toString()) {
                            value = surResult.decValue ? surResult.decValue.toString() : ""
                        } else if (surResult.type.type == Date.toString()) {
                            value = surResult.dateValue ? sdf.format(surResult.dateValue) : ""
                        } else if (surResult.type.type == URL.toString()) {
                            value = surResult.urlValue ? surResult.urlValue.toString() : ""
                        } else if (surResult.type.type == RefdataValue.toString()) {
                            value = surResult.refValue ? surResult.refValue.getI10n('value') : ""
                        }

                        row.add([field: value ?: '', style: null])
                        row.add([field: surResult.comment ?: '', style: null])
                        row.add([field: surResult.ownerComment ?: '', style: null])
                        row.add([field: surResult.finishDate ? sdf.format(surResult.finishDate) : '', style: null])


                    }
                    surveyData.add(row)
                }
            } else {

                List row = []

                row.add([field: surveyConfig.surveyInfo.owner.name ?: '', style: null])
                row.add([field: surveyConfig.comment ?: '', style: null])

                if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION || surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_ISSUE_ENTITLEMENT) {
                    row.add([field: surveyConfig.url ?: '', style: null])
                    row.add([field: surveyConfig.url2 ?: '', style: null])
                    row.add([field: surveyConfig.url3 ?: '', style: null])
                    subscription = surveyConfig.subscription.getDerivedSubscriptionBySubscribers(contextOrg) ?: null
                    row.add([field: subscription?.name ?: surveyConfig.getConfigNameShort() ?: "", style: null])
                    row.add([field: subscription?.providers ? subscription?.providers?.join(", ") : '', style: null])
                    row.add([field: subscription?.agencies ? subscription?.agencies?.join(", ") : '', style: null])

                    List licenseNames = []
                    Links.findAllByDestinationAndLinkType(GenericOIDService.getOID(subscription),RDStore.LINKTYPE_LICENSE).each { Links li ->
                        License l = genericOIDService.resolveOID(li.source)
                        licenseNames << l.reference
                    }
                    row.add([field: licenseNames ? licenseNames.join(", ") : '', style: null])
                    List packageNames = subscription?.packages?.collect {
                        it.pkg.name
                    }
                    row.add([field: packageNames ? packageNames.join(", ") : '', style: null])
                    row.add([field: subscription?.status?.getI10n("value") ?: '', style: null])
                    row.add([field: subscription?.kind?.getI10n("value") ?: '', style: null])
                    row.add([field: subscription?.form?.getI10n("value") ?: '', style: null])
                    row.add([field: subscription?.resource?.getI10n("value") ?: '', style: null])
                    row.add([field: subscription?.isPublicForApi ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"), style: null])
                    row.add([field: subscription?.hasPerpetualAccess ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"), style: null])

                    if (surveyConfig.subSurveyUseForTransfer) {
                        CostItem surveyCostItem = CostItem.findBySurveyOrgAndCostItemStatusNotEqual(SurveyOrg.findBySurveyConfigAndOrg(surveyConfig, contextOrg), RDStore.COST_ITEM_DELETED)

                        row.add([field: surveyCostItem?.costInBillingCurrencyAfterTax ?: '', style: null])
                        row.add([field: surveyCostItem?.billingCurrency?.value ?: '', style: null])
                        row.add([field: surveyCostItem?.costDescription ?: '', style: null])
                    }
                }

                if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_GENERAL_SURVEY) {
                    row.add([field: surveyConfig.getConfigNameShort() ?: '', style: null])
                    row.add([field: surveyConfig.url ?: '', style: null])
                    row.add([field: surveyConfig.url2 ?: '', style: null])
                    row.add([field: surveyConfig.url3 ?: '', style: null])
                }

                surveyData.add(row)
                surveyData.add([])
                surveyData.add([])
                surveyData.add([])
                List row2 = [[field: messageSource.getMessage('surveyProperty.label', null, LocaleContextHolder.getLocale()), style: 'bold'],
                             [field: messageSource.getMessage('default.type.label', null, LocaleContextHolder.getLocale()), style: 'bold'],
                             [field: messageSource.getMessage('surveyResult.result', null, LocaleContextHolder.getLocale()), style: 'bold'],
                             [field: messageSource.getMessage('surveyResult.comment', null, LocaleContextHolder.getLocale()), style: 'bold'],
                             [field: messageSource.getMessage('surveyResult.commentOnlyForParticipant', null, LocaleContextHolder.getLocale()), style: 'bold'],
                             [field: messageSource.getMessage('surveyResult.finishDate', null, LocaleContextHolder.getLocale()), style: 'bold']]
                surveyData.add(row2)


                SurveyResult.findAllBySurveyConfigAndParticipant(surveyConfig, contextOrg).sort{it.type.name}.each { surResult ->
                    List row3 = []
                    row3.add([field: surResult.type?.getI10n('name') ?: '', style: null])
                    row3.add([field: PropertyDefinition.getLocalizedValue(surResult.type.type) ?: '', style: null])

                    String value = ""

                    if (surResult.type.type == Integer.toString()) {
                        value = surResult?.intValue ? surResult.intValue.toString() : ""
                    } else if (surResult.type.type == String.toString()) {
                        value = surResult.stringValue ?: ""
                    } else if (surResult.type.type == BigDecimal.toString()) {
                        value = surResult.decValue ? surResult.decValue.toString() : ""
                    } else if (surResult.type.type == Date.toString()) {
                        value = surResult.dateValue ? sdf.format(surResult.dateValue) : ""
                    } else if (surResult.type.type == URL.toString()) {
                        value = surResult.urlValue ? surResult.urlValue.toString() : ""
                    } else if (surResult.type.type == RefdataValue.toString()) {
                        value = surResult.refValue ? surResult.refValue.getI10n('value') : ""
                    }

                    row3.add([field: value ?: '', style: null])
                    row3.add([field: surResult.comment ?: '', style: null])
                    row3.add([field: surResult.participantComment ?: '', style: null])
                    row3.add([field: surResult.finishDate ? sdf.format(surResult.finishDate) : '', style: null])

                    surveyData.add(row3)
                }
            }
            sheetData.put(escapeService.escapeString(surveyConfig.getConfigNameShort()), [titleRow: titles, columnData: surveyData])
        }

        return exportService.generateXLSXWorkbook(sheetData)
    }

    def exportSurveyCostItems(List<SurveyConfig> surveyConfigs, Org contextOrg) {
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()

        Map sheetData = [:]

        if (contextOrg.getCustomerType()  == 'ORG_CONSORTIUM') {
            surveyConfigs.each { surveyConfig ->
                List titles = []
                List surveyData = []

                titles.addAll([messageSource.getMessage('org.sortname.label', null, LocaleContextHolder.getLocale()),
                               messageSource.getMessage('surveyParticipants.label', null, LocaleContextHolder.getLocale())])
                if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION || surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_ISSUE_ENTITLEMENT ) {
                    titles.push(messageSource.getMessage('surveyProperty.subName', null, LocaleContextHolder.getLocale()))
                }
                if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_GENERAL_SURVEY) {
                    titles.addAll([messageSource.getMessage('surveyInfo.name.label', null, LocaleContextHolder.getLocale())])
                }

                titles.addAll([messageSource.getMessage('surveyConfig.url.label', null, LocaleContextHolder.getLocale()),
                               messageSource.getMessage('surveyConfig.url2.label', null, LocaleContextHolder.getLocale()),
                               messageSource.getMessage('surveyConfig.url3.label', null, LocaleContextHolder.getLocale())])

                if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION || surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_ISSUE_ENTITLEMENT ) {
                    titles.addAll([messageSource.getMessage('surveyProperty.subProvider', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('surveyProperty.subAgency', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('default.status.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('financials.costItemElement', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('financials.costInBillingCurrency', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('financials.billingCurrency', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('financials.newCosts.taxTypeAndRate', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('financials.costInBillingCurrencyAfterTax', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('default.startDate.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('default.endDate.label', null, LocaleContextHolder.getLocale()),
                                   messageSource.getMessage('surveyConfigsInfo.newPrice.comment', null, LocaleContextHolder.getLocale())])
                }

                Subscription subscription

                String surveyName = surveyConfig.getConfigNameShort()
                surveyConfig.orgs.sort { it.org.sortname }.each { surveyOrg ->
                    List row = []

                    row.add([field: surveyOrg.org.sortname ?: '', style: null])
                    row.add([field: surveyOrg.org.name ?: '', style: null])

                    if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION || surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_ISSUE_ENTITLEMENT) {

                        OrgRole orgRole = Subscription.findAllByInstanceOf(surveyConfig.subscription) ? OrgRole.findByOrgAndRoleTypeAndSubInList(surveyOrg.org, RDStore.OR_SUBSCRIBER_CONS, Subscription.findAllByInstanceOf(surveyConfig.subscription)) : null
                        subscription =  orgRole ? orgRole.sub : null
                        row.add([field: subscription?.name ?: surveyName ?: '', style: null])
                    }
                    if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_GENERAL_SURVEY) {
                        row.add([field: surveyName ?: '', style: null])
                    }

                    row.add([field: surveyConfig.url ?: '', style: null])
                    row.add([field: surveyConfig.url2 ?: '', style: null])
                    row.add([field: surveyConfig.url3 ?: '', style: null])

                    if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION || surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_ISSUE_ENTITLEMENT) {
                        row.add([field: subscription?.providers ? subscription?.providers?.join(", ") : '', style: null])
                        row.add([field: subscription?.agencies ? subscription?.agencies?.join(", ") : '', style: null])

                        row.add([field: subscription?.status?.getI10n("value") ?: '', style: null])

                        CostItem surveyCostItem = CostItem.findBySurveyOrgAndCostItemStatusNotEqual(SurveyOrg.findBySurveyConfigAndOrg(surveyConfig, surveyOrg.org), RDStore.COST_ITEM_DELETED)

                        if (surveyCostItem) {
                            row.add([field: surveyCostItem?.costItemElement?.getI10n('value') ?: '', style: null])
                            row.add([field: surveyCostItem?.costInBillingCurrency ?: '', style: null])
                            row.add([field: surveyCostItem?.billingCurrency?.value ?: '', style: null])
                            row.add([field: surveyCostItem?.taxKey ? surveyCostItem.taxKey.taxType?.getI10n("value") + " (" + surveyCostItem.taxKey.taxRate + "%)" : '', style: null])
                            row.add([field: surveyCostItem?.costInBillingCurrencyAfterTax ?: '', style: null])
                            row.add([field: surveyCostItem?.startDate ? formatter.format(surveyCostItem.startDate): '', style: null])
                            row.add([field: surveyCostItem?.endDate ? formatter.format(surveyCostItem.endDate): '', style: null])
                            row.add([field: surveyCostItem?.costDescription ?: '', style: null])
                        }
                    }

                    surveyData.add(row)
                    sheetData.put(escapeService.escapeString(surveyConfig.getConfigNameShort()), [titleRow: titles, columnData: surveyData])
                }
            }
        } else {
            List titles = []
            List surveyData = []

            titles.addAll([messageSource.getMessage('surveyInfo.owner.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyConfig.url.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyConfig.url2.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyConfig.url3.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyInfo.name.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyInfo.type.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyProperty.subProvider', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyProperty.subAgency', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('default.status.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('financials.costItemElement', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('financials.costInBillingCurrency', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('financials.billingCurrency', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('financials.newCosts.taxTypeAndRate', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('financials.costInBillingCurrencyAfterTax', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('default.startDate.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('default.endDate.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyConfigsInfo.newPrice.comment', null, LocaleContextHolder.getLocale())])


            surveyConfigs.each { surveyConfig ->

                List row = []
                Subscription subscription

                String surveyName = surveyConfig.getConfigNameShort()

                row.add([field: surveyConfig.surveyInfo.owner.name ?: '', style: null])
                row.add([field: surveyConfig.url ?: '', style: null])
                row.add([field: surveyConfig.url2 ?: '', style: null])
                row.add([field: surveyConfig.url3 ?: '', style: null])
                row.add([field: surveyName ?: '', style: null])
                row.add([field: surveyConfig.surveyInfo.type?.getI10n('value') ?: '', style: null])

                if (surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION || surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_ISSUE_ENTITLEMENT) {
                    subscription = surveyConfig.subscription.getDerivedSubscriptionBySubscribers(contextOrg) ?: null
                    row.add([field: subscription?.providers ? subscription?.providers?.join(", ") : '', style: null])
                    row.add([field: subscription?.agencies ? subscription?.agencies?.join(", ") : '', style: null])

                    row.add([field: subscription?.status?.getI10n("value") ?: '', style: null])

                    CostItem surveyCostItem = CostItem.findBySurveyOrgAndCostItemStatusNotEqual(SurveyOrg.findBySurveyConfigAndOrg(surveyConfig, contextOrg), RDStore.COST_ITEM_DELETED)

                    if (surveyCostItem) {
                        row.add([field: surveyCostItem?.costItemElement?.getI10n('value') ?: '', style: null])
                        row.add([field: surveyCostItem?.costInBillingCurrency ?: '', style: null])
                        row.add([field: surveyCostItem?.billingCurrency?.value ?: '', style: null])
                        row.add([field: surveyCostItem?.taxKey ? surveyCostItem.taxKey.taxType?.getI10n("value") + " (" + surveyCostItem.taxKey.taxRate + "%)" : '', style: null])
                        row.add([field: surveyCostItem?.costInBillingCurrencyAfterTax ?: '', style: null])
                        row.add([field: surveyCostItem?.startDate ? formatter.format(surveyCostItem.startDate) : '', style: null])
                        row.add([field: surveyCostItem?.endDate ? formatter.format(surveyCostItem.endDate) : '', style: null])
                        row.add([field: surveyCostItem?.costDescription ?: '', style: null])
                    }
                }
                surveyData.add(row)
            }

            sheetData.put(escapeService.escapeString(messageSource.getMessage('survey.exportSurveyCostItems', null, LocaleContextHolder.getLocale())), [titleRow: titles, columnData: surveyData])

        }

        return exportService.generateXLSXWorkbook(sheetData)
    }

    def emailToSurveyOwnerbyParticipationFinish(SurveyInfo surveyInfo, Org participationFinish){

        if (grailsApplication.config.grails.mail.disabled == true) {
            println 'surveyService.emailToSurveyOwnerbyParticipationFinish() failed due grailsApplication.config.grails.mail.disabled = true'
            return false
        }

        if(surveyInfo.owner)
        {
            //Only User that approved
            List<UserOrg> userOrgs = UserOrg.findAllByOrgAndStatus(surveyInfo.owner, 1)

            //Only User with Notification by Email and for Surveys Start
            userOrgs.each { userOrg ->
                if(userOrg.user.getSettingsValue(UserSettings.KEYS.IS_NOTIFICATION_FOR_SURVEYS_PARTICIPATION_FINISH) == RDStore.YN_YES &&
                        userOrg.user.getSettingsValue(UserSettings.KEYS.IS_NOTIFICATION_BY_EMAIL) == RDStore.YN_YES)
                {

                    User user = userOrg.user
                    Locale language = new Locale(user.getSetting(UserSettings.KEYS.LANGUAGE_OF_EMAILS, RefdataValue.getByValueAndCategory('de', de.laser.helper.RDConstants.LANGUAGE)).value.toString())
                    String emailReceiver = user.getEmail()
                    String currentServer = ServerUtils.getCurrentServer()
                    String subjectSystemPraefix = (currentServer == ServerUtils.SERVER_PROD)? "LAS:eR - " : (ConfigUtils.getLaserSystemId() + " - ")
                    String mailSubject = escapeService.replaceUmlaute(subjectSystemPraefix + surveyInfo.type.getI10n('value') + ": " + messageSource.getMessage('email.subject.surveysParticipationFinish', null, language) +  " (" + participationFinish.sortname + ")")

                        try {
                            if (emailReceiver == null || emailReceiver.isEmpty()) {
                                log.debug("The following user does not have an email address and can not be informed about surveys: " + user.username);
                            } else {
                                boolean isNotificationCCbyEmail = user.getSetting(UserSettings.KEYS.IS_NOTIFICATION_CC_BY_EMAIL, RDStore.YN_NO)?.rdValue == RDStore.YN_YES
                                String ccAddress = null
                                if (isNotificationCCbyEmail){
                                    ccAddress = user.getSetting(UserSettings.KEYS.NOTIFICATION_CC_EMAILADDRESS, null)?.getValue()
                                }

                                List surveyResults = SurveyResult.findAllByParticipantAndSurveyConfig(participationFinish, surveyInfo.surveyConfigs[0]).sort { it.surveyConfig.configOrder }

                                if (isNotificationCCbyEmail && ccAddress) {
                                    mailService.sendMail {
                                        to      emailReceiver
                                        from    from
                                        cc      ccAddress
                                        subject mailSubject
                                        html    (view: "/mailTemplates/html/notificationSurveyParticipationFinish", model: [user: user, org: participationFinish, survey: surveyInfo, surveyResults: surveyResults])
                                    }
                                } else {
                                    mailService.sendMail {
                                        to      emailReceiver
                                        from from
                                        subject mailSubject
                                        html    (view: "/mailTemplates/html/notificationSurveyParticipationFinish", model: [user: user, org: participationFinish, survey: surveyInfo, surveyResults: surveyResults])
                                    }
                                }

                                log.debug("emailToSurveyOwnerbyParticipationFinish - finished sendSurveyEmail() to " + user.displayName + " (" + user.email + ") " + surveyInfo.owner.name);
                            }
                        } catch (Exception e) {
                            String eMsg = e.message

                            log.error("emailToSurveyOwnerbyParticipationFinish - sendSurveyEmail() :: Unable to perform email due to exception ${eMsg}")
                            SystemEvent.createEvent('SUS_SEND_MAIL_ERROR', [user: user.getDisplayName(), org: participationFinish.name, survey: surveyInfo.name])
                        }
                }
            }

        }

    }

    def exportSurveysOfParticipant(List surveyConfigs, Org participant) {
        SimpleDateFormat sdf = DateUtil.getSDF_NoTime()

        Map sheetData = [:]
            List titles = []
            List surveyData = []

            titles.addAll([messageSource.getMessage('org.sortname.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyParticipants.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyInfo.name.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyConfig.url.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyConfig.url2.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyConfig.url3.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyConfigsInfo.comment', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyProperty.subProvider', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyProperty.subAgency', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('license.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('subscription.packages.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('default.status.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('subscription.kind.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('subscription.form.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('subscription.resource.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('subscription.isPublicForApi.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('subscription.hasPerpetualAccess.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyConfigsInfo.newPrice', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('financials.billingCurrency', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyConfigsInfo.newPrice.comment', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyProperty.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('default.type.label', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyResult.result', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyResult.comment', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyResult.commentOnlyForOwner', null, LocaleContextHolder.getLocale()),
                           messageSource.getMessage('surveyResult.finishDate', null, LocaleContextHolder.getLocale())])

            List<SurveyResult> surveyResults = SurveyResult.findAllByParticipantAndSurveyConfigInList(participant, surveyConfigs)
            surveyResults.each { surveyResult ->

                    Subscription subscription
                    String surveyName = surveyResult.surveyConfig.getConfigNameShort()
                        List row = []

                        row.add([field: surveyResult.participant.sortname ?: '', style: null])
                        row.add([field: surveyResult.participant.name ?: '', style: null])

                        if (surveyResult.surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION || surveyResult.surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_ISSUE_ENTITLEMENT) {

                            OrgRole orgRole = Subscription.findAllByInstanceOf(surveyResult.surveyConfig.subscription) ? OrgRole.findByOrgAndRoleTypeAndSubInList(participant, RDStore.OR_SUBSCRIBER_CONS, Subscription.findAllByInstanceOf(surveyResult.surveyConfig.subscription)) : null
                            subscription =  orgRole ? orgRole.sub : null
                            row.add([field: subscription?.name ?: surveyName ?: '', style: null])
                        }
                        if (surveyResult.surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_GENERAL_SURVEY) {
                            row.add([field: surveyName ?: '', style: null])

                        }
                        row.add([field: surveyResult.surveyConfig.url ?: '', style: null])
                        row.add([field: surveyResult.surveyConfig.url2 ?: '', style: null])
                        row.add([field: surveyResult.surveyConfig.url3 ?: '', style: null])
                        row.add([field: surveyResult.surveyConfig.comment ?: '', style: null])

                        if (surveyResult.surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_SUBSCRIPTION || surveyResult.surveyConfig.type == SurveyConfig.SURVEY_CONFIG_TYPE_ISSUE_ENTITLEMENT) {
                            //Performance lastig providers und agencies
                            row.add([field: subscription?.providers ? subscription?.providers?.join(", ") : '', style: null])
                            row.add([field: subscription?.agencies ? subscription?.agencies?.join(", ") : '', style: null])

                            List licenseNames = []
                            Links.findAllByDestinationAndLinkType(GenericOIDService.getOID(subscription),RDStore.LINKTYPE_LICENSE).each { Links li ->
                                License l = genericOIDService.resolveOID(li.source)
                                licenseNames << l.reference
                            }
                            row.add([field: licenseNames ? licenseNames.join(", ") : '', style: null])

                            List packageNames = subscription?.packages?.collect {
                                it.pkg.name
                            }
                            row.add([field: packageNames ? packageNames.join(", ") : '', style: null])

                            row.add([field: subscription?.status?.getI10n("value") ?: '', style: null])
                            row.add([field: subscription?.kind?.getI10n("value") ?: '', style: null])
                            row.add([field: subscription?.form?.getI10n("value") ?: '', style: null])
                            row.add([field: subscription?.resource?.getI10n("value") ?: '', style: null])
                            row.add([field: subscription?.isPublicForApi ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"), style: null])
                            row.add([field: subscription?.hasPerpetualAccess ? RDStore.YN_YES.getI10n("value") : RDStore.YN_NO.getI10n("value"), style: null])

                                CostItem surveyCostItem = CostItem.findBySurveyOrgAndCostItemStatusNotEqual(SurveyOrg.findBySurveyConfigAndOrg(surveyResult.surveyConfig, participant), RDStore.COST_ITEM_DELETED)

                                row.add([field: surveyCostItem?.costInBillingCurrencyAfterTax ?: '', style: null])
                                row.add([field: surveyCostItem?.billingCurrency?.value ?: '', style: null])
                                row.add([field: surveyCostItem?.costDescription ?: '', style: null])

                        }else {
                            row.add([field: '', style: null])
                            row.add([field: '', style: null])
                            row.add([field: '', style: null])
                            row.add([field: '', style: null])
                            row.add([field: '', style: null])
                            row.add([field: '', style: null])
                            row.add([field: '', style: null])
                            row.add([field: '', style: null])
                            row.add([field: '', style: null])
                            row.add([field: '', style: null])
                        }

                            row.add([field: surveyResult.type?.getI10n('name') ?: '', style: null])
                            row.add([field: PropertyDefinition.getLocalizedValue(surveyResult.type.type) ?: '', style: null])

                            String value = ""

                            if (surveyResult.type.type == Integer.toString()) {
                                value = surveyResult?.intValue ? surveyResult.intValue.toString() : ""
                            } else if (surveyResult.type.type == String.toString()) {
                                value = surveyResult.stringValue ?: ""
                            } else if (surveyResult.type.type == BigDecimal.toString()) {
                                value = surveyResult.decValue ? surveyResult.decValue.toString() : ""
                            } else if (surveyResult.type.type == Date.toString()) {
                                value = surveyResult.dateValue ? sdf.format(surveyResult.dateValue) : ""
                            } else if (surveyResult.type.type == URL.toString()) {
                                value = surveyResult.urlValue ? surveyResult.urlValue.toString() : ""
                            } else if (surveyResult.type.type == RefdataValue.toString()) {
                                value = surveyResult.refValue ? surveyResult.refValue.getI10n('value') : ""
                            }

                            row.add([field: value ?: '', style: null])
                            row.add([field: surveyResult.comment ?: '', style: null])
                            row.add([field: surveyResult.ownerComment ?: '', style: null])
                            row.add([field: surveyResult.finishDate ? sdf.format(surveyResult.finishDate) : '', style: null])



                        surveyData.add(row)


                sheetData.put(escapeService.escapeString(messageSource.getMessage('surveyInfo.members', null, LocaleContextHolder.getLocale())), [titleRow: titles, columnData: surveyData])
            }


        return exportService.generateXLSXWorkbook(sheetData)
    }

    def emailsToSurveyUsers(List surveyInfoIds){

        def surveys = SurveyInfo.findAllByIdInList(surveyInfoIds)

        def orgs = surveys?.surveyConfigs?.orgs?.org?.flatten()

        if(orgs)
        {
            //Only User that approved
            List<UserOrg> userOrgs = UserOrg.findAllByOrgInListAndStatus(orgs, 1)

            //Only User with Notification by Email and for Surveys Start
            userOrgs.each { userOrg ->
                if(userOrg.user.getSettingsValue(UserSettings.KEYS.IS_NOTIFICATION_FOR_SURVEYS_START) == RDStore.YN_YES &&
                        userOrg.user.getSettingsValue(UserSettings.KEYS.IS_NOTIFICATION_BY_EMAIL) == RDStore.YN_YES)
                {

                    def orgSurveys = SurveyInfo.executeQuery("SELECT s FROM SurveyInfo s " +
                            "LEFT JOIN s.surveyConfigs surConf " +
                            "LEFT JOIN surConf.orgs surOrg  " +
                            "WHERE surOrg.org IN (:org) " +
                            "AND s.id IN (:survey)", [org: userOrg.org, survey: surveys?.id])

                    sendSurveyEmail(userOrg.user, userOrg.org, orgSurveys, false)
                }
            }

        }

    }

    def emailsToSurveyUsersOfOrg(SurveyInfo surveyInfo, Org org, boolean reminderMail){

        //Only User that approved
        List<UserOrg> userOrgs = UserOrg.findAllByOrgAndStatus(org, UserOrg.STATUS_APPROVED)

        //Only User with Notification by Email and for Surveys Start
        userOrgs.each { userOrg ->
            if(userOrg.user.getSettingsValue(UserSettings.KEYS.IS_NOTIFICATION_FOR_SURVEYS_START) == RDStore.YN_YES &&
                    userOrg.user.getSettingsValue(UserSettings.KEYS.IS_NOTIFICATION_BY_EMAIL) == RDStore.YN_YES)
            {
                sendSurveyEmail(userOrg.user, userOrg.org, [surveyInfo], reminderMail)
            }
        }
    }

    private void sendSurveyEmail(User user, Org org, List<SurveyInfo> surveyEntries, boolean reminderMail) {

        if (grailsApplication.config.grails.mail.disabled == true) {
            println 'SurveyService.sendSurveyEmail() failed due grailsApplication.config.grails.mail.disabled = true'
        }else {

            String emailReceiver = user.getEmail()
            String currentServer = ServerUtils.getCurrentServer()
            String subjectSystemPraefix = (currentServer == ServerUtils.SERVER_PROD) ? "LAS:eR - " : (ConfigUtils.getLaserSystemId() + " - ")

            surveyEntries.each { survey ->
                try {
                    if (emailReceiver == null || emailReceiver.isEmpty()) {
                        log.debug("The following user does not have an email address and can not be informed about surveys: " + user.username);
                    } else {
                        boolean isNotificationCCbyEmail = user.getSetting(UserSettings.KEYS.IS_NOTIFICATION_CC_BY_EMAIL, RDStore.YN_NO)?.rdValue == RDStore.YN_YES
                        String ccAddress = null
                        if (isNotificationCCbyEmail) {
                            ccAddress = user.getSetting(UserSettings.KEYS.NOTIFICATION_CC_EMAILADDRESS, null)?.getValue()
                        }

                        List generalContactsEMails = []

                        survey.owner.getGeneralContactPersons(true)?.each { person ->
                            person.contacts.each { contact ->
                                if (['Mail', 'E-Mail'].contains(contact.contentType?.value)) {
                                    generalContactsEMails << contact.content
                                }
                            }
                        }

                        replyTo = (generalContactsEMails.size() > 0) ? generalContactsEMails[0].toString() : null
                        Object[] args = ["${survey.type.getI10n('value')}"]
                        Locale language = new Locale(user.getSetting(UserSettings.KEYS.LANGUAGE_OF_EMAILS, RefdataValue.getByValueAndCategory('de', de.laser.helper.RDConstants.LANGUAGE)).value.toString())

                        String mailSubject = escapeService.replaceUmlaute(subjectSystemPraefix + (reminderMail ? messageSource.getMessage('email.subject.surveysReminder', args, language)  : messageSource.getMessage('email.subject.surveys', args, language)) + " (" + survey.name + ")")

                        if (isNotificationCCbyEmail && ccAddress) {
                            mailService.sendMail {
                                to emailReceiver
                                from from
                                cc ccAddress
                                replyTo replyTo
                                subject mailSubject
                                body(view: "/mailTemplates/text/notificationSurvey", model: [user: user, org: org, survey: survey])
                            }
                        } else {
                            mailService.sendMail {
                                to emailReceiver
                                from from
                                replyTo replyTo
                                subject mailSubject
                                body(view: "/mailTemplates/text/notificationSurvey", model: [user: user, org: org, survey: survey])
                            }
                        }

                        log.debug("SurveyService - finished sendSurveyEmail() to " + user.displayName + " (" + user.email + ") " + org.name);
                    }
                } catch (Exception e) {
                    String eMsg = e.message

                    log.error("SurveyService - sendSurveyEmail() :: Unable to perform email due to exception ${eMsg}")
                    SystemEvent.createEvent('SUS_SEND_MAIL_ERROR', [user: user.getDisplayName(), org: org.name, survey: survey.name])
                }
            }
        }
    }
}
