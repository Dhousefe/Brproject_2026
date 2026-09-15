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
package ext.mods.gameserver.network.serverpackets.fermata;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import ext.mods.gameserver.enums.SayType;
import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.network.FermataProtocol;

/**
 * Metadados de itens linkados no chat do cliente Fermata associados a uma operacao de chat normal.
 * O contexto é mantido em escopo de ThreadLocal durante o despacho, garantindo que
 * Say2 ordinario nao possa forjar pacotes ricos e evitando duplicacao de regras de canais.
 */
public final class FermataLinkedItemChat
{
	public static final char ITEM_PLACEHOLDER = '\ufffc';
	public static final String LEGACY_UNAVAILABLE_SUFFIX = " (Item Linking Unavailable)";
	public static final int MAX_LINKS = 4;
	
	private static final ThreadLocal<FermataLinkedItemChat> ACTIVE = new ThreadLocal<>();
	
	private final String _template;
	private final String _displayText;
	private final String _legacyDisplayText;
	private final List<ItemSnapshot> _items;
	
	FermataLinkedItemChat(String template, String displayText, String legacyDisplayText, List<ItemSnapshot> items)
	{
		_template = template;
		_displayText = displayText;
		_legacyDisplayText = legacyDisplayText;
		_items = List.copyOf(items);
	}
	
	public static FermataLinkedItemChat fromOwnedItems(String template, List<ItemInstance> items)
	{
		if (template == null || items == null || items.isEmpty() || items.size() > MAX_LINKS)
			throw new IllegalArgumentException("Mensagem com link requer entre 1 e 4 itens.");
		
		final List<ItemSnapshot> snapshots = new ArrayList<>(items.size());
		final StringBuilder display = new StringBuilder(template.length() + items.size() * 16);
		final StringBuilder legacyDisplay = new StringBuilder(template.length() + items.size() * 48);
		int itemIndex = 0;
		
		for (int index = 0; index < template.length(); index++)
		{
			final char character = template.charAt(index);
			if (character != ITEM_PLACEHOLDER)
			{
				display.append(character);
				legacyDisplay.append(character);
				continue;
			}
			
			if (itemIndex >= items.size())
				throw new IllegalArgumentException("O texto possui mais marcadores do que itens fornecidos.");
			
			final ItemInstance item = items.get(itemIndex++);
			if (item == null || item.getCount() <= 0)
				throw new IllegalArgumentException("Item linkado invalido ou inexistente.");
			
			final String itemName = item.getItem().getName();
			display.append(itemName);
			legacyDisplay.append("[").append(itemName).append(LEGACY_UNAVAILABLE_SUFFIX).append("]");
			snapshots.add(new ItemSnapshot(item));
		}
		
		if (itemIndex != items.size())
			throw new IllegalArgumentException("O texto possui menos marcadores do que itens fornecidos.");
		
		return new FermataLinkedItemChat(template, display.toString(), legacyDisplay.toString(), snapshots);
	}
	
	public static Scope activate(FermataLinkedItemChat message)
	{
		if (message == null || ACTIVE.get() != null)
			throw new IllegalStateException("Contexto de chat com link ja ativo ou ausente.");
		
		ACTIVE.set(message);
		return new Scope(message);
	}
	
	public static FermataLinkedItemChat currentFor(String content)
	{
		final FermataLinkedItemChat active = ACTIVE.get();
		return active != null && active._displayText.equals(content) ? active : null;
	}
	
	public String getDisplayText()
	{
		return _displayText;
	}
	
	public String getLegacyDisplayText()
	{
		return _legacyDisplayText;
	}
	
	public String getTemplate()
	{
		return _template;
	}
	
	public List<ItemSnapshot> getItems()
	{
		return _items;
	}
	
	public void writeBody(ByteBuffer output, int speakerObjectId, SayType sayType, String speakerName)
	{
		output.put((byte) FermataProtocol.ENVELOPE_OPCODE);
		output.putShort((short) 0x0003);
		output.putInt(speakerObjectId);
		output.putInt(sayType.ordinal());
		writeString(output, speakerName);
		writeString(output, _template);
		output.putShort((short) _items.size());
		for (ItemSnapshot item : _items)
		{
			output.putShort((short) item.type1);
			output.putInt(item.itemId);
			output.putInt(item.count);
			output.putShort((short) item.type2);
			output.putShort((short) item.customType1);
			output.putShort((short) item.equipped);
			output.putInt(item.bodyPart);
			output.putShort((short) item.enchantLevel);
			output.putShort((short) item.customType2);
			output.putInt(item.augmentationId);
			output.putInt(item.manaLeft);
		}
	}
	
	private static void writeString(ByteBuffer output, String text)
	{
		if (text != null)
		{
			for (int index = 0; index < text.length(); index++)
				output.putChar(text.charAt(index));
		}
		output.putChar('\0');
	}
	
	public static final class Scope implements AutoCloseable
	{
		private final FermataLinkedItemChat _message;
		private boolean _closed;
		
		private Scope(FermataLinkedItemChat message)
		{
			_message = message;
		}
		
		@Override
		public void close()
		{
			if (_closed)
				return;
			
			_closed = true;
			if (ACTIVE.get() == _message)
				ACTIVE.remove();
		}
	}
	
	public static final class ItemSnapshot
	{
		public final int type1;
		public final int itemId;
		public final int count;
		public final int type2;
		public final int customType1;
		public final int equipped;
		public final int bodyPart;
		public final int enchantLevel;
		public final int customType2;
		public final int augmentationId;
		public final int manaLeft;
		
		public ItemSnapshot(ItemInstance item)
		{
			this(
				item.getItem().getType1(),
				item.getItemId(),
				item.getCount(),
				item.getItem().getType2(),
				item.getCustomType1(),
				item.isEquipped() ? 1 : 0,
				item.getItem().getBodyPart(),
				item.getEnchantLevel(),
				item.getCustomType2(),
				item.isAugmented() ? item.getAugmentation().getId() : 0,
				item.getDisplayedManaLeft());
		}
		
		public ItemSnapshot(int type1, int itemId, int count, int type2, int customType1, int equipped, int bodyPart, int enchantLevel, int customType2, int augmentationId, int manaLeft)
		{
			this.type1 = type1;
			this.itemId = itemId;
			this.count = count;
			this.type2 = type2;
			this.customType1 = customType1;
			this.equipped = equipped;
			this.bodyPart = bodyPart;
			this.enchantLevel = enchantLevel;
			this.customType2 = customType2;
			this.augmentationId = augmentationId;
			this.manaLeft = manaLeft;
		}
	}
}
