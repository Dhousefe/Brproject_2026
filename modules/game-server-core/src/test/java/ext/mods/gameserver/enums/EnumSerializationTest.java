package ext.mods.gameserver.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ext.mods.gameserver.enums.actors.ClassId;
import ext.mods.gameserver.enums.actors.ClassRace;
import ext.mods.gameserver.enums.actors.ClassType;
import ext.mods.gameserver.enums.actors.Sex;
import ext.mods.gameserver.enums.boats.BoatState;
import ext.mods.gameserver.enums.duels.DuelState;
import ext.mods.gameserver.enums.items.ArmorType;
import ext.mods.gameserver.enums.items.CrystalType;
import ext.mods.gameserver.enums.items.ItemLocation;
import ext.mods.gameserver.enums.items.MaterialType;
import ext.mods.gameserver.enums.items.ShotType;
import ext.mods.gameserver.enums.items.WeaponType;
import ext.mods.gameserver.enums.petitions.PetitionState;
import ext.mods.gameserver.enums.skills.ElementType;
import ext.mods.gameserver.enums.skills.Stats;

/**
 * Verifies enum serialization consistency — both name-based (DB storage) and
 * ordinal-based (packet protocol) representations.
 *
 * <p>These tests guard against silent data corruption when enums are moved to a new
 * module. If a constant name changes, DB lookups using valueOf() will throw.
 * If ordinals shift, network packets will decode to wrong values.</p>
 *
 * <p>Each test is self-contained and does not start the server.</p>
 */
class EnumSerializationTest
{
	// ===== Name-based serialization (used in DB queries via valueOf) =====

	@Test
	@DisplayName("ClassId — valueOf round-trip for key classes")
	void classId_valueOf_roundTrip()
	{
		assertRoundTrip(ClassId.class, "HUMAN_FIGHTER");
		assertRoundTrip(ClassId.class, "WARRIOR");
		assertRoundTrip(ClassId.class, "GLADIATOR");
		assertRoundTrip(ClassId.class, "WARLORD");
		assertRoundTrip(ClassId.class, "PALADIN");
		assertRoundTrip(ClassId.class, "DARK_AVENGER");
		assertRoundTrip(ClassId.class, "TREASURE_HUNTER");
		assertRoundTrip(ClassId.class, "HAWKEYE");
		assertRoundTrip(ClassId.class, "HUMAN_MYSTIC");
	}

	@Test
	@DisplayName("ClassRace — valueOf round-trip")
	void classRace_valueOf_roundTrip()
	{
		for (ClassRace race : ClassRace.values())
		{
			assertRoundTrip(ClassRace.class, race.name());
		}
	}

	@Test
	@DisplayName("Sex — valueOf round-trip")
	void sex_valueOf_roundTrip()
	{
		assertRoundTrip(Sex.class, "MALE");
		assertRoundTrip(Sex.class, "FEMALE");
		assertRoundTrip(Sex.class, "ETC");
	}

	@Test
	@DisplayName("ClassType — valueOf round-trip")
	void classType_valueOf_roundTrip()
	{
		assertRoundTrip(ClassType.class, "FIGHTER");
		assertRoundTrip(ClassType.class, "MYSTIC");
		assertRoundTrip(ClassType.class, "PRIEST");
	}

	@Test
	@DisplayName("ShotType — name matches expected string (protocol identifier)")
	void shotType_names_matchExpected()
	{
		assertEquals("SOULSHOT", ShotType.SOULSHOT.name());
		assertEquals("SPIRITSHOT", ShotType.SPIRITSHOT.name());
		assertEquals("BLESSED_SPIRITSHOT", ShotType.BLESSED_SPIRITSHOT.name());
		assertEquals("FISH_SOULSHOT", ShotType.FISH_SOULSHOT.name());
	}

	@Test
	@DisplayName("ShotType — valueOf round-trip")
	void shotType_valueOf_roundTrip()
	{
		for (ShotType type : ShotType.values())
		{
			assertRoundTrip(ShotType.class, type.name());
		}
	}

	@Test
	@DisplayName("CrystalType — valueOf round-trip for item grade storage")
	void crystalType_valueOf_roundTrip()
	{
		assertRoundTrip(CrystalType.class, "NONE");
		assertRoundTrip(CrystalType.class, "D");
		assertRoundTrip(CrystalType.class, "C");
		assertRoundTrip(CrystalType.class, "B");
		assertRoundTrip(CrystalType.class, "A");
		assertRoundTrip(CrystalType.class, "S");
	}

