package com.researchworx.cresco.controller.db;

import com.orientechnologies.orient.core.command.OCommandOutputListener;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.tool.ODatabaseExport;
import com.orientechnologies.orient.core.db.tool.ODatabaseImport;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.researchworx.cresco.controller.core.Launcher;
import com.researchworx.cresco.library.utilities.CLogger;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory;
import com.tinkerpop.blueprints.impls.orient.OrientGraphNoTx;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


public class DBBaseFunctions {

    private Launcher plugin;
    private CLogger logger;
    private OrientGraphFactory factory;
    private ODatabaseDocumentTx db;
    private int retryCount;
    //private DBEngine dbe;
    public OPartitionedDatabasePool pool;

    public String[] aNodeIndexParams = {"platform","environment","location"};

    private OrientGraphFactory getFactory() {
        OrientGraphFactory getFactory = null;

        try {
            String host = plugin.getConfig().getStringParam("gdb_host");
            String username = plugin.getConfig().getStringParam("gdb_username");
            String password = plugin.getConfig().getStringParam("gdb_password");
            String dbname = plugin.getConfig().getStringParam("gdb_dbname");

            String connection_string = "remote:" + host + "/" + dbname;


            if ((host != null) && (username != null) && (password != null) && (dbname != null)) {
                getFactory = new OrientGraphFactory(connection_string, username, password).setupPool(1, 20);
            }
            else {
                getFactory = new OrientGraphFactory("memory:internalDb").setupPool(1, 20);
            }
        }
        catch(Exception ex) {
            logger.error("isMemory : " + ex.getMessage());
        }
        return getFactory;

    }

    public DBBaseFunctions(Launcher plugin, DBEngine dbe) {
        this.logger = new CLogger(DBBaseFunctions.class, plugin.getMsgOutQueue(), plugin.getRegion(), plugin.getAgent(), plugin.getPluginID(), CLogger.Level.Info);
        this.plugin = plugin;
        //this.factory = dbe.factory;
        //this.factory = getFactory();
        this.factory = dbe.factory;
        this.db = dbe.db;
        this.pool = dbe.pool;
        this.retryCount = plugin.getConfig().getIntegerParam("db_retry_count",50);

        //ODatabaseDocumentTx db = ODatabaseDocumentPool.global().acquire("memory:MyDb", "admin", "admin");

        //create basic cresco constructs
        initCrescoDB();
    }

    //new database functions
    //READS

    public List<String> getANodeFromIndex(String indexName, String indexValue) {
        List<String> nodeList = null;
        OrientGraph graph = null;
        try
        {
            nodeList = new ArrayList<String>();
            graph = factory.getTx();
            Iterable<Vertex> resultIterator = null;
            if(indexValue == null) {
                resultIterator = graph.command(new OCommandSQL("SELECT rid FROM INDEX:aNode'")).execute();
            }
            else {
                resultIterator = graph.command(new OCommandSQL("SELECT rid FROM INDEX:aNode." + indexName + " WHERE key = '" + indexValue + "'")).execute();
            }
            Iterator<Vertex> iter = resultIterator.iterator();
            while(iter.hasNext())
            //if(iter.hasNext())
            {
                Vertex v = iter.next();
                String node_id = v.getProperty("rid").toString();
                if(node_id != null)
                {
                    node_id = node_id.substring(node_id.indexOf("[") + 1, node_id.indexOf("]"));
                    nodeList.add(node_id);
                }
            }

        }
        catch(Exception ex)
        {
            logger.debug("DBEngine : getANodeFromIndex : Error " + ex.toString());
            nodeList = null;
        }
        finally
        {
            if(graph != null)
            {
                graph.shutdown();
            }
        }
        return nodeList;
    }

    public String getNodeId(String region, String agent, String plugin)
    {
        String node_id = null;
        OrientGraph graph = null;

        try
        {

            if((region != null) && (agent == null) && (plugin == null))
            {
                graph = factory.getTx();

                Iterable<Vertex> resultIterator = graph.command(new OCommandSQL("SELECT rid FROM INDEX:rNode.nodePath WHERE key = '" + region + "'")).execute();

                Iterator<Vertex> iter = resultIterator.iterator();
                if(iter.hasNext())
                {
                    Vertex v = iter.next();
                    node_id = v.getProperty("rid").toString();
                }
                if(node_id != null)
                {
                    node_id = node_id.substring(node_id.indexOf("[") + 1, node_id.indexOf("]"));
                }
                //return isFound;
            }
            else if((region != null) && (agent != null) && (plugin == null))
            {
                //OrientGraph graph = factory.getTx();
                //OrientGraphNoTx graph = factory.getNoTx();
                graph = factory.getTx();
                Iterable<Vertex> resultIterator = graph.command(new OCommandSQL("SELECT rid FROM INDEX:aNode.nodePath WHERE key = [\"" + region + "\",\"" + agent + "\"]")).execute();
                Iterator<Vertex> iter = resultIterator.iterator();
                if(iter.hasNext())
                {
                    Vertex v = iter.next();
                    node_id = v.getProperty("rid").toString();
                }
                //graph.shutdown();
                //return node_id.substring(node_id.indexOf("[") + 1, node_id.indexOf("]"));
                //node_id = node_id.substring(node_id.indexOf("[") + 1, node_id.indexOf("]"));
                if(node_id != null)
                {
                    node_id = node_id.substring(node_id.indexOf("[") + 1, node_id.indexOf("]"));
                }
            }
            else if((region != null) && (agent != null) && (plugin != null))
            {
                /*
                //TransactionalGraph tgraph = factory.getTx();
                OIndex<?> idx = factory.getDatabase().getMetadata().getIndexManager().getIndex("pNode.nodePath");
                String key = "[\"" + region + "\",\"" + agent + "\",\"" + plugin +"\"]";
                //OIdentifiable rec = idx.get(key);
                Object luke = (Object)idx.get(key);
                logger.error(luke.toString());
                */
                //OIndex<OIdentifiable> idx = factory.getDatabase().getMetadata().getIndexManager().getIndex("pNode.nodePath");
                //OIdentifiable rec = idx.get(id);

                //OrientGraph graph = factory.getTx();
                //OrientGraphNoTx graph = factory.getNoTx();
                //graph = factory.getTx();
                //OrientGraphNoTx g = null;
                //g = factory.getNoTx();
                graph = factory.getTx();

                String query = "SELECT rid FROM INDEX:pNode.nodePath WHERE key = [\"" + region + "\",\"" + agent + "\",\"" + plugin +"\"]";
                Iterable<Vertex> resultIterator = graph.command(new OCommandSQL(query)).execute();
                Iterator<Vertex> iter = resultIterator.iterator();
                if(iter.hasNext())
                {
                    Vertex v = iter.next();
                    node_id = v.getProperty("rid").toString();
                }
                //graph.shutdown();
                //return node_id.substring(node_id.indexOf("[") + 1, node_id.indexOf("]"));
                //node_id = node_id.substring(node_id.indexOf("[") + 1, node_id.indexOf("]"));
                if(node_id != null)
                {
                    node_id = node_id.substring(node_id.indexOf("[") + 1, node_id.indexOf("]"));
                }

            }

        }
        catch(Exception ex)
        {
            logger.debug("DBEngine : getNodeID : Error " + ex.toString());
        }
        finally
        {
            if(graph != null)
            {
                graph.shutdown();
            }
        }
        return node_id;
    }

