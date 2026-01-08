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
// Apache许可证声明，说明代码的版权和使用许可
package org.apache.calcite.adapter.enumerable; // 定义包名，该类位于org.apache.calcite.adapter.enumerable包下，属于可枚举适配器模块

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂，用于处理Java类型系统
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入代码块构建器，用于构建Java代码块
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类，用于表示LINQ表达式
import org.apache.calcite.linq4j.tree.Expressions; // 导入表达式工具类，提供创建各种表达式的方法
import org.apache.calcite.linq4j.tree.Primitive; // 导入原始类型工具类，用于处理基本类型
import org.apache.calcite.plan.DeriveMode; // 导入派生模式枚举，定义元数据派生的行为模式
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群，包含查询优化相关的上下文信息
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合，定义关系节点的物理属性
import org.apache.calcite.rel.RelCollation; // 导入关系排序特征，定义数据的排序方式
import org.apache.calcite.rel.RelCollationTraitDef; // 导入关系排序特征定义，用于注册排序特征
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入关系分布特征定义，用于注册分布特征
import org.apache.calcite.rel.RelFieldCollation; // 导入关系字段排序，定义单个字段的排序规则
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，所有关系操作节点的基类
import org.apache.calcite.rel.core.Values; // 导入Values关系节点，用于表示常量值集合
import org.apache.calcite.rel.metadata.RelMdCollation; // 导入排序元数据提供者，用于计算排序信息
import org.apache.calcite.rel.metadata.RelMdDistribution; // 导入分布元数据提供者，用于计算分布信息
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入元数据查询接口，用于获取关系节点的元数据
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型，表示Calcite的类型系统
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段，表示结构化类型的字段
import org.apache.calcite.rex.RexLiteral; // 导入Rex字面量，表示常量表达式
import org.apache.calcite.util.BuiltInMethod; // 导入内置方法枚举，定义常用的内置方法
import org.apache.calcite.util.Pair; // 导入键值对工具类，用于存储两个相关联的对象

import com.google.common.collect.ImmutableList; // 导入不可变列表，提供线程安全的列表实现
import com.google.common.collect.Ordering; // 导入排序工具类，用于定义排序规则

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值

import java.lang.reflect.Type; // 导入Java类型接口，表示Java语言中的类型
import java.util.ArrayList; // 导入动态数组列表，提供可变长度的数组实现
import java.util.List; // 导入列表接口，定义有序集合的行为

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * 类作用说明：
 * 本类是Values关系节点在可枚举调用约定(EnumerableConvention)下的具体实现。
 * 
 * Values节点用于表示SQL中的常量值集合，例如：
 *   SELECT * FROM (VALUES (1, 'a'), (2, 'b')) AS t(id, name)
 * 
 * 作为可枚举实现，本类负责将Values节点转换为可执行的Java代码，
 * 返回一个包含所有常量行的Enumerable（可枚举集合）。
 * 
 * 主要功能：
 * 1. 存储常量值集合（tuples）和行类型信息（rowType）
 * 2. 实现可枚举接口，提供代码生成能力
 * 3. 支持排序特征的传递和验证
 * 4. 将常量值转换为Java表达式并生成可执行的代码块
 * 
 * 继承关系：
 * - 继承自Values：获得Values节点的核心功能（存储常量值、行类型等）
 * - 实现EnumerableRel接口：获得可枚举代码生成能力
 * 
 * 使用场景：
 * - SQL查询中的VALUES子句
 * - 查询优化过程中的常量折叠
 * - 测试和演示场景中的数据构造
 */
public class EnumerableValues extends Values implements EnumerableRel { // 定义类名，继承Values基类，实现EnumerableRel接口
  /**
   * 私有构造方法
   * 
   * 参数说明：
   * @param cluster - 关系优化集群，包含查询优化所需的上下文信息（如类型工厂、元数据查询等）
   * @param rowType - 行类型，描述每行数据的结构（字段名、字段类型等）
   * @param tuples - 常量元组集合，外层列表表示所有行，内层列表表示每行的所有字段值
   *                例如：ImmutableList.of(ImmutableList.of(literal1, literal2), ImmutableList.of(literal3, literal4))
   *                表示两行数据：第一行包含literal1和literal2，第二行包含literal3和literal4
   * @param traitSet - 特征集合，定义该关系节点的物理属性（如调用约定、排序、分布等）
   * 
   * 构造方法作用：
   * 创建一个新的EnumerableValues实例，初始化所有必要的参数
   * 调用父类Values的构造方法来设置基本属性
   * 
   * 设计考虑：
   * 私有构造方法强制使用create工厂方法创建实例，确保特征集合的正确初始化
   */
  private EnumerableValues(RelOptCluster cluster, RelDataType rowType, // 私有构造方法，接收集群对象、行类型、元组集合和特征集合作为参数
      ImmutableList<ImmutableList<RexLiteral>> tuples, RelTraitSet traitSet) { // 继续参数列表：元组集合和特征集合
    super(cluster, rowType, tuples, traitSet); // 调用父类Values的构造方法，初始化基本属性
  }

