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

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import tschipp.carryon.Constants;
import tschipp.carryon.common.scripting.CarryOnScript;

import javax.annotation.Nullable;
import java.util.Optional;

public class CarryOnData {

    private CarryType type;
    private CompoundTag nbt;
    private boolean keyPressed = false;
    private CarryOnScript activeScript;
    private int selectedSlot = 0;


    public static final Codec<CarryOnData> CODEC = CompoundTag.CODEC.flatXmap(
            tag -> {
                try {
                    if (tag == null) {
                        Constants.LOG.debug("Received null CompoundTag for CarryOnData");
                        return DataResult.success(new CarryOnData(new CompoundTag()));
                    }
                    return DataResult.success(new CarryOnData(tag));
                } catch (Exception e) {
                    Constants.LOG.error("Failed to deserialize CarryOnData from NBT: {}", e.getMessage());
                    return DataResult.success(new CarryOnData(new CompoundTag()));
                }
            },
            carry -> {
                try {
                    if (carry == null) {
                        Constants.LOG.debug("CarryOnData is null during serialization");
                        return DataResult.success(new CompoundTag());
                    }
                    return DataResult.success(carry.getNbt());
                } catch (Exception e) {
                    Constants.LOG.error("Failed to serialize CarryOnData to NBT: {}", e.getMessage());
                    return DataResult.success(new CompoundTag());
                }
            }
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CarryOnData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static final String SERIALIZATION_KEY = "CarryOnData";

    public CarryOnData() {
        this(new CompoundTag());
    }

    public CarryOnData(CompoundTag data) {
        try {
            this.nbt = data;
            if (data.contains("type")) {
                this.type = CarryType.valueOf(data.getString("type"));
            } else {
                this.type = CarryType.INVALID;
            }

            if (data.contains("keyPressed")) {
                this.keyPressed = data.getBoolean("keyPressed");
            }

            if (data.contains("activeScript")) {
                try {
                    DataResult<CarryOnScript> res = CarryOnScript.CODEC.parse(NbtOps.INSTANCE, data.get("activeScript"));
                    this.activeScript = res.getOrThrow((s) -> {throw new RuntimeException("Failed to decode activeScript during CarryOnData serialization: " + s);});
                } catch (Exception e) {
                    Constants.LOG.error("Failed to decode activeScript: {}", e.getMessage());
                    this.activeScript = null;
                }
            }

            if (data.contains("selected")) {
                this.selectedSlot = data.getInt("selected");
            }

        } catch (Exception e) {
            Constants.LOG.error("Failed to construct CarryOnData from NBT", e);
            this.type = CarryType.INVALID;
            this.nbt = new CompoundTag();
            this.activeScript = null;
        }
    }

    public CompoundTag getNbt() {
        nbt.putString("type", type.toString());
        nbt.putBoolean("keyPressed", keyPressed);
        if (activeScript != null) {
            try {
                DataResult<Tag> res = CarryOnScript.CODEC.encodeStart(NbtOps.INSTANCE, activeScript);
                Tag tag = res.getOrThrow((s) -> {throw new RuntimeException("Failed to encode activeScript during CarryOnData serialization: " + s);});
                nbt.put("activeScript", tag);
            } catch (Exception e) {
                Constants.LOG.error("Failed to encode activeScript: {}", e.getMessage());
                nbt.remove("activeScript");
            }
        }
        nbt.putInt("selected", this.selectedSlot);
        return nbt;
    }

    public CompoundTag getContentNbt() {
        if (type == CarryType.BLOCK && nbt.contains("block")) {
            return nbt.getCompound("block");
        } else if (type == CarryType.ENTITY && nbt.contains("entity")) {
            return nbt.getCompound("entity");
        }
        return null;
    }

    public void setBlock(BlockState state, @Nullable BlockEntity tile) {
        try {
            this.type = CarryType.BLOCK;

            if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                state = state.setValue(BlockStateProperties.WATERLOGGED, false);
            }

            CompoundTag stateData = NbtUtils.writeBlockState(state);
            nbt.put("block", stateData);

            if (tile != null) {
                try {
                    CompoundTag tileData = tile.saveWithId(tile.getLevel().registryAccess());
                    nbt.put("tile", tileData);
                } catch (Exception e) {
                    Constants.LOG.error("Failed to save block entity data: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to set block in CarryOnData: {}", e.getMessage());
            this.clear();
        }
    }

    public BlockState getBlock() {
        if (this.type != CarryType.BLOCK) {
            Constants.LOG.error("Called getBlock on data that contained {}", this.type);
            throw new IllegalStateException("Called getBlock on data that contained " + this.type);
        }

        try {
            return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), nbt.getCompound("block"));
        } catch (Exception e) {
            Constants.LOG.error("Failed to read block state from NBT: {}", e.getMessage());
            throw new IllegalStateException("Failed to read block state from NBT: " + e.getMessage());
        }
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos, HolderLookup.Provider lookup) {
        if (this.type != CarryType.BLOCK) {
            Constants.LOG.error("Called getBlockEntity on data that contained {}", this.type);
            throw new IllegalStateException("Called getBlockEntity on data that contained " + this.type);
        }

        if (!nbt.contains("tile")) {
            return null;
        }

        try {
            return BlockEntity.loadStatic(pos, this.getBlock(), nbt.getCompound("tile"), lookup);
        } catch (Exception e) {
            Constants.LOG.error("Failed to load block entity from NBT: {}", e.getMessage());
            return null;
        }
    }

