package br.project.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Minimal typed event bus for extension-to-extension or host-to-extension signals.
 * Not a replacement for game packet handlers.
 */
public final class EventBus
{
	private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();
	
	public <T> void subscribe(Class<T> eventType, Consumer<T> listener)
	{
		Objects.requireNonNull(eventType, "eventType");
		Objects.requireNonNull(listener, "listener");
		listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
	}
	
	public <T> void unsubscribe(Class<T> eventType, Consumer<T> listener)
	{
		final List<Consumer<?>> list = listeners.get(eventType);
		if (list != null)
			list.remove(listener);
	}
	
	@SuppressWarnings("unchecked")
	public <T> void publish(T event)
	{
		if (event == null)
			return;
		final List<Consumer<?>> list = listeners.get(event.getClass());
		if (list == null || list.isEmpty())
			return;
		for (Consumer<?> consumer : List.copyOf(list))
		{
			try
			{
				((Consumer<T>) consumer).accept(event);
			}
			catch (RuntimeException ex)
			{
				// Host may wrap logging; keep bus resilient
				System.err.println("[EventBus] listener failed for " + event.getClass().getName() + ": " + ex.getMessage());
			}
		}
	}
	
	public void clear()
	{
		listeners.clear();
	}
	
	public int listenerCount(Class<?> eventType)
	{
		final List<Consumer<?>> list = listeners.get(eventType);
		return list == null ? 0 : list.size();
	}
}