    public List<String> getNodeIds(String region, String agent, String plugin, boolean getAll)
    {
        OrientGraph graph = null;
        List<String> nodeIdList = null;
        try
        {
            nodeIdList = new ArrayList();

            if((region == null) && (agent == null) && (plugin == null))
            {
                //OrientGraphNoTx graph = factory.getNoTx();
                graph = factory.getTx();
                Iterable<Vertex> resultIterator = null;
                resultIterator = graph.command(new OCommandSQL("SELECT rid FROM INDEX:rNode.nodePath")).execute();
                Iterator<Vertex> iter = resultIterator.iterator();
                while(iter.hasNext())
                {
                    Vertex v = iter.next();
                    String node_id = v.getProperty("rid").toString();

                    if(node_id != null)
                    {
                        node_id = node_id.substring(node_id.indexOf("[") + 1, node_id.indexOf("]"));
                        nodeIdList.add(node_id);

                    }

                }

            }
            else if((region != null) && (agent == null) && (plugin == null))
            {
                //OrientGraph graph = factory.getTx();
                //OrientGraphNoTx graph = factory.getNoTx();
                graph = factory.getTx();
                Iterable<Vertex> resultIterator = null;
                if(getAll) {
                    resultIterator = graph.command(new OCommandSQL("SELECT rid FROM INDEX:aNode.nodePath")).execute();
                }
                else {
                    resultIterator = graph.command(new OCommandSQL("SELECT rid FROM INDEX:aNode.nodePath WHERE key = [\"" + region + "\"]")).execute();
                }
                Iterator<Vertex> iter = resultIterator.iterator();
                while(iter.hasNext())
                {
                    Vertex v = iter.next();
                    String node_id = v.getProperty("rid").toString();
                    if(node_id != null)
                    {
                        node_id = node_id.substring(node_id.indexOf("[") + 1, node_id.indexOf("]"));
                        nodeIdList.add(node_id);

                    }
                }

            }
            else if((region != null) && (agent != null) && (plugin == null))
            {
                //OrientGraph graph = factory.getTx();
                //OrientGraphNoTx graph = factory.getNoTx();
                graph = factory.getTx();
                Iterable<Vertex> resultIterator = null;

                if(getAll) {
                    resultIterator = graph.command(new OCommandSQL("SELECT rid FROM INDEX:pNode.nodePath")).execute();
                }
                else {
                    resultIterator = graph.command(new OCommandSQL("SELECT rid FROM INDEX:pNode.nodePath WHERE key = [\"" + region + "\",\"" + agent + "\"]")).execute();
                }

                Iterator<Vertex> iter = resultIterator.iterator();
                while(iter.hasNext())
                {
                    Vertex v = iter.next();
                    String node_id = v.getProperty("rid").toString();
                    if(node_id != null)
                    {
                        node_id = node_id.substring(node_id.indexOf("[") + 1, node_id.indexOf("]"));
                        nodeIdList.add(node_id);

                    }
                }
            }

        }
        catch(Exception ex)
        {
            logger.debug("DBEngine : getNodeID : Error " + ex.toString());
        }
        finally
        {
            if(graph != null)
            {
                graph.shutdown();
            }
        }
        return nodeIdList;
    }

