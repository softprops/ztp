package ztp

import zoey._
import unfiltered.Async.Intent
import unfiltered.response.Ok

case class Endpoints(zk: ZkClient) {
  def intent: Intent[Any, Any]= {
    case r => r.respond(Ok)
  }
}

