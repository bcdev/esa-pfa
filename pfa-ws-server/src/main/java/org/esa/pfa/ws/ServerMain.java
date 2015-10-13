package org.esa.pfa.ws;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.http.server.accesslog.AccessLogBuilder;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * Main class.
 *
 */
public class ServerMain {
    // Base URI the Grizzly HTTP server will listen on
    public static final String BASE_URI = "http://localhost:8089/pfa/";

    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     * @param serverURI
     */
    public static HttpServer startServer(URI serverURI) throws IOException {
        // create a resource config that scans for JAX-RS resources and providers
        // in org.esa.pfa.ws package
        final ResourceConfig resourceConfig = new ResourceConfig()
                .packages("org.esa.pfa.ws")
                .property("jersey.config.server.tracing.type ", "ALL");

        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        HttpServer httpServer = GrizzlyHttpServerFactory.createHttpServer(serverURI, resourceConfig, false);
        ServerConfiguration serverConfiguration = httpServer.getServerConfiguration();

        final AccessLogBuilder builder = new AccessLogBuilder("access.log");
        builder.instrument(serverConfiguration);

        httpServer.start();
        return httpServer;
    }

    /**
     * Main method.
     * @param args
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws IOException {
        if (!(args.length == 1 || args.length == 2)) {
            throw new IllegalArgumentException("Usage: " + ServerMain.class.getSimpleName() + "  <db-path> [<server-uri>]");
        }
        String dbPath = args[0];
        String serverUri = args.length == 2 ? args[1] : BASE_URI;
        System.setProperty("pfa.dbPath", dbPath);

        CachingLocalDatabaseManager.setDbUri(new File(dbPath).toURI());
        final HttpServer server = startServer(URI.create(serverUri));

        System.out.println(String.format("Jersey app started with WADL available at " +
                                                 "%s/application.wadl\nHit enter to stop it...", serverUri));
//        System.in.read();
//        server.shutdownNow();
    }
}

