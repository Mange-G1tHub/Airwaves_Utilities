package fr.mange.airwavesutilities.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "com.airwaves.client.RadioSounds")
abstract class RadioSoundsMixin {
    private static final int AIRWAVES_SURVEYOR_FIXED_RADIO_AUDIBLE_RANGE = 96;

    // RadioSounds stores the kind-specific distances as integer literals (32 / 72),
    // unlike the other radio classes which use double literals.
    @ModifyConstant(method = "play", constant = @Constant(intValue = 32), require = 0)
    private static int airwavesUtilities$extendShortRadioSoundRange(int original) {
        return AIRWAVES_SURVEYOR_FIXED_RADIO_AUDIBLE_RANGE;
    }

    @ModifyConstant(method = "play", constant = @Constant(intValue = 72), require = 0)
    private static int airwavesUtilities$extendLongRadioSoundRange(int original) {
        return AIRWAVES_SURVEYOR_FIXED_RADIO_AUDIBLE_RANGE;
    }
}
