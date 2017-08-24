// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem.horizons

import gem.math.Ephemeris

/**
  *
  */
object Horizons {

  // Horizons response parser.  This isn't really a general purpose parser so it
  // is left as an implementation module.
  private object parsers {

    import atto._
    import Atto._
    import cats.implicits._
    import gem.parser.CoordinateParsers._
    import gem.parser.MiscParsers._
    import gem.parser.TimeParsers._

    val soe           = string("$$SOE")
    val eoe           = string("$$EOE")
    val skipPrefix    = manyUntil(anyChar, soe)  ~> verticalWhitespace
    val skipEol       = skipMany(noneOf("\n\r")) ~> verticalWhitespace
    val solarPresence = oneOf("*CNA ").void namedOpaque "solarPresence"
    val lunarPresence = oneOf("mrts ").void namedOpaque "lunarPresence"

    val utc: Parser[java.time.Instant] =
      instantUTC(
        genYMD(monthMMM, hyphen) named "yyyy-MMM-dd",
        genLocalTime(colon)
      )

    val element: Parser[Ephemeris.Element] =
      for {
        _ <- space
        i <- utc           <~ space
        _ <- solarPresence
        _ <- lunarPresence <~ spaces1
        c <- coordinates   <~ skipEol
      } yield (i, c)

    val ephemeris: Parser[Ephemeris] =
      skipPrefix ~> (
        many(element).map(Ephemeris.fromFoldable[List]) <~ eoe
      )
  }

  def parseResponse(s: String): Option[Ephemeris] = {
    import atto.syntax.parser._

    parsers.ephemeris.parseOnly(s).option
  }
}
