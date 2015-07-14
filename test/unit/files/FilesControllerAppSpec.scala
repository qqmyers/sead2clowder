package unit




import services.DatasetService
import services.CollectionService
import services.MultimediaQueryService
import services.TagService
import services.CommentService
import services.ThreeDService
import services.ExtractionService
import services.ExtractionRequestsService
import services.PreviewService
import services.RdfSPARQLService
import services.ThumbnailService
import services.FileService

import play.api.GlobalSettings
import org.scalatestplus.play.OneAppPerSuite
import org.scalatestplus.play.PlaySpec
import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.test.Helpers._
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar._
import play.api.GlobalSettings




/*
 * Integration/Unit Test for Files Controller 
 * @author Eugene Roeder
 * 
 */

class FilesControllerAppSpec extends PlaySpec with OneAppPerSuite with FileTestData {
  
  val excludedPlugins = List(
    "services.mongodb.MongoSalatPlugin",
    "com.typesafe.plugin.CommonsMailerPlugin",
    "services.mongodb.MongoDBAuthenticatorStore",
    "securesocial.core.DefaultIdGenerator",
    "securesocial.core.providers.utils.DefaultPasswordValidator",
    "services.SecureSocialTemplatesPlugin",
    "services.mongodb.MongoUserService",
    "securesocial.core.providers.utils.BCryptPasswordHasher",
    "securesocial.core.providers.UsernamePasswordProvider",
    "services.RabbitmqPlugin",
    "services.VersusPlugin")

  implicit override lazy val app: FakeApplication = FakeApplication(withoutPlugins = excludedPlugins, withGlobal = Some(new GlobalSettings() {
		def onStart(app: App) { println("Fake Application Started") }
	}))


  val mockFiles = mock[FileService]
  val mockDatasets = mock[DatasetService]
  val mockCollections = mock[CollectionService]
  val mockQueries = mock[MultimediaQueryService]
  val mockTags = mock[TagService]
  val mockComments = mock[CommentService]
  val mockExtractions = mock[ExtractionService]
  val mockDTSRequests = mock[ExtractionRequestsService]
  val mockPreviews = mock[PreviewService]
  val mockThreeD = mock[ThreeDService]
  val mockSqarql = mock[RdfSPARQLService]
  val mockThumbnails = mock[ThumbnailService]




  when(mockFiles.listFiles).thenReturn(List(testFile1,testFile2))
  when(mockFiles.count).thenReturn(4)
 // when(mockExtractors.getExtractorInputTypes).thenReturn(List("image", "text"))
 // doNothing().when(mockFiles).File(List("ncsa.cv.face", "ncsa.ocr"))
//  doNothing().when(mockExtractors).insertServerIPs(List("dts1.ncsa.illinois.edu", "141.142.220.244"))
//  doNothing().when(mockExtractors).insertInputTypes(List("image", "text"))
  

  "The Files HTML Controller Test Suite" must {
     "return number of files" in {
      val files_controller = new api.Files(mockFiles, mockDatasets, mockCollections, mockQueries, mockTags, mockComments, mockExtractions, mockDTSRequests, mockPreviews, mockThreeD, mockSqarql, mockThumbnails)
      //val resultFileNames = files_controller.count.apply(FakeRequest())
      //contentType(resultFileNames) mustEqual Some("application/json")
      //contentAsString(resultFileNames) must include ("filename")
      //info("File names "+contentAsString(resultFileNames))
     }

    "return List of File Names" in {
      val files_controller = new api.Files(mockFiles, mockDatasets, mockCollections, mockQueries, mockTags, mockComments, mockExtractions, mockDTSRequests, mockPreviews, mockThreeD, mockSqarql, mockThumbnails)
      val resultFileNames = files_controller.list.apply(FakeRequest())
      assert(testFile1.contentType != "")
      assert(testFile2.filename == "morrowplots.jpeg")
      info("Eugene New Test")

    }
  }

}
