(function ($, Configuration) {
	console.log("Gigaimage previewer (IIP-JS) for " + Configuration.id);
	
	console.log("Updating tab " + Configuration.tab);
	
	var width = 750;
	var height = 550;
	
	var prNum = Configuration.tab.replace("#previewer","");
	
	var hostAddress = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '');
		
	var pathJs = hostAddress + Configuration.jsPath + "/";
	var pathImages = hostAddress + Configuration.imagesPath + "/";
	var pathCss = hostAddress + Configuration.stylesheetsPath + "/";
	var iframeURL = hostAddress + Configuration.iipIframe;
	
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