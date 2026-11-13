package com.renanpontes.isekaimahou.feature.space_magic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.afterlife.AfterlifeManager;
import com.renanpontes.isekaimahou.feature.afterlife.CorpseIndex;
import com.renanpontes.isekaimahou.feature.afterlife.entity.CorpseEntity;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * <b>Recuperar Pertences</b> — "eu daria até magia pra recuperar os itens à distância mas só lvl alto".
 * Glifo de pergaminho (Círculo 7), caro e com recarga longa: puxa pelo espaço os itens da sua ÚLTIMA morte — o Corpo
 * (módulo afterlife) e/ou os itens que ficaram soltos no chão — de qualquer distância e dimensão.
 * <p>
 * O lugar da morte pode estar descarregado: a magia segura o chunk por alguns segundos (bilhete temporário) e conclui
 * quando as entidades de lá carregam. Os itens nunca existem em dois lugares: saem do Corpo/chão direto para o jogador.
 * Dados no jogador ({@link SpaceData#tag}): {@code death} (onde e quais itens ficaram soltos) e {@code recover_until}.
 */
public final class RecoverBelongings {
    public static final ResourceLocation ID = IsekaiMahou.id("recover_belongings");
    /** Recarga depois de recuperar (tempo de jogo): 10 min. */
    public static final int COOLDOWN_TICKS = 10 * 60 * 20;
    /** Quanto a magia espera o lugar da morte carregar. */
    private static final int WAIT_TICKS = 120;
    /** Máximo de itens soltos lembrados por morte. */
    private static final int MAX_LOOSE = 96;
    /** Folga depois que o chunk do corpo carrega (os vizinhos, com itens espalhados, carregam logo em seguida). */
    private static final int LOAD_GRACE = 8;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("isekai_mahou_recover", Comparator.comparingLong(ChunkPos::toLong),
            WAIT_TICKS + 40);

    /** Uma recuperação em andamento. {@code readyAt}: quando o lugar carregou (+ uma folga para os chunks vizinhos). */
    private static final class Pending {
        final UUID player;
        final ResourceKey<Level> dim;
        final BlockPos pos;
        @Nullable final UUID corpse;
        final List<UUID> loose;
        final long deadline;
        final float refund;
        long readyAt = -1L;

        Pending(UUID player, ResourceKey<Level> dim, BlockPos pos, @Nullable UUID corpse, List<UUID> loose, long deadline, float refund) {
            this.player = player;
            this.dim = dim;
            this.pos = pos;
            this.corpse = corpse;
            this.loose = loose;
            this.deadline = deadline;
            this.refund = refund;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RecoverBelongings::onDrops);
        NeoForge.EVENT_BUS.addListener(RecoverBelongings::onServerTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> PENDING.clear());
    }

    // =====================================================================================
    //  O glifo
    // =====================================================================================
    public static class Effect extends EffectGlyph {
        public Effect() {
            super("recover_belongings", 200);
            element(Element.ARCANE);
            origin(MagicPath.NONE);
            tier(5);
            minCircle(7);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public int hintDuration() {
            return -1;
        }

        @Override
        public float hintRadius() {
            return -1;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerPlayer sp = ctx.casterPlayer();
            if (sp == null || !ArcaneUtil.firstThisTick(ctx, "recover_belongings")) return;
            float refund = cost() * SpellCost.COST_SCALE * 0.8F;
            if (!begin(sp, refund)) MagicApi.get(sp).addMana(refund);
        }
    }

    /** Começa a recuperação. false = nada a fazer agora (o chamador devolve a mana). */
    static boolean begin(ServerPlayer sp, float refund) {
        MinecraftServer server = sp.getServer();
        if (server == null || AfterlifeManager.isSpirit(sp)) return false;
        for (Pending p : PENDING) if (p.player.equals(sp.getUUID())) return false;
        long left = cooldownLeft(sp);
        if (left > 0 && !sp.getAbilities().instabuild) {
            long s = (left + 19) / 20;
            actionbar(sp, ChatFormatting.RED, "recover_cooldown", (s / 60) + ":" + String.format("%02d", s % 60));
            return false;
        }
        CorpseIndex.Entry corpse = CorpseIndex.latest(server, sp.getUUID());
        CompoundTag death = SpaceData.tag(sp).getCompound("death");
        List<UUID> loose = new ArrayList<>();
        ListTag ids = death.getList("ids", Tag.TAG_INT_ARRAY);
        for (Tag t : ids) loose.add(NbtUtils.loadUUID(t));
        ResourceLocation looseDim = ResourceLocation.tryParse(death.getString("dim"));

        ResourceKey<Level> dim;
        BlockPos pos;
        if (corpse != null) {
            dim = corpse.dim();
            pos = corpse.pos();
            // itens soltos só contam se forem da mesma morte (mesmo lugar do corpo)
            if (looseDim == null || !looseDim.equals(dim.location()) || BlockPos.of(death.getLong("pos")).distSqr(pos) > 64 * 64) loose.clear();
        } else if (!loose.isEmpty() && looseDim != null) {
            dim = ResourceKey.create(Registries.DIMENSION, looseDim);
            pos = BlockPos.of(death.getLong("pos"));
        } else {
            actionbar(sp, ChatFormatting.RED, "recover_nothing");
            return false;
        }
        ServerLevel level = server.getLevel(dim);
        if (level == null) {
            actionbar(sp, ChatFormatting.RED, "recover_nothing");
            return false;
        }
        level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(pos), 2, new ChunkPos(pos));
        PENDING.add(new Pending(sp.getUUID(), dim, pos, corpse == null ? null : corpse.corpse(), loose,
                server.overworld().getGameTime() + WAIT_TICKS, refund));
        actionbar(sp, ChatFormatting.LIGHT_PURPLE, "recover_start");
        ServerLevel here = sp.serverLevel();
        ArcaneUtil.ring(here, sp.position().add(0, 0.1, 0), 1.4, ArcaneUtil.dust(SpaceGlyphs.SPACE_COLOR, 1.1F), 28);
        here.sendParticles(ParticleTypes.REVERSE_PORTAL, sp.getX(), sp.getY() + 1.0, sp.getZ(), 40, 0.5, 0.6, 0.5, 0.05);
        ArcaneUtil.sound(here, sp.position(), SoundEvents.ENDER_CHEST_OPEN, 0.8F, 0.6F);
        return true;
    }

    public static long cooldownLeft(ServerPlayer sp) {
        MinecraftServer server = sp.getServer();
        if (server == null) return 0;
        return Math.max(0L, SpaceData.tag(sp).getLong("recover_until") - server.overworld().getGameTime());
    }

    public static void resetCooldown(ServerPlayer sp) {
        SpaceData.tag(sp).remove("recover_until");
    }

    // =====================================================================================
    //  Conclusão (quando o lugar da morte carrega)
    // =====================================================================================
    private static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
            Pending p = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(p.player);
            ServerLevel level = server.getLevel(p.dim);
            if (sp == null || level == null || !sp.isAlive()) {
                it.remove();
                continue;
            }
            boolean loaded = level.areEntitiesLoaded(ChunkPos.asLong(p.pos));
            if (loaded && p.readyAt < 0) p.readyAt = now + LOAD_GRACE;
            if (loaded ? now < p.readyAt : now < p.deadline) continue;
            it.remove();
            if (!loaded) {
                MagicApi.get(sp).addMana(p.refund);
                actionbar(sp, ChatFormatting.RED, "recover_failed");
                continue;
            }
            finish(server, level, sp, p, now);
        }
    }

    private static void finish(MinecraftServer server, ServerLevel level, ServerPlayer sp, Pending p, long now) {
        int stacks = 0;
        if (p.corpse != null) {
            if (level.getEntity(p.corpse) instanceof CorpseEntity c && c.isAlive() && c.isOwner(sp) && !c.inChallenge()) {
                for (int i = 0; i < c.container().getContainerSize(); i++) if (!c.container().getItem(i).isEmpty()) stacks++;
                c.restoreTo(sp);
            } else if (level.getEntity(p.corpse) == null) {
                // o corpo sumiu por fora (alguém já pegou tudo): a entrada velha sai do índice para a próxima tentativa
                CorpseIndex.remove(server, p.corpse);
            }
        }
        for (UUID id : p.loose) {
            if (!(level.getEntity(id) instanceof ItemEntity ie) || !ie.isAlive() || ie.getItem().isEmpty()) continue;
            ItemStack s = ie.getItem().copy();
            ie.discard();
            stacks++;
            if (!sp.getInventory().add(s) || !s.isEmpty()) {
                ItemEntity drop = sp.drop(s, false);
                if (drop != null) {
                    drop.setNoPickUpDelay();
                    drop.setTarget(sp.getUUID());
                }
            }
        }
        if (stacks == 0) {
            MagicApi.get(sp).addMana(p.refund);
            actionbar(sp, ChatFormatting.RED, "recover_nothing");
            return;
        }
        SpaceData.tag(sp).remove("death");
        if (!sp.getAbilities().instabuild) SpaceData.tag(sp).putLong("recover_until", now + COOLDOWN_TICKS);
        sp.inventoryMenu.broadcastChanges();
        ServerLevel here = sp.serverLevel();
        var dust = ArcaneUtil.dust(SpaceGlyphs.SPACE_COLOR, 1.2F);
        ArcaneUtil.ring(here, sp.position().add(0, 0.1, 0), 1.2, dust, 24);
        ArcaneUtil.teleportFx(here, sp.position(), sp.getBbHeight(), dust);
        ArcaneUtil.sound(here, sp.position(), SoundEvents.ENDER_CHEST_CLOSE, 0.8F, 0.7F);
        ArcaneUtil.sound(here, sp.position(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8F, 1.0F);
        actionbar(sp, ChatFormatting.LIGHT_PURPLE, "recover_done", stacks);
    }

    // =====================================================================================
    //  Lembra o que ficou solto no chão na morte (sem Corpo, ou Corpo cheio)
    // =====================================================================================
    private static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        CompoundTag tag = SpaceData.tag(sp);
        ListTag ids = new ListTag();
        for (ItemEntity ie : event.getDrops()) {
            if (ids.size() >= MAX_LOOSE) break;
            if (!ie.getItem().isEmpty()) ids.add(NbtUtils.createUUID(ie.getUUID()));
        }
        // cada morte substitui a anterior: a magia traz só os itens da ÚLTIMA
        CompoundTag death = new CompoundTag();
        death.putString("dim", sp.level().dimension().location().toString());
        death.putLong("pos", sp.blockPosition().asLong());
        death.put("ids", ids);
        tag.put("death", death);
    }

    private static void actionbar(ServerPlayer sp, ChatFormatting color, String key, Object... args) {
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.space_magic." + key, args).withStyle(color), true);
    }

    private RecoverBelongings() {}
}
