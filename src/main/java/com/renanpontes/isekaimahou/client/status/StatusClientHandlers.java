package com.renanpontes.isekaimahou.client.status;

import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.status.StatusPayloads;

/** Handlers dos pacotes do núcleo A1 (chamados só no cliente, já na thread principal). */
public final class StatusClientHandlers {
    public static void statusList(StatusPayloads.StatusList p) {
        ClientStatuses.receive(p);
    }

    public static void reaction(StatusPayloads.ReactionText p) {
        ElementReactions.Reaction r = ElementReactions.Reaction.byOrdinal(p.reaction());
        if (r != null) ReactionTexts.add(p.entityId(), r, p.x(), p.y(), p.z());
    }

    public static void place(StatusPayloads.PlaceChange p) {
        ClientPlaces.set(p.entered().orElse(null));
        if (p.entered().isPresent()) PlaceBanner.showEnter(p.entered().get(), p.firstVisit());
        else p.left().ifPresent(PlaceBanner::showLeave);
    }

    private StatusClientHandlers() {}
}
