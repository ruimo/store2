package controllers

import java.util.Locale

import play.api.i18n.Lang

trait I18n {
  val japanese = new Lang(Locale.JAPANESE)
  val japan = new Lang(Locale.JAPAN)
  val us = new Lang(Locale.US)

  val langs = List(japanese, japan, us)
}
