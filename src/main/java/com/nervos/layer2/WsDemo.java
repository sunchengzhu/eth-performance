package com.nervos.layer2;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.WebSocketService;

import java.io.*;
import java.math.BigInteger;
import java.net.ConnectException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class WsDemo {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static WebSocketService webSocketService;
    private static Web3j web3j;

    public static void main(String[] args) {
        // 删除旧的commands.txt文件并创建新文件
        prepareCommandsFile();

        String webSocketUrl = "wss://eth-mainnet.g.alchemy.com/v2/tyA5q3o8qKL_9GMtm1JniIMBfRG7vfRB";
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
                    System.out.println("旧的commands.txt文件已删除。");
                } else {
                    System.out.println("无法删除旧的commands.txt文件。");
                }
            }

            if (commandFile.createNewFile()) {
                System.out.println("新的commands.txt文件已创建。");
            } else {
                System.out.println("无法创建新的commands.txt文件。");
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
                        // 在这里添加更多命令
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

    private static void printBlockNumber() {
        try {
            BigInteger blockNumber = web3j.ethBlockNumber().send().getBlockNumber();
            System.out.println(blockNumber);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
