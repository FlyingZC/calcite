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
package org.apache.calcite.example.maze; // 定义包名，该类属于org.apache.calcite.example.maze包

import org.apache.calcite.DataContext; // 导入Calcite的数据上下文类，用于在查询执行时传递运行时信息
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入抽象可枚举类，用于实现LINQ风格的集合遍历
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口，定义可遍历的集合类型
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于逐个访问集合中的元素
import org.apache.calcite.linq4j.Linq4j; // 导入LINQ4j工具类，提供LINQ操作的支持方法
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示表或表达式的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.schema.ScannableTable; // 导入可扫描表接口，表示可以被扫描的表
import org.apache.calcite.schema.impl.AbstractTable; // 导入抽象表基类，提供表的基本实现
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义SQL标准数据类型
import org.apache.calcite.util.Util; // 导入Calcite工具类，提供各种实用方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的类型

import java.io.PrintWriter; // 导入打印写入器类，用于格式化输出文本
import java.util.Random; // 导入随机数生成器类，用于生成迷宫的随机结构
import java.util.Set; // 导入集合接口，用于存储迷宫的解路径

/**
 * User-defined table function that generates a Maze and prints it in text
 * form. // 自定义表函数，生成迷宫并以文本形式打印输出
 * 
 * 这个类是Calcite框架中自定义表函数的一个示例实现，展示了如何：
 * 1. 继承AbstractTable抽象类实现自定义表
 * 2. 实现ScannableTable接口使表可被扫描
 * 3. 通过反射机制被Calcite调用
 * 4. 生成随机迷宫数据并返回可枚举的结果集
 * 
 * MazeTable类演示了Calcite中表函数的核心概念：
 * - 表函数可以接受参数（宽度、高度、随机种子）
 * - 表函数返回一个可扫描的表对象
 * - 表的数据由程序动态生成，而不是从数据库表中读取
 * - 支持两种模式：生成迷宫和生成带解的迷宫
 */
public class MazeTable extends AbstractTable implements ScannableTable { // MazeTable类继承AbstractTable并实现ScannableTable接口，表示一个可扫描的自定义表
  final int width; // 成员变量：迷宫的宽度（列数），final表示不可变
  final int height; // 成员变量：迷宫的高度（行数），final表示不可变
  final int seed; // 成员变量：随机数种子，用于生成确定性的迷宫结构，final表示不可变
  final boolean solution; // 成员变量：是否显示迷宫的解路径，final表示不可变

  /**
   * 私有构造方法，创建MazeTable实例
   * 
   * @param width 迷宫的宽度（列数），决定迷宫的水平大小
   * @param height 迷宫的高度（行数），决定迷宫的垂直大小
   * @param seed 随机数种子，如果>=0则使用指定种子生成确定性的迷宫，如果<0则使用随机种子
   * @param solution 是否计算并显示迷宫的解路径，true表示显示解，false表示不显示
   * 
   * 构造方法的作用：
   * - 初始化迷宫的基本参数
   * - 保存用户传入的配置信息
   * - 这些参数将在scan方法中用于生成实际的迷宫数据
   */
  private MazeTable(int width, int height, int seed, boolean solution) { // 私有构造方法，通过静态工厂方法调用
    this.width = width; // 将传入的宽度参数赋值给成员变量width
    this.height = height; // 将传入的高度参数赋值给成员变量height
    this.seed = seed; // 将传入的随机种子参数赋值给成员变量seed
    this.solution = solution; // 将传入的解标志参数赋值给成员变量solution
  }

  /**
   * 静态工厂方法：生成不带解路径的迷宫表函数
   * 
   * <p>Called by reflection based on the definition of the user-defined
   * function in the schema. // 通过反射调用，基于schema中用户定义函数的定义
   * 
   * 这个方法是Calcite表函数的入口点之一，当SQL中调用generate函数时会被反射调用
   * 
   * @param width Width of maze // 迷宫的宽度，决定迷宫有多少列
   * @param height Height of maze // 迷宫的高度，决定迷宫有多少行
   * @param seed Random number seed, or -1 to create an unseeded random // 随机数种子，-1表示使用非种子的随机数（每次都不同）
   * @return Table that prints the maze in text form // 返回一个表对象，该表会以文本形式打印迷宫
   * 
   * 方法作用：
   * - 这是表函数的静态入口点，通过反射机制被Calcite调用
   * - 创建一个不显示解路径的MazeTable实例
   * - 返回ScannableTable接口的实现，使表可以被扫描查询
   * - 对应SQL中的generate(width, height, seed)函数调用
   */
  @SuppressWarnings("unused") // called via reflection // 抑制未使用警告，因为该方法通过反射调用
  public static ScannableTable generate(int width, int height, int seed) { // 静态方法，生成不带解的迷宫表
    return new MazeTable(width, height, seed, false); // 创建MazeTable实例，solution参数设为false表示不显示解
  }

