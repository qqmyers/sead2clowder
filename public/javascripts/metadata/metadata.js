// Assumption: the input list contains: <cat_short_name>;<cat_display_name> or <cat_short_name>:<var_name>, where <cat_display_name> contains no ":", and <var_name> contains no ";".
$.widget("custom.catcomplete", $.ui.autocomplete, {
	_create : function() {
		this._super();
		this.widget().menu("option", "items",
				"> :not(.ui-autocomplete-category)");
	},
	_renderMenu : function(ul, items) {
		var that = this;
		var currentCategory = "";
		var category = "";
		var dispCatMap = new Map();
		$.each(items, function(index, item) {
			// console.log("in renderMenu: item: ", item);
			var label = item.label;
			var char_index = label.indexOf(";");
			if (char_index > -1) {
				var shortName = label.slice(0, char_index);
				var displName = label.slice(char_index + 1);
				// console.log("Setting category name '" + shortName + "' to '"
				// + displName + "'");
				dispCatMap.set(shortName, displName);
			}
		});
		$.each(items, function(index, item) {
			var li;
			// Each item from an original array of strings was converted to an
			// object { label: "val1", value: "val1" }.
			// console.log("item: ", item);
			var label = item.label;
			var char_index = label.indexOf(":");
			if (char_index > -1) {
				category = label.slice(0, char_index);
				item.label = label.slice(char_index + 1);
			}
			if (category != currentCategory) {
				// console.log("category changed from " + currentCategory + "
				// to: " + category);
				var displayed_cat_name = category;
				if (dispCatMap.has(category)) {
					displayed_cat_name = dispCatMap.get(category);
				}
				ul.append("<li class='ui-autocomplete-category'>"
						+ displayed_cat_name + "</li>");
				currentCategory = category;
			}
			// Used to be: Display only the items containing ":".
			// Don't display the items containing ";".
			if (label.indexOf(";") <= 0) {
				li = that._renderItemData(ul, item);
			}
		});
	}
});

function displayAddEditForm(id, elem, md_id, footerTemplate, templateRoute) {

	var space_id = $(elem).attr("space_id");
	var field_id = $(elem).val();
	var footerHtml = "";
	if (md_id != null) {
		$('#actionLabel').text("Edit Existing Metadata");
	} else {
		$('#actionLabel').text("Add Metadata");
	}
	if (typeof space_id !== 'undefined' && showSpaceInfo) {
		var request2 = jsRoutes.api.Spaces.get(space_id).ajax({
			type : 'GET',
			contentType : "application/json"
		});
		request2
				.done(function(response, textStatus, jqXHR) {

					var spaceName = response.name;
					footerHtml = footerTemplate({
						'space_name' : spaceName,
						'space_url' : jsRoutes.controllers.Spaces
								.getSpace(space_id).url,
						'uri' : field_id
					});
					// Call after new html has been added to DOM tree
					setupField(id, elem, md_id, footerHtml, templateRoute);
				});
		request2.fail(function(jqXHR, textStatus, errorThrown) {
			setupField(id, elem, md_id, footerHtml, templateRoute);
		});
	} else {
		setupField(id, elem, md_id, footerHtml, templateRoute);
	}
}

