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
import de.upb.cs.swt.delphi.crawler.preprocessing.{ArtifactDependency, MavenArtifact}
import de.upb.cs.swt.delphi.crawler.processing.CallGraphActor
import de.upb.cs.swt.delphi.crawler.tools.ActorStreamIntegrationSignals.Ack
import org.neo4j.driver.Values.parameters
import org.neo4j.driver.{AuthTokens, Driver, GraphDatabase, Session}
import org.opalj.br.DeclaredMethod
import org.opalj.br.analyses.Project
import org.opalj.tac.cg.CallGraph

import java.net.URL
import scala.collection.mutable
import scala.util.Success

class CallGraphStorageActor(neo4jDriver: Driver) extends Actor with ActorLogging {

  val neo4jExternalMethodTemplateString: String =
    "CREATE (e: ExternalMethod :Method {UniqueName: $e_uid, FullName: $e_full, SimpleName: $e_name, " +
      "ArtifactName: $art})"

  val neo4jLibraryMethodTemplateString: String =
    "CREATE (m: LibraryMethod :Method {UniqueName: $l_uid, FullName: $l_full, SimpleName: $l_name, ArtifactName: $art, " +
      "IsPublic: $l_pub, IsPrivate: $l_pri, IsStatic: $l_sta})"


  override def receive: Receive = {
    case (artifact: MavenArtifact, cg: CallGraph, p:Project[URL]) =>
      log.info(s"Storing callgraph for artifact ${artifact.identifier.toString}...")
      val theSession: Session = neo4jDriver.session()
      val ident = artifact.identifier.toString

      createArtifactNode(artifact)(theSession)

      cg.reachableMethods().foreach{ m =>
        if(CallGraphActor.isExternalMethod(cg, m, p)) {
          mergeExternalMethod(m, ident)(theSession)
        } else {
          mergeLibraryMethod(m, ident)(theSession)
        }
        connectMethodToArtifact(ident, m.toJava)(theSession)
      }

      artifact.metadata.map(_.dependencies).getOrElse(Set.empty).foreach{ dependency =>
        connectArtifactToDependency(ident, dependency)(theSession)
      }

      log.info(s"Done storing methods. Storing relations...")

      cg.reachableMethods().foreach{ method =>
        cg.calleesOf(method).foreach{ case (_, targets) =>
          targets.foreach{ target =>
            createInvokeRelation(s"$ident:${target.toJava}", s"$ident:${method.toJava}")(theSession)
          }
        }
      }

      log.info(s"Done storing callgraph.")

      theSession.close()

      sender() ! Success(None)
  }

  private def connectArtifactToDependency(artifactGAV: String, dependency: ArtifactDependency)(implicit session: Session) = {
    session.run("MERGE (a: MavenDependency {GAV: $dgav})",
      parameters("dgav", dependency.identifier.toString))

    session.run("MATCH (a: MavenArtifact {GAV: $gav}) MATCH (d: MavenDependency {GAV: $dgav}) CREATE (a)-[:DEPENDS_ON {scope: $s}]->(d)", parameters(
      "gav", artifactGAV,
      "s", dependency.scope.getOrElse("default"),
      "dgav", dependency.identifier.toString
    ))
  }

  private def connectMethodToArtifact(artifactGAV: String, methodName: String)(implicit session: Session) = {
    session.run("MATCH (a: MavenArtifact {GAV: $gav}) MATCH (m: Method {UniqueName: $uid}) CREATE (a)-[:HAS_METHOD]->(m)", parameters(
      "gav", artifactGAV,
      "uid", s"$artifactGAV:$methodName"
    ))
  }

  private def createArtifactNode(artifact: MavenArtifact)(implicit session: Session) = {
    session.run("CREATE (a: MavenArtifact {GAV: $uid, groupId: $gid, artifactId: $aid, version: $v})", parameters(
      "uid", artifact.identifier.toString,
      "gid", artifact.identifier.groupId,
      "aid", artifact.identifier.artifactId,
      "v", artifact.identifier.version
    ))
  }

  private def createInvokeRelation(externalMethodUid: String, entryMethodUid: String) (implicit session: Session) = {
    session.run("MATCH (e: Method {UniqueName: $euid}) MATCH (l: Method {UniqueName: $luid}) MERGE (l)-[:INVOKES]->(e)", parameters(
      "euid", externalMethodUid,
      "luid", entryMethodUid
    ))
  }

  private def mergeExternalMethod(method: DeclaredMethod, artifactIdent: String)(implicit session: Session): Unit = {
    session.run(neo4jExternalMethodTemplateString, parameters(
      "e_uid", s"$artifactIdent:${method.toJava}",
      "e_full", method.toJava,
      "e_name", method.name,
      "art", artifactIdent
    ))
  }


  private def mergeLibraryMethod(method: DeclaredMethod, artifactIdent: String)(implicit session: Session): Unit = {
    session.run(neo4jLibraryMethodTemplateString, parameters(
      "l_uid", s"$artifactIdent:${method.toJava}",
      "l_full", method.toJava,
      "l_name", method.name,
      "art", artifactIdent,
      "l_pub", toJavaBool(method.definedMethod.isPublic),
      "l_pri", toJavaBool(method.definedMethod.isPrivate),
      "l_sta", toJavaBool(method.definedMethod.isStatic)
    ))
  }

  private def toJavaBool(b: Boolean): java.lang.Boolean = if(b) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE
  private def toJavaInt(i: Int): Integer = new Integer(i)

}

object  CallGraphStorageActor {
  def props(driver: Driver): Props = Props(new CallGraphStorageActor(driver))

  def initDriver(configuration: Configuration): Driver =
    GraphDatabase.driver(configuration.graphDatabaseUrl, AuthTokens.basic(configuration.graphDatabaseUser,configuration.graphDatabasePassword))
}
