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

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import de.upb.cs.swt.delphi.crawler.discovery.maven.MavenIdentifier
import de.upb.cs.swt.delphi.crawler.downloadArtifact
import de.upb.cs.swt.delphi.crawler.preprocessing.MavenArtifact
import org.opalj.br.analyses.Project
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatest.{Matchers, WordSpecLike}

import java.net.URL
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Success, Try}

class OpalProjectInitialisingActorTest extends TestKit(ActorSystem("OPALInitActor"))
  with Matchers
  with WordSpecLike
  with ImplicitSender{

  "The initialising actor" must {
    "successfully initialize OPAL projects" in {
      implicit val timeout: Timeout = Timeout(15.minutes)
      implicit val ec: ExecutionContext = system.dispatcher

      val mavenIdentifier =
        new MavenIdentifier("https://repo1.maven.org/maven2/", "org.neo4j", "neo4j-kernel", "4.3.0")

      val mavenArtifact = downloadArtifact(mavenIdentifier)

      val initActor = system.actorOf(OpalProjectInitialisingActor.props())

      val responseFuture = initActor ? mavenArtifact
      val result = Await.result(responseFuture, timeout.duration)

      assert(result.isInstanceOf[Success[(MavenArtifact, Project[URL])]])
      val resultTuple = result.asInstanceOf[Success[(MavenArtifact, Project[URL])]].get

      assert(mavenArtifact.equals(resultTuple._1))

      assert(resultTuple._2.statistics("ProjectPackages") == 83)
    }
  }

}
