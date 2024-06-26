package emissary.util.io;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UnitTestFileUtils {
    public static void cleanupDirectoryRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static List<Path> findFilesWithRegex(Path path, String regex) throws Exception {
        List<Path> paths = new ArrayList<>();
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(path, regex)) {
            for (Path p : dirStream) {
                paths.add(p);
            }
        }
        return paths;
    }

    @SuppressWarnings("PreferredInterfaceType")
    public static Collection<Path> findFilesByExtension(Path dir, String ext) throws IOException {
        return findFilesByExtension(dir, ext, Integer.MAX_VALUE);
    }

    @SuppressWarnings("PreferredInterfaceType")
    public static Collection<Path> findFilesByExtension(Path dir, String ext, int depth) throws IOException {
        try (Stream<Path> walk = Files.find(dir, depth, (path, attrs) -> path.toString().endsWith(ext))) {
            return walk.collect(Collectors.toList());
        }
    }

    /** This class is not meant to be instantiated. */
    private UnitTestFileUtils() {}
}
