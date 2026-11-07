// TabListPing - Displays ping time in CraftBukkit/Spigot player list
// Copyright 2026 Bobcat00
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package com.bobcat00.tablistping;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

// This class formats player tab names with placeholders for name, display name,
// ping time, scoreboard team prefix/suffix, and PlaceholderAPI (if present).

public final class TabNameFormatter
{
    private static Method papiSetPlaceholdersMethod;
    private static boolean papiInitialized = false;

    // -------------------------------------------------------------------------

    // Constructor

    private TabNameFormatter()
    {
    }

    // -------------------------------------------------------------------------

    // Initialize PlaceholderAPI reflection hook

    private static void initPapi()
    {
        if (papiInitialized)
        {
            return;
        }
        papiInitialized = true;

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
        {
            try
            {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                papiSetPlaceholdersMethod = papiClass.getMethod("setPlaceholders", Player.class, String.class);
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    // -------------------------------------------------------------------------

    // Format tab list entry for a player

    public static String format(String template, Player player, int ping)
    {
        if (template == null || player == null)
        {
            return "";
        }

        // Fetch Scoreboard Team prefix and suffix
        String teamPrefix = "";
        String teamSuffix = "";

        Scoreboard scoreboard = player.getScoreboard();
        Team team = scoreboard.getEntryTeam(player.getName());
        if (team == null && Bukkit.getScoreboardManager() != null)
        {
            Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            team = mainScoreboard.getEntryTeam(player.getName());
        }

        if (team != null)
        {
            teamPrefix = team.getPrefix();
            teamSuffix = team.getSuffix();
        }

        // Replace built-in placeholders
        String result = template
                .replace("%teamprefix%", teamPrefix)
                .replace("%prefix%", teamPrefix)
                .replace("%teamsuffix%", teamSuffix)
                .replace("%suffix%", teamSuffix)
                .replace("%name%", player.getName())
                .replace("%displayname%", player.getDisplayName())
                .replace("%ping%", String.valueOf(ping));

        // Process PlaceholderAPI placeholders if available
        initPapi();
        if (papiSetPlaceholdersMethod != null)
        {
            try
            {
                Object papiResult = papiSetPlaceholdersMethod.invoke(null, player, result);
                if (papiResult instanceof String)
                {
                    result = (String) papiResult;
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        return ChatColor.translateAlternateColorCodes('&', result);
    }
}
