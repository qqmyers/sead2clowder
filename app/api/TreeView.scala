package api

import api.Permission.Permission
import play.api.libs.json.Json.toJson

class TreeView extends ApiController {

  def getLevelOfTree(currentId : Option[String], currentType : String) = PermissionAction(Permission.ViewCollection) { implicit request =>
    request.user match {
      case Some(usr) => {
        //val result = getChildrenOfNode(currentId, currentType, request.user)
        Ok(toJson("Not implemented"))

      }
      case None => BadRequest("No user supplied")
    }
  }
}
