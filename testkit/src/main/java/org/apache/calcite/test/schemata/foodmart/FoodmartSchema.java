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
 */  // Apache 许可证声明，说明代码的版权和使用条款
package org.apache.calcite.test.schemata.foodmart;  // 包声明，该类属于 org.apache.calcite.test.schemata.foodmart 包

import org.apache.calcite.test.CalciteAssert;  // 导入 CalciteAssert 类，用于获取测试数据库配置信息

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入 Nullable 注解，用于标记可能为 null 的参数

import java.util.Objects;  // 导入 Objects 工具类，用于生成哈希码

/**
 * Foodmart schema.  // Foodmart 模式类，用于在 Calcite 测试中提供 Foodmart 数据库的测试数据
 */  // 这是一个测试用的模式类，Foodmart 是一个经典的零售数据集，常用于 OLAP 和数据仓库测试
public class FoodmartSchema {  // 定义 FoodmartSchema 类，用于定义 Foodmart 测试模式的结构
  public static final String FOODMART_SCHEMA = "     {\n"  // 定义 Foodmart 模式的 JSON 配置字符串，用于 JDBC 数据源连接
      + "       type: 'jdbc',\n"  // 指定数据源类型为 JDBC，表示通过 JDBC 连接数据库
      + "       name: 'foodmart',\n"  // 指定模式名称为 'foodmart'，用于在模型中引用该模式
      + "       jdbcDriver: " + q(CalciteAssert.DB.foodmart.driver) + ",\n"  // JDBC 驱动类名，从 CalciteAssert 测试配置中获取
      + "       jdbcUser: " + q(CalciteAssert.DB.foodmart.username) + ",\n"  // 数据库用户名，从测试配置中获取
      + "       jdbcPassword: " + q(CalciteAssert.DB.foodmart.password) + ",\n"  // 数据库密码，从测试配置中获取
      + "       jdbcUrl: " + q(CalciteAssert.DB.foodmart.url) + ",\n"  // JDBC 连接 URL，指定数据库连接地址
      + "       jdbcCatalog: " + q(CalciteAssert.DB.foodmart.catalog) + ",\n"  // 数据库目录名，指定要连接的数据库目录
      + "       jdbcSchema: " + q(CalciteAssert.DB.foodmart.schema) + "\n"  // 数据库模式名，指定要连接的数据库模式
      + "     }\n";  // 结束 JSON 对象定义
  public static final String FOODMART_MODEL = "{\n"  // 定义完整的 Foodmart 模型配置字符串，包含版本信息和模式列表
      + "  version: '1.0',\n"  // 指定模型版本为 '1.0'，用于模型配置的版本控制
      + "  defaultSchema: 'foodmart',\n"  // 指定默认模式为 'foodmart'，当 SQL 查询未指定模式时使用此默认模式
      + "   schemas: [\n"  // 开始定义模式列表，包含一个或多个模式配置
      + FOODMART_SCHEMA  // 引用上面定义的 FOODMART_SCHEMA 配置，将其添加到模式列表中
      + "   ]\n"  // 结束模式列表定义
      + "}";  // 结束模型 JSON 对象定义

  private static String q(@Nullable String s) {  // 私有静态辅助方法，用于将字符串转换为带单引号的 JSON 字符串格式
    return s == null ? "null" : "'" + s + "'";  // 如果输入字符串为 null，返回字符串 "null"；否则用单引号包裹该字符串
  }

  public final SalesFact[] sales_fact_1997 = {  // 定义一个包含 1997 年销售事实数据的数组，作为测试数据集
      new SalesFact(100, 10),  // 创建第一个销售事实记录，客户 ID 为 100，产品 ID 为 10
      new SalesFact(150, 20),  // 创建第二个销售事实记录，客户 ID 为 150，产品 ID 为 20
  };

  /**
   * Sales fact model.  // 销售事实模型类，用于表示销售事实数据
   */  // 这是一个静态内部类，用于建模销售事实表中的数据记录，包含客户 ID 和产品 ID
  public static class SalesFact {  // 定义 SalesFact 类，表示一个销售事实记录
    public final int cust_id;  // 客户 ID，用于标识购买产品的客户
    public final int prod_id;  // 产品 ID，用于标识被购买的产品

    public SalesFact(int cust_id, int prod_id) {  // 构造方法，创建一个新的销售事实记录
      this.cust_id = cust_id;  // 将传入的客户 ID 赋值给成员变量 cust_id
      this.prod_id = prod_id;  // 将传入的产品 ID 赋值给成员变量 prod_id
    }

    @Override public boolean equals(Object obj) {  // 重写 equals 方法，用于比较两个 SalesFact 对象是否相等
      return obj == this  // 如果传入的对象就是当前对象，直接返回 true
          || obj instanceof SalesFact  // 或者如果传入的对象是 SalesFact 类的实例
          && cust_id == ((SalesFact) obj).cust_id  // 并且客户 ID 相等
          && prod_id == ((SalesFact) obj).prod_id;  // 并且产品 ID 相等，则返回 true，否则返回 false
    }

    @Override public int hashCode() {  // 重写 hashCode 方法，用于生成对象的哈希码，以便在哈希集合中使用
      return Objects.hash(cust_id, prod_id);  // 使用 Objects 工具类基于 cust_id 和 prod_id 生成哈希码
    }
  }
}  // 结束 FoodmartSchema 类定义
