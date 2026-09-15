package ext.mods.security.fail2ban.core;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import ext.mods.config.ConfigLogin;
import ext.mods.config.ConfigServer;
import ext.mods.commons.gui.services.ProcessManagerService;

/**
 * Manages dynamic runtime toggling, safe persistence for Fail2Ban, Native Netty Proxy,
 * and dynamic configuration of proxy.xml with strict syntax validation and backup.
 */
public final class SecurityConfigManager {
	private static final Logger LOGGER = Logger.getLogger(SecurityConfigManager.class.getName());
	private static final SecurityConfigManager INSTANCE = new SecurityConfigManager();

	public static SecurityConfigManager getInstance() {
		return INSTANCE;
	}

	private SecurityConfigManager() {
	}

	/**
	 * Resolves server.properties file path with smart multi-location fallback.
	 */
	public Path getServerPropertiesPath() {
		Path[] candidates = new Path[]{
			Paths.get("game", "config", "server.properties"),
			Paths.get("..", "..", "game", "config", "server.properties"),
			Paths.get("config", "server.properties"),
			Paths.get("..", "..", "config", "server.properties"),
			Paths.get("server.properties")
		};
		for (Path p : candidates) {
			if (Files.exists(p)) {
				return p;
			}
		}
		return candidates[0];
	}

	/**
	 * Resolves loginserver.properties file path with smart multi-location fallback.
	 */
	public Path getLoginPropertiesPath() {
		Path[] candidates = new Path[]{
			Paths.get("login", "config", "loginserver.properties"),
			Paths.get("..", "..", "login", "config", "loginserver.properties"),
			Paths.get("config", "loginserver.properties"),
			Paths.get("..", "..", "config", "loginserver.properties"),
			Paths.get("loginserver.properties")
		};
		for (Path p : candidates) {
			if (Files.exists(p)) {
				return p;
			}
		}
		return candidates[0];
	}

	/**
	 * Resolves proxy.xml file path with smart multi-location fallback.
	 */
	public Path getProxyXmlPath() {
		Path[] candidates = new Path[]{
			Paths.get("game", "data", "custom", "mods", "proxy.xml"),
			Paths.get("..", "..", "game", "data", "custom", "mods", "proxy.xml"),
			Paths.get("data", "custom", "mods", "proxy.xml"),
			Paths.get("..", "..", "data", "custom", "mods", "proxy.xml"),
			Paths.get("proxy.xml")
		};
		for (Path p : candidates) {
			if (Files.exists(p)) {
				return p;
			}
		}
		return candidates[0];
	}

	/**
	 * Check if Fail2Ban is active both in configuration and runtime.
	 */
	public boolean isFail2BanEnabled() {
		return ConfigServer.ENABLE_FAIL2BAN && BanManager.getInstance().isEnabled();
	}

	/**
	 * Check if Native Netty Proxy is enabled in config or currently running.
	 */
	public boolean isProxyEnabled() {
		return ConfigServer.ENABLE_NATIVE_PROXY || ProcessManagerService.getInstance().isNativeProxyRunning();
	}

	/**
	 * Dynamic toggle for Fail2Ban: updates memory state, BanManager and .properties files.
	 */
	public synchronized boolean setFail2BanEnabled(boolean enabled) {
		try {
			ConfigServer.ENABLE_FAIL2BAN = enabled;
			ConfigLogin.ENABLE_FAIL2BAN = enabled;

			BanManager.getInstance().setEnabled(enabled);

			Path serverProp = getServerPropertiesPath();
			writePropertySafe(serverProp, "EnableFail2Ban", String.valueOf(enabled));

			Path loginProp = getLoginPropertiesPath();
			writePropertySafe(loginProp, "EnableFail2Ban", String.valueOf(enabled));

			LOGGER.info("[SecurityConfigManager] Fail2Ban toggled to: " + (enabled ? "ENABLED" : "DISABLED"));
			return true;
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to toggle Fail2Ban state", e);
			return false;
		}
	}

