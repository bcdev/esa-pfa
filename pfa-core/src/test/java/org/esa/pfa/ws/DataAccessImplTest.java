package org.esa.pfa.ws;

import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.WindowConstants;
import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Service;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderNotFoundException;
import java.util.ArrayList;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Ralf Quast
 */
@Ignore
public class DataAccessImplTest {

    private static final String WS_ADDRESS = "http://localhost:9999/ws/pfa";
    private static final String WS_NAMESPACE_URI = "http://ws.pfa.esa.org/";
    private static final String WS_NAME = "DataAccessImplService";

    // TODO - define final URI format
    private static final String QL_URI_CHL = "MER_RR__1PNACR20091016_130013_000026332083_00253_39886_0000.N1.fex.zip!/x000y008/chl_ql.png";
    private static final String QL_URI_FLH = "MER_RR__1PNACR20091016_130013_000026332083_00253_39886_0000.N1.fex.zip!/x000y008/flh_ql.png";
    private static final String QL_URI_MCI = "MER_RR__1PNACR20091016_130013_000026332083_00253_39886_0000.N1.fex.zip!/x000y008/mci_ql.png";
    private static final String QL_URI_RGB1 = "MER_RR__1PNACR20091016_130013_000026332083_00253_39886_0000.N1.fex.zip!/x000y008/rgb1_ql.png";
    private static final String QL_URI_RGB2 = "MER_RR__1PNACR20091016_130013_000026332083_00253_39886_0000.N1.fex.zip!/x000y008/rgb2_ql.png";

    private static final String TEST_PRODUCT_NAME = "MER_RR__1PNACR20091016_130013_000026332083_00253_39886_0000.N1";

    private Endpoint endpoint;

    public static void main(String[] args) throws IOException {
        final Endpoint endpoint = createEndpoint(WS_ADDRESS);
        try {
            final Service service = createWebService();
            final DataAccess serviceDataAccess = service.getPort(DataAccess.class);

            final byte[] data = serviceDataAccess.getQuicklookData(QL_URI_RGB1);
            final BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));

