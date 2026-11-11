package com.renanpontes.isekaimahou.feature.armory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.armory.item.ArmoryWeapon;
import com.renanpontes.isekaimahou.glyph.weapon.MeleeSwings;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Traços e passivas das armas de golpe das classes v0.9 (servidor):
 * <ul>
 *   <li>Espada Longa IV — Ímpeto Heroico: golpes cheios acumulam Ímpeto (até 3, somem em 4 s); com 3, o próximo golpe
 *       cheio causa +60% e solta um corte de fogo.</li>
 *   <li>Maça Sagrada — Abençoada: +25% contra mortos-vivos (IV: +50%). IV — Julgamento Sagrado: golpes cheios curam você
 *       e os aliados a até 6 blocos (no máx. 1× a cada 2 s).</li>
 *   <li>Machado Grande — Quebra-Escudos (no item). IV — Fúria Sanguinária: +1% de dano a cada 2% de vida perdida (até +45%).</li>
 *   <li>Manoplas de Ki IV — Palma do Vendaval: a cada 4 golpes cheios seguidos, uma onda de ki empurra e fere os inimigos
 *       em volta do alvo.</li>
 *   <li>Escudo do Cavaleiro — Muralha: bloquear um golpe corpo a corpo empurra o atacante. Lâmina do Cavaleiro IV —
 *       Bastião: bloquear com escudo devolve 40% do dano bloqueado e dá Absorção.</li>
 *   <li>Adagas Gêmeas — Golpe Duplo: golpes cheios acertam de novo com a outra adaga 4 ticks depois (35% do dano).
 *       IV — Lâminas Envenenadas: o golpe duplo envenena (Veneno II, 3 s) e golpes pelas costas causam +40%.</li>
 * </ul>
 * O "golpe cheio" é o golpe PRINCIPAL (não a varrida) com a barra de ataque ≥ 90%, medido pelo {@link MeleeSwings} do
 * núcleo — então os golpes extras daqui nunca disparam outros golpes extras.
 */
public final class ClassWeaponPassives {
    public static final float FULL_STRENGTH = 0.9F;

    public static final int IMPETUS_MAX = 3;
    public static final int IMPETUS_EXPIRE = 80;
    public static final float IMPETUS_BONUS = 0.6F;

    public static final float UNDEAD_BONUS = 0.25F;
    public static final float UNDEAD_BONUS_IV = 0.5F;
    /** v1.0: ×HEAL_SCALE (vida base dobrou). */
    public static final float JUDGEMENT_HEAL = 1.5F * com.renanpontes.isekaimahou.core.Progression.HEAL_SCALE;
    public static final int JUDGEMENT_COOLDOWN = 40;
    public static final double JUDGEMENT_RADIUS = 6.0;

    public static final float FURY_MAX = 0.45F;

    public static final int COMBO_HITS = 4;
    public static final int COMBO_GAP = 50;
    /** v1.0: ×DAMAGE_SCALE (mobs com mais vida). */
    public static final float KI_WAVE_DAMAGE = 5.0F * com.renanpontes.isekaimahou.core.Progression.DAMAGE_SCALE;
    public static final double KI_WAVE_RADIUS = 3.0;

    public static final int WALL_COOLDOWN = 10;
    public static final float BASTION_REFLECT = 0.4F;
    public static final int BASTION_COOLDOWN = 20;

    public static final float TWIN_FRACTION = 0.35F;
    public static final int TWIN_DELAY = 4;
    public static final float BACKSTAB_BONUS = 0.4F;
    private static final int MAX_PENDING = 256;

    private static final int FIRE = 0xFF7A2A;
    private static final int HOLY = 0xFFE58A;
    private static final int KI = 0x8AF0E0;
    private static final int SHADOW = 0xA070E8;

    private static final class State {
        int impetus;
        long impetusTick;
        long impetusSpentTick = Long.MIN_VALUE;
        int combo;
        long comboTick;
        long judgementTick = Long.MIN_VALUE;
        long wallTick = Long.MIN_VALUE;
        long bastionTick = Long.MIN_VALUE;
    }

