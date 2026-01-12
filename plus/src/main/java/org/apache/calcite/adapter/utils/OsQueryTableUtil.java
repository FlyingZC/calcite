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
package org.apache.calcite.adapter.utils; // 包声明：属于Calcite框架的adapter.utils工具包，提供操作系统查询相关的工具类

import java.util.ArrayList; // 导入ArrayList类，用于创建动态数组列表
import java.util.List; // 导入List接口，用于定义列表类型
import java.util.Locale; // 导入Locale类，用于本地化格式化
import java.util.Properties; // 导入Properties类，用于读取系统属性

import cn.hutool.system.oshi.CpuTicks; // 导入Hutool工具包的CpuTicks类，用于获取CPU时间片信息
import cn.hutool.system.oshi.OshiUtil; // 导入Hutool工具包的OshiUtil类，用于获取操作系统硬件信息
import oshi.hardware.CentralProcessor; // 导入OSHI库的CentralProcessor接口，用于获取CPU处理器信息
import oshi.hardware.GlobalMemory; // 导入OSHI库的GlobalMemory接口，用于获取全局内存信息
import oshi.hardware.NetworkIF; // 导入OSHI库的NetworkIF接口，用于获取网络接口信息
import oshi.hardware.VirtualMemory; // 导入OSHI库的VirtualMemory接口，用于获取虚拟内存信息
import oshi.software.os.FileSystem; // 导入OSHI库的FileSystem接口，用于获取文件系统信息
import oshi.software.os.OSFileStore; // 导入OSHI库的OSFileStore接口，用于获取文件存储信息
import oshi.software.os.OperatingSystem; // 导入OSHI库的OperatingSystem接口，用于获取操作系统信息

/**
 * Used to put OS query related func. // 类注释：用于封装操作系统查询相关的功能函数，提供获取系统信息、CPU信息、内存信息、网络信息等能力
 * 这个工具类通过OSHI库（Operating System and Hardware Information）来获取底层操作系统的硬件和软件信息
 * 主要为Calcite适配器提供系统监控和查询功能，支持在SQL查询中获取操作系统层面的数据
 */
public class OsQueryTableUtil { // 定义OsQueryTableUtil工具类，提供静态方法来获取各种系统信息
  private static final String IP_ADDRESS_SEPARATOR = "; "; // 定义私有静态常量：IP地址分隔符，用于将多个IP地址连接成字符串时使用分号和空格分隔

  private OsQueryTableUtil() { // 私有构造方法：防止实例化，确保该类只能通过静态方法调用
  } // 构造方法体为空，因为这是一个纯工具类，不需要实例化

