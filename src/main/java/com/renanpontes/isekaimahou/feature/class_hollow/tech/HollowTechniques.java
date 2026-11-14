package com.renanpontes.isekaimahou.feature.class_hollow.tech;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.class_hollow.Hollow;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowFx;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowNetwork;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowSounds;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowState;
import com.renanpontes.isekaimahou.feature.class_hollow.Hollowfication;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * v1.4 — o controle das TÉCNICAS INATAS do Hollow (servidor): a tecla "Instinto Hollow" (apertar/soltar, com carga) e
 * a Roda Hollow. Cada técnica tem recarga própria (não usa slot nem receita), custa Vigor e passa por um
 * {@link SpellContext} feito do glifo antigo da técnica (assim o poder do rank/Resurrección/fome, as Leis de Domínio que
 * anulam magia, o Silêncio e as regras de aliados continuam valendo). Ver a tabela em {@link HollowTech}.
 * <p>
 * Estado em memória: a tecla segurada, a invocação em andamento (Garganta/Resurrección), a Negación e as recargas
 * (as recargas sobrevivem a sair e voltar na mesma sessão do servidor).
 */
public final class HollowTechniques {
    private static final HollowTech[] TECHS = HollowTech.values();
    /** Ticks de invocação da Garganta e da Resurrección. */
    private static final int GARGANTA_CHANNEL = 30, RELEASE_CHANNEL = 36;
    /** Duração da Negación (a coluna de luz). */
    private static final int NEGACION_TICKS = 60;
    /** Duração da forma liberada. */
    public static final int RELEASE_TICKS = 1200;

    private static final class St {
        boolean holding, consumed, chargeFx, fullNotified, oscurasNotified;
        long pressAt;
        /** 0 = normal (Bala/Cero), 1 = agachado (Pesquisa/Gran Rey). */
        int mode;
        final long[] cd = new long[TECHS.length];
        int sonidoChain;
        long sonidoWindow;
        @Nullable
        HollowTech channel;
        long channelEnd;
        long negacionEnd;
        long lastWarn;

        boolean idle(long now) {
            if (holding || channel != null || negacionEnd != 0) return false;
            for (long c : cd) if (c > now) return false;
            return true;
        }
    }

    private static final Map<UUID, St> STATES = new HashMap<>();

