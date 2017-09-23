package functional

import play.api.test._
import play.api.test.Helpers._
import play.api.i18n.MessagesApi
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import java.time.Instant
import play.api.{Application => PlayApp}
import play.api.inject.guice.GuiceApplicationBuilder
import helpers.InjectorSupport
import play.api.db.Database
import helpers.UrlHelper
import helpers.UrlHelper._
import java.util.concurrent.TimeUnit
import play.api.test._
import play.api.test.Helpers._
import java.sql.Connection

import helpers.Helper._

import org.specs2.mutable.Specification
import play.api.test.{Helpers, TestServer}
import play.api.i18n.{Lang, Messages}
import models._
import play.api.test.TestServer
import controllers.ItemPictures
import java.nio.file.Files
import java.util
import java.nio.charset.Charset
import java.net.{HttpURLConnection, URL}
import java.io.{BufferedReader, InputStreamReader}
import java.text.SimpleDateFormat
import helpers.{ViewHelpers, QueryString}
import com.ruimo.scoins.Scoping._

class ItemMaintenanceSpec extends Specification with InjectorSupport {
  "Item maintenance" should {
    "Create new item." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ Map("hideNewlyCreatedItem" -> false))
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        browser.goTo(
          controllers.routes.ItemMaintenance.startCreateNewItem().url + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#siteId").find("option").text() === "Store01"
        browser.find("#categoryId").find("option").text() === "Cat01"
        browser.find("#taxId").find("option").text() === "外税"
        browser.find("#description").fill().`with`("Description01")

        browser.find("#itemName").fill().`with`("ItemName01")
        browser.find("#price").fill().`with`("1234")
        browser.find("#costPrice").fill().`with`("2345")
        browser.find("#createNewItemForm").find("input[type='submit']").click

        browser.find(".message").text() === Messages("itemIsCreated")

        val itemList = inject[ItemRepo].list(None, Ja, QueryString()).records

        itemList.size === 1
        doWith(itemList.head) { item =>
          item._2.name === "ItemName01"
          item._3.description === "Description01"
          item._4.name === "Store01"
          item._5.unitPrice === BigDecimal("1234")
          item._5.costPrice === BigDecimal("2345")
          item._5.listPrice === None
          Coupon.isCoupon(item._1.id.get) === false
        }
      }
    }

    "Create new item and set list price." in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        browser.goTo(
          controllers.routes.ItemMaintenance.startCreateNewItem().url + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#siteId").find("option").text() === "Store01"
        browser.find("#categoryId").find("option").text() === "Cat01"
        browser.find("#taxId").find("option").text() === "外税"
        browser.find("#description").fill().`with`("Description01")

        browser.find("#itemName").fill().`with`("ItemName01")
        browser.find("#price").fill().`with`("1234")
        browser.find("#costPrice").fill().`with`("2345")
        browser.find("#createNewItemForm").find("input[type='submit']").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".message").text() === Messages("itemIsCreated")

        val itemList = inject[ItemRepo].list(None, Ja, QueryString()).records
        itemList.size === 1

        browser.goTo(
          controllers.routes.ItemMaintenance.startChangeItem(itemList.head._1.id.get.id).url + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#itemNames_0_itemName").attribute("value") === "ItemName01"
        browser.find("#categoryId option").text === "Cat01"
        browser.find("#itemPrices_0_taxId option").text === "外税"
        browser.find("#itemPrices_0_itemPrice").attribute("value") === "1234.00"
        browser.find("#itemPrices_0_listPrice").attribute("value") === ""
        browser.find("#itemPrices_0_costPrice").attribute("value") === "2345.00"
        browser.find("#itemPrices_0_validUntil").attribute("value") === "9999-12-31 23:59:59"

        browser.find("#itemPrices_0_listPrice").fill().`with`("3000")
        browser.find("#changeItemPriceButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#itemPrices_0_listPrice").attribute("value") === "3000.00"

        browser.find("#itemPrices_0_listPrice").fill().`with`("")
        browser.find("#changeItemPriceButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#itemPrices_0_listPrice").attribute("value") === ""
      }
    }

    "Create new item with list price." in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        browser.goTo(
          controllers.routes.ItemMaintenance.startCreateNewItem().url + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#siteId").find("option").text() === "Store01"
        browser.find("#categoryId").find("option").text() === "Cat01"
        browser.find("#taxId").find("option").text() === "外税"
        browser.find("#description").fill().`with`("Description01")

        browser.find("#itemName").fill().`with`("ItemName01")
        browser.find("#price").fill().`with`("1234")
        browser.find("#listPrice").fill().`with`("3000")
        browser.find("#costPrice").fill().`with`("2345")
        browser.find("#createNewItemForm").find("input[type='submit']").click

        browser.find(".message").text() === Messages("itemIsCreated")

        val itemList = inject[ItemRepo].list(None, Ja, QueryString()).records

        itemList.size === 1
        doWith(itemList.head) { item =>
          item._2.name === "ItemName01"
          item._3.description === "Description01"
          item._4.name === "Store01"
          item._5.unitPrice === BigDecimal("1234")
          item._5.listPrice === Some(BigDecimal("3000"))
          item._5.costPrice === BigDecimal("2345")
          Coupon.isCoupon(item._1.id.get) === false
        }
      }
    }

    "Create new item with price memo." in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        browser.goTo(
          controllers.routes.ItemMaintenance.startCreateNewItem().url + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#siteId").find("option").text() === "Store01"
        browser.find("#categoryId").find("option").text() === "Cat01"
        browser.find("#taxId").find("option").text() === "外税"
        browser.find("#description").fill().`with`("Description01")

        browser.find("#itemName").fill().`with`("ItemName01")
        browser.find("#price").fill().`with`("1234")
        browser.find("#costPrice").fill().`with`("2345")
        browser.find("#createNewItemForm").find("input[type='submit']").click
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".message").text() === Messages("itemIsCreated")

        val itemList = inject[ItemRepo].list(None, Ja, QueryString()).records
        itemList.size === 1

        browser.goTo(
          controllers.routes.ItemMaintenance.startChangeItem(itemList.head._1.id.get.id).url + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#itemNames_0_itemName").attribute("value") === "ItemName01"
        browser.find("#categoryId option").text === "Cat01"
        browser.find("#itemPrices_0_taxId option").text === "外税"
        browser.find("#itemPrices_0_itemPrice").attribute("value") === "1234.00"
        browser.find("#itemPrices_0_listPrice").attribute("value") === ""
        browser.find("#itemPrices_0_costPrice").attribute("value") === "2345.00"
        browser.find("#itemPrices_0_validUntil").attribute("value") === "9999-12-31 23:59:59"

        doWith(browser.find("#addSiteItemTextMetadataForm")) { form =>
          form.find(
            "option[value=\"" + SiteItemTextMetadataType.PRICE_MEMO.ordinal + "\"]"
          ).click()

          browser.find("#addSiteItemTextMetadataForm #metadata").fill().`with`("Price memo")

          form.find("input[type='submit']").click()
        }
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#siteItemTextMetadatas_0_metadata").attribute("value") === "Price memo"

        browser.find(".removeSiteItemTextMetadataButton").click()
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".removeSiteItemTextMetadataButton").size === 0
      }
    }

    "Create new coupon item." in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        browser.goTo(
          controllers.routes.ItemMaintenance.startCreateNewItem().url + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#isCoupon").click()
        browser.find("#siteId").find("option").text() === "Store01"
        browser.find("#categoryId").find("option").text() === "Cat01"
        browser.find("#taxId").find("option").text() === "外税"
        browser.find("#description").fill().`with`("Description01")

        browser.find("#itemName").fill().`with`("ItemName01")
        browser.find("#price").fill().`with`("1234")
        browser.find("#costPrice").fill().`with`("2345")
        browser.find("#createNewItemForm").find("input[type='submit']").click

        browser.find(".message").text() === Messages("itemIsCreated")

        val itemList = inject[ItemRepo].list(None, Ja, QueryString()).records

        itemList.size === 1
        doWith(itemList.head) { item =>
          item._2.name === "ItemName01"
          item._3.description === "Description01"
          item._4.name === "Store01"
          item._5.unitPrice === BigDecimal("1234")
          item._5.costPrice === BigDecimal("2345")
          Coupon.isCoupon(item._1.id.get) === true
        }
      }
    }

    "Can edit item that has no handling stores." in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        browser.goTo(
          controllers.routes.ItemMaintenance.startCreateNewItem().url + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#siteId").find("option").text() === "Store01"
        browser.find("#categoryId").find("option").text() === "Cat01"
        browser.find("#taxId").find("option").text() === "外税"
        browser.find("#description").fill().`with`("Description01")

        browser.find("#itemName").fill().`with`("ItemName01")
        browser.find("#price").fill().`with`("1234")
        browser.find("#costPrice").fill().`with`("2345")
        browser.find("#createNewItemForm").find("input[type='submit']").click

        browser.find(".message").text() === Messages("itemIsCreated")

        browser.goTo(
          controllers.routes.ItemMaintenance.editItem(List("")).url + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        doWith(browser.find(".itemTableBody")) { tr =>
          tr.find(".itemTableItemName").text === "ItemName01"
          tr.find(".itemTableSiteName").text === "Store01"
          tr.find(".itemTablePrice").text === ViewHelpers.toAmount(BigDecimal(1234))
        }

        val itemId = browser.find(".itemTableBody .itemTableItemId a").text.toLong

        browser.goTo(
          controllers.routes.ItemMaintenance.startChangeItem(itemId).url + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        // Now handling site becomes zero.
        browser.find(".deleteHandlingSiteButton").click()

        browser.goTo(
          controllers.routes.ItemMaintenance.editItem(List("")).url + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        doWith(browser.find(".itemTableBody")) { tr =>
          tr.find(".itemTableItemName").text === "ItemName01"
          tr.find(".itemTableSiteName").text === "-"
          tr.find(".itemTablePrice").text === "-"
        }
      }
    }

    def createNormalUser(userName: String = "user")(implicit conn: Connection, app: PlayApp): StoreUser =
      inject[StoreUserRepo].create(
        userName, "Admin", None, "Manager", "admin@abc.com",
        4151208325021896473L, -1106301469931443100L, UserRole.NORMAL, Some("Company1")
      )

    def login(browser: TestBrowser, userName: String = "administrator") {
      browser.goTo(controllers.routes.Admin.index.url)
      browser.find("#userName").fill().`with`(userName)
      browser.find("#password").fill().`with`("password")
      browser.find("#doLoginButton").click()
    }

    "Store owner cannot edit item name." in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        val user01 = createNormalUser("user01")
        val siteOwner = inject[SiteUserRepo].createNew(user01.id.get, site.id.get)

        browser.goTo(
          controllers.routes.ItemMaintenance.startCreateNewItem().url + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find("#siteId").find("option").text() === "Store01"
        browser.find("#categoryId").find("option").text() === "Cat01"
        browser.find("#taxId").find("option").text() === "外税"
        browser.find("#description").fill().`with`("Description01")

        browser.find("#itemName").fill().`with`("ItemName01")
        browser.find("#price").fill().`with`("1234")
        browser.find("#costPrice").fill().`with`("2345")
        browser.find("#createNewItemForm").find("input[type='submit']").click

        browser.find(".message").text() === Messages("itemIsCreated")

        browser.goTo(
          controllers.routes.ItemMaintenance.editItem(List("")).url + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        doWith(browser.find(".itemTableBody")) { tr =>
          tr.find(".itemTableItemName").text === "ItemName01"
          tr.find(".itemTableSiteName").text === "Store01"
          tr.find(".itemTablePrice").text === ViewHelpers.toAmount(BigDecimal(1234))
        }

        val itemId = ItemId(browser.find(".itemTableBody .itemTableItemId a").text.toLong)

        inject[ItemNameRepo].add(itemId, En.id, "ItemName01-EN")

        logoff(browser)
        login(browser, "user01")
        // Store owner cannot remove item.
        browser.goTo(
          controllers.routes.ItemMaintenance.removeItemName(itemId.id, Ja.id).url + "&lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        inject[ItemNameRepo].list(itemId).size === 2
        browser.webDriver.getTitle === Messages("commonTitle", Messages("company.name"))
      }
    }

    "Supplemental category maintenance." in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val site01 = inject[SiteRepo].createNew(Ja, "Store01")
        val cat01 = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val cat02 = inject[CategoryRepo].createNew(Map(Ja -> "Cat02"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        browser.goTo(
          controllers.routes.ItemMaintenance.startCreateNewItem().url + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#siteId").find("option").index(0).text() === "Store01"
        browser.find("#siteId option[value='" + site01.id.get + "']").click()
        browser.find("#categoryId").find("option").text() === "Cat01"
        browser.find("#taxId").find("option").text() === "外税"
        browser.find("#description").fill().`with`("Description01")

        browser.find("#itemName").fill().`with`("ItemName01")
        browser.find("#price").fill().`with`("1234")
        browser.find("#costPrice").fill().`with`("2345")
        browser.find("#createNewItemForm").find("input[type='submit']").click

        browser.find(".message").text() === Messages("itemIsCreated")

        browser.goTo(
          controllers.routes.ItemMaintenance.startChangeItem(1000).url.addParm("lang", lang.code).toString
        )

        inject[SupplementalCategoryRepo].byItem(ItemId(1000)).size === 0
        browser.find("#removeSupplementalCategoriesTable .removeSupplementalCategoryRow").size === 0

        browser.find("#addSupplementalCategoryForm option[value='1000']").size === 1
        browser.find("#addSupplementalCategoryForm option[value='1001']").size === 1

        browser.find("#addSupplementalCategoryForm option[value='1001']").click()
        browser.find("#addSupplementalCategoryForm input[type='submit']").click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find(".message").text === Messages("itemIsUpdated")
        browser.find("#removeSupplementalCategoriesTable .removeSupplementalCategoryRow").size === 1
        inject[SupplementalCategoryRepo].byItem(ItemId(1000)).size === 1
        browser.find("#removeSupplementalCategoriesTable .categoryName").text === "Cat02"
        browser.find("#removeSupplementalCategoriesTable .removeForm input[type='submit']").click()

        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.find(".message").text === Messages("itemIsUpdated")
        browser.find("#removeSupplementalCategoriesTable .removeSupplementalCategoryRow").size === 0
        inject[SupplementalCategoryRepo].byItem(ItemId(1000)).size === 0
      }
    }

    "Creating an item that is treated by two sites." in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val site01 = inject[SiteRepo].createNew(Ja, "Store01")
        val site02 = inject[SiteRepo].createNew(Ja, "Store02")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        browser.goTo(
          controllers.routes.ItemMaintenance.startCreateNewItem().url + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#siteId").find("option").index(0).text() === "Store01"
        browser.find("#siteId option[value='" + site01.id.get + "']").click()
        browser.find("#categoryId").find("option").text() === "Cat01"
        browser.find("#taxId").find("option").text() === "外税"
        browser.find("#description").fill().`with`("Description01")

        browser.find("#itemName").fill().`with`("ItemName01")
        browser.find("#price").fill().`with`("1234")
        browser.find("#costPrice").fill().`with`("2345")
        browser.find("#createNewItemForm").find("input[type='submit']").click

        browser.find(".message").text() === Messages("itemIsCreated")

        browser.goTo(
          controllers.routes.ItemMaintenance.startChangeItem(1000).url.addParm("lang", lang.code).toString
        )
      }
    }

    "Store owner cannot change item name if storeOwnerCanModifyAllItemProperties is false" in new WithBrowser(
      WebDriverFactory(CHROME), appl()
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user01 = createNormalUser("user01")
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val siteOwner = inject[SiteUserRepo].createNew(user01.id.get, site.id.get)
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))
        val item = inject[ItemRepo].createNew(cat)
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPriceHistory = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )

        logoff(browser)
        login(browser, "user01")

        browser.goTo(
          controllers.routes.ItemMaintenance.startChangeItem(1000).url.addParm("lang", lang.code).toString
        )
        browser.find("#itemNames_0_itemName").fill().`with`("かえで2");
        browser.find(".changeItemName .itemBody .itemName").click()

        // By default, store owner cannot change item name. The attempt will transit the page to top.
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()
        browser.webDriver.getTitle === Messages("commonTitle", Messages("company.name"))
      }
    }

    "Store owner can change item name if storeOwnerCanModifyAllItemProperties is true" in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ Map("storeOwnerCanModifyAllItemProperties" -> true))
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user01 = createNormalUser("user01")
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val siteOwner = inject[SiteUserRepo].createNew(user01.id.get, site.id.get)
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))
        val item = inject[ItemRepo].createNew(cat)
        val siteItem = inject[SiteItemRepo].createNew(site, item)
        val itemName = inject[ItemNameRepo].createNew(item, Map(Ja -> "かえで"))
        val itemDesc = inject[ItemDescriptionRepo].createNew(item, site, "かえで説明")
        val itemPrice = inject[ItemPriceRepo].createNew(item, site)
        val itemPriceHistory = inject[ItemPriceHistoryRepo].createNew(
          itemPrice, tax, currencyInfo.Jpy, BigDecimal(999), None, BigDecimal("888"), date("9999-12-31")
        )

        logoff(browser)
        login(browser, "user01")

        browser.goTo(
          controllers.routes.ItemMaintenance.startChangeItem(1000).url.addParm("lang", lang.code).toString
        )
        browser.find("#itemNames_0_itemName").fill().`with`("かえで2");
        browser.find(".changeItemName .itemBody .itemName").click()

        // By default, store owner cannot change item name. The attempt will transit the page to top.
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.webDriver.getTitle === Messages("commonTitle", Messages("changeItemTitle"))
        browser.find(".message").text === Messages("itemIsUpdated")
        browser.find("#itemNames_0_itemName").attribute("value") === "かえで2"
      }
    }

    "Create new item with hideNewlyCreatedItem = true." in new WithBrowser(
      WebDriverFactory(CHROME),
      appl(inMemoryDatabase() ++ Map("hideNewlyCreatedItem" -> true))
    ) {
      inject[Database].withConnection { implicit conn =>
        val currencyInfo = inject[CurrencyRegistry]
        val localeInfo = inject[LocaleInfoRepo]
        import localeInfo.{En, Ja}
        implicit val lang = Lang("ja")
        implicit val storeUserRepo = inject[StoreUserRepo]
        val Messages = inject[MessagesApi]
        implicit val mp: MessagesProvider = new MessagesImpl(lang, Messages)

        val user = loginWithTestUser(browser)
        val site = inject[SiteRepo].createNew(Ja, "Store01")
        val cat = inject[CategoryRepo].createNew(Map(Ja -> "Cat01"))
        val tax = inject[TaxRepo].createNew
        val taxName = inject[TaxNameRepo].createNew(tax, Ja, "外税")
        val taxHis = inject[TaxHistoryRepo].createNew(tax, TaxType.INNER_TAX, BigDecimal("5"), date("9999-12-31"))

        browser.goTo(
          controllers.routes.ItemMaintenance.startCreateNewItem().url + "?lang=" + lang.code
        )
        browser.await().atMost(5, TimeUnit.SECONDS).untilPage().isLoaded()

        browser.find("#siteId").find("option").text() === "Store01"
        browser.find("#categoryId").find("option").text() === "Cat01"
        browser.find("#taxId").find("option").text() === "外税"
        browser.find("#description").fill().`with`("Description01")

        browser.find("#itemName").fill().`with`("ItemName01")
        browser.find("#price").fill().`with`("1234")
        browser.find("#costPrice").fill().`with`("2345")
        browser.find("#createNewItemForm").find("input[type='submit']").click

        browser.find(".message").text() === Messages("itemIsCreated")

        val itemList = inject[ItemRepo].list(None, Ja, QueryString()).records

        itemList.size === 0
      }
    }
  }
}