  public static List<Object[]> getSystemInfo() { // 公共静态方法：获取系统基本信息，返回包含系统信息的对象数组列表
    final List<Object[]> list = new ArrayList<>(); // 创建ArrayList实例，用于存储系统信息数据
    Object[] objects = { // 创建对象数组，用于存储单个系统信息记录的所有字段
        OshiUtil.getOs().getNetworkParams().getHostName(), // 获取主机名：通过OshiUtil获取操作系统网络参数中的主机名称
        OshiUtil.getSystem().getSerialNumber(), // 获取系统序列号：通过OshiUtil获取计算机系统的唯一序列号
        OshiUtil.getProcessor().getProcessorIdentifier().getMicroarchitecture(), // 获取CPU微架构：获取处理器标识符中的微架构类型（如x86、ARM等）
        OshiUtil.getProcessor().getProcessorIdentifier().getVendor(), // 获取CPU厂商：获取处理器标识符中的制造商名称（如Intel、AMD等）
        OshiUtil.getProcessor().getProcessorIdentifier().getModel(), // 获取CPU型号：获取处理器标识符中的具体型号名称
        OshiUtil.getProcessor().getPhysicalProcessorCount(), // 获取物理CPU核心数：获取处理器实际的物理核心数量
        OshiUtil.getProcessor().getLogicalProcessorCount(), // 获取逻辑CPU核心数：获取处理器支持逻辑核心数量（包含超线程）
        OshiUtil.getProcessor().getPhysicalPackageCount(), // 获取CPU物理封装数：获取物理CPU芯片的数量（即主板上的CPU插槽数）
        OshiUtil.getMemory().getTotal(), // 获取总内存大小：获取系统安装的物理内存总量（字节为单位）
        OshiUtil.getHardware().getComputerSystem().getFirmware().getManufacturer(), // 获取固件厂商：获取计算机系统固件（BIOS/UEFI）的制造商名称
        OshiUtil.getHardware().getComputerSystem().getModel(), // 获取计算机型号：获取计算机系统的型号名称
        OshiUtil.getHardware().getComputerSystem().getFirmware().getVersion(), // 获取固件版本：获取计算机系统固件的版本号
        OshiUtil.getHardware().getComputerSystem().getSerialNumber(), // 获取系统序列号：获取计算机系统的序列号
        OshiUtil.getHardware().getComputerSystem().getBaseboard().getManufacturer(), // 获取主板厂商：获取主板（Baseboard）的制造商名称
        OshiUtil.getHardware().getComputerSystem().getBaseboard().getModel(), // 获取主板型号：获取主板的具体型号名称
        OshiUtil.getHardware().getComputerSystem().getBaseboard().getVersion(), // 获取主板版本：获取主板的版本号
        OshiUtil.getHardware().getComputerSystem().getBaseboard().getSerialNumber(), // 获取主板序列号：获取主板的序列号
        OshiUtil.getOs().getNetworkParams().getDomainName() // 获取网络域名：获取操作系统网络参数中的域名
    }; // 结束对象数组初始化，包含19个系统信息字段
    list.add(objects); // 将包含系统信息的对象数组添加到列表中
    return list; // 返回包含系统信息的列表
  } // 方法结束，返回List<Object[]>类型的结果

  public static List<Object[]> getJavaInfo() { // 公共静态方法：获取Java运行环境信息，返回包含Java相关信息的对象数组列表
    final List<Object[]> list = new ArrayList<>(); // 创建ArrayList实例，用于存储Java信息数据
    final Properties props = System.getProperties(); // 获取系统属性对象，包含Java运行时的各种配置信息
    final Object[] objects = { // 创建对象数组，用于存储单个Java信息记录的所有字段
        props.getProperty("java.version"), // 获取Java版本：从系统属性中获取Java运行时的版本号（如1.8.0_301）
        props.getProperty("java.vendor"), // 获取Java厂商：从系统属性中获取Java运行时的厂商名称（如Oracle Corporation）
        props.getProperty("java.vendor.url"), // 获取Java厂商URL：从系统属性中获取Java厂商的官方网站地址
        props.getProperty("java.home"), // 获取Java安装目录：从系统属性中获取Java运行时的安装路径
        props.getProperty("java.vm.specification.version"), // 获取Java虚拟机规范版本：获取JVM规范的版本号
        props.getProperty("java.vm.specification.vendor"), // 获取Java虚拟机规范厂商：获取JVM规范的供应商名称
        props.getProperty("java.vm.specification.name"), // 获取Java虚拟机规范名称：获取JVM规范的名称（如Java Virtual Machine Specification）
        props.getProperty("java.vm.version"), // 获取Java虚拟机版本：获取实际运行的JVM版本号
        props.getProperty("java.vm.vendor"), // 获取Java虚拟机厂商：获取实际运行的JVM厂商名称
        props.getProperty("java.vm.name"), // 获取Java虚拟机名称：获取实际运行的JVM名称（如Java HotSpot(TM) 64-Bit Server VM）
        props.getProperty("java.specification.version"), // 获取Java运行时规范版本：获取Java运行时环境规范的版本号
        props.getProperty("java.specification.vender"), // 获取Java运行时规范厂商：获取Java运行时环境规范的供应商名称（注意：属性名拼写错误应为vendor）
        props.getProperty("java.specification.name"), // 获取Java运行时规范名称：获取Java运行时环境规范的名称
        props.getProperty("java.class.version"), // 获取Java类文件版本：获取Java类文件的版本号（如52.0代表Java 8）
        props.getProperty("java.class.path"), // 获取Java类路径：获取Java运行时的类路径（CLASSPATH）
        props.getProperty("java.io.tmpdir"), // 获取临时文件目录：获取Java默认的临时文件存储路径
        props.getProperty("java.ext.dirs"), // 获取扩展目录：获取Java扩展类的加载路径
        props.getProperty("java.library.path") // 获取本地库路径：获取Java本地库（JNI库）的搜索路径
    }; // 结束对象数组初始化，包含18个Java信息字段
    list.add(objects); // 将包含Java信息的对象数组添加到列表中
    return list; // 返回包含Java信息的列表
  } // 方法结束，返回List<Object[]>类型的结果

