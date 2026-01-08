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
 */ // Apache许可证声明，定义代码的使用权限和限制
package org.apache.calcite.rel; // 声明包名，表示这个类属于org.apache.calcite.rel包

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，用于表示关系代数表达式的集群信息，包含类型系统、优化器上下文等
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系代数表达式的特征集合，如排序、分区等物理属性
import org.apache.calcite.runtime.FlatLists; // 导入FlatLists工具类，用于创建不可变的扁平列表，提高性能

import java.util.List; // 导入List接口，用于表示有序的元素集合

/**
 * Abstract base class for relational expressions with a two inputs.
 * 具有两个输入的关系表达式的抽象基类
 *
 * <p>It is not required that two-input relational expressions use this
 * class as a base class. However, default implementations of methods make life
 * easier.
 * 并不强制要求所有双输入关系表达式都继承此类，但使用它可以获得默认的方法实现，简化开发
 */ // 类的JavaDoc注释，说明BiRel是双输入关系表达式的抽象基类
public abstract class BiRel extends AbstractRelNode { // 定义BiRel抽象类，继承自AbstractRelNode，表示具有两个输入的关系节点
  protected RelNode left; // 左子节点，表示关系表达式的第一个输入，类型为RelNode（关系节点接口）
  protected RelNode right; // 右子节点，表示关系表达式的第二个输入，类型为RelNode（关系节点接口）

  protected BiRel( // BiRel构造方法，用于创建双输入关系表达式实例，protected修饰符表示只能被子类访问
      RelOptCluster cluster, // 参数：关系集群，包含类型系统、优化器上下文等共享信息
      RelTraitSet traitSet, // 参数：特征集合，定义关系表达式的物理属性（如排序、分区等）
      RelNode left, // 参数：左子节点，第一个输入的关系表达式
      RelNode right) { // 参数：右子节点，第二个输入的关系表达式
    super(cluster, traitSet); // 调用父类AbstractRelNode的构造方法，初始化集群和特征集合
    this.left = left; // 将传入的左子节点赋值给成员变量left
    this.right = right; // 将传入的右子节点赋值给成员变量right
  } // 构造方法结束

  @Override public void childrenAccept(RelVisitor visitor) { // 重写childrenAccept方法，使用RelVisitor访问者模式遍历子节点
    visitor.visit(left, 0, this); // 访问左子节点，参数：left为要访问的节点，0为子节点序号（左子节点为0），this为父节点
    visitor.visit(right, 1, this); // 访问右子节点，参数：right为要访问的节点，1为子节点序号（右子节点为1），this为父节点
  } // childrenAccept方法结束

  @Override public List<RelNode> getInputs() { // 重写getInputs方法，返回所有输入节点的列表
    return FlatLists.of(left, right); // 使用FlatLists工具类创建包含左右两个子节点的不可变列表，性能优于ArrayList
  } // getInputs方法结束

  public RelNode getLeft() { // 获取左子节点的方法
    return left; // 返回左子节点成员变量
  } // getLeft方法结束

  public RelNode getRight() { // 获取右子节点的方法
    return right; // 返回右子节点成员变量
  } // getRight方法结束

  @Override public void replaceInput( // 重写replaceInput方法，用于替换指定位置的输入节点
      int ordinalInParent, // 参数：要替换的子节点在父节点中的序号（0表示左子节点，1表示右子节点）
      RelNode p) { // 参数：新的关系节点，用于替换原来的子节点
    switch (ordinalInParent) { // 根据子节点序号进行分支判断
    case 0: // 当序号为0时，表示要替换左子节点
      this.left = p; // 将左子节点替换为新的节点p
      break; // 跳出switch语句
    case 1: // 当序号为1时，表示要替换右子节点
      this.right = p; // 将右子节点替换为新的节点p
      break; // 跳出switch语句
    default: // 当序号既不是0也不是1时，表示序号越界
      throw new IndexOutOfBoundsException("Input " + ordinalInParent); // 抛出索引越界异常，提示输入的序号无效
    } // switch语句结束
    recomputeDigest(); // 重新计算关系节点的摘要信息（digest），用于缓存和等价性判断
  } // replaceInput方法结束

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写explainTerms方法，用于生成关系表达式的解释信息（用于查询计划的可视化）
    return super.explainTerms(pw) // 调用父类的explainTerms方法，获取基础的RelWriter对象，保持链式调用
        .input("left", left) // 将左子节点添加到解释信息中，标签为"left"
        .input("right", right); // 将右子节点添加到解释信息中，标签为"right"
  } // explainTerms方法结束
} // BiRel类定义结束
