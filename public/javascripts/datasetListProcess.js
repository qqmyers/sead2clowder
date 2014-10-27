function removeDataset(datasetId,event, reloadPage){
	if(reloadPage === undefined) reloadPage = false;
	
	var request = $.ajax({
	       type: 'POST',
	       url: window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')+"/api/datasets/"+datasetId+"/remove"
	     });
	request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);
        var upNodes;
        //Has the user clicked on an icon in the link field, or elsewhere in the link field?
        if($(event.target).is("span")){
        	upNodes = $(event.target.parentNode.parentNode.parentNode);
        }else{
        	upNodes = $(event.target.parentNode.parentNode);
        }
        
        //Did the user click on a list item or on a tile?
        if(upNodes.is("tr")){
        	upNodes.remove();
        }else{
        	upNodes.parent().parent().remove();
        }
        
        if(reloadPage == true)
        	location.reload(true);
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occured: "+textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a dataset from the system.";
            alert("The dataset was not removed due to : " + errorThrown); 
			});	
}