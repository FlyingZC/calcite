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
// Apache Calcite InnoDB适配器包，包含InnoDB数据源相关的所有实现类
package org.apache.calcite.adapter.innodb;

// 导入RelOptCluster：关系表达式集群，包含关系表达式树共享的数据
import org.apache.calcite.plan.RelOptCluster;
// 导入RelOptCost：关系表达式成本，用于优化器评估执行计划的代价
import org.apache.calcite.plan.RelOptCost;
// 导入RelOptPlanner：关系表达式优化器，负责生成最优执行计划
import org.apache.calcite.plan.RelOptPlanner;
// 导入RelTraitSet：关系特征集合，定义关系表达式的物理属性（如约定、排序等）
import org.apache.calcite.plan.RelTraitSet;
// 导入RelNode：关系表达式接口，是Calcite中所有关系操作节点的基类
import org.apache.calcite.rel.RelNode;
// 导入Project：投影操作符基类，用于选择和计算输出列
import org.apache.calcite.rel.core.Project;
// 导入RelMetadataQuery：元数据查询接口，用于获取关系表达式的统计信息
import org.apache.calcite.rel.metadata.RelMetadataQuery;
// 导入RelDataType：关系数据类型，描述表中列的类型信息
import org.apache.calcite.rel.type.RelDataType;
// 导入RexNode：行表达式节点，代表表达式树中的节点（如常量、引用、函数调用等）
import org.apache.calcite.rex.RexNode;
// 导入Pair：键值对工具类，用于存储成对的数据
import org.apache.calcite.util.Pair;

// 导入ImmutableList：不可变列表，Guava库提供的线程安全集合
import com.google.common.collect.ImmutableList;
// 导入ImmutableSet：不可变集合，Guava库提供的线程安全集合
import com.google.common.collect.ImmutableSet;

// 导入@Nullable注解：标记参数或返回值可以为null
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入LinkedHashMap：保持插入顺序的Map实现
import java.util.LinkedHashMap;
// 导入List：列表接口
import java.util.List;
// 导入Map：映射接口
import java.util.Map;

// 静态导入requireNonNull方法，用于空值检查
import static java.util.Objects.requireNonNull;

/**
 * InnoDB数据源的Project关系表达式实现类
 * 
 * 【类作用说明】：
 * 这个类是Calcite中Project操作符在InnoDB数据源上的具体实现。Project操作是SQL中最常见的操作之一，
 * 主要用于：
 * 1. 列选择：从输入数据源中选择特定的列
 * 2. 列计算：对列进行计算、转换或应用函数（如 col1 + col2, UPPER(name)等）
 * 3. 列重命名：给输出列指定新的别名
 * 
 * 【设计模式】：
 * - 继承自Project基类，复用Project的标准语义和接口
 * - 实现InnodbRel接口，表明这是一个InnoDB适配器特有的关系表达式
 * - 遵循Calcite的适配器模式，将通用的Project操作转换为InnoDB特定的实现
 * 
 * 【核心功能】：
 * 1. 将RexNode表达式树转换为InnoDB可理解的字段引用
 * 2. 维护输入输出字段的映射关系（原始字段名 -> 别名）
 * 3. 生成InnoDB SELECT语句所需的字段列表
 * 4. 提供成本估算，帮助优化器选择最优执行计划
 * 
 * 【使用场景】：
 * 当SQL查询中包含SELECT子句指定列、列表达式或别名时，会创建InnodbProject节点。
 * 例如：SELECT emp_id, salary * 1.1 AS new_salary FROM employees
 * 
 * 【与其他类的关系】：
 * - Input（输入）：通常是InnodbTableScan（表扫描）或其他InnodbRel节点
 * - Output（输出）：可以作为InnodbFilter、InnodbJoin等节点的输入
 * - 协作：与InnodbRules.RexToInnodbTranslator配合，将表达式转换为InnoDB字段引用
 * 
 * 【实现特点】：
 * 1. 成本系数为0.1，表示Project操作的成本较低
 * 2. 使用LinkedHashMap保持字段顺序，确保输出列的顺序与SQL一致
 * 3. 通过implement方法实现将关系表达式转换为可执行代码的逻辑
 */
