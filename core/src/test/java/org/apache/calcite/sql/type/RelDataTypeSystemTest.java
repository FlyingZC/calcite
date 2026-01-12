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
package org.apache.calcite.sql.type; // 声明包名，表示这个类属于org.apache.calcite.sql.type包

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，用于表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory工厂接口，用于创建RelDataType实例
import org.apache.calcite.rel.type.RelDataTypeSystemImpl; // 导入RelDataTypeSystemImpl抽象类，这是数据类型系统的默认实现
import org.apache.calcite.runtime.CalciteException; // 导入CalciteException异常类，表示Calcite运行时异常
import org.apache.calcite.runtime.Resources; // 导入Resources工具类，用于资源管理和国际化
import org.apache.calcite.sql.SqlLiteral; // 导入SqlLiteral类，表示SQL字面量
import org.apache.calcite.sql.SqlOperatorBinding; // 导入SqlOperatorBinding类，用于SQL操作符绑定时的上下文信息
import org.apache.calcite.sql.fun.SqlLibraryOperators; // 导入SqlLibraryOperators类，包含SQL库操作符
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，包含标准SQL操作符
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SqlParserPos类，表示SQL解析位置
import org.apache.calcite.sql.validate.SqlValidatorException; // 导入SqlValidatorException异常类，表示SQL验证异常

import com.google.common.collect.Lists; // 导入Google Guava的Lists工具类，用于创建列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可空类型
import org.junit.jupiter.api.Test; // 导入Junit5的Test注解，用于标记测试方法

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest的is匹配器，用于断言
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言工具
import static org.junit.jupiter.api.Assertions.assertEquals; // 导入Junit5的assertEquals断言方法
import static org.junit.jupiter.api.Assertions.assertThrows; // 导入Junit5的assertThrows断言方法

/**
 * Tests the inference of return types using {@code RelDataTypeSystem}. // 类注释：测试使用RelDataTypeSystem进行返回类型推断的功能
 * 这个测试类主要验证Calcite的数据类型系统在各种场景下的类型推断能力，
 * 包括DECIMAL类型的加减乘除运算、MOD运算、MEASURE类型处理、自定义类型系统等
 */
class RelDataTypeSystemTest { // 测试类定义：测试RelDataTypeSystem（关系数据类型系统）的类型推断功能

  /**
   * Custom type system class that overrides the default decimal plus type derivation and // 内部类注释：自定义类型系统类，覆盖默认的DECIMAL加法类型推导逻辑
   * overrides the max precision for timestamps. // 并且覆盖TIMESTAMP的最大精度设置
   * 这个类继承自RelDataTypeSystemImpl，用于测试自定义类型系统的行为，
   * 主要修改了DECIMAL类型的加法运算规则和TIMESTAMP类型的最大精度
   */
  private static final class CustomTypeSystem extends RelDataTypeSystemImpl { // 自定义类型系统类，继承默认实现并覆盖部分方法
    // Arbitrarily choose a different maximum timestamp precision from the default. // 注释说明：任意选择一个与默认值不同的最大时间戳精度
    private static final int CUSTOM_MAX_TIMESTAMP_PRECISION = // 常量：自定义的最大TIMESTAMP精度，比默认值大3
        SqlTypeName.MAX_DATETIME_PRECISION + 3; // 计算方式：在最大日期时间精度基础上加3，用于测试自定义精度限制

