# witan.gateway

Gateway/BFF for the Kixi infrastructure (aka Witan).
Uses CQRS.
Here be dragons. Ones with fire.

### Production
```
lein uberjar
java -jar target/witan.gateway-standalone.jar
```

### Development
You will need AWS credentials in a file called 'aws-variables.env' alonside `docker-compose.yml`. There is a helper script in the `scripts` directory to duplicate AWS credentials from `.aws/credentials` into a correctly-formatted file.
```
docker-compose pull
docker-compose up
```

To add some user data, you should check out the `kixi.heimdall` project and run

```
lein seed development
```

### Command Partition Keys

All commands must specify a partition key, these are defined in the command-key->partition-key-fn map in the handler namespace. An exception is thrown if one is not provided for a command key, this is intended to force you to consider this aspect of any new command.

Partition keys are used by the event stream to ensure that messages for a given resource (file metadata, schema, etc) are delivered in order. Without the partition key being used for, say, adding and removing a user from the shares of a files metadata, the remove could be processed before the add. The parition key ensures the two messages are on the same shard or partition of the event stream and therefore should be processed in order by the back end services.

### Issues

## License

Copyright © MastodonC Ltd
