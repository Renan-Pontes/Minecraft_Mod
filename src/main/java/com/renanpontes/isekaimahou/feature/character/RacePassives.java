package com.renanpontes.isekaimahou.feature.character;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicHover;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.LightLayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Passivas das raças (v1.0.1: começam FRACAS e crescem com o nível de raça I–IV, {@link RaceProgress}), os traços e o
 * comportamento extra dos efeitos das skills raciais. Os números (índice = nível de raça − 1, o V usa os do IV):
 * <ul>
 *   <li><b>Humano — Ambição Humana:</b> +10/12/15/20% de XP por abate; do II: −15/20/25% do MESMO tipo de dano já
 *       levado nos últimos 8 s (até 3 tipos); do III: +5/10% de Poder Mágico.</li>
 *   <li><b>Elfo — Sangue Élfico:</b> visão no escuro; do II: +8/12/15% de dano com projéteis e de regeneração de recurso.</li>
 *   <li><b>Anão — Robustez Anã:</b> visão nas profundezas, +10/15/20/25% de mineração; do II: +2/3/4 de armadura; do III:
 *       imune a veneno, +15/25% de firmeza.</li>
 *   <li><b>Meio-Orc — Faminto (v1.7, Geld):</b> +3/5/6/8% de dano corpo a corpo + os acúmulos de Fome Voraz
 *       ({@link RaceKits}); do II: +4/6/8 de vida; do III: Implacável (1 de vida em vez de morrer, 1× a cada 5 min) e
 *       Sangue Quente (+20% abaixo de 35% de vida).</li>
 *   <li><b>Demônio — Sangue Infernal:</b> visão no escuro, −20/35/50/50% de dano de fogo; do II: +8/12/15% com magias de
 *       fogo; do III: Repreensão Infernal e −15/25% de magias de fogo.</li>
 *   <li><b>Dracônico — Linhagem Dracônica:</b> −15/15/25/25% de dano de fogo e +1/1/2/2 de queda segura; do II: +8/12/15%
 *       com magias da ancestralidade e −15/25/25% delas; do IV: +2 de armadura.</li>
 *   <li><b>Bestial — Sentidos Bestiais:</b> +4/6/8/10% de velocidade e +1/2/3/4 de queda segura; do III: 10/12% de esquiva.</li>
 *   <li><b>Aracnídeo — Sangue Aracnídeo:</b> visão no escuro e escalar paredes (devagar → rápido, no cliente: {@code
 *       CharacterClient}); do II: veneno nos golpes corpo a corpo; do IV: +5% de velocidade.</li>
 *   <li><b>Fantasma — Corpo Etéreo:</b> visão no escuro, cai devagar (−15/25/30/35% de gravidade, +2/4/5/6 de queda
 *       segura); do III: 10/15% de chance de golpes e projéteis o atravessarem.</li>
 *   <li><b>Anjo — Graça Celestial:</b> regenera +1 de vida a cada 5/4/3 s (+2 no IV); do II: +10/20/25% de dano em
 *       mortos-vivos; do IV: cai mais devagar.</li>
 *   <li><b>Slime — Predador (v1.7, Rimuru):</b> regenera +1 de vida a cada 5/4/3 s (+2 no IV); do II: −25/50/75% de dano
 *       de queda (e quica, no cliente); do III: quem te golpeia gruda (Lentidão); do IV: +2 de armadura e +20% de firmeza.
 *       Mais as habilidades copiadas ({@link SlimePredator}): lobo = velocidade, morcego = visão no escuro, blaze = −40%
 *       de fogo, zumbi = imune a Fome/Veneno, aranha = escalar (aqui e no cliente).</li>
 * </ul>
 * v1.7 (raças de anime): o Aracnídeo (Kumoko) ganha Fio de Aranha, Armadilha e Olho Maligno ({@link KumoThreads}); o
 * Dracônico (Dragon Slayer) devora o elemento; o Demônio (Anos) desfaz magias; o Elfo (Frieren) esconde a mana
 * ({@link RaceKits}).
 * Modificadores de atributo permanentes com ids próprios ({@code isekai_mahou:character.race.*}), reaplicados ao subir
 * de nível. Jogadores da v0.9: os modificadores dos atributos antigos ({@code character.str.*} etc.) saem no login.
 */
