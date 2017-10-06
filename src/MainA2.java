/* author
Shreysa Sharma
09/24/2017
 */

package org.myorg;

import java.io.IOException;
import java.util.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.LineReader;



public class MainA2 {

    public static void main(String [] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: <app> input_path output_path num_neighbors");
            System.exit(1);
        }

      String inPath = args[0];
      String outPath = args[1];
      String kval = args[2];
      String interPath = outPath + "_intermediate";

      Configuration conf = new Configuration();
      conf.set("neighbors", kval);

      Job job1 = Job.getInstance(conf, "letter scores");
      job1.setJarByClass(LetterScore.class);
      job1.setMapperClass(LetterScore.LetterScoreMapper.class);
      job1.setCombinerClass(LetterScore.LetterScoreCombiner.class);
      job1.setReducerClass(LetterScore.LetterScoreReducer.class);
      job1.setOutputKeyClass(Text.class);
      job1.setOutputValueClass(IntWritable.class);

      FileInputFormat.addInputPath(job1, new Path(inPath));
      FileOutputFormat.setOutputPath(job1, new Path(interPath));

      boolean isJob1Completed = job1.waitForCompletion(true);

      if(!isJob1Completed) System.exit(1);

      String distributedPath = interPath + "/merged_results.txt";

        try {
            FileSystem hdfs = FileSystem.get(conf);
            FileUtil.copyMerge(hdfs, new Path(interPath), hdfs, new Path(distributedPath), false, conf, null);
        } catch (IOException e) {
           System.err.println(e.getMessage());
        }

        conf.set("tmp_file_path", distributedPath);
        conf.set("mapred.TextOutputFormat.separator", ",");

        Job job2 = Job.getInstance(conf, "KNeighborhood means");
        job2.setJarByClass(KNeighborhoodScore.class);
        job2.setMapperClass(KNeighborhoodScore.KNeighborhoodMapper.class);
        job2.setCombinerClass(KNeighborhoodScore.KNeighborhoodReducer.class);
        job2.setReducerClass(KNeighborhoodScore.KNeighborhoodReducer.class);
        job2.setOutputKeyClass(Text.class);
        job2.setOutputValueClass(FloatWritable.class);
        job2.setInputFormatClass(FullFileInput.FullFileAsInput.class);

        FileInputFormat.addInputPath(job2, new Path(inPath));
        FileOutputFormat.setOutputPath(job2, new Path(outPath));

        System.exit(job2.waitForCompletion(true) ? 0 : 1);
    }
}
