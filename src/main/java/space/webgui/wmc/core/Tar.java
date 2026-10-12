package space.webgui.wmc.core;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads a tar archive, far enough for a Chromium bundle and no further.
 *
 * <p>This exists so that unpacking the runtime costs no third-party library. What a bundle
 * actually contains is narrow — regular files, directories and symbolic links, names that
 * outgrow the header, and mode bits — and all of that is below.
 *
 * <p>Two of those are not optional. <strong>Mode bits</strong> decide whether the helper
 * executables in a bundle can be started at all, and <strong>symbolic links</strong> are what
 * a macOS framework is mostly made of. An extractor that drops either produces an install that
 * looks complete and does not work, which is why {@code java.util.zip} cannot do this job.
 *
 * <p>Reading this as a stream yields the bytes of the current entry only, and stops at its
 * end, so an entry can be handed to {@code Files.copy} without it running on into the next.
 */
final class Tar extends InputStream {

    private static final int BLOCK = 512;

    /** What the header says about one entry. */
    static final class Entry {

        private final String name;
        private final long size;
        private final int mode;
        private final char type;
        private final String linkName;

        private Entry(String name, long size, int mode, char type, String linkName) {
            this.name = name;
            this.size = size;
            this.mode = mode;
            this.type = type;
            this.linkName = linkName;
        }

        String name() {
            return name;
        }

        long size() {
            return size;
        }

        int mode() {
            return mode;
        }

        String linkName() {
            return linkName;
        }

        boolean isDirectory() {
            // A name ending in a slash is how an archive written before the type flag existed
            // says "directory", and some writers still do both.
            return type == '5' || name.endsWith("/");
        }

        boolean isSymbolicLink() {
            return type == '2';
        }

        /** Whether the entry carries bytes worth writing: not a link, a directory or metadata. */
        boolean isFile() {
            return !isDirectory() && !isSymbolicLink() && (type == '0' || type == '\0');
        }
    }

    private final InputStream in;
    private long entryRemaining;
    private long padding;

    Tar(InputStream in) {
        this.in = in;
    }

    /**
     * Moves to the next entry, skipping whatever is left of the current one.
     *
     * @return the entry, or {@code null} at the end of the archive
     */
    Entry next() throws IOException {
        skipFully(entryRemaining + padding);
        entryRemaining = 0;
        padding = 0;

        // Carried across the metadata entries below, which describe the entry after them
        // rather than being one.
        String longName = null;
        String longLinkName = null;

        while (true) {
            byte[] header = readBlock();
            if (header == null || isZeros(header)) {
                // One zero block ends the archive; the format writes two, but a reader that
                // insists on the second rejects archives that are otherwise perfectly good.
                return null;
            }

            String name = longName != null ? longName : readName(header);
            long size = readOctal(header, 124, 12);
            int mode = (int) readOctal(header, 100, 8);
            char type = (char) (header[156] & 0xFF);
            String linkName = longLinkName != null ? longLinkName
                    : readString(header, 157, 100);

            if (type == 'L' || type == 'K') {
                // GNU's answer to a name longer than the header: the name is the content of
                // an entry of its own, and the real entry follows.
                String value = readFully(size);
                skipFully(paddingFor(size));
                if (type == 'L') {
                    longName = value;
                } else {
                    longLinkName = value;
                }
                continue;
            }

            if (type == 'x' || type == 'g') {
                // PAX does the same thing with typed records. Only the two that can change
                // where bytes land are read; the rest describe times and owners we do not set.
                String records = readFully(size);
                skipFully(paddingFor(size));
                String path = paxValue(records, "path");
                String link = paxValue(records, "linkpath");
                if (path != null) {
                    longName = path;
                }
                if (link != null) {
                    longLinkName = link;
                }
                continue;
            }

            entryRemaining = size;
            padding = paddingFor(size);
            return new Entry(name, size, mode, type, linkName);
        }
    }

    @Override
    public int read() throws IOException {
        if (entryRemaining <= 0) {
            return -1;
        }
        int value = in.read();
        if (value < 0) {
            throw new EOFException("the archive ends in the middle of an entry");
        }
        entryRemaining--;
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (entryRemaining <= 0) {
            return -1;
        }
        int wanted = (int) Math.min(length, entryRemaining);
        int read = in.read(buffer, offset, wanted);
        if (read < 0) {
            throw new EOFException("the archive ends in the middle of an entry");
        }
        entryRemaining -= read;
        return read;
    }

