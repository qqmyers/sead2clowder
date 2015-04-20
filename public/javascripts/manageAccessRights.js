// Author: Constantinos Sophocleous
function setPermission(fullName, email, resourceType, resourceId, permissionType, callbackName, callback, cbParam1, toSubresources){
	
	var setOrder = {};
	setOrder['userFullName'] = fullName;
	setOrder['userEmail'] = email;
	setOrder['newPermissionLevel'] = permissionType;
	
	var subdocsToSet = "";
	if(toSubresources){
		if(resourceType == "dataset")
			subdocsToSet = "Files";
		else
			subdocsToSet = "Datasets";
	}
	
	var request = $.ajax({
	       type: 'POST',
	       url: window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')+"/api/users/modifyRightsTo"+capitaliseFirstLetter(resourceType)+subdocsToSet+"/"+resourceId,
	       data: JSON.stringify(setOrder),
	       contentType: "application/json"
	     });
	
	request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);        
        alert(response);
        if(response.indexOf("set to chosen level") >= 0){
        	if(callbackName == "addNewRow"){
        		callback(fullName, email, permissionType, cbParam1);
        	}
        	else if(callbackName == "removeElem"){
        		callback(cbParam1);
        	}
        	else if(callbackName == "removeElemTrySubdocs"){
        		callback(cbParam1, fullName, email);
        	}
        }
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error(
    		"The following error occured: "+
    		textStatus, errorThrown, jqXHR		            
			);
		alert("ERROR: " + errorThrown +"." );
		if(callbackName == "resetValue"){
    		callback(cbParam1);
    	}
		else if(callbackName == "subdocsRemovalErrorInfo"){
    		callback(cbParam1);
    	}
	
	});
	
}

function setIsPublic(isPublic, resourceType, resourceId){
	var setOrder={};
	setOrder['isPublic'] = isPublic;

	var request = $.ajax({
	       type: 'POST',
	       url: window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')+"/api/"+resourceType+"s/setIsPublic"+"/"+resourceId,
	       data: JSON.stringify(setOrder),
	       contentType: "application/json"
	     });
	request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);        
        if(response.indexOf("Done") >= 0){
        	alert("Done");
        }
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error(
    		"The following error occured: "+
    		textStatus, errorThrown		            
			);
		alert("ERROR: " + errorThrown +"." );
		$("#privatePublic").prop('checked', !isPublic);
	
	});
	
}

function capitaliseFirstLetter(string)
{
    return string.charAt(0).toUpperCase() + string.slice(1);
}