// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem
package dao

import cats.implicits._
import doobie.ConnectionIO
import doobie.implicits._
import gem.Observation
import gem.config.{ DynamicConfig, StaticConfig }
import gem.enum.Site
import gem.math.Ephemeris
import gem.util.Timestamp
import org.scalatest._
import org.scalatest.prop._
import org.scalatest.Matchers._

class ObservationDaoSpec extends PropSpec with PropertyChecks with DaoTest {
  import ObservationDaoSpec._

  import gem.arb.ArbEnumerated._

  property("ObservationDao should select all observation ids for a program") {
    forAll(genObservationMap(limit = 50)) { obsMap =>
      val oids = withProgram {
        for {
          _ <- obsMap.toList.traverse { case (i,o) => ObservationDao.insert(Observation.Id(pid, i), o) }
          o <- ObservationDao.selectIds(pid)
        } yield o
      }

      oids.toSet shouldEqual obsMap.keys.map(idx => Observation.Id(pid, idx)).toSet
    }
  }

  val One: Observation.Index =
    Observation.Index.unsafeFromInt(1)

  property("ObservationDao should select flat observations") {
    val oid = Observation.Id(pid, One)

    forAll { (obsIn: Observation[StaticConfig, Step[DynamicConfig]]) =>
      val obsInʹ = obsIn.removeDuplicateNonsidereal
      val obsOut = withProgram {
        for {
          _ <- ObservationDao.insert(oid, obsInʹ)
          _ <- insertEphemeris(obsInʹ)
          o <- ObservationDao.selectFlat(oid, Site.GS, Timestamp.Min, Timestamp.Max)
        } yield o
      }

      obsOut shouldEqual obsInʹ.leftMap(_.instrument).copy(steps = Nil).forSite(Site.GS)
    }
  }

  property("ObservationDao should select static observations") {
    val oid = Observation.Id(pid, One)

    forAll { (obsIn: Observation[StaticConfig, Step[DynamicConfig]]) =>

      val obsInʹ = obsIn.removeDuplicateNonsidereal
      val obsOut = withProgram {
        for {
          _ <- ObservationDao.insert(oid, obsInʹ)
          _ <- insertEphemeris(obsInʹ)
          o <- ObservationDao.selectStatic(oid, Site.GS, Timestamp.Min, Timestamp.Max)
        } yield o
      }

      obsOut shouldEqual obsInʹ.copy(steps = Nil).forSite(Site.GS)
    }
  }

  property("ObservationDao should roundtrip complete observations") {
    val oid = Observation.Id(pid, One)

    forAll { (obsIn: Observation[StaticConfig, Step[DynamicConfig]]) =>
      val obsInʹ = obsIn.removeDuplicateNonsidereal
      val obsOut = withProgram {
        for {
          _ <- ObservationDao.insert(oid, obsInʹ)
          _ <- insertEphemeris(obsInʹ)
          o <- ObservationDao.select(oid, Site.GS, Timestamp.Min, Timestamp.Max)
        } yield o
      }

      obsOut shouldEqual obsInʹ.forSite(Site.GS)
    }
  }

  /* TODO:
  there's still an issue here because two different observations may may have
  nonsidereal targets with the same id and yet different generated ephemerides
  */
  /*
  property("ObservationDao should roundtrip complete observation lists") {
    forAll(genObservationMap(limit = 50)) { obsMapIn =>
      val obsMapInʹ = obsMapIn.mapValues(_.removeDuplicateNonsidereal)
      val obsMapOut = withProgram {
        for {
          _ <- obsMapInʹ.toList.traverse { case (i,o) =>
                 ObservationDao.insert(Observation.Id(pid, i), o) *>
                   insertEphemeris(o)
               }
          m <- ObservationDao.selectAll(pid, Site.GS, Timestamp.Min, Timestamp.Max)
        } yield m
      }

      obsMapOut shouldEqual obsMapInʹ.toList.map { case (i,o) => (i,o.forSite(Site.GS)) }.toMap
    }
  }
  */
}

object ObservationDaoSpec {

  private def insertEphemeris[S, D](o: Observation[S, D]): ConnectionIO[Unit] =
    o.targets.userTargets.toList.map(_.target.track).traverse {
      case Track.Sidereal(_)       =>
        ().pure[ConnectionIO]

      case Track.Nonsidereal(k, m) =>
        m.toList.traverse { case (s, e) =>
          EphemerisDao.insert(k, s, e)
        }.void
    }.void

  implicit class ObsOps[S, D](o: Observation[S, D]) {
    def removeDuplicateNonsidereal: Observation[S, D] =
      o.copy(targets =
        o.targets.copy(userTargets = {
          val keyMap = o.targets.userTargets.collect {
            case ut@UserTarget(Target(_, Track.Nonsidereal(k, _)), _) => k -> ut
          }.toMap

          o.targets.userTargets.map {
            case ut@UserTarget(Target(_, Track.Nonsidereal(k, _)), _) => keyMap.getOrElse(k, ut)
            case ut                                             => ut
          }
        })
      )


    def forSite(s: Site): Observation[S, D] =
      o.copy(targets =
        TargetEnvironment.userTargets.modify { uts =>
          uts.map { ut =>
            UserTarget.ephemerides.modify { m =>
              Map(s -> m.get(s).getOrElse(Ephemeris.empty))
            }(ut)
          }
        }(o.targets)
      )

  }

}
