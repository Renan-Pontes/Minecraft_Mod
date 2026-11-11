package com.renanpontes.isekaimahou.feature.ritual_witch.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchContent;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchHeat;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchProgress;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchRecipe;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchRecipes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Estado do Caldeirão Ritual.
 * <ol>
 *   <li>EMPTY: seco. Balde de água → WATER.</li>
 *   <li>WATER/BREWING: com fogo embaixo, aceita ingredientes NA ORDEM (sequência que não começa nenhuma receita estraga).
 *       A Colher Ritual conta as mexidas (mexer demais estraga).</li>
 *   <li>Depois de ~2,5 s sem mexer/adicionar, se ingredientes e mexidas batem com uma receita, ela é concluída:
 *       calor, lua e noite são conferidos (errou → RUINED). Sem fogo por 30 s → esfria e estraga.</li>
 *   <li>READY: o jogador pega as doses com frascos de vidro. RUINED: lodo preto, limpa com balde.</li>
 * </ol>
 */
public class WitchCauldronBlockEntity extends BlockEntity {
    public enum Phase { EMPTY, WATER, BREWING, READY, RUINED }

    /** Por que a mistura estragou (chave de tradução message.isekai_mahou.witch.ruin.&lt;id&gt;). */
    public enum Ruin {
        ORDER("order"), OVER_STIRRED("over_stirred"), HEAT_LOW("heat_low"), HEAT_HIGH("heat_high"),
        MOON("moon"), NIGHT("night"), COLD("cold");

        final String key;

        Ruin(String id) { this.key = "message.isekai_mahou.witch.ruin." + id; }
    }

    public static final int WATER_COLOR = 0x3F76E4;
    public static final int SLUDGE_COLOR = 0x1E1824;
    private static final int SETTLE_TICKS = 50;
    private static final int COLD_RUIN_TICKS = 600;
    /**
     * Intervalo mínimo (ticks) desde a última TENTATIVA de pôr ingrediente. Segurar o botão repete o clique a cada
     * 4 ticks e nunca abre esse intervalo: só um clique novo (soltar e clicar de novo) adiciona outro ingrediente,
     * então segurar não despeja a pilha (o que estragaria a ordem).
     */
    private static final int CLICK_GAP = 7;

    private Phase phase = Phase.EMPTY;
    private final List<Item> ingredients = new ArrayList<>();
    private int stirs;
    private int settle;
    private int cold;
    private int color = WATER_COLOR;
    private ItemStack result = ItemStack.EMPTY;
    private int bottles;
    private int maxBottles;
    private UUID brewer;
    /** Total de mexidas (qualquer fase): só para animar o redemoinho no cliente. */
    private int swirls;
    /** Tick da última tentativa de pôr ingrediente, aceita ou não (não salvo). */
    private long lastAttempt = Long.MIN_VALUE / 2;

    // ---- só no cliente (animação) ----
    /** Ticks restantes do redemoinho depois de mexer. */
    public int stirAnim;
    private int lastSwirls;
    /** Cor exibida (vai se aproximando de {@link #color} suavemente). */
    public float shownR = -1, shownG, shownB;
    /** Ângulo do redemoinho da superfície e a velocidade atual (rad/tick). */
    public float spin, spinSpeed;

    public WitchCauldronBlockEntity(BlockPos pos, BlockState state) {
        super(WitchContent.CAULDRON_BE.get(), pos, state);
    }

    // =====================================================================================
    //  Leitura
    // =====================================================================================
    public Phase phase() { return phase; }
    public List<Item> ingredients() { return Collections.unmodifiableList(ingredients); }
    public int stirs() { return stirs; }
    public int color() { return color; }
    public int bottles() { return bottles; }
    public ItemStack result() { return result; }

    /** Altura do líquido dentro do bloco (0..1), ou -1 se não tem líquido. */
    public float liquidHeight() {
        float frac = switch (phase) {
            case EMPTY -> -1F;
            case READY -> maxBottles <= 0 ? 0.4F : 0.35F + 0.65F * bottles / (float) maxBottles;
            default -> 1F;
        };
        return frac < 0 ? -1F : (5F + 9F * frac) / 16F;
    }

    public int comparatorSignal() {
        return switch (phase) {
            case EMPTY, RUINED -> 0;
            case WATER -> 1;
            case BREWING -> Math.min(14, 1 + ingredients.size() * 2);
            case READY -> Math.min(15, Math.max(1, bottles * 5));
        };
    }

