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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import de.upb.cs.swt.delphi.crawler.preprocessing.MavenArtifact
import de.upb.cs.swt.delphi.crawler.tools.ActorStreamIntegrationSignals.{Ack, StreamCompleted, StreamFailure, StreamInitialized}
import org.opalj.br.{DeclaredMethod, VirtualDeclaredMethod}
import org.opalj.br.analyses.Project
import org.opalj.tac.cg.{CallGraph, XTACallGraphKey}

import java.net.URL
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}


class CallGraphActor(storageActor: ActorRef) extends Actor with ActorLogging with OPALFunctionality {

  override def receive: Receive = {
    case (artifact: MavenArtifact, libProject: Project[URL]) =>
      Try{
        log.info(s"Generating call graph for artifact: ${artifact.identifier}..")
        libProject.get(XTACallGraphKey)
      } match {
        case Success(theCG) =>
          log.info(s"Successfully processed callgraph for artifact ${artifact.identifier}")
          implicit val timeout: Timeout = Timeout(15.minutes)
          Await.result(storageActor ? (artifact, theCG, libProject), timeout.duration) match {
            case Success(storageResponse) =>
              log.info(s"Successfully stored CG information for ${artifact.identifier}")
              sender() ! Ack //Success((artifact, libProject))
            case Failure(ex) =>
              log.error("Invalid response from storage actor", ex)
              sender() ! Ack //Failure(ex)
            case _ =>
              log.error("Invalid response from storage actor")
              sender() ! Ack //Failure(new Exception())
          }
        case Failure(ex) =>
          log.error(s"Failed to generate call graph for artifact ${artifact.identifier}", ex)
          sender() ! Ack //Failure(ex)
      }

    case StreamInitialized =>
      log.info(s"Stream initialized!")
      sender() ! Ack
    case StreamCompleted =>
      log.info(s"Stream completed!")
    case StreamFailure(ex) =>
      log.error(ex, s"Stream failed!")
    case a@_ =>
      log.warning("Unexpected input " + a)
  }

}

object CallGraphActor {
  def props(storageActor: ActorRef): Props = Props(new CallGraphActor(storageActor))

  def isExternalMethod(callgraph: CallGraph,
                               method: DeclaredMethod,
                               project: Project[URL]): Boolean = {
    callgraph.calleesOf(method).isEmpty && (method.isInstanceOf[VirtualDeclaredMethod] ||
      !project.allProjectClassFiles.exists(_.thisType.equals(method.declaringClassType)))
  }
}
