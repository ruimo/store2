package models

import org.specs2.mutable._
import anorm._
import anorm.SqlParser
import play.api.test._
import play.api.test.Helpers._
import java.util.Locale

import com.ruimo.scoins.Scoping._
import helpers.InjectorSupport
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.db.Database

class CategorySpec extends Specification with InjectorSupport {
  "Category" should {
    "Can create new category." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      inject[Database].withConnection { implicit conn =>
        val localeInfo = inject[LocaleInfoRepo]
        val cat: Category = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )

        val root: Seq[Category] = inject[CategoryRepo].root
        root.size === 1
        root.head === cat

        inject[CategoryNameRepo].get(localeInfo.Ja, cat) === Some("植木")
        inject[CategoryNameRepo].get(localeInfo.En, cat) === Some("Plant")

        inject[CategoryPathRepo].parent(cat) === None
        inject[CategoryPathRepo].children(cat).size === 0

        inject[CategoryPathRepo].childrenNames(cat, localeInfo.Ja).size === 0
        inject[CategoryPathRepo].childrenNames(cat, localeInfo.En).size === 0
      }
    }

    "Can select single category." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      inject[Database].withConnection { implicit conn =>
        val localeInfo = inject[LocaleInfoRepo]
        val cat = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        inject[CategoryRepo].get(cat.id.get) === Some(cat)
        inject[CategoryRepo].get(cat.id.get+1000) === None
      }
    }


    "List category" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val localeInfo = inject[LocaleInfoRepo]
        val cat = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "うえき", localeInfo.En -> "Plant")
        )

        val cat2 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "はな", localeInfo.En -> "Flower")
        )

        val cat3 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "きゅうこん", localeInfo.En -> "Bulb")
        )

        val page1 = inject[CategoryRepo].list(0, 10, localeInfo.Ja)
        page1.page === 0
        page1.offset === 0
        page1.total === 3
        page1.list(0)._2.name === "うえき"
        page1.list(1)._2.name === "きゅうこん"
        page1.list(2)._2.name === "はな"
        page1.prev === None
        page1.next === None

        val page2 = inject[CategoryRepo].list(0, 2, localeInfo.Ja)
        page2.page === 0
        page2.offset === 0
        page2.total === 3
        page2.list(0)._2.name === "うえき"
        page2.list(1)._2.name === "きゅうこん"
        page2.prev === None
        page2.next === Some(1)

        val page3 = inject[CategoryRepo].list(1, 2, localeInfo.Ja)
        page3.page === 1
        page3.offset === 2
        page3.total === 3
        page3.list(0)._2.name === "はな"
        page3.prev === Some(0)
        page3.next === None
      }
    }

    "Parent child category" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val localeInfo = inject[LocaleInfoRepo]
        val parent = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
        val child = inject[CategoryRepo].createNew(
          parent,
          Map(localeInfo.Ja -> "果樹", localeInfo.En -> "Fruit Tree")
        )
        val child2 = inject[CategoryRepo].createNew(
          parent,
          Map(localeInfo.En -> "English Only Tree")
        )
        val root = inject[CategoryRepo].root
        root.size === 1
        root.head === parent

        inject[CategoryNameRepo].get(localeInfo.Ja, parent) === Some("植木")
        inject[CategoryNameRepo].get(localeInfo.En, parent) === Some("Plant")
        inject[CategoryNameRepo].get(localeInfo.Ja, child) === Some("果樹")
        inject[CategoryNameRepo].get(localeInfo.En, child) === Some("Fruit Tree")
        inject[CategoryNameRepo].get(localeInfo.Ja, child2) === None
        inject[CategoryNameRepo].get(localeInfo.En, child2) === Some("English Only Tree")

        inject[CategoryPathRepo].parent(parent) === None
        inject[CategoryPathRepo].children(parent).size === 2
        inject[CategoryPathRepo].parent(child) === parent.id
        inject[CategoryPathRepo].children(child).size === 0

        val jaChildNames = inject[CategoryPathRepo].childrenNames(parent, localeInfo.Ja)
        jaChildNames.size === 1
        val (jaCat, jaName) = jaChildNames.head
        jaCat === child.id.get
        jaName.locale === localeInfo.Ja
        jaName.name === "果樹"

        val enChildNames = inject[CategoryPathRepo].childrenNames(parent, localeInfo.En)
        enChildNames.size === 2
        val (enCat, enName) = enChildNames.head
        enCat === child.id.get
        enName.locale === localeInfo.En
        enName.name === "Fruit Tree"

        inject[CategoryPathRepo].childrenNames(child, localeInfo.Ja).size === 0
        inject[CategoryPathRepo].childrenNames(child, localeInfo.En).size === 0

        var pathList: Seq[(Long, CategoryName)] = inject[CategoryPathRepo].listNamesWithParent(localeInfo.Ja)
        pathList.contains((parent.id.get, CategoryName(localeInfo.Ja,parent.id.get,"植木"))) === true
        pathList.contains((parent.id.get, CategoryName(localeInfo.Ja,child.id.get,"果樹"))) === true
        pathList.contains((child.id.get, CategoryName(localeInfo.Ja,child.id.get,"果樹"))) === true
        pathList.contains((child2.id.get, CategoryName(localeInfo.En,child2.id.get,"English Only Tree"))) === true
      }
    }

    "be able to rename category name." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val localeInfo = inject[LocaleInfoRepo]
        val cat = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木", localeInfo.En -> "Plant")
        )
          
        inject[CategoryRepo].rename(cat, Map(localeInfo.Ja -> "うえき"))

        inject[CategoryNameRepo].get(localeInfo.Ja, cat) === Some("うえき")
        inject[CategoryNameRepo].get(localeInfo.En, cat) === Some("Plant")
      }
    }

    "be able to move category between different parent nodes" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      implicit val db = inject[Database]
      db.withConnection { implicit conn =>
        val parent = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "生物") )
        val child1 = inject[CategoryRepo].createNew(parent, Map(localeInfo.Ja -> "植物"))
        val child2 = inject[CategoryRepo].createNew(parent, Map(localeInfo.Ja -> "動物"))

        val child11 = inject[CategoryRepo].createNew(child1, Map(localeInfo.Ja -> "歩く木"))

        inject[CategoryRepo].move(child11, Some(child2))

        inject[CategoryPathRepo].parent(child11).get === child2.id.get

        inject[CategoryPathRepo].children(child1).size === 0

        inject[CategoryPathRepo].children(child2).size === 1

      }
    }
    
    "be able to move category under some parent node to root" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      implicit val db = inject[Database]

      db.withConnection { implicit conn =>
        val parent = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "生物") )
        val child1 = inject[CategoryRepo].createNew(parent, Map(localeInfo.Ja -> "植物"))
        val child2 = inject[CategoryRepo].createNew(parent, Map(localeInfo.Ja -> "動物"))

        val child11 = inject[CategoryRepo].createNew(child1, Map(localeInfo.Ja -> "歩く木"))

        inject[CategoryRepo].move(child11, None)

        inject[CategoryPathRepo].parent(parent) === None

        inject[CategoryPathRepo].parent(child11) === None

        inject[CategoryPathRepo].children(child1).size === 0

        inject[CategoryPathRepo].children(child2).size === 0
      }
    }


    "be able to move root category to under some parent" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      implicit val db = inject[Database]

      db.withConnection { implicit conn =>
        val parent = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "生物") )
        val child1 = inject[CategoryRepo].createNew(parent, Map(localeInfo.Ja -> "植物"))
        val child2 = inject[CategoryRepo].createNew(parent, Map(localeInfo.Ja -> "動物"))

        val child11 = inject[CategoryRepo].createNew(child1, Map(localeInfo.Ja -> "歩く木"))

        inject[CategoryRepo].move(child11, Some(child2))

        inject[CategoryPathRepo].parent(child11).get === child2.id.get

        inject[CategoryPathRepo].children(child1).size === 0

        inject[CategoryPathRepo].children(child2).size === 1
      }
    }
    
    "be able to move category under some parent node to root" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      implicit val db = inject[Database]

      db.withConnection { implicit conn =>
        val parent = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "生物") )
        val child1 = inject[CategoryRepo].createNew(parent, Map(localeInfo.Ja -> "植物"))
        val child2 = inject[CategoryRepo].createNew(parent, Map(localeInfo.Ja -> "動物"))

        val child11 = inject[CategoryRepo].createNew(child1, Map(localeInfo.Ja -> "歩く木"))

        val parent2 = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "石油"))

        inject[CategoryRepo].move(parent2, Some(parent))

        inject[CategoryPathRepo].parent(parent2) === parent.id

        inject[CategoryPathRepo].children(parent2).size === 0

        inject[CategoryPathRepo].children(parent).size === 3
      }
    }

    "reject when category and parent is same" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      implicit val db = inject[Database]

      db.withConnection { implicit conn =>
        val parent = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "生物") )
        val child1 = inject[CategoryRepo].createNew(parent, Map(localeInfo.Ja -> "植物"))
        val child2 = inject[CategoryRepo].createNew(parent, Map(localeInfo.Ja -> "動物"))

        val child11 = inject[CategoryRepo].createNew(child1, Map(localeInfo.Ja -> "歩く木"))

        inject[CategoryRepo].move(child1, Some(child1)) must throwA[Exception]
        
        inject[CategoryPathRepo].parent(child1) === parent.id

        inject[CategoryPathRepo].children(parent).size === 2
      }
    }

    "reject when parent is one of descendant of category" in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]
      implicit val db = inject[Database]

      db.withConnection { implicit conn =>
        val parent = inject[CategoryRepo].createNew(Map(localeInfo.Ja -> "生物") )
        val child1 = inject[CategoryRepo].createNew(parent, Map(localeInfo.Ja -> "植物"))
        val child2 = inject[CategoryRepo].createNew(parent, Map(localeInfo.Ja -> "動物"))

        val child11 = inject[CategoryRepo].createNew(child1, Map(localeInfo.Ja -> "歩く木"))

        inject[CategoryRepo].move(parent, Some(child1)) must throwA[Exception]

        inject[CategoryPathRepo].parent(child1) === parent.id

        inject[CategoryPathRepo].children(parent).size === 2
      }
    }

    "Be able to list categories when empty." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        doWith(
          inject[CategoryRepo].listWithName(
            locale = localeInfo.Ja,
            orderBy = OrderBy("category.category_id", Asc)
          )
        ) { records =>
          records.pageSize === 10
          records.pageCount === 0
          records.orderBy === OrderBy("category.category_id", Asc)
          records.records.size === 0
        }
      }
    }

    "Be able to list categories." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木1")
        )
        val cat2 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木2", localeInfo.En -> "Plant2")
        )
        val cat3 = inject[CategoryRepo].createNew(
          Map(localeInfo.En -> "Plant3")
        )

        doWith(
          inject[CategoryRepo].listWithName(
            locale = localeInfo.Ja,
            orderBy = OrderBy("category.category_id", Asc)
          )
        ) { records =>
          records.pageSize === 10
          records.pageCount === 1
          records.orderBy === OrderBy("category.category_id", Asc)

          doWith(records.records) { list =>
            list.size === 3

            list(0)._1 === cat1
            list(0)._2.get.name === "植木1"

            list(1)._1 === cat2
            list(1)._2.get.name === "植木2"

            list(2)._1 === cat3
            list(2)._2 === None
          }
        }

        doWith(
          inject[CategoryRepo].listWithName(
            locale = localeInfo.Ja,
            orderBy = OrderBy("category_name.category_name", Asc)
          )
        ) { records =>
          records.pageSize === 10
          records.pageCount === 1
          records.orderBy === OrderBy("category_name.category_name", Asc)

          doWith(records.records) { list =>
            list.size === 3

            list(0)._1 === cat1
            list(0)._2.get.name === "植木1"

            list(1)._1 === cat2
            list(1)._2.get.name === "植木2"

            list(2)._1 === cat3
            list(2)._2 === None
          }
        }

        doWith(
          inject[CategoryRepo].listWithName(
            locale = localeInfo.En,
            orderBy = OrderBy("category.category_id", Desc)
          )
        ) { records =>
          records.pageSize === 10
          records.pageCount === 1
          records.orderBy === OrderBy("category.category_id", Desc)

          doWith(records.records) { list =>
            list.size === 3

            list(0)._1 === cat3
            list(0)._2.get.name === "Plant3"

            list(1)._1 === cat2
            list(1)._2.get.name === "Plant2"

            list(2)._1 === cat1
            list(2)._2 === None
          }
        }

        doWith(
          inject[CategoryRepo].listWithName(
            locale = localeInfo.En,
            orderBy = OrderBy("category_name.category_name", Desc)
          )
        ) { records =>
          records.pageSize === 10
          records.pageCount === 1
          records.orderBy === OrderBy("category_name.category_name", Desc)

          doWith(records.records) { list =>
            list.size === 3

            list(0)._1 === cat3
            list(0)._2.get.name === "Plant3"

            list(1)._1 === cat2
            list(1)._2.get.name === "Plant2"

            list(2)._1 === cat1
            list(2)._2 === None
          }
        }
      }
    }

    "Be able to list category names." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        val cat1 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木1")
        )
        val cat2 = inject[CategoryRepo].createNew(
          Map(localeInfo.Ja -> "植木2", localeInfo.En -> "Plant2")
        )
        val cat3 = inject[CategoryRepo].createNew(Map())

        doWith(inject[CategoryNameRepo].all(cat1.id.get)) { map =>
          map.size === 1
          map(localeInfo.Ja) === CategoryName(localeInfo.Ja, cat1.id.get, "植木1")
        }
        doWith(inject[CategoryNameRepo].all(cat2.id.get)) { map =>
          map.size === 2
          map(localeInfo.Ja) === CategoryName(localeInfo.Ja, cat2.id.get, "植木2")
          map(localeInfo.En) === CategoryName(localeInfo.En, cat2.id.get, "Plant2")
        }
        inject[CategoryNameRepo].all(cat3.id.get).size === 0
      }
    }

    "Can add supplemental categories." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(
        inMemoryDatabase() + (
          "maxSupplementalCategoryCountPerItem" -> 10
        )
      ).build()
      val localeInfo = inject[LocaleInfoRepo]
      inject[Database].withConnection { implicit conn =>
        val categories = (1 to 20) map { idx =>
          inject[CategoryRepo].createNew(
            Map(localeInfo.Ja -> ("植木" + idx))
          )
        }
        val item1 = inject[ItemRepo].createNew(categories(0))
        val item2 = inject[ItemRepo].createNew(categories(1))
        doWith(inject[SupplementalCategoryRepo].byItem(item1.id.get)) { table =>
          table.size === 0
        }
        doWith(inject[SupplementalCategoryRepo].byItem(item2.id.get)) { table =>
          table.size === 0
        }

        (1 to 10) foreach { idx =>
          inject[SupplementalCategoryRepo].createNew(item1.id.get, categories(idx).id.get)
        }
        doWith(inject[SupplementalCategoryRepo].byItem(item1.id.get)) { table =>
          table.size === 10
          (0 to 9) foreach { idx =>
            table(idx) === SupplementalCategory(item1.id.get, categories(idx + 1).id.get)
          }
        }

        try {
          inject[SupplementalCategoryRepo].createNew(item1.id.get, categories(11).id.get)
          throw new AssertionError("Logic error")
        }
        catch {
          case e: MaxRowCountExceededException =>
          }

        // No exception should be thrown since item2 hash no supplemental categories.
        inject[SupplementalCategoryRepo].createNew(item2.id.get, categories(0).id.get) ===
        SupplementalCategory(item2.id.get, categories(0).id.get)

        // Remove
        inject[SupplementalCategoryRepo].remove(item1.id.get, categories(10).id.get) === 1
        inject[SupplementalCategoryRepo].byItem(item1.id.get).size === 9
      }
    }

    "Can get category name by category code." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()
      val localeInfo = inject[LocaleInfoRepo]

      inject[Database].withConnection { implicit conn =>
        inject[CategoryNameRepo].categoryNameByCode(Seq(), localeInfo.Ja).size === 0
      }
    }
  }
}
