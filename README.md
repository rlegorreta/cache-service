# <img height="25" src="./images/AILLogoSmall.png" width="40"/> cache-service

<a href="https://www.legosoft.com.mx"><img height="150px" src="./images/Icon.png" alt="AI Legorreta" align="left"/></a>
Microservice that acts as the cache memory for very frequent REST calls and therefore increase the performance.

The `cache-service` utilizes as memory cache the Redis database. Others REST calls in the future will be added in order
to increase performance. This is a common good microservice common pattern.

note: this microservice depends with the Kafka and audit-service.


## Introduction

This is a [Event Driven](https://en.wikipedia.org/wiki/Event-driven_architecture) application, the cache service
receives "very frequent" REST calls. The first time this microservice forwards the call to the actual microservice and 
stores the result in memory using Redis database, subsequent calls are read from Redis database to increase performance.

To keep in sync the Redis database with the server repo, the `cache-ervice` listens events from the microservice that
is reads data; these events 'invalidates' Redis memory database so when a next Rest call is received the data is 
refreshed from the database and then stored again in the cache memory Redis database.

The application listen to events from the microservice it caches without the postfix -audit (i.e., after the
audit-service has been logged.

This `cache-service` utilizes kafka event machine microservice created previously in the docker directory
or in kubernetes (and zookeeper for previous Kafka versions).

This microservice is just the initial concept for cache performance. It just caches de paramDB and just
some tables (not all). Future version, and depending on the Customer needs more database and tables
will be added in the Redis database.


## How to use it

### Send events

Send a Event via  **Kafka**

#### Event Request

The **Event Request** is a **JSON** with the follow properties:

* **username:** The name of the user that generates the event.
* **correlationId:** This id helps to a listener application to follow a track of events. This id is generated by the Zuul API Gateway and keep the same until all micro services involved in the transaction finished.  It is important this trasaction for the Zipkin and Graylog montior servers.
* **eventType:** This event type tell if our event will be stored or not, its a **enum** that accepts these values:
  * DB_STORE: Store only into a data base.
  * FILE_STORE: Store only into a .TXT file.
  * FULL_STORE: Store into a data base and .TXT file.
  * NON_STORE: No store the event.
* **eventName:** This is the name of the event for example: *saveUser.*
* **applicationName**: Is the name of the micro service or front end application that generates the event.
* **coreName**: Is the group which it belongs the application name.  In other words is the topic that the listener will suscribe.
* **body:** The body is the action of the event, if we create a user the body is the result of create a new user.

*__Example:__*

```json
{
    "username":"user1",
    "correlationId": "saveUser_1",
    "eventType":"FULL_STORE",
    "eventName":"saveUser",
    "applicationName":"userServices",
    "eventBody":{
        "notificaFacultad": "NOTIFICA_IAM",
        "datos": {
          "interno": false, 
          "activo": true, 
          "administrador": false, 
          "id": 55, 
          "idUsuario": 501, 
          "nombreUsuario": "adACME", 
          "nombre": "Mickey", 
          "apellido": "Mouse", 
          "telefono": "5591495040", 
          "mail": "admin@esmas.com.mx",
          "fechaIngreso":"2022-04-18", 
          "zonaHoraria":"America/Mexico", 
          "usuarioModificacion":"adminACME", 
          "fechaModificacion":2022-04-22T17:27:14.899735009, 
          "grupos": [], 
          "companias":[],
          "areas":[], 
          "name":"adACME", 
          "attributes": {}, 
          "fullName":"Mickey Mouse", 
          "zoneInfo":"America/Mexico", 
          "authorities":[], 
          "givenName"="Mickey", 
          "familyName"="Mouse", 
          "phoneNumber":"5591495040", 
          "preferredUsername":"adACME", 
          "updatedAt":2022-04-22T17:27:14.899735009Z, 
          "claims": {}, 
          "email=admin@esmas.com.mx", 
          "address": {}
        }
    }
}
```

#### Kafka

##### Produce

This microservice does NOT produce event, it is just a listener


###### Dependencies

We need add the **spring-cloud stream** and **kafka** dependencies to our **pom.xml:**

```
	implementation("org.springframework.cloud:spring-cloud-stream")
    implementation("org.springframework.cloud:spring-cloud-stream-binder-kafka")
```

### Redis database

This microservice utilizes redis to store cache in memory.

For more information see chapter 10 Event-driven architecture with Spring Cloud Stream in Spring Microservices in Action 
book (2nd edition)

  
### Create the image manually

```
./gradlew bootBuildImage
```

### Publish the image to GitHub manually

```
./gradlew bootBuildImage \
   --imageName ghcr.io/rlegorreta/cache-service \
   --publishImage \
   -PregistryUrl=ghcr.io \
   -PregistryUsername=rlegorreta \
   -PregistryToken=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
```

### Publish the image to GitHub from the IntelliJ

To publish the image to GitHub from the IDE IntelliJ a file inside the directory `.github/workflows/commit-stage.yml`
was created.

To validate the manifest file for kubernetes run the following command:

```
kubeval --strict -d k8s
```

This file compiles de project, test it (for this project is disabled for some bug), test vulnerabilities running
skype, commits the code, sends a report of vulnerabilities, creates the image and lastly push the container image.

<img height="340" src="./images/commit-stage.png" width="550"/>

For detail information see `.github/workflows/commit-stage.yml` file.


### Run the image inside the Docker desktop

```
docker run \
    --net ailegorretaNet \
    -p 8351:8351 \
    -e SPRING_PROFILES_ACTIVE=local \
    cache-service
```

Or a better method use the `docker-compose` tool. Go to the directory `ailegorreta-deployment/docker-platform` and run
the command:

```
docker-compose up
```

## Run inside Kubernetes

### Manually

If we do not use the `Tilt`tool nd want to do it manually, first we need to create the image:

Fist step:

```
./gradlew bootBuildImage
```

Second step:

Then we have to load the image inside the minikube executing the command:

```
image load ailegorreta/cache-service --profile ailegorreta 
```

To verify that the image has been loaded we can execute the command that lists all minikube images:

```
kubectl get pods --all-namespaces -o jsonpath="{..image}" | tr -s '[[:space:]]' '\n' | sort | uniq -c\n
```

Third step:

Then execute the deployment defined in the file `k8s/deployment.yml` with the command:

```
kubectl apply -f k8s/deployment.yml
```

And after the deployment can be deleted executing:

```
kubectl apply -f k8s/deployment.yml
```

Fourth step:

For service discovery we need to create a service applying with the file: `k8s/service.yml` executing the command:

```
kubectl apply -f k8s/service.yml
```

And after the process we can delete the service executing:

```
kubectl deltete -f k8s/service.yml
```

Fifth step:

If we want to use the project outside kubernetes we have to forward the port as follows:

```
kubectl port-forward service/config-service 8351:80
```

Appendix:

If we want to see the logs for this `pod` we can execute the following command:

```
kubectl logs deployment/cache-service
```

### Using Tilt tool

To avoid all these boilerplate steps is much better and faster to use the `Tilt` tool as follows: first create see the
file located in the root directory of the proyect called `TiltFile`. This file has the content:

```
# Tilt file for config-service
# Build
custom_build(
    # Name of the container image
    ref = 'cache-service',
    # Command to build the container image
    command = './gradlew bootBuildImage --imageName $EXPECTED_REF',
    # Files to watch that trigger a new build
    deps = ['build.gradle', 'src']
)

# Deploy
k8s_yaml(['k8s/deployment.yml', 'k8s/service.yml'])

# Manage
k8s_resource('config-service', port_forwards=['8351'])
```

To execute all five steps manually we just need to execute the command:

```
tilt up
```

In order to see the log of the deployment process please visit the following URL:

```
http://localhost:10350
```

Or execute outside Tilt the command:

```
kubectl logs deployment/cache-service
```

In order to undeploy everything just execute the command:

```
tilt down
```

To run inside a docker desktop the microservice need to use http://cach-service:8351 path


### Reference Documentation
This microservice uses the recent Spring Gateway :

* [Spring Boot Gateway](https://cloud.spring.io/spring-cloud-gateway/reference/html/)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/3.0.1/maven-plugin/reference/html/)
* [Config Client Quick Start](https://docs.spring.io/spring-cloud-config/docs/current/reference/html/#_client_side_usage)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/3.0.1/reference/htmlsingle/#production-ready)

### Links to Springboot 3 Observability

https://tanzu.vmware.com/developer/guides/observability-reactive-spring-boot-3/

Baeldung:

https://www.baeldung.com/spring-boot-3-observability



### Contact AI Legorreta

Feel free to reach out to AI Legorreta on [web page](https://legosoft.com.mx).


Version: 2.0.0
©LegoSoft Soluciones, S.C., 2023
