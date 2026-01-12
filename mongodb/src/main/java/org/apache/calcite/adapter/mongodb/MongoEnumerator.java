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
package org.apache.calcite.adapter.mongodb; // MongoDB适配器包，包含MongoDB相关的适配器实现

import org.apache.calcite.avatica.util.ByteString; // Avatica工具类，用于处理字节数组
import org.apache.calcite.avatica.util.DateTimeUtils; // Avatica日期时间工具类，提供日期时间常量和转换方法
import org.apache.calcite.linq4j.Enumerator; // LINQ4J枚举器接口，用于遍历数据集合
import org.apache.calcite.linq4j.function.Function1; // LINQ4J函数接口，表示单参数函数
import org.apache.calcite.linq4j.tree.Primitive; // LINQ4J原始类型工具类，用于处理基本类型的转换

import com.mongodb.client.MongoCursor; // MongoDB客户端游标接口，用于遍历查询结果

import org.bson.BsonTimestamp; // BSON时间戳类型，MongoDB的时间戳表示
import org.bson.Document; // BSON文档类型，MongoDB的基本数据结构
import org.bson.types.Binary; // BSON二进制类型，用于存储二进制数据
import org.bson.types.Decimal128; // BSON 128位十进制类型，高精度数值类型
import org.checkerframework.checker.nullness.qual.Nullable; // CheckerFramework注解，标记可空类型

import java.math.BigDecimal; // Java高精度十进制类型
import java.util.Date; // Java日期类型
import java.util.Iterator; // Java迭代器接口
import java.util.List; // Java列表接口
import java.util.Locale; // Java本地化类，用于区域相关的格式化
import java.util.Map; // Java映射接口

import static java.lang.String.format; // 静态导入String格式化方法

/** Enumerator that reads from a MongoDB collection. */ // 类注释：MongoDB集合枚举器，用于从MongoDB集合中读取数据
class MongoEnumerator implements Enumerator<Object> { // MongoEnumerator类实现Enumerator接口，用于枚举MongoDB查询结果
  private final Iterator<Document> cursor; // MongoDB文档迭代器，用于遍历查询结果集，final表示初始化后不可变
  private final Function1<Document, Object> getter; // 字段提取函数，用于将Document转换为目标对象，final表示初始化后不可变
  private @Nullable Object current; // 当前游标位置的数据对象，@Nullable表示可能为null，用于存储当前遍历到的数据

  /** Creates a MongoEnumerator. */ // 构造方法注释：创建MongoEnumerator实例
  /** */ // JavaDoc分隔符
  /** @param cursor Mongo iterator (usually a {@link com.mongodb.DBCursor}) */ // 参数说明：MongoDB迭代器，通常是DBCursor或MongoCursor，用于遍历查询结果
  /** @param getter Converts an object into a list of fields */ // 参数说明：转换函数，将Document对象转换为字段列表或单个对象
  MongoEnumerator(Iterator<Document> cursor, // 构造方法参数：MongoDB文档迭代器
      Function1<Document, Object> getter) { // 构造方法参数：字段提取函数，将Document转换为目标类型
    this.cursor = cursor; // 将传入的迭代器赋值给成员变量cursor
    this.getter = getter; // 将传入的转换函数赋值给成员变量getter
  }

  @Override public Object current() { // 重写Enumerator接口的current方法，返回当前游标位置的对象
    if (current == null) { // 检查当前对象是否为null
      throw new IllegalStateException(); // 如果为null，抛出非法状态异常，表示还未调用moveNext或已经遍历结束
    }
    return current; // 返回当前对象
  }

  @Override public boolean moveNext() { // 重写Enumerator接口的moveNext方法，移动游标到下一个位置
    try { // 使用try-catch捕获可能的异常
      if (cursor.hasNext()) { // 检查迭代器是否还有下一个元素
        Document map = cursor.next(); // 获取下一个Document对象
        current = getter.apply(map); // 使用getter函数将Document转换为目标对象并赋值给current
        return true; // 返回true表示成功移动到下一个元素
      } else { // 如果没有下一个元素
        current = null; // 将current置为null，表示遍历结束
        return false; // 返回false表示没有更多元素
      }
    } catch (Exception e) { // 捕获所有异常
      throw new RuntimeException(e); // 将异常包装为运行时异常抛出
    }
  }

  @Override public void reset() { // 重写Enumerator接口的reset方法，重置游标位置
    throw new UnsupportedOperationException(); // 抛出不支持操作异常，MongoDB游标不支持重置
  }

