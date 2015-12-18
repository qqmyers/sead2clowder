//This file is referenced by newDataset.scala.html
//
//It contains functions and callback definitions that are used by the create dataset workflow, and the multi-file-uploader.
//

//Flag to signal that dataset creation has started                
var asynchStarted = false;
//The id of the new dataset, to share among the file uploads
var id = "__notset";
//The original data to be submitted, for the initial file that handles the ajax call
//to create the dataset
var origData = null;
//Flag to determine if authentication is being checked
var authInProcess = false;

//On page load, ensure that everything is in a clean state
$(document).ready(function() {
    clearErrors();
    enableFields();
    resetDatasetItems();                        
});   

//Disable common input fields
function disableFields() {
	var name=$('#name').val();
	var desc=$('#description').val();
	$('#name').addClass("hiddenholdspace");
	$('#description').addClass("hiddenholdspace");	
	$("input[name=radiogroup]").attr('disabled', true);
	$("#spaceid").attr('disabled', true);
	$("#parentid").attr('disabled',true);
	$('#namelabel').html(htmlEncode(name).replace(/\n/g, "<br>"));
	$('#namelabel').show();
	$('#desclabel').html(htmlEncode(desc).replace(/\n/g, "<br>"));
	$('#desclabel').show();
}

//Enable common input fields
function enableFields() {
	$('#namelabel').hide();
	$('#desclabel').hide();
	$('#name').removeClass("hiddenholdspace");
    $('#description').removeClass("hiddenholdspace");
    $("input[name=radiogroup]").attr('disabled', false);
    $("#spaceid").attr('disabled', false);
	$("#parentid").attr('disabled',false);
}

//Remove the error messages that are provided to the user
function clearErrors() {
	$('.error').hide();
}

//Reset the interface to a state where user input can be provided again
function resetDatasetItems() {
	asynchStarted = false;
	id = "__notset";
	origData = null;
	//Ensure both tabs are shown
	$('#tab1anchor').show();
	$('#tab2anchor').show();
	hideStatus();
	$('#existingcreate').html("<span class='glyphicon glyphicon-ok'></span> Create Dataset");
	$('#uploadcreate').html("<span class='glyphicon glyphicon-ok'></span> Create Dataset");
}

//Hide the status items
function hideStatus() {
	$('#status').hide();
}

//Empty the common input elements
function clearFields() {
	$('#name').val("");
	$('#description').val("");
}

//Reset the common elements to enabled and the dataset specific variables to their starting state
function resetValues() {
	enableFields();
	resetDatasetItems();
	clearFields();
	$('#everywhere').prop('checked', true);
}

//Tab 1 - Upload New Files related functions below

//Utility method to allow calls for files to be uploaded to wait until the dataset ID is
//in place before finally proceeding with their submit.
function holdForId(data) {   
   setTimeout(function(){
	   //Only proceed to hold and submit if the asynchStarted value remains true.
	   //Otherwise, it has been reset and there is no reason to continue to hold.
	   if (asynchStarted) {
	      if (id == "__notset") {
	    	  // recurse
	          holdForId(data); 
	      }
	      else {     	  
	    	  data.submit();
	      }
	   }
  }, 500);
}

$(function () {	                	                 
	//Callback for any submit call, whether it is the overall one, or individual files, in the multi-file-uploader
    $('#fileupload').bind('fileuploadsubmit', function (e, data) {    	    	
    	//First, check if the id value is set and if an asych call is started. 
    	if (asynchStarted && id == "__notset") {	                    		
            //If so, wait for the ID to be set.
    		holdForId(data);
    		return false;
    	}
    	else {
    		  //In this case either asynch has started or the ID is set, so trip the flag 
    		  //and set the original data in the first case, and this is basically a no-op
    		  //in the latter case.	                    		  
    		  asynchStarted = true;
    		  origData = data;
    	}
    	
    	return createEmptyDataset(data);    
    });	    
});        

