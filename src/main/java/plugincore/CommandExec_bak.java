package plugincore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shared.MsgEvent;
import shared.MsgEventType;


public class CommandExec_bak {

	public CommandExec_bak()
	{
		//toats
	}
	private static final Logger logger = LoggerFactory.getLogger(CommandExec_bak.class);

	public MsgEvent cmdExec(MsgEvent ce)
	{
		System.out.println("MESSAGE IN CONTROLLER: " + ce.getParams());
		try
		{

			boolean isLocal = false;
			boolean isLocalPlugin = false;
			if ((ce.getParam("dst_region") != null) && (ce.getParam("dst_agent") != null)) {
				if ((ce.getParam("dst_region").equals(PluginEngine.region)) && (ce.getParam("dst_agent").equals(PluginEngine.agent))) {
					isLocal = true;
					if (ce.getParam("dst_plugin") != null) {
						if(ce.getParam("dst_plugin").equals(PluginEngine.plugin)) {
							isLocalPlugin = true;
						}
					}
				}
			}

			if (isLocal) {
				if(isLocalPlugin) {
					System.out.println("LOCAL CONTROLLER MESSAGE: " + ce.getParams());

					if (ce.getMsgType() == MsgEventType.CONFIG) //for init
					{
						if (ce.getMsgBody() != null) {
							if (ce.getMsgBody().equals("comminit")) {
								PluginEngine.commInit(); //initial init
								ce.setParam("set_region", PluginEngine.region);
								ce.setParam("set_agent", PluginEngine.agent);
								ce.setParam("is_regional_controller", Boolean.toString(PluginEngine.isRegionalController));
								ce.setParam("is_active", Boolean.toString(PluginEngine.isActive));

							}
							return ce;
						}

					}
					else if (ce.getMsgType() == MsgEventType.CONFIG) {

					}
					String callId = ce.getParam("callId-" + PluginEngine.region + "-" + PluginEngine.agent + "-" + PluginEngine.plugin); //unique callId
					if (callId != null) //this is a callback put in RPC hashmap
					{
						//PluginEngine.rpcMap.put(callId, ce);
					}
					else if ((ce.getMsgRegion().equals(PluginEngine.region) && (ce.getMsgAgent().equals(PluginEngine.agent)) && (ce.getMsgPlugin().equals(PluginEngine.plugin)))) {

						if ((ce.getParam("dst_region") != null) && (ce.getParam("dst_agent") != null) && (ce.getParam("dst_plugin") != null)) //plugin message
						{
							if ((ce.getParam("dst_region").equals(PluginEngine.region)) && (ce.getParam("dst_agent").equals(PluginEngine.agent)) && (ce.getParam("dst_plugin").equals(PluginEngine.plugin))) {
								logger.debug("MESSAGE FOR THIS PLUGIN");
							}
						}
						if ((ce.getParam("dst_region") != null) && (ce.getParam("dst_agent") != null) && (ce.getParam("dst_plugin") == null)) { //agent message
							logger.debug("OUTGOING MESSAGE FOR EXTERNAL AGENT");
						}

					} else if ((ce.getMsgRegion().equals(PluginEngine.region) && (ce.getMsgAgent().equals(PluginEngine.agent)) && (ce.getMsgPlugin() == null))) { //message for this agent

						if ((ce.getParam("dst_region").equals(PluginEngine.region)) && (ce.getParam("dst_agent").equals(PluginEngine.agent))) {
							//message for plugin send to agent
							logger.debug("MESSAGE FOR THIS AGENT");
							PluginEngine.msgInQueue.offer(ce);
						}
					}
					//
					else if ((ce.getParam("dst_region") != null) && (ce.getParam("dst_agent") != null)) //its a message for this plugin
					{
						//(isReachableAgent(targetAgent))
						//String dst_region = ce.getParam("dst_region");
						//String dst_agent = ce.getParam("dst_region");
						String targetAgent = ce.getParam("dst_region") + "_" + ce.getParam("dst_region");
						if (PluginEngine.isReachableAgent(targetAgent)) {
							PluginEngine.ap.sendMessage(ce);
						} else {
							logger.error("Unreachable External Agent : " + targetAgent);
						}
					}
				}
				else { //local, but not for this plugin, sent to agent.
					System.out.println("LOCAL AGENT MESSAGE Sending to Agent: " + ce.getParams());
					PluginEngine.msgInQueue.offer(ce);
				}
				return null; //default don't send anything back
			}
			else { //not local message send over broker

				String targetAgent = null;
				if ((ce.getParam("dst_region") != null) && (ce.getParam("dst_agent") != null)) {
					//agent message
					targetAgent = ce.getParam("dst_region") + "_" + ce.getParam("dst_agent");

				}
				else if ((ce.getParam("dst_region") != null) && (ce.getParam("dst_agent") == null)) {
					//regional message
					targetAgent = ce.getParam("dst_region");
				}

				if (PluginEngine.isReachableAgent(targetAgent)) {
					PluginEngine.ap.sendMessage(ce);
					System.out.println("SENT NOT CONTROLLER MESSAGE / REMOTE=: " + targetAgent + " " + " region=" + ce.getParam("dst_region") + " agent=" + ce.getParam("dst_agent") + " "  + ce.getParams());
				} else {
					logger.error("Unreachable External Agent : " + targetAgent);
				}
				return null;
			}
		//end try
		}
		catch(Exception ex)
		 {
			/*
			 MsgEvent ee = PluginEngine.clog.getError("Agent : CommandExec : Error" + ex.toString());
			 System.out.println("MsgType=" + ce.getMsgType().toString());
			 System.out.println("Region=" + ce.getMsgRegion() + " Agent=" + ce.getMsgAgent() + " plugin=" + ce.getMsgPlugin());
			 System.out.println("params=" + ce.getParamsString()); 
			 return ee;
			 */
			System.out.println("MsgType=" + ce.getMsgType().toString());
			 return null;
		 }
	}
	
}