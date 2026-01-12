/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * // Apache 许可证声明，指定开源协议版本为 2.0
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 * // 许可证获取地址
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * // 除非适用法律要求或书面同意，否则按"原样"分发，不提供任何明示或暗示的保证
 */
package org.apache.calcite.adapter.pig; // 声明包名，该类位于 org.apache.calcite.adapter.pig 包下，属于 Calcite 的 Pig 适配器模块

import org.apache.calcite.plan.Convention; // 导入 Calcite 的 Convention 类，用于定义关系表达式的调用约定
import org.apache.calcite.rel.RelNode; // 导入 Calcite 的 RelNode 接口，表示关系代数表达式节点

import java.util.ArrayList; // 导入 Java 集合框架的 ArrayList 类，用于动态数组
import java.util.List; // 导入 Java 集合框架的 List 接口，用于列表操作

/**
 * Relational expression that uses the Pig calling convention.
 * // 定义 PigRel 接口，表示使用 Pig 调用约定的关系表达式
 * // Pig 是 Apache 的一个大数据处理平台，使用 Pig Latin 脚本语言进行数据处理
 * // 该接口是 Calcite 适配器模式的一部分，用于将 SQL 查询转换为 Pig Latin 脚本
 * // 通过实现这个接口，Calcite 可以将 SQL 查询下推到 Pig 执行引擎
 */
public interface PigRel extends RelNode { // PigRel 接口继承自 RelNode，表示它是一个关系表达式节点

  /**
   * Converts this node to a Pig Latin statement.
   * // 核心方法：将当前关系表达式节点转换为 Pig Latin 语句
   * // 这是适配器模式的关键方法，每个 PigRel 实现类都需要提供将自身转换为 Pig Latin 的逻辑
   * // 例如：LogicalProject 可能转换为 FOREACH...GENERATE 语句
   * // 例如：LogicalFilter 可能转换为 FILTER 语句
   * // 例如：LogicalJoin 可能转换为 JOIN 语句
   * 
   * @param implementor the implementor that accumulates the statements
   * // implementor 参数是一个回调对象，用于累积生成的 Pig Latin 语句
   * // 通过调用 implementor.addStatement() 方法，将转换后的语句添加到脚本中
   * // implementor 还提供了访问子节点、获取表名、字段名等辅助方法
   */
  void implement(Implementor implementor); // 声明 implement 方法，要求实现类必须提供将节点转换为 Pig Latin 的逻辑

  // String getPigRelationAlias();
  // // 注释掉的代码：原本用于获取 Pig 关系别名的方法声明
  // // 这个方法可能被移除或重构到 Implementor 类中
  //
  // String getFieldName(int index);
  // // 注释掉的代码：原本用于根据索引获取字段名的方法声明
  // // 这个方法可能被移除或重构到 Implementor 类中

  /** Calling convention for relational operations that occur in Pig. */
  // 定义 Pig 调用约定常量，用于标识在 Pig 中执行的关系操作
  // Convention 是 Calcite 中的概念，用于区分不同的执行引擎或调用约定
  // 例如：CONVENTION.ENUMERABLE 表示使用 Java 枚举方式执行
  // 例如：CONVENTION.PHYSICAL 表示物理执行计划
  // 这里 "PIG" 表示使用 Pig Latin 执行
  // PigRel.class 指定使用此约定的节点必须实现 PigRel 接口
  Convention CONVENTION = new Convention.Impl("PIG", PigRel.class); // 创建 Pig 调用约定实例，标识为 "PIG"，绑定到 PigRel 接口

  /**
   * Callback for the implementation process that converts a tree of
   * {@link PigRel} nodes into complete Pig Latin script.
   * // Implementor 类是实现过程的回调对象，负责将 PigRel 节点树转换为完整的 Pig Latin 脚本
   * // 这是一个访问者模式的实现，通过遍历关系表达式树来生成对应的 Pig Latin 语句
   * // 主要职责：
   * // 1. 维护生成的 Pig Latin 语句列表
   * // 2. 提供访问子节点的机制（visitChild 方法）
   * // 3. 提供获取表名、字段名等元数据的辅助方法
   * // 4. 将所有语句组合成最终的 Pig Latin 脚本
   */
  class Implementor { // 定义 Implementor 内部类，作为 PigRel 节点转换为 Pig Latin 脚本的回调对象

    /**
     * An ordered list of Pig Latin statements.
     * // statements 成员变量：存储有序的 Pig Latin 语句列表
     * // 使用 final 修饰，保证引用不可变，但列表内容可以添加
     * // 使用 ArrayList 实现，保证插入顺序和查询效率
     * // 每个语句对应 Pig Latin 语言中的一条指令，如 LOAD、FILTER、GROUP 等
     * // 语句按生成顺序存储，最终按此顺序输出为 Pig Latin 脚本
     *
     * <p>See
     * <a href="https://pig.apache.org/docs/r0.13.0/start.html#pl-statements">
     * Pig Latin reference</a>.
     * // 参考 Pig Latin 官方文档，了解语句的语法和用法
     */
    private final List<String> statements = new ArrayList<>(); // 初始化语句列表为空的 ArrayList，用于存储生成的 Pig Latin 语句

