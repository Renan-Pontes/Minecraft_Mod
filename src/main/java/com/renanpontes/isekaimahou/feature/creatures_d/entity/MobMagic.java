package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Ajudantes de combate das criaturas: magias de glifo, mira, alvos em área e efeitos. */
public final class MobMagic {

    /**
     * O mob conjura uma magia de glifos (SpellCaster.castAsMob) mirando no alvo.
     * Os glifos podem ser de outros módulos: se algum não existir, devolve false e quem chamou usa o ataque próprio.
     */
    public static boolean cast(Mob mob, LivingEntity target, Element element, float power, String... glyphs) {
        List<ResourceLocation> ids = new ArrayList<>(glyphs.length);
        for (String g : glyphs) {
            ResourceLocation id = IsekaiMahou.id(g);
            if (GlyphRegistry.get(id) == null) return false;
            ids.add(id);
        }
        aim(mob, target);
        return SpellCaster.castAsMob(mob, element, new SpellRecipe(ids, "", -1, ""), power) != null;
    }

    /** Vira o corpo, a cabeça e o olhar do mob para o centro do alvo (as formas de magia usam a rotação). */
    public static void aim(Mob mob, Entity target) {
        Vec3 d = center(target).subtract(mob.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.yBodyRot = yaw;
        mob.setXRot(pitch);
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    /**
     * Alvos de um golpe em área: o alvo atual, jogadores e monstros — nunca aliados nem animais passivos
     * (um golem de musgo irritado não deve esmagar as galinhas em volta).
     */
    public static List<LivingEntity> victimsAround(Mob self, Vec3 c, double r) {
        AABB box = new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
        return self.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != self && e.isAlive()
                && !self.isAlliedTo(e) && e.distanceToSqr(c) <= r * r
                && (e == self.getTarget() || e instanceof Enemy || (e instanceof Player p && !p.isCreative() && !p.isSpectator())));
    }

    /** Empurra o alvo para longe de um ponto. */
    public static void knockAway(Entity target, Vec3 from, double strength, double up) {
        Vec3 d = target.position().subtract(from).multiply(1, 0, 1);
        if (d.lengthSqr() < 1.0E-4) d = new Vec3(0, 0, 1);
        d = d.normalize().scale(strength);
        target.push(d.x, up, d.z);
        target.hurtMarked = true;
    }

    /** Linha de partículas entre dois pontos. */
    public static void line(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions p, double step) {
        Vec3 d = b.subtract(a);
        int n = Math.max(2, (int) (d.length() / step));
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(d.scale(i / (double) n));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /** Anel horizontal de partículas. */
    public static void ring(ServerLevel level, Vec3 c, ParticleOptions p, double radius, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y + 0.1, c.z + Math.sin(a) * radius, 1, 0.05, 0.05, 0.05, speed);
        }
    }

    /** Partículas convergindo para um ponto (carregando energia). */
    public static void converge(ServerLevel level, Vec3 c, ParticleOptions p, double radius, int count) {
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(level.random.nextGaussian(), level.random.nextGaussian(), level.random.nextGaussian()).normalize();
            Vec3 from = c.add(dir.scale(radius));
            Vec3 v = c.subtract(from).scale(0.12);
            level.sendParticles(p, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
        }
    }

    /** Mensagem na barra de ação para os jogadores próximos. */
    public static void tellNear(Entity source, double radius, Component message) {
        if (!(source.level() instanceof ServerLevel sl)) return;
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(source) <= radius * radius) p.displayClientMessage(message, true);
        }
    }

    private MobMagic() {}
}