	@Test
	@DisplayName("ItemLocation — valueOf round-trip for inventory persistence")
	void itemLocation_valueOf_roundTrip()
	{
		for (ItemLocation loc : ItemLocation.values())
		{
			assertRoundTrip(ItemLocation.class, loc.name());
		}
	}

	@Test
	@DisplayName("MaterialType — valueOf round-trip (XML parsing)")
	void materialType_valueOf_roundTrip()
	{
		assertRoundTrip(MaterialType.class, "STEEL");
		assertRoundTrip(MaterialType.class, "FINE_STEEL");
		assertRoundTrip(MaterialType.class, "MITHRIL");
		assertRoundTrip(MaterialType.class, "ORIHARUKON");
		assertRoundTrip(MaterialType.class, "ADAMANTAITE");
		assertRoundTrip(MaterialType.class, "CRYSTAL");
	}

	@Test
	@DisplayName("WeaponType — valueOf round-trip (item XML parsing)")
	void weaponType_valueOf_roundTrip()
	{
		assertRoundTrip(WeaponType.class, "NONE");
		assertRoundTrip(WeaponType.class, "SWORD");
		assertRoundTrip(WeaponType.class, "BLUNT");
		assertRoundTrip(WeaponType.class, "DAGGER");
		assertRoundTrip(WeaponType.class, "BOW");
		assertRoundTrip(WeaponType.class, "POLE");
		assertRoundTrip(WeaponType.class, "DUAL");
		assertRoundTrip(WeaponType.class, "BIGSWORD");
	}

	@Test
	@DisplayName("ArmorType — valueOf round-trip (item XML parsing)")
	void armorType_valueOf_roundTrip()
	{
		for (ArmorType type : ArmorType.values())
		{
			assertRoundTrip(ArmorType.class, type.name());
		}
	}

	@Test
	@DisplayName("ElementType — valueOf round-trip")
	void elementType_valueOf_roundTrip()
	{
		for (ElementType type : ElementType.values())
		{
			assertRoundTrip(ElementType.class, type.name());
		}
	}

	// ===== Ordinal-based serialization (used in packet protocol) =====

	@Test
	@DisplayName("ClassRace — ordinals are used as packet race-id")
	void classRace_ordinals_matchProtocol()
	{
		assertEquals(0, ClassRace.HUMAN.ordinal());
		assertEquals(1, ClassRace.ELF.ordinal());
		assertEquals(2, ClassRace.DARK_ELF.ordinal());
		assertEquals(3, ClassRace.ORC.ordinal());
		assertEquals(4, ClassRace.DWARF.ordinal());
	}

	@Test
	@DisplayName("Sex — ordinal drives the char_create packet")
	void sex_ordinals_matchProtocol()
	{
		assertEquals(0, Sex.MALE.ordinal());
		assertEquals(1, Sex.FEMALE.ordinal());
	}

	@Test
	@DisplayName("WeaponType — ordinals drive bitmask (1 << ordinal)")
	void weaponType_ordinals_matchBitmask()
	{
		for (WeaponType wt : WeaponType.values())
		{
			assertEquals(1 << wt.ordinal(), wt.mask(),
				wt.name() + " mask must equal (1 << ordinal)");
		}
	}

	@Test
	@DisplayName("Paperdoll — ordinals from 0 (NULL) to 18 (HAIRALL) with sequential IDs")
	void paperdoll_ordinals_matchSlotIds()
	{
		// NULL has id=-1, then UNDER=0..HAIRALL=17
		assertEquals(0, Paperdoll.NULL.ordinal());
		assertEquals(-1, Paperdoll.NULL.getId());
		assertEquals(1, Paperdoll.UNDER.ordinal());
		assertEquals(0, Paperdoll.UNDER.getId());
		assertEquals(18, Paperdoll.HAIRALL.ordinal());
		assertEquals(17, Paperdoll.HAIRALL.getId());
	}

