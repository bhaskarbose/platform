/*
 *  ____    ____    _____    ____    ___     ____ 
 * |  _ \  |  _ \  | ____|  / ___|  / _/    / ___|        Precog (R)
 * | |_) | | |_) | |  _|   | |     | |  /| | |  _         Advanced Analytics Engine for NoSQL Data
 * |  __/  |  _ <  | |___  | |___  |/ _| | | |_| |        Copyright (C) 2010 - 2013 SlamData, Inc.
 * |_|     |_| \_\ |_____|  \____|   /__/   \____|        All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the 
 * GNU Affero General Public License as published by the Free Software Foundation, either version 
 * 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See 
 * the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this 
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.precog.niflheim

import blueeyes.json._
import scala.collection.mutable
import org.joda.time.DateTime
import java.io._

import com.precog.common._
import com.precog.common.json._
import com.precog.util._

object RawHandler {
  // file doesn't exist -> create new file
  def empty(id: Long, f: File): RawHandler = {
    if (f.exists)
      sys.error("rawlog %s already exists!" format f)
    val os = new BufferedOutputStream(new FileOutputStream(f, true))
    RawLoader.writeHeader(os, id)
    new RawHandler(id, f, Nil, os)
  }

  // file does exist and is ok -> load data
  def load(id: Long, f: File): (RawHandler, Seq[Long], Boolean) = {
    val (rows, events, ok) = RawLoader.load(id, f)
    val os = new BufferedOutputStream(new FileOutputStream(f, true))
    (new RawHandler(id, f, rows, os), events, ok)
  }

  def loadReadonly(id: Long, f: File): (RawReader, Seq[Long], Boolean) = {
    val (rows, events, ok) = RawLoader.load(id, f)
    (new RawReader(id, f, rows), events, ok)
  }
}

class RawReader private[niflheim] (val id: Long, val log: File, rs: Seq[JValue]) extends StorageReader {
  // TODO: weakrefs?
  @volatile protected[this] var rows = mutable.ArrayBuffer.empty[JValue] ++ rs
  @volatile protected[this] var segments = Segments.empty(id)
  protected[this] var count = rows.length

  def isStable: Boolean = true

  def structure: Iterable[(CPath, CType)] = snapshot(None).map { seg => (seg.cpath, seg.ctype) }

  def length: Int = count

  def snapshot(pathConstraint: Option[Set[CPath]]): Seq[Segment] = {
    if (!rows.isEmpty) {
      segments.synchronized {
        if (!rows.isEmpty) {
          segments.extendWithRows(rows)
          rows.clear()
        }
        segments
      }
    }

    pathConstraint.map { cpaths =>
      segments.a.filter { seg => cpaths(seg.cpath) }
    }.getOrElse(segments.a.clone)
  }
}

class RawHandler private[niflheim] (id: Long, log: File, rs: Seq[JValue], private var os: OutputStream) extends RawReader(id, log, rs) {
  def write(eventid: Long, values: Seq[JValue]) {
    if (!values.isEmpty) {
      count += values.length
      RawLoader.writeEvents(os, eventid, values)
      rows ++= values
    }
  }

  override def isStable: Boolean = os == null

  def close(): Unit = if (os != null) {
    os.close()
    os = null
  }
}