package helpers

import javax.inject.{Inject, Singleton}

import play.api.Logger
import twitter4j.{OEmbedRequest, Status, Twitter, TwitterFactory}
import twitter4j.conf.ConfigurationBuilder

class TwitterAdapter(
  consumerKey: String, secretKey: String,
  accessToken: String, accessTokenSecret: String,
  cacheDurationInMilli: Long = 5 * 60 * 1000,
  cache: Cache
) {
  private val twitter = new TwitterFactory(
    new ConfigurationBuilder()
      .setOAuthConsumerKey(consumerKey)
      .setOAuthConsumerSecret(secretKey)
      .setOAuthAccessToken(accessToken)
      .setOAuthAccessTokenSecret(accessTokenSecret)
      .build()
  ).getInstance()

  def getLatestTweet(screenName: String): () => Option[Status] = cache.mayBeCached[Option[Status]](
    gen = () => {
      val z: java.util.Iterator[Status] = twitter.getUserTimeline(screenName).iterator
      if (z.hasNext) Some(z.next) else None
    },
    expirationInMillis = Some(cacheDurationInMilli)
  )

  def getLatestTweetEmbed(
    screenName: String, omitScript: Boolean = true, maxWidth: Option[Int] = None
  ): () => Option[(String, java.time.Instant)] = cache.mayBeCached[Option[(String, java.time.Instant)]](
    gen = () => getLatestTweet(screenName)().map { st =>
      val tweetId = st.getId
      val req = new OEmbedRequest(tweetId, "https://twitter.com/" + screenName + "/status/" + tweetId)
      req.setOmitScript(omitScript)
      req.setHideMedia(false)
      maxWidth.foreach { mw => req.setMaxWidth(mw) }
      twitter.getOEmbed(req).getHtml -> java.time.Instant.ofEpochMilli(st.getCreatedAt.getTime)
    },
    expirationInMillis = Some(cacheDurationInMilli)
  )
}

@Singleton
class TwitterAdapterRepo @Inject() (
  cache: Cache
) {
  val logger = Logger(getClass)
  def apply(
    consumerKey: String, secretKey: String,
    accessToken: String, accessTokenSecret: String,
    cacheDurationInMilli: Long = 5 * 60 * 1000
  ) = new TwitterAdapter(
    consumerKey, secretKey, accessToken, accessTokenSecret, cacheDurationInMilli, cache
  )
}
