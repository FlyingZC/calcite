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
// Apache Calcite 文件系统适配器包，提供操作系统相关的表函数实现
package org.apache.calcite.adapter.os;

// 导入Calcite核心类：DataContext提供查询执行上下文，包含类型工厂等信息
import org.apache.calcite.DataContext;
// 导入Java类型工厂，用于创建Java类型系统中的类型
import org.apache.calcite.adapter.java.JavaTypeFactory;
// 导入LINQ4J抽象可枚举类，用于实现可枚举的数据集合
import org.apache.calcite.linq4j.AbstractEnumerable;
// 导入LINQ4J可枚举接口，提供类似LINQ的数据查询能力
import org.apache.calcite.linq4j.Enumerable;
// 导入LINQ4J枚举器接口，用于遍历数据集合
import org.apache.calcite.linq4j.Enumerator;
// 导入关系数据类型，表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataType;
// 导入关系数据类型工厂，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory;
// 导入可扫描表接口，表示可以被扫描的表
import org.apache.calcite.schema.ScannableTable;
// 导入SQL类型名称枚举，定义SQL标准类型
import org.apache.calcite.sql.type.SqlTypeName;
// 导入Calcite工具类，提供通用工具方法
import org.apache.calcite.util.Util;

// 导入Google Guava的不可变列表，提供线程安全的列表实现
import com.google.common.collect.ImmutableList;

// 导入CheckerFramework的可空注解，用于标记可能为null的值
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入BigDecimal类，用于高精度数值计算
import java.math.BigDecimal;
// 导入数组工具类，提供数组操作方法
import java.util.Arrays;
// 导入List接口，提供列表数据结构
import java.util.List;

// 静态导入Objects.requireNonNull方法，用于参数非空校验
import static java.util.Objects.requireNonNull;

/**
 * Table function that executes the OS "find" command to find files under a
 * particular path.
 * 表函数类，用于执行操作系统的"find"命令来查找指定路径下的文件
 * 这个类将文件系统的文件信息转换为Calcite可以查询的表结构
 * 支持Linux和Mac OS两种操作系统，通过调用系统命令获取文件元数据
 * 返回的表包含文件的访问时间、修改时间、大小、权限、用户信息等20个字段
 */
public class FilesTableFunction {

