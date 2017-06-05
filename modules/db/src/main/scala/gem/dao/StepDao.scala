package gem
package dao

import edu.gemini.spModel.core._
import gem.Location
import gem.config._
import gem.config.GcalConfig.GcalLamp
import gem.enum._
import doobie.imports._

import java.time.Duration

import scalaz._
import Scalaz._

object StepDao {

  type Loc = Location.Middle

  def insert[I <: InstrumentConfig](oid: Observation.Id, loc: Loc, s: Step[I]): ConnectionIO[Int] =
    for {
      id <- Statements.insertBaseSlice(oid, loc, s.instrument, StepType.forStep(s)).withUniqueGeneratedKeys[Int]("step_id")
      _  <- s match {
              case BiasStep(_)         => Statements.insertBiasSlice(id).run
              case DarkStep(_)         => Statements.insertDarkSlice(id).run
              case ScienceStep(_, t)   => Statements.insertScienceSlice(id, t).run
              case SmartGcalStep(_, t) => Statements.insertSmartGcalSlice(id, t).run
              case GcalStep(_, g)      => GcalDao.insert(g, Some(id)) >>= (Statements.insertGcalStep(id, _).run)
            }
      _  <- insertConfigSlice(id, s.instrument)
    } yield id

  /** Selects a `Step` with an `Instrument` element at the given location in the
    * sequence but without any instrument configuration information.
    *
    * @param oid observation whose step should be selected
    * @param loc position of the step to select
    */
  def selectOneEmpty(oid: Observation.Id, loc: Loc): MaybeConnectionIO[Step[Instrument]] =
    Statements.selectOneEmpty(oid, loc).maybe

  /** Selects `Step`s with an `Instrument` element but without any instrument
    * configuration information.
    *
    * @param oid observation whose steps should be selected
    */
  def selectAllEmpty(oid: Observation.Id): ConnectionIO[Loc ==>> Step[Instrument]] =
    Statements.selectAllEmpty(oid).list.map(==>>.fromList(_))

  /** Selects all steps with their F2 instrument configuration data, assuming
    * the indicated observation is an F2 observation.  If not, fails with an
    * exception.
    *
    * @param oid F2 observation whose steps are sought
    */
  def selectAllF2(oid: Observation.Id): ConnectionIO[Loc ==>> Step[F2Config]] =
    selectAll(oid, allF2Only)

  /** Selects all steps with a generic instrument configuration data.
    *
    * @param oid observation whose steps are sought
    */
  def selectAllGeneric(oid: Observation.Id): ConnectionIO[Loc ==>> Step[GenericConfig]] =
    selectAll(oid, allGenericOnly)

  /** Selects the step at the indicated location in the sequence associated with
    * the indicated observation.
    *
    * @param oid observation whose step configuration is sought
    * @param loc location within the sequence to find
    */
  def selectOne(oid: Observation.Id, loc: Loc): MaybeConnectionIO[Step[InstrumentConfig]] = {
    def instrumentConfig(s: Step[Instrument]): MaybeConnectionIO[InstrumentConfig] =
      s.instrument match {
        case Instrument.Flamingos2 => oneF2Only(oid, loc)       .widen[InstrumentConfig]
        case Instrument.GmosN      => oneGmosNorthOnly(oid, loc).widen[InstrumentConfig]
        case Instrument.GmosS      => oneGmosSouthOnly(oid, loc).widen[InstrumentConfig]
        case _                     => oneGenericOnly(oid, loc)  .widen[InstrumentConfig]
      }

    for {
      s <- selectOneEmpty(oid, loc)
      i <- instrumentConfig(s)
    } yield s.as(i)
  }

  /** Selects all steps with their instrument configuration data.
    *
    * @param oid observation whose step configurations are sought
    */
  def selectAll(oid: Observation.Id): ConnectionIO[Loc ==>> Step[InstrumentConfig]] = {
    def instrumentConfig(ss: Loc ==>> Step[Instrument]): ConnectionIO[Loc ==>> InstrumentConfig] =
      ss.findMin.map(_._2.instrument).fold(==>>.empty[Loc, InstrumentConfig].point[ConnectionIO]) {
        case Instrument.Flamingos2 => allF2Only(oid)       .map(_.widen[InstrumentConfig])
        case Instrument.GmosN      => allGmosNorthOnly(oid).map(_.widen[InstrumentConfig])
        case Instrument.GmosS      => allGmosSouthOnly(oid).map(_.widen[InstrumentConfig])
        case _                     => allGenericOnly(oid)  .map(_.widen[InstrumentConfig])
      }

    for {
      ss <- selectAllEmpty(oid)
      is <- instrumentConfig(ss)
    } yield ss.intersectionWith(is) { (s, i) => s.as(i) }
  }

