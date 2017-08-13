import play.api.ApplicationLoader
import play.api.inject.guice.{GuiceApplicationLoader, GuiceApplicationBuilder}
import com.google.inject.AbstractModule
import java.time.Clock

import com.ruimo.recoeng.{RecoEngApi, RecoEngApiImpl}

class Module extends AbstractModule {
  override def configure() = {
  }
}

class CustomApplicationLoader extends GuiceApplicationLoader {
  override protected def builder(context: ApplicationLoader.Context): GuiceApplicationBuilder = {
    super.builder(context).disableCircularProxies(false)
  }
}
