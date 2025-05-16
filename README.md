### Dropwizard Application Errors

[![Build](https://github.com/kiwiproject/dropwizard-application-errors/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/kiwiproject/dropwizard-application-errors/actions/workflows/build.yml?query=branch%3Amain)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_dropwizard-application-errors&metric=alert_status)](https://sonarcloud.io/dashboard?id=kiwiproject_dropwizard-application-errors)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_dropwizard-application-errors&metric=coverage)](https://sonarcloud.io/dashboard?id=kiwiproject_dropwizard-application-errors)
[![CodeQL](https://github.com/kiwiproject/dropwizard-application-errors/actions/workflows/codeql.yml/badge.svg)](https://github.com/kiwiproject/dropwizard-application-errors/actions/workflows/codeql.yml)
[![javadoc](https://javadoc.io/badge2/org.kiwiproject/dropwizard-application-errors/javadoc.svg)](https://javadoc.io/doc/org.kiwiproject/dropwizard-application-errors)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/org.kiwiproject/dropwizard-application-errors)](https://central.sonatype.com/artifact/org.kiwiproject/dropwizard-application-errors/)


Dropwizard Application Errors is a library that provides Dropwizard applications with a simple
way to record and search on application-level errors.

### Installation

Maven:

```xml
<dependency>
    <groupId>org.kiwiproject</groupId>
    <artifactId>dropwizard-application-errors</artifactId>
    <version>[version]</version>
</dependency>
```

Gradle:

```groovy
implementation group: 'org.kiwiproject', name: 'dropwizard-application-errors', version: '[version]'
```

### Basic Usage

In the `run` method of your Dropwizard `Application` class, set up the `ApplicationErrorDao`.

```java
// Build an error DAO. This one uses an in-memory H2 database.
var serviceDetails = ServiceDetails.from(theHostname, theIpAddress, thePortNumber);
var errorContext = ErrorContextBuilder.newInstance()
        .environment(theDropwizardEnvironment)
        .serviceDetails(serviceDetails)
        .buildInMemoryH2();
ApplicationErrorDao errorDao = errorContext.errorDao();
```

Then also in your `Application#run` method, pass the errorDao to other
objects (JakartaEE Resources, service classes, etc.):

```java
var weatherResource = new WeatherResource(weatherService, errorDao);
environment.jersey().register(weatherResource);
```        

In classes that want to record application errors, you can use the
`ApplicationErrorDao` to save errors:

```java
import org.kiwiproject.dropwizard.error.model.ApplicationError;

var anError = ApplicationError.builder()
        .description("An error occurred getting weather from service " + weatherService.getName())
        // set other properties
        .build();
errorDao.insertOrIncrementCount(anError);
```

You can also use any of the convenience factory methods in `ApplicationErrors` to both
log (using an SLF4J `Logger`) and save the error:

```java
ApplicationErrors.logAndSaveApplicationError(
        errorDao,
        LOG,
        exception, 
        "An error occurred updating getting weather from service {}", weatherService.getName());
```

Or, you can use `ApplicationErrorThrower`, which avoids passing the `ApplicationErrorDao` and `Logger`
to every invocation:

```java
// Store in an instance field, usually in a constructor
this.errorThrower = new ApplicationErrorThrower(errorDao, LOG);

// In methods that want to record application errors
errorThrower.logAndSaveApplicationError(exception,
        "An error occurred updating getting weather from service {}", weatherService.getName());
```

### HTTP Endpoints       

By default, the `ApplicationErrorResource` is registered with Jersey.
It provides HTTP endpoints to find and resolve errors.

### Health Check

A health check is registered by default, which checks that there aren't
any application errors in the last 15 minutes. You can change the time period as necessary.
                
### Testing

This library also provides a JUnit Jupiter extension, `ApplicationErrorExtension` which ensures
the persistent host information is setup correctly for tests. It also provides Mockito test helpers
to provide argument matchers and verifications.

### UTC Time Zone Requirement

This library currently _requires_ the JVM and database to use UTC as their time zone.
Otherwise the timestamp fields `createdAt` and `updatedAt` in `ApplicationError` may not
be saved or retrieved correctly from the database.

In addition, some methods in `ApplicationErrorDao` that accept `ZonedDateTime` objects
may not work as expected, as well as the `RecentErrorsHealthCheck`.

This requirement for UTC impacts test execution, specifically the JVM running the tests
must set the default time zone to UTC. When running via Maven, this is handed transparently
by adding `-Duser.timezone=UTC` to the Maven Surefire plugin. IntelliJ automatically picks
this property up when running tests in the IDE as opposed to via Maven, so it works as expected.

Unfortunately, VSCode does _not_ do this when using _Language Support for Java(TM) by Red Hat_.
To fix this, create a _workspace_ setting for the Java test configuration to add this system
property to the VM arguments in `.vscode/settings.json`

```json
{
  "java.test.config": {
    "vmArgs": [
      "-Duser.timezone=UTC"
    ]
  }
}
```
