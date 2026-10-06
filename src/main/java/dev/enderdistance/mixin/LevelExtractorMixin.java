package dev.enderdistance.mixin;

import dev.enderdistance.EnderDistance;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * applyFrustum is the one place where vanilla rebuilds the list of sections it will
 * draw this frame, so filtering it there removes draw work and nothing else.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Shadow @Final private LevelRenderer levelRenderer;

    @Inject(method = "applyFrustum", at = @At("TAIL"))
    private void enderDistance$cullDirectionalSections(Frustum frustum, CallbackInfo ci) {
        EnderDistance.onSectionsVisible(this.levelRenderer.visibleSections());
    }
}