public class InnodbProject extends Project implements InnodbRel {
  /**
   * 【构造方法】：创建InnodbProject关系表达式节点
   * 
   * 【方法作用】：初始化InnodbProject实例，设置其输入、投影表达式、输出行类型等核心属性
   * 
   * 【参数详解】：
   * @param cluster - RelOptCluster类型，关系表达式集群对象，包含：
   *                - 查询优化器引用
   *                - 类型工厂（用于创建数据类型）
   *                - RexBuilder（用于构建表达式）
   *                - 该集群内所有关系表达式共享的上下文信息
   * 
   * @param traitSet - RelTraitSet类型，关系特征集合，定义此节点的物理属性：
   *                 - Convention（约定）：必须是InnodbRel.CONVENTION，表示这是一个InnoDB节点
   *                 - RelCollation（排序）：输出结果的排序方式
   *                 - RelDistribution（分布）：数据分布方式（如单机、广播、分片等）
   * 
   * @param input - RelNode类型，输入关系表达式节点：
   *              - 通常是InnodbTableScan（扫描InnoDB表）
   *              - 或其他InnodbRel节点（如InnodbFilter、InnodbJoin等）
   *              - 提供Project操作的数据源
   * 
   * @param projects - List<? extends RexNode>类型，投影表达式列表：
   *                  - 每个RexNode代表一个输出列的表达式
   *                  - 可以是简单的字段引用（如 $0 表示第一个字段）
   *                  - 可以是复杂表达式（如 +($1, 100) 表示字段1加100）
   *                  - 可以是函数调用（如 CAST($0, VARCHAR) 等）
   *                  - 列表顺序决定了输出列的顺序
   * 
   * @param rowType - RelDataType类型，输出行类型描述：
   *                - 定义了Project操作后的行结构
   *                - 包含每个输出列的名称、类型、是否可空等信息
   *                - 由projects表达式计算得出
   * 
   * 【实现逻辑】：
   * 1. 调用父类Project的构造方法，传入所有必要参数：
   *    - ImmutableList.of()：空的条件表达式列表（Project本身不包含过滤条件）
   *    - ImmutableSet.of()：空的变量集合
   * 2. 断言检查：确保此节点的约定是InnodbRel.CONVENTION
   * 3. 断言检查：确保输入节点的约定也是InnodbRel.CONVENTION（约定必须一致）
   * 
   * 【断言说明】：
   * - getConvention() == InnodbRel.CONVENTION：确保此节点使用InnoDB约定
   * - getConvention() == input.getConvention()：确保输入节点也使用InnoDB约定
   * - 如果断言失败，说明优化器生成了错误的执行计划
   * 
   * 【使用示例】：
   * 对于SQL: SELECT emp_id, salary * 1.1 AS new_salary FROM employees
   * - input: InnodbTableScan(employees表)
   * - projects: [$0, *($1, 1.1)]  // $0是emp_id, $1是salary
   * - rowType: RelDataType{emp_id:INT, new_salary:DECIMAL}
   */
  InnodbProject(RelOptCluster cluster, RelTraitSet traitSet,
      RelNode input, List<? extends RexNode> projects, RelDataType rowType) {
    // 调用父类Project构造方法，初始化Project关系表达式
    // ImmutableList.of()：空的变量列表（Project不使用变量）
    // input：输入节点（如表扫描）
    // projects：投影表达式列表（要输出的列）
    // rowType：输出行类型（包含列名和类型信息）
    // ImmutableSet.of()：空的字段标记集合
    super(cluster, traitSet, ImmutableList.of(), input, projects, rowType, ImmutableSet.of());
    // 断言：确保此节点使用InnoDB约定（CONVENTION是InnodbRel接口定义的常量）
    assert getConvention() == InnodbRel.CONVENTION;
    // 断言：确保输入节点也使用InnoDB约定，约定必须在整个关系树中保持一致
    assert getConvention() == input.getConvention();
  }

