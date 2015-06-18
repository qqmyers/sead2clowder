package models

case class GateOneUserOnMachine(
  id: UUID = UUID.generate,
  userEmail: String,
  apiKey: String,
  accessUsername: String  
)