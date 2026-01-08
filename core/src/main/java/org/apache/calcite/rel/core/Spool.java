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
package org.apache.calcite.rel.core; // 包声明：定义Spool类所在的包路径，org.apache.calcite.rel.core是Calcite核心关系表达式包

import org.apache.calcite.linq4j.function.Experimental; // 导入Experimental注解，用于标记实验性API，可能随时变更
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式优化集群，包含优化器上下文信息
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式特征集合，如物理实现特征
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，是所有关系表达式的基接口
import org.apache.calcite.rel.RelWriter; // 导入RelWriter接口，用于将关系表达式写入输出流进行解释
import org.apache.calcite.rel.SingleRel; // 导入SingleRel类，表示只有一个输入的关系表达式基类

import java.util.List; // 导入List接口，用于处理输入关系表达式列表

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * Relational expression that iterates over its input and, in addition to
 * returning its results, will forward them into other consumers.
 * 关系表达式，迭代其输入并除了返回结果外，还会将结果转发给其他消费者
 *
 * <p>NOTE: The current API is experimental and subject to change without
 * notice.
 * 注意：当前API是实验性的，可能会在没有通知的情况下更改
 */
@Experimental // 使用Experimental注解标记这是一个实验性API，使用时需谨慎
public abstract class Spool extends SingleRel { // Spool类：抽象的Spool关系表达式类，继承自SingleRel表示只有一个输入

  /**
   * Enumeration representing spool read / write type.
   * 枚举类型，表示Spool的读/写类型
   */
  public enum Type { // Type枚举：定义Spool的两种读/写策略类型
    EAGER, // EAGER类型：急切型，表示立即消费或转发所有元素
    LAZY // LAZY类型：惰性型，表示按需消费或转发元素
  }

  /**
   * How the spool consumes elements from its input.
   * Spool如何从其输入中消费元素
   *
   * <ul>
   * <li>EAGER: the spool consumes the elements from its input at once at the
   *     initial request;
   *     EAGER：Spool在初始请求时一次性消费输入中的所有元素
   * <li>LAZY: the spool consumes the elements from its input one by one by
   *     request.
   *     LAZY：Spool在请求时逐个消费输入中的元素
   * </ul>
   */
  public final Type readType; // readType成员变量：表示Spool从输入读取数据的策略类型，final修饰不可变

  /**
   * How the spool forwards elements to consumers.
   * Spool如何将元素转发给消费者
   *
   * <ul>
   * <li>EAGER: the spool forwards each element as soon as it returns it;
   *     EAGER：Spool在返回每个元素时立即转发该元素
   * <li>LAZY: the spool forwards all elements at once when it is done returning
   *     all of them.
   *     LAZY：Spool在返回所有元素完成后一次性转发所有元素
   * </ul>
   */
  public final Type writeType; // writeType成员变量：表示Spool向消费者写入数据的策略类型，final修饰不可变

  //~ Constructors -----------------------------------------------------------
  // 构造方法分隔符，用于代码组织

  /** Creates a Spool.
   * 创建一个Spool关系表达式实例
   */
  protected Spool(RelOptCluster cluster, RelTraitSet traitSet, RelNode input, // 构造方法：初始化Spool对象，接收集群、特征集、输入和读/写类型
      Type readType, Type writeType) { // readType参数：读取类型，writeType参数：写入类型
    super(cluster, traitSet, input); // 调用父类SingleRel的构造方法，初始化集群、特征集和输入关系表达式
    this.readType = requireNonNull(readType, "readType"); // 使用requireNonNull校验readType非空，如果为null抛出NullPointerException
    this.writeType = requireNonNull(writeType, "writeType"); // 使用requireNonNull校验writeType非空，如果为null抛出NullPointerException
  }

  @Override public final RelNode copy(RelTraitSet traitSet, // 重写copy方法：final修饰表示不可被子类重写，用于复制关系表达式
      List<RelNode> inputs) { // inputs参数：输入关系表达式列表
    return copy(traitSet, sole(inputs), readType, writeType); // 调用抽象的copy方法，使用sole方法从列表中提取唯一输入，并保持读/写类型不变
  }

  protected abstract Spool copy(RelTraitSet traitSet, RelNode input, // 抽象方法：子类必须实现，用于创建Spool的副本，可以修改特征集、输入和读/写类型
      Type readType, Type writeType); // traitSet参数：新的特征集，input参数：新的输入，readType/writeType参数：新的读/写类型

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写explainTerms方法：用于输出关系表达式的详细说明信息
    return super.explainTerms(pw) // 调用父类的explainTerms方法，先输出父类的说明信息
        .item("readType", readType) // 添加readType项到说明中，显示读取类型
        .item("writeType", writeType); // 添加writeType项到说明中，显示写入类型
  }
} // Spool类结束
