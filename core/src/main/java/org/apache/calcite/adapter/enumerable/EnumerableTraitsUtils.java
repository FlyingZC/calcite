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
package org.apache.calcite.adapter.enumerable; // 包声明：属于Calcite的Enumerable适配器包，处理可枚举数据源的适配逻辑

import org.apache.calcite.linq4j.Ord; // 导入Ord类：用于为集合元素添加索引，支持带索引的遍历
import org.apache.calcite.plan.RelOptUtil; // 导入RelOptUtil工具类：提供关系表达式优化相关的实用方法
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类：表示关系节点的特征集合（如排序规则、物理实现方式等）
import org.apache.calcite.rel.RelCollation; // 导入RelCollation类：表示数据的排序规则（即字段排序顺序）
import org.apache.calcite.rel.RelCollations; // 导入RelCollations工具类：提供创建和操作RelCollation的静态方法
import org.apache.calcite.rel.RelFieldCollation; // 导入RelFieldCollation类：表示单个字段的排序方向（升序/降序/空值排序）
import org.apache.calcite.rel.core.JoinRelType; // 导入JoinRelType枚举：表示连接操作的类型（内连接、左外连接、右外连接、全连接）
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类：表示关系数据类型（即行的结构，包含字段列表）
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口：用于创建关系数据类型的工厂
import org.apache.calcite.rex.RexCall; // 导入RexCall类：表示函数调用表达式（如CAST、+、-等操作符）
import org.apache.calcite.rex.RexCallBinding; // 导入RexCallBinding类：表示函数调用的绑定信息，包含操作符、参数类型等
import org.apache.calcite.rex.RexInputRef; // 导入RexInputRef类：表示对输入字段的引用（如$input0表示第一个输入字段）
import org.apache.calcite.rex.RexNode; // 导入RexNode类：表示行表达式的基类（所有表达式都继承自此类）
import org.apache.calcite.rex.RexUtil; // 导入RexUtil工具类：提供行表达式操作的实用方法
import org.apache.calcite.sql.SqlKind; // 导入SqlKind枚举：表示SQL操作的类型（如SELECT、CAST、AND、OR等）
import org.apache.calcite.sql.validate.SqlMonotonicity; // 导入SqlMonotonicity枚举：表示表达式的单调性（是否保持排序）
import org.apache.calcite.util.Pair; // 导入Pair类：表示键值对，用于返回两个相关联的值
import org.apache.calcite.util.mapping.MappingType; // 导入MappingType枚举：表示映射类型（如函数映射、逆映射等）
import org.apache.calcite.util.mapping.Mappings; // 导入Mappings工具类：提供创建和操作字段映射的静态方法

import com.google.common.collect.ImmutableList; // 导入ImmutableList类：Google Guava库提供的不可变列表实现

import org.apiguardian.api.API; // 导入API注解：用于标记API的稳定性和版本信息
import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解：标记可空类型，用于空值检查

import java.util.ArrayList; // 导入ArrayList类：Java标准库的动态数组实现
import java.util.List; // 导入List接口：Java标准库的列表接口

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法：用于检查对象是否为null

/**
 * Utilities for traits propagation. // 类说明：特征传播工具类，用于在关系表达式树中传播和推导特征（主要是排序规则collation）
 * 
 * 这个类在Calcite优化器中扮演关键角色，负责处理关系节点之间的特征传递。
 * 特征（Traits）是Calcite优化器的核心概念，包括排序规则、物理实现方式等。
 * 
 * 主要功能：
 * 1. Project操作的特征传递：当上层要求Project输出有特定排序时，判断能否将此要求传递给下层输入
 * 2. Project操作的特征推导：当下层输入有特定排序时，判断Project操作能否保持或推导出新的排序
 * 3. Join操作的特征传递：当上层要求Join输出有特定排序时，判断能否将此要求传递给左输入
 * 4. Join操作的特征推导：当左输入有特定排序时，判断Join操作能否保持此排序
 * 
 * 为什么需要特征传播？
 * - 避免不必要的排序操作：如果下层已经有序，上层就不需要再排序
 * - 推进优化器决策：帮助优化器选择最优的执行计划
 * - 提高查询性能：减少数据移动和排序操作
 */
