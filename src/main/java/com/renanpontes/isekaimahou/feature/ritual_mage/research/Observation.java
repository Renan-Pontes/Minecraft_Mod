package com.renanpontes.isekaimahou.feature.ritual_mage.research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.ritual_mage.MageRegistries;
import com.renanpontes.isekaimahou.feature.ritual_mage.item.ResearchNoteItem;
import com.renanpontes.isekaimahou.feature.ritual_mage.network.MagePayloads;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Observação com a Lente Arcana (servidor). Ordem de prioridade dos candidatos:
 * entidade mirada → bloco/fluido mirado → ambiente (céu, bioma, dimensão, estrutura; os mais raros primeiro)
 * → lembranças de chefes. Escolhe o primeiro ainda não registrado; se todos já foram, aprofunda o estudo
 * de um que já passou do intervalo diário (1 nota extra).
 */
public final class Observation {
    /** Alcance da lente (blocos). */
    public static final double RANGE = 48.0;
    /** Intervalo para estudar o mesmo fenômeno de novo: 1 dia de jogo. */
    public static final long RESTUDY_TICKS = 24000L;

    public static ObservationContext context(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(RANGE));
        BlockHitResult solid = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        BlockHitResult fluidHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        double reach = solid.getType() == HitResult.Type.MISS ? RANGE : solid.getLocation().distanceTo(eye);
        Vec3 reachEnd = eye.add(look.scale(reach));
        AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, reachEnd, box,
                e -> !e.isSpectator() && e.isPickable() && e != player, reach * reach);

        Entity entity = entityHit != null ? entityHit.getEntity() : null;
        BlockState block = solid.getType() == HitResult.Type.BLOCK ? level.getBlockState(solid.getBlockPos()) : null;
        BlockState fluid = null;
        if (fluidHit.getType() == HitResult.Type.BLOCK) {
            BlockState s = level.getBlockState(fluidHit.getBlockPos());
            if (s.getBlock() instanceof LiquidBlock) fluid = s;
        }
        BlockPos eyePos = BlockPos.containing(eye);
        boolean openSky = player.getXRot() < -25.0F && solid.getType() == HitResult.Type.MISS && level.canSeeSky(eyePos);

        Vec3 focus;
        if (entity != null) focus = entity.getBoundingBox().getCenter();
        else if (fluid != null && fluidHit.getLocation().distanceToSqr(eye) <= reach * reach) focus = fluidHit.getLocation();
        else if (block != null) focus = solid.getLocation();
        else focus = eye.add(look.scale(10.0));
        return new ObservationContext(player, level, entity, block, fluid, openSky, eyePos, focus);
    }

    /** Ponto que a lente está focando (partículas durante o uso). */
    public static Vec3 focusPoint(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(RANGE));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        return hit.getType() == HitResult.Type.MISS ? eye.add(player.getViewVector(1.0F).scale(10.0)) : hit.getLocation();
    }

    /** Candidatos em ordem de prioridade. */
    static List<Phenomenon> candidates(ObservationContext ctx, MagicData magic) {
        List<Phenomenon> entityMatches = new ArrayList<>();
        List<Phenomenon> blockMatches = new ArrayList<>();
        List<Phenomenon> ambient = new ArrayList<>();
        List<Phenomenon> memories = new ArrayList<>();
        for (Phenomenon p : PhenomenaManager.all()) {
            if (p.hasTarget()) {
                if (p.matchesEntity(ctx) && p.contextOk(ctx)) entityMatches.add(p);
                else if (p.matchesBlock(ctx) && p.contextOk(ctx)) blockMatches.add(p);
                else if (p.memoryOk(magic)) memories.add(p);
            } else if (p.hasContext()) {
                if (p.contextOk(ctx)) ambient.add(p);
            } else if (p.memoryOk(magic)) {
                memories.add(p);
            }
        }
        ambient.sort(Comparator.comparingInt((Phenomenon p) -> -p.rarity.ordinal()));
        List<Phenomenon> out = new ArrayList<>(entityMatches);
        out.addAll(blockMatches);
        out.addAll(ambient);
        out.addAll(memories);
        return out;
    }

    /** Conclui uma observação (chamado quando o uso da lente termina). */
    public static void observe(ServerPlayer player) {
        ObservationContext ctx = context(player);
        MagicData magic = MagicApi.get(player);
        MageResearchData data = player.getData(MageRegistries.RESEARCH);
        List<Phenomenon> list = candidates(ctx, magic);
        long now = player.serverLevel().getGameTime();

        for (Phenomenon p : list) {
            if (!data.isDiscovered(p.id.toString())) {
                discover(player, ctx, data, p, now);
                return;
            }
        }
        for (Phenomenon p : list) {
            boolean memoryOnly = !p.matchesEntity(ctx) && !p.matchesBlock(ctx) && !(p.hasContext() && !p.hasTarget());
            if (!p.repeatable || memoryOnly) continue;
            if (now - data.lastObserved(p.id.toString()) >= RESTUDY_TICKS) {
                restudy(player, ctx, data, p, now);
                return;
            }
        }
        if (!list.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.mage.cooldown",
                    list.get(0).displayName()).withStyle(ChatFormatting.GRAY), true);
        } else {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.mage.nothing").withStyle(ChatFormatting.GRAY), true);
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SPYGLASS_STOP_USING, SoundSource.PLAYERS, 0.8F, 0.8F);
    }

    private static void discover(ServerPlayer player, ObservationContext ctx, MageResearchData data, Phenomenon p, long now) {
        data.observe(p.id.toString(), now);
        int notes = p.rarity.notes;
        for (int i = 0; i < notes; i++) {
            giveNote(player, ResearchNoteItem.of(p.id, p.elements.get(i % p.elements.size()), 1));
        }
        MagicApi.giveXp(player, p.rarity.xp, "research");
        MagicApi.addCounter(player, "mage_phenomena", 1);
        // XP dos elementos do fenômeno (fusões contam para os dois pais; só soma nos já despertados)
        Set<Element> bases = new LinkedHashSet<>();
        for (Element e : p.elements) {
            if (e.isBase()) bases.add(e);
            else if (e.isFusion()) { bases.add(e.parentA); bases.add(e.parentB); }
        }
        for (Element e : bases) MagicApi.giveElementXp(player, e, Math.max(2, p.rarity.xp / 3));
        MagePayloads.syncAtlas(player);

        player.sendSystemMessage(Component.translatable("message.isekai_mahou.mage.discovered",
                p.displayName().withStyle(ChatFormatting.BOLD)).withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.mage.discovered_detail",
                p.rarity.displayName(), elementList(p.elements), notes, p.rarity.xp).withStyle(ChatFormatting.GRAY));
        effects(ctx, p, true);
    }

    private static void restudy(ServerPlayer player, ObservationContext ctx, MageResearchData data, Phenomenon p, long now) {
        data.observe(p.id.toString(), now);
        Element e = p.elements.get(player.getRandom().nextInt(p.elements.size()));
        giveNote(player, ResearchNoteItem.of(p.id, e, 1));
        player.displayClientMessage(Component.translatable("message.isekai_mahou.mage.restudied", p.displayName())
                .withStyle(ChatFormatting.AQUA), true);
        effects(ctx, p, false);
    }

    private static void giveNote(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    public static MutableComponent elementList(List<Element> elements) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) out.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            out.append(elements.get(i).displayName());
        }
        return out;
    }

    /** Linha de luz do fenômeno até a lente, explosão no alvo e sons de "anotação". */
    private static void effects(ObservationContext ctx, Phenomenon p, boolean first) {
        ServerLevel level = ctx.level();
        ServerPlayer player = ctx.player();
        Vec3 lens = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.6)).subtract(0, 0.15, 0);
        Vec3 focus = ctx.focus();
        // A linha de partículas fica curta (no máx. 14 blocos) para não inundar a rede
        Vec3 lineStart = focus.distanceToSqr(lens) > 14 * 14 ? lens.add(focus.subtract(lens).normalize().scale(14)) : focus;
        for (Element e : p.elements) {
            ElementFx.line(level, lineStart, lens, e);
            ElementFx.burst(level, focus, e, first ? 24 : 10, 0.4);
        }
        level.sendParticles(Vfx.enchant(), lens.x, lens.y, lens.z, first ? 40 : 15, 0.3, 0.3, 0.3, 0.6);
        if (first) {
            level.sendParticles(Vfx.endRod(), player.getX(), player.getY() + 1.2, player.getZ(), 12, 0.5, 0.6, 0.5, 0.05);
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
            player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (p.rarity.ordinal() >= Phenomenon.Rarity.EPIC.ordinal()) {
                player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 1.4F);
            }
        } else {
            player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.2F);
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.3F);
        }
    }

    private Observation() {}
}