	@Test
	@DisplayName("ZoneId — ids match expected zone type codes")
	void zoneId_ids_matchProtocol()
	{
		assertEquals(0, ZoneId.PVP.getId());
		assertEquals(1, ZoneId.PEACE.getId());
		assertEquals(2, ZoneId.SIEGE.getId());
		assertEquals(6, ZoneId.WATER.getId());
		assertEquals(7, ZoneId.JAIL.getId());
		assertEquals(13, ZoneId.TOWN.getId());
		assertEquals(19, ZoneId.BOSS.getId());
	}

	@Test
	@DisplayName("QuestStatus — ordinals drive the quest_status column in DB")
	void questStatus_ordinals_matchDb()
	{
		assertEquals(0, QuestStatus.CREATED.ordinal());
		assertEquals(1, QuestStatus.STARTED.ordinal());
		assertEquals(2, QuestStatus.COMPLETED.ordinal());
	}

	@Test
	@DisplayName("Stats — critical stat names match XML parser expectations")
	void stats_names_matchXmlConfig()
	{
		// Stats has a getValue(name) used by XML skill parsing
		assertEquals("MAX_HP", Stats.MAX_HP.name());
		assertEquals("MAX_MP", Stats.MAX_MP.name());
		assertEquals("MAX_CP", Stats.MAX_CP.name());
		assertEquals("POWER_ATTACK", Stats.POWER_ATTACK.name());
		assertEquals("MAGIC_ATTACK", Stats.MAGIC_ATTACK.name());
		assertEquals("POWER_DEFENCE", Stats.POWER_DEFENCE.name());
		assertEquals("MAGIC_DEFENCE", Stats.MAGIC_DEFENCE.name());
		assertEquals("POWER_ATTACK_SPEED", Stats.POWER_ATTACK_SPEED.name());
		assertEquals("MAGIC_ATTACK_SPEED", Stats.MAGIC_ATTACK_SPEED.name());
	}

	@Test
	@DisplayName("DuelState — round-trip valueOf for all states")
	void duelState_valueOf_roundTrip()
	{
		for (DuelState state : DuelState.values())
		{
			assertRoundTrip(DuelState.class, state.name());
		}
	}

	@Test
	@DisplayName("BoatState — round-trip valueOf for all states")
	void boatState_valueOf_roundTrip()
	{
		for (BoatState state : BoatState.values())
		{
			assertRoundTrip(BoatState.class, state.name());
		}
	}

	@Test
	@DisplayName("PetitionState — round-trip valueOf for all states")
	void petitionState_valueOf_roundTrip()
	{
		for (PetitionState state : PetitionState.values())
		{
			assertRoundTrip(PetitionState.class, state.name());
		}
	}

	@Test
	@DisplayName("Full round-trip: ordinal -> values()[ordinal] is identity for critical enums")
	void ordinalIndex_roundTrip_allCriticalEnums()
	{
		// Ordinal-based lookup used by packet decoders
		for (ClassRace race : ClassRace.values())
		{
			assertSame(race, ClassRace.VALUES[race.ordinal()]);
		}
		for (Sex sex : Sex.values())
		{
			assertSame(sex, Sex.VALUES[sex.ordinal()]);
		}
		for (ShotType shot : ShotType.values())
		{
			assertSame(shot, ShotType.values()[shot.ordinal()]);
		}
		for (Paperdoll slot : Paperdoll.values())
		{
			assertSame(slot, Paperdoll.VALUES[slot.ordinal()]);
		}
		for (QuestStatus status : QuestStatus.values())
		{
			assertSame(status, QuestStatus.VALUES[status.ordinal()]);
		}
		for (ElementType elem : ElementType.values())
		{
			assertSame(elem, ElementType.VALUES[elem.ordinal()]);
		}
	}

	// ===== Helper =====

	/**
	 * Asserts that {@code EnumClass.valueOf(name).name()} equals the original name.
	 * This is the core serialization contract: name() output must round-trip through valueOf().
	 */
	private <E extends Enum<E>> void assertRoundTrip(Class<E> enumClass, String name)
	{
		E constant = Enum.valueOf(enumClass, name);
		assertNotNull(constant, enumClass.getSimpleName() + ".valueOf(\"" + name + "\") returned null");
		assertEquals(name, constant.name(),
			enumClass.getSimpleName() + ".valueOf(\"" + name + "\").name() should equal \"" + name + "\"");
	}
}
