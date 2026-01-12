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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.druid; // 定义包名，该类属于org.apache.calcite.adapter.druid包，用于Druid适配器相关功能

import org.apache.calcite.rel.core.AggregateCall; // 导入AggregateCall类，用于表示聚合调用，包含聚合函数的信息
import org.apache.calcite.sql.SqlKind; // 导入SqlKind类，用于表示SQL操作的类型，如COUNT、SUM等

/**
 * Stores information about available complex metrics in the Druid Adapter.
 * 存储Druid适配器中可用复杂度量指标的信息
 * 
 * 类作用说明：
 * ComplexMetric类用于封装Druid中复杂度量指标的元数据信息，Druid支持多种特殊的复杂类型，
 * 如HYPER_UNIQUE（用于基数估算）和THETA_SKETCH（用于近似去重计数）等。
 * 这些复杂类型不能像普通列那样直接使用，需要特殊的处理方式。
 * 该类负责：
 * 1. 存储复杂度量指标的名称和类型
 * 2. 验证类型是否为复杂类型
 * 3. 判断该复杂度量指标是否可以用于特定的聚合操作
 * 4. 提供获取度量指标类型名称的方法
 * 
 * 在Calcite适配Druid时，当遇到COUNT(DISTINCT)等操作时，会检查是否可以使用Druid的复杂度量指标
 * 来优化查询性能，避免全量计算。
 */
public class ComplexMetric { // 定义ComplexMetric类，用于表示Druid中的复杂度量指标

  /** The underlying metric column that this complex metric represents. */
  // 成员变量metricName：存储该复杂度量指标对应的基础度量列名称
  // 例如：如果Druid中有一个名为"user_unique"的HYPER_UNIQUE类型的列，则metricName为"user_unique"
  // 使用final修饰表示该字段在构造后不可变，保证线程安全
  private final String metricName; // 定义私有的最终字符串变量metricName，用于存储度量指标的列名

  /** The type of this metric. */
  // 成员变量type：存储该复杂度量指标的类型，使用DruidType枚举表示
  // DruidType枚举包含了Druid支持的所有数据类型，如HYPER_UNIQUE、THETA_SKETCH等复杂类型
  // 使用final修饰表示该字段在构造后不可变
  private final DruidType type; // 定义私有的最终DruidType变量type，用于存储度量指标的类型

  // 构造方法：创建ComplexMetric实例
  // 参数metricName：复杂度量指标的列名，对应Druid数据源中的列名
  // 参数type：度量指标的类型，必须是DruidType中的复杂类型（如HYPER_UNIQUE、THETA_SKETCH）
  // 构造方法首先会验证传入的类型是否为复杂类型，如果不是则抛出异常
  public ComplexMetric(String metricName, DruidType type) { // 定义公共构造方法，接收度量指标名称和类型两个参数
    validate(type); // 调用validate方法验证传入的类型是否为复杂类型，如果不是则抛出IllegalArgumentException异常
    this.metricName = metricName; // 将传入的metricName参数赋值给成员变量metricName
    this.type = type; // 将传入的type参数赋值给成员变量type
  }

  // 静态验证方法：验证DruidType是否为复杂类型
  // 参数type：需要验证的DruidType类型
  // 该方法检查传入的类型是否为复杂类型（即type.isComplex()返回true）
  // 如果不是复杂类型，抛出IllegalArgumentException异常
  // 使用private static修饰，表示这是一个私有静态工具方法，只能在类内部调用
  private static void validate(DruidType type) { // 定义私有静态方法validate，用于验证DruidType是否为复杂类型
    if (!type.isComplex()) { // 检查type的isComplex()方法是否返回false，即该类型不是复杂类型
      throw new IllegalArgumentException("Druid type: " + type + " is not complex"); // 如果不是复杂类型，抛出IllegalArgumentException异常，异常信息包含类型名称
    } // 结束if语句
  } // 结束validate方法

  // Getter方法：获取复杂度量指标的列名
  // 返回值：String类型，返回存储的metricName成员变量
  // 该方法用于外部获取该复杂度量指标对应的列名
  public String getMetricName() { // 定义公共方法getMetricName，用于获取度量指标的列名
    return metricName; // 返回成员变量metricName的值
  } // 结束getMetricName方法

