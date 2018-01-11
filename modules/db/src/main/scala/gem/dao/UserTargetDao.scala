// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem
package dao

import gem.dao.meta._
import gem.enum.{ Site, UserTargetType }
import gem.util.InstantMicros

import cats.implicits._
import doobie._, doobie.implicits._


object UserTargetDao {

  // A target ID and the corresponding user target type.  We use the id to
  // get the actual target.
  final case class ProtoUserTarget(targetId: Int, targetType: UserTargetType)

  import EnumeratedMeta._
  import ObservationIdMeta._

  def insert(userTarget: UserTarget, oid: Observation.Id): ConnectionIO[Int] =
    for {
      tid <- TargetDao.insert(userTarget.target)
      uid <- Statements.insert(tid, userTarget.targetType, oid).withUniqueGeneratedKeys[Int]("id")
    } yield uid

  private def toUserTarget(
    oput: Option[ProtoUserTarget],
    targetQuery: Int => ConnectionIO[Option[Target]]
 ): ConnectionIO[Option[UserTarget]] =
    oput.fold(Option.empty[UserTarget].pure[ConnectionIO]) { put =>
      targetQuery(put.targetId).map(_.map(UserTarget(_, put.targetType)))
    }

  private def _select(
    id: Int,
    targetQuery: Int => ConnectionIO[Option[Target]]
  ): ConnectionIO[Option[UserTarget]] =
    for {
      oput <- Statements.select(id).option
      out  <- toUserTarget(oput, targetQuery)
    } yield out

  def selectEmpty(id: Int): ConnectionIO[Option[UserTarget]] =
    _select(id, TargetDao.selectEmpty)

  def select(
    id: Int,
    site: Site,
    from: InstantMicros,
    to: InstantMicros
  ): ConnectionIO[Option[UserTarget]] =
    _select(id, TargetDao.select(_, site, from, to))

  private def _selectAll(
    oid: Observation.Id,
    targetQuery: Int => ConnectionIO[Option[Target]]
  ): ConnectionIO[List[(Int, UserTarget)]] =
    for {
      puts <- Statements.selectAll(oid).list                // List[(Int, ProtoUserTarget)]
      ots  <- puts.map(_._2.targetId).traverse(targetQuery) // List[Option[Target]]
    } yield puts.zip(ots).flatMap { case ((id, put), ot) =>
      ot.map(t => id -> UserTarget(t, put.targetType)).toList
    }

  def selectAllEmpty(oid: Observation.Id): ConnectionIO[List[(Int, UserTarget)]] =
    _selectAll(oid, TargetDao.selectEmpty)

  def selectAll(
    oid: Observation.Id,
    site: Site,
    from: InstantMicros,
    to: InstantMicros
  ): ConnectionIO[List[(Int, UserTarget)]] =
    _selectAll(oid, TargetDao.select(_, site, from, to))

  object Statements {

    def insert(targetId: Int, targetType: UserTargetType, oid: Observation.Id): Update0 =
      sql"""
        INSERT INTO user_target (
          target_id,
          user_target_type,
          observation_id
        ) VALUES (
          $targetId,
          $targetType,
          $oid
        )
      """.update

    def select(id: Int): Query0[ProtoUserTarget] =
      sql"""
        SELECT target_id,
               user_target_type
          FROM user_target
         WHERE id = $id
      """.query[ProtoUserTarget]

    def selectAll(oid: Observation.Id): Query0[(Int, ProtoUserTarget)] =
      sql"""
        SELECT id,
               target_id,
               user_target_type
          FROM user_target
         WHERE observation_id = $oid
      """.query[(Int, ProtoUserTarget)]
  }
}
