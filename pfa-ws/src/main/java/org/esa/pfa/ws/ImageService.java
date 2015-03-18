package org.esa.pfa.ws;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
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
    public Response getTestImage() throws IOException {

        return Response.ok().entity(new StreamingOutput() {
            @Override
            public void write(OutputStream output)
                    throws IOException, WebApplicationException {
                output.write(loadImageResource("bloom.png"));
                output.flush();
            }
        }).build();
    }

    private byte[] loadImageResource(String name) throws IOException {
        URL resource = ImageService.class.getResource(name);
        java.nio.file.Path path = Paths.get(URI.create(resource.toExternalForm()));
        return Files.readAllBytes(path);
    }
}
