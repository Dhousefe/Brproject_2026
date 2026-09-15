package ext.mods.gameserver.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ext.mods.gameserver.enums.actors.ClassId;
import ext.mods.gameserver.enums.actors.ClassRace;
import ext.mods.gameserver.enums.actors.ClassType;
import ext.mods.gameserver.enums.actors.Sex;
import ext.mods.gameserver.enums.bbs.ForumAccess;
import ext.mods.gameserver.enums.bbs.ForumType;
import ext.mods.gameserver.enums.boats.BoatState;
import ext.mods.gameserver.enums.duels.DuelState;
import ext.mods.gameserver.enums.items.CrystalType;
import ext.mods.gameserver.enums.items.ItemLocation;
import ext.mods.gameserver.enums.items.ShotType;
import ext.mods.gameserver.enums.petitions.PetitionState;
import ext.mods.gameserver.enums.skills.ElementType;
import ext.mods.gameserver.enums.skills.Stats;

/**
 * Verifies enum contracts are intact BEFORE and AFTER the game-enums module extraction.
 *
 * <p>This test guards the enum layer against accidental breaking changes during the
 * refactor that splits {@code game-server-core} into a dedicated {@code game-enums} module.
 * The enum layer is used throughout the codebase — its contract (constants, ordinals,
 * names) must not change.</p>
 *
 * <p>Each test is self-contained and does not start the server.</p>
 */
class EnumIntegrityTest
{
	// ===== Actors (CharacterClass, Race, Sex) =====

	@Test
	@DisplayName("CharacterClass — human fighter lineage is intact (HUMAN_FIGHTER -> WARRIOR -> GLADIATOR)")
	void characterClass_humanFighterLineage_isIntact()
	{
		assertNotNull(ClassId.HUMAN_FIGHTER, "HUMAN_FIGHTER must exist");
		assertNotNull(ClassId.WARRIOR, "WARRIOR must exist");
		assertNotNull(ClassId.GLADIATOR, "GLADIATOR must exist");
		assertNotNull(ClassId.KNIGHT, "KNIGHT must exist");
		assertNotNull(ClassId.PALADIN, "PALADIN must exist");
		assertNotNull(ClassId.ROGUE, "ROGUE must exist");
		assertNotNull(ClassId.HUMAN_MYSTIC, "HUMAN_MYSTIC must exist");

		// lineage
		assertSame(ClassId.HUMAN_FIGHTER, ClassId.WARRIOR.getParent());
		assertSame(ClassId.WARRIOR, ClassId.GLADIATOR.getParent());
		assertSame(ClassId.HUMAN_FIGHTER, ClassId.KNIGHT.getParent());
	}

	@Test
	@DisplayName("CharacterClass — ordinal 0 is HUMAN_FIGHTER (DB primary key)")
	void characterClass_ordinalZero_isHumanFighter()
	{
		// ClassId.ordinal() == 0 is a contract — character_create.sql stores base class by ordinal.
		assertEquals(0, ClassId.HUMAN_FIGHTER.ordinal(),
			"HUMAN_FIGHTER must remain at ordinal 0 (used by DB primary key storage)");
	}

	@Test
	@DisplayName("CharacterClass — count matches the L2 class roster")
	void characterClass_count_isStable()
	{
		// 119 classes: base + 1st/2nd/3rd profession changes (Interlude roster).
		assertEquals(119, ClassId.values().length,
			"ClassId enum count changed — possible class was lost during extraction");
	}

	@Test
	@DisplayName("Race — five races exist in canonical order")
	void race_fiveRacesExist()
	{
		assertEquals(5, ClassRace.values().length);
		assertNotNull(ClassRace.HUMAN);
		assertNotNull(ClassRace.ELF);
		assertNotNull(ClassRace.DARK_ELF);
		assertNotNull(ClassRace.ORC);
		assertNotNull(ClassRace.DWARF);

		assertEquals(0, ClassRace.HUMAN.ordinal());
		assertEquals(1, ClassRace.ELF.ordinal());
		assertEquals(2, ClassRace.DARK_ELF.ordinal());
		assertEquals(3, ClassRace.ORC.ordinal());
		assertEquals(4, ClassRace.DWARF.ordinal());
	}

	@Test
	@DisplayName("Sex — three values MALE/FEMALE/ETC")
	void sex_threeValues()
	{
		assertEquals(3, Sex.values().length);
		assertEquals(0, Sex.MALE.ordinal());
		assertEquals(1, Sex.FEMALE.ordinal());
		assertEquals(2, Sex.ETC.ordinal());
	}

