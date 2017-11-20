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

import java.util.UUID

import akka.actor._
import akka.cluster.MemberStatus
import akka.cluster.ClusterEvent._
import akka.pattern.pipe
import com.lightbend.rp.akkaclusterbootstrap.registrar._

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.util.Success

object ClusterManager {
  def props(id: UUID, client: Client, cluster: AkkaCluster): Props =
    Props(new ClusterManager(id, client, cluster))

  case object Refresh
  case object RefreshTimeout

  sealed trait State
  sealed trait Data

  case object Registering extends State
  case object RegisteringBackoff extends State

  case class RegisteringData(attempts: Int) extends Data

  case object Joining extends State

  case class JoiningData(record: Record, membersLeft: Seq[String], shouldStart: Boolean) extends Data

  case object Refreshing extends State

  case class RefreshingData(record: Record) extends Data

  case object NoData extends Data
}

import ClusterManager._

/**
 * Uses the registrar to form the cluster.
 */
class ClusterManager(id: UUID, client: Client, cluster: AkkaCluster) extends Actor with ActorSettings with FSM[State, Data] {
  import context.dispatcher

  startWith(Registering, RegisteringData(1))

  private val name = cluster.selfAddress.toString

  private val topic = settings.registrarTopicPattern.format(cluster.systemName)

  cluster.subscribe(self, InitialStateAsSnapshot, classOf[MemberJoined], classOf[MemberUp])

  /**
   * Attempt to remove id from registrar - note this is named attempt because there's no way to guarantee it. This
   * is effectively an optimization that would stop registrar from giving out old addresses when a node shuts down.
   */
  private def attemptRemove(): Unit =
    client.remove(topic, id, name).onComplete {
      case Success(true) =>
        log.info("Registration was removed from registrar")
      case Success(false) =>
        log.warning("Registration removal failed; this is unexpected")
      case _ =>
      // we don't need to log. Removing is an optimization and it could fail.
    }

  /**
   * Sets the refresh timer. This is called after each refresh because setting a timer cancels the existing, and
   * the refresh schedule is dictated by the response from Registrar -- it could have changed.
   */
  private def setRefreshTimer(timeMs: Long): Unit =
    setTimer("refresh", Refresh, timeMs.milliseconds, repeat = true)

  /**
   * Sets the refresh timer. This is called after each transition to Refreshing. We honor the advisory
   * setting from registrar.
   */
  private def setRefreshTimeoutTimer(timeMs: Long): Unit =
    setTimer("refreshTimeout", RefreshTimeout, timeMs.milliseconds, repeat = false)

  onTransition {
    case _ -> Registering =>
      pipe(client.register(topic, id, name)) to self

    case _ -> Joining =>
      nextStateData match {
        case JoiningData(_, membersLeft, shouldStart) if membersLeft.nonEmpty =>
          val addressToJoin =
            if (shouldStart) {
              log.info(s"Attempting to start cluster by joining self...")

              cluster.selfAddress
            } else {
              log.info(s"Attempting to join ${membersLeft.head}...")

              AddressFromURIString(membersLeft.head)
            }

          cluster.join(addressToJoin)
        case _ =>
          log.error("Failed to form a cluster; no members left to try, stopping...")
          stop()
      }
  }

  onTermination {
    case StopEvent(_, _, JoiningData(record, _, _)) =>
      cluster.unsubscribe(self)

      attemptRemove()

    case StopEvent(_, _, RefreshingData(record)) =>
      cluster.unsubscribe(self)

      attemptRemove()

    case _ =>
      cluster.unsubscribe(self)
  }

  when(Registering, settings.registrarRegisterTimeout) {
    case Event(Status.Failure(f), RegisteringData(nrOfAttempts)) =>
      if (nrOfAttempts < settings.registrarRegistrationAttempts) {
        logErrorThrowable(s"Failed to register with registrar (attempt #$nrOfAttempts), waiting...", f)

        goto(RegisteringBackoff) using RegisteringData(nrOfAttempts + 1)
      } else {
        logErrorThrowable(s"Failed to register with registrar (attempt #$nrOfAttempts), stopping...", f)

        stop()
      }

    case Event(Some(r: Record), _) =>
      val (members, shouldStart) =
        if (r.members.length > 1)
          r.members.filterNot(_ == name) -> false
        else
          r.members -> r.members.contains(name)

      goto(Joining) using JoiningData(
        record = r,
        membersLeft = members.take(settings.registrarJoinTries),
        shouldStart = shouldStart)

    case Event(None, _) =>
      // We're in a holding period, wait before retrying but don't count as failed attempt

      log.info("Failed to register with registrar because it is currently in a holding period (default 60s), waiting...")

      goto(RegisteringBackoff)

    case Event(StateTimeout, RegisteringData(nrOfAttempts)) =>
      if (nrOfAttempts < settings.registrarRegistrationAttempts) {
        log.error(s"Failed to register with registrar (attempt #$nrOfAttempts) due to timeout, waiting...")

        goto(RegisteringBackoff) using RegisteringData(nrOfAttempts + 1)
      } else {
        log.error(s"Failed to register with registrar (attempt #$nrOfAttempts) due to timeout, stopping...")

        stop()
      }
  }

  when(RegisteringBackoff, settings.registrarRegistrationFailTimeout) {
    case Event(StateTimeout, _) =>
      goto(Registering)
  }

  when(Joining, settings.registrarJoinTimeout) {
    case Event(CurrentClusterState(members, _, _, _, _), JoiningData(record, _, _)) if members.exists(m => m.status == MemberStatus.Up && m.address == cluster.selfAddress) =>
      setRefreshTimer(record.refreshInterval)
      setRefreshTimeoutTimer(record.expireAfter)

      goto(Refreshing) using RefreshingData(record)

    case Event(MemberUp(member), JoiningData(record, _, _)) if member.address == cluster.selfAddress =>
      setRefreshTimer(record.refreshInterval)
      setRefreshTimeoutTimer(record.expireAfter)

      goto(Refreshing) using RefreshingData(record)

    case Event(StateTimeout, JoiningData(record, membersLeft, _)) =>
      goto(Joining) using JoiningData(record, membersLeft.drop(1), shouldStart = false)
  }

  when(Refreshing) {
    case Event(Refresh, RefreshingData(r)) =>
      pipe(client.refresh(topic, r.id, r.name)) to self

      stay()

    case Event(RefreshResult(accepted, _, refreshInterval, expireAfter), RefreshingData(r)) if accepted.contains(Registration(r.id, r.name)) =>
      log.debug(s"Refreshed with registrar ($accepted)")

      setRefreshTimer(refreshInterval)
      setRefreshTimeoutTimer(expireAfter)

      stay()

    case Event(RefreshResult(_, rejected, _, _), RefreshingData(r)) if rejected.contains(Registration(r.id, r.name)) =>
      log.error(s"Refresh rejected by registrar ($rejected), stopping...")

      stop()

    case Event(RefreshResult(_, rejected, _, _), RefreshingData(r)) =>
      log.warning("Refresh didn't accept nor reject, waiting...")

      stay()

    case Event(RefreshTimeout, _) =>
      log.error(s"Refresh cycle timed out, stopping...")

      stop() using NoData

    case Event(Status.Failure(f), _) =>
      logErrorThrowable("Failed to refresh with registrar, waiting...", f)

      stay()
  }

  private def logErrorThrowable(message: String, t: Throwable): Unit = {
    if (log.isDebugEnabled) {
      log.error(t, message)
    } else {
      log.error(s"$message (${t.getMessage})")
    }
  }

  initialize()
}