  /** Deletes the step at the indicated location, if any.
    *
    * @param oid observation whose step should be deleted
    * @param loc location of the step to delete
    */
  def delete(oid: Observation.Id, loc: Loc): ConnectionIO[Int] =
    Statements.delete(oid, loc).run

  /** Deletes all steps for the given observation, if any.
    *
    * @param oid observation whose steps should be deleted
    */
  def delete(oid: Observation.Id): ConnectionIO[Int] =
    Statements.delete(oid).run

  // HELPERS

  private def insertConfigSlice(id: Int, i: InstrumentConfig): ConnectionIO[Int] =
    i match {
      case f2: F2Config        => Statements.insertF2Config(id, f2).run
      case gn: GmosNorthConfig => Statements.insertGmosNorthConfig(id, gn).run
      case gs: GmosSouthConfig => Statements.insertGmosSouthConfig(id, gs).run
      case GenericConfig(i)    => 0.point[ConnectionIO]
    }

  // The type we get when we select the fully joined step
  private case class StepKernel(
    i: Instrument,
    stepType: StepType, // todo: make an enum
    gcal: (Option[GcalContinuum], Option[Boolean], Option[Boolean], Option[Boolean], Option[Boolean], Option[GcalFilter], Option[GcalDiffuser], Option[GcalShutter], Option[Duration], Option[Short]),
    telescope: (Option[OffsetP],  Option[OffsetQ]),
    smartGcalType: Option[SmartGcalType])
  {
    def toStep: Step[Instrument] =
      stepType match {

        case StepType.Bias => BiasStep(i)
        case StepType.Dark => DarkStep(i)

        case StepType.Gcal =>
          import GcalArc._
          val (continuumOpt, arOpt, cuarOpt, tharOpt, xeOpt, filterOpt, diffuserOpt, shutterOpt, exposureOpt, coaddsOpt) = gcal
          (for {
            ar   <- arOpt
            cuar <- cuarOpt
            thar <- tharOpt
            xe   <- xeOpt
            l    <- GcalLamp.fromConfig(continuumOpt, ArArc -> ar, CuArArc -> cuar, ThArArc -> thar, XeArc -> xe)
            f    <- filterOpt
            d    <- diffuserOpt
            s    <- shutterOpt
            e    <- exposureOpt
            c    <- coaddsOpt
          } yield GcalStep(i, GcalConfig(l, f, d, s, e, c))).getOrElse(sys.error("missing gcal information: " + gcal))

        case StepType.SmartGcal =>
          smartGcalType.map(t => SmartGcalStep(i, t)).getOrElse(sys.error("missing smart gcal type"))

        case StepType.Science =>
          telescope.apply2(TelescopeConfig(_, _))
            .map(ScienceStep(i, _))
            .getOrElse(sys.error("missing telescope information: " + telescope))

      }
  }

  private def oneF2Only(oid: Observation.Id, loc: Loc): MaybeConnectionIO[F2Config] =
    Statements.oneF2Only(oid, loc).maybe

  private def allF2Only(oid: Observation.Id): ConnectionIO[Loc ==>> F2Config] =
    Statements.allF2Only(oid).list.map(==>>.fromList(_))

  private def oneGmosNorthOnly(oid: Observation.Id, loc: Loc): MaybeConnectionIO[GmosNorthConfig] =
    Statements.oneGmosNorthOnly(oid, loc).maybe

  private def allGmosNorthOnly(oid: Observation.Id): ConnectionIO[Loc ==>> GmosNorthConfig] =
    Statements.allGmosNorthOnly(oid).list.map(==>>.fromList(_))

  private def oneGmosSouthOnly(oid: Observation.Id, loc: Loc): MaybeConnectionIO[GmosSouthConfig] =
    Statements.oneGmosSouthOnly(oid, loc).maybe

  private def allGmosSouthOnly(oid: Observation.Id): ConnectionIO[Loc ==>> GmosSouthConfig] =
    Statements.allGmosSouthOnly(oid).list.map(==>>.fromList(_))

  private def oneGenericOnly(oid: Observation.Id, loc: Loc): MaybeConnectionIO[GenericConfig] =
    Statements.oneGenericOnly(oid, loc).maybe

  private def allGenericOnly(oid: Observation.Id): ConnectionIO[Loc ==>> GenericConfig] =
    Statements.allGenericOnly(oid).list.map(==>>.fromList(_))

