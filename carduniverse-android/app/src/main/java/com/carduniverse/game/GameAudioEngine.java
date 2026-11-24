package com.carduniverse.game;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.ToneGenerator;

public final class GameAudioEngine {
    private static final int SAMPLE_RATE = 22050;
    private AudioTrack ambientTrack;
    private ToneGenerator tones;
    private boolean released;

    public GameAudioEngine() {
        try { tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 28); }
        catch (Exception ignored) { tones = null; }
    }

    public synchronized void startAmbient() {
        if (released || (ambientTrack != null
                && ambientTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING)) return;
        stopAmbient();
        try {
            byte[] samples = createAmbientLoop();
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(samples.length)
                    .build();
            int written = track.write(samples, 0, samples.length);
            if (written <= 0) { track.release(); return; }
            track.setLoopPoints(0, written / 2, -1);
            track.setVolume(0.16f);
            track.play();
            ambientTrack = track;
        } catch (Exception ignored) { stopAmbient(); }
    }

    private byte[] createAmbientLoop() {
        int count = SAMPLE_RATE * 16;
        byte[] result = new byte[count * 2];
        double[] roots = {110.00, 146.83, 130.81, 98.00, 110.00, 164.81, 146.83, 98.00};
        double[] thirds = {130.81, 174.61, 155.56, 123.47, 130.81, 196.00, 174.61, 123.47};
        double[] fifths = {164.81, 220.00, 196.00, 146.83, 164.81, 246.94, 220.00, 146.83};
        double tau = Math.PI * 2.0;
        for (int i = 0; i < count; i++) {
            double time = i / (double) SAMPLE_RATE;
            int chord = (int) (time / 2.0) % roots.length;
            double swell = 0.76 + 0.24 * Math.sin(tau * time / 7.4);
            double pad = Math.sin(tau * roots[chord] * time) * 0.070
                    + Math.sin(tau * thirds[chord] * time) * 0.035
                    + Math.sin(tau * fifths[chord] * time) * 0.025
                    + Math.sin(tau * roots[chord] * 2.0 * time) * 0.009;
            double beat = time % 1.4286;
            double pluck = Math.exp(-beat * 3.8) * Math.sin(tau * fifths[chord] * 2.0 * time) * 0.027;
            double value = Math.max(-0.16, Math.min(0.16, (pad + pluck) * swell));
            short sample = (short) (value * 32767.0);
            result[i * 2] = (byte) (sample & 0xff);
            result[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
        }
        return result;
    }

    public void playClick() {
        ToneGenerator generator = tones;
        if (!released && generator != null) generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 42);
    }

    public void playSuccess() {
        ToneGenerator generator = tones;
        if (!released && generator != null) generator.startTone(ToneGenerator.TONE_PROP_ACK, 110);
    }

    public synchronized void stopAmbient() {
        if (ambientTrack != null) {
            try { if (ambientTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) ambientTrack.pause(); } catch (Exception ignored) {}
            try { ambientTrack.flush(); ambientTrack.stop(); } catch (Exception ignored) {}
            try { ambientTrack.release(); } catch (Exception ignored) {}
            ambientTrack = null;
        }
    }

    public synchronized void release() {
        if (released) return;
        stopAmbient();
        if (tones != null) { try { tones.release(); } catch (Exception ignored) {} tones = null; }
        released = true;
    }
}
