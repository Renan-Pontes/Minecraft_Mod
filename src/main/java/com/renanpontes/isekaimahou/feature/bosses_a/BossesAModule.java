package com.renanpontes.isekaimahou.feature.bosses_a;

import java.util.function.Supplier;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.ElementalGuardian;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Modulo "bosses_a": Guardiões Elementais — Ignis (fogo), Maré (água), Golem Primordial (terra), Zéfiro (vento).
 * Chefes GeckoLib com barra de chefe, 3 fases, cutscenes, golpes próprios e magias de glifos. Na v0.7 todo golpe de
 * área/linha/investida é telegrafado no chão (kit de chefes do núcleo), escalam com o grupo, reagem a quem se esconde
 * (salto/teleporte), funcionam como chefe final das Câmaras de Provação (rank V/VII) e pagam a recompensa de grupo.
 * Comando: /mahou spawnboss &lt;ignis|tide_guardian|primordial_golem|zephyr&gt;
 */
public class BossesAModule implements Module {
    @Override
    public String id() {
        return "bosses_a";
    }

    @Override
    public void init(IEventBus modBus) {
        BossesARegistry.register(modBus);

        // Imune ao próprio elemento, fraco ao oposto (Fogo x Água, Terra x Vento)
        resist("ignis", Element.FIRE, Element.WATER);
        resist("tide_guardian", Element.WATER, Element.FIRE);
        resist("primordial_golem", Element.EARTH, Element.WIND);
        resist("zephyr", Element.WIND, Element.EARTH);

        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
                BossesARegistry.eggs().forEach(egg -> e.accept(egg.get()));
            }
        });

        MahouCommands.extend((root, ctx) -> {
            LiteralArgumentBuilder<CommandSourceStack> spawn = MahouCommands.op("spawnboss");
            spawn.then(bossLiteral("ignis", BossesARegistry.IGNIS));
            spawn.then(bossLiteral("tide_guardian", BossesARegistry.TIDE_GUARDIAN));
            spawn.then(bossLiteral("primordial_golem", BossesARegistry.PRIMORDIAL_GOLEM));
            spawn.then(bossLiteral("zephyr", BossesARegistry.ZEPHYR));
            root.then(spawn);
        });
    }

    private static void resist(String id, Element immune, Element weak) {
        ElementReactions.setMultiplier(IsekaiMahou.id(id), immune, 0F);
        ElementReactions.setMultiplier(IsekaiMahou.id(id), weak, 2F);
    }

    /** /mahou spawnboss &lt;id&gt;: faz o chefe surgir 6 blocos à frente de quem usou o comando. */
    private static <T extends ElementalGuardian> LiteralArgumentBuilder<CommandSourceStack> bossLiteral(
            String id, Supplier<EntityType<T>> type) {
        return Commands.literal(id).executes(c -> {
            CommandSourceStack src = c.getSource();
            ServerLevel level = src.getLevel();
            Vec3 pos = src.getPosition();
            if (src.getEntity() != null) {
                Vec3 look = src.getEntity().getLookAngle();
                Vec3 flat = new Vec3(look.x, 0, look.z);
                if (flat.lengthSqr() > 1.0E-4) pos = pos.add(flat.normalize().scale(6));
            }
            T boss = type.get().spawn(level, BlockPos.containing(pos), MobSpawnType.COMMAND);
            if (boss == null) {
                src.sendFailure(Component.translatable("command.isekai_mahou.spawnboss.guardian_fail"));
                return 0;
            }
            src.sendSuccess(() -> Component.translatable("command.isekai_mahou.spawnboss.guardian_done", boss.getDisplayName()), true);
            return 1;
        });
    }
}