public final class RacePassives {
    // ---- modificadores raciais ----
    private static final ResourceLocation RACE_SCALE = IsekaiMahou.id("character.race.scale");
    private static final ResourceLocation RACE_SPEED = IsekaiMahou.id("character.race.speed");
    private static final ResourceLocation RACE_FALL = IsekaiMahou.id("character.race.safe_fall");
    private static final ResourceLocation RACE_MINING = IsekaiMahou.id("character.race.mining");
    private static final ResourceLocation RACE_ARMOR = IsekaiMahou.id("character.race.armor");
    private static final ResourceLocation RACE_STEADY = IsekaiMahou.id("character.race.steady");
    private static final ResourceLocation RACE_HEALTH = IsekaiMahou.id("character.race.health");
    private static final ResourceLocation RACE_POWER = IsekaiMahou.id("character.race.spell_power");
    private static final ResourceLocation RACE_REGEN = IsekaiMahou.id("character.race.regen");
    private static final ResourceLocation RACE_MELEE = IsekaiMahou.id("character.race.melee");
    private static final ResourceLocation RACE_GRAVITY = IsekaiMahou.id("character.race.gravity");

    /** Modificadores dos atributos da v0.9 (Força, Destreza...) a limpar no login. */
    private static final List<ResourceLocation> LEGACY = List.of(
            IsekaiMahou.id("character.str.attack_damage"), IsekaiMahou.id("character.str.knockback"),
            IsekaiMahou.id("character.str.steady"), IsekaiMahou.id("character.dex.attack_speed"),
            IsekaiMahou.id("character.con.max_health"), IsekaiMahou.id("character.int.max_mana"),
            IsekaiMahou.id("character.int.spell_power"), IsekaiMahou.id("character.wis.mana_regen"));

    private static final int RELENTLESS_COOLDOWN = 5 * 60 * 20;
    private static final int REBUKE_GAP = 8 * 20;
    private static final int DODGE_GAP = 40, INSTINCT_DODGE_GAP = 30;
    private static final int ADAPT_TICKS = 160, ADAPT_TYPES = 3;
    /** Visão no escuro: duração do efeito (acima de 10 s o ícone não pisca) e quando renovar. */
    private static final int VISION_TICKS = 400, VISION_REFRESH = 300;

    private record Adapt(ResourceLocation type, long until) {}

