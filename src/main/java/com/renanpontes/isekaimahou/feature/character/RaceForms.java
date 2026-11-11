package com.renanpontes.isekaimahou.feature.character;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicHover;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.api.Statuses;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * <b>Transformações raciais</b> (v1.0.1) — a ultimate de cada raça, liberada no nível de raça V ({@link RaceProgress}).
 * <p>
 * Ativação dedicada (não ocupa o slot G): <b>tecla racial</b> (V). Dura {@link #DURATION} ticks; recarga de
 * {@link #COOLDOWN} contada a partir da ativação + duração ({@link CharacterData#formReadyAt()}). Durante a forma, a
 * tecla racial usa o <b>golpe da forma</b> (recarga {@link #POWER_COOLDOWN}). Os atributos ficam no MobEffect da forma
 * ({@link CharacterContent#form}); o resto está aqui.
 * <p>
 * <b>Voo</b> (Anjo, Demônio, Dracônico; Fantasma voa devagar): {@code abilities.mayfly} enquanto o efeito durar, marcado
 * em {@code persistentData} ({@value #FLIGHT_TAG}). A checagem de todo tick tira o voo assim que o efeito some (fim do
 * tempo, leite, morte, relog com o efeito vencido) e dá Queda Lenta para pousar; criativo/espectador nunca são tocados.
 * O efeito da forma é do namespace do mod, então o {@link MagicHover} já isenta o jogador da expulsão por "voar".
 */
public final class RaceForms {
    public static final int DURATION = 45 * 20;
    public static final int COOLDOWN = 5 * 60 * 20;
    public static final int POWER_COOLDOWN = 6 * 20;
    private static final String FLIGHT_TAG = IsekaiMahou.MODID + ".race_flight";
    private static final float DEFAULT_FLY_SPEED = 0.05F, PHANTOM_FLY_SPEED = 0.03F;
    private static final int MITOSIS_GAP = 60;

    /** Forma em vigor por jogador (para perceber quando acaba, por qualquer motivo). */
    private static final Map<UUID, Race> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> POWER_READY = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> MITOSIS_READY = new ConcurrentHashMap<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener(RaceForms::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            UUID id = e.getEntity().getUUID();
            ACTIVE.remove(id);
            POWER_READY.remove(id);
            MITOSIS_READY.remove(id);
        });
    }

    static void clearAll() {
        ACTIVE.clear();
        POWER_READY.clear();
        MITOSIS_READY.clear();
    }

    /** A forma dá voo? */
    public static boolean flies(Race r) {
        return r == Race.ANGEL || r == Race.DEMON || r == Race.DRAGONBORN || r == Race.PHANTOM;
    }

    /** Forma racial ativa na entidade (null = nenhuma). */
    @Nullable
    public static Race formOf(LivingEntity e) {
        for (Race r : Race.ALL) {
            if (e.hasEffect(CharacterContent.form(r))) return r;
        }
        return null;
    }

    // =====================================================================================
    //  Tecla racial
    // =====================================================================================
    /** V: transforma (nível V), usa o golpe da forma (transformado) ou, antes do nível V, liga/desliga a visão. */
    static void onKey(ServerPlayer sp) {
        CharacterData d = CharacterContent.data(sp);
        if (!d.created()) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.not_created"), true);
            return;
        }
        Race form = formOf(sp);
        if (form != null) {
            usePower(sp, d, form);
            return;
        }
        Race r = d.race();
        if (RaceProgress.level(sp) < RaceProgress.MAX_LEVEL) {
            if (r.darkvision != Race.Darkvision.NONE) {
                RacePassives.toggleVisionKey(sp);
                return;
            }
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.form_locked", r.formName().withColor(r.rgb),
                    RaceProgress.charLevelFor(RaceProgress.MAX_LEVEL)).withColor(0xA89EC0), true);
            return;
        }
        long now = RacePassives.now(sp);
        if (now < d.formReadyAt() && !sp.getAbilities().instabuild) {
            int secs = (int) Math.ceil((d.formReadyAt() - now) / 20.0);
            CharacterService.fail(sp, Component.translatable("message.isekai_mahou.character.form_cooldown", r.formName(), secs));
            return;
        }
        activate(sp, d, r);
    }

    static void activate(ServerPlayer sp, CharacterData d, Race r) {
        d.setFormReadyAt(RacePassives.now(sp) + DURATION + COOLDOWN);
        sp.addEffect(new MobEffectInstance(CharacterContent.form(r), DURATION, 0, false, false, true));
        start(sp, r, DURATION, true);
    }

    /** Liga os visuais/efeitos ocultos (ativação ou o jogador voltando com a forma ainda ativa). */
    private static void start(ServerPlayer sp, Race r, int ticks, boolean burst) {
        ACTIVE.put(sp.getUUID(), r);
        RaceFx.entityFx(sp, CharacterNetwork.FX_FORM, ticks);
        MagicHover.allow(sp, ticks + 100);
        if (r == Race.PHANTOM) sp.addEffect(hidden(MobEffects.INVISIBILITY, ticks));
        if (r == Race.DEMON) sp.addEffect(hidden(MobEffects.FIRE_RESISTANCE, ticks));
        if (!burst) return;

        ServerLevel level = sp.serverLevel();
        Vec3 c = RaceFx.center(sp);
        switch (r) {
            case HUMAN -> {
                // Despertar Heroico: a vontade limpa o corpo
                List<Holder<MobEffect>> bad = new ArrayList<>();
                for (MobEffectInstance i : sp.getActiveEffects()) {
                    if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL && !i.is(CoreRegistries.MAGIC_EXHAUSTION)) {
                        bad.add(i.getEffect());
                    }
                }
                bad.forEach(sp::removeEffect);
                sp.heal(sp.getMaxHealth() * 0.3F);
            }
            case SLIME -> sp.heal(sp.getMaxHealth() * 0.25F);
            case ANGEL -> sp.clearFire();
            default -> { }
        }
        sp.setHealth(Math.min(sp.getHealth(), sp.getMaxHealth()));
        // Explosão de transformação: coluna, anel duplo e a partícula da raça
        RaceFx.dustBurst(level, c, r.rgb, 70, 0.9);
        RaceFx.spiral(level, sp, RaceFx.dust(r.rgb, 1.6F), 40, 1.1);
        RaceFx.ring(level, sp.position().add(0, 0.1, 0), 2.2, 28, RaceFx.dust(r.rgb, 1.4F));
        RaceFx.ring(level, sp.position().add(0, 0.1, 0), 3.2, 36, signature(sp, r));
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(signature(sp, r), c.x, c.y, c.z, 40, 0.8, 1.2, 0.8, 0.08);
        RaceFx.sound(level, c, formSound(r), 1.0F, 1.0F);
        RaceFx.sound(level, c, SoundEvents.TOTEM_USE, 0.5F, 1.3F);
        Rewards.banner(sp, Component.translatable("message.isekai_mahou.character.form_start", r.formName()), r.rgb);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.form_power_hint", r.formPower(),
                Component.keybind("key.isekai_mahou.racial_ability")).withColor(r.rgb), true);
    }

    /** Efeito vanilla oculto (ambiente, sem partícula nem ícone) — a marca para tirar só o nosso no fim. */
    private static MobEffectInstance hidden(Holder<MobEffect> e, int ticks) {
        return new MobEffectInstance(e, ticks, 0, true, false, false);
    }

    private static void removeHidden(ServerPlayer sp, Holder<MobEffect> e) {
        MobEffectInstance cur = sp.getEffect(e);
        if (cur != null && cur.isAmbient() && !cur.isVisible() && !cur.showIcon()) sp.removeEffect(e);
    }

    private static void end(ServerPlayer sp, Race r) {
        ACTIVE.remove(sp.getUUID());
        POWER_READY.remove(sp.getUUID());
        RaceFx.entityFx(sp, CharacterNetwork.FX_FORM, 0);
        if (r == Race.PHANTOM) removeHidden(sp, MobEffects.INVISIBILITY);
        if (r == Race.DEMON) removeHidden(sp, MobEffects.FIRE_RESISTANCE);
        if (!sp.isAlive()) return;
        ServerLevel level = sp.serverLevel();
        RaceFx.dustBurst(level, RaceFx.center(sp), r.rgb, 30, 0.6);
        level.sendParticles(ParticleTypes.POOF, sp.getX(), sp.getY() + 1.0, sp.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
        RaceFx.sound(level, sp.position(), SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.2F);
        CharacterData d = CharacterContent.data(sp);
        int secs = (int) Math.max(0, Math.ceil((d.formReadyAt() - RacePassives.now(sp)) / 20.0));
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.form_end", r.formName(), secs / 60, secs % 60)
                .withColor(0xA89EC0), true);
    }

    // =====================================================================================
    //  Tick (CharacterService, todo tick)
    // =====================================================================================
    static void tick(ServerPlayer sp, CharacterData d) {
        Race form = formOf(sp);
        handleFlight(sp, form);
        Race was = ACTIVE.get(sp.getUUID());
        if (form == null) {
            if (was != null) end(sp, was);
            return;
        }
        if (was != form) {
            if (was != null) end(sp, was);
            MobEffectInstance inst = sp.getEffect(CharacterContent.form(form));
            start(sp, form, inst == null ? DURATION : inst.getDuration(), false);
        }
        // Raça trocada no Altar no meio da forma: a forma acaba
        if (!d.is(form) && !sp.getAbilities().instabuild) {
            sp.removeEffect(CharacterContent.form(form));
            return;
        }
        if (!sp.isAlive()) return; // animação de morte: sem auras
        int t = sp.tickCount;
        if (t % 6 == 0) ambient(sp, form, d);
        if (t % 20 == 0) perSecond(sp, form);
        if (t % 40 == 0) everyTwoSeconds(sp, form);
    }

    private static void handleFlight(ServerPlayer sp, @Nullable Race form) {
        Abilities a = sp.getAbilities();
        boolean tagged = sp.getPersistentData().getBoolean(FLIGHT_TAG);
        boolean special = sp.isCreative() || sp.isSpectator();
        if (form != null && flies(form) && !special && sp.isAlive()) {
            boolean changed = false;
            if (!a.mayfly) {
                a.mayfly = true;
                changed = true;
            }
            float speed = form == Race.PHANTOM ? PHANTOM_FLY_SPEED : DEFAULT_FLY_SPEED;
            if (Math.abs(a.getFlyingSpeed() - speed) > 1e-4F) {
                a.setFlyingSpeed(speed);
                changed = true;
            }
            if (changed) sp.onUpdateAbilities();
            if (!tagged) sp.getPersistentData().putBoolean(FLIGHT_TAG, true);
        } else if (tagged) {
            revokeFlight(sp);
        }
    }

    /** Tira o voo dado pela forma (só se fomos nós que demos) e garante um pouso seguro. */
    static void revokeFlight(ServerPlayer sp) {
        sp.getPersistentData().remove(FLIGHT_TAG);
        Abilities a = sp.getAbilities();
        if (!sp.isCreative() && !sp.isSpectator()) {
            a.mayfly = false;
            a.flying = false;
        }
        a.setFlyingSpeed(DEFAULT_FLY_SPEED);
        sp.onUpdateAbilities();
        if (!sp.isAlive()) return;
        MagicHover.allow(sp, 200);
        sp.resetFallDistance();
        if (!sp.onGround()) sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, true, false, true));
    }

    private static void ambient(ServerPlayer sp, Race r, CharacterData d) {
        ServerLevel level = sp.serverLevel();
        double x = sp.getX(), y = sp.getY(), z = sp.getZ(), h = sp.getBbHeight();
        switch (r) {
            case ANGEL -> {
                level.sendParticles(ParticleTypes.END_ROD, x, y + h * 0.7, z, 1, 0.5, 0.4, 0.5, 0.01);
                level.sendParticles(RaceFx.dust(0xFFF2B0, 1.0F), x, y + h + 0.3, z, 2, 0.25, 0.05, 0.25, 0);
            }
            case DEMON -> {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + h * 0.4, z, 2, 0.35, 0.5, 0.35, 0.01);
                level.sendParticles(ParticleTypes.SMOKE, x, y + h * 0.8, z, 1, 0.3, 0.3, 0.3, 0.01);
            }
            case DRAGONBORN -> level.sendParticles(ElementFx.dust(d.ancestry(), 1.2F), x, y + h * 0.5, z, 3, 0.4, 0.6, 0.4, 0.01);
            case PHANTOM -> level.sendParticles(ParticleTypes.SOUL, x, y + 0.3, z, 1, 0.3, 0.2, 0.3, 0.01);
            case SLIME -> level.sendParticles(ParticleTypes.ITEM_SLIME, x, y + h * 0.5, z, 2, 0.5, 0.5, 0.5, 0.02);
            case ELF -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + h * 0.6, z, 1, 0.5, 0.6, 0.5, 0);
            case DWARF -> level.sendParticles(RaceFx.dust(0x9A8F80, 1.4F), x, y + h * 0.3, z, 2, 0.5, 0.4, 0.5, 0);
            case HALF_ORC -> level.sendParticles(RaceFx.dust(0xC02020, 1.2F), x, y + h * 0.6, z, 2, 0.4, 0.5, 0.4, 0);
            case BEASTFOLK -> level.sendParticles(RaceFx.dust(r.rgb, 1.0F), x, y + h * 0.4, z, 2, 0.4, 0.4, 0.4, 0);
            case HUMAN -> level.sendParticles(RaceFx.dust(0xFFD86B, 1.1F), x, y + h * 0.5, z, 2, 0.4, 0.6, 0.4, 0);
            case ARACHNID -> level.sendParticles(RaceFx.dust(r.rgb, 1.0F), x, y + h * 0.5, z, 2, 0.4, 0.5, 0.4, 0);
        }
    }

    private static void perSecond(ServerPlayer sp, Race r) {
        if (r == Race.SLIME && sp.getHealth() < sp.getMaxHealth()) {
            // Regeneração enorme do Rei Slime
            sp.heal(Math.max(3F, sp.getMaxHealth() * 0.03F));
        }
        MagicHover.allow(sp, 60);
    }

    private static void everyTwoSeconds(ServerPlayer sp, Race r) {
        ServerLevel level = sp.serverLevel();
        switch (r) {
            case ANGEL -> {
                // Pulso de cura em área + raios sagrados em até 2 inimigos
                for (Player p : level.getEntitiesOfClass(Player.class, sp.getBoundingBox().inflate(8.0), p -> p.isAlive() && !foe(sp, p))) {
                    p.heal(2F * Progression.HEAL_SCALE);
                    level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + p.getBbHeight() + 0.3, p.getZ(), 1, 0.2, 0.1, 0.2, 0);
                }
                RaceFx.ring(level, sp.position().add(0, 0.15, 0), 3.0, 20, RaceFx.dust(0xFFF2B0, 1.0F));
                List<LivingEntity> foes = foesAround(sp, 12.0);
                foes.sort(Comparator.comparingInt((LivingEntity e) -> e.getType().is(EntityTypeTags.UNDEAD) ? 0 : 1)
                        .thenComparingDouble(e -> e.distanceToSqr(sp)));
                for (int i = 0; i < Math.min(2, foes.size()); i++) smite(sp, foes.get(i), 6F);
            }
            case DEMON -> {
                for (LivingEntity le : foesAround(sp, 3.5)) {
                    hit(sp, le, 4F);
                    le.igniteForSeconds(2F);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
                }
            }
            case ELF -> {
                for (Player p : level.getEntitiesOfClass(Player.class, sp.getBoundingBox().inflate(8.0), p -> p.isAlive() && !foe(sp, p))) {
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, true, true), sp);
                }
            }
            default -> { }
        }
    }

    /** Raio sagrado do Anjo: coluna de luz do céu (dobro em mortos-vivos). */
    private static void smite(ServerPlayer sp, LivingEntity le, float base) {
        ServerLevel level = sp.serverLevel();
        float dmg = le.getType().is(EntityTypeTags.UNDEAD) ? base * 2F : base;
        hit(sp, le, dmg);
        le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false, true), sp);
        for (int i = 0; i < 10; i++) {
            level.sendParticles(ParticleTypes.END_ROD, le.getX(), le.getY() + i * 0.6, le.getZ(), 1, 0.05, 0.05, 0.05, 0);
        }
        level.sendParticles(RaceFx.dust(0xFFF2B0, 1.6F), le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 10, 0.3, 0.5, 0.3, 0);
        RaceFx.sound(level, le.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 1.8F);
    }

    // =====================================================================================
    //  Golpe da forma (tecla racial transformado)
    // =====================================================================================
    private static void usePower(ServerPlayer sp, CharacterData d, Race r) {
        long now = sp.serverLevel().getGameTime();
        Long ready = POWER_READY.get(sp.getUUID());
        if (ready != null && now < ready && !sp.getAbilities().instabuild) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.form_power_cooldown", r.formPower(),
                    (int) Math.ceil((ready - now) / 20.0)).withColor(0xA89EC0), true);
            return;
        }
        POWER_READY.put(sp.getUUID(), now + POWER_COOLDOWN);
        ServerLevel level = sp.serverLevel();
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        switch (r) {
            case HUMAN -> {
                // Golpe Heroico: meia-lua dourada à frente
                for (LivingEntity le : foesInCone(sp, 5.5, 0.2)) {
                    hit(sp, le, 18F);
                    knock(sp, le, 0.8);
                }
                Vec3 flat = flat(look);
                for (int i = -6; i <= 6; i++) {
                    Vec3 dir = rotateY(flat, i * 0.18);
                    Vec3 p = sp.position().add(0, 1.1, 0).add(dir.scale(3.0));
                    level.sendParticles(RaceFx.dust(0xFFD86B, 1.6F), p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0);
                    if (i % 3 == 0) level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                RaceFx.sound(level, eye, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.7F);
                RaceFx.sound(level, eye, SoundEvents.BELL_BLOCK, 0.6F, 1.4F);
            }
            case ELF -> {
                // Chuva de Folhas: até 8 alvos à frente, enraizados
                List<LivingEntity> foes = foesInCone(sp, 16.0, 0.75);
                for (int i = 0; i < Math.min(8, foes.size()); i++) {
                    LivingEntity le = foes.get(i);
                    hit(sp, le, 10F);
                    Statuses.apply(le, Statuses.Kind.ROOTED, 1, 40, sp, 1F);
                    RaceFx.line(level, eye, RaceFx.center(le), RaceFx.dust(i % 2 == 0 ? 0x7BD88F : 0x3E8A3A, 1.1F));
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, le.getX(), le.getY() + 0.5, le.getZ(), 6, 0.3, 0.3, 0.3, 0);
                }
                RaceFx.sound(level, eye, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.4F);
                RaceFx.sound(level, eye, SoundEvents.ARROW_SHOOT, 1.0F, 0.8F);
            }
            case DWARF -> {
                // Terremoto: o chão racha em volta
                for (LivingEntity le : foesAround(sp, 6.0)) {
                    hit(sp, le, 16F);
                    le.push(0, 0.6, 0);
                    le.hurtMarked = true;
                    Statuses.apply(le, Statuses.Kind.STUNNED, 1, 40, sp, 1F);
                }
                BlockParticleOption stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
                for (double rad = 1.5; rad <= 6.0; rad += 1.5) RaceFx.ring(level, sp.position().add(0, 0.2, 0), rad, (int) (rad * 8), stone);
                level.sendParticles(ParticleTypes.EXPLOSION, sp.getX(), sp.getY() + 0.3, sp.getZ(), 3, 1.5, 0.1, 1.5, 0);
                RaceFx.sound(level, sp.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 0.6F);
                RaceFx.sound(level, sp.position(), SoundEvents.ANVIL_LAND, 0.8F, 0.5F);
            }
            case HALF_ORC -> {
                // Brado de Guerra: inimigos fraquejam, aliados ganham Força
                for (LivingEntity le : foesAround(sp, 8.0)) {
                    le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1), sp);
                    le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 0), sp);
                    Statuses.apply(le, Statuses.Kind.EXPOSED, 1, 120, sp, 1F);
                    level.sendParticles(ParticleTypes.ANGRY_VILLAGER, le.getX(), le.getY() + le.getBbHeight() + 0.2, le.getZ(), 2, 0.2, 0.1, 0.2, 0);
                }
                for (Player p : level.getEntitiesOfClass(Player.class, sp.getBoundingBox().inflate(8.0), p -> p.isAlive() && !foe(sp, p))) {
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 0), sp);
                }
                RaceFx.ring(level, sp.position().add(0, 1.0, 0), 4.0, 32, RaceFx.dust(0xC02020, 1.5F));
                RaceFx.sound(level, eye, SoundEvents.RAVAGER_ROAR, 1.2F, 0.9F);
            }
            case BEASTFOLK -> {
                // Investida Feral: salta na presa mais alinhada e a dilacera
                LivingEntity target = aimed(sp, 14.0, 0.8);
                Vec3 dir = target == null ? flat(look) : RaceFx.center(target).subtract(sp.position()).normalize();
                sp.setDeltaMovement(dir.x * 1.6, Math.max(0.35, dir.y * 0.8 + 0.3), dir.z * 1.6);
                sp.hurtMarked = true;
                sp.resetFallDistance();
                if (target != null) {
                    hit(sp, target, 14F);
                    Statuses.apply(target, Statuses.Kind.BLEEDING, 2, 120, sp, 1F);
                    level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 20, 0.3, 0.4, 0.3, 0.3);
                }
                RaceFx.sound(level, eye, SoundEvents.WOLF_GROWL, 1.0F, 1.2F);
                RaceFx.sound(level, eye, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.8F);
            }
            case DEMON -> {
                // Chamas Negras: cone de fogo do Abismo
                for (LivingEntity le : foesInCone(sp, 8.0, 0.7)) {
                    hit(sp, le, 14F);
                    le.igniteForSeconds(6F);
                    Statuses.apply(le, Statuses.Kind.BURNING, 2, 100, sp, 1F);
                    Statuses.apply(le, Statuses.Kind.CORRUPTED, 1, 100, sp, 1F);
                }
                cone(level, eye, look, 8.0, ParticleTypes.SOUL_FIRE_FLAME, RaceFx.dust(0x2A1A22, 1.6F));
                RaceFx.sound(level, eye, SoundEvents.BLAZE_SHOOT, 1.0F, 0.5F);
                RaceFx.sound(level, eye, SoundEvents.WITHER_SHOOT, 0.6F, 0.7F);
            }
            case DRAGONBORN -> {
                // Sopro Dracônico no elemento da ancestralidade
                Element el = d.ancestry();
                for (LivingEntity le : foesInCone(sp, 10.0, 0.7)) {
                    hit(sp, le, 16F);
                    switch (el) {
                        case FIRE -> {
                            le.igniteForSeconds(5F);
                            Statuses.apply(le, Statuses.Kind.BURNING, 2, 100, sp, 1F);
                        }
                        case WATER -> {
                            Statuses.apply(le, Statuses.Kind.WET, 1, 100, sp, 1F);
                            Statuses.apply(le, Statuses.Kind.CHILLED, 2, 100, sp, 1F);
                        }
                        case EARTH -> Statuses.apply(le, Statuses.Kind.PETRIFIED, 1, 60, sp, 1F);
                        case WIND -> knock(sp, le, 1.4);
                        case LIGHT -> Statuses.apply(le, Statuses.Kind.EXPOSED, 2, 120, sp, 1F);
                        case SHADOW -> Statuses.apply(le, Statuses.Kind.CORRUPTED, 2, 120, sp, 1F);
                        default -> { }
                    }
                }
                cone(level, eye, look, 10.0, ElementFx.signature(el), ElementFx.dust(el, 1.6F));
                for (int i = 0; i < 12; i++) {
                    Vec3 j = look.add((sp.getRandom().nextDouble() - 0.5) * 0.5, (sp.getRandom().nextDouble() - 0.5) * 0.3,
                            (sp.getRandom().nextDouble() - 0.5) * 0.5).normalize();
                    level.sendParticles(ParticleTypes.DRAGON_BREATH, eye.x, eye.y - 0.1, eye.z, 0, j.x, j.y, j.z, 0.6);
                }
                RaceFx.sound(level, eye, SoundEvents.ENDER_DRAGON_SHOOT, 1.0F, 0.9F);
                RaceFx.sound(level, eye, SoundEvents.ENDER_DRAGON_GROWL, 0.5F, 1.3F);
            }
            case ARACHNID -> {
                // Tempestade de Teias: prende tudo em volta
                BlockParticleOption web = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState());
                for (LivingEntity le : foesAround(sp, 8.0)) {
                    hit(sp, le, 8F);
                    Statuses.apply(le, Statuses.Kind.ROOTED, 1, 60, sp, 1F);
                    le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3), sp);
                    le.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1), sp);
                    RaceFx.line(level, RaceFx.center(sp), RaceFx.center(le), RaceFx.dust(0xF0F0F0, 0.8F));
                    level.sendParticles(web, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
                }
                RaceFx.ring(level, sp.position().add(0, 0.2, 0), 4.0, 30, web);
                RaceFx.sound(level, eye, SoundEvents.SPIDER_AMBIENT, 1.2F, 0.7F);
            }
            case PHANTOM -> {
                // Travessia Espectral: atravessa os inimigos numa linha reta
                Vec3 from = sp.position().add(0, 0.6, 0);
                Vec3 to = from.add(look.scale(8.0));
                BlockHitResult bh = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
                Vec3 end = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation().subtract(look.scale(0.7)) : to;
                AABB path = new AABB(from, end).inflate(1.2);
                for (Entity e : level.getEntities(sp, path, e -> foe(sp, e))) {
                    LivingEntity le = (LivingEntity) e;
                    hit(sp, le, 12F);
                    le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), sp);
                    Statuses.apply(le, Statuses.Kind.CHILLED, 2, 100, sp, 1F);
                    level.sendParticles(ParticleTypes.SCULK_SOUL, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
                }
                RaceFx.line(level, from, end, ParticleTypes.SOUL);
                sp.teleportTo(end.x, end.y - 0.6, end.z);
                sp.resetFallDistance();
                RaceFx.sound(level, end, SoundEvents.PHANTOM_SWOOP, 1.0F, 1.2F);
                RaceFx.sound(level, end, SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.8F);
            }
            case ANGEL -> {
                // Julgamento: luz em área — fere inimigos (dobro em mortos-vivos), cura aliados
                for (LivingEntity le : foesAround(sp, 10.0)) smite(sp, le, 14F);
                for (Player p : level.getEntitiesOfClass(Player.class, sp.getBoundingBox().inflate(10.0), p -> p.isAlive() && !foe(sp, p))) {
                    p.heal(8F * Progression.HEAL_SCALE);
                    p.clearFire();
                    level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + p.getBbHeight() + 0.3, p.getZ(), 3, 0.3, 0.1, 0.3, 0);
                }
                RaceFx.ring(level, sp.position().add(0, 0.2, 0), 5.0, 40, RaceFx.dust(0xFFF2B0, 1.6F));
                RaceFx.ring(level, sp.position().add(0, 0.2, 0), 10.0, 60, ParticleTypes.END_ROD);
                level.sendParticles(ParticleTypes.FLASH, sp.getX(), sp.getY() + 2.0, sp.getZ(), 1, 0, 0, 0, 0);
                RaceFx.sound(level, eye, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.5F);
                RaceFx.sound(level, eye, SoundEvents.TRIDENT_THUNDER.value(), 0.5F, 1.6F);
            }
            case SLIME -> {
                // Absorção: engole os inimigos menores (e cura com eles); os grandes só levam ácido
                float healed = 0F;
                float cap = Math.max(60F, sp.getMaxHealth() * 1.5F);
                for (LivingEntity le : foesAround(sp, 4.5)) {
                    if (le.getMaxHealth() <= cap) {
                        Vec3 pull = sp.position().subtract(le.position()).normalize().scale(0.6);
                        le.push(pull.x, 0.2, pull.z);
                        le.hurtMarked = true;
                        float before = le.getHealth();
                        hit(sp, le, 20F);
                        healed += Math.max(0F, before - le.getHealth()) * 0.5F;
                        level.sendParticles(ParticleTypes.ITEM_SLIME, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 16, 0.3, 0.4, 0.3, 0.1);
                    } else {
                        hit(sp, le, 8F);
                        le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), sp);
                    }
                }
                if (healed > 0F) sp.heal(healed);
                RaceFx.ring(level, sp.position().add(0, 0.3, 0), 3.0, 30, ParticleTypes.ITEM_SLIME);
                RaceFx.sound(level, eye, SoundEvents.SLIME_SQUISH, 1.2F, 0.5F);
                RaceFx.sound(level, eye, SoundEvents.GENERIC_EAT, 1.0F, 0.6F);
            }
        }
    }

    // =====================================================================================
    //  Dano com a forma ativa
    // =====================================================================================
    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide()) return;
        DamageSource src = e.getSource();
        Entity attacker = src.getEntity();
        boolean melee = attacker instanceof LivingEntity && attacker == src.getDirectEntity() && attacker != victim;
        boolean spell = src.is(CoreRegistries.SPELL_DAMAGE);

        // ---------- quem bate transformado ----------
        if (attacker instanceof ServerPlayer sp && !(sp instanceof FakePlayer) && attacker != victim) {
            Race f = formOf(sp);
            if (f != null) {
                switch (f) {
                    case ELF -> {
                        if (src.getDirectEntity() instanceof Projectile && !spell) e.setAmount(e.getAmount() * 1.3F);
                    }
                    case HALF_ORC -> {
                        if (melee && e.getAmount() > 0) sp.heal(e.getAmount() * 0.2F);
                    }
                    case BEASTFOLK -> {
                        if (melee) Statuses.apply(victim, Statuses.Kind.BLEEDING, 1, 100, sp, 1F);
                    }
                    case DEMON -> {
                        if (melee) {
                            e.setAmount(e.getAmount() * 1.15F);
                            victim.igniteForSeconds(4F);
                            Statuses.apply(victim, Statuses.Kind.BURNING, 1, 80, sp, 1F);
                            ((ServerLevel) victim.level()).sendParticles(ParticleTypes.SOUL_FIRE_FLAME, victim.getX(),
                                    victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 8, 0.2, 0.3, 0.2, 0.03);
                        }
                    }
                    case ARACHNID -> {
                        if (melee) {
                            victim.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1), sp);
                            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), sp);
                        }
                    }
                    case ANGEL -> {
                        if (victim.getType().is(EntityTypeTags.UNDEAD)) e.setAmount(e.getAmount() * 1.5F);
                    }
                    case SLIME -> {
                        if (melee && e.getAmount() > 0) sp.heal(e.getAmount() * 0.1F);
                    }
                    default -> { }
                }
            }
        }

        // ---------- quem apanha transformado ----------
        if (!(victim instanceof ServerPlayer vp) || vp instanceof FakePlayer) return;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Race f = formOf(vp);
        if (f == null) return;
        if (src.is(DamageTypeTags.IS_FALL) && (flies(f) || f == Race.SLIME)) {
            e.setCanceled(true);
            return;
        }
        switch (f) {
            case HUMAN -> e.setAmount(e.getAmount() * 0.8F);
            case DWARF -> e.setAmount(e.getAmount() * 0.7F);
            case DRAGONBORN -> e.setAmount(e.getAmount() * 0.75F);
            case HALF_ORC -> e.setAmount(e.getAmount() * 1.1F);
            case PHANTOM -> {
                // Intangível: golpes e projéteis passam pela metade
                if (attacker != null && !spell) e.setAmount(e.getAmount() * 0.5F);
            }
            case BEASTFOLK -> {
                if (attacker != null && attacker != vp && !spell && !src.is(DamageTypeTags.IS_EXPLOSION)
                        && RacePassives.tryDodge(vp, vp.level().getGameTime(), 20, 0.25F, Race.BEASTFOLK)) {
                    e.setCanceled(true);
                }
            }
            case SLIME -> {
                e.setAmount(e.getAmount() * 0.85F);
                mitosis(vp, e.getAmount());
            }
            default -> { }
        }
    }

    /** Rei Slime: golpe forte faz uma gosma-clone se soltar e explodir em ácido em volta. */
    private static void mitosis(ServerPlayer sp, float amount) {
        if (amount < sp.getMaxHealth() * 0.08F) return;
        long now = sp.level().getGameTime();
        Long ready = MITOSIS_READY.get(sp.getUUID());
        if (ready != null && now < ready) return;
        if (sp.getRandom().nextFloat() > 0.5F) return;
        MITOSIS_READY.put(sp.getUUID(), now + MITOSIS_GAP);
        ServerLevel level = sp.serverLevel();
        Vec3 at = sp.position().add((sp.getRandom().nextDouble() - 0.5) * 3, 0.3, (sp.getRandom().nextDouble() - 0.5) * 3);
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.0), e -> foe(sp, e))) {
            hit(sp, le, 6F);
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), sp);
            Statuses.apply(le, Statuses.Kind.EXPOSED, 1, 80, sp, 1F);
        }
        level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, 30, 0.6, 0.4, 0.6, 0.15);
        level.sendParticles(RaceFx.dust(Race.SLIME.rgb, 1.8F), at.x, at.y, at.z, 16, 0.8, 0.3, 0.8, 0);
        RaceFx.sound(level, at, SoundEvents.SLIME_SQUISH_SMALL, 1.0F, 0.8F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.mitosis").withColor(Race.SLIME.rgb), true);
    }

    // =====================================================================================
    //  Ajudantes
    // =====================================================================================
    /** Inimigo para as auras/golpes da forma: nunca jogadores aliados, nem bichos do jogador, nem neutros quietos. */
    static boolean foe(ServerPlayer sp, Entity e) {
        if (!(e instanceof LivingEntity le) || e == sp || !le.isAlive() || le.isSpectator() || e instanceof ArmorStand) return false;
        Boolean social = Party.allyOverride(sp, e);
        if (social != null) return !social;
        if (e instanceof Player) return false;
        if (e instanceof OwnableEntity o && sp.getUUID().equals(o.getOwnerUUID())) return false;
        if (e instanceof Enemy) return true;
        return e instanceof Mob m && m.getTarget() == sp;
    }

    private static List<LivingEntity> foesAround(ServerPlayer sp, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity le : sp.serverLevel().getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(r), e -> foe(sp, e))) {
            if (le.distanceToSqr(sp) <= r * r) out.add(le);
            if (out.size() >= 32) break; // orçamento
        }
        return out;
    }

    /** Inimigos à frente (cosseno mínimo {@code cos}) e à vista, do mais perto ao mais longe. */
    private static List<LivingEntity> foesInCone(ServerPlayer sp, double range, double cos) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity le : foesAround(sp, range)) {
            Vec3 to = RaceFx.center(le).subtract(eye);
            double len = to.length();
            if (len > 1.5 && to.scale(1 / len).dot(look) < cos) continue;
            BlockHitResult wall = sp.level().clip(new ClipContext(eye, RaceFx.center(le), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
            if (wall.getType() == HitResult.Type.BLOCK) continue;
            out.add(le);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
        return out;
    }

    /** Inimigo mais alinhado com o olhar. */
    @Nullable
    private static LivingEntity aimed(ServerPlayer sp, double range, double cos) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        LivingEntity best = null;
        double bestDot = cos;
        for (LivingEntity le : foesAround(sp, range)) {
            Vec3 to = RaceFx.center(le).subtract(eye).normalize();
            double dot = to.dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = le;
            }
        }
        return best;
    }

    /** Dano mágico da forma: base × DAMAGE_SCALE × Poder Mágico (o mesmo escalonamento das magias). */
    static float scaled(ServerPlayer sp, float base) {
        double power = sp.getAttributeValue(CoreRegistries.SPELL_POWER);
        return base * Progression.DAMAGE_SCALE * (float) Math.max(0.5, power);
    }

    static boolean hit(ServerPlayer sp, LivingEntity target, float base) {
        DamageSource src = new DamageSource(sp.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(CoreRegistries.SPELL_DAMAGE), sp, sp);
        return target.hurt(src, scaled(sp, base));
    }

    private static void knock(ServerPlayer sp, LivingEntity le, double k) {
        Vec3 d = le.position().subtract(sp.position());
        d = new Vec3(d.x, 0, d.z);
        if (d.lengthSqr() < 1e-4) d = flat(sp.getLookAngle());
        d = d.normalize();
        le.push(d.x * k, 0.3, d.z * k);
        le.hurtMarked = true;
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
    }

    private static Vec3 rotateY(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    /** Cone de partículas (orçamento fixo). */
    private static void cone(ServerLevel level, Vec3 origin, Vec3 dir, double range, ParticleOptions a, ParticleOptions b) {
        for (double d = 0.8; d <= range; d += 0.8) {
            Vec3 p = origin.add(dir.scale(d));
            double r = 0.15 + d * 0.32;
            int n = 2 + (int) d;
            level.sendParticles(a, p.x, p.y, p.z, n, r, r * 0.6, r, 0.02);
            level.sendParticles(b, p.x, p.y, p.z, n, r, r * 0.6, r, 0.01);
        }
    }

    private static ParticleOptions signature(ServerPlayer sp, Race r) {
        return switch (r) {
            case ANGEL -> ParticleTypes.END_ROD;
            case DEMON -> ParticleTypes.SOUL_FIRE_FLAME;
            case DRAGONBORN -> ElementFx.signature(CharacterContent.data(sp).ancestry());
            case PHANTOM -> ParticleTypes.SOUL;
            case SLIME -> ParticleTypes.ITEM_SLIME;
            case ELF -> ParticleTypes.HAPPY_VILLAGER;
            case DWARF -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
            case HALF_ORC -> ParticleTypes.ANGRY_VILLAGER;
            case BEASTFOLK -> ParticleTypes.CRIT;
            case HUMAN -> ParticleTypes.TOTEM_OF_UNDYING;
            case ARACHNID -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState());
        };
    }

    private static SoundEvent formSound(Race r) {
        return switch (r) {
            case ANGEL -> SoundEvents.BEACON_ACTIVATE;
            case DEMON -> SoundEvents.WITHER_AMBIENT;
            case DRAGONBORN -> SoundEvents.ENDER_DRAGON_GROWL;
            case PHANTOM -> SoundEvents.PHANTOM_SWOOP;
            case SLIME -> SoundEvents.SLIME_SQUISH;
            case ELF -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case DWARF -> SoundEvents.ANVIL_LAND;
            case HALF_ORC -> SoundEvents.RAVAGER_ROAR;
            case BEASTFOLK -> SoundEvents.WOLF_HOWL;
            case HUMAN -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case ARACHNID -> SoundEvents.SPIDER_AMBIENT;
        };
    }

    /** Ticks que faltam na forma (0 = sem forma). */
    public static int remaining(LivingEntity e) {
        Race r = formOf(e);
        if (r == null) return 0;
        MobEffectInstance inst = e.getEffect(CharacterContent.form(r));
        return inst == null ? 0 : inst.getDuration();
    }

    private RaceForms() {}
}