    public List<String> getNodeList(String region, String agent, String plugin)
    {
        List<String> node_list = null;
        OrientGraph graph = null;
        try
        {

            if((region == null) && (agent == null) && (plugin == null))
            {
                //OrientGraph graph = factory.getTx();
                //OrientGraphNoTx graph = factory.getNoTx();
                graph = factory.getTx();
                Iterable<Vertex> resultIterator = graph.command(new OCommandSQL("SELECT rid FROM INDEX:rNode.nodePath")).execute();

                Iterator<Vertex> iter = resultIterator.iterator();
                if(iter.hasNext())
                {
                    node_list = new ArrayList<String>();
                    while(iter.hasNext())
                    {
                        Vertex v = iter.next();
                        String node_id = v.getProperty("rid").toString();
                        node_id = node_id.substring(node_id.indexOf("[") + 1, node_id.indexOf("]"));
                        Vertex rNode = graph.getVertex(node_id);
                        node_list.add(rNode.getProperty("region").toString());
                    }
                }

            }
            else if((region != null) && (agent == null) && (plugin == null))
            {
                graph = factory.getTx();
                //OrientGraphNoTx graph = factory.getNoTx();

                Iterable<Vertex> resultIterator = graph.command(new OCommandSQL("SELECT rid FROM INDEX:aNode.nodePath WHERE key = [\"" + region + "\"]")).execute();
                Iterator<Vertex> iter = resultIterator.iterator();
                if(iter.hasNext())
                {
                    node_list = new ArrayList<String>();
                    while(iter.hasNext())
                    {
                        Vertex v = iter.next();
                        String node_id = v.getProperty("rid").toString();
                        node_id = node_id.substring(node_id.indexOf("[") + 1, node_id.indexOf("]"));
                        Vertex aNode = graph.getVertex(node_id);
                        node_list.add(aNode.getProperty("agent").toString());
                    }
                }

            }
            else if((region != null) && (agent != null) && (plugin == null))
            {
                graph = factory.getTx();
                //OrientGraphNoTx graph = factory.getNoTx();

                Iterable<Vertex> resultIterator = graph.command(new OCommandSQL("SELECT rid FROM INDEX:pNode.nodePath WHERE key = [\"" + region + "\",\"" + agent +"\"]")).execute();
                Iterator<Vertex> iter = resultIterator.iterator();
                if(iter.hasNext())
                {
                    node_list = new ArrayList<String>();
                    while(iter.hasNext())
                    {
                        Vertex v = iter.next();
                        String node_id = v.getProperty("rid").toString();
                        node_id = node_id.substring(node_id.indexOf("[") + 1, node_id.indexOf("]"));
                        Vertex pNode = graph.getVertex(node_id);
                        node_list.add(pNode.getProperty("plugin").toString());
                    }
                }
                //graph.shutdown();
                //return node_list;

            }

        }
        catch(Exception ex)
        {
            logger.debug("GrapgDBEngine : getNodeList : Error " + ex.toString());
        }
        finally
        {
            if(graph != null)
            {
                graph.shutdown();
            }
        }
        return node_list;
    }

    public String getNodeParam(String node_id, String param)
    {
        String paramVal = null;

        int count = 0;
        try
        {

            while((paramVal == null) && (count != retryCount))
            {
                if(count > 0)
                {
                    Thread.sleep((long)(Math.random() * 1000)); //random wait to prevent sync error
                }
                paramVal = IgetNodeParam(node_id,param);
                count++;

            }

            if((paramVal == null) && (count == retryCount))
            {
                logger.debug("DBEngine : getNodeParam : Failed to add node in " + count + " retrys");
            }
        }
        catch(Exception ex)
        {
            logger.debug("DBEngine : getNodeParam : Error " + ex.toString());
        }

        return paramVal;
    }

    public String getNodeParam(String region, String agent, String plugin, String param)
    {
        String paramVal = null;
        String node_id  = getNodeId(region,agent,plugin);

        int count = 0;
        try
        {

            while((paramVal == null) && (count != retryCount))
            {
                if(count > 0)
                {
                    Thread.sleep((long)(Math.random() * 1000)); //random wait to prevent sync error
                }
                paramVal = IgetNodeParam(node_id,param);
                count++;

            }

            if((paramVal == null) && (count == retryCount))
            {
                logger.debug("DBEngine : getNodeParam : Failed to add node in " + count + " retrys");
            }
        }
        catch(Exception ex)
        {
            logger.debug("DBEngine : getNodeParam : Error " + ex.toString());
        }

        return paramVal;
    }

    public String IgetNodeParam(String node_id, String param)
    {
        String node_param = null;
        OrientGraph graph = null;

        try
        {
            graph = factory.getTx();
            Vertex iNode = graph.getVertex(node_id);
            node_param = iNode.getProperty(param).toString();

        }
        catch(Exception ex)
        {
            logger.debug("IgetNodeParam: Error " + ex.toString());
        }
        finally
        {
            if(graph != null)
            {
                graph.shutdown();
            }
        }
        return node_param;
    }

    public Map<String,String> IgetNodeParams(String node_id)
    {
        OrientGraph graph = null;
        Map<String,String> params = new HashMap();

        try
        {
            graph = factory.getTx();
            Vertex iNode = graph.getVertex(node_id);
            for(String key : iNode.getPropertyKeys()) {
                params.put(key,iNode.getProperty(key).toString());
            }
        }
        catch(Exception ex)
        {
            logger.debug("IgetNodeParams: Error " + ex.toString());
        }
        finally
        {
            if(graph != null)
            {
                graph.shutdown();
            }
        }
        return params;
    }

    public Map<String,String> getNodeParams(String node_id)
    {
        Map<String,String> paramsVal = null;

        int count = 0;
        try
        {

            while((paramsVal == null) && (count != retryCount))
            {
                if(count > 0)
                {
                    Thread.sleep((long)(Math.random() * 1000)); //random wait to prevent sync error
                }
                paramsVal = IgetNodeParams(node_id);
                count++;

            }

            if((paramsVal == null) && (count == retryCount))
            {
                logger.debug("DBEngine : getNodeParams : Failed to add node in " + count + " retrys");
            }
        }
        catch(Exception ex)
        {
            logger.debug("DBEngine : getNodeParams : Error " + ex.toString());
        }

        return paramsVal;
    }

