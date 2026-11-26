package dev.dragonofshuu.fps.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    // getBlockJumpFactor
    // @Shadow 
    // protected float getBlockJumpFactor() {
    //     return 0;
    // }

    // @Inject(method = "getJumpPower", at = @At("RETURN"), cancellable = true)
    // private void onJump(CallbackInfoReturnable<Float> cir) {
    //     // var newThis = (LivingEntity) (Object) this;
    //     // if (newThis instanceof Player) {
    //         cir.setReturnValue(3F);
    //     // }
    // }
}
