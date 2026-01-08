/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.plan.volcano;  // 定义包名，位于 org.apache.calcite.plan.volcano 包下，这是 Volcano 优化器的核心包

import org.apache.calcite.plan.RelOptCost;  // 导入 RelOptCost 接口，这是 Calcite 中成本模型的顶层接口，定义了成本比较和计算的基本方法
import org.apache.calcite.plan.RelOptCostFactory;  // 导入 RelOptCostFactory 接口，用于创建成本对象的工厂接口
import org.apache.calcite.plan.RelOptUtil;  // 导入 RelOptUtil 工具类，提供一些优化器相关的常量和方法，如 EPSILON 用于浮点数比较

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入可空性注解，用于标记可能为 null 的参数

import java.util.Objects;  // 导入 Objects 工具类，用于生成 hashCode 和 equals 方法

/**
 * <code>VolcanoCost</code> represents the cost of a plan node.
 * VolcanoCost 表示计划节点的成本，是 Volcano 优化器中的核心成本模型实现
 *
 * <p>This class is immutable: none of the methods modify any member
 * variables.
 * 这个类是不可变的：所有方法都不会修改任何成员变量，确保线程安全和成本计算的确定性
 */
class VolcanoCost implements RelOptCost {  // 定义 VolcanoCost 类，实现 RelOptCost 接口，表示 Volcano 优化器的成本模型
  //~ Static fields/initializers ---------------------------------------------
  // 静态字段和初始化块区域，定义了 VolcanoCost 的特殊常量值和工厂实例

  static final VolcanoCost INFINITY =  // 定义无穷大成本常量，表示不可接受或无法实现的计划成本
      new VolcanoCost(  // 创建一个新的 VolcanoCost 实例，表示无穷大成本
          Double.POSITIVE_INFINITY,  // 行数为正无穷大，表示该计划会产生无限的行数
          Double.POSITIVE_INFINITY,  // CPU成本为正无穷大，表示该计划的计算成本无限
          Double.POSITIVE_INFINITY) {  // IO成本为正无穷大，表示该计划的IO成本无限
        @Override public String toString() {  // 重写 toString 方法，提供自定义的字符串表示
          return  RelOptCost.toString(Double.POSITIVE_INFINITY);  // 使用 RelOptCost 的工具方法生成无穷大的字符串表示
        }
      };

  static final VolcanoCost HUGE =  // 定义巨大成本常量，表示非常高但不是无穷大的成本
      new VolcanoCost(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE) {  // 创建一个所有维度都是最大值的成本对象
        @Override public String toString() {  // 重写 toString 方法，提供自定义的字符串表示
          return RelOptCost.toString(Double.MAX_VALUE);  // 使用 RelOptCost 的工具方法生成最大值的字符串表示
        }
      };

  static final VolcanoCost ZERO =  // 定义零成本常量，表示没有任何成本的计划
      new VolcanoCost(0.0, 0.0, 0.0) {  // 创建一个所有维度都是零的成本对象
        @Override public String toString() {  // 重写 toString 方法，提供自定义的字符串表示
          return RelOptCost.toString(0.0);  // 使用 RelOptCost 的工具方法生成零的字符串表示
        }
      };

  static final VolcanoCost TINY =  // 定义极小成本常量，表示非常小的成本，用于某些特殊场景
      new VolcanoCost(1.0, 1.0, 0.0) {  // 创建一个行数和CPU为1，IO为0的成本对象，表示最小的非零成本
        @Override public String toString() {  // 重写 toString 方法，提供自定义的字符串表示
          return RelOptCost.toString(1.0);  // 使用 RelOptCost 的工具方法生成1.0的字符串表示
        }
      };

  public static final RelOptCostFactory FACTORY = new Factory();  // 定义成本工厂的静态实例，用于创建 VolcanoCost 对象，是整个优化器创建成本对象的统一入口

  //~ Instance fields --------------------------------------------------------
  // 实例字段区域，定义了成本模型的三个核心维度

  final double cpu;  // CPU成本，表示执行该计划节点所需的CPU计算资源消耗，是一个不可变的final字段
  final double io;  // IO成本，表示执行该计划节点所需的磁盘I/O操作成本，是一个不可变的final字段
  final double rowCount;  // 行数，表示该计划节点产生的结果行数，是一个不可变的final字段，也是成本比较的主要依据

  //~ Constructors -----------------------------------------------------------
  // 构造方法区域

  VolcanoCost(double rowCount, double cpu, double io) {  // 构造方法，创建一个新的 VolcanoCost 对象
    this.rowCount = rowCount;  // 初始化行数字段，表示该计划节点产生的行数
    this.cpu = cpu;  // 初始化CPU成本字段，表示该计划节点的CPU计算成本
    this.io = io;  // 初始化IO成本字段，表示该计划节点的IO操作成本
  }

  //~ Methods ----------------------------------------------------------------
  // 方法区域，定义了成本比较、计算和访问的各种方法

  @Override public double getCpu() {  // 实现 RelOptCost 接口的方法，获取CPU成本
    return cpu;  // 返回当前对象的CPU成本值
  }

