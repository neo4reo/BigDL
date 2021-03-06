/*
 * Licensed to Intel Corporation under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * Intel Corporation licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intel.analytics.bigdl.optim

import com.intel.analytics.bigdl.Criterion
import com.intel.analytics.bigdl.nn.abstractnn.Activity
import com.intel.analytics.bigdl.tensor.Tensor
import com.intel.analytics.bigdl.tensor.TensorNumericMath.TensorNumeric

import scala.reflect.ClassTag

trait ValidationMethod[T] extends Serializable {
  def apply(output: Activity, target: Activity, criterion: Criterion[T] = null): ValidationResult

  protected def format(): String

  override def toString(): String = format()
}

trait ValidationResult extends Serializable {

  def result(): (Float, Int) // (Result, Count)

  // scalastyle:off methodName
  def +(other: ValidationResult): ValidationResult

  // scalastyle:on methodName

  protected def format(): String

  override def toString(): String = format()
}

class AccuracyResult(private var correct: Int, private var count: Int)
  extends ValidationResult {

  override def result(): (Float, Int) = (correct.toFloat, count)

  // scalastyle:off methodName
  override def +(other: ValidationResult): ValidationResult = {
    val otherResult = other.asInstanceOf[AccuracyResult]
    this.correct += otherResult.correct
    this.count += otherResult.count
    this
  }

  // scalastyle:on methodName

  override protected def format(): String = {
    s"Accuracy(correct: $correct, count: $count, accuracy: ${correct.toDouble / count})"
  }

  override def equals(obj: Any): Boolean = {
    if (obj == null) {
      return false
    }
    if (!obj.isInstanceOf[AccuracyResult]) {
      return false
    }
    val other = obj.asInstanceOf[AccuracyResult]
    if (this.eq(other)) {
      return true
    }
    this.correct == other.correct && this.count == other.count
  }

  override def hashCode(): Int = {
    val seed = 37
    var hash = 1
    hash = hash * seed + this.correct
    hash = hash * seed + this.count
    hash
  }
}

class Top1Accuracy[T] extends ValidationMethod[T] {
  override def apply(output: Activity, target: Activity, criterion: Criterion[T] = null):
  ValidationResult = {
    var correct = 0
    var count = 0

    val _output = output.asInstanceOf[Tensor[T]]
    val _target = target.asInstanceOf[Tensor[T]]
    if (_output.dim() == 2) {
      _output.max(2)._2.squeeze().map(_target, (a, b) => {
        if (a == b) {
          correct += 1
        }
        a
      })
      count += _output.size(1)
    } else if (_output.dim == 1) {
      require(_target.size(1) == 1)
      _output.max(1)._2.map(_target, (a, b) => {
        if (a == b) {
          correct += 1
        }
        a
      })
      count += 1
    } else {
      throw new IllegalArgumentException
    }

    new AccuracyResult(correct, count)
  }

  override def format(): String = "top1 accuracy"
}

class Top5Accuracy[T] extends ValidationMethod[T] {
  override def apply(output: Activity, target: Activity, criterion: Criterion[T] = null):
  AccuracyResult = {
    val _output = output.asInstanceOf[Tensor[T]]
    val _target = target.asInstanceOf[Tensor[T]]
    var correct = 0
    var count = 0
    if (_output.dim() == 2) {
      val indices = _output.topk(5, 2, false)._2
      var i = 1
      while (i <= _output.size(1)) {
        if (indices.valueAt(i, 1) == _target.valueAt(i)
          || indices.valueAt(i, 2) == _target.valueAt(i)
          || indices.valueAt(i, 3) == _target.valueAt(i)
          || indices.valueAt(i, 4) == _target.valueAt(i)
          || indices.valueAt(i, 5) == _target.valueAt(i)) {
          correct += 1
        }
        i += 1
      }
      count += _output.size(1)
    } else if (_output.dim == 1) {
      require(_target.size(1) == 1)
      val indices = _output.topk(5, 1, false)._2
      if (indices.valueAt(1) == _target.valueAt(1) || indices.valueAt(2) == _target.valueAt(1)
        || indices.valueAt(3) == _target.valueAt(1) || indices.valueAt(4) == _target.valueAt(1)
        || indices.valueAt(5) == _target.valueAt(1)) {
        correct += 1
      }
      count += 1
    } else {
      throw new IllegalArgumentException
    }

    new AccuracyResult(correct, count)
  }

  override def format(): String = "top5 accuracy"
}

class LossResult(private var loss: Float, private var count: Int)
  extends ValidationResult {

  override def result(): (Float, Int) = (loss.toFloat, count)

  // scalastyle:off methodName
  override def +(other: ValidationResult): ValidationResult = {
    val otherResult = other.asInstanceOf[LossResult]
    this.loss += otherResult.loss
    this.count += otherResult.count
    this
  }

  // scalastyle:on methodName

  override protected def format(): String = {
    s"(Loss: $loss, count: $count, Average Loss: ${loss.toFloat / count})"
  }

  override def equals(obj: Any): Boolean = {
    if (obj == null) {
      return false
    }
    if (!obj.isInstanceOf[LossResult]) {
      return false
    }
    val other = obj.asInstanceOf[LossResult]
    if (this.eq(other)) {
      return true
    }
    this.loss == other.loss && this.count == other.count
  }

  override def hashCode(): Int = {
    val seed = 37
    var hash = 1
    hash = hash * seed + this.loss.toInt
    hash = hash * seed + this.count
    hash
  }
}

class Loss[@specialized(Float, Double)T: ClassTag]()
(implicit ev: TensorNumeric[T]) extends ValidationMethod[T] {
  override def apply(output: Activity, target: Activity, criterion: Criterion[T]): LossResult = {
    val _output = output.asInstanceOf[Tensor[T]]
    val _target = target.asInstanceOf[Tensor[T]]
    val loss = ev.toType[Float](criterion.forward(_output, _target))
    val count = 1

    new LossResult(loss, count)
  }

  override def format(): String = "language model Loss "
}
