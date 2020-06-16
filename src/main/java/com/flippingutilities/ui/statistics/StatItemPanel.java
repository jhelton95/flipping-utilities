/*
 * Copyright (c) 2020, Belieal <https://github.com/Belieal>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.flippingutilities.ui.statistics;

import com.flippingutilities.FlippingItem;
import com.flippingutilities.FlippingPlugin;
import com.flippingutilities.OfferInfo;
import com.flippingutilities.ui.utilities.UIUtilities;
import static com.flippingutilities.ui.utilities.UIUtilities.CLOSE_ICON;
import static com.flippingutilities.ui.utilities.UIUtilities.DELETE_ICON;
import static com.flippingutilities.ui.utilities.UIUtilities.OPEN_ICON;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

@Slf4j
public class StatItemPanel extends JPanel
{
	private static final Border ITEM_INFO_BORDER = new CompoundBorder(
		BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR.darker(), 3),
		BorderFactory.createEmptyBorder(3, 3, 3, 3));

	private static final Border ITEM_HISTORY_BORDER = new CompoundBorder(
		BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR.darker(), 3),
		BorderFactory.createEmptyBorder(3, 0, 0, 0));

	private static final Border TRADE_HISTORY_BORDER = new CompoundBorder(
		BorderFactory.createMatteBorder(0, 0, 2, 0, ColorScheme.LIGHT_GRAY_COLOR),
		BorderFactory.createEmptyBorder(3, 5, 3, 5));

	private FlippingPlugin plugin;
	@Getter
	private FlippingItem flippingItem;

	private StatsPanel statsPanel;

	@Getter
	private int totalFlips;

	private Instant startOfInterval;
	private ArrayList<OfferInfo> tradeHistory = new ArrayList<>();

	//Shows the item's profit
	private JLabel itemProfitLabel = new JLabel();

	//Shows the item's icon
	private JPanel itemIconTitlePanel = new JPanel(new BorderLayout());
	//Label that controls the collapse function of the item panel.
	private JLabel collapseIconTitleLabel = new JLabel();

	//Contains the sub info container and trade history panel.
	private JPanel subInfoAndHistoryContainer = new JPanel(new BorderLayout());

	private JLabel totalProfitValLabel = new JLabel("", SwingConstants.RIGHT);
	private JLabel profitEachValLabel = new JLabel("", SwingConstants.RIGHT);
	private JLabel timeOfLastFlipValLabel = new JLabel("", SwingConstants.RIGHT);
	private JLabel quantityValLabel = new JLabel("", SwingConstants.RIGHT);
	private JLabel roiValLabel = new JLabel("", SwingConstants.RIGHT);
	private JLabel avgBuyPriceValLabel = new JLabel("", SwingConstants.RIGHT);
	private JLabel avgSellPriceValLabel = new JLabel("", SwingConstants.RIGHT);

	private List<FlipPanel> flipPanels;
	private List<OfferPanel> offerPanels;

	private ItemManager itemManager;

	/**
	 * This panel represents the middle layer of information. It contains general information about the item
	 * along with being the container for the trade history of that item.
	 *
	 * @param plugin       Used to access the plugin user config.
	 * @param itemManager  Used to get the icon of the item.
	 * @param flippingItem The item that the panel represents.
	 */

	StatItemPanel(FlippingPlugin plugin, ItemManager itemManager, FlippingItem flippingItem)
	{
		this.plugin = plugin;
		this.flippingItem = flippingItem;
		this.itemManager = itemManager;
		this.statsPanel = plugin.getStatPanel();

		startOfInterval = statsPanel.getStartOfInterval();
		tradeHistory = flippingItem.getIntervalHistory(startOfInterval);

		offerPanels = tradeHistory.stream().map(OfferPanel::new).collect(Collectors.toList());
		flipPanels = flippingItem.getFlips(startOfInterval).stream().map(FlipPanel::new).collect(Collectors.toList());

		JPanel allOffersPanel = UIUtilities.stackPanelsVertically((List) offerPanels);
		JPanel allFlipsPanel = UIUtilities.stackPanelsVertically((List) flipPanels);

		JLabel[] descriptionLabels = {new JLabel("Total Profit: "), new JLabel("Avg. Profit ea: "), new JLabel("Last Traded: "), new JLabel("Quantity Flipped: "),
			new JLabel(" "), new JLabel("Avg. ROI: "), new JLabel("Avg. Buy Price: "), new JLabel("Avg. Sell Price: ")};

		JLabel[] valueLabels = {totalProfitValLabel, profitEachValLabel, timeOfLastFlipValLabel, quantityValLabel,
			new JLabel(" "), roiValLabel, avgBuyPriceValLabel, avgSellPriceValLabel};

		setLayout(new BorderLayout());

		JPanel titlePanel = titlePanel(iconPanel(), nameAndProfitPanel(), collapseIcon());

		JPanel subInfoPanel = subInfoPanel(descriptionLabels, valueLabels);

		JPanel tradeHistoryPanel = tradeHistoryPanel(allOffersPanel, allFlipsPanel);

		updateLabels();

		//Set background and border of container with sub infos and trade history
		subInfoAndHistoryContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		subInfoAndHistoryContainer.setBorder(ITEM_INFO_BORDER);

		subInfoAndHistoryContainer.add(subInfoPanel, BorderLayout.CENTER);
		subInfoAndHistoryContainer.add(tradeHistoryPanel, BorderLayout.SOUTH);

		add(titlePanel, BorderLayout.NORTH);
		add(subInfoAndHistoryContainer, BorderLayout.CENTER);

		//Set font colors of right value labels
		timeOfLastFlipValLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		profitEachValLabel.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
		quantityValLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		avgBuyPriceValLabel.setForeground(UIUtilities.PROFIT_COLOR);
		avgSellPriceValLabel.setForeground(UIUtilities.PROFIT_COLOR);

		totalFlips = flipPanels.size();

		revalidate();
		repaint();
	}

	private JPanel titlePanel(JPanel itemIconPanel, JPanel nameAndProfitPanel, JLabel collapseIcon)
	{
		JPanel titlePanel = new JPanel(new BorderLayout());
		titlePanel.setComponentPopupMenu(UIUtilities.createGeTrackerLinksPopup(flippingItem));
		titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
		titlePanel.setBorder(new EmptyBorder(2, 2, 2, 2));

		titlePanel.add(itemIconPanel, BorderLayout.WEST);
		titlePanel.add(nameAndProfitPanel, BorderLayout.CENTER);
		titlePanel.add(collapseIcon, BorderLayout.EAST);

		titlePanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					if (subInfoAndHistoryContainer.isVisible())
					{
						collapseIconTitleLabel.setIcon(CLOSE_ICON);
						subInfoAndHistoryContainer.setVisible(false);
						flippingItem.setShouldExpandStatItem(false);
					}
					else
					{
						collapseIconTitleLabel.setIcon(OPEN_ICON);
						subInfoAndHistoryContainer.setVisible(true);
						flippingItem.setShouldExpandStatItem(true);
					}
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				nameAndProfitPanel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				titlePanel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				itemIconTitlePanel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				nameAndProfitPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
				itemIconTitlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
				titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
			}
		});

		return titlePanel;
	}

	private JPanel subInfoPanel(JLabel[] descriptionLabels, JLabel[] valueLabels)
	{
		JPanel subInfoContainer = new JPanel();
		subInfoContainer.setLayout(new DynamicGridLayout(valueLabels.length, descriptionLabels.length));

		boolean useAltColor = false;
		for (int i = 0; i < descriptionLabels.length; i++)
		{
			JLabel textLabel = descriptionLabels[i];
			JLabel valLabel = valueLabels[i];
			JPanel panel = new JPanel(new BorderLayout());

			panel.add(textLabel, BorderLayout.WEST);
			panel.add(valLabel, BorderLayout.EAST);

			panel.setBorder(new EmptyBorder(4, 2, 4, 2));

			panel.setBackground(useAltColor ? UIUtilities.DARK_GRAY_ALT_ROW_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			useAltColor = !useAltColor;

			textLabel.setForeground(ColorScheme.GRAND_EXCHANGE_ALCH);

			textLabel.setFont(FontManager.getRunescapeSmallFont());
			valLabel.setFont(FontManager.getRunescapeSmallFont());

			subInfoContainer.add(panel);
		}

		return subInfoContainer;
	}

	private JPanel tradeHistoryPanel(JPanel offersPanel, JPanel flipsPanel)
	{
		JPanel tradeHistoryTitlePanel = new JPanel(new BorderLayout());
		tradeHistoryTitlePanel.setBorder(TRADE_HISTORY_BORDER);

		JPanel mainDisplay = new JPanel();
		MaterialTabGroup tabGroup = new MaterialTabGroup(mainDisplay);
		MaterialTab offersTab = new MaterialTab("Buys/Sells", tabGroup, offersPanel);
		MaterialTab flipsTab = new MaterialTab("Flips", tabGroup, flipsPanel);

		Arrays.asList(offersPanel, flipsPanel).forEach(panel -> {
			panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			panel.setBorder(ITEM_HISTORY_BORDER);
		});

		tabGroup.setBorder(new EmptyBorder(5, 0, 2, 0));
		tabGroup.addTab(offersTab);
		tabGroup.addTab(flipsTab);

		tabGroup.select(flipsTab);
		mainDisplay.setVisible(false);
		tabGroup.setVisible(false);

		JLabel collapseTradeHistoryIconLabel = new JLabel(CLOSE_ICON);
		JLabel tradeHistoryTitleLabel = new JLabel("Trade History", SwingConstants.CENTER);
		tradeHistoryTitlePanel.add(tradeHistoryTitleLabel, BorderLayout.CENTER);
		tradeHistoryTitlePanel.add(collapseTradeHistoryIconLabel, BorderLayout.EAST);
		tradeHistoryTitlePanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					if (tabGroup.isVisible())
					{
						tabGroup.setVisible(false);
						mainDisplay.setVisible(false);
						collapseTradeHistoryIconLabel.setIcon(CLOSE_ICON);
					}
					else
					{
						tabGroup.setVisible(true);
						mainDisplay.setVisible(true);
						collapseTradeHistoryIconLabel.setIcon(OPEN_ICON);
					}
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				tradeHistoryTitlePanel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				tradeHistoryTitlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
			}
		});


		JPanel tradeHistoryBody = new JPanel(new BorderLayout());
		tradeHistoryBody.add(tabGroup, BorderLayout.NORTH);
		tradeHistoryBody.add(mainDisplay, BorderLayout.CENTER);
		tradeHistoryBody.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

		JPanel tradeHistoryPanel = new JPanel(new BorderLayout());
		tradeHistoryPanel.add(tradeHistoryTitlePanel, BorderLayout.NORTH);
		tradeHistoryPanel.add(tradeHistoryBody, BorderLayout.CENTER);

		return tradeHistoryPanel;
	}


	private JPanel iconPanel()
	{
		JLabel deleteLabel = new JLabel(DELETE_ICON);
		deleteLabel.setPreferredSize(new Dimension(24, 24));
		deleteLabel.setVisible(false);

		AsyncBufferedImage itemImage = itemManager.getImage(flippingItem.getItemId());
		JLabel itemLabel = new JLabel();
		Runnable resize = () ->
		{
			BufferedImage subIcon = itemImage.getSubimage(0, 0, 32, 32);
			ImageIcon itemIcon = new ImageIcon(subIcon.getScaledInstance(24, 24, Image.SCALE_SMOOTH));
			itemLabel.setIcon(itemIcon);
		};
		itemImage.onLoaded(resize);
		resize.run();

		itemIconTitlePanel.add(itemLabel, BorderLayout.WEST);
		itemIconTitlePanel.add(deleteLabel, BorderLayout.EAST);
		itemIconTitlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
		itemIconTitlePanel.setBorder(new EmptyBorder(5, 2, 0, 5));
		itemIconTitlePanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				deletePanel();
				statsPanel.rebuild(plugin.getTradesForCurrentView());
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				itemLabel.setVisible(false);
				deleteLabel.setVisible(true);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				itemLabel.setVisible(true);
				deleteLabel.setVisible(false);
			}
		});

		return itemIconTitlePanel;
	}

	private JPanel nameAndProfitPanel() {
		JPanel nameAndProfitPanel = new JPanel(new BorderLayout());
		nameAndProfitPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
		JLabel itemNameLabel = new JLabel(flippingItem.getItemName());
		nameAndProfitPanel.add(itemNameLabel, BorderLayout.NORTH);
		nameAndProfitPanel.add(itemProfitLabel, BorderLayout.SOUTH);
		nameAndProfitPanel.setPreferredSize(new Dimension(0, 0));
		return nameAndProfitPanel;
	}

	private JLabel collapseIcon() {
		JLabel collapseIconLabel = new JLabel();
		collapseIconLabel.setIcon(flippingItem.isShouldExpandStatItem() ? OPEN_ICON : CLOSE_ICON);
		collapseIconLabel.setBorder(new EmptyBorder(2, 2, 2, 2));
		return collapseIconLabel;
	}

	public void updateLabels()
	{
		long revenue = flippingItem.getCashflow(tradeHistory, false);
		long expense = flippingItem.getCashflow(tradeHistory, true);
		int itemCountFlipped = flippingItem.countItemsFlipped(tradeHistory);

		if (itemCountFlipped == 0)
		{
			return;
		}

		updateTitleLabels(revenue-expense, itemCountFlipped);
		updateSubInfoLabels(revenue, expense, itemCountFlipped);
		updateTimeLabels();
	}

	/* Total profit and name label */
	private void updateTitleLabels(long profit, long numItems)
	{

		String totalProfitString = ((profit > 0) ? "+" : "") + UIUtilities.quantityToRSDecimalStack(profit, true) + " gp";

		if (numItems != 0)
		{
			totalProfitString += " (x " + QuantityFormatter.formatNumber(numItems) + ")";
		}

		itemProfitLabel.setText(totalProfitString);
		itemProfitLabel.setForeground((profit >= 0) ? ColorScheme.GRAND_EXCHANGE_PRICE : UIUtilities.OUTDATED_COLOR);
		itemProfitLabel.setBorder(new EmptyBorder(0, 0, 2, 0));
		itemProfitLabel.setFont(FontManager.getRunescapeSmallFont());
	}

	private void updateSubInfoLabels(long revenue, long expense, int numItems)
	{
		long profit = revenue - expense;
		totalProfitValLabel.setText(UIUtilities.quantityToRSDecimalStack(profit, true) + " gp");
		totalProfitValLabel.setForeground((profit >= 0) ? ColorScheme.GRAND_EXCHANGE_PRICE : UIUtilities.OUTDATED_COLOR);
		totalProfitValLabel.setToolTipText(QuantityFormatter.formatNumber(profit) + " gp");

		profitEachValLabel.setText(UIUtilities.quantityToRSDecimalStack((profit / numItems), true) + " gp/ea");
		profitEachValLabel.setForeground((profit >= 0) ? ColorScheme.GRAND_EXCHANGE_PRICE : UIUtilities.OUTDATED_COLOR);
		profitEachValLabel.setToolTipText(QuantityFormatter.formatNumber(profit / numItems) + " gp/ea");

		quantityValLabel.setText(QuantityFormatter.formatNumber(numItems) + " Items");

		avgBuyPriceValLabel.setText(QuantityFormatter.formatNumber((int) (expense / numItems)) + " gp");
		avgSellPriceValLabel.setText(QuantityFormatter.formatNumber((int) (revenue / numItems)) + " gp");

		float roi = (float) profit / expense * 100;

		roiValLabel.setText(String.format("%.2f", roi) + "%");
		roiValLabel.setForeground(UIUtilities.gradiatePercentage(roi, plugin.getConfig().roiGradientMax()));
		roiValLabel.setToolTipText("<html>Return on investment:<br>Percentage of profit relative to gp invested</html>");
	}

	public void updateTimeLabels()
	{
		if (tradeHistory.isEmpty())
		{
			return;
		}

		OfferInfo lastRecordedTrade = tradeHistory.get(tradeHistory.size() - 1);
		timeOfLastFlipValLabel.setText(UIUtilities.formatDurationTruncated(lastRecordedTrade.getTime()) + " ago");

		flipPanels.forEach(FlipPanel::updateTimeDisplay);
		offerPanels.forEach(OfferPanel::updateTimeDisplay);
	}

	private void deletePanel()
	{
		statsPanel.deletePanel(this, false);
	}
}