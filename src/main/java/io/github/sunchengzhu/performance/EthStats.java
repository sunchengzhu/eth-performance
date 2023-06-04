package io.github.sunchengzhu.performance;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetBlockTransactionCountByNumber;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.websocket.WebSocketService;

import java.io.*;
import java.math.BigInteger;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class EthStats {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static String[] headers = {"BlockNumber", "localTimestamp", "Interval", "TxCount", "TPS", "SuccessRate"};

    private static WebSocketService webSocketService;
    private static Web3j web3j;

    private static final AtomicReference<BigInteger> latestBlockHeight = new AtomicReference<>(BigInteger.ZERO);
    private static final AtomicReference<BigInteger> currentHeight = new AtomicReference<>(BigInteger.ZERO);
    private static final AtomicReference<LocalDateTime> old = new AtomicReference<>(LocalDateTime.now());
    private static final AtomicInteger counter = new AtomicInteger();
    private static AtomicBoolean isRunning = new AtomicBoolean(true);


    public static void main(String[] args) {
        // 删除旧的commands.txt文件并创建新文件
        prepareCommandsFile();

        String defaultWebSocketUrl = "wss://eth-mainnet.g.alchemy.com/v2/RCBd9pi7A5J4YpdugIxnyvzIFliZYZH_";
        String webSocketUrl = args.length > 0 ? args[0] : defaultWebSocketUrl;
        webSocketService = new WebSocketService(webSocketUrl, false);
        try {
            webSocketService.connect();
        } catch (ConnectException e) {
            e.printStackTrace();
        }
        web3j = Web3j.build(webSocketService);

        // 在JVM退出时打印当前时间
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(formatter.format(LocalDateTime.now()) + " JVM正在退出...");
        }));

        // 开始监听命令
        listenForCommands();
    }

    private static void prepareCommandsFile() {
        File commandFile = new File("commands.txt");
        try {
            if (commandFile.exists()) {
                if (commandFile.delete()) {
                    System.out.println("旧的commands.txt文件已删除");
                } else {
                    System.out.println("无法删除旧的commands.txt文件");
                }
            }

            if (commandFile.createNewFile()) {
                System.out.println("新的commands.txt文件已创建");
            } else {
                System.out.println("无法创建新的commands.txt文件");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void listenForCommands() {
        File commandFile = new File("commands.txt");
        try (BufferedReader reader = new BufferedReader(new FileReader(commandFile))) {
            String command;
            while (true) {
                if ((command = reader.readLine()) != null) {
                    switch (command) {
                        case "printBlockNumber":
                            printBlockNumber();
                            break;
                        case "tps":
                            tps();
                            break;
                        case "stopTps":
                            stopRunning();
                            break;
                        case "successRate":
                            successRate();
                            System.out.println(formatter.format(LocalDateTime.now()) + " webSocket正在退出...");
                            web3j.shutdown();
                            webSocketService.close();
                            return;
                        case "exit":
                            System.out.println(formatter.format(LocalDateTime.now()) + " webSocket正在退出...");
                            web3j.shutdown();
                            webSocketService.close();
                            return;
                        default:
                            System.out.println("未知命令: " + command);
                            break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void tps() throws IOException {
        Path path = Paths.get("tps.csv");
        BufferedWriter writer = Files.newBufferedWriter(path);
        // 写入自定义表头
        writer.write(String.join(",", headers));
        writer.flush();

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            if (!isRunning.get()) {
                executor.shutdown();
            } else {
                CompletableFuture<EthBlockNumber> futureBlockNumber = web3j.ethBlockNumber().sendAsync();
                futureBlockNumber.thenAccept(blockNumber -> {
                    BigInteger newHeight = blockNumber.getBlockNumber();
//                    LocalDateTime now = LocalDateTime.now();
//                    String timestamp = formatter.format(now);
//                    printColored("区块高度：" + newHeight + ", 时间：" + timestamp, Color.YELLOW);
                    if (newHeight.compareTo(latestBlockHeight.get()) > 0) {
                        latestBlockHeight.set(newHeight);
                        currentHeight.set(newHeight);
                        CompletableFuture<EthGetBlockTransactionCountByNumber> futureTransactionCount =
                                web3j.ethGetBlockTransactionCountByNumber(
                                        DefaultBlockParameter.valueOf(currentHeight.get())
                                ).sendAsync();
                        futureTransactionCount.thenAccept(transactionCount -> {
                            if (transactionCount.hasError()) {
                                System.err.println("Error fetching transaction count: " + transactionCount.getError().getMessage());
                            } else {
                                BigInteger count = transactionCount.getTransactionCount();
                                processNewBlockHeight(writer, count);
                            }
                        }).exceptionally(ex -> {
                            System.err.println("Error fetching transaction count: " + ex.getMessage());
                            return null;
                        });
                    }
                }).exceptionally(ex -> {
                    System.err.println("Error fetching block number: " + ex.getMessage());
                    return null;
                });
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    public static void stopRunning() {
        isRunning.set(false);
    }

    private static void processNewBlockHeight(BufferedWriter writer, BigInteger txCount) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = formatter.format(now);
        long milliseconds = now.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli() - old.get().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        DecimalFormat secondsDf = new DecimalFormat("0.000");
        DecimalFormat tpsDf = new DecimalFormat("0.##");
        double seconds = (double) milliseconds / 1000;
        if (counter.get() == 0) {
            seconds = 0;
        }
        String tps = tpsDf.format(txCount.doubleValue() / seconds);
        if (counter.get() >= 2) {
            String[] row = {String.valueOf(currentHeight.get()), timestamp, secondsDf.format(seconds), String.valueOf(txCount), tps};
            try {
                writer.newLine();
                writer.write(String.join(",", row) + ",");
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            tps = "无效";
        }
        Color.printColored("区块高度：" + currentHeight.get() + ", 出块时间：" + timestamp + ", 出块间隔：" + secondsDf.format(seconds) + ", 交易数量：" + txCount + ", tps：" + tps, Color.GREEN);
        old.set(now);
        counter.getAndIncrement();
    }

    public static String getSuccessRateByNumber(BigInteger blockHeight) {
        DefaultBlockParameter blockParameter = DefaultBlockParameter.valueOf(blockHeight);
        EthBlock.Block block = null;
        try {
            block = web3j.ethGetBlockByNumber(blockParameter, false).send().getBlock();
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<EthBlock.TransactionResult> transactions = block.getTransactions();

        int txSize = transactions.size();
        String successRate;
        if (txSize == 0) {
            successRate = "无交易";
            return successRate;
        }

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (EthBlock.TransactionResult transaction : transactions) {
            futures.add(executorService.submit(() -> {
                String txHash = transaction.get().toString();
                try {
                    EthGetTransactionReceipt transactionReceipt = web3j.ethGetTransactionReceipt(txHash).send();
                    return transactionReceipt.getTransactionReceipt().get().getStatus().equals("0x1");
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }));
        }

        executorService.shutdown();
        try {
            executorService.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        int successCount = 0;
        for (Future<Boolean> future : futures) {
            try {
                if (future.get()) {
                    successCount++;
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        DecimalFormat df = new DecimalFormat("0.##");
        successRate = df.format((float) 100 * successCount / txSize) + "%";

        return successRate;
    }


    public static void successRate() throws IOException {
        String inputFilePath = "tps.csv";
        String outputFilePath = "performance.csv";
        try (
                BufferedReader reader = Files.newBufferedReader(Paths.get(inputFilePath));
                BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFilePath));
        ) {
            writer.write(String.join(",", headers));
            writer.flush();

            // 跳过第一行
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                BigInteger blockHeight = new BigInteger(fields[0]);
                String blockTime = fields[1];
                String seconds = fields[2];
                String txSize = fields[3];
                String tps = fields[4];

                String successRate = getSuccessRateByNumber(blockHeight);

                // 将结果写入到当前行的末尾
                writer.newLine();
                writer.write(line + successRate);
                writer.flush();

                Color.printColored("区块高度: " + blockHeight + ", 出块时间: " + blockTime + ", 出块间隔: " + seconds +
                        ", 交易数: " + txSize + ", tps: " + tps + ", 交易成功率: " + successRate, Color.CYAN);
            }
        }
    }

    private static void printBlockNumber() {
        try {
            BigInteger blockNumber = web3j.ethBlockNumber().send().getBlockNumber();
            System.out.println(blockNumber);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
