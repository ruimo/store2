package models

import java.sql.Connection

case class CreateShippingFee(feeId: Long, countryCode: Int, locationCodeTable: List[Int]) {
  def update(boxId: Long)(implicit shippingFeeRepo: ShippingFeeRepo, conn: Connection) {
    locationCodeTable.map { loc =>
      try {
        ExceptionMapper.mapException {
          shippingFeeRepo.createNew(boxId, CountryCode.byIndex(countryCode), loc)
        }
      }
      catch {
        case e: UniqueConstraintException =>
      }
    }
  }
}

