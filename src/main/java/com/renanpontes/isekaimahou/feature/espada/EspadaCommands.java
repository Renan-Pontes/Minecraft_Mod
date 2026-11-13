package com.renanpontes.isekaimahou.feature.espada;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import javax.annotation.Nullable;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.espada.aging.Aging;
import com.renanpontes.isekaimahou.feature.espada.entity.EspadaBoss;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Comandos de teste:
 * <ul>
 *   <li>{@code /mahou spawnboss <ulquiorra|barragan|grimmjow>} — invoca o Espada alguns blocos à frente (o Brigadier junta
 *       este nó com o dos outros módulos de chefes);</li>
 *   <li>{@code /mahou espada aged <acúmulos>} — aplica o Envelhecido em quem usou; {@code /mahou espada cure} — tira;</li>
 *   <li>{@code /mahou espada phase <n>} — o Espada mais perto (até 64 blocos) acorda e cai para a vida da fase {@code n}
 *       (as mudanças de fase/liberações acontecem normalmente, uma por vez);</li>
 *   <li>{@code /mahou espada cast <golpe>} — o Espada mais perto usa já o golpe (alvo = quem usou o comando). Ex.:
 *       Ulquiorra {@code lanza|oscuras|outside|segunda}; Barragan {@code respira|arrogante|ruin|pools|domain};
 *       Grimmjow {@code desgarron|pantera}.</li>
 * </ul>
 */
public final class EspadaCommands {
    private static final Map<String, Supplier<? extends EntityType<?>>> BOSSES = new LinkedHashMap<>();

    static {
        BOSSES.put("ulquiorra", EspadaRegistries.ULQUIORRA);
        BOSSES.put("barragan", EspadaRegistries.BARRAGAN);
        BOSSES.put("grimmjow", EspadaRegistries.GRIMMJOW);
    }

    public static void register() {
        MahouCommands.extend((root, ctx) -> {
            LiteralArgumentBuilder<CommandSourceStack> spawn = MahouCommands.op("spawnboss");
            for (Map.Entry<String, Supplier<? extends EntityType<?>>> e : BOSSES.entrySet()) {
                spawn.then(Commands.literal(e.getKey()).executes(c -> spawn(c.getSource(), e.getValue().get())));
            }
            root.then(spawn);
            root.then(MahouCommands.op("espada")
                    .then(Commands.literal("aged").then(Commands.argument("stacks", IntegerArgumentType.integer(1, Aging.MAX_STACKS))
                            .executes(c -> aged(c.getSource(), IntegerArgumentType.getInteger(c, "stacks")))))
                    .then(Commands.literal("cure").executes(c -> cure(c.getSource())))
                    .then(Commands.literal("phase").then(Commands.argument("n", IntegerArgumentType.integer(1, 3))
                            .executes(c -> phase(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))
                    .then(Commands.literal("cast").then(Commands.argument("attack", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(ATTACKS, b))
                            .executes(c -> cast(c.getSource(), StringArgumentType.getString(c, "attack"))))));
        });
    }

    private static final List<String> ATTACKS = List.of("lanza", "oscuras", "cero", "luz", "bala", "outside", "murcielago", "segunda",
            "respira", "dome", "grab", "axe", "throw", "summon", "arrogante", "ruin", "pools", "domain",
            "desgarron", "garra", "gran_rey", "roar", "pounce", "pantera");

    @Nullable
    private static EspadaBoss nearest(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        EspadaBoss best = null;
        for (EspadaBoss b : level.getEntitiesOfClass(EspadaBoss.class, new AABB(at, at).inflate(64), e -> e.isAlive())) {
            if (best == null || b.distanceToSqr(at) < best.distanceToSqr(at)) best = b;
        }
        if (best == null) source.sendFailure(Component.translatable("command.isekai_mahou.espada.none_near"));
        return best;
    }

    private static int phase(CommandSourceStack source, int n) {
        EspadaBoss b = nearest(source);
        if (b == null) return 0;
        float th = b.debugPhaseThreshold(n);
        if (th < 0) {
            source.sendFailure(Component.translatable("command.isekai_mahou.espada.no_phase", b.getDisplayName(), n));
            return 0;
        }
        b.debugWake(source.getLevel());
        b.setHealth(Math.max(1F, b.getMaxHealth() * th - 1F));
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.espada.phase_done", b.getDisplayName(), n), true);
        return 1;
    }

    private static int cast(CommandSourceStack source, String attack) {
        EspadaBoss b = nearest(source);
        if (b == null) return 0;
        b.debugWake(source.getLevel());
        LivingEntity target = source.getEntity() instanceof LivingEntity le ? le : b.getTarget();
        if (target != null && b.getTarget() == null && target != b) b.setTarget(target);
        if (!b.debugCast(source.getLevel(), attack, target)) {
            source.sendFailure(Component.translatable("command.isekai_mahou.espada.cast_fail", b.getDisplayName(), attack,
                    String.join(", ", b.debugAttacks())));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.espada.cast_done", b.getDisplayName(), attack), true);
        return 1;
    }

    private static int aged(CommandSourceStack source, int stacks) {
        if (!(source.getEntity() instanceof LivingEntity le)) return 0;
        Aging.cure(le);
        int n = Aging.apply(le, stacks, null);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.espada.aged", n), false);
        return n;
    }

    private static int cure(CommandSourceStack source) {
        if (!(source.getEntity() instanceof LivingEntity le)) return 0;
        Aging.cure(le);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.espada.cured"), false);
        return 1;
    }

    private static int spawn(CommandSourceStack source, EntityType<?> type) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        Entity viewer = source.getEntity();
        if (viewer != null) {
            Vec3 eye = viewer.getEyePosition();
            Vec3 look = viewer.getLookAngle().multiply(1, 0, 1);
            if (look.lengthSqr() > 1.0E-4) {
                Vec3 to = eye.add(look.normalize().scale(8.0));
                BlockHitResult hit = level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
                Vec3 at = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation().subtract(look.normalize().scale(2.0));
                pos = new Vec3(at.x, viewer.getY(), at.z);
            }
        }
        Entity boss = type.spawn(level, BlockPos.containing(pos), MobSpawnType.COMMAND);
        if (boss == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.espada.spawn_fail"));
            return 0;
        }
        Component name = boss.getDisplayName().copy().withStyle(ChatFormatting.GOLD);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.espada.spawn_done", name), true);
        return 1;
    }

    private EspadaCommands() {}
}
