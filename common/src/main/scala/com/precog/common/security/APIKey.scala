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
package com.precog.common
package security

import json._

import blueeyes.json.JValue
import blueeyes.json.serialization._
import blueeyes.json.serialization.DefaultSerialization.{ DateTimeDecomposer => _, DateTimeExtractor => _, _ }

import scalaz.Scalaz._
import scalaz.Validation

import shapeless._

case class APIKeyRecord(
  apiKey:         APIKey,
  name:           Option[String],
  description:    Option[String],
  issuerKey:      Option[APIKey],
  grants:         Set[GrantId],
  isRoot:         Boolean)

object APIKeyRecord {
  implicit val apiKeyRecordIso = Iso.hlist(APIKeyRecord.apply _, APIKeyRecord.unapply _)
  
  val schema =       "apiKey"  :: "name" :: "description" :: "issuerKey" :: "grants" :: "isRoot" :: HNil
  val safeSchema =   "apiKey"  :: "name" :: "description" :: Omit        :: "grants" :: "isRoot" :: HNil

  val legacySchema = ("apiKey" | "tokenId") :: "name" :: "description" :: "issuerKey" :: "grants" :: ("isRoot" ||| false) :: HNil
  
  object Serialization {
    implicit val (apiKeyRecordDecomposer, apiKeyRecordExtractor) = serialization[APIKeyRecord](schema)
  }
  
  object SafeSerialization {
    implicit val (safeAPIKeyRecordDecomposer, safeAPIKeyRecordExtractor) = serialization[APIKeyRecord](safeSchema)
  }

  object LegacySerialization {
    val legacyAPIKeyExtractor = extractor[APIKeyRecord](legacySchema)
  }
}

case class NewAPIKeyRequest(name: Option[String], description: Option[String], grants: Set[NewGrantRequest])

object NewAPIKeyRequest {
  implicit val newAPIKeyRequestIso = Iso.hlist(NewAPIKeyRequest.apply _, NewAPIKeyRequest.unapply _)
  
  val schema = "name" :: "description" :: "grants" :: HNil
  
  implicit val (newAPIKeyRequestDecomposer, newAPIKeyRequestExtractor) = serialization[NewAPIKeyRequest](schema)
  
  def newAccount(accountId: String, path: Path, name: Option[String] = None, description: Option[String] = None) = {
    val grants = NewGrantRequest.newAccount(accountId, path, name.map(_+"-grant"), description.map(_+" standard account grant"), Set.empty[GrantId], None)
    NewAPIKeyRequest(name, description, Set(grants))
  }
}

// vim: set ts=4 sw=4 et:
