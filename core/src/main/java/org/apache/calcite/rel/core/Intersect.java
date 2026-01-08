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
// Apache Calcite 是一个动态数据管理框架，提供 SQL 解析、优化、执行等功能，本包包含关系代数核心类
package org.apache.calcite.rel.core;

// RelOptCluster: 关系表达式集群，包含了所有关系表达式共享的信息（如类型系统、优化器上下文等）
import org.apache.calcite.plan.RelOptCluster;
// RelTraitSet: 关系表达式特征集合，定义了关系表达式的物理属性（如排序、分区等）
import org.apache.calcite.plan.RelTraitSet;
// RelInput: 关系表达式的输入接口，用于从序列化数据中反序列化关系表达式
import org.apache.calcite.rel.RelInput;
// RelNode: 关系表达式节点接口，所有关系表达式都实现此接口，代表关系代数操作
import org.apache.calcite.rel.RelNode;
// RelHint: 关系表达式提示，用于给优化器提供额外的指导信息
import org.apache.calcite.rel.hint.RelHint;
// RelMetadataQuery: 关系元数据查询接口，用于查询关系表达式的元数据（如行数、唯一性等）
import org.apache.calcite.rel.metadata.RelMetadataQuery;
// SqlKind: SQL 操作类型枚举，定义了所有 SQL 操作类型（如 SELECT、JOIN、INTERSECT 等）
import org.apache.calcite.sql.SqlKind;

// Collections: Java 集合工具类，提供各种集合操作方法
import java.util.Collections;
// List: Java 列表接口，表示有序集合
import java.util.List;

/**
 * Intersect 类：关系代数中的交集操作
 * 
 * 【类的作用】：
 * 这个抽象类表示 SQL 中的 INTERSECT 操作，用于返回多个输入关系表达式的交集。
 * 交集操作的结果包含所有在所有输入中都存在的行。
 * 
 * 【核心功能】：
 * 1. 实现关系代数中的集合交集操作 (∩)
 * 2. 支持两种模式：
 *    - INTERSECT（去重模式）：返回去重后的交集，结果中不包含重复行
 *    - INTERSECT ALL（不去重模式）：返回多重集交集，保留重复行
 * 3. 继承自 SetOp（集合操作基类），复用集合操作的通用逻辑
 * 4. 提供行数估算功能，用于查询优化器的成本估算
 * 
 * 【使用场景】：
 * - SQL 查询中的 INTERSECT 或 INTERSECT ALL 操作
 * - 需要找出多个查询结果共同记录的场景
 * - 数据对比和数据一致性检查
 * 
 * 【示例】：
 * SELECT * FROM table1 INTERSECT SELECT * FROM table2
 * 返回 table1 和 table2 中都存在的记录
 * 
 * 【继承关系】：
 * Intersect -> SetOp -> AbstractRelNode -> RelNode
 * 
 * 【实现细节】：
 * - 是抽象类，具体实现由子类提供（如 LogicalIntersect、EnumerableIntersect 等）
 * - 通过 SqlKind.INTERSECT 标识操作类型
 * - 通过 all 标志区分是否去重
 * 
 * <p>If "all" is true, performs then multiset intersection; otherwise,
 * performs set set intersection (implying no duplicates in the results).
 * 如果 all 为 true，执行多重集交集（保留重复）；否则执行集合交集（结果无重复）
 */
