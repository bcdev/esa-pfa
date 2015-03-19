package org.esa.pfa.ws;

import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Root resource (exposed at "testimage" path)
 */
@Path("testimage")
public class ImageService {

    @GET
    @Produces("image/png")
    public Response getTestImage(@QueryParam(value = "name") final String name) throws IOException {

        return Response.ok().header("name", name).entity(new StreamingOutput() {
            @Override
            public void write(OutputStream output)
                    throws IOException, WebApplicationException {
                output.write(loadImageResource(name));
                output.flush();
            }
        }).build();
    }

    private byte[] loadImageResource(String name) throws IOException {
        System.out.println("name = " + name);
        URL resource = ImageService.class.getResource(name);
        java.nio.file.Path path = Paths.get(URI.create(resource.toExternalForm()));
        System.out.println("path = " + path);
        return Files.readAllBytes(path);
    }
}
