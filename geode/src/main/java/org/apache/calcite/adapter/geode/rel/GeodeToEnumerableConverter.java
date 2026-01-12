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
package org.apache.calcite.adapter.geode.rel; // 包声明：Geode适配器的关系表达式包

// 导入Calcite核心枚举接口，表示可枚举的关系表达式，用于生成Java代码执行查询
import org.apache.calcite.adapter.enumerable.EnumerableRel;
// 导入枚举关系表达式实现器，负责将关系表达式转换为可执行的Java代码
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor;
// 导入Java行格式枚举，定义了Java中表示行的不同格式（如ARRAY、CUSTOM等）
import org.apache.calcite.adapter.enumerable.JavaRowFormat;
// 导入物理类型接口，表示关系类型到Java类型的映射
import org.apache.calcite.adapter.enumerable.PhysType;
// 导入物理类型实现类，提供了PhysType接口的具体实现
import org.apache.calcite.adapter.enumerable.PhysTypeImpl;
// 导入Geode关系表达式的实现上下文，用于在Geode适配器中传递实现信息
import org.apache.calcite.adapter.geode.rel.GeodeRel.GeodeImplementContext;
// 导入代码块构建器，用于构建Java代码块表达式
import org.apache.calcite.linq4j.tree.BlockBuilder;
// 导入表达式基类，表示LINQ4J中的表达式树节点
import org.apache.calcite.linq4j.tree.Expression;
// 导入表达式工具类，提供创建各种表达式的方法
import org.apache.calcite.linq4j.tree.Expressions;
// 导入方法调用表达式，表示方法调用的表达式节点
import org.apache.calcite.linq4j.tree.MethodCallExpression;
// 导入类型工具类，提供反射相关的类型查找功能
import org.apache.calcite.linq4j.tree.Types;
// 导入约定特征定义，定义了关系表达式的调用约定（如LOGICAL、PHYSICAL等）
import org.apache.calcite.plan.ConventionTraitDef;
// 导入关系优化集群，包含关系表达式的共享资源（如类型工厂、Rex构建器等）
import org.apache.calcite.plan.RelOptCluster;
// 导入关系优化成本接口，表示执行计划的成本估算
import org.apache.calcite.plan.RelOptCost;
// 导入关系优化规划器接口，用于优化和选择最佳执行计划
import org.apache.calcite.plan.RelOptPlanner;
// 导入关系特征集合，表示关系表达式的一组特征（如调用约定、排序、分布等）
import org.apache.calcite.plan.RelTraitSet;
// 导入关系节点接口，表示关系代数表达式树的节点
import org.apache.calcite.rel.RelNode;
// 导入转换器实现基类，用于将一种调用约定的关系表达式转换为另一种调用约定
import org.apache.calcite.rel.convert.ConverterImpl;
// 导入关系元数据查询接口，用于查询关系表达式的元数据（如行数、大小等）
import org.apache.calcite.rel.metadata.RelMetadataQuery;
// 导入关系数据类型接口，表示SQL类型系统中的数据类型
import org.apache.calcite.rel.type.RelDataType;
// 导入内置方法枚举，包含常用的内置方法引用
import org.apache.calcite.util.BuiltInMethod;
// 导入键值对类，表示不可变的键值对
import org.apache.calcite.util.Pair;
// 导入工具类，提供各种实用方法
import org.apache.calcite.util.Util;

// 导入可空注解，用于标记可能为null的返回值或参数
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入Java反射Method类，用于表示方法对象
import java.lang.reflect.Method;
// 导入抽象列表类，用于创建自定义的列表实现
import java.util.AbstractList;
// 导入数组列表类，提供动态数组实现
import java.util.ArrayList;
// 导入列表接口，表示有序集合
import java.util.List;
// 导入映射接口，表示键值对映射
import java.util.Map;

// 静态导入Geode规则工具类中的geodeFieldNames方法，用于获取Geode字段名称
import static org.apache.calcite.adapter.geode.rel.GeodeRules.geodeFieldNames;

// 静态导入Java Objects工具类的requireNonNull方法，用于非空检查
import static java.util.Objects.requireNonNull;

