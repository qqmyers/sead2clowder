(function ($, Configuration) {
	console.log("Gigaimage previewer (IIP-JS) for " + Configuration.id);
	
	console.log("Updating tab " + Configuration.tab);
	
	var width = 750;
	var height = 550;
	
	var prNum = Configuration.tab.replace("#previewer","");
		
	var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
	var pathImages = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.imagesPath + "/";
	var pathCss = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.stylesheetsPath + "/";
	var iframeURL = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.iipIframe;
	
	$(Configuration.tab).append("<p>Position mouse pointer over IIP logo (top left of image viewer) for help navigating.</p>");
	
	  $.ajax({
		    url: Configuration.url,
		    async:false,
		    success: function (data) {
		    	var uploadDirs = data.split("\n");
		    	
		    	var theserver = uploadDirs[0].substring(8);
		    	var theimage = uploadDirs[1].substring(7);
		    			    	
		    	$(Configuration.tab).append(
		   		     "<iframe style='width: " + width + "px; height: " + height + "px' id='iipIframe" + prNum + "' src='" + iframeURL+"?image="+encodeURIComponent(theimage)
		   		     +"&server=" +encodeURIComponent(theserver)+"&pathJs=" + encodeURIComponent(pathJs) + "&pathCss=" + encodeURIComponent(pathCss) + "&pathImages=" + encodeURIComponent(pathImages) +"'></iframe>"
		   		  );
		    			    			    	
		    	 },
		    	 error: function(jqXHR, textStatus, errorThrown) { 
		    	        alert("Status: " + textStatus); alert("Error: " + errorThrown); 
		    	    },
		    dataType: 'text'
		});

	 			
}(jQuery, Configuration));