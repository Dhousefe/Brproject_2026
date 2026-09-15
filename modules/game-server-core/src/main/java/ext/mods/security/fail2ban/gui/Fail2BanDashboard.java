/*
* Copyleft © 2024-2026 L2Brproject
*/
package ext.mods.security.fail2ban.gui;

import ext.mods.security.fail2ban.core.BanManager;
import ext.mods.security.fail2ban.core.BanRecord;
import ext.mods.security.fail2ban.core.Fail2BanEvent;
import ext.mods.security.fail2ban.core.GeoLocationService;
import ext.mods.security.fail2ban.core.PanicMode;
import ext.mods.security.fail2ban.core.SecurityConfigManager;
import ext.mods.security.fail2ban.firewall.FirewallAdapter;
import ext.mods.security.fail2ban.firewall.FirewallAdapterFactory;
import ext.mods.commons.gui.CustomTopPanel;
import ext.mods.commons.gui.ModernUI;
import ext.mods.commons.gui.ThemeManager;
import ext.mods.security.fail2ban.simd.CleanRoomManager;
import ext.mods.security.fail2ban.simd.PacketVector128;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import ext.mods.commons.gui.services.ProcessManagerService;
import ext.mods.security.fail2ban.core.SecurityConfigManager.XmlValidationResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;

/**
 * Fail2Ban Dashboard: Full-featured reactive Swing UI.
 * Standardized with ModernUI Cyberpunk Dark theme.
 *
 * Tabs:
 * - Active Bans (with GeoIP, durations, and safe unban confirmation)
 * - Events Log (real-time stream via BanManager listener + SQLite history)
 * - Proxy Logs (live streaming tailer with pause/resume and auto-scroll)
 * - Panic Mode telemetry and manual control
 */
public class Fail2BanDashboard {
	private static Fail2BanDashboard instance;

	private JFrame frame;
	private JTable banTable;
	private JTable eventTable;
	private JTextArea logArea;
	private DefaultTableModel banTableModel;
	private DefaultTableModel eventTableModel;
	private JLabel statusLabel;
	private JButton panicBtn;
	private JButton fail2banToggleBtn;
	private JButton proxyToggleBtn;
	private JLabel configSyncLabel;

	private BanManager banManager;
	private GeoLocationService geoService;
	private FirewallAdapter firewall;
	private ProxyLogTailer logTailer;
	private Consumer<Fail2BanEvent> eventListener;
	private Timer refreshTimer;
	private Timer liveEventBatchTimer;
	private final java.util.concurrent.ConcurrentLinkedQueue<Fail2BanEvent> pendingEvents = new java.util.concurrent.ConcurrentLinkedQueue<>();
	private TableRowSorter<DefaultTableModel> banSorter;
	private TableRowSorter<DefaultTableModel> eventSorter;
	private JTextField banSearchField;
	private JTextField eventSearchField;
	private JTabbedPane tabbedPane;
	private DefaultTableModel routesTableModel;
	private JTable routesTable;
	private TableRowSorter<DefaultTableModel> routesSorter;
	private JTextField routesSearchField;
	private JTextArea xmlEditorArea;
	private JLabel xmlValidationLabel;

	// Golden Profile Components
	private DefaultTableModel goldenIpsModel;
	private JTable goldenIpsTable;
	private JProgressBar goldenTrainingProgress;
	private JLabel goldenStatusBadge;
	private JLabel goldenSamplesLabel;
	private JLabel goldenAnomaliesLabel;
	private JLabel goldenSpeedLabel;
	private JTextField goldenNewIpField;
	private JTextField goldenNotesField;
	private JTextField goldenTestIpField;
	private JLabel goldenTestResultLabel;
	private JButton goldenFreezeBtn;
	private JButton sendToGoldenBtn;
	private JSpinner goldenMarginSpinner;

	private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

	private Fail2BanDashboard() {}

	public static synchronized Fail2BanDashboard getInstance() {
		if (instance == null) {
			instance = new Fail2BanDashboard();
		}
		return instance;
	}

