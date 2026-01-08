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
package org.apache.calcite.rel.metadata;  // 声明包名，该类位于org.apache.calcite.rel.metadata包下，是Calcite关系表达式元数据提供者相关的包

import org.apache.calcite.rel.RelNode;  // 导入RelNode类，表示关系表达式节点，是Calcite中所有关系操作符的基类
import org.apache.calcite.util.Util;  // 导入Util工具类，提供通用的工具方法

import com.google.common.collect.ImmutableList;  // 导入Google Guava的不可变列表类，用于存储不可变的RelMetadataProvider列表
import com.google.common.collect.ImmutableMultimap;  // 导入Google Guava的不可变多重映射类，用于存储方法到处理器的映射
import com.google.common.collect.Multimap;  // 导入Google Guava的多重映射接口，一个键可以对应多个值

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入可空注解，用于标记可能为null的值

import java.lang.reflect.InvocationHandler;  // 导入Java反射的调用处理器接口，用于动态代理
import java.lang.reflect.InvocationTargetException;  // 导入Java反射的调用目标异常，当被调用的方法抛出异常时使用
import java.lang.reflect.Method;  // 导入Java反射的方法类，表示类的方法
import java.lang.reflect.Proxy;  // 导入Java反射的代理类，用于创建动态代理对象
import java.util.ArrayList;  // 导入Java的动态数组列表类，用于存储可变长度的列表
import java.util.List;  // 导入Java的列表接口，表示有序的集合

/**
 * 链式关系元数据提供者类，实现了{@link RelMetadataProvider}接口
 * 该类使用责任链模式（Chain of Responsibility Pattern）来管理多个元数据提供者
 * 
 * <p>当消费者调用{@link #apply}方法请求特定类型的{@link RelNode}和{@link Metadata}的提供者时，
 * 会扫描底层提供者列表，依次尝试从每个提供者中获取所需的元数据
 * 
 * <p>该类的主要作用是：
 * 1. 将多个RelMetadataProvider组合成一个链式结构
 * 2. 按顺序遍历每个提供者，尝试获取元数据
 * 3. 使用动态代理技术，将多个元数据对象组合成一个统一的接口
 * 4. 当调用元数据方法时，依次调用每个提供者的元数据，返回第一个非null的结果
 * 
 * <p>这种设计模式的优势：
 * - 可以灵活地组合多个元数据提供者
 * - 提供者之间相互独立，可以动态添加或移除
 * - 遵循开闭原则，易于扩展
 */
public class ChainedRelMetadataProvider implements RelMetadataProvider {  // 定义ChainedRelMetadataProvider类，实现RelMetadataProvider接口
  //~ Instance fields --------------------------------------------------------
  // 实例字段分隔标记，用于代码格式化

  private final ImmutableList<RelMetadataProvider> providers;  // 存储不可变的RelMetadataProvider列表，这是责任链中的所有提供者，按顺序存储，final表示初始化后不可修改

  //~ Constructors -----------------------------------------------------------
  // 构造方法分隔标记，用于代码格式化

  /**
   * 创建一个链式元数据提供者
   * 该构造方法接收一个RelMetadataProvider列表，并将其封装成链式结构
   */
  @SuppressWarnings("argument.type.incompatible")  // 抑制类型不兼容的警告，因为泛型擦除导致的类型检查问题
  protected ChainedRelMetadataProvider(  // 定义受保护的构造方法，只能由子类或同包类调用
      ImmutableList<RelMetadataProvider> providers) {  // 参数：不可变的RelMetadataProvider列表，包含所有要链式调用的元数据提供者
    this.providers = providers;  // 将传入的提供者列表赋值给实例变量，使用this关键字区分成员变量和参数
    assert !providers.contains(this);  // 断言：确保提供者列表中不包含当前对象本身，避免循环引用导致无限递归
  }  // 构造方法结束

  //~ Methods ----------------------------------------------------------------
  // 方法分隔标记，用于代码格式化

