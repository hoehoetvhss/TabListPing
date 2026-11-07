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
import java.util.UUID;
import org.bukkit.entity.Player;

// This class handles detection and ping retrieval for Bedrock players
// connecting through Geyser or Floodgate.

public final class BedrockHook
{
    private static Method geyserApiMethod;
    private static Method geyserIsBedrockPlayerMethod;
    private static Method geyserConnectionByUuidMethod;
    private static Method geyserConnectionPingMethod;

    private static Method floodgateGetInstanceMethod;
    private static Method floodgateIsFloodgatePlayerMethod;

    private static boolean initialized = false;

    // -------------------------------------------------------------------------

    // Constructor

    private BedrockHook()
    {
    }

    // -------------------------------------------------------------------------

    // Initialize reflection handles for Geyser and Floodgate APIs

    private static void init()
    {
        if (initialized)
        {
            return;
        }
        initialized = true;

        // Try Geyser API
        try
        {
            Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            geyserApiMethod = geyserApiClass.getMethod("api");
            geyserIsBedrockPlayerMethod = geyserApiClass.getMethod("isBedrockPlayer", UUID.class);
            geyserConnectionByUuidMethod = geyserApiClass.getMethod("connectionByUuid", UUID.class);

            Class<?> geyserConnectionClass = Class.forName("org.geysermc.geyser.api.connection.GeyserConnection");
            geyserConnectionPingMethod = geyserConnectionClass.getMethod("ping");
        }
        catch (Throwable ignored)
        {
        }

        // Try Floodgate API
        try
        {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateGetInstanceMethod = floodgateApiClass.getMethod("getInstance");
            floodgateIsFloodgatePlayerMethod = floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class);
        }
        catch (Throwable ignored)
        {
        }
    }

    // -------------------------------------------------------------------------

    // Check if player is a Bedrock player (Geyser or Floodgate)

    public static boolean isBedrockPlayer(Player player)
    {
        if (player == null)
        {
            return false;
        }

        init();
        UUID uuid = player.getUniqueId();

        // Check Geyser API first
        if (geyserApiMethod != null && geyserIsBedrockPlayerMethod != null)
        {
            try
            {
                Object api = geyserApiMethod.invoke(null);
                if (api != null)
                {
                    Object result = geyserIsBedrockPlayerMethod.invoke(api, uuid);
                    if (result instanceof Boolean && (Boolean) result)
                    {
                        return true;
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        // Check Floodgate API next
        if (floodgateGetInstanceMethod != null && floodgateIsFloodgatePlayerMethod != null)
        {
            try
            {
                Object instance = floodgateGetInstanceMethod.invoke(null);
                if (instance != null)
                {
                    Object result = floodgateIsFloodgatePlayerMethod.invoke(instance, uuid);
                    if (result instanceof Boolean && (Boolean) result)
                    {
                        return true;
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        return false;
    }

    // -------------------------------------------------------------------------

    // Get Bedrock player ping in milliseconds

    public static int getPing(Player player)
    {
        if (player == null)
        {
            return 0;
        }

        init();
        UUID uuid = player.getUniqueId();

        // Check GeyserConnection ping first (direct RakNet latency)
        if (geyserApiMethod != null && geyserConnectionByUuidMethod != null && geyserConnectionPingMethod != null)
        {
            try
            {
                Object api = geyserApiMethod.invoke(null);
                if (api != null)
                {
                    Object connection = geyserConnectionByUuidMethod.invoke(api, uuid);
                    if (connection != null)
                    {
                        Object pingObj = geyserConnectionPingMethod.invoke(connection);
                        if (pingObj instanceof Number)
                        {
                            return ((Number) pingObj).intValue();
                        }
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        // Fallback to Bukkit/Spigot player.getPing() which receives Geyser forward-player-ping
        try
        {
            return player.getPing();
        }
        catch (Throwable ignored)
        {
            return 0;
        }
    }
}
