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
package ext.mods.gameserver.network.clientpackets.fermata;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

import ext.mods.gameserver.enums.SayType;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.network.GameClient.GameClientState;
import ext.mods.gameserver.network.clientpackets.ChatRequestSupport;
import ext.mods.gameserver.network.clientpackets.L2GameClientPacket;
import ext.mods.gameserver.network.serverpackets.fermata.FermataLinkedItemChat;

/**
 * Pacote RequestItemLinkChat (C->S 0xFD 0x0002) da extensão de protocolo Fermata.
 * Envia links de itens resolvidos estritamente a partir do inventário do remetente autenticado.
 */
public final class RequestItemLinkChat extends L2GameClientPacket
{
	public static final int MIN_PAYLOAD_LENGTH = 16;
	public static final int MAX_PAYLOAD_LENGTH = 512;
	private static final int MAX_TARGET_LENGTH = 32;
	
	private int _channelId;
	private String _text;
	private String _target;
	private int[] _objectIds;
	private boolean _validPayload;
	
	@Override
	protected void readImpl()
	{
		if (!hasValidWireLayout(_buf))
		{
			getClient().closeNow();
			return;
		}
		
		_channelId = readD();
		_text = readS();
		_target = readS();
		final int itemCount = readH();
		_objectIds = new int[itemCount];
		for (int index = 0; index < itemCount; index++)
			_objectIds[index] = readD();
		
		_validPayload = isStructurallyValid(_text, _channelId, _target, _objectIds);
	}
	
	@Override
	protected void runImpl()
	{
		if (getClient().getState() != GameClientState.IN_GAME)
		{
			getClient().closeNow();
			return;
		}
		
		if (!_validPayload)
			return;
		
		final Player player = getClient().getPlayer();
		if (player == null)
			return;
		
		// Resolução estrita no inventário do remetente autenticado (Boundary de autoridade inviolável)
		final List<ItemInstance> items = resolveOwnedItems(_objectIds, player.getInventory()::getItemByObjectId);
		if (items == null)
			return;
		
		for (ItemInstance item : items)
		{
			if (item.getCount() <= 0)
				return;
		}
		
		final String template = _text.replaceAll("\\\\n", "");
		final FermataLinkedItemChat linked = FermataLinkedItemChat.fromOwnedItems(template, items);
		if (linked.getDisplayText().length() > 100 || linked.getLegacyDisplayText().length() > 200)
			return;
		
		try (FermataLinkedItemChat.Scope ignored = FermataLinkedItemChat.activate(linked))
		{
			ChatRequestSupport.dispatch(player, linked.getDisplayText(), _channelId, _target);
		}
	}
	
	public static boolean hasValidWireLayout(ByteBuffer payload)
	{
		if (payload == null || payload.remaining() < MIN_PAYLOAD_LENGTH || payload.remaining() > MAX_PAYLOAD_LENGTH)
			return false;
		
		final ByteBuffer data = payload.asReadOnlyBuffer().order(payload.order());
		data.getInt(); // _channelId
		if (!skipValidUtf16String(data) || !skipValidUtf16String(data) || data.remaining() < Short.BYTES)
			return false;
		
		final int itemCount = data.getShort() & 0xffff;
		return itemCount >= 1 && itemCount <= FermataLinkedItemChat.MAX_LINKS && data.remaining() == itemCount * Integer.BYTES;
	}
	
	static boolean isStructurallyValid(String text, int channelId, String target, int[] objectIds)
	{
		if (text == null || text.isEmpty() || text.length() > 100 || hasUnpairedSurrogate(text))
			return false;
		if (channelId < 0 || channelId >= SayType.VALUES.length || objectIds == null || objectIds.length < 1 || objectIds.length > FermataLinkedItemChat.MAX_LINKS)
			return false;
		
		final String checkedTarget = target == null ? "" : target;
		if (checkedTarget.length() > MAX_TARGET_LENGTH || hasUnpairedSurrogate(checkedTarget) || checkedTarget.indexOf(FermataLinkedItemChat.ITEM_PLACEHOLDER) >= 0)
			return false;
		if (channelId == SayType.TELL.ordinal() ? checkedTarget.isEmpty() : !checkedTarget.isEmpty())
			return false;
		
		int placeholders = 0;
		for (int index = 0; index < text.length(); index++)
		{
			if (text.charAt(index) == FermataLinkedItemChat.ITEM_PLACEHOLDER)
				placeholders++;
		}
		if (placeholders != objectIds.length)
			return false;
		
		final Set<Integer> uniqueObjectIds = new HashSet<>(objectIds.length);
		for (int objectId : objectIds)
		{
			if (objectId <= 0 || !uniqueObjectIds.add(objectId))
				return false;
		}
		return true;
	}
	
	static <T> List<T> resolveOwnedItems(int[] objectIds, IntFunction<T> inventoryLookup)
	{
		if (objectIds == null || inventoryLookup == null)
			return null;
		
		final List<T> items = new ArrayList<>(objectIds.length);
		for (int objectId : objectIds)
		{
			final T item = inventoryLookup.apply(objectId);
			if (item == null)
				return null;
			items.add(item);
		}
		return items;
	}
	
	private static boolean skipValidUtf16String(ByteBuffer data)
	{
		while (data.remaining() >= Character.BYTES)
		{
			final char character = data.getChar();
			if (character == 0)
				return true;
			if (Character.isHighSurrogate(character))
			{
				if (data.remaining() < Character.BYTES || !Character.isLowSurrogate(data.getChar()))
					return false;
			}
			else if (Character.isLowSurrogate(character))
				return false;
		}
		return false;
	}
	
	private static boolean hasUnpairedSurrogate(String value)
	{
		for (int index = 0; index < value.length(); index++)
		{
			final char character = value.charAt(index);
			if (Character.isHighSurrogate(character))
			{
				if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index)))
					return true;
			}
			else if (Character.isLowSurrogate(character))
				return true;
		}
		return false;
	}
	
	@Override
	protected boolean triggersOnActionRequest()
	{
		return false;
	}
}
