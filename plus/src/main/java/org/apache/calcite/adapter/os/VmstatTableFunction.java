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
// Apache许可证头，声明版权和使用许可，这是Apache软件基金会的标准许可证声明
package org.apache.calcite.adapter.os; // 定义包名，该类属于org.apache.calcite.adapter.os包，用于操作系统相关的适配器

import org.apache.calcite.DataContext; // 导入DataContext类，用于提供执行上下文信息，包含类型工厂等
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，用于支持LINQ风格的集合操作，提供可枚举的数据流
import org.apache.calcite.linq4j.function.Function1; // 导入Function1接口，表示接受一个参数并返回结果的函数
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型，用于描述表的结构
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口，表示可扫描的表，提供数据扫描能力
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义SQL类型名称如BIGINT、VARCHAR等
import org.apache.calcite.util.Util; // 导入Util工具类，提供各种实用方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList，用于创建不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值

import java.util.List; // 导入List接口，用于列表集合操作

import static java.lang.Long.parseLong; // 导入Long类的parseLong静态方法，用于将字符串解析为长整型

/**
 * Table function that executes the OS "vmstat" command
 * to share memory statistics.
 */
// 类文档注释：这是一个表函数，用于执行操作系统的"vmstat"命令来获取内存统计信息
// vmstat是Linux/Unix系统的一个命令，用于报告虚拟内存统计信息
// 该类将操作系统的vmstat命令输出转换为Calcite可以查询的表数据
public class VmstatTableFunction { // 定义VmstatTableFunction类，用于封装vmstat表函数的实现

  private VmstatTableFunction() {} // 私有构造方法，防止实例化，该类只提供静态方法

