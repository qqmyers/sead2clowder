package models

import java.util.Date

case class Subscriber (
  id: UUID = UUID.generate,
  name: String,
  surname: String,
  email: Option[String] = None,
  FBIdentifier: Option[String] = None,
  hashedPassword: String,
  fbAuthToken: Option[String] = None,
  expirationTime: Option[Date] = None
)

