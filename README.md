# reactive-lib

[![Build Status](https://api.travis-ci.org/lightbend/reactive-lib.png?branch=master)](https://travis-ci.org/lightbend/reactive-lib)

This project is a component of [Lightbend Orchestration for Kubernetes](https://developer.lightbend.com/docs/lightbend-orchestration-kubernetes/latest/). Refer to its documentation for usage, examples, and reference information.

## Usage

### Kubernetes

`reactive-lib` is included in your application by [sbt-reactive-app](https://github.com/lightbend/sbt-reactive-app). Consult
[Lightbend Orchestration for Kubernetes](https://developer.lightbend.com/docs/lightbend-orchestration-kubernetes/latest/) documentation
for setup and configuration.

### DC/OS

If you're using DC/OS, you can use some of the functionality of `reactive-lib`, notably its service locator for Akka and Lagom.

#### Akka

```sbt
"com.lightbend.rp" %% "reactive-lib-service-discovery" % "<version>"
```

#### Lagom 1.4

```sbt
"com.lightbend.rp" %% "reactive-lib-service-discovery-lagom14-java" % "<version>"
```

##### Java

```hocon
// In `application.conf`

play.modules.enabled += "com.lightbend.rp.servicediscovery.lagom.javadsl.ServiceLocatorModule"
```

##### Scala

```scala
// In your application loader

import com.lightbend.rp.servicediscovery.lagom.scaladsl.LagomServiceLocatorComponents

...

class LagomLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext) = 
    new MyLagomApplication(context) with LagomServiceLocatorComponents

  ...
}

...
```

## Releasing

Consult the _Lightbend Orchestration Release Process_ document in Google Drive.

## Maintenance

Enterprise Suite Platform Team <es-platform@lightbend.com>

## Third-party License

This project includes a copy of [akka-dns](https://github.com/ilya-epifanov/akka-dns), Copyright 2014-2016 Ilya Epifanov.
Refer to the `async-dns` directory for more information.

## License

Copyright (C) 2017-2018 Lightbend Inc. (https://www.lightbend.com).

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
