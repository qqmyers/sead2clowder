package services

import play.api.{ Plugin, Logger, Application }
/**
 * Staging Area Plugin.
 */
class StagingAreaPlugin(application: Application) extends Plugin {

  override def onStart() {
    Logger.info("Staging Area Plugin started")
  }

  override def onStop() {
    Logger.info("Staging Area Plugin has stopped")
  }

  /* These are labels and term URIs that are used in publishing and are populated by 'non-metadata' fields in Clowder, 
   * e.g. they are parts of the class models, etc. When using the plugin, it should not be possible to add new 
   * MetadataDefinitions that use these labels or uris. 
  */
  def isRestrictedLabel(label: String): Boolean = {
    val labels = Set(
      "Identifier",
      "@id",
      "Creation Date",
      "Label",
      "Title",
      "Uploaded By",
      "Size",
      "Mimetype",
      "Publication Date",
      "External Identifier",
      "SHA512 Hash",
      "@type",
      "Is Version Of",
      "similarTo",
      "Keyword",
      "Has Part",
      "Creator",
      "Abstract",
      "Comment",
      "Identifier",
      "Rights",
      "Date",
      "aggregates",
      "describes")

    labels(label)
  }

  def isRestrictedURI(uri: String): Boolean = {

    val uris = Set(
      "http://purl.org/dc/elements/1.1/identifier",
      "http://purl.org/dc/terms/created",
      "http://www.w3.org/2000/01/rdf-schema#label",
      "http://purl.org/dc/elements/1.1/title",
      "http://purl.org/dc/elements/1.1/creator",
      "tag:tupeloproject.org,2006:/2.0/files/length",
      "http://purl.org/dc/elements/1.1/format",
      "http://purl.org/dc/terms/issued",
      "http://purl.org/dc/terms/identifier",
      "http://sead-data.net/terms/hasSHA512Digest",
      "http://purl.org/dc/terms/isVersionOf",
      "http://www.openarchives.org/ore/terms/similarTo",
      "http://www.holygoat.co.uk/owl/redwood/0.1/tags/taggedWithTag",
      "http://purl.org/dc/terms/hasPart",
      "http://purl.org/dc/terms/creator",
      "http://purl.org/dc/terms/abstract",
      "http://cet.ncsa.uiuc.edu/2007/annotation/hasAnnotation",
      "http://purl.org/dc/terms/rights",
      "http://purl.org/dc/elements/1.1/date",
      "http://www.openarchives.org/ore/terms/aggregates",
      "http://www.openarchives.org/ore/terms/describes")

    uris(uri)
  }
}
