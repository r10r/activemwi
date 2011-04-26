package de.codewheel.activemwi;

import java.io.IOException;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.asteriskjava.manager.AuthenticationFailedException;
import org.asteriskjava.manager.ManagerConnection;
import org.asteriskjava.manager.ManagerConnectionFactory;
import org.asteriskjava.manager.ManagerConnectionState;
import org.asteriskjava.manager.ManagerEventListener;
import org.asteriskjava.manager.TimeoutException;
import org.asteriskjava.manager.action.MailboxStatusAction;
import org.asteriskjava.manager.action.OriginateAction;
import org.asteriskjava.manager.action.StatusAction;
import org.asteriskjava.manager.event.ManagerEvent;
import org.asteriskjava.manager.event.PeerStatusEvent;
import org.asteriskjava.manager.response.MailboxStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO fix reconnection problem on asterisk restart
 * 
 * @author <a href="mailto:rjenster@ocotec.de">Ruben Jenster</a>
 *
 */
public class ActiveMWI implements ManagerEventListener {
	

	private static final Logger LOG = LoggerFactory.getLogger(ActiveMWI.class);

	private ManagerConnection eventConnection;
	private ManagerConnection cmdConnection;

	private static CompositeConfiguration config = new CompositeConfiguration();
	static {
		try {
			config.addConfiguration(new PropertiesConfiguration("/etc/activemwi.properties"));
		} catch (ConfigurationException e) {
			throw new RuntimeException(e);
		}
	}

	private static String SERVER_IP = config.getString("SERVER_IP", "localhost");
	private static int SERVER_PORT = config.getInt("SERVER_PORT", 5038);
	private static String MANAGER_USER = config.getString("MANAGER_USER");
	private static String MANAGER_PASS = config.getString("MANAGER_PASS");
	private static String MBOX_EXTEN = config.getString("MBOX_EXTEN");
	private static String MBOX_CONTEXT = config.getString("MBOX_CONTEXT");
	private static long MBOX_RING_TIMEOUT = config.getLong("MBOX_RING_TIMEOUT", 20000);
	private static long MBOX_RETRY_INTERVAL = config.getLong("MBOX_RETRY_INTERVAL", 600000);
	private static int MBOX_RETRY_MAX = config.getInt("MBOX_RETRY_MAX", 6);
	private static long CONNECTION_TIMEOUT = config.getLong("CONNECTION_TIMEOUT", 10000);

	public ActiveMWI() throws IOException {
		ManagerConnectionFactory factory = new ManagerConnectionFactory(SERVER_IP, SERVER_PORT, MANAGER_USER, MANAGER_PASS);

		// create connections
		eventConnection = factory.createManagerConnection();
		cmdConnection = factory.createManagerConnection();
	}

	public void run() throws IOException, AuthenticationFailedException,
			TimeoutException, InterruptedException {

		// register for events
		eventConnection.addEventListener(this);

		while (true) {

			// connect to Asterisk and log in
			eventConnection.login();

			// request channel state
			eventConnection.sendAction(new StatusAction());

			// wait for events to come in
			while (eventConnection.getState() == ManagerConnectionState.CONNECTED) {
				Thread.sleep(60000L);
			}

			// and finally log off and disconnect
			eventConnection.logoff();
		}
	}

	public static void main(String[] args) throws Exception {
		ActiveMWI helloEvents;

		helloEvents = new ActiveMWI();
		helloEvents.run();
	}

	public void onManagerEvent(ManagerEvent event) {

		if (event.getClass() == PeerStatusEvent.class) {
			onPeerStatusEvent((PeerStatusEvent) event);
		}

	}

	public void onPeerStatusEvent(PeerStatusEvent event) {

		if (event.getPeerStatus().equals(PeerStatusEvent.STATUS_REACHABLE)) {

			try {
				// open command connection
				cmdConnection.login();
				
				String peer = event.getPeer();
				// connect reachable client to mailbox
				if (hasNewMessages(peer)) {
					connectToMailbox(peer);
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				cmdConnection.logoff();
			}

		}
	}

	public boolean hasNewMessages(String peer) {
		
		boolean hasNewMessages = false;

		try {
			// query mailbox for new messages
			MailboxStatusAction mboxAction = new MailboxStatusAction(peer.replace("SIP/", "") + "@default");
			MailboxStatusResponse mboxResponse = (MailboxStatusResponse) cmdConnection.sendAction(mboxAction, CONNECTION_TIMEOUT);
			
			// connect reachable client to mailbox
			hasNewMessages = mboxResponse.getWaiting();
			
			if (hasNewMessages) {
				// TODO log exact number of waiting messages
				LOG.info("Peer[{}] new messages", peer);				
			} else {
				LOG.info("Peer[{}] has no new messages");
			}
			
			return hasNewMessages;
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// TODO: add better retry policy
	public void connectToMailbox(String peer) {
		try {
			try {
				Thread.sleep(3000L);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			OriginateAction mboxConnect;
			mboxConnect = new OriginateAction();
			mboxConnect.setChannel(peer);
			mboxConnect.setContext(MBOX_CONTEXT);
			mboxConnect.setExten(MBOX_EXTEN);
			mboxConnect.setCallerId(peer.replace("SIP/", ""));
			mboxConnect.setPriority(new Integer(1));
			mboxConnect.setTimeout(MBOX_RING_TIMEOUT);

			boolean connectionEstablished = false;
			int retries = 0;
			// try to connect the peer to the mailbox until success
			while (!connectionEstablished) {

				retries++;

				if (retries >= MBOX_RETRY_MAX) {
					LOG.error("Maximum connection retries[{}] exceeded. Aborting!", MBOX_RETRY_MAX);
					break;
				}

				// if client has read message in the meantime don't call him
				if (!hasNewMessages(peer)) {
					LOG.debug("Client has read messages in meantime. Aborting!");
					break;
				}
				
				connectionEstablished = connect(mboxConnect);

				// if client anwered the call finish calling loop
				if (connectionEstablished) {
					LOG.info("Connection established after [{}] retries");
					break;
				}
				try {
					LOG.info("Retry after {} msec", MBOX_RETRY_INTERVAL);
					Thread.sleep(MBOX_RETRY_INTERVAL);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TimeoutException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		LOG.debug("Connect to mailbox loop finished!");
	}
	
	
	private boolean connect(OriginateAction mboxConnect) throws IllegalArgumentException, IllegalStateException, IOException, TimeoutException {		
		LOG.debug("Connecting to mailbox of peer[{}]", mboxConnect.getChannel());
		String response = cmdConnection.sendAction(mboxConnect, MBOX_RING_TIMEOUT).getResponse();
		LOG.debug("Connection to peer[{}] established, status[{}]", mboxConnect.getChannel(), response);
		return response.equals("Success");
	}

}
