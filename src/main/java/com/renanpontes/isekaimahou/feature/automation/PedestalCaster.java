package com.renanpontes.isekaimahou.feature.automation;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * O "mago invisível" dos Pedestais Rúnicos: um FakePlayer do NeoForge (nome "[Pedestal]") que conjura em nome do
 * dono — abates contam como de jogador (drops de fazenda), mas o núcleo não dá XP a FakePlayers.
 * <p>
 * É aliado de qualquer jogador e de toda criatura com dono: assim o motor de glifos ({@code SpellContext.isAlly}) nunca
 * escolhe jogadores/bichos de estimação como alvo de efeitos ruins (dano, fogo, empurrão, lentidão...), mesmo com
 * PvP ligado (o mundo solo liga PvP), e os efeitos bons do pedestal (cura, escudos) valem para eles.
 * A FakePlayerFactory só cria FakePlayers comuns (que não sabem dizer quem é aliado), por isso esta subclasse;
 * a guarda das instâncias e a limpeza ao descarregar o mundo ficam em {@link Pedestals}.
 */
public final class PedestalCaster extends FakePlayer {
    private final UUID owner;

    PedestalCaster(ServerLevel level, GameProfile profile, UUID owner) {
        super(level, profile);
        this.owner = owner;
    }

    /** Dono dos pedestais que este conjurador representa. */
    public UUID owner() {
        return owner;
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        if (entity instanceof Player) return true;
        if (entity instanceof OwnableEntity own && own.getOwnerUUID() != null) return true;
        return super.isAlliedTo(entity);
    }
}