// Intersect 是抽象类，继承自 SetOp（集合操作基类），表示关系代数中的交集操作
public abstract class Intersect extends SetOp {
  /**
   * Creates an Intersect.
   * 【构造方法作用】：创建一个 Intersect 关系表达式实例
   * 
   * 【参数说明】：
   * @param cluster - 关系表达式集群，包含优化器上下文、类型系统等共享信息
   * @param traits - 关系表达式特征集合，定义物理属性（如排序、分区、约定等）
   * @param hints - 关系表达式提示列表，用于指导优化器选择更好的执行计划
   * @param inputs - 输入关系表达式列表，至少包含两个输入（INTERSECT 至少需要两个操作数）
   * @param all - 是否为 INTERSECT ALL 模式（true=不去重保留重复，false=去重）
   * 
   *【实现逻辑】：
   * 1. 调用父类 SetOp 的构造方法
   * 2. 传递 SqlKind.INTERSECT 标识这是一个交集操作
   * 3. 将 all 标志传递给父类，用于区分是否去重
   * 4. 父类会验证输入列表至少包含两个关系表达式
   * 
   * 【使用场景】：
   * - 查询优化器在规则匹配和转换时创建新的 Intersect 节点
   * - 反序列化时重建关系表达式树
   * - 查询重写过程中生成新的集合操作节点
   */
  public Intersect(
      RelOptCluster cluster,  // 关系表达式集群，提供优化器上下文和类型系统
      RelTraitSet traits,     // 关系表达式特征集合，定义物理属性
      List<RelHint> hints,    // 关系表达式提示列表，优化器指导信息
      List<RelNode> inputs,   // 输入关系表达式列表，至少两个
      boolean all) {          // 是否为 INTERSECT ALL 模式
    // 调用父类 SetOp 的构造方法，初始化集合操作的基本属性
    // SqlKind.INTERSECT 标识这是交集操作
    // all 参数决定是否保留重复行
    super(cluster, traits, hints, inputs, SqlKind.INTERSECT, all);
  }

  /**
   * Creates an Intersect.
   * 【构造方法作用】：创建一个不带提示的 Intersect 关系表达式实例（简化版本）
   * 
   * 【参数说明】：
   * @param cluster - 关系表达式集群，包含优化器上下文、类型系统等共享信息
   * @param traits - 关系表达式特征集合，定义物理属性（如排序、分区、约定等）
   * @param inputs - 输入关系表达式列表，至少包含两个输入
   * @param all - 是否为 INTERSECT ALL 模式（true=不去重保留重复，false=去重）
   * 
   * 【实现逻辑】：
   * 1. 调用完整的构造方法，但传入空列表作为 hints 参数
   * 2. 使用 Collections.emptyList() 创建不可变的空列表
   * 3. 其他参数直接传递给完整的构造方法
   * 
   * 【使用场景】：
   * - 当不需要提供优化器提示时使用此简化构造方法
   * - 查询优化器内部创建节点时，通常不需要提示
   * - 向后兼容性考虑，保留此构造方法
   * 
   * 【设计考虑】：
   * - protected 访问级别，仅供子类使用
   * - 提供了更简洁的 API，减少不必要的参数
   */
  protected Intersect(
      RelOptCluster cluster,  // 关系表达式集群，提供优化器上下文和类型系统
      RelTraitSet traits,     // 关系表达式特征集合，定义物理属性
      List<RelNode> inputs,   // 输入关系表达式列表，至少两个
      boolean all) {          // 是否为 INTERSECT ALL 模式
    // 调用完整的构造方法，传入空的提示列表
    // Collections.emptyList() 返回一个不可变的空列表，避免创建新对象
    this(cluster, traits, Collections.emptyList(), inputs, all);
  }

