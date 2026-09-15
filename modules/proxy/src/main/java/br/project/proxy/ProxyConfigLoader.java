package br.project.proxy;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses game/data/custom/mods/proxy.xml into a ProxyConfig.
 *
 * Expected shape:
 * <list>
 *   <reverseProxy enabled="true">
 *     <route name="l2-login" type="tcp" enabled="true"
 *            bindHost="0.0.0.0" bindPort="2106"
 *            targetHost="127.0.0.1" targetPort="2106"
 *            preserveHost="true" addForwardedHeaders="false">
 *       <rateLimiter enabled="true" windowSeconds="60"
 *                    maxConnections="30" maxRequests="0"/>
 *     </route>
 *     <route name="api" type="http" ...>
 *       <rateLimiter enabled="true" windowSeconds="60"
 *                    maxConnections="120" maxRequests="120"/>
 *     </route>
 *   </reverseProxy>
 * </list>
 *
 * Backward compatibility: if the document has <gameserver> blocks (the legacy
 * ProxyDataLoader shape used by LoginServer), we synthesize a TCP route per
 * <proxy> child. The legacy shape is supported but the explicit
 * <reverseProxy><route/></reverseProxy> form is preferred.
 */
public final class ProxyConfigLoader {

    private static final DocumentBuilderFactory FACTORY = createFactory();

    private ProxyConfigLoader() {}

