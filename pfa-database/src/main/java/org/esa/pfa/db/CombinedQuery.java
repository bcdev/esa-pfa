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

package org.esa.pfa.db;

import org.esa.pfa.fe.op.Patch;

import java.io.IOException;

/**
 * @author marcoz
 */
public class CombinedQuery implements QueryInterface {

    private final QueryInterface forQuery;
    private final QueryInterface forRandom;

    public CombinedQuery(QueryInterface forQuery, QueryInterface forRandom) {
        this.forQuery = forQuery;
        this.forRandom = forRandom;
    }

    @Override
    public int getNumPatchesInDatabase() {
        return forRandom.getNumPatchesInDatabase();
    }

    @Override
    public Patch getPatch(int patchIndex) throws IOException {
        return forRandom.getPatch(patchIndex);
    }

    @Override
    public Patch[] getRandomPatches(int numPatches) throws IOException {
        return forRandom.getRandomPatches(numPatches);
    }

    @Override
    public Patch[] query(String queryExpr, int hitCount) throws IOException {
        return forQuery.query(queryExpr, hitCount);
    }
}
