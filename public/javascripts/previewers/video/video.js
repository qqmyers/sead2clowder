(function ($, Configuration) {
  console.log("Starting video previewer for " + Configuration.id);
  
  $(Configuration.tab).append("<br/><p><b>Important: </b>Do not use this previewer on computers using a second screen, as there are some issues. Use the Quicktime-based one instead.</p>");	
      
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(			  
     "<video width='750px' id='ourvideo' controls><source src='" + Configuration.url + "'></source></video>"
  );

}(jQuery, Configuration));
