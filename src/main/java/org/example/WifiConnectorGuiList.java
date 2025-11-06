package org.example;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import net.miginfocom.swing.MigLayout;
import org.pushingpixels.radiance.animation.api.Timeline;
import org.pushingpixels.radiance.animation.api.Timeline.RepeatBehavior;
import org.pushingpixels.radiance.animation.api.swing.SwingComponentTimeline;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

public class WifiConnectorGuiList extends JFrame {
    private DefaultListModel<String> listModel = new DefaultListModel<>();
    private JList<String> ssidList = new JList<>(listModel);
    private JPasswordField passwordField = new JPasswordField();
    private JTextArea logArea = new JTextArea(10, 50);
    private JButton refreshButton = new JButton("🔄 Обновить");
    private JButton connectButton = new JButton("🔗 Подключить");
    private JButton disconnectButton = new JButton("🔌 Отключить");
    private JButton bruteForceButton = new JButton("🔓 Подобрать пароль");
    private JButton themeToggleButton = new JButton("🌙");
    private JCheckBox showPasswordsCheckbox = new JCheckBox("Показывать пароли");
    private JCheckBox autoConnectCheckbox = new JCheckBox("Автоподключение");
    private ModernProgressBar progressBar = new ModernProgressBar();
    private JLabel statusLabel = new JLabel("Готов к работе");
    private JLabel signalStrengthLabel = new JLabel("💡 Выберите сеть для информации");
    private JLabel connectionStatusLabel = new JLabel("🔴 Не подключено");
    private JComboBox<String> filterComboBox = new JComboBox<>(new String[]{"Все сети", "Сильные сигналы", "Открытые сети", "Защищенные сети"});

    private WifiScanner wifiScanner;
    private WifiConnector wifiConnector;
    private PasswordBruteForcer bruteForcer;

    private boolean darkTheme = true;

    public WifiConnectorGuiList() {
        super("Wi-Fi Connector Pro");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        wifiScanner = new WifiScanner(this::appendLog);
        wifiConnector = new WifiConnector(this::appendLog);
        bruteForcer = new PasswordBruteForcer(wifiConnector, this::appendLog);

        setupProfessionalUI();
        pack();
        setLocationRelativeTo(null);
        addListeners();
        scanNetworks();
    }

