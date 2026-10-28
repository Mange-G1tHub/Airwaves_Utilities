package fr.mange.airwavesutilities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

final class TapeImportFileScreen extends Screen {
    private static final int PAGE_SIZE = 7;
    private final Screen returnScreen;
    private final Consumer<File> onSelected;
    private final List<Path> entries = new ArrayList<>();
    private Path currentDirectory;
    private int page;
    private String status = "Choisis un fichier audio WAV, AIFF, AU ou MP3.";
    private EditBox pathBox;
    private Button parentButton;
    private Button previousButton;
    private Button nextButton;

    TapeImportFileScreen(Screen returnScreen, Consumer<File> onSelected) {
        super(Component.literal("Choisir un fichier audio"));
        this.returnScreen = returnScreen;
        this.onSelected = onSelected;
        this.currentDirectory = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        pathBox = new EditBox(font, centerX - 190, height / 2 - 148, 330, 20,
                Component.literal("Chemin du dossier"));
        pathBox.setMaxLength(512);
        pathBox.setValue(currentDirectory.toString());
        addRenderableWidget(pathBox);
        addRenderableWidget(Button.builder(Component.literal("Ouvrir"), button -> openTypedDirectory())
                .bounds(centerX + 144, height / 2 - 148, 48, 20).build());
        parentButton = addRenderableWidget(Button.builder(Component.literal("Dossier parent"), button -> goParent())
                .bounds(centerX - 70, height / 2 - 121, 140, 20).build());

        rebuildEntries();
        renderPageRows();

        previousButton = addRenderableWidget(Button.builder(Component.literal("← Précédent"), button -> changePage(-1))
                .bounds(centerX - 105, height / 2 + 94, 100, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal("Suivant →"), button -> changePage(1))
                .bounds(centerX + 5, height / 2 + 94, 100, 20).build());
        updatePageButtons();
        addRenderableWidget(Button.builder(Component.literal("Annuler"), button -> onClose())
                .bounds(centerX - 45, height / 2 + 121, 90, 20).build());
    }

    private void openTypedDirectory() {
        try {
            Path requested = Path.of(pathBox.getValue()).toAbsolutePath().normalize();
            if (!Files.isDirectory(requested)) {
                status = "Ce dossier n'existe pas ou n'est pas accessible.";
                return;
            }
            currentDirectory = requested;
            page = 0;
            pathBox.setValue(currentDirectory.toString());
            rebuildEntries();
            rebuildScreen();
        } catch (InvalidPathException | SecurityException exception) {
            status = "Chemin de dossier invalide.";
        }
    }

    private void goParent() {
        Path parent = currentDirectory.getParent();
        if (parent == null) return;
        currentDirectory = parent;
        page = 0;
        pathBox.setValue(currentDirectory.toString());
        rebuildEntries();
        rebuildScreen();
    }

    private void changePage(int amount) {
        int maxPage = Math.max(0, (entries.size() - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(maxPage, page + amount));
        rebuildScreen();
    }

    private void rebuildScreen() {
        clearWidgets();
        init();
    }

    private void rebuildEntries() {
        entries.clear();
        status = "Choisis un fichier audio WAV, AIFF, AU ou MP3.";
        try (Stream<Path> children = Files.list(currentDirectory)) {
            children.filter(path -> Files.isDirectory(path) || isSupportedAudio(path))
                    .sorted(Comparator.<Path, Boolean>comparing(path -> !Files.isDirectory(path))
                            .thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(entries::add);
        } catch (IOException | SecurityException exception) {
            status = "Impossible de lire ce dossier.";
        }
        parentButtonActive();
    }

    private void parentButtonActive() {
        if (parentButton != null) parentButton.active = currentDirectory.getParent() != null;
    }

    private void renderPageRows() {
        int first = page * PAGE_SIZE;
        int last = Math.min(entries.size(), first + PAGE_SIZE);
        int centerX = width / 2;
        int y = height / 2 - 88;
        for (int i = first; i < last; i++) {
            Path entry = entries.get(i);
            boolean directory = Files.isDirectory(entry);
            String prefix = directory ? "[Dossier] " : "[Audio] ";
            String label = font.plainSubstrByWidth(prefix + entry.getFileName(), 350);
            addRenderableWidget(Button.builder(Component.literal(label), button -> select(entry))
                    .bounds(centerX - 180, y + (i - first) * 22, 360, 20).build());
        }
    }

    private void updatePageButtons() {
        int maxPage = Math.max(0, (entries.size() - 1) / PAGE_SIZE);
        previousButton.active = page > 0;
        nextButton.active = page < maxPage;
    }

    private void select(Path entry) {
        if (Files.isDirectory(entry)) {
            currentDirectory = entry.toAbsolutePath().normalize();
            page = 0;
            pathBox.setValue(currentDirectory.toString());
            rebuildEntries();
            rebuildScreen();
            return;
        }
        onSelected.accept(entry.toFile());
    }

    private static boolean isSupportedAudio(Path path) {
        if (!Files.isRegularFile(path)) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".wav") || name.endsWith(".aif") || name.endsWith(".aiff")
                || name.endsWith(".au") || name.endsWith(".mp3");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 174, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal("Page " + (page + 1) + " / "
                + Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE)), width / 2, height / 2 + 80, 0xD0D0D0);
        graphics.drawCenteredString(font, Component.literal(status), width / 2, height / 2 + 148, 0xC0C0C0);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(returnScreen);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