  /**
   * Creates an Intersect by parsing serialized output.
   * 【构造方法作用】：通过解析序列化输出创建 Intersect 关系表达式实例
   * 
   * 【参数说明】：
   * @param input - 关系表达式输入对象，包含序列化的关系表达式数据
   * 
   * 【实现逻辑】：
   * 1. 调用父类 SetOp 的构造方法
   * 2. 父类会从 RelInput 对象中读取序列化数据
   * 3. 重建 Intersect 节点及其所有子节点
   * 4. 恢复所有属性（cluster、traits、inputs、all 等）
   * 
   * 【使用场景】：
   * - 从 JSON 或其他序列化格式反序列化关系表达式树
   * - 查询计划的持久化和恢复
   * - 分布式查询计划传输
   * - 缓存查询计划
   * 
   * 【序列化格式】：
   * RelInput 包含以下信息：
   * - 操作类型（INTERSECT）
   * - 输入列表
   * - all 标志
   * - 其他元数据
   * 
   * 【设计考虑】：
   * - protected 访问级别，仅供框架内部使用
   * - 支持查询计划的跨进程传输
   */
  protected Intersect(RelInput input) {  // 序列化的关系表达式输入对象
    // 调用父类的反序列化构造方法
    // 父类会从 input 中读取所有必要的信息并重建节点
    super(input);
  }

  /**
   * 【方法作用】：估算 Intersect 操作的输出行数
   * 
   * 【返回值】：
   * @return 估算的输出行数（double 类型），用于查询优化器的成本计算
   * 
   * 【参数说明】：
   * @param mq - 关系元数据查询接口，用于查询输入的元数据信息
   * 
   * 【实现逻辑】：
   * 1. 初始值设为 Double.MAX_VALUE（最大可能的行数）
   * 2. 遍历所有输入关系表达式
   * 3. 对于每个输入，查询其估算行数
   * 4. 如果某个输入的行数未知（null），跳过该输入（假设不减少行数）
   * 5. 取所有输入行数的最小值（交集不可能超过最小的输入）
   * 6. 将最小值乘以 0.25（经验系数，假设交集约为最小输入的 25%）
   * 7. 返回估算结果
   * 
   * 【估算原理】：
   * - 交集的结果行数不会超过任何一个输入的行数
   * - 取所有输入的最小行数作为上限
   * - 乘以 0.25 是一个经验值，假设数据之间有一定重叠
   * - 这只是一个粗略估算，实际行数取决于数据分布
   * 
   * 【使用场景】：
   * - 查询优化器在成本估算时调用
   * - 用于比较不同执行计划的成本
   * - 帮助优化器选择最优的执行计划
   * 
   * 【局限性】：
   * - 0.25 系数是经验值，可能不准确
   * - 没有考虑数据分布和统计信息
   * - 对于某些情况可能高估或低估
   * - 注释中提到 "I just pulled this out of a hat" 表示这个系数是随意选择的
   * 
   * 【优化建议】：
   * - 可以使用统计信息（如直方图）提高估算准确性
   * - 可以根据数据的唯一性调整系数
   * - 可以考虑输入之间的相关性
   */
  @Override public double estimateRowCount(RelMetadataQuery mq) {  // 重写父类方法，估算输出行数
    // REVIEW jvs 30-May-2005:  I just pulled this out of a hat.
    // 注释说明：这个 0.25 的系数是随意选择的，没有经过严格验证
    // 初始化为最大值，后续会取所有输入的最小值
    double dRows = Double.MAX_VALUE;  // 初始值设为最大可能的行数
    // 遍历所有输入关系表达式
    for (RelNode input : inputs) {  // inputs 是从父类 SetOp 继承的输入列表
      // 查询当前输入的估算行数
      Double rowCount = mq.getRowCount(input);  // 通过元数据查询接口获取行数
      // 如果行数未知（null），跳过该输入
      if (rowCount == null) {  // 某些情况下元数据可能不可用
        // Assume this input does not reduce row count
        // 假设这个输入不会减少行数，继续处理下一个输入
        continue;  // 跳过当前输入，不更新 dRows
      }
      // 取当前最小值和输入行数的较小值
      // 交集的行数不会超过任何一个输入的行数
      dRows = Math.min(dRows, rowCount);  // 更新最小值
    }
    // 乘以 0.25 作为经验系数
    // 假设交集约为最小输入的 25%，这是一个粗略的估算
    dRows *= 0.25;  // 应用经验系数
    // 返回估算的行数
    return dRows;  // 返回最终估算结果
  }
}
