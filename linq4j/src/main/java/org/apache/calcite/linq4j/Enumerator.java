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
 */ // Apache许可证声明，规定了软件的使用权限和限制条件
package org.apache.calcite.linq4j; // 定义包名，表示该类属于org.apache.calcite.linq4j包，这是Calcite的LINQ4J模块

import org.checkerframework.framework.qual.Covariant; // 导入Checker框架的协变注解，用于类型系统的协变性检查

/**
 * Supports a simple iteration over a collection. // 支持对集合进行简单迭代
 *
 * <p>Analogous to LINQ's System.Collections.Enumerator. Unlike LINQ, if the // 类似于LINQ的System.Collections.Enumerator接口。与LINQ不同的是，如果
 * underlying collection has been modified it is only optional that an // 底层集合被修改，实现该接口的枚举器可以选择性地检测并抛出
 * implementation of the Enumerator interface detects it and throws a // ConcurrentModificationException异常
 * {@link java.util.ConcurrentModificationException}.
 *
 * @param <T> Element type // 泛型参数T表示集合中元素的类型
 */ // 这是一个接口定义，用于描述枚举器的基本行为
@Covariant(0) // 使用协变注解，表示泛型参数T是协变的，允许子类型转换
public interface Enumerator<T> extends AutoCloseable { // 定义Enumerator接口，继承自AutoCloseable接口，支持自动资源管理
  /**
   * Gets the current element in the collection. // 获取集合中的当前元素
   *
   * <p>After an enumerator is created or after the {@link #reset} method is // 在创建枚举器或调用reset方法后，必须先调用moveNext方法将枚举器
   * called, the {@link #moveNext} method must be called to advance the // 移动到集合的第一个元素，然后才能读取current属性的值；
   * enumerator to the first element of the collection before reading the // 否则，current的值是未定义的
   * value of the {@code current} property; otherwise, {@code current} is
   * undefined.
   *
   * <p>This method also throws {@link java.util.NoSuchElementException} if // 如果最后一次调用moveNext返回false，表示已经到达集合的末尾，
   * the last call to {@code moveNext} returned {@code false}, which indicates // 此时调用current方法会抛出NoSuchElementException异常
   * the end of the collection.
   *
   * <p>This method does not move the position of the enumerator, and // 该方法不会移动枚举器的位置，连续调用current会返回相同的对象，
   * consecutive calls to {@code current} return the same object until either // 直到调用moveNext或reset方法为止
   * {@code moveNext} or {@code reset} is called.
   *
   * <p>An enumerator remains valid as long as the collection remains // 只要集合保持不变，枚举器就保持有效。如果对集合进行了修改，
   * unchanged. If changes are made to the collection, such as adding, // 例如添加、修改或删除元素，枚举器将不可恢复地失效。
   * modifying, or deleting elements, the enumerator is irrecoverably // 下一次调用moveNext或reset可能会根据实现的选择抛出
   * invalidated. The next call to {@code moveNext} or {@code reset} may, // ConcurrentModificationException异常。如果在moveNext和current
   * at the discretion of the implementation, throw a // 之间修改了集合，current仍然返回它设置的元素，即使枚举器已经失效
   * {@link java.util.ConcurrentModificationException}. If the collection is
   * modified between {@code moveNext} and {@code current}, {@code current}
   * returns the element that it is set to, even if the enumerator is already
   * invalidated.
   *
   * @return Current element // 返回当前元素
   *
   * @throws java.util.ConcurrentModificationException if collection // 如果集合被修改，则抛出ConcurrentModificationException异常
   *          has been modified
   *
   * @throws java.util.NoSuchElementException if {@code moveToNext} // 如果moveNext未被调用、自最近一次reset后未被调用或返回false，
   *          has not been called, has not been called since the most // 则抛出NoSuchElementException异常
   *          recent call to {@code reset}, or returned false
   */ // 这是一个抽象方法，用于获取当前元素
  T current(); // 返回当前元素，类型为泛型T

