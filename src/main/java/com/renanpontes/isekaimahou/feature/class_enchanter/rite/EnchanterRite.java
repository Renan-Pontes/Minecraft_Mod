package com.renanpontes.isekaimahou.feature.class_enchanter.rite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_enchanter.AntiMagic;
import com.renanpontes.isekaimahou.feature.class_enchanter.EnchFx;
import com.renanpontes.isekaimahou.feature.class_enchanter.EnchRegistries;
import com.renanpontes.isekaimahou.feature.class_enchanter.Enchants;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * O RITO DE INSCRIÇÃO em andamento: 5 s ao lado de uma mesa de encantamento (as runas saem da mesa e se gravam no
 * corpo do Encantador). Se ele se afastar (> 6 blocos), morrer, sair ou trocar de mundo, o rito para e nada é gasto.
 * Ao completar: confere tudo de novo, queima a oferenda e sobe o rank ({@code ritual_rank.enchanter} e
 * {@code ritual_rank} +1), com XP, glifos, elemento, itens, letreiro e cena.
 */
public final class EnchanterRite {
    public static final int DURATION = 100;
    public static final double TABLE_RANGE = 6.0;

    private record State(BlockPos table, int rank, ServerLevel level, int[] ticks) {}

    private static final Map<UUID, State> RUNNING = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, EnchanterRite::onServerTick);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> RUNNING.remove(e.getEntity().getUUID()));
    }

    public static boolean running(ServerPlayer sp) {
        return RUNNING.containsKey(sp.getUUID());
    }

    // =====================================================================================
    //  Requisitos
    // =====================================================================================
    /** O que falta para o próximo rank (lista vazia = pronto). Sem mesa por perto conta como falta se {@code needTable}. */
    public static List<MutableComponent> missing(ServerPlayer sp, EnchanterRanks.Rank r, boolean needTable) {
        List<MutableComponent> out = new ArrayList<>();
        MagicData d = MagicApi.get(sp);
        if (d.circle() < r.circle()) out.add(Component.translatable("message.isekai_mahou.enchanter.need_circle", r.circle(), d.circle()));
        if (d.level() < r.level()) out.add(Component.translatable("message.isekai_mahou.enchanter.need_level", r.level(), d.level()));
        for (EnchanterRanks.Req q : r.reqs()) {
            int have = q.have(d);
            if (have < q.amount()) out.add(q.label().append(Component.literal(" (" + have + "/" + q.amount() + ")")));
        }
        boolean creative = sp.getAbilities().instabuild;
        for (EnchanterRanks.Offer o : r.offering()) {
            int have = o.have(sp);
            if (!creative && have < o.count()) {
                out.add(Component.translatable("message.isekai_mahou.enchanter.need_item", o.count(), o.item().get().getDescription(), have));
            }
        }
        if (needTable && findTable(sp) == null) out.add(Component.translatable("message.isekai_mahou.enchanter.need_table"));
        return out;
    }

    @Nullable
    public static BlockPos findTable(ServerPlayer sp) {
        BlockPos base = sp.blockPosition();
        int r = (int) Math.ceil(TABLE_RANGE);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-r, -2, -r), base.offset(r, 2, r))) {
            if (!(sp.serverLevel().getBlockState(p).getBlock() instanceof EnchantingTableBlock)) continue;
            double dd = p.distToCenterSqr(sp.position());
            if (dd < bestD) {
                bestD = dd;
                best = p.immutable();
            }
        }
        return best != null && bestD <= TABLE_RANGE * TABLE_RANGE ? best : null;
    }

    /** Pedido da aba (botão): procura a mesa perto. */
    public static void request(ServerPlayer sp) {
        BlockPos table = findTable(sp);
        if (table == null) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.need_table").withStyle(ChatFormatting.RED), false);
            no(sp);
            return;
        }
        tryStart(sp, table);
    }

    /** Começa o rito (já com a mesa escolhida). */
    public static void tryStart(ServerPlayer sp, BlockPos table) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.ENCHANTER) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.not_enchanter").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        if (running(sp)) return;
        int next = EnchanterRanks.current(d) + 1;
        EnchanterRanks.Rank r = EnchanterRanks.get(next);
        if (r == null) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.max_rank").withStyle(ChatFormatting.GOLD), false);
            return;
        }
        List<MutableComponent> miss = missing(sp, r, false);
        if (!miss.isEmpty()) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.enchanter.not_ready", r.title()).withStyle(ChatFormatting.RED));
            for (MutableComponent m : miss) sp.sendSystemMessage(Component.literal(" • ").append(m).withStyle(ChatFormatting.GRAY));
            no(sp);
            return;
        }
        RUNNING.put(sp.getUUID(), new State(table, next, sp.serverLevel(), new int[] {0}));
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.rite_begin", r.title()).withStyle(ChatFormatting.AQUA), true);
        EnchFx.sound(sp.serverLevel(), Vec3.atCenterOf(table), SoundEvents.ENCHANTMENT_TABLE_USE, 1.2F, 0.6F);
    }

    private static void no(ServerPlayer sp) {
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.0F);
    }

    // =====================================================================================
    //  Tique
    // =====================================================================================
    private static void onServerTick(ServerTickEvent.Post e) {
        if (RUNNING.isEmpty()) return;
        MinecraftServer server = e.getServer();
        Iterator<Map.Entry<UUID, State>> it = RUNNING.entrySet().iterator();
        List<Runnable> done = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, State> en = it.next();
            State st = en.getValue();
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            if (sp == null || !sp.isAlive() || sp.serverLevel() != st.level()
                    || sp.position().distanceToSqr(Vec3.atCenterOf(st.table())) > (TABLE_RANGE + 1) * (TABLE_RANGE + 1)
                    || !(st.level().getBlockState(st.table()).getBlock() instanceof EnchantingTableBlock)) {
                if (sp != null) {
                    sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.rite_broken").withStyle(ChatFormatting.RED), true);
                    EnchFx.sound(sp.serverLevel(), sp.position(), SoundEvents.BEACON_DEACTIVATE, 0.8F, 0.7F);
                }
                it.remove();
                continue;
            }
            int t = ++st.ticks()[0];
            fx(sp, st, t);
            if (t >= DURATION) {
                it.remove();
                done.add(() -> complete(sp, st.rank()));
            }
        }
        for (Runnable r : done) r.run();
    }

    private static void fx(ServerPlayer sp, State st, int t) {
        ServerLevel level = st.level();
        Vec3 table = Vec3.atCenterOf(st.table()).add(0, 0.7, 0);
        Vec3 body = sp.position().add(0, 1.0, 0);
        float prog = t / (float) DURATION;
        if (t % 2 == 0) {
            // runas saem da mesa e voam para o corpo (count 0 = velocidade dirigida)
            Vec3 d = table.subtract(body);
            for (int i = 0; i < 3; i++) {
                level.sendParticles(ParticleTypes.ENCHANT, body.x, body.y, body.z, 0, d.x, d.y, d.z, 1.0);
            }
            double a = t * 0.25;
            double h = sp.getY() + 0.1 + prog * 2.0;
            for (int k = 0; k < 3; k++) {
                double ang = a + k * Math.PI * 2 / 3;
                level.sendParticles(EnchFx.dust(k == 0 ? EnchFx.GOLD : EnchFx.TEAL, 1.0F), sp.getX() + Math.cos(ang) * 1.3, h,
                        sp.getZ() + Math.sin(ang) * 1.3, 1, 0, 0, 0, 0);
            }
        }
        if (t % 20 == 1) {
            EnchFx.sigil(level, sp.position().add(0, 0.08, 0), 2.2, EnchFx.TEAL, EnchFx.GOLD, t * 0.05F);
            EnchFx.sound(level, body, SoundEvents.ENCHANTMENT_TABLE_USE, 0.9F, 0.7F + prog * 0.8F);
        }
        if (t == 60) EnchFx.sound(level, body, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.6F);
        if (t == 90) EnchFx.sound(level, body, SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.2F);
    }

    // =====================================================================================
    //  Concluir
    // =====================================================================================
    private static void complete(ServerPlayer sp, int rank) {
        MagicData d = MagicApi.get(sp);
        EnchanterRanks.Rank r = EnchanterRanks.get(rank);
        if (r == null || EnchanterRanks.current(d) + 1 != rank) return;
        List<MutableComponent> miss = missing(sp, r, false);
        if (!miss.isEmpty()) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.enchanter.not_ready", r.title()).withStyle(ChatFormatting.RED));
            for (MutableComponent m : miss) sp.sendSystemMessage(Component.literal(" • ").append(m).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!sp.getAbilities().instabuild) {
            for (EnchanterRanks.Offer o : r.offering()) consume(sp, o.item().get(), o.count());
        }
        grant(sp, r);
    }

    /** Sobe o rank (também usado pelo comando de teste). */
    public static void grant(ServerPlayer sp, EnchanterRanks.Rank r) {
        ServerLevel level = sp.serverLevel();
        MagicApi.addCounter(sp, Enchants.RANK, 1);
        MagicApi.addCounter(sp, "ritual_rank", 1);
        MagicApi.giveXp(sp, r.xp(), "ritual");
        int taught = 0;
        for (String g : r.glyphs()) {
            Glyph glyph = GlyphRegistry.get(g);
            if (glyph != null && MagicApi.learnGlyph(sp, glyph.id())) taught++;
        }
        if (r.awaken() != null && !MagicApi.awakenElement(sp, r.awaken())) MagicApi.giveElementXp(sp, r.awaken(), 60);
        MagicApi.giveElementXp(sp, Element.LIGHT, 30 * r.rank());
        MagicApi.giveElementXp(sp, Element.WIND, 20 * r.rank());
        for (Supplier<ItemStack> gift : r.gifts()) {
            try {
                ItemStack s = gift.get();
                if (s != null && !s.isEmpty()) Rewards.give(sp, s);
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.warn("class_enchanter: presente do rank {} falhou: {}", r.rank(), ex.toString());
            }
        }

        Component title = Component.translatable("message.isekai_mahou.enchanter.rank_up_title", AntiMagic.roman(r.rank()), r.title());
        Rewards.banner(sp, title, EnchFx.TEAL);
        Rewards.toast(sp, title, Component.translatable("message.isekai_mahou.enchanter.rank_up_toast"),
                new ItemStack(EnchRegistries.CODEX.get()), EnchFx.TEAL);
        Cutscenes.play(sp, "ascension", sp, title, Component.translatable("message.isekai_mahou.enchanter.rank_up_sub"), EnchFx.TEAL, 60, false);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.enchanter.rank_up", r.title()).withStyle(ChatFormatting.AQUA));
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.enchanter.rank_perks", Enchants.stackCap(sp), Enchants.capacity(sp),
                AntiMagic.roman(Enchants.signatureCap(sp)), Math.round((Enchants.durationMult(sp) - 1F) * 100)).withStyle(ChatFormatting.GRAY));
        if (taught == 0 && !r.glyphs().isEmpty()) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.enchanter.rank_no_glyph").withStyle(ChatFormatting.DARK_GRAY));
        }
        Vec3 c = sp.position().add(0, 1, 0);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 60, 0.6, 0.9, 0.6, 0.4);
        level.sendParticles(ParticleTypes.ENCHANT, c.x, c.y, c.z, 120, 0.8, 1.0, 0.8, 1.2);
        EnchFx.sigil(level, sp.position().add(0, 0.08, 0), 3.0, EnchFx.GOLD, EnchFx.TEAL, 0F);
        EnchFx.sound(level, c, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        EnchFx.sound(level, c, SoundEvents.PLAYER_LEVELUP, 1.0F, 0.8F);
    }

    private static void consume(ServerPlayer sp, Item item, int count) {
        int left = count;
        for (List<ItemStack> list : List.of(sp.getInventory().items, sp.getInventory().offhand)) {
            for (ItemStack s : list) {
                if (left <= 0) return;
                if (!s.is(item)) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
    }

    public static void clear() {
        RUNNING.clear();
    }

    private EnchanterRite() {}
}
