// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem.horizons.tcs

import gem.EphemerisKey
import gem.dao.EphemerisDao
import gem.enum.Site
import gem.util.InstantMicros

import cats.effect._
import cats.implicits._
import doobie._
import doobie.implicits._
import fs2.text
import fs2.io.file

import java.nio.file.Path
import java.time.Instant

/** Provides support for exporting ephemeris data to files that may be read by
  * the TCS.
  *
  * @param xa transactor to use for working with the database
  */
final class TcsEphemerisExport(xa: Transactor[IO]) {
  import TcsEphemerisExport.RowLimit

  /** Exports up to `RowLimit` lines of ephemeris data associated with the given
    * key, site, and time range.
    *
    * @param path  file path to which the ephemeris data should be written
    * @param key   key corresponding to the object to export
    * @param site  site for which the data is relevant
    * @param start start time for ephemeris data, inclusive
    * @param end   end time for ephemeris data, exclusive
    */
  def exportOne(path: Path, key: EphemerisKey, site: Site, start: Instant, end: Instant): IO[Unit] =
    EphemerisDao
      .streamRange(key, site, InstantMicros.truncate(start), InstantMicros.truncate(end))
      .transact(xa)
      .take(RowLimit.toLong)
      .through(TcsFormat.ephemeris)
      .intersperse("\n")
      .through(text.utf8Encode)
      .to(file.writeAll(path))
      .run

  /** Exports all ephemerides for the given site for the given time period. File
    * names are invented based upon the corresponding ephemeris key.  See
    * `EphemerisKey.format`.
    *
    * @param dir   directory where the ephemerides will be written
    * @param site  site to which the ephemerides correspond
    * @param start start time for the ephemeris data, inclusive
    * @param end   end time for the ephemeirs day, exclusive
    */
  def exportAll(dir: Path, site: Site, start: Instant, end: Instant): IO[Unit] = {
    def name(k: EphemerisKey): String =
      s"${k.format}.eph"

    for {
      ks <- EphemerisDao.selectKeys(site).transact(xa)
      _  <- ks.toList.traverse(k => exportOne(dir.resolve(name(k)), k, site, start, end)).void
    } yield ()
  }
}

object TcsEphemerisExport {

  /** Max ephemeris elements supported by the TCS. */
  val RowLimit: Int =
    1440
}
