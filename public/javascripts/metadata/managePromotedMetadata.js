
function addPromotedMetadata(data) {

    var addApi = jsRoutes.api.Metadata.addPromotedMetadataField();
    var request = addApi.ajax({
        type: 'POST',
        data: JSON.stringify(data),
        contentType: "application/json"
    });

    request.done(function (response, textStatus, jqXHR) {
        if (textStatus == "success") {
            notify("Metadata field successfully promoted.", "success", 3000);
        }

    });

    request.fail(function (jqXHR, textStatus, errorThrown) {
        notify("ERROR: " + jqXHR.responseJSON + " Metadata field promotion failed.", "error");
    });
}

function editPromotedMetadata() {

}

function deletePromotedMetadata() {

}

function getPromotedMetadataFields() {

    return new Promise(function(resolve, reject) {

        var getApi = jsRoutes.api.Metadata.getPromotedMetadataFields();
        var request = getApi.ajax({
            type: 'GET',
            contentType: "application/json"
        });

        request.done(function (response, textStatus, jqXHR) {
            if (textStatus == "success") {
                resolve(response);
            }
        });

        request.fail(function (jqXHR, textStatus, errorThrown) {
            reject(Error(jqXHR.statusText))
        });
    });

}