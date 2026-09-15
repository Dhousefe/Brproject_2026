package ext.mods.gameserver.network.serverpackets.fermata;

import java.util.List;

import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.model.item.kind.Item;
import ext.mods.gameserver.network.serverpackets.L2GameServerPacket;

/**
 * Pacote LinkedItemChat (S->C 0xFD 0x0003) da extensão de protocolo Fermata.
 * Transmite mensagem de chat contendo de 1 a 4 snapshots de itens imutáveis de 32 bytes.
 */
public final class LinkedItemChat extends L2GameServerPacket
{
	private final int _speakerObjectId;
	private final int _channel;
	private final String _speakerName;
	private final String _text;
	private final List<ItemInstance> _items;
	
	public LinkedItemChat(int speakerObjectId, int channel, String speakerName, String text, List<ItemInstance> items)
	{
		_speakerObjectId = speakerObjectId;
		_channel = channel;
		_speakerName = speakerName;
		_text = text;
		_items = items;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfd); // Opcode Fermata
		writeH(0x0003); // Sub-opcode LinkedItemChat
		
		writeD(_speakerObjectId);
		writeD(_channel);
		writeS(_speakerName);
		writeS(_text);
		
		final int itemCount = Math.min(_items.size(), 4);
		writeH(itemCount);
		
		for (int i = 0; i < itemCount; i++)
		{
			final ItemInstance itemInstance = _items.get(i);
			final Item item = itemInstance.getItem();
			
			// Snapshot imutável de 32 bytes (sem expor object_id privado)
			writeH(item.getType1());
			writeD(itemInstance.getItemId());
			writeD(itemInstance.getCount());
			writeH(item.getType2());
			writeH(itemInstance.getCustomType1());
			writeH(itemInstance.isEquipped() ? 0x01 : 0x00);
			writeD(item.getBodyPart());
			writeH(itemInstance.getEnchantLevel());
			writeH(itemInstance.getCustomType2());
			writeD(itemInstance.isAugmented() ? itemInstance.getAugmentation().getId() : 0x00);
			writeD(itemInstance.getDisplayedManaLeft());
		}
	}
}
