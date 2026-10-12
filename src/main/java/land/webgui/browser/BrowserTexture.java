package land.webgui.browser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import space.webgui.wmc.api.Damage;
import space.webgui.wmc.api.Frame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
//? if >=1.21.5 {
import net.minecraft.resources.Identifier;
//? } else {
/*import net.minecraft.resources.ResourceLocation;
*///? }
//? if >=1.21.5 {
import com.mojang.blaze3d.systems.RenderSystem;
//? } else {
/*import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
*/
//? }

/**
 * A browser frame as something Minecraft can draw.
 *
 * <p>This is the one part of Rinku that could not simply be moved across, and the reason is
 * worth stating plainly: <strong>how a picture reaches the GPU is different on almost every
 * Minecraft version.</strong> Rinku was absorbing that difference on this mod's behalf, for
 * every build target. Owning the browser means owning this file, so here it is, in one class
 * with the version branches kept around the two methods that actually touch the card.
 *
 * <p>Three shapes, across seven targets:
 *
 * <ul>
 * <li><strong>Up to 1.21.4</strong> a texture is OpenGL's, allocated with
 *     {@code TextureUtil.prepareImage} and written with {@code glTexSubImage2D}. Chromium's
 *     BGRA goes straight in, because {@code GL_BGRA} is a format OpenGL accepts and exchanges
 *     for free.</li>
 * <li><strong>From 1.21.5</strong> it is a {@code GpuTexture} written through a command
 *     encoder, which takes RGBA — so the exchange is ours to do.</li>
 * <li><strong>From 26.2</strong> the same, except the pixel format moved out of the call and
 *     {@code TextureFormat} became {@code GpuFormat}.</li>
 * </ul>
 *
 * <p>Three things in it earn their keep, and all three are about not paying for pixels twice.
 *
 * <p><strong>Nothing is uploaded twice.</strong> A page that is not animating paints rarely,
 * and sending a megabyte to the card every frame regardless is the easiest way to make an
 * embedded browser look expensive. The frame carries a revision; this keeps the last one it
 * sent and does nothing while it has not moved.
 *
 * <p><strong>Nor is more of it uploaded than changed.</strong> A caret blinking in a text box
 * is a few hundred pixels while the page around it is a megabyte. The frame is asked what has
 * moved since the revision last sent — which is not always the one before it, because a page
 * may paint several times between two frames of the game — and only that rectangle is
 * fetched, converted and sent.
 *
 * <p><strong>There is no second copy of the picture.</strong> Minecraft's own dynamic texture
 * keeps the whole image in memory beside the one on the GPU, which for a browser means
 * several megabytes holding pixels that are already on the card.
 */
public final class BrowserTexture extends AbstractTexture {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    //? if >=1.21.5 {
    /** What a dynamic texture asks for: one that can be drawn and written into. */
    private static final int USAGE = 5;
    //? }

    //? if >=1.21.5 {
    private final Identifier id;
    //? } else {
    /*private final ResourceLocation id;
    *///? }

    private final String label;

    private ByteBuffer scratch;
    private int width;
    private int height;
    private long uploaded = -1;

    public BrowserTexture() {
        int serial = COUNTER.incrementAndGet();
        this.label = "webgui/browser/" + serial;
        //? if >=1.21.5 {
        this.id = Identifier.fromNamespaceAndPath("webgui", "browser/" + serial);
        //? } else {
        /*//? if >=1.21 {
        this.id = ResourceLocation.fromNamespaceAndPath("webgui", "browser/" + serial);
        //? } else {
        this.id = new ResourceLocation("webgui", "browser/" + serial);
        //? }
        *///? }
    }

    /**
     * Puts what has changed on the GPU, and answers where the picture is.
     *
     * <p>Must be called on the render thread, like everything else that touches a texture.
     *
     * @return the texture, or {@code null} if the page has painted nothing yet
     */
    //? if >=1.21.5 {
    public Identifier upload(Frame frame) {
    //? } else {
    /*public ResourceLocation upload(Frame frame) {
    *///? }
        if (frame == null || frame.isEmpty()) {
            return null;
        }
        if (width != frame.width() || height != frame.height()) {
            resize(frame.width(), frame.height());
        }
        long revision = frame.revision();
        if (revision == uploaded) {
            return id;
        }
        Damage damage = frame.damageSince(uploaded);
        if (damage.isEmpty()) {
            // Painted, and none of it visible. The revision still moves, so that the next
            // call asks about the right span rather than about this paint again.
            uploaded = revision;
            return id;
        }

        int needed = damage.width() * damage.height() * 4;
        if (scratch == null || scratch.capacity() < needed) {
            scratch = ByteBuffer.allocateDirect(needed).order(ByteOrder.nativeOrder());
        }
        scratch.clear().limit(needed);
        frame.copyTo(scratch, damage);
        scratch.flip();

        //? if >=1.21.5 {
        // The command encoder takes RGBA and Chromium paints BGRA, so the exchange is ours.
        swapChannels(scratch, damage.width() * damage.height());
        //? if >26.1.2 {
        /*RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                this.texture, scratch, 0, 0,
                damage.x(), damage.y(), damage.width(), damage.height());
        *///? } else {
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                this.texture, scratch, com.mojang.blaze3d.platform.NativeImage.Format.RGBA, 0, 0,
                damage.x(), damage.y(), damage.width(), damage.height());
        //? }
        //? } else {
        /*// No exchange here: GL_BGRA is a format OpenGL accepts, and it does it itself.
        GlStateManager._bindTexture(getId());
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL12.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0,
                damage.x(), damage.y(), damage.width(), damage.height(),
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, scratch);
        */
        //? }

