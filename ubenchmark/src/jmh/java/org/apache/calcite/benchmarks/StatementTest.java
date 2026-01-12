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
package org.apache.calcite.benchmarks; // 声明包名，该类位于 org.apache.calcite.benchmarks 包下

import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入 ReflectiveSchema 类，用于将 Java 对象反射为 Calcite Schema
import org.apache.calcite.jdbc.CalciteConnection; // 导入 CalciteConnection 类，Calcite 的 JDBC 连接接口
import org.apache.calcite.schema.SchemaPlus; // 导入 SchemaPlus 类，用于操作和管理 Calcite Schema

import org.openjdk.jmh.annotations.Benchmark; // 导入 Benchmark 注解，标记方法为基准测试方法
import org.openjdk.jmh.annotations.BenchmarkMode; // 导入 BenchmarkMode 注解，指定基准测试的模式
import org.openjdk.jmh.annotations.Level; // 导入 Level 注解，指定 Setup 方法的调用级别
import org.openjdk.jmh.annotations.Mode; // 导入 Mode 枚举，包含各种基准测试模式（如平均时间、吞吐量等）
import org.openjdk.jmh.annotations.Scope; // 导入 Scope 注解，指定状态对象的作用域
import org.openjdk.jmh.annotations.Setup; // 导入 Setup 注解，标记在基准测试前执行的方法
import org.openjdk.jmh.annotations.State; // 导入 State 注解，标记状态对象类

import java.sql.Connection; // 导入 Connection 接口，表示数据库连接
import java.sql.DriverManager; // 导入 DriverManager 类，用于管理数据库驱动程序并创建数据库连接
import java.sql.PreparedStatement; // 导入 PreparedStatement 接口，表示预编译的 SQL 语句对象
import java.sql.ResultSet; // 导入 ResultSet 接口，表示数据库查询结果集
import java.sql.SQLException; // 导入 SQLException 类，表示数据库操作异常
import java.sql.Statement; // 导入 Statement 接口，表示普通的 SQL 语句对象
import java.util.Arrays; // 导入 Arrays 工具类，提供数组操作方法
import java.util.Collections; // 导入 Collections 工具类，提供集合操作方法
import java.util.List; // 导入 List 接口，表示有序列表集合
import java.util.Properties; // 导入 Properties 类，用于存储键值对配置信息
import java.util.Random; // 导入 Random 类，用于生成随机数

/**
 * Compares {@link java.sql.Statement} vs {@link java.sql.PreparedStatement}.
 *
 * <p>This package contains micro-benchmarks to test calcite performance.
 *
 * <p>To run this and other benchmarks:
 *
 * <blockquote>
 *   <code>mvn package &amp;&amp;
 *   java -jar ./target/ubenchmarks.jar -wi 5 -i 5 -f 1</code>
 * </blockquote>
 *
 * <p>To run with profiling:
 *
 * <blockquote>
 *   <code>java -Djmh.stack.lines=10 -jar ./target/ubenchmarks.jar
 *     -prof hs_comp,hs_gc,stack -f 1 -wi 5</code>
 * </blockquote>
 */
public class StatementTest { // 定义 StatementTest 类，用于比较 Statement 和 PreparedStatement 的性能基准测试