	/**
	 * Dynamic toggle for Native Netty Proxy: updates memory state, process lifecycle, and .properties files.
	 */
	public synchronized boolean setProxyEnabled(boolean enabled) {
		try {
			ConfigServer.ENABLE_NATIVE_PROXY = enabled;
			ConfigServer.NATIVE_PROXY_AUTO_START = enabled;
			ConfigLogin.ENABLE_NATIVE_PROXY = enabled;

			ProcessManagerService pms = ProcessManagerService.getInstance();
			if (enabled) {
				syncProxyRoutesWithProperties();
				if (!pms.isNativeProxyRunning()) {
					pms.startNativeProxy(new File("."));
				}
			} else {
				if (pms.isNativeProxyRunning()) {
					pms.stopNativeProxy();
				}
			}

			Path serverProp = getServerPropertiesPath();
			writePropertySafe(serverProp, "EnableNativeProxy", String.valueOf(enabled));
			writePropertySafe(serverProp, "NativeProxyAutoStart", String.valueOf(enabled));

			Path loginProp = getLoginPropertiesPath();
			writePropertySafe(loginProp, "EnableNativeProxy", String.valueOf(enabled));

			LOGGER.info("[SecurityConfigManager] Native Proxy toggled to: " + (enabled ? "ENABLED" : "DISABLED"));
			return true;
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to toggle Native Proxy state", e);
			return false;
		}
	}

