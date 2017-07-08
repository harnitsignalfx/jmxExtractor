# collectd-jmxExtractor
Extract JMX Mbeans (cumulative counters), convert them to Rate/Sec and send as gauges to SignalFx

## Install (Assuming you have collectd installed and sending data to SignalFx)

### Clone the plugin repo.
```
git clone git://github.com/harnitsignalfx/jmxExtractor.git
```

### Compile the plugin with jre target 1.6 for collectd (also check the path of your collectd-api.jar)

```
cd jmxExtractor
javac -target 1.6 -classpath /usr/share/collectd/java/collectd-api.jar org/collectd/java/jmxExtractor.java
```

### Insert this into your collectd.conf (likely at /etc/collectd/collectd.conf or at /etc/collectd.conf):

```
LoadPlugin java
<Plugin java>
  JVMArg "-Djava.class.path=/usr/share/collectd/java/collectd-api.jar:/path/to/jmxExtractor/"

  LoadPlugin "org.collectd.java.jmxExtractor"
  <Plugin "jmxExtractor">
    JMXServiceURL "service:jmx:rmi://localhost:9000/jndi/rmi://localhost:9999/jmxrmi"
    MBeanObjectName "kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec"
    MBeanObjectAttribute "Count"
  </Plugin>
</Plugin>
Restart collectd.
```
