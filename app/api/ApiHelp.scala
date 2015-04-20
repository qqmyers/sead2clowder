package api

import play.api.mvc.Controller
import play.api.libs.json.Json._
import play.api.mvc.Action
import play.api.Play.current

/**
 * Documentation about API using swagger.
 * 
 * @author Luigi Marini
 * @author Constantinos Sophocleous
 */
object ApiHelp extends Controller {
  
  var appPort = play.api.Play.configuration.getString("https.port").getOrElse("")
  val httpProtocol = {
					if(!appPort.equals("")){
						"https://"
					}
					else{
						appPort = play.api.Play.configuration.getString("http.port").getOrElse("")
						"http://"
					}
		}

  /**
   * Used as entry point by swagger.
   */
  def getResources() = Action {
    Ok(toJson("""
		    {
			  apiVersion: "0.1",
			  swaggerVersion: "1.1",
			  basePath: """ + httpProtocol + play.Play.application().configuration().getString("hostIp") + ":" + appPort + """/api",
			  apis: [
			    {
			      path: "/datasets.json",
			      description: "Datasets are basic containers of data"
			    },
          {
            path: "/files.json",
            description: "Files include raw bytes and metadata"
          },
          {
            path: "/collections.json",
            description: "Collections are groupings of datasets"
          }
			  ]
			}
    """))
  }
  
}
