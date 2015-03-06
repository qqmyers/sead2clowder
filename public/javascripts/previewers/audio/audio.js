(function ($, Configuration) {
  console.log("audio previewer for " + Configuration.id);
  
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append("<br/>");
  $(Configuration.tab).append("<audio id='ouraudio' controls><source src='" + Configuration.url + "'></source></audio>");
     
}(jQuery, Configuration));
