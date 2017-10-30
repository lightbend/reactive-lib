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
import com.lightbend.dns.locator.{ ServiceLocator => DnsServiceLocator, Settings => DnsServiceLocatorSettings }
import com.lightbend.rp._
import java.net.URI
import scala.concurrent.Future

case class ServiceLocator(as: ActorSystem) {
  def lookup(name: String): Future[Option[URI]] = ServiceLocator.lookup(name)(as)
}

object ServiceLocator {
  def lookup(name: String)(implicit as: ActorSystem): Future[Option[URI]] =
    Platform.active match {
      case None =>
        Future.successful(None)

      case Some(Kubernetes) =>
        import as.dispatcher
        val locator = as.actorOf(Props[DnsServiceLocator])
        val serviceLocatorSettings = DnsServiceLocatorSettings(as)
        val settings = Settings(as)

        val askTimeout =
          settings.askTimeout +
            serviceLocatorSettings.resolveTimeout1 +
            serviceLocatorSettings.resolveTimeout1 +
            serviceLocatorSettings.resolveTimeout2

        for {
          result   <-
            locator
              .ask(DnsServiceLocator.GetAddress(name))(askTimeout)
              .mapTo[DnsServiceLocator.Addresses]
        } yield result.addresses.headOption.map(addressToUri)
    }

  private def addressToUri(a: DnsServiceLocator.ServiceAddress) =
    new URI(a.protocol, null, a.host, a.port, null, null, null)
}
