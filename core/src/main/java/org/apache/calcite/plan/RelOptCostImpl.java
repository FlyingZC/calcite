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
package org.apache.calcite.plan; // 声明该类所属的包，org.apache.calcite.plan包包含了Calcite查询优化器的核心类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空类型注解，用于标记可能为null的参数或返回值

/**
 * RelOptCostImpl provides a default implementation for the {@link RelOptCost} // 类名：RelOptCostImpl，作用：提供RelOptCost接口的默认实现
 * interface. It it defined in terms of a single scalar quantity; somewhat // 它通过单个标量值来定义成本；某种程度上
 * arbitrarily, it returns this scalar for rows processed and zero for both CPU // 它将此标量值作为处理的行数返回，CPU和I/O都返回零
 * and I/O. // 这个实现简化了成本模型，只考虑行数，不考虑CPU和I/O成本
 * 
 * 类作用详解：
 * 这是Calcite查询优化器中用于表示关系表达式成本的一个默认实现类。
 * 在SQL查询优化过程中，优化器需要评估不同执行计划的成本，从而选择最优的执行计划。
 * 成本通常由三个维度组成：处理的行数（rows）、CPU使用量（cpu）和I/O操作量（io）。
 * 
 * RelOptCostImpl采用简化的成本模型：
 * 1. 只使用一个标量值（value）来表示成本
 * 2. 这个值直接对应于getRows()方法返回的行数
 * 3. CPU和I/O成本始终返回0
 * 
 * 这种简化模型适合：
 * - 需要简单成本估计的场景
 * - 行数是主要考虑因素的场景
 * - 快速原型开发或测试
 * 
 * 注意：这是一个简化的实现，生产环境中通常会使用更复杂的VolcanoCost实现，
 * 该实现会综合考虑行数、CPU和I/O三个维度。
 */
public class RelOptCostImpl implements RelOptCost { // 类声明：RelOptCostImpl实现了RelOptCost接口，表示关系表达式的成本
  public static final RelOptCostFactory FACTORY = new Factory(); // 静态常量：成本工厂，用于创建RelOptCostImpl实例，这是一个工厂模式的实现

  //~ Instance fields -------------------------------------------------------- // 分隔符：实例字段区域开始

  private final double value; // 实例变量：成本值，使用double类型存储，final表示该值在构造后不可修改，这是整个成本模型的核心标量值

  //~ Constructors ----------------------------------------------------------- // 分隔符：构造方法区域开始

  public RelOptCostImpl(double value) { // 构造方法：创建一个RelOptCostImpl实例，参数value表示成本值
    this.value = value; // 将传入的value参数赋值给实例变量value，初始化成本值
  }

  //~ Methods ---------------------------------------------------------------- // 分隔符：方法区域开始

  // implement RelOptCost // 注释：实现RelOptCost接口的方法
  @Override public double getRows() { // 方法：获取处理的行数，这是成本的主要维度，返回value值
    return value; // 返回存储的成本值作为行数，在这个简化实现中，成本值就是行数
  }

  // implement RelOptCost // 注释：实现RelOptCost接口的方法
  @Override public double getIo() { // 方法：获取I/O成本，表示磁盘I/O操作的估计成本
    return 0; // 返回0，表示在这个简化实现中不考虑I/O成本
  }

  // implement RelOptCost // 注释：实现RelOptCost接口的方法
  @Override public double getCpu() { // 方法：获取CPU成本，表示CPU计算资源的估计成本
    return 0; // 返回0，表示在这个简化实现中不考虑CPU成本
  }

  // implement RelOptCost // 注释：实现RelOptCost接口的方法
  @Override public boolean isInfinite() { // 方法：判断成本是否为无穷大，用于标记不可达或极差的执行计划
    return Double.isInfinite(value); // 使用Double.isInfinite检查value是否为正无穷或负无穷
  }

  // implement RelOptCost // 注释：实现RelOptCost接口的方法
  @Override public boolean isLe(RelOptCost other) { // 方法：判断当前成本是否小于或等于另一个成本，用于成本比较
    return getRows() <= other.getRows(); // 比较当前行数是否小于或等于other的行数，返回布尔值
  }

  // implement RelOptCost // 注释：实现RelOptCost接口的方法
  @Override public boolean isLt(RelOptCost other) { // 方法：判断当前成本是否严格小于另一个成本，用于成本比较
    return getRows() < other.getRows(); // 比较当前行数是否严格小于other的行数，返回布尔值
  }

  @Override public int hashCode() { // 方法：计算对象的哈希码，用于在哈希表等数据结构中使用
    return Double.hashCode(getRows()); // 使用Double类的hashCode方法计算行数的哈希码并返回
  }

  // implement RelOptCost // 注释：实现RelOptCost接口的方法
  @SuppressWarnings("NonOverridingEquals") // 抑制警告：该equals方法不覆盖Object的equals方法，因为参数类型是RelOptCost而不是Object
  @Override public boolean equals(RelOptCost other) { // 方法：判断当前成本是否等于另一个成本，参数类型为RelOptCost接口
    return getRows() == other.getRows(); // 比较当前行数是否等于other的行数，返回布尔值
  }

