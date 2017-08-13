package controllers

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{WebDriverFactory, WithBrowser}
import play.api.test.Helpers._
import org.specs2.mutable._
import org.specs2.mock._
import play.api.mvc.Session
import models.{LoginSession, StoreUser, TestHelper, UserRole}
import org.mockito.Mockito.mock
import models.PagedRecords
import models.OrderBy
import models.Desc
import models.RecommendByAdmin
import com.ruimo.recoeng.json.SalesItem
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application => PlayApp}
import helpers.InjectorSupport

class RecommendationSpec extends Specification with InjectorSupport {
  "Recommendation controller" should {
    def appl: PlayApp = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

    "Can calculate recommendation by admin when item recommendation is empty." in new WithBrowser(
      WebDriverFactory(HTMLUNIT), appl
    ) {
      val expectedDetails = Map(
        (111L, 222L) -> mock(classOf[models.ItemDetail]),
        (112L, 223L) -> mock(classOf[models.ItemDetail]),
        (113L, 224L) -> mock(classOf[models.ItemDetail])
      )
      val details = inject[Recommendation].calcByAdmin(
        shoppingCartItems = List(),
        maxCount = 5,
        queryRecommendByAdmin = (recordCountToRetrieve: Int) => {
          recordCountToRetrieve === 5
          PagedRecords(
            currentPage = 0,
            pageSize = 10,
            pageCount = 1,
            orderBy = OrderBy("score", Desc),
            records = Seq(
              (RecommendByAdmin(Some(1L), 111L, 222L, 10, true), None, None),
              (RecommendByAdmin(Some(2L), 112L, 223L, 20, true), None, None),
              (RecommendByAdmin(Some(3L), 113L, 224L, 30, true), None, None)
            )
          )
        },
        (siteId: Long, itemId: Long) => expectedDetails(siteId -> itemId)
      )
      details.size === 3
      details(0) === expectedDetails(111L -> 222L)
      details(1) === expectedDetails(112L -> 223L)
      details(2) === expectedDetails(113L -> 224L)
    }

    "Can calculate recommendation by admin when there are some item recommendations." in new WithBrowser(
      WebDriverFactory(HTMLUNIT), appl
    ) {
      val expectedDetails = Map(
        (111L, 222L) -> mock(classOf[models.ItemDetail]),
        (112L, 223L) -> mock(classOf[models.ItemDetail]),
        (113L, 224L) -> mock(classOf[models.ItemDetail]),
        (111L, 777L) -> mock(classOf[models.ItemDetail]),
        (111L, 778L) -> mock(classOf[models.ItemDetail]),
        (111L, 779L) -> mock(classOf[models.ItemDetail])
      )
      val details = inject[Recommendation].calcByAdmin(
        shoppingCartItems = List(
          SalesItem("111", "777", 1),
          SalesItem("111", "778", 1),
          SalesItem("111", "779", 1)
        ),
        maxCount = 2,
        queryRecommendByAdmin = (recordCountToRetrieve: Int) => {
          recordCountToRetrieve === 5
          PagedRecords(
            currentPage = 0,
            pageSize = 10,
            pageCount = 1,
            orderBy = OrderBy("score", Desc),
            records = Seq(
              (RecommendByAdmin(Some(1L), 111L, 222L, 10, true), None, None),
              (RecommendByAdmin(Some(2L), 112L, 223L, 20, true), None, None),
              (RecommendByAdmin(Some(3L), 113L, 224L, 30, true), None, None)
            )
          )
        },
        (siteId: Long, itemId: Long) => expectedDetails(siteId -> itemId)
      )
      details.size === 2
      details(0) === expectedDetails(111L -> 222L)
      details(1) === expectedDetails(112L -> 223L)
    }
  }
}