  /**
   * Advances the enumerator to the next element of the collection. // 将枚举器移动到集合的下一个元素
   *
   * <p>After an enumerator is created or after the {@code reset} method is // 在创建枚举器或调用reset方法后，枚举器位于集合的第一个元素之前，
   * called, an enumerator is positioned before the first element of the // 第一次调用moveNext方法会将枚举器移动到集合的第一个元素上
   * collection, and the first call to the {@code moveNext} method moves the
   * enumerator over the first element of the collection.
   *
   * <p>If {@code moveNext} passes the end of the collection, the enumerator // 如果moveNext越过了集合的末尾，枚举器将位于集合的最后一个元素之后，
   * is positioned after the last element in the collection and // 并且moveNext返回false。当枚举器处于此位置时，后续对moveNext的调用
   * {@code moveNext} returns {@code false}. When the enumerator is at this // 也返回false，直到调用reset方法
   * position, subsequent calls to {@code moveNext} also return {@code false}
   * until {@code #reset} is called.
   *
   * <p>An enumerator remains valid as long as the collection remains // 只要集合保持不变，枚举器就保持有效。如果对集合进行了修改，
   * unchanged. If changes are made to the collection, such as adding, // 例如添加、修改或删除元素，枚举器将不可恢复地失效。
   * modifying, or deleting elements, the enumerator is irrecoverably // 下一次调用moveNext或reset可能会根据实现的选择抛出
   * invalidated. The next call to {@code moveNext} or {@link #reset} may, // ConcurrentModificationException异常
   * at the discretion of the implementation, throw a
   * {@link java.util.ConcurrentModificationException}.
   *
   * @return {@code true} if the enumerator was successfully advanced to the // 如果枚举器成功移动到下一个元素，则返回true；如果枚举器已经
   *         next element; {@code false} if the enumerator has passed the end of // 越过了集合的末尾，则返回false
   *         the collection
   */ // 这是一个抽象方法，用于移动到下一个元素
  boolean moveNext(); // 返回布尔值，表示是否成功移动到下一个元素

  /**
   * Sets the enumerator to its initial position, which is before the first // 将枚举器设置到其初始位置，即集合的第一个元素之前
   * element in the collection.
   *
   * <p>An enumerator remains valid as long as the collection remains // 只要集合保持不变，枚举器就保持有效。如果对集合进行了修改，
   * unchanged. If changes are made to the collection, such as adding, // 例如添加、修改或删除元素，枚举器将不可恢复地失效。
   * modifying, or deleting elements, the enumerator is irrecoverably // 下一次调用moveNext或reset可能会根据实现的选择抛出
   * invalidated. The next call to {@link #moveNext} or {@code reset} may, // ConcurrentModificationException异常
   * at the discretion of the implementation, throw a
   * {@link java.util.ConcurrentModificationException}.
   *
   * <p>This method is optional; it may throw // 该方法是可选的；实现可以选择抛出UnsupportedOperationException异常
   * {@link UnsupportedOperationException}.
   *
   * <p><b>Notes to Implementers</b> // <b>实现者注意事项</b>
   *
   * <p>All calls to Reset must result in the same state for the enumerator. // 对Reset的所有调用都必须使枚举器处于相同的状态。
   * The preferred implementation is to move the enumerator to the beginning // 首选的实现是将枚举器移动到集合的开头，即第一个元素之前。
   * of the collection, before the first element. This invalidates the // 如果在枚举器创建后集合已被修改，这将使枚举器失效，
   * enumerator if the collection has been modified since the enumerator was // 这与moveNext()和current()的行为一致
   * created, which is consistent with {@link #moveNext()} and
   * {@link #current()}.
   */ // 这是一个抽象方法，用于重置枚举器到初始状态
  void reset(); // 无返回值，将枚举器重置到初始位置

  /**
   * Closes this enumerable and releases resources. // 关闭此枚举器并释放资源
   *
   * <p>This method is idempotent. Calling it multiple times has the same effect // 该方法是幂等的。多次调用它的效果与调用一次相同
   * as calling it once.
   */ // 重写AutoCloseable接口的close方法，用于资源清理
  @Override void close(); // 无返回值，关闭枚举器并释放相关资源
} // 接口定义结束