    @Override public RelDataType deriveDecimalPlusType(RelDataTypeFactory typeFactory, // 覆盖方法：推导DECIMAL加法运算的结果类型，typeFactory是类型工厂，type1和type2是两个操作数类型
        RelDataType type1, RelDataType type2) { // 参数：type1是第一个操作数的类型，type2是第二个操作数的类型

      if (!SqlTypeUtil.isExactNumeric(type1) // 检查：如果第一个操作数不是精确数值类型
          && !SqlTypeUtil.isExactNumeric(type2)) { // 并且第二个操作数也不是精确数值类型
        return null; // 返回null表示无法推导结果类型
      }
      if (!SqlTypeUtil.isDecimal(type1) // 检查：如果第一个操作数不是DECIMAL类型
            || !SqlTypeUtil.isDecimal(type2)) { // 或者第二个操作数不是DECIMAL类型
        return null; // 返回null表示无法推导结果类型
      }

      int resultScale = Math.max(type1.getScale(), type2.getScale()); // 计算结果的小数位数：取两个操作数小数位数的最大值
      int resultPrecision = // 计算结果的总精度：小数位数 + 两个操作数整数位数的最大值 + 1（进位）
          resultScale // 基础值：结果的小数位数
              + Math.max(type1.getPrecision() - type1.getScale(), // 计算第一个操作数的整数位数
                  type2.getPrecision() - type2.getScale()) // 计算第二个操作数的整数位数，取最大值
              + 1; // 加1是因为加法可能导致进位
      if (resultPrecision > 38) { // 检查：如果计算出的精度超过38（DECIMAL类型的最大精度）
        int minScale = Math.min(resultScale, 6); // 计算最小保留的小数位数：不超过6位
        int delta = resultPrecision - 38; // 计算超出的精度值
        resultPrecision = 38; // 将结果精度限制为最大值38
        resultScale = Math.max(resultScale - delta, minScale); // 调整小数位数：减去超出值，但不低于最小值
      }

      return typeFactory.createSqlType(SqlTypeName.DECIMAL, resultPrecision, resultScale); // 返回：创建DECIMAL类型，使用计算出的精度和小数位数
    }

    @Override public RelDataType deriveDecimalMultiplyType(RelDataTypeFactory typeFactory, // 覆盖方法：推导DECIMAL乘法运算的结果类型
        RelDataType type1, RelDataType type2) { // 参数：type1和type2是两个操作数的DECIMAL类型

      if (!SqlTypeUtil.isExactNumeric(type1) // 检查：如果第一个操作数不是精确数值类型
          && !SqlTypeUtil.isExactNumeric(type2)) { // 并且第二个操作数也不是精确数值类型
        return null; // 返回null表示无法推导结果类型
      }
      if (!SqlTypeUtil.isDecimal(type1) // 检查：如果第一个操作数不是DECIMAL类型
            || !SqlTypeUtil.isDecimal(type2)) { // 或者第二个操作数不是DECIMAL类型
        return null; // 返回null表示无法推导结果类型
      }

      return typeFactory.createSqlType(SqlTypeName.DECIMAL, // 返回：创建DECIMAL类型
          type1.getPrecision() * type2.getPrecision(), type1.getScale() * type2.getScale()); // 乘法结果的精度和小数位数都是两个操作数的乘积
    }

    @Override public RelDataType deriveDecimalDivideType(RelDataTypeFactory typeFactory, // 覆盖方法：推导DECIMAL除法运算的结果类型
        RelDataType type1, RelDataType type2) { // 参数：type1是被除数，type2是除数

      if (!SqlTypeUtil.isExactNumeric(type1) // 检查：如果第一个操作数不是精确数值类型
          && !SqlTypeUtil.isExactNumeric(type2)) { // 并且第二个操作数也不是精确数值类型
        return null; // 返回null表示无法推导结果类型
      }
      if (!SqlTypeUtil.isDecimal(type1) // 检查：如果第一个操作数不是DECIMAL类型
            || !SqlTypeUtil.isDecimal(type2)) { // 或者第二个操作数不是DECIMAL类型
        return null; // 返回null表示无法推导结果类型
      }

      return typeFactory.createSqlType(SqlTypeName.DECIMAL, // 返回：创建DECIMAL类型
          Math.abs(type1.getPrecision() - type2.getPrecision()), // 精度为两个操作数精度差的绝对值
          Math.abs(type1.getScale() - type2.getScale())); // 小数位数为两个操作数小数位数差的绝对值
    }

    @Override public RelDataType deriveDecimalModType(RelDataTypeFactory typeFactory, // 覆盖方法：推导DECIMAL取模运算的结果类型
        RelDataType type1, RelDataType type2) { // 参数：type1是被模数，type2是模数
      if (!SqlTypeUtil.isExactNumeric(type1) // 检查：如果第一个操作数不是精确数值类型
          && !SqlTypeUtil.isExactNumeric(type2)) { // 并且第二个操作数也不是精确数值类型
        return null; // 返回null表示无法推导结果类型
      }
      if (!SqlTypeUtil.isDecimal(type1) // 检查：如果第一个操作数不是DECIMAL类型
            || !SqlTypeUtil.isDecimal(type2)) { // 或者第二个操作数不是DECIMAL类型
        return null; // 返回null表示无法推导结果类型
      }

      return type1; // 返回：取模运算的结果类型与第一个操作数（被模数）的类型相同
    }

