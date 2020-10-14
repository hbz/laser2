<%@ page import="com.k_int.kbplus.JasperReportsController; org.jasper.JasperExportFormat" contentType="text/html;charset=UTF-8" %>
<html>
<r:require module="scaffolding" />
<head>
    <title>${message(code:'laser')} ${message(code:'jasper.reports.label')}</title>
    <meta name="layout" content="semanticUI"/>
</head>

<body>

<div>

    <semui:breadcrumbs>
        <semui:crumb message="menu.datamanager.dash" controller="dataManager" action="index"/>
        <semui:crumb message="jasper.reports.label" class="active"/>
    </semui:breadcrumbs>

    <semui:messages data="${flash}" />

        <div class="inline-lists">
            <dl>
                <dt>${message(code:'jasper.reports.selected')}:</dt>
                <dd>
                    <span>
                        <g:select id="available_reports" name="report_name" from="${available_reports}"/>
                    </span>
                </dd>
                <dt>${message(code:'jasper.reports.format')}:</dt>
                <dd>
                    <span>
                        <g:select id="selectRepFormat" name="rep_format" from="${available_formats}"/>
                    </span>
                </dd>
            </dl>
        </div>

        <div id="report_details">
            <g:render template="report_details" model="params"/>
        </div>
</div>

</body>
<r:script language="JavaScript">

     $(function () {
        var repname = $('#available_reports option:selected').text()
        jQuery.ajax({type:'POST',data:{'report_name': repname}, url:'${createLink(controller: 'jasperReports', action: 'index')}'
        ,success:function(data,textStatus){jQuery('#report_details').html(data);}
        ,error:function(XMLHttpRequest,textStatus,errorThrown){}
        ,complete:function(XMLHttpRequest,textStatus){runJasperJS()}});
    });

    $(function () {
        $('#available_reports').change(function() {
            var repname = $('#available_reports option:selected').text()
            jQuery.ajax({type:'POST',data:{'report_name': repname}, url:'${createLink(controller: 'jasperReports', action: 'index')}'
            ,success:function(data,textStatus){jQuery('#report_details').html(data);}
            ,error:function(XMLHttpRequest,textStatus,errorThrown){}
            ,complete:function(XMLHttpRequest,textStatus){runJasperJS()}});
        });
    });

    $(function () {
        $('#selectRepFormat').change(function() {
            $('#hiddenReportFormat').val($("#selectRepFormat").val())
        });
    });
    function runJasperJS(){
        copyReportVals();
    }
    function copyReportVals() {
        $("#hiddenReportName").val($("#available_reports").val())
        $('#hiddenReportFormat').val($("#selectRepFormat").val())

    }
    function createSelect2Search(objectId, className) {
        $(objectId).select2({
            width: "90%",
            placeholder: "${message(code:'jasper.reports.search.ph')}",
            minimumInputLength: 1,
            ajax: { 
                url: '<g:createLink controller='ajax' action='lookup'/>',
                dataType: 'json',
                data: function (term, page) {
                    return {
                        hideIdent: true,
                        q: term, // search term
                        page_limit: 10,
                        baseClass:className
                    };
                },
                results: function (data, page) {
                    return {results: data.values};
                }
            }
            });

    }

    document.onload = runJasperJS();
</r:script>
</html>