function createEmptyDataset(data) {
 		
	//Remove error messages if present
	clearErrors();

	//Disable input elements
	disableFields();
	
	//Hide the other tab
	$('#tab2anchor').hide();
	
	//Update the input we are adding to the form programmatically      
	var name = $('#name');
	var desc = $('#description');

	var spaceList = [];
	$('#spaceid').find(":selected").each(function(i, selected) {
		spaceList[i] = $(selected).val();
	});

	var parentCollectionList = [];
	$('#parentid').find(":selected").each(function(i, selected) {
		parentCollectionList[i] = $(selected).val();
	});

    //Add errors and return false if validation fails. Validation comes from the host page, passing in the isNameRequired and isDescRequired
    //variables.
    var error = false;
    if (!name.val() && isNameRequired) {
    	$('#nameerror').show();
    	error = true;
    }
    if (!desc.val() && isDescRequired) {
        $('#descerror').show();
        error = true;
    }    	   
    if (error) {
    	if (data != null) {
	    	//On error, re-enable things to allow the user to fix items
	    	data.context.find('button').prop('disabled', false);
    	}
    	enableFields();
    	//Also, reset the dataset elements, since the workflow is starting over.
    	resetDatasetItems();
    	return false;
    }
    
    //No field errors, so set the input values	                        
    var radios = document.getElementsByName("radiogroup");                        
    for (var elem in radios) {
        if (radios[elem].checked) {
            $('#hiddenlevel').val(radios[elem].value);
        }
    }
    
    var encName = htmlEncode(name.val());
	var encDescription = htmlEncode(desc.val());
    
    $('#hiddenname').val(encName);
    $('#hiddendescription').val(encDescription);
    //Set the ID as it currently stands
    $('#hiddenid').val(id);
   
    if (id == "__notset") {
    	//Case for the primary file that is submitted. It will create the dataset and obtain the id.     
    	console.log("spaces are " + spaceList);
    	var jsonData = JSON.stringify({"name":encName, "description":encDescription, "space":spaceList, "parentCollections":parentCollectionList});
    	console.log("jsondata is " + jsonData);
        var request = null;		                         	                        
        request = jsRoutes.api.Datasets.createEmptyDataset().ajax({
            data: jsonData,
            type: 'POST',
            contentType: "application/json",
        });
    	                        	                        
        request.done(function (response, textStatus, jqXHR){	                            
            //Sucessful creation of the dataset. Set the id so that all files can
            //proceed to finish their submit.
            id = response["id"];
            console.log("Successful response from createEmptyDataset. ID is " + id);
            $('#hiddenid').val(id);   
            
            if (asynchStarted) {
	            //Now call the submit for the primary file that was submitted that triggered the dataset
	            //creation.
	            origData.submit();
            }
                        
            notify("Creation successful. Go to the <a href='" + jsRoutes.controllers.Datasets.dataset(id).url + "'>Dataset</a>", "success", 5000);
			$('#uploadcreate').html("<span class='glyphicon glyphicon-plus'></span> Attach Files");
        });


        request.fail(function (jqXHR, textStatus, errorThrown){
            console.error("The following error occured: " + textStatus, errorThrown);
            var errMsg = "You must be logged in to create a new dataset.";                                
            if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            	notify("Error in creating dataset. : " + errorThrown, "error");            	
            	if (data != null) {
	            	//On error, re-enable things to allow the user to fix items
	            	data.context.find('button').prop('disabled', false);
            	}
            	enableFields();
            	//Also, reset the dataset elements, since the workflow is starting over.
            	resetDatasetItems();
            	clearErrors();
            }  
        });
        //This block is the primary file, so don't submit yet, don't re-enable the buttons either.
        //The submission of this data will occur on the successful callback for the dataset creation.
        return false;
    }
    return true;
}

function checkZeroFiles() {
	var numFiles = $('#fileupload').fileupload('option').getNumberOfFiles();
	//Only needs to be checked if asynch hasn't already been started, otherwise dataset already created.
	if (!asynchStarted && id == "__notset") {
		if (numFiles == 0) {
			//Here, attempt to create the dataset since the user has simply clicked the button with no files added.
			createEmptyDataset(null);
		}
	}
}

//Existing file upload functions below

//Clear all selected items
function clearFiles() {
	$("#filelist option:selected").removeAttr("selected");
}

