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
package org.apache.calcite.linq4j;

/**
 * 委托枚举器：一个简单的枚举器实现，将所有方法调用委托给传入的内部枚举器对象
 * 这个类实现了装饰器模式，允许在不修改原始枚举器的情况下扩展或修改其行为
 * 典型应用场景包括：添加日志记录、性能监控、数据转换、过滤等横切关注点
 * 
 * 工作原理：
 * 1. 持有一个内部枚举器对象（delegate）作为实际的数据源
 * 2. 所有接口方法（current、moveNext、reset、close）都直接调用内部枚举器的对应方法
 * 3. 子类可以通过重写这些方法来添加额外的逻辑，同时保持委托行为
 *
 * 在Calcite中的用途：
 * - 作为其他复杂枚举器（如TransformingEnumerator、FilteringEnumerator）的基类
 * - 提供一种灵活的方式来组合多个枚举器
 * - 支持枚举器链式调用，实现复杂的数据处理流程
 *
 * @param <T> 枚举器返回的元素类型，该类型从委托的枚举器传递而来
 */
public class DelegatingEnumerator<T> implements Enumerator<T> {
  // 委托的内部枚举器对象，所有实际操作都转发到这个对象
  // 使用protected修饰允许子类直接访问和操作委托对象
  // 使用final修饰确保在构造函数初始化后不可再被修改，保证线程安全
  protected final Enumerator<T> delegate;

  /**
   * 构造函数：创建一个委托枚举器实例
   * 
   * @param delegate 要委托的内部枚举器对象，不能为null
   *                 这个枚举器是实际的数据源，所有操作都会转发给它
   */
  public DelegatingEnumerator(Enumerator<T> delegate) {
    this.delegate = delegate; // 将传入的枚举器对象保存到成员变量中
  }

  /**
   * 获取当前元素：返回枚举器当前位置的元素
   * 
   * 注意：必须在moveNext()返回true后调用此方法，否则行为未定义
   * 
   * @return 当前位置的元素，类型为泛型T
   */
  @Override public T current() {
    return delegate.current(); // 直接委托给内部枚举器的current()方法
  }

  /**
   * 移动到下一个元素：将枚举器位置向前移动一个位置
   * 
   * 工作流程：
   * 1. 调用内部枚举器的moveNext()方法
   * 2. 如果还有下一个元素，返回true，此时可以通过current()获取该元素
   * 3. 如果已经到达末尾，返回false，此时current()的行为未定义
   * 
   * @return 如果成功移动到下一个元素返回true，否则返回false
   */
  @Override public boolean moveNext() {
    return delegate.moveNext(); // 直接委托给内部枚举器的moveNext()方法
  }

  /**
   * 重置枚举器：将枚举器位置重置到初始状态
   * 
   * 重置后：
   * - 下一次调用moveNext()将返回第一个元素（如果存在）
   * - 可以重新开始遍历整个序列
   * 
   * 注意：并非所有枚举器都支持reset操作，具体取决于实现
   */
  @Override public void reset() {
    delegate.reset(); // 直接委托给内部枚举器的reset()方法
  }

  /**
   * 关闭枚举器：释放枚举器占用的资源
   * 
   * 资源释放可能包括：
   * - 关闭底层的数据库连接
   * - 释放文件句柄
   * - 清理内存缓冲区
   * - 取消正在进行的异步操作
   * 
   * 最佳实践：使用try-finally或try-with-resources确保枚举器被正确关闭
   */
  @Override public void close() {
    delegate.close(); // 直接委托给内部枚举器的close()方法
  }
}
