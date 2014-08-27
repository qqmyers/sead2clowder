package models

/**
 * A permission maps to a specific feature of the application.
 *
 * Created by Luigi Marini on 8/26/14.
 */
case class Permission (
  id: UUID = UUID.generate,
  name: String,
  description: String)
