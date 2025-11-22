package org.example;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import net.miginfocom.swing.MigLayout;
import org.pushingpixels.radiance.animation.api.Timeline;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.Preferences;

public class WifiConnectorGuiList extends JFrame {
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages");
    private static final Preferences PREFS = Preferences.userNodeForPackage(WifiConnectorGuiList.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // === UI COMPONENTS ===
    private DefaultListModel<WifiNetwork> listModel = new DefaultListModel<>();
    private JList<WifiNetwork> networkList = new JList<>(listModel);
    private JPasswordField passwordField = new JPasswordField(20);
    private JTextArea logArea = new JTextArea(12, 50);
    private ModernProgressBar progressBar = new ModernProgressBar();
    private JLabel statusLabel = new JLabel(BUNDLE.getString("ready"));
    private JLabel connectionStatusLabel = new JLabel(BUNDLE.getString("notConnected"));
    private JLabel signalInfoLabel = new JLabel(BUNDLE.getString("selectNetwork"));
    private JLabel attemptsLabel = new JLabel("");
    private JComboBox<String> filterCombo = new JComboBox<>(new String[]{
            BUNDLE.getString("allNetworks"),
            BUNDLE.getString("strongSignals"),
            BUNDLE.getString("openNetworks"),
            BUNDLE.getString("securedNetworks")
    });
    private JComboBox<String> logFilterCombo = new JComboBox<>(new String[]{
            BUNDLE.getString("allLogs"), BUNDLE.getString("errorsOnly"),
            BUNDLE.getString("successOnly"), BUNDLE.getString("warningsOnly")
    });

    private JButton refreshBtn = new JButton(BUNDLE.getString("refresh"));
    private JButton connectBtn = new JButton(BUNDLE.getString("connect"));
    private JButton disconnectBtn = new JButton(BUNDLE.getString("disconnect"));
    private JButton bruteBtn = new JButton(BUNDLE.getString("bruteforce"));
    private JButton themeBtn = new JButton("");
    private JButton settingsBtn = new JButton("");
    private JButton exportBtn = new JButton("");
    private JCheckBox showPassChk = new JCheckBox(BUNDLE.getString("showPasswords"));
    private JCheckBox autoConnChk = new JCheckBox(BUNDLE.getString("autoConnect"));

    // === BUSINESS LOGIC ===
    private WifiScanner scanner;
    private WifiConnector connector;
    private PasswordBruteForcer bruteForcer;

    // === STATE ===
    private boolean darkTheme = true;
    private Timer autoRefresh;
    private int scanInterval = 30;
    private boolean autoRefreshEnabled = true;
    private List<WifiNetwork> lastScan = new ArrayList<>();

    public WifiConnectorGuiList() {
        super(BUNDLE.getString("appTitle"));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        loadPrefs();
        initComponents();
        setupUI();
        setupLogic();
        pack();
        setSize(1200, 900);
        setLocationRelativeTo(null);
        setVisible(true);
        restartAutoRefreshTimer(); // вместо startAutoRefresh()
        scanNetworks();
    }

    private void initComponents() {
        scanner = new WifiScanner(this::log);
        connector = new WifiConnector(this::log);
        bruteForcer = new PasswordBruteForcer(connector, this::log);
    }

    private void setupUI() {
        FlatRobotoFont.install();
        setLayout(new MigLayout("fill, insets 15", "[grow]", "[][grow][]"));
        getContentPane().setBackground(darkTheme ? new Color(18, 18, 18) : new Color(245, 245, 245));

        add(createHeader(), "wrap");
        add(createMainPanel(), "grow, wrap");
        add(createLogPanel(), "grow");

        setIconImage(createIcon());
        applyTheme();
    }

    // === ОСТА • НЕ ТРОГАЙ НИЖЕ — РАБОТАЕТ ===

    private JPanel createHeader() {
        JPanel p = new ModernCardPanel();
        p.setLayout(new MigLayout("fill, insets 20", "[grow][]", "[]"));

        JLabel title = new JLabel("Wi-Fi Connector Pro");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(darkTheme ? Color.WHITE : Color.BLACK);

        JPanel right = new JPanel(new MigLayout("insets 0", "[][]10[]10[]", "[]"));
        right.setOpaque(false);
        connectionStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        right.add(connectionStatusLabel);
        right.add(new JLabel(BUNDLE.getString("filter") + ":"));
        setupCombo(filterCombo);
        right.add(filterCombo);
        settingsBtn.setIcon(new ImageIcon(createIcon(24, "settings")));
        themeBtn.setIcon(new ImageIcon(createIcon(24, darkTheme ? "moon" : "sun")));
        styleIconBtn(settingsBtn); styleIconBtn(themeBtn);
        right.add(settingsBtn); right.add(themeBtn);

        p.add(title, "grow");
        p.add(right);
        return p;
    }

    private JPanel createMainPanel() {
        JPanel p = new JPanel(new MigLayout("fill", "[450!][grow]", "[grow]"));
        p.setOpaque(false);
        p.add(createNetworkPanel(), "grow");
        p.add(createControlPanel(), "grow");
        return p;
    }

    private JPanel createNetworkPanel() {
        JPanel p = new ModernCardPanel();
        p.setLayout(new MigLayout("fill, insets 20", "[grow]", "[]8[grow]8[]"));

        p.add(new ModernSectionLabel("availableNetworks"), "wrap");
        networkList.setCellRenderer(new NetworkRenderer());
        networkList.setFixedCellHeight(68);
        JScrollPane scroll = new ModernScrollPane(networkList);
        p.add(scroll, "grow, wrap");

        signalInfoLabel.setForeground(darkTheme ? new Color(173, 181, 189) : new Color(100, 100, 100));
        JPanel info = new JPanel(new MigLayout("insets 10"));
        info.setBackground(darkTheme ? new Color(33, 37, 43) : new Color(230, 230, 230));
        info.setBorder(BorderFactory.createLineBorder(darkTheme ? new Color(52, 58, 70) : new Color(200, 200, 200)));
        info.add(signalInfoLabel);
        p.add(info, "grow");

        return p;
    }

    private JPanel createControlPanel() {
        JPanel p = new ModernCardPanel();
        p.setLayout(new MigLayout("fill, insets 20", "[grow]", "[]20[grow]"));

        p.add(new ModernSectionLabel("connectionManagement"), "wrap");

        JPanel content = new JPanel(new MigLayout("fill", "[grow]", "[]10[]10[]20[grow]"));
        content.setOpaque(false);

        // Password
        JPanel passPanel = new JPanel(new MigLayout("fill", "[grow][]", "[]"));
        passPanel.setOpaque(false);
        passPanel.add(new ModernFieldLabel("networkPassword"), "wrap");
        passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        passwordField.putClientProperty("JTextField.placeholderText", BUNDLE.getString("enterPassword"));
        passPanel.add(passwordField, "grow");
        JButton eye = new JButton("show");
        styleIconBtn(eye);
        eye.addActionListener(e -> togglePassword(eye));
        passPanel.add(eye);

        // Options
        JPanel opts = new JPanel(new MigLayout("gapy 8"));
        opts.setOpaque(false);
        setupCheckbox(showPassChk); setupCheckbox(autoConnChk);
        opts.add(showPassChk, "wrap"); opts.add(autoConnChk);

        // Buttons
        JPanel btns = new JPanel(new MigLayout("fill", "[grow][grow]", "[grow][grow]"));
        btns.setOpaque(false);
        stylePrimaryBtn(refreshBtn, new Color(0, 122, 255));
        stylePrimaryBtn(connectBtn, new Color(52, 199, 89));
        styleSecondaryBtn(disconnectBtn, new Color(255, 149, 0));
        styleSecondaryBtn(bruteBtn, new Color(151, 92, 228));
        btns.add(refreshBtn, "grow"); btns.add(connectBtn, "grow, wrap");
        btns.add(disconnectBtn, "grow"); btns.add(bruteBtn, "grow");

        // Progress
        JPanel prog = new JPanel(new MigLayout("fill", "[grow]", "[grow][]"));
        prog.setOpaque(false);
        progressBar.setVisible(false);
        prog.add(progressBar, "grow, wrap");
        JPanel statusP = new JPanel(new MigLayout("fill", "[grow][]"));
        statusP.setOpaque(false);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusP.add(statusLabel, "grow");
        statusP.add(attemptsLabel);
        prog.add(statusP, "grow");

        content.add(passPanel, "grow, wrap");
        content.add(opts, "grow, wrap");
        content.add(btns, "grow, wrap");
        content.add(prog, "grow");
        p.add(content, "grow");
        return p;
    }

    private JPanel createLogPanel() {
        JPanel p = new ModernCardPanel();
        p.setLayout(new MigLayout("fill, insets 20", "[grow]", "[]8[grow]8[]"));

        // === Заголовок с фильтром ===
        JPanel header = new JPanel(new MigLayout("fill", "[grow][]"));
        header.setOpaque(false);
        header.add(new ModernSectionLabel("eventLog"), "grow");
        header.add(new JLabel(BUNDLE.getString("logFilter") + ":"));
        setupCombo(logFilterCombo);
        logFilterCombo.addActionListener(e -> filterLogs()); // ← Добавлено!
        header.add(logFilterCombo);

        p.add(header, "wrap");

        // === Область логов ===
        logArea.setEditable(false);
        logArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        logArea.setBackground(darkTheme ? new Color(13, 17, 23) : Color.WHITE);
        logArea.setForeground(darkTheme ? new Color(248, 249, 250) : Color.BLACK);
        JScrollPane scroll = new ModernScrollPane(logArea);
        p.add(scroll, "grow, wrap");

        // === Кнопки: Экспорт + Очистка ===
        JPanel controls = new JPanel(new MigLayout("right"));
        controls.setOpaque(false);

        // Экспорт — используем эмодзи "download"
        exportBtn.setIcon(new ImageIcon(createIcon(20, "download")));
        styleIconBtn(exportBtn);
        exportBtn.addActionListener(e -> exportLog());

        JButton clear = new JButton(BUNDLE.getString("clear"));
        styleSecondaryBtn(clear, new Color(108, 117, 125));
        clear.addActionListener(e -> clearLog());

        controls.add(exportBtn);
        controls.add(clear);
        p.add(controls, "grow");

        return p;
    }

    private void clearLog() {
        logArea.setText("");
        log("logCleared");
    }

    // === LISTENERS ===
    private void setupLogic() {
        refreshBtn.addActionListener(e -> scanNetworks());
        connectBtn.addActionListener(e -> connectSelected());
        disconnectBtn.addActionListener(e -> disconnect());
        bruteBtn.addActionListener(e -> toggleBruteForce());
        themeBtn.addActionListener(e -> toggleTheme());
        settingsBtn.addActionListener(e -> showSettings());
        filterCombo.addActionListener(e -> applyFilter());
        logFilterCombo.addActionListener(e -> filterLogs());

        networkList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateInfo();
        });

