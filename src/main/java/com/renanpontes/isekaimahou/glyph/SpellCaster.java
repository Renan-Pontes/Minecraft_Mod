package com.renanpontes.isekaimahou.glyph;

import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.stack.StackManager;
import com.renanpontes.isekaimahou.glyph.fx.SpellVisuals;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Conjuração (servidor): teclas Z/X/C/G → valida → conjuração segurando → executa a forma.
 * Também permite que MOBS conjurem magias de glifos ({@link #castAsMob}).
 * <p>
 * v0.8 — a Pilha: a conjuração de um jogador com tempo de conjuração ≥ {@link SpellStack#MIN_PLAYER_CAST_TICKS}
 * fica na {@link SpellStack} enquanto ele segura a tecla (pode ser contrariada: {@link #counterInterrupt}); toda
 * conjuração que resolve (jogador, mob, {@link #castFrom}) é registrada em {@link SpellStack#recordCast}.
 * <p>
 * v1.0 — as teclas leem o LOADOUT ativo (9 loadouts, {@link MagicData#activeLoadout()}); o elemento da magia vem dos
 * glifos ({@link ParsedSpell#primary()}) — o parâmetro {@code element} de {@link #beginCast}/{@link #performCast} é
 * ignorado quando a receita tem primário; a carga de ultimate é única ({@link MagicData#charge()}). XP, carga, anúncio
 * e tempos usam o custo base ({@link ParsedSpell#baseCost()}); a mana paga é {@link ParsedSpell#manaCost()}.
 */
public final class SpellCaster {
    /** Custo BASE mínimo ({@link ParsedSpell#baseCost()}) para a magia anunciar o nome na tela de quem estiver perto. */
    public static final int ANNOUNCE_COST = 45;

    // =====================================================================================
    //  Teclas
    // =====================================================================================
    public static void onKey(ServerPlayer player, int slot, boolean pressed) {
        if (slot < 0 || slot >= Progression.SLOTS || player.isSpectator()) return;
        MagicData data = MagicApi.get(player);

        if (!pressed) {
            if (data.channelSlot == slot) clearChannel(player, data, false);
            return;
        }
        if (data.channelSlot >= 0) return;
        // v1.6: Modo Paz (cidade/casa) — nada de conjurar sem querer
        if (com.renanpontes.isekaimahou.core.ux.PeaceMode.isOn(player)) {
            com.renanpontes.isekaimahou.core.ux.PeaceMode.denied(player);
            return;
        }

        int loadout = data.activeLoadout();
        SpellSlot spellSlot = data.peekSlot(loadout, slot);
        MoveTrigger trigger = MoveTrigger.fromPlayer(player);
        SpellRecipe recipe = spellSlot.get(trigger);
        if (recipe == null || (trigger.glyphId() != null && !data.knowsGlyph(trigger.glyphId()))) {
            recipe = spellSlot.main();
        }
        if (recipe == null || recipe.isEmpty()) {
            fail(player, Component.translatable("message.isekai_mahou.empty_slot"));
            return;
        }
        String key = MagicData.slotKey(loadout, slot);
        beginCast(player, data, SpellCost.primaryElement(recipe), recipe, slot, key);
    }

    /**
     * Valida e começa a conjurar.
     * @param element ignorado desde a v1.0 (o elemento vem dos glifos); mantido por compatibilidade
     */
    public static void beginCast(ServerPlayer player, MagicData data, Element element, SpellRecipe recipe, int slot, String cdKey) {
        if (player.hasEffect(CoreRegistries.SILENCED) || player.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) {
            fail(player, Component.translatable("message.isekai_mahou.cannot_cast"));
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        // a maestria vale sempre (mesmo no criativo); as checagens de conhecimento e de limites, só fora dele
        SpellParser.Result r = SpellParser.parse(recipe, element, creative ? null : data, data);
        if (!r.ok()) {
            fail(player, r.error());
            return;
        }
        ParsedSpell spell = r.spell();
        element = spell.primary();
        boolean ultimateSlot = slot == Progression.ULTIMATE_SLOT;
        if (spell.isUltimate() && !ultimateSlot) {
            fail(player, Component.translatable("message.isekai_mahou.ultimate_only_g"));
            return;
        }
        if (!spell.isUltimate() && ultimateSlot) {
            fail(player, Component.translatable("message.isekai_mahou.g_needs_ultimate"));
            return;
        }
        if (spell.isUltimate() && !creative && data.charge() < Progression.MAX_CHARGE) {
            fail(player, Component.translatable("message.isekai_mahou.no_charge", (int) data.charge()));
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (now < data.cooldowns.getOrDefault(cdKey, 0L)) {
            fail(player, Component.translatable("message.isekai_mahou.on_cooldown"));
            return;
        }
        if (!canPay(player, data, spell, creative)) {
            fail(player, MagicApi.noResourceMessage(player));
            return;
        }

        data.lastCastSlot = slot;
        if (spell.castTicks() <= 0) {
            performCast(player, data, element, recipe, spell, cdKey, false);
            if (spell.formStats().channel) startChannel(player, data, element, recipe, slot, cdKey, 10, true);
        } else {
            startChannel(player, data, element, recipe, slot, cdKey, spell.castTicks(), spell.formStats().channel);
            // A Pilha: magia com tempo de conjuração fica "pendente" enquanto carrega
            StackManager.channelStarted(player, spell, recipe, element, spell.castTicks(), data.channelStart);
            // Um ouvinte do PendingCastEvent pode ter contrariado na hora ("Em Resposta"): sem cântico nem som
            if (data.channelSlot < 0) return;
            if (!recipe.chant().isEmpty() && !spell.formStats().silent) {
                Announce.chant(player, recipe.chant(), spell.castTicks());
            }
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
    }

    private static void startChannel(ServerPlayer player, MagicData data, Element element, SpellRecipe recipe, int slot,
                                     String cdKey, int ticks, boolean sustain) {
        data.channelSlot = slot;
        data.channelElement = element;
        data.channelRecipe = recipe;
        data.channelKey = cdKey;
        data.channelStart = player.serverLevel().getGameTime();
        data.channelTicks = ticks;
        data.channelSustain = sustain;
        PacketDistributor.sendToPlayer(player, new CorePayloads.CastState(slot, ticks, recipe.chant(), (byte) 0));
    }

    /** Todo tick: avança a conjuração em andamento. */
    public static void tickChannel(ServerPlayer player, MagicData data) {
        if (data.channelSlot < 0 || data.channelRecipe == null) return;
        long elapsed = player.serverLevel().getGameTime() - data.channelStart;
        if (elapsed < data.channelTicks) {
            // release 1.0: o jogador conjurando mostra só o círculo mágico (o da magia); a aura discreta de quem segura
            // uma conjuração longa é desenhada no cliente (client/spellfx/ChannelAura) e pode ser desligada lá
            if (elapsed % 3 == 0) SpellVisuals.channelCircle(player.serverLevel(), player, data.channelElement);
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        SpellParser.Result r = SpellParser.parse(data.channelRecipe, data.channelElement, creative ? null : data, data);
        if (!r.ok() || !canPay(player, data, r.spell(), creative)) {
            clearChannel(player, data, true);
            if (r.ok()) fail(player, MagicApi.noResourceMessage(player));
            return;
        }
        boolean chantDone = !data.channelRecipe.chant().isEmpty() && data.channelTicks > 10;
        boolean cast = performCast(player, data, data.channelElement, data.channelRecipe, r.spell(), data.channelKey, chantDone);
        StackManager.channelEnded(player, cast);
        if (data.channelSustain) {
            data.channelStart = player.serverLevel().getGameTime();
            data.channelTicks = 10;
        } else {
            PacketDistributor.sendToPlayer(player, new CorePayloads.CastState(data.channelSlot, 0, "", (byte) 1));
            data.channelSlot = -1;
            data.channelRecipe = null;
        }
    }

    public static void interrupt(ServerPlayer player) {
        MagicData data = MagicApi.get(player);
        if (data.channelSlot < 0) return;
        clearChannel(player, data, true);
        player.displayClientMessage(Component.translatable("message.isekai_mahou.cast_interrupted")
                .withStyle(ChatFormatting.RED), true);
    }

    /**
     * A conjuração do jogador foi CONTRARIADA (a Pilha chama): interrompe com o aviso "Sua magia foi contrariada!".
     * O Silenciado de 2 s é aplicado pela Pilha.
     */
    public static void counterInterrupt(ServerPlayer player, @Nullable LivingEntity by) {
        MagicData data = MagicApi.get(player);
        if (data.channelSlot >= 0) clearChannel(player, data, true);
        player.displayClientMessage(StackManager.counteredMessage(by), true);
    }

    private static void clearChannel(ServerPlayer player, MagicData data, boolean interrupted) {
        StackManager.channelEnded(player, false);
        if (data.channelSlot >= 0) {
            PacketDistributor.sendToPlayer(player, new CorePayloads.CastState(data.channelSlot, 0, "", (byte) (interrupted ? 2 : 1)));
        }
        data.channelSlot = -1;
        data.channelRecipe = null;
        data.channelElement = null;
        data.channelSustain = false;
    }

    private static boolean canPay(ServerPlayer player, MagicData data, ParsedSpell spell, boolean creative) {
        if (creative) return true;
        if (isOverload(spell)) return player.getHealth() > overloadHealthCost(spell) + 1.0F;
        return data.mana() >= spell.manaCost();
    }

    /** Sobrecarga em qualquer estágio faz a magia inteira ser paga com vida. */
    private static boolean isOverload(ParsedSpell spell) {
        return spell.anyStats(s -> s.overload);
    }

    /**
     * Vida paga por uma magia com Sobrecarga. v0.8: custo/5 (vida base 20). v1.0 mantém a PROPORÇÃO da inflação de
     * números: o custo de mana ficou ×{@link SpellCost#COST_SCALE} e a vida base ×{@code BASE_PLAYER_HEALTH/20}, então
     * {@code vida = custo × (vida base / 20) / (5 × COST_SCALE)} — o custo por cópia continua encarecendo a Sobrecarga.
     */
    public static float overloadHealthCost(ParsedSpell spell) {
        float healthScale = Progression.BASE_PLAYER_HEALTH / 20.0F;
        return spell.manaCost() * healthScale / (5.0F * SpellCost.COST_SCALE);
    }

    private static void fail(ServerPlayer player, Component msg) {
        player.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
        PacketDistributor.sendToPlayer(player, new CorePayloads.CastState(-1, 0, "", (byte) 2));
    }

    // =====================================================================================
    //  Execução
    // =====================================================================================
    public static boolean performCast(ServerPlayer player, MagicData data, Element element, SpellRecipe recipe,
                                      ParsedSpell spell, String cdKey, boolean chantDone) {
        // v1.0: o elemento vem dos glifos (quem chama pode passar o elemento antigo do "slot")
        if (spell.primary() != null) element = spell.primary();
        ServerLevel level = player.serverLevel();
        boolean creative = player.getAbilities().instabuild;

        SpellContext ctx = new SpellContext(level, player, element, recipe, spell);
        // Conjuração de jogador: efeitos de outros elementos rodam em visões e saltos pagam com mana
        ctx.markPlayerCast();
        if (chantDone) ctx.setPowerMult(1.15F);
        if (NeoForge.EVENT_BUS.post(new SpellCastEvent.Pre(player, ctx)).isCanceled()) return false;

        // Pagar
        if (!creative) {
            if (isOverload(spell)) player.setHealth(Math.max(1.0F, player.getHealth() - overloadHealthCost(spell)));
            else data.consumeMana(spell.manaCost());
            if (spell.isUltimate()) data.setCharge(0);
        }

        runForm(ctx);
        SpellStack.recordCast(player, recipe, element, ctx.powerMult());

        // Recarga
        data.cooldowns.put(cdKey, level.getGameTime() + spell.cooldown());
        PacketDistributor.sendToPlayer(player, new CorePayloads.Cooldown(cdKey, spell.cooldown()));

        // Progresso (v0.7): XP de nível 2+custo/6; XP de elemento 2+custo/4 dividida entre os elementos da magia
        // (v1.0: custo BASE — o custo por cópia e a escala ×3 não inflam o XP)
        int base = spell.baseCost();
        MagicApi.giveXp(player, 2 + base / 6, "cast");
        Set<Element> used = spell.elements().isEmpty() ? Set.of(element) : spell.elements();
        int elementXp = Math.max(1, Math.round((2 + base / 4) / (float) used.size()));
        for (Element e : used) {
            MagicApi.giveElementXp(player, e, elementXp);
            MagicApi.addCounter(player, "cast." + e.id, 1);
        }
        if (!spell.isUltimate()) data.addCharge(2.0F + base * 0.1F);

        // Anúncio / cutscene
        if (!spell.formStats().silent && (spell.isUltimate() || base >= ANNOUNCE_COST)) {
            Announce.spell(player, recipe, element, spell.isUltimate());
        }
        if (spell.isUltimate() && !spell.formStats().silent) {
            Cutscenes.ultimate(player, recipe, element, spell.form().id().getPath());
        }
        ElementFx.castSound(level, player, element);

        NeoForge.EVENT_BUS.post(new SpellCastEvent.Post(player, ctx));
        return true;
    }

    /** Executa a forma respeitando os aumentos de Atraso e Repetir aplicados na forma. */
    public static void runForm(SpellContext ctx) {
        SpellStats fs = ctx.formStats();
        Runnable r = () -> ctx.spell().form().cast(ctx);
        int delay = fs.delayTicks();
        if (delay > 0) SpellScheduler.schedule(delay, r);
        else r.run();
        for (int i = 1; i <= Math.max(0, fs.repeat); i++) {
            SpellScheduler.schedule(delay + 20 * i, r);
        }
    }

    /**
     * Mob conjura uma magia (cultistas, xamãs, chefes). Não gasta mana, não checa conhecimento.
     * @return o contexto usado, ou null se a receita for inválida
     */
    public static SpellContext castAsMob(LivingEntity caster, Element element, SpellRecipe recipe, float powerMult) {
        if (!(caster.level() instanceof ServerLevel level)) return null;
        SpellParser.Result r = SpellParser.parse(recipe, null, null);
        if (!r.ok()) return null;
        SpellContext ctx = new SpellContext(level, caster, element, recipe, r.spell());
        ctx.setPowerMult(powerMult);
        if (NeoForge.EVENT_BUS.post(new SpellCastEvent.Pre(caster, ctx)).isCanceled()) return null;
        runForm(ctx);
        SpellStack.recordCast(caster, recipe, element, powerMult);
        if (!r.spell().formStats().silent && (r.spell().isUltimate() || r.spell().baseCost() >= ANNOUNCE_COST)) {
            Announce.spell(caster, recipe, element, r.spell().isUltimate());
        }
        ElementFx.castSound(level, caster, element);
        NeoForge.EVENT_BUS.post(new SpellCastEvent.Post(caster, ctx));
        return ctx;
    }

    /**
     * Conjura uma receita a partir de um ponto qualquer (pedestais, portais, chefes, lendas).
     * Não gasta mana, não dá XP, não checa conhecimento nem Círculo; não anuncia nem toca som (quem chama decide).
     * Saltos/Insaciável pagos seguem a regra de mob ({@link SpellContext#MOB_HOP_CAP}) e não há visões por elemento.
     *
     * @param caster     dono da magia (dano/créditos de abate; pode ser um FakePlayer)
     * @param element    elemento primário (null = Arcano)
     * @param power      multiplicador de poder (1 = normal)
     * @param origin     de onde sai (null = olhos do conjurador)
     * @param dir        para onde vai (null = para onde o conjurador olha)
     * @param postEvents true = posta SpellCastEvent.Pre (cancelável) e Post, como uma conjuração normal
     * @return o contexto usado, ou null se a receita é inválida, o evento foi cancelado ou o conjurador não está num mundo de servidor
     */
    @Nullable
    public static SpellContext castFrom(LivingEntity caster, Element element, SpellRecipe recipe, float power,
                                        @Nullable Vec3 origin, @Nullable Vec3 dir, boolean postEvents) {
        if (caster == null || recipe == null || !(caster.level() instanceof ServerLevel level)) return null;
        SpellParser.Result r = SpellParser.parse(recipe, element, null);
        if (!r.ok()) return null;
        SpellContext ctx = new SpellContext(level, caster, element != null ? element : Element.ARCANE, recipe, r.spell());
        ctx.setPowerMult(power);
        if (origin != null || dir != null) {
            ctx.setOrigin(origin != null ? origin : caster.getEyePosition(), dir != null ? dir : caster.getLookAngle());
        }
        if (postEvents && NeoForge.EVENT_BUS.post(new SpellCastEvent.Pre(caster, ctx)).isCanceled()) return null;
        runForm(ctx);
        SpellStack.recordCast(caster, recipe, ctx.element(), power);
        if (postEvents) NeoForge.EVENT_BUS.post(new SpellCastEvent.Post(caster, ctx));
        return ctx;
    }

    private SpellCaster() {}
}
