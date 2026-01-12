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

import org.apache.calcite.linq4j.function.BigDecimalFunction1;
import org.apache.calcite.linq4j.function.DoubleFunction1;
import org.apache.calcite.linq4j.function.EqualityComparer;
import org.apache.calcite.linq4j.function.FloatFunction1;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.linq4j.function.Function2;
import org.apache.calcite.linq4j.function.IntegerFunction1;
import org.apache.calcite.linq4j.function.LongFunction1;
import org.apache.calcite.linq4j.function.NullableBigDecimalFunction1;
import org.apache.calcite.linq4j.function.NullableDoubleFunction1;
import org.apache.calcite.linq4j.function.NullableFloatFunction1;
import org.apache.calcite.linq4j.function.NullableIntegerFunction1;
import org.apache.calcite.linq4j.function.NullableLongFunction1;
import org.apache.calcite.linq4j.function.Predicate1;
import org.apache.calcite.linq4j.function.Predicate2;
import org.apache.calcite.linq4j.tree.FunctionExpression;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.checkerframework.framework.qual.Covariant;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Comparator;

import static org.apache.calcite.linq4j.QueryableDefaults.NonLeafReplayableQueryable;

import static java.util.Objects.requireNonNull;

/**
 * QueryableFactory接口的实现类，用于记录每个查询操作事件
 * 并返回一个可以在调用其replay方法时重放这些事件的对象
 * 这是一个记录器模式的实现，用于捕获和重放LINQ查询操作
 *
 * @param <T> 元素类型，表示查询集合中元素的类型
 */
@Covariant(0) // 协变类型注解，表示泛型参数T是协变的
public class QueryableRecorder<T> implements QueryableFactory<T> { // QueryableRecorder类实现了QueryableFactory接口，用于记录和重放查询操作
  private static final QueryableRecorder INSTANCE = new QueryableRecorder(); // 单例实例，所有QueryableRecorder共享同一个实例

  @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告
  public static <T> QueryableRecorder<T> instance() { // 获取QueryableRecorder的单例实例
    return INSTANCE; // 返回单例实例，由于泛型擦除，可以安全地转换为任意类型
  }

