## Assignment A3 for CS6240
### Fall 2017
### Shreysa Sharma 

###Directory Structure

`src` - Contains Kneighborhood.java associated with the implementation.

`README.md` - Description of the implementation and how to run

`Makefile` - Makefile to assist in building, running and managing the project.

`report.Rmd` - Report file in R-Markdown format.

`report.html` - HTML rendering of the above report.

`input/books` - Contains the input files that was provided for assignment A0 (taken from assignment task page).

`input/big-corpus` - contains an empty folder where the reviewer can place their big corpus files

`observations` - Contains csv files with the timings of Map reduce runs, log files of runs


###Instructions for building and running the program
1. Clone the repository on your system
2. run make gunzip
3. if s3 bucket is not already present then issue: make make-bucket
4. open the makefile and provide add the below details:
HADOOP_PATH=<your hadoop home>
MY_CLASSPATH=<your classpath>
jar.name=MainA2.jar
jar.path=target/$(jar.name)
local.input=input/books
local.logs=<path to where you want the logs>
local.output=<path to output directory>
job.name=KNeighborhood

aws.emr.release=<your emr version eg: emr-5.8.0>
aws.region=<your aws region>
aws.bucket.name=<your s3 bucket name>
aws.subnet.id=<your-subnet-id>
aws.input=<input folder name on s3>
aws.output=<output folder>
aws.log.dir=<log folder>
aws.num.nodes=<number of nodes>
aws.instance.type=<instance type eg- m3.xlarge>

5. to upload data to s3 input dir: upload-input-aws
6. on your local hadoop set up issue: make clean
  make build
7. to upload application to s3: upload-app-aws
8. to create and launch EMR : make cloud
9. to download data from s3: make download-output-aws
10. to download logs: make download-logs
11. If the emr launch doesnot happen then ssh to your EMR cluster, make clean, make build,
upload data to cluster master using scp or manual upload
12. if data is on cluster then copy to hadoop fs using : hadoop distcp s3://a3-emr/input_big/* input_big (here a3-emr is my bucket name and input_big is the folder where i have the big corpus files) 
13. if copied using scp then put data on Hadoop HDFS
14. Then run the job by : `make run INPUT=<input path? OUTPUT=<output path> NEIGHBORS=<KVALUE>
15. Collect the results and analyze
16. The analysis of time taken for run of the map reduce job that I executed on the big dataset provided for assignment A2 is placed in Observations/aws.csv.
17. make clean removes all the *.class files and the target folder, useful for a clean build eg. "make run".


### System requirements
1. make
2. pandoc
3. RMarkdown
4. ggplot


