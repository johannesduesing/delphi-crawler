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

package de.upb.cs.swt.delphi.crawler.preprocessing

import de.upb.cs.swt.delphi.crawler.discovery.maven.MavenIdentifier
import org.joda.time.DateTime

case class MavenArtifact(identifier : MavenIdentifier, jarFile: JarFile, pomFile: PomFile,
                         publicationDate: Option[DateTime], metadata: Option[MavenArtifactMetadata])

case class MavenArtifactMetadata(name: String, description: String, issueManagement: Option[IssueManagementData])
case class IssueManagementData(system: String, url: String)

object MavenArtifact{
  def withMetadata(artifact: MavenArtifact, metadata: MavenArtifactMetadata): MavenArtifact = {
    MavenArtifact(artifact.identifier, artifact.jarFile, artifact.pomFile, artifact.publicationDate, Some(metadata))
  }
}
