
<g:set var="actions_needed" value="false"/>

<g:each in="${conflicts_list}" var="conflict_item">
    <ul>
        <g:each in="${conflict_item.details}" var="detail_item">
            <li>
                <strong>
                    <g:if test="${detail_item.number}">
                        <span>${detail_item.number}</span>&nbsp
                    </g:if>
                    <g:if test="${detail_item.link}">
                        <a href="${detail_item.link}">${detail_item.text}</a>
                    </g:if>
                    <g:else>
                        ${detail_item.text}
                    </g:else>
                </strong>${conflict_item.action.text}
            </li>
        </g:each>
        <g:if test="${conflict_item.action.actionRequired}">
            <i class="fa fa-times-circle"></i>
            <g:set var="actions_needed" value="true"/>

        </g:if>
        <g:else>
            <i class="fa fa-check-circle"></i>
        </g:else>
    </ul>
</g:each>