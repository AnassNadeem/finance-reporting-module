package com.raez.finance;

import javafx.application.Application;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Use this class as the run target so the app works even when the IDE does not add JavaFX to the module path.
 * Right-click MainLauncher.java → Run, or set your run configuration main class to com.raez.finance.MainLauncher.
 */
public class MainLauncher {

    private static final String JAVAFX_VERSION = "21.0.2";
    private static final String[] JAVAFX_MODULES = {"javafx.base", "javafx.controls", "javafx.fxml", "javafx.graphics"};

    public static void main(String[] args) {
        if (hasJavaFX()) {
            Application.launch(MainApp.class, args);
            return;
        }
        reLaunchWithJavaFX(args);
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
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classPath = System.getProperty("java.class.path");
        String userHome = System.getProperty("user.home");
        Path m2 = Paths.get(userHome, ".m2", "repository", "org", "openjfx");
        String platform = platformClassifier();

        List<String> jfxJars = new ArrayList<>();
        for (String mod : JAVAFX_MODULES) {
            String artifact = mod.replace(".", "-");
            Path jar = m2.resolve(artifact).resolve(JAVAFX_VERSION)
                    .resolve(artifact + "-" + JAVAFX_VERSION + "-" + platform + ".jar");
            if (jar.toFile().exists()) {
                jfxJars.add(jar.toAbsolutePath().toString());
            }
        }

        if (jfxJars.isEmpty()) {
            System.err.println("JavaFX runtime components are missing.");
            System.err.println("Run from terminal: mvn javafx:run");
            System.err.println("Or run: run.bat");
            System.err.println("Or add VM args: --module-path <path-to-javafx>/lib --add-modules javafx.controls,javafx.fxml,javafx.graphics");
            System.exit(1);
        }

        String modulePath = String.format("%s", String.join(File.pathSeparator, jfxJars));
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("--module-path");
        cmd.add(modulePath);
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
            System.err.println("Failed to relaunch with JavaFX: " + e.getMessage());
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