  /**
   * 静态工厂方法
   * 
   * 参数说明：
   * @param cluster - 关系优化集群，提供类型工厂和元数据查询服务
   * @param rowType - 行类型，定义输出行的结构（字段名和类型）
   * @param tuples - 常量值集合，包含所有行的数据
   * 
   * 返回值：
   * @return 创建好的EnumerableValues实例，包含正确的特征集合
   * 
   * 方法作用：
   * 这是创建EnumerableValues实例的推荐方式，负责：
   * 1. 自动计算并设置该Values节点的排序特征（collation）
   * 2. 自动计算并设置该Values节点的分布特征（distribution）
   * 3. 确保特征集合包含可枚举调用约定（EnumerableConvention）
   * 4. 返回一个完全初始化的EnumerableValues实例
   * 
   * 实现细节：
   * - 从集群获取元数据查询对象，用于查询元数据
   * - 创建基础特征集合，包含可枚举调用约定
   * - 使用replaceIfs方法条件性地替换排序特征，只有当Values确实满足排序要求时才设置
   * - 使用replaceIf方法条件性地替换分布特征
   * - 最后调用私有构造方法创建实例
   * 
   * 为什么要这样设计：
   * - 将特征计算逻辑封装在工厂方法中，保证特征集合的正确性
   * - 使用条件替换避免不必要的特征设置，提高优化效率
   * - 通过元数据查询自动推导排序和分布特征，减少手动配置
   */
  public static EnumerableValues create(RelOptCluster cluster, // 静态工厂方法，接收集群对象作为第一个参数
      final RelDataType rowType, // 接收行类型作为第二个参数，定义输出行的结构
      final ImmutableList<ImmutableList<RexLiteral>> tuples) { // 接收常量元组集合作为第三个参数，包含所有行数据
    final RelMetadataQuery mq = cluster.getMetadataQuery(); // 从集群获取元数据查询对象，用于查询各种元数据信息
    final RelTraitSet traitSet = // 定义特征集合变量，用于存储该关系节点的所有物理属性
        cluster.traitSetOf(EnumerableConvention.INSTANCE) // 创建基础特征集合，包含可枚举调用约定，表示该节点可以生成可枚举代码
            .replaceIfs(RelCollationTraitDef.INSTANCE, // 条件性地替换排序特征，如果Values满足排序要求则设置排序特征
                () -> RelMdCollation.values(mq, rowType, tuples)) // 使用元数据提供者计算Values的排序特征，返回排序信息或null
            .replaceIf(RelDistributionTraitDef.INSTANCE, // 条件性地替换分布特征，如果Values满足分布要求则设置分布特征
                () -> RelMdDistribution.values(rowType, tuples)); // 使用元数据提供者计算Values的分布特征，返回分布信息或null
    return new EnumerableValues(cluster, rowType, tuples, traitSet); // 调用私有构造方法创建并返回EnumerableValues实例
  }

