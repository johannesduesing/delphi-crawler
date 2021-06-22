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
package de.upb.cs.swt.delphi.crawler.storage

import akka.actor.{Actor, ActorLogging, Props}
import de.upb.cs.swt.delphi.crawler.Configuration
import de.upb.cs.swt.delphi.crawler.preprocessing.MavenArtifact
import de.upb.cs.swt.delphi.crawler.processing.CallGraphActor
import org.neo4j.driver.Values.parameters
import org.neo4j.driver.{AuthTokens, Driver, GraphDatabase, Session}
import org.opalj.br.DeclaredMethod
import org.opalj.br.analyses.Project
import org.opalj.tac.cg.CallGraph

import java.net.URL
import scala.collection.mutable
import scala.util.Success

class CallGraphStorageActor(neo4jDriver: Driver) extends Actor with ActorLogging {

  neo4jDriver.verifyConnectivity()

  val neo4jExternalMethodTemplateString: String =
    "CREATE (e: ExternalMethod :Method {UniqueName: $e_uid, SimpleName: $e_name, " +
      "ArtifactName: $art, DeclaringType: $e_type, Package: $e_package})"

  val neo4jLibraryMethodTemplateString: String =
    "CREATE (m: LibraryMethod :Method {UniqueName: $l_uid, SimpleName: $l_name, ArtifactName: $art, DeclaringType: $l_type," +
      " Package: $l_package, IsPublic: $l_pub, IsPrivate: $l_pri, IsStatic: $l_sta})"

  val nodesCreated: mutable.HashSet[String] = mutable.HashSet()


  override def receive: Receive = {
    case (artifact: MavenArtifact, cg: CallGraph, p:Project[URL]) =>
      log.info(s"Storing callgraph for artifact ${artifact.identifier.toString}...")
      val theSession: Session = neo4jDriver.session()

      cg.reachableMethods().foreach{ m =>
        if(CallGraphActor.isExternalMethod(cg, m, p)) {
          mergeExternalMethod(m, artifact.identifier.toString)(theSession)
        } else {
          mergeLibraryMethod(m, artifact.identifier.toString)(theSession)
        }
      }

      log.info(s"Done storing methods. Storing relations...")

      cg.reachableMethods().foreach{ method =>
        cg.calleesOf(method).foreach{ case (_, targets) =>
          targets.foreach{ target =>
            createInvokeRelation(target.toJava, method.toJava)(theSession)
          }
        }
      }

      log.info(s"Done storing callgraph.")

      theSession.close()

      sender() ! Success(None)
  }

  private def createInvokeRelation(externalMethodUid: String, entryMethodUid: String) (implicit session: Session) = {
    session.run("MATCH (e: Method {UniqueName: $euid}) MATCH (l: Method {UniqueName: $luid}) MERGE (l)-[:INVOKES]->(e)", parameters(
      "euid", externalMethodUid,
      "luid", entryMethodUid
    ))
  }

  private def mergeExternalMethod(method: DeclaredMethod, artifactIdent: String)(implicit session: Session): Unit = {
    session.run(neo4jExternalMethodTemplateString, parameters(
      "e_uid", method.toJava,
      "e_name", method.name,
      "art", artifactIdent,
      "e_type", harmonize(method.declaringClassType.fqn),
      "e_package", harmonize(method.declaringClassType.packageName)
    ))
  }


  private def mergeLibraryMethod(method: DeclaredMethod, artifactIdent: String)(implicit session: Session): Unit = {
    session.run(neo4jLibraryMethodTemplateString, parameters(
      "l_uid", method.toJava,
      "l_name", method.name,
      "art", artifactIdent,
      "l_type", harmonize(method.declaringClassType.fqn),
      "l_package", harmonize(method.declaringClassType.packageName),
      "l_pub", toJavaBool(method.definedMethod.isPublic),
      "l_pri", toJavaBool(method.definedMethod.isPrivate),
      "l_sta", toJavaBool(method.definedMethod.isStatic)
    ))
  }

  private def harmonize(value: String) = value.replace("/", ".")
  def toJavaBool(b: Boolean): java.lang.Boolean = if(b) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE
  def toJavaInt(i: Int): Integer = new Integer(i)

}

object  CallGraphStorageActor {
  def props(driver: Driver): Props = Props(new CallGraphStorageActor(driver))

  def initDriver(configuration: Configuration): Driver =
    GraphDatabase.driver("", AuthTokens.basic(???,???))
}
