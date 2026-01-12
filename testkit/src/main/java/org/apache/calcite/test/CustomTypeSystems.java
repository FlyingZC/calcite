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
package org.apache.calcite.test; // 声明包名，该类位于org.apache.calcite.test包下，用于测试相关的类型系统

import org.apache.calcite.rel.type.DelegatingTypeSystem; // 导入委托类型系统类，用于包装现有的类型系统并自定义部分行为
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建数据类型实例
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入关系数据类型系统接口，定义了SQL类型的各种约束和行为
import org.apache.calcite.sql.type.BasicSqlType; // 导入基本SQL类型类，表示基础的SQL数据类型如INTEGER、VARCHAR等
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义了所有标准SQL类型名称
import org.apache.calcite.sql.type.SqlTypeUtil; // 导入SQL类型工具类，提供类型判断和操作的静态方法

import java.math.RoundingMode; // 导入舍入模式枚举，定义了不同的数值舍入策略如HALF_UP、DOWN等
import java.util.function.Function; // 导入函数式接口，用于定义类型到值的映射关系

/**
 * Custom implementations of {@link RelDataTypeSystem} for testing.
 * // 自定义的RelDataTypeSystem实现类，专门用于测试目的
 * // 该类提供了一系列预定义的类型系统实现和工具方法，用于测试Calcite在不同类型系统下的行为
 * // 包括舍入模式、精度、小数位数等类型属性的定制，以及模拟Spark等系统的类型行为
 */
@SuppressWarnings("SameParameterValue") // 抑制警告：忽略方法参数未被使用的警告，因为这些参数在匿名类中使用
public final class CustomTypeSystems { // 定义一个不可继承的工具类，提供自定义类型系统的实现
  private CustomTypeSystems() { // 私有构造方法，防止实例化，因为这是一个纯工具类
  }

  /** Type system with rounding behavior {@link RoundingMode#HALF_UP}.
   * (The default implementation is {@link RoundingMode#DOWN}.) */
  // // 定义一个类型系统常量，使用HALF_UP（四舍五入）舍入模式，默认是DOWN（直接舍去）
  public static final RelDataTypeSystem ROUNDING_MODE_HALF_UP = // 声明一个静态常量，表示使用四舍五入模式的类型系统
      withRoundingMode(RelDataTypeSystem.DEFAULT, RoundingMode.HALF_UP); // 调用withRoundingMode方法，基于默认类型系统创建使用HALF_UP舍入模式的新类型系统

  /** Type system that supports negative scale
   * and has rounding mode {@link RoundingMode#DOWN}. */
  // // 定义一个支持负小数位数的类型系统常量，使用默认的DOWN舍入模式
  // // 负小数位数表示可以将数值舍入到十位、百位等，例如scale=-2表示舍入到百位
  public static final RelDataTypeSystem NEGATIVE_SCALE = // 声明一个静态常量，表示支持负小数位数的类型系统
      withMinScale(RelDataTypeSystem.DEFAULT, sqlTypeName -> -1000); // 调用withMinScale方法，将最小小数位数设置为-1000，允许极大的负小数位数

  /** Type system that supports negative scale
   * and has rounding mode {@link RoundingMode#HALF_UP}. */
  // // 定义一个支持负小数位数且使用HALF_UP舍入模式的类型系统常量
  // // 结合了负小数位数支持和四舍五入行为
  public static final RelDataTypeSystem NEGATIVE_SCALE_ROUNDING_MODE_HALF_UP = // 声明一个静态常量，表示支持负小数位数且使用四舍五入的类型系统
      withMinScale(ROUNDING_MODE_HALF_UP, sqlTypeName -> -1000); // 调用withMinScale方法，基于ROUNDING_MODE_HALF_UP类型系统，将最小小数位数设置为-1000

  /** Type system that similar to Spark. */
  // // 定义一个模拟Spark类型系统的常量，用于测试与Spark兼容的行为
  // // Spark的SUM聚合函数返回类型规则与标准SQL不同，该类型系统实现了Spark的规则
  public static final RelDataTypeSystem SPARK_TYPE_SYSTEM = // 声明一个静态常量，表示模拟Spark的类型系统
      new DelegatingTypeSystem(RelDataTypeSystem.DEFAULT) { // 创建一个匿名类，继承DelegatingTypeSystem并基于默认类型系统
        @Override public RelDataType deriveSumType(RelDataTypeFactory typeFactory, // 重写deriveSumType方法，自定义SUM聚合函数的返回类型推导逻辑
            RelDataType argumentType) { // 参数：类型工厂和参数类型
          if (argumentType instanceof BasicSqlType) { // 检查参数类型是否是基本SQL类型
            // For TINYINT, SMALLINT, INTEGER, BIGINT,
            // using BIGINT
            // // 对于精确数值类型（TINYINT、SMALLINT、INTEGER、BIGINT），SUM返回BIGINT类型
            if (SqlTypeUtil.isExactNumeric(argumentType) && !SqlTypeUtil.isDecimal(argumentType)) { // 判断是否是精确数值类型且不是DECIMAL类型
              argumentType = // 重新赋值参数类型为BIGINT
                  typeFactory.createTypeWithNullability( // 创建带可空性的类型
                      typeFactory.createSqlType(SqlTypeName.BIGINT), // 创建BIGINT类型的SQL类型
                      argumentType.isNullable()); // 保持原有的可空性设置
            }
            // For FLOAT, REAL and DOUBLE,
            // using DOUBLE
            // // 对于近似数值类型（FLOAT、REAL、DOUBLE），SUM返回DOUBLE类型
            if (SqlTypeUtil.isApproximateNumeric(argumentType)) { // 判断是否是近似数值类型
              argumentType = // 重新赋值参数类型为DOUBLE
                  typeFactory.createTypeWithNullability( // 创建带可空性的类型
                      typeFactory.createSqlType(SqlTypeName.DOUBLE), // 创建DOUBLE类型的SQL类型
                      argumentType.isNullable()); // 保持原有的可空性设置
            }
            return argumentType; // 返回推导出的结果类型
          }
          return super.deriveSumType(typeFactory, argumentType); // 如果不是BasicSqlType，调用父类的默认实现
        }
    };

