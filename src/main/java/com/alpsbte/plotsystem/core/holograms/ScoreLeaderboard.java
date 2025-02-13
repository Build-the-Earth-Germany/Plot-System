/*
 * The MIT License (MIT)
 *
 *  Copyright © 2021-2022, Alps BTE <bte.atchli@gmail.com>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package com.alpsbte.plotsystem.core.holograms;

import com.alpsbte.plotsystem.PlotSystem;
import com.alpsbte.plotsystem.core.system.Builder;
import com.alpsbte.plotsystem.core.system.Payout;
import com.alpsbte.plotsystem.utils.Utils;
import com.alpsbte.plotsystem.utils.io.config.ConfigPaths;
import com.alpsbte.plotsystem.utils.io.language.LangPaths;
import com.alpsbte.plotsystem.utils.io.language.LangUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ScoreLeaderboard extends HolographicDisplay {
    private final DecimalFormat df = new DecimalFormat("#.##");
    private LeaderboardTimeframe sortBy = LeaderboardTimeframe.DAILY;
    private BukkitTask changeSortTask = null;
    private BukkitTask actionbarTask = null;
    int changeState = 0;

    public ScoreLeaderboard() {
        super("score-leaderboard");
        init();
    }

    private void init() {
        if (getPages().size() < 1) {
            PlotSystem.getPlugin().getLogger().log(Level.WARNING, "Unable to initialize Score-Leaderboard - No display pages enabled! Check config for display-options.");
            return;
        }

        sortBy = getPages().get(0);

        long interval = getInterval();
        long changeDelay = interval / 15;

        if (!PlotSystem.getPlugin().isEnabled()) return;

        changeSortTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (changeState >= changeDelay) {
                    LeaderboardTimeframe next = Utils.getNextListItem(getPages(), sortBy);
                    if (next == null) {
                        sortBy = getPages().get(0);
                    } else {
                        sortBy = next;
                    }
                    changeState = 0;
                } else {
                    changeState++;
                    updateHologram();
                }
            }
        }.runTaskTimerAsynchronously(PlotSystem.getPlugin(), changeDelay, changeDelay);

        actionbarTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : showToPlayers()) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, rankingString(player));
                }
            }
        }.runTaskTimerAsynchronously(PlotSystem.getPlugin(), 0L, 20L);
    }

    @Override
    protected String getTitle() {
        return "§b§lTOP SCORE §6§l[" + (sortBy.toString().charAt(0) + sortBy.toString().toLowerCase().substring(1)) + "]";
    }

    @Override
    public String getFooter() {
        long changeDelay = getInterval() / 15;
        int highlightCount = (int) (((float) changeState / changeDelay) * 15);

        StringBuilder highlighted = new StringBuilder();
        for (int i = 0; i < highlightCount; i++) {
            highlighted.append("-");
        }
        StringBuilder notH = new StringBuilder();
        for (int i = 0; i < 15 - highlightCount; i++) {
            notH.append("-");
        }

        return "§e" + highlighted + "§7" + notH;
    }

    @Override
    protected List<DataLine> getDataLines() {
        try {
            ArrayList<DataLine> lines = new ArrayList<>();

            for (int index = 0; index < 10; index++) {
                lines.add(new LeaderboardPositionLineWithPayout(index + 1, null, 0));
            }

            int index = 0;
            for (Builder.DatabaseEntry<String, Integer> entry : Builder.getBuildersByScore(sortBy)) {
                lines.set(index, new LeaderboardPositionLineWithPayout(index + 1, entry.getKey(), entry.getValue()));
                index++;
            }

            return lines;
        } catch (SQLException ex) {
            PlotSystem.getPlugin().getLogger().log(Level.SEVERE, "Could not read data lines.", ex);
        }
        return new ArrayList<>();
    }

    @Override
    protected ItemStack getItem() {
        return new ItemStack(Material.NETHER_STAR);
    }

    @Override
    public void onShutdown() {
        if (changeSortTask != null) {
            changeSortTask.cancel();
            changeSortTask = null;
        }

        if (actionbarTask != null) {
            actionbarTask.cancel();
            actionbarTask = null;
        }
    }

    @Override
    public void reloadHologram() {
        super.reloadHologram();
        onShutdown();
        init();
    }

    private List<LeaderboardTimeframe> getPages() {
        if (PlotSystem.getPlugin().getConfigManager() == null) return new ArrayList<>();
        FileConfiguration config = PlotSystem.getPlugin().getConfigManager().getConfig();
        return Arrays.stream(LeaderboardTimeframe.values()).filter(p -> config.getBoolean(p.configPath)).collect(Collectors.toList());
    }

    private BaseComponent[] rankingString(Player player) {
        int position;
        int rows;
        int myScore;
        try {
            position = Builder.getBuilderScorePosition(player.getUniqueId(), sortBy);
            rows = Builder.getBuildersInSort(sortBy);
            myScore = Builder.getBuilderScore(player.getUniqueId(), sortBy);
        } catch (SQLException e) {
            e.printStackTrace();
            return TextComponent.fromLegacyText("§cSQL Exception");
        }

        ComponentBuilder builder = new ComponentBuilder("");
        builder.append(
                new ComponentBuilder("  " + LangUtil.get(player, sortBy.langPath))
                        .color(ChatColor.GOLD)
                        .bold(true)
                        .create()
        );

        builder.append(
                new ComponentBuilder(" ➜ ")
                        .color(ChatColor.DARK_GRAY)
                        .bold(true)
                        .create()
        );

        if (position == -1) {
            builder.append(
                    new ComponentBuilder(LangUtil.get(player, LangPaths.Leaderboards.NOT_ON_LEADERBOARD))
                            .color(ChatColor.RED)
                            .bold(false)
                            .create()
            );
        } else if (position < 50) {
            builder.append(
                    new ComponentBuilder(
                            LangUtil.get(player, LangPaths.Leaderboards.ACTIONBAR_POSITION, String.valueOf(position))
                    ).color(ChatColor.GREEN).bold(false).create()
            );
        } else {
            String topPercentage = df.format(position * 1.0 / rows);
            builder.append(
                    new ComponentBuilder(
                            LangUtil.get(player, LangPaths.Leaderboards.ACTIONBAR_PERCENTAGE, topPercentage)
                    ).bold(false).create()
            );
        }

        if (myScore != -1) {
            builder.append(TextComponent.fromLegacyText("§8 (§b" + myScore + " points§8)"));
        }

        return builder.bold(false).create();
    }

    private List<Player> showToPlayers() {
        FileConfiguration config = PlotSystem.getPlugin().getConfigManager().getConfig();
        boolean actionBarEnabled = config.getBoolean(ConfigPaths.DISPLAY_OPTIONS_ACTION_BAR_ENABLED, true);
        int actionBarRadius = config.getInt(ConfigPaths.DISPLAY_OPTIONS_ACTION_BAR_RADIUS, 30);
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getName().equals(getLocation().getWorld().getName()) && (!actionBarEnabled || getLocation().distance(player.getLocation()) <= actionBarRadius)) {
                players.add(player);
            }
        }
        return players;
    }

    public enum LeaderboardTimeframe {
        DAILY(ConfigPaths.DISPLAY_OPTIONS_SHOW_DAILY),
        WEEKLY(ConfigPaths.DISPLAY_OPTIONS_SHOW_WEEKLY),
        MONTHLY(ConfigPaths.DISPLAY_OPTIONS_SHOW_MONTHLY),
        YEARLY(ConfigPaths.DISPLAY_OPTIONS_SHOW_YEARLY),
        LIFETIME(ConfigPaths.DISPLAY_OPTIONS_SHOW_LIFETIME);

        public final String configPath;
        public final String langPath;

        LeaderboardTimeframe(String configPath) {
            this.configPath = configPath;
            this.langPath = LangPaths.Leaderboards.PAGES + name();
        }
    }

    private class LeaderboardPositionLineWithPayout extends LeaderboardPositionLine {

        public LeaderboardPositionLineWithPayout(int position, String username, int score) {
            super(position, username, score);
        }

        @Override
        public String getLine() {
            try {
                String line = super.getLine();
                Payout payout = sortBy != LeaderboardTimeframe.LIFETIME ? Payout.getPayout(sortBy, position) : null;
                if (payout == null) {
                    return line;
                } else {
                    String payoutAmount = payout.getPayoutAmount();
                    try {
                        // if payout amount can be number, prefix with dollar sign
                        Integer.valueOf(payoutAmount);
                        payoutAmount = "$" + payoutAmount;
                    } catch (NumberFormatException ignored) {}

                    return line + " §7- §e§l" + payoutAmount;
                }
            } catch (SQLException e) {
                return super.getLine() + " §7- §cSQL ERR";
            }
        }
    }
}