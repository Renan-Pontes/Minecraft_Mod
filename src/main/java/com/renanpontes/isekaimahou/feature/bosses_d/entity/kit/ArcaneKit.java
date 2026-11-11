package com.renanpontes.isekaimahou.feature.bosses_d.entity.kit;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.AvatarMove;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialAvatar;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Arcano (padrão quando não há elemento): Campo de Runas (tabuleiro 3×3 em volta de cada jogador, duas ondas),
 * Nova Arcana (dentro → fora), Feixe Arcano giratório, Prisão Arcana (marca que prende e silencia), rastro rúnico e o
 * golpe máximo Sobrecarga Arcana (só os círculos verdes são seguros).
 */
public final class ArcaneKit extends AvatarKit {

    ArcaneKit(TrialAvatar boss) {
        super(boss, Element.ARCANE);
    }

    @Override
    public void flavor(LivingEntity e) {
        // Arcano puro: sem status, só o impacto (a dificuldade vem dos padrões)
        e.hurtMarked = true;
    }

    @Override
    protected ParticleOptions fallParticle() {
        return ParticleTypes.ENCHANT;
    }

    private void runeImpact(ServerLevel sl, Vec3 at, List<LivingEntity> hits) {
        hitAll(hits, 0.9F, this::flavor);
        sl.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 0.6, at.z, 20, 0.6, 0.6, 0.6, 0.6);
        sl.sendParticles(ParticleTypes.WITCH, at.x, at.y + 0.4, at.z, 6, 0.5, 0.2, 0.5, 0.02);
        burst(sl, at.add(0, 0.5, 0), 10, 0.5);
        sound(sl, at, SoundEvents.ILLUSIONER_CAST_SPELL, 0.8F, 1.4F);
    }

    @Override
    protected List<AvatarMove> build() {
        List<AvatarMove> l = new ArrayList<>();
        // 1) Campo de Runas: casas pares, depois ímpares, em volta de cada jogador
        l.add(AvatarMove.of("rune_field", 50, 160, (sl, target, i) -> {
            if (i == 8) {
                List<LivingEntity> foes = marks(3, 30);
                if (foes.isEmpty()) foes = List.of(target);
                int w1 = warm(26), w2 = warm(42);
                List<Vec3> grids = new ArrayList<>();
                for (LivingEntity f : foes) {
                    Vec3 c = f.position();
                    // Jogadores juntos dividem o mesmo tabuleiro: dois tabuleiros sobrepostos apagariam as casas seguras
                    boolean shared = false;
                    for (Vec3 g : grids) if (Math.hypot(g.x - c.x, g.z - c.z) < 10.5) shared = true;
                    if (shared) continue;
                    grids.add(c);
                    for (int gx = -1; gx <= 1; gx++) {
                        for (int gz = -1; gz <= 1; gz++) {
                            Vec3 p = boss.groundAt(new Vec3(c.x + gx * 3.2, c.y, c.z + gz * 3.2));
                            if (p == null || !boss.inArena(p)) continue;
                            boolean even = ((gx + gz) & 1) == 0;
                            // Raio 2,0 com passo 3,2: os cantos das casas também explodem (sem fresta segura nas
                            // duas ondas); o centro das casas da outra cor fica seguro com ~0,9 bloco de folga
                            delayed(sl, circle(p, 2.0F, even ? w1 : w2).noRunes(), hits -> runeImpact(sl, p, hits));
                        }
                    }
                }
                sound(sl, boss.position(), SoundEvents.EVOKER_PREPARE_ATTACK, 2.0F, 1.2F);
            }
        }).anim("meteor").range(0, 30).weight(10));

        // 2) Prisão Arcana: marca que prende e silencia
        l.add(prison("arcane_prison", 0.7F, e -> status(e, CoreRegistries.SILENCED, 40, 0)));

        // 3) Nova Arcana (dentro → fora)
        l.add(insideOutNova("arcane_nova", 1.3F, 1.1F));

        // 4) Feixe Arcano giratório
        l.add(rotatingBeams("arcane_beam"));
        return l;
    }

    /** Rastro: runas que explodem em sequência ao longo da investida. */
    @Override
    public void onDashTrail(ServerLevel sl, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 2) return;
        Vec3 u = d.scale(1 / len);
        int k = 0;
        for (double s = 1.5; s < len; s += 3.0, k++) {
            Vec3 p = boss.groundAt(from.add(u.scale(s)));
            if (p == null) continue;
            delayed(sl, circle(p, 1.4F, 12 + k * 2).noRunes(), hits -> runeImpact(sl, p, hits));
        }
    }

    @Override
    protected AvatarMove buildUltimate() {
        return safeZones("arcane_overload", 2.0F);
    }
}
