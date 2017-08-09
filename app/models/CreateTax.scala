package models

import java.sql.Date.{valueOf => date}
import java.sql.Connection

case class CreateTax(taxType: Int, localeId: Long, name: String, rate: BigDecimal) {
  def save()(
    implicit conn: Connection,
    taxRepo: TaxRepo,
    taxNameRepo: TaxNameRepo,
    localeInfoRepo: LocaleInfoRepo,
    taxHistoryRepo: TaxHistoryRepo
  ): Unit = {
    val tax = taxRepo.createNew
    taxNameRepo.createNew(tax, localeInfoRepo(localeId), name)
    taxHistoryRepo.createNew(tax, TaxType.byIndex(taxType), rate, date("9999-12-31").getTime)
  }
}

