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



public class FullFileInput {

    public final static IntWritable one = new IntWritable(1);
    public static final int NUM_CHARACTERS = 26;
    public static final int ASCII_START_A = 97;

    // collects k neighborhood scores
    public static class KNeighborhoodMapper extends Mapper<LongWritable, Text, Text, FloatWritable> {

        private Text word = new Text();
        private Configuration conf;
        private Integer kVal;
        private Integer[] letterScores;
        private HashMap<String, Integer> wordScoresCache;

        public void setup(Context context) throws IOException, InterruptedException {
            conf = context.getConfiguration();
            String letterScoresFilePath = conf.get("tmp_file_path");
            kVal = conf.getInt("neighbors", 2);

            letterScores = new Integer[NUM_CHARACTERS];
            wordScoresCache = new HashMap<>();

            final Path file = new Path(letterScoresFilePath);
            FileSystem fs = file.getFileSystem(conf);

            FSDataInputStream fileIn = fs.open(file);
            LineReader lineReader = new LineReader(fileIn, conf);

            long position = 0;
            long endOfFile = fs.getFileStatus(file).getLen();

            while (position < endOfFile) {
                Text record = new Text();
                int bytesReadInThisLine = 0;

                // Read first line and store its content to "value"
                bytesReadInThisLine = lineReader.readLine(record, Integer.MAX_VALUE,
                        Math.max((int) Math.min(Integer.MAX_VALUE, endOfFile - position), Integer.MAX_VALUE));

                if (bytesReadInThisLine == 0) {
                    break;
                }

                position += bytesReadInThisLine;

                String line = record.toString();
                String[] split = line.split("\\s+");
                assert (split[0].length() == 1);
                int index = (int) split[0].toCharArray()[0] - ASCII_START_A;
                int score = Integer.parseInt(split[1]);
                letterScores[index] = score;
            }
        }

        public Integer getWordScore(String keyWord) {
            Integer wordScore = 0;
            if (wordScoresCache.containsKey(keyWord)) {
                wordScore = wordScoresCache.get(keyWord);
            } else {
                for (char c : keyWord.toCharArray()) {
                    int index = (int) c - ASCII_START_A;
                    wordScore += letterScores[index];
                }
                wordScoresCache.put(keyWord, wordScore);
            }
            return wordScore;
        }


        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] tokens = value.toString()
                    .toLowerCase()
                    .replaceAll("[^a-z\\s]", "")
                    .split("\\s+");

