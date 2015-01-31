package ztp

import base64.Encode
import org.apache.zookeeper.data.Stat
import unfiltered.Async.Intent
import unfiltered.response.{ BadRequest, Created, Gone, HeaderName, NotAcceptable, Ok, NotFound, ResponseString }
import unfiltered.request.{ Accept, Accepts, Body, DELETE, Params, Path, GET, HEAD, HttpRequest, PUT, & }
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Failure, Success }
import scala.util.control.NonFatal
import zoey.{ NodeEvent, ZNode, ZkClient }
import org.json4s.native.JsonMethods.{ compact, parseOpt, render }

object XZNode {
  object Version extends HeaderName("X-ZNode-Version")
  object Mtime extends HeaderName("X-ZNode-Mtime")
  object Ctime extends HeaderName("X-ZNode-Ctime")
  object ChildCount extends HeaderName("X-ZNode-Child-Count")
}

object Watch extends Params.Extract("watch", Params.first)

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
      r match {
        case DELETE(_) =>
          (for {
            _ <- znode.delete()
          } yield Ok)
          .recover {
            case NonFatal(_) =>
              NotFound
          }
          .onSuccess {
            case f => r.respond(f)
          }
        case req @ GET(_) & Params(params) =>
          (for {
            dnode <- params match {
              case Watch(_) => znode.data.watch()
              case _        => znode.data()
            }
          } yield dnode match {
            case dat: ZNode.Data =>
              r.respond(stat(dat.stat) andThen data(req)(dat.bytes))
            case ZNode.Watch(trydat, update) =>
              update.onComplete {
                case Success(ev) =>
                  ev match {
                    case NodeEvent.Created(_)         =>
                      r.respond(ResponseString(ev.toString))
                    case NodeEvent.ChildrenChanged(_) =>
                      r.respond(ResponseString(ev.toString))
                    case NodeEvent.DataChanged(_)     =>
                      (for {
                        updated <- znode.data()
                      } yield stat(updated.stat) andThen data(req)(updated.bytes))
                       .recover {
                         case _ => BadRequest
                       }
                       .onSuccess {
                         case f => r.respond(f)
                       }
                    case NodeEvent.Deleted(_)         =>
                      r.respond(Gone)
                  }
                case Failure(fail) =>
                  r.respond(BadRequest)
              }
          })
          .recover {
            case NonFatal(_) =>
              r.respond(NotFound)
          }
        case HEAD(_) =>
          (for {
            exists <- znode.exists()
          } yield stat(exists.stat) andThen Ok)
          .recover {
            case NonFatal(_) =>
              NotFound
          }
          .onSuccess {
            case f => r.respond(f)
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
            .onSuccess {
              case f => r.respond(f)
            }
        case _ =>
          r.respond(NotFound)
      }
  }
}
