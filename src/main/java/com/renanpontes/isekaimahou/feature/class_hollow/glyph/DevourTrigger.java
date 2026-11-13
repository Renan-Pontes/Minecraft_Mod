package com.renanpontes.isekaimahou.feature.class_hollow.glyph;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.class_hollow.Hollow;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowFx;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowSounds;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * "Ao Devorar uma Alma" (gatilho de origem do Hollow, Gillian; vai no começo da receita): o slot sai SOZINHO no instante
 * em que o Hollow devora uma alma (mata algo que tem alma), mirando o inimigo mais próximo — e fica mais forte quanto
 * maior a alma: +1% de poder por Alma devorada naquele abate (até +100%: chefes e jogadores dão o máximo). Cada disparo
 * gasta 1 Alma guardada (sem Almas, não sai). Apertar a tecla do slot não conjura. No máximo 1 disparo por segundo.
 * Segue as regras normais (mana, recarga, Silêncio). Só quem devora almas (a classe Hollow) consegue dispará-lo.
 */
public class DevourTrigger extends TriggerGlyph {
    public static final String PATH = "hollowfied_on_devour";
    public static final ResourceLocation ID = IsekaiMahou.id(PATH);
    private static final double AIM_RANGE = 20;

    /** > 0 enquanto este gatilho conjura (thread do servidor). */
    private static int autoDepth;
    /** Poder extra da conjuração automática em andamento. */
    private static float pendingBonus = 1F;
    /** Último disparo por jogador (tempo de jogo). */
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public DevourTrigger() {
        super(PATH, 6, true);
        origin(MagicPath.HOLLOW);
        element(Element.SHADOW);
        tier(3);
        minCircle(3);
    }

    /** Apertar o slot não conjura; na conjuração automática, aplica o bônus da alma. */
    public static void onPre(SpellCastEvent.Pre e) {
        if (!(e.getCaster() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (!e.getContext().spell().hasTrigger(PATH)) return;
        if (autoDepth > 0) {
            e.getContext().setPowerMult(e.getContext().powerMult() * pendingBonus);
            return;
        }
        if (!e.getContext().isPlayerCast()) return;
        e.setCanceled(true);
        p.displayClientMessage(Component.translatable("message.isekai_mahou.hollow.devour_trigger_only").withStyle(ChatFormatting.DARK_RED), true);
    }

    /** O Hollow devorou {@code souls} Almas: dispara os slots com o gatilho. */
    public static void fire(ServerPlayer p, int souls) {
        if (souls <= 0 || p.hasEffect(CoreRegistries.SILENCED)) return;
        long now = p.serverLevel().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < 20) return;
        MagicData d = MagicApi.get(p);
        int loadout = d.activeLoadout();
        float bonus = 1F + Math.min(1F, souls / 100F);
        int delay = 3;
        boolean any = false;
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellRecipe recipe = d.peekSlot(loadout, slot).main();
            if (recipe == null || recipe.isEmpty() || !leads(recipe)) continue;
            int s = slot;
            any = true;
            SpellScheduler.schedule(delay, () -> {
                if (!p.isRemoved() && p.isAlive()) autoCast(p, loadout, s, recipe, bonus);
            });
            delay += 5;
        }
        if (any) LAST.put(p.getUUID(), now);
    }

    private static void autoCast(ServerPlayer p, int loadout, int slot, SpellRecipe recipe, float bonus) {
        if (p.isSpectator() || p.hasEffect(CoreRegistries.SILENCED) || p.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) return;
        MagicData d = MagicApi.get(p);
        if (d.activeLoadout() != loadout) return;
        boolean creative = p.getAbilities().instabuild;
        if (!creative && Hollow.souls(d) < 1) return;
        Element el = SpellCost.primaryElement(recipe);
        SpellParser.Result r = SpellParser.parse(recipe, el, creative ? null : d);
        if (!r.ok()) return;
        ParsedSpell spell = r.spell();
        if (spell.isUltimate() != (slot == Progression.ULTIMATE_SLOT)) return;
        if (spell.isUltimate() && !creative && d.charge() < Progression.MAX_CHARGE) return;
        String cdKey = MagicData.slotKey(loadout, slot);
        if (p.serverLevel().getGameTime() < d.cooldowns.getOrDefault(cdKey, 0L)) return;
        if (!creative && d.mana() < spell.manaCost()) return;

        LivingEntity target = nearestEnemy(p);
        float yRot = p.getYRot(), xRot = p.getXRot(), headRot = p.getYHeadRot();
        if (target != null) {
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
            double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
            float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
            float pitch = (float) -(Mth.atan2(to.y, horiz) * Mth.RAD_TO_DEG);
            p.setYRot(yaw);
            p.setXRot(Mth.clamp(pitch, -90F, 90F));
            p.setYHeadRot(yaw);
        }
        autoDepth++;
        pendingBonus = bonus;
        boolean ok;
        try {
            ok = SpellCaster.performCast(p, d, spell.primary(), recipe, spell, cdKey, false);
        } finally {
            autoDepth--;
            pendingBonus = 1F;
            if (target != null) {
                p.setYRot(yRot);
                p.setXRot(xRot);
                p.setYHeadRot(headRot);
            }
        }
        if (!ok) return;
        if (!creative) MagicApi.addCounter(p, Hollow.SOULS, -1);
        ServerLevel sl = p.serverLevel();
        Vec3 m = p.getEyePosition();
        sl.sendParticles(HollowFx.REIATSU, m.x, m.y, m.z, 10, 0.3, 0.3, 0.3, 0.05);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, m.x, m.y - 0.2, m.z, 4, 0.2, 0.2, 0.2, 0.02);
        HollowFx.sound(sl, m, HollowSounds.DEVOUR.get(), 0.6F, 1.5F);
        p.displayClientMessage(Component.translatable("message.isekai_mahou.hollow.devour_trigger",
                Math.round((bonus - 1F) * 100)).withColor(Hollow.REIATSU_RGB), true);
    }

    @Nullable
    private static LivingEntity nearestEnemy(ServerPlayer p) {
        LivingEntity best = null;
        double bestD = AIM_RANGE * AIM_RANGE;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(AIM_RANGE),
                e -> e != p && e.isAlive() && e instanceof Enemy && p.hasLineOfSight(e))) {
            double dd = e.distanceToSqr(p);
            if (dd < bestD) {
                bestD = dd;
                best = e;
            }
        }
        return best;
    }

    /** A receita começa com "Ao Devorar uma Alma" (entre os gatilhos antes da forma)? */
    public static boolean leads(SpellRecipe recipe) {
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (!(g instanceof TriggerGlyph)) return false;
            if (ID.equals(id)) return true;
        }
        return false;
    }

    public static void forget(UUID id) {
        LAST.remove(id);
    }

    public static void clear() {
        autoDepth = 0;
        pendingBonus = 1F;
        LAST.clear();
    }
}
