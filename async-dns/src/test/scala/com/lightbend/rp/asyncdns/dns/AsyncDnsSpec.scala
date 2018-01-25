/*
 * Copyright 2014-2016 Ilya Epifanov
 */

package com.lightbend.rp.asyncdns.dns

import akka.actor.ActorSystem
import akka.dispatch.Dispatchers
import akka.event.{ Logging, LoggingAdapter }
import akka.testkit._
import akka.testkit.TestEvent._
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import scala.concurrent.Future
import scala.language.{ postfixOps, reflectiveCalls }

object AsyncDnsSpec {
  val testConf: Config = ConfigFactory.parseString(
    """
      akka {
        loggers = ["akka.testkit.TestEventListener"]
        loglevel = "WARNING"
        stdout-loglevel = "WARNING"
        actor {
          default-dispatcher {
            executor = "fork-join-executor"
            fork-join-executor {
              parallelism-min = 8
              parallelism-factor = 2.0
              parallelism-max = 8
            }
          }
        }
      }
    """)

  def mapToConfig(map: Map[String, AnyRef]): Config = {
    import scala.collection.JavaConverters._
    ConfigFactory.parseMap(map.asJava)
  }

  def getCallerName(clazz: Class[_]): String = {
    val s = (Thread.currentThread.getStackTrace map (_.getClassName) drop 1)
      .dropWhile(_ matches "(java.lang.Thread|.*AsyncDnsSpec.?$)")
    val reduced = s.lastIndexWhere(_ == clazz.getName) match {
      case -1 ⇒ s
      case z ⇒ s drop (z + 1)
    }
    reduced.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
  }

}

abstract class AsyncDnsSpec(_system: ActorSystem)
  extends TestKit(_system) with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this(config: Config) = this(ActorSystem(
    AsyncDnsSpec.getCallerName(getClass),
    ConfigFactory.load(config.withFallback(AsyncDnsSpec.testConf))))

  def this(s: String) = this(ConfigFactory.parseString(s))

  def this(configMap: Map[String, AnyRef]) = this(AsyncDnsSpec.mapToConfig(configMap))

  def this() = this(ActorSystem(AsyncDnsSpec.getCallerName(getClass), AsyncDnsSpec.testConf))

  val log: LoggingAdapter = Logging(system, this.getClass)

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  final override def beforeAll() {
    atStartup()
  }

  final override def afterAll() {
    beforeTermination()
    shutdown()
    afterTermination()
  }

  protected def atStartup() {}

  protected def beforeTermination() {}

  protected def afterTermination() {}

  def spawn(dispatcherId: String = Dispatchers.DefaultDispatcherId)(body: ⇒ Unit): Unit =
    Future(body)(system.dispatchers.lookup(dispatcherId))

  def muteDeadLetters(messageClasses: Class[_]*)(sys: ActorSystem = system): Unit =
    if (!sys.log.isDebugEnabled) {
      def mute(clazz: Class[_]): Unit =
        sys.eventStream.publish(Mute(DeadLettersFilter(clazz)(occurrences = Int.MaxValue)))
      if (messageClasses.isEmpty) mute(classOf[AnyRef])
      else messageClasses foreach mute
    }

}
