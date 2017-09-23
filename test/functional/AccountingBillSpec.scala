package functional

import java.time.ZoneId
import play.api.i18n.MessagesApi
import anorm._
import play.api.test.Helpers._
import helpers.Helper._
import models._
import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import java.sql.Connection
import java.time.Instant
import java.time.format.DateTimeFormatter

import com.ruimo.scoins.Scoping._
import play.api.test.{WebDriverFactory, WithBrowser}
import play.api.{Application => PlayApp}
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database

class AccountingBillSpec extends Specification with InjectorSupport {
  def appl: PlayApp = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

  case class Master(
    sites: Vector[Site],
    items: Vector[Item],
    itemNames: Vector[Map[LocaleInfo, ItemName]],
    itemPriceHistories: Vector[ItemPriceHistory],
    boxes: Vector[ShippingBox],
    transporters: Vector[Transporter],
    transporterNames: Vector[TransporterName]
  )

  case class Tran(
    shoppingCartItems: Vector[ShoppingCartItem],
    shippingDate: ShippingDate,
    tranHeader: TransactionLogHeader,
    tranSiteHeader: Seq[TransactionLogSite]
  )

  "Accounting bill" should {
    "Only transaction for the specified store will be shown" in new WithBrowser(
      WebDriverFactory(CHROME), appl
    ) {
      inject[Database].withConnection { implicit conn =>
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val adminUser = loginWithTestUser(browser)
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        // Need site for other customizations where company is selected from sites available in the application.
        val site1 = inject[SiteRepo].createNew(Ja, "商店111")
        createNormalUser(
          browser, "11111111", "password01", "user01@mail.xxx", "firstName01", "lastName01", "company01"
        )
        val user = inject[StoreUserRepo].findByUserName("11111111").get

        val master = createMaster
        val tran = createTransaction(master, user, shoppingCartFactory = createShoppingCartItems)
        browser.goTo(
          controllers.routes.AccountingBill.index() + "?lang=" + lang.code
        )
        browser.find("#siteDropDown").find("option[value=\"" + master.sites(1).id.get + "\"]").click()

        SQL(
          """
          update transaction_status
          set status = {status},
          last_update = {lastUpdate}
          """
        ).on(
          'status -> TransactionStatus.SHIPPED.ordinal,
          'lastUpdate -> date("2015-02-03")
        ).executeUpdate()

        browser.find("#storeYear").fill().`with`("%tY".format(toDate(tran.tranHeader.transactionTime)))
        browser.find("#storeMonth").fill().`with`("%tm".format(toDate(tran.tranHeader.transactionTime)))
        browser.find("#storeSubmit").click();

        // Only transaction of tran.sites(1) should be shown.
        browser.find(".accountingBillTable").size() === 1
        doWith(browser.find(".accountingBillTable")) { tbl =>
          doWith(tbl.find(".accountingBillHeaderTable")) { headerTbl =>
            headerTbl.find(".tranId").text() === tran.tranHeader.id.get.toString
            headerTbl.find(".tranDateTime").text() === "%1$tY/%1$tm/%1$td %1$tH:%1$tM".format(
              toDate(tran.tranHeader.transactionTime)
            )
            headerTbl.find(".tranBuyer").text() === user.firstName + " " + user.lastName
          }

          doWith(tbl.find(".transactionStatus")) { statusTbl =>
            statusTbl.find(".status").text() === Messages("transaction.status.SHIPPED")
            statusTbl.find(".shippingDate").text() === DateTimeFormatter.ofPattern(Messages("shipping.date.format")).format(
              date("2015-02-03").atZone(ZoneId.systemDefault())
            )
          }

          val quantity = tran.shoppingCartItems.filter(_.siteId == master.sites(1).id.get).head.quantity
          tbl.find(".itemName.body").size === 1
          tbl.find(".itemName.body").text() === master.itemNames(1)(Ja).name
          tbl.find(".quantity.body").text() === quantity.toString
          tbl.find(".price.body").text() ===
            "%,.0f円".format(master.itemPriceHistories(1).costPrice * quantity)
          tbl.find(".subtotal.body").text() ===
            "%,.0f円".format(master.itemPriceHistories(1).costPrice * quantity)
          tbl.find(".boxName.body").text() === master.boxes.filter(_.siteId == master.sites(1).id.get).head.boxName
          val boxCount = (5 + 1) / 2
          tbl.find(".boxCount.body").text() === boxCount.toString
          tbl.find(".boxPrice.body").text() === "%,d円".format(234 * boxCount)
          tbl.find(".subtotal.box.body").text() === "%,d円".format(234 * boxCount)
          tbl.find(".tax.body").text() ===
            "%,d円".format((master.itemPriceHistories(1).costPrice * quantity).toInt * 5 / 100)
          tbl.find(".total.body").text() ===
            "%,d円".format(
              (master.itemPriceHistories(1).costPrice * quantity).toInt +
              (master.itemPriceHistories(1).costPrice * quantity).toInt * 5 / 100 +
              234 * boxCount
            )
        }

        // Check CSV
        val summaries = inject[TransactionSummary].listByPeriod(
          siteId = master.sites(1).id,
          yearMonth = YearMonth(
            "%tY".format(toDate(tran.tranHeader.transactionTime)).toInt,
            "%tm".format(toDate(tran.tranHeader.transactionTime)).toInt,
            ""
          ),
          onlyShipped = true, useShippedDate = false
        )
        val siteTranByTranId = inject[TransactionSummary].getSiteTranByTranId(summaries, List(lang))
        val csv = inject[controllers.AccountingBill].createCsv(
          summaries, siteTranByTranId, true
        )

        csv === (
          Messages("accountingBillCsvHeaderUserId") + "," +
            Messages("accountingBillCsvHeaderUserName") + "," +
            Messages("accountingBillCsvHeaderCompanyName") + "," +
            Messages("accountingBillCsvHeaderItemTotal") + "," +
            Messages("accountingBillCsvHeaderOuterTax") + "," +
            Messages("accountingBillCsvHeaderFee") + "," +
            Messages("accountingBillCsvHeaderGrandTotal") + "\r\n" +
          user.id.get + 
          ",\"" + user.fullName + "\"" +
          ",\"" + user.companyName.getOrElse("") + "\"" +
          ",950.00,47,702.00,1699.00\r\n"
        )
      }
    }

    "All transaction by users will be shown" in new WithBrowser(
      WebDriverFactory(CHROME), appl
    ) {
      inject[Database].withConnection { implicit conn =>
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)
        val adminUser = loginWithTestUser(browser)

        // Need site for other customizations where company is selected from sites available in the application.
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店111")
        createNormalUser(
          browser, "11111111", "password01", "user01@mail.xxx", "firstName01", "lastName01", "商店111"
        )
        createNormalUser(
          browser, "22222222", "password02", "user02@mail.xxx", "firstName02", "lastName02", "商店111"
        )
        val user01 = inject[StoreUserRepo].findByUserName("11111111").get
        val user02 = inject[StoreUserRepo].findByUserName("22222222").get

        val master = createMaster
        val trans = Vector(
          createTransaction(master, user01, shoppingCartFactory = createShoppingCartItems),
          createTransaction(master, user02, shoppingCartFactory = createShoppingCartItems),
          createTransaction(master, user01, shoppingCartFactory = createShoppingCartItems)
        )
        browser.goTo(
          controllers.routes.AccountingBill.index() + "?lang=" + lang.code
        )
        browser.find("#siteDropDown").find("option[value=\"" + master.sites(1).id.get + "\"]").click()

        SQL(
          """
          update transaction_status
          set status = {status},
          last_update = {lastUpdate}
          """
        ).on(
          'status -> TransactionStatus.SHIPPED.ordinal,
          'lastUpdate -> date("2015-02-03")
        ).executeUpdate()

        browser.find("#userYear").fill().`with`("%tY".format(toDate(trans(0).tranHeader.transactionTime)))
        browser.find("#userMonth").fill().`with`("%tm".format(toDate(trans(0).tranHeader.transactionTime)))
        browser.find("#userSubmit").click();

        browser.find(".accountingBillTable").size() === 6
        val (tbl0, tbl1) = if (
          browser.find(".accountingBillTable").index(0).find(".itemName.body").size == 2
        ) (0, 1) else (1, 0)

        doWith(browser.find(".accountingBillTable").index(tbl0)) { tbl =>
          doWith(tbl.find(".accountingBillHeaderTable")) { headerTbl =>
            headerTbl.find(".tranId").text() === trans(2).tranHeader.id.get.toString
            headerTbl.find(".tranDateTime").text() === "%1$tY/%1$tm/%1$td %1$tH:%1$tM".format(
              toDate(trans(tbl0).tranHeader.transactionTime)
            )
            headerTbl.find(".tranBuyer").text() === user01.firstName + " " + user01.lastName
          }

          doWith(tbl.find(".transactionStatus")) { statusTbl =>
            statusTbl.find(".status").text() === Messages("transaction.status.SHIPPED")
            statusTbl.find(".shippingDate").text() === DateTimeFormatter.ofPattern(Messages("shipping.date.format")).format(
              date("2015-02-03").atZone(ZoneId.systemDefault())
            )
          }
          val items = trans(0).shoppingCartItems.filter(_.siteId == master.sites(0).id.get)
          tbl.find(".itemName.body").size === 2
          tbl.find(".itemName.body").index(0).text() === master.itemNames(0)(Ja).name
          tbl.find(".itemName.body").index(1).text() === master.itemNames(2)(Ja).name

          tbl.find(".quantity.body").index(0).text() === items(0).quantity.toString
          tbl.find(".quantity.body").index(1).text() === items(1).quantity.toString
          tbl.find(".price.body").index(0).text() ===
            "%,.0f円".format(master.itemPriceHistories(0).unitPrice * items(0).quantity)
          tbl.find(".price.body").index(1).text() ===
            "%,.0f円".format(master.itemPriceHistories(2).unitPrice * items(1).quantity)
          tbl.find(".subtotal.body").text() ===
            "%,.0f円".format(
              master.itemPriceHistories(0).unitPrice * items(0).quantity +
              master.itemPriceHistories(2).unitPrice * items(1).quantity
            )
          tbl.find(".boxName.body").text() === master.boxes.filter(_.siteId == master.sites(0).id.get).head.boxName
          val boxCount = 1
          tbl.find(".boxCount.body").text() === boxCount.toString
          tbl.find(".boxPrice.body").text() === "%,d円".format(123 * boxCount)
          tbl.find(".subtotal.box.body").text() === "%,d円".format(123 * boxCount)
          tbl.find(".tax.body").text() ===
            "%,d円".format(
              (
                master.itemPriceHistories(0).unitPrice * items(0).quantity +
                master.itemPriceHistories(2).unitPrice * items(1).quantity
              ).toInt * 5 / 100
            )
          tbl.find(".total.body").text() ===
            "%,d円".format(
              (
                master.itemPriceHistories(0).unitPrice * items(0).quantity +
                master.itemPriceHistories(2).unitPrice * items(1).quantity
              ).toInt * 105 / 100 +
              123 * boxCount
            )
        }

        // Should be ordered by user and transaction id.
        doWith(browser.find(".accountingBillTable").index(tbl1)) { tbl =>
          doWith(tbl.find(".accountingBillHeaderTable")) { headerTbl =>
            headerTbl.find(".tranId").text() === trans(2).tranHeader.id.get.toString
            headerTbl.find(".tranBuyer").text() === user01.firstName + " " + user01.lastName
          }
        }

        doWith(browser.find(".accountingBillTable").index(2)) { tbl =>
          doWith(tbl.find(".accountingBillHeaderTable")) { headerTbl =>
            headerTbl.find(".tranId").text() === trans(0).tranHeader.id.get.toString
            headerTbl.find(".tranBuyer").text() === user01.firstName + " " + user01.lastName
          }
        }

        doWith(browser.find(".accountingBillTable").index(3)) { tbl =>
          doWith(tbl.find(".accountingBillHeaderTable")) { headerTbl =>
            headerTbl.find(".tranId").text() === trans(0).tranHeader.id.get.toString
            headerTbl.find(".tranBuyer").text() === user01.firstName + " " + user01.lastName
          }
        }

        doWith(browser.find(".accountingBillTable").index(4)) { tbl =>
          doWith(tbl.find(".accountingBillHeaderTable")) { headerTbl =>
            headerTbl.find(".tranId").text() === trans(1).tranHeader.id.get.toString
            headerTbl.find(".tranBuyer").text() === user02.firstName + " " + user02.lastName
          }
        }

        doWith(browser.find(".accountingBillTable").index(5)) { tbl =>
          doWith(tbl.find(".accountingBillHeaderTable")) { headerTbl =>
            headerTbl.find(".tranId").text() === trans(1).tranHeader.id.get.toString
            headerTbl.find(".tranBuyer").text() === user02.firstName + " " + user02.lastName
          }
        }

        // Check CSV
        val table: AccountingBillTable = inject[TransactionSummary].accountingBillForUser(
          None,
          YearMonth(
            "%tY".format(toDate(trans(0).tranHeader.transactionTime)).toInt,
            "%tm".format(toDate(trans(0).tranHeader.transactionTime)).toInt,
            ""
          ),
          None,
          List(lang),
          false
        )

        val csv = inject[controllers.AccountingBill].createCsv(
          table.summaries, table.siteTranByTranId, false
        )

        csv === (
          Messages("accountingBillCsvHeaderUserId") + "," +
            Messages("accountingBillCsvHeaderUserName") + "," +
            Messages("accountingBillCsvHeaderCompanyName") + "," +
            Messages("accountingBillCsvHeaderItemTotal") + "," +
            Messages("accountingBillCsvHeaderOuterTax") + "," +
            Messages("accountingBillCsvHeaderFee") + "," +
            Messages("accountingBillCsvHeaderGrandTotal") + "\r\n" +
          user01.id.get + 
          ",\"" + user01.fullName + "\"" +
          ",\"" + user01.companyName.getOrElse("") + "\"" +
          ",6800.00,340.00,1650.00,8790.00\r\n" +
          user02.id.get + 
          ",\"" + user02.fullName + "\"" +
          ",\"" + user02.companyName.getOrElse("") + "\"" +
          ",3400.00,170.00,825.00,4395.00\r\n"
        )
      }
    }

