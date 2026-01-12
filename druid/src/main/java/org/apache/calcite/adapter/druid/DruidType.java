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
 */ // Apache许可证声明，规定了代码的使用条件和限制
package org.apache.calcite.adapter.druid; // 声明包名，这个类属于org.apache.calcite.adapter.druid包，是Calcite框架中Druid适配器的一部分

import org.apache.calcite.sql.type.SqlTypeName; // 导入Calcite的SQL类型名称枚举，用于表示SQL标准中的数据类型，如BIGINT、VARCHAR等

import static java.util.Objects.requireNonNull; // 静态导入Objects的requireNonNull方法，用于参数非空校验，如果参数为null会抛出NullPointerException

/** Druid type. */ // 枚举类的JavaDoc注释，说明这是一个Druid类型的枚举，用于表示Druid数据库支持的数据类型
public enum DruidType { // 定义一个枚举类DruidType，枚举Druid支持的各种数据类型，每个枚举值都对应一个特定的Druid数据类型
  LONG(SqlTypeName.BIGINT), // LONG类型，对应SQL的BIGINT类型，表示64位整数，用于存储长整型数值
  FLOAT(SqlTypeName.REAL), // FLOAT类型，对应SQL的REAL类型，表示单精度浮点数，用于存储32位浮点数值
  DOUBLE(SqlTypeName.DOUBLE), // DOUBLE类型，对应SQL的DOUBLE类型，表示双精度浮点数，用于存储64位浮点数值
  STRING(SqlTypeName.VARCHAR), // STRING类型，对应SQL的VARCHAR类型，表示可变长度字符串，用于存储文本数据
  COMPLEX(SqlTypeName.OTHER), // COMPLEX类型，对应SQL的OTHER类型，表示复杂类型，用于存储嵌套或多值数据结构
  HYPER_UNIQUE(SqlTypeName.VARBINARY), // HYPER_UNIQUE类型，对应SQL的VARBINARY类型，表示HyperLogLog基数估计类型，用于高效计算去重计数
  THETA_SKETCH(SqlTypeName.VARBINARY); // THETA_SKETCH类型，对应SQL的VARBINARY类型，表示Theta Sketch基数估计类型，用于近似计算集合的基数

  /** The corresponding SQL type. */ // 成员变量的JavaDoc注释，说明这个字段存储对应的SQL类型名称
  public final SqlTypeName sqlType; // 定义一个公共的final成员变量sqlType，存储这个Druid类型对应的SQL类型名称，类型为SqlTypeName枚举

  DruidType(SqlTypeName sqlType) { // 构造方法，接收一个SqlTypeName参数，用于初始化枚举实例的sqlType字段
    this.sqlType = sqlType; // 将传入的SqlTypeName参数赋值给当前枚举实例的sqlType字段，建立Druid类型与SQL类型的映射关系
  }

  /** Returns whether this type should be used inside a
   * {@link ComplexMetric}. */ // 方法的JavaDoc注释，说明这个方法用于判断当前类型是否应该在ComplexMetric中使用
  public boolean isComplex() { // 定义一个公共方法isComplex，返回布尔值，用于判断当前Druid类型是否为复杂类型
    return this == THETA_SKETCH || this == HYPER_UNIQUE || this == COMPLEX; // 返回判断结果：如果是THETA_SKETCH、HYPER_UNIQUE或COMPLEX类型则返回true，表示是复杂类型；否则返回false
  }

  /** Returns a DruidType matching the given String type from a Druid metric. */ // 方法的JavaDoc注释，说明这个方法用于从Druid指标的字符串类型获取对应的DruidType枚举值
  static DruidType getTypeFromMetric(String type) { // 定义一个静态方法getTypeFromMetric，接收一个String参数type，返回对应的DruidType枚举值
    requireNonNull(type, "type"); // 调用requireNonNull方法检查type参数是否为null，如果为null则抛出NullPointerException，提示参数名"type"
    if (type.equals("hyperUnique")) { // 判断type字符串是否等于"hyperUnique"，这是Druid中HyperLogLog基数估计类型的标识
      return HYPER_UNIQUE; // 如果匹配，返回HYPER_UNIQUE枚举值，表示这是一个HyperLogLog基数估计类型
    } else if (type.equals("thetaSketch")) { // 判断type字符串是否等于"thetaSketch"，这是Druid中Theta Sketch基数估计类型的标识
      return THETA_SKETCH; // 如果匹配，返回THETA_SKETCH枚举值，表示这是一个Theta Sketch基数估计类型
    } else if (type.startsWith("long") || type.equals("count")) { // 判断type字符串是否以"long"开头或等于"count"，这些是Druid中表示长整型或计数的类型
      return LONG; // 如果匹配，返回LONG枚举值，表示这是一个长整型数据类型
    } else if (type.startsWith("double")) { // 判断type字符串是否以"double"开头，这是Druid中表示双精度浮点数的类型
      return DOUBLE; // 如果匹配，返回DOUBLE枚举值，表示这是一个双精度浮点数类型
    } else if (type.startsWith("float")) { // 判断type字符串是否以"float"开头，这是Druid中表示单精度浮点数的类型
      return FLOAT; // 如果匹配，返回FLOAT枚举值，表示这是一个单精度浮点数类型
    } // 结束if-else条件判断链
    throw new AssertionError("Unknown type: " + type); // 如果所有条件都不匹配，抛出AssertionError异常，提示遇到了未知的类型，并输出未知的类型字符串
  }

  /** Returns a DruidType matching the String from a metadata query. */ // 方法的JavaDoc注释，说明这个方法用于从元数据查询的字符串获取对应的DruidType枚举值
  static DruidType getTypeFromMetaData(String type) { // 定义一个静态方法getTypeFromMetaData，接收一个String参数type，返回对应的DruidType枚举值
    requireNonNull(type, "type"); // 调用requireNonNull方法检查type参数是否为null，如果为null则抛出NullPointerException，提示参数名"type"
    switch (type) { // 使用switch语句根据type字符串的值进行分支判断
    case "LONG": // 如果type等于"LONG"，这是Druid元数据中长整型类型的标准表示
      return LONG; // 返回LONG枚举值，表示这是一个长整型数据类型
    case "FLOAT": // 如果type等于"FLOAT"，这是Druid元数据中单精度浮点数类型的标准表示
      return FLOAT; // 返回FLOAT枚举值，表示这是一个单精度浮点数类型
    case "DOUBLE": // 如果type等于"DOUBLE"，这是Druid元数据中双精度浮点数类型的标准表示
      return DOUBLE; // 返回DOUBLE枚举值，表示这是一个双精度浮点数类型
    case "STRING": // 如果type等于"STRING"，这是Druid元数据中字符串类型的标准表示
      return STRING; // 返回STRING枚举值，表示这是一个字符串数据类型
    default: // 如果type不匹配以上任何case，进入默认分支
      // Likely a sketch, or a type String from the aggregations field. // 注释说明：这很可能是一个sketch类型，或者来自aggregations字段的类型字符串
      return getTypeFromMetric(type); // 调用getTypeFromMetric方法处理这个未知的类型字符串，尝试从指标类型中获取对应的DruidType枚举值
    } // 结束switch语句
  } // 结束getTypeFromMetaData方法
} // 结束DruidType枚举类定义
