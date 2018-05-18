package de.upb.cs.swt.delphi.crawler

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.Uri
import com.sksamuel.elastic4s.ElasticsearchClientUri
import com.sksamuel.elastic4s.http.HttpClient
import de.upb.cs.swt.delphi.crawler.discovery.maven.MavenCrawlActor
import de.upb.cs.swt.delphi.crawler.discovery.maven.MavenCrawlActor.StartDiscover
import de.upb.cs.swt.delphi.crawler.storage.ElasticActor

/**
  * Preliminary starter
  */
object Playground extends App {

  val system : ActorSystem = ActorSystem("trial")

  val client = HttpClient(ElasticsearchClientUri("localhost", 9200))
  val elastic : ActorRef = system.actorOf(ElasticActor.props(client), "elastic")

  val maven : ActorRef = system.actorOf(MavenCrawlActor.props(Uri("http://repo1.maven.org/maven2/de/tu-darmstadt/"), elastic), "maven")

  maven ! StartDiscover
}
