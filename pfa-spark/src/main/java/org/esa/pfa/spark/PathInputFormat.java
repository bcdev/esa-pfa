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

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;

import java.io.IOException;

/**
 * Created by marcoz on 06.02.15.
 */
public class PathInputFormat extends FileInputFormat<String, String> {

    @Override
    protected boolean isSplitable(JobContext context, Path filename) {
        return false;
    }

    @Override
    public RecordReader<String, String> createRecordReader(InputSplit inputSplit, TaskAttemptContext taskAttemptContext) throws IOException, InterruptedException {
        return new PathRecordReader();
    }


    private static class PathRecordReader extends RecordReader<String, String> {

        private String pathAsString;
        private boolean hasMoore;

        @Override
        public void initialize(InputSplit inputSplit, TaskAttemptContext taskAttemptContext) throws IOException, InterruptedException {
            FileSplit fileSplit = (FileSplit) inputSplit;
            pathAsString = fileSplit.getPath().toString();
            hasMoore = true;
        }

        @Override
        public boolean nextKeyValue() throws IOException, InterruptedException {
            if (hasMoore) {
                hasMoore = false;
                return true;
            }
            return false;
        }

        @Override
        public String getCurrentKey() throws IOException, InterruptedException {
            return pathAsString;
        }

        @Override
        public String getCurrentValue() throws IOException, InterruptedException {
            return pathAsString;
        }

        @Override
        public float getProgress() throws IOException, InterruptedException {
            return 0;
        }

        @Override
        public void close() throws IOException {

        }
    }
}
