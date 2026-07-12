package net.blouflin.photography.client;

import net.minecraft.resources.Identifier;

public final class PhotographyCameraSettings {
    private CompositionGuide compositionGuide = CompositionGuide.NONE;
    private SelfTimer selfTimer = SelfTimer.OFF;
    private ShutterSpeed shutterSpeed = ShutterSpeed.AUTO;
    private FocalLength focalLength = FocalLength.MM_35;
    private FlashMode flashMode = FlashMode.OFF;

    public CompositionGuide compositionGuide() {
        return compositionGuide;
    }

    public SelfTimer selfTimer() {
        return selfTimer;
    }

    public ShutterSpeed shutterSpeed() {
        return shutterSpeed;
    }

    public FocalLength focalLength() {
        return focalLength;
    }

    public FlashMode flashMode() {
        return flashMode;
    }

    public void cycleCompositionGuide() {
        compositionGuide = compositionGuide.next();
    }

    public void cycleSelfTimer() {
        selfTimer = selfTimer.next();
    }

    public void cycleShutterSpeed() {
        shutterSpeed = shutterSpeed.next();
    }

    public void cycleFocalLength() {
        focalLength = focalLength.next();
    }

    public void cycleFlashMode() {
        flashMode = flashMode.next();
    }

    public enum CompositionGuide {
        NONE("none", null, "camera_controls/composition_guide/none"),
        CROSSHAIR(
                "crosshair",
                Identifier.fromNamespaceAndPath("photography", "textures/gui/viewfinder/composition_guide/crosshair.png"),
                "camera_controls/composition_guide/crosshair"),
        QUADS(
                "quads",
                Identifier.fromNamespaceAndPath("photography", "textures/gui/viewfinder/composition_guide/quads.png"),
                "camera_controls/composition_guide/quads"),
        RULE_OF_THIRDS(
                "thirds",
                Identifier.fromNamespaceAndPath("photography", "textures/gui/viewfinder/composition_guide/rule_of_thirds.png"),
                "camera_controls/composition_guide/rule_of_thirds");

        private final String label;
        private final Identifier overlayTexture;
        private final Identifier controlSprite;

        CompositionGuide(String label, Identifier overlayTexture, String controlSpritePath) {
            this.label = label;
            this.overlayTexture = overlayTexture;
            this.controlSprite = Identifier.fromNamespaceAndPath("photography", controlSpritePath);
        }

        public String label() {
            return label;
        }

        public Identifier overlayTexture() {
            return overlayTexture;
        }

        public Identifier controlSprite() {
            return controlSprite;
        }

        private CompositionGuide next() {
            CompositionGuide[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum SelfTimer {
        OFF("off", 0, "camera_controls/self_timer/timer_off"),
        ONE_SECOND("1s", 1, "camera_controls/self_timer/timer_one"),
        TWO_SECONDS("2s", 2, "camera_controls/self_timer/timer_two"),
        FIVE_SECONDS("5s", 5, "camera_controls/self_timer/timer_five"),
        TEN_SECONDS("10s", 10, "camera_controls/self_timer/timer_ten");

        private final String label;
        private final int seconds;
        private final Identifier controlSprite;

        SelfTimer(String label, int seconds, String controlSpritePath) {
            this.label = label;
            this.seconds = seconds;
            this.controlSprite = Identifier.fromNamespaceAndPath("photography", controlSpritePath);
        }

        public String label() {
            return label;
        }

        public Identifier controlSprite() {
            return controlSprite;
        }

        public int seconds() {
            return seconds;
        }

        public int ticks() {
            return seconds * 20;
        }

        public boolean isOff() {
            return seconds == 0;
        }

        private SelfTimer next() {
            SelfTimer[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum ShutterSpeed {
        AUTO("Auto", "Auto", 60, 1.0f, 3),
        SPEED_1_15("1/15", "1/15", 15, 1.45f, 5),
        SPEED_1_30("1/30", "1/30", 30, 1.25f, 4),
        SPEED_1_60("1/60", "1/60", 60, 1.0f, 3),
        SPEED_1_125("1/125", "1/125", 125, 0.82f, 2),
        SPEED_1_250("1/250", "1/250", 250, 0.68f, 2),
        SPEED_1_500("1/500", "1/500", 500, 0.55f, 2);

        private static final Identifier CONTROL_SPRITE = Identifier.fromNamespaceAndPath("photography", "camera_controls/shutter_speed_dial");

        private final String label;
        private final String notation;
        private final int denominator;
        private final float brightnessMultiplier;
        private final int visualTicks;

        ShutterSpeed(String label, String notation, int denominator, float brightnessMultiplier, int visualTicks) {
            this.label = label;
            this.notation = notation;
            this.denominator = denominator;
            this.brightnessMultiplier = brightnessMultiplier;
            this.visualTicks = visualTicks;
        }

        public String label() {
            return label;
        }

        public Identifier controlSprite() {
            return CONTROL_SPRITE;
        }

        public String notation() {
            return notation;
        }

        public float brightnessMultiplier() {
            return brightnessMultiplier;
        }

        public float durationSeconds() {
            return 1.0f / denominator;
        }

        public int durationMilliseconds() {
            return Math.round(1000.0f / denominator);
        }

        public int visualTicks() {
            return visualTicks;
        }

        private ShutterSpeed next() {
            ShutterSpeed[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum FocalLength {
        MM_24(24),
        MM_35(35),
        MM_50(50),
        MM_70(70);

        private final int millimeters;

        FocalLength(int millimeters) {
            this.millimeters = millimeters;
        }

        public int millimeters() {
            return millimeters;
        }

        public String label() {
            return millimeters + "mm";
        }

        public double fovMultiplier() {
            return 35.0d / millimeters;
        }

        private FocalLength next() {
            FocalLength[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum FlashMode {
        OFF("off", "camera_controls/flash_mode/flash_off"),
        ON("on", "camera_controls/flash_mode/flash_on"),
        AUTO("auto", "camera_controls/flash_mode/flash_auto");

        private final String label;
        private final Identifier controlSprite;

        FlashMode(String label, String controlSpritePath) {
            this.label = label;
            this.controlSprite = Identifier.fromNamespaceAndPath("photography", controlSpritePath);
        }

        public String label() {
            return label;
        }

        public Identifier controlSprite() {
            return controlSprite;
        }

        public boolean isOn() {
            return this == ON;
        }

        public boolean isAuto() {
            return this == AUTO;
        }

        private FlashMode next() {
            FlashMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}