  @Override public boolean isInfinite() {  // 实现 RelOptCost 接口的方法，判断当前成本是否为无穷大
    return (this == INFINITY)  // 如果当前对象就是 INFINITY 常量，返回 true
        || (this.rowCount == Double.POSITIVE_INFINITY)  // 或者行数为正无穷大，返回 true
        || (this.cpu == Double.POSITIVE_INFINITY)  // 或者CPU成本为正无穷大，返回 true
        || (this.io == Double.POSITIVE_INFINITY);  // 或者IO成本为正无穷大，返回 true
  }

  @Override public double getIo() {  // 实现 RelOptCost 接口的方法，获取IO成本
    return io;  // 返回当前对象的IO成本值
  }

  @Override public boolean isLe(RelOptCost other) {  // 实现 RelOptCost 接口的方法，判断当前成本是否小于等于另一个成本
    VolcanoCost that = (VolcanoCost) other;  // 将另一个成本对象转换为 VolcanoCost 类型
    if (true) {  // 当前配置下使用简化的比较逻辑，只比较行数
      return this == that  // 如果是同一个对象，返回 true
          || this.rowCount <= that.rowCount;  // 或者当前行数小于等于另一个行数，返回 true
    }
    // 完整的比较逻辑（当前未使用），需要同时满足所有维度都小于等于
    return (this == that)  // 如果是同一个对象，返回 true
        || ((this.rowCount <= that.rowCount)  // 并且行数小于等于
        && (this.cpu <= that.cpu)  // 并且CPU成本小于等于
        && (this.io <= that.io));  // 并且IO成本小于等于
  }

  @Override public boolean isLt(RelOptCost other) {  // 实现 RelOptCost 接口的方法，判断当前成本是否严格小于另一个成本
    if (true) {  // 当前配置下使用简化的比较逻辑，只比较行数
      VolcanoCost that = (VolcanoCost) other;  // 将另一个成本对象转换为 VolcanoCost 类型
      return this.rowCount < that.rowCount;  // 返回当前行数是否严格小于另一个行数
    }
    // 完整的比较逻辑（当前未使用），使用小于等于且不等于来实现严格小于
    return isLe(other) && !equals(other);  // 小于等于且不等于，即严格小于
  }

  @Override public double getRows() {  // 实现 RelOptCost 接口的方法，获取行数
    return rowCount;  // 返回当前对象的行数值
  }

  @Override public int hashCode() {  // 重写 Object 的 hashCode 方法，用于哈希表等数据结构
    return Objects.hash(rowCount, cpu, io);  // 使用 Objects.hash 方法基于三个字段生成哈希码
  }

  @SuppressWarnings("NonOverridingEquals")  // 抑制编译器警告，因为这个 equals 方法不是重写 Object 的 equals
  @Override public boolean equals(RelOptCost other) {  // 实现 RelOptCost 接口的 equals 方法，比较两个成本对象是否相等
    return this == other  // 如果是同一个对象引用，返回 true
        || other instanceof VolcanoCost  // 或者另一个对象是 VolcanoCost 类型
        && (this.rowCount == ((VolcanoCost) other).rowCount)  // 并且行数相等
        && (this.cpu == ((VolcanoCost) other).cpu)  // 并且CPU成本相等
        && (this.io == ((VolcanoCost) other).io);  // 并且IO成本相等
  }

  @Override public boolean equals(@Nullable Object obj) {  // 重写 Object 的 equals 方法，用于通用对象比较
    if (obj instanceof VolcanoCost) {  // 如果对象是 VolcanoCost 类型
      return equals((VolcanoCost) obj);  // 调用 RelOptCost 接口的 equals 方法进行比较
    }
    return false;  // 否则返回 false
  }

  @Override public boolean isEqWithEpsilon(RelOptCost other) {  // 实现 RelOptCost 接口的方法，使用容差比较两个成本是否相等
    if (!(other instanceof VolcanoCost)) {  // 如果另一个对象不是 VolcanoCost 类型
      return false;  // 返回 false
    }
    VolcanoCost that = (VolcanoCost) other;  // 将另一个对象转换为 VolcanoCost 类型
    return (this == that)  // 如果是同一个对象，返回 true
        || ((Math.abs(this.rowCount - that.rowCount) < RelOptUtil.EPSILON)  // 或者行数差值小于容差 EPSILON
        && (Math.abs(this.cpu - that.cpu) < RelOptUtil.EPSILON)  // 并且CPU成本差值小于容差 EPSILON
        && (Math.abs(this.io - that.io) < RelOptUtil.EPSILON));  // 并且IO成本差值小于容差 EPSILON
  }

  @Override public RelOptCost minus(RelOptCost other) {  // 实现 RelOptCost 接口的方法，计算当前成本减去另一个成本的差值
    if (this == INFINITY) {  // 如果当前成本是无穷大
      return this;  // 返回无穷大本身
    }
    VolcanoCost that = (VolcanoCost) other;  // 将另一个成本对象转换为 VolcanoCost 类型
    return new VolcanoCost(  // 返回一个新的 VolcanoCost 对象，表示差值
        this.rowCount - that.rowCount,  // 行数相减
        this.cpu - that.cpu,  // CPU成本相减
        this.io - that.io);  // IO成本相减
  }

