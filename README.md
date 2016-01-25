#Mailgun example

Play! based implementation of a transaction email client using Mailgun.

##Instructions:

###Requirements:
Apache Kafka (0.9.0 /w 2.11 was used for testing.)

###Running Kafka: (relies on wget, obtainable via Homebrew)
    wget 'http://www.us.apache.org/dist/kafka/0.9.0.0/kafka_2.11-0.9.0.0.tgz'
    tar xvfz kafka_2.11-0.9.0.0.tgz

    cd kafka_2.11-0.9.0.0

Kafka makes use of Apache ZooKeeper for load balancing / resilience etc.

(Run each of the following commands in a separate command line tab)

    bin/zookeeper-server-start.sh config/zookeeper.properties
    bin/kafka-server-start.sh config/server.properties
    bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic test

Using the last tab, issue the following command:

    bin/kafka-topics.sh --list --zookeeper localhost:2181

You should see test listed.

Using the last tab, run

    bin/kafka-console-producer.sh --broker-list localhost:9092 --topic test

You now have a console to push JSON messages in to the queue to be read by the application.
Please note that the console only supports single line messages, while the application itself is agnostic.


Running the application:
The application issues a connection to the Zookeeper during startup time, so if Zookeeper is not available the application
will not start successfully. It will handle disconnects and reconnects reasonably gracefully after this point, however
without the initial connection it will not bootstrap itself.

To run the application, run activator run from the root of the directory.
This could take a long time to resolve dependencies via SBT.
After this process is finished, visit http://localhost:9000 to start the application.
Note this is running in development mode so the app will not be bootstrapped at all until the first request is received (this would not be the case in production).


Step based Instructions:
Step 1: Run ./basic.json <jsonfile> to upload a simple JSON file via the REST endpoint, for example ./basic.json basic.json
Step 2: Run ./template.json <jsonfile> to upload a JSON file referencing a template, for example ./template.sh template.json
Step 3: There are two end points available, available via HTTP post at /simple and /template. The first two steps leverage this functionality.
Currently it uses HTTP basic auth (the default username and password are a:a), as this is fairly trivial to implement and more importantly removes
any need to build user management and storage in to the application. It also integrates well with command line tools like curl
Step 4: Post a message in to the tab you previously run bin/kafka-console-producer.sh on. You should see output in the logs to indicate that a message was sent or failed to send.
Please note that this only supports single line messages to the way the producer consumes the input.