    //WRITES

    private String IaddNode(String region, String agent, String plugin)
    {
        String node_id = null;
        OrientGraph graph = null;
        try
        {
            node_id = getNodeId(region,agent,plugin);

            if(node_id != null)
            {
                //logger.debug("Node already Exist: region=" + region + " agent=" + agent + " plugin=" + plugin);
            }
            else
            {
                //logger.debug("Adding Node : region=" + region + " agent=" + agent + " plugin=" + plugin);
                if((region != null) && (agent == null) && (plugin == null))
                {
                    graph = factory.getTx();
                    Vertex v = graph.addVertex("class:rNode");
                    v.setProperty("region", region);
                    graph.commit();
                    node_id = v.getId().toString();
                }
                else if((region != null) && (agent != null) && (plugin == null))
                {
                    String region_id = getNodeId(region,null,null);

                    if(region_id == null)
                    {
                        //logger.debug("Must add region=" + region + " before adding agent=" + agent);
                        region_id = addNode(region,null,null);

                    }
                    if(region_id != null)
                    {
                        graph = factory.getTx();
                        Vertex v = graph.addVertex("class:aNode");
                        v.setProperty("region", region);
                        v.setProperty("agent", agent);

                        Vertex fromV = graph.getVertex(v.getId().toString());
                        Vertex toV = graph.getVertex(region_id);

                        graph.addEdge("class:isAgent", fromV, toV, "isAgent");
                        graph.commit();
                        node_id = v.getId().toString();
						/*
				    	//add edges

				    	String edge_id = addEdge(region,agent,null,region,null,null,"isAgent");
				    	if(edge_id == null)
				    	{
				    		logger.debug("Unable to add isAgent Edge between region=" + region + " and agent=" + agent);
				    	}
						 */
                    }
                }
                else if((region != null) && (agent != null) && (plugin != null))
                {
                    //logger.debug("Adding Plugin : region=" + region + " agent=" + agent + " plugin=" + plugin);

                    String agent_id = getNodeId(region,agent,null);
                    if(agent_id == null)
                    {
                        //logger.debug("For region=" + region + " we must add agent=" + agent + " before adding plugin=" + plugin);
                        agent_id = addNode(region,agent,null);

                    }

                    if(agent_id != null)
                    {
                        graph = factory.getTx();
                        Vertex v = graph.addVertex("class:pNode");
                        v.setProperty("region", region);
                        v.setProperty("agent", agent);
                        v.setProperty("plugin", plugin);

                        Vertex fromV = graph.getVertex(v.getId().toString());
                        Vertex toV = graph.getVertex(agent_id);

                        graph.addEdge("class:isPlugin", fromV, toV, "isPlugin");
                        graph.commit();
					    /*
					    //add Edge
					    String edge_id = addEdge(region,agent,plugin,region,agent,null,"isPlugin");
					    if(edge_id == null)
					    {
					    	logger.debug("Unable to add isPlugin Edge between region=" + region + " agent=" + "agent=" + region + " and agent=" + agent);
					    }
					    */
                        node_id = v.getId().toString();
                    }
                }
            }

        }
        catch(com.orientechnologies.orient.core.storage.ORecordDuplicatedException exc)
        {
            //eat exception.. this is not normal and should log somewhere
        }
        catch(com.orientechnologies.orient.core.exception.OConcurrentModificationException exc)
        {
            //eat exception.. this is normal
        }
        catch(Exception ex)
        {
            long threadId = Thread.currentThread().getId();
            logger.debug("IaddNode: thread_id: " + threadId + " Error " + ex.toString());
        }
        finally
        {
            if(graph != null)
            {
                graph.shutdown();
            }
        }
        return node_id;

    }

    public String addNode(String region, String agent, String plugin)
    {
        String node_id = null;
        int count = 0;
        try
        {

            while((node_id == null) && (count != retryCount))
            {
                if(count > 0)
                {
                    //logger.debug("ADDNODE RETRY : region=" + region + " agent=" + agent + " plugin" + plugin);
                    Thread.sleep((long)(Math.random() * 1000)); //random wait to prevent sync error
                }
                node_id = IaddNode(region, agent, plugin);
                count++;

            }

            if((node_id == null) && (count == retryCount))
            {
                logger.debug("DBEngine : addNode : Failed to add node in " + count + " retrys");
            }
        }
        catch(Exception ex)
        {
            logger.debug("DBEngine : addNode : Error " + ex.toString());
        }

        return node_id;
    }

    public String addEdge(String src_region, String src_agent, String src_plugin, String dst_region, String dst_agent, String dst_plugin, String className)
    {
        String edge_id = null;
        int count = 0;
        try
        {

            while((edge_id == null) && (count != retryCount))
            {
                edge_id = IaddEdge(src_region, src_agent, src_plugin, dst_region, dst_agent, dst_plugin, className);
                count++;
            }

            if((edge_id == null) && (count == retryCount))
            {
                logger.debug("DBEngine : addEdge : Failed to add node in " + count + " retrys");
            }
        }
        catch(Exception ex)
        {
            logger.debug("DBEngine : addEdge : Error " + ex.toString());
        }

        return edge_id;
    }

    private String IaddEdge(String src_region, String src_agent, String src_plugin, String dst_region, String dst_agent, String dst_plugin, String className)
    {
        String edge_id = null;
        try
        {
            String src_node_id = getNodeId(src_region,src_agent,src_plugin);
            String dst_node_id = getNodeId(dst_region,dst_agent,dst_plugin);

            OrientGraph graph = factory.getTx();
            Vertex fromV = graph.getVertex(src_node_id);
            Vertex toV = graph.getVertex(dst_node_id);

            Edge isEdge = graph.addEdge("class:" + className, fromV, toV, className);
            graph.commit();
            graph.shutdown();
            edge_id = isEdge.getId().toString();
        }
        catch(com.orientechnologies.orient.core.exception.OConcurrentModificationException exc)
        {
            //eat exception
        }
        catch(Exception ex)
        {
            logger.debug("addEdge Error: " + ex.toString());

        }
        return edge_id;

    }

