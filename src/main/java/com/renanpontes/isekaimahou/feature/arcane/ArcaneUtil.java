package com.renanpontes.isekaimahou.feature.arcane;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.entity.SpellProjectileEntity;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

/** Utilidades compartilhadas pelos efeitos arcanos (teleporte seguro, donos de magia, partículas...). */
public final class ArcaneUtil {
    /** Por conjuração: chave -> último tick em que rodou (ver {@link #firstThisTick}). */
    private static final Map<SpellContext, Map<String, Long>> GUARD = new WeakHashMap<>();

    // =====================================================================================
    //  Guarda "uma vez por conjuração e tick"
    // =====================================================================================

    /**
     * true só na primeira chamada desta conjuração neste tick. Evita que um efeito "centrado no conjurador"
     * rode duas vezes quando a forma Si Mesmo aplica em você E no bloco embaixo de você.
     * Repetir (Eco) roda em outro tick, então continua funcionando.
     */
    public static boolean firstThisTick(SpellContext ctx, String key) {
        long now = ctx.level().getGameTime();
        Long last = GUARD.computeIfAbsent(ctx, k -> new HashMap<>()).put(key, now);
        return last == null || last != now;
    }

    /** Marca que a chave rodou neste tick (sem bloquear outras chamadas de entidade). */
    public static void markRan(SpellContext ctx, String key) {
        GUARD.computeIfAbsent(ctx, k -> new HashMap<>()).put(key, ctx.level().getGameTime());
    }

    public static boolean ranThisTick(SpellContext ctx, String key) {
        Map<String, Long> m = GUARD.get(ctx);
        Long last = m == null ? null : m.get(key);
        return last != null && last == ctx.level().getGameTime();
    }

    // =====================================================================================
    //  Alvos
    // =====================================================================================

    /** Chefes resistem a controle (teleporte, telecinese, desarmar...). */
    public static boolean isBoss(Entity e) {
        if (e.getType().is(Tags.EntityTypes.BOSSES)) return true;
        return e instanceof LivingEntity le && le.getMaxHealth() >= 200F;
    }

    /** Entidade de magia do mod: o projétil do núcleo ou qualquer "isekai_mahou:spell_*" (zonas, muralhas, runas...). */
    public static boolean isSpellEntity(Entity e) {
        if (e instanceof SpellProjectileEntity) return true;
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return key.getNamespace().equals(IsekaiMahou.MODID) && key.getPath().startsWith("spell_");
    }

    /** Magia do mod OU projéteis mágicos do jogo (bolas de fogo, crânios do Wither, balas de shulker, presas de evocador). */
    public static boolean isMagicEntity(Entity e) {
        return isSpellEntity(e) || e instanceof AbstractHurtingProjectile || e instanceof ShulkerBullet || e instanceof EvokerFangs;
    }

    /** Dono de uma entidade de magia (projétil, invocação, entidade rastreável ou o "caster" salvo na magia). */
    public static UUID ownerOf(Entity e) {
        if (e instanceof Projectile p && p.getOwner() != null) return p.getOwner().getUUID();
        if (e instanceof OwnableEntity o && o.getOwnerUUID() != null) return o.getOwnerUUID();
        if (e instanceof TraceableEntity t && t.getOwner() != null) return t.getOwner().getUUID();
        // Entidades de outros módulos: procura os nomes mais comuns no NBT
        CompoundTag tag;
        try {
            tag = e.saveWithoutId(new CompoundTag());
        } catch (RuntimeException ex) {
            return null; // entidade com save quebrado: trata como magia sem dono
        }
        for (String k : new String[]{"Owner", "owner", "OwnerUUID", "Caster", "caster"}) {
            if (tag.hasUUID(k)) return tag.getUUID(k);
        }
        for (String k : new String[]{"Spell", "spell", "Ctx", "ctx"}) {
            CompoundTag s = tag.getCompound(k);
            if (s.hasUUID("caster")) return s.getUUID("caster");
        }
        return null;
    }