  @Override public RelOptCost multiplyBy(double factor) {  // 实现 RelOptCost 接口的方法，将当前成本乘以一个因子
    if (this == INFINITY) {  // 如果当前成本是无穷大
      return this;  // 返回无穷大本身
    }
    return new VolcanoCost(rowCount * factor, cpu * factor, io * factor);  // 返回一个新的 VolcanoCost 对象，所有维度都乘以因子
  }

  @Override public double divideBy(RelOptCost cost) {  // 实现 RelOptCost 接口的方法，计算当前成本除以另一个成本的比值
    // Compute the geometric average of the ratios of all of the factors
    // 计算所有非零且有限因子的比值的几何平均数
    // which are non-zero and finite.
    // 这些因子是非零且有限的
    VolcanoCost that = (VolcanoCost) cost;  // 将另一个成本对象转换为 VolcanoCost 类型
    double d = 1;  // 初始化乘积为1
    double n = 0;  // 初始化有效因子数量为0
    if ((this.rowCount != 0)  // 如果当前行数不为0
        && !Double.isInfinite(this.rowCount)  // 且当前行数不是无穷大
        && (that.rowCount != 0)  // 且另一个行数不为0
        && !Double.isInfinite(that.rowCount)) {  // 且另一个行数不是无穷大
      d *= this.rowCount / that.rowCount;  // 将行数比值乘入乘积
      ++n;  // 有效因子数量加1
    }
    if ((this.cpu != 0)  // 如果当前CPU成本不为0
        && !Double.isInfinite(this.cpu)  // 且当前CPU成本不是无穷大
        && (that.cpu != 0)  // 且另一个CPU成本不为0
        && !Double.isInfinite(that.cpu)) {  // 且另一个CPU成本不是无穷大
      d *= this.cpu / that.cpu;  // 将CPU成本比值乘入乘积
      ++n;  // 有效因子数量加1
    }
    if ((this.io != 0)  // 如果当前IO成本不为0
        && !Double.isInfinite(this.io)  // 且当前IO成本不是无穷大
        && (that.io != 0)  // 且另一个IO成本不为0
        && !Double.isInfinite(that.io)) {  // 且另一个IO成本不是无穷大
      d *= this.io / that.io;  // 将IO成本比值乘入乘积
      ++n;  // 有效因子数量加1
    }
    if (n == 0) {  // 如果没有有效的因子
      return 1.0;  // 返回1.0，表示没有差异
    }
    return Math.pow(d, 1 / n);  // 计算几何平均数，即乘积的n次方根
  }

  @Override public RelOptCost plus(RelOptCost other) {  // 实现 RelOptCost 接口的方法，计算当前成本加上另一个成本的和
    VolcanoCost that = (VolcanoCost) other;  // 将另一个成本对象转换为 VolcanoCost 类型
    if ((this == INFINITY) || (that == INFINITY)) {  // 如果当前成本或另一个成本是无穷大
      return INFINITY;  // 返回无穷大
    }
    return new VolcanoCost(  // 返回一个新的 VolcanoCost 对象，表示和
        this.rowCount + that.rowCount,  // 行数相加
        this.cpu + that.cpu,  // CPU成本相加
        this.io + that.io);  // IO成本相加
  }

  @Override public String toString() {  // 重写 Object 的 toString 方法，提供成本的字符串表示
    return "{" + rowCount + " rows, " + cpu + " cpu, " + io + " io}";  // 返回格式化的字符串，显示行数、CPU成本和IO成本
  }

  /** Implementation of {@link org.apache.calcite.plan.RelOptCostFactory}
   * that creates {@link org.apache.calcite.plan.volcano.VolcanoCost}s.
   * RelOptCostFactory 接口的实现类，用于创建 VolcanoCost 对象
   * 这是整个优化器创建成本对象的统一工厂入口
   */
  private static class Factory implements RelOptCostFactory {  // 定义私有静态内部类 Factory，实现 RelOptCostFactory 接口
    @Override public RelOptCost makeCost(double dRows, double dCpu, double dIo) {  // 实现工厂方法，创建指定参数的成本对象
      return new VolcanoCost(dRows, dCpu, dIo);  // 返回一个新的 VolcanoCost 对象，使用指定的行数、CPU成本和IO成本
    }

    @Override public RelOptCost makeHugeCost() {  // 实现工厂方法，创建巨大成本对象
      return VolcanoCost.HUGE;  // 返回预定义的 HUGE 常量
    }

    @Override public RelOptCost makeInfiniteCost() {  // 实现工厂方法，创建无穷大成本对象
      return VolcanoCost.INFINITY;  // 返回预定义的 INFINITY 常量
    }

    @Override public RelOptCost makeTinyCost() {  // 实现工厂方法，创建极小成本对象
      return VolcanoCost.TINY;  // 返回预定义的 TINY 常量
    }

    @Override public RelOptCost makeZeroCost() {  // 实现工厂方法，创建零成本对象
      return VolcanoCost.ZERO;  // 返回预定义的 ZERO 常量
    }
  }
}
