(function ($, Configuration) {
	console.log("Gigaimage previewer (multispectral) for " + Configuration.id);
	
	console.log("Updating tab " + Configuration.tab);
	
	var width = 750;
	var height = 550;
	
	var prNum = Configuration.tab.replace("#previewer","");
		
	var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
	var pathImages = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.imagesPath + "/";
	var pathCss = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.stylesheetsPath + "/";
	var iframeURL = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.multispectralIframe;
	
	$(Configuration.tab).append("<p>Position mouse pointer over IIP logo (top left of image viewer) for help navigating.</p>");
	
	  $.ajax({
		    url: Configuration.url,
		    async:false,
		    success: function (data) {
		    	var uploadDirs = data.split("\n");
		    	var uploadDirsArrays = new Array();
		    	for(var i = 0; i < uploadDirs.length-1; i++){
		    		var uploadDirSplit = uploadDirs[i].split(",");
		    		uploadDirsArrays[i] =  new Array();
		    		uploadDirsArrays[i][0] = uploadDirSplit[0];
		    		uploadDirsArrays[i][1] = uploadDirSplit[1];
		    		uploadDirsArrays[i][1] = uploadDirsArrays[i][1].replace("_"," ");
		    	}
		    	
		    	window["currentMultispectral" + prNum] = uploadDirsArrays;
		    	window["multispectralServer" + prNum] = uploadDirs[0].split(",")[2];
		    			    	
		    	$(Configuration.tab).append(
		   		     "<iframe style='width: " + width + "px; height: " + height + "px' id='multispectralIframe" + prNum + "' src='" + iframeURL
		   		     +"?pathJs=" + encodeURIComponent(pathJs) + "&pathCss=" + encodeURIComponent(pathCss) + "&pathImages=" + encodeURIComponent(pathImages) +"'></iframe>"
		   		  );
		    			    			    	
		    	 },
		    	 error: function(jqXHR, textStatus, errorThrown) { 
		    	        alert("Status: " + textStatus); alert("Error: " + errorThrown); 
		    	    },
		    dataType: 'text'
		});

	 			
}(jQuery, Configuration));