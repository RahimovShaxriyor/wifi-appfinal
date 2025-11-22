package org.example;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WifiScanner {
    private final Consumer<String> logger;
    private volatile boolean isScanning = false;
    private SwingWorker<List<WifiNetwork>, String> currentWorker;
    private List<WifiNetwork> lastScanResult = new ArrayList<>();

    public WifiScanner(Consumer<String> logger) {
        this.logger = logger;
    }

    // === Основной метод сканирования ===
    public void scanNetworks(Consumer<List<WifiNetwork>> callback) {
        if (isScanning) {
            log("Сканирование уже запущено...");
            return;
        }

        isScanning = true;
        log("Запуск сканирования Wi-Fi сетей...");

        currentWorker = new SwingWorker<>() {
            private long startTime;

            @Override
            protected List<WifiNetwork> doInBackground() {
                startTime = System.currentTimeMillis();
                List<WifiNetwork> networks = new ArrayList<>();

                try {
                    if (isLinux()) {
                        networks = scanWithNmcli();
                    } else if (isWindows()) {
                        networks = scanWithNetsh();
                    } else if (isMac()) {
                        networks = scanWithAirport();
                    } else {
                        networks = getDemoNetworks();
                        publish("Используются демо-данные");
                    }
                } catch (Exception ex) {
                    publish("Ошибка сканирования: " + ex.getMessage());
                    networks = getDemoNetworks();
                }

                // Сортировка: сначала сильные сигналы
                networks.sort((a, b) -> Integer.compare(b.getSignalStrength(), a.getSignalStrength()));
                return networks;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                chunks.forEach(logger::accept);
            }

            @Override
            protected void done() {
                isScanning = false;
                long duration = System.currentTimeMillis() - startTime;

                try {
                    List<WifiNetwork> result = get();
                    lastScanResult = result;

                    if (result.isEmpty()) {
                        log("Сети не найдены");
                    } else {
                        log("Сканирование завершено за " + duration + " мс");
                        log("Найдено сетей: " + result.size());
                    }
                    callback.accept(result);
                } catch (Exception ex) {
                    log("Ошибка получения результата: " + ex.getMessage());
                    callback.accept(getDemoNetworks());
                }
            }
        };

        currentWorker.execute();
    }

    public void stopScanning() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            isScanning = false;
            log("Сканирование остановлено");
        }
    }

    public boolean isScanning() {
        return isScanning;
    }

    public List<WifiNetwork> getLastScanResult() {
        return Collections.unmodifiableList(lastScanResult);
    }

    // === Фильтрация ===
    public List<WifiNetwork> filterNetworks(String filterType) {
        return switch (filterType) {
            case "allNetworks" -> lastScanResult;
            case "strongSignals" -> lastScanResult.stream()
                    .filter(n -> n.getSignalStrength() >= 60)
                    .collect(Collectors.toList());
            case "openNetworks" -> lastScanResult.stream()
                    .filter(n -> n.getSecurity().equals("Open") || n.getSecurity().contains("None"))
                    .collect(Collectors.toList());
            case "securedNetworks" -> lastScanResult.stream()
                    .filter(n -> !n.getSecurity().equals("Open") && !n.getSecurity().contains("None"))
                    .collect(Collectors.toList());
            default -> lastScanResult;
        };
    }

    // === Детальная информация ===
    public void getDetailedInfo(String ssid, Consumer<WifiNetwork> callback) {
        new Thread(() -> {
            WifiNetwork network = lastScanResult.stream()
                    .filter(n -> n.getSsid().equals(ssid))
                    .findFirst()
                    .orElse(null);

            if (network != null) {
                callback.accept(network);
            } else {
                callback.accept(new WifiNetwork(ssid, 0, "Unknown", "N/A", "N/A", false));
            }
        }).start();
    }

    // === ОС-специфичные сканеры ===
    private List<WifiNetwork> scanWithNmcli() {
        List<WifiNetwork> networks = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("nmcli", "-t", "-f", "SSID,SIGNAL,SECURITY,FREQ,BSSID", "dev", "wifi");
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                Set<String> seen = new HashSet<>();

                while ((line = reader.readLine()) != null && !isCancelled()) {
                    String[] parts = line.split(":", -1);
                    if (parts.length < 1) continue;

                    String ssid = parts[0].trim();
                    if (ssid.isEmpty() || ssid.equals("--") || seen.contains(ssid)) continue;
                    seen.add(ssid);

                    int signal = parseSignal(parts.length > 1 ? parts[1] : "0");
                    String security = parts.length > 2 ? parts[2].trim() : "Open";
                    String freq = parts.length > 3 ? parts[3].trim() : "2.4 GHz";
                    String bssid = parts.length > 4 ? parts[4].trim() : "N/A";

                    boolean is5G = freq.contains("5");
                    networks.add(new WifiNetwork(ssid, signal, security, freq, bssid, is5G));
                    currentWorker.publish("Обнаружена: " + ssid + " [" + signal + "%]");
                }
            }
            process.waitFor(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            currentWorker.publish("nmcli ошибка: " + e.getMessage());
        }
        return networks;
    }

    private boolean isCancelled() {
        return currentWorker != null && currentWorker.isCancelled();
    }

    private List<WifiNetwork> scanWithNetsh() {
        List<WifiNetwork> networks = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("netsh", "wlan", "show", "networks", "mode=bssid");
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                WifiNetwork current = null;
                Pattern ssidPattern = Pattern.compile("SSID \\d+ : (.+)");

                while ((line = reader.readLine()) != null && !isCancelled()) {
                    line = line.trim();

                    var ssidMatch = ssidPattern.matcher(line);
                    if (ssidMatch.find()) {
                        String ssid = ssidMatch.group(1);
                        if (!networks.stream().anyMatch(n -> n.getSsid().equals(ssid))) {
                            current = new WifiNetwork(ssid, 0, "Unknown", "2.4/5 GHz", "N/A", false);
                            networks.add(current);
                            publish("Сеть: " + ssid);
                        }
                    }

                    if (current != null) {
                        if (line.startsWith("Signal")) {
                            String signalStr = line.split(":")[1].trim().replace("%", "");
                            current.setSignalStrength(parseSignal(signalStr));
                        } else if (line.startsWith("Authentication")) {
                            current.setSecurity(line.split(":")[1].trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            publish("netsh ошибка: " + e.getMessage());
        }
        return networks;
    }

    private List<WifiNetwork> scanWithAirport() {
        List<WifiNetwork> networks = new ArrayList<>();
        publish("Сканирование на macOS: используем airport");
        // Реализация упрощена
        return networks;
    }

    private List<WifiNetwork> getDemoNetworks() {
        publish("Демо-режим: генерация тестовых сетей");
        Random r = new Random();
        String[] ssids = {
                "Home_5G", "Office_WiFi", "Guest_Network", "Cafe_Free",
                "TP-Link_1234", "Beeline_Home", "MTS_5G", "Rostelecom"
        };
        String[] securities = {"WPA2", "WPA3", "Open", "WEP"};

        List<WifiNetwork> demo = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String ssid = ssids[r.nextInt(ssids.length)] + (r.nextBoolean() ? "_5G" : "");
            int signal = 30 + r.nextInt(70);
            String security = securities[r.nextInt(securities.length)];
            boolean is5G = ssid.contains("5G") || r.nextBoolean();
            demo.add(new WifiNetwork(ssid, signal, security, is5G ? "5 GHz" : "2.4 GHz", "AA:BB:CC:DD:EE:FF", is5G));
        }
        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
        return demo;
    }

    // === Вспомогательные методы ===
    private int parseSignal(String signal) {
        try {
            return Math.min(100, Math.max(0, Integer.parseInt(signal.replaceAll("\\D", ""))));
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isLinux() { return System.getProperty("os.name").toLowerCase().contains("linux"); }
    private boolean isWindows() { return System.getProperty("os.name").toLowerCase().contains("windows"); }
    private boolean isMac() { return System.getProperty("os.name").toLowerCase().contains("mac"); }

    private void log(String msg) { if (logger != null) logger.accept(msg); }
}