    @Override public int getMaxNumericPrecision() { // 覆盖方法：返回数值类型的最大精度
      return 38; // 返回：DECIMAL类型的最大精度为38
    }

    @Override public int getMaxPrecision(SqlTypeName typeName) { // 覆盖方法：返回指定类型的最大精度
      if (typeName == SqlTypeName.TIMESTAMP) { // 检查：如果类型是TIMESTAMP
        return CUSTOM_MAX_TIMESTAMP_PRECISION; // 返回：使用自定义的时间戳最大精度
      }
      return super.getMaxPrecision(typeName); // 返回：其他类型使用父类的默认最大精度
    }
  }

  /** Test fixture with custom type factory. */ // 内部类注释：测试夹具，包含自定义类型工厂
  static class Fixture extends SqlTypeFixture { // 测试夹具类，继承自SqlTypeFixture，提供测试所需的类型工厂
    final SqlTypeFactoryImpl customTypeFactory = new SqlTypeFactoryImpl(new CustomTypeSystem()); // 成员变量：自定义类型工厂，使用CustomTypeSystem创建，用于测试自定义类型系统
  }

  @Test void testNegativeScale() { // 测试方法：测试DECIMAL类型支持负小数位数的功能
    final SqlTypeFactoryImpl customTypeFactory = // 创建自定义类型工厂，重写getMinScale方法以支持负小数位数
        new SqlTypeFactoryImpl(new RelDataTypeSystemImpl() { // 匿名内部类：覆盖默认类型系统的行为
          @Override public int getMinScale(SqlTypeName typeName) { // 覆盖方法：返回指定类型的最小小数位数
            switch (typeName) { // 根据类型名称进行判断
            case DECIMAL: // 如果是DECIMAL类型
              return -10; // 返回-10，表示允许最小小数位数为-10
            default: // 其他类型
              return super.getMinScale(typeName); // 使用父类的默认值
            }
          }
        });
    RelDataType dataType = customTypeFactory.createSqlType(SqlTypeName.DECIMAL, 10, -5); // 创建DECIMAL类型：精度10，小数位数-5（负数表示整数部分有位数）
    assertEquals(SqlTypeName.DECIMAL, dataType.getSqlTypeName()); // 断言：验证类型名称是DECIMAL
    assertEquals(10, dataType.getPrecision()); // 断言：验证精度是10
    assertEquals(-5, dataType.getScale()); // 断言：验证小数位数是-5
    assertThrows(CalciteException.class, () -> // 断言：验证创建小数位数-11的DECIMAL会抛出异常（超出最小值-10）
            customTypeFactory.createSqlType(SqlTypeName.DECIMAL, 10, -11), // 尝试创建小数位数为-11的DECIMAL类型
        "DECIMAL scale -11 must be between -10 and 19"); // 期望的异常消息
    assertThrows(CalciteException.class, () -> // 断言：验证使用默认类型工厂创建负小数位数的DECIMAL会抛出异常
            new Fixture().typeFactory.createSqlType(SqlTypeName.DECIMAL, 10, -5), // 使用默认类型工厂创建小数位数为-5的DECIMAL
        "DECIMAL scale -11 must be between 0 and 19"); // 期望的异常消息（默认最小小数位数为0）
  }

  @Test void testDecimalAdditionReturnTypeInference() { // 测试方法：测试DECIMAL加法运算的返回类型推断
    final SqlTypeFactoryImpl f = new Fixture().typeFactory; // 获取类型工厂实例
    RelDataType operand1 = f.createSqlType(SqlTypeName.DECIMAL, 10, 1); // 创建第一个操作数：DECIMAL类型，精度10，小数位数1
    RelDataType operand2 = f.createSqlType(SqlTypeName.DECIMAL, 10, 2); // 创建第二个操作数：DECIMAL类型，精度10，小数位数2

    RelDataType dataType = // 推断加法运算的结果类型
        SqlStdOperatorTable.PLUS.inferReturnType(f, // 调用PLUS操作符的推断方法
            Lists.newArrayList(operand1, operand2)); // 传入操作数列表
    assertThat(dataType.getPrecision(), is(12)); // 断言：验证精度是12（max(1,2)+max(10-1,10-2)+1=2+9+1=12）
    assertThat(dataType.getScale(), is(2)); // 断言：验证小数位数是2（max(1,2)=2）

    dataType = // 推断减法运算的结果类型
        SqlStdOperatorTable.MINUS.inferReturnType(f, // 调用MINUS操作符的推断方法
            Lists.newArrayList(operand1, operand2)); // 传入操作数列表
    assertThat(dataType.getPrecision(), is(12)); // 断言：验证精度是12（减法与加法使用相同的类型推导规则）
    assertThat(dataType.getScale(), is(2)); // 断言：验证小数位数是2
  }

