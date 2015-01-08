package ztp

import zoey._
import unfiltered.Async.Intent
import unfiltered.response.{ BadRequest, Created, Ok, NotFound, ResponseString }
import unfiltered.request.{ Body, Path, GET, HEAD, PUT }
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

case class ZNodes(zk: ZkClient) {
  def intent: Intent[Any, Any]= {
    case r @ Path(p) =>
      val znode = zk.aclOpenUnsafe(p match {
        case "/" => p
        case _ => p.stripSuffix("/")
      })
      val response = r match {
        case DELETE(_) =>
          (for {
            _ <- znode.delete()
          } yield Ok)
          .recover {
            case NonFatal(_) =>
              NotFound
          }
        case GET(_) =>
          (for {
            data <- znode.data()
          } yield ResponseString(new String(data.bytes)))
          .recover {
            case NonFatal(_) =>
              NotFound
          }
        case HEAD(_) =>
          (for {
            _ <- znode.exists()
          } yield Ok)
          .recover {
            case NonFatal(_) =>
              NotFound
          }
        case PUT(_) =>
          ZNode.mkdirp(
            znode.keeper, znode.keeper.acl, znode.path)
            .flatMap {
              case exists: ZNode.Exists =>
                for {
                  _ <- exists.set(Body.bytes(r), exists.stat.getVersion)
                } yield Ok
              case fresh =>
                for {
                  _ <- fresh.set(Body.bytes(r), 0)
                } yield Created
            }.recover {
              case NonFatal(_) =>
                BadRequest
            }
        case _ =>
          Future.successful(NotFound)
      }
      response.onSuccess {
        case s => r.respond(s)
      }
  }
}
