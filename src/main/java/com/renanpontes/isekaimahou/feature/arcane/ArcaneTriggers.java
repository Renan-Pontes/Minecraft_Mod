package com.renanpontes.isekaimahou.feature.arcane;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellSlot;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

/**
 * Gatilhos condicionais e de sequência (vão no começo da receita).
 * <ul>
 *   <li><b>Reativos</b> (Ao Receber Dano, Vida Baixa, Ao Matar, Ao Acertar Crítico): quando a condição acontece, os slots do
 *       loadout ativo do elemento ativo cuja receita principal começa com o gatilho são conjurados sozinhos (na hora, sem tempo de
 *       conjuração, mas pagando mana e respeitando recarga/silêncio). Apertar a tecla desses slots não conjura: protege a magia de
 *       emergência de ser gasta sem querer.</li>
 *   <li><b>Sequência</b>: Depois (o próximo slot é conjurado logo depois) e Junto (o próximo slot sai ao mesmo tempo).
 *       Sempre avança para o slot seguinte (sem voltar ao começo), então nunca entra em laço infinito.</li>
 * </ul>
 */
public final class ArcaneTriggers {
    public static final String ON_HURT = "on_hurt";
    public static final String ON_LOW_HEALTH = "on_low_health";
    public static final String ON_KILL = "on_kill";
    public static final String ON_CRIT = "on_crit";
    public static final String THEN = "then";
    public static final String TOGETHER = "together";

    private static final Set<ResourceLocation> REACTIVE = Set.of(IsekaiMahou.id(ON_HURT), IsekaiMahou.id(ON_LOW_HEALTH),
            IsekaiMahou.id(ON_KILL), IsekaiMahou.id(ON_CRIT));
    /** Ticks entre uma magia e a do próximo slot com "Depois". */
    private static final int THEN_DELAY = 8;
    private static final float LOW_HEALTH = 0.3F;

    /** > 0 enquanto o próprio módulo está conjurando automaticamente (thread do servidor). */
    private static int autoDepth;
    /** Reações já agendadas para o próximo tick ("uuid|slotKey"), para não empilhar várias no mesmo tick. */
    private static final Set<String> PENDING = new HashSet<>();

    // =====================================================================================
    //  Glifos
    // =====================================================================================
    public static final class OnHurtTrigger extends TriggerGlyph {
        public OnHurtTrigger() {
            super("on_hurt", 6, true);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }
    }

    public static final class OnLowHealthTrigger extends TriggerGlyph {
        public OnLowHealthTrigger() {
            super("on_low_health", 8, true);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }
    }

    public static final class OnKillTrigger extends TriggerGlyph {
        public OnKillTrigger() {
            super("on_kill", 5, true);
            element(Element.ARCANE);
            tier(2);
            minCircle(2);
        }
    }

    public static final class OnCritTrigger extends TriggerGlyph {
        public OnCritTrigger() {
            super("on_crit", 5, true);
            element(Element.ARCANE);
            tier(2);
            minCircle(2);
        }
    }

    public static final class ThenTrigger extends TriggerGlyph {
        public ThenTrigger() {
            super("then", 4, true);
            element(Element.ARCANE);
            tier(2);
            minCircle(2);
        }
    }

    public static final class TogetherTrigger extends TriggerGlyph {
        public TogetherTrigger() {
            super("together", 8, true);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }
    }

