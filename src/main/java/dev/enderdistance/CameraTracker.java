package dev.enderdistance;

import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Smoothed view state for the current frame: where the camera looks, which way the
 * player is travelling, and how violently the view is being swung around.
 */
final class CameraTracker {
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final double MOVEMENT_EPSILON = 0.02;
    private static final double SPRINT_SPEED = 0.28;
    private static final float MOVEMENT_BLEND = 0.25f;

    private double x;
    private double z;
    private float yaw;
    private float previousYaw;
    private float movementYaw;
    private float movementWeight;
    private float pitch;
    private float turnStress;
    private boolean initialised;
    private long lastFrameNanos;

    void update(Camera camera, LocalPlayer player, EnderDistanceConfig config) {
        long now = System.nanoTime();
        float delta = this.lastFrameNanos == 0L ? 0.016f : Math.min(0.1f, (now - this.lastFrameNanos) * 1e-9f);
        this.lastFrameNanos = now;

        Vec3 position = camera.position();
        this.x = position.x;
        this.z = position.z;
        this.pitch = camera.xRot();

        // Minecraft yaw grows clockwise around +Z, so the XZ heading is simply negated.
        float look = (float) -Math.toRadians(camera.yRot());

        Vec3 velocity = player.getDeltaMovement();
        double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (speed > MOVEMENT_EPSILON) {
            this.movementYaw = (float) Math.atan2(velocity.x, velocity.z);
            this.movementWeight = (float) Math.min(1.0, speed / SPRINT_SPEED);
        } else {
            this.movementWeight = 0.0f;
        }

        float heading = look + wrapPi(this.movementYaw - look) * this.movementWeight * MOVEMENT_BLEND;

        if (!this.initialised) {
            this.yaw = heading;
            this.previousYaw = look;
            this.initialised = true;
        } else {
            float rate = 16.0f - 11.0f * config.smoothing;
            this.yaw += wrapPi(heading - this.yaw) * (1.0f - (float) Math.exp(-rate * delta));
        }

        // Culling fades out while the view is being swung around and eases back in
        // afterwards, so newly revealed terrain is never missing for a visible moment.
        float turn = Math.abs(wrapPi(look - this.previousYaw));
        this.previousYaw = look;
        float stress = delta > 0.0f ? clamp((turn / delta - 0.8f) / 2.0f) : 0.0f;
        float follow = stress > this.turnStress ? 12.0f : 2.5f;
        this.turnStress += (stress - this.turnStress) * (1.0f - (float) Math.exp(-follow * delta));
    }

    void reset() {
        this.initialised = false;
        this.lastFrameNanos = 0L;
        this.turnStress = 0.0f;
        this.movementWeight = 0.0f;
    }

    double cameraX() {
        return this.x;
    }

    double cameraZ() {
        return this.z;
    }

    float pitch() {
        return this.pitch;
    }

    float heading() {
        return this.yaw;
    }

    float movementHeading() {
        return this.movementWeight > 0.0f ? this.movementYaw : Float.NaN;
    }

    float turnStress() {
        return this.turnStress;
    }

    float directionX() {
        return (float) Math.sin(this.yaw);
    }

    float directionZ() {
        return (float) Math.cos(this.yaw);
    }

    private static float wrapPi(float angle) {
        angle %= TAU;
        if (angle > Math.PI) {
            angle -= TAU;
        } else if (angle < -Math.PI) {
            angle += TAU;
        }
        return angle;
    }

    private static float clamp(float value) {
        return value < 0.0f ? 0.0f : Math.min(value, 1.0f);
    }
}