package com.opdent.mmdskin.sync.resource;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure-Java upload source collector. ZIP names are decoded deterministically and
 * every source is bounded before its bytes enter the network protocol.
 */
public final class SafeUploadCollector {
    public static final int MAX_ENTRIES = 4_096;
    public static final long MAX_ENTRY_BYTES = 512L * 1024L * 1024L;
    public static final long MAX_EXPANDED_BYTES = 512L * 1024L * 1024L;
    public static final int MAX_RELATIVE_PATH_BYTES = 2_048;
    public static final int MAX_COMPRESSION_RATIO = 200;
    private static final long COMPRESSION_RATIO_GRACE_BYTES = 1024L * 1024L;
    private static final Charset CP437 = Charset.forName("CP437");
    private static final Charset GBK = Charset.forName("GBK");

    private SafeUploadCollector() {
    }

    public record Entry(String relativePath, byte[] payload) {
    }

    public record ArchiveContents(String commonRoot, List<Entry> entries) {
    }

    public static ArchiveContents collectZip(Path archive) throws IOException {
        requireRegularSource(archive, "ZIP");
        List<Entry> entries = new ArrayList<>();
        Set<String> normalizedKeys = new HashSet<>();
        long expandedBytes = 0L;
        int visitedEntries = 0;

        // CP437 is the ZIP specification fallback. EFS entries remain UTF-8 and
        // Info-ZIP 0x7075 Unicode Path fields override it inside Commons Compress.
        try (ZipFile zip = ZipFile.builder()
                .setPath(archive)
                .setCharset(CP437)
                .setUseUnicodeExtraFields(true)
                .get()) {
            Enumeration<ZipArchiveEntry> enumeration = zip.getEntriesInPhysicalOrder();
            while (enumeration.hasMoreElements()) {
                ZipArchiveEntry zipEntry = enumeration.nextElement();
                if (++visitedEntries > MAX_ENTRIES) {
                    throw new IOException("ZIP 条目数超过安全上限 " + MAX_ENTRIES);
                }
                if (zipEntry.getGeneralPurposeBit().usesEncryption()) {
                    throw new IOException("不支持加密 ZIP 条目: " + printableName(zipEntry));
                }
                if (zipEntry.isUnixSymlink()) {
                    throw new IOException("ZIP 不允许符号链接条目: " + printableName(zipEntry));
                }
                if (!zip.canReadEntryData(zipEntry)) {
                    throw new IOException("ZIP 条目使用了不支持的压缩/加密方式: " + printableName(zipEntry));
                }

                String decodedName = decodeEntryName(zipEntry);
                String normalized = normalizeRelativePath(decodedName);
                if (zipEntry.isDirectory() || normalized.isEmpty() || isMacMetadata(normalized)) {
                    continue;
                }
                requireUniquePath(normalizedKeys, normalized);

                long declaredSize = zipEntry.getSize();
                long compressedSize = zipEntry.getCompressedSize();
                validateZipEntrySizes(normalized, declaredSize, compressedSize);
                expandedBytes = checkedExpandedTotal(expandedBytes, declaredSize);

                byte[] payload;
                try (InputStream input = zip.getInputStream(zipEntry)) {
                    payload = readBounded(input, declaredSize, normalized);
                }
                if (payload.length != declaredSize) {
                    throw new IOException("ZIP 条目声明大小与实际内容不一致: " + normalized);
                }
                entries.add(new Entry(normalized, payload));
            }
        } catch (CharacterCodingException e) {
            throw new IOException("ZIP 条目名称编码无效（支持 UTF-8/Unicode Path/GBK/CP437）", e);
        } catch (IllegalArgumentException e) {
            throw new IOException("ZIP 目录或 local header 无效: " + e.getMessage(), e);
        }

        return new ArchiveContents(detectCommonTopDirectory(entries), List.copyOf(entries));
    }