  @Test void testDecimalDivideReturnTypeInference() { // 测试方法：测试DECIMAL除法运算的返回类型推断
    final SqlTypeFactoryImpl f = new Fixture().typeFactory; // 获取类型工厂实例
    RelDataType operand1 = f.createSqlType(SqlTypeName.DECIMAL, 6, 2); // 创建被除数：DECIMAL类型，精度6，小数位数2
    RelDataType operand2 = f.createSqlType(SqlTypeName.DECIMAL, 6, 2); // 创建除数：DECIMAL类型，精度6，小数位数2

    RelDataType dataType = // 推断除法运算的结果类型
        SqlStdOperatorTable.DIVIDE.inferReturnType(f, // 调用DIVIDE操作符的推断方法
            Lists.newArrayList(operand1, operand2)); // 传入操作数列表
    assertThat(dataType.getPrecision(), is(15)); // 断言：验证精度是15（根据SQL标准：6+2+6=14+1=15）
    assertThat(dataType.getScale(), is(6)); // 断言：验证小数位数是6（根据SQL标准：被除数小数位数+除数小数位数=2+2+2=6）
  }

  /**
   * Tests that the return type inference for a division with a custom type system // 方法注释：测试自定义类型系统（最大精度28，最大小数位数10）下的除法返回类型推断是否正确
   * (max precision=28, max scale=10) works correctly. // 这是为了验证CALCITE-6464问题的修复
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6464">[CALCITE-6464] // JIRA问题链接：DECIMAL除法的类型推断似乎不正确
   * Type inference for DECIMAL division seems incorrect</a>
   */
  @Test void testCustomMaxPrecisionCustomMaxScaleDecimalDivideReturnTypeInference() { // 测试方法：测试自定义最大精度和最大小数位数时的DECIMAL除法类型推断
    /**
     * Custom type system class that overrides the default max precision and max scale. // 内部类注释：自定义类型系统类，覆盖默认的最大精度和最大小数位数
     */
    final class CustomTypeSystem extends RelDataTypeSystemImpl { // 自定义类型系统类，用于测试
      @Override public int getMaxNumericPrecision() { // 覆盖方法：返回数值类型的最大精度
        return getMaxPrecision(SqlTypeName.DECIMAL); // 返回DECIMAL类型的最大精度
      }

      @Override public int getMaxPrecision(SqlTypeName typeName) { // 覆盖方法：返回指定类型的最大精度
        switch (typeName) { // 根据类型名称判断
        case DECIMAL: // 如果是DECIMAL类型
          return 28; // 返回28作为最大精度
        default: // 其他类型
          return super.getMaxPrecision(typeName); // 使用父类的默认值
        }
      }

      @Override public int getMaxNumericScale() { // 覆盖方法：返回数值类型的最大小数位数
        return getMaxScale(SqlTypeName.DECIMAL); // 返回DECIMAL类型的最大小数位数
      }

      @Override public int getMaxScale(SqlTypeName typeName) { // 覆盖方法：返回指定类型的最大小数位数
        switch (typeName) { // 根据类型名称判断
        case DECIMAL: // 如果是DECIMAL类型
          return 10; // 返回10作为最大小数位数
        default: // 其他类型
          return super.getMaxScale(typeName); // 使用父类的默认值
        }
      }
    }

    final SqlTypeFactoryImpl f = new SqlTypeFactoryImpl(new CustomTypeSystem()); // 创建使用自定义类型系统的类型工厂

    RelDataType operand1 = f.createSqlType(SqlTypeName.DECIMAL, 28, 10); // 创建被除数：DECIMAL类型，精度28，小数位数10（最大值）
    RelDataType operand2 = f.createSqlType(SqlTypeName.DECIMAL, 28, 10); // 创建除数：DECIMAL类型，精度28，小数位数10（最大值）

    RelDataType dataType = SqlStdOperatorTable.DIVIDE.inferReturnType(f, Lists // 推断除法运算的结果类型
        .newArrayList(operand1, operand2)); // 传入操作数列表
    assertThat(dataType.getSqlTypeName(), is(SqlTypeName.DECIMAL)); // 断言：验证类型名称是DECIMAL
    assertThat(dataType.getPrecision(), is(28)); // 断言：验证精度是28（受限于最大精度）
    assertThat(dataType.getScale(), is(6)); // 断言：验证小数位数是6（计算值为10+10+10=30，但受限于最大小数位数10，调整为6）
  }