  /**
   * Connection to be used during tests.
   */
  @State(Scope.Thread) // 标记为 JMH 状态类，作用域为线程级别，每个测试线程拥有独立的实例
  @BenchmarkMode(Mode.AverageTime) // 指定基准测试模式为平均时间，测量方法执行的平均耗时
  public static class HrConnection { // 定义 HrConnection 内部静态类，封装测试用的数据库连接和相关资源
    final Connection con; // 声明最终的 Connection 对象，表示数据库连接，初始化后不可修改
    int id; // 声明员工 ID 变量，用于存储随机选择的员工 ID
    final HrSchema hr = new HrSchema(); // 声明并初始化 HrSchema 对象，包含测试用的员工和部门数据
    final Random rnd = new Random(); // 声明并初始化 Random 对象，用于生成随机数选择员工
    { // 实例初始化块，在构造函数执行前执行，用于初始化数据库连接
      try { // 尝试加载 Calcite JDBC 驱动类
        Class.forName("org.apache.calcite.jdbc.Driver"); // 加载 Calcite 的 JDBC 驱动类，使其注册到 DriverManager
      } catch (ClassNotFoundException e) { // 捕获类未找到异常
        throw new IllegalStateException(e); // 抛出非法状态异常，包装原始异常信息
      }
      Connection connection; // 声明临时 Connection 变量，用于存储创建的连接

      try { // 尝试创建数据库连接
        Properties info = new Properties(); // 创建 Properties 对象，用于存储连接配置信息
        info.put("lex", "JAVA"); // 设置词法分析器为 JAVA 模式，使用 Java 的词法规则解析 SQL
        info.put("quoting", "DOUBLE_QUOTE"); // 设置标识符引用方式为双引号
        connection = DriverManager.getConnection("jdbc:calcite:", info); // 通过 DriverManager 获取 Calcite 数据库连接
      } catch (SQLException e) { // 捕获 SQL 异常
        throw new IllegalStateException(e); // 抛出非法状态异常，包装原始异常信息
      }
      CalciteConnection calciteConnection; // 声明 CalciteConnection 变量，用于访问 Calcite 特有功能
      try { // 尝试解包获取 CalciteConnection 对象
        calciteConnection = connection.unwrap(CalciteConnection.class); // 将普通 Connection 解包为 CalciteConnection
      } catch (SQLException e) { // 捕获 SQL 异常
        throw new IllegalStateException(e); // 抛出非法状态异常，包装原始异常信息
      }
      final SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根 Schema 对象，用于添加自定义 Schema
      rootSchema.add("hr", new ReflectiveSchema(new HrSchema())); // 将 HrSchema 反射包装后添加到根 Schema，命名为 "hr"
      try { // 尝试设置当前 Schema
        calciteConnection.setSchema("hr"); // 设置当前 Schema 为 "hr"，后续 SQL 查询默认在此 Schema 下执行
      } catch (SQLException e) { // 捕获 SQL 异常
        throw new IllegalStateException(e); // 抛出非法状态异常，包装原始异常信息
      }
      con = connection; // 将创建的连接赋值给 final 成员变量 con
    }

    @Setup(Level.Iteration) // 标记此方法为 JMH Setup 方法，在每次迭代前执行
    public void pickEmployee() { // 定义 pickEmployee 方法，用于随机选择一个员工 ID
      id = hr.emps[rnd.nextInt(4)].empid; // 从员工数组中随机选择一个员工，获取其 empid 赋值给 id 变量
    }
  }

  /**
   * Tests performance of reused execution of prepared statement.
   */
  public static class HrPreparedStatement extends HrConnection { // 定义 HrPreparedStatement 内部静态类，继承自 HrConnection，封装可重用的 PreparedStatement
    final PreparedStatement ps; // 声明最终的 PreparedStatement 对象，表示预编译的 SQL 语句，初始化后不可修改
    { // 实例初始化块，在构造函数执行前执行，用于初始化 PreparedStatement
      try { // 尝试创建 PreparedStatement
        ps = con.prepareStatement("select name from emps where empid = ?"); // 创建预编译语句，查询指定 empid 的员工姓名，使用参数占位符 ?
      } catch (SQLException e) { // 捕获 SQL 异常
        throw new IllegalStateException(e); // 抛出非法状态异常，包装原始异常信息
      }
    }
  }

