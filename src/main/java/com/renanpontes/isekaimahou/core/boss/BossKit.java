package com.renanpontes.isekaimahou.core.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Ferramentas de chefe do núcleo (servidor): modo provação, escala de grupo, anti-trapaça (salto/teleporte até
 * alvos inalcançáveis), rugido e tremor de câmera. Tudo estático; chame na thread do servidor.
 */
public final class BossKit {
    /** Tag de entidade ({@code entity.addTag}) dos chefes de Câmara de Provação: sem reset por ociosidade. */
    public static final String TRIAL_TAG = "isekai_mahou.trial_boss";

    /** persistentData: tamanho do grupo usado na escala (gravado uma vez). */
    public static final String PARTY_KEY = "isekai_mahou.party_size";
    /** persistentData: arena do chefe {x,y,z,r} (ver {@link #setArena}). */
    public static final String ARENA_KEY = "isekai_mahou.arena";
    public static final ResourceLocation PARTY_HEALTH_ID = IsekaiMahou.id("party_health");
    public static final ResourceLocation PARTY_DAMAGE_ID = IsekaiMahou.id("party_damage");
    public static final double HEALTH_PER_PLAYER = 0.6;
    public static final double DAMAGE_PER_PLAYER = 0.15;
    /** Tag comum de chefes ({@code c:bosses}). */
    public static final TagKey<EntityType<?>> BOSSES_TAG = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("c", "bosses"));
    /** Vida máxima a partir da qual uma criatura conta como chefe (§2.1 da v0.8). */
    public static final float BOSS_HEALTH = 150F;

    /** Resultado do teste de caminho por mob (recalcula a cada 20 ticks): {gameTime, alcançável 0/1, id do alvo}. */
    private static final Map<Mob, long[]> REACH_CACHE = new WeakHashMap<>();
    /** Mobs no meio de um {@link #leapAt}: até quando ignoram dano de queda. */
    private static final Map<Entity, Long> LEAPING = new WeakHashMap<>();

    // ------------------------------------------------------------------ provação

    public static boolean isTrialBoss(Entity e) {
        return e != null && e.getTags().contains(TRIAL_TAG);
    }

    /**
     * "É chefe?" para regras de jogo (Pilha, status): tag {@code c:bosses}, chefe de provação, ou vida máxima ≥ 150.
     * Jogadores nunca.
     */
    public static boolean isBossLike(@Nullable Entity e) {
        if (!(e instanceof LivingEntity le) || e instanceof Player) return false;
        return le.getType().is(BOSSES_TAG) || isTrialBoss(le) || le.getMaxHealth() >= BOSS_HEALTH;
    }

    /** Marca o chefe como "de provação" (o módulo trials faz isso ao invocar). */
    public static void markTrialBoss(Entity e) {
        e.addTag(TRIAL_TAG);
    }

    /**
     * Grava a arena do chefe (centro + raio) no persistentData — quem invoca (ex.: trials) chama; o chefe usa
     * {@link #arenaCenter} como "casa" e não sai do raio. Fica salvo com a entidade. v1.0: o mesmo centro/raio (24–48)
     * vira a arena PROTEGIDA da luta ({@code core.api.BossArena}) — e qualquer mob com arena gravada conta como chefe de arena.
     */
    public static void setArena(Entity boss, Vec3 center, double radius) {
        CompoundTag t = new CompoundTag();
        t.putDouble("x", center.x);
        t.putDouble("y", center.y);
        t.putDouble("z", center.z);
        t.putDouble("r", radius);
        boss.getPersistentData().put(ARENA_KEY, t);
    }

    /**
     * Sela já a arena protegida do chefe ({@link com.renanpontes.isekaimahou.core.api.BossArena}) usando o centro/raio de
     * {@link #setArena} (ou automáticos). Normalmente não precisa: a arena sela sozinha quando o chefe mira um jogador
     * ou é ferido por um. Use na intro/cutscene para proteger o cenário antes do primeiro golpe.
     */
    public static void sealArena(LivingEntity boss) {
        com.renanpontes.isekaimahou.core.api.BossArena.seal(boss);
    }

    /** Centro da arena gravado por {@link #setArena}, ou null (chefe de mundo aberto: use o ponto de spawn). */
    @Nullable
    public static Vec3 arenaCenter(Entity boss) {
        CompoundTag pd = boss.getPersistentData();
        if (!pd.contains(ARENA_KEY)) return null;
        CompoundTag t = pd.getCompound(ARENA_KEY);
        return new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
    }

    /** Raio da arena gravado por {@link #setArena}, ou 0 se não houver. */
    public static double arenaRadius(Entity boss) {
        CompoundTag pd = boss.getPersistentData();
        return pd.contains(ARENA_KEY) ? pd.getCompound(ARENA_KEY).getDouble("r") : 0;
    }

    // ------------------------------------------------------------------ grupo

    /** Jogadores vivos e não-espectadores no mesmo nível a até {@code radius} blocos de {@code center}. */
    public static List<ServerPlayer> partyAround(Entity center, double radius) {
        List<ServerPlayer> out = new ArrayList<>();
        if (!(center.level() instanceof ServerLevel level)) return out;
        double r2 = radius * radius;
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && p.distanceToSqr(center) <= r2) out.add(p);
        }
        return out;
    }

    /**
     * Escala o chefe pelo grupo (n = {@link #partyAround} no raio, mín. 1): vida máx. ×(1 + 0.6·(n−1)),
     * ATTACK_DAMAGE ×(1 + 0.15·(n−1)) e cura até o máximo. Só aplica UMA vez por entidade (fica salvo);
     * chamadas seguintes só devolvem o n gravado. Chame na intro do chefe.
     */
    public static int scaleToParty(Mob boss, double radius) {
        // No cliente não há jogadores "no raio" de verdade: não grava nada (senão o n=1 ficaria preso)
        if (boss.level().isClientSide()) return partySize(boss);
        CompoundTag pd = boss.getPersistentData();
        if (pd.contains(PARTY_KEY)) return Math.max(1, pd.getInt(PARTY_KEY));
        int n = Math.max(1, partyAround(boss, radius).size());
        pd.putInt(PARTY_KEY, n);
        if (n > 1) {
            AttributeInstance hp = boss.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) {
                hp.addOrReplacePermanentModifier(new AttributeModifier(PARTY_HEALTH_ID, HEALTH_PER_PLAYER * (n - 1),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            AttributeInstance dmg = boss.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmg != null) {
                dmg.addOrReplacePermanentModifier(new AttributeModifier(PARTY_DAMAGE_ID, DAMAGE_PER_PLAYER * (n - 1),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        boss.setHealth(boss.getMaxHealth());
        return n;
    }

    /** n gravado por {@link #scaleToParty} (1 se ainda não escalou). */
    public static int partySize(Entity boss) {
        CompoundTag pd = boss.getPersistentData();
        return pd.contains(PARTY_KEY) ? Math.max(1, pd.getInt(PARTY_KEY)) : 1;
    }

    /** Multiplicador de dano do grupo — use em danos PRÓPRIOS que não leem ATTACK_DAMAGE (magias, ondas). */
    public static float damageScale(Entity boss) {
        return (float) (1 + DAMAGE_PER_PLAYER * (partySize(boss) - 1));
    }

    // ------------------------------------------------------------------ anti-trapaça

    /**
     * O alvo está fora do alcance "honesto" do mob? Verdadeiro quando: está voando/planando alto (elytra, voo,
     * levitação) acima de um mob que não voa; ou não existe caminho até ele (pilar, buraco, barco fora da margem).
     * O teste de caminho é refeito no máx. a cada 20 ticks por mob. Alvo fora do FOLLOW_RANGE conta como alcançável
     * (está só longe). Mob voador só é "inalcançável" quando o caminho falha. No cliente sempre devolve false.
     */
    public static boolean isUnreachable(Mob mob, @Nullable LivingEntity target) {
        // O cache é do servidor (o cliente não tem navegação real e mexeria no mapa de outra thread)
        if (mob.level().isClientSide()) return false;
        if (target == null || !target.isAlive() || target.level() != mob.level()) return false;
        if (mob.isWithinMeleeAttackRange(target)) return false;
        double follow = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (mob.distanceToSqr(target) > follow * follow) return false;
        boolean flyer = canFly(mob);
        if (!flyer) {
            double air = heightAboveGround(target);
            double rise = target.getY() - mob.getY();
            boolean wings = target.isFallFlying() || (target instanceof Player p && p.getAbilities().flying);
            boolean airborne = air > 3.0 || (wings && air > 2.0);
            if (airborne && rise > mob.getBbHeight() + 1.0) return true;
        }
        // Barco na água com o mob em terra: a navegação até acha a margem, mas o golpe nunca chega
        Entity vehicle = target.getVehicle();
        boolean boatCheese = !flyer && (vehicle instanceof Boat || vehicle instanceof AbstractMinecart)
                && vehicle.isInWater() && !mob.isInWater();
        return boatCheese || !canReach(mob, target, flyer);
    }

    private static boolean canReach(Mob mob, LivingEntity target, boolean flyer) {
        long now = mob.level().getGameTime();
        long[] cached = REACH_CACHE.get(mob);
        boolean sameTarget = cached != null && cached[2] == target.getId();
        if (sameTarget && now - cached[0] < 20) return cached[1] == 1;
        // No ar (salto, queda) a navegação terrestre não calcula caminho: mantém o último resultado deste alvo
        if (!flyer && !mob.onGround() && !mob.isInLiquid() && !mob.isPassenger()) return !sameTarget || cached[1] == 1;
        PathNavigation nav = mob.getNavigation();
        Path current = nav.getPath();
        BlockPos tp = target.blockPosition();
        boolean reach;
        if (current != null && !current.isDone() && current.getTarget().closerThan(tp, 2)) {
            reach = current.canReach();
        } else {
            Path p = nav.createPath(target, 1);
            reach = p != null && p.canReach();
        }
        REACH_CACHE.put(mob, new long[] {now, reach ? 1 : 0, target.getId()});
        return reach;
    }

    private static boolean canFly(Mob mob) {
        return mob.getNavigation() instanceof FlyingPathNavigation || mob.isNoGravity();
    }

    /** Distância do pé da entidade até o chão (ou fluido) logo abaixo, até 8 blocos. */
    private static double heightAboveGround(Entity e) {
        if (e.onGround()) return 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos base = e.blockPosition();
        for (int i = 0; i <= 8; i++) {
            m.set(base.getX(), base.getY() - i, base.getZ());
            var state = e.level().getBlockState(m);
            if (!state.getFluidState().isEmpty()) return Math.max(0, e.getY() - (m.getY() + 1));
            VoxelShape s = state.getCollisionShape(e.level(), m);
            if (!s.isEmpty()) return Math.max(0, e.getY() - (m.getY() + s.max(Direction.Axis.Y)));
        }
        return 8;
    }

    /**
     * Salto balístico até o alvo (mira na posição ATUAL: dá para desviar). {@code power} 1 = normal;
     * &gt;1 mais rápido e baixo, &lt;1 mais lento e alto. Devolve os ticks de voo previstos (use para telegrafar
     * o pouso: {@code Telegraph.circle(level, alvo, 3, ticks, cor)}). O mob não leva dano de queda deste salto.
     * Alvo alto: o arco passa acima do topo e cai em cima. Alvo MUITO alto e colado (dy &gt; 6 a menos de 3 blocos
     * na horizontal) bate na lateral do pilar — nesse caso prefira {@link #blinkNear}.
     */
    public static int leapAt(Mob mob, LivingEntity target, double power) {
        return leapTo(mob, target.position(), power);
    }

    /** Igual a {@link #leapAt}, para um ponto (centro da arena, plataforma). */
    public static int leapTo(Mob mob, Vec3 dest, double power) {
        double p = Mth.clamp(power, 0.3, 3.0);
        // Montado (barco/carrinho: trapaça clássica) o veículo seguraria o salto
        if (mob.isPassenger() && !mob.level().isClientSide()) mob.stopRiding();
        Vec3 from = mob.position();
        double dx = dest.x - from.x, dz = dest.z - from.z, dy = dest.y - from.y;
        double dist = Math.sqrt(dx * dx + dz * dz);
        int ticks = Mth.clamp((int) Math.round((8 + dist * 1.1 + Math.max(0, dy) * 0.8) / p), 6, 60);
        // Alvo mais alto (pilar): mira acima do topo para passar por cima da quina e cair em cima dele
        double margin = dy <= 1 ? 0 : Math.min(4.0, 1.0 + dy * 0.35);

        // Física do LivingEntity no ar: a cada tick MOVE e depois horizontal ×0.91, vertical (vy − g)×0.98.
        // No 1º tick o fator horizontal ainda é o do chão (atrito do bloco × 0.91 ≈ 0.55): o LivingEntity o escolhe
        // antes de decolar. Sem isso o salto cai ~35% antes do alvo.
        double f = 0.91, a = 0.98, g = mob.getGravity();
        double f0 = f;
        if (mob.onGround()) {
            BlockPos under = mob.getBlockPosBelowThatAffectsMyMovement();
            f0 = mob.level().getBlockState(under).getFriction(mob.level(), under, mob) * f;
        }
        double hs = 1 + f0 * (1 - Math.pow(f, ticks - 1)) / (1 - f);
        double vs = (1 - Math.pow(a, ticks)) / (1 - a);
        double vx = dx / hs, vz = dz / hs;
        double vy = (dy + margin + g * a * (ticks - vs) / (1 - a)) / vs;
        Vec3 v = new Vec3(vx, vy, vz);
        if (v.length() > 4.0) v = v.normalize().scale(4.0);

        // Com margem, o pouso real é depois de "ticks": simula a descida para devolver o tempo certo
        int land = ticks;
        if (margin > 0 && g > 0) {
            double y = 0, vyk = v.y;
            for (int k = 1; k <= ticks + 40; k++) {
                y += vyk;
                vyk = (vyk - g) * a;
                if (k >= ticks && y <= dy) {
                    land = k;
                    break;
                }
            }
        }

        mob.getNavigation().stop();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.yBodyRot = yaw;
        mob.setDeltaMovement(v);
        mob.hasImpulse = true;
        mob.hurtMarked = true;
        mob.fallDistance = 0;
        if (!mob.level().isClientSide()) LEAPING.put(mob, mob.level().getGameTime() + land + 60);
        return land;
    }

    /**
     * Teleporte seguro para perto do alvo (a ~{@code dist} blocos), num chão firme, sem colisão nem líquido,
     * preferindo um ponto com linha de visão. Partículas e som na saída e na chegada. Devolve se teleportou.
     */
    public static boolean blinkNear(Mob mob, LivingEntity target, double dist) {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        RandomSource rnd = mob.getRandom();
        boolean flyer = canFly(mob);
        Vec3 best = null;
        for (int i = 0; i < 24; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double r = Math.max(1.0, dist) * (i < 16 ? 0.8 + rnd.nextDouble() * 0.4 : 0.35 + rnd.nextDouble() * 0.4);
            double x = target.getX() + Math.cos(ang) * r;
            double z = target.getZ() + Math.sin(ang) * r;
            Vec3 spot = flyer ? airSpot(level, mob, x, target.getY() + 1.0 + rnd.nextDouble() * 2, z)
                    : groundSpot(level, mob, x, target.getY(), z);
            if (spot == null) continue;
            Vec3 eye = spot.add(0, mob.getEyeHeight(), 0);
            if (level.clip(new ClipContext(eye, target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob))
                    .getType() == HitResult.Type.MISS) {
                best = spot;
                break;
            }
            if (best == null) best = spot;
        }
        if (best == null) return false;

        Vec3 from = mob.position();
        poof(level, mob, from);
        // Preso num barco/carrinho: sem descer, o veículo puxaria o mob de volta no tick seguinte
        if (mob.isPassenger()) mob.stopRiding();
        mob.teleportTo(best.x, best.y, best.z);
        mob.getNavigation().stop();
        mob.setDeltaMovement(Vec3.ZERO);
        mob.fallDistance = 0;
        float yaw = (float) (Mth.atan2(target.getZ() - best.z, target.getX() - best.x) * Mth.RAD_TO_DEG) - 90F;
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.yBodyRot = yaw;
        poof(level, mob, best);
        level.gameEvent(GameEvent.TELEPORT, from, GameEvent.Context.of(mob));
        return true;
    }

    @Nullable
    private static Vec3 groundSpot(ServerLevel level, Mob mob, double x, double baseY, double z) {
        // Começa só 2 acima do alvo: mais alto pegaria o TETO de uma sala baixa e o chefe pararia no telhado
        int top = Mth.floor(baseY) + 2;
        for (int y = top; y >= top - 12; y--) {
            BlockPos below = BlockPos.containing(x, y - 1, z);
            VoxelShape s = level.getBlockState(below).getCollisionShape(level, below);
            if (s.isEmpty()) continue;
            double feet = below.getY() + s.max(Direction.Axis.Y);
            AABB box = mob.getDimensions(mob.getPose()).makeBoundingBox(x, feet, z);
            if (level.getWorldBorder().isWithinBounds(box) && level.noCollision(mob, box) && !level.containsAnyLiquid(box)) {
                return new Vec3(x, feet, z);
            }
        }
        return null;
    }

    @Nullable
    private static Vec3 airSpot(ServerLevel level, Mob mob, double x, double y, double z) {
        AABB box = mob.getDimensions(mob.getPose()).makeBoundingBox(x, y, z);
        if (level.getWorldBorder().isWithinBounds(box) && level.noCollision(mob, box) && !level.containsAnyLiquid(box)) {
            return new Vec3(x, y, z);
        }
        return null;
    }

    private static void poof(ServerLevel level, Mob mob, Vec3 at) {
        double w = mob.getBbWidth() * 0.5, h = mob.getBbHeight();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + h * 0.5, at.z, 40, w, h * 0.4, w, 0.06);
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + h * 0.3, at.z, 30, w, h * 0.3, w, 0.4);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.75F);
    }

    // ------------------------------------------------------------------ presença

    /** Rugido: som grave (pelo tamanho), anel de poeira, leve empurrão nos jogadores perto e tremor de câmera. */
    public static void roar(Mob mob) {
        float pitch = Mth.clamp(1.3F - mob.getBbHeight() * 0.15F, 0.5F, 1.2F);
        roar(mob, SoundEvents.RAVAGER_ROAR, pitch);
    }

    public static void roar(Mob mob, SoundEvent sound, float pitch) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundSource.HOSTILE, 3.0F, pitch);
        Vec3 c = mob.position();
        double chest = mob.getBbHeight() * 0.6;
        double r0 = mob.getBbWidth() * 0.6 + 0.4;
        for (int i = 0; i < 36; i++) {
            double ang = i * Math.PI * 2 / 36;
            double cx = Math.cos(ang), cz = Math.sin(ang);
            // count 0 = (dx,dy,dz) vira a velocidade: o anel se espalha para fora
            level.sendParticles(ParticleTypes.CLOUD, c.x + cx * r0, c.y + chest, c.z + cz * r0, 0, cx, 0.03, cz, 0.55);
            level.sendParticles(ParticleTypes.POOF, c.x + cx * r0, c.y + 0.2, c.z + cz * r0, 0, cx, 0.0, cz, 0.35);
        }
        double push = 6 + mob.getBbWidth();
        for (ServerPlayer p : partyAround(mob, push)) {
            Vec3 d = new Vec3(p.getX() - c.x, 0, p.getZ() - c.z);
            double len = d.length();
            d = len < 1.0E-3 ? new Vec3(1, 0, 0) : d.scale(1 / len);
            double k = 0.65 * (1 - Math.min(1, len / push));
            p.push(d.x * k, 0.18, d.z * k);
            p.hurtMarked = true;
        }
        shake(level, c, 28, 1.2F, 24);
    }

    /**
     * Tremor de câmera para os jogadores a até {@code radius} blocos de {@code pos}; diminui com a distância e
     * com o tempo. {@code intensity} ≈ graus de balanço perto do centro (0.5 leve · 1.5 forte · 3 máx.).
     * Sempre ativo (não depende das cutscenes), mas suave.
     */
    public static void shake(ServerLevel level, Vec3 pos, double radius, float intensity, int ticks) {
        if (radius <= 0 || intensity <= 0 || ticks <= 0) return;
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, radius,
                new BossPayloads.Shake(pos.x, pos.y, pos.z, (float) radius, Math.min(3.0F, intensity), Math.min(200, ticks)));
    }

    /** Roda {@code action} daqui a {@code delayTicks} ticks (agendador do kit; descartado se o servidor parar). */
    public static void schedule(ServerLevel level, int delayTicks, Runnable action) {
        BossScheduler.schedule(level, delayTicks, action);
    }

    // ------------------------------------------------------------------ eventos

    static void onFall(LivingFallEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide()) return;
        Long until = LEAPING.get(e);
        if (until == null) return;
        LEAPING.remove(e);
        if (e.level().getGameTime() <= until) event.setDamageMultiplier(0);
    }

    private BossKit() {}
}
