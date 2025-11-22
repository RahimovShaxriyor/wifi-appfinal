package org.example;

import javax.swing.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class WifiConnector {
    private final Consumer<String> logger;
    private volatile boolean isConnecting = false;
    private SwingWorker<Boolean, String> currentWorker;
    private String currentSsid;

    public WifiConnector(Consumer<String> logger) {
        this.logger = logger;
    }

    // === ПОДКЛЮЧЕНИЕ К СЕТИ ===
    public void connectToNetwork(String ssid, String password, Consumer<Boolean> callback) {
        if (isConnecting) {
            log("Подключение уже выполняется...");
            callback.accept(false);
            return;
        }

        this.currentSsid = ssid;
        isConnecting = true;
        log("Начинаю подключение к: " + ssid);

        currentWorker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    if (isLinux()) {
                        return connectLinux(ssid, password);
                    } else if (isWindows()) {
                        return connectWindows(ssid, password);
                    } else if (isMac()) {
                        return connectMac(ssid, password);
                    } else {
                        log("Неподдерживаемая ОС");
                        return false;
                    }
                } catch (Exception e) {
                    log("Критическая ошибка: " + e.getMessage());
                    return false;
                }
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                chunks.forEach(logger::accept);
            }

            @Override
            protected void done() {
                isConnecting = false;
                try {
                    boolean success = get();
                    callback.accept(success);
                } catch (Exception e) {
                    callback.accept(false);
                }
            }
        };

        currentWorker.execute();
    }

    // === ОТКЛЮЧЕНИЕ ОТ СЕТИ ===
    public void disconnectFromNetwork(Consumer<Boolean> callback) {
        new Thread(() -> {
            boolean success = false;
            try {
                if (isLinux()) {
                    success = runCommand("nmcli", "device", "disconnect", "wlan0");
                } else if (isWindows()) {
                    success = runCommand("netsh", "wlan", "disconnect");
                } else if (isMac()) {
                    runCommand("networksetup", "-setairportpower", "en0", "off");
                    Thread.sleep(1000);
                    runCommand("networksetup", "-setairportpower", "en0", "on");
                    success = true;
                }
            } catch (Exception e) {
                log("Ошибка отключения: " + e.getMessage());
            }
            log(success ? "Отключено от сети" : "Не удалось отключиться");
            callback.accept(success);
        }).start();
    }

    // === ПРОВЕРКА ПОДКЛЮЧЕНИЯ (ДЛЯ BRUTEFORCE) ===
    public boolean testConnection(String ssid, String password) {
        if (password == null || password.isEmpty()) {
            return testOpenNetwork(ssid);
        }

        try {
            if (isLinux()) {
                return testLinux(ssid, password);
            } else if (isWindows()) {
                return testWindows(ssid, password);
            } else {
                return demoPasswordCheck(password);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean testOpenNetwork(String ssid) {
        return connectTemporarily(ssid, null, 5);
    }

    private boolean testLinux(String ssid, String password) {
        return runCommandWithTimeout(8,
                "nmcli", "device", "wifi", "connect", ssid, "password", password);
    }

    private boolean testWindows(String ssid, String password) {
        String xml = createProfileXml(ssid, password);
        Path temp = createTempProfile(xml);
        boolean success = runCommand("netsh", "wlan", "add", "profile", "filename=" + temp);
        if (success) {
            success = runCommand("netsh", "wlan", "connect", "name=" + ssid);
            deleteTempProfile(temp);
        }
        return success;
    }

    // === ВРЕМЕННОЕ ПОДКЛЮЧЕНИЕ (ДЛЯ BRUTEFORCE) ===
    private boolean connectTemporarily(String ssid, String password, int timeoutSec) {
        try {
            List<String> cmd = new ArrayList<>();
            if (isLinux()) {
                cmd.addAll(List.of("timeout", timeoutSec + "s", "nmcli", "device", "wifi", "connect", ssid));
                if (password != null) cmd.addAll(List.of("password", password));
            } else if (isWindows()) {
                String xml = createProfileXml(ssid, password);
                Path temp = createTempProfile(xml);
                cmd.addAll(List.of("netsh", "wlan", "connect", "name=" + ssid));
                new Timer().schedule(new TimerTask() {
                    @Override public void run() {
                        deleteTempProfile(temp);
                    }
                }, timeoutSec * 1000 + 2000);
            } else {
                return false;
            }

            return runCommand(cmd.toArray(new String[0]));
        } catch (Exception e) {
            return false;
        }
    }

    // === LINUX ===
    private boolean connectLinux(String ssid, String password) {
        if (hasNetworkManager()) {
            List<String> cmd = new ArrayList<>(List.of("nmcli", "device", "wifi", "connect", ssid));
            if (password != null && !password.isEmpty()) {
                cmd.addAll(List.of("password", password));
            }
            if (runCommand(cmd.toArray(new String[0]))) {
                return verifyConnection(ssid);
            }
        }
        return false;
    }

    // === WINDOWS ===
    private boolean connectWindows(String ssid, String password) {
        deleteProfile(ssid);
        String xml = password == null || password.isEmpty()
                ? createOpenProfileXml(ssid)
                : createProfileXml(ssid, password);

        Path temp = createTempProfile(xml);
        if (!runCommand("netsh", "wlan", "add", "profile", "filename=" + temp)) {
            deleteTempProfile(temp);
            return false;
        }

        boolean connected = runCommand("netsh", "wlan", "connect", "name=" + ssid);
        deleteTempProfile(temp);

        if (connected) {
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            return verifyWindowsConnection(ssid);
        }
        return false;
    }

    // === MACOS ===
    private boolean connectMac(String ssid, String password) {
        List<String> cmd = new ArrayList<>(List.of("networksetup", "-setairportnetwork", "en0", ssid));
        if (password != null && !password.isEmpty()) {
            cmd.add(password);
        }
        return runCommand(cmd.toArray(new String[0])) && verifyConnection(ssid);
    }

    // === ПРОВЕРКА ПОДКЛЮЧЕНИЯ ===
    private boolean verifyConnection(String ssid) {
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        return hasInternet() && isConnectedTo(ssid);
    }

    private boolean isConnectedTo(String ssid) {
        if (isLinux()) {
            String output = runCommandWithOutput("nmcli", "-t", "-f", "NAME", "con", "show", "--active");
            return output != null && output.contains(ssid);
        } else if (isWindows()) {
            return verifyWindowsConnection(ssid);
        }
        return true;
    }

    private boolean verifyWindowsConnection(String ssid) {
        String output = runCommandWithOutput("netsh", "wlan", "show", "interfaces");
        return output != null
                && output.contains("SSID")
                && output.contains(ssid)
                && output.contains("connected");
    }

    private boolean hasInternet() {
        return runCommand("ping", "-c", "1", "8.8.8.8") || runCommand("ping", "-n", "1", "8.8.8.8");
    }

    // === XML ПРОФИЛИ ===
    private String createProfileXml(String ssid, String password) {
        return """
                <?xml version="1.0"?>
                <WLANProfile xmlns="http://www.microsoft.com/networking/WLAN/profile/v1">
                    <name>%s</name>
                    <SSIDConfig><SSID><name>%s</name></SSID></SSIDConfig>
                    <connectionType>ESS</connectionType>
                    <connectionMode>auto</connectionMode>
                    <MSM>
                        <security>
                            <authEncryption>
                                <authentication>WPA2PSK</authentication>
                                <encryption>AES</encryption>
                            </authEncryption>
                            <sharedKey>
                                <keyType>passPhrase</keyType>
                                <protected>false</protected>
                                <keyMaterial>%s</keyMaterial>
                            </sharedKey>
                        </security>
                    </MSM>
                </WLANProfile>
                """.formatted(escape(ssid), escape(ssid), escape(password));
    }

    private String createOpenProfileXml(String ssid) {
        return """
                <?xml version="1.0"?>
                <WLANProfile xmlns="http://www.microsoft.com/networking/WLAN/profile/v1">
                    <name>%s</name>
                    <SSIDConfig><SSID><name>%s</name></SSID></SSIDConfig>
                    <connectionType>ESS</connectionType>
                    <MSM><security><authEncryption><authentication>open</authentication><encryption>none</encryption></authEncryption></security></MSM>
                </WLANProfile>
                """.formatted(escape(ssid), escape(ssid));
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // === УТИЛИТЫ ===
    private Path createTempProfile(String xml) {
        try {
            Path temp = Files.createTempFile("wifi_", ".xml");
            Files.writeString(temp, xml);
            temp.toFile().deleteOnExit();
            return temp;
        } catch (IOException e) {
            log("Ошибка создания профиля: " + e.getMessage());
            return null;
        }
    }

    private void deleteTempProfile(Path path) {
        if (path != null) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) {}
        }
    }

    private void deleteProfile(String ssid) {
        runCommand("netsh", "wlan", "delete", "profile", ssid);
    }

    private boolean hasNetworkManager() {
        return runCommand("which", "nmcli") || runCommand("command", "-v", "nmcli");
    }

    // === ВЫПОЛНЕНИЕ КОМАНД ===
    private boolean runCommand(String... cmd) {
        return runCommandWithTimeout(30, cmd);
    }

    private boolean runCommandWithTimeout(int timeoutSec, String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            String output = readOutput(p);
            boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);

            if (!finished) {
                p.destroyForcibly();
                return false;
            }

            int code = p.exitValue();
            log("CMD: " + maskPassword(Arrays.toString(cmd)) + " → " + code);
            return code == 0;
        } catch (Exception e) {
            log("Ошибка команды: " + e.getMessage());
            return false;
        }
    }

    private String runCommandWithOutput(String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = readOutput(p);
            p.waitFor();
            return output;
        } catch (Exception e) {
            return null;
        }
    }

    private String readOutput(Process p) throws IOException {
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append("\n");
                log(line);
            }
        }
        return out.toString();
    }

    private String maskPassword(String cmd) {
        return cmd.replaceAll("(?i)password[=\\s][^\\s]+", "password=*****");
    }

    private boolean demoPasswordCheck(String password) {
        Set<String> valid = Set.of("12345678", "00000000", "password", "admin");
        return valid.contains(password) || (password != null && password.matches(".*\\d{4}.*"));
    }

    // === СОСТОЯНИЕ ===
    public boolean isConnecting() { return isConnecting; }
    public void stopConnection() {
        if (currentWorker != null) {
            currentWorker.cancel(true);
            isConnecting = false;
            log("Подключение остановлено");
        }
    }

    public String getCurrentSsid() { return currentSsid; }

    private boolean isLinux() { return System.getProperty("os.name").toLowerCase().contains("linux"); }
    private boolean isWindows() { return System.getProperty("os.name").toLowerCase().contains("windows"); }
    private boolean isMac() { return System.getProperty("os.name").toLowerCase().contains("mac"); }

    private void log(String msg) { if (logger != null) logger.accept(msg); }
}