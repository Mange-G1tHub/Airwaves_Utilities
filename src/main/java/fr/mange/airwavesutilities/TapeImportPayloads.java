package fr.mange.airwavesutilities;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class TapeImportPayloads {
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            ByteBufCodecs.stringUtf8(36).map(UUID::fromString, UUID::toString).cast();

    private TapeImportPayloads() { }

    public record Start(int slot, int totalFrames, UUID transferId) implements CustomPacketPayload {
        public static final Type<Start> TYPE = new Type<>(id("tape_import_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Start> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Start::slot,
                ByteBufCodecs.VAR_INT, Start::totalFrames,
                UUID_CODEC, Start::transferId,
                Start::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Chunk(UUID transferId, int firstFrame, byte[] encodedFrames) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE = new Type<>(id("tape_import_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Chunk> STREAM_CODEC = StreamCodec.composite(
                UUID_CODEC, Chunk::transferId,
                ByteBufCodecs.VAR_INT, Chunk::firstFrame,
                ByteBufCodecs.byteArray(TapeImportServer.MAX_CHUNK_BYTES), Chunk::encodedFrames,
                Chunk::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Finish(UUID transferId) implements CustomPacketPayload {
        public static final Type<Finish> TYPE = new Type<>(id("tape_import_finish"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Finish> STREAM_CODEC = UUID_CODEC.map(
                Finish::new, Finish::transferId);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AirwavesUtilities.MOD_ID, path);
    }
}
