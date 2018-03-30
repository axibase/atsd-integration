# AWS Route 53

This java program copies AWS Route53 Health Check attributes into entity tags on the target ATSD server.

![](images/entities-list.png)

![](images/entity.png)

## Build JAR file from source

Update packages.

```
sudo apt update
```

Install git and maven packages.

```
sudo apt install git maven
```

Create project directory.

```
mkdir ~/aws-route53-atsd
```

```
cd ~/aws-route53-atsd
```

Clone repository from github.

```
git clone -b aws-route53 https://github.com/axibase/atsd-integration
```

Build the `jar` executable using Maven.

```
cd ~/aws-route53-atsd/atsd-integration/
```

```
mvn clean package
```

The jar file will be created in the `target` directory and named `aws-route53-atsd-[version]-jar-with-dependencies.jar`

## Configure

Change directory to `~/aws-route53-atsd`

Create `aws.properties` file with AWS credentials.

```sh
nano aws.properties
```

```
aws.access.key=access_key
aws.secret.key=secret_key
aws.region=us-east-1
```

Create `atsd.properties` file with ATSD connection parameters and user credentials.

```sh
nano atsd.properties
```

```
# Replace atsd_hostname below with localhost if the ATSD is running in the same container
axibase.tsd.api.server.name=atsd_hostname
axibase.tsd.api.server.port=8443
axibase.tsd.api.protocol=https
axibase.tsd.api.ssl.errors.ignore=true
axibase.tsd.api.username=axibase
axibase.tsd.api.password=axibase
```

## Run

Run the program by executing the following command. 

Specify absolute path to `aws.properties` and `atsd.properties` files using `-Daws.properties` and `-Daxibase.tsd.api.client.properties` arguments. 

Replace `/home/axibase` with the absolute path to the current user's home directory.

```sh
pwd
```

```sh
java \
    -Daxibase.tsd.api.client.properties=/home/axibase/aws-route53-atsd/atsd.properties \
    -Daws.properties=/home/axibase/aws-route53-atsd/aws.properties \
    -jar /home/axibase/aws-route53-atsd/atsd-integration/target/aws-route53-atsd-1.0-jar-with-dependencies.jar
```

Login into ATSD user interface and verify that health check ids can be located on the Entities tab.

## Schedule

To upload Health Check attributes into ATSD on schedule, add the command to `cron` schedule.

```
crontab -e
```

Specify the schedule and the command. Replace `/home/axibase` as before.

```
@hourly java -Daxibase.tsd.api.client.properties=/home/axibase/aws-route53-atsd/atsd.properties -Daws.properties=/home/axibase/aws-route53-atsd/aws.properties -jar /home/axibase/aws-route53-atsd/atsd-integration/target/aws-route53-atsd-1.0-jar-with-dependencies.jar
```
