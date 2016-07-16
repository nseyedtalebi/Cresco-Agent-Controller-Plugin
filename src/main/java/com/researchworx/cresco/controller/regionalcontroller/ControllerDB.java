package com.researchworx.cresco.controller.regionalcontroller;

import com.researchworx.cresco.controller.core.Launcher;
import com.researchworx.cresco.library.messaging.MsgEvent;
import com.researchworx.cresco.library.utilities.CLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ControllerDB {
    //private static final Logger logger = LoggerFactory.getLogger(ControllerDB.class);

    private Map<String, AgentNode> agentMap;
    private Launcher plugin;
    private CLogger logger;

    public ControllerDB(Launcher plugin) {
        this.logger = new CLogger(ControllerDB.class, plugin.getMsgOutQueue(), plugin.getRegion(), plugin.getAgent(), plugin.getPluginID());
        this.plugin = plugin;
        this.agentMap = new ConcurrentHashMap<>();
    }

    public Boolean isNode(String region, String agent, String plugin) {
        boolean isNode = false;
        try {
            logger.debug("AGENTMAP = " + this.agentMap.size() + this.agentMap);
            if ((region != null) && (agent != null) && (plugin == null)) {
                if (this.agentMap.containsKey(agent)) {
                    isNode = true;
                }
            } else if ((region != null) && (agent != null)) {
                if (isNode(region, agent, null)) {
                    if (this.agentMap.get(agent).isPlugin(plugin)) {
                        isNode = true;
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Controller : ControllerDB : isNode ERROR : " + ex.toString());
        }
        return isNode;
    }

    public MsgEvent controllerMsgEvent(String region, String agent, String plugin, String controllercmd) {
        MsgEvent ce = new MsgEvent(MsgEvent.Type.CONFIG, null, null, null, "Generated by ControllerDB");
        ce.setParam("controllercmd", controllercmd);
        if ((region != null) && (agent != null) && (plugin != null)) {
            ce.setParam("src_region", region);
            ce.setParam("src_agent", agent);
            ce.setParam("src_plugin", plugin);

        } else if ((region != null) && (agent != null)) {
            ce.setParam("src_region", region);
            ce.setParam("src_agent", agent);
        }
        return ce;
    }

    public void addNode(String region, String agent, String plugin) {
        logger.info("Adding Node: " + region + " " + agent + " " + plugin);
        try {
            if ((region != null) && (agent != null) && (plugin == null)) {
                AgentNode aNode = new AgentNode(agent);
                agentMap.put(agent, aNode);
                //add to controller
                if (this.plugin.hasGlobalController()) {
                    if (!this.plugin.getGlobalControllerChannel().addNode(controllerMsgEvent(region, agent, plugin, "addnode"))) {
                        logger.info("Controller : ControllerDB : Failed to addNode to Controller");
                    }
                }
            } else if ((region != null) && (agent != null)) {
                if (!isNode(region, agent, null)) {
                    addNode(region, agent, null);
                }
                agentMap.get(agent).addPlugin(plugin);
                //add to controller
                if (this.plugin.hasGlobalController()) {
                    if (!this.plugin.getGlobalControllerChannel().addNode(controllerMsgEvent(region, agent, plugin, "addnode"))) {
                        logger.info("Controller : ControllerDB : Failed to addNode to Controller");
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Controller : ControllerDB : addNode ERROR : " + ex.toString());
        }
    }


    public void setNodeParams(String region, String agent, String plugin, Map<String, String> paramMap) {
        //extract config from param Map
        Map<String, String> configMap = this.plugin.getControllerConfig().buildPluginMap(paramMap.get("msg"));

        try {

            if ((region != null) && (agent != null) && (plugin == null)) //agent node
            {
                agentMap.get(agent).setAgentParams(configMap);
                if (this.plugin.hasGlobalController()) {
                    MsgEvent ce = controllerMsgEvent(region, agent, plugin, "setparams");
                    ce.setParam("configparams", paramMap.get("msg"));
                    if (!this.plugin.getGlobalControllerChannel().setNodeParams(ce)) {
                        System.out.println("Controller : ControllerDB : Failed to setParams for Node on Controller");
                    }
                }
            } else if ((region != null) && (agent != null) && (plugin != null)) //plugin node
            {
                agentMap.get(agent).setPluginParams(plugin, configMap);
                if (this.plugin.hasGlobalController()) {
                    MsgEvent ce = controllerMsgEvent(region, agent, plugin, "setparams");
                    ce.setParam("configparams", paramMap.get("msg"));
                    if (!this.plugin.getGlobalControllerChannel().setNodeParams(ce)) {
                        System.out.println("Controller : ControllerDB : Failed to setParams for Node on Controller");
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("Controller : ControllerDB : setNodeParams ERROR : " + ex.toString());
        }
    }

    public Map<String, String> getNodeParams(String region, String agent, String plugin) {
        try {
            if ((region != null) && (agent != null) && (plugin == null)) //agent node
            {
                return agentMap.get(agent).getAgentParams();
            } else if ((region != null) && (agent != null) && (plugin != null)) //plugin node
            {
                return agentMap.get(agent).getPluginParams(plugin);
            }
            return null;
        } catch (Exception ex) {
            System.out.println("Controller : ControllerDB : getNodeParams ERROR : " + ex.toString());
            return null;
        }
    }


    public void setNodeParam(String region, String agent, String plugin, String key, String value) {
        try {
            if ((region != null) && (agent != null) && (plugin == null)) //agent node
            {
                agentMap.get(agent).setAgentParam(key, value);
            } else if ((region != null) && (agent != null) && (plugin != null)) //plugin node
            {
                agentMap.get(agent).setPluginParam(plugin, key, value);
            }
        } catch (Exception ex) {
            System.out.println("Controller : ControllerDB : setNodeParam ERROR : " + ex.toString());
        }
    }

    public void removeNode(String region, String agent, String plugin) {
        try {
            if ((region != null) && (agent != null) && (plugin == null)) //agent node
            {
                agentMap.remove(agent);
                //controller
                if (this.plugin.hasGlobalController()) {
                    if (!this.plugin.getGlobalControllerChannel().removeNode(controllerMsgEvent(region, agent, plugin, "removenode"))) {
                        System.out.println("Controller : ControllerDB : Failed to addNode to Controller");
                    }
                }
            } else if ((region != null) && (agent != null) && (plugin != null)) //plugin node
            {
                agentMap.get(agent).removePlugin(plugin);
                //controller
                if (this.plugin.hasGlobalController()) {
                    if (!this.plugin.getGlobalControllerChannel().removeNode(controllerMsgEvent(region, agent, plugin, "removenode"))) {
                        System.out.println("Controller : ControllerDB : Failed to addNode to Controller");
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("Controller : ControllerDB : removeNode ERROR : " + ex.toString());
        }
    }
}
