package com.nervos.layer2;

import org.testng.annotations.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetBlockTransactionCountByNumber;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.websocket.WebSocketService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class WsTps {
    public static final String RESET = "\u001B[0m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String CYAN = "\u001B[36m";

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final AtomicReference<BigInteger> latestBlockHeight = new AtomicReference<>(BigInteger.ZERO);
    private static final AtomicReference<BigInteger> currentHeight = new AtomicReference<>(BigInteger.ZERO);
    private static final AtomicReference<LocalDateTime> old = new AtomicReference<>(LocalDateTime.now());
    private static final AtomicInteger counter = new AtomicInteger();
    private static String[] headers = {"BlockNumber", "localTimestamp", "Interval", "TxCount", "TPS", "SuccessRate"};
    private static String testrUrl = "http://13.237.199.246:8010";

    public static void tps(String url, int durationInSeconds) throws IOException {
        Path path = Paths.get("data.csv");
        BufferedWriter writer = Files.newBufferedWriter(path);
        // 写入自定义表头
        writer.write(String.join(",", headers));
        writer.flush();

        WebSocketService webSocketService = new WebSocketService(url, false);
        webSocketService.connect();
        Web3j web3j = Web3j.build(webSocketService);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            CompletableFuture<EthBlockNumber> futureBlockNumber = web3j.ethBlockNumber().sendAsync();
            futureBlockNumber.thenAccept(blockNumber -> {
                BigInteger newHeight = blockNumber.getBlockNumber();
//                LocalDateTime now = LocalDateTime.now();
//                String timestamp = formatter.format(now);
//                printColored("区块高度：" + newHeight + ", 时间：" + timestamp, com.nervos.layer2.WsTps.YELLOW);
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
                            // Pass `count` to `processNewBlockHeight`
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
        }, 0, 100, TimeUnit.MILLISECONDS);


        try {
            executor.awaitTermination(durationInSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            executor.shutdownNow();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("长连接断开开始时间: " + formatter.format(LocalDateTime.now()));
            web3j.shutdown();
            webSocketService.close();
        }
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
        printColored("区块高度：" + currentHeight.get() + ", 出块时间：" + timestamp + ", 出块间隔：" + secondsDf.format(seconds) + ", 交易数量：" + txCount + ", tps：" + tps, WsTps.GREEN);
        old.set(now);
        counter.getAndIncrement();
    }

    private static void printColored(String message, String color) {
        System.out.println(color + message + WsTps.RESET);
    }

    @Test
    public void tpsTest() throws IOException {
        tps(testrUrl, 20);
    }

    public static String getSuccessRateByNumber(Web3j web3j, BigInteger blockHeight) throws IOException, InterruptedException, ExecutionException {
        DefaultBlockParameter blockParameter = DefaultBlockParameter.valueOf(blockHeight);
        EthBlock.Block block = web3j.ethGetBlockByNumber(blockParameter, false).send().getBlock();
        List<EthBlock.TransactionResult> transactions = block.getTransactions();

        int txSize = transactions.size();
        String successRate;
        if (txSize == 0) {
            successRate = "无交易";
            return successRate;
        }

        ExecutorService executorService = Executors.newFixedThreadPool(50);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (EthBlock.TransactionResult transaction : transactions) {
            futures.add(executorService.submit(() -> {
                String txHash = transaction.get().toString();
                EthGetTransactionReceipt transactionReceipt = web3j.ethGetTransactionReceipt(txHash).send();
                return transactionReceipt.getTransactionReceipt().get().getStatus().equals("0x1");
            }));
        }

        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.MINUTES);

        int successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successCount++;
            }
        }

        DecimalFormat df = new DecimalFormat("0.##");
        successRate = df.format((float) 100 * successCount / txSize) + "%";

        return successRate;
    }


    @Test
    public void getSuccessRateByNumberTest() throws IOException, ExecutionException, InterruptedException {
        String url = "wss://v1.testnet.godwoken.io/ws";
        WebSocketService webSocketService = new WebSocketService(url, false);
        webSocketService.connect();
        Web3j web3j = Web3j.build(webSocketService);
        // 测试数据：2733188、1368573、2886546、2860056
        BigInteger blockNumber = new BigInteger("2733188");
        String successRate = getSuccessRateByNumber(web3j, blockNumber);
        System.out.println(successRate);
        web3j.shutdown();
        webSocketService.close();
    }

    public static void successRate(String url) throws IOException, InterruptedException, ExecutionException {
        WebSocketService webSocketService = new WebSocketService(url, false);
        webSocketService.connect();
        Web3j web3j = Web3j.build(webSocketService);
        String inputFilePath = "data.csv";
        String outputFilePath = "tps.csv";
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

                String successRate = getSuccessRateByNumber(web3j, blockHeight);

                // 将结果写入到当前行的末尾
                writer.newLine();
                writer.write(line + successRate);
                writer.flush();

                printColored("区块高度: " + blockHeight + ", 出块时间: " + blockTime + ", 出块间隔: " + seconds +
                        ", 交易数: " + txSize + ", tps: " + tps + ", 交易成功率: " + successRate, WsTps.CYAN);
            }
        }
        web3j.shutdown();
        webSocketService.close();
    }

    @Test
    public void successRateTest() throws IOException, ExecutionException, InterruptedException {
        successRate(testrUrl);
    }

    public static void websocketDemo() {
        WebSocketService webSocketService = new WebSocketService(testrUrl, false);
        try {
            webSocketService.connect();
        } catch (ConnectException e) {
            e.printStackTrace();
        }
        Web3j web3j = Web3j.build(webSocketService);
        try {
            BigInteger blockNumber = web3j.ethBlockNumber().send().getBlockNumber();
            System.out.println(blockNumber);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("长连接断开开始时间: " + formatter.format(LocalDateTime.now()));
        web3j.shutdown();
        webSocketService.close();
    }

    public static void main(String[] args) {
        if (args.length >= 2) {
            String arg1 = args[0];
            String arg2 = args[1];
            String arg3 = "20";
            if (args.length >= 3) {
                arg3 = args[2];
            }
            String url;
            switch (arg1) {
                case "axon":
                    url = "http://13.237.199.246:8010";
                    break;
                case "testnet":
                    url = "wss://v1.testnet.godwoken.io/ws";
                    break;
                case "alphanet":
                    url = "wss://gw-alphanet-v1.godwoken.cf/ws";
                    break;
                default:
                    url = arg1;
                    System.out.println("Use env: " + arg1);
                    break;
            }
            switch (arg2) {
                case "tps":
                    try {
                        tps(url, Integer.parseInt(arg3));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "rate":
                    try {
                        successRate(url);
                    } catch (InterruptedException | ExecutionException | IOException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    System.out.println("Unknown function: " + arg1);
                    break;
            }
        } else {
            System.out.println("No command provided");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("jvm退出时间: " + formatter.format(LocalDateTime.now()));
        }));
    }
}