# Got the makefile to run on aws from Prof. Merik's class students
HADOOP_PATH=/usr/lib/hadoop
MY_CLASSPATH=`yarn classpath`
jar.name=MainA2.jar
jar.path=target/$(jar.name)
local.input=input/books
local.logs=A3logs
local.output=output_big
job.name=KNeighborhood
# AWS EMR Execution
aws.emr.release=emr-5.8.0
aws.region=<your aws region>
aws.bucket.name=<your s3 bucket name>
aws.subnet.id=<your-subnet-id>
aws.input=<input folder name on s3>
aws.output=<output folder>
aws.log.dir=log
aws.num.nodes=<number of nodes>
aws.instance.type=<instance type eg- m3.xlarge>

build:  clean compile
	mkdir _build
	mkdir target
	javac -classpath $(MY_CLASSPATH):./src -d _build ./src/*.java
	jar -cvf ./target/MainA2.jar -C _build/ .
	rm -rf ./_build

compile:
	javac -cp $(MY_CLASSPATH) -d classes src/*.java

run:
	$(HADOOP_HOME)/bin/hadoop jar ./target/MainA2.jar org.myorg.MainA2 $(INPUT) $(OUTPUT) $(NEIGHBORS)

jar:
	cp -r src/META-INF/MANIFEST.MF classes
	cd classes; jar cvmf MANIFEST.MF MainA2.jar
	mv classes/MainA2.jar .


	
# Removes local output directory.
clean-local-output:
	rm -rf ${local.output}*

clean:
	rm -rf ./target
	rm -rf ./_build





# Create S3 bucket.
make-bucket:
	aws s3 mb s3://${aws.bucket.name}

# Upload data to S3 input dir.
upload-input-aws: make-bucket
	aws s3 sync ${local.input} s3://${aws.bucket.name}/${aws.input}
	
# Delete S3 output dir.
delete-output-aws:
	aws s3 rm s3://${aws.bucket.name}/ --recursive --exclude "*" --include "${aws.output}*"

# Upload application to S3 bucket.
upload-app-aws:
	aws s3 cp ${jar.path} s3://${aws.bucket.name}

# Main EMR launch.
cloud: jar upload-app-aws delete-output-aws
	aws emr create-cluster \
		--name "In Mapper Cluster" \
		--release-label ${aws.emr.release} \
		--instance-groups '[{"InstanceCount":${aws.num.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.instance.type}"},{"InstanceCount":1,"InstanceGroupType":"MASTER","InstanceType":"${aws.instance.type}"}]' \
	    --applications Name=Hadoop \
	    --steps '[{"Args":["${job.name}","s3://${aws.bucket.name}/${aws.input}","s3://${aws.bucket.name}/${aws.output}"],"Type":"CUSTOM_JAR","Jar":"s3://${aws.bucket.name}/${jar.name}","ActionOnFailure":"TERMINATE_CLUSTER","Name":"Custom JAR"}]' \
		--log-uri s3://${aws.bucket.name}/${aws.log.dir} \
		--service-role EMR_DefaultRole \
		--ec2-attributes InstanceProfile=EMR_EC2_DefaultRole,SubnetId=${aws.subnet.id} \
		--region ${aws.region} \
		--enable-debugging \
		--auto-terminate

# Download output from S3.
download-output-aws: clean-local-output
	mkdir ${local.output}
	aws s3 sync s3://${aws.bucket.name}/${aws.output} ${local.output}

download-logs: 
	mkdir ${local.logs}
	aws s3 sync s3://${aws.bucket.name}/${aws.log.dir} ${local.logs}



