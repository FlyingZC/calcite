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
// Apache开源协议声明，说明该代码的版权和使用许可
package org.apache.calcite.rel.hint;  // 定义包名，该类属于org.apache.calcite.rel.hint包，处理关系表达式提示功能

import org.apache.calcite.plan.RelOptRule;  // 导入优化规则类，用于定义查询优化规则
import org.apache.calcite.rel.RelNode;  // 导入关系表达式接口，表示关系代数中的操作
import org.apache.calcite.rel.convert.ConverterRule;  // 导入转换规则类，用于定义关系表达式之间的转换规则
import org.apache.calcite.util.Litmus;  // 导入断言工具类，用于验证条件并处理错误
import org.apache.calcite.util.trace.CalciteTrace;  // 导入Calcite日志追踪工具，用于获取日志记录器

import com.google.common.collect.ImmutableMap;  // 导入Google Guava的不可变Map类，用于存储不可变的键值对映射

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入空值检查注解，用于标记可能为null的字段
import org.slf4j.Logger;  // 导入SLF4J日志接口，用于记录日志信息

import java.util.HashMap;  // 导入Java集合框架的HashMap类，用于存储键值对映射
import java.util.List;  // 导入Java集合框架的List接口，用于存储有序的元素列表
import java.util.Locale;  // 导入Locale类，用于处理区域设置相关的操作（如大小写转换）
import java.util.Map;  // 导入Java集合框架的Map接口，用于存储键值对映射
import java.util.Set;  // 导入Java集合框架的Set接口，用于存储不重复的元素集合
import java.util.stream.Collectors;  // 导入Stream API的Collectors工具类，用于流式数据处理和收集

import static java.util.Objects.requireNonNull;  // 导入Objects工具类的requireNonNull方法，用于参数非空检查

/**
 * A collection of {@link HintStrategy}s.  // HintStrategy集合类，用于管理所有查询提示策略
 *
 * <p>Every hint must register a {@link HintStrategy} into the collection.  // 每个提示都必须在集合中注册一个HintStrategy策略
 * With a hint strategies mapping, the hint strategy table is used as a tool  // 通过提示策略映射，提示策略表作为工具用于判断：
 * to decide i) if the given hint was registered; ii) which hints are suitable for the rel with  // i) 给定的提示是否已注册；ii) 哪些提示适用于带有给定提示集合的关系表达式；
 * a given hints collection; iii) if the hint options are valid.  // iii) 提示选项是否有效
 *
 * <p>The hint strategy table is immutable. To create one, use  // 提示策略表是不可变的。要创建一个实例，使用builder()方法
 * {@link #builder()}.
 *
 * <p>Match of hint name is case insensitive.  // 提示名称的匹配不区分大小写
 *
 * @see HintPredicate  // 参见HintPredicate接口，用于定义提示的适用性判断逻辑
 */
public class HintStrategyTable {  // 定义HintStrategyTable类，用于管理所有查询提示策略的集合
  //~ Static fields/initializers ---------------------------------------------  // 静态字段/初始化块分隔符（IDE格式化标记）

  /** Empty strategies. */  // 空的提示策略表常量，用于表示没有任何提示策略的情况
  public static final HintStrategyTable EMPTY =  // 定义公共静态常量，类型为HintStrategyTable
      new HintStrategyTable(ImmutableMap.of(), HintErrorLogger.INSTANCE);  // 创建空的HintStrategyTable实例，使用空Map和默认错误处理器

  //~ Instance fields --------------------------------------------------------  // 实例字段分隔符（IDE格式化标记）

  /** Mapping from hint name to {@link HintStrategy}. */  // 提示名称到HintStrategy的映射关系
  private final Map<Key, HintStrategy> strategies;  // 私有final字段，存储提示名称（使用Key对象）到对应策略的不可变映射

  /** Handler for the hint error. */  // 提示错误处理器，用于处理提示验证和检查时的错误
  private final Litmus errorHandler;  // 私有final字段，使用Litmus接口处理错误检查和日志记录

  private HintStrategyTable(Map<Key, HintStrategy> strategies, Litmus litmus) {  // 私有构造方法，确保只能通过Builder创建实例
    this.strategies = ImmutableMap.copyOf(strategies);  // 将传入的strategies Map转换为不可变Map并赋值，确保线程安全
    this.errorHandler = litmus;  // 初始化错误处理器字段
  }

  //~ Methods ----------------------------------------------------------------  // 方法分隔符（IDE格式化标记）

