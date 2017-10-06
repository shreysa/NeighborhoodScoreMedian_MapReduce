HADOOP_HOME = /usr/lib/hadoop
MY_CLASSPATH = `yarn classpath`
ifndef NEIGHBORS
NEIGHBORS=2
endif

build:	clean compile
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
	cd classes; jar cvmf MANIFEST.MF MainA2.jar *
	mv classes/MainA2.jar . 

clean:
	rm -rf ./target
	rm -rf ./_build

gzip:
	-gzip -q ./input/books/*

gunzip:
	-gunzip -q ./input/books/*

