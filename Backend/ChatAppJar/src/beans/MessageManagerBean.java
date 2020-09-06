package beans;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.Topic;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.LoggerFactory;

import com.sun.org.slf4j.internal.Logger;

import DTO.ACLMessageDTO;
import data.Data;
import model.ACLMessage;
import model.AID;
import model.MessageManager;
import model.Performative;
import util.JMSFactory;

@Stateless
@Remote(MessageManager.class)
@LocalBean
public class MessageManagerBean implements MessageManager {

	
	@EJB
	Data data; // data for agents and agent types

	@Resource(lookup = "java:jboss/exported/jms/topic/publicTopic")
	private Topic defaultTopic;
	
	private Connection connection;

	@Resource(lookup = "java:jboss/exported/jms/RemoteConnectionFactory")
	private ConnectionFactory connectionFactory;

	Session session;
	
	public MessageManagerBean() {
		super();
		try {
			connection = connectionFactory.createConnection("guest", "guest.guest.1");
			connection.start();
			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		} catch (JMSException ex) {
			throw new IllegalStateException(ex);
		}
		System.out.println("Created Message manager!");
	}

	@PostConstruct
	public void postConstruction() {
		try {
			connection = connectionFactory.createConnection("guest", "guest.guest.1");
		} catch (JMSException ex) {
			throw new IllegalStateException(ex);
		}
		System.out.println("Created Message manager!");
	}

	@Override
	public boolean sendMessage(ACLMessage aclMessage) {
		System.out.println("In message manager...");

		try {
			for (int i = 0; i < aclMessage.getRecievers().size(); i++) {
				if (aclMessage.getRecievers().get(i) == null) {
					throw new IllegalArgumentException("AID cannot be null.");
				}
				postToReceiver(aclMessage, i);
			}

		} catch (JMSException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}


	private void postToReceiver(ACLMessage msg, int index) throws JMSException {
		//Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		//connection.start();

		MessageProducer producer = session.createProducer(this.defaultTopic);
		try {
			ObjectMessage jmsMsg = session.createObjectMessage(msg);
			jmsMsg.setIntProperty("AIDIndex", index);
			producer.send(jmsMsg);
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}

	}

	@Override
	public String ping() {
		// TODO Auto-generated method stub
		return null;
	}
}