  @Override public boolean equals(@Nullable Object obj) {  // 重写equals方法，用于比较两个ChainedRelMetadataProvider对象是否相等
    return obj == this  // 如果obj就是当前对象，直接返回true（自反性）
        || obj instanceof ChainedRelMetadataProvider  // 或者obj是ChainedRelMetadataProvider类的实例
        && providers.equals(((ChainedRelMetadataProvider) obj).providers);  // 并且两个对象的providers列表相等，使用equals比较列表内容
  }  // equals方法结束

  @Override public int hashCode() {  // 重写hashCode方法，用于计算对象的哈希值，与equals方法配套使用
    return providers.hashCode();  // 返回providers列表的哈希值，确保相等的对象有相同的哈希码
  }  // hashCode方法结束

  @Deprecated // to be removed before 2.0  // 标记为已过时，将在2.0版本前移除
  @Override public <@Nullable M extends @Nullable Metadata> @Nullable UnboundMetadata<M> apply(  // 重写apply方法，用于获取指定RelNode类型和Metadata类型的未绑定元数据
      Class<? extends RelNode> relClass,  // 参数：关系表达式节点的Class对象，指定要查询的关系类型
      final Class<? extends M> metadataClass) {  // 参数：元数据接口的Class对象，指定要查询的元数据类型，final表示局部变量不可重新赋值
    final List<UnboundMetadata<M>> functions = new ArrayList<>();  // 创建一个动态列表，用于存储从各个提供者获取的UnboundMetadata对象
    for (RelMetadataProvider provider : providers) {  // 遍历providers列表中的每个元数据提供者
      final UnboundMetadata<M> function =  // 调用当前提供者的apply方法，获取未绑定的元数据函数
          provider.apply(relClass, metadataClass);  // 传入关系类型和元数据类型，让提供者尝试提供对应的元数据
      if (function == null) {  // 如果当前提供者无法提供该元数据（返回null）
        continue;  // 跳过当前提供者，继续尝试下一个提供者
      }  // if判断结束
      functions.add(function);  // 如果提供者返回了有效的元数据函数，将其添加到functions列表中
    }  // for循环结束，遍历完所有提供者
    switch (functions.size()) {  // 根据获取到的元数据函数数量进行不同的处理
    case 0:  // 如果没有找到任何提供者能够提供该元数据
      return null;  // 返回null，表示无法提供该元数据
    case 1:  // 如果只有一个提供者能够提供该元数据
      return functions.get(0);  // 直接返回该提供者的元数据函数，无需创建代理
    default:  // 如果有多个提供者都能提供该元数据（2个或更多）
      return (rel, mq) -> {  // 返回一个Lambda表达式，该表达式实现了UnboundMetadata接口的bind方法
        final List<Metadata> metadataList = new ArrayList<>();  // 创建一个列表，用于存储绑定后的元数据对象
        for (UnboundMetadata<M> function : functions) {  // 遍历所有未绑定的元数据函数
          final Metadata metadata = function.bind(rel, mq);  // 调用bind方法，将元数据绑定到具体的关系表达式和元数据查询上下文
          if (metadata != null) {  // 如果绑定成功（返回非null的元数据对象）
            metadataList.add(metadata);  // 将绑定后的元数据对象添加到列表中
          }  // if判断结束
        }  // for循环结束，遍历完所有元数据函数
        return metadataClass.cast(  // 使用类型转换，将代理对象转换为指定的元数据类型
            Proxy.newProxyInstance(metadataClass.getClassLoader(),  // 创建动态代理实例，使用元数据类的类加载器
                new Class[]{metadataClass},  // 指定代理要实现的接口，即元数据接口
                new ChainedInvocationHandler(metadataList)));  // 指定调用处理器，使用自定义的链式调用处理器
      };  // Lambda表达式结束
    }  // switch语句结束
  }  // apply方法结束

