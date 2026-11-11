package com.renanpontes.isekaimahou.feature.legends_f.necro;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_f.entity.KodamaEntity;
import com.renanpontes.isekaimahou.feature.legends_f.entity.ShadowSoldierEntity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * "Sombras" que ficam no lugar de criaturas mortas por ~60 s: a matéria-prima do "Levante-se!". Só na memória do
 * servidor (até 256 por vez). Quem conhece o glifo vê a sombra persistir (fumaça roxa e almas) perto do corpo.
 */
public final class ShadowRemnants {
    public static final int LIFETIME = 1200;
    public static final int TRIES = 3;
    private static final int MAX = 256;
    static final ResourceLocation ARISE = IsekaiMahou.id("necro_arise");

    /** Uma sombra no chão. */
    public static final class Remnant {
        public final ResourceKey<Level> dim;
        public final Vec3 pos;
        public final String type;
        @Nullable
        public final String customName;
        public final float maxHp;
        public final float attack;
        public final float width;
        public final float height;
        public final boolean boss;
        public final long time;
        public int tries = TRIES;

        Remnant(ResourceKey<Level> dim, Vec3 pos, String type, @Nullable String customName, float maxHp, float attack,
                float width, float height, boolean boss, long time) {
            this.dim = dim;
            this.pos = pos;
            this.type = type;
            this.customName = customName;
            this.maxHp = maxHp;
            this.attack = attack;
            this.width = width;
            this.height = height;
            this.boss = boss;
            this.time = time;
        }
    }

    private static final List<Remnant> LIST = new ArrayList<>();

    /** A criatura pode virar sombra? (não jogadores, não invocações/pets de alguém, não suportes de armadura). */
    public static boolean eligible(LivingEntity e) {
        if (e instanceof Player || e instanceof ArmorStand || e instanceof ShadowSoldierEntity || e instanceof KodamaEntity) return false;
        if (e instanceof OwnableEntity oe && oe.getOwnerUUID() != null) return false;
        return e.getMaxHealth() >= 4F && EntityType.getKey(e.getType()) != null;
    }

    /** Monta a sombra de uma criatura (sem guardar). */
    public static Remnant of(LivingEntity e) {
        float atk = 2F;
        var inst = e.getAttribute(Attributes.ATTACK_DAMAGE);
        if (inst != null) atk = (float) Math.max(1.0, inst.getValue());
        String name = e.hasCustomName() && e.getCustomName() != null ? e.getCustomName().getString() : null;
        if (name != null && name.length() > 32) name = name.substring(0, 32);
        return new Remnant(e.level().dimension(), e.position(), EntityType.getKey(e.getType()).toString(), name,
                e.getMaxHealth(), atk, e.getBbWidth(), e.getBbHeight(), Statuses.isBossLike(e), e.level().getGameTime());
    }

    static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || event.isCanceled() || !eligible(e)) return;
        // O Monarca das Sombras ergue na hora quem cai perto dele
        if (MonarchState.autoArise(level, e)) return;
        if (LIST.size() >= MAX) LIST.remove(0);
        LIST.add(of(e));
    }

    /** Sombras a até {@code radius} de {@code c} (na mesma dimensão), mais perto primeiro. */
    public static List<Remnant> near(ServerLevel level, Vec3 c, double radius) {
        long now = level.getGameTime();
        List<Remnant> out = new ArrayList<>();
        for (Remnant r : LIST) {
            if (r.dim == level.dimension() && now - r.time <= LIFETIME && r.pos.distanceToSqr(c) <= radius * radius) out.add(r);
        }
        out.sort((a, b) -> Double.compare(a.pos.distanceToSqr(c), b.pos.distanceToSqr(c)));
        return out;
    }

    public static void remove(Remnant r) {
        LIST.remove(r);
    }

    /** A cada 10 ticks: some com as velhas e mostra as sombras para quem conhece o "Levante-se!". */
    static void tick(MinecraftServer server) {
        if (LIST.isEmpty()) return;
        ServerLevel any = server.overworld();
        long now = any.getGameTime();
        if (now % 10 != 0) return;
        Iterator<Remnant> it = LIST.iterator();
        while (it.hasNext()) {
            if (now - it.next().time > LIFETIME) it.remove();
        }
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.35F, 0.12F, 0.6F), 1.4F);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!MagicApi.get(sp).knowsGlyph(ARISE)) continue;
            ServerLevel level = sp.serverLevel();
            for (Remnant r : LIST) {
                if (r.dim != level.dimension() || r.pos.distanceToSqr(sp.position()) > 32 * 32) continue;
                double w = Math.max(0.3, r.width * 0.4);
                level.sendParticles(sp, dust, false, r.pos.x, r.pos.y + 0.2, r.pos.z, 4, w, 0.1, w, 0);
                level.sendParticles(sp, ParticleTypes.SMOKE, false, r.pos.x, r.pos.y + 0.3, r.pos.z, 2, w, 0.2, w, 0.01);
                if (now % 40 == 0) level.sendParticles(sp, ParticleTypes.SOUL, false, r.pos.x, r.pos.y + 0.5, r.pos.z, 1, w, 0.2, w, 0.02);
            }
        }
    }

    static void clear() {
        LIST.clear();
    }

    private ShadowRemnants() {}
}