    public boolean removeNode(String region, String agent, String plugin)
    {
        boolean nodeRemoved = false;
        int count = 0;
        try
        {

            while((!nodeRemoved) && (count != retryCount))
            {
                if(count > 0)
                {
                    //logger.debug("REMOVENODE RETRY : region=" + region + " agent=" + agent + " plugin" + plugin);
                    Thread.sleep((long)(Math.random() * 1000)); //random wait to prevent sync error
                }
                nodeRemoved = IremoveNode(region, agent, plugin);
                count++;

            }

            if((!nodeRemoved) && (count == retryCount))
            {
                logger.debug("DBEngine : removeNode : Failed to add node in " + count + " retrys");
            }
        }
        catch(Exception ex)
        {
            logger.debug("DBEngine : removeNode : Error " + ex.toString());
        }

        return nodeRemoved;
    }

    private boolean IremoveNode(String region, String agent, String plugin)
    {
        boolean nodeRemoved = false;
        OrientGraph graph = null;
        try
        {
            //String pathname = getPathname(region,agent,plugin);
            String node_id = getNodeId(region,agent,plugin);
            if(node_id == null)
            {
                //logger.debug("Tried to remove missing node : " + pathname);
                nodeRemoved = true;
            }
            else
            {
                if((region != null) && (agent == null) && (plugin == null))
                {
                    List<String> agentList = getNodeList(region,null,null);
                    if(agentList != null)
                    {
                        for(String removeAgent : agentList)
                        {
                            removeNode(region,removeAgent,null);
                        }
                    }
                    agentList = getNodeList(region,null,null);
                    if(agentList == null)
                    {
                        graph = factory.getTx();
                        Vertex rNode = graph.getVertex(node_id);
                        graph.removeVertex(rNode);
                        graph.commit();
                        nodeRemoved = true;
                    }

                }
                if((region != null) && (agent != null) && (plugin == null))
                {

                    List<String> pluginList = getNodeList(region,agent,null);
                    if(pluginList != null)
                    {
                        for(String removePlugin : pluginList)
                        {
                            removeNode(region,agent,removePlugin);
                        }
                    }
                    pluginList = getNodeList(region,agent,null);
                    if(pluginList == null)
                    {
                        graph = factory.getTx();
                        Vertex aNode = graph.getVertex(node_id);
                        graph.removeVertex(aNode);
                        graph.commit();
                        nodeRemoved = true;
                    }
                }
                if((region != null) && (agent != null) && (plugin != null))
                {
                    graph = factory.getTx();
                    Vertex pNode = graph.getVertex(node_id);
                    graph.removeVertex(pNode);
                    graph.commit();
                    nodeRemoved = true;
                }

            }
        }
        catch(com.orientechnologies.orient.core.exception.OConcurrentModificationException exc)
        {
            //eat exception
        }
        catch(Exception ex)
        {
            long threadId = Thread.currentThread().getId();
            logger.debug("GrapgDBEngine : removeNode :  thread_id: " + threadId + " Error " + ex.toString());
        }
        finally
        {
            if(graph != null)
            {
                graph.shutdown();
            }
        }
        return nodeRemoved;

    }

    public boolean setNodeParams(String region, String agent, String plugin, Map<String,String> paramMap)
    {
        boolean isUpdated = false;
        int count = 0;
        try
        {

            while((!isUpdated) && (count != retryCount))
            {
                if(count > 0)
                {
                    //logger.debug("iNODEUPDATE RETRY : region=" + region + " agent=" + agent + " plugin" + plugin);
                    Thread.sleep((long)(Math.random() * 1000)); //random wait to prevent sync error
                }
                isUpdated = IsetNodeParams(region, agent, plugin, paramMap);
                count++;

            }

            if((!isUpdated) && (count == retryCount))
            {
                logger.debug("DBEngine : setINodeParams : Failed to add node in " + count + " retrys");
            }
        }
        catch(Exception ex)
        {
            logger.debug("DBEngine : setINodeParams : Error " + ex.toString());
        }

        return isUpdated;
    }

    public boolean IsetNodeParams(String region, String agent, String plugin, Map<String,String> paramMap)
    {

        boolean isUpdated = false;
        OrientGraph graph = null;
        String node_id = null;
        try
        {
            node_id = getNodeId(region,agent,plugin);
            if(node_id != null)
            {
                graph = factory.getTx();
                Vertex iNode = graph.getVertex(node_id);
                Iterator it = paramMap.entrySet().iterator();
                while (it.hasNext())
                {
                    Map.Entry pairs = (Map.Entry)it.next();
                    iNode.setProperty( pairs.getKey().toString(), pairs.getValue().toString());
                }
                graph.commit();
                isUpdated = true;
            }
        }
        catch(com.orientechnologies.orient.core.storage.ORecordDuplicatedException exc)
        {
            //eat exception.. this is not normal and should log somewhere
        }
        catch(com.orientechnologies.orient.core.exception.OConcurrentModificationException exc)
        {
            //eat exception.. this is normal
        }
        catch(Exception ex)
        {
            long threadId = Thread.currentThread().getId();
            logger.debug("setINodeParams: thread_id: " + threadId + " Error " + ex.toString());
        }
        finally
        {
            if(graph != null)
            {
                graph.shutdown();
            }
        }
        return isUpdated;
    }

