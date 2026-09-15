package ext.mods.questrecommender;

import br.project.spi.Extension;
import br.project.spi.ExtensionContext;
import ext.mods.extensions.hooks.QuestRecommenderHooks;
import ext.mods.gameserver.model.actor.Player;

/**
 * Ponto de entrada SPI do mod Quest Recommender.
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
