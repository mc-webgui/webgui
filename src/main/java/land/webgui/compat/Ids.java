package land.webgui.compat;

import land.webgui.WebGUIMod;

/**
 * Making an identifier, which Minecraft has renamed twice.
 *
 * <p>Three shapes across the versions this mod builds for: a constructor up to 1.20.x, a
 * factory method after it, and the class itself renamed from {@code ResourceLocation} to
 * {@code Identifier} in 1.21.5. This mod names identifiers in a dozen places and none of them
 * cares which shape is current.
 *
 * <p>Unlike the rest of {@link Mc}, the branch cannot be hidden entirely: the type that comes
 * back is part of the difference, so the signature is branched too. One place rather than a
 * dozen is still the whole point.
 */
public final class Ids {

    private Ids() {
    }

    /** An identifier in this mod's own namespace. */
    //? if >=1.21.5 {
    public static net.minecraft.resources.Identifier of(String path) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, path);
    }
    //? } else {
    /*//? if >=1.21 {
    public static net.minecraft.resources.ResourceLocation of(String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, path);
    }
    //? } else {
    public static net.minecraft.resources.ResourceLocation of(String path) {
        return new net.minecraft.resources.ResourceLocation(WebGUIMod.MOD_ID, path);
    }
    //? }
    *///? }

    /** An identifier in someone else's namespace — the game's own, usually. */
    //? if >=1.21.5 {
    public static net.minecraft.resources.Identifier of(String namespace, String path) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(namespace, path);
    }
    //? } else {
    /*//? if >=1.21 {
    public static net.minecraft.resources.ResourceLocation of(String namespace, String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
    //? } else {
    public static net.minecraft.resources.ResourceLocation of(String namespace, String path) {
        return new net.minecraft.resources.ResourceLocation(namespace, path);
    }
    //? }
    *///? }
}
