// Author: Constantinos Sophocleous
function subscribeUser(userIdentifier){
	
	var identifierData = {};
	identifierData['identifier'] = userIdentifier;
	
	var request = $.ajax({
	       type: 'POST',
	       url: window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')+"/api/newsletters/submitLoggedIn",
	       data: JSON.stringify(identifierData),
	       contentType: "application/json"
	     });

	request.done(function (response){
        console.log("Response " + response);
        if(response == "success")
        	alert("Subscribed.");
        else if(response == "notmodified")
        	alert("Subscribed already.")
        else
        	//FB authentication redirection
        	window.location.replace(response);
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error(
    		"The following error occured: "+
    		textStatus, errorThrown		            
			);
		alert("ERROR: " + errorThrown +". Subscription not added." );
			});	
}

function unsubscribeUser(userIdentifier){

	var identifierData = {};
	identifierData['identifier'] = userIdentifier;

	var request = $.ajax({
	       type: 'POST',
	       url: window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')+"/api/newsletters/removeSubscriptionLoggedIn",
	       data: JSON.stringify(identifierData),
	       contentType: "application/json"
	     });

	request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);        
        
        if(textStatus == "success")
        	alert("Unsubscribed.");
        else if(textStatus == "notmodified")
        	alert("Not subscribed already.")              
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error(
    		"The following error occured: "+
    		textStatus, errorThrown		            
			);
		alert("ERROR: " + errorThrown +". Subscription not removed." );
			});	
}