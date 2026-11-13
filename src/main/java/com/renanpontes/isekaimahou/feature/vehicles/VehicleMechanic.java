package com.renanpontes.isekaimahou.feature.vehicles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.vehicles.entity.MagicVehicle;
import com.renanpontes.isekaimahou.feature.vehicles.item.VehicleItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Onda 10: os SERVIÇOS do Mecânico, iguais na cidade do jogador (Oficina Mecânica, {@code settlements/CityTrades}) e
 * nas cidades do mundo ({@code cities/CityServices}). Quem chama cobra as moedas; aqui só se calcula o preço e se faz
 * o serviço.
 * <ul>
 *   <li><b>Consertar</b> ({@link #REPAIR}): o veículo guardado na mão, ou o seu veículo mais perto do Mecânico (até
 *       {@value #NEAR} blocos). Preço pela vida que falta e pelo tamanho do veículo; Oficina melhor cobra menos.</li>
 *   <li><b>Resgatar</b> ({@link #RECOVER}): um veículo seu que ficou longe (no mundo, num lugar descarregado) ou cujo
 *       item se perdeu. Você recebe um item novo com carga, vida e melhorias; a cópia antiga some onde estiver
 *       ({@link VehicleRegistry}). Se o veículo estiver carregado, a carga do baú vem junto.</li>
 *   <li><b>Reconstruir</b> ({@link #REBUILD}): um veículo seu destruído volta inteiro, com as melhorias (tanque vazio).</li>
 * </ul>
 */
public final class VehicleMechanic {
    public static final String REPAIR = "vehicle_repair", RECOVER = "vehicle_recover", REBUILD = "vehicle_rebuild";
    public static final List<String> SERVICES = List.of(REPAIR, REBUILD, RECOVER);
    /** Até onde o Mecânico enxerga o seu veículo estacionado para consertar. */
    public static final int NEAR = 16;
    /** Veículo carregado mais perto que isto do jogador não precisa de resgate (é só ir buscar). */
    private static final int HERE = 48;

    /**
     * Um serviço com preço. {@code target} = veículo no mundo a consertar; {@code hand} = conserto do item da mão;
     * {@code entry} = veículo do caderno (resgate/reconstrução).
     */
    public record Offer(String service, int cost, Component name, int hpPct, @Nullable MagicVehicle target, boolean hand,
                        @Nullable UUID entry) {
        /** Chave estável para a confirmação em dois cliques. */
        public String key() {
            return service + ":" + (entry != null ? entry : target != null ? target.getUUID() : "hand");
        }

        /** "Consertar X (40% de vida) por N moedas?" */
        public MutableComponent ask() {
            return Component.translatable("message.isekai_mahou.mechanic.ask." + service, name, hpPct, cost);
        }
    }

    /** Desconto da Oficina: nível 1 = preço cheio, nível 5 = 60%. */
    private static float discount(int shopLevel) {
        return 1.1F - 0.1F * Math.max(1, Math.min(5, shopLevel));
    }

    public static int repairCost(VehicleKind kind, float hpFrac, int shopLevel) {
        float missing = 1F - Math.max(0F, Math.min(1F, hpFrac));
        return Math.max(1, Math.round(missing * (3F + kind.price / 20F) * discount(shopLevel)));
    }

    public static int recoverCost(VehicleKind kind, int shopLevel) {
        return Math.max(2, Math.round((4F + kind.price / 10F) * discount(shopLevel)));
    }

    public static int rebuildCost(VehicleKind kind, int shopLevel) {
        return Math.max(5, Math.round(kind.price * 0.35F * discount(shopLevel)));
    }

    // =====================================================================================
    //  Orçamento
    // =====================================================================================
    /** Todos os serviços úteis para o jogador agora, na ordem consertar → reconstruir → resgatar. */
    public static List<Offer> offers(ServerPlayer sp, Vec3 at, int shopLevel) {
        List<Offer> out = new ArrayList<>();
        for (String s : SERVICES) {
            Offer o = quote(sp, at, shopLevel, s);
            if (o != null) out.add(o);
        }
        return out;
    }

    /** O orçamento de um serviço (null = nada a fazer). */
    @Nullable
    public static Offer quote(ServerPlayer sp, Vec3 at, int shopLevel, String service) {
        return switch (service) {
            case REPAIR -> repairQuote(sp, at, shopLevel);
            case REBUILD -> registryQuote(sp, shopLevel, true);
            case RECOVER -> registryQuote(sp, shopLevel, false);
            default -> null;
        };
    }

    @Nullable
    private static Offer repairQuote(ServerPlayer sp, Vec3 at, int shopLevel) {
        ItemStack hand = sp.getMainHandItem();
        if (hand.getItem() instanceof VehicleItem vi) {
            VehicleStacks.Data d = VehicleStacks.read(hand);
            if (d.hp() < 0.999F) {
                return new Offer(REPAIR, repairCost(vi.kind(), d.hp(), shopLevel), hand.getHoverName(), Math.round(d.hp() * 100), null, true, null);
            }
        }
        MagicVehicle best = null;
        double bestD = Double.MAX_VALUE;
        AABB box = new AABB(at, at).inflate(NEAR);
        for (MagicVehicle v : sp.serverLevel().getEntitiesOfClass(MagicVehicle.class, box, v -> v.isAlive() && sp.getUUID().equals(v.ownerId()))) {
            if (v.healthFraction() >= 0.999F) continue;
            double dist = v.distanceToSqr(at);
            if (dist < bestD) {
                bestD = dist;
                best = v;
            }
        }
        if (best == null) return null;
        return new Offer(REPAIR, repairCost(best.kind(), best.healthFraction(), shopLevel), best.getDisplayName(),
                Math.round(best.healthFraction() * 100), best, false, null);
    }

    @Nullable
    private static Offer registryQuote(ServerPlayer sp, int shopLevel, boolean wrecked) {
        for (VehicleRegistry.Entry e : VehicleRegistry.get(sp.server).of(sp.getUUID())) {
            VehicleKind kind = e.kind();
            if (kind == null) continue;
            if (wrecked) {
                if (e.state != VehicleRegistry.WRECKED) continue;
                return new Offer(REBUILD, rebuildCost(kind, shopLevel), name(kind), 0, null, false, e.id);
            }
            if (e.state == VehicleRegistry.WRECKED || !lost(sp, e)) continue;
            return new Offer(RECOVER, recoverCost(kind, shopLevel), name(kind), Math.round(e.hp * 100), null, false, e.id);
        }
        return null;
    }

    private static MutableComponent name(VehicleKind kind) {
        return Component.translatable("item.isekai_mahou." + kind.id);
    }

    /** Perdido = não está no inventário do dono nem carregado perto dele. */
    private static boolean lost(ServerPlayer sp, VehicleRegistry.Entry e) {
        if (e.state == VehicleRegistry.PACKED) return !inInventory(sp, e);
        MagicVehicle v = loaded(sp.server, e);
        return v == null || v.level() != sp.level() || v.distanceToSqr(sp) > (double) HERE * HERE;
    }

    private static boolean inInventory(ServerPlayer sp, VehicleRegistry.Entry e) {
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack st = sp.getInventory().getItem(i);
            if (!(st.getItem() instanceof VehicleItem)) continue;
            VehicleStacks.Data d = VehicleStacks.read(st);
            if (e.id.equals(d.id()) && d.gen() >= e.gen) return true;
        }
        return false;
    }

    /** A entidade do veículo, se estiver carregada em alguma dimensão. */
    @Nullable
    private static MagicVehicle loaded(MinecraftServer server, VehicleRegistry.Entry e) {
        if (e.state != VehicleRegistry.OUT || e.entity == null) return null;
        if (e.dim != null) {
            ServerLevel sl = server.getLevel(e.dim);
            if (sl != null && sl.getEntity(e.entity) instanceof MagicVehicle v && v.isAlive()) return v;
        }
        for (ServerLevel sl : server.getAllLevels()) {
            if (sl.getEntity(e.entity) instanceof MagicVehicle v && v.isAlive()) return v;
        }
        return null;
    }

    // =====================================================================================
    //  Serviço (já pago)
    // =====================================================================================
    /**
     * Faz o serviço de um orçamento que acabou de ser refeito e cobrado. Devolve a frase de "pronto" (null = não deu:
     * o veículo saiu de perto ou já foi consertado entre o orçamento e a confirmação — quem chamou devolve as moedas).
     */
    @Nullable
    public static Component perform(ServerPlayer sp, Offer o) {
        MinecraftServer server = sp.server;
        switch (o.service()) {
            case REPAIR -> {
                if (o.hand()) {
                    ItemStack hand = sp.getMainHandItem();
                    if (!(hand.getItem() instanceof VehicleItem)) return null;
                    VehicleStacks.Data d = VehicleStacks.read(hand);
                    VehicleStacks.write(hand, new VehicleStacks.Data(d.fuel(), d.id(), d.gen(), 1F, d.upg()));
                } else {
                    MagicVehicle v = o.target();
                    if (v == null || !v.isAlive()) return null;
                    v.setHealth(v.getMaxHealth());
                    VehicleRegistry.get(server).touch(server, v);
                    sp.serverLevel().sendParticles(ParticleTypes.WAX_OFF, v.getX(), v.getY() + v.kind().height * 0.6, v.getZ(), 16,
                            v.kind().width * 0.3, 0.3, v.kind().width * 0.3, 0.05);
                }
                sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.7F, 1.3F);
                return Component.translatable("message.isekai_mahou.mechanic.done.repair", o.name());
            }
            case RECOVER, REBUILD -> {
                VehicleRegistry reg = VehicleRegistry.get(server);
                VehicleRegistry.Entry e = reg.get(o.entry());
                VehicleKind kind = e == null ? null : e.kind();
                if (kind == null || !sp.getUUID().equals(e.owner)) return null;
                boolean rebuild = REBUILD.equals(o.service());
                if (rebuild != (e.state == VehicleRegistry.WRECKED)) return null;
                float hp = rebuild ? 1F : Math.max(0.01F, e.hp);
                int fuel = rebuild ? 0 : e.fuel;
                int upg = e.upg;
                if (!rebuild) {
                    // carregado em algum lugar: os dados mais frescos são os dele, e a carga vem junto
                    MagicVehicle v = loaded(server, e);
                    if (v != null) {
                        hp = Math.max(0.01F, v.healthFraction());
                        fuel = v.getFuel();
                        upg = v.upgrades();
                        e.upg = upg;
                        v.retireTo(sp);
                    }
                }
                int gen = reg.reissue(server, e, hp, fuel);
                ItemStack stack = VehicleStacks.write(new ItemStack(VehiclesRegistries.item(kind)),
                        new VehicleStacks.Data(fuel, e.id, gen, hp, upg));
                if (!sp.getInventory().add(stack)) sp.drop(stack, false);
                sp.serverLevel().playSound(null, sp.blockPosition(), rebuild ? SoundEvents.SMITHING_TABLE_USE : SoundEvents.ITEM_PICKUP,
                        SoundSource.NEUTRAL, 0.8F, 1.0F);
                return Component.translatable("message.isekai_mahou.mechanic.done." + (rebuild ? "rebuild" : "recover"), o.name());
            }
            default -> {
                return null;
            }
        }
    }

    // =====================================================================================
    //  Resumo (conversa do Mecânico e comando)
    // =====================================================================================
    /** "Seus veículos: 2 no mundo, 1 guardado, 1 destruído" (ou "nenhum"). */
    public static Component summary(ServerPlayer sp) {
        int out = 0, packed = 0, wrecked = 0;
        for (VehicleRegistry.Entry e : VehicleRegistry.get(sp.server).of(sp.getUUID())) {
            if (e.kind() == null) continue;
            switch (e.state) {
                case VehicleRegistry.OUT -> out++;
                case VehicleRegistry.WRECKED -> wrecked++;
                default -> packed++;
            }
        }
        if (out + packed + wrecked == 0) return Component.translatable("message.isekai_mahou.mechanic.summary.none");
        return Component.translatable("message.isekai_mahou.mechanic.summary", out, packed, wrecked);
    }

    /** Uma linha por veículo do jogador (comando {@code /mahou vehicles list}). */
    public static List<Component> lines(ServerPlayer sp) {
        List<Component> out = new ArrayList<>();
        for (VehicleRegistry.Entry e : VehicleRegistry.get(sp.server).of(sp.getUUID())) {
            VehicleKind kind = e.kind();
            if (kind == null) continue;
            int hp = Math.round(e.hp * 100);
            MutableComponent line = switch (e.state) {
                case VehicleRegistry.OUT -> Component.translatable("message.isekai_mahou.mechanic.line.out", name(kind), hp,
                        e.pos == null ? "?" : e.pos.getX() + " " + e.pos.getY() + " " + e.pos.getZ(),
                        e.dim == null ? "?" : e.dim.location().getPath());
                case VehicleRegistry.WRECKED -> Component.translatable("message.isekai_mahou.mechanic.line.wrecked", name(kind));
                default -> Component.translatable("message.isekai_mahou.mechanic.line.packed", name(kind), hp);
            };
            int up = VehicleUpgrades.total(e.upg);
            if (up > 0) line.append(Component.literal(" +" + up).withStyle(ChatFormatting.LIGHT_PURPLE));
            out.add(line);
        }
        return out;
    }

    /** Para quem chamou devolver as moedas quando o serviço não deu certo. */
    public static void refund(ServerPlayer sp, net.minecraft.world.item.Item coin, int cost) {
        if (cost <= 0 || sp.isCreative()) return;
        ItemStack back = new ItemStack(coin, cost);
        if (!sp.getInventory().add(back)) sp.drop(back, false);
    }

    private VehicleMechanic() {}
}
