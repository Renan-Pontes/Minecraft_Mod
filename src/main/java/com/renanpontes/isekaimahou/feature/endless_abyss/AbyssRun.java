package com.renanpontes.isekaimahou.feature.endless_abyss;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.boss.BossKit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Uma descida ao Abismo (servidor). Dois modos:
 * <ul>
 *   <li>{@link Mode#ENDLESS}: andares sem fim — ondas de inimigos cada vez mais fortes e, a cada 5 andares, um chefe
 *       (a Sombra de um chefe que alguém do grupo já venceu; sem nenhuma, um "Campeão do Abismo");</li>
 *   <li>{@link Mode#SHADOW}: uma Luta das Sombras avulsa contra o chefe escolhido.</li>
 * </ul>
 * Fases: {@code BUILDING} (monta a arena aos poucos, se a vaga ainda não tem) → {@code REST} (pausa segura entre
 * andares) → {@code FIGHT} → de volta ao {@code REST} do próximo andar, até todos caírem ou saírem.
 * Cair não mata: o jogador volta inteiro para onde estava. A tela fica limpa: só uma barra no topo e avisos curtos.
 */
final class AbyssRun {
    enum Mode { ENDLESS, SHADOW }

    enum Phase { BUILDING, REST, FIGHT, DONE }

    /** persistentData das criaturas da descida: UUID da descida e multiplicador de dano. */
    static final String RUN_KEY = "isekai_mahou.abyss_run";
    static final String DMG_KEY = "isekai_mahou.abyss_dmg";
    private static final ResourceLocation HEALTH_ID = IsekaiMahou.id("abyss_health");
    private static final ResourceLocation SCALE_ID = IsekaiMahou.id("abyss_champion_scale");

    private static final int BLOCKS_PER_TICK = 700;
    private static final int REST_FIRST = 80, REST_BETWEEN = 120, WAVE_GAP = 30, VICTORY_HOLD = 100;
    /** Depois disso os inimigos que sobraram ficam com contorno (ninguém caça mob escondido no escuro). */
    private static final int REVEAL_AFTER = 20 * 60;

    final UUID id = UUID.randomUUID();
    final ServerLevel level;
    final int index;
    final Mode mode;
    @Nullable final String shadowBoss;
    final Set<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> waiting = new LinkedHashSet<>();
    private final List<Mob> alive = new ArrayList<>();
    private final List<String> shadowNames = new ArrayList<>();
    private final ServerBossEvent bar;
    private final RandomSource random = RandomSource.create();

    private Phase phase = Phase.BUILDING;
    private boolean open = true;
    private int floor;
    private int wave, waves, waveTotal;
    private int timer, fightTicks, ticks, deadTicks;
    private boolean bossFloor;
    /** Path do chefe cuja Sombra está em campo neste andar (null = ondas comuns ou Campeão). */
    @Nullable private String currentShadow;
    private float currentShadowHealth;
    @Nullable private List<AbyssDimension.Placement> build;
    private int buildCursor;

    AbyssRun(ServerLevel level, int index, Mode mode, int startFloor, @Nullable String shadowBoss, boolean needsBuild) {
        this.level = level;
        this.index = index;
        this.mode = mode;
        this.floor = Math.max(1, startFloor);
        this.shadowBoss = shadowBoss;
        this.bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        this.bar.setDarkenScreen(false);
        if (needsBuild) this.build = AbyssDimension.layout(index);
        else enterRest(REST_FIRST);
    }

    boolean open() { return open; }
    Phase phase() { return phase; }
    int floor() { return floor; }
    /** Para a aba: andar atual, ou −1 numa Luta das Sombras avulsa. */
    int shownFloor() { return mode == Mode.SHADOW ? -1 : Math.max(1, floor); }
    boolean isMember(UUID p) { return members.contains(p); }

    /** Dá para um companheiro de grupo entrar agora? (só na pausa entre andares de uma descida sem fim) */
    boolean joinable() {
        return open && mode == Mode.ENDLESS && (phase == Phase.BUILDING || phase == Phase.REST) && members.size() < 4;
    }

    /** Fora da luta ninguém toma dano (pausa, montagem, vitória). */
    boolean safeNow() {
        return phase != Phase.FIGHT;
    }

    // =====================================================================================
    //  Membros
    // =====================================================================================
    void addMember(ServerPlayer p) {
        members.add(p.getUUID());
        if (phase == Phase.BUILDING) waiting.add(p.getUUID());
        else bringIn(p);
    }

    private void bringIn(ServerPlayer p) {
        Vec3 c = AbyssDimension.centerVec(index);
        double ang = random.nextDouble() * Math.PI * 2;
        Vec3 to = c.add(Math.cos(ang) * 2.5, 0, Math.sin(ang) * 2.5);
        fx(p.serverLevel(), p.position());
        p.teleportTo(level, to.x, to.y, to.z, p.getYRot(), 0F);
        p.setDeltaMovement(Vec3.ZERO);
        p.resetFallDistance();
        fx(level, to);
        bar.addPlayer(p);
    }

    /** Tira o jogador da descida (sem teleportar: quem chama decide). Sem ninguém, a descida fecha. */
    void removeMember(UUID player) {
        if (!members.remove(player)) return;
        waiting.remove(player);
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(player);
        if (p != null) bar.removePlayer(p);
        if (members.isEmpty()) close();
    }

    private List<ServerPlayer> present() {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID u : members) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(u);
            if (p != null && !p.hasDisconnected() && p.level() == level) out.add(p);
        }
        return out;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    void tick() {
        if (!open) return;
        ticks++;
        switch (phase) {
            case BUILDING -> tickBuild();
            case REST -> tickRest();
            case FIGHT -> tickFight();
            case DONE -> {
                if (--timer <= 0) close();
            }
        }
        if (open && phase != Phase.BUILDING && ticks % 10 == 0) keepInside();
    }

    private void tickBuild() {
        if (build != null) {
            int end = Math.min(build.size(), buildCursor + BLOCKS_PER_TICK);
            for (; buildCursor < end; buildCursor++) {
                AbyssDimension.Placement pl = build.get(buildCursor);
                level.setBlock(pl.pos(), pl.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            if (buildCursor < build.size()) return;
            build = null;
            AbyssManager.data().markBuilt(index);
        }
        enterRest(REST_FIRST);
        for (UUID u : List.copyOf(waiting)) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(u);
            if (p != null && !p.hasDisconnected()) bringIn(p);
            else removeMember(u);
        }
        waiting.clear();
    }

    private void enterRest(int ticksLeft) {
        phase = Phase.REST;
        timer = ticksLeft;
        updateBar();
    }

    private void tickRest() {
        if (timer % 20 == 0) updateBar();
        if (--timer > 0) return;
        if (present().isEmpty()) return; // ninguém chegou ainda (teleporte em andamento): espera
        startFloor();
    }

    private void tickFight() {
        fightTicks++;
        int before = alive.size();
        // Chefes têm morte longa (animação/cutscene): o andar só fecha quando o corpo some (ou depois de 15 s)
        if (bossFloor && !alive.isEmpty() && alive.stream().noneMatch(Mob::isAlive)) deadTicks++;
        else deadTicks = 0;
        alive.removeIf(m -> m.isRemoved() || (!m.isAlive() && (!bossFloor || deadTicks > 300)));
        if (alive.size() != before) updateBar();
        if (alive.isEmpty()) {
            if (timer > 0) {
                // intervalo curto entre ondas
                if (--timer == 0) spawnWave();
                return;
            }
            if (!bossFloor && wave < waves) {
                timer = WAVE_GAP;
                return;
            }
            floorCleared();
            return;
        }
        if (ticks % 20 == 0) {
            List<ServerPlayer> players = fighters();
            for (Mob m : alive) {
                // Chefe com arena selada: o núcleo (BossArena) já o mantém lá dentro, inclusive voando alto
                if (!BossArena.isSealed(m) && !AbyssDimension.inside(index, m.position(), 1.5)) {
                    Vec3 to = spot(4, 14);
                    m.teleportTo(to.x, to.y, to.z);
                }
                LivingEntity t = m.getTarget();
                if ((t == null || !t.isAlive()) && !players.isEmpty()) m.setTarget(nearest(players, m.position()));
                if (fightTicks > REVEAL_AFTER && !bossFloor) m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
            }
        }
    }

    /** Jogadores e criaturas não saem da arena (voo, empurrão, queda): voltam para dentro sem dano. */
    private void keepInside() {
        for (ServerPlayer p : present()) {
            if (p.isSpectator() || AbyssDimension.inside(index, p.position(), 2.5)) continue;
            Vec3 c = AbyssDimension.centerVec(index);
            p.teleportTo(level, c.x, c.y, c.z, p.getYRot(), p.getXRot());
            p.setDeltaMovement(Vec3.ZERO);
            p.resetFallDistance();
        }
    }

    // =====================================================================================
    //  Andares
    // =====================================================================================
    private void startFloor() {
        phase = Phase.FIGHT;
        fightTicks = 0;
        timer = 0;
        wave = 0;
        currentShadow = null;
        bossFloor = mode == Mode.SHADOW || AbyssFloors.isBossFloor(floor);
        if (mode == Mode.SHADOW) {
            int wins = 0;
            for (ServerPlayer p : present()) wins = Math.max(wins, AbyssRecords.shadowWins(level.getServer(), p.getUUID(), shadowBoss));
            if (!spawnShadow(shadowBoss, AbyssFloors.shadowFightHealth(wins), AbyssFloors.shadowFightDamage(wins))) {
                // o tipo sumiu (módulo ausente): fecha sem prejuízo
                broadcast(Component.translatable("message.isekai_mahou.endless_abyss.shadow_missing").withStyle(ChatFormatting.RED));
                close();
                return;
            }
        } else if (bossFloor) {
            List<String> candidates = new ArrayList<>();
            for (ServerPlayer p : present()) {
                for (String s : AbyssShadows.defeated(p)) if (!candidates.contains(s)) candidates.add(s);
            }
            boolean ok = false;
            if (!candidates.isEmpty()) {
                String pick = candidates.get(random.nextInt(candidates.size()));
                ok = spawnShadow(pick, AbyssFloors.bossHealthMult(floor), AbyssFloors.bossDamageMult(floor));
            }
            if (!ok) spawnChampion();
        } else {
            waves = AbyssFloors.waves(floor);
            spawnWave();
        }
        if (bossFloor && !alive.isEmpty()) {
            Component name = alive.get(0).getDisplayName();
            for (ServerPlayer p : present()) {
                title(p, Component.translatable("title.isekai_mahou.endless_abyss.boss").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), name);
            }
            level.playSound(null, BlockPos.containing(AbyssDimension.centerVec(index)), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.4F, 0.8F);
        }
        updateBar();
    }

    private void spawnWave() {
        wave++;
        fightTicks = 0;
        List<EntityType<?>> pool = AbyssFloors.pool(floor);
        int count = AbyssFloors.mobsPerWave(floor, Math.max(1, present().size()));
        float hp = AbyssFloors.healthMult(floor), dmg = AbyssFloors.damageMult(floor);
        for (int i = 0; i < count; i++) {
            Entity e = pool.get(random.nextInt(pool.size())).create(level);
            if (!(e instanceof Mob mob)) {
                if (e != null) e.discard();
                continue;
            }
            place(mob, spot(8, 18), hp, dmg);
        }
        waveTotal = alive.size();
        level.playSound(null, BlockPos.containing(AbyssDimension.centerVec(index)), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.9F, 0.7F);
        updateBar();
    }

    private boolean spawnShadow(String path, float hp, float dmg) {
        Mob mob = AbyssShadows.create(level, path);
        if (mob == null) return false;
        Vec3 c = AbyssDimension.centerVec(index);
        Vec3 at = c.add(0, 0, -10);
        // A arena protegida do núcleo fica centrada na arena do Abismo (e não onde o chefe nasceu)
        BossKit.setArena(mob, c, AbyssDimension.RADIUS + 2);
        AbyssShadows.mark(mob, AbyssShadows.bossName(path), false);
        shadowNames.add(mob.getScoreboardName());
        if (!place(mob, at, hp, dmg)) {
            releaseShadows();
            return false;
        }
        BossArena.seal(mob);
        currentShadow = path;
        currentShadowHealth = mob.getMaxHealth();
        waveTotal = alive.size();
        return true;
    }

    /** Andar de chefe sem Sombra disponível: uma criatura do poço, enorme e bem mais forte. */
    private void spawnChampion() {
        Entity e = AbyssFloors.championType(floor, random).create(level);
        if (!(e instanceof Mob mob)) {
            if (e != null) e.discard();
            return;
        }
        Component base = mob.getType().getDescription();
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null) scale.addPermanentModifier(new AttributeModifier(SCALE_ID, 0.7, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        AbyssShadows.mark(mob, base, true);
        shadowNames.add(mob.getScoreboardName());
        place(mob, AbyssDimension.centerVec(index).add(0, 0, -10), AbyssFloors.healthMult(floor) * 8F, AbyssFloors.damageMult(floor) * 1.5F);
        waveTotal = alive.size();
    }

    /** Põe a criatura no mundo já marcada e fortalecida. */
    private boolean place(Mob mob, Vec3 at, float healthMult, float damageMult) {
        Vec3 c = AbyssDimension.centerVec(index);
        float yaw = (float) (Mth.atan2(c.z - at.z, c.x - at.x) * Mth.RAD_TO_DEG) - 90F;
        mob.moveTo(at.x, at.y, at.z, yaw, 0F);
        mob.getPersistentData().putUUID(RUN_KEY, id);
        mob.getPersistentData().putFloat(DMG_KEY, damageMult);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
        mob.setPersistenceRequired();
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && healthMult > 1F) {
            hp.removeModifier(HEALTH_ID);
            hp.addPermanentModifier(new AttributeModifier(HEALTH_ID, healthMult - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (!level.addFreshEntity(mob)) return false;
        // Depois de entrar no mundo (o balanço do núcleo e alguns chefes mexem na vida ao nascer): vida cheia
        AbyssManager.later(3, () -> {
            if (mob.isAlive()) mob.setHealth(mob.getMaxHealth());
        });
        level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.8, at.z, 10, 0.3, 0.6, 0.3, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.6, at.z, 8, 0.3, 0.5, 0.3, 0.01);
        List<ServerPlayer> players = fighters();
        if (!players.isEmpty()) mob.setTarget(nearest(players, at));
        alive.add(mob);
        return true;
    }

    private void floorCleared() {
        sweepMinions();
        List<ServerPlayer> players = present();
        long now = level.getGameTime();
        if (mode == Mode.SHADOW) {
            for (ServerPlayer p : players) {
                int wins = AbyssManager.data().recordShadow(p.getUUID(), p.getGameProfile().getName(), shadowBoss);
                AbyssRewards.shadowFight(p, shadowBoss, wins, currentShadowHealth);
                AbyssNetwork.sendInfo(p);
            }
            phase = Phase.DONE;
            timer = VICTORY_HOLD;
            level.playSound(null, BlockPos.containing(AbyssDimension.centerVec(index)), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 0.8F);
            updateBar();
            return;
        }
        for (ServerPlayer p : players) {
            int oldBest = AbyssRecords.bestFloor(level.getServer(), p.getUUID());
            boolean record = AbyssManager.data().recordFloor(p.getUUID(), p.getGameProfile().getName(), floor, now);
            AbyssRewards.floor(p, floor, bossFloor, currentShadow, oldBest);
            if (currentShadow != null) AbyssManager.data().recordShadow(p.getUUID(), p.getGameProfile().getName(), currentShadow);
            // Fôlego entre andares: um quarto da vida (a mana se recupera sozinha na pausa)
            p.heal(p.getMaxHealth() * 0.25F);
            MutableComponent msg = Component.translatable(record ? "message.isekai_mahou.endless_abyss.floor_record"
                    : "message.isekai_mahou.endless_abyss.floor_clear", floor);
            p.displayClientMessage(msg.withStyle(record ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY), true);
            if (record && floor % AbyssFloors.CHECKPOINT_EVERY == 0) {
                // Um aviso só, no chat de todos, a cada marco inédito de 10 andares
                level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
                        "message.isekai_mahou.endless_abyss.announce", p.getDisplayName(), floor).withStyle(ChatFormatting.DARK_PURPLE), false);
            }
        }
        level.playSound(null, BlockPos.containing(AbyssDimension.centerVec(index)), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 0.8F, 1.2F);
        floor++;
        enterRest(REST_BETWEEN);
    }

    /** Lacaios que os chefes invocaram (não são da onda): somem junto com o andar. Invocações de jogadores ficam. */
    private void sweepMinions() {
        for (Mob m : level.getEntitiesOfClass(Mob.class, AbyssDimension.box(index))) {
            if (m instanceof OwnableEntity own && own.getOwnerUUID() != null && members.contains(own.getOwnerUUID())) continue;
            if (m.getPersistentData().hasUUID(RUN_KEY) || m instanceof Enemy) m.discard();
        }
        releaseShadows();
        alive.clear();
    }

    /**
     * Tira a vida extra do Abismo de uma criatura que acabou de morrer: o XP de abate do núcleo é pela vida máxima, e
     * um inimigo inflado renderia XP demais (upar não pode ficar fácil lá dentro).
     */
    static void stripHealthBonus(LivingEntity mob) {
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.removeModifier(HEALTH_ID);
    }

    private void releaseShadows() {
        for (String n : shadowNames) AbyssShadows.unmark(level.getServer(), n);
        shadowNames.clear();
    }

    // =====================================================================================
    //  Fim
    // =====================================================================================
    /** O jogador caiu (ou saiu): mensagem com o resultado. O teleporte de volta é do gerenciador. */
    void reportExit(ServerPlayer p, boolean fell) {
        if (mode == Mode.SHADOW) {
            if (phase != Phase.DONE) {
                p.sendSystemMessage(Component.translatable(fell ? "message.isekai_mahou.endless_abyss.shadow_fell"
                        : "message.isekai_mahou.endless_abyss.left").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        int best = AbyssRecords.bestFloor(level.getServer(), p.getUUID());
        p.sendSystemMessage(Component.translatable(fell ? "message.isekai_mahou.endless_abyss.fell"
                : "message.isekai_mahou.endless_abyss.left_floor", floor, best).withStyle(ChatFormatting.GRAY));
    }

    /** Fecha a descida: devolve quem ainda está, apaga as criaturas e solta os chunks. */
    void close() {
        if (!open) return;
        open = false;
        phase = Phase.DONE;
        for (UUID u : List.copyOf(members)) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(u);
            if (p != null && p.level() == level) AbyssManager.sendHome(p);
        }
        members.clear();
        waiting.clear();
        bar.removeAllPlayers();
        // Tudo que sobrou na arena some (criaturas, invocações temporárias, itens no chão): a vaga fica limpa para a próxima
        for (Entity e : level.getEntities((Entity) null, AbyssDimension.box(index), x -> !(x instanceof Player))) e.discard();
        releaseShadows();
        alive.clear();
        AbyssManager.release(this);
    }

    /** Servidor parando: só desmarca as Sombras (os jogadores são devolvidos quando entrarem de novo). */
    void shutdown() {
        open = false;
        bar.removeAllPlayers();
        releaseShadows();
    }

    // =====================================================================================
    //  Comandos de teste
    // =====================================================================================
    /** Mata tudo que está em campo (o andar/onda conta como vencido). */
    void debugSkip() {
        for (Mob m : List.copyOf(alive)) m.discard();
        alive.clear();
    }

    /** Pula para o andar informado (começa depois de uma pausa curta). */
    void debugFloor(int target) {
        if (mode != Mode.ENDLESS || phase == Phase.BUILDING) return;
        for (Mob m : List.copyOf(alive)) m.discard();
        sweepMinions();
        floor = Math.max(1, target);
        enterRest(40);
    }

    Component describe() {
        return Component.literal("#" + index + " " + mode + " " + phase + " floor=" + floor + " wave=" + wave + "/" + waves
                + " alive=" + alive.size() + " members=" + members.size());
    }

    // =====================================================================================
    //  Utilidades
    // =====================================================================================
    /** Quem pode ser alvo: presente, vivo e fora do criativo/espectador. */
    private List<ServerPlayer> fighters() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : present()) if (p.isAlive() && !p.isCreative() && !p.isSpectator()) out.add(p);
        return out;
    }

    private static ServerPlayer nearest(List<ServerPlayer> players, Vec3 at) {
        ServerPlayer best = players.get(0);
        for (ServerPlayer p : players) if (p.distanceToSqr(at) < best.distanceToSqr(at)) best = p;
        return best;
    }

    /** Ponto aleatório no piso entre os raios dados, de preferência longe dos jogadores. */
    private Vec3 spot(double minR, double maxR) {
        Vec3 c = AbyssDimension.centerVec(index);
        List<ServerPlayer> players = present();
        Vec3 pick = c;
        for (int tries = 0; tries < 8; tries++) {
            double ang = random.nextDouble() * Math.PI * 2, r = minR + random.nextDouble() * (maxR - minR);
            pick = c.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            boolean far = true;
            for (ServerPlayer p : players) if (p.distanceToSqr(pick) < 36) far = false;
            if (far) break;
        }
        return pick;
    }

    private void broadcast(Component msg) {
        for (ServerPlayer p : present()) p.sendSystemMessage(msg);
    }

    private void updateBar() {
        Component name;
        float progress = 1F;
        if (phase == Phase.DONE) {
            name = Component.translatable("bar.isekai_mahou.endless_abyss.victory");
        } else if (mode == Mode.SHADOW) {
            name = Component.translatable("bar.isekai_mahou.endless_abyss.shadow", AbyssShadows.bossName(shadowBoss));
            if (phase == Phase.FIGHT) progress = alive.isEmpty() ? 0F : 1F;
        } else if (phase == Phase.REST) {
            name = Component.translatable("bar.isekai_mahou.endless_abyss.rest", floor, Math.max(1, (timer + 19) / 20));
        } else if (bossFloor) {
            name = Component.translatable("bar.isekai_mahou.endless_abyss.boss", floor);
            progress = alive.isEmpty() ? 0F : 1F;
        } else {
            name = Component.translatable("bar.isekai_mahou.endless_abyss.wave", floor, Math.max(1, wave), waves);
            progress = waveTotal <= 0 ? 0F : Mth.clamp(alive.size() / (float) waveTotal, 0F, 1F);
        }
        bar.setName(name);
        bar.setProgress(progress);
    }

    private static void title(ServerPlayer p, Component title, Component subtitle) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(6, 40, 12));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    static void fx(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 36, 0.35, 0.8, 0.35, 0.05);
        level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.8, at.z, 8, 0.3, 0.6, 0.3, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.6F);
    }
}
