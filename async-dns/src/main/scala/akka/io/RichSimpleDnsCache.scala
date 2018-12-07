/*
 * Copyright 2017-2018 Lightbend, Inc.
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

package akka.io

import scala.language.implicitConversions
import akka.io.dns.CachePolicy._

object RichSimpleDnsCache {
  implicit def enrichedSimpleDnsCache(c: SimpleDnsCache): RichSimpleDnsCache =
    new RichSimpleDnsCache(c)
}

/**
 * This class exists to expose `private[io] def put(...)` to AsyncDnsResolver.
 */
final class RichSimpleDnsCache(val underlying: SimpleDnsCache) extends AnyVal {
  def doPut(r: Dns.Resolved, ttl: CachePolicy): Unit = underlying.put(r, ttl)
}
