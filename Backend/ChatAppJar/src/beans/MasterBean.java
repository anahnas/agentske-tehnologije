package beans;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.vfs.VirtualFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import DTO.ACLMessageDTO;
import DTO.PredictResultDTO;
import DTO.predictDTO;
import agent.Collector;
import agent.Predictor;
import data.Data;
import data.NetworkData;
import model.ACLMessage;
import model.AID;
import model.Agent;
import model.AgentCenter;
import model.AgentType;
import model.Performative;

@Path("/master")
@LocalBean
@Singleton
@Startup
public class MasterBean extends AgentCenter {

	@EJB
	Data data; // data for agents and agent types

	@EJB
	NetworkData networkData; // data for nodes

	private String masterAddress;

	private Connection connection;
	@Resource(lookup = "java:jboss/exported/jms/RemoteConnectionFactory")
	private ConnectionFactory connectionFactory;
	@Resource(lookup = "java:jboss/exported/jms/topic/publicTopic")
	private Topic defaultTopic;

	@PostConstruct
	public void postConstruction() {
		try {
			connection = connectionFactory.createConnection("guest", "guest.guest.1");
			System.out.println("Created a connection.");
		} catch (JMSException ex) {
			throw new IllegalStateException(ex);
		}
		System.out.println("Created AgentCenter!");
		// InetAddress inetAddress;
		// AgentCenter node = new AgentCenter();

		try {
			AgentCenter node = new AgentCenter();
			InetAddress inetAddress = InetAddress.getLocalHost();
			node.setAddress(inetAddress.getHostAddress());
			node.setAlias(inetAddress.getHostName() + networkData.getCounter());

			networkData.setThisNode(node);
			System.out.println("IP Address:- " + node.getAddress() + " alias: " + node.getAlias());

			try {
				File f = getFile(SessionBean.class, "", "connections.properties");
				FileInputStream fileInput;
				fileInput = new FileInputStream(f);
				Properties properties = new Properties();

				try {
					properties.load(fileInput);
					fileInput.close();
					this.masterAddress = properties.getProperty("master");

					if (this.masterAddress == null || this.masterAddress.equals("")) {
						System.out.println("master created");
						networkData.setMaster(node);
						this.masterAddress = node.getAddress();

					} else {
						System.out.println("slave created");
						handshake(node);
					}
				} catch (IOException e) {

					e.printStackTrace();
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	@POST
	@Path("/test")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response testMsg(ACLMessageDTO aclMessageDTO) {
		System.out.println("In test msg....");
		ACLMessage aclMessage = new ACLMessage();
		aclMessage.setContent(aclMessageDTO.getContent());

		AID sender = this.data.getAIDByIndex(aclMessageDTO.getSenderIndex());
		
		List<AID> receivers = new ArrayList<>();
		for(int receiverIndex: aclMessageDTO.getReceiverIndexes()) {
			receivers.add(this.data.getAIDByIndex(receiverIndex));
		}
		
		if(sender == null || receivers.size() == 0) {
			System.out.println("You need to set a sender and receivers.");
		    return Response.status(Response.Status.BAD_REQUEST).build();
		}
		
		aclMessage.setSender(sender);
		aclMessage.setRecievers(receivers);
		
		this.sendTestMsg(aclMessage);
		return Response.ok("Ok", MediaType.APPLICATION_JSON).build();
	}
	
	public void sendTestMsg(ACLMessage aclMessage) {
		try {
			// metoda koja ce slati preko JMSa poruku MDBConsumeru, koji ce uraditi lookup
			// za agenta koji je receiver u ACL poruci i poslati mu poruku
			// ************************TEST KOMUNIKACIJE IZMEDJU
			// AGENATA**********************//
			/*AgentType agentType = new AgentType("collector", "lol-module");
			data.getAgentTypes().add(agentType);

			Collector testSender = new Collector();
			AID aidSender = new AID("testSender", this.networkData.getMaster(), agentType);
			testSender.setId(aidSender);
			this.data.getAgents().add(testSender);
			this.data.getRunningAgents().add(testSender);

			Collector testReceiver = new Collector();
			AID aidReceiver = new AID("testReceiver", this.networkData.getMaster(), agentType);
			testReceiver.setId(aidReceiver);
			this.data.getAgents().add(testReceiver);
			this.data.getRunningAgents().add(testReceiver);
			*/
			

			// ovo nadalje samo treba da se nalazi u metodi, sve inad je samo priprema
			// (zakucano) jer trenutno nemamo pravo slanje acl poruka i agente u bazi nego
			// ih pravim iznad

			for (int i = 0; i < aclMessage.getRecievers().size(); i++) {
				if (aclMessage.getRecievers().get(i) == null) {
					throw new IllegalArgumentException("AID cannot be null.");
				}
				// postToReceiver(msg, i, delayMillisec);
				postToReceiver(aclMessage, i);
			}

		} catch (JMSException e) {
			e.printStackTrace();
		}

	}

	private void postToReceiver(ACLMessage msg, int index) throws JMSException {
		System.out.println("ovdje");
		Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		connection.start();

		MessageProducer producer = session.createProducer(this.defaultTopic);
		try {
			ObjectMessage jmsMsg = session.createObjectMessage(msg);
			jmsMsg.setIntProperty("AIDIndex", index);
			producer.send(jmsMsg);
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}

	}

	// ******************************************MASTER ->
	// SLAVES*************************************************//

	public void delete(String alias) {
		System.out.println("Deleting node...");
		// this method calls @DELETE/node/{alias} rest method
		this.networkData.deleteNode(alias);
		for (int i = 0; i < networkData.getNodes().size(); i++) {
			if (!(networkData.getNodes().get(i).getAlias()).equals(alias)) {
				System.out.println(i + 1 + "/" + networkData.getNodes().size());
				ResteasyClient client = new ResteasyClientBuilder().build();

				ResteasyWebTarget target = client.target("http://" + networkData.getNodes().get(i).getAddress()
						+ ":8080/ChatAppWar/rest/master/node/" + alias);
				Response response = target.request().delete();
				String ret = response.readEntity(String.class);
				System.out.println("deleted node from " + networkData.getNodes().get(i).getAlias());
			}
		}
	}

	public void handshake(AgentCenter node) {
		try {
			register(node);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Retrying handshake");
			try {
				register(node);
			} catch (Exception e1) {
				System.out.println("Handshake unsuccessful. Node not registered");
			}
		}
	}

	public void register(AgentCenter node) {
		System.out.println("Registering node:");
		ResteasyClient client = new ResteasyClientBuilder().build();

		ResteasyWebTarget target = client.target("http://" + this.masterAddress + ":8080/ChatAppWar/rest/master/node");
		Response response = target.request().post(Entity.entity(node, "application/json"));
		client.close();
		if (response.getStatus() == 200)
			System.out.println("Node registered");
		else
			System.out.println("Node with same alias already exists");
	}

	public void sendNodesToNewNode(AgentCenter node) {
		System.out.println("****NODE ADDRESS: " + node.getAddress());
		AgentCenter ac = new AgentCenter("alias", "192.168.0.102");
		// AgentType at = new AgentType("node", "modules");

		// AID aid = new AID("imee", ac, at);
		this.networkData.getNodes().add(ac);

		try {
			// throw new EmptyStackException();
			System.out.println("try send nodes to new");
			ResteasyClient client1 = new ResteasyClientBuilder().build();

			ResteasyWebTarget target1 = client1
					.target("http://" + node.getAddress() + ":8080/ChatAppWar/rest/master/nodes");
			Response response1 = target1.request().post(Entity.entity(networkData.getNodes(), "application/json"));
			String ret1 = response1.readEntity(String.class);
			System.out.println("Sent node info to new node.");
			client1.close();
			sendNewNodeToNodes(node);
		} catch (Exception e) {
			try {
				// throw new EmptyStackException();
				ResteasyClient client1 = new ResteasyClientBuilder().build();
				ResteasyWebTarget target1 = client1
						.target("http://" + node.getAddress() + ":8080/ChatAppWar/rest/master/nodes");

				Response response1 = target1.request().post(Entity.entity(networkData.getNodes(), "application/json"));
				String ret1 = response1.readEntity(String.class);
				System.out.println("Sent node info to new node");
				client1.close();
				sendNewNodeToNodes(node);
			} catch (Exception e1) {
				System.out.println("Handshake unsuccessful: Roll-back...");
				delete(node.getAlias());
			}
		}
	}

	public void sendNewNodeToNodes(AgentCenter node) {
		System.out.println("****** NODES heh: " + node.getAddress());
		// AgentCenter ac = new AgentCenter("alias", "192.168.0.102");
		// AgentType at = new AgentType("node", "modules");

		// AID aid = new AID("imee", ac, at);
		// this.networkData.getNodes().add(ac);
		System.out.println("sending new node to nodes");

		// send info about new node to other nodes
		for (AgentCenter agentCenter : networkData.getNodes()) {
			if (!agentCenter.getAlias().equals(node.getAlias())) {
				ResteasyClient client = new ResteasyClientBuilder().build();

				ResteasyWebTarget target = client
						.target("http://" + agentCenter.getAddress() + ":8080/ChatAppWar/rest/master/node");
				Response response = target.request().post(Entity.entity(node, "application/json"));
				String ret = response.readEntity(String.class);
				System.out.println("Sent new node to other nodes.");
				client.close();
			}
		}
		// send agents to new node
		/*
		 * ResteasyClient client1 = new ResteasyClientBuilder().build();
		 * ResteasyWebTarget target1 = client1 .target("http://" + host.getAdress() +
		 * ":8080/ChatAppWar/rest/users/loggedIn"); Response response1 =
		 * target1.request().post(Entity.entity(userData.getLoggedIn(),
		 * "application/json")); String ret1 = response1.readEntity(String.class);
		 * System.out.println("sent users to new node"); client1.close();
		 */
	}

	// ****************************************AGENT-CENTER -
	// AGENT-CENTER****************************************//

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/node")
	public Response registerNode(AgentCenter agentCenter) {

		System.out.println("******AGENT ALLIAS:" + agentCenter.getAddress() + "******");

		if (networkData.getThisNode().getAddress().equals(masterAddress)) {
			System.out.println("imal te rodjo");
			System.out.println("***master adresa + " + masterAddress);
			// master registering new node
			for (AgentCenter a : networkData.getNodes()) {
				System.out.println("****All nodes:*** " + a.getAlias() + "** " + a.getAddress());
				if (a.getAlias().equals(agentCenter.getAlias()))
					// already exists
					return Response.status(400).build();
			}
//			networkData.getNodes().add(agentCenter);
//			System.out.println("New node registered.");
//			sendNodesToNewNode(agentCenter);

			new Thread(new Runnable() {
				public void run() {
					networkData.getNodes().add(agentCenter);
					System.out.println("New node registered.");
					// TODO:implement postNodes method *****************************************
					sendNodesToNewNode(agentCenter);
				}
			}).start();

			return Response.status(200).build();
		}
		// other nodes registering new node from master
		networkData.getNodes().add(agentCenter);

		return Response.status(200).build();

	}

	@GET
	@Path("/node/agents/classes")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSupportedAgentsClasses() {
		// return list of agent types from new node to master node
		return Response.ok(this.data.getAgentTypes(), MediaType.APPLICATION_JSON).build();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/node/agents/classes")
	public Response registerAgentTypes(ArrayList<AgentType> agentTypes) {
		this.data.setAgentTypes(agentTypes); // master setting agent types list to other nodes
		return Response.ok("Ok", MediaType.APPLICATION_JSON).build();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/nodes")
	public Response nodes(ArrayList<AgentCenter> nodes) {

		System.out.println("***List: " + nodes + "***");
		this.networkData.setNodes(nodes); // master setting node list to other nodes
		return Response.ok("Ok", MediaType.APPLICATION_JSON).build();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/node/agents/classes/{alias}")
	public Response nodes(@PathParam("alias") String alias, ArrayList<AgentType> agentTypes) {
		// find node with given alias
		this.data.setAgentTypes(agentTypes); // master setting node list to new node
		return Response.ok("Ok", MediaType.APPLICATION_JSON).build();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/node/agents/running")
	public Response getRunningAgentsNode(ArrayList<Agent> agents) {
		this.data.setRunningAgents(agents);
		return Response.ok("Ok", MediaType.APPLICATION_JSON).build();
	}

	@DELETE
	@Path("/node/{alias}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deleteNode(@PathParam("alias") String alias) {
		// delete node from node list
		this.networkData.deleteNode(alias);

		// delete all node agent types and shut down agents from that node
		ArrayList<Agent> toDelete = new ArrayList<>();
		for (Agent agent : this.data.getAgents()) {
			if (agent.getId().getHost().getAlias().equals(alias)) {
				toDelete.add(agent);
			}
		}

		for (Agent agent : toDelete) {
			this.data.deleteAgent(agent);
		}

		return Response.ok("Ok", MediaType.APPLICATION_JSON).build();

		// return Response.noContent().build();
	}

	/*
	 * @GET
	 * 
	 * @Path("/node/agents/classes")
	 * 
	 * @Produces(MediaType.APPLICATION_JSON) public Response
	 * getSupportedAgentsClasses() { // return list of agent types from new node to
	 * master node return Response.ok(this.data.getAgentTypes(),
	 * MediaType.APPLICATION_JSON).build(); }
	 * 
	 * @POST
	 * 
	 * @Consumes(MediaType.APPLICATION_JSON)
	 * 
	 * @Produces(MediaType.APPLICATION_JSON)
	 * 
	 * @Path("/node/agents/classes") public Response
	 * registerAgentTypes(ArrayList<AgentType> agentTypes) {
	 * this.data.setAgentTypes(agentTypes); // master setting agent types list to
	 * other nodes return Response.ok("Ok", MediaType.APPLICATION_JSON).build(); }
	 * 
	 * @POST
	 * 
	 * @Consumes(MediaType.APPLICATION_JSON)
	 * 
	 * @Produces(MediaType.APPLICATION_JSON)
	 * 
	 * @Path("/nodes") public Response nodes(ArrayList<AgentCenter> nodes) {
	 * this.networkData.setNodes(nodes); // master setting node list to other nodes
	 * return Response.ok("Ok", MediaType.APPLICATION_JSON).build(); }
	 * 
	 * @POST
	 * 
	 * @Consumes(MediaType.APPLICATION_JSON)
	 * 
	 * @Produces(MediaType.APPLICATION_JSON)
	 * 
	 * @Path("/node/agents/classes/{alias}") public Response
	 * nodes(@PathParam("alias") String alias, ArrayList<AgentType> agentTypes) { //
	 * find node with given alias this.data.setAgentTypes(agentTypes); // master
	 * setting node list to new node return Response.ok("Ok",
	 * MediaType.APPLICATION_JSON).build(); }
	 * 
	 * @POST
	 * 
	 * @Consumes(MediaType.APPLICATION_JSON)
	 * 
	 * @Produces(MediaType.APPLICATION_JSON)
	 * 
	 * @Path("/node/agents/running") public Response
	 * getRunningAgentsNode(ArrayList<Agent> agents) {
	 * this.data.setRunningAgents(agents); return Response.ok("Ok",
	 * MediaType.APPLICATION_JSON).build(); }
	 * 
	 * @DELETE
	 * 
	 * @Path("/node/{alias}")
	 * 
	 * @Produces(MediaType.APPLICATION_JSON) public Response
	 * deleteNode(@PathParam("alias") String alias) { // delete node from node list
	 * this.networkData.deleteNode(alias);
	 * 
	 * // delete all node agent types and shut down agents from that node
	 * ArrayList<Agent> toDelete = new ArrayList<>(); for (Agent agent :
	 * this.data.getAgents()) { if
	 * (agent.getId().getHost().getAlias().equals(alias)) { toDelete.add(agent); } }
	 * 
	 * for (Agent agent : toDelete) { this.data.deleteAgent(agent); }
	 * 
	 * return Response.ok("Ok", MediaType.APPLICATION_JSON).build();
	 * 
	 * // return Response.noContent().build(); }
	 * 
	 * 
	 * @GET
	 * 
	 * @Path("/node")
	 * 
	 * @Produces(MediaType.APPLICATION_JSON) public Response getNode() { return
	 * Response.ok(this.networkData.getThisNode(),
	 * MediaType.APPLICATION_JSON).build(); }
	 */
	// ******************************************AGENT-CENTER -
	// CLIENT*******************************************//



	@GET
	@Path("/agents/classes")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAgentsClasses() {
		// return list of agent types
		System.out.println("***Agent types:---" + this.data.getAgentTypes());
		return Response.ok(this.data.getAgentTypes(), MediaType.APPLICATION_JSON).build();
	}

	@GET
	@Path("/agents/running")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getRunningAgents() {
		System.out.println("***Running agents:---" + this.data.getRunningAgents());
		ArrayList<Agent> retVal = new ArrayList<>(); // return list of agents which have been run

		for (Agent agent : data.getRunningAgents()) {
			retVal.add(agent);
		}

		return Response.ok(retVal, MediaType.APPLICATION_JSON).build();

		// return list of agents which have been run
		// return Response.ok(this.getRunningAgents(),
		// MediaType.APPLICATION_JSON).build();
	}

	@GET
	@Path("/messages")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPerformatives() {
		// return list of performatives from enum
		ArrayList<Performative> retVal = new ArrayList<Performative>();

		Performative[] performative = Performative.values();

		for (Performative p : performative)

			retVal.add(p);

		return Response.ok(retVal, MediaType.APPLICATION_JSON).build();
	}

	@PUT
	@Path("/agents/running/{type}/{name}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response runAgent(@PathParam("type") String type, @PathParam("name") String name) {
		// return agent which has been run AID id = new	AID();
		AgentType agentType = this.data.getAgentType(type);
		
		if(agentType == null) {
			System.out.println("Type not found, creating new one...");
			agentType = new AgentType();
			agentType.setName(type);
			//default module
			agentType.setModule("lol-module");
			this.data.getAgentTypes().add(agentType);
		} else {
			System.out.println("Found type " + agentType.getName());
		}
		
		Agent agent = this.data.getAgentByName(name);
		
		if(agent == null) {
			System.out.println("Agent with given name not found, creating new one...");
			//zasad ovako
			if(agentType.getName().equals("collector")) {
				agent = new Collector();
			} else if(agentType.getName().equals("predictor")) {
				agent = new Predictor();
			} else {
				System.out.println("Agent cannot be created.");
			    return Response.status(Response.Status.BAD_REQUEST).build();
			}
		
			AID aid = new AID(this.networkData.getMaster(), agentType);
			aid.setName(name);
			agent.setId(aid);
			
			this.data.getAgents().add(agent);
		} else {
			System.out.println("Found agent with name " + agent.getId().getName());
		}
		
		if(!this.data.getRunningAgents().contains(agent))
			this.data.getRunningAgents().add(agent);
		else
			System.out.println("The agent has already been run.");
		
		return Response.ok(agent, MediaType.APPLICATION_JSON).build();
	}


	/*
	 * @DELETE
	 * 
	 * @Path("/agents/running/{alias}")
	 * 
	 * @Produces(MediaType.APPLICATION_JSON) public Response
	 * stopAgent(@PathParam("aid") String aid) { Agent retVal = new Agent(); //
	 * return agent which has been stopped
	 * 
	 * String agentId = retVal.getId().toString(); if (agentId == aid) {
	 * 
	 * this.data.getRunningAgents().remove(retVal);
	 * 
	 * }
	 * 
	 * return Response.ok(retVal, MediaType.APPLICATION_JSON).build();
	 * 
	 * }
	 * 
	 * @POST
	 * 
	 * @Consumes(MediaType.APPLICATION_JSON)
	 * 
	 * @Produces(MediaType.APPLICATION_JSON)
	 * 
	 * @Path("/messages") public Response sendACLMessage(ACLMessage aclMessage) {
	 * 
	 * ACLMessage aclMess = new ACLMessage();
	 * 
	 * aclMess.setSender(aclMessage.getSender());
	 * 
	 * aclMess.setRecievers(aclMessage.getRecievers());
	 * 
	 * aclMess.setPerformative(aclMessage.getPerformative());
	 * 
	 * aclMess.setContent(aclMessage.getContent());
	 * 
	 * aclMess.setContentObj(aclMessage.getContentObj());
	 * 
	 * aclMess.setConversationId(aclMessage.getConversationId());
	 * 
	 * aclMess.setEncoding(aclMessage.getEncoding());
	 * 
	 * aclMess.setInReplyTo(aclMessage.getInReplyTo());
	 * 
	 * aclMess.setLanguage(aclMessage.getLanguage());
	 * 
	 * aclMess.setOntology(aclMessage.getOntology());
	 * 
	 * aclMess.setProtocol(aclMessage.getProtocol());
	 * 
	 * aclMess.setReplyBy(aclMessage.getReplyBy());
	 * 
	 * aclMess.setReplyTo(aclMessage.getReplyTo());
	 * 
	 * aclMess.setReplyWith(aclMessage.getReplyWith());
	 * 
	 * aclMess.setUserArgs(aclMessage.getUserArgs());
	 * 
	 * this.data.getAclMessages().add(aclMess);
	 * 
	 * return Response.ok("Ok.", MediaType.APPLICATION_JSON).build(); }
	 * 
	 * 
	 * @POST
	 * 
	 * @Consumes(MediaType.APPLICATION_JSON)
	 * 
	 * @Produces(MediaType.APPLICATION_JSON)
	 * 
	 * @Path("/predict") public Response predictResult(predictDTO predictDTO) {
	 * 
	 * try { Session session = connection.createSession(false,
	 * Session.AUTO_ACKNOWLEDGE); connection.start(); MessageProducer producer =
	 * session.createProducer(this.defaultTopic); Message message =
	 * session.createTextMessage(); ObjectMapper mapper = new ObjectMapper(); String
	 * predictDTOJSON = ""; try { predictDTOJSON =
	 * mapper.writeValueAsString(predictDTO); } catch (Exception e) {
	 * e.printStackTrace(); }
	 * 
	 * ((TextMessage) message).setText(predictDTOJSON); producer.send(message);
	 * producer.close(); connection.close(); } catch (JMSException e) {
	 * e.printStackTrace(); }
	 * 
	 * PredictResultDTO retVal = new PredictResultDTO(); return Response.ok(retVal,
	 * MediaType.APPLICATION_JSON).build(); }
	 */
	// ***********************************************PREBACITI KASNIJE U
	// UTILS*******************************************//
	public static File getFile(Class<?> c, String prefix, String fileName) {
		File f = null;
		URL url = c.getResource(prefix + fileName);
		if (url != null) {
			if (url.toString().startsWith("vfs:/")) {
				try {
					URLConnection conn = new URL(url.toString()).openConnection();
					VirtualFile vf = (VirtualFile) conn.getContent();
					f = vf.getPhysicalFile();
				} catch (Exception ex) {
					ex.printStackTrace();
					f = new File(".");
				}
			} else {
				try {
					f = new File(url.toURI());
				} catch (URISyntaxException e) {
					e.printStackTrace();
					f = new File(".");
				}
			}
		} else {
			f = new File(fileName);
		}
		return f;
	}
}
