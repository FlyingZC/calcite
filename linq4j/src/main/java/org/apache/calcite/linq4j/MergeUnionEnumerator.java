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
package org.apache.calcite.linq4j; // 包声明，属于org.apache.calcite.linq4j包，这是Calcite中LINQ(Language Integrated Query)4J的核心包

import org.apache.calcite.linq4j.function.EqualityComparer; // 导入EqualityComparer接口，用于自定义对象相等性比较
import org.apache.calcite.linq4j.function.Function1; // 导入Function1接口，表示接受一个参数并返回一个结果的函数

import org.checkerframework.checker.initialization.qual.UnknownInitialization; // CheckerFramework注解，表示对象可能未完全初始化
import org.checkerframework.checker.nullness.qual.Nullable; // CheckerFramework注解，表示字段或返回值可能为null
import org.checkerframework.checker.nullness.qual.RequiresNonNull; // CheckerFramework注解，表示方法调用前某些字段必须非null

import java.util.Comparator; // 导入Comparator接口，用于对象比较
import java.util.HashSet; // 导入HashSet类，用于存储已处理的元素以去重
import java.util.List; // 导入List接口，表示有序集合
import java.util.NoSuchElementException; // 导入NoSuchElementException异常类，当枚举器没有元素时抛出
import java.util.Set; // 导入Set接口，表示不重复元素的集合

/**
 * Performs a union (or union all) of all its inputs (which must be already sorted),
 * respecting the order.
 * // 这个类的作用是执行多个已排序输入的合并联合(UNION)或全联合(UNION ALL)操作，保持排序顺序
 * // 这是一个枚举器实现，使用归并排序算法的思想来合并多个有序数据源
 * // 核心功能：
 * // 1. 接收多个已排序的数据源输入
 * // 2. 按照排序键的顺序合并这些输入
 * // 3. 根据参数决定是否去除重复元素(UNION vs UNION ALL)
 * // 4. 使用归并算法，时间复杂度为O(n)，其中n是所有输入元素的总数
 * // 5. 利用输入已排序的特性，优化去重逻辑，只保留相同键的元素在去重集合中
 *
 * @param <TSource> record type // 泛型参数TSource表示记录类型，即枚举器中元素的类型
 * @param <TKey> sort key // 泛型参数TKey表示排序键的类型，用于比较和排序元素
 */
// MergeUnionEnumerator是一个泛型类，实现了Enumerator接口，提供枚举功能
// final修饰表示该类不能被继承，确保枚举器实现的封闭性
final class MergeUnionEnumerator<TSource, TKey> implements Enumerator<TSource> {
  // 存储所有输入枚举器的数组，每个枚举器代表一个有序数据源
  // 例如：对于UNION ALL查询，可能有多个表的扫描结果，每个结果都是一个Enumerator
  private final Enumerator<TSource>[] inputs; // 输入枚举器数组，final表示初始化后不可改变引用
  // 存储每个输入枚举器当前值的数组，与inputs数组一一对应
  // 用于在归并过程中快速访问每个输入的当前元素，避免重复调用current()方法
  private final TSource[] currentInputsValues; // 当前输入值数组，缓存每个枚举器的当前元素
  // 标记每个输入枚举器是否已完成的布尔数组，与inputs数组一一对应
  // true表示该枚举器已遍历完所有元素，false表示还有元素
  private final boolean[] inputsFinished; // 输入完成状态数组，标记哪些枚举器已耗尽
  // 函数对象，用于从元素中提取排序键
  // 例如：对于ORDER BY dept_id，这个函数会从员工记录中提取dept_id作为排序键
  private final Function1<TSource, TKey> sortKeySelector; // 排序键选择器函数，从元素中提取比较键
  // 比较器对象，用于比较两个排序键的大小关系
  // 例如：对于整数键，可能使用自然顺序；对于字符串键，可能使用字典序
  private final Comparator<TKey> sortComparator; // 排序键比较器，定义键的排序规则
  // 存储当前返回给调用者的元素值
  // 当moveNext()返回true时，current()方法返回这个值
  private TSource currentValue; // 当前元素值，表示最近一次moveNext()返回的元素
  // 仍然活跃（未完成）的输入枚举器数量
  // 当这个值降为0时，表示所有输入都已遍历完成，枚举器结束
  private int activeInputs; // 活跃输入数量，表示还有多少枚举器未耗尽

