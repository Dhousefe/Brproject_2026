package ext.mods.gameserver.model.location;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for model/location classes.
 * Verifies structural integrity before and after extraction.
 */
class LocationContractTest
{
	// ========================================================================
	// Point2D
	// ========================================================================

	@Nested
	@DisplayName("Point2D")
	class Point2DTests
	{
		@Test
		@DisplayName("stores x and y correctly")
		void storesCoordinates()
		{
			final Point2D point = new Point2D(100, 200);

			assertEquals(100, point.getX());
			assertEquals(200, point.getY());
		}

		@Test
		@DisplayName("set modifies x and y in place (mutable pattern)")
		void setMutates()
		{
			final Point2D point = new Point2D(0, 0);
			point.set(50, 75);

			assertEquals(50, point.getX());
			assertEquals(75, point.getY());
		}

		@Test
		@DisplayName("equals works by value")
		void equalsContract()
		{
			final Point2D a = new Point2D(10, 20);
			final Point2D b = new Point2D(10, 20);
			final Point2D c = new Point2D(10, 30);

			assertEquals(a, b);
			assertNotEquals(a, c);
		}

		@Test
		@DisplayName("hashCode consistent with equals")
		void hashCodeConsistent()
		{
			final Point2D a = new Point2D(10, 20);
			final Point2D b = new Point2D(10, 20);

			assertEquals(a.hashCode(), b.hashCode());
		}

		@Test
		@DisplayName("distance2D calculates Euclidean distance")
		void distance2D()
		{
			final Point2D a = new Point2D(0, 0);
			final Point2D b = new Point2D(3, 4);

			assertEquals(5.0, a.distance2D(b), 0.0001);
		}

		@Test
		@DisplayName("clean resets to 0,0")
		void cleanResets()
		{
			final Point2D point = new Point2D(100, 200);
			point.clean();

			assertEquals(0, point.getX());
			assertEquals(0, point.getY());
		}
	}

	// ========================================================================
	// Location
	// ========================================================================

	@Nested
	@DisplayName("Location")
	class LocationTests
	{
		@Test
		@DisplayName("stores x, y, z correctly")
		void storesCoordinates()
		{
			final Location loc = new Location(100, 200, -300);

			assertEquals(100, loc.getX());
			assertEquals(200, loc.getY());
			assertEquals(-300, loc.getZ());
		}

		@Test
		@DisplayName("distance3D calculates correctly")
		void distance3D()
		{
			final Location a = new Location(0, 0, 0);
			final Location b = new Location(1, 2, 2);

			// sqrt(1+4+4) = 3
			assertEquals(3.0, a.distance3D(b), 0.0001);
		}

		@Test
		@DisplayName("distance3D to self is 0")
		void distanceToSelf()
		{
			final Location loc = new Location(50, 60, 70);

			assertEquals(0.0, loc.distance3D(loc), 0.0001);
		}

		@Test
		@DisplayName("isIn3DRadius returns true when within range")
		void isInRadius()
		{
			final Location a = new Location(0, 0, 0);
			final Location b = new Location(5, 0, 0);

			assertTrue(a.isIn3DRadius(b, 10));
			assertFalse(a.isIn3DRadius(b, 3));
		}

		@Test
		@DisplayName("equals and hashCode by value")
		void equalsAndHashCode()
		{
			final Location a = new Location(10, 20, 30);
			final Location b = new Location(10, 20, 30);
			final Location c = new Location(10, 20, 31);

			assertEquals(a, b);
			assertEquals(a.hashCode(), b.hashCode());
			assertNotEquals(a, c);
		}

		@Test
		@DisplayName("set() mutates the current instance (mutable pattern)")
		void setMutates()
		{
			final Location loc = new Location(0, 0, 0);
			loc.set(10, 20, 30);

			assertEquals(10, loc.getX());
			assertEquals(20, loc.getY());
			assertEquals(30, loc.getZ());
		}