    private void setupProfessionalUI() {
        setLayout(new MigLayout("fill, insets 30", "[grow]", "[][grow][]"));

        add(createHeaderPanel(), "grow, wrap");
        add(createContentPanel(), "grow, wrap");
        add(createBottomPanel(), "grow");

        setIconImage(createModernAppIcon());
        setMinimumSize(new Dimension(1000, 800));
        setPreferredSize(new Dimension(1200, 900));
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new ModernCardPanel();
        panel.setLayout(new MigLayout("fill, insets 20", "[grow][]", "[]"));

        JLabel titleLabel = new JLabel("🌐 Wi-Fi Connector Pro");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));

        JLabel subtitleLabel = new JLabel("Управляй Wi-Fi соединениями легко и эффективно");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        JPanel titlePanel = new JPanel(new MigLayout("insets 0, gapy 0", "[grow]", "[]0[]"));
        titlePanel.setOpaque(false);
        titlePanel.add(titleLabel, "wrap");
        titlePanel.add(subtitleLabel);

        JPanel rightPanel = new JPanel(new MigLayout("insets 0, gapx 10", "[][][]", "[]"));
        rightPanel.setOpaque(false);

        connectionStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        rightPanel.add(connectionStatusLabel);

        JLabel filterLabel = new JLabel("Фильтр:");
        filterLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        rightPanel.add(filterLabel);

        setupModernComboBox(filterComboBox);
        rightPanel.add(filterComboBox);

        themeToggleButton.setPreferredSize(new Dimension(40, 40));
        styleIconButton(themeToggleButton);
        rightPanel.add(themeToggleButton);

        panel.add(titlePanel, "grow");
        panel.add(rightPanel);

        return panel;
    }

    private JPanel createContentPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 0", "[400:400:500][300:300:400]", "[grow]"));
        panel.setOpaque(false);

        panel.add(createNetworksPanel(), "grow");
        panel.add(createControlPanel(), "grow");

        return panel;
    }

    private JPanel createNetworksPanel() {
        JPanel panel = new ModernCardPanel();
        panel.setLayout(new MigLayout("fill, insets 20", "[grow]", "[][grow][]"));

        JLabel titleLabel = new ModernSectionLabel("📶 Доступные сети Wi-Fi");
        panel.add(titleLabel, "wrap");

        ssidList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        ssidList.setCellRenderer(new ModernNetworkListRenderer());

        JScrollPane scrollPane = new ModernScrollPane(ssidList);
        panel.add(scrollPane, "grow, wrap");

        signalStrengthLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JPanel infoPanel = createInfoPanel();
        infoPanel.add(signalStrengthLabel);
        panel.add(infoPanel, "grow");

        return panel;
    }

    private JPanel createControlPanel() {
        JPanel panel = new ModernCardPanel();
        panel.setLayout(new MigLayout("fill, insets 20", "[grow]", "[][grow]"));

        panel.add(new ModernSectionLabel("⚙️ Управление подключением"), "wrap");

        JPanel content = new JPanel(new MigLayout("fill, insets 0", "[grow]", "[][][][grow]"));
        content.setOpaque(false);

        content.add(createPasswordPanel(), "grow, wrap");
        content.add(createOptionsPanel(), "grow, wrap");
        content.add(createButtonsPanel(), "grow, wrap");
        content.add(createProgressPanel(), "grow");

        panel.add(content, "grow");

        return panel;
    }

    private JPanel createPasswordPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 0", "[grow][]", "[]0[]"));
        panel.setOpaque(false);

        JLabel label = new ModernFieldLabel("🔑 Пароль сети");
        panel.add(label, "wrap");

        passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        passwordField.putClientProperty("JTextField.placeholderText", "Введите пароль...");
        panel.add(passwordField, "grow");

        JButton toggleBtn = new JButton("👁");
        toggleBtn.setPreferredSize(new Dimension(45, 45));
        styleIconButton(toggleBtn);
        panel.add(toggleBtn);

        toggleBtn.addActionListener(e -> togglePasswordVisibility(toggleBtn));

        return panel;
    }
    private JPanel createOptionsPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 15 0 15 0, gapy 8", "[grow]", "[]8[]"));
        panel.setOpaque(false);

        setupModernCheckbox(showPasswordsCheckbox);
        setupModernCheckbox(autoConnectCheckbox);

        panel.add(showPasswordsCheckbox, "grow, wrap");
        panel.add(autoConnectCheckbox, "grow");

        return panel;
    }

    private JPanel createButtonsPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 0", "[grow][grow]", "[grow][grow]"));
        panel.setOpaque(false);

        styleModernButton(refreshButton, new Color(0, 122, 255));
        styleModernButton(connectButton, new Color(52, 199, 89));
        styleModernButton(disconnectButton, new Color(255, 149, 0));
        styleModernButton(bruteForceButton, new Color(151, 92, 228));

        panel.add(refreshButton, "grow");
        panel.add(connectButton, "grow, wrap");
        panel.add(disconnectButton, "grow");
        panel.add(bruteForceButton, "grow");

        return panel;
    }

    private JPanel createProgressPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 10 0 0 0", "[grow]", "[grow][]"));
        panel.setOpaque(false);

        progressBar.setVisible(false);
        panel.add(progressBar, "grow, wrap");

        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        panel.add(statusLabel, "grow");

        return panel;
    }


    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 0", "[grow]", "[grow]"));
        panel.setOpaque(false);

        JPanel logPanel = new ModernCardPanel();
        logPanel.setLayout(new MigLayout("fill, insets 20", "[grow]", "[][grow][]"));

        logPanel.add(new ModernSectionLabel("📝 Журнал событий"), "wrap");

        logArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane logScroll = new ModernScrollPane(logArea);
        logPanel.add(logScroll, "grow, wrap");

        JPanel logControls = new JPanel(new MigLayout("insets 0", "[right]", "[]"));
        logControls.setOpaque(false);

        JButton clearBtn = new JButton("Очистить");
        styleModernButton(clearBtn, new Color(108, 117, 125));
        clearBtn.setPreferredSize(new Dimension(100, 35));
        logControls.add(clearBtn);

        clearBtn.addActionListener(e -> logArea.setText(""));

        logPanel.add(logControls, "grow");

        panel.add(logPanel, "grow");

        return panel;
    }

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 15", "[grow]", "[]"));
        panel.setBackground(new Color(248, 249, 250));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 226, 230)),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));
        return panel;
    }

    private void setupModernComboBox(JComboBox<String> comboBox) {
        comboBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        comboBox.setPreferredSize(new Dimension(160, 40));
    }

    private void setupModernCheckbox(JCheckBox checkbox) {
        checkbox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        checkbox.setOpaque(false);
    }

    private void styleModernButton(JButton button, Color color) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        SwingComponentTimeline timeline = SwingComponentTimeline.componentBuilder(button)
                .addPropertyToInterpolate("background", color, color.brighter())
                .setDuration(200)
                .build();

        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                timeline.play();
            }
            public void mouseExited(MouseEvent e) {
                timeline.playReverse();
            }
        });
    }

    private void styleIconButton(JButton button) {
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBackground(new Color(73, 73, 74));
        button.setForeground(Color.WHITE);

        Timeline timeline = Timeline.builder(button)
                .addPropertyToInterpolate("background", new Color(73, 73, 74), new Color(99, 99, 102))
                .setDuration(150)
                .build();

        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                timeline.play();
            }
            public void mouseExited(MouseEvent e) {
                timeline.playReverse();
            }
        });
    }

    private void togglePasswordVisibility(JButton toggleBtn) {
        if (passwordField.getEchoChar() == 0) {
            passwordField.setEchoChar('•');
            toggleBtn.setText("👁");
        } else {
            passwordField.setEchoChar((char) 0);
            toggleBtn.setText("🙈");
        }
    }

    private void addListeners() {
        refreshButton.addActionListener(e -> scanNetworks());

        connectButton.addActionListener(e -> {
            String ssid = ssidList.getSelectedValue();
            if (ssid == null || ssid.isEmpty()) {
                showToast("Выберите сеть из списка", new Color(255, 59, 48));
                return;
            }
            connectToNetwork(ssid, new String(passwordField.getPassword()));
        });

        disconnectButton.addActionListener(e -> disconnectFromNetwork());

        bruteForceButton.addActionListener(e -> {
            String ssid = ssidList.getSelectedValue();
            if (ssid == null || ssid.isEmpty()) {
                showToast("Выберите сеть из списка", new Color(255, 59, 48));
                return;
            }

            if (bruteForcer.isRunning()) {
                stopBruteForce();
            } else {
                startBruteForce(ssid);
            }
        });

        themeToggleButton.addActionListener(e -> toggleTheme());

        ssidList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = ssidList.getSelectedValue();
                if (selected != null) {
                    statusLabel.setText("Выбрана сеть: " + selected);
                    updateNetworkInfo(selected);
                }
            }
        });

        ssidList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String ssid = ssidList.getSelectedValue();
                    if (ssid != null) {
                        passwordField.requestFocusInWindow();
                        appendLog("🔄 Двойной клик по сети: " + ssid);
                    }
                }
            }
        });

        filterComboBox.addActionListener(e -> {
            String filter = (String) filterComboBox.getSelectedItem();
            appendLog("🔍 Применен фильтр: " + filter);
        });
    }

    private void toggleTheme() {
        FlatAnimatedLafChange.showSnapshot();

        try {
            if (darkTheme) {
                FlatLightLaf.setup();
                themeToggleButton.setText("🌞");
            } else {
                FlatDarkLaf.setup();
                themeToggleButton.setText("🌙");
            }
            darkTheme = !darkTheme;

            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }

    private void showToast(String message, Color color) {
        JWindow toast = new JWindow();
        toast.setLayout(new BorderLayout());

        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Segoe UI", Font.BOLD, 14));
        label.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));

        JPanel content = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                g2.dispose();
            }
        };
        content.setLayout(new BorderLayout());
        content.add(label);

        toast.setContentPane(content);
        toast.setSize(300, 60);
        toast.setLocationRelativeTo(null);

        animateToast(toast);
    }

    private void animateToast(JWindow toast) {
        toast.setOpacity(0f);
        toast.setVisible(true);

        Timeline.builder(toast)
                .addPropertyToInterpolate("opacity", 0f, 1f)
                .addPropertyToInterpolate("location",
                        new Point(toast.getX(), toast.getY() + 20),
                        new Point(toast.getX(), toast.getY()))
                .setDuration(300)
                .play();

        Timeline.builder(toast)
                .addPropertyToInterpolate("opacity", 1f, 0f)
                .addPropertyToInterpolate("location",
                        new Point(toast.getX(), toast.getY()),
                        new Point(toast.getX(), toast.getY() - 20))
                .setDuration(300)
                .play();

        new Timer(3100, e -> {
            toast.dispose();
        }).start();
    }

    private void updateNetworkInfo(String ssid) {
        int signalStrength = (int)(Math.random() * 5) + 1;
        boolean isSecure = Math.random() > 0.3;

        String signalText = switch (signalStrength) {
            case 5 -> "📶 Отличный сигнал";
            case 4 -> "📶 Хороший сигнал";
            case 3 -> "📶 Средний сигнал";
            case 2 -> "📶 Слабый сигнал";
            default -> "📶 Очень слабый сигнал";
        };

        String securityText = isSecure ? "🔒 Защита: WPA2" : "🔓 Открытая сеть";
        signalStrengthLabel.setText(signalText + " • " + securityText);
    }

    private void scanNetworks() {
        setControlsEnabled(false);
        statusLabel.setText("🔍 Сканирование сетей...");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        refreshButton.setText("⏳ Сканирование...");

        wifiScanner.scanNetworks(networks -> {
            SwingUtilities.invokeLater(() -> {
                listModel.clear();
                if (networks.isEmpty()) {
                    appendLog("❌ Wi-Fi сети не обнаружены");
                    statusLabel.setText("Сети не найдены");
                    showToast("Не удалось найти Wi-Fi сети", new Color(255, 59, 48));
                } else {
                    appendLog("✅ Обнаружено сетей: " + networks.size());
                    statusLabel.setText("Найдено " + networks.size() + " сетей");
                    for (String network : networks) {
                        listModel.addElement(network);
                    }
                    if (!networks.isEmpty()) {
                        ssidList.setSelectedIndex(0);
                    }
                }
                setControlsEnabled(true);
                progressBar.setVisible(false);
                refreshButton.setText("🔄 Обновить");
            });
        });
    }

    private void connectToNetwork(String ssid, String password) {
        setControlsEnabled(false);
        statusLabel.setText("🔗 Подключение к " + ssid + "...");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        connectButton.setText("⏳ Подключение...");

        wifiConnector.connectToNetwork(ssid, password, success -> {
            SwingUtilities.invokeLater(() -> {
                if (success) {
                    appendLog("✅ Успешное подключение к " + ssid);
                    statusLabel.setText("Подключено к " + ssid);
                    connectionStatusLabel.setText("🟢 Подключено к " + ssid);
                    connectionStatusLabel.setForeground(new Color(52, 199, 89));
                    showToast("Успешное подключение к " + ssid, new Color(52, 199, 89));
                } else {
                    appendLog("❌ Ошибка подключения к " + ssid);
                    statusLabel.setText("Ошибка подключения");
                    showToast("Не удалось подключиться к " + ssid, new Color(255, 59, 48));
                }
                setControlsEnabled(true);
                progressBar.setVisible(false);
                connectButton.setText("🔗 Подключить");
            });
        });
    }

    private void disconnectFromNetwork() {
        setControlsEnabled(false);
        statusLabel.setText("🔌 Отключение от сети...");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        new Thread(() -> {
            try {
                Thread.sleep(2000);
                SwingUtilities.invokeLater(() -> {
                    appendLog("🔌 Отключено от текущей сети");
                    statusLabel.setText("Не подключено");
                    connectionStatusLabel.setText("🔴 Не подключено");
                    connectionStatusLabel.setForeground(new Color(255, 59, 48));
                    progressBar.setVisible(false);
                    setControlsEnabled(true);
                    showToast("Успешно отключено от сети", new Color(52, 199, 89));
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void startBruteForce(String ssid) {
        boolean showPasswords = showPasswordsCheckbox.isSelected();
        bruteForceButton.setText("⏹️ Остановить");
        bruteForceButton.setBackground(new Color(255, 59, 48));
        statusLabel.setText("🔓 Подбор пароля для " + ssid);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        bruteForcer.startBruteForce(ssid, showPasswords, foundPassword -> {
            SwingUtilities.invokeLater(() -> {
                if (foundPassword != null) {
                    passwordField.setText(foundPassword);
                    appendLog("🎉 Пароль успешно подобран: " + foundPassword);
                    statusLabel.setText("Пароль найден!");
                    progressBar.setVisible(false);

                    if (autoConnectCheckbox.isSelected()) {
                        appendLog("⚡ Автоподключение к сети...");
                        connectToNetwork(ssid, foundPassword);
                    } else {
                        int result = JOptionPane.showConfirmDialog(this,
                                "<html><b>Пароль найден!</b><br>Сеть: <b>" + ssid +
                                        "</b><br>Пароль: <b>" + foundPassword + "</b><br><br>" +
                                        "Подключиться к сети?</html>",
                                "Пароль подобран",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.QUESTION_MESSAGE);

                        if (result == JOptionPane.YES_OPTION) {
                            connectToNetwork(ssid, foundPassword);
                        }
                    }
                } else {
                    statusLabel.setText("Подбор завершен - пароль не найден");
                    progressBar.setVisible(false);
                    showToast("Пароль не найден за отведенное время", new Color(255, 149, 0));
                }
                bruteForceButton.setText("🔓 Подобрать пароль");
                bruteForceButton.setBackground(new Color(151, 92, 228));
                setControlsEnabled(true);
            });
        });
    }

    private void stopBruteForce() {
        bruteForcer.stopBruteForce();
        bruteForceButton.setText("🔓 Подобрать пароль");
        bruteForceButton.setBackground(new Color(151, 92, 228));
        statusLabel.setText("Подбор остановлен пользователем");
        progressBar.setVisible(false);
        setControlsEnabled(true);
        appendLog("⏹️ Подбор пароля остановлен");
    }

    private void setControlsEnabled(boolean enabled) {
        refreshButton.setEnabled(enabled);
        connectButton.setEnabled(enabled);
        disconnectButton.setEnabled(enabled);
        ssidList.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        showPasswordsCheckbox.setEnabled(enabled);
        autoConnectCheckbox.setEnabled(enabled);
        filterComboBox.setEnabled(enabled);

        if (enabled) {
            bruteForceButton.setEnabled(true);
        }
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = java.time.LocalTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));

            if (message.contains("❌") || message.contains("Ошибка") || message.contains("остановлен")) {
                logArea.append("[" + timestamp + "] " + message + "\n");
            } else if (message.contains("✅") || message.contains("Успех") || message.contains("НАЙДЕН")) {
                logArea.append("[" + timestamp + "] " + message + "\n");
            } else if (message.contains("⚠️") || message.contains("Внимание")) {
                logArea.append("[" + timestamp + "] " + message + "\n");
            } else {
                logArea.append("[" + timestamp + "] " + message + "\n");
            }

            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private Image createModernAppIcon() {
        int size = 64;
        BufferedImage icon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = icon.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint gradient = new GradientPaint(0, 0, new Color(0, 122, 255),
                size, size, new Color(13, 110, 253));
        g2.setPaint(gradient);
        g2.fillRoundRect(0, 0, size, size, 16, 16);

        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2.8f));
        int centerX = size / 2;
        int centerY = size / 2;

        for (int i = 0; i < 4; i++) {
            int radius = 6 + i * 6;
            float alpha = 1.0f - (i * 0.25f);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.drawArc(centerX - radius, centerY - radius, radius * 2, radius * 2, -50, 100);
        }

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g2.fillOval(centerX - 3, centerY - 3, 6, 6);

        g2.dispose();
        return icon;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                FlatDarkLaf.setup();

                UIManager.put("Button.arc", 12);
                UIManager.put("Component.arc", 12);
                UIManager.put("TextComponent.arc", 8);
                UIManager.put("ScrollBar.width", 16);

                new WifiConnectorGuiList().setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}

class ModernCardPanel extends JPanel {
    public ModernCardPanel() {
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

        g2.setColor(UIManager.getColor("Component.borderColor"));
        g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 20, 20);

        g2.dispose();
    }
}

class ModernScrollPane extends JScrollPane {
    public ModernScrollPane(Component view) {
        super(view);
        setBorder(BorderFactory.createEmptyBorder());
        setOpaque(false);
        getViewport().setOpaque(false);
    }
}

class ModernProgressBar extends JProgressBar {
    public ModernProgressBar() {
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }
}
//
class ModernSectionLabel extends JLabel {
    public ModernSectionLabel(String text) {
        super(text);
        setFont(new Font("Segoe UI", Font.BOLD, 18));
    }
}

class ModernFieldLabel extends JLabel {
    public ModernFieldLabel(String text) {
        super(text);
        setFont(new Font("Segoe UI", Font.BOLD, 14));
    }
}

class ModernNetworkListRenderer extends JPanel implements ListCellRenderer<String> {
    private JLabel nameLabel = new JLabel();
    private JLabel signalLabel = new JLabel();
    private JLabel securityLabel = new JLabel();

    public ModernNetworkListRenderer() {
        setLayout(new MigLayout("fill, insets 12", "[grow]", "[]0[]"));
        setOpaque(true);

        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        signalLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        securityLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        JPanel detailsPanel = new JPanel(new MigLayout("insets 0, gapx 8", "[][right]", "[]"));
        detailsPanel.setOpaque(false);
        detailsPanel.add(signalLabel);
        detailsPanel.add(securityLabel, "push");

        add(nameLabel, "wrap");
        add(detailsPanel, "grow");
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends String> list, String value,
                                                  int index, boolean isSelected, boolean cellHasFocus) {
        nameLabel.setText(value);

        int signalStrength = (int)(Math.random() * 5) + 1;
        boolean isSecure = Math.random() > 0.3;

        signalLabel.setText("📶 " + getSignalText(signalStrength));
        signalLabel.setForeground(getSignalColor(signalStrength));
        securityLabel.setText(isSecure ? "🔒 WPA2" : "🔓 Открытая");
        securityLabel.setForeground(isSecure ? new Color(52, 199, 89) : new Color(255, 149, 0));

        if (isSelected) {
            setBackground(new Color(0, 122, 255));
            nameLabel.setForeground(Color.WHITE);
            signalLabel.setForeground(Color.WHITE);
            securityLabel.setForeground(Color.WHITE);
        } else {
            setBackground(index % 2 == 0 ?
                    UIManager.getColor("Table.background") :
                    UIManager.getColor("Table.alternateRowColor"));
            nameLabel.setForeground(UIManager.getColor("Label.foreground"));
        }

        return this;
    }

    private String getSignalText(int strength) {
        return switch (strength) {
            case 5 -> "Отличный";
            case 4 -> "Хороший";
            case 3 -> "Средний";
            case 2 -> "Слабый";
            default -> "Очень слабый";
        };
    }

    private Color getSignalColor(int strength) {
        return switch (strength) {
            case 5 -> new Color(52, 199, 89);
            case 4 -> new Color(48, 176, 199);
            case 3 -> new Color(255, 149, 0);
            case 2 -> new Color(255, 59, 48);
            default -> new Color(142, 142, 147);
        };
    }
}