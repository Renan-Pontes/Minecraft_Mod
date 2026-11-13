package com.renanpontes.isekaimahou.feature.settlements.magic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.settlements.CityHappiness;
import com.renanpontes.isekaimahou.feature.settlements.CityPace;
import com.renanpontes.isekaimahou.feature.settlements.CityPlanning;
import com.renanpontes.isekaimahou.feature.settlements.CityPlots;
import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.SettlementService;
import com.renanpontes.isekaimahou.feature.settlements.SettlementsLang;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain;
import com.renanpontes.isekaimahou.feature.settlements.build.ConstructionEngine;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;
import com.renanpontes.isekaimahou.feature.settlements.build.Templates;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * CONSTRUIR COM MAGIA (release 1.0, onda 4 — "city_magic"): três efeitos Arcanos que o jogador monta no Grimório como
 * qualquer magia (Si Mesmo, Toque, Projétil... + o efeito + aumentos) e que só funcionam na cidade FUNDADA em que ele
 * pode planejar (Senhor ou conselheiro = permissão do Senhor):
 * <ul>
 *   <li><b>Impulso de Obra</b> ({@link #HASTEN}): as obras em andamento perto do alvo andam mais rápido por alguns
 *       segundos (Estender = mais tempo, Expandir = mais longe, Amplificar = mais forte, Canalizar = manter);</li>
 *   <li><b>Erguer Obra</b> ({@link #ERECT}): faz de uma vez uma FRAÇÃO DE DIA da obra mais perto do alvo, por metade
 *       da mana máxima (onda 6: as obras levam dias — a magia adianta, com limite diário por obra, não termina);</li>
 *   <li><b>Moldar Lote</b> ({@link #SHAPE}): nivela o lote vazio do alvo; com Amplificar sobe, com Enfraquecer baixa
 *       (1 bloco por aumento, limite pelo Círculo).</li>
 * </ul>
 * Nada aqui pula os requisitos da cidade: a magia só age em obras que o planejamento já aceitou (nível do centro,
 * lote liberado, limite por tipo...) e o Moldar Lote passa pelas mesmas regras do "aplainar" da planta. Fora da
 * própria cidade os efeitos não fazem nada — cidades são sagradas.
 * <p>Os glifos são Arcanos com tier: o Caminho Arcano ensina a todos pelo nível (5, 10 e 18) e Círculo.
 */
public final class CityMagic {
    public static final String HASTEN = "city_hasten", ERECT = "city_erect", SHAPE = "city_shape";
    private static final ResourceLocation HOME_REALM = IsekaiMahou.id("home_realm");
    private static final int COLOR = 0xE8C56B;
    /** Teto do Impulso de Obra (× a velocidade dos construtores) e da duração. */
    private static final float MAX_HASTEN = 4F;
    private static final int MAX_HASTEN_TICKS = 1200;
    /** Erguer Obra custa esta fração da mana MÁXIMA (além do custo da magia). */
    public static final float ERECT_MANA = 0.5F;
    /** Moldar Lote: fração da mana máxima para nivelar, e a mais por bloco subido/baixado. */
    public static final float SHAPE_MANA = 0.15F, SHAPE_STEP_MANA = 0.10F;

    /** Última aplicação de cada efeito por jogador (uma forma em área chama o efeito várias vezes no mesmo tick). */
    private static final Map<String, Long> LAST = new HashMap<>();

    public static void register() {
        GlyphRegistry.register(new CityEffect(HASTEN, 14, 2, 1));
        GlyphRegistry.register(new CityEffect(SHAPE, 18, 3, 2));
        GlyphRegistry.register(new CityEffect(ERECT, 30, 4, 3));
    }

    /** Um efeito de obra: vale em bloco ou criatura (o ponto atingido é o que conta). */
    private static final class CityEffect extends EffectGlyph {
        private final String kind;

        CityEffect(String id, int cost, int tier, int minCircle) {
            super(id, cost);
            this.kind = id;
            tier(tier);
            minCircle(minCircle);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            cast(kind, ctx, hit.getLocation(), stats);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            cast(kind, ctx, target.position(), stats);
        }

        @Override
        public float baseDamage() {
            return 0F;
        }

        @Override
        public float baseHeal() {
            return 0F;
        }

        @Override
        public int hintDuration() {
            return HASTEN.equals(kind) ? 240 : -1;
        }

        @Override
        public float hintRadius() {
            return HASTEN.equals(kind) ? 16F : -1F;
        }
    }

    // =====================================================================================
    //  Regras
    // =====================================================================================
    /** Tamanho (blocos do molde) da maior obra que o Erguer Obra terminava na hora (antes da onda 6; sem uso hoje). */
    public static int erectLimit(int circle) {
        return 300 + 180 * Math.max(1, circle);
    }

    /** Onda 6: dias de obra (nominais) que o Erguer Obra faz de uma vez — cresce com o Círculo, nunca um dia inteiro. */
    public static float erectDays(int circle) {
        return Math.min(CityPace.MAGIC_DAILY, 0.25F + 0.05F * Math.max(1, circle));
    }

    /** Quantos blocos o Moldar Lote sobe/baixa por conjuração. */
    public static int shapeLimit(int circle) {
        return 1 + Math.max(1, circle) / 4;
    }

    /** Multiplicador do Impulso de Obra (Círculo e nível do conjurador; aumentos entram em {@code power}). */
    public static float hastenMult(MagicData d, float power) {
        return Math.min(MAX_HASTEN, 1F + (0.5F + 0.1F * d.circle() + d.level() / 200F) * Math.max(0.25F, power));
    }

    private static void cast(String kind, SpellContext ctx, Vec3 at, SpellStats stats) {
        ServerPlayer sp = ctx.casterPlayer();
        if (sp == null || sp instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
        ServerLevel sl = ctx.level();
        long now = sl.getGameTime();
        String key = sp.getUUID() + kind;
        Long last = LAST.get(key);
        if (last != null && now - last < 5) return;
        if (LAST.size() > 512) LAST.clear();
        LAST.put(key, now);
        if (sl.dimension().location().equals(HOME_REALM)) return;
        Settlement s = cityFor(sp, sl, BlockPos.containing(at));
        if (s == null) return;
        switch (kind) {
            case HASTEN -> hasten(sp, sl, s, at, stats, ctx.powerMult());
            case ERECT -> erect(sp, sl, s, at, stats);
            default -> shape(sp, sl, s, at, stats);
        }
    }

    /** A cidade fundada do ponto, se o jogador pode planejá-la (avisa quando não pode). */
    @Nullable
    private static Settlement cityFor(ServerPlayer sp, ServerLevel sl, BlockPos pos) {
        Places.Place p = SettlementService.cityAt(sl, pos);
        Settlement s = p == null ? null : SettlementService.city(sl.getServer(), p.id());
        if (s == null || !s.own() || s.dim != sl.dimension()) {
            warn(sp, "magic.no_city");
            return null;
        }
        if (!s.canPlan(sp.getUUID())) {
            warn(sp, "magic.not_yours", SettlementService.cityName(sl.getServer(), s));
            return null;
        }
        return s;
    }

    private static void warn(ServerPlayer sp, String key, Object... args) {
        sp.displayClientMessage(SettlementsLang.tr(key, args).withColor(0xE8735E), true);
    }

    private static void ok(ServerPlayer sp, Settlement s, String key, Object... args) {
        sp.displayClientMessage(SettlementsLang.tr(key, args).withColor(s.style.color), true);
    }

    private static double flatDist(Project.Piece piece, Vec3 at) {
        double dx = piece.cx + 0.5 - at.x, dz = piece.cz + 0.5 - at.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // =====================================================================================
    //  Impulso de Obra
    // =====================================================================================
    private static void hasten(ServerPlayer sp, ServerLevel sl, Settlement s, Vec3 at, SpellStats stats, float powerMult) {
        MagicData d = MagicApi.get(sp);
        float mult = hastenMult(d, stats.power() * powerMult);
        int ticks = Math.min(MAX_HASTEN_TICKS, stats.duration(240));
        double reach = stats.radius(16F) + 8;
        long until = sl.getGameTime() + ticks;
        // só as obras que já têm construtor (as primeiras da fila): a magia acelera quem está trabalhando
        // onda 7 (city_pause): obra pausada não tem equipe — a magia vai para as que estão andando
        java.util.List<Project> crewed = com.renanpontes.isekaimahou.feature.settlements.CityPause.crewed(s);
        int n = 0, capped = 0;
        for (int i = 0; i < crewed.size(); i++) {
            Project p = crewed.get(i);
            Project.Piece piece = p.current();
            if (piece == null || p.undo || flatDist(piece, at) > reach) continue;
            // onda 6 (city_pace): a obra leva dias — a magia adianta só uma fração, com limite por obra por dia
            CityPace.ensure(sl.getServer(), s, p);
            if (CityPace.magicLeft(sl.getServer(), p) <= 0.001F) {
                capped++;
                continue;
            }
            boolean running = p.magicUntil > sl.getGameTime();
            p.magicMult = running ? Math.max(p.magicMult, mult) : mult;
            p.magicUntil = Math.max(p.magicUntil, until);
            n++;
            if (piece.resolved()) {
                Vec3 c = new Vec3(piece.cx + 0.5, piece.baseY + 1.05, piece.cz + 0.5);
                ElementFx.runeCircle(sl, c, COLOR, 6F, Math.min(ticks, 200), 2);
            }
        }
        if (n == 0) {
            warn(sp, capped > 0 ? "pace.magic.limit" : "magic.no_site");
            return;
        }
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.9F, 1.3F);
        ok(sp, s, "magic.hasten", n, Math.round((mult - 1F) * 100), ticks / 20);
    }

    // =====================================================================================
    //  Erguer Obra
    // =====================================================================================
    private static void erect(ServerPlayer sp, ServerLevel sl, Settlement s, Vec3 at, SpellStats stats) {
        Project best = null;
        double bestD = stats.radius(12F) + 8;
        for (Project p : s.projects) {
            Project.Piece piece = p.current();
            if (piece == null) continue;
            double dist = flatDist(piece, at);
            if (dist < bestD) {
                bestD = dist;
                best = p;
            }
        }
        if (best == null) {
            warn(sp, "magic.no_site");
            return;
        }
        Project p = best;
        MagicData d = MagicApi.get(sp);
        Project.Piece piece = p.current();
        Templates.Tpl tpl = piece == null ? null : Templates.get(sl, piece.template);
        if (tpl == null) {
            warn(sp, "magic.no_site");
            return;
        }
        if (ConstructionEngine.waiting(s, p)) {
            warn(sp, "magic.waiting");
            return;
        }
        // a magia termina o que os construtores já fariam: sem construtor na cidade, ou com o povo na miséria, ela não pega
        if (s.population(Settlement.Role.BUILDER) < 1) {
            warn(sp, "magic.no_builders");
            return;
        }
        if (s.affinity < CityHappiness.MISERABLE) {
            warn(sp, "magic.unhappy");
            return;
        }
        if (!sl.hasChunk(piece.cx >> 4, piece.cz >> 4)) {
            warn(sp, "plan.unloaded");
            return;
        }
        // onda 6 (city_pace): a obra leva DIAS — Erguer Obra faz de uma vez uma fração de dia (mais com o Círculo),
        // dentro do limite diário de magia da obra. A espera continua; a magia só adianta.
        CityPace.ensure(sl.getServer(), s, p);
        // onda 7 (city_pause): obra pausada (ou sendo desmontada) não aceita magia de obra — sem gastar mana
        if (p.paused || p.undo) {
            warn(sp, "pause.magic.paused");
            return;
        }
        if (CityPace.magicLeft(sl.getServer(), p) <= 0.001F) {
            warn(sp, "pace.magic.limit");
            return;
        }
        int cost = Mth.ceil(d.maxMana() * ERECT_MANA);
        if (!d.consumeMana(cost)) {
            warn(sp, "magic.need_mana", cost);
            return;
        }
        float got = CityPace.magicWork(sl.getServer(), s, p, erectDays(d.circle()));
        Vec3 c = new Vec3(piece.cx + 0.5, (piece.resolved() ? piece.baseY : s.heart.getY()) + 1.05, piece.cz + 0.5);
        ElementFx.runeCircle(sl, c, COLOR, 8F, 50, 4);
        ElementFx.pillar(sl, c, COLOR, 3F, Math.max(6, tpl.sy() + 4), 30);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.4F, 1.2F);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 0.8F);
        ok(sp, s, "pace.magic.erect", CityPlanning.projectName(s, p), Math.max(1, Math.round(got / Math.max(0.01F, p.days) * 100)), cost);
        SettlementService.refreshViewers(sl.getServer(), s);
    }

    // =====================================================================================
    //  Moldar Lote
    // =====================================================================================
    private static void shape(ServerPlayer sp, ServerLevel sl, Settlement s, Vec3 at, SpellStats stats) {
        BlockPos pos = BlockPos.containing(at);
        int cell = CityPlots.plotAt(s, pos.getX(), pos.getZ());
        if (cell < 0) {
            warn(sp, "magic.no_plot");
            return;
        }
        // lote livre só existe com prédio ou obra; o da grade pode estar com prédio
        if (CityLayout.isFree(cell) || s.buildings.containsKey(cell)) {
            warn(sp, "plan.terrain_occupied");
            return;
        }
        MagicData d = MagicApi.get(sp);
        int step = shapeLimit(d.circle());
        int delta = Mth.clamp(stats.amplify, -step, step);
        Integer cur = s.plotBase.get(cell);
        if (cur == null) cur = CityTerrain.streetLevel(sl, s, cell);
        if (cur == null) {
            warn(sp, "plan.unloaded");
            return;
        }
        int plaza = s.heart.getY() - 1;
        int y = Mth.clamp(cur + delta, plaza - CityPlanning.MAX_TERRAIN_OFFSET, plaza + CityPlanning.MAX_TERRAIN_OFFSET);
        if (delta != 0 && y == cur) {
            warn(sp, "magic.shape_limit", CityPlanning.MAX_TERRAIN_OFFSET);
            return;
        }
        int cost = Mth.ceil(d.maxMana() * (SHAPE_MANA + SHAPE_STEP_MANA * Math.abs(y - cur)));
        if (d.mana() < cost) {
            warn(sp, "magic.need_mana", cost);
            return;
        }
        Project p = CityPlanning.terrainByMagic(sp, s, cell, y);
        if (p == null) return;
        d.consumeMana(cost);
        BlockPos center = CityLayout.cellCenter(s.heart, cell);
        Vec3 c = new Vec3(center.getX() + 0.5, y + 1.05, center.getZ() + 0.5);
        // terreno ainda não carregado: a obra fica na fila e os construtores a fazem
        ConstructionEngine.finishOne(sl, s, p);
        ElementFx.runeCircle(sl, c, COLOR, 8F, 40, 3);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1.4F, 0.7F);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.9F);
        int off = y - plaza;
        ok(sp, s, y == cur ? "magic.shape.level" : y > cur ? "magic.shape.raise" : "magic.shape.lower",
                off >= 0 ? "+" + off : Integer.toString(off), cost);
        SettlementService.refreshViewers(sl.getServer(), s);
    }

    // =====================================================================================
    //  Dica e limpeza
    // =====================================================================================
    /** Primeira obra planejada por quem já conhece o Impulso de Obra: lembra que a magia ajuda. */
    public static void hint(ServerPlayer sp) {
        if (Hints.seen(sp, "city_magic_build")) return;
        if (MagicApi.get(sp).knowsGlyph(IsekaiMahou.id(HASTEN))) Hints.show(sp, "city_magic_build");
    }

    /**
     * Depuração ({@code /mahou city magic ...}): roda o efeito no ponto onde o jogador está, sem precisar montar a magia.
     * As regras são as mesmas da magia de verdade (cidade dele, mana, Círculo, requisitos da cidade).
     * {@code amplify} = quantos Amplificar (positivo) ou Enfraquecer (negativo) contam.
     */
    public static void debug(ServerPlayer sp, String kind, int amplify) {
        ServerLevel sl = sp.serverLevel();
        if (sl.dimension().location().equals(HOME_REALM)) return;
        Settlement s = cityFor(sp, sl, sp.blockPosition());
        if (s == null) return;
        SpellStats stats = new SpellStats();
        stats.amplify = amplify;
        switch (kind) {
            case HASTEN -> hasten(sp, sl, s, sp.position(), stats, 1F);
            case ERECT -> erect(sp, sl, s, sp.position(), stats);
            default -> shape(sp, sl, s, sp.position(), stats);
        }
    }

    public static void forget(UUID player) {
        String id = player.toString();
        LAST.keySet().removeIf(k -> k.startsWith(id));
    }

    public static void clear() {
        LAST.clear();
    }

    private CityMagic() {}
}
