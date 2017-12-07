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

package com.lightbend.rp.servicediscovery.scaladsl

import akka.actor._
import akka.io.AsyncDnsResolver.SrvResolved
import akka.io.Dns.Resolved
import akka.io.{ Dns, IO }
import akka.pattern.ask
import com.lightbend.rp.common._
import com.lightbend.rp.servicediscovery.scaladsl.ServiceLocatorLike.{ AddressSelection, AddressSelectionRandom }
import java.net.URI
import java.util.concurrent.ThreadLocalRandom

import ru.smslv.akka.dns.raw.SRVRecord

import scala.collection.immutable.Seq
import scala.concurrent.Future

case class ServiceLocator(as: ActorSystem) {
  def lookupOne(namespace: String, name: String, endpoint: String): Future[Option[Service]] =
    ServiceLocator.lookupOne(namespace, name, endpoint)(as)

  def lookupOne(namespace: String, name: String): Future[Option[Service]] =
    ServiceLocator.lookupOne(namespace, name)(as)

  def lookupOne(name: String): Future[Option[Service]] =
    ServiceLocator.lookupOne(name)(as)

  def lookupOne(namespace: String, name: String, endpoint: String, addressSelection: AddressSelection): Future[Option[Service]] =
    ServiceLocator.lookupOne(namespace, name, endpoint, addressSelection)(as)

  def lookupOne(namespace: String, name: String, addressSelection: AddressSelection): Future[Option[Service]] =
    ServiceLocator.lookupOne(namespace, name, addressSelection)(as)

  def lookupOne(name: String, addressSelection: AddressSelection): Future[Option[Service]] =
    ServiceLocator.lookupOne(name, addressSelection)(as)

  def lookup(namespace: String, name: String, endpoint: String): Future[Seq[Service]] =
    ServiceLocator.lookup(namespace, name, endpoint)(as)

  def lookup(name: String, endpoint: String): Future[Seq[Service]] =
    ServiceLocator.lookup(name, endpoint)(as)

  def lookup(name: String): Future[Seq[Service]] =
    ServiceLocator.lookup(name)(as)
}

object ServiceLocator extends ServiceLocatorLike {
  def dnsResolver(implicit as: ActorSystem): ActorRef = IO(Dns)

  def env: Map[String, String] = sys.env

  def targetRuntime: Option[Platform] = Platform.active

}

object ServiceLocatorLike {
  type AddressSelection = Seq[Service] => Option[Service]

  val AddressSelectionRandom: AddressSelection =
    services => if (services.isEmpty) None else Some(services(ThreadLocalRandom.current.nextInt(services.length)))

  val AddressSelectionFirst: AddressSelection = services =>
    services.headOption
}

trait ServiceLocatorLike {
  val DnsCharacters: Set[Char] = Set('_', '.')

  val ValidEndpointServiceChars: Set[Char] =
    (('0' to '9') ++ ('A' to 'Z') ++ ('a' to 'z') ++ Seq('-')).toSet

  def targetRuntime: Option[Platform]
  def dnsResolver(implicit as: ActorSystem): ActorRef
  def env: Map[String, String]

  def translateName(namespace: Option[String], name: String, endpoint: String): String =
    targetRuntime match {
      case Some(Kubernetes) =>
        if (name.exists(DnsCharacters.contains) && endpoint.isEmpty) {
          name
        } else {
          val serviceNamespace = namespace.orElse(namespaceFromEnv()).getOrElse(kubernetes.DefaultNamespace)
          val serviceName = endpointServiceName(name)
          val endpointName = endpointServiceName(endpoint)

          // @TODO hardcoded _tcp
          // @TODO if endpoint is empty, consider this better - can you even lookup if the endpoint is empty???
          s"_$endpointName._tcp.$serviceName.$serviceNamespace.svc.cluster.local"
        }

      case None =>
        name
    }

  def translateProtocol(endpoint: String, srvName: String): Option[String] =
    targetRuntime match {
      case Some(Kubernetes) =>
        Option(srvName.split('.'))
          .filter(_.length >= 2)
          .map(parts => normalizeProtocol(parts(1).dropWhile(_ == '_'), endpoint))

      case _ => Option.empty
    }

  def translateResolved(protocol: Option[String], srvRecord: SRVRecord, addressARecord: Dns.Resolved): Seq[Service] =
    targetRuntime match {
      case Some(Kubernetes) =>
        val uris =
          addressARecord.ipv4.map(v4 => new URI(protocol.orNull, null, v4.getHostAddress, srvRecord.port, null, null, null)) ++
            addressARecord.ipv6.map(v6 => new URI(protocol.orNull, null, v6.getHostAddress, srvRecord.port, null, null, null))

        uris.map(u => Service(srvRecord.target, u))
      case None =>
        Seq.empty
    }