function setupField(id, elem, md_id, footerHtml, templateRoute) {

	// create html form
	var field_label = $(elem).text();
	var field_id = $(elem).val();
	var field_description = $(elem).data("description");
	var field_type = $(elem).data("type");
	// Optional terms for some types
	var field_service = $(elem).data("service");
	var field_param = $(elem).data("param");

	var space_id = $(elem).attr("space_id");
	var field_value = null;
	// Default for string fields
	if (md_id != null) {
		field_value = $("#" + md_id + ">span").text();
	}

	// Create the template and HTML content.
	var template_map = {
		"string" : "add_metadata_string",
		"list" : "add_metadata_list",
		"listjquery" : "add_metadata_listjquery",
		"listgeocode" : "add_metadata_string",
		"scientific_variable" : "add_metadata_scientific_variable",
		"datetime" : "add_metadata_datetime",
		"wkt" : "add_metadata_wktlocation",
		"person" : "add_metadata_list"
	};
	var modalTemplate = Handlebars.getTemplate(templateRoute
			+ template_map[field_type]);
	var html = modalTemplate({
		'field_label' : field_label,
		'field_description' : field_description,
		'field_id' : field_id,
		'field_type' : field_type,
		'entry_id' : md_id,
		'field_value' : field_value
	});
	$('#' + id + ' .selected_field').html(html + footerHtml);

	if (field_type === "list") {
		// find field with the specific uri
		var field = $.grep(fields, function(e) {
			return e.json.uri == field_id;
		});

		// make call to external service
		var request = jsRoutes.api.Metadata.getDefinition(field[0].id).ajax({
			type : 'GET',
			contentType : "application/json"
		});

		request.done(function(response, textStatus, jqXHR) {
			var vocabulary = JSON.parse(response);
			// modalTemplate was assigned above using the map.
			var html = modalTemplate({
				'field_label' : field_label,
				'field_description' : field_description,
				'field_id' : field_id,
				'field_type' : field_type,
				'options' : vocabulary,
				'entry_id' : md_id,
				'field_value' : field_value
			});
			$("#" + id + " .selected_field").html(html + footerHtml);

			configureField(id, md_id, "field-value", field_type, field_value,
					field_service, field_param);

			// register submit listener
			if (md_id != null) {
				$("#" + id + " #add-metadata-button").click(
						window["update_" + id]);
			} else {
				$("#" + id + " #add-metadata-button").click(
						window["submit_" + id]);
			}
		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			console.error("The following error occured: " + textStatus,
					errorThrown);
			notify("Could not retrieve external vocabulary: " + errorThrown,
					"error");
		});

	} else {
		// register submit listener
		configureField(id, md_id, "field-value", field_type, field_value,
				field_service, field_param);

		if (md_id != null) {
			$("#" + id + " #add-metadata-button").click(window["update_" + id]);
		} else {
			$("#" + id + " #add-metadata-button").click(window["submit_" + id]);
		}
	}
}

