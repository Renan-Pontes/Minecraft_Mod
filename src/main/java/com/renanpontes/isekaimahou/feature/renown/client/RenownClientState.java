package com.renanpontes.isekaimahou.feature.renown.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.feature.renown.RenownNetwork;

/** O que o cliente sabe do Renome: o último quadro, a lista de títulos e os títulos em uso de quem está no servidor. */
final class RenownClientState {
    static RenownNetwork.Board board = RenownNetwork.Board.EMPTY;
    static RenownNetwork.Titles titles = RenownNetwork.Titles.EMPTY;
    /** Jogador → chave do título em uso (para o nome sobre a cabeça). */
    static final Map<UUID, String> TITLES_IN_USE = new HashMap<>();

    private RenownClientState() {}

    static void onTitleSync(RenownNetwork.TitleSync sync) {
        if (sync.full()) TITLES_IN_USE.clear();
        sync.titles().forEach((id, key) -> {
            if (key.isEmpty()) TITLES_IN_USE.remove(id);
            else TITLES_IN_USE.put(id, key);
        });
    }

    static void clear() {
        board = RenownNetwork.Board.EMPTY;
        titles = RenownNetwork.Titles.EMPTY;
        TITLES_IN_USE.clear();
    }
}
