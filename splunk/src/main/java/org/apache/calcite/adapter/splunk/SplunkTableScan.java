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
// Apache许可证头部声明，说明该文件遵循Apache 2.0许可证
package org.apache.calcite.adapter.splunk; // 声明包名，该类属于org.apache.calcite.adapter.splunk包，是Calcite框架中Splunk适配器的一部分

import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入EnumerableConvention，表示可枚举的约定，用于定义物理实现的特征
import org.apache.calcite.adapter.enumerable.EnumerableRel; // 导入EnumerableRel接口，表示可枚举的关系表达式，可以生成LINQ4J表达式
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor; // 导入EnumerableRelImplementor，用于实现可枚举关系表达式的工具类
import org.apache.calcite.adapter.enumerable.PhysType; // 导入PhysType接口，表示物理类型，用于描述Java类型和关系类型的映射
import org.apache.calcite.adapter.enumerable.PhysTypeImpl; // 导入PhysTypeImpl，PhysType接口的实现类
import org.apache.calcite.config.CalciteSystemProperty; // 导入CalciteSystemProperty，用于访问Calcite的系统属性配置
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入BlockBuilder，用于构建代码块表达式
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression，表示LINQ4J表达式树的基类
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions，用于创建各种表达式对象的工具类
import org.apache.calcite.linq4j.tree.Types; // 导入Types，用于处理类型相关的工具类
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster，表示关系优化集群，包含优化器共享的资源
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner，表示关系优化器，用于执行查询优化
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable，表示优化器中的表对象
import org.apache.calcite.rel.RelWriter; // 导入RelWriter，用于将关系表达式输出为可读的文本格式
import org.apache.calcite.rel.core.TableScan; // 导入TableScan，表示表扫描操作的关系表达式基类
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType，表示关系数据类型，描述表的行类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory，用于创建关系数据类型的工厂
import org.apache.calcite.runtime.Hook; // 导入Hook，用于在查询执行过程中插入钩子，便于调试和监控
import org.apache.calcite.util.Util; // 导入Util，Calcite的工具类，提供各种通用方法

import com.google.common.collect.ImmutableList; // 导入ImmutableList，Google Guava库提供的不可变列表实现
import com.google.common.collect.ImmutableMap; // 导入ImmutableMap，Google Guava库提供的不可变Map实现

import java.lang.reflect.Method; // 导入Method，用于反射操作，获取方法对象
import java.util.AbstractList; // 导入AbstractList，Java集合框架中的抽象列表类
import java.util.Arrays; // 导入Arrays，提供数组操作的工具类
import java.util.List; // 导入List，Java集合框架中的列表接口
import java.util.Map; // 导入Map，Java集合框架中的映射接口

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * Relational expression representing a scan of Splunk.
 * 表示Splunk扫描的关系表达式
 *
 * <p>Splunk does not have tables, but it's easiest to imagine that a Splunk
 * instance is one large table. This "table" does not have a fixed set of
 * columns (Splunk calls them "fields") but each query specifies the fields that
 * it wants. It also specifies a search expression, and optionally earliest and
 * latest dates.
 * Splunk没有传统的表结构，但可以想象一个Splunk实例是一个巨大的表。这个"表"没有固定的列集合
 * （Splunk称之为"字段"fields），但每个查询可以指定它需要的字段。查询还指定搜索表达式，
 * 以及可选的最早和最晚时间范围。
 */