  private def selectAll[I](oid: Observation.Id, f: Observation.Id => ConnectionIO[Loc ==>> I]): ConnectionIO[Loc ==>> Step[I]] =
    for {
      ss <- selectAllEmpty(oid)
      is <- f(oid)
    } yield ss.intersectionWith(is) { (s, i) => s.as(i) } // .map { case ((l, s), i) => (l, s.as(i)) }

  object Statements {

    def delete(oid: Observation.Id, loc: Loc): Update0 =
      sql"""
        DELETE FROM step
              WHERE observation_id = $oid
                AND location       = $loc
      """.update

    def delete(oid: Observation.Id): Update0 =
      sql"""
        DELETE FROM step
              WHERE observation_id = $oid
      """.update

    def allGenericOnly(oid: Observation.Id): Query0[(Loc, GenericConfig)] =
      sql"""
        SELECT location,
               instrument
          FROM step
         WHERE observation_id = $oid
      """.query[(Loc, Instrument)].map(_.map(GenericConfig))

    def oneGenericOnly(oid: Observation.Id, loc: Loc): Query0[GenericConfig] =
      sql"""
        SELECT instrument
          FROM step
         WHERE observation_id = $oid AND location = $loc
      """.query[Instrument].map(GenericConfig)

    def allF2Only(oid: Observation.Id): Query0[(Loc, F2Config)] =
      sql"""
        SELECT s.location,
               i.disperser,
               i.exposure_time,
               i.filter,
               i.fpu,
               i.lyot_wheel,
               i.mos_preimaging,
               i.read_mode,
               i.window_cover
          FROM step s
               LEFT OUTER JOIN step_f2 i
                 ON i.step_f2_id = s.step_id
         WHERE s.observation_id = $oid
      """.query[(Loc, F2Config)]

    def oneF2Only(oid: Observation.Id, loc: Loc): Query0[F2Config] =
      sql"""
        SELECT i.disperser,
               i.exposure_time,
               i.filter,
               i.fpu,
               i.lyot_wheel,
               i.mos_preimaging,
               i.read_mode,
               i.window_cover
          FROM step s
               LEFT OUTER JOIN step_f2 i
                 ON i.step_f2_id = s.step_id
         WHERE s.observation_id = $oid AND s.location = $loc
      """.query[F2Config]

    def allGmosNorthOnly(oid: Observation.Id): Query0[(Loc, GmosNorthConfig)] =
      sql"""
        SELECT s.location,
               i.disperser,
               i.filter,
               i.fpu,
               i.stage_mode
          FROM step s
               LEFT OUTER JOIN step_gmos_north i
                 ON i.step_gmos_north_id = s.step_id
         WHERE s.observation_id = $oid
      """.query[(Loc, GmosNorthConfig)]

    def oneGmosNorthOnly(oid: Observation.Id, loc: Loc): Query0[GmosNorthConfig] =
      sql"""
        SELECT i.disperser,
               i.filter,
               i.fpu,
               i.stage_mode
          FROM step s
               LEFT OUTER JOIN step_gmos_north i
                 ON i.step_gmos_north_id = s.step_id
         WHERE s.observation_id = $oid AND s.location = $loc
      """.query[GmosNorthConfig]

    def allGmosSouthOnly(oid: Observation.Id): Query0[(Loc, GmosSouthConfig)] =
      sql"""
        SELECT s.location,
               i.disperser,
               i.filter,
               i.fpu,
               i.stage_mode
          FROM step s
               LEFT OUTER JOIN step_gmos_south i
                 ON i.step_gmos_south_id = s.step_id
         WHERE s.observation_id = $oid
      """.query[(Loc, GmosSouthConfig)]

    def oneGmosSouthOnly(oid: Observation.Id, loc: Loc): Query0[GmosSouthConfig] =
      sql"""
        SELECT i.disperser,
               i.filter,
               i.fpu,
               i.stage_mode
          FROM step s
               LEFT OUTER JOIN step_gmos_south i
                 ON i.step_gmos_south_id = s.step_id
         WHERE s.observation_id = $oid AND s.location = $loc
      """.query[GmosSouthConfig]

