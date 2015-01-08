package ztp

import unfiltered.netty
import unfiltered.netty.async.Planify
import unfiltered.response.Ok
import scala.concurrent.Await
import scala.concurrent.duration._
import zoey._

case class Server
 (zk: ZkClient, port: Int = 8080) {
  def run() {
    netty.Server.http(port)
     .plan(Planify(ZNodes(zk).intent))
     .beforeStop {
       Await.ready(zk.close(), 4.seconds)
     }.run()
  }
}
