package ext.mods.questrecommender.vector;

/**
 * Representa um item de recompensa estruturado para exibicao com icone no HTML.
 */
public final class QuestRewardItem
{
	private final int _itemId;
	private final long _count;

	public QuestRewardItem(int itemId, long count)
	{
		_itemId = itemId;
		_count = count;
	}

	public int getItemId()
	{
		return _itemId;
	}

	public long getCount()
	{
		return _count;
	}
}
