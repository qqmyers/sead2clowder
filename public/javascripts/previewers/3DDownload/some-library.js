// Author: Constantinos Sophocleous
function NavigateToSite(prNum){
    var ddl = document.getElementById("ddlMyList");
    var selectedVal = ddl.options[ddl.selectedIndex].value;

    window.open(selectedVal)
}

(function ($, Configuration) {
  console.log("3D model download interface for " + Configuration.id);
  
  console.log("Updating tab " + Configuration.tab);
  
  var prNum = Configuration.tab.replace("#previewer","");
  window["download"+prNum]
  
  var hostAddress = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')
  $(Configuration.tab).append("<a href='" + hostAddress+ Configuration.url + "'>Download base 3D model generated from PTM file</a>");

}(jQuery, Configuration));