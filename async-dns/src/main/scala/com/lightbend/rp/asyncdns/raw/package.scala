/*
 * Copyright 2014-2016 Ilya Epifanov
 */

package com.lightbend.rp.asyncdns

import java.nio.ByteOrder

package object raw {
  implicit val networkByteOrder = ByteOrder.BIG_ENDIAN
}
