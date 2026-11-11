package com.renanpontes.isekaimahou.feature.ritual_druid.block;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.ritual_druid.DruidContent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Muda germinando: guarda o dono e, depois de {@link #GERMINATION_TICKS}, vira o Núcleo da Árvore Coração. */
public class AncestralSaplingBlockEntity extends BlockEntity {
    public static final int GERMINATION_TICKS = 70;

    @Nullable private UUID owner;
    private String ownerName = "";
    private int age;

    public AncestralSaplingBlockEntity(BlockPos pos, BlockState state) {
        super(DruidContent.ANCESTRAL_SAPLING_BE.get(), pos, state);
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(ServerPlayer player) {
        this.owner = player.getUUID();
        this.ownerName = player.getGameProfile().getName();
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AncestralSaplingBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        be.age++;
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        // Espiral de luz subindo pela muda, cada vez mais rápida
        if (be.age % 2 == 0) {
            double a = be.age * 0.45;
            double r = 0.9 - be.age / (double) GERMINATION_TICKS * 0.6;
            double h = (be.age % 20) / 20.0 * 1.4;
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, x + Math.cos(a) * r, y + h, z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.END_ROD, x - Math.cos(a) * r, y + h, z - Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        if (be.age % 20 == 0) {
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.6F + be.age / (float) GERMINATION_TICKS);
            sl.sendParticles(ParticleTypes.COMPOSTER, x, y + 0.2, z, 8, 0.4, 0.1, 0.4, 0.0);
        }
        if (be.age >= GERMINATION_TICKS) {
            HeartTreeCoreBlockEntity.germinate(sl, pos, be.owner, be.ownerName);
        } else {
            be.setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("owner_name", ownerName);
        tag.putInt("age", age);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        age = tag.getInt("age");
    }
}