    /** Segundo golpe das Adagas Gêmeas, agendado para daqui a {@link #TWIN_DELAY} ticks. */
    private record Strike(ServerPlayer player, LivingEntity target, long due, float damage, boolean poison) {}

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final List<Strike> PENDING = new ArrayList<>();

    private static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    /** Golpe corpo a corpo direto do jogador (não magia, não espinhos, não flecha). */
    private static ServerPlayer meleeAttacker(DamageSource src) {
        if (!(src.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer || src.getDirectEntity() != sp) return null;
        if (src.is(CoreRegistries.SPELL_DAMAGE) || src.is(DamageTypes.THORNS)) return null;
        return sp;
    }

    private static boolean fullHit(ServerPlayer sp, LivingEntity target) {
        return MeleeSwings.mainHitStrength(sp, target, sp.level().getGameTime()) >= FULL_STRENGTH;
    }

    // =====================================================================================
    //  Antes do dano: multiplicadores
    // =====================================================================================
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        ServerPlayer sp = meleeAttacker(event.getSource());
        if (sp == null || target == sp) return;
        ArmoryWeapon w = ArmoryWeapon.of(sp.getMainHandItem());
        if (w == null) return;
        long now = sp.level().getGameTime();
        float mult = 1F;
        switch (w.kind()) {
            case LONGSWORD -> {
                State st = state(sp);
                if (w.tier() >= WeaponKind.MAX_TIER && st.impetus >= IMPETUS_MAX && now - st.impetusTick <= IMPETUS_EXPIRE
                        && fullHit(sp, target)) {
                    st.impetus = 0;
                    st.impetusSpentTick = now;
                    mult += IMPETUS_BONUS;
                    ServerLevel level = sp.serverLevel();
                    WeaponFx.slash(level, sp, WeaponFx.center(target), Element.FIRE, FIRE);
                    level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                            14, 0.4, 0.4, 0.4, 0.06);
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
                }
            }
            case HOLY_MACE -> {
                if (target.getType().is(EntityTypeTags.UNDEAD)) {
                    mult += w.tier() >= WeaponKind.MAX_TIER ? UNDEAD_BONUS_IV : UNDEAD_BONUS;
                }
            }
            case GREATAXE -> {
                if (w.tier() >= WeaponKind.MAX_TIER) {
                    float missing = 1F - sp.getHealth() / Math.max(1F, sp.getMaxHealth());
                    float bonus = Math.min(FURY_MAX, missing * 0.5F);
                    mult += bonus;
                    if (bonus >= 0.15F) {
                        sp.serverLevel().sendParticles(WeaponFx.dust(0xC81E1E, 1.2F), target.getX(), target.getY() + target.getBbHeight() * 0.6,
                                target.getZ(), 6 + Math.round(bonus * 20), 0.3, 0.3, 0.3, 0);
                    }
                }
            }
            case TWIN_DAGGERS -> {
                if (w.tier() >= WeaponKind.MAX_TIER && behind(sp, target)) {
                    mult += BACKSTAB_BONUS;
                    sp.serverLevel().sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(),
                            8, 0.25, 0.25, 0.25, 0.2);
                }
            }
            default -> {
            }
        }
        if (mult != 1F) event.setAmount(event.getAmount() * mult);
    }

    /** O atacante está atrás do alvo (fora do cone de 110° da frente dele). */
    private static boolean behind(LivingEntity attacker, LivingEntity target) {
        Vec3 to = new Vec3(attacker.getX() - target.getX(), 0, attacker.getZ() - target.getZ());
        if (to.lengthSqr() < 1.0E-4) return false;
        Vec3 facing = Vec3.directionFromRotation(0, target.getYHeadRot());
        return facing.dot(to.normalize()) < -0.35;
    }

    // =====================================================================================
    //  Depois do dano: acúmulos, curas, golpes extras
    // =====================================================================================
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0) return;
        LivingEntity target = event.getEntity();
        ServerPlayer sp = meleeAttacker(event.getSource());
        if (sp == null || target == sp) return;
        ArmoryWeapon w = ArmoryWeapon.of(sp.getMainHandItem());
        if (w == null || !fullHit(sp, target)) return;
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        State st = state(sp);
        switch (w.kind()) {
            case LONGSWORD -> {
                if (w.tier() < WeaponKind.MAX_TIER || st.impetusSpentTick == now) return;
                if (now - st.impetusTick > IMPETUS_EXPIRE) st.impetus = 0;
                st.impetus = Math.min(IMPETUS_MAX, st.impetus + 1);
                st.impetusTick = now;
                Vec3 hand = WeaponFx.handPos(sp);
                level.sendParticles(WeaponFx.dust(FIRE, 0.8F), hand.x, hand.y + 0.3, hand.z, 3 * st.impetus, 0.15, 0.3, 0.15, 0);
                if (st.impetus >= IMPETUS_MAX) {
                    level.sendParticles(ParticleTypes.FLAME, hand.x, hand.y + 0.4, hand.z, 8, 0.1, 0.35, 0.1, 0.02);
                    level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.4F, 1.6F);
                }
            }
            case HOLY_MACE -> {
                if (target.getType().is(EntityTypeTags.UNDEAD)) {
                    // Pilar de luz no morto-vivo atingido
                    for (int i = 0; i < 8; i++) {
                        level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + i * 0.35, target.getZ(), 1, 0.12, 0.05, 0.12, 0.01);
                    }
                }
                if (w.tier() >= WeaponKind.MAX_TIER && now - st.judgementTick >= JUDGEMENT_COOLDOWN) {
                    st.judgementTick = now;
                    judgement(level, sp);
                }
            }
            case KI_GAUNTLETS -> {
                if (w.tier() < WeaponKind.MAX_TIER) return;
                if (now - st.comboTick > COMBO_GAP) st.combo = 0;
                st.combo++;
                st.comboTick = now;
                Vec3 c = WeaponFx.center(target);
                level.sendParticles(WeaponFx.dust(KI, 0.9F), c.x, c.y, c.z, 3 + st.combo * 2, 0.25, 0.25, 0.25, 0);
                if (st.combo >= COMBO_HITS) {
                    st.combo = 0;
                    kiWave(level, sp, target);
                }
            }
            case TWIN_DAGGERS -> {
                if (PENDING.size() >= MAX_PENDING) return;
                float dmg = (float) sp.getAttributeValue(Attributes.ATTACK_DAMAGE) * TWIN_FRACTION;
                PENDING.add(new Strike(sp, target, now + TWIN_DELAY, dmg, w.tier() >= WeaponKind.MAX_TIER));
            }
            default -> {
            }
        }
    }

    /** Julgamento Sagrado: cura você e os aliados próximos, com um anel dourado. */
    private static void judgement(ServerLevel level, ServerPlayer sp) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(JUDGEMENT_RADIUS),
                e -> e.isAlive() && ArmoryAllies.isFriend(sp, e) && e.distanceToSqr(sp) <= JUDGEMENT_RADIUS * JUDGEMENT_RADIUS)) {
            if (e.getHealth() >= e.getMaxHealth()) continue;
            e.heal(JUDGEMENT_HEAL);
            level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(), 1, 0.2, 0.1, 0.2, 0);
        }
        WeaponFx.groundRing(level, sp, HOLY, 1.6, 18);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 0.8F);
    }

    /** Palma do Vendaval: onda de ki em volta do alvo (fere e empurra os OUTROS inimigos; o alvo já levou o golpe). */
    private static void kiWave(ServerLevel level, ServerPlayer sp, LivingEntity target) {
        Vec3 c = target.position();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(KI_WAVE_RADIUS),
                e -> e != target && ArmoryAllies.isEnemy(sp, e) && e.distanceToSqr(target) <= KI_WAVE_RADIUS * KI_WAVE_RADIUS)) {
            e.hurt(sp.damageSources().playerAttack(sp), KI_WAVE_DAMAGE);
            Vec3 away = e.position().subtract(c);
            if (away.lengthSqr() > 1.0E-4) e.knockback(0.7, -away.x, -away.z);
        }
        Vec3 push = target.position().subtract(sp.position());
        if (push.lengthSqr() > 1.0E-4) target.knockback(0.6, -push.x, -push.z);
        ParticleOptions ki = WeaponFx.dust(KI, 1.3F);
        for (int ring = 1; ring <= 3; ring++) {
            double r = ring * 0.9;
            int points = 10 + ring * 6;
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                level.sendParticles(ring == 3 ? ParticleTypes.CLOUD : ki, c.x + Math.cos(a) * r, c.y + 0.3 + ring * 0.15,
                        c.z + Math.sin(a) * r, 1, 0, 0.02, 0, ring == 3 ? 0.02 : 0);
            }
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.9F, 1.2F);
    }

    // =====================================================================================
    //  Escudo: Muralha (Escudo do Cavaleiro) e Bastião (Lâmina do Cavaleiro IV)
    // =====================================================================================
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!event.getBlocked() || !(event.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        DamageSource src = event.getDamageSource();
        if (!(src.getEntity() instanceof LivingEntity attacker) || src.getDirectEntity() != attacker || attacker == sp) return;
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        State st = state(sp);
        if (sp.getUseItem().is(ArmoryItems.KNIGHT_SHIELD.get()) && now - st.wallTick >= WALL_COOLDOWN) {
            st.wallTick = now;
            Vec3 d = attacker.position().subtract(sp.position());
            if (d.lengthSqr() > 1.0E-4) attacker.knockback(0.8, -d.x, -d.z);
            Vec3 mid = sp.getEyePosition().lerp(WeaponFx.center(attacker), 0.35);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, mid.x, mid.y, mid.z, 8, 0.2, 0.2, 0.2, 0.15);
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.6F);
        }
        ItemStack main = sp.getMainHandItem();
        if (ArmoryWeapon.is(main, WeaponKind.KNIGHT_BLADE, WeaponKind.MAX_TIER) && now - st.bastionTick >= BASTION_COOLDOWN
                && attacker.distanceToSqr(sp) <= 36) {
            st.bastionTick = now;
            float reflect = event.getBlockedDamage() * BASTION_REFLECT;
            if (reflect > 0) attacker.hurt(sp.damageSources().thorns(sp), reflect);
            if (!sp.hasEffect(MobEffects.ABSORPTION)) {
                sp.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0, false, true));
            }
            WeaponFx.groundRing(level, sp, HOLY, 1.2, 14);
            level.sendParticles(ParticleTypes.END_ROD, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(),
                    6, 0.2, 0.3, 0.2, 0.03);
        }
    }

    // =====================================================================================
    //  Golpe duplo das adagas (agenda curta) e limpeza
    // =====================================================================================
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        Iterator<Strike> it = PENDING.iterator();
        while (it.hasNext()) {
            Strike s = it.next();
            if (s.due() > now && s.due() - now <= TWIN_DELAY * 4L) continue;
            it.remove();
            twinStrike(s);
        }
    }

    private static void twinStrike(Strike s) {
        ServerPlayer sp = s.player();
        LivingEntity target = s.target();
        if (!sp.isAlive() || sp.isRemoved() || !target.isAlive() || target.isRemoved() || target.level() != sp.level()) return;
        if (target.distanceToSqr(sp) > 36 || !ArmoryWeapon.is(sp.getMainHandItem(), WeaponKind.TWIN_DAGGERS, 1)) return;
        // Sem a invencibilidade do golpe principal (senão o segundo corte nunca entraria), e devolvendo-a depois para
        // não atrapalhar o próximo golpe do jogador
        int prev = target.invulnerableTime;
        target.invulnerableTime = 0;
        boolean hit = target.hurt(sp.damageSources().playerAttack(sp), s.damage());
        target.invulnerableTime = prev;
        sp.swing(InteractionHand.OFF_HAND, true);
        ServerLevel level = sp.serverLevel();
        Vec3 c = WeaponFx.center(target);
        level.sendParticles(WeaponFx.dust(SHADOW, 0.9F), c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.7F);
        if (hit && s.poison()) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1), sp);
            level.sendParticles(ParticleTypes.ITEM_SLIME, c.x, c.y, c.z, 5, 0.25, 0.25, 0.25, 0.05);
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        STATES.remove(id);
        PENDING.removeIf(s -> s.player().getUUID().equals(id));
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
        PENDING.clear();
    }

    private ClassWeaponPassives() {}
}