    private static final Map<UUID, Long> LAST_REBUKE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_DODGE = new ConcurrentHashMap<>();
    private static final Map<UUID, Deque<Adapt>> ADAPT = new ConcurrentHashMap<>();
    /** Evita que o espinho das Escamas rebata outro espinho. */
    private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);

    static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, RacePassives::onDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RacePassives::onKill);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, RacePassives::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(RacePassives::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(RacePassives::onEffectApplicable);
        NeoForge.EVENT_BUS.addListener(RacePassives::onEffectRemoved);
        NeoForge.EVENT_BUS.addListener(RacePassives::onEffectExpired);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity().getUUID()));
    }

    static void forget(UUID id) {
        LAST_REBUKE.remove(id);
        LAST_DODGE.remove(id);
        ADAPT.remove(id);
    }

    /** Ao parar o servidor. */
    static void clearAll() {
        LAST_REBUKE.clear();
        LAST_DODGE.clear();
        ADAPT.clear();
    }

    /** Valor da tabela no nível de raça {@code lv} (1..5; o V usa o do IV). 0 sem raça. */
    static float at(int lv, float... byLevel) {
        if (lv <= 0) return 0F;
        return byLevel[Math.min(byLevel.length, Math.min(4, lv)) - 1];
    }

    // =====================================================================================
    //  Modificadores
    // =====================================================================================
    /** Reaplica os modificadores raciais (login, renascer, trocar de raça, subir de nível). */
    public static void apply(ServerPlayer sp, CharacterData d) {
        Race r = d.activeRace();
        int lv = r == null ? 0 : RaceProgress.level(sp);
        set(sp, Attributes.SCALE, RACE_SCALE, r == null ? 0 : r.scale, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        double speed = r == Race.BEASTFOLK ? at(lv, 0.04F, 0.06F, 0.08F, 0.10F) : r == Race.ARACHNID && lv >= 4 ? 0.05
                : SlimePredator.has(d, SlimePredator.Mimic.WOLF) ? 0.06 : 0;
        set(sp, Attributes.MOVEMENT_SPEED, RACE_SPEED, speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        double fall = r == Race.BEASTFOLK ? at(lv, 1, 2, 3, 4) : r == Race.PHANTOM ? at(lv, 2, 4, 5, 6)
                : r == Race.DRAGONBORN ? at(lv, 1, 1, 2, 2) : 0;
        set(sp, Attributes.SAFE_FALL_DISTANCE, RACE_FALL, fall, AttributeModifier.Operation.ADD_VALUE);
        double gravity = r == Race.PHANTOM ? -at(lv, 0.15F, 0.25F, 0.30F, 0.35F) : r == Race.ANGEL && lv >= 4 ? -0.15 : 0;
        set(sp, Attributes.GRAVITY, RACE_GRAVITY, gravity, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(sp, Attributes.BLOCK_BREAK_SPEED, RACE_MINING, r == Race.DWARF ? at(lv, 0.10F, 0.15F, 0.20F, 0.25F) : 0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        double armor = r == Race.DWARF ? at(lv, 0, 2, 3, 4) : (r == Race.DRAGONBORN || r == Race.SLIME) && lv >= 4 ? 2 : 0;
        set(sp, Attributes.ARMOR, RACE_ARMOR, armor, AttributeModifier.Operation.ADD_VALUE);
        double steady = r == Race.DWARF ? at(lv, 0, 0, 0.15F, 0.25F) : r == Race.SLIME && lv >= 4 ? 0.2 : 0;
        set(sp, Attributes.KNOCKBACK_RESISTANCE, RACE_STEADY, steady, AttributeModifier.Operation.ADD_VALUE);
        set(sp, Attributes.MAX_HEALTH, RACE_HEALTH, r == Race.HALF_ORC ? at(lv, 0, 4, 6, 8) : 0, AttributeModifier.Operation.ADD_VALUE);
        set(sp, Attributes.ATTACK_DAMAGE, RACE_MELEE, r == Race.HALF_ORC ? at(lv, 0.03F, 0.05F, 0.06F, 0.08F) : 0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(sp, CoreRegistries.SPELL_POWER, RACE_POWER, r == Race.HUMAN ? at(lv, 0, 0, 0.05F, 0.10F) : 0,
                AttributeModifier.Operation.ADD_VALUE);
        set(sp, CoreRegistries.MANA_REGEN, RACE_REGEN, r == Race.ELF ? at(lv, 0, 0.08F, 0.12F, 0.15F) : 0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (sp.getHealth() > sp.getMaxHealth()) sp.setHealth(sp.getMaxHealth());
    }

    /** Tira os modificadores dos atributos da v0.9 (Força, Destreza, Constituição...). */
    static void clearLegacy(ServerPlayer sp) {
        for (Holder<Attribute> a : List.of(Attributes.ATTACK_DAMAGE, Attributes.ATTACK_KNOCKBACK, Attributes.KNOCKBACK_RESISTANCE,
                Attributes.ATTACK_SPEED, Attributes.MAX_HEALTH, CoreRegistries.MAX_MANA, CoreRegistries.SPELL_POWER,
                CoreRegistries.MANA_REGEN)) {
            AttributeInstance inst = sp.getAttribute(a);
            if (inst == null) continue;
            for (ResourceLocation id : LEGACY) inst.removeModifier(id);
        }
        if (sp.getHealth() > sp.getMaxHealth()) sp.setHealth(sp.getMaxHealth());
    }

    private static void set(ServerPlayer sp, Holder<Attribute> attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = sp.getAttribute(attr);
        if (inst == null) return;
        if (Math.abs(amount) < 1e-6) {
            inst.removeModifier(id);
            return;
        }
        AttributeModifier cur = inst.getModifier(id);
        if (cur != null && Math.abs(cur.amount() - amount) < 1e-6 && cur.operation() == op) return;
        inst.addOrReplacePermanentModifier(new AttributeModifier(id, amount, op));
    }

    // =====================================================================================
    //  A cada segundo / a cada poucos ticks (chamado pelo CharacterService)
    // =====================================================================================
    /** Regeneração do Anjo e do Slime (e, v1.7, os kits de anime: {@link RaceKits#tickSecond}). */
    static void tickSecond(ServerPlayer sp, CharacterData d) {
        RaceKits.tickSecond(sp, d);
        Race r = d.activeRace();
        if (r != Race.ANGEL && r != Race.SLIME) return;
        if (!sp.isAlive() || sp.getHealth() >= sp.getMaxHealth()) return;
        int lv = RaceProgress.level(sp);
        int every = (int) at(lv, 5, 4, 3, 3);
        if ((sp.tickCount / 20) % every != 0) return;
        sp.heal(at(lv, 1, 1, 1, 2));
        if (sp.getRandom().nextInt(2) == 0) {
            sp.serverLevel().sendParticles(r == Race.ANGEL ? RaceFx.dust(0xFFF2B0, 0.8F) : ParticleTypes.ITEM_SLIME,
                    sp.getX(), sp.getY() + 1.0, sp.getZ(), 3, 0.3, 0.4, 0.3, 0.01);
        }
    }

    /** Aracnídeo (ou Slime com o Fio Pegajoso) encostado na parede: não acumula queda e não é expulso por "voar". */
    static void tickWall(ServerPlayer sp, CharacterData d) {
        if (!(d.is(Race.ARACHNID) || SlimePredator.has(d, SlimePredator.Mimic.SPIDER)) || sp.onGround()) return;
        if (!touchingWall(sp)) return;
        sp.resetFallDistance();
        MagicHover.allow(sp, 40);
    }

    public static boolean touchingWall(Player p) {
        return !p.level().noCollision(p, p.getBoundingBox().inflate(0.08, -0.05, 0.08));
    }

    // =====================================================================================
    //  Dano
    // =====================================================================================
    private static CharacterData raceData(Entity e) {
        if (!(e instanceof ServerPlayer sp) || sp instanceof FakePlayer) return null;
        CharacterData d = CharacterContent.data(sp);
        return d.created() ? d : null;
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide()) return;
        DamageSource src = e.getSource();
        Entity attacker = src.getEntity();
        boolean melee = attacker instanceof LivingEntity && attacker == src.getDirectEntity() && attacker != victim;

        // ---------- quem bate ----------
        CharacterData ad = raceData(attacker);
        if (ad != null && attacker != victim) {
            int lv = RaceProgress.level((Player) attacker);
            switch (ad.race()) {
                case ELF -> {
                    if (src.getDirectEntity() instanceof Projectile && !src.is(CoreRegistries.SPELL_DAMAGE)) {
                        e.setAmount(e.getAmount() * (1F + at(lv, 0F, 0.08F, 0.12F, 0.15F)));
                    }
                }
                case HALF_ORC -> {
                    LivingEntity orc = (LivingEntity) attacker;
                    if (melee && lv >= 3 && orc.getHealth() < orc.getMaxHealth() * 0.35F) e.setAmount(e.getAmount() * 1.2F);
                }
                case ARACHNID -> {
                    if (melee && lv >= 2 && victim.isAlive()) {
                        victim.addEffect(new MobEffectInstance(MobEffects.POISON, lv >= 3 ? 60 : 40, lv >= 4 ? 1 : 0), attacker);
                    }
                }
                case ANGEL -> {
                    if (!src.is(CoreRegistries.SPELL_DAMAGE) && victim.getType().is(EntityTypeTags.UNDEAD)) {
                        e.setAmount(e.getAmount() * (1F + at(lv, 0F, 0.10F, 0.20F, 0.25F)));
                    }
                }
                default -> { }
            }
        }
        if (melee && attacker instanceof LivingEntity la && la.hasEffect(CharacterContent.BLOOD_FURY) && e.getAmount() > 0) {
            la.heal(e.getAmount() * 0.15F);
            ((ServerLevel) victim.level()).sendParticles(RaceFx.dust(0xB01E28, 1.0F), victim.getX(), victim.getY() + victim.getBbHeight() * 0.6,
                    victim.getZ(), 4, 0.2, 0.2, 0.2, 0);
        }

        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        long now = victim.level().getGameTime();

        // ---------- efeitos das skills (qualquer criatura) ----------
        if (victim.hasEffect(CharacterContent.STONE_SKIN)) e.setAmount(e.getAmount() * 0.85F);
        if (victim.hasEffect(CharacterContent.DRAGON_SCALES)) {
            if (src.is(CoreRegistries.SPELL_DAMAGE)) e.setAmount(e.getAmount() * 0.75F);
            if (melee && !REFLECTING.get() && e.getAmount() > 0) {
                REFLECTING.set(true);
                try {
                    attacker.hurt(victim.damageSources().thorns(victim), e.getAmount() * 0.2F);
                } finally {
                    REFLECTING.set(false);
                }
            }
        }
        boolean dodgeable = attacker != null && attacker != victim && !src.is(DamageTypeTags.IS_EXPLOSION)
                && !src.is(DamageTypeTags.IS_FIRE) && !src.is(CoreRegistries.SPELL_DAMAGE);
        if (dodgeable && victim.hasEffect(CharacterContent.WILD_INSTINCT) && tryDodge(victim, now, INSTINCT_DODGE_GAP, 0.20F, Race.BEASTFOLK)) {
            e.setCanceled(true);
            return;
        }

        // ---------- passivas de quem apanha ----------
        CharacterData vd = raceData(victim);
        if (vd == null) return;
        ServerPlayer sp = (ServerPlayer) victim;
        int lv = RaceProgress.level(sp);
        switch (vd.race()) {
            case HUMAN -> {
                if (lv >= 2) adapt(sp, e, now, at(lv, 1F, 0.85F, 0.80F, 0.75F));
            }
            case DEMON -> {
                if (src.is(DamageTypeTags.IS_FIRE)) {
                    e.setAmount(e.getAmount() * (1F - at(lv, 0.20F, 0.35F, 0.50F, 0.50F)));
                } else if (melee && lv >= 3) {
                    rebuke(sp, (LivingEntity) attacker, now);
                }
            }
            case DRAGONBORN -> {
                if (src.is(DamageTypeTags.IS_FIRE)) e.setAmount(e.getAmount() * (1F - at(lv, 0.15F, 0.15F, 0.25F, 0.25F)));
            }
            case BEASTFOLK -> {
                if (lv >= 3 && dodgeable && tryDodge(sp, now, DODGE_GAP, at(lv, 0, 0, 0.10F, 0.12F), Race.BEASTFOLK)) e.setCanceled(true);
            }
            case PHANTOM -> {
                if (lv >= 3 && dodgeable && tryDodge(sp, now, DODGE_GAP, at(lv, 0, 0, 0.10F, 0.15F), Race.PHANTOM)) e.setCanceled(true);
            }
            case ARACHNID -> {
                // Pendurado no Fio de Aranha: a queda logo depois do puxão não machuca
                if (src.is(DamageTypeTags.IS_FALL) && KumoThreads.safeFall(sp)) e.setCanceled(true);
            }
            case SLIME -> {
                if (src.is(DamageTypeTags.IS_FIRE) && SlimePredator.has(vd, SlimePredator.Mimic.BLAZE)) {
                    e.setAmount(e.getAmount() * 0.6F);
                }
                if (src.is(DamageTypeTags.IS_FALL)) {
                    e.setAmount(e.getAmount() * (1F - at(lv, 0F, 0.25F, 0.50F, 0.75F)));
                } else if (melee && lv >= 3 && attacker instanceof LivingEntity la && la.isAlive()) {
                    la.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, true, true), sp);
                    sp.serverLevel().sendParticles(ParticleTypes.ITEM_SLIME, la.getX(), la.getY() + la.getBbHeight() * 0.5, la.getZ(),
                            6, 0.3, 0.3, 0.3, 0.02);
                }
            }
            default -> { }
        }
    }

    /** Humano: menos dano do mesmo tipo que já levou nos últimos 8 s; registra o tipo atual. */
    private static void adapt(ServerPlayer sp, LivingIncomingDamageEvent e, long now, float mult) {
        ResourceLocation type = e.getSource().typeHolder().unwrapKey().map(k -> k.location()).orElse(null);
        if (type == null) return;
        Deque<Adapt> list = ADAPT.computeIfAbsent(sp.getUUID(), k -> new ArrayDeque<>(ADAPT_TYPES + 1));
        list.removeIf(a -> a.until() < now);
        boolean adapted = false;
        for (Adapt a : list) {
            if (a.type().equals(type)) {
                adapted = true;
                break;
            }
        }
        if (adapted) {
            e.setAmount(e.getAmount() * mult);
            if (sp.getRandom().nextInt(3) == 0) {
                sp.serverLevel().sendParticles(RaceFx.dust(0xE8C77A, 0.8F), sp.getX(), sp.getY() + 1.0, sp.getZ(), 5, 0.3, 0.4, 0.3, 0);
            }
        }
        list.removeIf(a -> a.type().equals(type));
        list.addLast(new Adapt(type, now + ADAPT_TICKS));
        while (list.size() > ADAPT_TYPES) list.removeFirst();
    }

    /** Esquiva (Bestial) ou "atravessar" (Fantasma): no máx. 1 a cada {@code gap} ticks. */
    static boolean tryDodge(LivingEntity v, long now, int gap, float chance, Race style) {
        if (chance <= 0F) return false;
        Long last = LAST_DODGE.get(v.getUUID());
        if (last != null && now - last < gap) return false;
        if (v.getRandom().nextFloat() >= chance) return false;
        LAST_DODGE.put(v.getUUID(), now);
        ServerLevel level = (ServerLevel) v.level();
        if (style == Race.PHANTOM) {
            level.sendParticles(ParticleTypes.SOUL, v.getX(), v.getY() + 0.9, v.getZ(), 8, 0.35, 0.5, 0.35, 0.02);
            level.sendParticles(RaceFx.dust(Race.PHANTOM.rgb, 1.0F), v.getX(), v.getY() + 0.9, v.getZ(), 10, 0.4, 0.6, 0.4, 0);
            level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 0.7F, 0.6F);
        } else {
            level.sendParticles(ParticleTypes.CLOUD, v.getX(), v.getY() + 0.9, v.getZ(), 10, 0.35, 0.5, 0.35, 0.03);
            level.sendParticles(RaceFx.dust(style.rgb, 0.9F), v.getX(), v.getY() + 0.9, v.getZ(), 8, 0.4, 0.5, 0.4, 0);
            level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.9F, 1.5F);
        }
        if (v instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable(style == Race.PHANTOM ? "message.isekai_mahou.character.phase"
                    : "message.isekai_mahou.character.dodge").withColor(style.rgb), true);
        }
        return true;
    }

    /** Demônio — Repreensão Infernal: quem bate corpo a corpo pega fogo (no máx. 1 vez a cada 8 s). */
    private static void rebuke(ServerPlayer sp, LivingEntity le, long now) {
        if (!le.isAlive()) return;
        Long last = LAST_REBUKE.get(sp.getUUID());
        if (last != null && now - last < REBUKE_GAP) return;
        LAST_REBUKE.put(sp.getUUID(), now);
        le.igniteForSeconds(3F);
        sp.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 14, 0.3, 0.4, 0.3, 0.03);
        sp.serverLevel().playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 0.8F);
    }

    /** Magias: resistências e bônus raciais + o aumento Adaptável (Humano). */
    private static void onSpellDamage(SpellDamageEvent.Pre e) {
        // ---------- quem conjura ----------
        int adaptive = e.stats().custom(RaceGlyphs.ADAPTIVE) + e.ctx().formStats().custom(RaceGlyphs.ADAPTIVE);
        if (adaptive > 0) {
            boolean afflicted = false;
            for (Statuses.Kind k : Statuses.Kind.values()) {
                if (Statuses.has(e.target(), k)) {
                    afflicted = true;
                    break;
                }
            }
            if (!afflicted) {
                for (MobEffectInstance i : e.target().getActiveEffects()) {
                    if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                        afflicted = true;
                        break;
                    }
                }
            }
            e.setAmount(e.getAmount() * (1F + adaptive * (afflicted ? 0.5F : 0.25F)));
            if (afflicted) {
                ((ServerLevel) e.target().level()).sendParticles(RaceFx.dust(0xE8C77A, 0.9F), e.target().getX(),
                        e.target().getY() + e.target().getBbHeight() * 0.7, e.target().getZ(), 6, 0.25, 0.25, 0.25, 0);
            }
        }
        ServerPlayer caster = e.ctx().casterPlayer();
        CharacterData cd = caster == null ? null : raceData(caster);
        if (cd != null) {
            int lv = RaceProgress.level(caster);
            float bonus = at(lv, 0F, 0.08F, 0.12F, 0.15F);
            switch (cd.race()) {
                case DRAGONBORN -> {
                    // v1.7 Dragon Slayer: Saciado (comeu o elemento há pouco) = +15%
                    if (e.element().matches(cd.ancestry())) {
                        e.setAmount(e.getAmount() * (1F + bonus + (RaceKits.fed(caster) ? 0.15F : 0F)));
                    }
                }
                case DEMON -> {
                    if (e.element().matches(Element.FIRE)) e.setAmount(e.getAmount() * (1F + bonus));
                }
                case ELF -> {
                    String form = e.ctx().spell().form().id().getPath();
                    if (form.equals("projectile") || form.equals("arrow") || form.equals("race_moon_arrow")) {
                        e.setAmount(e.getAmount() * (1F + bonus));
                    }
                }
                case ANGEL -> {
                    if (e.target().getType().is(EntityTypeTags.UNDEAD)) e.setAmount(e.getAmount() * (1F + at(lv, 0F, 0.10F, 0.20F, 0.25F)));
                }
                default -> { }
            }
        }
        // ---------- quem recebe ----------
        CharacterData td = raceData(e.target());
        if (td == null) return;
        int lv = RaceProgress.level((Player) e.target());
        if (td.race() == Race.DEMON) {
            // v1.7 Anos: Olhos Mágicos da Destruição (nível IV) — às vezes a magia some antes de acertar
            RaceKits.onSpellAtDemon(e, (ServerPlayer) e.target(), lv);
            if (e.isCanceled()) return;
        }
        if (td.race() == Race.DRAGONBORN && lv >= 2 && e.element().matches(td.ancestry())) {
            e.setAmount(e.getAmount() * (1F - at(lv, 0F, 0.15F, 0.25F, 0.25F)));
        } else if (td.race() == Race.DEMON && lv >= 3 && e.element().matches(Element.FIRE)) {
            e.setAmount(e.getAmount() * (1F - at(lv, 0F, 0F, 0.15F, 0.25F)));
        }
    }

    // =====================================================================================
    //  Efeitos
    // =====================================================================================
    /** Anão (nível III+): imune a veneno. v1.7: Slime com a Resistência à Podridão (zumbi): imune a Veneno e Fome. */
    private static void onEffectApplicable(MobEffectEvent.Applicable e) {
        MobEffectInstance inst = e.getEffectInstance();
        if (inst == null || !(inst.is(MobEffects.POISON) || inst.is(MobEffects.HUNGER))) return;
        CharacterData d = raceData(e.getEntity());
        if (d == null) return;
        boolean immune = (d.race() == Race.DWARF && inst.is(MobEffects.POISON) && RaceProgress.level((Player) e.getEntity()) >= 3)
                || SlimePredator.has(d, SlimePredator.Mimic.ZOMBIE);
        if (immune) e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    /** As asas somem junto com o efeito (leite, Determinação inimiga, fim do tempo). */
    private static void onEffectRemoved(MobEffectEvent.Remove e) {
        if (e.getEffectInstance() != null && e.getEffectInstance().is(CharacterContent.SHADOW_WINGS)) {
            RaceFx.entityFx(e.getEntity(), CharacterNetwork.FX_WINGS, 0);
        }
    }

    private static void onEffectExpired(MobEffectEvent.Expired e) {
        if (e.getEffectInstance() != null && e.getEffectInstance().is(CharacterContent.SHADOW_WINGS)) {
            RaceFx.entityFx(e.getEntity(), CharacterNetwork.FX_WINGS, 0);
        }
    }

    /** Meio-Orc (nível III+) — Implacável: uma vez a cada 5 min, um golpe fatal deixa com 1 de vida. */
    private static void onDeath(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        CharacterData d = CharacterContent.data(sp);
        if (!d.is(Race.HALF_ORC) || RaceProgress.level(sp) < 3) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // /kill e o vazio
        long now = now(sp);
        if (now < d.relentlessReadyAt()) return;
        d.setRelentlessReadyAt(now + RELENTLESS_COOLDOWN);
        e.setCanceled(true);
        sp.setHealth(1F);
        sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 2, false, true, true));
        ServerLevel level = sp.serverLevel();
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, sp.getX(), sp.getY() + 1.6, sp.getZ(), 8, 0.4, 0.4, 0.4, 0);
        level.sendParticles(ParticleTypes.CRIT, sp.getX(), sp.getY() + 1.0, sp.getZ(), 30, 0.5, 0.7, 0.5, 0.3);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.PLAYERS, 1.0F, 0.8F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.relentless").withColor(Race.HALF_ORC.rgb), true);
    }

    /** Humano — Ambição: XP extra por abate. */
    private static void onKill(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getSource().getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        LivingEntity victim = e.getEntity();
        if (victim instanceof Player) return;
        CharacterData d = CharacterContent.data(sp);
        if (!d.is(Race.HUMAN)) return;
        int base = Progression.killXp(victim.getMaxHealth(), e.getSource().is(CoreRegistries.SPELL_DAMAGE));
        int bonus = Math.max(1, Math.round(base * at(RaceProgress.level(sp), 0.10F, 0.12F, 0.15F, 0.20F)));
        MagicApi.giveXp(sp, bonus, "race");
    }

    /** Relógio das recargas (tempo do Overworld). */
    static long now(Player p) {
        return p.level().getServer() != null ? p.level().getServer().overworld().getGameTime() : p.level().getGameTime();
    }

    // =====================================================================================
    //  Visão no escuro
    // =====================================================================================
    /** Shift + tecla racial (ou o botão da aba): liga/desliga a visão no escuro. */
    static void toggleVisionKey(ServerPlayer sp) {
        CharacterData d = CharacterContent.data(sp);
        if (!d.created()) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.not_created"), true);
            return;
        }
        Race race = d.race();
        if (race.darkvision == Race.Darkvision.NONE) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.skills_in_grimoire",
                    Component.keybind("key.isekai_mahou.menu")).withColor(race.rgb), true);
            return;
        }
        d.setDarkvision(!d.darkvision());
        if (!d.darkvision()) clearVision(sp);
        sp.displayClientMessage(Component.translatable(d.darkvision()
                ? "message.isekai_mahou.character.darkvision_on" : "message.isekai_mahou.character.darkvision_off").withColor(race.rgb), true);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.4F,
                d.darkvision() ? 1.6F : 0.9F);
    }

    /** A cada segundo: visão no escuro das raças que têm. */
    static void tickVision(ServerPlayer sp, CharacterData d) {
        Race race = d.activeRace();
        // v1.7: o Slime que copiou o Ultrassom do morcego também enxerga no escuro
        boolean sees = race != null && (race.darkvision != Race.Darkvision.NONE || SlimePredator.has(d, SlimePredator.Mimic.BAT));
        boolean wants = sees && d.darkvision() && inDark(sp, race);
        MobEffectInstance cur = sp.getEffect(MobEffects.NIGHT_VISION);
        boolean ours = cur != null && cur.isAmbient() && !cur.isVisible() && !cur.showIcon();
        if (wants) {
            if (cur == null || (ours && cur.getDuration() < VISION_REFRESH)) {
                sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, VISION_TICKS, 0, true, false, false));
            }
        } else if (ours && cur.getDuration() <= VISION_TICKS) {
            sp.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    static void clearVision(ServerPlayer sp) {
        MobEffectInstance cur = sp.getEffect(MobEffects.NIGHT_VISION);
        if (cur != null && cur.isAmbient() && !cur.isVisible() && !cur.showIcon()) sp.removeEffect(MobEffects.NIGHT_VISION);
    }

    private static boolean inDark(ServerPlayer sp, Race race) {
        BlockPos eye = BlockPos.containing(sp.getEyePosition());
        if (race.darkvision == Race.Darkvision.UNDERGROUND) {
            // Anão: embaixo da terra (sem céu e abaixo do nível do mar) ou em caverna escura
            return !sp.level().canSeeSky(eye) && (eye.getY() < sp.level().getSeaLevel() - 4
                    || sp.level().getBrightness(LightLayer.SKY, eye) < 4);
        }
        return sp.level().getMaxLocalRawBrightness(eye) < 8;
    }

    private RacePassives() {}
}
