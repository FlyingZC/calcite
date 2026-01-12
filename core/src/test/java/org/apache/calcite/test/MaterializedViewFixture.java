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
package org.apache.calcite.test;  // 声明包名，该类属于org.apache.calcite.test测试包

import org.apache.calcite.util.Pair;  // 导入Pair工具类，用于存储键值对
import org.apache.calcite.util.Util;  // 导入Util工具类，提供通用工具方法

import com.google.common.collect.ImmutableList;  // 导入Google Guava的不可变列表类

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入空值检查注解

import java.util.function.Predicate;  // 导入Java 8的函数式接口Predicate，用于谓词判断

/**
 * Fluent class that contains information necessary to run a test.
 * 这是一个流式风格的测试辅助类，用于封装运行物化视图测试所需的所有信息
 * 该类采用Builder模式的变体，通过链式调用方法来配置测试参数
 * 主要用于测试Calcite的物化视图优化功能，验证查询是否能够被物化视图替换
 * 提供了便捷的方法来设置查询语句、物化视图列表、验证器等测试参数
 */
public class MaterializedViewFixture {  // 定义物化视图测试固定装置类，用于封装测试上下文
  // 测试用的SQL查询语句，这个查询将被测试是否可以使用物化视图进行优化
  public final String query;  // 查询语句字符串，表示需要被优化的目标SQL查询
  // 物化视图测试器对象，负责执行实际的测试逻辑和验证
  public final MaterializedViewTester tester;  // 测试器实例，包含测试执行和验证的核心逻辑
  // 可选的Schema规范，指定测试使用的数据库模式（Schema），可能为null表示使用默认模式
  public final CalciteAssert.@Nullable SchemaSpec schemaSpec;  // Schema规范，定义测试的数据源结构，可为空
  // 物化视图列表，每个Pair表示一个物化视图的名称和对应的SQL定义
  public final ImmutableList<Pair<String, String>> materializationList;  // 不可变的物化视图列表，存储(视图名, 视图SQL)对
  // 可选的结果验证器，用于检查查询结果是否符合预期，可能为null表示不进行额外验证
  public final @Nullable Predicate<String> checker;  // 谓词验证器，用于验证查询输出结果，可为空

  // 静态工厂方法，创建一个基本的MaterializedViewFixture实例
  // 参数query: 要测试的SQL查询语句
  // 参数tester: 物化视图测试器实例
  // 返回值: 新创建的MaterializedViewFixture对象，包含基本的查询和测试器信息
  public static MaterializedViewFixture create(String query,  // 创建测试固定装置的静态工厂方法，接收查询语句和测试器
      MaterializedViewTester tester) {  // 物化视图测试器参数
    return new MaterializedViewFixture(tester, query, null, ImmutableList.of(),  // 返回新实例，schemaSpec为null，物化视图列表为空，checker为null
        null);  // checker参数设为null
  }

  // 私有构造方法，通过Builder模式创建实例，确保对象不可变性
  // 参数tester: 物化视图测试器
  // 参数query: SQL查询语句
  // 参数schemaSpec: Schema规范（可为null）
  // 参数materializationList: 物化视图列表
  // 参数checker: 结果验证器（可为null）
  private MaterializedViewFixture(MaterializedViewTester tester, String query,  // 私有构造方法，初始化所有成员变量
      CalciteAssert.@Nullable SchemaSpec schemaSpec,  // Schema规范参数
      ImmutableList<Pair<String, String>> materializationList,  // 物化视图列表参数
      @Nullable Predicate<String> checker) {  // 验证器参数
    this.query = query;  // 保存查询语句到成员变量
    this.tester = tester;  // 保存测试器到成员变量
    this.schemaSpec = schemaSpec;  // 保存Schema规范到成员变量
    this.materializationList = materializationList;  // 保存物化视图列表到成员变量
    this.checker = checker;  // 保存验证器到成员变量
  }

  // 执行测试并验证查询成功使用了物化视图优化
  // 调用tester的checkMaterialize方法来验证物化视图优化是否成功
  // 如果物化视图优化失败，该方法会抛出断言异常
  public void ok() {  // 验证物化视图优化成功的测试方法
    tester.checkMaterialize(this);  // 委托给测试器执行物化视图验证，传入当前fixture对象
  }

  // 执行测试并验证查询没有使用物化视图优化
  // 调用tester的checkNoMaterialize方法来验证物化视图没有被使用
  // 如果错误地使用了物化视图，该方法会抛出断言异常
  public void noMat() {  // 验证不应该使用物化视图优化的测试方法
    tester.checkNoMaterialize(this);  // 委托给测试器执行验证，确保没有使用物化视图
  }

