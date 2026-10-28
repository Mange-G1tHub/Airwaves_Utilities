package fr.mange.airwavesutilities;

import com.airwaves.api.AirwavesApi;
import com.airwaves.logic.TapeAudio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;

final class TapeAudioFileDecoder {
    private static final AudioFormat TARGET = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, AirwavesApi.SAMPLE_RATE, 16, 1,
            2, AirwavesApi.SAMPLE_RATE, false);

    private TapeAudioFileDecoder() { }

    static List<TapeAudio.Frame> decode(File file) throws IOException, UnsupportedAudioFileException {
        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".mp3")) return decodeMp3(file);
        try (AudioInputStream source = AudioSystem.getAudioInputStream(file)) {
            AudioFormat sourceFormat = source.getFormat();
            AudioInputStream pcm = source;
            if (!TARGET.matches(sourceFormat)) {
                if (!AudioSystem.isConversionSupported(TARGET, sourceFormat)) {
                    throw new UnsupportedAudioFileException("Ce format audio ne peut pas être converti en WAV PCM mono.");
                }
                pcm = AudioSystem.getAudioInputStream(TARGET, source);
            }
            if (pcm == source) return decodePcm(source);
            try (AudioInputStream converted = pcm) {
                return decodePcm(converted);
            }
        }
    }

    private static List<TapeAudio.Frame> decodePcm(AudioInputStream pcm) throws IOException, UnsupportedAudioFileException {
        int maxPcmBytes = TapeAudio.MAX_FRAMES * AirwavesApi.FRAME_SAMPLES * 2;
        byte[] samples = pcm.readNBytes(maxPcmBytes + 1);
        if (samples.length > maxPcmBytes)
            throw new UnsupportedAudioFileException("Le fichier dépasse la durée maximale de 5 minutes.");
        int sampleCount = samples.length / 2;
        if (sampleCount < AirwavesApi.FRAME_SAMPLES)
            throw new UnsupportedAudioFileException("Le fichier est vide ou dure moins de 20 ms.");

        short[] pcmSamples = new short[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int byteIndex = i * 2;
            pcmSamples[i] = (short) ((samples[byteIndex] & 0xff) | (samples[byteIndex + 1] << 8));
        }
        return encodeSamples(pcmSamples, sampleCount);
    }

    private static List<TapeAudio.Frame> decodeMp3(File file) throws IOException, UnsupportedAudioFileException {
        short[] monoSamples = new short[8192];
        int sampleCount = 0;
        int sourceRate = -1;
        javazoom.jl.decoder.Decoder decoder = new javazoom.jl.decoder.Decoder();
        Bitstream bitstream;
        try {
            bitstream = new Bitstream(new BufferedInputStream(new FileInputStream(file)));
        } catch (RuntimeException exception) {
            throw new IOException("Impossible d'ouvrir le fichier MP3.", exception);
        }

        try {
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                try {
                    SampleBuffer decoded = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    int channels = decoded.getChannelCount();
                    int frameRate = decoded.getSampleFrequency();
                    if (channels < 1 || channels > 2 || frameRate <= 0)
                        throw new UnsupportedAudioFileException("Paramètres audio MP3 non pris en charge.");
                    if (sourceRate < 0) sourceRate = frameRate;
                    if (sourceRate != frameRate)
                        throw new UnsupportedAudioFileException("La fréquence audio change au milieu du MP3.");

                    short[] frame = decoded.getBuffer();
                    int frameSamples = decoded.getBufferLength() / channels;
                    if ((long) sampleCount + frameSamples > (long) sourceRate * 301)
                        throw new UnsupportedAudioFileException("Le fichier dépasse la durée maximale de 5 minutes.");
                    int needed = sampleCount + frameSamples;
                    if (needed > monoSamples.length) {
                        int capacity = monoSamples.length;
                        while (capacity < needed) capacity = Math.max(capacity * 2, needed);
                        monoSamples = java.util.Arrays.copyOf(monoSamples, capacity);
                    }
                    for (int i = 0; i < frameSamples; i++) {
                        int left = frame[i * channels];
                        monoSamples[sampleCount + i] = channels == 1
                                ? (short) left
                                : (short) ((left + frame[i * channels + 1]) / 2);
                    }
                    sampleCount = needed;
                } finally {
                    bitstream.closeFrame();
                }
            }
        } catch (JavaLayerException exception) {
            UnsupportedAudioFileException invalid = new UnsupportedAudioFileException("Le fichier MP3 est illisible ou endommagé.");
            invalid.initCause(exception);
            throw invalid;
        } finally {
            try {
                bitstream.close();
            } catch (JavaLayerException ignored) {
                // The audio has already been decoded or a more useful error is being reported.
            }
        }

        if (sampleCount < 1 || sourceRate < 1)
            throw new UnsupportedAudioFileException("Aucune piste audio MP3 lisible n'a été trouvée.");
        int targetCount = (int) Math.ceil(sampleCount * (double) AirwavesApi.SAMPLE_RATE / sourceRate);
        if (targetCount > TapeAudio.MAX_FRAMES * AirwavesApi.FRAME_SAMPLES)
            throw new UnsupportedAudioFileException("Le fichier dépasse la durée maximale de 5 minutes.");
        short[] resampled = new short[targetCount];
        for (int i = 0; i < targetCount; i++) {
            double position = i * (double) sourceRate / AirwavesApi.SAMPLE_RATE;
            int first = Math.min((int) position, sampleCount - 1);
            int second = Math.min(first + 1, sampleCount - 1);
            double fraction = position - first;
            int value = (int) Math.round(monoSamples[first] * (1.0 - fraction) + monoSamples[second] * fraction);
            resampled[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
        }
        if (targetCount < AirwavesApi.FRAME_SAMPLES)
            throw new UnsupportedAudioFileException("Le fichier est vide ou dure moins de 20 ms.");
        return encodeSamples(resampled, targetCount);
    }

    private static List<TapeAudio.Frame> encodeSamples(short[] samples, int sampleCount) {

        int frameCount = (sampleCount + AirwavesApi.FRAME_SAMPLES - 1) / AirwavesApi.FRAME_SAMPLES;
        List<TapeAudio.Frame> frames = new ArrayList<>(frameCount);
        short[] frameSamples = new short[AirwavesApi.FRAME_SAMPLES];
        for (int frame = 0; frame < frameCount; frame++) {
            java.util.Arrays.fill(frameSamples, (short) 0);
            int start = frame * AirwavesApi.FRAME_SAMPLES;
            int available = Math.min(AirwavesApi.FRAME_SAMPLES, sampleCount - start);
            System.arraycopy(samples, start, frameSamples, 0, available);
            frames.add(new TapeAudio.Frame(frame * 20, AirwavesApi.encode(frameSamples)));
        }
        return List.copyOf(frames);
    }

}
