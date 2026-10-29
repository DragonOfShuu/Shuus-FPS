package dev.dragonofshuu.fps.shared;

import dev.dragonofshuu.fps.ShuuFPS;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Shared client HUD rendering for match stats.
 */
@Mod.EventBusSubscriber(modid = ShuuFPS.MOD_ID, value = Dist.CLIENT)
public final class SharedHudOverlay {

    private static final ResourceLocation SKULL_ICON = ResourceLocation.fromNamespaceAndPath(ShuuFPS.MOD_ID,
            "textures/gui/skull.png");
    private static final ResourceLocation TARGET_ICON = ResourceLocation.fromNamespaceAndPath(ShuuFPS.MOD_ID,
            "textures/gui/target.png");

    private static final int PLAYER_ICON_SIZE = 8;
    private static final int OTHER_ICON_SIZE = 9;
    private static final int HUD_GROUP_PADDING = 10;
    private static final int FACE_U = 8;
    private static final int FACE_V = 8;
    private static final int SKIN_TEX_SIZE = 64;

    // private static boolean resultsScreenOpened;

    private SharedHudOverlay() {
    }

    // @SubscribeEvent
    // public static void onRenderGlobalOverlay(RenderGuiOverlayEvent.Post event) {
    // if (!"minecraft:crosshair".equals(event.getOverlay().id().toString())) {
    // return;
    // }

    // Minecraft mc = Minecraft.getInstance();
    // if (mc.player == null || mc.options.hideGui) {
    // return;
    // }

    // // MatchStatsSnapshot snapshot = SharedMatchStats.getClientSnapshot();
    // // handleFinalResultsPopup(mc, snapshot);
    // }

    @SubscribeEvent
    public static void onRenderHealthOverlay(RenderGuiOverlayEvent.Post event) {
        if (!"minecraft:player_health".equals(event.getOverlay().id().toString())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        MatchStatsSnapshot snapshot = SharedMatchStats.getClientSnapshot();
        if (snapshot.phase() != MatchPhase.RUNNING) {
            return;
        }

        PlayerMatchStats local = findLocal(snapshot);
        if (local == null) {
            return;
        }

        Font font = mc.font;
        GuiGraphics gui = event.getGuiGraphics();
        int x = 2;
        // int y = event.getWindow().getGuiScaledHeight() - 69;
        int y = event.getWindow().getGuiScaledHeight() / 2 - 15;

        // int killsWidth = drawKillsStat(gui, font, x, y, local.kills());
        // int deathsX = x + killsWidth + HUD_GROUP_PADDING;
        // int deathsWidth = drawDeathStat(gui, font, deathsX, y, local.deaths());
        // int livesX = deathsX + deathsWidth + HUD_GROUP_PADDING;
        drawKillsStat(gui, font, x, y, local.kills());
        drawDeathStat(gui, font, x, y + 10, local.deaths());
        drawLivesStat(mc, gui, font, x, y + 20, local.livesLeft());

        if (snapshot.timerSeconds() >= 0) {
            String timer = formatTimer(snapshot.timerSeconds());
            int width = font.width(timer);
            int timerX = (event.getWindow().getGuiScaledWidth() - width) / 2;
            int timerY = 8;
            gui.drawString(font, timer, timerX + 1, timerY + 1, 0x333333, false);
            gui.drawString(font, timer, timerX, timerY, 0xFFFFFF, false);
        }
    }

    @SubscribeEvent
    public static void onRenderTabLeaderboard(RenderGuiOverlayEvent.Pre event) {
        if (!"minecraft:player_list".equals(event.getOverlay().id().toString())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !mc.options.keyPlayerList.isDown()) {
            return;
        }

        MatchStatsSnapshot snapshot = SharedMatchStats.getClientSnapshot();
        if (snapshot.phase() == MatchPhase.IDLE) {
            return;
        }

        event.setCanceled(true);

        GuiGraphics gui = event.getGuiGraphics();
        Font font = mc.font;

        MatchLeaderboardPanelRenderer.renderCenteredPanel(
                gui,
                font,
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight(),
                snapshot,
                "Match Results");
    }

    private static PlayerMatchStats findLocal(MatchStatsSnapshot snapshot) {
        if (snapshot.localPlayerId() == null) {
            return null;
        }
        for (PlayerMatchStats row : snapshot.players()) {
            if (snapshot.localPlayerId().equals(row.playerId())) {
                return row;
            }
        }
        return null;
    }

    private static String formatTimer(int totalSeconds) {
        int safe = Math.max(0, totalSeconds);
        int minutes = safe / 60;
        int seconds = safe % 60;
        return String.format("Time Left: %d:%02d", minutes, seconds);
    }

    private static String formatLivesValue(int lives) {
        return lives < 0 ? "INF" : Integer.toString(lives);
    }

    private static int drawKillsStat(GuiGraphics gui, Font font, int x, int y, int kills) {
        String value = "x " + kills;
        gui.blit(TARGET_ICON, x, y, 0, 0, OTHER_ICON_SIZE, OTHER_ICON_SIZE, OTHER_ICON_SIZE, OTHER_ICON_SIZE);
        int textX = x + OTHER_ICON_SIZE + 4;
        drawHudValue(gui, font, value, textX, y + 1);
        return (textX - x) + font.width(value);
    }

    private static int drawDeathStat(GuiGraphics gui, Font font, int x, int y, int deaths) {
        String value = "x " + deaths;
        gui.blit(SKULL_ICON, x, y, 0, 0, OTHER_ICON_SIZE, OTHER_ICON_SIZE, OTHER_ICON_SIZE, OTHER_ICON_SIZE);
        int textX = x + OTHER_ICON_SIZE + 4;
        drawHudValue(gui, font, value, textX, y + 1);
        return (textX - x) + font.width(value);
    }

    private static int drawLivesStat(Minecraft mc, GuiGraphics gui, Font font, int x, int y, int livesLeft) {
        String value = "x " + formatLivesValue(livesLeft);
        ResourceLocation skinTexture = mc.player != null ? mc.player.getSkinTextureLocation() : null;
        if (skinTexture != null) {
            gui.blit(skinTexture, x, y, FACE_U, FACE_V, PLAYER_ICON_SIZE, PLAYER_ICON_SIZE, SKIN_TEX_SIZE,
                    SKIN_TEX_SIZE);
        }
        int textX = x + PLAYER_ICON_SIZE + 4;
        drawHudValue(gui, font, value, textX, y + 1);
        return (textX - x) + font.width(value);
    }

    private static void drawHudValue(GuiGraphics gui, Font font, String value, int x, int y) {
        gui.drawString(font, value, x + 1, y + 1, 0x333333, false);
        gui.drawString(font, value, x, y, 0xFFFFFF, false);
    }

    public static void handleFinalResultsPopup(MatchStatsSnapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        ShuuFPS.LOGGER.info("HANDLING MATCH RESULTS SCREEN ON THE CLIENT: {}", mc.player);
        if (mc.player != null) {
            mc.setScreen(new MatchResultsScreen(snapshot));
        }
    }
}
