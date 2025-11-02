package org.example;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class WifiScanner {
    private Consumer<String> logger;
    private volatile boolean isScanning = false;
    private SwingWorker<List<String>, String> currentWorker;

    public WifiScanner(Consumer<String> logger) {
        this.logger = logger;
    }

    public void scanNetworks(Consumer<List<String>> callback) {
        if (isScanning) {
            log("⚠️ Сканирование уже выполняется...");
            return;
        }

        isScanning = true;
        log("🔍 Запуск сканирования Wi-Fi сетей...");

        currentWorker = new SwingWorker<List<String>, String>() {
            private long startTime;

            @Override
            protected List<String> doInBackground() {
                startTime = System.currentTimeMillis();
                List<String> networks = new ArrayList<>();

                try {
                    // Способ 1: Используем nmcli (Linux)
                    if (isLinux()) {
                        networks = scanWithNmcli();
                    }
                    // Способ 2: Используем netsh (Windows)
                    else if (isWindows()) {
                        networks = scanWithNetsh();
                    }
                    // Способ 3: Используем airport (macOS)
                    else if (isMac()) {
                        networks = scanWithAirport();
                    }
                    // Способ 4: Резервный - демо-данные
                    else {
                        networks = getDemoNetworks();
                        publish("ℹ️ Используются демо-данные для тестирования");
                    }

                } catch (Exception ex) {
                    publish("❌ Критическая ошибка сканирования: " + ex.getMessage());
                    networks = getDemoNetworks(); // Fallback to demo data
                }

                return networks;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    log(message);
                }
            }

            @Override
            protected void done() {
                isScanning = false;
                long scanTime = System.currentTimeMillis() - startTime;

                try {
                    List<String> result = get();
                    if (result.isEmpty()) {
                        log("❌ Сети не обнаружены");
                    } else {
                        log("✅ Сканирование завершено за " + scanTime + "мс");
                        log("📊 Найдено сетей: " + result.size());
                    }
                    callback.accept(result);
                } catch (Exception ex) {
                    log("❌ Ошибка получения результатов: " + ex.getMessage());
                    callback.accept(getDemoNetworks());
                }
            }

            // === МЕТОДЫ СКАНИРОВАНИЯ ДЛЯ РАЗНЫХ ОС ===

            private List<String> scanWithNmcli() {
                List<String> networks = new ArrayList<>();
                try {
                    ProcessBuilder pb = new ProcessBuilder("nmcli", "-t", "-f", "SSID,SIGNAL,SECURITY", "dev", "wifi");
                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        Set<String> seenNetworks = new LinkedHashSet<>();

                        while ((line = reader.readLine()) != null && !isCancelled()) {
                            String[] parts = line.split(":", -1);
                            if (parts.length >= 1) {
                                String ssid = parts[0].trim();

                                // Пропускаем пустые SSID и скрытые сети
                                if (!ssid.isEmpty() && !ssid.equals("--") && !seenNetworks.contains(ssid)) {
                                    seenNetworks.add(ssid);

                                    // Извлекаем дополнительную информацию
                                    String signal = parts.length > 1 ? parts[1] : "0";
                                    String security = parts.length > 2 ? parts[2] : "none";

                                    String networkInfo = formatNetworkInfo(ssid, signal, security);
                                    publish("📶 Обнаружена сеть: " + networkInfo);
                                }
                            }
                        }

                        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
                        if (!finished) {
                            process.destroy();
                            publish("⚠️ Сканирование прервано по таймауту");
                        }

                        networks.addAll(seenNetworks);
                    }
                } catch (Exception e) {
                    publish("❌ Ошибка nmcli: " + e.getMessage());
                    // Пробуем альтернативную команду
                    networks = scanWithIwlist();
                }
                return networks;
            }

            private List<String> scanWithIwlist() {
                List<String> networks = new ArrayList<>();
                try {
                    ProcessBuilder pb = new ProcessBuilder("iwlist", "scanning");
                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        String currentSsid = null;

                        while ((line = reader.readLine()) != null && !isCancelled()) {
                            line = line.trim();

                            // Ищем SSID
                            if (line.contains("ESSID:")) {
                                String ssid = line.substring(line.indexOf("ESSID:") + 6).trim();
                                ssid = ssid.replace("\"", "").trim();

                                if (!ssid.isEmpty() && !ssid.equals("\\x00")) {
                                    currentSsid = ssid;
                                    if (!networks.contains(ssid)) {
                                        networks.add(ssid);
                                        publish("📶 Сеть: " + ssid);
                                    }
                                }
                            }
                        }

                        process.waitFor(10, TimeUnit.SECONDS);
                    }
                } catch (Exception e) {
                    publish("❌ Ошибка iwlist: " + e.getMessage());
                }
                return networks;
            }

            private List<String> scanWithNetsh() {
                List<String> networks = new ArrayList<>();
                try {
                    ProcessBuilder pb = new ProcessBuilder("netsh", "wlan", "show", "networks", "mode=bssid");
                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        String currentSsid = null;
                        Pattern ssidPattern = Pattern.compile("SSID \\d+ : (.+)");

                        while ((line = reader.readLine()) != null && !isCancelled()) {
                            line = line.trim();

                            if (line.startsWith("SSID") && line.contains(":")) {
                                String ssid = line.substring(line.indexOf(":") + 1).trim();
                                if (!ssid.isEmpty() && !networks.contains(ssid)) {
                                    networks.add(ssid);
                                    currentSsid = ssid;
                                    publish("📶 Сеть: " + ssid);
                                }
                            }
                        }

                        process.waitFor(15, TimeUnit.SECONDS);
                    }
                } catch (Exception e) {
                    publish("❌ Ошибка netsh: " + e.getMessage());
                    // Альтернативная команда для Windows
                    networks = scanWithNetshSimple();
                }
                return networks;
            }

            private List<String> scanWithNetshSimple() {
                List<String> networks = new ArrayList<>();
                try {
                    ProcessBuilder pb = new ProcessBuilder("netsh", "wlan", "show", "networks");
                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        boolean inNetworkSection = false;

                        while ((line = reader.readLine()) != null && !isCancelled()) {
                            line = line.trim();

                            if (line.contains("SSID") && line.contains(":")) {
                                String ssid = line.substring(line.indexOf(":") + 1).trim();
                                if (!ssid.isEmpty() && !networks.contains(ssid)) {
                                    networks.add(ssid);
                                    publish("📶 Сеть: " + ssid);
                                }
                            }
                        }

                        process.waitFor(10, TimeUnit.SECONDS);
                    }
                } catch (Exception e) {
                    publish("❌ Ошибка альтернативного сканирования: " + e.getMessage());
                }
                return networks;
            }

            private List<String> scanWithAirport() {
                List<String> networks = new ArrayList<>();
                try {
                    // Пробуем разные пути к airport utility
                    String[] airportPaths = {
                            "/System/Library/PrivateFrameworks/Apple80211.framework/Versions/Current/Resources/airport",
                            "/System/Library/PrivateFrameworks/Apple80211.framework/Versions/A/Resources/airport",
                            "/usr/sbin/airport"
                    };

                    Process process = null;
                    for (String path : airportPaths) {
                        try {
                            ProcessBuilder pb = new ProcessBuilder(path, "-s");
                            pb.redirectErrorStream(true);
                            process = pb.start();
                            break;
                        } catch (Exception e) {
                            continue;
                        }
                    }

                    if (process == null) {
                        publish("❌ Airport utility не найден");
                        return networks;
                    }

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        boolean firstLine = true;

                        while ((line = reader.readLine()) != null && !isCancelled()) {
                            if (firstLine) {
                                firstLine = false;
                                continue; // Пропускаем заголовок
                            }

                            if (!line.trim().isEmpty()) {
                                // Парсим строку вида "SSID BSSID RSSI CHANNEL SECURITY"
                                String[] parts = line.split("\\s+", 5);
                                if (parts.length >= 1) {
                                    String ssid = parts[0].trim();
                                    if (!ssid.isEmpty() && !networks.contains(ssid)) {
                                        networks.add(ssid);
                                        publish("📶 Сеть: " + ssid);
                                    }
                                }
                            }
                        }

                        process.waitFor(10, TimeUnit.SECONDS);
                    }
                } catch (Exception e) {
                    publish("❌ Ошибка airport: " + e.getMessage());
                }
                return networks;
            }

            private List<String> getDemoNetworks() {
                publish("ℹ️ Используются демонстрационные сети для тестирования");

                // Реалистичные демо-сети
                String[] demoNetworks = {
                        "Home_Network_5G",
                        "TP-Link_Office",
                        "Moscow_WiFi_Free",
                        "Yota_Public",
                        "Beeline_Home",
                        "MTS_FREE",
                        "AndroidAP",
                        "iPhone_Network",
                        "Xiaomi_Router",
                        "Asus_RT-AC86U",
                        "Dlink_DIR-825",
                        "Huawei_Home",
                        "Rostelecom",
                        "Dom.ru_WiFi",
                        "Starbucks_Free",
                        "Airport_WiFi",
                        "Hotel_Guest",
                        "Conference_Room"
                };

                // Имитация задержки сканирования
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                return new ArrayList<>(Arrays.asList(demoNetworks));
            }

            private String formatNetworkInfo(String ssid, String signal, String security) {
                StringBuilder info = new StringBuilder();
                info.append(ssid);

                if (!signal.equals("0") && !signal.isEmpty()) {
                    try {
                        int signalStrength = Integer.parseInt(signal);
                        info.append(" (").append(signalStrength).append("%%)");
                    } catch (NumberFormatException e) {
                        info.append(" [").append(signal).append("]");
                    }
                }

                if (!security.isEmpty() && !security.equals("none")) {
                    info.append(" - ").append(security);
                }

                return info.toString();
            }
        };

        currentWorker.execute();
    }

    public void stopScanning() {
        if (isScanning && currentWorker != null) {
            currentWorker.cancel(true);
            isScanning = false;
            log("⏹️ Сканирование остановлено пользователем");
        }
    }

    public boolean isScanning() {
        return isScanning;
    }

    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===

    private boolean isLinux() {
        return System.getProperty("os.name").toLowerCase().contains("linux");
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }

    // === ДОПОЛНИТЕЛЬНЫЕ МЕТОДЫ ДЛЯ РАСШИРЕННОЙ ИНФОРМАЦИИ ===

    public void getDetailedNetworkInfo(String ssid, Consumer<Map<String, String>> callback) {
        new Thread(() -> {
            Map<String, String> info = new HashMap<>();
            try {
                if (isLinux()) {
                    info = getNmcliDetailedInfo(ssid);
                } else if (isWindows()) {
                    info = getNetshDetailedInfo(ssid);
                } else if (isMac()) {
                    info = getAirportDetailedInfo(ssid);
                }
            } catch (Exception e) {
                log("❌ Ошибка получения детальной информации: " + e.getMessage());
            }
            callback.accept(info);
        }).start();
    }

    private Map<String, String> getNmcliDetailedInfo(String ssid) {
        Map<String, String> info = new HashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("nmcli", "-t", "-f", "ACTIVE,SSID,SIGNAL,SECURITY,FREQ", "dev", "wifi");
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":");
                    if (parts.length >= 2 && ssid.equals(parts[1])) {
                        info.put("active", parts[0]);
                        info.put("signal", parts.length > 2 ? parts[2] : "N/A");
                        info.put("security", parts.length > 3 ? parts[3] : "N/A");
                        info.put("frequency", parts.length > 4 ? parts[4] : "N/A");
                        break;
                    }
                }
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            // Игнорируем ошибки детальной информации
        }
        return info;
    }

    private Map<String, String> getNetshDetailedInfo(String ssid) {
        Map<String, String> info = new HashMap<>();
        // Реализация для Windows...
        return info;
    }

    private Map<String, String> getAirportDetailedInfo(String ssid) {
        Map<String, String> info = new HashMap<>();
        // Реализация для macOS...
        return info;
    }
}