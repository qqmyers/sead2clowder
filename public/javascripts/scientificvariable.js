function expandSciVariables() {
	$('.scientific_variable')
			.each(
					function() {
						var sciElement = this;
						if (!$(sciElement).hasClass('expanded')) {
							$(sciElement).addClass('expanded');
							var contents = $(sciElement).find('span');
							try {
								var json = JSON.parse($(contents).text());
								var newElem = $("<span/>").prependTo(
										$(sciElement));
								$(newElem)
										.append(
												$("<span/>")
														.attr("title", "Name")
														.addClass("scivarname")
														.text(
																json["http://www.w3.org/2000/01/rdf-schema#label"]));
								$(newElem)
										.append(
												$("<span/>")
														.attr("title", "Unit")
														.text(
																" ("
																		+ json["http://ecgs.ncsa.illinois.edu/gsis/sas/unit/udunits2"]
																		+ ")"));
								$(contents).remove();
							} catch (err) {
								console
										.log("Couldn't parse scientific variable entry as json: "
												+ $(contents).text());
							}
						}

					});

}

$(function() {
	expandSciVariables();
})