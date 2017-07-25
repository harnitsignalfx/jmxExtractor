# collectd-jmxExtractor
Extract JMX Mbeans (cumulative counters), convert them to Rate/Sec and send as gauges to SignalFx

## Install (Assuming you have collectd installed and sending data to SignalFx)

### Copy the included jar file to `/usr/share/collectd/java/`

### Insert the sample configuration file under /etc/collectd/managed_config/ 
Keep in mind that for multiple MBeans, you need the following items in order -
```
MBeanObjectName "insert obj name here"
MBeanObjectAttribute "insert attribute"
MetricName "metric name"
MBeanObjectName "insert obj name here"
MBeanObjectAttribute "insert attribute"
MetricName "metric name"
..
..
MetricName "metric name"
```

Modifiers available are 
1) JMXServiceURL
2) MBeanObjectName
3) MBeanObjectAttribute
4) MetricName
5) HostName (Optional, if not set then it'll be fetched from collectd)

### Restart collectd
```
sudo service collectd restart
```