  @Benchmark // 标记此方法为基准测试方法，JMH 会多次执行以测量性能
  public String prepareBindExecute(HrConnection state) throws SQLException { // 定义 prepareBindExecute 方法，测试每次都创建 PreparedStatement 并绑定参数执行的性能
    Connection con = state.con; // 获取状态对象中的数据库连接
    Statement st = null; // 声明 Statement 变量并初始化为 null，用于后续关闭
    ResultSet rs = null; // 声明 ResultSet 变量并初始化为 null，用于后续关闭
    String ename = null; // 声明员工姓名变量并初始化为 null
    try { // 开始 try 块，用于资源清理
      final PreparedStatement ps = // 创建 PreparedStatement 变量
          con.prepareStatement("select name from emps where empid = ?"); // 每次调用都创建新的 PreparedStatement，查询指定 empid 的员工姓名
      st = ps; // 将 PreparedStatement 赋值给 Statement 变量，用于统一关闭
      ps.setInt(1, state.id); // 设置第一个参数占位符的值为随机选择的员工 ID
      rs = ps.executeQuery(); // 执行查询，获取结果集
      rs.next(); // 移动结果集指针到第一行（查询结果只有一行）
      ename = rs.getString(1); // 获取结果集第一列（name 列）的值
    } finally { // finally 块确保资源被正确关闭
      close(rs, st); // 关闭 ResultSet 和 Statement，释放数据库资源
    }
    return ename; // 返回查询到的员工姓名
  }

  @Benchmark // 标记此方法为基准测试方法，JMH 会多次执行以测量性能
  public String bindExecute(HrPreparedStatement state) // 定义 bindExecute 方法，测试重用 PreparedStatement 只绑定参数执行的性能
      throws SQLException { // 声明可能抛出 SQLException
    PreparedStatement st = state.ps; // 获取状态对象中已预编译的 PreparedStatement
    ResultSet rs = null; // 声明 ResultSet 变量并初始化为 null，用于后续关闭
    String ename = null; // 声明员工姓名变量并初始化为 null
    try { // 开始 try 块，用于资源清理
      st.setInt(1, state.id); // 设置第一个参数占位符的值为随机选择的员工 ID
      rs = st.executeQuery(); // 执行查询，获取结果集
      rs.next(); // 移动结果集指针到第一行（查询结果只有一行）
      ename = rs.getString(1); // 获取结果集第一列（name 列）的值
    } finally { // finally 块确保资源被正确关闭
      close(rs, null); // 只关闭 ResultSet，不关闭 Statement（因为 PreparedStatement 被重用）
    }
    return ename; // 返回查询到的员工姓名
  }

  @Benchmark // 标记此方法为基准测试方法，JMH 会多次执行以测量性能
  public String executeQuery(HrConnection state) throws SQLException { // 定义 executeQuery 方法，测试使用普通 Statement 执行 SQL 查询的性能
    Connection con = state.con; // 获取状态对象中的数据库连接
    Statement st = null; // 声明 Statement 变量并初始化为 null，用于后续关闭
    ResultSet rs = null; // 声明 ResultSet 变量并初始化为 null，用于后续关闭
    String ename = null; // 声明员工姓名变量并初始化为 null
    try { // 开始 try 块，用于资源清理
      st = con.createStatement(); // 创建普通 Statement 对象
      rs = st.executeQuery("select name from emps where empid = " + state.id); // 执行 SQL 查询，将 empid 直接拼接到 SQL 字符串中
      rs.next(); // 移动结果集指针到第一行（查询结果只有一行）
      ename = rs.getString(1); // 获取结果集第一列（name 列）的值
    } finally { // finally 块确保资源被正确关闭
      close(rs, st); // 关闭 ResultSet 和 Statement，释放数据库资源
    }
    return ename; // 返回查询到的员工姓名
  }

  @Benchmark // 标记此方法为基准测试方法，JMH 会多次执行以测量性能
  public String forEach(HrConnection state) { // 定义 forEach 方法，测试直接遍历内存数组查找员工的性能（作为基准对比）
    final Employee[] emps = state.hr.emps; // 获取状态对象中的员工数组
    for (Employee emp : emps) { // 遍历员工数组中的每个员工对象
      if (emp.empid == state.id) { // 检查当前员工的 empid 是否等于目标 ID
        return emp.name; // 如果匹配，返回员工姓名
      }
    }
    return null; // 如果遍历完数组仍未找到，返回 null
  }

