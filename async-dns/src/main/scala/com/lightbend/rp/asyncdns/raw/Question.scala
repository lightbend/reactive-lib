/*
 * Copyright 2014-2016 Ilya Epifanov
 */

package com.lightbend.rp.asyncdns.raw

import akka.util.{ ByteIterator, ByteString, ByteStringBuilder }

case class Question(name: String, qType: RecordType.Value, qClass: RecordClass.Value) {
  def write(out: ByteStringBuilder) {
    DomainName.write(out, name)
    RecordType.write(out, qType)
    RecordClass.write(out, qClass)
  }
}

object Question {
  def parse(it: ByteIterator, msg: ByteString): Option[Question] = {
    // don't use for comprehension yet because we need to read all the bytes
    val name = DomainName.parse(it, msg)
    val qType = RecordType.parse(it)
    val qClass = RecordClass.parse(it)
    for {
      t <- qType
      c <- qClass
    } yield Question(name, t, c)
  }
}