function configureField(id, md_id, input_id, field_type, field_value,
		field_service, field_param) {

	// Do type-specific setup:
	if (field_type === "scientific_variable") {
		if (md_id != null) {
			var index = field_value.indexOf('(');
			var unit = null;
			if (index > 0) { // assume at least one char for var name
				unit = field_value.substring(index + 1, field_value.length - 1);
				field_value = field_value.substring(0, index - 1);
			}
			$("#" + id + " #" + input_id).val(field_value);
			$("#" + id + " #unit-value").val(unit);
		}
		// Find the field with the specific uri.
		$("#" + id + " #" + input_id).catcomplete(
				{
					minLength : 3,
					source : function(request, response) {

						var useSyn = $("#" + id + "  #useSynonyms").prop(
								"checked");

						var url = encodeURIComponent(field_service + "?"
								+ field_param + "=" + request.term
								+ "&useSynonyms=" + useSyn);
						$.ajax({
							url : jsRoutes.api.Metadata.getUrl(url).url,
							// dataType: "jsonp",
							dataType : "json",
							// data: { term: request.term,
							// useSynonyms:
							// useSyn },
							success : function(data) {
								// The vars list is in
								// data.vars_data, and
								// the categories in
								// data.cat_data. Assuming
								// that "listjquery" will use a
								// URL that
								// returns filtered data, we
								// don't filter
								// again. Returns cat_data with
								// the vars
								// listif present, otherwise
								// returns the
								// original data.
								if ('cat_data' in data) {
									var res = data.cat_data
											.concat(data.vars_data);
									response(res);
								} else {
									response(data);
								}
							}
						});
					}
				});

		$("#" + id + " #unit-value")
				.autocomplete(
						{
							minLength : 1,
							source : function(request, response) {
								var url = encodeURIComponent(field_service);
								$
										.ajax({
											url : jsRoutes.api.Metadata
													.getUrl(url).url,
											dataType : "json",
											success : function(data) {
												if (!('unit_data' in data)) {
													response(Array("Error: no unit_data field in the returned result."));
												} else {
													var searchspace = data.unit_data;
													var searchwords = request.term
															.split(" ");
													$
															.each(
																	searchwords,
																	function() {
																		searchspace = $.ui.autocomplete
																				.filter(
																						searchspace,
																						this);
																	});
													response(searchspace);
												}
											}
										});
							}
						});
	} else if (field_type === "datetime") {
		// This widget uses the ISO 8601 format, such as
		// 2016-01-01T10:00:00-06:00 or 2016-01-01T10:00:00Z.
		// This uses Trent Richardson's jQuery UI Timepicker add-on.
		// jQuery UI Datepicker options:
		// http://api.jqueryui.com/datepicker/
		// jQuery UI Timepicker addon options:
		// http://trentrichardson.com/examples/timepicker/
		$("#" + input_id).datetimepicker({
			controlType : 'select',
			// Uses "select" instead of the default slider.
			// If we put "T" in the "separator" option instead
			// of "timeFormat", the widget changes 2-digit year
			// values xx we put directly into the field to 20xx;
			// if we put "T" in timeFormat, then the year values
			// are kept. So we used the latter.
			dateFormat : $.datepicker.ISO_8601,
			timeFormat : "'T'HH:mm:ssZ",
			separator : '',
			// Allows direct input.
			timeInput : true
		});
	} else if (field_type === "listjquery") {
		// Find the field with the specific uri.
		$("#" + id + " #" + input_id).autocomplete(
				{
					minLength : 3,
					source : function(request, response) {
						// Get the query parameter from the saved json,
						// not
						// hardcoded.

						var url = encodeURIComponent(field_service + "?"
								+ field_param + "=" + request.term);
						alert(url);
						$.ajax({
							url : jsRoutes.api.Metadata.getUrl(url).url,
							dataType : "json",
							success : function(data) {
								var searchspace = data;
								var searchwords = request.term.split(" ");
								$.each(searchwords, function() {
									searchspace = $.ui.autocomplete.filter(
											searchspace, this);
								});
								response(searchspace);
							}
						});
					}
				});
	} else if (field_type === "listgeocode") {

		$("#" + id + " #" + input_id).autocomplete(
				{
					minLength : 3,
					source : function(request, response) {
						// Sets a variable query parameter in
						// $.ajax.data
						// below.
						var url = encodeURIComponent(field_service + "?"
								+ field_param + "=" + request.term);
						$.ajax({
							url : jsRoutes.api.Metadata.getUrl(url).url,
							// dataType: "jsonp",
							dataType : "json",
							// data: query_data,
							success : function(data) {
								// Assuming that the remote
								// service returns
								// filtered data, no need to
								// filter again.
								response(data);
							}
						});
					}
				});
		if (md_id != null) {
			var curVal = ($("#" + md_id + " span .placename")).text();
			curVal = curVal.substring(0, curVal.length - 2);
			$("#" + id + " #" + input_id).attr("value",
					curVal.replace(/\s+/g, ''));
			$("#" + id + " #" + input_id).autocomplete("search");
		}
	} else if (field_type === "list") {
		// chosen pulldown configuration
		$("#" + id + " #" + input_id).chosen({
			no_results_text : "Not found. Press enter to add ",
			add_search_option : true,
			search_contains : true,
			width : "100%",
			placeholder_text_single : "Select field"
		});

		// register submit listener
		if (md_id != null) {
			$("#" + id + " #" + input_id).val(field_value).trigger(
					'chosen:updated');
		}

	} else if (field_type === "person") {
		var curVal = null;
		if (md_id != null) {
			curVal = "Currently: " + ($("#" + md_id + " span")).text();
			if ($("#" + md_id + " span").children().length > 0)
				curVal = curVal + " ("
						+ $("#" + md_id + " span a").attr('href') + ")";
		}
		startPersonSelect("#" + input_id, curVal);
	}
}