    /** A entidade de magia é de um inimigo do conjurador? (dono desconhecido conta como magia "selvagem" = inimiga) */
    public static boolean isHostileMagic(SpellContext ctx, Entity e) {
        UUID owner = ownerOf(e);
        if (owner == null) return true;
        if (owner.equals(ctx.casterId())) return false;
        Entity ownerEntity = ctx.level().getEntity(owner);
        if (ownerEntity == null) ownerEntity = ctx.level().getServer().getPlayerList().getPlayer(owner);
        return ownerEntity == null || !ctx.isAlly(ownerEntity);
    }

    /** Destrói as magias inimigas numa esfera. Retorna quantas foram destruídas. */
    public static int destroyHostileMagic(SpellContext ctx, Vec3 center, double radius) {
        ServerLevel level = ctx.level();
        int n = 0;
        for (Entity e : level.getEntities((Entity) null, new AABB(center, center).inflate(radius),
                e -> e.isAlive() && isMagicEntity(e) && e.distanceToSqr(center) <= radius * radius)) {
            if (!isHostileMagic(ctx, e)) continue;
            Vec3 p = e.position().add(0, e.getBbHeight() / 2, 0);
            level.sendParticles(Vfx.magicCrit(), p.x, p.y, p.z, 14, 0.25, 0.25, 0.25, 0.3);
            level.sendParticles(Vfx.smoke(), p.x, p.y, p.z, 8, 0.2, 0.2, 0.2, 0.02);
            e.discard();
            n++;
        }
        if (n > 0) {
            level.playSound(null, center.x, center.y, center.z, net.minecraft.sounds.SoundEvents.GLASS_BREAK,
                    SoundSource.PLAYERS, 0.8F, 1.4F);
        }
        return n;
    }

    // =====================================================================================
    //  Teleporte
    // =====================================================================================

    /** Teleporta respeitando EntityTeleportEvent (outros módulos, ex.: Domínio "Sem Fuga", podem cancelar). */
    public static boolean teleport(Entity e, Vec3 to) {
        EntityTeleportEvent ev = new EntityTeleportEvent(e, to.x, to.y, to.z);
        if (NeoForge.EVENT_BUS.post(ev).isCanceled()) return false;
        if (e.isPassenger()) e.stopRiding();
        e.teleportTo(ev.getTargetX(), ev.getTargetY(), ev.getTargetZ());
        e.resetFallDistance();
        if (e instanceof Mob m) m.getNavigation().stop();
        return true;
    }

    /** Igual a {@link #teleport(Entity, Vec3)}, mas virando a entidade para a rotação dada. */
    public static boolean teleport(Entity e, Vec3 to, float yRot, float xRot) {
        if (!(e.level() instanceof ServerLevel level)) return false;
        EntityTeleportEvent ev = new EntityTeleportEvent(e, to.x, to.y, to.z);
        if (NeoForge.EVENT_BUS.post(ev).isCanceled()) return false;
        if (e.isPassenger()) e.stopRiding();
        e.teleportTo(level, ev.getTargetX(), ev.getTargetY(), ev.getTargetZ(), java.util.Set.of(), yRot, xRot);
        e.resetFallDistance();
        if (e instanceof Mob m) m.getNavigation().stop();
        return true;
    }

    /** Rotação (yaw, pitch) para olhar de {@code from} para {@code to}. */
    public static float[] lookRotation(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        return new float[]{yaw, pitch};
    }

    /** A entidade cabe com os pés em {@code feet}? (sem colisão e sem lava) */
    public static boolean fits(Entity e, Vec3 feet) {
        AABB box = e.getDimensions(e.getPose()).makeBoundingBox(feet);
        Level level = e.level();
        if (!level.noCollision(e, box)) return false;
        return level.getBlockStatesIfLoaded(box).noneMatch(s -> s.getFluidState().is(FluidTags.LAVA));
    }

