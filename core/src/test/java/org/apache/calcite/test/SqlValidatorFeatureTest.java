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
package org.apache.calcite.test; // 定义包名，该类位于org.apache.calcite.test包下

import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型
import org.apache.calcite.runtime.CalciteContextException; // 导入CalciteContextException类，用于处理带有位置信息的Calcite异常
import org.apache.calcite.runtime.CalciteException; // 导入CalciteException类，表示Calcite运行时异常
import org.apache.calcite.runtime.Feature; // 导入Feature类，表示SQL特性或功能
import org.apache.calcite.sql.SqlOperatorTable; // 导入SqlOperatorTable接口，提供SQL操作符表
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SqlParserPos类，表示SQL解析位置信息
import org.apache.calcite.sql.validate.SqlValidatorCatalogReader; // 导入SqlValidatorCatalogReader接口，用于SQL验证时读取目录信息
import org.apache.calcite.sql.validate.SqlValidatorImpl; // 导入SqlValidatorImpl类，SQL验证器的实现类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，标记可能为null的值
import org.junit.jupiter.api.Test; // 导入Test注解，用于标记JUnit测试方法

import static org.apache.calcite.util.Static.RESOURCE; // 导入RESOURCE静态变量，提供SQL特性资源

import static java.util.Objects.requireNonNull; // 导入requireNonNull方法，用于检查对象是否为null

/**
 * SqlValidatorFeatureTest verifies that features can be independently enabled
 * or disabled.
 * // SqlValidatorFeatureTest类用于验证SQL特性可以独立启用或禁用
 * // 该类继承自SqlValidatorTestCase，提供了测试SQL验证器特性控制的基础设施
 * // 主要测试各种SQL特性（如DISTINCT、ORDER BY DESC、INTERSECT、EXCEPT、MULTISET、TABLESAMPLE等）是否可以正确地启用和禁用
 * // 通过自定义FeatureValidator类来拦截特性验证，当特性被禁用时抛出异常
 */
class SqlValidatorFeatureTest extends SqlValidatorTestCase { // 定义SqlValidatorFeatureTest类，继承自SqlValidatorTestCase
  private static final String FEATURE_DISABLED = "feature_disabled"; // 定义静态常量，表示特性被禁用时的错误消息

  private @Nullable Feature disabledFeature; // 定义成员变量，存储当前被禁用的SQL特性，可能为null

  @Override public SqlValidatorFixture fixture() { // 重写fixture方法，配置SQL验证器测试装置
    return super.fixture() // 调用父类的fixture方法获取基础配置
        .withFactory(f -> f.withValidator(FeatureValidator::new)); // 使用工厂方法配置自定义的FeatureValidator验证器
  }

  @Test void testDistinct() { // 定义测试方法，测试DISTINCT特性的启用和禁用
    checkFeature( // 调用checkFeature方法检查DISTINCT特性
        "select ^distinct^ name from dept", // 测试SQL语句，^符号标记特性位置
        RESOURCE.sQLFeature_E051_01()); // 传入DISTINCT特性资源（SQL标准特性E051-01）
  }

  @Test void testOrderByDesc() { // 定义测试方法，测试ORDER BY DESC特性的启用和禁用
    checkFeature( // 调用checkFeature方法检查ORDER BY DESC特性
        "select name from dept order by ^name desc^", // 测试SQL语句，^符号标记DESC特性位置
        RESOURCE.sQLConformance_OrderByDesc()); // 传入ORDER BY DESC特性资源
  }

  // NOTE jvs 6-Mar-2006:  carets don't come out properly placed
  // for INTERSECT/EXCEPT, so don't bother
  // 注意：jvs在2006年3月6日指出，INTERSECT/EXCEPT的^符号位置不能正确显示，所以不在SQL语句中使用

  @Test void testIntersect() { // 定义测试方法，测试INTERSECT集合操作特性的启用和禁用
    checkFeature( // 调用checkFeature方法检查INTERSECT特性
        "^select name from dept intersect select name from dept^", // 测试SQL语句，^符号标记整个INTERSECT操作
        RESOURCE.sQLFeature_F302()); // 传入INTERSECT特性资源（SQL标准特性F302）
  }

  @Test void testExcept() { // 定义测试方法，测试EXCEPT集合操作特性的启用和禁用
    checkFeature( // 调用checkFeature方法检查EXCEPT特性
        "^select name from dept except select name from dept^", // 测试SQL语句，^符号标记整个EXCEPT操作
        RESOURCE.sQLFeature_E071_03()); // 传入EXCEPT特性资源（SQL标准特性E071-03）
  }