@API(since = "1.24", status = API.Status.INTERNAL) // API注解：表示从1.24版本开始提供，状态为内部API（不保证向后兼容）
class EnumerableTraitsUtils { // 类定义：Enumerable特征工具类，使用默认访问权限（包级私有）

  private EnumerableTraitsUtils() {} // 私有构造方法：防止实例化，这是一个纯工具类，所有方法都是静态的

  /**
   * Determine whether there is mapping between project input and output fields. // 方法说明：判断Project操作的输入和输出字段之间是否存在映射关系
   * Bail out if sort relies on non-trivial expressions. // 如果排序依赖于非平凡表达式（复杂表达式），则放弃（返回false）
   * 
   * 这个方法的核心作用是判断一个排序要求能否通过Project操作传递。
   * 
   * 关键概念解释：
   * - 平凡表达式：简单的字段引用（如$input0）或保持单调性的类型转换（如CAST(INT AS BIGINT)）
   * - 非平凡表达式：复杂的计算表达式（如$input0 + $input1），这类表达式会破坏排序
   * - 单调性：如果函数f是单调递增的，那么a <= b时f(a) <= f(b)，排序得以保持
   * 
   * 参数说明：
   * @param projects Project操作的投影表达式列表，每个表达式对应一个输出字段
   * @param typeFactory 类型工厂，用于创建类型信息
   * @param map 字段映射关系，表示输入字段到输出字段的映射
   * @param fc 要检查的字段排序规则（字段索引+排序方向）
   * @param passDown 布尔值，true表示向下传递（从上层要求到下层），false表示向上推导（从下层到上层）
   * @return boolean true表示可以传递/推导，false表示不能传递/推导
   * 
   * 工作原理：
   * 1. 获取排序字段在输入中的索引
   * 2. 通过映射找到对应的输出字段索引
   * 3. 检查对应的投影表达式是否是平凡表达式
   * 4. 如果是CAST表达式，检查是否保持单调性
   * 5. 如果是其他表达式，判断是否破坏排序
   * 
   * 示例场景：
   * 场景1：Project输出要求按字段0排序，字段0直接来自输入字段5
   *   - 这是平凡表达式，可以传递
   * 场景2：Project输出要求按字段0排序，字段0是CAST($input5 AS BIGINT)
   *   - CAST是单调函数，可以传递
   * 场景3：Project输出要求按字段0排序，字段0是$input5 + 1
   *   - 这是非平凡表达式，不能传递（因为+1会改变相对顺序）
   */
  private static boolean isCollationOnTrivialExpr( // 方法定义：判断排序是否基于平凡表达式
      List<RexNode> projects, // 参数1：Project操作的投影表达式列表
      RelDataTypeFactory typeFactory, // 参数2：类型工厂，用于检查单调性
      Mappings.TargetMapping map, // 参数3：输入字段到输出字段的映射
      RelFieldCollation fc, // 参数4：要检查的字段排序规则
      boolean passDown) { // 参数5：是否向下传递（true）或向上推导（false）
    final int index = fc.getFieldIndex(); // 获取排序字段的索引位置（在Project输出或输入中的索引）
    int target = map.getTargetOpt(index); // 通过映射获取对应的字段索引，-1表示没有映射
    if (target < 0) { // 如果没有找到映射关系
      return false; // 返回false：无法传递排序要求
    }

    final RexNode node = passDown ? projects.get(index) : projects.get(target); // 根据passDown决定检查哪个表达式：向下传递检查index处的表达式，向上推导检查target处的表达式
    if (node.isA(SqlKind.CAST)) { // 如果表达式是类型转换操作（CAST）
      // Check whether it is a monotonic preserving cast // 注释：检查是否是保持单调性的类型转换
      final RexCall cast = (RexCall) node; // 将表达式强制转换为RexCall类型（CAST是函数调用的一种）
      RelFieldCollation newFieldCollation = // 创建新的字段排序规则
          requireNonNull(RexUtil.apply(map, fc)); // 应用映射到排序规则，并确保结果不为null
      final RexCallBinding binding = // 创建函数调用绑定信息
          RexCallBinding.create(typeFactory, cast, // 绑定类型工厂和CAST操作符
              ImmutableList.of(RelCollations.of(newFieldCollation))); // 传入排序规则作为参数
      return cast.getOperator().getMonotonicity(binding) // 获取CAST操作符的单调性
          != SqlMonotonicity.NOT_MONOTONIC; // 如果不是"非单调"，则返回true（保持单调性，可以传递）
    }

    return true; // 如果不是CAST，且前面的检查都通过，则返回true（是平凡表达式，可以传递）
  }

