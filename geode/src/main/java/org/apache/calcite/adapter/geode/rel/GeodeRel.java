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
package org.apache.calcite.adapter.geode.rel; // Geode适配器的rel包，包含Geode相关的关系表达式实现

import org.apache.calcite.plan.Convention; // 引入Convention类，用于定义调用约定，表示关系表达式的执行约定
import org.apache.calcite.plan.RelOptTable; // 引入RelOptTable类，表示优化过程中的表对象
import org.apache.calcite.rel.RelNode; // 引入RelNode接口，关系表达式的基础接口

import java.util.ArrayList; // 引入ArrayList集合类，用于存储有序的元素列表
import java.util.LinkedHashMap; // 引入LinkedHashMap类，用于保持插入顺序的键值对映射
import java.util.List; // 引入List接口，表示有序集合
import java.util.Map; // 引入Map接口，表示键值对映射

/**
 * Relational expression that uses Geode calling convention. // 使用Geode调用约定的关系表达式接口
 * 
 * GeodeRel接口定义了所有Geode适配器关系表达式的通用行为，它是Calcite关系表达式树中
 * 与Apache Geode数据存储交互的节点的基础接口。Geode是Apache的一个内存数据网格平台，
 * 提供了高性能的分布式数据存储和处理能力。这个接口确保所有Geode相关的关系表达式
 * 都遵循相同的约定和实现模式。
 * 
 * 主要功能：
 * 1. 定义GEODE调用约定，标识哪些关系表达式应该在Geode中执行
 * 2. 提供implement方法，允许关系表达式将自身转换为Geode OQL查询
 * 3. 包含GeodeImplementContext内部类，用于在转换过程中收集和传递上下文信息
 * 
 * 设计模式：
 * - 使用访问者模式，通过implement方法让每个GeodeRel节点访问并修改上下文
 * - 使用约定模式，通过CONVENTION常量定义Geode特定的执行约定
 * 
 * 在Calcite优化器中的作用：
 * - 当优化器遇到GEODE约定的节点时，会使用Geode特定的规则进行优化
 * - 最终通过implement方法将整个关系表达式树转换为可执行的Geode OQL查询
 */
public interface GeodeRel extends RelNode { // 定义GeodeRel接口，继承自RelNode基础接口

  /**
   * Calling convention for relational operations that occur in Geode. // 定义Geode中执行的关系操作的调用约定
   * 
   * CONVENTION是一个静态常量，使用Convention.Impl创建，它标识了关系表达式应该在
   * Apache Geode数据存储中执行。Calcite使用调用约定来决定如何实现关系表达式，
   * 不同的调用约定对应不同的数据源或执行引擎。
   * 
   * 约定的作用：
   * - 在优化过程中，优化器会根据约定选择合适的规则集
   - 约定决定了关系表达式如何被转换为物理执行计划
   - GEODE约定表示该表达式应该转换为Geode OQL查询
   * 
   * 参数说明：
   * - "GEODE": 约定的名称，用于标识和调试
   * - GeodeRel.class: 约定关联的接口类型，确保只有GeodeRel节点使用此约定
   * 
   * 使用场景：
   * - 在创建Geode适配器的关系表达式时，会设置此约定
   * - 优化器通过检查约定来决定应用哪些优化规则
   * - 实现阶段根据约定选择正确的实现策略
   */
  Convention CONVENTION = new Convention.Impl("GEODE", GeodeRel.class); // 创建并初始化GEODE调用约定常量

  /**
   * Callback for the implementation process that collects the context from the // 实现过程的回调方法，从GeodeRel收集上下文
   * {@link GeodeRel} required to convert the relational tree into physical such. // 用于将关系树转换为物理执行计划
   *
   * @param geodeImplementContext Context class that collects the feedback from the // 上下文类，收集回调方法的反馈
   *                              call back method calls // 来自回调方法调用的反馈
   * 
   * implement方法是GeodeRel接口的核心方法，它实现了访问者模式中的访问者接口。
   * 当Calcite需要将关系表达式树转换为Geode OQL查询时，会调用此方法。
   * 
   * 方法功能：
   * - 允许每个GeodeRel节点将自身的信息（如字段、过滤条件等）添加到上下文中
   * - 递归地访问子节点，构建完整的OQL查询
   * - 收集查询的所有组件：SELECT字段、WHERE条件、ORDER BY、GROUP BY等
   * 
   * 执行流程：
   * 1. 当前节点根据自身类型（如投影、过滤、扫描等）提取相关信息
   * 2. 将提取的信息添加到geodeImplementContext中
   * 3. 调用子节点的implement方法，继续递归处理
   * 4. 最终上下文包含构建完整OQL查询所需的所有信息
   * 
   * 实现示例：
   * - GeodeTableScan会添加表名和WHERE条件
   * - GeodeProject会添加SELECT字段
   * - GeodeFilter会添加额外的过滤条件
   * - GeodeSort会添加ORDER BY和LIMIT
   * - GeodeAggregate会添加GROUP BY和聚合函数
   */
  void implement(GeodeImplementContext geodeImplementContext); // 声明implement方法，接收GeodeImplementContext参数

