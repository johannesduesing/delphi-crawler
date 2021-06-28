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
package de.upb.cs.swt.delphi

import akka.actor.ActorSystem
import akka.util.Timeout
import akka.pattern.ask
import de.upb.cs.swt.delphi.crawler.discovery.maven.MavenIdentifier
import de.upb.cs.swt.delphi.crawler.preprocessing.{MavenArtifact, MavenDownloadActor, MavenDownloadActorResponse}

import scala.concurrent.{Await, ExecutionContext}

package object crawler {

  def downloadArtifact(identifier: MavenIdentifier)
                              (implicit timeout: Timeout,
                               system: ActorSystem,
                               ec: ExecutionContext): MavenArtifact = {

    val downloadActor = system.actorOf(MavenDownloadActor.props)

    val f = downloadActor ? identifier

    val msg = Await.result(f, timeout.duration)

    assert(msg.isInstanceOf[MavenDownloadActorResponse])
    val response = msg.asInstanceOf[MavenDownloadActorResponse]
    assert(!response.jarDownloadFailed && !response.pomDownloadFailed && response.artifact.isDefined)
    response.artifact.get
  }

}
