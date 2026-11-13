package com.renanpontes.isekaimahou.feature.domain;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.domain.entity.DomainDomeEntity;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

/**
 * Eventos de jogo do módulo:
 * <ul>
 *   <li>Ultimates só com a Carga cheia (fecha o "Canalizar" repetindo a ultimate de graça);</li>
 *   <li>Lei Anulação: cancela magias (de jogadores E mobs) conjuradas por inimigos dentro do domo;</li>
 *   <li>Paisagem Elemental: os blocos trocados não podem ser quebrados, explodidos nem empurrados por pistões
 *       enquanto o domo existe, e nunca soltam item (senão daria para "duplicar" o bloco original ao devolver);</li>
 *   <li>Comando {@code /mahou domain clear}: desfaz todos os domínios da dimensão (devolvendo a paisagem);</li>
 *   <li>Comando {@code /mahou domain info}: integridade, lado, invertida, quem envolve quem (v0.8).</li>
 * </ul>
 */
public final class DomainEvents {
    static void register() {
        NeoForge.EVENT_BUS.addListener(DomainEvents::onSpellPre);
        NeoForge.EVENT_BUS.addListener(DomainEvents::onBreak);
        NeoForge.EVENT_BUS.addListener(DomainEvents::onDetonate);
        NeoForge.EVENT_BUS.addListener(DomainEvents::onPiston);
        NeoForge.EVENT_BUS.addListener(DomainEvents::onDrops);
        // v0.8: golpes na barreira (magias, corpo a corpo e explosões através da parede)
        NeoForge.EVENT_BUS.addListener(DomainBarrier::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(DomainBarrier::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(DomainBarrier::onExplosion);
        NeoForge.EVENT_BUS.addListener(DomainBarrier::onServerStopped);
        MahouCommands.extend((root, buildCtx) -> root.then(MahouCommands.op("domain")
                .then(Commands.literal("info").executes(c -> info(c.getSource())))
                .then(Commands.literal("clear").executes(c -> {
                    ServerLevel level = c.getSource().getLevel();
                    int n = 0;
                    for (DomainDomeEntity d : DomainDomeEntity.all(level)) {
                        if (d.state() != DomainDomeEntity.ACTIVE) continue;
                        d.collapse(false);
                        n++;
                    }
                    int count = n;
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.domain.cleared", count), true);
                    return count;
                }))));
    }

    /** {@code /mahou domain info}: cada domínio da dimensão — dono, raio, tipo, integridade, lado de quem pergunta, relações. */
    private static int info(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        List<DomainDomeEntity> domes = DomainDomeEntity.all(level);
        if (domes.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("command.isekai_mahou.domain.info.none").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        Vec3 me = src.getPosition();
        int n = 0;
        for (DomainDomeEntity d : domes) {
            n++;
            LivingEntity owner = d.ownerLiving();
            Component ownerName = owner != null ? owner.getDisplayName() : Component.literal("?");
            String kind = d.isSimple() ? "command.isekai_mahou.domain.info.kind_simple"
                    : d.isOpen() ? "command.isekai_mahou.domain.info.kind_open"
                    : d.isInverted() ? "command.isekai_mahou.domain.info.kind_inverted" : "command.isekai_mahou.domain.info.kind_closed";
            String state = d.state() == DomainDomeEntity.ACTIVE
                    ? (d.isLive() ? "command.isekai_mahou.domain.info.state_active" : "command.isekai_mahou.domain.info.state_expanding")
                    : d.state() == DomainDomeEntity.COLLAPSING ? "command.isekai_mahou.domain.info.state_collapsing"
                    : "command.isekai_mahou.domain.info.state_shattered";
            Vec3 c = d.center();
            int idx = n;
            src.sendSuccess(() -> Component.translatable("command.isekai_mahou.domain.info.head", idx, ownerName,
                    Component.translatable(kind),
                    String.format(java.util.Locale.ROOT, "%.1f", d.radius()),
                    (int) c.x, (int) c.y, (int) c.z,
                    Component.translatable(state)).withStyle(ChatFormatting.GOLD), false);
            if (!d.isSimple() && !d.isOpen()) {
                boolean inside = d.contains(me, 0);
                boolean breaks = d.isInverted() == inside;
                src.sendSuccess(() -> Component.translatable("command.isekai_mahou.domain.info.barrier",
                        (int) Math.ceil(d.integrity()), (int) Math.ceil(d.maxIntegrity()),
                        Component.translatable(inside ? "command.isekai_mahou.domain.info.side.inside" : "command.isekai_mahou.domain.info.side.outside"),
                        Component.translatable(breaks ? "command.isekai_mahou.domain.info.can_break" : "command.isekai_mahou.domain.info.cannot_break"))
                        .withStyle(ChatFormatting.AQUA), false);
            }
            StringBuilder laws = new StringBuilder();
            for (ResourceLocation law : d.laws()) {
                if (laws.length() > 0) laws.append(", ");
                laws.append(law.getPath());
            }
            if (laws.length() > 0) {
                String text = laws.toString();
                src.sendSuccess(() -> Component.translatable("command.isekai_mahou.domain.info.laws", text).withStyle(ChatFormatting.GRAY), false);
            }
            for (DomainDomeEntity o : domes) {
                if (o == d || o.isSimple() || d.isSimple()) continue;
                int other = domes.indexOf(o) + 1;
                String rel = d.envelops(o) ? "command.isekai_mahou.domain.info.rel_envelops"
                        : o.envelops(d) ? "command.isekai_mahou.domain.info.rel_inside"
                        : d.overlaps(o) ? "command.isekai_mahou.domain.info.rel_overlaps" : null;
                if (rel == null) continue;
                src.sendSuccess(() -> Component.translatable(rel, other)
                        .withStyle(ChatFormatting.DARK_AQUA), false);
            }
        }
        return n;
    }

    private static void onSpellPre(SpellCastEvent.Pre event) {
        LivingEntity caster = event.getCaster();
        if (!(caster.level() instanceof ServerLevel sl)) return;
        // Ultimates (Cataclismo, Expansão de Domínio) só saem com a Carga cheia. O núcleo confere ao COMEÇAR a
        // conjuração, mas com Canalizar a forma se repete a cada 10 ticks enquanto a tecla fica apertada — sem esta
        // trava sairia um Cataclismo/Domínio a cada meio segundo, de graça.
        if (caster instanceof ServerPlayer sp && event.getContext().spell().isUltimate() && !sp.getAbilities().instabuild) {
            // A carga é do elemento PRIMÁRIO (slot), mesmo numa magia multi-elemento
            float charge = MagicApi.get(sp).charge(event.getContext().primaryElement());
            if (charge < Progression.MAX_CHARGE) {
                event.setCanceled(true);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.no_charge", (int) charge).withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        for (DomainDomeEntity d : DomainDomeEntity.around(sl, caster.position(), 2.0)) {
            if (!d.nullifies(sl, caster)) continue;
            event.setCanceled(true);
            Vec3 p = caster.position().add(0, caster.getBbHeight() * 0.6, 0);
            sl.sendParticles(Vfx.smoke(), p.x, p.y, p.z, 14, 0.3, 0.4, 0.3, 0.02);
            sl.sendParticles(Vfx.enchant(), p.x, p.y, p.z, 10, 0.4, 0.4, 0.4, 0.4);
            sl.playSound(null, p.x, p.y, p.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.4F);
            if (caster instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.domain_nullified").withStyle(ChatFormatting.RED), true);
            }
            return;
        }
    }

    /** Margem para achar o domo de um bloco: plantas guardadas ficam até 2 blocos acima da casca. */
    private static final double BLOCK_MARGIN = 3.0;

    /** Algum domo carregado protege o bloco {@code state} em {@code pos}? */
    private static boolean protectedLandscape(ServerLevel sl, BlockPos pos, BlockState state) {
        for (DomainDomeEntity d : DomainDomeEntity.around(sl, Vec3.atCenterOf(pos), BLOCK_MARGIN)) {
            if (d.isLandscapeBlock(pos, state)) return true;
        }
        return false;
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!protectedLandscape(sl, event.getPos(), event.getState())) return;
        event.setCanceled(true);
        if (event.getPlayer() instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.domain_landscape_locked").withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        List<DomainDomeEntity> domes = DomainDomeEntity.around(sl, event.getExplosion().center(), 16.0);
        if (domes.isEmpty()) return;
        event.getAffectedBlocks().removeIf(p -> {
            BlockState st = sl.getBlockState(p);
            for (DomainDomeEntity d : domes) if (d.isLandscapeBlock(p, st)) return true;
            return false;
        });
    }

    /** Pistões não movem nem quebram blocos da paisagem (o original não voltaria e o bloco do domínio ficaria solto). */
    private static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (DomainDomeEntity.around(sl, Vec3.atCenterOf(event.getPos()), 16.0).isEmpty()) return;
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) return;
        for (List<BlockPos> list : List.of(resolver.getToPush(), resolver.getToDestroy())) {
            for (BlockPos p : list) {
                if (protectedLandscape(sl, p, sl.getBlockState(p))) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    /**
     * Rede de segurança contra duplicação: se algo (efeito de magia, Wither, mod...) destruir um bloco da paisagem
     * sem passar pelos bloqueios acima, ele some sem soltar item — e o original volta quando o domínio acabar.
     */
    private static void onDrops(BlockDropsEvent event) {
        if (protectedLandscape(event.getLevel(), event.getPos(), event.getState())) event.setCanceled(true);
    }

    private DomainEvents() {}
}
