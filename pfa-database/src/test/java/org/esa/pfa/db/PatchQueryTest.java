package org.esa.pfa.db;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;

/**
 *
 */
public class PatchQueryTest {

    @Test
    @Ignore
    public void testQueryAll() throws Exception {
        PatchQuery db = new PatchQuery(new File("c:\\temp"), null, null);

        db.query("product: ENVI*", 30);
    }


}