  /**
   * 方法说明：为Project操作传递特征（traits）到下层输入
   * 
   * 这个方法实现了"特征传递"机制，即当上层要求Project输出有特定排序时，
   * 判断能否将此排序要求传递给Project的输入，从而避免在Project之后进行排序。
   * 
   * 核心概念：
   * - 特征（Trait）：关系节点的属性，如排序规则、物理实现方式等
   * - 特征传递：将上层的要求传递到下层，满足上层要求的同时避免重复操作
   * - 代价优化：通过特征传递减少排序操作，提高查询性能
   * 
   * 参数说明：
   * @param required 上层要求的特征集合（包含要求的排序规则）
   * @param exps Project操作的投影表达式列表（定义输出字段如何从输入字段计算）
   * @param inputRowType 输入行的类型（包含输入字段的数量和类型）
   * @param typeFactory 类型工厂，用于类型检查
   * @param currentTraits Project当前的特征集合
   * @return Pair<RelTraitSet, List<RelTraitSet>> 返回值对：
   *         - first: Project应该采纳的特征集合
   *         - second: 子节点应该采纳的特征集合列表（每个子节点一个）
   *         如果无法传递特征，返回null
   * 
   * 工作流程：
   * 1. 检查上层是否要求排序，如果没有则返回null
   * 2. 创建输入到输出的字段映射关系（忽略CAST）
   * 3. 检查排序要求的所有字段是否都基于平凡表达式
   * 4. 如果通过检查，计算传递给输入的新排序规则
   * 5. 返回Project和输入应该采纳的特征集合
   * 
   * 示例场景：
   * 场景：SQL: SELECT emp_id, emp_name FROM emp ORDER BY emp_id
   * - 上层要求输出按emp_id排序
   * - emp_id直接来自输入字段0
   * - 可以传递：要求输入也按字段0排序
   * - 结果：避免在Project之后排序
   * 
   * 场景：SQL: SELECT emp_id, emp_name, salary * 1.1 AS new_salary FROM emp ORDER BY new_salary
   * - 上层要求输出按new_salary排序
   * - new_salary是salary * 1.1，非平凡表达式
   * - 不能传递：无法要求输入按计算字段排序
   * - 结果：需要在Project之后排序
   */
  static @Nullable Pair<RelTraitSet, List<RelTraitSet>> passThroughTraitsForProject( // 方法定义：为Project传递特征到输入
      RelTraitSet required, // 参数1：上层要求的特征集合
      List<RexNode> exps, // 参数2：Project的投影表达式列表
      RelDataType inputRowType, // 参数3：输入行的类型信息
      RelDataTypeFactory typeFactory, // 参数4：类型工厂
      RelTraitSet currentTraits) { // 参数5：Project当前的特征集合
    final RelCollation collation = required.getCollation(); // 获取上层要求的排序规则
    if (collation == null || collation == RelCollations.EMPTY) { // 如果没有排序要求或排序要求为空
      return null; // 返回null：不需要传递排序特征
    }

    final Mappings.TargetMapping map = // 创建目标映射（输入字段到输出字段的映射）
        RelOptUtil.permutationIgnoreCast( // 使用工具方法创建映射，忽略CAST操作
            exps, inputRowType); // 传入投影表达式和输入行类型

    if (collation.getFieldCollations().stream().anyMatch( // 检查排序要求中的所有字段排序规则
        rc -> !isCollationOnTrivialExpr(exps, typeFactory, // 如果存在任何一个字段不是基于平凡表达式
            map, rc, true))) { // 则无法传递排序特征
      return null; // 返回null：无法传递排序要求
    }

    final RelCollation newCollation = collation.apply(map); // 应用映射到排序规则，得到新的排序规则
    return Pair.of(currentTraits.replace(collation), // 返回值对：第一个元素是Project采纳的特征（保持原排序要求）
        ImmutableList.of(currentTraits.replace(newCollation))); // 第二个元素是输入采纳的特征（应用映射后的排序要求）
  }

