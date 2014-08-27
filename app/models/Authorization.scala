package models

import java.util.Date

import securesocial.core.IdentityId

/**
 * User authorization. Tracks roles and account status.
 *
 * Created by Luigi Marini on 8/26/14.
 */
case class Authorization (
  id: UUID = UUID.generate,
  identityId: IdentityId,
  status: String = "disabled",
  roles: Map[UUID, List[UUID]] = Map.empty[UUID, List[UUID]], // key is space id, maps to a list of role ids
  created: Date = new Date())