  @Deprecated // to be removed before 2.0  // 标记为已过时，将在2.0版本前移除
  @Override public <M extends Metadata> Multimap<Method, MetadataHandler<M>> handlers(  // 重写handlers方法，用于获取指定元数据定义的方法到处理器的映射
      MetadataDef<M> def) {  // 参数：元数据定义对象，描述了元数据的接口和方法
    final ImmutableMultimap.Builder<Method, MetadataHandler<M>> builder =  // 创建不可变多重映射的构建器
        ImmutableMultimap.builder();  // 用于构建Method到MetadataHandler的映射关系
    for (RelMetadataProvider provider : providers.reverse()) {  // 反向遍历providers列表（从最后一个到第一个）
      builder.putAll(provider.handlers(def));  // 将当前提供者的所有处理器映射添加到构建器中，反向遍历确保后面的提供者优先级更高
    }  // for循环结束
    return builder.build();  // 构建并返回不可变的多重映射
  }  // handlers方法结束

  @Override public List<MetadataHandler<?>> handlers(  // 重写handlers方法，用于获取指定处理器类的所有处理器实例
      Class<? extends MetadataHandler<?>> handlerClass) {  // 参数：处理器类的Class对象，指定要查询的处理器类型
    final ImmutableList.Builder<MetadataHandler<?>> builder =  // 创建不可变列表的构建器
        ImmutableList.builder();  // 用于构建处理器列表
    for (RelMetadataProvider provider : providers) {  // 正向遍历providers列表
      builder.addAll(provider.handlers(handlerClass));  // 将当前提供者的所有处理器添加到构建器中
    }  // for循环结束
    return builder.build();  // 构建并返回不可变的处理器列表
  }  // handlers方法结束

  /** Creates a chain. */  // 创建一个链式元数据提供者
  public static RelMetadataProvider of(List<RelMetadataProvider> list) {  // 静态工厂方法，用于创建链式元数据提供者
    return new ChainedRelMetadataProvider(ImmutableList.copyOf(list));  // 将输入列表转换为不可变列表，然后创建ChainedRelMetadataProvider实例
  }  // of方法结束

  /** Invocation handler that calls a list of {@link Metadata} objects,
   * returning the first non-null value. */  // 链式调用处理器，实现InvocationHandler接口，用于调用多个Metadata对象，返回第一个非null的结果
  private static class ChainedInvocationHandler implements InvocationHandler {  // 定义私有静态内部类ChainedInvocationHandler，实现InvocationHandler接口
    private final List<Metadata> metadataList;  // 存储不可变的Metadata对象列表，这些对象将被依次调用

    ChainedInvocationHandler(List<Metadata> metadataList) {  // 构造方法，接收Metadata对象列表
      this.metadataList = ImmutableList.copyOf(metadataList);  // 将输入列表转换为不可变列表并赋值给实例变量
    }  // 构造方法结束

    @Override public @Nullable Object invoke(Object proxy, Method method, @Nullable Object[] args)  // 重写invoke方法，当代理对象的方法被调用时执行
        throws Throwable {  // 声明可能抛出Throwable异常
      for (Metadata metadata : metadataList) {  // 遍历metadataList中的每个元数据对象
        try {  // 尝试执行方法调用
          final Object o = method.invoke(metadata, args);  // 使用反射调用当前元数据对象的指定方法，传入参数
          if (o != null) {  // 如果调用结果不为null
            return o;  // 立即返回该结果，不再继续调用后续的元数据对象
          }  // if判断结束
        } catch (InvocationTargetException e) {  // 捕获调用目标异常（被调用的方法抛出异常）
          throw Util.throwAsRuntime(Util.causeOrSelf(e));  // 将异常转换为运行时异常并抛出，Util.causeOrSelf获取根本原因
        }  // try-catch块结束
      }  // for循环结束，遍历完所有元数据对象
      return null;  // 如果所有元数据对象都返回null，则返回null
    }  // invoke方法结束
  }  // ChainedInvocationHandler类结束
}  // ChainedRelMetadataProvider类结束