    public boolean setNodeParam(String nodeId, String paramKey, String paramValue)
    {
        boolean isUpdated = false;
        int count = 0;
        try
        {

            while((!isUpdated) && (count != retryCount))
            {
                if(count > 0)
                {
                    //logger.debug("iNODEUPDATE RETRY : region=" + region + " agent=" + agent + " plugin" + plugin);
                    Thread.sleep((long)(Math.random() * 1000)); //random wait to prevent sync error
                }
                isUpdated = IsetNodeParam(nodeId, paramKey, paramValue);
                count++;

            }

            if((!isUpdated) && (count == retryCount))
            {
                logger.debug("DBEngine : setINodeParam : Failed to add node in " + count + " retrys");
            }
        }
        catch(Exception ex)
        {
            logger.debug("DBEngine : setINodeParam : Error " + ex.toString());
        }

        return isUpdated;
    }

    public boolean IsetNodeParam(String nodeId, String paramKey, String paramValue)
    {
        boolean isUpdated = false;
        OrientGraph graph = null;
        String node_id;
        try
        {
            node_id = nodeId;
            if(node_id != null)
            {
                graph = factory.getTx();
                Vertex iNode = graph.getVertex(node_id);

                iNode.setProperty( paramKey, paramValue);
                graph.commit();
                isUpdated = true;
            }
        }
        catch(com.orientechnologies.orient.core.storage.ORecordDuplicatedException exc)
        {
            //eat exception.. this is not normal and should log somewhere
        }
        catch(com.orientechnologies.orient.core.exception.OConcurrentModificationException exc)
        {
            //eat exception.. this is normal
        }
        catch(Exception ex)
        {
            long threadId = Thread.currentThread().getId();
            logger.debug("setINodeParams: thread_id: " + threadId + " Error " + ex.toString());
        }
        finally
        {
            if(graph != null)
            {
                graph.shutdown();
            }
        }
        return isUpdated;
    }

    public boolean setNodeParam(String region, String agent, String plugin, String paramKey, String paramValue)
    {
        boolean isUpdated = false;
        int count = 0;
        try
        {

            while((!isUpdated) && (count != retryCount))
            {
                if(count > 0)
                {
                    //logger.debug("iNODEUPDATE RETRY : region=" + region + " agent=" + agent + " plugin" + plugin);
                    Thread.sleep((long)(Math.random() * 1000)); //random wait to prevent sync error
                }
                isUpdated = IsetNodeParam(region, agent, plugin, paramKey, paramValue);
                count++;

            }

            if((!isUpdated) && (count == retryCount))
            {
                logger.debug("DBEngine : setINodeParam : Failed to add node in " + count + " retrys");
            }
        }
        catch(Exception ex)
        {
            logger.debug("DBEngine : setINodeParam : Error " + ex.toString());
        }

        return isUpdated;
    }

    public boolean IsetNodeParam(String region, String agent, String plugin, String paramKey, String paramValue)
    {
        boolean isUpdated = false;
        OrientGraph graph = null;
        String node_id = null;
        try
        {
            node_id = getNodeId(region, agent, plugin);
            if(node_id != null)
            {
                graph = factory.getTx();
                Vertex iNode = graph.getVertex(node_id);
                //set envparams if aNode
                if((paramKey.equals("configparams")) && (region != null) && (agent != null) && (plugin == null))
                {
                    String[] configParams = paramValue.split(",");
                    for(String cParam : configParams)
                    {
                        String[] cPramKV = cParam.split("=");
                        for(String indexParam : aNodeIndexParams)
                        {
                            if(cPramKV[0].equals(indexParam))
                            {
                                iNode.setProperty(cPramKV[0],cPramKV[1]);
                            }
                        }
                    }
                }
                iNode.setProperty( paramKey, paramValue);
                graph.commit();
                isUpdated = true;
            }
        }
        catch(com.orientechnologies.orient.core.storage.ORecordDuplicatedException exc)
        {
            //eat exception.. this is not normal and should log somewhere
        }
        catch(com.orientechnologies.orient.core.exception.OConcurrentModificationException exc)
        {
            //eat exception.. this is normal
        }
        catch(Exception ex)
        {
            long threadId = Thread.currentThread().getId();
            logger.debug("setINodeParams: thread_id: " + threadId + " Error " + ex.toString());
        }
        finally
        {
            if(graph != null)
            {
                graph.shutdown();
            }
        }
        return isUpdated;
    }

    //Base INIT Functions
    public void initCrescoDB()
    {
        try
        {
            //index properties

            logger.debug("Create Region Vertex Class");
            String[] rProps = {"region"}; //Property names
            createVertexClass("rNode", rProps);

            logger.debug("Create Agent Vertex Class");
            String[] aProps = {"region", "agent"}; //Property names
            createVertexClass("aNode", aProps);

            //indexes for searching
            logger.debug("Create Agent Vertex Class Index's");
            for(String indexName : aNodeIndexParams)
            {
                createVertexIndex("aNode", indexName, false);
            }

            logger.debug("Create Plugin Vertex Class");
            String[] pProps = {"region", "agent", "plugin"}; //Property names
            createVertexClass("pNode", pProps);

            logger.debug("Create isAgent Edge Class");
            String[] isAgentProps = {"edge_id"}; //Property names
            //createEdgeClass("isAgent",isAgentProps);
            createEdgeClass("isAgent",null);

            logger.debug("Create isPlugin Edge Class");
            String[] isPluginProps = {"edge_id"}; //Property names
            //createEdgeClass("isPlugin",isPluginProps);
            createEdgeClass("isPlugin",null);

        }
        catch(Exception ex)
        {
            logger.debug("Base initCrescoDB Error: " + ex.toString());
            StringWriter errors = new StringWriter();
            ex.printStackTrace(new PrintWriter(errors));
            logger.debug("Base initCrescoDB Error: " + errors.toString());

        }
    }

