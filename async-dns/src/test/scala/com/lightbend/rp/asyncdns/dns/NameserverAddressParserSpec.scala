/*
 * Copyright 2014-2016 Ilya Epifanov
 */

package com.lightbend.rp.asyncdns.dns

import com.lightbend.rp.asyncdns.AsyncDnsResolver
import java.net.InetSocketAddress
import org.scalatest.{ Matchers, WordSpec }

class NameserverAddressParserSpec extends WordSpec with Matchers {
  "Parser" should {
    "handle explicit port in IPv4 address" in {
      AsyncDnsResolver.parseNameserverAddress("8.8.8.8:153") should equal(new InetSocketAddress("8.8.8.8", 153))
    }
    "handle explicit port in IPv6 address" in {
      AsyncDnsResolver.parseNameserverAddress("[2001:4860:4860::8888]:153") should equal(new InetSocketAddress("2001:4860:4860::8888", 153))
    }
    "handle default port in IPv4 address" in {
      AsyncDnsResolver.parseNameserverAddress("8.8.8.8") should equal(new InetSocketAddress("8.8.8.8", 53))
    }
    "handle default port in IPv6 address" in {
      AsyncDnsResolver.parseNameserverAddress("[2001:4860:4860::8888]") should equal(new InetSocketAddress("2001:4860:4860::8888", 53))
    }
  }
}
