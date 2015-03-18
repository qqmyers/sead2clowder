$(function() {
  var request = jsRoutes.api.Geostreams.searchSensors().ajax({
    type: 'GET',
    contentType: "application/json",
    dataType: 'json',
  });

  request.done(function (response, textStatus, jqXHR){
    response.forEach(function(sensor) {
      var currentURL = window.location.href;
      $('table tr:last').after("<tr> \
	                <td><a href='" + currentURL + "/" + sensor.id + "'>Sensor " + sensor.id + "</a></td> \
	                <td>" + sensor.name + "</td> \
	                <td>" + sensor.properties.type.id + "</td> \
	                <td>lat: " + sensor.geometry.coordinates[1] + ", long: " + sensor.geometry.coordinates[0] + "</td> \
	                <td style='max-height: 100px'><div style='height: 100px; overflow: auto'>" + sensor.parameters.join("<br />") + "</div></td> \
	                <td><div class='btn btn-primary submit delete-sensor' id='" + sensor.id + "'>Delete Sensor #" + sensor.id + "</div></td> \
	                </tr>");
    });

    $('.delete-sensor').on('click', function (event) {
      if (confirm("Delete Sensor, all its Streams, and all its Datapoints?")) {
           var request = jsRoutes.api.Geostreams.deleteSensor(event.target.id).ajax({
             type: 'DELETE',
             contentType: "application/json",
             dataType: 'json',
             data: '{}'
           });
          request.done(function(response, textStatus, jqXHR) {
            window.location.href = window.location.href;
          });
          request.fail(function(jqXHR, textStatus, errorThrown) {
            window.alert("Could not delete the sensor.");
            console.error("Could not delete the sensor: " + textStatus + errorThrown);
          });
      }

    });

  });

  request.fail(function (jqXHR, textStatus, errorThrown){
    console.error(
      "The following error occured: "+
      textStatus, errorThrown
    );
  });

});