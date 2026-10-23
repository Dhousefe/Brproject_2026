package ext.mods.questrecommender;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.extensions.hooks.QuestRecommenderHooks;
import ext.mods.extensions.hooks.QuestRecommenderHooks.Api.RewardQuest;
import ext.mods.gameserver.model.actor.Player;

import java.util.List;

/**
 * Ponto de entrada SPI do mod Quest Recommender.
 * Dhousefe - L2JBR
 */
public final class QuestRecommenderExtension implements Extension
{
	public static final String ID = "quest-recommender";

	@Override
	public String id()
	{
		return ID;
	}

	@Override
	public String version()
	{
		return "1.0.0";
	}

	@Override
	public void onEnable(ExtensionContext context)
	{
		try
		{
			QuestRecommenderManager.getInstance().init();
			QuestRecommenderHooks.register(new QuestRecommenderHooks.Api()
			{
				@Override
				public boolean isEnabled()
				{
					return QuestRecommenderManager.getInstance().isEnabled();
				}

				@Override
				public int getQuestionMarkId()
				{
					return QuestRecommenderManager.getInstance().getQuestionMarkId();
				}

				@Override
				public String getTutorialAlertHtml(Player player)
				{
					return QuestRecommenderManager.getInstance().getTutorialAlertHtml(player);
				}

				@Override
				public void sendQuestionMark(Player player)
				{
					QuestRecommenderManager.getInstance().sendQuestionMark(player);
				}

				@Override
				public void showRecommendationsWindow(Player player, int page)
				{
					QuestRecommenderManager.getInstance().showRecommendationsWindow(player, page);
				}

				@Override
				public String renderRecommendationsHtml(Player player, int limit)
				{
					return QuestRecommenderManager.getInstance().renderRecommendationsHtml(player, limit);
				}

				@Override
				public List<RewardQuest> findAvailableRewardQuests(Player player, int itemId)
				{
					return QuestRecommenderManager.getInstance().findAvailableRewardQuests(player, itemId);
				}

				@Override
				public boolean markAvailableRewardQuestStart(Player player, int itemId, int questId)
				{
					return QuestRecommenderManager.getInstance().markAvailableRewardQuestStart(player, itemId, questId);
				}
			});

			context.info("quest-recommender extension enabled successfully.");
		}
		catch (Throwable t)
		{
			context.warn("quest-recommender failed to start: " + t.getMessage());
			t.printStackTrace();
		}
	}

	@Override
	public void onDisable(ExtensionContext context)
	{
		QuestRecommenderHooks.clear();
		QuestRecommenderManager.getInstance().shutdown();
		context.info("quest-recommender extension disabled.");
	}
}
