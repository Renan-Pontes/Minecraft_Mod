package com.renanpontes.isekaimahou.glyph.fx;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.glyph.CircleStyle;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Inicialização (lado comum) dos efeitos visuais do sistema de glifos: partículas {@code fx_*}, círculo mágico de
 * conjuração ({@link SpellVisuals}), efeitos compostos ({@link FxBurst}) e o comando de vitrine (op 2):
 * <ul>
 *   <li>{@code /spellfx <elemento> [tier] [tipo]} — tipo: all (padrão), cast, impact, gather, nova, area, aura, beam,
 *   portal, domain, sky, slash;</li>
 *   <li>{@code /spellfx reaction <reação|all>} — a explosão de cada reação elemental num boneco invisível à frente.</li>
 *   <li>{@code /spellfx circle <forma 0-7> <símbolo 0-12>} — o círculo mágico por magia ({@link CircleStyle}) aos pés e
 *   na frente da mão por 4 s, na cor do elemento em uso.</li>
 * </ul>
 * O cliente fica em {@code client/spellfx/SpellFxClient} e {@code FxBurstClient}.
 */
public final class SpellFxCore {
    private static final List<String> KINDS = List.of("all", "cast", "impact", "gather", "nova", "area", "aura", "beam",
            "portal", "domain", "sky", "slash");

