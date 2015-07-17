package unit


import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import play.api.http.Status._
import play.api.libs.json.Json._
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
import services.EventService
import services.UserService

import models.{UUID, File}

import play.api.{Logger, GlobalSettings}
import org.scalatestplus.play.OneAppPerSuite
import org.scalatestplus.play.PlaySpec
import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.test.Helpers._
import org.mockito.Mockito.when
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doThrow
import org.scalatest.mock.MockitoSugar._
import play.api.libs.json.Json




/*
 * Unit Test for Files API/HTML Controller
 * @author Eugene Roeder
 * 
 */

class FilesHTMLControllerAppSpec extends PlaySpec with OneAppPerSuite with FileTestData with FakeMultipartUpload {
  
  val excludedPlugins = List(
   // "services.mongodb.MongoSalatPlugin",
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
  val mockEvents = mock[EventService]
  val mockUser = mock[UserService]


  when(mockFiles.listFilesNotIntermediate()).thenReturn(List(testFile1,testFile2))
  when(mockFiles.listFilesAfter("12-01-2001",5)).thenReturn(List(testFile1,testFile2))
  when(mockFiles.listFilesBefore("12-01-2020", 5)).thenReturn(List(testFile1,testFile2))
  when(mockFiles.get(UUID("55a845799908d07af78cb602"))).thenReturn(testOptionFile1)
  when(mockFiles.getBytes(UUID("55a845799908d07af78cb602"))).thenReturn(Option(file1InputStream,"morrowplots.jpeg","image/jpg",40000.toLong))
  //doThrow(new Exception()).when(mockFiles).addMetadata(UUID("55a845799908d07af78cb602"),Json.parse(testJson))
  when(mockFiles.addMetadata(UUID("55a845799908d07af78cb602"),Json.parse(testJson))).thenAnswer(new Answer[Void](){
    override def answer(invocationOnMock: InvocationOnMock): Void = {
      startLF.set()
      //Void.TYPE.newInstance()
      null: Void
    }
  })


  "The Files HTML Controller Test Suite" must {
     "return list of files json calling list() in api which calls listFilesNotIntermediate in file service" in {
      val files_controller = new api.Files(mockFiles, mockDatasets, mockCollections, mockQueries, mockTags, mockComments, mockExtractions, mockDTSRequests, mockPreviews, mockThreeD, mockSqarql, mockThumbnails,mockEvents,mockUser)
      val resultFileNames = files_controller.list.apply(FakeRequest())
      contentType(resultFileNames) mustEqual Some("application/json")
      contentAsString(resultFileNames) must include ("filename")
      info("File names "+contentAsString(resultFileNames))
     }

    "return file json calling get() in api which calls listFilesNotIntermediate in file service" in {
      val files_controller = new api.Files(mockFiles, mockDatasets, mockCollections, mockQueries, mockTags, mockComments, mockExtractions, mockDTSRequests, mockPreviews, mockThreeD, mockSqarql, mockThumbnails,mockEvents,mockUser)
      val resultFileNames = files_controller.get(UUID("55a845799908d07af78cb602")).apply(FakeRequest())
      contentType(resultFileNames) mustEqual Some("application/json")
      contentAsString(resultFileNames) must include ("filename")
      info("File names "+contentAsString(resultFileNames))
    }

    "return files calling download() in api which calls get() and getBytes() in file service" in {
      val files_controller = new api.Files(mockFiles, mockDatasets, mockCollections, mockQueries, mockTags, mockComments, mockExtractions, mockDTSRequests, mockPreviews, mockThreeD, mockSqarql, mockThumbnails,mockEvents,mockUser)
      val resultFileNames = files_controller.download(UUID("55a845799908d07af78cb602")).apply(FakeRequest())
      //assert(testFile1.contentType != "")
      //assert(testFile2.filename == "morrowplots.jpeg")
      info("File names "+contentAsString(resultFileNames))
    }

    "return success calling addMetadata() in api which calls get() and addMetadata() in file service" in {
      val files_controller = new api.Files(mockFiles, mockDatasets, mockCollections, mockQueries, mockTags, mockComments, mockExtractions, mockDTSRequests, mockPreviews, mockThreeD, mockSqarql, mockThumbnails,mockEvents,mockUser)
      val resultFileNames = files_controller.addMetadata(UUID("55a845799908d07af78cb602")).apply(FakeRequest())
      info("Test completed - Return Null")
    }

  }

}