  /**
   * Applies this {@link HintStrategyTable} hint strategies to the given relational  // 将此HintStrategyTable的提示策略应用于给定的关系表达式和提示集合
   * expression and the {@code hints}.
   *
   * @param hints Hints that may attach to the {@code rel}  // 参数：可能附加到rel上的提示列表
   * @param rel   Relational expression  // 参数：关系表达式，即要应用提示的RelNode对象
   * @return A hint list that can be attached to the {@code rel}  // 返回值：可以附加到rel上的有效提示列表
   */
  public List<RelHint> apply(List<RelHint> hints, RelNode rel) {  // 公共方法，应用提示策略并返回可用的提示列表
    return hints.stream()  // 将提示列表转换为流，进行流式处理
        .filter(relHint -> canApply(relHint, rel))  // 过滤出可以应用于当前关系表达式的提示
        .collect(Collectors.toList());  // 将过滤后的提示流收集为List并返回
  }

  private boolean canApply(RelHint hint, RelNode rel) {  // 私有方法，判断给定的提示是否可以应用于指定的关系表达式
    final Key key = Key.of(hint.hintName);  // 根据提示名称创建Key对象（转换为小写，不区分大小写）
    assert this.strategies.containsKey(key) : "hint " + hint.hintName + " must be present";  // 断言：提示名称必须在策略映射中存在，否则抛出AssertionError
    return this.strategies.get(key).predicate.apply(hint, rel);  // 获取对应的HintStrategy，调用其predicate的apply方法判断提示是否适用
  }

  /**
   * Checks if the given hint is valid.  // 检查给定的提示是否有效
   *
   * @param hint The hint  // 参数：要验证的提示对象
   */
  public boolean validateHint(RelHint hint) {  // 公共方法，验证提示的有效性
    final Key key = Key.of(hint.hintName);  // 根据提示名称创建Key对象（转换为小写，不区分大小写）
    boolean hintExists =  // 检查提示是否已注册
        this.errorHandler.check(this.strategies.containsKey(key),  // 使用错误处理器检查提示是否在策略映射中存在
            "Hint: {} should be registered in the {}",  // 错误消息模板
            hint.hintName,  // 错误消息参数1：提示名称
            this.getClass().getSimpleName());  // 错误消息参数2：类名
    if (!hintExists) {  // 如果提示不存在
      return false;  // 返回false，表示验证失败
    }
    final HintStrategy strategy = strategies.get(key);  // 获取对应的HintStrategy对象
    if (strategy != null && strategy.hintOptionChecker != null) {  // 如果策略存在且配置了选项检查器
      return strategy.hintOptionChecker.checkOptions(hint, this.errorHandler);  // 调用选项检查器验证提示选项，返回验证结果
    }
    return true;  // 如果没有配置选项检查器，默认返回true，表示验证通过
  }

  /** Returns whether the {@code hintable} has hints that imply  // 判断hintable的提示是否暗示给定的规则应该被排除
   * the given {@code rule} should be excluded. */
  public boolean isRuleExcluded(Hintable hintable, RelOptRule rule) {  // 公共方法，检查给定的优化规则是否应该被排除
    final List<RelHint> hints = hintable.getHints();  // 获取hintable对象的所有提示列表
    if (hints.isEmpty()) {  // 如果提示列表为空
      return false;  // 返回false，表示不需要排除任何规则
    }

    for (RelHint hint : hints) {  // 遍历所有提示
      final Key key = Key.of(hint.hintName);  // 根据提示名称创建Key对象（转换为小写，不区分大小写）
      assert this.strategies.containsKey(key) : "hint " + hint.hintName + " must be present";  // 断言：提示名称必须在策略映射中存在，否则抛出AssertionError
      final HintStrategy strategy = strategies.get(key);  // 获取对应的HintStrategy对象
      if (strategy.excludedRules.contains(rule)) {  // 如果该策略的排除规则集合中包含给定的规则
        return isDesiredConversionPossible(strategy.converterRules, hintable);  // 检查是否可以通过转换规则进行转换，返回结果
      }
    }

    return false;  // 如果没有任何提示要求排除该规则，返回false
  }

  /** Returns whether the {@code hintable} has hints that imply  // 判断hintable的提示是否暗示可以成功进行转换
   * the given {@code hintable} can make conversion successfully. */
  private static boolean isDesiredConversionPossible(  // 私有静态方法，判断是否可以进行期望的转换
      Set<ConverterRule> converterRules,  // 参数：转换规则集合
      Hintable hintable) {  // 参数：可提示的关系表达式对象
    // If no converter rules are specified, we assume the conversion is possible.  // 如果没有指定转换规则，我们假设转换是可能的
    return converterRules.isEmpty()  // 如果转换规则集合为空，返回true
        || converterRules.stream()  // 否则，将转换规则集合转换为流
            .anyMatch(converterRule -> converterRule.convert((RelNode) hintable) != null);  // 检查是否有任何一个转换规则可以成功转换hintable
  }