  /**
   * Shared context used by the {@link GeodeRel} relations. // GeodeRel关系表达式使用的共享上下文
   *
   * <p>Callback context class for the implementation process that converts a // 实现过程的回调上下文类，用于转换
   * tree of {@code GeodeRel} nodes into an OQL query. // GeodeRel节点树为OQL查询
   * 
   * GeodeImplementContext是一个内部类，用于在关系表达式树转换为OQL查询的过程中
   * 收集和维护所有必要的信息。它作为访问者模式中的访问者状态，在遍历关系表达式树时
   * 逐步构建最终的OQL查询。
   * 
   * 核心功能：
   * - 收集SELECT子句中的字段信息
   * - 收集WHERE子句中的过滤条件
   * - 收集ORDER BY子句中的排序字段
   * - 收集GROUP BY子句中的分组字段
   * - 收集聚合函数信息
   * - 存储LIMIT值
   * - 维护表信息和GeodeTable元数据
   * 
   * 数据结构选择：
   * - selectFields使用LinkedHashMap：保持字段顺序，避免重复
   * - whereClause使用ArrayList：保持条件顺序，允许重复条件
   * - orderByFields使用ArrayList：保持排序顺序
   * - groupByFields使用ArrayList：保持分组顺序
   * - oqlAggregateFunctions使用LinkedHashMap：保持函数顺序，避免重复
   * 
   * 工作原理：
   * 1. 从根节点开始遍历关系表达式树
   * 2. 每个节点调用implement方法，将自身信息添加到上下文
   * 3. 上下文逐步积累信息，直到遍历完成
   * 4. 最终可以基于上下文信息构建完整的OQL查询字符串
   * 
   * OQL查询示例：
   * SELECT selectFields FROM table WHERE whereClause
   * GROUP BY groupByFields ORDER BY orderByFields LIMIT limitValue
   */
  class GeodeImplementContext { // 定义GeodeImplementContext内部类，用于收集实现上下文
    final Map<String, String> selectFields = new LinkedHashMap<>(); // selectFields：存储SELECT子句中的字段，key为字段名，value为字段表达式，使用LinkedHashMap保持插入顺序

    final List<String> whereClause = new ArrayList<>(); // whereClause：存储WHERE子句中的过滤条件列表，使用ArrayList保持条件顺序

    final List<String> orderByFields = new ArrayList<>(); // orderByFields：存储ORDER BY子句中的排序字段列表，使用ArrayList保持排序顺序

    final List<String> groupByFields = new ArrayList<>(); // groupByFields：存储GROUP BY子句中的分组字段列表，使用ArrayList保持分组顺序

    final Map<String, String> oqlAggregateFunctions = new LinkedHashMap<>(); // oqlAggregateFunctions：存储OQL聚合函数，key为函数别名，value为函数表达式，使用LinkedHashMap保持顺序

    Long limitValue; // limitValue：存储LIMIT子句的值，表示返回结果的最大行数，可为null表示无限制

    RelOptTable table; // table：存储RelOptTable对象，表示Calcite优化器中的表信息，包含表名、schema等元数据

    GeodeTable geodeTable; // geodeTable：存储GeodeTable对象，表示Geode特定的表信息，包含region名称、字段映射等

