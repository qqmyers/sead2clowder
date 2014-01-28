function subscribeUser(userEmail){
	
	var emailData = {};
	emailData['email'] = userEmail;
	
	var request = $.ajax({
	       type: 'POST',
	       url: "http://"+hostIp+":"+window.location.port+"/api/newsletters/submitLoggedIn",
	       data: JSON.stringify(emailData),
	       contentType: "application/json"
	     });

	request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);
        
        if(textStatus == "success")
        	alert("Subscribed.");
        else if(textStatus == "notmodified")
        	alert("Subscribed already.")
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error(
    		"The following error occured: "+
    		textStatus, errorThrown		            
			);
		alert("ERROR: " + errorThrown +". Subscription not added." );
			});	
}

function unsubscribeUser(userEmail){

	var emailData = {};
	emailData['email'] = userEmail;

	var request = $.ajax({
	       type: 'POST',
	       url: "http://"+hostIp+":"+window.location.port+"/api/newsletters/removeSubscriptionLoggedIn",
	       data: JSON.stringify(emailData),
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