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
package org.apache.calcite.rel.core; // 定义包名，org.apache.calcite.rel.core表示这是Calcite核心关系表达式包中的核心类

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，用于表示关系表达式优化集群，包含关系表达式的公共信息
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系表达式的特征集合，如物理属性、排序等
import org.apache.calcite.rel.RelInput; // 导入RelInput类，用于从序列化数据中反序列化关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式的基础接口
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint类，用于表示关系表达式的提示信息，用于优化器指导
import org.apache.calcite.rel.metadata.RelMdUtil; // 导入RelMdUtil类，提供关系表达式元数据查询的工具方法
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据信息
import org.apache.calcite.sql.SqlKind; // 导入SqlKind枚举，用于表示SQL操作符的类型，如SELECT、EXCEPT等

import java.util.Collections; // 导入Collections工具类，提供不可变集合的创建方法
import java.util.List; // 导入List接口，用于表示有序集合，存储关系表达式的输入列表

/**
 * Relational expression that returns the rows of its first input minus any // 关系表达式，返回第一个输入的所有行，减去其他输入中匹配的行
 * matching rows from its other inputs. // 减去其他输入中匹配的行
 *
 * <p>Corresponds to the SQL {@code EXCEPT} operator. // 对应于SQL中的EXCEPT操作符，用于集合差集操作
 *
 * <p>If "all" is true, then multiset subtraction is // 如果"all"为true，则执行多重集减法运算
 * performed; otherwise, set subtraction is performed (implying no duplicates in // 否则执行集合减法运算（意味着结果中不会有重复行）
 * the results). // 结果中不会有重复行
 */
public abstract class Minus extends SetOp { // 定义抽象类Minus，继承自SetOp，表示SQL的EXCEPT操作（集合差集）

  public Minus(RelOptCluster cluster, RelTraitSet traits, List<RelHint> hints, // 构造方法，创建Minus关系表达式，参数：cluster-优化集群，traits-特征集合，hints-提示列表
      List<RelNode> inputs, boolean all) { // inputs-输入关系表达式列表，all-是否执行多重集减法（EXCEPT ALL）
    super(cluster, traits, hints, inputs, SqlKind.EXCEPT, all); // 调用父类SetOp的构造方法，传入集群、特征、提示、输入列表、操作符类型EXCEPT和all标志
  } // 构造方法结束

  protected Minus(RelOptCluster cluster, RelTraitSet traits, List<RelNode> inputs, // 受保护的构造方法，创建Minus关系表达式，参数：cluster-优化集群，traits-特征集合
      boolean all) { // inputs-输入关系表达式列表，all-是否执行多重集减法
    this(cluster, traits, Collections.emptyList(), inputs, all); // 调用完整构造方法，传入空的提示列表（Collections.emptyList()创建不可变空列表）
  } // 受保护的构造方法结束

  /**
   * Creates a Minus by parsing serialized output. // 通过解析序列化输出来创建Minus关系表达式
   */
  protected Minus(RelInput input) { // 受保护的构造方法，从RelInput对象反序列化创建Minus，参数：input-包含序列化数据的RelInput对象
    super(input); // 调用父类SetOp的构造方法，从RelInput中读取数据并初始化关系表达式
  } // 反序列化构造方法结束

  @Override public double estimateRowCount(RelMetadataQuery mq) { // 重写estimateRowCount方法，估算此Minus操作产生的行数，参数：mq-元数据查询对象
    return RelMdUtil.getMinusRowCount(mq, this); // 使用RelMdUtil工具类的getMinusRowCount方法计算Minus操作的结果行数，传入元数据查询对象和当前Minus实例
  } // 估算行数方法结束
} // Minus类结束
