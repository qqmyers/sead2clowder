package models

case class GateOneMachine(
  id: UUID = UUID.generate,
  apiKey: String,
  secret: String  
)