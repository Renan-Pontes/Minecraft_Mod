package com.renanpontes.isekaimahou.client;

import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.network.ClientMagic;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * v1.0 — Troca dos 9 loadouts no cliente:
 * <ul>
 *   <li><b>Alt + scroll</b> e <b>Mouse 5 + scroll</b>: loadout anterior/próximo (os dois fazem o mesmo);</li>
 *   <li><b>Alt + 1..9</b>: vai direto ao loadout (a tecla da hotbar NÃO troca o item enquanto o Alt está segurado).</li>
 * </ul>
 * A troca é prevista na hora ({@link ClientMagic#predictLoadout}) e confirmada pelo servidor
 * ({@link CorePayloads.SelectLoadout}); mostra "Loadout N/9" com as magias e toca um som de página.
 */
public final class LoadoutInput {

    /** Registra os ouvintes (chamado pelo cliente do núcleo). */
    public static void init() {
        NeoForge.EVENT_BUS.addListener(LoadoutInput::onScroll);
        NeoForge.EVENT_BUS.addListener(LoadoutInput::onTickPre);
    }

    /** Algum modificador de loadout está segurado (Alt ou Mouse 5)? */
    public static boolean modifierDown() {
        return CoreKeys.LOADOUT_MODIFIER.isDown() || CoreKeys.LOADOUT_MOUSE.isDown();
    }

    private static void onScroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.player.isSpectator() || !modifierDown()) return;
        double delta = e.getScrollDeltaY();
        if (delta == 0) return;
        int dir = delta > 0 ? -1 : 1;
        select(ClientMagic.data().activeLoadout() + dir);
        e.setCanceled(true);
    }

    /** Antes do vanilla ler as teclas da hotbar: com Alt segurado, 1..9 escolhem o loadout. */
    private static void onTickPre(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !CoreKeys.LOADOUT_MODIFIER.isDown()) return;
        int n = Math.min(MagicData.LOADOUTS, mc.options.keyHotbarSlots.length);
        for (int i = 0; i < n; i++) {
            boolean pressed = false;
            while (mc.options.keyHotbarSlots[i].consumeClick()) pressed = true;
            if (pressed) select(i);
        }
    }

    /** Escolhe o loadout (0..8, com volta) e avisa o servidor. */
    public static void select(int loadout) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int next = Math.floorMod(loadout, MagicData.LOADOUTS);
        boolean changed = next != ClientMagic.data().activeLoadout();
        ClientMagic.predictLoadout(next);
        PacketDistributor.sendToServer(new CorePayloads.SelectLoadout(next));
        mc.gui.setOverlayMessage(describe(ClientMagic.data(), next), false);
        if (changed) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.5F, 0.6F));
    }

    /** "Loadout 3/9 · [Z] Bola de Fogo · [X] Escudo ..." (ou "vazio"). */
    public static Component describe(MagicData d, int loadout) {
        MutableComponent out = Component.translatable("hud.isekai_mahou.loadout_selected", loadout + 1, MagicData.LOADOUTS)
                .withStyle(ChatFormatting.GOLD);
        boolean any = false;
        for (int s = 0; s < Progression.SLOTS; s++) {
            SpellRecipe r = d.peekSlot(loadout, s).main();
            if (r == null || r.isEmpty()) continue;
            any = true;
            out.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(CoreKeys.SLOTS[s].getTranslatedKeyMessage().copy().withStyle(ChatFormatting.GRAY))
                    .append(" ")
                    .append(spellName(r).copy().withColor(color(r) & 0xFFFFFF));
        }
        if (!any) out.append(" ").append(Component.translatable("hud.isekai_mahou.loadout_empty").withStyle(ChatFormatting.DARK_GRAY));
        return out;
    }

    /** Nome da magia, ou o nome da forma se ela não tem nome. */
    public static Component spellName(SpellRecipe r) {
        if (!r.name().isBlank()) return Component.literal(r.name());
        Glyph first = r.glyphs().isEmpty() ? null : GlyphRegistry.get(r.glyphs().get(0));
        return first != null ? first.displayName() : Component.literal("?");
    }

    /** Cor da magia (a escolhida, ou a do elemento dos glifos). ARGB. */
    public static int color(SpellRecipe r) {
        return r.color() >= 0 ? 0xFF000000 | r.color() : SpellCost.primaryElement(r).argb();
    }

    private LoadoutInput() {}
}