  @Override public void close() { // 重写Enumerator接口的close方法，关闭资源
    if (cursor instanceof MongoCursor) { // 检查迭代器是否是MongoCursor类型
      ((MongoCursor) cursor).close(); // 如果是MongoCursor，调用close方法关闭游标，释放资源
    }
    // AggregationOutput implements Iterator but not DBCursor. There is no
    // available close() method -- apparently there is no open resource. // 注释说明：聚合输出实现Iterator但不是DBCursor，没有可用的close方法，似乎没有打开的资源需要关闭
  }

  static Function1<Document, Map> mapGetter() { // 静态工厂方法：创建一个将Document直接转换为Map的函数
    return a0 -> (Map) a0; // 返回一个lambda表达式，直接将Document强转为Map返回
  }

  /** Returns a function that projects a single field. */ // 方法注释：返回一个投影单个字段的函数
  static Function1<Document, Object> singletonGetter(final String fieldName, // 静态工厂方法参数：字段名，final表示不可变
      final Class fieldClass) { // 静态工厂方法参数：字段类型，final表示不可变
    return a0 -> convert(fieldName, a0.get(fieldName), fieldClass); // 返回lambda表达式，从Document中获取指定字段并转换为指定类型
  }

  /** Returns a function that projects fields. */ // 方法注释：返回一个投影多个字段的函数
  /** */ // JavaDoc分隔符
  /** @param fields List of fields to project; or null to return map */ // 参数说明：要投影的字段列表，包含字段名和类型的映射
  static Function1<Document, Object[]> listGetter( // 静态工厂方法：创建投影多个字段的函数
      final List<Map.Entry<String, Class>> fields) { // 参数：字段列表，每个元素是字段名和类型的键值对
    return a0 -> { // 返回lambda表达式，接受Document参数
      Object[] objects = new Object[fields.size()]; // 创建对象数组，大小等于字段数量
      for (int i = 0; i < fields.size(); i++) { // 遍历所有字段
        final Map.Entry<String, Class> field = fields.get(i); // 获取第i个字段的键值对
        final String name = field.getKey(); // 获取字段名
        objects[i] = convert(name, a0.get(name), field.getValue()); // 从Document中获取字段值并转换为指定类型，存入数组
      }
      return objects; // 返回包含所有字段值的对象数组
    };
  }

  static Function1<Document, Object> getter( // 静态工厂方法：根据字段列表创建合适的getter函数
      List<Map.Entry<String, Class>> fields) { // 参数：字段列表，可能为null
    //noinspection unchecked // 抑制未检查类型转换警告
    return fields == null // 如果字段列表为null
        ? (Function1) mapGetter() // 返回mapGetter，将Document直接转换为Map
        : fields.size() == 1 // 如果只有一个字段
            ? singletonGetter(fields.get(0).getKey(), fields.get(0).getValue()) // 返回singletonGetter，投影单个字段
            : (Function1) listGetter(fields); // 否则返回listGetter，投影多个字段
  }

