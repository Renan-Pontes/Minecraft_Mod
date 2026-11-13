package com.renanpontes.isekaimahou.feature.forms_b.summon;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * Os "corpos" da Forja de Invocações: as invocações fixas dos módulos elementais. Tudo por id em TEXTO (o glifo que
 * invoca e a criatura que nasce) — o forms_b não depende das classes dos outros módulos.
 * <p>
 * Numa receita "Invocação + [corpo] + efeitos", o glifo do corpo escolhe a criatura e o resto manda nela.
 */
public final class SummonBodies {
    /**
     * @param glyph  id do glifo de efeito que invoca o corpo
     * @param entity id do tipo de entidade que nasce
     * @param max    quantas o próprio corpo deixa um dono ter (o Dividir não passa disso; o glifo do corpo é quem
     *               conta o Dividir e respeita este limite)
     * @param caster corpo que quase não bate (Fada): lança os efeitos da receita sozinho, como o espírito
     */
    public record Body(ResourceLocation glyph, ResourceLocation entity, int max, boolean caster) {
        @Nullable
        public EntityType<?> type() {
            return BuiltInRegistries.ENTITY_TYPE.getOptional(entity).orElse(null);
        }
    }

    private static final Map<ResourceLocation, Body> BY_GLYPH = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Body> BY_ENTITY = new LinkedHashMap<>();
    /** Tipos de entidade resolvidos (os registros já estão congelados quando alguém pergunta). */
    private static Set<EntityType<?>> types;

    static {
        add("summon_salamander", "summoned_salamander", 2, false);
        add("summon_undine", "summoned_undine", 2, false);
        add("summon_golem", "summoned_stone_golem", 2, false);
        add("summon_sylph", "summoned_sylph", 2, false);
        add("summon_guardian_fairy", "summoned_guardian_fairy", 3, true);
        add("living_shadow", "living_shadow", 4, false);
        add("summon_ent", "summoned_ent", 2, false);
    }

    private static void add(String glyph, String entity, int max, boolean caster) {
        Body b = new Body(IsekaiMahou.id(glyph), IsekaiMahou.id(entity), max, caster);
        BY_GLYPH.put(b.glyph(), b);
        BY_ENTITY.put(b.entity(), b);
    }

    @Nullable
    public static Body byGlyph(ResourceLocation glyphId) {
        return BY_GLYPH.get(glyphId);
    }

    @Nullable
    public static Body byEntity(EntityType<?> type) {
        return isBody(type) ? BY_ENTITY.get(BuiltInRegistries.ENTITY_TYPE.getKey(type)) : null;
    }

    public static Collection<Body> all() {
        return BY_GLYPH.values();
    }

    /** O tipo de entidade é um corpo de invocação conhecido? (consulta rápida: roda em eventos de entidade) */
    public static boolean isBody(EntityType<?> type) {
        Set<EntityType<?>> t = types;
        if (t == null) {
            t = new HashSet<>();
            for (Body b : BY_GLYPH.values()) {
                EntityType<?> et = b.type();
                if (et != null) t.add(et);
            }
            types = t;
        }
        return t.contains(type);
    }

    private SummonBodies() {}
}