  /**
   * 方法说明：为Project操作从下层输入推导特征（traits）
   * 
   * 这个方法实现了"特征推导"机制，即当Project的输入已经有序时，
   * 判断Project操作能否保持或推导出新的排序规则，从而避免在Project之前排序。
   * 
   * 与passThroughTraitsForProject的区别：
   * - passThroughTraitsForProject：从上层要求向下传递（满足上层需求）
   * - deriveTraitsForProject：从下层输入向上推导（利用下层已有的排序）
   * 
   * 核心概念：
   * - 特征推导：从下层的特征推导出当前节点的特征
   * - 前缀排序：如果输入按字段A、B、C排序，输出可能只保持按A、B排序
   * - 映射反转：输入字段索引到输出字段索引的反向映射
   * 
   * 参数说明：
   * @param childTraits 子节点（输入）的特征集合
   * @param childId 子节点ID（0表示唯一子节点，预留用于扩展）
   * @param exps Project操作的投影表达式列表
   * @param inputRowType 输入行的类型
   * @param typeFactory 类型工厂
   * @param currentTraits Project当前的特征集合
   * @return Pair<RelTraitSet, List<RelTraitSet>> 返回值对：
   *         - first: Project采纳的特征集合（推导出的排序）
   *         - second: 子节点采纳的特征集合列表
   *         如果无法推导特征，返回null
   * 
   * 工作流程：
   * 1. 检查输入是否有排序，如果没有则返回null
   * 2. 创建反向映射（输出字段到输入字段）
   * 3. 遍历输入的排序字段，检查能否在输出中保持排序
   * 4. 只能保持前缀排序（一旦某个字段无法保持，后面的字段也无法保持）
   * 5. 计算推导出的排序规则
   * 6. 返回特征集合
   * 
   * 示例场景：
   * 场景：输入按emp_id、dept_id排序，Project: SELECT emp_id, emp_name FROM emp
   * - emp_id直接来自输入字段0，可以保持排序
   * - emp_name不是排序字段，不影响
   * - 可以推导：输出按emp_id排序
   * 
   * 场景：输入按dept_id、emp_id排序，Project: SELECT emp_id FROM emp
   * - emp_id来自输入字段1
   * - 输入按dept_id, emp_id排序，但Project只保留emp_id
   * - 不能保持完整的排序，只能推导：输出按emp_id排序（部分排序）
   * 
   * 场景：输入按salary排序，Project: SELECT CAST(salary AS BIGINT) FROM emp
   * - CAST保持单调性，可以传递排序
   * - 可以推导：输出按CAST(salary)排序
   */
  static @Nullable Pair<RelTraitSet, List<RelTraitSet>> deriveTraitsForProject( // 方法定义：从子节点推导Project的特征
      RelTraitSet childTraits, // 参数1：子节点的特征集合
      int childId, // 参数2：子节点ID（预留参数，当前只支持单子节点）
      List<RexNode> exps, // 参数3：Project的投影表达式列表
      RelDataType inputRowType, // 参数4：输入行的类型
      RelDataTypeFactory typeFactory, // 参数5：类型工厂
      RelTraitSet currentTraits) { // 参数6：Project当前的特征集合
    final RelCollation collation = childTraits.getCollation(); // 获取子节点的排序规则
    if (collation == null || collation == RelCollations.EMPTY) { // 如果子节点没有排序或排序为空
      return null; // 返回null：无法推导排序特征
    }

    final int maxField = Math.max(exps.size(), inputRowType.getFieldCount()); // 计算最大字段数（输出字段数和输入字段数的较大值）
    Mappings.TargetMapping mapping = Mappings // 创建映射（输出字段到输入字段）
        .create(MappingType.FUNCTION, maxField, maxField); // 创建函数映射，源和目标都是maxField
    for (Ord<RexNode> node : Ord.zip(exps)) { // 遍历所有投影表达式（带索引）
      if (node.e instanceof RexInputRef) { // 如果表达式是输入字段引用
        mapping.set(((RexInputRef) node.e).getIndex(), node.i); // 建立映射：输入字段索引 -> 输出字段索引
      } else if (node.e.isA(SqlKind.CAST)) { // 如果表达式是类型转换
        final RexNode operand = ((RexCall) node.e).getOperands().get(0); // 获取CAST的操作数
        if (operand instanceof RexInputRef) { // 如果操作数是输入字段引用
          mapping.set(((RexInputRef) operand).getIndex(), node.i); // 建立映射：操作数字段索引 -> 输出字段索引
        }
      }
    }

    List<RelFieldCollation> collationFieldsToDerive = new ArrayList<>(); // 创建列表，存储可以推导的字段排序规则
    for (RelFieldCollation rc : collation.getFieldCollations()) { // 遍历子节点的所有字段排序规则
      if (isCollationOnTrivialExpr(exps, typeFactory, mapping, rc, false)) { // 检查该字段是否可以推导（向上推导模式）
        collationFieldsToDerive.add(rc); // 可以推导，添加到列表
      } else { // 不能推导
        break; // 跳出循环：只能保持前缀排序，一旦某个字段无法保持，后面的字段也无法保持
      }
    }

    if (!collationFieldsToDerive.isEmpty()) { // 如果有可以推导的字段排序规则
      final RelCollation newCollation = RelCollations // 创建新的排序规则
          .of(collationFieldsToDerive).apply(mapping); // 从可推导的字段创建排序规则，并应用映射
      return Pair.of(currentTraits.replace(newCollation), // 返回值对：第一个元素是Project采纳的特征（推导出的排序）
          ImmutableList.of(currentTraits.replace(collation))); // 第二个元素是子节点采纳的特征（保持原排序）
    } else { // 没有可推导的字段排序规则
      return null; // 返回null：无法推导排序特征
    }
  }

