// Functions to Confirm deleting or removing resources.
//
//Created by mo on 3/2/16.
function confirmDeleteResource(resourceType, resourceId, resourceName, url) {
    var msg = "Are you sure you want to delete the " + resourceType + " '" + resourceName + "'?";
    var modalHTML = confirmDeleteTemplate(msg);

    $(document).on("click", "#OKModalButton", function(event) {
        confirmModal.modal("hide");
        if (resourceType == "file") {
            removeFileAndRedirect(resourceId, url);
        } else if (resourceType == "dataset") {
            removeDatasetAndRedirect(resourceId, url);
        } else if (resourceType == "collection") {
            removeCollectionAndRedirect(resourceId, url);
        } else if (resourceType == "space") {
            removeSpaceAndRedirect(resourceId, url);
        }
    });

    var confirmModal = $(modalHTML);
    confirmModal.modal("show");
}

function confirmRemoveResourceFromResourceEvent(resourceFromType, resourceFromId, resourceType, resourceId, event) {
    var msg = "Are you sure you want to remove the " + resourceType + " from the " + resourceFromType + "?";
    var modalHTML = confirmDeleteTemplate(msg);

    $(document).on("click", "#OKModalButton", function(event) {
        confirmModal.modal("hide");
        if (resourceFromType == "collection") {
            if (resourceType == "dataset") {
                removeCollection(resourceFromId,resourceId, event);
            }
        } else if (resourceFromType == "space") {
            if (resourceType == "dataset") {
                removeDatasetFromSpace(resourceFromId, resourceId, event);
            } else if (resourceType == "collection") {
                removeCollectionFromSpace(resourceFromId, resourceId, event);
            }
        }
    });

    var confirmModal = $(modalHTML);
    confirmModal.modal("show");
}

function confirmRemoveResourceFromResource(resourceFromType, resourceFromId, resourceType, resourceId, resourceName, url) {
    var msg = "Are you sure you want to remove the ";
    if (resourceFromType == "collection" && resourceType == "collection") {
        msg = msg + "child collection" + " '" + resourceName + "' from the parent " + resourceFromType + "?";
    } else {
        msg = msg + resourceType + " '" + resourceName + "' from " + resourceFromType + "?";
    }
    var modalHTML = confirmDeleteTemplate(msg);

    $(document).on("click", "#OKModalButton", function(event) {
        confirmModal.modal("hide");
        if (resourceFromType == "collection") {
            if (resourceType == "collection") {
                removeChildCollectionFromParent(resourceFromId,resourceId,url);
            } else if (resourceType == "dataset") {
                alert(resourceFromId);
                removeDatasetFromCollectionAndRedirect(resourceFromId,resourceId, url);
            }
        } else if (resourceFromType == "space") {
            if (resourceType == "dataset") {
                removeDatasetFromSpaceAndRedirect(resourceFromId, resourceId, url);
            } else if (resourceType == "collection") {
                removeCollectionFromSpaceAndRedirect(resourceFromId, resourceId, url);
            }
        }
    });

    var confirmModal = $(modalHTML);
    confirmModal.modal("show");
}

function confirmDeleteTemplate(message) {
    var modalHTML = '<div id="confirm-delete" class="modal fade" role="dialog">';
    modalHTML += '<div class="modal-dialog">';
    modalHTML += '<div class="modal-content">';
    modalHTML += '<div class="modal-header">';
    modalHTML += '<button type="button" class="close" data-dismiss="modal">&times;</button>';
    modalHTML += '<h4 class="modal-title">Confirm</h4>';
    modalHTML += '</div>';
    modalHTML += '<div class="modal-body">';
    modalHTML += '<p>' + message + '</p>';
    modalHTML += '</div>';
    modalHTML += '<div class="modal-footer">';
    modalHTML += '<button type="button" class="btn btn-link" data-dismiss="modal"><span class="glyphicon glyphicon-remove"></span> Cancel</button>';
    modalHTML += '<button type="button" class="btn btn-primary" id="OKModalButton"><span class="glyphicon glyphicon-ok"></span> OK</button>';
    modalHTML += '</div>';
    modalHTML += '</div>';
    modalHTML += '</div>';
    modalHTML += '</div>';

    return modalHTML;
}