  public static List<Object[]> getOsVersionInfo() { // 公共静态方法：获取操作系统版本信息，返回包含操作系统版本相关信息的对象数组列表
    final List<Object[]> list = new ArrayList<>(); // 创建ArrayList实例，用于存储操作系统版本信息数据
    OperatingSystem os = OshiUtil.getOs(); // 获取操作系统实例：通过OshiUtil获取当前操作系统的信息对象
    final Object[] objects = { // 创建对象数组，用于存储单个操作系统版本信息记录的所有字段
        os.getVersionInfo().toString(), // 获取操作系统完整版本信息：将版本信息对象转换为字符串（包含版本号、代号等）
        os.getVersionInfo().getCodeName(), // 获取操作系统代号：获取操作系统的开发代号（如Windows 10的代号）
        os.getVersionInfo().getBuildNumber(), // 获取操作系统构建号：获取操作系统的内部构建版本号
        os.getVersionInfo().getCodeName(), // 获取操作系统代号：重复获取代号（可能是代码冗余）
        os.getSystemBootTime() // 获取系统启动时间：获取操作系统上次启动的时间戳（秒为单位）
    }; // 结束对象数组初始化，包含5个操作系统版本信息字段
    list.add(objects); // 将包含操作系统版本信息的对象数组添加到列表中
    return list; // 返回包含操作系统版本信息的列表
  } // 方法结束，返回List<Object[]>类型的结果

  public static List<Object[]> getMemoryInfo() { // 公共静态方法：获取内存信息，返回包含物理内存和虚拟内存信息的对象数组列表
    GlobalMemory memory = OshiUtil.getMemory(); // 获取全局内存实例：通过OshiUtil获取系统的物理内存信息对象
    VirtualMemory virtualMemory = memory.getVirtualMemory(); // 获取虚拟内存实例：从全局内存对象中获取虚拟内存（交换空间）信息

    final List<Object[]> list = new ArrayList<>(); // 创建ArrayList实例，用于存储内存信息数据
    final Object[] objects = { // 创建对象数组，用于存储单个内存信息记录的所有字段
        getNetFileSizeDescription(memory.getTotal()), // 获取总内存大小：调用格式化方法将总内存字节数转换为可读格式（如8.00GB）
        getNetFileSizeDescription(memory.getAvailable()), // 获取可用内存大小：调用格式化方法将可用内存字节数转换为可读格式
        getNetFileSizeDescription(memory.getTotal() - memory.getAvailable()), // 获取已用内存大小：计算总内存减去可用内存，并格式化为可读格式
        getNetFileSizeDescription(virtualMemory.getSwapTotal()), // 获取交换空间总大小：调用格式化方法将交换空间总量转换为可读格式
        getNetFileSizeDescription(virtualMemory.getSwapUsed()), // 获取交换空间已用大小：调用格式化方法将已使用的交换空间转换为可读格式
        getNetFileSizeDescription(virtualMemory.getSwapTotal() - virtualMemory.getSwapUsed()), // 获取交换空间可用大小：计算交换空间总量减去已用量，并格式化为可读格式
        getNetFileSizeDescription(virtualMemory.getSwapPagesIn()), // 获取换入页面数：调用格式化方法将换入内存的页面数转换为可读格式
        getNetFileSizeDescription(virtualMemory.getSwapPagesOut()) // 获取换出页面数：调用格式化方法将换出到磁盘的页面数转换为可读格式
    }; // 结束对象数组初始化，包含8个内存信息字段
    list.add(objects); // 将包含内存信息的对象数组添加到列表中
    return list; // 返回包含内存信息的列表
  } // 方法结束，返回List<Object[]>类型的结果

