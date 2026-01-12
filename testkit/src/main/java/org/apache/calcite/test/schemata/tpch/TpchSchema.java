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
package org.apache.calcite.test.schemata.tpch; // 定义包名，表示该类位于 org.apache.calcite.test.schemata.tpch 包下

/**
 * TPC-H table schema. // TPC-H 表模式定义类，用于在测试环境中模拟 TPC-H 基准测试的数据库表结构
 * TPC-H 是一个决策支持基准测试，用于评估数据库系统在复杂查询环境下的性能
 * 该类提供了简化的 TPC-H 数据模型，包含客户、订单项、零件和零件供应商等核心表
 * 每个表都使用数组存储少量示例数据，用于单元测试和集成测试
 */
public class TpchSchema { // 定义 TpchSchema 类，作为 TPC-H 模式的容器
  public final Customer[] customer = { c(1), c(2) }; // 客户表数组，包含两个客户记录，custId 分别为 1 和 2，Customer 类代表 TPC-H 中的客户实体
  public final LineItem[] lineitem = { li(1), li(2) }; // 订单项表数组，包含两个订单项记录，custId 分别为 1 和 2，LineItem 类代表 TPC-H 中的订单明细实体
  public final Part[] part = { p(1), p(2) }; // 零件表数组，包含两个零件记录，pPartkey 分别为 1 和 2，Part 类代表 TPC-H 中的零件实体
  public final PartSupp[] partsupp = { ps(1, 250), ps(2, 100) }; // 零件供应商表数组，包含两个零件供应商记录，第一个是零件 1 供应成本 250，第二个是零件 2 供应成本 100，PartSupp 类代表 TPC-H 中的零件供应商关系

  /**
   * Customer in TPC-H. // TPC-H 中的客户实体类，代表数据库中的客户表
   * 客户是 TPC-H 模型的核心实体之一，包含客户的基本信息如客户 ID 和所属国家
   */
  public static class Customer { // 定义 Customer 静态内部类，表示 TPC-H 客户实体
    public final int custId; // 客户 ID，唯一标识一个客户，在 TPC-H 规范中是主键
    // CHECKSTYLE: IGNORE 1 // 检查样式忽略指令，允许该字段使用下划线命名（不符合 Java 标准命名规范）
    public final String nation_name; // 客户所属国家名称，在 TPC-H 规范中通过外键关联到国家表，这里简化为字符串

    public Customer(int custId) { // Customer 类的构造方法，用于创建客户对象
      this.custId = custId; // 将传入的客户 ID 参数赋值给实例变量 custId，初始化客户唯一标识
      this.nation_name = "USA"; // 将国家名称固定设置为 "USA"，这是简化实现，实际 TPC-H 中应该从国家表获取
    }

    @Override public String toString() { // 重写 Object 类的 toString 方法，用于返回对象的字符串表示
      return "Customer [custId=" + custId + "]"; // 返回格式化的客户信息字符串，包含客户 ID，便于调试和日志输出
    }
  }

  /**
   * Line Item in TPC-H. // TPC-H 中的订单项实体类，代表数据库中的订单明细表
   * 订单项是 TPC-H 模型的核心实体之一，记录订单中包含的具体商品信息
   */
  public static class LineItem { // 定义 LineItem 静态内部类，表示 TPC-H 订单项实体
    public final int custId; // 客户 ID，用于关联订单项所属的客户，在 TPC-H 规范中通过订单表间接关联

    public LineItem(int custId) { // LineItem 类的构造方法，用于创建订单项对象
      this.custId = custId; // 将传入的客户 ID 参数赋值给实例变量 custId，建立订单项与客户的关联关系
    }

    @Override public String toString() { // 重写 Object 类的 toString 方法，用于返回对象的字符串表示
      return "LineItem [custId=" + custId + "]"; // 返回格式化的订单项信息字符串，包含客户 ID，便于调试和日志输出
    }
  }

