 function expandGeocodes() {
            $('.listgeocode').each(function() {
                var geoElement = this;
                if(!$(geoElement).hasClass('expanded')) {
                	$(geoElement).addClass('expanded');
                	var contents =  $(geoElement).find('span');
                	var json = JSON.parse($(contents).text());
                	var newElem = $("<span/>").prependTo($(geoElement));
                	$(newElem).append($("<span/>").attr("title","Name").addClass("placename").text(json["http://www.w3.org/2000/01/rdf-schema#label"]+": "));
                	$(newElem).append($("<span/>").attr("title","WGS84 Latitude").text(json["http://www.w3.org/2003/01/geo/wgs84_pos#lat"]+", "));
                	$(newElem).append($("<span/>").attr("title","WGS84 Longitude").text(json["http://www.w3.org/2003/01/geo/wgs84_pos#lon"]));
                	$(contents).remove();
                }
                	
            });
            
        }
 
 $( function() {
	 expandGeocodes();
 })