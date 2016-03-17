package models

/**
  * A user can have many API keys. Keys are used to access API endpoints.
  */
case class APIKey (
  id: UUID = UUID.generate(),
  userId: UUID,
  name: String,
  key: String
)
