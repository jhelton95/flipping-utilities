package com.flippingutilities;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import joptsimple.internal.Strings;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.account.AccountSession;
import net.runelite.client.account.SessionManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.SessionClose;
import net.runelite.client.events.SessionOpen;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.ge.GrandExchangeTrade;
import net.runelite.http.api.item.ItemStats;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
	name = "Flipping Utilities",
	description = "Provides utilities for GE flipping"
)

public class FlippingPlugin extends Plugin
{
	//Limit the amount of trades every item holds.
	private static final int TRADE_HISTORY_MAX_SIZE = 20;
	//Limit the amount of items stored.
	private static final int TRADES_LIST_MAX_SIZE = 200;
	public static final String CONFIG_GROUP = "flipping";
	public static final String CONFIG_KEY = "items";

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ScheduledExecutorService executor;
	private ScheduledFuture timeUpdateFuture;
	@Inject
	private ClientToolbar clientToolbar;
	private NavigationButton navButton;

	@Inject
	private ConfigManager configManager;
	@Inject
	private SessionManager sessionManager;
	@Inject
	@Getter
	private FlippingConfig config;

	@Inject
	private ItemManager itemManager;

	private FlippingPanel panel;
	@Getter
	private ArrayList<FlippingItem> tradesList;

	//Ensures we don't rebuild constantly when highlighting
	@Setter
	private int prevHighlight;

	@Override
	protected void startUp()
	{
		//Main visuals.
		panel = new FlippingPanel(this, itemManager, clientThread);

		// I wanted to put it below the GE plugin, but can't as the GE and world switcher buttonhave the same priority...
		navButton = NavigationButton.builder()
			.tooltip("Flipping Plugin")
			.icon(ImageUtil.getResourceStreamFromClass(getClass(), "/graphIconGreen.png"))
			.priority(3)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		//Stores all bought or sold trades.
		tradesList = new ArrayList<>();

		clientThread.invokeLater(() -> {
			switch (client.getGameState())
			{
				case STARTING:
				case UNKNOWN:
					return false;
			}
			//Loads tradesList with data from previous sessions.
			loadConfig();

			executor.submit(() -> clientThread.invokeLater(() -> SwingUtilities.invokeLater(() -> {
				if (tradesList != null)
				{
					panel.rebuildFlippingPanel(tradesList);
				}
			})));
			return true;
		});

		//Ensures the panel timers are updated at 10 times per second.
		timeUpdateFuture = executor.scheduleAtFixedRate(() -> panel.updateTimes(), 100, 100, TimeUnit.MILLISECONDS);
	}

	@Override
	protected void shutDown()
	{
		if (timeUpdateFuture != null)
		{
			//Stop all timers
			timeUpdateFuture.cancel(true);
			timeUpdateFuture = null;
		}

		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onSessionOpen(SessionOpen event)
	{
		//Load new account config
		final AccountSession session = sessionManager.getAccountSession();
		if (session != null && session.getUsername() != null)
		{
			clientThread.invokeLater(() ->
			{
				loadConfig();
				SwingUtilities.invokeLater(() -> panel.rebuildFlippingPanel(tradesList));
				return true;
			});
		}
	}

	@Subscribe
	public void onSessionClose(SessionClose event)
	{
		//Config is now locally stored
		clientThread.invokeLater(() ->
		{
			loadConfig();
			SwingUtilities.invokeLater(() -> panel.rebuildFlippingPanel(tradesList));
			return true;
		});
	}

	//When flipping via margin checking, we look for the highest instant buy price
	// to determine what our sell price should be to undercut existing offers, and vice versa for our buy price.
	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged newOfferEvent)
	{
		if (newOfferEvent.getOffer().getItemId() == 0)
		{
			return;
		}

		final GrandExchangeOffer newOffer = newOfferEvent.getOffer();
		final GrandExchangeOfferState newOfferState = newOffer.getState();

		//May change this in the future to be able to update prices independent of quantity.
		//Perhaps some way of timing the state change from buying to bought such that we know it was "instantly" bought/sold?
		if ((newOfferState == GrandExchangeOfferState.BOUGHT || newOfferState == GrandExchangeOfferState.SOLD) && newOffer.getQuantitySold() == 1)
		{
			addTrade(tradeConstructor(newOffer));
			panel.rebuildFlippingPanel(tradesList);
			updateConfig();
		}
	}

	private GrandExchangeTrade tradeConstructor(GrandExchangeOffer offer)
	{
		GrandExchangeTrade result = new GrandExchangeTrade();
		result.setBuy(offer.getState() == GrandExchangeOfferState.BOUGHT);
		result.setItemId(offer.getItemId());
		result.setPrice(offer.getSpent() / offer.getQuantitySold());
		result.setQuantity(offer.getQuantitySold());
		result.setTime(Instant.now());

		return result;
	}

	//Adds GE trade data to the trades list.
	public void addTrade(GrandExchangeTrade trade)
	{
		tradesList.stream()
			.filter(t -> t.getItemId() == trade.getItemId())
			.findFirst()
			.ifPresentOrElse(item -> {
					log.info("Found a match, updating item.");
					//Found a match, update the existing flipping item.
					updateFlip(item, trade);

					//Move item to top
					tradesList.remove(item);
					tradesList.add(0, item);
				},
				//No match found, create a new flipping item.
				() -> {
					log.info("No matching trade found, adding new one.");
					addToTradesList(trade);
				});
	}