  public static List<Object[]> getCpuInfo() { // 公共静态方法：获取CPU处理器信息，返回包含CPU详细信息的对象数组列表
    final List<Object[]> list = new ArrayList<>(); // 创建ArrayList实例，用于存储CPU信息数据
    CentralProcessor processor = OshiUtil.getProcessor(); // 获取中央处理器实例：通过OshiUtil获取系统的CPU处理器信息对象
    Object[] objects = { // 创建对象数组，用于存储单个CPU信息记录的所有字段
        processor.getProcessorIdentifier().getProcessorID(), // 获取CPU处理器ID：获取处理器的唯一标识符
        processor.getProcessorIdentifier().getModel(), // 获取CPU型号：获取处理器的具体型号名称
        processor.getProcessorIdentifier().getMicroarchitecture(), // 获取CPU微架构：获取处理器的微架构类型
        processor.getPhysicalProcessorCount(), // 获取物理CPU核心数：获取处理器的物理核心数量
        processor.getLogicalProcessorCount(), // 获取逻辑CPU核心数：获取处理器的逻辑核心数量（包含超线程）
        processor.getProcessorIdentifier().isCpu64bit() ? 64 : 32, // 获取CPU位数：判断CPU是否为64位架构，返回64或32
        processor.getMaxFreq(), // 获取CPU最大频率：获取处理器的最大时钟频率（Hz为单位）
        processor.getPhysicalPackageCount(), // 获取CPU物理封装数：获取物理CPU芯片的数量
        processor.getSystemCpuLoad(1000L) // 获取CPU系统负载：获取过去1000毫秒（1秒）的CPU系统负载百分比（0.0到1.0之间）
    }; // 结束对象数组初始化，包含9个CPU信息字段
    list.add(objects); // 将包含CPU信息的对象数组添加到列表中
    return list; // 返回包含CPU信息的列表
  } // 方法结束，返回List<Object[]>类型的结果

  public static List<Object[]> getCpuTimeInfo() { // 公共静态方法：获取CPU时间片信息，返回包含CPU各状态时间统计的对象数组列表
    final List<Object[]> list = new ArrayList<>(); // 创建ArrayList实例，用于存储CPU时间信息数据
    CpuTicks cpuTicks = OshiUtil.getCpuInfo().getTicks(); // 获取CPU时间片对象：通过OshiUtil获取CPU的时间片统计信息
    Object[] objects = { // 创建对象数组，用于存储单个CPU时间信息记录的所有字段
        cpuTicks.getIdle(), // 获取CPU空闲时间：获取CPU处于空闲状态的时间片数
        cpuTicks.getNice(), // 获取CPU Nice时间：获取CPU在用户模式下低优先级进程运行的时间片数（Linux特有）
        cpuTicks.getIrq(), // 获取CPU中断时间：获取CPU处理硬件中断请求的时间片数
        cpuTicks.getSoftIrq(), // 获取CPU软中断时间：获取CPU处理软件中断的时间片数
        cpuTicks.getSteal(), // 获取CPU窃取时间：获取运行其他操作系统虚拟机的时间片数（虚拟化环境）
        cpuTicks.getcSys(), // 获取CPU内核时间：获取CPU在内核模式下运行的时间片数
        cpuTicks.getUser(), // 获取CPU用户时间：获取CPU在用户模式下运行的时间片数
        cpuTicks.getIoWait() // 获取CPU IO等待时间：获取CPU等待IO操作完成的时间片数
    }; // 结束对象数组初始化，包含8个CPU时间信息字段
    list.add(objects); // 将包含CPU时间信息的对象数组添加到列表中
    return list; // 返回包含CPU时间信息的列表
  } // 方法结束，返回List<Object[]>类型的结果

