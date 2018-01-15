// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem
package dao

import cats.implicits._

import gem.enum.Site
import gem.util.Timestamp

import doobie._
import doobie.implicits._


object TargetEnvironmentDao {

  private def toTargetEnvironment(
    userTargets: ConnectionIO[List[(Int, UserTarget)]]
  ): ConnectionIO[TargetEnvironment] =
    userTargets.map { lst =>
      TargetEnvironment(lst.unzip._2.toSet)
    }

  def insert(oid: Observation.Id, e: TargetEnvironment): ConnectionIO[Unit] =
    e.userTargets.toList.traverse(UserTargetDao.insert(_, oid)).void

  def selectEmpty(oid: Observation.Id): ConnectionIO[TargetEnvironment] =
    toTargetEnvironment(UserTargetDao.selectAllEmpty(oid))

  def select(
    oid: Observation.Id,
    site: Site,
    from: Timestamp,
    to: Timestamp
  ): ConnectionIO[TargetEnvironment] =
    toTargetEnvironment(UserTargetDao.selectAll(oid, site, from, to))

}
