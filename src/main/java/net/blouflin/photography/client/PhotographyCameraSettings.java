package net.blouflin.photography.client;

import net.minecraft.resources.Identifier;

public final class PhotographyCameraSettings {
    private CompositionGuide compositionGuide = CompositionGuide.NONE;
    private SelfTimer selfTimer = SelfTimer.OFF;
    private ShutterSpeed shutterSpeed = ShutterSpeed.AUTO;

    public CompositionGuide compositionGuide() {
        return compositionGuide;
    }

    public SelfTimer selfTimer() {
        return selfTimer;
    }

    public ShutterSpeed shutterSpeed() {
        return shutterSpeed;
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
        OFF("off", "camera_controls/self_timer/timer_off"),
        ONE_SECOND("1s", "camera_controls/self_timer/timer_one"),
        TWO_SECONDS("2s", "camera_controls/self_timer/timer_two"),
        FIVE_SECONDS("5s", "camera_controls/self_timer/timer_five"),
        TEN_SECONDS("10s", "camera_controls/self_timer/timer_ten");

        private final String label;
        private final Identifier controlSprite;

        SelfTimer(String label, String controlSpritePath) {
            this.label = label;
            this.controlSprite = Identifier.fromNamespaceAndPath("photography", controlSpritePath);
        }

        public String label() {
            return label;
        }

        public Identifier controlSprite() {
            return controlSprite;
        }

        private SelfTimer next() {
            SelfTimer[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum ShutterSpeed {
        AUTO("auto"),
        FAST("fast"),
        SLOW("slow");

        private static final Identifier CONTROL_SPRITE = Identifier.fromNamespaceAndPath("photography", "camera_controls/shutter_speed_dial");

        private final String label;

        ShutterSpeed(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public Identifier controlSprite() {
            return CONTROL_SPRITE;
        }

        private ShutterSpeed next() {
            ShutterSpeed[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}