    /**
     * Procura o ponto seguro mais distante (até {@code maxDist}) na direção {@code dir}, sem atravessar paredes:
     * o raio parte dos olhos e para antes do primeiro bloco sólido; depois testa alturas próximas.
     */
    public static Vec3 findSafeSpot(LivingEntity e, Vec3 dir, double maxDist) {
        if (dir.lengthSqr() < 1.0E-6) return null;
        dir = dir.normalize();
        Level level = e.level();
        Vec3 eye = e.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(maxDist)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, e));
        double reach = hit.getType() == HitResult.Type.MISS ? maxDist
                : Math.max(0, eye.distanceTo(hit.getLocation()) - e.getBbWidth() * 0.5 - 0.3);
        double[] heights = {0, 0.5, 1.0, -0.5, -1.0, 1.5};
        for (double d = reach; d >= 0.75; d -= 0.5) {
            Vec3 feet = e.position().add(dir.scale(d));
            for (double dy : heights) {
                Vec3 c = feet.add(0, dy, 0);
                if (fits(e, c)) return c;
            }
        }
        return null;
    }

    // =====================================================================================
    //  Visual / mensagens
    // =====================================================================================

    public static ParticleOptions dust(int rgb, float size) {
        // v1.3: brilho fx aditivo (mesmo tamanho do pó do vanilla; respeita o preset gráfico no cliente)
        return com.renanpontes.isekaimahou.core.ElementFx.glow(rgb, size);
    }

    /** Pó colorido com a cor da magia (a escolhida pelo jogador ou a do elemento). */
    public static ParticleOptions dust(SpellContext ctx, float size) {
        return dust(ctx.color(), size);
    }

    /** Explosão de "teleporte": portal + pó da magia, na altura do corpo. */
    public static void teleportFx(ServerLevel level, Vec3 feet, float height, ParticleOptions dust) {
        double cy = feet.y + height * 0.5;
        level.sendParticles(Vfx.portal(), feet.x, cy, feet.z, 28, 0.3, height * 0.4, 0.3, 0.4);
        level.sendParticles(Vfx.reversePortal(), feet.x, cy, feet.z, 12, 0.25, height * 0.35, 0.25, 0.02);
        level.sendParticles(dust, feet.x, cy, feet.z, 16, 0.3, height * 0.35, 0.3, 0);
    }

    /** Linha de partículas entre dois pontos. */
    public static void line(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions p, double step) {
        Vec3 d = b.subtract(a);
        int n = Math.max(2, (int) (d.length() / step));
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(d.scale(i / (double) n));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    /** Casca esférica de partículas (escudos, pulsos). */
    public static void sphere(ServerLevel level, Vec3 c, double r, ParticleOptions p, int points) {
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < points; i++) {
            double y = 1 - (i / (double) (points - 1)) * 2;
            double rad = Math.sqrt(1 - y * y);
            double th = golden * i;
            level.sendParticles(p, c.x + Math.cos(th) * rad * r, c.y + y * r, c.z + Math.sin(th) * rad * r, 1, 0, 0, 0, 0);
        }
    }

    /** Anel horizontal de partículas. */
    public static void ring(ServerLevel level, Vec3 c, double r, ParticleOptions p, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Mensagem na barra de ação (só para jogadores). */
    public static void actionbar(Entity e, Component msg) {
        if (e instanceof ServerPlayer p) p.displayClientMessage(msg, true);
    }

    /** Visual de "resistiu" (chefes imunes a controle). */
    public static void resisted(ServerLevel level, Entity target, Entity caster) {
        Vec3 c = center(target);
        level.sendParticles(Vfx.smoke(), c.x, c.y, c.z, 12, 0.4, 0.4, 0.4, 0.02);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                SoundSource.HOSTILE, 0.7F, 0.6F);
        actionbar(caster, Component.translatable("message.isekai_mahou.arcane_resisted", target.getDisplayName()));
    }

    private ArcaneUtil() {}
}
