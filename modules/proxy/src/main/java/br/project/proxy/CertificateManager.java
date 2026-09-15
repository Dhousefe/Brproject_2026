package br.project.proxy;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Caddy-style automatic TLS certificate manager.
 * Generates and persists X.509 self-signed certificates with SAN support (localhost / 127.0.0.1 / LAN IP)
 * when manual certificate paths are omitted or autoTls is enabled.
 */
public final class CertificateManager {

    private static final Logger LOG = LoggerFactory.getLogger(CertificateManager.class);
    private static final Path DEFAULT_CERTS_DIR = Path.of("data", "certs");

    private CertificateManager() {}

    public record TlsCertKey(File certFile, File keyFile) {}

    /**
     * Gets an existing valid self-signed certificate/key pair or generates a new one.
     */
    public static TlsCertKey getOrCreateSelfSigned(String domain) {
        return getOrCreateSelfSigned(domain, DEFAULT_CERTS_DIR);
    }

    /**
     * Gets an existing valid self-signed certificate/key pair or generates a new one in the given directory.
     */
    public static synchronized TlsCertKey getOrCreateSelfSigned(String domain, Path certsDir) {
        if (domain == null || domain.isBlank() || "*".equals(domain) || "0.0.0.0".equals(domain)) {
            domain = "127.0.0.1";
        }

        try {
            if (!Files.exists(certsDir)) {
                Files.createDirectories(certsDir);
            }

            String safeDomain = domain.replaceAll("[^a-zA-Z0-9.-]", "_");
            Path domainCertPath = certsDir.resolve("auto-signed-" + safeDomain + ".crt");
            Path domainKeyPath = certsDir.resolve("auto-signed-" + safeDomain + ".key");
            Path legacyCertPath = certsDir.resolve("auto-signed.crt");
            Path legacyKeyPath = certsDir.resolve("auto-signed.key");

            // Check if domain-specific certificate is still valid and contains required SAN extensions
            if (Files.exists(domainCertPath) && Files.exists(domainKeyPath) 
                && Files.size(domainCertPath) > 0 && Files.size(domainKeyPath) > 0) {
                if (isCertificateValidForDomain(domainCertPath.toFile(), domain)) {
                    LOG.info("[proxy/tls] Reusing existing valid self-signed TLS certificate for '{}': {}", domain, domainCertPath.toAbsolutePath());
                    return new TlsCertKey(domainCertPath.toFile(), domainKeyPath.toFile());
                } else {
                    LOG.warn("[proxy/tls] Existing certificate for '{}' lacks SAN or expired. Regenerating with full SAN...", domain);
                }
            } else if (Files.exists(legacyCertPath) && Files.exists(legacyKeyPath)
                && Files.size(legacyCertPath) > 0 && Files.size(legacyKeyPath) > 0) {
                if (isCertificateValidForDomain(legacyCertPath.toFile(), domain)) {
                    LOG.info("[proxy/tls] Reusing existing valid legacy TLS certificate for '{}': {}", domain, legacyCertPath.toAbsolutePath());
                    return new TlsCertKey(legacyCertPath.toFile(), legacyKeyPath.toFile());
                }
            }

            // Generate new modern certificate with Subject Alternative Names (SAN) via BouncyCastle
            LOG.info("[proxy/tls] Generating automatic self-signed TLS certificate with SAN for '{}'...", domain);
            generateSanCertificate(domain, domainCertPath, domainKeyPath);

            // Copy to legacy fallback path
            Files.copy(domainCertPath, legacyCertPath, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(domainKeyPath, legacyKeyPath, StandardCopyOption.REPLACE_EXISTING);

            LOG.info("[proxy/tls] Self-signed SAN certificate successfully generated and saved to: {}", domainCertPath.toAbsolutePath());
            installCertificateToWindowsRootAsync(domainCertPath.toFile());
            return new TlsCertKey(domainCertPath.toFile(), domainKeyPath.toFile());

        } catch (Exception e) {
            LOG.error("[proxy/tls] Failed to generate automatic TLS certificate for '{}': {}", domain, e.getMessage(), e);
            throw new RuntimeException("Failed to generate automatic TLS certificate: " + e.getMessage(), e);
        }
    }

    private static void generateSanCertificate(String domain, Path certPath, Path keyPath) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();

        long now = System.currentTimeMillis();
        Date startDate = new Date(now - 24 * 3600 * 1000L); // 1 day in past for clock skew
        Date endDate = new Date(now + 10L * 365 * 24 * 3600 * 1000L); // 10 years validity

        BigInteger serialNumber = new BigInteger(64, new SecureRandom());
        X500Name subjectAndIssuer = new X500Name("CN=" + domain + ", O=BrProject Reverse Proxy, OU=Development");

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
            subjectAndIssuer,
            serialNumber,
            startDate,
            endDate,
            subjectAndIssuer,
            keyPair.getPublic()
        );

