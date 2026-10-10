package dream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Keeps installed-app data in a user-writable location. */
final class UserData {
    private UserData() { }

    static Path file(String name) {
        if (!Boolean.getBoolean("dream.packaged")) {
            return Paths.get(name);
        }

        String appData = System.getenv("APPDATA");
        Path directory = appData == null || appData.isEmpty()
            ? Paths.get(System.getProperty("user.home"), ".dream")
            : Paths.get(appData, "DREAM");
        return directory.resolve(name);
    }

    static void createParent(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