    public static List<Entry> collectDirectory(Path directory) throws IOException {
        if (directory == null || Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("所选目录不存在或是符号链接");
        }

        List<Entry> entries = new ArrayList<>();
        Set<String> normalizedKeys = new HashSet<>();
        long expandedBytes = 0L;
        int visited = 0;
        try (var stream = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) stream.sorted()::iterator) {
                if (path.equals(directory)) {
                    continue;
                }
                if (++visited > MAX_ENTRIES) {
                    throw new IOException("目录条目数超过安全上限 " + MAX_ENTRIES);
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("上传目录不允许符号链接: " + directory.relativize(path));
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                String normalized = normalizeRelativePath(directory.relativize(path).toString());
                if (normalized.isEmpty() || isMacMetadata(normalized)) {
                    continue;
                }
                requireUniquePath(normalizedKeys, normalized);
                long size = Files.size(path);
                if (size < 0L || size > MAX_ENTRY_BYTES) {
                    throw new IOException("文件大小超过上传上限: " + normalized);
                }
                expandedBytes = checkedExpandedTotal(expandedBytes, size);
                byte[] payload = Files.readAllBytes(path);
                if (payload.length != size) {
                    throw new IOException("文件读取大小发生变化: " + normalized);
                }
                entries.add(new Entry(normalized, payload));
            }
        }
        return List.copyOf(entries);
    }

    public static Entry collectSingleFile(Path file) throws IOException {
        requireRegularSource(file, "文件");
        String normalized = normalizeRelativePath(file.getFileName().toString());
        long size = Files.size(file);
        if (size > MAX_ENTRY_BYTES) {
            throw new IOException("文件大小超过上传上限: " + normalized);
        }
        return new Entry(normalized, Files.readAllBytes(file));
    }

    static String decodeEntryName(ZipArchiveEntry entry) throws CharacterCodingException {
        if (entry.getNameSource() == ZipArchiveEntry.NameSource.NAME_WITH_EFS_FLAG
                || entry.getNameSource() == ZipArchiveEntry.NameSource.UNICODE_EXTRA_FIELD) {
            // Commons Compress strictly applies UTF-8 for EFS and validates the
            // CRC-protected Info-ZIP Unicode Path field before selecting it.
            return entry.getName();
        }
        byte[] raw = entry.getRawName();
        String utf8 = decodeStrict(raw, StandardCharsets.UTF_8);
        if (utf8 != null) {
            return utf8;
        }
        String gbk = decodeStrict(raw, GBK);
        if (gbk != null && containsCjk(gbk)) {
            return gbk;
        }
        return decodeStrictRequired(raw, CP437);
    }

    public static String normalizeRelativePath(String rawPath) throws IOException {
        if (rawPath == null || rawPath.isBlank()) {
            return "";
        }
        if (rawPath.indexOf('\0') >= 0) {
            throw new IOException("路径不允许包含 NUL 字符");
        }
        String candidate = rawPath.replace('\\', '/');
        if (candidate.startsWith("/") || candidate.startsWith("//")
                || candidate.matches("(?i)^[a-z]:.*")) {
            throw new IOException("不允许绝对路径或盘符路径: " + rawPath);
        }
        String[] segments = candidate.split("/", -1);
        List<String> clean = new ArrayList<>(segments.length);
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.equals(".") || segment.equals("..")) {
                throw new IOException("不允许 ZIP Slip 路径段: " + rawPath);
            }
            if (segment.indexOf(':') >= 0) {
                throw new IOException("路径段不允许冒号: " + rawPath);
            }
            clean.add(segment);
        }
        String normalized = String.join("/", clean);
        if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_RELATIVE_PATH_BYTES) {
            throw new IOException("相对路径超过 " + MAX_RELATIVE_PATH_BYTES + " UTF-8 字节: " + rawPath);
        }
        return normalized;
    }

    private static void requireRegularSource(Path path, String kind) throws IOException {
        if (path == null || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("所选" + kind + "不存在或是符号链接");
        }
    }

    private static byte[] readBounded(InputStream input, long declaredSize, String name) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(declaredSize, 1024L * 1024L));
        byte[] buffer = new byte[64 * 1024];
        long count = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            count += read;
            if (count > declaredSize || count > MAX_ENTRY_BYTES) {
                throw new IOException("ZIP 条目实际展开大小超过声明或安全上限: " + name);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    static void validateZipEntrySizes(String normalized, long declaredSize,
                                      long compressedSize) throws IOException {
        if (declaredSize < 0L || declaredSize > MAX_ENTRY_BYTES) {
            throw new IOException("ZIP 条目展开大小非法或超过上限: " + normalized);
        }
        if (compressedSize < 0L) {
            throw new IOException("ZIP 条目缺少压缩大小: " + normalized);
        }
        if (declaredSize > COMPRESSION_RATIO_GRACE_BYTES
                && declaredSize > saturatedAdd(saturatedMultiply(compressedSize, MAX_COMPRESSION_RATIO),
                COMPRESSION_RATIO_GRACE_BYTES)) {
            throw new IOException("ZIP 条目压缩比超过安全上限: " + normalized);
        }
    }

    static long checkedExpandedTotal(long current, long addition) throws IOException {
        if (addition < 0L || current > MAX_EXPANDED_BYTES - addition) {
            throw new IOException("上传内容总展开大小超过安全上限 " + MAX_EXPANDED_BYTES + " 字节");
        }
        return current + addition;
    }

    private static void requireUniquePath(Set<String> keys, String normalized) throws IOException {
        String key = normalized.toLowerCase(Locale.ROOT);
        if (!keys.add(key)) {
            throw new IOException("归一化后出现重复路径: " + normalized);
        }
    }

    private static boolean isMacMetadata(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.startsWith("__macosx/") || lower.equals("__macosx")
                || lower.endsWith("/.ds_store") || lower.equals(".ds_store");
    }

    private static String detectCommonTopDirectory(List<Entry> entries) {
        String common = null;
        for (Entry entry : entries) {
            int slash = entry.relativePath().indexOf('/');
            if (slash <= 0) {
                return "";
            }
            String top = entry.relativePath().substring(0, slash);
            if (common == null) {
                common = top;
            } else if (!common.equals(top)) {
                return "";
            }
        }
        return common == null ? "" : common;
    }

    private static String decodeStrict(byte[] bytes, Charset charset) {
        try {
            return decodeStrictRequired(bytes, charset);
        } catch (CharacterCodingException ignored) {
            return null;
        }
    }

    private static String decodeStrictRequired(byte[] bytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes == null ? new byte[0] : bytes))
                .toString();
    }

    private static boolean containsCjk(String value) {
        return value.codePoints().anyMatch(codePoint ->
                (codePoint >= 0x3400 && codePoint <= 0x9FFF)
                        || (codePoint >= 0xF900 && codePoint <= 0xFAFF));
    }

    private static long saturatedMultiply(long value, long multiplier) {
        if (value <= 0L || multiplier <= 0L) {
            return 0L;
        }
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static String printableName(ZipArchiveEntry entry) {
        String name = entry == null ? "" : entry.getName();
        return name == null || name.isBlank() ? "<unnamed>" : name;
    }
}