        networkList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) passwordField.requestFocus();
            }
        });

        setupShortcuts();
    }

    private void scanNetworks() {
        setBusy(true, "scanningNetworks");
        scanner.scanNetworks(networks -> {
            lastScan = networks;
            SwingUtilities.invokeLater(() -> {
                listModel.clear();
                networks.forEach(listModel::addElement);
                setBusy(false, networks.isEmpty() ? "noNetworks" : "foundNetworks " + networks.size());
                if (!networks.isEmpty()) networkList.setSelectedIndex(0);
            });
        });
    }

    private void connectSelected() {
        WifiNetwork net = networkList.getSelectedValue();
        if (net == null) { toast("selectNetworkFirst", Color.RED); return; }
        String pass = new String(passwordField.getPassword());
        setBusy(true, "connectingTo " + net.getSsid());
        connector.connectToNetwork(net.getSsid(), pass, success -> {
            SwingUtilities.invokeLater(() -> {
                setBusy(false, success ? "connectedTo " + net.getSsid() : "connectionError");
                if (success) updateConnectionStatus(net.getSsid(), true);
            });
        });
    }

    private void disconnect() {
        connector.disconnectFromNetwork(success -> {
            SwingUtilities.invokeLater(() -> {
                setBusy(false, success ? "disconnected" : "disconnectError");
                updateConnectionStatus(null, false);
            });
        });
    }

    private void toggleBruteForce() {
        WifiNetwork net = networkList.getSelectedValue();
        if (net == null) { toast("selectNetworkFirst", Color.RED); return; }

        if (bruteForcer.isRunning()) {
            bruteForcer.stopBruteForce();
            bruteBtn.setText(BUNDLE.getString("bruteforce"));
            bruteBtn.setBackground(new Color(151, 92, 228));
            setBusy(false, "bruteForceStopped");
        } else {
            setBusy(true, "bruteForcing " + net.getSsid());
            bruteBtn.setText(BUNDLE.getString("stop"));
            bruteBtn.setBackground(new Color(255, 59, 48));
            bruteForcer.startBruteForce(net.getSsid(), net.getBssid(), password -> {
                SwingUtilities.invokeLater(() -> {
                    if (password != null) {
                        passwordField.setText(password);
                        log("passwordFound: " + (password.startsWith("WPS:") ? "WPS PIN" : password));
                        setBusy(false, "passwordFound");
                        if (autoConnChk.isSelected()) connectSelected();
                    } else {
                        setBusy(false, "passwordNotFound");
                    }
                    bruteBtn.setText(BUNDLE.getString("bruteforce"));
                    bruteBtn.setBackground(new Color(151, 92, 228));
                });
            });
        }
    }

    private void updateInfo() {
        WifiNetwork net = networkList.getSelectedValue();
        if (net == null) return;
        scanner.getDetailedInfo(net.getSsid(), info -> {
            SwingUtilities.invokeLater(() -> {
                String signal = switch (info.getSignalStrength() / 20) {
                    case 5 -> "excellentSignal";
                    case 4 -> "goodSignal";
                    case 3 -> "averageSignal";
                    case 2 -> "weakSignal";
                    default -> "veryWeakSignal";
                };
                String sec = info.getSecurity().equals("Open") ? "openNetwork" : "security" + info.getSecurity();
                signalInfoLabel.setText(BUNDLE.getString(signal) +
                        (info.is5G() ? " [5G]" : "") + " • " + BUNDLE.getString(sec));
            });
        });
    }

    private void applyFilter() {
        String filter = (String) filterCombo.getSelectedItem();
        List<WifiNetwork> filtered = scanner.filterNetworks(
                switch (filter) {
                    case "strongSignals" -> "strongSignals";
                    case "openNetworks" -> "openNetworks";
                    case "securedNetworks" -> "securedNetworks";
                    default -> "allNetworks";
                }
        );
        listModel.clear();
        filtered.forEach(listModel::addElement);
    }

    // === УПРАВЛЕНИЕ ТАЙМЕРОМ ===
    private void restartAutoRefreshTimer() {
        if (autoRefresh != null) {
            autoRefresh.cancel();
            autoRefresh.purge();
        }
        autoRefresh = new Timer("AutoRefresh", true);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (isVisible() && !progressBar.isVisible()) {
                    SwingUtilities.invokeLater(WifiConnectorGuiList.this::scanNetworks);
                }
            }
        };
        autoRefresh.scheduleAtFixedRate(task, scanInterval * 1000L, scanInterval * 1000L);
    }

    // === НАСТРОЙКИ ===
    private void showSettings() {
        JDialog d = new JDialog(this, BUNDLE.getString("settings"), true);
        d.setLayout(new MigLayout("fill, insets 20", "[grow]", "[][][][]"));
        JSpinner spin = new JSpinner(new SpinnerNumberModel(scanInterval, 5, 300, 5));
        JCheckBox auto = new JCheckBox(BUNDLE.getString("autoRefresh"), autoRefreshEnabled);
        d.add(new JLabel(BUNDLE.getString("scanInterval") + " (s):"), "wrap");
        d.add(spin, "wrap");
        d.add(auto, "wrap");
        JButton save = new JButton(BUNDLE.getString("save"));
        save.addActionListener(e -> {
            int newInterval = (int) spin.getValue();
            boolean newEnabled = auto.isSelected();

            if (newInterval != scanInterval || newEnabled != autoRefreshEnabled) {
                scanInterval = newInterval;
                autoRefreshEnabled = newEnabled;
                PREFS.putInt("scanInterval", scanInterval);
                PREFS.putBoolean("autoRefreshEnabled", autoRefreshEnabled);
                restartAutoRefreshTimer();
                log("Settings updated: interval=" + scanInterval + "s, autoRefresh=" + autoRefreshEnabled);
            }
            d.dispose();
        });
        d.add(save, "tag ok, span, right");
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    // === UTILS ===
    private void setBusy(boolean busy, String msgKey) {
        progressBar.setVisible(busy);
        progressBar.setIndeterminate(busy);
        statusLabel.setText(busy ? BUNDLE.getString(msgKey) : BUNDLE.getString("ready"));
        boolean enabled = !busy;
        refreshBtn.setEnabled(enabled);
        connectBtn.setEnabled(enabled && networkList.getSelectedValue() != null);
        disconnectBtn.setEnabled(enabled);
        networkList.setEnabled(enabled);
        passwordField.setEnabled(enabled);
    }

    private void updateConnectionStatus(String ssid, boolean connected) {
        connectionStatusLabel.setText(connected ? BUNDLE.getString("connectedTo") + " " + ssid : BUNDLE.getString("notConnected"));
        connectionStatusLabel.setForeground(connected ? new Color(52, 199, 89) : new Color(255, 59, 48));
    }

    private void togglePassword(JButton btn) {
        char echo = passwordField.getEchoChar();
        passwordField.setEchoChar(echo == 0 ? '•' : 0);
        btn.setText(echo == 0 ? "show" : "hide");
    }

    private void toggleTheme() {
        FlatAnimatedLafChange.showSnapshot();
        darkTheme = !darkTheme;
        if (darkTheme) FlatDarkLaf.setup(); else FlatLightLaf.setup();
        PREFS.putBoolean("darkTheme", darkTheme);
        themeBtn.setIcon(new ImageIcon(createIcon(24, darkTheme ? "moon" : "sun")));
        SwingUtilities.updateComponentTreeUI(this);
        FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }


    private void setupShortcuts() {
        InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getRootPane().getActionMap();

        im.put(KeyStroke.getKeyStroke("ctrl R"), "refresh");
        im.put(KeyStroke.getKeyStroke("ENTER"), "connect");
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");

        am.put("refresh", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                scanNetworks();
            }
        });

        am.put("connect", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                connectSelected();
            }
        });

        am.put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (bruteForcer.isRunning()) {
                    toggleBruteForce();
                }
            }
        });
    }

    private void toast(String key, Color bg) {
        JWindow w = new JWindow(this);
        JLabel l = new JLabel(BUNDLE.getString(key), SwingConstants.CENTER);
        l.setForeground(Color.WHITE);
        l.setFont(new Font("Segoe UI", Font.BOLD, 14));
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
            }
        };
        p.setLayout(new BorderLayout());
        p.add(l);
        w.setContentPane(p);
        w.setSize(320, 60);
        w.setLocationRelativeTo(this);
        w.setOpacity(0f);
        w.setVisible(true);
        Timeline.builder(w)
                .addPropertyToInterpolate("opacity", 0f, 1f)
                .setDuration(300).play();
        new Timer(2500, e -> {
            Timeline.builder(w).addPropertyToInterpolate("opacity", 1f, 0f).setDuration(300).play();
            new Timer(300, ee -> w.dispose()).start();
        }).start();
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + LocalTime.now().format(TIME_FMT) + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void exportLog() {
        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                java.nio.file.Files.writeString(fc.getSelectedFile().toPath(), logArea.getText());
                toast("logExported", new Color(52, 199, 89));
            } catch (Exception ex) {
                toast("exportError", Color.RED);
            }
        }
    }

    private void filterLogs() { /* Реализуй по аналогии */ }

    private void loadPrefs() {
        darkTheme = PREFS.getBoolean("darkTheme", true);
        scanInterval = PREFS.getInt("scanInterval", 30);
        autoRefreshEnabled = PREFS.getBoolean("autoRefreshEnabled", true);
    }

    @Override
    public void dispose() {
        PREFS.putBoolean("darkTheme", darkTheme);
        PREFS.putInt("scanInterval", scanInterval);
        PREFS.putBoolean("autoRefreshEnabled", autoRefreshEnabled);
        if (autoRefresh != null) {
            autoRefresh.cancel();
        }
        super.dispose();
    }

    private void applyTheme() {
        if (darkTheme) FlatDarkLaf.setup(); else FlatLightLaf.setup();
    }

    // === UI HELPERS ===
    private void setupCombo(JComboBox<?> c) {
        c.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        c.setPreferredSize(new Dimension(160, 36));
    }

    private void setupCheckbox(JCheckBox c) {
        c.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        c.setOpaque(false);
        c.setForeground(darkTheme ? Color.WHITE : Color.BLACK);
    }

    private void stylePrimaryBtn(JButton b, Color c) {
        b.setBackground(c);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(c.darker(), 2),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));
        addHover(b, c, c.brighter());
    }

    private void styleSecondaryBtn(JButton b, Color c) {
        b.setBackground(c);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(c.brighter()),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));
        addHover(b, c, c.brighter());
    }

    private void styleIconBtn(JButton b) {
        b.setBorder(new EmptyBorder(8, 8, 8, 8));
        b.setBackground(new Color(73, 73, 74));
        b.setForeground(Color.WHITE);
        addHover(b, new Color(73, 73, 74), new Color(99, 99, 102));
    }

    private void addHover(JButton b, Color from, Color to) {
        Timeline t = Timeline.builder(b)
                .addPropertyToInterpolate("background", from, to)
                .setDuration(200).build();
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { t.play(); }
            public void mouseExited(MouseEvent e) { t.playReverse(); }
        });
    }

    private Image createIcon() {
        int s = 64;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint gp = new GradientPaint(0, 0, new Color(0, 122, 255), s, s, new Color(13, 110, 253));
        g.setPaint(gp);
        g.fillRoundRect(0, 0, s, s, 16, 16);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3));
        int cx = s/2, cy = s/2;
        for (int i = 0; i < 4; i++) {
            int r = 8 + i*7;
            float a = 1f - i*0.25f;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g.drawArc(cx-r, cy-r, r*2, r*2, -45, 90);
        }
        g.setComposite(AlphaComposite.SrcOver);
        g.fillOval(cx-4, cy-4, 8, 8);
        g.dispose();
        return img;
    }

    private BufferedImage createIcon(int size, String emoji) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(new Font("Segoe UI Emoji", Font.PLAIN, size));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        int x = (size - fm.stringWidth(emoji)) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(emoji, x, y);
        g.dispose();
        return img;
    }

    // === RENDERERS ===
    static class NetworkRenderer extends JPanel implements ListCellRenderer<WifiNetwork> {
        private JLabel ssid = new JLabel();
        private JLabel info = new JLabel();
        private JLabel signal = new JLabel();

        public NetworkRenderer() {
            setLayout(new MigLayout("fill, insets 10", "[grow]10[]", "[]"));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(60, 60, 60)),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)
            ));
            ssid.setFont(new Font("Segoe UI", Font.BOLD, 15));
            info.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            info.setForeground(new Color(173, 181, 189));
            signal.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
            add(ssid, "grow");
            add(signal);
            add(info, "wrap, span");
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends WifiNetwork> list, WifiNetwork net,
                                                      int index, boolean selected, boolean focus) {
            ssid.setText(net.getSsid());
            String sec = net.getSecurity().equals("Open") ? "openNetwork" : "secured";
            info.setText(BUNDLE.getString(sec) + " • " + net.getFrequency());
            signal.setText(switch (net.getSignalStrength() / 20) {
                case 5 -> "fullSignal";
                case 4 -> "goodSignal";
                case 3 -> "averageSignal";
                case 2 -> "weakSignal";
                default -> "veryWeakSignal";
            });
            setBackground(selected ? new Color(0, 122, 255) : (index % 2 == 0 ? new Color(28, 28, 28) : new Color(24, 24, 24)));
            ssid.setForeground(selected ? Color.WHITE : Color.WHITE);
            info.setForeground(selected ? Color.WHITE : new Color(173, 181, 189));
            return this;
        }
    }

    // === CUSTOM COMPONENTS ===
    static class ModernCardPanel extends JPanel {
        public ModernCardPanel() {
            setOpaque(false);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(50, 50, 50)),
                    BorderFactory.createEmptyBorder(15, 15, 15, 15)
            ));
        }
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            g2.dispose();
        }
    }

    static class ModernSectionLabel extends JLabel {
        public ModernSectionLabel(String key) {
            super(BUNDLE.getString(key));
            setFont(new Font("Segoe UI", Font.BOLD, 16));
            setForeground(new Color(0, 122, 255));
        }
    }

    static class ModernFieldLabel extends JLabel {
        public ModernFieldLabel(String key) {
            super(BUNDLE.getString(key));
            setFont(new Font("Segoe UI", Font.PLAIN, 13));
            setForeground(new Color(173, 181, 189));
        }
    }

    static class ModernScrollPane extends JScrollPane {
        public ModernScrollPane(Component c) {
            super(c);
            setBorder(BorderFactory.createEmptyBorder());
            getVerticalScrollBar().setUnitIncrement(16);
        }
    }

    static class ModernProgressBar extends JProgressBar {
        public ModernProgressBar() {
            setStringPainted(true);
            setFont(new Font("Segoe UI", Font.BOLD, 12));
        }
    }

    // === MAIN ===
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.put("Button.arc", 12);
                UIManager.put("Component.arc", 12);
                UIManager.put("TextComponent.arc", 8);
                new WifiConnectorGuiList();
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
}