            if (tokens.length > 1) {
                for (int i = kVal; i < (tokens.length - kVal); i++) {
                    String keyWord = tokens[i];
                    word.set(keyWord);

                    int start = (i - kVal < 0) ? 0 : i - kVal;
                    int end = (i + kVal >= tokens.length) ? tokens.length - 1 : i + kVal;
                    int neighborhoodScore = 0;
                    for (int j = start; j <= end; j++) {
                        if (j == i) continue;
                        String neighboringWord = tokens[j];
                        // This is to extract the buffer words added at the start and
                        // end of a file - Of course, this implies that any word 'null'
                        // in the actual file also gets ignored.
                        if (neighboringWord.equals("null"))
                            continue;

                        Integer wordScore = getWordScore(neighboringWord);
                        neighborhoodScore += wordScore;
                    }
                    context.write(word, new FloatWritable((float) neighborhoodScore));
                }
            }
        }

    }

    // to take the entire file as input to 1 mapper
    public static class FullFileAsInput extends FileInputFormat<LongWritable, Text> {
        @Override
        public RecordReader<LongWritable, Text> createRecordReader(
                InputSplit split, TaskAttemptContext context) throws IOException,
                InterruptedException {
            return new LineRecordToFileReader();
        }

        // Disallows splitting of file
        @Override
        protected boolean isSplitable(JobContext context, Path fileName) {
            return false;
        }

    }


    // Derived from implementations described in:
    // https://hadoopi.wordpress.com/2013/05/27/understand-recordreader-inputsplit/
    // http://analyticspro.org/2012/08/01/wordcount-with-custom-record-reader-of-textinputformat/
    // adds k ghost words in front and end of each line fed to mapper
    public static class LineRecordToFileReader extends RecordReader<LongWritable, Text> {
        private long start;
        private long position;
        private long prevPos;
        private long endOfFile;
        private LineReader lineReader;
        private int maxLineLength;
        private Integer kVal;
        private LongWritable key = new LongWritable();
        private Text value = new Text();
        private boolean doneReadingFile = true;
        private FSDataInputStream fileIn;
        private Configuration job;


        @Override
        public void initialize(InputSplit genericSplit, TaskAttemptContext context) throws IOException {


            //For FileInputSplit
            FileSplit split = (FileSplit) genericSplit;


            job = context.getConfiguration();

            //sets the maximum bytes allowed for a record
            this.maxLineLength = job.getInt("mapred.linerecordreader.maxlength", Integer.MAX_VALUE);
            this.kVal = job.getInt("neighbors", 2);


            //setting start and endOfFile positions in split
            start = split.getStart();
            endOfFile = start + split.getLength();

            final Path file = split.getPath();
            FileSystem fs = file.getFileSystem(job);
            fileIn = fs.open(split.getPath());

            //if the start of split is not from byte 0 then the first line shall be ignored
            // as it would have already been processed by the previous split
            boolean skipFirstLine = false;

            if (start != 0) {
                skipFirstLine = true;

                //The file pointer is set to start - 1 position so that no lines are missed in case
                // start is at the endOfFile of line
                --start;
                fileIn.seek(start);
            }

            lineReader = new LineReader(fileIn, job);


            //put first line into a temp text variable if skipFirstLine is true
            if (skipFirstLine) {
                Text temp = new Text();

                //set start to 'start + lineOffset'
                start += lineReader.readLine(temp, 0,
                        (int) Math.min(
                                (long) Integer.MAX_VALUE,
                                endOfFile - start));
            }

            //set the position to actual start;
            this.position = start;
            this.prevPos = start;
        }

        public String[] getSplitWords(Text readLine) {
            return readLine.toString()
                    .toLowerCase()
                    .replaceAll("[^a-z\\s]", "")
                    .split("\\s+");
        }

        public int getGhosts(List<String> ghostWords, boolean ghostStart) throws IOException {

            long localPos = position;
            long endPos = endOfFile;

            if (ghostStart) {
                fileIn.seek(prevPos);
                localPos = prevPos;
                lineReader = new LineReader(fileIn, job);
            }

            int numLinesRead = 0;

            while (localPos < endPos) {
                Text record = new Text();
                int bytesReadInThisLine = 0;

                // Read first line and store its content to "value"
                bytesReadInThisLine = lineReader.readLine(record, maxLineLength,
                        Math.max((int) Math.min(Integer.MAX_VALUE, endPos - localPos), maxLineLength));

                ++numLinesRead;

                // No byte read, seems that we reached end of Split
                // Break and return false (no key / value)
                if (bytesReadInThisLine == 0) {
                    break;
                }

                String[] words = getSplitWords(record);
                boolean exitLoop = false;
                if (!ghostStart) {
                    for (String word : words) {
                        ghostWords.add(word);
                        if (ghostWords.size() >= this.kVal) {
                            exitLoop = true;
                            break;
                        }
                    }
                } else {
                    for (int i = words.length - 1; i >= 0; i--) {
                        ghostWords.add(words[i]);
                        if (ghostWords.size() >= this.kVal) {
                            exitLoop = true;
                            break;
                        }
                    }
                }

                if (exitLoop) {
                    break;
                }

                // Line is read, new position is set
                localPos += bytesReadInThisLine;
            }

            if (!ghostStart && (ghostWords.size() < this.kVal)) {
                String empty = "NULL"; // Hack to pad the buffers
                for (int i = 0; i < (this.kVal - ghostWords.size()); i++) {
                    ghostWords.add(empty);
                }
            }

            if (ghostStart) {
                prevPos = position;
            }
            fileIn.seek(position);
            lineReader = new LineReader(fileIn, job);

            return numLinesRead;
        }


        @Override
        public boolean nextKeyValue() throws IOException {
            key.set(position);
            value.clear();
            int totalBytesRead = 0;
            final Text endLine = new Text(" ");

            final Text spaceString = new Text(" ");

            boolean initialGhosts = false;
            boolean endingGhosts = false;

            // If its at the start of the file add k-ghosts
            //
            if (this.position == this.start) {
                String empty = "NULL"; // Hack to pad the buffers
                for (int i = 0; i < this.kVal; i++) {
                    value.append(empty.getBytes(), 0, empty.length());
                    value.append(spaceString.getBytes(), 0, spaceString.getLength());
                }
                initialGhosts = true;
            }

            List<String> ghostWordsBefore = new ArrayList<>();
            List<String> ghostWordsAfter = new ArrayList<>();
            int numLinesForGhostWords = 2;

            // Add ghosts at the start of the current record
            if (!initialGhosts) {
                numLinesForGhostWords = getGhosts(ghostWordsBefore, true);
                for (String ghostWord : ghostWordsBefore) {
                    value.append(ghostWord.getBytes(), 0, ghostWord.length());
                    value.append(spaceString.getBytes(), 0, spaceString.getLength());
                }
            }

            for (int i = 0; i < 3 * numLinesForGhostWords; i++) {
                // Make sure we get at least one record that starts in this Split
                while (position < endOfFile) {
                    Text record = new Text();
                    int bytesReadInThisLine = 0;
                    // Read first line and store its content to "value"
                    bytesReadInThisLine = lineReader.readLine(record, maxLineLength,
                            Math.max((int) Math.min(
                                    Integer.MAX_VALUE, endOfFile - position),
                                    maxLineLength));

                    value.append(record.getBytes(), 0, record.getLength());
                    value.append(spaceString.getBytes(), 0, spaceString.getLength());

                    // No byte read, seems that we reached end of Split
                    // Break and return false (no key / value)
                    if (bytesReadInThisLine == 0) {
                        break;
                    }

                    // Line is read, new position is set
                    position += bytesReadInThisLine;
                    totalBytesRead += bytesReadInThisLine;

                    if (bytesReadInThisLine < maxLineLength) {
                        break;
                    }
                }
            }

            if (!endingGhosts) {
                numLinesForGhostWords = getGhosts(ghostWordsAfter, false);
                for (String ghostWord : ghostWordsAfter) {
                    value.append(ghostWord.getBytes(), 0, ghostWord.length());
                    value.append(spaceString.getBytes(), 0, spaceString.getLength());
                }
            }

            if (totalBytesRead == 0) {
                // We've reached end of Split
                key = null;
                value = null;
                return false;
            } else {
                // Tell Hadoop a new line has been found
                // key / value will be retrieved by
                // getCurrentKey getCurrentValue methods
                return true;
            }
        }


        @Override
        public LongWritable getCurrentKey() throws IOException,
                InterruptedException {
            return key;
        }


        @Override
        public Text getCurrentValue() throws IOException, InterruptedException {
            return value;
        }


        @Override
        public float getProgress() throws IOException, InterruptedException {
            if (start == endOfFile) {
                return 0.0F;
            } else {
                return Math.min(1.0F, (position - start) / (float) (endOfFile - start));
            }
        }


        @Override
        public void close() throws IOException {
            if (lineReader != null) {
                lineReader.close();
            }
        }
    }
}