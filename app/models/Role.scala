package models

import java.util.Date

import securesocial.core.IdentityId

/**
 * A role is a collection of permissions.
 *
 * Created by Luigi Marini on 8/26/14.
 */
case class Role (
  id: UUID = UUID.generate,
  name: String,
  description: String,
  permissions: List[UUID],
  created: Date)
