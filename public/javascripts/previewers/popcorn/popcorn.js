(function ($, Configuration) {
  console.log("video previewer for " + Configuration.id);
  
  var pathJs = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '') + Configuration.jsPath + "/";
  
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "popcorn-complete.js";
    
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s);  
  $(Configuration.tab).append("<br/>");
  $(Configuration.tab).append(			  
     "<video width='750px' id='ourvideo_"+Configuration.originalFileId+"' controls><source src='" + Configuration.url + "'></source></video>"
  );
  
  
}(jQuery, Configuration));