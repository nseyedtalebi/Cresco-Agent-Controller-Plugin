package com.researchworx.cresco.controller.core;

import com.google.gson.Gson;
import com.researchworx.cresco.controller.regionalcontroller.RegionHealthWatcher;
import com.researchworx.cresco.library.messaging.MsgEvent;
import com.researchworx.cresco.library.plugin.core.CExecutor;
import com.researchworx.cresco.library.utilities.CLogger;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

class Executor extends CExecutor {
    private Launcher mainPlugin;
    private CLogger logger;
    public RegionHealthWatcher regionHealthWatcher;
    private Gson gson;

    private long messageCount = 0;

    Executor(Launcher plugin) {
        super(plugin);
        this.mainPlugin = plugin;
        this.logger = new CLogger(Executor.class, plugin.getMsgOutQueue(), plugin.getRegion(), plugin.getAgent(), plugin.getPluginID(), CLogger.Level.Info);
        this.gson = new Gson();
    }

    @Override
    public MsgEvent processExec(MsgEvent ce) {
        logger.trace("Processing Exec message");

            switch (ce.getParam("action")) {

                case "getfreeports":
                    return getFreePort(ce);
                case "ping":
                    return pingReply(ce);
                case "noop":
                    noop();
                    break;

                default:
                    logger.error("Unknown configtype found {} for {}:", ce.getParam("action"), ce.getMsgType().toString());

            }

        return null;
    }

    @Override
    public MsgEvent processWatchDog(MsgEvent ce) {
        return forwardToRegion(ce);
    }

    @Override
    public MsgEvent processConfig(MsgEvent ce) {
        logger.trace("Processing Config message");

            switch (ce.getParam("action")) {
                case "comminit":
                    return commInit(ce);
                case "agent_enable":
                    return forwardToRegion(ce);
                case "agent_disable":
                    return forwardToRegion(ce);

                default:
                    logger.error("Unknown configtype found {} for {}:", ce.getParam("action"), ce.getMsgType().toString());
            }
        return null;
    }

    @Override
    public MsgEvent processKPI(MsgEvent ce) {
        logger.trace("Processing KPI message");
        return forwardToGlobal(ce);
    }

    private MsgEvent pingReply(MsgEvent msg) {


        if(msg.getParam("reversecount") != null) {

            long starttime = System.currentTimeMillis();
            int count = 1;

            int samples = Integer.parseInt(msg.getParam("reversecount"));

            //RPCCall rpc = new RPCCall();

            while(count < samples) {
                MsgEvent me = new MsgEvent(MsgEvent.Type.EXEC, mainPlugin.getRegion(), mainPlugin.getAgent(), mainPlugin.getPluginID(), "external");
                me.setParam("action","ping");
                //me.setParam("action","noop");
                me.setParam("src_region", mainPlugin.getRegion());
                me.setParam("src_agent", mainPlugin.getAgent());
                me.setParam("src_plugin", mainPlugin.getPluginID());
                me.setParam("dst_region", me.getParam("src_region"));
                me.setParam("dst_agent", me.getParam("src_agent"));
                if(msg.getParam("src_plugin") != null) {
                    me.setParam("dst_plugin", msg.getParam("src_plugin"));
                }
                me.setParam("count",String.valueOf(count));
                //msgIn(me);
                //System.out.print(".");
                MsgEvent re = mainPlugin.getRPC().call(me);
                count++;
            }

            long endtime = System.currentTimeMillis();
            long elapsed = (endtime - starttime);
            float timemp = elapsed/samples;
            float mps = -1;
            try {
                mps = samples / ((endtime - starttime) / 1000);
            } catch(Exception ex) {
                //do nothing
            }
            msg.setParam("elapsedtime",String.valueOf(elapsed));
            msg.setParam("timepermessage",String.valueOf(timemp));
            msg.setParam("mps",String.valueOf(mps));
            msg.setParam("samples",String.valueOf(samples));
        }

        msg.setParam("action","pong");
        logger.debug("ping message type found");
        msg.setParam("remote_ts", String.valueOf(System.currentTimeMillis()));
        msg.setParam("type", "agent_controller");
        logger.debug("Returning communication details to Cresco agent");
        return msg;

    }

    private MsgEvent forwardToGlobal(MsgEvent le) {

        le.setParam("dst_region", mainPlugin.cstate.getRegionalRegion());
        le.setParam("dst_agent", mainPlugin.cstate.getRegionalAgent());
        le.setParam("dst_plugin",mainPlugin.cstate.getControllerId());
        le.setParam("is_global", Boolean.TRUE.toString());

        plugin.sendMsgEvent(le);
        //set it and forget it!
        return null;

    }

    private MsgEvent forwardToRegion(MsgEvent le) {

        le.setParam("dst_region", mainPlugin.cstate.getRegionalRegion());
        le.setParam("dst_agent", mainPlugin.cstate.getRegionalAgent());
        le.setParam("dst_plugin",mainPlugin.cstate.getControllerId());
        le.setParam("is_regional", Boolean.TRUE.toString());

        plugin.sendMsgEvent(le);
        //set it and forget it!
        return null;

    }

    private void noop() {

    }