  /**
   * 方法说明：为Join操作传递特征（traits）到左输入
   * 
   * 这个方法实现了Join操作的特征传递，特别是将排序要求传递给Join的左输入。
   * 只支持传递到左输入，因为：
   * 1. 内连接和左连接可以保持左输入的排序
   * 2. 全连接和右连接可能打乱左输入的顺序
   * 3. 不支持传递到右输入（因为连接操作可能改变右输入的顺序）
   * 
   * 核心概念：
   * - Join类型限制：只有INNER、LEFT、SEMI、ANTI等连接类型可以保持左输入排序
   * - 字段归属：排序字段必须属于左输入（字段索引 < leftInputFieldCount）
   * - 特征传递：避免在Join之后排序，而是要求左输入预先排序
   * 
   * 参数说明：
   * @param required 上层要求的特征集合（包含排序规则）
   * @param joinType Join操作的类型（INNER、LEFT、RIGHT、FULL等）
   * @param leftInputFieldCount 左输入的字段数量（用于判断字段归属）
   * @param joinTraitSet Join当前的特征集合
   * @return Pair<RelTraitSet, List<RelTraitSet>> 返回值对：
   *         - first: Join采纳的特征集合
   *         - second: 子节点采纳的特征集合列表（左输入、右输入）
   *         如果无法传递特征，返回null
   * 
   * 工作流程：
   * 1. 检查上层是否要求排序，如果没有则返回null
   * 2. 检查Join类型是否支持传递（排除FULL和RIGHT）
   * 3. 检查排序要求的所有字段是否都属于左输入
   * 4. 如果通过检查，返回特征集合
   * 5. 左输入采纳排序要求，右输入采纳空排序
   * 
   * 示例场景：
   * 场景：SQL: SELECT * FROM emp LEFT JOIN dept ON emp.dept_id = dept.id ORDER BY emp.emp_id
   * - 上层要求输出按emp.emp_id排序
   * - emp.emp_id属于左输入，字段索引 < leftInputFieldCount
   * - Join类型是LEFT，支持传递
   * - 可以传递：要求左输入按emp_id排序
   * 
   * 场景：SQL: SELECT * FROM emp RIGHT JOIN dept ON emp.dept_id = dept.id ORDER BY emp.emp_id
   * - 上层要求输出按emp.emp_id排序
   * - Join类型是RIGHT，不支持传递（右连接可能打乱左输入顺序）
   * - 不能传递：需要在Join之后排序
   * 
   * 场景：SQL: SELECT * FROM emp JOIN dept ON emp.dept_id = dept.id ORDER BY dept.name
   * - 上层要求输出按dept.name排序
   * - dept.name属于右输入，字段索引 >= leftInputFieldCount
   * - 不能传递：只能传递到左输入
   */
  static @Nullable Pair<RelTraitSet, List<RelTraitSet>> passThroughTraitsForJoin( // 方法定义：为Join传递特征到左输入
      RelTraitSet required, // 参数1：上层要求的特征集合
      JoinRelType joinType, // 参数2：Join操作的类型
      int leftInputFieldCount, // 参数3：左输入的字段数量
      RelTraitSet joinTraitSet) { // 参数4：Join当前的特征集合
    RelCollation collation = required.getCollation(); // 获取上层要求的排序规则
    if (collation == null // 如果没有排序要求
        || collation == RelCollations.EMPTY // 或排序要求为空
        || joinType == JoinRelType.FULL // 或Join类型是全连接（不支持传递）
        || joinType == JoinRelType.RIGHT) { // 或Join类型是右连接（不支持传递）
      return null; // 返回null：无法传递排序特征
    }

    for (RelFieldCollation fc : collation.getFieldCollations()) { // 遍历排序要求中的所有字段排序规则
      // If field collation belongs to right input: cannot push down collation. // 注释：如果字段排序属于右输入，则不能传递排序
      if (fc.getFieldIndex() >= leftInputFieldCount) { // 检查字段索引是否 >= 左输入字段数（即属于右输入）
        return null; // 返回null：无法传递排序特征
      }
    }

    RelTraitSet passthroughTraitSet = joinTraitSet.replace(collation); // 创建传递特征集合：将Join的排序替换为要求的排序
    return Pair.of(passthroughTraitSet, // 返回值对：第一个元素是Join采纳的特征（保持排序要求）
        ImmutableList.of( // 第二个元素是子节点采纳的特征集合列表
            passthroughTraitSet, // 左输入采纳排序要求
            passthroughTraitSet.replace(RelCollations.EMPTY))); // 右输入采纳空排序（不要求排序）
  }