		@Test
		@DisplayName("clone creates independent copy")
		void cloneCreatesNewInstance()
		{
			final Location original = new Location(1, 2, 3);
			final Location copy = original.clone();

			assertEquals(original, copy);
			assertNotSame(original, copy);

			// Mutation of copy should not affect original
			copy.set(99, 99, 99);
			assertEquals(1, original.getX());
		}

		@Test
		@DisplayName("extends Point2D")
		void extendsPoint2D()
		{
			assertEquals(Point2D.class, Location.class.getSuperclass());
		}

		@Test
		@DisplayName("DUMMY_LOC is the zero location")
		void dummyLoc()
		{
			assertEquals(0, Location.DUMMY_LOC.getX());
			assertEquals(0, Location.DUMMY_LOC.getY());
			assertEquals(0, Location.DUMMY_LOC.getZ());
		}

		@Test
		@DisplayName("toString contains all coordinates")
		void toStringFormat()
		{
			final Location loc = new Location(111, 222, 333);
			final String s = loc.toString();

			assertTrue(s.contains("111"));
			assertTrue(s.contains("222"));
			assertTrue(s.contains("333"));
		}
	}

	// ========================================================================
	// SpawnLocation
	// ========================================================================

	@Nested
	@DisplayName("SpawnLocation")
	class SpawnLocationTests
	{
		@Test
		@DisplayName("extends Location with heading")
		void extendsLocation()
		{
			assertEquals(Location.class, SpawnLocation.class.getSuperclass());
		}

		@Test
		@DisplayName("stores x, y, z, heading correctly")
		void storesAll()
		{
			final SpawnLocation sl = new SpawnLocation(10, 20, 30, 45000);

			assertEquals(10, sl.getX());
			assertEquals(20, sl.getY());
			assertEquals(30, sl.getZ());
			assertEquals(45000, sl.getHeading());
		}

		@Test
		@DisplayName("equals includes heading in comparison")
		void equalsIncludesHeading()
		{
			final SpawnLocation a = new SpawnLocation(1, 2, 3, 100);
			final SpawnLocation b = new SpawnLocation(1, 2, 3, 100);
			final SpawnLocation c = new SpawnLocation(1, 2, 3, 200);

			assertEquals(a, b);
			assertNotEquals(a, c);
		}

		@Test
		@DisplayName("hashCode includes heading")
		void hashCodeIncludesHeading()
		{
			final SpawnLocation a = new SpawnLocation(1, 2, 3, 100);
			final SpawnLocation b = new SpawnLocation(1, 2, 3, 100);
			final SpawnLocation c = new SpawnLocation(1, 2, 3, 200);

			assertEquals(a.hashCode(), b.hashCode());
			assertNotEquals(a.hashCode(), c.hashCode());
		}

		@Test
		@DisplayName("set(x,y,z,heading) updates all fields")
		void setWithHeading()
		{
			final SpawnLocation sl = new SpawnLocation(0, 0, 0, 0);
			sl.set(5, 10, 15, 500);

			assertEquals(5, sl.getX());
			assertEquals(10, sl.getY());
			assertEquals(15, sl.getZ());
			assertEquals(500, sl.getHeading());
		}

		@Test
		@DisplayName("clone produces independent copy with heading")
		void cloneWithHeading()
		{
			final SpawnLocation original = new SpawnLocation(1, 2, 3, 999);
			final SpawnLocation copy = original.clone();

			assertEquals(original, copy);
			assertNotSame(original, copy);
			assertEquals(999, copy.getHeading());
		}

		@Test
		@DisplayName("DUMMY_SPAWNLOC is the zero spawn location")
		void dummySpawnLoc()
		{
			assertEquals(0, SpawnLocation.DUMMY_SPAWNLOC.getX());
			assertEquals(0, SpawnLocation.DUMMY_SPAWNLOC.getHeading());
		}