    public static void registerGlyphs() {
        GlyphRegistry.register(new OnHurtTrigger());
        GlyphRegistry.register(new OnLowHealthTrigger());
        GlyphRegistry.register(new OnKillTrigger());
        GlyphRegistry.register(new OnCritTrigger());
        GlyphRegistry.register(new ThenTrigger());
        GlyphRegistry.register(new TogetherTrigger());
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    /**
     * Bloqueia o uso manual (tecla) de magias reativas. As conjurações automáticas passam (autoDepth > 0).
     * Também faz o Silêncio valer para MOBS: o núcleo só checa o silêncio de jogadores (na tecla), então
     * cultistas, xamãs e chefes silenciados (Silêncio, Contramagia, Roubar Mana) "falham" a magia aqui.
     */
    public static void onPre(SpellCastEvent.Pre event) {
        LivingEntity caster = event.getCaster();
        if (caster != null && !(caster instanceof Player) && caster.hasEffect(CoreRegistries.SILENCED) && !ignoresSilence(caster)) {
            event.setCanceled(true);
            if (caster.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SMOKE, caster.getX(), caster.getEyeY(), caster.getZ(), 8, 0.2, 0.15, 0.2, 0.02);
                level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.FIRE_EXTINGUISH,
                        caster.getSoundSource(), 0.5F, 1.6F);
            }
            return;
        }
        if (autoDepth > 0 || !(caster instanceof ServerPlayer p)) return;
        if (isReactive(event.getContext().spell())) {
            event.setCanceled(true);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.arcane_reactive_only").withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Depois / Junto: dispara o próximo slot do mesmo loadout. */
    public static void onPost(SpellCastEvent.Post event) {
        if (!(event.getCaster() instanceof ServerPlayer p)) return;
        ParsedSpell spell = event.getContext().spell();
        boolean together = spell.hasTrigger(TOGETHER);
        boolean then = spell.hasTrigger(THEN);
        if (!together && !then) return;
        MagicData d = MagicApi.get(p);
        Element el = event.getContext().element();
        int loadout = d.activeLoadout(el);
        int slot = findSlot(p, d, el, loadout, event.getContext().recipe(), spell);
        if (slot < 0 || slot + 1 >= Progression.SLOTS) return;
        int next = slot + 1;
        SpellScheduler.schedule(together ? 1 : THEN_DELAY, () -> castNext(p, el, loadout, next));
    }

    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getNewDamage() <= 0 || !p.isAlive()) return;
        if (event.getSource().getEntity() != p) react(p, ON_HURT);
        float max = p.getMaxHealth();
        float now = p.getHealth();
        float before = now + event.getNewDamage();
        if (now > 0 && now < max * LOW_HEALTH && before >= max * LOW_HEALTH) react(p, ON_LOW_HEALTH);
    }

    public static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && event.getEntity() != p) react(p, ON_KILL);
    }

    public static void onCrit(CriticalHitEvent event) {
        // O evento roda em todo ataque (até em barcos e molduras): só conta crítico de verdade em criatura viva
        if (event.isCriticalHit() && event.getEntity() instanceof ServerPlayer p
                && event.getTarget() instanceof LivingEntity target && target.isAlive() && target != p) {
            react(p, ON_CRIT);
        }
    }

    // =====================================================================================
    //  Conjuração automática
    // =====================================================================================

    /** Agenda (para o próximo tick) os slots do loadout ativo que reagem a {@code trigger}. */
    public static void react(ServerPlayer p, String trigger) {
        MagicData d = MagicApi.get(p);
        Element el = d.activeElement();
        int loadout = d.activeLoadout(el);
        ResourceLocation id = IsekaiMahou.id(trigger);
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellRecipe recipe = d.peekSlot(el, loadout, slot).main();
            if (recipe == null || !leadingTriggers(recipe).contains(id)) continue;
            String key = p.getUUID() + "|" + MagicData.slotKey(el, loadout, slot);
            if (!PENDING.add(key)) continue;
            int s = slot;
            // Próximo tick: fora do meio do cálculo de dano/morte, e já com as recargas atualizadas
            SpellScheduler.schedule(1, () -> {
                PENDING.remove(key);
                if (!p.isRemoved() && p.isAlive()) autoCast(p, el, loadout, s, recipe, false);
            });
        }
    }

    /** Depois/Junto: conjura a versão do próximo slot que valeria para o movimento atual (igual à tecla). */
    private static void castNext(ServerPlayer p, Element el, int loadout, int slot) {
        if (p.isRemoved() || !p.isAlive()) return;
        MagicData d = MagicApi.get(p);
        SpellSlot s = d.peekSlot(el, loadout, slot);
        MoveTrigger move = MoveTrigger.fromPlayer(p);
        SpellRecipe recipe = s.get(move);
        if (recipe == null || (move.glyphId() != null && !d.knowsGlyph(move.glyphId()))) recipe = s.main();
        if (recipe == null || recipe.isEmpty()) return;
        autoCast(p, el, loadout, slot, recipe, true);
    }

    /**
     * Valida como o {@link SpellCaster#beginCast} (silêncio, conhecimento, slot G, carga, recarga, mana) e conjura.
     * Magias reativas saem na hora. Na sequência (Depois/Junto), uma magia com tempo de conjuração (Carregar, magias
     * caras...) é conjurada normalmente pelo {@link SpellCaster#beginCast}, para os gatilhos não "pularem" esse tempo.
     * @param sequence true = Depois/Junto (mostra o motivo se falhar); false = reação (silenciosa, para não poluir a tela)
     */
    private static boolean autoCast(ServerPlayer p, Element el, int loadout, int slot, SpellRecipe recipe, boolean sequence) {
        if (p.isSpectator()) return false;
        MagicData d = MagicApi.get(p);
        if (p.hasEffect(CoreRegistries.SILENCED) || p.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) {
            return fail(p, sequence, Component.translatable("message.isekai_mahou.cannot_cast"));
        }
        boolean creative = p.getAbilities().instabuild;
        SpellParser.Result r = SpellParser.parse(recipe, el, creative ? null : d);
        if (!r.ok()) return fail(p, sequence, r.error());
        ParsedSpell spell = r.spell();
        boolean ultimateSlot = slot == Progression.ULTIMATE_SLOT;
        if (spell.isUltimate() != ultimateSlot) return false;
        if (spell.isUltimate() && !creative && d.charge(el) < Progression.MAX_CHARGE) {
            return fail(p, sequence, Component.translatable("message.isekai_mahou.no_charge", (int) d.charge(el)));
        }
        String key = MagicData.slotKey(el, loadout, slot);
        if (p.serverLevel().getGameTime() < d.cooldowns.getOrDefault(key, 0L)) {
            return fail(p, sequence, Component.translatable("message.isekai_mahou.on_cooldown"));
        }
        if (!creative && !canPay(p, d, spell)) return fail(p, sequence, Component.translatable("message.isekai_mahou.no_mana"));

        // Canalizar fica de fora: o canal só termina ao soltar a tecla DAQUELE slot, que ninguém apertou
        if (sequence && spell.castTicks() > 0 && !spell.formStats().channel && !isReactive(spell)) {
            // Já conjurando/canalizando outra (ex.: a 1ª magia é Canalizar): não atropela a conjuração em andamento
            if (d.channelSlot >= 0) return false;
            SpellCaster.beginCast(p, d, el, recipe, slot, key);
            return true;
        }
        autoDepth++;
        try {
            boolean ok = SpellCaster.performCast(p, d, el, recipe, spell, key, false);
            if (ok) ElementFx.ring(p.serverLevel(), p.position(), el, 0.9, 14); // "o gatilho disparou"
            return ok;
        } finally {
            autoDepth--;
        }
    }

    private static boolean canPay(ServerPlayer p, MagicData d, ParsedSpell spell) {
        // v1.0: Sobrecarga em QUALQUER estágio paga a magia inteira com vida (mesma regra do SpellCaster)
        if (spell.anyStats(s -> s.overload)) return p.getHealth() > SpellCaster.overloadHealthCost(spell) + 1.0F; // v1.0: mesma conta do núcleo
        return d.mana() >= spell.manaCost();
    }

    private static boolean fail(ServerPlayer p, boolean feedback, Component msg) {
        if (feedback) p.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
        return false;
    }

    // =====================================================================================
    //  Auxiliares
    // =====================================================================================

    /** Gatilhos no começo da receita (antes da forma). */
    public static Set<ResourceLocation> leadingTriggers(SpellRecipe recipe) {
        Set<ResourceLocation> out = new HashSet<>();
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (!(g instanceof TriggerGlyph)) break;
            out.add(id);
        }
        return out;
    }

    /**
     * A Sombra da Tribulação (módulo ascension) descarta de vez a magia que falha ao conjurar; com o silêncio isso
     * apagaria as magias dela para sempre, então ela ignora o bloqueio (a Tribulação continua justa).
     */
    private static boolean ignoresSilence(LivingEntity caster) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(caster.getType());
        return key.getNamespace().equals(IsekaiMahou.MODID) && key.getPath().equals("ascension_shadow");
    }

    private static boolean isReactive(ParsedSpell spell) {
        for (TriggerGlyph t : spell.triggers()) if (REACTIVE.contains(t.id())) return true;
        return false;
    }

    /**
     * Qual slot acabou de ser conjurado: o que tem esta receita e cuja recarga foi marcada agora
     * (o núcleo grava a recarga antes do evento Post). Sem empate, cai no primeiro slot com a receita.
     */
    private static int findSlot(ServerPlayer p, MagicData d, Element el, int loadout, SpellRecipe recipe, ParsedSpell spell) {
        long expected = p.serverLevel().getGameTime() + spell.cooldown();
        int fallback = -1;
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellSlot s = d.peekSlot(el, loadout, slot);
            if (!slotHas(s, recipe)) continue;
            Long cd = d.cooldowns.get(MagicData.slotKey(el, loadout, slot));
            if (cd != null && cd == expected) return slot;
            if (fallback < 0) fallback = slot;
        }
        return fallback;
    }

    private static boolean slotHas(SpellSlot s, SpellRecipe recipe) {
        List<SpellRecipe> all = List.copyOf(s.all().values());
        for (SpellRecipe r : all) if (r.equals(recipe)) return true;
        return false;
    }

    public static void clear() {
        PENDING.clear();
        autoDepth = 0;
    }

    private ArcaneTriggers() {}
}
