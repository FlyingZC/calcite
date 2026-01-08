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
// 声明当前类所在的包，位于org.apache.calcite.interpreter包下，这是Calcite解释器模块的核心包
package org.apache.calcite.interpreter;

// 导入SingleRel类，这是Calcite中表示只有一个输入的关系表达式基类
import org.apache.calcite.rel.SingleRel;

/**
 * An interpreter that takes expects one incoming source relational expression.
 * 这是一个抽象的解释器节点类，专门用于处理只有一个输入源的关系表达式
 * 它是Calcite解释器模式中的基础组件，为所有单输入节点的解释器提供通用功能
 * 解释器模式允许Calcite不生成代码而是直接解释执行关系代数树
 * 
 * 这个类实现了Node接口，表示它是一个可执行的解释器节点
 * 它是抽象类，需要子类实现具体的执行逻辑
 *
 * @param <T> Type of relational expression - 泛型参数T表示关系表达式的具体类型，必须是SingleRel的子类
 * 例如：Filter、Project、Sort等都是SingleRel的子类
 */
// 定义抽象类AbstractSingleNode，继承自SingleRel的关系表达式类型T，并实现Node接口
abstract class AbstractSingleNode<T extends SingleRel> implements Node {
  // source：数据源对象，表示当前节点的输入数据来源
  // 它是Source接口的实例，负责从上游节点读取数据行
  // 在解释器执行过程中，source会迭代地提供输入数据
  // protected修饰符允许子类直接访问这个数据源
  protected final Source source;
  
  // sink：数据接收器对象，表示当前节点的输出目标
  // 它是Sink接口的实例，负责将处理后的数据行发送到下游节点
  // 在解释器执行过程中，sink会接收当前节点处理后的结果
  // protected修饰符允许子类直接访问这个数据接收器
  protected final Sink sink;
  
  // rel：当前节点对应的关系表达式对象
  // 它是泛型类型T，必须是SingleRel的子类
  // 这个对象包含了当前节点的元数据信息，如字段信息、谓词条件等
  // 在执行过程中，子类可能需要访问rel来获取执行所需的配置信息
  // protected修饰符允许子类直接访问这个关系表达式
  protected final T rel;

  // 构造方法：初始化单节点解释器
  // 参数compiler：编译器对象，负责创建source和sink等执行组件
  // 参数rel：当前节点对应的关系表达式，类型为泛型T（SingleRel的子类）
  // 构造方法负责建立当前节点与上下游节点的连接
  AbstractSingleNode(Compiler compiler, T rel) {
    // 保存当前节点对应的关系表达式对象，供后续执行时使用
    this.rel = rel;
    
    // 通过编译器创建当前节点的输入数据源
    // compiler.source(rel, 0)：第二个参数0表示从rel的第0个输入（即唯一输入）创建source
    // source会负责从上游节点读取数据行，并封装成可迭代的Row对象
    this.source = compiler.source(rel, 0);
    
    // 通过编译器创建当前节点的输出数据接收器
    // compiler.sink(rel)：为当前关系表达式创建一个sink
    // sink会负责将当前节点处理后的数据行发送到下游节点
    this.sink = compiler.sink(rel);
  }

  // 实现Node接口的close方法，用于释放资源
  // 当解释器执行完毕或遇到错误时，需要调用此方法来清理资源
  // 这是一个标准的资源管理模式，确保不会发生资源泄漏
  @Override public void close() {
    // 关闭输入数据源，释放相关资源
    // source.close()会关闭上游连接、释放内存等
    // 这是资源清理的关键步骤，确保解释器执行后系统状态正确
    source.close();
  }
}
