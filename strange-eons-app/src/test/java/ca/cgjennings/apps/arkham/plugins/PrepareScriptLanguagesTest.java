package ca.cgjennings.apps.arkham.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PrepareScriptLanguagesTest {

    @AfterEach
    void reset() {
        TestScriptLanguage.prepareCount.set(0);
        TestScriptLanguage.nextPrepareFailure = null;
    }

    @Test
    void preparesEachLanguageOnceOnADaemonThread() throws InterruptedException {
        Thread thread = BundleInstaller.prepareScriptLanguages();
        assertThat(thread.isDaemon()).isTrue();
        assertThat(thread.getPriority()).isEqualTo(Thread.MIN_PRIORITY);
        thread.join(10_000);
        assertThat(TestScriptLanguage.prepareCount.get()).isEqualTo(1);
    }

    @Test
    void failureIsContainedInTheThread() throws InterruptedException {
        TestScriptLanguage.nextPrepareFailure = new IllegalStateException("no runtime");
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Throwable[] uncaught = new Throwable[1];
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> uncaught[0] = e);
        try {
            BundleInstaller.prepareScriptLanguages().join(10_000);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
        assertThat(TestScriptLanguage.prepareCount.get()).isEqualTo(1);
        assertThat(uncaught[0]).isNull();
    }
}