	@Test
	@DisplayName("ClassType — FIGHTER/MYSTIC/PRIEST exist")
	void classType_threeTypesExist()
	{
		assertEquals(3, ClassType.values().length);
		assertNotNull(ClassType.FIGHTER);
		assertNotNull(ClassType.MYSTIC);
		assertNotNull(ClassType.PRIEST);
	}

	// ===== Items =====

	@Test
	@DisplayName("ShotType — bitmask ordinals drive packet protocol")
	void shotType_ordinalsAreStable()
	{
		assertEquals(4, ShotType.values().length);
		assertEquals(0, ShotType.SOULSHOT.ordinal());
		assertEquals(1, ShotType.SPIRITSHOT.ordinal());
		assertEquals(2, ShotType.BLESSED_SPIRITSHOT.ordinal());
		assertEquals(3, ShotType.FISH_SOULSHOT.ordinal());

		// mask is derived from ordinal — verify bit math stays consistent
		assertEquals(1 << 0, ShotType.SOULSHOT.getMask());
		assertEquals(1 << 1, ShotType.SPIRITSHOT.getMask());
		assertEquals(1 << 2, ShotType.BLESSED_SPIRITSHOT.getMask());
		assertEquals(1 << 3, ShotType.FISH_SOULSHOT.getMask());
	}

	@Test
	@DisplayName("CrystalType — grade ordinals (NONE/D/C/B/A/S) drive item crystal-id mapping")
	void crystalType_gradeIdsAreStable()
	{
		assertEquals(6, CrystalType.values().length);
		assertEquals(0, CrystalType.NONE.getId());
		assertEquals(1, CrystalType.D.getId());
		assertEquals(2, CrystalType.C.getId());
		assertEquals(3, CrystalType.B.getId());
		assertEquals(4, CrystalType.A.getId());
		assertEquals(5, CrystalType.S.getId());
	}

	@Test
	@DisplayName("ItemLocation — 8 storage slots (VOID -> FREIGHT)")
	void itemLocation_eightSlots()
	{
		assertEquals(8, ItemLocation.values().length);
		assertEquals(0, ItemLocation.VOID.ordinal());
		assertEquals(1, ItemLocation.INVENTORY.ordinal());
		assertEquals(2, ItemLocation.PAPERDOLL.ordinal());
		assertEquals(3, ItemLocation.WAREHOUSE.ordinal());
		assertEquals(4, ItemLocation.CLANWH.ordinal());
		assertEquals(5, ItemLocation.PET.ordinal());
		assertEquals(6, ItemLocation.PET_EQUIP.ordinal());
		assertEquals(7, ItemLocation.FREIGHT.ordinal());
	}

	// ===== Skills =====

	@Test
	@DisplayName("Stats — ordinals match stat-table index (127 stats)")
	void stats_countAndOrderAreStable()
	{
		assertEquals(127, Stats.values().length,
			"Stats enum count changed — stat bonus calculation would silently break");
		assertNotNull(Stats.MAX_HP);
		assertNotNull(Stats.MAX_MP);
		assertNotNull(Stats.MAX_CP);
		assertNotNull(Stats.POWER_DEFENCE);
		assertNotNull(Stats.MAGIC_ATTACK);
	}

	@Test
	@DisplayName("ElementType — 7 attack elements (NONE + WIND/FIRE/WATER/EARTH/HOLY/DARK) + VALAKAS")
	void elementType_eightElements()
	{
		assertEquals(8, ElementType.values().length);
		assertEquals(0, ElementType.NONE.ordinal());
		assertEquals(1, ElementType.WIND.ordinal());
		assertEquals(2, ElementType.FIRE.ordinal());
		assertEquals(3, ElementType.WATER.ordinal());
		assertEquals(4, ElementType.EARTH.ordinal());
		assertEquals(5, ElementType.HOLY.ordinal());
		assertEquals(6, ElementType.DARK.ordinal());
		assertEquals(7, ElementType.VALAKAS.ordinal());

		// ElementType pairs with Stats — verify the wiring is intact
		assertSame(Stats.WIND_POWER, ElementType.WIND.getAtkStat());
		assertSame(Stats.WIND_RES, ElementType.WIND.getResStat());
		assertSame(Stats.FIRE_POWER, ElementType.FIRE.getAtkStat());
		assertSame(Stats.HOLY_POWER, ElementType.HOLY.getAtkStat());
	}

	// ===== Misc top-level enums =====

	@Test
	@DisplayName("QuestStatus — CREATED/STARTED/COMPLETED")
	void questStatus_threeStates()
	{
		assertEquals(3, QuestStatus.values().length);
		assertEquals(0, QuestStatus.CREATED.ordinal());
		assertEquals(1, QuestStatus.STARTED.ordinal());
		assertEquals(2, QuestStatus.COMPLETED.ordinal());
	}

