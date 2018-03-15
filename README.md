# AWS Route 53

This java program copies AWS Route53 Health Check attributes into entity tags on the target ATSD server.

![](images/entities-list.png)

![](images/entity.png)

## Build JAR file from source

Clone repository

```
cd /home
mkdir aws-route53-atsd
cd aws-route53-atsd
git clone -b aws-route53 https://github.com/axibase/atsd-integration
cd atsd-integration/
```

Build jar executable using Maven

```
mvn clean package
```

The jar file will be created in the `target` directory and named `aws-route53-atsd-[version]-jar-with-dependencies.jar`

## Configure

Change directory to `/home/aws-route53-atsd`

Create `aws.properties` file with AWS credentials

```
aws.access.key=access_key
aws.secret.key=secret_key
aws.region=us-east-1
```

Create `atsd.properties` file with ATSD credentials.

```
axibase.tsd.api.server.name=atsd_hostname
axibase.tsd.api.server.port=8443
axibase.tsd.api.protocol=https
axibase.tsd.api.ssl.errors.ignore=true
axibase.tsd.api.username=axibase
axibase.tsd.api.password=axibase
```

## Run

Run the program by executing the following command. Specify absolute path to `aws.properties` and `atsd.properties` files using `-Daws.properties` and `-Daxibase.tsd.api.client.properties` arguments.

```
java -Daxibase.tsd.api.client.properties=/home/aws-route53-atsd/atsd.properties -Daws.properties=/home/aws-route53-atsd/aws.properties -jar /home/aws-route53-atsd/atsd-integration/target/aws-route53-atsd-1.0-jar-with-dependencies.jar
```

Login into ATSD user interface and search for entities by entering Health Check id in the Entity search form.

## Schedule

To upload Health Check attributes into ATSD on schedule, add the command to `cron`.

```
crontab -e
```

Specify the schedule and the command.

```
@hourly java -Daxibase.tsd.api.client.properties=/home/aws-route53-atsd/atsd.properties -Daws.properties=/home/aws-route53-atsd/aws.properties -jar /home/aws-route53-atsd/atsd-integration/target/aws-route53-atsd-1.0-jar-with-dependencies.jar
```
