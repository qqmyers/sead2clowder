$(document).ready(function() {

  // this function performs a GET request on a specified "url"
  // and optionally a parameters object
  // and returns immediately with a deferred object
  var deferredGet = function(url, parameters) {

    var deferred = $.Deferred();

    $.get( url, parameters, function() {
      deferred.notify( "deferred notify GET for " + url );
    })
      .done(function(data) {
        deferred.notify( "deferred notify done for " + url );
        deferred.resolve( data );
      })
      .fail(function(err) {
        deferred.notify( "deferred notify fail for " + url);
        deferred.reject(err)
      });

    return deferred.promise();
  };

  // this function performs a POST request on a specified "url"
  // with specified "data" and returns immediately with a deferred object
  var deferredPost = function(url, data) {

    var deferred = $.Deferred();

    $.ajax( {
      url: url,
      type: 'POST',
      contentType: 'application/json',
      data: data
    })
      .done(function(data) {
        deferred.notify( "deferred notify done for " + url );
        deferred.resolve( data );
      })
      .fail(function(err) {
        deferred.notify( "deferred notify fail for " + url);
        deferred.reject(err)
      });

    return deferred.promise();
  };

  // setup form validation
  var sensorForm = $('#sensor-edit');
  sensorForm.validate({
    messages: {
      sensorFullName: "You must provide a name",
      sensor_name: "You must provide a sensor ID",
      sensorDataSource: "You must identify the provider of this data",
      sensorRegion: "You must specify the region. All sensors are grouped into regions on the dashboard."
    }
  });

  // set the link to sensor types dynamically - TODO store this in the sensor config
  var sensorTypesUrl = "https://opensource.ncsa.illinois.edu/confluence/display/IMLCZO/Data+Types";
  var sensorTypesUrlElement = $("#sensorTypesUrl");
  sensorTypesUrlElement.attr('href', sensorTypesUrl);

  var insertInstrumentForm = function(data) {
    var parametersTemplate = Handlebars.getTemplate(jsRoutes.controllers.Assets.at("templates/sensors/parameters-form").url);
    Handlebars.registerPartial('parameters', parametersTemplate);
    var instrumentTemplate = Handlebars.getTemplate(jsRoutes.controllers.Assets.at("templates/sensors/stream-form").url);
    $("#instruments").append(instrumentTemplate(data));
  };


  // set to a high number to prevent colliding with stream IDs TODO improve this
  var instrumentCounter = 500000;
  var addInstrumentButton = $("#addInstrument");
  addInstrumentButton.on('click', instrumentCounter, function() {
    instrumentCounter++;
    var data = {id: instrumentCounter.toString()};
    insertInstrumentForm(data);
  });

  // add the first sensor on page load
  addInstrumentButton.click();

  $("#instruments").on('click', '.removeInstrument', function() {
    console.log($(this).data('id'));
    var instrumentNumber = $(this).data('id');
    $("#instrument-" + instrumentNumber).remove();
  });

  // enable tooltips
  $('[data-toggle="tooltip"]').tooltip();

  $("#formSubmit").click(function(event) {
    event.preventDefault();
    if (!$("#sensor-edit").valid()) {
      return;
    }
    var sensorsValid = true;

    $('.stream-tmpl').each(function() {

      $(this).validate({
        ignore: false,
        messages: {
          instrumentName: "You must provide a name for this instrument"
        }
      });
      if (!$(this).valid()) {
        $(this).find('.collapse').collapse('show');
        sensorsValid = false;
        return false;
      }
    });

    if (!sensorsValid) {
      return;
    }


    var clowderSensorsURL = jsRoutes.api.Geostreams.searchSensors().url;
    var clowderStreamsURL = jsRoutes.api.Geostreams.searchStreams().url;
    var data = {geometry: { type: "Point", coordinates: [0,0,0]}, properties: { type: {id: "", "title": ""}}, type: "Feature"};
    data.name = $("#sensor_name").val();
    data.properties.name = data.name;
    data.properties.popupContent = $("#sensorFullName").val();
    data.geometry = JSON.parse($("#sensorLocation").val());
    data.properties.type.id = $("#sensorDataSource").val().toLowerCase();
    data.properties.type.title = $("#sensorDataSource").val();
    data.properties.type.sensorType = +$("#sensorType").val();
    data.properties.region = $("#sensorRegion").val();

    var sensorPOSTpromise = deferredPost(clowderSensorsURL, JSON.stringify(data));

    var deferredStreams = [];

    $.when(sensorPOSTpromise).done(function() {
      var sensorGETpromise = deferredGet(clowderSensorsURL + '?geojson=' + JSON.stringify(data.geometry));

      $.when(sensorGETpromise).done(function(sensorData) {
        var sensorJSON = sensorData[0];
        $(".stream-tmpl").each(function() {

          var streamJSON = {};
          var streamData = $(this).find(':input').filter(function() {return $.trim(this.value).length > 0}).serializeJSON({
            parseNumbers: true,
            parseBooleans: true
          });

          streamJSON['name'] = streamData['instrumentName'];
          delete streamData['instrumentName'];
          streamJSON['properties'] = streamData;

          streamJSON['geometry'] = sensorJSON['geometry'];
          streamJSON['sensor_id'] = sensorJSON['id'].toString();
          streamJSON['type'] = sensorJSON['type'];
          deferredStreams.push(deferredPost(clowderStreamsURL, JSON.stringify(streamJSON)));
        });

        $.when.apply($, deferredStreams).done(function(data) {
          // redirect removing the "/new" from the current href
          // necessary until we add the Geostreams to the @controllers
          window.location.href = window.location.href.substring(0, window.location.href.length - 4);
        });
        
      });
    });


  });
});