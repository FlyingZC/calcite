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
package org.apache.calcite.adapter.os; // 包声明：该类位于org.apache.calcite.adapter.os包下，属于操作系统适配器模块

import org.apache.calcite.DataContext; // 导入Calcite的数据上下文类，用于在查询执行过程中传递运行时信息
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入LINQ4J的抽象可枚举类，用于实现可枚举的数据集合
import org.apache.calcite.linq4j.Enumerable; // 导入LINQ4J的可枚举接口，表示可以遍历的数据源
import org.apache.calcite.linq4j.Enumerator; // 导入LINQ4J的枚举器接口，用于遍历数据集合
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型类，表示表或表达式的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂类，用于创建数据类型
import org.apache.calcite.schema.ScannableTable; // 导入可扫描表接口，表示可以逐行扫描的表
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义SQL标准的数据类型

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值

import java.util.NoSuchElementException; // 导入无此元素异常类，当枚举器没有更多元素时抛出

import static java.lang.Long.parseLong; // 静态导入Long类的parseLong方法，用于将字符串解析为长整型

/**
 * Table function that executes the OS "git log" command
 * to discover git commits.
 * 表函数：通过执行操作系统的"git log"命令来发现Git提交记录
 * 
 * 这个类实现了一个Calcite表函数，可以将Git仓库的提交历史作为关系表来查询
 * 它通过执行"git log --pretty=raw"命令获取提交信息，并将结果转换为Calcite可识别的表结构
 * 
 * 核心功能：
 * 1. 执行系统命令"git log --pretty=raw"获取Git提交记录
 * 2. 解析Git日志的原始格式，提取关键字段
 * 3. 将解析结果转换为Calcite的行数据格式
 * 4. 提供表结构定义，包含9个字段：commit、tree、parent、parent2、author、author_timestamp、committer、commit_timestamp、message
 * 
 * 使用场景：在SQL查询中将Git仓库作为数据源进行分析，例如统计提交数量、分析提交者行为等
 */
public class GitCommitsTableFunction { // Git提交表函数类，提供将Git提交历史转换为Calcite表的功能

  /** An example of the timestamp + offset at the end of author and committer
   * fields. */
  private static final String TS_OFF = "1500769547 -0700"; // 常量：时间戳和时区偏移的示例字符串，长度为15，用于计算字符串截取位置

  /** An example of the offset at the end of author and committer fields. */
  private static final String OFF = "-0700"; // 常量：时区偏移的示例字符串，长度为5，用于计算字符串截取位置

  private GitCommitsTableFunction() {} // 私有构造函数，防止实例化，该类只提供静态方法

