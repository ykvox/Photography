package net.blouflin.photography.player;

public interface PlayerIsUsingCamera {
    boolean isUsingPhotographyCamera();
    String handUsingPhotographyCamera();
    boolean isUsingPhotographySelfie();
    void setUsingPhotographyCamera(boolean isUsingPhotographyCamera, String handUsingPhotographyCamera);
    void setUsingPhotographyCamera(boolean isUsingPhotographyCamera, String handUsingPhotographyCamera, boolean selfie);
}