  @Test void testDecimalModReturnTypeInference() { // 测试方法：测试DECIMAL取模运算的返回类型推断
    final SqlTypeFactoryImpl f = new Fixture().typeFactory; // 获取类型工厂实例
    RelDataType operand1 = f.createSqlType(SqlTypeName.DECIMAL, 10, 1); // 创建被模数：DECIMAL类型，精度10，小数位数1
    RelDataType operand2 = f.createSqlType(SqlTypeName.DECIMAL, 19, 2); // 创建模数：DECIMAL类型，精度19，小数位数2

    RelDataType dataType = SqlStdOperatorTable.MOD.inferReturnType(f, Lists // 推断取模运算的结果类型
            .newArrayList(operand1, operand2)); // 传入操作数列表
    assertThat(dataType.getPrecision(), is(11)); // 断言：验证精度是11（max(10,19)+1=19，但取模结果不会超过被模数，所以是10+1=11）
    assertThat(dataType.getScale(), is(2)); // 断言：验证小数位数是2（max(1,2)=2）
  }

  @Test void testDoubleModReturnTypeInference() { // 测试方法：测试DOUBLE类型取模运算的返回类型推断
    final SqlTypeFactoryImpl f = new Fixture().typeFactory; // 获取类型工厂实例
    RelDataType operand1 = f.createSqlType(SqlTypeName.DOUBLE); // 创建第一个操作数：DOUBLE类型
    RelDataType operand2 = f.createSqlType(SqlTypeName.DOUBLE); // 创建第二个操作数：DOUBLE类型

    RelDataType dataType = SqlStdOperatorTable.MOD.inferReturnType(f, Lists // 推断取模运算的结果类型
            .newArrayList(operand1, operand2)); // 传入操作数列表
    assertThat(dataType.getSqlTypeName(), is(SqlTypeName.DOUBLE)); // 断言：验证类型名称是DOUBLE（浮点数取模结果仍是浮点数）
  }

  /** Tests that LEAST_RESTRICTIVE considers a MEASURE's element type // 方法注释：测试LEAST_RESTRICTIVE（最不严格类型）是否考虑MEASURE类型的元素类型
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5869">[CALCITE-5869] // JIRA问题链接：LEAST_RESTRICTIVE没有使用MEASURE元素类型
   * LEAST_RESTRICTIVE does not use MEASURE element type</a>. */
  @Test void testLeastRestrictiveUsesMeasureElementType() { // 测试方法：测试IFNULL操作符在处理MEASURE类型时的类型推断
    final SqlTypeFactoryImpl f = new Fixture().typeFactory; // 获取类型工厂实例
    RelDataType innerType = f.createSqlType(SqlTypeName.DOUBLE); // 创建内部类型：DOUBLE类型
    RelDataType operand1 = f.createMeasureType(innerType); // 创建MEASURE类型，元素类型为DOUBLE
    RelDataType operand2 = f.createSqlType(SqlTypeName.INTEGER); // 创建第二个操作数：INTEGER类型
    RelDataType dataType = SqlLibraryOperators.IFNULL // 推断IFNULL操作符的返回类型
        .inferReturnType(f, Lists.newArrayList(operand1, operand2)); // 传入操作数列表：MEASURE(DOUBLE)和INTEGER
    assertThat(dataType, is(innerType)); // 断言：验证返回类型是DOUBLE（MEASURE的元素类型，而不是INTEGER）
  }

