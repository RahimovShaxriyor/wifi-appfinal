package org.example;

import javax.swing.*;
import javax.swing.border.*;
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
    private JCheckBox showPasswordsCheckbox = new JCheckBox("Показывать пароли");
    private JCheckBox autoConnectCheckbox = new JCheckBox("Автоподключение");
    private JProgressBar progressBar = new JProgressBar();
    private JLabel statusLabel = new JLabel("Готов к работе");
    private JLabel signalStrengthLabel = new JLabel("💡 Выберите сеть для информации");
    private JLabel connectionStatusLabel = new JLabel("🔴 Не подключено");
    private JComboBox<String> filterComboBox = new JComboBox<>(new String[]{"Все сети", "Сильные сигналы", "Открытые сети", "Защищенные сети"});

    private WifiScanner wifiScanner;
    private WifiConnector wifiConnector;
    private PasswordBruteForcer bruteForcer;

    // Профессиональная цветовая схема
    private final Color PRIMARY_COLOR = new Color(0, 123, 255);
    private final Color SUCCESS_COLOR = new Color(40, 167, 69);
    private final Color WARNING_COLOR = new Color(255, 193, 7);
    private final Color ERROR_COLOR = new Color(220, 53, 69);
    private final Color BACKGROUND_COLOR = new Color(248, 249, 250);
    private final Color CARD_COLOR = Color.WHITE;
    private final Color TEXT_PRIMARY = new Color(33, 37, 41);
    private final Color TEXT_SECONDARY = new Color(108, 117, 125);
    private final Color BORDER_COLOR = new Color(222, 226, 230);

    // Тени
    private final DropShadowBorder shadowBorder = new DropShadowBorder();

    public WifiConnectorGuiList() {
        super("Wi-Fi Connector Pro");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Инициализация компонентов
        wifiScanner = new WifiScanner(this::appendLog);
        wifiConnector = new WifiConnector(this::appendLog);
        bruteForcer = new PasswordBruteForcer(wifiConnector, this::appendLog);

        initUI();
        pack();
        setLocationRelativeTo(null);
        addListeners();
        scanNetworks();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(BACKGROUND_COLOR);

        // Главный контейнер
        JPanel mainContainer = new JPanel(new BorderLayout(25, 25));
        mainContainer.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        mainContainer.setBackground(BACKGROUND_COLOR);

        // === HEADER ===
        JPanel headerPanel = createCardPanel();
        headerPanel.setLayout(new BorderLayout(20, 20));

        // Левый блок - заголовок и статус
        JPanel titlePanel = new JPanel(new BorderLayout(10, 5));
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel("🌐 Wi-Fi Connector Pro");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(PRIMARY_COLOR);

        JLabel subtitleLabel = new JLabel("Управляй Wi-Fi соединениями легко и эффективно");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitleLabel.setForeground(TEXT_SECONDARY);

        titlePanel.add(titleLabel, BorderLayout.NORTH);
        titlePanel.add(subtitleLabel, BorderLayout.CENTER);

        // Правый блок - статус подключения и управление
        JPanel headerRightPanel = new JPanel(new BorderLayout(15, 0));
        headerRightPanel.setOpaque(false);

        // Статус подключения
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        statusPanel.setOpaque(false);
        connectionStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        connectionStatusLabel.setForeground(ERROR_COLOR);
        statusPanel.add(connectionStatusLabel);

        // Фильтр
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        filterPanel.setOpaque(false);
        JLabel filterLabel = new JLabel("Фильтр:");
        filterLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        filterLabel.setForeground(TEXT_SECONDARY);
        filterPanel.add(filterLabel);
        setupFilterComboBox(filterComboBox);
        filterPanel.add(filterComboBox);

        headerRightPanel.add(statusPanel, BorderLayout.NORTH);
        headerRightPanel.add(filterPanel, BorderLayout.SOUTH);

        headerPanel.add(titlePanel, BorderLayout.WEST);
        headerPanel.add(headerRightPanel, BorderLayout.EAST);

        // === CENTER CONTENT ===
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 25, 0));
        centerPanel.setOpaque(false);

        // Левая панель - список сетей
        JPanel networkPanel = createCardPanel();
        networkPanel.setLayout(new BorderLayout(20, 20));

        // Заголовок сети
        JPanel networkHeader = new JPanel(new BorderLayout());
        networkHeader.setOpaque(false);
        JLabel networkTitle = createSectionLabel("📶 Доступные сети Wi-Fi");
        JLabel networkCount = new JLabel("0 сетей");
        networkCount.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        networkCount.setForeground(TEXT_SECONDARY);

        networkHeader.add(networkTitle, BorderLayout.WEST);
        networkHeader.add(networkCount, BorderLayout.EAST);
        networkPanel.add(networkHeader, BorderLayout.NORTH);

        // Список сетей с улучшенным рендерером
        ssidList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        ssidList.setBackground(CARD_COLOR);
        ssidList.setSelectionBackground(PRIMARY_COLOR);
        ssidList.setSelectionForeground(Color.WHITE);
        ssidList.setCellRenderer(new ModernNetworkListRenderer());

        JScrollPane listScroll = new JScrollPane(ssidList);
        listScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1, true));
        listScroll.getVerticalScrollBar().setUnitIncrement(16);
        networkPanel.add(listScroll, BorderLayout.CENTER);

        // Панель информации о сигнале
        JPanel signalPanel = createInfoPanel();
        signalStrengthLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        signalPanel.add(signalStrengthLabel);
        networkPanel.add(signalPanel, BorderLayout.SOUTH);

        // Правая панель - управление
        JPanel controlPanel = createCardPanel();
        controlPanel.setLayout(new BorderLayout(20, 20));
        controlPanel.add(createSectionLabel("⚙️ Управление подключением"), BorderLayout.NORTH);

        JPanel controlContent = new JPanel();
        controlContent.setLayout(new BoxLayout(controlContent, BoxLayout.Y_AXIS));
        controlContent.setOpaque(false);
        controlContent.setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));

        // Поле пароля с улучшенным дизайном
        JPanel passwordPanel = new JPanel(new BorderLayout(12, 12));
        passwordPanel.setOpaque(false);
        passwordPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        JLabel passwordLabel = createFieldLabel("🔑 Пароль сети");
        passwordPanel.add(passwordLabel, BorderLayout.NORTH);

        JPanel passwordFieldPanel = new JPanel(new BorderLayout(8, 0));
        passwordFieldPanel.setOpaque(false);

        passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        passwordField.setBorder(createModernInputBorder());
        passwordField.setPreferredSize(new Dimension(200, 48));
        passwordField.putClientProperty("JTextField.placeholderText", "Введите пароль для выбранной сети...");

        // Кнопка показа пароля
        JButton togglePasswordButton = new JButton("👁");
        togglePasswordButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        togglePasswordButton.setPreferredSize(new Dimension(40, 48));
        togglePasswordButton.setBorder(BorderFactory.createEmptyBorder());
        togglePasswordButton.setBackground(new Color(245, 245, 245));
        togglePasswordButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        passwordFieldPanel.add(passwordField, BorderLayout.CENTER);
        passwordFieldPanel.add(togglePasswordButton, BorderLayout.EAST);
        passwordPanel.add(passwordFieldPanel, BorderLayout.CENTER);

        // Панель опций
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setOpaque(false);
        optionsPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));

        setupModernCheckbox(showPasswordsCheckbox, "Показывать вводимые пароли");
        setupModernCheckbox(autoConnectCheckbox, "Автоматически подключаться при найденном пароле");

        optionsPanel.add(showPasswordsCheckbox);
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        optionsPanel.add(autoConnectCheckbox);

        // Панель кнопок действий
        JPanel actionButtonsPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        actionButtonsPanel.setOpaque(false);
        actionButtonsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        styleModernButton(refreshButton, PRIMARY_COLOR);
        styleModernButton(connectButton, SUCCESS_COLOR);
        styleModernButton(disconnectButton, WARNING_COLOR);
        styleModernButton(bruteForceButton, new Color(151, 92, 228)); // Фиолетовый

        actionButtonsPanel.add(refreshButton);
        actionButtonsPanel.add(connectButton);
        actionButtonsPanel.add(disconnectButton);
        actionButtonsPanel.add(bruteForceButton);

        controlContent.add(passwordPanel);
        controlContent.add(optionsPanel);
        controlContent.add(actionButtonsPanel);
        controlPanel.add(controlContent, BorderLayout.CENTER);

        centerPanel.add(networkPanel);
        centerPanel.add(controlPanel);

        // === BOTTOM PANEL ===
        JPanel bottomPanel = new JPanel(new BorderLayout(20, 20));
        bottomPanel.setOpaque(false);

        // Панель прогресса и статуса
        JPanel progressPanel = createCardPanel();
        progressPanel.setLayout(new BorderLayout(15, 15));

        progressBar.setVisible(false);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        progressBar.setForeground(PRIMARY_COLOR);
        progressBar.setBackground(new Color(243, 244, 246));
        progressBar.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setForeground(TEXT_PRIMARY);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(statusLabel, BorderLayout.SOUTH);

        // Панель логов
        JPanel logPanel = createCardPanel();
        logPanel.setLayout(new BorderLayout());
        logPanel.add(createSectionLabel("📝 Журнал событий"), BorderLayout.NORTH);

        logArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        logArea.setBackground(new Color(250, 250, 250));
        logArea.setForeground(TEXT_PRIMARY);
        logArea.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1, true));
        logScroll.getVerticalScrollBar().setUnitIncrement(16);

        // Панель управления логами
        JPanel logControls = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        logControls.setOpaque(false);
        JButton clearLogButton = new JButton("Очистить");
        styleModernButton(clearLogButton, TEXT_SECONDARY);
        clearLogButton.setPreferredSize(new Dimension(100, 32));
        logControls.add(clearLogButton);

        logPanel.add(logScroll, BorderLayout.CENTER);
        logPanel.add(logControls, BorderLayout.SOUTH);

        bottomPanel.add(progressPanel, BorderLayout.NORTH);
        bottomPanel.add(logPanel, BorderLayout.CENTER);

        // Сборка интерфейса
        mainContainer.add(headerPanel, BorderLayout.NORTH);
        mainContainer.add(centerPanel, BorderLayout.CENTER);
        mainContainer.add(bottomPanel, BorderLayout.SOUTH);

        add(mainContainer);

        // Установка иконки и размеров
        setIconImage(createModernAppIcon());
        setMinimumSize(new Dimension(1000, 800));
        setPreferredSize(new Dimension(1200, 900));

        // Обработчик для кнопки показа пароля
        togglePasswordButton.addActionListener(e -> {
            if (passwordField.getEchoChar() == 0) {
                passwordField.setEchoChar('•');
                togglePasswordButton.setText("👁");
            } else {
                passwordField.setEchoChar((char) 0);
                togglePasswordButton.setText("🙈");
            }
        });

        // Обработчик для кнопки очистки логов
        clearLogButton.addActionListener(e -> logArea.setText(""));
    }

    private JPanel createCardPanel() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Рисуем тень
                g2.setColor(new Color(0, 0, 0, 15));
                g2.fillRoundRect(3, 3, getWidth()-6, getHeight()-6, 20, 20);

                // Рисуем основную панель
                g2.setColor(CARD_COLOR);
                g2.fillRoundRect(0, 0, getWidth()-6, getHeight()-6, 20, 20);

                // Тонкая граница
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth()-7, getHeight()-7, 20, 20);

                g2.dispose();
            }
        };
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        panel.setOpaque(false);
        return panel;
    }

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 12));
        panel.setBackground(new Color(248, 249, 250));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));
        return panel;
    }

    private JLabel createSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 18));
        label.setForeground(TEXT_PRIMARY);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        return label;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 14));
        label.setForeground(TEXT_SECONDARY);
        return label;
    }

    private Border createModernInputBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)
        );
    }

    private void setupFilterComboBox(JComboBox<String> comboBox) {
        comboBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        comboBox.setBackground(Color.WHITE);
        comboBox.setBorder(createModernInputBorder());
        comboBox.setPreferredSize(new Dimension(160, 42));
    }

    private void setupModernCheckbox(JCheckBox checkbox, String text) {
        checkbox.setText(text);
        checkbox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        checkbox.setForeground(TEXT_SECONDARY);
        checkbox.setOpaque(false);
        checkbox.setFocusPainted(false);
        checkbox.setIcon(new ModernCheckboxIcon());
    }

    private void styleModernButton(JButton button, Color color) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));

        // Плавные анимации при наведении
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                button.setBackground(lightenColor(color, 20));
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(darkenColor(color, 10), 2),
                        BorderFactory.createEmptyBorder(12, 18, 12, 18)
                ));
            }
            public void mouseExited(MouseEvent e) {
                button.setBackground(color);
                button.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));
            }
            public void mousePressed(MouseEvent e) {
                button.setBackground(darkenColor(color, 15));
            }
        });
    }

    private Color lightenColor(Color color, int amount) {
        int red = Math.min(255, color.getRed() + amount);
        int green = Math.min(255, color.getGreen() + amount);
        int blue = Math.min(255, color.getBlue() + amount);
        return new Color(red, green, blue);
    }

    private Color darkenColor(Color color, int amount) {
        int red = Math.max(0, color.getRed() - amount);
        int green = Math.max(0, color.getGreen() - amount);
        int blue = Math.max(0, color.getBlue() - amount);
        return new Color(red, green, blue);
    }

    private Image createModernAppIcon() {
        int size = 64;
        BufferedImage icon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = icon.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Градиентный фон
        GradientPaint gradient = new GradientPaint(0, 0, PRIMARY_COLOR, size, size, new Color(13, 110, 253));
        g2.setPaint(gradient);
        g2.fillRoundRect(0, 0, size, size, 16, 16);

        // Иконка Wi-Fi
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2.8f));
        int centerX = size / 2;
        int centerY = size / 2;

        // Рисуем сигналы Wi-Fi с градиентом прозрачности
        for (int i = 0; i < 4; i++) {
            int radius = 6 + i * 6;
            float alpha = 1.0f - (i * 0.25f);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.drawArc(centerX - radius, centerY - radius, radius * 2, radius * 2, -50, 100);
        }

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        // Точка в центре
        g2.fillOval(centerX - 3, centerY - 3, 6, 6);

        // Добавляем легкое свечение
        g2.setColor(new Color(255, 255, 255, 50));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(1, 1, size-2, size-2, 16, 16);

        g2.dispose();
        return icon;
    }

    // Кастомный рендерер для списка сетей
    private class ModernNetworkListRenderer extends JPanel implements ListCellRenderer<String> {
        private JLabel nameLabel = new JLabel();
        private JLabel signalLabel = new JLabel();
        private JLabel securityLabel = new JLabel();

        public ModernNetworkListRenderer() {
            setLayout(new BorderLayout(10, 5));
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

            // Настройка компонентов
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            signalLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            securityLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));

            JPanel infoPanel = new JPanel(new BorderLayout());
            infoPanel.setOpaque(false);
            infoPanel.add(nameLabel, BorderLayout.NORTH);

            JPanel detailsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            detailsPanel.setOpaque(false);
            detailsPanel.add(signalLabel);
            detailsPanel.add(securityLabel);

            infoPanel.add(detailsPanel, BorderLayout.SOUTH);
            add(infoPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(value);

            // Случайные характеристики для демонстрации
            int signalStrength = (int)(Math.random() * 5) + 1;
            boolean isSecure = Math.random() > 0.3;

            signalLabel.setText("📶 " + getSignalText(signalStrength));
            signalLabel.setForeground(getSignalColor(signalStrength));
            securityLabel.setText(isSecure ? "🔒 WPA2" : "🔓 Открытая");
            securityLabel.setForeground(isSecure ? SUCCESS_COLOR : WARNING_COLOR);

            if (isSelected) {
                setBackground(PRIMARY_COLOR);
                nameLabel.setForeground(Color.WHITE);
            } else {
                setBackground(index % 2 == 0 ? new Color(252, 252, 252) : Color.WHITE);
                nameLabel.setForeground(TEXT_PRIMARY);
            }

            return this;
        }

        private String getSignalText(int strength) {
            switch (strength) {
                case 5: return "Отличный";
                case 4: return "Хороший";
                case 3: return "Средний";
                case 2: return "Слабый";
                default: return "Очень слабый";
            }
        }

        private Color getSignalColor(int strength) {
            switch (strength) {
                case 5: return SUCCESS_COLOR;
                case 4: return new Color(40, 167, 69);
                case 3: return WARNING_COLOR;
                case 2: return new Color(253, 126, 20);
                default: return ERROR_COLOR;
            }
        }
    }

    // Кастомная иконка для чекбоксов
    private class ModernCheckboxIcon implements Icon {
        private final int SIZE = 18;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            JCheckBox cb = (JCheckBox) c;
            boolean selected = cb.isSelected();

            // Фон
            g2.setColor(selected ? PRIMARY_COLOR : new Color(233, 236, 239));
            g2.fillRoundRect(x, y, SIZE, SIZE, 4, 4);

            // Граница
            g2.setColor(selected ? PRIMARY_COLOR : BORDER_COLOR);
            g2.drawRoundRect(x, y, SIZE, SIZE, 4, 4);

            // Галочка
            if (selected) {
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(x + 4, y + 9, x + 7, y + 12);
                g2.drawLine(x + 7, y + 12, x + 14, y + 5);
            }

            g2.dispose();
        }

        @Override
        public int getIconWidth() { return SIZE; }

        @Override
        public int getIconHeight() { return SIZE; }
    }

    private void addListeners() {
        refreshButton.addActionListener(e -> scanNetworks());

        connectButton.addActionListener(e -> {
            String ssid = ssidList.getSelectedValue();
            if (ssid == null || ssid.isEmpty()) {
                showMessage("Выберите сеть из списка", "Внимание", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String password = new String(passwordField.getPassword());
            connectToNetwork(ssid, password);
        });

        disconnectButton.addActionListener(e -> {
            disconnectFromNetwork();
        });

        bruteForceButton.addActionListener(e -> {
            String ssid = ssidList.getSelectedValue();
            if (ssid == null || ssid.isEmpty()) {
                showMessage("Выберите сеть из списка", "Внимание", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (bruteForcer.isRunning()) {
                stopBruteForce();
            } else {
                startBruteForce(ssid);
            }
        });

        ssidList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = ssidList.getSelectedValue();
                if (selected != null) {
                    statusLabel.setText("Выбрана сеть: " + selected);
                    updateNetworkInfo(selected);
                }
            }
        });

        // Двойной клик для быстрого подключения
        ssidList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String ssid = ssidList.getSelectedValue();
                    if (ssid != null) {
                        passwordField.requestFocusInWindow();
                        appendLog("🔄 Двойной клик по сети: " + ssid + " - готов к вводу пароля");
                    }
                }
            }
        });

        // Фильтрация сетей
        filterComboBox.addActionListener(e -> {
            String filter = (String) filterComboBox.getSelectedItem();
            appendLog("🔍 Применен фильтр: " + filter);
            applyNetworkFilter(filter);
        });
    }

    private void updateNetworkInfo(String ssid) {
        // Обновляем информацию о выбранной сети
        int signalStrength = (int)(Math.random() * 5) + 1;
        boolean isSecure = Math.random() > 0.3;

        String signalText = "";
        switch (signalStrength) {
            case 5: signalText = "📶 Отличный сигнал"; break;
            case 4: signalText = "📶 Хороший сигнал"; break;
            case 3: signalText = "📶 Средний сигнал"; break;
            case 2: signalText = "📶 Слабый сигнал"; break;
            default: signalText = "📶 Очень слабый сигнал";
        }

        String securityText = isSecure ? "🔒 Защита: WPA2" : "🔓 Открытая сеть";
        signalStrengthLabel.setText(signalText + " • " + securityText);
    }

    private void applyNetworkFilter(String filter) {
        // Здесь будет логика фильтрации сетей
        appendLog("⚡ Фильтр '" + filter + "' применен к списку сетей");
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
                    showMessage("Не удалось найти Wi-Fi сети. Проверьте адаптер Wi-Fi.", "Информация", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    appendLog("✅ Обнаружено сетей: " + networks.size());
                    statusLabel.setText("Найдено " + networks.size() + " сетей");
                    for (String network : networks) {
                        listModel.addElement(network);
                    }
                    // Автовыбор первой сети
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
                    connectionStatusLabel.setForeground(SUCCESS_COLOR);
                    showMessage("<html><b>Успешное подключение!</b><br>Сеть: " + ssid + "</html>",
                            "Подключение установлено", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    appendLog("❌ Ошибка подключения к " + ssid);
                    statusLabel.setText("Ошибка подключения");
                    showMessage("<html><b>Не удалось подключиться</b><br>Сеть: " + ssid + "<br>Проверьте пароль и повторите попытку</html>",
                            "Ошибка подключения", JOptionPane.ERROR_MESSAGE);
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

        // Имитация отключения
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                SwingUtilities.invokeLater(() -> {
                    appendLog("🔌 Отключено от текущей сети");
                    statusLabel.setText("Не подключено");
                    connectionStatusLabel.setText("🔴 Не подключено");
                    connectionStatusLabel.setForeground(ERROR_COLOR);
                    progressBar.setVisible(false);
                    setControlsEnabled(true);
                    showMessage("Успешно отключено от сети", "Отключение", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void startBruteForce(String ssid) {
        boolean showPasswords = showPasswordsCheckbox.isSelected();
        bruteForceButton.setText("⏹️ Остановить");
        bruteForceButton.setBackground(ERROR_COLOR);
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
                                "<html><b>Пароль найден!</b><br>" +
                                        "Сеть: <b>" + ssid + "</b><br>" +
                                        "Пароль: <b>" + foundPassword + "</b><br><br>" +
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
                    showMessage("Не удалось подобрать пароль для сети " + ssid + " за отведенное время",
                            "Подбор завершен", JOptionPane.INFORMATION_MESSAGE);
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
            String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));

            // Цветовое кодирование сообщений
            if (message.contains("❌") || message.contains("Ошибка") || message.contains("остановлен")) {
                logArea.append("<font color='#dc3545'>[" + timestamp + "] " + message + "</font>" + System.lineSeparator());
            } else if (message.contains("✅") || message.contains("Успех") || message.contains("НАЙДЕН")) {
                logArea.append("<font color='#28a745'>[" + timestamp + "] " + message + "</font>" + System.lineSeparator());
            } else if (message.contains("⚠️") || message.contains("Внимание")) {
                logArea.append("<font color='#ffc107'>[" + timestamp + "] " + message + "</font>" + System.lineSeparator());
            } else if (message.contains("🔍") || message.contains("Фильтр")) {
                logArea.append("<font color='#6c757d'>[" + timestamp + "] " + message + "</font>" + System.lineSeparator());
            } else {
                logArea.append("[" + timestamp + "] " + message + System.lineSeparator());
            }

            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void showMessage(String message, String title, int messageType) {
        JOptionPane.showMessageDialog(this,
                "<html><body style='width: 300px; padding: 10px;'>" + message + "</body></html>",
                title, messageType);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Установка современного Look and Feel
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

                // Кастомные настройки UI для профессионального вида
                UIManager.put("Button.arc", 12);
                UIManager.put("Component.arc", 12);
                UIManager.put("TextComponent.arc", 8);
                UIManager.put("ScrollBar.width", 14);
                UIManager.put("ScrollBar.thumbArc", 999);
                UIManager.put("ScrollBar.thumbInsets", new Insets(3, 3, 3, 3));
                UIManager.put("ScrollBar.track", new Color(248, 249, 250));
                UIManager.put("ScrollBar.thumb", new Color(206, 212, 218));
                UIManager.put("ScrollBar.thumbHover", new Color(173, 181, 189));

            } catch (Exception e) {
                e.printStackTrace();
            }

            WifiConnectorGuiList app = new WifiConnectorGuiList();
            app.setVisible(true);
        });
    }
}

// Класс для создания теней
class DropShadowBorder extends AbstractBorder {
    private static final int SHADOW_SIZE = 6;

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Рисуем тень
        for (int i = 0; i < SHADOW_SIZE; i++) {
            float alpha = (SHADOW_SIZE - i) * 0.03f;
            g2.setColor(new Color(0, 0, 0, alpha));
            g2.drawRoundRect(x + i, y + i, width - 1 - i * 2, height - 1 - i * 2, 20, 20);
        }

        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(SHADOW_SIZE, SHADOW_SIZE, SHADOW_SIZE, SHADOW_SIZE);
    }
}