  /**
   * 静态工厂方法：生成带解路径的迷宫表函数
   * 
   * <p>Called by reflection based on the definition of the user-defined
   * function in the schema. // 通过反射调用，基于schema中用户定义函数的定义
   * 
   * 这个方法是Calcite表函数的另一个入口点，当SQL中调用solve函数时会被反射调用
   * 
   * @param width Width of maze // 迷宫的宽度，决定迷宫有多少列
   * @param height Height of maze // 迷宫的高度，决定迷宫有多少行
   * @param seed Random number seed, or -1 to create an unseeded random // 随机数种子，-1表示使用非种子的随机数（每次都不同）
   * @return Table that prints the maze in text form, with solution shown // 返回一个表对象，该表会以文本形式打印迷宫并显示解路径
   * 
   * 方法作用：
   * - 这是表函数的静态入口点，通过反射机制被Calcite调用
   * - 创建一个显示解路径的MazeTable实例
   * - 返回ScannableTable接口的实现，使表可以被扫描查询
   * - 对应SQL中的solve(width, height, seed)函数调用
   * - 与generate方法的区别在于solution参数设为true，会计算并显示从起点到终点的路径
   */
  @SuppressWarnings("unused") // called via reflection // 抑制未使用警告，因为该方法通过反射调用
  public static ScannableTable solve(int width, int height, int seed) { // 静态方法，生成带解的迷宫表
    return new MazeTable(width, height, seed, true); // 创建MazeTable实例，solution参数设为true表示显示解
  }

  /**
   * 获取表的行类型（schema定义）
   * 
   * @param typeFactory RelDataTypeFactory // 关系数据类型工厂，用于创建数据类型
   * @return RelDataType // 返回表的行类型定义，包含列名、数据类型和长度信息
   * 
   * 方法作用：
   * - 实现ScannableTable接口的方法，定义表的结构
   * - 告诉Calcite这个表有哪些列，每列是什么类型
   * - 这里定义了一个名为"S"的VARCHAR列，长度为width*3+1
   * - 列长度的计算：每个迷宫单元格在文本表示中占用3个字符，加上1个换行符
   * - 例如：width=5时，列长度为5*3+1=16
   * 
   * Calcite工作流程：
   * 1. 解析SQL时调用此方法获取表的元数据
   * 2. 根据返回的RelDataType进行查询优化和验证
   * 3. 在执行时使用这个类型信息来处理结果集
   */
  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写AbstractTable的方法，获取表的行类型
    return typeFactory.builder() // 使用类型工厂创建一个类型构建器
        .add("S", SqlTypeName.VARCHAR, width * 3 + 1) // 添加一个名为"S"的VARCHAR类型列，长度为width*3+1
        .build(); // 构建并返回RelDataType对象
  }

  /**
   * 扫描表并返回可枚举的结果集
   * 
   * @param root DataContext // 数据上下文，提供查询执行时的运行时环境信息
   * @return Enumerable<@Nullable Object[]> // 返回可枚举的对象数组，每个数组代表一行数据
   * 
   * 方法作用：
   * - 实现ScannableTable接口的核心方法，实际生成表的数据
   * - 创建迷宫对象并生成迷宫结构
   * - 如果需要解路径，则计算从起点(0,0)到终点的路径
   * - 返回一个可枚举的结果集，每行包含迷宫的一行文本表示
   * 
   * 执行流程：
   * 1. 根据seed参数创建随机数生成器
   * 2. 创建Maze对象并初始化迷宫结构
   * 3. 调用layout方法生成迷宫的随机布局
   * 4. 如果DEBUG模式开启，打印调试信息
   * 5. 创建AbstractEnumerable匿名子类，实现enumerator方法
   * 6. 在enumerator中，如果需要解则调用solve方法计算解路径
   * 7. 使用Linq4j.transform将迷宫的枚举器转换为Object数组形式
   * 
   * Calcite工作流程：
   * - 查询执行时调用此方法获取数据
   * - 返回的Enumerable会被Calcite的查询引擎逐行读取
   * - 每行数据是一个Object数组，对应getRowType定义的列
   */
  @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写ScannableTable的方法，扫描表数据
    final Random random = seed >= 0 ? new Random(seed) : new Random(); // 创建随机数生成器，如果seed>=0则使用指定种子，否则使用随机种子
    final Maze maze = new Maze(width, height); // 创建Maze对象，初始化指定宽高的迷宫
    final PrintWriter pw = Util.printWriter(System.out); // 创建打印写入器，用于输出迷宫到控制台
    maze.layout(random, pw); // 调用Maze的layout方法，使用随机数生成器生成迷宫的布局结构
    if (Maze.DEBUG) { // 如果Maze的DEBUG标志为true
      maze.print(pw, true); // 打印迷宫的调试信息，true参数表示打印详细信息
    }
    return new AbstractEnumerable<@Nullable Object[]>() { // 返回一个匿名抽象枚举类，实现可枚举的结果集
      @Override public Enumerator<@Nullable Object[]> enumerator() { // 重写enumerator方法，创建枚举器
        final Set<Integer> solutionSet; // 声明解路径集合，用于存储解路径上的单元格索引
        if (solution) { // 如果solution标志为true
          solutionSet = maze.solve(0, 0); // 调用Maze的solve方法计算从起点(0,0)到终点的解路径，返回解路径的单元格索引集合
        } else { // 如果solution标志为false
          solutionSet = null; // 不计算解路径，设为null
        }
        return Linq4j.transform(maze.enumerator(solutionSet), // 使用Linq4j的transform方法转换枚举器
            s -> new Object[] {s}); // 将迷宫的每一行文本字符串转换为Object数组，数组包含一个元素（该行文本）
      }
    };
  }
} // 类定义结束
