package br.project.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

class CertificateManagerTest {

    @Test
    void generatesAndPersistsSelfSignedCertificate(@TempDir Path tempDir) throws Exception {
        CertificateManager.TlsCertKey pair = CertificateManager.getOrCreateSelfSigned("localhost", tempDir);

        assertNotNull(pair.certFile());
        assertNotNull(pair.keyFile());
        assertTrue(pair.certFile().exists(), "Certificate file must exist");
        assertTrue(pair.keyFile().exists(), "Private key file must exist");
        assertTrue(pair.certFile().length() > 0, "Certificate file must not be empty");
        assertTrue(pair.keyFile().length() > 0, "Private key file must not be empty");

        // Verify X.509 validity
        try (InputStream in = new FileInputStream(pair.certFile())) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
            assertNotNull(cert);
            assertTrue(cert.getSubjectX500Principal().getName().contains("localhost"));
        }

        // Test reuse
        long certModified = pair.certFile().lastModified();
        CertificateManager.TlsCertKey reused = CertificateManager.getOrCreateSelfSigned("localhost", tempDir);
        assertEquals(pair.certFile().getAbsolutePath(), reused.certFile().getAbsolutePath());
        assertEquals(certModified, reused.certFile().lastModified(), "Existing valid certificate must be reused");
    }

    @Test
    void parsesAutoTlsAttributesFromXml() throws Exception {
        String xml = """
            <list>
              <reverseProxy enabled="true">
                <route name="site-https" type="http" enabled="true" bindHost="0.0.0.0" bindPort="443"
                       targetHost="127.0.0.1" targetPort="8080" autoTls="true">
                  <rateLimiter enabled="false" windowSeconds="60" maxConnections="0" maxRequests="0"/>
                </route>
                <route name="site-legacy" type="http" enabled="true" bindHost="0.0.0.0" bindPort="8443"
                       targetHost="127.0.0.1" targetPort="8080" tlsCert="auto">
                  <rateLimiter enabled="false" windowSeconds="60" maxConnections="0" maxRequests="0"/>
                </route>
              </reverseProxy>
            </list>
            """;

        ProxyConfig config = ProxyConfigLoader.load(new StringReader(xml));
        assertEquals(2, config.routes().size());

        ProxyRoute r1 = config.routes().get(0);
        assertTrue(r1.tlsEnabled(), "autoTls=true must enable TLS");
        assertTrue(r1.isAutoTls(), "autoTls=true must report isAutoTls");

        ProxyRoute r2 = config.routes().get(1);
        assertTrue(r2.tlsEnabled(), "tlsCert=auto must enable TLS");
        assertTrue(r2.isAutoTls(), "tlsCert=auto must report isAutoTls");
    }

    @Test
    void generatesAndValidatesCertificateForIpAddress(@TempDir Path tempDir) throws Exception {
        CertificateManager.TlsCertKey pair = CertificateManager.getOrCreateSelfSigned("127.0.0.1", tempDir);

        assertNotNull(pair.certFile());
        assertNotNull(pair.keyFile());
        assertTrue(pair.certFile().exists());
        assertTrue(pair.keyFile().exists());

        // Validate domain validation helper
        assertTrue(CertificateManager.isCertificateValidForDomain(pair.certFile(), "127.0.0.1"));
        assertFalse(CertificateManager.isCertificateValidForDomain(pair.certFile(), "otherdomain.com"));
    }

    @Test
    void generatesCertificateWithSubjectAlternativeNames(@TempDir Path tempDir) throws Exception {
        CertificateManager.TlsCertKey pair = CertificateManager.getOrCreateSelfSigned("127.0.0.1", tempDir);

        try (InputStream in = new FileInputStream(pair.certFile())) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
            var sans = cert.getSubjectAlternativeNames();
            assertNotNull(sans, "Subject Alternative Names (SAN) must not be null");
            assertFalse(sans.isEmpty(), "SAN must contain entries for Chromium compliance");

            boolean hasIpSan = false;
            boolean hasDnsSan = false;
            for (var entry : sans) {
                int type = (Integer) entry.get(0);
                String val = String.valueOf(entry.get(1));
                if (type == 7 && "127.0.0.1".equals(val)) hasIpSan = true;
                if (type == 2 && "localhost".equalsIgnoreCase(val)) hasDnsSan = true;
            }
            assertTrue(hasIpSan, "Must contain iPAddress SAN for 127.0.0.1");
            assertTrue(hasDnsSan, "Must contain dNSName SAN for localhost");
        }
    }

    @Test
    void generateDefaultProductionCerts() {
        CertificateManager.TlsCertKey pair = CertificateManager.getOrCreateSelfSigned("127.0.0.1", java.nio.file.Path.of("data", "certs"));
        assertTrue(pair.certFile().exists());
        assertTrue(pair.keyFile().exists());
    }
}
