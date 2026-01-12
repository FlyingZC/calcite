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
 */ // Apache许可证声明，说明代码的开源许可协议和版权信息
package org.apache.calcite.adapter.spark; // 定义包名，表示这个类属于Calcite的Spark适配器包

import org.apache.calcite.adapter.enumerable.JavaRelImplementor; // 导入JavaRelImplementor类，用于实现Java关系表达式
import org.apache.calcite.adapter.enumerable.PhysType; // 导入PhysType类，表示物理类型信息
import org.apache.calcite.linq4j.tree.BlockStatement; // 导入BlockStatement类，表示代码块语句
import org.apache.calcite.plan.Convention; // 导入Convention类，表示调用约定
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式节点
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类，用于构建行表达式

/**
 * Relational expression that uses Spark calling convention.
 */ // 类注释：使用Spark调用约定的关系表达式接口
public interface SparkRel extends RelNode { // 定义SparkRel接口，继承自RelNode，表示Spark关系表达式
  Result implementSpark(Implementor implementor); // 声明implementSpark方法，用于实现Spark关系表达式，返回Result对象

  /** Calling convention for relational operations that occur in Spark. */ // 注释：Spark中发生的关系操作的调用约定
  Convention CONVENTION = new Convention.Impl("SPARK", SparkRel.class); // 定义SPARK调用约定常量，使用"SPARK"标识符，关联到SparkRel类

  /** Extension to {@link JavaRelImplementor} that can handle Spark relational
   * expressions. */ // 注释：JavaRelImplementor的扩展，可以处理Spark关系表达式
  abstract class Implementor extends JavaRelImplementor { // 定义Implementor抽象类，继承自JavaRelImplementor，用于实现Spark关系表达式
    protected Implementor(RexBuilder rexBuilder) { // 定义构造方法，接收RexBuilder参数
      super(rexBuilder); // 调用父类构造方法，传入rexBuilder参数
    } // 构造方法结束

    abstract Result result(PhysType physType, BlockStatement blockStatement); // 声明result抽象方法，用于创建Result对象，接收物理类型和代码块参数

    abstract Result visitInput(SparkRel parent, int ordinal, SparkRel input); // 声明visitInput抽象方法，用于访问输入节点，接收父节点、序号和输入节点参数
  } // Implementor类结束

  /** Result of generating Java code to implement a Spark relational
   * expression. */ // 注释：生成Java代码以实现Spark关系表达式的结果
  class Result { // 定义Result类，用于封装代码生成的结果
    public final BlockStatement block; // 定义公共final变量block，表示生成的代码块语句
    public final PhysType physType; // 定义公共final变量physType，表示物理类型信息

    public Result(PhysType physType, BlockStatement block) { // 定义构造方法，接收物理类型和代码块参数
      this.physType = physType; // 将传入的physType赋值给成员变量physType
      this.block = block; // 将传入的block赋值给成员变量block
    } // 构造方法结束
  } // Result类结束
} // SparkRel接口结束