    /**
     * 获取输入关系节点对应的表名。
     * // getTableName 方法：从 RelNode 中提取表名
     * // 表名用于生成 Pig Latin 中的 LOAD 语句或其他引用表的语句
     * // 例如：如果表的限定名是 ["catalog", "schema", "employees"]，则返回 "employees"
     * 
     * @param input 输入的关系节点，可能是 TableScan 或其他产生表的节点
     * // input 参数：表示输入的关系表达式节点，通常包含表信息
     * 
     * @return 表的简单名称（不包含 schema 和 catalog）
     * // 返回值：返回表的最后一部分名称，即表名本身
     */
    public String getTableName(RelNode input) { // 定义 getTableName 方法，用于从 RelNode 获取表名
      final List<String> qualifiedName = input.getTable().getQualifiedName(); // 获取表的限定名列表，如 ["catalog", "schema", "table"]
      return qualifiedName.get(qualifiedName.size() - 1); // 返回限定名列表的最后一个元素，即表名
    }

    /**
     * 获取输入关系节点对应的 Pig 关系别名。
     * // getPigRelationAlias 方法：获取 Pig 关系别名
     * // 在 Pig Latin 中，每个关系操作都会产生一个别名，用于后续引用
     * // 例如：A = LOAD 'data' USING PigStorage(',') AS (x:int, y:int); 中的 "A" 就是别名
     * // 这个方法目前直接使用表名作为别名，简化了别名生成逻辑
     * 
     * @param input 输入的关系节点
     * // input 参数：表示输入的关系表达式节点
     * 
     * @return Pig 关系别名
     * // 返回值：返回表名作为 Pig 关系别名
     */
    public String getPigRelationAlias(RelNode input) { // 定义 getPigRelationAlias 方法，用于获取 Pig 关系别名
      return getTableName(input); // 直接调用 getTableName 方法，使用表名作为别名
    }

    /**
     * 获取输入关系节点指定索引位置的字段名。
     * // getFieldName 方法：根据字段索引获取字段名称
     * // 在生成 Pig Latin 语句时，需要引用具体的字段名
     * // 例如：在 FOREACH...GENERATE 语句中，需要指定字段名
     * 
     * @param input 输入的关系节点
     * // input 参数：表示输入的关系表达式节点，包含行类型信息
     * 
     * @param index 字段在行类型中的索引位置（从 0 开始）
     * // index 参数：字段的索引位置，0 表示第一个字段
     * 
     * @return 字段名称
     * // 返回值：返回指定索引位置的字段名
     */
    public String getFieldName(RelNode input, int index) { // 定义 getFieldName 方法，用于根据索引获取字段名
      return input.getRowType().getFieldList().get(index).getName(); // 获取行类型的字段列表，返回指定索引的字段名
    }

    /**
     * 添加一条 Pig Latin 语句到语句列表中。
     * // addStatement 方法：将生成的 Pig Latin 语句添加到语句列表
     * // 这是实现过程中最常用的方法之一
     * // 每个 PigRel 实现类在 implement 方法中调用此方法来累积语句
     * // 语句按添加顺序存储，最终按此顺序输出
     * 
     * @param statement 要添加的 Pig Latin 语句字符串
     * // statement 参数：要添加的 Pig Latin 语句，如 "A = LOAD 'data' USING PigStorage(',');"
     */
    public void addStatement(String statement) { // 定义 addStatement 方法，用于添加 Pig Latin 语句
      statements.add(statement); // 将语句添加到 statements 列表的末尾
    }

    /**
     * 访问并实现子节点。
     * // visitChild 方法：递归访问子节点并实现其 Pig Latin 转换
     * // 这是深度优先遍历关系表达式树的关键方法
     * // 通过调用子节点的 implement 方法，实现递归转换
     * 
     * @param ordinal 子节点在父节点中的序号（目前只支持 0）
     * // ordinal 参数：子节点的序号，当前实现只处理序号为 0 的子节点
     * 
     * @param input 子节点关系表达式，必须实现 PigRel 接口
     * // input 参数：要访问的子节点，必须是 PigRel 类型
     */
    public void visitChild(int ordinal, RelNode input) { // 定义 visitChild 方法，用于递归访问子节点
      assert ordinal == 0; // 断言检查，确保子节点序号为 0，当前实现只支持单子节点
      ((PigRel) input).implement(this); // 将子节点强制转换为 PigRel，并调用其 implement 方法，传入当前 implementor 对象
    }

    /**
     * 获取所有已生成的 Pig Latin 语句列表。
     * // getStatements 方法：返回存储的所有 Pig Latin 语句
     * // 这个方法通常用于调试或测试，查看生成的语句列表
     * 
     * @return 语句列表的副本（或直接引用，取决于实现）
     * // 返回值：返回 statements 列表，包含所有已添加的 Pig Latin 语句
     */
    public List<String> getStatements() { // 定义 getStatements 方法，用于获取所有语句
      return statements; // 直接返回 statements 列表
    }

    /**
     * 将所有语句连接成完整的 Pig Latin 脚本。
     * // getScript 方法：将语句列表组合成最终的 Pig Latin 脚本字符串
    * // 使用换行符连接所有语句，形成可执行的 Pig Latin 脚本
    * // 这是最终输出方法，生成的脚本可以直接提交给 Pig 执行
     * 
     * @return Pig Latin 脚本字符串
     * // 返回值：返回用换行符连接的完整 Pig Latin 脚本
     */
    public String getScript() { // 定义 getScript 方法，用于获取完整的 Pig Latin 脚本
      return String.join("\n", statements); // 使用换行符连接所有语句，形成完整的脚本字符串
    }
  }
}
