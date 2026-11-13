package com.renanpontes.isekaimahou.feature.city_arcana;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.settlements.CityRaids;
import com.renanpontes.isekaimahou.feature.settlements.SettlementService;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.LawGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * DEFESA MONTADA COMO UM DOMÍNIO (release 1.0, onda 4 — "city_magic"): o Senhor grava na Barreira Arcana uma RECEITA
 * (efeitos + leis, montada no Grimório como qualquer magia) e, durante um CERCO (ataque de monstros à cidade ou
 * jogador inimigo dentro do território), a Barreira a aplica nos invasores a cada pulso (2 s), gastando a mana do
 * NÚCLEO DE MANA — o reservatório da Barreira (150 × nível + 100 × níveis de Torre de Mana), que se enche sozinho
 * devagar e com a mana que o Senhor doar.
 * <p>Como gravar: clicar no catalisador (a coluna central da Barreira) com um Pergaminho de Magia, ou clicar de mão
 * vazia e escolher um dos slots Z/X/C/G no chat ({@code /mahou city_arcana ward record <1-4>}). A receita tem de ser
 * uma que o Senhor consegue conjurar (glifos conhecidos, Círculo, complexidade): defesa forte vem com Círculo alto.
 * <p><b>Nunca atinge moradores, aliados nem blocos</b>:
 * <ul>
 *   <li>a forma da receita é ignorada: os efeitos caem direto em cada INVASOR ({@link CityArcana#hostile}), nunca em área;</li>
 *   <li>efeitos de bloco ({@code wantsBlocks}) são recusados ao gravar; Atraso/Eco/Encadear/Dividir são ignorados;</li>
 *   <li>todo dano que saia da receita contra quem não é invasor é cancelado ({@link #onSpellDamage});</li>
 *   <li>enquanto os efeitos rodam: explosões não quebram blocos, nenhuma entidade nova nasce (raios, projéteis, zonas),
 *       status não pegam em quem a cidade protege, e os blocos em volta do alvo são conferidos e devolvidos se algum
 *       efeito os mudou — esse efeito fica banido da Barreira até o servidor reiniciar;</li>
 *   <li>das Leis de Domínio só valem as de status (Território, Anulação, Tempo Lento, Gravidade, Acerto Garantido,
 *       Absoluto); Paisagem, Ruína, Sem Fuga e Barreira Aberta não fazem nada aqui.</li>
 * </ul>
 * Chefes nunca são alvo. A receita não dá XP, carga nem maestria a ninguém (não tem conjurador).
 */
public final class WardRecipe {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();
    private static final String SURE_HIT = "law_sure_hit", TERRITORY = "law_territory", NULLIFY = "law_nullify",
            SLOW = "law_slow_time", GRAVITY = "law_gravity", ABSOLUTE = "law_absolute";
    private static final Set<String> KNOWN_LAWS = Set.of(SURE_HIT, TERRITORY, NULLIFY, SLOW, GRAVITY, ABSOLUTE);
    private static final ResourceLocation GRAVITY_EFFECT = IsekaiMahou.id("domain_gravity");
    /** Meia-caixa conferida em volta do alvo (blocos mudados por um efeito voltam ao que eram). */
    private static final int SNAP_H = 3, SNAP_DOWN = 2, SNAP_UP = 3;
    private static final int STATUS_TICKS = 60;

    /** Cidade cujos efeitos estão rodando AGORA (só na thread do servidor); null fora disso. */
    @Nullable private static CityArcana.City applying;
    private static final List<Entity> SPAWNED_DROPS = new ArrayList<>();
    /** Efeitos que mexeram em blocos ou quebraram ao rodar sem conjurador: fora da Barreira nesta sessão. */
    private static final Set<ResourceLocation> BANNED = new HashSet<>();
    /** "Conjurador" de mentira de cada Barreira (UUID fixo por cidade) → id da cidade. */
    private static final Map<UUID, String> WARD_IDS = new HashMap<>();
    /** Avisos por cidade: [último disparo, último aviso de Núcleo vazio]. */
    private static final Map<String, long[]> NOTICE = new HashMap<>();

    /** Receita pronta para um pulso. */
    private record Loaded(SpellContext ctx, List<ParsedSpell.EffectEntry> effects, Set<String> laws, int perTarget,
                          int color, Component name) {
        boolean law(String id) {
            return laws.contains(id);
        }
    }

    // =====================================================================================
    //  Núcleo de Mana
    // =====================================================================================
    /** Quanto de mana cabe no Núcleo. */
    public static float capacity(CityArcana.City c) {
        return 150F * c.ward + 100F * c.spireTotal;
    }

    /** Poder dos efeitos pelo nível (efetivo) da Barreira: 52% no nível 1 → 100% no 5. */
    public static float power(int wardLevel) {
        return 0.4F + 0.12F * Math.max(1, Math.min(5, wardLevel));
    }

    public static UUID wardId(String city) {
        return UUID.nameUUIDFromBytes(("isekai_mahou:arcane_ward:" + city).getBytes(StandardCharsets.UTF_8));
    }

    /** A cada 100 ticks: o Núcleo se enche devagar (1% + 0,5 por nível de Torre de Mana) e a dica aparece para o Senhor. */
    static void regen(MinecraftServer server) {
        CityArcanaData data = CityArcanaData.get(server);
        for (CityArcana.City c : CityArcana.cities()) {
            if (c.ward <= 0) continue;
            float cap = capacity(c), m = data.wardMana(c.s.id);
            if (m < cap) data.setWardMana(c.s.id, Math.min(cap, m + cap * 0.01F + c.spireTotal * 0.5F));
            else if (m > cap) data.setWardMana(c.s.id, cap);
            ServerPlayer lord = c.s.owner == null ? null : server.getPlayerList().getPlayer(c.s.owner);
            if (lord != null && !Hints.seen(lord, "city_arcana_ward") && CityArcana.at(lord) == c) Hints.show(lord, "city_arcana_ward");
        }
    }

    /** O Senhor (ou quem a cidade protege) doa a própria mana ao Núcleo, 1 para 1. */
    public static void pour(ServerPlayer sp, CityArcana.City c) {
        CityArcanaData data = CityArcanaData.get(sp.server);
        MagicData d = MagicApi.get(sp);
        float cap = capacity(c), m = data.wardMana(c.s.id);
        float give = (float) Math.floor(Math.min(d.mana(), cap - m));
        if (give < 1F) {
            sp.displayClientMessage(tr(cap - m < 1F ? "ward.pour.full" : "ward.pour.none").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        d.consumeMana(give);
        data.setWardMana(c.s.id, m + give);
        sp.displayClientMessage(tr("ward.pour", Math.round(give), Math.round(m + give), Math.round(cap))
                .withColor(CityArcana.wardColor(c)), true);
        CityArcana.Site site = c.site(CityArcana.WARD);
        if (site != null) {
            Vec3 at = Vec3.atBottomCenterOf(site.center()).add(0, 2, 0);
            CityArcanaNet.send(sp.serverLevel(), CityArcanaNet.MANA, at, Vec3.ZERO, 1F, 0xC08CFF);
            sp.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
    }

    // =====================================================================================
    //  Gravar / apagar / mostrar
    // =====================================================================================
    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("city_arcana.isekai_mahou." + key, args);
    }

    private static boolean isLord(ServerPlayer sp, CityArcana.City c) {
        return sp.getUUID().equals(c.s.owner) || sp.hasPermissions(2);
    }

    private static boolean refuse(ServerPlayer sp, Component why) {
        sp.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), false);
        sp.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 1.4F);
        return false;
    }

    /** Efeito que a Barreira aplica: ofensivo (ou invertido), sem ser de bloco e sem estar banido. */
    private static boolean usable(ParsedSpell.EffectEntry e) {
        EffectGlyph g = e.effect();
        return !g.wantsBlocks() && !BANNED.contains(g.id()) && (!g.isBeneficial() || e.stats().inverted);
    }

    /** Grava a receita na Barreira da cidade (só o Senhor; a receita tem de ser conjurável por ele). */
    public static boolean record(ServerPlayer sp, CityArcana.City c, @Nullable SpellRecipe recipe) {
        if (!isLord(sp, c)) return refuse(sp, tr("ward.not_lord", SettlementService.cityName(sp.server, c.s)));
        if (recipe == null || recipe.isEmpty()) return refuse(sp, tr("ward.recipe.no_spell"));
        Element el = SpellCost.primaryElement(recipe);
        SpellParser.Result r = SpellParser.parse(recipe, el, MagicApi.get(sp));
        if (!r.ok()) return refuse(sp, tr("ward.recipe.invalid", r.error()));
        ParsedSpell spell = r.spell();
        int used = 0;
        for (ParsedSpell.EffectEntry e : spell.effects()) {
            if (e.effect().wantsBlocks() || BANNED.contains(e.effect().id())) {
                return refuse(sp, tr("ward.recipe.blocks", e.effect().displayName()));
            }
            if (usable(e)) used++;
        }
        List<Component> ignored = new ArrayList<>();
        int laws = 0;
        for (LawGlyph law : spell.laws()) {
            if (KNOWN_LAWS.contains(law.id().getPath())) laws++;
            else ignored.add(law.displayName());
        }
        if (used == 0 && laws == 0) return refuse(sp, tr("ward.recipe.empty"));
        Tag encoded = SpellRecipe.CODEC.encodeStart(NbtOps.INSTANCE, recipe).result().orElse(null);
        if (encoded == null) return refuse(sp, tr("ward.recipe.no_spell"));
        CityArcanaData.get(sp.server).setWardRecipe(c.s.id, encoded, el.id, sp.getGameProfile().getName());
        ServerLevel sl = sp.serverLevel();
        sp.displayClientMessage(tr("ward.recipe.set", SettlementService.cityName(sp.server, c.s), nameOf(recipe))
                .withColor(CityArcana.wardColor(c)), false);
        if (!ignored.isEmpty()) sp.displayClientMessage(tr("ward.recipe.law_ignored", join(ignored)).withStyle(ChatFormatting.GRAY), false);
        Vec3 center = Vec3.atBottomCenterOf(c.s.heart);
        CityArcanaNet.send(sl, CityArcanaNet.DOME, center, Vec3.ZERO, c.halfSize(), CityArcana.wardColor(c));
        sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.4F, 1.1F);
        status(sp, c);
        return true;
    }

    public static void erase(ServerPlayer sp, CityArcana.City c) {
        if (!isLord(sp, c)) {
            refuse(sp, tr("ward.not_lord", SettlementService.cityName(sp.server, c.s)));
            return;
        }
        CityArcanaData.get(sp.server).clearWardRecipe(c.s.id);
        sp.displayClientMessage(tr("ward.recipe.cleared").withStyle(ChatFormatting.GRAY), false);
    }

    private static Component nameOf(SpellRecipe recipe) {
        return recipe.name().isBlank() ? tr("ward.recipe.unnamed") : Component.literal(recipe.name());
    }

    private static Component join(List<Component> parts) {
        if (parts.isEmpty()) return Component.literal("—");
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(parts.get(i));
        }
        return out;
    }

    /** Estado da Barreira no chat; para o Senhor, com os botões de gravar (slots), doar mana e apagar. */
    public static void status(ServerPlayer sp, CityArcana.City c) {
        CityArcanaData data = CityArcanaData.get(sp.server);
        int color = CityArcana.wardColor(c);
        sp.displayClientMessage(tr("ward.core", SettlementService.cityName(sp.server, c.s), Math.round(data.wardMana(c.s.id)),
                Math.round(capacity(c))).withColor(color), false);
        ServerLevel sl = sp.server.getLevel(c.dim);
        Loaded l = sl == null ? null : load(sl, c, data.ward(c.s.id));
        if (l == null) {
            sp.displayClientMessage(tr("ward.recipe.none").withStyle(ChatFormatting.GRAY), false);
        } else {
            List<Component> fx = new ArrayList<>(), laws = new ArrayList<>();
            for (ParsedSpell.EffectEntry e : l.effects()) fx.add(e.effect().displayName());
            for (LawGlyph law : l.ctx().spell().laws()) if (l.law(law.id().getPath())) laws.add(law.displayName());
            sp.displayClientMessage(tr("ward.recipe.status", l.name(), join(fx), join(laws), l.perTarget())
                    .withStyle(ChatFormatting.GRAY), false);
        }
        if (!isLord(sp, c)) return;
        MutableComponent row = tr("ward.buttons").withStyle(ChatFormatting.GRAY);
        String[] keys = {"Z", "X", "C", "G"};
        for (int i = 0; i < keys.length; i++) {
            row.append(" ").append(button(Component.literal("[" + keys[i] + "]"), "/mahou city_arcana ward record " + (i + 1),
                    tr("ward.button.record", keys[i]), color));
        }
        row.append("  ").append(button(tr("ward.button.pour"), "/mahou city_arcana ward pour", tr("ward.button.pour.tip"), 0xC08CFF));
        if (l != null) {
            row.append("  ").append(button(tr("ward.button.clear"), "/mahou city_arcana ward clear", tr("ward.button.clear.tip"), 0xE8735E));
        }
        sp.displayClientMessage(row, false);
    }

    private static Component button(Component label, String command, Component tip, int color) {
        return label.copy().withStyle(s -> s.withColor(color).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tip)));
    }

    /** Clique no catalisador (coluna central da Barreira): pergaminho = gravar; agachado = doar mana; senão, o estado. */
    static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer sp)) return;
        if (CityArcana.cities().isEmpty()) return;
        ServerLevel sl = sp.serverLevel();
        BlockPos pos = e.getPos();
        CityArcana.City c = CityArcana.at(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (c == null || c.ward <= 0) return;
        CityArcana.Site site = c.site(CityArcana.WARD);
        if (site == null) return;
        BlockPos center = site.center();
        int dy = pos.getY() - center.getY();
        if (Math.abs(pos.getX() - center.getX()) > 1 || Math.abs(pos.getZ() - center.getZ()) > 1 || dy < 1 || dy > 20) return;
        if (sl.getBlockState(pos).isAir()) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        SpellRecipe held = sp.getMainHandItem().get(CoreRegistries.SPELL.get());
        if (held != null && !held.isEmpty()) record(sp, c, held);
        else if (sp.isShiftKeyDown() && CityArcana.friendly(c, sp)) pour(sp, c);
        else status(sp, c);
    }

    // =====================================================================================
    //  Pulso do cerco (a cada 40 ticks)
    // =====================================================================================
    @Nullable
    private static Loaded load(ServerLevel sl, CityArcana.City c, CompoundTag ward) {
        if (!ward.contains("Recipe")) return null;
        UUID id = wardId(c.s.id);
        WARD_IDS.put(id, c.s.id);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("caster", id);
        tag.putString("element", ward.getString("Element"));
        tag.put("recipe", ward.get("Recipe").copy());
        tag.putFloat("power", power(c.ward));
        SpellContext ctx;
        try {
            ctx = SpellContext.load(sl, tag);
        } catch (RuntimeException ex) {
            LOG.warn("Receita da Barreira de {} não carregou", c.s.id, ex);
            return null;
        }
        if (ctx == null) return null;
        List<ParsedSpell.EffectEntry> effects = new ArrayList<>();
        float cost = 0F;
        for (ParsedSpell.EffectEntry e : ctx.spell().effects()) {
            if (!usable(e)) continue;
            // sem Atraso/Eco/Encadear/Dividir: o efeito cai uma vez, agora, só neste invasor
            SpellStats st = e.stats().copy();
            st.delay = 0;
            st.repeat = 0;
            st.chain = 0;
            st.split = 0;
            st.channel = false;
            st.custom.remove(SpellStats.RELENTLESS);
            effects.add(new ParsedSpell.EffectEntry(e.effect(), st));
            cost += Math.max(1, e.effect().cost()) * Math.max(1F, st.power());
        }
        Set<String> laws = new HashSet<>();
        for (LawGlyph law : ctx.spell().laws()) if (KNOWN_LAWS.contains(law.id().getPath())) laws.add(law.id().getPath());
        if (effects.isEmpty() && laws.isEmpty()) return null;
        int perTarget = Math.max(2, Math.round(cost * 0.2F) + 3 * laws.size());
        SpellRecipe recipe = ctx.recipe();
        return new Loaded(ctx, effects, laws, perTarget, ctx.color() & 0xFFFFFF, nameOf(recipe));
    }

    /** Há jogador inimigo (guerra, toque de recolher) dentro do território? */
    private static boolean enemyPlayerInside(MinecraftServer server, CityArcana.City c) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() == c.dim && !p.isSpectator() && c.contains(p.getX(), p.getY(), p.getZ())
                    && CityArcana.hostile(c, p)) return true;
        }
        return false;
    }

    static void pulse(MinecraftServer server) {
        if (CityArcana.cities().isEmpty()) return;
        CityArcanaData data = CityArcanaData.get(server);
        for (CityArcana.City c : CityArcana.cities()) {
            if (c.ward <= 0 || !data.ward(c.s.id).contains("Recipe")) continue;
            boolean raid = CityRaids.active(c.s.id);
            if (!raid && !enemyPlayerInside(server, c)) continue;
            fire(server, data, c, raid, false);
        }
    }

    /**
     * Um pulso da receita. {@code raid} = há ataque de monstros (todo invasor é alvo; sem ele, só jogadores inimigos e
     * criaturas de horda). {@code test} = comando de op: qualquer inimigo vale. Devolve quantos invasores atingiu.
     */
    static int fire(MinecraftServer server, CityArcanaData data, CityArcana.City c, boolean raid, boolean test) {
        CityArcana.Site site = c.site(CityArcana.WARD);
        ServerLevel sl = server.getLevel(c.dim);
        if (site == null || sl == null || !sl.isLoaded(site.center())) return 0;
        Loaded l = load(sl, c, data.ward(c.s.id));
        if (l == null) return 0;
        long now = sl.getGameTime();
        long[] notice = NOTICE.computeIfAbsent(c.s.id, k -> new long[] {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2});
        float mana = data.wardMana(c.s.id);
        if (mana < l.perTarget()) {
            if (now - notice[1] > 1200) {
                notice[1] = now;
                tellLord(server, c, tr("ward.dry", SettlementService.cityName(server, c.s)).withStyle(ChatFormatting.RED));
            }
            return 0;
        }
        int lv = c.ward;
        boolean sure = l.law(SURE_HIT);
        AABB box;
        if (sure) {
            box = new AABB(c.minX, c.minY, c.minZ, c.maxX + 1, c.maxY + 1, c.maxZ + 1);
        } else {
            double r = ArcaneWard.pulseRadius(lv) * 1.5;
            box = new AABB(site.center()).inflate(r, 16, r);
        }
        int cap = sure ? 6 + 2 * lv : 3 + lv;
        Vec3 crystal = Vec3.atBottomCenterOf(site.center()).add(0, 4 + 2 * lv + 2, 0);
        int hit = 0;
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && CityArcana.hostile(c, x))) {
            if (hit >= cap || mana < l.perTarget()) break;
            if (!c.contains(le.getX(), le.getY(), le.getZ()) || CityArcana.isBoss(le)) continue;
            if (!raid && !test && !(le instanceof ServerPlayer) && !le.getTags().contains(CityRaids.TAG)) continue;
            if (le instanceof ServerPlayer p && (p.isSpectator() || p.isCreative() && !CityArcana.DEBUG_OUTSIDER.contains(p.getUUID()))) continue;
            if (CityArcana.inBossArena(sl, le.position())) continue;
            mana -= l.perTarget();
            CityArcanaNet.send(sl, CityArcanaNet.ZAP, crystal, le.position().add(0, le.getBbHeight() * 0.5, 0), 1F, l.color());
            apply(sl, c, l, le);
            hit++;
        }
        if (hit == 0) return 0;
        data.setWardMana(c.s.id, mana);
        sl.playSound(null, crystal.x, crystal.y, crystal.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2F, 1.5F);
        if (now - notice[0] > 1200) tellLord(server, c, tr("ward.fire", l.name()).withColor(CityArcana.wardColor(c)));
        notice[0] = now;
        return hit;
    }

    private static void tellLord(MinecraftServer server, CityArcana.City c, Component text) {
        ServerPlayer lord = c.s.owner == null ? null : server.getPlayerList().getPlayer(c.s.owner);
        if (lord != null) lord.displayClientMessage(text, true);
    }

    private static void give(LivingEntity e, Holder<MobEffect> effect, int amp) {
        e.addEffect(new MobEffectInstance(effect, STATUS_TICKS, amp, true, true, true));
    }

    private static void apply(ServerLevel sl, CityArcana.City c, Loaded l, LivingEntity le) {
        // ---- leis (só status no invasor)
        int abs = l.law(ABSOLUTE) ? 1 : 0;
        if (l.law(TERRITORY)) {
            give(le, CoreRegistries.EXPOSED, abs);
            give(le, MobEffects.WEAKNESS, abs);
        }
        if (l.law(NULLIFY)) give(le, CoreRegistries.SILENCED, 0);
        if (l.law(SLOW)) give(le, MobEffects.MOVEMENT_SLOWDOWN, 2 + 2 * abs);
        if (l.law(GRAVITY)) {
            BuiltInRegistries.MOB_EFFECT.getHolder(GRAVITY_EFFECT).ifPresent(h -> give(le, h, abs));
            le.removeEffect(MobEffects.LEVITATION);
            le.removeEffect(MobEffects.SLOW_FALLING);
        }
        if (l.effects().isEmpty()) return;
        // ---- efeitos, dentro da "redoma": nada de blocos, entidades novas ou status em quem a cidade protege
        BlockPos origin = le.blockPosition();
        BlockState[] before = snapshot(sl, origin);
        applying = c;
        try {
            for (ParsedSpell.EffectEntry e : l.effects()) {
                if (!le.isAlive()) break;
                ResourceLocation id = e.effect().id();
                if (BANNED.contains(id)) continue;
                SPAWNED_DROPS.clear();
                try {
                    l.ctx().applyEffect(e, le);
                } catch (RuntimeException ex) {
                    BANNED.add(id);
                    LOG.warn("Barreira Arcana: o efeito {} falhou sem conjurador e saiu da defesa das cidades", id, ex);
                }
                if (restore(sl, origin, before)) {
                    BANNED.add(id);
                    for (Entity drop : SPAWNED_DROPS) drop.discard();
                    LOG.warn("Barreira Arcana: o efeito {} mexeu em blocos e saiu da defesa das cidades", id);
                }
            }
        } finally {
            applying = null;
            SPAWNED_DROPS.clear();
        }
    }

    private static BlockState[] snapshot(ServerLevel sl, BlockPos o) {
        int side = 2 * SNAP_H + 1, tall = SNAP_DOWN + SNAP_UP + 1;
        BlockState[] out = new BlockState[side * side * tall];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int y = -SNAP_DOWN; y <= SNAP_UP; y++) {
            for (int x = -SNAP_H; x <= SNAP_H; x++) {
                for (int z = -SNAP_H; z <= SNAP_H; z++) {
                    p.set(o.getX() + x, o.getY() + y, o.getZ() + z);
                    out[i++] = sl.isLoaded(p) ? sl.getBlockState(p) : null;
                }
            }
        }
        return out;
    }

    /** Devolve os blocos que mudaram desde a foto. True se algum tinha mudado. */
    private static boolean restore(ServerLevel sl, BlockPos o, BlockState[] before) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean changed = false;
        int i = 0;
        for (int y = -SNAP_DOWN; y <= SNAP_UP; y++) {
            for (int x = -SNAP_H; x <= SNAP_H; x++) {
                for (int z = -SNAP_H; z <= SNAP_H; z++) {
                    BlockState old = before[i++];
                    if (old == null) continue;
                    p.set(o.getX() + x, o.getY() + y, o.getZ() + z);
                    if (sl.getBlockState(p) == old) continue;
                    sl.setBlock(p, old, 2 | 16);
                    changed = true;
                }
            }
        }
        return changed;
    }

    // =====================================================================================
    //  A "redoma": o que a receita NÃO pode fazer
    // =====================================================================================
    /** Dano de uma receita de Barreira em quem não é invasor (ou num chefe): cancelado — vale também para efeitos tardios. */
    static void onSpellDamage(SpellDamageEvent.Pre e) {
        UUID id = e.ctx().casterId();
        if (id == null || WARD_IDS.isEmpty()) return;
        String city = WARD_IDS.get(id);
        if (city == null) return;
        CityArcana.City c = CityArcana.byId(city);
        if (c == null || !CityArcana.hostile(c, e.target()) || CityArcana.isBoss(e.target())) e.setCanceled(true);
    }

    /** Enquanto os efeitos rodam, só criaturas e os drops de quem morreu podem nascer (nada de raio, projétil, zona...). */
    static void onJoin(EntityJoinLevelEvent e) {
        if (applying == null || e.getLevel().isClientSide()) return;
        Entity en = e.getEntity();
        if (en instanceof ItemEntity || en instanceof ExperienceOrb) SPAWNED_DROPS.add(en);
        else if (!(en instanceof LivingEntity)) e.setCanceled(true);
    }

    /** Explosão causada por um efeito da receita: não quebra blocos nem pega quem não é invasor. */
    static void onDetonate(ExplosionEvent.Detonate e) {
        CityArcana.City c = applying;
        if (c == null || e.getLevel().isClientSide()) return;
        e.getAffectedBlocks().clear();
        e.getAffectedEntities().removeIf(x -> !CityArcana.hostile(c, x));
    }

    /** Status espirrado por um efeito da receita não pega em quem não é invasor. */
    static void onEffect(MobEffectEvent.Applicable e) {
        CityArcana.City c = applying;
        if (c == null || e.getEntity().level().isClientSide()) return;
        if (!CityArcana.hostile(c, e.getEntity())) e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    static void clear() {
        applying = null;
        SPAWNED_DROPS.clear();
        BANNED.clear();
        WARD_IDS.clear();
        NOTICE.clear();
    }

    private WardRecipe() {}
}
