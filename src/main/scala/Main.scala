package ztp

import zoey._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Main {
  def main(args: Array[String]) {
    val client = ZkClient()
    Await.ready(client().map(_ => Server(client).run()), Duration.Inf)
  }
}