  // Getter方法：获取复杂度量指标的DruidType类型
  // 返回值：DruidType类型，返回存储的type成员变量
  // 该方法用于外部获取该复杂度量指标的类型枚举值
  public DruidType getDruidType() { // 定义公共方法getDruidType，用于获取度量指标的DruidType类型
    return type; // 返回成员变量type的值
  } // 结束getDruidType方法

  // 方法：获取度量指标的类型名称字符串
  // 返回值：String类型，返回Druid中使用的度量指标类型名称
  // 该方法将DruidType枚举转换为Druid实际使用的字符串类型名称
  // 例如：HYPER_UNIQUE枚举对应"hyperUnique"字符串，THETA_SKETCH枚举对应"thetaSketch"字符串
  // 这些字符串名称用于生成Druid查询JSON时指定复杂的聚合类型
  public String getMetricType() { // 定义公共方法getMetricType，用于获取度量指标的类型名称字符串
    switch (type) { // 使用switch语句根据type的值进行分支判断
    case HYPER_UNIQUE: // 如果type为HYPER_UNIQUE（基数估算类型）
      return "hyperUnique"; // 返回"hyperUnique"字符串，这是Druid中HYPER_UNIQUE类型的标识符
    case THETA_SKETCH: // 如果type为THETA_SKETCH（Theta Sketch近似去重类型）
      return "thetaSketch"; // 返回"thetaSketch"字符串，这是Druid中THETA_SKETCH类型的标识符
    default: // 如果type不是上述任何一种已知类型
      throw new AssertionError("Type: " // 抛出AssertionError断言错误，表示遇到了未预期的类型
              + type + " does not have an associated metric type"); // 错误信息包含类型名称，说明该类型没有关联的度量类型字符串
    } // 结束switch语句
  } // 结束getMetricType方法

  /**
   * Returns true if and only if this <code>ComplexMetric</code>
   * can be used in the given {@link AggregateCall}.
   * 判断该复杂度量指标是否可以用于给定的聚合调用
   * 
   * 方法作用说明：
   * 该方法用于判断当前ComplexMetric实例是否可以用于指定的AggregateCall聚合操作
   * 这是一个关键的决策方法，决定了查询优化器是否可以使用Druid的复杂度量指标来替代
   * 普通的聚合计算，从而提高查询性能。
   * 
   * 参数call：AggregateCall对象，表示SQL中的聚合调用，包含聚合函数类型、参数等信息
   * 
   * 返回值：boolean类型，true表示可以使用该复杂度量指标，false表示不能使用
   * 
   * 判断逻辑：
   * 1. 对于HYPER_UNIQUE和THETA_SKETCH类型：
   *    - 聚合调用不能为null
   *    - 聚合函数类型必须是COUNT（计数操作）
   *    - 必须是DISTINCT计数（即COUNT(DISTINCT column)）
   *    这是因为HYPER_UNIQUE和THETA_SKETCH都是用于基数估算的复杂类型，只能用于去重计数
   * 2. 对于其他类型：返回false，表示不能使用
   * 
   * 使用场景：
   * 当Calcite将SQL查询转换为Druid查询时，会检查聚合操作是否可以使用Druid的复杂度量指标
   * 例如：SELECT COUNT(DISTINCT user_id) FROM table
   * 如果Druid中有一个HYPER_UNIQUE类型的列"user_id_unique"，则可以使用该列来替代
   * 普通的COUNT(DISTINCT)操作，大大提高查询性能。
   * */
  public boolean canBeUsed(AggregateCall call) { // 定义公共方法canBeUsed，用于判断该复杂度量指标是否可以用于给定的聚合调用
    switch (type) { // 使用switch语句根据type的值进行分支判断
    case HYPER_UNIQUE: // 如果type为HYPER_UNIQUE（基数估算类型）
    case THETA_SKETCH: // 如果type为THETA_SKETCH（Theta Sketch近似去重类型）
      return call != null // 首先检查聚合调用call不为null
            && call.getAggregation().getKind() == SqlKind.COUNT // 然后检查聚合函数的类型是否为COUNT（计数操作）
            && call.isDistinct(); // 最后检查是否为DISTINCT计数（去重计数），只有同时满足这三个条件才返回true
    default: // 如果type不是上述任何一种类型
      return false; // 返回false，表示该复杂度量指标不能用于该聚合调用
    } // 结束switch语句
  } // 结束canBeUsed方法
} // 结束ComplexMetric类
