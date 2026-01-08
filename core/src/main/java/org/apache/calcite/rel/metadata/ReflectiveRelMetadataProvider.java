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
// Apache Calcite 反射式关系表达式元数据提供者实现类,用于通过反射将元数据方法调用分发给目标对象的方法
package org.apache.calcite.rel.metadata;

import org.apache.calcite.rel.RelNode;  // 导入关系表达式节点类,表示关系代数操作
import org.apache.calcite.rex.RexNode;  // 导入行表达式节点类,表示表达式
import org.apache.calcite.runtime.FlatLists;  // 导入扁平列表工具类,用于创建扁平化的列表结构
import org.apache.calcite.util.BuiltInMethod;  // 导入内置方法枚举类,定义常用的元数据方法
import org.apache.calcite.util.ImmutableNullableList;  // 导入不可变可空列表构建器类
import org.apache.calcite.util.Pair;  // 导入键值对工具类,用于存储两个对象的组合
import org.apache.calcite.util.ReflectiveVisitor;  // 导入反射式访问者接口,支持基于反射的方法分发
import org.apache.calcite.util.Util;  // 导入通用工具类,提供各种辅助方法

import com.google.common.collect.ImmutableList;  // 导入Google Guava不可变列表类,提供线程安全的不可变列表
import com.google.common.collect.ImmutableMultimap;  // 导入Google Guava不可变多值映射类,支持一个键对应多个值
import com.google.common.collect.Multimap;  // 导入Google Guava多值映射接口,支持一对多的键值映射

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入空值检查注解,用于标记可空类型

import java.lang.reflect.InvocationTargetException;  // 导入反射调用异常类,处理方法调用时的异常
import java.lang.reflect.Method;  // 导入反射方法类,用于获取和调用方法
import java.lang.reflect.Proxy;  // 导入动态代理类,用于创建代理对象
import java.lang.reflect.UndeclaredThrowableException;  // 导入未声明抛出异常类,处理代理调用中的异常
import java.util.ArrayList;  // 导入动态数组列表类,提供可变大小的数组实现
import java.util.Arrays;  // 导入数组工具类,提供数组操作方法
import java.util.HashMap;  // 导入哈希映射类,提供基于哈希表的键值对存储
import java.util.HashSet;  // 导入哈希集合类,提供基于哈希表的集合实现
import java.util.List;  // 导入列表接口,定义有序集合
import java.util.Map;  // 导入映射接口,定义键值对集合
import java.util.Set;  // 导入集合接口,定义无序不重复元素集合
import java.util.concurrent.ConcurrentHashMap;  // 导入并发哈希映射类,提供线程安全的哈希表实现
import java.util.concurrent.ConcurrentMap;  // 导入并发映射接口,定义线程安全的键值对集合

import static com.google.common.base.Preconditions.checkArgument;  // 导入前置条件检查方法,用于参数校验

import static org.apache.calcite.util.ReflectUtil.isPublic;  // 导入反射工具方法,检查方法是否为public
import static org.apache.calcite.util.ReflectUtil.isStatic;  // 导入反射工具方法,检查方法是否为static

import static java.util.Objects.requireNonNull;  // 导入对象非空检查方法,用于参数校验

/**
 * RelMetadataProvider接口的实现类,通过反射将元数据方法调用分发给给定对象的方法
 *
 * <p>目标对象上的方法必须是public且非static的,并且与实现的元数据方法具有相同的签名,
 * 除了第一个额外的参数类型为{@link RelNode}或其子类。该参数指示此提供者可以处理的关系表达式
 *
 * <p>例如,参见{@link RelMdColumnOrigins#SOURCE}
 * 
 * 这个类是Calcite元数据系统的核心组件之一,它使用Java反射机制来实现元数据查询的动态分发
 * 主要功能包括:
 * 1. 为不同类型的RelNode(关系表达式节点)提供对应的元数据查询实现
 * 2. 通过反射自动匹配和调用目标对象中的方法
 * 3. 支持元数据的懒加载和缓存
 * 4. 处理元数据查询中的循环依赖问题
 * 
 * 工作原理:
 * - 当查询某个RelNode的元数据时(如选择性、行数等),系统会查找对应的MetadataHandler
 * - 通过反射机制,将元数据接口的方法调用转换为对具体Handler类方法的调用
 * - Handler类的方法签名要求:第一个参数是RelNode类型,第二个参数是RelMetadataQuery类型,
 *   后续参数与元数据接口方法的参数一致
 * - 使用动态代理技术创建元数据接口的实现,拦截方法调用并转发给实际的Handler
 */
