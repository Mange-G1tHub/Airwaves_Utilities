package fr.mange.airwavesutilities;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

@Mod(AirwavesUtilities.MOD_ID)
public final class AirwavesUtilities {
    public static final String MOD_ID = "airwaves_utilities";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<Item> AIRWAVES_DETECTOR = ITEMS.register("airwaves_detector",
            () -> new AirwavesDetectorItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> TAPE_IMPORTER = ITEMS.register("tape_importer",
            () -> new TapeImportToolItem(new Item.Properties().stacksTo(1)));
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> TARGET =
            COMPONENTS.register("target", () -> DataComponentType.<GlobalPos>builder()
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC)
                    .build());

    public AirwavesUtilities(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, UtilitiesConfig.SPEC);
        ITEMS.register(modBus);
        modBus.addListener(this::addCreativeTabContents);
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(AirwavesDetectorMarkers::onLevelTick);
        NeoForge.EVENT_BUS.addListener(TapeImportServer::onPlayerLogout);
        COMPONENTS.register(modBus);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(TapeImportPayloads.Start.TYPE, TapeImportPayloads.Start.STREAM_CODEC,
                TapeImportServer::handleStart);
        registrar.playToServer(TapeImportPayloads.Chunk.TYPE, TapeImportPayloads.Chunk.STREAM_CODEC,
                TapeImportServer::handleChunk);
        registrar.playToServer(TapeImportPayloads.Finish.TYPE, TapeImportPayloads.Finish.STREAM_CODEC,
                TapeImportServer::handleFinish);
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (tab.getNamespace().equals("airwaves") && tab.getPath().equals("main")) {
            event.accept(AIRWAVES_DETECTOR);
            event.accept(TAPE_IMPORTER);
        }
    }
}