  /** <a href="https://issues.apache.org/jira/browse/CALCITE-6343">[CALCITE-6343]</a> // JIRA问题链接：确保AS操作符不改变MEASURE的返回类型
   * Ensure that AS operator doesn't change return type of measures. */
  @Test void testAsOperatorReturnTypeInferenceDoesNotRemoveMeasure() { // 测试方法：测试AS操作符不会移除MEASURE类型
    final SqlTypeFactoryImpl f = new Fixture().typeFactory; // 获取类型工厂实例
    RelDataType innerType = f.createSqlType(SqlTypeName.DOUBLE); // 创建内部类型：DOUBLE类型
    RelDataType measureType = f.createMeasureType(innerType); // 创建MEASURE类型，元素类型为DOUBLE
    RelDataType dataType = // 推断AS操作符的返回类型
        SqlStdOperatorTable.AS.inferReturnType(f, Lists.newArrayList(measureType)); // 传入操作数：MEASURE(DOUBLE)
    assertThat(dataType, is(measureType)); // 断言：验证返回类型仍然是MEASURE(DOUBLE)，AS操作符不会移除MEASURE包装
  }

  @Test void testCustomDecimalPlusReturnTypeInference() { // 测试方法：测试自定义类型系统下的DECIMAL加法返回类型推断
    final SqlTypeFactoryImpl f = new Fixture().customTypeFactory; // 获取自定义类型工厂实例
    RelDataType operand1 = f.createSqlType(SqlTypeName.DECIMAL, 38, 10); // 创建第一个操作数：DECIMAL类型，精度38，小数位数10
    RelDataType operand2 = f.createSqlType(SqlTypeName.DECIMAL, 38, 20); // 创建第二个操作数：DECIMAL类型，精度38，小数位数20

    RelDataType dataType = SqlStdOperatorTable.PLUS.inferReturnType(f, Lists // 推断加法运算的结果类型
            .newArrayList(operand1, operand2)); // 传入操作数列表
    assertThat(dataType.getSqlTypeName(), is(SqlTypeName.DECIMAL)); // 断言：验证类型名称是DECIMAL
    assertThat(dataType.getPrecision(), is(38)); // 断言：验证精度是38（计算值超过38，被限制为最大值）
    assertThat(dataType.getScale(), is(9)); // 断言：验证小数位数是9（计算值20+18+1=39，delta=1，调整为20-1=19，但受限于min(20,6)=6，最终调整为9）
  }

  @Test void testCustomDecimalMultiplyReturnTypeInference() { // 测试方法：测试自定义类型系统下的DECIMAL乘法返回类型推断
    final SqlTypeFactoryImpl f = new Fixture().customTypeFactory; // 获取自定义类型工厂实例
    RelDataType operand1 = f.createSqlType(SqlTypeName.DECIMAL, 2, 4); // 创建第一个操作数：DECIMAL类型，精度2，小数位数4
    RelDataType operand2 = f.createSqlType(SqlTypeName.DECIMAL, 3, 5); // 创建第二个操作数：DECIMAL类型，精度3，小数位数5

    RelDataType dataType = SqlStdOperatorTable.MULTIPLY.inferReturnType(f, Lists // 推断乘法运算的结果类型
            .newArrayList(operand1, operand2)); // 传入操作数列表
    assertThat(dataType.getSqlTypeName(), is(SqlTypeName.DECIMAL)); // 断言：验证类型名称是DECIMAL
    assertThat(dataType.getPrecision(), is(6)); // 断言：验证精度是6（根据自定义规则：2*3=6）
    assertThat(dataType.getScale(), is(20)); // 断言：验证小数位数是20（根据自定义规则：4*5=20）
  }

  @Test void testCustomDecimalDivideReturnTypeInference() { // 测试方法：测试自定义类型系统下的DECIMAL除法返回类型推断
    final SqlTypeFactoryImpl f = new Fixture().customTypeFactory; // 获取自定义类型工厂实例
    RelDataType operand1 = f.createSqlType(SqlTypeName.DECIMAL, 28, 10); // 创建被除数：DECIMAL类型，精度28，小数位数10
    RelDataType operand2 = f.createSqlType(SqlTypeName.DECIMAL, 38, 20); // 创建除数：DECIMAL类型，精度38，小数位数20

    RelDataType dataType = SqlStdOperatorTable.DIVIDE.inferReturnType(f, Lists // 推断除法运算的结果类型
            .newArrayList(operand1, operand2)); // 传入操作数列表
    assertThat(dataType.getSqlTypeName(), is(SqlTypeName.DECIMAL)); // 断言：验证类型名称是DECIMAL
    assertThat(dataType.getPrecision(), is(10)); // 断言：验证精度是10（根据自定义规则：|28-38|=10）
    assertThat(dataType.getScale(), is(10)); // 断言：验证小数位数是10（根据自定义规则：|10-20|=10）
  }

