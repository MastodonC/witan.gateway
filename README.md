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