    // =====================================================================================
    //  Ações (servidor)
    // =====================================================================================
    public boolean fillWater() {
        if (phase != Phase.EMPTY) return false;
        phase = Phase.WATER;
        color = WATER_COLOR;
        resetBrew();
        changed();
        return true;
    }

    /** Esvazia (água, preparo ou lodo). Retorna o estado anterior. */
    public Phase drain(ServerLevel level) {
        Phase old = phase;
        if (old == Phase.RUINED || old == Phase.BREWING) {
            puff(level, ParticleTypes.LARGE_SMOKE, 10, 0.25);
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.4F);
        }
        phase = Phase.EMPTY;
        color = WATER_COLOR;
        resetBrew();
        clearResult();
        changed();
        return old;
    }

    /** Coloca um ingrediente. Retorna true se o item foi aceito (deve ser consumido). */
    public boolean addIngredient(ServerLevel level, Player player, Item item) {
        if (phase != Phase.WATER && phase != Phase.BREWING) return false;
        long now = level.getGameTime();
        boolean freshClick = now - lastAttempt >= CLICK_GAP;
        lastAttempt = now;
        if (!freshClick) return false;
        if (WitchHeat.below(level, worldPosition) == WitchHeat.NONE) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.witch.cold").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (ingredients.size() >= WitchRecipe.MAX_INGREDIENTS) {
            ruin(level, Ruin.ORDER);
            return true;
        }
        brewer = player.getUUID();
        phase = Phase.BREWING;
        ingredients.add(item);
        cold = 0;
        settle = SETTLE_TICKS;
        color = mix(color, ingredientColor(item), ingredients.size() == 1 ? 0.6F : 0.45F);

        double y = worldPosition.getY() + liquidHeight();
        level.sendParticles(ParticleTypes.SPLASH, worldPosition.getX() + 0.5, y, worldPosition.getZ() + 0.5, 12, 0.2, 0.05, 0.2, 0.1);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | color),
                worldPosition.getX() + 0.5, y + 0.1, worldPosition.getZ() + 0.5, 10, 0.2, 0.1, 0.2, 0.5);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.5F, 1.2F + level.random.nextFloat() * 0.3F);
        level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.4F, 1.6F);

        if (WitchRecipes.candidates(ingredients).isEmpty()) {
            ruin(level, Ruin.ORDER);
        } else {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.witch.added",
                    new ItemStack(item).getHoverName(), ingredients.size()).withStyle(ChatFormatting.DARK_PURPLE), true);
            changed();
        }
        return true;
    }

    /** Mexe com a Colher Ritual. */
    public void stir(ServerLevel level, Player player) {
        double y = worldPosition.getY() + Math.max(0.4, liquidHeight());
        swirls++;
        swirlParticles(level, y);
        level.playSound(null, worldPosition, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundSource.BLOCKS, 0.5F,
                1.3F + level.random.nextFloat() * 0.3F);
        if (phase != Phase.BREWING) {
            changed();
            return;
        }
        brewer = player.getUUID();
        stirs++;
        settle = SETTLE_TICKS;
        int max = 0;
        for (WitchRecipe r : WitchRecipes.candidates(ingredients)) max = Math.max(max, r.stirs());
        if (stirs > max) {
            ruin(level, Ruin.OVER_STIRRED);
            return;
        }
        WitchHeat heat = WitchHeat.below(level, worldPosition);
        player.displayClientMessage(Component.translatable("message.isekai_mahou.witch.stirred", stirs, heat.displayName())
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        changed();
    }

    /** Enche um frasco com uma dose pronta. Retorna a dose (ou vazio). */
    public ItemStack takeBottle(ServerLevel level) {
        if (phase != Phase.READY || bottles <= 0 || result.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = result.copyWithCount(1);
        bottles--;
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | color),
                worldPosition.getX() + 0.5, worldPosition.getY() + liquidHeight() + 0.1, worldPosition.getZ() + 0.5, 6, 0.15, 0.1, 0.15, 0.3);
        if (bottles <= 0) {
            phase = Phase.EMPTY;
            color = WATER_COLOR;
            clearResult();
            resetBrew();
        }
        changed();
        return out;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    public static void serverTick(Level level, BlockPos pos, BlockState state, WitchCauldronBlockEntity be) {
        if (!(level instanceof ServerLevel sl) || be.phase != Phase.BREWING) return;
        WitchHeat heat = WitchHeat.below(level, pos);
        if (heat == WitchHeat.NONE) {
            if (++be.cold >= COLD_RUIN_TICKS) {
                be.ruin(sl, Ruin.COLD);
                return;
            }
        } else {
            be.cold = 0;
        }
        if (be.settle > 0 && --be.settle == 0) {
            WitchRecipe r = WitchRecipes.exact(be.ingredients);
            if (r != null && be.stirs == r.stirs()) be.finish(sl, r, heat);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, WitchCauldronBlockEntity be) {
        if (be.stirAnim > 0) be.stirAnim--;
        // Gira devagar sempre; acelera logo depois de mexer e desacelera suavemente
        float target = be.phase == Phase.BREWING || be.phase == Phase.READY ? 0.02F : 0.006F;
        if (be.stirAnim > 0) target += 0.28F * be.stirAnim / 24F;
        be.spinSpeed += (target - be.spinSpeed) * 0.25F;
        be.spin = (be.spin + be.spinSpeed) % (float) (Math.PI * 2000);
        float r = ((be.color >> 16) & 255) / 255F, g = ((be.color >> 8) & 255) / 255F, b = (be.color & 255) / 255F;
        if (be.shownR < 0) {
            be.shownR = r; be.shownG = g; be.shownB = b;
        } else {
            be.shownR += (r - be.shownR) * 0.08F;
            be.shownG += (g - be.shownG) * 0.08F;
            be.shownB += (b - be.shownB) * 0.08F;
        }
    }

    private void finish(ServerLevel level, WitchRecipe recipe, WitchHeat heat) {
        if (heat != recipe.heat()) {
            ruin(level, heat.ordinal() < recipe.heat().ordinal() ? Ruin.HEAT_LOW : Ruin.HEAT_HIGH);
            return;
        }
        if (recipe.night() && !level.isNight()) {
            ruin(level, Ruin.NIGHT);
            return;
        }
        if (!recipe.moonAllowed(level.getMoonPhase())) {
            ruin(level, Ruin.MOON);
            return;
        }
        phase = Phase.READY;
        result = recipe.result().copyWithCount(1);
        bottles = maxBottles = Math.max(1, recipe.result().getCount());
        color = recipe.color();
        ingredients.clear();
        stirs = 0;
        settle = 0;
        changed();

        double cx = worldPosition.getX() + 0.5, cy = worldPosition.getY() + liquidHeight() + 0.1, cz = worldPosition.getZ() + 0.5;
        level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | color), cx, cy + 0.3, cz, 40, 0.3, 0.5, 0.3, 1.0);
        level.sendParticles(ParticleTypes.WITCH, cx, cy + 0.2, cz, 20, 0.35, 0.3, 0.35, 0.05);
        level.sendParticles(ParticleTypes.ENCHANT, cx, cy + 0.8, cz, 30, 0.4, 0.4, 0.4, 0.8);
        level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 0.9F);
        level.playSound(null, worldPosition, SoundEvents.WITCH_CELEBRATE, SoundSource.BLOCKS, 0.5F, 1.3F);
        int tier = recipe.revelationTier();
        if (tier > 0) {
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24;
                level.sendParticles(ParticleTypes.END_ROD, cx + Math.cos(a) * 0.9, cy + 0.2, cz + Math.sin(a) * 0.9, 1, 0, 0.05, 0, 0.02);
            }
            level.sendParticles(ParticleTypes.SOUL, cx, cy + 0.4, cz, 10, 0.3, 0.3, 0.3, 0.03);
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.5F);
        }

        ServerPlayer player = brewer == null ? null : level.getServer().getPlayerList().getPlayer(brewer);
        if (player != null) {
            MagicApi.giveXp(player, recipe.xp(), "brewing");
            MagicApi.addCounter(player, "brew", 1);
            recipe.element().ifPresent(e -> MagicApi.giveElementXp(player, e, Math.max(2, recipe.xp() / 2)));
            if (WitchProgress.learn(player, recipe.id(), false)) {
                player.sendSystemMessage(Component.translatable("message.isekai_mahou.witch.discovered",
                        WitchRecipe.displayName(recipe.id()).withStyle(ChatFormatting.LIGHT_PURPLE)).withStyle(ChatFormatting.GOLD));
            }
        }
        Component msg = Component.translatable("message.isekai_mahou.witch.ready", result.getHoverName(), bottles)
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        notifyNear(level, msg);
    }

    private void ruin(ServerLevel level, Ruin reason) {
        phase = Phase.RUINED;
        color = SLUDGE_COLOR;
        resetBrew();
        clearResult();
        changed();
        puff(level, ParticleTypes.LARGE_SMOKE, 25, 0.3);
        puff(level, ParticleTypes.SQUID_INK, 10, 0.25);
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.playSound(null, worldPosition, SoundEvents.WITCH_AMBIENT, SoundSource.BLOCKS, 0.6F, 0.6F);
        notifyNear(level, Component.translatable("message.isekai_mahou.witch.ruined",
                Component.translatable(reason.key)).withStyle(ChatFormatting.DARK_RED));
    }

    // =====================================================================================
    //  Auxiliares
    // =====================================================================================
    private void resetBrew() {
        ingredients.clear();
        stirs = 0;
        settle = 0;
        cold = 0;
    }

    private void clearResult() {
        result = ItemStack.EMPTY;
        bottles = maxBottles = 0;
    }

    /** Partículas em volta da superfície do caldeirão. */
    private void puff(ServerLevel level, net.minecraft.core.particles.ParticleOptions p, int count, double spread) {
        level.sendParticles(p, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5,
                count, spread, 0.15, spread, 0.02);
    }

    private void swirlParticles(ServerLevel level, double y) {
        int c = 0xFF000000 | (phase == Phase.RUINED ? SLUDGE_COLOR : color);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 + level.random.nextDouble() * 0.3;
            double r = 0.18 + (i % 3) * 0.1;
            level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, c),
                    worldPosition.getX() + 0.5 + Math.cos(a) * r, y + 0.05, worldPosition.getZ() + 0.5 + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.BUBBLE_POP, worldPosition.getX() + 0.5, y + 0.05, worldPosition.getZ() + 0.5, 6, 0.2, 0.02, 0.2, 0.02);
    }

    private void notifyNear(ServerLevel level, Component msg) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 144) {
                p.displayClientMessage(msg, true);
            }
        }
    }

    /** Marca para salvar, atualiza a luz (pronto = brilha) e envia ao cliente. */
    private void changed() {
        setChanged();
        if (level == null) return;
        BlockState state = getBlockState();
        boolean lit = phase == Phase.READY;
        if (state.hasProperty(WitchCauldronBlock.LIT) && state.getValue(WitchCauldronBlock.LIT) != lit) {
            state = state.setValue(WitchCauldronBlock.LIT, lit);
            level.setBlock(worldPosition, state, Block.UPDATE_ALL);
        }
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
    }

    /** Cor de um ingrediente: matiz derivado do id (estável e variado). */
    public static int ingredientColor(Item item) {
        int h = BuiltInRegistries.ITEM.getKey(item).hashCode() * 0x9E3779B1;
        float hue = ((h >>> 12) & 0xFFFF) / 65535F;
        return Mth.hsvToRgb(hue, 0.6F, 0.85F);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    // =====================================================================================
    //  Salvar / sincronizar
    // =====================================================================================
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("phase", phase.name());
        ListTag list = new ListTag();
        for (Item i : ingredients) list.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(i).toString()));
        tag.put("ingredients", list);
        tag.putInt("stirs", stirs);
        tag.putInt("settle", settle);
        tag.putInt("cold", cold);
        tag.putInt("color", color);
        tag.putInt("bottles", bottles);
        tag.putInt("max_bottles", maxBottles);
        if (!result.isEmpty()) tag.put("result", result.save(registries));
        if (brewer != null) tag.putUUID("brewer", brewer);
        tag.putInt("swirls", swirls);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            phase = Phase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException e) {
            phase = Phase.EMPTY;
        }
        ingredients.clear();
        ListTag list = tag.getList("ingredients", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) BuiltInRegistries.ITEM.getOptional(id).ifPresent(ingredients::add);
        }
        stirs = tag.getInt("stirs");
        settle = tag.getInt("settle");
        cold = tag.getInt("cold");
        color = tag.contains("color") ? tag.getInt("color") : WATER_COLOR;
        bottles = tag.getInt("bottles");
        maxBottles = tag.getInt("max_bottles");
        result = tag.contains("result", Tag.TAG_COMPOUND) ? ItemStack.parseOptional(registries, tag.getCompound("result")) : ItemStack.EMPTY;
        brewer = tag.hasUUID("brewer") ? tag.getUUID("brewer") : null;
        swirls = tag.getInt("swirls");
        // Cliente: dispara a animação do redemoinho quando alguém mexe
        if (level != null && level.isClientSide) {
            if (swirls != lastSwirls && shownR >= 0) stirAnim = 24;
            lastSwirls = swirls;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
