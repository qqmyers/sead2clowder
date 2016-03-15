/**
 * Created by lmarini on 3/25/15.
 */

function addToCollection(datasetId) {
    var selectedId = $("#collectionAddSelect").val();
    if (!selectedId) return false;
    var selectedName = $("#collectionAddSelect option:selected").text();
    selectedName = selectedName.replace(/\n/g, "<br>");

    var request = jsRoutes.api.Collections.attachDataset(selectedId, datasetId).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR) {
        var o =$.parseJSON(jqXHR.responseText);
        // TODO retrieve more information about collection from API and show it in the GUI
        var txt = '<div id="col_'+selectedId+'" class="row bottom-padding">' +
            '<div class="col-md-2"></div>' +
            '<div class="col-md-10">' +
            '<div><a href="'+jsRoutes.controllers.Collections.collection(selectedId).url+'" id='+selectedId+' class ="collection">'+selectedName+'</a> ' +
            '<span class="glyphicon glyphicon glyphicon-file" title="datasets in this collection"></span>';
        txt = txt + o.datasetsInCollection;
        txt = txt + ' | <button class="btn btn-link btn-xs" onclick="confirmRemoveResourceFromResourceEvent(\'collection\',\''+selectedId+'\',\'dataset\',\''+datasetId+'\', event)" title="Remove the datatset from the collection">' +
            '<span class="glyphicon glyphicon-remove"></span> Remove</button>';
        txt = txt + '</div>';
        txt = txt + '</div>';
        $("#collectionsList").append(txt);
        $("#collectionAddSelect").select2("val", "");
    });
    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to add a dataset to a collection.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not added to the collection due to the following : " + errorThrown, "error");
        }
    });

    return false;
}


function removeCollection(collectionId, datasetId, event){

    var request = jsRoutes.api.Collections.removeDataset(collectionId, datasetId, "True").ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR){
        $('#col_'+collectionId).remove();
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a dataset from a collection.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not removed from the collection due to : " + errorThrown, "error");
        }
    });
    return false;
}

//Method to remove the datatset from collection and redirect back to a specific URL on completion
function removeDatasetFromCollectionAndRedirect(collectionId, datasetId, url){
    if(url === undefined) reloadPage = "/collections";

    var request = jsRoutes.api.Collections.removeDataset(collectionId, datasetId).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR){
        $('#col_'+collectionId).remove();
        //console.log("Response " + response);
        window.location.href=url;
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a dataset from a collection.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not removed from the space due to : " + errorThrown, "error");
        }
    });
}