/**
 * Geode到可枚举转换器类
 * 
 * 【类作用】：
 * 这是一个关系表达式转换器，负责将Geode数据源的关系表达式（GeodeRel约定）
 * 转换为可枚举的关系表达式（EnumerableRel约定）。
 * 
 * 【核心功能】：
 * 1. 作为Calcite优化器中的规则转换节点，实现从Geode物理约定到可枚举约定的转换
 * 2. 将Geode特定的查询逻辑转换为可执行的Java代码（通过LINQ4J表达式树）
 * 3. 生成调用GeodeTable.GeodeQueryable#query方法的代码，实现从Geode数据源查询数据
 * 4. 处理字段映射、选择条件、聚合函数、分组、排序、限制等查询操作
 * 
 * 【继承关系】：
 * - 继承ConverterImpl：获得关系表达式转换的基础能力
 * - 实现EnumerableRel：表明该节点可以被枚举，可以生成Java代码执行
 * 
 * 【工作流程】：
 * 1. 接收GeodeRel约定的关系表达式作为输入
 * 2. 通过implement方法生成可执行的Java代码
 * 3. 调用GeodeTable的query方法执行实际查询
 * 4. 返回可枚举的结果集
 * 
 * 【使用场景】：
 * 当查询计划需要从Geode数据源读取数据时，优化器会使用此转换器
 * 将Geode扫描节点转换为可执行的代码节点。
 */
public class GeodeToEnumerableConverter extends ConverterImpl implements EnumerableRel { // 类定义：继承转换器基类并实现可枚举接口

  /**
   * 构造方法
   * 
   * 【方法作用】：
   * 创建一个Geode到可枚举的转换器实例，初始化关系表达式的基本属性。
   * 
   * 【参数说明】：
   * @param cluster 关系优化集群对象，包含类型工厂、Rex构建器等共享资源，
   *                用于在整个查询计划中共享上下文信息
   * @param traitSet 关系特征集合，定义了该关系表达式的各种特征属性，
   *                 如调用约定、排序方式、数据分布等
   * @param input 输入的关系节点，通常是GeodeRel约定下的关系表达式（如GeodeTableScan等），
   *              表示要转换的Geode数据源查询节点
   * 
   * 【实现细节】：
   * 1. 调用父类ConverterImpl的构造方法，传入必要的参数
   * 2. ConventionTraitDef.INSTANCE指定目标约定为可枚举约定（EnumerableConvention）
   * 3. 将输入节点（Geode约定）转换为输出节点（可枚举约定）
   * 
   * 【调用时机】：
   * 当优化器应用GeodeToEnumerableRule规则时，会创建此转换器实例
   * 来替换原始的Geode关系表达式节点。
   */
  protected GeodeToEnumerableConverter(RelOptCluster cluster, // 参数：关系优化集群，提供共享的查询上下文
      RelTraitSet traitSet, RelNode input) { // 参数：关系特征集合和输入关系节点
    super(cluster, ConventionTraitDef.INSTANCE, traitSet, input); // 调用父类构造方法，将Geode约定转换为可枚举约定
  }

