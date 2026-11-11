package com.renanpontes.isekaimahou.feature.class_enchanter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * ANTI-MAGIA (homenagem: o lutador que magia nenhuma alcança). Encantamento de assinatura, só em si mesmo, com níveis
 * I–V:
 * <ul>
 *   <li>Dano de magia (tipo {@code isekai_mahou:spell} e o "mágico" do vanilla: poções, presas do evocador, raio do guardião)
 *   cai {@link #REDUCTION} — no V é anulado (contra chefes, no máximo 70%);</li>
 *   <li>condições e efeitos ruins de origem mágica falham com chance {@link #STATUS_BLOCK} (no V, sempre);</li>
 *   <li>em troca, as SUAS magias ofensivas causam só {@link #OWN} do dano, e manter custa recurso por segundo
 *   ({@link #upkeep}); sem recurso, a Anti-Magia se apaga.</li>
 * </ul>
 * Inverter: em um inimigo vira um selo — Silenciado e perde encantamentos/efeitos bons.
 * O Encantador também tem a passiva "Pele Antimágica" (−10% de dano mágico, +2% por rank), mesmo sem o encantamento.
 */
public final class AntiMagic {
    public static final float[] REDUCTION = {0.45F, 0.60F, 0.72F, 0.84F, 1.0F};
    public static final float[] STATUS_BLOCK = {0.35F, 0.55F, 0.75F, 0.90F, 1.0F};
    public static final float[] OWN = {0.60F, 0.50F, 0.45F, 0.40F, 0.35F};
    /** Teto contra chefes (golpe mágico de chefe sempre passa um pouco). */
    public static final float BOSS_CAP = 0.70F;

    private static final Map<UUID, Long> FX = new HashMap<>();
    private static final Map<UUID, Float> NULLIFIED = new HashMap<>();
    private static final Map<UUID, Long> WARN = new HashMap<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, LivingIncomingDamageEvent.class, AntiMagic::onIncoming);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, SpellDamageEvent.Pre.class, AntiMagic::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Applicable.class, AntiMagic::onApplicable);
    }

    public static int level(LivingEntity e) {
        return Enchants.level(e, EnchRegistries.ANTI_MAGIC);
    }

    private static float idx(float[] table, int lvl) {
        return table[Math.max(0, Math.min(table.length - 1, lvl - 1))];
    }

    // =====================================================================================
    //  Ativar (glifo)
    // =====================================================================================
    /** Liga/empilha a Anti-Magia no próprio conjurador. Devolve o nível. */
    public static int activate(SpellContext ctx, LivingEntity self, SpellStats stats) {
        int cap = Enchants.signatureCap(self);
        int cur = level(self);
        int add = 1 + Math.max(0, stats.amplify) + (cur == 0 ? Enchants.signatureBonus(self) : 0);
        int lvl = Math.max(cur, Math.min(cap, cur + add));
        int ticks = Enchants.duration(self, stats, 600);
        HeavenlyRestriction.end(self, false);
        Enchants.apply(self, self, EnchRegistries.ANTI_MAGIC, lvl - cur, cap, ticks);
        if (self.level() instanceof ServerLevel level) {
            Vec3 c = EnchFx.center(self);
            EnchFx.shatter(level, c, 1.4);
            EnchFx.ring(level, self.position().add(0, 0.1, 0), 1.6, EnchFx.ANTI, 28, 1.0F);
            EnchFx.ring(level, self.position().add(0, 1.0, 0), 1.2, EnchFx.WHITE, 20, 0.7F);
            EnchFx.sound(level, c, SoundEvents.BEACON_DEACTIVATE, 0.9F, 1.5F);
            EnchFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.7F);
            if (cur == 0 && !ctx.formStats().silent) {
                Announce.send(self, Component.translatable("announce.isekai_mahou.enchanter.anti_magic", roman(lvl)), EnchFx.ANTI,
                        Announce.STYLE_SPELL);
            }
        }
        // pulsos (Santuário/Elo) renovam sem repetir o aviso; só avisa quando liga ou muda de nível
        if (self instanceof ServerPlayer sp && lvl != cur) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.anti_magic_on", roman(lvl),
                    Math.round(idx(REDUCTION, lvl) * 100), Math.round(idx(OWN, lvl) * 100)).withStyle(ChatFormatting.AQUA), true);
        }
        return lvl;
    }

    /** Inverter num inimigo: selo anti-magia — Silenciado + arranca até 1+Amplificar efeitos bons. */
    public static void seal(SpellContext ctx, LivingEntity target, SpellStats stats) {
        Statuses.apply(target, Statuses.Kind.SILENCED, 1, stats.duration(60), ctx.caster(), ctx.powerMult());
        int strip = 1 + Math.max(0, stats.amplify);
        List<Holder<MobEffect>> good = new ArrayList<>();
        for (MobEffectInstance i : target.getActiveEffects()) {
            if (i.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) good.add(i.getEffect());
        }
        for (int k = 0; k < strip && k < good.size(); k++) target.removeEffect(good.get(k));
        ServerLevel level = ctx.level();
        EnchFx.shatter(level, EnchFx.center(target), Math.max(0.8, target.getBbWidth()));
        EnchFx.sound(level, target.position(), SoundEvents.GLASS_BREAK, 0.8F, 1.3F);
        EnchFx.sound(level, target.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7F, 0.6F);
    }

    // =====================================================================================
    //  Manutenção (a cada 1 s, chamada pelo efeito)
    // =====================================================================================
    /** Custa (1% + 0,4%/nível) do recurso máximo por segundo. @return false = acabou o recurso, a Anti-Magia apaga */
    public static boolean upkeep(LivingEntity e, int lvl) {
        if (!(e.level() instanceof ServerLevel level)) return true;
        if (e instanceof ServerPlayer sp && !sp.getAbilities().instabuild) {
            MagicData d = MagicApi.get(sp);
            float cost = d.maxMana() * (0.010F + 0.004F * lvl);
            if (!d.consumeMana(cost)) {
                d.setMana(0);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.anti_magic_off")
                        .withStyle(ChatFormatting.RED), true);
                EnchFx.sound(level, sp.position(), SoundEvents.BEACON_DEACTIVATE, 0.8F, 0.8F);
                EnchFx.shatter(level, EnchFx.center(sp), 1.0);
                return false;
            }
        }
        // véu discreto: hexágonos girando na altura do peito
        double a = e.tickCount * 0.15;
        Vec3 c = EnchFx.center(e);
        for (int i = 0; i < 6; i++) {
            double ang = a + Math.PI / 3 * i;
            level.sendParticles(EnchFx.dust(EnchFx.ANTI, 0.55F), c.x + Math.cos(ang) * 0.8, c.y + Math.sin(ang * 2) * 0.15,
                    c.z + Math.sin(ang) * 0.8, 1, 0, 0, 0, 0);
        }
        return true;
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================
    public static boolean isMagic(DamageSource src) {
        return src.is(CoreRegistries.SPELL_DAMAGE) || src.is(DamageTypeTags.WITCH_RESISTANT_TO);
    }

    /** Pele Antimágica do Encantador (passiva): 10% + 2% por rank. */
    public static float innate(LivingEntity e) {
        int rank = Enchants.rank(e);
        return rank < 0 ? 0F : 0.10F + 0.02F * rank;
    }

    private static void onIncoming(LivingIncomingDamageEvent e) {
        LivingEntity target = e.getEntity();
        if (!(target.level() instanceof ServerLevel level)) return;
        DamageSource src = e.getSource();
        if (!isMagic(src)) return;
        Entity attacker = src.getEntity();
        if (attacker == target) return;
        int lvl = level(target);
        float innate = innate(target);
        if (lvl <= 0 && innate <= 0F) return;
        float am = lvl > 0 ? idx(REDUCTION, lvl) : 0F;
        if (am > 0F && attacker instanceof LivingEntity le && Enchants.isBoss(le)) am = Math.min(am, BOSS_CAP);
        float total = 1F - (1F - innate) * (1F - am);
        float amount = e.getAmount();
        float prevented = amount * total;
        if (total >= 0.999F) e.setCanceled(true);
        else e.setAmount(amount - prevented);
        if (lvl > 0) {
            Vec3 at = src.getSourcePosition() != null ? EnchFx.center(target).lerp(src.getSourcePosition(), 0.25) : EnchFx.center(target);
            fx(level, target, at);
            if (target instanceof ServerPlayer sp) addNullified(sp, prevented);
        }
    }

    private static void fx(ServerLevel level, LivingEntity target, Vec3 at) {
        long now = level.getGameTime();
        Long last = FX.get(target.getUUID());
        if (last != null && now - last < 6) return;
        if (FX.size() > 512) FX.clear();
        FX.put(target.getUUID(), now);
        EnchFx.shatter(level, at, 0.9);
        EnchFx.sound(level, at, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.9F, 1.5F + level.random.nextFloat() * 0.3F);
    }

    /** Soma dano anulado (fração guardada até virar inteiro). */
    public static void addNullified(ServerPlayer sp, float amount) {
        if (!Enchants.isEnchanter(sp) || amount <= 0F) return;
        float acc = NULLIFIED.getOrDefault(sp.getUUID(), 0F) + amount;
        int whole = (int) acc;
        NULLIFIED.put(sp.getUUID(), acc - whole);
        if (whole > 0) MagicApi.addCounter(sp, Enchants.C_NULLIFIED, whole);
    }

    /** As próprias magias ofensivas de quem está com Anti-Magia ficam fracas. */
    private static void onSpellDamage(SpellDamageEvent.Pre e) {
        SpellContext ctx = e.ctx();
        LivingEntity caster = ctx.caster();
        if (caster == null || e.target() == caster || ctx.isAlly(e.target())) return;
        int lvl = level(caster);
        if (lvl <= 0) return;
        e.setAmount(e.getAmount() * idx(OWN, lvl));
        if (caster instanceof ServerPlayer sp) {
            long now = sp.serverLevel().getGameTime();
            Long last = WARN.get(sp.getUUID());
            if (last == null || now - last > 200) {
                WARN.put(sp.getUUID(), now);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.anti_magic_own",
                        Math.round(idx(OWN, lvl) * 100)).withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    /** Condições/efeitos ruins de origem mágica falham (chance por nível). */
    private static void onApplicable(MobEffectEvent.Applicable e) {
        LivingEntity target = e.getEntity();
        if (target.level().isClientSide) return;
        MobEffectInstance inst = e.getEffectInstance();
        Holder<MobEffect> eff = inst.getEffect();
        if (eff.value().getCategory() != MobEffectCategory.HARMFUL) return;
        int lvl = level(target);
        if (lvl <= 0) return;
        Entity source = e.getEffectSource();
        if (source == target) return;
        if (!magicalEffect(eff, source)) return;
        if (target.getRandom().nextFloat() >= idx(STATUS_BLOCK, lvl)) return;
        e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        if (target.level() instanceof ServerLevel level) {
            fx(level, target, EnchFx.center(target));
            if (target instanceof ServerPlayer sp) addNullified(sp, 3F);
        }
    }

    /** Efeito "mágico": do mod (condições, maldições...) ou vanilla vindo de bruxa/conjurador/poção/jogador. */
    private static boolean magicalEffect(Holder<MobEffect> eff, @Nullable Entity source) {
        var key = BuiltInRegistries.MOB_EFFECT.getKey(eff.value());
        if (key != null && key.getNamespace().equals(IsekaiMahou.MODID)) return true;
        return source instanceof ThrownPotion || source instanceof AreaEffectCloud || source instanceof Witch
                || source instanceof SpellcasterIllager || source instanceof Player
                || (source != null && BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).getNamespace().equals(IsekaiMahou.MODID));
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    static void clear() {
        FX.clear();
        NULLIFIED.clear();
        WARN.clear();
    }

    private AntiMagic() {}
}
