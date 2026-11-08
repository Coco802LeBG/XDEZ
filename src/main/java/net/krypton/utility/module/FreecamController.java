package net.krypton.utility.module;

import net.krypton.utility.Keybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime state and motion of the free camera.
 *
 * <p>The camera is a client-only {@link RemotePlayer} that is never added to the level. Minecraft is told to render
 * from it ({@code setCameraEntity}); the real player is left untouched, so nothing here produces a packet. The only
 * code path that changes what the server sees is {@link #teleportBody}, which is off unless the user enables it.
 */
final class FreecamController {
    private final FreecamModule module;

    private RemotePlayer camera;
    private ClientLevel level;
    private Vec3 pos = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;
    private Vec3 origin = Vec3.ZERO;
    private float yaw;
    private float pitch;
    private float originYaw;
    private float originPitch;
    private Entity followTarget;
    private boolean controlBody;
    private float lastHealth;

    FreecamController(FreecamModule module) {
        this.module = module;
    }

    // ------------------------------------------------------------------ lifecycle

    void start(Minecraft mc) {
        level = mc.level;
        camera = new RemotePlayer(mc.level, mc.player.getGameProfile());
        Vec3 eye = mc.gameRenderer.getMainCamera().position();
        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
        pos = new Vec3(eye.x, eye.y - camera.getEyeHeight(), eye.z);
        origin = pos;
        originYaw = yaw;
        originPitch = pitch;
        velocity = Vec3.ZERO;
        followTarget = null;
        controlBody = !module.freezeBody.get();
        lastHealth = mc.player.getHealth();
        apply(false);
        mc.setCameraEntity(camera);
    }

    void stop(Minecraft mc) {
        if (mc.player != null && (mc.getCameraEntity() == camera || mc.getCameraEntity() == null)) {
            mc.setCameraEntity(mc.player);
        }
        camera = null;
        level = null;
        followTarget = null;
        velocity = Vec3.ZERO;
    }

    boolean isRunning() {
        return camera != null;
    }

    boolean controlBody() {
        return controlBody;
    }

    Vec3 eyePosition() {
        return pos.add(0, camera == null ? 1.62 : camera.getEyeHeight(), 0);
    }

    String modeLabel() {
        if (controlBody) {
            return "Body control";
        }
        if (followTarget != null) {
            return "Following " + followTarget.getName().getString();
        }
        return "Free";
    }

    // ------------------------------------------------------------------ per-tick update

    void tick(Minecraft mc) {
        if (camera == null) {
            return;
        }
        if (mc.player == null || mc.level == null || mc.level != level || mc.player.isDeadOrDying()) {
            module.setEnabled(false);
            return;
        }
        float health = mc.player.getHealth();
        if (module.disableOnDamage.get() && health < lastHealth - 0.01f) {
            notify(mc, "Freecam stopped: you took damage");
            module.setEnabled(false);
            return;
        }
        lastHealth = health;
        if (mc.getCameraEntity() != camera) {
            mc.setCameraEntity(camera);
        }
        if (controlBody) {
            velocity = Vec3.ZERO;
            apply(true);
            return;
        }

        if (followTarget != null && (!followTarget.isAlive() || followTarget.isRemoved() || followTarget.level() != mc.level)) {
            followTarget = null;
            notify(mc, "Follow target lost");
        }

        if (followTarget != null) {
            followStep();
        } else {
            freeStep(mc);
        }
        limitDistance(mc);
        apply(true);
    }

    private void freeStep(Minecraft mc) {
        Options o = mc.options;
        FreecamModule.setBypass(true);
        double forward = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        double strafe = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        double vertical = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
        FreecamModule.setBypass(false);
        boolean boost = Keybinds.boost != null && Keybinds.boost.isDown();
        boolean slow = Keybinds.slow != null && Keybinds.slow.isDown();

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        Vec3 look = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
        Vec3 left = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad));
        Vec3 wish = look.scale(forward).add(left.scale(strafe)).add(0, vertical * module.verticalFactor.asDouble(), 0);
        boolean moving = wish.lengthSqr() > 1.0E-6;
        if (moving) {
            wish = wish.normalize();
        }

        double speed = module.speed.asDouble();
        if (boost) {
            speed *= module.boostMultiplier.asDouble();
        }
        if (slow) {
            speed *= module.slowMultiplier.asDouble();
        }
        Vec3 target = wish.scale(speed);
        double response = moving ? module.acceleration.asDouble() : module.deceleration.asDouble();
        velocity = velocity.add(target.subtract(velocity).scale(response));
        if (!moving && velocity.lengthSqr() < 1.0E-6) {
            velocity = Vec3.ZERO;
        }
        pos = moveWithCollision(mc, pos, velocity);
    }

    private void followStep() {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        Vec3 look = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
        Vec3 anchor = followTarget.getEyePosition().add(0, module.followHeight.asDouble(), 0);
        Vec3 desiredEye = anchor.subtract(look.scale(module.followDistance.asDouble()));
        Vec3 desiredFeet = desiredEye.subtract(0, camera.getEyeHeight(), 0);
        double k = module.followSmoothing.asDouble();
        pos = pos.add(desiredFeet.subtract(pos).scale(k));
        velocity = Vec3.ZERO;
    }

    private Vec3 moveWithCollision(Minecraft mc, Vec3 from, Vec3 delta) {
        if (!module.collisions.get() || delta.lengthSqr() == 0) {
            return from.add(delta);
        }
        double size = module.collisionSize.asDouble();
        double eye = camera.getEyeHeight();
        Vec3 p = from;
        double[] axes = {delta.x, delta.y, delta.z};
        for (int i = 0; i < 3; i++) {
            if (axes[i] == 0) {
                continue;
            }
            Vec3 step = new Vec3(i == 0 ? axes[0] : 0, i == 1 ? axes[1] : 0, i == 2 ? axes[2] : 0);
            Vec3 next = p.add(step);
            AABB box = AABB.ofSize(next.add(0, eye, 0), size, size, size);
            if (mc.level.noCollision(box)) {
                p = next;
            } else {
                velocity = new Vec3(i == 0 ? 0 : velocity.x, i == 1 ? 0 : velocity.y, i == 2 ? 0 : velocity.z);
            }
        }
        return p;
    }

    private void limitDistance(Minecraft mc) {
        double max = module.maxDistance.asDouble();
        if (max <= 0 || mc.player == null) {
            return;
        }
        Vec3 body = mc.player.position();
        Vec3 offset = pos.subtract(body);
        double d = offset.length();
        if (d > max) {
            pos = body.add(offset.scale(max / d));
        }
    }

    /** Pushes the current pose to the camera entity; with interpolation the previous pose is kept for smooth rendering. */
    private void apply(boolean interpolate) {
        if (interpolate) {
            camera.setOldPosAndRot();
        }
        camera.setPos(pos.x, pos.y, pos.z);
        camera.setYRot(yaw);
        camera.setXRot(pitch);
        if (!interpolate) {
            camera.setOldPosAndRot();
        }
    }

    // ------------------------------------------------------------------ user actions

    void look(double yawDelta, double pitchDelta) {
        float sensitivity = module.sensitivity.asFloat();
        float vertical = module.invertY.get() ? -1f : 1f;
        yaw += (float) yawDelta * 0.15f * sensitivity;
        pitch = Mth.clamp(pitch + (float) pitchDelta * 0.15f * sensitivity * vertical, -90f, 90f);
        camera.setYRot(yaw);
        camera.setXRot(pitch);
        // keep the previous rotation in sync so mouse look is not delayed by tick interpolation
        camera.yRotO = yaw;
        camera.xRotO = pitch;
    }

    void adjustSpeed(Minecraft mc, int direction) {
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

    void returnToStart(Minecraft mc) {
        pos = origin;
        yaw = originYaw;
        pitch = originPitch;
        velocity = Vec3.ZERO;
        followTarget = null;
        apply(false);
        notify(mc, "Camera returned to its starting point");
    }

    void snapToBody(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        pos = mc.player.position();
        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
        velocity = Vec3.ZERO;
        followTarget = null;
        apply(false);
        notify(mc, "Camera snapped to your body");
    }

    void toggleControl(Minecraft mc) {
        controlBody = !controlBody;
        velocity = Vec3.ZERO;
        notify(mc, controlBody ? "You control your body; the camera stays parked" : "You control the camera");
    }

    /** Moves the REAL player to the camera. Server-visible, hence disabled unless the user opted in. */
    void teleportBody(Minecraft mc) {
        if (!module.allowBodyTeleport.get()) {
            notify(mc, "Enable 'Allow body teleport' (server-visible) in the Freecam options first");
            return;
        }
        if (mc.player == null) {
            return;
        }
        mc.player.setPos(pos.x, pos.y, pos.z);
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        if (module.exitAfterTeleport.get()) {
            module.setEnabled(false);
        }
    }

    void cycleFollow(Minecraft mc) {
        List<Entity> candidates = candidates(mc);
        if (candidates.isEmpty()) {
            notify(mc, "No entity in range to follow");
            return;
        }
        int index = followTarget == null ? -1 : candidates.indexOf(followTarget);
        followTarget = candidates.get((index + 1) % candidates.size());
        notify(mc, "Following " + followTarget.getName().getString());
    }

    /** Follows the entity closest to the centre of the camera view, or stops following when none is in view. */
    void followLookedAt(Minecraft mc) {
        Vec3 eye = eyePosition();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        Vec3 look = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
        Entity best = null;
        double bestDot = Math.cos(Math.toRadians(12));
        for (Entity e : candidates(mc)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            if (to.lengthSqr() < 1.0E-6) {
                continue;
            }
            double dot = to.normalize().dot(look);
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
        notify(mc, "Following " + best.getName().getString());
    }

    private List<Entity> candidates(Minecraft mc) {
        double range = module.followRange.asDouble();
        double rangeSq = range * range;
        Vec3 eye = eyePosition();
        List<Entity> list = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LivingEntity && e.isAlive() && e.getBoundingBox().getCenter().distanceToSqr(eye) <= rangeSq) {
                list.add(e);
            }
        }
        boolean players = module.preferPlayers.get();
        list.sort((a, b) -> {
            if (players) {
                boolean pa = a instanceof net.minecraft.world.entity.player.Player;
                boolean pb = b instanceof net.minecraft.world.entity.player.Player;
                if (pa != pb) {
                    return pa ? -1 : 1;
                }
            }
            return Double.compare(a.getBoundingBox().getCenter().distanceToSqr(eye), b.getBoundingBox().getCenter().distanceToSqr(eye));
        });
        return list;
    }

    private static void notify(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(text), true);
        }
    }
}
