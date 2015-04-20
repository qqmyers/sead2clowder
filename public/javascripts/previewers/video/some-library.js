// Author: Constantinos Sophocleous
(function ($, Configuration) {
  console.log("video previewer for " + Configuration.id);

  var height = 400;  
  var width = 750;
  
  var hostAddress = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')
  $(Configuration.tab).append(
		  "<object classid='clsid:02BF25D5-8C17-4B23-BC80-D3488ABDDC6B' height='"+ height +"' width='"+ width +"'>"+
		  "<param name='src' value='" + hostAddress+ Configuration.url   +"' />"+
		  "<param name='autoplay' value='false'>"+
		  "<param name='controller' value='true'>"+
		  "<param name='scale' value='tofit'>"+
		  "<embed type='video/quicktime' alt='No video plugin capable of playing this video was found.' src='" + hostAddress+ Configuration.url   +"' height='"+ height +"' width='"+ width +"' autoplay='false' controller='true' scale='tofit'></embed>"+
		  "</object>"		  
  );

}(jQuery, Configuration));
