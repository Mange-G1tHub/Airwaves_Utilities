package fr.mange.airwavesutilities;

import net.minecraft.client.Minecraft;

public final class TapeImportClient {
    private TapeImportClient() { }

    public static void open() {
        Minecraft.getInstance().setScreen(new TapeImportScreen());
    }
}