  /**
   * 复制关系节点方法
   * 
   * 【方法作用】：
   * 创建当前关系节点的副本，可以指定新的特征集合和输入节点。
   * 这是Calcite关系表达式树的标准方法，用于在优化过程中创建修改后的节点。
   * 
   * 【参数说明】：
   * @param traitSet 新的关系特征集合，可以包含不同的约定、排序、分布等特征
   * @param inputs 新的输入节点列表，通常包含一个输入节点（即Geode关系表达式）
   * 
   * 【返回值】：
   * @return 返回一个新的GeodeToEnumerableConverter实例，具有指定的特征集合和输入节点
   * 
   * 【实现细节】：
   * 1. 使用sole(inputs)方法获取输入列表中的唯一元素（因为转换器只有一个输入）
   * 2. 创建新的GeodeToEnumerableConverter实例，保持相同的集群信息
   * 3. 使用新的特征集合和输入节点
   * 
   * 【调用时机】：
   * - 当优化器需要应用新的特征（如改变排序方式）时
   * - 当需要复制节点以进行等价变换时
   * - 在规则匹配和转换过程中
   */
  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，用于创建节点副本
    return new GeodeToEnumerableConverter( // 返回新的转换器实例
        getCluster(), traitSet, sole(inputs)); // 使用当前集群、新特征集和唯一输入节点
  }

  /**
   * 计算自身成本方法
   * 
   * 【方法作用】：
   * 估算执行此转换器节点所需的成本，用于优化器选择最优执行计划。
   * 转换器本身的成本通常很低，因为它只是将查询转换为可执行代码。
   * 
   * 【参数说明】：
   * @param planner 关系优化规划器，提供成本计算上下文和配置
   * @param mq 关系元数据查询接口，用于查询输入节点的元数据（如行数、大小等）
   * 
   * 【返回值】：
   * @return 返回估算的执行成本对象，包含CPU、IO和内存成本的估算值
   * 
   * 【实现细节】：
   * 1. 调用父类的computeSelfCost方法获取基础成本
   * 2. 使用requireNonNull确保成本不为null（防御性编程）
   * 3. 将基础成本乘以0.1，表示转换操作的成本相对较低
   * 4. 低成本鼓励优化器优先使用此转换路径
   * 
   * 【成本计算策略】：
   * - 转换器本身不执行实际数据操作，只是代码生成
   * - 因此成本应该远低于实际的数据扫描或连接操作
   * - 乘以0.1使得转换器在成本比较中具有优势
   * 
   * 【调用时机】：
   * - 优化器在选择最佳执行计划时
   * - 在应用规则转换后评估新计划的成本
   * - 在成本比较和剪枝过程中
   */
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写成本计算方法
      RelMetadataQuery mq) { // 参数：规划器和元数据查询接口
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq)); // 计算父类的基础成本并确保非空
    return cost.multiplyBy(.1); // 将成本乘以0.1，表示转换操作成本较低
  }

  /**
   * Geode查询方法引用常量
   * 
   * 【成员变量作用】：
   * 保存GeodeTable.GeodeQueryable#query方法的反射引用，
   * 用于在生成的Java代码中调用该方法。
   * 
   * 【方法签名】：
   * query(List<Pair<String, Class>> fields,
   *      List<Pair<String, String>> selectFields,
   *      List<Pair<String, String>> aggregateFunctions,
   *      List<String> groupByFields,
   *      List<String> whereClause,
   *      List<String> orderByFields,
   *      Long limit)
   * 
   * 【参数说明】：
   * - fields: 字段名称和类型的映射列表
   * - selectFields: 选择字段列表（字段名到别名的映射）
   * - aggregateFunctions: 聚合函数列表（函数名到别名的映射）
   * - groupByFields: 分组字段列表
   * - whereClause: WHERE条件子句列表
   * - orderByFields: 排序字段列表
   * - limit: 限制返回的行数
   * 
   * 【实现方式】：
   * 使用Types.lookupMethod通过反射查找并缓存方法引用，
   * 避免在每次代码生成时重复查找，提高性能。
   * 
   * 【使用场景】：
   * 在implement方法中构建表达式树时，使用此方法引用
   * 生成调用GeodeTable.query方法的代码。
   */
  private static final Method GEODE_QUERY_METHOD = // 常量：Geode查询方法的反射引用
      Types.lookupMethod(GeodeTable.GeodeQueryable.class, "query", List.class, // 查找GeodeTable.GeodeQueryable类的query方法
          List.class, List.class, List.class, List.class, List.class, List.class, // 方法参数类型：6个List和1个Long
          Long.class); // 最后一个参数类型：Long（limit值）

  /**
   * 实现可枚举关系表达式方法
   * 
   * 【方法作用】：
   * 将Geode关系表达式转换为可执行的Java代码块，生成调用Geode查询的代码。
   * 这是整个转换流程的核心方法，负责代码生成。
   * 
   * 【参数说明】：
   * @param implementor 可枚举关系表达式实现器，提供代码生成所需的工具和上下文
   * @param pref 偏好设置，指示生成的Java代码应该使用的行格式（如ARRAY、CUSTOM等）
   * 
   * 【返回值】：
   * @return 返回Result对象，包含生成的Java代码块和物理类型信息
   * 
   * 【实现步骤】：
   * 1. 创建Geode实现上下文，用于收集查询信息
   * 2. 遍历关系表达式树，从当前节点到扫描叶子节点，收集查询信息
   * 3. 获取行类型信息（SQL类型）
   * 4. 创建物理类型映射（SQL类型到Java类型）
   * 5. 创建字段类列表（抽象列表实现）
   * 6. 构建代码块，生成调用GeodeTable.query方法的表达式
   * 7. 返回实现结果
   * 
   * 【详细实现】：
   */
  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 重写implement方法，生成可执行代码

    // 步骤1：创建Geode实现上下文
    // 【作用】：创建一个空的上下文对象，用于在遍历关系树时收集查询相关信息
    // 【信息收集】：包括表引用、选择字段、聚合函数、分组、WHERE条件、排序、LIMIT等
    final GeodeImplementContext geodeImplementContext = new GeodeImplementContext(); // 创建Geode实现上下文实例

    // 步骤2：遍历关系表达式树
    // 【作用】：从当前转换器节点开始，向下遍历到Geode扫描叶子节点
    // 【实现】：将输入节点强制转换为GeodeRel接口，调用其implement方法
    // 【结果】：geodeImplementContext被填充，包含完整的查询信息
    ((GeodeRel) getInput()).implement(geodeImplementContext); // 调用输入节点的implement方法，收集查询信息

    // 步骤3：获取行类型
    // 【作用】：获取当前关系表达式的输出行类型（SQL类型系统）
    // 【包含】：字段名称、字段类型、是否可空等信息
    final RelDataType rowType = getRowType(); // 获取关系表达式的行类型

    // 步骤4：创建物理类型
    // 【作用】：将SQL类型映射为Java类型，用于生成Java代码
    // 【PhysType】：是Calcite的可枚举适配器类，负责SQL类型和Java类型的映射
    // 【映射关系】：SQL INTEGER -> Java Integer, SQL VARCHAR -> Java String等
    // 【格式】：使用JavaRowFormat.ARRAY格式，表示每行数据用Object[]数组表示
    final PhysType physType = // 创建物理类型实例
        PhysTypeImpl.of(implementor.getTypeFactory(), rowType, // 使用实现器的类型工厂和行类型
            pref.prefer(JavaRowFormat.ARRAY)); // 根据偏好选择ARRAY格式

    // 步骤5：创建字段类列表
    // 【作用】：创建一个抽象列表，提供字段对应的Java类类型
    // 【实现】：使用匿名内部类继承AbstractList，按需获取字段类型
    // 【优势】：延迟计算，不需要预先创建完整的类型列表
    final List<Class> physFieldClasses = new AbstractList<Class>() { // 创建抽象列表，按需提供字段类类型
      @Override public Class get(int index) { // 重写get方法，按索引获取字段类
        return physType.fieldClass(index); // 从物理类型中获取指定索引的字段类
      }

      @Override public int size() { // 重写size方法，返回字段数量
        return rowType.getFieldCount(); // 返回行类型中的字段总数
      }
    };

    // 步骤6：构建代码块
    // 【作用】：使用表达式元编程技术，生成调用GeodeTable.query方法的Java代码
    // 【BlockBuilder】：Calcite提供的代码块构建器，用于构建Java代码块表达式
    // 【表达式树】：构建LINQ4J表达式树，表示方法调用
    final BlockBuilder blockBuilder = new BlockBuilder(); // 创建代码块构建器
    blockBuilder.append( // 向代码块中追加表达式
        Expressions.call( // 创建方法调用表达式
            // 步骤6.1：获取目标对象表达式
            // 【作用】：获取GeodeTable实例的表达式，作为方法调用的目标对象
            // 【实现】：从实现上下文中获取表对象，并转换为GeodeTable.GeodeQueryable类型的表达式
            geodeImplementContext.table.getExpression(GeodeTable.GeodeQueryable.class), // 获取表对象的表达式
            // 步骤6.2：指定要调用的方法
            GEODE_QUERY_METHOD, // 使用缓存的query方法引用
            // 步骤6.3：构建方法参数
            // 【参数1】：fields - 字段名称和类型的映射列表
            // 【实现】：将字段名和字段类配对，转换为Pair列表
            // 【格式】：List<Pair<String, Class>>，如 [("id", Integer.class), ("name", String.class)]
            constantArrayList(Pair.zip(geodeFieldNames(rowType), physFieldClasses), Pair.class), // 字段名和类类型的配对列表
            // 【参数2】：selectFields - 选择字段列表
            // 【实现】：将选择字段的Map转换为Entry列表
            // 【格式】：List<Pair<String, String>>，如 [("empid", "employee_id"), ("name", "employee_name")]
            constantArrayList(toListMapPairs(geodeImplementContext.selectFields), Pair.class), // 选择字段映射列表
            // 【参数3】：aggregateFunctions - 聚合函数列表
            // 【实现】：将聚合函数的Map转换为Entry列表
            // 【格式】：List<Pair<String, String>>，如 [("COUNT(*)", "count"), ("SUM(salary)", "total_salary")]
            constantArrayList( // 聚合函数映射列表
                toListMapPairs(geodeImplementContext.oqlAggregateFunctions), Pair.class), // 聚合函数映射
            // 【参数4】：groupByFields - 分组字段列表
            // 【格式】：List<String>，如 ["department_id", "job_title"]
            constantArrayList(geodeImplementContext.groupByFields, String.class), // 分组字段名称列表
            // 【参数5】：whereClause - WHERE条件子句列表
            // 【格式】：List<String>，每个字符串代表一个条件表达式
            constantArrayList(geodeImplementContext.whereClause, String.class), // WHERE条件表达式列表
            // 【参数6】：orderByFields - 排序字段列表
            // 【格式】：List<String>，如 ["salary DESC", "hire_date ASC"]
            constantArrayList(geodeImplementContext.orderByFields, String.class), // 排序字段列表
            // 【参数7】：limitValue - 限制返回的行数
            // 【格式】：Long类型，null表示无限制
            Expressions.constant(geodeImplementContext.limitValue))); // LIMIT值常量表达式

    // 步骤7：返回实现结果
    // 【作用】：将生成的代码块和物理类型封装为Result对象返回
    // 【Result】：包含可执行的Java代码和类型信息，供后续代码生成使用
    return implementor.result(physType, blockBuilder.toBlock()); // 返回实现结果，包含物理类型和代码块
  }

  /**
   * 将Map转换为Entry列表方法
   * 
   * 【方法作用】：
   * 将Map<String, String>转换为List<Map.Entry<String, String>>，
   * 用于在构建表达式时传递键值对参数。
   * 
   * 【参数说明】：
   * @param map 输入的映射对象，包含字符串键和字符串值
   *             例如：{"empid" -> "employee_id", "name" -> "employee_name"}
   * 
   * 【返回值】：
   * @return 返回包含Map.Entry的列表，每个Entry代表一个键值对
   *         例如：[Entry("empid", "employee_id"), Entry("name", "employee_name")]
   * 
   * 【实现细节】：
   * 1. 创建空的ArrayList用于存储结果
   * 2. 使用Pair.zip将keySet和values配对
   * 3. 遍历配对后的Entry，添加到结果列表中
   * 4. 返回填充好的列表
   * 
   * 【为什么使用Pair.zip】：
   * - 确保键和值的对应关系正确
   * - 返回的是不可变的Pair对象，保证数据安全
   * - Pair实现了Map.Entry接口，可以直接使用
   * 
   * 【使用场景】：
   * - 转换selectFields映射（字段名到别名的映射）
   * - 转换oqlAggregateFunctions映射（聚合函数到别名的映射）
   * 
   * 【示例】：
   * 输入：{"COUNT(*)" -> "count", "SUM(salary)" -> "total"}
   * 输出：[Entry("COUNT(*)", "count"), Entry("SUM(salary)", "total")]
   */
  private static List<Map.Entry<String, String>> toListMapPairs(Map<String, String> map) { // 将Map转换为Entry列表
    List<Map.Entry<String, String>> selectList = new ArrayList<>(); // 创建结果列表
    for (Map.Entry<String, String> entry : Pair.zip(map.keySet(), map.values())) { // 遍历键值对
      selectList.add(entry); // 添加到结果列表
    }
    return selectList; // 返回转换后的列表
  }

  /**
   * 创建常量数组列表表达式方法
   * 
   * 【方法作用】：
   * 生成调用Arrays.asList方法的表达式，用于创建包含常量值的列表。
   * 这是表达式元编程的一部分，用于在生成的Java代码中创建列表。
   * 
   * 【参数说明】：
   * @param <T> 泛型类型参数，表示列表中元素的类型
   * @param values 值列表，包含要转换为常量表达式的值
   *               例如：["x", "y", "z"]
   * @param clazz 元素的Java类类型，用于创建数组初始化表达式
   *              例如：String.class, Integer.class
   * 
   * 【返回值】：
   * @return 返回方法调用表达式，表示Arrays.asList(...)调用
   *         例如：Expressions.call(Arrays.asList, [ConstantExpression("x"), ConstantExpression("y")])
   * 
   * 【生成的代码示例】：
   * 输入：values=["x", "y"], clazz=String.class
   * 生成的Java代码：Arrays.asList("x", "y")
   * 
   * 【实现细节】：
   * 1. 使用constantList将值列表转换为常量表达式列表
   * 2. 使用Expressions.newArrayInit创建数组初始化表达式
   * 3. 使用Expressions.call构建Arrays.asList方法调用
   * 4. 返回完整的方法调用表达式
   * 
   * 【为什么使用Arrays.asList】：
   * - 标准Java方法，无需额外依赖
   * - 可以方便地从数组创建固定大小的列表
   * - 在生成的代码中简洁易读
   * 
   * 【使用场景】：
   * - 在implement方法中创建各种列表参数的表达式
   * - 包括字段列表、选择字段列表、聚合函数列表等
   * 
   * 【示例】：
   * 输入：values=["department", "salary"], clazz=String.class
   * 输出：Arrays.asList("department", "salary")
   */
  private static <T> MethodCallExpression constantArrayList(List<T> values, // 泛型方法：创建常量数组列表表达式
      Class clazz) { // 参数：值列表和元素类型
    return Expressions.call(BuiltInMethod.ARRAYS_AS_LIST.method, // 调用Arrays.asList方法
        Expressions.newArrayInit(clazz, constantList(values))); // 创建数组初始化表达式，传入常量表达式列表
  }

  /**
   * 将值列表转换为常量表达式列表方法
   * 
   * 【方法作用】：
   * 将普通的值列表转换为LINQ4J常量表达式列表，用于构建表达式树。
   * 每个值都被包装为ConstantExpression对象。
   * 
   * 【参数说明】：
   * @param <T> 泛型类型参数，表示值的类型
   * @param values 原始值列表，包含要转换的Java对象
   *               例如：["x", "y", "z"]
   * 
   * 【返回值】：
   * @return 返回常量表达式列表，每个元素是一个ConstantExpression
   *         例如：[ConstantExpression("x"), ConstantExpression("y"), ConstantExpression("z")]
   * 
   * 【生成的代码示例】：
   * 输入：values=["COUNT(*)", "SUM(salary)"]
   * 输出：{ConstantExpression("COUNT(*)"), ConstantExpression("SUM(salary)")}
   * 
   * 【实现细节】：
   * 1. 使用Util.transform工具方法进行列表转换
   * 2. 对每个值应用Expressions::constant方法
   * 3. Expressions.constant将值包装为ConstantExpression
   * 4. 返回转换后的表达式列表
   * 
   * 【为什么使用Util.transform】：
   * - 函数式编程风格，代码简洁
   * - 自动处理列表遍历和转换
   * - 性能优化，避免显式循环
   * 
   * 【ConstantExpression的作用】：
   * - 表示表达式树中的常量值
   * - 可以在代码生成时直接输出为字面量
   * - 保持类型信息和值信息
   * 
   * 【使用场景】：
   * - 在constantArrayList中创建数组初始化参数
   * - 在任何需要常量表达式的地方使用
   * 
   * 【示例】：
   * 输入：values=[100, 200, 300]
   * 输出：[ConstantExpression(100), ConstantExpression(200), ConstantExpression(300)]
   */
  private static <T> List<Expression> constantList(List<T> values) { // 泛型方法：将值列表转换为常量表达式列表
    return Util.transform(values, Expressions::constant); // 使用工具方法转换，每个值转为常量表达式
  }
}