  private static void close(ResultSet rs, Statement st) { // 定义 close 静态方法，用于安全关闭 ResultSet 和 Statement 资源
    if (rs != null) { // 检查 ResultSet 是否不为 null
      try { // 尝试关闭 ResultSet
        rs.close(); // 关闭 ResultSet，释放数据库资源
      } catch (SQLException e) { // 捕获 SQL 异常
        // ignore // 忽略异常，不进行任何处理
      }
    }
    if (st != null) { // 检查 Statement 是否不为 null
      try { // 尝试关闭 Statement
        st.close(); // 关闭 Statement，释放数据库资源
      } catch (SQLException e) { // 捕获 SQL 异常
        // ignore // 忽略异常，不进行任何处理
      }
    }
  }

  /** Pojo schema containing "emps" and "depts" tables. */
  public static class HrSchema { // 定义 HrSchema 内部静态类，使用 Java POJO 对象模拟数据库表结构
    @Override public String toString() { // 重写 toString 方法
      return "HrSchema"; // 返回 Schema 的名称字符串
    }

    public final Employee[] emps = { // 声明并初始化员工数组，模拟 emps 表
        new Employee(100, 10, "Bill", 10000, 1000), // 创建员工对象：ID 100，部门 10，姓名 Bill，薪资 10000，佣金 1000
        new Employee(200, 20, "Eric", 8000, 500), // 创建员工对象：ID 200，部门 20，姓名 Eric，薪资 8000，佣金 500
        new Employee(150, 10, "Sebastian", 7000, null), // 创建员工对象：ID 150，部门 10，姓名 Sebastian，薪资 7000，无佣金
        new Employee(110, 10, "Theodore", 11500, 250), // 创建员工对象：ID 110，部门 10，姓名 Theodore，薪资 11500，佣金 250
    };
    public final Department[] depts = { // 声明并初始化部门数组，模拟 depts 表
        new Department(10, "Sales", Arrays.asList(emps[0], emps[2])), // 创建部门对象：编号 10，名称 Sales，员工列表包含 Bill 和 Sebastian
        new Department(30, "Marketing", Collections.<Employee>emptyList()), // 创建部门对象：编号 30，名称 Marketing，无员工
        new Department(40, "HR", Collections.singletonList(emps[1])), // 创建部门对象：编号 40，名称 HR，员工列表包含 Eric
    };
  }

  /** Employee record. */
  public static class Employee { // 定义 Employee 内部静态类，表示员工记录
    public final int empid; // 声明员工 ID 字段，final 表示初始化后不可修改
    public final int deptno; // 声明部门编号字段，final 表示初始化后不可修改
    public final String name; // 声明员工姓名字段，final 表示初始化后不可修改
    public final float salary; // 声明薪资字段，final 表示初始化后不可修改
    public final Integer commission; // 声明佣金字段，final 表示初始化后不可修改，可为 null

    public Employee(int empid, int deptno, String name, float salary, // 定义 Employee 构造函数
        Integer commission) { // 接收员工的所有属性作为参数
      this.empid = empid; // 初始化员工 ID
      this.deptno = deptno; // 初始化部门编号
      this.name = name; // 初始化员工姓名
      this.salary = salary; // 初始化薪资
      this.commission = commission; // 初始化佣金
    }

    @Override public String toString() { // 重写 toString 方法
      return "Employee [empid: " + empid + ", deptno: " + deptno // 返回员工信息的字符串表示
          + ", name: " + name + "]";
    }
  }

  /** Department record. */
  public static class Department { // 定义 Department 内部静态类，表示部门记录
    public final int deptno; // 声明部门编号字段，final 表示初始化后不可修改
    public final String name; // 声明部门名称字段，final 表示初始化后不可修改
    public final List<Employee> employees; // 声明员工列表字段，final 表示初始化后不可修改

    public Department( // 定义 Department 构造函数
        int deptno, String name, List<Employee> employees) { // 接收部门的所有属性作为参数
      this.deptno = deptno; // 初始化部门编号
      this.name = name; // 初始化部门名称
      this.employees = employees; // 初始化员工列表
    }


    @Override public String toString() { // 重写 toString 方法
      return "Department [deptno: " + deptno + ", name: " + name // 返回部门信息的字符串表示
          + ", employees: " + employees + "]";
    }
  }

}
