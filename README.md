# witan.gateway

Gateway/BFF for the Kixi infrastructure (aka Witan).
Uses CQRS.
Here be dragons.

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

## License

Copyright © MastodonC Ltd