    @Override
    public int available() {
        return (int) Math.min(Integer.MAX_VALUE, entryRemaining);
    }

    /** Closes the archive. The entry stream is not closed by whoever reads an entry. */
    @Override
    public void close() throws IOException {
        in.close();
    }

    private byte[] readBlock() throws IOException {
        byte[] block = new byte[BLOCK];
        int read = in.readNBytes(block, 0, BLOCK);
        if (read == 0) {
            return null;
        }
        if (read < BLOCK) {
            throw new InstallException("the archive ends in the middle of a header");
        }
        return block;
    }

    private String readFully(long count) throws IOException {
        if (count < 0 || count > 1 << 20) {
            throw new InstallException("a name in the archive is implausibly long: " + count);
        }
        byte[] bytes = new byte[(int) count];
        if (in.readNBytes(bytes, 0, bytes.length) != bytes.length) {
            throw new InstallException("the archive ends in the middle of an entry name");
        }
        return trimNul(new String(bytes, StandardCharsets.UTF_8));
    }

    private void skipFully(long count) throws IOException {
        long left = count;
        while (left > 0) {
            long skipped = in.skip(left);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    throw new EOFException("the archive ends before an entry does");
                }
                skipped = 1;
            }
            left -= skipped;
        }
    }

    private static long paddingFor(long size) {
        long overflow = size % BLOCK;
        return overflow == 0 ? 0 : BLOCK - overflow;
    }

    private static boolean isZeros(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * The entry name, which ustar may have split in two.
     *
     * <p>The prefix field holds the leading directories when the whole path does not fit in
     * the hundred bytes the original format allowed, and the two are joined with a slash.
     */
    private static String readName(byte[] header) {
        String name = readString(header, 0, 100);
        if (isUstar(header)) {
            String prefix = readString(header, 345, 155);
            if (!prefix.isEmpty()) {
                return prefix + "/" + name;
            }
        }
        return name;
    }

    private static boolean isUstar(byte[] header) {
        return header[257] == 'u' && header[258] == 's' && header[259] == 't'
                && header[260] == 'a' && header[261] == 'r';
    }

    private static String readString(byte[] header, int offset, int length) {
        int end = offset;
        int limit = offset + length;
        while (end < limit && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }

    /**
     * A numeric header field.
     *
     * <p>Octal text, except where a value does not fit, where GNU writes it as base 256 with
     * the top bit of the first byte set. A Chromium bundle has no file big enough to need
     * that, but an archive that uses it would otherwise be read as a wildly wrong size rather
     * than refused.
     */
    private static long readOctal(byte[] header, int offset, int length) {
        if ((header[offset] & 0x80) != 0) {
            long value = header[offset] & 0x7F;
            for (int i = offset + 1; i < offset + length; i++) {
                value = (value << 8) | (header[i] & 0xFF);
            }
            return value;
        }
        long value = 0;
        for (int i = offset; i < offset + length; i++) {
            int digit = header[i] & 0xFF;
            if (digit == 0 || digit == ' ') {
                // Leading and trailing padding; a field that is all padding is zero.
                if (value != 0) {
                    break;
                }
                continue;
            }
            if (digit < '0' || digit > '7') {
                throw new InstallException("a header field in the archive is not a number");
            }
            value = (value << 3) + (digit - '0');
        }
        return value;
    }

    /**
     * One PAX record's value.
     *
     * <p>Records are {@code <length> <key>=<value>\n}, where the length counts its own digits
     * and the newline, so they are walked rather than split: a value may contain both a space
     * and a newline.
     */
    private static String paxValue(String records, String key) {
        int at = 0;
        while (at < records.length()) {
            int space = records.indexOf(' ', at);
            if (space < 0) {
                return null;
            }
            int length;
            try {
                length = Integer.parseInt(records.substring(at, space));
            } catch (NumberFormatException e) {
                return null;
            }
            if (length <= 0 || at + length > records.length()) {
                return null;
            }
            String record = records.substring(space + 1, at + length);
            int equals = record.indexOf('=');
            if (equals > 0 && record.substring(0, equals).equals(key)) {
                return trimNul(record.substring(equals + 1).stripTrailing());
            }
            at += length;
        }
        return null;
    }

    private static String trimNul(String value) {
        int end = value.indexOf('\0');
        return end < 0 ? value : value.substring(0, end);
    }
}
