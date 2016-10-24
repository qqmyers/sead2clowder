package integration

import org.scalatestplus.play.{PlaySpec, _}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Play}
import play.api.i18n.Messages

/**
 * Functional test. This throws an java.lang.IllegalStateException: cannot enqueue after timer shutdown due to the Akka timer
 * in securesocial.core.UserServicePlugin.onStart(UserService.scala:129).
 *
 * Running a test server for each test avoids this problem. See ApplciationFunctionalTest.
 *
 * @author Luigi Marini
 */
//@DoNotDiscover
class ApplicationSpec extends PlaySpec with ConfiguredApp with FakeMultipartUpload {

  implicit val user: Option[models.User] = None

  "The Application API Spec" must {
    "provide a FakeApplication" in {
      app.configuration.getString("ehcacheplugin") mustBe Some("disabled")
    }
    "make the FakeApplication available implicitly" in {
      def getConfig(key: String)(implicit app: Application) = app.configuration.getString(key)
      getConfig("ehcacheplugin") mustBe Some("disabled")
    }
    "start the FakeApplication" in {
      Play.maybeApplication mustBe Some(app)
    }

    "send 404 on a bad request" in {
      route(FakeRequest(GET, "/wut")) mustEqual (None)
    }

    "render the index page" in {
      val home = route(FakeRequest(GET, "/")).get

      status(home) mustEqual OK
      contentType(home) mustEqual Some("text/html")
      contentAsString(home) must include("Powered by <a href=\"http://clowder.ncsa.illinois.edu\">Clowder</a>")
    }

    "render index template" in {
      val html = views.html.index(1, 2, 3, 4, 5, 6, "1234567890", "ABCDEFGHIJKLMNOPQRSTUVWXYZ")
      /**
        * String name of the Space such as 'Project space' etc., parsed from the config file
        */
      val spaceTitle: String = Messages("spaces.title")

      contentType(html) mustEqual ("text/html")

      contentAsString(html) must include("1234567890")
      contentAsString(html) must include("ABCDEFGHIJKLMNOPQRSTUVWXYZ")

      contentAsString(html) must include("Resources")
      contentAsString(html) must include("5 " + Messages("spaces.title"))
      contentAsString(html) must include("4 " + Messages("collections.title"))
      contentAsString(html) must include("1 " + Messages("datasets.title"))
    }
  }
}
