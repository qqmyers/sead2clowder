$(document).ready(function() {

    $(".btnDataset").hide();
    $("#otherOptions").hide();
    $(".showTemplates").show();
    $(".showGlobalTemplates").show();
    $(".showTagTemplates").show();

    $('#checkShareTemplate').prop('checked', false);
    $(".templates").empty();
    $(".globalTemplates").empty();
    $(".tagTemplates").empty();
    $("#btnTemplate").hide();
    $('<option>').val('').text('--Select One--').appendTo('.templates');
    $('<option>').val('').text('--Select One--').appendTo('.globalTemplates');
    $('<option>').val('').text('--Select One--').appendTo('.tagTemplates');

    $(".tagData").hide();
    $(".templateSearch").val('');
    $(".metaDataSettings").empty();
    $(".templateData").empty();
    $('[data-toggle="popover"]').popover();
    getTemplates();
    getPublicTemplates();

});

//TEMPLATES
function getTemplates() {
    $.ajax({

        url: "listExperimentTemplates",
        type:"GET",
        dataType: "json",
        beforeSend: function(xhr){
            xhr.setRequestHeader("Content-Type", "application/json");
            xhr.setRequestHeader("Accept", "application/json");
            // xhr.setRequestHeader("Authorization", "Basic " + btoa(username + ":" + password));
        },
        success: function(data){
            showTemplates(data);
        },
        error: function(xhr, status, error) {
            swal({
                title: "Error",
                text: "There was a problem returning custom templates",
                type: "error",
                timer: 1500,
                showConfirmButton: false
            });
        }
    })

}

function getPublicTemplates() {
    $.ajax({

        url: "getPublic",
        type:"GET",
        dataType: "json",
        beforeSend: function(xhr){
            xhr.setRequestHeader("Content-Type", "application/json");
            xhr.setRequestHeader("Accept", "application/json");
            // xhr.setRequestHeader("Authorization", "Basic " + btoa(username + ":" + password));
        },
        success: function(data){
            showGlobalTemplates(data);
        },
        error: function(xhr, status, error) {
            swal({
                title: "Error",
                text: "There was a problem returning custom templates",
                type: "error",
                timer: 1500,
                showConfirmButton: false
            });
        }
    })

}

function getTemplate(id){
    $.ajax({
        url: "getExperimentTemplateById/"+id+"",
        type:"GET",
        dataType: "json",
        beforeSend: function(xhr){
            xhr.setRequestHeader("Content-Type", "application/json");
            xhr.setRequestHeader("Accept", "application/json");
            // xhr.setRequestHeader("Authorization", "Basic " + btoa(username + ":" + password));
        },
        success: function(data){
            createBoxes(data);
        },
        error: function(xhr, status, error) {
            swal({
                title: "Error",
                text: "There was a problem returning global templates",
                type: "error",
                timer: 1500,
                showConfirmButton: false
            });
        }
    })
}

//Load user templates
function showTemplates(data) {
    $.each(data, function(key, val) {
        $(".templates").append($("<option class='placeholder'></option>").val(val.id).html(val.name));
    });

    $(".templates").focus();
}

//Load user templates
function showGlobalTemplates(templatesData) {
    $.each(templatesData, function(key, val) {
        $(".globalTemplates").append($("<option class='placeholder'></option>").val(val.id).html(val.name));
    });

    $(".globalTemplates").focus();
}

function showTagTemplates(data){

    $('<option>').val('').text('--Select One--').appendTo('.tagTemplates');
    $.each(data, function(key, val) {
        $(".tagTemplates").append($("<option class='placeholder'></option>").val(val.template_id).html(val.name));
    });

    $(".tagTemplates").focus();
}

function getAllTags(){

    $.ajax({
        url: "allTags",
        type:"GET",
        beforeSend: function(xhr){
            xhr.setRequestHeader("Content-Type", "application/json");
            xhr.setRequestHeader("Accept", "application/json");
            // xhr.setRequestHeader("Authorization", "Basic " + btoa(username + ":" + password));
        },
        success: function(data){

            //Format the object by removing whitespace, duplicates, and capitalize characters.
            var showUserTags = data.join(",");
            var allCapsTags = showUserTags.toUpperCase();
            var trimTags = $.map(allCapsTags.split(","), $.trim);
            var uniqueTags = jQuery.unique(trimTags);
            $(".templateSearch").autocomplete({
                source: uniqueTags,
                select: function (event, ui) {
                    $(".tagTemplates").empty();
                    $(".tagData").show();
                    $(".templateData").empty();
                    $(".metaDataSettings").empty();
                    $("#otherOptions").hide();
                    $(".templates").val();
                    $(".globalTemplates").val();
                    var selectedObj = ui.item;
                    getByTagId(selectedObj.value);
                },
                change: function( event, ui ) {

                }
            });
        }

    });
}

