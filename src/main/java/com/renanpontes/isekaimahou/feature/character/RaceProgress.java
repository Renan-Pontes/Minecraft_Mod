package com.renanpontes.isekaimahou.feature.character;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * <b>Níveis de raça</b> (I–V) e os <b>feitos raciais</b> (v1.0.1 — substitui os desbloqueios secretos da v1.0).
 * <p>
 * Igual para todas as raças: o nível de raça sobe com o nível do personagem ({@link #CHAR_LEVEL} = 1/15/35/60/90) e o
 * nível V também exige o FEITO da raça ({@link #goal}): cumprido o feito no nível 90, a <b>Transformação</b>
 * ({@link RaceForms}) desperta. O nível II desperta a 2ª skill racial ({@link RaceGlyphs#SECOND_SKILL_LEVEL}); II–IV
 * fortalecem a passiva ({@link RacePassives}). Os feitos contam sempre (qualquer raça), então quem renasce leva o
 * progresso que já fez.
 * <ul>
 *   <li>Humano: vencer 400 monstros · Elfo: 150 abates à distância · Anão: minerar 1000 blocos abaixo de y 0 ·
 *       Meio-Orc: 250 abates corpo a corpo · Demônio: 200 abates com fogo ou no Nether · Dracônico: 250 abates com
 *       magia · Bestial: domar 10 animais · Aracnídeo: 200 abates no subterrâneo · Fantasma: 200 abates à noite ·
 *       Anjo: purificar 150 mortos-vivos · Slime: devorar 150 coisas (comidas e, v1.7, criaturas que o Predador copia —
 *       o contador antigo de comidas continua valendo).</li>
 * </ul>
 * v1.7: as raças que evoluem ({@link Race#hasStages()}) anunciam o novo estágio junto com o nível de raça.
 */
public final class RaceProgress {
    public static final int MAX_LEVEL = 5;
    /** Nível de personagem exigido para cada nível de raça (I..V). */
    public static final int[] CHAR_LEVEL = {1, 15, 35, 60, 90};

    static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RaceProgress::onKill);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RaceProgress::onBreak); // depois de quem cancela a quebra
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RaceProgress::onTame);
        NeoForge.EVENT_BUS.addListener(RaceProgress::onEat);
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.LevelUp e) -> {
            CharacterContent.data(e.getPlayer()).dirty = true; // a passiva cresce com o nível
            check(e.getPlayer());
        });
    }

    // =====================================================================================
    //  Consultas (lado comum: o cliente passa o nível do ClientMagic)
    // =====================================================================================
    /** Meta do feito da raça. */
    public static int goal(Race r) {
        return switch (r) {
            case HUMAN -> 400;
            case ELF -> 150;
            case DWARF -> 1000;
            case HALF_ORC -> 250;
            case DEMON -> 200;
            case DRAGONBORN -> 250;
            case BEASTFOLK -> 10;
            case ARACHNID -> 200;
            case PHANTOM -> 200;
            case ANGEL -> 150;
            case SLIME -> 150;
        };
    }

    /** Progresso atual do feito. */
    public static int featCount(CharacterData d, Race r) {
        return switch (r) {
            case DWARF -> d.deepMined();
            case BEASTFOLK -> d.tames();
            default -> d.feat(r);
        };
    }

    public static boolean featDone(CharacterData d, Race r) {
        return featCount(d, r) >= goal(r);
    }

    public static float featProgress(CharacterData d, Race r) {
        return Math.min(1F, featCount(d, r) / (float) goal(r));
    }

    /** Nível de raça (1..5) da raça {@code r} para quem tem o nível de personagem {@code charLevel}. */
    public static int level(Race r, CharacterData d, int charLevel) {
        int lv = 1;
        for (int i = 1; i < CHAR_LEVEL.length; i++) {
            if (charLevel >= CHAR_LEVEL[i]) lv = i + 1;
        }
        if (lv >= MAX_LEVEL && !featDone(d, r)) lv = MAX_LEVEL - 1;
        return lv;
    }

    /** Nível de raça do jogador no servidor (0 = sem raça escolhida). */
    public static int level(Player p) {
        CharacterData d = CharacterContent.data(p);
        if (!d.created()) return 0;
        return level(d.race(), d, MagicApi.get(p).level());
    }

    /** Nível do personagem pedido para o nível de raça {@code lv} (1..5). */
    public static int charLevelFor(int lv) {
        return CHAR_LEVEL[Math.max(1, Math.min(MAX_LEVEL, lv)) - 1];
    }

    /** "I".."V". */
    public static String roman(int lv) {
        return switch (lv) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> "—";
        };
    }

    // =====================================================================================
    //  Checagem e aviso (servidor)
    // =====================================================================================
    /** Alinha o nível anunciado com o atual SEM aviso (escolher/trocar de raça, save antigo). */
    public static void sync(ServerPlayer sp) {
        CharacterData d = CharacterContent.data(sp);
        d.setAnnouncedLevel(d.created() ? level(sp) : -1);
    }

    /** Anuncia um nível de raça novo por vez (o próximo sai na checagem seguinte). */
    public static void check(ServerPlayer sp) {
        if (sp instanceof FakePlayer) return;
        CharacterData d = CharacterContent.data(sp);
        if (!d.created()) return;
        int lv = level(sp);
        if (d.announcedLevel() < 0 || d.announcedLevel() > lv) {
            d.setAnnouncedLevel(lv);
            return;
        }
        if (lv <= d.announcedLevel() || Onboarding.quiet(sp)) return;
        int next = d.announcedLevel() + 1;
        d.setAnnouncedLevel(next);
        announce(sp, d.race(), next);
    }

    static void announce(ServerPlayer sp, Race r, int lv) {
        PacketDistributor.sendToPlayer(sp, new CharacterNetwork.LevelUp(r.ordinal(), lv));
        CharacterService.sendLook(sp);
        ServerLevel level = sp.serverLevel();
        RaceFx.dustBurst(level, sp.position().add(0, 1.0, 0), r.rgb, 40, 0.6);
        level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.2, sp.getZ(), 30, 0.5, 1.0, 0.5, 0.06);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 0.8F);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), raceSound(r), SoundSource.PLAYERS, 0.7F, 1.0F);
        Component lvText = Component.literal(roman(lv)).withColor(0xFFD86B);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.character.race_level", r.displayName(), lvText).withColor(0xFFD86B));
        if (r.hasStages()) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.character.evolved", r.stageName(lv).withColor(r.rgb))
                    .withColor(0xE8E2F4));
        }
        if (lv >= MAX_LEVEL) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 1.0, sp.getZ(), 60, 0.6, 1.0, 0.6, 0.4);
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.4F, 1.3F);
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.character.form_awakened", r.formName().withColor(r.rgb),
                    Component.keybind("key.isekai_mahou.racial_ability")).withColor(0xE8E2F4));
            Rewards.toast(sp, Component.translatable("toast.isekai_mahou.character.form"), r.formName().withColor(r.rgb),
                    new ItemStack(CharacterContent.relic(r).get()), r.rgb);
        } else {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.character.race_level_gain", r.passiveLevel(lv))
                    .withColor(0xE8E2F4));
            Rewards.toast(sp, Component.translatable("toast.isekai_mahou.character.race_level"),
                    r.displayName().append(Component.literal(" " + roman(lv))), new ItemStack(CharacterContent.ALTAR_ITEM.get()), r.rgb);
        }
    }

    private static SoundEvent raceSound(Race r) {
        return switch (r) {
            case ELF -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case DWARF -> SoundEvents.ANVIL_USE;
            case HALF_ORC -> SoundEvents.RAVAGER_ROAR;
            case DEMON -> SoundEvents.WITHER_AMBIENT;
            case DRAGONBORN -> SoundEvents.ENDER_DRAGON_GROWL;
            case BEASTFOLK -> SoundEvents.WOLF_HOWL;
            case ARACHNID -> SoundEvents.SPIDER_AMBIENT;
            case PHANTOM -> SoundEvents.PHANTOM_AMBIENT;
            case ANGEL -> SoundEvents.BEACON_POWER_SELECT;
            case SLIME -> SoundEvents.SLIME_JUMP;
            default -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
        };
    }

    // =====================================================================================
    //  Contadores dos feitos
    // =====================================================================================
    private static void bump(ServerPlayer sp, CharacterData d, Race r) {
        int before = featCount(d, r);
        int goal = goal(r);
        if (before >= goal) return; // já cumprido: não precisa contar mais
        if (r == Race.DWARF) d.addDeepMined(1);
        else if (r == Race.BEASTFOLK) d.addTame();
        else d.addFeat(r, 1);
        int now = featCount(d, r);
        if (now % 10 == 0 || r == Race.BEASTFOLK) d.dirty = true; // a barrinha anda sem mandar pacote a cada abate
        if (now >= goal) {
            d.dirty = true;
            if (d.is(r)) {
                sp.sendSystemMessage(Component.translatable("message.isekai_mahou.character.feat_done", r.displayName(),
                        r.featDesc(goal)).withColor(0xFFD86B));
                sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                        SoundSource.PLAYERS, 0.6F, 1.1F);
            }
            check(sp);
        }
    }

    private static void onKill(LivingDeathEvent e) {
        if (e.isCanceled()) return;
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        LivingEntity victim = e.getEntity();
        if (victim instanceof Player || victim == sp) return;
        CharacterData d = CharacterContent.data(sp);
        Entity direct = src.getDirectEntity();
        boolean spell = src.is(CoreRegistries.SPELL_DAMAGE);
        Level level = victim.level();
        if (victim instanceof Enemy) bump(sp, d, Race.HUMAN);
        if (direct instanceof Projectile || (spell && direct != null && direct != sp)) bump(sp, d, Race.ELF);
        if (direct == sp && !spell && !src.is(DamageTypeTags.IS_PROJECTILE)) bump(sp, d, Race.HALF_ORC);
        if (victim.isOnFire() || src.is(DamageTypeTags.IS_FIRE) || level.dimension() == Level.NETHER) bump(sp, d, Race.DEMON);
        if (spell) bump(sp, d, Race.DRAGONBORN);
        if (!level.canSeeSky(victim.blockPosition().above())) bump(sp, d, Race.ARACHNID);
        if (!level.dimensionType().hasSkyLight() || level.isNight()) bump(sp, d, Race.PHANTOM);
        if (victim.getType().is(EntityTypeTags.UNDEAD)) bump(sp, d, Race.ANGEL);
        if (SlimePredator.Mimic.of(victim) != null) bump(sp, d, Race.SLIME);
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (e.isCanceled() || !(e.getPlayer() instanceof ServerPlayer sp) || sp instanceof FakePlayer || sp.isCreative()) return;
        if (e.getPos().getY() >= 0 || !e.getState().is(BlockTags.MINEABLE_WITH_PICKAXE)) return;
        CharacterData d = CharacterContent.data(sp);
        bump(sp, d, Race.DWARF);
    }

    private static void onTame(AnimalTameEvent e) {
        if (e.isCanceled() || !(e.getTamer() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        bump(sp, CharacterContent.data(sp), Race.BEASTFOLK);
    }

    private static void onEat(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        ItemStack it = e.getItem();
        if (it.isEmpty() || it.getFoodProperties(sp) == null) return;
        bump(sp, CharacterContent.data(sp), Race.SLIME);
    }

    /** Cumpre o feito da raça já (comando de op / testes). */
    static void completeFeat(ServerPlayer sp, Race r) {
        CharacterData d = CharacterContent.data(sp);
        switch (r) {
            case DWARF -> d.setDeepMined(goal(r));
            case BEASTFOLK -> d.setTames(goal(r));
            default -> d.setFeat(r, goal(r));
        }
        check(sp);
    }

    /** Linha de depuração para o /mahou character info. */
    static String debug(ServerPlayer sp) {
        CharacterData d = CharacterContent.data(sp);
        StringBuilder b = new StringBuilder("race_level=").append(level(sp)).append(" feats:");
        for (Race r : Race.ALL) b.append(' ').append(r.id).append('=').append(featCount(d, r)).append('/').append(goal(r));
        return b.toString();
    }

    private RaceProgress() {}
}
