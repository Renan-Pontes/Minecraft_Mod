package com.renanpontes.isekaimahou.feature.farm.machine;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.farm.FarmRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.HitResult;

/**
 * Pedestal Elemental (Gerador de Essência): a nossa versão do altar dos Santuários, que só se consegue roubando o
 * altar com Toque Suave (ou na receita cara com o núcleo do guardião). O elemento vai no estado do bloco e no item.
 */
public class ElementalPedestalBlock extends FarmMachineBlock {
    public static final EnumProperty<Element> ELEMENT = EnumProperty.create("element", Element.class, Element.BASE);

    private final MapCodec<ElementalPedestalBlock> pedestalCodec;

    public ElementalPedestalBlock(MachineKind kind, Properties props) {
        super(kind, props);
        this.pedestalCodec = simpleCodec(p -> new ElementalPedestalBlock(kind, p));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return pedestalCodec;
    }

    @Override
    protected BlockState defaultState(BlockState any) {
        return super.defaultState(any).setValue(ELEMENT, Element.FIRE);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ELEMENT);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return FarmRegistries.pedestalStack(state.getValue(ELEMENT));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Element e = state.getValue(ELEMENT);
        boolean active = state.getValue(ACTIVE);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.85, z = pos.getZ() + 0.5;
        int n = active ? 3 : 1;
        for (int i = 0; i < n; i++) {
            if (!active && random.nextInt(2) != 0) continue;
            double a = random.nextDouble() * Math.PI * 2, r = 0.3 + random.nextDouble() * 0.2;
            level.addParticle(ElementFx.dust(e, active ? 1.0F : 0.7F), x + Math.cos(a) * r, y, z + Math.sin(a) * r, 0, 0.05, 0);
        }
        if (active && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, x + (random.nextDouble() - 0.5) * 2, y + 1.2, z + (random.nextDouble() - 0.5) * 2, 0, -0.5, 0);
        }
    }
}
