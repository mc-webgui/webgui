package land.webgui.browser;

/**
 * What the game says about a key, said again in the numbers the browser expects.
 *
 * <p>From 26.3 Minecraft's window is SDL's, and the key it hands a mod is an SDL scan code —
 * a USB HID usage, where {@code A} is 4. The engine underneath speaks GLFW, where {@code A} is
 * 65, because that is what every Minecraft before 26.3 spoke and what java-cef was taught. So
 * the translation happens here, at the edge of the mod: the engine keeps one contract and
 * never learns there was a second windowing library.
 *
 * <p>Two numbers come out of each key, and they are not the same thing:
 *
 * <ul>
 * <li>the <strong>GLFW key code</strong>, which decides what the page sees as
 *     {@code event.code};</li>
 * <li>the <strong>native scan code</strong>, which on Windows is the only thing java-cef
 *     reads — it maps it with {@code MapVirtualKey}, and a key event without one arrives at
 *     the page as no key at all.</li>
 * </ul>
 *
 * <p>Unknown keys answer zero rather than guessing. A key the page never hears about is a
 * missing feature; a key it hears about as the wrong one is a bug nobody can explain.
 */
final class SdlKeys {

    private SdlKeys() {
    }

    // SDL groups keys the way the USB HID table does, so whole runs are contiguous and worth
    // treating as ranges rather than listing one by one.
    private static final int A = 4, Z = 29, ONE = 30, NINE = 38, ZERO = 39;
    private static final int F1 = 58, F12 = 69, KP_1 = 89, KP_9 = 97;

    /** @return the GLFW key code, or zero if this mod has no name for it */
    static int toGlfwKey(int sdl) {
        if (sdl >= A && sdl <= Z) {
            return 'A' + (sdl - A);
        }
        if (sdl >= ONE && sdl <= NINE) {
            return '1' + (sdl - ONE);
        }
        if (sdl == ZERO) {
            return '0';
        }
        if (sdl >= F1 && sdl <= F12) {
            return 290 + (sdl - F1);
        }
        if (sdl >= KP_1 && sdl <= KP_9) {
            return 321 + (sdl - KP_1);
        }
        return switch (sdl) {
            case 40 -> 257;   // enter
            case 41 -> 256;   // escape
            case 42 -> 259;   // backspace
            case 43 -> 258;   // tab
            case 44 -> 32;    // space
            case 45 -> 45;    // minus
            case 46 -> 61;    // equal
            case 47 -> 91;    // left bracket
            case 48 -> 93;    // right bracket
            case 49 -> 92;    // backslash
            case 51 -> 59;    // semicolon
            case 52 -> 39;    // apostrophe
            case 53 -> 96;    // grave
            case 54 -> 44;    // comma
            case 55 -> 46;    // period
            case 56 -> 47;    // slash
            case 57 -> 280;   // caps lock
            case 70 -> 283;   // print screen
            case 71 -> 281;   // scroll lock
            case 72 -> 284;   // pause
            case 73 -> 260;   // insert
            case 74 -> 268;   // home
            case 75 -> 266;   // page up
            case 76 -> 261;   // delete
            case 77 -> 269;   // end
            case 78 -> 267;   // page down
            case 79 -> 262;   // right
            case 80 -> 263;   // left
            case 81 -> 264;   // down
            case 82 -> 265;   // up
            case 83 -> 282;   // num lock
            case 84 -> 331;   // keypad divide
            case 85 -> 332;   // keypad multiply
            case 86 -> 333;   // keypad subtract
            case 87 -> 334;   // keypad add
            case 88 -> 335;   // keypad enter
            case 98 -> 320;   // keypad 0
            case 99 -> 330;   // keypad decimal
            case 224 -> 341;  // left control
            case 225 -> 340;  // left shift
            case 226 -> 342;  // left alt
            case 227 -> 343;  // left super
            case 228 -> 345;  // right control
            case 229 -> 344;  // right shift
            case 230 -> 346;  // right alt
            case 231 -> 347;  // right super
            default -> 0;
        };
    }

