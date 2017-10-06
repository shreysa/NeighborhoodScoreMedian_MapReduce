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

public  class LetterScore {
    public final static IntWritable one = new IntWritable(1);
    public static final int NUM_CHARACTERS = 26;
    public static final int ASCII_START_A = 97;

    /*
    LetterScoreMapper emits the letter occurances
     */
    public static class LetterScoreMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
        private Text word = new Text();

        // removes all punctuations and provides letter occurances
        public void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String line = value.toString();
            String[] tokens = value.toString()
                    .toLowerCase()
                    .replaceAll("[^a-z\\s]", "")
                    .split("\\s+");

            for (String token : tokens) {
                for (Character c : token.toCharArray()) {
                    word.set(c.toString());
                    context.write(word, one);
                }
            }
        }
    }

    //provides total occurances of letters
    public static class LetterScoreCombiner extends Reducer<Text, IntWritable, Text, IntWritable> {

        public void reduce(Text key, Iterable<IntWritable> values, Context context)
                throws IOException, InterruptedException {
            int sum = 0;
            for (IntWritable val : values) {
                sum += val.get();
            }
            context.write(key, new IntWritable(sum));
        }
    }

    // provides total ocurrances and assigns scores as per requirement of A0
    public static class LetterScoreReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        private Configuration conf;
        private Map<String, Integer> letterOccurances;

        public void setup(Context context) throws IOException, InterruptedException {
            conf = context.getConfiguration();
            letterOccurances = new TreeMap<>();
	    for (int i = 0; i < NUM_CHARACTERS; i++) {
                Character letter = (char) (i + ASCII_START_A);
		letterOccurances.put(letter.toString(), 0);
	    }
        }

        public void reduce(Text key, Iterable<IntWritable> values, Context context)
                throws IOException, InterruptedException {
            int sum = 0;
            for (IntWritable val : values) {
                sum += val.get();
            }
            letterOccurances.put(key.toString(), sum);
        }

        public void cleanup(Context context) throws IOException, InterruptedException {
            long totalCharacters = 0;
            for (Integer val : letterOccurances.values()) {
                totalCharacters += val;
            }

            Integer[] letterScore = new Integer[NUM_CHARACTERS];
            for (int i = 0; i < NUM_CHARACTERS; i++) {
                Character letter = (char) (i + ASCII_START_A);
		System.out.println("Letter: " + i);
                float letterOccurance = (float) letterOccurances.get(letter.toString());
                Float percentageOccurrence = letterOccurance / (float) totalCharacters * 100.0F;

                if (percentageOccurrence >= 10.0) letterScore[i] = 0;
                else if (percentageOccurrence >= 8.0) letterScore[i] = 1;
                else if (percentageOccurrence >= 6.0) letterScore[i] = 2;
                else if (percentageOccurrence >= 4.0) letterScore[i] = 4;
                else if (percentageOccurrence >= 2.0) letterScore[i] = 8;
                else if (percentageOccurrence >= 1.0) letterScore[i] = 16;
                else letterScore[i] = 32;
            }

            for (int i = 0; i < NUM_CHARACTERS; i++) {
                Character letter = (char) (i + ASCII_START_A);
                context.write(new Text(letter.toString()), new IntWritable(letterScore[i]));
            }
        }
    }
}