  // Set to control duplicates, only used if "all" is false
  // 用于控制重复元素的集合，仅在all参数为false(即执行UNION而非UNION ALL)时使用
  // 存储已经输出过的元素的包装对象，用于检测和过滤重复元素
  private final @Nullable Set<EnumerableDefaults.Wrapped<TSource>> processed; // 已处理元素集合，用于去重，@Nullable表示可能为null
  // 函数对象，用于将元素包装成Wrapped对象，以便在Set中按照自定义相等性比较
  // Wrapped对象使用equalityComparer来定义相等性，而非默认的equals()方法
  private final @Nullable Function1<TSource, EnumerableDefaults.Wrapped<TSource>> wrapper; // 包装器函数，将元素转换为可比较的包装对象
  // 记录当前processed集合中元素的排序键
  // 由于输入已排序，当遇到新的排序键时，可以清空processed集合，释放内存
  // 这是一个重要的优化，避免processed集合无限增长
  private @Nullable TKey currentKeyInProcessedSet; // 已处理集合中的当前键，用于优化去重集合的清理

  // 特殊标记对象，用于表示currentValue尚未初始化的状态
  // 使用NOT_INIT而不是null，是因为TSource本身可能就是null类型
  // 当currentValue等于NOT_INIT时，调用current()方法会抛出NoSuchElementException
  private static final Object NOT_INIT = new Object(); // 未初始化标记对象，用于区分currentValue是否已设置

  // 构造方法，初始化MergeUnionEnumerator实例
  // 参数说明：
  // - sources: 输入数据源列表，每个数据源都已按照sortKeySelector提取的键排序
  // - sortKeySelector: 从元素中提取排序键的函数
  // - sortComparator: 比较排序键的比较器
  // - all: 是否保留重复元素，true表示UNION ALL(保留重复)，false表示UNION(去重)
  // - equalityComparer: 自定义相等性比较器，用于判断两个元素是否相等(仅在all为false时使用)
  MergeUnionEnumerator( // 构造方法，创建MergeUnionEnumerator实例
      List<Enumerable<TSource>> sources, // 输入数据源列表，每个数据源都是已排序的
      Function1<TSource, TKey> sortKeySelector, // 排序键选择器函数
      Comparator<TKey> sortComparator, // 排序键比较器
      boolean all, // 是否保留重复元素的标志
      EqualityComparer<TSource> equalityComparer) { // 自定义相等性比较器
    this.sortKeySelector = sortKeySelector; // 保存排序键选择器函数
    this.sortComparator = sortComparator; // 保存排序键比较器

    // 根据all参数决定是否初始化去重相关的字段
    if (all) { // 如果all为true，表示执行UNION ALL操作
      this.processed = null; // 不需要去重集合，设为null
      this.wrapper = null; // 不需要包装器函数，设为null
    } else { // 如果all为false，表示执行UNION操作，需要去重
      this.processed = new HashSet<>(); // 创建HashSet用于存储已处理的元素
      this.wrapper = EnumerableDefaults.wrapperFor(equalityComparer); // 创建包装器函数，使用自定义相等性比较器
    }

    final int size = sources.size(); // 获取输入数据源的数量
    //noinspection unchecked // 抑制未检查的类型转换警告，因为泛型数组创建需要强制转换
    this.inputs = new Enumerator[size]; // 创建输入枚举器数组，大小为数据源数量
    int i = 0; // 初始化索引变量
    for (Enumerable<TSource> source : sources) { // 遍历所有输入数据源
      this.inputs[i++] = source.enumerator(); // 从每个数据源获取枚举器并存入数组
    }

    //noinspection unchecked // 抑制未检查的类型转换警告
    this.currentInputsValues = (TSource[]) new Object[size]; // 创建当前值数组，用于缓存每个枚举器的当前元素
    this.activeInputs = this.currentInputsValues.length; // 初始化活跃输入数量为所有输入的总数
    this.inputsFinished = new boolean[size]; // 创建完成状态数组，初始值默认为false
    //noinspection unchecked // 抑制未检查的类型转换警告
    this.currentValue = (TSource) NOT_INIT; // 初始化当前值为NOT_INIT，表示尚未开始枚举

    initEnumerators(); // 初始化所有枚举器，将它们的第一个元素加载到currentInputsValues数组中
  }