function getContextAndContent(id, input_id) {

	var field_label = $("#" + id + " #" + input_id).data("label");
	var field_id = $("#" + id + " #" + input_id).data("id");

	var field_type = $(
			"#" + id + " #add-metadata-select-" + id + " option[value='"
					+ field_id + "']").data("type");

	var field_value = "";
	var field_types_with_simple_values = {
		"string" : 1,
		"listjquery" : 1,
		"listgeocode" : 1,
		"scientific_variable" : 1,
		"datetime" : 1,
		"wkt" : 1,
		"person" : 1
	};
	if (field_type in field_types_with_simple_values) {
		field_value = $("#" + id + " #" + input_id).val();
	} else if (field_type === "list") {
		field_value = $("#" + id + "  #" + input_id + " option:selected").val();
	} else {
		console.log("Wrong field type: " + field_type);
	}

	var error = false;
	var contexts = [];
	var content = {};
	if (field_value != "") {
		// define contexts
		contexts
				.push("https://clowder.ncsa.illinois.edu/contexts/metadata.jsonld");
		var context = {};
		context[field_label] = field_id;
		context["content_ld"] = "https://clowder.ncsa.illinois.edu/metadata#content_ld"
		contexts.push(context);

		if (field_type === "listgeocode") {
			try {
				// geocode example: "Champaign, IL, USA: 40.12, -88.24"
				var parts = field_value.split(":");
				var geoval = {};
				geoval["Label"] = parts[0].trim();
				var lat_lng = parts[1].trim().split(",");
				geoval["Latitude"] = lat_lng[0].trim();
				geoval["Longitude"] = lat_lng[1].trim();
				content[field_label] = geoval;
				context["Latitude"] = "http://www.w3.org/2003/01/geo/wgs84_pos#lat";
				context["Longitude"] = "http://www.w3.org/2003/01/geo/wgs84_pos#lon";
				context["Label"] = "http://www.w3.org/2000/01/rdf-schema#label";
			} catch (err) {
				notify("Entry must be of the form name: latitude,longitude ",
						"error", false, 2000);
				error = true;
			}
		} else if (field_type === "scientific_variable") {
			var scival = {};
			scival["Label"] = field_value;
			scival["Unit"] = $("#" + id + " #unit-value").val();
			content[field_label] = scival;
			context["Unit"] = "http://ecgs.ncsa.illinois.edu/gsis/sas/unit/udunits2";
			context["Label"] = "http://www.w3.org/2000/01/rdf-schema#label";
		} else if (field_type === "wkt") {
			try {
				var primitive = Terraformer.WKT.parse(field_value);
				content[field_label] = field_value;
			} catch (err) {
				notify("There is an error in your WKT. " + err, "error", false,
						2000);
				error = true;
			}
		} else {
			content[field_label] = field_value;
		}
	} else {
		error = true;
	}
	return {
		contexts : contexts,
		content : content,
		error : error
	}
}

function addValueFieldForType(parent, id, field_type, field_service,
		field_param) {
	if (field_type === 'person') {
		parent.append($("<select/>").addClass('form-control').attr('id', id));
		startPersonSelect("#" + id, "");
	} else if (field_type === 'scientific_variable') {
		parent.append($("<input/>").addClass('form-control').attr('id', id)
				.attr('type', 'text').attr('placeholder', 'Type value here'));
		parent.append($('<div/>').addClass('col-sm-12').append(
				$("<input/>").attr('type', 'checkbox')
						.attr('id', 'useSynonyms')).append(
				$('<label/>').attr('for', id).addClass('control-label').text(
						'Use synonyms')));
//		parent.append($('<div/>').addClass('form-group').append(
//				$('<label/>').attr('for', id)
//						.addClass('col-sm-2 control-label').append(
//								$('<strong/>').text('Unit'))).append(
//				$('<div/>').addClass('col-sm-10').append(
//						$('<input/>').attr('type', 'text').addClass(
//								'form-control').attr('id', 'unit-value').attr(
//								'placeholder', 'Enter the unit here'))));
	} else { // string, listquery, listgeocode
		parent.append($("<input/>").addClass('form-control').attr('id', id)
				.attr('type', 'text').attr('placeholder', 'Type value here'));
	}
}

function getTypeSpecificLabel(field_type, rowStr) {
	var label = "Label (" + field_type + ") ";
	if(field_type === "") {
		label = "Label ";
	} else if (field_type === "listjquery" || field_type === "listgeocode") {
		label = "Label (dynamic list) ";
	} else if (field_type === "scientific_variable") {
		label = "Label (var/unit from dynamic lists) ";
	} else if (field_type === "wkt") {
		label = "Label (<a href='https://en.wikipedia.org/wiki/Well-known_text' target='_blank'>Well Known Text</a>) ";
	}
	
	
	return $("<div>")
			.addClass('col-lg-3 col-md-3')
			.attr('id', 'label-' + rowStr)
			.html(label)
			.append(
					$("<a/>")
							.attr("tabindex", "0")
							.attr("role", "button")
							.attr("aria-hidden", "true")
							.attr("title", "Value")
							.attr("data-content",
									"The value used in comparing datasets and with your query conditions.")
							.attr("data-toggle", "popover").append(
									$("<span/>").addClass(
											"glyphicon glyphicon-info-sign")));
}
