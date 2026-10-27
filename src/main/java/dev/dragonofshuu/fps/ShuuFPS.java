package dev.dragonofshuu.fps;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import dev.dragonofshuu.fps.shared.SharedUiNetworking;
import dev.dragonofshuu.fps.shared.SharedArenaSafetyNetworking;
import net.minecraftforge.fml.common.Mod;

@Mod(ShuuFPS.MOD_ID)
public class ShuuFPS {
    public static final String MOD_ID = "shuusfps";
    public static Logger LOGGER = LogUtils.getLogger();

    public ShuuFPS() {
        SharedUiNetworking.register();
        SharedArenaSafetyNetworking.register();
    }
}