  /**
   * 【copy方法】：创建InnodbProject的副本
   * 
   * 【方法作用】：生成此InnodbProject节点的一个新副本，可以修改某些属性（如特征集、输入、投影表达式等）
   * 
   * 【为什么需要copy方法】：
   * 1. 优化器在探索不同的执行计划时，需要复制节点并修改其属性
   * 2. 规则转换时，需要创建修改后的节点版本
   * 3. 保持不可变性：Calcite中的关系表达式是不可变的，修改需要创建新副本
   * 
   * 【参数详解】：
   * @param traitSet - RelTraitSet类型，新的关系特征集合：
   *                 - 可以包含不同的排序约定
   *                 - 可以包含不同的分布策略
   *                 - 通常由优化器规则转换时传入
   * 
   * @param input - RelNode类型，新的输入节点：
   *              - 可以是不同的输入（如经过规则转换后的输入）
   *              - 必须是RelNode类型（关系表达式节点）
   * 
   * @param projects - List<RexNode>类型，新的投影表达式列表：
   *                  - 可以是优化后的表达式（如常量折叠、表达式简化等）
   *                  - 可以是不同的列选择
   * 
   * @param rowType - RelDataType类型，新的输出行类型：
   *                - 必须与新的projects表达式匹配
   *                - 定义了新节点的输出结构
   * 
   * 【返回值】：
   * @return Project类型，返回新创建的InnodbProject实例
   * 
   * 【实现逻辑】：
   * 1. 使用getCluster()获取当前节点的集群对象（保持集群不变）
   * 2. 使用传入的traitSet创建新的特征集
   * 3. 使用传入的input作为新的输入节点
   * 4. 使用传入的projects作为新的投影表达式列表
   * 5. 使用传入的rowType作为新的输出行类型
   * 6. 创建并返回新的InnodbProject实例
   * 
   * 【使用场景】：
   * 1. 优化器应用规则时：如ProjectMergeRule（合并连续的Project）
   * 2. 改变物理属性时：如添加排序约定
   * 3. 表达式优化时：如常量折叠、表达式简化
   * 
   * 【示例】：
   * 原节点: InnodbProject(projects: [$0, $1], rowType: {a:INT, b:INT})
   * copy调用: copy(traitSet, input, [$0, $1, +($0,$1)], {a:INT, b:INT, c:INT})
   * 结果: 新的InnodbProject，包含一个额外的计算列
   */
  @Override public Project copy(RelTraitSet traitSet, RelNode input,
      List<RexNode> projects, RelDataType rowType) {
    // 创建并返回新的InnodbProject实例
    // getCluster()：保持当前节点的集群对象不变
    // traitSet：使用新的特征集（可能包含不同的约定、排序、分布等）
    // input：使用新的输入节点
    // projects：使用新的投影表达式列表
    // rowType：使用新的输出行类型
    return new InnodbProject(getCluster(), traitSet, input, projects, rowType);
  }

