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

package org.esa.pfa.spark;

import org.apache.hadoop.fs.FSDataInputStream;

import javax.imageio.stream.ImageInputStreamImpl;
import java.io.IOException;


/**
 * An {@link javax.imageio.stream.ImageInputStream} that can read from a Hadoop {@link FSImageInputStream}.
 *
 * @author Marco Zuehlke
 * @since 0.1
 */
public class FSImageInputStream extends ImageInputStreamImpl {

    private FSDataInputStream fsInStream;
    private final long length;

    public FSImageInputStream(FSDataInputStream fsInStream, long length) {
        this.fsInStream = fsInStream;
        this.length = length;
    }

    public int read() throws IOException {
        checkClosed();
        bitOffset = 0;
        int val = fsInStream.read();
        if (val != -1) {
            ++streamPos;
        }
        return val;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        checkClosed();
        bitOffset = 0;
        int nbytes = fsInStream.read(b, off, len);
        if (nbytes != -1) {
            streamPos += nbytes;
        }
        return nbytes;
    }

    public long length() {
        return length;
    }

    public void seek(long pos) throws IOException {
        checkClosed();
        if (pos < flushedPos) {
            throw new IndexOutOfBoundsException("pos < flushedPos!");
        }
        bitOffset = 0;
        fsInStream.seek(pos);
        streamPos = fsInStream.getPos();
    }

    public void close() throws IOException {
        try {
            super.close();
            fsInStream.close();
        } finally {
            fsInStream = null;
        }
    }
}
