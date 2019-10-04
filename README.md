# Chameleon

This tool transforms monitoring events sent by CheckMK and forwards it to InfluxDB.

## Why would I need this tool?

CheckMK Enterprise Edition supports sending events to Graphite and InfluxDB on the other hand supports accepting Graphite data.
You could use this setup without this tool.

Events (more precisely *HOST*, *SERVICE* and *METRIK*; see CheckMK documentation) sent by CheckMK are escaped in any of the following ways:

Special characters (including dots) are escaped by either
* an underscore (*_*)
* C-style (e.g. *\056*)

See https://checkmk.com/check_mk-werks.php?werk_id=8567&HTML=yes for C-style mangling.

This limits the usability of these events, because some parts of the events e.g. the host name may contain dots.

This is where Chameleon comes into play. It replaces C-style strings with the corresponding character (see ASCII table)
and forwards these events to InfluxDB.

## How to use this tool?

### Set up InfluxDB

Create the database you will use later for the metrics if it does not exist yet.

### Set up Chameleon

You can build the tool using Maven and extract the built *tar* file:
```
export GRAPHITE_LISTENER_INTERFACE=0.0.0.0
export GRAPHITE_LISTENER_PORT=2003
export INFLUXDB_HOST=influxdb
export INFLUXDB_PORT=8086
export INFLUXDB_DATABASE=checkmk
export JAVA_OPTS="-Xms256m -Xmx256m -Dlogback.configurationFile=logback-packaged.xml -Dconfig.resource=application-packaged.conf"

java $JAVA_OPTS -cp "/path/to/chameleon/chameleon-1.0-SNAPSHOT/*" de.devk.chameleon.MainImpl
```

For building and running you need at least Java 11.

You can also use Docker to run it:
```
docker run \
  -ti \
  -e GRAPHITE_LISTENER_INTERFACE=0.0.0.0 \
  -e GRAPHITE_LISTENER_PORT=2003 \
  -e INFLUXDB_HOST=influxdb \
  -e INFLUXDB_PORT=8086 \
  -e INFLUXDB_DATABASE=checkmk \
  -e JAVA_OPTS="-Xms256m -Xmx256m" \
  -p 2003:2003 \
  devk/chameleon
```

See *src/main/resources/application.conf* for more configuration values.

### Set up CheckMK

Next, configure the CheckMK Graphite exporter to point to Chameleon.
If you do so, use the C-style strings setting.

## Links

* CheckMK sending events to Graphite: https://checkmk.com/cms_graphing.html#graphing_api
* InfluxDB accepting Graphite events: https://docs.influxdata.com/influxdb/v1.7/supported_protocols/graphite/

## License

Chameleon is released under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0).
