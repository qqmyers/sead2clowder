(function ($, Configuration) {
  console.log("video presentation previewer for " + Configuration.id);  
  console.log("Updating tab " + Configuration.tab);
  
  var hostAddress = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '');
  var pathJs = hostAddress + Configuration.jsPath + "/";
  var pathImages = hostAddress + Configuration.imagesPath + "/";
  
  var useTab = Configuration.tab;
  var referenceUrl = Configuration.url;
  
  var mediumQualityLimit = (Configuration.presentationsMediumQualityLimit > 0 ? Configuration.presentationsMediumQualityLimit : 256) * 1000;
  var highQualityLimit = (Configuration.presentationsHighQualityLimit > 0 ? Configuration.presentationsHighQualityLimit : 512) * 1000;
  //Calculate user's connection speed
  var imageAddr = pathImages +"connectionSpeedTester.jpg" + "?n=" + Math.random();
  var startTime, endTime;
  var downloadSize = 5616998;
  var download = new Image();
  download.onload = function () {
      endTime = (new Date()).getTime();
      var duration = (endTime - startTime) / 1000;
      var bitsLoaded = downloadSize * 8;
      var speedbps = (bitsLoaded / duration).toFixed(2);
      var usedVersion = 0;
      //0=slides only, 1=medium-quality, 2=high-quality
      if(speedbps >= highQualityLimit)
    	  usedVersion = 2;
      else if(speedbps >= mediumQualityLimit)
    	  usedVersion = 1;
      
      var s = document.createElement("script");
      s.type = "text/javascript";
      s.src = pathJs + "popcorn-complete.js";
      $(useTab).append(s);
      
      $.ajax({
  	    url: referenceUrl,
  	    async:true,
  	    success: function (data) {
  	    	  var videosIds = data.split("\n");
  	    		
  	    	  $(useTab).append("<br/>");
  	    	  $(useTab).append(			  
  	    	     "<video width='750px' id='vid_"+videosIds[usedVersion]+"' controls><source src='" + jsRoutes.api.Previews.download(videosIds[usedVersion]).url  + "'></source></video>"
  	    	  );    		    			    	
  	    	 },
  	    	 error: function(jqXHR, textStatus, errorThrown) { 
  	    	        alert("Status: " + textStatus); alert("Error: " + errorThrown); 
  	    	    },
  	    dataType: 'text'
  	});
      
  }
  startTime = (new Date()).getTime();
  download.src = imageAddr;
  

}(jQuery, Configuration));

