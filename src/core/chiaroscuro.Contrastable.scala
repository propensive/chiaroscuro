/*
    Chiaroscuro, version [unreleased]. Copyright 2025 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
    either express or implied. See the License for the specific language governing permissions
    and limitations under the License.
*/

package chiaroscuro

import anticipation.*
import dissonance.*
import gossamer.*
import hieroglyph.*
import rudiments.*
import spectacular.*
import vacuous.*
import wisteria.*

import scala.deriving.*

trait Contrastable:
  type Self
  def apply(left: Self, right: Self): Semblance

object Contrastable extends Derivation[[ValueType] =>> ValueType is Contrastable]:
  def nothing[ValueType]: ValueType is Contrastable = (left, right) =>
    Semblance.Identical(left.inspect)

  given Int is Contrastable as int = (left, right) =>
    if left == right then Semblance.Identical(left.show)
    else Semblance.Different(left.show, right.show, t"${math.abs(right - left)}")

  given Double is Contrastable = (left, right) =>
    given Decimalizer = Decimalizer(3)
    if left == right then Semblance.Identical(left.show)
    else
      val size = 100*(right - left)/left
      val sizeText = if size.isFinite then t"${if size > 0 then t"+" else t""}$size%" else t""
      Semblance.Different(left.show, right.show, sizeText)

  inline def general[ValueType]: ValueType is Contrastable = (left, right) =>
    if left == right then Semblance.Identical(left.inspect) else Semblance.Different(left.inspect, right.inspect)

  given Exception is Contrastable:
    def apply(left: Exception, right: Exception): Semblance =
      val leftMsg = Option(left.getMessage).fold(t"null")(_.nn.inspect)
      val rightMsg = Option(right.getMessage).fold(t"null")(_.nn.inspect)

      if left.getClass == right.getClass && leftMsg == rightMsg then Semblance.Identical(leftMsg)
      else Semblance.Different(leftMsg, rightMsg)

  given Char is Contrastable = (left, right) =>
    if left == right then Semblance.Identical(left.show) else Semblance.Different(left.show, right.show)

  given Text is Contrastable = (left, right) => compareSeq[Char](left.chars, right.chars, left, right)

  def compareSeq[ValueType: Contrastable: Similarity]
     (left: IndexedSeq[ValueType], right: IndexedSeq[ValueType], leftDebug: Text, rightDebug: Text)
          : Semblance =
    if left == right then Semblance.Identical(leftDebug) else
      val comparison = IArray.from:
        diff(left, right).rdiff(summon[Similarity[ValueType]].similar).changes.map:
          case Par(leftIndex, rightIndex, value) =>
            val label =
              if leftIndex == rightIndex then leftIndex.show
              else t"${leftIndex.show.superscript}⫽${rightIndex.show.subscript}"

            label -> Semblance.Identical(value.inspect)

          case Ins(rightIndex, value) =>
            t" ⧸${rightIndex.show.subscript}" -> Semblance.Different(t"—", value.inspect)

          case Del(leftIndex, value) =>
            t"${leftIndex.show.superscript}⧸" -> Semblance.Different(value.inspect, t"—")

          case Sub(leftIndex, rightIndex, leftValue, rightValue) =>
            val label = t"${leftIndex.show.superscript}⫽${rightIndex.show.subscript}"

            label -> leftValue.contrastWith(rightValue)

      Semblance.Breakdown(comparison, leftDebug, rightDebug)


  given [ValueType: {Contrastable, Similarity}] => IArray[ValueType] is Contrastable as iarray:
    def apply(left: IArray[ValueType], right: IArray[ValueType]): Semblance =
      compareSeq[ValueType](left.to(IndexedSeq), right.to(IndexedSeq), left.inspect, right.inspect)

  given [ValueType: {Contrastable, Similarity}] => List[ValueType] is Contrastable as list:
    def apply(left: List[ValueType], right: List[ValueType]): Semblance =
      compareSeq[ValueType](left.to(IndexedSeq), right.to(IndexedSeq), left.inspect, right.inspect)

  given [ValueType: {Contrastable, Similarity}] => Trie[ValueType] is Contrastable as trie:
    def apply(left: Trie[ValueType], right: Trie[ValueType]): Semblance =
      compareSeq[ValueType](left.to(IndexedSeq), right.to(IndexedSeq), left.inspect, right.inspect)

  inline def join[DerivationType <: Product: ProductReflection]: DerivationType is Contrastable =
    (left, right) =>
      val elements = fields(left, true): [FieldType] =>
        leftParam =>
          if leftParam == complement(right) then (label, Semblance.Identical(leftParam.inspect))
          else (label, context(leftParam, complement(right)))

      Semblance.Breakdown(elements, left.inspect, right.inspect)

  inline def split[DerivationType: SumReflection]: DerivationType is Contrastable =
    (left, right) =>
      variant(left):
        [VariantType <: DerivationType] => left =>
          complement(right).let(left.contrastWith(_)).or:
            Semblance.Different(left.inspect, right.inspect)