    /**
     * Adds new projected fields. // 添加新的投影字段
     *
     * @param fields New fields to be projected from a query // 要从查询中投影的新字段，Map的key为字段别名，value为字段表达式
     * 
     * addSelectFields方法用于将SELECT子句中的字段添加到上下文中。
     * 
     * 方法功能：
     * - 将新的字段映射添加到selectFields中
     * - 使用putAll方法批量添加，避免多次调用
     * - 如果fields为null，则不执行任何操作，避免空指针异常
     * 
     * 参数说明：
     * - fields: Map<String, String>类型，key是字段别名（在SELECT中显示的名称），
     *   value是字段表达式（可能是字段名、计算表达式等）
     * 
     * 使用场景：
     * - GeodeProject节点调用此方法添加投影字段
     * - GeodeAggregate节点调用此方法添加聚合函数结果字段
     * - 可以多次调用，逐步添加字段
     * 
     * 示例：
     * - fields = {"name": "e.name", "salary": "e.salary * 1.1"}
     * - 添加后，selectFields包含name和salary两个字段
     * 
     * 注意事项：
     * - 使用LinkedHashMap，后添加的字段会覆盖同名的已存在字段
     * - 保持字段顺序对最终查询的可读性很重要
     */
    public void addSelectFields(Map<String, String> fields) { // 定义addSelectFields方法，接收字段映射参数
      if (fields != null) { // 检查fields参数是否为null，避免空指针异常
        selectFields.putAll(fields); // 将fields中的所有键值对添加到selectFields中，使用putAll批量添加
      }
    }

    /**
     * Adds new  restricted predicates. // 添加新的限制谓词（过滤条件）
     *
     * @param predicates New predicates to be applied to the query // 要应用到查询的新谓词列表，每个谓词是一个字符串表达式
     * 
     * addPredicates方法用于将WHERE子句中的过滤条件添加到上下文中。
     * 
     * 方法功能：
     * - 将新的过滤条件添加到whereClause列表中
     * - 使用addAll方法批量添加多个条件
     * - 如果predicates为null，则不执行任何操作
     * 
     * 参数说明：
     * - predicates: List<String>类型，每个字符串代表一个过滤条件表达式
     *   例如："age > 18", "name = 'John'", "salary BETWEEN 5000 AND 10000"
     * 
     * 使用场景：
     * - GeodeFilter节点调用此方法添加过滤条件
     * - GeodeTableScan节点调用此方法添加下推的过滤条件
     * - 可以多次调用，逐步添加多个条件
     * 
     * 条件组合：
     * - 多个条件最终会用AND连接
     * - 保持条件顺序对查询性能可能有影响
     * 
     * 示例：
     * - predicates = ["age > 18", "status = 'active'"]
     * - 添加后，whereClause包含两个条件
     * - 最终WHERE子句：WHERE age > 18 AND status = 'active'
     */
    public void addPredicates(List<String> predicates) { // 定义addPredicates方法，接收谓词列表参数
      if (predicates != null) { // 检查predicates参数是否为null，避免空指针异常
        whereClause.addAll(predicates); // 将predicates中的所有条件添加到whereClause列表中，使用addAll批量添加
      }
    }

    public void addOrderByFields(List<String> orderByFieldLists) { // 定义addOrderByFields方法，接收排序字段列表参数
      orderByFields.addAll(orderByFieldLists); // 将orderByFieldLists中的所有排序字段添加到orderByFields列表中
    }

    public void setLimit(long limit) { // 定义setLimit方法，接收limit参数
      limitValue = limit; // 将limit值赋给limitValue成员变量，设置查询的返回行数限制
    }

    public void addGroupBy(List<String> groupByFields) { // 定义addGroupBy方法，接收分组字段列表参数
      this.groupByFields.addAll(groupByFields); // 将groupByFields参数中的所有分组字段添加到成员变量groupByFields中
    }

    public void addAggregateFunctions(Map<String, String> oqlAggregateFunctions) { // 定义addAggregateFunctions方法，接收聚合函数映射参数
      this.oqlAggregateFunctions.putAll(oqlAggregateFunctions); // 将oqlAggregateFunctions参数中的所有聚合函数添加到成员变量oqlAggregateFunctions中
    }

    void visitChild(RelNode input) { // 定义visitChild方法，接收RelNode参数，用于访问子节点
      ((GeodeRel) input).implement(this); // 将input强制转换为GeodeRel类型，并调用其implement方法，传入当前上下文对象，实现递归遍历
    }

    @Override public String toString() { // 重写toString方法，用于返回上下文对象的字符串表示
      return "GeodeImplementContext{" // 返回字符串，以类名开始
          + "selectFields=" + selectFields // 添加selectFields的字符串表示
          + ", whereClause=" + whereClause // 添加whereClause的字符串表示
          + ", orderByFields=" + orderByFields // 添加orderByFields的字符串表示
          + ", limitValue='" + limitValue + '\'' // 添加limitValue的字符串表示，用单引号包围
          + ", groupByFields=" + groupByFields // 添加groupByFields的字符串表示
          + ", table=" + table // 添加table的字符串表示
          + ", geodeTable=" + geodeTable // 添加geodeTable的字符串表示
          + '}'; // 添加结束大括号，完成字符串构建
    }
  }
}