    private static DocumentBuilderFactory createFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (Exception ignored) {
            // best-effort hardening; older JVMs may lack some features
        }
        factory.setNamespaceAware(false);
        return factory;
    }

    public static ProxyConfig load(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            return load(in);
        }
    }

    public static ProxyConfig load(InputStream input) throws Exception {
        DocumentBuilder builder = FACTORY.newDocumentBuilder();
        org.w3c.dom.Document doc = builder.parse(new InputSource(input));
        return parse(doc);
    }

    public static ProxyConfig load(Reader reader) throws Exception {
        DocumentBuilder builder = FACTORY.newDocumentBuilder();
        org.w3c.dom.Document doc = builder.parse(new InputSource(reader));
        return parse(doc);
    }

    private static ProxyConfig parse(org.w3c.dom.Document doc) {
        Element root = doc.getDocumentElement();
        if (root == null) {
            return new ProxyConfig(false, List.of());
        }

        Element reverseProxy = firstChild(root, "reverseProxy");
        if (reverseProxy != null) {
            return parseReverseProxy(reverseProxy);
        }

        // Legacy fallback: <gameserver>/<proxy ... />
        return parseLegacyGameserver(root);
    }

    private static ProxyConfig parseReverseProxy(Element reverseProxy) {
        boolean enabled = parseBoolAttr(reverseProxy, "enabled", true);
        List<ProxyRoute> routes = new ArrayList<>();
        NodeList routeNodes = reverseProxy.getElementsByTagName("route");
        for (int i = 0; i < routeNodes.getLength(); i++) {
            Element routeEl = (Element) routeNodes.item(i);
            ProxyRoute route = parseRoute(routeEl);
            if (route != null) {
                routes.add(route);
            }
        }
        return new ProxyConfig(enabled, List.copyOf(routes));
    }

    private static ProxyRoute parseRoute(Element routeEl) {
        String name = routeEl.getAttribute("name");
        if (name == null || name.isBlank()) {
            name = String.format(Locale.ROOT, "%s:%d",
                routeEl.getAttribute("bindHost"),
                parseIntAttr(routeEl, "bindPort", 0));
        }
        ProxyRoute.RouteType type = ProxyRoute.RouteType.fromXml(routeEl.getAttribute("type"));
        boolean enabled = parseBoolAttr(routeEl, "enabled", true);
        String bindHost = attrOr(routeEl, "bindHost", "0.0.0.0");
        int bindPort = parseIntAttr(routeEl, "bindPort", 0);
        String targetHost = attrOr(routeEl, "targetHost", "127.0.0.1");
        int targetPort = parseIntAttr(routeEl, "targetPort", bindPort);
        boolean preserveHost = parseBoolAttr(routeEl, "preserveHost", true);
        boolean addForwarded = parseBoolAttr(routeEl, "addForwardedHeaders", false);

        RateLimitConfig rateLimit = parseRateLimit(firstChild(routeEl, "rateLimiter"), type);

        String tlsCert = routeEl.getAttribute("tlsCert");
        String tlsKey = routeEl.getAttribute("tlsKey");
        if (tlsCert != null && tlsCert.isBlank()) tlsCert = null;
        if (tlsKey != null && tlsKey.isBlank()) tlsKey = null;

        boolean autoTls = parseBoolAttr(routeEl, "autoTls", false) || "auto".equalsIgnoreCase(tlsCert);
        String tlsDomain = routeEl.getAttribute("tlsDomain");
        if (tlsDomain != null && tlsDomain.isBlank()) tlsDomain = null;
        int maxContentLength = parseIntAttr(routeEl, "maxContentLength", ProxyRoute.DEFAULT_MAX_CONTENT_LENGTH);
        if (maxContentLength <= 0) {
            maxContentLength = ProxyRoute.DEFAULT_MAX_CONTENT_LENGTH;
        }

        if (bindPort <= 0 || targetPort <= 0) {
            return null;
        }

        return new ProxyRoute(name.trim(), type, enabled, bindHost.trim(), bindPort,
            targetHost.trim(), targetPort, preserveHost, addForwarded, rateLimit, tlsCert, tlsKey, autoTls, tlsDomain, maxContentLength);
    }

    private static RateLimitConfig parseRateLimit(Element rl, ProxyRoute.RouteType type) {
        if (rl == null) {
            return RateLimitConfig.disabled();
        }
        boolean enabled = parseBoolAttr(rl, "enabled", false);
        int windowSeconds = Math.max(1, parseIntAttr(rl, "windowSeconds", 60));
        int maxConnections = parseIntAttr(rl, "maxConnections", 0);
        int maxRequests = parseIntAttr(rl, "maxRequests", 0);
        if (!enabled) {
            return RateLimitConfig.disabled();
        }
        return new RateLimitConfig(true, windowSeconds, maxConnections, maxRequests);
    }

    private static ProxyConfig parseLegacyGameserver(Element root) {
        List<ProxyRoute> routes = new ArrayList<>();
        NodeList gsList = root.getElementsByTagName("gameserver");
        for (int i = 0; i < gsList.getLength(); i++) {
            Element gs = (Element) gsList.item(i);
            NodeList proxyList = gs.getElementsByTagName("proxy");
            for (int j = 0; j < proxyList.getLength(); j++) {
                Element p = (Element) proxyList.item(j);
                String name = "legacy-" + p.getAttribute("proxyServerId");
                String bindHost = attrOr(p, "proxyHost", "0.0.0.0");
                int bindPort = parseIntAttr(p, "proxyPort", 0);
                int targetPort = parseIntAttr(p, "targetPort", 7777);
                if (bindPort <= 0) continue;
                routes.add(new ProxyRoute(
                    name,
                    ProxyRoute.RouteType.TCP,
                    true,
                    bindHost,
                    bindPort,
                    "127.0.0.1",
                    targetPort,
                    false,
                    false,
                    RateLimitConfig.defaultsFor(ProxyRoute.RouteType.TCP),
                    null,
                    null
                ));
            }
        }
        return new ProxyConfig(!routes.isEmpty(), List.copyOf(routes));
    }

    private static Element firstChild(Element parent, String tag) {
        NodeList children = parent.getElementsByTagName(tag);
        if (children.getLength() == 0) return null;
        org.w3c.dom.Node n = children.item(0);
        return (n instanceof Element e) ? e : null;
    }

    private static String attrOr(Element el, String name, String fallback) {
        String v = el.getAttribute(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private static int parseIntAttr(Element el, String name, int fallback) {
        String v = el.getAttribute(name);
        if (v == null || v.isBlank()) return fallback;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean parseBoolAttr(Element el, String name, boolean fallback) {
        String v = el.getAttribute(name);
        if (v == null || v.isBlank()) return fallback;
        return Boolean.parseBoolean(v.trim());
    }

    public static String defaultFilePath() {
        if (Files.exists(Path.of("game/data/custom/mods/proxy.xml"))) {
            return "game/data/custom/mods/proxy.xml";
        }
        if (Files.exists(Path.of("data/custom/mods/proxy.xml"))) {
            return "data/custom/mods/proxy.xml";
        }
        return "game/data/custom/mods/proxy.xml";
    }

    public static String readUtf8(InputStream in) throws Exception {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
