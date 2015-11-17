(function($, Configuration) {

	function checkForLatLng(json_obj) {
		/**
		 * Iterate through object looking for lat/lon data under several possible fields
		 */
		var coords_found = [];
		Object.keys(json_obj).forEach(function(key){
			var lat=null, lon=null;
			var lat_names = ["latitude", "lat", "y"];
			var lon_names = ["longitude", "lon", "long", "x"];

			for (var l in lat_names) {
				if (json_obj[key].hasOwnProperty(lat_names[l])) {
					lat = json_obj[key][lat_names[l]];
					break;
				}
			}
			for (var l in lon_names) {
				if (json_obj[key].hasOwnProperty(lon_names[l])) {
					lon = json_obj[key][lon_names[l]];
					break;
				}
			}

			if (lat != null && lon != null) {
				coords_found.push([lat,lon]);
			}
		});
		return coords_found;
	}

	function checkForGeoJSON(json_obj) {
		/**
		 * Iterate through object looking for geojson data field
		 */
		var geojson_found = [];
		// We can load a FeatureCollection directly
		if (json_obj.hasOwnProperty("features") &&
				json_obj.hasOwnProperty("type") && json_obj["type"] == "FeatureCollection") {
			geojson_found.push(json_obj);
		}
		// We can load a Feature directly, but not a primitive geometry (e.g. Point) or GeometryCollection
		else if (json_obj.hasOwnProperty("geometry") &&
				json_obj.hasOwnProperty("type") && json_obj["type"] == "Feature") {
			geojson_found.push(json_obj);
		}
		// If we haven't found any GeoJSON, we keep looking deeper in the object
		else {
			if (typeof json_obj == 'object') {
				Object.keys(json_obj).forEach(function (key) {
					geojson_found = geojson_found.concat(checkForGeoJSON(json_obj[key]));
				});
			}
		}
		return geojson_found;
	}

	function initializeMap(center_lat, center_lon) {
		/**
		 * Initial prep of map canvas - only should happen once per page load
		 */
		center_lat = center_lat || 0;
		center_lon = center_lon || 0;

		// Apply CSS
		$("<link/>", {
			rel: "stylesheet",
			type: "text/css",
			href: Configuration.path+"main.css"
		}).appendTo(Configuration.div);

		// Add map canvas to the main Dataset page
		$(Configuration.div).append("<h4>Dataset Location</h4><div id='ds-coords-canvas'></div>");

		var mapOptions = {
			center: {lat: center_lat, lng: center_lon},
			zoom: 8,
			mapTypeId: google.maps.MapTypeId.SATELLITE
		};

		return new google.maps.Map(document.getElementById('ds-coords-canvas'), mapOptions);
	}

	function loadGeoJsonObject(gmap, geo_obj, file_url) {
		/**
		 * Parse a GeoJSON string and add to given google map, then adjust view
		 * @param file_url will be the link on this object's points, if it doesnt exist
		 */

		if (geo_obj.type == "Feature") {
			if (!geo_obj.hasOwnProperty("properties")) geo_obj["properties"] = {};
			if (!(file_url === undefined) && !geo_obj["properties"].hasOwnProperty("_file_url")) {
				geo_obj["properties"]["_file_url"] = file_url
			}
		} else {
			Object.keys(geo_obj.features).forEach(function(key){
				if (!geo_obj.features[key].hasOwnProperty("properties")) geo_obj.features[key]["properties"] = {};
				if (!(file_url === undefined) && !geo_obj.features[key]["properties"].hasOwnProperty("_file_url")) {
					geo_obj.features[key]["properties"]["_file_url"] = file_url
				}
			});
		}
		gmap.data.addGeoJson(geo_obj);

		// Update the map bounds to fit all our current geometry
		var bounds = new google.maps.LatLngBounds();
		gmap.data.forEach(function(feature) {
			// TODO - add logic to cluster features in same location - perhaps with paging?
			processPoints(feature.getGeometry(), bounds.extend, bounds);
		});
		gmap.fitBounds(bounds);
	}

	function processPoints(geometry, callback, this_arg) {
		/**
		 * Process each point in geometry with callback fn
		 * @param callback is function to call on each LatLng point encountered (e.g. Array.push)
		 * @param this_arg is the value of 'this' as provided to 'callback'
		 * Adapted from https://developers.google.com/maps/documentation/javascript/examples/layer-data-dragndrop
		 */
		if (geometry instanceof google.maps.LatLng) {
			callback.call(this_arg, geometry);
		} else if (geometry instanceof google.maps.Data.Point) {
			callback.call(this_arg, geometry.get());
		} else {
			geometry.getArray().forEach(function(g) {
				processPoints(g, callback, this_arg);
			});
		}
	}

	function buildPopupContentFromJSON(json_obj, leading) {
		/**
		 * Take a JSON object and create some nice display HTML for it.
		 * @param leading allows one to specify indents, mostly used for recursive sub-objects
		 */
		var indent = '&nbsp;&nbsp;&nbsp;&nbsp;';
		var content = '';
		leading = leading || '';

		// GMaps creates the ["O"] sub-object structure on GeoJSON load
		if (json_obj.hasOwnProperty("O")) {
			// Put link to [_file_url] at the top, with [title] as link text if we find it
			if (json_obj["O"].hasOwnProperty("_file_url")) {
				content = leading+'<a href="'+json_obj["O"]["_file_url"]+'"><b>'
				if (json_obj["O"].hasOwnProperty("title")) {
					content = content.concat(json_obj["O"]["title"]);
				} else {
					content = content.concat("View File in Clowder");
				}
				content = content.concat('</b></a></br></br>');
			// Or just put the title if we don't have a [_file_url]
			} else {
				if (json_obj["O"].hasOwnProperty("title")) {
					content = leading+'<b>'+json_obj["O"]["title"]+'</b></br></br>';
				}
			}

			// Add thumbnail below title if it's available
			if (json_obj["O"].hasOwnProperty("_thumbnail")) {
				content = content.concat('<img class="img-thumbnail ds-coords-img-tmb" src="'+json_obj["O"]["_thumbnail"]+'"></br></br>');
			}

			// Format the JSON object for human eyes, indenting sub-objects where we find them
			for (p in json_obj["O"]) {
				if (json_obj["O"][p] instanceof Object) {
					content = content.concat(leading+'<b>'+p+':</b></br>'+buildPopupContentFromJSON(json_obj["O"][p], leading+indent));
				} else {
					if (p[0] != "_") { // treat params with leading '_' as hidden
						content = content.concat(leading + '<b>' + p + ':</b> ' + json_obj["O"][p] + '</br>');
					}
				}
			}
		} else {
			// If we don't have ["O"] GeoJSON properties, just show everything raw
			for (p in json_obj) {
				if (json_obj[p] instanceof Object) {
					content = content.concat(leading+'<b>'+p+':</b></br>'+buildPopupContentFromJSON(json_obj[p], leading+indent));
				} else {
					if (p[0] != "_") { // treat params with leading '_' as hidden
						content = content.concat(leading+'<b>'+p+':</b> '+json_obj[p]+'</br>');
					}
				}
			}
		}
		return content;
	}


	var dataset_id = Configuration.dataset_id;

	// Request list of files in this dataset
	var req = $.ajax({
		type: "GET",
		url: "/api/datasets/"+dataset_id+"/listFiles",
		dataType: "json"
	});

	req.done(function(data){
		// We found at least one file...
		if (data.length > 0){
			var map = null;
			var infowindow = new google.maps.InfoWindow({content: ''});

			// For each file in the dataset...
			for(var file_details in data){
				// Request the technical metadata
				// TODO - replace this with GeoJSON endpoint call
				var file_md_req = $.ajax({
					type: "GET",
					url: "/api/files/"+data[file_details]["id"]+"/technicalmetadatajson",
					dataType: "json"
				});

				file_md_req.done(function(file_data) {
					if (file_data.length > 0) {
						var file_url = '/files/'+file_data[0]["id"];

						// Check for geojson members in the metadata, and add to map
						var geojson = checkForGeoJSON(file_data, file_url);
						if (geojson.length > 0) {
							if (map == null) {
								// Set up the map first if necessary
								map = initializeMap(0, 0);
								map.data.addListener('click', function(event) {
									var popup_content = buildPopupContentFromJSON(event.feature);
									infowindow.setContent(popup_content);
									infowindow.open(map);
									infowindow.setPosition(event.latLng);
								});
							}

							Object.keys(geojson).forEach(function(gj){
								loadGeoJsonObject(map, geojson[gj], file_url);
							});
						}

						// Check for lat/lon members in the metadata, and add to map
						var coords = checkForLatLng(file_data);
						if (coords.length > 0) {
							if (map == null) {
								// Set up the map first if necessary
								map = initializeMap(coords[0][0], coords[0][1]);
								map.data.addListener('click', function(event) {
									var popup_content = buildPopupContentFromJSON(event.feature);
									infowindow.setContent(popup_content);
									infowindow.open(map);
									infowindow.setPosition(event.latLng);
								});
							}

							Object.keys(coords).forEach(function(c) {
								// We'll create a little geojson object from the lat/lon so we can load the same way
								var geojson_point = {
									"type": "Feature",
									"properties": {
										"_file_url": file_url,
										"_thumbnail": file_url+'/blob', // TODO - make this actually point to thumbnails instead of the file itself
										"title": file_url
									},
									"geometry": {
										"type": "Point",
										"coordinates": [coords[c][1], coords[c][0]] // X,Y (LON,LAT)
									}
								};

								loadGeoJsonObject(map, geojson_point, file_url);
							});
						}
					}
				});
			}
		}
	});
}(jQuery, Configuration));