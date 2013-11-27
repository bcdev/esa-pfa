package org.esa.rss.pfa.fe.mahout;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.mahout.clustering.Cluster;
import org.apache.mahout.clustering.iterator.CIMapper;
import org.apache.mahout.clustering.iterator.CIReducer;
import org.apache.mahout.clustering.iterator.ClusterIterator;
import org.apache.mahout.clustering.iterator.ClusterWritable;
import org.junit.Test;

import static org.junit.Assert.*;

public class MahoutTest {

    @Test
    public void testKMeansClustering() throws Exception {
        org.apache.mahout.clustering.syntheticcontrol.kmeans.Job.main(new String[0]);
    }

    @Test
    public void testJob() throws Exception {
        String jobName = "TestJob";
        Configuration conf = new Configuration();
        Path outPath = new Path("output");
        Path priorPath = new Path(outPath, Cluster.CLUSTERS_DIR + 5);
        conf.set("org.apache.mahout.clustering.prior.path", priorPath.toString());
        Job job = new Job(conf, jobName);
        job.setMapOutputKeyClass(IntWritable.class);
        job.setMapOutputValueClass(ClusterWritable.class);
        job.setOutputKeyClass(IntWritable.class);
        job.setOutputValueClass(ClusterWritable.class);

        job.setInputFormatClass(SequenceFileInputFormat.class);
        job.setOutputFormatClass(SequenceFileOutputFormat.class);
        job.setMapperClass(CIMapper.class);
        job.setReducerClass(CIReducer.class);

        Path inPath = new Path("output/data");
        FileInputFormat.addInputPath(job, inPath);
        Path clustersOut = new Path(outPath, Cluster.CLUSTERS_DIR + 6);
        FileOutputFormat.setOutputPath(job, clustersOut);

        job.setJarByClass(ClusterIterator.class);

        assertTrue(job.waitForCompletion(true));
    }
}
