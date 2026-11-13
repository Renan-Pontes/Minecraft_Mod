package com.renanpontes.isekaimahou.feature.depths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * <b>Afixos da Masmorra em Camadas</b> (v1.0): cada andar tem modificadores sorteados (1 nos andares 1–2, 2 nos
 * andares 3–4, 1 no ninho) que mudam a cada 7 dias do jogo. Valem para os monstros comuns do andar (nunca o chefe
 * nem invocações) e ficam gravados no próprio monstro. Também cuida do <b>cronômetro</b> da descida: começa ao
 * pisar no 1º andar e para quando o Verme-Rei morre; o melhor tempo fica no contador {@code depths.best_time}.
 * <p>Custo: uma busca de monstros por segundo, só para jogadores que estão dentro de um andar.</p>
 */
public final class DepthsAffixes {
    public enum Affix {
        /** +50% de vida. */
        FORTIFIED(0x8FB8E8),
        /** +25% de velocidade. */
        FRENZIED(0xF0C050),
        /** +35% de dano. */
        BRUTAL(0xE06A4A),
        /** Estoura ao morrer (fere quem está colado; não quebra blocos). */
        VOLATILE(0xFF8A3A),
        /** Cura-se com o dano que causa. */
        VAMPIRIC(0xC83A5A),
        /** Cada golpe rouba mana. */
        DRAINING(0x7A8CFF);

        public final String id = name().toLowerCase(Locale.ROOT);
        public final int color;

        Affix(int color) {
            this.color = color;
        }

        public MutableComponent label() {
            return Component.translatable("affix.isekai_mahou.depths." + id).withColor(color);
        }

        public MutableComponent desc() {
            return Component.translatable("affix.isekai_mahou.depths." + id + ".desc");
        }
    }

    /** Lista de afixos gravada no monstro (ids separados por vírgula; vazio = já conferido, sem afixo). */
    public static final String TAG = "isekai_mahou.depths_affixes";
    public static final String COUNTER_BEST = "depths.best_time";
    public static final String COUNTER_CLEARS = "depths.clears";
    private static final long ROTATION_TICKS = 24000L * 7;

    /** Descida em andamento: quando começou e em qual masmorra. */
    private record Run(long startTick, long dungeon) {}

