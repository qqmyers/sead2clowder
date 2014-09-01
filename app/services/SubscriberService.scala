package services

import models.Subscriber
import models.UUID

case class FBNotFoundException(msg:String) extends Exception(msg)

trait SubscriberService {
  
  def findOneByEmail(email: String): Option[Subscriber]
  
  def findAllHavingEmail(): List[Subscriber]
  
  def findAllHavingFB(): List[Subscriber]
  
   def findAll(): List[Subscriber]
  
  def findOneByIdentifier(identifier: String, findIfExistsInGraph: Boolean = true): Option[Subscriber]
  
  def setAuthToken(id: String, token: String, expirationOffset: Int)
  
  def getAuthToken(identifier: String): Option[String]
  
  def get(id: String): Option[Subscriber]
  
  def getAllExpiring(): List[Subscriber]
  
  def insert(subscriber: Subscriber)
  
  def remove(subscriberId: UUID)

}