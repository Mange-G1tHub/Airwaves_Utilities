package fr.mange.airwavesutilities.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "com.airwaves.client.AudioPlayback")
abstract class AudioPlaybackMixin {
    private static final double AIRWAVES_SURVEYOR_FIXED_RADIO_AUDIBLE_RANGE = 96.0;

    @ModifyConstant(method = "playRadio", constant = @Constant(doubleValue = 24.0), require = 0)
    private static double airwavesUtilities$extendDigitalRadioAudioRange(double original) {
        return AIRWAVES_SURVEYOR_FIXED_RADIO_AUDIBLE_RANGE;
    }
}
