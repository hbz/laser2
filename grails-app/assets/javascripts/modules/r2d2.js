
// modules/r2d2.js

r2d2 = {

    configs : {

        datepicker : {
            type: 'date',
            onChange: function(date, text, mode) {
                // deal with colored input field only when in filter context
                if ($(this).parents('.la-filter').length) {
                    if (!text) {
                        $(this).removeClass("la-calendar-selected");
                    } else {
                        if( ! $(this).hasClass("la-calendar-selected") ) {
                            $(this).addClass("la-calendar-selected");
                            //r2d2.countSettedFilters();
                        }
                    }
                }
            },
            onShow: function() {
                $('.ui.popup.calendar .table .link').attr( {
                    'role' : 'button'
                });
            },
            firstDayOfWeek: 1,
            monthFirst: false,
            minDate: new Date('1582-10-15'), //this is the start of the gregorian calendar
            maxDate: new Date('2099-12-31'), //our grand-grandchildren may update this date ...
            formatter: {
                date: function (date, settings) {
                    if (!date) return '';
                    var day = date.getDate();
                    if (day<10) day="0"+day;
                    var month = date.getMonth() + 1;
                    if (month<10) month="0"+month;
                    var year = date.getFullYear();

                    if ('dd.mm.yyyy' == JSPC.vars.dateFormat) {
                        return day + '.' + month + '.' + year;
                    }
                    else if ('yyyy-mm-dd' == JSPC.vars.dateFormat) {
                        //console.log('yyyy-mm-dd');
                        return year + '-' + month + '-' + day;
                    }
                    else {
                        alert('Please report this error: ' + JSPC.vars.dateFormat + ' for semui-datepicker unsupported');
                    }
                }
            },
            text: {
                days: [
                    JSPC.dict.get('loc.weekday.short.Sunday', JSPC.currLanguage),
                    JSPC.dict.get('loc.weekday.short.Monday', JSPC.currLanguage),
                    JSPC.dict.get('loc.weekday.short.Tuesday', JSPC.currLanguage),
                    JSPC.dict.get('loc.weekday.short.Wednesday', JSPC.currLanguage),
                    JSPC.dict.get('loc.weekday.short.Thursday', JSPC.currLanguage),
                    JSPC.dict.get('loc.weekday.short.Friday', JSPC.currLanguage),
                    JSPC.dict.get('loc.weekday.short.Saturday', JSPC.currLanguage)
                ],
                months: [
                    JSPC.dict.get('loc.January', JSPC.currLanguage),
                    JSPC.dict.get('loc.February', JSPC.currLanguage),
                    JSPC.dict.get('loc.March', JSPC.currLanguage),
                    JSPC.dict.get('loc.April', JSPC.currLanguage),
                    JSPC.dict.get('loc.May', JSPC.currLanguage),
                    JSPC.dict.get('loc.June', JSPC.currLanguage),
                    JSPC.dict.get('loc.July', JSPC.currLanguage),
                    JSPC.dict.get('loc.August', JSPC.currLanguage),
                    JSPC.dict.get('loc.September', JSPC.currLanguage),
                    JSPC.dict.get('loc.October', JSPC.currLanguage),
                    JSPC.dict.get('loc.November', JSPC.currLanguage),
                    JSPC.dict.get('loc.December', JSPC.currLanguage)
                ]
            }
        }
    },

    go : function() {
        r2d2.initGlobalAjaxLogin();

        r2d2.initGlobalSemuiStuff();
        r2d2.initGlobalXEditableStuff();

        r2d2.initDynamicSemuiStuff('body');
        r2d2.initDynamicXEditableStuff('body');

        $("html").css("cursor", "auto");
    },

    initGlobalAjaxLogin : function() {
        console.log('r2d2.initGlobalAjaxLogin()');

        $.ajaxSetup({
            statusCode: {
                401: function() {
                    $('.select2-container').select2('close');
                    $('*[class^=xEditable]').editable('hide');
                    showAjaxLoginModal();
                }
            }
        });

        function showAjaxLoginModal() {
            $('#ajaxLoginModal').modal('setting', 'closable', false).modal('show');
        }

        function ajaxAuth() {
            $.ajax({
                url: $('#ajaxLoginForm').attr('action'),
                data: $('#ajaxLoginForm').serialize(),
                method: 'POST',
                dataType: 'JSON',
                success: function (json, textStatus, xhr) {
                    if (json.success) {
                        $('#ajaxLoginForm')[0].reset();
                        $('#ajaxLoginMessage').empty();
                        $('#ajaxLoginModal').modal('hide');
                    }
                    else if (json.error) {
                        $('#ajaxLoginMessage').html('<div class="ui negative message">' + json.error + '</div>');
                    }
                    else {
                        $('#loginMessage').html(xhr.responseText);
                    }
                },
                error: function (xhr, textStatus, errorThrown) {
                    if (xhr.status == 401 && xhr.getResponseHeader('Location')) {
                        // the login request itself wasn't allowed, possibly because the
                        // post url is incorrect and access was denied to it
                        $('#loginMessage').html('<div class="ui negative message">Unbekannter Fehler beim Login. Melden Sie sich bitte über die Startseite an.</div>');
                    }
                    else {
                        var responseText = xhr.responseText;
                        if (responseText) {
                            var json = $.parseJSON(responseText);
                            if (json.error) {
                                $('#loginMessage').html('<div class="ui negative message">' + json.error + '</div>');
                                return;
                            }
                        }
                        else {
                            responseText = 'Status: ' + textStatus + ', Fehler: ' + errorThrown + ')';
                        }
                        $('#ajaxLoginMessage').html('<div class="ui negative message">' + responseText + '</div>');
                    }
                }
            })
        }

        $('#ajaxLoginForm').submit(function(event) {
            event.preventDefault();
            ajaxAuth();
        });
    },

    initGlobalSemuiStuff : function() {
        console.log("r2d2.initGlobalSemuiStuff()");
        // copy email adress next to icon and putting it in cache

        $('.js-copyTrigger').click(function(){
            var element = $(this).parents('.js-copyTriggerParent').find('.js-copyTopic')
            var $temp = $("<input>");
            $("body").append($temp);
            $temp.val($(element).text()).select();
            document.execCommand("copy");
            $temp.remove();
        });
        $('.js-copyTrigger').hover(
            function(){ $(this).addClass('open') },
            function(){ $(this).removeClass('open') }
        )
        $('.js-linkGoogle').hover(
            function(){ $(this).removeClass('alternate') },
            function(){ $(this).addClass('alternate') }
        )
        //JS Library readmore.js
        $('.la-readmore').readmore({
            speed: 75,
            lessLink: '<a href="#">' + JSPC.dict.get('link.readless', JSPC.currLanguage) + '</a>',
            moreLink: '<a href="#">' + JSPC.dict.get('link.readmore', JSPC.currLanguage) + '</a>',
            collapsedHeight: 115
        });
        //overwriting the template for input search (spotlight)
        // see https://jsfiddle.net/xnfkLnwe/1/
        // and https://github.com/Semantic-Org/Semantic-UI/issues/2405
        $.fn.search.settings.templates.message = function (message, type) {
            var
                html = '';
            if (message !== undefined && type !== undefined) {
                html += '' + '<div class="message ' + type + '">';
                // message type
                if (type == 'empty') {
                    html += '' + '<div class="header">' + JSPC.dict.get('search.API.heading.noResults', JSPC.currLanguage) + '</div class="header">' + '<div class="description">' + message + '</div class="description">';
                } else {
                    html += ' <div class="description">' + message + '</div>';
                }
                html += '</div>';
            }
            return html;
        };

        // spotlight

        $('.ui.search.spotlight').search({
            error : {
                source          : '"' + JSPC.dict.get('search.API.source', JSPC.currLanguage) + '"',
                noResults       : '',
                logging         : '"' + JSPC.dict.get('search.API.logging', JSPC.currLanguage) + '"',
                noEndpoint      : '"' + JSPC.dict.get('search.API.noEndpoint', JSPC.currLanguage) + '"',
                noTemplate      : '"' + JSPC.dict.get('search.API.noTemplate', JSPC.currLanguage) + '"',
                serverError     : '"' + JSPC.dict.get('search.API.serverError', JSPC.currLanguage) + '"',
                maxResults      : '"' + JSPC.dict.get('search.API.maxResults', JSPC.currLanguage) + '"',
                method          : '"' + JSPC.dict.get('search.API.method', JSPC.currLanguage) + '"'
            },

            type: 'category',
            minCharacters: 3,
            apiSettings: {

                url: JSPC.vars.spotlightSearchUrl + "/?query={query}",
                onResponse: function(elasticResponse) {
                    var response = { results : {} };

                    // translate Elasticsearch API response to work with semantic ui search
                    $.each(elasticResponse.results, function(index, item) {

                        var category   = item.category || 'Unknown';
                        // create new object category
                        if (response.results[category] === undefined) {
                            response.results[category] = {
                                name    : category,
                                results : []
                            };
                        }
                        // add result to category
                        response.results[category].results.push({
                            title       : item.title,
                            url         : item.url,
                            description : item.description
                        });
                    });
                    return response;
                },
                onError: function(errorMessage) {
                    // invalid response
                }
            }
        });
    },


    initGlobalXEditableStuff : function() {
        console.log("r2d2.initGlobalXEditableStuff()");

        $.fn.editable.defaults.mode = 'inline'
        $.fn.editableform.buttons = '<button aria-label="' + JSPC.dict.get('xEditable.button.ok', JSPC.currLanguage) + '" type="submit" class="ui icon button editable-submit"><i aria-hidden="true" class="check icon"></i></button>' +
            '<button aria-label="' + JSPC.dict.get('xEditable.button.cancel', JSPC.currLanguage) + '" type="button" class="ui icon button editable-cancel"><i aria-hidden="true" class="times icon"></i></button>'
        $.fn.editableform.template =
            '<form class="ui form editableform">' +
            '	<div class="control-group">' +
            '		<div class="ui calendar xEditable-datepicker">' +
            '			<div class="ui input right icon editable-input">' +
            '			</div>' +
            '			<div class="editable-buttons">' +
            '			</div>' +
            '		</div>' +
            '        <div id="characters-count"></div>' +
            '		<div class="editable-error-block">' +
            '		</div>' +
            '	</div>' +
            '</form>'
        $.fn.editableform.loading =
            '<div class="ui active inline loader"></div>'

        // TODO $.fn.datepicker.defaults.language = JSPC.vars.locale
    },


    initDynamicXEditableStuff : function(ctxSel) {
        console.log("r2d2.initDynamicXEditableStuff( " + ctxSel + " )");

        if (! ctxSel) { return null }

        $(ctxSel + ' .xEditableValue').editable({

            highlight: false,
            language: JSPC.vars.locale,
            format:   JSPC.vars.dateFormat,
            validate: function(value) {
                if ($(this).attr('data-format') && value) {
                    if(! (value.match(/^\d{1,2}\.\d{1,2}\.\d{4}$/) || value.match(/^\d{4}-\d{1,2}-\d{1,2}$/)) ) {
                        return "Ungültiges Format";
                    }
                }
                // custom validate functions via semui:xEditable validation="xy"
                var dVal = $(this).attr('data-validation')
                if (dVal) {
                    if (dVal.includes('notEmpty')) {
                        if($.trim(value) == '') {
                            return "Das Feld darf nicht leer sein";
                        }
                    }
                    if (dVal.includes('url')) {
                        var regex = /^(https?|ftp):\/\/(.)*/;
                        var test = regex.test($.trim(value)) || $.trim(value) == ''
                        if (! test) {
                            return "Ein URL muss mit 'http://' oder 'https://' oder 'ftp://' beginnen."
                        }
                    }
                    if (dVal.includes('datesCheck')) {
                        var thisInput = $.trim(value), startDateInput, endDateInput, startDate, endDate;
                        if($(this).attr("data-name") === "startDate") {
                            startDateInput = thisInput;
                            endDateInput = $('a[data-name="endDate"][data-pk="'+$(this).attr("data-pk")+'"]').text();
                        }
                        else if($(this).attr("data-name") === "endDate") {
                            startDateInput = $('a[data-name="startDate"][data-pk="'+$(this).attr("data-pk")+'"]').text();
                            endDateInput = thisInput
                        }
                        if(startDateInput !== '' && endDateInput !== '') {
                            startDate = Date.parse(JSPC.helper.formatDate(startDateInput));
                            endDate = Date.parse(JSPC.helper.formatDate(endDateInput));
                            console.log(startDate+" "+endDate);
                            if(startDate > endDate)
                                return "Das Enddatum darf nicht vor dem Anfangsdatum liegen.";
                        }
                    }
                }
            },
            success: function(response) {
                // override newValue with response from backend
                return {newValue: (response != 'null' ? response : null)}
            },
            error: function (xhr, status, error) {
                return xhr.status + ": " + xhr.statusText
            }
        }).on('save', function(e, params){
            if ($(this).attr('data-format')) {
                console.log(params)
            }
        }).on('shown', function() {
            if ($(this).attr('data-format')) {
                $(ctxSel + ' .xEditable-datepicker').calendar(r2d2.configs.datepicker);
                $(ctxSel + ' .editable-clear-x').click(function() {
                    $('.calendar').calendar('clear');
                });
            }else {
                var dType = $(this).attr('data-type')
                if (dType == "text") {
                    var maxLength = 255;
                    $('input').keyup(function () {
                        if($(this).attr('type') == 'text') {
                            var textlen = maxLength - $(this).val().length;
                            $('#characters-count').text(textlen + '/' + maxLength);
                        }
                    });
                }
            }
            $(".table").trigger('reflow')
        });

        $(ctxSel + ' .xEditableDatepicker').editable({});

        $(ctxSel + ' .xEditableManyToOne').editable({
            tpl: '<select class="ui search selection dropdown"></select>',
            success: function(response, newValue) {
                if(response.status == 'error') return response.msg; //msg will be shown in editable form
            }
        }).on('shown', function(e, obj) {

            $('.table').trigger('reflow');
            obj.input.$input.dropdown({clearable: true}) // reference to current dropdown
        });

        $(ctxSel + ' .simpleHiddenValue').editable({
            language: JSPC.vars.locale,
            format:   JSPC.vars.dateFormat,
            url: function(params) {
                var hidden_field_id = $(this).data('hidden-id');
                $("#" + hidden_field_id).val(params.value);
                // Element has a data-hidden-id which is the hidden form property that should be set to the appropriate value
            }
        });
    },


    initDynamicSemuiStuff : function(ctxSel) {
        console.log("r2d2.initDynamicSemuiStuff( " + ctxSel + " )")

        if (! ctxSel) { return null }

        $(ctxSel + " a[href], " + ctxSel + " input.js-wait-wheel").not("a[href^='#'], a[href*='ajax'], a[target='_blank'], .js-open-confirm-modal, a[data-tab], a[data-content], a.la-ctrls , .close, .js-no-wait-wheel, .trigger-modal").click(function() {
            $("html").css("cursor", "wait");
        });

        // selectable table to avoid button is showing when focus after modal closed
        $(ctxSel + ' .la-selectable').hover(function() {
            $( ".button" ).blur();
        });

        // close semui:messages alerts
        $(ctxSel + ' .close.icon').click(function() {
            $(this).parent().hide();
        });

        // modals
        $(ctxSel + " *[data-semui='modal']").click(function() {
            var triggerElement = $(this)
            var href = $(this).attr('data-href')
            if (! href) {
                href = $(this).attr('href')
            }
            $(href + '.ui.modal').modal({
                onVisible: function() {
                    $(this).find('.datepicker').calendar(r2d2.configs.datepicker);
                },
                detachable: true,
                autofocus: false,
                closable: false,
                transition: 'scale',
                onApprove : function() {
                    $(this).find('.ui.form').submit();
                    return false;
                },
                onShow : function() {
                    var modalCallbackFunction = JSPC.callbacks.modal.show[$(this).attr('id')];
                    if (typeof modalCallbackFunction === "function") {
                        modalCallbackFunction(triggerElement)
                    }
                }
            }).modal('show')
        });

        // accordions
        $(ctxSel + ' .ui.accordion').accordion({
            onOpening: function() {
                $(".table").trigger('reflow')
            },
            onOpen: function() {
                $(".table").trigger('reflow')
            }
        });

        // tabs
        $(ctxSel + ' .tabular.menu .item').tab();

        // checkboxes
        $(ctxSel + ' .ui.checkbox').not('#la-advanced').checkbox();

        // datepicker
        $(ctxSel + ' .datepicker').calendar(r2d2.configs.datepicker);

        $(ctxSel + ' form').attr('autocomplete', 'off');

        // DROPDOWN

        // all dropdowns but dropdowns inside mainMenue and but la-not-clearable at user/create view
        // simple dropdown
        $(ctxSel + ' .ui.dropdown').not('#mainMenue .ui.dropdown').not('.la-not-clearable').dropdown({
            selectOnKeydown: false,
            clearable: true,
        });
        // all search dropdowns but la-not-clearable at user/create view
        // search dropdown
        $(ctxSel + ' .ui.search.dropdown').not('.la-not-clearable').dropdown({
            forceSelection: false,
            selectOnKeydown: false,
            fullTextSearch: 'exact',
            clearable: true,
        });

        // FILTER
        // special: stuff on change
        // simple dropdown
        $(ctxSel + ' .la-filter .ui.dropdown').dropdown({
            selectOnKeydown: false,
            clearable: true,
            onChange: function(value, text, $choice){
                (value !== '') ? _addFilterDropdown(this) : _removeFilterDropdown(this);
            }
        });
        // search dropdown
        $(ctxSel + ' .la-filter .ui.search.dropdown').dropdown({
            forceSelection: false,
            selectOnKeydown: false,
            fullTextSearch: 'exact',
            clearable: true,
            onChange: function(value, text, $choice){
                (value !== '') ? _addFilterDropdown(this) : _removeFilterDropdown(this);
            }
        });

        // dropdowns escape
        $(ctxSel + ' .la-filter .ui.dropdown').on('keydown', function(e) {
            if(['Escape','Backspace','Delete'].includes(event.key)) {
                //e.preventDefault();
                $(this).dropdown('clear').dropdown('hide').removeClass("la-filter-dropdown-selected");
            }
        });

        $(ctxSel + ' .la-filter .ui.dropdown').each(function(index, elem){
            if ($(elem).dropdown('get value') != "") {
                _addFilterDropdown(elem);
            }
            r2d2.countSettedFilters();
        })

        function _addFilterDropdown(elem) {
            $(elem).is('select') ? $( elem ).parent().addClass("la-filter-dropdown-selected" ) : $( elem ).addClass("la-filter-dropdown-selected" );
        }

        function _removeFilterDropdown(elem) {
            $(elem).is('select') ? $( elem ).parent().removeClass("la-filter-dropdown-selected" ) : $( elem ).removeClass("la-filter-dropdown-selected" );
        }

        $(ctxSel + '.la-filter .checkbox').checkbox();

        // FILTER SELECT FUNCTION - INPUT LOADING
        $(ctxSel + ' .la-filter input[type=text]').each(function() {
            $(this).val().length === 0 ? $(this).removeClass("la-filter-selected") : $(this).addClass("la-filter-selected");
            r2d2.countSettedFilters(true);
        });

        //  FILTER SELECT FUNCTION - INPUT CHANGE
        $(ctxSel + ' .la-filter input[type=text]').change(function() {
            $(this).val().length === 0 ? $(this).removeClass("la-filter-selected") : $(this).addClass("la-filter-selected");
            //r2d2.countSettedFilters();
        });

        //
        $(ctxSel + ' .js-click-control').click(function(e) {

            var lastClicked = $(this).data("lastClicked");

            if ( lastClicked ) {
                if ((e.timeStamp - lastClicked) < 2000) {
                    e.preventDefault();
                }
            }
            $(this).data("lastClicked", e.timeStamp);
        });

        // confirmation modal
        function _buildConfirmationModal(elem) {

                //var $body = $('body');
                //var $modal = $('#js-modal');
                //var focusableElementsString = "a[href], area[href], input:not([type='hidden']):not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled]), iframe, object, embed, *[tabindex], *[contenteditable]";
                var ajaxUrl = elem.getAttribute("data-confirm-messageUrl")
                if (ajaxUrl) {
                    $.ajax({
                        url: ajaxUrl
                    })
                        .done(function (data) {
                            $('#js-confirmation-content-term').html(data)
                        })
                        .fail(function (data) {
                            $('#js-confirmation-content-term').html('WARNING: AJAX-CALL FAILED')
                        })
                }

                var tokenMsg = elem.getAttribute("data-confirm-tokenMsg") ? elem.getAttribute("data-confirm-tokenMsg") : false;
                tokenMsg ? $('#js-confirmation-term').html(tokenMsg) : $("#js-confirmation-term").remove();

                var dataAttr = elem.getAttribute("data-confirm-id")? elem.getAttribute("data-confirm-id")+'_form':false;
                var how = elem.getAttribute("data-confirm-term-how") ? elem.getAttribute("data-confirm-term-how"):"delete";
                var url = elem.getAttribute('href') && (elem.getAttribute('class').indexOf('la-js-remoteLink') == -1) && (elem.getAttribute('class') != 'js-gost') ? elem.getAttribute('href'): false; // use url only if not remote link
                var $jscb = $('#js-confirmation-button')

                switch (how) {
                    case "delete":
                        $jscb.html(JSPC.dict.get('confirm.dialog.delete', JSPC.currLanguage) + '<i aria-hidden="true" class="trash alternate icon"></i>');
                        break;
                    case "unlink":
                        $jscb.html(JSPC.dict.get('confirm.dialog.unlink', JSPC.currLanguage) + '<i aria-hidden="true" class="la-chain broken icon"></i>');
                        break;
                    case "share":
                        $jscb.html(JSPC.dict.get('confirm.dialog.share', JSPC.currLanguage) + '<i aria-hidden="true" class="la-share icon"></i>');
                        break;
                    case "inherit":
                        $jscb.html(JSPC.dict.get('confirm.dialog.inherit', JSPC.currLanguage) + '<i aria-hidden="true" class="thumbtack icon"></i>');
                        break;
                    case "ok":
                        $jscb.html(JSPC.dict.get('confirm.dialog.ok', JSPC.currLanguage) + '<i aria-hidden="true" class="check icon"></i>');
                        break;
                    case "concludeBinding":
                        $jscb.html(JSPC.dict.get('confirm.dialog.concludeBinding', JSPC.currLanguage) + '<i aria-hidden="true" class="check icon"></i>');
                        break;
                    case "clearUp":
                        $jscb.html(JSPC.dict.get('confirm.dialog.clearUp', JSPC.currLanguage) + '<i aria-hidden="true" class="bath icon"></i>');
                        break;
                    default:
                        $('').html('Entfernen<i aria-hidden="true" class="x icon"></i>');
                }

                var remoteLink = $(elem).hasClass('la-js-remoteLink')


                $('.tiny.modal')
                    .modal({
                        onShow : function() {
                            //only in form context
                            if (dataAttr) {
                                // only if the button that triggers the confirmation modal has the attribute value set
                                if ($(elem).val()) {
                                    // than find the form that wraps the button
                                    // and insert the hidden field with name and value
                                    var name = $(elem).attr('name')
                                    var  hiddenField = $('<input id="additionalHiddenField" type="hidden"/>')
                                        .attr( 'name',name )
                                        .val($(elem).val());
                                    $('[data-confirm-id='+dataAttr+']').prepend(hiddenField);
                                }
                            }
                        },
                        closable  : false,
                        onApprove : function() {

                            // open confirmation modal from inside a form
                            if (dataAttr){
                                $('[data-confirm-id='+dataAttr+']').submit();
                            }
                            // open confirmation modal and open a new url after conirmation
                            if (url){
                                window.location.href = url;
                            }
                            if (remoteLink) {
                                bb8.ajax4remoteLink(elem)
                            }
                            $('#js-confirmation-content-term').html('');
                        },
                        onDeny : function() {
                            $('#js-confirmation-content-term').html('');
                            // delete hidden field
                            if ($('#additionalHiddenField')) {
                                $('#additionalHiddenField').remove();
                            }
                        }
                        /*                        onShow : function() {
                                                    $modal.removeAttr('aria-hidden');
                                                    // is needed to hide the rest of the page from Screenreaders in case of open the modal
                                                    if ($('#js-modal-page').length === 0) { // just to avoid missing #js-modal-page
                                                        $body.wrapInner('<div id="js-modal-page"></div>');
                                                    }
                                                    $page = $('#js-modal-page');
                                                    $page.attr('aria-hidden', 'true');
                                                    $body.on("keydown", "#js-modal", function(event) {
                                                        var $this = $(this);
                                                        if (event.keyCode == 9) { // tab or Strg tab

                                                            // get list of all children elements in given object
                                                            var children = $this.find('*');

                                                            // get list of focusable items
                                                            var focusableItems = children.filter(focusableElementsString).filter(':visible');

                                                            // get currently focused item
                                                            var focusedItem = $(document.activeElement);

                                                            // get the number of focusable items
                                                            var numberOfFocusableItems = focusableItems.length;

                                                            var focusedItemIndex = focusableItems.index(focusedItem);

                                                            if (!event.shiftKey && (focusedItemIndex == numberOfFocusableItems - 1)) {
                                                                focusableItems.get(0).focus();
                                                                event.preventDefault();
                                                            }
                                                            if (event.shiftKey && focusedItemIndex == 0) {
                                                                focusableItems.get(numberOfFocusableItems - 1).focus();
                                                                event.preventDefault();
                                                            }
                                                        }

                                                    })
                                                },
                                                onHidden : function() {
                                                    $page.removeAttr('aria-hidden');
                                                    $modal.attr('aria-hidden', 'true');
                                                }*/
                    })
                    .modal('show');
        }

        // for links and submit buttons
        $(ctxSel + ' .js-open-confirm-modal').click(function(e) {
            e.preventDefault();
            _buildConfirmationModal(this);
        });

        // for old remote links = ajax calls
        $(ctxSel + ' .js-open-confirm-modal-copycat').click(function(e) {
            var onclickString = $(this).next('.js-gost').attr("onclick");
            $('#js-confirmation-button').attr("onclick", onclickString);
            var gostObject = $(this).next('.js-gost');
            _buildConfirmationModal(gostObject[0]);
        });
    },


    countSettedFilters : function () {
        // DROPDOWN AND INPUT FIELDS
        $( document ).ready(function() {
            var dropdownFilter  = $('main > .la-filter .la-filter-dropdown-selected').length;
            var inputTextFilter = $('main > .la-filter .la-filter-selected').length;
            var calendarFilter  = $('main > .la-filter .la-calendar-selected').length;
            var checkboxFilter  = 0;

            // CHECKBOXES
            // LOOP TROUGH CHECKBOXES
            var allCheckboxes = [];
            $('.la-filter .checkbox').each(function() {
                allCheckboxes.push($(this).children('input').attr("name"));
            });
            // ELIMINATE DUPLICATES
            var eliminateDuplicates = function (uniquecheckboxNames){
                return uniquecheckboxNames.filter (function(v,i) {
                    return uniquecheckboxNames.indexOf(v) === i
                });
            };
            var uniquecheckboxNames = eliminateDuplicates(allCheckboxes);
            // COUNT SELECTED CHECKBOXES
            _countSettedCheckboxes(uniquecheckboxNames);
            function _countSettedCheckboxes(params) {
                var sumCheck = 0;
                for (i=0; i<params.length; i++) {
                    var checkboxName = params[i];
                    $('input[name='+ checkboxName +']').is(':checked')? sumCheck=sumCheck+1: sumCheck= sumCheck;
                }
                checkboxFilter = sumCheck;
            }

            // COUNT ALL SELECTIONS IN TOTAL
            var total = dropdownFilter + inputTextFilter + calendarFilter +checkboxFilter;

            if (total == 0) {
                $('.la-js-filter-total').addClass('hidden');
                $('.la-js-filterButton i').removeClass('hidden');

            } else {
                $('.la-js-filter-total').text(total);
                $('.la-js-filter-total').removeClass('hidden');
                $('.la-js-filterButton i').addClass('hidden');
            }
        });
    }
}
