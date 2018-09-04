/*
 * Copyright 2014-2016 Ilya Epifanov
 */

package com.lightbend.rp.asyncdns.raw

import akka.util.{ ByteIterator, ByteStringBuilder }
import java.util.NoSuchElementException

object RecordType extends Enumeration {
  val A = Value(1)
  val NS = Value(2)
  val MD = Value(3)
  val MF = Value(4)
  val CNAME = Value(5)
  val SOA = Value(6)
  val MB = Value(7)
  val MG = Value(8)
  val MR = Value(9)
  val NULL = Value(10)
  val WKS = Value(11)
  val PTR = Value(12)
  val HINFO = Value(13)
  val MINFO = Value(14)
  val MX = Value(15)
  val TXT = Value(16)
  val AAAA = Value(28)
  val SRV = Value(33)

  val AXFR = Value(252)
  val MAILB = Value(253)
  val MAILA = Value(254)
  val WILDCARD = Value(255)

  def parse(it: ByteIterator): Option[Value] =
    try {
      Option(RecordType(it.getShort))
    } catch {
      case _: NoSuchElementException => None
    }

  def write(out: ByteStringBuilder, t: Value): Unit = {
    out.putShort(t.id)
  }
}
