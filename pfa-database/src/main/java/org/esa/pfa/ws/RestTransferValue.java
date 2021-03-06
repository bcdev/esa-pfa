/*
 * Copyright (C) 2015 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.pfa.ws;

import com.thoughtworks.xstream.XStream;
import org.esa.pfa.fe.op.Patch;

import java.io.IOException;
import java.io.StringWriter;

/**
 * Used for transfering information between the REST service and the client
 */
public class RestTransferValue {

    private Patch[] patches;

    public Patch[] getPatches() {
        return patches;
    }

    public void setPatches(Patch[] patches) {
        this.patches = patches;
    }

    public static RestTransferValue fromXML(String xml) {
        RestTransferValue model = new RestTransferValue();
        getXStream().fromXML(xml, model);
        return model;
    }

    public String toXML() throws IOException {
        try (StringWriter writer = new StringWriter()) {
            getXStream().toXML(this, writer);
            return writer.toString();
        }
    }

    private static XStream getXStream() {
        XStream xStream = new XStream();
        xStream.omitField(Patch.class, "featureList");
        xStream.omitField(Patch.class, "imageMap");
        xStream.omitField(Patch.class, "listenerList");
        xStream.omitField(Patch.class, "patchProduct");
        xStream.setClassLoader(RestTransferValue.class.getClassLoader());
        return xStream;
    }
}