    private static final Map<UUID, Run> RUNS = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DepthsAffixes::onDeath);
        NeoForge.EVENT_BUS.addListener(DepthsAffixes::onDamage);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> RUNS.remove(e.getEntity().getUUID()));
    }

    // ------------------------------------------------------------------ sorteio

    /** Afixos do andar (1–4, 5 = ninho) da masmorra que começa no chunk {@code start}; mudam a cada 7 dias do jogo. */
    public static List<Affix> roll(ServerLevel level, ChunkPos start, int floor) {
        if (floor < 1) return List.of();
        int count = floor >= DepthsDungeon.LAIR ? 1 : (floor + 1) / 2;
        long epoch = level.getDayTime() / ROTATION_TICKS;
        Random r = new Random(level.getSeed() ^ start.toLong() * 0x9E3779B97F4A7C15L ^ (long) floor << 48 ^ epoch * 0xC2B2AE3D27D4EB4FL);
        List<Affix> pool = new ArrayList<>(List.of(Affix.values()));
        List<Affix> out = new ArrayList<>();
        for (int i = 0; i < count && !pool.isEmpty(); i++) out.add(pool.remove(r.nextInt(pool.size())));
        return out;
    }

    /** "Afixos: Fortificado · Volátil" (uma linha para a barra de ação). */
    public static Component line(List<Affix> affixes) {
        MutableComponent list = Component.empty();
        for (int i = 0; i < affixes.size(); i++) {
            if (i > 0) list.append(Component.literal(" · ").withColor(0x808080));
            list.append(affixes.get(i).label());
        }
        return Component.translatable("message.isekai_mahou.depths.affixes", list).withColor(0xC8B8A0);
    }

    // ------------------------------------------------------------------ aplicar nos monstros

    /** Marca (uma vez só) os monstros do andar que estão perto do jogador e dá a eles os afixos do andar. */
    public static void applyNear(ServerLevel level, ServerPlayer sp, BoundingBox piece, List<Affix> affixes) {
        AABB area = AABB.of(piece);
        AABB near = sp.getBoundingBox().inflate(28.0);
        if (!area.intersects(near)) return;
        for (Monster m : level.getEntitiesOfClass(Monster.class, area.intersect(near))) {
            CompoundTag data = m.getPersistentData();
            if (data.contains(TAG)) continue;
            if (BossKit.isBossLike(m) || (m instanceof OwnableEntity own && own.getOwnerUUID() != null)) {
                data.putString(TAG, "");
                continue;
            }
            apply(m, affixes);
        }
        if (!affixes.isEmpty()) Hints.show(sp, "depths_affixes");
    }

    private static void apply(Mob m, List<Affix> affixes) {
        StringBuilder ids = new StringBuilder();
        for (Affix a : affixes) {
            if (ids.length() > 0) ids.append(',');
            ids.append(a.id);
            switch (a) {
                case FORTIFIED -> {
                    float ratio = m.getHealth() / Math.max(1.0F, m.getMaxHealth());
                    modify(m, Attributes.MAX_HEALTH, a, 0.5);
                    m.setHealth(m.getMaxHealth() * ratio);
                }
                case FRENZIED -> modify(m, Attributes.MOVEMENT_SPEED, a, 0.25);
                case BRUTAL -> modify(m, Attributes.ATTACK_DAMAGE, a, 0.35);
                default -> { }
            }
        }
        m.getPersistentData().putString(TAG, ids.toString());
    }

    private static void modify(Mob m, Holder<Attribute> attribute, Affix a, double amount) {
        AttributeInstance inst = m.getAttribute(attribute);
        if (inst == null) return;
        inst.addOrReplacePermanentModifier(new AttributeModifier(IsekaiMahou.id("depths_affix_" + a.id), amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static boolean has(String tags, Affix a) {
        return !tags.isEmpty() && ("," + tags + ",").contains("," + a.id + ",");
    }

    // ------------------------------------------------------------------ efeitos dos afixos

    private static void onDamage(LivingDamageEvent.Post e) {
        if (!(e.getSource().getEntity() instanceof Mob attacker) || attacker.level().isClientSide || e.getNewDamage() <= 0.0F) return;
        String tags = attacker.getPersistentData().getString(TAG);
        if (tags.isEmpty()) return;
        ServerLevel sl = (ServerLevel) attacker.level();
        LivingEntity victim = e.getEntity();
        if (has(tags, Affix.VAMPIRIC) && attacker.isAlive()) {
            attacker.heal(1.0F + e.getNewDamage() * 0.5F);
            sl.sendParticles(ParticleTypes.CRIMSON_SPORE, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(),
                    6, 0.3, 0.3, 0.3, 0.0);
        }
        if (has(tags, Affix.DRAINING) && victim instanceof ServerPlayer sp) {
            MagicData d = MagicApi.get(sp);
            float take = Math.min(d.mana(), 6.0F + d.maxMana() * 0.03F);
            if (take > 0.0F && d.consumeMana(take)) {
                sl.sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.2, sp.getZ(), 10, 0.3, 0.4, 0.3, 0.3);
            }
        }
    }

    private static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(dead.level() instanceof ServerLevel sl)) return;
        if (dead.getType() == DepthsRegistries.WORM_KING.get()) {
            finishRuns(sl, dead);
            return;
        }
        String tags = dead.getPersistentData().getString(TAG);
        if (tags.isEmpty()) return;
        if (has(tags, Affix.VOLATILE)) volatileBurst(sl, dead.position().add(0, dead.getBbHeight() * 0.5, 0));
        // um pouco de XP a mais por afixo (bem pouco: upar continua pesado)
        if (e.getSource().getEntity() instanceof ServerPlayer sp) MagicApi.giveXp(sp, 3 * tags.split(",").length, "kill");
    }

    /** Volátil: chia por ~0,8 s e estoura. Só fere jogadores a até 3 blocos; não quebra bloco nenhum. */
    private static void volatileBurst(ServerLevel sl, Vec3 at) {
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.0F, 1.3F);
        sl.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.02);
        BossKit.schedule(sl, 16, () -> {
            sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.4, 0.3, 0.4, 0.0);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8F, 1.3F);
            for (ServerPlayer sp : sl.players()) {
                if (sp.isSpectator() || sp.isCreative() || sp.distanceToSqr(at) > 9.0) continue;
                sp.hurt(sl.damageSources().explosion(null, null), 6.0F);
            }
        });
    }

    // ------------------------------------------------------------------ cronômetro da descida

    /** Chamado quando o jogador muda de andar: pisar em qualquer andar de uma masmorra nova começa a contagem. */
    public static void onFloor(ServerPlayer sp, ChunkPos dungeon, int floor) {
        if (floor < 1) return;
        Run run = RUNS.get(sp.getUUID());
        if (run == null || run.dungeon() != dungeon.toLong()) {
            RUNS.put(sp.getUUID(), new Run(sp.serverLevel().getGameTime(), dungeon.toLong()));
        }
    }

    private static void finishRuns(ServerLevel sl, LivingEntity boss) {
        long now = sl.getGameTime();
        for (ServerPlayer sp : sl.players()) {
            if (sp.isSpectator() || sp.distanceToSqr(boss) > 64.0 * 64.0) continue;
            Run run = RUNS.remove(sp.getUUID());
            if (run == null) continue;
            int secs = (int) Math.max(1L, (now - run.startTick()) / 20L);
            MagicData d = MagicApi.get(sp);
            int best = d.counter(COUNTER_BEST);
            boolean record = best <= 0 || secs < best;
            if (record) MagicApi.addCounter(sp, COUNTER_BEST, secs - best);
            MagicApi.addCounter(sp, COUNTER_CLEARS, 1);
            sp.sendSystemMessage(Component.translatable(record ? "message.isekai_mahou.depths.run.record" : "message.isekai_mahou.depths.run.done",
                    clock(secs), clock(record ? secs : best)).withColor(record ? 0xFFE27A : 0xC8B8A0));
        }
    }

    /** Tempo da descida em andamento, em segundos (ou -1 se não há). */
    public static int runSeconds(ServerPlayer sp) {
        Run run = RUNS.get(sp.getUUID());
        return run == null ? -1 : (int) ((sp.serverLevel().getGameTime() - run.startTick()) / 20L);
    }

    public static String clock(int secs) {
        return String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
    }

    private DepthsAffixes() {}
}
