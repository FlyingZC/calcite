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

import org.apache.calcite.linq4j.function.Function2;

import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of {@link Lookup} that uses an underlying map.
 * Lookup接口的实现类，使用底层的Map来存储键值对，其中每个键对应一个值的列表
 * 这个类实现了Lookup接口，用于LINQ风格的分组查询操作
 *
 * @param <K> Key type - 键的类型
 * @param <V> Value type - 值的类型
 */
class LookupImpl<K, V> extends AbstractEnumerable<Grouping<K, V>>
    implements Lookup<K, V> {
  private final Map<K, List<V>> map; // 底层Map，存储键到值列表的映射关系，每个键对应一个包含多个值的列表

  /**
   * Creates a MultiMapImpl.
   * 构造方法，创建一个LookupImpl实例
   *
   * @param map Underlying map - 底层的Map对象，存储键到值列表的映射
   */
  LookupImpl(Map<K, List<V>> map) {
    this.map = map; // 将传入的Map赋值给成员变量map
  }

  @Override public Enumerator<Grouping<K, V>> enumerator() { // 重写enumerator方法，返回一个枚举器用于遍历分组
    return new Enumerator<Grouping<K, V>>() { // 创建一个匿名枚举器类
      final Enumerator<Entry<K, List<V>>> enumerator = // 创建底层Map的entrySet枚举器
          Linq4j.enumerator(map.entrySet()); // 使用Linq4j工具方法将entrySet转换为枚举器

      @Override public Grouping<K, V> current() { // 获取当前分组元素
        final Entry<K, List<V>> keyAndList = enumerator.current(); // 从底层枚举器获取当前键值对
        return new GroupingImpl<>(keyAndList.getKey(), // 创建GroupingImpl对象，将键和值列表封装成分组
            keyAndList.getValue());
      }

      @Override public boolean moveNext() { // 移动到下一个元素
        return enumerator.moveNext(); // 委托给底层枚举器的moveNext方法
      }

      @Override public void reset() { // 重置枚举器到初始状态
        enumerator.reset(); // 委托给底层枚举器的reset方法
      }

      @Override public void close() { // 关闭枚举器，释放资源
        enumerator.close(); // 委托给底层枚举器的close方法
      }
    };
  }

  // Map methods - 以下是Map接口的方法实现

  @Override public int size() { // 获取Lookup中键值对的数量
    return map.size(); // 返回底层Map的大小
  }

  @Override public boolean isEmpty() { // 判断Lookup是否为空
    return map.isEmpty(); // 返回底层Map是否为空
  }

  @SuppressWarnings("contracts.conditional.postcondition.not.satisfied")
  @Override public boolean containsKey(@Nullable Object key) { // 判断是否包含指定的键
    return map.containsKey(key); // 委托给底层Map的containsKey方法
  }

  @Override public boolean containsValue(@Nullable Object value) { // 判断是否包含指定的值列表
    @SuppressWarnings("unchecked")
    List<V> list = (List<V>) value; // 将传入的值强制转换为List类型
    return map.containsValue(list); // 委托给底层Map的containsValue方法
  }

  @Override public @Nullable Enumerable<V> get(@Nullable Object key) { // 根据键获取对应的值列表
    final List<V> list = map.get(key); // 从底层Map获取值列表
    return list == null ? null : Linq4j.asEnumerable(list); // 如果列表为空返回null，否则转换为Enumerable返回
  }

  @SuppressWarnings("contracts.postcondition.not.satisfied")
  @Override public @Nullable Enumerable<V> put(K key, Enumerable<V> value) { // 向Lookup中添加或更新键值对
    final List<V> list = map.put(key, value.toList()); // 将Enumerable转换为List并存入底层Map
    return list == null ? null : Linq4j.asEnumerable(list); // 返回旧的值列表，如果不存在则返回null
  }

  @Override public @Nullable Enumerable<V> remove(@Nullable Object key) { // 根据键移除对应的值列表
    final List<V> list = map.remove(key); // 从底层Map移除键值对
    return list == null ? null : Linq4j.asEnumerable(list); // 返回被移除的值列表，如果不存在则返回null
  }

  @Override public void putAll(Map<? extends K, ? extends Enumerable<V>> m) { // 将另一个Map的所有键值对添加到当前Lookup
    for (Entry<? extends K, ? extends Enumerable<V>> entry : m.entrySet()) { // 遍历传入Map的所有条目
      map.put(entry.getKey(), entry.getValue().toList()); // 将每个条目的值转换为List并存入底层Map
    }
  }

  @Override public void clear() { // 清空Lookup中的所有键值对
    map.clear(); // 委托给底层Map的clear方法
  }

  @SuppressWarnings("return.type.incompatible")
  @Override public Set<@KeyFor("this") K> keySet() { // 获取Lookup中所有键的集合
    return map.keySet(); // 返回底层Map的键集合
  }

  @Override public Collection<Enumerable<V>> values() { // 获取Lookup中所有值列表的集合
    final Collection<List<V>> lists = map.values(); // 从底层Map获取所有值列表
    return new AbstractCollection<Enumerable<V>>() { // 创建一个抽象集合，将List包装成Enumerable
      @Override public Iterator<Enumerable<V>> iterator() { // 创建迭代器
        return new Iterator<Enumerable<V>>() { // 创建匿名迭代器
          final Iterator<List<V>> iterator = lists.iterator(); // 获取底层List集合的迭代器

          @Override public boolean hasNext() { // 判断是否还有下一个元素
            return iterator.hasNext(); // 委托给底层迭代器的hasNext方法
          }

          @Override public Enumerable<V> next() { // 获取下一个元素
            return Linq4j.asEnumerable(iterator.next()); // 将List转换为Enumerable返回
          }

          @Override public void remove() { // 移除当前元素
            iterator.remove(); // 委托给底层迭代器的remove方法
          }
        };
      }

      @Override public int size() { // 获取集合大小
        return lists.size(); // 返回底层List集合的大小
      }
    };
  }

  @SuppressWarnings("return.type.incompatible")
  @Override public Set<Entry<@KeyFor({"this"}) K, Enumerable<V>>> entrySet() { // 获取Lookup中所有键值对的集合
    final Set<Entry<@KeyFor("map") K, List<V>>> entries = map.entrySet(); // 从底层Map获取所有键值对条目
    return new AbstractSet<Entry<K, Enumerable<V>>>() { // 创建一个抽象集合，将List包装成Enumerable
      @Override public Iterator<Entry<K, Enumerable<V>>> iterator() { // 创建迭代器
        final Iterator<Entry<K, List<V>>> iterator = entries.iterator(); // 获取底层条目集合的迭代器
        return new Iterator<Entry<K, Enumerable<V>>>() { // 创建匿名迭代器
          @Override public boolean hasNext() { // 判断是否还有下一个元素
            return iterator.hasNext(); // 委托给底层迭代器的hasNext方法
          }

          @Override public Entry<K, Enumerable<V>> next() { // 获取下一个元素
            final Entry<K, List<V>> entry = iterator.next(); // 从底层迭代器获取条目
            return new AbstractMap.SimpleEntry<>(entry.getKey(), // 创建新的条目，将List转换为Enumerable
                Linq4j.asEnumerable(entry.getValue()));
          }

          @Override public void remove() { // 移除当前元素
            iterator.remove(); // 委托给底层迭代器的remove方法
          }
        };
      }

      @Override public int size() { // 获取集合大小
        return entries.size(); // 返回底层条目集合的大小
      }
    };
  }

  @Override public <TResult> Enumerable<TResult> applyResultSelector( // 应用结果选择器函数，将每个分组转换为指定类型的结果
      final Function2<K, Enumerable<V>, TResult> resultSelector) { // 结果选择器函数，接收键和值列表，返回转换后的结果
    return new AbstractEnumerable<TResult>() { // 创建一个抽象可枚举对象
      @Override public Enumerator<TResult> enumerator() { // 创建枚举器
        final Enumerator<Grouping<K, V>> groupingEnumerator = // 获取分组枚举器
            LookupImpl.this.enumerator(); // 调用当前对象的enumerator方法
        return new Enumerator<TResult>() { // 创建匿名枚举器
          @Override public TResult current() { // 获取当前元素
            final Grouping<K, V> grouping = groupingEnumerator.current(); // 从分组枚举器获取当前分组
            return resultSelector.apply(grouping.getKey(), grouping); // 应用结果选择器函数，将分组转换为结果
          }

          @Override public boolean moveNext() { // 移动到下一个元素
            return groupingEnumerator.moveNext(); // 委托给分组枚举器的moveNext方法
          }

          @Override public void reset() { // 重置枚举器到初始状态
            groupingEnumerator.reset(); // 委托给分组枚举器的reset方法
          }

          @Override public void close() { // 关闭枚举器，释放资源
            groupingEnumerator.close(); // 委托给分组枚举器的close方法
          }
        };
      }
    };
  }

  /**
   * Returns an enumerable over the values in this lookup, in map order.
   * If the map is sorted, the values will be emitted sorted by key.
   * 返回一个包含Lookup中所有值（扁平化）的可枚举对象，按照Map的顺序输出
   * 如果底层Map是有序的，那么值也会按键的顺序输出
   */
  public Enumerable<V> valuesEnumerable() { // 获取所有值的扁平化可枚举对象
    return new AbstractEnumerable<V>() { // 创建一个抽象可枚举对象
      @Override public Enumerator<V> enumerator() { // 创建枚举器
        final Enumerator<Enumerable<V>> listEnumerator = // 获取值列表的枚举器
            Linq4j.iterableEnumerator(values()); // 使用Linq4j工具方法将values()转换为枚举器
        return new Enumerator<V>() { // 创建匿名枚举器
          Enumerator<V> enumerator = Linq4j.emptyEnumerator(); // 当前值的枚举器，初始化为空枚举器

          @Override public V current() { // 获取当前值
            return enumerator.current(); // 返回当前枚举器的当前值
          }

          @Override public boolean moveNext() { // 移动到下一个值
            for (;;) { // 无限循环，直到找到下一个值或遍历完成
              if (enumerator.moveNext()) { // 尝试在当前列表中移动到下一个值
                return true; // 如果成功，返回true
              }
              enumerator.close(); // 关闭当前枚举器，释放资源
              if (!listEnumerator.moveNext()) { // 尝试移动到下一个值列表
                enumerator = Linq4j.emptyEnumerator(); // 如果没有更多列表，设置为空枚举器
                return false; // 返回false，表示遍历完成
              }
              enumerator = listEnumerator.current().enumerator(); // 获取下一个值列表的枚举器
            }
          }

          @Override public void reset() { // 重置枚举器到初始状态
            listEnumerator.reset(); // 重置列表枚举器
            enumerator = Linq4j.emptyEnumerator(); // 将值枚举器重置为空枚举器
          }

          @Override public void close() { // 关闭枚举器，释放资源
            enumerator.close(); // 关闭当前值枚举器
          }
        };
      }
    };
  }
}
