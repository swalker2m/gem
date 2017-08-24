// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem.horizons

import gem.math.{ Coordinates, Ephemeris }

import cats.tests.CatsSuite

import java.time.{ Instant, LocalDateTime, ZoneOffset }
import java.time.format.DateTimeFormatter

import scala.collection.immutable.TreeMap
import scala.io.Source


/** Not really a spec per se, but rather a way to exercise the horizons parser
  * with a few fixed examples and get a sense of whether it works.
  */
@SuppressWarnings(Array("org.wartremover.warts.Equals"))
final class HorizonsParserSpec extends CatsSuite {

  import HorizonsParserSpec._

  test("Must parse borrelly") {

    val head = eph(
      "2017-Oct-31 17:00:00.000" -> "15 31 44.2394 -09 15 58.395",
      "2017-Oct-31 17:04:00.000" -> "15 31 44.3565 -09 15 59.092",
      "2017-Oct-31 17:08:00.000" -> "15 31 44.4735 -09 15 59.789"
    )

    val tail = eph(
      "2017-Nov-01 16:52:00.000" -> "15 32 26.6860 -09 20 07.012",
      "2017-Nov-01 16:56:00.000" -> "15 32 26.8033 -09 20 07.705",
      "2017-Nov-01 17:00:00.000" -> "15 32 26.9206 -09 20 08.397"
    )

    checkEph("borrelly", head, tail)
  }

  test("Must parse ceres") {

    val head = eph(
      "2017-Oct-31 17:00:00.000" -> "09 01 55.3295 +22 33 52.937",
      "2017-Oct-31 17:04:00.000" -> "09 01 55.5195 +22 33 52.862",
      "2017-Oct-31 17:08:00.000" -> "09 01 55.7096 +22 33 52.787"
    )

    val tail = eph(
      "2017-Nov-01 16:52:00.000" -> "09 03 02.9429 +22 33 35.969",
      "2017-Nov-01 16:56:00.000" -> "09 03 03.1304 +22 33 35.907",
      "2017-Nov-01 17:00:00.000" -> "09 03 03.3179 +22 33 35.846"
    )

    checkEph("ceres", head, tail)
  }

  test("Must parse enceladus") {

    val head = eph(
      "2017-Oct-31 17:00:00.000" -> "17 35 31.6041 -22 19 47.693",
      "2017-Oct-31 17:04:00.000" -> "17 35 31.6425 -22 19 47.727",
      "2017-Oct-31 17:08:00.000" -> "17 35 31.6810 -22 19 47.759"
    )

    val tail = eph(
      "2017-Nov-01 16:52:00.000" -> "17 35 57.9508 -22 19 55.845",
      "2017-Nov-01 16:56:00.000" -> "17 35 58.0218 -22 19 56.089",
      "2017-Nov-01 17:00:00.000" -> "17 35 58.0925 -22 19 56.333"
    )

    checkEph("enceladus", head, tail)
  }

  test("Must parse mars") {

    val head = eph(
      "2017-Oct-31 17:00:00.000" -> "12 21 38.4418 -01 06 03.706",
      "2017-Oct-31 17:04:00.000" -> "12 21 38.8247 -01 06 06.221",
      "2017-Oct-31 17:08:00.000" -> "12 21 39.2077 -01 06 08.737"
    )

    val tail = eph(
      "2017-Nov-01 16:52:00.000" -> "12 23 56.5700 -01 21 04.169",
      "2017-Nov-01 16:56:00.000" -> "12 23 56.9529 -01 21 06.682",
      "2017-Nov-01 17:00:00.000" -> "12 23 57.3359 -01 21 09.196"
    )

    checkEph("mars", head, tail)
  }

  private def checkEph(name: String,
               head: TreeMap[Instant, Coordinates],
               tail: TreeMap[Instant, Coordinates]): org.scalatest.Assertion = {

    val e = Horizons.parseResponse(load(name)).getOrElse(Ephemeris.Empty)

    // This works but the error message isn't helpful when it fails.  There
    // should be a way to combine shouldEqual assertions but it apparently isn't
    // documented.
    assert(
      (e.toMap.size                == 361 ) &&
      (e.toMap.to(head.lastKey)    == head) &&
      (e.toMap.from(tail.firstKey) == tail)
    )
  }
}

object HorizonsParserSpec {
  private val TimeFormat = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss.SSS")

  private def load(n: String): String =
    Source.fromInputStream(getClass.getResourceAsStream(s"$n.eph")).mkString

  private def time(s: String): Instant =
    LocalDateTime.parse(s, TimeFormat).toInstant(ZoneOffset.UTC)

  private def coords(s: String): Coordinates =
    Coordinates.parse(s).getOrElse(Coordinates.Zero)

  private def eph(elems: (String, String)*): TreeMap[Instant, Coordinates] =
    TreeMap(elems.map { case (i, c) => time(i) -> coords(c) }: _*)


}