  @Test void testMultiset() { // 定义测试方法，测试MULTISET集合类型的启用和禁用
    checkFeature( // 调用checkFeature方法检查MULTISET特性
        "values ^multiset[1]^", // 测试SQL语句，^符号标记multiset数组字面量
        RESOURCE.sQLFeature_S271()); // 传入MULTISET特性资源（SQL标准特性S271）

    checkFeature( // 调用checkFeature方法检查MULTISET子查询特性
        "values ^multiset(select * from dept)^", // 测试SQL语句，^符号标记multiset子查询
        RESOURCE.sQLFeature_S271()); // 传入MULTISET特性资源（SQL标准特性S271）
  }

  @Test void testTablesample() { // 定义测试方法，测试TABLESAMPLE表采样特性的启用和禁用
    checkFeature( // 调用checkFeature方法检查TABLESAMPLE特性
        "select name from ^dept tablesample bernoulli(50)^", // 测试SQL语句，^符号标记tablesample bernoulli采样
        RESOURCE.sQLFeature_T613()); // 传入TABLESAMPLE特性资源（SQL标准特性T613）

    checkFeature( // 调用checkFeature方法检查TABLESAMPLE substitute特性
        "select name from ^dept tablesample substitute('sample_dept')^", // 测试SQL语句，^符号标记tablesample substitute采样
        RESOURCE.sQLFeatureExt_T613_Substitution()); // 传入TABLESAMPLE substitute特性资源（扩展特性）
  }

  private void checkFeature(String sql, Feature feature) { // 定义私有方法，用于检查SQL特性的启用和禁用
    // Test once with feature enabled:  should pass
    sql(sql).ok(); // 第一次测试：特性启用时，SQL应该验证通过（ok方法期望验证成功）

    // Test once with feature disabled:  should fail
    try { // 第二次测试：特性禁用时，SQL应该验证失败
      disabledFeature = feature; // 设置当前禁用的特性
      sql(sql).fails(FEATURE_DISABLED); // 验证SQL应该失败，并期望错误消息为FEATURE_DISABLED
    } finally { // 使用finally确保清理
      disabledFeature = null; // 清理禁用特性，恢复为null
    }
  }

  //~ Inner Classes ----------------------------------------------------------

  /** Extension to {@link SqlValidatorImpl} that validates features. */
  // 内部类注释：FeatureValidator是SqlValidatorImpl的扩展，用于验证SQL特性
  // 该类重写了validateFeature方法，在特性验证时检查特性是否被禁用
  // 如果特性被禁用，则抛出CalciteContextException异常，阻止SQL验证通过
  public class FeatureValidator extends SqlValidatorImpl { // 定义内部类FeatureValidator，继承自SqlValidatorImpl
    protected FeatureValidator( // 定义构造方法，初始化FeatureValidator
        SqlOperatorTable opTab, // 参数：SQL操作符表，提供SQL操作符信息
        SqlValidatorCatalogReader catalogReader, // 参数：目录读取器，提供表和列的元数据信息
        RelDataTypeFactory typeFactory, // 参数：类型工厂，用于创建和操作关系数据类型
        Config config) { // 参数：配置对象，包含验证器的配置信息
      super(opTab, catalogReader, typeFactory, config); // 调用父类SqlValidatorImpl的构造方法，初始化验证器
    }

    protected void validateFeature( // 重写validateFeature方法，用于验证SQL特性
        Feature feature, // 参数：要验证的特性对象
        SqlParserPos pos) { // 参数：特性在SQL中的位置信息
      requireNonNull(pos, "pos"); // 检查位置参数是否为null，如果为null则抛出NullPointerException
      if (feature.equals(disabledFeature)) { // 判断当前特性是否是被禁用的特性
        CalciteException ex = // 创建CalciteException异常对象
            new CalciteException( // 异常构造函数
                FEATURE_DISABLED, // 异常消息：特性被禁用
                null); // 异常原因：无
        throw new CalciteContextException( // 抛出带有位置信息的Calcite上下文异常
            "location", // 异常描述：位置信息
            ex, // 原始异常对象
            pos.getLineNum(), // 起始行号
            pos.getColumnNum(), // 起始列号
            pos.getEndLineNum(), // 结束行号
            pos.getEndColumnNum()); // 结束列号
      } // 如果特性未被禁用，则不做任何操作，允许特性正常使用
    } // validateFeature方法结束
  } // FeatureValidator类结束
} // SqlValidatorFeatureTest类结束