        /*

        if (msg.getParam("configtype") == null || msg.getMsgBody() == null) return null;
        logger.debug("Config-type is properly set, as well as message body");
        switch (msg.getParam("action")) {
            case "comminit":
                return commInit(msg);
            case "enablenetdiscovery":
                return enableNetworkDiscovery(msg);
            case "disablenetdiscovery":
                return disableNetworkDiscovery(msg);
            case "staticnetworkdiscovery":
                return staticNetworkDiscovery(msg);
            default:
                logger.debug("Unknown configtype found: {}", msg.getParam("configtype"));
                return null;
        }
        */



    MsgEvent staticNetworkDiscovery(MsgEvent msg) {
        boolean isEnabled = false;
        try {
            if(msg.getParam("action_iplist") != null) {
                String ipliststr = msg.getParam("action_iplist");
                List<String> iplist = new ArrayList<>();
                if(ipliststr.contains(",")) {
                    String[] iplistar = ipliststr.split(",");
                    for(String ip : iplistar) {
                        iplist.add(ip);
                    }
                } else {
                    iplist.add(ipliststr);
                }

                String discoveryListString = mainPlugin.getPerfMonitorNet().getStaticNetworkDiscovery(iplist);
                msg.setCompressedParam("network_map", discoveryListString);

            } else {
                logger.error("staticNetworkDiscovery: no ip list");
            }

        } catch(Exception ex) {
            logger.error("staticNetworkDiscovery: " + ex.getMessage());
            msg.setParam("error", ex.getMessage());
        }
        msg.setParam("is_complete",String.valueOf(isEnabled));
        return msg;
    }

    MsgEvent enableNetworkDiscovery(MsgEvent msg) {
        boolean isEnabled = false;
        try {
            if(mainPlugin.startNetDiscoveryEngine()) {
                isEnabled = true;
            } else {
                msg.setParam("error", "Network Discovery Failed to Start");
            }
        } catch(Exception ex) {
            logger.error("enableNetworkDiscovery: " + ex.getMessage());
            msg.setParam("error", ex.getMessage());
        }
        msg.setParam("is_discoveryactive",String.valueOf(isEnabled));
        return msg;
    }

    MsgEvent disableNetworkDiscovery(MsgEvent msg) {
        boolean isDisabled = false;
        try {
            if(mainPlugin.stopNetDiscoveryEngine()) {
                isDisabled = true;
            }
            msg.setParam("error", "Network Discovery Failed to Stop");
        } catch(Exception ex) {
            logger.error("disableNetworkDiscovery: " + ex.getMessage());
            msg.setParam("error", ex.getMessage());
        }
        msg.setParam("is_discoveryactive",String.valueOf(isDisabled));
        return msg;
    }

    MsgEvent commInit(MsgEvent msg) {
        logger.debug("comminit message type found");
        //String initAgent = msg.getParam("src_agent");
        //String initRegion = msg.getParam("src_region");

        while(!mainPlugin.commInit());
        //msg.setParam("src_agent",initAgent);
        //msg.setParam("src_region",initRegion);
        //msg.setParam("dst_agent",initAgent);
        //msg.setParam("dst_region",initRegion);
        msg.setParam("set_region", this.plugin.getRegion());
        msg.setParam("set_agent", this.plugin.getAgent());
        msg.setParam("is_regional_controller", Boolean.toString(this.mainPlugin.cstate.isRegionalController()));
        msg.setParam("is_global_controller", Boolean.toString(this.mainPlugin.cstate.isGlobalController()));
        msg.setParam("is_active", Boolean.toString(this.plugin.isActive()));
        logger.debug("Returning communication details to Cresco agent");

        return msg;
    }

    MsgEvent getFreePort(MsgEvent msg) {

        try {

            String portCount = msg.getParam("action_portcount");

            InetAddress addr = InetAddress.getLocalHost();
            String ip = addr.getHostAddress();
            Map<String, List<Map<String, String>>> portMap = new HashMap<>();
            List<Map<String, String>> portList = new ArrayList<>();

            for (int i = 0; i < Integer.parseInt(portCount); i++) {
                int port = getPort();
                if (port != -1) {
                    Map<String, String> tmpP = new HashMap<>();
                    tmpP.put("ip", ip);
                    tmpP.put("port", String.valueOf(port));
                    portList.add(tmpP);
                }
            }

            portMap.put("ports", portList);

            String freeports = gson.toJson(portMap);
            msg.setCompressedParam("freeports",freeports);

        } catch (Exception ex) {
            logger.error(ex.getMessage());
        }

        return msg;
    }

    public int getPort() {

        int freePort = -1;

        boolean isFree = false;

        while (!isFree) {
            int port = ThreadLocalRandom.current().nextInt(10000, 30000 + 1);
            ServerSocket ss = null;
            DatagramSocket ds = null;
            try {
                ss = new ServerSocket(port);
                ss.setReuseAddress(true);
                ds = new DatagramSocket(port);
                ds.setReuseAddress(true);
                isFree = true;
                freePort = port;

            } catch (IOException e) {
            } finally {
                if (ds != null) {
                    ds.close();
                }

                if (ss != null) {
                    try {
                        ss.close();
                    } catch (IOException e) {
                        /* should not be thrown */
                    }
                }
            }
        }
        return freePort;
    }
}