    //Class Create Functions
    boolean createVertexIndex(String className, String indexName, boolean isUnique)
    {
        boolean wasCreated = false;
        try
        {
            OrientGraphNoTx txGraph = factory.getNoTx();
            //OSchema schema = ((OrientGraph)odb).getRawGraph().getMetadata().getSchema();
            OSchema schema = txGraph.getRawGraph().getMetadata().getSchema();

            if (schema.existsClass(className))
            {
                OClass vt = txGraph.getVertexType(className);
                //OClass vt = txGraph.createVertexType(className);
                //vt.createProperty(indexName, OType.STRING);

                if(vt.getProperty(indexName) == null) {
                    vt.createProperty(indexName, OType.STRING);
                }

                if(isUnique)
                {
                    vt.createIndex(className + "." + indexName, OClass.INDEX_TYPE.UNIQUE, indexName);
                }
                else
                {
                    vt.createIndex(className + "." + indexName, OClass.INDEX_TYPE.NOTUNIQUE, indexName);
                }

                wasCreated = true;
            }

            txGraph.commit();
            txGraph.shutdown();
        }
        catch(Exception ex)
        {
            logger.debug("DBEngine : createVertexIndex : Error " + ex.toString());
        }

        return wasCreated;
    }

    boolean createVertexIndex(String className, String[] props, String indexName, boolean isUnique)
    {
        boolean wasCreated = false;
        OrientGraphNoTx txGraph = factory.getNoTx();
        //OSchema schema = ((OrientGraph)odb).getRawGraph().getMetadata().getSchema();
        OSchema schema = txGraph.getRawGraph().getMetadata().getSchema();

        if (schema.existsClass(className))
        {
            OClass vt = txGraph.getVertexType(className);
            //OClass vt = txGraph.createVertexType(className);
            for(String prop : props)
            {
                vt.createProperty(prop, OType.STRING);
            }
            if(isUnique)
            {
                vt.createIndex(className + "." + indexName, OClass.INDEX_TYPE.UNIQUE, props);
            }
            else
            {
                vt.createIndex(className + "." + indexName, OClass.INDEX_TYPE.NOTUNIQUE, props);
            }

            wasCreated = true;
        }
        txGraph.commit();
        txGraph.shutdown();
        return wasCreated;
    }

    boolean createVertexClass(String className, String[] props)
    {
        boolean wasCreated = false;
        OrientGraphNoTx txGraph = factory.getNoTx();
        //OSchema schema = ((OrientGraph)odb).getRawGraph().getMetadata().getSchema();
        OSchema schema = txGraph.getRawGraph().getMetadata().getSchema();

        if (!schema.existsClass(className))
        {
            OClass vt = txGraph.createVertexType(className);
            for(String prop : props)
                vt.createProperty(prop, OType.STRING);
            vt.createIndex(className + ".nodePath", OClass.INDEX_TYPE.UNIQUE, props);
            txGraph.commit();
            if (schema.existsClass(className)) {
                wasCreated = true;
            }
        }
        txGraph.shutdown();
        return wasCreated;
    }

    boolean createEdgeClass(String className, String[] props)
    {
        boolean wasCreated = false;
        OrientGraphNoTx txGraph = factory.getNoTx();
        //OSchema schema = ((OrientGraph)odb).getRawGraph().getMetadata().getSchema();
        OSchema schema = txGraph.getRawGraph().getMetadata().getSchema();

        if (!schema.existsClass(className))
        {
            OClass et = txGraph.createEdgeType(className);
            if(props != null) {
                for (String prop : props) {
                    et.createProperty(prop, OType.STRING);
                }
                et.createIndex(className + ".edgeProp", OClass.INDEX_TYPE.UNIQUE, props);
            }
            wasCreated = true;
        }
        txGraph.commit();
        txGraph.shutdown();
        return wasCreated;
    }

    //DB IO Functions
    //dbIO functions
    public boolean setDBImport(String exportData) {
        boolean isImported = false;
        try {

            //logger.info("Import Raw : " + exportData);

            //decode base64
            byte[] exportDataRawCompressed = DatatypeConverter.parseBase64Binary(exportData);
            InputStream iss = new ByteArrayInputStream(exportDataRawCompressed);
            //uncompress
            InputStream is = new GZIPInputStream(iss);

            //Scanner s = new Scanner(is).useDelimiter("\\A");
            //String result = s.hasNext() ? s.next() : "";
            //logger.info("Uncompressed Import :" + result);

            //InputStream is = new ByteArrayInputStream(exportData.getBytes(StandardCharsets.UTF_8));
            if(db == null) {
                ODatabaseDocumentTx db = pool.acquire();
            }


            DBImport dbImport = new DBImport(plugin, is, this,db);
            isImported = dbImport.importDump();
            //db.close();

        }
        catch(Exception ex) {
            logger.error("setDBImport  " + ex.getMessage());
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            logger.error(sw.toString());
        }
        //export.exportDatabase();
        //export.close();
        //database.close();
        return isImported;
    }

