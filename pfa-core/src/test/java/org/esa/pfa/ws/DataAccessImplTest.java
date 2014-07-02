package org.esa.pfa.ws;

import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
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
import java.net.URI;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Ralf Quast
 */
public class DataAccessImplTest {

    private static final String WS_ADDRESS = "http://localhost:9999/ws/pfa";
    private static final String WS_NAMESPACE_URI = "http://ws.pfa.esa.org/";
    private static final String WS_NAME = "DataAccessImplService";

    private Endpoint endpoint;

    @Before
    public void setUp() throws Exception {
        endpoint = Endpoint.publish(WS_ADDRESS, new DataAccessImpl());
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

        final Element rootElement = document.getRootElement();
        assertNotNull(rootElement);

        final Attribute targetNamespaceAttribute = rootElement.getAttribute("targetNamespace");
        assertNotNull(targetNamespaceAttribute);

        final String namespaceURI = targetNamespaceAttribute.getValue();
        assertEquals(WS_NAMESPACE_URI, namespaceURI);

        final Attribute nameAttribute = rootElement.getAttribute("name");
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

        final DataAccess dataAccess = service.getPort(DataAccess.class);

        final String[] uris = dataAccess.getAllQuicklookUris("MER_RR__1PNACR20091016_130013_000026332083_00253_39886_0000.N1", 0, 8);
        assertEquals(4, uris.length);
    }

    @WebService
    @SOAPBinding(style = SOAPBinding.Style.RPC)
    public interface DataAccess {

        @WebMethod
        String[] getAllQuicklookUris(String productName, int patchX, int patchY);
    }

    @WebService(endpointInterface = "org.esa.pfa.ws.DataAccessImplTest$DataAccess")
    public static class DataAccessImpl implements DataAccess {

        @Override
        public String[] getAllQuicklookUris(String productName, int patchX, int patchY) {
            return new String[0];
        }
    }

}
