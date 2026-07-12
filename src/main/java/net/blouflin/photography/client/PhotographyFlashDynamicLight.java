package net.blouflin.photography.client;

import net.blouflin.photography.Photography;
import net.blouflin.photography.PhotographyCamera;
import net.blouflin.photography.player.PlayerIsUsingCamera;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class PhotographyFlashDynamicLight {
    private static final String FLASH_ACTIVE_TAG = "photographyFlashActive";
    private static boolean loggedMissingLambDynamicLights;
    private static boolean active;
    private static long flagSetTick = -1L;
    private static long readbackTick = -1L;
    private static int readbackFramesWaited;
    private static final Map<UUID, Integer> REMOTE_FLASH_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> REMOTE_FLASH_PENDING_ON_TICKS = new HashMap<>();
    private static int localClearTicks;

    private PhotographyFlashDynamicLight() {
    }

    public static void startFlashLight() {
        String captureId = PhotographyHud.activeCaptureId();
        forceClearCurrentCameraStack(captureId, "off");
        active = true;
        localClearTicks = 0;
        flagSetTick = clientTick();
        readbackTick = -1L;
        readbackFramesWaited = 0;
        boolean lambPresent = FabricLoader.getInstance().isModLoaded("lambdynlights");
        ItemStack cameraStack = currentCameraStack();
        if (!cameraStack.isEmpty()) {
            setFlashActive(cameraStack, false);
            setFlashActive(cameraStack, true);
        }
        Photography.LOGGER.info("[flash-cycle] captureId={} phase=start tick={} frame=client active=true",
                captureId, flagSetTick);
        Photography.LOGGER.info("[flash-light-cycle] captureId={} phase=on local=true remote=false tick={} frame=client",
                captureId, flagSetTick);
        if (lambPresent) {
            Photography.LOGGER.info("[flash-light] method=lambdynamiclights active=true luminance=15 startedBeforeReadback=true stoppedAfterReadback=false fallback=false");
        } else if (!loggedMissingLambDynamicLights) {
            loggedMissingLambDynamicLights = true;
            Photography.LOGGER.info("[flash-light] method=lambdynamiclights active=false luminance=0 startedBeforeReadback=true stoppedAfterReadback=false fallback=image_only");
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static void markReadback(String captureId, int framesWaited) {
        if (!active) {
            return;
        }
        readbackTick = clientTick();
        readbackFramesWaited = framesWaited;
        Photography.LOGGER.info("[flash-cycle] captureId={} phase=readback tick={} frame={} active=true",
                captureId, readbackTick, framesWaited);
        Photography.LOGGER.info("[flash-light-timing] captureId={} resolvedFlash=true flagSetTick={} readbackTick={} flagClearTick=pending framesWaited={}",
                captureId, flagSetTick, readbackTick, readbackFramesWaited);
    }

    public static void stopFlashLight() {
        if (!active) {
            return;
        }
        active = false;
        long clearTick = clientTick();
        localClearTicks = 3;
        Photography.LOGGER.info("[flash-cycle] captureId={} phase=clear tick={} frame=client active=false",
                net.blouflin.photography.client.PhotographyHud.activeCaptureId(), clearTick);
        Photography.LOGGER.info("[flash-light-cycle] captureId={} phase=clear local=true remote=false tick={} frame=client",
                net.blouflin.photography.client.PhotographyHud.activeCaptureId(), clearTick);
        if (FabricLoader.getInstance().isModLoaded("lambdynlights")) {
            Photography.LOGGER.info("[flash-light] method=lambdynamiclights active=false luminance=0 startedBeforeReadback=true stoppedAfterReadback=true fallback=false");
        }
        Photography.LOGGER.info("[flash-light-timing] captureId={} resolvedFlash=true flagSetTick={} readbackTick={} flagClearTick={} framesWaited={}",
                net.blouflin.photography.client.PhotographyHud.activeCaptureId(),
                flagSetTick,
                readbackTick,
                clearTick,
                readbackFramesWaited);
    }

    public static void setFlashActiveForPlayer(UUID playerId, boolean flashActive, int ticks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Player player = minecraft.level.getPlayerByUUID(playerId);
        if (player == null) {
            return;
        }
        if (flashActive) {
            setFlashActiveOnPlayer(player, false);
            REMOTE_FLASH_TICKS.remove(playerId);
            REMOTE_FLASH_PENDING_ON_TICKS.put(playerId, Math.max(1, ticks));
            Photography.LOGGER.info("[flash-light-cycle] captureId=remote phase=off local=false remote=true tick={} frame=client",
                    clientTick());
            return;
        }
        REMOTE_FLASH_PENDING_ON_TICKS.remove(playerId);
        setFlashActiveOnPlayer(player, false);
        REMOTE_FLASH_TICKS.remove(playerId);
        if (PhotographyCaptureDebug.DEBUG_CAPTURE_IMAGES) {
            Photography.LOGGER.info("[flash-remote] player={} observer=client active={} startTick={} clearTick={} origin=dynamic-light-stack luminance=15",
                    player.getName().getString(),
                    false,
                    clientTick(),
                    clientTick());
        }
    }

    private static void startRemoteFlash(Player player, UUID playerId, int ticks) {
        setFlashActiveOnPlayer(player, true);
        REMOTE_FLASH_TICKS.put(playerId, Math.max(1, ticks));
        if (PhotographyCaptureDebug.DEBUG_CAPTURE_IMAGES) {
            Photography.LOGGER.info("[flash-remote] player={} observer=client active={} startTick={} clearTick={} origin=dynamic-light-stack luminance=15",
                    player.getName().getString(),
                    true,
                    clientTick(),
                    clientTick() + Math.max(1, ticks));
        }
        Photography.LOGGER.info("[flash-light-cycle] captureId=remote phase=on local=false remote=true tick={} frame=client",
                clientTick());
    }

    public static void tickClient() {
        if (active) {
            ItemStack cameraStack = currentCameraStack();
            if (!cameraStack.isEmpty()) {
                setFlashActive(cameraStack, true);
            }
        } else if (localClearTicks > 0) {
            localClearTicks--;
            if (localClearTicks <= 0) {
                ItemStack cameraStack = currentCameraStack();
                if (!cameraStack.isEmpty()) {
                    setFlashActive(cameraStack, false);
                }
                Photography.LOGGER.info("[flash-light-cycle] captureId={} phase=clear local=true remote=false tick={} frame=client",
                        PhotographyHud.activeCaptureId(), clientTick());
            }
        }

        if (!REMOTE_FLASH_PENDING_ON_TICKS.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> pendingIterator = REMOTE_FLASH_PENDING_ON_TICKS.entrySet().iterator();
            while (pendingIterator.hasNext()) {
                Map.Entry<UUID, Integer> entry = pendingIterator.next();
                Player player = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getPlayerByUUID(entry.getKey());
                if (player != null) {
                    startRemoteFlash(player, entry.getKey(), entry.getValue());
                }
                pendingIterator.remove();
            }
        }

        if (REMOTE_FLASH_TICKS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> iterator = REMOTE_FLASH_TICKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;
            Player player = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getPlayerByUUID(entry.getKey());
            if (remaining <= 0 || player == null) {
                if (player != null) {
                    setFlashActiveOnPlayer(player, false);
                }
                iterator.remove();
                Photography.LOGGER.info("[flash-light-cycle] captureId=remote phase=clear local=false remote=true tick={} frame=client",
                        clientTick());
            } else {
                if (player != null) {
                    setFlashActiveOnPlayer(player, true);
                }
                entry.setValue(remaining);
            }
        }
    }

    private static ItemStack currentCameraStack() {
        if (net.minecraft.client.Minecraft.getInstance().player == null) {
            return ItemStack.EMPTY;
        }
        try {
            InteractionHand hand = InteractionHand.valueOf(PhotographyHud.handUsingPhotographyCamera);
            return net.minecraft.client.Minecraft.getInstance().player.getItemInHand(hand);
        } catch (IllegalArgumentException e) {
            return ItemStack.EMPTY;
        }
    }

    public static void setFlashActive(ItemStack stack, boolean active) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, customData -> customData.update(tag -> {
            if (active) {
                tag.putBoolean(FLASH_ACTIVE_TAG, true);
            } else {
                tag.remove(FLASH_ACTIVE_TAG);
            }
        }));
    }

    private static void forceClearCurrentCameraStack(String captureId, String phase) {
        ItemStack cameraStack = currentCameraStack();
        if (!cameraStack.isEmpty()) {
            setFlashActive(cameraStack, false);
        }
        localClearTicks = 0;
        Photography.LOGGER.info("[flash-cycle] captureId={} phase={} tick={} frame=client active=false",
                captureId, phase, clientTick());
    }

    private static void setFlashActiveOnPlayer(Player player, boolean flashActive) {
        PlayerIsUsingCamera cameraState = (PlayerIsUsingCamera) player;
        if (cameraState.isUsingPhotographyCamera()) {
            try {
                ItemStack stack = player.getItemInHand(InteractionHand.valueOf(cameraState.handUsingPhotographyCamera()));
                if (PhotographyCamera.isPhotographyCamera(stack)) {
                    setFlashActive(stack, flashActive);
                    return;
                }
            } catch (IllegalArgumentException | NullPointerException ignored) {
            }
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (PhotographyCamera.isPhotographyCamera(stack)) {
                setFlashActive(stack, flashActive);
            }
        }
    }

    private static long clientTick() {
        if (net.minecraft.client.Minecraft.getInstance().level == null) {
            return -1L;
        }
        return net.minecraft.client.Minecraft.getInstance().level.getGameTime();
    }
}
