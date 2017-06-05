package gem
package config

import gem.enum._

import java.time.Duration

sealed trait SmartGcalKey

sealed abstract class InstrumentConfig extends Product with Serializable {
  def smartGcalKey: Option[SmartGcalKey] =
    this match {
      case f2: F2Config        => Some(f2.key)
      case gn: GmosNorthConfig => Some(gn.key)
      case gs: GmosSouthConfig => Some(gs.key)
      case GenericConfig(_)    => None
    }

  def instrument: Instrument = {
    import gem.enum.Instrument._

    this match {
      case _: F2Config        => Flamingos2
      case _: GmosNorthConfig => GmosN
      case _: GmosSouthConfig => GmosS
      case GenericConfig(i)   => i
    }
  }
}

final case class F2SmartGcalKey(
  disperser: F2Disperser,
  filter:    F2Filter,
  fpu:       F2FpUnit
) extends SmartGcalKey

final case class F2Config(
  disperser:     F2Disperser,
  exposureTime:  Duration,
  filter:        F2Filter,
  fpu:           F2FpUnit,
  lyotWheel:     F2LyotWheel,
  mosPreimaging: Boolean,
  readMode:      F2ReadMode,
  windowCover:   F2WindowCover
) extends InstrumentConfig {

  def key: F2SmartGcalKey =
    F2SmartGcalKey(disperser, filter, fpu)
}

final case class GmosNorthSmartGcalKey(
  disperser: Option[GmosNorthDisperser]
) extends SmartGcalKey

final case class GmosNorthConfig(
  disperser:    Option[GmosNorthDisperser],
  filter:       Option[GmosNorthFilter],
  fpu:          Option[GmosNorthFpu],
  stageMode:    GmosNorthStageMode
) extends InstrumentConfig {

  def key: GmosNorthSmartGcalKey =
    GmosNorthSmartGcalKey(disperser)
}

final case class GmosSouthSmartGcalKey(
  disperser: Option[GmosSouthDisperser]
) extends SmartGcalKey

final case class GmosSouthConfig(
  disperser:    Option[GmosSouthDisperser],
  filter:       Option[GmosSouthFilter],
  fpu:          Option[GmosSouthFpu],
  stageMode:    GmosSouthStageMode
) extends InstrumentConfig {

  def key: GmosSouthSmartGcalKey =
    GmosSouthSmartGcalKey(disperser)
}

// TODO: temporary, until all instruments are supported
case class GenericConfig(i: Instrument) extends InstrumentConfig
