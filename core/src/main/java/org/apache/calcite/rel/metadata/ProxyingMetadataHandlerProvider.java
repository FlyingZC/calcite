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
package org.apache.calcite.rel.metadata;  // 声明包名，该类位于org.apache.calcite.rel.metadata包下，属于Calcite关系表达式元数据管理模块

import org.apache.calcite.rel.RelNode;  // 导入RelNode类，表示关系表达式节点，是Calcite中所有关系操作符的基类

import java.lang.reflect.Field;  // 导入Field类，用于通过反射获取类的字段信息
import java.lang.reflect.InvocationHandler;  // 导入InvocationHandler接口，用于动态代理的方法调用处理
import java.lang.reflect.InvocationTargetException;  // 导入InvocationTargetException类，用于处理反射调用方法时抛出的异常
import java.lang.reflect.Method;  // 导入Method类，用于通过反射获取类的方法信息
import java.lang.reflect.ParameterizedType;  // 导入ParameterizedType接口，用于获取泛型类型的实际类型参数
import java.lang.reflect.Proxy;  // 导入Proxy类，用于创建动态代理实例
import java.lang.reflect.Type;  // 导入Type接口，表示Java中的所有类型（包括原始类型、参数化类型、数组类型等）
import java.util.Arrays;  // 导入Arrays类，提供操作数组的各种方法
import java.util.List;  // 导入List接口，表示有序集合
import java.util.Locale;  // 导入Locale类，用于本地化相关的操作
import java.util.Map;  // 导入Map接口，表示键值对映射
import java.util.stream.Collectors;  // 导入Collectors类，提供Stream的收集器方法

import static java.util.Objects.requireNonNull;  // 静态导入requireNonNull方法，用于检查对象引用是否为null

/**
 * 一个基于RelMetadataProvider构建的MetadataHandlerProvider（元数据处理器提供者）
 *
 * <p>使用代理模式来调用底层的元数据提供者，实现了动态代理机制，能够灵活地处理各种元数据查询请求
 * 这个类是Calcite元数据系统的核心组件之一，负责将元数据查询请求委托给合适的处理器
 * 
 * 类的作用说明：
 * 1. 代理模式实现：通过Java动态代理技术，创建MetadataHandler的代理实例，拦截所有方法调用
 * 2. 元数据分发：根据关系表达式类型和元数据类型，将查询请求分发给正确的元数据处理器
 * 3. 反射机制：利用反射动态解析元数据接口和方法，实现灵活的元数据查询
 * 4. 类型安全：通过泛型确保类型安全的元数据查询和返回
 * 5. 异常处理：统一处理元数据查询过程中的各种异常，包括循环依赖检测
 */
public class ProxyingMetadataHandlerProvider implements MetadataHandlerProvider {  // 定义类名，实现MetadataHandlerProvider接口，表示这是一个代理的元数据处理器提供者

  private final RelMetadataProvider provider;  // 成员变量：底层的关系元数据提供者，负责实际提供元数据查询功能，final修饰表示一旦初始化不可改变

  /**
   * 创建一个代理的处理器提供者
   *
   * @param provider 该提供者将基于此provider进行操作，传入的RelMetadataProvider实例将被用于实际的元数据查询
   */
  public ProxyingMetadataHandlerProvider(RelMetadataProvider provider) {  // 构造方法：接收一个RelMetadataProvider参数并初始化成员变量
    this.provider = provider;  // 将传入的provider赋值给成员变量，保存对底层元数据提供者的引用
  }