function getByTagId(tagId){

    var url = "getIdNameFromTag/"+tagId+"";
    $.ajax({
        url: url,
        type:"GET",
        beforeSend: function(xhr){
            xhr.setRequestHeader("Content-Type", "application/json");
            xhr.setRequestHeader("Accept", "application/json");
            // xhr.setRequestHeader("Authorization", "Basic " + btoa(username + ":" + password));
        },
        success: function(data){
            $(".tagData").show();
            $("#otherOptions").hide();
            showTagTemplates(data);
        }

    });
}

function createBoxes(data){
    // $(".templateData").empty();
    $(".btnDataset").show();
    $(".btnAdd").show();
    $("#btnTemplate").show();
    $("#otherOptions").show();

    $.each(data.terms, function(key, val) {
        var div = $("<div />");
        div.html(createDiv(val.key, val.default_value));
        $(".templateData").append(div);
    });

}

function clearTemplate(){
    $(".templateData").empty();
    $(".metaDataSettings").empty();
    $("#otherOptions").hide();
    $(".btnDataset").hide();
    $("#btnTemplate").hide();
    $(".datasetName").val("");
    $(".tagName").val("");
    $(".templates").focus();
    $(".templates").val("");
    $(".globalTemplates").val("");
    $(".templateSearch").val("");
    $(".tagTemplates").val("");
    $(".tagData").hide();
    $('#checkShareTemplate').prop('checked', false);
}

$(document).on('click', '.clearTemplate', function(e){
    e.preventDefault();
    e.stopPropagation();
    clearTemplate();
});

//Handle template load when new menu item is selected
$(document).on('change', '.templates', function(){
    var id = $(this).val();
    if (id != ''){
        $(".globalTemplates").val("");
        $(".templateSearch").val("");
        $(".tagTemplates").val("");
        $(".templateData").empty();
        $(".metaDataSettings").empty();
        $(".tagData").hide();
        $("#otherOptions").show();
        getTemplate(id);
    }
});

$(document).on('change', '.globalTemplates', function(){
    var id = $(this).val();

    if (id != ''){
        $(".templates").val("");
        $(".templateSearch").val("");
        $(".tagTemplates").val("");
        $(".templateData").empty();
        $(".metaDataSettings").empty();
        $(".tagData").hide();
        $("#otherOptions").show();
        getTemplate(id);
    }
});

$(document).on('change', '.tagTemplates', function(){
    var id = $(this).val();

    if (id != ''){
        $(".templates").val("");
        $(".globalTemplates").val("");
        $(".templateData").empty();
        $(".metaDataSettings").empty();
        $("#otherOptions").show();
        $(".tagData").show();
        getTemplate(id);
    }
});

//Create a new template
$(document).on('click', '#btnTemplate', function(e){
    if ($("#formGetDatasets").valid()){
        postTemplate(e);
    }

});

$("#formGetDatasets").validate();

//Posts new template
function postTemplate(e) {
    e.preventDefault();
    e.stopPropagation();

    var templateTerms = buildTemplate();
    var templateName = $('.datasetName').val();
    var tagName = $('.tagName').val().toUpperCase();
    var shareTemplate = $('#checkShareTemplate').is(":checked");
    $.ajax({
        url: "createExperimentTemplateFromJson?isPublic="+shareTemplate+"",
        type:"POST",
        data: JSON.stringify({ name: templateName, terms: templateTerms, tags: tagName}),
        beforeSend: function(xhr){
            xhr.setRequestHeader("Content-Type", "application/json");
            xhr.setRequestHeader("Accept", "application/json");
            // xhr.setRequestHeader("Authorization", "Basic " + btoa(username + ":" + password));
        },
        success: function(data){
            swal({
                title: "Success",
                text: "A new template was created",
                type: "success",
                timer: 1500,
                showConfirmButton: false
            });

            // clear all the inputs in the new dataset field tabs
            $(".templateData").empty();
            $(".metaDataSettings").empty();
            $(".templates").empty();
            $(".globalTemplates").empty();
            $("#btnTemplate").hide();
            $(".datasetName").val('');
            $(".tagName").val('');
            $('#checkShareTemplate').prop('checked', false);
            $('<option>').val('').text('--Select One--').appendTo('.templates');
            $('<option>').val('').text('--Select One--').appendTo('.globalTemplates');
            $('<option>').val('').text('--Select One--').appendTo('.tagTemplates');

            $("#otherOptions").hide();
            getTemplates();
            getPublicTemplates();
        },
        error: function(xhr, status, error) {
            swal({
                title: "Error",
                text: "There was a problem creating this template",
                type: "error",
                timer: 1500,
                showConfirmButton: false
            });
        }
    })
}

