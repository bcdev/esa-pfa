package org.esa.pfa.ws;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;

/**
 * Root resource (exposed at "testimage" path)
 */
@Path("testimage")
public class ImageService {

    @GET
    @Produces("image/png")
    public Response getTestImage(@QueryParam(value = "name") final String name) throws IOException {
        InputStream inputStream = ImageService.class.getResourceAsStream(name);
        return Response.ok().header("name", name).entity(inputStream).build();
    }
}
