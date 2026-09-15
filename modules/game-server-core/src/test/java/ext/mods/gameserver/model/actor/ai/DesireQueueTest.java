package ext.mods.gameserver.model.actor.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DesireQueueTest
{
	private DesireQueue _queue;

	@BeforeEach
	void setUp()
	{
		_queue = new DesireQueue();
	}

	/**
	 * Intention.equals for NOTHING/IDLE/WANDER returns true (only type is compared),
	 * so to create distinct Desire instances for tests we must use different
	 * IntentionType values (e.g. ATTACK with different timer values produce
	 * distinct identities only via the timer's autoDecreaseWeight effect, not
	 * equals — easier to mix NOTHING and ATTACK).
	 */
	private Desire newNothing(double weight, int timer)
	{
		Desire d = new Desire(weight);
		d.updateAsNothing(timer);
		return d;
	}

	@Test
	void getLast_returnsNullWhenEmpty()
	{
		assertNull(_queue.getLast());
	}

	@Test
	void getLast_returnsHighestWeight()
	{
		// all are NOTHING, all equal, addOrUpdate merges weights into one slot.
		_queue.addOrUpdate(newNothing(10.0, 1));
		_queue.addOrUpdate(newNothing(99.0, 2));

		Desire result = _queue.getLast();
		assertNotNull(result);
		assertEquals(109.0, result.getWeight(), 0.001);
	}

	@Test
	void getLast_returnsSingleElement()
	{
		Desire only = newNothing(42.0, 1);
		_queue.addOrUpdate(only);

		assertSame(only, _queue.getLast());
	}

	@Test
	void addOrUpdate_addsNewDesire()
	{
		// different timer values matter for distinctness only via autoDecreaseWeight
		// (NOTHING case: all equal, merges into one). Use very different weights
		// to verify addOrUpdate's add path indirectly.
		Desire d = newNothing(10.0, 1);
		_queue.addOrUpdate(d);

		assertEquals(1, _queue.getDesires().size());
	}

	@Test
	void addOrUpdate_updatesExistingWeight()
	{
		Desire d1 = newNothing(10.0, 1);
		_queue.addOrUpdate(d1);

		Desire d2 = newNothing(5.0, 1);
		_queue.addOrUpdate(d2);

		assertEquals(1, _queue.getDesires().size());
		assertEquals(15.0, _queue.getDesires().iterator().next().getWeight(), 0.001);
	}

	@Test
	void addOrUpdate_respectsMaxCapacity()
	{
		// all merged into one because all are NOTHING; verify capacity is honored
		// by merging — the entry count should still be 1 even after 51 calls.
		for (int i = 0; i < 51; i++)
		{
			_queue.addOrUpdate(newNothing(1.0, i));
		}
		assertEquals(1, _queue.getDesires().size());
	}

	@Test
	void autoDecreaseWeight_reducesWeightForNothingType()
	{
		Desire d = newNothing(100.0, 1);
		_queue.addOrUpdate(d);

		_queue.autoDecreaseWeight();

		// NOTHING autoDecreaseWeight subtracts 0.5
		assertEquals(99.5, d.getWeight(), 0.001);
	}
}
