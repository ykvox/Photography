package net.blouflin.photography.client;

public interface PhotographyCameraRenderState {
    boolean photography$isUsingCamera();
    String photography$cameraHand();
    boolean photography$isSelfie();
    void photography$setCameraState(boolean usingCamera, String hand, boolean selfie);
}