  /**
   * Returns a {@code HintStrategyTable} builder.  // 返回HintStrategyTable的构建器
   */
  public static Builder builder() {  // 公共静态工厂方法，创建Builder实例
    return new Builder();  // 返回新的Builder对象
  }

  //~ Inner Class ------------------------------------------------------------  // 内部类分隔符（IDE格式化标记）

  /**
   * Key used to keep the strategies which ignores the case sensitivity.  // 用于存储策略的Key类，忽略大小写敏感性
   */
  private static class Key {  // 定义私有静态内部类Key，作为策略映射的键
    private final String name;  // 私有final字段，存储提示名称（已转换为小写）

    private Key(String name) {  // 私有构造方法
      this.name = name;  // 初始化name字段
    }

    static Key of(String name) {  // 静态工厂方法，根据提示名称创建Key对象
      return new Key(name.toLowerCase(Locale.ROOT));  // 将提示名称转换为小写（使用ROOT区域设置）并创建Key对象
    }

    @Override public boolean equals(@Nullable Object o) {  // 重写equals方法，用于比较两个Key对象是否相等
      if (this == o) {  // 如果是同一个对象引用
        return true;  // 返回true
      }
      if (o == null || getClass() != o.getClass()) {  // 如果o为null或类型不匹配
        return false;  // 返回false
      }
      Key key = (Key) o;  // 将o强制转换为Key类型
      return name.equals(key.name);  // 比较name字段是否相等
    }

    @Override public int hashCode() {  // 重写hashCode方法，用于在HashMap等集合中使用
      return this.name.hashCode();  // 返回name字段的hashCode值
    }
  }

  /**
   * Builder for {@code HintStrategyTable}.  // HintStrategyTable的构建器类，使用建造者模式创建HintStrategyTable实例
   */
  public static class Builder {  // 定义公共静态内部类Builder
    private final Map<Key, HintStrategy> strategies = new HashMap<>();  // 存储提示名称到提示策略的映射，使用HashMap以便于添加
    private Litmus errorHandler = HintErrorLogger.INSTANCE;  // 错误处理器，默认使用HintErrorLogger实例

    public Builder hintStrategy(String hintName, HintPredicate hintPredicate) {  // 添加提示策略的方法，使用HintPredicate构建
      this.strategies.put(Key.of(hintName),  // 将提示名称转换为Key对象作为映射的键
          HintStrategy.builder(requireNonNull(hintPredicate, "hintPredicate")).build());  // 使用HintPredicate创建HintStrategy对象作为映射的值
      return this;  // 返回Builder实例，支持链式调用
    }

    public Builder hintStrategy(String hintName, HintStrategy hintStrategy) {  // 添加提示策略的方法，直接使用HintStrategy对象
      this.strategies.put(Key.of(hintName), requireNonNull(hintStrategy, "hintStrategy"));  // 将提示名称转换为Key对象，并存储对应的HintStrategy
      return this;  // 返回Builder实例，支持链式调用
    }

    /**
     * Sets an error handler to customize the hints error handling.  // 设置自定义的错误处理器来处理提示错误
     *
     * <p>The default behavior is to log warnings.  // 默认行为是记录警告日志
     *
     * @param errorHandler The handler  // 参数：错误处理器对象
     */
    public Builder errorHandler(Litmus errorHandler) {  // 设置错误处理器的方法
      this.errorHandler = errorHandler;  // 设置错误处理器字段
      return this;  // 返回Builder实例，支持链式调用
    }

    public HintStrategyTable build() {  // 构建HintStrategyTable实例的方法
      return new HintStrategyTable(  // 创建新的HintStrategyTable实例
          this.strategies,  // 传入策略映射
          this.errorHandler);  // 传入错误处理器
    }
  }

  /** Implementation of {@link org.apache.calcite.util.Litmus} that returns  // Litmus接口的实现类，用于处理提示错误检查
   * a status code, it logs warnings for fail check and does not throw. */  // 返回状态码，对失败的检查记录警告日志而不抛出异常
  public static class HintErrorLogger implements Litmus {  // 定义公共静态内部类HintErrorLogger，实现Litmus接口
    private static final Logger LOGGER = CalciteTrace.PARSER_LOGGER;  // 静态final字段，使用Calcite的解析器日志记录器

    public static final HintErrorLogger INSTANCE = new HintErrorLogger();  // 公共静态常量，HintErrorLogger的单例实例

    @Override public boolean fail(@Nullable String message, @Nullable Object... args) {  // 重写fail方法，处理检查失败的情况
      LOGGER.warn(requireNonNull(message, "message"), args);  // 使用日志记录器记录警告级别的日志
      return false;  // 返回false，表示检查失败
    }
  }
}
