package com.renanpontes.isekaimahou.feature.ritual_necro.block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.ritual_necro.NecroContent;
import com.renanpontes.isekaimahou.feature.ritual_necro.NecroRitual;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Estado do Altar Ossuário: valida o multibloco a cada segundo (estado LIT), mostra ao jogador o que falta
 * (partículas só para ele) e conduz a animação do rito (badaladas, almas espiralando dos cantos, raio final).
 * A recompensa fica guardada no jogador ({@link NecroRitual#PENDING}) e é entregue no fim — ou ao entrar
 * no mundo, se ele sair no meio.
 */
public class OssuaryAltarBlockEntity extends BlockEntity {
    /** Duração da animação do rito (ticks). */
    public static final int RITUAL_TICKS = 120;
    private static final int HIGHLIGHT_TICKS = 100;
    static final DustParticleOptions SOUL_DUST = new DustParticleOptions(new Vector3f(0.35F, 0.95F, 1.0F), 1.1F);
    private static final DustParticleOptions MISSING_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.25F, 0.2F), 1.3F);

    // ---- salvos ----
    private UUID owner;
    private int rank;
    private int activeTicks;

    // ---- temporários (servidor) ----
    private int validateCooldown;
    private UUID highlightViewer;
    private List<BlockPos> highlightRing = List.of();
    private List<BlockPos> highlightCorners = List.of();
    private int highlightTicks;

    public OssuaryAltarBlockEntity(BlockPos pos, BlockState state) {
        super(NecroContent.ALTAR_BE.get(), pos, state);
    }

    public boolean isActive() {
        return activeTicks > 0;
    }

    /** Progresso da animação (0..1), interpolado para o renderer. */
    public float progress(float partialTick) {
        return isActive() ? Mth.clamp((activeTicks + partialTick) / RITUAL_TICKS, 0F, 1F) : 0F;
    }

    // =====================================================================================
    //  Rito
    // =====================================================================================
    public void start(ServerPlayer player, int rank) {
        this.owner = player.getUUID();
        this.rank = rank;
        this.activeTicks = 1;
        this.highlightTicks = 0;
        sync();
    }

    /** Altar removido no meio do rito: conclui na hora para o dono (se estiver online). */
    void interrupt() {
        if (!isActive() || !(level instanceof ServerLevel server)) return;
        UUID o = owner;
        activeTicks = 0;
        owner = null;
        ServerPlayer p = o == null ? null : server.getServer().getPlayerList().getPlayer(o);
        if (p != null) NecroRitual.finishPending(p, null, null);
    }

    private void finish(ServerLevel level) {
        UUID o = owner;
        activeTicks = 0;
        rank = 0;
        owner = null;
        sync();
        Vec3 c = Vec3.atBottomCenterOf(worldPosition).add(0, 1.1, 0);
        level.sendParticles(Vfx.soulFlame(), c.x, c.y, c.z, 70, 0.5, 0.9, 0.5, 0.06);
        level.sendParticles(Vfx.sculkSoul(), c.x, c.y, c.z, 25, 0.6, 0.6, 0.6, 0.04);
        level.sendParticles(SOUL_DUST, c.x, c.y, c.z, 40, 1.4, 0.4, 1.4, 0);
        level.playSound(null, worldPosition, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2F, 0.5F);
        ServerPlayer p = o == null ? null : level.getServer().getPlayerList().getPlayer(o);
        if (p != null) NecroRitual.finishPending(p, level, worldPosition);
    }

    private void tickRitual(ServerLevel level) {
        activeTicks++;
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1.0, z = worldPosition.getZ() + 0.5;
        RandomSource r = level.random;
        if (activeTicks == 2) {
            level.playSound(null, worldPosition, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 1.2F, 0.6F);
            level.playSound(null, worldPosition, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.BLOCKS, 1.0F, 0.7F);
        }
        // As doze badaladas da meia-noite (comprimidas): 3 toques graves
        if (activeTicks % 22 == 6 && activeTicks < 70) {
            level.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.5F);
            level.playSound(null, worldPosition, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
        if (activeTicks % 10 == 0) {
            BlockPos corner = OssuaryStructure.corners(worldPosition).get(r.nextInt(4));
            level.playSound(null, corner.getX() + 0.5, corner.getY() + 1.0, corner.getZ() + 0.5, SoundEvents.SOUL_ESCAPE,
                    SoundSource.BLOCKS, 1.2F, 0.7F + r.nextFloat() * 0.5F);
        }
        if (activeTicks == 60) level.playSound(null, worldPosition, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 2.0F, 0.8F);
        if (activeTicks == 85) level.playSound(null, worldPosition, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.BLOCKS, 1.0F, 0.9F);
        if (activeTicks == RITUAL_TICKS - 10) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(x, worldPosition.getY() + 1, z);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            level.sendParticles(ParticleTypes.SONIC_BOOM, x, y + 0.8, z, 1, 0, 0, 0, 0);
        }
        if (activeTicks % 20 == 0) setChanged();
        if (activeTicks >= RITUAL_TICKS) finish(level);
    }

    // =====================================================================================
    //  Mostrar o que falta
    // =====================================================================================
    /** Marca para {@code viewer} os blocos que faltam (anel em vermelho, cantos em azul-alma) por alguns segundos. */
    public void highlight(ServerPlayer viewer, List<BlockPos> ring, List<BlockPos> corners) {
        if (ring.isEmpty() && corners.isEmpty()) return;
        highlightViewer = viewer.getUUID();
        highlightRing = new ArrayList<>(ring);
        highlightCorners = new ArrayList<>(corners);
        highlightTicks = HIGHLIGHT_TICKS;
    }

    private void tickHighlight(ServerLevel level) {
        highlightTicks--;
        if (highlightTicks % 5 != 0) return;
        ServerPlayer viewer = level.getServer().getPlayerList().getPlayer(highlightViewer);
        if (viewer == null || viewer.level() != level) {
            highlightTicks = 0;
            return;
        }
        for (BlockPos p : highlightRing) {
            level.sendParticles(viewer, MISSING_DUST, true, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
        }
        for (BlockPos p : highlightCorners) {
            level.sendParticles(viewer, SOUL_DUST, true, p.getX() + 0.5, p.getY() + 0.4, p.getZ() + 0.5, 5, 0.2, 0.3, 0.2, 0);
            level.sendParticles(viewer, Vfx.soulFlame(), true, p.getX() + 0.5, p.getY() + 0.3, p.getZ() + 0.5, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    // =====================================================================================
    //  Ticks
    // =====================================================================================
    public static void serverTick(Level level, BlockPos pos, BlockState state, OssuaryAltarBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        if (--be.validateCooldown <= 0) {
            be.validateCooldown = 20;
            boolean ok = OssuaryStructure.check(level, pos).complete();
            if (state.getValue(OssuaryAltarBlock.LIT) != ok) {
                level.setBlock(pos, state.setValue(OssuaryAltarBlock.LIT, ok), Block.UPDATE_ALL);
                if (ok) level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
            }
        }
        if (be.highlightTicks > 0) be.tickHighlight(server);
        if (be.activeTicks > 0) be.tickRitual(server);
    }

    /** Partículas no cliente: brilho de almas quando montado (mais forte à meia-noite) e a espiral do rito. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, OssuaryAltarBlockEntity be) {
        RandomSource r = level.random;
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        if (be.activeTicks > 0) {
            be.activeTicks = Math.min(RITUAL_TICKS, be.activeTicks + 1);
            be.ritualParticles(level, r);
            return;
        }
        if (!state.getValue(OssuaryAltarBlock.LIT)) return;
        boolean midnight = NecroRitual.isMidnight(level);
        if (r.nextInt(midnight ? 3 : 14) == 0) {
            BlockPos c = OssuaryStructure.corners(pos).get(r.nextInt(4));
            level.addParticle(Vfx.soulFlame(), c.getX() + 0.5 + (r.nextDouble() - 0.5) * 0.3, c.getY() + 0.7 + r.nextDouble() * 0.6,
                    c.getZ() + 0.5 + (r.nextDouble() - 0.5) * 0.3, 0, 0.015, 0);
        }
        if (r.nextInt(midnight ? 4 : 12) == 0) {
            double a = r.nextDouble() * Math.PI * 2;
            level.addParticle(SOUL_DUST, cx + Math.cos(a) * 0.4, pos.getY() + 0.86, cz + Math.sin(a) * 0.4, 0, 0, 0);
        }
        if (midnight && r.nextInt(18) == 0) {
            level.addParticle(Vfx.soul(), cx + (r.nextDouble() - 0.5) * 0.5, pos.getY() + 0.9, cz + (r.nextDouble() - 0.5) * 0.5,
                    0, 0.02, 0);
        }
    }

    /** Correntes de almas saem dos quatro cantos em espiral até o topo do altar (mais densas nos ranks altos); no fim, uma coluna. */
    private void ritualParticles(Level level, RandomSource r) {
        float t = activeTicks / (float) RITUAL_TICKS;
        int streams = 2 + Math.min(2, rank / 3);
        double cx = worldPosition.getX() + 0.5, cz = worldPosition.getZ() + 0.5;
        double topY = worldPosition.getY() + 1.1 + t * 0.6;
        List<BlockPos> corners = OssuaryStructure.corners(worldPosition);
        for (int i = 0; i < corners.size(); i++) {
            BlockPos c = corners.get(i);
            for (int k = 0; k < streams; k++) {
                double phase = ((activeTicks * 0.045) + i * 0.25 + k / (double) streams) % 1.0;
                double fx = c.getX() + 0.5 - cx, fz = c.getZ() + 0.5 - cz;
                double ang = phase * Math.PI * 0.75;
                double rad = 1.0 - phase;
                double px = cx + (fx * Math.cos(ang) - fz * Math.sin(ang)) * rad;
                double pz = cz + (fx * Math.sin(ang) + fz * Math.cos(ang)) * rad;
                double py = Mth.lerp(phase, c.getY() + 1.0, topY) + Math.sin(phase * Math.PI) * 0.8;
                level.addParticle(Vfx.soulFlame(), px, py, pz, 0, 0, 0);
                if (r.nextInt(4) == 0) level.addParticle(SOUL_DUST, px, py, pz, 0, 0, 0);
            }
        }
        double ring = activeTicks * 0.22;
        for (int i = 0; i < 3; i++) {
            double a = ring + i * Math.PI * 2 / 3;
            level.addParticle(Vfx.soul(), cx + Math.cos(a) * 1.6, worldPosition.getY() + 0.1, cz + Math.sin(a) * 1.6, 0, 0.01, 0);
        }
        if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.SCULK_SOUL, cx, topY, cz, (r.nextDouble() - 0.5) * 0.05, 0.03, (r.nextDouble() - 0.5) * 0.05);
        if (t > 0.75F) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(Vfx.soulFlame(), cx + (r.nextDouble() - 0.5) * 0.4, topY, cz + (r.nextDouble() - 0.5) * 0.4,
                        0, 0.12 + r.nextDouble() * 0.1, 0);
            }
        }
    }

    // =====================================================================================
    //  Salvar / sincronizar
    // =====================================================================================
    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putInt("rank", rank);
        tag.putInt("active", activeTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        rank = tag.getInt("rank");
        activeTicks = tag.getInt("active");
    }

    /** O cliente só precisa do rank e do andamento do rito (o dono fica no servidor). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("rank", rank);
        tag.putInt("active", activeTicks);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
