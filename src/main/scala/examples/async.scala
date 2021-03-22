package examples

import effekt._
import concurrent.Await.result
import concurrent.Future
import concurrent.duration.Duration
import concurrent.ExecutionContext.Implicits.global
import java.net._
import java.net.http._
import java.nio.file._
import scala.jdk.CollectionConverters._

@main def async = {
    trait Async[T] extends Eff {
        def apply(t: =>T): Future[T] / effect
    }

    trait Await[T] extends Eff {
        def apply(fut: Future[T]): T / effect
    }

    trait Async_ extends Eff {
        def apply[T](t: =>T): Future[T] / effect
    }

    trait Await_ extends Eff {
        def apply[T](fut: Future[T]): T / effect
    }

    def async[R, E](prog: (async: Async_) => R / (async.effect & E)): R / E = handle { scope =>
        val async = new Async_ {
            type effect = scope.effect
            def apply[T](t: =>T) = scope.switch { resume =>
                resume(Future(t)) }
        }
        prog(async)
    }

    def await[R, E](prog: (await: Await_) => R / (await.effect & E)): R / E = handle { scope =>
        val await = new Await_ {
            type effect = scope.effect
            def apply[T](fut: Future[T]) = scope.switch { resume =>
                resume(result(fut, Duration.Inf)) }
        }
        prog(await)
    }

    def awaitAtMost[R, E](atMost: Duration) (prog: (await: Await_) => R / (await.effect & E)): R / E = handle { scope =>
        val await = new Await_ {
            type effect = scope.effect
            def apply[T](fut: Future[T]) = scope.switch { resume =>
                resume(result(fut, atMost)) }
        }
        prog(await)
    }

    def asyncMap[R, E, T](f: T => T)(prog: (async: Async[T]) => R / (async.effect & E)): R / E = handle { scope =>
        val async = new Async[T] {
            type effect = scope.effect
            def apply(t: =>T) = scope.switch { resume =>
                resume(Future(t) map f) }
        }
        prog(async)
    }

    def awaitMap[R, E, T](f: T => T)(prog: (await: Await[T]) => R / (await.effect & E)): R / E = handle { scope =>
        val await = new Await[T] {
            type effect = scope.effect
            def apply(fut: Future[T]) = scope.switch { resume =>
                resume(result(fut map f, Duration.Inf)) }
        }
        prog(await)
    }

    def readAll(async: Async[String], await: Await[String],
            paths: List[Path]): String / (async.effect & await.effect) = paths match {
        case head :: tail => for {
            fut <- async { Files readString head }
            tail <- readAll(async, await, tail)
            head <- await(fut)
        } yield head + tail
        case Nil => pure("")
    }

    {
        val paths = Files.walk(Path of "./src").iterator.asScala.toList filter (!Files.isDirectory(_))
        val keyword = "def"
        val res = run {
            asyncMap ((s: String) => (s.lines.iterator.asScala filter (_.contains(keyword))) mkString("\n")) { async =>
                awaitMap (identity[String]) { await =>
                    readAll(async, await, paths)
                }
            }
        };

        println(res);
    }

    def getAndWrite(async: Async_, await: Await_, 
            requests: List[(URI, Path)]): Unit / (async.effect & await.effect) = for {
        fut <- getAndWriteHelper(async, await, requests)
        _ <- await(fut)
    } yield ()

    def getAndWriteHelper(async: Async_, await: Await_, 
            requests: List[(URI, Path)]): Future[Unit] / (async.effect & await.effect) = requests match {
        case (uri, path) :: tail =>
            val request = HttpRequest.newBuilder.uri(uri).build
            val handler =  HttpResponse.BodyHandlers.ofByteArray
            for {
                fut <- async { HttpClient.newHttpClient send(request, handler) }
                tail <- getAndWriteHelper(async, await, tail)
                response <- await(fut)
                fut <- async { Files write(path, response.body) }
            } yield fut flatMap (_ => tail)
        case Nil => pure(Future successful ())
    }

    {
        val uris = List.fill (8) { URI create "https://www.google.com" }
        val paths = List(Path of "./a0", Path of "./a1", Path of "./a2", Path of "./a3",
                        Path of "./a4", Path of "./a5", Path of "./a6", Path of "./a7")
        val requests = uris zip paths 
        val res = run {
            async { async =>
                awaitAtMost (Duration("500ms")) { await =>
                    getAndWrite(async, await, requests)
                }
            }
        };

        println(res);
    }
}