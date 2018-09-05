/*
 * Copyright 2014-2016 Ilya Epifanov
 */

package com.lightbend.rp.asyncdns.raw

import akka.util.{ ByteIterator, ByteStringBuilder }
import java.util.NoSuchElementException

object RecordClass extends Enumeration {
  val IN = Value(1)
  val CS = Value(2)
  val CH = Value(3)
  val HS = Value(4)

  val WILDCARD = Value(255)

  def parse(it: ByteIterator): Option[Value] =
    try {
      Option(RecordClass(it.getShort))
    } catch {
      case _: NoSuchElementException => None
    }

  def write(out: ByteStringBuilder, c: Value): Unit = {
    out.putShort(c.id)
  }
}
