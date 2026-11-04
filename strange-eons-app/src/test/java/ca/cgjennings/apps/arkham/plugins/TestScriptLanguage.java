package ca.cgjennings.apps.arkham.plugins;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.mockito.Mockito;
import org.strangeeons.scripts.ScriptException;
import org.strangeeons.scripts.ScriptLanguage;
import org.strangeeons.scripts.ScriptSession;

/**
 * Test {@link ScriptLanguage} registered through {@code META-INF/services}
 * for the {@code script-language} dispatch tests.
 *
 * <p>The session it returns ignores eval source content and records every
 * call so tests can assert what the host did.
 */
public class TestScriptLanguage implements ScriptLanguage {

    public static final String ID = "test-language";

    /** Captures the most recently created session so tests can inspect it. */
    public static volatile RecordingSession lastSession;

    /** How many times {@link #prepare()} has run, across instances. */
    public static final java.util.concurrent.atomic.AtomicInteger prepareCount = new java.util.concurrent.atomic.AtomicInteger();

    /** When set, {@link #prepare()} throws this. */
    public static volatile RuntimeException nextPrepareFailure;

    /** When set, {@code createHandler} on every session fails with this. */
    public static volatile ScriptException nextHandlerFailure;

    /**
     * Returns to install on every newly created session's {@code returns}
     * map, simulating script functions the host probes for. Tests populate
     * this <em>before</em> calling {@link ScriptLanguagePlugin#initializePlugin}.
     */
    public final Map<String, Object> defaultReturns = new HashMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Test Language";
    }

    @Override
    public Set<String> fileExtensions() {
        return Set.of(".test");
    }

    @Override
    public void prepare() {
        prepareCount.incrementAndGet();
        RuntimeException failure = nextPrepareFailure;
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public ScriptSession createSession() {
        RecordingSession s = new RecordingSession();
        s.returns.putAll(defaultReturns);
        lastSession = s;
        return s;
    }

    /**
     * Session impl that records every method invocation. Tests configure
     * {@link #returns} to dictate what {@link #callIfDefined} returns for
     * a named function (defaults to {@code null}, i.e. "not defined").
     */
    public static final class RecordingSession implements ScriptSession {

        public final Map<String, Object> bindings = new HashMap<>();
        public final Map<String, Object> returns = new HashMap<>();
        public int evalCount;
        public String lastEvalSourceName;
        public boolean closed;
        public String lastHandlerSpec;
        public final java.util.List<String> optionalCalls = new java.util.ArrayList<>();

        @Override
        public Object eval(String source, String sourceName) {
            evalCount++;
            lastEvalSourceName = sourceName;
            return null;
        }

        @Override
        public Object call(String name, Object... args) throws ScriptException {
            if (!returns.containsKey(name)) {
                throw new ScriptException("undefined: " + name);
            }
            return returns.get(name);
        }

        @Override
        public Object callIfDefined(String name, Object... args) {
            optionalCalls.add(name);
            return returns.get(name);
        }

        @Override
        public void bind(String name, Object value) {
            bindings.put(name, value);
        }

        @Override
        public <T> T createHandler(String spec, Class<T> type) throws ScriptException {
            lastHandlerSpec = spec;
            if (nextHandlerFailure != null) {
                throw nextHandlerFailure;
            }
            return Mockito.mock(type);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
