package models

import securesocial.core.Identity

/**
 * @author Constantinos Sophocleous
 */
case class UserPermissions (
	id: UUID = UUID.generate,
	email: String,
	name: String,	
	collectionsViewOnly: List[String] = List.empty,
	collectionsViewModify: List[String] = List.empty,
	collectionsAdministrate: List[String] = List.empty,
	datasetsViewOnly: List[String] = List.empty,
	datasetsViewModify: List[String] = List.empty,
	datasetsAdministrate: List[String] = List.empty,	
	filesViewOnly: List[String] = List.empty,
	filesViewModify: List[String] = List.empty,
	filesAdministrate: List[String] = List.empty
)