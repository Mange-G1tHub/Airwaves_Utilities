package fr.mange.airwavesutilities;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class UtilitiesConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue ZONE_BASE_RADIUS;
    public static final ModConfigSpec.IntValue ZONE_MAX_RADIUS;
    public static final ModConfigSpec.DoubleValue ZONE_WEAK_SIGNAL;
    public static final ModConfigSpec.DoubleValue ZONE_WEATHER;
    public static final ModConfigSpec.DoubleValue ZONE_TERRAIN;
    public static final ModConfigSpec.DoubleValue ZONE_JAMMERS;
    public static final ModConfigSpec.DoubleValue ZONE_OBSTACLES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("zone");
        ZONE_BASE_RADIUS = builder.comment("Rayon de base de la zone (blocs), même sans interférences")
                .defineInRange("baseRadius", 16, 4, 64);
        ZONE_MAX_RADIUS = builder.comment("Rayon maximal de la zone (blocs)")
                .defineInRange("maxRadius", 96, 16, 256);
        ZONE_WEAK_SIGNAL = builder.comment("Blocs ajoutés quand la réception tombe à 0%")
                .defineInRange("weakSignal", 24.0, 0.0, 128.0);
        ZONE_WEATHER = builder.comment("Blocs ajoutés si un orage ferait perdre tout le signal")
                .defineInRange("weather", 30.0, 0.0, 128.0);
        ZONE_TERRAIN = builder.comment("Blocs ajoutés si le relief bloque tout le signal")
                .defineInRange("terrain", 30.0, 0.0, 128.0);
        ZONE_JAMMERS = builder.comment("Blocs ajoutés si les brouilleurs bloquent presque tout le signal")
                .defineInRange("jammers", 48.0, 0.0, 128.0);
        ZONE_OBSTACLES =  builder.comment("Blocs ajoutés quand la ligne de vue est très encombrée")
                .defineInRange("obstacles", 12.0, 0.0, 128.0);
        builder.pop();
        SPEC = builder.build();
    }

    private UtilitiesConfig() {}
}