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

import akka.actor.{Actor, ActorLogging, Props}
import de.upb.cs.swt.delphi.crawler.preprocessing.MavenArtifact

import scala.util.Try

/**
  * Implementation of an Actor that initializes an OPAL Project[URL] instance for a given MavenArtifact. This
  * will consume and close the JarFile inputstream associated with the MavenArtifact. The Actor returns a tuple
  * of (MavenArtifact, Project[URL]) for further processing.
  *
  * Projects are initialized as "Libraries", meaning all public methods will be considered as entry points.
  *
  * @author Johannes Düsing
  */
class OpalProjectInitialisingActor extends Actor with ActorLogging with OPALFunctionality {

  override def receive: Receive = {
    case artifact: MavenArtifact =>
      val (projectTry, execTime) = execTimeMillis(() => Try{reifyProject(artifact, loadAsLibraryProject = true)})
      log.info(s"Initialization of OPAL project took $execTime ms")
      sender() ! projectTry.map(p => (artifact, p))
  }

  private def execTimeMillis[T](operation: () => T): (T, Long) = {
    val startTime = System.currentTimeMillis()
    val result = operation.apply()

    (result, System.currentTimeMillis() - startTime)
  }
}

object OpalProjectInitialisingActor {
  def props(): Props = Props(new OpalProjectInitialisingActor())
}
