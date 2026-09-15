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
 */
package ext.mods.gameserver.network.clientpackets;

public final class SiteJoinPledgeRequest extends L2GameClientPacket
{
	private final int _clanId;
	private final int _pledgeType;
	private final int _leaderObjId;
	
	public SiteJoinPledgeRequest(int clanId, int pledgeType, int leaderObjId)
	{
		_clanId = clanId;
		_pledgeType = pledgeType;
		_leaderObjId = leaderObjId;
	}
	
	public int getClanId()
	{
		return _clanId;
	}
	
	public int getPledgeType()
	{
		return _pledgeType;
	}
	
	public int getLeaderObjId()
	{
		return _leaderObjId;
	}
	
	@Override
	protected void readImpl()
	{
	}
	
	@Override
	protected void runImpl()
	{
	}
}
