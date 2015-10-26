	//Counters for DOM node uniqueness.
	var elementCounterChildCollections = 1;
	var elementCounterAdd = 1;
	
	var currentFirstDatasets = 1;
	var currentFirstAdd = 1;
	var searchResultsCount = 0;
	
	var childCollectionsInCollection = $("#collectionChildCollectionsTable tbody tr");
	var childCollectionsInCollectionCount = childCollectionsInCollection.length;
	
	var areRestDatasetsVisible = false;

	function addChildCollection(childCollectionId, event){
		
		var request = jsRoutes.api.Collections.attachSubCollection(collectionId, childCollectionId).ajax({
			type: 'POST'
		});

		//Note - need to make the "replace" calls below more generic.
		request.done(function (response, textStatus, jqXHR){	        
	        //Remove selected dataset from datasets not in collection.
	        var resultId = event.target.parentNode.parentNode.getAttribute('data-childcollectionid');
	        var inputDate = $("tr[data-childcollectionid='" + resultId + "'] td:nth-child(2)").text();
	        var inputDescr = $("tr[data-childcollectionid='" + resultId + "'] td:nth-child(3)").html();
	        var inputThumbnail = $("tr[data-childcollectionid='" + resultId + "'] td:nth-child(4)").html();
	        $("#addChildCollectionsTable tbody tr[data-childcollectionsid='" + resultId + "']").remove();
	        
	        //Add the node to the contained datasets table, with associated data
	        $('#collectionChildCollectionsTable tbody').append("<tr data-childcollectionid='" + childCollectionId + "'><td><a href='" + jsRoutes.controllers.Collections.collection(childCollectionId).url + "'>"+ event.target.innerHTML.replace(/\n/g, "<br>") + "</a></td>"
					+ "<td>" + inputDate + "</td>"
					+ "<td style='white-space:pre-line;'>" + inputDescr.replace(/\n/g, "<br>") + "</td>"
					+ "<td>" + inputThumbnail + "</td>"
					+ "<td><a href='#!' onclick='removeChildCollection(\"" + childCollectionId + "\",event)'>Remove</a>"
					+ "<button class='btn btn-link' title='Detach the ChildCollection' style='text-align:right' onclick='removeChildCollection(\"" + childCollectionId + "\",event)'>"
					+ "<span class='glyphicon glyphicon-trash'></span></button></td></tr>");
		});	
		
		request.fail(function (jqXHR, textStatus, errorThrown){
			console.error("The following error occured: "+textStatus, errorThrown);
	        var errMsg = "You must be logged in to add a child collection to a collection.";
	        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
	            notify("The child collection was not added to the collection due to : " + errorThrown, "error");
	        }    		
 		});
		
	}

	//done up to here #tn
	function removeChildCollection(childCollectionId, event){
		
		var request = jsRoutes.api.Collections.removeSubCollection(collectionId, childCollectionId).ajax({
			type: 'POST'
		});
		
		request.done(function (response, textStatus, jqXHR){	        	       
	      //Remove selected dataset from datasets in collection.
	      var rowId = event.target.parentNode.parentNode.getAttribute('data-childcollectionid');
	      var inputDate = $("tr[data-childcollectionid='" + rowId + "'] td:nth-child(2)").text();
	      var inputDescr = $("tr[data-childcollectionid='" + rowId + "'] td:nth-child(3)").html();
	      var inputThumbnail = $("tr[data-childcollectionid='" + rowId + "'] td:nth-child(4)").html();
	      $("#collectionChildCollectionsTable tbody tr[data-childcollectionid='" + rowId + "']").remove();
	      
	      //Add the data back to the uncontained datasets table
	      var newDatasetHTML = "<tr data-childcollectionId='" + childCollectionId + "'><td><a href='#!' "
	      + "onclick='addChildCollection(\"" + childCollection + "\",event)' "
	      + ">"+ event.target.parentNode.parentNode.children[0].children[0].innerHTML + "</a></td>"
	      + "<td>" + inputDate + "</td>"
	      + "<td style='white-space:pre-line;'>" + inputDescr + "</td>"
	      + "<td>" + inputThumbnail + "</td>"
	      + "<td><a target='_blank' href='" + jsRoutes.controllers.Collections.collection(childCollectionId).url + "'>View</a></td></tr>";
	      
	      $('#addChildCollectionsTable tbody').append(newDatasetHTML);
		});  	
		
		request.fail(function (jqXHR, textStatus, errorThrown){
			console.error("The following error occured: "+textStatus, errorThrown);
	        var errMsg = "You must be logged in to remove a child collection from a collection.";
	        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
	            notify("The dataset was not removed from the collection due to : " + errorThrown, "error");
	        }
 		});	
	}
	
	function findPos(reqNode){
		
		var dateString = reqNode.children[1].innerHTML.split(" ");
		dateString[1] = dateString[1].replace(",","");
		dateString[0] = dateString[0].replace("Jan","01");
		dateString[0] = dateString[0].replace("Feb","02");
		dateString[0] = dateString[0].replace("Mar","03");
		dateString[0] = dateString[0].replace("Apr","04");
		dateString[0] = dateString[0].replace("May","05");
		dateString[0] = dateString[0].replace("Jun","06");
		dateString[0] = dateString[0].replace("Jul","07");
		dateString[0] = dateString[0].replace("Aug","08");
		dateString[0] = dateString[0].replace("Sep","09");
		dateString[0] = dateString[0].replace("Oct","10");
		dateString[0] = dateString[0].replace("Nov","11");
		dateString[0] = dateString[0].replace("Dec","12");		
		for(var pos = 1;pos <= searchResultsCount; pos++){
			var currRowDate = $("#addChildCollectionsTable tbody tr[id='resultRow" + pos + "'] td:nth-child(2)").text().split(" ");
			currRowDate[1] = currRowDate[1].replace(",","");
			if(dateString[2] > currRowDate[2])
				return pos;
			else if(dateString[2] < currRowDate[2])
				continue;
			else{
				currRowDate[0] = currRowDate[0].replace("Jan","01");
				currRowDate[0] = currRowDate[0].replace("Feb","02");
				currRowDate[0] = currRowDate[0].replace("Mar","03");
				currRowDate[0] = currRowDate[0].replace("Apr","04");
				currRowDate[0] = currRowDate[0].replace("May","05");
				currRowDate[0] = currRowDate[0].replace("Jun","06");
				currRowDate[0] = currRowDate[0].replace("Jul","07");
				currRowDate[0] = currRowDate[0].replace("Aug","08");
				currRowDate[0] = currRowDate[0].replace("Sep","09");
				currRowDate[0] = currRowDate[0].replace("Oct","10");
				currRowDate[0] = currRowDate[0].replace("Nov","11");
				currRowDate[0] = currRowDate[0].replace("Dec","12");
				if(dateString[0] > currRowDate[0])
					return pos;
				else if(dateString[0] < currRowDate[0])
					continue;
				else
					if(dateString[1] > currRowDate[1])
						return pos;
					else if(dateString[1] < currRowDate[1])
						continue;
					else
						return pos;
			}	
		}
		return searchResultsCount+1;
	}
	
	
	childCollectionsInCollection.slice(0,10).each(function() {
			$(this).css('display','table-row');	
	});
	if(childCollectionsInCollection.length > 10)
		$('#childCollectionsPagerNext').css('visibility','visible');
	childCollectionsInCollection.each(function() {
		$(this).attr("id","childCollectionRow" + elementCounterChildCollections);
		elementCounterChildCollections++;
	});
	
	 $('body').on('click','#childCollectionsPagerNext',function(e){
		 currentFirstChildCollections = currentFirstCollections + 10;
		 $("#collectionChildCollectionsTable tbody tr").each(function() {
        	    $(this).css('display','none');
         });
		 for(var i = currentFirstChildCollections; i < currentFirstChildCollections + 10; i++){
			 $("#collectionChildCollectionsTable tbody tr[id='childCollectionRow" + i + "']").each(function() {
				 $(this).css('display','table-row');
			 });
		 }
		 $('#childCollectionsPagerPrev').css('visibility','visible');
		 if(currentFirstChildCollections + 10 > childCollectionsInCollectionCount)
			 $('#childCollectionsPagerNext').css('visibility','hidden');
		 
		 return false;
	 });
	 $('body').on('click','#childCollectionsPagerPrev',function(e){
		 currentFirstChildCollections = currentFirstChildCollections - 10;
		 $("#collectionChildCollectionsTable tbody tr").each(function() {
        	    $(this).css('display','none');
         });
		 for(var i = currentFirstChildCollections; i < currentFirstChildCollections + 10; i++){
			 $("#collectionChildCollectionsTable tbody tr[id='childCollections" + i + "']").each(function() {
				 $(this).css('display','table-row');
			 });
		 }
		 if(currentFirstChildCollections + 10 <= childCollectionsInCollectionCount)
			 $('#childCollectionsPagerNext').css('visibility','visible');
		 if(currentFirstChildCollections == 1)
			 $('#childCollectionsPagerPrev').css('visibility','hidden');
		 
		 return false;
	 });

	//TODO - MMF - Is this really necessary? The list of available datasets that are external to the collection should be available already.
	//This would also unify the htmlDecoding on the server side instead of having to happen both here and there.
	//Note - need to make the "replace" calls below more generic.
	 $('body').on('click','#addChildCollectionBtn',function(e){
			var request = $.ajax({
		       type: 'GET',
		       url: queryIp,
		       dataType: "json",
		     });
			
			request.done(function (respJSON){
		        console.log("Response " + respJSON);
		        $('#addPagerPrev').css('visibility','hidden');
		        $('#addPagerNext').css('visibility','hidden');
		        searchResultsCount = respJSON.length;
		        $('#addDatasetsTable tbody tr').remove();
		        for(var i = 0; i < respJSON.length; i++){
		        	var createdDateArray = respJSON[i].created.split(" ");
		        	var createdDate = createdDateArray.slice(1,3).join(" ") + ", " + createdDateArray[5];
		        	var datasetThumbnail = "";
		        	if(respJSON[i].thumbnail != "None")
		        		datasetThumbnail = "<img src='" + window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '') + "/fileThumbnail/" + respJSON[i].thumbnail + "/blob' "
		        							+ "alt='Thumbnail of " + respJSON[i].datasetname.replace(/\n/g, "<br>") + "' width='120'>";
		        	else
		        		datasetThumbnail = "No thumbnail available"

		        	$('#addDatasetsTable tbody').append("<tr id='resultRow" + (i+1) + "' style='display:none;' data-datasetId='" + respJSON[i].id + "'><td><a href='#!' "
		        								+ "onclick='addDataset(\"" + respJSON[i].id + "\",event)' "
		        								+ ">"+ respJSON[i].datasetname.replace(/\n/g, "<br>") + "</a></td>"
		        								+ "<td>" + createdDate + "</td>"
		        								+ "<td style='white-space:pre-line;'>" + respJSON[i].description.replace(/\n/g, "<br>") + "</td>"
		        								+ "<td>" + datasetThumbnail + "</td>"
		        								+ "<td><a target='_blank' href='" +  jsRoutes.controllers.Datasets.dataset(respJSON[i].id).url + "'>View</a></td></tr>");
		        	
		        }
		        $('#addDatasetsTable').show();
		        
		        for(var i = 0; i < 10; i++){
		        	$("#addDatasetsTable tbody tr[id='resultRow" + (i+1) + "']").each(function() {
		        	    $(this).css('display','table-row');
		        	});
		        }
		        
		        if(respJSON.length > 10){
		        	currentFirstAdd = 1;
		        	$('#addPagerNext').css('visibility','visible');
		        }
		        
		        $("#hideAddDatasetBtn").show();
		        areRestDatasetsVisible = true;
		        
		        return false;
 			});
			request.fail(function (jqXHR, textStatus, errorThrown){
				console.error("The following error occured: "+textStatus, errorThrown);
		        var errMsg = "You must be logged in to add a dataset to a collection.";
		        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
		            notify("The dataset was not added to the collection due to : " + errorThrown, "error");
		        }        		        		
        		return false;
     		});		 
	 });
	 $('body').on('click','#hideAddDatasetBtn',function(e){
		 $('#addPagerPrev').css('visibility','hidden');
	     $('#addPagerNext').css('visibility','hidden');
	     $('#addDatasetsTable tbody tr').remove();
	     $('#addDatasetsTable').css('display','none');
	     $('#hideAddDatasetBtn').css('display','none');
	     areRestDatasetsVisible = false;
	     
	     return false;
	 });
	
	 $('body').on('click','#addPagerNext',function(e){
		 currentFirstAdd = currentFirstAdd + 10;
		 $("#addDatasetsTable tbody tr").each(function() {
        	    $(this).css('display','none');
         });
		 for(var i = currentFirstAdd; i < currentFirstAdd + 10; i++){
			 $("#addDatasetsTable tbody tr[id='resultRow" + i + "']").each(function() {
				 $(this).css('display','table-row');
			 });
		 }
		 $('#addPagerPrev').css('visibility','visible');
		 if(currentFirstAdd + 10 > searchResultsCount)
			 $('#addPagerNext').css('visibility','hidden');
		 
		 return false;
	 });
	 $('body').on('click','#addPagerPrev',function(e){
		 currentFirstAdd = currentFirstAdd - 10;
		 $("#addDatasetsTable tbody tr").each(function() {
        	    $(this).css('display','none');
         });
		 for(var i = currentFirstAdd; i < currentFirstAdd + 10; i++){
			 $("#addDatasetsTable tbody tr[id='resultRow" + i + "']").each(function() {
				 $(this).css('display','table-row');
			 });
		 }
		 if(currentFirstAdd + 10 <= searchResultsCount)
			 $('#addPagerNext').css('visibility','visible');
		 if(currentFirstAdd == 1)
			 $('#addPagerPrev').css('visibility','hidden');
		 
		 return false;
	 });	
		
		