        // Basic Constraints: isCA = true (allows Windows and Chromium to trust as Root CA)
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyCertSign | KeyUsage.cRLSign));

        // Subject Alternative Names (SAN) - MANDATORY for Chromium / Chrome / Edge!
        List<GeneralName> sanList = new ArrayList<>();
        sanList.add(new GeneralName(GeneralName.dNSName, "localhost"));
        sanList.add(new GeneralName(GeneralName.iPAddress, "127.0.0.1"));
        sanList.add(new GeneralName(GeneralName.iPAddress, "::1"));

        if (!"localhost".equalsIgnoreCase(domain) && !"127.0.0.1".equals(domain) && !"::1".equals(domain)) {
            if (ClientIpExtractor.isValidIp(domain)) {
                sanList.add(new GeneralName(GeneralName.iPAddress, domain));
            } else {
                sanList.add(new GeneralName(GeneralName.dNSName, domain));
            }
        }

        // Automatically discover local LAN IPv4 addresses (e.g., 192.168.100.14) so local network and Android clients match SAN
        try {
            Enumeration<java.net.NetworkInterface> nifs = java.net.NetworkInterface.getNetworkInterfaces();
            while (nifs.hasMoreElements()) {
                var nif = nifs.nextElement();
                if (nif.isLoopback() || !nif.isUp()) continue;
                var addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    var addr = addrs.nextElement();
                    if (addr.isSiteLocalAddress() && addr instanceof java.net.Inet4Address) {
                        sanList.add(new GeneralName(GeneralName.iPAddress, addr.getHostAddress()));
                    }
                }
            }
        } catch (Exception ignored) {}

        GeneralNames subjectAltNames = new GeneralNames(sanList.toArray(new GeneralName[0]));
        certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509CertificateHolder holder = certBuilder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(holder);

        // Write certificate in PEM format
        try (FileWriter fw = new FileWriter(certPath.toFile());
             JcaPEMWriter pemWriter = new JcaPEMWriter(fw)) {
            pemWriter.writeObject(cert);
        }

        // Write private key in PEM format
        try (FileWriter fw = new FileWriter(keyPath.toFile());
             JcaPEMWriter pemWriter = new JcaPEMWriter(fw)) {
            pemWriter.writeObject(keyPair.getPrivate());
        }
    }

    public static boolean isCertificateValid(File certFile) {
        try (InputStream in = new FileInputStream(certFile)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
            cert.checkValidity(new Date());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isCertificateValidForDomain(File certFile, String domain) {
        try (InputStream in = new FileInputStream(certFile)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
            cert.checkValidity(new Date());

            // Chromium / Chrome / Edge strictly REQUIRE Subject Alternative Names (SAN)!
            var sans = cert.getSubjectAlternativeNames();
            if (sans == null || sans.isEmpty()) {
                LOG.warn("[proxy/tls] Certificate lacks Subject Alternative Names (SAN). Rejecting to force SAN regeneration.");
                return false;
            }

            for (var san : sans) {
                if (san.size() >= 2 && domain.equalsIgnoreCase(String.valueOf(san.get(1)))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static boolean isWindowsAdmin() {
        if (!isWindows()) return false;
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c", "net session").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isCertInstalledInWindowsRoot(File certFile) {
        if (!isWindows() || certFile == null || !certFile.exists()) {
            return false;
        }
        try {
            KeyStore ks = KeyStore.getInstance("Windows-ROOT");
            ks.load(null, null);

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate targetCert;
            try (InputStream in = new FileInputStream(certFile)) {
                targetCert = (X509Certificate) cf.generateCertificate(in);
            }

            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate c = ks.getCertificate(alias);
                if (c instanceof X509Certificate x509) {
                    if (x509.getSerialNumber().equals(targetCert.getSerialNumber())
                        || x509.getSubjectX500Principal().equals(targetCert.getSubjectX500Principal())) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void installCertificateToWindowsRootAsync(File certFile) {
        if (!isWindows() || certFile == null || !certFile.exists()) {
            return;
        }
        Thread t = new Thread(() -> installCertificateToWindowsRoot(certFile), "proxy-cert-installer");
        t.setDaemon(true);
        t.start();
    }

    public static boolean installCertificateToWindowsRoot(File certFile) {
        if (!isWindows() || certFile == null || !certFile.exists()) {
            return false;
        }

        if (isCertInstalledInWindowsRoot(certFile)) {
            LOG.info("[proxy/tls] Certificate is already trusted in Windows-ROOT store: {}", certFile.getName());
            return true;
        }

        String certPath = certFile.getAbsolutePath();
        LOG.info("[proxy/tls] Certificate is not in Windows-ROOT. Installing trust store for: {}", certPath);

        try {
            if (isWindowsAdmin()) {
                ProcessBuilder pb = new ProcessBuilder("certutil.exe", "-addstore", "-f", "ROOT", certPath);
                Process p = pb.start();
                boolean finished = p.waitFor(10, TimeUnit.SECONDS);
                if (finished && p.exitValue() == 0) {
                    LOG.info("[proxy/tls] Certificate successfully installed into Windows ROOT store via certutil.");
                    return true;
                }
            } else {
                LOG.info("[proxy/tls] Prompting for Windows Administrator UAC elevation to trust certificate...");
                String powershellCmd = "Start-Process cmd.exe -ArgumentList '/c certutil -addstore -f \"\"ROOT\"\" \"\"" + certPath + "\"\"' -Verb RunAs -Wait";
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", powershellCmd);
                Process p = pb.start();
                p.waitFor(30, TimeUnit.SECONDS);
                if (isCertInstalledInWindowsRoot(certFile)) {
                    LOG.info("[proxy/tls] Certificate successfully installed and verified in Windows ROOT store.");
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.warn("[proxy/tls] Could not automatically install certificate into Windows ROOT store: {}", e.getMessage());
        }
        return false;
    }
}
