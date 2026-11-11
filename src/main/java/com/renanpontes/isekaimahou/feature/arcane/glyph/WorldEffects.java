package com.renanpontes.isekaimahou.feature.arcane.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneRegistries;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneUtil;
import com.renanpontes.isekaimahou.feature.arcane.network.OreRevealPayload;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.IShearable;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.PacketDistributor;

/** Efeitos arcanos que mexem no mundo: Quebrar, Colher, Luz, Colocar Bloco, Detectar. */
public final class WorldEffects {

    // =====================================================================================
    //  Permissões (proteção de spawn, mods de proteção, mobGriefing)
    // =====================================================================================

    /** O conjurador pode quebrar este bloco? Para jogadores dispara o BlockEvent.BreakEvent (mods de proteção). */
    static boolean mayBreak(SpellContext ctx, BlockPos pos, BlockState state) {
        ServerLevel level = ctx.level();
        ServerPlayer sp = ctx.casterPlayer();
        if (sp != null) {
            if (!level.mayInteract(sp, pos)) return false;
            return !CommonHooks.fireBlockBreak(level, sp.gameMode.getGameModeForPlayer(), sp, pos, state).isCanceled();
        }
        return EventHooks.canEntityGrief(level, ctx.caster());
    }

    /**
     * Coloca {@code state} (já colocado no mundo) e confirma com o evento de colocação; se algum mod cancelar,
     * restaura o bloco antigo. Retorna true se ficou.
     */
    static boolean confirmPlace(SpellContext ctx, BlockSnapshot before, Direction face) {
        LivingEntity c = ctx.caster();
        if (c instanceof Player || c == null) {
            if (EventHooks.onBlockPlace(c, before, face)) {
                before.restore();
                return false;
            }
            return true;
        }
        if (!EventHooks.canEntityGrief(ctx.level(), c) || EventHooks.onBlockPlace(c, before, face)) {
            before.restore();
            return false;
        }
        return true;
    }