	@Test
	@DisplayName("OlympiadState — COMPETITION/VALIDATION")
	void olympiadState_twoStates()
	{
		assertEquals(2, OlympiadState.values().length);
		assertEquals(0, OlympiadState.COMPETITION.ordinal());
		assertEquals(1, OlympiadState.VALIDATION.ordinal());
	}

	@Test
	@DisplayName("MessageType — 4 disconnect codes (EXPELLED/LEFT/NONE/DISCONNECTED)")
	void messageType_fourStates()
	{
		assertEquals(4, MessageType.values().length);
		assertNotNull(MessageType.EXPELLED);
		assertNotNull(MessageType.LEFT);
		assertNotNull(MessageType.NONE);
		assertNotNull(MessageType.DISCONNECTED);
	}

	@Test
	@DisplayName("IntentionType — AI intentions all exist (16 intentions)")
	void intentionType_sixteenIntentions()
	{
		assertEquals(16, IntentionType.values().length,
			"IntentionType count must remain 16 — AI engine switch table is sized to this");
		assertNotNull(IntentionType.ATTACK);
		assertNotNull(IntentionType.CAST);
		assertNotNull(IntentionType.IDLE);
		assertNotNull(IntentionType.MOVE_TO);
		assertNotNull(IntentionType.FOLLOW);
		assertNotNull(IntentionType.PICK_UP);
		assertNotNull(IntentionType.USE_ITEM);
	}

	@Test
	@DisplayName("Paperdoll — 18 slots + NULL = 19 total")
	void paperdoll_nineteenSlots()
	{
		assertEquals(19, Paperdoll.values().length,
			"Paperdoll slot count changed — equipped-item packet layout would break");
		assertEquals(18, Paperdoll.TOTAL_SLOTS,
			"TOTAL_SLOTS constant must remain 18 (client-side slot count)");
		assertEquals(-1, Paperdoll.NULL.getId());
		assertEquals(0, Paperdoll.UNDER.getId());
		assertEquals(17, Paperdoll.HAIRALL.getId());
	}

	@Test
	@DisplayName("SayType — 19 chat channels")
	void sayType_nineteenChannels()
	{
		assertEquals(19, SayType.values().length,
			"SayType channels changed — chat packet decoder routes by ordinal");
		assertNotNull(SayType.ALL);
		assertNotNull(SayType.SHOUT);
		assertNotNull(SayType.TELL);
		assertNotNull(SayType.PARTY);
		assertNotNull(SayType.CLAN);
		assertNotNull(SayType.TRADE);
	}

	// ===== Sub-package enums (BBS, boats, duels, petitions) =====

	@Test
	@DisplayName("BoatState — 5 boat lifecycle states")
	void boatState_fiveStates()
	{
		assertEquals(5, BoatState.values().length);
		assertEquals(0, BoatState.PREPARING.ordinal());
		assertEquals(1, BoatState.EXECUTE_ROUTE.ordinal());
		assertEquals(2, BoatState.SEALING.ordinal());
		assertEquals(3, BoatState.READY_TO_MOVE_TO_DOCK.ordinal());
		assertEquals(4, BoatState.DOCKED.ordinal());
	}

	@Test
	@DisplayName("DuelState — 6 duel states (NO_DUEL..INTERRUPTED)")
	void duelState_sixStates()
	{
		assertEquals(6, DuelState.values().length);
		assertNotNull(DuelState.NO_DUEL);
		assertNotNull(DuelState.DUELLING);
		assertNotNull(DuelState.WINNER);
		assertNotNull(DuelState.INTERRUPTED);
	}

	@Test
	@DisplayName("PetitionState — 5 states (PENDING..CLOSED)")
	void petitionState_fiveStates()
	{
		assertEquals(5, PetitionState.values().length);
		assertEquals(0, PetitionState.PENDING.ordinal());
		assertEquals(1, PetitionState.ACCEPTED.ordinal());
		assertEquals(2, PetitionState.REJECTED.ordinal());
		assertEquals(3, PetitionState.CANCELLED.ordinal());
		assertEquals(4, PetitionState.CLOSED.ordinal());
	}

	@Test
	@DisplayName("ForumAccess — BBS access tiers exist")
	void forumAccess_tiersExist()
	{
		// ForumAccess is small; just verify it is loadable and has at least 2 tiers
		assertTrue(ForumAccess.values().length >= 2);
		assertTrue(ForumType.values().length >= 1);
	}
}