  // 常量：数值1000，用于将秒转换为毫秒（时间戳转换）
  private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000L);

  // 私有构造函数，防止实例化，这是一个工具类，所有方法都是静态的
  private FilesTableFunction() {
  }

  /**
   * Evaluates the function.
   * 评估函数，根据给定的路径创建一个可扫描的表
   * 这个方法是表函数的入口点，用于将文件系统查询转换为Calcite表
   *
   * @param path Directory in which to start the search. Typically '.'
   *            path参数：开始搜索的目录路径，通常使用'.'表示当前目录
   *            这个路径会传递给操作系统的find命令来查找文件
   * @return Table that can be inspected, planned, and evaluated
   *         返回值：一个ScannableTable实例，可以被检查、规划和执行
   *         返回的表实现了ScannableTable接口，可以被Calcite查询引擎扫描
   */
  public static ScannableTable eval(final String path) {
    return new AbstractBaseScannableTable() { // 返回一个抽象可扫描表的匿名内部类实例
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，定义返回表的行类型结构
        return typeFactory.builder() // 创建关系类型构建器，用于构建表的行类型
            .add("access_time", SqlTypeName.TIMESTAMP) // 添加access_time字段：文件访问时间，TIMESTAMP类型，对应Linux find的%A@格式（从纪元开始的秒数）
            .add("block_count", SqlTypeName.INTEGER) // 添加block_count字段：文件占用的块数，INTEGER类型，对应%b格式（以512字节块为单位）
            .add("change_time", SqlTypeName.TIMESTAMP) // 添加change_time字段：文件状态改变时间，TIMESTAMP类型，对应%C@格式（从纪元开始的秒数）
            .add("depth", SqlTypeName.INTEGER) // 添加depth字段：文件在目录树中的深度，INTEGER类型，对应%d格式
            .add("device", SqlTypeName.INTEGER) // 添加device字段：设备号，INTEGER类型，对应%D格式
            .add("file_name", SqlTypeName.VARCHAR) // 添加file_name字段：文件名（不包含目录路径），VARCHAR类型，对应%f格式
            .add("fstype", SqlTypeName.VARCHAR) // 添加fstype字段：文件系统类型，VARCHAR类型，对应%F格式
            .add("gname", SqlTypeName.VARCHAR) // 添加gname字段：文件所属组名，VARCHAR类型，对应%g格式
            .add("gid", SqlTypeName.INTEGER) // 添加gid字段：文件所属组的数字ID，INTEGER类型，对应%G格式
            .add("dir_name", SqlTypeName.VARCHAR) // 添加dir_name字段：文件所在目录名，VARCHAR类型，对应%h格式
            .add("inode", SqlTypeName.BIGINT) // 添加inode字段：文件的inode号，BIGINT类型，对应%i格式
            .add("link", SqlTypeName.VARCHAR) // 添加link字段：符号链接指向的目标，VARCHAR类型，对应%l格式
            .add("perm", SqlTypeName.CHAR, 4) // 添加perm字段：文件权限（八进制表示），CHAR类型长度4，对应%#m格式
            .add("hard", SqlTypeName.INTEGER) // 添加hard字段：硬链接数量，INTEGER类型，对应%n格式
            .add("path", SqlTypeName.VARCHAR) // 添加path字段：文件的完整路径名，VARCHAR类型，对应%P格式
            .add("size", SqlTypeName.BIGINT) // 添加size字段：文件大小（字节），BIGINT类型，对应%s格式
            .add("mod_time", SqlTypeName.TIMESTAMP) // 添加mod_time字段：文件修改时间，TIMESTAMP类型，对应%T@格式（从纪元开始的秒数）
            .add("user", SqlTypeName.VARCHAR) // 添加user字段：文件所属用户名，VARCHAR类型，对应%u格式
            .add("uid", SqlTypeName.INTEGER) // 添加uid字段：文件所属用户的数字ID，INTEGER类型，对应%U格式
            .add("type", SqlTypeName.CHAR, 1) // 添加type字段：文件类型，CHAR类型长度1，对应%Y格式（f=普通文件，d=目录，l=符号链接等）
            .build(); // 构建并返回关系类型对象

        // Fields in Linux find that are currently ignored: // Linux find命令中当前被忽略的字段
        // %y file type (not following sym links) // %y：文件类型（不跟随符号链接）
        // %k block count in 1KB blocks // %k：块数（以1KB块为单位）
        // %p file name (including argument) // %p：文件名（包括参数）
      }

      private Enumerable<String> sourceLinux() { // 私有方法：在Linux系统上获取文件信息数据源
        final String[] args = { // 定义find命令的参数数组
            "find", path, "-printf", "" // find命令：查找path路径下的文件，使用printf格式化输出
              + "%A@\\0" // access_time：文件访问时间（秒数），用\0分隔
              + "%b\\0" // block_count：块数（512字节块），用\0分隔
              + "%C@\\0" // change_time：文件状态改变时间（秒数），用\0分隔
              + "%d\\0" // depth：目录树深度，用\0分隔
              + "%D\\0" // device：设备号，用\0分隔
              + "%f\\0" // file_name：文件名（不含目录），用\0分隔
              + "%F\\0" // fstype：文件系统类型，用\0分隔
              + "%g\\0" // gname：组名，用\0分隔
              + "%G\\0" // gid：组ID，用\0分隔
              + "%h\\0" // dir_name：目录名，用\0分隔
              + "%i\\0" // inode：inode号，用\0分隔
              + "%l\\0" // link：符号链接目标，用\0分隔
              + "%#m\\0" // perm：权限（八进制），用\0分隔
              + "%n\\0" // hard：硬链接数，用\0分隔
              + "%P\\0" // path：文件路径，用\0分隔
              + "%s\\0" // size：文件大小（字节），用\0分隔
              + "%T@\\0" // mod_time：修改时间（秒数），用\0分隔
              + "%u\\0" // user：用户名，用\0分隔
              + "%U\\0" // uid：用户ID，用\0分隔
              + "%Y\\0" // type：文件类型，用\0分隔
        };
        return Processes.processLines('\0', args); // 调用Processes工具类执行命令，按\0字符分割返回结果
      }

      private Enumerable<String> sourceMacOs() { // 私有方法：在Mac OS系统上获取文件信息数据源
        if (path.contains("'")) { // 检查路径中是否包含单引号
          // no injection monkey business // 防止命令注入攻击，如果路径包含单引号则抛出异常
          throw new IllegalArgumentException(); // 抛出非法参数异常，拒绝执行
        }
        final String[] args = {"/bin/sh", "-c", "find '" + path // 定义shell命令参数，使用sh执行find和stat命令
              + "' | xargs stat -f " // find查找文件，xargs传递给stat，-f指定格式
              + "%a%n" // access_time：访问时间，用换行符分隔
              + "%b%n" // block_count：块数，用换行符分隔
              + "%c%n" // change_time：改变时间，用换行符分隔
              + "0%n" // depth：深度（macOS stat不支持，固定为0），用换行符分隔
              + "%Hd%n" // device：设备号（只使用H,L设备号的高位部分），用换行符分隔
              + "filename%n" // filename：文件名（macOS stat不支持，占位符），用换行符分隔
              + "fstype%n" // fstype：文件系统类型（macOS stat不支持，占位符），用换行符分隔
              + "%Sg%n" // gname：组名，用换行符分隔
              + "%g%n" // gid：组ID，用换行符分隔
              + "dir_name%n" // dir_name：目录名（macOS stat不支持，占位符），用换行符分隔
              + "%i%n" // inode：inode号，用换行符分隔
              + "%Y%n" // link：符号链接目标，用换行符分隔
              + "%Lp%n" // perm：权限，用换行符分隔
              + "%l%n" // hard：硬链接数，用换行符分隔
              + "%SN%n" // path：文件路径，用换行符分隔
              + "%z%n" // size：文件大小，用换行符分隔
              + "%m%n" // mod_time：修改时间，用换行符分隔
              + "%Su%n" // user：用户名，用换行符分隔
              + "%u%n" // uid：用户ID，用换行符分隔
              + "%LT%n" // type：文件类型，用换行符分隔
        };
        return Processes.processLines('\n', args); // 调用Processes工具类执行命令，按换行符分割返回结果
      }

      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，扫描表数据并返回可枚举的对象数组
        JavaTypeFactory typeFactory = root.getTypeFactory(); // 从数据上下文中获取Java类型工厂
        final RelDataType rowType = getRowType(typeFactory); // 获取行类型结构
        final List<String> fieldNames = // 获取所有字段名称列表
            ImmutableList.copyOf(rowType.getFieldNames()); // 创建字段名的不可变副本
        final String osName = System.getProperty("os.name"); // 获取操作系统名称
        final String osVersion = System.getProperty("os.version"); // 获取操作系统版本
        Util.discard(osVersion); // 丢弃版本号（未使用），避免编译器警告
        final Enumerable<String> enumerable; // 声明可枚举字符串变量
        switch (osName) { // 根据操作系统名称选择数据源
        case "Mac OS X": // 如果是Mac OS X系统
          enumerable = sourceMacOs(); // 使用Mac OS的数据源方法
          break; // 跳出switch
        default: // 默认情况（包括Linux系统）
          enumerable = sourceLinux(); // 使用Linux的数据源方法
        }
        return new AbstractEnumerable<@Nullable Object[]>() { // 返回一个抽象可枚举对象数组的匿名内部类
          @Override public Enumerator<@Nullable Object[]> enumerator() { // 重写enumerator方法，创建枚举器用于遍历数据
            final Enumerator<String> e = enumerable.enumerator(); // 从字符串可枚举对象创建枚举器
            return new Enumerator<@Nullable Object[]>() { // 返回一个对象数组枚举器的匿名内部类
              @Nullable Object @Nullable [] current; // 声明当前行的对象数组，可能为null

              @Override public Object[] current() { // 重写current方法，返回当前行的数据
                return requireNonNull(current, "current"); // 返回当前行，确保不为null
              }

              @Override public boolean moveNext() { // 重写moveNext方法，移动到下一行数据
                current = new Object[fieldNames.size()]; // 创建新的对象数组，大小等于字段数
                for (int i = 0; i < current.length; i++) { // 遍历每个字段
                  if (!e.moveNext()) { // 尝试移动到下一个字符串值
                    return false; // 如果没有更多数据，返回false
                  }
                  final String v = e.current(); // 获取当前字符串值
                  try { // 尝试将字符串值转换为适当的类型
                    current[i] = field(fieldNames.get(i), v); // 根据字段名转换值
                  } catch (RuntimeException e) { // 捕获运行时异常
                    throw new RuntimeException("while parsing value [" // 抛出包含详细信息的运行时异常
                        + v + "] of field [" + fieldNames.get(i) // 包含值和字段名
                        + "] in line [" + Arrays.toString(current) + "]", e); // 包含当前行数据
                  }
                }
                switch (osName) { // 根据操作系统进行特殊处理
                case "Mac OS X": // 如果是Mac OS X系统
                  // Strip leading "./" // 去除路径开头的"./"
                  String path = requireNonNull((String) current[14]); // 获取path字段（索引14）
                  if (".".equals(path)) { // 如果路径是"."
                    current[14] = path = ""; // 将path设为空字符串
                    current[3] = 0; // depth：将深度设为0
                  } else if (path.startsWith("./")) { // 如果路径以"./"开头
                    current[14] = path = path.substring(2); // 去除开头的"./"
                    current[3] = count(path, '/') + 1; // depth：计算深度（斜杠数+1）
                  } else { // 其他情况
                    current[3] = count(path, '/'); // depth：计算深度（斜杠数）
                  }
                  final int slash = path.lastIndexOf('/'); // 查找最后一个斜杠的位置
                  if (slash >= 0) { // 如果找到斜杠
                    current[5] = path.substring(slash + 1); // filename：提取文件名（斜杠后的部分）
                    current[9] = path.substring(0, slash); // dir_name：提取目录名（斜杠前的部分）
                  } else { // 如果没有斜杠
                    current[5] = path; // filename：整个路径就是文件名
                    current[9] = ""; // dir_name：目录名为空
                  }

                  // Make type values more like those on Linux // 使类型值更像Linux的格式
                  final String type = (String) current[19]; // 获取type字段（索引19）
                  current[19] = "/".equals(type) ? "d" // 如果是"/"则为目录"d"
                      : "".equals(type) || "*".equals(type) ? "f" // 如果是空或"*"则为普通文件"f"
                      : "@".equals(type) ? "l" // 如果是"@"则为符号链接"l"
                      : type; // 其他情况保持原样
                  break; // 跳出switch
                default: // 默认情况（Linux）
                  break; // 不需要特殊处理
                }
                return true; // 成功移动到下一行，返回true
              }

              private int count(String s, char c) { // 私有方法：计算字符串中指定字符的出现次数
                int n = 0; // 初始化计数器为0
                for (int i = 0, len = s.length(); i < len; i++) { // 遍历字符串的每个字符
                  if (s.charAt(i) == c) { // 如果当前字符等于指定字符
                    ++n; // 计数器加1
                  }
                }
                return n; // 返回字符出现次数
              }

              @Override public void reset() { // 重写reset方法，重置枚举器到初始位置
                throw new UnsupportedOperationException(); // 抛出不支持操作异常，不支持重置
              }

              @Override public void close() { // 重写close方法，关闭枚举器释放资源
                e.close(); // 关闭底层的字符串枚举器
              }

              private Object field(String field, String value) { // 私有方法：根据字段名将字符串值转换为适当的Java类型
                switch (field) { // 根据字段名进行类型转换
                case "block_count": // 如果是block_count字段
                case "depth": // 如果是depth字段
                case "device": // 如果是device字段
                case "gid": // 如果是gid字段
                case "uid": // 如果是uid字段
                case "hard": // 如果是hard字段
                  return Integer.valueOf(value); // 转换为Integer类型
                case "inode": // 如果是inode字段
                case "size": // 如果是size字段
                  return Long.valueOf(value); // 转换为Long类型
                case "access_time": // 如果是access_time字段
                case "change_time": // 如果是change_time字段
                case "mod_time": // 如果是mod_time字段
                  return new BigDecimal(value).multiply(THOUSAND).longValue(); // 转换为毫秒时间戳（秒*1000）
                default: // 默认情况（字符串类型字段）
                  return value; // 直接返回字符串值
                }
              }
            };
          }
        };
      }
    };
  }
}
