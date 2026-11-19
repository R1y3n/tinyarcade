package com.tiny.arcade;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public final class ArcadeAudio {
    private static volatile boolean enabled = true;

    private final AudioTrack light;
    private final AudioTrack heavy;

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean enabled() {
        return enabled;
    }

    public ArcadeAudio() {
        light = build(false);
        heavy = build(true);
    }

    public void play(boolean useHeavy) {
        if (!enabled) return;
        playTrack(useHeavy ? heavy : light);
    }

    public void release() {
        releaseTrack(light);
        releaseTrack(heavy);
    }

    private static void playTrack(AudioTrack tr) {
        try {
            if (tr == null) return;
            if (tr.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) tr.stop();
            tr.reloadStaticData();
            tr.play();
        } catch (Throwable ignored) {
        }
    }

    private static void releaseTrack(AudioTrack tr) {
        try {
            if (tr != null) tr.release();
        } catch (Throwable ignored) {
        }
    }

    private static AudioTrack build(boolean heavy) {
        int sr = 22050;
        int samples = heavy ? 1500 : 950;
        short[] pcm = new short[samples];
        int rng = heavy ? 0x51A79E3 : 0x2F8C1B5;

        for (int i = 0; i < samples; i++) {
            double t = i / (double) sr;
            rng ^= rng << 13;
            rng ^= rng >>> 17;
            rng ^= rng << 5;
            double noise = (rng & 0xFFFF) / 32768.0 - 1.0;

            double env = Math.exp(-t * (heavy ? 22.0 : 52.0));
            double tone = Math.sin(2.0 * Math.PI * (heavy ? 180.0 : 620.0) * t) * env * 0.52;
            double body = Math.sin(2.0 * Math.PI * (heavy ? 92.0 : 310.0) * t)
                * Math.exp(-t * (heavy ? 17.0 : 38.0)) * 0.26;
            double tick = noise * Math.exp(-t * (heavy ? 90.0 : 145.0)) * 0.20;

            int val = (int) Math.round((tone + body + tick) * 24000.0);
            if (val > 32767) val = 32767;
            else if (val < -32768) val = -32768;
            pcm[i] = (short) val;
        }

        try {
            AudioTrack tr = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sr,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                samples * 2,
                AudioTrack.MODE_STATIC
            );
            tr.write(pcm, 0, samples);
            return tr;
        } catch (Throwable e) {
            return null;
        }
    }
}
