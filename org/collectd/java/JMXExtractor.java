package org.collectd.java;

import java.io.IOException;
import java.util.ArrayList;
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
	private List<String> _mbean_attr = new ArrayList<String>();
	private List<String> _mbean_obj_name = new ArrayList<String>();
	private List<String> _metric_name = new ArrayList<String>();
	private String _host_name = null;

	private List<Long> prevValue = new ArrayList<Long>();
	private List<Long> prevTimeStamp = new ArrayList<Long>();

	JMXServiceURL service_url;
	JMXConnector connector;
	MBeanServerConnection connection;


	public enum ConfigType {
		URL, OBJNAME, ATTR, METRICNAME, HOSTNAME
	}

	public JMXExtractor () {
		Collectd.logDebug("registering configs");
		Collectd.registerConfig   ("JMXExtractor", this);
		Collectd.registerInit     ("JMXExtractor", this);
		Collectd.registerRead     ("JMXExtractor", this);
		Collectd.registerShutdown ("JMXExtractor", this);

		Collectd.logDebug("Completed registering configs");
	}

	public int init () {
		Collectd.logDebug("Entering the init loop");

		if (_jmx_service_url == null || _mbean_attr.isEmpty() || _mbean_obj_name.isEmpty() || _metric_name.isEmpty())
		{
			Collectd.logError ("JMXExtractor: An attribute is missing or is null");
			return (-1);
		}

		if( _host_name == null)
			_host_name = Collectd.getHostname();

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


	//	TODO: Clean up submitting multiple MBeans
	private void submit () {
		Collectd.logDebug("Entering submit..");
		ValueList vl;

		vl = new ValueList ();

		vl.setHost (_host_name);
		vl.setPlugin ("JMXStandalone");
		vl.setType ("gauge");
		

		Collectd.logDebug("Testing loop ..");

		Long curTimeStamp;
		Long TimeDiff;
		Long curValue ;
		Long valueDiff;

		int mbeansize = _mbean_obj_name.size() - 1;
		int idx = 0;
		
		while(idx <= mbeansize){
			try {
				ObjectName mObjectName = new ObjectName(_mbean_obj_name.get(idx));

				Set<ObjectName> myMbean = connection.queryNames(mObjectName, null);
				for (ObjectName obj : myMbean) {
					
					Collectd.logDebug("MBean Obj Name - "+_mbean_obj_name.get(idx)+" , Index - "+idx);
					
					curValue = (Long) connection.getAttribute(obj, _mbean_attr.get(idx));
					curTimeStamp = System.currentTimeMillis();
					TimeDiff = curTimeStamp - prevTimeStamp.get(idx); 
					Collectd.logDebug("Time Difference in seconds - "+TimeDiff*0.001);

					prevTimeStamp.set(idx, curTimeStamp); 
					valueDiff = curValue - prevValue.get(idx);

					Collectd.logDebug("Previous Value - "+prevValue.get(idx));
					Collectd.logDebug("Current Value - "+curValue);

					prevValue.set(idx,curValue);
					
					
					Double gaugeValue = valueDiff/(TimeDiff*0.001);
					Collectd.logDebug("Rate/Sec - "+gaugeValue);

					vl.addValue(gaugeValue);
					vl.setTypeInstance(_metric_name.get(idx));
					Collectd.dispatchValues(vl);
					vl.clearValues();

				}
				idx++;

			} catch (Exception e) {
				System.out.println("JMXExtractor: Creating MBean failed: " + e);

			}
		}



		Collectd.logDebug("Exiting submit..");

	} 

	//	COMPLETED

	public int config (OConfigItem ci) {
		Collectd.logDebug("Entering the config loop");
		List<OConfigItem> children;
		
		Collectd.logDebug ("JMXExtractor plugin: config: ci = " + ci + ";");

		children = ci.getChildren ();
		for (int i = 0; i < children.size (); i++)
		{
			OConfigItem child;
			String key;
			child = children.get (i);
			key = child.getKey ();
			Collectd.logDebug("Key ->"+key);

			if (key.equalsIgnoreCase ("JMXServiceURL"))
			{
				configService (child,ConfigType.URL);
			}
			//			Make sure that MBean ObjName, ObjAttr and MetricName are all Tied together.
			else if(key.equalsIgnoreCase("MBeanObjectName")){  
				OConfigItem child2 = children.get(++i);
				key = child2.getKey();
				if(key.equalsIgnoreCase("MbeanObjectAttribute")){
					OConfigItem child3 = children.get(++i);
					key = child3.getKey();
					if(key.equalsIgnoreCase("MetricName")){
						configService(child,child2,child3);
					}
					else{
						Collectd.logError ("JMXExtractor plugin: Unknown config option: " + key +", Expecting MBeanObjectName, MBeanAttribute and MetricName");
					}
				}
				else{
					Collectd.logError ("JMXExtractor plugin: Unknown config option: " + key +", Expecting MBeanObjectName, MBeanAttribute and MetricName");
				}

			}
			else if(key.equalsIgnoreCase("HostName")){
				configService(child, ConfigType.HOSTNAME);
			}
			else
			{
				Collectd.logError ("JMXExtractor plugin: Unknown config option: " + key);
			}
		}
		
		
		int mbeansize = _mbean_obj_name.size();
		if(prevTimeStamp.isEmpty()){
			for(int i =0; i < mbeansize; i++){
				prevTimeStamp.add(new Long(0));
				prevValue.add(new Long(0));
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
		case HOSTNAME:
			_host_name = cv.getString();
			break;
		default:
			break;
		}

		Collectd.logDebug("Exiting configService..");
		return (0);
	} 

	private int configService (OConfigItem ci,OConfigItem ci2,OConfigItem ci3) {
		Collectd.logDebug("Entering configService..");
		List<OConfigValue> values,values2,values3;
		OConfigValue cv,cv2,cv3;

		values = ci.getValues ();
		values2 = ci2.getValues();
		values3 = ci3.getValues();

		if (values.size () != 1 || values2.size() !=1 || values3.size() != 1)
		{
			Collectd.logError ("JMXExtractor plugin: The Mbean options each need "
					+ "exactly one string argument.");
			return (-1);
		}

		cv = values.get (0);
		cv2 = values2.get(0);
		cv3 = values3.get(0);

		if (cv.getType () != OConfigValue.OCONFIG_TYPE_STRING || cv2.getType () != OConfigValue.OCONFIG_TYPE_STRING || cv3.getType () != OConfigValue.OCONFIG_TYPE_STRING)
		{
			Collectd.logError ("JMXExtractor plugin: The Mbean options each need "
					+ "exactly one string argument.");
			return (-1);
		}

		_mbean_obj_name.add(cv.getString());
		_mbean_attr.add(cv2.getString());
		_metric_name.add(cv3.getString());

		Collectd.logDebug("Exiting configService..");
		return (0);
	} 

	public int shutdown () {
		Collectd.logDebug("org.collectd.java.JMXExtractor.Shutdown ();\n");
		_jmx_service_url = null;
		_mbean_attr = null;
		_mbean_obj_name = null;
		_metric_name = null;

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