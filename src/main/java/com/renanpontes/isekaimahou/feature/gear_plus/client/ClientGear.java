package com.renanpontes.isekaimahou.feature.gear_plus.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.gear_plus.WeaponBond;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Cópia de cliente dos dados do módulo (Vínculo da arma, recarga do kit e as peças que a recuperação devolveria),
 * vinda do pacote de sincronização.
 */
final class ClientGear {
    private static int kills;
    private static long kitWait;
    private static long syncedAt;
    /** Kit que o servidor devolveria (ainda em NBT: as pilhas são montadas no 1º uso, com o mundo já carregado). */
    private static ListTag kitTag = new ListTag();
    private static String kitPath = "";
    @Nullable
    private static List<ItemStack> kit;

    static void onSync(CompoundTag tag) {
        kills = tag.getInt("kills");
        kitWait = tag.getLong("kit_wait");
        syncedAt = Util.getMillis();
        kitTag = tag.getList("kit", Tag.TAG_COMPOUND);
        kitPath = tag.getString("kit_path");
        kit = null;
    }

    static void reset() {
        kills = 0;
        kitWait = 0;
        syncedAt = 0;
        kitTag = new ListTag();
        kitPath = "";
        kit = null;
    }

    /**
     * Peças que a recuperação devolveria (a melhor que o jogador já carregou em cada vaga), ou null se o servidor
     * ainda não mandou o kit desta classe — quem chama mostra o kit básico.
     */
    @Nullable
    static List<ItemStack> kit(MagicPath path) {
        if (!kitPath.equals(path.id)) return null;
        if (kit == null) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return null;
            List<ItemStack> out = new ArrayList<>(kitTag.size());
            for (int i = 0; i < kitTag.size(); i++) {
                ItemStack.parse(level.registryAccess(), kitTag.getCompound(i)).ifPresent(out::add);
            }
            kit = out;
        }
        return kit;
    }

    static int kills() {
        return kills;
    }

    /** Nível do Vínculo da classe atual (0 se a classe não tem arma que desperta). */
    static int bondLevel() {
        MagicData md = ClientMagic.data();
        return WeaponBond.hasBond(md.path()) ? WeaponBond.level(kills, md.level()) : 0;
    }

    /** Ticks que faltam para recuperar o kit de graça (contados no cliente desde a última sincronização). */
    static long kitWaitTicks() {
        long passed = (Util.getMillis() - syncedAt) / 50L;
        return Math.max(0, kitWait - passed);
    }

    private ClientGear() {}
}