  public static List<Object[]> getInterfaceAddressesInfo() { // 公共静态方法：获取网络接口地址信息，返回包含所有网络接口IP地址信息的对象数组列表
    final List<Object[]> list = new ArrayList<>(); // 创建ArrayList实例，用于存储网络接口地址信息数据
    List<NetworkIF> networkIFList = OshiUtil.getNetworkIFs(); // 获取网络接口列表：通过OshiUtil获取系统所有网络接口的列表
    for (NetworkIF intf : networkIFList) { // 遍历每个网络接口：使用for-each循环处理每一个网络接口
      Object[] objects = { // 创建对象数组，用于存储单个网络接口地址信息的所有字段
          intf.getName(), // 获取接口名称：获取网络接口的名称（如eth0、wlan0等）
          getIPAddressesString(intf.getIPv4addr()), // 获取IPv4地址：调用辅助方法将IPv4地址数组格式化为字符串
          getIPAddressesString(intf.getIPv6addr()), // 获取IPv6地址：调用辅助方法将IPv6地址数组格式化为字符串
          intf.getMacaddr(), // 获取MAC地址：获取网络接口的物理地址（硬件地址）
          intf.getIfOperStatus().toString().contains("UNKNOWN") ? "" : intf.getIfOperStatus() // 获取接口操作状态：获取接口的运行状态，如果是UNKNOWN则返回空字符串
      }; // 结束对象数组初始化，包含5个网络接口地址信息字段
      list.add(objects); // 将包含网络接口地址信息的对象数组添加到列表中
    } // 结束for循环，处理完所有网络接口
    return list; // 返回包含网络接口地址信息的列表
  } // 方法结束，返回List<Object[]>类型的结果

  public static List<Object[]> getInterfaceDetailsInfo() { // 公共静态方法：获取网络接口详细信息，返回包含所有网络接口详细统计信息的对象数组列表
    final List<Object[]> list = new ArrayList<>(); // 创建ArrayList实例，用于存储网络接口详细信息数据
    List<NetworkIF> networkIFList = OshiUtil.getNetworkIFs(); // 获取网络接口列表：通过OshiUtil获取系统所有网络接口的列表
    for (NetworkIF intf : networkIFList) { // 遍历每个网络接口：使用for-each循环处理每一个网络接口
      Object[] objects = { // 创建对象数组，用于存储单个网络接口详细信息的所有字段
          intf.getName(), // 获取接口名称：获取网络接口的名称
          intf.getMacaddr(), // 获取MAC地址：获取网络接口的物理地址
          intf.queryNetworkInterface().isVirtual(), // 获取是否为虚拟接口：查询网络接口并判断是否为虚拟接口
          intf.getMTU(), // 获取MTU值：获取网络接口的最大传输单元（Maximum Transmission Unit）大小
          intf.getSpeed(), // 获取接口速度：获取网络接口的传输速度（位/秒）
          intf.getPacketsRecv(), // 获取接收包数：获取网络接口接收的数据包总数
          intf.getPacketsSent(), // 获取发送包数：获取网络接口发送的数据包总数
          intf.getBytesRecv(), // 获取接收字节数：获取网络接口接收的字节总数
          intf.getBytesSent(), // 获取发送字节数：获取网络接口发送的字节总数
          intf.getInErrors(), // 获取接收错误数：获取网络接口接收时的错误包数
          intf.getOutErrors(), // 获取发送错误数：获取网络接口发送时的错误包数
          intf.getInDrops(), // 获取接收丢包数：获取网络接口接收时丢弃的包数
          intf.getCollisions() // 获取冲突数：获取网络接口发送时的冲突次数（以太网特有）
      }; // 结束对象数组初始化，包含13个网络接口详细信息字段
      list.add(objects); // 将包含网络接口详细信息的对象数组添加到列表中
    } // 结束for循环，处理完所有网络接口
    return list; // 返回包含网络接口详细信息的列表
  } // 方法结束，返回List<Object[]>类型的结果