  /**
   * Part in TPC-H. // TPC-H 中的零件实体类，代表数据库中的零件表
   * 零件是 TPC-H 模型的核心实体之一，记录可销售商品的基本信息
   */
  public static class Part { // 定义 Part 静态内部类，表示 TPC-H 零件实体
    public final int pPartkey; // 零件键，唯一标识一个零件，在 TPC-H 规范中是主键
    // CHECKSTYLE: IGNORE 1 // 检查样式忽略指令，允许该字段使用下划线命名（不符合 Java 标准命名规范）
    public final String p_brand; // 零件品牌，表示零件所属的品牌分类，在 TPC-H 规范中是零件的重要属性

    public Part(int pPartkey) { // Part 类的构造方法，用于创建零件对象
      this.pPartkey = pPartkey; // 将传入的零件键参数赋值给实例变量 pPartkey，初始化零件唯一标识
      this.p_brand = "brand" + pPartkey; // 根据零件键生成品牌名称，格式为 "brand" 加上零件键，例如 "brand1"，这是简化实现
    }

    @Override public String toString() { // 重写 Object 类的 toString 方法，用于返回对象的字符串表示
      return "Part [pPartkey=" + pPartkey + "]"; // 返回格式化的零件信息字符串，包含零件键，便于调试和日志输出
    }
  }

  /**
   * Part supplier in TPC-H. // TPC-H 中的零件供应商实体类，代表数据库中的零件供应商关系表
   * 零件供应商是 TPC-H 模型的核心实体之一，记录零件与供应商之间的供应关系和成本信息
   */
  public static class PartSupp { // 定义 PartSupp 静态内部类，表示 TPC-H 零件供应商实体
    public final int psPartkey; // 零件键，外键关联到零件表，标识该供应关系对应的零件
    public final int psSupplyCost; // 供应成本，表示供应商提供该零件的成本价格，是 TPC-H 查询中的重要指标

    public PartSupp(int psPartkey, int psSupplyCost) { // PartSupp 类的构造方法，用于创建零件供应商对象
      this.psPartkey = psPartkey; // 将传入的零件键参数赋值给实例变量 psPartkey，建立与零件的关联
      this.psSupplyCost = psSupplyCost; // 将传入的供应成本参数赋值给实例变量 psSupplyCost，记录供应成本信息
    }

    @Override public String toString() { // 重写 Object 类的 toString 方法，用于返回对象的字符串表示
      return "PartSupp [pSupplyCost=" + psPartkey + ", pSupplyCost=" // 返回格式化的零件供应商信息字符串，注意这里存在一个 bug，应该显示 psPartkey 而不是 pSupplyCost
        + psSupplyCost + "]"; // 继续返回供应成本信息，完整格式为 "PartSupp [pSupplyCost=零件键, pSupplyCost=供应成本]"
    }
  }

  public static Customer c(int custId) { // 静态工厂方法，用于创建 Customer 对象的便捷方法
    return new Customer(custId); // 调用 Customer 构造方法创建并返回一个新的 Customer 实例，传入客户 ID 参数
  }

  public static LineItem li(int custId) { // 静态工厂方法，用于创建 LineItem 对象的便捷方法
    return new LineItem(custId); // 调用 LineItem 构造方法创建并返回一个新的 LineItem 实例，传入客户 ID 参数
  }

  public static PartSupp ps(int pPartkey, int pSupplyCost) { // 静态工厂方法，用于创建 PartSupp 对象的便捷方法
    return new PartSupp(pPartkey, pSupplyCost); // 调用 PartSupp 构造方法创建并返回一个新的 PartSupp 实例，传入零件键和供应成本参数
  }

  public static Part p(int pPartkey) { // 静态工厂方法，用于创建 Part 对象的便捷方法
    return new Part(pPartkey); // 调用 Part 构造方法创建并返回一个新的 Part 实例，传入零件键参数
  }
}
