package fr.mange.airwavesutilities;

import com.airwaves.ModRegistry;
import com.airwaves.api.AirwavesApi;
import com.airwaves.item.TapeItem;
import com.airwaves.logic.TapeAudio;
import com.airwaves.logic.TapeData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TapeImportServer {
    public static final int FRAME_BYTES = AirwavesApi.FRAME_SAMPLES;
    public static final int FRAMES_PER_CHUNK = 32;
    public static final int MAX_CHUNK_BYTES = FRAME_BYTES * FRAMES_PER_CHUNK;
    private static final long TRANSFER_TIMEOUT_TICKS = 1200;
    private static final Map<UUID, Transfer> TRANSFERS = new ConcurrentHashMap<>();

    private TapeImportServer() { }

    public static void handleStart(TapeImportPayloads.Start payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> start(player, payload));
    }

    public static void handleChunk(TapeImportPayloads.Chunk payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> acceptChunk(player, payload));
    }

    public static void handleFinish(TapeImportPayloads.Finish payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> finish(player, payload));
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TRANSFERS.remove(event.getEntity().getUUID());
    }

    private static void start(ServerPlayer player, TapeImportPayloads.Start payload) {
        int maxFrames = TapeAudio.MAX_FRAMES;
        if (payload.slot() < 0 || payload.slot() >= player.getInventory().items.size()
                || payload.totalFrames() < 1 || payload.totalFrames() > maxFrames) {
            fail(player, "Import refusé : durée ou emplacement de tape invalide.");
            return;
        }
        ItemStack tape = player.getInventory().getItem(payload.slot());
        if (!(tape.getItem() instanceof TapeItem)) {
            fail(player, "La tape sélectionnée n'est plus dans cet emplacement.");
            return;
        }
        TRANSFERS.put(player.getUUID(), new Transfer(payload.transferId(), payload.slot(),
                payload.totalFrames(), player.serverLevel().getGameTime()));
        player.displayClientMessage(Component.literal("Import audio reçu, transfert en cours…")
                .withStyle(ChatFormatting.YELLOW), false);
    }

    private static void acceptChunk(ServerPlayer player, TapeImportPayloads.Chunk payload) {
        Transfer transfer = TRANSFERS.get(player.getUUID());
        if (transfer == null || !transfer.id.equals(payload.transferId())) return;
        if (player.serverLevel().getGameTime() - transfer.lastActivity > TRANSFER_TIMEOUT_TICKS) {
            TRANSFERS.remove(player.getUUID(), transfer);
            fail(player, "Import interrompu : transfert expiré.");
            return;
        }
        byte[] bytes = payload.encodedFrames();
        if (bytes.length == 0 || bytes.length > MAX_CHUNK_BYTES || bytes.length % FRAME_BYTES != 0
                || payload.firstFrame() < 0 || payload.firstFrame() % FRAMES_PER_CHUNK != 0) {
            TRANSFERS.remove(player.getUUID(), transfer);
            fail(player, "Import refusé : paquet audio invalide.");
            return;
        }
        int count = bytes.length / FRAME_BYTES;
        if (count > FRAMES_PER_CHUNK || payload.firstFrame() + count > transfer.totalFrames) {
            TRANSFERS.remove(player.getUUID(), transfer);
            fail(player, "Import refusé : paquet audio hors limites.");
            return;
        }
        for (int i = 0; i < count; i++) {
            int frame = payload.firstFrame() + i;
            if (transfer.received.get(frame)) {
                TRANSFERS.remove(player.getUUID(), transfer);
                fail(player, "Import refusé : paquet audio répété.");
                return;
            }
            transfer.frames[frame] = Arrays.copyOfRange(bytes, i * FRAME_BYTES, (i + 1) * FRAME_BYTES);
            transfer.received.set(frame);
        }
        transfer.lastActivity = player.serverLevel().getGameTime();
    }

    private static void finish(ServerPlayer player, TapeImportPayloads.Finish payload) {
        Transfer transfer = TRANSFERS.get(player.getUUID());
        if (transfer == null || !transfer.id.equals(payload.transferId())) return;
        TRANSFERS.remove(player.getUUID(), transfer);
        if (transfer.received.cardinality() != transfer.totalFrames) {
            fail(player, "Import incomplet : certaines données audio manquent.");
            return;
        }
        ItemStack tape = player.getInventory().getItem(transfer.slot);
        if (!(tape.getItem() instanceof TapeItem)) {
            fail(player, "La tape sélectionnée a changé pendant l'import.");
            return;
        }

        String audioId = UUID.randomUUID().toString();
        List<TapeAudio.Frame> frames = new java.util.ArrayList<>(transfer.totalFrames);
        for (int i = 0; i < transfer.totalFrames; i++)
            frames.add(new TapeAudio.Frame(i * 20, transfer.frames[i]));
        TapeAudio.save(player.serverLevel().getServer(), audioId, frames);

        int lengthTicks = (transfer.totalFrames * 20 + 49) / 50;
        int voiceSeconds = transfer.totalFrames / 50;
        tape.set(ModRegistry.TAPE.get(), TapeData.of(lengthTicks, List.of(), audioId, voiceSeconds));
        player.getInventory().setChanged();
        player.displayClientMessage(Component.literal("Fichier importé sur la tape ("
                        + TapeData.time(lengthTicks) + ").")
                .withStyle(ChatFormatting.GREEN), false);
    }

    private static void fail(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), false);
    }

    private static final class Transfer {
        private final UUID id;
        private final int slot;
        private final int totalFrames;
        private final byte[][] frames;
        private final BitSet received;
        private long lastActivity;

        private Transfer(UUID id, int slot, int totalFrames, long lastActivity) {
            this.id = id;
            this.slot = slot;
            this.totalFrames = totalFrames;
            this.frames = new byte[totalFrames][];
            this.received = new BitSet(totalFrames);
            this.lastActivity = lastActivity;
        }
    }
}
