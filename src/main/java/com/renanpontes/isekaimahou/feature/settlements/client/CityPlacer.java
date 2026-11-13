package com.renanpontes.isekaimahou.feature.settlements.client;

import javax.annotation.Nullable;

import org.joml.Matrix4fStack;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.feature.settlements.CityPlots;
import com.renanpontes.isekaimahou.feature.settlements.SettlementsContent;
import com.renanpontes.isekaimahou.feature.settlements.SettlementsLang;
import com.renanpontes.isekaimahou.feature.settlements.SkyCity;
import com.renanpontes.isekaimahou.feature.settlements.SkyIsles;
import com.renanpontes.isekaimahou.feature.settlements.WaterCity;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.network.SettlementsNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * <b>Prévias no mundo</b> (v1.0, onda 4 — "Prévia antes de fundar" e "Posição livre dos prédios").
 * <ul>
 *   <li><b>Fundação</b> (Carta de Fundação na mão): um holograma mostra a praça, os 24 lotes do começo e a linha da
 *       primeira muralha no terreno para onde o jogador olha. Verde = bom, amarelo = desnível grande, azul = água,
 *       tudo vermelho = não pode fundar ali (perto demais de outra cidade/estrutura...).</li>
 *   <li><b>Prédio em posição livre / mudar um prédio</b> (aberto pela aba Planejar): o lote e a caixa do prédio, com
 *       a seta da entrada. Verde = pode, vermelho = não pode.</li>
 * </ul>
 * <p>Onda 7 (sky_ui): com a <b>Ilha Celeste</b> escolhida (na Carta de Fundação, ou com Tab na prévia — só para quem
 * tem o Núcleo Celeste), o holograma da fundação mostra o contorno da ilha no céu, na altitude que o servidor calculou,
 * e um feixe do ponto escolhido no chão até lá ({@link SkyPreview}).
 * <p>Controles (iguais nos dois): clique direito fixa a prévia; fixada, as SETAS movem 1 bloco (Ctrl = 4), R (ou agachar +
 * roda do mouse) gira, clique direito/Enter confirma, agachar + clique direito solta de novo. Clique esquerdo ou
 * Backspace cancela o posicionamento de prédio. Nada é gasto antes de confirmar: quem decide é o servidor
 * ({@link CityPlots#probe}); aqui só se desenha a resposta dele.
 * <p>Tela limpa: o único texto é UMA linha acima da barra de itens (os controles ou o motivo do vermelho).
 */
public final class CityPlacer {
    private static final int NONE = 0, FOUND = 1, BUILD = 2, MOVE = 3;
    private static final int GREEN = 0x6BE07A, YELLOW = 0xF2C94C, BLUE = 0x5AA9FF, RED = 0xFF5A4A, GREY = 0xD8D8E0, GOLD = 0xFFD86B;
    /** Onda 8 (water_city): cor da Cidade das Águas na prévia e na Carta. */
    static final int AQUA = 0x5FD8E8;
    /** Pontos de altura por lado da linha da muralha (a cada 7 blocos). */
    private static final int WALL_STEP = 7;

    private static int mode = NONE;
    private static String city = "", key = "", style = "";
    private static int fromCell = -1, moveLevel = 1;
    /** Onda 7: altura escolhida no painel para o prédio novo ("" = automática). */
    private static String height = "";
    private static Component label = Component.empty();
    private static int heartX, heartZ;
    /**
     * Onda 7 (sky_ui): tipo de terreno escolhido para a fundação ({@code SkyCity.GROUND} / {@code SkyCity.SKYLAND}).
     * Escolhido na Carta de Fundação (ou com a tecla Tab na prévia); vale enquanto a Carta estiver na mão.
     */
    private static String terrain = SkyCity.GROUND;

    private static boolean pinned;
    @Nullable private static BlockPos pos;
    private static int rot;
    /**
     * O botão de usar / de bater foi solto desde o último clique tratado aqui? Segurar o botão não fixa e confirma de
     * uma vez, e o clique que confirma/cancela nunca "vaza" para o jogo (usar o item da mão, quebrar um bloco).
     */
    private static boolean armed = true, attackArmed = true;
    private static int age, sentAge = -100;
    private static long sentKey = Long.MIN_VALUE;
    private static CompoundTag result = new CompoundTag();
    /** Câmera do quadro atual (as linhas são desenhadas relativas a ela, em double, para não tremer longe da origem). */
    private static double camX, camY, camZ;

    /** Alturas do chão guardadas para o holograma da fundação (recalculadas quando a prévia anda). */
    private static long groundKey = Long.MIN_VALUE;
    private static int groundAge;
    private static final int[] PLOT_Y = new int[CityLayout.CELLS];
    private static int[][] wallY = new int[4][0];

    // =====================================================================================
    //  Entrada pela tela da cidade
    // =====================================================================================
    /** Começa a posicionar um prédio novo (catálogo → "Posição livre"). */
    static void startBuild(String cityId, String buildKey, String styleId, Component name) {
        startBuild(cityId, buildKey, styleId, name, "");
    }

    /** Onda 7: com a altura escolhida no painel ("" = automática; "-1", "2"... em relação à praça). */
    static void startBuild(String cityId, String buildKey, String styleId, Component name, String heightArg) {
        begin(BUILD, cityId, name);
        key = buildKey;
        style = styleId;
        height = heightArg == null ? "" : heightArg;
    }

    /** Começa a mudar/girar um prédio pronto (lote {@code cell}); a prévia começa virada como o prédio está. */
    static void startMove(String cityId, int cell, Component name, int rotation, int level) {
        begin(MOVE, cityId, name);
        fromCell = cell;
        moveLevel = Math.max(1, level);
        rot = Math.floorMod(rotation, 4);
    }

    private static void begin(int newMode, String cityId, Component name) {
        reset();
        mode = newMode;
        city = cityId;
        label = name;
        CompoundTag st = ClientSettlements.state();
        heartX = st.getInt("HeartX");
        heartZ = st.getInt("HeartZ");
        Minecraft.getInstance().setScreen(null);
    }

    /** Rotação da praça escolhida na prévia da fundação (vai junto no pedido de fundar). */
    static int foundRot() {
        return mode == FOUND ? rot : 0;
    }

    static void reset() {
        mode = NONE;
        pinned = false;
        pos = null;
        rot = 0;
        fromCell = -1;
        key = "";
        style = "";
        height = "";
        result = new CompoundTag();
        sentKey = Long.MIN_VALUE;
        groundKey = Long.MIN_VALUE;
    }

    static void onProbe(CompoundTag tag) {
        String kind = tag.getString("Kind");
        if (mode == FOUND && kind.equals("found") || mode == BUILD && kind.equals("build") || mode == MOVE && kind.equals("move")) result = tag;
    }

    /** A última resposta do servidor vale para a posição/rotação (e, na fundação, o terreno) de agora? */
    private static boolean fresh() {
        return pos != null && result.contains("Ok") && result.getInt("X") == pos.getX() && result.getInt("Z") == pos.getZ()
                && result.getInt("Y") == pos.getY() && result.getInt("Rot") == rot
                && (mode != FOUND || result.getBoolean("Sky") == sky() && result.getBoolean("Water") == water());
    }

    // =====================================================================================
    //  Onda 7 (sky_ui): terreno da fundação — "No chão" / "Ilha Celeste (SkyLand)"
    // =====================================================================================
    /** A Ilha Celeste está escolhida para a fundação? */
    static boolean sky() {
        return mode == FOUND && SkyCity.SKYLAND.equals(terrain);
    }

    /** Onda 8 (water_city): "Sobre a Água" está escolhido para a fundação? (a prévia passa a mirar a superfície da água) */
    static boolean water() {
        return mode == FOUND && WaterCity.WATER.equals(terrain);
    }

    static String terrain() {
        return sky() ? SkyCity.SKYLAND : water() ? WaterCity.WATER : SkyCity.GROUND;
    }

    /** Parte (%) do território inicial que é água, pela conta do SERVIDOR (-1 = ainda sem resposta). */
    static int waterPct() {
        return water() && fresh() ? result.getInt("WaterPct") : -1;
    }

    /** Troca o terreno da fundação (Carta de Fundação ou tecla Tab) e já pergunta ao servidor se pode. */
    static void setTerrain(String t) {
        String next = SkyCity.SKYLAND.equals(t) ? SkyCity.SKYLAND : WaterCity.WATER.equals(t) ? WaterCity.WATER : SkyCity.GROUND;
        if (next.equals(terrain)) return;
        terrain = next;
        probeNow();
    }

    /** Pede a prévia da fundação agora (a tela da Carta fica aberta: o tick normal não pergunta com tela aberta). */
    static void probeNow() {
        if (mode != FOUND || pos == null) return;
        sentKey = probeKey();
        sentAge = age;
        PacketDistributor.sendToServer(new SettlementsNetwork.Probe("found", "", terrain(), pos, rot));
    }

    private static long probeKey() {
        return pos == null ? Long.MIN_VALUE : pos.asLong() * 31 + rot * 7L + mode + (sky() ? 101 : 0) + (water() ? 211 : 0);
    }

    /** Resposta do servidor para a fundação no terreno escolhido: -1 = ainda não chegou, 0 = não pode, 1 = pode. */
    static int foundState() {
        return mode != FOUND || !fresh() ? -1 : result.getBoolean("Ok") ? 1 : 0;
    }

    /** O motivo do "não pode" (só com {@link #foundState} = 0). */
    static Component foundProblem() {
        return ClientSettlements.text(result, "Problem");
    }

    /** Altitude da ilha que o SERVIDOR calculou para este ponto (0 = ainda sem resposta ou terreno alto demais). */
    static int skyY() {
        return sky() && fresh() ? Math.max(0, result.getInt("SkyY")) : 0;
    }

    /** O jogador tem um Núcleo Celeste? (sem ele a Ilha Celeste aparece apagada — o servidor confere de novo) */
    static boolean hasSkyCore() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getInventory().hasAnyMatching(st -> st.is(SettlementsContent.SKY_CORE.get()));
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) {
            if (mode != NONE) reset();
            return;
        }
        age++;
        if (!mc.options.keyUse.isDown()) armed = true;
        if (!mc.options.keyAttack.isDown()) attackArmed = true;
        boolean charter = p.getMainHandItem().is(SettlementsContent.FOUNDING_CHARTER.get());
        if (mode == NONE && charter) {
            reset();
            mode = FOUND;
        } else if (mode == FOUND && !charter) {
            reset();
            // onda 7: guardou a Carta (ou fundou): a próxima prévia começa "no chão" e a tela esquece estilo/nome
            terrain = SkyCity.GROUND;
            FoundingScreen.forget();
        }
        if (mode == NONE) return;
        if (mode != FOUND && !level.dimension().location().toString().equals(ClientSettlements.state().getString("Dim"))) {
            reset();
            return;
        }
        if (mc.screen != null) return;
        if (!pinned) {
            BlockPos look = lookAt(p, level);
            if (look != null) pos = look;
        }
        if (pos == null) return;
        // sem o Núcleo Celeste não há Ilha Celeste (gastou, guardou): a prévia volta para o chão
        if (sky() && age % 20 == 7 && !hasSkyCore()) terrain = SkyCity.GROUND;
        long k = probeKey();
        if (k != sentKey && age - sentAge >= 4 || age - sentAge >= 40) {
            sentKey = k;
            sentAge = age;
            String kind = mode == FOUND ? "found" : mode == BUILD ? "build" : "move";
            // onda 7: na fundação o argumento é o terreno escolhido ("ground" / "skyland")
            String arg = mode == BUILD ? key + "|" + style + "|" + height : mode == MOVE ? Integer.toString(fromCell) : terrain();
            PacketDistributor.sendToServer(new SettlementsNetwork.Probe(kind, city, arg, pos, rot));
        }
        if (mode == FOUND) ground(level);
        if (age % 20 == 1) mc.gui.setOverlayMessage(hint(), false);
    }

    /** Ponto da prévia a partir do olhar: o chão (fundação = o bloco livre em cima dele). */
    @Nullable
    private static BlockPos lookAt(LocalPlayer p, ClientLevel level) {
        // onda 8 (water_city): com "Sobre a Água" escolhido o olhar para na superfície da água (ela é o chão da prévia)
        HitResult hr = p.pick(CityPlots.REACH, 1F, water());
        if (!(hr instanceof BlockHitResult bh) || hr.getType() != HitResult.Type.BLOCK) return null;
        BlockPos b = bh.getBlockPos();
        if (water() && !level.getFluidState(b).isEmpty()) {
            // quem está nadando acerta a água em volta de si: sobe até o bloco de água de cima
            while (b.getY() < level.getMaxBuildHeight() - 2 && !level.getFluidState(b.above()).isEmpty()) b = b.above();
            return b.above();
        }
        // mato/neve: a prévia fica no chão de verdade, não em cima da planta
        if (level.getBlockState(b).canBeReplaced()) b = b.below();
        return mode == FOUND ? b.above() : b;
    }

    /** Primeiro bloco livre em cima do chão firme de uma coluna (passa por folhas, troncos e mato). */
    private static int surface(ClientLevel level, int x, int z, int fallback) {
        if (!level.hasChunk(x >> 4, z >> 4)) return fallback;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y, z);
        for (int guard = 0; guard < 48 && y > level.getMinBuildHeight() + 1; guard++) {
            BlockState st = level.getBlockState(m.setY(y - 1));
            if (!(st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.canBeReplaced() && st.getFluidState().isEmpty())) break;
            y--;
        }
        return y;
    }

    private static void ground(ClientLevel level) {
        if (pos == null) return;
        long k = pos.asLong();
        if (k == groundKey && age - groundAge < 20) return;
        groundKey = k;
        groundAge = age;
        for (int c = 0; c < CityLayout.CELLS; c++) {
            BlockPos cc = CityLayout.cellCenter(pos, c);
            PLOT_Y[c] = surface(level, cc.getX(), cc.getZ(), pos.getY());
        }
        int d = CityLayout.wallDist(1), n = 2 * d / WALL_STEP + 1;
        if (wallY.length != 4 || wallY[0].length != n) wallY = new int[4][n];
        for (int side = 0; side < 4; side++) {
            for (int i = 0; i < n; i++) {
                int t = -d + i * WALL_STEP;
                int x = pos.getX() + (side < 2 ? t : side == 2 ? -d : d), z = pos.getZ() + (side < 2 ? (side == 0 ? -d : d) : t);
                wallY[side][i] = surface(level, x, z, pos.getY());
            }
        }
    }

    /** A única linha de texto da prévia: o motivo do vermelho, ou os controles. */
    private static Component hint() {
        if (fresh() && !result.getBoolean("Ok")) return ClientSettlements.text(result, "Problem").copy().withColor(RED);
        // onda 7: com a Ilha Celeste escolhida a linha diz isso; quem tem o Núcleo Celeste vê a tecla que troca o terreno
        if (mode == FOUND && sky()) return SettlementsLang.tr(pinned ? "sky.place.pinned" : "sky.place.free").withColor(SkyPreview.SKY);
        // onda 8 (water_city): "Sobre a Água" tem a linha dela; no chão a linha conta que o Tab troca o terreno
        if (mode == FOUND && water()) return SettlementsLang.tr(pinned ? "water.place.pinned" : "water.place.free").withColor(AQUA);
        if (mode == FOUND && !pinned) return SettlementsLang.tr("water.place.ground").withColor(GOLD);
        if (mode == FOUND) return SettlementsLang.tr(pinned ? "place.found.pinned" : "place.found.free").withColor(GOLD);
        // aviso de terreno (não impede): aparece 1 segundo a cada 3, revezando com os controles
        if (fresh() && age / 20 % 3 == 0) {
            int terrain = result.getInt("Terrain");
            if (terrain == CityPlots.TERRAIN_WATER) return SettlementsLang.tr("place.warn.water").withColor(BLUE);
            if (terrain == CityPlots.TERRAIN_STEEP) return SettlementsLang.tr("place.warn.steep").withColor(YELLOW);
        }
        if (mode == MOVE) {
            return SettlementsLang.tr(pinned ? "place.move.pinned" : "place.move.free", label, CityPlots.moveCost(moveLevel)).withColor(GOLD);
        }
        return SettlementsLang.tr(pinned ? "place.build.pinned" : "place.build.free", label).withColor(GOLD);
    }

    // =====================================================================================
    //  Controles
    // =====================================================================================
    static void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        // botão ainda segurado depois de confirmar/cancelar: engole até soltar
        if (e.isUseItem() && !armed || e.isAttack() && !attackArmed) {
            e.setSwingHand(false);
            e.setCanceled(true);
            return;
        }
        if (mode == NONE || mc.player == null || mc.screen != null) return;
        if (e.isAttack() && mode != FOUND) {
            // clique esquerdo cancela o posicionamento de prédio (não bate em nada)
            e.setSwingHand(false);
            e.setCanceled(true);
            attackArmed = false;
            cancel(mc);
            return;
        }
        if (!e.isUseItem()) return;
        if (mode == FOUND && e.getHand() != InteractionHand.MAIN_HAND) return;
        // com a Carta na mão ainda dá para falar com NPCs e montar: só o clique no chão/ar mexe na prévia
        if (mode == FOUND && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) return;
        e.setSwingHand(false);
        e.setCanceled(true);
        if (!armed) return;
        armed = false;
        if (pos == null) return;
        if (pinned && mc.player.isShiftKeyDown()) {
            pinned = false;
            click(mc, 0.8F);
        } else if (!pinned) {
            pinned = true;
            click(mc, 1.2F);
        } else {
            confirm(mc);
        }
        mc.gui.setOverlayMessage(hint(), false);
    }

    static void onKey(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (mode == NONE || mc.player == null || mc.level == null || mc.screen != null || pos == null) return;
        if (e.getAction() == GLFW.GLFW_RELEASE) return;
        boolean press = e.getAction() == GLFW.GLFW_PRESS;
        int step = (e.getModifiers() & GLFW.GLFW_MOD_CONTROL) != 0 ? 4 : 1;
        Direction facing = mc.player.getDirection();
        switch (e.getKey()) {
            case GLFW.GLFW_KEY_R -> {
                if (press) rotate(mc, 1);
            }
            case GLFW.GLFW_KEY_TAB -> {
                // onda 7 (sky_ui): Tab troca chão ↔ Ilha Celeste na prévia da fundação (só com o Núcleo Celeste).
                // Tab e não uma letra: Z X C G B V H N O já são teclas do mod (magias, menu, habilidade racial...)
                // onda 8 (water_city): o Tab passa pelos três terrenos: chão → sobre a água → Ilha Celeste (só com o Núcleo) → chão
                if (!press || mode != FOUND) return;
                setTerrain(water() ? (hasSkyCore() ? SkyCity.SKYLAND : SkyCity.GROUND) : sky() ? SkyCity.GROUND : WaterCity.WATER);
                // o "chão" da prévia mudou (terra ↔ água): solta o ponto fixado para mirar de novo
                pinned = false;
                click(mc, sky() ? 1.6F : water() ? 1.3F : 0.9F);
            }
            case GLFW.GLFW_KEY_UP -> nudge(mc, facing, step);
            case GLFW.GLFW_KEY_DOWN -> nudge(mc, facing.getOpposite(), step);
            case GLFW.GLFW_KEY_LEFT -> nudge(mc, facing.getCounterClockWise(), step);
            case GLFW.GLFW_KEY_RIGHT -> nudge(mc, facing.getClockWise(), step);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (press && pinned) confirm(mc);
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!press) return;
                if (mode == FOUND) pinned = false;
                else cancel(mc);
            }
            default -> {
                return;
            }
        }
        mc.gui.setOverlayMessage(hint(), false);
    }

    /** Agachar + roda do mouse também gira (para quem usa a tecla R em outra coisa). */
    static void onScroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mode == NONE || mc.player == null || mc.screen != null || pos == null || !mc.player.isShiftKeyDown()) return;
        if (e.getScrollDeltaY() == 0) return;
        rotate(mc, e.getScrollDeltaY() > 0 ? 1 : -1);
        e.setCanceled(true);
    }

    private static void rotate(Minecraft mc, int dir) {
        rot = Math.floorMod(rot + dir, 4);
        click(mc, 1.5F);
    }

    private static void nudge(Minecraft mc, Direction dir, int n) {
        if (pos == null || mc.level == null) return;
        pinned = true;
        int x = pos.getX() + dir.getStepX() * n, z = pos.getZ() + dir.getStepZ() * n;
        pos = mode == FOUND ? new BlockPos(x, surface(mc.level, x, z, pos.getY()), z) : new BlockPos(x, pos.getY(), z);
        // o holograma anda junto na hora (sem esperar o próximo tick)
        if (mode == FOUND) ground(mc.level);
    }

    private static void confirm(Minecraft mc) {
        if (pos == null) return;
        if (fresh() && !result.getBoolean("Ok")) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            return;
        }
        if (mode == FOUND) {
            // o servidor confere de novo e abre a tela de estilo/nome; a Carta só é gasta quando a cidade nasce
            // onda 7: o terreno escolhido vai junto (no céu o servidor confere a caixa da ILHA e o Núcleo Celeste)
            PacketDistributor.sendToServer(new SettlementsNetwork.Probe("found_open", "", terrain(), pos, rot));
            return;
        }
        int dx = pos.getX() - heartX, dz = pos.getZ() - heartZ;
        if (mode == BUILD) ClientSettlements.action(city, "plan_free", key + "|" + dx + "|" + dz + "|" + rot + "|" + style + "|" + height);
        else ClientSettlements.action(city, "plan_move", fromCell + "|" + dx + "|" + dz + "|" + rot);
        reset();
    }

    private static void cancel(Minecraft mc) {
        reset();
        mc.gui.setOverlayMessage(SettlementsLang.tr("place.cancelled").withColor(GREY), false);
        click(mc, 0.7F);
    }

    private static void click(Minecraft mc, float pitch) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || mode == NONE || pos == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        Vec3 cam = event.getCamera().getPosition();
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(2.5F);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        PoseStack pose = new PoseStack();
        camX = cam.x;
        camY = cam.y;
        camZ = cam.z;
        float pulse = 0.75F + 0.2F * (float) Math.sin((mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) * 0.2);
        if (mode == FOUND) drawFound(pose, buf, pulse);
        else drawPlot(mc.level, pose, buf, pulse);
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.lineWidth(1.0F);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /** Holograma da fundação: praça (dourada), os 24 lotes do começo e a linha da primeira muralha. */
    private static void drawFound(PoseStack pose, VertexConsumer buf, float pulse) {
        BlockPos at = pos;
        if (at == null || groundKey != at.asLong()) return;
        boolean known = fresh();
        boolean bad = known && !result.getBoolean("Ok");
        if (sky()) {
            drawSky(pose, buf, pulse, at, bad);
            return;
        }
        if (water()) {
            drawWater(pose, buf, pulse, at, bad, known ? result.getIntArray("Cells") : new int[0]);
            return;
        }
        int[] cells = known ? result.getIntArray("Cells") : new int[0];
        for (int c = 0; c < CityLayout.CELLS; c++) {
            BlockPos cc = CityLayout.cellCenter(at, c);
            boolean plaza = c == CityLayout.CENTER_CELL;
            int status = c < cells.length ? cells[c] : CityPlots.TERRAIN_UNKNOWN;
            int color = bad ? RED : status == CityPlots.TERRAIN_WATER ? BLUE : status == CityPlots.TERRAIN_STEEP ? YELLOW
                    : status == CityPlots.TERRAIN_UNKNOWN ? GREY : plaza ? GOLD : GREEN;
            // os 4 lotes liberados desde o primeiro dia aparecem mais fortes; os outros abrem com o nível do centro
            float alpha = plaza ? pulse : CityLayout.unlockLevel(c) == 1 ? 0.85F : 0.4F;
            double y = (plaza ? at.getY() : PLOT_Y[c]) + 0.06;
            box(pose, buf, cc.getX() - 7, y, cc.getZ() - 7, cc.getX() + 8, y + 0.05, cc.getZ() + 8, color, alpha);
        }
        // praça: o marco do coração e a seta da frente (para onde a praça vira)
        double hx = at.getX() + 0.5, hy = at.getY() + 0.1, hz = at.getZ() + 0.5;
        int main = bad ? RED : GOLD;
        line(pose, buf, hx, hy, hz, hx, hy + 8, hz, main, pulse);
        box(pose, buf, at.getX(), at.getY(), at.getZ(), at.getX() + 1, at.getY() + 1, at.getZ() + 1, main, pulse);
        arrow(pose, buf, hx, hy + 0.4, hz, Rotation.values()[rot].rotate(Direction.SOUTH), 1.5, 7, main, pulse);
        // linha da primeira muralha, acompanhando o chão
        int d = CityLayout.wallDist(1);
        int wall = bad ? RED : GREY;
        for (int side = 0; side < 4 && side < wallY.length; side++) {
            int[] ys = wallY[side];
            for (int i = 0; i + 1 < ys.length; i++) {
                int t0 = -d + i * WALL_STEP, t1 = Math.min(d, t0 + WALL_STEP);
                double x0 = at.getX() + 0.5 + (side < 2 ? t0 : side == 2 ? -d : d), x1 = at.getX() + 0.5 + (side < 2 ? t1 : side == 2 ? -d : d);
                double z0 = at.getZ() + 0.5 + (side < 2 ? (side == 0 ? -d : d) : t0), z1 = at.getZ() + 0.5 + (side < 2 ? (side == 0 ? -d : d) : t1);
                line(pose, buf, x0, ys[i] + 0.1, z0, x1, ys[i + 1] + 0.1, z1, wall, 0.7F);
                line(pose, buf, x0, ys[i] + 3.1, z0, x1, ys[i + 1] + 3.1, z1, wall, 0.35F);
            }
        }
    }

    /**
     * Onda 7 (sky_ui) — holograma da ILHA CELESTE: o ponto escolhido no chão, um feixe até a altitude que o servidor
     * calculou e, lá em cima, o contorno verdadeiro da ilha (a mesma conta do gerador), a barriga em cone, a praça, os
     * 24 lotes do começo e a linha da primeira muralha — tudo plano, na altura em que a cidade vai nascer.
     */
    private static void drawSky(PoseStack pose, VertexConsumer buf, float pulse, BlockPos at, boolean bad) {
        int serverY = skyY();
        // sem resposta ainda: desenha no palpite, mais apagado (o servidor é quem decide a altura)
        int hy = serverY > 0 ? serverY : SkyPreview.guessY(at);
        float sure = serverY > 0 ? 1F : 0.5F;
        int main = bad ? RED : SkyPreview.SKY;
        double hx = at.getX() + 0.5, hz = at.getZ() + 0.5;
        // no chão: o marco do ponto escolhido
        box(pose, buf, at.getX() - 1, at.getY() + 0.06, at.getZ() - 1, at.getX() + 2, at.getY() + 0.11, at.getZ() + 2, main, pulse);
        // onda 9 (isles): a cidade nova é um arquipélago — a ilha do MEIO (as dos lotes nascem com as obras) e, ao lado da
        // praça, o poço da Corrente Ascendente: o feixe sobe do chão por dentro dele
        Direction front = Rotation.values()[rot].rotate(Direction.SOUTH);
        int sx = front.getStepX() * SkyIsles.SHAFT_ALONG - front.getStepZ() * SkyIsles.SHAFT_ACROSS;
        int sz = front.getStepZ() * SkyIsles.SHAFT_ALONG + front.getStepX() * SkyIsles.SHAFT_ACROSS;
        line(pose, buf, hx + sx, at.getY() + 0.1, hz + sz, hx + sx, hy - 1, hz + sz, main, pulse * sure);
        box(pose, buf, at.getX() + sx - 1, hy - 1.0, at.getZ() + sz - 1, at.getX() + sx + 2, hy + 0.1, at.getZ() + sz + 2, main, pulse * sure);
        // contorno da ilha do meio no topo (onde se pisa) e a barriga: raios da borda até a ponta de baixo
        BlockPos heart = new BlockPos(at.getX(), hy, at.getZ());
        float[] rim = SkyPreview.coreRim(heart, front);
        int n = SkyPreview.POINTS;
        double top = hy + 0.05, tip = hy - 26;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double x0 = hx + rim[i * 2], z0 = hz + rim[i * 2 + 1], x1 = hx + rim[j * 2], z1 = hz + rim[j * 2 + 1];
            line(pose, buf, x0, top, z0, x1, top, z1, main, 0.9F * sure);
            line(pose, buf, x0, top - 3, z0, x1, top - 3, z1, main, 0.3F * sure);
            if (i % 8 == 0) {
                // a barriga afina para o miolo: meio caminho a 14 blocos abaixo, ponta a 36
                double mx = hx + rim[i * 2] * 0.45, mz = hz + rim[i * 2 + 1] * 0.45;
                line(pose, buf, x0, top - 3, z0, mx, top - 14, mz, main, 0.3F * sure);
                line(pose, buf, mx, top - 14, mz, hx, tip, hz, main, 0.3F * sure);
            }
        }
        // a cidade: lotes (planos, cada um vira uma ilha quando receber obra), praça, coração e a seta da frente
        for (int c = 0; c < CityLayout.CELLS; c++) {
            BlockPos cc = CityLayout.cellCenter(heart, CityLayout.isleCell(CityLayout.cellI(c), CityLayout.cellJ(c)));
            boolean plaza = c == CityLayout.CENTER_CELL;
            int color = bad ? RED : plaza ? GOLD : GREEN;
            float alpha = (plaza ? pulse : CityLayout.unlockLevel(c) == 1 ? 0.85F : 0.4F) * sure;
            box(pose, buf, cc.getX() - 7, top, cc.getZ() - 7, cc.getX() + 8, top + 0.05, cc.getZ() + 8, color, alpha);
        }
        int gold = bad ? RED : GOLD;
        line(pose, buf, hx, hy + 0.1, hz, hx, hy + 8, hz, gold, pulse * sure);
        box(pose, buf, at.getX(), hy, at.getZ(), at.getX() + 1, hy + 1, at.getZ() + 1, gold, pulse * sure);
        arrow(pose, buf, hx, hy + 0.4, hz, front, 1.5, 7, gold, pulse * sure);
        // a primeira muralha no céu: só torres e portões, cada um na sua ilhota de vigia
        int d = CityLayout.isleWallDist(1);
        int wall = bad ? RED : GREY;
        for (int[] o : new int[][] {{-d, -d}, {d, -d}, {-d, d}, {d, d}, {0, -d}, {0, d}, {-d, 0}, {d, 0}}) {
            box(pose, buf, at.getX() + o[0] - 2, hy + 0.1, at.getZ() + o[1] - 2, at.getX() + o[0] + 3, hy + 4.1, at.getZ() + o[1] + 3, wall, 0.5F * sure);
        }
    }

    /**
     * Onda 8 (water_city) — holograma da CIDADE DAS ÁGUAS, plano na altura do piso (logo acima da água): a praça, os
     * 24 lotes do começo (azul-claro = sobre a água, verde = terra firme), a linha da primeira muralha (o quebra-mar),
     * o canal em volta dela e, na frente da praça, a ponte e o cais dos barcos.
     */
    private static void drawWater(PoseStack pose, VertexConsumer buf, float pulse, BlockPos at, boolean bad, int[] cells) {
        double y = at.getY() + 0.06;
        // onda 9 (isles): a cidade das águas nova é um arquipélago — cada lote vira uma plataforma quando receber obra
        for (int c = 0; c < CityLayout.CELLS; c++) {
            BlockPos cc = CityLayout.cellCenter(at, CityLayout.isleCell(CityLayout.cellI(c), CityLayout.cellJ(c)));
            boolean plaza = c == CityLayout.CENTER_CELL;
            boolean dry = c < cells.length && cells[c] == 1;
            int color = bad ? RED : plaza ? GOLD : dry ? GREEN : AQUA;
            float alpha = plaza ? pulse : CityLayout.unlockLevel(c) == 1 ? 0.85F : 0.4F;
            box(pose, buf, cc.getX() - 7, y, cc.getZ() - 7, cc.getX() + 8, y + 0.05, cc.getZ() + 8, color, alpha);
        }
        double hx = at.getX() + 0.5, hz = at.getZ() + 0.5;
        int main = bad ? RED : GOLD;
        line(pose, buf, hx, y + 1, hz, hx, y + 9, hz, main, pulse);
        box(pose, buf, at.getX(), at.getY() + 1, at.getZ(), at.getX() + 1, at.getY() + 2, at.getZ() + 1, main, pulse);
        Direction front = Rotation.values()[rot].rotate(Direction.SOUTH);
        arrow(pose, buf, hx, y + 0.4, hz, front, 1.5, 7, main, pulse);
        // quebra-mar (linha da primeira muralha), no vão entre o 2º e o 3º anel de plataformas
        int d = CityLayout.isleWallDist(1);
        box(pose, buf, hx - d, y, hz - d, hx + d, y + 3, hz + d, bad ? RED : GREY, 0.5F);
        // pontes até as plataformas da cruz (arco no meio do vão) e o cais do porto de dentro, no canto da praça
        // (o lado do cais é o +x/+z do mundo, como o servidor conta em WaterWorks.sidePos)
        double sx = front.getStepX(), sz = front.getStepZ(), px = sx != 0 ? 0 : 1, pz = sx != 0 ? 1 : 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            double ax = dir.getStepX(), az = dir.getStepZ(), qx = -az, qz = ax;
            for (int side = -1; side <= 1; side += 2) {
                double ox = qx * 1.5 * side, oz = qz * 1.5 * side;
                line(pose, buf, hx + ax * 9 + ox, y, hz + az * 9 + oz, hx + ax * 13 + ox, y, hz + az * 13 + oz, main, 0.8F);
                line(pose, buf, hx + ax * 13 + ox, y, hz + az * 13 + oz, hx + ax * 15 + ox, y + 2, hz + az * 15 + oz, main, 0.8F);
                line(pose, buf, hx + ax * 15 + ox, y + 2, hz + az * 15 + oz, hx + ax * 17 + ox, y, hz + az * 17 + oz, main, 0.8F);
                line(pose, buf, hx + ax * 17 + ox, y, hz + az * 17 + oz, hx + ax * 21 + ox, y, hz + az * 21 + oz, main, 0.8F);
            }
        }
        for (double l : new double[] {6, 8.9}) {
            line(pose, buf, hx + sx * 9 + px * l, y, hz + sz * 9 + pz * l, hx + sx * 14 + px * l, y, hz + sz * 14 + pz * l, main, 0.8F);
        }
        line(pose, buf, hx + sx * 14 + px * 5, y, hz + sz * 14 + pz * 5, hx + sx * 14 + px * 13.9, y, hz + sz * 14 + pz * 13.9, main, 0.8F);
        line(pose, buf, hx + sx * 16.9 + px * 5, y, hz + sz * 16.9 + pz * 5, hx + sx * 16.9 + px * 13.9, y, hz + sz * 16.9 + pz * 13.9, main, 0.8F);
    }

    /** Fantasma de um prédio: o lote 15×15, a caixa do prédio girado e a seta da entrada. */
    private static void drawPlot(ClientLevel level, PoseStack pose, VertexConsumer buf, float pulse) {
        BlockPos at = pos;
        if (at == null) return;
        boolean known = fresh();
        boolean bad = known && !result.getBoolean("Ok");
        int terrain = known ? result.getInt("Terrain") : CityPlots.TERRAIN_OK;
        int color = !known ? GREY : bad ? RED : terrain == CityPlots.TERRAIN_WATER ? BLUE : terrain == CityPlots.TERRAIN_STEEP ? YELLOW : GREEN;
        // piso do lote (a camada de baixo do molde fica nele): o que o servidor calculou, ou o chão de agora
        double y = known && result.contains("Base") ? result.getInt("Base") : surface(level, at.getX(), at.getZ(), at.getY() + 1) - 1;
        int x = at.getX(), z = at.getZ();
        box(pose, buf, x - 7, y + 1.06, z - 7, x + 8, y + 1.11, z + 8, color, 0.6F);
        int w = known ? result.getInt("W") : 0, d = known ? result.getInt("D") : 0, h = known ? result.getInt("H") : 0;
        Direction front = Rotation.values()[rot].rotate(Direction.SOUTH);
        if (w > 0 && d > 0) {
            // mesma conta do canteiro: canto = centro − metade (ConstructionEngine.origin)
            int x0 = x - w / 2, z0 = z - d / 2;
            box(pose, buf, x0, y, z0, x0 + w, y + Math.max(1, h), z0 + d, color, pulse);
            double reach = (front.getAxis() == Direction.Axis.X ? w : d) / 2.0;
            arrow(pose, buf, x + 0.5, y + 2.2, z + 0.5, front, reach, reach + 4, color, pulse);
        } else {
            arrow(pose, buf, x + 0.5, y + 2.2, z + 0.5, front, 2, 7, color, pulse);
        }
    }

    private static void box(PoseStack pose, VertexConsumer buf, double x0, double y0, double z0, double x1, double y1, double z1,
                            int rgb, float alpha) {
        LevelRenderer.renderLineBox(pose, buf, new AABB(x0 - camX, y0 - camY, z0 - camZ, x1 - camX, y1 - camY, z1 - camZ), (rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F,
                (rgb & 255) / 255F, alpha);
    }

    private static void line(PoseStack pose, VertexConsumer buf, double x0, double y0, double z0, double x1, double y1, double z1,
                             int rgb, float alpha) {
        float nx = (float) (x1 - x0), ny = (float) (y1 - y0), nz = (float) (z1 - z0);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-4F) return;
        nx /= len;
        ny /= len;
        nz /= len;
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
        PoseStack.Pose last = pose.last();
        buf.addVertex(last, (float) (x0 - camX), (float) (y0 - camY), (float) (z0 - camZ)).setColor(r, g, b, alpha).setNormal(last, nx, ny, nz);
        buf.addVertex(last, (float) (x1 - camX), (float) (y1 - camY), (float) (z1 - camZ)).setColor(r, g, b, alpha).setNormal(last, nx, ny, nz);
    }

    /** Seta no chão saindo do centro para o lado {@code dir}, de {@code from} a {@code to} blocos. */
    private static void arrow(PoseStack pose, VertexConsumer buf, double cx, double y, double cz, Direction dir, double from, double to,
                              int rgb, float alpha) {
        double sx = dir.getStepX(), sz = dir.getStepZ();
        double x0 = cx + sx * from, z0 = cz + sz * from, x1 = cx + sx * to, z1 = cz + sz * to;
        line(pose, buf, x0, y, z0, x1, y, z1, rgb, alpha);
        // ponta: duas linhas voltando em diagonal
        double px = -sz, pz = sx;
        line(pose, buf, x1, y, z1, x1 - sx * 1.5 + px * 1.5, y, z1 - sz * 1.5 + pz * 1.5, rgb, alpha);
        line(pose, buf, x1, y, z1, x1 - sx * 1.5 - px * 1.5, y, z1 - sz * 1.5 - pz * 1.5, rgb, alpha);
    }

    private CityPlacer() {}
}
