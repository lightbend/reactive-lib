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
import akka.pattern.ask
import com.lightbend.rp.common._
import com.lightbend.dns.locator.{ ServiceLocator => DnsServiceLocator, Settings => DnsServiceLocatorSettings }
import com.lightbend.rp.servicediscovery.scaladsl.ServiceLocator.{ AddressSelection, AddressSelectionRandom }
import java.net.URI
import java.util.concurrent.ThreadLocalRandom
import scala.collection.immutable.Seq
import scala.concurrent.Future

case class ServiceLocator(as: ActorSystem) {
  def lookupOne(name: String, endpoint: String = "", addressSelection: AddressSelection = AddressSelectionRandom): Future[Option[URI]] =
    ServiceLocator.lookupOne(name, endpoint, addressSelection)(as)

  def lookup(name: String, endpoint: String = ""): Future[Seq[URI]] =
    ServiceLocator.lookup(name, endpoint)(as)
}

object ServiceLocator {
  type AddressSelection = Seq[URI] => Option[URI]

  val AddressSelectionRandom: AddressSelection =
    services => if (services.isEmpty) None else Some(services(ThreadLocalRandom.current.nextInt(services.length)))

  val AddressSelectionFirst: AddressSelection =
    services => services.headOption

  def lookupOne(
    name: String,
    endpoint: String = "",
    addressSelection: AddressSelection = AddressSelectionRandom)(implicit as: ActorSystem): Future[Option[URI]] = {
    import as.dispatcher

    for {
      addresses <- lookup(name, endpoint)
    } yield addressSelection(addresses)
  }

  def lookup(name: String, endpoint: String = "")(implicit as: ActorSystem): Future[Seq[URI]] =
    doLookup(name, endpoint, externalChecks = 0)

  private def doLookup(name: String, endpoint: String, externalChecks: Int)(implicit as: ActorSystem): Future[Seq[URI]] = {
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
                doLookup(parts(0), parts(1), externalChecks + 1)
              else
                doLookup(parts(0), "", externalChecks + 1)
            } else
              Future.successful(Seq(uri))
          }

        for {
          results <- Future.sequence(resolved)
        } yield results.flatten.toVector

      case None =>
        Platform.active match {
          case None =>
            Future.successful(Seq.empty)

          case Some(Kubernetes) =>
            val locator = as.actorOf(Props[DnsServiceLocator])
            val serviceLocatorSettings = DnsServiceLocatorSettings(as)

            // The timeout value is deduced from the DnsServiceLocator logic
            // which does upto three attempts (first timeout value twice, then second once)
            // plus additional time for local processing

            val askTimeout =
              settings.askTimeout +
                serviceLocatorSettings.resolveTimeout1 +
                serviceLocatorSettings.resolveTimeout1 +
                serviceLocatorSettings.resolveTimeout2

            for {
              result <- locator
                .ask(DnsServiceLocator.GetAddress(translateName(name, endpoint)))(askTimeout)
                .mapTo[DnsServiceLocator.Addresses]
            } yield result.addresses.map(addressToUri).toVector
        }
    }
  }

  private def translateName(name: String, endpoint: String) =
    Platform.active match {
      case Some(Kubernetes) =>
        if (name.exists(dnsCharacters.contains) && endpoint.isEmpty) {
          name
        } else {
          val serviceName = endpointServiceName(name)
          val endpointName = endpointServiceName(endpoint)

          // @TODO hardcoded _tcp
          // @TODO namespace to consider here when it lands
          // @TODO if endpoint is empty, consider this better

          s"_$endpointName._tcp.$serviceName.default.svc.cluster.local"
        }

      case None =>
        name
    }

  private val dnsCharacters = Set('_', '.')

  private val validEndpointServiceChars =
    (('0' to '9') ++ ('A' to 'Z') ++ ('a' to 'z') ++ Seq('-')).toSet

  private def endpointServiceName(name: String): String =
    name
      .map(c => if (validEndpointServiceChars.contains(c)) c else '-')
      .toLowerCase

  private def addressToUri(a: DnsServiceLocator.ServiceAddress) =
    new URI(a.protocol, null, a.host, a.port, null, null, null)
}
