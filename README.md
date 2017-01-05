# witan.gateway

CQRS HTTP gateway

## Usage

### Production
```
lein uberjar
java -jar target/witan.gateway-standalone.jar
```

### Development
```
docker-compose up -d
lein run -m witan.gateway.system development
```

## License

Copyright © MastodonC Ltd