    public boolean setDBImportNative(String exportData) {
        boolean isImported = false;
        try {

            OCommandOutputListener listener = new OCommandOutputListener() {
                @Override
                public void onMessage(String iText) {
                    // System.out.print(iText);
                    logger.info(iText);
                }
            };
            //Not sure what this does, but is needed to dump database.
            //ODatabaseRecordThreadLocal.INSTANCE.set(db);
            //create location for output stream

            InputStream is = new ByteArrayInputStream(exportData.getBytes(StandardCharsets.UTF_8));

            if(db == null) {
                ODatabaseDocumentTx db = pool.acquire();
            }

            ODatabaseImport dbImport = new ODatabaseImport(db, is, listener);
            //operation
            dbImport.setMerge(true);
            dbImport.setDeleteRIDMapping(true);
            dbImport.setMigrateLinks(true);
            //dbImport.setRebuildIndexes(true);

            //filter export
            dbImport.setIncludeInfo(false);
            dbImport.setIncludeClusterDefinitions(false);
            dbImport.setIncludeSchema(false);
            dbImport.setIncludeIndexDefinitions(false);
            dbImport.setIncludeManualIndexes(false);
            dbImport.setIncludeSecurity(true);

            dbImport.importDatabase();
            dbImport.close();
            isImported = true;
            //db.close();


        }
        catch(Exception ex) {
            logger.error(ex.getMessage());
            logger.error(ex.toString());
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            logger.error(sw.toString()); //
        }
        //export.exportDatabase();
        //export.close();
        //database.close();
        return isImported;
    }

    public String getDBExport() {
        String exportString = null;
        try {

            Set<String> crescoDbClasses = new HashSet<String>();
            crescoDbClasses.add("rnode".toUpperCase());
            crescoDbClasses.add("anode".toUpperCase());
            crescoDbClasses.add("pnode".toUpperCase());
            //crescoDbClasses.add("resourcenode".toUpperCase());
            //crescoDbClasses.add("inode".toUpperCase());
            crescoDbClasses.add("isagent".toUpperCase());
            crescoDbClasses.add("isplugin".toUpperCase());
            //crescoDbClasses.add("isconnected".toUpperCase());
            //crescoDbClasses.add("isresource".toUpperCase());
            //crescoDbClasses.add("isreachable".toUpperCase());
            //crescoDbClasses.add("isassigned".toUpperCase());
            //System.out.println(crescoDbClasses);


            OCommandOutputListener listener = new OCommandOutputListener() {
                @Override
                public void onMessage(String iText) {
                    // System.out.print(iText);
                }
            };
            //Not sure what this does, but is needed to dump database.


            //create location for output stream
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            //ODatabaseDocumentTx db = null;
            //db = factory.getDatabase();


            if(db == null) {
                ODatabaseDocumentTx db = pool.acquire();
            }
            ODatabaseRecordThreadLocal.INSTANCE.set(db);
            ODatabaseExport export = new ODatabaseExport(db, os, listener);
            //filter export


            export.setIncludeInfo(false);
            export.setIncludeClusterDefinitions(false);
            export.setIncludeSchema(false);

            export.setIncludeIndexDefinitions(false);
            export.setIncludeManualIndexes(false);

            export.setIncludeSecurity(false);
            //include classes
            export.setIncludeClasses(crescoDbClasses);


            export.exportDatabase();

            String exportStringRaw = new String(os.toByteArray(),"UTF-8");

            export.close();

            //Now Compress and Encode
            exportString = DatatypeConverter.printBase64Binary(stringCompress(exportStringRaw));
            //byte[] message = "hello world".getBytes("UTF-8");
            //String encoded = DatatypeConverter.printBase64Binary(message);
            //byte[] decoded = DatatypeConverter.parseBase64Binary(encoded);


        }
        catch(Exception ex) {
            logger.error("getDBExport()  " + ex.getMessage());
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            logger.error(sw.toString());
        }
        //export.exportDatabase();
        //export.close();
        //database.close();
        return exportString;
    }

    public String stringUncompress(String str) {
        String uncompressedString = null;
        try {

            byte[] exportDataRawCompressed = DatatypeConverter.parseBase64Binary(str);
            InputStream iss = new ByteArrayInputStream(exportDataRawCompressed);
            //uncompress
            InputStream is = new GZIPInputStream(iss);
            uncompressedString = new Scanner(is,"UTF-8").useDelimiter("\\A").next();
        }
        catch(Exception ex) {
            logger.error("uncompressParam " + ex.getMessage());
        }
        return uncompressedString;
    }

    public byte[] stringCompress(String str) {
        byte[] dataToCompress = str.getBytes(StandardCharsets.UTF_8);
        byte[] compressedData = null;
        try
        {
            ByteArrayOutputStream byteStream =
                    new ByteArrayOutputStream(dataToCompress.length);
            try
            {
                GZIPOutputStream zipStream =
                        new GZIPOutputStream(byteStream);
                try
                {
                    zipStream.write(dataToCompress);
                }
                finally
                {
                    zipStream.close();
                }
            }
            finally
            {
                byteStream.close();
            }

            compressedData = byteStream.toByteArray();

        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        return compressedData;
    }

    public String getDBExport2() {
        String exportString = null;
        try {

            OCommandOutputListener listener = new OCommandOutputListener() {
                @Override
                public void onMessage(String iText) {
                    // System.out.print(iText);
                }
            };
            //Not sure what this does, but is needed to dump database.
            //ODatabaseRecordThreadLocal.INSTANCE.set(db);
            //create location for output stream
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            //ODatabaseDocumentTx db = pool.acquire();

            if(db == null) {
                ODatabaseDocumentTx db = pool.acquire();
            }


        ODatabaseExport export = new ODatabaseExport(db, os, listener);

            export.exportDatabase();

            exportString = new String(os.toByteArray(),"UTF-8");

            export.close();
            //db.close();
        }
        catch(Exception ex) {
            logger.error(ex.getMessage());
        }

        return exportString;
    }

}