	public void showWindow(BanManager banManager) {
		if (frame != null && frame.isVisible()) {
			frame.toFront();
			return;
		}

		this.banManager = banManager;
		this.geoService = new GeoLocationService();
		this.firewall = FirewallAdapterFactory.detect();
		this.logTailer = new ProxyLogTailer();

		try {
			ThemeManager.applyTheme();
		} catch (Exception ignored) {
		}

		frame = new JFrame("Fail2Ban Security Dashboard");
		frame.setUndecorated(true);
		frame.setSize(1040, 680);
		frame.setMinimumSize(new Dimension(850, 550));
		frame.setLocationRelativeTo(null);
		frame.setResizable(true);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		// Habilita redimensionamento suave com o mouse via todas as 4 bordas e 4 cantos
		new ext.mods.commons.gui.ComponentResizer(frame);
		((JComponent) frame.getContentPane()).setBorder(BorderFactory.createLineBorder(new Color(60, 45, 90), 1));

		// Top composite panel: CustomTopPanel + ServiceControlBar
		JPanel northContainer = new JPanel(new BorderLayout());
		northContainer.setOpaque(false);
		String iconPath = "./images/16x16.png";
		Runnable closeAction = this::closeWindow;
		CustomTopPanel topPanel = new CustomTopPanel(frame, null, closeAction, false, iconPath);
		northContainer.add(topPanel, BorderLayout.NORTH);
		northContainer.add(createServiceControlBar(), BorderLayout.SOUTH);
		frame.add(northContainer, BorderLayout.NORTH);

		// Main content with tabs
		this.tabbedPane = new JTabbedPane();
		tabbedPane.setUI(new ModernUI.ModernTabbedPaneUI());
		tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 12));
		tabbedPane.setBackground(ModernUI.BG_DARK);
		tabbedPane.setForeground(ModernUI.TEXT_WHITE);

		tabbedPane.addTab("  Active Bans  ", createBansTab());
		tabbedPane.addTab("  Live Events  ", createEventsTab());
		tabbedPane.addTab("  Proxy Logs  ", createLogsTab());
		tabbedPane.addTab("  Proxy Routes  ", createRoutesTab());
		tabbedPane.addTab("  XML Config  ", createXmlConfigTab());
		tabbedPane.addTab("  Golden Profile  ", createGoldenProfileTab());

		frame.add(tabbedPane, BorderLayout.CENTER);
		frame.add(createStatusBar(), BorderLayout.SOUTH);

		// Register live pub/sub listener for Fail2Ban events
		this.eventListener = this::handleLiveEvent;
		if (this.banManager != null) {
			this.banManager.addListener(this.eventListener);
		}

		// Decoupled batch timer for high-throughput live events (100ms ticks)
		this.liveEventBatchTimer = new Timer(100, e -> flushPendingEvents());
		this.liveEventBatchTimer.start();

		// Initial data load
		refreshBans();
		loadHistoricalEvents();
		refreshLogs();
		loadRoutesAndXml();
		refreshGoldenProfileTab();
		updateStatus();

		// Auto-refresh timer for periodic background checks
		startRefreshTimer();

		frame.setVisible(true);
	}

	/**
	 * Tab 1: Active Bans with geolocation and manual controls.
	 */
	private JPanel createBansTab() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ModernUI.BG_DARK);
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));

		// Top: Manual ban controls
		panel.add(createManualBanPanel(), BorderLayout.NORTH);

		// Center: Table with RowSorter and Search Filter
		String[] columns = {"IP Address", "Country", "City", "ISP", "Jail", "Since", "Expires", "Reason"};
		banTableModel = new DefaultTableModel(columns, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		banTable = new JTable(banTableModel);
		banSorter = new TableRowSorter<>(banTableModel);
		banTable.setRowSorter(banSorter);
		styleTable(banTable, true);

		// Double-click to unban
		banTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					unbanSelected();
				}
			}
		});

		JScrollPane scroll = new JScrollPane(banTable);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getViewport().setBackground(ModernUI.BG_DARK);
		scroll.setBorder(BorderFactory.createLineBorder(new Color(40, 35, 55), 1));
		scroll.getVerticalScrollBar().setUI(new ModernUI.ModernScrollBarUI());
		scroll.getHorizontalScrollBar().setUI(new ModernUI.ModernScrollBarUI());

		// Center container: Search bar + Table
		JPanel centerPanel = new JPanel(new BorderLayout(0, 6));
		centerPanel.setOpaque(false);

		JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		searchBar.setOpaque(false);
		JLabel searchLbl = new JLabel("Filter Bans:");
		searchLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
		searchLbl.setForeground(ModernUI.NEON_CYAN);
		searchBar.add(searchLbl);

		banSearchField = new JTextField(22);
		styleTextField(banSearchField);
		banSearchField.setToolTipText("Filter by IP, country, city, ISP or reason in real time");
		banSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			@Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applyBanFilter(); }
			@Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applyBanFilter(); }
			@Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyBanFilter(); }
		});
		searchBar.add(banSearchField);

		JButton clearFilterBtn = createStyledButton("Reset", new Color(30, 28, 42), ModernUI.NEON_CYAN);
		clearFilterBtn.addActionListener(e -> {
			banSearchField.setText("");
			applyBanFilter();
		});
		searchBar.add(clearFilterBtn);

		centerPanel.add(searchBar, BorderLayout.NORTH);
		centerPanel.add(scroll, BorderLayout.CENTER);
		panel.add(centerPanel, BorderLayout.CENTER);

		// Bottom: Action buttons
		panel.add(createBanActionsPanel(), BorderLayout.SOUTH);

		return panel;
	}

	/**
	 * Manual ban input panel with duration selector.
	 */
	private JPanel createManualBanPanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(ModernUI.BG_DARK);
		panel.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ModernUI.NEON_PURPLE, 1),
			" Manual Ban Control ",
			javax.swing.border.TitledBorder.LEFT,
			javax.swing.border.TitledBorder.TOP,
			new Font("Segoe UI", Font.BOLD, 11),
			ModernUI.NEON_CYAN
		));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.fill = GridBagConstraints.HORIZONTAL;

		// 1. IP Input
		gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
		JLabel ipLabel = new JLabel("IP Address:");
		ipLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		ipLabel.setForeground(ModernUI.TEXT_WHITE);
		panel.add(ipLabel, gbc);

		gbc.gridx = 1; gbc.weightx = 1.0;
		JTextField ipField = new JTextField(15);
		styleTextField(ipField);
		panel.add(ipField, gbc);

		// 2. Reason Input
		gbc.gridx = 2; gbc.weightx = 0;
		JLabel reasonLabel = new JLabel("Reason:");
		reasonLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		reasonLabel.setForeground(ModernUI.TEXT_WHITE);
		panel.add(reasonLabel, gbc);

		gbc.gridx = 3; gbc.weightx = 1.5;
		JTextField reasonField = new JTextField(20);
		styleTextField(reasonField);
		panel.add(reasonField, gbc);

		// 3. Duration Selector
		gbc.gridx = 4; gbc.weightx = 0;
		JLabel durationLabel = new JLabel("Duration:");
		durationLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		durationLabel.setForeground(ModernUI.TEXT_WHITE);
		panel.add(durationLabel, gbc);

		gbc.gridx = 5; gbc.weightx = 0.8;
		String[] durations = {"1 Hour", "12 Hours", "24 Hours", "7 Days", "Permanent"};
		JComboBox<String> durationCombo = new JComboBox<>(durations);
		durationCombo.setBackground(ModernUI.BG_PANEL);
		durationCombo.setForeground(ModernUI.TEXT_WHITE);
		durationCombo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		panel.add(durationCombo, gbc);

		// 4. Ban Button
		gbc.gridx = 6; gbc.weightx = 0;
		JButton banBtn = createStyledButton("Ban IP", new Color(180, 20, 60), Color.WHITE);
		banBtn.addActionListener(e -> {
			String ip = ipField.getText().trim();
			String reason = reasonField.getText().trim();
			if (ip.isEmpty()) {
				JOptionPane.showMessageDialog(frame, "IP address cannot be empty.", "Validation Error", JOptionPane.ERROR_MESSAGE);
				return;
			}
			long durationMs;
			String selected = (String) durationCombo.getSelectedItem();
			if ("12 Hours".equals(selected)) durationMs = 43200000L;
			else if ("24 Hours".equals(selected)) durationMs = 86400000L;
			else if ("7 Days".equals(selected)) durationMs = 604800000L;
			else if ("Permanent".equals(selected)) durationMs = -1L;
			else durationMs = 3600000L; // 1 Hour default

			if (banManager != null) {
				banManager.ban(ip, "manual", reason.isEmpty() ? "Manual administrator ban" : reason, durationMs);
				ipField.setText("");
				reasonField.setText("");
				markEventsAsBlockedForIp(ip);
				refreshBans();
				updateStatus();
			}
		});
		panel.add(banBtn, gbc);

		return panel;
	}

	/**
	 * Action buttons for bans table.
	 */
	private JPanel createBanActionsPanel() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		panel.setBackground(ModernUI.BG_DARK);

		JButton unbanBtn = createStyledButton("Unban Selected", ModernUI.NEON_BLUE, Color.WHITE);
		unbanBtn.addActionListener(e -> unbanSelected());
		panel.add(unbanBtn);

		JButton refreshBtn = createStyledButton("Refresh", new Color(30, 28, 42), ModernUI.NEON_CYAN);
		refreshBtn.addActionListener(e -> {
			refreshBans();
			updateStatus();
		});
		panel.add(refreshBtn);

		return panel;
	}

	/**
	 * Unban currently selected IP with safety confirmation.
	 */
	private void unbanSelected() {
		int row = banTable.getSelectedRow();
		if (row < 0) {
			JOptionPane.showMessageDialog(frame, "Please select an active ban from the table first.", "No Selection", JOptionPane.WARNING_MESSAGE);
			return;
		}
		String ip = (String) banTableModel.getValueAt(row, 0);
		int confirm = JOptionPane.showConfirmDialog(
			frame,
			"Are you sure you want to unban IP: " + ip + "?\nThis will remove it from the active database and OS firewall.",
			"Confirm Unban",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.QUESTION_MESSAGE
		);

		if (confirm == JOptionPane.YES_OPTION && banManager != null) {
			banManager.unban(ip);
			markEventsAsUnbannedForIp(ip);
			refreshBans();
			updateStatus();
		}
	}

	/**
	 * Tab 2: Events log (Live event stream + SQLite historical log).
	 */
	private JPanel createEventsTab() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ModernUI.BG_DARK);
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));

		String[] columns = {"Timestamp", "Event Type", "IP Address", "IP Ver", "Status", "Location", "Jail / Route", "Details / Rule", "Payload Sample"};
		eventTableModel = new DefaultTableModel(columns, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		eventTable = new JTable(eventTableModel);
		eventSorter = new TableRowSorter<>(eventTableModel);
		eventTable.setRowSorter(eventSorter);
		styleTable(eventTable, false);

		// Hide the raw payload sample column from visual table (stored in model for inspector modal)
		if (eventTable.getColumnModel().getColumnCount() > 8) {
			eventTable.getColumnModel().getColumn(8).setMinWidth(0);
			eventTable.getColumnModel().getColumn(8).setMaxWidth(0);
			eventTable.getColumnModel().getColumn(8).setPreferredWidth(0);
		}

		// Double-click or single-click inspection dialog, right-click context menu
		eventTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isRightMouseButton(e)) {
					int r = eventTable.rowAtPoint(e.getPoint());
					if (r >= 0 && r < eventTable.getRowCount()) {
						eventTable.setRowSelectionInterval(r, r);
					}
					showEventContextMenu(e.getX(), e.getY());
				} else if (e.getClickCount() >= 2 || (e.getClickCount() == 1 && eventTable.getSelectedRow() >= 0)) {
					showEventDetails(eventTable.getSelectedRow());
				}
			}
		});

		// Dynamic 1-click IP selection listener for Golden Profile
		eventTable.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				int row = eventTable.getSelectedRow();
				if (row >= 0) {
					int modelRow = eventTable.convertRowIndexToModel(row);
					String ip = (String) eventTableModel.getValueAt(modelRow, 2);
					if (ip != null && !ip.isBlank()) {
						sendToGoldenBtn.setText("[Area Limpa] Enviar " + ip + " para Golden Profile");
						sendToGoldenBtn.setEnabled(true);
						return;
					}
				}
				sendToGoldenBtn.setText("[Area Limpa] Enviar IP para Golden Profile");
				sendToGoldenBtn.setEnabled(false);
			}
		});

		JScrollPane scroll = new JScrollPane(eventTable);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getViewport().setBackground(ModernUI.BG_DARK);
		scroll.setBorder(BorderFactory.createLineBorder(new Color(40, 35, 55), 1));
		scroll.getVerticalScrollBar().setUI(new ModernUI.ModernScrollBarUI());
		scroll.getHorizontalScrollBar().setUI(new ModernUI.ModernScrollBarUI());

		// Center container: Search bar + Table
		JPanel centerPanel = new JPanel(new BorderLayout(0, 6));
		centerPanel.setOpaque(false);

		JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		searchBar.setOpaque(false);
		JLabel searchLbl = new JLabel("Filter Events:");
		searchLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
		searchLbl.setForeground(ModernUI.NEON_CYAN);
		searchBar.add(searchLbl);

		eventSearchField = new JTextField(22);
		styleTextField(eventSearchField);
		eventSearchField.setToolTipText("Filter live events by type, IP, IPv4/IPv6, status, jail or rule in real time");
		eventSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			@Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applyEventFilter(); }
			@Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applyEventFilter(); }
			@Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyEventFilter(); }
		});
		searchBar.add(eventSearchField);

		JButton clearFilterBtn = createStyledButton("Reset", new Color(30, 28, 42), ModernUI.NEON_CYAN);
		clearFilterBtn.addActionListener(e -> {
			eventSearchField.setText("");
			applyEventFilter();
		});
		searchBar.add(clearFilterBtn);

		centerPanel.add(searchBar, BorderLayout.NORTH);
		centerPanel.add(scroll, BorderLayout.CENTER);
		panel.add(centerPanel, BorderLayout.CENTER);

		// Event controls
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		controls.setBackground(ModernUI.BG_DARK);

		sendToGoldenBtn = createStyledButton("[Area Limpa] Enviar IP para Golden Profile", new Color(75, 30, 120), ModernUI.NEON_CYAN);
		sendToGoldenBtn.setEnabled(false);
		sendToGoldenBtn.setToolTipText("Adiciona o IP selecionado diretamente a Area Limpa e abre a aba Golden Profile para calibracao SIMD");
		sendToGoldenBtn.addActionListener(e -> {
			int row = eventTable.getSelectedRow();
			if (row >= 0) {
				int modelRow = eventTable.convertRowIndexToModel(row);
				String ip = (String) eventTableModel.getValueAt(modelRow, 2);
				if (ip != null && !ip.isBlank()) {
					addIpToGoldenProfileAndSwitch(ip);
				}
			} else {
				JOptionPane.showMessageDialog(frame, "Selecione um evento com endereco IP na tabela.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
			}
		});
		controls.add(sendToGoldenBtn);

		JButton inspectBtn = createStyledButton("Inspecionar Alvo", ModernUI.NEON_PURPLE, Color.WHITE);
		inspectBtn.setToolTipText("Abre a janela de detalhes completos do alvo selecionado com ações de Banir/Desbanir");
		inspectBtn.addActionListener(e -> {
			int row = eventTable.getSelectedRow();
			if (row >= 0) {
				showEventDetails(row);
			} else {
				JOptionPane.showMessageDialog(frame, "Selecione um evento na tabela para inspecionar os detalhes.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
			}
		});
		controls.add(inspectBtn);

		JButton exportBtn = createStyledButton("Export CSV", new Color(40, 90, 60), new Color(180, 255, 200));
		exportBtn.setToolTipText("Export current events to CSV file for auditing");
		exportBtn.addActionListener(e -> exportEventsToCsv());
		controls.add(exportBtn);

		JButton clearBtn = createStyledButton("Clear Table", new Color(30, 28, 42), ModernUI.NEON_CYAN);
		clearBtn.addActionListener(e -> eventTableModel.setRowCount(0));
		controls.add(clearBtn);

		JButton reloadBtn = createStyledButton("Reload History", new Color(30, 28, 42), ModernUI.NEON_CYAN);
		reloadBtn.addActionListener(e -> loadHistoricalEvents());
		controls.add(reloadBtn);

		panel.add(controls, BorderLayout.SOUTH);

		return panel;
	}

	private void showEventContextMenu(int x, int y) {
		int row = eventTable.getSelectedRow();
		if (row < 0) return;
		int modelRow = eventTable.convertRowIndexToModel(row);
		String ip = (String) eventTableModel.getValueAt(modelRow, 2);
		if (ip == null || ip.isBlank()) return;

		JPopupMenu menu = new JPopupMenu();
		menu.setBackground(ModernUI.BG_PANEL);
		menu.setBorder(BorderFactory.createLineBorder(ModernUI.NEON_PURPLE, 1));

		JMenuItem cleanRoomItem = new JMenuItem("[Area Limpa] Mover IP " + ip + " para Area Limpa (Golden Profile)");
		cleanRoomItem.setFont(new Font("Segoe UI", Font.BOLD, 11));
		cleanRoomItem.setBackground(ModernUI.BG_PANEL);
		cleanRoomItem.setForeground(ModernUI.NEON_CYAN);
		cleanRoomItem.addActionListener(e -> {
			addIpToGoldenProfileAndSwitch(ip);
		});
		menu.add(cleanRoomItem);

		JMenuItem inspectItem = new JMenuItem("[Inspecionar] Detalhes do Evento");
		inspectItem.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		inspectItem.setBackground(ModernUI.BG_PANEL);
		inspectItem.setForeground(ModernUI.TEXT_WHITE);
		inspectItem.addActionListener(e -> showEventDetails(row));
		menu.add(inspectItem);

		JMenuItem banItem = new JMenuItem("[Banir] IP " + ip);
		banItem.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		banItem.setBackground(ModernUI.BG_PANEL);
		banItem.setForeground(new Color(255, 100, 120));
		banItem.addActionListener(e -> {
			if (banManager != null) {
				banManager.ban(ip, "manual", "Ban manual acionado via Live Events", 86400000L);
				markEventsAsBlockedForIp(ip);
				refreshBans();
				updateStatus();
			}
		});
		menu.add(banItem);

		JMenuItem unbanItem = new JMenuItem("[Desbanir] IP " + ip);
		unbanItem.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		unbanItem.setBackground(ModernUI.BG_PANEL);
		unbanItem.setForeground(new Color(80, 255, 140));
		unbanItem.addActionListener(e -> {
			if (banManager != null) {
				banManager.unban(ip);
				markEventsAsUnbannedForIp(ip);
				refreshBans();
				updateStatus();
			}
		});
		menu.add(unbanItem);

		menu.show(eventTable, x, y);
	}

	/**
	 * Tab 3: Proxy logs tailer.
	 */
	private JPanel createLogsTab() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ModernUI.BG_DARK);
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));

		logArea = new JTextArea();
		logArea.setEditable(false);
		logArea.setBackground(ModernUI.BG_CONSOLE);
		logArea.setForeground(new Color(230, 230, 245));
		logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		logArea.setLineWrap(true);
		logArea.setWrapStyleWord(true);
		logArea.setCaretColor(ModernUI.NEON_CYAN);

		JScrollPane scroll = new JScrollPane(logArea);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getViewport().setBackground(ModernUI.BG_CONSOLE);
		scroll.setBorder(BorderFactory.createLineBorder(new Color(40, 35, 55), 1));
		scroll.getVerticalScrollBar().setUI(new ModernUI.ModernScrollBarUI());
		scroll.getHorizontalScrollBar().setUI(new ModernUI.ModernScrollBarUI());
		panel.add(scroll, BorderLayout.CENTER);

		// Controls
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		controls.setBackground(ModernUI.BG_DARK);

		JButton pauseBtn = createStyledButton("Pause", new Color(30, 28, 42), ModernUI.NEON_CYAN);
		pauseBtn.addActionListener(e -> {
			if (logTailer != null) {
				if (logTailer.isPaused()) {
					logTailer.resume();
					pauseBtn.setText("Pause");
				} else {
					logTailer.pause();
					pauseBtn.setText("Resume");
				}
			}
		});
		controls.add(pauseBtn);

		JButton clearBtn = createStyledButton("Clear Logs", new Color(30, 28, 42), ModernUI.NEON_CYAN);
		clearBtn.addActionListener(e -> {
			if (logTailer != null) logTailer.clear();
			logArea.setText("");
		});
		controls.add(clearBtn);

		JButton reloadBtn = createStyledButton("Reload", new Color(30, 28, 42), ModernUI.NEON_CYAN);
		reloadBtn.addActionListener(e -> {
			if (logTailer != null) logTailer.reload();
			refreshLogs();
		});
		controls.add(reloadBtn);

		panel.add(controls, BorderLayout.SOUTH);

		return panel;
	}

	/**
	 * Service Control Bar: Dynamic toggles for Fail2Ban and Native Netty Proxy.
	 * Updates server.properties and loginserver.properties on change.
	 */
	private JPanel createServiceControlBar() {
		JPanel bar = new JPanel(new BorderLayout(12, 0));
		bar.setBackground(new Color(24, 22, 34));
		bar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 35, 55)),
			BorderFactory.createEmptyBorder(6, 12, 6, 12)
		));

		// Left title / summary
		JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		leftPanel.setOpaque(false);
		JLabel titleLabel = new JLabel("SISTEMA DE DEFESA & BORDA:");
		titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
		titleLabel.setForeground(ModernUI.NEON_CYAN);
		leftPanel.add(titleLabel);

		configSyncLabel = new JLabel("Auto-Sync: server.properties & loginserver.properties");
		configSyncLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
		configSyncLabel.setForeground(new Color(160, 160, 180));
		leftPanel.add(configSyncLabel);
		bar.add(leftPanel, BorderLayout.WEST);

		// Right controls
		JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		controlsPanel.setOpaque(false);

		// Fail2Ban Toggle
		fail2banToggleBtn = createStyledButton("Fail2Ban: CARREGANDO...", new Color(30, 28, 42), ModernUI.NEON_CYAN);
		fail2banToggleBtn.addActionListener(e -> toggleFail2Ban());
		controlsPanel.add(fail2banToggleBtn);

		// Proxy Toggle
		proxyToggleBtn = createStyledButton("Proxy Nativo: CARREGANDO...", new Color(30, 28, 42), ModernUI.NEON_CYAN);
		proxyToggleBtn.addActionListener(e -> toggleProxy());
		controlsPanel.add(proxyToggleBtn);



		bar.add(controlsPanel, BorderLayout.EAST);
		updateServiceToggleButtons();

		return bar;
	}

	private void toggleFail2Ban() {
		SecurityConfigManager scm = SecurityConfigManager.getInstance();
		boolean current = scm.isFail2BanEnabled();
		boolean target = !current;

		if (target && isWindows() && !isWindowsAdmin()) {
			int confirmAdmin = JOptionPane.showConfirmDialog(
				frame,
				"O Fail2Ban requer privilégios de Administrador para:\n\n"
				+ "1. Aplicar regras ativas de bloqueio no Firewall do Windows (netsh advfirewall)\n"
				+ "2. Instalar o Certificado TLS autoassinado nas Autoridades Raiz Confiáveis (Windows ROOT)\n\n"
				+ "Deseja solicitar Elevação de Administrador (UAC) e aplicar as permissões agora?",
				"Elevação de Administrador: Fail2Ban & TLS",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE
			);
			if (confirmAdmin == JOptionPane.YES_OPTION) {
				elevateFail2BanAndCertificate();
			}
		}

		String msg = target
			? "Deseja ATIVAR o Fail2Ban?\n\n- O motor de defesa analisará eventos e aplicará punições ativas.\n- Sincronizará EnableFail2Ban = true em server.properties e loginserver.properties."
			: "Deseja DESATIVAR o Fail2Ban?\n\n- O motor de defesa entrará em modo bypass (nenhum IP será punido).\n- Sincronizará EnableFail2Ban = false em server.properties e loginserver.properties.";

		int confirm = JOptionPane.showConfirmDialog(frame, msg, "Controle de Serviço: Fail2Ban", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (confirm == JOptionPane.YES_OPTION) {
			boolean ok = scm.setFail2BanEnabled(target);
			if (ok) {
				updateServiceToggleButtons();
				updateStatus();
				JOptionPane.showMessageDialog(frame, "Fail2Ban " + (target ? "ATIVADO" : "DESATIVADO") + " com sucesso!\nConfigurações gravadas em server.properties e loginserver.properties.", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
			} else {
				JOptionPane.showMessageDialog(frame, "Erro ao alterar estado do Fail2Ban.", "Erro", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void toggleProxy() {
		SecurityConfigManager scm = SecurityConfigManager.getInstance();
		boolean current = scm.isProxyEnabled();
		boolean target = !current;

		if (target && isWindows()) {
			File certFile = resolveLocalCertFile();
			if (certFile != null && !isCertInstalledInWindowsRoot(certFile)) {
				int confirmCert = JOptionPane.showConfirmDialog(
					frame,
					"O Proxy Reverso Nativo possui Auto-TLS habilitado.\n\n"
					+ "Para que o navegador (Chrome/Edge) não exiba aviso de segurança ('Não Seguro'),\n"
					+ "o certificado (" + certFile.getName() + ") precisa ser instalado nas Autoridades Raiz.\n\n"
					+ "Deseja instalar o certificado com Elevação de Administrador agora?",
					"Instalação de Certificado TLS",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.QUESTION_MESSAGE
				);
				if (confirmCert == JOptionPane.YES_OPTION) {
					installCertWithElevation(certFile);
				}
			}
		}

		String msg = target
			? "Deseja ATIVAR o Proxy Reverso Nativo Netty (:proxy)?\n\n- O processo do Proxy será iniciado em background.\n- As portas públicas (7777 / 2106) passarão por rate limiting e mitigação L4/L7.\n- Sincronizará EnableNativeProxy = true e NativeProxyAutoStart = true em server.properties e loginserver.properties."
			: "Deseja DESATIVAR o Proxy Reverso Nativo Netty?\n\n- O processo do Proxy será encerrado.\n- O tráfego de rede não passará pela camada de borda.\n- Sincronizará EnableNativeProxy = false em server.properties e loginserver.properties.";

		int confirm = JOptionPane.showConfirmDialog(frame, msg, "Controle de Serviço: Proxy Reverso Nativo", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (confirm == JOptionPane.YES_OPTION) {
			boolean ok = scm.setProxyEnabled(target);
			if (ok) {
				updateServiceToggleButtons();
				updateStatus();
				refreshLogs();
				JOptionPane.showMessageDialog(frame, "Proxy Reverso " + (target ? "ATIVADO" : "DESATIVADO") + " com sucesso!\nConfigurações gravadas em server.properties e loginserver.properties.", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
			} else {
				JOptionPane.showMessageDialog(frame, "Erro ao alterar estado do Proxy Nativo.", "Erro", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	private boolean isWindowsAdmin() {
		if (!isWindows()) return false;
		try {
			Process p = new ProcessBuilder("cmd.exe", "/c", "net session").start();
			return p.waitFor() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	private File resolveLocalCertFile() {
		Path[] candidates = {
			Path.of("data", "certs", "auto-signed-127.0.0.1.crt"),
			Path.of("..", "..", "data", "certs", "auto-signed-127.0.0.1.crt"),
			Path.of("data", "certs", "auto-signed.crt"),
			Path.of("..", "..", "data", "certs", "auto-signed.crt")
		};
		for (Path p : candidates) {
			if (java.nio.file.Files.exists(p)) {
				return p.toFile();
			}
		}
		return null;
	}

	private boolean isCertInstalledInWindowsRoot(File certFile) {
		if (!isWindows() || certFile == null || !certFile.exists()) return false;
		try {
			java.security.KeyStore ks = java.security.KeyStore.getInstance("Windows-ROOT");
			ks.load(null, null);
			java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
			java.security.cert.X509Certificate targetCert;
			try (java.io.InputStream in = new java.io.FileInputStream(certFile)) {
				targetCert = (java.security.cert.X509Certificate) cf.generateCertificate(in);
			}
			java.util.Enumeration<String> aliases = ks.aliases();
			while (aliases.hasMoreElements()) {
				String alias = aliases.nextElement();
				java.security.cert.Certificate c = ks.getCertificate(alias);
				if (c instanceof java.security.cert.X509Certificate x509) {
					if (x509.getSerialNumber().equals(targetCert.getSerialNumber())
						|| x509.getSubjectX500Principal().equals(targetCert.getSubjectX500Principal())) {
						return true;
					}
				}
			}
		} catch (Throwable ignored) {}
		return false;
	}

	private void installCertWithElevation(File certFile) {
		if (!isWindows() || certFile == null || !certFile.exists()) return;
		try {
			String certPath = certFile.getAbsolutePath();
			String powershellCmd = "Start-Process cmd.exe -ArgumentList '/c certutil -addstore -f \"\"ROOT\"\" \"\"" + certPath + "\"\"' -Verb RunAs -Wait";
			ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", powershellCmd);
			pb.start();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frame, "Erro ao solicitar elevação para instalar certificado: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void elevateFail2BanAndCertificate() {
		if (!isWindows()) return;
		try {
			File certFile = resolveLocalCertFile();
			String certCmd = "";
			if (certFile != null && certFile.exists()) {
				certCmd = "certutil -addstore -f \"\"ROOT\"\" \"\"" + certFile.getAbsolutePath() + "\"\" & ";
			}
			String innerCmd = certCmd + "netsh advfirewall firewall show rule name=all dir=in >nul";
			String powershellCmd = "Start-Process cmd.exe -ArgumentList '/c " + innerCmd + "' -Verb RunAs -Wait";
			ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", powershellCmd);
			Process p = pb.start();
			p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
		} catch (Exception e) {
			System.err.println("[Fail2BanDashboard] Falha ao elevar: " + e.getMessage());
		}
	}

	private void updateServiceToggleButtons() {
		SecurityConfigManager scm = SecurityConfigManager.getInstance();
		boolean f2b = scm.isFail2BanEnabled();
		boolean proxy = scm.isProxyEnabled();

		if (fail2banToggleBtn != null) {
			if (f2b) {
				fail2banToggleBtn.setText("Fail2Ban: ATIVO");
				fail2banToggleBtn.setBackground(new Color(20, 80, 50));
				fail2banToggleBtn.setForeground(new Color(140, 255, 180));
				fail2banToggleBtn.setToolTipText("Fail2Ban está ATIVO. Clique para desativar e atualizar .properties.");
			} else {
				fail2banToggleBtn.setText("Fail2Ban: DESLIGADO");
				fail2banToggleBtn.setBackground(new Color(120, 25, 40));
				fail2banToggleBtn.setForeground(new Color(255, 160, 180));
				fail2banToggleBtn.setToolTipText("Fail2Ban está DESLIGADO. Clique para ativar e atualizar .properties.");
			}
		}

		if (proxyToggleBtn != null) {
			if (proxy) {
				proxyToggleBtn.setText("Proxy Netty: ATIVO");
				proxyToggleBtn.setBackground(new Color(20, 70, 80));
				proxyToggleBtn.setForeground(new Color(140, 240, 255));
				proxyToggleBtn.setToolTipText("Proxy Reverso Netty está ATIVO. Clique para desativar e atualizar .properties.");
			} else {
				proxyToggleBtn.setText("Proxy Netty: DESLIGADO");
				proxyToggleBtn.setBackground(new Color(90, 30, 40));
				proxyToggleBtn.setForeground(new Color(255, 160, 180));
				proxyToggleBtn.setToolTipText("Proxy Reverso Netty está DESLIGADO. Clique para ativar e atualizar .properties.");
			}
		}
	}

	/**
	 * Status bar with Panic Mode integration.
	 */
	private JPanel createStatusBar() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ModernUI.BG_PANEL);
		panel.setBorder(new EmptyBorder(6, 12, 6, 12));

		statusLabel = new JLabel("Initializing security status...");
		statusLabel.setForeground(ModernUI.TEXT_WHITE);
		statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		panel.add(statusLabel, BorderLayout.WEST);

		// Panic Mode Action Controls on Right
		JPanel panicPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		panicPanel.setOpaque(false);

		panicBtn = createStyledButton("Panic Mode: NORMAL", new Color(20, 80, 50), new Color(140, 255, 180));
		panicBtn.addActionListener(e -> showPanicModeDialog());
		panicPanel.add(panicBtn);

		panel.add(panicPanel, BorderLayout.EAST);

		return panel;
	}

	/**
	 * Dialog to inspect or change Panic Mode level.
	 */
	private void showPanicModeDialog() {
		PanicMode panic = PanicMode.getInstance();
		String[] levels = {"0 - NORMAL (Standard Operation)", "1 - ALERT (2x Sensitivity)", "2 - DEFENSIVE (Aggressive Subnet Ban)", "3 - LOCKDOWN (Whitelist Only)", "4 - BLACKHOLE (Reject All)"};
		JComboBox<String> combo = new JComboBox<>(levels);
		combo.setSelectedIndex(panic.getCurrentLevel());

		JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
		p.add(new JLabel("Current Panic Level: " + panic.getLevelName()));
		p.add(new JLabel("Impact Estimate: " + panic.getLossEstimate().display() + " (" + panic.getLossEstimate().description() + ")"));
		p.add(new JLabel("Total Accepted: " + panic.getTotalAccepted() + " | Total Rejected: " + panic.getTotalRejected()));
		p.add(new JLabel("Select new defense posture:"));
		p.add(combo);

		int res = JOptionPane.showConfirmDialog(frame, p, "Panic Mode Emergency Control", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (res == JOptionPane.OK_OPTION) {
			int selected = combo.getSelectedIndex();
			if (selected != panic.getCurrentLevel()) {
				panic.setLevel(selected, "Manual change from Fail2Ban Dashboard");
				updateStatus();
			}
		}
	}

	/**
	 * Handle incoming Fail2Ban event in real time.
	 * Decoupled: offers to lock-free queue, processed in batches by liveEventBatchTimer
	 * so high network throughput is never throttled by the Swing Event Dispatch Thread.
	 */
	private void handleLiveEvent(Fail2BanEvent event) {
		if (event != null) {
			pendingEvents.offer(event);
		}
	}

	/**
	 * Flushes queued live events to the table model in batches.
	 * Executes on the Swing EDT.
	 */
	private void flushPendingEvents() {
		if (eventTableModel == null || pendingEvents.isEmpty()) return;

		boolean bansChanged = false;
		int batchCount = 0;
		// Process up to 100 events per 100ms cycle to maintain 60fps UI responsiveness
		while (batchCount < 100) {
			Fail2BanEvent event = pendingEvents.poll();
			if (event == null) break;

			String time = TIME_FORMAT.format(new Date(event.timestamp()));
			GeoLocationService.GeoInfo geo = (geoService != null) ? geoService.lookup(event.ip()) : GeoLocationService.GeoInfo.unknown();

			eventTableModel.insertRow(0, new Object[]{
				time,
				event.type(),
				event.ip(),
				event.ipVersion(),
				event.status(),
				geo.shortDisplay(),
				event.jail() != null ? event.jail() : "—",
				event.detail() != null ? event.detail() : "",
				event.payloadSample() != null ? event.payloadSample() : ""
			});

			if ("BAN".equalsIgnoreCase(event.type())) {
				bansChanged = true;
				markEventsAsBlockedForIp(event.ip());
			} else if ("UNBAN".equalsIgnoreCase(event.type()) || "EXPIRE".equalsIgnoreCase(event.type())) {
				bansChanged = true;
				markEventsAsUnbannedForIp(event.ip());
			}
			batchCount++;
		}

		// Retain up to 2,000 events in memory while the window is open for comprehensive auditing
		int excess = eventTableModel.getRowCount() - 2000;
		if (excess > 0) {
			for (int i = 0; i < excess; i++) {
				eventTableModel.removeRow(eventTableModel.getRowCount() - 1);
			}
		}

		if (bansChanged) {
			refreshBans();
			updateStatus();
		}
	}

	/**
	 * Load initial historical events from SQLite.
	 */
	private void loadHistoricalEvents() {
		if (banManager == null || eventTableModel == null) return;
		eventTableModel.setRowCount(0);
		List<Fail2BanEvent> events = banManager.getRecentEvents(100);
		for (Fail2BanEvent ev : events) {
			String time = TIME_FORMAT.format(new Date(ev.timestamp()));
			GeoLocationService.GeoInfo geo = (geoService != null) ? geoService.lookup(ev.ip()) : GeoLocationService.GeoInfo.unknown();
			boolean isCurrentlyBanned = (banManager != null && banManager.isBanned(ev.ip()));
			String status = isCurrentlyBanned ? "BLOQUEADO" : ev.status();
			String detail = ev.detail() != null ? ev.detail() : "";
			if (isCurrentlyBanned && !detail.contains("[BLOQUEADO NO PROXY]")) {
				detail = detail + (detail.isBlank() ? "" : " ") + "[BLOQUEADO NO PROXY]";
			}
			eventTableModel.addRow(new Object[]{
				time,
				ev.type(),
				ev.ip(),
				ev.ipVersion(),
				status,
				geo.shortDisplay(),
				ev.jail() != null ? ev.jail() : "—",
				detail,
				ev.payloadSample() != null ? ev.payloadSample() : ""
			});
		}
	}

	/**
	 * Atualiza todas as linhas de eventos pertencentes ao IP como BLOQUEADO no modelo da tabela.
	 */
	public void markEventsAsBlockedForIp(String ip) {
		if (eventTableModel == null || ip == null || ip.isBlank()) return;
		final String targetIp = ip.trim();
		Runnable task = () -> {
			for (int r = 0; r < eventTableModel.getRowCount(); r++) {
				String rowIp = String.valueOf(eventTableModel.getValueAt(r, 2));
				if (targetIp.equalsIgnoreCase(rowIp) || (isLoopback(targetIp) && isLoopback(rowIp))) {
					eventTableModel.setValueAt("BLOQUEADO", r, 4); // Coluna Status
					Object detailsObj = eventTableModel.getValueAt(r, 7);
					String detailStr = (detailsObj != null) ? detailsObj.toString() : "";
					if (!detailStr.contains("[BLOQUEADO NO PROXY]")) {
						eventTableModel.setValueAt(detailStr + (detailStr.isBlank() ? "" : " ") + "[BLOQUEADO NO PROXY]", r, 7);
					}
				}
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			task.run();
		} else {
			SwingUtilities.invokeLater(task);
		}
	}

	/**
	 * Atualiza todas as linhas de eventos pertencentes ao IP como LIBERADO no modelo da tabela.
	 */
	public void markEventsAsUnbannedForIp(String ip) {
		if (eventTableModel == null || ip == null || ip.isBlank()) return;
		final String targetIp = ip.trim();
		Runnable task = () -> {
			for (int r = 0; r < eventTableModel.getRowCount(); r++) {
				String rowIp = String.valueOf(eventTableModel.getValueAt(r, 2));
				if (targetIp.equalsIgnoreCase(rowIp) || (isLoopback(targetIp) && isLoopback(rowIp))) {
					String status = String.valueOf(eventTableModel.getValueAt(r, 4));
					if ("BLOQUEADO".equalsIgnoreCase(status)) {
						eventTableModel.setValueAt("LIBERADO", r, 4);
					}
					Object detailsObj = eventTableModel.getValueAt(r, 7);
					String detailStr = (detailsObj != null) ? detailsObj.toString() : "";
					if (detailStr.contains("[BLOQUEADO NO PROXY]")) {
						eventTableModel.setValueAt(detailStr.replace("[BLOQUEADO NO PROXY]", "[DESBANIDO]").trim(), r, 7);
					}
				}
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			task.run();
		} else {
			SwingUtilities.invokeLater(task);
		}
	}

	private static boolean isLoopback(String ip) {
		return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip) || "localhost".equalsIgnoreCase(ip);
	}

	/**
	 * Opens detailed inspector modal for selected event.
	 */
	private void showEventDetails(int viewRow) {
		if (viewRow < 0 || eventTable == null || eventTableModel == null) return;
		int modelRow = eventTable.convertRowIndexToModel(viewRow);
		String time = String.valueOf(eventTableModel.getValueAt(modelRow, 0));
		String type = String.valueOf(eventTableModel.getValueAt(modelRow, 1));
		String ip = String.valueOf(eventTableModel.getValueAt(modelRow, 2));
		String ipVer = String.valueOf(eventTableModel.getValueAt(modelRow, 3));
		String status = String.valueOf(eventTableModel.getValueAt(modelRow, 4));
		String loc = String.valueOf(eventTableModel.getValueAt(modelRow, 5));
		String jail = String.valueOf(eventTableModel.getValueAt(modelRow, 6));
		String detail = String.valueOf(eventTableModel.getValueAt(modelRow, 7));
		String sample = (eventTableModel.getColumnCount() > 8 && eventTableModel.getValueAt(modelRow, 8) != null)
			? String.valueOf(eventTableModel.getValueAt(modelRow, 8)) : "";

		new EventDetailsDialog(frame, time, type, ip, ipVer, status, loc, jail, detail, sample,
			banManager, geoService, firewall, () -> {
				refreshBans();
				updateStatus();
				if (banManager != null && banManager.isBanned(ip)) {
					markEventsAsBlockedForIp(ip);
				} else {
					markEventsAsUnbannedForIp(ip);
				}
			}).setVisible(true);
	}

	/**
	 * Apply consistent cyber-dark styling and cell renderers to tables.
	 */
	private void styleTable(JTable table, boolean isBanTable) {
		table.setBackground(ModernUI.BG_DARK);
		table.setForeground(ModernUI.TEXT_WHITE);
		table.setFont(new Font("Monospaced", Font.PLAIN, 11));
		table.setRowHeight(24);
		table.setSelectionBackground(ModernUI.NEON_PURPLE);
		table.setSelectionForeground(Color.WHITE);
		table.setGridColor(new Color(30, 28, 42));
		table.setIntercellSpacing(new Dimension(1, 1));

		table.getTableHeader().setBackground(ModernUI.BG_PANEL);
		table.getTableHeader().setForeground(ModernUI.NEON_CYAN);
		table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
		table.getTableHeader().setReorderingAllowed(false);

		// Custom cell renderers with semantic color tags
		for (int col = 0; col < table.getColumnCount(); col++) {
			int alignment;
			if (isBanTable) {
				alignment = (col == 1 || col == 4 || col == 5 || col == 6) ? SwingConstants.CENTER : SwingConstants.LEFT;
			} else {
				alignment = (col == 0 || col == 1 || col == 3 || col == 4 || col == 5) ? SwingConstants.CENTER : SwingConstants.LEFT;
			}
			boolean isEventTypeCol = !isBanTable && col == 1;
			boolean isIpVerCol = !isBanTable && col == 3;
			boolean isStatusCol = !isBanTable && col == 4;
			boolean isExpirationCol = isBanTable && col == 6;
			table.getColumnModel().getColumn(col).setCellRenderer(new StyledTableCellRenderer(alignment, isEventTypeCol, isIpVerCol, isStatusCol, isExpirationCol));
		}
	}

	/**
	 * Semantic cell renderer for table rows.
	 */
	private static class StyledTableCellRenderer extends DefaultTableCellRenderer {
		private final boolean isEventType;
		private final boolean isIpVer;
		private final boolean isStatus;
		private final boolean isExpiration;

		public StyledTableCellRenderer(int alignment, boolean isEventType, boolean isIpVer, boolean isStatus, boolean isExpiration) {
			this.isEventType = isEventType;
			this.isIpVer = isIpVer;
			this.isStatus = isStatus;
			this.isExpiration = isExpiration;
			setHorizontalAlignment(alignment);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if (isSelected) {
				c.setBackground(ModernUI.NEON_PURPLE);
				c.setForeground(Color.WHITE);
			} else {
				c.setBackground(row % 2 == 0 ? ModernUI.BG_DARK : new Color(18, 16, 25));
				c.setForeground(ModernUI.TEXT_WHITE);

				if (isEventType && value != null) {
					String type = value.toString().toUpperCase();
					setFont(getFont().deriveFont(Font.BOLD));
					switch (type) {
						case "BAN", "BAN_DROP" -> c.setForeground(new Color(255, 77, 109));       // Neon Red
						case "FAILURE", "PROXY_REJECT", "RATE_LIMIT_DENIED", "RATE_LIMIT" -> c.setForeground(new Color(255, 183, 3)); // Neon Amber
						case "HTTPS", "WSS" -> c.setForeground(new Color(6, 214, 160));          // Mint Green (Encrypted)
						case "WEBSOCKET" -> c.setForeground(new Color(199, 125, 255));           // Neon Violet
						case "HTTP", "TCP", "UDP" -> c.setForeground(ModernUI.NEON_CYAN);         // Neon Cyan
						case "UNBAN" -> c.setForeground(new Color(6, 214, 160));                  // Neon Green
						case "EXPIRE" -> c.setForeground(new Color(141, 153, 174));               // Slate Gray
						default -> c.setForeground(ModernUI.NEON_CYAN);
					}
				} else if (isIpVer && value != null) {
					setFont(getFont().deriveFont(Font.BOLD));
					if ("IPv6".equalsIgnoreCase(value.toString())) {
						c.setForeground(new Color(199, 125, 255)); // Neon Violet
					} else {
						c.setForeground(ModernUI.NEON_CYAN); // Neon Cyan
					}
				} else if (isStatus && value != null) {
					String status = value.toString().toUpperCase();
					setFont(getFont().deriveFont(Font.BOLD));
					if (status.contains("BLOQUEADO") || status.contains("BLOCK") || status.contains("REJECT")
						|| status.contains("BAN") || status.contains("DROP") || status.contains("SERVER_ERR")) {
						c.setForeground(new Color(255, 77, 109)); // Neon Red
					} else if (status.contains("SUSPEITO") || status.contains("WARN") || status.contains("ALERT")
						|| status.contains("RATE_LIMIT") || status.contains("CLIENT_ERR")) {
						c.setForeground(new Color(255, 183, 3)); // Neon Amber
					} else if (status.contains("PERMITIDO") || status.contains("ALLOW") || status.contains("PASS")
						|| status.contains("ATIVO")) {
						c.setForeground(new Color(6, 214, 160)); // Neon Green
					} else {
						c.setForeground(new Color(141, 153, 174));
					}
				} else if (isExpiration && value != null) {
					if ("PERMANENT".equals(value.toString())) {
						setFont(getFont().deriveFont(Font.BOLD));
						c.setForeground(new Color(199, 125, 255)); // Neon Violet
					}
				}
			}
			return c;
		}
	}

	private void styleTextField(JTextField field) {
		field.setBackground(ModernUI.BG_PANEL);
		field.setForeground(ModernUI.TEXT_WHITE);
		field.setCaretColor(ModernUI.NEON_CYAN);
		field.setFont(new Font("Monospaced", Font.PLAIN, 11));
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ModernUI.NEON_BLUE, 1),
			BorderFactory.createEmptyBorder(3, 6, 3, 6)
		));
	}

	private JButton createStyledButton(String text, Color bg, Color fg) {
		JButton btn = new JButton(text);
		btn.setBackground(bg);
		btn.setForeground(fg);
		btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
		btn.setFocusPainted(false);
		btn.setOpaque(true);
		btn.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(bg.brighter(), 1),
			BorderFactory.createEmptyBorder(5, 14, 5, 14)
		));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return btn;
	}

	/**
	 * Start auto-refresh timer.
	 */
	private void startRefreshTimer() {
		if (refreshTimer != null && refreshTimer.isRunning()) {
			refreshTimer.stop();
		}
		refreshTimer = new Timer(3000, e -> {
			refreshBans();
			refreshLogs();
			updateServiceToggleButtons();
			updateStatus();
			refreshGoldenProfileTab();
		});
		refreshTimer.start();
	}

	/**
	 * Refresh bans table.
	 */
	private void refreshBans() {
		if (banManager == null || banTableModel == null) return;
		banTableModel.setRowCount(0);
		Map<String, BanRecord> bans = banManager.getActiveBans();

		for (BanRecord ban : bans.values()) {
			GeoLocationService.GeoInfo geo = geoService.lookup(ban.ip());
			String since = TIME_FORMAT.format(new Date(ban.banTime()));
			String expires = ban.expireTime() < 0 ? "PERMANENT" : TIME_FORMAT.format(new Date(ban.expireTime()));

			banTableModel.addRow(new Object[]{
				ban.ip(),
				geo.shortDisplay(),
				geo.city() != null ? geo.city() : "—",
				geo.isp() != null ? geo.isp() : "—",
				ban.jailName(),
				since,
				expires,
				ban.reason()
			});
		}
	}

	/**
	 * Refresh proxy logs.
	 */
	private void refreshLogs() {
		if (logArea == null || logTailer == null) return;
		String content = logTailer.read();
		if (!content.equals(logArea.getText())) {
			logArea.setText(content);
			logArea.setCaretPosition(content.length());
		}
	}

	/**
	 * Update status bar.
	 */
	private void updateStatus() {
		if (statusLabel == null || banManager == null) return;
		int activeBans = banManager.getActiveBans().size();
		int eventCount = (eventTableModel != null) ? eventTableModel.getRowCount() : 0;
		PanicMode panic = PanicMode.getInstance();

		SecurityConfigManager scm = SecurityConfigManager.getInstance();
		String f2b = scm.isFail2BanEnabled() ? "ATIVO" : "DESLIGADO";
		String proxy = scm.isProxyEnabled() ? "ATIVO" : "DESLIGADO";

		statusLabel.setText(String.format("Fail2Ban: %s | Proxy Netty: %s | Firewall: %s | Active Bans: %d | Events: %d | Defended: %d | Cloudflare: TUNNEL_READY",
			f2b, proxy, firewall.name(), activeBans, eventCount, panic.getTotalRejected()));
		statusLabel.setToolTipText("Auto-defense active: SQLite WAL persistent + IPC Datagram zero-latency push (<0.2ms) + Cloudflare Real-IP extractor");

		if (panicBtn != null) {
			int level = panic.getCurrentLevel();
			panicBtn.setText("Panic Mode: " + panic.getLevelName());
			switch (level) {
				case PanicMode.NORMAL -> {
					panicBtn.setBackground(new Color(20, 80, 50));
					panicBtn.setForeground(new Color(140, 255, 180));
				}
				case PanicMode.ALERT -> {
					panicBtn.setBackground(new Color(120, 90, 20));
					panicBtn.setForeground(new Color(255, 220, 100));
				}
				case PanicMode.DEFENSIVE -> {
					panicBtn.setBackground(new Color(140, 60, 20));
					panicBtn.setForeground(new Color(255, 180, 120));
				}
				case PanicMode.LOCKDOWN, PanicMode.BLACKHOLE -> {
					panicBtn.setBackground(new Color(160, 20, 40));
					panicBtn.setForeground(new Color(255, 160, 180));
				}
			}
		}
	}

	private void applyBanFilter() {
		if (banSorter == null) return;
		String text = (banSearchField != null) ? banSearchField.getText().trim() : "";
		if (text.isEmpty()) {
			banSorter.setRowFilter(null);
		} else {
			banSorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
		}
	}

	private void applyEventFilter() {
		if (eventSorter == null) return;
		String text = (eventSearchField != null) ? eventSearchField.getText().trim() : "";
		if (text.isEmpty()) {
			eventSorter.setRowFilter(null);
		} else {
			eventSorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
		}
	}

	private void exportEventsToCsv() {
		if (eventTableModel == null || eventTableModel.getRowCount() == 0) {
			JOptionPane.showMessageDialog(frame, "No events available to export.", "Export Empty", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Export Fail2Ban Security Events to CSV");
		chooser.setSelectedFile(new java.io.File("fail2ban_events_" + System.currentTimeMillis() + ".csv"));
		int userSelection = chooser.showSaveDialog(frame);

		if (userSelection == JFileChooser.APPROVE_OPTION) {
			java.io.File fileToSave = chooser.getSelectedFile();
			try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.OutputStreamWriter(
				new java.io.FileOutputStream(fileToSave), java.nio.charset.StandardCharsets.UTF_8))) {

				// Write CSV Header
				pw.println("Timestamp,EventType,IPAddress,IPVersion,Status,Location,Jail,Details,PayloadSample");

				for (int row = 0; row < eventTableModel.getRowCount(); row++) {
					StringBuilder sb = new StringBuilder();
					for (int col = 0; col < eventTableModel.getColumnCount(); col++) {
						Object val = eventTableModel.getValueAt(row, col);
						String str = (val != null) ? val.toString().replace("\"", "\"\"") : "";
						sb.append("\"").append(str).append("\"");
						if (col < eventTableModel.getColumnCount() - 1) sb.append(",");
					}
					pw.println(sb);
				}

				JOptionPane.showMessageDialog(frame, "Security audit log exported successfully to:\n" + fileToSave.getAbsolutePath(),
					"Audit Log Exported", JOptionPane.INFORMATION_MESSAGE);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(frame, "Failed to export audit log: " + ex.getMessage(),
					"Export Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	
	/**
	 * Tab 4: Proxy Routes inspection and direct state toggle.
	 */
	private JPanel createRoutesTab() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ModernUI.BG_DARK);
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));

		String[] columns = {"Route Name", "Servico", "Type", "Status", "Bind (Host:Port)", "Target Host:Port", "Max Connections/min", "Max Requests/min"};
		routesTableModel = new DefaultTableModel(columns, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		routesTable = new JTable(routesTableModel);
		routesSorter = new TableRowSorter<>(routesTableModel);
		routesTable.setRowSorter(routesSorter);
		styleRoutesTable(routesTable);

		// Double-click to inspect and edit in XML
		routesTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					editSelectedRouteInXml();
				}
			}
		});

		JScrollPane scroll = new JScrollPane(routesTable);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getViewport().setBackground(ModernUI.BG_DARK);
		scroll.setBorder(BorderFactory.createLineBorder(new Color(40, 35, 55), 1));
		scroll.getVerticalScrollBar().setUI(new ModernUI.ModernScrollBarUI());
		scroll.getHorizontalScrollBar().setUI(new ModernUI.ModernScrollBarUI());

		// Center container: Search bar + Table
		JPanel centerPanel = new JPanel(new BorderLayout(0, 6));
		centerPanel.setOpaque(false);

		JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		searchBar.setOpaque(false);
		JLabel searchLbl = new JLabel("Filter Routes:");
		searchLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
		searchLbl.setForeground(ModernUI.NEON_CYAN);
		searchBar.add(searchLbl);

		routesSearchField = new JTextField(22);
		styleTextField(routesSearchField);
		routesSearchField.setToolTipText("Filter routes by name, type, bind port, target port or limits in real time");
		routesSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			@Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applyRouteFilter(); }
			@Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applyRouteFilter(); }
			@Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyRouteFilter(); }
		});
		searchBar.add(routesSearchField);

		JButton clearFilterBtn = createStyledButton("Reset", new Color(30, 28, 42), ModernUI.NEON_CYAN);
		clearFilterBtn.addActionListener(e -> {
			routesSearchField.setText("");
			applyRouteFilter();
		});
		searchBar.add(clearFilterBtn);

		centerPanel.add(searchBar, BorderLayout.NORTH);
		centerPanel.add(scroll, BorderLayout.CENTER);
		panel.add(centerPanel, BorderLayout.CENTER);

		// Bottom: Action buttons
		JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		actionsPanel.setBackground(ModernUI.BG_DARK);

		JButton syncBtn = createStyledButton("Sincronizar Portas", new Color(15, 80, 50), new Color(140, 255, 180));
		syncBtn.setToolTipText("Sincroniza atomicamente as portas e IPs das rotas com server.properties e loginserver.properties");
		syncBtn.addActionListener(e -> {
			SecurityConfigManager.RouteSyncResult res = SecurityConfigManager.getInstance().syncProxyRoutesWithProperties();
			if (res.success()) {
				JOptionPane.showMessageDialog(frame, "Sincronizacao Atomica Concluida com Sucesso!\n\nRotas e Portas Atualizadas:\n" + String.join("\n", res.updatedProperties()), "Sincronizacao de Portas", JOptionPane.INFORMATION_MESSAGE);
			} else {
				JOptionPane.showMessageDialog(frame, "Falha na sincronizacao: " + res.errorMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
			}
		});
		actionsPanel.add(syncBtn);

		JButton toggleStateBtn = createStyledButton("Alternar Estado da Rota", ModernUI.NEON_BLUE, Color.WHITE);
		toggleStateBtn.setToolTipText("Ativa ou desativa a rota selecionada diretamente no proxy.xml com backup automatico");
		toggleStateBtn.addActionListener(e -> toggleSelectedRouteState());
		actionsPanel.add(toggleStateBtn);

		JButton editInXmlBtn = createStyledButton("Editar no XML", ModernUI.NEON_PURPLE, Color.WHITE);
		editInXmlBtn.setToolTipText("Navega para a aba de edicao direta do XML com a rota selecionada");
		editInXmlBtn.addActionListener(e -> editSelectedRouteInXml());
		actionsPanel.add(editInXmlBtn);

		JButton reloadBtn = createStyledButton("Recarregar Rotas", new Color(30, 28, 42), ModernUI.NEON_CYAN);
		reloadBtn.setToolTipText("Recarrega as rotas diretamente do arquivo proxy.xml em disco");
		reloadBtn.addActionListener(e -> loadRoutesAndXml());
		actionsPanel.add(reloadBtn);

		panel.add(actionsPanel, BorderLayout.SOUTH);

		return panel;
	}

	/**
	 * Tab 5: Direct XML Configuration Editor with validation and live reload.
	 */
	private JPanel createXmlConfigTab() {
		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setBackground(ModernUI.BG_DARK);
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));

		// Top file path & info
		Path path = SecurityConfigManager.getInstance().getProxyXmlPath();
		JPanel headerPanel = new JPanel(new BorderLayout(8, 0));
		headerPanel.setOpaque(false);

		JLabel pathLbl = new JLabel("Arquivo: " + path.toString());
		pathLbl.setFont(new Font("Monospaced", Font.PLAIN, 11));
		pathLbl.setForeground(ModernUI.NEON_CYAN);
		headerPanel.add(pathLbl, BorderLayout.WEST);

		JLabel infoLbl = new JLabel("Edicao direta com backup (.bak), protecao XXE e escrita atomica");
		infoLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
		infoLbl.setForeground(new Color(160, 160, 185));
		headerPanel.add(infoLbl, BorderLayout.EAST);

		panel.add(headerPanel, BorderLayout.NORTH);

		// Center: Code editor
		xmlEditorArea = new JTextArea();
		xmlEditorArea.setBackground(ModernUI.BG_CONSOLE);
		xmlEditorArea.setForeground(new Color(226, 232, 240));
		xmlEditorArea.setCaretColor(ModernUI.NEON_CYAN);
		xmlEditorArea.setFont(new Font("Consolas", Font.PLAIN, 12));
		xmlEditorArea.setTabSize(2);
		xmlEditorArea.setLineWrap(false);

		JScrollPane scroll = new JScrollPane(xmlEditorArea);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getViewport().setBackground(ModernUI.BG_CONSOLE);
		scroll.setBorder(BorderFactory.createLineBorder(new Color(40, 35, 55), 1));
		scroll.getVerticalScrollBar().setUI(new ModernUI.ModernScrollBarUI());
		scroll.getHorizontalScrollBar().setUI(new ModernUI.ModernScrollBarUI());

		// Center subpanel with validation status strip
		JPanel centerSubPanel = new JPanel(new BorderLayout(0, 4));
		centerSubPanel.setOpaque(false);
		centerSubPanel.add(scroll, BorderLayout.CENTER);

		xmlValidationLabel = new JLabel("Clique em 'Validar XML' para verificar a integridade da sintaxe.");
		xmlValidationLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		xmlValidationLabel.setForeground(new Color(140, 140, 160));
		xmlValidationLabel.setBorder(new EmptyBorder(4, 4, 4, 4));
		centerSubPanel.add(xmlValidationLabel, BorderLayout.SOUTH);

		panel.add(centerSubPanel, BorderLayout.CENTER);

		// Bottom: Action buttons
		JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		actionsPanel.setBackground(ModernUI.BG_DARK);

		JButton validateBtn = createStyledButton("Validar XML", new Color(30, 28, 42), ModernUI.NEON_CYAN);
		validateBtn.setToolTipText("Valida a sintaxe XML e previne conflitos de porta sem persistir em disco");
		validateBtn.addActionListener(e -> validateXmlDirect());
		actionsPanel.add(validateBtn);

		JButton reloadBtn = createStyledButton("Recarregar do Disco", new Color(30, 28, 42), new Color(200, 200, 220));
		reloadBtn.setToolTipText("Descarta alteracoes nao salvas e recarrega o arquivo original do disco");
		reloadBtn.addActionListener(e -> loadRoutesAndXml());
		actionsPanel.add(reloadBtn);

		JButton saveBtn = createStyledButton("Salvar & Aplicar", new Color(15, 80, 50), new Color(140, 255, 180));
		saveBtn.setToolTipText("Valida, gera backup .bak, grava atomicamente e reinicia o proxy caso ativo");
		saveBtn.addActionListener(e -> saveXmlDirect());
		actionsPanel.add(saveBtn);

		panel.add(actionsPanel, BorderLayout.SOUTH);

		return panel;
	}

	private void applyRouteFilter() {
		if (routesSorter == null) return;
		String text = (routesSearchField != null) ? routesSearchField.getText().trim() : "";
		if (text.isEmpty()) {
			routesSorter.setRowFilter(null);
		} else {
			routesSorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
		}
	}

	/**
	 * Load proxy.xml into both editor and routes table.
	 */
	private void loadRoutesAndXml() {
		try {
			String content = SecurityConfigManager.getInstance().readProxyXmlContent();
			if (xmlEditorArea != null) {
				xmlEditorArea.setText(content);
				xmlEditorArea.setCaretPosition(0);
			}
			parseAndPopulateRoutesTable(content);
			if (xmlValidationLabel != null) {
				xmlValidationLabel.setText("Conteudo carregado com sucesso do disco.");
				xmlValidationLabel.setForeground(new Color(140, 220, 160));
			}
		} catch (Exception e) {
			if (xmlValidationLabel != null) {
				xmlValidationLabel.setText("Erro ao ler proxy.xml: " + e.getMessage());
				xmlValidationLabel.setForeground(new Color(255, 100, 120));
			}
		}
	}

	private void parseAndPopulateRoutesTable(String xml) {
		if (routesTableModel == null || xml == null || xml.isBlank()) return;
		routesTableModel.setRowCount(0);
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(false);
			Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

			NodeList routeList = doc.getElementsByTagName("route");
			for (int i = 0; i < routeList.getLength(); i++) {
				Element r = (Element) routeList.item(i);
				String name = r.getAttribute("name");
				String type = r.getAttribute("type").toUpperCase();
				String enabledStr = r.hasAttribute("enabled") ? r.getAttribute("enabled") : "true";
				boolean enabled = Boolean.parseBoolean(enabledStr);
				String bind = r.getAttribute("bindHost") + ":" + r.getAttribute("bindPort");
				String target = r.getAttribute("targetHost") + ":" + r.getAttribute("targetPort");

				String maxConn = "—";
				String maxReq = "—";
				NodeList limiters = r.getElementsByTagName("rateLimiter");
				if (limiters.getLength() > 0) {
					Element lim = (Element) limiters.item(0);
					if (lim.hasAttribute("maxConnections")) maxConn = lim.getAttribute("maxConnections");
					if (lim.hasAttribute("maxRequests")) maxReq = lim.getAttribute("maxRequests");
				}

				int bPortNum = 0;
				try { bPortNum = Integer.parseInt(r.getAttribute("bindPort").trim()); } catch (Exception ignored) {}
				int tPortNum = 0;
				try { tPortNum = Integer.parseInt(r.getAttribute("targetPort").trim()); } catch (Exception ignored) {}
				String targetService = SecurityConfigManager.detectTargetService(name, type, bPortNum, tPortNum);

				routesTableModel.addRow(new Object[]{
					name,
					targetService,
					type,
					enabled ? "ATIVO" : "DESATIVADO",
					bind,
					target,
					maxConn,
					maxReq
				});
			}
		} catch (Exception ignored) {
		}
	}

	private void styleRoutesTable(JTable table) {
		table.setBackground(ModernUI.BG_DARK);
		table.setForeground(ModernUI.TEXT_WHITE);
		table.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		table.setRowHeight(24);
		table.setSelectionBackground(ModernUI.NEON_PURPLE);
		table.setSelectionForeground(Color.WHITE);
		table.setGridColor(new Color(30, 28, 42));
		table.setIntercellSpacing(new Dimension(1, 1));

		table.getTableHeader().setBackground(ModernUI.BG_PANEL);
		table.getTableHeader().setForeground(ModernUI.NEON_CYAN);
		table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
		table.getTableHeader().setReorderingAllowed(false);

		for (int col = 0; col < table.getColumnCount(); col++) {
			int alignment = (col == 1 || col == 2 || col == 3 || col == 6 || col == 7) ? SwingConstants.CENTER : SwingConstants.LEFT;
			final int c = col;
			table.getColumnModel().getColumn(col).setCellRenderer(new DefaultTableCellRenderer() {
				@Override
				public Component getTableCellRendererComponent(JTable tbl, Object val, boolean sel, boolean foc, int row, int column) {
					Component comp = super.getTableCellRendererComponent(tbl, val, sel, foc, row, column);
					setHorizontalAlignment(alignment);
					if (sel) {
						comp.setBackground(ModernUI.NEON_PURPLE);
						comp.setForeground(Color.WHITE);
					} else {
						comp.setBackground(row % 2 == 0 ? ModernUI.BG_DARK : new Color(18, 16, 25));
						comp.setForeground(ModernUI.TEXT_WHITE);

						if (c == 0 && val != null) {
							setFont(getFont().deriveFont(Font.BOLD));
							comp.setForeground(ModernUI.NEON_CYAN);
						} else if (c == 1 && val != null) {
							setFont(getFont().deriveFont(Font.BOLD));
							String s = val.toString();
							if (s.contains("GameServer")) comp.setForeground(ModernUI.NEON_CYAN);
							else if (s.contains("LoginServer")) comp.setForeground(new Color(199, 125, 255));
							else if (s.contains("Site")) comp.setForeground(new Color(6, 214, 160));
							else comp.setForeground(new Color(141, 153, 174));
						} else if (c == 2 && val != null) {
							setFont(getFont().deriveFont(Font.BOLD));
							if (val.toString().contains("TCP")) {
								comp.setForeground(ModernUI.NEON_CYAN);
							} else if (val.toString().contains("HTTP")) {
								comp.setForeground(new Color(199, 125, 255));
							}
						} else if (c == 3 && val != null) {
							setFont(getFont().deriveFont(Font.BOLD));
							if ("ATIVO".equals(val.toString())) {
								comp.setForeground(new Color(6, 214, 160)); // Neon Green
							} else {
								comp.setForeground(new Color(255, 77, 109)); // Neon Red
							}
						}
					}
					return comp;
				}
			});
		}
	}

	private void toggleSelectedRouteState() {
		int row = (routesTable != null) ? routesTable.getSelectedRow() : -1;
		if (row < 0) {
			JOptionPane.showMessageDialog(frame, "Selecione uma rota na tabela primeiro.", "Nenhuma Selecao", JOptionPane.WARNING_MESSAGE);
			return;
		}
		int modelRow = routesTable.convertRowIndexToModel(row);
		String routeName = String.valueOf(routesTableModel.getValueAt(modelRow, 0));
		String currentStatus = String.valueOf(routesTableModel.getValueAt(modelRow, 2));
		boolean currentlyActive = "ATIVO".equalsIgnoreCase(currentStatus);
		boolean newActive = !currentlyActive;

		String currentXml = (xmlEditorArea != null && !xmlEditorArea.getText().isBlank())
			? xmlEditorArea.getText()
			: null;
		if (currentXml == null) {
			try {
				currentXml = SecurityConfigManager.getInstance().readProxyXmlContent();
			} catch (Exception e) {
				JOptionPane.showMessageDialog(frame, "Erro ao ler proxy.xml: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
				return;
			}
		}

		java.util.regex.Pattern p = java.util.regex.Pattern.compile("(<route\\b[^>]*\\bname=[\"']" + java.util.regex.Pattern.quote(routeName) + "[\"'][^>]*>)", java.util.regex.Pattern.CASE_INSENSITIVE);
		java.util.regex.Matcher m = p.matcher(currentXml);
		if (!m.find()) {
			JOptionPane.showMessageDialog(frame, "Nao foi possivel localizar a tag <route> para '" + routeName + "' no XML.", "Erro", JOptionPane.ERROR_MESSAGE);
			return;
		}

		String tag = m.group(1);
		String updatedTag;
		if (tag.contains("enabled=")) {
			updatedTag = tag.replaceAll("enabled=[\"'][^\"']*[\"']", "enabled=\"" + newActive + "\"");
		} else {
			updatedTag = tag.substring(0, tag.length() - 1) + " enabled=\"" + newActive + "\">";
		}

		String updatedXml = currentXml.substring(0, m.start()) + updatedTag + currentXml.substring(m.end());

		SecurityConfigManager scm = SecurityConfigManager.getInstance();
		XmlValidationResult res = scm.saveProxyXmlContentSafe(updatedXml);
		if (res.isValid()) {
			if (xmlEditorArea != null) {
				xmlEditorArea.setText(updatedXml);
			}
			parseAndPopulateRoutesTable(updatedXml);
			if (xmlValidationLabel != null) {
				xmlValidationLabel.setText("[OK] Rota '" + routeName + "' alterada para " + (newActive ? "ATIVO" : "DESATIVADO") + " e salva.");
				xmlValidationLabel.setForeground(new Color(6, 214, 160));
			}
			JOptionPane.showMessageDialog(frame, "Rota '" + routeName + "' " + (newActive ? "ATIVADA" : "DESATIVADA") + " com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
		} else {
			JOptionPane.showMessageDialog(frame, "Falha ao salvar XML atualizado: " + res.errorMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void editSelectedRouteInXml() {
		int row = (routesTable != null) ? routesTable.getSelectedRow() : -1;
		String routeName = null;
		if (row >= 0) {
			int modelRow = routesTable.convertRowIndexToModel(row);
			routeName = String.valueOf(routesTableModel.getValueAt(modelRow, 0));
		}
		if (tabbedPane != null) {
			tabbedPane.setSelectedIndex(4); // Switch to XML Config tab
		}
		if (xmlEditorArea != null && routeName != null && !routeName.isBlank()) {
			String text = xmlEditorArea.getText();
			int idx = text.indexOf("\"" + routeName + "\"");
			if (idx >= 0) {
				xmlEditorArea.setCaretPosition(idx);
				xmlEditorArea.select(idx, idx + routeName.length() + 2);
				xmlEditorArea.requestFocusInWindow();
			}
		}
	}

	private boolean validateXmlDirect() {
		if (xmlEditorArea == null) return false;
		String xml = xmlEditorArea.getText();
		XmlValidationResult res = SecurityConfigManager.validateXmlSyntax(xml);
		if (res.isValid()) {
			StringBuilder sb = new StringBuilder("[OK] Sintaxe XML 100% Valida!");
			if (!res.warnings().isEmpty()) {
				sb.append(" (Avisos: ").append(String.join("; ", res.warnings())).append(")");
				if (xmlValidationLabel != null) xmlValidationLabel.setForeground(new Color(255, 183, 3));
			} else {
				if (xmlValidationLabel != null) xmlValidationLabel.setForeground(new Color(6, 214, 160));
			}
			if (xmlValidationLabel != null) xmlValidationLabel.setText(sb.toString());
			parseAndPopulateRoutesTable(xml);
			return true;
		} else {
			if (xmlValidationLabel != null) {
				xmlValidationLabel.setText("[ERRO] Sintaxe Invalida (Linha " + res.lineNumber() + ", Coluna " + res.columnNumber() + "): " + res.errorMessage());
				xmlValidationLabel.setForeground(new Color(255, 77, 109));
			}
			return false;
		}
	}

	private void saveXmlDirect() {
		if (!validateXmlDirect()) {
			JOptionPane.showMessageDialog(frame,
				"Nao e possivel salvar: O XML contem erros de sintaxe!\n\n" + (xmlValidationLabel != null ? xmlValidationLabel.getText() : ""),
				"Erro de Validacao", JOptionPane.ERROR_MESSAGE);
			return;
		}

		String xml = xmlEditorArea.getText();
		SecurityConfigManager scm = SecurityConfigManager.getInstance();
		XmlValidationResult saveRes = scm.saveProxyXmlContentSafe(xml);

		if (saveRes.isValid()) {
			StringBuilder msg = new StringBuilder("Configuracoes salvas com sucesso em:\n")
				.append(scm.getProxyXmlPath().toString())
				.append("\n\nBackup automatico criado: proxy.xml.bak");

			if (!saveRes.warnings().isEmpty()) {
				msg.append("\n\nAvisos:\n");
				for (String w : saveRes.warnings()) {
					msg.append("- ").append(w).append("\n");
				}
			}

			// Check if Native Proxy is currently running
			ProcessManagerService pms = ProcessManagerService.getInstance();
			if (pms.isNativeProxyRunning()) {
				msg.append("\n\nO Proxy Nativo esta em execucao. Deseja reinicia-lo agora para aplicar as novas rotas?");
				int choice = JOptionPane.showConfirmDialog(frame, msg.toString(), "Sucesso & Reinicio do Proxy",
					JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
				if (choice == JOptionPane.YES_OPTION) {
					scm.restartProxyIfRunning();
					JOptionPane.showMessageDialog(frame, "Processo do Proxy Nativo reiniciado em background.", "Proxy Reiniciado", JOptionPane.INFORMATION_MESSAGE);
				}
			} else {
				JOptionPane.showMessageDialog(frame, msg.toString(), "Configuracao Salva", JOptionPane.INFORMATION_MESSAGE);
			}

			parseAndPopulateRoutesTable(xml);
			refreshLogs();
			updateStatus();
		} else {
			JOptionPane.showMessageDialog(frame, "Falha ao salvar XML:\n" + saveRes.errorMessage(), "Erro de Escrita", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Tab 6: Golden Profile & Clean Room Management (SIMD AVX2 Automated Training).
	 */
	private JPanel createGoldenProfileTab() {
		JPanel panel = new JPanel(new BorderLayout(10, 10));
		panel.setBackground(ModernUI.BG_DARK);
		panel.setBorder(new EmptyBorder(10, 12, 10, 12));

		// Top: Banner with 4-Step Auto Training Guide & Telemetry
		JPanel topContainer = new JPanel(new BorderLayout(0, 8));
		topContainer.setOpaque(false);

		// 4-Step Horizontal Guide Cards
		JPanel guidePanel = new JPanel(new GridLayout(1, 4, 8, 0));
		guidePanel.setOpaque(false);
		guidePanel.add(createStepCard("1. Selecao de IP Seguro", "Adicione IPs confiaveis (via Live Events em 1-click ou manual).", ModernUI.NEON_CYAN));
		guidePanel.add(createStepCard("2. Ingestao Automatica", "Netty/Proxy captura pacotes e extrai vetores de 128 dimensoes.", ModernUI.NEON_BLUE));
		guidePanel.add(createStepCard("3. Calibracao Online", "Algoritmo Welford refina media e variancia online (0 -> 100 amostras).", ModernUI.NEON_PURPLE));
		guidePanel.add(createStepCard("4. Protecao SIMD AVX2", "Compara em ~3.4ns. Desvios extremos (Z > 3.5) disparam banimento.", new Color(80, 255, 140)));
		topContainer.add(guidePanel, BorderLayout.NORTH);

		// Telemetry & Progress Bar Banner
		JPanel banner = new JPanel(new BorderLayout(10, 6));
		banner.setBackground(ModernUI.BG_PANEL);
		banner.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(60, 45, 90), 1),
			new EmptyBorder(8, 12, 8, 12)
		));

		JPanel bannerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		bannerLeft.setOpaque(false);

		goldenStatusBadge = new JLabel("Status: NAO CALIBRADO");
		goldenStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
		goldenStatusBadge.setForeground(ModernUI.NEON_PURPLE);
		bannerLeft.add(goldenStatusBadge);

		goldenSamplesLabel = new JLabel("Amostras: 0 / 100");
		goldenSamplesLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		goldenSamplesLabel.setForeground(ModernUI.TEXT_WHITE);
		bannerLeft.add(goldenSamplesLabel);

		JLabel marginLbl = new JLabel("Margem Alvo:");
		marginLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		marginLbl.setForeground(ModernUI.TEXT_WHITE);
		bannerLeft.add(marginLbl);

		SpinnerNumberModel marginModel = new SpinnerNumberModel(CleanRoomManager.getInstance().getCalibrationMargin(), 10, 10000, 10);
		goldenMarginSpinner = new JSpinner(marginModel);
		goldenMarginSpinner.setPreferredSize(new Dimension(65, 22));
		goldenMarginSpinner.setFont(new Font("Segoe UI", Font.BOLD, 11));
		goldenMarginSpinner.setToolTipText("Margem de amostras necessarias para calibrar o Golden Profile");
		goldenMarginSpinner.addChangeListener(e -> {
			int val = (Integer) goldenMarginSpinner.getValue();
			CleanRoomManager.getInstance().setCalibrationMargin(val);
			refreshGoldenProfileTab();
		});
		bannerLeft.add(goldenMarginSpinner);

		goldenAnomaliesLabel = new JLabel("Anomalias Bloqueadas: 0");
		goldenAnomaliesLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
		goldenAnomaliesLabel.setForeground(new Color(255, 100, 120));
		bannerLeft.add(goldenAnomaliesLabel);

		goldenSpeedLabel = new JLabel("SIMD Latency: ~3.4 ns / vetor (AVX2/FMA3)");
		goldenSpeedLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		goldenSpeedLabel.setForeground(ModernUI.NEON_CYAN);
		bannerLeft.add(goldenSpeedLabel);

		banner.add(bannerLeft, BorderLayout.NORTH);

		// Modern Progress Bar
		goldenTrainingProgress = new JProgressBar(0, 100);
		goldenTrainingProgress.setValue(0);
		goldenTrainingProgress.setStringPainted(true);
		goldenTrainingProgress.setString("Progresso de Treinamento Automatico: 0% (Aguardando trafego)");
		goldenTrainingProgress.setForeground(ModernUI.NEON_CYAN);
		goldenTrainingProgress.setBackground(ModernUI.BG_DARK);
		goldenTrainingProgress.setFont(new Font("Segoe UI", Font.BOLD, 10));
		goldenTrainingProgress.setPreferredSize(new Dimension(100, 18));
		goldenTrainingProgress.setBorder(BorderFactory.createLineBorder(new Color(60, 50, 85), 1));
		banner.add(goldenTrainingProgress, BorderLayout.SOUTH);

		topContainer.add(banner, BorderLayout.SOUTH);
		panel.add(topContainer, BorderLayout.NORTH);

		// Center: Split into 2 columns (Left: Clean Room IPs Table | Right: Security & Realtime SIMD Tester)
		JPanel centerPanel = new JPanel(new GridLayout(1, 2, 10, 0));
		centerPanel.setOpaque(false);

		// Column 1 (Left): Verified IPs
		JPanel leftCol = new JPanel(new BorderLayout(0, 6));
		leftCol.setOpaque(false);

		// Add IP bar
		JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		addPanel.setBackground(ModernUI.BG_PANEL);
		addPanel.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ModernUI.NEON_PURPLE, 1),
			" Adicionar IP Confiavel a Area Limpa ",
			javax.swing.border.TitledBorder.LEFT,
			javax.swing.border.TitledBorder.TOP,
			new Font("Segoe UI", Font.BOLD, 11),
			ModernUI.NEON_CYAN
		));

		addPanel.add(new JLabel("IP:") {{ setForeground(ModernUI.TEXT_WHITE); setFont(new Font("Segoe UI", Font.PLAIN, 11)); }});
		goldenNewIpField = new JTextField(12);
		styleTextField(goldenNewIpField);
		addPanel.add(goldenNewIpField);

		addPanel.add(new JLabel("Notas:") {{ setForeground(ModernUI.TEXT_WHITE); setFont(new Font("Segoe UI", Font.PLAIN, 11)); }});
		goldenNotesField = new JTextField(12);
		styleTextField(goldenNotesField);
		addPanel.add(goldenNotesField);

		JButton addIpBtn = createStyledButton("Adicionar", new Color(30, 120, 60), Color.WHITE);
		addIpBtn.addActionListener(e -> {
			String ip = goldenNewIpField.getText().trim();
			String notes = goldenNotesField.getText().trim();
			if (ip.isEmpty()) {
				JOptionPane.showMessageDialog(frame, "Informe um endereco IP valido.", "Aviso", JOptionPane.WARNING_MESSAGE);
				return;
			}
			CleanRoomManager.getInstance().addCleanIp(ip, notes.isEmpty() ? "Adicionado manualmente" : notes);
			goldenNewIpField.setText("");
			goldenNotesField.setText("");
			refreshGoldenProfileTab();
		});
		addPanel.add(addIpBtn);
		leftCol.add(addPanel, BorderLayout.NORTH);

		// Table of Clean IPs
		String[] ipCols = {"IP Verificado", "Data de Inclusao", "Notas / Descricao", "Pacotes Capturados"};
		goldenIpsModel = new DefaultTableModel(ipCols, 0) {
			@Override public boolean isCellEditable(int r, int c) { return false; }
		};
		goldenIpsTable = new JTable(goldenIpsModel);
		styleTable(goldenIpsTable, false);

		JScrollPane ipScroll = new JScrollPane(goldenIpsTable);
		ipScroll.setOpaque(false);
		ipScroll.getViewport().setOpaque(false);
		ipScroll.getViewport().setBackground(ModernUI.BG_DARK);
		ipScroll.setBorder(BorderFactory.createLineBorder(new Color(40, 35, 55), 1));
		ipScroll.getVerticalScrollBar().setUI(new ModernUI.ModernScrollBarUI());
		leftCol.add(ipScroll, BorderLayout.CENTER);

		// Controls for Left Col
		JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
		leftControls.setOpaque(false);
		JButton removeIpBtn = createStyledButton("Remover Selecionado", new Color(120, 30, 40), Color.WHITE);
		removeIpBtn.addActionListener(e -> {
			int row = goldenIpsTable.getSelectedRow();
			if (row >= 0) {
				String ip = (String) goldenIpsModel.getValueAt(row, 0);
				CleanRoomManager.getInstance().removeCleanIp(ip);
				refreshGoldenProfileTab();
			} else {
				JOptionPane.showMessageDialog(frame, "Selecione um IP na tabela para remover.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
			}
		});
		leftControls.add(removeIpBtn);
		leftCol.add(leftControls, BorderLayout.SOUTH);

		centerPanel.add(leftCol);

		// Column 2 (Right): Anti-Poisoning & Interactive SIMD Tester
		JPanel rightCol = new JPanel(new BorderLayout(0, 8));
		rightCol.setOpaque(false);

		// Anti-Poisoning Box
		JPanel securityBox = new JPanel(new BorderLayout(6, 6));
		securityBox.setBackground(ModernUI.BG_PANEL);
		securityBox.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(ModernUI.NEON_PURPLE, 1),
			" Controle Anti-Poisoning & Ciclo de Vida ",
			javax.swing.border.TitledBorder.LEFT,
			javax.swing.border.TitledBorder.TOP,
			new Font("Segoe UI", Font.BOLD, 11),
			ModernUI.NEON_CYAN
		));

		JLabel secDesc = new JLabel("<html><font color='#a0a0b0'><b>Seguranca Ativa:</b> Uma vez calibrado, congele o modelo para impedir que adversarios tentem 'treinar' o perfil inserindo trafego anomalo aos poucos. Quando congelado, nenhum pacote novo altera a media e o desvio padrao.</font></html>");
		secDesc.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		secDesc.setBorder(new EmptyBorder(4, 8, 4, 8));
		securityBox.add(secDesc, BorderLayout.CENTER);

		JPanel secBtnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		secBtnBar.setOpaque(false);

		goldenFreezeBtn = createStyledButton("Congelar Perfil (Anti-Poisoning)", new Color(80, 50, 130), Color.WHITE);
		goldenFreezeBtn.addActionListener(e -> {
			CleanRoomManager mgr = CleanRoomManager.getInstance();
			if (mgr.isFrozen()) {
				mgr.unfreezeProfile();
			} else {
				mgr.freezeProfile();
			}
			refreshGoldenProfileTab();
		});
		secBtnBar.add(goldenFreezeBtn);

		JButton resetProfileBtn = createStyledButton("Resetar Baseline", new Color(60, 60, 75), Color.LIGHT_GRAY);
		resetProfileBtn.addActionListener(e -> {
			int res = JOptionPane.showConfirmDialog(frame,
				"Tem certeza que deseja zerar todas as estatisticas do Perfil Dourado?\nO modelo retornara ao estado inicial de calibracao.",
				"Confirmar Reset", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (res == JOptionPane.YES_OPTION) {
				CleanRoomManager.getInstance().resetProfile();
				refreshGoldenProfileTab();
			}
		});
		secBtnBar.add(resetProfileBtn);

		JButton inspectPacketsBtn = createStyledButton("[Pacotes] Inspecionar Pacotes Capturados", ModernUI.NEON_BLUE, Color.WHITE);
		inspectPacketsBtn.setToolTipText("Visualizar, auditar, selecionar amostras para o modelo ou remover pacotes capturados");
		inspectPacketsBtn.addActionListener(e -> {
			new CapturedPacketsDialog(frame, this::refreshGoldenProfileTab).setVisible(true);
		});
		secBtnBar.add(inspectPacketsBtn);

		securityBox.add(secBtnBar, BorderLayout.SOUTH);
		rightCol.add(securityBox, BorderLayout.NORTH);

		// Realtime SIMD Tester Box
		JPanel testBox = new JPanel(new BorderLayout(6, 6));
		testBox.setBackground(ModernUI.BG_PANEL);
		testBox.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(new Color(60, 50, 85), 1),
			" Testador de Inferencia SIMD em Tempo Real (AVX2 / Vector API) ",
			javax.swing.border.TitledBorder.LEFT,
			javax.swing.border.TitledBorder.TOP,
			new Font("Segoe UI", Font.BOLD, 11),
			ModernUI.NEON_CYAN
		));

		JPanel testInputBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		testInputBar.setOpaque(false);

		testInputBar.add(new JLabel("Testar IP:") {{ setForeground(ModernUI.TEXT_WHITE); setFont(new Font("Segoe UI", Font.PLAIN, 11)); }});
		goldenTestIpField = new JTextField(14);
		styleTextField(goldenTestIpField);
		testInputBar.add(goldenTestIpField);

		JButton runSimdBtn = createStyledButton("Avaliar Vetor SIMD", ModernUI.NEON_PURPLE, Color.WHITE);
		runSimdBtn.addActionListener(e -> evaluateSimdTest());
		testInputBar.add(runSimdBtn);

		testBox.add(testInputBar, BorderLayout.NORTH);

		goldenTestResultLabel = new JLabel("Insira um IP ou selecione um evento no Live Events para testar a classificacao SIMD.");
		goldenTestResultLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		goldenTestResultLabel.setForeground(Color.LIGHT_GRAY);
		goldenTestResultLabel.setBorder(new EmptyBorder(6, 10, 8, 10));
		testBox.add(goldenTestResultLabel, BorderLayout.CENTER);

		// Mechanical Sympathy Details Card
		JPanel techCard = new JPanel(new BorderLayout());
		techCard.setBackground(new Color(25, 22, 35));
		techCard.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(45, 40, 60), 1),
			new EmptyBorder(6, 8, 6, 8)
		));
		JLabel techLabel = new JLabel("<html><font color='#00f0ff'><b>Arquitetura de Alta Performance:</b></font><br>" +
			"<font color='#888899'>• <b>Vetor Contiguo:</b> 128 floats (512 bytes alinhados a 8 linhas de cache de 64 bytes).<br>" +
			"• <b>SIMD AVX2 Loop Unrolling:</b> 8 floats processados por ciclo (FMA3).<br>" +
			"• <b>Algoritmo Online de Welford:</b> Calculo incremental de media e variancia em O(1) sem alocacao de memoria (Zero-GC).</font></html>");
		techLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
		techCard.add(techLabel, BorderLayout.CENTER);
		testBox.add(techCard, BorderLayout.SOUTH);

		rightCol.add(testBox, BorderLayout.CENTER);
		centerPanel.add(rightCol);

		panel.add(centerPanel, BorderLayout.CENTER);

		return panel;
	}

	private JPanel createStepCard(String title, String desc, Color accent) {
		JPanel card = new JPanel(new BorderLayout(4, 2));
		card.setBackground(ModernUI.BG_PANEL);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(45, 40, 65), 1),
			new EmptyBorder(6, 8, 6, 8)
		));

		JLabel tLbl = new JLabel(title);
		tLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
		tLbl.setForeground(accent);
		card.add(tLbl, BorderLayout.NORTH);

		JLabel dLbl = new JLabel("<html><font color='#a0a0b0'>" + desc + "</font></html>");
		dLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
		card.add(dLbl, BorderLayout.CENTER);

		return card;
	}

	public void addIpToGoldenProfileAndSwitch(String ip) {
		if (ip == null || ip.isBlank()) return;
		CleanRoomManager.getInstance().addCleanIp(ip, "Promovido via Live Events");
		if (goldenTestIpField != null) {
			goldenTestIpField.setText(ip);
		}
		refreshGoldenProfileTab();
		if (tabbedPane != null && tabbedPane.getTabCount() >= 6) {
			tabbedPane.setSelectedIndex(5);
		}
		if (statusLabel != null) {
			statusLabel.setText("[Area Limpa] IP " + ip + " adicionado com sucesso a Area Limpa!");
		}
	}

	private void evaluateSimdTest() {
		if (goldenTestIpField == null || goldenTestResultLabel == null) return;
		String ip = goldenTestIpField.getText().trim();
		if (ip.isEmpty()) {
			goldenTestResultLabel.setText("Informe um IP para testar a classificacao vetorial SIMD.");
			goldenTestResultLabel.setForeground(Color.ORANGE);
			return;
		}

		CleanRoomManager mgr = CleanRoomManager.getInstance();
		PacketVector128 testVector = PacketVector128.synthesizeTcp(ip, 128, 0x0E, 25L, 64240);
		long start = System.nanoTime();
		CleanRoomManager.AnomalyDecision dec = mgr.processPacket(testVector);
		long elapsedNanos = System.nanoTime() - start;

		if (dec.isAnomaly()) {
			goldenTestResultLabel.setText(String.format("<html><font color='#ff4d6d'><b>[ALERTA] ANOMALIA DETECTADA em %d ns!</b></font> Z-Score: <b>%.2f</b> | Cosine Dist: <b>%.3f</b> (%s)</html>",
				elapsedNanos, dec.zScore(), dec.cosineDist(), dec.reason()));
		} else {
			goldenTestResultLabel.setText(String.format("<html><font color='#06d6a0'><b>[OK] PADRAO NORMAL (%d ns)</b></font> — Z-Score: <b>%.2f</b> | Cosine Dist: <b>%.3f</b> (%s)</html>",
				elapsedNanos, dec.zScore(), dec.cosineDist(), dec.reason()));
		}
	}

	private void refreshGoldenProfileTab() {
		if (goldenStatusBadge == null || goldenTrainingProgress == null) return;
		CleanRoomManager mgr = CleanRoomManager.getInstance();
		CleanRoomManager.ProfileState state = mgr.getState();

		goldenStatusBadge.setText("Status: " + state.getLabel().toUpperCase());
		if (state == CleanRoomManager.ProfileState.CALIBRATED) {
			goldenStatusBadge.setForeground(new Color(80, 255, 140));
		} else if (state == CleanRoomManager.ProfileState.FROZEN) {
			goldenStatusBadge.setForeground(ModernUI.NEON_PURPLE);
		} else if (state == CleanRoomManager.ProfileState.COLLECTING) {
			goldenStatusBadge.setForeground(ModernUI.NEON_CYAN);
		} else {
			goldenStatusBadge.setForeground(new Color(180, 180, 200));
		}

		long samples = mgr.getTotalSamplesCollected();
		long anomalies = mgr.getTotalAnomaliesDetected();
		int margin = mgr.getCalibrationMargin();
		goldenSamplesLabel.setText(String.format("Amostras: %d / %d", samples, margin));
		goldenAnomaliesLabel.setText(String.format("Anomalias Bloqueadas: %d", anomalies));

		if (goldenMarginSpinner != null && !goldenMarginSpinner.hasFocus()) {
			if (!goldenMarginSpinner.getValue().equals(margin)) {
				goldenMarginSpinner.setValue(margin);
			}
		}

		int progress = (int) Math.min(100, (samples * 100) / Math.max(1, margin));
		goldenTrainingProgress.setValue(progress);
		if (state == CleanRoomManager.ProfileState.CALIBRATED) {
			goldenTrainingProgress.setString(String.format("Perfil Calibrado 100%% (%d amostras) - Inferencia SIMD Ativa", samples));
		} else if (state == CleanRoomManager.ProfileState.FROZEN) {
			goldenTrainingProgress.setString(String.format("Perfil Congelado (Anti-Poisoning) - %d amostras preservadas", samples));
		} else if (state == CleanRoomManager.ProfileState.COLLECTING) {
			goldenTrainingProgress.setString(String.format("Treinando com Trafego Real: %d%% (%d / %d amostras)", progress, samples, margin));
		} else {
			goldenTrainingProgress.setString("Progresso de Treinamento Automatico: 0% (Aguardando trafego dos IPs verificados)");
		}

		if (goldenFreezeBtn != null) {
			if (mgr.isFrozen()) {
				goldenFreezeBtn.setText("Descongelar Perfil");
				goldenFreezeBtn.setBackground(new Color(120, 60, 30));
			} else {
				goldenFreezeBtn.setText("Congelar Perfil (Anti-Poisoning)");
				goldenFreezeBtn.setBackground(new Color(80, 50, 130));
			}
		}

		if (goldenIpsModel != null) {
			goldenIpsModel.setRowCount(0);
			SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss dd/MM");
			for (CleanRoomManager.CleanIpEntry entry : mgr.getCleanIps()) {
				goldenIpsModel.addRow(new Object[]{
					entry.ip(),
					fmt.format(new Date(entry.addedAt())),
					entry.notes(),
					entry.packetCount().get()
				});
			}
		}
	}

	public void closeWindow() {
		if (refreshTimer != null) {
			refreshTimer.stop();
			refreshTimer = null;
		}
		if (liveEventBatchTimer != null) {
			liveEventBatchTimer.stop();
			liveEventBatchTimer = null;
		}
		pendingEvents.clear();
		if (banManager != null && eventListener != null) {
			banManager.removeListener(eventListener);
			eventListener = null;
		}
		if (frame != null) {
			frame.dispose();
			frame = null;
		}
	}
}
