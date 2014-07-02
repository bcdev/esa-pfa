package org.esa.pfa.ws;

import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.input.SAXBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Service;
import java.io.InputStream;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A Jax-WS example.
 *
 * @author Ralf Quast
 */

public class JaxWsExample {


    private static final String WS_ADDRESS = "http://localhost:9999/ws/hello";
    private static final String WS_NAMESPACE_URI = "http://ws.pfa.esa.org/";
    private static final String WS_NAME = "HelloWorldImplService";

    private Endpoint endpoint;

    @Before
    public void setUp() throws Exception {
        try {
            endpoint = Endpoint.publish(WS_ADDRESS, new HelloWorldImpl());
        } catch (Exception ignored) {
        }
    }

    @After
    public void tearDown() throws Exception {
        if (endpoint != null) {
            endpoint.stop();
        }
    }

    @Test
    public void getWsdlDocument() throws Exception {
        final URL url = new URL(WS_ADDRESS + "?wsdl");

        final Object content = url.getContent();
        assertTrue(content instanceof InputStream);

        @SuppressWarnings("ConstantConditions") final InputStream inputStream = (InputStream) content;
        final Document document = new SAXBuilder().build(inputStream);
        assertNotNull(document);

        final Attribute targetNamespaceAttribute = document.getRootElement().getAttribute("targetNamespace");
        assertNotNull(targetNamespaceAttribute);

        final String namespaceURI = targetNamespaceAttribute.getValue();
        assertEquals(WS_NAMESPACE_URI, namespaceURI);

        final Attribute nameAttribute = document.getRootElement().getAttribute("name");
        assertNotNull(nameAttribute);

        final String name = nameAttribute.getValue();
        assertEquals(WS_NAME, name);
    }

    @Test
    public void simulateWebServiceClient() throws Exception {
        final URL url = new URL(WS_ADDRESS + "?wsdl");

        final QName qname = new QName(WS_NAMESPACE_URI, WS_NAME);

        final Service service = Service.create(url, qname);
        assertNotNull(service);

        final HelloWorld helloWorld = service.getPort(HelloWorld.class);

        assertEquals("Hello World JAX-WS example", helloWorld.getHelloWorldAsString("example"));
    }

    @WebService
    @SOAPBinding(style = SOAPBinding.Style.RPC)
    public static interface HelloWorld {

        @WebMethod
        String getHelloWorldAsString(String name);
    }


    @WebService(endpointInterface = "org.esa.pfa.ws.JaxWsExample$HelloWorld")
    public static class HelloWorldImpl implements HelloWorld {

        @Override
        public String getHelloWorldAsString(String name) {
            return "Hello World JAX-WS " + name;
        }
    }

}