  // 初始化所有枚举器的方法
  // 这个方法在构造函数中被调用，用于将每个输入枚举器的第一个元素加载到currentInputsValues数组中
  // 这样后续的moveNext()方法就可以直接使用这些缓存值进行比较，而不需要重复调用current()
  @RequiresNonNull("inputs") // 注解表示调用此方法前inputs字段必须非null
  @SuppressWarnings("method.invocation.invalid") // 抑制方法调用无效的警告
  private void initEnumerators(@UnknownInitialization MergeUnionEnumerator<TSource, TKey> this) { // 初始化枚举器方法
    for (int i = 0; i < inputs.length; i++) { // 遍历所有输入枚举器
      moveEnumerator(i); // 调用moveEnumerator方法，将第i个枚举器的第一个元素加载到缓存中
    }
  }

  // 移动指定索引的枚举器到下一个元素
  // 参数i: 枚举器在inputs数组中的索引
  // 这个方法会尝试将指定枚举器移动到下一个元素，并更新相关的状态：
  // - 如果成功移动，将新元素存入currentInputsValues数组
  // - 如果枚举器已耗尽，减少activeInputs计数，标记该枚举器为已完成
  private void moveEnumerator(int i) { // 移动枚举器方法
    final Enumerator<TSource> enumerator = inputs[i]; // 获取指定索引的枚举器
    if (!enumerator.moveNext()) { // 尝试移动到下一个元素
      // 如果moveNext()返回false，表示该枚举器已耗尽
      activeInputs--; // 减少活跃输入数量
      inputsFinished[i] = true; // 标记该枚举器为已完成
      @Nullable TSource[] auxInputsValues = currentInputsValues; // 创建局部变量引用，用于CheckerFramework空值分析
      auxInputsValues[i] = null; // 将该枚举器的缓存值设为null
    } else { // 如果moveNext()返回true，表示成功移动到下一个元素
      currentInputsValues[i] = enumerator.current(); // 将当前元素存入缓存数组
      inputsFinished[i] = false; // 标记该枚举器未完成
    }
  }

  // 检查元素是否重复的方法
  // 参数value: 待检查的元素
  // 返回值: true表示元素不重复，可以输出；false表示元素重复，应该跳过
  // 这个方法实现了UNION的去重逻辑，利用输入已排序的特性进行优化：
  // - 只在processed集合中保留相同排序键的元素
  // - 当遇到新的排序键时，清空processed集合，释放内存
  private boolean checkNotDuplicated(TSource value) { // 检查是否重复的方法
    if (processed == null) { // 如果processed为null，表示执行UNION ALL操作
      return true; // UNION ALL不检查重复，直接返回true
    }

    // check duplicates // 开始检查重复
    @SuppressWarnings("dereference.of.nullable") // 抑制可能空指针解引用的警告（实际上这里processed不为null）
    final EnumerableDefaults.Wrapped<TSource> wrapped = wrapper.apply(value); // 使用包装器函数将元素包装成Wrapped对象
    if (!processed.contains(wrapped)) { // 如果processed集合中不包含该元素
      final TKey key = sortKeySelector.apply(value); // 提取元素的排序键
      if (!processed.isEmpty()) { // 如果processed集合不为空
        // Since inputs are sorted, we do not need to keep in the set all the items that we
        // have previously returned, just the ones with the same key, as soon as we see a new
        // key, we can clear the set containing the items belonging to the previous key
        // 由于输入已排序，我们不需要在集合中保留之前返回的所有元素
        // 只需要保留具有相同键的元素，一旦看到新的键，就可以清空包含前一个键元素的集合
        @SuppressWarnings("argument.type.incompatible") // 抑制参数类型不兼容的警告
        final int sortComparison = sortComparator.compare(key, currentKeyInProcessedSet); // 比较当前键与已处理集合中的键
        if (sortComparison != 0) { // 如果键不相同
          processed.clear(); // 清空已处理集合，释放内存
          currentKeyInProcessedSet = key; // 更新当前键为新键
        }
      } else { // 如果processed集合为空
        currentKeyInProcessedSet = key; // 设置当前键
      }
      processed.add(wrapped); // 将包装后的元素添加到已处理集合中
      return true; // 返回true，表示元素不重复，可以输出
    }
    return false; // 返回false，表示元素重复，应该跳过
  }

  // 比较两个元素的方法
  // 参数e1: 第一个元素
  // 参数e2: 第二个元素
  // 返回值: 负数表示e1 < e2，0表示e1 == e2，正数表示e1 > e2
  // 这个方法通过sortKeySelector提取排序键，然后使用sortComparator比较键的大小
  private int compare(TSource e1, TSource e2) { // 比较两个元素的方法
    final TKey key1 = sortKeySelector.apply(e1); // 提取第一个元素的排序键
    final TKey key2 = sortKeySelector.apply(e2); // 提取第二个元素的排序键
    return sortComparator.compare(key1, key2); // 使用比较器比较两个键的大小
  }

