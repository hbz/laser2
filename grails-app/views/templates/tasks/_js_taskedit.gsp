<laser:script file="${this.getGroovyPageFileName()}">
    JSPC.taskedit = function (id) {

        $.ajax({
            url: '<g:createLink controller="ajaxHtml" action="editTask"/>?id='+id,
            success: function(result){
                $("#dynamicModalContainer").empty();
                $("#modalEditTask").remove();

                $("#dynamicModalContainer").html(result);
                $("#dynamicModalContainer .ui.modal").modal({
                    onVisible: function () {
                            JSPC.callbacks.dynPostFunc();
                    }
                }).modal('show');
            }
        });
    }
</laser:script>