// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem.syntax

import scala.collection.immutable.TreeMap

final class TreeMapOps[A, B](val self: TreeMap[A, B]) extends AnyVal {

  /** Merge two maps with the same key type according to a function that
    * combines their values.  Only elements that appear in both maps are
    * included in the final result.
    */
  def merge[C, D](that: Map[A, C])(f: (B, C) => D)(implicit ordering: Ordering[A]): TreeMap[A, D] =
    self.foldLeft(TreeMap.empty[A, D]) { case (m, (a, b)) =>
      that.get(a).fold(m)(c => m.updated(a, f(b, c)))
    }

}

trait ToTreeMapOps {
  implicit def ToTreeMapOps[A, B](m: TreeMap[A, B]): TreeMapOps[A, B] = new TreeMapOps(m)
}

object map extends ToTreeMapOps