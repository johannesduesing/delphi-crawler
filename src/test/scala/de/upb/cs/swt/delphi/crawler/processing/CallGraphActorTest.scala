// Copyright (C) 2018 The Delphi Team.
// See the LICENCE file distributed with this work for additional
// information regarding copyright ownership.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package de.upb.cs.swt.delphi.crawler.processing

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import org.scalatest.{Matchers, WordSpecLike}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import de.upb.cs.swt.delphi.crawler.discovery.maven.MavenIdentifier
import de.upb.cs.swt.delphi.crawler.preprocessing.{MavenArtifact, MavenDownloadActor, MavenDownloadActorResponse}
import de.upb.cs.swt.delphi.crawler.storage.CallGraphStorageActor
import org.neo4j.driver.{AuthTokens, Driver, GraphDatabase}
import org.opalj.br.DeclaredMethod
import org.opalj.br.analyses.Project
import org.opalj.tac.cg.CallGraph
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

import java.net.URL
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Success

class CallGraphActorTest extends TestKit(ActorSystem("CGActor"))
  with Matchers
  with WordSpecLike
  with ImplicitSender
  with OPALFunctionality {

  "The CallGraph Actor" must {
    "successfully generate callgraphs" in {
      implicit val timeout: Timeout = Timeout(15.minutes)
      implicit val ec: ExecutionContext = system.dispatcher

      val mavenIdentifier =
        new MavenIdentifier("https://repo1.maven.org/maven2/", "org.neo4j", "neo4j-kernel", "4.3.0")

      val mavenArtifact = downloadArtifact(mavenIdentifier)
      val storageDummy = dummyStorageActor{ (artifact, cg) =>
        assert(artifact.identifier.equals(mavenIdentifier))
        assert(cg.reachableMethods().nonEmpty)
        Success(None)
      }
      val cgActor = system.actorOf(CallGraphActor.props(storageDummy))

      val project = reifyProject(mavenArtifact, true)

      val cgFuture = cgActor ? (mavenArtifact,  project)
      val result = Await.result(cgFuture, timeout.duration)

      assert(result.isInstanceOf[Success[_]])
      val content = result.asInstanceOf[Success[MavenArtifact]]
    }

  }

  private def dummyStorageActor[T](implicit handler: (MavenArtifact, CallGraph) => T): ActorRef = {
    system.actorOf(Props(new Actor {
      override def receive: Receive = {
        case (a: MavenArtifact, s: CallGraph, _: Project[URL]) =>
          sender() ! handler(a, s)
        case _ =>
          ???
      }
    }))
  }

  private def downloadArtifact(identifier: MavenIdentifier)
                              (implicit timeout: Timeout, ec: ExecutionContext): MavenArtifact = {

    val downloadActor = system.actorOf(MavenDownloadActor.props)

    val f = downloadActor ? identifier

    val msg = Await.result(f, timeout.duration)

    assert(msg.isInstanceOf[MavenDownloadActorResponse])
    val response = msg.asInstanceOf[MavenDownloadActorResponse]
    assert(!response.jarDownloadFailed && !response.pomDownloadFailed && response.artifact.isDefined)
    response.artifact.get
  }
}