function buildTemplate() {
    var metaDataKeys = $.map($('.metaDataKey'), function (el) { return el.value; });
    var metaDataVals = $.map($('.metaDataVal'), function (el) {return el.value;});
    var arr = [];

    $.each(metaDataKeys, function (idx, keyName) {
        if (keyName != ''){
            var objCombined = {};
            objCombined['key'] = keyName;
            objCombined['default_value'] = metaDataVals[idx];
            arr.push(objCombined);
        }
    });
    return(arr);
}

//Auto complete dataset field
$(function() {

    var availableTags = [
        "Device Characterization",
        "Diffusion",
        "Ellipsometry",
        "Lithography",
        "Metallization",
        "Optical Microscopy",
        "Oxidation",
        "Plasma Etching",
        "Profilometry",
        "SEM",
        "SiO2 Mask Deposition",
        "SIMS",
        "SiNx Deposition",
        "SiNx Removal",
        "SPA"
    ];

    $(".datasetName").autocomplete({
        source: availableTags
    });

    getAllTags();
    // $(".tagData").hide();

});

//Auto create and remove textboxes for custom dataset settings
$(function () {
    $(".btnAdd").on('click', function () {
        var metaDataTags = [
            "Power",
            "Element",
            "Current",
            "Pressure",
            "Time",
            "Temperature",
            "Depth",
            "Lateral Depth",
            "Disorder Depth",
            "Tool",
            "Sample",
            "Spin",
            "RF",
            "ICP",
            "SFP",
            "EBR",
            "Expose",
            "RIE",
            "PostExp Bake",
            "Spin",
            "PreExp Bake"
        ];

        var div = $("<div />");
        div.html(createDiv(" "));

        //Future: check and see if any classes with this value exist already before making another input
        $(".metaDataSettings").append(div);
        $(".metaDataKey").first().focus();
        $(".btnDataset").show();
        $("#btnTemplate").show();
        $("#otherOptions").show();
        //Call autocomplete on dynamically created textbox after it's created
        $(".metaDataKey").autocomplete({
            source:metaDataTags
        });
    });

    $("body").on("click", ".remove", function () {
        $(this).closest(".top-buffer").remove();
    });

    $("body").keypress(function(e){
        if(e.which == 13){
            $(".metaDataVal").blur();
        }
    });
});

//Create dynamic textbox
function createDiv(keyName, val) {
    var valKeyName = jQuery.trim(keyName);
    var valStr = jQuery.trim(val);
    var txtToWrite = "Default Value: (optional)";

    return '<div class="row top-buffer"><div class="col-xs-5"><b>' + "<label for='name'>Name: " + '</label></b></span>' +
        '<input class="metaDataKey form-control" id="name" type="text" value=' + valKeyName.replace(/ /g,"&nbsp;") +'></div>' +

            // '<div class="col-xs-2" style="margin-left:-15px;"><b>' + "<label for='val'>Unit: " + '</label></b>' +
            // '<input class="metaDataVal form-control" type="text" id="val"></div>'  +

        '<div class="col-xs-5" style="margin-left:-15px;"><b>' + "<label for='val'>"+txtToWrite + '</label></b>' +
        '<input class="metaDataVal form-control" type="text" id="val" value=' + valStr.replace(/ /g,"&nbsp;") +'></div>' +

        '<div class="col-xs-1" style="margin-left:-15px;"><b>' + "<label for='val'>&nbsp;" + '</label></b>' +
        '<input type="button" value="Remove" class="remove btn btn-danger"></div></div>'

}





