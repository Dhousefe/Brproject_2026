package ext.mods.gameserver.data.repository;

import java.util.List;

/**
 * Persistence port for the clan table facade.
 *
 * <p>The game-facing {@code ClanTable} keeps the in-memory clan model and
 * business rules. This port owns the database representation used while
 * loading clans, restoring wars, maintaining the ladder and deleting clans.</p>
 */
public interface ClanStore
{
	List<ClanRecord> loadClans();

	List<WarRecord> loadWars(long now);

	void saveWar(int clanId1, int clanId2);

	void updateWarExpiry(int clanId1, int clanId2, long expiryTime);

	void deleteWar(int clanId1, int clanId2);

	void deleteClan(ClanDeletion deletion);

	List<Integer> loadRankedClanIds(int limit);

	void clearGraduates();

	record ClanRecord(int clanId, int leaderId, String name, int level, int castleId, int allyId, String allyName,
		long allyPenaltyExpiryTime, int allyPenaltyType, long charPenaltyExpiryTime, long dissolvingExpiryTime,
		int crestId, int crestLargeId, int allyCrestId, int reputationScore, int auctionBiddedAt, int newLeaderId,
		String notice, boolean noticeEnabled, String introduction, String graduates)
	{
	}

	record WarRecord(int clanId1, int clanId2, long expiryTime)
	{
	}

	record ClanDeletion(int clanId, int castleId)
	{
	}
}
