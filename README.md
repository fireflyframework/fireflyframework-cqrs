# Firefly Framework - CQRS

[![CI](https://github.com/fireflyframework/fireflyframework-cqrs/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-cqrs/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> CQRS pattern implementation with reactive command/query buses, execution contexts, authorization, validation, and metrics.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework CQRS provides a production-ready implementation of the Command Query Responsibility Segregation pattern for reactive Spring Boot microservices. It separates read (query) and write (command) operations through dedicated buses, handlers, and execution contexts.

The library features annotation-driven handler registration (`@CommandHandlerComponent`, `@QueryHandlerComponent`), fluent command/query builders, pluggable authorization with `@CustomAuthorization`, automatic Bean Validation integration, and comprehensive metrics collection. It supports caching at the query handler level and includes Actuator endpoints for monitoring CQRS operations.

The execution context propagates correlation IDs, tenant information, and security context across command and query processing pipelines, enabling full traceability in distributed environments.

## Features

- Reactive `CommandBus` and `QueryBus` with type-safe handler dispatch
- `@CommandHandlerComponent` and `@QueryHandlerComponent` annotations for auto-registration
- `Command<R>` and `Query<R>` marker interfaces with response types
- Context-aware handlers via `ContextAwareCommandHandler` and `ContextAwareQueryHandler`
- `ExecutionContext` with correlation ID, tenant, and security context propagation
- Fluent `CommandBuilder` and `QueryBuilder` APIs
- Authorization framework with `@CustomAuthorization` and `AuthorizationService`
- Automatic Bean Validation on commands and queries
- Query-level caching with `@Cacheable` and `@CacheEvict`
- CQRS health indicator and metrics endpoint for Actuator
- Configurable properties via `CqrsProperties` and `AuthorizationProperties`
- Generic type resolution for handler-to-command/query matching

## Requirements

- Java 21+
- Spring Boot 3.x
- Maven 3.9+

## Installation

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cqrs</artifactId>
    <version>26.02.07</version>
</dependency>
```

## Quick Start

```java
import org.fireflyframework.cqrs.command.*;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;

// Define a command
public record CreateAccountCommand(String name, String email) implements Command<AccountId> {}

// Implement the handler
@CommandHandlerComponent
public class CreateAccountHandler implements CommandHandler<CreateAccountCommand, AccountId> {

    @Override
    public Mono<AccountId> handle(CreateAccountCommand command) {
        return accountService.create(command.name(), command.email());
    }
}

// Dispatch from a controller
@RestController
public class AccountController {

    private final CommandBus commandBus;

    @PostMapping("/accounts")
    public Mono<AccountId> create(@RequestBody CreateAccountCommand command) {
        return commandBus.dispatch(command);
    }
}
```

## Configuration

```yaml
firefly:
  cqrs:
    validation:
      enabled: true
    authorization:
      enabled: true
    metrics:
      enabled: true
    cache:
      enabled: true
```

## Documentation

Additional documentation is available in the [docs/](docs/) directory:

- [Quickstart](docs/QUICKSTART.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Configuration](docs/CONFIGURATION.md)
- [Developer Guide](docs/DEVELOPER_GUIDE.md)
- [Event Sourcing Integration](docs/EVENT_SOURCING_INTEGRATION.md)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Solutions Inc.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
