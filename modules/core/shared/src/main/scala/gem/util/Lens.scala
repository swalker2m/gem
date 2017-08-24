// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem.util

import cats.data.State

// embarrassigly minimal lens implementation to avoid a lib conflict
final case class Lens[A, B](set: (A, B) => A, get: A => B) {

  def andThen[C](bc: Lens[B, C]): Lens[A, C] =
    Lens((a, c) => set(a, bc.set(get(a), c)), a => bc.get(get(a)))

  def >=>[C](bc: Lens[B, C]): Lens[A, C] =
    andThen(bc)

  def compose[C](bc: Lens[C, A]): Lens[C, B] =
    bc andThen this

  def modify(a: A, f: B => B): A =
    set(a, f(get(a)))

  def xmapA[AA](f: AA => A, g: A => AA): Lens[AA, B] =
    Lens((aa, b) => g(set(f(aa), b)), aa => get(f(aa)))

  def xmapB[BB](f: BB => B, g: B => BB): Lens[A, BB] =
    Lens((a, bb) => set(a, f(bb)), a => g(get(a)))

  def mods(f: B => B): State[A, B] =
    State { a =>
      val b  = get(a)
      val bʹ = f(b)
      val aʹ = set(a, bʹ)
      (aʹ, bʹ)
    }

  def %=(f: B => B): State[A, B] =
    mods(f)

  def :=(b: => B): State[A, B] =
    mods(_ => b)

}

object Lens {
  type @>[A,B] = Lens[A,B]
}