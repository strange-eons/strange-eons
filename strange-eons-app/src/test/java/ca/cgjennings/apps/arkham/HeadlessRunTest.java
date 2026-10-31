package ca.cgjennings.apps.arkham;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Starts the application in a separate headless JVM, since headless state is
 * fixed for the life of a JVM.
 */
class HeadlessRunTest {

    private static final long TIMEOUT_SECONDS = 120;

    @TempDir
    Path temp;

    private record Result(int status, String output) {
    }

    private Result launch(String... appArgs) throws IOException, InterruptedException {
        return launch(true, appArgs);
    }

    private Result launch(boolean headless, String... appArgs) throws IOException, InterruptedException {
        Path user = temp.resolve("user");
        Files.createDirectories(user);
        Files.writeString(user.resolve("preferences"), "");
        List<String> cmd = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=" + headless,
                "--add-opens=java.desktop/javax.swing.table=ALL-UNNAMED",
                "--enable-native-access=ALL-UNNAMED",
                "-cp", System.getProperty("java.class.path"),
                "ca.cgjennings.apps.arkham.StrangeEons",
                "--xDisableJreCheck", "--glang", "en", "--ulang", "en"
        ));
        cmd.addAll(List.of(appArgs));
        Path log = temp.resolve("output.txt");
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        pb.environment().put("STRANGE_EONS_USER_DIR", user.toString());
        Process p = pb.start();
        if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new AssertionError("timed out:\n" + Files.readString(log, StandardCharsets.UTF_8));
        }
        return new Result(p.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
    }

    private Result runScript(String source) throws IOException, InterruptedException {
        Path script = temp.resolve("script.js");
        Files.writeString(script, source);
        return launch("--run", script.toString());
    }

    @Test
    void scriptRunWithDisplayStaysHidden() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path script = temp.resolve("script.js");
        Files.writeString(script, """
                var windows = java.awt.Window.getWindows();
                var visible = 0;
                for (var i = 0; i < windows.length; ++i) {
                    if (windows[i].isVisible()) ++visible;
                }
                println('windows=' + windows.length + ' visible=' + visible);
                println('uiElement=' + java.lang.System.getProperty('apple.awt.UIElement'));
                """);
        Result r = launch(false, "--run", script.toString());
        assertEquals(0, r.status(), r.output());
        assertTrue(r.output().contains("visible=0"), r.output());
        assertFalse(r.output().contains("windows=0"), r.output());
        assertTrue(r.output().contains("uiElement=true"), r.output());
    }

    @Test
    void scriptRunsAndExitsWithSuccess() throws Exception {
        Result r = runScript("println('headless-marker');");
        assertEquals(0, r.status(), r.output());
        assertTrue(r.output().contains("headless-marker"), r.output());
    }

    @Test
    void scriptCallingExitSucceeds() throws Exception {
        Result r = runScript("println('before'); exit(); println('after');");
        assertEquals(0, r.status(), r.output());
        assertTrue(r.output().contains("before"), r.output());
        assertFalse(r.output().contains("after"), r.output());
    }

    @Test
    void uncaughtScriptErrorExitsWithFailure() throws Exception {
        Result r = runScript("throw new Error('headless-failure');");
        assertEquals(1, r.status(), r.output());
        assertTrue(r.output().contains("headless-failure"), r.output());
    }

    @Test
    void missingScriptExitsWithFailure() throws Exception {
        Result r = launch("--run", new File(temp.toFile(), "missing.js").getPath());
        assertEquals(1, r.status(), r.output());
    }

    @Test
    void noScriptIsRejected() throws Exception {
        Result r = launch();
        assertEquals(11, r.status(), r.output());
        assertTrue(r.output().contains("--run"), r.output());
    }

    @Test
    void servicesUsedByPluginsWorkWithoutWindows() throws Exception {
        Result r = runScript("""
                var prefs = Packages.ca.cgjennings.apps.arkham.dialog.prefs.Preferences;
                prefs.addPreferenceUpdateListener(function () {});
                prefs.firePreferenceUpdate();
                var commands = Packages.ca.cgjennings.apps.arkham.commands.Commands;
                println('commandable=' + commands.findCommandable(commands.CUT));
                var monkey = Packages.ca.cgjennings.apps.arkham.plugins.ScriptMonkey;
                monkey.getConsoleWriter().println('out-marker');
                monkey.getConsoleErrorWriter().println('err-marker');
                monkey.getConsoleWarningWriter().println('warn-marker');
                monkey.flushConsole();
                println('console=' + monkey.getSharedConsole());
                var platform = Packages.ca.cgjennings.platform.PlatformSupport;
                var context = Packages.ca.cgjennings.apps.arkham.plugins.PluginContext;
                println('mask=' + (context.MENU == platform.getMenuShortcutMask()));
                println('stroke=' + (platform.getKeyStroke('menu S') != null));
                Console.visible = true;
                Console.clear();
                Console.scrollToTop();
                Console.scrollToBottom();
                Console.queue();
                Console.flush();
                Console.printHTML('<b>ignored</b>');
                println('visible=' + Console.visible + ' history=[' + Console.history() + ']');
                Console.err.println('console-err-marker');
                """);
        assertEquals(0, r.status(), r.output());
        for (String expected : List.of("commandable=null", "out-marker", "err-marker", "warn-marker", "console=null", "mask=true", "stroke=true", "visible=false history=[]", "console-err-marker")) {
            assertTrue(r.output().contains(expected), expected + " not in:\n" + r.output());
        }
    }
}
