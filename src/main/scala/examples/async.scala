package examples

import effekt._
import concurrent.Await.result
import concurrent.Future
import concurrent.duration.Duration
import concurrent.ExecutionContext.Implicits.global

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

    def async[R, E](prog: (async: Async_) ⇒ R / (async.effect & E)): R / E = handle { scope =>
        val async = new Async_ {
            type effect = scope.effect
            def apply[T](t: =>T) = scope.switch { resume =>
                resume(Future(t)) }
        }
        prog(async)
    }

    def await[R, E](prog: (await: Await_) ⇒ R / (await.effect & E)): R / E = handle { scope =>
        val await = new Await_ {
            type effect = scope.effect
            def apply[T](fut: Future[T]) = scope.switch { resume =>
                resume(result(fut, Duration.Inf)) }
        }
        prog(await)
    }

    def asyncMap[R, E, T](f: T => T, prog: (async: Async[T]) ⇒ R / (async.effect & E)): R / E = handle { scope =>
        val async = new Async[T] {
            type effect = scope.effect
            def apply(t: =>T) = scope.switch { resume =>
                resume(Future(t) map f) }
        }
        prog(async)
    }

    def awaitMap[R, E, T](f: T => T, prog: (await: Await[T]) ⇒ R / (await.effect & E)): R / E = handle { scope =>
        val await = new Await[T] {
            type effect = scope.effect
            def apply(fut: Future[T]) = scope.switch { resume =>
                resume(result(fut map f, Duration.Inf)) }
        }
        prog(await)
    }
}