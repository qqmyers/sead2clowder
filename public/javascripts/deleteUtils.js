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

function confirmRemoveResourceFromSpaceEvent(spaceId, resourceType, resourceId, event) {
    var msg = "Are you sure you want to remove the " + resourceType + " from the space?";
    var modalHTML = confirmDeleteTemplate(msg);

    $(document).on("click", "#OKModalButton", function(event) {
        confirmModal.modal("hide");
        if (resourceType == "dataset") {
            removeDatasetFromSpace(spaceId, resourceId, event);
        } else if (resourceType == "collection") {
            removeCollectionFromSpace(spaceId, resourceId, event);
        }
    });

    var confirmModal = $(modalHTML);
    confirmModal.modal("show");
}

function confirmRemoveResourceFromSpace(spaceId, resourceType, resourceId, resourceName, url) {
    var msg = "Are you sure you want to remove the " + resourceType + " '" + resourceName + "' from this space?";
    var modalHTML = confirmDeleteTemplate(msg);

    $(document).on("click", "#OKModalButton", function(event) {
        confirmModal.modal("hide");
        if (resourceType == "dataset") {
            removeDatasetFromSpaceAndRedirect(spaceId, resourceId, url);
        } else if (resourceType == "collection") {
            removeCollectionFromSpaceAndRedirect(spaceId, resourceId, url);
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
