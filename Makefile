
clean:
	mvn clean

package: clean
	mvn -B package -DskipTests=true

deploy: package
	mvn -B deploy -DskipTests=true