    public static void init(IEventBus modBus) {
        // release 1.0: maestria por glifo (estado temporário + /mahou mastery) entra junto com o núcleo dos glifos
        com.renanpontes.isekaimahou.glyph.GlyphMastery.init();
        SpellFxParticles.register(modBus);
        modBus.addListener(FxBurst::register);
        NeoForge.EVENT_BUS.addListener(SpellCastEvent.Post.class, SpellVisuals::onCast);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> SpellVisuals.clear());
        NeoForge.EVENT_BUS.addListener(SpellFxCore::commands);
        NeoForge.EVENT_BUS.addListener(MagicProgressEvent.Counter.class, SpellFxCore::onCounter);
        NeoForge.EVENT_BUS.addListener(MagicProgressEvent.CircleChanged.class, SpellFxCore::onCircle);
        NeoForge.EVENT_BUS.addListener(MagicProgressEvent.ElementAwakened.class, SpellFxCore::onAwaken);
    }

    // =====================================================================================
    //  Marcos de progresso (rituais de classe, Círculo novo, elemento despertado): espetáculo único
    // =====================================================================================

    private static Element activeOf(ServerPlayer sp) {
        try {
            Element e = MagicApi.get(sp).activeElement();
            return e != null ? e : Element.ARCANE;
        } catch (RuntimeException ex) {
            return Element.ARCANE;
        }
    }

    /** Ritual de classe concluído ("ritual_rank" sobe, qualquer classe): portal no chão, pilar e nova do elemento. */
    private static void onCounter(MagicProgressEvent.Counter e) {
        if (!"ritual_rank".equals(e.key)) return;
        ServerPlayer sp = e.getPlayer();
        ServerLevel level = sp.serverLevel();
        Element el = activeOf(sp);
        Vec3 at = sp.position();
        ElementFx.portal(level, at.add(0, 0.06, 0), new Vec3(0, 1, 0), el, el.color, 2.6F, 5, 40);
        ElementFx.pillar(level, at, el.color, 1.1F, 24F, 40);
        ElementFx.nova(level, at.add(0, 0.1, 0), el, el.color, 7F, 5, true);
    }

    /** Subiu de Círculo: golpe do céu do elemento ativo em cima do jogador (só visual). */
    private static void onCircle(MagicProgressEvent.CircleChanged e) {
        if (e.newCircle <= e.oldCircle) return;
        ServerPlayer sp = e.getPlayer();
        Element el = activeOf(sp);
        ElementFx.skyStrike(sp.serverLevel(), sp.position(), el, el.color, 2.0F + 0.3F * Math.min(10, e.newCircle), e.newCircle >= 6 ? 5 : 4, 20);
    }

    /** Elemento despertado: nova pequena + pilar na cor do elemento. */
    private static void onAwaken(MagicProgressEvent.ElementAwakened e) {
        ServerPlayer sp = e.getPlayer();
        Element el = e.element != null ? e.element : Element.ARCANE;
        ElementFx.pillar(sp.serverLevel(), sp.position(), el.color, 0.9F, 14F, 30);
        ElementFx.nova(sp.serverLevel(), sp.position().add(0, 0.1, 0), el, el.color, 4F, 4, false);
    }

    private static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("spellfx")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("reaction")
                        .then(Commands.argument("reaction", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(reactionIds(), b))
                                .executes(SpellFxCore::reactionDemo)))
                .then(Commands.literal("circle")
                        .then(Commands.argument("shape", IntegerArgumentType.integer(0, CircleStyle.SHAPES))
                                .then(Commands.argument("symbol", IntegerArgumentType.integer(0, CircleStyle.SYMBOLS))
                                        .executes(SpellFxCore::circleDemo))))
                .then(Commands.argument("element", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Element.values()).map(el -> el.id), b))
                        .executes(c -> demo(c, 3, "all"))
                        .then(Commands.argument("tier", IntegerArgumentType.integer(0, 5))
                                .executes(c -> demo(c, IntegerArgumentType.getInteger(c, "tier"), "all"))
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(KINDS, b))
                                        .executes(c -> demo(c, IntegerArgumentType.getInteger(c, "tier"),
                                                StringArgumentType.getString(c, "kind")))))));
    }

    private static List<String> reactionIds() {
        List<String> ids = new java.util.ArrayList<>();
        ids.add("all");
        for (ElementReactions.Reaction r : ElementReactions.Reaction.values()) ids.add(r.id());
        return ids;
    }

    /** Vitrine: mostra o efeito pedido (ou uma sequência de todos) na frente do jogador. */
    private static int demo(CommandContext<CommandSourceStack> c, int tier, String kind) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Element el = Element.byId(StringArgumentType.getString(c, "element"));
        if (el == null) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.spellfx.bad_element"));
            return 0;
        }
        String k = kind.toLowerCase(Locale.ROOT);
        if (!KINDS.contains(k)) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.spellfx.bad_kind", String.join(", ", KINDS)));
            return 0;
        }
        ServerLevel level = p.serverLevel();
        int rgb = el.color;
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 at = p.position().add(look.scale(6)).add(0, 0.05, 0);
        Vec3 far = p.position().add(look.scale(14)).add(0, 0.05, 0);
        boolean all = k.equals("all");
        if (all || k.equals("cast")) castShow(level, p, rgb, tier);
        if (all || k.equals("impact")) {
            ElementFx.impact(level, at.add(0, 1.0, 0), el, rgb, tier, new Vec3(0, 1, 0));
            ElementFx.line(level, p.getEyePosition(), at.add(0, 1.0, 0), el);
        }
        if (k.equals("gather")) ElementFx.gather(level, p, el, rgb, 1.5F + 0.5F * tier, tier, 30);
        if (k.equals("nova") || all && tier >= 5) ElementFx.nova(level, far, el, rgb, 4 + 1.5F * tier, tier);
        if (k.equals("area") || all && tier >= 2) ElementFx.area(level, at, el, rgb, 1.5F + 0.6F * tier, tier, 100);
        if (k.equals("aura")) ElementFx.aura(level, p, el, rgb, 1.0F, tier, 100);
        if (k.equals("beam") || all && tier >= 3) {
            ElementFx.beam(level, p.getEyePosition().add(look.scale(1.2)).add(0, -0.3, 0), far.add(0, 1.0, 0), el, rgb,
                    0.15F + 0.06F * tier, tier);
        }
        if (k.equals("portal")) ElementFx.portal(level, at.add(0, 1.6, 0), look.reverse(), el, rgb, 1.2F + 0.3F * tier, tier, 60);
        if (k.equals("domain")) ElementFx.domainSphere(level, p.position(), el, rgb, 8 + 2 * tier, 30);
        if (k.equals("sky") || all && tier >= 4) ElementFx.skyStrike(level, far, el, rgb, 1.5F + 0.5F * tier, tier, 20);
        if (k.equals("slash")) ElementFx.slash(level, p.getEyePosition().add(look.scale(1.5)).add(0, -0.4, 0), look, el, rgb, 3 + tier, tier);
        if (all && tier >= 4) ElementFx.pillar(level, at, rgb, 0.6F + 0.2F * tier, 4.0F + 2 * tier, 24);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.spellfx.demo", el.displayName(), tier), false);
        return 1;
    }

    /** Vitrine do círculo mágico por magia: a forma e o símbolo pedidos, aos pés e na frente da mão, por 4 s. */
    private static int circleDemo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CircleStyle style = new CircleStyle(IntegerArgumentType.getInteger(c, "shape"), IntegerArgumentType.getInteger(c, "symbol"), -1);
        int rgb = activeOf(p).color;
        ServerLevel level = p.serverLevel();
        ElementFx.castCircle(level, p, rgb, SpellVisuals.radius(3), 80,
                SpellFxParticles.flags(3, SpellFxParticles.MODE_GROUND, 0) | style.flags());
        ElementFx.castCircle(level, p, rgb, 0.8F, 80, SpellFxParticles.flags(3, SpellFxParticles.MODE_FRONT, 0) | style.flags());
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.spellfx.circle",
                CircleStyle.shapeName(style.shape()), CircleStyle.symbolName(style.symbol())), false);
        return 1;
    }

    /** Círculos de conjuração (pés, mão e céu) "disparando". */
    private static void castShow(ServerLevel level, ServerPlayer p, int rgb, int tier) {
        float r = SpellVisuals.radius(tier);
        int rel = SpellFxParticles.FLAG_RELEASE;
        ElementFx.castCircle(level, p, rgb, r, 30, SpellFxParticles.flags(tier, SpellFxParticles.MODE_GROUND, tier <= 1 ? 3 : 0) | rel);
        ElementFx.castCircle(level, p, rgb, 0.45F + 0.12F * tier, 30, SpellFxParticles.flags(tier, SpellFxParticles.MODE_FRONT, tier >= 3 ? 0 : 3) | rel);
        if (tier >= 5) {
            ElementFx.castCircle(level, p, rgb, r * 1.7F, 50, SpellFxParticles.flags(tier, SpellFxParticles.MODE_SKY, 0) | rel);
        }
    }

    /** Vitrine das reações: a explosão própria de cada uma (sem dano). "all" mostra todas em fila, uma a cada 1 s. */
    private static int reactionDemo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "reaction").toLowerCase(Locale.ROOT);
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        Vec3 at = p.position().add(look.normalize().scale(4)).add(0, 1.0, 0);
        ElementReactions.Reaction[] all = ElementReactions.Reaction.values();
        int shown = 0;
        for (ElementReactions.Reaction r : all) {
            if (!id.equals("all") && !id.equals(r.id())) continue;
            int delay = id.equals("all") ? shown * 20 : 0;
            Runnable show = () -> FxBurst.send(level, FxBurst.REACTION, Element.ARCANE, r.color(), at, Vec3.ZERO, 1.1F, 2, r.ordinal(), 0);
            if (delay == 0) show.run();
            else com.renanpontes.isekaimahou.core.boss.BossKit.schedule(level, delay, show);
            shown++;
        }
        if (shown == 0) {
            c.getSource().sendFailure(Component.translatable("command.isekai_mahou.spellfx.bad_reaction"));
            return 0;
        }
        final int n = shown;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.spellfx.reaction", n), false);
        return n;
    }

    private SpellFxCore() {}
}
