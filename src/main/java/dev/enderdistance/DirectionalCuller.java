package dev.enderdistance;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.BlockPos;

import java.util.Arrays;

/**
 * Turns the camera state into a per-chunk render decision.
 *
 * <p>Decisions live in a square byte grid centred on the player's chunk, so the hot
 * per-section lookup is a bounds check plus an array read. The grid is rebuilt only
 * when the player has actually moved or turned, never once per frame.
 */
final class DirectionalCuller {
    private static final double FRONT_COS = 0.5;
    private static final double SIDE_COS = -0.5;
    private static final double RESTORE_MARGIN_SQ = 1.15 * 1.15;
    private static final double REBUILD_MOVE_SQ = 0.5 * 0.5;
    private static final float REBUILD_TURN = 0.02f;
    private static final int MAX_RENDER_DISTANCE = 32;
    private static final double SAFE_RADIUS = 72.0;

    private byte[] culled = new byte[0];
    private int size;
    private int centreChunkX;
    private int centreChunkZ;

    private double builtX;
    private double builtZ;
    private float builtYaw;
    private int builtRenderDistance = -1;

    private int trackedChunks;
    private int culledChunks;
    private long lastRebuildNanos;

    void reset() {
        this.builtRenderDistance = -1;
        this.trackedChunks = 0;
        this.culledChunks = 0;
        this.size = 0;
    }

    int trackedChunks() {
        return this.trackedChunks;
    }

    int culledChunks() {
        return this.culledChunks;
    }

    long lastRebuildNanos() {
        return this.lastRebuildNanos;
    }

    /**
     * Cheap check for the once-in-a-while rebuild. Half a block of movement or about a
     * degree of heading change is far below what is visible, so the grid stays valid.
     */
    boolean needsRebuild(CameraTracker tracker, int renderDistance) {
        if (this.builtRenderDistance != renderDistance || this.size == 0) {
            return true;
        }

        double dx = tracker.cameraX() - this.builtX;
        double dz = tracker.cameraZ() - this.builtZ;
        if (dx * dx + dz * dz > REBUILD_MOVE_SQ) {
            return true;
        }

        return Math.abs(wrapPi(tracker.heading() - this.builtYaw)) > REBUILD_TURN;
    }

    void rebuild(CameraTracker tracker, EnderDistanceConfig config, int renderDistance) {
        long start = System.nanoTime();
        int radius = Math.min(Math.max(renderDistance, 2), MAX_RENDER_DISTANCE) + 1;
        this.size = radius * 2 + 1;

        if (this.culled.length != this.size * this.size) {
            this.culled = new byte[this.size * this.size];
        } else {
            Arrays.fill(this.culled, (byte) 0);
        }

        double cameraX = tracker.cameraX();
        double cameraZ = tracker.cameraZ();
        this.centreChunkX = (int) Math.floor(cameraX) >> 4;
        this.centreChunkZ = (int) Math.floor(cameraZ) >> 4;

        this.builtX = cameraX;
        this.builtZ = cameraZ;
        this.builtYaw = tracker.heading();
        this.builtRenderDistance = renderDistance;

        this.trackedChunks = 0;
        this.culledChunks = 0;

        double strength = config.strength * (1.0f - tracker.turnStress());
        if (strength <= 0.0) {
            this.lastRebuildNanos = System.nanoTime() - start;
            return;
        }

        double reach = renderDistance * 16.0;
        double front = reach * lerp(1.0, config.forwardDistance, strength);
        double side = reach * lerp(1.0, config.sideDistance, strength);
        double back = reach * lerp(1.0, config.backDistance, strength);
        double safeRadius = Math.min(reach * 0.35, SAFE_RADIUS);
        double safeRadiusSq = safeRadius * safeRadius;

        double directionX = tracker.directionX();
        double directionZ = tracker.directionZ();

        for (int gridZ = 0; gridZ < this.size; gridZ++) {
            double dz = (this.centreChunkZ + gridZ - radius) * 16.0 + 8.0 - cameraZ;
            int row = gridZ * this.size;

            for (int gridX = 0; gridX < this.size; gridX++) {
                double dx = (this.centreChunkX + gridX - radius) * 16.0 + 8.0 - cameraX;
                double distanceSq = dx * dx + dz * dz;

                if (distanceSq <= safeRadiusSq) {
                    continue;
                }

                this.trackedChunks++;

                double facing = (dx * directionX + dz * directionZ) / Math.sqrt(distanceSq);
                double reachLimit = facing >= FRONT_COS ? front : facing >= SIDE_COS ? side : back;
                double limitSq = reachLimit * reachLimit;

                // A chunk that is already skipped needs to come well back inside the
                // limit before it is drawn again, which is what keeps it from flickering.
                double thresholdSq = this.culled[row + gridX] != 0 ? limitSq * RESTORE_MARGIN_SQ : limitSq;

                if (distanceSq > thresholdSq) {
                    this.culled[row + gridX] = 1;
                    this.culledChunks++;
                }
            }
        }

        this.lastRebuildNanos = System.nanoTime() - start;
    }

    /**
     * Drops the sections of skipped chunks from the draw list. Nothing outside this
     * list is touched: chunks stay loaded, ticked and fully simulated.
     */
    int filter(ObjectArrayList<RenderSection> sections) {
        int count = sections.size();
        int kept = 0;

        for (int i = 0; i < count; i++) {
            RenderSection section = sections.get(i);
            BlockPos origin = section.getRenderOrigin();
            int dx = (origin.getX() >> 4) - this.centreChunkX;
            int dz = (origin.getZ() >> 4) - this.centreChunkZ;

            // Unsigned comparisons reject chunks outside the grid in a single branch;
            // anything we have no opinion about is rendered.
            if (Integer.compareUnsigned(dx, this.size) < 0 && Integer.compareUnsigned(dz, this.size) < 0
                    && this.culled[dz * this.size + dx] != 0) {
                continue;
            }

            if (kept != i) {
                sections.set(kept, section);
            }
            kept++;
        }

        if (kept < count) {
            sections.removeElements(kept, count);
        }
        return count - kept;
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static float wrapPi(float angle) {
        angle %= (float) (Math.PI * 2.0);
        if (angle > Math.PI) {
            angle -= (float) (Math.PI * 2.0);
        } else if (angle < -Math.PI) {
            angle += (float) (Math.PI * 2.0);
        }
        return angle;
    }
}