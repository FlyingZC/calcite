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
package org.apache.calcite.plan; // 声明包名，表示该类属于org.apache.calcite.plan包，这是Calcite优化器相关的包

import org.apache.calcite.rel.RelRoot; // 导入RelRoot类，表示关系代数树的根节点，包含完整的查询计划
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint类，表示关系代数提示，用于优化器指导

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型，描述表或视图的行类型

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList，用于创建不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的参数

import java.util.List; // 导入Java标准库的List接口，用于表示列表集合

/**
 * Utilities for {@link RelOptTable.ViewExpander} and
 * {@link RelOptTable.ToRelContext}.
 */
// 类注释：这是一个工具类，提供了RelOptTable.ViewExpander和RelOptTable.ToRelContext之间的转换功能
// ViewExpander是视图扩展器接口，用于将视图的定义SQL转换为关系代数树
// ToRelContext是转换上下文接口，提供了将表转换为关系代数树时所需的环境信息
// 这个类的主要作用是在这两个接口之间提供转换和创建功能
public abstract class ViewExpanders { // 声明ViewExpanders类，使用abstract修饰表示这是一个抽象类，不能被实例化
  private ViewExpanders() {} // 私有构造方法，防止类被实例化，因为这是一个纯工具类，只包含静态方法

  /** Converts a {@code ViewExpander} to a {@code ToRelContext}. */
  // 方法注释：将ViewExpander转换为ToRelContext
  // ViewExpander只关注如何扩展视图，而ToRelContext提供了更完整的上下文信息（包括cluster和hints）
  // 这个方法通过创建ToRelContext的匿名实现类，将ViewExpander的功能包装在ToRelContext接口中
  // 参数viewExpander：视图扩展器，负责将视图SQL转换为关系代数树
  // 参数cluster：关系优化集群，包含了优化器的上下文信息，如RexBuilder、类型工厂等
  // 参数hints：表提示列表，用于指导优化器如何处理表，如使用特定索引等
  // 返回值：返回一个ToRelContext实例，该实例使用给定的ViewExpander来扩展视图
  public static RelOptTable.ToRelContext toRelContext( // 声明公共静态方法toRelContext，返回ToRelContext类型
      RelOptTable.ViewExpander viewExpander, // 参数：视图扩展器接口，用于扩展视图定义
      RelOptCluster cluster, // 参数：关系优化集群，提供优化器运行所需的上下文环境
      List<RelHint> hints) { // 参数：关系提示列表，包含优化器提示信息
    return new RelOptTable.ToRelContext() { // 返回ToRelContext的匿名内部类实例
      @Override public RelOptCluster getCluster() { // 重写getCluster方法，返回关系优化集群
        return cluster; // 返回传入的cluster参数，提供优化器上下文
      }

      @Override public List<RelHint> getTableHints() { // 重写getTableHints方法，返回表提示列表
        return hints; // 返回传入的hints参数，提供优化器提示信息
      }

      @Override public RelRoot expandView(RelDataType rowType, String queryString, // 重写expandView方法，扩展视图为关系代数树
          List<String> schemaPath, @Nullable List<String> viewPath) { // 参数：rowType是视图的行类型，queryString是视图定义的SQL，schemaPath是模式路径，viewPath是视图路径（可为null）
        return viewExpander.expandView(rowType, queryString, schemaPath, // 调用传入的viewExpander的expandView方法，将视图SQL转换为关系代数树
            viewPath); // 传递viewPath参数
      }
    };
  }

  /** Converts a {@code ViewExpander} to a {@code ToRelContext}. */
  // 方法注释：将ViewExpander转换为ToRelContext的重载方法
  // 这是上一个方法的简化版本，不提供hints参数，使用空的提示列表
  // 参数viewExpander：视图扩展器，负责将视图SQL转换为关系代数树
  // 参数cluster：关系优化集群，包含了优化器的上下文信息
  // 返回值：返回一个ToRelContext实例，使用空的提示列表
  public static RelOptTable.ToRelContext toRelContext( // 声明公共静态方法toRelContext，返回ToRelContext类型
      RelOptTable.ViewExpander viewExpander, // 参数：视图扩展器接口
      RelOptCluster cluster) { // 参数：关系优化集群
    return toRelContext(viewExpander, cluster, ImmutableList.of()); // 调用三参数版本的toRelContext方法，传入空的不可变列表作为hints
  }

  /** Creates a simple {@code ToRelContext} that cannot expand views. */
  // 方法注释：创建一个简单的ToRelContext，该上下文不能扩展视图
  // 这是一个简化版本的上下文，用于不需要视图扩展的场景
  // 当调用expandView方法时会抛出UnsupportedOperationException异常
  // 参数cluster：关系优化集群，提供了优化器运行所需的上下文环境
  // 返回值：返回一个不支持视图扩展的ToRelContext实例
  public static RelOptTable.ToRelContext simpleContext(RelOptCluster cluster) { // 声明公共静态方法simpleContext，返回ToRelContext类型
    return simpleContext(cluster, ImmutableList.of()); // 调用两参数版本的simpleContext方法，传入空的不可变列表作为hints
  }

  /** Creates a simple {@code ToRelContext} that cannot expand views. */
  // 方法注释：创建一个简单的ToRelContext，该上下文不能扩展视图
  // 这个方法提供了完整的参数控制，可以指定hints
  // 当调用expandView方法时会抛出UnsupportedOperationException异常，表示不支持视图扩展
  // 参数cluster：关系优化集群，提供了优化器运行所需的上下文环境
  // 参数hints：表提示列表，用于指导优化器如何处理表
  // 返回值：返回一个不支持视图扩展的ToRelContext实例
  public static RelOptTable.ToRelContext simpleContext( // 声明公共静态方法simpleContext，返回ToRelContext类型
      RelOptCluster cluster, // 参数：关系优化集群，提供优化器上下文
      List<RelHint> hints) { // 参数：关系提示列表，包含优化器提示信息
    return new RelOptTable.ToRelContext() { // 返回ToRelContext的匿名内部类实例
      @Override public RelOptCluster getCluster() { // 重写getCluster方法，返回关系优化集群
        return cluster; // 返回传入的cluster参数
      }

      @Override public RelRoot expandView(RelDataType rowType, String queryString, // 重写expandView方法，但抛出异常表示不支持
          List<String> schemaPath, @Nullable List<String> viewPath) { // 参数：rowType是视图的行类型，queryString是视图定义的SQL，schemaPath是模式路径，viewPath是视图路径
        throw new UnsupportedOperationException(); // 抛出不支持操作异常，表示这个上下文不支持视图扩展
      }

      @Override public List<RelHint> getTableHints() { // 重写getTableHints方法，返回表提示列表
        return hints; // 返回传入的hints参数
      }
    };
  }
}