    private static St st(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), k -> new St());
    }

    private static long now(ServerPlayer sp) {
        return sp.serverLevel().getGameTime();
    }

    // =====================================================================================
    //  Entrada (pacote do cliente)
    // =====================================================================================
    public static void onInput(ServerPlayer sp, int action, int value) {
        if (!Hollow.isHollow(sp)) return;
        St s = st(sp);
        switch (action) {
            case HollowNetwork.TECH_PRESS -> press(sp, s);
            case HollowNetwork.TECH_RELEASE -> release(sp, s);
            case HollowNetwork.TECH_WHEEL -> {
                HollowTech t = HollowTech.byIndex(value);
                if (t != null && t.wheel) wheel(sp, s, t);
            }
            default -> { }
        }
    }

    /** Pode agir agora? (avisa o porquê, no máximo 1×/s) */
    private static boolean blocked(ServerPlayer sp, St s) {
        if (!sp.isAlive() || sp.isSpectator() || Hollowfication.inProgress(sp)) return true;
        if (HollowState.inNegacion(sp) || s.channel != null) return true;
        String why = null;
        if (sp.hasEffect(CoreRegistries.SILENCED)) why = "tech_silenced";
        else if (sp.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) why = "tech_exhausted";
        if (why == null) return false;
        warn(sp, s, Hollow.msg(why).withStyle(ChatFormatting.DARK_GRAY));
        return true;
    }

    private static void warn(ServerPlayer sp, St s, net.minecraft.network.chat.Component c) {
        long n = now(sp);
        if (n - s.lastWarn < 15) return;
        s.lastWarn = n;
        sp.displayClientMessage(c, true);
    }

    private static boolean unlocked(ServerPlayer sp, HollowTech t) {
        return sp.getAbilities().instabuild && t != HollowTech.RESURRECCION && t != HollowTech.CERO_OSCURAS
                || t.unlocked(MagicApi.get(sp), HollowState.released(sp) != null);
    }

    private static boolean ready(ServerPlayer sp, St s, HollowTech t, boolean tell) {
        long left = s.cd[t.ordinal()] - now(sp);
        if (left <= 0) return true;
        if (tell) warn(sp, s, Hollow.msg("tech_cd", t.displayName(), String.format(java.util.Locale.ROOT, "%.1f", left / 20F))
                .withStyle(ChatFormatting.GRAY));
        return false;
    }

    private static void cooldown(ServerPlayer sp, St s, HollowTech t, int ticks) {
        s.cd[t.ordinal()] = now(sp) + ticks;
        PacketDistributor.sendToPlayer(sp, new HollowNetwork.TechCd(t.ordinal(), ticks, t == HollowTech.SONIDO ? s.sonidoChain : 0));
    }

    private static boolean canPay(ServerPlayer sp, St s, float fraction) {
        if (sp.getAbilities().instabuild) return true;
        MagicData d = MagicApi.get(sp);
        if (d.mana() >= d.maxMana() * fraction) return true;
        warn(sp, s, MagicApi.noResourceMessage(sp).withStyle(ChatFormatting.RED));
        return false;
    }

    /**
     * Abre o contexto da técnica (a "conjuração" dela): posta {@link SpellCastEvent.Pre} — Leis de Domínio, Silêncio,
     * Tempo Parado etc. podem cancelar — e já traz o poder do rank/Resurrección/fome (HollowEvents). null = cancelada.
     */
    @Nullable
    static SpellContext open(ServerPlayer sp, HollowTech t) {
        Glyph g = GlyphRegistry.get(t.legacyId());
        if (g == null) return null;
        SpellRecipe recipe = new SpellRecipe(List.of(t.legacyId()), t.displayName().getString(), t.rgb, "");
        SpellParser.Result r = SpellParser.parse(recipe, null, null);
        if (!r.ok()) return null;
        Element el = g.element();
        SpellContext ctx = new SpellContext(sp.serverLevel(), sp, el, recipe, r.spell());
        ctx.markPlayerCast();
        if (NeoForge.EVENT_BUS.post(new SpellCastEvent.Pre(sp, ctx)).isCanceled()) return null;
        return ctx;
    }

    // =====================================================================================
    //  A tecla "Instinto Hollow"
    // =====================================================================================
    private static void press(ServerPlayer sp, St s) {
        if (s.holding || blocked(sp, s)) return;
        s.holding = true;
        s.consumed = false;
        s.chargeFx = false;
        s.fullNotified = false;
        s.oscurasNotified = false;
        s.pressAt = now(sp);
        // correndo: Sonído na hora (se já sabe)
        if (sp.isSprinting() && unlocked(sp, HollowTech.SONIDO)) {
            s.consumed = true;
            sonido(sp, s);
            return;
        }
        s.mode = sp.isShiftKeyDown() ? 1 : 0;
    }

    private static void release(ServerPlayer sp, St s) {
        if (!s.holding) return;
        s.holding = false;
        if (s.chargeFx) stopCharge(sp);
        if (s.consumed) return;
        s.consumed = true;
        if (blocked(sp, s)) return;
        int held = (int) (now(sp) - s.pressAt);
        if (s.mode == 1) releaseSneak(sp, s, held);
        else releaseNormal(sp, s, held);
    }

    private static void releaseNormal(ServerPlayer sp, St s, int held) {
        if (held < HollowTech.CERO_MIN || !ready(sp, s, HollowTech.CERO, false)) {
            if (held >= HollowTech.CERO_MIN) ready(sp, s, HollowTech.CERO, true);
            bala(sp, s);
            return;
        }
        if (held >= HollowTech.OSCURAS_FULL && unlocked(sp, HollowTech.CERO_OSCURAS) && ready(sp, s, HollowTech.CERO_OSCURAS, false)) {
            if (!canPay(sp, s, 0.35F)) return;
            SpellContext ctx = open(sp, HollowTech.CERO_OSCURAS);
            if (ctx == null) return;
            Hollow.spendVigor(sp, 0.35F);
            HollowMoves.oscuras(ctx);
            cooldown(sp, s, HollowTech.CERO_OSCURAS, 1200);
            cooldown(sp, s, HollowTech.CERO, 60);
            MagicApi.giveXp(sp, 12, "cast");
            return;
        }
        float c = Math.min(1F, (held - HollowTech.CERO_MIN) / (float) (HollowTech.CERO_FULL - HollowTech.CERO_MIN));
        float cost = 0.05F + 0.07F * c;
        if (!canPay(sp, s, cost)) return;
        SpellContext ctx = open(sp, HollowTech.CERO);
        if (ctx == null) return;
        Hollow.spendVigor(sp, cost);
        HollowMoves.cero(ctx, c);
        cooldown(sp, s, HollowTech.CERO, Math.round(30 + 40 * c));
        MagicApi.giveXp(sp, 1 + Math.round(2 * c), "cast");
    }

    private static void releaseSneak(ServerPlayer sp, St s, int held) {
        if (held <= HollowTech.TAP_MAX || !unlocked(sp, HollowTech.GRAN_REY_CERO)) {
            if (held <= HollowTech.TAP_MAX + 3) useWheel(sp, s, HollowTech.PESQUISA);
            return;
        }
        boolean released = HollowState.released(sp) != null;
        int min = released ? HollowTech.GRAN_REY_MIN_RELEASED : HollowTech.GRAN_REY_MIN;
        if (held < min) {
            warn(sp, s, Hollow.msg("gran_rey_short").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!ready(sp, s, HollowTech.GRAN_REY_CERO, true)) return;
        float cost = released ? 0.22F : 0.30F;
        if (!canPay(sp, s, cost)) return;
        SpellContext ctx = open(sp, HollowTech.GRAN_REY_CERO);
        if (ctx == null) return;
        Hollow.spendVigor(sp, cost);
        HollowMoves.granRey(ctx, (held - min) / (float) min);
        cooldown(sp, s, HollowTech.GRAN_REY_CERO, released ? 600 : 900);
        cooldown(sp, s, HollowTech.CERO, 40);
        MagicApi.giveXp(sp, 10, "cast");
    }

    private static void bala(ServerPlayer sp, St s) {
        if (!ready(sp, s, HollowTech.BALA, false) || !canPay(sp, s, 0.012F)) return;
        SpellContext ctx = open(sp, HollowTech.BALA);
        if (ctx == null) return;
        Hollow.spendVigor(sp, 0.012F);
        HollowMoves.bala(ctx);
        cooldown(sp, s, HollowTech.BALA, 5);
    }

    private static void sonido(ServerPlayer sp, St s) {
        long n = now(sp);
        if (n > s.sonidoWindow) s.sonidoChain = 0;
        if (!ready(sp, s, HollowTech.SONIDO, true) || !canPay(sp, s, 0.03F)) return;
        SpellContext ctx = open(sp, HollowTech.SONIDO);
        if (ctx == null) return;
        Hollow.spendVigor(sp, 0.03F);
        HollowMoves.sonido(ctx);
        s.sonidoChain++;
        s.sonidoWindow = n + 30;
        if (s.sonidoChain >= HollowTech.SONIDO_CHAIN) {
            s.sonidoChain = 0;
            cooldown(sp, s, HollowTech.SONIDO, 50);
        } else {
            cooldown(sp, s, HollowTech.SONIDO, 5);
        }
    }

    // =====================================================================================
    //  A carga (tick)
    // =====================================================================================
    private static void tickHold(ServerPlayer sp, St s, long n) {
        int held = (int) (n - s.pressAt);
        if (held > HollowTech.MAX_HOLD) {
            // segurou demais: dispara sozinho
            release(sp, s);
            return;
        }
        if (held <= HollowTech.TAP_MAX) return;
        boolean released = HollowState.released(sp) != null;
        boolean gr = s.mode == 1;
        if (gr && (!unlocked(sp, HollowTech.GRAN_REY_CERO) || !ready(sp, s, HollowTech.GRAN_REY_CERO, false))) return;
        if (!gr && !ready(sp, s, HollowTech.CERO, false)) return;
        int full = gr ? (released ? HollowTech.GRAN_REY_MIN_RELEASED : HollowTech.GRAN_REY_MIN) : HollowTech.CERO_FULL;
        int rgb = HollowMoves.ceroRgb(sp);
        boolean hand = HollowMoves.fromHand(sp);
        ServerLevel sl = sp.serverLevel();
        if (!s.chargeFx) {
            s.chargeFx = true;
            HollowNetwork.fx(sp, gr ? HollowNetwork.GRAN_REY_CHARGE : HollowNetwork.CERO_CHARGE, sp.getEyePosition(),
                    new Vec3(hand ? 1 : 0, Math.max(1, full - held), 0), HollowTech.MAX_HOLD, rgb);
            MahouAnims.play(sp, MahouAnims.CERO_CHARGE);
            HollowFx.sound(sl, sp.getEyePosition(), HollowSounds.CERO_CHARGE.get(), gr ? 1.4F : 1.0F, gr ? 0.7F : 1.0F);
            if (gr) HollowFx.sound(sl, sp.getEyePosition(), SoundEvents.WARDEN_SONIC_CHARGE, 1.2F, 0.6F);
        }
        // planta os pés enquanto carrega
        if (held % 10 == 0) sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, gr ? 2 : 1, false, false, false));
        if (gr && held % 6 == 0) {
            // o sangue pinga no Cero
            Vec3 m = HollowMoves.muzzle(sp, sp.getLookAngle(), hand);
            sl.sendParticles(HollowFx.dust(0x9A0010, 0.8F), m.x, m.y + 0.5, m.z, 1, 0.02, 0.02, 0.02, 0);
        }
        if (!s.fullNotified && held >= full) {
            s.fullNotified = true;
            HollowFx.sound(sl, sp.getEyePosition(), SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.8F);
            sp.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
        if (!gr && !s.oscurasNotified && held >= HollowTech.OSCURAS_FULL && unlocked(sp, HollowTech.CERO_OSCURAS)
                && ready(sp, s, HollowTech.CERO_OSCURAS, false)) {
            // a carga escurece: Cero Oscuras pronto
            s.oscurasNotified = true;
            HollowNetwork.fx(sp, HollowNetwork.CERO_CHARGE, sp.getEyePosition(), new Vec3(hand ? 1 : 0, 1, 0),
                    HollowTech.MAX_HOLD - held, 0x14000C);
            HollowFx.sound(sl, sp.getEyePosition(), SoundEvents.WITHER_AMBIENT, 0.7F, 0.5F);
            sp.displayClientMessage(Hollow.msg("oscuras_ready").withColor(rgb), true);
        }
    }

    private static void stopCharge(ServerPlayer sp) {
        HollowNetwork.fx(sp, HollowNetwork.CHARGE_STOP, sp.getEyePosition(), Vec3.ZERO, 0F, 0);
    }

    // =====================================================================================
    //  A Roda Hollow
    // =====================================================================================
    private static void wheel(ServerPlayer sp, St s, HollowTech t) {
        if (s.holding || blocked(sp, s)) return;
        useWheel(sp, s, t);
    }

    /** Usa uma técnica da roda (também a Pesquisa do agachado + toque). */
    static void useWheel(ServerPlayer sp, St s, HollowTech t) {
        if (!unlocked(sp, t)) {
            warn(sp, s, Hollow.msg("tech_locked", t.displayName(), Hollow.rankTitle(t.rank)).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!ready(sp, s, t, true)) return;
        switch (t) {
            case PESQUISA -> {
                if (!canPay(sp, s, 0.04F)) return;
                SpellContext ctx = open(sp, t);
                if (ctx == null) return;
                Hollow.spendVigor(sp, 0.04F);
                HollowMoves.pesquisa(ctx);
                cooldown(sp, s, t, 200);
            }
            case REGEN -> {
                SpellContext ctx = open(sp, t);
                if (ctx == null || !HollowMoves.regen(ctx)) return;
                cooldown(sp, s, t, 300);
            }
            case HIERRO -> {
                if (!canPay(sp, s, 0.08F)) return;
                SpellContext ctx = open(sp, t);
                if (ctx == null) return;
                Hollow.spendVigor(sp, 0.08F);
                HollowMoves.hierro(ctx);
                cooldown(sp, s, t, 400);
            }
            case DESCORRER -> {
                if (!canPay(sp, s, 0.10F)) return;
                SpellContext ctx = open(sp, t);
                if (ctx == null || !HollowMoves.descorrer(ctx)) return;
                Hollow.spendVigor(sp, 0.10F);
                cooldown(sp, s, t, 160);
            }
            case NEGACION -> {
                if (!canPay(sp, s, 0.20F)) return;
                Hollow.spendVigor(sp, 0.20F);
                startNegacion(sp, s);
                cooldown(sp, s, t, 3600);
            }
            case GARGANTA -> {
                if (!canPay(sp, s, 0.15F)) return;
                startChannel(sp, s, t, GARGANTA_CHANNEL);
            }
            case RESURRECCION -> {
                if (HollowState.released(sp) != null) {
                    warn(sp, s, Hollow.msg("release_already").withStyle(ChatFormatting.GRAY));
                    return;
                }
                if (!canPay(sp, s, 0.40F)) return;
                startChannel(sp, s, t, RELEASE_CHANNEL);
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- invocação (Garganta / Resurrección)
    private static void startChannel(ServerPlayer sp, St s, HollowTech t, int ticks) {
        s.channel = t;
        s.channelEnd = now(sp) + ticks;
        ServerLevel sl = sp.serverLevel();
        Vec3 at = sp.position();
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks + 4, 3, false, false, false));
        if (t == HollowTech.RESURRECCION) {
            Hollow.Release r = Hollow.release(MagicApi.get(sp));
            int rgb = r == null ? Hollow.REIATSU_RGB : r.rgb;
            MahouAnims.play(sp, MahouAnims.RESURRECCION);
            HollowNetwork.fxNear(sl, HollowNetwork.RELEASE_CHANNEL, sp.getId(), at, Vec3.ZERO, ticks, rgb, 96);
            HollowNetwork.fxNear(sl, HollowNetwork.SHAKE, sp.getId(), at, Vec3.ZERO, 0.4F, rgb, 32);
            if (r != null) {
                Announce.send(sp, Hollow.msg("release_shout", r.command()), r.rgb, Announce.STYLE_ULTIMATE);
                // v1.1: a Resurrección é uma transformação — cutscene coreografada de transformação (agente C)
                String style = switch (r) {
                    case PANTERA -> "beast";
                    case MURCIELAGO -> "storm"; // asas negras e a Lança do Relâmpago
                    case SERPIENTE -> "dark";
                };
                Cutscenes.transform(sp, r.command(), r.displayName(), r.rgb, style, ticks + 24);
            }
            HollowFx.sound(sl, at, SoundEvents.BEACON_AMBIENT, 1.5F, 0.5F);
            HollowFx.sound(sl, at, SoundEvents.WARDEN_HEARTBEAT, 1.5F, 0.8F);
            HollowFx.sound(sl, at, HollowSounds.MASK.get(), 1.0F, 0.8F);
        } else {
            MahouAnims.play(sp, MahouAnims.KNEEL_CHANNEL);
            HollowFx.sound(sl, at, SoundEvents.WARDEN_DIG, 1.0F, 1.2F);
            sp.displayClientMessage(Hollow.msg("garganta_tearing").withColor(Hollow.REIATSU_RGB), true);
        }
    }

    private static void tickChannel(ServerPlayer sp, St s, long n) {
        HollowTech t = s.channel;
        if (t == null) return;
        ServerLevel sl = sp.serverLevel();
        if (!sp.isAlive()) {
            s.channel = null;
            return;
        }
        if (n % 4 == 0) {
            int rgb = t == HollowTech.RESURRECCION ? HollowMoves.ceroRgb(sp) : Hollow.DARK_RGB;
            sl.sendParticles(HollowFx.dust(rgb, 1.4F), sp.getX(), sp.getY() + 1, sp.getZ(), 4, 0.5, 0.8, 0.5, 0.04);
            if (t == HollowTech.GARGANTA) {
                Vec3 at = HmTravelSpot.ahead(sp);
                sl.sendParticles(HollowFx.BLACK, at.x, at.y + 1.6, at.z, 6, 0.1, 1.0, 0.1, 0.01);
            }
        }
        if (n < s.channelEnd) return;
        s.channel = null;
        if (t == HollowTech.RESURRECCION) {
            SpellContext ctx = open(sp, t);
            if (ctx == null) return;
            if (!sp.getAbilities().instabuild && !Hollow.spendVigor(sp, 0.40F)) {
                sp.displayClientMessage(MagicApi.noResourceMessage(sp).withStyle(ChatFormatting.RED), true);
                return;
            }
            if (HollowMoves.resurreccionBurst(ctx, RELEASE_TICKS)) {
                cooldown(sp, s, t, 6000);
                MagicApi.giveXp(sp, 20, "cast");
            }
        } else if (t == HollowTech.GARGANTA) {
            SpellContext ctx = open(sp, t);
            if (ctx == null) return;
            if (HollowMoves.garganta(ctx)) {
                Hollow.spendVigor(sp, 0.15F);
                cooldown(sp, s, t, 600);
            }
        }
    }

    // ---------------------------------------------------------------- Negación
    private static void startNegacion(ServerPlayer sp, St s) {
        ServerLevel sl = sp.serverLevel();
        HollowState.negacion(sp, NEGACION_TICKS);
        s.negacionEnd = now(sp) + NEGACION_TICKS;
        sp.addEffect(new MobEffectInstance(MobEffects.LEVITATION, NEGACION_TICKS, 0, false, false, false));
        // os perseguidores perdem o alvo (uma consulta só, na hora)
        for (Mob m : sl.getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(24), m -> m.getTarget() == sp)) m.setTarget(null);
        HollowNetwork.fxNear(sl, HollowNetwork.NEGACION, sp.getId(), sp.position(), Vec3.ZERO, NEGACION_TICKS, 0xFFE9A0, 128);
        MahouAnims.play(sp, MahouAnims.TRANSFORM_RISE);
        HollowFx.sound(sl, sp.position(), SoundEvents.BEACON_ACTIVATE, 1.6F, 0.7F);
        HollowFx.sound(sl, sp.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.5F);
        sp.displayClientMessage(Hollow.msg("negacion_start").withColor(0xFFE9A0), true);
    }

    private static void endNegacion(ServerPlayer sp, St s) {
        s.negacionEnd = 0;
        if (!sp.isAlive()) return;
        ServerLevel sl = sp.serverLevel();
        // a luz o leva para longe do inimigo mais próximo (até 20 blocos)
        LivingEntity near = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(20),
                e -> e != sp && e.isAlive() && (e instanceof Enemy || e instanceof Mob m && m.getTarget() == sp))) {
            double d = e.distanceToSqr(sp);
            if (d < best) {
                best = d;
                near = e;
            }
        }
        Vec3 dir = near == null ? sp.getLookAngle().scale(-1) : sp.position().subtract(near.position());
        dir = new Vec3(dir.x, 0, dir.z);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 start = sp.position();
        Vec3 end = HollowMoves.dash(sl, sp, start, dir, 20);
        HollowNetwork.fxNear(sl, HollowNetwork.SONIDO, sp.getId(), start, end, sp.getBbHeight(), 0xFFE9A0, 64);
        sp.teleportTo(sl, end.x, end.y, end.z, sp.getYRot(), sp.getXRot());
        sp.removeEffect(MobEffects.LEVITATION);
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false, true));
        sp.fallDistance = 0;
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, end.x, end.y + 1, end.z, 20, 0.4, 0.8, 0.4, 0.05);
        HollowFx.sound(sl, end, SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.4F);
        sp.displayClientMessage(Hollow.msg("negacion_end").withColor(0xFFE9A0), true);
    }

    // =====================================================================================
    //  Tick do servidor
    // =====================================================================================
    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) return;
        Iterator<Map.Entry<UUID, St>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, St> me = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(me.getKey());
            St s = me.getValue();
            if (sp == null) {
                // offline: só guarda se ainda há recarga (não dá para zerar saindo e voltando)
                if (s.idle(server.overworld().getGameTime())) it.remove();
                continue;
            }
            long n = now(sp);
            if (s.holding && !s.consumed) {
                if (!Hollow.isHollow(sp) || !sp.isAlive()) s.holding = false;
                else tickHold(sp, s, n);
            }
            if (s.channel != null) tickChannel(sp, s, n);
            if (s.negacionEnd != 0 && n >= s.negacionEnd) endNegacion(sp, s);
        }
    }

    /** Saiu do jogo / morreu: solta a tecla e cancela a invocação (as recargas ficam). */
    public static void forget(ServerPlayer sp) {
        St s = STATES.get(sp.getUUID());
        if (s == null) return;
        s.holding = false;
        s.channel = null;
        s.negacionEnd = 0;
    }

    /** Entrou no jogo: manda as recargas que ainda correm. */
    public static void syncCooldowns(ServerPlayer sp) {
        St s = STATES.get(sp.getUUID());
        if (s == null) return;
        long n = now(sp);
        for (HollowTech t : TECHS) {
            long left = s.cd[t.ordinal()] - n;
            if (left > 0) PacketDistributor.sendToPlayer(sp, new HollowNetwork.TechCd(t.ordinal(), (int) left, 0));
        }
    }

    /** Comando de teste: zera todas as recargas. */
    public static void resetCooldowns(ServerPlayer sp) {
        St s = st(sp);
        for (HollowTech t : TECHS) {
            s.cd[t.ordinal()] = 0;
            PacketDistributor.sendToPlayer(sp, new HollowNetwork.TechCd(t.ordinal(), 0, 0));
        }
        s.sonidoChain = 0;
    }

    /**
     * Comando de teste: usa a técnica na hora, com carga cheia (ignora rank e recarga; paga Vigor como sempre, a menos
     * que esteja no criativo). Cero Oscuras não exige estar liberado.
     */
    public static boolean debugUse(ServerPlayer sp, HollowTech t) {
        St s = st(sp);
        s.cd[t.ordinal()] = 0;
        switch (t) {
            case CERO -> {
                SpellContext ctx = open(sp, t);
                if (ctx == null) return false;
                HollowMoves.cero(ctx, 1F);
            }
            case BALA -> bala(sp, s);
            case GRAN_REY_CERO -> {
                SpellContext ctx = open(sp, t);
                if (ctx == null) return false;
                HollowMoves.granRey(ctx, 1F);
            }
            case CERO_OSCURAS -> {
                SpellContext ctx = open(sp, t);
                if (ctx == null) return false;
                HollowMoves.oscuras(ctx);
            }
            case SONIDO -> {
                s.sonidoChain = 0;
                sonido(sp, s);
            }
            case NEGACION -> startNegacion(sp, s);
            case GARGANTA -> startChannel(sp, s, t, GARGANTA_CHANNEL);
            case RESURRECCION -> {
                if (Hollow.release(MagicApi.get(sp)) == null) return false;
                startChannel(sp, s, t, RELEASE_CHANNEL);
            }
            default -> {
                // Pesquisa, Regeneração, Hierro, Descorrer: o caminho normal sem a porta do rank
                SpellContext ctx = open(sp, t);
                if (ctx == null) return false;
                switch (t) {
                    case PESQUISA -> HollowMoves.pesquisa(ctx);
                    case REGEN -> HollowMoves.regen(ctx);
                    case HIERRO -> HollowMoves.hierro(ctx);
                    case DESCORRER -> HollowMoves.descorrer(ctx);
                    default -> { }
                }
            }
        }
        return true;
    }

    public static void clear() {
        STATES.clear();
    }

    /** Onde a Garganta vai se abrir (para as partículas da invocação). */
    private static final class HmTravelSpot {
        static Vec3 ahead(ServerPlayer sp) {
            Vec3 look = sp.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
            return sp.position().add(flat.scale(3));
        }
    }

    private HollowTechniques() {}
}
