package com.renanpontes.isekaimahou.feature.bosses_d.entity.kit;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialAvatar;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialElement;

/**
 * Monta os kits de um Avatar: [kit do corpo (pai A / elemento), kit do pai B (só os 2 golpes de assinatura, em
 * fusões), golpes comuns]. O golpe máximo vem sempre do kit do corpo.
 */
public final class AvatarKits {

    public static List<AvatarKit> create(TrialAvatar boss, Element element) {
        List<AvatarKit> out = new ArrayList<>();
        AvatarKit main = forElement(boss, TrialElement.body(element));
        out.add(main);
        if (element.isFusion()) {
            AvatarKit accent = forElement(boss, element.parentB);
            accent.markSecondary();
            out.add(accent);
        }
        out.add(new CommonKit(boss, main));
        return out;
    }

    private static AvatarKit forElement(TrialAvatar boss, Element e) {
        return switch (e) {
            case FIRE -> new FireKit(boss);
            case WATER -> new WaterKit(boss);
            case EARTH -> new EarthKit(boss);
            case WIND -> new WindKit(boss);
            case LIGHT -> new LightKit(boss);
            case SHADOW -> new ShadowKit(boss);
            default -> new ArcaneKit(boss);
        };
    }

    private AvatarKits() {}
}
