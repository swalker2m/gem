package gem

import gem.config._

import scalaz.ISet

// The members of this package are generated from database tables, which are the source of truth.
// See project/gen2.scala for details. Associations with other model types, as needed, are provided
// here as implicit classes wrapping the generated companion objects.
package object enum {

  /** Add mapping from InstrumentConfig to Instrument. */
  implicit class InstrumentCompanionOps(companion: Instrument.type) {
    def forConfig(c: InstrumentConfig): Instrument =
      c match {
        case F2Config(_, _, _, _, _, _, _, _) => Instrument.Flamingos2
        case _: GmosNorthConfig               => Instrument.GmosN
        case _: GmosSouthConfig               => Instrument.GmosS
        case GenericConfig(i)                 => i
      }
  }

  /** Add mapping from Step to StepType. */
  implicit class StepTypeCompanionOps(companion: StepType.type) {
    def forStep(s: Step[_]): StepType =
      s match {
        case BiasStep(_)         => StepType.Bias
        case DarkStep(_)         => StepType.Dark
        case GcalStep(_, _)      => StepType.Gcal
        case ScienceStep(_, _)   => StepType.Science
        case SmartGcalStep(_, _) => StepType.SmartGcal
      }
  }

  /** Add fold on SmartGcalType. */
  implicit class SmartGcalTypeOps(t: SmartGcalType) {
    def fold[X](lamp: GcalLampType => X, baseline: GcalBaselineType => X): X =
      t match {
        case SmartGcalType.Arc           => lamp(GcalLampType.Arc)
        case SmartGcalType.Flat          => lamp(GcalLampType.Flat)
        case SmartGcalType.DayBaseline   => baseline(GcalBaselineType.Day)
        case SmartGcalType.NightBaseline => baseline(GcalBaselineType.Night)
      }
  }

  /** Add GMOS GmosAmpCount options for the two detector types. */
  implicit class GmosDetectorOps(d: GmosDetector) {
    import GmosAmpCount._

    def ampCountOptions: ISet[GmosAmpCount] =
      d match {
        case GmosDetector.E2V       => ISet.fromList(List(Three, Six))
        case GmosDetector.HAMAMATSU => ISet.fromList(List(Six, Twelve))
      }
  }
}
