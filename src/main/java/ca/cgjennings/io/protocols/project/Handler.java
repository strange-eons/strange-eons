package ca.cgjennings.io.protocols.project;

import ca.cgjennings.apps.arkham.StrangeEons;
import ca.cgjennings.apps.arkham.StrangeEonsAppWindow;
import ca.cgjennings.apps.arkham.project.Project;
import ca.cgjennings.io.protocols.MappedURLHandler;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/**
 * A URL protocol handler for the {@code project:} protocol, which accesses the
 * contents of the open project.
 *
 * <p>
 * Note: the unusual class name is required for this class to be used by the
 * default protocol handler factory.
 *
 * @author Chris Jennings <https://cgjennings.ca/contact>
 * @since 3.0
 */
public class Handler extends ca.cgjennings.io.protocols.MappedURLHandler {

    @Override
    protected void parseURL(URL u, String spec, int start, int limit) {
        super.parseURL(u, spec, start, limit);
        if (u.getHost() != null && !u.getHost().isEmpty()) {
            setURL(u, u.getProtocol(), "", -1, null, null, u.getHost() + u.getPath(), u.getQuery(), u.getRef());
        }
    }

    @Override
    protected URLConnection mapConnection(URL sourceURL, URL mappedURL) throws IOException {
        URL resolved = resolve(sourceURL, getOpenProjectFolder());
        if (resolved == null) {
            throw new FileNotFoundException(MappedURLHandler.getComposedPath(sourceURL));
        }
        return resolved.openConnection();
    }

    /**
     * Returns the folder of the open project.
     *
     * @return the project folder, or {@code null} if no project is open
     */
    public static File getOpenProjectFolder() {
        StrangeEonsAppWindow af = StrangeEons.getApplication() == null ? null : StrangeEons.getWindow();
        Project p = af == null ? null : af.getOpenProject();
        return p == null ? null : p.getFile();
    }

    /**
     * Returns the URL that a {@code project:} URL reads from within a project
     * folder.
     *
     * @param url a {@code project:} URL
     * @param projectFolder the project folder, or {@code null}
     * @return the URL in the project folder, or {@code null} if the folder is
     * {@code null} or the URL has no path
     * @throws MalformedURLException if the path cannot be joined to the folder
     */
    public static URL resolve(URL url, File projectFolder) throws MalformedURLException {
        String path = MappedURLHandler.getComposedPath(url);
        if (path == null || projectFolder == null) {
            return null;
        }
        int slashes = 0;
        while (slashes < path.length() && path.charAt(slashes) == '/') {
            ++slashes;
        }
        return new URL(projectFolder.toURI().toURL(), path.substring(slashes));
    }
}
