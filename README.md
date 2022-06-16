# Netflow Collector with FS2

This is a NetFlow collector built using Cats Effect and FS2.
It transforms the incoming NetFlow v5 binary protocol to CSV and forwards it to a Kafka topic.

The motivation to build this project was to try out FS2 by implementing "something practical". It's more of a demo project and far from a fully featured NetFlow collector.
However, if you find it useful, feel free to tinker / reuse in any way you'd like.

#### Potentially useful bits you can grab
* A simple UDP server
* Functions to transform binary data to case classes
* FS2 transformation pipes (e.g. toCSV)
* A minimal Kafka producer setup

#### Libs 
* Using [typelevel/cats-effect](https://github.com/typelevel/cats-effect#readme) 3.3.12
* UDP server and stream transformations done with [typelevel/fs2](https://github.com/typelevel/fs2#readme) 3.2.7
* Kafka integration via [fd4s/fs2-kafka](https://github.com/fd4s/fs2-kafka#readme) 2.5.0-M3
* Uses [typelevel/log4cats](https://github.com/typelevel/log4cats#readme) 2.3.1 for logging
* Scala 3.1.3

### My use case

I had a router running [OpenWRT](https://openwrt.org/) lying around where I installed [softflowd](https://openwrt.org/packages/pkgdata/softflowd)
using [this guide](https://mattjhayes.com/2018/08/04/netflow-on-openwrt/). 
The goal was to collect IP traffic on the home network for logging and/or further processing.

Using `option host_port` in `/etc/config/softflowd` I routed the flow to my workstation to a select port. 
Since this is UDP, nothing blows up if the collector is not running.

#### Config

Passed to the app via 3 environment variables:
```shell
BIND_PORT=4444 # collector server port
BOOTSTRAP_SERVER=localhost:9092 # Kafka bootstrap server
TOPIC_NAME=netflow-in-csv # Kafka topic
```

#### References
* NetFlow on [Wikipedia](https://en.wikipedia.org/wiki/NetFlow)
* Docs for the binary data format on the [Cisco](https://www.cisco.com/c/en/us/td/docs/net_mgmt/netflow_collection_engine/3-6/user/guide/format.html) web