        uploaded = revision;
        return id;
    }

    /** Whether there is a picture to draw. */
    public boolean isReady() {
        return uploaded >= 0;
    }

    /** The texture identifier, valid only after a successful {@link #upload}. */
    //? if >=1.21.5 {
    public Identifier id() {
    //? } else {
    /*public ResourceLocation id() {
    *///? }
        return id;
    }

    private void resize(int newWidth, int newHeight) {
        releaseGpu();
        width = newWidth;
        height = newHeight;
        scratch = null;
        uploaded = -1;

        //? if >=1.21.5 {
        var device = RenderSystem.getDevice();
        //? if >26.2 {
        /*// 26.3 moved the whole GPU API out of blaze3d into renderpearl. Same calls, new home.
        this.texture = device.createTexture(label, USAGE,
                com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, width, height, 1, 1);
        *///? } else if >26.1.2 {
        /*this.texture = device.createTexture(label, USAGE,
                com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, width, height, 1, 1);
        *///? } else {
        this.texture = device.createTexture(label, USAGE,
                com.mojang.blaze3d.textures.TextureFormat.RGBA8, width, height, 1, 1);
        //? }
        this.textureView = device.createTextureView(this.texture);
        Minecraft.getInstance().getTextureManager().register(id, this);
        //? } else {
        /*// Nearest, not linear: a page is text and sharp edges, and a browser that is
        // slightly out of focus is the first thing anybody notices.
        TextureUtil.prepareImage(getId(), width, height);
        setFilter(false, false);
        Minecraft.getInstance().getTextureManager().register(id, this);
        */
        //? }
    }

    //? if >=1.21.5 {
    /**
     * BGRA to RGBA in place, one pixel per iteration.
     *
     * <p>Read little-endian, a BGRA pixel is {@code 0xAARRGGBB} and an RGBA one is
     * {@code 0xAABBGGRR}: the same bits with red and blue exchanged. Alpha and green stay
     * where they are, which is why this is three operations rather than four byte moves.
     */
    private static void swapChannels(ByteBuffer pixels, int count) {
        IntBuffer view = pixels.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer();
        int total = Math.min(count, view.remaining());
        for (int i = 0; i < total; i++) {
            int bgra = view.get(i);
            view.put(i, (bgra & 0xFF00FF00) | ((bgra >>> 16) & 0x000000FF) | ((bgra & 0x000000FF) << 16));
        }
    }
    //? }

    //? if <1.21.5 {
    /*// Nothing to load: the pixels come from a browser, not from a resource pack. Declared
    // abstract by the texture this extends, which until 1.21.5 assumed every texture was a
    // file somebody shipped.
    @Override
    public void load(net.minecraft.server.packs.resources.ResourceManager manager) {
    }
    */
    //? }

    /**
     * Takes the texture out of the game's register, which also closes it.
     *
     * <p>Only from outside — never from {@link #close()}. The game closes its texture manager
     * by walking its own map and closing each texture in turn, so a {@code close} that reached
     * back and removed an entry modified that map mid-walk: the game died on
     * {@code ConcurrentModificationException} every time it was shut down with a page open.
     */
    void unregister() {
        //? if >=1.21.5 {
        if (this.texture == null && width == 0) {
            return;
        }
        //? } else {
        /*if (width == 0) {
            return;
        }
        *///? }
        Minecraft.getInstance().getTextureManager().release(id);
    }

    @Override
    public void close() {
        releaseGpu();
        scratch = null;
        uploaded = -1;
        width = 0;
        height = 0;
    }

    /** Frees what is on the card, and touches nothing the game is iterating. */
    private void releaseGpu() {
        //? if >=1.21.5 {
        super.close();
        //? } else {
        /*releaseId();
        *///? }
    }
}
