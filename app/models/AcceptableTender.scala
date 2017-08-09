package models

import javax.inject.Inject

import helpers.{Cache, HasLogger}

import scala.collection.immutable
import scala.collection.JavaConversions._
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigValue
import play.api.{Configuration, Logger}

class AcceptableTender @Inject() (
  conf: Configuration,
  cache: Cache
) extends HasLogger {
  val AcceptableTender: () => immutable.Map[UserTypeCode, immutable.Set[TenderType]] = cache.config { cfg =>
    val tendersByUserTypeCode = conf.getOptional[Configuration]("acceptableTenders").getOrElse(
      throw new IllegalStateException("Cannot find configuration 'acceptableTenders'")
    )

    immutable.HashMap[UserTypeCode, immutable.Set[TenderType]]() ++
    classOf[UserTypeCode].getEnumConstants().map { utc =>
      val tendersList: Seq[String] = tendersByUserTypeCode.getOptional[Seq[String]](utc.toString).getOrElse {
        logger.error("Cannot find configuration 'acceptableTenders." + utc + "'")
        Seq()
      }

      utc -> (
        immutable.HashSet[TenderType]() ++ tendersList.map(
          t => TenderType.fromString(t)
        )
      )
    }
  }
}
