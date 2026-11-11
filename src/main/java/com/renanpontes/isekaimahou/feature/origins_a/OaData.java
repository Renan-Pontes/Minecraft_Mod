package com.renanpontes.isekaimahou.feature.origins_a;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;

/**
 * Dados do jogador do módulo (attachment "isekai_mahou:origins_a"):
 * os Círculos de Teleporte (até 2, na ordem em que foram criados), o último círculo usado/criado
 * (destino da Runa de Retorno) e o elemento da última Metamorfose (cor do lobo, tipo da Fera Elemental).
 */
public class OaData {
    public static final int MAX_CIRCLES = 2;

    /** Um círculo: id = UUID da entidade do círculo; pos = dimensão + bloco. */
    public record Circle(UUID id, GlobalPos pos) {
        public static final Codec<Circle> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Circle::id),
                GlobalPos.CODEC.fieldOf("pos").forGetter(Circle::pos)).apply(i, Circle::new));
    }

    public static final Codec<OaData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Circle.CODEC.listOf().optionalFieldOf("circles", List.of()).forGetter(d -> d.circles),
            UUIDUtil.CODEC.optionalFieldOf("last_circle").forGetter(d -> Optional.ofNullable(d.lastCircle)),
            Codec.STRING.optionalFieldOf("form_element", "arcane").forGetter(d -> d.formElement.id)
    ).apply(i, OaData::new));

    private final List<Circle> circles = new ArrayList<>();
    private UUID lastCircle;
    private Element formElement = Element.ARCANE;

    public OaData() {}

    private OaData(List<Circle> circles, Optional<UUID> last, String formElement) {
        this.circles.addAll(circles);
        this.lastCircle = last.orElse(null);
        Element e = Element.byId(formElement);
        this.formElement = e != null ? e : Element.ARCANE;
    }

    public static OaData of(Player player) {
        return player.getData(OaRegistries.DATA);
    }

    // ---------------- Círculos ----------------
    public List<Circle> circles() { return List.copyOf(circles); }

    public Circle circle(UUID id) {
        for (Circle c : circles) if (c.id().equals(id)) return c;
        return null;
    }

    /** O outro círculo do par (ou null se só existe um). */
    public Circle partnerOf(UUID id) {
        if (circle(id) == null) return null;
        for (Circle c : circles) if (!c.id().equals(id)) return c;
        return null;
    }

    /** Adiciona um círculo; devolve o círculo mais antigo que saiu (se passou do limite). */
    public Circle addCircle(Circle c) {
        circles.add(c);
        lastCircle = c.id();
        return circles.size() > MAX_CIRCLES ? circles.remove(0) : null;
    }

    public boolean removeCircle(UUID id) {
        boolean removed = circles.removeIf(c -> c.id().equals(id));
        if (id.equals(lastCircle)) lastCircle = circles.isEmpty() ? null : circles.get(circles.size() - 1).id();
        return removed;
    }

    /** Destino da Runa de Retorno: o último círculo criado ou usado (se ainda existir). */
    public Circle lastCircle() {
        return lastCircle != null ? circle(lastCircle) : null;
    }

    public void setLastCircle(UUID id) {
        if (circle(id) != null) lastCircle = id;
    }

    // ---------------- Metamorfose ----------------
    public Element formElement() { return formElement; }

    public void setFormElement(Element e) { formElement = e != null ? e : Element.ARCANE; }
}
