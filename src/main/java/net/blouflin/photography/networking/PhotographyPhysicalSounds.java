package net.blouflin.photography.networking;

import net.blouflin.photography.Photography;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.blouflin.photography.player.PlayerIsUsingCamera;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class PhotographyPhysicalSounds {
    private static final List<ScheduledSound> SCHEDULED_SOUNDS = new ArrayList<>();

    private PhotographyPhysicalSounds() {
    }

    public static void tick(MinecraftServer server) {
        Iterator<ScheduledSound> iterator = SCHEDULED_SOUNDS.iterator();
        while (iterator.hasNext()) {
            ScheduledSound scheduled = iterator.next();
            scheduled.delayTicks--;
            if (scheduled.delayTicks <= 0) {
                play(scheduled.player, scheduled.sound, scheduled.volume, scheduled.pitch);
                iterator.remove();
            }
        }
    }

    public static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void schedule(ServerPlayer player, SoundEvent sound, int delayTicks, float volume, float pitch) {
        if (delayTicks <= 0) {
            play(player, sound, volume, pitch);
            return;
        }
        SCHEDULED_SOUNDS.add(new ScheduledSound(player, sound, delayTicks, volume, pitch));
    }

    public static void playCaptureSequence(ServerPlayer player, boolean resolvedFlash, int shutterTicks, boolean lastFrameAdvance) {
        schedule(player, Photography.CAMERA_SHUTTER_OPEN, 0, 0.7f, 1.08f);
        schedule(player, Photography.CAMERA_SHUTTER, Math.max(1, shutterTicks / 2), 0.65f, 1.0f);
        schedule(player, Photography.CAMERA_SHUTTER_CLOSE, Math.max(1, shutterTicks), 0.7f, 1.08f);
        schedule(player, lastFrameAdvance ? Photography.CAMERA_FILM_ADVANCE_LAST : Photography.CAMERA_FILM_ADVANCE,
                Math.max(3, shutterTicks + 4), 0.7f, lastFrameAdvance ? 0.95f : 1.0f);
    }

    public static void playFlash(ServerPlayer player) {
        for (ServerPlayer observer : player.level().getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(observer, new CameraFlashStatePayload(player.getUUID(), false, 0));
            ServerPlayNetworking.send(observer, new CameraFlashStatePayload(player.getUUID(), true, 8));
        }
        play(player, Photography.CAMERA_FLASH, 0.9f, 1.0f);
        Vec3 pos = flashLensPosition(player);
        ServerLevel level = player.level();
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF), true, true,
                pos.x, pos.y, pos.z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFFFFFF), true, true,
                pos.x, pos.y, pos.z, 14, 0.16d, 0.16d, 0.16d, 0.02d);
        level.sendParticles(ParticleTypes.FIREWORK, true, true,
                pos.x, pos.y, pos.z, 18, 0.18d, 0.18d, 0.18d, 0.06d);
        Photography.LOGGER.info("[flash-effect] spawning post-capture particles for player={} origin={} {} {}",
                player.getName().getString(),
                String.format("%.3f", pos.x),
                String.format("%.3f", pos.y),
                String.format("%.3f", pos.z));
        Photography.LOGGER.info("[flash-visual] player={} mode={} origin=<{}, {}, {}> particles=white_entity+flash firework=true postReadback=true",
                player.getName().getString(),
                ((PlayerIsUsingCamera) player).isUsingPhotographySelfie() ? "selfie" : "normal",
                String.format("%.3f", pos.x),
                String.format("%.3f", pos.y),
                String.format("%.3f", pos.z));
    }

    private static Vec3 flashLensPosition(ServerPlayer player) {
        PlayerIsUsingCamera cameraState = (PlayerIsUsingCamera) player;
        Vec3 eye = player.getEyePosition();
        if (cameraState.isUsingPhotographySelfie()) {
            return eye.add(player.getLookAngle().scale(1.75d));
        }
        return eye;
    }

    private static final class ScheduledSound {
        private final ServerPlayer player;
        private final SoundEvent sound;
        private int delayTicks;
        private final float volume;
        private final float pitch;

        private ScheduledSound(ServerPlayer player, SoundEvent sound, int delayTicks, float volume, float pitch) {
            this.player = player;
            this.sound = sound;
            this.delayTicks = delayTicks;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

}