  @Override public @Nullable T aggregate(final Queryable<T> source, // 聚合操作，对源序列应用累加器函数，返回单个结果值
      final FunctionExpression<Function2<@Nullable T, T, T>> func) { // 累加器函数，接受累加值和当前元素，返回新的累加值
    return new QueryableDefaults.NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的聚合操作
        factory.aggregate(source, func); // 在传入的factory上执行aggregate操作
      }
    }.single(); // 调用single方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TAccumulate> TAccumulate aggregate(final Queryable<T> source, // 带种子的聚合操作，使用指定的种子值作为累加器的初始值
      final TAccumulate seed, // 累加器的初始种子值
      final FunctionExpression<Function2<TAccumulate, T, TAccumulate>> func) { // 累加器函数，接受累加值和当前元素，返回新的累加值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的聚合操作
        factory.aggregate(source, seed, func); // 在传入的factory上执行带种子的aggregate操作
      }
    }.castSingle(); // 调用castSingle方法获取单个结果值并进行类型转换 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TAccumulate, TResult> TResult aggregate(final Queryable<T> source, // 带种子和结果选择器的聚合操作
      final TAccumulate seed, // 累加器的初始种子值
      final FunctionExpression<Function2<TAccumulate, T, TAccumulate>> func, // 累加器函数，接受累加值和当前元素，返回新的累加值
      final FunctionExpression<Function1<TAccumulate, TResult>> selector) { // 结果选择器函数，将最终的累加值转换为结果类型
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的聚合操作
        factory.aggregate(source, seed, func, selector); // 在传入的factory上执行带种子和结果选择器的aggregate操作
      }
    }.castSingle(); // 调用castSingle方法获取单个结果值并进行类型转换 // CHECKSTYLE: IGNORE 0
  }

  @Override public boolean all(final Queryable<T> source, // 检查序列中的所有元素是否都满足指定条件
      final FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试每个元素是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的all操作
        factory.all(source, predicate); // 在传入的factory上执行all操作
      }
    }.<Boolean>castSingle(); // 调用castSingle方法获取单个布尔结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public boolean any(final Queryable<T> source) { // 检查序列中是否包含任何元素
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的any操作
        factory.any(source); // 在传入的factory上执行any操作
      }
    }.<Boolean>castSingle(); // 调用castSingle方法获取单个布尔结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public boolean any(final Queryable<T> source, // 检查序列中是否存在满足指定条件的元素
      final FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试每个元素是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的any操作
        factory.any(source, predicate); // 在传入的factory上执行带谓词的any操作
      }
    }.<Boolean>castSingle(); // 调用castSingle方法获取单个布尔结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public BigDecimal averageBigDecimal(final Queryable<T> source, // 计算序列中BigDecimal类型元素的平均值
      final FunctionExpression<BigDecimalFunction1<T>> selector) { // 选择器函数，从元素中提取BigDecimal值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的averageBigDecimal操作
        factory.averageBigDecimal(source, selector); // 在传入的factory上执行averageBigDecimal操作
      }
    }.castSingle(); // 调用castSingle方法获取单个BigDecimal结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public BigDecimal averageNullableBigDecimal(final Queryable<T> source, // 计算序列中可空BigDecimal类型元素的平均值
      final FunctionExpression<NullableBigDecimalFunction1<T>> selector) { // 选择器函数，从元素中提取可空BigDecimal值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的averageNullableBigDecimal操作
        factory.averageNullableBigDecimal(source, selector); // 在传入的factory上执行averageNullableBigDecimal操作
      }
    }.castSingle(); // 调用castSingle方法获取单个BigDecimal结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public double averageDouble(final Queryable<T> source, // 计算序列中double类型元素的平均值
      final FunctionExpression<DoubleFunction1<T>> selector) { // 选择器函数，从元素中提取double值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的averageDouble操作
        factory.averageDouble(source, selector); // 在传入的factory上执行averageDouble操作
      }
    }.<Double>castSingle(); // 调用castSingle方法获取单个Double结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public Double averageNullableDouble(final Queryable<T> source, // 计算序列中可空Double类型元素的平均值
      final FunctionExpression<NullableDoubleFunction1<T>> selector) { // 选择器函数，从元素中提取可空Double值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的averageNullableDouble操作
        factory.averageNullableDouble(source, selector); // 在传入的factory上执行averageNullableDouble操作
      }
    }.<Double>castSingle(); // 调用castSingle方法获取单个Double结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public int averageInteger(final Queryable<T> source, // 计算序列中int类型元素的平均值
      final FunctionExpression<IntegerFunction1<T>> selector) { // 选择器函数，从元素中提取int值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的averageInteger操作
        factory.averageInteger(source, selector); // 在传入的factory上执行averageInteger操作
      }
    }.<Integer>castSingle(); // 调用castSingle方法获取单个Integer结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public Integer averageNullableInteger(final Queryable<T> source, // 计算序列中可空Integer类型元素的平均值
      final FunctionExpression<NullableIntegerFunction1<T>> selector) { // 选择器函数，从元素中提取可空Integer值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的averageNullableInteger操作
        factory.averageNullableInteger(source, selector); // 在传入的factory上执行averageNullableInteger操作
      }
    }.<Integer>castSingle(); // 调用castSingle方法获取单个Integer结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public float averageFloat(final Queryable<T> source, // 计算序列中float类型元素的平均值
      final FunctionExpression<FloatFunction1<T>> selector) { // 选择器函数，从元素中提取float值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的averageFloat操作
        factory.averageFloat(source, selector); // 在传入的factory上执行averageFloat操作
      }
    }.<Float>castSingle(); // 调用castSingle方法获取单个Float结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public Float averageNullableFloat(final Queryable<T> source, // 计算序列中可空Float类型元素的平均值
      final FunctionExpression<NullableFloatFunction1<T>> selector) { // 选择器函数，从元素中提取可空Float值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的averageNullableFloat操作
        factory.averageNullableFloat(source, selector); // 在传入的factory上执行averageNullableFloat操作
      }
    }.<Float>castSingle(); // 调用castSingle方法获取单个Float结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public long averageLong(final Queryable<T> source, // 计算序列中long类型元素的平均值
      final FunctionExpression<LongFunction1<T>> selector) { // 选择器函数，从元素中提取long值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的averageLong操作
        factory.averageLong(source, selector); // 在传入的factory上执行averageLong操作
      }
    }.<Long>castSingle(); // 调用castSingle方法获取单个Long结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public Long averageNullableLong(final Queryable<T> source, // 计算序列中可空Long类型元素的平均值
      final FunctionExpression<NullableLongFunction1<T>> selector) { // 选择器函数，从元素中提取可空Long值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的averageNullableLong操作
        factory.averageNullableLong(source, selector); // 在传入的factory上执行averageNullableLong操作
      }
    }.<Long>castSingle(); // 调用castSingle方法获取单个Long结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public <T2> Queryable<T2> cast(final Queryable<T> source, // 将序列中的元素转换为指定的类型
      final Class<T2> clazz) { // 目标类型，用于类型转换
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的cast操作
        factory.cast(source, clazz); // 在传入的factory上执行cast操作
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public Queryable<T> concat(final Queryable<T> source, // 连接两个序列，将第二个序列的元素追加到第一个序列的末尾
      final Enumerable<T> source2) { // 要连接的第二个序列
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的concat操作
        factory.concat(source, source2); // 在传入的factory上执行concat操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public boolean contains(final Queryable<T> source, final T element) { // 检查序列中是否包含指定的元素
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的contains操作
        factory.contains(source, element); // 在传入的factory上执行contains操作
      }
    }.<Boolean>castSingle(); // 调用castSingle方法获取单个布尔结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public boolean contains(final Queryable<T> source, final T element, // 使用指定的相等比较器检查序列中是否包含指定的元素
      final EqualityComparer<T> comparer) { // 相等比较器，用于比较元素的相等性
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的contains操作
        factory.contains(source, element, comparer); // 在传入的factory上执行带比较器的contains操作
      }
    }.<Boolean>castSingle(); // 调用castSingle方法获取单个布尔结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public int count(final Queryable<T> source) { // 返回序列中的元素数量
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的count操作
        factory.count(source); // 在传入的factory上执行count操作
      }
    }.<Integer>castSingle(); // 调用castSingle方法获取单个整数结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public int count(final Queryable<T> source, // 返回序列中满足指定条件的元素数量
      final FunctionExpression<Predicate1<T>> func) { // 谓词函数，用于测试每个元素是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的count操作
        factory.count(source, func); // 在传入的factory上执行带谓词的count操作
      }
    }.<Integer>castSingle(); // 调用castSingle方法获取单个整数结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public Queryable<@Nullable T> defaultIfEmpty(final Queryable<T> source) { // 如果序列为空，则返回包含默认值的序列；否则返回原序列
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的defaultIfEmpty操作
        factory.defaultIfEmpty(source); // 在传入的factory上执行defaultIfEmpty操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @SuppressWarnings("return.type.incompatible") // 抑制返回类型不兼容的警告
  @Override public Queryable<@PolyNull T> defaultIfEmpty(final Queryable<T> source, // 如果序列为空，则返回包含指定值的序列；否则返回原序列
        final @PolyNull T value) { // 如果序列为空时使用的默认值
      return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
        @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的defaultIfEmpty操作
          factory.defaultIfEmpty(source, value); // 在传入的factory上执行带默认值的defaultIfEmpty操作
        }
      }; // 返回NonLeafReplayableQueryable对象
    }
  @Override public Queryable<T> distinct(final Queryable<T> source) { // 返回序列中的不重复元素，去除重复项
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的distinct操作
        factory.distinct(source); // 在传入的factory上执行distinct操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public Queryable<T> distinct(final Queryable<T> source, // 使用指定的相等比较器返回序列中的不重复元素
      final EqualityComparer<T> comparer) { // 相等比较器，用于比较元素的相等性
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的distinct操作
        factory.distinct(source, comparer); // 在传入的factory上执行带比较器的distinct操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public T elementAt(final Queryable<T> source, final int index) { // 返回序列中指定索引处的元素
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的elementAt操作
        factory.elementAt(source, index); // 在传入的factory上执行elementAt操作
      }
    }.castSingle(); // 调用castSingle方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public T elementAtOrDefault(final Queryable<T> source, final int index) { // 返回序列中指定索引处的元素，如果索引超出范围则返回默认值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的elementAtOrDefault操作
        factory.elementAtOrDefault(source, index); // 在传入的factory上执行elementAtOrDefault操作
      }
    }.castSingle(); // 调用castSingle方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public Queryable<T> except(final Queryable<T> source, // 返回序列中不包含在第二个序列中的元素（差集操作）
      final Enumerable<T> enumerable) { // 要排除的第二个序列
    return except(source, enumerable, false); // 调用重载方法，all参数设为false表示不保留重复元素
  }

  @Override public Queryable<T> except(final Queryable<T> source, // 返回序列中不包含在第二个序列中的元素，可选择是否保留重复元素
      final Enumerable<T> enumerable, boolean all) { // 要排除的第二个序列；all为true时保留重复元素，false时去除重复
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的except操作
        factory.except(source, enumerable, all); // 在传入的factory上执行except操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public Queryable<T> except(final Queryable<T> source, // 使用指定的相等比较器返回序列中不包含在第二个序列中的元素
      final Enumerable<T> enumerable, final EqualityComparer<T> comparer) { // 要排除的第二个序列；相等比较器
    return except(source, enumerable, comparer, false); // 调用重载方法，all参数设为false表示不保留重复元素
  }

  @Override public Queryable<T> except(final Queryable<T> source, // 使用指定的相等比较器返回序列中不包含在第二个序列中的元素，可选择是否保留重复元素
      final Enumerable<T> enumerable, final EqualityComparer<T> comparer, boolean all) { // 要排除的第二个序列；相等比较器；all为true时保留重复元素
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的except操作
        factory.except(source, enumerable, comparer, all); // 在传入的factory上执行带比较器和all参数的except操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public T first(final Queryable<T> source) { // 返回序列中的第一个元素
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的first操作
        factory.first(source); // 在传入的factory上执行first操作
      }
    }.single(); // 调用single方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public T first(final Queryable<T> source, // 返回序列中满足指定条件的第一个元素
      final FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试每个元素是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的first操作
        factory.first(source, predicate); // 在传入的factory上执行带谓词的first操作
      }
    }.single(); // 调用single方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public @Nullable T firstOrDefault(final Queryable<T> source) { // 返回序列中的第一个元素，如果序列为空则返回默认值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的firstOrDefault操作
        factory.firstOrDefault(source); // 在传入的factory上执行firstOrDefault操作
      }
    }.single(); // 调用single方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public @Nullable T firstOrDefault(final Queryable<T> source, // 返回序列中满足指定条件的第一个元素，如果没有找到则返回默认值
      final FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试每个元素是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的firstOrDefault操作
        factory.firstOrDefault(source, predicate); // 在传入的factory上执行带谓词的firstOrDefault操作
      }
    }.single(); // 调用single方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TKey> Queryable<Grouping<TKey, T>> groupBy(final Queryable<T> source, // 根据指定的键选择器对序列中的元素进行分组
      final FunctionExpression<Function1<T, TKey>> keySelector) { // 键选择器函数，从元素中提取分组键
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的groupBy操作
        factory.groupBy(source, keySelector); // 在传入的factory上执行groupBy操作
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TKey> Queryable<Grouping<TKey, T>> groupBy(final Queryable<T> source, // 根据指定的键选择器和比较器对序列中的元素进行分组
      final FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数，从元素中提取分组键
      final EqualityComparer<TKey> comparer) { // 相等比较器，用于比较键的相等性
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的groupBy操作
        factory.groupBy(source, keySelector, comparer); // 在传入的factory上执行带比较器的groupBy操作
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TKey, TElement> Queryable<Grouping<TKey, TElement>> groupBy( // 根据指定的键选择器和元素选择器对序列中的元素进行分组
      final Queryable<T> source, // 源序列
      final FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数，从元素中提取分组键
      final FunctionExpression<Function1<T, TElement>> elementSelector) { // 元素选择器函数，从元素中提取要包含在分组中的元素
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的groupBy操作
        factory.groupBy(source, keySelector, elementSelector); // 在传入的factory上执行带元素选择器的groupBy操作
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TKey, TElement> Queryable<Grouping<TKey, TElement>> groupBy( // 根据指定的键选择器、元素选择器和比较器对序列中的元素进行分组
      final Queryable<T> source, // 源序列
      final FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数，从元素中提取分组键
      final FunctionExpression<Function1<T, TElement>> elementSelector, // 元素选择器函数，从元素中提取要包含在分组中的元素
      final EqualityComparer<TKey> comparer) { // 相等比较器，用于比较键的相等性
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的groupBy操作
        factory.groupBy(source, keySelector, elementSelector, comparer); // 在传入的factory上执行带比较器的groupBy操作
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TKey, TResult> Queryable<TResult> groupByK( // 根据指定的键选择器和结果选择器对序列中的元素进行分组，并投影到结果类型
      final Queryable<T> source, // 源序列
      final FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数，从元素中提取分组键
      final FunctionExpression<Function2<TKey, Enumerable<T>, TResult>> resultSelector) { // 结果选择器函数，接受键和对应的元素集合，返回结果
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的groupByK操作
        factory.groupByK(source, keySelector, resultSelector); // 在传入的factory上执行groupByK操作
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TKey, TResult> Queryable<TResult> groupByK(final Queryable<T> source, // 根据指定的键选择器、结果选择器和比较器对序列中的元素进行分组，并投影到结果类型
      final FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数，从元素中提取分组键
      final FunctionExpression<Function2<TKey, Enumerable<T>, TResult>> resultSelector, // 结果选择器函数，接受键和对应的元素集合，返回结果
      final EqualityComparer<TKey> comparer) { // 相等比较器，用于比较键的相等性
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的groupByK操作
        factory.groupByK(source, keySelector, resultSelector, comparer); // 在传入的factory上执行带比较器的groupByK操作
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TKey, TElement, TResult> Queryable<TResult> groupBy( // 根据指定的键选择器、元素选择器和结果选择器对序列中的元素进行分组，并投影到结果类型
      final Queryable<T> source, // 源序列
      final FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数，从元素中提取分组键
      final FunctionExpression<Function1<T, TElement>> elementSelector, // 元素选择器函数，从元素中提取要包含在分组中的元素
      final FunctionExpression<Function2<TKey, Enumerable<TElement>, TResult>> resultSelector) { // 结果选择器函数，接受键和对应的元素集合，返回结果
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的groupBy操作
        factory.groupBy(source, keySelector, elementSelector, resultSelector); // 在传入的factory上执行带元素选择器和结果选择器的groupBy操作
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TKey, TElement, TResult> Queryable<TResult> groupBy( // 根据指定的键选择器、元素选择器、结果选择器和比较器对序列中的元素进行分组，并投影到结果类型
      final Queryable<T> source, // 源序列
      final FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数，从元素中提取分组键
      final FunctionExpression<Function1<T, TElement>> elementSelector, // 元素选择器函数，从元素中提取要包含在分组中的元素
      final FunctionExpression<Function2<TKey, Enumerable<TElement>, TResult>> resultSelector, // 结果选择器函数，接受键和对应的元素集合，返回结果
      final EqualityComparer<TKey> comparer) { // 相等比较器，用于比较键的相等性
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的groupBy操作
        factory.groupBy(source, keySelector, elementSelector, resultSelector, // 在传入的factory上执行带比较器的groupBy操作
            comparer);
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> groupJoin( // 基于键相等性将两个序列的元素进行关联，并对结果进行分组
      final Queryable<T> source, final Enumerable<TInner> inner, // 外部序列和内部序列
      final FunctionExpression<Function1<T, TKey>> outerKeySelector, // 外部键选择器函数，从外部元素中提取键
      final FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 内部键选择器函数，从内部元素中提取键
      final FunctionExpression<Function2<T, Enumerable<TInner>, TResult>> resultSelector) { // 结果选择器函数，接受外部元素和匹配的内部元素集合，返回结果
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的groupJoin操作
        factory.groupJoin(source, inner, outerKeySelector, innerKeySelector, // 在传入的factory上执行groupJoin操作
            resultSelector);
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> groupJoin( // 基于键相等性和指定的比较器将两个序列的元素进行关联，并对结果进行分组
      final Queryable<T> source, final Enumerable<TInner> inner, // 外部序列和内部序列
      final FunctionExpression<Function1<T, TKey>> outerKeySelector, // 外部键选择器函数，从外部元素中提取键
      final FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 内部键选择器函数，从内部元素中提取键
      final FunctionExpression<Function2<T, Enumerable<TInner>, TResult>> resultSelector, // 结果选择器函数，接受外部元素和匹配的内部元素集合，返回结果
      final EqualityComparer<TKey> comparer) { // 相等比较器，用于比较键的相等性
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的groupJoin操作
        factory.groupJoin(source, inner, outerKeySelector, innerKeySelector, // 在传入的factory上执行带比较器的groupJoin操作
            resultSelector, comparer);
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public Queryable<T> intersect(final Queryable<T> source, // 返回两个序列的交集（同时出现在两个序列中的元素）
      final Enumerable<T> enumerable) { // 第二个序列
    return intersect(source, enumerable, false); // 调用重载方法，all参数设为false表示不保留重复元素
  }

  @Override public Queryable<T> intersect(final Queryable<T> source, // 返回两个序列的交集，可选择是否保留重复元素
      final Enumerable<T> enumerable, boolean all) { // 第二个序列；all为true时保留重复元素，false时去除重复
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的intersect操作
        factory.intersect(source, enumerable, all); // 在传入的factory上执行intersect操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public Queryable<T> intersect(final Queryable<T> source, // 使用指定的相等比较器返回两个序列的交集
      final Enumerable<T> enumerable, final EqualityComparer<T> comparer) { // 第二个序列；相等比较器
    return intersect(source, enumerable, comparer, false); // 调用重载方法，all参数设为false表示不保留重复元素
  }

  @Override public Queryable<T> intersect(final Queryable<T> source, // 使用指定的相等比较器返回两个序列的交集，可选择是否保留重复元素
      final Enumerable<T> enumerable, final EqualityComparer<T> comparer, boolean all) { // 第二个序列；相等比较器；all为true时保留重复元素
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的intersect操作
        factory.intersect(source, enumerable, comparer, all); // 在传入的factory上执行带比较器和all参数的intersect操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> join( // 基于键相等性将两个序列的元素进行关联（内连接）
      final Queryable<T> source, final Enumerable<TInner> inner, // 外部序列和内部序列
      final FunctionExpression<Function1<T, TKey>> outerKeySelector, // 外部键选择器函数，从外部元素中提取键
      final FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 内部键选择器函数，从内部元素中提取键
      final FunctionExpression<Function2<T, TInner, TResult>> resultSelector) { // 结果选择器函数，接受匹配的外部和内部元素，返回结果
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的join操作
        factory.join(source, inner, outerKeySelector, innerKeySelector, // 在传入的factory上执行join操作
            resultSelector);
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> join( // 基于键相等性和指定的比较器将两个序列的元素进行关联（内连接）
      final Queryable<T> source, final Enumerable<TInner> inner, // 外部序列和内部序列
      final FunctionExpression<Function1<T, TKey>> outerKeySelector, // 外部键选择器函数，从外部元素中提取键
      final FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 内部键选择器函数，从内部元素中提取键
      final FunctionExpression<Function2<T, TInner, TResult>> resultSelector, // 结果选择器函数，接受匹配的外部和内部元素，返回结果
      final EqualityComparer<TKey> comparer) { // 相等比较器，用于比较键的相等性
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的join操作
        factory.join(source, inner, outerKeySelector, innerKeySelector, // 在传入的factory上执行带比较器的join操作
            resultSelector, comparer);
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public T last(final Queryable<T> source) { // 返回序列中的最后一个元素
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的last操作
        factory.last(source); // 在传入的factory上执行last操作
      }
    }.single(); // 调用single方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public T last(final Queryable<T> source, // 返回序列中满足指定条件的最后一个元素
      final FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试每个元素是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的last操作
        factory.last(source, predicate); // 在传入的factory上执行带谓词的last操作
      }
    }.single(); // 调用single方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public T lastOrDefault(final Queryable<T> source) { // 返回序列中的最后一个元素，如果序列为空则返回默认值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的lastOrDefault操作
        factory.lastOrDefault(source); // 在传入的factory上执行lastOrDefault操作
      }
    }.single(); // 调用single方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public T lastOrDefault(final Queryable<T> source, // 返回序列中满足指定条件的最后一个元素，如果没有找到则返回默认值
      final FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试每个元素是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的lastOrDefault操作
        factory.lastOrDefault(source, predicate); // 在传入的factory上执行带谓词的lastOrDefault操作
      }
    }.single(); // 调用single方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public long longCount(final Queryable<T> source) { // 返回序列中的元素数量，返回类型为long
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的longCount操作
        factory.longCount(source); // 在传入的factory上执行longCount操作
      }
    }.<Long>castSingle(); // 调用castSingle方法获取单个Long结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public long longCount(final Queryable<T> source, // 返回序列中满足指定条件的元素数量，返回类型为long
      final FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试每个元素是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的longCount操作
        factory.longCount(source, predicate); // 在传入的factory上执行带谓词的longCount操作
      }
    }.longCount(); // 调用longCount方法获取单个Long结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public T max(final Queryable<T> source) { // 返回序列中的最大值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的max操作
        factory.max(source); // 在传入的factory上执行max操作
      }
    }.castSingle(); // 调用castSingle方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TResult extends Comparable<TResult>> TResult max( // 根据指定的选择器函数返回序列中的最大值
      final Queryable<T> source, // 源序列
      final FunctionExpression<Function1<T, TResult>> selector) { // 选择器函数，从元素中提取要比较的值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的max操作
        factory.max(source, selector); // 在传入的factory上执行带选择器的max操作
      }
    }.castSingle(); // 调用castSingle方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public T min(final Queryable<T> source) { // 返回序列中的最小值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的min操作
        factory.min(source); // 在传入的factory上执行min操作
      }
    }.castSingle(); // 调用castSingle方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TResult extends Comparable<TResult>> TResult min( // 根据指定的选择器函数返回序列中的最小值
      final Queryable<T> source, // 源序列
      final FunctionExpression<Function1<T, TResult>> selector) { // 选择器函数，从元素中提取要比较的值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的min操作
        factory.min(source, selector); // 在传入的factory上执行带选择器的min操作
      }
    }.castSingle(); // 调用castSingle方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TResult> Queryable<TResult> ofType(final Queryable<T> source, // 根据指定类型筛选序列中的元素
      final Class<TResult> clazz) { // 目标类型，只保留该类型或其子类型的元素
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的ofType操作
        factory.ofType(source, clazz); // 在传入的factory上执行ofType操作
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TKey extends Comparable> OrderedQueryable<T> orderBy( // 根据键对序列中的元素进行升序排序
      final Queryable<T> source, // 源序列
      final FunctionExpression<Function1<T, TKey>> keySelector) { // 键选择器函数，从元素中提取用于排序的键
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的orderBy操作
        factory.orderBy(source, keySelector); // 在传入的factory上执行orderBy操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public <TKey> OrderedQueryable<T> orderBy(final Queryable<T> source, // 使用指定的比较器根据键对序列中的元素进行升序排序
      final FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数，从元素中提取用于排序的键
      final Comparator<TKey> comparator) { // 比较器，用于比较键的大小
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的orderBy操作
        factory.orderBy(source, keySelector, comparator); // 在传入的factory上执行带比较器的orderBy操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public <TKey extends Comparable> OrderedQueryable<T> orderByDescending( // 根据键对序列中的元素进行降序排序
      final Queryable<T> source, // 源序列
      final FunctionExpression<Function1<T, TKey>> keySelector) { // 键选择器函数，从元素中提取用于排序的键
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的orderByDescending操作
        factory.orderByDescending(source, keySelector); // 在传入的factory上执行orderByDescending操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public <TKey> OrderedQueryable<T> orderByDescending(final Queryable<T> source, // 使用指定的比较器根据键对序列中的元素进行降序排序
      final FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数，从元素中提取用于排序的键
      final Comparator<TKey> comparator) { // 比较器，用于比较键的大小
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的orderByDescending操作
        factory.orderByDescending(source, keySelector, comparator); // 在传入的factory上执行带比较器的orderByDescending操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public Queryable<T> reverse(final Queryable<T> source) { // 反转序列中元素的顺序
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的reverse操作
        factory.reverse(source); // 在传入的factory上执行reverse操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public <TResult> Queryable<TResult> select(final Queryable<T> source, // 将序列中的每个元素投影到新形式
      final FunctionExpression<Function1<T, TResult>> selector) { // 选择器函数，将元素转换为新形式
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的select操作
        factory.select(source, selector); // 在传入的factory上执行select操作
      }

      @Override public Type getElementType() { // 重写getElementType方法，返回结果元素的类型
        return requireNonNull(selector.body, "selector.body").type; // 从选择器的表达式体中获取类型
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TResult> Queryable<TResult> selectN(final Queryable<T> source, // 通过合并元素的索引将序列中的每个元素投影到新形式
      final FunctionExpression<Function2<T, Integer, TResult>> selector) { // 选择器函数，接受元素和索引，返回新形式
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的selectN操作
        factory.selectN(source, selector); // 在传入的factory上执行selectN操作
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TResult> Queryable<TResult> selectMany(final Queryable<T> source, // 将序列的每个元素投影到一个序列，并将结果序列合并为一个序列
      final FunctionExpression<Function1<T, Enumerable<TResult>>> selector) { // 选择器函数，将元素转换为一个序列
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的selectMany操作
        factory.selectMany(source, selector); // 在传入的factory上执行selectMany操作
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TResult> Queryable<TResult> selectManyN(final Queryable<T> source, // 通过合并元素的索引将序列的每个元素投影到一个序列，并将结果序列合并为一个序列
      final FunctionExpression<Function2<T, Integer, Enumerable<TResult>>> // 选择器函数，接受元素和索引，返回一个序列
        selector) {
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的selectManyN操作
        factory.selectManyN(source, selector); // 在传入的factory上执行selectManyN操作
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public <TCollection, TResult> Queryable<TResult> selectMany( // 将序列的每个元素投影到一个序列，对每个中间序列的每个元素调用结果选择器函数，并将结果合并为一个序列

        final Queryable<T> source, // 源序列

        final FunctionExpression<Function2<T, Integer, Enumerable<TCollection>>> // 集合选择器函数，接受元素和索引，返回一个序列

          collectionSelector,

        final FunctionExpression<Function2<T, TCollection, TResult>> // 结果选择器函数，接受源元素和集合元素，返回结果

          resultSelector) {

      return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象

        @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的selectMany操作

  

          factory.selectMany(source, collectionSelector, resultSelector); // 在传入的factory上执行selectMany操作

        }

      }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0

    }

  @Override public <TCollection, TResult> Queryable<TResult> selectManyN( // 通过合并元素的索引将序列的每个元素投影到一个序列，对每个中间序列的每个元素调用结果选择器函数，并将结果合并为一个序列
      final Queryable<T> source, // 源序列
      final FunctionExpression<Function1<T, Enumerable<TCollection>>> // 集合选择器函数，接受元素，返回一个序列
        collectionSelector,
      final FunctionExpression<Function2<T, TCollection, TResult>> // 结果选择器函数，接受源元素和集合元素，返回结果
        resultSelector) {
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的selectManyN操作
        factory.selectManyN(source, collectionSelector, resultSelector); // 在传入的factory上执行selectManyN操作
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }

  @Override public boolean sequenceEqual(final Queryable<T> source, // 通过使用默认的相等比较器比较元素，确定两个序列是否相等
      final Enumerable<T> enumerable) { // 要比较的第二个序列
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的sequenceEqual操作
        factory.sequenceEqual(source, enumerable); // 在传入的factory上执行sequenceEqual操作
      }
    }.<Boolean>castSingle(); // 调用castSingle方法获取单个布尔结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public boolean sequenceEqual(final Queryable<T> source, // 通过使用指定的相等比较器比较元素，确定两个序列是否相等
      final Enumerable<T> enumerable, final EqualityComparer<T> comparer) { // 要比较的第二个序列；相等比较器
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的sequenceEqual操作
        factory.sequenceEqual(source, enumerable, comparer); // 在传入的factory上执行带比较器的sequenceEqual操作
      }
    }.<Boolean>castSingle(); // 调用castSingle方法获取单个布尔结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public T single(final Queryable<T> source) { // 返回序列中的唯一元素，如果序列包含多个元素则抛出异常
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的single操作
        factory.single(source); // 在传入的factory上执行single操作
      }
    }.single(); // 调用single方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public T single(final Queryable<T> source, // 返回序列中满足指定条件的唯一元素，如果没有找到或找到多个则抛出异常
      final FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试每个元素是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的single操作
        factory.single(source, predicate); // 在传入的factory上执行带谓词的single操作
      }
    }.single(); // 调用single方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public T singleOrDefault(final Queryable<T> source) { // 返回序列中的唯一元素，如果序列为空则返回默认值，如果包含多个元素则抛出异常
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的singleOrDefault操作
        factory.singleOrDefault(source); // 在传入的factory上执行singleOrDefault操作
      }
    }.single(); // 调用single方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public T singleOrDefault(final Queryable<T> source, // 返回序列中满足指定条件的唯一元素，如果没有找到则返回默认值，如果找到多个则抛出异常
      final FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试每个元素是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的singleOrDefault操作
        factory.singleOrDefault(source, predicate); // 在传入的factory上执行带谓词的singleOrDefault操作
      }
    }.single(); // 调用single方法获取单个结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public Queryable<T> skip(final Queryable<T> source, final int count) { // 跳过序列中指定数量的元素，然后返回剩余的元素
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的skip操作
        factory.skip(source, count); // 在传入的factory上执行skip操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public Queryable<T> skipWhile(final Queryable<T> source, // 只要满足指定条件，就跳过序列中的元素，然后返回剩余的元素
      final FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试每个元素是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的skipWhile操作
        factory.skipWhile(source, predicate); // 在传入的factory上执行skipWhile操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public Queryable<T> skipWhileN(final Queryable<T> source, // 只要满足指定条件，就跳过序列中的元素（使用元素的索引），然后返回剩余的元素
      final FunctionExpression<Predicate2<T, Integer>> predicate) { // 谓词函数，接受元素和索引，用于测试是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的skipWhileN操作
        factory.skipWhileN(source, predicate); // 在传入的factory上执行skipWhileN操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public BigDecimal sumBigDecimal(final Queryable<T> source, // 计算序列中BigDecimal类型元素的总和
      final FunctionExpression<BigDecimalFunction1<T>> selector) { // 选择器函数，从元素中提取BigDecimal值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的sumBigDecimal操作
        factory.sumBigDecimal(source, selector); // 在传入的factory上执行sumBigDecimal操作
      }
    }.castSingle(); // 调用castSingle方法获取单个BigDecimal结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public BigDecimal sumNullableBigDecimal(final Queryable<T> source, // 计算序列中可空BigDecimal类型元素的总和
      final FunctionExpression<NullableBigDecimalFunction1<T>> selector) { // 选择器函数，从元素中提取可空BigDecimal值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的sumNullableBigDecimal操作
        factory.sumNullableBigDecimal(source, selector); // 在传入的factory上执行sumNullableBigDecimal操作
      }
    }.castSingle(); // 调用castSingle方法获取单个BigDecimal结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public double sumDouble(final Queryable<T> source, // 计算序列中double类型元素的总和
      final FunctionExpression<DoubleFunction1<T>> selector) { // 选择器函数，从元素中提取double值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的sumDouble操作
        factory.sumDouble(source, selector); // 在传入的factory上执行sumDouble操作
      }
    }.<Double>castSingle(); // 调用castSingle方法获取单个Double结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public Double sumNullableDouble(final Queryable<T> source, // 计算序列中可空Double类型元素的总和
      final FunctionExpression<NullableDoubleFunction1<T>> selector) { // 选择器函数，从元素中提取可空Double值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的sumNullableDouble操作
        factory.sumNullableDouble(source, selector); // 在传入的factory上执行sumNullableDouble操作
      }
    }.<Double>castSingle(); // 调用castSingle方法获取单个Double结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public int sumInteger(final Queryable<T> source, // 计算序列中int类型元素的总和
      final FunctionExpression<IntegerFunction1<T>> selector) { // 选择器函数，从元素中提取int值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的sumInteger操作
        factory.sumInteger(source, selector); // 在传入的factory上执行sumInteger操作
      }
    }.<Integer>castSingle(); // 调用castSingle方法获取单个Integer结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public Integer sumNullableInteger(final Queryable<T> source, // 计算序列中可空Integer类型元素的总和
      final FunctionExpression<NullableIntegerFunction1<T>> selector) { // 选择器函数，从元素中提取可空Integer值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的sumNullableInteger操作
        factory.sumNullableInteger(source, selector); // 在传入的factory上执行sumNullableInteger操作
      }
    }.<Integer>castSingle(); // 调用castSingle方法获取单个Integer结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public long sumLong(final Queryable<T> source, // 计算序列中long类型元素的总和
      final FunctionExpression<LongFunction1<T>> selector) { // 选择器函数，从元素中提取long值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的sumLong操作
        factory.sumLong(source, selector); // 在传入的factory上执行sumLong操作
      }
    }.<Long>castSingle(); // 调用castSingle方法获取单个Long结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public Long sumNullableLong(final Queryable<T> source, // 计算序列中可空Long类型元素的总和
      final FunctionExpression<NullableLongFunction1<T>> selector) { // 选择器函数，从元素中提取可空Long值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的sumNullableLong操作
        factory.sumNullableLong(source, selector); // 在传入的factory上执行sumNullableLong操作
      }
    }.<Long>castSingle(); // 调用castSingle方法获取单个Long结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public float sumFloat(final Queryable<T> source, // 计算序列中float类型元素的总和
      final FunctionExpression<FloatFunction1<T>> selector) { // 选择器函数，从元素中提取float值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的sumFloat操作
        factory.sumFloat(source, selector); // 在传入的factory上执行sumFloat操作
      }
    }.<Float>castSingle(); // 调用castSingle方法获取单个Float结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public Float sumNullableFloat(final Queryable<T> source, // 计算序列中可空Float类型元素的总和
      final FunctionExpression<NullableFloatFunction1<T>> selector) { // 选择器函数，从元素中提取可空Float值
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的sumNullableFloat操作
        factory.sumNullableFloat(source, selector); // 在传入的factory上执行sumNullableFloat操作
      }
    }.<Float>castSingle(); // 调用castSingle方法获取单个Float结果值 // CHECKSTYLE: IGNORE 0
  }

  @Override public Queryable<T> take(final Queryable<T> source, final int count) { // 从序列开头返回指定数量的元素
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的take操作
        factory.take(source, count); // 在传入的factory上执行take操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public Queryable<T> takeWhile(final Queryable<T> source, // 只要满足指定条件，就从序列开头返回元素
      final FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试每个元素是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的takeWhile操作
        factory.takeWhile(source, predicate); // 在传入的factory上执行takeWhile操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public Queryable<T> takeWhileN(final Queryable<T> source, // 只要满足指定条件，就从序列开头返回元素（使用元素的索引）
      final FunctionExpression<Predicate2<T, Integer>> predicate) { // 谓词函数，接受元素和索引，用于测试是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的takeWhileN操作
        factory.takeWhileN(source, predicate); // 在传入的factory上执行takeWhileN操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public <TKey extends Comparable<TKey>> OrderedQueryable<T> thenBy( // 对有序序列的元素执行后续升序排序
      final OrderedQueryable<T> source, // 有序序列
      final FunctionExpression<Function1<T, TKey>> keySelector) { // 键选择器函数，从元素中提取用于排序的键
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的thenBy操作
        factory.thenBy(source, keySelector); // 在传入的factory上执行thenBy操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public <TKey> OrderedQueryable<T> thenBy(final OrderedQueryable<T> source, // 使用指定的比较器对有序序列的元素执行后续升序排序
      final FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数，从元素中提取用于排序的键
      final Comparator<TKey> comparator) { // 比较器，用于比较键的大小
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的thenBy操作
        factory.thenBy(source, keySelector, comparator); // 在传入的factory上执行带比较器的thenBy操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public <TKey extends Comparable<TKey>> OrderedQueryable<T> thenByDescending( // 对有序序列的元素执行后续降序排序
      final OrderedQueryable<T> source, // 有序序列
      final FunctionExpression<Function1<T, TKey>> keySelector) { // 键选择器函数，从元素中提取用于排序的键
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的thenByDescending操作
        factory.thenByDescending(source, keySelector); // 在传入的factory上执行thenByDescending操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public <TKey> OrderedQueryable<T> thenByDescending( // 使用指定的比较器对有序序列的元素执行后续降序排序
      final OrderedQueryable<T> source, // 有序序列
      final FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数，从元素中提取用于排序的键
      final Comparator<TKey> comparator) { // 比较器，用于比较键的大小
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的thenByDescending操作
        factory.thenByDescending(source, keySelector, comparator); // 在传入的factory上执行带比较器的thenByDescending操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public Queryable<T> union(final Queryable<T> source, // 返回两个序列的并集（包含任一序列中的元素，去除重复项）
      final Enumerable<T> source1) { // 第二个序列
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的union操作
        factory.union(source, source1); // 在传入的factory上执行union操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public Queryable<T> union(final Queryable<T> source, // 使用指定的相等比较器返回两个序列的并集
      final Enumerable<T> source1, final EqualityComparer<T> comparer) { // 第二个序列；相等比较器
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的union操作
        factory.union(source, source1, comparer); // 在传入的factory上执行带比较器的union操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public Queryable<T> where(final Queryable<T> source, // 根据谓词筛选序列中的元素
      final FunctionExpression<? extends Predicate1<T>> predicate) { // 谓词函数，用于测试每个元素是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的where操作
        factory.where(source, predicate); // 在传入的factory上执行where操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public Queryable<T> whereN(final Queryable<T> source, // 根据谓词筛选序列中的元素（使用元素的索引）
      final FunctionExpression<? extends Predicate2<T, Integer>> predicate) { // 谓词函数，接受元素和索引，用于测试是否满足条件
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的whereN操作
        factory.whereN(source, predicate); // 在传入的factory上执行whereN操作
      }
    }; // 返回NonLeafReplayableQueryable对象
  }

  @Override public <T1, TResult> Queryable<TResult> zip(final Queryable<T> source, // 将两个序列的元素合并为一个序列，使用指定的函数对每个元素对进行投影
      final Enumerable<T1> source1, // 第二个序列
      final FunctionExpression<Function2<T, T1, TResult>> resultSelector) { // 结果选择器函数，接受两个序列的元素，返回结果
    return new NonLeafReplayableQueryable<T>(source) { // 创建一个非叶子节点的可重放查询对象
      @Override public void replay(QueryableFactory<T> factory) { // 重放方法，当调用时会执行实际的zip操作
        factory.zip(source, source1, resultSelector); // 在传入的factory上执行zip操作
      }
    }.castQueryable(); // 调用castQueryable方法获取Queryable结果 // CHECKSTYLE: IGNORE 0
  }
} // 类结束
