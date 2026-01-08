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
package org.apache.calcite.adapter.cassandra; // 包声明：Cassandra适配器包，包含所有Cassandra相关的适配器类

import org.apache.calcite.adapter.enumerable.EnumerableRel; // 导入：可枚举关系表达式接口，定义可枚举的物理关系节点
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor; // 导入：可枚举关系表达式实现器，负责生成可执行代码
import org.apache.calcite.adapter.enumerable.JavaRowFormat; // 导入：Java行格式枚举，定义Java中行的表示方式（如数组、对象等）
import org.apache.calcite.adapter.enumerable.PhysType; // 导入：物理类型接口，表示物理实现中的行类型
import org.apache.calcite.adapter.enumerable.PhysTypeImpl; // 导入：物理类型实现类，提供物理类型的具体实现
import org.apache.calcite.config.CalciteSystemProperty; // 导入：Calcite系统属性配置类，用于读取系统级别的配置参数
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入：代码块构建器，用于构建Java代码块表达式
import org.apache.calcite.linq4j.tree.Expression; // 导入：表达式基类，表示LINQ4J中的各种表达式
import org.apache.calcite.linq4j.tree.Expressions; // 导入：表达式工具类，提供创建各种表达式的静态方法
import org.apache.calcite.linq4j.tree.MethodCallExpression; // 导入：方法调用表达式，表示对方法的调用
import org.apache.calcite.plan.ConventionTraitDef; // 导入：约定特征定义，定义关系表达式的调用约定特征
import org.apache.calcite.plan.RelOptCluster; // 导入：关系优化集群，包含优化器需要的共享资源
import org.apache.calcite.plan.RelOptCost; // 导入：关系优化代价，表示执行计划的成本估计
import org.apache.calcite.plan.RelOptPlanner; // 导入：关系优化规划器接口，负责查询优化
import org.apache.calcite.plan.RelOptTable; // 导入：关系优化表，表示优化过程中的表
import org.apache.calcite.plan.RelTraitSet; // 导入：关系特征集合，包含关系表达式的一组特征
import org.apache.calcite.rel.RelNode; // 导入：关系节点接口，所有关系表达式的基类
import org.apache.calcite.rel.convert.ConverterImpl; // 导入：转换器实现类，用于将一种约定转换为另一种约定
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入：关系元数据查询，用于查询关系表达式的元数据
import org.apache.calcite.rel.type.RelDataType; // 导入：关系数据类型，表示关系表达式的类型系统
import org.apache.calcite.runtime.Hook; // 导入：钩子类，用于在特定事件发生时执行回调
import org.apache.calcite.util.BuiltInMethod; // 导入：内置方法枚举，定义Calcite中常用的内置方法
import org.apache.calcite.util.Pair; // 导入：键值对类，用于存储两个相关联的值
import org.apache.calcite.util.Util; // 导入：工具类，提供各种通用的工具方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入：可空注解，标记可能为null的值

import java.util.AbstractList; // 导入：抽象列表类，用于创建自定义的列表实现
import java.util.ArrayList; // 导入：数组列表类，动态数组实现
import java.util.List; // 导入：列表接口，定义有序集合
import java.util.Map; // 导入：映射接口，定义键值对集合

import static java.util.Objects.requireNonNull; // 导入：静态导入，requireNonNull方法用于检查非空

/**
 * Relational expression representing a scan of a table in a Cassandra data source.
 * 关系表达式，表示对Cassandra数据源中表的扫描操作。
 * 
 * 类作用说明：
 * 1. 这个类是Calcite适配器模式中的关键组件，负责将逻辑关系树转换为可执行的Cassandra查询
 * 2. 它继承自ConverterImpl，实现了将Cassandra特定的关系表达式转换为可枚举的关系表达式的功能
 * 3. 它实现了EnumerableRel接口，表示这个节点可以生成Java代码来执行查询
 * 4. 在查询优化过程中，这个节点作为连接逻辑计划和物理执行的桥梁
 * 5. 它负责生成调用Cassandra数据源的代码，包括字段选择、过滤条件、排序、分页等
 * 
 * 工作流程：
 * 1. 在查询优化阶段，优化器会将逻辑计划转换为物理计划
 * 2. CassandraToEnumerableConverter接收Cassandra特定的关系节点
 * 3. 通过implement方法生成可执行的Java代码表达式
 * 4. 生成的代码会调用Cassandra数据源，执行实际的查询
 * 
 * 核心功能：
 * - 将Cassandra关系表达式转换为LINQ4J表达式树
 * - 生成包含字段、选择列表、谓词、排序、偏移和限制的表达式
 * - 使用表达式生成技术，在运行时动态创建查询代码
 */