  /**
   * 【computeSelfCost方法】：计算InnodbProject节点的执行成本
   * 
   * 【方法作用】：估算此Project操作符的执行成本，供优化器用于选择最优执行计划
   * 
   * 【成本计算原理】：
   * 1. Project操作的成本通常包括：
   *    - CPU成本：处理每一行数据，计算投影表达式
   *    - I/O成本：读取输入数据（通常由子节点负责）
   *    - 内存成本：存储中间结果
   * 2. InnoDB中的Project操作成本较低，因为：
   *    - 字段选择只是跳过不需要的列
   *    - 简单表达式计算开销小
   *    - 通常可以和扫描操作合并执行
   * 
   * 【参数详解】：
   * @param planner - RelOptPlanner类型，优化器对象：
   *                - 提供成本工厂（用于创建RelOptCost对象）
   *                - 包含优化器的配置和参数
   *                - 可以是VolcanoPlanner或HepPlanner
   * 
   * @param mq - RelMetadataQuery类型，元数据查询对象：
   *           - 用于获取关系表达式的统计信息
   *           - 可以查询行数、列唯一值数量、列大小分布等
   *           - 帮助更准确地估算成本
   * 
   * 【返回值】：
   * @return RelOptCost类型，返回计算出的成本对象：
   *         - 包含CPU、I/O、内存三个维度的成本
   *         - 可能为null（如果无法计算成本）
   *         - @Nullable注解表示返回值可能为null
   * 
   * 【实现逻辑】：
   * 1. 调用父类Project的computeSelfCost方法，获取基础成本
   * 2. 使用requireNonNull确保成本不为null（如果为null会抛出异常）
   * 3. 将基础成本乘以0.1，表示Project操作的成本较低
   * 4. 返回调整后的成本
   * 
   * 【成本系数0.1的含义】：
   * - 0.1表示Project操作的成本只有基础成本的10%
   * - 这反映了Project操作相对简单的特性
   * - 优化器会更倾向于选择包含Project的计划，因为成本低
   * - 与其他操作（如Join、Sort）相比，Project的成本确实较低
   * 
   * 【成本模型】：
   * RelOptCost通常包含三个组件：
   * - CPU：处理时间（行数 * 每行处理时间）
   * - I/O：磁盘读写时间
   * - MEMORY：内存占用
   * 优化器根据这些成本选择总成本最小的计划
   * 
   * 【使用场景】：
   * 1. 优化器枚举执行计划时，需要比较不同计划的成本
   * 2. 应用规则转换后，需要重新计算成本
   * 3. 成本基于启发式规则，不是精确的执行时间
   * 
   * 【示例】：
   * 基础成本: CPU=1000, I/O=500, MEMORY=100
   * 乘以0.1后: CPU=100, I/O=50, MEMORY=10
   * 这表示Project操作的成本相对较低
   */
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) {
    // 调用父类Project的computeSelfCost方法计算基础成本
    // planner：优化器对象，提供成本工厂等资源
    // mq：元数据查询对象，提供行数、列统计等信息
    // requireNonNull：确保返回的cost不为null，如果为null则抛出NullPointerException
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq));
    // 将基础成本乘以0.1，表示Project操作的成本较低
    // 0.1是一个经验值，反映了Project操作相对于其他操作的低成本特性
    // 优化器会根据这个成本选择最优的执行计划
    return cost.multiplyBy(0.1);
  }

  /**
   * 【implement方法】：实现InnodbProject节点，生成InnoDB执行代码
   * 
   * 【方法作用】：将InnodbProject关系表达式转换为可执行的InnoDB查询代码
   * 这是InnodbRel接口的核心方法，负责将逻辑计划转换为物理执行计划
   * 
   * 【核心概念】：
   * 1. Implementor：代码生成器，负责遍历关系树并生成执行代码
   * 2. RexToInnodbTranslator：表达式转换器，将RexNode表达式转换为InnoDB字段引用
   * 3. 字段映射：建立原始字段名到输出别名的映射关系
   * 
   * 【参数详解】：
   * @param implementor - Implementor类型，代码生成器对象：
   *                    - 维护当前生成的SQL语句状态
   *                    - 管理已访问的子节点
   *                    - 提供添加SELECT字段、WHERE条件等方法
   *                    - 负责生成最终的InnoDB查询语句
   * 
   * 【返回值】：无返回值（void类型）
   * 
   * 【实现逻辑详解】：
   * 
   * 步骤1：访问子节点（输入）
   * - implementor.visitChild(0, getInput())：递归访问输入节点
   * - 0表示这是第0个子节点（Project只有一个输入）
   * - 这会触发输入节点的implement方法，生成FROM子句等
   * - 例如：如果输入是InnodbTableScan，会生成 "FROM employees"
   * 
   * 步骤2：创建表达式转换器
   * - InnodbRules.RexToInnodbTranslator：将RexNode转换为InnoDB字段引用
   * - InnodbRules.innodbFieldNames(getInput().getRowType())：获取输入行的字段名列表
   * - 转换器需要知道输入字段的名称，才能正确转换表达式中的字段引用
   * 
   * 步骤3：创建字段映射表
   * - LinkedHashMap：保持插入顺序，确保输出列顺序与SQL一致
   * - Key：原始字段名（如 "salary", "emp_id"）
   * - Value：输出别名（如 "new_salary", "employee_id"）
   * 
   * 步骤4：遍历所有投影表达式
   * - getNamedProjects()：获取命名的投影表达式列表
   * - 返回List<Pair<RexNode, String>>，每个Pair包含：
   *   - left：RexNode表达式（如 *($1, 1.1)）
   *   - right：输出别名（如 "new_salary"）
   * 
   * 步骤5：转换每个表达式并建立映射
   * - pair.right：获取输出别名
   * - pair.left.accept(translator)：将RexNode表达式转换为InnoDB字段引用
   *   - 简单字段引用：$0 -> "emp_id"
   *   - 表达式：+($1, 100) -> "salary + 100"
   *   - 函数调用：UPPER($2) -> "UPPER(name)"
   * - fields.put(originalName, name)：建立映射关系
   * 
   * 步骤6：添加SELECT字段到implementor
   * - implementor.addSelectFields(fields)：将字段映射添加到代码生成器
   * - implementor会根据这些字段生成SELECT子句
   * - 例如：SELECT emp_id, salary * 1.1 AS new_salary
   * 
   * 【完整流程示例】：
   * SQL: SELECT emp_id, salary * 1.1 AS new_salary FROM employees
   * 
   * 1. visitChild(0, getInput()) -> 生成 "FROM employees"
   * 2. 创建转换器，知道输入字段是 [emp_id, salary, name]
   * 3. 遍历投影表达式：
   *    - Pair($0, "emp_id") -> 转换 $0 -> "emp_id" -> fields.put("emp_id", "emp_id")
   *    - Pair(*($1, 1.1), "new_salary") -> 转换 *($1, 1.1) -> "salary * 1.1" -> fields.put("salary * 1.1", "new_salary")
   * 4. addSelectFields(fields) -> 生成 "SELECT emp_id, salary * 1.1 AS new_salary"
   * 
   * 最终SQL: SELECT emp_id, salary * 1.1 AS new_salary FROM employees
   * 
   * 【关键技术点】：
   * 1. 访问者模式：使用accept方法让表达式自我转换
   * 2. 递归遍历：先访问子节点，再处理当前节点
   * 3. 代码生成：将关系树逐步转换为SQL语句
   * 4. 顺序保持：使用LinkedHashMap确保输出列顺序
   * 
   * 【注意事项】：
   * - 必须先调用visitChild访问输入节点
   * - 字段映射的顺序决定了SQL中SELECT字段的顺序
   * - 复杂表达式会被转换为对应的SQL表达式
   * - 别名会正确处理（AS子句）
   */
  @Override public void implement(Implementor implementor) {
    // 步骤1：访问子节点（输入），递归生成输入节点的代码
    // 0：表示这是第0个子节点（Project只有一个输入）
    // getInput()：获取输入节点（通常是InnodbTableScan或其他InnodbRel）
    // visitChild会调用输入节点的implement方法，生成FROM子句等
    implementor.visitChild(0, getInput());
    
    // 步骤2：创建Rex表达式到InnoDB字段的转换器
    // InnodbRules.RexToInnodbTranslator：负责将RexNode表达式树转换为InnoDB可理解的字符串
    // InnodbRules.innodbFieldNames(getInput().getRowType())：获取输入行的所有字段名列表
    //   - getInput().getRowType()：获取输入节点的行类型
    //   - innodbFieldNames()：从行类型中提取字段名列表
    // 转换器需要知道字段名，才能将表达式中的字段引用（如$0）转换为实际字段名（如"emp_id"）
    final InnodbRules.RexToInnodbTranslator translator =
        new InnodbRules.RexToInnodbTranslator(
            InnodbRules.innodbFieldNames(getInput().getRowType()));
    
    // 步骤3：创建字段映射表，使用LinkedHashMap保持插入顺序
    // Key：原始字段名或表达式（如 "salary * 1.1"）
    // Value：输出别名（如 "new_salary"）
    // LinkedHashMap保证字段顺序与SQL中的顺序一致
    final Map<String, String> fields = new LinkedHashMap<>();
    
    // 步骤4：遍历所有命名的投影表达式
    // getNamedProjects()：返回List<Pair<RexNode, String>>
    //   - Pair.left：RexNode表达式（如 *($1, 1.1) 表示字段1乘以1.1）
    //   - Pair.right：输出别名（如 "new_salary"）
    // 每个Pair代表一个输出列
    for (Pair<RexNode, String> pair : getNamedProjects()) {
      // 获取输出别名（右侧值）
      // 这是SQL中AS后面的名称，如 "new_salary"
      final String name = pair.right;
      
      // 将RexNode表达式转换为InnoDB字段引用字符串
      // pair.left：RexNode表达式
      // accept(translator)：使用访问者模式，让表达式自我转换为字符串
      //   - 简单字段引用 $0 -> "emp_id"
      //   - 算术表达式 +($1, 100) -> "salary + 100"
      //   - 函数调用 UPPER($2) -> "UPPER(name)"
      final String originalName = pair.left.accept(translator);
      
      // 将原始字段名（或表达式）映射到输出别名
      // Key：原始字段名或表达式字符串
      // Value：输出别名
      // 例如：fields.put("salary * 1.1", "new_salary")
      fields.put(originalName, name);
    }
    
    // 步骤5：将字段映射添加到implementor，生成SELECT子句
    // implementor会根据fields映射生成SQL的SELECT部分
    // 例如：fields = {"emp_id"->"emp_id", "salary * 1.1"->"new_salary"}
    // 生成：SELECT emp_id, salary * 1.1 AS new_salary
    implementor.addSelectFields(fields);
  }
// 类定义结束：InnodbProject类
// 这个类实现了InnoDB数据源的Project操作，负责将SQL的SELECT子句转换为InnoDB可执行的查询
// 核心功能包括：表达式转换、字段映射、代码生成、成本估算
}