	/**
	 * Reads proxy.xml content as string.
	 */
	public String readProxyXmlContent() throws Exception {
		Path path = getProxyXmlPath();
		if (!Files.exists(path)) {
			return "";
		}
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	/**
	 * Result of an XML syntax and semantic validation.
	 */
	
	/**
	 * Definition of a proxy route with its mapped target subsystem.
	 */
	public record ProxyRouteDefinition(
		String name,
		String type,
		boolean enabled,
		String bindHost,
		int bindPort,
		String targetHost,
		int targetPort,
		int maxConnections,
		int maxRequests,
		String targetService,
		boolean autoTls
	) {
		public ProxyRouteDefinition(String name, String type, boolean enabled, String bindHost, int bindPort, String targetHost, int targetPort, int maxConnections, int maxRequests, String targetService) {
			this(name, type, enabled, bindHost, bindPort, targetHost, targetPort, maxConnections, maxRequests, targetService, false);
		}
	}

	/**
	 * Result of an atomic route synchronization with .properties files.
	 */
	public record RouteSyncResult(boolean success, List<String> updatedProperties, List<String> warnings, String errorMessage) {}

	/**
	 * Detects which backend service a proxy route targets (GameServer, LoginServer, Site Ktor).
	 */
	public static String detectTargetService(String name, String type, int bindPort, int targetPort) {
		String lower = (name != null) ? name.toLowerCase() : "";
		if (lower.contains("redirect") || "http-redirect".equalsIgnoreCase(type)) {
			return "Redirect";
		}
		if (lower.contains("game") || bindPort == 7777 || targetPort == 7778) {
			return "GameServer";
		}
		if (lower.contains("login") || lower.contains("auth") || bindPort == 2106 || targetPort == 2107) {
			return "LoginServer";
		}
		if (lower.contains("site") || lower.contains("web") || "http".equalsIgnoreCase(type) || bindPort == 80 || bindPort == 443 || targetPort == 8080) {
			return "Site Ktor";
		}
		return "Custom";
	}

	/**
	 * Parses structured route definitions from proxy.xml content.
	 */
	public static List<ProxyRouteDefinition> parseProxyRoutes(String xmlContent) {
		List<ProxyRouteDefinition> list = new ArrayList<>();
		if (xmlContent == null || xmlContent.isBlank()) {
			return list;
		}
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(false);
			Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlContent)));

			NodeList routeNodes = doc.getElementsByTagName("route");
			for (int i = 0; i < routeNodes.getLength(); i++) {
				Element r = (Element) routeNodes.item(i);
				String name = r.getAttribute("name");
				String type = r.getAttribute("type").toUpperCase();
				String enabledStr = r.hasAttribute("enabled") ? r.getAttribute("enabled") : "true";
				boolean enabled = Boolean.parseBoolean(enabledStr);

				String bindHost = r.hasAttribute("bindHost") ? r.getAttribute("bindHost") : "0.0.0.0";
				int bindPort = r.hasAttribute("bindPort") ? Integer.parseInt(r.getAttribute("bindPort").trim()) : 0;
				String targetHost = r.hasAttribute("targetHost") ? r.getAttribute("targetHost") : "127.0.0.1";
				int targetPort = r.hasAttribute("targetPort") ? Integer.parseInt(r.getAttribute("targetPort").trim()) : 0;

				int maxConn = 0;
				int maxReq = 0;
				NodeList limiters = r.getElementsByTagName("rateLimiter");
				if (limiters.getLength() > 0) {
					Element lim = (Element) limiters.item(0);
					if (lim.hasAttribute("maxConnections")) maxConn = Integer.parseInt(lim.getAttribute("maxConnections").trim());
					if (lim.hasAttribute("maxRequests")) maxReq = Integer.parseInt(lim.getAttribute("maxRequests").trim());
				}

				boolean autoTls = false;
				if (r.hasAttribute("autoTls")) {
					autoTls = Boolean.parseBoolean(r.getAttribute("autoTls"));
				} else if (r.hasAttribute("tlsCert") && "auto".equalsIgnoreCase(r.getAttribute("tlsCert"))) {
					autoTls = true;
				}

				String targetService = detectTargetService(name, type, bindPort, targetPort);
				list.add(new ProxyRouteDefinition(name, type, enabled, bindHost, bindPort, targetHost, targetPort, maxConn, maxReq, targetService, autoTls));
			}
		} catch (Exception ignored) {
		}
		return list;
	}

	public record XmlValidationResult(boolean isValid, String errorMessage, int lineNumber, int columnNumber, List<String> warnings) {
		public static XmlValidationResult ok(List<String> warnings) {
			return new XmlValidationResult(true, null, -1, -1, warnings);
		}

		public static XmlValidationResult error(String message, int line, int col) {
			return new XmlValidationResult(false, message, line, col, List.of());
		}
	}

	/**
	 * Strictly validates XML syntax with XXE protection and checks for port collisions.
	 */
	public static XmlValidationResult validateXmlSyntax(String xmlContent) {
		if (xmlContent == null || xmlContent.isBlank()) {
			return XmlValidationResult.error("XML content is empty", 1, 1);
		}

		List<String> warnings = new ArrayList<>();
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			try {
				factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
				factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			} catch (Exception ignored) {
			}
			factory.setNamespaceAware(false);

			DocumentBuilder builder = factory.newDocumentBuilder();
			final List<SAXParseException> parseErrors = new ArrayList<>();
			builder.setErrorHandler(new ErrorHandler() {
				@Override public void warning(SAXParseException exception) { warnings.add("Warning: " + exception.getMessage()); }
				@Override public void error(SAXParseException exception) { parseErrors.add(exception); }
				@Override public void fatalError(SAXParseException exception) { parseErrors.add(exception); }
			});

			Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));

			if (!parseErrors.isEmpty()) {
				SAXParseException err = parseErrors.get(0);
				return XmlValidationResult.error(err.getMessage(), err.getLineNumber(), err.getColumnNumber());
			}

			// Semantic check: Port collisions among enabled routes and internal loopback protection
			NodeList routeNodes = doc.getElementsByTagName("route");
			Set<String> boundPorts = new HashSet<>();
			for (int i = 0; i < routeNodes.getLength(); i++) {
				Element route = (Element) routeNodes.item(i);
				boolean enabled = !route.hasAttribute("enabled") || Boolean.parseBoolean(route.getAttribute("enabled"));
				if (enabled) {
					String name = route.getAttribute("name");
					if (route.hasAttribute("bindPort")) {
						String port = route.getAttribute("bindPort").trim();
						if (!boundPorts.add(port)) {
							warnings.add("Conflito de Porta: A porta " + port + " esta vinculada a mais de uma rota ativa (rota: " + name + ").");
						}
					}
					if (route.hasAttribute("bindPort") && route.hasAttribute("targetPort")) {
						try {
							int bPort = Integer.parseInt(route.getAttribute("bindPort").trim());
							int tPort = Integer.parseInt(route.getAttribute("targetPort").trim());
							String bHost = route.hasAttribute("bindHost") ? route.getAttribute("bindHost").trim() : "0.0.0.0";
							String tHost = route.hasAttribute("targetHost") ? route.getAttribute("targetHost").trim() : "127.0.0.1";

							boolean isLocalBind = bHost.equals("0.0.0.0") || bHost.equals("127.0.0.1") || bHost.equals("localhost") || bHost.equals("*");
							boolean isLocalTarget = tHost.equals("0.0.0.0") || tHost.equals("127.0.0.1") || tHost.equals("localhost") || tHost.equals("*");

							if (bPort == tPort && isLocalBind && isLocalTarget) {
								return XmlValidationResult.error(
									"Conflito Fatal de Porta na Rota '" + name + "': bindPort (" + bPort + ") e targetPort (" + tPort + ") sao identicos no mesmo host local (" + bHost + " -> " + tHost + "). O proxy e o servico causarao BindException (Address already in use).",
									1, 1
								);
							}
						} catch (NumberFormatException ignored) {
						}
					}
				}
			}

			return XmlValidationResult.ok(warnings);
		} catch (SAXParseException spe) {
			return XmlValidationResult.error(spe.getMessage(), spe.getLineNumber(), spe.getColumnNumber());
		} catch (Exception e) {
			return XmlValidationResult.error("Erro no parser XML: " + e.getMessage(), 1, 1);
		}
	}

	/**
	 * Safely saves XML content with pre-validation, automatic backup (.bak), and atomic write.
	 */
	public synchronized XmlValidationResult saveProxyXmlContentSafe(String xmlContent) {
		XmlValidationResult valResult = validateXmlSyntax(xmlContent);
		if (!valResult.isValid()) {
			return valResult;
		}

		Path path = getProxyXmlPath();
		try {
			if (path.getParent() != null) {
				Files.createDirectories(path.getParent());
			}

			// 1. Create backup if original exists
			if (Files.exists(path)) {
				Path bakPath = path.resolveSibling(path.getFileName().toString() + ".bak");
				Files.copy(path, bakPath, StandardCopyOption.REPLACE_EXISTING);
			}

			// 2. Write to temporary file in same directory for atomic rename
			Path tmpPath = path.resolveSibling(path.getFileName().toString() + ".tmp");
			Files.writeString(tmpPath, xmlContent, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

			// 3. Atomic move to target
			try {
				Files.move(tmpPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception moveEx) {
				// Fallback to normal replace if file system does not support ATOMIC_MOVE across partitions
				Files.move(tmpPath, path, StandardCopyOption.REPLACE_EXISTING);
			}

			LOGGER.info("[SecurityConfigManager] proxy.xml saved safely. Backup created.");
			syncProxyRoutesWithProperties();
			return valResult;
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to save proxy.xml safely", e);
			return XmlValidationResult.error("Falha de I/O ao salvar arquivo: " + e.getMessage(), 1, 1);
		}
	}

	/**
	 * Restarts the native proxy process if currently active to apply new routes.
	 */
	public void restartProxyIfRunning() {
		ProcessManagerService pms = ProcessManagerService.getInstance();
		if (pms.isNativeProxyRunning()) {
			new Thread(() -> {
				try {
					pms.stopNativeProxy();
					Thread.sleep(800);
					pms.startNativeProxy(new File("."));
				} catch (Exception e) {
					LOGGER.log(Level.WARNING, "Error during proxy restart", e);
				}
			}, "Proxy-Restart-Thread").start();
		}
	}

	/**
	 * Safely writes or replaces a key-value pair in a .properties file.
	 * Preserves all surrounding comments, sections, indentation and line ordering.
	 */
	
	/**
	 * Safely writes or replaces multiple key-value pairs in a .properties file in a single atomic batch.
	 * Preserves all surrounding comments, sections, indentation and line ordering.
	 */
	public static boolean writePropertiesSafe(Path configFile, Map<String, String> propertiesToUpdate) {
		if (configFile == null || propertiesToUpdate == null || propertiesToUpdate.isEmpty()) {
			return false;
		}

		try {
			if (!Files.exists(configFile)) {
				if (configFile.getParent() != null) {
					Files.createDirectories(configFile.getParent());
				}
				StringBuilder sb = new StringBuilder();
				for (Map.Entry<String, String> entry : propertiesToUpdate.entrySet()) {
					sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append(System.lineSeparator());
				}
				Files.writeString(configFile, sb.toString(),
					StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				return true;
			}

			List<String> lines = Files.readAllLines(configFile, StandardCharsets.UTF_8);
			List<String> newLines = new ArrayList<>(lines.size() + propertiesToUpdate.size() + 2);
			Set<String> updatedKeys = new HashSet<>();

			for (String line : lines) {
				boolean matched = false;
				for (Map.Entry<String, String> entry : propertiesToUpdate.entrySet()) {
					String key = entry.getKey();
					Pattern pattern = Pattern.compile("^\\s*(" + Pattern.quote(key) + "\\s*[=:])\\s*(.*?)\\s*$", Pattern.CASE_INSENSITIVE);
					Matcher matcher = pattern.matcher(line);
					if (matcher.find()) {
						line = matcher.group(1) + " " + entry.getValue();
						updatedKeys.add(key.toLowerCase());
						matched = true;
						break;
					}
				}
				newLines.add(line);
			}

			// Append keys that did not exist yet in the file
			for (Map.Entry<String, String> entry : propertiesToUpdate.entrySet()) {
				if (!updatedKeys.contains(entry.getKey().toLowerCase())) {
					if (!newLines.isEmpty() && !newLines.get(newLines.size() - 1).trim().isEmpty()) {
						newLines.add("");
					}
					newLines.add(entry.getKey() + " = " + entry.getValue());
				}
			}

			// Atomic write via temp file
			Path tmpPath = configFile.resolveSibling(configFile.getFileName().toString() + ".tmp");
			Files.write(tmpPath, newLines, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			try {
				Files.move(tmpPath, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception moveEx) {
				Files.move(tmpPath, configFile, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to safely write batch properties in " + configFile, e);
			return false;
		}
	}

	/**
	 * Atomically synchronizes proxy.xml route configurations with server.properties and loginserver.properties,
	 * updating in-memory runtime ConfigServer and ConfigLogin settings with zero port collisions.
	 */
	public synchronized RouteSyncResult syncProxyRoutesWithProperties() {
		try {
			String xml = readProxyXmlContent();
			if (xml == null || xml.isBlank()) {
				return new RouteSyncResult(false, List.of(), List.of(), "proxy.xml esta vazio ou nao encontrado");
			}

			XmlValidationResult val = validateXmlSyntax(xml);
			if (!val.isValid()) {
				return new RouteSyncResult(false, List.of(), val.warnings(), "Validacao de XML falhou: " + val.errorMessage());
			}

			List<ProxyRouteDefinition> routes = parseProxyRoutes(xml);
			Map<String, String> serverUpdates = new LinkedHashMap<>();
			Map<String, String> loginUpdates = new LinkedHashMap<>();
			List<String> updatedKeys = new ArrayList<>();

			boolean proxyActive = isProxyEnabled();

			for (ProxyRouteDefinition route : routes) {
				if (!route.enabled()) continue;

				if ("GameServer".equals(route.targetService())) {
					serverUpdates.put("GameserverPort", String.valueOf(route.bindPort()));
					serverUpdates.put("GameServerInternalPort", String.valueOf(route.targetPort()));
					ConfigServer.GAMESERVER_PORT = route.bindPort();
					ConfigServer.GAMESERVER_INTERNAL_PORT = route.targetPort();
					updatedKeys.add("GameServer: PublicPort=" + route.bindPort() + ", InternalPort=" + route.targetPort());
				} else if ("LoginServer".equals(route.targetService())) {
					loginUpdates.put("LoginserverPort", String.valueOf(route.bindPort()));
					loginUpdates.put("LoginServerInternalPort", String.valueOf(route.targetPort()));
					ConfigLogin.LOGINSERVER_PORT = route.bindPort();
					ConfigLogin.LOGINSERVER_INTERNAL_PORT = route.targetPort();
					updatedKeys.add("LoginServer: PublicPort=" + route.bindPort() + ", InternalPort=" + route.targetPort());
				} else if ("Site Ktor".equals(route.targetService())) {
					serverUpdates.put("SiteBindPort", String.valueOf(route.targetPort()));
					serverUpdates.put("KtorWebServerPort", String.valueOf(route.targetPort()));
					serverUpdates.put("SiteBindHost", route.targetHost());
					serverUpdates.put("KtorWebServerIp", route.targetHost());
					serverUpdates.put("SiteWsPushPort", String.valueOf(route.targetPort()));
					serverUpdates.put("SiteWsPushHost", route.targetHost());
					updatedKeys.add("Site Ktor: PublicPort=" + route.bindPort() + ", InternalPort=" + route.targetPort() + ", Host=" + route.targetHost());
				}
			}

			if (proxyActive) {
				serverUpdates.put("EnableNativeProxy", "true");
				serverUpdates.put("NativeProxyAutoStart", "false");
				loginUpdates.put("EnableNativeProxy", "true");
			}

			// Atomic writes to disk
			boolean sOk = serverUpdates.isEmpty() || writePropertiesSafe(getServerPropertiesPath(), serverUpdates);
			boolean lOk = loginUpdates.isEmpty() || writePropertiesSafe(getLoginPropertiesPath(), loginUpdates);

			if (sOk && lOk) {
				LOGGER.info("[SecurityConfigManager] Sincronizacao atomica concluida com sucesso. Rotas atualizadas: " + updatedKeys);
				return new RouteSyncResult(true, updatedKeys, val.warnings(), null);
			} else {
				return new RouteSyncResult(false, updatedKeys, val.warnings(), "Falha ao gravar em server.properties ou loginserver.properties");
			}
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Erro durante sincronizacao atomica de rotas", e);
			return new RouteSyncResult(false, List.of(), List.of(), e.getMessage());
		}
	}

	public static boolean writePropertySafe(Path configFile, String key, String newValue) {
		if (configFile == null) {
			return false;
		}

		try {
			if (!Files.exists(configFile)) {
				if (configFile.getParent() != null) {
					Files.createDirectories(configFile.getParent());
				}
				Files.writeString(configFile, key + " = " + newValue + System.lineSeparator(),
					StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				return true;
			}

			List<String> lines = Files.readAllLines(configFile, StandardCharsets.UTF_8);
			List<String> newLines = new ArrayList<>(lines.size() + 2);
			boolean valueReplaced = false;

			Pattern pattern = Pattern.compile("^\\s*(" + Pattern.quote(key) + "\\s*[=:])\\s*(.*?)\\s*$", Pattern.CASE_INSENSITIVE);

			for (String line : lines) {
				Matcher matcher = pattern.matcher(line);
				if (matcher.find()) {
					line = matcher.group(1) + " " + newValue;
					valueReplaced = true;
				}
				newLines.add(line);
			}

			if (!valueReplaced) {
				if (!newLines.isEmpty() && !newLines.get(newLines.size() - 1).trim().isEmpty()) {
					newLines.add("");
				}
				newLines.add(key + " = " + newValue);
			}

			Files.write(configFile, newLines, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			return true;
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to safely write property '" + key + "' in " + configFile, e);
			return false;
		}
	}
}