public class CassandraToEnumerableConverter // 类定义：Cassandra到可枚举转换器，将Cassandra关系表达式转换为可执行代码
    extends ConverterImpl // 继承：转换器实现类，提供关系表达式转换的基础功能
    implements EnumerableRel { // 实现：可枚举关系表达式接口，表示可以生成可枚举的执行代码
  /**
   * 构造方法：创建Cassandra到可枚举转换器实例
   * 
   * 参数说明：
   * @param cluster - 关系优化集群，包含优化器需要的共享资源（如类型工厂、表达式工厂等）
   *                 这个集群在整个查询优化过程中是共享的，用于维护优化上下文
   * @param traits - 关系特征集合，定义了这个关系节点的各种特征（如调用约定、排序方式等）
   *                特征集合决定了这个节点如何与其他节点协作以及如何执行
   * @param input - 输入关系节点，通常是一个Cassandra特定的关系节点（如CassandraTableScan）
   *              这个节点包含了要扫描的表信息和查询条件
   * 
   * 构造过程：
   * 1. 调用父类ConverterImpl的构造方法，传入集群、约定特征定义、特征集和输入节点
   * 2. 规定特征ConventionTraitDef.INSTANCE表示这是一个可枚举的约定
   * 3. 将输入节点保存为子节点，形成关系树结构
   * 
   * 设计意图：
   * - 使用工厂模式，通过RelOptCluster创建和共享资源
   * - 使用特征模式，通过RelTraitSet定义节点的行为特征
   * - 使用组合模式，通过input参数形成关系树
   */
  protected CassandraToEnumerableConverter( // 构造方法声明：protected修饰，允许子类访问
      RelOptCluster cluster, // 参数：关系优化集群，包含优化器的共享资源
      RelTraitSet traits, // 参数：关系特征集合，定义节点的特征
      RelNode input) { // 参数：输入关系节点，通常是Cassandra特定的关系节点
    super(cluster, ConventionTraitDef.INSTANCE, traits, input); // 调用父类构造方法，传入集群、可枚举约定、特征集和输入节点
  }

  /**
   * 复制方法：创建当前关系节点的副本，可以修改特征集
   * 
   * 这个方法在查询优化过程中非常重要，优化器会通过复制节点并修改特征来探索不同的执行计划
   * 
   * 参数说明：
   * @param traitSet - 新的特征集合，用于创建具有不同特征的节点副本
   *                  优化器可能会尝试不同的特征组合来找到最优计划
   * @param inputs - 输入节点列表，包含新的子节点
   *               通常只有一个输入节点，因为这是一个单子节点的转换器
   * 
   * 返回值说明：
   * @return - 返回新的CassandraToEnumerableConverter实例
   *          新实例具有指定的特征集和输入节点，但其他属性相同
   * 
   * 使用场景：
   * 1. 优化器在探索不同的执行计划时，会复制节点并修改特征
   * 2. 当需要改变节点的某些特征（如排序方式）时，会调用此方法
   * 3. 在规则匹配和应用过程中，会创建节点的副本
   * 
   * 实现细节：
   * - 使用sole方法确保inputs列表只有一个元素
   * - 创建新的实例，保持原有集群，使用新的特征集和输入
   */
  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 方法声明：重写父类的copy方法，创建节点副本
    return new CassandraToEnumerableConverter( // 返回：创建新的转换器实例
        getCluster(), traitSet, sole(inputs)); // 参数：使用当前集群、新特征集、从输入列表中提取唯一输入
  }

  /**
   * 计算自身代价方法：估算执行这个关系节点的成本
   * 
   * 代价计算是查询优化的核心，优化器根据代价选择最优的执行计划
   * 
   * 参数说明：
   * @param planner - 关系优化规划器，提供代价计算上下文
   *                 不同的规划器可能有不同的代价模型
   * @param mq - 关系元数据查询，用于获取节点的元数据信息
   *            如行数、大小、CPU成本等
   * 
   * 返回值说明：
   * @return - 返回估算的执行代价，如果无法计算则返回null
   *          代价包括CPU成本、IO成本等
   * 
   * 实现细节：
   * 1. 调用父类的computeSelfCost方法获取基础代价
   * 2. 将基础代价乘以0.1，表示这个转换器的成本较低
   * 3. 使用requireNonNull确保代价不为null
   * 
   * 设计意图：
   * - 乘以0.1表示这个转换器相对"便宜"，鼓励优化器选择这个转换规则
   * - 这是一种启发式方法，帮助优化器做出更好的决策
   * - 代价模型可以进一步调整以获得更好的优化效果
   */
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 方法声明：重写父类的computeSelfCost方法，计算执行代价
      RelMetadataQuery mq) { // 参数：元数据查询对象，用于获取元数据
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq)); // 调用父类方法获取基础代价，并确保不为null
    return cost.multiplyBy(.1); // 返回：将基础代价乘以0.1，表示这个转换器的成本较低
  }

  /**
   * 实现方法：生成可执行的代码表达式
   * 
   * 这是整个类最核心的方法，负责将关系表达式转换为可执行的Java代码
   * 
   * 参数说明：
   * @param implementor - 可枚举关系表达式实现器，负责代码生成的上下文
   *                     提供类型工厂、表达式构建等功能
   * @param pref - 偏好设置，定义了行格式等偏好
   *              控制生成的代码如何表示行数据
   * 
   * 返回值说明：
   * @return - 返回实现结果，包含物理类型和生成的代码块
   *          这个结果会被上层调用者用来生成最终的执行代码
   * 
   * 实现步骤详解：
   * 
   * 步骤1：创建代码块构建器
   * - BlockBuilder用于构建Java代码块，会按顺序添加表达式
   * - 最终会生成一个完整的Java方法体
   * 
   * 步骤2：创建Cassandra实现器并访问子节点
   * - CassandraRel.Implementor是专门用于实现Cassandra关系的实现器
   * - visitChild方法会遍历输入节点，收集查询信息
   * - 收集的信息包括：选择的字段、WHERE条件、排序、分页等
   * 
   * 步骤3：确定行类型和物理类型
   * - getRowType()获取当前节点的输出行类型
   * - PhysTypeImpl创建物理类型，决定Java中如何表示行
   * - pref.prefer(JavaRowFormat.ARRAY)表示使用数组格式表示行
   * 
   * 步骤4：生成字段表达式
   * - 使用Pair.zip将字段名和字段类型配对
   * - 创建匿名AbstractList，动态获取每个字段的Java类型
   * - constantArrayList将列表转换为常量数组表达式
   * 
   * 步骤5：生成选择字段表达式
   * - 从Cassandra实现器中提取选择的字段
   * - 将keySet和values配对，创建Map.Entry列表
   * - 转换为常量数组表达式
   * 
   * 步骤6：生成表表达式
   * - 从Cassandra实现器中获取表对象
   * - 调用getExpression方法获取表的查询表达式
   * - CassandraTable.CassandraQueryable.class是Cassandra表的查询接口
   * 
   * 步骤7：生成谓词表达式
   * - whereClause包含WHERE条件
   * - 转换为常量数组表达式
   * 
   * 步骤8：生成排序表达式
   * - order包含排序字段和方向
   * - 转换为常量数组表达式
   * 
   * 步骤9：生成分页表达式
   * - offset表示跳过的行数
   * - fetch表示返回的行数限制
   * - 使用Expressions.constant创建常量表达式
   * 
   * 步骤10：生成可枚举表达式
   * - 调用CassandraMethod.CASSANDRA_QUERYABLE_QUERY.method方法
   * - 传入所有查询参数：字段、选择字段、谓词、排序、偏移、限制
   * - 这个方法会返回一个可枚举的结果集
   * 
   * 步骤11：调试和钩子
   * - 如果DEBUG模式开启，打印谓词信息
   * - Hook.QUERY_PLAN.run(predicates)允许外部监听查询计划
   * 
   * 步骤12：返回结果
   * - 添加return语句，返回可枚举表达式
   * - 调用implementor.result生成最终结果
   * 
   * 代码生成示例：
   * 生成的代码类似于：
   * List<Pair<String, Class<?>>> fields = Arrays.asList(...);
   * List<Pair<String, String>> selectFields = Arrays.asList(...);
   * List<String> predicates = Arrays.asList(...);
   * List<String> order = Arrays.asList(...);
   * int offset = 0;
   * int fetch = 100;
   * return table.query(fields, selectFields, predicates, order, offset, fetch);
   */
  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 方法声明：实现可枚举关系表达式，生成可执行代码
    // Generates a call to "query" with the appropriate fields and predicates
    // 注释：生成一个"query"方法调用，包含适当的字段和谓词参数
    final BlockBuilder list = new BlockBuilder(); // 创建代码块构建器，用于构建Java代码块
    final CassandraRel.Implementor cassandraImplementor = new CassandraRel.Implementor(); // 创建Cassandra实现器，用于收集查询信息
    cassandraImplementor.visitChild(0, getInput()); // 访问子节点，收集查询的字段、条件、排序等信息
    final RelDataType rowType = getRowType(); // 获取当前节点的行类型，定义输出的数据结构
    final PhysType physType = // 创建物理类型，定义Java中如何表示行数据
        PhysTypeImpl.of( // 使用PhysTypeImpl工厂方法创建物理类型
                implementor.getTypeFactory(), rowType, // 传入类型工厂和行类型
                pref.prefer(JavaRowFormat.ARRAY)); // 使用数组格式表示行（如Object[]）
    final Expression fields = // 创建字段表达式，包含字段名和对应的Java类型
        list.append("fields", // 将表达式添加到代码块，命名为"fields"
            constantArrayList( // 将列表转换为常量数组表达式
                Pair.zip(CassandraRules.cassandraFieldNames(rowType), // 将Cassandra字段名与Java类型配对
                    new AbstractList<Class<?>>() { // 创建匿名列表，动态获取每个字段的Java类型
                      @Override public Class<?> get(int index) { // 重写get方法，返回指定索引的字段类型
                        return physType.fieldClass(index); // 从物理类型中获取字段的Java类
                      }

                      @Override public int size() { // 重写size方法，返回字段总数
                        return rowType.getFieldCount(); // 从行类型中获取字段数量
                      }
                    }),
                Pair.class)); // 指定列表元素类型为Pair
    List<Map.Entry<String, String>> selectList = new ArrayList<>(); // 创建选择字段列表，用于存储字段映射
    for (Map.Entry<String, String> entry // 遍历字段映射条目
            : Pair.zip(cassandraImplementor.selectFields.keySet(), // 将Cassandra字段名与Calcite字段名配对
                cassandraImplementor.selectFields.values())) { // 获取字段值
      selectList.add(entry); // 将字段映射条目添加到选择列表
    }
    final Expression selectFields = // 创建选择字段表达式
        list.append("selectFields", constantArrayList(selectList, Pair.class)); // 将选择列表转换为常量数组表达式
    final RelOptTable cassandraTable = // 获取Cassandra表对象
        requireNonNull(cassandraImplementor.table); // 确保表对象不为null
    final Expression table = // 创建表表达式
        list.append("table", // 将表达式添加到代码块，命名为"table"
            requireNonNull( // 确保表达式不为null
                cassandraTable.getExpression( // 获取表的表达式对象
                    CassandraTable.CassandraQueryable.class))); // 指定获取Cassandra查询接口的表达式
    final Expression predicates = // 创建谓词表达式，包含WHERE条件
        list.append("predicates", // 将表达式添加到代码块，命名为"predicates"
            constantArrayList(cassandraImplementor.whereClause, String.class)); // 将WHERE条件列表转换为常量数组表达式
    final Expression order = // 创建排序表达式
        list.append("order", // 将表达式添加到代码块，命名为"order"
            constantArrayList(cassandraImplementor.order, String.class)); // 将排序字段列表转换为常量数组表达式
    final Expression offset = // 创建偏移量表达式，表示跳过的行数
        list.append("offset", // 将表达式添加到代码块，命名为"offset"
            Expressions.constant(cassandraImplementor.offset)); // 创建常量表达式，值为偏移量
    final Expression fetch = // 创建获取行数表达式，表示返回的行数限制
        list.append("fetch", // 将表达式添加到代码块，命名为"fetch"
            Expressions.constant(cassandraImplementor.fetch)); // 创建常量表达式，值为获取行数
    Expression enumerable = // 创建可枚举表达式，表示查询结果
        list.append("enumerable", // 将表达式添加到代码块，命名为"enumerable"
            Expressions.call(table, // 创建方法调用表达式，调用表对象的方法
                CassandraMethod.CASSANDRA_QUERYABLE_QUERY.method, fields, // 调用query方法，传入字段参数
                selectFields, predicates, order, offset, fetch)); // 传入选择字段、谓词、排序、偏移、限制参数
    if (CalciteSystemProperty.DEBUG.value()) { // 检查是否开启DEBUG模式
      System.out.println("Cassandra: " + predicates); // 如果开启，打印谓词信息用于调试
    }
    Hook.QUERY_PLAN.run(predicates); // 运行查询计划钩子，允许外部监听查询计划
    list.add( // 向代码块添加表达式
        Expressions.return_(null, enumerable)); // 添加return语句，返回可枚举表达式
    return implementor.result(physType, list.toBlock()); // 返回实现结果，包含物理类型和生成的代码块
  }

  /**
   * 常量数组列表方法：将值列表转换为Arrays.asList调用表达式
   * 
   * 这是一个辅助方法，用于生成创建常量数组的代码
   * 
   * 类型参数说明：
   * @param <T> - 列表元素的类型，可以是任何类型
   * 
   * 参数说明：
   * @param values - 值列表，包含要转换为数组的值
   *                这些值会被转换为常量表达式
   * @param clazz - 元素类型的Class对象，用于创建数组
   *               指定数组中元素的类型
   * 
   * 返回值说明：
   * @return - 返回方法调用表达式，表示Arrays.asList(...)调用
   *          例如：constantArrayList(["x", "y"], String.class)返回Arrays.asList("x", "y")
   * 
   * 实现细节：
   * 1. 使用constantList将值列表转换为常量表达式列表
   * 2. 使用Expressions.newArrayInit创建数组初始化表达式
   * 3. 使用Expressions.call创建Arrays.asList方法调用
   * 
   * 使用示例：
   * 输入：values = ["name", "age"], clazz = String.class
   * 输出：Arrays.asList("name", "age")
   * 
   * 代码生成：
   * 生成的表达式会被编译为Java代码，如：
   * Arrays.asList("field1", "field2", "field3")
   */
  /** E.g. {@code constantArrayList("x", "y")} returns
   * "Arrays.asList('x', 'y')". */
  private static <T> MethodCallExpression constantArrayList(List<T> values, // 方法声明：私有静态方法，泛型T表示元素类型
      Class<?> clazz) { // 参数：元素类型的Class对象
    return Expressions.call( // 返回：创建方法调用表达式
        BuiltInMethod.ARRAYS_AS_LIST.method, // 调用Arrays.asList方法
        Expressions.newArrayInit(clazz, constantList(values))); // 创建数组初始化表达式，传入元素类型和常量列表
  }

  /**
   * 常量列表方法：将值列表转换为常量表达式列表
   * 
   * 这是一个辅助方法，用于将普通值转换为表达式树中的常量表达式
   * 
   * 类型参数说明：
   * @param <T> - 列表元素的类型，可以是任何类型
   * 
   * 参数说明：
   * @param values - 值列表，包含要转换的值
   *                这些值会被转换为ConstantExpression对象
   * 
   * 返回值说明：
   * @return - 返回常量表达式列表
   *          每个元素都是一个ConstantExpression，表示一个常量值
   * 
   * 实现细节：
   * 1. 使用Util.transform工具方法对列表进行转换
   * 2. 对每个值调用Expressions::constant，转换为常量表达式
   * 3. 返回转换后的表达式列表
   * 
   * 使用示例：
   * 输入：values = ["name", "age", "salary"]
   * 输出：[ConstantExpression("name"), ConstantExpression("age"), ConstantExpression("salary")]
   * 
   * 表达式树结构：
   * 生成的表达式树结构如下：
   * List<Expression>
   *   ├── ConstantExpression("name")
   *   ├── ConstantExpression("age")
   *   └── ConstantExpression("salary")
   * 
   * 与constantArrayList的关系：
   * constantList是constantArrayList的底层实现
   * constantArrayList调用constantList创建表达式列表，然后包装为Arrays.asList调用
   */
  /** E.g. {@code constantList("x", "y")} returns
   * {@code {ConstantExpression("x"), ConstantExpression("y")}}. */
  private static <T> List<Expression> constantList(List<T> values) { // 方法声明：私有静态方法，泛型T表示元素类型
    return Util.transform(values, Expressions::constant); // 返回：使用工具方法转换列表，将每个值转换为常量表达式
  }
} // 类结束