    def selectAllEmpty(oid: Observation.Id): Query0[(Loc, Step[Instrument])] =
      sql"""
        SELECT s.location,
               s.instrument,
               s.step_type,
               gc.continuum,
               gc.ar_arc,
               gc.cuar_arc,
               gc.thar_arc,
               gc.xe_arc,
               gc.filter,
               gc.diffuser,
               gc.shutter,
               gc.exposure_time,
               gc.coadds,
               sc.offset_p,
               sc.offset_q,
               ss.type
          FROM step s
               LEFT OUTER JOIN step_gcal sg
                  ON sg.step_gcal_id       = s.step_id
               LEFT OUTER JOIN gcal gc
                  ON gc.gcal_id            = sg.gcal_id
               LEFT OUTER JOIN step_science sc
                  ON sc.step_science_id    = s.step_id
               LEFT OUTER JOIN step_smart_gcal ss
                  ON ss.step_smart_gcal_id = s.step_id
         WHERE s.observation_id = $oid
      """.query[(Loc, StepKernel)].map(_.map(_.toStep))

    def selectOneEmpty(oid: Observation.Id, loc: Loc): Query0[Step[Instrument]] =
      sql"""
        SELECT s.instrument,
               s.step_type,
               gc.continuum,
               gc.ar_arc,
               gc.cuar_arc,
               gc.thar_arc,
               gc.xe_arc,
               gc.filter,
               gc.diffuser,
               gc.shutter,
               gc.exposure_time,
               gc.coadds,
               sc.offset_p,
               sc.offset_q,
               ss.type
          FROM step s
               LEFT OUTER JOIN step_gcal sg
                  ON sg.step_gcal_id       = s.step_id
               LEFT OUTER JOIN gcal gc
                  ON gc.gcal_id            = sg.gcal_id
               LEFT OUTER JOIN step_science sc
                  ON sc.step_science_id    = s.step_id
               LEFT OUTER JOIN step_smart_gcal ss
                  ON ss.step_smart_gcal_id = s.step_id
         WHERE s.observation_id = $oid AND s.location = $loc
      """.query[StepKernel].map(_.toStep)

    def insertF2Config(id: Int, f2: F2Config): Update0 =
      sql"""
        INSERT INTO step_f2 (
          step_f2_id,
          disperser, exposure_time, filter, fpu, lyot_wheel, mos_preimaging, read_mode, window_cover
        )
        VALUES (
          $id,
          ${f2.disperser},
          ${f2.exposureTime},
          ${f2.filter},
          ${f2.fpu},
          ${f2.lyotWheel},
          ${f2.mosPreimaging},
          ${f2.readMode},
          ${f2.windowCover})
      """.update

    def insertGmosNorthConfig(id: Int, gn: GmosNorthConfig): Update0 =
      sql"""
        INSERT INTO step_gmos_north (
          step_gmos_north_id,
          disperser, filter, fpu, stage_mode
        )
        VALUES (
          $id,
          ${gn.disperser},
          ${gn.filter},
          ${gn.fpu},
          ${gn.stageMode})
      """.update

    def insertGmosSouthConfig(id: Int, gs: GmosSouthConfig): Update0 =
      sql"""
        INSERT INTO step_gmos_south (
          step_gmos_south_id,
          disperser, filter, fpu, stage_mode
        )
        VALUES (
          $id,
          ${gs.disperser},
          ${gs.filter},
          ${gs.fpu},
          ${gs.stageMode})
      """.update

    def insertScienceSlice(id: Int, t: TelescopeConfig): Update0 =
      sql"""
        INSERT INTO step_science (step_science_id, offset_p, offset_q)
        VALUES ($id, ${t.p}, ${t.q})
      """.update

    def insertSmartGcalSlice(id: Int, t: SmartGcalType): Update0 =
      sql"""
        INSERT INTO step_smart_gcal (step_smart_gcal_id, type)
        VALUES ($id, $t :: smart_gcal_type)
      """.update

    def insertGcalStep(id: Int, gcal_id: Int): Update0 =
      sql"""
        INSERT into step_gcal (step_gcal_id, gcal_id)
        VALUES ($id, $gcal_id)
      """.update

    def insertDarkSlice(id: Int): Update0 =
      sql"""
        INSERT INTO step_dark (step_dark_id)
        VALUES ($id)
      """.update

    def insertBiasSlice(id: Int): Update0 =
      sql"""
        INSERT INTO step_bias (step_bias_id)
        VALUES ($id)
      """.update

    def insertBaseSlice(oid: Observation.Id, loc: Loc, i: InstrumentConfig, t: StepType): Update0 =
      sql"""
        INSERT INTO step (observation_id, location, instrument, step_type)
        VALUES ($oid, $loc, ${Instrument.forConfig(i)}, ${t} :: step_type)
      """.update

  }

}
