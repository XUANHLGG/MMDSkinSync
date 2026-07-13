package com.opdent.mmdskin.sync.resource;

import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SafeUploadCollectorTest {
    @TempDir
    Path tempDir;

    @Test
    void readsUtf8GbkCp437AndUnicodePathNamesDeterministically() throws Exception {
        assertSinglePath(writeZip("utf8.zip", StandardCharsets.UTF_8, true,
                ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER,
                List.of(spec("模型/材质.png", "utf8"))), "模型/材质.png");

        assertSinglePath(writeZip("gbk.zip", Charset.forName("GBK"), false,
                ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER,
                List.of(spec("模型/贴图.png", "gbk"))), "模型/贴图.png");

        assertSinglePath(writeZip("cp437.zip", Charset.forName("CP437"), false,
                ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER,
                List.of(spec("café/model.pmx", "cp437"))), "café/model.pmx");

        assertSinglePath(writeZip("unicode-extra.zip", Charset.forName("GBK"), false,
                ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS,
                List.of(spec("原始模型/动作.vmd", "unicode-extra"))), "原始模型/动作.vmd");
    }

    @Test
    void ignoresDirectoriesAndMacMetadataAndDetectsCommonRoot() throws Exception {
        Path zip = writeZip("metadata.zip", StandardCharsets.UTF_8, true,
                ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER,
                List.of(
                        directory("Root/"),
                        spec("__MACOSX/._model.pmx", "ignored"),
                        spec("Root/.DS_Store", "ignored"),
                        spec("Root/nested/model.pmx", "model"),
                        spec("Root/texture/skin.png", "texture")));

        SafeUploadCollector.ArchiveContents archive = SafeUploadCollector.collectZip(zip);
        assertEquals("Root", archive.commonRoot());
        assertEquals(List.of("Root/nested/model.pmx", "Root/texture/skin.png"),
                archive.entries().stream().map(SafeUploadCollector.Entry::relativePath).toList());
    }

    @Test
    void rejectsMalformedLocalHeaderAndUnsafePaths() throws Exception {
        Path valid = writeZip("malformed.zip", StandardCharsets.UTF_8, true,
                ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER,
                List.of(spec("root/file.pmx", "payload")));
        byte[] damaged = Files.readAllBytes(valid);
        assertTrue(damaged.length > 30);
        damaged[26] = (byte) 0xFF;
        damaged[27] = 0x7F;
        Files.write(valid, damaged);
        assertThrows(IOException.class, () -> SafeUploadCollector.collectZip(valid));

        for (String path : List.of("../evil.pmx", "/absolute.pmx", "C:/drive.pmx", "a/./b", "a/../b")) {
            assertThrows(IOException.class, () -> SafeUploadCollector.normalizeRelativePath(path), path);
        }
        assertThrows(IOException.class,
                () -> SafeUploadCollector.normalizeRelativePath("bad\0name"));
        assertThrows(IOException.class, () -> SafeUploadCollector.normalizeRelativePath(
                "x".repeat(SafeUploadCollector.MAX_RELATIVE_PATH_BYTES + 1)));
    }

    @Test
    void rejectsDuplicateNormalizedPathsAndZipSymlinks() throws Exception {
        Path duplicate = writeZip("duplicate.zip", StandardCharsets.UTF_8, true,
                ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER,
                List.of(spec("Root/A.pmx", "a"), spec("root/a.pmx", "b")));
        assertThrows(IOException.class, () -> SafeUploadCollector.collectZip(duplicate));

        Path slashDuplicate = writeZip("slash-duplicate.zip", StandardCharsets.UTF_8, true,
                ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER,
                List.of(spec("root/a.pmx", "a"), spec("root//a.pmx", "b")));
        assertThrows(IOException.class, () -> SafeUploadCollector.collectZip(slashDuplicate));

        Path symlink = tempDir.resolve("symlink.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(symlink)) {
            ZipArchiveEntry link = new ZipArchiveEntry("root/link.pmx");
            link.setUnixMode(UnixStat.LINK_FLAG | 0777);
            out.putArchiveEntry(link);
            out.write("target.pmx".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }
        assertThrows(IOException.class, () -> SafeUploadCollector.collectZip(symlink));
    }

    @Test
    void enforcesEntryCountExpandedSizeAndCompressionRatioWithoutHugeFixtures() throws Exception {
        List<Spec> entries = new ArrayList<>();
        for (int i = 0; i <= SafeUploadCollector.MAX_ENTRIES; i++) {
            entries.add(directory("d" + i + "/"));
        }
        Path excessiveEntries = writeZip("too-many.zip", StandardCharsets.UTF_8, true,
                ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER, entries);
        assertThrows(IOException.class, () -> SafeUploadCollector.collectZip(excessiveEntries));

        assertThrows(IOException.class, () -> SafeUploadCollector.validateZipEntrySizes(
                "oversized.pmx", SafeUploadCollector.MAX_ENTRY_BYTES + 1, 1));
        assertThrows(IOException.class, () -> SafeUploadCollector.validateZipEntrySizes(
                "unknown.pmx", 1, -1));
        assertThrows(IOException.class, () -> SafeUploadCollector.validateZipEntrySizes(
                "bomb.pmx", 1024L * 1024L + 1L, 0));
        assertEquals(SafeUploadCollector.MAX_EXPANDED_BYTES,
                SafeUploadCollector.checkedExpandedTotal(SafeUploadCollector.MAX_EXPANDED_BYTES - 1, 1));
        assertThrows(IOException.class, () -> SafeUploadCollector.checkedExpandedTotal(
                SafeUploadCollector.MAX_EXPANDED_BYTES, 1));
    }

    @Test
    void collectsDirectoryDirectlyAndFiltersMacMetadata() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("directory-source"));
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("nested/model.pmx"), "model", StandardCharsets.UTF_8);
        Files.writeString(root.resolve(".DS_Store"), "metadata", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("texture.png"), "texture", StandardCharsets.UTF_8);

        List<SafeUploadCollector.Entry> entries = SafeUploadCollector.collectDirectory(root);
        assertEquals(List.of("nested/model.pmx", "texture.png"),
                entries.stream().map(SafeUploadCollector.Entry::relativePath).toList());
        assertArrayEquals("model".getBytes(StandardCharsets.UTF_8), entries.getFirst().payload());
    }

    private void assertSinglePath(Path zip, String expected) throws Exception {
        SafeUploadCollector.ArchiveContents archive = SafeUploadCollector.collectZip(zip);
        assertEquals(1, archive.entries().size());
        assertEquals(expected, archive.entries().getFirst().relativePath());
    }

    private Path writeZip(String fileName, Charset charset, boolean efs,
                          ZipArchiveOutputStream.UnicodeExtraFieldPolicy unicodePolicy,
                          List<Spec> entries) throws Exception {
        Path path = tempDir.resolve(fileName);
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(path)) {
            out.setEncoding(charset.name());
            out.setUseLanguageEncodingFlag(efs);
            out.setFallbackToUTF8(false);
            out.setCreateUnicodeExtraFields(unicodePolicy);
            for (Spec spec : entries) {
                ZipArchiveEntry entry = new ZipArchiveEntry(spec.path());
                out.putArchiveEntry(entry);
                if (!spec.directory()) {
                    out.write(spec.data());
                }
                out.closeArchiveEntry();
            }
        }
        return path;
    }

    private static Spec spec(String path, String payload) {
        return new Spec(path, payload.getBytes(StandardCharsets.UTF_8), false);
    }

    private static Spec directory(String path) {
        return new Spec(path.endsWith("/") ? path : path + "/", new byte[0], true);
    }

    private record Spec(String path, byte[] data, boolean directory) {
    }
}
