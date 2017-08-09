package models

import play.api.db.Database
import javax.inject.Inject
import javax.inject.Singleton

import anorm._
import anorm.SqlParser
import java.util.Locale

import play.api.i18n.{Lang, Messages, MessagesProvider}

import scala.collection.mutable
import scala.collection.immutable
import scala.language.postfixOps
import java.sql.Connection


case class LocaleInfo(id: Long, lang: String, country: Option[String] = None) {
  def toLocale: Locale = country match {
    case None => new Locale(lang)
    case Some(c) => new Locale(lang, c)
  }

  def matchExactly(l: Lang): Boolean =
    matchLanguage(l) && (country match {
      case None => true
      case Some(c) => c == l.country
    })

  def matchLanguage(l: Lang): Boolean = lang == l.language
}

@Singleton
class LocaleInfoRepo @Inject() (
  localeInfoRepo: LocaleInfoRepo,
  db: Database
) {
  lazy val Ja = apply(1L)
  lazy val En = apply(2L)

  val simple = {
    SqlParser.get[Long]("locale.locale_id") ~
    SqlParser.get[String]("locale.lang") ~
    SqlParser.get[Option[String]]("locale.country") map {
      case id~lang~country => LocaleInfo(id, lang, country)
    }
  }

  lazy val registry: immutable.SortedMap[Long, LocaleInfo] = db.withConnection { implicit conn =>
    immutable.TreeMap(
      SQL("select * from locale")
        .as(localeInfoRepo.simple *)
        .map(r => r.id -> r): _*
    )
  }

  lazy val byLangTable: Map[Lang, LocaleInfo] =
    registry.values.foldLeft(new mutable.HashMap[Lang, LocaleInfo]) {
      (map, e) => {map.put(Lang(e.lang, e.country.getOrElse("")), e); map}
    }.toMap

  def byLang(lang: Lang): LocaleInfo =
    byLangTable.get(lang).orElse(
      byLangTable.get(Lang(lang.language))
    ).get

  def localeTable(implicit mp: MessagesProvider): Seq[(String, String)] = registry.values.map {
    e => e.id.toString -> Messages("lang." + e.lang)
  }.toSeq

  def getDefault(implicit langs: List[Lang]): LocaleInfo = langs match {
    case Seq() => localeInfoRepo.En
    case l::tail =>
      byLangTable.get(l).orElse {
        byLangTable.get(Lang(l.language))
      } match {
        case None => getDefault(tail)
        case Some(linfo) => linfo
      }
  }

  def apply(id: Long): LocaleInfo = get(id).get

  def get(id: Long): Option[LocaleInfo] = registry.get(id)

  def insert(locale: LocaleInfo)(implicit conn: Connection) = SQL(
    """
    insert into locale values (
    {id}, {lang}, {country}
      )
    """
  ).on(
    'id -> locale.id,
    'lang -> locale.lang,
    'country -> locale.country
  ).executeUpdate()
}
