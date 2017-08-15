package models

import play.api.Logger
import play.api.libs.json._
import services.{MetadataService, DI}

/**
 * Definition of metadata fields to present to the user a list of options.
 * This can be defined local within the local instance or retrieved from a remote server.
 */
case class MetadataDefinition(
  id: UUID = UUID.generate(),
//  remoteURL: Option[URL] = None,
  spaceId: Option[UUID] = None,
  json: JsValue
)

object MetadataDefinition {

  implicit val repositoryFormat = Json.format[MetadataDefinition]

  /** Register default definitions that every instance should have **/
  def registerDefaultDefinitions(): Unit = {
    // add default definition
    Logger.debug("Adding core metadata vocabulary definitions to database")
    val metadataService: MetadataService = DI.injector.getInstance(classOf[MetadataService])
    val default = List(
      Json.parse("""{"label":"Alternative Title",
          "uri":"http://purl.org/dc/terms/alternative",
          "type":"string",
          "description":"Another title for this resource."}"""),
      Json.parse("""{"label":"Audience",
          "uri":"http://purl.org/dc/terms/audience",
          "type":"string",
          "description":"The people/group(s) who would be interested in this resource."}"""),
      Json.parse("""{"label":"References",
          "uri":"http://purl.org/dc/terms/references",
          "type":"string",
          "description":"A related resource that is referenced, cited, or otherwise pointed to by this resource."}"""),
      Json.parse("""{
          "label":"Date and Time",
          "uri":"http://purl.org/dc/terms/date",
          "type":"datetime",
          "description":"A date/time associated with this resource."}"""),
        Json.parse("""{"label":"CSDMS Variable",
          "uri":"http://csdms.colorado.edu/wiki/CSN_Searchable_List",
          "type":"list",
          "definitions_url":"http://ecgs.ncsa.illinois.edu/gsis/CSN",
          "description":"A Community Surface Dynamics Modeling System standard name for an input or output variable associated with this resource."}"""),
        Json.parse("""{
          "label":"ODM2 Variable Name",
          "uri":"http://vocabulary.odm2.org/variablename",
          "type":"list",
          "definitions_url":"http://ecgs.ncsa.illinois.edu/gsis/sas/sn/odm2",
          "description":"An Observational Data Model v. 2 name for an input or output variable associated with this resource."}"""),
        Json.parse("""{
          "label":"SAS Variable Name",
          "uri":"http://ecgs.ncsa.illinois.edu/gsis/sas/vars",
          "type":"scientific_variable",
          "definitions_url":"http://ecgs.ncsa.illinois.edu/gsis/sas/vars/unit/udunits2",
          "query_parameter": "term",
          "description":"A Semantic Annotation Service name/unit combination for an input or output variable associated with this resource."}"""),
        Json.parse("""{
          "label":"SAS Spatial Geocode",
          "uri":"http://ecgs.ncsa.illinois.edu/gsis/sas/geocode",
          "type":"listgeocode",
          "definitions_url":"http://ecgs.ncsa.illinois.edu/gsis/sas/geocode",
          "query_parameter": "loc",
          "description":"A Semantic Annotation Service location (name, latitude, longitude) associated with this resource."}"""),
        Json.parse("""{
          "label":"Unit",
          "uri":"http://ecgs.ncsa.illinois.edu/gsis/sas/unit/udunits2",
          "type":"list",
          "definitions_url":"http://ecgs.ncsa.illinois.edu/gsis/sas/unit/udunits2",
          "description":"A Semantic Annotation Service demonstration of units retrieved from a standard list of unit names." }"""),
        Json.parse("""
          {"label":"Principal Investigator(s)",
            "uri":"http://sead-data.net/terms/PrincipalInvestigator",
            "type":"person",
          "description":"A demonstration showing how groups of users can be entered as metadata. PIs would usually be included in the 'creators' entered on the Dataset page."}"""),
        Json.parse("""
          {"label":"Funding Institution",
            "uri":"http://sead-data.net/terms/FundingInstitution",
            "type":"string",
          "description":"The name of the organization financially suporting the research producing this resource."}"""),
        Json.parse("""
          {"label":"Grant Number",
            "uri":"http://sead-data.net/terms/GrantNumber",
            "type":"string",
          "description":"The identifier assigned by the Funding Agency for the grant supporting the research producing this resource."}"""),
        Json.parse("""
          {"label":"Related Publications",
            "uri":"http://sead-data.net/terms/RelatedPublications",
            "type":"string",
          "description":"Reference to related paper or data publications analogous to references in a paper."}"""),
        Json.parse("""
          {"label":"Time Periods",
            "uri":"http://purl.org/dc/terms/PeriodOfTime",
            "type":"string",
          "description":"A demonstration of using a string field to allow a period of time (a range) to be described." }"""),
        Json.parse("""
          {"label":"Primary/Initial Publication",
            "uri":"http://sead-data.net/terms/PrimaryPublication",
            "type":"string",
          "description":"A way to identify the primary publication for resources that are derived from others."}"""),
        Json.parse("""
          {"label":"Location",
            "uri":"http://geojson.org/geojson-spec.html",
            "type":"wkt",
          "description":"A demonstration of using Well Know Text to describe a location."}""")
      )
    // Add the default definitions, do not update if they already exist.
    if(metadataService.getDefinitions().size == 0) {
      Logger.debug("Add default metadata definition.")
      default.map(d => metadataService.addDefinition(MetadataDefinition(json = d)))
    }
  }
}