    /**
     * @return the scan code Windows would have reported for this key, or zero
     *
     * <p>Set 1, the low byte, which is what sits in the scan-code field of a Windows key
     * message. The extended-key flag lives elsewhere in that message and java-cef does not
     * read it, so the arrows and the navigation block give their plain codes here.
     */
    static int toWindowsScanCode(int sdl) {
        if (sdl >= A && sdl <= Z) {
            return LETTERS[sdl - A];
        }
        if (sdl >= ONE && sdl <= NINE) {
            return 0x02 + (sdl - ONE);
        }
        if (sdl == ZERO) {
            return 0x0B;
        }
        if (sdl >= F1 && sdl <= 67) {
            return 0x3B + (sdl - F1);   // F1..F10 run together
        }
        return switch (sdl) {
            case 68 -> 0x57;  // F11
            case 69 -> 0x58;  // F12
            case 40 -> 0x1C;  // enter
            case 41 -> 0x01;  // escape
            case 42 -> 0x0E;  // backspace
            case 43 -> 0x0F;  // tab
            case 44 -> 0x39;  // space
            case 45 -> 0x0C;  // minus
            case 46 -> 0x0D;  // equal
            case 47 -> 0x1A;  // left bracket
            case 48 -> 0x1B;  // right bracket
            case 49 -> 0x2B;  // backslash
            case 51 -> 0x27;  // semicolon
            case 52 -> 0x28;  // apostrophe
            case 53 -> 0x29;  // grave
            case 54 -> 0x33;  // comma
            case 55 -> 0x34;  // period
            case 56 -> 0x35;  // slash
            case 57 -> 0x3A;  // caps lock
            case 73 -> 0x52;  // insert
            case 74 -> 0x47;  // home
            case 75 -> 0x49;  // page up
            case 76 -> 0x53;  // delete
            case 77 -> 0x4F;  // end
            case 78 -> 0x51;  // page down
            case 79 -> 0x4D;  // right
            case 80 -> 0x4B;  // left
            case 81 -> 0x50;  // down
            case 82 -> 0x48;  // up
            case 224 -> 0x1D; // left control
            case 225 -> 0x2A; // left shift
            case 226 -> 0x38; // left alt
            case 228 -> 0x1D; // right control
            case 229 -> 0x36; // right shift
            case 230 -> 0x38; // right alt
            default -> 0;
        };
    }

    /** Where the letters sit on a keyboard, which is not where the alphabet does. */
    private static final int[] LETTERS = {
        0x1E, 0x30, 0x2E, 0x20, 0x12, 0x21, 0x22, 0x23, 0x17, 0x24, 0x25, 0x26, 0x32,
        0x31, 0x18, 0x19, 0x10, 0x13, 0x1F, 0x14, 0x16, 0x2F, 0x11, 0x2D, 0x15, 0x2C
    };

    // SDL reports each side of a modifier separately; GLFW reports the pair as one bit.
    private static final int SDL_SHIFT = 0x0003, SDL_CONTROL = 0x00C0, SDL_ALT = 0x0300;
    private static final int SDL_SUPER = 0x0C00, SDL_NUM_LOCK = 0x1000, SDL_CAPS_LOCK = 0x2000;

    /** @return the same modifiers, in GLFW's bits */
    static int toGlfwModifiers(int sdl) {
        int glfw = 0;
        if ((sdl & SDL_SHIFT) != 0) {
            glfw |= 1;
        }
        if ((sdl & SDL_CONTROL) != 0) {
            glfw |= 2;
        }
        if ((sdl & SDL_ALT) != 0) {
            glfw |= 4;
        }
        if ((sdl & SDL_SUPER) != 0) {
            glfw |= 8;
        }
        if ((sdl & SDL_CAPS_LOCK) != 0) {
            glfw |= 16;
        }
        if ((sdl & SDL_NUM_LOCK) != 0) {
            glfw |= 32;
        }
        return glfw;
    }
}
