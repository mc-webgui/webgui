package land.webgui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mixin(targets = "com.cinemamod.mcef.CefUtil", remap = false)
public class CefUtilMixin {

    @ModifyArg(
            method = "init",
            at = @At(value = "INVOKE",
                     target = "Lorg/cef/CefApp;startup([Ljava/lang/String;)Z",
                     remap = false),
            index = 0,
            remap = false
    )
    private static String[] webgui$injectGpuFlags(String[] original) {
        List<String> args = new ArrayList<>(Arrays.asList(original));
        // GPU rasterization: CEF renders canvas/CSS/text on GPU even in CPU-OSR mode
        args.add("--enable-gpu-rasterization");
        // Out-of-process rasterization: separate GPU process handles raster work
        args.add("--enable-oop-rasterization");
        // Prefer native desktop OpenGL over ANGLE (better performance on desktop)
        args.add("--use-gl=desktop");
        // Remove GPU vsync so CEF isn't throttled by display refresh during off-screen render
        args.add("--disable-gpu-vsync");
        return args.toArray(new String[0]);
    }
}