  public static List<Object[]> getMountsInfo() { // 公共静态方法：获取挂载点信息，返回包含所有文件系统挂载点信息的对象数组列表
    final List<Object[]> list = new ArrayList<>(); // 创建ArrayList实例，用于存储挂载点信息数据
    FileSystem fileSystem = OshiUtil.getOs().getFileSystem(); // 获取文件系统实例：通过OshiUtil获取操作系统的文件系统对象
    List<OSFileStore> fileStores = fileSystem.getFileStores(); // 获取文件存储列表：从文件系统中获取所有文件存储（挂载点）的列表
    for (OSFileStore fileStore : fileStores) { // 遍历每个文件存储：使用for-each循环处理每一个文件存储（挂载点）
      Object[] objects = { // 创建对象数组，用于存储单个挂载点信息的所有字段
          fileStore.getName(), // 获取存储名称：获取文件存储的名称（如C:、/等）
          fileStore.getName(), // 获取存储名称：重复获取名称（可能是代码冗余）
          getNetFileSizeDescription(fileStore.getTotalSpace()), // 获取总空间大小：调用格式化方法将总空间字节数转换为可读格式
          getNetFileSizeDescription(fileStore.getUsableSpace()), // 获取可用空间大小：调用格式化方法将可用空间字节数转换为可读格式
          getNetFileSizeDescription(fileStore.getFreeSpace()), // 获取剩余空间大小：调用格式化方法将剩余空间字节数转换为可读格式
          getNetFileSizeDescription(fileStore.getTotalInodes()), // 获取总inode数：调用格式化方法将总inode数转换为可读格式（Unix/Linux特有）
          getNetFileSizeDescription(fileStore.getFreeInodes()), // 获取可用inode数：调用格式化方法将可用inode数转换为可读格式（Unix/Linux特有）
          fileStore.getMount() // 获取挂载点路径：获取文件存储的挂载路径（如/、/home、C:\等）
      }; // 结束对象数组初始化，包含8个挂载点信息字段
      list.add(objects); // 将包含挂载点信息的对象数组添加到列表中
    } // 结束for循环，处理完所有文件存储
    return list; // 返回包含挂载点信息的列表
  } // 方法结束，返回List<Object[]>类型的结果

  public static String getIPAddressesString(String[] ipAddressArr) { // 公共静态方法：将IP地址数组格式化为字符串，使用分隔符连接多个IP地址
    StringBuilder sb = new StringBuilder(); // 创建StringBuilder实例，用于高效拼接字符串
    boolean first = true; // 定义布尔标志：标记是否为第一个IP地址，用于控制分隔符的添加

    for (String ipAddress : ipAddressArr) { // 遍历IP地址数组：使用for-each循环处理每一个IP地址
      if (first) { // 判断是否为第一个IP地址
        first = false; // 如果是第一个，将标志设置为false，表示后续不再是第一个
      } else { // 如果不是第一个IP地址
        sb.append(IP_ADDRESS_SEPARATOR); // 在StringBuilder中追加分隔符（"; "）
      } // 结束if-else语句
      sb.append(ipAddress); // 在StringBuilder中追加当前IP地址
    } // 结束for循环，处理完所有IP地址

    return sb.toString(); // 返回拼接完成的IP地址字符串
  } // 方法结束，返回String类型的结果

  public static String getNetFileSizeDescription(long size) { // 公共静态方法：将文件大小字节数格式化为人类可读的字符串（如8.00GB）
    String[] units = {"B", "KB", "MB", "GB"}; // 定义单位数组：包含字节、千字节、兆字节、吉字节四个单位
    int index = 0; // 定义索引变量：用于追踪当前使用的单位索引，初始为0表示字节
    double fileSize = size; // 定义文件大小变量：将传入的long类型转换为double类型，便于进行除法运算
    while (fileSize >= 1024 && index < units.length - 1) { // 循环条件：当文件大小大于等于1024且未达到最大单位时继续循环
      fileSize /= 1024; // 将文件大小除以1024，转换为更大的单位
      index++; // 索引加1，切换到下一个更大的单位
    } // 结束while循环，此时fileSize已转换为最合适的单位大小
    return String.format(Locale.ROOT, "%.2f%s", fileSize, units[index]); // 返回格式化字符串：使用ROOT本地化格式，保留两位小数，并追加单位
  } // 方法结束，返回String类型的结果，如"8.00GB"
} // 类结束，OsQueryTableUtil工具类定义完成