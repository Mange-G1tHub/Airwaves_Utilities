package fr.mange.airwavesutilities.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "com.airwaves.logic.RadioNetwork")
abstract class RadioNetworkMixin {
    private static final double AIRWAVES_SURVEYOR_FIXED_RADIO_AUDIBLE_RANGE = 96.0;

    @ModifyConstant(method = "send", constant = @Constant(doubleValue = 26.0), require = 0)
    private static double airwavesUtilities$extendVoicePacketRange(double original) {
        return AIRWAVES_SURVEYOR_FIXED_RADIO_AUDIBLE_RANGE;
    }

    @ModifyConstant(method = "sendSound", constant = @Constant(doubleValue = 32.0), require = 0)
    private static double airwavesUtilities$extendShortRadioSoundRange(double original) {
        return AIRWAVES_SURVEYOR_FIXED_RADIO_AUDIBLE_RANGE;
    }

    @ModifyConstant(method = "sendSound", constant = @Constant(doubleValue = 72.0), require = 0)
    private static double airwavesUtilities$extendLongRadioSoundRange(double original) {
        return AIRWAVES_SURVEYOR_FIXED_RADIO_AUDIBLE_RANGE;
    }
}