  public static ScannableTable eval(boolean b) { // 静态方法：评估表函数，返回一个可扫描的表对象，参数b是布尔值（实际未使用）
    return new AbstractBaseScannableTable() { // 返回一个匿名内部类，继承自AbstractBaseScannableTable，实现可扫描表的基本功能
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，扫描表数据并返回可枚举的对象数组集合，root是数据上下文
        final Enumerable<String> enumerable = // 创建字符串类型的可枚举对象
            Processes.processLines("git", "log", "--pretty=raw"); // 调用Processes工具类执行系统命令"git log --pretty=raw"，获取Git日志的原始格式输出，每行作为一个字符串
        return new AbstractEnumerable<@Nullable Object[]>() { // 返回一个抽象可枚举对象，封装了对象数组的枚举逻辑
          @Override public Enumerator<@Nullable Object[]> enumerator() { // 重写enumerator方法，返回一个对象数组的枚举器
            final Enumerator<String> e = enumerable.enumerator(); // 从字符串可枚举对象中获取字符串枚举器，用于逐行读取Git日志
            return new Enumerator<@Nullable Object[]>() { // 返回一个匿名内部类，实现对象数组的枚举器接口
              private @Nullable Object @Nullable [] objects; // 成员变量：当前行的对象数组，用于存储解析后的Git提交信息，可能为null
              private final StringBuilder b = new StringBuilder(); // 成员变量：字符串构建器，用于构建提交消息内容

              @Override public @Nullable Object[] current() { // 重写current方法，返回当前行的对象数组
                if (objects == null) { // 如果当前对象数组为null
                  throw new NoSuchElementException(); // 抛出无此元素异常，表示没有当前元素
                }
                return objects; // 返回当前对象数组
              }

              @Override public boolean moveNext() { // 重写moveNext方法，移动到下一行数据，返回是否还有下一行
                if (!e.moveNext()) { // 尝试移动到下一行Git日志
                  objects = null; // 如果没有更多行，将当前对象设为null
                  return false; // 返回false表示没有更多数据
                }
                objects = new Object[9]; // 创建一个包含9个元素的对象数组，对应9个字段
                for (;;) { // 无限循环，用于解析Git日志的头部信息
                  final String line = e.current(); // 获取当前行的Git日志内容
                  if (line.isEmpty()) { // 如果当前行为空行
                    break; // 跳出循环，空行表示头部信息结束，下一行开始是提交消息
                  }
                  if (line.startsWith("commit ")) { // 如果行以"commit "开头
                    objects[0] = line.substring("commit ".length()); // 提取commit哈希值（40位），存储到数组第0个位置
                  } else if (line.startsWith("tree ")) { // 如果行以"tree "开头
                    objects[1] = line.substring("tree ".length()); // 提取tree哈希值（40位），存储到数组第1个位置
                  } else if (line.startsWith("parent ")) { // 如果行以"parent "开头
                    if (objects[2] == null) { // 如果第一个parent字段为空
                      objects[2] = line.substring("parent ".length()); // 提取第一个parent哈希值（40位），存储到数组第2个位置
                    } else { // 如果第一个parent字段已有值
                      objects[3] = line.substring("parent ".length()); // 提取第二个parent哈希值（40位），存储到数组第3个位置
                    }
                  } else if (line.startsWith("author ")) { // 如果行以"author "开头
                    objects[4] = // 提取作者信息（姓名和邮箱），去掉时间戳和时区偏移部分，存储到数组第4个位置
                        line.substring("author ".length(), // 从"author "之后开始截取
                            line.length() - TS_OFF.length() - 1); // 截取到时间戳和时区偏移之前的位置（减去15+1=16个字符）
                    objects[5] = // 提取作者时间戳，转换为毫秒级时间戳，存储到数组第5个位置
                        parseLong( // 将字符串解析为长整型
                            line.substring(line.length() - TS_OFF.length(), // 从时间戳和时区偏移的开始位置截取
                            line.length() - OFF.length() - 1)) * 1000; // 截取到时区偏移之前的位置（10位数字），乘以1000转换为毫秒
                  } else if (line.startsWith("committer ")) { // 如果行以"committer "开头
                    objects[6] = // 提取提交者信息（姓名和邮箱），去掉时间戳和时区偏移部分，存储到数组第6个位置
                        line.substring("committer ".length(), // 从"committer "之后开始截取
                            line.length() - TS_OFF.length() - 1); // 截取到时间戳和时区偏移之前的位置（减去15+1=16个字符）
                    objects[7] = // 提取提交者时间戳，转换为毫秒级时间戳，存储到数组第7个位置
                        parseLong( // 将字符串解析为长整型
                            line.substring(line.length() - TS_OFF.length(), // 从时间戳和时区偏移的开始位置截取
                            line.length() - OFF.length() - 1)) * 1000; // 截取到时区偏移之前的位置（10位数字），乘以1000转换为毫秒
                  }
                  if (!e.moveNext()) { // 尝试移动到下一行Git日志
                    // We have a row, and it's the last because input is empty // 我们已经有一行数据，并且这是最后一行，因为输入已经为空
                    return true; // 返回true表示成功读取到一行数据
                  }
                }
                for (;;) { // 无限循环，用于解析Git日志的提交消息部分
                  if (!e.moveNext()) { // 尝试移动到下一行Git日志
                    // We have a row, and it's the last because input is empty // 我们已经有一行数据，并且这是最后一行，因为输入已经为空
                    objects[8] = b.toString(); // 将构建的提交消息存储到数组第8个位置
                    b.setLength(0); // 清空字符串构建器，为下一次使用做准备
                    return true; // 返回true表示成功读取到一行数据
                  }
                  final String line = e.current(); // 获取当前行的Git日志内容
                  if (line.isEmpty()) { // 如果当前行为空行
                    // We're seeing the empty line at the end of message // 我们看到的是提交消息末尾的空行
                    objects[8] = b.toString(); // 将构建的提交消息存储到数组第8个位置
                    b.setLength(0); // 清空字符串构建器，为下一次使用做准备
                    return true; // 返回true表示成功读取到一行数据
                  }
                  b.append(line.substring("    ".length())).append("\n"); // 提取提交消息内容（去掉每行开头的4个空格），并添加换行符
                }
              }

              @Override public void reset() { // 重写reset方法，重置枚举器到初始位置
                throw new UnsupportedOperationException(); // 抛出不支持操作异常，因为Git日志流不支持重置
              }

              @Override public void close() { // 重写close方法，关闭枚举器并释放资源
                e.close(); // 关闭底层字符串枚举器，释放系统资源
              }
            };
          }
        };
      }

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，返回表的数据类型定义，typeFactory是数据类型工厂
        return typeFactory.builder() // 使用类型工厂创建一个构建器
            .add("commit", SqlTypeName.CHAR, 40) // 添加第1列：commit，类型为定长字符，长度40（Git SHA-1哈希值）
            .add("tree", SqlTypeName.CHAR, 40) // 添加第2列：tree，类型为定长字符，长度40（Git tree对象哈希值）
            .add("parent", SqlTypeName.CHAR, 40) // 添加第3列：parent，类型为定长字符，长度40（第一个父提交的哈希值）
            .add("parent2", SqlTypeName.CHAR, 40) // 添加第4列：parent2，类型为定长字符，长度40（第二个父提交的哈希值，用于合并提交）
            .add("author", SqlTypeName.VARCHAR) // 添加第5列：author，类型为可变长度字符（作者姓名和邮箱）
            .add("author_timestamp", SqlTypeName.TIMESTAMP) // 添加第6列：author_timestamp，类型为时间戳（作者提交时间）
            .add("committer", SqlTypeName.VARCHAR) // 添加第7列：committer，类型为可变长度字符（提交者姓名和邮箱）
            .add("commit_timestamp", SqlTypeName.TIMESTAMP) // 添加第8列：commit_timestamp，类型为时间戳（提交者提交时间）
            .add("message", SqlTypeName.VARCHAR) // 添加第9列：message，类型为可变长度字符（提交消息内容）
            .build(); // 构建并返回关系数据类型对象
      }
    };
  }
}
