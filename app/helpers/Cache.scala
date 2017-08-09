package helpers

import play.api._
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Cache @Inject() (
  env: Environment,
  conf: Configuration
) {
  def expiringCache[T](expirationInMillis: Long, gen: () => T, genTime: () => Long): () => T = {
    trait CacheEntry

    case object InitCacheEntry extends CacheEntry

    case class ExpiringCacheEntry(
      currentValue: T,
      lastUpdateInMillis: Long
    ) extends CacheEntry

    class CacheEntryHolder {
      private var entry: CacheEntry = InitCacheEntry

      def cacheEntry(e: CacheEntry): Unit = this.synchronized {
        this.entry = e
      }

      def cacheEntry: CacheEntry = this.synchronized {
        entry
      }
    }

    val current: CacheEntryHolder = new CacheEntryHolder

    () => current.cacheEntry match {
      case InitCacheEntry => {
        val newVal = gen()
        current.cacheEntry(ExpiringCacheEntry(newVal, genTime()))
        newVal
      }

      case ExpiringCacheEntry(currentValue, lastUpdateInMillis) => {
        val now = genTime()
        if (now - lastUpdateInMillis > expirationInMillis) {
          val newValue = gen()
          current.cacheEntry(ExpiringCacheEntry(newValue, now))
          newValue
        }
        else {
          currentValue
        }
      }
    }
  }

  def mayBeCached[T](
    cacheOn: Boolean = true,
    gen: () => T,
    expirationInMillis: Option[Long] = None,
    currentTimeInMillis: () => Long = System.currentTimeMillis _
  ): () => T = {
    if (cacheOn) {
      expirationInMillis match {
        case None => {
          lazy val value = gen()
          () => value
        }
        case Some(dur) =>
          expiringCache(dur, gen, currentTimeInMillis)
      }
    }
    else () => gen()
  }

  def cacheOnProd[T](gen: () => T): () => T = mayBeCached(env.mode == Mode.Prod, gen)

  def Conf: Configuration = cacheOnProd(() => conf)()
  def config[T](f: Configuration => T): () => T = cacheOnProd(() => f(Conf))
}