		@Test
		@DisplayName("setHeading updates only heading")
		void setHeading()
		{
			final SpawnLocation sl = new SpawnLocation(10, 20, 30, 0);
			sl.setHeading(65535);

			assertEquals(10, sl.getX());
			assertEquals(65535, sl.getHeading());
		}
	}

	// ========================================================================
	// Location hierarchy verification
	// ========================================================================

	@Nested
	@DisplayName("Location hierarchy")
	class HierarchyTests
	{
		@Test
		@DisplayName("BoatLocation extends Location")
		void boatLocation()
		{
			assertEquals(Location.class, BoatLocation.class.getSuperclass());
		}

		@Test
		@DisplayName("ObserverLocation extends Location")
		void observerLocation()
		{
			assertEquals(Location.class, ObserverLocation.class.getSuperclass());
		}

		@Test
		@DisplayName("RadarMarker extends Location")
		void radarMarker()
		{
			assertEquals(Location.class, RadarMarker.class.getSuperclass());
		}

		@Test
		@DisplayName("TeleportLocation extends Location")
		void teleportLocation()
		{
			assertEquals(Location.class, TeleportLocation.class.getSuperclass());
		}

		@Test
		@DisplayName("WalkerLocation extends Location")
		void walkerLocation()
		{
			assertEquals(Location.class, WalkerLocation.class.getSuperclass());
		}

		@Test
		@DisplayName("TowerSpawnLocation extends SpawnLocation")
		void towerSpawnLocation()
		{
			assertEquals(SpawnLocation.class, TowerSpawnLocation.class.getSuperclass());
		}

		@Test
		@DisplayName("all location classes in the package are loadable")
		void allLocationsLoadable()
		{
			final String[] classes = {
				"ext.mods.gameserver.model.location.Point2D",
				"ext.mods.gameserver.model.location.Location",
				"ext.mods.gameserver.model.location.SpawnLocation",
				"ext.mods.gameserver.model.location.BoatLocation",
				"ext.mods.gameserver.model.location.ObserverLocation",
				"ext.mods.gameserver.model.location.RadarMarker",
				"ext.mods.gameserver.model.location.TeleportLocation",
				"ext.mods.gameserver.model.location.TowerSpawnLocation",
				"ext.mods.gameserver.model.location.WalkerLocation",
			};

			for (String className : classes)
			{
				assertDoesNotThrow(() -> Class.forName(className),
					"Class should be loadable: " + className);
			}
		}
	}

	// ========================================================================
	// Mutable pattern verification
	// ========================================================================

	@Nested
	@DisplayName("Mutable pattern documentation")
	class MutabilityTests
	{
		@Test
		@DisplayName("Location uses mutable set() not immutable copy")
		void locationIsMutable()
		{
			final Location loc = new Location(1, 2, 3);
			loc.set(4, 5, 6);

			// Verifies set() modifies in place (mutable pattern)
			assertEquals(4, loc.getX());
			assertEquals(5, loc.getY());
			assertEquals(6, loc.getZ());
		}

		@Test
		@DisplayName("Location _x, _y are protected (accessible to subclasses)")
		void protectedFields() throws Exception
		{
			final Field xField = Point2D.class.getDeclaredField("_x");
			final Field yField = Point2D.class.getDeclaredField("_y");

			assertTrue(Modifier.isProtected(xField.getModifiers()));
			assertTrue(Modifier.isProtected(yField.getModifiers()));
		}

		@Test
		@DisplayName("Location _z is protected")
		void zFieldProtected() throws Exception
		{
			final Field zField = Location.class.getDeclaredField("_z");

			assertTrue(Modifier.isProtected(zField.getModifiers()));
		}

		@Test
		@DisplayName("SpawnLocation _heading is protected volatile")
		void headingFieldProtectedVolatile() throws Exception
		{
			final Field headingField = SpawnLocation.class.getDeclaredField("_heading");

			assertTrue(Modifier.isProtected(headingField.getModifiers()));
			assertTrue(Modifier.isVolatile(headingField.getModifiers()));
		}
	}
}
