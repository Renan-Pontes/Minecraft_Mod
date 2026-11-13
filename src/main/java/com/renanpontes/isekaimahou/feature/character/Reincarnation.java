package com.renanpontes.isekaimahou.feature.character;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.classes.ClassTalents;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.talents.Talents;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * release 1.0 — os DOIS lugares de recomeçar (palavra do dono: "um local pra resetar e outro pra trocar de classe e
 * Raça"):
 * <ul>
 *   <li><b>Altar do Recomeço</b> ({@code new_beginnings_altar}): devolve todos os pontos de talento da classe atual e
 *       desfaz a especialização ({@link ClassTalents#reset}). Custo médio ({@link #RENEWAL}).</li>
 *   <li><b>Altar da Reencarnação</b> ({@code reincarnation_altar}): troca de RAÇA ({@link #RACE}, pela tela de raça) e de
 *       CLASSE ({@link #CLASS}). Custo MUITO alto: Cristais de Mana + 1 Núcleo de Guardião (drop de chefe). O nível, o
 *       Círculo e os elementos ficam; o que é só da classe antiga se perde: talentos e especialização, glifos de origem
 *       (e as magias montadas com eles), os graus de ritual/contadores da classe e os itens de classe no inventário.</li>
 * </ul>
 * Tudo é conferido de novo no servidor (distância do altar, custo, classe válida). Criativo não paga.
 */
public final class Reincarnation {
    /** Custo de uma ação: Cristais de Mana + Núcleos de Guardião (qualquer elemento). */
    public record Cost(int crystals, int cores) {
        /** O jogador tem tudo? (funciona no cliente: lê o inventário.) */
        public boolean canPay(Player p) {
            if (p.getAbilities().instabuild) return true;
            return p.getInventory().countItem(CoreRegistries.MANA_CRYSTAL.get()) >= crystals && countCores(p) >= cores;
        }
    }

    public static final Cost RENEWAL = new Cost(12, 0);
    public static final Cost RACE = new Cost(32, 1);
    public static final Cost CLASS = new Cost(64, 1);

    /** Ações do pacote {@link CharacterNetwork.AltarAction}. */
    public static final int ACT_RENEW = 0, ACT_CLASS = 1;
    private static final double REACH = 8.0;

    /** Altar do Recomeço aberto por jogador (a ação só vale perto dele). */
    private static final Map<UUID, GlobalPos> RENEWAL_SESSION = new ConcurrentHashMap<>();

    static void forget(UUID id) {
        RENEWAL_SESSION.remove(id);
    }

    static void clearAll() {
        RENEWAL_SESSION.clear();
    }

    // =====================================================================================
    //  Custo
    // =====================================================================================
    /** Núcleos de Guardião (de qualquer elemento) no inventário. */
    public static int countCores(Player p) {
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (isCore(st)) n += st.getCount();
        }
        return n;
    }

    private static boolean isCore(ItemStack st) {
        if (st.isEmpty()) return false;
        for (var core : CoreRegistries.GUARDIAN_CORES.values()) {
            if (st.is(core.get())) return true;
        }
        return false;
    }

    /** Cobra o custo (criativo não paga). False = não tinha tudo (nada é tirado). */
    static boolean pay(ServerPlayer sp, Cost c) {
        if (sp.getAbilities().instabuild) return true;
        if (!c.canPay(sp)) return false;
        Item crystal = CoreRegistries.MANA_CRYSTAL.get();
        if (c.crystals() > 0) {
            sp.getInventory().clearOrCountMatchingItems(s -> s.is(crystal), c.crystals(), sp.inventoryMenu.getCraftSlots());
        }
        int left = c.cores();
        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!isCore(st)) continue;
            int take = Math.min(left, st.getCount());
            st.shrink(take);
            left -= take;
        }
        sp.inventoryMenu.broadcastChanges();
        return true;
    }

    /** "64 Cristais de Mana + 1 Núcleo de Guardião" (para mensagens). */
    public static Component describe(Cost c) {
        Component crystals = Component.translatable("message.isekai_mahou.character.cost_crystals", c.crystals(),
                CoreRegistries.MANA_CRYSTAL.get().getDescription());
        if (c.cores() <= 0) return crystals;
        return Component.translatable("message.isekai_mahou.character.cost_with_core", crystals, c.cores());
    }

    // =====================================================================================
    //  Altar do Recomeço
    // =====================================================================================
    /** Altar do Recomeço usado: abre a confirmação. */
    public static void openRenewal(ServerPlayer sp, BlockPos pos) {
        if (MagicApi.get(sp).path() == MagicPath.NONE) {
            CharacterService.fail(sp, Component.translatable("message.isekai_mahou.character.altar_no_class"));
            return;
        }
        RENEWAL_SESSION.put(sp.getUUID(), GlobalPos.of(sp.level().dimension(), pos.immutable()));
        hint(sp);
        PacketDistributor.sendToPlayer(sp, new CharacterNetwork.Open(CharacterNetwork.MODE_RENEWAL, pos.immutable()));
    }

    private static boolean nearRenewal(ServerPlayer sp) {
        GlobalPos g = RENEWAL_SESSION.get(sp.getUUID());
        if (g == null || !g.dimension().equals(sp.level().dimension())) return false;
        if (!sp.level().getBlockState(g.pos()).is(CharacterContent.RENEWAL_ALTAR.get())) return false;
        return sp.distanceToSqr(g.pos().getCenter()) <= REACH * REACH;
    }

    /** Pontos gastos + especialização da classe atual (0 e vazio = nada para devolver). */
    private static boolean hasTalentsToReset(ServerPlayer sp) {
        String cls = MagicApi.get(sp).path().id;
        return Talents.data(sp).spent(cls) > 0 || !Talents.data(sp).subclass(cls).isEmpty();
    }

    static void action(ServerPlayer sp, int action, int arg) {
        switch (action) {
            case ACT_RENEW -> renew(sp, false);
            case ACT_CLASS -> changeClass(sp, MagicPath.byIndex(arg), false);
            default -> { }
        }
    }

    /** Recomeço: devolve os pontos de talento e desfaz a especialização. {@code free} = comando de op (sem altar/custo). */
    static boolean renew(ServerPlayer sp, boolean free) {
        MagicData d = MagicApi.get(sp);
        if (d.path() == MagicPath.NONE) return false;
        if (!free && !nearRenewal(sp)) {
            CharacterService.fail(sp, Component.translatable("message.isekai_mahou.character.renewal_far"));
            return false;
        }
        if (!hasTalentsToReset(sp)) {
            CharacterService.fail(sp, Component.translatable("message.isekai_mahou.character.renewal_nothing"));
            return false;
        }
        if (!free && !pay(sp, RENEWAL)) {
            CharacterService.fail(sp, Component.translatable("message.isekai_mahou.character.cant_pay", describe(RENEWAL)));
            return false;
        }
        ClassTalents.reset(sp);
        MahouAnims.play(sp, MahouAnims.KNEEL_CHANNEL);
        ServerLevel level = sp.serverLevel();
        GlobalPos g = RENEWAL_SESSION.get(sp.getUUID());
        Vec3 at = g != null && !free ? g.pos().getCenter().add(0, 0.6, 0) : sp.position().add(0, 1.0, 0);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, at.x, at.y + 0.8, at.z, 40, 0.8, 0.8, 0.8, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.0, sp.getZ(), 24, 0.4, 0.8, 0.4, 0.03);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sp.getX(), sp.getY() + 1.0, sp.getZ(), 16, 0.5, 0.8, 0.5, 0.0);
        RaceFx.ring(level, sp.position().add(0, 0.1, 0), 1.6, 18, RaceFx.dust(0x9FE8B0, 1.2F));
        RaceFx.sound(level, sp.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 0.9F);
        RaceFx.sound(level, sp.position(), SoundEvents.PLAYER_LEVELUP, 0.6F, 1.4F);
        Rewards.banner(sp, Component.translatable("message.isekai_mahou.character.renewed"), 0x9FE8B0);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.character.renewed_body",
                Component.keybind("key.isekai_mahou.menu")).withColor(0xCFEFD8));
        return true;
    }

    // =====================================================================================
    //  Altar da Reencarnação: troca de classe
    // =====================================================================================
    /** Por que a classe {@code target} não pode ser escolhida agora (null = pode). */
    private static Component classBlock(ServerPlayer sp, MagicPath target) {
        MagicData d = MagicApi.get(sp);
        if (d.path() == MagicPath.NONE) return Component.translatable("message.isekai_mahou.character.altar_no_class");
        if (target == null || !target.pickableAtStart()) return Component.translatable("message.isekai_mahou.character.class_invalid");
        if (target == d.path()) return Component.translatable("message.isekai_mahou.character.same_class", target.displayName());
        // Classe de história (Hollow): a alma tomada pelo Vazio não reencarna em outra classe
        if (!d.path().pickableAtStart()) return Component.translatable("message.isekai_mahou.character.class_story_locked", d.path().displayName());
        if (RaceForms.charging(sp) || RaceForms.formOf(sp) != null) return Component.translatable("message.isekai_mahou.character.class_in_form");
        return null;
    }

    /** Troca a classe. {@code free} = comando de op (sem altar/custo). */
    static boolean changeClass(ServerPlayer sp, MagicPath target, boolean free) {
        Component block = classBlock(sp, target);
        if (block != null) {
            CharacterService.fail(sp, block);
            return false;
        }
        if (!free && !CharacterService.nearAltar(sp)) {
            CharacterService.fail(sp, Component.translatable("message.isekai_mahou.character.altar_far"));
            return false;
        }
        if (!free && !pay(sp, CLASS)) {
            CharacterService.fail(sp, Component.translatable("message.isekai_mahou.character.cant_pay", describe(CLASS)));
            return false;
        }
        MagicData d = MagicApi.get(sp);
        MagicPath old = d.path();
        int lostGlyphs = stripOldClass(sp, d, old);

        d.setPath(target);
        for (String g : CoreGlyphs.starter(target.category())) MagicApi.learnGlyphSilently(sp, IsekaiMahou.id(g));
        ClassKits.give(sp, target);
        d.fillMana();
        d.markDirty();
        // Os módulos da classe nova entregam o que é dela (arma, glifos de origem, afinidade) como na 1ª escolha
        NeoForge.EVENT_BUS.post(new MagicProgressEvent.PathChosen(sp));
        CharacterContent.data(sp).addRebirth();
        CharacterService.flush(sp);

        MahouAnims.play(sp, MahouAnims.TRANSFORM_RISE);
        ServerLevel level = sp.serverLevel();
        Vec3 c = RaceFx.center(sp);
        int rgb = target.rgb();
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 50, 0.4, 1.0, 0.4, 0.05);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 80, 0.6, 1.0, 0.6, 0.45);
        level.sendParticles(RaceFx.dust(rgb, 1.8F), c.x, c.y, c.z, 60, 0.8, 1.1, 0.8, 0.02);
        RaceFx.ring(level, sp.position().add(0, 0.1, 0), 2.2, 24, RaceFx.dust(rgb, 1.5F));
        RaceFx.sound(level, sp.position(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 1.0F, 0.7F);
        RaceFx.sound(level, sp.position(), SoundEvents.TOTEM_USE, 0.7F, 1.0F);
        Rewards.banner(sp, Component.translatable("message.isekai_mahou.character.class_reborn", target.displayName()), rgb);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.character.class_reborn_body", old.displayName(),
                target.displayName(), lostGlyphs).withColor(0xE8E2F4));
        return true;
    }

    /**
     * Tira o que é só da classe antiga: talentos/especialização, glifos de origem (e as magias que os usam), contadores
     * e graus de ritual da classe e os itens de classe do inventário. Devolve quantos glifos foram esquecidos.
     */
    private static int stripOldClass(ServerPlayer sp, MagicData d, MagicPath old) {
        // Talentos: ainda com a classe antiga em vigor (o reset é "da classe atual")
        if (ClassTalents.available()) ClassTalents.reset(sp);

        // Glifos de Magia de Origem da classe antiga
        Set<ResourceLocation> lost = new HashSet<>();
        for (ResourceLocation id : d.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null && g.origin() == old) lost.add(id);
        }
        if (!lost.isEmpty()) {
            d.glyphs().removeAll(lost);
            for (ResourceLocation id : lost) d.setGlyphXp(id, 0);
            // Magias montadas com eles saem dos loadouts (ficariam quebradas nos slots)
            for (int l = 0; l < MagicData.LOADOUTS; l++) {
                for (int s = 0; s < com.renanpontes.isekaimahou.core.Progression.SLOTS; s++) {
                    SpellSlot slot = d.peekSlot(l, s);
                    if (slot == null) continue;
                    List<MoveTrigger> broken = new ArrayList<>();
                    for (Map.Entry<MoveTrigger, SpellRecipe> e : slot.all().entrySet()) {
                        SpellRecipe r = e.getValue();
                        if (r != null && r.glyphs().stream().anyMatch(lost::contains)) broken.add(e.getKey());
                    }
                    for (MoveTrigger t : broken) d.setSlotRecipe(l, s, t, null);
                }
            }
        }

        // Contadores só da classe (grau de ritual dela e os "<classe>.<algo>"); o grau geral e o resto ficam
        String prefix = old.id + ".";
        String rank = "ritual_rank." + old.id;
        for (String key : new ArrayList<>(d.counters().keySet())) {
            if (!key.equals(rank) && !key.startsWith(prefix)) continue;
            int v = d.counter(key);
            if (v != 0) MagicApi.addCounter(sp, key, -v);
        }
        // Flags só da classe ("<classe>.<algo>": kit inicial dela entregue, etapas do sistema próprio...)
        for (String flag : new ArrayList<>(d.flags())) {
            if (flag.startsWith(prefix)) d.setFlag(flag, false);
        }

        // Itens de classe (vinculados à alma) que estão com o jogador
        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isItemOf(old, inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        }
        sp.inventoryMenu.broadcastChanges();
        d.markDirty();
        return lost.size();
    }

    /** Item da classe {@code p}? (as mesmas regras do vínculo de alma do núcleo, mas só para uma classe.) */
    private static boolean isItemOf(MagicPath p, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!IsekaiMahou.MODID.equals(id.getNamespace())) return false;
        String path = id.getPath();
        ResourceLocation kit = p.kitWeapon();
        if (kit != null && kit.getPath().equals(path)) return true;
        String prefix = p.weaponPrefix();
        if (prefix != null && !prefix.isEmpty() && path.startsWith(prefix + "_t") && path.length() == prefix.length() + 3
                && Character.isDigit(path.charAt(path.length() - 1))) return true;
        return path.startsWith(p.id + "_");
    }

    // =====================================================================================
    //  Dica de primeira vez
    // =====================================================================================
    /** "Existem dois altares..." — uma vez por jogador, pela API de dicas do HUD. */
    static void hint(ServerPlayer sp) {
        com.renanpontes.isekaimahou.feature.ui_hud.Hints.show(sp, "character.altars");
    }

    private Reincarnation() {}
}
