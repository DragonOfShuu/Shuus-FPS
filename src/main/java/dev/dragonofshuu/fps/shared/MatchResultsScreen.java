package dev.dragonofshuu.fps.shared;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MatchResultsScreen extends Screen {
    public MatchStatsSnapshot snapshot;

    public MatchResultsScreen(MatchStatsSnapshot snapshot) {
        super(Component.literal("Match Results"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        MatchLeaderboardPanelRenderer.PanelBounds panel = MatchLeaderboardPanelRenderer
                .centeredPanelBounds(this.width, this.height);

        this.addRenderableWidget(Button.builder(Component.literal("X"), button -> onClose())
                .bounds(panel.x() + panel.width() - 24, panel.y() + 6, 18, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        MatchLeaderboardPanelRenderer.renderCenteredPanel(
                guiGraphics,
                Minecraft.getInstance().font,
                this.width,
                this.height,
                this.snapshot,
                "Match Results");

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
