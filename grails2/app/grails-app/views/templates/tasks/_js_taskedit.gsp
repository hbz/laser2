<r:script>
    function taskedit(id) {

        $.ajax({
            url: '<g:createLink controller="ajaxHtml" action="editTask"/>?id='+id,
            success: function(result){
                $("#dynamicModalContainer").empty();
                $("#modalEditTask").remove();

                $("#dynamicModalContainer").html(result);
                $("#dynamicModalContainer .ui.modal").modal({
                    onVisible: function () {
                            ajaxPostFunc();
                    }
                }).modal('show');
            }
        });
    }
</r:script>