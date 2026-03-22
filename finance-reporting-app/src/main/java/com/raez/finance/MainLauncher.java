package com.raez.finance;

import javafx.application.Application;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * MainLauncher — IDE convenience entry point.
 *
 * WHY KEEP THIS SEPARATE FROM MainApp?
 * ─────────────────────────────────────────────────────────────────────────
 * MainLauncher does NOT import any javafx.* classes.  This is intentional.
 *
 * When JavaFX is NOT on the JVM module-path (common in IDEs that don't
 * auto-configure the Maven JavaFX plugin), the JVM would fail to LOAD any
 * class that imports javafx.*, crashing before even reaching main().
 *
 * By keeping this file completely javafx-free, we can:
 *   1. Detect at runtime whether JavaFX is available (via Class.forName)
 *   2. If not: find the JavaFX jars in the local Maven repo (~/.m2) and
 *      re-launch the JVM with the correct --module-path flags
 *   3. If yes: delegate straight to Application.launch(MainApp, args)
 *
 * Merging MainApp and MainLauncher into a single file is NOT possible
 * cleanly — a class extending Application that also imports javafx.* will
 * fail to load if JavaFX isn't on the module path, before the launcher
 * logic can run.  Reflection workarounds are fragile.
 *
 * ── Bottom line ──────────────────────────────────────────────────────────
 *   Keep these two files separate. Set your IDE run target to MainLauncher.
 *   Use `mvn javafx:run` for clean builds.
 */
public class MainLauncher {

    private static final String JAVAFX_VERSION = "21.0.2";
    private static final String[] JAVAFX_MODULES = {
        "javafx.base", "javafx.controls", "javafx.fxml", "javafx.graphics"
    };

    public static void main(String[] args) {
        if (hasJavaFX()) {
            // JavaFX is already on the module path — just launch.
            Application.launch(MainApp.class, args);
        } else {
            // Not available: re-launch with the jars from the local Maven repo.
            reLaunchWithJavaFX(args);
        }
    }

    private static boolean hasJavaFX() {
        try {
            Class.forName("javafx.application.Application");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void reLaunchWithJavaFX(String[] args) {
        String javaHome  = System.getProperty("java.home");
        String javaBin   = javaHome + File.separator + "bin" + File.separator + "java";
        String classPath = System.getProperty("java.class.path");
        String userHome  = System.getProperty("user.home");
        Path   m2        = Paths.get(userHome, ".m2", "repository", "org", "openjfx");
        String platform  = platformClassifier();

        List<String> jfxJars = new ArrayList<>();
        for (String mod : JAVAFX_MODULES) {
            String artifact = mod.replace(".", "-");
            Path jar = m2.resolve(artifact)
                         .resolve(JAVAFX_VERSION)
                         .resolve(artifact + "-" + JAVAFX_VERSION + "-" + platform + ".jar");
            if (jar.toFile().exists()) jfxJars.add(jar.toAbsolutePath().toString());
        }

        if (jfxJars.isEmpty()) {
            System.err.println("[MainLauncher] JavaFX runtime jars not found in ~/.m2.");
            System.err.println("  → Run: mvn javafx:run");
            System.err.println("  → Or:  add --module-path <path>/lib --add-modules javafx.controls,javafx.fxml,javafx.graphics to your IDE VM args");
            System.exit(1);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("--module-path");
        cmd.add(String.join(File.pathSeparator, jfxJars));
        cmd.add("--add-modules");
        cmd.add("javafx.controls,javafx.fxml,javafx.graphics");
        cmd.add("-cp");
        cmd.add(classPath);
        cmd.add("com.raez.finance.MainApp");
        Stream.of(args).forEach(cmd::add);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO();
            Process p = pb.start();
            System.exit(p.waitFor());
        } catch (Exception e) {
            System.err.println("[MainLauncher] Re-launch failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String platformClassifier() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "win";
        if (os.contains("mac")) return "mac";
        return "linux";
    }
}