	//Constructs a FlippingItem and adds it to the tradeList.
	private void addToTradesList(GrandExchangeTrade trade)
	{
		int tradeItemId = trade.getItemId();
		String itemName = itemManager.getItemComposition(tradeItemId).getName();

		ItemStats itemStats = itemManager.getItemStats(tradeItemId, false);
		int tradeGELimit = itemStats != null ? itemStats.getGeLimit() : 0;
		ArrayList<GrandExchangeTrade> tradeHistory = new ArrayList<GrandExchangeTrade>()
		{{
			add(trade);
		}};

		int tradeBuyPrice = !trade.isBuy() ? trade.getPrice() : 0;
		int tradeSellPrice = trade.isBuy() ? trade.getPrice() : 0;

		Instant tradeBuyTime = !trade.isBuy() ? trade.getTime() : null;
		Instant tradeSellTime = trade.isBuy() ? trade.getTime() : null;

		tradesList.add(0, new FlippingItem(tradeHistory, tradeItemId, itemName, tradeGELimit, tradeBuyPrice, tradeSellPrice, tradeBuyTime, tradeSellTime));

		//Make sure we don't have too many trades at once.
		if (tradesList.size() > TRADES_LIST_MAX_SIZE)
		{
			tradesList.remove(tradesList.size() - 1);
		}
	}

	//Updates the latest margins writes history for a Flipping Item
	private void updateFlip(FlippingItem flippingItem, GrandExchangeTrade trade)
	{
		boolean tradeBuyState = trade.isBuy();
		int tradePrice = trade.getPrice();
		int tradeQuantity = trade.getQuantity();
		Instant tradeTime = trade.getTime();

		flippingItem.addTradeHistory(trade);
		//Make sure the individual item objects aren't massive.
		if (flippingItem.getTradeHistory().size() > TRADE_HISTORY_MAX_SIZE)
		{
			flippingItem.getTradeHistory().remove(flippingItem.getTradeHistory().size() - 1);
		}
		//Bought
		if (tradeBuyState)
		{
			flippingItem.setLatestSellPrice(tradePrice);
			flippingItem.setLatestSellTime(tradeTime);
		}
		else
		{
			flippingItem.setLatestBuyPrice(tradePrice);
			flippingItem.setLatestBuyTime(tradeTime);
		}
		//TODO: Add quantity of remaining GE limit.
	}

	@Provides
	FlippingConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FlippingConfig.class);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		//This seems to be the scriptid for closing the GE window.
		if (event.getScriptId() == 29 || event.getScriptId() == 1646)
		{
			if (panel.isItemHighlighted())
			{
				panel.dehighlightItem();
			}
		}

		// GE offers setup init
		if (client.getVar(VarPlayer.CURRENT_GE_ITEM) != -1)
		{
			highlightOffer();
		}
		else if (panel.isItemHighlighted())
		{
			panel.dehighlightItem();
		}
	}

	//TODO: Refactor this with a search on the search bar
	private void highlightOffer()
	{
		int currentGEItemId = client.getVar(VarPlayer.CURRENT_GE_ITEM);
		if (currentGEItemId == prevHighlight || panel.isItemHighlighted())
		{
			return;
		}
		prevHighlight = currentGEItemId;
		panel.highlightItem(currentGEItemId);
	}

	//Functionality to the top right reset button.
	public void resetTradeHistory()
	{
		tradesList.clear();
		configManager.unsetConfiguration(CONFIG_GROUP, CONFIG_KEY);
		panel.cardLayout.show(panel.getCenterPanel(), FlippingPanel.getWELCOME_PANEL());
		panel.rebuildFlippingPanel(tradesList);
	}

	//Stores all the session trade data in config.
	public void updateConfig()
	{
		if (tradesList.isEmpty())
		{
			return;
		}
		final Gson gson = new Gson();
		executor.submit(() -> {
			final String json = gson.toJson(tradesList);
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY, json);
		});
	}

	//Loads previous session data to tradeList.
	public void loadConfig()
	{
		log.debug("Loading flipping config");
		final String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY);
		if (Strings.isNullOrEmpty(json) || !config.storeTradeHistory())
		{
			panel.setItemsToBeAddedList(new ArrayList<>());
		}
		else
		{
			try
			{
				final Gson gson = new Gson();
				Type type = new TypeToken<ArrayList<FlippingItem>>()
				{

				}.getType();
				ArrayList<FlippingItem> storedItemList = gson.fromJson(json, type);
				panel.setItemsToBeAddedList(gson.fromJson(json, type));
				tradesList = storedItemList;
			}
			catch (Exception e)
			{
				panel.setItemsToBeAddedList(new ArrayList<>());
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(CONFIG_GROUP))
		{
			//Ensure that user configs are updated after being changed
			switch (event.getKey())
			{
				case ("storeTradeHistory"):
				case ("outOfDateWarning"):
				case ("roiGradientMax"):
				case ("marginCheckLoss"):
					panel.rebuildFlippingPanel(tradesList);
				default:
			}
		}
	}
}