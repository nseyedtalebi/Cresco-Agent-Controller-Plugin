package com.researchworx.cresco.controller.globalscheduler;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.researchworx.cresco.controller.core.Launcher;
import com.researchworx.cresco.controller.globalcontroller.GlobalHealthWatcher;
import com.researchworx.cresco.library.messaging.MsgEvent;
import com.researchworx.cresco.library.utilities.CLogger;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;


public class ResourceSchedulerEngine implements Runnable {

	private Launcher plugin;
	private GlobalHealthWatcher ghw;
	private CLogger logger;
    public Cache<String, String> jarStringCache;
    public Cache<String, String> jarHashCache;
    public Cache<String, String> jarTimeCache;


    public ResourceSchedulerEngine(Launcher plugin, GlobalHealthWatcher ghw) {
		this.plugin = plugin;
		this.ghw = ghw;
        logger = new CLogger(ResourceSchedulerEngine.class, plugin.getMsgOutQueue(), plugin.getRegion(), plugin.getAgent(), plugin.getPluginID(), CLogger.Level.Trace);

        jarStringCache = CacheBuilder.newBuilder()
                .concurrencyLevel(4)
                .softValues()
                .maximumSize(10)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .build();
        jarHashCache = CacheBuilder.newBuilder()
                .concurrencyLevel(4)
                .softValues()
                .maximumSize(100)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .build();
        jarTimeCache = CacheBuilder.newBuilder()
                .concurrencyLevel(4)
                .softValues()
                .maximumSize(100)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .build();
    }
		
	public void run() 
	{
		try
		{
			ghw.SchedulerActive = true;
			while (ghw.SchedulerActive)
			{
				try
				{
					MsgEvent ce = plugin.getResourceScheduleQueue().take();
					if(ce != null)
					{

						logger.debug("me.added");
						//check the pipeline node
						if(ce.getParam("globalcmd").equals("addplugin"))
						{
							//do something to activate a plugin
							logger.debug("starting precheck...");
							//String pluginJar = verifyPlugin(ce);
							String pluginFile = verifyPlugin(ce);
                            if(pluginFile == null)
							{
								if((plugin.getGDB().dba.setINodeParam(ce.getParam("inode_id"),"status_code","1")) &&
										(plugin.getGDB().dba.setINodeParam(ce.getParam("inode_id"),"status_desc","iNode Failed Activation : Plugin not found!")))
								{
									logger.debug("Provisioning Failed: No matching controller plugins found!");
								}
							}
							else
							{
								//adding in jar name information
								//ce.setParam("configparams",ce.getParam("configparams") + ",jarfile=" + pluginJar);

								//Here is where scheduling is taking place
								logger.debug("plugin precheck = OK");
								//String agentPath = getLowAgent();

                                //String[] agentPath_s = agentPath.split(",");
									String region = ce.getParam("location_region");
									String agent = ce.getParam("location_agent");
									String resource_id = ce.getParam("resource_id");
									String inode_id = ce.getParam("inode_id");

									//have agent download plugin
									//String pluginurl = "http://127.0.0.1:32003/";
									//downloadPlugin(region,agent,pluginJar,pluginurl, false);
									//logger.debug("Downloading plugin on region=" + region + " agent=" + agent);
                                    //include jar

                                    /*
                                    String jarString = jarStringCache.getIfPresent(pluginFile);

									if(jarString == null) {
                                        jarString = getJarString(pluginFile);
                                        jarStringCache.put(pluginFile,jarString);
                                    }
                                    */
									//logger.error(getJarString(pluginFile));

									//schedule plugin
									logger.debug("Scheduling plugin on region=" + region + " agent=" + agent);
									MsgEvent me = addPlugin(region,agent,ce.getParam("configparams"));
                                    me.setParam("http_host",getNetworkAddresses());
                                    me.setParam("jarmd5",jarHashCache.getIfPresent(pluginFile));
									//me.setParam("jarstring",jarString);
									logger.debug("pluginadd message: " + me.getParams().toString());
									
									//ControllerEngine.commandExec.cmdExec(me);
                                    //logger.error("before send");
                                    //MsgEvent re = plugin.getRPC().call(me);
                                    //logger.error("after send");
                                    //will send in thread
                                    plugin.msgIn(me);

									new Thread(new PollAddPlugin(plugin,resource_id, inode_id,region,agent, me)).start();

								
								/*
								if((ControllerEngine.gdb.setINodeParam(ce.getParam("resource_id"),ce.getParam("inode_id"),"status_code","10")) &&
										(ControllerEngine.gdb.setINodeParam(ce.getParam("resource_id"),ce.getParam("inode_id"),"status_desc","iNode Active.")))
								{
										//recorded plugin activations
									
								}
								*/
							}
						}
						else if(ce.getParam("globalcmd").equals("removeplugin"))
						{
							Map<String,String> pNodeMap = plugin.getGDB().dba.getpNodeINode(ce.getParam("inode_id"));
                            logger.debug("Incoming Remove Request : resource_id: " + ce.getParam("resource_id") + " inode_id: " + ce.getParam("inode_id") + " pnodeMap: " + pNodeMap.toString());
                            new Thread(new PollRemovePlugin(plugin,  ce.getParam("resource_id"),ce.getParam("inode_id"))).start();
						}
					}

				}
				catch(Exception ex)
				{
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    ex.printStackTrace(pw);
                    logger.error(sw.toString());

                    logger.error("ResourceSchedulerEngine Error: " + ex.toString());
				}
			}
		}
		catch(Exception ex)
		{
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            logger.error(sw.toString());

            logger.error("ResourceSchedulerEngine Error: " + ex.toString());
		}
	}

