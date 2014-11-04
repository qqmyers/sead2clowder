(function ($, Configuration) {
  console.log("video presentation previewer for " + Configuration.id);  
  console.log("Updating tab " + Configuration.tab);
  
  var pathJs = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '') + Configuration.jsPath + "/";
  
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "popcorn-complete.js";
  $(Configuration.tab).append(s);
  
  $.ajax({
	    url: Configuration.url,
	    async:false,
	    success: function (data) {
	    	  var videosIds = data.split("\n");
	    		
	    	  //Slides only
	    	  $(Configuration.tab).append("<br/>");
	    	  $(Configuration.tab).append(			  
	    	     "<video width='750px' id='vid_"+videosIds[0]+"' controls><source src='" + jsRoutes.api.Previews.download(videosIds[0]).url  + "'></source></video>"
	    	  );
	    	  
	    	  //Medium-quality
	    	  $(Configuration.tab).append("<br/>");
	    	  $(Configuration.tab).append(			  
	    	     "<video width='750px' id='vid_"+videosIds[1]+"' controls><source src='" + jsRoutes.api.Previews.download(videosIds[1]).url  + "'></source></video>"
	    	  );
	    	  
	    	  //High-quality
	    	  $(Configuration.tab).append("<br/>");
	    	  $(Configuration.tab).append(			  
	    	     "<video width='750px' id='vid_"+videosIds[2]+"' controls><source src='" + jsRoutes.api.Previews.download(videosIds[2]).url  + "'></source></video>"
	    	  );
	    		    		    			    	
	    	 },
	    	 error: function(jqXHR, textStatus, errorThrown) { 
	    	        alert("Status: " + textStatus); alert("Error: " + errorThrown); 
	    	    },
	    dataType: 'text'
	});
  

}(jQuery, Configuration));