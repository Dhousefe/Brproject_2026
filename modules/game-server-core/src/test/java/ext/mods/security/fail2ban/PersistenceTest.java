/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
* * L2Brproject is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License.
* * L2Brproject is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* General Public License for more details.
* * You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
* Our main Developers, Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
*/
package ext.mods.security.fail2ban;

import ext.mods.security.fail2ban.core.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence unit tests: SQLite round-trip for bans and events.
 */
public class PersistenceTest {
	
	private Persistence persistence;
	private Path tempDb;
	
	@BeforeEach
	public void setUp() throws Exception {
		tempDb = Files.createTempFile("fail2ban_test", ".sqlite");
		Files.delete(tempDb); // Let Persistence create it
		persistence = new Persistence(tempDb.toString());
	}
	
	@AfterEach
	public void tearDown() throws Exception {
		if (persistence != null) {
			persistence.close();
		}
		Files.deleteIfExists(tempDb);
		Files.deleteIfExists(tempDb.getParent().resolve(tempDb.getFileName() + "-shm"));
		Files.deleteIfExists(tempDb.getParent().resolve(tempDb.getFileName() + "-wal"));
	}
	
	/**
	 * Test: Save ban and load it back.
	 */
	@Test
	public void testSaveAndLoadBan() throws Exception {
		BanRecord ban = BanRecord.temporary("1.2.3.4", "bruteforce", "test ban", 3600000, 1);
		
		// Save
		persistence.saveBan(ban);
		
		// Load
		List<BanRecord> loaded = persistence.loadActiveBans();
		
		assertEquals(1, loaded.size(), "Should have 1 ban");
		BanRecord savedBan = loaded.get(0);
		assertEquals("1.2.3.4", savedBan.ip(), "IP mismatch");
		assertEquals("bruteforce", savedBan.jailName(), "Jail mismatch");
	}
	
	/**
	 * Test: Save multiple bans.
	 */
	@Test
	public void testSaveMultipleBans() throws Exception {
		persistence.saveBan(BanRecord.temporary("1.1.1.1", "bruteforce", "ban1", 3600000, 1));
		persistence.saveBan(BanRecord.temporary("2.2.2.2", "scanner", "ban2", 86400000, 2));
		persistence.saveBan(BanRecord.temporary("3.3.3.3", "bruteforce", "ban3", 3600000, 1));
		
		List<BanRecord> loaded = persistence.loadActiveBans();
		assertEquals(3, loaded.size(), "Should have 3 bans");
	}
	
	/**
	 * Test: Mark ban as unbanned.
	 */
	@Test
	public void testMarkUnbanned() throws Exception {
		String ip = "5.5.5.5";
		persistence.saveBan(BanRecord.temporary(ip, "bruteforce", "test", 3600000, 1));
		
		// Verify it's there
		List<BanRecord> before = persistence.loadActiveBans();
		assertTrue(before.stream().anyMatch(b -> ip.equals(b.ip())), "Ban should exist");
		
		// Mark as unbanned
		persistence.markUnbanned(ip);
		
		// Should not appear in active bans anymore
		List<BanRecord> after = persistence.loadActiveBans();
		assertTrue(after.stream().noneMatch(b -> ip.equals(b.ip())), "Ban should be gone");
	}
	
	/**
	 * Test: Save and load events.
	 */
	@Test
	public void testSaveAndLoadEvents() throws Exception {
		Fail2BanEvent event1 = Fail2BanEvent.failure("1.1.1.1", "bruteforce", "login failed");
		Fail2BanEvent event2 = Fail2BanEvent.ban("1.1.1.1", "bruteforce", 3600000);
		
		// Save
		persistence.appendEvent(event1);
		persistence.appendEvent(event2);
		
		// Load
		List<Fail2BanEvent> loaded = persistence.loadRecentEvents(10);
		
		assertTrue(loaded.size() >= 2, "Should have at least 2 events");
		Fail2BanEvent first = loaded.get(0);
		assertEquals("BAN", first.type(), "Most recent should be BAN");
	}
	
	/**
	 * Test: Load recent events limit.
	 */
	@Test
	public void testLoadRecentEventsLimit() throws Exception {
		// Save 10 events
		for (int i = 0; i < 10; i++) {
			persistence.appendEvent(Fail2BanEvent.failure("1.1.1.1", "bruteforce", "attempt " + i));
		}
		
		// Load only 5
		List<Fail2BanEvent> loaded = persistence.loadRecentEvents(5);
		assertEquals(5, loaded.size(), "Should return exactly 5 events");
	}
}