    public String getJarMD5(String pluginFile) {
        String jarString = null;
        try
        {
            Path path = Paths.get(pluginFile);
            byte[] data = Files.readAllBytes(path);

            MessageDigest m= MessageDigest.getInstance("MD5");
            m.update(data);
            jarString = new BigInteger(1,m.digest()).toString(16);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        return jarString;
    }

    public String getJarString(String pluginFile) {
        String jarString = null;
	    try
        {
            Path path = Paths.get(pluginFile);
            byte[] data = Files.readAllBytes(path);
            jarString = DatatypeConverter.printBase64Binary(data);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        return jarString;
    }

	private String verifyPlugin(MsgEvent ce) {
	    String returnPluginfile = null;
	    //boolean isVerified = false;
		//pre-schedule check
		String configparams = ce.getParam("configparams");
		logger.debug("verifyPlugin params " + configparams);

        Map<String,String> params = getMapFromString(configparams, false);

        logger.debug("config params: " + params.toString());

        String requestedPlugin = params.get("pluginname");

        List<String> pluginMap = getPluginInventory();
        for(String pluginfile : pluginMap) {
            logger.debug("plugin = " + pluginfile);
            logger.debug("plugin name = " + getPluginName(pluginfile));
                String pluginName = getPluginName(pluginfile);
                if(pluginName != null) {
                    if (requestedPlugin.equals(pluginName)) {
                        returnPluginfile = pluginfile;
                    }
                }
        }

        /*
        String[] cparams = configparams.split(",");
		Map<String,String> cm = new HashMap<String,String>();
		for(String param : cparams)
		{
			String[] paramkv = param.split("=");
			cm.put(paramkv[0], paramkv[1]);
		}

		String requestedPlugin = cm.get("pluginname") + "=" + cm.get("pluginversion");
        String requestedPlugin = params.get("pluginname");
		logger.debug("Requested Plugin=" + requestedPlugin);
		if(pluginMap.contains(requestedPlugin))
		{
			return getPluginFileMap().get(requestedPlugin);
		}
		else
		{
			ce.setMsgBody("Matching plugin could not be found!");
			ce.setParam("pluginstatus","failed");
		}
		*/
		return returnPluginfile;
	}
	
	public Map<String,String> paramStringToMap(String param)
	{
		Map<String,String> params = null;
		try
		{
			params = new HashMap<String,String>();
			String[] pstr = param.split(",");
			for(String str : pstr)
			{
				String[] pstrs = str.split("=");
				params.put(pstrs[0], pstrs[1]);
			}
		}
		catch(Exception ex)
		{
			logger.error("ResourceSchedulerEngine : Error " + ex.toString());
		}
		return params;
	}

	public String getLowAgent()
	{
		
		Map<String,Integer> pMap = new HashMap<String,Integer>();
		String agent_path = null;
		try
		{
			List<String> regionList = plugin.getGDB().gdb.getNodeList(null,null,null);
			//logger.debug("Region Count: " + regionList.size());
			for(String region : regionList)
			{
				List<String> agentList = plugin.getGDB().gdb.getNodeList(region,null,null);
				//logger.debug("Agent Count: " + agentList.size());
				
				for(String agent: agentList)
				{
					List<String> pluginList = plugin.getGDB().gdb.getNodeList(region,agent,null);
					int pluginCount = 0;
					if(pluginList != null)
					{
						pluginCount = pluginList.size();
					}
					String tmp_agent_path = region + "," + agent;
					pMap.put(tmp_agent_path, pluginCount);
				}
			}
			
			
			if(pMap != null)
			{
				Map<String, Integer> sortedMapAsc = sortByComparator(pMap, true);
				Entry<String, Integer> entry = sortedMapAsc.entrySet().iterator().next();
				agent_path = entry.getKey().toString();
				/*
				for (Entry<String, Integer> entry : sortedMapAsc.entrySet())
				{
					logger.debug("Key : " + entry.getKey() + " Value : "+ entry.getValue());
				}
				*/
			}
	        
		}
		catch(Exception ex)
		{
			logger.error("DBEngine : getLowAgent : Error " + ex.toString());
		}
		
		return agent_path;
	}

	private static Map<String, Integer> sortByComparator(Map<String, Integer> unsortMap, final boolean order)
    {

        List<Entry<String, Integer>> list = new LinkedList<Entry<String, Integer>>(unsortMap.entrySet());

        // Sorting the list based on values
        Collections.sort(list, new Comparator<Entry<String, Integer>>()
        {
            public int compare(Entry<String, Integer> o1,
                    Entry<String, Integer> o2)
            {
                if (order)
                {
                    return o1.getValue().compareTo(o2.getValue());
                }
                else
                {
                    return o2.getValue().compareTo(o1.getValue());

                }
            }
        });

        // Maintaining insertion order with the help of LinkedList
        Map<String, Integer> sortedMap = new LinkedHashMap<String, Integer>();
        for (Entry<String, Integer> entry : list)
        {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }
	
	public MsgEvent addPlugin(String region, String agent, String configParams)
	{

	    //else if (ce.getParam("configtype").equals("pluginadd"))

		MsgEvent me = new MsgEvent(MsgEvent.Type.CONFIG,region,null,null,"add plugin");
		me.setParam("src_region", plugin.getRegion());
		me.setParam("src_agent", plugin.getAgent());
        me.setParam("src_plugin", plugin.getPluginID());
        me.setParam("dst_region", region);
		me.setParam("dst_agent", agent);
		me.setParam("action", "pluginadd");
		me.setParam("configparams",configParams);
		return me;
	}
	
	public MsgEvent downloadPlugin(String region, String agent, String pluginId, String pluginurl, boolean forceDownload)
	{
		MsgEvent me = new MsgEvent(MsgEvent.Type.CONFIG,region,null,null,"download plugin");
        me.setParam("src_region", plugin.getRegion());
        me.setParam("src_agent", plugin.getAgent());
        me.setParam("src_plugin", plugin.getPluginID());
        me.setParam("dst_region", region);
        me.setParam("dst_agent", agent);
        me.setParam("configtype", "plugindownload");
		me.setParam("plugin", pluginId);
		me.setParam("pluginurl", pluginurl);
		//me.setParam("configparams", "perflevel="+ perflevel + ",pluginname=DummyPlugin,jarfile=..//Cresco-Agent-Dummy-Plugin/target/cresco-agent-dummy-plugin-0.5.0-SNAPSHOT-jar-with-dependencies.jar,region=test2,watchdogtimer=5000");
		if(forceDownload)
		{
			me.setParam("forceplugindownload", "true");
		}
		return me;
	}

    public List<String> getPluginInventory()
    {
        List<String> pluginFiles = null;
        try
        {
            String pluginDirectory = null;
            if(plugin.getConfig().getStringParam("localpluginrepo") != null) {
                pluginDirectory = plugin.getConfig().getStringParam("localpluginrepo");
            }
            else {
                //if not listed use the controller directory
                File jarLocation = new File(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
                pluginDirectory = jarLocation.getParent(); // to get the parent dir name
            }

            logger.debug("pluginDirectory: " + pluginDirectory);

            File folder = new File(pluginDirectory);
            if(folder.exists())
            {
                pluginFiles = new ArrayList<String>();
                File[] listOfFiles = folder.listFiles();

                for (int i = 0; i < listOfFiles.length; i++)
                {
                    if (listOfFiles[i].isFile())
                    {
                        pluginFiles.add(listOfFiles[i].getAbsolutePath());
                        logger.debug(listOfFiles[i].getAbsolutePath().toString());
                    }

                }
                if(pluginFiles.isEmpty())
                {
                    pluginFiles = null;
                }
            }
            else {
                logger.error("getPluginFiles Directory ");
            }

        }
        catch(Exception ex)
        {
            logger.debug(ex.toString());
            pluginFiles = null;
        }
        return pluginFiles;
    }

    public String getPluginName(String jarFile) //This should pull the version information from jar Meta data
    {
        String version = null;
        try{
            //String jarFile = AgentEngine.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            //logger.debug("JARFILE:" + jarFile);
            //File file = new File(jarFile.substring(5, (jarFile.length() )));
            File file = new File(jarFile);

            boolean calcHash = true;
            BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            long fileTime = attr.creationTime().toMillis();

            String jarCreateTimeString = jarTimeCache.getIfPresent(jarFile);


            if(jarCreateTimeString != null) {
              long jarCreateTime = Long.parseLong(jarCreateTimeString);
              if(jarCreateTime == fileTime) {
                calcHash = false;
              } else {
                  jarStringCache.invalidate(jarFile);
                  jarHashCache.invalidate(jarFile);
              }
            }
            if(calcHash) {
                jarStringCache.put(jarFile,String.valueOf(fileTime));
                jarHashCache.put(jarFile,getJarMD5(jarFile));
            }

            FileInputStream fis = new FileInputStream(file);
            @SuppressWarnings("resource")
            JarInputStream jarStream = new JarInputStream(fis);
            Manifest mf = jarStream.getManifest();

            Attributes mainAttribs = mf.getMainAttributes();
            version = mainAttribs.getValue("artifactId");
        }
        catch(Exception ex)
        {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            logger.error(sw.toString());

            logger.error("Unable to determine Plugin Version " + ex.getMessage());
            //version = "Unable to determine Version";
        }
        return version;
    }

    public String getNetworkAddresses() {
        String netwokrAddressesString = null;
        try {
            List<InterfaceAddress> interfaceAddressList = new ArrayList<>();

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.getDisplayName().startsWith("veth") && !networkInterface.isLoopback() && networkInterface.supportsMulticast() && !networkInterface.isPointToPoint() && !networkInterface.isVirtual()) {
                    logger.debug("Found Network Interface [" + networkInterface.getDisplayName() + "] initialized");
                    interfaceAddressList.addAll(networkInterface.getInterfaceAddresses());
                }
            }
            StringBuilder nsb = new StringBuilder();
            ////String pluginurl = "http://127.0.0.1:32003/";

            for(InterfaceAddress inaddr : interfaceAddressList) {
                logger.debug("interface addresses " + inaddr);
                String hostAddress = inaddr.getAddress().getHostAddress();
                if(!hostAddress.contains(":")) {
                    nsb.append("http://" + hostAddress + ":32000/PLUGINS/,");
                }
            }
            if(nsb.length() > 0) {
                nsb.deleteCharAt(nsb.length() -1 );
            }
            netwokrAddressesString = nsb.toString();
        } catch (Exception ex) {
            logger.error("getNetworkAddresses ", ex.getMessage());
        }
    return netwokrAddressesString;
    }

    public String getPluginVersion(String jarFile) //This should pull the version information from jar Meta data
    {
        String version;
        try{
            //String jarFile = AgentEngine.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            //logger.debug("JARFILE:" + jarFile);
            //File file = new File(jarFile.substring(5, (jarFile.length() )));
            File file = new File(jarFile);
            FileInputStream fis = new FileInputStream(file);
            @SuppressWarnings("resource")
            JarInputStream jarStream = new JarInputStream(fis);
            Manifest mf = jarStream.getManifest();

            Attributes mainAttribs = mf.getMainAttributes();
            version = mainAttribs.getValue("Implementation-Version");
        }
        catch(Exception ex)
        {
            String msg = "Unable to determine Plugin Version " + ex.toString();
            System.err.println(msg);
            version = "Unable to determine Version";
        }
        return version;
    }

	public Map<String,String> getPluginFileMap()
	{
		Map<String,String> pluginList = new HashMap<String,String>();
		
		try
		{
		File jarLocation = new File(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
		String parentDirName = jarLocation.getParent(); // to get the parent dir name
		
		File folder = new File(parentDirName);
		if(folder.exists())
		{
		File[] listOfFiles = folder.listFiles();

		    for (int i = 0; i < listOfFiles.length; i++) 
		    {
		      if (listOfFiles[i].isFile()) 
		      {
		        //logger.debug("Found Plugin: " + listOfFiles[i].getName());
		        //<pluginName>=<pluginVersion>,
		        String pluginPath = listOfFiles[i].getAbsolutePath();
		        //pluginList.add(ControllerEngine.commandExec.getPluginName(pluginPath) + "=" + ControllerEngine.commandExec.getPluginVersion(pluginPath));
		        String pluginKey = getPluginName(pluginPath) + "=" + getPluginVersion(pluginPath);
		        String pluginValue = listOfFiles[i].getName();
		        pluginList.put(pluginKey, pluginValue);
		        //pluginList = pluginList + getPluginName(pluginPath) + "=" + getPluginVersion(pluginPath) + ",";
		        //pluginList = pluginList + listOfFiles[i].getName() + ",";
		      } 
		      
		    }
		    if(pluginList.size() > 0)
		    {
		    	return pluginList;
		    }
		}
		
		
		}
		catch(Exception ex)
		{
			logger.debug(ex.toString());
		}
		return null; 
		
	}

    public Map<String,String> getMapFromString(String param, boolean isRestricted) {
        Map<String,String> paramMap = null;

        logger.debug("PARAM: " + param);

        try{
            String[] sparam = param.split(",");
            logger.debug("PARAM LENGTH: " + sparam.length);

            paramMap = new HashMap<String,String>();

            for(String str : sparam)
            {
                String[] sstr = str.split("=");

                if(isRestricted)
                {
                    paramMap.put(URLDecoder.decode(sstr[0], "UTF-8"), "");
                }
                else
                {
                    if(sstr.length > 1)
                    {
                        paramMap.put(URLDecoder.decode(sstr[0], "UTF-8"), URLDecoder.decode(sstr[1], "UTF-8"));
                    }
                    else
                    {
                        paramMap.put(URLDecoder.decode(sstr[0], "UTF-8"), "");
                    }
                }
            }
        }
        catch(Exception ex)
        {
            logger.error("getMapFromString Error: " + ex.toString());
        }

        return paramMap;
    }


}



