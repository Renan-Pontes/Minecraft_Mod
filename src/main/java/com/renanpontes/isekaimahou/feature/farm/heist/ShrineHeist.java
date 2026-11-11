package com.renanpontes.isekaimahou.feature.farm.heist;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.farm.FarmFx;
import com.renanpontes.isekaimahou.feature.farm.FarmRegistries;
import com.renanpontes.isekaimahou.feature.farm.build.Placer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * "Roubar o pedestal": o Altar dos Santuários Elementais (bloco {@code isekai_mahou:shrine_altar} do módulo de
 * estruturas — lido só por id e pelo nome da propriedade {@code element}) é inquebrável. Com TOQUE SUAVE ele sai
 * inteiro e vira o NOSSO {@code elemental_pedestal} (Gerador de Essência):
 * <ul>
 *   <li>ferramenta com Toque Suave: {@link #HITS} golpes seguidos (agachado) desprendem o altar;</li>
 *   <li>glifo Extração Arcana com o aumento Toque Suave: na hora.</li>
 * </ul>
 * Só quem já venceu a prova daquele santuário (flag {@code shrine.<elemento>}) e nunca no meio de uma prova.
 */
public final class ShrineHeist {
    public static final ResourceLocation SHRINE_ALTAR = IsekaiMahou.id("shrine_altar");
    /** Golpes com a ferramenta de Toque Suave para soltar o altar. */
    public static final int HITS = 6;
    /** Tempo máximo (ticks) entre um golpe e outro. */
    private static final int HIT_WINDOW = 60;

    private record Progress(int hits, long lastTick) {}

    private static final Map<GlobalPos, Progress> PROGRESS = new HashMap<>();

    public static boolean isShrineAltar(BlockState state) {
        return SHRINE_ALTAR.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    /** Elemento do altar (propriedade "element" lida pelo nome; reserva Fogo). */
    public static Element elementOf(BlockState state) {
        Property<?> prop = state.getBlock().getStateDefinition().getProperty("element");
        if (prop != null) {
            Element e = Element.byId(valueName(state, prop));
            if (e != null && Element.BASE.contains(e)) return e;
        }
        return Element.FIRE;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> prop) {
        return prop.getName(state.getValue(prop));
    }

    /** Prova acontecendo neste altar? (campo "active" da block entity, lido pelo NBT para não depender da classe) */
    private static boolean trialActive(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.saveWithoutMetadata(level.registryAccess()).getBoolean("active");
    }

    /** Pode roubar? Manda a mensagem do motivo quando não pode. */
    public static boolean canSteal(ServerLevel level, BlockPos pos, ServerPlayer player, Element e) {
        if (player.getAbilities().instabuild) return true;
        if (trialActive(level, pos)) {
            player.displayClientMessage(Component.translatable("farm.isekai_mahou.heist.trial_active").withStyle(ChatFormatting.RED), true);
            return false;
        }
        MagicData d = MagicApi.get(player);
        if (!d.hasFlag("shrine." + e.id)) {
            player.displayClientMessage(Component.translatable("farm.isekai_mahou.heist.not_worthy",
                    e.displayName()).withStyle(ChatFormatting.GOLD), true);
            level.sendParticles(ElementFx.dust(e, 1.2F), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 16, 0.4, 0.3, 0.4, 0);
            FarmFx.sound(level, Vec3.atCenterOf(pos), SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.6F);
            return false;
        }
        if (!Placer.canEdit(level, player, pos)) {
            player.displayClientMessage(Component.translatable("farm.isekai_mahou.heist.protected").withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    /** Tira o altar e entrega o Pedestal Elemental. */
    public static boolean steal(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BlockState state = level.getBlockState(pos);
        if (!isShrineAltar(state)) return false;
        Element e = elementOf(state);
        if (!canSteal(level, pos, player, e)) return false;
        level.levelEvent(2001, pos, Block.getId(state));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        PROGRESS.remove(GlobalPos.of(level.dimension(), pos));
        ItemStack loot = FarmRegistries.pedestalStack(e);
        Vec3 c = Vec3.atCenterOf(pos);
        if (!player.getInventory().add(loot)) {
            ItemEntity ie = new ItemEntity(level, c.x, c.y, c.z, loot);
            ie.setDefaultPickUpDelay();
            level.addFreshEntity(ie);
        }
        ElementFx.burst(level, c, e, 40, 0.8);
        ElementFx.ring(level, c.add(0, -0.3, 0), e, 1.6, 28);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 40, 0.5, 0.6, 0.5, 0.08);
        FarmFx.sound(level, c, SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.8F);
        FarmFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_BREAK, 1.2F, 0.7F);
        player.displayClientMessage(Component.translatable("farm.isekai_mahou.heist.success", e.displayName()).withColor(e.color), true);
        player.sendSystemMessage(Component.translatable("farm.isekai_mahou.heist.success.hint").withStyle(ChatFormatting.GRAY));
        MagicApi.addCounter(player, "farm.pedestal_stolen", 1);
        return true;
    }

    // =====================================================================================
    //  Ferramenta com Toque Suave
    // =====================================================================================
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof ServerPlayer player)) return;
        BlockPos pos = e.getPos();
        BlockState state = level.getBlockState(pos);
        if (!isShrineAltar(state)) return;
        ItemStack tool = player.getMainHandItem();
        Element el = elementOf(state);
        if (!hasSilkTouch(level, tool)) {
            // Dica só para quem já venceu a prova (é quem pode roubar) — e só com uma ferramenta na mão
            if (!tool.isEmpty() && MagicApi.get(player).hasFlag("shrine." + el.id)) {
                player.displayClientMessage(Component.translatable("farm.isekai_mahou.heist.need_silk").withStyle(ChatFormatting.GOLD), true);
            }
            return;
        }
        if (!canSteal(level, pos, player, el)) return;
        GlobalPos key = GlobalPos.of(level.dimension(), pos);
        long now = level.getGameTime();
        Progress p = PROGRESS.get(key);
        int hits = (p == null || now - p.lastTick > HIT_WINDOW) ? 1 : p.hits + 1;
        if (player.getAbilities().instabuild) hits = HITS;
        Vec3 c = Vec3.atCenterOf(pos);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), c.x, c.y + 0.3, c.z, 12, 0.35, 0.3, 0.35, 0.05);
        level.sendParticles(ElementFx.dust(el, 0.9F), c.x, c.y + 0.5, c.z, 6 + hits * 2, 0.4, 0.4, 0.4, 0);
        if (hits >= HITS) {
            if (!player.getAbilities().instabuild) tool.hurtAndBreak(8, player, EquipmentSlot.MAINHAND);
            steal(level, pos, player);
            return;
        }
        if (PROGRESS.size() > 64) PROGRESS.values().removeIf(old -> now - old.lastTick > HIT_WINDOW);
        PROGRESS.put(key, new Progress(hits, now));
        if (!player.getAbilities().instabuild) tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        FarmFx.sound(level, c, SoundEvents.DEEPSLATE_BRICKS_HIT, 1.0F, 0.6F + hits * 0.12F);
        player.displayClientMessage(Component.translatable("farm.isekai_mahou.heist.progress", hits, HITS).withColor(el.color), true);
    }

    public static boolean hasSilkTouch(ServerLevel level, ItemStack tool) {
        if (tool.isEmpty()) return false;
        var silk = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.SILK_TOUCH);
        return silk.isPresent() && EnchantmentHelper.getItemEnchantmentLevel(silk.get(), tool) > 0;
    }

    @Nullable
    public static Element shrineElementAt(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return isShrineAltar(s) ? elementOf(s) : null;
    }

    public static void onServerStopped(ServerStoppedEvent e) {
        PROGRESS.clear();
    }

    private ShrineHeist() {}
}