  def lookupOne(namespace: String, name: String, endpoint: String, addressSelection: AddressSelection)(implicit as: ActorSystem): Future[Option[Service]] = {
    import as.dispatcher
    lookup(namespace, name, endpoint).map(addressSelection)
  }

  def lookupOne(name: String, endpoint: String, addressSelection: AddressSelection)(implicit as: ActorSystem): Future[Option[Service]] = {
    import as.dispatcher
    lookup(name, endpoint).map(addressSelection)
  }

  def lookupOne(name: String, addressSelection: AddressSelection)(implicit as: ActorSystem): Future[Option[Service]] = {
    import as.dispatcher
    lookup(name).map(addressSelection)
  }

  def lookupOne(namespace: String, name: String, endpoint: String)(implicit as: ActorSystem): Future[Option[Service]] = {
    import as.dispatcher
    lookup(namespace, name, endpoint).map(AddressSelectionRandom)
  }

  def lookupOne(name: String, endpoint: String)(implicit as: ActorSystem): Future[Option[Service]] = {
    import as.dispatcher
    lookup(name, endpoint).map(AddressSelectionRandom)
  }

  def lookupOne(name: String)(implicit as: ActorSystem): Future[Option[Service]] = {
    import as.dispatcher
    lookup(name).map(AddressSelectionRandom)
  }

  def lookup(namespace: String, name: String, endpoint: String)(implicit as: ActorSystem): Future[Seq[Service]] =
    doLookup(namespace = Option(namespace).filter(_.nonEmpty), name, endpoint, externalChecks = 0)

  def lookup(name: String, endpoint: String)(implicit as: ActorSystem): Future[Seq[Service]] =
    doLookup(namespace = None, name, endpoint, externalChecks = 0)

  def lookup(name: String)(implicit as: ActorSystem): Future[Seq[Service]] =
    doLookup(namespace = None, name, endpoint = "", externalChecks = 0)

  private def normalizeProtocol(protocol: String, endpoint: String) =
    if (protocol == "tcp" && (endpoint == "http" || endpoint.contains("http-") || endpoint.contains("-http")))
      "http"
    else
      protocol

  private def doLookup(namespace: Option[String], name: String, endpoint: String, externalChecks: Int)(implicit as: ActorSystem): Future[Seq[Service]] = {
    val settings = Settings(as)

    import as.dispatcher

    val externalEntry =
      if (endpoint.isEmpty)
        name
      else
        s"$name/$endpoint"

    settings.externalServiceAddresses.get(externalEntry) match {
      case Some(services) =>
        val resolved =
          services.map { uri =>
            if (uri.getScheme == null && externalChecks < settings.externalServiceAddressLimit) {
              val parts = uri.toString.split("/", 2)

              if (parts.length == 2)
                doLookup(namespace, parts(0), parts(1), externalChecks + 1)
              else
                doLookup(namespace, parts(0), "", externalChecks + 1)
            } else {
              Future.successful(Seq(Service(name, uri)))
            }
          }

        for {
          results <- Future.sequence(resolved)
        } yield results.flatten.toVector

      case None =>
        targetRuntime match {
          case None =>
            Future.successful(Seq.empty)

          case Some(Kubernetes) =>
            val nameToLookup = translateName(namespace, name, endpoint)

            for {
              result <- dnsResolver
                .ask(Dns.Resolve(nameToLookup))(settings.askTimeout)
                .flatMap {
                  case SrvResolved(srvName, srvRecords) =>
                    val protocol = translateProtocol(endpoint, srvName)
                    val lookups =
                      for {
                        srvRecord <- srvRecords
                      } yield {
                        dnsResolver
                          .ask(Dns.Resolve(srvRecord.target))(settings.askTimeout)
                          .collect {
                            case aRecord: Resolved =>
                              translateResolved(protocol, srvRecord, aRecord)
                          }
                      }

                    Future
                      .sequence(lookups)
                      .map(_.flatten.toVector)

                  case _: Resolved =>
                    // Future improvement: support regular DNS

                    Future.successful(Seq.empty)

                }
            } yield result
        }
    }
  }

  protected def namespaceFromEnv(): Option[String] =
    env.get("RP_NAMESPACE")

  private def endpointServiceName(name: String): String =
    name
      .map(c => if (ValidEndpointServiceChars.contains(c)) c else '-')
      .toLowerCase
}
