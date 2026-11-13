package com.renanpontes.isekaimahou.feature.class_hollow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.PendingCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.class_hollow.glyph.CeroFusionAugment;
import com.renanpontes.isekaimahou.feature.class_hollow.glyph.DevourTrigger;
import com.renanpontes.isekaimahou.feature.class_hollow.tech.HollowMoves;
import com.renanpontes.isekaimahou.feature.class_hollow.tech.HollowTechniques;
import com.renanpontes.isekaimahou.feature.class_shinigami.inner.HollowFate;
import com.renanpontes.isekaimahou.feature.class_shinigami.inner.InnerWorld;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.HmAllies;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.HmMob;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Os eventos da classe Hollow:
 * <ul>
 *   <li>devorar almas ao matar ({@link HollowSouls}), a fome de almas e a evolução ({@link HollowRanks});</li>
 *   <li>Garras: socos de mão vazia rasgam (+2 de dano, +1 por rank); Hierro reduz o dano recebido; a Resurrección muda os
 *   golpes (Pantera rasga em volta, Murciélago bate mais forte, Serpiente envenena);</li>
 *   <li>técnicas Hollow ganham poder pelo rank e pela Resurrección e perdem com a fome;</li>
 *   <li>a carga do Cero/Gran Rey Cero na boca enquanto o jogador segura a conjuração;</li>
 *   <li>os Vazios do Mundo Oco não atacam um Hollow (a menos que ele ataque primeiro);</li>
 *   <li>a aparência (máscara, buraco, ossos) vai aos clientes ao entrar, ao ser visto e ao mudar.</li>
 * </ul>
 */
public final class HollowEvents {
    /** Quem teve a aparência de Hollow enviada (para mandar "não é mais Hollow" se a classe mudar por comando). */
    private static final Set<UUID> LOOKED = new HashSet<>();
    /** w10: quando cada Pantera pode soltar o próximo Desgarrón (tempo do jogo). */
    private static final Map<UUID, Long> DESGARRON_READY = new HashMap<>();
    /** w10: intervalo mínimo entre dois Desgarrón da Pantera (ticks). */
    private static final int DESGARRON_CD = 30;