  @Test void testCustomDecimalModReturnTypeInference() { // 测试方法：测试自定义类型系统下的DECIMAL取模返回类型推断
    final SqlTypeFactoryImpl f = new Fixture().customTypeFactory; // 获取自定义类型工厂实例
    RelDataType operand1 = f.createSqlType(SqlTypeName.DECIMAL, 28, 10); // 创建被模数：DECIMAL类型，精度28，小数位数10
    RelDataType operand2 = f.createSqlType(SqlTypeName.DECIMAL, 38, 20); // 创建模数：DECIMAL类型，精度38，小数位数20

    RelDataType dataType = SqlStdOperatorTable.MOD.inferReturnType(f, Lists // 推断取模运算的结果类型
            .newArrayList(operand1, operand2)); // 传入操作数列表
    assertThat(dataType.getSqlTypeName(), is(SqlTypeName.DECIMAL)); // 断言：验证类型名称是DECIMAL
    assertThat(dataType.getPrecision(), is(28)); // 断言：验证精度是28（根据自定义规则：返回被模数的精度）
    assertThat(dataType.getScale(), is(10)); // 断言：验证小数位数是10（根据自定义规则：返回被模数的小数位数）
  }

  /** Tests that when inferring the return type for a timestamp function that takes a precision, // 方法注释：测试时间戳函数（带精度参数）的返回类型推断时，是否使用类型系统定义的最大精度
   * the maximum precision as defined by the type system is used, rather than the default max // 而不是使用默认的最大精度
   * precision.
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6262">[CALCITE-6262] // JIRA问题链接：CURRENT_TIMESTAMP(P)忽略了DataTypeSystem#getMaxPrecision
   * CURRENT_TIMESTAMP(P) ignores DataTypeSystem#getMaxPrecision</a>. */
  @Test void testCustomMaxTimestampPrecisionTimeFunctionReturnTypeInference() { // 测试方法：测试自定义最大时间戳精度时的时间函数返回类型推断
    final SqlTypeFactoryImpl f = new Fixture().customTypeFactory; // 获取自定义类型工厂实例
    final SqlLiteral sqlOperand = // 创建SQL字面量：表示时间戳精度参数
        SqlTypeName.INTEGER.createLiteral( // 创建INTEGER类型的字面量
            String.valueOf(CustomTypeSystem.CUSTOM_MAX_TIMESTAMP_PRECISION), SqlParserPos.ZERO); // 值为自定义的最大时间戳精度，位置为ZERO

    final RelDataType dataType = // 推断LOCALTIMESTAMP函数的返回类型
        SqlStdOperatorTable.LOCALTIMESTAMP.inferReturnType( // 调用LOCALTIMESTAMP操作符的推断方法
            new SqlOperatorBinding(f, SqlStdOperatorTable.LOCALTIMESTAMP) { // 创建操作符绑定的匿名子类
            @Override public int getOperandCount() { // 覆盖方法：返回操作数数量
              return 1; // 返回1，表示有1个操作数（精度参数）
            }

            @Override public RelDataType getOperandType(int ordinal) { // 覆盖方法：返回指定位置操作数的类型
              return sqlOperand.createSqlType(f); // 返回操作数的SQL类型
            }

            @Override public CalciteException newError(Resources.ExInst<SqlValidatorException> e) { // 覆盖方法：创建错误异常
              return null; // 返回null（测试用例不需要处理错误）
            }

            @Override public <T> @Nullable T getOperandLiteralValue(int ordinal, Class<T> clazz) { // 覆盖方法：获取操作数的字面量值
              return sqlOperand.getValueAs(clazz); // 返回操作数的值，转换为指定类型
            }
          });
    assertThat(dataType.getSqlTypeName(), is(SqlTypeName.TIMESTAMP)); // 断言：验证类型名称是TIMESTAMP
    assertThat(dataType.getPrecision(), // 断言：验证精度值
        is(CustomTypeSystem.CUSTOM_MAX_TIMESTAMP_PRECISION)); // 验证精度等于自定义的最大时间戳精度
  }
}