public class ReflectiveRelMetadataProvider  // 定义反射式关系表达式元数据提供者类,实现RelMetadataProvider和ReflectiveVisitor接口
    implements RelMetadataProvider, ReflectiveVisitor {  // 实现元数据提供者接口和反射式访问者接口

  //~ Instance fields --------------------------------------------------------  // 实例字段区域标记
  @Deprecated // to be removed before 2.0  // 标记为已过时,将在2.0版本前移除
  private final ConcurrentMap<Class<RelNode>, UnboundMetadata> map;  // 并发映射,存储RelNode类到未绑定元数据的映射关系,用于缓存不同类型关系表达式的元数据处理器
  @Deprecated // to be removed before 2.0  // 标记为已过时,将在2.0版本前移除
  private final Class<? extends Metadata> metadataClass0;  // 元数据接口的Class对象,表示此提供者支持的元数据类型(如Selectivity、RowCount等)
  @Deprecated // to be removed before 2.0  // 标记为已过时,将在2.0版本前移除
  private final ImmutableMultimap<Method, MetadataHandler> handlerMap;  // 不可变多值映射,存储元数据方法到元数据处理器对象的映射关系,一个方法可能有多个处理器
  private final Class<? extends MetadataHandler<?>> handlerClass;  // 元数据处理器类的Class对象,表示此提供者使用的处理器类型
  private final ImmutableList<MetadataHandler<?>> handlers;  // 不可变列表,存储所有元数据处理器对象的集合,用于快速遍历所有处理器

  //~ Constructors -----------------------------------------------------------  // 构造方法区域标记

  /**
   * Creates a ReflectiveRelMetadataProvider.  // 创建ReflectiveRelMetadataProvider实例
   *
   * @param map Map  // RelNode类到未绑定元数据的映射关系
   * @param metadataClass0 Metadata class  // 元数据接口的Class对象
   * @param handlerMap Methods handled and the objects to call them on  // 方法到处理器对象的映射关系
   */
  protected ReflectiveRelMetadataProvider(  // 受保护的构造方法,创建反射式元数据提供者实例
      ConcurrentMap<Class<RelNode>, UnboundMetadata> map,  // 参数:RelNode类到未绑定元数据的并发映射
      Class<? extends Metadata> metadataClass0,  // 参数:元数据接口的Class对象
      Multimap<Method, MetadataHandler<?>> handlerMap,  // 参数:方法到处理器对象的多值映射
      Class<? extends MetadataHandler<?>> handlerClass) {  // 参数:元数据处理器类的Class对象
    checkArgument(!map.isEmpty(), "ReflectiveRelMetadataProvider "  // 检查参数:映射不能为空,否则抛出异常提示方法命名可能错误
        + "methods map is empty; are your methods named wrong?");  // 异常消息提示用户检查方法命名
    this.map = map;  // 将传入的映射赋值给实例变量
    this.metadataClass0 = metadataClass0;  // 将传入的元数据类赋值给实例变量
    this.handlerMap = ImmutableMultimap.copyOf(handlerMap);  // 将传入的处理器映射转换为不可变副本并赋值
    this.handlerClass = handlerClass;  // 将传入的处理器类赋值给实例变量
    this.handlers = ImmutableList.copyOf(handlerMap.values());  // 从处理器映射中提取所有处理器对象并转换为不可变列表
  }  // 构造方法结束

  /** Returns an implementation of {@link RelMetadataProvider} that scans for  // 返回一个RelMetadataProvider实现,扫描具有前置参数的方法
   * methods with a preceding argument.  // 扫描带有前置参数的方法
   *
   * <p>For example, {@link BuiltInMetadata.Selectivity} has a method  // 例如,BuiltInMetadata.Selectivity有一个方法
   * {@link BuiltInMetadata.Selectivity#getSelectivity(RexNode)}.  // getSelectivity(RexNode)方法
   * A class  // 一个类
   *
   * <blockquote><pre><code>  // 代码块开始
   * class RelMdSelectivity {  // 定义RelMdSelectivity类,提供选择性元数据实现
   *   public Double getSelectivity(Union rel, RexNode predicate) { }  // 为Union关系表达式提供选择性计算方法
   *   public Double getSelectivity(Filter rel, RexNode predicate) { }  // 为Filter关系表达式提供选择性计算方法
   * </code></pre></blockquote>  // 代码块结束
   *
   * <p>provides implementations of selectivity for relational expressions  // 为关系表达式提供选择性实现
   * that extend {@link org.apache.calcite.rel.core.Union}  // 扩展Union的关系表达式
   * or {@link org.apache.calcite.rel.core.Filter}.  // 或扩展Filter的关系表达式
   */
  @Deprecated // to be removed before 2.0  // 标记为已过时,将在2.0版本前移除
  public static RelMetadataProvider reflectiveSource(Method method,  // 静态方法:创建反射式元数据提供者,参数为单个元数据方法和目标处理器
      MetadataHandler target) {  // 参数:元数据处理器对象,包含实际的元数据计算逻辑
    return reflectiveSource(target, ImmutableList.of(method), target.getDef().handlerClass);  // 调用重载方法,将单个方法包装为列表并传递处理器类
  }  // 方法结束

  /** Returns a reflective metadata provider that implements several  // 返回一个反射式元数据提供者,实现多个方法
   * methods. */  // 实现多个元数据方法
  @Deprecated // to be removed before 2.0  // 标记为已过时,将在2.0版本前移除
  public static RelMetadataProvider reflectiveSource(MetadataHandler target,  // 静态方法:创建反射式元数据提供者,参数为处理器对象和多个元数据方法
      Method... methods) {  // 可变参数:元数据方法数组,表示要实现的多个元数据查询方法
    return reflectiveSource(target, ImmutableList.copyOf(methods),  // 调用重载方法,将方法数组转换为不可变列表
        target.getDef().handlerClass);  // 传递处理器类定义
  }  // 方法结束

  @SuppressWarnings("deprecation")  // 抑制已过时警告
  public static <M extends Metadata> RelMetadataProvider reflectiveSource(  // 泛型静态方法:创建反射式元数据提供者,支持泛型元数据类型
      MetadataHandler<? extends M> handler,  // 参数:元数据处理器对象,处理指定类型的元数据
      Class<? extends MetadataHandler<M>> handlerClass) {  // 参数:元数据处理器类的Class对象
    // When deprecated code is removed, handler.getDef().methods will  // 当已过时代码被移除时,handler.getDef().methods将不再需要
    // no longer be required  // 不再需要
    return reflectiveSource(handler, handler.getDef().methods, handlerClass);  // 调用私有重载方法,使用处理器定义中的方法集合
  }  // 方法结束

  @Deprecated // to be removed before 2.0  // 标记为已过时,将在2.0版本前移除
  private static RelMetadataProvider reflectiveSource(  // 私有静态方法:核心的反射式元数据提供者创建逻辑
      final MetadataHandler target, final ImmutableList<Method> methods,  // 参数:目标处理器对象和要实现的元数据方法列表
      final Class<? extends MetadataHandler<?>> handlerClass) {  // 参数:元数据处理器类的Class对象
    final Space2 space = Space2.create(target, methods);  // 创建工作空间对象,用于分析方法和处理器的关系

    // This needs to be a concurrent map since RelMetadataProvider are cached in static  // 这里需要使用并发映射,因为RelMetadataProvider被缓存在静态字段中
    // fields, thus the map is subject to concurrent modifications later.  // 因此该映射后续可能受到并发修改
    // See map.put in org.apache.calcite.rel.metadata.ReflectiveRelMetadataProvider.apply(  // 参见apply方法中的map.put操作
    // java.lang.Class<? extends org.apache.calcite.rel.RelNode>)  // 可能发生并发修改的位置
    final ConcurrentMap<Class<RelNode>, UnboundMetadata> methodsMap = new ConcurrentHashMap<>();  // 创建并发映射,存储RelNode类到未绑定元数据的映射
    for (Class<RelNode> key : space.classes) {  // 遍历工作空间中的所有RelNode类
      ImmutableNullableList.Builder<Method> builder =  // 创建可空不可变列表构建器,用于构建方法列表
          ImmutableNullableList.builder();  // 初始化构建器
      for (final Method method : methods) {  // 遍历所有元数据方法
        builder.add(space.find(key, method));  // 为当前RelNode类查找对应的方法实现并添加到构建器
      }  // 内层循环结束
      final List<Method> handlerMethods = builder.build();  // 构建不可变的方法列表,包含当前RelNode类的所有方法实现
      final UnboundMetadata function = (rel, mq) ->  // 创建未绑定元数据函数,接收RelNode和RelMetadataQuery参数
          (Metadata) Proxy.newProxyInstance(  // 使用动态代理创建元数据接口的实现对象
              space.metadataClass0.getClassLoader(),  // 使用元数据接口的类加载器
              new Class[]{space.metadataClass0}, (proxy, method, args) -> {  // 指定要实现的接口和调用处理器
                // Suppose we are an implementation of Selectivity  // 假设我们是Selectivity的实现
                // that wraps "filter", a LogicalFilter. Then we  // 包装了一个LogicalFilter节点"filter"
                // implement  // 那么我们实现
                //   Selectivity.selectivity(rex)  // Selectivity.selectivity(rex)方法
                // by calling method  // 通过调用方法
                //   new SelectivityImpl().selectivity(filter, rex)  // new SelectivityImpl().selectivity(filter, rex)来实现
                if (method.equals(BuiltInMethod.METADATA_REL.method)) {  // 如果调用的是获取RelNode的方法
                  return rel;  // 返回当前关系表达式节点
                }  // 条件分支结束
                if (method.equals(BuiltInMethod.OBJECT_TO_STRING.method)) {  // 如果调用的是toString方法
                  return space.metadataClass0.getSimpleName() + "(" + rel + ")";  // 返回元数据类名和关系表达式的字符串表示
                }  // 条件分支结束
                int i = methods.indexOf(method);  // 查找当前方法在方法列表中的索引
                if (i < 0) {  // 如果找不到方法
                  throw new AssertionError("not handled: " + method  // 抛出断言错误,表示方法未被处理
                      + " for " + rel);  // 包含方法名和关系表达式信息
                }  // 条件分支结束
                final Method handlerMethod = handlerMethods.get(i);  // 根据索引获取对应的处理器方法
                if (handlerMethod == null) {  // 如果处理器方法为null
                  throw new AssertionError("not handled: " + method  // 抛出断言错误,表示方法未被处理
                      + " for " + rel);  // 包含方法名和关系表达式信息
                }  // 条件分支结束
                final Object[] args1;  // 声明实际调用参数数组
                final List key1;  // 声明缓存键列表
                if (args == null) {  // 如果没有额外参数
                  args1 = new Object[]{rel, mq};  // 创建参数数组,包含关系表达式和元数据查询对象
                  key1 = FlatLists.of(rel, method);  // 创建扁平列表作为缓存键
                } else {  // 如果有额外参数
                  args1 = new Object[args.length + 2];  // 创建参数数组,长度为原参数长度加2
                  args1[0] = rel;  // 第一个参数是关系表达式
                  args1[1] = mq;  // 第二个参数是元数据查询对象
                  System.arraycopy(args, 0, args1, 2, args.length);  // 将原参数复制到新数组的剩余位置

                  final Object[] args2 = args1.clone();  // 克隆参数数组用于构建缓存键
                  args2[1] = method; // replace RelMetadataQuery with method  // 将第二个参数替换为方法对象
                  for (int j = 0; j < args2.length; j++) {  // 遍历参数数组
                    if (args2[j] == null) {  // 如果参数为null
                      args2[j] = NullSentinel.INSTANCE;  // 替换为空值哨兵对象
                    } else if (args2[j] instanceof RexNode) {  // 如果参数是RexNode类型
                      // Can't use RexNode.equals - it is not deep  // 不能使用RexNode.equals,因为它不是深度比较
                      args2[j] = args2[j].toString();  // 转换为字符串表示
                    }  // 条件分支结束
                  }  // 循环结束
                  key1 = FlatLists.copyOf(args2);  // 创建参数数组的不可变扁平列表副本作为缓存键
                }  // 条件分支结束
                if (mq.map.put(rel, key1, NullSentinel.INSTANCE) != null) {  // 尝试将缓存键放入元数据查询的映射中
                  throw new CyclicMetadataException();  // 如果键已存在,抛出循环元数据异常
                }  // 条件分支结束
                try {  // 开始异常处理
                  return handlerMethod.invoke(target, args1);  // 通过反射调用处理器方法并返回结果
                } catch (InvocationTargetException  // 捕获反射调用异常
                    | UndeclaredThrowableException e) {  // 捕获未声明抛出异常
                  throw Util.throwAsRuntime(Util.causeOrSelf(e));  // 将异常转换为运行时异常并抛出
                } finally {  // finally块,确保清理
                  mq.map.remove(rel, key1);  // 从映射中移除缓存键,释放资源
                }  // finally块结束
              });  // 动态代理调用处理器结束
      methodsMap.put(key, function);  // 将RelNode类和对应的未绑定元数据函数放入映射
    }  // 外层循环结束
    return new ReflectiveRelMetadataProvider(methodsMap, space.metadataClass0,  // 创建并返回ReflectiveRelMetadataProvider实例
        space.providerMap, handlerClass);  // 传递方法映射、元数据类、处理器映射和处理器类
  }  // 方法结束

  @Deprecated // to be removed before 2.0  // 标记为已过时,将在2.0版本前移除
  @Override public <M extends Metadata> Multimap<Method, MetadataHandler<M>> handlers(  // 重写接口方法:返回指定元数据定义的处理器映射
      MetadataDef<M> def) {  // 参数:元数据定义对象,包含元数据的方法集合
    final ImmutableMultimap.Builder<Method, MetadataHandler<M>> builder =  // 创建不可变多值映射构建器
        ImmutableMultimap.builder();  // 初始化构建器
    for (Map.Entry<Method, MetadataHandler> entry : handlerMap.entries()) {  // 遍历处理器映射的所有条目
      if (def.methods.contains(entry.getKey())) {  // 如果元数据定义包含当前方法
        //noinspection unchecked  // 抑制未检查转换警告
        builder.put(entry.getKey(), entry.getValue());  // 将方法和处理器添加到构建器
      }  // 条件分支结束
    }  // 循环结束
    return builder.build();  // 构建并返回不可变多值映射
  }  // 方法结束

  @Override public List<MetadataHandler<?>> handlers(  // 重写接口方法:返回指定处理器类的处理器列表
      Class<? extends MetadataHandler<?>> handlerClass) {  // 参数:元数据处理器类的Class对象
    if (this.handlerClass.isAssignableFrom(handlerClass)) {  // 如果当前处理器类可以赋值给指定的处理器类
      return handlers;  // 返回所有处理器的不可变列表
    } else {  // 否则
      return ImmutableList.of();  // 返回空列表
    }  // 条件分支结束
  }  // 方法结束

  @Deprecated // to be removed before 2.0  // 标记为已过时,将在2.0版本前移除
  private static boolean couldImplement(Method handlerMethod, Method method) {  // 私有静态方法:检查处理器方法是否可以实现元数据方法
    if (!handlerMethod.getName().equals(method.getName())  // 如果方法名不相同
        || isStatic(handlerMethod)  // 或者处理器方法是静态的
        || !isPublic(handlerMethod)) {  // 或者处理器方法不是public的
      return false;  // 返回false,表示不能实现
    }  // 条件分支结束
    final Class<?>[] parameterTypes1 = handlerMethod.getParameterTypes();  // 获取处理器方法的参数类型数组
    final Class<?>[] parameterTypes = method.getParameterTypes();  // 获取元数据方法的参数类型数组
    return parameterTypes1.length == parameterTypes.length + 2  // 检查处理器方法参数长度是否比元数据方法多2
        && RelNode.class.isAssignableFrom(parameterTypes1[0])  // 检查第一个参数是否是RelNode或其子类
        && RelMetadataQuery.class == parameterTypes1[1]  // 检查第二个参数是否是RelMetadataQuery类
        && Arrays.asList(parameterTypes)  // 将元数据方法参数类型转换为列表
            .equals(Util.skip(Arrays.asList(parameterTypes1), 2));  // 检查剩余参数是否与元数据方法参数完全一致
  }  // 方法结束

  //~ Methods ----------------------------------------------------------------  // 方法区域标记
  @Deprecated // to be removed before 2.0  // 标记为已过时,将在2.0版本前移除
  @Override public <@Nullable M extends @Nullable Metadata> @Nullable UnboundMetadata<M> apply(  // 重写接口方法:为指定的RelNode类和元数据类返回未绑定元数据
      Class<? extends RelNode> relClass, Class<? extends M> metadataClass) {  // 参数:关系表达式类和元数据类
    if (metadataClass == metadataClass0) {  // 如果请求的元数据类与此提供者支持的元数据类相同
      return apply(relClass);  // 调用重载方法,只传递RelNode类
    } else {  // 否则
      return null;  // 返回null,表示不支持
    }  // 条件分支结束
  }  // 方法结束

  @SuppressWarnings({ "unchecked", "SuspiciousMethodCalls" })  // 抑制未检查转换和可疑方法调用警告
  @Deprecated // to be removed before 2.0  // 标记为已过时,将在2.0版本前移除
  public <@Nullable M extends @Nullable Metadata> @Nullable UnboundMetadata<M> apply(  // 重载方法:为指定的RelNode类返回未绑定元数据
      Class<? extends RelNode> relClass) {  // 参数:关系表达式类
    List<Class<? extends RelNode>> newSources = new ArrayList<>();  // 创建列表,存储需要缓存的新RelNode类
    for (;;) {  // 无限循环,直到找到匹配或确定没有匹配
      UnboundMetadata<M> function = map.get(relClass);  // 从映射中获取当前RelNode类的未绑定元数据
      if (function != null) {  // 如果找到了匹配
        for (@SuppressWarnings("rawtypes") Class clazz : newSources) {  // 遍历所有新发现的RelNode类
          map.put(clazz, function);  // 将找到的未绑定元数据缓存到这些类上
        }  // 循环结束
        return function;  // 返回找到的未绑定元数据
      } else {  // 如果没找到
        newSources.add(relClass);  // 将当前类添加到新源列表
      }  // 条件分支结束
      for (Class<?> interfaceClass : relClass.getInterfaces()) {  // 遍历当前类的所有接口
        if (RelNode.class.isAssignableFrom(interfaceClass)) {  // 如果接口是RelNode的子接口
          final UnboundMetadata<M> function2 = map.get(interfaceClass);  // 从映射中获取接口的未绑定元数据
          if (function2 != null) {  // 如果在接口上找到了匹配
            for (@SuppressWarnings("rawtypes") Class clazz : newSources) {  // 遍历所有新发现的RelNode类
              map.put(clazz, function2);  // 将找到的未绑定元数据缓存到这些类上
            }  // 循环结束
            return function2;  // 返回找到的未绑定元数据
          }  // 条件分支结束
        }  // 条件分支结束
      }  // 循环结束
      Class<?> superclass = relClass.getSuperclass();  // 获取当前类的父类
      if (superclass != null && RelNode.class.isAssignableFrom(superclass)) {  // 如果父类存在且是RelNode的子类
        relClass = (Class<RelNode>) superclass;  // 将当前类设置为父类,继续向上查找
      } else {  // 如果没有可查找的父类
        return null;  // 返回null,表示没有找到匹配
      }  // 条件分支结束
    }  // 无限循环结束
  }  // 方法结束

  /** Workspace for computing which methods can act as handlers for  // 工作空间,用于计算哪些方法可以作为给定元数据方法的处理器
   * given metadata methods. */  // 给定元数据方法的处理器
  @Deprecated // to be removed before 2.0  // 标记为已过时,将在2.0版本前移除
  static class Space {  // 静态内部类:工作空间,用于分析方法和处理器的匹配关系
    final Set<Class<RelNode>> classes = new HashSet<>();  // 存储此提供者处理的所有RelNode类的集合
    final Map<Pair<Class<RelNode>, Method>, Method> handlerMap = new HashMap<>();  // 存储RelNode类和方法到处理器方法的映射
    final ImmutableMultimap<Method, MetadataHandler<?>> providerMap;  // 存储方法到处理器对象的不可变多值映射

    Space(Multimap<Method, MetadataHandler<?>> providerMap) {  // 构造方法:创建工作空间对象
      this.providerMap = ImmutableMultimap.copyOf(providerMap);  // 将传入的映射转换为不可变副本

      // Find the distinct set of RelNode classes handled by this provider,  // 查找此提供者处理的不同的RelNode类集合
      // ordered base-class first.  // 按基类优先的顺序
      for (Map.Entry<Method, MetadataHandler<?>> entry : providerMap.entries()) {  // 遍历处理器映射的所有条目
        final Method method = entry.getKey();  // 获取元数据方法
        final MetadataHandler<?> provider = entry.getValue();  // 获取处理器对象
        for (final Method handlerMethod : provider.getClass().getMethods()) {  // 遍历处理器类的所有公共方法
          if (couldImplement(handlerMethod, method)) {  // 如果处理器方法可以实现元数据方法
            @SuppressWarnings("unchecked") final Class<RelNode> relNodeClass =  // 获取处理器方法的第一个参数类型,即RelNode类
                (Class<RelNode>) handlerMethod.getParameterTypes()[0];  // 强制转换为RelNode类型
            classes.add(relNodeClass);  // 将RelNode类添加到类集合
            handlerMap.put(Pair.of(relNodeClass, method), handlerMethod);  // 将RelNode类和方法组合映射到处理器方法
          }  // 条件分支结束
        }  // 循环结束
      }  // 循环结束
    }  // 构造方法结束

    /** Finds an implementation of a method for {@code relNodeClass} or its  // 查找指定RelNode类或其最近基类的方法实现
     * nearest base class. Assumes that base classes have already been added to  // 假设基类已经添加到
     * {@code map}. */  // 映射中
    @SuppressWarnings({ "unchecked", "SuspiciousMethodCalls" })  // 抑制未检查转换和可疑方法调用警告
    Method find(final Class<? extends RelNode> relNodeClass, Method method) {  // 方法:查找指定RelNode类和方法的处理器实现
      requireNonNull(relNodeClass, "relNodeClass");  // 检查参数非空
      for (Class r = relNodeClass;;) {  // 无限循环,从当前类开始向上查找
        Method implementingMethod = handlerMap.get(Pair.of(r, method));  // 尝试从映射中获取当前类的处理器方法
        if (implementingMethod != null) {  // 如果找到了实现
          return implementingMethod;  // 返回找到的处理器方法
        }  // 条件分支结束
        for (Class<?> clazz : r.getInterfaces()) {  // 遍历当前类的所有接口
          if (RelNode.class.isAssignableFrom(clazz)) {  // 如果接口是RelNode的子接口
            implementingMethod = handlerMap.get(Pair.of(clazz, method));  // 尝试从映射中获取接口的处理器方法
            if (implementingMethod != null) {  // 如果找到了实现
              return implementingMethod;  // 返回找到的处理器方法
            }  // 条件分支结束
          }  // 条件分支结束
        }  // 循环结束
        r = r.getSuperclass();  // 获取父类
        if (r == null || !RelNode.class.isAssignableFrom(r)) {  // 如果父类为null或不是RelNode的子类
          throw new IllegalArgumentException("No handler for method [" + method  // 抛出非法参数异常,表示没有找到处理器
              + "] applied to argument of type [" + relNodeClass  // 包含方法名和RelNode类名
              + "]; we recommend you create a catch-all (RelNode) handler");  // 建议创建一个通用的RelNode处理器
        }  // 条件分支结束
      }  // 无限循环结束
    }  // 方法结束
  }  // 内部类Space结束

  /** Extended work space. */  // 扩展的工作空间
  @Deprecated // to be removed before 2.0  // 标记为已过时,将在2.0版本前移除
  static class Space2 extends Space {  // 静态内部类:扩展工作空间,继承自Space类
    private final Class<Metadata> metadataClass0;  // 元数据接口的Class对象,表示此工作空间支持的元数据类型

    Space2(Class<Metadata> metadataClass0,  // 构造方法:创建扩展工作空间对象
        ImmutableMultimap<Method, MetadataHandler<?>> providerMap) {  // 参数:元数据类和方法到处理器的映射
      super(providerMap);  // 调用父类构造方法
      this.metadataClass0 = metadataClass0;  // 保存元数据类
    }  // 构造方法结束

    @Deprecated // to be removed before 2.0  // 标记为已过时,将在2.0版本前移除
    public static Space2 create(  // 静态工厂方法:创建扩展工作空间对象
        MetadataHandler<?> target,  // 参数:目标处理器对象
        ImmutableList<Method> methods) {  // 参数:要实现的元数据方法列表
      assert !methods.isEmpty();  // 断言方法列表不为空
      final Method method0 = methods.get(0);  // 获取第一个方法
      //noinspection unchecked  // 抑制未检查转换警告
      Class<Metadata> metadataClass0 = (Class) method0.getDeclaringClass();  // 获取方法声明的类,即元数据接口类
      assert Metadata.class.isAssignableFrom(metadataClass0);  // 断言该类是Metadata的子类
      for (Method method : methods) {  // 遍历所有方法
        assert method.getDeclaringClass() == metadataClass0;  // 断言所有方法都来自同一个元数据接口
      }  // 循环结束

      final ImmutableMultimap.Builder<Method, MetadataHandler<?>> providerBuilder =  // 创建不可变多值映射构建器
          ImmutableMultimap.builder();  // 初始化构建器
      for (final Method method : methods) {  // 遍历所有元数据方法
        providerBuilder.put(method, target);  // 将每个方法映射到目标处理器
      }  // 循环结束
      return new Space2(metadataClass0, providerBuilder.build());  // 创建并返回扩展工作空间对象
    }  // 方法结束
  }  // 内部类Space2结束
}  // 外部类ReflectiveRelMetadataProvider结束