            final JFrame frame = new JFrame();
            frame.add(new JLabel(new ImageIcon(image)));
            frame.pack();
            frame.setVisible(true);
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        } finally {
            endpoint.stop();
        }
    }

    @Before
    public void setUp() throws Exception {
        endpoint = createEndpoint(WS_ADDRESS);
    }

    private static Endpoint createEndpoint(String address) {
        final DataAccessImpl dataAccess = new DataAccessImpl();
        // TODO - get archive root from system property
        dataAccess.setArchiveRootPathString("/Users/ralf/scratch/pfa/algal_blooms/benchmarking/output");
        dataAccess.setQuicklookFileNameSuffix("_ql.png");
        dataAccess.setZipFileSuffix(".fex.zip");
        return Endpoint.publish(address, dataAccess);
    }

    @After
    public void tearDown() throws Exception {
        if (endpoint != null) {
            endpoint.stop();
        }
    }

    @Test
    public void testWebServiceEndpoint() throws Exception {
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

    private static Service createWebService() throws MalformedURLException {
        final URL url = new URL(WS_ADDRESS + "?wsdl");
        final QName qname = new QName(WS_NAMESPACE_URI, WS_NAME);

        return Service.create(url, qname);
    }

    @Test
    public void testGetAllQuicklookUris() throws Exception {
        final Service service = createWebService();
        assertNotNull(service);

        final DataAccess dataAccess = service.getPort(DataAccess.class);
        assertNotNull(dataAccess);

        final String[] uris = dataAccess.getAllQuicklookUris(TEST_PRODUCT_NAME, 0, 8);

        assertEquals(5, uris.length);
        assertEquals(QL_URI_CHL, uris[0]);
        assertEquals(QL_URI_FLH, uris[1]);
        assertEquals(QL_URI_MCI, uris[2]);
        assertEquals(QL_URI_RGB1, uris[3]);
        assertEquals(QL_URI_RGB2, uris[4]);
    }

    @Test
    public void testGetQuicklookData() throws Exception {
        final Service service = createWebService();
        assertNotNull(service);

        final DataAccess dataAccess = service.getPort(DataAccess.class);
        assertNotNull(dataAccess);

        byte[] data;

        data = dataAccess.getQuicklookData(QL_URI_CHL);
        assertEquals(12312, data.length);

        data = dataAccess.getQuicklookData(QL_URI_FLH);
        assertEquals(21399, data.length);

        data = dataAccess.getQuicklookData(QL_URI_MCI);
        assertEquals(19298, data.length);

        data = dataAccess.getQuicklookData(QL_URI_RGB1);
        assertEquals(39685, data.length);

        data = dataAccess.getQuicklookData(QL_URI_RGB2);
        assertEquals(58078, data.length);

        final BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));

        assertEquals(200, image.getHeight());
        assertEquals(200, image.getWidth());
    }

    @Test
    public void testGetPatchDirName() throws Exception {
        assertEquals("x000y000", DataAccessImpl.getPatchDirName(0, 0));
        assertEquals("x000y008", DataAccessImpl.getPatchDirName(0, 8));
        assertEquals("x008y000", DataAccessImpl.getPatchDirName(8, 0));
        assertEquals("x008y008", DataAccessImpl.getPatchDirName(8, 8));
    }

    @WebService
    @SOAPBinding(style = SOAPBinding.Style.RPC)
    public interface DataAccess {

        @WebMethod
        String[] getAllQuicklookUris(String productName, int patchX, int patchY);

        @WebMethod
        byte[] getQuicklookData(String quicklookUri);
    }

    @WebService(endpointInterface = "org.esa.pfa.ws.DataAccessImplTest$DataAccess")
    public static class DataAccessImpl implements DataAccess {

        private String archiveRootPathString;
        private String quicklookFileNameSuffix;
        private String zipFileSuffix;

        String getArchiveRootPathString() {
            return archiveRootPathString;
        }

        void setArchiveRootPathString(String pathString) {
            this.archiveRootPathString = pathString;
        }

        String getQuicklookFileNameSuffix() {
            return quicklookFileNameSuffix;
        }

        void setQuicklookFileNameSuffix(String suffix) {
            this.quicklookFileNameSuffix = suffix;
        }

        String getZipFileSuffix() {
            return zipFileSuffix;
        }

        void setZipFileSuffix(String suffix) {
            this.zipFileSuffix = suffix;
        }

        @Override
        public String[] getAllQuicklookUris(String productName, int patchX, int patchY) {
            try {
                final Path zipFilePath = Paths.get(getArchiveRootPathString(), productName + getZipFileSuffix());
                try (final FileSystem fileSystem = FileSystems.newFileSystem(zipFilePath, null)) {
                    final String patchDirName = getPatchDirName(patchX, patchY);
                    final Path patchDirPath = fileSystem.getPath(patchDirName);
                    final ArrayList<String> uriStrings = new ArrayList<>(5);
                    Files.list(patchDirPath).forEach(path -> {
                        if (Files.isRegularFile(path)) {
                            if (path.getFileName().toString().endsWith(getQuicklookFileNameSuffix())) {
                                final String uriString = productName + getZipFileSuffix() + "!" + path.toString();
                                uriStrings.add(uriString);
                            }
                        }
                    });
                    uriStrings.sort((o1, o2) -> o1.compareTo(o2));
                    return uriStrings.toArray(new String[uriStrings.size()]);
                } catch (ProviderNotFoundException e) {
                    e.printStackTrace();
                    return null;
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    return null;
                } catch (NotDirectoryException e) {
                    e.printStackTrace();
                    return null;
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            } catch (InvalidPathException e) {
                return null;
            }
        }

        @Override
        public byte[] getQuicklookData(String uriString) {
            final String[] parts = uriString.split("!", 2);
            try {
                final Path zipFilePath = Paths.get(getArchiveRootPathString(), parts[0]);
                try (final FileSystem fileSystem = FileSystems.newFileSystem(zipFilePath, null)) {
                    final Path quicklookFilePath = fileSystem.getPath(parts[1]);
                    return Files.readAllBytes(quicklookFilePath);
                } catch (InvalidPathException e) {
                    e.printStackTrace();
                    return null;
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            } catch (InvalidPathException e) {
                e.printStackTrace();
                return null;
            }
        }

        // package public for testing only
        static String getPatchDirName(int patchX, int patchY) {
            return String.format("x%03dy%03d", patchX, patchY);
        }
    }

}
