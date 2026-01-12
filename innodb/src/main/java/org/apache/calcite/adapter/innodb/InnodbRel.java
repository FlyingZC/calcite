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
package org.apache.calcite.adapter.innodb; // 指定当前类所在的包路径，org.apache.calcite.adapter.innodb表示这是Calcite框架中InnoDB适配器包

import org.apache.calcite.plan.Convention; // 导入Calcite计划器中的约定类，用于定义关系代数操作的调用约定
import org.apache.calcite.plan.RelOptTable; // 导入Calcite中的优化表类，用于表示可优化的表对象
import org.apache.calcite.rel.RelNode; // 导入Calcite中的关系节点接口，是所有关系表达式的基础接口

import java.util.LinkedHashMap; // 导入Java集合框架中的LinkedHashMap类，用于维护插入顺序的哈希映射
import java.util.Map; // 导入Java集合框架中的Map接口，用于存储键值对映射关系

/**
 * Relational expression that uses InnoDB calling convention. // 使用InnoDB调用约定的关系表达式接口，这是InnoDB适配器中所有关系节点的基接口
 */ // 该接口定义了InnoDB适配器中关系表达式必须实现的方法，用于将Calcite的关系代数树转换为InnoDB的直接调用查询
public interface InnodbRel extends RelNode { // 定义InnodbRel接口，继承自RelNode接口，表示InnoDB适配器中的关系节点
  void implement(Implementor implementor); // 声明实现方法，用于将当前关系节点转换为InnoDB的直接调用查询，参数implementor是实现器对象

  /** Calling convention for relational operations that occur in InnoDB. */ // 注释：定义InnoDB中关系操作的调用约定
  Convention CONVENTION = new Convention.Impl("INNODB", InnodbRel.class); // 创建并初始化InnoDB调用约定常量，名称为"INNODB"，关联类为InnodbRel.class

  /** Callback for the implementation process that converts a tree of
   * {@link InnodbRel} nodes into an InnoDB direct call query. */ // 注释：实现过程的回调类，用于将InnodbRel节点树转换为InnoDB直接调用查询
  class Implementor { // 定义实现器内部类，负责收集和转换InnoDB查询的各种信息
    final Map<String, String> selectFields = new LinkedHashMap<>(); // 使用LinkedHashMap存储SELECT字段映射，键为字段名，值为字段表达式，保持插入顺序
    IndexCondition indexCondition = IndexCondition.EMPTY_CONDITION; // 初始化索引条件为空条件，用于存储WHERE子句中的索引过滤条件
    boolean ascOrder = true; // 默认升序排序标志，true表示升序，false表示降序，用于ORDER BY子句

    RelOptTable table; // 声明优化表对象，用于存储当前操作的表信息，包含表的元数据和统计信息
    InnodbTable innodbTable; // 声明InnoDB表对象，用于存储InnoDB特定的表信息和操作接口

    public void addSelectFields(Map<String, String> fields) { // 添加SELECT字段的方法，将字段映射添加到selectFields集合中
      if (fields != null) { // 检查传入的字段映射是否为空，避免空指针异常
        selectFields.putAll(fields); // 将传入的字段映射全部添加到selectFields中，使用putAll方法批量添加
      } // 结束if条件判断
    } // 结束addSelectFields方法

    public void setIndexCondition(IndexCondition indexCondition) { // 设置索引条件的方法，用于更新WHERE子句的索引过滤条件
      this.indexCondition = indexCondition; // 将传入的索引条件赋值给当前对象的indexCondition成员变量
    } // 结束setIndexCondition方法

    public void setAscOrder(boolean ascOrder) { // 设置排序顺序的方法，用于更新ORDER BY子句的排序方向
      this.ascOrder = ascOrder; // 将传入的排序方向赋值给当前对象的ascOrder成员变量
    } // 结束setAscOrder方法

    public void visitChild(int ordinal, RelNode input) { // 访问子节点的方法，用于递归遍历关系表达式树，ordinal表示子节点的序号，input是子节点对象
      assert ordinal == 0; // 断言子节点序号必须为0，因为InnoDB适配器通常只处理单个子节点，如果序号不为0则抛出断言错误
      ((InnodbRel) input).implement(this); // 将子节点强制转换为InnodbRel类型，并调用其implement方法，传入当前实现器对象，实现递归转换
    } // 结束visitChild方法
  } // 结束Implementor内部类
} // 结束InnodbRel接口
