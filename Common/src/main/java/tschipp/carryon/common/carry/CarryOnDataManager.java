/*
 * GNU Lesser General Public License v3
 * Copyright (C) 2024 Tschipp
 * mrtschipp@gmail.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package tschipp.carryon.common.carry;

import net.minecraft.world.entity.player.Player;
import tschipp.carryon.Constants;
import tschipp.carryon.platform.Services;

public class CarryOnDataManager {

    public static CarryOnData getCarryData(Player player) {
        CarryOnData data = Services.PLATFORM.getCarryData(player);
        if (data == null || !data.isValid()) {
            Constants.LOG.warn("Invalid CarryOnData for player {}, returning empty", player.getName().getString());
            return new CarryOnData();
        }
        return data;
    }

    public static void setCarryData(Player player, CarryOnData data)
    {
        data.setSelected(player.getInventory().selected);
        data.setTick(player.tickCount);
        Services.PLATFORM.setCarryData(player, data);
    }

}