  /**
   * 复制方法
   * 
   * 参数说明：
   * @param traitSet - 新的特征集合，用于替换当前节点的特征
   * @param inputs - 输入节点列表，Values节点没有输入，所以应该为空列表
   * 
   * 返回值：
   * @return 复制的新EnumerableValues实例，具有新的特征集合
   * 
   * 方法作用：
   * 创建当前节点的一个副本，可以用于：
   * 1. 在查询优化过程中创建具有不同特征的节点变体
   * 2. 保持原始节点不变的同时生成新的优化版本
   * 3. 实现关系节点的不可变性设计模式
   * 
   * 实现细节：
   * - 断言输入列表为空，因为Values节点不接收输入
   * - 创建新的EnumerableValues实例，复制所有属性但使用新的特征集合
   * - 保留原始的元组集合，因为常量值不会改变
   * 
   * 为什么需要这个方法：
   * - Calcite的关系节点是不可变的，任何修改都需要创建新实例
   * - 优化器可能需要创建具有不同物理属性的节点变体
   * - 复制操作是查询重写和优化的基础
   */
  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，接收新的特征集合和输入列表
    assert inputs.isEmpty(); // 断言输入列表为空，Values节点不应该有输入
    return new EnumerableValues(getCluster(), getRowType(), tuples, traitSet); // 创建并返回新的EnumerableValues实例，使用新的特征集合
  }

  /**
   * 传递特征方法
   * 
   * 参数说明：
   * @param required - 要求的特征集合，包含上层节点需要的物理属性（如排序、分布等）
   * 
   * 返回值：
   * @return 如果当前Values节点已经满足要求的特征，返回带有新特征集合的节点副本
   *         如果不满足要求，返回null，表示需要添加额外的物理操作（如排序）
   * 
   * 方法作用：
   * 检查当前Values节点是否已经满足上层节点要求的物理特征（主要是排序）：
   * 1. 如果满足要求，直接传递特征，不需要额外的物理操作
   * 2. 如果不满足要求，返回null，让优化器添加排序等物理操作
   * 
   * 实现细节：
   * - 获取要求的排序特征
   * - 如果没有排序要求或使用默认排序，直接返回null
   * - 如果只有0或1行数据，可以满足任何排序要求
   * - 如果有多行数据，检查每行是否已经按照要求的字段排序
   * - 构建排序比较器，验证元组集合是否已排序
   * - 如果已排序，返回带有新排序特征的节点副本
   * - 如果未排序，返回null
   * 
   * 排序验证逻辑：
   * - 遍历要求的字段排序定义
   * - 为每个字段排序创建对应的比较器
   * - 使用复合比较器组合多个字段的排序规则
   * - 使用比较器验证元组集合是否已排序
   * 
   * 性能优化：
   * - 0行或1行的数据可以满足任何排序，无需验证
   * - 使用Guava的Ordering工具类简化排序逻辑
   * - 提前返回避免不必要的计算
   */
  @Override public @Nullable RelNode passThrough(final RelTraitSet required) { // 重写passThrough方法，接收要求的特征集合，返回可能为null的关系节点
    RelCollation collation = required.getCollation(); // 从要求的特征集合中获取排序特征
    if (collation == null || collation.isDefault()) { // 如果没有排序要求或使用默认排序
      return null; // 返回null，表示不需要传递特征
    }

    // 只有0或1行的Values可以满足任何排序要求，因为单个或零个元素总是有序的
    if (tuples.size() > 1) { // 如果有多行数据（大于1行），需要验证是否已排序
      Ordering<List<RexLiteral>> ordering = null; // 初始化排序比较器为null
      // 根据要求的排序规则生成排序比较器
      for (RelFieldCollation fc : collation.getFieldCollations()) { // 遍历每个字段的排序定义
        Ordering<List<RexLiteral>> comparator = RelMdCollation.comparator(fc); // 为当前字段排序创建对应的比较器
        if (ordering == null) { // 如果这是第一个比较器
          ordering = comparator; // 直接使用该比较器作为主排序器
        } else { // 如果已经有比较器
          ordering = ordering.compound(comparator); // 将新比较器复合到现有排序器中，实现多字段排序
        }
      }
      // 检查元组集合是否按照要求的排序规则已排序
      if (!requireNonNull(ordering, "ordering").isOrdered(tuples)) { // 使用排序比较器验证元组集合是否有序，如果未排序
        return null; // 返回null，表示不满足排序要求，需要添加排序操作
      }
    }

    // 元组顺序满足排序要求，创建带有新排序特征的关系节点副本
    return copy(traitSet.replace(collation), ImmutableList.of()); // 调用copy方法创建新节点，替换特征集合中的排序特征，输入列表为空
  }

  /**
   * 获取派生模式方法
   * 
   * 返回值：
   * @return DeriveMode.PROHIBITED，表示禁止派生元数据
   * 
   * 方法作用：
   * 指定该关系节点的元数据派生模式
   * 
   * DeriveMode枚举值说明：
   * - PROHIBITED：禁止派生，元数据必须显式提供
   * - OMITTED：可以省略，元数据可以不提供
   * - ALLOWED：允许派生，元数据可以自动计算
   * 
   * 为什么返回PROHIBITED：
   * - Values节点的元数据（如排序、分布）在创建时就已经计算并设置
   * - 这些元数据是静态的，不会在运行时改变
   * - 避免重复计算，提高性能
   * - 确保元数据的一致性和准确性
   */
  @Override public DeriveMode getDeriveMode() { // 重写getDeriveMode方法，返回元数据派生模式
    return DeriveMode.PROHIBITED; // 返回禁止派生模式，表示元数据必须显式提供，不能自动计算
  }

  /**
   * 实现方法 - 核心代码生成方法
   * 
   * 参数说明：
   * @param implementor - 可枚举关系实现器，提供代码生成所需的上下文和工具
   * @param pref - 代码生成偏好设置，指定如何生成代码（如是否使用自定义类型）
   * 
   * 返回值：
   * @return Result对象，包含生成的代码块和物理类型信息
   * 
   * 方法作用：
   * 将Values节点转换为可执行的Java代码，生成一个返回常量值集合的代码块
   * 
   * 生成的代码示例：
   * <pre>
   * return Linq4j.asEnumerable(
   *     new Object[][] {
   *         new Object[] {1, 2},
   *         new Object[] {3, 4}
   *     });
   * </pre>
   * 
   * 实现步骤：
   * 1. 获取类型工厂，用于类型转换
   * 2. 创建代码块构建器，用于构建Java代码
   * 3. 创建物理类型，描述Java层面的行结构
   * 4. 遍历每个元组（每行数据）
   * 5. 将每个字段的字面量转换为Java表达式
   * 6. 将字段表达式组合成行表达式
   * 7. 将所有行表达式组合成数组
   * 8. 调用asEnumerable方法将数组转换为可枚举集合
   * 9. 返回包含代码块的结果对象
   * 
   * 关键技术点：
   * - RexToLixTranslator：将Rex表达式转换为LINQ表达式
   * - PhysType：物理类型，描述Java层面的数据结构
   * - BlockBuilder：代码块构建器，用于构建方法体
   * - Expressions：表达式工具类，提供创建各种表达式的方法
   * 
   * 类型转换：
   * - RelDataType（Calcite类型） -> Java Type（Java类型）
   * - RexLiteral（字面量） -> Expression（表达式）
   * - List<RexLiteral>（元组） -> Expression（行表达式）
   * - List<Expression>（行列表） -> ArrayExpression（数组表达式）
   * 
   * 性能考虑：
   * - 使用数组存储常量值，访问效率高
   * - 使用基本类型的包装类，支持null值
   * - 代码在编译时确定，运行时无额外开销
   */
  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 重写implement方法，接收实现器和偏好设置，返回代码生成结果
