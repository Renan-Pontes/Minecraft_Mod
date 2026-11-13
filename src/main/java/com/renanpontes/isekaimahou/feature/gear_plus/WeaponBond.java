package com.renanpontes.isekaimahou.feature.gear_plus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * <b>Armas que despertam</b> — palavra do dono: "Legal pra algumas classes" (físicas/destreza).
 * <p>
 * A arma de classe cria um <b>Vínculo</b> com o dono: cada abate de monstro com ela na mão conta (chefes valem 10) e o
 * Vínculo sobe do nível 1 ao 10. Cada nível dá +1,5% de dano com a arma. No nível 10 a arma <b>desperta</b> e libera UMA
 * habilidade da classe — mas só para quem já chegou ao nível {@value #AWAKEN_PLAYER_LEVEL} (upar pesa mais que item).
 * <p>
 * O Vínculo fica com o JOGADOR (por classe), não no item: trocar a arma pelo tier seguinte ou recuperar o kit não zera
 * nada. Vale a arma do kit, as armas de tier ({@code <prefixo>_t1..t4}) e, no Ferreiro, a lâmina forjada.
 * <p>
 * A habilidade não usa tecla nem HUD: golpes com a arma desperta acumulam <b>Ressonância</b>; com {@value #RESONANCE_HITS}
 * golpes a arma avisa (um som e um brilho) e o PRÓXIMO golpe solta a habilidade. Depois ela descansa
 * {@value #RESONANCE_REST} ticks. Nada aqui quebra blocos: é só dano/efeito em inimigos.
 * <p>
 * Classes: Ferreiro, Ladino, Cavaleiro Rúnico, Quincy, Pistoleiro, Ranger, Apostador e Steel Ball. O Shinigami já tem
 * Shikai/Bankai, o Encantador ganha as gemas e o Hollow é de história.
 */
public final class WeaponBond {
    public static final int MAX_LEVEL = 10;
    /** Abates para CHEGAR a cada nível (índice 0 = nível 1). */
    private static final int[] KILLS = {0, 15, 40, 80, 140, 220, 320, 450, 600, 800};
    public static final int AWAKEN_PLAYER_LEVEL = 50;
    public static final float DAMAGE_PER_LEVEL = 0.015F;
    public static final int BOSS_KILLS = 10;
    public static final int RESONANCE_HITS = 12;
    public static final int RESONANCE_REST = 300;
    /** Golpes mais rápidos que isso (multi-acertos da mesma técnica) contam como um só. */
    private static final int HIT_GAP = 6;

    private static final class Resonance {
        int charges;
        long lastHit;
        long restUntil;
    }

    private static final Map<UUID, Resonance> RESONANCE = new ConcurrentHashMap<>();
    /** Dano extra da própria habilidade: não acumula Ressonância nem ganha o bônus de novo. */
    private static boolean inBurst;

    // =====================================================================================
    //  Regras
    // =====================================================================================

    /** A classe tem arma que desperta? */
    public static boolean hasBond(MagicPath path) {
        return switch (path) {
            case SMITH, ROGUE, KNIGHT, QUINCY, GUNSLINGER, RANGER, GAMBLER, STEELBALL -> true;
            default -> false;
        };
    }

    /** O item é a arma PRINCIPAL da classe: a do kit ou uma das de tier ({@code <prefixo>_t1..t4})? */
    public static boolean isMainClassWeapon(MagicPath path, ItemStack stack) {
        if (stack.isEmpty() || path == null || path.kitWeapon() == null) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!IsekaiMahou.MODID.equals(id.getNamespace())) return false;
        String p = id.getPath();
        if (p.equals(path.kitWeapon().getPath())) return true;
        String prefix = path.weaponPrefix();
        return prefix != null && !prefix.isEmpty() && p.startsWith(prefix + "_t") && p.length() == prefix.length() + 3
                && Character.isDigit(p.charAt(p.length() - 1));
    }

    /** O item é arma de classe de {@code path} (kit, tier ou a lâmina forjada do Ferreiro)? */
    public static boolean isClassWeapon(MagicPath path, ItemStack stack) {
        if (isMainClassWeapon(path, stack)) return true;
        return path == MagicPath.SMITH && !stack.isEmpty()
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(IsekaiMahou.id("smith_forged_blade"));
    }

    public static boolean isAnyClassWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (MagicPath p : MagicPath.values()) {
            if (p != MagicPath.NONE && isClassWeapon(p, stack)) return true;
        }
        return false;
    }

    /** Arma do Vínculo: arma de classe de uma classe que desperta. */
    public static boolean isBondWeapon(MagicPath path, ItemStack stack) {
        return hasBond(path) && isClassWeapon(path, stack);
    }

    /** Nível do Vínculo (1..10). O 10 (despertar) exige também o nível do jogador. */
    public static int level(int kills, int playerLevel) {
        int lv = 1;
        for (int i = 0; i < KILLS.length; i++) if (kills >= KILLS[i]) lv = i + 1;
        if (lv >= MAX_LEVEL && playerLevel < AWAKEN_PLAYER_LEVEL) return MAX_LEVEL - 1;
        return lv;
    }

    /** Abates para chegar ao nível (1..10). */
    public static int killsFor(int level) {
        return KILLS[Math.max(1, Math.min(MAX_LEVEL, level)) - 1];
    }

    /** Multiplicador de dano do nível do Vínculo. */
    public static float damageBonus(int level) {
        return (Math.max(1, level) - 1) * DAMAGE_PER_LEVEL;
    }

    public static MutableComponent abilityName(MagicPath path) {
        return Component.translatable("gear_plus.isekai_mahou.ability." + path.id);
    }

    public static MutableComponent abilityDesc(MagicPath path) {
        return Component.translatable("gear_plus.isekai_mahou.ability." + path.id + ".desc");
    }

    public static int level(ServerPlayer sp) {
        MagicData md = MagicApi.get(sp);
        return hasBond(md.path()) ? level(GearData.bondKills(sp, md.path()), md.level()) : 0;
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    static void register() {
        NeoForge.EVENT_BUS.addListener(WeaponBond::onDeath);
        NeoForge.EVENT_BUS.addListener(WeaponBond::onIncoming);
        NeoForge.EVENT_BUS.addListener(WeaponBond::onDamaged);
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.LevelUp e) -> {
            checkAwakening(e.getPlayer());
            GearNet.sync(e.getPlayer());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> RESONANCE.remove(e.getEntity().getUUID()));
    }

    private static ServerPlayer attacker(Entity source) {
        return source instanceof ServerPlayer sp && !(sp instanceof FakePlayer) ? sp : null;
    }

    private static void onDeath(LivingDeathEvent e) {
        ServerPlayer sp = attacker(e.getSource().getEntity());
        LivingEntity victim = e.getEntity();
        if (sp == null || victim instanceof Player) return;
        boolean boss = BossKit.isBossLike(victim);
        if (!(victim instanceof Enemy) && !boss) return;
        MagicPath path = MagicApi.get(sp).path();
        if (!isBondWeapon(path, sp.getMainHandItem())) return;
        addKills(sp, path, boss ? BOSS_KILLS : 1);
    }

    /** Soma abates ao Vínculo, avisa a subida de nível (uma linha discreta) e sincroniza. */
    public static void addKills(ServerPlayer sp, MagicPath path, int amount) {
        if (!hasBond(path) || amount == 0) return;
        int playerLevel = MagicApi.get(sp).level();
        int before = GearData.bondKills(sp, path);
        int after = Math.max(0, before + amount);
        GearData.setBondKills(sp, path, after);
        int oldLv = level(before, playerLevel), newLv = level(after, playerLevel);
        Hints.show(sp, "gear_plus.bond");
        if (newLv > oldLv && newLv < MAX_LEVEL) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gear_plus.bond.level", newLv)
                    .withStyle(ChatFormatting.GOLD), true);
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 0.7F);
        } else if (after >= killsFor(MAX_LEVEL) && before < killsFor(MAX_LEVEL) && playerLevel < AWAKEN_PLAYER_LEVEL) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.gear_plus.bond.waiting", AWAKEN_PLAYER_LEVEL)
                    .withStyle(ChatFormatting.GOLD));
        }
        checkAwakening(sp);
        GearNet.sync(sp);
    }

    /** Anuncia o despertar uma única vez por classe (abates E nível do jogador cumpridos). */
    static void checkAwakening(ServerPlayer sp) {
        MagicData md = MagicApi.get(sp);
        MagicPath path = md.path();
        if (!hasBond(path) || GearData.awakeAnnounced(sp, path)) return;
        if (level(GearData.bondKills(sp, path), md.level()) < MAX_LEVEL) return;
        GearData.setAwakeAnnounced(sp, path, true);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.gear_plus.bond.awakened",
                abilityName(path).withColor(path.rgb())).withStyle(ChatFormatting.GOLD));
        sp.sendSystemMessage(abilityDesc(path).withStyle(ChatFormatting.GRAY));
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
        if (sp.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.0, sp.getZ(), 40, 0.5, 0.8, 0.5, 0.08);
        }
    }

    /** +1,5% de dano por nível do Vínculo, em todo dano que o dono causa segurando a arma. */
    private static void onIncoming(LivingIncomingDamageEvent e) {
        if (inBurst) return;
        ServerPlayer sp = attacker(e.getSource().getEntity());
        if (sp == null || e.getEntity() == sp) return;
        MagicData md = MagicApi.get(sp);
        if (!isBondWeapon(md.path(), sp.getMainHandItem())) return;
        float bonus = damageBonus(level(GearData.bondKills(sp, md.path()), md.level()));
        if (bonus > 0) e.setAmount(e.getAmount() * (1F + bonus));
    }

    /** Ressonância: golpes da arma desperta carregam; cheia, o próximo golpe solta a habilidade. */
    private static void onDamaged(LivingDamageEvent.Post e) {
        if (inBurst || e.getNewDamage() < 1F) return;
        ServerPlayer sp = attacker(e.getSource().getEntity());
        LivingEntity target = e.getEntity();
        if (sp == null || !isFoe(sp, target)) return;
        MagicData md = MagicApi.get(sp);
        MagicPath path = md.path();
        if (!isBondWeapon(path, sp.getMainHandItem())) return;
        if (level(GearData.bondKills(sp, path), md.level()) < MAX_LEVEL) return;
        long now = sp.level().getGameTime();
        Resonance r = RESONANCE.computeIfAbsent(sp.getUUID(), u -> new Resonance());
        if (now < r.restUntil || now - r.lastHit < HIT_GAP) return;
        r.lastHit = now;
        if (r.charges < RESONANCE_HITS) {
            r.charges++;
            if (r.charges == RESONANCE_HITS) cueReady(sp, path);
            return;
        }
        r.charges = 0;
        r.restUntil = now + RESONANCE_REST;
        float amount = e.getNewDamage();
        // No tick seguinte: o golpe atual termina primeiro (sem dano dentro de dano)
        sp.server.tell(new TickTask(sp.server.getTickCount() + 1, () -> {
            if (sp.isAlive() && !sp.hasDisconnected()) unleash(sp, path, target, amount);
        }));
    }

    /** Depuração: enche a Ressonância (o próximo golpe solta a habilidade). */
    public static void fillResonance(ServerPlayer sp) {
        Resonance r = RESONANCE.computeIfAbsent(sp.getUUID(), u -> new Resonance());
        r.charges = RESONANCE_HITS;
        r.restUntil = 0;
        r.lastHit = 0;
        cueReady(sp, MagicApi.get(sp).path());
    }

    private static void cueReady(ServerPlayer sp, MagicPath path) {
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.5F);
        if (sp.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, sp.getX(), sp.getY() + 1.0, sp.getZ(), 12, 0.4, 0.5, 0.4, 0.05);
        }
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.gear_plus.bond.ready",
                abilityName(path).withColor(path.rgb())), true);
    }

    // =====================================================================================
    //  Habilidades do despertar (uma por classe)
    // =====================================================================================

    private static void unleash(ServerPlayer sp, MagicPath path, LivingEntity target, float amount) {
        if (!(sp.level() instanceof ServerLevel level)) return;
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
        switch (path) {
            case SMITH -> {
                // Golpe da Forja: onda de choque em volta do alvo, com brasas
                for (LivingEntity foe : foesAround(sp, level, at, 4.0, null, 8)) {
                    burst(sp, foe, amount * 0.5F);
                    foe.igniteForSeconds(3F);
                }
                fx(level, at, ParticleTypes.FLAME, 30, 1.6, SoundEvents.ANVIL_LAND, 0.5F, 0.7F);
                fx(level, at, ParticleTypes.LAVA, 8, 1.0, null, 0, 0);
            }
            case KNIGHT -> {
                // Juramento de Aço: o alvo perde o equilíbrio e o cavaleiro ganha um escudo curto
                if (target.isAlive()) target.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, 20, 0), sp);
                sp.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, 1));
                sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 0));
                fx(level, sp.position().add(0, 1, 0), ParticleTypes.END_ROD, 24, 0.8, SoundEvents.SHIELD_BLOCK, 0.9F, 0.8F);
            }
            case ROGUE -> {
                // Lâmina Fantasma: corte extra e o ladino some por um instante
                if (target.isAlive()) burst(sp, target, amount * 0.6F);
                sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
                sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, false, false));
                fx(level, at, ParticleTypes.SMOKE, 24, 0.8, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9F, 0.6F);
            }
            case QUINCY -> {
                // Chuva de Luz: setas de luz caem nos 3 inimigos mais próximos do alvo
                for (LivingEntity foe : foesAround(sp, level, at, 6.0, null, 3)) {
                    burst(sp, foe, amount * 0.35F);
                    level.sendParticles(ParticleTypes.END_ROD, foe.getX(), foe.getY() + foe.getBbHeight() + 1.5, foe.getZ(),
                            10, 0.1, 0.9, 0.1, 0.01);
                }
                fx(level, at, ParticleTypes.GLOW, 14, 1.2, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.9F, 1.6F);
            }
            case GUNSLINGER -> {
                // Ricochete Perfeito: o tiro salta para até 3 outros inimigos
                Vec3 from = at;
                for (LivingEntity foe : foesAround(sp, level, at, 8.0, target, 3)) {
                    burst(sp, foe, amount * 0.5F);
                    Vec3 to = foe.position().add(0, foe.getBbHeight() * 0.5, 0);
                    trail(level, from, to, ParticleTypes.CRIT);
                    from = to;
                }
                fx(level, at, ParticleTypes.CRIT, 10, 0.6, SoundEvents.FIREWORK_ROCKET_BLAST, 0.6F, 1.5F);
            }
            case RANGER -> {
                // Vento Caçador: a presa fica lenta e exposta; o caçador acelera
                if (target.isAlive()) {
                    burst(sp, target, amount * 0.4F);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2), sp);
                    target.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 80, 0), sp);
                }
                sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, false));
                fx(level, at, ParticleTypes.CLOUD, 20, 1.0, SoundEvents.ELYTRA_FLYING, 0.3F, 1.8F);
            }
            case GAMBLER -> {
                // Sorte Grande: um de três prêmios
                int roll = sp.getRandom().nextInt(3);
                if (roll == 0) {
                    if (target.isAlive()) burst(sp, target, amount);
                } else if (roll == 1) {
                    sp.heal(sp.getMaxHealth() * 0.15F);
                } else {
                    MagicData md = MagicApi.get(sp);
                    md.addMana(md.maxMana() * 0.30F);
                    md.markDirty();
                }
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.gear_plus.bond.gambler." + roll)
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                fx(level, at, ParticleTypes.HAPPY_VILLAGER, 16, 0.9, SoundEvents.NOTE_BLOCK_BELL.value(), 0.9F, 1.4F);
                return; // a mensagem do prêmio já é o aviso
            }
            case STEELBALL -> {
                // Rotação Dourada: o alvo é lançado e fica tonto; quem está perto é empurrado
                if (target.isAlive()) {
                    burst(sp, target, amount * 0.5F);
                    target.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, 30, 0), sp);
                    if (!BossKit.isBossLike(target)) {
                        target.setDeltaMovement(target.getDeltaMovement().add(0, 0.6, 0));
                        target.hurtMarked = true;
                    }
                }
                for (LivingEntity foe : foesAround(sp, level, at, 3.5, target, 6)) {
                    Vec3 push = foe.position().subtract(at).multiply(1, 0, 1);
                    if (push.lengthSqr() > 1.0E-4 && !BossKit.isBossLike(foe)) {
                        foe.setDeltaMovement(foe.getDeltaMovement().add(push.normalize().scale(0.7).add(0, 0.25, 0)));
                        foe.hurtMarked = true;
                    }
                }
                fx(level, at, ParticleTypes.WAX_ON, 24, 1.2, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.7F, 1.3F);
            }
            default -> {
                return;
            }
        }
        sp.displayClientMessage(abilityName(path).withColor(path.rgb()), true);
    }

    /** Inimigo de verdade: monstro (ou mob que está atacando o jogador). Nunca jogadores, bichos mansos ou aliados. */
    static boolean isFoe(ServerPlayer sp, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le == sp || le instanceof Player) return false;
        if (le instanceof TamableAnimal t && t.isTame()) return false;
        if (le.isAlliedTo(sp)) return false;
        return le instanceof Enemy || (le instanceof Mob m && m.getTarget() == sp);
    }

    private static List<LivingEntity> foesAround(ServerPlayer sp, ServerLevel level, Vec3 center, double radius,
                                                 LivingEntity except, int max) {
        List<LivingEntity> out = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(center, center).inflate(radius),
                e -> e != except && isFoe(sp, e) && e.position().distanceToSqr(center) <= (radius + 1) * (radius + 1)));
        out.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(center)));
        return out.size() > max ? out.subList(0, max) : out;
    }

    /** Dano extra da habilidade (mágico, em nome do jogador), sem contar para a própria Ressonância. */
    private static void burst(ServerPlayer sp, LivingEntity target, float amount) {
        if (amount <= 0 || !target.isAlive()) return;
        boolean was = inBurst;
        inBurst = true;
        try {
            target.invulnerableTime = 0;
            target.hurt(sp.damageSources().indirectMagic(sp, sp), amount);
        } finally {
            inBurst = was;
        }
    }

    private static void fx(ServerLevel level, Vec3 at, ParticleOptions particle, int count, double spread,
                           SoundEvent sound, float volume, float pitch) {
        level.sendParticles(particle, at.x, at.y, at.z, count, spread * 0.5, spread * 0.4, spread * 0.5, 0.04);
        if (sound != null) level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void trail(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle) {
        int steps = (int) Math.min(16, Math.max(2, from.distanceTo(to) * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private WeaponBond() {}
}
