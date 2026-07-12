package net.blouflin.photography.client;

import net.blouflin.photography.Photography;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

public final class PhotographyCameraSounds {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Minecraft CLIENT = Minecraft.getInstance();
    private static long lastScrollLensRingMs;

    private PhotographyCameraSounds() {
    }

    public static void playViewfinderOpen() {
        play(Photography.CAMERA_VIEWFINDER_OPEN, 0.35f, 0.9f);
    }

    public static void playViewfinderClose() {
        play(Photography.CAMERA_VIEWFINDER_CLOSE, 0.35f, 0.9f);
    }

    public static void playButtonClick() {
        play(Photography.CAMERA_BUTTON_CLICK, 0.6f, 1.0f);
    }

    public static void playButtonRelease() {
        play(Photography.CAMERA_RELEASE_BUTTON_CLICK, 0.45f, 1.0f);
    }

    public static void playDialClick() {
        play(Photography.CAMERA_DIAL_CLICK, 0.65f, 1.0f);
    }

    public static void playLensRing() {
        play(Photography.CAMERA_LENS_RING_CLICK, 0.9f, 1.0f);
    }

    public static void playLensRingBurst() {
        int[] delays = {0, 3, 5, 8, 13};
        for (int delay : delays) {
            playDelayed(Photography.CAMERA_LENS_RING_CLICK, 0.88f, 1.0f, delay);
        }
    }

    public static void playRateLimitedScrollLensRing() {
        long now = System.currentTimeMillis();
        if (now - lastScrollLensRingMs < 90L) {
            return;
        }
        lastScrollLensRingMs = now;
        play(Photography.CAMERA_LENS_RING_CLICK, 0.55f, 1.0f);
    }

    public static void playTimerTick() {
        play(Photography.CAMERA_TIMER_TICK, 1.0f, 0.8f);
    }

    public static void playPrint() {
        play(Photography.CAMERA_PRINT, 2.25f, 1.0f);
    }

    private static void play(SoundEvent sound, float volume, float pitch) {
        playDelayed(sound, volume, pitch, 0);
    }

    private static void playDelayed(SoundEvent sound, float volume, float pitch, int delayTicks) {
        Player player = CLIENT.player;
        if (player == null) {
            return;
        }

        SimpleSoundInstance instance = new SimpleSoundInstance(
                sound,
                SoundSource.PLAYERS,
                volume,
                pitch,
                RANDOM,
                player.getX(),
                player.getY(),
                player.getZ());
        if (delayTicks <= 0) {
            CLIENT.getSoundManager().play(instance);
        } else {
            CLIENT.getSoundManager().playDelayed(instance, delayTicks);
        }
    }
}