    "All transaction that are having sent status should be shown" in new WithBrowser(
      WebDriverFactory(CHROME), appl
    ) {
      inject[Database].withConnection { implicit conn =>
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)
        val adminUser = loginWithTestUser(browser)

        // Need site for other customizations where company is selected from sites available in the application.
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店111")
        createNormalUser(
          browser, "11111111", "password01", "user01@mail.xxx", "firstName01", "lastName01", "商店111"
        )
        createNormalUser(
          browser, "22222222", "password02", "user02@mail.xxx", "firstName02", "lastName02", "商店111"
        )
        val user01 = inject[StoreUserRepo].findByUserName("11111111").get
        val user02 = inject[StoreUserRepo].findByUserName("22222222").get

        val master = createMaster
        val trans = Vector(
          createTransaction(master, user01, shoppingCartFactory = createShoppingCartItems),
          createTransaction(master, user02, shoppingCartFactory = createShoppingCartItems),
          createTransaction(master, user01, shoppingCartFactory = createShoppingCartItems)
        )
        browser.goTo(
          controllers.routes.AccountingBill.index() + "?lang=" + lang.code
        )
        browser.find("#siteDropDown").find("option[value=\"" + master.sites(1).id.get + "\"]").click()

        SQL(
          """
          update transaction_status
          set status = {status},
          last_update = {lastUpdate}
          """
        ).on(
          'status -> TransactionStatus.SHIPPED.ordinal,
          'lastUpdate -> date("2015-02-03")
        ).executeUpdate()

        browser.find("#userYear").fill().`with`("%tY".format(toDate(trans(0).tranHeader.transactionTime)))
        browser.find("#userMonth").fill().`with`("%tm".format(toDate(trans(0).tranHeader.transactionTime)))
        browser.find("#userSubmit").click();

        browser.find(".accountingBillTable").size() === 6
        val (tbl0, tbl1) = if (
          browser.find(".accountingBillTable").index(0).find(".itemName.body").size == 2
        ) (0, 1) else (1, 0)

        doWith(browser.find(".accountingBillTable").index(tbl0)) { tbl =>
          doWith(tbl.find(".accountingBillHeaderTable")) { headerTbl =>
            headerTbl.find(".tranId").text() === trans(2).tranHeader.id.get.toString
            headerTbl.find(".tranDateTime").text() === "%1$tY/%1$tm/%1$td %1$tH:%1$tM".format(
              trans(0).tranHeader.transactionTime.atZone(ZoneId.systemDefault())
            )
            headerTbl.find(".tranBuyer").text() === user01.firstName + " " + user01.lastName
          }

          doWith(tbl.find(".transactionStatus")) { statusTbl =>
            statusTbl.find(".status").text() === Messages("transaction.status.SHIPPED")
            statusTbl.find(".shippingDate").text() === DateTimeFormatter.ofPattern(Messages("shipping.date.format")).format(
              date("2015-02-03").atZone(ZoneId.systemDefault())
            )
          }
          val items = trans(0).shoppingCartItems.filter(_.siteId == master.sites(0).id.get)
          tbl.find(".itemName.body").size === 2
          tbl.find(".itemName.body").index(0).text() === master.itemNames(0)(Ja).name
          tbl.find(".itemName.body").index(1).text() === master.itemNames(2)(Ja).name

          tbl.find(".quantity.body").index(0).text() === items(0).quantity.toString
          tbl.find(".quantity.body").index(1).text() === items(1).quantity.toString
          tbl.find(".price.body").index(0).text() ===
            "%,.0f円".format(master.itemPriceHistories(0).unitPrice * items(0).quantity)
          tbl.find(".price.body").index(1).text() ===
            "%,.0f円".format(master.itemPriceHistories(2).unitPrice * items(1).quantity)
          tbl.find(".subtotal.body").text() ===
            "%,.0f円".format(
              master.itemPriceHistories(0).unitPrice * items(0).quantity +
              master.itemPriceHistories(2).unitPrice * items(1).quantity
            )
          tbl.find(".boxName.body").text() === master.boxes.filter(_.siteId == master.sites(0).id.get).head.boxName
          val boxCount = 1
          tbl.find(".boxCount.body").text() === boxCount.toString
          tbl.find(".boxPrice.body").text() === "%,d円".format(123 * boxCount)
          tbl.find(".subtotal.box.body").text() === "%,d円".format(123 * boxCount)
          tbl.find(".tax.body").text() ===
            "%,d円".format(
              (
                master.itemPriceHistories(0).unitPrice * items(0).quantity +
                master.itemPriceHistories(2).unitPrice * items(1).quantity
              ).toInt * 5 / 100
            )
          tbl.find(".total.body").text() ===
            "%,d円".format(
              (
                master.itemPriceHistories(0).unitPrice * items(0).quantity +
                master.itemPriceHistories(2).unitPrice * items(1).quantity
              ).toInt * 105 / 100 +
              123 * boxCount
            )
        }

        // Should be ordered by user and transaction id.
        doWith(browser.find(".accountingBillTable").index(tbl1)) { tbl =>
          doWith(tbl.find(".accountingBillHeaderTable")) { headerTbl =>
            headerTbl.find(".tranId").text() === trans(2).tranHeader.id.get.toString
            headerTbl.find(".tranBuyer").text() === user01.firstName + " " + user01.lastName
          }
        }

        doWith(browser.find(".accountingBillTable").index(2)) { tbl =>
          doWith(tbl.find(".accountingBillHeaderTable")) { headerTbl =>
            headerTbl.find(".tranId").text() === trans(0).tranHeader.id.get.toString
            headerTbl.find(".tranBuyer").text() === user01.firstName + " " + user01.lastName
          }
        }

        doWith(browser.find(".accountingBillTable").index(3)) { tbl =>
          doWith(tbl.find(".accountingBillHeaderTable")) { headerTbl =>
            headerTbl.find(".tranId").text() === trans(0).tranHeader.id.get.toString
            headerTbl.find(".tranBuyer").text() === user01.firstName + " " + user01.lastName
          }
        }

        doWith(browser.find(".accountingBillTable").index(4)) { tbl =>
          doWith(tbl.find(".accountingBillHeaderTable")) { headerTbl =>
            headerTbl.find(".tranId").text() === trans(1).tranHeader.id.get.toString
            headerTbl.find(".tranBuyer").text() === user02.firstName + " " + user02.lastName
          }
        }

        doWith(browser.find(".accountingBillTable").index(5)) { tbl =>
          doWith(tbl.find(".accountingBillHeaderTable")) { headerTbl =>
            headerTbl.find(".tranId").text() === trans(1).tranHeader.id.get.toString
            headerTbl.find(".tranBuyer").text() === user02.firstName + " " + user02.lastName
          }
        }

        // Check CSV
        val table: AccountingBillTable = inject[TransactionSummary].accountingBillForUser(
          None,
          YearMonth(
            "%tY".format(toDate(trans(0).tranHeader.transactionTime)).toInt,
            "%tm".format(toDate(trans(0).tranHeader.transactionTime)).toInt,
            ""
          ),
          None,
          List(lang),
          false
        )

        val csv = inject[controllers.AccountingBill].createCsv(
          table.summaries, table.siteTranByTranId, false
        )

        csv === (
          Messages("accountingBillCsvHeaderUserId") + "," +
            Messages("accountingBillCsvHeaderUserName") + "," +
            Messages("accountingBillCsvHeaderCompanyName") + "," +
            Messages("accountingBillCsvHeaderItemTotal") + "," +
            Messages("accountingBillCsvHeaderOuterTax") + "," +
            Messages("accountingBillCsvHeaderFee") + "," +
            Messages("accountingBillCsvHeaderGrandTotal") + "\r\n" +
          user01.id.get + 
          ",\"" + user01.fullName + "\"" +
          ",\"" + user01.companyName.getOrElse("") + "\"" +
          ",6800.00,340.00,1650.00,8790.00\r\n" +
          user02.id.get + 
          ",\"" + user02.fullName + "\"" +
          ",\"" + user02.companyName.getOrElse("") + "\"" +
          ",3400.00,170.00,825.00,4395.00\r\n"
        )
      }
    }

    "Transactions having sent status should be shown." in new WithBrowser(
      WebDriverFactory(CHROME), appl
    ) {
      inject[Database].withConnection { implicit conn =>
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)
        val adminUser = loginWithTestUser(browser)

        // Need site for other customizations where company is selected from sites available in the application.
        val site1 = inject[SiteRepo].createNew(localeInfo.Ja, "商店111")
        createNormalUser(
          browser, "11111111", "password01", "user01@mail.xxx", "firstName01", "lastName01", "商店111"
        )
        val user01 = inject[StoreUserRepo].findByUserName("11111111").get

        val master = createMaster
        val trans = Vector(
          createTransaction(
            master = master, user = user01,
            shoppingCartFactory =
              (userId, master) => Vector(
                inject[ShoppingCartItemRepo].addItem(user01.id.get, master.sites(0).id.get, master.items(0).id.get.id, 3)(conn),
                inject[ShoppingCartItemRepo].addItem(user01.id.get, master.sites(1).id.get, master.items(1).id.get.id, 5)(conn)
              )
          )
        )

        trans.size === 1
        SQL(
          """
          update transaction_status
          set status = {status},
          last_update = {lastUpdate}
          where transaction_site_id = {tranSiteId}
          """
        ).on(
          'status -> TransactionStatus.SHIPPED.ordinal,
          'lastUpdate -> date("2015-02-03"),
          'tranSiteId -> trans(0).tranSiteHeader(1).id.get
        ).executeUpdate()

        browser.goTo(
          controllers.routes.AccountingBill.index() + "?lang=" + lang.code
        )

        browser.find("#userYear").fill().`with`("%tY".format(toDate(trans(0).tranHeader.transactionTime)))
        browser.find("#userMonth").fill().`with`("%tm".format(toDate(trans(0).tranHeader.transactionTime)))
        browser.find("#userSubmit").click();

        browser.find(".transactionStatus").size === 1
        browser.find(".transactionStatus .status").text() === Messages("transaction.status.SHIPPED")

        // Check CSV
        val table: AccountingBillTable = inject[TransactionSummary].accountingBillForUser(
          None,
          YearMonth(
            "%tY".format(toDate(trans(0).tranHeader.transactionTime)).toInt,
            "%tm".format(toDate(trans(0).tranHeader.transactionTime)).toInt,
            ""
          ),
          None,
          List(lang),
          false
        )

        val csv = inject[controllers.AccountingBill].createCsv(
          table.summaries, table.siteTranByTranId, false
        )

        csv === (
          Messages("accountingBillCsvHeaderUserId") + "," +
            Messages("accountingBillCsvHeaderUserName") + "," +
            Messages("accountingBillCsvHeaderCompanyName") + "," +
            Messages("accountingBillCsvHeaderItemTotal") + "," +
            Messages("accountingBillCsvHeaderOuterTax") + "," +
            Messages("accountingBillCsvHeaderFee") + "," +
            Messages("accountingBillCsvHeaderGrandTotal") + "\r\n" +
          user01.id.get + 
          ",\"" + user01.fullName + "\"" +
          ",\"" + user01.companyName.getOrElse("") + "\"" +
          ",1000.00,50.00,702.00,1752.00\r\n"
        )
      }
    }
  }

  def createMaster(implicit conn: Connection, app: PlayApp): Master = {
    val currencyInfo = inject[CurrencyRegistry]
    val localeInfo = inject[LocaleInfoRepo]
    import localeInfo.{Ja, En}

    val taxes = Vector(
      inject[TaxRepo].createNew, inject[TaxRepo].createNew
    )
    val taxHistories = Vector(
      inject[TaxHistoryRepo].createNew(taxes(0), TaxType.OUTER_TAX, BigDecimal("5"), date("9999-12-31")),
      inject[TaxHistoryRepo].createNew(taxes(1), TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))
    )

    val sites = Vector(inject[SiteRepo].createNew(Ja, "商店1"), inject[SiteRepo].createNew(Ja, "商店2"))
    
    val cat1 = inject[CategoryRepo].createNew(
      Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
    )
    
    val items = Vector(
      inject[ItemRepo].createNew(cat1), inject[ItemRepo].createNew(cat1), inject[ItemRepo].createNew(cat1)
    )
    
    inject[SiteItemRepo].createNew(sites(0), items(0))
    inject[SiteItemRepo].createNew(sites(1), items(1))
    inject[SiteItemRepo].createNew(sites(0), items(2))
    
    inject[SiteItemNumericMetadataRepo].createNew(sites(0).id.get, items(0).id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, 1L)
    inject[SiteItemNumericMetadataRepo].createNew(sites(1).id.get, items(1).id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, 1L)
    inject[SiteItemNumericMetadataRepo].createNew(sites(0).id.get, items(2).id.get, SiteItemNumericMetadataType.SHIPPING_SIZE, 1L)
    
    val itemNames = Vector(
      inject[ItemNameRepo].createNew(items(0), Map(Ja -> "植木1")),
      inject[ItemNameRepo].createNew(items(1), Map(Ja -> "植木2")),
      inject[ItemNameRepo].createNew(items(2), Map(Ja -> "植木3"))
    )
    
    val itemDesc1 = inject[ItemDescriptionRepo].createNew(items(0), sites(0), "desc1")
    val itemDesc2 = inject[ItemDescriptionRepo].createNew(items(1), sites(1), "desc2")
    val itemDesc3 = inject[ItemDescriptionRepo].createNew(items(2), sites(0), "desc3")
    
    val itemPrice1 = inject[ItemPriceRepo].createNew(items(0), sites(0))
    val itemPrice2 = inject[ItemPriceRepo].createNew(items(1), sites(1))
    val itemPrice3 = inject[ItemPriceRepo].createNew(items(2), sites(0))
    
    val itemPriceHistories = Vector(
      inject[ItemPriceHistoryRepo].createNew(
        itemPrice1, taxes(0), currencyInfo.Jpy, BigDecimal("100"), None, BigDecimal("90"), date("9999-12-31")
      ),
      inject[ItemPriceHistoryRepo].createNew(
        itemPrice2, taxes(0), currencyInfo.Jpy, BigDecimal("200"), None, BigDecimal("190"), date("9999-12-31")
      ),
      inject[ItemPriceHistoryRepo].createNew(
        itemPrice3, taxes(0), currencyInfo.Jpy, BigDecimal("300"), None, BigDecimal("290"), date("9999-12-31")
      )
    )

    val boxes = Vector(
      inject[ShippingBoxRepo].createNew(sites(0).id.get, 1L, 3, "site-box1"),
      inject[ShippingBoxRepo].createNew(sites(1).id.get, 1L, 2, "site-box2")
    )
    
    val fee1 = inject[ShippingFeeRepo].createNew(boxes(0).id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
    val fee2 = inject[ShippingFeeRepo].createNew(boxes(1).id.get, CountryCode.JPN, JapanPrefecture.東京都.code)
    
    val feeHis1 = inject[ShippingFeeHistoryRepo].createNew(
      fee1.id.get, taxes(1).id.get, BigDecimal(123), None, date("9999-12-31")
    )
    val feeHis2 = inject[ShippingFeeHistoryRepo].createNew(
      fee2.id.get, taxes(1).id.get, BigDecimal(234), Some(BigDecimal(111)), date("9999-12-31")
    )

    val transporters = Vector(inject[TransporterRepo].createNew, inject[TransporterRepo].createNew)
    val transporterNames = Vector(
      inject[TransporterNameRepo].createNew(
        transporters(0).id.get, Ja, "トマト運輸"
      ),
      inject[TransporterNameRepo].createNew(
        transporters(1).id.get, Ja, "ヤダワ急便"
      )
    )

    Master(sites, items, itemNames, itemPriceHistories, boxes, transporters, transporterNames)
  }

  def createShoppingCartItems(
    userId: Long, master: Master
  )(
    implicit conn: Connection, app: PlayApp
  ): Vector[ShoppingCartItem] = Vector(
    inject[ShoppingCartItemRepo].addItem(userId, master.sites(0).id.get, master.items(0).id.get.id, 3),
    inject[ShoppingCartItemRepo].addItem(userId, master.sites(1).id.get, master.items(1).id.get.id, 5),
    inject[ShoppingCartItemRepo].addItem(userId, master.sites(0).id.get, master.items(2).id.get.id, 7)
  )

  def createTransaction(
    master: Master, user: StoreUser, now: Instant = Instant.now(), offset: Int = 0,
    sentDate: Option[Long] = None,
    shoppingCartFactory: (Long, Master) => Vector[ShoppingCartItem]
  )(
    implicit conn: Connection, app: PlayApp
  ): Tran = {
    val currencyInfo = inject[CurrencyRegistry]
    val localeInfo = inject[LocaleInfoRepo]
    inject[ShoppingCartItemRepo].removeForUser(user.id.get)

    val shoppingCartItems = shoppingCartFactory(user.id.get, master)

    val addr1 = Address.createNew(
      countryCode = CountryCode.JPN,
      firstName = "firstName1",
      lastName = "lastName1",
      zip1 = "zip1",
      zip2 = "zip2",
      prefecture = JapanPrefecture.東京都,
      address1 = "address1-1",
      address2 = "address1-2",
      tel1 = "tel1-1",
      comment = "comment1"
    )
    
    val shippingTotal1 = inject[ShippingFeeHistoryRepo].feeBySiteAndItemClass(
      CountryCode.JPN, JapanPrefecture.東京都.code,
      ShippingFeeEntries().add(master.sites(0), 1L, 3).add(master.sites(1), 1L, 5),
      now
    )
    val shippingDate1 = ShippingDate(
      Map(
        master.sites(0).id.get -> ShippingDateEntry(master.sites(0).id.get, date("2013-02-03")),
        master.sites(1).id.get -> ShippingDateEntry(master.sites(1).id.get, date("2013-02-03"))
      )
    )

    implicit val storeUserRepo = inject[StoreUserRepo]
    val (cartTotal: ShoppingCartTotal, errors: Seq[ItemExpiredException]) =
      inject[ShoppingCartItemRepo].listItemsForUser(localeInfo.Ja, LoginSession(user, None, 0))
    implicit val taxRepo = inject[TaxRepo]
    val tranId = inject[TransactionPersister].persist(
      Transaction(user.id.get, currencyInfo.Jpy, cartTotal, Some(addr1), shippingTotal1, shippingDate1, now)
    )
    val tranList = TransactionLogHeader.list()
    val tranSiteList = inject[TransactionLogSiteRepo].list()

    Tran(
      shoppingCartItems,
      shippingDate1,
      tranList(0),
      tranSiteList
    )
  }
}