  @Override public boolean equals(@Nullable Object obj) { // 方法：覆盖Object类的equals方法，用于对象相等性比较，参数可能为null
    if (obj instanceof RelOptCostImpl) { // 检查obj是否是RelOptCostImpl类的实例
      return equals((RelOptCost) obj); // 如果是，调用参数类型为RelOptCost的equals方法进行比较
    }
    return false; // 如果不是RelOptCostImpl实例，返回false表示不相等
  }

  // implement RelOptCost // 注释：实现RelOptCost接口的方法
  @Override public boolean isEqWithEpsilon(RelOptCost other) { // 方法：判断当前成本是否在误差范围内等于另一个成本，用于处理浮点数精度问题
    return Math.abs(getRows() - other.getRows()) < RelOptUtil.EPSILON; // 计算行数差的绝对值，如果小于RelOptUtil.EPSILON（一个很小的误差值）则认为相等
  }

  // implement RelOptCost // 注释：实现RelOptCost接口的方法
  @Override public RelOptCost minus(RelOptCost other) { // 方法：当前成本减去另一个成本，返回新的成本对象
    return new RelOptCostImpl(getRows() - other.getRows()); // 创建新的RelOptCostImpl实例，值为当前行数减去other的行数
  }

  // implement RelOptCost // 注释：实现RelOptCost接口的方法
  @Override public RelOptCost plus(RelOptCost other) { // 方法：当前成本加上另一个成本，返回新的成本对象
    return new RelOptCostImpl(getRows() + other.getRows()); // 创建新的RelOptCostImpl实例，值为当前行数加上other的行数
  }

  // implement RelOptCost // 注释：实现RelOptCost接口的方法
  @Override public RelOptCost multiplyBy(double factor) { // 方法：当前成本乘以一个因子，返回新的成本对象，用于成本缩放
    return new RelOptCostImpl(getRows() * factor); // 创建新的RelOptCostImpl实例，值为当前行数乘以因子factor
  }

  @Override public double divideBy(RelOptCost cost) { // 方法：当前成本除以另一个成本，返回商，用于计算成本比例
    RelOptCostImpl that = (RelOptCostImpl) cost; // 将cost参数强制转换为RelOptCostImpl类型
    return this.getRows() / that.getRows(); // 返回当前行数除以that的行数的结果
  }

  // implement RelOptCost // 注释：实现RelOptCost接口的方法
  @Override public String toString() { // 方法：将成本对象转换为字符串表示，用于调试和日志输出
    return RelOptCost.toString(value); // 调用RelOptCost接口的静态toString方法，格式化输出成本值
  }

  /** Implementation of {@link RelOptCostFactory} that creates // 内部类：Factory实现了RelOptCostFactory接口，用于创建RelOptCostImpl实例
   * {@link RelOptCostImpl}s. */ // 这是一个工厂模式的实现，专门负责生产RelOptCostImpl对象
  private static class Factory implements RelOptCostFactory { // 内部类声明：Factory实现了RelOptCostFactory接口，是一个静态内部类
    // implement RelOptPlanner // 注释：实现RelOptPlanner的方法
    @Override public RelOptCost makeCost( // 方法：创建一个成本对象，参数包括行数、CPU成本和I/O成本
        double dRows, // 参数：处理的行数
        double dCpu, // 参数：CPU成本，在这个实现中会被忽略
        double dIo) { // 参数：I/O成本，在这个实现中会被忽略
      return new RelOptCostImpl(dRows); // 创建并返回RelOptCostImpl实例，只使用行数dRows，忽略CPU和I/O成本
    }

    // implement RelOptPlanner // 注释：实现RelOptPlanner的方法
    @Override public RelOptCost makeHugeCost() { // 方法：创建一个巨大的成本对象，用于标记极差的执行计划
      return new RelOptCostImpl(Double.MAX_VALUE); // 创建并返回RelOptCostImpl实例，值为Double.MAX_VALUE（double类型的最大值）
    }

    // implement RelOptPlanner // 注释：实现RelOptPlanner的方法
    @Override public RelOptCost makeInfiniteCost() { // 方法：创建一个无穷大的成本对象，用于标记不可达的执行计划
      return new RelOptCostImpl(Double.POSITIVE_INFINITY); // 创建并返回RelOptCostImpl实例，值为正无穷大
    }

    // implement RelOptPlanner // 注释：实现RelOptPlanner的方法
    @Override public RelOptCost makeTinyCost() { // 方法：创建一个极小的成本对象，用于标记最优的执行计划
      return new RelOptCostImpl(1.0); // 创建并返回RelOptCostImpl实例，值为1.0，表示最小的非零成本
    }

    // implement RelOptPlanner // 注释：实现RelOptPlanner的方法
    @Override public RelOptCost makeZeroCost() { // 方法：创建一个零成本对象，用于标记无成本的操作
      return new RelOptCostImpl(0.0); // 创建并返回RelOptCostImpl实例，值为0.0，表示零成本
    }
  }
}