    // =====================================================================================
    //  Quebrar (C1)
    // =====================================================================================
    public static class BreakEffect extends EffectGlyph {
        public BreakEffect() {
            super("break", 8);
            element(Element.ARCANE);
            tier(1);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        /** Em criaturas: racha a armadura e derruba o escudo de quem está defendendo. */
        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || target == ctx.caster()) return;
            ServerLevel level = ctx.level();
            if (le instanceof Player p && p.isBlocking()) {
                p.disableShield();
                ArcaneUtil.sound(level, p.position(), SoundEvents.SHIELD_BREAK, 1.0F, 1.0F);
            }
            int dmg = Math.max(1, Math.round(4 * stats.power()));
            boolean hit = false;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack armor = le.getItemBySlot(slot);
                if (!armor.isEmpty() && armor.isDamageableItem()) {
                    armor.hurtAndBreak(dmg, le, slot);
                    hit = true;
                }
            }
            if (hit) {
                Vec3 c = ArcaneUtil.center(le);
                level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.2);
                ArcaneUtil.sound(level, c, SoundEvents.ITEM_BREAK, 0.6F, 1.4F);
            }
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            ServerLevel level = ctx.level();
            BlockPos center = hit.getBlockPos();
            int r = Mth.clamp(stats.aoe, 0, 3); // Expandir: 3x3, 5x5, 7x7 no plano da face atingida
            Direction.Axis axis = hit.getDirection().getAxis();
            int broken = 0;
            boolean tooHard = false;
            for (int a = -r; a <= r; a++) {
                for (int b = -r; b <= r; b++) {
                    BlockPos p = switch (axis) {
                        case X -> center.offset(0, a, b);
                        case Y -> center.offset(a, 0, b);
                        case Z -> center.offset(a, b, 0);
                    };
                    int res = breakOne(ctx, level, p, stats);
                    if (res > 0) broken++;
                    else if (res < 0) tooHard = true;
                }
            }
            Vec3 at = hit.getLocation();
            if (broken > 0) {
                level.sendParticles(ArcaneUtil.dust(ctx, 1.2F), at.x, at.y, at.z, 8 + broken * 2, 0.3 + r * 0.5, 0.3 + r * 0.5,
                        0.3 + r * 0.5, 0);
            } else if (tooHard) {
                level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.02);
                ArcaneUtil.sound(level, at, SoundEvents.SHIELD_BLOCK, 0.6F, 1.6F);
                ArcaneUtil.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.arcane_too_hard"));
            }
        }

        /** 1 = quebrou, 0 = nada a quebrar/sem permissão, -1 = duro demais para a ferramenta virtual. */
        private static int breakOne(SpellContext ctx, ServerLevel level, BlockPos pos, SpellStats stats) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof LiquidBlock) return 0;
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0) return -1; // rocha-mãe, barreiras, portais
            int tier = Mth.clamp(stats.amplify, 0, 2); // 0 ferro, 1 diamante, 2 netherita
            if (hardness > 20 + 30 * tier) return -1;
            ItemStack tool = virtualTool(level, state, tier, stats);
            if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) return -1;
            if (!mayBreak(ctx, pos, state)) return 0;
            BlockEntity be = level.getBlockEntity(pos);
            LivingEntity c = ctx.caster();
            boolean creative = c instanceof Player p && p.getAbilities().instabuild;
            if (!level.destroyBlock(pos, false, c)) return 0;
            if (!creative) Block.dropResources(state, level, pos, be, c, tool);
            return 1;
        }

        /** Ferramenta "imaginária" do tipo certo para o bloco (Fortuna III / Toque Suave pelos aumentos). */
        private static ItemStack virtualTool(ServerLevel level, BlockState state, int tier, SpellStats stats) {
            Item[] axes = {Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE};
            Item[] shovels = {Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL};
            Item[] hoes = {Items.IRON_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE};
            Item[] picks = {Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE};
            Item item;
            if (state.is(BlockTags.MINEABLE_WITH_AXE)) item = axes[tier];
            else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) item = shovels[tier];
            else if (state.is(BlockTags.MINEABLE_WITH_HOE)) item = hoes[tier];
            else if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) item = picks[tier];
            else {
                // Sem tag de ferramenta (teia, lã...): tesoura ou espada se elas forem a ferramenta certa
                Item[] swords = {Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
                item = picks[tier];
                for (Item other : new Item[]{Items.SHEARS, swords[tier]}) {
                    if (new ItemStack(other).isCorrectToolForDrops(state)) {
                        item = other;
                        break;
                    }
                }
            }
            ItemStack tool = new ItemStack(item);
            var enchants = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            if (stats.silkTouch) tool.enchant(enchants.getOrThrow(Enchantments.SILK_TOUCH), 1);
            else if (stats.fortune) tool.enchant(enchants.getOrThrow(Enchantments.FORTUNE), 3);
            return tool;
        }
    }

    // =====================================================================================
    //  Colher (C1)
    // =====================================================================================
    public static class HarvestEffect extends EffectGlyph {
        public HarvestEffect() {
            super("harvest", 6);
            element(Element.ARCANE);
            tier(1);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        /** Em animais tosquiáveis (ovelhas, mooshrooms...): tosquia como uma tesoura. */
        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (target == ctx.caster() || target instanceof Player || !(target instanceof IShearable shearable)) return;
            ServerLevel level = ctx.level();
            ItemStack shears = new ItemStack(Items.SHEARS);
            Player player = ctx.casterPlayer();
            if (!shearable.isShearable(player, shears, level, target.blockPosition())) return;
            for (ItemStack drop : shearable.onSheared(player, shears, level, target.blockPosition())) {
                shearable.spawnShearedDrop(level, target.blockPosition(), drop);
            }
            Vec3 c = ArcaneUtil.center(target);
            level.sendParticles(ArcaneUtil.dust(ctx, 1.0F), c.x, c.y, c.z, 8, 0.4, 0.3, 0.4, 0);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            ServerLevel level = ctx.level();
            BlockPos center = hit.getBlockPos();
            int r = Math.min(8, Math.round(stats.radius(2.0F)));
            ItemStack tool = new ItemStack(Items.IRON_HOE);
            if (stats.fortune) {
                tool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), 3);
            }
            int n = 0;
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, 2, r))) {
                int dx = p.getX() - center.getX(), dz = p.getZ() - center.getZ();
                if (dx * dx + dz * dz > r * r + 1) continue;
                if (harvestOne(ctx, level, p.immutable(), tool)) n++;
            }
            if (n > 0) {
                Vec3 at = Vec3.atCenterOf(center);
                ArcaneUtil.ring(level, at.add(0, 0.6, 0), r + 0.3, ArcaneUtil.dust(ctx, 1.0F), 12 + r * 6);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.8, at.z, 6 + n, r * 0.6, 0.3, r * 0.6, 0);
                ArcaneUtil.sound(level, at, SoundEvents.CROP_BREAK, 1.0F, 1.0F);
            }
        }

        private static boolean harvestOne(SpellContext ctx, ServerLevel level, BlockPos p, ItemStack tool) {
            BlockState s = level.getBlockState(p);
            Block b = s.getBlock();
            BlockState replant;
            if (b instanceof CropBlock crop) {
                if (!crop.isMaxAge(s)) return false;
                replant = crop.getStateForAge(0);
            } else if (b instanceof NetherWartBlock) {
                if (s.getValue(NetherWartBlock.AGE) < NetherWartBlock.MAX_AGE) return false;
                replant = s.setValue(NetherWartBlock.AGE, 0);
            } else if (b instanceof CocoaBlock) {
                if (s.getValue(CocoaBlock.AGE) < CocoaBlock.MAX_AGE) return false;
                replant = s.setValue(CocoaBlock.AGE, 0);
            } else if (b instanceof SweetBerryBushBlock) {
                int age = s.getValue(SweetBerryBushBlock.AGE);
                if (age < 2 || !mayBreak(ctx, p, s)) return false;
                // Igual a colher com a mão: 1-2 frutas (+1 se madura), o arbusto fica
                Block.popResource(level, p, new ItemStack(Items.SWEET_BERRIES, 1 + level.random.nextInt(2) + (age == 3 ? 1 : 0)));
                level.setBlock(p, s.setValue(SweetBerryBushBlock.AGE, 1), 2);
                level.playSound(null, p, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F,
                        0.8F + level.random.nextFloat() * 0.4F);
                return true;
            } else if ((s.is(Blocks.MELON) || s.is(Blocks.PUMPKIN)) && hasAttachedStem(level, p)) {
                replant = Blocks.AIR.defaultBlockState();
            } else if ((s.is(Blocks.SUGAR_CANE) || s.is(Blocks.CACTUS) || s.is(Blocks.BAMBOO)) && level.getBlockState(p.below()).is(b)
                    && !level.getBlockState(p.below(2)).is(b)) {
                // Corta acima da base (a base continua crescendo); o que estiver em cima cai junto
                replant = Blocks.AIR.defaultBlockState();
            } else {
                return false;
            }
            if (!mayBreak(ctx, p, s)) return false;
            LivingEntity c = ctx.caster();
            List<ItemStack> drops = new ArrayList<>(Block.getDrops(s, level, p, null, c, tool));
            if (!replant.isAir()) {
                // A replantação "gasta" uma semente da colheita
                for (ItemStack d : drops) {
                    if (d.getItem() instanceof BlockItem bi && bi.getBlock() == b) {
                        d.shrink(1);
                        break;
                    }
                }
            }
            level.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_DESTROY_BLOCK, p, Block.getId(s));
            level.setBlock(p, replant, 3);
            for (ItemStack d : drops) if (!d.isEmpty()) Block.popResource(level, p, d);
            return true;
        }

        private static boolean hasAttachedStem(ServerLevel level, BlockPos p) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(p.relative(d)).getBlock() instanceof AttachedStemBlock) return true;
            }
            return false;
        }
    }

    // =====================================================================================
    //  Luz (C1) — Inverter: escuridão
    // =====================================================================================
    public static class ConjureLightEffect extends EffectGlyph {
        public ConjureLightEffect() {
            super("conjure_light", 5);
            element(Element.ARCANE);
            tier(1);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        /** Em você: visão noturna. Em outros: brilho (aparecem através das paredes). Inverter: cegueira. */
        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            Vec3 c = ArcaneUtil.center(le);
            if (stats.inverted) {
                if (le == ctx.caster()) return; // em você: só apaga as luzes em volta (no bloco), sem se cegar
                ctx.status(le, MobEffects.BLINDNESS, 60, 0, stats);
                level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.4, c.z, 10, 0.3, 0.2, 0.3, 0.01);
                return;
            }
            if (le == ctx.caster()) ctx.status(le, MobEffects.NIGHT_VISION, 600, 0, stats);
            else le.addEffect(new MobEffectInstance(MobEffects.GLOWING, stats.duration(200), 0, false, false), ctx.caster());
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 6, 0.3, 0.4, 0.3, 0.02);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            ServerLevel level = ctx.level();
            var light = ArcaneRegistries.ARCANE_LIGHT.get();
            if (stats.inverted) {
                // Apaga as luzes arcanas em volta
                int r = Math.round(stats.radius(4));
                int n = 0;
                for (BlockPos p : BlockPos.betweenClosed(hit.getBlockPos().offset(-r, -r, -r), hit.getBlockPos().offset(r, r, r))) {
                    if (level.getBlockState(p).is(light)) {
                        level.removeBlock(p, false);
                        level.sendParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.1, 0.1, 0.1, 0.01);
                        n++;
                    }
                }
                if (n > 0) ArcaneUtil.sound(level, hit.getLocation(), SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.4F);
                return;
            }
            BlockPos pos = hit.getBlockPos();
            if (!light.canHost(level.getBlockState(pos))) pos = pos.relative(hit.getDirection());
            if (!light.canHost(level.getBlockState(pos))) return;
            BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, pos);
            if (!light.placeFor(level, pos, stats.duration(3600))) return;
            if (!confirmPlace(ctx, before, hit.getDirection())) return;
            Vec3 at = Vec3.atCenterOf(pos);
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.05);
            level.sendParticles(ArcaneUtil.dust(ctx, 1.0F), at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0);
            ArcaneUtil.sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.4F);
        }
    }

    // =====================================================================================
    //  Colocar Bloco (C2)
    // =====================================================================================
    public static class PlaceBlockEffect extends EffectGlyph {
        public PlaceBlockEffect() {
            super("place_block", 6);
            element(Element.ARCANE);
            tier(2);
            minCircle(2);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            ServerLevel level = ctx.level();
            Direction face = hit.getDirection();
            BlockPos base = level.getBlockState(hit.getBlockPos()).canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(face);
            int r = Mth.clamp(stats.aoe, 0, 3); // Expandir: plataforma/parede 3x3, 5x5...
            Direction.Axis axis = face.getAxis();
            LivingEntity c = ctx.caster();
            ServerPlayer sp = ctx.casterPlayer();
            ItemStack off = sp != null ? sp.getOffhandItem() : ItemStack.EMPTY;
            boolean real = sp != null && off.getItem() instanceof BlockItem;
            int placed = 0;
            for (int a = -r; a <= r; a++) {
                for (int b = -r; b <= r; b++) {
                    BlockPos p = switch (axis) {
                        case X -> base.offset(0, a, b);
                        case Y -> base.offset(a, 0, b);
                        case Z -> base.offset(a, b, 0);
                    };
                    if (real) {
                        if (off.isEmpty()) break;
                        // Proteção de spawn é checada pelo pacote de clique, não pelo BlockItem: checa aqui
                        if (!level.getBlockState(p).canBeReplaced() || !level.mayInteract(sp, p)) continue;
                        // Usa o fluxo normal de colocar item (consome no sobrevivência, eventos, sons, modo aventura)
                        var res = off.useOn(new UseOnContext(sp, InteractionHand.OFF_HAND, new BlockHitResult(Vec3.atCenterOf(p), face, p, false)));
                        if (res.consumesAction()) placed++;
                    } else if (placeConjured(ctx, level, p, face, stats)) {
                        placed++;
                    }
                }
            }
            if (placed > 0) {
                Vec3 at = Vec3.atCenterOf(base);
                level.sendParticles(ArcaneUtil.dust(ctx, 1.2F), at.x, at.y, at.z, 10 + placed * 3, 0.4 + r * 0.5, 0.4 + r * 0.5,
                        0.4 + r * 0.5, 0);
                if (!real) ArcaneUtil.sound(level, at, SoundEvents.AMETHYST_BLOCK_PLACE, 1.0F, 0.8F);
            } else if (c != null) {
                Vec3 at = Vec3.atCenterOf(base);
                level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.01);
            }
        }

        private static boolean placeConjured(SpellContext ctx, ServerLevel level, BlockPos p, Direction face, SpellStats stats) {
            var stone = ArcaneRegistries.CONJURED_STONE.get();
            ServerPlayer sp = ctx.casterPlayer();
            if (sp != null && (!level.mayInteract(sp, p) || !sp.mayBuild())) return false;
            BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, p);
            if (!stone.placeFor(level, p, stats.duration(600))) return false;
            return confirmPlace(ctx, before, face);
        }
    }

    // =====================================================================================
    //  Detectar (C2): brilho em criaturas (raio 24) + minérios destacados (raio 8)
    // =====================================================================================
    public static class DetectEffect extends EffectGlyph {
        public DetectEffect() {
            super("detect", 10);
            element(Element.ARCANE);
            tier(2);
            minCircle(2);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!ArcaneUtil.firstThisTick(ctx, "detect")) return;
            detect(ctx, target.position(), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!ArcaneUtil.firstThisTick(ctx, "detect")) return;
            detect(ctx, hit.getLocation(), stats);
        }

        private static void detect(SpellContext ctx, Vec3 center, SpellStats stats) {
            ServerLevel level = ctx.level();
            LivingEntity c = ctx.caster();
            int dur = stats.duration(200);

            // Criaturas: brilho (visível através das paredes)
            float r = stats.radius(24);
            int mobs = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r),
                    e -> e != c && e.isAlive() && !e.isSpectator() && e.distanceToSqr(center) <= r * r)) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur, 0, false, false), c);
                mobs++;
            }

            // Minérios: só quem conjurou vê (pacote para o cliente desenhar o contorno)
            int ores = 0;
            ServerPlayer sp = ctx.casterPlayer();
            if (sp != null) {
                int or = Math.min(16, Math.round(stats.radius(8)));
                BlockPos cp = BlockPos.containing(center);
                List<BlockPos> found = new ArrayList<>();
                List<Integer> colors = new ArrayList<>();
                for (BlockPos p : BlockPos.betweenClosed(cp.offset(-or, -or, -or), cp.offset(or, or, or))) {
                    if (found.size() >= OreRevealPayload.MAX) break;
                    BlockState s = level.getBlockState(p);
                    if (!s.is(Tags.Blocks.ORES)) continue;
                    found.add(p.immutable());
                    colors.add(oreColor(s));
                }
                ores = found.size();
                if (ores > 0) {
                    PacketDistributor.sendToPlayer(sp, new OreRevealPayload(found, colors, dur));
                    for (int i = 0; i < Math.min(64, found.size()); i++) {
                        BlockPos p = found.get(i);
                        level.sendParticles(sp, ArcaneUtil.dust(colors.get(i), 1.0F), true, p.getX() + 0.5, p.getY() + 0.5,
                                p.getZ() + 0.5, 4, 0.35, 0.35, 0.35, 0);
                    }
                }
                ArcaneUtil.actionbar(sp, Component.translatable("message.isekai_mahou.arcane_detect", mobs, ores));
            }

            // Onda de detecção
            for (int i = 1; i <= 3; i++) {
                ArcaneUtil.ring(level, center.add(0, 0.2, 0), i * 2.0, ArcaneUtil.dust(ctx, 1.0F), 16 * i);
            }
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 0.5, center.z, 12, 0.5, 0.5, 0.5, 0.08);
            ArcaneUtil.sound(level, center, SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.6F);
        }

        /** Cor do contorno de cada minério (RGB). */
        private static int oreColor(BlockState s) {
            if (s.is(Tags.Blocks.ORES_DIAMOND)) return 0x5CF0E0;
            if (s.is(Tags.Blocks.ORES_EMERALD)) return 0x2FE065;
            if (s.is(Tags.Blocks.ORES_GOLD)) return 0xFCEE4B;
            if (s.is(Tags.Blocks.ORES_IRON)) return 0xE3B18F;
            if (s.is(Tags.Blocks.ORES_COPPER)) return 0xE8784A;
            if (s.is(Tags.Blocks.ORES_REDSTONE)) return 0xFF2A2A;
            if (s.is(Tags.Blocks.ORES_LAPIS)) return 0x3566F0;
            if (s.is(Tags.Blocks.ORES_COAL)) return 0x8A8A8A;
            if (s.is(Tags.Blocks.ORES_QUARTZ)) return 0xF2EDE6;
            if (s.is(Tags.Blocks.ORES_NETHERITE_SCRAP)) return 0xA0685A;
            return 0xC9A6FF;
        }
    }

    private WorldEffects() {}
}
