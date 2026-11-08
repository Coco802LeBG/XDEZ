package net.krypton.utility.module;

import net.krypton.utility.Keybinds;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_315;
import net.minecraft.class_3532;
import net.minecraft.class_638;
import net.minecraft.class_745;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime state and motion of the free camera.
 *
 * <p>The camera is a client-only {@link class_745} that is never added to the level. Minecraft is told to render
 * from it ({@code setCameraEntity}); the real player is left untouched, so nothing here produces a packet. The only
 * code path that changes what the server sees is {@link #teleportBody}, which is off unless the user enables it.
 */
final class FreecamController {
    private final FreecamModule module;

    private class_745 camera;
    private class_638 level;
    private class_243 pos = class_243.field_1353;
    private class_243 velocity = class_243.field_1353;
    private class_243 origin = class_243.field_1353;
    private float yaw;
    private float pitch;
    private float originYaw;
    private float originPitch;
    private class_1297 followTarget;
    private boolean controlBody;
    private float lastHealth;
    private long lastFrameNanos;

    FreecamController(FreecamModule module) {
        this.module = module;
    }

    // ------------------------------------------------------------------ lifecycle

    void start(class_310 mc) {
        level = mc.field_1687;
        camera = new class_745(mc.field_1687, mc.field_1724.method_7334());
        class_243 eye = mc.field_1773.method_19418().method_71156();
        yaw = mc.field_1724.method_36454();
        pitch = mc.field_1724.method_36455();
        pos = new class_243(eye.field_1352, eye.field_1351 - camera.method_5751(), eye.field_1350);
        origin = pos;
        originYaw = yaw;
        originPitch = pitch;
        velocity = class_243.field_1353;
        followTarget = null;
        controlBody = !module.freezeBody.get();
        lastHealth = mc.field_1724.method_6032();
        lastFrameNanos = 0L;
        apply();
        mc.method_1504(camera);
    }

    void stop(class_310 mc) {
        if (mc.field_1724 != null && (mc.method_1560() == camera || mc.method_1560() == null)) {
            mc.method_1504(mc.field_1724);
        }
        camera = null;
        level = null;
        followTarget = null;
        velocity = class_243.field_1353;
    }

    boolean isRunning() {
        return camera != null;
    }

    boolean controlBody() {
        return controlBody;
    }

    class_243 eyePosition() {
        return pos.method_1031(0, camera == null ? 1.62 : camera.method_5751(), 0);
    }

    String modeLabel() {
        if (controlBody) {
            return "Body control";
        }
        if (followTarget != null) {
            return "Following " + followTarget.method_5477().getString();
        }
        return "Free";
    }

    // ------------------------------------------------------------------ per-tick update

    void tick(class_310 mc) {
        if (camera == null) {
            return;
        }
        if (mc.field_1724 == null || mc.field_1687 == null || mc.field_1687 != level || mc.field_1724.method_29504()) {
            module.setEnabled(false);
            return;
        }
        float health = mc.field_1724.method_6032();
        if (module.disableOnDamage.get() && health < lastHealth - 0.01f) {
            notify(mc, "Freecam stopped: you took damage");
            module.setEnabled(false);
            return;
        }
        lastHealth = health;
        if (mc.method_1560() != camera) {
            mc.method_1504(camera);
        }
        if (controlBody) {
            velocity = class_243.field_1353;
            apply();
            return;
        }

        if (followTarget != null && (!followTarget.method_5805() || followTarget.method_31481() || followTarget.method_73183() != mc.field_1687)) {
            followTarget = null;
            notify(mc, "Follow target lost");
        }

        // the motion itself runs every rendered frame, see frame()
    }

    /**
     * Per-frame motion. Doing this once per tick (20 Hz) made the camera step and lag behind the mouse; here the
     * pose is advanced every frame with a frame-rate independent response and written without interpolation, so
     * the camera is as smooth as the display allows. The acceleration/deceleration settings keep their meaning
     * ("fraction of the gap closed per tick").
     */
    void frame(class_310 mc) {
        if (camera == null) {
            return;
        }
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0f : (now - lastFrameNanos) / 1.0E9f;
        lastFrameNanos = now;
        dt = Math.min(dt, 0.1f);
        if (controlBody || mc.field_1724 == null || mc.field_1687 != level || level == null) {
            return;
        }
        if (followTarget != null) {
            followStep(dt);
        } else {
            freeStep(mc, dt);
        }
        limitDistance(mc);
        apply();
    }

    /** Converts a per-tick response fraction into the equivalent fraction for a frame of dt seconds. */
    private static double perFrame(double perTick, float dt) {
        double keep = Math.max(0.0, 1.0 - perTick);
        return 1.0 - Math.pow(keep, dt * 20.0);
    }

    private void freeStep(class_310 mc, float dt) {
        class_315 o = mc.field_1690;
        FreecamModule.setBypass(true);
        double forward = (o.field_1894.method_1434() ? 1 : 0) - (o.field_1881.method_1434() ? 1 : 0);
        double strafe = (o.field_1913.method_1434() ? 1 : 0) - (o.field_1849.method_1434() ? 1 : 0);
        double vertical = (o.field_1903.method_1434() ? 1 : 0) - (o.field_1832.method_1434() ? 1 : 0);
        FreecamModule.setBypass(false);
        boolean boost = Keybinds.boost != null && Keybinds.boost.method_1434();
        boolean slow = Keybinds.slow != null && Keybinds.slow.method_1434();

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        class_243 look = new class_243(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
        class_243 left = new class_243(Math.cos(yawRad), 0, Math.sin(yawRad));
        class_243 wish = look.method_1021(forward).method_1019(left.method_1021(strafe)).method_1031(0, vertical * module.verticalFactor.asDouble(), 0);
        boolean moving = wish.method_1027() > 1.0E-6;
        if (moving) {
            wish = wish.method_1029();
        }

        double speed = module.speed.asDouble();
        if (boost) {
            speed *= module.boostMultiplier.asDouble();
        }
        if (slow) {
            speed *= module.slowMultiplier.asDouble();
        }
        class_243 target = wish.method_1021(speed);
        double response = perFrame(moving ? module.acceleration.asDouble() : module.deceleration.asDouble(), dt);
        velocity = velocity.method_1019(target.method_1020(velocity).method_1021(response));
        if (!moving && velocity.method_1027() < 1.0E-6) {
            velocity = class_243.field_1353;
        }
        // velocity is in blocks per tick; scale it to this frame
        pos = moveWithCollision(mc, pos, velocity.method_1021(dt * 20.0));
    }

    private void followStep(float dt) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        class_243 look = new class_243(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
        class_243 anchor = followTarget.method_33571().method_1031(0, module.followHeight.asDouble(), 0);
        class_243 desiredEye = anchor.method_1020(look.method_1021(module.followDistance.asDouble()));
        class_243 desiredFeet = desiredEye.method_1023(0, camera.method_5751(), 0);
        double k = perFrame(module.followSmoothing.asDouble(), dt);
        pos = pos.method_1019(desiredFeet.method_1020(pos).method_1021(k));
        velocity = class_243.field_1353;
    }

    private class_243 moveWithCollision(class_310 mc, class_243 from, class_243 delta) {
        if (!module.collisions.get() || delta.method_1027() == 0) {
            return from.method_1019(delta);
        }
        double size = module.collisionSize.asDouble();
        double eye = camera.method_5751();
        class_243 p = from;
        double[] axes = {delta.field_1352, delta.field_1351, delta.field_1350};
        for (int i = 0; i < 3; i++) {
            if (axes[i] == 0) {
                continue;
            }
            class_243 step = new class_243(i == 0 ? axes[0] : 0, i == 1 ? axes[1] : 0, i == 2 ? axes[2] : 0);
            class_243 next = p.method_1019(step);
            class_238 box = class_238.method_30048(next.method_1031(0, eye, 0), size, size, size);
            if (mc.field_1687.method_18026(box)) {
                p = next;
            } else {
                velocity = new class_243(i == 0 ? 0 : velocity.field_1352, i == 1 ? 0 : velocity.field_1351, i == 2 ? 0 : velocity.field_1350);
            }
        }
        return p;
    }

    private void limitDistance(class_310 mc) {
        double max = module.maxDistance.asDouble();
        if (max <= 0 || mc.field_1724 == null) {
            return;
        }
        class_243 body = mc.field_1724.method_73189();
        class_243 offset = pos.method_1020(body);
        double d = offset.method_1033();
        if (d > max) {
            pos = body.method_1019(offset.method_1021(max / d));
        }
    }

    /** Pushes the current pose to the camera entity. Old and new pose are equal: rendering uses it exactly as is. */
    private void apply() {
        camera.method_5814(pos.field_1352, pos.field_1351, pos.field_1350);
        camera.method_36456(yaw);
        camera.method_36457(pitch);
        camera.method_22862();
    }

    // ------------------------------------------------------------------ user actions

    void look(double yawDelta, double pitchDelta) {
        float sensitivity = module.sensitivity.asFloat();
        float vertical = module.invertY.get() ? -1f : 1f;
        yaw += (float) yawDelta * 0.15f * sensitivity;
        pitch = class_3532.method_15363(pitch + (float) pitchDelta * 0.15f * sensitivity * vertical, -90f, 90f);
        camera.method_36456(yaw);
        camera.method_36457(pitch);
        // keep the previous rotation in sync so mouse look is not delayed by tick interpolation
        camera.field_5982 = yaw;
        camera.field_6004 = pitch;
    }

    void adjustSpeed(class_310 mc, int direction) {
        if (followTarget != null) {
            module.followDistance.set(module.followDistance.asDouble() + direction * 0.5);
            notify(mc, String.format("Follow distance %.1f", module.followDistance.asDouble()));
            return;
        }
        double factor = 1.0 + module.scrollStep.asDouble() / 100.0;
        double next = direction > 0 ? module.speed.asDouble() * factor : module.speed.asDouble() / factor;
        module.speed.set(next);
        notify(mc, String.format("Freecam speed %.2f", module.speed.asDouble()));
    }

    void returnToStart(class_310 mc) {
        pos = origin;
        yaw = originYaw;
        pitch = originPitch;
        velocity = class_243.field_1353;
        followTarget = null;
        apply();
        notify(mc, "Camera returned to its starting point");
    }

    void snapToBody(class_310 mc) {
        if (mc.field_1724 == null) {
            return;
        }
        pos = mc.field_1724.method_73189();
        yaw = mc.field_1724.method_36454();
        pitch = mc.field_1724.method_36455();
        velocity = class_243.field_1353;
        followTarget = null;
        apply();
        notify(mc, "Camera snapped to your body");
    }

    void toggleControl(class_310 mc) {
        controlBody = !controlBody;
        velocity = class_243.field_1353;
        notify(mc, controlBody ? "You control your body; the camera stays parked" : "You control the camera");
    }

    /** Moves the REAL player to the camera. Server-visible, hence disabled unless the user opted in. */
    void teleportBody(class_310 mc) {
        if (!module.allowBodyTeleport.get()) {
            notify(mc, "Enable 'Allow body teleport' (server-visible) in the Freecam options first");
            return;
        }
        if (mc.field_1724 == null) {
            return;
        }
        mc.field_1724.method_5814(pos.field_1352, pos.field_1351, pos.field_1350);
        mc.field_1724.method_18799(class_243.field_1353);
        mc.field_1724.method_36456(yaw);
        mc.field_1724.method_36457(pitch);
        if (module.exitAfterTeleport.get()) {
            module.setEnabled(false);
        }
    }

    void cycleFollow(class_310 mc) {
        List<class_1297> candidates = candidates(mc);
        if (candidates.isEmpty()) {
            notify(mc, "No entity in range to follow");
            return;
        }
        int index = followTarget == null ? -1 : candidates.indexOf(followTarget);
        followTarget = candidates.get((index + 1) % candidates.size());
        notify(mc, "Following " + followTarget.method_5477().getString());
    }

    /** Follows the entity closest to the centre of the camera view, or stops following when none is in view. */
    void followLookedAt(class_310 mc) {
        class_243 eye = eyePosition();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        class_243 look = new class_243(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
        class_1297 best = null;
        double bestDot = Math.cos(Math.toRadians(12));
        for (class_1297 e : candidates(mc)) {
            class_243 to = e.method_5829().method_1005().method_1020(eye);
            if (to.method_1027() < 1.0E-6) {
                continue;
            }
            double dot = to.method_1029().method_1026(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = e;
            }
        }
        if (best == null) {
            if (followTarget != null) {
                followTarget = null;
                notify(mc, "Stopped following");
            } else {
                notify(mc, "No entity at the centre of the view");
            }
            return;
        }
        followTarget = best;
        notify(mc, "Following " + best.method_5477().getString());
    }

    private List<class_1297> candidates(class_310 mc) {
        double range = module.followRange.asDouble();
        double rangeSq = range * range;
        class_243 eye = eyePosition();
        List<class_1297> list = new ArrayList<>();
        for (class_1297 e : mc.field_1687.method_18112()) {
            if (e instanceof class_1309 && e.method_5805() && e.method_5829().method_1005().method_1025(eye) <= rangeSq) {
                list.add(e);
            }
        }
        boolean players = module.preferPlayers.get();
        list.sort((a, b) -> {
            if (players) {
                boolean pa = a instanceof net.minecraft.class_1657;
                boolean pb = b instanceof net.minecraft.class_1657;
                if (pa != pb) {
                    return pa ? -1 : 1;
                }
            }
            return Double.compare(a.method_5829().method_1005().method_1025(eye), b.method_5829().method_1005().method_1025(eye));
        });
        return list;
    }

    private static void notify(class_310 mc, String text) {
        if (mc.field_1724 != null) {
            mc.field_1724.method_7353(class_2561.method_43470(text), true);
        }
    }
}
