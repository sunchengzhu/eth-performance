# 项目介绍

主要用于以太坊兼容链的性能统计

# 用法

1. 下载jar包或用maven打包

```shell
wget https://github.com/sunchengzhu/eth-performance/releases/download/v1.0.0/ethStats.jar
```

```shell
mvn clean package
```

2. 启动服务

cd到ethStats.jar的目录

```shell
# 传入webSocket url
java -jar ethStats.jar $webSocketUrl
# 不传入参数的话默认连以太坊主网
java -jar ethStats.jar
```

3. 测试ethStats服务是否可用

打开另一个终端窗口，cd到ethStats.jar的目录

```shell
# 执行下面的命令，如果服务端成功打印区块高度了，说明服务可用
echo "printBlockNumber" >> commands.txt
```

4. 统计tps

```shell
echo "tps" >> commands.txt
```

5. 启动性能测试

执行你自己的性能测试任务

6. 性能测试结束后停止统计tps

```shell
echo "stopTps" >> commands.txt
```

7. 统计交易成功率并结束ethStats服务

```shell
echo "successRate" >> commands.txt
```