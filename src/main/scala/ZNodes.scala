package ztp

import base64.Encode
import org.apache.zookeeper.data.Stat
import unfiltered.Async.Intent
import unfiltered.response.{ BadRequest, Created, HeaderName, NotAcceptable, Ok, NotFound, ResponseString }
import unfiltered.request.{ Accept, Accepts, Body, DELETE, Path, GET, HEAD, HttpRequest, PUT }
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import zoey.{ ZNode, ZkClient }
import org.json4s.native.JsonMethods.{ compact, parseOpt, render }

object XZNode {
  object Version extends HeaderName("X-ZNode-Version")
  object Mtime extends HeaderName("X-ZNode-Mtime")
  object Ctime extends HeaderName("X-ZNode-Ctime")
  object ChildCount extends HeaderName("X-ZNode-Child-Count")
}

case class ZNodes(zk: ZkClient) {
  private def stat(s: Stat) =
    XZNode.Version(s.getVersion.toString) andThen
    XZNode.Mtime(s.getMtime.toString) andThen
    XZNode.Ctime(s.getCtime.toString) andThen
    XZNode.ChildCount(s.getNumChildren.toString)

  private def utf8Str(bytes: Array[Byte]) =
    new String(bytes, "utf8")

  private def data(request: HttpRequest[_])(bytes: Array[Byte]) =
    if (bytes.length == 0) Ok else request match {
      case Accept("text/plain" :: Nil) =>
        ResponseString(utf8Str(bytes))
      case Accepts.Json(_) =>
        val str = utf8Str(bytes)
        parseOpt(str).map(_ => ResponseString(str)).getOrElse(NotAcceptable)
      case _ =>
        ResponseString(utf8Str(Encode.urlSafe(bytes)))
    }

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
        case req @ GET(_) =>
          (for {
            dnode <- znode.data()
          } yield {
            stat(dnode.stat) andThen data(req)(dnode.bytes)
          })
          .recover {
            case NonFatal(_) =>
              NotFound
          }
        case HEAD(_) =>
          (for {
            exists <- znode.exists()
          } yield stat(exists.stat) andThen Ok)
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
                  set <- exists.set(Body.bytes(r), exists.stat.getVersion)
                } yield stat(set.stat) andThen Ok
              case fresh =>
                for {
                  set <- fresh.set(Body.bytes(r), 0)
                } yield stat(set.stat) andThen Created
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