/*
          return Linq4j.asEnumerable( // 注释示例：生成的代码应该调用Linq4j的asEnumerable方法
              new Object[][] { // 创建二维数组，每行是一个Object数组
                  new Object[] {1, 2}, // 第一行数据，包含两个整数
                  new Object[] {3, 4}  // 第二行数据，包含两个整数
              });
*/
    final JavaTypeFactory typeFactory = // 获取Java类型工厂，用于处理Java类型系统
        (JavaTypeFactory) getCluster().getTypeFactory(); // 从集群中获取类型工厂并强制转换为JavaTypeFactory
    final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器，用于构建Java方法体
    final PhysType physType = // 创建物理类型对象，描述Java层面的行结构
        PhysTypeImpl.of( // 使用PhysTypeImpl工厂方法创建物理类型
            implementor.getTypeFactory(), // 使用实现器的类型工厂
            getRowType(), // 获取行类型，描述每行的结构
            pref.preferCustom()); // 根据偏好设置决定是否使用自定义类型
    final Type rowClass = physType.getJavaRowType(); // 获取Java行类型，即行数据在Java中的具体类型

    final List<Expression> expressions = new ArrayList<>(); // 创建表达式列表，用于存储每行的表达式
    final List<RelDataTypeField> fields = getRowType().getFieldList(); // 获取行类型的字段列表，包含所有字段的元数据
    for (List<RexLiteral> tuple : tuples) { // 遍历每个元组（每行数据）
      final List<Expression> literals = new ArrayList<>(); // 创建字面量表达式列表，用于存储当前行的所有字段表达式
      for (Pair<RelDataTypeField, RexLiteral> pair // 遍历当前行的每个字段，Pair将字段元数据和字面量值配对
          : Pair.zip(fields, tuple)) { // 使用Pair.zip将字段列表和字面量列表配对，生成键值对列表
        literals.add( // 将转换后的表达式添加到字面量列表中
            RexToLixTranslator.translateLiteral( // 调用翻译器将Rex字面量转换为LINQ表达式
                pair.right, // 字面量值（RexLiteral）
                pair.left.getType(), // 字段类型（RelDataType）
                typeFactory, // 类型工厂，用于类型转换
                RexImpTable.NullAs.NULL)); // null值处理策略，将SQL null转换为Java null
      }
      expressions.add(physType.record(literals)); // 将字段表达式列表转换为行表达式，添加到表达式列表中
    }
    builder.add( // 将返回语句添加到代码块中
        Expressions.return_( // 创建return表达式
            null, // 返回类型标签，null表示无标签
            Expressions.call( // 创建方法调用表达式
                BuiltInMethod.AS_ENUMERABLE.method, // 调用内置的asEnumerable方法，将数组转换为可枚举集合
                Expressions.newArrayInit( // 创建数组初始化表达式
                    Primitive.box(rowClass), expressions)))); // 使用行类型的包装类型初始化数组，传入所有行的表达式
    return implementor.result(physType, builder.toBlock()); // 返回实现结果，包含物理类型和构建好的代码块
  }
} // 类结束
