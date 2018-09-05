/*
 * Copyright 2017 Lightbend, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lightbend.rp.akkaclusterbootstrap

import akka.actor.ExtendedActorSystem
import akka.cluster.{ Cluster, MemberStatus }
import com.lightbend.rp.status.{ HealthCheck, ReadinessCheck }
import scala.concurrent.{ ExecutionContext, Future }

class ClusterStatusCheck extends ReadinessCheck with HealthCheck {
  def healthy(actorSystem: ExtendedActorSystem)(implicit ec: ExecutionContext): Future[Boolean] =
    status(actorSystem) map {
      case MemberStatus.Joining => true
      case MemberStatus.WeaklyUp => true
      case MemberStatus.Up => true
      case MemberStatus.Leaving => true
      case MemberStatus.Exiting => true
      case MemberStatus.Down => false
      case MemberStatus.Removed => false
    }

  def ready(actorSystem: ExtendedActorSystem)(implicit ec: ExecutionContext): Future[Boolean] =
    status(actorSystem) map {
      case MemberStatus.Joining => false
      case MemberStatus.WeaklyUp => true
      case MemberStatus.Up => true
      case MemberStatus.Leaving => false
      case MemberStatus.Exiting => false
      case MemberStatus.Down => false
      case MemberStatus.Removed => false
    }

  private def status(actorSystem: ExtendedActorSystem)(implicit ec: ExecutionContext): Future[MemberStatus] = {
    val cluster = Cluster(actorSystem)
    val selfNow = cluster.selfMember
    Future.successful(selfNow.status)
  }
}