  // 获取当前元素的方法，实现Enumerator接口
  // 返回值: 当前元素，即最近一次moveNext()返回的元素
  // 异常: 如果尚未调用moveNext()或moveNext()返回false，抛出NoSuchElementException
  @Override public TSource current() { // 获取当前元素的方法
    if (currentValue == NOT_INIT) { // 如果currentValue等于NOT_INIT，表示尚未初始化
      throw new NoSuchElementException(); // 抛出NoSuchElementException异常
    }
    return currentValue; // 返回当前元素值
  }

  // 移动到下一个元素的方法，实现Enumerator接口
  // 返回值: true表示成功移动到下一个元素，false表示枚举器已耗尽
  // 这个方法是归并联合的核心实现，使用归并算法：
  // 1. 在所有活跃输入中找到排序键最小的元素
  // 2. 检查该元素是否重复（如果需要去重）
  // 3. 如果不重复，将该元素作为当前元素并返回true
  // 4. 如果重复，跳过该元素，继续寻找下一个
  @Override public boolean moveNext() { // 移动到下一个元素的方法
    while (activeInputs > 0) { // 只要还有活跃输入，就继续循环
      int candidateIndex = -1; // 初始化候选索引为-1
      for (int i = 0; i < currentInputsValues.length; i++) { // 遍历所有输入
        if (!inputsFinished[i]) { // 如果该输入未完成
          candidateIndex = i; // 将该索引设为候选索引
          break; // 跳出循环
        }
      }

      // 如果有多个活跃输入，需要找到其中排序键最小的元素
      if (activeInputs > 1) { // 如果活跃输入数量大于1
        for (int i = candidateIndex + 1; i < currentInputsValues.length; i++) { // 从候选索引的下一个开始遍历
          if (inputsFinished[i]) { // 如果该输入已完成
            continue; // 跳过该输入
          }

          // 比较候选元素与当前元素
          final int comp = // 比较结果
              compare(currentInputsValues[candidateIndex], // 候选元素
                  currentInputsValues[i]); // 当前元素
          if (comp > 0) { // 如果当前元素小于候选元素
            candidateIndex = i; // 更新候选索引为当前索引
          }
        }
      }

      // 检查候选元素是否重复
      if (checkNotDuplicated(currentInputsValues[candidateIndex])) { // 如果候选元素不重复
        currentValue = currentInputsValues[candidateIndex]; // 将候选元素设为当前元素
        moveEnumerator(candidateIndex); // 移动该枚举器到下一个元素
        return true; // 返回true，表示成功获取下一个元素
      } else { // 如果候选元素重复
        moveEnumerator(candidateIndex); // 移动该枚举器到下一个元素
        // continue loop // 继续循环，寻找下一个不重复的元素
      }
    }
    return false; // 如果所有输入都已耗尽，返回false
  }

  // 重置枚举器的方法，实现Enumerator接口
  // 这个方法将枚举器重置到初始状态，可以重新开始枚举
  // 重置操作包括：
  // 1. 重置所有输入枚举器
  // 2. 清空已处理集合（如果存在）
  // 3. 重置当前值和活跃输入数量
  // 4. 重新初始化枚举器，加载第一个元素
  @Override public void reset() { // 重置枚举器的方法
    for (Enumerator<TSource> enumerator : inputs) { // 遍历所有输入枚举器
      enumerator.reset(); // 重置每个枚举器
    }
    if (processed != null) { // 如果存在已处理集合
      processed.clear(); // 清空已处理集合
      currentKeyInProcessedSet = null; // 重置当前键
    }
    //noinspection unchecked // 抑制未检查的类型转换警告
    currentValue = (TSource) NOT_INIT; // 重置当前值为NOT_INIT
    activeInputs = currentInputsValues.length; // 重置活跃输入数量
    initEnumerators(); // 重新初始化枚举器
  }

  // 关闭枚举器的方法，实现Enumerator接口
  // 这个方法释放所有输入枚举器占用的资源
  // 通常在枚举完成后调用，用于清理资源
  @Override public void close() { // 关闭枚举器的方法
    for (Enumerator<TSource> enumerator : inputs) { // 遍历所有输入枚举器
      enumerator.close(); // 关闭每个枚举器
    }
  }
}
