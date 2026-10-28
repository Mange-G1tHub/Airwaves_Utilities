package fr.mange.airwavesutilities;

import com.mojang.logging.LogUtils;
import com.airwaves.item.TapeItem;
import com.airwaves.logic.TapeAudio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;

final class TapeImportScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<Integer> tapeSlots = new ArrayList<>();
    private int selectedTape;
    private String status = "Formats pris en charge : WAV, AIFF, AU et MP3 (durée max. 5 min).";
    private Button tapeButton;
    private Button importButton;

    TapeImportScreen() {
        super(Component.literal("Importer un fichier audio sur une tape"));
    }

    @Override
    protected void init() {
        tapeSlots.clear();
        if (minecraft != null && minecraft.player != null) {
            for (int slot = 0; slot < minecraft.player.getInventory().items.size(); slot++) {
                if (minecraft.player.getInventory().getItem(slot).getItem() instanceof TapeItem)
                    tapeSlots.add(slot);
            }
        }
        selectedTape = Math.min(selectedTape, Math.max(0, tapeSlots.size() - 1));
        int centerX = width / 2;
        tapeButton = addRenderableWidget(Button.builder(tapeButtonText(), button -> cycleTape())
                .bounds(centerX - 105, height / 2 - 32, 210, 20).build());
        importButton = addRenderableWidget(Button.builder(importButtonText(), button -> chooseFile())
                .bounds(centerX - 105, height / 2 - 5, 210, 20).build());
        importButton.active = !tapeSlots.isEmpty();
        addRenderableWidget(Button.builder(Component.literal("Fermer"), button -> onClose())
                .bounds(centerX - 45, height / 2 + 23, 90, 20).build());
    }

    private Component tapeButtonText() {
        return tapeSlots.isEmpty() ? Component.literal("Aucune tape dans l'inventaire")
                : Component.literal("Tape " + (selectedTape + 1) + "/" + tapeSlots.size());
    }

    private void cycleTape() {
        if (tapeSlots.isEmpty()) return;
        selectedTape = (selectedTape + 1) % tapeSlots.size();
        tapeButton.setMessage(tapeButtonText());
        importButton.setMessage(importButtonText());
    }

    private Component importButtonText() {
        return Component.literal(isSelectedTapeOccupied() ? "Remplacer le contenu par un fichier…"
                : "Importer un fichier audio…");
    }

    private boolean isSelectedTapeOccupied() {
        if (tapeSlots.isEmpty() || minecraft == null || minecraft.player == null) return false;
        ItemStack stack = minecraft.player.getInventory().getItem(tapeSlots.get(selectedTape));
        return !TapeItem.data(stack).blank();
    }

    private void chooseFile() {
        Minecraft.getInstance().setScreen(new TapeImportFileScreen(this, this::importFile));
    }

    private void importFile(File file) {
        Minecraft.getInstance().setScreen(this);
        int inventorySlot = tapeSlots.get(selectedTape);
        importButton.active = false;
        status = "Conversion de « " + file.getName() + " » en audio Airwaves…";
        Thread decoder = new Thread(() -> {
            try {
                List<TapeAudio.Frame> frames = TapeAudioFileDecoder.decode(file);
                Minecraft.getInstance().execute(() -> sendFrames(inventorySlot, frames));
            } catch (Exception exception) {
                String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                LOGGER.error("Failed to import audio file '{}'", file.getAbsolutePath(), exception);
                Minecraft.getInstance().execute(() -> {
                    status = "Échec de l'import (" + exception.getClass().getSimpleName() + ") : " + reason;
                    if (importButton != null) importButton.active = !tapeSlots.isEmpty();
                });
            }
        }, "Airwaves tape audio decoder");
        decoder.setDaemon(true);
        decoder.start();
    }

    private void sendFrames(int inventorySlot, List<TapeAudio.Frame> frames) {
        UUID transferId = UUID.randomUUID();
        PacketDistributor.sendToServer(new TapeImportPayloads.Start(inventorySlot, frames.size(), transferId));
        for (int first = 0; first < frames.size(); first += TapeImportServer.FRAMES_PER_CHUNK) {
            int count = Math.min(TapeImportServer.FRAMES_PER_CHUNK, frames.size() - first);
            byte[] packed = new byte[count * TapeImportServer.FRAME_BYTES];
            for (int i = 0; i < count; i++) {
                byte[] data = frames.get(first + i).data();
                if (data.length != TapeImportServer.FRAME_BYTES) {
                    status = "Import annulé : format audio interne invalide.";
                    importButton.active = !tapeSlots.isEmpty();
                    return;
                }
                System.arraycopy(data, 0, packed, i * TapeImportServer.FRAME_BYTES, data.length);
            }
            PacketDistributor.sendToServer(new TapeImportPayloads.Chunk(transferId, first, packed));
        }
        PacketDistributor.sendToServer(new TapeImportPayloads.Finish(transferId));
        status = "Fichier envoyé au serveur. La confirmation apparaîtra dans le chat.";
        importButton.active = !tapeSlots.isEmpty();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 65, 0xFFFFFF);
        if (isSelectedTapeOccupied()) {
            graphics.drawCenteredString(font, Component.literal("Attention : l'ancien contenu sera remplacé."),
                    width / 2, height / 2 + 43, 0xFFAA55);
        }
        graphics.drawCenteredString(font, Component.literal(status), width / 2, height / 2 + 54, 0xD0D0D0);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