  /**
   * Converts the given object to a specific runtime type based on the provided class. */ // 方法注释：将给定对象转换为指定的运行时类型
  /** */ // JavaDoc分隔符
  /** @param fieldName The name of the field being processed, used for error reporting if */ // 参数说明：正在处理的字段名，用于转换失败时的错误报告
  /**                  conversion fails. */ // 参数说明续
  /** @param o The object to be converted. If `null`, the method returns `null` immediately. */ // 参数说明：要转换的对象，如果为null则立即返回null
  /** @param clazz The target class to which the object `o` should be converted. */ // 参数说明：目标类，对象o应该转换成的类型
  /** @return The converted object as an instance of the specified `clazz`, or `null` if `o` is */ // 返回值说明：转换后的对象，是指定clazz的实例，如果o为null则返回null
  /** `null`. */ // 返回值说明续
  /** */ // JavaDoc分隔符
  /** @throws IllegalArgumentException if the object `o` cannot be converted to the desired */ // 异常说明：如果对象o无法转换为目标clazz类型时抛出
  /** `clazz` type, including a message indicating the field name, expected data type, and the */ // 异常说明续：包含字段名、预期数据类型和无效值的错误消息
  /** invalid value. */ // 异常说明续
  /** */ // JavaDoc分隔符
  /** <h3>Conversion Details:</h3> */ // 转换详情标题
  /** */ // JavaDoc分隔符
  /** <p>If the target type is one of the following, the method performs specific conversions: */ // 如果目标类型是以下类型之一，方法执行特定转换
  /** <ul> */ // 无序列表开始
  /**   <li>`Long`: Converts a `Date` or `BsonTimestamp` object into the respective epoch time */ // Long类型：将Date或BsonTimestamp对象转换为对应的纪元时间（毫秒）
  /**   (milliseconds).</li> */ // Long类型转换说明续
  /**   <li>`BigDecimal`: Converts a `Decimal128` object into a `BigDecimal` instance.</li> */ // BigDecimal类型：将Decimal128对象转换为BigDecimal实例
  /**   <li>`String`: Converts arrays to string and uses `String.valueOf(o)` for other objects.</li> */ // String类型：将数组转换为字符串，其他对象使用String.valueOf(o)
  /**   <li>`ByteString`: Converts a `Binary` object into a `ByteString` instance.</li> */ // ByteString类型：将Binary对象转换为ByteString实例
  /**   <li>`Primitive or Boxed Primitive`: */ // 原始类型或包装类型
  /**       <ul> */ // 嵌套无序列表开始
  /**         <li>If the object is a `String`, it will be converted to the corresponding boxed */ // 如果对象是String，使用Primitive.parse转换为对应的包装原始类型
  /**         primitive */ // 转换说明续
  /**             using {@link Primitive#parse}.</li> */ // 转换方法引用
  /**         <li>If the object is numeric, it will be converted to the boxed primitive type using */ // 如果对象是数值，使用Primitive.number转换为包装原始类型
  /**             {@link Primitive#number}.</li> */ // 转换方法引用
  /**       </ul> */ // 嵌套无序列表结束
  /**   </li> */ // 列表项结束
  /** </ul> */ // 无序列表结束
  /** */ // JavaDoc分隔符
  @SuppressWarnings("JavaUtilDate") // 抑制JavaUtilDate警告，因为这里使用了Date类型
  private static Object convert(String fieldName, Object o, Class clazz) { // 私有静态方法：类型转换方法，将MongoDB类型转换为Java类型
    if (o == null) { // 检查对象是否为null
      return null; // 如果为null，直接返回null
    }
    Primitive primitive = Primitive.of(clazz); // 获取目标类对应的原始类型枚举
    if (primitive != null) { // 如果是原始类型（如int、double等）
      clazz = primitive.boxClass; // 将目标类转换为对应的包装类（如Integer、Double等）
    } else { // 如果不是原始类型
      primitive = Primitive.ofBox(clazz); // 尝试获取包装类对应的原始类型枚举
    }
    if (clazz.isInstance(o)) { // 检查对象o是否已经是目标类型的实例
      return o; // 如果类型匹配，直接返回对象o，无需转换
    }

    if (clazz == Long.class) { // 如果目标类型是Long
      if (o instanceof Date) { // 如果对象是Date类型
        return ((Date) o).getTime(); // 返回Date的毫秒时间戳
      } else if (o instanceof BsonTimestamp) { // 如果对象是BsonTimestamp类型
        return ((BsonTimestamp) o).getTime() * DateTimeUtils.MILLIS_PER_SECOND; // 返回时间戳的秒数转换为毫秒数
      }
    } else if (clazz == BigDecimal.class) { // 如果目标类型是BigDecimal
      if (o instanceof Decimal128) { // 如果对象是Decimal128类型
        return new BigDecimal(((Decimal128) o).toString()); // 将Decimal128转换为字符串，再构造BigDecimal
      }
    } else if (clazz == String.class) { // 如果目标类型是String
      if (o.getClass().isArray()) { // 如果对象是数组
        return Primitive.OTHER.arrayToString(o); // 使用Primitive工具类将数组转换为字符串
      } else { // 如果不是数组
        return String.valueOf(o); // 使用String.valueOf将对象转换为字符串
      }
    } else if (clazz == ByteString.class) { // 如果目标类型是ByteString
      if (o instanceof Binary) { // 如果对象是Binary类型
        return new ByteString(((Binary) o).getData()); // 将Binary的字节数组包装为ByteString对象
      }
    }

    if (primitive != null) { // 如果原始类型枚举不为null（表示目标是原始类型或包装类型）
      if (o instanceof String) { // 如果对象是String类型
        return primitive.parse((String) o); // 使用原始类型的parse方法将字符串解析为目标类型
      } else if (o instanceof Number) { // 如果对象是Number类型
        return primitive.number((Number) o); // 使用原始类型的number方法将数值转换为目标类型
      } else if (o instanceof Date) { // 如果对象是Date类型
        return primitive.number(((Date) o).getTime() / DateTimeUtils.MILLIS_PER_DAY); // 将Date的毫秒时间戳转换为天数，再转换为目标类型
      }
    }

    throw new IllegalArgumentException( // 如果以上所有转换都不适用，抛出非法参数异常
        format(Locale.ROOT, "Invalid field: '%s'. The dataType '%s' is invalid for '%s'.", // 格式化错误消息
            fieldName, // 字段名
            clazz.getSimpleName(), o)); // 目标类型简单名称和实际值
  }
}
