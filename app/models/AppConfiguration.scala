package models

/**
 * Tracks application wide configurations.
 *
 * @author Luigi Marini
 * @author Constantinos Sophocleous
 *
 */
case class AppConfiguration(
  id: UUID = UUID.generate,
  name: String = "default",
  theme: String = "bootstrap/bootstrap.css",
  viewNoLoggedIn: Boolean = false,
  admins: List[String] = List.empty)

