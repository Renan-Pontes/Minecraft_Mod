package com.renanpontes.isekaimahou.feature.flat_lands;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Omikuji (おみくじ): o sorteio da sorte do dia no Santuário Torii. Cada jogador tira UMA vez por dia do jogo,
 * deixando uma oferenda (tag {@code #isekai_mahou:sakura_offerings}: pepita de ouro, esmeralda ou moeda da guilda).
 * O papel sorteado vira item ({@code sakura_fortune_slip}) e aplica a sorte por 1 dia (24000 ticks).
 * Tradição: papel de azar se AMARRA no santuário (usar o papel na caixa) — a má sorte fica para trás.
 */
public final class Omikuji {
    /** 1 dia do Minecraft. */
    public static final int DAY_TICKS = 24000;
    public static final TagKey<Item> OFFERINGS = TagKey.create(Registries.ITEM, IsekaiMahou.id("sakura_offerings"));
    private static final String KEY_DAY = "isekai_mahou_omikuji_day";

    /** As sortes (pesos somam 100). amp = nível do efeito; blessing = bênção (true) ou má sorte (false); amp < 0 = nada. */
    public enum Fortune {
        DAIKICHI("daikichi", 7, true, 2, 0xFF6FA8),
        CHUKICHI("chukichi", 13, true, 1, 0xFF8FBF),
        KICHI("kichi", 18, true, 1, 0xFFA8CC),
        SHOKICHI("shokichi", 20, true, 0, 0xFFC4DC),
        SUEKICHI("suekichi", 17, true, -1, 0xD8C8A8),
        KYO("kyo", 17, false, 0, 0x9A86B8),
        DAIKYO("daikyo", 8, false, 1, 0x6E5A8E);

        public final String id;
        public final int weight;
        public final boolean blessing;
        public final int amp;
        public final int color;

        Fortune(String id, int weight, boolean blessing, int amp, int color) {
            this.id = id;
            this.weight = weight;
            this.blessing = blessing;
            this.amp = amp;
            this.color = color;
        }

        public boolean isCurse() {
            return !blessing && amp >= 0;
        }

        public Component title() {
            return Component.translatable("fortune.isekai_mahou.sakura." + id).withStyle(s -> s.withColor(color));
        }

        public Component reading() {
            return Component.translatable("fortune.isekai_mahou.sakura." + id + ".text");
        }

        public static Fortune byIndex(int i) {
            Fortune[] all = values();
            return all[Math.floorMod(i, all.length)];
        }

        public static Fortune byId(String id) {
            for (Fortune f : values()) if (f.id.equals(id)) return f;
            return null;
        }

        static Fortune roll(RandomSource r) {
            int n = r.nextInt(100);
            for (Fortune f : values()) {
                n -= f.weight;
                if (n < 0) return f;
            }
            return SHOKICHI;
        }
    }

    private static CompoundTag persisted(Player p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) pd.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return pd.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Dia atual do mundo (Overworld) — o mesmo para todo mundo no servidor. */
    public static long today(ServerLevel level) {
        return level.getServer().overworld().getDayTime() / DAY_TICKS;
    }

    public static boolean drewToday(ServerPlayer p) {
        CompoundTag t = persisted(p);
        return t.contains(KEY_DAY) && t.getLong(KEY_DAY) == today(p.serverLevel());
    }

    public static void reset(ServerPlayer p) {
        persisted(p).remove(KEY_DAY);
    }

    /** Sorteia (ou usa {@code forced}), marca o dia, aplica a sorte e entrega o papel. */
    public static Fortune draw(ServerPlayer p, BlockPos at, Fortune forced) {
        ServerLevel level = p.serverLevel();
        Fortune f = forced != null ? forced : Fortune.roll(p.getRandom());
        persisted(p).putLong(KEY_DAY, today(level));
        apply(p, f);

        ItemStack slip = FortuneSlips.create(f);
        if (!p.getInventory().add(slip)) p.drop(slip, false);

        p.sendSystemMessage(Component.translatable("message.isekai_mahou.sakura.omikuji.drawn", f.title())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        p.sendSystemMessage(Component.literal("  ").append(f.reading().copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        p.displayClientMessage(f.title(), true);

        double x = at.getX() + 0.5, y = at.getY() + 1.0, z = at.getZ() + 0.5;
        if (f.blessing) {
            level.sendParticles(ParticleTypes.CHERRY_LEAVES, x, y + 0.5, z, 18 + 8 * Math.max(0, f.amp), 0.8, 0.6, 0.8, 0.0);
            level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F + 0.15F * f.amp);
            level.playSound(null, at, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.7F, 1.5F);
        } else {
            level.sendParticles(ParticleTypes.SMOKE, x, y + 0.3, z, 14, 0.4, 0.3, 0.4, 0.01);
            level.playSound(null, at, SoundEvents.WITCH_AMBIENT, SoundSource.BLOCKS, 0.6F, 1.4F);
        }
        if (f.isCurse()) {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.sakura.omikuji.tie_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        return f;
    }

    /** Troca a sorte do dia: tira a anterior (bênção ou má sorte) e aplica a nova por 1 dia. */
    public static void apply(ServerPlayer p, Fortune f) {
        p.removeEffect(FlatLandsContent.SAKURA_BLESSING);
        p.removeEffect(FlatLandsContent.SAKURA_MISFORTUNE);
        if (f.amp < 0) return;
        Holder<MobEffect> eff = f.blessing ? FlatLandsContent.SAKURA_BLESSING : FlatLandsContent.SAKURA_MISFORTUNE;
        p.addEffect(new MobEffectInstance(eff, DAY_TICKS, f.amp, false, true, true));
    }

    /** Amarra um papel de azar no santuário: a má sorte some e o papel fica preso na corda (consumido). */
    public static boolean tie(ServerPlayer p, BlockPos at, ItemStack slip) {
        Fortune f = FortuneSlips.fortune(slip);
        if (f == null || !f.isCurse()) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.sakura.omikuji.keep_good"), true);
            return false;
        }
        slip.consume(1, p);
        p.removeEffect(FlatLandsContent.SAKURA_MISFORTUNE);
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 14, 0.5, 0.4, 0.5, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.6F, 1.6F);
        p.sendSystemMessage(Component.translatable("message.isekai_mahou.sakura.omikuji.tied").withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }

    private Omikuji() {}
}
