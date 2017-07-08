package org.collectd.java;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.collectd.api.Collectd;
import org.collectd.api.CollectdConfigInterface;
import org.collectd.api.CollectdInitInterface;
import org.collectd.api.CollectdReadInterface;
import org.collectd.api.CollectdShutdownInterface;
import org.collectd.api.OConfigItem;
import org.collectd.api.OConfigValue;
import org.collectd.api.ValueList;



public class JMXExtractor implements CollectdConfigInterface,
CollectdInitInterface,
CollectdReadInterface,
CollectdShutdownInterface{

	private String _jmx_service_url = null;
	private String _mbean_attr = null;
	private String _mbean_obj_name = null;

	private Long prevValue = (long) 0;
	private Long prevTimeStamp = (long) 0;

	JMXServiceURL service_url;
	JMXConnector connector;
	MBeanServerConnection connection;

	public enum ConfigType {
		URL, OBJNAME, ATTR
	}

	public JMXExtractor () {
		Collectd.logDebug("registering configs");
		Collectd.registerConfig   ("JMXExtractor", this);
		Collectd.registerInit     ("JMXExtractor", this);
		Collectd.registerRead     ("JMXExtractor", this);
		Collectd.registerShutdown ("JMXExtractor", this);

		prevValue = (long) 0;
		prevTimeStamp =(long) 0;
		Collectd.logDebug("Completed registering configs");
	}

	public int init () {
		Collectd.logDebug("Entering the init loop");
		Collectd.logDebug("service url? mbean? attr?"+_jmx_service_url+" "+_mbean_obj_name+" "+_mbean_attr);
		
		if (_jmx_service_url == null)
		{
			Collectd.logError ("JMXExtractor: _jmx_service_url == null");
			return (-1);
		}

		try
		{
			service_url = new JMXServiceURL (_jmx_service_url);
			connector = JMXConnectorFactory.connect (service_url);
			connection = connector.getMBeanServerConnection ();
		}
		catch (Exception e)
		{
			Collectd.logError ("JMXExtractor: Creating MBean connection failed: " + e);
			return (-1);
		}

		return (0);
	} 

	private void submit () {
		Collectd.logDebug("Entering submit..");
		ValueList vl;

		vl = new ValueList ();

		vl.setHost ("localhost");
		vl.setPlugin ("JMXStandalone");
		vl.setPluginInstance (_mbean_obj_name);
		vl.setType ("gauge");
		vl.setTypeInstance("jmxMetric");

		Collectd.logDebug("Testing loop ..");

		Long curTimeStamp;
		Long TimeDiff;
		Long curValue ;
		Long valueDiff;

		try {
			ObjectName mObjectName = new ObjectName(_mbean_obj_name);

			Set<ObjectName> myMbean = connection.queryNames(mObjectName, null);
			for (ObjectName obj : myMbean) {

				curValue = (Long) connection.getAttribute(obj, _mbean_attr);
				curTimeStamp = System.currentTimeMillis();
				TimeDiff = curTimeStamp - prevTimeStamp; 
				Collectd.logDebug("Time Difference in seconds - "+TimeDiff*0.001);

				prevTimeStamp = curTimeStamp;
				valueDiff = curValue - prevValue;

				Collectd.logDebug("Previous Value - "+prevValue);
				Collectd.logDebug("Current Value - "+curValue);

				prevValue = curValue;
				Double gaugeValue = valueDiff/(TimeDiff*0.001);
				Collectd.logDebug("Rate/Sec - "+gaugeValue);

				vl.addValue(gaugeValue);
				Collectd.dispatchValues(vl);
				vl.clearValues();

			}

		} catch (Exception e) {
			System.out.println("JMXExtractor: Creating MBean failed: " + e);

		} 

		Collectd.logDebug("Exiting submit..");

	} 

	public int config (OConfigItem ci) {
		Collectd.logDebug("Entering the config loop");
		List<OConfigItem> children;
		int i;
		Collectd.logDebug ("JMXExtractor plugin: config: ci = " + ci + ";");

		children = ci.getChildren ();
		for (i = 0; i < children.size (); i++)
		{
			OConfigItem child;
			String key;

			child = children.get (i);
			key = child.getKey ();
			if (key.equalsIgnoreCase ("JMXServiceURL"))
			{
				configService (child,ConfigType.URL);
			}
			else if(key.equalsIgnoreCase("MBeanObjectName")){
				configService (child, ConfigType.OBJNAME);
			}
			else if(key.equalsIgnoreCase("MbeanObjectAttribute")){
				configService (child, ConfigType.ATTR);
			}
			else
			{
				Collectd.logError ("JMXExtractor plugin: Unknown config option: " + key);
			}
		}
		Collectd.logDebug("Exiting config loop");

		return (0);
	} 

	private int configService (OConfigItem ci, ConfigType type) {
		Collectd.logDebug("Entering configService..");
		List<OConfigValue> values;
		OConfigValue cv;

		values = ci.getValues ();
		if (values.size () != 1)
		{
			Collectd.logError ("JMXExtractor plugin: The "+ type + " option needs "
					+ "exactly one string argument.");
			return (-1);
		}

		cv = values.get (0);
		if (cv.getType () != OConfigValue.OCONFIG_TYPE_STRING)
		{
			Collectd.logError ("JMXExtractor plugin: The "+ type + " option needs "
					+ "exactly one string argument.");
			return (-1);
		}
		switch(type){
		case URL:
			_jmx_service_url = cv.getString ();
			break;
		case ATTR:
			_mbean_attr = cv.getString();
			break;
		case OBJNAME:
			_mbean_obj_name = cv.getString();
			break;
		}

		Collectd.logDebug("Exiting configService..");
		return (0);
	} 

	public int shutdown () {
		Collectd.logDebug("org.collectd.java.JMXExtractor.Shutdown ();\n");
		_jmx_service_url = null;
		_mbean_attr = null;
		_mbean_obj_name = null;

		try {
			connector.close();
		} catch (IOException e) {
			Collectd.logError("Connector close error");
		}
		return (0);
	} 

	public int read () {
		Collectd.logDebug("Entering read loop");
		if (_mbean_obj_name == null || _mbean_attr == null) 
		{
			Collectd.logError ("JMXExtractor: MBean Object Name or MBean Attribute is null");
			return (-1);
		}

		submit ();

		Collectd.logDebug("Exiting read loop");

		return (0);
	} 

}