    public void setEntity(Entity entity) {
        try {
            this.type = CarryType.ENTITY;
            CompoundTag entityData = new CompoundTag();

            entity.save(entityData);

            entityData.putDouble("PosX", entity.getX());
            entityData.putDouble("PosY", entity.getY());
            entityData.putDouble("PosZ", entity.getZ());
            entityData.putFloat("RotationYaw", entity.getYRot());
            entityData.putFloat("RotationPitch", entity.getXRot());

            nbt.put("entity", entityData);

        } catch (Exception e) {
            Constants.LOG.error("Failed to set entity in CarryOnData: {}", e.getMessage());
            this.clear();
        }
    }

    public Entity getEntity(Level level) {
        if (this.type != CarryType.ENTITY) {
            Constants.LOG.error("Called getEntity on data that contained {}", this.type);
            throw new IllegalStateException("Called getEntity on data that contained " + this.type);
        }

        if (!nbt.contains("entity")) {
            Constants.LOG.error("NBT missing 'entity' tag for ENTITY type");
            this.clear();
            return new AreaEffectCloud(level, 0, 0, 0);
        }

        try {
            var optionalEntity = EntityType.create(nbt.getCompound("entity"), level);
            if (optionalEntity.isPresent()) {
                Entity entity = optionalEntity.get();

                CompoundTag entityData = nbt.getCompound("entity");
                if (entityData.contains("PosX")) {
                    entity.setPos(
                            entityData.getDouble("PosX"),
                            entityData.getDouble("PosY"),
                            entityData.getDouble("PosZ")
                    );
                }

                return entity;
            }

            Constants.LOG.error("EntityType.create returned empty for entity data");
            this.clear();
            return new AreaEffectCloud(level, 0, 0, 0);
        } catch (Exception e) {
            Constants.LOG.error("Failed to create entity from NBT: {}", e.getMessage());
            this.clear();
            return new AreaEffectCloud(level, 0, 0, 0);
        }
    }

    public Optional<CarryOnScript> getActiveScript() {
        if (activeScript == null) {
            return Optional.empty();
        }
        return Optional.of(activeScript);
    }

    public void setActiveScript(CarryOnScript script) {
        this.activeScript = script;
    }

    public void setCarryingPlayer() {
        this.type = CarryType.PLAYER;
    }

    public boolean isCarrying() {
        return this.type != CarryType.INVALID;
    }

    public boolean isCarrying(CarryType type) {
        return this.type == type;
    }

    public boolean isKeyPressed() {
        return this.keyPressed;
    }

    public void setKeyPressed(boolean val) {
        this.keyPressed = val;
        this.nbt.putBoolean("keyPressed", val);
    }

    public void setSelected(int selectedSlot) {
        this.selectedSlot = selectedSlot;
    }

    public int getSelected() {
        return this.selectedSlot;
    }

    public boolean isValid() {
        try {
            if (type == null) {
                Constants.LOG.debug("CarryOnData has null type");
                return false;
            }

            if (nbt == null) {
                Constants.LOG.debug("CarryOnData has null nbt");
                return false;
            }

            if (type == CarryOnData.CarryType.ENTITY && nbt.contains("entity")) {
                CompoundTag entityTag = nbt.getCompound("entity");
                if (entityTag.isEmpty()) {
                    Constants.LOG.debug("CarryOnData has empty entity tag");
                    return false;
                }

                if (!entityTag.contains("id")) {
                    Constants.LOG.debug("Entity tag missing id");
                    return false;
                }
            }

            if (type == CarryOnData.CarryType.BLOCK && nbt.contains("block")) {
                CompoundTag blockTag = nbt.getCompound("block");
                if (blockTag.isEmpty()) {
                    Constants.LOG.debug("CarryOnData has empty block tag");
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            Constants.LOG.error("Error validating CarryOnData", e);
            return false;
        }
    }

    public void clear() {
        this.type = CarryType.INVALID;
        this.nbt = new CompoundTag();
        this.activeScript = null;
        this.keyPressed = false;
        this.selectedSlot = 0;
    }

    public CarryOnData clone() {
        return new CarryOnData(nbt.copy());
    }

    public int getTick() {
        if (!this.nbt.contains("tick")) {
            return -1;
        }
        return this.nbt.getInt("tick");
    }

    public void setTick(int tick) {
        this.nbt.putInt("tick", tick);
    }

    public CarryType getType() {
        return this.type;
    }

    public enum CarryType {
        BLOCK,
        ENTITY,
        PLAYER,
        INVALID
    }
}
