// TabListPing - Displays ping time in CraftBukkit/Spigot player list
// Copyright 2019 Bobcat00
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

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import com.earth2me.essentials.IEssentials;
import com.earth2me.essentials.User;
import net.ess3.api.IUser;
import net.ess3.api.events.AfkStatusChangeEvent;

// This class primarily handles updating each player's ping time in the tab list.

@SuppressWarnings("deprecation")
public final class Listeners implements Listener
{
    private TabListPing plugin;
    private DataStore dataStore;
    private IEssentials ess;
    private long minUpdateTime = 4500; // 4.5 seconds

    // -------------------------------------------------------------------------

    // Constructor

    public Listeners(TabListPing plugin)
    {
        this.plugin = plugin;
        this.dataStore = new DataStore(plugin);

        // Hook in to Essentials
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials != null && essentials.isEnabled())
        {
            ess = (IEssentials) essentials;
        }

        // Register events

        plugin.getServer().getPluginManager().registerEvent(PlayerJoinEvent.class, this, EventPriority.MONITOR,
                new EventExecutor()
                {
                    public void execute(Listener l, Event e)
                    {
                        onPlayerJoin((PlayerJoinEvent) e);
                    }
                },
                plugin, true); // ignoreCancelled=true

        plugin.getServer().getPluginManager().registerEvent(PlayerQuitEvent.class, this, EventPriority.MONITOR,
                new EventExecutor()
                {
                    public void execute(Listener l, Event e)
                    {
                        onPlayerQuit((PlayerQuitEvent) e);
                    }
                },
                plugin, true); // ignoreCancelled=true

        plugin.getServer().getPluginManager().registerEvent(PlayerChangedWorldEvent.class, this, EventPriority.MONITOR,
                new EventExecutor()
                {
                    public void execute(Listener l, Event e)
                    {
                        onChangedWorld((PlayerChangedWorldEvent) e);
                    }
                },
                plugin, true); // ignoreCancelled=true

        if (ess != null)
        {
            plugin.getServer().getPluginManager().registerEvent(AfkStatusChangeEvent.class, this, EventPriority.MONITOR,
                    new EventExecutor()
                    {
                        public void execute(Listener l, Event e)
                        {
                            onAfk((AfkStatusChangeEvent) e);
                        }
                    },
                    plugin, true); // ignoreCancelled=true
        }

        // Periodic task for updating Bedrock players
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateBedrockPlayers, 60L, 60L);
    }

    // -------------------------------------------------------------------------

    // Keep Alive from server to client. Always process this for Java players.

    public void processServerToClient(Player player)
    {
        if (BedrockHook.isBedrockPlayer(player))
        {
            return;
        }
        Long currentTime = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        dataStore.saveTime(uuid, currentTime);
    }

    // -------------------------------------------------------------------------

    // Keep Alive response from client to server. Only process if the time since
    // the last ping calculation is greater than minUpdateTime.

    public void processClientToServer(Player player)
    {
        if (BedrockHook.isBedrockPlayer(player))
        {
            return;
        }

        // Get time from hashmap and calculate ping time in msec
        Long currentTime = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        // Check if it's time to update ping time
        if ((currentTime - dataStore.getUpdateTime(uuid)) >= minUpdateTime)
        {
            final int pingTime = dataStore.calculatePing(uuid, currentTime);
            // Save time for update rate limiting
            dataStore.saveUpdateTime(uuid, currentTime);

            // Go to the main thread
            Bukkit.getScheduler().runTask(plugin, new Runnable()
            {
                @Override
                public void run()
                {
                    if (player.isOnline())
                    {
                        boolean afk = isAfk(player);
                        setTabList(player, pingTime, afk);
                    }
                }
            });
        }
    }

    // -------------------------------------------------------------------------

    // Periodically update Bedrock players ping in tab list

    private void updateBedrockPlayers()
    {
        for (Player player : plugin.getServer().getOnlinePlayers())
        {
            if (BedrockHook.isBedrockPlayer(player))
            {
                int ping = BedrockHook.getPing(player);
                boolean afk = isAfk(player);
                setTabList(player, ping, afk);
            }
        }
    }

    // -------------------------------------------------------------------------

    // Check AFK status

    private boolean isAfk(Player player)
    {
        if (ess != null)
        {
            User user = ess.getUser(player);
            if (user != null)
            {
                return user.isAfk();
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------

    // Set tab list - Call from main thread only

    private void setTabList(Player player, int ping, boolean afk)
    {
        String format = afk ? plugin.config.getFormatAfk() : plugin.config.getFormat();
        player.setPlayerListName(TabNameFormatter.format(format, player, ping));
    }

    // -------------------------------------------------------------------------

    // Player join event

    public void onPlayerJoin(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () ->
        {
            if (player.isOnline())
            {
                int ping = BedrockHook.isBedrockPlayer(player) ? BedrockHook.getPing(player) : 0;
                setTabList(player, ping, isAfk(player));
            }
        }, 10L);
    }

    // -------------------------------------------------------------------------

    // AFK change

    public void onAfk(AfkStatusChangeEvent event)
    {
        IUser user = event.getAffected();
        Player player = user.getBase();
        if (player.isOnline())
        {
            UUID uuid = player.getUniqueId();
            int ping = BedrockHook.isBedrockPlayer(player) ? BedrockHook.getPing(player) : dataStore.getPing(uuid);
            setTabList(player, ping, event.getValue());
        }
    }

    // -------------------------------------------------------------------------

    // Player quit or was kicked

    public void onPlayerQuit(PlayerQuitEvent event)
    {
        // Remove player's entry from hashmap
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        dataStore.removeUuid(uuid);
    }

    // -------------------------------------------------------------------------

    // Player changed world - Used for header/footer

    public void onChangedWorld(PlayerChangedWorldEvent event)
    {
        if (plugin.config.getEnableTps() && (plugin.tpsTask != null))
        {
            Player player = event.getPlayer();
            plugin.tpsTask.setHeaderFooter(player);
        }
    }

    // -------------------------------------------------------------------------

    // Get ping report (for command)

    public Map<String, Integer> getPingReport()
    {
        Map<String, Integer> treeMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Player player : plugin.getServer().getOnlinePlayers())
        {
            String name = player.getName();
            UUID uuid = player.getUniqueId();
            int ping = BedrockHook.isBedrockPlayer(player) ? BedrockHook.getPing(player) : dataStore.getPing(uuid);
            treeMap.put(name, ping);
        }

        return treeMap;
    }
}