  public static ScannableTable eval(boolean b) { // 静态方法eval，返回一个ScannableTable对象，参数b未使用（保留用于未来扩展）
    return new AbstractBaseScannableTable() { // 返回AbstractBaseScannableTable的匿名子类实例，实现可扫描表功能
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，扫描数据并返回可枚举的对象数组
        final RelDataType rowType = getRowType(root.getTypeFactory()); // 获取行类型，即表的结构定义，通过类型工厂创建
        final List<String> fieldNames = // 从行类型中提取所有字段名称列表
            ImmutableList.copyOf(rowType.getFieldNames()); // 使用ImmutableList创建不可变的字段名列表副本
        final String[] args; // 声明字符串数组args，用于存储执行shell命令的参数
        final String osName = System.getProperty("os.name"); // 获取操作系统名称，如"Mac OS X"或"Linux"
        final String osVersion = System.getProperty("os.version"); // 获取操作系统版本号，但实际未使用
        Util.discard(osVersion); // 调用Util.discard方法显式丢弃osVersion变量，避免编译器警告
        // Fork out to a shell so that we can get normal text-munging support.
        // Could do this here too..
        // 注释说明：通过fork一个shell进程来获取正常的文本处理支持，也可以在这里实现
        switch (osName) { // 根据操作系统名称选择不同的vmstat命令和解析方式
        case "Mac OS X": // 如果是Mac OS X系统
          args = new String[] { // 创建shell命令参数数组
              "/bin/sh", "-c", // 使用/bin/sh执行命令，-c表示从字符串读取命令
              "vm_stat | tail -n +2 | awk '{print $NF}' | sed 's/\\.//' | tr '\\n' ' '" // Mac OS X的vmstat解析命令链：vm_stat获取统计|跳过第一行|提取最后一个字段|删除点号|将换行符替换为空格
          };
          break; // 跳出switch语句
        default: // 默认情况（Linux等其他Unix系统）
          args = new String[]{"/bin/sh", "-c", "vmstat -n | tail -n +3"}; // Linux的vmstat命令：-n不更新头部，跳过前3行（头部和空行）
        }
        return Processes.processLines(args) // 调用Processes.processLines方法执行shell命令并处理输出行
            .select( // 使用LINQ的select方法对每一行进行转换
                new Function1<String, Object[]>() { // 创建匿名函数，将字符串行转换为对象数组
                  @Override public Object[] apply(String line) { // 实现apply方法，处理每一行文本
                    final String[] fields = line.trim().split("\\s+"); // 去除行首尾空白，按空白字符分割为字段数组
                    final Object[] values = new Object[fieldNames.size()]; // 创建对象数组用于存储转换后的值，大小等于字段名数量
                    for (int i = 0; i < values.length; i++) { // 遍历所有字段
                      try { // 尝试转换字段值
                        values[i] = field(fieldNames.get(i), fields[i]); // 调用field方法将字符串值转换为对象，传入字段名和值
                      } catch (RuntimeException e) { // 捕获运行时异常
                        e.printStackTrace(System.out); // 打印异常堆栈到标准输出
                        throw new RuntimeException("while parsing value [" // 抛出新的运行时异常，包含详细的错误信息
                            + fields[i] + "] of field [" + fieldNames.get(i) // 显示字段值和字段名
                            + "] in line [" + line + "]"); // 显示出错的行内容
                      }
                    }
                    return values; // 返回转换后的对象数组
                  }

                  private Object field(@SuppressWarnings("unused") String field, String value) { // 私有方法field，将字符串值转换为对象，field参数未使用但保留用于扩展
                    if (value.isEmpty()) { // 如果值为空字符串
                      return 0; // 返回0作为默认值
                    }
                    if (value.endsWith(".")) { // 如果值以点号结尾（Mac OS X的vmstat输出格式）
                      return parseLong(value); // 去掉点号后解析为长整型
                    }
                    return parseLong(value); // 直接解析为长整型
                  }
                });
      }

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，定义表的行类型结构
        final String osName = System.getProperty("os.name"); // 获取操作系统名称
        final RelDataTypeFactory.Builder builder = typeFactory.builder(); // 创建类型工厂的构建器，用于构建行类型
        switch (osName) { // 根据操作系统名称返回不同的行类型定义
        case "Mac OS X": // Mac OS X系统的内存统计字段定义
          return builder // 使用构建器创建行类型
              .add("pages_free", SqlTypeName.BIGINT) // 添加pages_free字段（空闲页数），类型为BIGINT
              .add("pages_active", SqlTypeName.BIGINT) // 添加pages_active字段（活动页数），类型为BIGINT
              .add("pages_inactive", SqlTypeName.BIGINT) // 添加pages_inactive字段（非活动页数），类型为BIGINT
              .add("pages_speculative", SqlTypeName.BIGINT) // 添加pages_speculative字段（推测页数），类型为BIGINT
              .add("pages_throttled", SqlTypeName.BIGINT) // 添加pages_throttled字段（受限页数），类型为BIGINT
              .add("pages_wired_down", SqlTypeName.BIGINT) // 添加pages_wired_down字段（锁定页数），类型为BIGINT
              .add("pages_purgeable", SqlTypeName.BIGINT) // 添加pages_purgeable字段（可清除页数），类型为BIGINT
              .add("translation_faults", SqlTypeName.BIGINT) // 添加translation_faults字段（转换错误数），类型为BIGINT
              .add("pages_copy_on_write", SqlTypeName.BIGINT) // 添加pages_copy_on_write字段（写时复制页数），类型为BIGINT
              .add("pages_zero_filed", SqlTypeName.BIGINT) // 添加pages_zero_filed字段（零填充页数），类型为BIGINT
              .add("pages_reactivated", SqlTypeName.BIGINT) // 添加pages_reactivated字段（重新激活页数），类型为BIGINT
              .add("pages_purged", SqlTypeName.BIGINT) // 添加pages_purged字段（已清除页数），类型为BIGINT
              .add("pages_file_backed", SqlTypeName.BIGINT) // 添加pages_file_backed字段（文件支持页数），类型为BIGINT
              .add("pages_anonymous", SqlTypeName.BIGINT) // 添加pages_anonymous字段（匿名页数），类型为BIGINT
              .add("pages_stored_compressor", SqlTypeName.BIGINT) // 添加pages_stored_compressor字段（压缩存储页数），类型为BIGINT
              .add("pages_occupied_compressor", SqlTypeName.BIGINT) // 添加pages_occupied_compressor字段（压缩占用页数），类型为BIGINT
              .add("decompressions", SqlTypeName.BIGINT) // 添加decompressions字段（解压缩次数），类型为BIGINT
              .add("compressions", SqlTypeName.BIGINT) // 添加compressions字段（压缩次数），类型为BIGINT
              .add("pageins", SqlTypeName.BIGINT) // 添加pageins字段（页面换入次数），类型为BIGINT
              .add("pageouts", SqlTypeName.BIGINT) // 添加pageouts字段（页面换出次数），类型为BIGINT
              .add("swapins", SqlTypeName.BIGINT) // 添加swapins字段（交换换入次数），类型为BIGINT
              .add("swapouts", SqlTypeName.BIGINT) // 添加swapouts字段（交换换出次数），类型为BIGINT
              .build(); // 构建并返回RelDataType对象
        default: // 默认情况（Linux等Unix系统）的内存统计字段定义
          return builder // 使用构建器创建行类型
              .add("proc_r", SqlTypeName.BIGINT) // 添加proc_r字段（等待运行的进程数），类型为BIGINT
              .add("proc_b", SqlTypeName.BIGINT) // 添加proc_b字段（处于不可中断睡眠状态的进程数），类型为BIGINT
              .add("mem_swpd", SqlTypeName.BIGINT) // 添加mem_swpd字段（使用的虚拟内存量），类型为BIGINT
              .add("mem_free", SqlTypeName.BIGINT) // 添加mem_free字段（空闲内存量），类型为BIGINT
              .add("mem_buff", SqlTypeName.BIGINT) // 添加mem_buff字段（用作缓冲区的内存量），类型为BIGINT
              .add("mem_cache", SqlTypeName.BIGINT) // 添加mem_cache字段（用作缓存的内存量），类型为BIGINT
              .add("swap_si", SqlTypeName.BIGINT) // 添加swap_si字段（每秒从磁盘换入的内存量），类型为BIGINT
              .add("swap_so", SqlTypeName.BIGINT) // 添加swap_so字段（每秒换出到磁盘的内存量），类型为BIGINT
              .add("io_bi", SqlTypeName.BIGINT) // 添加io_bi字段（从块设备接收的块数），类型为BIGINT
              .add("io_bo", SqlTypeName.BIGINT) // 添加io_bo字段（发送到块设备的块数），类型为BIGINT
              .add("system_in", SqlTypeName.BIGINT) // 添加system_in字段（每秒中断数），类型为BIGINT
              .add("system_cs", SqlTypeName.BIGINT) // 添加system_cs字段（每秒上下文切换数），类型为BIGINT
              .add("cpu_us", SqlTypeName.BIGINT) // 添加cpu_us字段（用户空间CPU时间百分比），类型为BIGINT
              .add("cpu_sy", SqlTypeName.BIGINT) // 添加cpu_sy字段（内核空间CPU时间百分比），类型为BIGINT
              .add("cpu_id", SqlTypeName.BIGINT) // 添加cpu_id字段（空闲CPU时间百分比），类型为BIGINT
              .add("cpu_wa", SqlTypeName.BIGINT) // 添加cpu_wa字段（等待I/O的CPU时间百分比），类型为BIGINT
              .add("cpu_st", SqlTypeName.BIGINT) // 添加cpu_st字段（虚拟机偷走的CPU时间百分比），类型为BIGINT
              .build(); // 构建并返回RelDataType对象
        }
      }
    };
  }
}