  // 设置默认的Schema规范，返回新的MaterializedViewFixture实例
  // 如果新的schemaSpec与当前相同，则返回当前对象（不变性优化）
  // 参数schemaSpec: 要设置的Schema规范
  // 返回值: 新的MaterializedViewFixture实例或当前实例（如果值未改变）
  public MaterializedViewFixture withDefaultSchemaSpec(  // 设置Schema规范的流式方法
      CalciteAssert.@Nullable SchemaSpec schemaSpec) {  // Schema规范参数
    if (schemaSpec == this.schemaSpec) {  // 如果新值与当前值相同
      return this;  // 返回当前对象，避免创建新实例
    }
    return new MaterializedViewFixture(tester, query, schemaSpec,  // 创建并返回新实例，使用新的schemaSpec
        materializationList, checker);  // 保持其他参数不变
  }

  // 设置物化视图列表，返回新的MaterializedViewFixture实例
  // 参数materialize: 可迭代的物化视图集合，每个元素是(视图名, 视图SQL)对
  // 返回值: 新的MaterializedViewFixture实例或当前实例（如果值未改变）
  public MaterializedViewFixture withMaterializations(  // 设置物化视图列表的流式方法
      Iterable<? extends Pair<String, String>> materialize) {  // 物化视图集合参数
    final ImmutableList<Pair<String, String>> materializationList =  // 将可迭代集合转换为不可变列表
        ImmutableList.copyOf(materialize);  // 使用Guava的copyOf方法创建不可变副本
    if (materializationList.equals(this.materializationList)) {  // 如果新列表与当前列表相同
      return this;  // 返回当前对象，避免创建新实例
    }
    return new MaterializedViewFixture(tester, query, schemaSpec,  // 创建并返回新实例，使用新的物化视图列表
        materializationList, checker);  // 保持其他参数不变
  }

  // 设置查询语句，返回新的MaterializedViewFixture实例
  // 参数query: 新的SQL查询语句
  // 返回值: 新的MaterializedViewFixture实例或当前实例（如果值未改变）
  public MaterializedViewFixture withQuery(String query) {  // 设置查询语句的流式方法
    if (query.equals(this.query)) {  // 如果新查询与当前查询相同
      return this;  // 返回当前对象，避免创建新实例
    }
    return new MaterializedViewFixture(tester, query, schemaSpec,  // 创建并返回新实例，使用新的查询语句
        materializationList, checker);  // 保持其他参数不变
  }

  // 设置结果验证器，返回新的MaterializedViewFixture实例
  // 参数checker: 新的结果验证器，是一个谓词函数，接收查询结果字符串并返回是否验证通过
  // 返回值: 新的MaterializedViewFixture实例或当前实例（如果值未改变）
  public MaterializedViewFixture withChecker(Predicate<String> checker) {  // 设置验证器的流式方法
    if (checker == this.checker) {  // 如果新验证器与当前验证器相同（对象引用相同）
      return this;  // 返回当前对象，避免创建新实例
    }
    return new MaterializedViewFixture(tester, query, schemaSpec,  // 创建并返回新实例，使用新的验证器
        materializationList, checker);  // 保持其他参数不变
  }

  // 设置验证器为检查结果是否包含指定的字符串
  // 这是一个便捷方法，用于创建一个验证器来检查查询结果是否包含所有预期的字符串
  // 参数expectedStrings: 可变参数，期望在结果中包含的所有字符串
  // 返回值: 新的MaterializedViewFixture实例，其验证器会检查结果是否包含所有期望字符串
  public MaterializedViewFixture checkingThatResultContains(  // 设置结果包含检查的流式方法
      String... expectedStrings) {  // 可变参数，期望结果中包含的字符串数组
    return withChecker(s -> resultContains(s, expectedStrings));  // 创建lambda谓词并设置为新验证器
  }

  /** Returns whether the result contains all the given strings.
   * 静态工具方法，检查结果字符串是否包含所有给定的期望字符串
   * 参数result: 要检查的查询结果字符串
   * 参数expected: 可变参数，期望在结果中找到的所有字符串
   * 返回值: 如果结果包含所有期望字符串则返回true，否则返回false
   * 该方法使用Util.toLinux()将字符串转换为Linux格式（统一换行符），确保跨平台一致性
   */
  public static boolean resultContains(String result, final String... expected) {  // 静态方法，检查结果是否包含所有期望字符串
    String sLinux = Util.toLinux(result);  // 将结果字符串转换为Linux格式（统一换行符为\n）
    for (String st : expected) {  // 遍历所有期望字符串
      if (!sLinux.contains(Util.toLinux(st))) {  // 如果结果不包含当前期望字符串（都转换为Linux格式比较）
        return false;  // 只要有一个期望字符串不包含就返回false
      }
    }
    return true;  // 所有期望字符串都包含在结果中，返回true
  }

}  // 类定义结束