    static void register() {
        HollowFate.hook = Hollowfication.HOOK;
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            Hollowfication.tick(e.getServer());
            HollowState.tick(e.getServer());
            HollowTechniques.tick(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener(HollowEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, HollowEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(HollowEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(HollowEvents::onKnockback);
        NeoForge.EVENT_BUS.addListener(HollowEvents::onCastPre);
        NeoForge.EVENT_BUS.addListener(HollowEvents::onPendingCast);
        NeoForge.EVENT_BUS.addListener(HollowEvents::onChangeTarget);
        // v1.4: glifos novos da classe (aumento "Cero Fusionado" e gatilho "Ao Devorar uma Alma")
        NeoForge.EVENT_BUS.addListener(CeroFusionAugment::onPost);
        NeoForge.EVENT_BUS.addListener(DevourTrigger::onPre);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e) -> {
            if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer viewer && Hollow.isHollow(target)) {
                PacketDistributor.sendToPlayer(viewer, look(target));
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            if (Hollow.isHollow(sp)) {
                // logou dentro do Mundo Interior depois de virar Hollow (caiu no meio da cena): volta ao mundo
                if (InnerWorld.is(sp.level())) InnerWorld.leave(sp);
                syncLook(sp);
                HollowRanks.catchUp(sp);
                HollowRanks.check(sp);
                HollowTechniques.syncCooldowns(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp && Hollow.isHollow(sp)) syncLook(sp);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp && Hollow.isHollow(sp)) syncLook(sp);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                HollowState.forget(sp);
                HollowTechniques.forget(sp);
                DevourTrigger.forget(sp.getUUID());
                Hollowfication.forget(sp.getUUID());
                LOOKED.remove(sp.getUUID());
            }
        });
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.CircleChanged e) -> HollowRanks.check(e.getPlayer()));
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.Counter e) -> {
            if (e.key.startsWith("boss.")) HollowRanks.check(e.getPlayer());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            Hollowfication.clear();
            HollowState.clear();
            HollowTechniques.clear();
            DevourTrigger.clear();
            LOOKED.clear();
        });
    }

    // =====================================================================================
    //  Aparência
    // =====================================================================================
    static HollowNetwork.Look look(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.HOLLOW || Hollowfication.maskHidden(sp)) return new HollowNetwork.Look(sp.getId(), -1, 0, 0);
        int flags = 0;
        if (HollowState.released(sp) != null) flags |= HollowNetwork.Look.RELEASED;
        if (HollowState.hierroActive(sp)) flags |= HollowNetwork.Look.HIERRO;
        if (HollowSouls.hunger(d, Hollow.nowSeconds(sp)) >= 2) flags |= HollowNetwork.Look.STARVING;
        return new HollowNetwork.Look(sp.getId(), Hollow.rank(d), Math.max(0, d.counter(Hollow.RELEASE)), flags);
    }

    /** Manda a aparência do Hollow (ou "não é mais Hollow") para ele e quem o vê. */
    public static void syncLook(ServerPlayer sp) {
        HollowNetwork.Look l = look(sp);
        if (l.rank() >= 0) LOOKED.add(sp.getUUID());
        else LOOKED.remove(sp.getUUID());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, l);
    }

    // =====================================================================================
    //  Tick do jogador
    // =====================================================================================
    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer || sp.tickCount % 100 != 0) return;
        if (!Hollow.isHollow(sp)) {
            // deixou de ser Hollow (comando): a máscara some para todos
            if (LOOKED.contains(sp.getUUID())) {
                HollowState.forget(sp);
                syncLook(sp);
            }
            return;
        }
        HollowSouls.hungerTick(sp);
        HollowForge.tick(sp);
        if (Hollowfication.purgeBlade(sp) > 0) sp.displayClientMessage(Hollow.msg("blade_rejected").withStyle(ChatFormatting.DARK_GRAY), true);
    }

    // =====================================================================================
    //  Combate
    // =====================================================================================
    private static void onDeath(LivingDeathEvent e) {
        if (e.isCanceled()) return;
        LivingEntity dead = e.getEntity();
        // morreu liberado: a forma acaba (sem o preço; o voo dado volta ao normal)
        if (dead instanceof ServerPlayer dp && HollowState.released(dp) != null) HollowState.forget(dp);
        if (dead instanceof ServerPlayer dp) HollowTechniques.forget(dp);
        if (!(e.getSource().getEntity() instanceof ServerPlayer sp) || sp == dead || sp instanceof FakePlayer || !Hollow.isHollow(sp)) return;
        HollowSouls.devour(sp, dead);
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide) return;
        DamageSource src = e.getSource();
        // v1.4 — Negación: dentro da coluna de luz nada toca o Hollow, e ele também não ataca
        if (victim instanceof ServerPlayer np && HollowState.inNegacion(np) && !src.is(DamageTypes.FELL_OUT_OF_WORLD)
                && !src.is(DamageTypes.GENERIC_KILL)) {
            e.setCanceled(true);
            return;
        }
        if (src.getEntity() instanceof ServerPlayer ap && HollowState.inNegacion(ap)) {
            e.setCanceled(true);
            return;
        }
        // Hierro: a pele de ferro (não protege do vazio / comandos)
        if (victim instanceof ServerPlayer vp && Hollow.isHollow(vp) && !src.is(DamageTypes.FELL_OUT_OF_WORLD) && !src.is(DamageTypes.GENERIC_KILL)) {
            float red = HollowState.hierroReduction(vp, MagicApi.get(vp));
            if (red > 0) {
                e.setAmount(e.getAmount() * (1F - red));
                if (vp.tickCount % 6 == 0) {
                    ServerLevel sl = vp.serverLevel();
                    sl.sendParticles(HollowFx.BONE, vp.getX(), vp.getY() + 1.1, vp.getZ(), 4, 0.3, 0.4, 0.3, 0.05);
                    HollowFx.sound(sl, vp.position(), SoundEvents.ANVIL_LAND, 0.25F, 1.8F);
                }
            }
        }
        // golpes corpo a corpo do Hollow
        if (!(src.getEntity() instanceof ServerPlayer sp) || src.getDirectEntity() != sp || !src.is(DamageTypes.PLAYER_ATTACK)
                || !Hollow.isHollow(sp) || victim == sp) return;
        MagicData d = MagicApi.get(sp);
        float amount = e.getAmount();
        if (sp.getMainHandItem().isEmpty()) amount += 2F + Hollow.rank(d);
        Hollow.Release r = HollowState.released(sp);
        if (r != null) {
            ServerLevel sl = sp.serverLevel();
            switch (r) {
                case PANTERA -> {
                    amount *= 1.4F;
                    // w10 — Desgarrón: o golpe LANÇA três marcas de garra que voam 20 blocos para a frente, cortando
                    // todo mundo no caminho (no máx. 1 vez a cada 1,5 s; o alvo do soco não leva o corte de novo)
                    long now = sl.getGameTime();
                    Long ready = DESGARRON_READY.get(sp.getUUID());
                    if (ready == null || now >= ready) {
                        DESGARRON_READY.put(sp.getUUID(), now + DESGARRON_CD);
                        if (DESGARRON_READY.size() > 256) DESGARRON_READY.values().removeIf(t -> t < now);
                        HollowMoves.desgarron(sp, amount * 0.5F, victim);
                    }
                }
                case MURCIELAGO -> amount *= 1.25F;
                case SERPIENTE -> {
                    amount *= 1.15F;
                    victim.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1), sp);
                }
            }
        }
        e.setAmount(amount);
        Hollow.gainVigor(sp, 0.015F);
    }

    private static void onKnockback(LivingKnockBackEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && HollowState.hierroStrong(sp)) e.setCanceled(true);
    }

    // =====================================================================================
    //  Conjuração
    // =====================================================================================
    private static void onCastPre(SpellCastEvent.Pre e) {
        SpellContext ctx = e.getContext();
        if (!(e.getCaster() instanceof ServerPlayer sp) || !ctx.isPlayerCast() || !Hollow.isHollow(sp)) return;
        if (!Hollow.hasHollowGlyph(ctx.recipe())) return;
        if (Hollowfication.inProgress(sp)) {
            e.setCanceled(true);
            return;
        }
        MagicData d = MagicApi.get(sp);
        float mult = Hollow.rankPower(d);
        if (HollowState.released(sp) != null) mult *= 1.25F;
        int hunger = HollowSouls.hunger(d, Hollow.nowSeconds(sp));
        if (hunger == 1) mult *= 0.8F;
        else if (hunger >= 2) mult *= 0.6F;
        if (mult != 1.0F) ctx.setPowerMult(ctx.powerMult() * mult);
    }

    /** Uma conjuração com tempo entrou na Pilha: a carga certa na boca enquanto o jogador segura. */
    private static void onPendingCast(PendingCastEvent e) {
        if (!e.getPending().fromPlayerCast() || !(e.getCaster() instanceof ServerPlayer sp) || !Hollow.isHollow(sp)) return;
        SpellRecipe r = MagicApi.get(sp).channelRecipe;
        if (r == null) return;
        int ticks = Math.max(4, e.getPending().ticksLeft(e.getLevel()));
        Vec3 at = sp.getEyePosition();
        int rgb = Hollow.ceroRgb(MagicApi.get(sp));
        if (Hollow.hasGlyph(r, Hollow.GRAN_REY_CERO)) {
            HollowNetwork.fx(sp, HollowNetwork.GRAN_REY_CHARGE, at, Vec3.ZERO, ticks, rgb);
            MahouAnims.play(sp, "cero_charge");
            HollowFx.sound(sp.serverLevel(), at, HollowSounds.CERO_CHARGE.get(), 1.4F, 0.7F);
            HollowFx.sound(sp.serverLevel(), at, SoundEvents.WARDEN_SONIC_CHARGE, 1.2F, 0.6F);
        } else if (Hollow.hasGlyph(r, Hollow.CERO)) {
            HollowNetwork.fx(sp, HollowNetwork.CERO_CHARGE, at, Vec3.ZERO, ticks, rgb);
            MahouAnims.play(sp, "cero_charge");
            HollowFx.sound(sp.serverLevel(), at, HollowSounds.CERO_CHARGE.get(), 1.0F, 1.0F);
        } else if (Hollow.hasGlyph(r, Hollow.RESURRECCION)) {
            Hollow.Release rel = Hollow.release(MagicApi.get(sp));
            HollowNetwork.fx(sp, HollowNetwork.CERO_CHARGE, sp.position().add(0, 1, 0), Vec3.ZERO, ticks, rel == null ? Hollow.REIATSU_RGB : rel.rgb);
            MahouAnims.play(sp, "kneel_channel");
            HollowFx.sound(sp.serverLevel(), at, SoundEvents.BEACON_AMBIENT, 1.2F, 0.5F);
        } else if (Hollow.hasGlyph(r, Hollow.GARGANTA) || Hollow.hasGlyph(r, Hollow.DESCORRER)) {
            MahouAnims.play(sp, "kneel_channel");
        }
    }

    // =====================================================================================
    //  Os Vazios do Mundo Oco reconhecem um dos seus
    // =====================================================================================
    private static void onChangeTarget(LivingChangeTargetEvent e) {
        if (!(e.getNewAboutToBeSetTarget() instanceof ServerPlayer sp) || !(e.getEntity() instanceof Mob mob)) return;
        if (!(mob instanceof HmMob) || !HmAllies.isHollow(mob) || !Hollow.isHollow(sp)) return;
        // provocou (bateu nele): aí ele revida
        if (mob.getLastHurtByMob() == sp) return;
        e.setCanceled(true);
    }

    // =====================================================================================
    //  Respostas das telas
    // =====================================================================================
    static void onChoice(ServerPlayer sp, int option, int value) {
        switch (option) {
            case HollowNetwork.CHOICE_FIGHT -> Hollowfication.onChoice(sp, false);
            case HollowNetwork.CHOICE_ACCEPT -> Hollowfication.onChoice(sp, true);
            case HollowNetwork.CHOICE_OPEN_RELEASE -> {
                if (Hollow.isHollow(sp) && HollowRanks.arrancarReady(MagicApi.get(sp))) {
                    PacketDistributor.sendToPlayer(sp, new HollowNetwork.Open(HollowNetwork.SCREEN_RELEASE));
                }
            }
            case HollowNetwork.CHOICE_RELEASE -> chooseRelease(sp, value);
            case HollowNetwork.CHOICE_TOGGLE_HIERRO -> {
                MagicData d = MagicApi.get(sp);
                if (!Hollow.isHollow(sp) || Hollow.rank(d) < Hollow.VASTO_LORDE) return;
                boolean off = !d.hasFlag(Hollow.FLAG_HIERRO_OFF);
                d.setFlag(Hollow.FLAG_HIERRO_OFF, off);
                sp.displayClientMessage(Hollow.msg(off ? "hierro_off" : "hierro_on").withColor(Hollow.BONE_RGB), true);
            }
            default -> { }
        }
    }

    /** A escolha da Resurrección (uma vez): vira Arrancar. */
    private static void chooseRelease(ServerPlayer sp, int index) {
        MagicData d = MagicApi.get(sp);
        Hollow.Release r = Hollow.Release.byIndex(index);
        if (r == null || !Hollow.isHollow(sp) || Hollow.release(d) != null || !HollowRanks.arrancarReady(d)) return;
        MagicApi.addCounter(sp, Hollow.RELEASE, r.counterValue() - d.counter(Hollow.RELEASE));
        HollowRanks.Rank arr = HollowRanks.rank(Hollow.ARRANCAR);
        if (arr != null) HollowRanks.grant(sp, arr);
        sp.sendSystemMessage(Hollow.msg("release_chosen", r.displayName(), r.command()).withColor(r.rgb));
    }

    private HollowEvents() {}
}
