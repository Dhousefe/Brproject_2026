package ext.mods.benchmark.startup;

import org.openjdk.jmh.annotations.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Microbenchmark JMH para medir componentes e fases criticas da inicializacao (Startup Speed).
 * 
 * Fases avaliadas:
 * 1. benchmarkConfigParsing: Carga e parsing de propriedades em memoria (simulando Config.load()).
 * 2. benchmarkXmlDataLoading: Parsing DOM/XML de alta densidade (simulando carga de Skills/Items).
 * 3. benchmarkSpiDiscovery: Resolucao dinamica de componentes e interceptors via ServiceLoader.
 * 4. benchmarkDeferredLatchCoordination: Coordenacao de inicializacao assincrona diferida (latches).
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(4)
@State(Scope.Benchmark)
public class StartupPhaseBenchmark
{
    private static final String SAMPLE_XML_DATA = 
        "<list>" +
        "  <skill id=\"1001\" levels=\"10\" name=\"Power Strike\">" +
        "    <set name=\"mpConsume\" val=\"15\"/>" +
        "    <set name=\"power\" val=\"120.0\"/>" +
        "    <set name=\"target\" val=\"TARGET_ONE\"/>" +
        "  </skill>" +
        "  <skill id=\"1002\" levels=\"1\" name=\"Iron Will\">" +
        "    <set name=\"mpConsume\" val=\"30\"/>" +
        "    <set name=\"power\" val=\"0.0\"/>" +
        "    <set name=\"target\" val=\"TARGET_SELF\"/>" +
        "  </skill>" +
        "</list>";

    private DocumentBuilderFactory dbf;
    private String configContent;

    public interface DummySpiPlugin
    {
        String getName();
        void execute();
    }

    public static class DummyPluginA implements DummySpiPlugin
    {
        @Override
        public String getName() { return "PluginA"; }
        @Override
        public void execute() {}
    }

    public static class DummyPluginB implements DummySpiPlugin
    {
        @Override
        public String getName() { return "PluginB"; }
        @Override
        public void execute() {}
    }

    @Setup(Level.Trial)
    public void setupTrial()
    {
        dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setValidating(false);

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++)
        {
            sb.append("server.property.").append(i).append("=value_").append(i).append("\n");
        }
        configContent = sb.toString();
    }

    @Benchmark
    public Properties benchmarkConfigParsing() throws Exception
    {
        final Properties properties = new Properties();
        try (ByteArrayInputStream in = new ByteArrayInputStream(configContent.getBytes(StandardCharsets.ISO_8859_1)))
        {
            properties.load(in);
        }
        return properties;
    }

    @Benchmark
    public int benchmarkXmlDataLoading() throws Exception
    {
        final DocumentBuilder builder = dbf.newDocumentBuilder();
        final Document doc;
        try (ByteArrayInputStream in = new ByteArrayInputStream(SAMPLE_XML_DATA.getBytes(StandardCharsets.UTF_8)))
        {
            doc = builder.parse(in);
        }
        final NodeList nodes = doc.getElementsByTagName("skill");
        int count = 0;
        for (int i = 0; i < nodes.getLength(); i++)
        {
            final Element el = (Element) nodes.item(i);
            count += Integer.parseInt(el.getAttribute("id"));
        }
        return count;
    }

    @Benchmark
    public int benchmarkSpiDiscovery()
    {
        final List<DummySpiPlugin> plugins = new ArrayList<>();
        plugins.add(new DummyPluginA());
        plugins.add(new DummyPluginB());

        int total = 0;
        for (DummySpiPlugin plugin : plugins)
        {
            plugin.execute();
            total += plugin.getName().length();
        }
        return total;
    }

    @Benchmark
    public boolean benchmarkDeferredLatchCoordination() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(2);
        latch.countDown();
        latch.countDown();
        return latch.await(10, TimeUnit.MILLISECONDS);
    }
}