//Call on Create button click. Move to create a dataset as specified, and attach any files if they are specified. This is the 
//use case for attaching existing files to a given dataset.
function attachFiles() {			
	//Remove error messages if present
	clearErrors();		
    
    var ids = $("#filelist option:selected").map(function(){ return this.value }).get().join(",");    		
	var jsonData = null;
	var request = null;	
	
	if (id == "__notset") {
		//Create the empty dataset, but pass the existing file ids if present, so it knows to append those afterwards
		
		//Disable input elements
		disableFields();
		
		//Hide the other tab
		$('#tab1anchor').hide();
		
		//Update the input we are adding to the form programmatically      
		var name = $('#name');
	    var desc = $('#description');
		var spaceList = [];
		$('#spaceid').find(":selected").each(function(i, selected) {
			spaceList[i] = $(selected).val();
		});

		var parentCollectionList = [];
		$('#parentid').find(":selected").each(function(i, selected) {
			parentCollectionList[i] = $(selected).val();
		});
	    
	    console.log("isNameRequried is " + isNameRequired);
	    console.log("isDescRequired is " + isDescRequired);

	    //Add errors and return false if validation fails. Validation comes from the host page, passing in the isNameRequired and isDescRequired
	    //variables.
	    var error = false;
	    if (!name.val() && isNameRequired) {
	    	$('#nameerror').show();
	    	error = true;
	    }
	    if (!desc.val() && isDescRequired) {
	        $('#descerror').show();
	        error = true;
	    }    	   
	    if (error) {
	    	enableFields();
	    	$('#tab1anchor').show();
	    	return false;
	    }
	    
	    var encName = htmlEncode(name.val());
		var encDescription = htmlEncode(desc.val());
	    
		if (ids.length == 0) {
			jsonData = JSON.stringify({"name":encName, "description":encDescription, "space":spaceList, "parentCollections" : parentCollectionList});
		}
		else {
			jsonData = JSON.stringify({"name":encName, "description":encDescription, "space":spaceList, "parentCollections": parentCollectionList, "existingfiles":ids});
		}	
	    	                         	                        
	    request = jsRoutes.api.Datasets.createEmptyDataset().ajax({
	        data: jsonData,
	        type: 'POST',
	        contentType: "application/json",
	    });
		                        	                        
	    request.done(function (response, textStatus, jqXHR){	    
	    	//Successful creation and file attachment. Update the staus label accordingly.
	        id = response["id"];
	        console.log("Successful response from createEmptyDataset existing files. ID is " + id);
	        notify("Creation successful. Go to the <a href='" + jsRoutes.controllers.Datasets.dataset(id).url + "'>Dataset</a>", "success", 5000);
			$('#existingcreate').html("<span class='glyphicon glyphicon-plus'></span> Attach Files");
	    });
	
	
	    request.fail(function (jqXHR, textStatus, errorThrown){
	        console.error("The following error occured: " + textStatus, errorThrown);
	        var errMsg = "You must be logged in to create a new dataset.";                                
	        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
	        	notify("Error in creating dataset with existing files. : " + errorThrown, "error");	        	
	        }  
	    });
	}
	else {
		//In this case, the process is simply attaching files
		if (ids.length == 0) {
			hideStatus();
			//No files selected, show error.
			notify("No files selected to attach. Please select some files.", "error");
        	return false;
		}
		
		jsonData = JSON.stringify({"datasetid":id, "existingfiles":ids});
		request = jsRoutes.api.Datasets.attachMultipleFiles().ajax({
	        data: jsonData,
	        type: 'POST',
	        contentType: "application/json",
	    });
		                        	                        
	    request.done(function (response, textStatus, jqXHR){	    
	    	//Successful attachment of multiple files
	        console.log("Successful response from attachMultipleFiles.");
	        notify("Attach files successful. Go to the <a href='" + jsRoutes.controllers.Datasets.dataset(id).url + "'>Dataset</a>", "success", 5000);
	    });
	
	
	    request.fail(function (jqXHR, textStatus, errorThrown){
	        console.error("The following error occured: " + textStatus, errorThrown);
	        var errMsg = "You must be logged in to attach files to a dataset.";                                
	        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
	        	notify("Error in attaching exising files to a dataset. : " + errorThrown, "error");	        	
	        }  
	    });
	}
    
    return false;
}


//Call needed for the new file upload page, in order to ensure that the user's authentication hasn't timed out.
//This callback is invoked on user add of files, to try to catch it as early as possible. This code exists in
//file-uploader/jquery-fileupload-medici-auth.js as well.
//
//It requires the loading page to also bring in the javascript/errorRedirect.js 
//
$(function () {	                	                 
	//Callback for any submit call, whether it is the overall one, or individual files, in the multi-file-uploader
    $('#fileupload').bind('fileuploadadd', function (e, data) {
    	
    	if (authInProcess) {    		
    		return holdForAuthAdd();    		
    	}
    	else {
    		//No auth started yet, so we'll start
    		authInProcess = true;
    	}
    	//Perform authentication check
    	var request = null;		                         	                        
        request = jsRoutes.api.Users.getUser().ajax({
            type: 'GET',
            contentType: "application/json"
        });
    	                        	                        
        request.done(function (response, textStatus, jqXHR){	                            
            //Sucessful call, so authenticated. Need to simply ensure that we have a user. It always should be there in
        	//this case, but log the odd corner case.
            var responseText = jqXHR.responseText;           
            authInProcess = false;
            if (responseText == "No user found") {
	            //The weird corner case - log it and alert for now
	            console.log("Odd corner case in file uploader. Authenticated but no user found.");
	            //Return false
	            return false;
            }
            else {
            	//User present and authentication successful, so proceed to submit the files
            	return true;
            }
                        
        });


        request.fail(function (jqXHR, textStatus, errorThrown){
            console.error("addCallback - fileUploader - The following error occured: " + textStatus, errorThrown);
            authInProcess = false;
            var errMsg = "You must be logged in to upload new files.";                                
            if (!checkErrorAndRedirect(jqXHR, errMsg)) {            	
            	console.log("Different error message on failure.");
            	alert("ERROR: " + jqXHR.responseText);
            }  
            return false;
        });    	
    });	    
});        


//Utility method to allow calls for files to be uploaded to wait until authentication is
//verified before finally proceeding with the add. In reality, the holds will be canceled on 
//an authentication failure, since the redirect to login will end them. This code exists in
//file-uploader/jquery-fileupload-medici-auth.js as well.
function holdForAuthAdd(data) { 
	counter = 0;
	function checkAuth() {		
		if (authInProcess) {
			counter++;
			if (counter > 20) {
				setTimeout(checkAuth, 500);
			}
			else {
				return false;
			}
		}
		else {
			return true;
		}
	}
	checkAuth();	
}