  /**
   * 方法说明：为Join操作从左输入推导特征（traits）
   * 
   * 这个方法实现了Join操作的特征推导，特别是从Join的左输入推导排序规则。
   * 只支持从左输入推导，因为：
   * 1. 内连接和左连接可以保持左输入的排序
   * 2. 全连接和右连接可能打乱左输入的顺序
   * 3. 不支持从右输入推导（因为连接操作可能改变右输入的顺序）
   * 
   * 与passThroughTraitsForJoin的区别：
   * - passThroughTraitsForJoin：从上层要求向下传递到左输入
   * - deriveTraitsForJoin：从左输入向上推导到Join
   * 
   * 核心概念：
   * - 左输入排序保持：某些Join类型可以保持左输入的排序
   * - Merge Join优化：如果左输入有序，可以使用归并连接，提高性能
   * - 特征推导：利用下层的排序，避免在Join之后排序
   * 
   * 参数说明：
   * @param childTraits 子节点（左输入）的特征集合
   * @param childId 子节点ID（必须为0，表示左输入）
   * @param joinType Join操作的类型
   * @param joinTraitSet Join当前的特征集合
   * @param rightTraitSet 右输入的特征集合
   * @return Pair<RelTraitSet, List<RelTraitSet>> 返回值对：
   *         - first: Join采纳的特征集合（推导出的排序）
   *         - second: 子节点采纳的特征集合列表（左输入、右输入）
   *         如果无法推导特征，返回null
   * 
   * 工作流程：
   * 1. 断言childId必须为0（只支持从左输入推导）
   * 2. 检查左输入是否有排序，如果没有则返回null
   * 3. 检查Join类型是否支持推导（排除FULL和RIGHT）
   * 4. 如果通过检查，返回特征集合
   * 5. Join采纳左输入的排序，左输入保持原排序，右输入保持原特征
   * 
   * 示例场景：
   * 场景：左输入按emp_id排序，Join: emp LEFT JOIN dept ON emp.dept_id = dept.id
   * - Join类型是LEFT，支持推导
   * - 可以推导：Join输出按emp_id排序
   * - 优化机会：可以使用归并连接，避免排序
   * 
   * 场景：左输入按emp_id排序，Join: emp RIGHT JOIN dept ON emp.dept_id = dept.id
   * - Join类型是RIGHT，不支持推导（右连接可能打乱左输入顺序）
   * - 不能推导：Join输出无序
   * 
   * 场景：左输入按emp_id排序，Join: emp FULL JOIN dept ON emp.dept_id = dept.id
   * - Join类型是FULL，不支持推导（全连接可能打乱左输入顺序）
   * - 不能推导：Join输出无序
   */
  static @Nullable Pair<RelTraitSet, List<RelTraitSet>> deriveTraitsForJoin( // 方法定义：从左输入推导Join的特征
      RelTraitSet childTraits, // 参数1：子节点（左输入）的特征集合
      int childId, // 参数2：子节点ID（必须为0，表示左输入）
      JoinRelType joinType, // 参数3：Join操作的类型
      RelTraitSet joinTraitSet, // 参数4：Join当前的特征集合
      RelTraitSet rightTraitSet) { // 参数5：右输入的特征集合
    // should only derive traits (limited to collation for now) from left join input. // 注释：应该只从左连接输入推导特征（目前仅限于排序）
    assert childId == 0; // 断言：childId必须为0（只支持从左输入推导）

    RelCollation collation = childTraits.getCollation(); // 获取左输入的排序规则
    if (collation == null // 如果左输入没有排序
        || collation == RelCollations.EMPTY // 或排序为空
        || joinType == JoinRelType.FULL // 或Join类型是全连接（不支持推导）
        || joinType == JoinRelType.RIGHT) { // 或Join类型是右连接（不支持推导）
      return null; // 返回null：无法推导排序特征
    }

    RelTraitSet derivedTraits = joinTraitSet.replace(collation); // 创建推导特征集合：将Join的排序替换为左输入的排序
    return Pair.of( // 返回值对
        derivedTraits, // 第一个元素是Join采纳的特征（推导出的排序，即左输入的排序）
        ImmutableList.of(derivedTraits, rightTraitSet)); // 第二个元素是子节点采纳的特征集合列表（左输入保持排序，右输入保持原特征）
  }
}