  @SuppressWarnings("deprecation")  // 注解：抑制编译器关于使用已弃用方法的警告，因为这里需要使用RelMetadataProvider的反射功能
  @Override public <MH extends MetadataHandler<?>> MH handler(Class<MH> handlerClass) {  // 泛型方法：根据传入的处理器类创建一个代理实例，MH是泛型类型参数，必须继承自MetadataHandler<?>，返回类型为MH

    Type[] types = handlerClass.getGenericInterfaces();  // 获取handlerClass实现的所有泛型接口类型数组，用于后续解析元数据类型
    if (types.length != 1 || !(types[0] instanceof ParameterizedType)) {  // 检查接口数量是否为1且第一个接口是否为参数化类型，确保handlerClass只实现了一个泛型接口
      throw new UnsupportedOperationException("Unexpected failure. " + handlerClass);  // 如果不符合预期，抛出不支持的操作异常
    }

    ParameterizedType pType = (ParameterizedType) types[0];  // 将第一个接口类型强转为ParameterizedType，以便获取泛型参数信息
    if (pType.getRawType() != MetadataHandler.class) {  // 检查原始类型是否为MetadataHandler.class，确保接口类型正确
      throw new UnsupportedOperationException("Unexpected failure. " + handlerClass);  // 如果不是MetadataHandler接口，抛出异常
    }
    Class<?> metadataType = (Class<?>) pType.getActualTypeArguments()[0];  // 获取泛型接口的第一个类型参数，即元数据类型（如RowCount、CumulativeCost等）
    final Field field;  // 声明一个Field变量，用于存储元数据类型中的DEF字段
    final MetadataDef<?> def;  // 声明一个MetadataDef变量，用于存储元数据定义信息
    try {  // 开始try块，用于捕获反射操作可能抛出的异常
      field = metadataType.getField("DEF");  // 通过反射获取元数据类型中名为"DEF"的静态字段，该字段包含元数据定义
      def =  // 将获取的DEF字段的值转换为MetadataDef类型
          requireNonNull((MetadataDef<?>) field.get(null),  // 获取静态字段的值（null表示静态字段），并检查是否为null
              () -> "Unexpected failure. " + handlerClass);  // 如果为null，抛出异常并附带错误信息
    } catch (NoSuchFieldException | IllegalAccessException e) {  // 捕获字段不存在或访问权限异常
      throw new RuntimeException(e);  // 将检查异常包装为运行时异常抛出
    }

    List<Method> methods = def.methods;  // 从元数据定义中获取所有元数据方法列表
    Map<String, Method> methodMap = methods.stream()  // 将方法列表转换为Stream流
        .collect(Collectors.toMap(Method::getName, f -> f));  // 收集为Map，键为方法名，值为Method对象，便于快速查找
    InvocationHandler handler = (proxy, method, args) -> {  // 创建InvocationHandler匿名内部类，这是动态代理的核心，拦截所有方法调用
      Method metadataMethod =  // 从methodMap中查找对应的元数据方法
          requireNonNull(methodMap.get(method.getName()),  // 根据方法名查找Method对象
              () -> "Not supported: " + method);  // 如果找不到方法，抛出异常
      RelNode rel = requireNonNull((RelNode) args[0], "rel must be non null");  // 获取第一个参数（关系表达式节点），并检查是否为null
      RelMetadataQuery mq =  // 获取第二个参数（元数据查询上下文）
          requireNonNull((RelMetadataQuery) args[1], "mq must be non null");  // 检查是否为null

      // 这里使用已弃用的RelMetadataProvider方法，因为非弃用的方法完全绕过了RelMetadataProvider基于反射的功能
      @SuppressWarnings({"unchecked", "rawtypes"})  // 抑制未检查的转换和原始类型警告
      UnboundMetadata metadata =  // 调用底层provider的apply方法，获取未绑定的元数据对象
          provider.apply(rel.getClass(),  // 传入关系表达式的类类型
              (Class<? extends Metadata>) metadataType);  // 传入元数据类型，强制转换为Metadata的子类

      if (metadata == null) {  // 如果返回的元数据对象为null，表示没有找到对应的处理器
        Method handlerMethod =  // 在handlerClass中查找对应的处理器方法
            Arrays.stream(handlerClass.getMethods())  // 获取handlerClass的所有方法并转换为Stream
                .filter(m -> m.getName().equals(metadataMethod.getName()))  // 过滤出与元数据方法同名的方法
                .findFirst()  // 获取第一个匹配的方法
                .orElseThrow(()  // 如果找不到方法，抛出异常
                    -> new IllegalArgumentException("Unable to find method."));  // 抛出非法参数异常
        throw new IllegalArgumentException(  // 抛出异常，提示没有找到对应的处理器
            String.format(Locale.ROOT, "No handler for method [%s] applied to "  // 格式化错误消息
                + "argument of type [%s]; we recommend you create a catch-all "  // 建议创建一个通用的(RelNode)处理器
                + "(RelNode) handler", handlerMethod, rel.getClass()));  // 包含方法名和关系表达式类型信息
      }
      Metadata bound =  // 将未绑定的元数据对象绑定到具体的关系表达式和元数据查询上下文
          requireNonNull(metadata, "expected defined metadata")  // 检查metadata是否为null
              .bind(rel, mq);  // 调用bind方法，传入关系表达式和元数据查询上下文，返回绑定后的Metadata对象

      Object[] abbreviatedArgs = new Object[args.length - 2];  // 创建新的参数数组，长度为原参数数组减2（去掉前两个参数）
      System.arraycopy(args, 2, abbreviatedArgs, 0, abbreviatedArgs.length);  // 将原参数数组从第3个参数开始复制到新数组中
      try {  // 开始try块，用于捕获反射调用可能抛出的异常
        return metadataMethod.invoke(bound, abbreviatedArgs);  // 通过反射调用绑定后的元数据方法，传入绑定对象和简化后的参数数组
      } catch (InvocationTargetException ex) {  // 捕获反射调用目标方法时抛出的异常
        if (ex.getCause() instanceof CyclicMetadataException) {  // 检查异常原因是否为循环元数据异常
          throw (CyclicMetadataException) ex.getCause();  // 如果是循环异常，直接抛出
        }

        throw new RuntimeException(ex.getCause());  // 否则将异常原因包装为运行时异常抛出
      }
    };  // InvocationHandler匿名内部类结束

    return (MH) Proxy.newProxyInstance(  // 创建动态代理实例并返回
        handlerClass.getClassLoader(),  // 使用handlerClass的类加载器
        new Class[]{handlerClass},  // 指定代理类要实现的接口数组
        handler);  // 传入InvocationHandler对象，处理所有方法调用
  }

}  // 类定义结束