  /** Decorates a type system so that
   * {@link org.apache.calcite.rel.type.RelDataTypeSystem#roundingMode()}
   * returns a given value. */
  // // 工厂方法：装饰一个类型系统，使其返回指定的舍入模式
  // // 使用委托模式创建新的类型系统实例，只修改舍入模式行为
  public static RelDataTypeSystem withRoundingMode(RelDataTypeSystem typeSystem, // 参数：基础类型系统
      RoundingMode roundingMode) { // 参数：要设置的舍入模式
    return new DelegatingTypeSystem(typeSystem) { // 返回一个匿名类实例，继承DelegatingTypeSystem并包装基础类型系统
      @Override public RoundingMode roundingMode() { // 重写roundingMode方法，返回自定义的舍入模式
        return roundingMode; // 返回传入的舍入模式值
      }
    };
  }

  /** Decorates a type system so that
   * {@link org.apache.calcite.rel.type.RelDataTypeSystem#getMaxPrecision(SqlTypeName)}
   * returns a given value. */
  // // 工厂方法：装饰一个类型系统，使其返回指定类型的最大精度
  // // 使用委托模式创建新的类型系统实例，允许自定义每种SQL类型的最大精度
  public static RelDataTypeSystem withMaxPrecision(RelDataTypeSystem typeSystem, // 参数：基础类型系统
      Function<SqlTypeName, Integer> maxPrecision) { // 参数：一个函数，根据SQL类型名称返回最大精度值
    return new DelegatingTypeSystem(typeSystem) { // 返回一个匿名类实例，继承DelegatingTypeSystem并包装基础类型系统
      @Override public int getMaxPrecision(SqlTypeName typeName) { // 重写getMaxPrecision方法，返回自定义的最大精度
        return maxPrecision.apply(typeName); // 调用传入的函数，根据类型名称计算并返回最大精度
      }
    };
  }

  /** Decorates a type system so that
   * {@link org.apache.calcite.rel.type.RelDataTypeSystem#getMaxScale(SqlTypeName)}
   * returns a given value. */
  // // 工厂方法：装饰一个类型系统，使其返回指定类型的最大小数位数
  // // 使用委托模式创建新的类型系统实例，允许自定义每种SQL类型的最大小数位数
  public static RelDataTypeSystem withMaxScale(RelDataTypeSystem typeSystem, // 参数：基础类型系统
      Function<SqlTypeName, Integer> maxScale) { // 参数：一个函数，根据SQL类型名称返回最大小数位数
    return new DelegatingTypeSystem(typeSystem) { // 返回一个匿名类实例，继承DelegatingTypeSystem并包装基础类型系统
      @Override public int getMaxScale(SqlTypeName typeName) { // 重写getMaxScale方法，返回自定义的最大小数位数
        return maxScale.apply(typeName); // 调用传入的函数，根据类型名称计算并返回最大小数位数
      }
    };
  }

  /** Decorates a type system so that
   * {@link org.apache.calcite.rel.type.RelDataTypeSystem#getMinScale(SqlTypeName)}
   * returns a given value. */
  // // 工厂方法：装饰一个类型系统，使其返回指定类型的最小小数位数
  // // 使用委托模式创建新的类型系统实例，允许自定义每种SQL类型的最小小数位数，支持负值
  public static RelDataTypeSystem withMinScale(RelDataTypeSystem typeSystem, // 参数：基础类型系统
      Function<SqlTypeName, Integer> minNumericScale) { // 参数：一个函数，根据SQL类型名称返回最小小数位数
    return new DelegatingTypeSystem(typeSystem) { // 返回一个匿名类实例，继承DelegatingTypeSystem并包装基础类型系统
      @Override public int getMinScale(SqlTypeName typeName) { // 重写getMinScale方法，返回自定义的最小小数位数
        return minNumericScale.apply(typeName); // 调用传入的函数，根据类型名称计算并返回最小小数位数
      }
    };
  }
} // 类结束，该类提供了多种自定义类型系统的实现和工厂方法，用于测试和模拟不同的类型系统行为
