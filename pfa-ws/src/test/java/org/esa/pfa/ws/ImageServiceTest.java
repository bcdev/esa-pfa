package org.esa.pfa.ws;

import org.esa.pfa.ws.Main;
import org.glassfish.grizzly.http.server.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ImageServiceTest {

    private HttpServer server;
    private WebTarget target;

    @Before
    public void setUp() throws Exception {
        // start the server
        server = Main.startServer();
        // create the client
        Client c = ClientBuilder.newClient();

        // uncomment the following line if you want to enable
        // support for JSON in the client (you also have to uncomment
        // dependency on jersey-media-json module in pom.xml and Main.startServer())
        // --
        // c.configuration().enable(new org.glassfish.jersey.media.json.JsonJaxbFeature());
        target = c.target(Main.BASE_URI);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdownNow();
    }

    /**
     * Test to see that the message "Got it!" is sent in the response.
     */
    @Test
    public void testGetIt() {
        testImage("bloom1.png");
        testImage("bloom2.png");
    }

    private void testImage(String name) {
        Response response = target.path("testimage").queryParam("name", name).request().get();
        assertNotNull(response);
        assertEquals(200, response.getStatus());
        assertEquals(new MediaType("image", "png"), response.getMediaType());
        assertEquals(name, response.getHeaderString("name"));
    }
}
