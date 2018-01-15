// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem
package dao

import cats.implicits._
import doobie._, doobie.implicits._
import gem.config.{DynamicConfig, StaticConfig}
import gem.dao.meta._
import gem.enum.{ Instrument, Site }
import gem.syntax.map._
import gem.util.{ Location, Timestamp }

import scala.collection.immutable.TreeMap

object ObservationDao {
  import EnumeratedMeta._
  import ObservationIdMeta._
  import ProgramIdMeta._

  /** Construct a program to insert a fully-populated Observation. This program
    * will raise a key violation if an observation with the same id already
    * exists.
    */
  def insert(oid: Observation.Id, o: Observation[StaticConfig, Step[DynamicConfig]]): ConnectionIO[Unit] =
    for {
      id <- StaticConfigDao.insert(o.staticConfig)
      _  <- Statements.insert(oid, o, id).run
      _  <- o.steps.zipWithIndex.traverse { case (s, i) =>
              StepDao.insert(oid, Location.unsafeMiddle((i + 1) * 100), s)
            }.void
    } yield ()

  /** Construct a program to select the specified observation, with the
    * instrument and target but no static configuration or steps or ephemeris
    * for non-sidereal targets.
    */
  def selectFlat(
    id: Observation.Id,
    site: Site,
    from: Timestamp,
    to: Timestamp
  ): ConnectionIO[Observation[Instrument, Nothing]] =
    for {
      o <- Statements.selectFlat(id).unique.map(_._1)
      t <- TargetEnvironmentDao.select(id, site, from, to)
    } yield o.copy(targets = t)

  /** Construct a program to select the specified observation, with static
    * config but no steps or ephemeris for non-sidereal targets. */
  def selectStatic(
    id: Observation.Id,
    site: Site,
    from: Timestamp,
    to: Timestamp
  ): ConnectionIO[Observation[StaticConfig, Nothing]] =
    for {
      obs <- selectFlat(id, site, from, to)
      tup <- Statements.selectStaticId(id).unique
      sc  <- StaticConfigDao.select(tup._1, tup._2)
    } yield obs.leftMap(_ => sc)

  /** Construct a program to select the specified observation, with static connfig and steps. */
  def select(
    id: Observation.Id,
    site: Site,
    from: Timestamp,
    to: Timestamp
  ): ConnectionIO[Observation[StaticConfig, Step[DynamicConfig]]] =
    for {
      on <- selectStatic(id, site, from, to)
      ss <- StepDao.selectAll(id)
    } yield on.copy(steps = ss.values.toList)

  /** Construct a program to select the all obseravation ids for the specified science program. */
  def selectIds(pid: Program.Id): ConnectionIO[List[Observation.Id]] =
    Statements.selectIds(pid).list

  /**
   * Construct a program to select all observations for the specified science program, with the
   * instrument and no steps.
   */
  def selectAllFlat(
    pid: Program.Id,
    site: Site,
    from: Timestamp,
    to: Timestamp
  ): ConnectionIO[TreeMap[Observation.Index, Observation[Instrument, Nothing]]] =
    for {
      m  <- Statements.selectAllFlat(pid).list.map(lst => TreeMap(lst.map { case (i,o,_) => (i,o) }: _*))
      ts <- m.keys.toList.traverse(i => TargetEnvironmentDao.select(Observation.Id(pid, i), site, from, to).tupleLeft(i))
    } yield m.merge(ts.toMap) { (o, e) => o.copy(targets = e) }

  /**
   * Construct a program to select all observations for the specified science program, with the
   * static component and no steps.
   */
  def selectAllStatic(
    pid: Program.Id,
    site: Site,
    from: Timestamp,
    to: Timestamp
  ): ConnectionIO[TreeMap[Observation.Index, Observation[StaticConfig, Nothing]]] =
    for {
      ids <- selectIds(pid)
      oss <- ids.traverse(selectStatic(_, site, from, to))
      ts  <- ids.traverse(i => TargetEnvironmentDao.select(i, site, from, to).tupleLeft(i.index))
    } yield TreeMap(ids.map(_.index).zip(oss): _*).merge(ts.toMap) { (o, e) => o.copy(targets = e) }

  /**
   * Construct a program to select all observations for the specified science program, with the
   * static component and steps.
   */
  def selectAll(
    pid: Program.Id,
    site: Site,
    from: Timestamp,
    to: Timestamp
  ): ConnectionIO[TreeMap[Observation.Index, Observation[StaticConfig, Step[DynamicConfig]]]] =
    for {
      ids <- selectIds(pid)
      oss <- ids.traverse(select(_, site, from, to))
      ts  <- ids.traverse(i => TargetEnvironmentDao.select(i, site, from, to).tupleLeft(i.index))
    } yield TreeMap(ids.map(_.index).zip(oss): _*).merge(ts.toMap) { (o, e) => o.copy(targets = e) }

  object Statements {

    // Observation.Index has a DISTINCT type due to its check constraint so we
    // need a fine-grained mapping here to satisfy the query checker.
    implicit val ObservationIndexMeta: Meta[Observation.Index] =
    Distinct.integer("id_index").xmap(Observation.Index.unsafeFromInt, _.toInt)

    def insert(oid: Observation.Id, o: Observation[StaticConfig, _], staticId: Int): Update0 =
      sql"""
        INSERT INTO observation (observation_id,
                                program_id,
                                observation_index,
                                title,
                                static_id,
                                instrument)
              VALUES (${oid},
                      ${oid.pid},
                      ${oid.index},
                      ${o.title},
                      $staticId,
                      ${o.staticConfig.instrument: Instrument})
      """.update

    def selectIds(pid: Program.Id): Query0[Observation.Id] =
      sql"""
        SELECT observation_id
          FROM observation
         WHERE program_id = $pid
      """.query[Observation.Id]

    def selectStaticId(id: Observation.Id): Query0[(Instrument, Int)] =
      sql"""
        SELECT instrument, static_id
          FROM observation
         WHERE observation_id = $id
      """.query[(Instrument, Int)]

    def selectFlat(id: Observation.Id): Query0[(Observation[Instrument, Nothing], Int)] =
      sql"""
        SELECT title, instrument, static_id
          FROM observation
         WHERE observation_id = ${id}
      """.query[(String, Instrument, Int)]
        .map { case (t, i, s) =>
          (Observation(t, TargetEnvironment.empty, i, Nil), s)
        }

    def selectAllFlat(pid: Program.Id): Query0[(Observation.Index, Observation[Instrument, Nothing], Int)] =
      sql"""
        SELECT observation_index, title, instrument, static_id
          FROM observation
         WHERE program_id = ${pid}
      ORDER BY observation_index
      """.query[(Short, String, Instrument, Int)]
        .map { case (n, t, i, s) =>
          (Observation.Index.unsafeFromInt(n.toInt), Observation(t, TargetEnvironment.empty, i, Nil), s)
        }

  }
}
