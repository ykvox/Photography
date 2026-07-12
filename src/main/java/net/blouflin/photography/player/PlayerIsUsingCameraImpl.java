package net.blouflin.photography.player;

public class PlayerIsUsingCameraImpl implements PlayerIsUsingCamera {
    private boolean isUsingPhotographyCamera = false;
    private String handUsingPhotographyCamera;
    private boolean isUsingPhotographySelfie = false;

    @Override
    public boolean isUsingPhotographyCamera() {
        return (isUsingPhotographyCamera);
    }

    @Override
    public String handUsingPhotographyCamera() {
        return handUsingPhotographyCamera;
    }

    @Override
    public boolean isUsingPhotographySelfie() {
        return isUsingPhotographySelfie;
    }

    @Override
    public void setUsingPhotographyCamera(boolean isUsingPhotographyCamera, String handUsingPhotographyCamera) {
        setUsingPhotographyCamera(isUsingPhotographyCamera, handUsingPhotographyCamera, false);
    }

    @Override
    public void setUsingPhotographyCamera(boolean isUsingPhotographyCamera, String handUsingPhotographyCamera, boolean selfie) {
        this.isUsingPhotographyCamera = isUsingPhotographyCamera;
        this.handUsingPhotographyCamera = handUsingPhotographyCamera;
        this.isUsingPhotographySelfie = selfie;
    }
}