public class SplunkTableScan // 定义SplunkTableScan类，表示对Splunk数据源的扫描操作
    extends TableScan // 继承TableScan基类，表示这是一个表扫描操作
    implements EnumerableRel { // 实现EnumerableRel接口，表示该关系表达式可以被实现为可枚举的Java代码
  final SplunkTable splunkTable; // 成员变量：引用SplunkTable对象，表示Splunk表的元数据和配置信息
  final String search; // 成员变量：Splunk搜索表达式字符串，用于指定查询条件和过滤规则
  final String earliest; // 成员变量：最早时间范围字符串，用于限制查询的时间范围起始点
  final String latest; // 成员变量：最晚时间范围字符串，用于限制查询的时间范围结束点
  final List<String> fieldList; // 成员变量：字段列表，包含查询需要返回的字段名称集合

  protected SplunkTableScan( // 构造方法：创建SplunkTableScan实例，protected修饰符允许子类访问
      RelOptCluster cluster, // 参数：关系优化集群，包含优化器共享的类型工厂等资源
      RelOptTable table, // 参数：优化器中的表对象，表示被扫描的表
      SplunkTable splunkTable, // 参数：SplunkTable对象，包含Splunk特定的表信息和配置
      String search, // 参数：搜索表达式字符串，用于构建Splunk查询
      String earliest, // 参数：最早时间字符串，用于指定查询的起始时间
      String latest, // 参数：最晚时间字符串，用于指定查询的结束时间
      List<String> fieldList) { // 参数：字段列表，指定查询需要返回的字段
    super( // 调用父类TableScan的构造方法
        cluster, // 传递关系优化集群给父类
        cluster.traitSetOf(EnumerableConvention.INSTANCE), // 设置特征集合为可枚举约定，表示该操作可以被实现为可枚举的代码
        ImmutableList.of(), // 传递空的输入列表，因为表扫描没有输入关系表达式
        table); // 传递表对象给父类
    this.splunkTable = requireNonNull(splunkTable, "splunkTable"); // 初始化splunkTable成员变量，使用requireNonNull进行非空校验
    this.search = requireNonNull(search, "search"); // 初始化search成员变量，使用requireNonNull进行非空校验
    this.earliest = earliest; // 初始化earliest成员变量，可以为null
    this.latest = latest; // 初始化latest成员变量，可以为null
    this.fieldList = fieldList; // 初始化fieldList成员变量，可以为null
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写explainTerms方法，用于生成关系表达式的可读描述
    return super.explainTerms(pw) // 调用父类的explainTerms方法，获取基础的描述信息
        .item("table", table.getQualifiedName()) // 添加表的限定名称到描述中
        .item("earliest", earliest) // 添加最早时间到描述中
        .item("latest", latest) // 添加最晚时间到描述中
        .item("fieldList", fieldList); // 添加字段列表到描述中
  }

  @Override public void register(RelOptPlanner planner) { // 重写register方法，向优化器注册优化规则
    planner.addRule(SplunkPushDownRule.FILTER); // 注册过滤器下推规则，将过滤条件下推到Splunk查询中
    planner.addRule(SplunkPushDownRule.FILTER_ON_PROJECT); // 注册在投影上的过滤器下推规则
    planner.addRule(SplunkPushDownRule.PROJECT); // 注册投影下推规则，将投影下推到Splunk查询中
    planner.addRule(SplunkPushDownRule.PROJECT_ON_FILTER); // 注册在过滤器上的投影下推规则
  }

  @Override public RelDataType deriveRowType() { // 重写deriveRowType方法，推导并返回该扫描操作的行类型
    final RelDataTypeFactory.Builder builder = // 创建关系数据类型构建器，用于构建行类型
        getCluster().getTypeFactory().builder(); // 从集群中获取类型工厂并创建构建器
    for (String field : fieldList) { // 遍历字段列表中的每个字段名
      // REVIEW: is case-sensitive match what we want here?
      // 注释：这里需要确认是否使用大小写敏感匹配，可能需要根据实际需求调整
      builder.add(table.getRowType().getField(field, true, false)); // 从表的行类型中获取指定字段并添加到构建器中，参数true表示大小写不敏感，false表示不抛出异常
    }
    return builder.build(); // 构建并返回最终的行类型
  }

  private static final Method METHOD = // 静态常量：通过反射获取SplunkTableQueryable类的createQuery方法引用
      Types.lookupMethod( // 查找方法
          SplunkTable.SplunkTableQueryable.class, // 指定要查找的类：SplunkTable的内部类SplunkTableQueryable
          "createQuery", // 方法名称：createQuery
          String.class, // 第一个参数类型：String，用于search参数
          String.class, // 第二个参数类型：String，用于earliest参数
          String.class, // 第三个参数类型：String，用于latest参数
          List.class); // 第四个参数类型：List，用于fieldList参数

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 重写implement方法，实现该关系表达式为可执行的Java代码
    Map map = ImmutableMap.builder() // 创建不可变Map构建器，用于构建查询参数映射
        .put("search", search) // 添加搜索表达式到映射中
        .put("earliest", Util.first(earliest, "")) // 添加最早时间到映射中，如果为null则使用空字符串
        .put("latest", Util.first(latest, "")) // 添加最晚时间到映射中，如果为null则使用空字符串
        .put("fieldList", fieldList) // 添加字段列表到映射中
        .build(); // 构建不可变Map
    if (CalciteSystemProperty.DEBUG.value()) { // 如果启用了调试模式
      System.out.println("Splunk: " + map); // 输出Splunk查询参数到控制台
    }
    Hook.QUERY_PLAN.run(map); // 运行查询计划钩子，允许外部监听器获取查询计划信息

    final PhysType physType = // 创建物理类型对象，描述Java类型和关系类型的映射
        PhysTypeImpl.of( // 使用PhysTypeImpl创建实例
            implementor.getTypeFactory(), // 从实现器中获取类型工厂
            getRowType(), // 获取当前扫描操作的行类型
            pref.preferCustom()); // 根据偏好设置是否优先使用自定义类型
    final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器，用于构建Java代码块
    return implementor.result( // 返回实现结果
        physType, // 传递物理类型
        builder.append( // 向代码块中追加表达式
            Expressions.call( // 创建方法调用表达式
                table.getExpression(SplunkTable.SplunkTableQueryable.class), // 获取SplunkTableQueryable类的表达式对象
                METHOD, // 使用之前反射获取的createQuery方法
                Expressions.constant(search), // 创建搜索表达式的常量表达式
                Expressions.constant(earliest), // 创建最早时间的常量表达式
                Expressions.constant(latest), // 创建最晚时间的常量表达式
                fieldList == null // 判断字段列表是否为null
                    ? Expressions.constant(null) // 如果为null，创建null常量表达式
                    : constantStringList(fieldList))) // 如果不为null，调用constantStringList方法创建字段列表表达式
            .toBlock()); // 将代码块构建器转换为Block表达式
  }

  private static Expression constantStringList(final List<String> strings) { // 私有静态方法：将字符串列表转换为常量表达式列表
    return Expressions.call( // 创建方法调用表达式
        Arrays.class, // 调用Arrays类
        "asList", // 调用asList方法
        Expressions.newArrayInit( // 创建数组初始化表达式
            Object.class, // 数组元素类型为Object
            new AbstractList<Expression>() { // 创建抽象列表的匿名子类，用于延迟生成表达式
              @Override public Expression get(int index) { // 重写get方法，根据索引获取表达式
                return Expressions.constant(strings.get(index)); // 返回指定索引的字符串常量表达式
              }

              @Override public int size() { // 重写size方法，返回列表大小
                return strings.size(); // 返回字符串列表的大小
              }
            })); // 传递匿名列表作为数组初始化的元素来源
  }
} // 类定义结束
