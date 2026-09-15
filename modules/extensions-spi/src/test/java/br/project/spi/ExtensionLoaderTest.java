package br.project.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ExtensionLoaderTest
{
	static final class TestContext implements ExtensionContext
	{
		final EventBus bus = new EventBus();
		final Map<Class<?>, Object> services = new HashMap<>();
		final List<String> logs = new ArrayList<>();
		boolean modsDisabled;
		
		@Override
		public void info(String message)
		{
			logs.add("I:" + message);
		}
		
		@Override
		public void warn(String message)
		{
			logs.add("W:" + message);
		}
		
		@Override
		public EventBus eventBus()
		{
			return bus;
		}
		
		@Override
		public <T> void registerService(Class<T> type, T implementation)
		{
			services.put(type, implementation);
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <T> T getService(Class<T> type)
		{
			return (T) services.get(type);
		}
		
		@Override
		public boolean modsDisabled()
		{
			return modsDisabled;
		}
	}
	
	static final class CountingExtension implements Extension
	{
		static final AtomicInteger ENABLES = new AtomicInteger();
		static final AtomicInteger DISABLES = new AtomicInteger();
		
		@Override
		public String id()
		{
			return "test-counting";
		}
		
		@Override
		public String version()
		{
			return "0.0.1";
		}
		
		@Override
		public void onEnable(ExtensionContext context)
		{
			ENABLES.incrementAndGet();
			context.registerService(String.class, "ok");
		}
		
		@Override
		public void onDisable(ExtensionContext context)
		{
			DISABLES.incrementAndGet();
		}
	}
	
	@Test
	void eventBus_publishDelivers()
	{
		final EventBus bus = new EventBus();
		final AtomicInteger hits = new AtomicInteger();
		bus.subscribe(String.class, s -> hits.incrementAndGet());
		bus.publish("hello");
		assertEquals(1, hits.get());
		assertEquals(1, bus.listenerCount(String.class));
	}
	
	@Test
	void loader_skipsWhenModsDisabled()
	{
		final TestContext ctx = new TestContext();
		ctx.modsDisabled = true;
		final ExtensionLoader loader = new ExtensionLoader();
		final List<String> ids = loader.loadAndEnable(ctx, CountingExtension.class.getClassLoader());
		assertTrue(ids.isEmpty());
		assertFalse(loader.isLoaded("test-counting"));
	}
	
	@Test
	void loader_manualLifecycle()
	{
		CountingExtension.ENABLES.set(0);
		CountingExtension.DISABLES.set(0);
		final TestContext ctx = new TestContext();
		final Extension ext = new CountingExtension();
		ext.onLoad(ctx);
		ext.onEnable(ctx);
		assertEquals(1, CountingExtension.ENABLES.get());
		assertEquals("ok", ctx.getService(String.class));
		ext.onDisable(ctx);
		assertEquals(1, CountingExtension.DISABLES.get());
	}
}
